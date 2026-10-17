package kr.chacademy.motionplugin;

import kr.chacademy.motionplugin.broom.BroomCommand;
import kr.chacademy.motionplugin.broom.BroomService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 모드 → chacademy:cast → (검증) → 주변 유저 → chacademy:anim
 */
public final class ChacademyMotionPlugin extends JavaPlugin implements PluginMessageListener, Listener, TabCompleter {

    private static ChacademyMotionPlugin instance;

    private final Map<UUID, Long> lastCast = new HashMap<>();
    private Set<String> allowed = Set.of();
    private long cooldownMs;
    private boolean rangeMode;
    private double rangeSq;
    private boolean debug;
    private BroomService brooms;

    public static ChacademyMotionPlugin get() {
        return instance;
    }

    // ------------------------------------------------------------------ 빗자루 비행 API
    // 다른 플러그인(마법 미몹 등)은 컴파일 의존 없이 리플렉션으로 아래 메서드를 부른다.

    /**
     * 비행 마법 진입점. 타고 있으면 내리고, 아니면 탄다
     * (빗자루가 있으면 시간 제한 없음, 없으면 연습용 빗자루로 practiceTicks 동안).
     *
     * @return 처리했으면 true, 모드가 없어서 빗자루 비행을 못 하면 false (호출한 쪽에서 예전 방식으로 처리)
     */
    public boolean broomFlight(@NotNull Player player, int practiceTicks) {
        return brooms != null && brooms.flightSpell(player, practiceTicks);
    }

    /** 빗자루를 타고 있는지. 위치를 바꾸는 마법을 막을 때 쓴다. */
    public boolean isRidingBroom(@NotNull Player player) {
        return brooms != null && brooms.isRiding(player);
    }

    /**
     * 빗자루 비행 속도 {x, y, z} (블록/틱). 타고 있지 않으면 null.
     * 투사체 마법(마법 미몹 chabolt, PortableVFX 투사체 연출)이 리플렉션으로 불러
     * "탄 사람 속도만큼 더 빠르게, 지연만큼 앞에서" 출발시킨다.
     */
    public double[] broomVelocity(@NotNull Player player) {
        return brooms == null ? null : brooms.velocity(player);
    }

    /** 강제로 빗자루에서 내리게 한다 (구역 보호 등). */
    public void stopBroom(@NotNull Player player) {
        if (brooms != null) brooms.stop(player);
    }

    /** 균형(안정성) 게이지를 가득 채운다 (큐디치 안정성 성소). 타고 있지 않으면 false. */
    /** 장착한 빗자루 id (없으면 ""). 큐디치 준비 화면에 쓴다. */
    public String equippedBroomId(@NotNull Player player) {
        var t = brooms == null ? null : brooms.ownedBroom(player);
        return t == null ? "" : t.id();
    }

    public boolean refillBroomBalance(@NotNull Player player) {
        return brooms != null && brooms.fx(player, kr.chacademy.motionplugin.broom.BroomCodec.FX_REFILL_BALANCE);
    }

    /** 기본 가속과 같은 가속을 바로 한 번. 가속 쿨타임은 쓰지도 시작하지도 않아 쿨타임 중에도 된다 (큐디치 가속 성소). */
    public boolean freeBroomBoost(@NotNull Player player) {
        return brooms != null && brooms.fx(player, kr.chacademy.motionplugin.broom.BroomCodec.FX_FREE_BOOST);
    }

    public BroomService brooms() {
        return brooms;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();

        var messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, MotionCodec.CHANNEL_CAST, this);
        messenger.registerOutgoingPluginChannel(this, MotionCodec.CHANNEL_ANIM);

        getServer().getPluginManager().registerEvents(this, this);

        brooms = new BroomService(this);
        brooms.start();
        var broomCmd = getCommand("빗자루");
        if (broomCmd != null) {
            var exec = new BroomCommand(brooms);
            broomCmd.setExecutor(exec);
            broomCmd.setTabCompleter(exec);
        }

