package kr.chacademy.portrait.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ChacaPortrait 테이블 (모두 cport_ 접두어). 이 클래스의 메서드는 DB 스레드 안에서만 호출한다 ({@link Database#async}).
 * 서버 간 공유(MariaDB)를 전제로, 예산·작업 잠금·다시 그리기 상태 변경은 모두 조건부 UPDATE로 한 번만 성공하게 한다.
 */
public final class PortraitStorage {

    public record Portrait(String sha, byte[] png, String model, String source, long createdAt) {
    }

    public record Reroll(String token, UUID player, String server, byte[] item, String prompt, String state, long createdAt) {
    }

    public record BudgetState(long spent, long reserved) {
    }

    public static final String R_PREPARED = "PREPARED"; // durable intent before inventory debit
    public static final String R_CANCELLED = "CANCELLED"; // intent never debited
    public static final String R_PENDING = "PENDING";   // 아이템 차감됨, 생성 중
    public static final String R_DONE = "DONE";         // 성공 (아이템 소모)
    public static final String R_REFUND = "REFUND_DUE"; // 실패 → 아이템 돌려줘야 함
    public static final String R_REFUNDED = "REFUNDED";

    private final Database db;

    public PortraitStorage(Database db) {
        this.db = db;
    }

    private String blob() {
        return db.isMariaDb() ? "MEDIUMBLOB" : "BLOB";
    }

    public void init() throws SQLException {
        db.with(c -> {
            try (Statement s = c.createStatement()) {
                String text = db.textType();
                s.execute("CREATE TABLE IF NOT EXISTS cport_portrait (uuid VARCHAR(36) PRIMARY KEY, sha CHAR(64) NOT NULL, "
                        + "png " + blob() + " NOT NULL, model VARCHAR(64) NOT NULL, source VARCHAR(16) NOT NULL, prompt " + text + ", "
                        + "cost_micro BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS cport_state (uuid VARCHAR(36) PRIMARY KEY, auto_attempts INT NOT NULL DEFAULT 0, "
                        + "last_error " + text + ", running_server VARCHAR(32), running_since BIGINT NOT NULL DEFAULT 0, "
                        + "last_reroll BIGINT NOT NULL DEFAULT 0, reroll_day VARCHAR(10), reroll_count INT NOT NULL DEFAULT 0)");
                s.execute("CREATE TABLE IF NOT EXISTS cport_reroll (token VARCHAR(32) PRIMARY KEY, uuid VARCHAR(36) NOT NULL, "
                        + "server VARCHAR(32) NOT NULL, item " + blob() + " NOT NULL, prompt " + text + ", state VARCHAR(16) NOT NULL, "
                        + "created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS cport_budget (id INT PRIMARY KEY, spent BIGINT NOT NULL DEFAULT 0, "
                        + "reserved BIGINT NOT NULL DEFAULT 0)");
                s.execute("CREATE TABLE IF NOT EXISTS cport_reservation (id VARCHAR(32) PRIMARY KEY, server VARCHAR(32) NOT NULL, "
                        + "amount BIGINT NOT NULL, created_at BIGINT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS cport_usage (id " + db.autoId() + ", at BIGINT NOT NULL, uuid VARCHAR(36), "
                        + "kind VARCHAR(16) NOT NULL, model VARCHAR(64) NOT NULL, text_in BIGINT NOT NULL, image_in BIGINT NOT NULL, "
                        + "output BIGINT NOT NULL, cost_micro BIGINT NOT NULL, ok INT NOT NULL)");
                try {
                    s.execute("CREATE INDEX cport_reroll_uuid ON cport_reroll (uuid, state)");
                } catch (SQLException ignored) {
                    // 이미 있음
                }
                s.execute(db.isMariaDb() ? "INSERT IGNORE INTO cport_budget (id, spent, reserved) VALUES (1, 0, 0)"
                        : "INSERT OR IGNORE INTO cport_budget (id, spent, reserved) VALUES (1, 0, 0)");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ 일러스트

    public Portrait portrait(UUID id) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sha, png, model, source, created_at FROM cport_portrait WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new Portrait(rs.getString(1), rs.getBytes(2), rs.getString(3), rs.getString(4), rs.getLong(5)) : null;
                }
            }
        });
    }

    public String sha(UUID id) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT sha FROM cport_portrait WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    public void savePortrait(UUID id, String sha, byte[] png, String model, String source, String prompt, long cost) throws SQLException {
        db.with(c -> {
            String sql = db.isMariaDb()
                    ? "INSERT INTO cport_portrait (uuid, sha, png, model, source, prompt, cost_micro, created_at) VALUES (?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE sha=VALUES(sha), png=VALUES(png), model=VALUES(model), source=VALUES(source), "
                    + "prompt=VALUES(prompt), cost_micro=VALUES(cost_micro), created_at=VALUES(created_at)"
                    : "INSERT INTO cport_portrait (uuid, sha, png, model, source, prompt, cost_micro, created_at) VALUES (?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET sha=excluded.sha, png=excluded.png, model=excluded.model, source=excluded.source, "
                    + "prompt=excluded.prompt, cost_micro=excluded.cost_micro, created_at=excluded.created_at";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id.toString());
                ps.setString(2, sha);
                ps.setBytes(3, png);
                ps.setString(4, model);
                ps.setString(5, source);
                ps.setString(6, prompt);
                ps.setLong(7, cost);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 생성 결과 확정 (한 트랜잭션). 다시 그리기면 먼저 PENDING→DONE 전이를 시도하고, 이미 다른 상태(반환됨 등)면
     * 아무것도 저장하지 않고 false. 성공하면 일러스트 저장·자동 시도 초기화·다시 그리기 횟수 기록까지 함께 커밋한다.
     */
    public boolean commitResult(UUID id, String rerollToken, String sha, byte[] png, String model, String source,
                                String prompt, long cost, String day, String owner, long generation) throws SQLException {
        return db.with(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Lock the ownership row in the same transaction as the portrait overwrite.
                try (PreparedStatement ps = c.prepareStatement("SELECT running_server, running_since FROM cport_state WHERE uuid=?" + (db.isMariaDb() ? " FOR UPDATE" : ""))) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !owner.equals(rs.getString(1)) || generation != rs.getLong(2)) {
                            c.rollback(); return false;
                        }
                    }
                }
                if (rerollToken != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE cport_reroll SET state=?, updated_at=? WHERE token=? AND state=?")) {
                        ps.setString(1, R_DONE);
                        ps.setLong(2, System.currentTimeMillis());
                        ps.setString(3, rerollToken);
                        ps.setString(4, R_PENDING);
                        if (ps.executeUpdate() != 1) {
                            c.rollback();
                            return false;
                        }
                    }
                }
                String sql = db.isMariaDb()
                        ? "INSERT INTO cport_portrait (uuid, sha, png, model, source, prompt, cost_micro, created_at) VALUES (?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE sha=VALUES(sha), png=VALUES(png), model=VALUES(model), source=VALUES(source), "
                        + "prompt=VALUES(prompt), cost_micro=VALUES(cost_micro), created_at=VALUES(created_at)"
                        : "INSERT INTO cport_portrait (uuid, sha, png, model, source, prompt, cost_micro, created_at) VALUES (?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET sha=excluded.sha, png=excluded.png, model=excluded.model, source=excluded.source, "
                        + "prompt=excluded.prompt, cost_micro=excluded.cost_micro, created_at=excluded.created_at";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, id.toString());
                    ps.setString(2, sha);
                    ps.setBytes(3, png);
                    ps.setString(4, model);
                    ps.setString(5, source);
                    ps.setString(6, prompt);
                    ps.setLong(7, cost);
                    ps.setLong(8, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                ensureState(c, id);
                try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET auto_attempts=0, last_error=NULL WHERE uuid=?")) {
                    ps.setString(1, id.toString());
                    ps.executeUpdate();
                }
                if (rerollToken != null) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET last_reroll=?, "
                            + "reroll_count=CASE WHEN reroll_day=? THEN reroll_count+1 ELSE 1 END, reroll_day=? WHERE uuid=?")) {
                        ps.setLong(1, System.currentTimeMillis());
                        ps.setString(2, day);
                        ps.setString(3, day);
                        ps.setString(4, id.toString());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    public boolean deletePortrait(UUID id) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cport_portrait WHERE uuid=?")) {
                ps.setString(1, id.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ------------------------------------------------------------------ 상태·잠금

    private void ensureState(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(db.isMariaDb()
                ? "INSERT IGNORE INTO cport_state (uuid) VALUES (?)" : "INSERT OR IGNORE INTO cport_state (uuid) VALUES (?)")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    /** 작업 잠금. 다른 서버·작업이 잡고 있으면 false. staleMs보다 오래된 잠금은 죽은 것으로 보고 가져온다. */
    public long claimGeneration(UUID id, String server, long staleMs) throws SQLException {
        return db.with(c -> {
            ensureState(c, id);
            boolean auto=c.getAutoCommit();c.setAutoCommit(false);
            try {
                long now=System.currentTimeMillis();
                try (PreparedStatement ps=c.prepareStatement("UPDATE cport_state SET running_server=?, running_since=CASE WHEN running_since>=? THEN running_since+1 ELSE ? END WHERE uuid=? AND (running_server IS NULL OR running_since<?)")) {
                    ps.setString(1,server);ps.setLong(2,now);ps.setLong(3,now);ps.setString(4,id.toString());ps.setLong(5,now-staleMs);
                    if(ps.executeUpdate()!=1){c.rollback();return 0L;}
                }
                long token;
                try(PreparedStatement ps=c.prepareStatement("SELECT running_since FROM cport_state WHERE uuid=?")) {
                    ps.setString(1,id.toString());try(ResultSet rs=ps.executeQuery()){rs.next();token=rs.getLong(1);}
                }
                c.commit();return token;
            } catch(SQLException e){c.rollback();throw e;} finally {c.setAutoCommit(auto);}
        });
    }

    public void releaseGeneration(UUID id, String server, long generation) throws SQLException {
        db.with(c -> {try(PreparedStatement ps=c.prepareStatement("UPDATE cport_state SET running_server=NULL WHERE uuid=? AND running_server=? AND running_since=?")) {
            ps.setString(1,id.toString());ps.setString(2,server);ps.setLong(3,generation);ps.executeUpdate();
        }return null;});
    }

    public boolean claim(UUID id, String server, long staleMs) throws SQLException {
        return db.with(c -> {
            ensureState(c, id);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET running_server=?, running_since=? "
                    + "WHERE uuid=? AND (running_server IS NULL OR running_since < ?)")) {
                ps.setString(1, server);
                ps.setLong(2, now);
                ps.setString(3, id.toString());
                ps.setLong(4, now - staleMs);
                return ps.executeUpdate() == 1;
            }
        });
    }

    public void release(UUID id, String server) throws SQLException {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE cport_state SET running_server=NULL WHERE uuid=? AND running_server=?")) {
                ps.setString(1, id.toString());
                ps.setString(2, server);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** 시작 시: 이 서버가 잡고 있던 잠금 해제 (지난 실행이 비정상 종료). */
    public int releaseAll(String server) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET running_server=NULL WHERE running_server=?")) {
                ps.setString(1, server);
                return ps.executeUpdate();
            }
        });
    }

    public int autoAttempts(UUID id) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT auto_attempts FROM cport_state WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public void recordAttempt(UUID id, boolean auto, String error) throws SQLException {
        db.with(c -> {
            ensureState(c, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET auto_attempts=auto_attempts+?, last_error=? WHERE uuid=?")) {
                ps.setInt(1, auto ? 1 : 0);
                ps.setString(2, error == null ? null : error.substring(0, Math.min(500, error.length())));
                ps.setString(3, id.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void resetState(UUID id) throws SQLException {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE cport_state SET auto_attempts=0, last_error=NULL WHERE uuid=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public String lastError(UUID id) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT last_error, auto_attempts, running_server FROM cport_state WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return "시도 " + rs.getInt(2) + "회, 진행 서버 " + (rs.getString(3) == null ? "없음" : rs.getString(3))
                            + (rs.getString(1) == null ? "" : ", 마지막 오류: " + rs.getString(1));
                }
            }
        });
    }

    /** {마지막 성공 시각, 오늘 횟수} (day가 다르면 횟수 0). */
    public long[] rerollUsage(UUID id, String day) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT last_reroll, reroll_day, reroll_count FROM cport_state WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new long[]{0, 0};
                    }
                    return new long[]{rs.getLong(1), day.equals(rs.getString(2)) ? rs.getInt(3) : 0};
                }
            }
        });
    }

    public void recordRerollSuccess(UUID id, String day) throws SQLException {
        db.with(c -> {
            ensureState(c, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_state SET last_reroll=?, "
                    + "reroll_count=CASE WHEN reroll_day=? THEN reroll_count+1 ELSE 1 END, reroll_day=? WHERE uuid=?")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, day);
                ps.setString(3, day);
                ps.setString(4, id.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ 다시 그리기 아이템 (2단계)

    public void insertReroll(String token, UUID id, String server, byte[] item, String prompt) throws SQLException {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cport_reroll (token, uuid, server, item, prompt, state, "
                    + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)")) {
                long now = System.currentTimeMillis();
                ps.setString(1, token);
                ps.setString(2, id.toString());
                ps.setString(3, server);
                ps.setBytes(4, item);
                ps.setString(5, prompt);
                ps.setString(6, R_PREPARED);
                ps.setLong(7, now);
                ps.setLong(8, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** 상태 전이 (from → to). 정확히 한 번만 성공한다. */
    public boolean moveReroll(String token, String from, String to) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_reroll SET state=?, updated_at=? WHERE token=? AND state=?")) {
                ps.setString(1, to);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, token);
                ps.setString(4, from);
                return ps.executeUpdate() == 1;
            }
        });
    }

    /** 시작 시: 이 서버에서 진행 중이던(PENDING) 다시 그리기 → 돌려줄 대상으로. */
    public int failPendingRerolls(String server) throws SQLException {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cport_reroll SET state=?, updated_at=? WHERE server=? AND state=?")) {
                ps.setString(1, R_REFUND);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, server);
                ps.setString(4, R_PENDING);
                return ps.executeUpdate();
            }
        });
    }

    public List<Reroll> refundsDue(UUID id, String server) throws SQLException {
        return db.with(c -> {
            List<Reroll> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT token, uuid, server, item, prompt, state, created_at FROM cport_reroll "
                    + "WHERE uuid=? AND server=? AND state IN (?,?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, server);
                ps.setString(3, R_REFUND);
                ps.setString(4, R_PREPARED);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Reroll(rs.getString(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getBytes(4),
                                rs.getString(5), rs.getString(6), rs.getLong(7)));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ 예산 (서버 간 공유, 조건부 갱신)

    /** 예약. 총예산을 넘으면 false. */
    public boolean reserve(String id, String server, long amount, long cap) throws SQLException {
        return db.with(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int n;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE cport_budget SET reserved=reserved+? WHERE id=1 AND spent+reserved+? <= ?")) {
                    ps.setLong(1, amount);
                    ps.setLong(2, amount);
                    ps.setLong(3, cap);
                    n = ps.executeUpdate();
                }
                if (n != 1) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO cport_reservation (id, server, amount, created_at) VALUES (?,?,?,?)")) {
                    ps.setString(1, id);
                    ps.setString(2, server);
                    ps.setLong(3, amount);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    /** 정산. 같은 예약을 두 번 정산하지 않는다 (예약 행 삭제가 성공한 쪽만 반영). */
    public boolean settle(String id, long actual) throws SQLException {
        return db.with(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long amount;
                try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM cport_reservation WHERE id=?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        amount = rs.getLong(1);
                    }
                }
                int deleted;
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM cport_reservation WHERE id=?")) {
                    ps.setString(1, id);
                    deleted = ps.executeUpdate();
                }
                if (deleted != 1) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE cport_budget SET reserved=CASE WHEN reserved>=? THEN reserved-? ELSE 0 END, spent=spent+? WHERE id=1")) {
                    ps.setLong(1, amount);
                    ps.setLong(2, amount);
                    ps.setLong(3, Math.max(0, actual));
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    /** 시작 시: 이 서버가 남긴 예약을 예약액 그대로 정산 (뒤늦은 응답 비용 누락 방지, 보수적). */
    public int settleStale(String server) throws SQLException {
        List<String[]> rows = db.with(c -> {
            List<String[]> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id, amount FROM cport_reservation WHERE server=?")) {
                ps.setString(1, server);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new String[]{rs.getString(1), Long.toString(rs.getLong(2))});
                    }
                }
            }
            return out;
        });
        int n = 0;
        for (String[] r : rows) {
            if (settle(r[0], Long.parseLong(r[1]))) {
                n++;
            }
        }
        return n;
    }

    public BudgetState budget() throws SQLException {
        return db.with(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT spent, reserved FROM cport_budget WHERE id=1")) {
                return rs.next() ? new BudgetState(rs.getLong(1), rs.getLong(2)) : new BudgetState(0, 0);
            }
        });
    }

    public void logUsage(UUID id, String kind, String model, long textIn, long imageIn, long output, long cost, boolean ok) throws SQLException {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cport_usage (at, uuid, kind, model, text_in, image_in, output, "
                    + "cost_micro, ok) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, id == null ? null : id.toString());
                ps.setString(3, kind);
                ps.setString(4, model);
                ps.setLong(5, textIn);
                ps.setLong(6, imageIn);
                ps.setLong(7, output);
                ps.setLong(8, cost);
                ps.setInt(9, ok ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
