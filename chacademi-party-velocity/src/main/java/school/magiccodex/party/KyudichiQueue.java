package school.magiccodex.party;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import school.magiccodex.protocol.KyudichiQueueProtocol;
import school.magiccodex.protocol.KyudichiQueueProtocol.*;

/**
 * 큐디치 대기열 (오너 결정, docs claude/quidditch-plan.md):
 *  - 3인 파티는 파티장이 신청, 혼자는 랜덤 매칭. 대기 중에는 지금 있는 서버에 그대로 있는다.
 *  - 매칭이 잡히면 6명 모두 수락 확인(10초). 거절·시간 초과한 쪽은 대기열에서 빠지고, 나머지는 원래 대기 순서로 돌아간다.
 *  - 모두 수락하면 경기장 서버로 이동, 경기가 끝나면 원래 있던 서버로 돌아간다 (위치는 그 서버가 저장한 자리).
 *  - 경기 중 접속이 끊겼다가 다시 들어오면 경기장 서버로 바로 보낸다 (60초 재접속은 경기장 서버가 판정).
 * All state lives on the party plugin's single worker thread.
 */
final class KyudichiQueue {
    static final MinecraftChannelIdentifier QUEUE = MinecraftChannelIdentifier.from(KyudichiQueueProtocol.QUEUE);
    static final MinecraftChannelIdentifier LINK = MinecraftChannelIdentifier.from(KyudichiQueueProtocol.LINK);
    static final long OFFER_MS = 10_000, UNSEEN_MS = 120_000, LIVE_FRESH_MS = 10_000;

    interface Host {
        boolean enqueue(Runnable task);
        PartyBook.Party party(UUID member);
        String name(UUID id);
        void notice(Player p, String text);
        /** House 0..3 or -1, from the party plugin's identity memory. */
        int house(UUID id);
        /** Skin texture hash (textures.minecraft.net) last seen for this player, or "". */
        String skin(UUID id);
        boolean trusted(String server);
    }

    private final ProxyServer proxy; private final Logger log; private final Host host; private final Object plugin;
    private String arenaServer = "arena", fallbackServer = "school";
    private int arenaSlots = 3;
    private KyudichiDb db;

    private record Queued(KyudichiMatchmaker.Entry entry, UUID leader) {}
    private final Map<UUID, Queued> queue = new LinkedHashMap<>();       // entry id → entry
    private final Map<UUID, UUID> queuedBy = new HashMap<>();             // player → entry id
    private final Set<UUID> joining = new HashSet<>();                    // DB lookup in flight
    private static final class Offer {
        final UUID id = UUID.randomUUID(); final KyudichiMatchmaker.Pair pair; final long deadline; final Set<UUID> accepted = new HashSet<>();
        Offer(KyudichiMatchmaker.Pair pair, long deadline) { this.pair = pair; this.deadline = deadline; }
        List<UUID> everyone() { var l = new ArrayList<>(pair.a().members()); l.addAll(pair.b().members()); return l; }
        List<KyudichiMatchmaker.Entry> entries() { var l = new ArrayList<>(pair.a().parts()); l.addAll(pair.b().parts()); return l; }
    }
    private final Map<UUID, Offer> offerOf = new HashMap<>();
    /** lastSeen: assignment time, then every LIVE report that still lists the match. Unseen for 2 minutes = gone. */
    private static final class Assigned { final Assign assign; long lastSeen; Assigned(Assign a, long t) { assign = a; lastSeen = t; } }
    private final Map<UUID, Assigned> matches = new HashMap<>();          // match id → assignment
    private final Map<UUID, UUID> playing = new ConcurrentHashMap<>();    // player → match id (also read by initialServer)
    private final Map<UUID, String> origin = new HashMap<>();             // player → server to go back to
    private final Map<UUID, String> pendingSpectate = new HashMap<>();
    private final Set<UUID> pendingPractice = new HashSet<>();
    private final Set<UUID> modded = new HashSet<>();
    private List<LiveMatch> live = List.of(); private int liveFree = -1; private long liveAt;

    KyudichiQueue(ProxyServer proxy, Logger log, Host host, Object plugin) { this.proxy = proxy; this.log = log; this.host = host; this.plugin = plugin; }

