package school.chacademia.magicmythic;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** chanova types for the 150-spell set (301~450), element dark. Rows: tools/nova150/dark.py. VFX: PC 차카데미 vfx_Claude\<Bundle>\NOTES.md. */
final class Nova150Dark {
    private Nova150Dark() {}

    // ------------------------------------------------------------------ live state (listeners in Hooks)
    /** 434 그림자극: caster -> shadow on the wall waiting to be struck. */
    private static final Map<UUID, Shadow> SHADOWS = new HashMap<>();
    /** 436 반달 방패: caster -> frontal guard collecting damage. */
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    /** 447 그믐 장막: caster -> hidden state (an attack breaks it early). */
    private static final Map<UUID, Veil> VEILS = new HashMap<>();
    /** 448 그림자 도플갱어: caster -> shadow behind an enemy copying the caster's attacks. */
    private static final Map<UUID, Doppel> DOPPELS = new HashMap<>();

    private static final class Shadow { final Location wall; final Runnable strike; final int until; Shadow(Location w, Runnable s, int u) { wall = w; strike = s; until = u; } }
    private static final class Guard { final Nova n; final int until; double stored; int lastFx = -100; Guard(Nova n, int u) { this.n = n; until = u; } }
    private static final class Veil { final Runnable reveal; final int until; Veil(Runnable r, int u) { reveal = r; until = u; } }
    private static final class Doppel { final Nova n; final LivingEntity t; final Location spot; final int from, until; int last = -100; Doppel(Nova n, LivingEntity t, Location s, int f, int u) { this.n = n; this.t = t; spot = s; from = f; until = u; } }

    private static MagicMythicPlugin plugin;
    private static int now() { return Bukkit.getCurrentTick(); }

