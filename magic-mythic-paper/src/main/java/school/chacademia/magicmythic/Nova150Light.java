package school.chacademia.magicmythic;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** chanova types for the 150-spell set (301~450), element light. Rows: tools/nova150/light.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Light {
    private Nova150Light() {}

    /** Visual-only armor stands (link anchors, mirror double): removed on enable / end. */
    static final String TAG = "chacademia_nova150_light";
    /** 408 거울판: live pane per caster (my light shots crossing it are bent, hostile projectiles reflected). */
    private static final Map<UUID, Pane> PANES = new HashMap<>();
    /** 421 거울 세계: live mirror double per caster. */
    private static final Map<UUID, Realm> REALMS = new HashMap<>();
    /** 422 천체 망원경: live scope mode per caster. */
    private static final Map<UUID, Scope> SCOPES = new HashMap<>();
    private static boolean echoing;

    private record Pane(Nova n, Location base, Vector f, Vector x, int from, int until) {}
    private static final class Realm {
        final Nova n; final ArmorStand body; int last = -100, echoes;
        Realm(Nova n, ArmorStand body) { this.n = n; this.body = body; }
    }
    private static final class Scope {
        final Nova n; final int start; int shots, lastFire = -100; boolean charging;
        Scope(Nova n, int start) { this.n = n; this.start = start; }
    }

    static void register(Map<String, NovaMech.Impl> m) {
        // 401 별똥 바늘 (StarNeedle): 3 needles formed at the right shoulder slots, thrown at 0.45 / 0.60 / 0.75 s toward the aim
        // point (converging), 20 blocks; every needle hits once (impact at the hit)
        m.put("star_needle", n -> {
            double speed = n.d("speed", 1.6), range = n.d("range", 20);
            double[][] slot = {{0.55, 1.95, -0.05}, {0.8, 1.62, 0.05}, {0.6, 1.3, 0.18}};
            int[] at = {9, 12, 15};
            for (int k = 0; k < 3; k++) {
                double[] s = slot[k];
                n.later(at[k] - 1, () -> {
                    Location from = local(n.pl.getLocation(), n.flat(), s[0], s[1], s[2]);
                    if (!Nova.passable(from)) from = n.hand();
                    Vector dir = aimPoint(n, range).toVector().subtract(from.toVector());
                    boolean[] hit = {false};
                    go(n, n.shotFrom(from, dir.lengthSquared() < 1e-4 ? n.look() : dir, speed, range + 2, "projectile").radius(0.45)
                        .hit((t, p) -> { hit[0] = true; fresh(t); n.hit(t, n.dmg()); n.fx("impact", p); return false; })
                        .end((p, wall) -> { if (!hit[0]) n.fx("impact", p); }));
                });
            }
            return true;
        });

        // 402 초점 렌즈 (FocusLens): lens forms (0.35 s), then up to 2 s the sun is focused on the aimed point (r 1.2):
        // burn ticks at +0.5 / 1.0 / 1.5 s, last ember burst (x1.5) at the end. Looking away (> 25°) or walking off ends it early.
        m.put("focus_lens", n -> {
            double range = n.d("range", 12), r = n.d("r", 1.2);
            int start = 7, hold = Math.clamp(n.i("hold", 40), 10, 40);
            Location fp = aimPoint(n, range);
            Location from = n.pl.getLocation();
            boolean[] over = {false};
            Runnable end = () -> {
                if (over[0] || !n.pl.isOnline()) return;
                over[0] = true;
                n.fx("lensBreak", n.pl.getLocation(), n.pl);
                n.fx("burnEnd", fp);
                for (LivingEntity e : n.near(fp, r)) n.hit(e, n.dmg(1.5));
            };
            n.loop(start + hold, age -> {
                if (n.pl.isDead()) return false;
                if (age == start) { n.fx("lens", n.pl.getLocation(), n.pl); n.fx("beam", fp); n.fx("burn", fp); }
                if (age > start) {
                    Location eye = n.pl.getEyeLocation();
                    Vector to = fp.toVector().subtract(eye.toVector());
                    if (to.lengthSquared() > 1e-4 && to.normalize().dot(n.look()) < Math.cos(Math.toRadians(25))) return false;
                    if (n.pl.getWorld() != from.getWorld() || n.pl.getLocation().distanceSquared(from) > 6.25) return false;
                    if ((age - start) % 10 == 3 && age < start + hold - 10) for (LivingEntity e : n.near(fp, r)) n.hit(e, n.dmg());
                }
                return age < start + hold;
            }, end);
            return true;
        });

        // 403 촛불 성가 (CandleHymn): 3 candles float around the caster for 8 s; from 0.5 s, an enemy within 5 blocks (in sight)
        // draws one candle's flash (≥ 0.7 s apart, candles 1 → 3); flash flies 0.15 s, damage at the burst
        m.put("candle_hymn", n -> {
            double r = n.d("r", 5);
            int life = n.i("ticks", 160);
            double[][] slot = {{-0.8, 1.7, 0.1}, {0.8, 1.85, 0.0}, {0.0, 2.15, -0.65}};
            int[] used = {0}, last = {-100};
            n.loop(life, age -> {
                if (n.pl.isDead()) return false;
                if (used[0] >= 3) return false;
                if (age < 10 || age - last[0] < 14 || age % 2 != 0) return true;
                LivingEntity t = null;
                for (LivingEntity e : n.near(n.pl.getLocation().add(0, 1, 0), r, 6)) if (n.pl.hasLineOfSight(e)) { t = e; break; }
                if (t == null) return true;
                int k = used[0]++;
                last[0] = age;
                LivingEntity tt = t;
                Location from = local(n.pl.getLocation(), n.flat(), slot[k][0], slot[k][1] + 0.23, slot[k][2]);
                String burst = "burst" + (k + 1);
                fly(n, "flash", from, () -> alive(tt) ? Nova.chest(tt) : null, 3, 0, p -> {
                    if (alive(tt)) n.hit(tt, n.dmg());
                    n.fx(burst, p);
                });
                return used[0] < 3;
            }, () -> { if (used[0] < 3 && n.pl.isOnline()) n.fx("hymn_end", n.pl.getLocation().add(0, 1.2, 0)); });
            return true;
        });

        // 404 빛 바늘땀 (LightStitch): 3 threaded needles fanned ±0.2 rad at 0.2 s (14 blocks); each hit = damage + slow 3 s.
        // At 0.9 s the hit enemies are stitched in hit order (1–2, 2–3): stronger slow, pulled back together past 4.5 blocks (n.pull); release at 3.9 s
        m.put("light_stitch", n -> {
            double speed = n.d("speed", 1.3), range = n.d("range", 14), yaw = n.d("yaw", 0.2);
            List<LivingEntity> got = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                double a = (k - 1) * yaw;
                n.later(3 + (k == 2 ? 1 : 0), () -> {
                    boolean[] hit = {false};
                    go(n, n.shotFrom(n.hand(), n.look().rotateAroundY(-a), speed, range, "needle").radius(0.5)
                        .hit((t, p) -> {
                            hit[0] = true;
                            n.fx("mark", p);
                            fresh(t);
                            if (n.hit(t, n.dmg()) && !got.contains(t)) { got.add(t); n.status(t, "slow:60:0.25"); n.fx("bound", t.getLocation(), t); }
                            return false;
                        })
                        .end((p, wall) -> { if (!hit[0] && wall) n.fx("mark", p); }));
                });
            }
            n.later(18, () -> {
                List<LivingEntity> live = new ArrayList<>();
                for (LivingEntity e : got) if (alive(e) && e.getWorld() == n.pl.getWorld()) live.add(e);
                if (live.isEmpty()) return;
                for (int i = 0; i + 1 < live.size(); i++) {
                    LivingEntity a = live.get(i), b = live.get(i + 1);
                    n.fx("thread", Nova.chest(a), b);
                    n.status(a, "slow:60:0.45");
                    n.status(b, "slow:60:0.45");
                }
                n.loop(60, age -> {
                    if (age % 5 != 0) return true;
                    for (int i = 0; i + 1 < live.size(); i++) {
                        LivingEntity a = live.get(i), b = live.get(i + 1);
                        if (!alive(a) || !alive(b) || a.getWorld() != b.getWorld()) continue;
                        Vector ab = b.getLocation().toVector().subtract(a.getLocation().toVector()).setY(0);
                        if (ab.length() <= n.d("slack", 4.5)) continue;
                        n.pull(a, b.getLocation(), 0.3, 0.05);                       // 끌기 도우미: 닻·면역 제외, 보스 감소
                        n.pull(b, a.getLocation(), 0.3, 0.05);
                    }
                    return true;
                }, () -> { for (LivingEntity e : live) if (alive(e)) n.fx("release", e.getLocation(), e); });
            });
            return true;
        });

        // 405 거울 조각 (MirrorShard): spinning shard at 0.15 s (20 blocks); the first block it meets reflects it once
        // (r = d − 2(d·n)n, rest of the range); enemy hit = damage, x1.3 after a reflection. The reflection itself does no damage.
        m.put("mirror_shard", n -> {
            n.later(3, () -> shard(n, n.hand(), n.look(), n.d("range", 20), false));
            return true;
        });

        // 406 별빛 뛰기 (StarHop): a star hops from the hand to the target, then to the nearest other enemy within 6 (in sight of the
        // last one), 3 hits; each hop arcs for 0.26 s, damage on arrival
        m.put("star_hop", n -> {
            LivingEntity t = pick(n, n.d("range", 12), n.d("mana", 9));
            if (t == null) return false;
            n.later(3, () -> hop(n, n.hand(), t, n.i("hops", 3), new HashSet<>()));
            return true;
        });

        // 407 빛 부채 (LightFan): 120° / 3 m fan in front, the wipe crosses its middle at 0.18 s → damage once
        m.put("light_fan", n -> {
            double r = n.d("r", 3), half = n.d("half", 60);
            n.later(3, () -> { for (LivingEntity e : cone(n, r, half)) n.hit(e, n.dmg()); });
            return true;
        });

        // 408 거울판 (MirrorPane): pane 1.6 m in front for 6 s (active 0.4–5.75 s, 2 × 2.75 m). Hostile vanilla projectiles crossing it
        // toward the caster are reflected (shooter = caster); my own light shots (this class) crossing it outward bend to the nearest enemy ahead
        m.put("mirror_pane", n -> {
            UUID id = n.pl.getUniqueId();
            Vector f = n.flat(), x = side(f);
            Location base = Nova.ground(n.pl.getLocation().add(f.clone().multiply(n.d("dist", 1.6))).add(0, 1, 0));
            int now = Bukkit.getCurrentTick();
            Pane pane = new Pane(n, base, f, x, now + 8, now + 115);
            PANES.put(id, pane);
            n.fx("pane", base);
            Set<UUID> done = new HashSet<>();
            n.loop(116, age -> {
                if (PANES.get(id) != pane) return false;
                if (age < 8) return true;
                Location c = base.clone().add(0, 1.45, 0);
                for (Entity e : base.getWorld().getNearbyEntities(c, 4, 3.5, 4)) {
                    if (!(e instanceof Projectile pr) || !pr.isValid() || done.contains(pr.getUniqueId()) || !hostile(n, pr)) continue;
                    Vector v = pr.getVelocity();
                    double vf = v.dot(f);
                    if (vf > -0.05) continue;                                          // not flying toward the caster's side
                    Vector rel = pr.getLocation().toVector().subtract(base.toVector());
                    double z = rel.dot(f), lx = rel.dot(x), y = rel.getY();
                    if (z < -0.4 || z + vf > 0.6 || Math.abs(lx) > 1.15 || y < 0 || y > 2.9) continue;
                    done.add(pr.getUniqueId());
                    Vector out = v.clone().subtract(f.clone().multiply(2 * vf));
                    Location at = base.clone().add(x.clone().multiply(lx)).add(0, y, 0).add(f.clone().multiply(0.35));
                    pr.teleport(at);
                    pr.setShooter(n.pl);
                    call(pr, "setDirection", Vector.class, out.clone().normalize());          // fireballs keep accelerating along it
                    pr.setVelocity(out);
                    pr.addScoreboardTag("chacademia_reflected");
                    n.fx("reflect", at);
                }
                return age < 115;
            }, () -> PANES.remove(id, pane));
            return true;
        });

        // 409 삼각 별자리 (TriadConstellation): triangle (circumradius 3.2, stars at local (0,-3.2) (∓2.77,1.6)) on the aimed ground;
        // at 1.3 s enemies inside take damage + stun 1 s (status engine: boss rule, artifacts)
        m.put("triad_constellation", n -> {
            Location P = groundAim(n, n.d("range", 18));
            Vector f = n.flat(), x = side(f);
            double R = n.d("r", 3.2);
            n.fx("main", P);
            n.later(25, () -> {
                for (LivingEntity e : n.near(P.clone().add(0, 1, 0), R + 1.5)) {
                    Vector rel = e.getLocation().toVector().subtract(P.toVector());
                    double lx = rel.dot(x), lz = rel.dot(f), dy = rel.getY(), m0 = e.getWidth() / 2;
                    if (dy < -1.5 || dy > 3) continue;
                    // inside: distance to each edge (inradius R/2, outward normals opposite to the vertices)
                    if (lz > R / 2 + m0) continue;                                                     // edge opposite the (0,-R) star
                    if ((lx * 0.8660 - lz * 0.5) > R / 2 + m0 || (-lx * 0.8660 - lz * 0.5) > R / 2 + m0) continue;
                    if (!n.hit(e, n.dmg())) continue;
                    n.status(e, "stun:20");                                                  // 보스 규칙은 엔진이 적용
                    n.fx("stun", e.getLocation(), e);
                }
            });
            return true;
        });

        // 410 해시계 바늘 (SundialSweep): sundial r 4.5 at the caster's feet; 0.9–2.4 s the needle turns once clockwise from the front
        // (θ = 4.19 (t − 0.9)), every enemy it passes takes damage once; closing flash at 2.4 s = x0.5 on everyone inside
        m.put("sundial_sweep", n -> {
            Location P = Nova.ground(n.pl.getLocation().add(0, 0.5, 0));
            Vector f = n.flat(), x = side(f);
            double R = n.d("r", 4.5);
            n.fx("main", P);
            Set<UUID> swept = new HashSet<>();
            n.loop(49, age -> {
                if (age >= 18 && age <= 47) {
                    double th0 = age == 18 ? -0.05 : 4.19 * (age - 19) / 20.0, th1 = 4.19 * (age - 18) / 20.0;
                    for (LivingEntity e : inCircle(n, P, R)) {
                        if (swept.contains(e.getUniqueId())) continue;
                        Vector rel = e.getLocation().toVector().subtract(P.toVector());
                        double phi = Math.atan2(rel.dot(x), rel.dot(f));
                        if (phi < 0) phi += 2 * Math.PI;
                        double d = Math.hypot(rel.dot(x), rel.dot(f)), tol = Math.atan2(e.getWidth() / 2 + 0.3, Math.max(0.5, d));
                        if (phi < th0 - tol || phi > th1 + tol) continue;
                        swept.add(e.getUniqueId());
                        n.hit(e, n.dmg());
                    }
                }
                if (age == 48) { for (LivingEntity e : inCircle(n, P, R)) n.hit(e, n.dmg(0.5)); return false; }
                return true;
            }, null);
            return true;
        });

        // 411 남십자성 (SouthernCross): 4 stars land in a cross on the aimed ground, lines join, centre explodes at 1.45 s → r 3.5 damage
        m.put("southern_cross", n -> {
            Location P = groundAim(n, n.d("range", 18));
            double R = n.d("r", 3.5);
            n.fx("main", P);
            n.later(28, () -> { for (LivingEntity e : inCircle(n, P, R)) n.hit(e, n.dmg()); });
            return true;
        });

        // 412 색유리 파편 (StainedShards): rose window breaks at 0.6 s over the aimed ground; shard rain hits r 3.5 at 1.05 / 1.55 / 2.05 s,
        // last big wave at 2.62 s (x2.5)
        m.put("stained_shards", n -> {
            Location P = groundAim(n, n.d("range", 20));
            double R = n.d("r", 3.5);
            n.fx("main", P);
            for (int k = 0; k < 3; k++) n.later(20 + 10 * k, () -> { for (LivingEntity e : inCircle(n, P, R)) n.hit(e, n.dmg()); });
            n.later(51, () -> { for (LivingEntity e : inCircle(n, P, R)) n.hit(e, n.dmg(2.5)); });
            return true;
        });

        // 413 별 잇기 (StarLink): star A 1.5 m in front of the caster, star B on the aimed ground (≤ 14); line A–B (1.2 m up) from 0.35 s,
        // 0.6–3.2 s every enemy touching it takes damage, at most once per 0.5 s
        m.put("star_link", n -> {
            Vector f = n.flat();
            Location A = Nova.ground(n.pl.getLocation().add(f.clone().multiply(1.5)).add(0, 1, 0));
            Location B = groundAim(n, n.d("range", 14));
            if (horiz(A, B) < 2) B = Nova.ground(A.clone().add(f.clone().multiply(3)).add(0, 1, 0));
            Location Bf = B;
            n.fx("star", A);
            n.fx("star", B);
            ArmorStand anchor = anchor(B, false);
            n.later(6, () -> { if (anchor.isValid()) n.fx("line", A.clone().add(0, 1.2, 0), anchor); });
            Map<UUID, Integer> last = new HashMap<>();
            Location a1 = A.clone().add(0, 1.2, 0), b1 = Bf.clone().add(0, 1.2, 0);
            Location mid = a1.clone().add(b1).multiply(0.5);
            double half = a1.distance(b1) / 2;
            n.loop(66, age -> {
                if (age >= 12 && age <= 64 && age % 2 == 0) {
                    for (LivingEntity e : n.near(mid, half + 2)) {
                        Integer lt = last.get(e.getUniqueId());
                        if (lt != null && age - lt < 10) continue;
                        Location on = onSegment(a1, b1, e);
                        if (on == null) continue;
                        last.put(e.getUniqueId(), age);
                        if (n.hit(e, n.dmg())) n.fx("hit", on);
                    }
                }
                return age < 65;
            }, () -> { if (anchor.isValid()) anchor.remove(); });
            return true;
        });

        // 414 등대 (Lighthouse): lighthouse on the aimed ground; 1.0–6.0 s two opposite beams (r 7) turn at 1.8 rad/s (+X → −Z):
        // beam over an enemy = x0.3 + burn 1.5 s (once per pass); lamp explodes at 6.05 s → r 6 full damage
        m.put("lighthouse", n -> {
            Location P = groundAim(n, n.d("range", 16));
            Vector f = n.flat(), x = side(f);
            double R = n.d("r", 7), boom = n.d("boom", 6);
            n.fx("main", P);
            Map<UUID, Integer> last = new HashMap<>();
            n.loop(122, age -> {
                if (age >= 20 && age <= 120 && age % 2 == 0) {
                    double a = 1.8 * (age - 20) / 20.0;
                    for (LivingEntity e : inCircle(n, P, R)) {
                        Vector rel = e.getLocation().toVector().subtract(P.toVector());
                        double lx = rel.dot(x), lz = rel.dot(f), d = Math.hypot(lx, lz);
                        double phi = Math.atan2(-lz, lx);
                        double off = ((phi - a) % Math.PI + Math.PI) % Math.PI;
                        off = Math.min(off, Math.PI - off);
                        if (off > Math.max(0.12, Math.atan2(e.getWidth() / 2 + 0.6, Math.max(0.5, d)))) continue;
                        Integer lt = last.get(e.getUniqueId());
                        if (lt != null && age - lt < 15) continue;
                        last.put(e.getUniqueId(), age);
                        if (n.hit(e, n.dmg(0.3))) n.status(e, "burn:30");
                    }
                }
                if (age == 121) { for (LivingEntity e : inCircle(n, P, boom)) n.hit(e, n.dmg()); return false; }
                return true;
            }, null);
            return true;
        });

        // 415 굴절 창 (RefractionSpear): 2 lenses ahead (≈30 % / 60 % of the aim distance, 1.4 m to either side) at 0.05 / 0.15 s;
        // spear at 0.45 s flies hand → lens 1 → lens 2 → aim point and on (30 blocks in all), piercing every enemy once;
        // where it finally stops: r 2 impact (x0.5) on enemies it did not pierce
        m.put("refraction_spear", n -> {
            double range = n.d("range", 30), speed = n.d("speed", 1.6), side = n.d("side", 1.4);
            Vector f = n.flat(), x = side(f), look = n.look();
            n.fx("cast", n.pl.getLocation());
            Location eye = n.pl.getEyeLocation(), aim = aimPoint(n, range);
            double D = Math.max(3, eye.distance(aim));
            Location L1 = lensAt(eye, look, x, Math.min(4, D * 0.3), side), L2 = lensAt(eye, look, x, Math.min(8, D * 0.6), -side);
            n.later(1, () -> n.fx("lens", L1));
            n.later(3, () -> n.fx("lens", L2));
            Set<UUID> pierced = new HashSet<>();
            List<Location> path = List.of(L1, L2, aim);
            n.later(9, () -> spear(n, n.hand(), path, 0, pierced, speed, range));
            return true;
        });

        // 416 북두칠성 (BigDipper): on the aimed ground the 7 stars land one by one at 0.95 + 0.3k s (r 1.2, x0.25 each, star layout
        // from the pack), then the star ladle slams at 3.3 s: bowl centre local (1, −0.6), r 5.5 full damage + slow 2 s
        m.put("big_dipper", n -> {
            Location P = groundAim(n, n.d("range", 20));
            Vector f = n.flat();
            double[][] star = {{-5, 1.6}, {-3.4, 0.6}, {-1.9, 0.2}, {-0.4, 0.3}, {-0.1, -1.4}, {2, -1.6}, {2.4, 0.4}};
            n.fx("main", P);
            for (int k = 0; k < 7; k++) {
                double[] s = star[k];
                n.later(18 + 6 * k, () -> {
                    Location at = Nova.ground(local(P, f, s[0], 1.5, s[1]));
                    for (LivingEntity e : inCircle(n, at, n.d("star_r", 1.2))) n.hit(e, n.dmg(0.25));
                });
            }
            n.later(65, () -> {
                Location bowl = Nova.ground(local(P, f, 1.0, 1.5, -0.6));
                for (LivingEntity e : inCircle(n, bowl, n.d("r", 5.5))) if (n.hit(e, n.dmg())) n.status(e, "slow:40:0.3");
            });
            return true;
        });

        // 417 거울 미궁 (MirrorMaze): 4 mirrors (r 2.6) around the target's feet. Enemies inside at 0.7 s are rooted (속박) until 3.9 s
        // (status engine: bosses immune, artifacts shorten it); reflected beams hit the caged at 2.1 / 2.6 / 3.1 s (x0.25), 3.9 s blast r 3 full
        m.put("mirror_maze", n -> {
            LivingEntity t = pick(n, n.d("range", 16), n.d("mana", 55));
            if (t == null) return false;
            Location P = Nova.ground(t.getLocation().add(0, 0.5, 0));
            double R = n.d("r", 2.6);
            n.fx("main", P);
            Set<LivingEntity> caged = new LinkedHashSet<>();
            n.loop(80, age -> {
                if (age == 14) {                                                  // 감금 = 속박 3.2초 (보스 면역·아티팩트 감소는 엔진)
                    caged.addAll(inCircle(n, P, R));
                    for (LivingEntity e : caged) n.status(e, "root:" + n.i("root", 64));
                }
                if (age == 42 || age == 52 || age == 62)
                    for (LivingEntity e : caged) if (alive(e) && horiz(e.getLocation(), P) <= R + 0.6) n.hit(e, n.dmg(0.25));
                if (age == 78) { for (LivingEntity e : inCircle(n, P, n.d("boom", 3))) n.hit(e, n.dmg()); return false; }
                return true;
            }, null);
            return true;
        });

        // 418 오리온의 활 (OrionBow): star bow over and behind the caster (fixed where cast); at 1.45 s one thick arrow leaves local
        // (0, 4.4, 1.3) toward where the caster aims then (40 blocks), piercing all (full damage each); r 2 impact x0.5 where it stops
        m.put("orion_bow", n -> {
            double range = n.d("range", 40), speed = n.d("speed", 3);
            Location o = n.pl.getLocation();
            Vector f = n.flat();
            n.fx("bow", o);
            n.later(28, () -> {
                if (n.pl.isDead()) return;
                Location from = local(o, f, 0, 4.4, 1.3);
                if (from.getWorld() != n.pl.getWorld() || !Nova.passable(from) || !clear(n.pl.getEyeLocation(), from)) from = n.hand();
                Vector dir = aimPoint(n, range).toVector().subtract(from.toVector());
                Set<UUID> pierced = new HashSet<>();
                go(n, n.shotFrom(from, dir.lengthSquared() < 1e-4 ? n.look() : dir, speed, range + 6, "arrow").radius(1.0)
                    .hit((t, p) -> { pierced.add(t.getUniqueId()); if (n.hit(t, n.dmg())) n.fx("pierce", Nova.chest(t)); return true; })
                    .end((p, wall) -> {
                        n.fx("impact", p);
                        for (LivingEntity e : n.near(p, 2)) if (!pierced.contains(e.getUniqueId())) n.hit(e, n.dmg(0.5));
                    }));
            });
            return true;
        });

        // 419 카시오페이아 (Cassiopeia): up to 5 different enemies within 12 in front (in sight), ordered across the caster's view so the
        // chain draws a W; first bolt at 0.5 s, each bolt 0.1 s, next leaves on arrival; damage on arrival, the last one = finale x1.6
        m.put("cassiopeia", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat(), x = side(f);
            List<LivingEntity> list = new ArrayList<>();
            for (LivingEntity e : n.near(o.clone().add(0, 1, 0), n.d("range", 12), 16)) {
                if (list.size() >= 5) break;
                if (e.getLocation().toVector().subtract(o.toVector()).dot(f) < -1 || !n.pl.hasLineOfSight(e)) continue;
                list.add(e);
            }
            if (list.isEmpty()) { n.warn("마법을 걸 대상이 없어요"); n.refund(n.d("mana", 50)); return false; }
            list.sort(Comparator.comparingDouble(e -> -e.getLocation().toVector().subtract(o.toVector()).dot(x)));
            n.fx("sign", o);
            n.later(9, () -> chain(n, list, 0, n.hand()));
            return true;
        });

        // 420 성운 직조 (NebulaWeave): sky canvas (r 6, 10 m up) over the aimed ground. 0.8–3.3 s, every 0.5 s a star is pinned where the
        // caster is looking (kept inside the canvas) and joined to the previous one; 3.5–4.75 s light falls under each star in order
        // (lands +0.45 s, r 1.8, x0.45, once per 0.5 s per enemy); finale at 5.6 s: r 6 x0.7
        m.put("nebula_weave", n -> {
            Location C = groundAim(n, n.d("range", 20));
            double R = n.d("r", 6), sky = n.d("sky", 10);
            n.fx("canvas", C);
            List<Location> nodes = new ArrayList<>();
            for (int k = 0; k < 6; k++) {
                n.later(15 + 10 * k, () -> {
                    if (n.pl.isDead() || n.pl.getWorld() != C.getWorld()) return;
                    Vector h = aimPoint(n, 32).toVector().subtract(C.toVector()).setY(0);
                    if (h.length() > R) h.normalize().multiply(R);
                    Location node = C.clone().add(h).add(0, sky, 0);
                    nodes.add(node);
                    n.fx("node", node);
                    if (nodes.size() > 1) {
                        Location prev = nodes.get(nodes.size() - 2);
                        fly(n, "thread", prev, () -> node, 9, 0, p -> n.fx("knot", node));
                    }
                });
            }
            Map<UUID, Integer> last = new HashMap<>();
            for (int k = 0; k < 6; k++) {
                int kk = k;
                n.later(69 + 4 * k, () -> {
                    if (kk >= nodes.size()) return;
                    Location node = nodes.get(kk);
                    Location g = Nova.ground(new Location(C.getWorld(), node.getX(), C.getY() + 4, node.getZ()));
                    n.fx("rain", g);
                    n.later(8, () -> {
                        int now = Bukkit.getCurrentTick();
                        for (LivingEntity e : inCircle(n, g, n.d("rain_r", 1.8))) {
                            Integer lt = last.get(e.getUniqueId());
                            if (lt != null && now - lt < 10) continue;
                            last.put(e.getUniqueId(), now);
                            n.hit(e, n.dmg(0.45));
                        }
                    });
                });
            }
            n.later(111, () -> { for (LivingEntity e : inCircle(n, C, R)) n.hit(e, n.dmg(0.7)); });
            return true;
        });

        // 421 거울 세계 (MirrorRealm): mirror gate 1.2 m to the caster's side; for 5 s a mirror double (armor stand wearing the caster's
        // head / armour / held item, mirrored across the gate every tick) copies the caster's hits: each hit the caster lands on an enemy
        // (≥ 0.5 s apart, max 8) is echoed 0.12 s later for 40 % of it (capped at this spell's damage)
        m.put("mirror_realm", n -> {
            UUID id = n.pl.getUniqueId();
            if (REALMS.containsKey(id)) { n.warn("거울 세계가 이미 열려 있어요"); n.refund(n.d("mana", 110)); return false; }
            Location o = n.pl.getLocation();
            Vector f = n.flat();
            Vector x = null;
            Location spot = null;
            for (int s : new int[]{1, -1}) {
                Vector xs = side(f).multiply(s);
                Location c = o.clone().add(xs.clone().multiply(2.4)).add(0, 0.5, 0);
                Location st = c.isChunkLoaded() ? Nova.stand(c) : null;
                if (st != null && Math.abs(st.getY() - o.getY()) <= 1.5 && n.plugin.protection().at(st)) { x = xs; spot = st; break; }
            }
            if (spot == null) { n.warn("거울상이 설 자리가 없어요"); n.refund(n.d("mana", 110)); return false; }
            Vector xn = x;
            Location G = Nova.ground(o.clone().add(xn.clone().multiply(1.2)).add(0, 1, 0));
            spot.setX(o.getX() + xn.getX() * 2.4); spot.setZ(o.getZ() + xn.getZ() * 2.4);
            spot.setDirection(mirror(n.pl.getLocation().getDirection(), xn));
            n.fx("gate", G);
            ArmorStand body = anchor(spot, false);
            body.setMarker(true);
            body.setArms(true);
            dress(body, n.pl);
            Realm rm = new Realm(n, body);
            REALMS.put(id, rm);
            n.fx("aura", spot, body);
            n.loop(100, age -> {
                if (REALMS.get(id) != rm || !body.isValid() || n.pl.isDead() || n.pl.getWorld() != G.getWorld()) return false;
                if (age == 8) body.setInvisible(false);
                Location p = n.pl.getLocation();
                Vector rel = p.toVector().subtract(G.toVector());
                double d = rel.dot(xn);
                Location to = p.clone().subtract(xn.clone().multiply(2 * d));
                to.setDirection(mirror(p.getDirection(), xn));
                if (horiz(to, G) <= 12 && Nova.passable(to) && Nova.passable(to.clone().add(0, 1, 0)) && n.plugin.protection().at(to)) body.teleport(to);
                else { Location keep = body.getLocation(); keep.setDirection(to.getDirection()); body.teleport(keep); }
                return age < 99;
            }, () -> { REALMS.remove(id, rm); if (body.isValid()) body.remove(); });
            return true;
        });

        // 422 천체 망원경 (CelestialScope): 10 s long-range scope; each arm swing (left click) charges 0.47 s then fires a star shot
        // along the look (80 blocks, 6 blocks/tick, first enemy only, full damage). 3 shots, ≥ 0.8 s apart; the mode ends after the 3rd
        m.put("celestial_scope", n -> {
            UUID id = n.pl.getUniqueId();
            if (SCOPES.containsKey(id)) return false;
            Scope s = new Scope(n, Bukkit.getCurrentTick());
            SCOPES.put(id, s);
            n.fx("scope", n.pl.getLocation(), n.pl);
            n.info("천체 망원경: 10초 동안 좌클릭으로 별빛 저격 3발");
            n.loop(200, age -> SCOPES.get(id) == s && !n.pl.isDead() && (s.shots < 3 || s.charging), () -> SCOPES.remove(id, s));
            return true;
        });

        // 423 은하수 (MilkyWay): river 3 m wide, local z 1 → 17 in front of the caster (fixed where cast). 1.3–3.8 s every 0.5 s enemies on
        // it take x0.12 + burn 1.5 s and are pushed downstream; 4.7–5.2 s the great wave (front z = 1.6 + 32 (t − 4.7)) throws them on;
        // river end (z 17) bursts at 5.2 s: r 3 x0.6
        m.put("milky_way", n -> {
            Location o = n.pl.getLocation();
            Vector f = n.flat(), x = side(f);
            n.fx("main", o);
            Set<UUID> surged = new HashSet<>();
            n.loop(105, age -> {
                if (age >= 26 && age <= 76 && (age - 26) % 10 == 0) {
                    for (LivingEntity e : river(n, o, f, x, 17)) {
                        if (!n.hit(e, n.dmg(0.12))) continue;
                        n.status(e, "burn:30");
                        n.fx("burn", Nova.chest(e));
                        push(n, e, f, 0.35, 0.05);
                    }
                }
                if (age >= 94 && age <= 103) {
                    double front = 1.6 + 32 * (age - 94) / 20.0;
                    for (LivingEntity e : river(n, o, f, x, Math.min(17, front))) {
                        if (!surged.add(e.getUniqueId())) continue;
                        push(n, e, f, 1.1, 0.35);
                        n.fx("burn", Nova.chest(e));
                    }
                }
                if (age == 104) {
                    Location end = Nova.ground(o.clone().add(f.clone().multiply(17)).add(0, 1.5, 0));
                    for (LivingEntity e : inCircle(n, end, 3)) n.hit(e, n.dmg(0.6));
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 424 황도 12궁 (ZodiacWheel): over the aimed ground; 12 constellations fire star spears in turn: landing k at 2.65 + 0.4k s on
        // local (cos 2.399k, sin 2.399k)·(1.6 + 1.35 (k mod 4)), r 1.8 x0.15 + slow 1.5 s; ring collapses at 8.2 s (r 8 x0.5) and
        // slams down at 8.75 s (r 8 full)
        m.put("zodiac_wheel", n -> {
            Location P = groundAim(n, n.d("range", 24));
            Vector f = n.flat();
            double R = n.d("r", 8);
            n.fx("main", P);
            for (int k = 0; k < 12; k++) {
                double rr = 1.6 + 1.35 * (k % 4), a = 2.399 * k;
                n.later(52 + 8 * k, () -> {
                    Location at = Nova.ground(local(P, f, Math.cos(a) * rr, 2, Math.sin(a) * rr));
                    for (LivingEntity e : inCircle(n, at, 1.8)) if (n.hit(e, n.dmg(0.15))) n.status(e, "slow:30:0.3");
                });
            }
            n.later(163, () -> { for (LivingEntity e : inCircle(n, P, R)) n.hit(e, n.dmg(0.5)); });
            n.later(174, () -> { for (LivingEntity e : inCircle(n, P, R)) { if (n.hit(e, n.dmg())) n.knock(e, P, f, 0.4, 0.45); } });
            return true;
        });

        // 425 초신성 (Supernova): a star is born 12 m over the aimed ground; 1.0–6.6 s it swells and draws enemies within 12 toward the
        // centre (n.pull: anchored / immune skipped, bosses scaled); 7.3 s supernova: r 10 full damage + thrown out; 8.9 s the white core falls: r 4 x0.6;
        // falling stars 9.5 / 10.0 / 10.5 s: 3 random spots within 13, r 2 x0.1
        m.put("supernova", n -> {
            Location P = groundAim(n, n.d("range", 24));
            Vector f = n.flat();
            n.fx("main", P);
            n.loop(212, age -> {
                if (age >= 20 && age < 132 && age % 5 == 0) {
                    for (LivingEntity e : inCircle(n, P, 12)) {
                        Vector in = P.toVector().subtract(e.getLocation().toVector()).setY(0);
                        if (in.length() < 2) continue;
                        n.pull(e, P, 0.18, 0);                                          // 끌기 도우미 (보스 감소)
                    }
                }
                if (age == 146) for (LivingEntity e : inCircle(n, P, n.d("r", 10))) if (n.hit(e, n.dmg())) n.knock(e, P, f, 0.8, 0.4);
                if (age == 178) for (LivingEntity e : inCircle(n, P, 4)) n.hit(e, n.dmg(0.6));
                if (age == 190 || age == 200 || age == 210) {
                    var rnd = java.util.concurrent.ThreadLocalRandom.current();
                    for (int k = 0; k < 3; k++) {
                        double a = rnd.nextDouble(0, 2 * Math.PI), d = rnd.nextDouble(2, 13);
                        Location at = Nova.ground(P.clone().add(Math.cos(a) * d, 2, Math.sin(a) * d));
                        for (LivingEntity e : inCircle(n, at, 2)) n.hit(e, n.dmg(0.1));
                    }
                }
                return age < 211;
            }, null);
            return true;
        });
    }

    // ================================================================== shots and flights
    /** Fires a shot of this class; while the caster has a mirror pane (408) the shot bends at the pane toward the nearest enemy ahead. */
    private static Nova.Shot go(Nova n, Nova.Shot s) {
        var prev = s.onStep;
        boolean[] bent = {false};
        s.onStep = (p, d) -> {
            if (prev != null) prev.accept(p, d);
            if (!bent[0]) bent[0] = bend(n, s);
        };
        return s.fire();
    }

    private static boolean bend(Nova n, Nova.Shot s) {
        Pane pane = PANES.get(n.pl.getUniqueId());
        if (pane == null || pane.base().getWorld() != s.pos.getWorld()) return false;
        int now = Bukkit.getCurrentTick();
        if (now < pane.from() || now > pane.until()) return false;
        double df = s.dir.dot(pane.f());
        if (df < 0.05) return false;
        double z1 = s.pos.toVector().subtract(pane.base().toVector()).dot(pane.f()), z0 = z1 - df * s.speed;
        if (z0 > 0 || z1 < 0) return false;
        Location cross = s.pos.clone().subtract(s.dir.clone().multiply(z1 / df));
        Vector rel = cross.toVector().subtract(pane.base().toVector());
        if (Math.abs(rel.dot(pane.x())) > 1.1 || rel.getY() < 0.1 || rel.getY() > 2.8) return false;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : n.near(cross, 18, 12)) {
            Vector to = Nova.chest(e).toVector().subtract(cross.toVector());
            if (to.dot(pane.f()) < 0.5) continue;
            double d = to.lengthSquared();
            if (d < bd) { bd = d; best = e; }
        }
        pane.n().fx("refract", cross);
        if (best == null) return true;
        Vector to = Nova.chest(best).toVector().subtract(cross.toVector());
        s.pos = cross.add(to.clone().normalize().multiply(0.1));
        s.dir = to.normalize();
        s.homing(best, 0.25);
        s.range = s.travelled + Math.sqrt(bd) + 4;
        return true;
    }

    /** A system moved by the server from `from` to the (moving) point `to` in `ticks`, arcing `arc` blocks up; then arrive(point). */
    private static void fly(Nova n, String system, Location from, Supplier<Location> to, int ticks, double arc, Consumer<Location> arrive) {
        Location e0 = to.get();
        Location[] end = {e0 == null ? from.clone() : e0};
        Vector d0 = end[0].toVector().subtract(from.toVector());
        GameplayVisual vis = n.shot(system, from, d0.lengthSquared() < 1e-6 ? n.look() : d0);
        Location[] last = {from.clone()};
        n.loop(ticks, age -> {
            Location e = to.get();
            if (e != null && e.getWorld() == from.getWorld()) end[0] = e;
            double s = Math.min(1, (age + 1) / (double) ticks);
            Location p = from.clone().add(end[0].toVector().subtract(from.toVector()).multiply(s)).add(0, arc * 4 * s * (1 - s), 0);
            Vector d = p.toVector().subtract(last[0].toVector());
            if (vis != null) vis.move(p, d.lengthSquared() < 1e-6 ? n.look() : d);
            last[0] = p;
            return s < 1;
        }, () -> {
            if (vis != null) vis.close();
            if (n.pl.isOnline()) arrive.accept(last[0].clone());
        });
    }

    /** 405: one shard flight; the first wall reflects it once. */
    private static void shard(Nova n, Location from, Vector dir, double range, boolean bounced) {
        double speed = n.d("speed", 1.3);
        boolean[] hit = {false};
        Nova.Shot[] ref = new Nova.Shot[1];
        ref[0] = n.shotFrom(from, dir, speed, range, "shard").radius(0.45)
            .hit((t, p) -> { hit[0] = true; n.hit(t, n.dmg(bounced ? n.d("bonus", 1.3) : 1)); n.fx("impact", p); return false; })
            .end((p, wall) -> {
                if (hit[0]) return;
                if (wall && !bounced) {
                    Vector d = ref[0].dir.clone();
                    var rt = p.getWorld().rayTraceBlocks(p.clone().subtract(d.clone().multiply(0.3)), d, 1.0, FluidCollisionMode.NEVER, true);
                    double left = range - ref[0].travelled;
                    if (rt != null && rt.getHitBlockFace() != null && left > 1) {
                        Vector nrm = rt.getHitBlockFace().getDirection();
                        Vector r = d.clone().subtract(nrm.clone().multiply(2 * d.dot(nrm)));
                        n.fx("reflect", p);
                        Location next = p.clone().add(nrm.clone().multiply(0.15));
                        n.later(0, () -> shard(n, next, r, left, true));
                        return;
                    }
                }
                n.fx("impact", p);
            });
        go(n, ref[0]);
    }

    /** 406: one hop to `t`; on arrival damage + the next hop (nearest unhit enemy within 6, in sight of `t`). */
    private static void hop(Nova n, Location from, LivingEntity t, int left, Set<UUID> done) {
        fly(n, "hop", from, () -> alive(t) ? Nova.chest(t) : null, 5, 1.2, p -> {
            if (!alive(t) || t.getWorld() != p.getWorld()) { n.fx("hit", p); return; }
            done.add(t.getUniqueId());
            n.hit(t, n.dmg());
            n.fx("hit", p);
            if (left <= 1) return;
            LivingEntity next = null;
            for (LivingEntity e : n.near(p, n.d("hop_r", 6), 8)) if (!done.contains(e.getUniqueId()) && e.hasLineOfSight(t)) { next = e; break; }
            if (next == null) return;
            LivingEntity nx = next;
            hop(n, p, nx, left - 1, done);
        });
    }

    /** 415: spear segment k (to path[k]); lenses refract into the next segment, the last one flies on to the range end. */
    private static void spear(Nova n, Location from, List<Location> path, int k, Set<UUID> pierced, double speed, double budget) {
        Location to = path.get(k);
        boolean last = k == path.size() - 1;
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.3 && !last) { spear(n, from, path, k + 1, pierced, speed, budget); return; }
        if (len < 0.3) d = n.look();
        go(n, n.shotFrom(from, d, speed, last ? Math.max(len, budget) : len, "spear").radius(0.7)
            .hit((t, p) -> { if (pierced.add(t.getUniqueId()) && n.hit(t, n.dmg())) n.fx("pierce", Nova.chest(t)); return true; })
            .end((p, wall) -> {
                if (!last && !wall) { n.fx("refract", to); spear(n, to, path, k + 1, pierced, speed, Math.max(4, budget - len)); return; }
                n.fx("impact", p);
                for (LivingEntity e : n.near(p, 2)) if (!pierced.contains(e.getUniqueId())) n.hit(e, n.dmg(0.5));
            }));
    }

    /** 419: bolt k of the chain (0.1 s flight), the last target gets the finale. */
    private static void chain(Nova n, List<LivingEntity> list, int k, Location from) {
        if (k >= list.size()) return;
        LivingEntity t = list.get(k);
        boolean fin = k == list.size() - 1;
        fly(n, "bolt", from, () -> alive(t) ? Nova.chest(t) : null, 2, 0, p -> {
            if (alive(t)) n.hit(t, n.dmg(fin ? n.d("finale", 1.6) : 1));
            n.fx(fin ? "finale" : "hit", p);
            chain(n, list, k + 1, p);
        });
    }

    // ================================================================== geometry
    /** Runtime local +X (= up × forward) for the horizontal facing f. */
    private static Vector side(Vector f) { return new Vector(f.getZ(), 0, -f.getX()); }
    /** o + local (x right, y up, z forward) in the frame of the horizontal facing f. */
    private static Location local(Location o, Vector f, double x, double y, double z) {
        return o.clone().add(side(f).multiply(x)).add(0, y, 0).add(f.clone().multiply(z));
    }
    private static double horiz(Location a, Location b) { return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()); }
    private static Vector mirror(Vector d, Vector nrm) { return d.clone().subtract(nrm.clone().multiply(2 * d.dot(nrm))); }
    private static boolean alive(LivingEntity e) { return e != null && e.isValid() && !e.isDead(); }

    /** First block or enemy along the look within `range` (else the end of the ray). */
    private static Location aimPoint(Nova n, double range) {
        Player pl = n.pl;
        Location eye = pl.getEyeLocation();
        Vector d = n.look();
        var hit = pl.getWorld().rayTrace(eye, d, range, FluidCollisionMode.NEVER, true, 0.3, e -> e != pl && n.enemy(e));
        if (hit != null) return hit.getHitPosition().toLocation(pl.getWorld());
        return eye.add(d.multiply(range));
    }

    /** The Bridge aim point, limited to `range` horizontally, put on the ground. */
    private static Location groundAim(Nova n, double range) {
        Location o = n.pl.getLocation(), a = n.aim();
        if (a.getWorld() != o.getWorld()) a = o.clone();
        Vector h = a.toVector().subtract(o.toVector()).setY(0);
        if (h.length() > range) { double y = a.getY(); a = o.clone().add(h.normalize().multiply(range)); a.setY(y); }
        return Nova.ground(a.clone().add(0, 1.5, 0));
    }

    /** Enemies standing within horizontal r of the ground point (feet −1.5 … +3 blocks around it). */
    private static List<LivingEntity> inCircle(Nova n, Location at, double r) {
        var out = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(at.clone().add(0, 1, 0), r + 2.5)) {
            Location l = e.getLocation();
            double dy = l.getY() - at.getY();
            if (dy < -1.5 || dy > 3 || horiz(l, at) > r + e.getWidth() / 2) continue;
            out.add(e);
        }
        return out;
    }

    /** 423: enemies on the river (|x| ≤ 1.5, 1 ≤ z ≤ zMax from the caster's cast spot). */
    private static List<LivingEntity> river(Nova n, Location o, Vector f, Vector x, double zMax) {
        var out = new ArrayList<LivingEntity>();
        Location mid = o.clone().add(f.clone().multiply(9)).add(0, 1, 0);
        for (LivingEntity e : n.near(mid, 11)) {
            Vector rel = e.getLocation().toVector().subtract(o.toVector());
            double z = rel.dot(f), lx = Math.abs(rel.dot(x)), dy = rel.getY();
            if (z < 1 || z > zMax || lx > 1.5 + e.getWidth() / 2 || dy < -2 || dy > 3) continue;
            out.add(e);
        }
        return out;
    }

    /** 413: the point of segment a–b over the enemy, or null when the line does not touch it. */
    private static Location onSegment(Location a, Location b, LivingEntity e) {
        Vector ab = b.toVector().subtract(a.toVector());
        Vector h = ab.clone().setY(0);
        double len2 = h.lengthSquared();
        Location l = e.getLocation();
        double s = len2 < 1e-4 ? 0 : Math.clamp(l.toVector().subtract(a.toVector()).setY(0).dot(h) / len2, 0, 1);
        Location p = a.clone().add(ab.clone().multiply(s));
        if (Math.hypot(p.getX() - l.getX(), p.getZ() - l.getZ()) > 0.6 + e.getWidth() / 2) return null;
        if (p.getY() < l.getY() - 0.3 || p.getY() > l.getY() + e.getHeight() + 0.3) return null;
        return p;
    }

    /** 415: lens point `dist` along the look and `off` to the side, pulled back in front of blocks. */
    private static Location lensAt(Location eye, Vector look, Vector x, double dist, double off) {
        Vector v = look.clone().multiply(dist).add(x.clone().multiply(off));
        return Nova.reach(eye, v, v.length());
    }

    /** No block between a and b. */
    private static boolean clear(Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        return len < 0.1 || a.getWorld().rayTraceBlocks(a, d.normalize(), len, FluidCollisionMode.NEVER, true) == null;
    }

    /** Horizontal cone in front of the caster (feet origin), in sight (same as NovaBasic). */
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

    // ================================================================== targets / movement
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

    /** Velocity push along `dir` (horizontal), boss scaling from Targets.knock. */
    private static void push(Nova n, LivingEntity t, Vector dir, double power, double up) {
        Vector d = dir.clone().setY(0);
        if (d.lengthSquared() < 1e-4) return;
        n.knock(t, t.getLocation().subtract(d.normalize()), d, power, up);
    }

    /** Lets a planned rapid follow-up hit land (vanilla hurt cooldown would swallow needles 3 ticks apart / echo hits). */
    private static void fresh(LivingEntity t) { if (alive(t)) t.setNoDamageTicks(0); }

    /** 408: hostile projectile (same rule as Guards.hostileShot / NovaMid2.catchable). */
    private static boolean hostile(Nova n, Projectile pr) {
        if (pr.getScoreboardTags().contains("chacademia_reflected") || !n.plugin.protection().pair(n.pl, pr.getLocation())) return false;
        Object src = pr.getShooter();
        if (!(src instanceof Entity shooter)) return true;
        if (shooter.getUniqueId().equals(n.pl.getUniqueId())) return false;
        if (shooter instanceof Player) return n.enemy(shooter);
        return !n.plugin.targets().excluded(shooter);
    }

    /** Visual-only armor stand (never targeted: Targets.excluded skips armor stands). */
    private static ArmorStand anchor(Location at, boolean visible) {
        return at.getWorld().spawn(at, ArmorStand.class, a -> {
            a.setInvisible(!visible); a.setGravity(false); a.setBasePlate(false); a.setInvulnerable(true);
            a.setPersistent(false); a.setSilent(true); a.setCanPickupItems(false); a.addScoreboardTag(TAG);
        });
    }

    /** 421: the double wears the caster's head, armour and held item (copies). */
    private static void dress(ArmorStand a, Player pl) {
        var eq = a.getEquipment();
        var src = pl.getEquipment();
        if (eq == null || src == null) return;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        var meta = head.getItemMeta();
        try {
            if (meta != null && call(meta, "setOwningPlayer", Class.forName("org.bukkit.OfflinePlayer"), pl)) head.setItemMeta(meta);
        } catch (ClassNotFoundException ignored) {}
        eq.setHelmet(head);
        eq.setChestplate(copy(src.getChestplate()));
        eq.setLeggings(copy(src.getLeggings()));
        eq.setBoots(copy(src.getBoots()));
        eq.setItemInMainHand(copy(src.getItemInMainHand()));
    }
    private static ItemStack copy(ItemStack i) { return i == null ? null : i.clone(); }
    /** obj.name(arg) when obj has it (Fireball.setDirection, SkullMeta.setOwningPlayer); false otherwise. */
    private static boolean call(Object obj, String name, Class<?> type, Object arg) {
        try { obj.getClass().getMethod(name, type).invoke(obj, arg); return true; }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }

    // ================================================================== 422 scope shots
    private static void fire(Scope s) {
        int now = Bukkit.getCurrentTick();
        Nova n = s.n;
        if (s.charging || s.shots >= 3 || now - s.lastFire < 16 || now - s.start < 14 || n.pl.isDead()) return;
        s.charging = true;
        s.shots++;
        n.fx("charge", n.pl.getLocation(), n.pl);
        n.later(9, () -> {
            s.charging = false;
            s.lastFire = Bukkit.getCurrentTick();
            if (n.pl.isDead()) return;
            Location from = n.pl.getEyeLocation().add(n.look().multiply(0.8));
            boolean[] hit = {false};
            go(n, n.shotFrom(from, n.look(), n.d("speed", 6), n.d("range", 80), "shot").radius(0.5)
                .hit((t, p) -> { hit[0] = true; n.hit(t, n.dmg()); n.fx("hit", p); return false; })
                .end((p, wall) -> { if (!hit[0]) n.fx("hit", p); }));
        });
    }

    // ================================================================== lifecycle
    static void enable(MagicMythicPlugin plugin) {
        PANES.clear(); SCOPES.clear();
        for (Realm r : REALMS.values()) if (r.body.isValid()) r.body.remove();
        REALMS.clear();
        echoing = false;
        for (World w : Bukkit.getWorlds())
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class)) if (a.getScoreboardTags().contains(TAG)) a.remove();
        Bukkit.getPluginManager().registerEvents(new Hooks(), plugin);
    }

    static final class Hooks implements Listener {
        /** 421: the caster's hits are echoed by the mirror double. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void echo(EntityDamageByEntityEvent e) {
            if (echoing || REALMS.isEmpty() || !(e.getEntity() instanceof LivingEntity victim)) return;
            Entity d = e.getDamager();
            Player p = d instanceof Player pp ? pp : d instanceof Projectile pr && pr.getShooter() instanceof Player ps ? ps : null;
            if (p == null) return;
            Realm rm = REALMS.get(p.getUniqueId());
            if (rm == null || !rm.body.isValid()) return;
            Nova n = rm.n;
            int now = Bukkit.getCurrentTick();
            if (now - rm.last < 10 || rm.echoes >= n.i("echoes", 8) || !n.enemy(victim)) return;
            double amount = Math.min(e.getFinalDamage() * n.d("share", 0.4), n.dmg());
            if (amount < 0.5) return;
            rm.last = now;
            rm.echoes++;
            n.fx("echoCast", rm.body.getLocation(), rm.body);
            n.later(2, () -> {
                if (!alive(victim)) return;
                echoing = true;
                try { fresh(victim); if (n.hit(victim, amount)) n.fx("echo", Nova.chest(victim)); }
                finally { echoing = false; }
            });
        }

        /** 422: arm swing = scope shot. */
        @EventHandler(ignoreCancelled = true)
        public void swing(PlayerAnimationEvent e) {
            Scope s = SCOPES.get(e.getPlayer().getUniqueId());
            if (s != null) fire(s);
        }

        @EventHandler
        public void quit(PlayerQuitEvent e) {
            UUID id = e.getPlayer().getUniqueId();
            PANES.remove(id);
            SCOPES.remove(id);
            Realm r = REALMS.remove(id);
            if (r != null && r.body.isValid()) r.body.remove();
        }
    }
}
