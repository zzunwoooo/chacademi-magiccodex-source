package school.magiccodex.paper;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import school.magiccodex.database.DatabaseSettings;

/**
 * 통발 수조 (2026-10-20, owner): caught fish are not items, they swim in a big sub-space tank (opened by the 통발 spell).
 * What is in the tank is what the player owns: the fish shop sells from it, fish can be taken out as items and put back.
 * Capacity 100 (fishing.yml tank.capacity); a full tank blocks fishing.
 *
 * codex_fish_tank(id, uuid, fish, size, caught_at)  -- one row per fish
 * codex_fish_lock(uuid, fish)                       -- species the fish shop must never sell
 * codex_fish_item(id, fish, size, caught_at)        -- fish taken out as items (owner: data in the DB, the item has the id)
 *
 * Shared by school and wild through the player-state MariaDB (else a per-server fishing.db, like the codex).
 * The database is the truth: removing fish (selling, taking out) deletes rows by id and only what was really deleted
 * counts, so two servers or a double click can never sell one fish twice. A tank is re-read from the database every
 * time a window opens. JDBC runs only on the IO thread; callbacks come back on the main thread.
 */
final class FishTank implements AutoCloseable {
    record Fish(String id, String fish, double size, long caughtAt) {}
    static final class Tank {
        final List<Fish> fish = new ArrayList<>();
        final Set<String> locks = new HashSet<>();
        boolean loaded, loading;
        final List<Runnable> waiting = new ArrayList<>();
    }

