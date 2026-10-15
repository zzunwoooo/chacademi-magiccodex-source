package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/**
 * chabolt - invisible travelling hit (the look comes from PortableVFX).
 *  speed=1.5 (blocks/tick) range=24 radius=0.6 pierce=1 falloff=1 (damage x per extra pierced target)
 *  mult/flat knock up ignite effect shield  aoe=0 aoemult=1 (burst radius where it stops)
 *  homing=false  returns=false (comes back to caster, hitting again)  bounce=0 bouncer=6 (jump to next enemy)
 *  breaks=CHAIN,... (blocks it breaks on contact)  from=eye|caster  spread=0 (degrees, sideways offset)
 *  combo=key combocount=3 combobuff=SPEED:60:2 (buff caster when the same target is hit N times within 3s)
 *  빗자루 위에서 쏘면 CarrierShot: 출발점을 지연만큼 앞당기고, 쏘는 방향의 비행 속도만큼 빨라진다.
 */
final class BoltMech extends Mech {
    BoltMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        Location start = p.s("from", "eye").equalsIgnoreCase("caster") ? c.caster().getLocation().add(0, 1, 0) : c.caster().getEyeLocation();
        Vector lead = CarrierShot.lead(c.caster());
        Location aimFrom = lead == null ? start : start.clone().add(lead); // 조준은 내 화면 위치 기준
        Location aim = c.target() instanceof LivingEntity le ? le.getEyeLocation().subtract(0, 0.3, 0) : c.at();
        Vector dir = aim.toVector().subtract(aimFrom.toVector());
        if (dir.lengthSquared() < 1e-4) dir = start.getDirection();
        dir.normalize();
        double spread = p.d("spread", 0);
        if (spread != 0) dir.rotateAroundY(Math.toRadians(spread));
        var bolt = new Bolt(c, start, dir, CarrierShot.extra(c.caster(), dir), lead);
        return plugin.ticker().add(bolt);
    }

    private final class Bolt implements Ticker.Tick {
        final Ctx c;
        final Location pos;
        Vector dir;
        final double speed, range = Math.clamp(p.d("range", 24), 1, 64),
            radius = Math.clamp(p.d("radius", 0.6), 0.1, 4);
        final int pierce = Math.max(1, p.i("pierce", 1));
        final boolean homing = p.b("homing", false), returns = p.b("returns", false);
        int bouncesLeft = Math.max(0, p.i("bounce", 0));
        final boolean bouncing = bouncesLeft > 0;
        final Set<String> breaks = new HashSet<>();
        Set<UUID> hit = new HashSet<>();
        Entity homeTarget;
        double travelled = 0;
        int hitCount = 0;
        boolean coming = false;
        int life = 0;

        /** 빗자루 지연 보정: 첫 틱에 이 구간을 판정하며 먼저 지나간다 (null = 없음). */
        Vector leadLeft;

        Bolt(Ctx c, Location start, Vector dir, double carrierSpeed, Vector lead) {
            this.c = c; this.pos = start.clone(); this.dir = dir; this.leadLeft = lead;
            this.speed = Math.min(Math.clamp(p.d("speed", 1.5), 0.1, 6) + carrierSpeed, 8);
            this.homeTarget = c.target();
            for (String m : p.s("breaks", "").split(",")) if (!m.isBlank()) breaks.add(m.trim().toUpperCase(Locale.ROOT));
        }
        @Override public String kind() { return "bolt"; }

        @Override public boolean tick() {
            if (!plugin.protection().pair(c.caster(),pos))return false;
            if (++life > 400 || !c.caster().isValid() || pos.getWorld() == null) return false;
            if (coming) {
                Vector back = c.caster().getEyeLocation().toVector().subtract(pos.toVector());
                if (back.length() < 1.2) return false;
                dir = back.normalize();
            } else if ((homing || bouncing && hitCount > 0) && homeTarget instanceof LivingEntity && homeTarget.isValid()) {
                Vector to = ((LivingEntity) homeTarget).getEyeLocation().toVector().subtract(pos.toVector());
                if (to.lengthSquared() > 1e-4) dir = homing ? dir.multiply(0.55).add(to.normalize().multiply(0.45)).normalize() : to.normalize();
            }
            if (leadLeft != null) {
                Vector lead = leadLeft;
                leadLeft = null;
                double len = lead.length();
                if (!advance(lead.multiply(1 / len), len, false)) return false;
            }
            return advance(dir, speed, true);
        }

        /** along 방향으로 distance 만큼 0.4블록씩 나아가며 판정. false = 투사체 끝. */
        private boolean advance(Vector along, double distance, boolean counts) {
            double step = 0.4;
            for (double moved = 0; moved < distance; moved += step) {
                pos.add(along.clone().multiply(step));
                if (counts) travelled += step;
                if(!plugin.protection().pair(c.caster(),pos))return false;
                if (!pos.getWorld().isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) return false;
                Block b = pos.getBlock();
                if (!b.isPassable()) {
                    if (breaks.contains(b.getType().name()) && c.caster() instanceof Player pl && Targets.canBreak(pl, b)) {
                        b.breakNaturally();
                    } else if (!turnBack()) { burst(); return false; } else continue;
                }
                if (!MagicImpactEvent.fire(c.caster(), p.s("element", ""), pos, radius, MagicImpactEvent.Kind.BOLT)) { burst(); return false; }
                for (Entity e : pos.getWorld().getNearbyEntities(pos, radius + 1, radius + 1, radius + 1)) {
                    if (hit.contains(e.getUniqueId()) || !plugin.targets().enemy(c.caster(), e)) continue;
                    if (Targets.closestDistance(e, pos) > radius) continue;
                    strike((LivingEntity) e);
                    if (bouncesLeft > 0) {
                        bouncesLeft--;
                        LivingEntity next = nextBounce((LivingEntity) e);
                        if (next == null) { burst(); return false; }
                        homeTarget = next; travelled = 0;
                        break;
                    } else if (hitCount >= pierce && !returns) { burst(); return false; }
                }
                if (counts && travelled >= range && !turnBack()) { burst(); return false; }
                if (coming && travelled >= range * 2 + 4) return false;
            }
            return true;
        }

        private boolean turnBack() {
            if (!returns || coming) return false;
            coming = true; travelled = range; hit = new HashSet<>(); // second pass may hit the same enemies again
            return true;
        }

        private LivingEntity nextBounce(LivingEntity from) {
            for (LivingEntity t : plugin.targets().enemiesNear(c.caster(), from.getLocation(), p.d("bouncer", 6), 16))
                if (!hit.contains(t.getUniqueId())) return t;
            return null;
        }

        private void strike(LivingEntity t) {
            hit.add(t.getUniqueId());
            double dmg = c.dmg(p) * Geometry.falloff(p.d("falloff", 1), hitCount);
            hitCount++;
            var targets = plugin.targets();
            if(!targets.hurt(c.caster(), t, dmg))return;
            plugin.reactions().hit(c.caster(), t, p.s("element", ""), dmg);
            targets.effects(t, p.effects("effect"));
            int ignite = p.i("ignite", 0);
            if (ignite > 0) t.setFireTicks(Math.max(t.getFireTicks(), ignite));
            int shield = p.i("shield", 0);
            if (shield > 0 && t instanceof Player pl && pl.isBlocking()) pl.setCooldown(Material.SHIELD, shield);
            Location from = pos.clone().subtract(dir.clone().multiply(2));
            targets.knock(t, from, dir, p.d("knock", 0), p.d("up", 0));
            String combo = p.s("combo", "");
            if (!combo.isBlank() && plugin.worldState().combo(c.caster(), combo, t, p.i("combocount", 3)))
                targets.effects(c.caster(), p.effects("combobuff"));
        }

        private void burst() {
            double aoe = p.d("aoe", 0);
            if (aoe <= 0) return;
            double dmg = c.damage() * p.d("aoemult", 1);
            for (LivingEntity t : plugin.targets().enemiesNear(c.caster(), pos, aoe, p.i("max", 16))) {
                if(!plugin.targets().hurt(c.caster(), t, dmg))continue;
                plugin.reactions().hit(c.caster(), t, p.s("element", ""), dmg);
                plugin.targets().effects(t, p.effects("effect"));
                plugin.targets().knock(t, pos, dir, p.d("aoeknock", 0), p.d("aoeup", 0));
            }
        }
    }
}