    void initialize(Path directory) {
        int start = 1000; int[] floors = {900, 1100, 1300, 1500, 1700};
        try {
            Path file = directory.resolve("kyudichi.properties");
            var p = new Properties();
            if (Files.exists(file)) { try (var r = Files.newBufferedReader(file)) { p.load(r); } }
            else {
                p.setProperty("arena-server", "arena"); p.setProperty("return-fallback-server", "school"); p.setProperty("arena-slots", "3");
                p.setProperty("rating-start", "1000"); p.setProperty("tier-floors", "900,1100,1300,1500,1700");
                try (var w = Files.newBufferedWriter(file)) { p.store(w, "Kyudichi queue. arena-server = Velocity server name of the arena/PvP server. tier-floors must match ChacaKyudichi config.yml rating.tiers (silver..master). Restart after editing."); }
            }
            arenaServer = p.getProperty("arena-server", "arena").strip();
            fallbackServer = p.getProperty("return-fallback-server", "school").strip();
            arenaSlots = Math.clamp(Integer.parseInt(p.getProperty("arena-slots", "3").strip()), 1, 16);
            start = Integer.parseInt(p.getProperty("rating-start", "1000").strip());
            var f = p.getProperty("tier-floors", "900,1100,1300,1500,1700").split(",");
            if (f.length == 5) for (int i = 0; i < 5; i++) floors[i] = Integer.parseInt(f[i].strip());
        } catch (Exception e) { log.error("kyudichi.properties 를 읽지 못해 기본값을 씁니다", e); }
        db = new KyudichiDb(log, start, floors);
        db.open(directory.resolve("kyudichi-database.properties"));
        proxy.getChannelRegistrar().register(QUEUE, LINK);
        proxy.getEventManager().register(plugin, this);
        proxy.getScheduler().buildTask(plugin, () -> host.enqueue(this::tick)).repeat(1, TimeUnit.SECONDS).schedule();
        var manager = proxy.getCommandManager();
        manager.register(manager.metaBuilder("큐디치").aliases("kyudichi").plugin(plugin).build(), new Command());
        log.info("큐디치 대기열: 경기장 서버 '" + arenaServer + "', 경기장 " + arenaSlots + "개");
    }
    void shutdown() { if (db != null) db.close(); }

    // =============================================================== helpers
    private Player online(UUID id) { return proxy.getPlayer(id).orElse(null); }
    private String serverOf(Player p) { return p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(""); }
    private boolean onArena(Player p) { return serverOf(p).equals(arenaServer); }
    private void say(UUID id, String text) { var p = online(id); if (p != null) host.notice(p, text); }
    private void sayAll(List<UUID> ids, String text) { for (UUID id : ids) say(id, text); }
    private void toClient(Player p, ToClient m) { if (modded.contains(p.getUniqueId())) p.sendPluginMessage(QUEUE, KyudichiQueueProtocol.encode(m)); }
    private void toArena(Player via, ToArena m) {
        var c = via.getCurrentServer().orElse(null);
        if (c != null && c.getServerInfo().getName().equals(arenaServer)) c.sendPluginMessage(LINK, KyudichiQueueProtocol.encode(m));
    }
    private int parties() { return queue.size(); }

    /** Arenas we may still fill: configured slots minus matches we sent, and never more than the arena server last reported free. */
    private int freeArenas(long now) {
        int free = arenaSlots - matches.size() - offersOpen();
        if (liveFree >= 0 && now - liveAt < LIVE_FRESH_MS) {
            long notYetLive = matches.keySet().stream().filter(id -> live.stream().noneMatch(l -> l.match().equals(id))).count();
            free = Math.min(free, liveFree - (int) notYetLive - offersOpen());
        }
        return free;
    }
    private int offersOpen() { return (int) offerOf.values().stream().distinct().count(); }

