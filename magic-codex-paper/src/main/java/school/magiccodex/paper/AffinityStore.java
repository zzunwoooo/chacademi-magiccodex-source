package school.magiccodex.paper;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/**
 * NPC 호감도 저장소. MagicCodexBridge가 소유한다.
 * MariaDB: codex_affinity / codex_affinity_daily / codex_affinity_gift (school·wild 공유).
 * SQLite fallback: 반드시 Bridge 데이터 폴더의 {@code affinity.db} (호출자가 경로를 명시해서 넘긴다).
 *
 * <p>하루 한도와 점수 갱신은 트랜잭션 + 조건부 UPDATE로 처리해 서버 간 동시 갱신에도 한도를 넘지 않는다.
 * 하나의 IO 스레드만 이 객체를 사용한다.
 */
final class AffinityStore implements AutoCloseable {

    /** 하트 이벤트를 몇 단계 봤는지에 따른 점수 상한: heart 0 → 20, 1 → 40 ... 4 이상 → 100. */
    static int gate(int heart) {
        return Math.min(100, 20 * (Math.max(0, heart) + 1));
    }

    record Row(int score, int heart, String nickname) {
        static final Row EMPTY = new Row(0, 0, "");
    }

    record AddResult(Row row, int applied) {
    }

    enum GiftBegin { OK, CAP, DUPLICATE }

    private final ConnectionHolder holder;
    private final boolean maria;
    private final String affinity;
    private final String daily;
    private final String gift;

