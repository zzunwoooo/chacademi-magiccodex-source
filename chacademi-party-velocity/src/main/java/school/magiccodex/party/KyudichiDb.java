package school.magiccodex.party;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.KyudichiQueueProtocol.Rank;
import school.magiccodex.protocol.KyudichiQueueProtocol.Recent;
import school.magiccodex.protocol.KyudichiQueueProtocol.PlayerRecord;

/**
 * Read-only view of the 큐디치 tables that the arena server (ChacaKyudichi) writes. Shared MariaDB only:
 * the proxy never creates or changes rows. Without MariaDB every player counts as the start rating and
 * the ranking / record screens say there is no data.
 */
final class KyudichiDb {
    static final String MODE = "kyudichi";
    record Standing(int rating, long bannedUntil) {}

    private final Logger log; private final int start; private final int[] floors;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "Chacademi-kyudichi-db"); t.setDaemon(true); return t; });
    private ConnectionHolder db; private String table, matches, players;

    KyudichiDb(Logger log, int start, int[] floors) { this.log = log; this.start = start; this.floors = floors; }

    void open(Path file) {
        io.execute(() -> {
            try {
                var s = DatabaseSettings.load(file);
                log.info(DatabaseSettings.describe(file, s));
                if (!s.mariaDb()) { log.warn("큐디치: MariaDB 설정이 없어 레이팅 없이(모두 " + start + ") 매칭합니다. 경기장 서버와 같은 DB를 " + file.getFileName() + " 에 넣으세요."); return; }
                db = s.holder(file.resolveSibling("unused.db"));
                table = s.table("kyudichi_rating", "kyudichi_rating");
                matches = s.table("kyudichi_match", "kyudichi_match");
                players = s.table("kyudichi_match_player", "kyudichi_match_player");
            } catch (Exception e) { log.error("큐디치 DB 연결 실패: 레이팅 없이 매칭합니다", e); db = null; }
        });
    }
    void close() { io.shutdown(); try { if (db != null) db.close(); } catch (Exception ignored) { } }

    interface Sql<T> { T run(Connection c) throws SQLException; }
    private <T> void async(Sql<T> work, T fallback, Consumer<T> done) {
        io.execute(() -> {
            T v = fallback;
            if (db != null) {
                try { v = work.run(db.get()); }
                catch (SQLException | RuntimeException e) { log.warn("큐디치 DB 읽기 실패: " + e.getMessage()); }
            }
            done.accept(v);
        });
    }
    boolean available() { return db != null; }

    /** Rating and ban for each player (missing rows = start rating, no ban). Callback runs on the DB thread. */
    void standings(List<UUID> ids, Consumer<Map<UUID, Standing>> done) {
        var fallback = new HashMap<UUID, Standing>();
        for (UUID id : ids) fallback.put(id, new Standing(start, 0));
        async(c -> {
            var out = new HashMap<>(fallback);
            try (PreparedStatement s = c.prepareStatement("SELECT rating, banned_until FROM " + table + " WHERE player=? AND mode=?")) {
                for (UUID id : ids) {
                    s.setString(1, id.toString()); s.setString(2, MODE);
                    try (ResultSet r = s.executeQuery()) { if (r.next()) out.put(id, new Standing(r.getInt(1), r.getLong(2))); }
                }
            }
            return out;
        }, fallback, done);
    }

    int tier(int rating, int rank) {
        if (rating >= floors[4] && rank >= 1 && rank <= 3) return 5 + rank;
        for (int i = floors.length - 1; i >= 0; i--) if (rating >= floors[i]) return i + 1;
        return 0;
    }

    private int rank(Connection c, int rating, long reachedAt) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE mode=? AND games>0 AND (rating>? OR (rating=? AND reached_at<?))")) {
            s.setString(1, MODE); s.setInt(2, rating); s.setInt(3, rating); s.setLong(4, reachedAt);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getInt(1) + 1 : 0; }
        }
    }

    /** Top 100 (rating, then who reached it first) and the viewer's own line. */
    void ranking(UUID viewer, Consumer<Object[]> done) {
        async(c -> {
            var top = new ArrayList<Rank>(); Rank mine = null;
            try (PreparedStatement s = c.prepareStatement("SELECT player,name,rating,games,wins,losses FROM " + table + " WHERE mode=? AND games>0 ORDER BY rating DESC, reached_at ASC LIMIT 100")) {
                s.setString(1, MODE);
                try (ResultSet r = s.executeQuery()) {
                    int i = 1;
                    while (r.next()) {
                        int rating = r.getInt(3);
                        var row = new Rank(i, UUID.fromString(r.getString(1)), r.getString(2), rating, tier(rating, i), r.getInt(4), r.getInt(5), r.getInt(6));
                        top.add(row); if (row.id().equals(viewer)) mine = row; i++;
                    }
                }
            }
            if (mine == null) {
                try (PreparedStatement s = c.prepareStatement("SELECT name,rating,games,wins,losses,reached_at FROM " + table + " WHERE player=? AND mode=? AND games>0")) {
                    s.setString(1, viewer.toString()); s.setString(2, MODE);
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) { int rating = r.getInt(2); int rk = rank(c, rating, r.getLong(6)); mine = new Rank(rk, viewer, r.getString(1), rating, tier(rating, rk), r.getInt(3), r.getInt(4), r.getInt(5)); }
                    }
                }
            }
            return new Object[]{List.copyOf(top), mine};
        }, new Object[]{List.<Rank>of(), null}, done);
    }

    /** Full record by player id, or by the last stored nickname when id is null. */
    void record(UUID id, String nick, Consumer<PlayerRecord> done) {
        async(c -> {
            String sql = "SELECT * FROM " + table + " WHERE mode=? AND " + (id != null ? "player=?" : "LOWER(name)=LOWER(?)") + " LIMIT 1";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setString(1, MODE); s.setString(2, id != null ? id.toString() : nick);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) return null;
                    UUID who = UUID.fromString(r.getString("player"));
                    int rating = r.getInt("rating"); int rk = r.getInt("games") > 0 ? rank(c, rating, r.getLong("reached_at")) : 0;
                    return new PlayerRecord(who, r.getString("name"), rating, tier(rating, rk), rk, r.getInt("games"), r.getInt("wins"), r.getInt("losses"), r.getInt("draws"),
                            r.getInt("goals"), r.getInt("kills"), r.getInt("deaths"), r.getInt("assists"), r.getInt("snitch"), r.getInt("deflect_hits"), r.getInt("mvp"),
                            (float) r.getDouble("damage"), (float) r.getDouble("heal"), recent(c, who));
                }
            }
        }, null, done);
    }

    private List<Recent> recent(Connection c, UUID id) throws SQLException {
        var out = new ArrayList<Recent>();
        try (PreparedStatement s = c.prepareStatement("SELECT p.ended_at,p.outcome,p.team,m.score_a,m.score_b,p.goals,p.kills,p.deaths,p.mvp,p.delta FROM "
                + players + " p JOIN " + matches + " m ON m.id=p.match_id WHERE p.player=? ORDER BY p.ended_at DESC LIMIT 10")) {
            s.setString(1, id.toString());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(new Recent(r.getLong(1), Math.clamp(r.getInt(2), 0, 2), Math.clamp(r.getInt(3), 0, 1), r.getInt(4), r.getInt(5),
                        r.getInt(6), r.getInt(7), r.getInt(8), r.getInt(9) == 1, r.getInt(10)));
            }
        }
        return out;
    }
}
