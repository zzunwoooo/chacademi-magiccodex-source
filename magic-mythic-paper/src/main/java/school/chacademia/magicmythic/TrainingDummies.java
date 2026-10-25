package school.chacademia.magicmythic;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * 오픈 전 #107 마법 연습장 훈련 허수아비 (2026-10-25).
 * 운영자가 서 있는 자리에 "죽지 않는 허수아비"를 세운다. 모델이 따로 없어(저장소에 허수아비 모델 없음) 바닐라 허스크를 AI 끔으로 쓴다.
 *  - 무적이 아니다: 피해·원소 표식·반응·디버프(기절·둔화 …)가 보통 몹과 똑같이 들어가고, 체력만 다시 채운다(한 방에 죽을 피해는 1 남김).
 *  - 맞으면 머리 위 글씨(최근 N초 합계 · 초당 피해)와 때린 사람에게 알림 카드(이번 피해 · 합계 · 초당).
 *  - 위치 목록은 plugins/ChAcademiaMagicMythic/training-dummies.yml. 엔티티는 저장하지 않고(persistent=false) 청크가 로드돼 있으면 5초마다 확인해 다시 세운다.
 *  - 넉백·끌어오기로 밀려도 제자리로 돌아온다.
 * 서버 부하: 위치 수만큼(보통 몇 개) 5초 간격 확인 + 맞을 때만 계산. 매 틱 순회 없음.
 */
final class TrainingDummies implements Listener, AutoCloseable {
    static final String TAG = "chacademia_training_dummy";
    /** 다른 플러그인이 이미 제외하는 표시(교화·퀘스트 처치·야생 몹 상한·기믹)를 같이 단다. */
    static final String PET_TAG = "chacademia_pet";
    private static final String LABEL_TAG = "chacademia_training_dummy_label";