    private final MagicCodexBridge plugin;
    private final DatabaseSettings settings;
    private final java.nio.file.Path sqlite;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "MagicCodex-fish-tank"); t.setDaemon(true); return t; });
    private final Map<UUID, Tank> tanks = new HashMap<>();   // main thread
    private Connection connection;
    private volatile int capacity = 100;

    FishTank(MagicCodexBridge plugin) {
        this.plugin = plugin;
        DatabaseSettings s;
        try {
            var folder = plugin.getDataFolder().toPath();
            var dedicated = folder.resolve("player-state-database.properties");
            s = DatabaseSettings.load(java.nio.file.Files.exists(dedicated) ? dedicated : folder.resolve("database.properties"));
        } catch (Exception e) { s = new DatabaseSettings(false, "", "", ""); }
        settings = s;
        sqlite = plugin.getDataFolder().toPath().resolve("fishing.db");
        io.execute(() -> {
            try (var st = db().createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS codex_fish_tank(id VARCHAR(36) PRIMARY KEY,uuid VARCHAR(36) NOT NULL,fish VARCHAR(64) NOT NULL,size DOUBLE NOT NULL,caught_at BIGINT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS codex_fish_lock(uuid VARCHAR(36) NOT NULL,fish VARCHAR(64) NOT NULL,PRIMARY KEY(uuid,fish))");
                st.execute("CREATE TABLE IF NOT EXISTS codex_fish_item(id VARCHAR(36) PRIMARY KEY,fish VARCHAR(64) NOT NULL,size DOUBLE NOT NULL,caught_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
                try { st.execute("CREATE INDEX " + (settings.mariaDb() ? "" : "IF NOT EXISTS ") + "codex_fish_tank_uuid ON codex_fish_tank(uuid)"); } catch (Exception ignored) {}
            } catch (Exception e) { plugin.getLogger().warning("통발 수조 테이블 준비 실패: " + e.getMessage()); }
        });
    }

    void capacity(int n) { capacity = Math.clamp(n, 1, 10000); }
    int capacity() { return capacity; }

    private Connection db() throws Exception {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            if (connection != null) try { connection.close(); } catch (Exception ignored) {}
            connection = settings.connect(sqlite);
            settings.configure(connection);
        }
        return connection;
    }

    // ------------------------------------------------------------------ reading (main thread)

    Tank tank(UUID player) { return tanks.computeIfAbsent(player, k -> new Tank()); }
    int count(UUID player) { return tank(player).fish.size(); }
    boolean full(UUID player) { return count(player) >= capacity; }
    boolean loaded(UUID player) { return tank(player).loaded; }

    /** Re-reads the tank from the database (queued after any write still in flight), then runs done. */
    void refresh(UUID player, Runnable done) {
        var tank = tank(player);
        if (done != null) tank.waiting.add(done);
        if (tank.loading) return;
        tank.loading = true;
        io.execute(() -> {
            var fish = new ArrayList<Fish>(); var locks = new HashSet<String>();
            boolean ok = true;
            try (var p = db().prepareStatement("SELECT id,fish,size,caught_at FROM codex_fish_tank WHERE uuid=? ORDER BY caught_at,id")) {
                p.setString(1, player.toString());
                try (var r = p.executeQuery()) { while (r.next()) fish.add(new Fish(r.getString(1), r.getString(2), r.getDouble(3), r.getLong(4))); }
                try (var q = db().prepareStatement("SELECT fish FROM codex_fish_lock WHERE uuid=?")) {
                    q.setString(1, player.toString());
                    try (var r = q.executeQuery()) { while (r.next()) locks.add(r.getString(1)); }
                }
            } catch (Exception e) { ok = false; plugin.getLogger().warning("통발 수조 읽기 실패 " + player + ": " + e.getMessage()); }
            boolean read = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (read) { tank.fish.clear(); tank.fish.addAll(fish); tank.locks.clear(); tank.locks.addAll(locks); tank.loaded = true; }
                tank.loading = false;
                var list = List.copyOf(tank.waiting); tank.waiting.clear();
                for (Runnable r : list) r.run();
            });
        });
    }
    void forget(UUID player) { tanks.remove(player); }

    // ------------------------------------------------------------------ writing

    /** Puts one fish in the tank (caller checked capacity). Main thread. */
    Fish add(UUID player, String fishId, double size) {
        var f = new Fish(UUID.randomUUID().toString(), fishId, size, System.currentTimeMillis());
        tank(player).fish.add(f);
        io.execute(() -> {
            try (var p = db().prepareStatement("INSERT INTO codex_fish_tank(id,uuid,fish,size,caught_at) VALUES(?,?,?,?,?)")) {
                p.setString(1, f.id()); p.setString(2, player.toString()); p.setString(3, f.fish()); p.setDouble(4, f.size()); p.setLong(5, f.caughtAt());
                p.executeUpdate();
            } catch (Exception e) { plugin.getLogger().severe("통발 수조 저장 실패 " + player + " " + fishId + " " + size + "cm: " + e.getMessage()); }
        });
        return f;
    }

    enum Put { OK, DUPLICATE, FULL, FAILED }
    /**
     * Puts this exact fish (same row id) into the tank: a fish item going back in, or a sale that could not be paid.
     * The id is the primary key, so a duplicated item can never become two fish. The capacity is checked again in the
     * database (another server may have filled the tank). done (main thread, may be null) gets the outcome.
     */
    void put(UUID player, Fish f, Consumer<Put> done) {
        int cap = capacity;
        boolean checkCapacity = done != null;
        io.execute(() -> {
            Put out;
            try {
                int n = 0;
                if (checkCapacity) try (var c = db().prepareStatement("SELECT COUNT(*) FROM codex_fish_tank WHERE uuid=?")) {
                    c.setString(1, player.toString());
                    try (var r = c.executeQuery()) { if (r.next()) n = r.getInt(1); }
                }
                if (checkCapacity && n >= cap) out = Put.FULL;
                else try (var p = db().prepareStatement("INSERT INTO codex_fish_tank(id,uuid,fish,size,caught_at) VALUES(?,?,?,?,?)")) {
                    p.setString(1, f.id()); p.setString(2, player.toString()); p.setString(3, f.fish()); p.setDouble(4, f.size()); p.setLong(5, f.caughtAt());
                    p.executeUpdate();
                    out = Put.OK;
                }
            } catch (java.sql.SQLException e) {
                String state = e.getSQLState();
                String text = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                boolean dup = e instanceof java.sql.SQLIntegrityConstraintViolationException || state != null && state.startsWith("23")
                        || text.contains("constraint") || text.contains("duplicate");   // xerial SQLite: SQLITE_CONSTRAINT, no SQL state
                out = dup ? Put.DUPLICATE : Put.FAILED;
                if (out == Put.FAILED) plugin.getLogger().severe("통발 수조 넣기 실패 " + player + " " + f + ": " + e.getMessage());
            } catch (Exception e) { out = Put.FAILED; plugin.getLogger().severe("통발 수조 넣기 실패 " + player + " " + f + ": " + e.getMessage()); }
            Put result = out;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result == Put.OK) { var t = tank(player); t.fish.removeIf(x -> x.id().equals(f.id())); t.fish.add(f); }
                if (done != null) done.accept(result);
            });
        });
    }

    /**
     * Removes these fish; done gets only the ones that were really still in the database (main thread).
     * Used by selling and taking out, so a fish is never paid or given twice.
     */
    void remove(UUID player, Collection<Fish> fish, Consumer<List<Fish>> done) {
        var list = List.copyOf(fish);
        io.execute(() -> {
            var removed = new ArrayList<Fish>();
            try (var p = db().prepareStatement("DELETE FROM codex_fish_tank WHERE id=? AND uuid=?")) {
                for (Fish f : list) {
                    p.setString(1, f.id()); p.setString(2, player.toString());
                    if (p.executeUpdate() == 1) removed.add(f);
                }
            } catch (Exception e) { plugin.getLogger().warning("통발 수조 꺼내기 실패 " + player + ": " + e.getMessage()); }
            Bukkit.getScheduler().runTask(plugin, () -> {
                var ids = new HashSet<String>(); for (Fish f : list) ids.add(f.id());
                tank(player).fish.removeIf(f -> ids.contains(f.id()));
                done.accept(removed);
            });
        });
    }

    // ------------------------------------------------------------------ fish items (taken out of the tank)

    /**
     * Takes this fish out as an item: its tank row moves to codex_fish_item (same id). done(true) only when the tank
     * row was really there (main thread).
     */
    void takeOut(UUID player, Fish f, Consumer<Boolean> done) {
        io.execute(() -> {
            boolean moved = false;
            try (var d = db().prepareStatement("DELETE FROM codex_fish_tank WHERE id=? AND uuid=?")) {
                d.setString(1, f.id()); d.setString(2, player.toString());
                if (d.executeUpdate() == 1) {
                    moved = true;
                    try (var i = db().prepareStatement("INSERT INTO codex_fish_item(id,fish,size,caught_at,updated_at) VALUES(?,?,?,?,?)")) {
                        i.setString(1, f.id()); i.setString(2, f.fish()); i.setDouble(3, f.size()); i.setLong(4, f.caughtAt()); i.setLong(5, System.currentTimeMillis());
                        i.executeUpdate();
                    }
                }
            } catch (Exception e) { plugin.getLogger().severe("통발 아이템화 저장 실패 " + player + " " + f + ": " + e.getMessage()); }
            boolean ok = moved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok) tank(player).fish.removeIf(x -> x.id().equals(f.id()));
                done.accept(ok);
            });
        });
    }

    /** Registers a fish item made outside the tank (admin gift) so it can be put in later. */
    void registerItem(Fish f) {
        io.execute(() -> {
            try (var i = db().prepareStatement("INSERT INTO codex_fish_item(id,fish,size,caught_at,updated_at) VALUES(?,?,?,?,?)")) {
                i.setString(1, f.id()); i.setString(2, f.fish()); i.setDouble(3, f.size()); i.setLong(4, f.caughtAt()); i.setLong(5, System.currentTimeMillis());
                i.executeUpdate();
            } catch (Exception e) { plugin.getLogger().warning("물고기 아이템 등록 실패 " + f + ": " + e.getMessage()); }
        });
    }

    /** Size / caught date of fish items by id (only the ones that exist), main thread. */
    void itemInfo(Collection<String> ids, Consumer<Map<String, Fish>> done) {
        var list = List.copyOf(ids);
        if (list.isEmpty()) { done.accept(Map.of()); return; }
        io.execute(() -> {
            var out = new HashMap<String, Fish>();
            try (var p = db().prepareStatement("SELECT id,fish,size,caught_at FROM codex_fish_item WHERE id=?")) {
                for (String id : list) {
                    p.setString(1, id);
                    try (var r = p.executeQuery()) { if (r.next()) out.put(id, new Fish(r.getString(1), r.getString(2), r.getDouble(3), r.getLong(4))); }
                }
            } catch (Exception e) { plugin.getLogger().warning("물고기 아이템 읽기 실패: " + e.getMessage()); }
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(out));
        });
    }

    /**
     * Puts a fish item back: its codex_fish_item row moves into the tank (capacity checked in the database).
     * DUPLICATE = no such item row (a copied item, or already put back); the Fish is the restored row on OK.
     */
    void putBack(UUID player, String id, java.util.function.BiConsumer<Put, Fish> done) {
        int cap = capacity;
        io.execute(() -> {
            Put out = Put.FAILED; Fish restored = null;
            try {
                int n = 0;
                try (var c = db().prepareStatement("SELECT COUNT(*) FROM codex_fish_tank WHERE uuid=?")) {
                    c.setString(1, player.toString());
                    try (var r = c.executeQuery()) { if (r.next()) n = r.getInt(1); }
                }
                if (n >= cap) out = Put.FULL;
                else {
                    Fish row = null;
                    try (var q = db().prepareStatement("SELECT id,fish,size,caught_at FROM codex_fish_item WHERE id=?")) {
                        q.setString(1, id);
                        try (var r = q.executeQuery()) { if (r.next()) row = new Fish(r.getString(1), r.getString(2), r.getDouble(3), r.getLong(4)); }
                    }
                    if (row == null) out = Put.DUPLICATE;
                    else try (var d = db().prepareStatement("DELETE FROM codex_fish_item WHERE id=?")) {
                        d.setString(1, id);
                        if (d.executeUpdate() != 1) out = Put.DUPLICATE;
                        else try (var i = db().prepareStatement("INSERT INTO codex_fish_tank(id,uuid,fish,size,caught_at) VALUES(?,?,?,?,?)")) {
                            i.setString(1, row.id()); i.setString(2, player.toString()); i.setString(3, row.fish()); i.setDouble(4, row.size()); i.setLong(5, row.caughtAt());
                            i.executeUpdate();
                            out = Put.OK; restored = row;
                        } catch (Exception insert) {
                            // never lose the fish: the item row comes back
                            try (var back = db().prepareStatement("INSERT INTO codex_fish_item(id,fish,size,caught_at,updated_at) VALUES(?,?,?,?,?)")) {
                                back.setString(1, row.id()); back.setString(2, row.fish()); back.setDouble(3, row.size()); back.setLong(4, row.caughtAt()); back.setLong(5, System.currentTimeMillis());
                                back.executeUpdate();
                            }
                            throw insert;
                        }
                    }
                }
            } catch (Exception e) { out = Put.FAILED; plugin.getLogger().severe("통발 넣기 실패 " + player + " " + id + ": " + e.getMessage()); }
            Put result = out; Fish fish = restored;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result == Put.OK) { var t = tank(player); t.fish.removeIf(x -> x.id().equals(fish.id())); t.fish.add(fish); }
                done.accept(result, fish);
            });
        });
    }

    /** Replaces the locked species. Main thread. */
    void locks(UUID player, Set<String> species) {
        var tank = tank(player);
        tank.locks.clear(); tank.locks.addAll(species);
        var copy = Set.copyOf(species);
        io.execute(() -> {
            try (var d = db().prepareStatement("DELETE FROM codex_fish_lock WHERE uuid=?"); var i = db().prepareStatement("INSERT INTO codex_fish_lock(uuid,fish) VALUES(?,?)")) {
                d.setString(1, player.toString()); d.executeUpdate();
                for (String s : copy) { i.setString(1, player.toString()); i.setString(2, s); i.executeUpdate(); }
            } catch (Exception e) { plugin.getLogger().warning("물고기 잠금 저장 실패 " + player + ": " + e.getMessage()); }
        });
    }

    @Override public void close() {
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }
}
