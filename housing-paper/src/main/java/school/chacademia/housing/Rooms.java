package school.chacademia.housing;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.HousingProtocol;
import school.magiccodex.protocol.HousingProtocol.*;
import school.magiccodex.protocol.HousingRules.Layout;
import school.magiccodex.protocol.SchoolProtocol;

/**
 * 누가 어느 방을 보고 있는지 (이 서버), 요청 처리, 다른 서버와 구경 중 명단·방 변경 맞추기.
 * 방이 바뀌면(저장·좋아요·방명록·방문·확장) DB 의 rev 가 올라가고, 그 방을 보고 있는 모든 서버가 다음 동기화 때 다시 보낸다.
 */
final class Rooms {
    /** home = 이 창을 기숙사실 문(또는 /테스트 하우징 내방)으로 열었음 → 내 방은 편집, 남의 방에서 내 방으로 돌아갈 수 있음. */
    record Session(UUID owner, boolean edit, boolean home, long token) {}

    private final HousingPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Integer> catalogSent = new HashMap<>();
    private final Map<UUID, Long> knownRev = new HashMap<>();
    private final Map<UUID, List<Viewer>> lastViewers = new HashMap<>();
    private final Map<UUID, Person> people = new HashMap<>();
    /** 방 크기 캐시 (접속·방 읽기 때 채움, /유저관리 보기 요약용). */
    private final Map<UUID, Integer> sizes = new HashMap<>();
    private final Map<UUID, Long> lastAction = new HashMap<>(), lastPost = new HashMap<>();
    private long tokens;
    private boolean syncing;

