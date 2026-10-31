package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/** chanova types for the 150-spell set (301~450), element wind. Rows: tools/nova150/wind.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Wind {
    private Nova150Wind() {}

    /** 315 천 마리 종이학: casters chanting right now, and the server tick each player last took damage. */
    private static final Map<UUID, Integer> CHANTING = new HashMap<>();   // caster -> chant start tick
    private static final Map<UUID, Integer> HURT = new HashMap<>();
    /** 321 무풍지대: projectiles frozen by some calm zone right now. */
    private static final Set<UUID> FROZEN = new HashSet<>();

    static void register(Map<String, NovaMech.Impl> m) {
        // ================================================================== 초급
        // 301 민들레 홀씨탄 (DandelionVolley): 5 seeds at yaw 0, ∓14, ∓28° (centre first, 0.1~0.25 s), 0.225 blocks/tick rising
        // slightly, 9 blocks; each seed hits its first enemy once (pop), pops on blocks / at the end of its flight
        m.put("dandelion_volley", n -> {
            double speed = n.d("speed", 0.225), range = n.d("range", 9);
            int[] at = {1, 2, 2, 3, 4};
            double[] yaw = {0, -14, 14, -28, 28};
            for (int k = 0; k < 5; k++) {
                double a = Math.toRadians(yaw[k]);
                n.later(at[k], () -> {
                    boolean[] hit = {false};
                    n.shotFrom(n.hand(), n.look().rotateAroundY(a), speed, range, "seed").radius(0.5).gravity(-0.001)
                        .hit((t, p) -> { hit[0] = true; tick(n, t, n.dmg()); n.fx("pop", p); return false; })
                        .end((p, wall) -> { if (!hit[0]) n.fx("pop", p); })
                        .fire();
                });
            }
            return true;
        });

        // 302 바람개비 던지기 (PinwheelToss): spinning pinwheel (1 block/tick, 14 blocks); it sticks in the first enemy / block hit and
        // spins there 3 s: stick = 50 % on the enemy, then 6 cuts at +0.5..3.0 s on enemies within 1.6 of the stick point (10 % each)
        m.put("pinwheel_toss", n -> {
            double speed = n.d("speed", 1.0), range = n.d("range", 14), r = n.d("r", 1.6);
            n.later(1, () -> {
                boolean[] stuck = {false};
                n.shotFrom(n.hand(), n.look(), speed, range, "projectile").radius(0.5)
                    .hit((t, p) -> { stuck[0] = true; pin(n, p, t, r); return false; })
                    .end((p, wall) -> { if (!stuck[0] && wall) pin(n, p, null, r); })
                    .fire();
            });
            return true;
        });

        // 303 종이학 (PaperCrane): one crane from the hand at +0.1 s, 0.45 blocks/tick homing (turn 0.2) on the target for ≤ 5 s;
        // the first enemy it touches takes damage (impact); a block or the time limit ends it with the same burst
        m.put("paper_crane", n -> {
            LivingEntity t = pick(n, 18);
            if (t == null) return false;
            double speed = n.d("speed", 0.45), turn = n.d("turn", 0.2);
            int life = n.i("life", 100);
            n.later(1, () -> {
                Vector dir = Nova.chest(t).toVector().subtract(n.hand().toVector());
                boolean[] touched = {false};
                var s = n.shotFrom(n.hand(), dir.lengthSquared() < 1e-4 ? n.look() : dir, speed, speed * life, "projectile").radius(0.55).homing(t, turn)
                    .hit((e, p) -> { touched[0] = true; tick(n, e, n.dmg()); n.fx("impact", p); return false; })
                    .end((p, wall) -> { if (!touched[0]) n.fx("impact", p); });
                s.maxLife = life + 5;
                s.fire();
            });
            return true;
        });

        // 304 회전 베기 (WhirlCut): one turn; at 0.15 s every enemy in sight within 2 blocks (−1.5..+2.5 high) is cut once + pushed out a little
        m.put("whirl_cut", n -> {
            double r = n.d("r", 2);
            n.later(2, () -> {
                Location o = n.pl.getLocation();
                for (LivingEntity e : ring(n, o, 0, r, -1.5, 2.5)) {
                    if (!n.pl.hasLineOfSight(e)) continue;
                    if (tick(n, e, n.dmg())) n.knock(e, o, n.flat(), 0.25, 0.1);
                }
            });
            return true;
        });

        // 305 휘파람 칼날 (WhistleBlade): two crescent blades (bladeA at +0.05 s, bladeB at +0.2 s) from chest height 0.7 ahead,
        // 0.85 blocks/tick (17 m/s), 7 blocks; each blade stops on its first enemy (1 hit each, impact) or on a block
        m.put("whistle_blade", n -> {
            double speed = n.d("speed", 0.85), range = n.d("range", 7);
            String[] sys = {"bladeA", "bladeB"};
            int[] at = {0, 3};
            for (int k = 0; k < 2; k++) {
                String system = sys[k];
                n.later(at[k], () -> {
                    Location from = n.pl.getLocation().add(0, 1.25, 0).add(n.flat().multiply(0.7));
                    n.shotFrom(from, n.look(), speed, range, system).radius(0.7)
                        .hit((t, p) -> { tick(n, t, n.dmg()); n.fx("impact", p); return false; })
                        .end((p, wall) -> { if (wall) n.fx("impact", p); })
                        .fire();
                });
            }
            return true;
        });

        // 306 돌풍 장타 (GustPalm): palm contact at 0.1 s: the nearest enemy in front (3 blocks, ±35°, in sight) takes damage and is
        // blown ~3 blocks away from the caster (knock 1.1 + up 0.3, bosses less); `knock` follows it. A miss still costs the mana.
        m.put("gust_palm", n -> {
            double r = n.d("r", 3), knock = n.d("knock", 1.1);
            n.later(1, () -> {
                Location o = n.pl.getLocation();
                List<LivingEntity> c = cone(n, r, 35);
                if (c.isEmpty()) return;
                LivingEntity t = c.get(0);
                if (!tick(n, t, n.dmg())) return;
                n.fx("knock", t.getLocation(), t);
                n.knock(t, o, n.flat(), knock, 0.3);
            });
            return true;
        });

        // 307 공기 풍선 (AirBalloon): balloon lobbed at +0.1 s (0.4 blocks/tick, gravity 0.02, ≤ 14 blocks); it bursts on an enemy,
        // the ground, a wall or at the end: enemies within 1.8 take damage + float up 0.45 (impact = burst + updraft)
        m.put("air_balloon", n -> {
            double speed = n.d("speed", 0.4), range = n.d("range", 14), r = n.d("r", 1.8);
            n.later(1, () -> {
                Vector dir = n.look().add(new Vector(0, 0.2, 0)).normalize();
                boolean[] burst = {false};
                n.shotFrom(n.hand(), dir, speed, range, "projectile").radius(0.5).gravity(0.02)
                    .hit((t, p) -> { burst[0] = true; balloon(n, p, r); return false; })
                    .end((p, wall) -> { if (!burst[0]) balloon(n, p, r); })
                    .fire();
            });
            return true;
        });

        // ================================================================== 중급
        // 308 연줄 (KiteTether): at 0.15 s a kite catches the target (50 %); then n.carry (고정 movement_lock, 3.85 s): held 1.2 above the
        // ground and dragged so it never gets more than 4.5 blocks from the caster (artifact CC reduction / cleanse end it early; bosses get
        // the status but are not carried); tug pulses at 0.62 + 0.45k s (k 0..6, 10 % each); the kite tears at 3.85 s (60 %).
        m.put("kite_tether", n -> {
            LivingEntity t = pick(n, 14);
            if (t == null) return false;
            double h = n.d("height", 1.2), leash = n.d("leash", 4.5);
            n.later(2, () -> {
                if (!alive(t) || t.getWorld() != n.pl.getWorld()) return;
                if (!tick(n, t, n.dmg(0.5))) return;
                n.fx("kite", t.getLocation(), t);
                n.fx("tether", Nova.chest(t), t);
                n.carry(t, 77, age -> {
                    if (n.pl.isDead() || t.getWorld() != n.pl.getWorld() || t.getLocation().distanceSquared(n.pl.getLocation()) > 24 * 24) return null;
                    Location tl = t.getLocation(), cl = n.pl.getLocation();
                    Vector away = tl.toVector().subtract(cl.toVector()).setY(0);
                    double d = away.length();
                    Location to = d > leash && d > 1e-3 ? cl.clone().add(away.multiply(leash / d)) : tl.clone();
                    to.setY(Telegraphs.ground(tl.clone().add(0, 0.3, 0)) + h);
                    t.setFallDistance(0);
                    return to;
                }, 0.6, null);
                for (int at : new int[]{12, 21, 30, 39, 48, 57, 66}) n.later(at - 1, () -> { if (alive(t)) tick(n, t, n.dmg(0.1)); });
                n.later(76, () -> { if (alive(t)) tick(n, t, n.dmg(0.6)); });
            });
            return true;
        });

        // 309 풍차 날개 (WindmillBlades): 6 s, 3 blades around the caster (follows them); from 0.45 s every 0.3 s (18 cuts) enemies
        // at 0.8..2.2 blocks (horizontal) and −0.5..2 high take 15 %
        m.put("windmill_blades", n -> {
            double in = n.d("inner", 0.8), out = n.d("outer", 2.2), each = n.d("each", 0.15);
            n.loop(118, age -> {
                if (n.pl.isDead()) return false;
                if (age >= 9 && (age - 9) % 6 == 0) for (LivingEntity e : ring(n, n.pl.getLocation(), in, out, -0.5, 2)) tick(n, e, n.dmg(each));
                return age < 117;
            }, null);
            return true;
        });

        // 310 역풍로 (HeadwindLane): lane fixed at the cast (z 0.8..10.8 along the facing, half width 1.25); every 0.5 s from 0.3 s
        // (10 ticks) enemies on it (−0.5..2.5 high) take 20 % and are pushed along +z (away from the caster)
        m.put("headwind_lane", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat();
            double each = n.d("each", 0.2), push = n.d("push", 0.45);
            for (int k = 0; k < 10; k++) {
                n.later(5 + 10 * k, () -> {
                    for (LivingEntity e : lane(n, o, f, 0.8, 10.8, 1.25, -0.5, 2.5)) {
                        if (!tick(n, e, n.dmg(each))) continue;
                        n.fx("hit", e.getLocation());
                        n.knock(e, e.getLocation().subtract(f), f, push, 0.05);
                    }
                });
            }
            return true;
        });

        // 311 홀씨 지뢰밭 (SeedMines): 6 seeds on a r 1.8 hexagon around 3 blocks ahead, planted 0.07 s apart, armed 0.55 s later, 10 s.
        // An enemy within 0.55 of a seed sets it off (pop, scoped to that seed): 0.05 s later r 1.2 damage + lift 0.75. Unused seeds fizzle.
        m.put("seed_mines", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat(), rt = right(f);
            double trig = n.d("trig", 0.55), r = n.d("r", 1.2);
            int life = n.i("life", 200);
            double[][] spot = {{0, 4.8}, {1.56, 3.9}, {1.56, 2.1}, {0, 1.2}, {-1.56, 2.1}, {-1.56, 3.9}};
            int[] sowAt = {0, 1, 3, 4, 6, 7};
            Location[] seed = new Location[6];
            int[] state = new int[6];                       // 0 waiting, 1 armed, 2 gone
            for (int k = 0; k < 6; k++) {
                Location s = Nova.ground(o.clone().add(rt.clone().multiply(spot[k][0])).add(f.clone().multiply(spot[k][1])).add(0, 1.5, 0));
                s.setYaw(0); s.setPitch(0);
                if (!n.plugin.protection().pair(n.pl, s)) { state[k] = 2; continue; }
                seed[k] = s;
            }
            n.loop(sowAt[5] + 11 + life + 2, age -> {
                boolean any = false;
                for (int k = 0; k < 6; k++) {
                    Location s = seed[k];
                    if (state[k] == 2 || s == null) continue;
                    any = true;
                    if (age == sowAt[k]) n.fx("sow", s);
                    if (age == sowAt[k] + 11) { state[k] = 1; n.fx("armed", s); }
                    if (state[k] != 1) continue;
                    if (age >= sowAt[k] + 11 + life) { state[k] = 2; n.fx("fizzle", s); continue; }
                    if (age % 2 != 0) continue;
                    if (ring(n, s, 0, trig, -0.5, 1.5).isEmpty()) continue;
                    state[k] = 2;
                    n.fx("pop", s);
                    n.later(0, () -> {
                        for (LivingEntity e : n.near(s.clone().add(0, 0.6, 0), r)) if (tick(n, e, n.dmg())) n.lift(e, 0.75);
                    });
                }
                return any;
            }, null);
            return true;
        });

        // 312 회오리 발차기 (CycloneKick): contact at 0.22 s: enemies 0.5..2.8 ahead, ±1.2 sideways, −0.5..2.5 high take damage + lift 0.9
        m.put("cyclone_kick", n -> {
            double up = n.d("up", 0.9);
            n.later(3, () -> {
                Location o = n.pl.getLocation();
                Vector f = n.flat();
                for (LivingEntity e : lane(n, o, f, 0.5, 2.8, 1.2, -0.5, 2.5)) if (tick(n, e, n.dmg())) n.knock(e, o, f, 0.15, up);
            });
            return true;
        });

        // 313 바람 톱 (AirSaw): saw disc at chest height, flat forward 0.1875 blocks/tick (3.75 m/s), 12 blocks (~3.2 s), passes through
        // enemies: anyone within 1.1 is cut every 0.25 s (35 %); breaking on a block / at the end = impact + r 1.5 50 %
        m.put("air_saw", n -> {
            double speed = n.d("speed", 0.1875), range = n.d("range", 12), each = n.d("each", 0.35);
            Vector f = n.flat();
            Location from = n.pl.getLocation().add(0, 1.2, 0).add(f.clone().multiply(0.9));
            var s = n.shotFrom(from, f, speed, range, "projectile").radius(1.1).rehit(5)
                .hit((t, p) -> { tick(n, t, n.dmg(each)); return true; })
                .end((p, wall) -> {
                    n.fx("impact", p);
                    for (LivingEntity e : n.near(p, 1.5)) tick(n, e, n.dmg(0.5));
                });
            s.maxLife = (int) Math.ceil(range / speed) + 10;
            s.fire();
            return true;
        });

        // ================================================================== 고급
        // 314 칼바람 장막 (ShearCurtain): released at 0.72 s, a 5 × 3.4 wind wall (centre 1.6 up, starts 0.9 ahead) moves flat 0.25 blocks/tick
        // for 11 blocks; every enemy the wall passes (|x| ≤ 2.5, −1.6..+1.8 around the centre) is cut once + nudged; on a block / at the end
        // the wall scatters (end) with a r 2 extra cut (40 %)
        m.put("shear_curtain", n -> {
            double speed = n.d("speed", 0.25), range = n.d("range", 11);
            n.later(13, () -> {
                Vector f = n.flat(), rt = right(f);
                Location from = n.pl.getLocation().add(0, 1.6, 0).add(f.clone().multiply(0.9));
                Set<UUID> done = new HashSet<>();
                var s = n.shotFrom(from, f, speed, range, "wall").radius(0.3)
                    .hit((t, p) -> true)
                    .step((p, d) -> {
                        for (LivingEntity e : n.near(p, 4.2)) {
                            if (done.contains(e.getUniqueId())) continue;
                            Vector v = e.getLocation().toVector().subtract(p.toVector());
                            double z = v.dot(f), x = Math.abs(v.dot(rt)), y = v.getY(), w = e.getWidth() / 2;
                            if (Math.abs(z) > 0.6 + w || x > 2.5 + w || y + e.getHeight() < -1.6 || y > 1.8) continue;
                            done.add(e.getUniqueId());
                            if (tick(n, e, n.dmg())) n.knock(e, e.getLocation().subtract(f), f, 0.35, 0.1);
                        }
                    })
                    .end((p, wall) -> {
                        n.fx("end", p);
                        for (LivingEntity e : n.near(p, 2)) tick(n, e, n.dmg(0.4));
                    });
                s.maxLife = (int) Math.ceil(range / speed) + 10;
                s.fire();
            });
            return true;
        });

        // 315 천 마리 종이학 (ThousandCranes): 3 s chant (moving > 0.3 blocks, taking damage, or losing the target breaks it: chant_break);
        // then the swarm at the target's feet: 8 pecks at 3.25 + 0.24k s on enemies within 2 of the swarm (12 %), finale at 5.1 s (60 %)
        m.put("thousand_cranes", n -> {
            LivingEntity t = pick(n, 20);
            if (t == null) return false;
            UUID id = n.pl.getUniqueId();
            Integer old = CHANTING.get(id);
            if (old != null && Bukkit.getCurrentTick() - old < 70) return refuse(n, "이미 영창 중이에요");
            double r = n.d("r", 2), move = n.d("move", 0.3);
            Location start = n.pl.getLocation();
            int began = Bukkit.getCurrentTick();
            CHANTING.put(id, began);
            boolean[] ok = {false};
            n.loop(60, age -> {
                boolean broken = n.pl.isDead() || n.pl.getWorld() != start.getWorld() || hdist(n.pl.getLocation(), start) > move
                    || HURT.getOrDefault(id, -1) >= began || !alive(t) || t.getWorld() != start.getWorld()
                    || t.getLocation().distanceSquared(start) > 26 * 26;
                if (broken) return false;
                if (age >= 59) { ok[0] = true; return false; }
                return true;
            }, () -> {
                CHANTING.remove(id, began);
                if (!ok[0]) { n.fx("chant_break", n.pl.getLocation(), n.pl); return; }
                Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
                n.fx("swarm", at);
                int[] peck = {5, 10, 15, 19, 24, 29, 34, 39};
                for (int k : peck) n.later(k - 1, () -> { for (LivingEntity e : n.near(at.clone().add(0, 1, 0), r)) tick(n, e, n.dmg(0.12)); });
                n.later(41, () -> { for (LivingEntity e : n.near(at.clone().add(0, 1, 0), r + 0.5)) tick(n, e, n.dmg(0.6)); });
            });
            return true;
        });

        // 316 바람 낙하산 (GustCanopy): rune at the target's feet; 0.35 s updraft (50 %), then n.carry (고정 movement_lock 3.15 s) lifts it
        // to +2.35 over 0.35 s and holds it over the rune until 3.5 s (10 % at 1.5 / 2.5 s); released, landing hit at 3.95 s (70 %).
        // Bosses get the status (engine rules) but are not lifted.
        m.put("gust_canopy", n -> {
            LivingEntity t = pick(n, 16);
            if (t == null) return false;
            double lift = n.d("lift", 2.35);
            Location base = Nova.ground(t.getLocation().add(0, 0.5, 0));
            n.fx("main", base);
            n.later(6, () -> {
                if (!alive(t) || t.getWorld() != base.getWorld()) return;
                if (!tick(n, t, n.dmg(0.5))) return;
                n.later(22, () -> tick(n, t, n.dmg(0.1)));
                n.later(42, () -> tick(n, t, n.dmg(0.1)));
                n.later(71, () -> tick(n, t, n.dmg(0.7)));
                // 공중 고정 = n.carry (고정 movement_lock 3.15 s): rises to +2.35 over 0.35 s, held over the rune, released (falls) when it ends
                n.carry(t, 63, age -> {
                    if (t.getWorld() != base.getWorld() || t.getLocation().distanceSquared(base) > 12 * 12) return null;
                    t.setFallDistance(0);
                    return base.clone().add(0, lift * Math.min(1, (age + 1) / 7.0), 0);
                }, 0.5, null);
            });
            return true;
        });

        // 317 회전목마 돌풍 (CarouselGale): r 5 zone at the aimed ground; 0.8..4.8 s enemies inside are held (n.carry = 고정) on the r 3.6
        // orbit and dragged clockwise seen from above (~1.1 rad/s; bosses get the status but are not carried); 8 ticks at 1.0 + 0.5k s (15 %); 4.8 s finale (60 %) + flung outward
        m.put("carousel_gale", n -> {
            Map<UUID, Boolean> ok = new HashMap<>();
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 5), orbit = n.d("orbit", 3.6);
            n.fx("main", c);
            Set<UUID> caught = new HashSet<>();
            n.loop(97, age -> {
                // an enemy entering the zone is caught once: n.carry (고정 movement_lock until 4.8 s) along the r 3.6 orbit, clockwise from above
                if (age >= 16 && age < 92 && age % 2 == 0) for (LivingEntity e : ring(n, c, 0, r + 0.5, -1.5, 4)) {
                    if (caught.contains(e.getUniqueId()) || !allowed(n, ok, e)) continue;
                    caught.add(e.getUniqueId());
                    n.carry(e, 96 - age, k -> {
                        Location l = e.getLocation();
                        double a = Math.atan2(l.getZ() - c.getZ(), l.getX() - c.getX()) + 0.12;
                        return new Location(c.getWorld(), c.getX() + orbit * Math.cos(a), l.getY(), c.getZ() + orbit * Math.sin(a));
                    }, 0.25, null);
                }
                if (age >= 20 && age <= 90 && (age - 20) % 10 == 0) for (LivingEntity e : ring(n, c, 0, r, -1.5, 4)) tick(n, e, n.dmg(0.15));
                if (age == 96) for (LivingEntity e : ring(n, c, 0, r + 0.5, -1.5, 4)) if (tick(n, e, n.dmg(0.6))) n.knock(e, c, n.flat(), 1.0, 0.4);
                return age < 96;
            }, null);
            return true;
        });

        // 318 칼날비 (SkyBladeRain): r 4 at the aimed ground; blades land every 0.25 s from 1.25 to 4.0 s (12 ticks, 10 %); 3.9 s great
        // blade at the centre: r 2 extra 60 %
        m.put("sky_blade_rain", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 4), each = n.d("each", 0.1);
            n.fx("main", c);
            for (int k = 0; k < 12; k++) n.later(24 + 5 * k, () -> { for (LivingEntity e : ring(n, c, 0, r, -1.5, 4)) tick(n, e, n.dmg(each)); });
            n.later(77, () -> { for (LivingEntity e : ring(n, c, 0, 2, -1.5, 4)) tick(n, e, n.dmg(0.6)); });
            return true;
        });

        // 319 진공 회랑 (VacuumCorridor): tunnel fixed at the cast (z 1..13, axis 1.2 up, r 1.6). 0.6..3.6 s enemies within 4 of the axis
        // are sucked onto it (≈ 5 m/s, n.pull: 닻·면역 skip, bosses scaled); every 0.25 s (12 ticks) enemies in the tunnel take 8 %; 3.6 s collapse 80 %
        m.put("vacuum_corridor", n -> {
            Map<UUID, Boolean> ok = new HashMap<>();
            Location o = n.pl.getLocation();
            Vector f = n.flat(), rt = right(f);
            double pull = n.d("pull", 0.25), each = n.d("each", 0.08);
            n.fx("main", o);
            n.loop(73, age -> {
                if (age >= 12 && age < 72) for (LivingEntity e : lane(n, o, f, 1, 13, 4, -1.5, 3.5)) {
                    if (!allowed(n, ok, e)) continue;
                    double x = e.getLocation().toVector().subtract(o.toVector()).dot(rt);
                    if (Math.abs(x) < 0.3) continue;
                    n.pull(e, e.getLocation().subtract(rt.clone().multiply(x)), Math.min(pull, Math.abs(x) * 0.25), 0);   // 끌기 to the axis (helper scales bosses)
                }
                if (age >= 12 && age <= 67 && (age - 12) % 5 == 0) for (LivingEntity e : lane(n, o, f, 1, 13, 1.6, -0.4, 2.8)) tick(n, e, n.dmg(each));
                if (age == 72) for (LivingEntity e : lane(n, o, f, 1, 13, 1.8, -0.6, 3)) tick(n, e, n.dmg(0.8));
                return age < 72;
            }, null);
            return true;
        });

        // ================================================================== 비전
        // 320 풍경 결계 (WindChimeWard): 8 s ward at the aimed ground, chimes at (±2, ±2) (body 2.2 up), order SE → NE → NW → SW.
        // 0.6..7.5 s: an enemy within 1.5 (horizontal) of a chime starts a chain there: that chime rings, then the next ones 0.25 s apart
        // (4 rings); each ring hits enemies within 2.5 of its chime (35 %). A new chain can start 0.5 s after the last ring.
        m.put("wind_chime_ward", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            c.setYaw(0); c.setPitch(0);
            double each = n.d("each", 0.35), ringR = n.d("ring", 2.5);
            double[][] pos = {{2, 2}, {2, -2}, {-2, -2}, {-2, 2}};
            Location[] foot = new Location[4];
            for (int k = 0; k < 4; k++) foot[k] = c.clone().add(pos[k][0], 0, pos[k][1]);
            n.fx("ward", c);
            int[] busyUntil = {0};
            n.loop(150, age -> {
                if (age < 12 || age < busyUntil[0] || age % 2 != 0) return age < 150;
                for (int k = 0; k < 4; k++) {
                    if (ring(n, foot[k], 0, 1.5, -1, 3.5).isEmpty()) continue;
                    for (int j = 0; j < 4; j++) {
                        Location f = foot[(k + j) % 4];
                        n.later(Math.max(0, 5 * j - 1), () -> {
                            n.fx("ring", f.clone().add(0, 2.2, 0));
                            for (LivingEntity e : ring(n, f, 0, ringR, -1, 3.5)) tick(n, e, n.dmg(each));
                        });
                    }
                    busyUntil[0] = age + 15 + 10;
                    break;
                }
                return age < 150;
            }, null);
            return true;
        });

        // 321 무풍지대 (DeadCalm): r 6 at the caster's feet; calm 0.8..5.8 s: hostile vanilla projectiles inside (r 6.4) stop in the air
        // (released with a small drop at the end), enemies inside are slowed 50 % (status engine) and dashes / leaps are stopped (halt);
        // pressure ticks at 1.3 + k s (5 ticks, 20 %), collapse at 6.05 s (50 %)
        m.put("dead_calm", n -> {
            Location c = n.pl.getLocation();
            double r = n.d("r", 6), each = n.d("each", 0.2);
            n.fx("main", c);
            Map<UUID, Object[]> held = new LinkedHashMap<>();     // projectile -> {Projectile, Location, gravity}
            Runnable release = () -> {
                for (Object[] h : held.values()) {
                    Projectile pr = (Projectile) h[0];
                    FROZEN.remove(pr.getUniqueId());
                    if (!pr.isValid()) continue;
                    pr.setGravity((Boolean) h[2]);
                    pr.setVelocity(new Vector(0, -0.1, 0));
                }
                held.clear();
            };
            n.loop(122, age -> {
                if (age >= 16 && age < 116) {
                    for (Entity x : c.getWorld().getNearbyEntities(c.clone().add(0, 2, 0), r + 0.4, 4, r + 0.4)) {
                        if (!(x instanceof Projectile pr) || held.containsKey(pr.getUniqueId()) || !calmable(n, pr)) continue;
                        if (hdist(pr.getLocation(), c) > r + 0.4) continue;
                        held.put(pr.getUniqueId(), new Object[]{pr, pr.getLocation(), pr.hasGravity()});
                        FROZEN.add(pr.getUniqueId());
                        pr.setGravity(false);
                    }
                    for (var it = held.values().iterator(); it.hasNext(); ) {
                        Object[] h = it.next();
                        Projectile pr = (Projectile) h[0];
                        if (!pr.isValid()) { FROZEN.remove(pr.getUniqueId()); it.remove(); continue; }
                        pr.setVelocity(new Vector());
                        Location keep = (Location) h[1];
                        if (pr.getLocation().distanceSquared(keep) > 0.01) pr.teleport(keep);
                    }
                    // 둔화 (slow 50 %, refreshed while inside) through the status engine; a dash / leap in progress is stopped (halt)
                    for (LivingEntity e : ring(n, c, 0, r, -2, 5)) {
                        if (age % 10 == 6) n.status(e, "slow:24:0.5");
                        Vector v = e.getVelocity();
                        if (Math.hypot(v.getX(), v.getZ()) > 0.3 && n.movable(e)) n.halt(e);
                    }
                }
                if (age == 116) release.run();
                if (age >= 26 && age <= 106 && (age - 26) % 20 == 0) for (LivingEntity e : ring(n, c, 0, r, -2, 5)) tick(n, e, n.dmg(each));
                if (age == 121) for (LivingEntity e : ring(n, c, 0, r, -2, 5)) tick(n, e, n.dmg(0.5));
                return age < 121;
            }, release);
            return true;
        });

        // 322 거대 바람개비 (GreatPinwheel): at the aimed ground; 0.8..6.8 s enemies within 5.6 are pulled to the centre (~1.5 m/s, stop
        // inside 1 block, n.pull = 닻·면역 skip, bosses scaled); cuts every 0.25 s from 0.9 s (24 ticks, r 4, 6 %); 6.8 s blades burst (60 %) + flung outward
        m.put("great_pinwheel", n -> {
            Map<UUID, Boolean> ok = new HashMap<>();
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 4), pull = n.d("pull", 0.075), each = n.d("each", 0.06);
            n.fx("main", c);
            n.loop(137, age -> {
                if (age >= 16 && age < 136) for (LivingEntity e : ring(n, c, 0, 5.6, -1.5, 4)) {
                    if (!allowed(n, ok, e)) continue;
                    Location l = e.getLocation();
                    double dx = c.getX() - l.getX(), dz = c.getZ() - l.getZ(), d = Math.hypot(dx, dz);
                    if (d < 1) continue;
                    n.pull(e, c, pull, 0);
                }
                if (age >= 18 && age <= 133 && (age - 18) % 5 == 0) for (LivingEntity e : ring(n, c, 0, r, -1.5, 4)) tick(n, e, n.dmg(each));
                if (age == 136) for (LivingEntity e : ring(n, c, 0, r + 0.5, -1.5, 4)) if (tick(n, e, n.dmg(0.6))) n.knock(e, c, n.flat(), 1.0, 0.35);
                return age < 136;
            }, null);
            return true;
        });

        // 323 연날리기 축제 (KiteFestival): r 4 at the aimed ground; 5 dives land at 1.72 / 2.42 / 3.12 / 3.82 / 4.52 s, each on the next
        // enemy in the zone (round robin, nearest first): r 1.5 around it (30 %); 5.5 s all 5 kites dive on the centre: r 3 (60 %)
        m.put("kite_festival", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 4), each = n.d("each", 0.3);
            n.fx("main", c);
            int[] land = {34, 48, 62, 76, 90};
            int[] turn = {0};
            for (int at : land) n.later(at - 1, () -> {
                List<LivingEntity> in = ring(n, c, 0, r, -1.5, 5);
                if (in.isEmpty()) return;
                LivingEntity t = in.get(turn[0]++ % in.size());
                Location p = t.getLocation();
                for (LivingEntity e : n.near(p.clone().add(0, 0.8, 0), 1.5)) tick(n, e, n.dmg(each));
            });
            n.later(109, () -> { for (LivingEntity e : ring(n, c, 0, 3, -1.5, 5)) tick(n, e, n.dmg(0.6)); });
            return true;
        });

        // ================================================================== 궁극
        // 324 천공의 방아 (SkyMill): r 8 at the aimed ground (12 s visual). 3.0..8.0 s ground blades grind every 0.5 s (11 ticks, 6 %);
        // 7.2..8.0 s suction to the centre (n.pull); 8.0 s lift (40 %, n.lift 1.0); 9.0 s slam (100 % + 기절 1 s, airborne: 중력 too;
        // boss rules come from the status engine)
        m.put("sky_mill", n -> {
            Map<UUID, Boolean> ok = new HashMap<>();
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 8), each = n.d("each", 0.06);
            n.fx("main", c);
            n.loop(181, age -> {
                if (age >= 60 && age <= 160 && (age - 60) % 10 == 0) for (LivingEntity e : ring(n, c, 0, r, -2, 6)) tick(n, e, n.dmg(each));
                if (age >= 144 && age < 160) for (LivingEntity e : ring(n, c, 0, r + 1, -2, 6)) {
                    if (!allowed(n, ok, e)) continue;
                    Location l = e.getLocation();
                    double dx = c.getX() - l.getX(), dz = c.getZ() - l.getZ(), d = Math.hypot(dx, dz);
                    if (d < 1.2) continue;
                    n.pull(e, c, 0.35, 0);
                }
                if (age == 160) for (LivingEntity e : ring(n, c, 0, r + 0.5, -2, 6)) if (tick(n, e, n.dmg(0.4))) { n.lift(e, 1.0); e.setFallDistance(0); }
                if (age == 180) for (LivingEntity e : ring(n, c, 0, r + 0.5, -2, 10)) {
                    if (!tick(n, e, n.dmg())) continue;
                    n.status(e, e.isOnGround() ? "stun:20" : "gravity:20,stun:20");   // 중력 drags the airborne down; boss rules: engine
                }
                return age < 180;
            }, null);
            return true;
        });

        // 325 창공 절단 (SkySever): line fixed at the cast, z 3..33 along the facing, |x| ≤ 1.5. 6.3 s blade falls: every enemy on the line
        // (−2..+5 high) 100 %; 6.9 + 0.27k s (k 0..7) scar bursts near → far, r 2.2 each 12 %; 9.3 s the rift closes: whole scar 80 % + up 0.6
        m.put("sky_sever", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat();
            double half = n.d("width", 3) / 2, each = n.d("each", 0.12);
            n.fx("main", o);
            n.later(125, () -> { for (LivingEntity e : lane(n, o, f, 3, 33, half, -2, 5)) tick(n, e, n.dmg()); });
            for (int k = 0; k < 8; k++) {
                Location p = o.clone().add(f.clone().multiply(3 + 30 * (k + 0.5) / 8));
                n.later(137 + (int) Math.round(5.4 * k), () -> {
                    Location g = Nova.ground(p.clone().add(0, 2, 0));
                    for (LivingEntity e : ring(n, g, 0, 2.2, -2, 5)) tick(n, e, n.dmg(each));
                });
            }
            n.later(185, () -> { for (LivingEntity e : lane(n, o, f, 3, 33, half, -2, 5)) if (tick(n, e, n.dmg(0.8))) n.lift(e, 0.6); });
            return true;
        });
    }

    // ------------------------------------------------------------------ helpers
    /** True right of a horizontal facing. */
    private static Vector right(Vector f) { return new Vector(-f.getZ(), 0, f.getX()); }
    private static double hdist(Location a, Location b) { return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()); }
    private static boolean alive(LivingEntity t) { return t != null && t.isValid() && !t.isDead(); }

    /** Spell damage that ignores the target's vanilla hurt cooldown, so the timed multi-hits land on their beat. */
    private static boolean tick(Nova n, LivingEntity e, double amount) {
        if (e != null && e.getNoDamageTicks() > 0) e.setNoDamageTicks(0);
        return n.hit(e, amount);
    }
    /** May this spell move `e` (PvP / region rules: one zero-damage probe per target per cast)? */
    private static boolean allowed(Nova n, Map<UUID, Boolean> ok, LivingEntity e) {
        return ok.computeIfAbsent(e.getUniqueId(), k -> n.hit(e, 0));
    }

    /** Refused cast: message, mana back (row param `mana`), cooldown cleared. */
    private static boolean refuse(Nova n, String msg) {
        n.warn(msg); n.refund(n.d("mana", 0)); n.clearCooldown(); return false;
    }

    /** ENTITY spells: the Bridge's target (or the enemy nearest the aim point); null (warned + mana back) when none in range. */
    private static LivingEntity pick(Nova n, double range) {
        LivingEntity t = n.target();
        if (t == null || !n.enemy(t)) {
            var l = n.near(n.aim(), 2.5, 1);
            t = l.isEmpty() ? null : l.get(0);
        }
        if (t == null || t.getWorld() != n.pl.getWorld() || t.getLocation().distanceSquared(n.pl.getLocation()) > (range + 2) * (range + 2)) {
            refuse(n, "마법을 걸 대상이 없어요");
            return null;
        }
        return t;
    }

    /** Enemies whose body is within the horizontal ring [in, out] around `c` (by the body edge) and dyLo..dyHi above c. Nearest first. */
    private static List<LivingEntity> ring(Nova n, Location c, double in, double out, double dyLo, double dyHi) {
        var res = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(c.clone().add(0, (dyLo + dyHi) / 2, 0), out + Math.max(Math.abs(dyLo), Math.abs(dyHi)) + 2, 48)) {
            Location l = e.getLocation();
            double d = hdist(l, c), w = e.getWidth() / 2, dy = l.getY() - c.getY();
            if (d - w > out || d + w < in || dy + e.getHeight() < dyLo || dy > dyHi) continue;
            res.add(e);
        }
        return res;
    }

    /** Enemies in the box z0..z1 along `f` from `o`, |x| ≤ half (+ body half width), dyLo..dyHi above o. */
    private static List<LivingEntity> lane(Nova n, Location o, Vector f, double z0, double z1, double half, double dyLo, double dyHi) {
        Vector rt = right(f);
        Location mid = o.clone().add(f.clone().multiply((z0 + z1) / 2)).add(0, (dyLo + dyHi) / 2, 0);
        var res = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(mid, (z1 - z0) / 2 + half + Math.max(Math.abs(dyLo), Math.abs(dyHi)) + 2, 48)) {
            Vector v = e.getLocation().toVector().subtract(o.toVector());
            double z = v.dot(f), x = Math.abs(v.dot(rt)), dy = v.getY(), w = e.getWidth() / 2;
            if (z < z0 - w || z > z1 + w || x > half + w || dy + e.getHeight() < dyLo || dy > dyHi) continue;
            res.add(e);
        }
        return res;
    }

    /** Enemies in a horizontal cone in front of the caster (feet origin, reach r, half angle in degrees), in sight, nearest first. */
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

    /** 302: the pinwheel sticks at `p` (in `t` when it hit an enemy) and spins 3 s: 6 cuts at 0.5 s intervals within r. */
    private static void pin(Nova n, Location p, LivingEntity t, double r) {
        n.fx("impact", p);
        if (t != null) tick(n, t, n.dmg(0.5));
        for (int k = 1; k <= 6; k++) n.later(10 * k - 1, () -> { for (LivingEntity e : n.near(p, r)) tick(n, e, n.dmg(0.1)); });
    }

    /** 307: balloon burst at `p`: r damage + small lift. */
    private static void balloon(Nova n, Location p, double r) {
        n.fx("impact", p);
        for (LivingEntity e : n.near(p, r)) if (tick(n, e, n.dmg())) n.lift(e, 0.45);
    }

    /** 321: a hostile vanilla projectile the calm may stop (not pearls / hooks / grounded arrows / our own or allies' shots / other holds). */
    private static boolean calmable(Nova n, Projectile pr) {
        if (pr instanceof org.bukkit.entity.EnderPearl || pr instanceof org.bukkit.entity.FishHook || !pr.isValid()) return false;
        if (pr instanceof org.bukkit.entity.AbstractArrow a && (a.isInBlock() || a.isOnGround())) return false;
        if (FROZEN.contains(pr.getUniqueId()) || NovaMid2.HELD.contains(pr.getUniqueId())) return false;
        if (!n.plugin.protection().pair(n.pl, pr.getLocation())) return false;
        if (!(pr.getShooter() instanceof Entity shooter)) return false;
        if (shooter.getUniqueId().equals(n.pl.getUniqueId())) return false;
        return n.enemy(shooter);
    }

    static void enable(MagicMythicPlugin plugin) {
        CHANTING.clear(); HURT.clear(); FROZEN.clear();
        if (listening != plugin) { Bukkit.getPluginManager().registerEvents(new Hooks(), plugin); listening = plugin; }
    }
    private static MagicMythicPlugin listening;

    static final class Hooks implements Listener {
        /** 315: damage taken while chanting breaks the chant. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void hurt(EntityDamageEvent e) {
            if (e.getFinalDamage() > 0 && CHANTING.containsKey(e.getEntity().getUniqueId())) HURT.put(e.getEntity().getUniqueId(), Bukkit.getCurrentTick());
        }
        @EventHandler public void quit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); CHANTING.remove(id); HURT.remove(id); }
    }
}
