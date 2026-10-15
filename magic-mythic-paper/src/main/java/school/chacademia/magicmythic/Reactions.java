package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
 * Owner rules (2026-10-14): every reaction acts on enemies (only 무지개 helps allies), one simple effect each,
 * crowd control at most 1.5 s, a few small flashy damage reactions are fine (연쇄 번개 etc.), nothing lingers.
 * Bosses never receive crowd control / aggro drop; damage, glow and rewards still apply.
 *
 * Cost model: work happens only on accepted spell hits (one map lookup, plus a small radius search for the area
 * reactions). No per-tick task: marks expire lazily and a sweep runs every 30 s. Two reactions use one delayed
 * task each (공허 균열 1 s, 일식 0.5 s; at most one per target per 3 s because of the reaction cooldown).
 * Icons are drawn by the MagicCodex client from a 6-8 byte packet; the server spawns no particles or entities.
 * Reaction VFX (2026-10-15): PortableVFX pack "ElementReactions", one short impact system per reaction (0.15-0.5 s,
 * 공허 균열 1 s), played through {@link ReactionVfx}. Visual only.
 */
final class Reactions implements Listener, AutoCloseable {
    private final MagicMythicPlugin plugin;
    private final ReactionBook book = new ReactionBook(60, 60);
    /** EMBER_CURSE: target -> tick until which its death gives double exp. */
    private final Map<UUID, Long> doubleExp = new HashMap<>();
    /** CRYSTAL: mobs whose AI is paused, restored by a task or on shutdown. */
    private final Set<Mob> frozen = new HashSet<>();
    private final BukkitTask sweeper;
    private final ReactionVfx vfx;
    private boolean enabled = true, applying;
    private double floor = 2, scale = 1, sendRange = 48;

