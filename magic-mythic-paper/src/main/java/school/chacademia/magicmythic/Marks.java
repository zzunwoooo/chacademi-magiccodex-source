package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Marks that wait for something to happen.
 *  ash_brand - bursts after `ticks`, the burst brands the enemies it hits (each enemy rebrandable after `cooldown`)
 *  wedge     - bursts when the marked enemy takes a melee hit from a player, or never (no timed burst)
 *  prison    - normal mobs stop acting until `ticks` end or they take enough damage; bosses are only slowed
 *  swords    - on the caster: each melee hit on an enemy spends one sword for extra damage
 */
final class Marks implements Listener {
    enum Type { ASH_BRAND, WEDGE, PRISON, SWORDS }

    final class Mark {
        final Type type; final LivingEntity caster; final LivingEntity holder; final double damage; final Params p;
        final long until; int charges; double taken = 0; boolean done = false;
        Mark(Type type, LivingEntity caster, LivingEntity holder, double damage, Params p, long ticks) {
            this.type = type; this.caster = caster; this.holder = holder; this.damage = damage; this.p = p;
            this.until = now + ticks; this.charges = Math.max(1, p.i("count", 3));
        }
    }

    private final MagicMythicPlugin plugin;
    private final List<Mark> marks = new ArrayList<>();
    private final Map<UUID, Long> brandCooldown = new HashMap<>();
    private long now = 0;
    private boolean applyingSword = false;

    Marks(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    int count() { return marks.size(); }

    boolean add(Type type, LivingEntity caster, LivingEntity holder, double damage, Params p) {
        if (marks.size() >= plugin.getConfig().getInt("limits.marks", 512)) return false;
        long ticks = Math.clamp(p.i("ticks", 60), 1, 20 * 600);
        switch (type) {
            case ASH_BRAND -> {
                if (brandCooldown.getOrDefault(holder.getUniqueId(), -1L) > now) return false;
                if (marks.stream().anyMatch(m -> m.type == Type.ASH_BRAND && m.holder.equals(holder) && !m.done)) return false;
                brandCooldown.put(holder.getUniqueId(), now + Math.max(0, p.i("cooldown", 200)));
            }
            case PRISON -> {
                if (marks.stream().anyMatch(m -> m.type == Type.PRISON && m.holder.equals(holder) && !m.done)) return false;
                if (plugin.targets().boss(holder) || !(holder instanceof Mob)) {
                    holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, 2));
                    return true;
                }
                ((Mob) holder).setAware(false);
            }
            case SWORDS -> marks.removeIf(m -> m.type == Type.SWORDS && m.holder.equals(holder));
            default -> {}
        }
        marks.add(new Mark(type, caster, holder, damage, p, ticks));
        return true;
    }

    private void tick() {
        now++;
        if ((now & 255) == 0) brandCooldown.values().removeIf(t -> t < now);
        if (marks.isEmpty()) return;
        for (Mark m : new ArrayList<>(marks)) {
            if (m.done) continue;
            boolean gone = !m.holder.isValid() || m.holder.isDead();
            if (m.type == Type.ASH_BRAND && !gone && now >= m.until) { burstBrand(m); m.done = true; }
            else if (gone || now >= m.until) m.done = true;
            if (m.done && m.type == Type.PRISON) release(m);
        }
        marks.removeIf(m -> m.done);
    }

    private void burstBrand(Mark m) {
        var targets = plugin.targets();
        var hit = targets.enemiesNear(m.caster, m.holder.getLocation(), m.p.d("r", 3), m.p.i("max", 12));
        for (LivingEntity t : hit) targets.hurt(m.caster, t, m.damage);
        if (!m.caster.isValid()) return;
        for (LivingEntity t : hit) if (!t.equals(m.holder) && t.isValid() && !t.isDead()) add(Type.ASH_BRAND, m.caster, t, m.damage, m.p);
    }

    private void burstWedge(Mark m) {
        var targets = plugin.targets();
        for (LivingEntity t : targets.enemiesNear(m.caster, m.holder.getLocation(), m.p.d("r", 2.5), m.p.i("max", 12))) {
            targets.hurt(m.caster, t, m.damage);
            targets.knock(t, m.holder.getLocation(), m.caster.getLocation().getDirection(), m.p.d("knock", 0.6), m.p.d("up", 0.3));
        }
    }

    private void release(Mark m) { if (m.holder instanceof Mob mob && mob.isValid()) mob.setAware(true); }

    void releaseAll() { for (Mark m : marks) if (m.type == Type.PRISON) release(m); marks.clear(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageEvent e) {
        if (marks.isEmpty() || !(e.getEntity() instanceof LivingEntity victim)) return;
        boolean melee = e instanceof EntityDamageByEntityEvent by && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            && by.getDamager() instanceof Player;
        for (Mark m : new ArrayList<>(marks)) {
            if (m.done || !m.holder.equals(victim)) continue;
            if (m.type == Type.PRISON) {
                m.taken += e.getFinalDamage();
                if (m.taken >= Targets.maxHealth(victim) * plugin.getConfig().getDouble("prison-break-damage-ratio", 0.2)) {
                    m.done = true; release(m);
                }
            } else if (m.type == Type.WEDGE && melee) {
                m.done = true;
                Bukkit.getScheduler().runTask(plugin, () -> burstWedge(m));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swordHit(EntityDamageByEntityEvent e) {
        if (applyingSword || marks.isEmpty() || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof LivingEntity attacker) || !(e.getEntity() instanceof LivingEntity victim)) return;
        for (Mark m : marks) {
            if (m.done || m.type != Type.SWORDS || !m.holder.equals(attacker) || !plugin.targets().enemy(attacker, victim)) continue;
            if (--m.charges <= 0) m.done = true;
            double dmg = m.damage;
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyingSword = true;
                try { plugin.targets().hurt(attacker, victim, dmg); } finally { applyingSword = false; }
            });
            return;
        }
    }
}
