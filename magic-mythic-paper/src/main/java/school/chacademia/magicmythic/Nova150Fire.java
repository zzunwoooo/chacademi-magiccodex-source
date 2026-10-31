package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/** chanova types for the 150-spell set (301~450), element fire. Rows: tools/nova150/fire.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Fire {
    private Nova150Fire() {}

    // ------------------------------------------------------------------ 334 state
    /** 쇳물 갑주: caster -> plating (spell instance, end time ms, attacker -> last retaliation ms). */
    private static final Map<UUID, Plating> PLATED = new HashMap<>();
    private record Plating(Nova n, long until, Map<UUID, Long> last) {}
    private static MagicMythicPlugin listening;

    static void register(Map<String, NovaMech.Impl> m) {
        // ================================================================ 초급
        // 326 불티 집게 (EmberTongs): tongs bite at 1.45 m ahead / 1.15 m up (r 1.3) at +0.14 s and +0.44 s; each bite damages once
        m.put("ember_tongs", n -> {
            double r = n.d("r", 1.3), reach = n.d("reach", 1.45);
            for (int at : new int[]{3, 9}) n.later(at, () -> {
                Location q = n.pl.getLocation().add(n.flat().multiply(reach)).add(0, 1.15, 0);
                for (LivingEntity e : n.near(q, r + 0.6, 8)) {
                    if (Targets.closestDistance(e, q) > r || !n.pl.hasLineOfSight(e)) continue;
                    n.hit(e, n.dmg());
                }
            });
            return true;
        });

        // 327 쇳물 방울 (MoltenDrip): lobbed glob (gravity); landing: splash r 1.3 hit + pool on the ground below for 2 s
        // (r 1.1, every 0.5 s: 25 % damage + burn 1 s)
        m.put("molten_drip", n -> {
            double speed = n.d("speed", 0.75), g = n.d("gravity", 0.04), pr = n.d("pool_r", 1.1);
            Vector dir = n.look().add(new Vector(0, 0.18, 0));
            boolean[] done = {false};
            var s = n.shotFrom(n.hand(), dir, speed, 40, "glob").radius(0.5).gravity(g)
                .hit((t, p) -> { land(n, p, t, done, pr); return false; })
                .end((p, wall) -> land(n, p, null, done, pr));
            s.maxLife = 80;
            s.fire();
            return true;
        });

        // 328 등롱 불씨 (LanternWisp): lantern floats 2 m up beside the caster (fixed) for 6 s; at 1.5 / 3.2 / 4.9 s one homing ember
        // to the nearest enemy within 10 of the lantern (no enemy -> that ember is skipped)
        m.put("lantern_wisp", n -> {
            double r = n.d("r", 10);
            Vector f = n.flat(), side = side(f);
            Location lantern = n.pl.getLocation().add(side.multiply(-0.8)).add(f.clone().multiply(-0.2)).add(0, 2.0, 0);
            if (!Nova.passable(lantern)) lantern = n.pl.getLocation().add(0, 2.0, 0);
            Location L = lantern;
            n.fx("lantern", L);
            for (int at : new int[]{30, 64, 98}) n.later(at, () -> {
                LivingEntity t = null;
                for (LivingEntity e : n.near(L, r, 8)) if (e.hasLineOfSight(n.pl) || n.pl.hasLineOfSight(e)) { t = e; break; }
                if (t == null) return;
                Vector to = Nova.chest(t).toVector().subtract(L.toVector());
                n.shotFrom(L, to, n.d("speed", 0.9), r + 6, "ember").radius(0.5).homing(t, 0.3)
                    .hit((e, p) -> { if (n.hit(e, n.dmg())) n.fx("emberHit", p); return false; })
                    .end((p, wall) -> { if (wall) n.fx("emberHit", p); })
                    .fire();
            });
            return true;
        });

        // 329 성냥 긋기 (MatchStrike): line from 0.6 m ahead on the ground, 5 m along the facing; ignites at +1.0 s:
        // enemies on the line (half width 0.4) -> damage + burn 2 s
        m.put("match_strike", n -> {
            Vector f = n.flat();
            Location a = Nova.ground(n.pl.getLocation().add(f.clone().multiply(0.6)).add(0, 0.5, 0));
            a.setYaw(n.pl.getLocation().getYaw());
            double len = n.d("len", 5), half = n.d("half", 0.4);
            Location b = a.clone().add(f.clone().multiply(len));
            n.fx("line", a);
            n.later(20, () -> {
                for (LivingEntity e : n.near(a.clone().add(f.clone().multiply(len / 2)), len / 2 + 1.5, 16)) {
                    if (!onSegment(e, a, b, half, -1, 2.2)) continue;
                    if (n.hit(e, n.dmg())) n.status(e, "burn:40");
                }
            });
            return true;
        });

        // 330 숯불 투척 (CoalLob): lobbed coal that bounces twice on the ground (bounce visual each time), explodes on the 3rd
        // ground contact / a wall / an enemy: r 1.7 damage
        m.put("coal_lob", n -> {
            double speed = n.d("speed", 0.7), g = n.d("gravity", 0.045), r = n.d("r", 1.7);
            Location pos = n.hand();
            Vector vel = n.look().add(new Vector(0, 0.2, 0)).normalize().multiply(speed);
            GameplayVisual vis = n.shot("coal", pos, vel);
            int[] bounces = {0};
            Runnable[] blast = new Runnable[1];
            Location[] last = {pos.clone()};
            boolean[] over = {false};
            blast[0] = () -> {
                if (over[0]) return;
                over[0] = true;
                if (vis != null) { vis.move(last[0], vel.lengthSquared() < 1e-6 ? n.look() : vel); vis.close(); }
                n.fx("blast", last[0]);
                for (LivingEntity e : n.near(last[0], r)) n.hit(e, n.dmg());
            };
            n.loop(100, age -> {
                vel.setY(vel.getY() - g);
                double len = vel.length();
                if (len < 1e-4) { blast[0].run(); return false; }
                Location p = last[0];
                var hit = p.getWorld().rayTraceBlocks(p, vel.clone().normalize(), len, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitBlock() != null) {
                    Location at = hit.getHitPosition().toLocation(p.getWorld()).subtract(vel.clone().normalize().multiply(0.05));
                    last[0] = at;
                    boolean floor = hit.getHitBlockFace() != null && hit.getHitBlockFace().getModY() > 0;
                    if (!floor || bounces[0] >= 2) { blast[0].run(); return false; }
                    bounces[0]++;
                    n.fx("bounce", at);
                    vel.setY(-vel.getY() * 0.55); vel.setX(vel.getX() * 0.7); vel.setZ(vel.getZ() * 0.7);
                    last[0] = at.add(0, 0.06, 0);
                } else {
                    last[0] = p.clone().add(vel);
                }
                if (!n.plugin.protection().pair(n.pl, last[0])) { blast[0].run(); return false; }
                for (LivingEntity e : n.near(last[0], 1.5, 4)) if (Targets.closestDistance(e, last[0]) <= 0.5) { blast[0].run(); return false; }
                if (vis != null) vis.move(last[0], vel);
                return true;
            }, () -> blast[0].run());
            return true;
        });

        // 331 불꽃 손톱 (FlameClaw): claws cross the target at +0.16 s: 2.4 m / ±50° cone -> damage + burn 1.5 s + burn visual on each
        m.put("flame_claw", n -> {
            n.later(3, () -> {
                for (LivingEntity e : cone(n, n.d("r", 2.4), n.d("half", 50))) {
                    if (!n.hit(e, n.dmg())) continue;
                    n.status(e, "burn:30");
                    n.fx("burn", e.getLocation(), e);
                }
            });
            return true;
        });

        // 332 풀무질 (BellowsBlast): blast at +0.15 s, judged at +0.2 s: 5 m / ±30° cone -> damage + push ~3 blocks (knock scales down for bosses)
        m.put("bellows_blast", n -> {
            double push = n.d("push", 0.9);
            n.later(4, () -> {
                Location from = n.pl.getLocation(); Vector f = n.flat();
                for (LivingEntity e : cone(n, n.d("r", 5), n.d("half", 30))) {
                    if (n.hit(e, n.dmg())) n.knock(e, from, f, push, 0.2);
                }
            });
            return true;
        });

        // ================================================================ 중급
        // 333 단조 망치 (ForgeHammer): circle at the target's feet now, hammer lands at +0.62 s: r 1.8 there -> damage + stun 1 s
        // (status engine: boss rules / artifacts); moving off the spot dodges it
        m.put("forge_hammer", n -> {
            LivingEntity t = pick(n, 16, n.d("mana", 22));
            if (t == null) return false;
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            n.fx("main", at);
            double r = n.d("r", 1.8);
            n.later(12, () -> {
                for (LivingEntity e : n.near(at, r + 1, 12)) {
                    if (!onSpot(e, at, r, 3)) continue;
                    if (n.hit(e, n.dmg())) n.status(e, "stun:" + n.i("stun", 20));   // engine: boss -> slow
                }
            });
            return true;
        });

        // 334 쇳물 갑주 (MoltenPlating): 4 s; an enemy that hits the caster in melee -> damage + burn 3 s + retaliate visual
        // (same attacker at most every 0.5 s). Listener: Hooks.melee
        m.put("molten_plating", n -> {
            int ticks = Math.clamp(n.i("ticks", 80), 20, 200);
            UUID id = n.pl.getUniqueId();
            Plating pt = new Plating(n, System.currentTimeMillis() + ticks * 50L, new HashMap<>());
            PLATED.put(id, pt);
            n.later(ticks + 2, () -> PLATED.remove(id, pt));
            return true;
        });

        // 335 등롱 사슬 (LanternChain): fire line 4 m ahead across the facing, x -2.6..2.6 (local), live 0.85~3.4 s:
        // enemies touching it (half width 0.6, height 0..2) -> damage + burn 2 s, same enemy every 0.5 s
        m.put("lantern_chain", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat(), side = side(f);
            Location c0 = Nova.ground(o.clone().add(f.clone().multiply(4)).add(0, 1, 0));
            Location a = c0.clone().add(side.clone().multiply(2.6)), b = c0.clone().add(side.clone().multiply(-2.6));
            double half = n.d("half", 0.6);
            Map<UUID, Integer> last = new HashMap<>();
            n.loop(68, age -> {
                if (age < 17) return true;
                for (LivingEntity e : n.near(c0, 4.5, 16)) {
                    if (!onSegment(e, a, b, half, -0.6, 2.2)) continue;
                    Integer l = last.get(e.getUniqueId());
                    if (l != null && age - l < 10) continue;
                    last.put(e.getUniqueId(), age);
                    if (n.hit(e, n.dmg())) n.status(e, "burn:40");
                }
                return true;
            }, null);
            return true;
        });

        // 336 불꽃 팽이 (FlameTop): top spawns 1.5 m ahead on the ground, wanders 4 s (0.17 b/tick, steers to the nearest enemy within 6,
        // turns at walls); touching enemies (r 0.7) -> damage + burn 2 s + burn visual, same enemy every 0.75 s; fade at the end
        m.put("flame_top", n -> {
            Vector f = n.flat();
            Location pos = Nova.ground(n.pl.getLocation().add(f.clone().multiply(1.5)).add(0, 1, 0)).add(0, 0.05, 0);
            if (!n.plugin.protection().pair(n.pl, pos)) return false;
            double speed = n.d("speed", 0.17), r = n.d("r", 0.7);
            int life = Math.clamp(n.i("ticks", 80), 20, 160);
            n.fx("spawn", pos);
            Vector dir = f.clone();
            GameplayVisual vis = n.shot("top", pos, dir);
            Map<UUID, Integer> last = new HashMap<>();
            n.loop(life, age -> {
                if (age % 5 == 0) {
                    LivingEntity t = null;
                    for (LivingEntity e : n.near(pos, 6, 4)) { t = e; break; }
                    if (t != null) {
                        Vector to = t.getLocation().toVector().subtract(pos.toVector()).setY(0);
                        if (to.lengthSquared() > 0.04) { dir.multiply(0.6).add(to.normalize().multiply(0.4)); dir.setY(0); if (dir.lengthSquared() < 1e-4) dir.copy(to); dir.normalize(); }
                    }
                }
                Location next = pos.clone().add(dir.clone().multiply(speed));
                double gy = Telegraphs.ground(next.clone().add(0, 1, 0));
                if (Math.abs(gy - next.getY()) > 1.1 || !Nova.passable(next.clone().add(0, Math.max(0, gy - next.getY()) + 0.2, 0))) {
                    dir.rotateAroundY(Math.PI / 2 + Math.random());
                } else {
                    next.setY(gy + 0.05);
                    if (!n.plugin.protection().pair(n.pl, next)) return false;
                    pos.setX(next.getX()); pos.setY(next.getY()); pos.setZ(next.getZ());
                }
                if (vis != null) vis.move(pos, dir);
                for (LivingEntity e : n.near(pos.clone().add(0, 0.5, 0), r + 1.2, 6)) {
                    if (Targets.closestDistance(e, pos.clone().add(0, 0.5, 0)) > r) continue;
                    Integer l = last.get(e.getUniqueId());
                    if (l != null && age - l < 15) continue;
                    last.put(e.getUniqueId(), age);
                    if (n.hit(e, n.dmg())) { n.status(e, "burn:40"); n.fx("burn", Nova.chest(e)); }
                }
                return true;
            }, () -> { if (vis != null) vis.close(); n.fx("fade", pos); });
            return true;
        });

        // 337 화염 꽃잎 (FlamePetals): petals leave at +0.15 s, reach r 4 at +0.65 s, back at +1.2 s; enemies within r 4 (snapshot at +0.15 s)
        // are hit when the petal ring passes them outward and again inward (2nd hit adds burn 2 s); still within r 5 to be hit
        m.put("flame_petals", n -> {
            double r = n.d("r", 4);
            n.later(3, () -> {
                Location c0 = n.pl.getLocation();
                for (LivingEntity e : n.near(c0.clone().add(0, 1, 0), r + 0.5, 16)) {
                    double d = horiz(e.getLocation(), c0);
                    if (d > r + e.getWidth() / 2 || !n.pl.hasLineOfSight(e)) continue;
                    double dd = Math.min(r, Math.max(0.45, d));
                    int out = (int) Math.round((dd - 0.45) / (r - 0.45) * 10);
                    int back = 10 + (int) Math.round((r - dd) / r * 11);
                    n.later(out, () -> { if (near(n, e, r + 1)) n.hit(e, n.dmg()); });
                    n.later(back, () -> { if (near(n, e, r + 1) && n.hit(e, n.dmg())) n.status(e, "burn:40"); });
                }
            });
            return true;
        });

        // 338 용암 거품 (LavaBubbles): r 2.5 pool at the aimed ground; bubbles (local x,z) (-1.15,-0.55) / (1.2,-0.35) / (0.05,1.15)
        // burst at 0.80 / 1.15 / 1.50 s: r 1.3 each -> damage
        m.put("lava_bubbles", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            Vector f = n.flat(), side = side(f);
            n.fx("main", at);
            double[][] b = {{-1.15, -0.55, 16}, {1.20, -0.35, 23}, {0.05, 1.15, 30}};
            double r = n.d("r", 1.3);
            for (double[] q : b) {
                Location p = at.clone().add(side.clone().multiply(q[0])).add(f.clone().multiply(q[1]));
                n.later((int) q[2], () -> { for (LivingEntity e : n.near(p, r + 1, 12)) if (onSpot(e, p, r, 2.5)) n.hit(e, n.dmg()); });
            }
            return true;
        });

        // ================================================================ 고급
        // 339 주물 거푸집 (CastingMold): mold at the target's feet; at +0.6 s the target still within r 1.6 is rooted 2 s (속박, engine rules);
        // at +2.6 s the mold bursts: r 2.2 -> damage + burn 3 s
        m.put("casting_mold", n -> {
            LivingEntity t = pick(n, 16, n.d("mana", 55));
            if (t == null) return false;
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            n.fx("main", at);
            n.later(12, () -> {
                if (!onSpot(t, at, n.d("r", 1.6), 3) || !n.enemy(t)) return;
                n.status(t, "root:" + n.i("root", 40));   // engine: bosses are immune to root
            });
            n.later(52, () -> { for (LivingEntity e : n.near(at, n.d("blast", 2.2))) if (n.hit(e, n.dmg())) n.status(e, "burn:60"); });
            return true;
        });

        // 340 풍등 폭격 (SkyLanternRaid): lanterns land on 5 pentagon points (local x,z from the pack) at 2.25..3.45 s (r 1.3, 50 %),
        // the big one at the centre at 3.95 s (r 2, 100 %)
        m.put("sky_lantern_raid", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            Vector f = n.flat(), side = side(f);
            n.fx("main", at);
            double[][] pts = {{-1.242, -1.816}, {-2.111, 0.620}, {-0.062, 2.199}, {2.072, 0.739}, {1.343, -1.743}};
            for (int k = 0; k < 5; k++) {
                Location p = at.clone().add(side.clone().multiply(pts[k][0])).add(f.clone().multiply(pts[k][1]));
                n.later(45 + 6 * k, () -> { for (LivingEntity e : n.near(p, 2.5, 12)) if (onSpot(e, p, 1.3, 3)) n.hit(e, n.dmg(0.5)); });
            }
            n.later(79, () -> { for (LivingEntity e : n.near(at, 3, 16)) if (onSpot(e, at, 2, 3)) n.hit(e, n.dmg()); });
            return true;
        });

        // 341 불실 그물 (FlameLoom): net shot (0.9 b/tick, 16 blocks, r 0.9); on an enemy (its feet) or a wall: r 1.8 caught ->
        // 50 % + slow 3 s; burn ticks 20 % at +1 / +2 s; net burns away at +3 s (30 %). Caught enemies must stay within r 3
        m.put("flame_loom", n -> {
            boolean[] done = {false};
            n.shotFrom(n.hand(), n.look(), n.d("speed", 0.9), n.d("range", 16), "projectile").radius(0.9)
                .hit((t, p) -> { net(n, Nova.ground(t.getLocation().add(0, 0.5, 0)), done); return false; })
                .end((p, wall) -> net(n, Nova.ground(p.clone().add(0, 0.5, 0)), done))
                .fire();
            return true;
        });

        // 342 화로 돔 (FurnaceDome): r 5 dome at the aimed ground; heat pulses at 1/2/3/4 s (15 % + burn 1.5 s), closing blast at 5.2 s (100 %)
        m.put("furnace_dome", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            double r = n.d("r", 5);
            n.fx("main", at);
            for (int k = 1; k <= 4; k++) n.later(20 * k, () -> { for (LivingEntity e : n.near(at, r)) if (onSpot(e, at, r, 4.2) && n.hit(e, n.dmg(0.15))) n.status(e, "burn:30"); });
            n.later(104, () -> { for (LivingEntity e : n.near(at, r)) if (onSpot(e, at, r, 4.5)) n.hit(e, n.dmg()); });
            return true;
        });

        // 343 쇳물 해일 (MoltenWave): ground wave from 1 m ahead, 0.325 b/tick (6.5 m/s) for 10 blocks; body = 2.4 each side x 1 front/back,
        // 0..2.5 high: every enemy once -> damage + burn 3 s + small push; at the end (or a wall) crash: r 2 -> 50 %
        m.put("molten_wave", n -> {
            Vector f = n.flat(), side = side(f);
            Location from = Nova.ground(n.pl.getLocation().add(f.clone().multiply(1)).add(0, 1, 0)).add(0, 0.05, 0);
            Set<UUID> got = new HashSet<>();
            n.shotFrom(from, f, n.d("speed", 0.325), n.d("range", 10), "wave").radius(0.01).ground()
                .hit((t, p) -> true)
                .step((p, d) -> {
                    for (LivingEntity e : n.near(p, 3.6, 16)) {
                        if (got.contains(e.getUniqueId())) continue;
                        Vector rel = e.getLocation().toVector().subtract(p.toVector());
                        double w = e.getWidth() / 2, dy = rel.getY();
                        if (Math.abs(rel.dot(side)) > 2.4 + w || Math.abs(rel.dot(f)) > 1.0 + w || dy < -1 || dy > 2.5) continue;
                        got.add(e.getUniqueId());
                        if (n.hit(e, n.dmg())) { n.status(e, "burn:60"); n.knock(e, p, f, 0.45, 0.2); }
                    }
                })
                .end((p, wall) -> {
                    n.fx("crash", p);
                    for (LivingEntity e : n.near(p, n.d("crash_r", 2))) n.hit(e, n.dmg(0.5));
                })
                .fire();
            return true;
        });

        // 344 불꽃 연꽃 (FireLotus): lotus at the aimed ground; centre blast at 1.6 s (r 2, 100 %); petal flames spread 7 m/s out to r 6:
        // enemies at 2..6 hit when the flame reaches them (60 % + burn 3 s)
        m.put("fire_lotus", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            double r = n.d("r", 6);
            n.fx("main", at);
            n.later(32, () -> {
                for (LivingEntity e : n.near(at, r + 1, 24)) {
                    double d = horiz(e.getLocation(), at) - e.getWidth() / 2;
                    if (!onSpot(e, at, r, 3)) continue;
                    if (d <= 2) { n.hit(e, n.dmg()); continue; }
                    n.later((int) Math.round(d / 7 * 20), () -> { if (onSpot(e, at, r + 0.5, 3) && n.hit(e, n.dmg(0.6))) n.status(e, "burn:60"); });
                }
            });
            return true;
        });

        // ================================================================ 비전
        // 345 쇳물 손 (MoltenGrasp): circle at the target's feet; 0.8 s hand erupts (r 1.8, 30 %); 1.55 s grab if the target is within r 2.2
        // (n.carry: 고정 held in the hand until 2.9 s); 1.9 / 2.4 s squeeze 15 %; 2.9 s throw forward+up along the caster facing (30 %);
        // 3.3 s fist bursts where the target is (r 2.5, 40 %)
        m.put("molten_grasp", n -> {
            LivingEntity t = pick(n, 16, n.d("mana", 120));
            if (t == null) return false;
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            Vector f = n.flat();
            n.fx("main", at);
            boolean[] held = {false};
            n.later(16, () -> { for (LivingEntity e : n.near(at, 2.8, 12)) if (onSpot(e, at, 1.8, 3)) n.hit(e, n.dmg(0.3)); });
            n.later(31, () -> {
                if (!onSpot(t, at, 2.2, 3) || !n.enemy(t)) return;
                held[0] = true;
                Location hold = at.clone().add(0, 0.6, 0);
                n.carry(t, 27, age -> hold, 0.3, null);   // 고정 via the engine; bosses keep the status but are not moved
            });
            for (int k : new int[]{38, 48}) n.later(k, () -> { if (held[0] && onSpot(t, at, 3, 4)) n.hit(t, n.dmg(0.15)); });
            n.later(58, () -> {
                if (!held[0] || !t.isValid() || t.isDead()) return;
                n.hit(t, n.dmg(0.3));
                n.knock(t, t.getLocation().subtract(f), f, n.d("throw", 1.3), n.d("up", 0.8));
            });
            n.later(66, () -> {
                Location p = held[0] && t.isValid() && t.getWorld() == at.getWorld() ? t.getLocation() : at.clone().add(f.clone().multiply(3)).add(0, 1, 0);
                for (LivingEntity e : n.near(p, 2.5)) n.hit(e, n.dmg(0.4));
            });
            return true;
        });

        // 346 태양 흑점 (Sunspot): sun over the aimed ground; heat pulses at 1.5..6.5 s every 1 s: centre beam r 1.2 -> 20 % + burn 1 s,
        // rotating-beam band 1.2..3.2 -> 10 %; final flare at 7.0 s r 4 -> 80 %
        m.put("sunspot", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            n.fx("main", at);
            for (int k = 0; k < 6; k++) n.later(30 + 20 * k, () -> {
                for (LivingEntity e : n.near(at, 4.5, 16)) {
                    if (!onSpot(e, at, 3.2, 7)) continue;
                    double d = horiz(e.getLocation(), at) - e.getWidth() / 2;
                    if (d <= 1.2) { if (n.hit(e, n.dmg(0.2))) n.status(e, "burn:20"); }
                    else n.hit(e, n.dmg(0.1));
                }
            });
            n.later(140, () -> { for (LivingEntity e : n.near(at, n.d("r", 4))) if (onSpot(e, at, 4, 7)) n.hit(e, n.dmg(0.8)); });
            return true;
        });

        // 347 도가니 붓기 (Crucible): crucible over the target's feet (fixed spot); pour 2.2~4.6 s every 0.4 s: r 1.4 -> 12 % + burn 1 s,
        // pool r 2.3 -> burn 2 s only; 5.2 s pool eruption r 2.4 -> 80 %
        m.put("crucible", n -> {
            LivingEntity t = pick(n, 18, n.d("mana", 140));
            if (t == null) return false;
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            n.fx("main", at);
            for (int k = 0; k < 7; k++) n.later(44 + 8 * k, () -> {
                for (LivingEntity e : n.near(at, 3.3, 16)) {
                    if (onSpot(e, at, 1.4, 4)) { if (n.hit(e, n.dmg(0.12))) n.status(e, "burn:20"); }
                    else if (onSpot(e, at, 2.3, 2)) n.status(e, "burn:40");
                }
            });
            n.later(104, () -> { for (LivingEntity e : n.near(at, 3.4, 16)) if (onSpot(e, at, 2.4, 3)) n.hit(e, n.dmg(0.8)); });
            return true;
        });

        // 348 불나비 떼 (EmberButterflies): 12 butterflies at +0.5 + 0.05 k s from around the chest; nearest enemies within 16 (max 6) are
        // shared round-robin; each scatters outward/up then homes in (0.55 b/tick); first enemy touched -> damage + pop; 3 s life.
        // No enemy: they scatter ahead and pop after ~1.5 s
        m.put("ember_butterflies", n -> {
            double r = n.d("r", 16);
            List<LivingEntity> foes = new ArrayList<>();
            for (LivingEntity e : n.near(n.pl.getLocation().add(0, 1, 0), r, 6)) if (n.pl.hasLineOfSight(e)) foes.add(e);
            Random rnd = new Random();
            for (int k = 0; k < 12; k++) {
                int kk = k;
                n.later(10 + k, () -> {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    Vector out = new Vector(Math.cos(ang), 0, Math.sin(ang));
                    Location from = n.pl.getLocation().add(out.clone().multiply(0.7)).add(0, 1.2 + rnd.nextDouble() * 0.5, 0);
                    LivingEntity t = foes.isEmpty() ? null : foes.get(kk % foes.size());
                    if (t != null && (!t.isValid() || t.isDead())) t = null;
                    Vector dir = out.clone().add(new Vector(0, 0.6, 0)).add(n.flat().multiply(0.5));
                    boolean[] popped = {false};
                    var s = n.shotFrom(from, dir, n.d("speed", 0.55), t == null ? 9 : 40, "fly").radius(0.6)
                        .hit((e, p) -> { popped[0] = true; n.hit(e, n.dmg()); n.fx("pop", p); return false; })
                        .end((p, wall) -> { if (!popped[0]) n.fx("pop", p); });
                    if (t != null) s.homing(t, n.d("turn", 0.15));
                    s.maxLife = 60;
                    s.fire();
                });
            }
            return true;
        });

        // ================================================================ 궁극
        // 349 대장장이 신의 모루 (ForgeGodAnvil): forge circle r 7 at the aimed ground for 12 s. Anvil lands at 3.4 s -> slow 30 % inside
        // (refreshed every 1 s until 7.8 s); strikes 5.1 s 25 % / 6.2 s 35 % (r 7), 7.8 s 100 % + stun 1 s (engine boss rules);
        // 8.15 s cracks erupt: r 9 -> 40 % + burn 4 s
        m.put("forge_god_anvil", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            double r = n.d("r", 7);
            n.fx("main", at);
            for (int k = 68; k < 156; k += 20) n.later(k, () -> { for (LivingEntity e : n.near(at, r)) if (onSpot(e, at, r, 6)) n.status(e, "slow:24:0.3"); });
            n.later(102, () -> { for (LivingEntity e : n.near(at, r)) if (onSpot(e, at, r, 6)) n.hit(e, n.dmg(0.25)); });
            n.later(124, () -> { for (LivingEntity e : n.near(at, r)) if (onSpot(e, at, r, 6)) n.hit(e, n.dmg(0.35)); });
            n.later(156, () -> {
                for (LivingEntity e : n.near(at, r)) {
                    if (onSpot(e, at, r, 6) && n.hit(e, n.dmg())) n.status(e, "stun:20");   // engine: boss -> slow
                }
            });
            n.later(163, () -> { for (LivingEntity e : n.near(at, n.d("erupt", 9))) if (onSpot(e, at, 9, 6) && n.hit(e, n.dmg(0.4))) n.status(e, "burn:80"); });
            return true;
        });

        // 350 용광로의 문 (FurnaceGate): gate over the aimed ground for 12 s. Molten fall 2.6~8.4 s every 1 s: r 3 -> 12 % + burn 1.5 s,
        // r 7 -> slow 30 %; 9.0 s gate slams: r 8 -> 100 %; 9.6 s gate shatters: r 10 -> 50 % + burn 4 s
        m.put("furnace_gate", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            at.setYaw(n.pl.getLocation().getYaw());
            n.fx("main", at);
            for (int k = 0; k < 6; k++) n.later(52 + 20 * k, () -> {
                for (LivingEntity e : n.near(at, 7.5, 32)) {
                    if (!onSpot(e, at, 7, 8)) continue;
                    if (onSpot(e, at, 3, 8)) { if (n.hit(e, n.dmg(0.12))) n.status(e, "burn:30"); }
                    else n.status(e, "slow:24:0.3");
                }
            });
            n.later(180, () -> { for (LivingEntity e : n.near(at, n.d("r", 8), 32)) if (onSpot(e, at, 8, 8)) n.hit(e, n.dmg()); });
            n.later(192, () -> { for (LivingEntity e : n.near(at, n.d("shard", 10), 32)) if (onSpot(e, at, 10, 8) && n.hit(e, n.dmg(0.5))) n.status(e, "burn:80"); });
            return true;
        });
    }

    static void enable(MagicMythicPlugin plugin) {
        PLATED.clear();
        if (listening != plugin) { Bukkit.getPluginManager().registerEvents(new Hooks(), plugin); listening = plugin; }
    }

    // ------------------------------------------------------------------ listeners
    static final class Hooks implements Listener {
        /** 334 쇳물 갑주: melee hit on a plated caster -> retaliation next tick (damage + burn 3 s + retaliate visual). */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void melee(EntityDamageByEntityEvent e) {
            if (PLATED.isEmpty() || !(e.getEntity() instanceof Player victim)) return;
            String cause = e.getCause().name();
            if (!cause.equals("ENTITY_ATTACK") && !cause.equals("ENTITY_SWEEP_ATTACK")) return;
            Entity d = e.getDamager();
            if (d instanceof Projectile || !(d instanceof LivingEntity attacker)) return;
            Plating pt = PLATED.get(victim.getUniqueId());
            if (pt == null) return;
            long now = System.currentTimeMillis();
            if (now > pt.until()) { PLATED.remove(victim.getUniqueId()); return; }
            Nova n = pt.n();
            if (n.pl != victim || !n.enemy(attacker)) return;
            Long last = pt.last().get(attacker.getUniqueId());
            if (last != null && now - last < 500) return;
            pt.last().put(attacker.getUniqueId(), now);
            n.later(0, () -> {
                if (!attacker.isValid() || attacker.isDead()) return;
                if (n.hit(attacker, n.dmg())) n.status(attacker, "burn:60");
                n.fx("retaliate", Nova.chest(attacker));
            });
        }
        @EventHandler public void quit(PlayerQuitEvent e) { PLATED.remove(e.getPlayer().getUniqueId()); }
    }

    // ------------------------------------------------------------------ shared pieces
    /** 327 landing (once): splash + r 1.3 hit, then the 2 s pool on the ground below (every 0.5 s: 25 % + burn 1 s within pr). */
    private static void land(Nova n, Location p, LivingEntity direct, boolean[] done, double pr) {
        if (done[0]) return;
        done[0] = true;
        n.fx("splash", p);
        for (LivingEntity e : n.near(p, n.d("r", 1.3))) n.hit(e, n.dmg());
        Location pool = Nova.ground((direct != null ? direct.getLocation() : p.clone()).add(0, 0.5, 0));
        n.fx("pool", pool);
        for (int k = 1; k <= 4; k++) n.later(10 * k, () -> {
            for (LivingEntity e : n.near(pool, pr + 1, 8)) if (onSpot(e, pool, pr, 1.5) && n.hit(e, n.dmg(0.25))) n.status(e, "burn:20");
        });
    }

    /** 341 net landing (once): impact visual at `at`, then the caught enemies' timeline. */
    private static void net(Nova n, Location at, boolean[] done) {
        if (done[0]) return;
        done[0] = true;
        n.fx("impact", at);
        List<LivingEntity> caught = new ArrayList<>();
        for (LivingEntity e : n.near(at, n.d("net_r", 1.8) + 0.5, 12)) if (onSpot(e, at, n.d("net_r", 1.8), 3)) caught.add(e);
        for (LivingEntity e : caught) if (n.hit(e, n.dmg(0.5))) n.status(e, "slow:60:0.5");
        for (int k : new int[]{20, 40}) n.later(k, () -> { for (LivingEntity e : caught) if (onSpot(e, at, 3, 3)) n.hit(e, n.dmg(0.2)); });
        n.later(60, () -> { for (LivingEntity e : caught) if (onSpot(e, at, 3, 3)) n.hit(e, n.dmg(0.3)); });
    }

    /** Local +X of a pack frame whose +Z = f and +Y = up (up x f). */
    private static Vector side(Vector f) { return new Vector(0, 1, 0).crossProduct(f).normalize(); }

    private static double horiz(Location a, Location b) { return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()); }

    /** Enemy still alive and within r (horizontal) of the caster. */
    private static boolean near(Nova n, LivingEntity e, double r) {
        return e.isValid() && !e.isDead() && e.getWorld() == n.pl.getWorld() && horiz(e.getLocation(), n.pl.getLocation()) <= r + e.getWidth() / 2;
    }

    /** Entity's feet within `half` (+ its half width) of the horizontal segment a-b, and dy (from a) within [lo, hi]. */
    private static boolean onSegment(LivingEntity e, Location a, Location b, double half, double lo, double hi) {
        if (!e.isValid() || e.isDead() || e.getWorld() != a.getWorld()) return false;
        Location l = e.getLocation();
        double ax = a.getX(), az = a.getZ(), bx = b.getX() - ax, bz = b.getZ() - az, px = l.getX() - ax, pz = l.getZ() - az;
        double len2 = bx * bx + bz * bz, t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, (px * bx + pz * bz) / len2));
        double dx = px - bx * t, dz = pz - bz * t, dy = l.getY() - (a.getY() + (b.getY() - a.getY()) * t);
        return Math.hypot(dx, dz) <= half + e.getWidth() / 2 && dy >= lo && dy <= hi;
    }

    /** Enemies in a horizontal cone in front of the caster (feet origin, reach r, half angle in degrees), in sight. */
    private static List<LivingEntity> cone(Nova n, double r, double halfDeg) {
        Location o = n.pl.getLocation();
        Vector f = n.flat();
        var out = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(o.clone().add(0, 1, 0), r + 1.5, 16)) {
            Vector to = e.getLocation().toVector().subtract(o.toVector());
            double dy = to.getY(); to.setY(0);
            double d = to.length(), half = e.getWidth() / 2;
            if (d - half > r || dy < -1.5 || dy > 2.5) continue;
            if (d > half + 0.2) {
                double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(f)))));
                if (ang - Math.toDegrees(Math.atan2(half, d)) > halfDeg) continue;
            }
            if (!n.pl.hasLineOfSight(e)) continue;
            out.add(e);
        }
        return out;
    }

    /** ENTITY spells: the Bridge's target (or the enemy nearest the aim point); null (warned + mana back) when none in range. */
    private static LivingEntity pick(Nova n, double range, double mana) {
        LivingEntity t = n.target();
        if (t == null || !n.enemy(t)) {
            var l = n.near(n.aim(), 2.5, 1);
            t = l.isEmpty() ? null : l.get(0);
        }
        if (t == null || t.getWorld() != n.pl.getWorld() || t.getLocation().distanceSquared(n.pl.getLocation()) > (range + 2) * (range + 2)) {
            n.warn("마법을 걸 대상이 없어요");
            n.refund(mana);
            return null;
        }
        return t;
    }

    /** Target still alive and standing on the marked ground spot (horizontal r, up to `h` blocks above it). */
    private static boolean onSpot(LivingEntity t, Location at, double r, double h) {
        if (!t.isValid() || t.isDead() || t.getWorld() != at.getWorld()) return false;
        Location l = t.getLocation();
        double dy = l.getY() - at.getY();
        return Math.hypot(l.getX() - at.getX(), l.getZ() - at.getZ()) <= r + t.getWidth() / 2 && dy >= -1 && dy <= h;
    }
}