    record Spot(String world, double x, double y, double z, float yaw) {
        String line() { return world + ";" + x + ";" + y + ";" + z + ";" + yaw; }
        static Spot parse(String s) {
            String[] f = s.split(";");
            if (f.length != 5) return null;
            try { return new Spot(f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), Float.parseFloat(f[4])); }
            catch (NumberFormatException e) { return null; }
        }
        Location at(World w) { return new Location(w, x, y, z, yaw, 0); }
    }
    /** 한 번 맞은 기록 (ms, 피해). */
    record Hit(long at, double amount) {}

    /** Pure: 최근 window 안의 합계와 초당 피해(첫 타부터 지금까지, 최소 1초로 나눔). */
    static double[] window(Deque<Hit> hits, long now, long windowMs) {
        while (!hits.isEmpty() && now - hits.peekFirst().at() > windowMs) hits.pollFirst();
        double sum = 0; long first = now;
        for (Hit h : hits) { sum += h.amount(); first = Math.min(first, h.at()); }
        double seconds = Math.max(1.0, (now - first) / 1000.0);
        return new double[]{sum, hits.isEmpty() ? 0 : sum / seconds};
    }

    private final class Live {
        final Spot spot; UUID body, label; final Deque<Hit> hits = new ArrayDeque<>();
        final Map<UUID, Deque<Hit>> byPlayer = new HashMap<>(); UUID lastAttacker; long lastAt;
        Live(Spot spot) { this.spot = spot; }
    }

    private final MagicMythicPlugin plugin;
    private final File file;
    private final List<Live> live = new ArrayList<>();
    private final Map<UUID, Live> byBody = new HashMap<>();
    private final Map<UUID, Double> pendingFinal = new HashMap<>();
    private final BukkitTask task;

    TrainingDummies(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "training-dummies.yml");
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 40, 100);
    }

    private long windowMs() { return Math.round(Math.clamp(plugin.getConfig().getDouble("training-dummy.window-seconds", 5), 1, 60) * 1000); }

    // ------------------------------------------------------------------ 저장

    private void load() {
        if (!file.isFile()) return;
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("spots")) {
            Spot spot = Spot.parse(s);
            if (spot != null) live.add(new Live(spot));
        }
    }

    private boolean save() {
        var yaml = new YamlConfiguration();
        var lines = new ArrayList<String>();
        for (Live l : live) lines.add(l.spot.line());
        yaml.set("spots", lines);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            yaml.save(tmp);
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("training-dummies.yml 저장 실패: " + ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ 세우기·유지

    /** 5초마다: 청크가 로드된 자리에 허수아비가 없으면 세우고, 밀려났으면 되돌린다. */
    private void check() {
        long now = System.currentTimeMillis();
        for (Live l : live) {
            World w = Bukkit.getWorld(l.spot.world());
            if (w == null || !w.isChunkLoaded(((int) Math.floor(l.spot.x())) >> 4, ((int) Math.floor(l.spot.z())) >> 4)) continue;
            Entity body = l.body == null ? null : Bukkit.getEntity(l.body);
            if (!(body instanceof LivingEntity le) || !le.isValid() || le.isDead()) { spawn(l, w); continue; }
            home(l, le);
            if (!l.hits.isEmpty() && now - l.lastAt > windowMs()) { l.hits.clear(); l.byPlayer.clear(); label(l, null); }
        }
    }

    private void spawn(Live l, World w) {
        despawn(l);
        Location at = l.spot.at(w);
        Husk body = w.spawn(at, Husk.class, h -> {
            h.setAI(false); h.setSilent(true); h.setPersistent(false); h.setRemoveWhenFarAway(false);
            h.setCanPickupItems(false); h.setShouldBurnInDay(false);
            h.addScoreboardTag(TAG); h.addScoreboardTag(PET_TAG);
            h.customName(Component.text("훈련 허수아비", NamedTextColor.GOLD)); h.setCustomNameVisible(true);
            if (h.getEquipment() != null) h.getEquipment().clear();
            var hp = h.getAttribute(Attribute.MAX_HEALTH); if (hp != null) hp.setBaseValue(1024);
            var armor = h.getAttribute(Attribute.ARMOR); if (armor != null) armor.setBaseValue(0);
            var kb = h.getAttribute(Attribute.KNOCKBACK_RESISTANCE); if (kb != null) kb.setBaseValue(1);
            h.setHealth(Targets.maxHealth(h));
        });
        TextDisplay label = w.spawn(at.clone().add(0, body.getHeight() + 0.55, 0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER); d.setPersistent(false); d.setShadowed(true);
            d.addScoreboardTag(LABEL_TAG); d.text(Component.empty());
        });
        l.body = body.getUniqueId(); l.label = label.getUniqueId();
        byBody.put(l.body, l);
    }

    private void despawn(Live l) {
        if (l.body != null) { byBody.remove(l.body); Entity e = Bukkit.getEntity(l.body); if (e != null) e.remove(); }
        if (l.label != null) { Entity e = Bukkit.getEntity(l.label); if (e != null) e.remove(); }
        l.body = null; l.label = null;
    }

    private void home(Live l, LivingEntity body) {
        Location at = l.spot.at(body.getWorld());
        if (body.getLocation().distanceSquared(at) > 0.09) { body.setVelocity(new org.bukkit.util.Vector()); body.teleport(at); }
    }

    private void label(Live l, String text) {
        if (l.label == null || !(Bukkit.getEntity(l.label) instanceof TextDisplay d)) return;
        d.text(text == null ? Component.empty() : Component.text(text, NamedTextColor.YELLOW));
    }

    // ------------------------------------------------------------------ 피해

    /** 한 방에 죽는 피해는 1을 남긴다(무적 아님: 피해·반응·디버프는 그대로 처리됨). 원래 피해는 기록용으로 기억. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !byBody.containsKey(le.getUniqueId())) return;
        double fin = e.getFinalDamage();
        pendingFinal.put(le.getUniqueId(), fin);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        if (fin >= le.getHealth() - 1) e.setDamage(Math.max(0, le.getHealth() - 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        Live l = byBody.get(le.getUniqueId());
        if (l == null) return;
        Double original = pendingFinal.remove(le.getUniqueId());
        double amount = original != null ? original : e.getFinalDamage();
        if (amount <= 0) return;
        long now = System.currentTimeMillis();
        Player attacker = e instanceof EntityDamageByEntityEvent by ? PartyFriendlyFire.attacker(by.getDamager()) : null;
        // 독·화상 터짐처럼 출처 없이 들어오는 피해는 직전(창 안)에 때린 사람 몫으로 센다.
        if (attacker == null && l.lastAttacker != null && now - l.lastAt <= windowMs()) attacker = Bukkit.getPlayer(l.lastAttacker);
        l.lastAt = now;
        l.hits.addLast(new Hit(now, amount));
        double[] all = window(l.hits, now, windowMs());
        long sec = Math.round(windowMs() / 1000.0);
        label(l, String.format("최근 %d초 %.1f · 초당 %.1f", sec, all[0], all[1]));
        if (attacker != null) {
            l.lastAttacker = attacker.getUniqueId();
            var mine = l.byPlayer.computeIfAbsent(attacker.getUniqueId(), k -> new ArrayDeque<>());
            mine.addLast(new Hit(now, amount));
            double[] own = window(mine, now, windowMs());
            ChacaLink.notify(attacker, "system", "magic-mythic:training-dummy", "허수아비 피해 " + String.format("%.1f", amount),
                String.format("최근 %d초 합계 %.1f · 초당 %.1f", sec, own[0], own[1]), 700);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!le.isValid() || le.isDead()) return;
            le.setHealth(Targets.maxHealth(le));
            home(l, le);
        }, 10);
    }

    /** AI 를 꺼 두었지만 혹시 다른 플러그인이 켜도 아무도 노리지 않는다. */
    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetEvent e) { if (byBody.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true); }

    @EventHandler public void worldUnload(WorldUnloadEvent e) {
        for (Live l : live) if (l.spot.world().equals(e.getWorld().getName())) despawn(l);
    }

    // ------------------------------------------------------------------ 명령 (/컨텐츠관리 연습장 허수아비 …)

    LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("연습장").then(Commands.literal("허수아비")
            .then(Commands.literal("설치").executes(ctx -> {
                Player p = player(ctx.getSource());
                Location at = p.getLocation();
                Spot spot = new Spot(at.getWorld().getName(), at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5, Math.round(at.getYaw() / 45f) * 45f);
                for (Live l : live) if (l.spot.world().equals(spot.world()) && Math.abs(l.spot.x() - spot.x()) < 0.6 && Math.abs(l.spot.z() - spot.z()) < 0.6 && Math.abs(l.spot.y() - spot.y()) < 2)
                    throw ChacaLink.error("여기에는 이미 허수아비가 있어요");
                Live l = new Live(spot);
                live.add(l);
                if (!save()) { live.remove(l); throw ChacaLink.error("training-dummies.yml 을 저장하지 못했어요 (콘솔 확인)"); }
                spawn(l, at.getWorld());
                ChacaLink.ok(ctx.getSource().getSender(), "훈련 허수아비를 세웠어요 (" + live.size() + "개)");
                return 1;
            }))
            .then(Commands.literal("제거").executes(ctx -> {
                Player p = player(ctx.getSource());
                Live best = null; double bd = 9;
                for (Live l : live) {
                    if (!l.spot.world().equals(p.getWorld().getName())) continue;
                    double d = l.spot.at(p.getWorld()).distanceSquared(p.getLocation());
                    if (d < bd) { bd = d; best = l; }
                }
                if (best == null) throw ChacaLink.error("3칸 안에 허수아비가 없어요");
                live.remove(best);
                save();
                despawn(best);
                ChacaLink.ok(ctx.getSource().getSender(), "훈련 허수아비를 치웠어요 (남은 " + live.size() + "개)");
                return 1;
            }))
            .then(Commands.literal("목록").executes(ctx -> {
                var lines = new ArrayList<String>();
                lines.add("── 훈련 허수아비 " + live.size() + "개 ──");
                for (Live l : live) lines.add(String.format(" %s %.1f, %.1f, %.1f", l.spot.world(), l.spot.x(), l.spot.y(), l.spot.z()));
                ChacaLink.lines(ctx.getSource().getSender(), lines);
                return 1;
            })));
    }

    private static Player player(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (src.getSender() instanceof Player p) return p;
        throw ChacaLink.error("게임 안에서만 쓸 수 있어요");
    }

    int count() { return live.size(); }

    @Override public void close() {
        task.cancel();
        for (Live l : live) despawn(l);
        byBody.clear(); pendingFinal.clear();
    }
}
