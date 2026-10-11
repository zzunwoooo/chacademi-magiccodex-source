package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/**
 * chazone - lasting area.
 *  center=target|caster follow=false r=3 ticks=100 period=20 max=16
 *  mult/flat (damage per pulse) effect ignite suck (pull toward center per pulse) lift (upward per pulse)
 *  heal=0 (health per pulse to allies, caster included)
 *  wall=0 (width; makes a wall facing the caster, depth = r, height=3)
 *  trap=true trapr=3 trapmult=1 (explode once when an enemy steps within r, then end)
 *  key=name limit=2 (keep at most N zones with this key per caster; oldest removed)
 */
final class ZoneMech extends Mech {
    ZoneMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String key = p.s("key", "");
        int limit = p.i("limit", 0);
        if (!key.isBlank() && limit > 0) {
            var mine = new ArrayList<Zone>();
            for (Zone z : Zone.ACTIVE) if (z.owner.equals(c.caster().getUniqueId()) && z.key.equals(key)) mine.add(z);
            while (mine.size() >= limit) { Zone oldest = mine.removeFirst(); oldest.dead = true; }
        }
        var zone = new Zone(c, center(c).clone(), c.caster().getLocation().getDirection(), key);
        if (!plugin.ticker().add(zone)) return false;
        Zone.ACTIVE.add(zone);
        return true;
    }

    private final class Zone implements Ticker.Tick {
        static final List<Zone> ACTIVE = new ArrayList<>();
        final Ctx c; final Location center; final Vector facing; final UUID owner; final String key;
        final int ticks = Math.clamp(p.i("ticks", 100), 1, 20 * 600), period = Math.max(1, p.i("period", 20));
        final double r = Math.clamp(p.d("r", 3), 0.5, 16), wall = p.d("wall", 0);
        final boolean follow = p.b("follow", false), trap = p.b("trap", false);
        int age = 0; boolean dead = false;

        Zone(Ctx c, Location center, Vector facing, String key) {
            this.c = c; this.center = center; this.facing = facing; this.owner = c.caster().getUniqueId(); this.key = key;
        }
        @Override public String kind() { return "zone"; }
        @Override public void stop() { ACTIVE.remove(this); }

        @Override public boolean tick() {
            if (dead || age++ >= ticks || !c.caster().isValid()) return false;
            if (follow) { var l = c.caster().getLocation(); center.setX(l.getX()); center.setY(l.getY()); center.setZ(l.getZ()); }
            if (trap) return tickTrap();
            if ((age - 1) % period != 0) return true;
            MagicImpactEvent.fire(c.caster(), p.s("element", ""), center, wall > 0 ? Math.max(wall / 2, r) : r, MagicImpactEvent.Kind.ZONE);
            var targets = plugin.targets();
            double heal = p.d("heal", 0);
            if (heal > 0) for (Player ally : targets.alliesNear(c.caster(), center, r, 16)) {
                ally.setHealth(Math.min(Targets.maxHealth(ally), ally.getHealth() + heal));
            }
            double dmg = c.dmg(p), suck = p.d("suck", 0), lift = p.d("lift", 0);
            int ignite = p.i("ignite", 0);
            double search = wall > 0 ? Math.max(wall, r) : r;
            for (LivingEntity t : targets.enemiesNear(c.caster(), center, search + 1, p.i("max", 16))) {
                if (wall > 0 && !Geometry.inWall(center.toVector(), facing, t.getLocation().toVector(), wall, Math.max(1, r), 3)) continue;
                if (wall <= 0 && Targets.closestDistance(t, center) > r) continue;
                targets.hurt(c.caster(), t, dmg);
                targets.effects(t, p.effects("effect"));
                if (ignite > 0) t.setFireTicks(Math.max(t.getFireTicks(), ignite));
                if (suck > 0) {
                    Vector v = center.toVector().subtract(t.getLocation().toVector()).setY(0);
                    if (v.length() > 0.6) t.setVelocity(v.normalize().multiply(Math.min(suck, v.length() * 0.5)).setY(0.05));
                }
                if (lift > 0) t.setVelocity(t.getVelocity().setY(lift));
            }
            return true;
        }

        private boolean tickTrap() {
            var targets = plugin.targets();
            var near = targets.enemiesNear(c.caster(), center, r, 1);
            if (near.isEmpty()) return true;
            double dmg = c.damage() * p.d("trapmult", p.d("mult", 1)) + p.d("flat", 0);
            for (LivingEntity t : targets.enemiesNear(c.caster(), center, p.d("trapr", 3), p.i("max", 16))) {
                targets.hurt(c.caster(), t, dmg);
                targets.effects(t, p.effects("effect"));
                int ignite = p.i("ignite", 0);
                if (ignite > 0) t.setFireTicks(Math.max(t.getFireTicks(), ignite));
                targets.knock(t, center, facing, p.d("knock", 0.6), p.d("up", 0.3));
            }
            return false;
        }
    }
}