    // =============================================================== join / leave
    void join(Player p) {
        UUID me = p.getUniqueId();
        if (queuedBy.containsKey(me) || joining.contains(me)) { host.notice(p, "이미 큐디치 대기열에 있어요. 나가려면 /큐디치 취소"); return; }
        if (offerOf.containsKey(me)) { host.notice(p, "매칭 수락 확인 중이에요."); return; }
        if (playing.containsKey(me)) { host.notice(p, "이미 큐디치 경기 중이에요."); return; }
        var party = host.party(me);
        List<UUID> members;
        if (party == null) members = List.of(me);
        else {
            if (!party.leader().equals(me)) { host.notice(p, "파티는 파티장만 큐디치를 신청할 수 있어요."); return; }
            if (party.members().size() == 1) members = List.of(me);
            else if (party.members().size() != KyudichiMatchmaker.TEAM_SIZE) { host.notice(p, "큐디치는 3인 파티 또는 혼자(랜덤 매칭)만 신청할 수 있어요."); return; }
            else members = party.members();
        }
        for (UUID m : members) {
            Player mp = online(m);
            if (mp == null) { host.notice(p, host.name(m) + " 님이 접속 중이 아니에요."); return; }
            String s = serverOf(mp);
            if (!host.trusted(s) && !s.equals(arenaServer)) { host.notice(p, host.name(m) + " 님이 있는 곳에서는 신청할 수 없어요."); return; }
            if (queuedBy.containsKey(m) || offerOf.containsKey(m) || playing.containsKey(m) || joining.contains(m)) { host.notice(p, host.name(m) + " 님은 이미 대기열이나 경기 중이에요."); return; }
            if (pendingPractice.contains(m)) { host.notice(p, host.name(m) + " 님이 연습장으로 가는 중이에요."); return; }
        }
        joining.addAll(members);
        db.standings(members, standings -> host.enqueue(() -> {
            joining.removeAll(members);
            long now = System.currentTimeMillis();
            for (UUID m : members) {
                var st = standings.get(m);
                if (st != null && st.bannedUntil() > now) {
                    long min = Math.max(1, (st.bannedUntil() - now + 59_999) / 60_000);
                    sayAll(members, host.name(m) + " 님은 큐디치 이용이 제한된 상태예요 (" + (min >= 60 ? min / 60 + "시간 " : "") + min % 60 + "분 남음).");
                    return;
                }
                if (online(m) == null || queuedBy.containsKey(m) || playing.containsKey(m)) { say(me, "신청하는 사이에 파티원 상태가 바뀌었어요. 다시 신청해 주세요."); return; }
            }
            int rating = members.stream().mapToInt(m -> standings.get(m).rating()).max().orElse(1000);
            var entry = new KyudichiMatchmaker.Entry(UUID.randomUUID(), List.copyOf(members), rating, now);
            queue.put(entry.id(), new Queued(entry, me));
            for (UUID m : members) queuedBy.put(m, entry.id());
            sayAll(members, members.size() == 1 ? "큐디치 랜덤 매칭을 시작했어요. 취소: /큐디치 취소" : "파티 큐디치 매칭을 시작했어요. 취소: /큐디치 취소");
            status(now);
        }));
    }

    void leave(Player p, String why) {
        UUID eid = queuedBy.get(p.getUniqueId());
        if (eid == null) { if (why == null) host.notice(p, "대기열에 있지 않아요."); return; }
        var q = queue.remove(eid);
        for (UUID m : q.entry().members()) { queuedBy.remove(m); var mp = online(m); if (mp != null) { toClient(mp, idle()); clearBar(mp); } }
        sayAll(q.entry().members(), why != null ? why : host.name(p.getUniqueId()) + " 님이 큐디치 매칭을 취소했어요.");
    }

    private static Status idle() { return new Status(KyudichiQueueProtocol.S_NONE, 0, 0, 0, 0, false, 0); }
    private void clearBar(Player p) { if (!modded.contains(p.getUniqueId())) p.sendActionBar(Component.empty()); }

