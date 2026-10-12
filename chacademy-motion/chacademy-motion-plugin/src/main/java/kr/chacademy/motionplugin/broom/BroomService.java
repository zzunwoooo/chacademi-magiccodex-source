package kr.chacademy.motionplugin.broom;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 빗자루 비행 (서버 쪽).
 *
 * <p>움직임·균형·낙마 판정은 전부 모드(플레이어 PC)가 한다. 서버는
 * <ol>
 *   <li>탈 수 있는지 확인하고 조작 수치를 한 번 보낸다 ({@link BroomCodec#CHANNEL_RIDE})</li>
 *   <li>주변 유저에게 "누가 어떤 빗자루를 탔다"를 한 번 알린다 ({@link BroomCodec#CHANNEL_STATE})</li>
 *   <li>탄 사람만 틱마다 가볍게 검사한다: 낙하 거리 초기화, 속도 상한, 시간 제한, 숙련도 기록</li>
 * </ol>
 * 탄 사람이 없으면 틱 작업도 돌지 않는다.
 */
public final class BroomService implements Listener, PluginMessageListener {

    private final JavaPlugin plugin;
    private final BroomStore store;
    private final Map<String, BroomType> brooms = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, PendingCatch> catches = new HashMap<>();
    private final Map<UUID, Long> lastRoll = new HashMap<>();
    private BukkitTask task;
    private long tick;

    // brooms.yml 설정
    private String practiceBroom = "broom_1";
    private long masteryFullSeconds = 3 * 3600;
    private int boostCooldown = 120;
    private int rollCooldown = 60;
    private int catchWindow = 40;
    private Set<String> blockedWorlds = Set.of();
    private boolean debug;

    private static final class Session {
        final UUID id;
        final BroomType type;
        final boolean prevAllowFlight;
        int remaining; // 0 = 제한 없음
        final boolean limited;
        final SpeedGuard guard = new SpeedGuard();
        Location last;
        int secondTicks;

        Session(UUID id, BroomType type, boolean prevAllowFlight, int limitTicks) {
            this.id = id;
            this.type = type;
            this.prevAllowFlight = prevAllowFlight;
            this.remaining = limitTicks;
            this.limited = limitTicks > 0;
        }
    }

    private record PendingCatch(BroomType type, int remaining, boolean prevAllowFlight, long until) {}

    public BroomService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new BroomStore(plugin);
        reload();
    }

    // ------------------------------------------------------------------ 설정

    public void reload() {
        File file = new File(plugin.getDataFolder(), "brooms.yml");
        if (!file.exists()) plugin.saveResource("brooms.yml", false);
        var yml = YamlConfiguration.loadConfiguration(file);
        brooms.clear();
        var section = yml.getConfigurationSection("brooms");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var s = section.getConfigurationSection(id);
                if (s != null && id.matches("[a-z0-9_]{1,40}")) brooms.put(id, BroomType.read(id, s));
            }
        }
        practiceBroom = yml.getString("practice-broom", "broom_1");
        masteryFullSeconds = Math.max(60, Math.round(yml.getDouble("mastery-full-hours", 3) * 3600));
        boostCooldown = Math.max(20, yml.getInt("boost-cooldown-ticks", 120));
        rollCooldown = Math.max(20, yml.getInt("roll-cooldown-ticks", 60));
        catchWindow = Math.max(10, yml.getInt("catch-window-ticks", 40));
        blockedWorlds = new HashSet<>(yml.getStringList("blocked-worlds"));
        debug = yml.getBoolean("debug", false);
    }

    public Map<String, BroomType> brooms() {
        return brooms;
    }

    public void start() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, BroomCodec.CHANNEL_RIDE);
        messenger.registerOutgoingPluginChannel(plugin, BroomCodec.CHANNEL_STATE);
        messenger.registerIncomingPluginChannel(plugin, BroomCodec.CHANNEL_EVENT, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // 기록은 5분마다 저장 (바뀐 게 있을 때만)
        Bukkit.getScheduler().runTaskTimer(plugin, store::saveIfDirty, 6000, 6000);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) stop(id, BroomCodec.STATE_NORMAL, true);
        store.saveIfDirty();
    }

    // ------------------------------------------------------------------ 공개 API

    /** 모드가 깔려 있어서 빗자루 비행을 할 수 있는 클라이언트인지. */
    public boolean hasMod(Player p) {
        return p.getListeningPluginChannels().contains(BroomCodec.CHANNEL_RIDE);
    }

    public boolean isRiding(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** 고른(가진) 빗자루. 없으면 null. */
    public BroomType ownedBroom(Player p) {
        return brooms.get(store.broom(p.getUniqueId()));
    }

    public float mastery(UUID id) {
        return BroomMastery.of(store.seconds(id), masteryFullSeconds);
    }

    /**
     * 비행 마법에서 부르는 진입점. 타고 있으면 내리고, 아니면 탄다.
     * 빗자루를 가지고 있으면 시간 제한 없이, 없으면 연습용 빗자루로 practiceTicks 동안.
     *
     * @return 처리했으면 true. 모드가 없는 등 빗자루 비행을 못 하면 false (호출한 쪽이 예전 비행으로 대신 처리)
     */
    public boolean flightSpell(Player p, int practiceTicks) {
        if (isRiding(p)) {
            stop(p.getUniqueId(), BroomCodec.STATE_NORMAL, false);
            return true;
        }
        if (!hasMod(p)) return false;
        BroomType owned = ownedBroom(p);
        if (owned != null) return start(p, owned, 0);
        BroomType practice = brooms.get(practiceBroom);
        if (practice == null && !brooms.isEmpty()) practice = brooms.values().iterator().next();
        return practice != null && start(p, practice, Math.max(20, practiceTicks));
    }

    /** 빗자루 타기 시작. limitTicks 0 = 제한 없음. */
    public boolean start(Player p, BroomType type, int limitTicks) {
        if (!hasMod(p) || isRiding(p)) return false;
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInsideVehicle() || p.isGliding()) return false;
        if (blockedWorlds.contains(p.getWorld().getName())) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("이곳에서는 빗자루를 탈 수 없습니다."));
            return false;
        }
        if (!p.hasPermission("chacademy.broom.use")) return false;
        var event = new BroomStartEvent(p, type, limitTicks);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        return begin(p, type, limitTicks, p.getAllowFlight());
    }

    private boolean begin(Player p, BroomType type, int limitTicks, boolean prevAllowFlight) {
        var s = new Session(p.getUniqueId(), type, prevAllowFlight, limitTicks);
        s.last = p.getLocation();
        sessions.put(p.getUniqueId(), s);
        catches.remove(p.getUniqueId());
        // 모드가 abilities.flying 을 켜서 서버의 "비행 금지" 판정·낙하 피해 누적을 막는다.
        // 서버가 이 값을 받아들이려면 allowFlight 가 먼저 켜져 있어야 한다.
        p.setAllowFlight(true);
        p.setFallDistance(0);
        var ride = new BroomCodec.Ride(type.id(), type.maxSpeed(), type.accel(), type.turn(), type.brake(),
                type.stability(), type.climb(), mastery(p.getUniqueId()), limitTicks, boostCooldown, rollCooldown);
        p.sendPluginMessage(plugin, BroomCodec.CHANNEL_RIDE, BroomCodec.encodeRide(ride));
        broadcast(p, type.id(), BroomCodec.STATE_START, false);
        ensureTask();
        if (debug) plugin.getLogger().info(p.getName() + " 빗자루 탑승: " + type.id() + (limitTicks > 0 ? " (" + limitTicks + "틱)" : ""));
        return true;
    }

    /** 빗자루에서 내림. reason 은 BroomCodec.STATE_* */
    public void stop(UUID id, byte reason, boolean quitting) {
        var s = sessions.remove(id);
        if (s == null) return;
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        // 본인 포함 전원에게 "내렸다" 알림 (본인 모드는 이걸 받고 비행을 끝냄)
        broadcast(p, "", reason, true);
        if (!quitting) {
            p.setFlying(false);
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.setAllowFlight(s.prevAllowFlight);
            }
            if (reason == BroomCodec.STATE_TIMEOUT) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                p.sendActionBar(net.kyori.adventure.text.Component.text("연습용 빗자루의 마력이 다했습니다."));
            }
            if (reason == BroomCodec.STATE_FALL) {
                catches.put(id, new PendingCatch(s.type, s.remaining, s.prevAllowFlight, tick + catchWindow));
            }
        }
        if (debug) plugin.getLogger().info(p.getName() + " 빗자루 하차 (reason " + reason + ")");
    }

    public void stop(Player p) {
        stop(p.getUniqueId(), BroomCodec.STATE_NORMAL, false);
    }

    // ------------------------------------------------------------------ 틱 검사 (탄 사람만)

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        tick++;
        if (!catches.isEmpty()) catches.values().removeIf(c -> c.until() < tick);
        if (sessions.isEmpty()) {
            if (catches.isEmpty() && task != null) {
                task.cancel();
                task = null;
            }
            return;
        }
        for (var s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.id);
            if (p == null || !p.isOnline()) {
                sessions.remove(s.id);
                continue;
            }
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInsideVehicle()
                    || blockedWorlds.contains(p.getWorld().getName())) {
                stop(s.id, BroomCodec.STATE_NORMAL, false);
                continue;
            }
            p.setFallDistance(0);
            if (!p.getAllowFlight()) p.setAllowFlight(true);

            Location now = p.getLocation();
            if (s.last != null && s.last.getWorld() == now.getWorld()) {
                double cap = s.type.maxSpeed() * 1.45 + 0.6; // 부스트·급강하·구르기 여유
                if (s.guard.tick(s.last.distance(now), cap)) {
                    plugin.getLogger().warning(p.getName() + " 빗자루 속도 상한 초과로 비행 종료 (" + s.type.id() + ")");
                    stop(s.id, BroomCodec.STATE_NORMAL, false);
                    continue;
                }
            } else {
                s.guard.reset();
            }
            s.last = now;

            if (++s.secondTicks >= 20) {
                s.secondTicks = 0;
                store.addSecond(s.id);
            }
            if (s.limited && --s.remaining <= 0) {
                stop(s.id, BroomCodec.STATE_TIMEOUT, false);
            }
        }
    }

    // ------------------------------------------------------------------ 모드 → 서버

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!BroomCodec.CHANNEL_EVENT.equals(channel)) return;
        BroomCodec.Event e;
        try {
            e = BroomCodec.decodeEvent(message);
        } catch (BroomCodec.MalformedException ex) {
            if (debug) plugin.getLogger().warning(player.getName() + " 잘못된 빗자루 패킷: " + ex.getMessage());
            return;
        }
        UUID id = player.getUniqueId();
        switch (e.type()) {
            case BroomCodec.EVT_DISMOUNT -> stop(id, BroomCodec.STATE_NORMAL, false);
            case BroomCodec.EVT_FALL -> {
                if (isRiding(player)) stop(id, BroomCodec.STATE_FALL, false);
            }
            case BroomCodec.EVT_CATCH -> {
                var c = catches.remove(id);
                if (c == null || c.until() < tick || isRiding(player) || player.isDead() || player.isOnGround()) return;
                begin(player, c.type(), c.remaining(), c.prevAllowFlight());
            }
            case BroomCodec.EVT_ROLL_LEFT, BroomCodec.EVT_ROLL_RIGHT -> {
                if (!isRiding(player)) return;
                Long last = lastRoll.get(id);
                if (last != null && tick - last < rollCooldown - 10) return;
                lastRoll.put(id, tick);
                // 구르는 순간 짧은 무적 (회피)
                player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 10));
                byte r = e.type() == BroomCodec.EVT_ROLL_LEFT ? BroomCodec.STATE_ROLL_LEFT : BroomCodec.STATE_ROLL_RIGHT;
                broadcast(player, sessions.get(id).type.id(), r, false);
            }
            default -> {
                // BOOST, CRASH 는 지금은 기록만 (마나 소모 등은 나중에 연결)
                if (debug) plugin.getLogger().info(player.getName() + " 빗자루 이벤트 " + e.type() + " " + e.value());
            }
        }
    }

    // ------------------------------------------------------------------ 전파

    private void broadcast(Player rider, String broom, byte reason, boolean includeSelf) {
        byte[] payload = BroomCodec.encodeState(new BroomCodec.State(rider.getUniqueId(), broom, reason));
        Collection<Player> viewers = rider.getTrackedBy();
        for (Player viewer : viewers) {
            if (!viewer.equals(rider)) viewer.sendPluginMessage(plugin, BroomCodec.CHANNEL_STATE, payload);
        }
        // 시작할 때 본인은 ride 패킷으로 이미 알고 있으므로, 시작이 아닐 때만 본인에게도 보냄
        if (includeSelf || reason != BroomCodec.STATE_START) {
            rider.sendPluginMessage(plugin, BroomCodec.CHANNEL_STATE, payload);
        }
    }

    // ------------------------------------------------------------------ 이벤트

    /** 나중에 시야에 들어온 유저에게도 "이 사람 빗자루 탐"을 알려준다. */
    @EventHandler
    public void onTrack(PlayerTrackEntityEvent e) {
        if (!(e.getEntity() instanceof Player rider)) return;
        var s = sessions.get(rider.getUniqueId());
        if (s == null) return;
        Player viewer = e.getPlayer();
        byte[] payload = BroomCodec.encodeState(new BroomCodec.State(rider.getUniqueId(), s.type.id(), BroomCodec.STATE_NORMAL));
        // 엔티티 생성 패킷보다 늦게 도착하도록 다음 틱에 보냄 (모드는 UUID 로 기억하므로 순서가 바뀌어도 괜찮음)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && sessions.containsKey(rider.getUniqueId())) {
                viewer.sendPluginMessage(plugin, BroomCodec.CHANNEL_STATE, payload);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (sessions.containsKey(id)) {
            var s = sessions.get(id);
            stop(id, BroomCodec.STATE_NORMAL, true);
            // 다음 접속 때 비행 권한이 남지 않게 되돌려 둠
            if (e.getPlayer().getGameMode() == GameMode.SURVIVAL || e.getPlayer().getGameMode() == GameMode.ADVENTURE) {
                e.getPlayer().setFlying(false);
                e.getPlayer().setAllowFlight(s.prevAllowFlight);
            }
        }
        catches.remove(id);
        lastRoll.remove(id);
        store.saveIfDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        stop(e.getPlayer().getUniqueId(), BroomCodec.STATE_NORMAL, false);
        catches.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == GameMode.SPECTATOR) stop(e.getPlayer().getUniqueId(), BroomCodec.STATE_NORMAL, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        var s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) {
            s.guard.reset();
            s.last = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        var s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) {
            s.guard.reset();
            s.last = null;
        }
    }

    /** 타고 있는 동안 낙하 피해는 없다 (내리거나 떨어진 뒤의 낙하만 피해). */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && isRiding(p)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ 명령어에서 쓰는 것

    BroomStore store() {
        return store;
    }

    long masteryFullSeconds() {
        return masteryFullSeconds;
    }

    String practiceBroomId() {
        return practiceBroom;
    }

    BroomType ridingType(Player p) {
        var s = sessions.get(p.getUniqueId());
        return s == null ? null : s.type;
    }

    int ridingCount() {
        return sessions.size();
    }
}
