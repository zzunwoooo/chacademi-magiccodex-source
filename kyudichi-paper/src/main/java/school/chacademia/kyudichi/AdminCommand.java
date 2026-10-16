package school.chacademia.kyudichi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /큐디치관리 — arena setup, test matches, match control, user admin. Permission chacademi.kyudichi.admin. */
final class AdminCommand implements TabExecutor {
    private final KyudichiPlugin plugin;
    AdminCommand(KyudichiPlugin plugin) { this.plugin = plugin; }

    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }
    private static void info(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GRAY)); }
    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        try {
            if (a.length == 0) { help(s); return true; }
            switch (a[0]) {
                case "막대" -> player(s).getInventory().addItem(plugin.selections().wand());
                case "pos1", "pos2" -> plugin.selections().set(player(s), a[0].equals("pos1") ? 0 : 1, player(s).getLocation());
                case "선택" -> {
                    if (a.length >= 2 && a[1].equals("해제")) { plugin.selections().clear(player(s)); ok(s, "선택을 지웠어요."); }
                    else if (a.length >= 4 && a[1].equals("확장")) info(s, plugin.selections().expand(player(s), a[2], Integer.parseInt(a[3])));
                    else err(s, "/큐디치관리 선택 확장 <위|아래|사방> <칸> · 선택 해제");
                }
                case "경기장" -> arena(s, a);
                case "테스트" -> test(s, a);
                case "공테스트" -> {
                    if (a.length < 3) { err(s, "/큐디치관리 공테스트 <경기장> 시작|끝"); return true; }
                    if (a[2].equals("끝")) endIn(s, a[1]);
                    else test(s, new String[]{"테스트", a[1], "준비:0", "스니치:0", "무적"});
                }
                case "경기" -> match(s, a);
                case "대기열" -> info(s, "대기열은 Velocity 프록시가 관리해요 (/큐디치 대기열 · /큐디치 신청). 매칭 없이 직접 열려면 /큐디치관리 경기 시작");
                case "리로드" -> { plugin.reload(); ok(s, "config.yml · arenas.yml 다시 읽음 (진행 중인 경기는 이전 규칙 유지)"); }
                case "유저" -> user(s, a);
                default -> help(s);
            }
        } catch (IllegalArgumentException e) { err(s, e.getMessage() == null ? "잘못된 입력" : e.getMessage()); }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        throw new IllegalArgumentException("게임 안에서만 쓸 수 있어요.");
    }

    private void help(CommandSender s) {
        for (String line : List.of(
                "/큐디치관리 막대 · pos1 · pos2 · 선택 확장 <위|아래|사방> <칸> · 선택 해제",
                "/큐디치관리 경기장 생성|삭제 <이름> · 경기장 목록",
                "/큐디치관리 경기장 <이름> 정보|이동|검사|켜기|끄기|보기",
                "/큐디치관리 경기장 <이름> 경계|공영역|스니치영역 (선택 영역으로)",
                "/큐디치관리 경기장 <이름> 골대 <A|B> 추가|목록|삭제 <번호> · 금지영역 추가|목록|삭제 <번호>",
                "/큐디치관리 경기장 <이름> 시작위치 <A|B> <1-3> · 부활위치 <A|B> <1-3> · 준비위치 <A|B>",
                "/큐디치관리 경기장 <이름> 퀘이플위치 · 블러저위치 <1|2> · 스니치위치 추가|목록|삭제 <번호>",
                "/큐디치관리 경기장 <이름> 성소 <체력|마나|안정성|가속> 추가 · 성소 목록 · 성소 <종류> 삭제 <번호> · 연습용 켜기|끄기",
                "/큐디치관리 테스트 <경기장> [준비:초] [시간:분] [스니치:초] [무적] [블러저:끔] · 테스트 참가 <경기장> <A|B> · 테스트 종료 [경기장]",
                "/큐디치관리 공테스트 <경기장> 시작|끝",
                "/큐디치관리 경기 목록 · 경기 시작 <경기장> <파랑:닉,닉,닉> <빨강:닉,닉,닉> [레이팅] · 경기 <경기장> 강제종료",
                "/큐디치관리 유저 <닉> 정보|레이팅 설정 <값>|레이팅 추가 <±값>|레이팅 초기화|전적 초기화|퇴장|금지 <1h|1d|영구|해제>",
                "/큐디치관리 리로드")) info(s, line);
    }

    // ================================================================== arenas
    private void arena(CommandSender s, String[] a) {
        if (a.length < 2) { err(s, "/큐디치관리 경기장 생성|삭제|목록|<이름> ..."); return; }
        var store = plugin.arenas();
        switch (a[1]) {
            case "생성" -> {
                need(a, 3); if (!ArenaStore.valid(a[2])) throw new IllegalArgumentException("이름은 한글·영문·숫자·_- 32자까지");
                if (store.get(a[2]) != null) throw new IllegalArgumentException("이미 있는 경기장이에요.");
                store.create(a[2]); ok(s, "경기장 " + a[2] + " 생성. 이제 경계·골대·시작위치 등을 지정하세요.");
                return;
            }
            case "삭제" -> {
                need(a, 3);
                if (plugin.matches().inArena(a[2]) != null) throw new IllegalArgumentException("경기 중인 경기장은 지울 수 없어요.");
                if (store.remove(a[2])) ok(s, "삭제했어요."); else err(s, "없는 경기장이에요.");
                return;
            }
            case "목록" -> {
                if (store.all().isEmpty()) info(s, "경기장이 없어요.");
                for (Arena ar : store.all()) info(s, ar.name + (ar.enabled ? " [켜짐]" : " [꺼짐]") + (ar.practice ? " [연습용]" : "")
                        + (plugin.matches().inArena(ar.name) != null ? " [경기 중]" : "") + (ar.problems().isEmpty() ? "" : " · 빠짐 " + ar.problems().size()));
                return;
            }
            default -> { }
        }
        Arena ar = store.get(a[1]);
        if (ar == null) throw new IllegalArgumentException("없는 경기장: " + a[1]);
        if (a.length < 3) { describe(s, ar); return; }
        String sub = a[2];
        boolean changed = true;
        switch (sub) {
            case "정보" -> { describe(s, ar); changed = false; }
            case "이동" -> {
                changed = false;
                if (ar.bounds == null) throw new IllegalArgumentException("경계가 없어요.");
                var w = Bukkit.getWorld(ar.bounds.world());
                if (w == null) throw new IllegalArgumentException("월드가 없어요.");
                var c = ar.bounds.center(); player(s).teleport(new org.bukkit.Location(w, c.getX(), c.getY(), c.getZ()));
            }
            case "검사" -> {
                changed = false;
                var p = ar.problems();
                if (p.isEmpty()) ok(s, "필수 항목 모두 OK. 켜기로 사용할 수 있어요."); else err(s, "빠진 것: " + String.join(", ", p));
            }
            case "켜기" -> {
                var p = ar.problems();
                if (!p.isEmpty()) throw new IllegalArgumentException("켤 수 없어요. 빠진 것: " + String.join(", ", p));
                ar.enabled = true; ok(s, ar.name + " 켜짐");
            }
            case "끄기" -> { ar.enabled = false; ok(s, ar.name + " 꺼짐"); }
            case "보기" -> { changed = false; ok(s, plugin.selections().toggleShow(player(s), ar.name) ? "표시 켬 (흰 경계, 주황 공영역, 금 스니치영역, 파랑/빨강 골대, 검정 금지영역)" : "표시 끔"); }
            case "연습용" -> { need(a, 4); ar.practice = a[3].equals("켜기"); ok(s, "연습용 " + (ar.practice ? "켬" : "끔")); }
            case "경계" -> { ar.bounds = selection(s); ok(s, "경계 " + ar.bounds.size()); }
            case "공영역" -> { ar.ballArea = selection(s); ok(s, "공영역 " + ar.ballArea.size()); }
            case "스니치영역" -> { ar.snitchArea = selection(s); ok(s, "스니치영역 " + ar.snitchArea.size()); }
            case "골대" -> {
                need(a, 5); Team t = team(a[3]);
                changed = list(s, a, 4, ar.goals.get(t), () -> selection(s), Box::size, t.key + "팀 골대");
            }
            case "금지영역" -> { need(a, 4); changed = list(s, a, 3, ar.forbidden, () -> selection(s), Box::size, "금지영역"); }
            case "시작위치", "부활위치" -> {
                need(a, 5); Team t = team(a[3]); int i = slot(a[4]);
                (sub.equals("시작위치") ? ar.starts : ar.respawns).get(t)[i] = here(s);
                ok(s, t.key + "팀 " + sub + " " + (i + 1) + " 저장 (지금 위치·방향)");
            }
            case "준비위치" -> { need(a, 4); Team t = team(a[3]); ar.prepare.put(t, here(s)); ok(s, t.key + "팀 준비위치 저장"); }
            case "퀘이플위치" -> { ar.quaffle = here(s); ok(s, "퀘이플위치 저장"); }
            case "블러저위치" -> { need(a, 4); int i = Integer.parseInt(a[3]) - 1; if (i < 0 || i > 1) throw new IllegalArgumentException("1 또는 2"); ar.bludgers[i] = here(s); ok(s, "블러저위치 " + (i + 1) + " 저장"); }
            case "스니치위치" -> { need(a, 4); changed = list(s, a, 3, ar.snitchSpawns, () -> here(s), this::fmt, "스니치위치"); }
            case "성소" -> {
                need(a, 4);
                if (a[3].equals("목록")) {
                    changed = false;
                    for (ShrineType t : ShrineType.values()) { var l = ar.shrines.get(t); info(s, t.label + " 성소 " + l.size() + "곳"); for (int i = 0; i < l.size(); i++) info(s, "  " + (i + 1) + ". " + fmt(l.get(i))); }
                } else {
                    ShrineType t = ShrineType.parse(a[3]);
                    if (t == null) throw new IllegalArgumentException("성소 종류: 체력 · 마나 · 안정성 · 가속");
                    need(a, 5);
                    changed = list(s, a, 4, ar.shrines.get(t), () -> here(s), this::fmt, t.label + " 성소");
                }
            }
            default -> { changed = false; err(s, "모르는 항목: " + sub); }
        }
        if (changed) {
            if (ar.enabled && !ar.problems().isEmpty()) { ar.enabled = false; err(s, "필수 항목이 빠져 경기장이 꺼졌어요."); }
            store.save();
        }
    }

    private <T> boolean list(CommandSender s, String[] a, int at, List<T> list, java.util.function.Supplier<T> make, java.util.function.Function<T, String> show, String what) {
        switch (a[at]) {
            case "추가" -> { T v = make.get(); list.add(v); ok(s, what + " " + list.size() + " 추가 (" + show.apply(v) + ")"); return true; }
            case "목록" -> { info(s, what + " " + list.size() + "개"); for (int i = 0; i < list.size(); i++) info(s, "  " + (i + 1) + ". " + show.apply(list.get(i))); return false; }
            case "삭제" -> {
                need(a, at + 2); int i = Integer.parseInt(a[at + 1]) - 1;
                if (i < 0 || i >= list.size()) throw new IllegalArgumentException("번호는 1~" + list.size());
                list.remove(i); ok(s, what + " " + (i + 1) + " 삭제"); return true;
            }
            default -> throw new IllegalArgumentException("추가 · 목록 · 삭제 <번호>");
        }
    }

    private void describe(CommandSender s, Arena ar) {
        info(s, "── 경기장 " + ar.name + (ar.enabled ? " [켜짐]" : " [꺼짐]") + (ar.practice ? " [연습용]" : "") + " ──");
        info(s, "경계 " + (ar.bounds == null ? "없음" : ar.bounds.size()) + " · 공영역 " + (ar.ballArea == null ? "(경계)" : ar.ballArea.size())
                + " · 스니치영역 " + (ar.snitchArea == null ? "(공영역)" : ar.snitchArea.size()));
        for (Team t : Team.values()) {
            long starts = Arrays.stream(ar.starts.get(t)).filter(java.util.Objects::nonNull).count();
            info(s, t.label + " 골대 " + ar.goals.get(t).size() + " · 시작위치 " + starts + "/3");
        }
        info(s, "퀘이플위치 " + (ar.quaffle == null ? "없음" : "있음") + " · 블러저위치 " + Arrays.stream(ar.bludgers).filter(java.util.Objects::nonNull).count() + "/2 · 스니치위치 " + ar.snitchSpawns.size());
        var shr = new StringBuilder();
        for (ShrineType t : ShrineType.values()) shr.append(t.label).append(' ').append(ar.shrines.get(t).size()).append("  ");
        info(s, "성소 후보: " + shr);
        var p = ar.problems();
        if (!p.isEmpty()) err(s, "빠진 것: " + String.join(", ", p));
    }

    private Box selection(CommandSender s) {
        Box b = plugin.selections().box(player(s));
        if (b == null) throw new IllegalArgumentException("먼저 막대로 pos1, pos2를 찍으세요. (/큐디치관리 막대)");
        return b;
    }
    private Point here(CommandSender s) { return Point.of(player(s).getLocation()); }
    private String fmt(Point p) { return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.x(), p.y(), p.z()); }
    private static Team team(String s) { Team t = Team.parse(s); if (t == null) throw new IllegalArgumentException("팀은 A 또는 B"); return t; }
    private static int slot(String s) { int i = Integer.parseInt(s) - 1; if (i < 0 || i > 2) throw new IllegalArgumentException("번호는 1~3"); return i; }
    private static void need(String[] a, int n) { if (a.length < n) throw new IllegalArgumentException("인자가 부족해요. /큐디치관리 로 도움말"); }

    // ================================================================== tests and matches
    private void test(CommandSender s, String[] a) {
        need(a, 2);
        if (a[1].equals("종료")) { endIn(s, a.length > 2 ? a[2] : null); return; }
        if (a[1].equals("참가")) {
            need(a, 4);
            Match m = plugin.matches().inArena(a[2]);
            if (m == null || !m.opt.test()) throw new IllegalArgumentException("그 경기장에서 테스트 경기가 없어요.");
            Player p = player(s); Team t = team(a[3]);
            if (plugin.matches().of(p.getUniqueId()) != null) throw new IllegalArgumentException("이미 경기 중이에요.");
            var mp = m.add(p, t, plugin.store().cached(p.getUniqueId(), p.getName()).rating());
            m.rejoinAs(p, mp);
            ok(s, t.label + "으로 참가");
            return;
        }
        Arena ar = arenaFor(a[1]);
        Rules r = plugin.rules();
        int prep = r.prepareTicks, play = r.playTicks, snitch = r.snitchAtTicks; boolean bl = true; Set<UUID> inv = new HashSet<>();
        Player p = player(s);
        for (int i = 2; i < a.length; i++) {
            String o = a[i];
            if (o.startsWith("준비:")) prep = 20 * Integer.parseInt(o.substring(3));
            else if (o.startsWith("시간:")) play = 1200 * Integer.parseInt(o.substring(3));
            else if (o.startsWith("스니치:")) snitch = 20 * Integer.parseInt(o.substring(4));
            else if (o.equals("무적")) inv.add(p.getUniqueId());
            else if (o.equals("블러저:끔")) bl = false;
            else throw new IllegalArgumentException("옵션: 준비:초 시간:분 스니치:초 무적 블러저:끔");
        }
        var teams = new EnumMap<Team, List<Player>>(Team.class);
        teams.put(Team.A, List.of(p)); teams.put(Team.B, List.of());
        String e = plugin.matches().start(ar, teams, new Match.Options(false, true, Math.max(0, prep), Math.max(200, play), Math.max(0, snitch), bl, inv));
        if (e != null) throw new IllegalArgumentException(e);
        ok(s, "테스트 경기 시작 (" + ar.name + ", 레이팅 미반영). 다른 사람은 /큐디치관리 테스트 참가 " + ar.name + " <A|B>");
    }

    private Arena arenaFor(String name) {
        Arena ar = plugin.arenas().get(name);
        if (ar == null) throw new IllegalArgumentException("없는 경기장: " + name);
        var p = ar.problems();
        if (!p.isEmpty()) throw new IllegalArgumentException("경기장 설정이 덜 됐어요: " + String.join(", ", p));
        return ar;
    }

    private void endIn(CommandSender s, String arena) {
        Match m = arena != null ? plugin.matches().inArena(arena) : s instanceof Player p ? plugin.matches().of(p.getUniqueId()) : null;
        if (m == null) throw new IllegalArgumentException("끝낼 경기가 없어요.");
        m.end(null, m.opt.test() ? Match.EndReason.ADMIN : Match.EndReason.VOID);
        ok(s, "경기 종료" + (m.opt.rated() ? " (무효 처리: 레이팅·기록 미반영)" : ""));
    }

    private void match(CommandSender s, String[] a) {
        need(a, 2);
        if (a[1].equals("목록")) {
            if (plugin.matches().all().isEmpty()) info(s, "진행 중인 경기가 없어요.");
            for (Match m : plugin.matches().all()) {
                var names = new EnumMap<Team, List<String>>(Team.class);
                for (var mp : m.players.values()) names.computeIfAbsent(mp.team, k -> new ArrayList<>()).add(mp.nick);
                info(s, m.arena.name + " · " + m.phase + " · " + m.score[0] + ":" + m.score[1] + " · " + names.getOrDefault(Team.A, List.of()) + " vs "
                        + names.getOrDefault(Team.B, List.of()) + (m.opt.test() ? " [테스트]" : m.opt.rated() ? "" : " [비레이팅]") + " · 관전 " + m.spectators.size());
            }
            return;
        }
        if (a[1].equals("시작")) {
            need(a, 5);
            Arena ar = arenaFor(a[2]);
            var teams = new EnumMap<Team, List<Player>>(Team.class);
            teams.put(Team.A, players(a[3])); teams.put(Team.B, players(a[4]));
            boolean rated = a.length > 5 && a[5].equals("레이팅");
            Rules r = plugin.rules();
            String e = plugin.matches().start(ar, teams, new Match.Options(rated, false, r.prepareTicks, r.playTicks, r.snitchAtTicks, true, Set.of()));
            if (e != null) throw new IllegalArgumentException(e);
            ok(s, "경기 시작" + (rated ? " (레이팅 반영)" : " (레이팅 미반영)"));
            return;
        }
        need(a, 3);
        if (a[2].equals("강제종료")) endIn(s, a[1]);
        else err(s, "/큐디치관리 경기 <경기장> 강제종료");
    }

    private List<Player> players(String arg) {
        String list = arg.contains(":") ? arg.substring(arg.indexOf(':') + 1) : arg;
        var out = new ArrayList<Player>();
        for (String n : list.split(",")) {
            if (n.isBlank()) continue;
            Player p = Bukkit.getPlayerExact(n.trim());
            if (p == null) throw new IllegalArgumentException("접속 중이 아님: " + n);
            out.add(p);
        }
        if (out.isEmpty() || out.size() > 3) throw new IllegalArgumentException("팀은 1~3명");
        return out;
    }

    // ================================================================== users
    private void user(CommandSender s, String[] a) {
        need(a, 3);
        String name = a[1], action = a[2];
        withRow(s, name, row -> {
            var store = plugin.store();
            switch (action) {
                case "정보" -> {
                    var tier = plugin.rules().rating.tier(row.rating(), store.rank(row.id()));
                    info(s, "── " + row.name() + " 큐디치 ──");
                    info(s, "레이팅 " + row.rating() + " (" + tier.label + ") · " + row.games() + "경기 " + row.wins() + "승 " + row.losses() + "패 " + row.draws() + "무 · 탈주 " + row.leaves());
                    info(s, "골 " + row.goals() + " · 처치 " + row.kills() + " · 데스 " + row.deaths() + " · 딜 " + Math.round(row.damage()) + " · 힐 " + Math.round(row.heal())
                            + " · 스니치 " + row.snitch() + " · 블러저 명중 " + row.deflectHits() + " · MVP " + row.mvp());
                    if (row.bannedUntil() > System.currentTimeMillis()) info(s, "신청 금지 중: " + (row.bannedUntil() == Long.MAX_VALUE ? "영구" : new java.util.Date(row.bannedUntil()).toString()));
                    Match m = plugin.matches().of(row.id());
                    info(s, m == null ? "상태: 경기 없음" : "상태: " + m.arena.name + " 경기 중");
                }
                case "레이팅" -> {
                    need(a, 4);
                    switch (a[3]) {
                        case "설정" -> { need(a, 5); int v = Integer.parseInt(a[4]); edit(s, row, r -> with(r, Math.max(0, v), r.bannedUntil())); }
                        case "추가" -> { need(a, 5); int v = Integer.parseInt(a[4]); edit(s, row, r -> with(r, Math.max(0, r.rating() + v), r.bannedUntil())); }
                        case "초기화" -> edit(s, row, r -> with(r, (int) plugin.rules().rating.start(), r.bannedUntil()));
                        default -> err(s, "레이팅 설정 <값> · 추가 <±값> · 초기화");
                    }
                }
                case "전적" -> {
                    if (a.length < 4 || !a[3].equals("초기화")) { err(s, "전적 초기화"); return; }
                    edit(s, row, r -> new RatingStore.Row(r.id(), r.name(), r.mode(), r.rating(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, r.reachedAt(), r.bannedUntil(), 0));
                }
                case "퇴장" -> {
                    Match m = plugin.matches().of(row.id());
                    Player p = Bukkit.getPlayer(row.id());
                    if (m == null || p == null) { err(s, "경기 중이 아니에요."); return; }
                    m.kick(p); ok(s, row.name() + " 님을 경기에서 내보냈어요 (이탈 처리 없음).");
                }
                case "금지" -> {
                    need(a, 4);
                    long until = switch (a[3]) {
                        case "해제" -> 0;
                        case "영구" -> Long.MAX_VALUE;
                        default -> System.currentTimeMillis() + duration(a[3]);
                    };
                    edit(s, row, r -> with(r, r.rating(), until));
                }
                default -> err(s, "정보 · 레이팅 · 전적 초기화 · 퇴장 · 금지");
            }
        });
    }

    private static RatingStore.Row with(RatingStore.Row r, int rating, long bannedUntil) {
        return new RatingStore.Row(r.id(), r.name(), r.mode(), rating, r.games(), r.wins(), r.losses(), r.draws(), r.goals(), r.kills(), r.deaths(), r.assists(),
                r.damage(), r.heal(), r.snitch(), r.deflectHits(), r.mvp(), rating != r.rating() ? System.currentTimeMillis() : r.reachedAt(), bannedUntil, r.leaves());
    }

    private void edit(CommandSender s, RatingStore.Row row, java.util.function.UnaryOperator<RatingStore.Row> f) {
        plugin.store().update(row.id(), row.name(), f, n -> { if (n == null) err(s, "저장 실패 (콘솔 확인)"); else ok(s, n.name() + " 저장됨 · 레이팅 " + n.rating()); });
    }

    static long duration(String s) {
        var m = java.util.regex.Pattern.compile("(\\d+)([smhd분시일])").matcher(s);
        long total = 0; int end = 0;
        while (m.find()) {
            if (m.start() != end) break;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) { case "s" -> 1000L; case "m", "분" -> 60_000L; case "h", "시" -> 3_600_000L; default -> 86_400_000L; };
            end = m.end();
        }
        if (end != s.length() || total <= 0) throw new IllegalArgumentException("기간 예: 30m, 1h, 1d, 영구, 해제");
        return total;
    }

    private void withRow(CommandSender s, String name, Consumer<RatingStore.Row> then) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) { plugin.store().load(online.getUniqueId(), online.getName(), r -> { if (r != null) then.accept(r); else err(s, "불러오기 실패"); }); return; }
        plugin.store().find(name, r -> { if (r == null) err(s, "큐디치 기록이 없는 닉네임이에요: " + name); else then.accept(r); });
    }

    // ================================================================== tab
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> opts = switch (a.length) {
            case 1 -> List.of("막대", "pos1", "pos2", "선택", "경기장", "테스트", "공테스트", "경기", "유저", "리로드");
            case 2 -> switch (a[0]) {
                case "경기장" -> concat(List.of("생성", "삭제", "목록"), names());
                case "테스트" -> concat(List.of("참가", "종료"), names());
                case "공테스트" -> names();
                case "경기" -> concat(List.of("목록", "시작"), names());
                case "선택" -> List.of("확장", "해제");
                case "유저" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            case 3 -> switch (a[0]) {
                case "경기장" -> a[1].equals("삭제") ? names() : List.of("정보", "이동", "검사", "켜기", "끄기", "보기", "경계", "공영역", "스니치영역", "골대", "금지영역",
                        "시작위치", "부활위치", "준비위치", "퀘이플위치", "블러저위치", "스니치위치", "성소", "연습용");
                case "테스트" -> a[1].equals("참가") || a[1].equals("종료") ? names() : List.of("준비:0", "시간:3", "스니치:0", "무적", "블러저:끔");
                case "공테스트" -> List.of("시작", "끝");
                case "경기" -> a[1].equals("시작") ? names() : List.of("강제종료");
                case "선택" -> List.of("위", "아래", "사방");
                case "유저" -> List.of("정보", "레이팅", "전적", "퇴장", "금지");
                default -> List.of();
            };
            case 4 -> switch (a[0]) {
                case "경기장" -> switch (a[2]) {
                    case "골대", "시작위치", "부활위치", "준비위치" -> List.of("A", "B");
                    case "금지영역", "스니치위치" -> List.of("추가", "목록", "삭제");
                    case "성소" -> List.of("체력", "마나", "안정성", "가속", "목록");
                    case "블러저위치" -> List.of("1", "2");
                    case "연습용" -> List.of("켜기", "끄기");
                    default -> List.of();
                };
                case "테스트" -> a[1].equals("참가") ? List.of("A", "B") : List.of("준비:0", "시간:3", "스니치:0", "무적", "블러저:끔");
                case "유저" -> switch (a[2]) { case "레이팅" -> List.of("설정", "추가", "초기화"); case "전적" -> List.of("초기화"); case "금지" -> List.of("1h", "1d", "영구", "해제"); default -> List.of(); };
                default -> List.of();
            };
            case 5 -> a[0].equals("경기장") ? switch (a[2]) { case "골대" -> List.of("추가", "목록", "삭제"); case "시작위치", "부활위치" -> List.of("1", "2", "3"); case "성소" -> List.of("추가", "삭제"); default -> List.of(); } : List.of();
            default -> List.of();
        };
        String last = a.length == 0 ? "" : a[a.length - 1];
        return opts.stream().filter(o -> o.startsWith(last)).toList();
    }

    private List<String> names() { return plugin.arenas().all().stream().map(ar -> ar.name).toList(); }
    private static List<String> concat(List<String> a, List<String> b) { var l = new ArrayList<>(a); l.addAll(b); return l; }
}