    AffinityStore(DatabaseSettings settings, Path sqliteFile) throws Exception {
        maria = settings.mariaDb();
        affinity = settings.table("affinity", "npc_affinity");
        daily = settings.table("affinity_daily", "npc_affinity_daily");
        gift = settings.table("affinity_gift", "npc_affinity_gift");
        holder = settings.holder(sqliteFile);
        String engine = maria ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        String uuid = maria ? "CHAR(36)" : "TEXT";
        try (var s = holder.get().createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + affinity + " (owner " + uuid + " NOT NULL, npc VARCHAR(48) NOT NULL,"
                    + " score INT NOT NULL DEFAULT 0, heart INT NOT NULL DEFAULT 0, nickname VARCHAR(32) NOT NULL DEFAULT '',"
                    + " updated BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (owner, npc))" + engine);
            s.execute("CREATE TABLE IF NOT EXISTS " + daily + " (owner " + uuid + " NOT NULL, npc VARCHAR(48) NOT NULL,"
                    + " day CHAR(10) NOT NULL, source VARCHAR(16) NOT NULL, amount INT NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (owner, npc, day, source))" + engine);
            s.execute("CREATE TABLE IF NOT EXISTS " + gift + " (token CHAR(36) NOT NULL PRIMARY KEY, owner " + uuid + " NOT NULL,"
                    + " npc VARCHAR(48) NOT NULL, day CHAR(10) NOT NULL, delta INT NOT NULL, status VARCHAR(12) NOT NULL,"
                    + " created BIGINT NOT NULL)" + engine);
        }
    }

    private String lock() {
        return maria ? " FOR UPDATE" : "";
    }

    // ------------------------------------------------------------------ 읽기

    Row load(UUID owner, String npc) throws SQLException {
        try (PreparedStatement s = holder.get().prepareStatement(
                "SELECT score, heart, nickname FROM " + affinity + " WHERE owner=? AND npc=?")) {
            s.setString(1, owner.toString());
            s.setString(2, npc);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Row(r.getInt(1), r.getInt(2), r.getString(3)) : Row.EMPTY;
            }
        }
    }

    Map<String, Row> loadAll(UUID owner) throws SQLException {
        Map<String, Row> out = new HashMap<>();
        try (PreparedStatement s = holder.get().prepareStatement(
                "SELECT npc, score, heart, nickname FROM " + affinity + " WHERE owner=?")) {
            s.setString(1, owner.toString());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    out.put(r.getString(1), new Row(r.getInt(2), r.getInt(3), r.getString(4)));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 쓰기 (트랜잭션)

    private interface Tx<T> {
        T run(Connection db) throws SQLException;
    }

    /** 트랜잭션. MariaDB 교착(1213)·직렬화 실패(40001)는 최대 3번 다시 시도한다. */
    private <T> T tx(Tx<T> body) throws SQLException {
        for (int attempt = 0; ; attempt++) {
            Connection db = holder.begin();
            try {
                T result = body.run(db);
                db.commit();
                return result;
            } catch (SQLException e) {
                holder.rollback();
                boolean retry = (e.getErrorCode() == 1213 || "40001".equals(e.getSQLState())) && attempt < 2;
                if (!retry) {
                    throw e;
                }
            } catch (RuntimeException e) {
                holder.rollback();
                throw e;
            } finally {
                holder.end();
            }
        }
    }

    private void ensureRow(Connection db, UUID owner, String npc) throws SQLException {
        // MariaDB: INSERT IGNORE는 공유 잠금 후 FOR UPDATE로 올라가며 교착이 날 수 있어 ON DUPLICATE KEY 사용
        String sql = maria
                ? "INSERT INTO " + affinity + " (owner, npc, score, heart, nickname, updated) VALUES (?,?,0,0,'',0)"
                + " ON DUPLICATE KEY UPDATE score=score"
                : "INSERT OR IGNORE INTO " + affinity + " (owner, npc, score, heart, nickname, updated) VALUES (?,?,0,0,'',0)";
        try (PreparedStatement s = db.prepareStatement(sql)) {
            s.setString(1, owner.toString());
            s.setString(2, npc);
            s.executeUpdate();
        }
    }

    /** 하루 한도 칸을 잠그고, 이번에 쓸 수 있는 양만큼 조건부로 늘린다. 반환 = 실제로 늘린 양. */
    private int consumeDaily(Connection db, UUID owner, String npc, String day, String source, int want, int cap)
            throws SQLException {
        if (want <= 0 || cap <= 0) {
            return 0;
        }
        String ensure = maria
                ? "INSERT INTO " + daily + " (owner, npc, day, source, amount) VALUES (?,?,?,?,0) ON DUPLICATE KEY UPDATE amount=amount"
                : "INSERT OR IGNORE INTO " + daily + " (owner, npc, day, source, amount) VALUES (?,?,?,?,0)";
        try (PreparedStatement s = db.prepareStatement(ensure)) {
            s.setString(1, owner.toString());
            s.setString(2, npc);
            s.setString(3, day);
            s.setString(4, source);
            s.executeUpdate();
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            int used;
            try (PreparedStatement s = db.prepareStatement("SELECT amount FROM " + daily
                    + " WHERE owner=? AND npc=? AND day=? AND source=?" + lock())) {
                s.setString(1, owner.toString());
                s.setString(2, npc);
                s.setString(3, day);
                s.setString(4, source);
                try (ResultSet r = s.executeQuery()) {
                    used = r.next() ? r.getInt(1) : 0;
                }
            }
            int allowed = Math.max(0, Math.min(want, cap - used));
            if (allowed == 0) {
                return 0;
            }
            try (PreparedStatement s = db.prepareStatement("UPDATE " + daily + " SET amount=amount+?"
                    + " WHERE owner=? AND npc=? AND day=? AND source=? AND amount=?")) {
                s.setInt(1, allowed);
                s.setString(2, owner.toString());
                s.setString(3, npc);
                s.setString(4, day);
                s.setString(5, source);
                s.setInt(6, used);
                if (s.executeUpdate() == 1) {
                    return allowed;
                }
            }
        }
        throw new SQLException("affinity daily limit contention");
    }

    private void releaseDaily(Connection db, UUID owner, String npc, String day, String source, int amount) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("UPDATE " + daily + " SET amount=amount-?"
                + " WHERE owner=? AND npc=? AND day=? AND source=? AND amount>=?")) {
            s.setInt(1, amount);
            s.setString(2, owner.toString());
            s.setString(3, npc);
            s.setString(4, day);
            s.setString(5, source);
            s.setInt(6, amount);
            s.executeUpdate();
        }
    }

    /** 점수를 조건부로 바꾼다 (하트 단계 상한 적용). 반환 = 새 Row와 실제 변화량. */
    private AddResult applyScore(Connection db, UUID owner, String npc, int delta, long now) throws SQLException {
        for (int attempt = 0; attempt < 3; attempt++) {
            Row cur;
            try (PreparedStatement s = db.prepareStatement("SELECT score, heart, nickname FROM " + affinity
                    + " WHERE owner=? AND npc=?" + lock())) {
                s.setString(1, owner.toString());
                s.setString(2, npc);
                try (ResultSet r = s.executeQuery()) {
                    cur = r.next() ? new Row(r.getInt(1), r.getInt(2), r.getString(3)) : Row.EMPTY;
                }
            }
            int next = Math.max(0, Math.min(gate(cur.heart()), cur.score() + delta));
            if (next == cur.score()) {
                return new AddResult(cur, 0);
            }
            try (PreparedStatement s = db.prepareStatement("UPDATE " + affinity + " SET score=?, updated=?"
                    + " WHERE owner=? AND npc=? AND score=?")) {
                s.setInt(1, next);
                s.setLong(2, now);
                s.setString(3, owner.toString());
                s.setString(4, npc);
                s.setInt(5, cur.score());
                if (s.executeUpdate() == 1) {
                    return new AddResult(new Row(next, cur.heart(), cur.nickname()), next - cur.score());
                }
            }
        }
        throw new SQLException("affinity score contention");
    }

    /**
     * 호감도 변화. 양수이고 cap > 0 이면 출처별 하루 한도(cap) 안에서만 오른다. 음수는 한도 없음.
     */
    AddResult add(UUID owner, String npc, String source, int amount, int cap, String day, long now) throws SQLException {
        return tx(db -> {
            ensureRow(db, owner, npc);
            int delta = amount;
            if (amount > 0) {
                // 하트 단계 상한까지 남은 만큼만 (상한에 막혀 오르지 않으면 하루 한도도 쓰지 않음)
                Row cur = lockRow(db, owner, npc);
                delta = Math.min(amount, gate(cur.heart()) - cur.score());
                if (delta <= 0) {
                    return new AddResult(cur, 0);
                }
                if (cap > 0) {
                    delta = consumeDaily(db, owner, npc, day, source, delta, cap);
                    if (delta == 0) {
                        return new AddResult(cur, 0);
                    }
                }
            }
            return applyScore(db, owner, npc, delta, now);
        });
    }

    /** 점수 행을 먼저 잠근다 (affinity → daily 순서로 잠가 교착을 줄임). */
    private Row lockRow(Connection db, UUID owner, String npc) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("SELECT score, heart, nickname FROM " + affinity
                + " WHERE owner=? AND npc=?" + lock())) {
            s.setString(1, owner.toString());
            s.setString(2, npc);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Row(r.getInt(1), r.getInt(2), r.getString(3)) : Row.EMPTY;
            }
        }
    }

    private Row loadIn(Connection db, UUID owner, String npc) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("SELECT score, heart, nickname FROM " + affinity
                + " WHERE owner=? AND npc=?")) {
            s.setString(1, owner.toString());
            s.setString(2, npc);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Row(r.getInt(1), r.getInt(2), r.getString(3)) : Row.EMPTY;
            }
        }
    }

    /** 하트 이벤트 단계 기록 (내려가지 않음, 0~5). */
    Row setHeart(UUID owner, String npc, int level, long now) throws SQLException {
        int lv = Math.max(0, Math.min(5, level));
        return tx(db -> {
            ensureRow(db, owner, npc);
            try (PreparedStatement s = db.prepareStatement("UPDATE " + affinity + " SET heart=?, updated=?"
                    + " WHERE owner=? AND npc=? AND heart<?")) {
                s.setInt(1, lv);
                s.setLong(2, now);
                s.setString(3, owner.toString());
                s.setString(4, npc);
                s.setInt(5, lv);
                s.executeUpdate();
            }
            return loadIn(db, owner, npc);
        });
    }

    Row setNickname(UUID owner, String npc, String nickname, long now) throws SQLException {
        return tx(db -> {
            ensureRow(db, owner, npc);
            try (PreparedStatement s = db.prepareStatement("UPDATE " + affinity + " SET nickname=?, updated=?"
                    + " WHERE owner=? AND npc=?")) {
                s.setString(1, nickname == null ? "" : nickname);
                s.setLong(2, now);
                s.setString(3, owner.toString());
                s.setString(4, npc);
                s.executeUpdate();
            }
            return loadIn(db, owner, npc);
        });
    }

    // ------------------------------------------------------------------ 선물 (2단계)

    /**
     * 1단계: 하루 선물 횟수를 예약하고 pending 기록을 만든다. 아이템은 아직 차감하지 않는다.
     * 같은 토큰은 한 번만 처리된다.
     */
    GiftBegin beginGift(String token, UUID owner, String npc, int delta, String day, int dailyCount, long now)
            throws SQLException {
        return tx(db -> {
            try (PreparedStatement s = db.prepareStatement("SELECT status FROM " + gift + " WHERE token=?")) {
                s.setString(1, token);
                try (ResultSet r = s.executeQuery()) {
                    if (r.next()) {
                        return GiftBegin.DUPLICATE;
                    }
                }
            }
            ensureRow(db, owner, npc);
            lockRow(db, owner, npc);
            if (consumeDaily(db, owner, npc, day, "gift", 1, dailyCount) == 0) {
                return GiftBegin.CAP;
            }
            try (PreparedStatement s = db.prepareStatement("INSERT INTO " + gift
                    + " (token, owner, npc, day, delta, status, created) VALUES (?,?,?,?,?,'pending',?)")) {
                s.setString(1, token);
                s.setString(2, owner.toString());
                s.setString(3, npc);
                s.setString(4, day);
                s.setInt(5, delta);
                s.setLong(6, now);
                s.executeUpdate();
            }
            return GiftBegin.OK;
        });
    }

    /** 2단계: 아이템 차감 후 확정. pending이 아니면(이미 처리됨) null. */
    AddResult commitGift(String token, long now) throws SQLException {
        return tx(db -> commitIn(db, token, now));
    }

    private AddResult commitIn(Connection db, String token, long now) throws SQLException {
        String owner;
        String npc;
        int delta;
        try (PreparedStatement s = db.prepareStatement("SELECT owner, npc, delta FROM " + gift
                + " WHERE token=? AND status='pending'" + lock())) {
            s.setString(1, token);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) {
                    return null;
                }
                owner = r.getString(1);
                npc = r.getString(2);
                delta = r.getInt(3);
            }
        }
        try (PreparedStatement s = db.prepareStatement("UPDATE " + gift + " SET status='done' WHERE token=? AND status='pending'")) {
            s.setString(1, token);
            if (s.executeUpdate() != 1) {
                return null;
            }
        }
        return applyScore(db, UUID.fromString(owner), npc, delta, now);
    }

    /** 아이템 차감 전에 취소 (손에 아이템이 없어짐 등). 하루 횟수도 돌려준다. */
    boolean cancelGift(String token) throws SQLException {
        return tx(db -> {
            String owner;
            String npc;
            String day;
            try (PreparedStatement s = db.prepareStatement("SELECT owner, npc, day FROM " + gift
                    + " WHERE token=? AND status='pending'" + lock())) {
                s.setString(1, token);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) {
                        return false;
                    }
                    owner = r.getString(1);
                    npc = r.getString(2);
                    day = r.getString(3);
                }
            }
            try (PreparedStatement s = db.prepareStatement("UPDATE " + gift + " SET status='cancelled' WHERE token=? AND status='pending'")) {
                s.setString(1, token);
                if (s.executeUpdate() != 1) {
                    return false;
                }
            }
            releaseDaily(db, UUID.fromString(owner), npc, day, "gift", 1);
            return true;
        });
    }

    /**
     * 서버가 선물 도중 꺼졌을 때: 오래된 pending은 "아이템이 이미 차감됐다"고 보고 확정한다.
     * (환불하지 않으므로 아이템 복제가 없고, 토큰당 한 번만 점수가 오른다.)
     */
    int recoverPendingGifts(long olderThan, long now) throws SQLException {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        try (PreparedStatement s = holder.get().prepareStatement("SELECT token FROM " + gift
                + " WHERE status='pending' AND created<?")) {
            s.setLong(1, olderThan);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    tokens.add(r.getString(1));
                }
            }
        }
        int n = 0;
        for (String t : tokens) {
            if (commitGift(t, now) != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void close() throws SQLException {
        holder.close();
    }
}
