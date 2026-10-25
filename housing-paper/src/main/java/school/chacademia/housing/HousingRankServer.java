package school.chacademia.housing;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.HousingRankProtocol;
import school.magiccodex.protocol.HousingRankProtocol.*;

/**
 * 하우징 랭킹 채널 (#51). 창은 /하우징랭킹 이 연다 (OPEN). 요청은 사람마다 0.25초에 한 번, 같은 사람의 앞 요청이 끝나기 전 새 요청은
 * 마지막 것만 처리 (generation). 방 이동은 /방구경 과 같은 규칙: 내 방·운영자는 바로, 그 밖은 친구만 (Rooms.open 이 다시 확인).
 */
final class HousingRankServer implements PluginMessageListener, Listener, AutoCloseable {
    private final HousingPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();

    private static final class State { Query latest; boolean busy; long next, done; }

    HousingRankServer(HousingPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HousingRankProtocol.CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, HousingRankProtocol.CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    boolean hasMod(Player p) { return p.getListeningPluginChannels().contains(HousingRankProtocol.CHANNEL); }

    /** /하우징랭킹: 클라에 창을 열라고 알린다. 모드가 없으면 false. */
    boolean open(Player p) {
        if (!hasMod(p)) return false;
        send(p, new Open(HousingRankProtocol.DAILY));
        return true;
    }

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!HousingRankProtocol.CHANNEL.equals(channel)) return;
        ToServer m;
        try { m = HousingRankProtocol.toServer(bytes); } catch (IllegalArgumentException bad) { return; }
        var s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        switch (m) {
            case Query q -> {
                if (s.latest != null && q.generation() <= s.latest.generation()) return;
                s.latest = q;
                if (!s.busy) begin(p, s);
            }
            case Visit v -> visit(p, v.owner());
        }
    }

    private void begin(Player p, State s) {
        var q = s.latest;
        if (q == null || q.generation() <= s.done || !p.isOnline()) return;
        long now = System.currentTimeMillis();
        if (now < s.next) { Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!s.busy) begin(p, s); }, Math.max(1, (s.next - now + 49) / 50)); return; }
        s.next = now + 250; s.busy = true;
        plugin.store().ranking(q.metric(), q.page(), p.getUniqueId(), p.isOp(), page -> {
            s.busy = false;
            if (!p.isOnline() || states.get(p.getUniqueId()) != s) return;
            if (s.latest != q) { begin(p, s); return; }
            s.done = q.generation();
            if (page == null) { send(p, new Page(q.generation(), q.metric(), 0, 1, List.of(), "순위를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")); return; }
            var rows = new ArrayList<Row>();
            for (var r : page.rows()) rows.add(new Row(r.rank(), r.owner(), r.name(), r.today(), r.total(), r.hearts(), r.visit()));
            send(p, new Page(q.generation(), q.metric(), page.page(), page.pages(), rows, ""));
        });
    }

    /** /방구경 과 같은 규칙. 친구가 아니면 이동하지 않고 알림만. */
    private void visit(Player p, UUID owner) {
        if (owner.equals(p.getUniqueId()) || p.isOp()) { plugin.rooms().open(p, owner, false); return; }
        plugin.store().isFriend(p.getUniqueId(), owner, friend -> {
            if (!p.isOnline()) return;
            if (Boolean.TRUE.equals(friend)) plugin.rooms().open(p, owner, false);
            else send(p, new Notice(friend == null ? "지금은 친구 정보를 확인할 수 없어요. 잠시 후 다시 시도해 주세요." : "친구의 방만 방문할 수 있어요."));
        });
    }

    private void send(Player p, ToClient m) { if (p.isOnline() && hasMod(p)) p.sendPluginMessage(plugin, HousingRankProtocol.CHANNEL, HousingRankProtocol.encode(m)); }

    @EventHandler public void quit(PlayerQuitEvent e) { states.remove(e.getPlayer().getUniqueId()); }

    @Override public void close() {
        states.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, HousingRankProtocol.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, HousingRankProtocol.CHANNEL);
    }
}
