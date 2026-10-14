package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.ElementProtocol;
import school.magiccodex.protocol.ElementProtocol.Element;
import school.magiccodex.protocol.ElementProtocol.Reaction;

/**
 * Element reactions (regular system). Called by Hit/Bolt/Zone right after a spell hit was accepted.
 *
 * Owner direction: combat is not the server's main focus, so reactions are small utility / control / support /
 * reward effects. None of them adds damage (MAGMA only sets the target on fire for 3 s).
 * Bosses never receive movement control (push / pull / slow / root / stun / target drop).
 *
 * Cost model: work happens only on accepted spell hits (one map lookup, plus a small radius search for the area
 * reactions). No per-tick task: marks expire lazily and a sweep runs every 30 s. Icons are drawn by the MagicCodex
 * client from a 6-8 byte packet; the server spawns no particles or display entities.
 */
final class Reactions implements Listener, AutoCloseable {
    private final MagicMythicPlugin plugin;
    private final ReactionBook book = new ReactionBook(60, 60);
    /** Target -> reward that applies if it dies before `until` (EMBER_CURSE: exp x2, ECLIPSE: drops once more). */
    private final Map<UUID, Reward> rewards = new HashMap<>();
    private record Reward(boolean doubleExp, boolean extraDrops, long until) {
        Reward merge(Reward o) { return new Reward(doubleExp || o.doubleExp, extraDrops || o.extraDrops, Math.max(until, o.until)); }
    }
    private final BukkitTask sweeper;
    private boolean enabled = true, applying, extraDrops = true;
    private double sendRange = 48;

