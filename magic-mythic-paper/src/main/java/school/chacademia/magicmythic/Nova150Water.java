package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;

/** chanova types for the 150-spell set (301~450), element water. Rows: tools/nova150/water.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Water {
    private Nova150Water() {}

    /** 355 서리 결정: caster -> (target -> {stacks, server tick of the last stack}). */
    private static final Map<UUID, Map<UUID, int[]>> RIME = new HashMap<>();
    /** 367 안개 바다: caster -> active fog (hide + first strike bonus). */
    private static final Map<UUID, Fog> FOG = new HashMap<>();

    private static final class Fog {
        final Nova n; final Location c; final double r, mult, extra; final int until;
        boolean used;
        Fog(Nova n, Location c, double r, double mult, double extra, int until) { this.n = n; this.c = c; this.r = r; this.mult = mult; this.extra = extra; this.until = until; }
        boolean active() { return !used && Bukkit.getCurrentTick() <= until; }
        boolean inside(Location l) {
            return l.getWorld() == c.getWorld() && Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) <= r && Math.abs(l.getY() - c.getY()) <= 6;
        }
    }

    static void register(Map<String, NovaMech.Impl> m) {
        // ================================================================== 초급
        // 351 빗방울 연사 (RainPatter): 3 drops from the hand at 0 / 0.15 / 0.30 s (16 blocks, 0.9 b/t), each hits its first enemy once
        m.put("rain_patter", n -> {
            double speed = n.d("speed", 0.9), range = n.d("range", 16);
            for (int k = 0; k < 3; k++) {
                n.later(k * 3, () -> {
                    boolean[] hitOne = {false};
                    n.shotFrom(n.hand(), n.look(), speed, range, "projectile").radius(0.45)
                        .hit((t, p) -> { hitOne[0] = true; if (n.hit(t, n.dmg())) n.fx("impact", p); return false; })
                        .end((p, wall) -> { if (!hitOne[0] && wall) n.fx("impact", p); })
                        .fire();
                });
            }
            return true;
        });

        // 352 물보라 발차기 (SprayKick): kicks at 0.06 / 0.36 / 0.66 s, each = 2.2 m / ±70° cone in front; the 3rd pushes ~1 block
        m.put("spray_kick", n -> {
            double r = n.d("r", 2.2), half = n.d("half", 70), push = n.d("push", 0.5);
            int[] at = {1, 7, 13};
            for (int k = 0; k < 3; k++) {
                boolean last = k == 2;
                n.later(at[k], () -> {
                    Location from = n.pl.getLocation(); Vector f = n.flat();
                    for (LivingEntity e : cone(n, r, half)) {
                        if (!n.hit(e, n.dmg())) continue;
                        if (last) n.knock(e, from, f, push, 0.15);
                    }
                });
            }
            return true;
        });

        // 353 물시계 방울 (ClepsydraDrop): clock fixed at the target's feet at cast, water mass lands at 2.16 s -> r 1.5 damage + slow 2 s
        m.put("clepsydra_drop", n -> {
            LivingEntity t = pick(n, 16);
            if (t == null) return false;
            Location p = Nova.ground(t.getLocation().add(0, 0.5, 0));
            p.setYaw(n.pl.getLocation().getYaw()); p.setPitch(0);
            double r = n.d("r", 1.5);
            n.fx("clock", p);
            n.later(43, () -> { for (LivingEntity e : disc(n, p, r, 3)) if (n.hit(e, n.dmg())) n.status(e, "slow:40"); });
            return true;
        });

        // 354 물 리본 (WaterRibbon): lash 1 at 0.15 s, lash 2 at 0.55 s on the lane z 0.3..4.2, |x| <= 0.9 in front;
        // enemies hit by lash 2 are pulled ~1 block toward the caster at 0.60 s (not bosses)
        m.put("water_ribbon", n -> {
            double len = n.d("len", 4.2), half = n.d("w", 1.8) / 2, pull = n.d("pull", 0.45);
            n.later(3, () -> { for (LivingEntity e : lane(n, n.pl.getLocation(), n.flat(), 0.3, len, half)) n.hit(e, n.dmg()); });
            n.later(11, () -> {
                List<LivingEntity> got = new ArrayList<>();
                for (LivingEntity e : lane(n, n.pl.getLocation(), n.flat(), 0.3, len, half)) if (n.hit(e, n.dmg())) got.add(e);
                n.later(1, () -> {
                    for (LivingEntity e : got) {
                        if (!e.isValid() || e.isDead() || e.getLocation().distanceSquared(n.pl.getLocation()) < 1.5 * 1.5) continue;
                        n.pull(e, n.pl.getLocation(), pull, 0.12);
                    }
                });
            });
            return true;
        });

        // 355 서리 결정 (RimeCrystal): one crystal (18 blocks, 1.0 b/t); each hit = damage + 1 stack (5 s, per caster);
        // 3 stacks = freeze (stun 1 s; bosses: engine rule) + shatter 50 % at +1.0 s, stacks reset
        m.put("rime_crystal", n -> {
            double speed = n.d("speed", 1.0), range = n.d("range", 18);
            boolean[] hitOne = {false};
            n.shotFrom(n.hand(), n.look(), speed, range, "projectile").radius(0.45)
                .hit((t, p) -> {
                    hitOne[0] = true;
                    if (!n.hit(t, n.dmg())) return false;
                    n.fx("impact", p);
                    var mine = RIME.computeIfAbsent(n.pl.getUniqueId(), k -> new HashMap<>());
                    int now = Bukkit.getCurrentTick();
                    mine.values().removeIf(v -> now - v[1] > 100);
                    int[] s = mine.get(t.getUniqueId());
                    if (s == null || now - s[1] > 100) s = new int[]{0, now};
                    s[0]++; s[1] = now;
                    if (s[0] >= 3) {
                        mine.remove(t.getUniqueId());
                        n.status(t, "stun:20");
                        n.fx("freeze", t.getLocation(), t);
                        n.later(19, () -> n.hit(t, n.dmg(0.5)));
                    } else mine.put(t.getUniqueId(), s);
                    return false;
                })
                .end((p, wall) -> { if (!hitOne[0] && wall) n.fx("impact", p); })
                .fire();
            return true;
        });

        // 356 안개 숨 (MistBreath): 4 m / ±25° cone from the mouth, damage at 0.15 / 0.40 / 0.65 / 0.90 s, slow 3 s with the last
        m.put("mist_breath", n -> {
            double r = n.d("r", 4), half = n.d("half", 25);
            int[] at = {3, 8, 13, 18};
            for (int k = 0; k < 4; k++) {
                boolean last = k == 3;
                n.later(at[k], () -> {
                    for (LivingEntity e : cone(n, r, half)) if (n.hit(e, n.dmg()) && last) n.status(e, "slow:60");
                });
            }
            return true;
        });

        // 357 고드름 투척 (IcicleFling): 2 icicles thrown at 0.25 / 0.42 s on an arc (0.6 b/t, gravity 0.035, ≤ 18 blocks), each hits once
        m.put("icicle_fling", n -> {
            double speed = n.d("speed", 0.6), g = n.d("gravity", 0.035), range = n.d("range", 18);
            int[] at = {5, 8};
            double[] side = {-0.12, 0.14};
            for (int k = 0; k < 2; k++) {
                double sd = side[k];
                n.later(at[k], () -> {
                    Vector right = n.flat().crossProduct(new Vector(0, 1, 0)).multiply(-1);
                    Location from = n.hand().add(right.multiply(sd)).add(0, k0(sd), 0);
                    Vector dir = n.look().add(new Vector(0, 0.22, 0));
                    boolean[] hitOne = {false};
                    n.shotFrom(from, dir, speed, range, "projectile").radius(0.5).gravity(g)
                        .hit((t, p) -> { hitOne[0] = true; if (n.hit(t, n.dmg())) n.fx("impact", p); return false; })
                        .end((p, wall) -> { if (!hitOne[0]) n.fx("impact", p); })
                        .fire();
                });
            }
            return true;
        });

        // ================================================================== 중급
        // 358 물장구 (WaterDrum): drum on the aimed ground; shock rings at 0.77 / 1.47 / 2.17 s (r 3.8 / 4.1 / 4.5): damage + small push out
        m.put("water_drum", n -> {
            Location c = spot(n);
            n.fx("main", c);
            int[] at = {15, 29, 43};
            double[] rad = {3.8, 4.1, 4.5};
            double push = n.d("push", 0.3);
            for (int k = 0; k < 3; k++) {
                double r = rad[k];
                n.later(at[k], () -> {
                    for (LivingEntity e : disc(n, c, r, 3)) {
                        if (!n.hit(e, n.dmg())) continue;
                        Vector out = flatTo(c, e.getLocation());
                        n.knock(e, c, out, push, 0.2);
                    }
                });
            }
            return true;
        });

        // 359 고드름 처마 (IcicleEaves): eaves over the lane in front (static at the cast feet); icicles land at 1.175 / 1.345 / 1.515 /
        // 1.685 / 1.855 s on z = 2.0 / 3.55 / 5.1 / 6.65 / 8.2 (r 1.5): damage + slow 2 s; points past a wall do nothing
        m.put("icicle_eaves", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            double r = n.d("r", 1.5);
            Location eye = o.clone().add(0, 1.0, 0);
            double wall = eye.distance(Nova.reach(eye, f, 8.6));
            double[] z = {2.0, 3.55, 5.1, 6.65, 8.2};
            int[] at = {23, 27, 30, 34, 37};
            for (int k = 0; k < 5; k++) {
                if (z[k] > wall + 0.6) break;
                Location p = Nova.ground(o.clone().add(f.clone().multiply(z[k])).add(0, 1.5, 0));
                n.later(at[k], () -> { for (LivingEntity e : disc(n, p, r, 3)) if (n.hit(e, n.dmg())) n.status(e, "slow:40"); });
            }
            return true;
        });

        // 360 결빙 파동 (FreezePulse): 3 frost rings around the caster's current feet at 0.65 / 1.25 / 1.90 s, r 2.6 / 4.0 / 5.6:
        // damage + slow 2 s (3 s on the last)
        m.put("freeze_pulse", n -> {
            int[] at = {13, 25, 38};
            double[] rad = {2.6, 4.0, 5.6};
            for (int k = 0; k < 3; k++) {
                double r = rad[k]; String st = k == 2 ? "slow:60" : "slow:40";
                n.later(at[k], () -> { for (LivingEntity e : disc(n, n.pl.getLocation(), r, 3)) if (n.hit(e, n.dmg())) n.status(e, st); });
            }
            return true;
        });

        // 361 소용돌이 원반 (WhirlpoolDisc): r 2.5 whirlpool on the aimed ground, 6 hits at 0.6 + 0.5k s + a small pull to the centre
        m.put("whirlpool_disc", n -> {
            Location c = spot(n);
            double r = n.d("r", 2.5), pull = n.d("pull", 0.12);
            n.fx("main", c);
            for (int k = 0; k < 6; k++) {
                n.later(12 + 10 * k, () -> {
                    for (LivingEntity e : disc(n, c, r, 3)) {
                        if (!n.hit(e, n.dmg())) continue;
                        pullTo(n, e, c, pull);
                    }
                });
            }
            return true;
        });

        // 362 해파리 떼 (JellySwarm): 4 jellies orbit the caster (same formula as the swarm visual: a = π/2·k + 1.15·(t − 0.4),
        // local (2.4 cos a, h_k + 0.08 (t − 0.4), −2.4 sin a), +Z = facing, +X = right); 0.5..4.0 s, an enemy within 0.7 of a jelly
        // is stung (damage + slow 2 s, same enemy again after 0.5 s)
        m.put("jelly_swarm", n -> {
            double[] h = {1.25, 1.75, 1.45, 1.9};
            double touch = n.d("touch", 0.7);
            int again = n.i("rehit", 10);
            Map<UUID, Integer> last = new HashMap<>();
            n.loop(80, age -> {
                if (age < 10) return true;
                if (age >= 80) return false;
                double t = age / 20.0;
                Location feet = n.pl.getLocation();
                Vector f = n.flat(), right = new Vector(-f.getZ(), 0, f.getX());
                for (int k = 0; k < 4; k++) {
                    double a = Math.PI / 2 * k + 1.15 * (t - 0.4);
                    double x = 2.4 * Math.cos(a), y = h[k] + 0.08 * (t - 0.4), z = -2.4 * Math.sin(a);
                    Location p = feet.clone().add(right.clone().multiply(x)).add(f.clone().multiply(z)).add(0, y, 0);
                    for (LivingEntity e : n.near(p, touch + 1.5, 8)) {
                        if (Targets.closestDistance(e, p) > touch) continue;
                        Integer prev = last.get(e.getUniqueId());
                        if (prev != null && age - prev < again) continue;
                        last.put(e.getUniqueId(), age);
                        if (n.hit(e, n.dmg())) { n.status(e, "slow:40"); n.fx("sting", p); }
                    }
                }
                return true;
            }, null);
            return true;
        });

        // 363 얼음 미끄럼 (IceSlide): slide up to 7 blocks along the facing on walkable ground in 0.7 s (walls / protection stop it,
        // not on a broom); every enemy within 0.9 ahead is hit once + knocked along the slide (bosses weakly)
        m.put("ice_slide", n -> {
            Player pl = n.pl;
            if (BroomLink.riding(pl)) return refuse(n, "빗자루 위에서는 쓸 수 없어요");
            Location start = pl.getLocation(); Vector f = n.flat();
            List<Location> pts = path(n.plugin, start, f, n.d("dist", 7));
            if (pts.size() < 3) return refuse(n, "앞쪽이 막혀 있어요");
            int dur = n.i("ticks", 14);
            double knock = n.d("knock", 0.9);
            Set<UUID> done = new HashSet<>();
            int[] idx = {0};
            n.loop(dur + 1, age -> {
                if (pl.isDead()) return false;
                double a = Math.min(1, (age + 1) / (double) dur);
                int to = Math.min(pts.size(), Math.max(idx[0] + 1, (int) Math.round(pts.size() * (1 - (1 - a) * (1 - a)))));
                idx[0] = to;
                if (!move(pl, pts.get(to - 1))) return false;
                Location front = pl.getLocation().add(f.clone().multiply(0.6)).add(0, 0.9, 0);
                for (LivingEntity e : n.near(front, 2.4, 8)) {
                    if (Targets.closestDistance(e, front) > 0.9 || !done.add(e.getUniqueId())) continue;
                    if (!n.hit(e, n.dmg())) continue;
                    n.knock(e, pl.getLocation(), f, knock, 0.3);
                    n.fx("bump", Nova.chest(e));
                }
                return to < pts.size();
            }, () -> { if (pl.isOnline()) pl.setVelocity(f.clone().multiply(0.15)); });
            return true;
        });

        // ================================================================== 고급
        // 364 폭우 (RainBarrage): r 5 on the aimed ground; rain ticks every 0.5 s from 1.18 s to 6.68 s (x0.08),
        // big waves land at 2.58 / 4.08 / 5.58 / 6.58 s (extra x0.15 + slow 1.5 s)
        m.put("rain_barrage", n -> {
            Location c = spot(n);
            double r = n.d("r", 5), tick = n.d("tick", 0.08), wave = n.d("wave", 0.15);
            n.fx("main", c);
            for (int k = 0; k < 12; k++) n.later(24 + 10 * k - 1, () -> { for (LivingEntity e : disc(n, c, r, 6)) n.hit(e, n.dmg(tick)); });
            for (int at : new int[]{52, 82, 112, 132})
                n.later(at - 1, () -> { for (LivingEntity e : disc(n, c, r, 6)) if (n.hit(e, n.dmg(wave))) n.status(e, "slow:30"); });
            return true;
        });

        // 365 서리 정원 (RimeGarden): frost front 0 -> 6 m between 0.4 and 2.4 s; each enemy it reaches: x0.3 + freeze (stun 1.25 s,
        // bosses: engine rule) + freeze visual at the feet; all crystals shatter at 4.0 s: r 6 full damage + slow 3 s
        m.put("rime_garden", n -> {
            Location c = spot(n);
            double r = n.d("r", 6), first = n.d("front", 0.3);
            Set<UUID> frozen = new HashSet<>();
            n.fx("main", c);
            n.loop(80, age -> {
                if (age >= 8 && age <= 48) {
                    double front = r * Math.min(1, (age - 8) / 40.0);
                    for (LivingEntity e : disc(n, c, front + 0.3, 4)) {
                        if (!frozen.add(e.getUniqueId())) continue;
                        if (!n.hit(e, n.dmg(first))) continue;
                        n.status(e, "stun:25");
                        n.fx("freeze", e.getLocation());
                    }
                }
                if (age >= 79) {
                    for (LivingEntity e : disc(n, c, r, 4)) if (n.hit(e, n.dmg())) n.status(e, "slow:60");
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 366 물시계 감옥 (ClepsydraCage): sphere at the target's feet; 0.35..3.35 s movement lock (bosses 1.5 s) and held at the
        // centre (not bosses); hits at 1.35 / 2.35 / 3.35 s x0.3 / x0.45 / x0.6 while still inside; the burst also hits others in r 2.2 (x0.3)
        m.put("clepsydra_cage", n -> {
            LivingEntity t = pick(n, 16);
            if (t == null) return false;
            Location p = Nova.ground(t.getLocation().add(0, 0.5, 0));
            p.setYaw(n.pl.getLocation().getYaw()); p.setPitch(0);
            n.fx("main", p);
            Location hold = p.clone().add(0, 0.05, 0);
            n.later(6, () -> { if (n.hit(t, 0)) n.carry(t, 60, a -> hold, 0.5, null); });
            double[] mult = {0.3, 0.45, 0.6};
            n.loop(68, age -> {
                if (!t.isValid() || t.isDead() || t.getWorld() != p.getWorld()) return false;
                Location l = t.getLocation();
                double off = Math.hypot(l.getX() - p.getX(), l.getZ() - p.getZ());
                int k = age == 27 ? 0 : age == 47 ? 1 : age == 67 ? 2 : -1;
                if (k >= 0 && off <= 2.0 && Math.abs(l.getY() - p.getY()) <= 3) n.hit(t, n.dmg(mult[k]));
                if (age >= 67) {
                    for (LivingEntity e : disc(n, p, 2.2, 3)) if (e != t) n.hit(e, n.dmg(0.3));
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 367 안개 바다 (SeaOfFog): r 8 fog at the cast feet for 8 s. Inside it the caster is invisible and monsters lose / cannot pick
        // the caster from > 3 blocks (Hooks). First hit dealt within the 8 s (melee, arrow or spell): x1.3 + base damage, ambush visual
        // (stops veil), hiding ends.
        m.put("sea_of_fog", n -> {
            Player pl = n.pl;
            Location c = pl.getLocation().clone();
            double r = n.d("r", 8);
            int ticks = Math.clamp(n.i("ticks", 160), 20, 400);
            Fog fog = new Fog(n, c, r, n.d("mult", 1.3), n.dmg(), Bukkit.getCurrentTick() + ticks);
            FOG.put(pl.getUniqueId(), fog);
            n.loop(ticks, age -> {
                if (FOG.get(pl.getUniqueId()) != fog || !fog.active()) return false;
                if (age < 16 || age % 5 != 0 || !fog.inside(pl.getLocation())) return true;
                pl.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 12, 0, false, false, false));
                for (Entity e : pl.getNearbyEntities(24, 12, 24))
                    if (e instanceof Mob mob && mob.getTarget() == pl && n.enemy(mob) && mob.getLocation().distanceSquared(pl.getLocation()) > 9) n.status(mob, "blindness:30");
                return true;
            }, () -> {
                if (FOG.get(pl.getUniqueId()) == fog) FOG.remove(pl.getUniqueId());
            });
            return true;
        });

        // 368 빙산 낙하 (IcebergDrop): iceberg lands at 1.2 s: r 4 damage (centre 100 % -> edge 60 %) + slow 2 s; shatters at 2.2 s: r 4 x0.3
        m.put("iceberg_drop", n -> {
            Location c = spot(n);
            double r = n.d("r", 4);
            n.fx("main", c);
            n.later(23, () -> {
                for (LivingEntity e : disc(n, c, r, 5)) {
                    double d = Math.hypot(e.getLocation().getX() - c.getX(), e.getLocation().getZ() - c.getZ());
                    if (n.hit(e, n.dmg(1 - 0.4 * Math.min(1, d / r)))) n.status(e, "slow:40");
                }
            });
            n.later(43, () -> { for (LivingEntity e : disc(n, c, r, 5)) n.hit(e, n.dmg(0.3)); });
            return true;
        });

        // 369 이안류 (RipCurrent): lane x ±1.5, z 0.6..14 along the facing (shorter at a wall). The crest (z = 1 + 14.4 (t − 0.3))
        // catches each enemy once (x0.5) and drags it to the end until 2.4 s (bosses 30 %); 2.4 s water column at the end r 2.5 (x0.7 + lift)
        m.put("rip_current", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            double half = n.d("w", 3) / 2;
            Location eye = o.clone().add(0, 1.0, 0);
            double len = Math.min(n.d("len", 14), eye.distance(Nova.reach(eye, f, 14.5)));
            Location end = Nova.ground(o.clone().add(f.clone().multiply(Math.max(1.5, len - 0.5))).add(0, 1.5, 0));
            double drag = n.d("drag", 0.55);
            Set<LivingEntity> caught = new LinkedHashSet<>();
            n.loop(48, age -> {
                if (age >= 6 && age <= 24) {
                    double front = Math.min(len, 1 + 14.4 * (age / 20.0 - 0.3));
                    for (LivingEntity e : lane(n, o, f, 0.6, front, half)) {
                        if (!caught.add(e)) continue;
                        if (n.hit(e, n.dmg(0.5))) n.carry(e, 46 - age, a -> end, drag, null);
                    }
                }
                if (age >= 47) {
                    for (LivingEntity e : disc(n, end, 2.5, 4)) if (n.hit(e, n.dmg(0.7))) n.lift(e, 0.6);
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // ================================================================== 비전
        // 370 빙하 결정탑 (GlacierSpire): spire 8 s on the aimed ground; at 1.4 s + 1 s·k (7 shots) a shard from the crown (+7.6) homes
        // on the nearest enemy within 9 (clear line only): x0.22 + slow 1 s; collapse at 8.0 s: r 4.5 x0.5
        m.put("glacier_spire", n -> {
            Location c = spot(n);
            Location crown = c.clone().add(0, 7.6, 0);
            double seek = n.d("seek", 9), shard = n.d("shard", 0.22), speed = n.d("speed", 1.25);
            n.fx("spire", c);
            for (int k = 0; k < 7; k++) {
                n.later(28 + 20 * k, () -> {
                    LivingEntity best = null; double bd = Double.MAX_VALUE;
                    for (LivingEntity e : disc(n, c, seek, 8)) {
                        Vector to = Nova.chest(e).toVector().subtract(crown.toVector());
                        double d = to.length();
                        if (d < 0.5 || d >= bd) continue;
                        if (crown.getWorld().rayTraceBlocks(crown, to.normalize(), d, FluidCollisionMode.NEVER, true) != null) continue;
                        best = e; bd = d;
                    }
                    if (best == null) return;
                    LivingEntity t = best;
                    boolean[] hitOne = {false};
                    n.shotFrom(crown, Nova.chest(t).toVector().subtract(crown.toVector()), speed, bd + 6, "shard").radius(0.6).homing(t, 0.4)
                        .hit((e, p) -> { hitOne[0] = true; if (n.hit(e, n.dmg(shard))) n.status(e, "slow:20"); n.fx("shardHit", p); return false; })
                        .end((p, wall) -> { if (!hitOne[0]) n.fx("shardHit", p); })
                        .fire();
                });
            }
            n.later(159, () -> { for (LivingEntity e : disc(n, c, n.d("collapse", 4.5), 5)) n.hit(e, n.dmg(0.5)); });
            return true;
        });

        // 371 결빙 화음 (FrozenChord): three points P + 4·(sin 120°k, 0, cos 120°k) in the caster frame (k = 0 ahead); 1.45 s pillars:
        // r 1.7 full damage + lift (not bosses); 3.4 s shatter: r 2.0 x0.35 + slow 2 s. An enemy is hit once per wave.
        m.put("frozen_chord", n -> {
            Location c = spot(n);
            Vector f = n.flat(), right = new Vector(-f.getZ(), 0, f.getX());
            double gap = n.d("gap", 4);
            n.fx("main", c);
            List<Location> pts = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(120 * k);
                pts.add(Nova.ground(c.clone().add(right.clone().multiply(gap * Math.sin(a))).add(f.clone().multiply(gap * Math.cos(a))).add(0, 1.5, 0)));
            }
            n.later(28, () -> {
                Set<UUID> once = new HashSet<>();
                for (Location p : pts) for (LivingEntity e : disc(n, p, 1.7, 4)) {
                    if (!once.add(e.getUniqueId()) || !n.hit(e, n.dmg())) continue;
                    n.lift(e, n.d("lift", 0.75));
                }
            });
            n.later(67, () -> {
                Set<UUID> once = new HashSet<>();
                for (Location p : pts) for (LivingEntity e : disc(n, p, 2.0, 5))
                    if (once.add(e.getUniqueId()) && n.hit(e, n.dmg(0.35))) n.status(e, "slow:40");
            });
            return true;
        });

        // 372 거대 물방울 (GiantDroplet): summon 1.8 ahead; at 0.75 s the droplet (centre 1.6 above the ground, d 3.2) rolls along the
        // facing 14 blocks at 0.233 b/t on the ground (wall, drop > 1.2, protection = stop). Enemies touched are swallowed and carried
        // (bosses: contact damage only); x0.12 every 0.5 s; end = burst: r 3 x0.8 + push out, everyone released
        m.put("giant_droplet", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            Location p0 = Nova.ground(o.clone().add(f.clone().multiply(1.8)).add(0, 1.5, 0));
            p0.setYaw(o.getYaw()); p0.setPitch(0);
            double speed = n.d("speed", 0.233), range = n.d("range", 14), tick = n.d("tick", 0.12), burst = n.d("burst", 0.8);
            n.fx("summon", p0);
            n.later(14, () -> {
                Location ctr = p0.clone().add(0, 1.6, 0);
                GameplayVisual vis = n.shot("droplet", ctr, f);
                Set<LivingEntity> held = new LinkedHashSet<>();
                Map<UUID, Integer> touched = new HashMap<>();
                double[] gone = {0};
                int maxT = (int) Math.ceil(range / speed) + 2;
                n.loop(maxT, age -> {
                    Location next = ctr.clone().add(f.clone().multiply(speed));
                    double gy = Telegraphs.ground(next.clone().add(0, 0.2, 0));
                    double feet = ctr.getY() - 1.6;
                    if (gy - feet > 1.05 || feet - gy > 1.2 || !n.plugin.protection().pair(n.pl, next)) return false;
                    Location low = next.clone(); low.setY(gy + 0.5);
                    if (!Nova.passable(low) || !Nova.passable(low.clone().add(0, 1, 0))) return false;
                    next.setY(gy + 1.6);
                    ctr.setX(next.getX()); ctr.setY(next.getY()); ctr.setZ(next.getZ());
                    gone[0] += speed;
                    if (vis != null) vis.move(ctr, f);
                    for (LivingEntity e : n.near(ctr, 3.2, 12)) {
                        if (Targets.closestDistance(e, ctr) > 1.6) continue;
                        if (held.add(e) && n.hit(e, 0)) {
                            int left = maxT - age;
                            n.carry(e, left, a -> ctr.clone().add(0, -e.getHeight() / 2, 0).add(f.clone().multiply(speed)), 1.2, null);
                        }
                        Integer last = touched.get(e.getUniqueId());
                        if (last == null || age - last >= 10) { touched.put(e.getUniqueId(), age); n.hit(e, n.dmg(tick)); }
                    }
                    return gone[0] < range;
                }, () -> {
                    if (vis != null) vis.close();
                    n.fx("burst", ctr);
                    for (LivingEntity e : held) if (e.isValid()) n.clear(e, Status.MOVEMENT_LOCK);
                    for (LivingEntity e : n.near(ctr, 3.0)) {
                        if (!n.hit(e, n.dmg(burst))) continue;
                        n.knock(e, ctr, flatTo(ctr, e.getLocation()), 0.5, 0.25);
                    }
                });
            });
            return true;
        });

        // 373 심해 촉수 (DeepTentacles): 4 tentacles at P + 2.2·(cos a, 0, sin a), a = 45/135/225/315° in the caster frame; rise at
        // 0.8 / 0.88 / 0.96 / 1.04 s (r 1.2 x0.25 + lift, not bosses); sweeps at 1.75 / 2.55 / 3.35 s (r 4 x0.3 + slow 1 s); 5.0 s column r 2 x0.2
        m.put("deep_tentacles", n -> {
            Location c = spot(n);
            Vector f = n.flat(), right = new Vector(-f.getZ(), 0, f.getX());
            n.fx("main", c);
            int[] rise = {16, 18, 19, 21};
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(45 + 90 * k);
                Location p = Nova.ground(c.clone().add(right.clone().multiply(2.2 * Math.cos(a))).add(f.clone().multiply(2.2 * Math.sin(a))).add(0, 1.5, 0));
                n.later(rise[k], () -> {
                    for (LivingEntity e : disc(n, p, 1.2, 3))
                        if (n.hit(e, n.dmg(0.25))) n.lift(e, n.d("lift", 0.6));
                });
            }
            for (int at : new int[]{35, 51, 67})
                n.later(at - 1, () -> { for (LivingEntity e : disc(n, c, n.d("r", 4), 5)) if (n.hit(e, n.dmg(0.3))) n.status(e, "slow:20"); });
            n.later(99, () -> { for (LivingEntity e : disc(n, c, 2, 4)) n.hit(e, n.dmg(0.2)); });
            return true;
        });

        // ================================================================== 궁극
        // 374 천상의 물시계 (CelestialClepsydra): r 7 under the clock. 2.0..8.0 s every 0.5 s x0.04 — movement 1 rain (2.0..4.2),
        // 2 ice (4.2..6.2, + slow), 3 current (6.2..8.0, pull to the centre, bosses 30 %); 8.6 s clock breaks: r 7 x0.6 + stun 1 s
        // (bosses 0.5 s); 9.1 s flood: r 14 x0.5 + push out
        m.put("celestial_clepsydra", n -> {
            Location c = spot(n);
            double r = n.d("r", 7), flood = n.d("flood", 14), tick = n.d("tick", 0.04);
            n.fx("main", c);
            n.loop(182, age -> {
                if (age >= 40 && age < 160 && age % 10 == 0) {
                    for (LivingEntity e : disc(n, c, r, 8)) {
                        if (!n.hit(e, n.dmg(tick))) continue;
                        if (age >= 84 && age < 124) n.status(e, "slow:30");
                        else if (age >= 124) pullTo(n, e, c, 0.25);
                    }
                }
                if (age == 171) for (LivingEntity e : disc(n, c, r, 8)) if (n.hit(e, n.dmg(0.6))) n.status(e, "stun:20");
                if (age >= 181) {
                    for (LivingEntity e : disc(n, c, flood, 8)) {
                        if (!n.hit(e, n.dmg(0.5))) continue;
                        n.knock(e, c, flatTo(c, e.getLocation()), 0.8, 0.3);
                    }
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 375 영겁의 빙하 (GlacialEpoch): r 15. 0.7..2.7 s frost front 0 -> 15: each enemy reached x0.1 + slow 5 s; 4.5..8.0 s slow kept
        // on everyone inside; 8.0 s shatter r 15 x0.7; 8.75 s absolute zero r 15 x0.6 + stun 1.5 s (bosses 0.5 s)
        m.put("glacial_epoch", n -> {
            Location c = spot(n);
            double r = n.d("r", 15), first = n.d("front", 0.1);
            Set<UUID> reached = new HashSet<>();
            n.fx("main", c);
            n.loop(176, age -> {
                if (age >= 14 && age <= 54) {
                    double front = r * Math.min(1, (age - 14) / 40.0);
                    for (LivingEntity e : disc(n, c, front + 0.3, 8))
                        if (reached.add(e.getUniqueId()) && n.hit(e, n.dmg(first))) n.status(e, "slow:100");
                }
                if (age >= 90 && age < 160 && age % 20 == 0) for (LivingEntity e : disc(n, c, r, 8)) n.status(e, "slow:30");
                if (age == 159) for (LivingEntity e : disc(n, c, r, 10)) n.hit(e, n.dmg(0.7));
                if (age >= 174) {
                    for (LivingEntity e : disc(n, c, r, 10)) if (n.hit(e, n.dmg(0.6))) n.status(e, "stun:30");
                    return false;
                }
                return true;
            }, null);
            return true;
        });
    }

    static void enable(MagicMythicPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new Hooks(), plugin);
    }

    // ------------------------------------------------------------------ helpers
    /** 357: the second icicle leaves a little lower in the hand. */
    private static double k0(double side) { return side > 0 ? -0.2 : 0; }

    /** Refused cast: message, mana back (the Mythic skill already ran), cooldown cleared. */
    private static boolean refuse(Nova n, String msg) {
        n.warn(msg); n.refund(n.d("mana", 0)); n.clearCooldown(); return false;
    }

    /** ENTITY spells: the Bridge's target (or the enemy nearest the aim point); refused when none in range. */
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

    /** Ground under the aim point, facing the caster's horizontal look (static visuals use the location yaw as +Z). */
    private static Location spot(Nova n) {
        Location c = Nova.ground(n.aim().add(0, 1.5, 0));
        c.setYaw(n.pl.getLocation().getYaw()); c.setPitch(0);
        return c;
    }

    /** Enemies whose feet are within horizontal r of c (entity half width counted), from 1.5 below to `up` above. */
    private static List<LivingEntity> disc(Nova n, Location c, double r, double up) {
        var out = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(c.clone().add(0, up / 2, 0), r + up / 2 + 2, 32)) {
            Location l = e.getLocation();
            double dy = l.getY() - c.getY();
            if (dy < -1.5 || dy > up) continue;
            if (Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) > r + e.getWidth() / 2) continue;
            out.add(e);
        }
        return out;
    }

    /** Enemies on a lane in front of `o` along horizontal f: z0..z1, |x| <= half (+ half width), feet within -1.5..2.5. */
    private static List<LivingEntity> lane(Nova n, Location o, Vector f, double z0, double z1, double half) {
        var out = new ArrayList<LivingEntity>();
        if (z1 <= z0) return out;
        Vector side = new Vector(-f.getZ(), 0, f.getX());
        Location mid = o.clone().add(f.clone().multiply((z0 + z1) / 2)).add(0, 1, 0);
        for (LivingEntity e : n.near(mid, (z1 - z0) / 2 + half + 2.5, 32)) {
            Vector v = e.getLocation().toVector().subtract(o.toVector());
            double z = v.dot(f), x = Math.abs(v.dot(side)), dy = v.getY();
            if (z < z0 || z > z1 || x > half + e.getWidth() / 2 || dy < -1.5 || dy > 2.5) continue;
            out.add(e);
        }
        return out;
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

    /** Horizontal unit vector from a to b (any direction when they overlap). */
    private static Vector flatTo(Location a, Location b) {
        Vector v = b.toVector().subtract(a.toVector()).setY(0);
        return v.lengthSquared() < 1e-4 ? new Vector(1, 0, 0) : v.normalize();
    }

    /** Small horizontal pull toward c through Nova.pull (anchor / immunity / boss scaling), not when already near the centre. */
    private static void pullTo(Nova n, LivingEntity e, Location c, double s) {
        if (!e.isValid() || e.isDead()) return;
        if (Math.hypot(e.getLocation().getX() - c.getX(), e.getLocation().getZ() - c.getZ()) < 0.8) return;
        n.pull(e, c, s, 0);
    }

    /** Keeps the player's current look when moving them. */
    private static boolean move(Player pl, Location to) {
        Location at = to.clone(); at.setYaw(pl.getLocation().getYaw()); at.setPitch(pl.getLocation().getPitch());
        boolean ok = pl.teleport(at, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (ok) pl.setFallDistance(0);
        return ok;
    }

    /** Walkable ground path along a horizontal dir (0.25 steps, 1-block step up, ≤3 down), stops at walls / protection. */
    private static List<Location> path(MagicMythicPlugin plugin, Location start, Vector dir, double dist) {
        List<Location> pts = new ArrayList<>();
        Location cur = start.clone();
        for (double d = 0.25; d <= dist + 1e-6; d += 0.25) {
            Location probe = start.clone().add(dir.clone().multiply(d)); probe.setY(cur.getY() + 1.0);
            if (!probe.isChunkLoaded()) break;
            Location s = Nova.stand(probe);
            if (s == null || s.getY() - cur.getY() > 1.01 || cur.getY() - s.getY() > 3.01 || !plugin.protection().at(s)) break;
            cur = s; pts.add(s.clone());
        }
        return pts;
    }

    // ------------------------------------------------------------------ listeners (367 안개 바다, 355 cleanup)
    static final class Hooks implements Listener {
        /** 367: the first real hit the fogged caster deals is strengthened, then the hiding ends. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void ambush(EntityDamageByEntityEvent e) {
            if (e.getDamage() <= 0 || !(e.getEntity() instanceof LivingEntity v)) return;
            Player a = NovaMid2.attacker(e.getDamager());
            if (a == null || v == a) return;
            Fog f = FOG.get(a.getUniqueId());
            if (f == null || !f.active() || !f.n.enemy(v)) return;
            f.used = true;
            FOG.remove(a.getUniqueId());
            e.setDamage(e.getDamage() * f.mult + f.extra);
            f.n.fx("ambush", Nova.chest(v));
            a.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        /** 367: a monster that tries to pick the hidden caster from more than 3 blocks away is blinded (status engine: loses far targets). */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void target(EntityTargetLivingEntityEvent e) {
            if (!(e.getTarget() instanceof Player p) || !(e.getEntity() instanceof LivingEntity mob)) return;
            Fog f = FOG.get(p.getUniqueId());
            if (f == null || !f.active() || !f.inside(p.getLocation()) || !f.n.enemy(mob)) return;
            if (mob.getWorld() == p.getWorld() && mob.getLocation().distanceSquared(p.getLocation()) <= 9) return;
            if (!f.n.has(mob, Status.BLINDNESS)) f.n.status(mob, "blindness:30");
        }

        @EventHandler public void quit(PlayerQuitEvent e) {
            UUID id = e.getPlayer().getUniqueId();
            RIME.remove(id); FOG.remove(id);
            for (var mine : RIME.values()) mine.remove(id);
        }
    }
}
