import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import school.magiccodex.database.DatabaseSettings;

/**
 * 일반 database.properties 도메인(닉네임·친구·칭호·기숙사/기부/점수·의뢰·대화·호감도)을
 * 서버별 SQLite(friends.db, titles.db, school.db, quests.db, dialogue.db, affinity.db)에서 공유 MariaDB로 병합 이관한다.
 *
 * <p>실행 (서버를 모두 멈춘 뒤, SQLite 파일 사본으로):
 * <pre>
 * java -cp magic-codex-bridge-*.jar:sqlite-jdbc-3.47.2.0.jar tools/db-migrate/GenericDomainMigration.java \
 *   --source school=/backup/school/MagicCodexBridge --source wild=/backup/wild/MagicCodexBridge [--source arena=...] \
 *   --target /path/player-state-database.properties [--apply] [--merge-into-existing] [--domains nickname,friend,...] [--report report.md]
 * </pre>
 * Windows는 classpath 구분자가 ';'. MariaDB 드라이버와 DatabaseSettings는 Bridge jar 안에 이미 들어 있다(magic-codex-paper build.gradle의 jar zipTree).
 * 표는 Bridge jar의 저장소 클래스(NicknameStore 등)를 그대로 생성해서 만든다(플러그인과 같은 DDL).
 *
 * <p>기본은 점검만(dry-run): 아무것도 쓰지 않고 한국어 보고서만 낸다. {@code --apply}면 도메인마다 트랜잭션 하나로 쓴다.
 * 같은 원본을 다시 넣지 않도록 codex_generic_migration(domain, source, checksum)에 기록한다.
 * 대상 표에 이미 플레이어 데이터가 있으면 {@code --merge-into-existing} 없이는 그 도메인을 거부한다.
 * 비밀번호는 어디에도 출력하지 않는다(대상은 host:port/db만 표시).
 */
public final class GenericDomainMigration {
    static final String MARKER = "codex_generic_migration";
    static final String EXISTING = "existing-db";
    static final long DAY = 24L * 60 * 60 * 1000;
    static final long REQUEST_TTL = 7 * DAY;           // FriendStore.REQUEST_TTL
    static final int FRIEND_LIMIT = 50;                 // SocialProtocol.LIMIT
    static final int STORY_LIMIT = 512;                 // DialogueStore 스토리 상태 한도
    static final long MAX_SCORE = 1_000_000_000L;       // SchoolStore.MAX_SCORE

    enum Mode { UPSERT, IGNORE, CATALOG }

    /** target = MariaDB 표, sqlite = SQLite 표. key = 기본키(병합·upsert 기준). defaults = 예전 SQLite 파일에 없을 수 있는 열. */
    record Table(String target, String sqlite, List<String> columns, List<String> key, Mode mode, String sqliteOrder, String targetOrder, Map<String, Object> defaults) {
        List<String> values() { return columns.stream().filter(c -> !key.contains(c)).toList(); }
    }

    static Table t(String target, String sqlite, String columns, String key, Mode mode, String sqliteOrder, String targetOrder, Object... defaults) {
        var d = new LinkedHashMap<String, Object>();
        for (int i = 0; i < defaults.length; i += 2) d.put((String) defaults[i], defaults[i + 1]);
        return new Table(target, sqlite, List.of(columns.split(",")), List.of(key.split(",")), mode, sqliteOrder, targetOrder, Collections.unmodifiableMap(d));
    }

    enum Domain {
        NICKNAME("nickname", "닉네임", "friends.db", "NicknameStore", true, List.of(
                t("codex_nicknames", "nicknames", "owner,account,nickname,nickname_key,revision,first_done", "owner", Mode.UPSERT, "rowid", "owner", "nickname_key", null, "first_done", 0L))),
        FRIEND("friend", "친구·친구 신청", "friends.db", "FriendStore", true, List.of(
                t("codex_friends", "friends", "owner,target,name,dorm", "owner,target", Mode.IGNORE, "rowid", "id"),
                t("codex_friend_requests", "friend_requests", "sender,target,name,dorm,target_name,target_dorm,created", "sender,target", Mode.UPSERT, "rowid", "sender,target"),
                t("codex_friend_declines", "friend_declines", "sender,target,blocked_until", "sender,target", Mode.UPSERT, "rowid", "sender,target"),
                t("codex_first_friend_signal", "first_friend_signal", "owner,delivered", "owner", Mode.UPSERT, "rowid", "owner"))),
        TITLE("title", "칭호", "titles.db", "TitleStore", false, List.of(
                t("codex_title_player", "codex_title_player", "player,revision,prefix_id,suffix_id", "player", Mode.UPSERT, "rowid", "player"),
                t("codex_title_owned", "codex_title_owned", "player,title_id", "player,title_id", Mode.IGNORE, "rowid", "player,title_id"))),
        SCHOOL("school", "기숙사 배정·마법 기부·기숙사 점수", "school.db", "SchoolStore", true, List.of(
                t("codex_houses", "houses", "player,house", "player", Mode.UPSERT, "rowid", "player"),
                t("codex_donations", "donations", "spell,spell_name,donor,nickname,house,created", "spell", Mode.UPSERT, "rowid", "created,spell"),
                t("codex_house_points", "house_points", "house,points", "house", Mode.UPSERT, "house", "house"),
                t("codex_donation_policy", "donation_policy", "spell,blocked", "spell", Mode.UPSERT, "rowid", "spell"),
                t("codex_quest_house_awards", "codex_quest_house_awards", "token,house,points", "token", Mode.IGNORE, "rowid", "token"))),
        QUEST("quest", "의뢰 진행", "quests.db", "QuestStore", false, List.of(
                t("codex_quest_progress", "codex_quest_progress", "player,quest,cycle,definition,status,progress,token,updated,completions,stage", "player,quest,cycle", Mode.UPSERT, "rowid", "player,quest,cycle", "completions", 0L, "stage", 0L),
                t("codex_quest_catalog", "codex_quest_catalog", "id,definition", "id", Mode.CATALOG, "rowid", "id"))),
        DIALOGUE("dialogue", "대화·스토리 상태", "dialogue.db", "DialogueStore", false, List.of(
                t("codex_story_state", "codex_story_state", "player,revision,content", "player", Mode.UPSERT, "rowid", "player"),
                t("codex_dialogue_effect", "codex_dialogue_effect", "player,effect_key,token,status,actions", "player,effect_key", Mode.UPSERT, "rowid", "player,effect_key"),
                t("codex_dialogue_catalog", "codex_dialogue_catalog", "id,revision,definition", "id", Mode.CATALOG, "rowid", "id"))),
        AFFINITY("affinity", "NPC 호감도", "affinity.db", "AffinityStore", false, List.of(
                t("codex_affinity", "npc_affinity", "owner,npc,score,heart,nickname,updated", "owner,npc", Mode.UPSERT, "rowid", "owner,npc", "nickname", "", "updated", 0L),
                t("codex_affinity_daily", "npc_affinity_daily", "owner,npc,day,source,amount", "owner,npc,day,source", Mode.UPSERT, "rowid", "owner,npc,day,source"),
                t("codex_affinity_gift", "npc_affinity_gift", "token,owner,npc,day,delta,status,created", "token", Mode.IGNORE, "rowid", "token")));

        final String id, label, file, store;
        /** true: 생성자가 (Path, DatabaseSettings), false: (DatabaseSettings, Path). */
        final boolean pathFirst;
        final List<Table> tables;
        Domain(String id, String label, String file, String store, boolean pathFirst, List<Table> tables) {
            this.id = id; this.label = label; this.file = file; this.store = store; this.pathFirst = pathFirst; this.tables = tables;
        }
        Table table(String target) { for (var t : tables) if (t.target.equals(target)) return t; throw new IllegalArgumentException(target); }
        static Domain of(String id) { for (var d : values()) if (d.id.equals(id)) return d; throw new IllegalArgumentException("알 수 없는 도메인: " + id); }
    }

    // ------------------------------------------------------------------ 데이터 모델

    /** 한 원본(서버 또는 기존 DB)의 한 도메인 데이터. rows: MariaDB 표 이름 → 행(원본 순서 유지). */
    record Src(String label, Map<String, List<Map<String, Object>>> rows, Map<String, Long> donationPoints) {
        List<Map<String, Object>> of(String table) { return rows.getOrDefault(table, List.of()); }
    }