    // =============================================================== offers
    private void offer(KyudichiMatchmaker.Pair pair, long now) {
        var o = new Offer(pair, now + OFFER_MS);
        for (var e : o.entries()) { queue.remove(e.id()); for (UUID m : e.members()) queuedBy.remove(m); }
        for (UUID m : o.everyone()) {
            offerOf.put(m, o);
            var p = online(m); if (p == null) continue;
            p.sendMessage(Component.text("[큐디치] ", NamedTextColor.GOLD).append(Component.text("경기를 찾았어요! 10초 안에 수락해 주세요  ", NamedTextColor.YELLOW))
                    .append(Component.text("[수락]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/큐디치 수락")).hoverEvent(HoverEvent.showText(Component.text("/큐디치 수락"))))
                    .append(Component.text("  "))
                    .append(Component.text("[거절]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/큐디치 거절")).hoverEvent(HoverEvent.showText(Component.text("/큐디치 거절")))));
        }
        status(now);
    }

    void accept(Player p) {
        var o = offerOf.get(p.getUniqueId());
        if (o == null) { host.notice(p, "수락할 매칭이 없어요."); return; }
        o.accepted.add(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (o.accepted.containsAll(o.everyone())) transfer(o, now); else status(now);
    }

    void decline(Player p) {
        var o = offerOf.get(p.getUniqueId());
        if (o == null) { host.notice(p, "거절할 매칭이 없어요."); return; }
        failOffer(o, Set.of(p.getUniqueId()), host.name(p.getUniqueId()) + " 님이 매칭을 거절했어요.");
    }

    /** Entries with anyone in `out` leave the queue; the rest go back with their original waiting time. */
    private void failOffer(Offer o, Set<UUID> out, String why) {
        for (UUID m : o.everyone()) offerOf.remove(m);
        for (var e : o.entries()) {
            boolean drop = e.members().stream().anyMatch(out::contains);
            if (drop) {
                for (UUID m : e.members()) { var mp = online(m); if (mp != null) { toClient(mp, idle()); clearBar(mp); } }
                sayAll(e.members(), why + " 대기열에서 나왔어요.");
            } else {
                boolean allOnline = e.members().stream().allMatch(m -> online(m) != null);
                if (!allOnline) { sayAll(e.members(), why + " 파티원이 접속 중이 아니라 대기열에서 나왔어요."); continue; }
                UUID leader = e.members().getFirst();
                queue.put(e.id(), new Queued(e, leader));
                for (UUID m : e.members()) queuedBy.put(m, e.id());
                sayAll(e.members(), why + " 대기열 앞자리로 돌아갔어요.");
            }
        }
        status(System.currentTimeMillis());
    }

    private void transfer(Offer o, long now) {
        for (UUID m : o.everyone()) offerOf.remove(m);
        var assign = new Assign(UUID.randomUUID(), true, o.pair.a().members(), o.pair.b().members());
        matches.put(assign.match(), new Assigned(assign, now));
        RegisteredServer arena = proxy.getServer(arenaServer).orElse(null);
        for (UUID m : o.everyone()) {
            playing.put(m, assign.match());
            pendingSpectate.remove(m); pendingPractice.remove(m);
            Player p = online(m); if (p == null) continue;
            toClient(p, new Status(KyudichiQueueProtocol.S_MOVING, 0, 0, 6, 6, true, parties()));
            clearBar(p);
            if (onArena(p)) { toArena(p, assign); continue; }
            origin.put(m, serverOf(p));
            if (arena == null) { host.notice(p, "경기장 서버(" + arenaServer + ")를 찾을 수 없어요. 관리자에게 알려 주세요."); continue; }
            host.notice(p, "큐디치 경기장으로 이동해요.");
            p.createConnectionRequest(arena).connect().whenComplete((r, err) -> host.enqueue(() -> {
                if (err == null && r.isSuccessful()) return;
                log.warn("큐디치 경기장 이동 실패: " + p.getUsername() + (err != null ? " " + err : " " + r.getStatus()));
                host.notice(p, "경기장으로 이동하지 못했어요. 다시 접속하면 경기장으로 들어가요.");
            }));
        }
    }

    // =============================================================== tick
    private void tick() {
        long now = System.currentTimeMillis();
        // entries whose people left or whose party changed
        for (var q : List.copyOf(queue.values())) {
            var e = q.entry();
            String gone = null;
            for (UUID m : e.members()) if (online(m) == null) { gone = host.name(m) + " 님이 접속을 끊어서"; break; }
            if (gone == null && e.members().size() > 1) {
                var party = host.party(q.leader());
                if (party == null || !new HashSet<>(party.members()).equals(new HashSet<>(e.members()))) gone = "파티 구성이 바뀌어서";
            }
            if (gone != null) {
                queue.remove(e.id());
                for (UUID m : e.members()) { queuedBy.remove(m); var mp = online(m); if (mp != null) toClient(mp, idle()); }
                sayAll(e.members(), gone + " 큐디치 매칭이 취소됐어요.");
            }
        }
        for (Offer o : offerOf.values().stream().distinct().toList()) {
            if (now < o.deadline) continue;
            var out = new HashSet<UUID>(); for (UUID m : o.everyone()) if (!o.accepted.contains(m)) out.add(m);
            failOffer(o, out, "수락하지 않은 사람이 있어서");
        }
        matches.values().removeIf(a -> now - a.lastSeen > UNSEEN_MS);
        playing.values().removeIf(id -> !matches.containsKey(id));
        int free = freeArenas(now);
        while (free > 0) {
            var pair = KyudichiMatchmaker.find(queue.values().stream().map(Queued::entry).toList(), now);
            if (pair == null) break;
            offer(pair, now); free--;
        }
        status(now);
    }

    private void status(long now) {
        int n = parties();
        for (var q : queue.values()) {
            int waited = (int) Math.min(65535, (now - q.entry().since()) / 1000);
            for (UUID m : q.entry().members()) {
                var p = online(m); if (p == null) continue;
                if (modded.contains(m)) toClient(p, new Status(KyudichiQueueProtocol.S_SEARCHING, waited, 0, 0, 0, false, n));
                else p.sendActionBar(Component.text("큐디치 매칭 중 · " + waited / 60 + ":" + String.format("%02d", waited % 60) + " · 대기 파티 " + n, NamedTextColor.GOLD));
            }
        }
        for (Offer o : offerOf.values().stream().distinct().toList()) {
            int left = (int) Math.max(0, (o.deadline - now + 999) / 1000);
            for (UUID m : o.everyone()) {
                var p = online(m); if (p == null) continue;
                if (modded.contains(m)) toClient(p, new Status(KyudichiQueueProtocol.S_OFFER, 0, left, o.accepted.size(), o.everyone().size(), o.accepted.contains(m), n));
                else p.sendActionBar(Component.text("큐디치 경기 수락 " + o.accepted.size() + "/" + o.everyone().size() + " · " + left + "초 · /큐디치 수락", NamedTextColor.YELLOW));
            }
        }
    }

    // =============================================================== spectate / practice
    void spectate(Player p, String arena) {
        UUID me = p.getUniqueId();
        if (playing.containsKey(me) || offerOf.containsKey(me)) { host.notice(p, "경기 중에는 관전할 수 없어요."); return; }
        if (onArena(p)) { toArena(p, new Spectate(me, arena)); return; }
        var target = proxy.getServer(arenaServer).orElse(null);
        if (target == null) { host.notice(p, "경기장 서버를 찾을 수 없어요."); return; }
        pendingSpectate.put(me, arena); origin.put(me, serverOf(p));
        host.notice(p, "관전하러 경기장으로 이동해요.");
        p.createConnectionRequest(target).connect().whenComplete((r, err) -> host.enqueue(() -> {
            if (err == null && r.isSuccessful()) return;
            pendingSpectate.remove(me); origin.remove(me); host.notice(p, "경기장으로 이동하지 못했어요.");
        }));
    }

    void practice(Player p) {
        UUID me = p.getUniqueId();
        if (playing.containsKey(me) || offerOf.containsKey(me)) { host.notice(p, "경기 중이에요."); return; }
        if (queuedBy.containsKey(me)) { host.notice(p, "대기열에 있는 동안에는 연습장에 갈 수 없어요."); return; }
        if (onArena(p)) { toArena(p, new Practice(me)); return; }
        var target = proxy.getServer(arenaServer).orElse(null);
        if (target == null) { host.notice(p, "경기장 서버를 찾을 수 없어요."); return; }
        pendingPractice.add(me); origin.put(me, serverOf(p));
        host.notice(p, "연습장으로 이동해요.");
        p.createConnectionRequest(target).connect().whenComplete((r, err) -> host.enqueue(() -> {
            if (err == null && r.isSuccessful()) return;
            pendingPractice.remove(me); origin.remove(me); host.notice(p, "경기장으로 이동하지 못했어요.");
        }));
    }

    private void sendHome(UUID id) {
        playing.remove(id); pendingSpectate.remove(id); pendingPractice.remove(id);
        String to = origin.remove(id);
        Player p = online(id); if (p == null || !onArena(p)) return;
        var server = proxy.getServer(to != null && !to.equals(arenaServer) ? to : fallbackServer).orElse(null);
        if (server == null) { host.notice(p, "돌아갈 서버를 찾을 수 없어요."); return; }
        p.createConnectionRequest(server).connect().whenComplete((r, err) -> host.enqueue(() -> {
            if (err == null && r.isSuccessful()) { toClient(p, idle()); return; }
            host.notice(p, "원래 서버로 돌아가지 못했어요. /서버 로 이동해 주세요.");
        }));
    }

    // =============================================================== dashboard
    private void dash(Player p) {
        long now = System.currentTimeMillis();
        var list = new ArrayList<LiveCard>();
        if (now - liveAt < LIVE_FRESH_MS) for (var lm : live) {
            var l = lm.live();
            list.add(new LiveCard(l.arena(), l.phase(), l.scoreA(), l.scoreB(), l.timeLeftMs(), l.spectators(), whos(lm.teamA()), whos(lm.teamB())));
        }
        toClient(p, new Dash(parties(), list));
    }
    private List<Who> whos(List<UUID> ids) {
        var out = new ArrayList<Who>();
        for (UUID id : ids) if (out.size() < 6) out.add(new Who(id, host.name(id), host.skin(id)));
        return out;
    }
    private Rank skinned(Rank r) { return r == null ? null : new Rank(r.rank(), r.id(), r.nick(), host.skin(r.id()), r.rating(), r.tier(), r.games(), r.wins(), r.losses()); }

    /** 신청 창: my party (or me alone) with houses and ratings; only the leader (or a solo player) can start. */
    private void apply(Player p) {
        UUID me = p.getUniqueId();
        var party = host.party(me);
        List<UUID> members = party == null || party.members().size() == 1 ? List.of(me) : party.members();
        UUID leader = party == null ? me : party.leader();
        db.standings(members, st -> host.enqueue(() -> {
            if (online(me) != p) return;
            var list = new ArrayList<Member>();
            int base = 0;
            for (UUID id : members) {
                if (list.size() >= 3) break;
                var s = st.get(id); int rating = s == null ? 1000 : s.rating();
                base = Math.max(base, rating);
                list.add(new Member(id, host.name(id), host.skin(id), host.house(id), rating, members.size() > 1 && id.equals(leader)));
            }
            var mine = st.get(me);
            boolean solo = members.size() == 1, three = members.size() == KyudichiMatchmaker.TEAM_SIZE;
            boolean can = (solo || three && leader.equals(me)) && !playing.containsKey(me);
            String note = solo ? "혼자 신청하면 랜덤 팀으로 매칭돼요." : !three ? "큐디치는 3인 파티 또는 혼자만 신청할 수 있어요." : "파티장만 시작할 수 있습니다.";
            toClient(p, new Apply(mine == null ? 1000 : mine.rating(), mine == null ? 0 : mine.wins(), mine == null ? 0 : mine.losses(), base, can, note, list));
        }));
    }
    private void ranking(Player p) {
        db.ranking(p.getUniqueId(), res -> host.enqueue(() -> {
            @SuppressWarnings("unchecked") var top = (List<Rank>) res[0];
            toClient(p, new Ranking(top.stream().map(this::skinned).toList(), skinned((Rank) res[1])));
            if (!modded.contains(p.getUniqueId())) {
                if (top.isEmpty()) { host.notice(p, db.available() ? "아직 랭킹이 없어요." : "랭킹을 불러올 수 없어요."); return; }
                host.notice(p, "── 큐디치 랭킹 ──");
                for (var r : top.subList(0, Math.min(10, top.size()))) host.notice(p, r.rank() + ". " + r.nick() + " · " + TIERS[r.tier()] + " " + r.rating() + " · " + r.wins() + "승 " + r.losses() + "패");
                if (res[1] instanceof Rank mine) host.notice(p, "내 순위 " + mine.rank() + "위 · " + TIERS[mine.tier()] + " " + mine.rating());
            }
        }));
    }
    static final String[] TIERS = {"브론즈", "실버", "골드", "플래티넘", "다이아몬드", "마스터", "챌린저 1", "챌린저 2", "챌린저 3"};
    private void record(Player p, String nick) {
        UUID id = nick == null || nick.isBlank() ? p.getUniqueId() : null;
        db.record(id, nick, rec -> host.enqueue(() -> {
            toClient(p, new RecordReply(rec == null ? null : new PlayerRecord(rec.id(), rec.nick(), host.skin(rec.id()), rec.rating(), rec.tier(), rec.rank(), rec.games(),
                    rec.wins(), rec.losses(), rec.draws(), rec.goals(), rec.kills(), rec.deaths(), rec.assists(), rec.snitch(), rec.deflectHits(), rec.mvp(), rec.damage(), rec.heal(), rec.recent())));
            if (!modded.contains(p.getUniqueId())) {
                if (rec == null) { host.notice(p, "기록이 없어요."); return; }
                host.notice(p, "── " + rec.nick() + " · " + TIERS[rec.tier()] + " " + rec.rating() + (rec.rank() > 0 ? " · " + rec.rank() + "위" : "") + " ──");
                host.notice(p, rec.games() + "경기 " + rec.wins() + "승 " + rec.losses() + "패 " + rec.draws() + "무 · 골 " + rec.goals() + " · 처치 " + rec.kills() + " · 데스 " + rec.deaths()
                        + " · 딜 " + Math.round(rec.damage()) + " · 힐 " + Math.round(rec.heal()) + " · 스니치 " + rec.snitch() + " · MVP " + rec.mvp());
            }
        }));
    }

    // =============================================================== events
    @Subscribe public void queueMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(QUEUE)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof Player p)) return; // only the client talks on this channel
        FromClient req;
        try { req = KyudichiQueueProtocol.decodeFromClient(event.getData().clone()); } catch (IllegalArgumentException e) { return; }
        host.enqueue(() -> {
            if (online(p.getUniqueId()) != p) return;
            switch (req) {
                case ClientHello h -> { modded.add(p.getUniqueId()); status(System.currentTimeMillis()); }
                case Join j -> join(p);
                case Leave l -> leave(p, null);
                case Accept a -> accept(p);
                case Decline d -> decline(p);
                case DashRequest d -> dash(p);
                case RankingRequest r -> ranking(p);
                case RecordRequest r -> record(p, r.nick());
                case SpectateRequest s -> spectate(p, s.arena());
                case PracticeRequest x -> practice(p);
                case ApplyRequest x -> apply(p);
            }
        });
    }

    @Subscribe public void linkMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(LINK)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled()); // never forwarded either way
        if (!(event.getSource() instanceof ServerConnection source) || !source.getServerInfo().getName().equals(arenaServer)) return;
        ToProxy msg;
        try { msg = KyudichiQueueProtocol.decodeToProxy(event.getData().clone()); } catch (IllegalArgumentException e) { return; }
        Player via = source.getPlayer();
        host.enqueue(() -> {
            switch (msg) {
                case Hello h -> {
                    if (!h.player().equals(via.getUniqueId())) return;
                    UUID me = via.getUniqueId();
                    UUID match = playing.get(me);
                    if (match != null && matches.containsKey(match)) toArena(via, matches.get(match).assign);
                    else if (pendingSpectate.containsKey(me)) toArena(via, new Spectate(me, pendingSpectate.remove(me)));
                    else if (pendingPractice.remove(me)) toArena(via, new Practice(me));
                }
                case LiveList l -> {
                    live = l.matches(); liveFree = l.freeArenas(); liveAt = System.currentTimeMillis();
                    for (var lm : live) { var a = matches.get(lm.match()); if (a != null) a.lastSeen = liveAt; }
                }
                case Return r -> { for (UUID id : r.players()) sendHome(id); }
                case Ended e -> {
                    var a = matches.remove(e.match());
                    if (a != null) { var all = new ArrayList<>(a.assign.teamA()); all.addAll(a.assign.teamB()); for (UUID id : all) playing.remove(id, e.match()); }
                }
            }
        });
    }

    @Subscribe public void initialServer(PlayerChooseInitialServerEvent event) {
        // Concurrent read from the event thread; a stale read just means "lobby as usual".
        if (playing.containsKey(event.getPlayer().getUniqueId())) proxy.getServer(arenaServer).ifPresent(event::setInitialServer);
    }

    @Subscribe public void disconnect(DisconnectEvent event) {
        Player p = event.getPlayer(); UUID me = p.getUniqueId();
        host.enqueue(() -> {
            modded.remove(me);
            if (queuedBy.containsKey(me)) leave(p, host.name(me) + " 님이 접속을 끊어서 큐디치 매칭이 취소됐어요.");
            var o = offerOf.get(me);
            if (o != null) failOffer(o, Set.of(me), host.name(me) + " 님이 접속을 끊었어요.");
            pendingSpectate.remove(me); pendingPractice.remove(me);
            if (!playing.containsKey(me)) origin.remove(me);
        });
    }

    // =============================================================== command
    /**
     * /큐디치 on the proxy handles the queue, spectating, ranking and records from any server.
     * 포기 · 준비 · 관전 나가기 (and everything else) return false from hasPermission, so Velocity passes them to the arena server.
     */
    private final class Command implements SimpleCommand {
        private static final Set<String> MINE = Set.of("신청", "취소", "수락", "거절", "대기열", "관전", "랭킹", "정보", "연습", "도움말", "현황");
        @Override public boolean hasPermission(Invocation i) {
            if (!(i.source() instanceof Player p)) return false;
            String[] a = i.arguments();
            if (a.length == 0) return true;
            if (!MINE.contains(a[0])) return false;
            if (a[0].equals("관전") && a.length > 1 && a[1].equals("나가기")) return false;
            if (a[0].equals("연습") && onArena(p)) return false; // arena server runs its own practice start
            return true;
        }
        @Override public void execute(Invocation i) {
            if (!(i.source() instanceof Player p)) return;
            String[] a = i.arguments().clone();
            if (!host.enqueue(() -> {
                if (online(p.getUniqueId()) != p) return;
                // No arguments: the MagicCodex client opens the dashboard (an unrequested DASH); others get the command list.
                if (a.length == 0 && modded.contains(p.getUniqueId())) { apply(p); return; }
                if (a.length == 1 && a[0].equals("현황") && modded.contains(p.getUniqueId())) { dash(p); return; }
                String sub = a.length == 0 ? "도움말" : a[0];
                switch (sub) {
                    case "신청" -> join(p);
                    case "취소" -> leave(p, null);
                    case "수락" -> accept(p);
                    case "거절" -> decline(p);
                    case "현황" -> host.notice(p, "진행 중인 경기 " + (System.currentTimeMillis() - liveAt < LIVE_FRESH_MS ? live.size() : 0) + "개 · 대기 중인 파티 " + parties() + "개");
                    case "대기열" -> host.notice(p, "대기 중인 파티 " + parties() + "개" + (queuedBy.containsKey(p.getUniqueId()) ? " · 나는 매칭 중" : ""));
                    case "관전" -> spectate(p, a.length > 1 ? a[1] : "");
                    case "랭킹" -> ranking(p);
                    case "정보" -> record(p, a.length > 1 ? a[1] : null);
                    case "연습" -> practice(p);
                    default -> host.notice(p, "/큐디치 (신청 창) · 신청 · 취소 · 수락 · 거절 · 대기열 · 현황 · 관전 [경기장] · 랭킹 · 정보 [닉] · 연습 · (경기 중) 포기 · 준비");
                }
            })) host.notice(p, "요청이 많아요. 잠시 후 다시 시도해 주세요.");
        }
        @Override public List<String> suggest(Invocation i) {
            String[] a = i.arguments();
            if (a.length <= 1) { String pre = a.length == 0 ? "" : a[0]; return List.of("신청", "취소", "수락", "거절", "대기열", "관전", "랭킹", "정보", "연습", "포기", "준비").stream().filter(s -> s.startsWith(pre)).toList(); }
            return List.of();
        }
    }
}