    Reactions(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        this.vfx = new ReactionVfx(plugin);
        reload();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ElementProtocol.CHANNEL);
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 600, 600);
    }

    void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("reactions.enabled", true);
        book.configure(c.getInt("reactions.mark-ticks", 60), c.getInt("reactions.cooldown-ticks", 60));
        floor = Math.max(0, c.getDouble("reactions.damage-floor", 2));
        scale = Math.clamp(c.getDouble("reactions.damage-scale", 1), 0, 5);
        sendRange = Math.clamp(c.getDouble("reactions.icon-range", 48), 8, 128);
        vfx.reload();
        if (!enabled) clearAll();
    }

    /** Entry point, after the spell's damage to `target` was accepted. `damage` = what the spell dealt (pre-armour). */
    void hit(LivingEntity caster, LivingEntity target, String elementId, double damage) {
        if (!enabled || applying || caster == null || target == null || !target.isValid() || target.isDead()) return;
        Element element = Element.of(elementId);
        if (element == null) return;
        long now = Bukkit.getCurrentTick();
        switch (book.hit(target.getUniqueId(), caster.getUniqueId(), element, now)) {
            case ReactionBook.Immune ignored -> {}
            case ReactionBook.Marked m -> send(target, new ElementProtocol.Mark(target.getEntityId(), m.element(), m.ticks()));
            case ReactionBook.Reacted r -> {
                send(target, new ElementProtocol.Clear(target.getEntityId()));
                send(target, new ElementProtocol.Pop(target.getEntityId(), r.reaction()));
                applying = true;
                try { apply(r.reaction(), caster, target, Math.max(damage, floor) * scale, now); }
                catch (RuntimeException e) { plugin.getLogger().warning("원소 반응 오류: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
                finally { applying = false; }
            }
        }
    }

    private void apply(Reaction reaction, LivingEntity caster, LivingEntity target, double ref, long now) {
        var targets = plugin.targets();
        boolean boss = targets.boss(target);
        Location at = target.getLocation();
        switch (reaction) {
            case MAGMA, FLARE, EMBER_CURSE, MUD, ABYSS, FLASH, CRYSTAL, BIND, WILDFIRE -> vfx.body(reaction, target);
            case STEAM, SANDSTORM, ECLIPSE -> vfx.area(reaction, at, 30);
            case RAINBOW -> vfx.area(reaction, caster.getLocation(), 30);
            case VOID_GUST -> { if (!boss) vfx.body(reaction, at, target.getHeight()); }
            case SHOCK -> {} // per jump below
        }
        switch (reaction) {
            // 증기 폭발: 주변 2블록 작은 피해 + 살짝 튀어 오름
            case STEAM -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 2, 8)) {
                    if (!targets.hurt(caster, t, ref * 0.15)) continue;
                    if (!targets.boss(t)) t.setVelocity(t.getVelocity().setY(0.35));
                }
            }
            // 불씨 확산: 근처 적 최대 2마리에게 화염 표식이 번짐 (표식 없는 대상만)
            case WILDFIRE -> {
                int left = 2;
                for (LivingEntity t : targets.enemiesNear(caster, at, 4, 8)) {
                    if (t.equals(target) || !book.spread(t.getUniqueId(), caster.getUniqueId(), Element.FIRE, now)) continue;
                    send(t, new ElementProtocol.Mark(t.getEntityId(), Element.FIRE, book.markTicks()));
                    vfx.body(Reaction.WILDFIRE, t);
                    if (--left == 0) break;
                }
            }
            // 용암 분출: 2초 불붙음
            case MAGMA -> target.setFireTicks(Math.max(target.getFireTicks(), 40));
            // 태양 플레어: 4초 발광 (벽 너머로도 보임)
            case FLARE -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
            // 잿불 낙인: 8초 안에 처치되면 경험치 2배
            case EMBER_CURSE -> doubleExp.put(target.getUniqueId(), now + 160);
            // 연쇄 번개: 대상에서 가까운 적으로 최대 3번 튐, 튈 때마다 30% 감소
            case SHOCK -> {
                var done = new HashSet<UUID>(List.of(target.getUniqueId()));
                LivingEntity from = target;
                double amount = ref * 0.15;
                for (int i = 0; i < 3; i++) {
                    LivingEntity next = null;
                    for (LivingEntity t : targets.enemiesNear(caster, from.getLocation(), 4, 8))
                        if (!done.contains(t.getUniqueId())) { next = t; break; }
                    if (next == null) break;
                    done.add(next.getUniqueId());
                    targets.hurt(caster, next, amount);
                    // one bolt per jump, 0.15 s apart so the chain reads in order (visual only)
                    LivingEntity a = from, b = next;
                    if (i == 0) vfx.bolt(a, b);
                    else later(3L * i, () -> { if (a.isValid() && b.isValid() && a.getWorld() == b.getWorld()) vfx.bolt(a, b); });
                    amount *= 0.7;
                    from = next;
                }
            }
            // 진흙: 1.5초 크게 느려짐
            case MUD -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 3)); }
            // 무지개: 시전자와 주변 4블록 아군 체력 반 칸 회복 (유일한 아군 효과)
            case RAINBOW -> {
                for (Player ally : targets.alliesNear(caster, caster.getLocation(), 4, 8))
                    ally.setHealth(Math.min(Targets.maxHealth(ally), ally.getHealth() + 1));
            }
            // 심연: 1초 둥실 떠오름
            case ABYSS -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 1)); }
            // 모래 회오리: 주변 3블록 적을 대상 쪽으로 빨아들임
            case SANDSTORM -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 3, 8)) {
                    if (t.equals(target) || targets.boss(t)) continue;
                    Vector v = at.toVector().subtract(t.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 0.36) t.setVelocity(v.normalize().multiply(Math.min(0.55, v.length() * 0.3)).setY(0.08));
                }
            }
            // 섬광: 주변 6블록 몬스터가 노리던 플레이어를 놓침
            case FLASH -> {
                for (LivingEntity t : targets.enemiesNear(caster, at, 6, 12))
                    if (t instanceof Mob mob && !targets.boss(t) && mob.getTarget() instanceof Player) mob.setTarget(null);
            }
            // 공허 균열: 1초 뒤 대상이 반응한 자리로 끌려 돌아옴
            case VOID_GUST -> { if (!boss) later(20, () -> pullBack(target, at.clone())); }
            // 수정화: 0.5초 경직 (이동·공격 불가)
            case CRYSTAL -> { if (!boss) stun(target, 10); }
            // 매장: 1초 이동 불가
            case BIND -> { if (!boss) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 9)); }
            // 일식: 0.5초 뒤 반응 자리에서 터지며 주변 3블록 피해
            case ECLIPSE -> {
                Location center = at.clone();
                double amount = ref * 0.2;
                later(10, () -> {
                    if (!caster.isValid() || !plugin.protection().pair(caster, center)) return;
                    applying = true;
                    try { for (LivingEntity t : targets.enemiesNear(caster, center, 3, 8)) targets.hurt(caster, t, amount); }
                    finally { applying = false; }
                });
            }
        }
    }

    private void pullBack(LivingEntity target, Location origin) {
        if (!target.isValid() || target.isDead() || target.getWorld() != origin.getWorld()) return;
        double d = target.getLocation().distanceSquared(origin);
        if (d < 1 || d > 100 || !plugin.protection().at(origin)) return; // barely moved, or flung far away / into protection
        origin.setYaw(target.getLocation().getYaw());
        origin.setPitch(target.getLocation().getPitch());
        target.teleport(origin);
    }

    private void stun(LivingEntity target, int ticks) {
        if (target instanceof Mob mob) {
            if (!mob.isAware() || !frozen.add(mob)) return;
            mob.setAware(false);
            later(ticks, () -> { if (frozen.remove(mob) && mob.isValid()) mob.setAware(true); });
        } else {
            // Players (PvP areas): no AI to pause, so a hard slow for the same time.
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9));
        }
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!enabled) return;
            try { task.run(); }
            catch (RuntimeException e) { plugin.getLogger().warning("원소 반응 오류: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
        }, ticks);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        book.forget(id);
        if (event.getEntity() instanceof Mob mob) frozen.remove(mob);
        if (doubleExp.isEmpty()) return;
        Long until = doubleExp.remove(id);
        if (until != null && until > Bukkit.getCurrentTick() && !(event.getEntity() instanceof Player))
            event.setDroppedExp(event.getDroppedExp() * 2);
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
        doubleExp.values().removeIf(t -> t <= now);
    }

    private void unfreezeAll() {
        for (Mob mob : frozen) if (mob.isValid()) mob.setAware(true);
        frozen.clear();
    }

    private void clearAll() { book.clear(); doubleExp.clear(); unfreezeAll(); }

    String summary() { return "원소 표식 " + book.size() + ", 경험치 낙인 " + doubleExp.size() + ", 경직 " + frozen.size() + ", " + vfx.summary() + (enabled ? "" : " (꺼짐)"); }

    @Override public void close() {
        sweeper.cancel();
        clearAll();
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ElementProtocol.CHANNEL);
    }
}