    static void register(Map<String, NovaMech.Impl> m) {
        // 426 초승달 낫 (CrescentSickle): slash attached at the cast, blade crosses at 0.20 s -> 2.8 m / ±70° cone: damage +
        // bleed (bleed system on the target; bleed ticks at +0.5 / +1.0 s = 25 % damage each)
        m.put("crescent_sickle", n -> {
            double r = n.d("r", 2.8), half = n.d("half", 70), bleed = n.d("bleed", 0.25);
            n.later(3, () -> {
                for (LivingEntity e : cone(n, r, half)) {
                    if (!n.hit(e, n.dmg())) continue;
                    n.fx("bleed", e.getLocation(), e);
                    for (int k = 1; k <= 2; k++) n.later(10 * k - 1, () -> { if (e.isValid() && !e.isDead()) n.hit(e, n.dmg(bleed)); });
                }
            });
            return true;
        });

        // 427 박쥐 떼 (BatFlurry): one swarm (4 bats in the projectile system) leaves the hand at +0.15 s and homes on the target
        // for ≤ 5.8 s (0.55 blocks/tick); first enemy touched -> damage + impact; a wall stops it
        m.put("bat_flurry", n -> {
            LivingEntity t = pick(n, n.d("range", 16));
            if (t == null) return false;
            n.later(3, () -> {
                Vector dir = Nova.chest(t).toVector().subtract(n.hand().toVector());
                boolean[] bit = {false};
                var s = n.shotFrom(n.hand(), dir.lengthSquared() < 1e-4 ? n.look() : dir, n.d("speed", 0.55), 60, "projectile")
                    .radius(0.7).homing(t, n.d("turn", 0.25))
                    .hit((e, p) -> { bit[0] = true; n.hit(e, n.dmg()); n.fx("impact", p); return false; })
                    .end((p, wall) -> { if (!bit[0]) n.fx("impact", p); });
                s.maxLife = 116;
                s.fire();
            });
            return true;
        });

        // 428 꿈 조각 (DreamSplinter): crystal shard at +0.1 s, 18 blocks; hit -> damage + daze 1.5 s: a mob forgets its target and
        // wanders to random spots (re-picked every 0.5 s), a player is nudged sideways, a boss is only slowed
        m.put("dream_splinter", n -> {
            int daze = n.i("ticks", 30);
            n.later(2, () -> {
                boolean[] hitOne = {false};
                n.shotFrom(n.hand(), n.look(), n.d("speed", 1.0), n.d("range", 18), "projectile").radius(0.5)
                    .hit((t, p) -> {
                        hitOne[0] = true;
                        n.fx("impact", p);
                        if (n.hit(t, n.dmg())) { n.fx("daze", t.getLocation(), t); daze(n, t, daze); }
                        return false;
                    })
                    .end((p, wall) -> { if (!hitOne[0]) n.fx("impact", p); })
                    .fire();
            });
            return true;
        });

        // 429 꼭두각시 당김 (MarionetteTug): thread (link hand -> target) + bind at the cast; caught at 0.18 s (damage),
        // dragged 0.30~0.70 s to 1.5 m in front of the caster (bosses: not moved)
        m.put("marionette_tug", n -> {
            LivingEntity t = pick(n, n.d("range", 12));
            if (t == null) return false;
            n.fx("tug", Nova.chest(t), t);
            n.fx("bind", t.getLocation(), t);
            n.later(3, () -> n.hit(t, n.dmg()));
            n.later(6, () -> {
                if (!t.isValid() || t.isDead() || n.boss(t) || t.getWorld() != n.pl.getWorld()) return;
                n.loop(8, age -> {
                    if (!t.isValid() || t.isDead()) return false;
                    Location dest = n.pl.getLocation().add(n.flat().multiply(1.5));
                    Vector to = dest.toVector().subtract(t.getLocation().toVector());
                    double len = to.length();
                    if (len < 0.8 || age >= 8 || len > 30) return false;
                    Vector v = to.multiply(1.0 / Math.max(1, 8 - age));
                    if (v.length() > 2.0) v.normalize().multiply(2.0);
                    t.setVelocity(v.add(new Vector(0, 0.08, 0)));
                    t.setFallDistance(0);
                    return true;
                }, () -> { if (t.isValid()) t.setVelocity(t.getVelocity().multiply(0.2)); });
            });
            return true;
        });

        // 430 달빛 화살 (MoonbeamDart): one fast moonlight arrow at +0.1 s, 24 blocks, first enemy -> damage + impact
        m.put("moonbeam_dart", n -> {
            n.later(2, () -> {
                boolean[] hitOne = {false};
                n.shotFrom(n.hand(), n.look(), n.d("speed", 1.6), n.d("range", 24), "projectile").radius(0.45)
                    .hit((t, p) -> { hitOne[0] = true; n.hit(t, n.dmg()); n.fx("impact", p); return false; })
                    .end((p, wall) -> { if (!hitOne[0]) n.fx("impact", p); })
                    .fire();
            });
            return true;
        });

        // 431 달 조각 (MoonShard): the little moon breaks at 0.22 s; 3 shards from the left / middle / right slots at
        // 0.25 / 0.28 / 0.31 s, yaw -0.3 / 0 / +0.3 rad, 14 blocks; every shard hits once
        m.put("moon_shard", n -> {
            double speed = n.d("speed", 1.1), range = n.d("range", 14), yaw = n.d("yaw", 0.3);
            int[] at = {5, 6, 6};
            double[][] slot = {{-0.22, 1.48, 0.66}, {0, 1.58, 0.70}, {0.22, 1.48, 0.66}};
            for (int k = 0; k < 3; k++) {
                int i = k;
                n.later(at[k], () -> {
                    Vector f = n.flat(), right = new Vector(-f.getZ(), 0, f.getX());
                    Location from = n.pl.getLocation().add(right.multiply(slot[i][0])).add(0, slot[i][1], 0).add(f.multiply(slot[i][2]));
                    Vector dir = n.look().rotateAroundY(-(i - 1) * yaw);
                    boolean[] hitOne = {false};
                    n.shotFrom(from, dir, speed, range, "projectile").radius(0.5)
                        .hit((t, p) -> { hitOne[0] = true; n.hit(t, n.dmg()); n.fx("impact", p); return false; })
                        .end((p, wall) -> { if (!hitOne[0]) n.fx("impact", p); })
                        .fire();
                });
            }
            return true;
        });

        // 432 검은 나방 (BlackMoth): slow moth swarm (0.4 blocks/tick) homes on the target ≤ 5.8 s; first enemy touched ->
        // damage + blindness 2 s (moth dust)
        m.put("black_moth", n -> {
            LivingEntity t = pick(n, n.d("range", 18));
            if (t == null) return false;
            String st = "blindness:" + n.i("ticks", 40);
            n.later(3, () -> {
                Vector dir = Nova.chest(t).toVector().subtract(n.hand().toVector());
                boolean[] bit = {false};
                var s = n.shotFrom(n.hand(), dir.lengthSquared() < 1e-4 ? n.look() : dir, n.d("speed", 0.4), 48, "projectile")
                    .radius(0.6).homing(t, n.d("turn", 0.2))
                    .hit((e, p) -> { bit[0] = true; if (n.hit(e, n.dmg())) n.status(e, st); n.fx("impact", p); return false; })
                    .end((p, wall) -> { if (!bit[0]) n.fx("impact", p); });
                s.maxLife = 116;
                s.fire();
            });
            return true;
        });

        // 433 달빛 표식 (LunarMark): mark on the target 4 s, strike starts at 4.0 s, lands at 4.12 s: target full damage,
        // other enemies within 1.8 m of it half damage. Target dead / gone -> nothing
        m.put("lunar_mark", n -> {
            LivingEntity t = pick(n, n.d("range", 18));
            if (t == null) return false;
            if (!n.hit(t, 0)) return refuse(n, "표식을 새길 수 없는 대상이에요");
            n.fx("mark", t.getLocation(), t);
            int delay = n.i("ticks", 80);
            n.later(delay - 1, () -> {
                if (!t.isValid() || t.isDead() || t.getWorld() != n.pl.getWorld()) return;
                n.fx("strike", t.getLocation(), t);
                n.later(2, () -> {
                    if (!t.isValid() || t.isDead()) return;
                    n.hit(t, n.dmg());
                    for (LivingEntity e : n.near(t.getLocation().add(0, 0.5, 0), n.d("splash_r", 1.8))) if (e != t) n.hit(e, n.dmg(0.5));
                });
            });
            return true;
        });

        // 434 그림자극 (ShadowPlay): the target's shadow is cast on the wall behind it (ray along caster -> target, ≤ 6 blocks;
        // no wall = on the ground 1.5 m behind). For 3 s, the caster swinging an arm while looking at the shadow (≤ 24 blocks)
        // strikes it: cut on the wall, echo on the target, damage at +0.05 s even through cover
        m.put("shadow_play", n -> {
            LivingEntity t = pick(n, n.d("range", 16));
            if (t == null) return false;
            Vector dir = Nova.flat(t.getLocation().toVector().subtract(n.pl.getLocation().toVector()));
            Location base = t.getLocation().add(0, 0.6, 0);
            var ray = t.getWorld().rayTraceBlocks(base, dir, n.d("wall", 6), FluidCollisionMode.NEVER, true);
            Location wall = ray == null ? t.getLocation().add(dir.clone().multiply(1.5))
                : ray.getHitPosition().toLocation(t.getWorld()).subtract(dir.clone().multiply(0.05));
            wall.setY(Nova.ground(t.getLocation().add(0, 0.5, 0)).getY());
            wall.setYaw(n.pl.getLocation().getYaw()); wall.setPitch(0);
            UUID id = n.pl.getUniqueId();
            int life = n.i("ticks", 60);
            boolean[] done = {false};
            Shadow[] me = new Shadow[1];
            Runnable strike = () -> {
                if (done[0]) return;
                done[0] = true;
                SHADOWS.remove(id, me[0]);
                n.fx("cut", wall.clone().add(0, 1.2, 0));
                if (!t.isValid() || t.isDead() || t.getWorld() != wall.getWorld()) return;
                n.fx("echo", t.getLocation(), t);
                n.later(0, () -> n.hit(t, n.dmg()));
            };
            me[0] = new Shadow(wall, strike, now() + life);
            SHADOWS.put(id, me[0]);
            n.fx("stage", wall);
            n.later(life, () -> { done[0] = true; SHADOWS.remove(id, me[0]); });
            return true;
        });

        // 435 박쥐 망토 (BatCloak): scatter into bats and dash 4 blocks along the ground in 0.36 s (hidden, through enemies,
        // not walls); each enemy passed (≤ 1.3 m) once: drain = damage + heal 40 % of it
        m.put("bat_cloak", n -> {
            Player pl = n.pl;
            if (BroomLink.riding(pl)) return refuse(n, "빗자루 위에서는 쓸 수 없어요");
            Location start = pl.getLocation(); Vector dir = n.flat();
            List<Location> pts = path(n.plugin, start, dir, n.d("dist", 4));
            if (pts.size() < 3) return refuse(n, "앞쪽이 막혀 있어요");
            int dash = n.i("dash", 7), per = (int) Math.ceil(pts.size() / (double) dash);
            double heal = n.d("heal", 0.4);
            Set<UUID> drained = new HashSet<>();
            pl.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dash + 1, 0, false, false, false));
            int[] idx = {0};
            n.loop(dash + 2, age -> {
                int from = idx[0], to = Math.min(pts.size(), from + per);
                for (int k = from; k < to; k++) {
                    Location p = pts.get(k).clone().add(0, 0.9, 0);
                    for (LivingEntity e : n.near(p, 1.8)) {
                        if (Targets.closestDistance(e, p) > 1.3 || !drained.add(e.getUniqueId())) continue;
                        double d = n.dmg();
                        if (!n.hit(e, d)) continue;
                        n.fx("drain", Nova.chest(e), e);
                        n.plugin.targets().heal(pl, pl, d * heal, "");
                    }
                }
                idx[0] = to;
                if (to <= from || !move(pl, pts.get(to - 1)) || to >= pts.size()) return false;
                return true;
            }, () -> pl.setVelocity(new Vector()));
            return true;
        });

        // 436 반달 방패 (HalfMoonGuard): 2.0 s frontal guard (front half circle): 80 % of each frontal hit is absorbed and stored
        // (absorb at the membrane); at 2.12 s a half-moon blade flies forward (16 blocks, pierces):
        // damage = base + stored (stored part capped at 2 x base)
        m.put("half_moon_guard", n -> {
            UUID id = n.pl.getUniqueId();
            int hold = n.i("ticks", 40);
            Guard g = new Guard(n, now() + hold);
            GUARDS.put(id, g);
            n.later(hold, () -> GUARDS.remove(id, g));
            n.later(42, () -> {
                GUARDS.remove(id, g);
                if (n.pl.isDead()) return;
                double dmg = n.dmg() + Math.min(g.stored, n.dmg() * 2);
                Vector f = n.flat();
                Location from = n.pl.getLocation().add(0, 1.1, 0).add(f.clone().multiply(1.1));
                n.shotFrom(from, f, n.d("speed", 1.2), n.d("range", 16), "blade").radius(1.0)
                    .hit((t, p) -> { if (n.hit(t, dmg)) n.fx("bladeHit", p); return true; })
                    .end((p, wall) -> n.fx("bladeHit", p))
                    .fire();
            });
            return true;
        });

        // 437 상현·하현 칼 (QuarterBlades): two half-moon cuts in front (3.2 m / ±75°): 1st (left -> right) at 0.24 s,
        // 2nd (right -> left) at 0.58 s; each enemy can take both
        m.put("quarter_blades", n -> {
            double r = n.d("r", 3.2), half = n.d("half", 75);
            for (int at : new int[]{4, 11}) n.later(at, () -> { for (LivingEntity e : cone(n, r, half)) n.hit(e, n.dmg()); });
            return true;
        });

        // 438 달의 인력 (MoonPull): enemies within 6 m are pulled toward the caster between 0.2 and 1.0 s (to ~1.4 m; bosses 30 %),
        // grip on each; convergence at 1.05 s: damage + slow 2 s to enemies within 3 m
        m.put("moon_pull", n -> {
            double r = n.d("r", 6), pull = n.d("pull", 0.9);
            Map<UUID, LivingEntity> held = new LinkedHashMap<>();
            n.loop(21, age -> {
                if (age < 4) return true;
                Location c = n.pl.getLocation();
                if (age <= 8) for (LivingEntity e : n.near(c.clone().add(0, 1, 0), r, 16)) {
                    if (held.containsKey(e.getUniqueId()) || !n.hit(e, 0)) continue;
                    held.put(e.getUniqueId(), e);
                    n.fx("grip", e.getLocation(), e);
                }
                if (age <= 20) for (LivingEntity e : held.values()) {
                    if (!e.isValid() || e.isDead() || e.getWorld() != c.getWorld()) continue;
                    Vector to = c.toVector().subtract(e.getLocation().toVector()).setY(0);
                    double d = to.length();
                    if (d < 1.6 || d > r + 4) continue;
                    double step = Math.min(pull, (d - 1.4) / Math.max(1, 21 - age) * 1.6) * (n.boss(e) ? 0.3 : 1);
                    Vector v = e.getVelocity();
                    e.setVelocity(new Vector(0, Math.min(v.getY(), 0.1), 0).add(to.multiply(step / d)));
                }
                if (age >= 21) {
                    for (LivingEntity e : n.near(c.clone().add(0, 1, 0), n.d("hit_r", 3))) if (n.hit(e, n.dmg())) n.status(e, "slow:40");
                    return false;
                }
                return true;
            }, null);
            return true;
        });

        // 439 달의 울음 (MoonHowl): howl at 0.92 s: enemies within 7 m take damage + fear 3 s (bosses 1 s);
        // echoes at 1.65 / 2.35 s fear (1.5 s, no damage) enemies that came inside afterwards
        m.put("moon_howl", n -> {
            double r = n.d("r", 7);
            int fear = n.i("ticks", 60);
            Set<UUID> got = new HashSet<>();
            n.later(18, () -> {
                for (LivingEntity e : n.near(n.pl.getLocation().add(0, 1, 0), r)) {
                    if (!n.hit(e, n.dmg())) continue;
                    got.add(e.getUniqueId());
                    n.status(e, "fear:" + (n.boss(e) ? 20 : fear));
                }
            });
            for (int at : new int[]{33, 47}) n.later(at, () -> {
                for (LivingEntity e : n.near(n.pl.getLocation().add(0, 1, 0), r)) {
                    if (!got.add(e.getUniqueId()) || !n.hit(e, 0)) continue;
                    n.status(e, "fear:" + (n.boss(e) ? 10 : 30));
                }
            });
            return true;
        });

        // 440 꿈의 미로 (DreamLabyrinth): dream prison at the target's feet; closed at 0.3 s = stun until 3.0 s (bosses 1 s);
        // wakes at 3.0 s = big damage
        m.put("dream_labyrinth", n -> {
            LivingEntity t = pick(n, n.d("range", 16));
            if (t == null) return false;
            if (!n.hit(t, 0)) return refuse(n, "꿈에 가둘 수 없는 대상이에요");
            n.fx("dream", t.getLocation());
            n.later(6, () -> { if (t.isValid() && !t.isDead()) n.status(t, "stun:" + (n.boss(t) ? 20 : n.i("ticks", 54))); });
            n.later(60, () -> { if (t.isValid() && !t.isDead() && t.getWorld() == n.pl.getWorld()) n.hit(t, n.dmg()); });
            return true;
        });

        // 441 박쥐 군주 (BatSovereign): bat swarm follows the caster 8 s; drain ticks at 0.8 + k s (k = 0..7) on enemies within 6 m:
        // damage each, the caster heals 30 % of the total (≤ 4 per tick)
        m.put("bat_sovereign", n -> {
            double r = n.d("r", 6), heal = n.d("heal", 0.3), cap = n.d("heal_cap", 4);
            for (int k = 0; k < 8; k++) n.later(16 + 20 * k - 1, () -> {
                if (n.pl.isDead()) return;
                double sum = 0;
                for (LivingEntity e : n.near(n.pl.getLocation().add(0, 1, 0), r)) if (n.hit(e, n.dmg())) sum += n.dmg();
                if (sum > 0) n.plugin.targets().heal(n.pl, n.pl, Math.min(cap, sum * heal), "");
            });
            return true;
        });

        // 442 꼭두각시 무대 (PuppetStage): stage r 4.5 at the aimed ground; strings at 0.55 s: enemies on it take damage and are
        // walked for 3 s in the caster's current facing (≈ 1.8 m/s, disarmed while puppeted; bosses 30 % / disarm 1 s)
        m.put("puppet_stage", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 4.5), speed = n.d("speed", 0.1);
            int ticks = n.i("ticks", 60);
            List<LivingEntity> held = new ArrayList<>();
            n.later(11, () -> {
                for (LivingEntity e : n.near(c.clone().add(0, 1, 0), r)) {
                    if (Math.abs(e.getLocation().getY() - c.getY()) > 2.5 || !n.hit(e, n.dmg())) continue;
                    held.add(e);
                    n.status(e, "disarm:" + (n.boss(e) ? 20 : ticks));
                }
                if (held.isEmpty()) return;
                n.loop(ticks, age -> {
                    Vector f = n.flat();
                    for (LivingEntity e : held) {
                        if (!e.isValid() || e.isDead() || e.getWorld() != c.getWorld()) continue;
                        if (e instanceof Mob mob && age % 5 == 0) mob.setTarget(null);
                        double s = n.boss(e) ? speed * 0.3 : speed;
                        Vector v = e.getVelocity();
                        e.setVelocity(new Vector(f.getX() * s, Math.min(v.getY(), 0.2), f.getZ() * s));
                    }
                    return age < ticks;
                }, null);
            });
            return true;
        });

        // 443 달 떨구기 (MoonDrop): a small moon over the aimed ground, lands at 1.6 s: enemies within 6 m take damage + a small lift
        m.put("moon_drop", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 6);
            n.later(32, () -> {
                for (LivingEntity e : n.near(c.clone().add(0, 1, 0), r)) if (n.hit(e, n.dmg())) n.knock(e, c, null, 0.35, 0.3);
            });
            return true;
        });

        // 444 달빛 기둥 (MoonPillar): pillars straight ahead of the cast spot at 3 / 6 / 9 m (r 1.3), erupting at 0.6 / 1.1 / 1.6 s:
        // enemies in a pillar (horizontal ≤ 1.3 m, up to 9 m high) take damage + a small lift
        m.put("moon_pillar", n -> {
            Location o = n.pl.getLocation(); Vector f = n.flat();
            double pr = n.d("r", 1.3);
            for (int k = 0; k < 3; k++) {
                Location p = Nova.ground(o.clone().add(f.clone().multiply(3 * (k + 1))).add(0, 1.5, 0));
                n.later(12 + 10 * k, () -> {
                    for (LivingEntity e : n.near(p.clone().add(0, 3, 0), 6)) {
                        Location l = e.getLocation();
                        double dy = l.getY() - p.getY();
                        if (Math.hypot(l.getX() - p.getX(), l.getZ() - p.getZ()) > pr + e.getWidth() / 2 || dy < -1.5 || dy > 9) continue;
                        if (n.hit(e, n.dmg())) n.knock(e, p, f, 0.1, 0.55);
                    }
                });
            }
            return true;
        });

        // 445 월광 회전륜 (MoonWheel): 8 blades circle the caster (r 2) from 0.35 to 6.0 s; cuts at 0.75 + 0.5k s (k = 0..10):
        // every enemy within 2.9 m of the caster takes damage
        m.put("moon_wheel", n -> {
            double r = n.d("r", 2.9);
            for (int k = 0; k <= 10; k++) n.later(15 + 10 * k - 1, () -> {
                if (n.pl.isDead()) return;
                Location c = n.pl.getLocation();
                for (LivingEntity e : n.near(c.clone().add(0, 1, 0), r + 1)) {
                    Location l = e.getLocation();
                    if (Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) > r + e.getWidth() / 2 || Math.abs(l.getY() - c.getY()) > 2.5) continue;
                    n.hit(e, n.dmg());
                }
            });
            return true;
        });

        // 446 막 내림 (CurtainFall): stage at the caster's feet (r 5.2); the curtain lands at 1.0 s: enemies inside are blinded 2 s
        // and lose their target; the curtain tears at 3.0 s: every enemy inside takes damage
        m.put("curtain_fall", n -> {
            Location c = n.pl.getLocation();
            double r = n.d("r", 5.2);
            n.later(20, () -> {
                for (LivingEntity e : inRing(n, c, r)) {
                    if (!n.hit(e, 0)) continue;
                    n.status(e, "blindness:" + n.i("ticks", 40));
                    if (e instanceof Mob mob && mob.getTarget() == n.pl) mob.setTarget(null);
                }
            });
            n.later(60, () -> { for (LivingEntity e : inRing(n, c, r + 0.3)) n.hit(e, n.dmg()); });
            return true;
        });

        // 447 그믐 장막 (NewMoonVeil): veil rises, hidden at 0.72 s (invisible, mobs drop the caster) for 2.5 s; the veil breaks at
        // the end or early when the caster attacks: strike = damage to enemies within 2.6 m of the point 1.6 m ahead at +0.05 s
        m.put("new_moon_veil", n -> {
            Player pl = n.pl;
            UUID id = pl.getUniqueId();
            if (VEILS.containsKey(id)) return refuse(n, "이미 모습을 숨기고 있어요");
            int hide = n.i("ticks", 50);
            boolean[] over = {false};
            Veil[] me = new Veil[1];
            Runnable reveal = () -> {
                if (over[0]) return;
                over[0] = true;
                VEILS.remove(id, me[0]);
                if (!pl.isOnline()) return;
                var inv = pl.getPotionEffect(PotionEffectType.INVISIBILITY);
                if (inv != null && inv.getDuration() <= hide + 4) pl.removePotionEffect(PotionEffectType.INVISIBILITY);
                if (pl.isDead()) return;
                n.fx("strike", pl.getLocation(), pl);
                n.later(0, () -> {
                    Location at = pl.getLocation().add(n.flat().multiply(1.6)).add(0, 1.0, 0);
                    for (LivingEntity e : n.near(at, n.d("r", 2.6))) n.hit(e, n.dmg());
                });
            };
            me[0] = new Veil(reveal, now() + 14 + hide);
            n.later(14, () -> {
                if (over[0] || pl.isDead()) { reveal.run(); return; }
                VEILS.put(id, me[0]);
                pl.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, hide + 2, 0, false, false, false));
                for (Entity e : pl.getNearbyEntities(24, 12, 24)) if (e instanceof Mob mob && mob.getTarget() == pl) mob.setTarget(null);
                n.later(1, () -> { if (!over[0]) n.fx("hidden", pl.getLocation(), pl); });
                n.later(hide, reveal);
            });
            return true;
        });

        // 448 그림자 도플갱어 (ShadowDoppel): the caster's shadow rises 1.2 m behind the target (from 0.45 s for 3 s);
        // every damaging attack of the caster in that time (melee, shots or spells, ≤ once per 0.6 s) is copied: mimic slash +
        // damage on the target while it stays within 8 m of the shadow
        m.put("shadow_doppel", n -> {
            LivingEntity t = pick(n, n.d("range", 16));
            if (t == null) return false;
            if (!n.hit(t, 0)) return refuse(n, "그림자를 세울 수 없는 대상이에요");
            Location spot = t.getLocation().add(n.flat().multiply(1.2));
            spot.setY(Nova.ground(t.getLocation().add(0, 0.5, 0)).getY());
            spot.setYaw(n.pl.getLocation().getYaw()); spot.setPitch(0);
            n.fx("doppel", spot);
            UUID id = n.pl.getUniqueId();
            Doppel d = new Doppel(n, t, spot, now() + 9, now() + 9 + n.i("ticks", 60));
            DOPPELS.put(id, d);
            n.later(9 + n.i("ticks", 60) + 1, () -> DOPPELS.remove(id, d));
            return true;
        });

        // 449 만월 강림 (FullMoonDescent): sky ritual over the aimed ground (r 7). 2.0~7.5 s: moonlight slows enemies inside;
        // 8.1 s the full moon lands: full damage + lift; 8.6 s giant crescent slash: 45 % damage + weakness 5 s
        m.put("full_moon_descent", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 7);
            n.loop(150, age -> {
                if (age >= 40 && age % 10 == 0) for (LivingEntity e : inRing(n, c, r)) n.status(e, "slow:15:0.3");
                return age < 150;
            }, null);
            n.later(161, () -> { for (LivingEntity e : inRing(n, c, r)) if (n.hit(e, n.dmg())) n.knock(e, c, null, 0.4, 0.45); });
            n.later(171, () -> { for (LivingEntity e : inRing(n, c, r + 0.5)) if (n.hit(e, n.dmg(n.d("second", 0.45)))) n.status(e, "magic_weakness:100"); });
            return true;
        });

        // 450 월하 가면무도회 (MoonlitMasquerade): masked ball over the aimed ground (r 7). 2.5~7.5 s: 6 dance cuts (10 % each)
        // + slow while inside; 8.6 s the 12 dancers meet in the centre: 80 % (100 % within 2.5 m);
        // 9.2 s blade rain: 60 % + stun 1 s (bosses: no stun)
        m.put("moonlit_masquerade", n -> {
            Location c = Nova.ground(n.aim().add(0, 1.5, 0));
            double r = n.d("r", 7);
            for (int k = 0; k < 6; k++) n.later(50 + 20 * k, () -> {
                for (LivingEntity e : inRing(n, c, r)) if (n.hit(e, n.dmg(0.1))) n.status(e, "slow:25:0.25");
            });
            n.later(171, () -> {
                for (LivingEntity e : inRing(n, c, r)) {
                    Location l = e.getLocation();
                    boolean core = Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) <= 2.5;
                    n.hit(e, n.dmg(core ? 1.0 : 0.8));
                }
            });
            n.later(183, () -> {
                for (LivingEntity e : inRing(n, c, r + 0.5)) if (n.hit(e, n.dmg(0.6)) && !n.boss(e)) n.status(e, "stun:20");
            });
            return true;
        });
    }

    // ------------------------------------------------------------------ helpers
    /** Refused cast: message, mana back, cooldown cleared. */
    private static boolean refuse(Nova n, String msg) {
        n.warn(msg); n.refund(n.d("mana", 0)); n.clearCooldown(); return false;
    }

    /** ENTITY spells: the Bridge's target (or the enemy nearest the aim point); null (refused) when none in range. */
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

    /** Enemies standing inside a ground circle (horizontal r, -2 .. +4 blocks around its height). */
    private static List<LivingEntity> inRing(Nova n, Location c, double r) {
        var out = new ArrayList<LivingEntity>();
        for (LivingEntity e : n.near(c.clone().add(0, 1, 0), r + 3, 32)) {
            Location l = e.getLocation();
            double dy = l.getY() - c.getY();
            if (Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) > r + e.getWidth() / 2 || dy < -2 || dy > 4) continue;
            out.add(e);
        }
        return out;
    }

    /** 428: confusion for `ticks` — mobs forget the target and wander, players are nudged sideways, bosses only slowed. */
    private static void daze(Nova n, LivingEntity t, int ticks) {
        if (n.boss(t)) { n.status(t, "slow:" + ticks + ":0.3"); return; }
        var rnd = ThreadLocalRandom.current();
        n.loop(ticks, age -> {
            if (!t.isValid() || t.isDead()) return false;
            if (age % 10 == 0) {
                double a = rnd.nextDouble(0, Math.PI * 2);
                Vector dir = new Vector(Math.cos(a), 0, Math.sin(a));
                if (t instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.getPathfinder().moveTo(t.getLocation().add(dir.multiply(4)), 1.0);
                } else {
                    Vector v = t.getVelocity();
                    t.setVelocity(dir.multiply(0.3).setY(Math.min(v.getY(), 0.1)));
                }
            } else if (t instanceof Mob mob && mob.getTarget() != null) mob.setTarget(null);
            return age < ticks;
        }, () -> { if (t instanceof Mob mob && t.isValid()) mob.getPathfinder().stopPathfinding(); });
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

    /** The player behind a damage event (melee, or the shooter of a projectile), or null. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    static void enable(MagicMythicPlugin p) {
        plugin = p;
        SHADOWS.clear(); GUARDS.clear(); VEILS.clear(); DOPPELS.clear();
        Bukkit.getPluginManager().registerEvents(new Hooks(), p);
    }

    static final class Hooks implements Listener {
        /** 436: frontal hits on a guarding caster are mostly absorbed and stored for the blade. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void guard(EntityDamageByEntityEvent e) {
            if (GUARDS.isEmpty() || !(e.getEntity() instanceof Player pl)) return;
            Guard g = GUARDS.get(pl.getUniqueId());
            if (g == null || now() > g.until || e.getDamage() <= 0) return;
            Entity src = e.getDamager();
            if (src == pl || attacker(src) == pl) return;
            Vector to = src.getLocation().toVector().subtract(pl.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 1e-4 || to.normalize().dot(g.n.flat()) <= 0) return;
            double cut = e.getDamage() * g.n.d("absorb", 0.8);
            e.setDamage(e.getDamage() - cut);
            g.stored += cut;
            int t = now();
            if (t - g.lastFx >= 4) { g.lastFx = t; g.n.fx("absorb", pl.getLocation().add(0, 1.1, 0).add(to.multiply(1.0))); }
        }

        /** 447 (an attack breaks the veil) and 448 (the shadow copies the attack). */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void attack(EntityDamageByEntityEvent e) {
            if (VEILS.isEmpty() && DOPPELS.isEmpty() || e.getDamage() <= 0) return;
            Player p = attacker(e.getDamager());
            if (p == null) return;
            UUID id = p.getUniqueId();
            Veil v = VEILS.get(id);
            if (v != null) if (plugin != null) Bukkit.getScheduler().runTask(plugin, v.reveal);
            Doppel d = DOPPELS.get(id);
            if (d == null) return;
            int t = now();
            if (t < d.from || t > d.until || t - d.last < 12) return;
            LivingEntity target = d.t;
            if (!target.isValid() || target.isDead() || target.getWorld() != d.spot.getWorld() || target.getLocation().distanceSquared(d.spot) > 64) return;
            d.last = t;
            Nova n = d.n;
            n.later(0, () -> {
                if (!target.isValid() || target.isDead()) return;
                n.fx("mimic", Nova.chest(target));
                n.hit(target, n.dmg());
            });
        }

        /** 447: mobs do not pick a hidden caster. */
        @EventHandler(ignoreCancelled = true)
        public void target(EntityTargetLivingEntityEvent e) {
            if (!VEILS.isEmpty() && e.getTarget() instanceof Player p && VEILS.containsKey(p.getUniqueId())) e.setCancelled(true);
        }

        /** 434: an arm swing while looking at the shadow strikes it. */
        @EventHandler(ignoreCancelled = true)
        public void swing(PlayerAnimationEvent e) {
            if (SHADOWS.isEmpty()) return;
            Player p = e.getPlayer();
            Shadow s = SHADOWS.get(p.getUniqueId());
            if (s == null) return;
            if (now() > s.until) { SHADOWS.remove(p.getUniqueId(), s); return; }
            if (s.wall.getWorld() != p.getWorld()) return;
            Location eye = p.getEyeLocation();
            Vector to = s.wall.clone().add(0, 1.2, 0).toVector().subtract(eye.toVector());
            double len = to.length();
            if (len > 24 || len < 1e-3) return;
            double ang = Math.acos(Math.max(-1, Math.min(1, to.multiply(1 / len).dot(eye.getDirection().normalize()))));
            if (ang > Math.max(Math.toRadians(8), Math.atan2(1.3, len))) return;
            s.strike.run();
        }

        @EventHandler public void quit(PlayerQuitEvent e) {
            UUID id = e.getPlayer().getUniqueId();
            SHADOWS.remove(id); GUARDS.remove(id); DOPPELS.remove(id);
            Veil v = VEILS.remove(id);
            if (v != null) v.reveal.run();
        }
    }
}
