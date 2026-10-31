package school.chacademia.magicmythic;

import java.util.*;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** chanova types for the 150-spell set (301~450), element earth. Rows: tools/nova150/earth.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Earth {
    private Nova150Earth() {}

    static void register(Map<String, NovaMech.Impl> m) {
        // ================================================================== 초급 376~382
        // 376 모래 채찍 (SandLash): `lash` attached at cast; 2 sweeps of the 2.6 m / ±60° front cone at +0.13 s (3 t) and +0.47 s (9 t)
        m.put("sand_lash", n -> {
            double r = n.d("r", 2.6), half = n.d("half", 60);
            for (int at : new int[]{3, 9}) n.later(at, () -> { for (LivingEntity e : cone(n, r, half)) n.hit(e, n.dmg()); });
            return true;
        });

        // 377 모래 알갱이 (SandPellet): 3 pellets (projectile) from the hand at 0 / 0.15 / 0.30 s along the current look, 16 blocks;
        // each hits its first enemy (impact at the hit / wall point)
        m.put("sand_pellet", n -> {
            double speed = n.d("speed", 1.3), range = n.d("range", 16);
            for (int k = 0; k < 3; k++) n.later(k * 3, () -> n.shotFrom(n.hand(), n.look(), speed, range, "projectile").radius(0.45)
                .hit((t, p) -> { n.hit(t, n.dmg()); return false; })
                .end((p, wall) -> n.fx("impact", p))
                .fire());
            return true;
        });

        // 378 화석 송곳니 (FossilFang): `main` static on the aimed ground at t 0 (warning), jaws snap at +0.50 s (10 t): r 1.3
        m.put("fossil_fang", n -> {
            Location at = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, at)) return false;
            double r = n.d("r", 1.3);
            n.fx("main", at);
            n.later(10, () -> { for (LivingEntity e : n.near(at.clone().add(0, 0.8, 0), r + 0.6)) if (onSpot(e, at, r, 2.5)) n.hit(e, n.dmg()); });
            return true;
        });

        // 379 점토 덩이 (ClayGlob): thrown clay (projectile, gravity arc) 18 blocks; first enemy: damage + slow 3 s with `slowed`
        // attached to its feet; `impact` splat wherever it stops
        m.put("clay_glob", n -> {
            double speed = n.d("speed", 0.9), g = n.d("gravity", 0.035);
            int ticks = n.i("ticks", 60);
            Vector dir = n.look().add(new Vector(0, 0.12, 0));
            n.shotFrom(n.hand(), dir, speed, 18, "projectile").radius(0.5).gravity(g)
                .hit((t, p) -> {
                    if (n.hit(t, n.dmg())) { n.status(t, "slow:" + ticks + ":0.3"); n.fx("slowed", t.getLocation(), t); }
                    return false;
                })
                .end((p, wall) -> n.fx("impact", p))
                .fire();
            return true;
        });

        // 380 자철 조각 (LodestoneShard): shard 16 blocks; embed (impact) = hit 1; +0.45 s (9 t) pulled out = hit 2 (70 %) on the
        // same enemy; then `returnShard` flies back to the hand (homing on the caster), 50 % to other enemies it passes; `catch` at the hand
        m.put("lodestone_shard", n -> {
            double speed = n.d("speed", 1.2), range = n.d("range", 16), pull = n.d("pull", 0.7), back = n.d("back", 0.5);
            LivingEntity[] got = {null};
            n.shotFrom(n.hand(), n.look(), speed, range, "projectile").radius(0.45)
                .hit((t, p) -> { got[0] = t; n.hit(t, n.dmg()); return false; })
                .end((p, wall) -> {
                    n.fx("embed", p);
                    n.later(9, () -> {
                        LivingEntity t = got[0];
                        Location from = p;
                        if (t != null && t.isValid() && !t.isDead() && t.getWorld() == p.getWorld()) { n.hit(t, n.dmg(pull)); from = Nova.chest(t); }
                        if (from.getWorld() != n.pl.getWorld()) return;
                        double dist = from.distance(n.hand());
                        Vector to = n.hand().toVector().subtract(from.toVector());
                        if (to.lengthSquared() < 1e-4) { n.fx("catch", n.hand()); return; }
                        var s = n.shotFrom(from, to, 1.1, dist + 0.3, "returnShard").radius(0.5).homing(n.pl, 0.35).ghost()
                            .hit((e, q) -> { if (e != t) n.hit(e, n.dmg(back)); return true; })
                            .end((q, w) -> n.fx("catch", n.hand()));
                        s.maxLife = 60;
                        s.fire();
                    });
                })
                .fire();
            return true;
        });

        // 381 흙먼지 발구르기 (SandStomp): `stomp` attached at cast; stomp at +0.15 s (3 t): r 2 around the feet, damage + small lift
        m.put("sand_stomp", n -> {
            double r = n.d("r", 2), up = n.d("up", 0.5);
            n.later(3, () -> {
                Location c = n.pl.getLocation();
                for (LivingEntity e : n.near(c.clone().add(0, 0.8, 0), r + 1)) {
                    if (!onSpot(e, c, r, 2)) continue;
                    if (n.hit(e, n.dmg())) n.knock(e, c, n.flat(), 0.1, up);
                }
            });
            return true;
        });

        // 382 보석 파편 (GemSplinter): 3 gems (gemTopaz / gemGarnet / gemEmerald) at once, -15° / 0° / +15°, 14 blocks; each hits once
        m.put("gem_splinter", n -> {
            double speed = n.d("speed", 1.2), range = n.d("range", 14), yaw = Math.toRadians(n.d("yaw", 15));
            String[] sys = {"gemTopaz", "gemGarnet", "gemEmerald"};
            for (int k = 0; k < 3; k++) {
                double a = (k - 1) * yaw;
                n.shotFrom(n.hand(), n.look().rotateAroundY(-a), speed, range, sys[k]).radius(0.45)
                    .hit((t, p) -> { n.hit(t, n.dmg()); return false; })
                    .end((p, wall) -> n.fx("impact", p))
                    .fire();
            }
            return true;
        });

        // ================================================================== 중급 383~388
        // 383 모래시계 구덩이 (HourglassPit): `main` static r 3 on the aimed ground for 4.9 s. Slow inside grows at 0.4 / 1.4 / 2.3 s
        // (20 / 40 / 60 %), bury at 3.0 s (60 t): damage + root 1 s; release at 4.0 s (80 t): 50 % + small lift
        m.put("hourglass_pit", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 3);
            n.fx("main", c);
            n.loop(80, age -> {
                if (age >= 8 && age < 60 && age % 5 == 0) {
                    double pw = age < 28 ? 0.2 : age < 46 ? 0.4 : 0.6;
                    for (LivingEntity e : inside(n, c, r)) n.status(e, "slow:10:" + pw);
                }
                if (age == 60) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, n.dmg())) n.status(e, "root:20");
                if (age == 80) { for (LivingEntity e : inside(n, c, r + 0.5)) if (n.hit(e, n.dmg(0.5))) n.knock(e, c, n.flat(), 0.2, 0.3); return false; }
                return true;
            }, null);
            return true;
        });

        // 384 모래 분수 (SandSpout): `main` static at the target's feet at t 0, spout at +0.35 s (7 t): r 1.4 there -> damage + lift
        m.put("sand_spout", n -> {
            LivingEntity t = pick(n, 14, n.d("mana", 18));
            if (t == null) return false;
            double r = n.d("r", 1.4), up = n.d("up", 0.9);
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            n.fx("main", at);
            n.later(7, () -> { for (LivingEntity e : inside(n, at, r)) if (n.hit(e, n.dmg())) { e.setFallDistance(0); n.lift(e, up); } });
            return true;
        });

        // 385 자석 손 (MagnetPalm): `cast` attached; enemies in the 5.5 m / ±35° cone at +0.3 s (6 t) are pulled to 1.3 m in front of
        // the hand until 1.2 s (n.pull: anchored / immune targets stay, bosses scaled by the helper); clamp at 1.2 s (24 t): damage to the caught ones still within 8 blocks
        m.put("magnet_palm", n -> {
            double r = n.d("r", 5.5), half = n.d("half", 35), pull = n.d("pull", 0.35);
            List<LivingEntity> got = new ArrayList<>();
            n.loop(24, age -> {
                if (age == 6) for (LivingEntity e : cone(n, r, half)) if (n.hit(e, 0)) got.add(e);
                if (age >= 6 && age < 24) {
                    Location to = n.pl.getLocation().add(n.flat().multiply(1.3));
                    for (LivingEntity e : got) {
                        if (!e.isValid() || e.isDead() || e.getWorld() != to.getWorld()) continue;
                        double d = Math.hypot(to.getX() - e.getLocation().getX(), to.getZ() - e.getLocation().getZ());
                        if (d < 0.9) { n.halt(e); continue; }
                        n.pull(e, to, Math.min(pull, d * 0.3), 0);
                    }
                }
                if (age == 24) {
                    for (LivingEntity e : got) if (e.isValid() && e.getWorld() == n.pl.getWorld() && e.getLocation().distanceSquared(n.pl.getLocation()) < 64) n.hit(e, n.dmg());
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 386 모래 두더지 (SandBurrow): `burrow` (projectile) hugs the ground from the caster's feet to the target (homing),
        // first enemy touched: damage + root 1.6 s, `bind` at its feet
        m.put("sand_burrow", n -> {
            LivingEntity t = pick(n, 14, n.d("mana", 18));
            if (t == null) return false;
            double speed = n.d("speed", 0.7), turn = n.d("turn", 0.25);
            int ticks = n.i("ticks", 32);
            Location from = Nova.ground(n.pl.getLocation().add(n.flat().multiply(0.8)).add(0, 1, 0)).add(0, 0.05, 0);
            Vector dir = Nova.flat(t.getLocation().toVector().subtract(from.toVector()));
            double dist = Math.sqrt(t.getLocation().distanceSquared(from));
            n.shotFrom(from, dir, speed, dist + 4, "burrow").radius(0.8).ground().homing(t, turn)
                .hit((e, p) -> {
                    n.fx("bind", Nova.ground(e.getLocation().add(0, 0.5, 0)));
                    if (n.hit(e, n.dmg())) n.status(e, "root:" + ticks);
                    return false;
                })
                .fire();
            return true;
        });

        // 387 진흙 폭탄 (MudBomb): thrown mud (projectile, gravity arc) 20 blocks; wherever it stops: `impact`, r 2.2 damage,
        // then a 2.6 s mud pool (r 2, 0.05~2.6 s): slow 40 % on enemies inside
        m.put("mud_bomb", n -> {
            double speed = n.d("speed", 0.9), g = n.d("gravity", 0.035), r = n.d("r", 2.2);
            Vector dir = n.look().add(new Vector(0, 0.12, 0));
            n.shotFrom(n.hand(), dir, speed, 20, "projectile").radius(0.5).gravity(g)
                .hit((t, p) -> false)
                .end((p, wall) -> {
                    Location c = Nova.ground(p.clone().add(0, 0.5, 0));
                    if (p.getY() - c.getY() > 2.5) c = p;
                    n.fx("impact", p);
                    for (LivingEntity e : n.near(p, r)) n.hit(e, n.dmg());
                    Location pool = c;
                    n.loop(52, age -> {
                        if (age % 10 == 1) for (LivingEntity e : inside(n, pool, 2)) n.status(e, "slow:15:0.4");
                        return age < 52;
                    }, null);
                })
                .fire();
            return true;
        });

        // 388 바위 팽이 (StoneTop): `form` at the start, `top` (projectile) rolls on the ground 4 s at 0.16 blocks/tick, leaning toward
        // the enemy nearest the aim; each contact (same enemy every 0.8 s): `clash` + damage + bounce away; `crumble` at the end / wall
        m.put("stone_top", n -> {
            double speed = n.d("speed", 0.16), turn = n.d("turn", 0.06);
            int ticks = n.i("ticks", 80);
            Vector f = n.flat();
            Location from = Nova.ground(n.pl.getLocation().add(f.clone().multiply(1.2)).add(0, 1, 0)).add(0, 0.05, 0);
            n.fx("form", from);
            var s = n.shotFrom(from, f, speed, speed * ticks, "top").radius(0.8).ground().rehit(16)
                .hit((t, p) -> {
                    if (n.hit(t, n.dmg())) n.knock(t, p, f, 0.6, 0.25);
                    n.fx("clash", Nova.chest(t));
                    return true;
                })
                .end((p, wall) -> n.fx("crumble", p));
            var aimed = n.near(n.aim(), 8, 1);
            if (!aimed.isEmpty()) s.homing(aimed.get(0), turn);
            s.maxLife = ticks + 5;
            s.fire();
            return true;
        });

        // ================================================================== 고급 389~394
        // 389 유사 파도 (QuicksandTide): `main` static at the feet (caster facing). Wave front leaves z = 1 at 0.35 s (7 t), 5.55 m/s
        // to z = 11 (2.15 s, 43 t), width 5.2: each enemy when the front reaches it -> damage + root 1.2 s, then slow 3 s.
        // Collapse at 2.15 s: r 2 at the end, enemies not hit yet
        m.put("quicksand_tide", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            double half = n.d("half", 2.6);
            n.fx("main", o);
            Set<UUID> done = sweep(n, o, f, 7, 43, 1, 5.55 / 20, half, (e, d) -> {
                if (n.hit(e, n.dmg())) { n.status(e, "root:24"); n.later(24, () -> n.status(e, "slow:60:0.35")); }
            });
            n.later(43, () -> {
                Location end = o.clone().add(f.clone().multiply(11));
                for (LivingEntity e : inside(n, end, 2)) if (done.add(e.getUniqueId()) && n.hit(e, n.dmg())) n.status(e, "slow:60:0.35");
            });
            return true;
        });

        // 390 화석 뱀뼈 (FossilWyrm): `main` static at the feet (caster facing). Head erupts at z = 2 at 0.5 s (10 t), 8 m/s to z = 12.7
        // (1.83 s, 37 t), lane width 2.5: damage + small lift as the head passes. Dive burst at 1.83 s: r 2 at the end, 50 %
        m.put("fossil_wyrm", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            double half = n.d("half", 1.25);
            n.fx("main", o);
            sweep(n, o, f, 10, 37, 2, 8.0 / 20, half, (e, d) -> { if (n.hit(e, n.dmg())) n.knock(e, o, f, 0.15, 0.4); });
            n.later(37, () -> {
                Location end = o.clone().add(f.clone().multiply(12.7));
                for (LivingEntity e : inside(n, end, 2)) if (n.hit(e, n.dmg(0.5))) n.knock(e, end, f, 0.3, 0.3);
            });
            return true;
        });

        // 391 석화의 손 (PetrifyTouch): melee target (3.5 blocks). `strike` attached at cast; palm lands at +0.12 s (2 t): 35 % + stun 3 s
        // (bosses: slow) and `petrify` static at its feet; shatter at petrify + 3.0 s (62 t): full damage
        m.put("petrify_touch", n -> {
            double reach = n.d("reach", 3.5), touch = n.d("touch", 0.35);
            LivingEntity t = pick(n, reach, n.d("mana", 60));
            if (t == null) return false;
            int ticks = n.i("ticks", 60);
            n.later(2, () -> {
                if (!t.isValid() || t.isDead() || t.getWorld() != n.pl.getWorld() || t.getLocation().distanceSquared(n.pl.getLocation()) > (reach + 1.5) * (reach + 1.5)) return;
                if (!n.hit(t, n.dmg(touch))) return;
                n.status(t, "stun:" + ticks);
                n.fx("petrify", t.getLocation());
                n.later(ticks, () -> n.hit(t, n.dmg()));
            });
            return true;
        });

        // 392 모래관 (SandCoffin): `main` static at the target's feet at t 0; coffin shuts at 0.5 s (10 t): enemy still on the spot
        // (r 1.6) is held 2 s (stun, bosses slow); crush at 2.6 s (52 t): full damage on the spot (r 1.8), 50 % to others within 3
        m.put("sand_coffin", n -> {
            LivingEntity t = pick(n, 16, n.d("mana", 55));
            if (t == null) return false;
            int ticks = n.i("ticks", 40);
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            n.fx("main", at);
            n.later(10, () -> { if (onSpot(t, at, 1.6, 3) && n.hit(t, 0)) n.status(t, "stun:" + ticks); });
            n.later(52, () -> {
                for (LivingEntity e : inside(n, at, 3)) {
                    boolean in = onSpot(e, at, 1.8, 3);
                    n.hit(e, n.dmg(in ? 1 : 0.5));
                }
            });
            return true;
        });

        // 393 쇳가루 폭풍 (IronDustStorm): `main` static r 4 on the aimed ground. 0.4~3.4 s (8~67 t): enemies inside (checked every
        // 0.25 s) are caught by n.carry (고정 status; artifacts / cleanse cut it short, bosses keep the status but are not moved) and swirled
        // around the eye, slowly drawn in; 8 collisions every 0.33 s from 0.75 s: an enemy with another enemy within 2.5 takes 22 %, alone 12 %;
        // dispersal at 3.4 s (68 t): 50 % + push out
        m.put("iron_dust_storm", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 4);
            n.fx("main", c);
            Set<Integer> hits = new HashSet<>();
            for (int k = 0; k < 8; k++) hits.add(15 + (int) Math.round(k * 6.6));
            Set<UUID> caught = new HashSet<>();
            n.loop(68, age -> {
                if (age >= 8 && age < 62 && age % 5 == 3) {
                    for (LivingEntity e : inside(n, c, r)) {
                        if (caught.contains(e.getUniqueId()) || !n.hit(e, 0)) continue;
                        caught.add(e.getUniqueId());
                        Location l0 = e.getLocation();
                        double a0 = Math.atan2(l0.getZ() - c.getZ(), l0.getX() - c.getX()), d0 = Math.max(1, Math.hypot(l0.getX() - c.getX(), l0.getZ() - c.getZ()));
                        n.carry(e, 67 - age, a -> {
                            double ang = a0 + a * 0.12, rad = Math.max(1, d0 - a * 0.03);
                            return new Location(c.getWorld(), c.getX() + Math.cos(ang) * rad, e.getLocation().getY(), c.getZ() + Math.sin(ang) * rad);
                        }, 0.45, null);
                    }
                }
                if (hits.contains(age)) {
                    List<LivingEntity> all = inside(n, c, r);
                    for (LivingEntity e : all) {
                        LivingEntity mate = null; double best = 2.5 * 2.5;
                        for (LivingEntity o : all) {
                            if (o == e) continue;
                            double d2 = o.getLocation().distanceSquared(e.getLocation());
                            if (d2 < best) { best = d2; mate = o; }
                        }
                        n.hit(e, n.dmg(mate != null ? 0.22 : 0.12));
                    }
                }
                if (age == 68) { for (LivingEntity e : inside(n, c, r + 0.5)) if (n.hit(e, n.dmg(0.5))) n.knock(e, c, n.flat(), 0.55, 0.25); return false; }
                return true;
            }, null);
            return true;
        });

        // 394 모래 폭포 (SandFall): `main` static on the aimed ground. 0.5~2.5 s: every 0.25 s (10, 15 .. 50 t) r 2: 12 % + slow;
        // final impact at 2.5 s (50 t): 60 % r 2.4 + small push
        m.put("sand_fall", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 2);
            n.fx("main", c);
            n.loop(50, age -> {
                if (age >= 10 && age <= 50 && age % 5 == 0) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, n.dmg(0.12))) n.status(e, "slow:12:0.3");
                if (age == 50) { for (LivingEntity e : inside(n, c, r + 0.4)) if (n.hit(e, n.dmg(0.6))) n.knock(e, c, n.flat(), 0.35, 0.2); return false; }
                return true;
            }, null);
            return true;
        });

        // ================================================================== 비전 395~398
        // 395 정동 개화 (GeodeBloom): `main` static r 6 on the aimed ground. Bloom at 2.6 s (52 t): centre (d <= 1.6) hit at once,
        // spike rings reach d at 2.6 + 0.107 (d - 1.6) + 0.05 s -> damage + lift; spikes shatter at 4.4 s (88 t): 30 %
        m.put("geode_bloom", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 6);
            n.fx("main", c);
            n.later(52, () -> {
                for (LivingEntity e : inside(n, c, r)) {
                    double d = Math.hypot(e.getLocation().getX() - c.getX(), e.getLocation().getZ() - c.getZ());
                    int dt = d <= 1.6 ? 0 : (int) Math.round((0.107 * (d - 1.6) + 0.05) * 20);
                    n.later(Math.max(0, dt - 1), () -> { if (n.hit(e, n.dmg())) n.knock(e, c, n.flat(), 0.2, 0.35); });
                }
            });
            n.later(88, () -> { for (LivingEntity e : inside(n, c, r + 0.3)) n.hit(e, n.dmg(0.3)); });
            return true;
        });

        // 396 쌍극 감옥 (DipolePrison): `main` static at the target's feet (caster facing). Field on at 0.95 s (19 t): enemies within
        // 2.6 of the spot are held ~0.8 blocks up by n.carry (고정 status, 4 s; artifacts / cleanse shorten it, bosses get the status
        // but are not lifted) + disarm 4 s;
        // collapse at 5.0 s: damage to the held ones and anyone within 2.6
        m.put("dipole_prison", n -> {
            LivingEntity t = pick(n, 16, n.d("mana", 110));
            if (t == null) return false;
            double r = n.d("r", 2.6);
            Location at = Nova.ground(t.getLocation().add(0, 0.5, 0));
            n.fx("main", at);
            Set<LivingEntity> held = new LinkedHashSet<>();
            n.loop(100, age -> {
                if (age == 19) {
                    for (LivingEntity e : inside(n, at, r)) {
                        if (!n.hit(e, 0)) continue;
                        held.add(e);
                        n.status(e, "disarm:81");
                        Location l = e.getLocation();
                        Location hold = Nova.ground(l.clone().add(0, 0.5, 0)).add(0, 0.8, 0);
                        hold.setX(l.getX()); hold.setZ(l.getZ());
                        n.carry(e, 81, a -> { e.setFallDistance(0); return hold; }, 0.3, null);
                    }
                }
                if (age == 100) {
                    Set<LivingEntity> all = new LinkedHashSet<>(held);
                    all.addAll(inside(n, at, r));
                    for (LivingEntity e : all) { e.setFallDistance(0); n.hit(e, n.dmg()); }
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 397 모래폭풍 (Sandstorm): `main` static r 7 on the aimed ground, 0.5~8.0 s: every 0.5 s (10..160 t) 12 % + blindness 1.5 s
        m.put("sandstorm", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 7);
            n.fx("main", c);
            n.loop(160, age -> {
                if (age >= 10 && age % 10 == 0) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, n.dmg(0.12))) n.status(e, "blindness:30");
                return age < 160;
            }, null);
            return true;
        });

        // 398 사막화 (Desertify): `zone` static r 7 at the caster's feet + `haste` attached on the caster, both at t 0.
        // Caster +30 % move speed for 8 s; 0.3~8.0 s: enemies inside slowed 40 % (refreshed every 0.5 s). No damage.
        m.put("desertify", n -> {
            Location c = Nova.ground(n.pl.getLocation().add(0, 0.5, 0));
            double r = n.d("r", 7), slow = n.d("slow", 0.4);
            int ticks = Math.clamp(n.i("ticks", 160), 20, 400);
            n.fx("zone", c);
            n.fx("haste", n.pl.getLocation(), n.pl);
            BuffMech.apply(n.plugin, n.pl, "desertify", List.<Object[]>of(new Object[]{Attribute.MOVEMENT_SPEED, n.d("haste", 0.3), org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR}), ticks + 4);
            n.loop(ticks, age -> {
                if (age >= 6 && age % 10 == 6) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, 0)) n.status(e, "slow:14:" + slow);
                return age < ticks;
            }, null);
            return true;
        });

        // ================================================================== 궁극 399~400
        // 399 대사막의 모래시계 (GreatHourglass): `main` static r 7 on the aimed ground (11.8 s). Storm 2.6~7.3 s (52~146 t): enemies
        // inside slowed 30 % and drawn toward the centre (n.pull, bosses scaled); sinkhole at 8.2 s (164 t): 45 % + root 0.5 s;
        // geyser at 8.9 s (178 t): full damage + big lift (r 7.5)
        m.put("great_hourglass", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 7);
            n.fx("main", c);
            n.loop(178, age -> {
                if (age >= 52 && age <= 146 && age % 2 == 0) {
                    for (LivingEntity e : inside(n, c, r)) {
                        if (!n.hit(e, 0)) continue;
                        if (age % 10 == 0) n.status(e, "slow:20:0.3");
                        if (Math.hypot(e.getLocation().getX() - c.getX(), e.getLocation().getZ() - c.getZ()) > 0.8) n.pull(e, c, 0.12, 0);
                    }
                }
                if (age == 164) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, n.dmg(0.45))) n.status(e, "root:10");
                if (age == 178) {
                    for (LivingEntity e : inside(n, c, r + 0.5)) { e.setFallDistance(0); if (n.hit(e, n.dmg())) n.knock(e, c, n.flat(), 0.2, 0.9); }
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 400 지핵 자기장 (CoreMagnetosphere): `main` static r 7 on the aimed ground (12.4 s). 1.8~7.6 s (36~152 t): enemies inside
        // gravity (no flying) + slow 25 %; sphere lands at 8.55 s (171 t): 100 % within 4 / 60 % to 7 + stun 1 s + push out;
        // lava geysers at 9.15 s (183 t): 45 % + burn 3 s (r 7.5)
        m.put("core_magnetosphere", n -> {
            Location c = Nova.ground(n.aim().add(0, 0.5, 0));
            if (!n.plugin.protection().pair(n.pl, c)) return false;
            double r = n.d("r", 7);
            n.fx("main", c);
            n.loop(183, age -> {
                if (age >= 36 && age <= 152 && age % 10 == 6) for (LivingEntity e : inside(n, c, r)) if (n.hit(e, 0)) n.status(e, "gravity:20,slow:20:0.25");
                if (age == 171) {
                    for (LivingEntity e : inside(n, c, r)) {
                        double d = Math.hypot(e.getLocation().getX() - c.getX(), e.getLocation().getZ() - c.getZ());
                        if (n.hit(e, n.dmg(d <= 4 ? 1 : 0.6))) { n.status(e, "stun:20"); n.knock(e, c, n.flat(), 0.6, 0.3); }
                    }
                }
                if (age == 183) {
                    for (LivingEntity e : inside(n, c, r + 0.5)) if (n.hit(e, n.dmg(0.45))) n.status(e, "burn:60");
                    return false;
                }
                return true;
            }, null);
            return true;
        });
    }

    static void enable(MagicMythicPlugin plugin) {
    }

    // ------------------------------------------------------------------ helpers
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

    /** Enemies standing on the ground disc (centre `at`, horizontal r, from 1.5 below to 4 above it). */
    private static List<LivingEntity> inside(Nova n, Location at, double r) {
        var out = new ArrayList<LivingEntity>();
        if (at.getWorld() == null || at.getWorld() != n.pl.getWorld()) return out;
        for (LivingEntity e : n.near(at.clone().add(0, 1, 0), r + 3, 48)) if (onSpot(e, at, r, 4)) out.add(e);
        return out;
    }

    /**
     * A front moving along `f` from `o`: at tick `start` it is at forward distance z0, then advances `step` blocks per tick until
     * tick `end`. Each enemy within the lane (|side| <= half) is hit once when the front reaches its forward distance.
     * Returns the set of enemies already hit (filled as the front moves).
     */
    private static Set<UUID> sweep(Nova n, Location o, Vector f, int start, int end, double z0, double step, double half, BiConsumer<LivingEntity, Double> hit) {
        Set<UUID> done = new HashSet<>();
        Vector side = new Vector(-f.getZ(), 0, f.getX());
        n.loop(end, age -> {
            if (age < start) return true;
            double front = z0 + step * (age - start);
            Location mid = o.clone().add(f.clone().multiply(front)).add(0, 1, 0);
            for (LivingEntity e : n.near(mid, half + step + 2.5, 32)) {
                if (done.contains(e.getUniqueId())) continue;
                Vector rel = e.getLocation().toVector().subtract(o.toVector());
                double z = rel.dot(f), x = Math.abs(rel.dot(side)), dy = rel.getY(), w = e.getWidth() / 2;
                if (z < 0.3 || z - w > front || x - w > half || dy < -2 || dy > 3) continue;
                done.add(e.getUniqueId());
                hit.accept(e, z);
            }
            return age < end;
        }, null);
        return done;
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

    /** Target still alive and standing on the marked ground spot (horizontal r, from 1 below to `h` above it). */
    private static boolean onSpot(LivingEntity t, Location at, double r, double h) {
        if (!t.isValid() || t.isDead() || t.getWorld() != at.getWorld()) return false;
        Location l = t.getLocation();
        double dy = l.getY() - at.getY();
        return Math.hypot(l.getX() - at.getX(), l.getZ() - at.getZ()) <= r + t.getWidth() / 2 && dy >= -1 && dy <= h;
    }
}
