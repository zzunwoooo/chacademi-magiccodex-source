package school.chacademia.housing;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.HousingProtocol.Entry;
import school.magiccodex.protocol.HousingProtocol.Friend;
import school.magiccodex.protocol.HousingProtocol.Person;
import school.magiccodex.protocol.HousingProtocol.Viewer;
import school.magiccodex.protocol.HousingRules;
import school.magiccodex.protocol.HousingRules.Def;
import school.magiccodex.protocol.HousingRules.Layout;
import school.magiccodex.protocol.SchoolProtocol;

/**
 * 방·레이아웃·보유 가구·좋아요·방문·방명록·구경 중 명단. 공용 MariaDB(codex_housing_*)를 학교·야생이 함께 쓴다.
 * SQL 은 전부 작업 스레드 하나에서 돌고, 결과는 메인 스레드로 돌아온다. 실패하면 경고를 남기고 결과는 null.
 * MagicCodexBridge 테이블(닉네임·기숙사·친구)은 읽기만 한다.
 */
final class HousingStore {
    record RoomRow(UUID owner, String name, int house, String skin, boolean slim, int size, int active, int likes, int visits, long rev) {
        Person person() { return new Person(owner, name.isBlank() ? "학생" : name, house, skin, slim); }
    }
    record RoomData(RoomRow row, Map<Integer, Layout> layouts, int today, boolean liked) {}
    record Presence(UUID viewer, UUID owner, String name) {}
    record Sync(Map<UUID, List<Viewer>> viewers, Map<UUID, Long> revs) {}

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final long PRESENCE_TTL = 8_000, ONLINE_TTL = 75_000;

