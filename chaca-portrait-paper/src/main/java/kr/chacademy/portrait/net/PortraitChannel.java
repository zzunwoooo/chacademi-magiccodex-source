package kr.chacademy.portrait.net;

import kr.chacademy.portrait.ChacaPortraitPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import school.magiccodex.portrait.PortraitProtocol;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 일러스트 채널. 버전 handshake(HELLO/HELLO_ACK)를 마친 클라이언트에게만 보낸다.
 * 클라이언트가 HELLO에 캐시된 SHA-256을 실어 보내면, 같을 때는 META(조각 0)만 보내 다시 받지 않게 한다.
 * PNG는 192KB 조각으로 틱당 1개씩 보낸다 (접속 직후 대역폭 몰림 방지).
 * 다른 서버에서 완성된 일러스트는 {@link #syncRemote()}가 주기적으로 SHA를 비교해 전달한다.
 */
public final class PortraitChannel implements PluginMessageListener, Listener {

    private final ChacaPortraitPlugin plugin;
    private final Map<UUID, Boolean> capable = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> transfers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gate = new ConcurrentHashMap<>();
    private final Map<UUID, Long> helloAt = new ConcurrentHashMap<>();
    /** 이번 접속에서 클라이언트에게 마지막으로 알려 준 일러스트 SHA ("" = 없음). HELLO를 마친 플레이어만 들어 있다. */
    private final Map<UUID, String> sentSha = new ConcurrentHashMap<>();
    private volatile boolean syncing;

    public PortraitChannel(ChacaPortraitPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, PortraitProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, PortraitProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        transfers.values().forEach(BukkitTask::cancel);
        transfers.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, PortraitProtocol.REQUEST, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, PortraitProtocol.RESPONSE);
        capable.clear();
        sentSha.clear();
    }

    public boolean capable(Player p) {
        return Boolean.TRUE.equals(capable.get(p.getUniqueId()))
                && p.getListeningPluginChannels().contains(PortraitProtocol.RESPONSE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        capable.remove(id);
        gate.remove(id);
        helloAt.remove(id);
        sentSha.remove(id);
        BukkitTask t = transfers.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    private void send(Player p, PortraitProtocol.Packet packet) {
        if (!p.isOnline()) {
            return;
        }
        try {
            p.sendPluginMessage(plugin, PortraitProtocol.RESPONSE, PortraitProtocol.encode(packet));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[ChacaPortrait] 패킷 인코딩 실패: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!PortraitProtocol.REQUEST.equals(channel)) {
            return;
        }
        PortraitProtocol.Packet r;
        try {
            r = PortraitProtocol.decode(bytes);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (r.op() == PortraitProtocol.C_PROMPT) {
            long now = System.currentTimeMillis();
            Long last = gate.get(p.getUniqueId());
            if (last != null && now - last < 100) {
                return;
            }
            gate.put(p.getUniqueId(), now);
        }
        switch (r.op()) {
            case PortraitProtocol.C_HELLO -> hello(p, r);
            case PortraitProtocol.C_PROMPT -> {
                if (!capable(p) || !PortraitProtocol.validToken(r.text())) {
                    return;
                }
                plugin.rerolls().answer(p, r.text(), r.extra(), r.flag());
            }
            default -> {
            }
        }
    }

    private void hello(Player p, PortraitProtocol.Packet r) {
        // 접속마다 한 번이면 충분 (반복 HELLO로 DB·전송을 몰아붙이지 못하게 10초 간격)
        long at = System.currentTimeMillis();
        Long last = helloAt.get(p.getUniqueId());
        if (last != null && at - last < 10_000) {
            return;
        }
        helloAt.put(p.getUniqueId(), at);
        if (r.number() != PortraitProtocol.VERSION) {
            capable.remove(p.getUniqueId());
            return;
        }
        capable.put(p.getUniqueId(), true);
        send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_HELLO_ACK, "", PortraitProtocol.VERSION, ""));
        String cached = PortraitProtocol.validSha(r.text()) ? r.text() : "";
        UUID id = p.getUniqueId();
        plugin.service().then(plugin.db().call(() -> plugin.storage().sha(id)), sha -> {
            Player now = Bukkit.getPlayer(id);
            if (now == null || !capable(now)) {
                return;
            }
            if (sha == null) {
                sentSha.put(id, "");
                send(now, PortraitProtocol.Packet.of(PortraitProtocol.S_META, "", 0, "0"));
            } else if (sha.equals(cached)) {
                sentSha.put(id, sha);
                send(now, PortraitProtocol.Packet.of(PortraitProtocol.S_META, sha, 0, "0"));
            } else {
                plugin.service().then(plugin.db().call(() -> plugin.storage().portrait(id)), portrait -> {
                    Player again = Bukkit.getPlayer(id);
                    if (again == null || !capable(again)) {
                        return;
                    }
                    if (portrait != null) {
                        push(again, portrait.sha(), portrait.png());
                    } else {
                        // 그 사이에 삭제됨 → "없음"으로 알려 클라이언트가 계속 기다리지 않게 한다
                        sentSha.put(id, "");
                        send(again, PortraitProtocol.Packet.of(PortraitProtocol.S_META, "", 0, "0"));
                    }
                }, ex -> plugin.getLogger().warning("[ChacaPortrait] 일러스트 읽기 실패: " + ex.getMessage()));
            }
        }, ex -> plugin.getLogger().warning("[ChacaPortrait] 일러스트 조회 실패: " + ex.getMessage()));
    }

    /** 메인 스레드: 본인 일러스트 전송 (이전 전송은 취소). */
    public void push(Player p, String sha, byte[] png) {
        if (!capable(p) || png == null || png.length == 0 || png.length > PortraitProtocol.MAX_IMAGE_BYTES) {
            return;
        }
        UUID id = p.getUniqueId();
        BukkitTask old = transfers.remove(id);
        if (old != null) {
            old.cancel();
        }
        int chunks = PortraitProtocol.chunks(png.length);
        sentSha.put(id, sha);
        send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_META, sha, png.length, Integer.toString(chunks)));
        int[] next = {0};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player now = Bukkit.getPlayer(id);
            if (now == null || next[0] >= chunks || transfers.get(id) != self[0]) {
                self[0].cancel();
                transfers.remove(id, self[0]);
                return;
            }
            int from = next[0] * PortraitProtocol.CHUNK_BYTES;
            int to = Math.min(png.length, from + PortraitProtocol.CHUNK_BYTES);
            send(now, new PortraitProtocol.Packet(PortraitProtocol.S_CHUNK, sha, next[0], "", false, Arrays.copyOfRange(png, from, to)));
            next[0]++;
        }, 1L, 1L);
        transfers.put(id, self[0]);
    }

    /** 일러스트 삭제 알림 (/portrait reset). */
    public void cleared(Player p) {
        if (capable(p)) {
            sentSha.put(p.getUniqueId(), "");
            send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_META, "", 0, "0"));
        }
    }

    /**
     * 다른 서버에서 완성·삭제된 일러스트 전달 (메인 스레드, 주기 호출).
     * HELLO를 마친 접속자들의 현재 SHA를 쿼리 한 번으로 읽어, 이번 접속에서 보낸 것과 다르면 새 일러스트를 보내고 완성 안내를 한 번 한다.
     * 이 서버에서 그리는 중인 플레이어는 건너뛴다 (완료 시 이 서버가 직접 보낸다).
     */
    public void syncRemote() {
        if (syncing || sentSha.isEmpty()) {
            return;
        }
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (UUID id : sentSha.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && capable(p) && !plugin.service().busy(id) && !transfers.containsKey(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        syncing = true;
        plugin.service().then(plugin.db().call(() -> plugin.storage().shas(ids)), found -> {
            syncing = false;
            for (UUID id : ids) {
                String sent = sentSha.get(id);
                Player p = Bukkit.getPlayer(id);
                if (sent == null || p == null || !capable(p) || plugin.service().busy(id)) {
                    continue;
                }
                String current = found.get(id);
                if (current == null) {
                    if (!sent.isEmpty()) {
                        cleared(p); // 다른 서버에서 삭제됨 (/portrait reset)
                    }
                    continue;
                }
                if (current.equals(sent)) {
                    continue;
                }
                plugin.service().then(plugin.db().call(() -> plugin.storage().portrait(id)), portrait -> {
                    Player again = Bukkit.getPlayer(id);
                    if (again == null || portrait == null || !capable(again) || portrait.sha().equals(sentSha.get(id))) {
                        return;
                    }
                    push(again, portrait.sha(), portrait.png());
                    if (!portrait.sha().equals(sentSha.get(id))) {
                        sentSha.put(id, portrait.sha()); // 보낼 수 없는 그림 (크기 초과 등) — 20초마다 다시 읽고 안내하지 않게 한다
                        return;
                    }
                    again.sendMessage(plugin.settings().message("done"));
                }, ex -> plugin.getLogger().warning("[ChacaPortrait] 일러스트 읽기 실패: " + ex.getMessage()));
            }
        }, ex -> {
            syncing = false;
            plugin.getLogger().warning("[ChacaPortrait] 일러스트 변경 확인 실패: " + ex.getMessage());
        });
    }

    public void openPrompt(Player p, String token, String hint) {
        send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_PROMPT_OPEN, token, 0, hint));
    }

    public void closePrompt(Player p) {
        if (capable(p)) {
            send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_PROMPT_CLOSE, "", 0, ""));
        }
    }

    public void status(Player p, String text) {
        if (capable(p) && text != null && !text.isEmpty()) {
            send(p, PortraitProtocol.Packet.of(PortraitProtocol.S_STATUS, "", 0,
                    text.length() > PortraitProtocol.MAX_STATUS_CHARS ? text.substring(0, PortraitProtocol.MAX_STATUS_CHARS) : text));
        }
    }
}
