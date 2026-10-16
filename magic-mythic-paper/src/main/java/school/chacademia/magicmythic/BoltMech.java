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
 *  count=1 countmax=count interval=4 (fire a random count of bolts, one every interval ticks, same aim)
 *  rehit=0 (ticks; >0 lets the same enemy be hit again after that many ticks - a rolling boulder)
 *  ally=false (hits allied players instead of enemies and heals them by the damage value; caster never)
 *  proc=false (passive extra hit: cannot trigger passives again)
 *  spell=id (VFX events: external:contact on each hit, external:block_contact where it stops on a block)
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
        int count = Math.max(1, p.i("count", 1)), countMax = Math.max(count, p.i("countmax", count));
        if (countMax > count) count += java.util.concurrent.ThreadLocalRandom.current().nextInt(countMax - count + 1);
        if (count == 1) return plugin.ticker().add(new Bolt(c, start, dir, CarrierShot.extra(c.caster(), dir), lead));
        int interval = Math.clamp(p.i("interval", 4), 0, 40);
        final Vector first = dir.clone();
        final int total = Math.min(count, 16);
        return plugin.ticker().add(new Ticker.Tick() {
            int age = 0, fired = 0;
            public boolean tick() {
                if (!c.caster().isValid()) return false;
                while (fired < total && age >= fired * interval) {
                    Location from = p.s("from", "eye").equalsIgnoreCase("caster") ? c.caster().getLocation().add(0, 1, 0) : c.caster().getEyeLocation();
                    Vector d = first.clone();
                    if (fired > 0 && interval > 0) d.rotateAroundY(Math.toRadians((fired % 2 == 0 ? 1 : -1) * 2.5 * ((fired + 1) / 2)));
                    plugin.ticker().add(new Bolt(c, from, d, CarrierShot.extra(c.caster(), d), CarrierShot.lead(c.caster())));
                    fired++;
                }
                age++;
                return fired < total;
            }
        });
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
        final Map<UUID, Integer> lastHit = new HashMap<>();
        final int rehit = Math.max(0, p.i("rehit", 0));
        final boolean ally = p.b("ally", false);
        Entity homeTarget;
        double travelled = 0;
        int hitCount = 0;
        boolean coming = false;
        int life = 0;
        GameplayVisual visual;boolean visualStarted,visualTerminal;

        /** 빗자루 지연 보정: 첫 틱에 이 구간을 판정하며 먼저 지나간다 (null = 없음). */
        Vector leadLeft;

        Bolt(Ctx c, Location start, Vector dir, double carrierSpeed, Vector lead) {
            this.c = c; this.pos = start.clone(); this.dir = dir; this.leadLeft = lead;
            this.speed = Math.min(Math.clamp(p.d("speed", 1.5), 0.1, 6) + carrierSpeed, 8);
            this.homeTarget = c.target();
            for (String m : p.s("breaks", "").split(",")) if (!m.isBlank()) breaks.add(m.trim().toUpperCase(Locale.ROOT));
        }
        @Override public String kind() { return "bolt"; }

        @Override public void stop(){if(visual!=null){visual.close();visual=null;}if(visualStarted&&!visualTerminal){visualTerminal=true;vfx("external:miss",pos,null);}}
        @Override public boolean tick(){
            if(!GameplayVisual.PROJECTILES.containsKey(p.s("spell","")))return advance();
            if(!visualStarted){visualStarted=true;if(c.caster() instanceof Player player)visual=GameplayVisual.projectile(player,p.s("spell",""),pos,dir);if(!p.s("spell","").equals("primordial_stone"))vfx("external:launch",pos,null);}
            boolean alive=advance();if(visual!=null)visual.move(pos,dir);if(!alive)stop();return alive;
        }
        private boolean advance() {
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
                    } else if (!turnBack()) { vfx(p.s("missevent", "external:block_contact"), pos, null); burst(); return false; } else continue;
                }
                if (!MagicImpactEvent.fire(c.caster(), p.s("element", ""), pos, radius, MagicImpactEvent.Kind.BOLT)) { burst(); return false; }
                for (Entity e : pos.getWorld().getNearbyEntities(pos, radius + 1, radius + 1, radius + 1)) {
                    if (ally) {
                        if (hit.contains(e.getUniqueId()) || e.equals(c.caster()) || !plugin.targets().ally(c.caster(), e)) continue;
                        if (Targets.closestDistance(e, pos) > radius) continue;
                        hit.add(e.getUniqueId()); hitCount++;
                        Targets.heal(c.caster(), (LivingEntity) e, c.dmg(p));
                        vfx(p.s("hitevent", "external:contact"), e.getLocation(), e.getUniqueId());
                        if (hitCount >= pierce) return false;
                        continue;
                    }
                    if (!plugin.targets().enemy(c.caster(), e)) continue;
                    if (hit.contains(e.getUniqueId())) {
                        Integer last = lastHit.get(e.getUniqueId());
                        if (rehit <= 0 || last == null || life - last < rehit) continue;
                    }
                    if (Targets.closestDistance(e, pos) > radius) continue;
                    lastHit.put(e.getUniqueId(), life);
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
            if(!(p.b("proc", false) ? targets.hurtProc(c.caster(), t, dmg) : targets.hurt(c.caster(), t, dmg, p.s("element", ""))))return;
            plugin.reactions().hit(c.caster(), t, p.s("element", ""), dmg);
            vfx(p.s("hitevent", "external:contact"), t.getLocation(), t.getUniqueId());
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

        private void vfx(String event, Location at, UUID target) {
            if(!event.equals("external:launch"))visualTerminal=true;
            String spell = p.s("spell", "");
            if (!spell.isBlank() && c.caster() instanceof Player pl) Links.vfx(pl, spell, event, at, target);
        }

        private void burst() {
            double aoe = p.d("aoe", 0);
            if (aoe <= 0) return;
            double dmg = c.damage() * p.d("aoemult", 1);
            for (LivingEntity t : plugin.targets().enemiesNear(c.caster(), pos, aoe, p.i("max", 16))) {
                if(!plugin.targets().hurt(c.caster(), t, dmg, p.s("element", "")))continue;
                plugin.reactions().hit(c.caster(), t, p.s("element", ""), dmg);
                plugin.targets().effects(t, p.effects("effect"));
                plugin.targets().knock(t, pos, dir, p.d("aoeknock", 0), p.d("aoeup", 0));
            }
        }
    }
}