    private final Plugin plugin; private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "ChacaHousing-db"); t.setDaemon(true); return t; });
    private ConnectionHolder db; private DatabaseSettings settings; private boolean maria;
    private String tRoom, tLayout, tLike, tVisit, tBook, tPresence, tOnline, tFriends, tNick, tHouse;

    HousingStore(Plugin plugin) { this.plugin = plugin; this.log = plugin.getLogger(); }

    void open(Path dataFolder) throws Exception {
        io.submit(() -> {
            Path file = dataFolder.resolve("database.properties");
            settings = DatabaseSettings.load(file);
            log.info(DatabaseSettings.describe(file, settings));
            if (!settings.mariaDb()) log.warning("SQLite 모드: 이 서버 안에서만 방이 보이고 닉네임·기숙사·친구 목록은 읽지 못합니다. 학교·야생 공용 MariaDB 를 설정해 주세요.");
            maria = settings.mariaDb();
            db = settings.holder(dataFolder.resolve("housing.db"));
            tRoom = settings.table("housing_room", "housing_room");
            tLayout = settings.table("housing_layout", "housing_layout");
            tLike = settings.table("housing_like", "housing_like");
            tVisit = settings.table("housing_visit", "housing_visit");
            tBook = settings.table("housing_guestbook", "housing_guestbook");
            tPresence = settings.table("housing_presence", "housing_presence");
            tOnline = settings.table("housing_online", "housing_online");
            tFriends = settings.table("friends", "friends");
            tNick = settings.table("nicknames", "nicknames");
            tHouse = settings.table("houses", "houses");
            String engine = maria ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
            try (var s = db.get().createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS " + tRoom + "(owner CHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, house INT NOT NULL,"
                        + " skin VARCHAR(64) NOT NULL, slim INT NOT NULL, size INT NOT NULL, active INT NOT NULL, likes INT NOT NULL, visits INT NOT NULL,"
                        + " rev BIGINT NOT NULL)" + engine);
                s.execute("CREATE TABLE IF NOT EXISTS " + tLayout + "(owner CHAR(36) NOT NULL, slot INT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(owner, slot))" + engine);
                s.execute("CREATE TABLE IF NOT EXISTS " + tLike + "(owner CHAR(36) NOT NULL, liker CHAR(36) NOT NULL, created BIGINT NOT NULL, PRIMARY KEY(owner, liker))" + engine);
                s.execute("CREATE TABLE IF NOT EXISTS " + tVisit + "(owner CHAR(36) NOT NULL, visitor CHAR(36) NOT NULL, day INT NOT NULL, PRIMARY KEY(owner, visitor, day))" + engine);
                s.execute("CREATE TABLE IF NOT EXISTS " + tBook + "(" + (maria ? "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "id INTEGER PRIMARY KEY AUTOINCREMENT")
                        + ", owner CHAR(36) NOT NULL, author CHAR(36) NOT NULL, name VARCHAR(32) NOT NULL, house INT NOT NULL, skin VARCHAR(64) NOT NULL,"
                        + " slim INT NOT NULL, created BIGINT NOT NULL, body VARCHAR(255) NOT NULL" + (maria ? ", KEY ix_housing_book(owner, id)" : "") + ")" + engine);
                if (!maria) s.execute("CREATE INDEX IF NOT EXISTS ix_housing_book ON " + tBook + "(owner, id)");
                s.execute("CREATE TABLE IF NOT EXISTS " + tPresence + "(viewer CHAR(36) NOT NULL PRIMARY KEY, owner CHAR(36) NOT NULL, name VARCHAR(32) NOT NULL,"
                        + " server VARCHAR(32) NOT NULL, seen BIGINT NOT NULL" + (maria ? ", KEY ix_housing_presence(owner)" : "") + ")" + engine);
                if (!maria) s.execute("CREATE INDEX IF NOT EXISTS ix_housing_presence ON " + tPresence + "(owner)");
                s.execute("CREATE TABLE IF NOT EXISTS " + tOnline + "(player CHAR(36) NOT NULL PRIMARY KEY, server VARCHAR(32) NOT NULL, seen BIGINT NOT NULL)" + engine);
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    void close() {
        io.shutdown();
        try { io.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { if (db != null) db.close(); } catch (Exception ignored) { }
    }

    interface Sql<T> { T run(Connection c) throws SQLException; }

    private <T> void async(String what, Sql<T> work, Consumer<T> done) {
        if (io.isShutdown()) return;
        io.execute(() -> {
            T v;
            try { v = work.run(db.get()); }
            catch (SQLException | RuntimeException e) { log.warning("하우징 DB 작업 실패 (" + what + "): " + e.getMessage()); v = null; }
            T out = v;
            if (done != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> done.accept(out));
        });
    }
    private <T> T tx(Sql<T> work) throws SQLException {
        Connection c = db.begin();
        try { T v = work.run(c); c.commit(); return v; }
        catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
        finally { db.end(); }
    }

    // ------------------------------------------------------------ SQL 방언
    private String upsert(String table, String keys, String... cols) {
        var all = new ArrayList<String>(List.of(keys.split(","))); all.addAll(List.of(cols));
        String marks = String.join(",", Collections.nCopies(all.size(), "?"));
        var set = new StringJoiner(",");
        for (String c : cols) set.add(maria ? c + "=VALUES(" + c + ")" : c + "=excluded." + c);
        return "INSERT INTO " + table + "(" + String.join(",", all) + ") VALUES(" + marks + ") "
                + (maria ? "ON DUPLICATE KEY UPDATE " : "ON CONFLICT(" + keys + ") DO UPDATE SET ") + set;
    }
    private String insertIgnore() { return maria ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO "; }
    static int today() { var d = LocalDate.now(KST); return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth(); }
    private static String in(int n) { return String.join(",", Collections.nCopies(n, "?")); }

    // ------------------------------------------------------------ 방
    /**
     * 접속할 때: 방이 없으면 만들고, 닉네임·기숙사·스킨을 최신으로 맞춘다.
     * 기숙사는 Bridge 와 같은 순서: codex_houses → (없으면) fallbackHouse (Bridge StatsService·magiccodex.house.* 권한, 메인 스레드에서 구함).
     * allowed = 지금 권한으로 놓을 수 있는 가구 → 권한이 빠진 가구는 저장된 레이아웃에서 뺀다.
     */
    void touch(UUID id, String fallback, int fallbackHouse, String skin, boolean slim, int startSize, Function<String, Def> defs, Map<String, Integer> allowed, Consumer<RoomRow> done) {
        async("접속 갱신", c -> {
            String name = nickname(c, id); if (name == null || name.isBlank()) name = fallback;
            int house = house(c, id); if (house < 0) house = fallbackHouse;
            RoomRow row = room(c, id);
            if (row == null) {
                try (var s = c.prepareStatement(insertIgnore() + tRoom + "(owner,name,house,skin,slim,size,active,likes,visits,rev) VALUES(?,?,?,?,?,?,0,0,0,1)")) {
                    s.setString(1, id.toString()); s.setString(2, cut(name)); s.setInt(3, house); s.setString(4, skin); s.setInt(5, slim ? 1 : 0);
                    s.setInt(6, Math.clamp(startSize, HousingRules.MIN_SIZE, HousingRules.MAX_SIZE)); s.executeUpdate();
                }
            } else if (!row.name().equals(cut(name)) || row.house() != house || !row.skin().equals(skin) || row.slim() != slim) {
                try (var s = c.prepareStatement("UPDATE " + tRoom + " SET name=?,house=?,skin=?,slim=?,rev=rev+1 WHERE owner=?")) {
                    s.setString(1, cut(name)); s.setInt(2, house); s.setString(3, skin); s.setInt(4, slim ? 1 : 0); s.setString(5, id.toString()); s.executeUpdate();
                }
            }
            RoomRow now = room(c, id);
            if (now != null) pruneAll(c, id, now.size(), defs, allowed);
            return now;
        }, done);
    }

    /** 방 전체. viewer 가 주인이 아니고 countVisit 이면 오늘 방문을 한 번 센다. 방이 없으면 null. */
    void load(UUID owner, UUID viewer, boolean countVisit, Consumer<RoomData> done) {
        async("방 읽기", c -> {
            if (room(c, owner) == null) return null;
            int day = today();
            if (countVisit && !owner.equals(viewer)) {
                int added;
                try (var s = c.prepareStatement(insertIgnore() + tVisit + "(owner,visitor,day) VALUES(?,?,?)")) {
                    s.setString(1, owner.toString()); s.setString(2, viewer.toString()); s.setInt(3, day); added = s.executeUpdate();
                }
                if (added > 0) try (var s = c.prepareStatement("UPDATE " + tRoom + " SET visits=visits+1,rev=rev+1 WHERE owner=?")) { s.setString(1, owner.toString()); s.executeUpdate(); }
            }
            RoomRow row = room(c, owner);
            var layouts = layouts(c, owner);
            int today;
            try (var s = c.prepareStatement("SELECT COUNT(*) FROM " + tVisit + " WHERE owner=? AND day=?")) {
                s.setString(1, owner.toString()); s.setInt(2, day);
                try (var r = s.executeQuery()) { today = r.next() ? r.getInt(1) : 0; }
            }
            boolean liked;
            try (var s = c.prepareStatement("SELECT 1 FROM " + tLike + " WHERE owner=? AND liker=?")) {
                s.setString(1, owner.toString()); s.setString(2, viewer.toString());
                try (var r = s.executeQuery()) { liked = r.next(); }
            }
            return new RoomData(row, layouts, today, liked);
        }, done);
    }

    /** @return null = 저장됨, 아니면 이유 */
    void save(UUID owner, int slot, Layout layout, Function<String, Def> defs, Map<String, Integer> allowed, Consumer<String> done) {
        async("레이아웃 저장", c -> tx(t -> {
            RoomRow row = room(t, owner);
            if (row == null) return "방을 찾을 수 없어요.";
            String why = HousingRules.validate(layout, row.size(), defs, allowed);
            if (why != null) return why;
            try (var s = t.prepareStatement(upsert(tLayout, "owner,slot", "data"))) {
                s.setString(1, owner.toString()); s.setInt(2, slot); s.setString(3, HousingRules.encode(layout)); s.executeUpdate();
            }
            try (var s = t.prepareStatement("UPDATE " + tRoom + " SET active=?,rev=rev+1 WHERE owner=?")) { s.setInt(1, slot); s.setString(2, owner.toString()); s.executeUpdate(); }
            return "";
        }), r -> done.accept(r == null ? "저장하지 못했어요. 잠시 후 다시 시도해 주세요." : r.isEmpty() ? null : r));
    }

    /** 지금 크기가 from 일 때만 to 로 (두 번 눌러도 한 번만). @return true = 넓어짐, false = 크기가 달라짐, null = DB 오류 */
    void expand(UUID owner, int from, int to, Consumer<Boolean> done) {
        async("방 확장", c -> {
            try (var s = c.prepareStatement("UPDATE " + tRoom + " SET size=?,rev=rev+1 WHERE owner=? AND size=?")) {
                s.setInt(1, to); s.setString(2, owner.toString()); s.setInt(3, from); return s.executeUpdate() > 0;
            }
        }, done);
    }

    /** 관리자: 크기 바꾸기 (줄이면 밖으로 나간 가구는 보관함으로). */
    void setSize(UUID owner, int size, Function<String, Def> defs, Map<String, Integer> limits, Consumer<Boolean> done) {
        async("방 크기 설정", c -> tx(t -> {
            int n;
            try (var s = t.prepareStatement("UPDATE " + tRoom + " SET size=?,rev=rev+1 WHERE owner=?")) { s.setInt(1, size); s.setString(2, owner.toString()); n = s.executeUpdate(); }
            if (n == 0) return false;
            pruneAll(t, owner, size, defs, limits);
            return true;
        }), done);
    }

    /** 좋아요 켜기/끄기. @return 새 상태 (true = 좋아요) */
    void like(UUID owner, UUID liker, Consumer<Boolean> done) {
        async("좋아요", c -> tx(t -> {
            boolean had;
            try (var s = t.prepareStatement("SELECT 1 FROM " + tLike + " WHERE owner=? AND liker=?")) {
                s.setString(1, owner.toString()); s.setString(2, liker.toString()); try (var r = s.executeQuery()) { had = r.next(); }
            }
            if (had) try (var s = t.prepareStatement("DELETE FROM " + tLike + " WHERE owner=? AND liker=?")) { s.setString(1, owner.toString()); s.setString(2, liker.toString()); s.executeUpdate(); }
            else try (var s = t.prepareStatement("INSERT INTO " + tLike + "(owner,liker,created) VALUES(?,?,?)")) { s.setString(1, owner.toString()); s.setString(2, liker.toString()); s.setLong(3, System.currentTimeMillis()); s.executeUpdate(); }
            try (var s = t.prepareStatement("UPDATE " + tRoom + " SET likes=" + (had ? "CASE WHEN likes>0 THEN likes-1 ELSE 0 END" : "likes+1") + ",rev=rev+1 WHERE owner=?")) {
                s.setString(1, owner.toString()); s.executeUpdate();
            }
            return !had;
        }), done);
    }

    // ------------------------------------------------------------ 방명록
    void post(UUID owner, Person author, String text, int keep, Consumer<Boolean> done) {
        async("방명록 쓰기", c -> tx(t -> {
            try (var s = t.prepareStatement("INSERT INTO " + tBook + "(owner,author,name,house,skin,slim,created,body) VALUES(?,?,?,?,?,?,?,?)")) {
                s.setString(1, owner.toString()); s.setString(2, author.id().toString()); s.setString(3, cut(author.name())); s.setInt(4, author.house());
                s.setString(5, author.skin()); s.setInt(6, author.slim() ? 1 : 0); s.setLong(7, System.currentTimeMillis()); s.setString(8, text); s.executeUpdate();
            }
            Long floor = null;
            try (var s = t.prepareStatement("SELECT id FROM " + tBook + " WHERE owner=? ORDER BY id DESC LIMIT 1 OFFSET ?")) {
                s.setString(1, owner.toString()); s.setInt(2, Math.max(0, keep - 1));
                try (var r = s.executeQuery()) { if (r.next()) floor = r.getLong(1); }
            }
            if (floor != null) try (var s = t.prepareStatement("DELETE FROM " + tBook + " WHERE owner=? AND id<?")) { s.setString(1, owner.toString()); s.setLong(2, floor); s.executeUpdate(); }
            bump(t, owner);
            return true;
        }), done);
    }

    /** @return null = 지움, 아니면 이유 */
    void deleteEntry(UUID owner, long id, UUID requester, boolean admin, Consumer<String> done) {
        async("방명록 지우기", c -> tx(t -> {
            UUID author;
            try (var s = t.prepareStatement("SELECT author FROM " + tBook + " WHERE owner=? AND id=?")) {
                s.setString(1, owner.toString()); s.setLong(2, id);
                try (var r = s.executeQuery()) { if (!r.next()) return "이미 지워진 글이에요."; author = UUID.fromString(r.getString(1)); }
            }
            if (!admin && !requester.equals(owner) && !requester.equals(author)) return "내 방이나 내가 쓴 글만 지울 수 있어요.";
            try (var s = t.prepareStatement("DELETE FROM " + tBook + " WHERE owner=? AND id=?")) { s.setString(1, owner.toString()); s.setLong(2, id); s.executeUpdate(); }
            bump(t, owner);
            return "";
        }), r -> done.accept(r == null ? "지우지 못했어요." : r.isEmpty() ? null : r));
    }

    void guestbook(UUID owner, int limit, Consumer<List<Entry>> done) {
        async("방명록 읽기", c -> {
            var list = new ArrayList<Entry>();
            try (var s = c.prepareStatement("SELECT id,author,name,house,skin,slim,created,body FROM " + tBook + " WHERE owner=? ORDER BY id DESC LIMIT ?")) {
                s.setString(1, owner.toString()); s.setInt(2, limit);
                try (var r = s.executeQuery()) {
                    while (r.next()) {
                        try {
                            var p = new Person(UUID.fromString(r.getString(2)), r.getString(3), Math.clamp(r.getInt(4), -1, 3), skinOrEmpty(r.getString(5)), r.getInt(6) != 0);
                            list.add(new Entry(r.getLong(1), p, r.getLong(7), r.getString(8)));
                        } catch (IllegalArgumentException bad) { /* 깨진 줄은 건너뛴다 */ }
                    }
                }
            }
            return list;
        }, done);
    }

    // ------------------------------------------------------------ 친구 방 목록
    void friends(UUID viewer, int limit, Consumer<List<Friend>> done) {
        async("친구 목록", c -> {
            record F(UUID id, String name, int house) {}
            var list = new ArrayList<F>();
            try (var s = c.prepareStatement("SELECT target,name,dorm FROM " + tFriends + " WHERE owner=? ORDER BY name LIMIT ?")) {
                s.setString(1, viewer.toString()); s.setInt(2, limit);
                try (var r = s.executeQuery()) {
                    while (r.next()) {
                        try { list.add(new F(UUID.fromString(r.getString(1)), r.getString(2), SchoolProtocol.HOUSES.indexOf(r.getString(3)))); }
                        catch (IllegalArgumentException bad) { /* 건너뜀 */ }
                    }
                }
            } catch (SQLException missing) { return List.<Friend>of(); } // SQLite 모드 등 친구 테이블 없음
            if (list.isEmpty()) return List.<Friend>of();
            var ids = list.stream().map(f -> f.id().toString()).toList();
            var rooms = new HashMap<UUID, RoomRow>();
            try (var s = c.prepareStatement("SELECT * FROM " + tRoom + " WHERE owner IN (" + in(ids.size()) + ")")) {
                for (int i = 0; i < ids.size(); i++) s.setString(i + 1, ids.get(i));
                try (var r = s.executeQuery()) { while (r.next()) { var row = row(r); rooms.put(row.owner(), row); } }
            }
            var online = new HashSet<UUID>();
            try (var s = c.prepareStatement("SELECT player FROM " + tOnline + " WHERE seen>? AND player IN (" + in(ids.size()) + ")")) {
                s.setLong(1, System.currentTimeMillis() - ONLINE_TTL);
                for (int i = 0; i < ids.size(); i++) s.setString(i + 2, ids.get(i));
                try (var r = s.executeQuery()) { while (r.next()) online.add(UUID.fromString(r.getString(1))); }
            }
            var out = new ArrayList<Friend>();
            for (var f : list) {
                var room = rooms.get(f.id());
                String name = room != null && !room.name().isBlank() ? room.name() : f.name();
                try {
                    var p = new Person(f.id(), name == null || name.isBlank() ? "학생" : cut(name), room != null ? room.house() : f.house(),
                            room != null ? room.skin() : "", room != null && room.slim());
                    out.add(new Friend(p, online.contains(f.id()), room != null ? room.size() : 0));
                } catch (IllegalArgumentException bad) { /* 건너뜀 */ }
            }
            out.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.person().name()));
            return List.copyOf(out);
        }, done);
    }

    /** 닉네임(또는 저장된 방 이름)으로 찾기. 없으면 null. */
    void find(String name, Consumer<UUID> done) {
        async("닉네임 찾기", c -> {
            try (var s = c.prepareStatement("SELECT owner FROM " + tNick + " WHERE LOWER(nickname)=LOWER(?) LIMIT 1")) {
                s.setString(1, name); try (var r = s.executeQuery()) { if (r.next()) return UUID.fromString(r.getString(1)); }
            } catch (SQLException missing) { /* SQLite 모드 */ }
            try (var s = c.prepareStatement("SELECT owner FROM " + tRoom + " WHERE LOWER(name)=LOWER(?) LIMIT 1")) {
                s.setString(1, name); try (var r = s.executeQuery()) { return r.next() ? UUID.fromString(r.getString(1)) : null; }
            }
        }, done);
    }

    // ------------------------------------------------------------ 구경 중 명단 · 접속 표시
    /**
     * 이 서버의 구경 중 명단을 올리고(seen 갱신), owners 방에 지금 있는 사람(모든 서버)과 방 revision 을 읽는다.
     * 오래된 줄은 지운다.
     */
    void sync(String server, List<Presence> local, Collection<UUID> owners, Consumer<Sync> done) {
        async("구경 중 명단", c -> {
            long now = System.currentTimeMillis();
            if (!local.isEmpty()) try (var s = c.prepareStatement(upsert(tPresence, "viewer", "owner", "name", "server", "seen"))) {
                for (var p : local) { s.setString(1, p.viewer().toString()); s.setString(2, p.owner().toString()); s.setString(3, cut(p.name())); s.setString(4, server); s.setLong(5, now); s.addBatch(); }
                s.executeBatch();
            }
            try (var s = c.prepareStatement("DELETE FROM " + tPresence + " WHERE seen<?")) { s.setLong(1, now - PRESENCE_TTL * 4); s.executeUpdate(); }
            var viewers = new HashMap<UUID, List<Viewer>>(); var revs = new HashMap<UUID, Long>();
            if (owners.isEmpty()) return new Sync(viewers, revs);
            var ids = owners.stream().map(UUID::toString).toList();
            try (var s = c.prepareStatement("SELECT owner,viewer,name FROM " + tPresence + " WHERE seen>? AND owner IN (" + in(ids.size()) + ") ORDER BY seen")) {
                s.setLong(1, now - PRESENCE_TTL);
                for (int i = 0; i < ids.size(); i++) s.setString(i + 2, ids.get(i));
                try (var r = s.executeQuery()) {
                    while (r.next()) viewers.computeIfAbsent(UUID.fromString(r.getString(1)), k -> new ArrayList<>()).add(new Viewer(UUID.fromString(r.getString(2)), r.getString(3)));
                }
            }
            try (var s = c.prepareStatement("SELECT owner,rev FROM " + tRoom + " WHERE owner IN (" + in(ids.size()) + ")")) {
                for (int i = 0; i < ids.size(); i++) s.setString(i + 1, ids.get(i));
                try (var r = s.executeQuery()) { while (r.next()) revs.put(UUID.fromString(r.getString(1)), r.getLong(2)); }
            }
            return new Sync(viewers, revs);
        }, done);
    }
    void leave(UUID viewer) {
        async("구경 끝", c -> { try (var s = c.prepareStatement("DELETE FROM " + tPresence + " WHERE viewer=?")) { s.setString(1, viewer.toString()); s.executeUpdate(); } return null; }, null);
    }
    void heartbeat(String server, List<UUID> players) {
        if (players.isEmpty()) return;
        async("접속 표시", c -> {
            long now = System.currentTimeMillis();
            try (var s = c.prepareStatement(upsert(tOnline, "player", "server", "seen"))) {
                for (var id : players) { s.setString(1, id.toString()); s.setString(2, server); s.setLong(3, now); s.addBatch(); }
                s.executeBatch();
            }
            try (var s = c.prepareStatement("DELETE FROM " + tOnline + " WHERE seen<?")) { s.setLong(1, now - ONLINE_TTL * 4); s.executeUpdate(); }
            try (var s = c.prepareStatement("DELETE FROM " + tVisit + " WHERE day<?")) { s.setInt(1, today() - 1); s.executeUpdate(); }
            return null;
        }, null);
    }
    /** 나갈 때: 다른 서버로 옮겨 가는 중이면 그 서버가 곧 다시 올린다. 이 서버 줄만 지운다. */
    void offline(UUID id, String server) {
        async("접속 끝", c -> {
            try (var s = c.prepareStatement("DELETE FROM " + tOnline + " WHERE player=? AND server=?")) { s.setString(1, id.toString()); s.setString(2, server); s.executeUpdate(); }
            try (var s = c.prepareStatement("DELETE FROM " + tPresence + " WHERE viewer=? AND server=?")) { s.setString(1, id.toString()); s.setString(2, server); s.executeUpdate(); }
            return null;
        }, null);
    }

    // ------------------------------------------------------------ 내부
    private void bump(Connection c, UUID owner) throws SQLException {
        try (var s = c.prepareStatement("UPDATE " + tRoom + " SET rev=rev+1 WHERE owner=?")) { s.setString(1, owner.toString()); s.executeUpdate(); }
    }
    private void pruneAll(Connection c, UUID owner, int size, Function<String, Def> defs, Map<String, Integer> owned) throws SQLException {
        for (var e : layouts(c, owner).entrySet()) {
            Layout pruned = HousingRules.prune(e.getValue(), size, defs, owned);
            if (pruned.equals(e.getValue())) continue;
            try (var s = c.prepareStatement("UPDATE " + tLayout + " SET data=? WHERE owner=? AND slot=?")) {
                s.setString(1, HousingRules.encode(pruned)); s.setString(2, owner.toString()); s.setInt(3, e.getKey()); s.executeUpdate();
            }
        }
    }
    private RoomRow room(Connection c, UUID owner) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM " + tRoom + " WHERE owner=?")) {
            s.setString(1, owner.toString()); try (var r = s.executeQuery()) { return r.next() ? row(r) : null; }
        }
    }
    private static RoomRow row(ResultSet r) throws SQLException {
        return new RoomRow(UUID.fromString(r.getString("owner")), r.getString("name"), Math.clamp(r.getInt("house"), -1, 3), skinOrEmpty(r.getString("skin")),
                r.getInt("slim") != 0, Math.clamp(r.getInt("size"), HousingRules.MIN_SIZE, HousingRules.MAX_SIZE),
                Math.clamp(r.getInt("active"), 0, HousingRules.SLOTS - 1), Math.max(0, r.getInt("likes")), Math.max(0, r.getInt("visits")), r.getLong("rev"));
    }
    private Map<Integer, Layout> layouts(Connection c, UUID owner) throws SQLException {
        var map = new HashMap<Integer, Layout>();
        try (var s = c.prepareStatement("SELECT slot,data FROM " + tLayout + " WHERE owner=?")) {
            s.setString(1, owner.toString());
            try (var r = s.executeQuery()) {
                while (r.next()) {
                    int slot = r.getInt(1); if (slot < 0 || slot >= HousingRules.SLOTS) continue;
                    try { map.put(slot, HousingRules.decode(r.getString(2))); }
                    catch (IllegalArgumentException bad) { log.warning("방 " + owner + " 레이아웃 " + slot + " 형식 오류: 빈 방으로 보여요."); }
                }
            }
        }
        return map;
    }
    private String nickname(Connection c, UUID id) {
        try (var s = c.prepareStatement("SELECT nickname FROM " + tNick + " WHERE owner=?")) {
            s.setString(1, id.toString()); try (var r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        } catch (SQLException missing) { return null; }
    }
    private int house(Connection c, UUID id) {
        try (var s = c.prepareStatement("SELECT house FROM " + tHouse + " WHERE player=?")) {
            s.setString(1, id.toString()); try (var r = s.executeQuery()) { return r.next() ? Math.clamp(r.getInt(1), -1, 3) : -1; }
        } catch (SQLException missing) { return -1; }
    }
    private static String cut(String name) { return name == null ? "" : name.length() > 32 ? name.substring(0, 32) : name; }
    static String skinOrEmpty(String s) { return s != null && s.matches("[0-9a-f]{32,64}") ? s : ""; }
}