        var cmd = getCommand("motion");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("모션 채널 등록 완료 (" + MotionCodec.CHANNEL_CAST + " → " + MotionCodec.CHANNEL_ANIM + ")");
    }

    @Override
    public void onDisable() {
        if (brooms != null) brooms.shutdown();
        var messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this);
        messenger.unregisterOutgoingPluginChannel(this);
        instance = null;
    }

    private void loadSettings() {
        reloadConfig();
        var cfg = getConfig();
        allowed = new HashSet<>(cfg.getStringList("allowed-animations"));
        cooldownMs = Math.max(0, cfg.getLong("cooldown-ms", 500));
        rangeMode = "range".equalsIgnoreCase(cfg.getString("broadcast-mode", "tracked"));
        double range = cfg.getDouble("range", 48);
        rangeSq = range * range;
        debug = cfg.getBoolean("debug", false);
    }

    // ------------------------------------------------------------------ 모드 → 서버

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!MotionCodec.CHANNEL_CAST.equals(channel)) return;

        String animation;
        try {
            animation = MotionCodec.decodeCast(message);
        } catch (MotionCodec.MalformedPacketException e) {
            if (debug) getLogger().warning(player.getName() + " 의 잘못된 cast 패킷: " + e.getMessage());
            return;
        }

        if (!allowed.contains(animation)) {
            if (debug) getLogger().info(player.getName() + " 이(가) 허용되지 않은 모션 요청: " + animation);
            return;
        }
        if (!player.hasPermission("chacademy.motion.cast")) return;

        long now = System.currentTimeMillis();
        Long last = lastCast.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastCast.put(player.getUniqueId(), now);

        var event = new MotionCastEvent(player, animation, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // 시전자는 이미 자기 화면에서 재생했으므로 제외
        int sent = broadcast(player, animation, false);
        if (debug) getLogger().info(player.getName() + " → " + animation + " (" + sent + "명에게 전송)");
    }

    // ------------------------------------------------------------------ 서버 → 모드

    /**
     * 서버 쪽에서 모션을 재생시킨다. 다른 플러그인(마법 판정 등)에서 호출하면 된다.
     *
     * @param includeSelf 시전자 본인 화면에서도 재생할지 (서버 주도 방식이면 true)
     * @return 패킷을 보낸 유저 수 (모드를 안 깐 유저는 Bukkit 이 알아서 건너뜀)
     */
    public int playMotion(@NotNull Player player, @NotNull String animation, boolean includeSelf) {
        var event = new MotionCastEvent(player, animation, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return 0;
        return broadcast(player, animation, includeSelf);
    }

    private int broadcast(Player caster, String animation, boolean includeSelf) {
        byte[] payload = MotionCodec.encodeAnim(caster.getUniqueId(), animation);
        int count = 0;
        for (Player viewer : viewersOf(caster)) {
            if (viewer.equals(caster)) continue;
            viewer.sendPluginMessage(this, MotionCodec.CHANNEL_ANIM, payload);
            count++;
        }
        if (includeSelf) {
            caster.sendPluginMessage(this, MotionCodec.CHANNEL_ANIM, payload);
            count++;
        }
        return count;
    }

    private Collection<? extends Player> viewersOf(Player caster) {
        if (!rangeMode) {
            // Paper API: 이 플레이어를 클라이언트에 불러와 둔(=볼 수 있는) 유저들
            return caster.getTrackedBy();
        }
        List<Player> list = new ArrayList<>();
        var loc = caster.getLocation();
        for (Player p : caster.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) list.add(p);
        }
        return list;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastCast.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ 명령어

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§a[ChacademyMotion] 설정을 다시 불러왔어요. 허용 모션: " + allowed);
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("play")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c접속 중인 플레이어가 아니에요: " + args[1]);
                return true;
            }
            int sent = playMotion(target, args[2], true);
            sender.sendMessage("§a" + target.getName() + " 에게 " + args[2] + " 모션 재생 (" + sent + "명에게 전송)");
            return true;
        }
        sender.sendMessage("§e/motion play <플레이어> <모션>  §7- 서버에서 모션 재생 (본인 포함)");
        sender.sendMessage("§e/motion reload  §7- config.yml 다시 불러오기");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return filter(List.of("play", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("play")) return filter(new ArrayList<>(allowed), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).toList();
    }
}
