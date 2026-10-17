package school.chacademia.kyudichi;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/**
 * Ratings and cumulative stats, shared by every server through the same database (plugins/ChacaKyudichi/database.properties,
 * same format as MagicCodexBridge; MariaDB → tables codex_kyudichi_*). `mode` keeps the table ready for 1:1 / 3:3 PvP.
 * All SQL runs on one worker thread; callbacks come back on the main thread.
 */
final class RatingStore {
    static final String MODE = "kyudichi";

    record Row(UUID id, String name, String mode, int rating, int games, int wins, int losses, int draws, int goals, int kills, int deaths,
               int assists, double damage, double heal, int snitch, int deflectHits, int mvp, long reachedAt, long bannedUntil, int leaves) {
        static Row fresh(UUID id, String name, String mode, int start) { return new Row(id, name, mode, start, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }
    record Recent(UUID match, long endedAt, int team, int scoreA, int scoreB, int outcome, int goals, int kills, int deaths, boolean mvp, int delta) {}
    record Delta(UUID id, String name, int rating, boolean win, boolean draw, boolean loss, boolean leaver, int goals, int kills, int deaths, int assists,
                 double damage, double heal, int snitch, int deflectHits, boolean mvp, int team, int delta) {}

    private final Plugin plugin; private final Logger log; private final int start;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "ChacaKyudichi-db"); t.setDaemon(true); return t; });
    private final Map<UUID, Row> cache = new ConcurrentHashMap<>();
    private volatile List<UUID> challengers = List.of();
    private ConnectionHolder db; private DatabaseSettings settings;
    private String tRating, tMatch, tPlayer;

    RatingStore(Plugin plugin, int start) { this.plugin = plugin; this.log = plugin.getLogger(); this.start = start; }

    void open(Path dataFolder) throws Exception {
        io.submit(() -> {
            Path file = dataFolder.resolve("database.properties");
            settings = DatabaseSettings.load(file);
            log.info(DatabaseSettings.describe(file, settings));
            db = settings.holder(dataFolder.resolve("kyudichi.db"));
            tRating = settings.table("kyudichi_rating", "kyudichi_rating");
            tMatch = settings.table("kyudichi_match", "kyudichi_match");
            tPlayer = settings.table("kyudichi_match_player", "kyudichi_match_player");
            try (var s = db.get().createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS " + tRating + "(player CHAR(36) NOT NULL, mode VARCHAR(16) NOT NULL, name VARCHAR(32) NOT NULL,"
                        + " rating INT NOT NULL, games INT NOT NULL, wins INT NOT NULL, losses INT NOT NULL, draws INT NOT NULL, goals INT NOT NULL,"
                        + " kills INT NOT NULL, deaths INT NOT NULL, assists INT NOT NULL, damage DOUBLE NOT NULL, heal DOUBLE NOT NULL, snitch INT NOT NULL,"
                        + " deflect_hits INT NOT NULL, mvp INT NOT NULL, reached_at BIGINT NOT NULL, banned_until BIGINT NOT NULL, leaves INT NOT NULL,"
                        + " PRIMARY KEY(player, mode))");
                s.execute("CREATE TABLE IF NOT EXISTS " + tMatch + "(id CHAR(36) NOT NULL PRIMARY KEY, mode VARCHAR(16) NOT NULL, arena VARCHAR(32) NOT NULL,"
                        + " ended_at BIGINT NOT NULL, score_a INT NOT NULL, score_b INT NOT NULL, winner INT NOT NULL, reason VARCHAR(16) NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS " + tPlayer + "(match_id CHAR(36) NOT NULL, player CHAR(36) NOT NULL, team INT NOT NULL,"
                        + " outcome INT NOT NULL, goals INT NOT NULL, kills INT NOT NULL, deaths INT NOT NULL, mvp INT NOT NULL, delta INT NOT NULL,"
                        + " ended_at BIGINT NOT NULL, PRIMARY KEY(match_id, player))");
            }
            refreshChallengersNow();
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    void close() {
        io.shutdown();
        try { io.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { if (db != null) db.close(); } catch (Exception ignored) { }
    }

    private <T> void async(Sql<T> work, Consumer<T> done) {
        io.execute(() -> {
            T v;
            try { v = work.run(db.get()); }
            catch (SQLException | RuntimeException e) { log.warning("큐디치 DB 작업 실패: " + e.getMessage()); v = null; }
            T out = v;
            if (done != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> done.accept(out));
        });
    }
    interface Sql<T> { T run(Connection c) throws SQLException; }

    /** Cached row (or a fresh default) — never blocks. Use {@link #load} on join to fill the cache. */
    Row cached(UUID id, String name) { return cache.getOrDefault(id, Row.fresh(id, name, MODE, start)); }
    List<UUID> challengers() { return challengers; }

    /** Loads a player's row; when they already have games under another name, the stored name becomes {@code name}
     *  (the MagicCodex 한글 닉네임) so 랭킹 · 정보 show the current nickname. */
    void load(UUID id, String name, Consumer<Row> done) {
        async(c -> {
            Row r = read(c, id, name);
            if (r != null && r.games() > 0 && name != null && !name.isBlank() && !name.equals(r.name())) {
                try (PreparedStatement s = c.prepareStatement("UPDATE " + tRating + " SET name=? WHERE player=? AND mode=?")) {
                    s.setString(1, name); s.setString(2, id.toString()); s.setString(3, MODE); s.executeUpdate();
                }
                r = new Row(r.id(), name, r.mode(), r.rating(), r.games(), r.wins(), r.losses(), r.draws(), r.goals(), r.kills(), r.deaths(), r.assists(),
                        r.damage(), r.heal(), r.snitch(), r.deflectHits(), r.mvp(), r.reachedAt(), r.bannedUntil(), r.leaves());
            }
            return r;
        }, r -> { if (r != null) cache.put(id, r); if (done != null) done.accept(r); });
    }
    void forget(UUID id) { cache.remove(id); }

    /** By nickname (case-insensitive, latest name we stored). */
    void find(String name, Consumer<Row> done) {
        async(c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT player FROM " + tRating + " WHERE mode=? AND LOWER(name)=LOWER(?) LIMIT 1")) {
                s.setString(1, MODE); s.setString(2, name);
                try (ResultSet r = s.executeQuery()) { return r.next() ? read(c, UUID.fromString(r.getString(1)), name) : null; }
            }
        }, done);
    }

    private Row read(Connection c, UUID id, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM " + tRating + " WHERE player=? AND mode=?")) {
            s.setString(1, id.toString()); s.setString(2, MODE);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Row.fresh(id, name, MODE, start);
                return new Row(id, r.getString("name"), MODE, r.getInt("rating"), r.getInt("games"), r.getInt("wins"), r.getInt("losses"), r.getInt("draws"),
                        r.getInt("goals"), r.getInt("kills"), r.getInt("deaths"), r.getInt("assists"), r.getDouble("damage"), r.getDouble("heal"),
                        r.getInt("snitch"), r.getInt("deflect_hits"), r.getInt("mvp"), r.getLong("reached_at"), r.getLong("banned_until"), r.getInt("leaves"));
            }
        }
    }

    private void write(Connection c, Row w) throws SQLException {
        String up = settings.mariaDb() ? "REPLACE INTO " : "INSERT OR REPLACE INTO ";
        try (PreparedStatement s = c.prepareStatement(up + tRating + "(player,mode,name,rating,games,wins,losses,draws,goals,kills,deaths,assists,damage,heal,snitch,deflect_hits,mvp,reached_at,banned_until,leaves)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            s.setString(i++, w.id().toString()); s.setString(i++, w.mode()); s.setString(i++, w.name()); s.setInt(i++, w.rating()); s.setInt(i++, w.games());
            s.setInt(i++, w.wins()); s.setInt(i++, w.losses()); s.setInt(i++, w.draws()); s.setInt(i++, w.goals()); s.setInt(i++, w.kills()); s.setInt(i++, w.deaths());
            s.setInt(i++, w.assists()); s.setDouble(i++, w.damage()); s.setDouble(i++, w.heal()); s.setInt(i++, w.snitch()); s.setInt(i++, w.deflectHits());
            s.setInt(i++, w.mvp()); s.setLong(i++, w.reachedAt()); s.setLong(i++, w.bannedUntil()); s.setInt(i, w.leaves());
            s.executeUpdate();
        }
        cache.computeIfPresent(w.id(), (k, v) -> w);
    }

    /** One finished rated match: match row, per-player rows and the cumulative rows, in one transaction. */
    void record(UUID match, String arena, int scoreA, int scoreB, int winner, String reason, List<Delta> deltas, Consumer<Map<UUID, Row>> done) {
        long at = System.currentTimeMillis();
        async(ignored -> {
            var out = new java.util.HashMap<UUID, Row>();
            Connection c = db.begin();
            boolean ok = false;
            try {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO " + tMatch + "(id,mode,arena,ended_at,score_a,score_b,winner,reason) VALUES(?,?,?,?,?,?,?,?)")) {
                    s.setString(1, match.toString()); s.setString(2, MODE); s.setString(3, arena); s.setLong(4, at);
                    s.setInt(5, scoreA); s.setInt(6, scoreB); s.setInt(7, winner); s.setString(8, reason); s.executeUpdate();
                }
                for (Delta d : deltas) {
                    Row o = read(c, d.id(), d.name());
                    int rating = Math.max(0, o.rating() + d.delta());
                    Row n = new Row(d.id(), d.name(), MODE, rating, o.games() + 1, o.wins() + (d.win() ? 1 : 0), o.losses() + (d.loss() || d.leaver() ? 1 : 0),
                            o.draws() + (d.draw() ? 1 : 0), o.goals() + d.goals(), o.kills() + d.kills(), o.deaths() + d.deaths(), o.assists() + d.assists(),
                            o.damage() + d.damage(), o.heal() + d.heal(), o.snitch() + d.snitch(), o.deflectHits() + d.deflectHits(), o.mvp() + (d.mvp() ? 1 : 0),
                            d.delta() != 0 ? at : o.reachedAt(), o.bannedUntil(), o.leaves() + (d.leaver() ? 1 : 0));
                    write(c, n);
                    out.put(d.id(), n);
                    try (PreparedStatement s = c.prepareStatement("INSERT INTO " + tPlayer + "(match_id,player,team,outcome,goals,kills,deaths,mvp,delta,ended_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                        s.setString(1, match.toString()); s.setString(2, d.id().toString()); s.setInt(3, d.team());
                        s.setInt(4, d.win() ? 1 : d.draw() ? 2 : 0); s.setInt(5, d.goals()); s.setInt(6, d.kills()); s.setInt(7, d.deaths());
                        s.setInt(8, d.mvp() ? 1 : 0); s.setInt(9, d.delta()); s.setLong(10, at); s.executeUpdate();
                    }
                }
                c.commit(); ok = true;
            } finally { if (!ok) db.rollback(); db.end(); }
            refreshChallengersNow();
            return out;
        }, done);
    }

    /** Admin edits. `edit` receives the current row and returns the new one. */
    void update(UUID id, String name, java.util.function.UnaryOperator<Row> edit, Consumer<Row> done) {
        async(c -> { Row n = edit.apply(read(c, id, name)); write(c, n); refreshChallengersNow(); return n; }, done);
    }

    void recent(UUID id, int limit, Consumer<List<Recent>> done) {
        async(c -> {
            var out = new ArrayList<Recent>();
            try (PreparedStatement s = c.prepareStatement("SELECT p.match_id,p.ended_at,p.team,p.outcome,p.goals,p.kills,p.deaths,p.mvp,p.delta,m.score_a,m.score_b FROM "
                    + tPlayer + " p JOIN " + tMatch + " m ON m.id=p.match_id WHERE p.player=? ORDER BY p.ended_at DESC LIMIT ?")) {
                s.setString(1, id.toString()); s.setInt(2, Math.clamp(limit, 1, 50));
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) out.add(new Recent(UUID.fromString(r.getString(1)), r.getLong(2), r.getInt(3), r.getInt(10), r.getInt(11), r.getInt(4),
                            r.getInt(5), r.getInt(6), r.getInt(7), r.getInt(8) == 1, r.getInt(9)));
                }
            }
            return out;
        }, done);
    }

    void top(int limit, Consumer<List<Row>> done) {
        async(c -> {
            var out = new ArrayList<Row>();
            try (PreparedStatement s = c.prepareStatement("SELECT player,name FROM " + tRating + " WHERE mode=? AND games>0 ORDER BY rating DESC, reached_at ASC LIMIT ?")) {
                s.setString(1, MODE); s.setInt(2, Math.clamp(limit, 1, 100));
                try (ResultSet r = s.executeQuery()) { while (r.next()) out.add(read(c, UUID.fromString(r.getString(1)), r.getString(2))); }
            }
            return out;
        }, done);
    }

    private void refreshChallengersNow() throws SQLException {
        var list = new ArrayList<UUID>();
        int master = plugin instanceof KyudichiPlugin k ? k.rules().rating.tierFloors()[4] : 1700;
        try (PreparedStatement s = db.get().prepareStatement("SELECT player FROM " + tRating + " WHERE mode=? AND games>0 AND rating>=? ORDER BY rating DESC, reached_at ASC LIMIT 3")) {
            s.setString(1, MODE); s.setInt(2, master);
            try (ResultSet r = s.executeQuery()) { while (r.next()) list.add(UUID.fromString(r.getString(1))); }
        }
        challengers = List.copyOf(list);
    }

    /** 1..3 for challengers, 0 otherwise. */
    int rank(UUID id) { int i = challengers.indexOf(id); return i < 0 ? 0 : i + 1; }
}
