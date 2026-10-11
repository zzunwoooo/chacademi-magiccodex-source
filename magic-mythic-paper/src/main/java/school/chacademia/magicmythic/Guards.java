package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Personal guards on the caster.
 *  lethal  - the next fatal hit is cancelled once (phoenix_oath)
 *  reflect - nearby hostile projectiles flying at the caster are turned back (returning_wind)
 *  block   - hostile projectiles entering a wall in front of the caster vanish (ripple_guard)
 *  deflect - the next melee hit is reduced and the attacker is pushed aside (waterwheel_shield)
 */
final class Guards implements Listener {
    enum Type { LETHAL, REFLECT, BLOCK, DEFLECT }
    record Guard(Type type, long until, double r, double reduce, double heal, double push) {}

    private final MagicMythicPlugin plugin;
    private final Map<UUID, EnumMap<Type, Guard>> guards = new HashMap<>();
    private long now = 0;

    Guards(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    void add(LivingEntity owner, Guard g) {
        guards.computeIfAbsent(owner.getUniqueId(), k -> new EnumMap<>(Type.class)).put(g.type(), g);
    }
    long now() { return now; }
    int count() { return guards.values().stream().mapToInt(Map::size).sum(); }

    private Guard active(Entity e, Type type) {
        if(!plugin.protection().at(e.getLocation()))return null;
        var m = guards.get(e.getUniqueId());
        if (m == null) return null;
        Guard g = m.get(type);
        if (g == null) return null;
        if (g.until() < now) { m.remove(type); return null; }
        return g;
    }
    private void consume(Entity e, Type type) {
        var m = guards.get(e.getUniqueId());
        if (m != null) { m.remove(type); if (m.isEmpty()) guards.remove(e.getUniqueId()); }
    }

    private void tick() {
        now++;
        if (guards.isEmpty()) return;
        var it = guards.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            entry.getValue().values().removeIf(g -> g.until() < now);
            if (entry.getValue().isEmpty()) { it.remove(); continue; }
            Entity owner = Bukkit.getEntity(entry.getKey());
            if (!(owner instanceof LivingEntity le) || !le.isValid()) { it.remove(); continue; }
            if(!plugin.protection().at(le.getLocation())){it.remove();continue;}
            Guard reflect = entry.getValue().get(Type.REFLECT), block = entry.getValue().get(Type.BLOCK);
            if (reflect != null) reflect(le, reflect);
            if (block != null) block(le, block);
        }
    }

    private boolean hostileShot(LivingEntity owner, Projectile pr) {
        if(!plugin.protection().pair(owner,pr.getLocation()))return false;
        ProjectileSource src = pr.getShooter();
        if (!(src instanceof Entity shooter)) return true;            // dispensers etc.
        if (shooter.getUniqueId().equals(owner.getUniqueId())) return false;
        if (shooter instanceof Player) return plugin.targets().enemy(owner, shooter); // allies' shots pass
        return true;
    }

    private void reflect(LivingEntity owner, Guard g) {
        Location c = owner.getLocation().add(0, 1, 0);
        for (Entity e : owner.getWorld().getNearbyEntities(c, g.r(), g.r(), g.r())) {
            if (!(e instanceof Projectile pr) || !hostileShot(owner, pr)) continue;
            Vector v = pr.getVelocity();
            Vector toOwner = c.toVector().subtract(pr.getLocation().toVector());
            if (v.lengthSquared() < 1e-3 || v.dot(toOwner) <= 0) continue;
            pr.setVelocity(v.multiply(-1.1));
            pr.setShooter(owner instanceof ProjectileSource ps ? ps : null);
        }
    }

    private void block(LivingEntity owner, Guard g) {
        Location base = owner.getLocation();
        Vector facing = base.getDirection();
        Vector wallCenter = base.toVector().add(facing.clone().setY(0).normalize().multiply(1.5));
        for (Entity e : owner.getWorld().getNearbyEntities(base, 4, 4, 4)) {
            if (!(e instanceof Projectile pr) || !hostileShot(owner, pr)) continue;
            if (Geometry.inWall(wallCenter, facing, pr.getLocation().toVector(), 4, 2, 3)) pr.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void lethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        Guard g = active(le, Type.LETHAL);
        if (g == null || e.getFinalDamage() < le.getHealth()) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        e.setCancelled(true);
        consume(le, Type.LETHAL);
        le.setHealth(Math.min(Targets.maxHealth(le), Math.max(1, g.heal())));
        le.setFireTicks(0);
        le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void deflect(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof LivingEntity attacker)) return;
        if(!plugin.targets().enemy(le,attacker)||!plugin.protection().harm(le,attacker))return;
        Guard g = active(le, Type.DEFLECT);
        if (g == null) return;
        consume(le, Type.DEFLECT);
        e.setDamage(e.getDamage() * (1 - Math.clamp(g.reduce(), 0, 1)));
        Vector away = attacker.getLocation().toVector().subtract(le.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = le.getLocation().getDirection().setY(0);
        Vector side = new Vector(-away.getZ(), 0, away.getX()).normalize();
        attacker.setVelocity(side.multiply(g.push()).add(away.normalize().multiply(g.push() * 0.5)).setY(0.25));
    }

    @EventHandler public void quit(PlayerQuitEvent e) { guards.remove(e.getPlayer().getUniqueId()); }
}