    Rooms(HousingPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ 접속
    void join(Player p) {
        var skin = Skins.of(p);
        var catalog = plugin.catalog();
        plugin.store().touch(p.getUniqueId(), p.getName(), fallbackHouse(p), skin.hash(), skin.slim(), plugin.settings().startSize(), catalog::get, catalog.allowed(p), row -> {
            if (row != null) { people.put(p.getUniqueId(), row.person()); sizes.put(p.getUniqueId(), row.size()); }
        });
        plugin.store().heartbeat(plugin.settings().server(), List.of(p.getUniqueId()));
    }
    void quit(Player p) {
        UUID id = p.getUniqueId();
        sessions.remove(id); catalogSent.remove(id); people.remove(id); sizes.remove(id); lastAction.remove(id); lastPost.remove(id);
        plugin.store().offline(id, plugin.settings().server());
    }
    /** 기숙사 문 검사용 (아직 못 읽었으면 -2, 기숙사 없음 -1). */
    int house(UUID id) { var p = people.get(id); return p == null ? -2 : p.house(); }
    /** 캐시된 방 크기 (모르면 null). */
    Integer size(UUID id) { return sizes.get(id); }
    void size(UUID id, int size) { if (Bukkit.getPlayer(id) != null) sizes.put(id, size); }

    static final List<String> HOUSE_PERMISSIONS = List.of("arkeon", "lumina", "bestiaz", "noxer");
    /**
     * DB(codex_houses)에 기숙사가 없을 때 MagicCodexBridge 와 같은 방법으로 찾는다:
     * Bridge StatsService 의 dormitory → magiccodex.house.&lt;영문&gt; 권한. 없으면 -1.
     */
    static int fallbackHouse(Player p) {
        try {
            for (Class<?> type : Bukkit.getServicesManager().getKnownServices()) {
                if (!type.getName().equals("school.magiccodex.paper.StatsService")) continue;
                @SuppressWarnings({"rawtypes", "unchecked"}) var reg = Bukkit.getServicesManager().getRegistration((Class) type);
                if (reg == null) break;
                Object extra = type.getMethod("get", UUID.class).invoke(reg.getProvider(), p.getUniqueId());
                Object dorm = extra.getClass().getMethod("dormitory").invoke(extra);
                int i = dorm == null ? -1 : SchoolProtocol.HOUSES.indexOf(dorm.toString());
                if (i >= 0) return i;
                break;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { /* Bridge 없음 */ }
        for (int i = 0; i < HOUSE_PERMISSIONS.size(); i++) if (p.hasPermission("magiccodex.house." + HOUSE_PERMISSIONS.get(i))) return i;
        return -1;
    }
    Person person(Player p) {
        var known = people.get(p.getUniqueId());
        if (known != null) return known;
        var skin = Skins.of(p);
        return new Person(p.getUniqueId(), p.getName(), -1, skin.hash(), skin.slim());
    }
    void catalogChanged() { catalogSent.clear(); }

    // ------------------------------------------------------------ 열기·닫기
    /** @param home 기숙사실 문·/테스트 하우징 내방 으로 열었는지 (또는 그렇게 연 창 안에서 이동했는지) */
    void open(Player p, UUID owner, boolean home) {
        if (!plugin.net().hasMod(p)) { ChacaLink.notify(p, "warning", "housing-nomod", "방 꾸미기를 열 수 없어요", "차카데미 클라이언트에서만 열 수 있어요", 2000); return; }
        UUID id = p.getUniqueId();
        var catalog = plugin.catalog();
        if (!Objects.equals(catalogSent.get(id), catalog.revision())) { plugin.net().send(p, catalog.message()); catalogSent.put(id, catalog.revision()); }
        // 기숙사를 아직 몰랐는데 지금은 알 수 있으면 (Bridge 가 늦게 채운 경우) 방을 읽기 전에 먼저 고쳐 둔다 (같은 DB 작업 줄이라 순서 보장).
        if (owner.equals(id) && house(id) < 0 && fallbackHouse(p) >= 0) join(p);
        var session = new Session(owner, home && owner.equals(id), home, ++tokens);
        var previous = sessions.put(id, session);
        load(p, session, true, true, previous);
    }
    void close(Player p) {
        if (sessions.remove(p.getUniqueId()) != null) plugin.store().leave(p.getUniqueId());
    }
    Session session(UUID viewer) { return sessions.get(viewer); }

    /** 방을 읽어 보낸다. 창이 그 사이에 닫혔거나 다른 방으로 옮겼으면 버린다. */
    private void load(Player p, Session s, boolean open, boolean guestbook) { load(p, s, open, guestbook, null); }
    /** @param previous 방을 못 찾았을 때 되돌릴 이전 창 (친구 방으로 가다가 실패하면 보던 방에 그대로 남는다) */
    private void load(Player p, Session s, boolean open, boolean guestbook, Session previous) {
        UUID id = p.getUniqueId();
        plugin.store().load(s.owner(), id, open && !s.owner().equals(id), data -> {
            if (!p.isOnline() || sessions.get(id) != s) return;
            if (data == null) {
                if (previous != null) sessions.put(id, previous); else sessions.remove(id);
                plugin.net().result(p, false, "방을 찾을 수 없어요. 아직 서버에 들어온 적이 없는 학생일 수 있어요.");
                return;
            }
            knownRev.put(s.owner(), data.row().rev());
            size(s.owner(), data.row().size());
            plugin.net().send(p, new RoomMessage(room(data, s, open, p)));
            // 스토리 "내방열기": 내 방을 DB 에서 읽어 창을 연 뒤에만 (남의 방 구경·갱신 재전송 제외)
            if (open && s.owner().equals(id)) StoryProgressEvent.fire(p, StoryProgressEvent.OWN_ROOM);
            if (guestbook) sendGuestbook(p, s);
            var viewers = lastViewers.get(s.owner());
            if (viewers != null) sendViewers(p, s.owner(), viewers);
        });
    }
    private void sendGuestbook(Player p, Session s) {
        plugin.store().guestbook(s.owner(), HousingProtocol.MAX_GUESTBOOK, entries -> {
            if (entries != null && p.isOnline() && sessions.get(p.getUniqueId()) == s) plugin.net().send(p, new Guestbook(s.owner(), entries));
        });
    }
    private Room room(HousingStore.RoomData d, Session s, boolean open, Player viewer) {
        var row = d.row();
        int flags = (open ? HousingProtocol.F_OPEN : 0) | (s.edit() ? HousingProtocol.F_EDIT : 0) | (d.liked() ? HousingProtocol.F_LIKED : 0) | (s.home() ? HousingProtocol.F_HOME : 0);
        var next = plugin.settings().next(row.size());
        Map<Integer, Layout> layouts = new HashMap<>();
        if (s.edit()) layouts.putAll(d.layouts());
        else layouts.put(row.active(), d.layouts().getOrDefault(row.active(), Layout.EMPTY));
        Map<String, Integer> owned = s.edit() ? limit(plugin.catalog().allowed(viewer)) : Map.of();
        return new Room(flags, row.person(), row.size(), next == null ? 0 : next.getKey(), next == null ? 0 : next.getValue(),
                row.likes(), d.today(), row.visits(), row.active(), layouts, owned);
    }
    private static Map<String, Integer> limit(Map<String, Integer> owned) {
        if (owned.size() <= HousingProtocol.MAX_OWNED) return owned;
        var map = new TreeMap<String, Integer>();
        for (var e : new TreeMap<>(owned).entrySet()) { if (map.size() >= HousingProtocol.MAX_OWNED) break; map.put(e.getKey(), e.getValue()); }
        return map;
    }

    /** 이 서버에서 owner 방을 보고 있는 모든 사람에게 다시 보낸다 (방명록 포함). */
    void refresh(UUID owner) {
        for (var e : new ArrayList<>(sessions.entrySet())) {
            if (!e.getValue().owner().equals(owner)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) load(p, e.getValue(), false, true);
        }
    }

    // ------------------------------------------------------------ 요청
    void handle(Player p, Request r) {
        UUID id = p.getUniqueId();
        Session s = sessions.get(id);
        if (r instanceof Close) { close(p); return; }
        if (s == null) return; // 창이 열려 있지 않으면 무시 (창은 서버가 연다)
        long now = System.currentTimeMillis();
        if (now - lastAction.getOrDefault(id, 0L) < 300) { plugin.net().result(p, false, "잠시 후 다시 눌러 주세요."); return; }
        lastAction.put(id, now);
        switch (r) {
            case Visit v -> {
                if (v.owner().equals(id) && s.home()) open(p, id, true);
                else open(p, v.owner(), s.home());
            }
            case Save save -> save(p, s, save);
            case Expand e -> expand(p, s, e.currentSize());
            case Like like -> {
                if (!like.owner().equals(s.owner())) return;
                if (like.owner().equals(id)) { plugin.net().result(p, false, "내 방에는 좋아요를 누를 수 없어요."); return; }
                plugin.store().like(s.owner(), id, liked -> {
                    if (liked == null) { plugin.net().result(p, false, "좋아요를 누르지 못했어요."); return; }
                    refresh(s.owner());
                });
            }
            case Post post -> post(p, s, post);
            case Delete d -> {
                if (!d.owner().equals(s.owner())) return;
                plugin.store().deleteEntry(s.owner(), d.entry(), id, p.isOp(), why -> {
                    plugin.net().result(p, why == null, why == null ? "방명록 글을 지웠어요." : why);
                    if (why == null) refresh(s.owner());
                });
            }
            case Friends f -> plugin.store().friends(id, HousingProtocol.MAX_FRIENDS, list -> {
                if (p.isOnline()) plugin.net().send(p, new FriendList(list == null ? List.of() : list));
            });
            case Close c -> close(p);
        }
    }

    private void save(Player p, Session s, Save save) {
        if (!s.edit()) { plugin.net().result(p, false, "내 방에서만 꾸밀 수 있어요."); return; }
        plugin.store().save(s.owner(), save.slot(), save.layout(), plugin.catalog()::get, plugin.catalog().allowed(p), why -> {
            plugin.net().result(p, why == null, why == null ? "저장했어요. 방문한 친구에게는 레이아웃 " + (save.slot() + 1) + "이(가) 보여요." : why);
            if (why == null) refresh(s.owner());
        });
    }

    private void expand(Player p, Session s, int from) {
        if (!s.edit()) { plugin.net().result(p, false, "내 방에서만 넓힐 수 있어요."); return; }
        var next = plugin.settings().next(from);
        if (next == null) { plugin.net().result(p, false, "더 넓힐 수 없어요."); return; }
        long cost = next.getValue();
        if (cost > 0 && !Economy.available()) { plugin.net().result(p, false, "경제 서비스 연결이 필요해요. 관리자에게 알려 주세요."); return; }
        if (!Economy.withdraw(p, cost)) { plugin.net().result(p, false, "돈이 부족해요. (필요: " + String.format("%,d", cost) + ")"); return; }
        UUID owner = s.owner();
        plugin.store().expand(owner, from, next.getKey(), ok -> {
            if (Boolean.TRUE.equals(ok)) {
                size(owner, next.getKey());
                plugin.net().result(p, true, "방이 " + next.getKey() + "×" + next.getKey() + " 크기로 넓어졌어요!");
                refresh(owner);
                return;
            }
            boolean refunded = Economy.deposit(p, cost);
            if (!refunded) plugin.getLogger().severe("방 확장 환불 실패: " + p.getName() + " " + cost + " (직접 돌려주세요)");
            plugin.net().result(p, false, ok == null ? "넓히지 못했어요. 돈은 돌려드렸어요." : "방 크기가 이미 바뀌었어요. 돈은 돌려드렸어요.");
            refresh(owner);
        });
    }

    private void post(Player p, Session s, Post post) {
        if (!post.owner().equals(s.owner())) return;
        String text = HousingProtocol.cleanText(post.text());
        if (text.isEmpty()) { plugin.net().result(p, false, "한마디를 적어 주세요."); return; }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis(), wait = plugin.settings().guestbookCooldownMs() - (now - lastPost.getOrDefault(id, 0L));
        if (wait > 0) { plugin.net().result(p, false, "방명록은 " + ((wait + 999) / 1000) + "초 뒤에 다시 쓸 수 있어요."); return; }
        lastPost.put(id, now);
        plugin.store().post(s.owner(), person(p), text, plugin.settings().guestbookKeep(), ok -> {
            plugin.net().result(p, ok != null, ok != null ? "방명록을 남겼어요." : "방명록을 남기지 못했어요.");
            if (ok != null) refresh(s.owner());
        });
    }

    // ------------------------------------------------------------ 다른 서버와 맞추기
    void sync() {
        if (syncing) return;
        var local = new ArrayList<HousingStore.Presence>();
        var owners = new HashSet<UUID>();
        for (var e : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            local.add(new HousingStore.Presence(e.getKey(), e.getValue().owner(), person(p).name()));
            owners.add(e.getValue().owner());
        }
        lastViewers.keySet().retainAll(owners); knownRev.keySet().retainAll(owners);
        syncing = true;
        plugin.store().sync(plugin.settings().server(), local, owners, result -> {
            syncing = false;
            if (result == null) return;
            for (UUID owner : owners) {
                var viewers = result.viewers().getOrDefault(owner, List.of());
                if (!viewers.equals(lastViewers.get(owner))) {
                    lastViewers.put(owner, viewers);
                    for (var e : sessions.entrySet()) {
                        if (!e.getValue().owner().equals(owner)) continue;
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null) sendViewers(p, owner, viewers);
                    }
                }
                Long rev = result.revs().get(owner);
                if (rev != null && !rev.equals(knownRev.get(owner))) { knownRev.put(owner, rev); refresh(owner); }
            }
        });
    }
    private void sendViewers(Player p, UUID owner, List<Viewer> all) {
        var shown = all.size() > HousingProtocol.MAX_VIEWERS_SHOWN ? all.subList(0, HousingProtocol.MAX_VIEWERS_SHOWN) : all;
        plugin.net().send(p, new Viewers(owner, all.size(), shown));
    }
    void heartbeat() {
        var ids = new ArrayList<UUID>();
        for (var p : Bukkit.getOnlinePlayers()) ids.add(p.getUniqueId());
        plugin.store().heartbeat(plugin.settings().server(), ids);
    }
    void shutdown() {
        for (var id : new ArrayList<>(sessions.keySet())) plugin.store().leave(id);
        sessions.clear();
    }
    int open() { return sessions.size(); }
}
