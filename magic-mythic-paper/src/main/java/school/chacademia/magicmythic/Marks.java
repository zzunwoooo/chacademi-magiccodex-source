package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Marks that wait for something to happen.
 *  ash_brand - bursts after `ticks`, the burst brands the enemies it hits (each enemy rebrandable after `cooldown`)
 *  wedge     - bursts when the marked enemy takes a melee hit from a player, or never (no timed burst)
 *  prison    - normal mobs stop acting until `ticks` end or they take enough damage; bosses are only slowed.
 *              The mob's own awareness is saved on the mob (PDC) and put back on release, on plugin stop and,
 *              after a crash, when the mob loads again.
 *  swords    - on the caster: each melee hit on an enemy spends one sword for extra damage
 *  root      - movement only is held for `ticks` (attacks and skills still work); bosses are immune (material_exchange)
 *  stun      - all actions stop for `ticks`: mobs lose AI (same saved-awareness rule as prison), players are slowed,
 *              weakened and cannot dig; bosses are only slowed (counterweight)
 */
final class Marks implements Listener {
    enum Type { ASH_BRAND, WEDGE, PRISON, SWORDS, ROOT, STUN }

    final class Mark {
        final Type type; final LivingEntity caster; final LivingEntity holder; final double damage; final Params p;
        final long until; int charges; double taken = 0; boolean done = false;
        Mark(Type type, LivingEntity caster, LivingEntity holder, double damage, Params p, long ticks) {
            this.type = type; this.caster = caster; this.holder = holder; this.damage = damage; this.p = p;
            this.until = now + ticks; this.charges = Math.max(1, p.i("count", 3));
        }
    }

    private final MagicMythicPlugin plugin;
    private final NamespacedKey prisonKey;
    private final List<Mark> marks = new ArrayList<>();
    private final Map<UUID, Long> brandCooldown = new HashMap<>();
    private long now = 0;
    private boolean applyingSword = false;

    Marks(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        this.prisonKey = new NamespacedKey(plugin, "prison_was_aware");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        // Mobs left frozen by a crash in already-loaded chunks.
        for (var w : Bukkit.getWorlds()) for (var e : w.getEntitiesByClass(Mob.class)) recover(e);
    }
    int count() { return marks.size(); }

    boolean add(Type type, LivingEntity caster, LivingEntity holder, double damage, Params p) {
        if(plugin.targets().excluded(holder)||!plugin.protection().pair(caster,holder.getLocation()))return false;
        if (marks.size() >= plugin.getConfig().getInt("limits.marks", 512)) return false;
        if(type!=Type.SWORDS&&!plugin.targets().hurt(caster,holder,0))return false;
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
                Mob mob = (Mob) holder;
                // Remember the mob's own awareness (some mobs are intentionally unaware) before freezing it.
                freezeMob(mob, prisonKey);
            }
            case SWORDS -> marks.removeIf(m -> m.type == Type.SWORDS && m.holder.equals(holder));
            case ROOT -> {
                if (plugin.targets().boss(holder)) return false;
                marks.removeIf(m -> m.type == Type.ROOT && m.holder.equals(holder));
                holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, 9, false, false, true));
            }
            case STUN -> {
                if (marks.stream().anyMatch(m -> (m.type == Type.STUN || m.type == Type.PRISON) && m.holder.equals(holder) && !m.done)) return false;
                if (plugin.targets().boss(holder)) { holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, 2)); return true; }
                if (holder instanceof Mob mob) freezeMob(mob, prisonKey);
                else {
                    holder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, 9, false, false, true));
                    holder.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) ticks, 9, false, false, true));
                    holder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, (int) ticks, 9, false, false, true));
                }
            }
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
            var traceBefore = SpellTrace.set(SpellTrace.of(m.p));
            try {
            boolean gone = !m.holder.isValid() || m.holder.isDead() || !plugin.protection().pair(m.caster,m.holder.getLocation());
            if (m.type == Type.ASH_BRAND && !gone && now >= m.until) { burstBrand(m); m.done = true; }
            else if (gone || now >= m.until) m.done = true;
            if (m.done && (m.type == Type.PRISON || m.type == Type.STUN)) release(m);
            if (!m.done && m.type == Type.ROOT) {
                if (m.holder instanceof Mob mob) mob.getPathfinder().stopPathfinding();
                var v = m.holder.getVelocity();
                m.holder.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, v.getY()), 0));
            }
            } finally { SpellTrace.set(traceBefore); }
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
            if(!targets.hurt(m.caster, t, m.damage))continue;
            targets.knock(t, m.holder.getLocation(), m.caster.getLocation().getDirection(), m.p.d("knock", 0.6), m.p.d("up", 0.3));
        }
    }

    private void release(Mark m) {
        if (!(m.holder instanceof Mob mob)) return;
        boolean other = marks.stream().anyMatch(x -> x != m && !x.done && (x.type == Type.PRISON || x.type == Type.STUN) && x.holder.equals(mob));
        if (!other) recover(mob);
    }

    /** Put back the awareness saved when the prison started, and forget it. */
    private void recover(Mob mob) { recoverMob(mob, prisonKey); }
    static void freezeMob(Mob mob, NamespacedKey prisonKey) {
        if (!mob.getPersistentDataContainer().has(prisonKey, PersistentDataType.BYTE))
            mob.getPersistentDataContainer().set(prisonKey, PersistentDataType.BYTE, (byte)(mob.isAware()?1:0));
        mob.setAware(false);
    }
    static void recoverMob(Mob mob, NamespacedKey prisonKey) {
        var pdc = mob.getPersistentDataContainer();
        Byte was = pdc.get(prisonKey, PersistentDataType.BYTE);
        if (was == null) return;
        mob.setAware(was != 0);
        pdc.remove(prisonKey);
    }

    @EventHandler public void entitiesLoad(EntitiesLoadEvent e) {
        for (var en : e.getEntities()) {
            if (!(en instanceof Mob mob)) continue;
            boolean stillHeld = marks.stream().anyMatch(m -> (m.type == Type.PRISON || m.type == Type.STUN) && !m.done && m.holder.getUniqueId().equals(mob.getUniqueId()));
            if (!stillHeld) recover(mob); // frozen by a crash or by a prison that ended while unloaded
        }
    }

    void releaseAll() { for (Mark m : marks) if (m.type == Type.PRISON || m.type == Type.STUN) release(m); marks.clear(); }

    /** counterweight: stop the attacker for `ticks` (no damage dealt). */
    boolean stun(LivingEntity caster, LivingEntity target, int ticks) {
        var p = new Params(Map.of("ticks", String.valueOf(ticks)));
        return add(Type.STUN, caster, target, 0, p);
    }

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