    /** 병합 결과. */
    static final class Plan {
        final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        final List<String> conflicts = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        List<Map<String, Object>> table(String name) { return rows.computeIfAbsent(name, k -> new ArrayList<>()); }
    }

    record Cand(int source, int index, Map<String, Object> row) {}

    // ------------------------------------------------------------------ 값 도우미

    static String s(Map<String, Object> row, String column) { Object v = row.get(column); return v == null ? null : v.toString(); }
    static long l(Map<String, Object> row, String column) {
        Object v = row.get(column);
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return 0; }
    }
    static Object norm(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? 1L : 0L;
        if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) return ((Number) v).longValue();
        if (v instanceof java.math.BigInteger b) return b.longValue();
        if (v instanceof java.math.BigDecimal b) return b.scale() <= 0 ? (Object) b.longValue() : (Object) b.doubleValue();
        if (v instanceof Float f) return f.doubleValue();
        if (v instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return v;
    }
    static String keyOf(Table t, Map<String, Object> row) {
        var b = new StringBuilder();
        for (String k : t.key) b.append(s(row, k)).append('\u0001');
        return b.toString();
    }
    static boolean sameValues(Table t, Map<String, Object> a, Map<String, Object> b) {
        for (String c : t.values()) if (!Objects.equals(text(a.get(c)), text(b.get(c)))) return false;
        return true;
    }
    static String text(Object v) { return v == null ? null : v instanceof Number n && !(v instanceof Double) ? Long.toString(n.longValue()) : v.toString(); }
    static String shortId(String uuid) { return uuid == null ? "?" : uuid.length() > 8 ? uuid.substring(0, 8) : uuid; }

    /** NicknameStore.key 와 같은 규칙: NFKC + 소문자 + NFKC. 비어 있거나 64자 초과면 null. */
    static String nicknameKey(String nickname) {
        if (nickname == null) return null;
        String k = Normalizer.normalize(nickname, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        k = Normalizer.normalize(k, Normalizer.Form.NFKC);
        return k.isEmpty() || k.length() > 64 ? null : k;
    }

    /** 키별 후보(원본 우선순위 → 원본 안 순서). */
    static LinkedHashMap<String, List<Cand>> group(Table t, List<Src> sources) {
        var map = new LinkedHashMap<String, List<Cand>>();
        for (int i = 0; i < sources.size(); i++) {
            var rows = sources.get(i).of(t.target);
            for (int j = 0; j < rows.size(); j++) map.computeIfAbsent(keyOf(t, rows.get(j)), k -> new ArrayList<>()).add(new Cand(i, j, rows.get(j)));
        }
        return map;
    }
    static String where(List<Src> sources, Cand c) { return sources.get(c.source).label; }

    // ------------------------------------------------------------------ 병합 규칙

    static Plan merge(Domain d, List<Src> sources, long now) {
        var plan = new Plan();
        switch (d) {
            case NICKNAME -> mergeNickname(sources, plan);
            case FRIEND -> mergeFriend(sources, plan, now);
            case TITLE -> mergeTitle(sources, plan);
            case SCHOOL -> mergeSchool(sources, plan);
            case QUEST -> mergeQuest(sources, plan);
            case DIALOGUE -> mergeDialogue(sources, plan);
            case AFFINITY -> mergeAffinity(sources, plan);
        }
        return plan;
    }

    /**
     * 같은 UUID에 다른 닉네임: 닉네임 표에 수정 시각이 없으므로 우선순위가 높은 원본(첫 --source, 보통 school)이 이긴다.
     * first_done은 OR, revision은 최댓값. 같은 정규화 키를 다른 UUID가 쓰면 가장 먼저 저장된 것(원본 우선순위 → 원본 안 rowid)을 남기고,
     * 나머지는 닉네임을 그대로 두되 nickname_key를 비워(NULL) 둔다. NicknameStore가 시작할 때 이 행을 "중복 닉네임 발견"으로 경고하고, 관리자가 이름을 바꾼다.
     */
    static void mergeNickname(List<Src> sources, Plan plan) {
        var table = Domain.NICKNAME.table("codex_nicknames");
        record Winner(Cand cand, Map<String, Object> row) {}
        var winners = new ArrayList<Winner>();
        for (var e : group(table, sources).entrySet()) {
            var cands = e.getValue();
            var first = cands.get(0);
            var row = new LinkedHashMap<>(first.row);
            long revision = 0, done = 0;
            for (var c : cands) {
                revision = Math.max(revision, Math.max(1, l(c.row, "revision")));
                done |= l(c.row, "first_done") != 0 ? 1 : 0;
                if (c != first && !Objects.equals(s(c.row, "nickname"), s(first.row, "nickname")))
                    plan.conflicts.add("같은 UUID 다른 닉네임: " + s(first.row, "owner") + " → '" + s(first.row, "nickname") + "'(" + where(sources, first) + ") 유지, '" + s(c.row, "nickname") + "'(" + where(sources, c) + ") 버림 (수정 시각 기록이 없어 우선 원본 기준)");
            }
            row.put("revision", revision);
            row.put("first_done", done);
            row.put("nickname_key", nicknameKey(s(row, "nickname")));
            if (row.get("nickname_key") == null) plan.notes.add("규칙에 맞지 않는 닉네임(키 없음으로 이관): " + s(row, "owner") + " '" + s(row, "nickname") + "'");
            winners.add(new Winner(first, row));
        }
        var byKey = new LinkedHashMap<String, List<Winner>>();
        for (var w : winners) { String k = s(w.row, "nickname_key"); if (k != null) byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(w); }
        for (var e : byKey.entrySet()) {
            var list = e.getValue();
            if (list.size() < 2) continue;
            String key = e.getKey();
            // 같은 원본 안에서는 이미 이 키를 가진 행(기존 DB의 현재 주인)을 먼저, 그다음 저장 순서.
            list.sort(Comparator.comparingInt((Winner w) -> w.cand.source).thenComparing(w -> !key.equals(s(w.cand.row, "nickname_key"))).thenComparingInt(w -> w.cand.index));
            var keep = list.get(0);
            for (var other : list.subList(1, list.size())) {
                other.row.put("nickname_key", null);
                plan.conflicts.add("중복 닉네임 '" + s(other.row, "nickname") + "': " + s(keep.row, "owner") + "(" + s(keep.row, "account") + ", " + where(sources, keep.cand) + ") 유지 / "
                        + s(other.row, "owner") + "(" + s(other.row, "account") + ", " + where(sources, other.cand) + ") 이름 변경 필요 → /유저관리 " + s(other.row, "account") + " 닉네임 설정 <새닉네임>");
            }
        }
        var accounts = new HashMap<String, String>();
        for (var w : winners) { String a = s(w.row, "account"); if (a != null) accounts.put(a.toLowerCase(Locale.ROOT), s(w.row, "owner")); }
        for (var w : winners) {
            String k = s(w.row, "nickname_key"), owner = accounts.get(k);
            if (k != null && owner != null && !owner.equals(s(w.row, "owner")))
                plan.notes.add("참고: 닉네임 '" + s(w.row, "nickname") + "'(" + s(w.row, "owner") + ")이 다른 플레이어 계정 이름과 같음 (" + owner + "). 기존 데이터라 그대로 둠.");
        }
        // 키를 비우는 행을 먼저 쓴다: 기존 DB에서 키 주인이 바뀌는 경우에도 UNIQUE 인덱스에 걸리지 않게.
        for (var w : winners) if (w.row.get("nickname_key") == null) plan.table("codex_nicknames").add(w.row);
        for (var w : winners) if (w.row.get("nickname_key") != null) plan.table("codex_nicknames").add(w.row);
    }

    /** 친구 관계는 합집합(이름·기숙사 표시는 우선 원본). 친구 신청은 합집합, 만료(7일)된 것과 이미 서로 친구인 쌍의 신청은 뺀다. 거절 대기는 큰 값, 첫 친구 신호는 delivered OR. */
    static void mergeFriend(List<Src> sources, Plan plan, long now) {
        var friends = Domain.FRIEND.table("codex_friends");
        var pairs = new HashSet<String>();
        var counts = new LinkedHashMap<String, Integer>();
        for (var e : group(friends, sources).entrySet()) {
            var row = e.getValue().get(0).row;
            plan.table("codex_friends").add(row);
            pairs.add(s(row, "owner") + "|" + s(row, "target"));
            counts.merge(s(row, "owner"), 1, Integer::sum);
        }
        // 표시 순서(id)는 우선 원본의 행이 먼저 오도록 원본 순서대로 넣는다(group이 이미 그 순서).
        for (var e : counts.entrySet()) if (e.getValue() > FRIEND_LIMIT)
            plan.conflicts.add("친구 수 한도(" + FRIEND_LIMIT + ") 초과: " + e.getKey() + " = " + e.getValue() + "명 (모두 유지, 새 친구 추가만 막힘)");
        int expired = 0, contradicted = 0;
        for (var e : group(Domain.FRIEND.table("codex_friend_requests"), sources).entrySet()) {
            var best = e.getValue().get(0).row;
            for (var c : e.getValue()) if (l(c.row, "created") > l(best, "created")) best = c.row;
            String a = s(best, "sender"), b = s(best, "target");
            if (l(best, "created") < now - REQUEST_TTL) { expired++; continue; }
            if (pairs.contains(a + "|" + b) && pairs.contains(b + "|" + a)) { contradicted++; continue; }
            plan.table("codex_friend_requests").add(best);
        }
        if (expired > 0) plan.notes.add("만료된 친구 신청 " + expired + "건 제외 (7일 경과)");
        if (contradicted > 0) plan.notes.add("이미 서로 친구인 쌍의 친구 신청 " + contradicted + "건 제외");
        int declines = 0;
        for (var e : group(Domain.FRIEND.table("codex_friend_declines"), sources).entrySet()) {
            var best = e.getValue().get(0).row;
            for (var c : e.getValue()) if (l(c.row, "blocked_until") > l(best, "blocked_until")) best = c.row;
            if (l(best, "blocked_until") <= now) { declines++; continue; }
            plan.table("codex_friend_declines").add(best);
        }
        if (declines > 0) plan.notes.add("지난 재신청 대기 기록 " + declines + "건 제외");
        for (var e : group(Domain.FRIEND.table("codex_first_friend_signal"), sources).entrySet()) {
            var row = new LinkedHashMap<>(e.getValue().get(0).row);
            long delivered = 0;
            for (var c : e.getValue()) delivered |= l(c.row, "delivered") != 0 ? 1 : 0;
            row.put("delivered", delivered);
            plan.table("codex_first_friend_signal").add(row);
        }
    }

    /** 보유 칭호는 합집합. 선택(prefix/suffix)은 수정 시각이 없어 우선 원본. revision은 최댓값. */
    static void mergeTitle(List<Src> sources, Plan plan) {
        var players = Domain.TITLE.table("codex_title_player");
        for (var e : group(players, sources).entrySet()) {
            var first = e.getValue().get(0);
            var row = new LinkedHashMap<>(first.row);
            long revision = 0;
            for (var c : e.getValue()) {
                revision = Math.max(revision, l(c.row, "revision"));
                if (c != first && (!Objects.equals(s(c.row, "prefix_id"), s(first.row, "prefix_id")) || !Objects.equals(s(c.row, "suffix_id"), s(first.row, "suffix_id"))))
                    plan.conflicts.add("칭호 선택 다름: " + s(row, "player") + " → " + where(sources, first) + " 선택 유지 [" + s(first.row, "prefix_id") + " / " + s(first.row, "suffix_id") + "], "
                            + where(sources, c) + " 선택 [" + s(c.row, "prefix_id") + " / " + s(c.row, "suffix_id") + "] 버림");
            }
            row.put("revision", revision);
            plan.table("codex_title_player").add(row);
        }
        for (var e : group(Domain.TITLE.table("codex_title_owned"), sources).entrySet()) plan.table("codex_title_owned").add(e.getValue().get(0).row);
    }

    /**
     * 기숙사 배정: 배정 시각 기록이 없어 우선 원본(school)이 이긴다. 기부: 마법마다 가장 이른 기부(created)가 이긴다(같으면 우선 원본).
     * 기숙사 점수: 원본별 점수의 합에서, 탈락한 기부가 그 원본 서버에서 받은 점수(원본 폴더의 donation-spells.yml 기준)를 뺀다.
     * yml이 없는 원본이 있으면 빼지 못하고 합만 쓴 뒤 보고한다. 기증 금지는 OR. 의뢰 기숙사 점수 지급 기록은 토큰 합집합.
     */
    static void mergeSchool(List<Src> sources, Plan plan) {
        var counts = new long[4];
        for (var e : group(Domain.SCHOOL.table("codex_houses"), sources).entrySet()) {
            var first = e.getValue().get(0);
            for (var c : e.getValue()) if (c != first && l(c.row, "house") != l(first.row, "house"))
                plan.conflicts.add("기숙사 배정 다름: " + s(first.row, "player") + " → " + l(first.row, "house") + "번(" + where(sources, first) + ") 유지, " + l(c.row, "house") + "번(" + where(sources, c) + ") 버림 (배정 시각 기록이 없어 우선 원본 기준)");
            long h = l(first.row, "house");
            if (h < 0 || h > 3) { plan.conflicts.add("잘못된 기숙사 번호 제외: " + s(first.row, "player") + " = " + h); continue; }
            counts[(int) h]++;
            plan.table("codex_houses").add(first.row);
        }
        plan.notes.add("병합 후 기숙사 인원: 0번 " + counts[0] + " · 1번 " + counts[1] + " · 2번 " + counts[2] + " · 3번 " + counts[3]);
        long[] deduct = new long[4];
        boolean exact = true;
        for (var e : group(Domain.SCHOOL.table("codex_donations"), sources).entrySet()) {
            var cands = new ArrayList<>(e.getValue());
            cands.sort(Comparator.comparingLong((Cand c) -> l(c.row, "created")).thenComparingInt(c -> c.source));
            var keep = cands.get(0);
            plan.table("codex_donations").add(keep.row);
            for (var c : cands.subList(1, cands.size())) {
                if (Objects.equals(s(c.row, "donor"), s(keep.row, "donor")) && l(c.row, "created") == l(keep.row, "created") && c.source != keep.source) continue; // 같은 기록(이미 이관된 것)
                var pts = sources.get(c.source).donationPoints;
                long h = l(c.row, "house");
                String spell = s(c.row, "spell");
                Long p = pts == null ? null : pts.getOrDefault(spell, 1L);
                if (p == null) exact = false;
                else if (h >= 0 && h <= 3) deduct[(int) h] += p;
                plan.conflicts.add("같은 마법 중복 기부 '" + spell + "': " + s(keep.row, "donor") + "(" + where(sources, keep) + ", " + l(keep.row, "created") + ") 유지 / "
                        + s(c.row, "donor") + "(" + where(sources, c) + ", " + l(c.row, "created") + ") 제외" + (p == null ? " — 점수 차감 불가(" + where(sources, c) + " donation-spells.yml 없음)" : " — " + h + "번 기숙사 " + p + "점 차감"));
            }
        }
        long[] sum = new long[4];
        var detail = new StringBuilder();
        for (var src : sources) {
            long[] mine = new long[4];
            for (var row : src.of("codex_house_points")) { long h = l(row, "house"); if (h >= 0 && h <= 3) mine[(int) h] = l(row, "points"); }
            for (int i = 0; i < 4; i++) sum[i] += mine[i];
            detail.append(src.label).append(Arrays.toString(mine)).append(' ');
        }
        // 의뢰 기숙사 점수: 같은 토큰이 두 원본에 있으면(사실상 없음) 한 번만 남기고 그만큼 뺀다.
        for (var e : group(Domain.SCHOOL.table("codex_quest_house_awards"), sources).entrySet()) {
            var first = e.getValue().get(0);
            plan.table("codex_quest_house_awards").add(first.row);
            for (var c : e.getValue()) if (c != first) {
                long h = l(c.row, "house");
                if (h >= 0 && h <= 3) deduct[(int) h] += l(c.row, "points");
                plan.conflicts.add("의뢰 기숙사 점수 토큰 중복: " + s(c.row, "token") + " (" + where(sources, c) + " 쪽 " + l(c.row, "points") + "점 차감)");
            }
        }
        for (int h = 0; h < 4; h++) {
            long value = sum[h] - deduct[h];
            if (value > MAX_SCORE || value < -MAX_SCORE) { plan.conflicts.add(h + "번 기숙사 점수 범위 초과 " + value + " → " + Math.max(-MAX_SCORE, Math.min(MAX_SCORE, value)) + "로 자름"); value = Math.max(-MAX_SCORE, Math.min(MAX_SCORE, value)); }
            var row = new LinkedHashMap<String, Object>();
            row.put("house", (long) h);
            row.put("points", value);
            plan.table("codex_house_points").add(row);
        }
        plan.notes.add("기숙사 점수: 원본별 " + detail.toString().trim() + " → 합 " + Arrays.toString(sum) + " − 중복 기부/지급 차감 " + Arrays.toString(deduct)
                + " = " + Arrays.toString(plan.table("codex_house_points").stream().mapToLong(r -> l(r, "points")).toArray())
                + (exact ? "" : " (일부 원본에 donation-spells.yml이 없어 차감하지 못한 점수가 있음: 관리자 확인)"));
        for (var e : group(Domain.SCHOOL.table("codex_donation_policy"), sources).entrySet()) {
            var row = new LinkedHashMap<>(e.getValue().get(0).row);
            long blocked = 0;
            for (var c : e.getValue()) blocked |= l(c.row, "blocked") != 0 ? 1 : 0;
            row.put("blocked", blocked);
            plan.table("codex_donation_policy").add(row);
        }
    }

    static int questRank(String status) {
        return switch (status == null ? "" : status) { case "claimed" -> 3; case "active" -> 2; case "ready" -> 1; default -> 0; };
    }
    static long progressSum(String progress) {
        long total = 0;
        if (progress == null) return 0;
        for (String p : progress.split(",")) try { total += Long.parseLong(p.trim()); } catch (NumberFormatException ignored) { }
        return total;
    }

    /** 카탈로그(의뢰·대화 정의)는 대상이 비어 있을 때만 첫 원본 것을 넣는다. 다른 원본과 다르면 보고한다. */
    static void catalog(List<Src> sources, Plan plan, Table table, String what) {
        Src base = null;
        for (var src : sources) if (!src.of(table.target).isEmpty()) { base = src; break; }
        if (base == null) return;
        plan.table(table.target).addAll(base.of(table.target));
        var reference = new TreeMap<String, String>();
        for (var r : base.of(table.target)) reference.put(keyOf(table, r), String.valueOf(r.get(table.columns.get(table.columns.size() - 1))));
        for (var src : sources) {
            if (src == base || src.of(table.target).isEmpty()) continue;
            var other = new TreeMap<String, String>();
            for (var r : src.of(table.target)) other.put(keyOf(table, r), String.valueOf(r.get(table.columns.get(table.columns.size() - 1))));
            if (!other.equals(reference)) plan.notes.add(what + " 카탈로그가 " + base.label + "와 " + src.label + "에서 다름: " + base.label + " 것을 씀 (대상 표가 비어 있을 때만). " + src.label + "에만 있는 ID: " + diff(other.keySet(), reference.keySet()));
        }
    }
    static String diff(Set<String> a, Set<String> b) {
        var only = new TreeSet<String>();
        for (String k : a) if (!b.contains(k)) only.add(k.replace("\u0001", ""));
        return only.isEmpty() ? "(없음, 내용만 다름)" : String.join(", ", only);
    }

    /** 같은 (플레이어, 의뢰, 주기): 지급 중(paying) 기록이 있으면 그것(관리자 확인 필요) → 완료 횟수 많은 것 → 상태(claimed > active > ready) → 진행도 합 → 갱신 시각 → 우선 원본. */
    static void mergeQuest(List<Src> sources, Plan plan) {
        var table = Domain.QUEST.table("codex_quest_progress");
        Comparator<Cand> order = Comparator
                .comparing((Cand c) -> !"paying".equals(s(c.row, "status")))
                .thenComparing(Comparator.comparingLong((Cand c) -> l(c.row, "completions")).reversed())
                .thenComparing(Comparator.comparingInt((Cand c) -> questRank(s(c.row, "status"))).reversed())
                .thenComparing(Comparator.comparingLong((Cand c) -> progressSum(s(c.row, "progress"))).reversed())
                .thenComparing(Comparator.comparingLong((Cand c) -> l(c.row, "updated")).reversed())
                .thenComparingInt(c -> c.source);
        int paying = 0;
        for (var e : group(table, sources).entrySet()) {
            var cands = new ArrayList<>(e.getValue());
            cands.sort(order);
            var keep = cands.get(0);
            if ("paying".equals(s(keep.row, "status"))) paying++;
            plan.table(table.target).add(keep.row);
            for (var c : cands.subList(1, cands.size())) if (!sameValues(table, c.row, keep.row))
                plan.conflicts.add("의뢰 진행 다름: " + s(keep.row, "player") + " " + s(keep.row, "quest") + "(" + s(keep.row, "cycle") + ") → " + where(sources, keep) + " [" + s(keep.row, "status") + ", 완료 " + l(keep.row, "completions") + ", " + s(keep.row, "progress") + "] 유지, "
                        + where(sources, c) + " [" + s(c.row, "status") + ", 완료 " + l(c.row, "completions") + ", " + s(c.row, "progress") + "] 버림");
        }
        if (paying > 0) plan.notes.add("보상 지급 중(paying)으로 남은 의뢰 " + paying + "건: 이관 후 /유저관리 <닉네임> 퀘스트 에서 관리자 확인 필요");
        catalog(sources, plan, Domain.QUEST.table("codex_quest_catalog"), "의뢰");
    }

    static Map<String, String> decodeStory(String content) {
        var out = new TreeMap<String, String>();
        if (content == null) return out;
        content.lines().forEach(line -> { int i = line.indexOf('='); if (i > 0) out.put(line.substring(0, i), line.substring(i + 1)); });
        return out;
    }
    static String encodeStory(Map<String, String> values) {
        return String.join("\n", new TreeMap<>(values).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }
    static int journalRank(String v) { return switch (v == null ? "" : v) { case "claimed" -> 3; case "active" -> 2; case "hidden" -> 1; default -> 0; }; }
    static int effectRank(String status) { return switch (status == null ? "" : status) { case "running", "review", "pending" -> 3; case "queued" -> 2; case "done" -> 1; default -> 0; }; }

    /**
     * 스토리 상태는 키마다 병합: 한쪽에만 있으면 그 값, 다르면 journal.*은 claimed > active > hidden, 둘 다 숫자면 큰 값,
     * 그 밖에는 그 플레이어의 revision(진행 횟수)이 큰 원본(같으면 우선 원본). 대화 외부 동작 기록은 끝나지 않은 쪽(running/review/pending > queued > done)을 남긴다.
     */
    static void mergeDialogue(List<Src> sources, Plan plan) {
        var states = Domain.DIALOGUE.table("codex_story_state");
        for (var e : group(states, sources).entrySet()) {
            var cands = e.getValue();
            String player = s(cands.get(0).row, "player");
            var merged = new TreeMap<String, String>();
            var from = new HashMap<String, Cand>();
            long revision = 0;
            for (var c : cands) revision = Math.max(revision, l(c.row, "revision"));
            for (var c : cands) {
                for (var kv : decodeStory(s(c.row, "content")).entrySet()) {
                    String k = kv.getKey(), v = kv.getValue(), old = merged.get(k);
                    if (old == null) { merged.put(k, v); from.put(k, c); continue; }
                    if (old.equals(v)) continue;
                    var prev = from.get(k);
                    String chosen;
                    if (k.startsWith("journal.")) chosen = journalRank(v) > journalRank(old) ? v : old;
                    else {
                        Long a = number(old), b = number(v);
                        if (a != null && b != null) chosen = b > a ? v : old;
                        else chosen = l(c.row, "revision") > l(prev.row, "revision") ? v : old;
                    }
                    if (chosen.equals(v)) { merged.put(k, v); from.put(k, c); }
                    plan.conflicts.add("스토리 상태 다름: " + player + " " + k + " = '" + old + "'(" + where(sources, prev) + ") / '" + v + "'(" + where(sources, c) + ") → '" + chosen + "'");
                }
            }
            if (merged.size() > STORY_LIMIT) plan.conflicts.add("스토리 상태 한도(" + STORY_LIMIT + ") 초과: " + player + " = " + merged.size() + "개. 다음 대화 진행이 막히므로 관리자 정리 필요");
            var row = new LinkedHashMap<String, Object>();
            row.put("player", player);
            row.put("revision", revision);
            row.put("content", encodeStory(merged));
            plan.table(states.target).add(row);
        }
        var effects = Domain.DIALOGUE.table("codex_dialogue_effect");
        int open = 0;
        for (var e : group(effects, sources).entrySet()) {
            var cands = new ArrayList<>(e.getValue());
            cands.sort(Comparator.comparingInt((Cand c) -> effectRank(s(c.row, "status"))).reversed().thenComparingInt(c -> c.source));
            var keep = cands.get(0);
            if (effectRank(s(keep.row, "status")) >= 2) open++;
            plan.table(effects.target).add(keep.row);
            for (var c : cands.subList(1, cands.size())) if (!sameValues(effects, c.row, keep.row))
                plan.conflicts.add("대화 실행 기록 다름: " + s(keep.row, "player") + " " + s(keep.row, "effect_key") + " → " + s(keep.row, "status") + "(" + where(sources, keep) + ") 유지, " + s(c.row, "status") + "(" + where(sources, c) + ") 버림");
        }
        if (open > 0) plan.notes.add("끝나지 않은 대화 실행 기록 " + open + "건 (queued는 다음 접속 때 이어서 실행, running/review/pending은 관리자 확인)");
        catalog(sources, plan, Domain.DIALOGUE.table("codex_dialogue_catalog"), "대화");
    }
    static Long number(String v) { try { return Long.parseLong(v.trim()); } catch (RuntimeException e) { return null; } }

    /** 호감도: 갱신 시각(updated)이 가장 최근인 행(같으면 우선 원본), 하트 단계는 최댓값. 하루 한도 기록은 큰 값. 선물 기록은 토큰 합집합. */
    static void mergeAffinity(List<Src> sources, Plan plan) {
        var table = Domain.AFFINITY.table("codex_affinity");
        for (var e : group(table, sources).entrySet()) {
            var cands = new ArrayList<>(e.getValue());
            cands.sort(Comparator.comparingLong((Cand c) -> l(c.row, "updated")).reversed().thenComparingInt(c -> c.source));
            var keep = cands.get(0);
            var row = new LinkedHashMap<>(keep.row);
            long heart = 0;
            for (var c : cands) heart = Math.max(heart, l(c.row, "heart"));
            row.put("heart", heart);
            plan.table(table.target).add(row);
            for (var c : cands.subList(1, cands.size())) if (l(c.row, "score") != l(keep.row, "score") || l(c.row, "heart") != l(keep.row, "heart"))
                plan.conflicts.add("호감도 다름: " + s(row, "owner") + " " + s(row, "npc") + " → " + where(sources, keep) + " [점수 " + l(keep.row, "score") + ", 하트 " + l(keep.row, "heart") + "] 유지(하트는 " + heart + "), "
                        + where(sources, c) + " [점수 " + l(c.row, "score") + ", 하트 " + l(c.row, "heart") + "] 버림");
        }
        for (var e : group(Domain.AFFINITY.table("codex_affinity_daily"), sources).entrySet()) {
            var best = e.getValue().get(0).row;
            for (var c : e.getValue()) if (l(c.row, "amount") > l(best, "amount")) best = c.row;
            plan.table("codex_affinity_daily").add(best);
        }
        int pending = 0;
        for (var e : group(Domain.AFFINITY.table("codex_affinity_gift"), sources).entrySet()) {
            var row = e.getValue().get(0).row;
            if ("pending".equals(s(row, "status"))) pending++;
            plan.table("codex_affinity_gift").add(row);
        }
        if (pending > 0) plan.notes.add("미확정 NPC 선물 " + pending + "건 (서버 시작 시 기존 복구 로직이 확정 처리)");
    }

    // ------------------------------------------------------------------ 읽기

    static boolean missingTable(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return e.getErrorCode() == 1146 || "42S02".equals(e.getSQLState()) || m.contains("no such table");
    }

    /** SELECT * 후 스펙 열만 꺼낸다. 없는 열은 기본값. 표가 없으면 null. */
    static List<Map<String, Object>> read(Connection db, String name, Table spec, String order) throws SQLException {
        var out = new ArrayList<Map<String, Object>>();
        try (var st = db.createStatement(); ResultSet r = st.executeQuery("SELECT * FROM " + name + (order.isEmpty() ? "" : " ORDER BY " + order))) {
            var meta = r.getMetaData();
            var index = new HashMap<String, Integer>();
            for (int i = 1; i <= meta.getColumnCount(); i++) index.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
            for (String c : spec.columns) if (!index.containsKey(c) && !spec.defaults.containsKey(c)) throw new SQLException(name + " 표에 " + c + " 열이 없습니다");
            while (r.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (String c : spec.columns) row.put(c, index.containsKey(c) ? norm(r.getObject(index.get(c))) : spec.defaults.get(c));
                out.add(row);
            }
        } catch (SQLException e) {
            if (missingTable(e)) return null;
            throw e;
        }
        return out;
    }

    static Connection sqlite(Path file) throws Exception {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** 원본 폴더의 donation-spells.yml에서 마법별 점수(points, 기본 1)를 읽는다. 없으면 null. */
    static Map<String, Long> donationPoints(Path dir) throws IOException {
        Path file = dir.resolve("donation-spells.yml");
        if (!Files.exists(file)) return null;
        var out = new HashMap<String, Long>();
        boolean inSpells = false;
        String current = null;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String bare = line.replaceAll("\\s+#.*$", "");
            if (bare.isBlank() || bare.stripLeading().startsWith("#")) continue;
            if (!bare.startsWith(" ")) { inSpells = bare.trim().equals("spells:"); current = null; continue; }
            if (!inSpells) continue;
            var id = java.util.regex.Pattern.compile("^ {2}([a-z0-9_-]{1,64}):\\s*$").matcher(bare);
            if (id.matches()) { current = id.group(1); out.put(current, 1L); continue; }
            var pts = java.util.regex.Pattern.compile("^ {4}points:\\s*(-?\\d+)\\s*$").matcher(bare);
            if (pts.matches() && current != null) out.put(current, Long.parseLong(pts.group(1)));
        }
        return out;
    }

    // ------------------------------------------------------------------ 실행

    record Options(List<String[]> sources, Path target, boolean apply, boolean mergeExisting, List<Domain> domains, Path report, boolean noTarget) {}

    static Options parse(String[] args) {
        var sources = new ArrayList<String[]>();
        Path target = null, report = null;
        boolean apply = false, merge = false, noTarget = false;
        List<Domain> domains = List.of(Domain.values());
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--source" -> {
                    String v = args[++i];
                    int eq = v.indexOf('=');
                    if (eq <= 0) throw new IllegalArgumentException("--source 라벨=폴더 형식이어야 합니다: " + v);
                    String label = v.substring(0, eq);
                    if (!label.matches("[a-z0-9_-]{1,32}") || label.equals(EXISTING)) throw new IllegalArgumentException("원본 라벨은 영문 소문자·숫자 32자 이하: " + label);
                    for (var s : sources) if (s[0].equals(label)) throw new IllegalArgumentException("원본 라벨 중복: " + label);
                    sources.add(new String[]{label, v.substring(eq + 1)});
                }
                case "--target" -> target = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--apply" -> apply = true;
                case "--merge-into-existing" -> merge = true;
                case "--no-target" -> noTarget = true;
                case "--domains" -> { var list = new ArrayList<Domain>(); for (String d : args[++i].split(",")) list.add(Domain.of(d.trim())); domains = List.copyOf(list); }
                default -> throw new IllegalArgumentException("알 수 없는 옵션: " + a);
            }
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("--source 가 하나 이상 필요합니다 (먼저 적은 원본이 우선, 보통 school)");
        if (apply && noTarget) throw new IllegalArgumentException("--apply 와 --no-target 은 함께 쓸 수 없습니다");
        if (!noTarget && target == null) throw new IllegalArgumentException("--target <MariaDB 설정 properties> 가 필요합니다 (대상 없이 원본만 점검하려면 --no-target)");
        return new Options(sources, target, apply, merge, domains, report, noTarget);
    }

    static String checksum(Domain d, Src src) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var t : d.tables) {
            var lines = new ArrayList<String>();
            for (var row : src.of(t.target)) { var b = new StringBuilder(t.target); for (String c : t.columns) b.append('\u0001').append(text(row.get(c))); lines.add(b.toString()); }
            Collections.sort(lines);
            for (String line : lines) digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static int total(Domain d, Src src) { int n = 0; for (var t : d.tables) n += src.of(t.target).size(); return n; }

    /** 대상에 "플레이어 데이터"가 있는지 (카탈로그와 0점 기숙사 점수 행은 제외: 저장소가 시작할 때 만드는 기본 행). */
    static long targetRows(Connection db, Domain d) throws SQLException {
        long n = 0;
        for (var t : d.tables) {
            if (t.mode == Mode.CATALOG) continue;
            String sql = "SELECT COUNT(*) FROM " + t.target + (t.target.equals("codex_house_points") ? " WHERE points<>0" : "");
            try (var st = db.createStatement(); var r = st.executeQuery(sql)) { if (r.next()) n += r.getLong(1); }
            catch (SQLException e) { if (!missingTable(e)) throw e; }
        }
        return n;
    }

    static Map<String, String> markers(Connection db, Domain d) throws SQLException {
        var out = new TreeMap<String, String>();
        try (var p = db.prepareStatement("SELECT source,checksum FROM " + MARKER + " WHERE domain=?")) {
            p.setString(1, d.id);
            try (var r = p.executeQuery()) { while (r.next()) out.put(r.getString(1), r.getString(2)); }
        } catch (SQLException e) { if (!missingTable(e)) throw e; }
        return out;
    }

    /** Bridge jar의 저장소 클래스를 대상 설정으로 한 번 만들어 표·인덱스·추가 열을 플러그인과 똑같이 만든다. */
    static void ensureSchema(Domain d, DatabaseSettings settings) throws Exception {
        Class<?> type;
        try { type = Class.forName("school.magiccodex.paper." + d.store); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("magic-codex-bridge jar가 classpath에 없습니다 (" + d.store + ")", e); }
        Path unused = Path.of("unused-" + d.file);
        var ctor = d.pathFirst ? type.getDeclaredConstructor(Path.class, DatabaseSettings.class) : type.getDeclaredConstructor(DatabaseSettings.class, Path.class);
        ctor.setAccessible(true);
        Object store;
        try { store = d.pathFirst ? ctor.newInstance(unused, settings) : ctor.newInstance(settings, unused); }
        catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause() instanceof Exception x ? x : e; }
        ((AutoCloseable) store).close();
    }

    static void createMarker(Connection db) throws SQLException {
        try (var st = db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + MARKER + "(domain VARCHAR(32) NOT NULL,source VARCHAR(32) NOT NULL,checksum CHAR(64) NOT NULL,row_count INT NOT NULL,applied_at BIGINT NOT NULL,PRIMARY KEY(domain,source)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    static int write(Connection db, Table t, List<Map<String, Object>> rows, List<String> notes) throws SQLException {
        if (rows.isEmpty()) return 0;
        String columns = String.join(",", t.columns), marks = String.join(",", Collections.nCopies(t.columns.size(), "?"));
        String sql;
        switch (t.mode) {
            case UPSERT -> {
                var sets = new ArrayList<String>();
                for (String c : t.values()) sets.add(c + "=VALUES(" + c + ")");
                sql = "INSERT INTO " + t.target + "(" + columns + ") VALUES(" + marks + ")" + (sets.isEmpty() ? "" : " ON DUPLICATE KEY UPDATE " + String.join(",", sets));
            }
            case IGNORE -> sql = "INSERT IGNORE INTO " + t.target + "(" + columns + ") VALUES(" + marks + ")";
            default -> {
                try (var st = db.createStatement(); var r = st.executeQuery("SELECT COUNT(*) FROM " + t.target)) {
                    if (r.next() && r.getLong(1) > 0) { notes.add(t.target + ": 대상에 이미 정의가 있어 원본 카탈로그를 넣지 않음"); return 0; }
                }
                sql = "INSERT INTO " + t.target + "(" + columns + ") VALUES(" + marks + ")";
            }
        }
        int n = 0;
        try (var p = db.prepareStatement(sql)) {
            int batch = 0;
            for (var row : rows) {
                for (int i = 0; i < t.columns.size(); i++) { Object v = row.get(t.columns.get(i)); if (v == null) p.setNull(i + 1, java.sql.Types.VARCHAR); else p.setObject(i + 1, v); }
                p.addBatch();
                if (++batch == 500) { p.executeBatch(); batch = 0; }
                n++;
            }
            if (batch > 0) p.executeBatch();
        }
        return n;
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--self-test")) { SelfTest.run(); return; }
        Options o;
        try { o = parse(args); }
        catch (RuntimeException e) { System.err.println("사용법 오류: " + e.getMessage()); System.err.println(USAGE); System.exit(64); return; }
        var out = new StringBuilder();
        int status;
        try { status = run(o, out); }
        catch (Exception e) {
            out.append("\n**실패:** ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            status = 1;
        }
        System.out.println(out);
        if (o.report != null) {
            try { Files.writeString(o.report, out.toString(), StandardCharsets.UTF_8); System.out.println("보고서 저장: " + o.report.toAbsolutePath()); }
            catch (IOException e) { System.err.println("보고서 저장 실패: " + e.getMessage()); status = Math.max(status, 1); }
        }
        System.exit(status);
    }

    static final String USAGE = """
            java -cp <magic-codex-bridge.jar>:<sqlite-jdbc.jar> GenericDomainMigration.java
              --source school=<school MagicCodexBridge 폴더 사본> --source wild=<...> [--source arena=<...>]
              --target <MariaDB properties (database.properties 또는 player-state-database.properties)> | --no-target
              [--apply] [--merge-into-existing] [--domains nickname,friend,title,school,quest,dialogue,affinity] [--report 보고서.md]
            java -cp ... GenericDomainMigration.java --self-test""";

    static int run(Options o, StringBuilder out) throws Exception {
        long now = System.currentTimeMillis();
        DatabaseSettings settings = null;
        if (!o.noTarget) {
            settings = DatabaseSettings.load(o.target);
            if (!settings.mariaDb()) throw new IllegalStateException("대상 설정이 MariaDB가 아닙니다: " + o.target.getFileName());
        }
        out.append("# 일반 DB 도메인 이관 보고서\n\n");
        out.append("- 실행 시각: ").append(LocalDateTime.now().withNano(0)).append('\n');
        out.append("- 모드: ").append(o.apply ? "**적용(--apply)**" : "점검만(dry-run, 아무것도 쓰지 않음)").append(o.mergeExisting ? " · 기존 데이터와 병합 허용(--merge-into-existing)" : "").append('\n');
        out.append("- 대상: ").append(settings == null ? "(없음, --no-target)" : settings.target() + " (" + o.target.getFileName() + ")").append('\n');
        out.append("- 원본(우선순위 순): ");
        for (int i = 0; i < o.sources.size(); i++) out.append(i == 0 ? "" : " > ").append(o.sources.get(i)[0]).append(" = ").append(o.sources.get(i)[1]);
        out.append("\n\n");
        out.append(RULES).append('\n');
        Connection target = settings == null ? null : settings.connect(Path.of("unused.sqlite"));
        int status = 0;
        boolean locked = false;
        try {
            if (target != null && o.apply) {
                try (var p = target.prepareStatement("SELECT GET_LOCK('codex_generic_migration',10)"); var r = p.executeQuery()) { locked = r.next() && r.getInt(1) == 1; }
                if (!locked) throw new IllegalStateException("다른 이관 도구가 실행 중입니다 (GET_LOCK 실패)");
                createMarker(target);
            }
            for (var d : o.domains) {
                out.append("## ").append(d.label).append(" (").append(d.id).append(")\n\n");
                try { status = Math.max(status, domain(o, d, settings, target, now, out)); }
                catch (Exception e) {
                    out.append("- **실패**: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append(" (이 도메인은 롤백됨)\n\n");
                    status = Math.max(status, 1);
                }
            }
        } finally {
            if (target != null) {
                if (locked) try (var p = target.prepareStatement("SELECT RELEASE_LOCK('codex_generic_migration')"); var r = p.executeQuery()) { r.next(); } catch (SQLException ignored) { }
                target.close();
            }
        }
        out.append("---\n결과: ").append(status == 0 ? (o.apply ? "적용 완료" : "점검 완료 (적용하려면 --apply)") : status == 2 ? "일부 도메인 거부됨 (위 '거부' 확인)" : "실패 있음").append('\n');
        return status;
    }

    static int domain(Options o, Domain d, DatabaseSettings settings, Connection target, long now, StringBuilder out) throws Exception {
        // 1) 원본 읽기
        var loaded = new ArrayList<Src>();
        var sums = new LinkedHashMap<String, String>();
        for (String[] s : o.sources) {
            Path dir = Path.of(s[1]), file = dir.resolve(d.file);
            if (!Files.exists(file)) { out.append("- ").append(s[0]).append(": ").append(d.file).append(" 없음 → 건너뜀\n"); continue; }
            var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
            try (var db = sqlite(file)) {
                for (var t : d.tables) {
                    var list = read(db, t.sqlite, t, t.sqliteOrder);
                    if (list == null) { out.append("- ").append(s[0]).append(": ").append(t.sqlite).append(" 표 없음 (0행으로 봄)\n"); list = List.of(); }
                    rows.put(t.target, list);
                }
            }
            var src = new Src(s[0], rows, d == Domain.SCHOOL ? donationPoints(dir) : null);
            loaded.add(src);
            sums.put(s[0], checksum(d, src));
            var counts = new StringBuilder();
            for (var t : d.tables) counts.append(t.sqlite).append(' ').append(src.of(t.target).size()).append(" · ");
            out.append("- ").append(s[0]).append(" (").append(file.getFileName()).append(", ").append(Files.size(file)).append(" bytes): ").append(counts.substring(0, counts.length() - 3)).append('\n');
        }
        if (loaded.isEmpty()) { out.append("- 원본 파일이 하나도 없어 건너뜀\n\n"); return 0; }

        // 2) 대상 상태와 이관 기록
        long existing = 0;
        Map<String, String> done = Map.of();
        if (target != null) {
            if (o.apply) ensureSchema(d, settings);
            existing = targetRows(target, d);
            done = markers(target, d);
            out.append("- 대상 기존 플레이어 데이터: ").append(existing).append("행").append(done.isEmpty() ? "" : " · 이전 이관 기록: " + String.join(", ", done.keySet())).append('\n');
        }
        var fresh = new ArrayList<Src>();
        for (var src : loaded) {
            String previous = done.get(src.label);
            if (previous == null) fresh.add(src);
            else if (previous.equals(sums.get(src.label))) out.append("- ").append(src.label).append(": 이미 이관됨(같은 체크섬) → 다시 넣지 않음\n");
            else { out.append("- **거부**: ").append(src.label).append("는 예전에 다른 내용으로 이관됨 (체크섬 불일치). 원본이 바뀌었거나 다른 사본입니다. 관리자 판단 필요\n\n"); return 2; }
        }
        if (fresh.isEmpty()) { out.append("- 새로 넣을 원본 없음 (완료)\n\n"); return 0; }
        if (existing > 0 && !o.mergeExisting) {
            out.append(o.apply ? "- **거부**: " : "- **적용 시 거부됨**: ").append("대상 표에 이미 플레이어 데이터가 ").append(existing).append("행 있습니다. 확인 후 --merge-into-existing 으로 다시 실행하세요 (기존 DB 데이터가 최우선)\n\n");
            return 2;
        }

        // 3) 병합 (기존 DB가 있으면 최우선 원본으로 넣는다)
        var sources = new ArrayList<Src>();
        if (existing > 0 && target != null) {
            var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
            for (var t : d.tables) { var list = read(target, t.target, t, t.targetOrder); rows.put(t.target, list == null ? List.of() : list); }
            sources.add(new Src(EXISTING, rows, null));
            out.append("- 기존 DB 데이터를 최우선 원본(").append(EXISTING).append(")으로 병합\n");
        }
        sources.addAll(fresh);
        var plan = merge(d, sources, now);
        out.append("- 병합 결과: ");
        var parts = new ArrayList<String>();
        for (var t : d.tables) parts.add(t.target + " " + plan.rows.getOrDefault(t.target, List.of()).size());
        out.append(String.join(" · ", parts)).append('\n');
        out.append("- 충돌 ").append(plan.conflicts.size()).append("건\n");
        for (String n : plan.notes) out.append("  - ").append(n).append('\n');
        for (String c : plan.conflicts) out.append("  - 충돌: ").append(c).append('\n');

        // 4) 적용: 도메인당 트랜잭션 하나
        if (!o.apply || target == null) { out.append('\n'); return 0; }
        target.setAutoCommit(false);
        try {
            var notes = new ArrayList<String>();
            var written = new ArrayList<String>();
            for (var t : d.tables) written.add(t.target + " " + write(target, t, plan.rows.getOrDefault(t.target, List.of()), notes));
            try (var p = target.prepareStatement("INSERT INTO " + MARKER + "(domain,source,checksum,row_count,applied_at) VALUES(?,?,?,?,?)")) {
                for (var src : fresh) { p.setString(1, d.id); p.setString(2, src.label); p.setString(3, sums.get(src.label)); p.setInt(4, total(d, src)); p.setLong(5, now); p.addBatch(); }
                p.executeBatch();
            }
            target.commit();
            out.append("- **적용됨**: ").append(String.join(" · ", written)).append('\n');
            for (String n : notes) out.append("  - ").append(n).append('\n');
        } catch (Exception e) {
            target.rollback();
            throw e;
        } finally {
            target.setAutoCommit(true);
        }
        out.append('\n');
        return 0;
    }

    static final String RULES = """
            ### 병합 규칙
            - 우선순위: `--merge-into-existing`일 때 기존 DB > 첫 번째 --source(보통 school) > 그다음 원본.
            - 닉네임: 같은 UUID에 다른 닉네임이면 수정 시각 기록이 없어 우선 원본이 이김(보고). 같은 닉네임(NFKC+소문자 키)을 다른 UUID가 쓰면 가장 먼저 저장된 쪽(우선 원본 → 원본 안 저장 순서)을 남기고, 나머지는 닉네임은 그대로·키는 비워 둠 → 관리자가 `/유저관리 <계정명> 닉네임 설정 <새닉네임>`. first_done은 OR.
            - 친구: 관계 합집합. 친구 신청 합집합(같은 쌍은 최근 것), 7일 지난 신청과 이미 서로 친구인 쌍의 신청은 제외. 재신청 대기는 큰 값. 첫 친구 신호는 OR.
            - 칭호: 보유 합집합. 선택(앞·뒤 칭호)은 우선 원본.
            - 기숙사: 배정 시각 기록이 없어 우선 원본(보고). 기부는 마법마다 가장 이른 기부(created). 기숙사 점수 = 원본별 점수 합 − 탈락한 중복 기부 점수(그 원본의 donation-spells.yml 기준). 기증 금지는 OR.
            - 의뢰: 같은 (플레이어·의뢰·주기)면 지급 중(paying) > 완료 횟수 > 상태(claimed>active>ready) > 진행도 > 갱신 시각.
            - 대화: 스토리 상태는 키마다 병합(journal은 claimed>active>hidden, 숫자는 큰 값, 그 밖에는 진행 횟수가 많은 쪽). 실행 기록은 끝나지 않은 쪽을 남김.
            - 호감도: 최근 갱신(updated) 행, 하트 단계는 최댓값. 하루 한도 기록은 큰 값. 선물 기록은 합집합.
            - 의뢰·대화 카탈로그는 대상이 비어 있을 때만 첫 원본 것을 넣음.
            - 같은 원본을 두 번 넣지 않음: codex_generic_migration(domain, source, checksum)에 기록. 같은 라벨이 다른 내용으로 다시 오면 거부.
            """;

    // ------------------------------------------------------------------ 자체 점검 (DB 없이 병합 규칙만)

    static final class SelfTest {
        static int failures;
        static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("실패: " + what); } else System.out.println("통과: " + what); }
        static Map<String, Object> row(Object... kv) { var m = new LinkedHashMap<String, Object>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]); return m; }
        static Src src(String label, Map<String, List<Map<String, Object>>> rows) { return new Src(label, rows, null); }
        static void run() {
            long now = 10 * DAY;
            String a = "00000000-0000-0000-0000-00000000000a", b = "00000000-0000-0000-0000-00000000000b", c = "00000000-0000-0000-0000-00000000000c";
            // 닉네임
            var school = src("school", Map.of("codex_nicknames", List.of(
                    row("owner", a, "account", "Alpha", "nickname", "하늘", "nickname_key", "하늘", "revision", 2L, "first_done", 0L),
                    row("owner", b, "account", "Beta", "nickname", "Moon", "nickname_key", "moon", "revision", 1L, "first_done", 1L))));
            var wild = src("wild", Map.of("codex_nicknames", List.of(
                    row("owner", a, "account", "Alpha", "nickname", "바다", "nickname_key", "바다", "revision", 5L, "first_done", 1L),
                    row("owner", c, "account", "Gamma", "nickname", "ＭＯＯＮ", "nickname_key", "moon", "revision", 1L, "first_done", 1L))));
            var plan = merge(Domain.NICKNAME, List.of(school, wild), now);
            var nick = new HashMap<String, Map<String, Object>>();
            for (var r : plan.rows.get("codex_nicknames")) nick.put(s(r, "owner"), r);
            check(nick.size() == 3, "닉네임 3명");
            check("하늘".equals(s(nick.get(a), "nickname")) && l(nick.get(a), "first_done") == 1 && l(nick.get(a), "revision") == 5, "같은 UUID: school 닉네임, first_done OR, revision 최댓값");
            check("moon".equals(s(nick.get(b), "nickname_key")) && nick.get(c).get("nickname_key") == null, "중복 닉네임: school 유지, wild 키 비움");
            check(plan.conflicts.size() == 2, "닉네임 충돌 2건 보고");
            // 친구
            var fs = src("school", Map.of("codex_friends", List.of(row("owner", a, "target", b, "name", "Moon", "dorm", "x"), row("owner", b, "target", a, "name", "하늘", "dorm", "y")),
                    "codex_friend_requests", List.of(row("sender", a, "target", b, "name", "n", "dorm", "d", "target_name", "t", "target_dorm", "td", "created", now - DAY)),
                    "codex_first_friend_signal", List.of(row("owner", a, "delivered", 0L))));
            var fw = src("wild", Map.of("codex_friends", List.of(row("owner", a, "target", c, "name", "G", "dorm", "z"), row("owner", a, "target", b, "name", "Moon2", "dorm", "x")),
                    "codex_friend_requests", List.of(row("sender", c, "target", b, "name", "n", "dorm", "d", "target_name", "t", "target_dorm", "td", "created", now - 8 * DAY),
                            row("sender", b, "target", c, "name", "n", "dorm", "d", "target_name", "t", "target_dorm", "td", "created", now - DAY)),
                    "codex_first_friend_signal", List.of(row("owner", a, "delivered", 1L))));
            plan = merge(Domain.FRIEND, List.of(fs, fw), now);
            check(plan.rows.get("codex_friends").size() == 3, "친구 관계 합집합 3행");
            check("Moon".equals(s(plan.rows.get("codex_friends").get(0), "name")), "친구 표시 이름은 우선 원본");
            check(plan.rows.get("codex_friend_requests").size() == 1 && s(plan.rows.get("codex_friend_requests").get(0), "sender").equals(b), "만료·이미 친구인 신청 제외");
            check(l(plan.rows.get("codex_first_friend_signal").get(0), "delivered") == 1, "첫 친구 신호 OR");
            // 칭호
            plan = merge(Domain.TITLE, List.of(
                    src("school", Map.of("codex_title_player", List.of(row("player", a, "revision", 3L, "prefix_id", "p1", "suffix_id", "")), "codex_title_owned", List.of(row("player", a, "title_id", "p1")))),
                    src("wild", Map.of("codex_title_player", List.of(row("player", a, "revision", 7L, "prefix_id", "p2", "suffix_id", "")), "codex_title_owned", List.of(row("player", a, "title_id", "p2"), row("player", a, "title_id", "p1"))))), now);
            check(plan.rows.get("codex_title_owned").size() == 2 && "p1".equals(s(plan.rows.get("codex_title_player").get(0), "prefix_id")) && l(plan.rows.get("codex_title_player").get(0), "revision") == 7, "칭호 보유 합집합, 선택은 school");
            // 기숙사
            var ss = new Src("school", Map.of("codex_houses", List.of(row("player", a, "house", 1L)),
                    "codex_donations", List.of(row("spell", "fire", "spell_name", "불", "donor", a, "nickname", "하늘", "house", 1L, "created", 200L)),
                    "codex_house_points", List.of(row("house", 0L, "points", 0L), row("house", 1L, "points", 10L), row("house", 2L, "points", 0L), row("house", 3L, "points", 0L))), Map.of("fire", 3L));
            var sw = new Src("wild", Map.of("codex_houses", List.of(row("player", a, "house", 2L), row("player", b, "house", 0L)),
                    "codex_donations", List.of(row("spell", "fire", "spell_name", "불", "donor", b, "nickname", "Moon", "house", 0L, "created", 100L)),
                    "codex_house_points", List.of(row("house", 0L, "points", 5L), row("house", 1L, "points", 0L), row("house", 2L, "points", 4L), row("house", 3L, "points", 0L))), Map.of("fire", 3L));
            plan = merge(Domain.SCHOOL, List.of(ss, sw), now);
            var houses = new HashMap<String, Long>();
            for (var r : plan.rows.get("codex_houses")) houses.put(s(r, "player"), l(r, "house"));
            check(houses.get(a) == 1 && houses.get(b) == 0, "기숙사 배정 school 우선");
            check(b.equals(s(plan.rows.get("codex_donations").get(0), "donor")), "기부는 가장 이른 것");
            var pts = plan.rows.get("codex_house_points").stream().mapToLong(r -> l(r, "points")).toArray();
            check(Arrays.equals(pts, new long[]{5, 7, 4, 0}), "기숙사 점수 합 − 탈락 기부 점수 (" + Arrays.toString(pts) + ")");
            // 의뢰
            plan = merge(Domain.QUEST, List.of(
                    src("school", Map.of("codex_quest_progress", List.of(row("player", a, "quest", "q", "cycle", "once", "definition", "d", "status", "active", "progress", "1,0", "token", "", "updated", 5L, "completions", 0L, "stage", 0L)))),
                    src("wild", Map.of("codex_quest_progress", List.of(row("player", a, "quest", "q", "cycle", "once", "definition", "d", "status", "claimed", "progress", "2,2", "token", "", "updated", 1L, "completions", 1L, "stage", 0L))))), now);
            check("claimed".equals(s(plan.rows.get("codex_quest_progress").get(0), "status")), "의뢰는 더 진행된 쪽");
            // 대화
            plan = merge(Domain.DIALOGUE, List.of(
                    src("school", Map.of("codex_story_state", List.of(row("player", a, "revision", 2L, "content", "journal.ch1=active\nstory.ch1=2\nflag.x=a")))),
                    src("wild", Map.of("codex_story_state", List.of(row("player", a, "revision", 9L, "content", "journal.ch1=claimed\nstory.ch1=1\nflag.x=b\nflag.y=1"))))), now);
            var story = decodeStory(s(plan.rows.get("codex_story_state").get(0), "content"));
            check("claimed".equals(story.get("journal.ch1")) && "2".equals(story.get("story.ch1")) && "b".equals(story.get("flag.x")) && "1".equals(story.get("flag.y")), "스토리 상태 키별 병합 " + story);
            // 호감도
            plan = merge(Domain.AFFINITY, List.of(
                    src("school", Map.of("codex_affinity", List.of(row("owner", a, "npc", "n", "score", 30L, "heart", 2L, "nickname", "", "updated", 5L)))),
                    src("wild", Map.of("codex_affinity", List.of(row("owner", a, "npc", "n", "score", 12L, "heart", 1L, "nickname", "", "updated", 9L))))), now);
            var aff = plan.rows.get("codex_affinity").get(0);
            check(l(aff, "score") == 12 && l(aff, "heart") == 2, "호감도 최근 행 + 하트 최댓값");
            check(nicknameKey("ＡＢＣ").equals("abc"), "닉네임 키 정규화");
            System.out.println(failures == 0 ? "자체 점검 통과" : "자체 점검 실패 " + failures + "건");
            if (failures > 0) System.exit(1);
        }
    }
}