    Reactions(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ElementProtocol.CHANNEL);
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 600, 600);
    }

    void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("reactions.enabled", true);
        book.configure(c.getInt("reactions.mark-ticks", 60), c.getInt("reactions.cooldown-ticks", 60));
        extraDrops = c.getBoolean("reactions.eclipse-extra-drops", true);
        sendRange = Math.clamp(c.getDouble("reactions.icon-range", 48), 8, 128);
        if (!enabled) clearAll();
    }

    /** Entry point, after the spell's damage to `target` was accepted. */
    void hit(LivingEntity caster, LivingEntity target, String elementId) {
        if (!enabled || applying || caster == null || target == null || !target.isValid() || target.isDead()) return;
        Element element = Element.of(elementId);
        if (element == null) return;
        long now = Bukkit.getCurrentTick();
        switch (book.hit(target.getUniqueId(), caster.getUniqueId(), element, now)) {
            case ReactionBook.Marked m -> send(target, new ElementProtocol.Mark(target.getEntityId(), m.element(), m.ticks()));
            case ReactionBook.Reacted r -> {
                send(target, new ElementProtocol.Clear(target.getEntityId()));
                send(target, new ElementProtocol.Pop(target.getEntityId(), r.reaction()));
                applying = true;
                try { apply(r.reaction(), caster, target, now); }
                catch (RuntimeException e) { plugin.getLogger().warning("원소 반응 오류: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
                finally { applying = false; }
            }
        }
    }

    private void apply(Reaction reaction, LivingEntity caster, LivingEntity target, long now) {
        var targets = plugin.targets();
        boolean boss = targets.boss(target);
        Location at = target.getLocation();
        switch (reaction) {
            // 증기 폭발: 대상 주변 2블록 적을 바깥으로 살짝 밀어냄
            case STEAM -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 2, 8)) if (!targets.boss(t)) targets.knock(t, at, caster.getLocation().getDirection(), 0.5, 0.15);
            }
            // 불꽃 회오리: 시전자 3초 신속 I
            case WILDFIRE -> caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));
            // 용암화: 대상 3초 불붙음 (유일하게 피해가 생기는 반응, 바닐라 화염 피해)
            case MAGMA -> target.setFireTicks(Math.max(target.getFireTicks(), 60));
            // 태양 플레어: 대상 5초 발광 (벽 너머로도 보임)
            case FLARE -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            // 잿불 낙인: 8초 안에 처치되면 경험치 2배
            case EMBER_CURSE -> reward(target, new Reward(true, false, now + 160));
            // 감전: 1초 경직
            case SHOCK -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 9)); }
            // 진흙: 3초 구속 I
            case MUD -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0)); }
            // 무지개: 시전자와 주변 4블록 아군 체력 반 칸 회복
            case RAINBOW -> {
                for (Player ally : targets.alliesNear(caster, caster.getLocation(), 4, 8))
                    ally.setHealth(Math.min(Targets.maxHealth(ally), ally.getHealth() + 1));
            }
            // 심연: 주변 3블록 적을 대상 쪽으로 끌어당김
            case ABYSS -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 3, 8)) {
                    if (t.equals(target) || targets.boss(t)) continue;
                    Vector v = at.toVector().subtract(t.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 0.36) t.setVelocity(v.normalize().multiply(0.35).setY(0.05));
                }
            }
            // 모래바람: 대상 3초 나약함 I (근접 공격력 감소)
            case SANDSTORM -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            // 섬광: 주변 6블록 몬스터가 노리던 플레이어를 잠시 놓침
            case FLASH -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 6, 12))
                    if (t instanceof Mob mob && !targets.boss(t) && mob.getTarget() instanceof Player) mob.setTarget(null);
            }
            // 공허 돌풍: 대상 약한 넉백
            case VOID_GUST -> targets.knock(target, caster.getLocation(), caster.getLocation().getDirection(), 0.5, 0.15);
            // 수정 방패: 시전자 5초 흡수 하트 2칸
            case CRYSTAL -> caster.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 0));
            // 발 묶기: 0.5초 이동 불가
            case BIND -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 9)); }
            // 일식: 8초 안에 처치되면 일반 드롭 한 번 더 (보스·플레이어 제외)
            case ECLIPSE -> { if (!boss && !(target instanceof Player)) reward(target, new Reward(false, extraDrops, now + 160)); }
        }
    }

    private void reward(LivingEntity target, Reward r) { rewards.merge(target.getUniqueId(), r, Reward::merge); }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        book.forget(id);
        if (rewards.isEmpty()) return;
        Reward r = rewards.remove(id);
        if (r == null || r.until() <= Bukkit.getCurrentTick() || event.getEntity() instanceof Player) return;
        if (r.doubleExp()) event.setDroppedExp(event.getDroppedExp() * 2);
        if (r.extraDrops() && !plugin.targets().boss(event.getEntity())) {
            var copy = new ArrayList<ItemStack>();
            for (ItemStack s : event.getDrops()) if (s != null && !s.getType().isAir()) copy.add(s.clone());
            event.getDrops().addAll(copy);
        }
    }

    // ---------------------------------------------------------------- icons

    private void send(LivingEntity target, ElementProtocol.Frame frame) {
        byte[] bytes;
        try { bytes = ElementProtocol.encode(frame); } catch (IllegalArgumentException e) { return; }
        Location at = target.getLocation();
        double r2 = sendRange * sendRange;
        for (Player p : target.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) continue;
            if (!p.getListeningPluginChannels().contains(ElementProtocol.CHANNEL)) continue;
            p.sendPluginMessage(plugin, ElementProtocol.CHANNEL, bytes);
        }
    }

    // ---------------------------------------------------------------- housekeeping

    private void sweep() {
        long now = Bukkit.getCurrentTick();
        book.sweep(now);
        rewards.values().removeIf(r -> r.until() <= now);
    }

    private void clearAll() { book.clear(); rewards.clear(); }

    String summary() { return "원소 표식 " + book.size() + ", 처치 보상 대기 " + rewards.size() + (enabled ? "" : " (꺼짐)"); }

    @Override public void close() {
        sweeper.cancel();
        clearAll();
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ElementProtocol.CHANNEL);
    }
}
