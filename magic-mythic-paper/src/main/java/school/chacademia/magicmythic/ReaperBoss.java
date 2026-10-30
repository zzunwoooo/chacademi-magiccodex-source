package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;

/**
 * chaboss - 할로윈 3인 보스 "호박 리퍼" 진행자 (2026-10-31). 미몹 CA_Boss_pumpkin_reaper 의 ~onSpawn 에서
 * {@code chaboss{a=start;arena=20;g1=0.70;g2=0.40;g3=0.15} @self} 로 시작한다.
 *
 * <ul>
 *   <li>경기장 = 소환 위치 중심 반경 arena. 기본 패턴 10종을 페이즈별로 고르고, 대상 규칙(가장 먼 적 / 체력 가장 낮은 적 /
 *       둘 다 / 전원)은 여기서 정해 CHA_REAPER_* 미몹 스킬을 그 대상에게 시전한다 (예고·판정은 chatelegraph).</li>
 *   <li>체력 g1·g2·g3 비율에서 기믹: 체력은 기준선에서 멈추고(건너뛰기 방지), 소환 위치로 귀환, 기믹 동안 무적.
 *       기믹 실패 = 경기장 안 전원 즉사 (PR_Harvested).</li>
 *   <li>빨간 예고는 모두 모드 예고(Telegraphs). VFX 는 PortableVFX 팩 claude:pr_* (VFX 에는 예고 없음).</li>
 * </ul>
 * 서버 부담: 보스 하나당 Ticker 항목 1개(5틱마다 판단), 기믹 중 근접 검사도 5틱마다. 매 틱 엔티티 검색 없음.
 *
 * Also registers {@code chavfx{id=pack/system;at=caster|target;ticks=40;follow=false}} - any PortableVFX effect from a
 * MythicMobs line (static at the caster's or target's feet facing the caster, or following the entity).
 */
final class ReaperBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_reaper_prop";

    ReaperBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), p);
        if (!plugin.ticker().add(f)) return false;
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    /** /테스트 보스 호박리퍼 - 가장 가까운 호박 리퍼(64칸 안)에 기믹·패턴을 바로 걸거나 처음 상태로. */
    static String test(Player p, String action, String arg) {
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 호박 리퍼가 없어요 (/mm mobs spawn CA_Boss_pumpkin_reaper - 소환한 자리가 경기장 중심)";
        switch (action) {
            case "기믹" -> {
                if (f.gimmick != null || f.invulnerable) return "이미 기믹 진행 중";
                int k = Math.clamp(Integer.parseInt(arg), 1, 3) - 1;
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, f.gate[k] * f.maxHp()));
                f.startGimmick(k);
                return "기믹" + (k + 1) + " 시작";
            }
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                f.run(arg, ps, f.phase()); f.nextAt = f.t + 100;
                return arg + " 시전";
            }
            default -> { f.reset(); return "처음 상태로 (체력 가득, 기믹 다시)"; }
        }
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        final MagicMythicPlugin plugin; final LivingEntity boss; final Location center; final double arena;
        final double[] gate; final boolean[] gateDone = new boolean[3];
        int t, nextAt = 60, emptyFor, deflectAt; String last = "";
        boolean invulnerable, closed;
        Gimmick gimmick;
        UUID guardFx;
        final List<Pool> pools = new ArrayList<>();
        final List<Entity> props = new ArrayList<>();
        final List<Runnable> cleanups = new ArrayList<>();

        Fight(MagicMythicPlugin plugin, LivingEntity boss, Params p) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone();
            this.arena = Math.clamp(p.d("arena", 20), 8, 40);
            this.gate = new double[]{ Math.clamp(p.d("g1", 0.70), 0.05, 0.95), Math.clamp(p.d("g2", 0.40), 0.05, 0.95), Math.clamp(p.d("g3", 0.15), 0.02, 0.95) };
        }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            if (++t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) {   // 아무도 없으면 60초 뒤 처음 상태로
                if ((emptyFor += 5) >= 1200) reset();
                return true;
            }
            emptyFor = 0;
            if (t % 10 == 0) pools(ps);
            touchCandies(this, ps);
            if (gimmick != null) { if (!gimmick.step(ps)) { gimmick.cleanup(); gimmick = null; endGimmick(); } return true; }
            if (t >= nextAt) pattern(ps);
            return true;
        }

        int phase() {
            double f = boss.getHealth() / Math.max(1, maxHp());
            return f > gate[0] ? 1 : f > gate[1] ? 2 : f > gate[2] ? 3 : 4;
        }
        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = arena + 6;
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < 24) out.add(pl);
            }
            return out;
        }

        /* ---------------------------------------------------------------- basic patterns */

        record Pat(String id, int minPhase, int weight, int ticks) {}
        static final List<Pat> PATTERNS = List.of(
            new Pat("p01", 1, 4, 34), new Pat("p02", 1, 3, 34), new Pat("p03", 1, 3, 44), new Pat("p04", 1, 3, 46),
            new Pat("p05", 2, 2, 30), new Pat("p06", 2, 3, 60), new Pat("p08", 2, 2, 40), new Pat("p09", 2, 2, 150),
            new Pat("p07", 3, 3, 56), new Pat("p10", 3, 3, 50));

        void pattern(List<Player> ps) {
            int ph = phase();
            List<Pat> pool = new ArrayList<>();
            for (Pat pt : PATTERNS) if (pt.minPhase() <= ph && !pt.id().equals(last) && !(pt.id().equals("p05") && ps.size() < 2)) pool.add(pt);
            if (pool.isEmpty()) return;
            int sum = pool.stream().mapToInt(Pat::weight).sum(), roll = RNG.nextInt(sum);
            Pat pick = pool.get(0);
            for (Pat pt : pool) { roll -= pt.weight(); if (roll < 0) { pick = pt; break; } }
            last = pick.id();
            int gap = switch (ph) { case 1 -> 40; case 2 -> 30; case 3 -> 22; default -> 12; };
            nextAt = t + pick.ticks() + gap;
            run(pick.id(), ps, ph);
        }

        void run(String id, List<Player> ps, int ph) {
            Player near = nearest(ps), far = farthest(ps), weak = weakest(ps, null);
            switch (id) {
                case "p01" -> cast(ph >= 3 ? "CHA_REAPER_p01x2" : "CHA_REAPER_p01", near);
                case "p02" -> cast("CHA_REAPER_p02", near);
                case "p03" -> cast("CHA_REAPER_p03", far);
                case "p04" -> { anim("p04_pumpkin_bomb"); int k = 0; for (Player pl : ps) { int d = 5 + 8 * k++; later(d, () -> cast("CHA_REAPER_p04", pl)); } }
                case "p05" -> soulChain(far, weakest(ps, far));
                case "p06" -> groundHands(weak);
                case "p07" -> ghostDash(far);
                case "p08" -> trickOrTreat(ps);
                case "p09" -> candyRain(ps, weak);
                case "p10" -> cast("CHA_REAPER_p10", weak);
                default -> {}
            }
        }

        /** 5. 영혼 사슬: 두 사람을 잇고, 5초 안에 12블록 이상 떨어지면 끊김, 못 끊으면 두 사람 모두 큰 피해. */
        void soulChain(Player a, Player b) {
            if (a == null || b == null || a.equals(b)) return;
            anim("p05_soul_chain");
            later(8, () -> {
                if (!a.isValid() || !b.isValid()) return;
                UUID ba = Fx.follow(a, "claude:pr_soulchain/bind", 110, 0), bb = Fx.follow(b, "claude:pr_soulchain/bind", 110, 0);
                Fx.Link link = Fx.link("claude:pr_soulchain/chain", chest(a), chest(b));
                Ticker.Tick tk = new Ticker.Tick() {
                    int age;
                    @Override public boolean tick() {
                        if (closed || !a.isValid() || !b.isValid() || a.isDead() || b.isDead()) { stop(); return false; }
                        age++;
                        if (link != null && age % 2 == 0) link.move(chest(a), chest(b));
                        if (a.getLocation().distanceSquared(b.getLocation()) >= 12 * 12) {
                            Location mid = chest(a).add(chest(b)).multiply(0.5);
                            Fx.effect(plugin, "claude:pr_soulchain/snap", mid, 0, 30);
                            stop(); return false;
                        }
                        if (age >= 100) {
                            for (Player pl : List.of(a, b)) { Fx.effect(plugin, "claude:pr_soulchain/fail", pl.getLocation(), 0, 40); plugin.targets().hurt(boss, pl, 16, "dark"); }
                            stop(); return false;
                        }
                        return true;
                    }
                    @Override public void stop() { if (link != null) link.close(); Fx.stop(ba); Fx.stop(bb); }
                    @Override public String kind() { return "boss"; }
                };
                plugin.ticker().add(tk);
            });
        }

        /** 6. 땅속의 손: 가장 체력 낮은 적이 서 있는 곳에 0.35초 간격 5번 (그때그때 위치), 각 예고 0.6초 뒤 손. */
        void groundHands(Player target) {
            if (target == null) return;
            cast("CHA_REAPER_p06", target);
            for (int k = 0; k < 5; k++) later(11 + 7 * k, () -> { if (target.isValid() && !target.isDead() && near(target)) castAt("CHA_REAPER_p06_hand", target.getLocation()); });
        }

        /** 7. 유령 돌진: 사라짐 → 대상 반대편 경기장 끝에서 나타남 → 직선 예고 0.6초 → 돌진, 지나간 길에 저주 장판 10초. */
        void ghostDash(Player target) {
            if (target == null) return;
            anim("p07_ghost_dash");
            Fx.effect(plugin, "claude:pr_ghostdash/vanish", boss.getLocation(), boss.getLocation().getYaw(), 40);
            later(15, () -> {
                if (!target.isValid()) return;
                Vector dir = flat(target.getLocation().toVector().subtract(center.toVector()));
                if (dir.lengthSquared() < 1e-3) dir = new Vector(0, 0, 1);
                dir.normalize();
                Location from = ground(center.clone().subtract(dir.clone().multiply(arena * 0.75)));
                from.setDirection(dir); boss.teleport(from);
                Fx.effect(plugin, "claude:pr_ghostdash/appear", from, from.getYaw(), 40);
                cast("CHA_REAPER_p07", target);   // 직선 예고 길이 26 · 폭 2.6 · 0.6초, 판정은 chatelegraph
                Vector d = dir.clone();
                later(12, () -> dash(from, d, 26));
            });
        }
        void dash(Location from, Vector dir, double len) {
            if (!boss.isValid()) return;
            UUID fx = Fx.follow(boss, "claude:pr_ghostdash/dash", 20, 0);
            double reach = len;
            for (double s = 0; s <= len; s += 1) { if (flat2(from.clone().add(dir.clone().multiply(s)), center) > (arena - 1) * (arena - 1)) { reach = s; break; } }
            final double total = reach;
            plugin.ticker().add(new Ticker.Tick() {
                int k; double done;
                @Override public boolean tick() {
                    if (closed || !boss.isValid()) return false;
                    k++; double to = Math.min(total, total * k / 6.0);
                    for (double s = Math.ceil(done / 2) * 2; s <= to; s += 2) {
                        if (s == 0) continue;
                        Location at = ground(from.clone().add(dir.clone().multiply(s)));
                        Fx.effect(plugin, "claude:pr_ghostdash/curse", at, 0, 200);
                        pools.add(new Pool(at, 1.1, t + 200, Pool.CURSE));
                    }
                    done = to + 0.01;
                    Location now = ground(from.clone().add(dir.clone().multiply(to))); now.setDirection(dir); boss.teleport(now);
                    if (k >= 6) { Fx.stop(fx); return false; }
                    return true;
                }
                @Override public String kind() { return "boss"; }
            });
        }

        /** 8. 장난칠까 사탕 줄까: 사탕 3개를 흩뿌림 → 떨어질 자리 예고 0.5초 → 사탕 모델. 주우면 반은 회복, 반은 저주. */
        void trickOrTreat(List<Player> ps) {
            anim("p08_trick_or_treat");
            later(22, () -> {
                Fx.effect(plugin, "claude:pr_trickortreat/scatter", boss.getLocation(), boss.getLocation().getYaw(), 40);
                for (int k = 0; k < 3; k++) {
                    double a = RNG.nextDouble() * Math.PI * 2, r = 3 + RNG.nextDouble() * Math.min(9, arena - 3);
                    Location at = ground(boss.getLocation().clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                    warn(Shape.CIRCLE, at, 0, 0.9f, 0, 10, false, s -> {
                        Fx.effect(plugin, "claude:pr_trickortreat/land", at, 0, 60);
                        Entity candy = prop("CA_Reaper_candy", at, "candy");
                        if (candy != null) later(300, () -> { if (candy.isValid()) candy.remove(); });
                    });
                }
            });
        }
        void candyPicked(Player pl, Entity candy) {
            candy.remove();
            if (RNG.nextBoolean()) {   // 사탕(회복)
                Fx.follow(pl, "claude:pr_trickortreat/treat", 30, 0);
                var a = pl.getAttribute(Attribute.MAX_HEALTH); double max = a == null ? 20 : a.getValue();
                pl.setHealth(Math.min(max, pl.getHealth() + max * 0.25));
            } else {                   // 장난(저주): 썩은 호박 시야 가림 3초
                Fx.follow(pl, "claude:pr_trickortreat/trick", 60, 0);
                pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
                pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false, false));
            }
        }

        /** 9. 사탕 비: 떠오름 → 메테오 2파(6~7개, 둘은 체력 낮은 적) → 거대 막대사탕 나눠 맞기(경기장 중앙 반경 3, 2초). */
        void candyRain(List<Player> ps, Player weak) {
            anim("p09_candy_rain_start");
            UUID aura = Fx.follow(boss, "claude:pr_candyrain/aura", 160, 0);
            for (int wave = 0; wave < 2; wave++) {
                int at0 = 26 + wave * 30;
                later(at0, () -> {
                    List<Location> spots = new ArrayList<>();
                    for (int k = 0; k < 5; k++) {
                        double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * (arena - 2);
                        spots.add(ground(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
                    }
                    Player w = weakest(players(), null);
                    if (w != null) { spots.add(w.getLocation()); spots.add(w.getLocation().clone().add(RNG.nextDouble() * 2 - 1, 0, RNG.nextDouble() * 2 - 1)); }
                    int k = 0;
                    for (Location s : spots) {
                        int d = k++ * 2;
                        later(d, () -> { castAt("CHA_REAPER_p09_meteor", s); later(5, () -> Fx.fall(plugin, "claude:pr_candyrain/meteor", s.clone().add(3, 12, 2), s.clone().add(0, 0.3, 0), 11)); });
                        later(d + 16, () -> pools.add(new Pool(ground(s), 1.1, t + 60, Pool.GOO)));
                    }
                });
            }
            later(90, () -> {   // 나눠 맞기: 원 안 인원이 3명(살아 있는 인원보다 많을 수 없음) 이상이면 30을 나눠 받고, 아니면 경기장 전원 24
                Location mid = ground(center.clone());
                Fx.effect(plugin, "claude:pr_candyrain/sharemark", mid, 0, 50);
                later(26, () -> Fx.fall(plugin, "claude:pr_candyrain/lolliptrail", mid.clone().add(0, 16, 0), mid.clone().add(0, 0.5, 0), 14));
                warn(Shape.CIRCLE, mid, 0, 3f, 0, 40, true, s -> {
                    Fx.effect(plugin, "claude:pr_candyrain/lollipophit", mid, 0, 50);
                    List<Player> all = players(), in = new ArrayList<>();
                    for (Player pl : all) if (TelegraphProtocol.inside(s, pl.getLocation().getX(), pl.getLocation().getZ(), 0)) in.add(pl);
                    int need = Math.min(3, all.size());
                    if (in.size() >= need && need > 0) for (Player pl : in) plugin.targets().hurt(boss, pl, 30.0 / in.size(), "fire");
                    else for (Player pl : all) plugin.targets().hurt(boss, pl, 24, "fire");
                });
            });
            later(132, () -> { anim("p09_candy_rain_end"); Fx.stop(aura); });
        }

        void pools(List<Player> ps) {
            pools.removeIf(pl -> t >= pl.until());
            for (Pool pool : pools) for (Player pl : ps) {
                if (pl.getLocation().distanceSquared(pool.at()) > pool.r() * pool.r() || Math.abs(pl.getLocation().getY() - pool.at().getY()) > 1.5) continue;
                if (pool.kind() == Pool.CURSE) { plugin.targets().hurt(boss, pl, 2, "dark"); pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, true)); }
                else pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false, true));
            }
        }
        record Pool(Location at, double r, int until, int kind) { static final int CURSE = 0, GOO = 1; }

        /* ---------------------------------------------------------------- gimmicks */

        /** 보스가 맞을 때: 무적이면 막고, 다음 기믹 기준선을 넘으려 하면 체력을 기준선에 멈추고 기믹 시작. */
        void damaged(EntityDamageEvent e) {
            if (invulnerable) {
                e.setCancelled(true);
                if (t >= deflectAt && e instanceof EntityDamageByEntityEvent be) {
                    deflectAt = t + 4;
                    Location at = be.getDamager().getLocation().add(0, 1, 0);
                    Vector toward = at.toVector().subtract(boss.getLocation().toVector()); toward.setY(0);
                    Location hit = boss.getLocation().add(toward.lengthSquared() > 1e-3 ? toward.normalize().multiply(1.8) : new Vector()).add(0, 1.6, 0);
                    Fx.effect(plugin, "claude:pr_returnguard/deflect", hit, 0, 16);
                }
                return;
            }
            for (int k = 0; k < 3; k++) {
                if (gateDone[k]) continue;
                double line = gate[k] * maxHp();
                if (boss.getHealth() - e.getFinalDamage() <= line) {
                    e.setCancelled(true);
                    boss.setHealth(Math.max(1, line));
                    startGimmick(k);
                }
                return;   // 기믹은 순서대로
            }
        }

        void startGimmick(int k) {
            gateDone[k] = true; invulnerable = true; nextAt = Integer.MAX_VALUE;
            // 귀환: 흩어짐 → 소환 위치에서 나타남, 기믹 동안 보호막
            Fx.effect(plugin, "claude:pr_returnguard/vanish", boss.getLocation(), 0, 40);
            anim("return_vanish");
            later(14, () -> {
                Location home = center.clone(); boss.teleport(home);
                Fx.effect(plugin, "claude:pr_returnguard/appear", home, home.getYaw(), 40);
                anim("return_appear");
                guardFx = Fx.follow(boss, "claude:pr_returnguard/guard", 20 * 120, 0);
                later(20, () -> gimmick = switch (k) { case 0 -> new LyingPumpkins(this); case 1 -> new ThreeBells(this); default -> new LastLantern(this); });
            });
        }
        void endGimmick() {
            invulnerable = false; Fx.stop(guardFx); guardFx = null; nextAt = t + 40;
        }

        /** 즉사 (기믹 실패): 수확 연출 후 체력 0. */
        void harvest(Player pl) {
            Fx.effect(plugin, "claude:pr_harvested/harvest", pl.getLocation(), 0, 40);
            later(6, () -> { if (pl.isValid() && !pl.isDead()) pl.setHealth(0); });
        }

        /* ---------------------------------------------------------------- helpers */

        boolean cast(String skill, Entity target) {
            if (target == null) return false;
            try {
                return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.of(target), List.of(target.getLocation()), 1f, d -> {});
            } catch (RuntimeException | LinkageError e) { return false; }
        }
        boolean castAt(String skill, Location at) {
            try {
                return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {});
            } catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 모델 모션 (CHA_REAPER_a_<이름> = state{s=<이름>}). */
        void anim(String name) { castAt("CHA_REAPER_a_" + name, boss.getLocation()); }

        void later(int ticks, Runnable r) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!closed && boss.isValid() && !boss.isDead()) r.run(); }, Math.max(1, ticks));
        }

        /** 모드 예고 (빨강) + 같은 모양 판정 콜백. yaw = 도(마인크래프트), size = 반경/길이. */
        Show warn(Shape shape, Location at, float yaw, float size, float width, int ticks, boolean drawn, Consumer<Show> then) {
            try {
                Show s = new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw, size, width, 60, ticks, TelegraphProtocol.RED);
                return plugin.telegraphs().start(s, boss, boss.getWorld(), true, drawn, then) ? s : null;
            } catch (IllegalArgumentException e) { return null; }
        }

        Entity prop(String mob, Location at, String kind) {
            try {
                var am = MythicBukkit.inst().getMobManager().spawnMob(mob, at);
                if (am == null) return null;
                Entity e = am.getEntity().getBukkitEntity();
                e.addScoreboardTag(TAG); e.addScoreboardTag(TAG + ":" + kind); e.setPersistent(false);
                props.add(e);
                return e;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("호박 리퍼 소품 소환 실패: " + mob + " " + ex); return null; }
        }

        boolean near(Entity e) { return flat2(e.getLocation(), center) <= (arena + 6) * (arena + 6); }
        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player farthest(List<Player> ps) { return ps.stream().max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player weakest(List<Player> ps, Player not) {
            return ps.stream().filter(p -> !p.equals(not)).min(Comparator.comparingDouble(p -> p.getHealth() / Math.max(1, maxOf(p)))).orElse(null);
        }
        static double maxOf(Player p) { var a = p.getAttribute(Attribute.MAX_HEALTH); return a == null ? 20 : a.getValue(); }

        void reset() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            Arrays.fill(gateDone, false); invulnerable = false; Fx.stop(guardFx); guardFx = null;
            pools.clear(); clearProps();
            boss.setHealth(maxHp()); boss.teleport(center); nextAt = t + 60; emptyFor = 0; last = "";
        }
        void clearProps() { for (Entity e : props) if (e.isValid()) { e.leaveVehicle(); e.remove(); } props.clear(); }

        void close() {
            if (closed) return;
            closed = true;
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            Fx.stop(guardFx); clearProps(); pools.clear();
            for (Runnable r : cleanups) try { r.run(); } catch (RuntimeException ignored) {}
            FIGHTS.remove(boss.getUniqueId());
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== gimmicks */

    interface Gimmick {
        /** 5틱마다. false = 끝 (성공이든 실패든). */
        boolean step(List<Player> ps);
        void cleanup();
        default void propHit(Player by, Entity prop) {}
        default void sneak(Player pl) {}
        default void propGone(Entity prop) {}
    }

    /** 기믹1 거짓말하는 호박들: 표정 다른 호박 3개, 단서를 한 줄씩 나눠 받아 15초 안에 진짜를 부순다. */
    static final class LyingPumpkins implements Gimmick {
        static final String[] FACE = {"smile", "angry", "sad", "blank", "surprised"};
        static final String[] KO = {"웃는", "화난", "우는", "무표정한", "놀란"};
        final Fight f; final Entity[] pumpkins = new Entity[3]; final int[] face = new int[3]; final int truth;
        final Map<UUID, String> clues = new HashMap<>(); Show countdown; int age; boolean over, ok;

        LyingPumpkins(Fight f) {
            this.f = f;
            f.anim("g1_scythe_plant");
            f.later(15, () -> Fx.effect(f.plugin, "claude:pr_lyingpumpkins/plant", f.center, f.center.getYaw(), 300));
            List<Integer> fs = new ArrayList<>(List.of(0, 1, 2, 3, 4)); Collections.shuffle(fs, RNG);
            truth = RNG.nextInt(3);
            Vector fwd = flat(f.center.getDirection()); if (fwd.lengthSquared() < 1e-3) fwd = new Vector(0, 0, 1); fwd.normalize();
            Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
            for (int i = 0; i < 3; i++) {   // 0 = 한쪽 끝, 1 = 가운데, 2 = 다른 쪽 끝 (가운데/가장자리만 단서에 씀 - 좌우는 보는 쪽에 따라 달라서)
                face[i] = fs.get(i);
                Location at = ground(f.center.clone().add(fwd.clone().multiply(i == 1 ? 11 : 9)).add(right.clone().multiply((i - 1) * 6)));
                Fx.effect(f.plugin, "claude:pr_lyingpumpkins/rise", at, 0, 300);
                pumpkins[i] = f.prop("CA_Reaper_pumpkin_" + FACE[face[i]], at, "pumpkin" + i);
            }
            List<String> lines = clueLines();
            List<Player> ps = f.players(); Collections.shuffle(ps, RNG);
            for (int k = 0; k < Math.max(ps.size(), 1) && !ps.isEmpty(); k++) {
                StringBuilder sb = new StringBuilder();
                for (int j = k; j < lines.size(); j += ps.size()) { if (sb.length() > 0) sb.append("  /  "); sb.append(lines.get(j)); }
                if (k < ps.size()) { clues.put(ps.get(k).getUniqueId(), sb.toString()); ps.get(k).sendMessage("§6[호박 리퍼] §f속삭임이 들린다... §e" + sb); }
            }
            countdown = f.warn(Shape.CIRCLE, f.center, 0, (float) f.arena, 0, 300, true, s -> { if (!over) fail(); });
        }

        /**
         * 단서 3줄 (모두 참). 진짜 t, 나머지 a·b. 1줄 = a 가 아님, 2줄 = b 가 아님(또는 b 가 가운데면 "가장자리") → 둘을 합쳐야 하나로 좁혀짐.
         * 3줄 = 같은 사실을 다른 말로 하거나, 범위를 줄이지 않는 헷갈림용 참말. 위치는 가운데/가장자리만 씀 (좌우는 보는 쪽마다 달라서).
         */
        List<String> clueLines() {
            List<Integer> others = new ArrayList<>(); for (int i = 0; i < 3; i++) if (i != truth) others.add(i);
            Collections.shuffle(others, RNG);
            int a = others.get(0), b = others.get(1);
            List<String> out = new ArrayList<>();
            out.add("진짜 호박은 " + KO[face[a]] + " 얼굴이 아니다.");
            out.add(b == 1 ? "진짜 호박은 가장자리에 있다." : "진짜 호박은 " + KO[face[b]] + " 얼굴이 아니다.");
            if (a == 1) out.add("진짜 호박은 가운데 호박 바로 옆에 있다.");
            else if (b == 1) out.add("진짜 호박은 " + KO[face[b]] + " 호박 바로 옆에 있다.");
            else out.add("셋 중 둘은 거짓의 얼굴을 하고 있다.");
            Collections.shuffle(out, RNG);
            return out;
        }

        @Override public boolean step(List<Player> ps) {
            age += 5;
            if (age % 40 == 0) for (Player pl : ps) { String c = clues.get(pl.getUniqueId()); if (c != null && !over) pl.sendActionBar(net.kyori.adventure.text.Component.text("§e" + c)); }
            return !over;
        }

        @Override public void propHit(Player by, Entity prop) {
            if (over) return;
            for (int i = 0; i < 3; i++) if (prop.equals(pumpkins[i])) { if (i == truth) success(i); else fail(); return; }
        }

        void success(int i) {
            over = true; ok = true;
            if (countdown != null) f.plugin.telegraphs().clear(countdown.id());
            Fx.effect(f.plugin, "claude:pr_lyingpumpkins/truth", pumpkins[i].getLocation(), 0, 40);
            f.anim("laugh");
            for (Entity e : pumpkins) if (e != null) e.remove();
        }
        void fail() {
            over = true;
            if (countdown != null) f.plugin.telegraphs().clear(countdown.id());
            for (Entity e : pumpkins) if (e != null && e.isValid()) { Fx.effect(f.plugin, "claude:pr_lyingpumpkins/lie", e.getLocation(), 0, 40); e.remove(); }
            f.later(6, () -> { Fx.effect(f.plugin, "claude:pr_lyingpumpkins/wipe", f.center, 0, 60); for (Player pl : f.players()) f.harvest(pl); });
        }
        @Override public void cleanup() { if (countdown != null && !over) f.plugin.telegraphs().clear(countdown.id()); for (Entity e : pumpkins) if (e != null && e.isValid()) e.remove(); }
    }

    /** 기믹2 세 번의 종소리: 분신 셋(촛불 보라·주황·청록), 머리 위 표식 = 그 색은 때리면 안 됨. 세 번째 종에 셋이 1초 안에 각자 다른 분신. */
    static final class ThreeBells implements Gimmick {
        static final String[] COLOR = {"violet", "orange", "teal"}, KO = {"보라", "주황", "청록"}, CHAT = {"§d", "§6", "§b"};
        final Fight f; final Entity[] clones = new Entity[3]; final UUID[] candles = new UUID[3];
        final Map<UUID, Integer> mark = new HashMap<>(); final List<Entity> markers = new ArrayList<>();
        final Map<Integer, UUID> hitBy = new HashMap<>(); final Map<Integer, Integer> hitAt = new HashMap<>();
        int age, third = -1; boolean over;

        ThreeBells(Fight f) {
            this.f = f;
            f.anim("g2_split");
            Fx.effect(f.plugin, "claude:pr_threebells/split", f.center, 0, 40);
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(f.center.getYaw() + 90 + i * 120);
                Location at = ground(f.center.clone().add(-Math.sin(a) * 7, 0, Math.cos(a) * 7));
                at.setDirection(f.center.toVector().subtract(at.toVector()));
                clones[i] = f.prop("CA_Reaper_clone", at, "clone" + i);
                if (clones[i] != null) candles[i] = Fx.follow(clones[i], "claude:pr_threebells/candle" + COLOR[i], 20 * 20, 0);
            }
            List<Player> ps = f.players(); Collections.shuffle(ps, RNG);
            for (int k = 0; k < ps.size(); k++) {   // 표식 = 서로 다른 색 (3명 이상이면 셋을 나눠 가짐)
                Player pl = ps.get(k); int c = k % 3; mark.put(pl.getUniqueId(), c);
                TextDisplay td = pl.getWorld().spawn(pl.getLocation().add(0, 2.4, 0), TextDisplay.class, d -> {
                    d.text(net.kyori.adventure.text.Component.text(CHAT[c] + "§l◆"));
                    d.setBillboard(Display.Billboard.CENTER); d.setPersistent(false); d.addScoreboardTag(TAG);
                });
                pl.addPassenger(td); markers.add(td);
                pl.sendActionBar(net.kyori.adventure.text.Component.text(CHAT[c] + "머리 위에 " + KO[c] + " 촛불이 켜졌다."));
            }
        }

        @Override public boolean step(List<Player> ps) {
            if (over) return false;
            age += 5;
            for (int k = 1; k <= 3; k++) if (age == 40 * k) {   // 2초 간격 종소리
                Fx.effect(f.plugin, "claude:pr_threebells/toll", f.center, 0, 30);
                for (Player pl : ps) pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_USE, 2f, k == 3 ? 0.7f : 1f);
                if (k == 3) third = age;
            }
            if (third >= 0 && age > third + 30) { check(true); return !over; }
            return true;
        }

        @Override public void propHit(Player by, Entity prop) {
            if (over) return;
            for (int i = 0; i < 3; i++) if (prop.equals(clones[i])) {
                if (third < 0) { fail(); return; }                       // 종이 다 울리기 전에 때림
                Integer own = mark.get(by.getUniqueId());
                if (own != null && own == i) { fail(); return; }         // 내 표식 색
                if (hitBy.containsKey(i)) { if (!hitBy.get(i).equals(by.getUniqueId())) fail(); return; }   // 둘이 같은 분신
                hitBy.put(i, by.getUniqueId()); hitAt.put(i, age);
                Fx.effect(f.plugin, "claude:pr_threebells/hit" + COLOR[i], prop.getLocation().add(0, 1.6, 0), 0, 20);
                check(false); return;
            }
        }

        void check(boolean timeUp) {
            if (over) return;
            int players = Math.max(1, Math.min(3, mark.size()));
            boolean all = hitBy.size() == 3, distinct = new HashSet<>(hitBy.values()).size() >= Math.min(3, players);
            if (all) {
                int lo = Collections.min(hitAt.values()), hi = Collections.max(hitAt.values());
                if (distinct && hi - lo <= 20) { success(); return; }
                fail(); return;
            }
            if (timeUp) fail();
        }

        void success() {
            over = true;
            for (Entity c : clones) if (c != null && c.isValid()) { Fx.effect(f.plugin, "claude:pr_threebells/split", c.getLocation(), 0, 30); c.remove(); }
            f.anim("laugh");
        }
        void fail() {
            over = true;
            f.anim("g2_merge_sweep");
            Fx.effect(f.plugin, "claude:pr_threebells/merge", f.center, 0, 40);
            for (Entity c : clones) if (c != null && c.isValid()) c.remove();
            f.warn(Shape.CIRCLE, f.center, 0, (float) f.arena, 0, 18, true, s -> {
                Fx.effect(f.plugin, "claude:pr_threebells/sweep", f.center, 0, 60);
                for (Player pl : f.players()) f.harvest(pl);
            });
        }
        @Override public void cleanup() {
            for (UUID h : candles) Fx.stop(h);
            for (Entity c : clones) if (c != null && c.isValid()) c.remove();
            for (Entity m : markers) if (m.isValid()) { m.leaveVehicle(); m.remove(); }
        }
    }

    /** 기믹3 마지막 등불: 30초 동안 어둠. 등불을 든 사람 주변만 안전, 든 사람은 체력이 깎이고, 허수아비 사탕을 넣으면 빛이 커진다. */
    static final class LastLantern implements Gimmick {
        static final int LENGTH = 600; static final double START_R = 4, MIN_R = 1.5, MAX_R = 9;
        final Fight f; Entity lantern; Player holder; UUID lightFx, carryFx; double r = START_R, shownR;
        final Map<UUID, Integer> noPick = new HashMap<>(); final Map<UUID, Entity> carrying = new HashMap<>();
        final List<Entity> scarecrows = new ArrayList<>(), candies = new ArrayList<>();
        int age; boolean over;

        LastLantern(Fight f) {
            this.f = f;
            f.anim("g3_lantern_drop");
            f.later(27, () -> {
                Fx.effect(f.plugin, "claude:pr_lastlantern/darkfall", f.center, 0, 50);
                for (Player pl : f.players()) pl.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, LENGTH + 40, 0, false, false, false));
                Vector fwd = flat(f.center.getDirection()); if (fwd.lengthSquared() < 1e-3) fwd = new Vector(0, 0, 1);
                Location at = ground(f.center.clone().add(fwd.normalize().multiply(4)));
                Fx.effect(f.plugin, "claude:pr_lastlantern/lanterndrop", at, 0, 40);
                lantern = f.prop("CA_Reaper_lantern", at, "lantern");
                light();
            });
        }

        Entity anchor() { return holder != null ? holder : lantern; }
        Location lightAt() { Entity a = anchor(); return a == null ? f.center : a.getLocation(); }

        void light() {
            Entity a = anchor(); if (a == null) return;
            Fx.stop(lightFx); lightFx = Fx.follow(a, "claude:pr_lanternlight/light", LENGTH + 100, r); shownR = r;
        }

        @Override public boolean step(List<Player> ps) {
            if (over) return false;
            if (lantern == null) { age += 5; return true; }   // 아직 떨어지는 중
            age += 5;
            r = Math.max(MIN_R, r - 0.15 * 5 / 20);   // 초당 0.15 줄어듦
            noPick.replaceAll((k, v) -> v - 5); noPick.values().removeIf(v -> v <= 0);
            // 등불 줍기
            if (holder == null && lantern.isValid()) for (Player pl : ps)
                if (!noPick.containsKey(pl.getUniqueId()) && pl.getLocation().distanceSquared(lantern.getLocation()) < 1.6 * 1.6) { pick(pl); break; }
            // 든 사람: 초당 최대 체력 4%
            if (holder != null && age % 20 == 0) {
                if (!holder.isValid() || holder.isDead()) drop();
                else f.plugin.targets().hurt(f.boss, holder, Fight.maxOf(holder) * 0.04, "dark");
            }
            // 허수아비 최대 4마리 (어둠 속, 빛 밖)
            scarecrows.removeIf(e -> !e.isValid());
            if (age % 100 == 0 && scarecrows.size() < 4) {
                double a = RNG.nextDouble() * Math.PI * 2, d = 8 + RNG.nextDouble() * (f.arena - 9);
                Entity s = f.prop("CA_Reaper_scarecrow", ground(f.center.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d)), "scarecrow");
                if (s != null) scarecrows.add(s);
            }
            // 사탕 줍기 / 넣기
            candies.removeIf(e -> !e.isValid());
            for (Entity c : List.copyOf(candies)) {
                if (c.getVehicle() != null) continue;
                for (Player pl : ps) if (!carrying.containsKey(pl.getUniqueId()) && pl.getLocation().distanceSquared(c.getLocation()) < 1.4 * 1.4) {
                    pl.addPassenger(c); carrying.put(pl.getUniqueId(), c); break;
                }
            }
            Location la = lightAt();
            for (var en : List.copyOf(carrying.entrySet())) {
                Player pl = f.boss.getServer().getPlayer(en.getKey()); Entity c = en.getValue();
                if (pl == null || !c.isValid()) { carrying.remove(en.getKey()); continue; }
                if (pl.getLocation().distanceSquared(la) <= r * r) {
                    Fx.effect(f.plugin, "claude:pr_lastlantern/deposit", pl.getLocation(), 0, 30);
                    c.leaveVehicle(); c.remove(); candies.remove(c); carrying.remove(en.getKey());
                    r = Math.min(MAX_R, r + 1.2);
                }
            }
            if (Math.abs(r - shownR) >= 0.5) light();
            // 끝: 0.9초 예고 후 휩쓸기, 빛 밖은 즉사
            if (age == LENGTH) {
                f.anim("g3_arena_sweep");
                f.warn(Shape.CIRCLE, f.center, 0, (float) f.arena, 0, 18, true, s -> {
                    Fx.effect(f.plugin, "claude:pr_lastlantern/sweep", f.center, 0, 40);
                    Location safe = lightAt(); double rr = r * r;
                    for (Player pl : f.players()) if (flat2(pl.getLocation(), safe) > rr) f.harvest(pl);
                    over = true;
                });
            }
            if (age > LENGTH + 40) over = true;   // 판정 콜백이 못 돌았을 때 안전장치
            return !over;
        }

        void pick(Player pl) {
            holder = pl; pl.addPassenger(lantern);
            carryFx = Fx.follow(pl, "claude:pr_lastlantern/carry", LENGTH, 0);
            light();
        }
        void drop() {
            if (holder == null) return;
            Player was = holder; holder = null; Fx.stop(carryFx); carryFx = null;
            if (lantern != null && lantern.isValid()) { lantern.leaveVehicle(); lantern.teleport(was.isValid() ? ground(was.getLocation()) : lantern.getLocation()); }
            noPick.put(was.getUniqueId(), 20);
            light();
        }
        @Override public void sneak(Player pl) { if (pl.equals(holder)) drop(); }
        @Override public void propGone(Entity prop) {
            if (scarecrows.remove(prop)) {   // 허수아비가 쓰러진 자리에 사탕
                Entity c = f.prop("CA_Reaper_candy", prop.getLocation(), "candy3");
                if (c != null) candies.add(c);
            }
        }
        @Override public void cleanup() {
            Fx.stop(lightFx); Fx.stop(carryFx);
            if (lantern != null && lantern.isValid()) { Fx.effect(f.plugin, "claude:pr_lastlantern/lightout", lantern.getLocation(), 0, 30); lantern.leaveVehicle(); lantern.remove(); }
            for (Entity e : scarecrows) if (e.isValid()) e.remove();
            for (Entity e : candies) if (e.isValid()) { e.leaveVehicle(); e.remove(); }
            for (Player pl : f.boss.getWorld().getPlayers()) pl.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    /* ======================================================================== events */

    static final class Hooks implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            e.setCancelled(true);   // 소품은 안 죽음 (허수아비 제외: 아래)
            if (e.getEntity().getScoreboardTags().contains(TAG + ":scarecrow")) { e.setCancelled(false); return; }
            if (!(e instanceof EntityDamageByEntityEvent be)) return;
            Player by = be.getDamager() instanceof Player pl ? pl : be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
            if (by == null) return;
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null && fight.props.contains(e.getEntity())) fight.gimmick.propHit(by, e.getEntity());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f != null) { f.close(); return; }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null) fight.gimmick.propGone(e.getEntity());
        }

        @EventHandler public void sneak(PlayerToggleSneakEvent e) {
            if (!e.isSneaking() || FIGHTS.isEmpty()) return;
            for (Fight f : FIGHTS.values()) if (f.gimmick != null) f.gimmick.sneak(e.getPlayer());
        }

        @EventHandler public void quit(PlayerQuitEvent e) {
            for (Entity p : List.copyOf(e.getPlayer().getPassengers())) if (p.getScoreboardTags().contains(TAG)) { e.getPlayer().removePassenger(p); if (p instanceof TextDisplay) p.remove(); }
            for (Fight f : FIGHTS.values()) if (f.gimmick != null) f.gimmick.sneak(e.getPlayer());
        }
    }

    /** 사탕 줍기: 장난칠까 사탕 줄까 의 사탕은 가까이 가면 바로 먹힘 (5틱마다 검사는 Fight 에서). */
    static void touchCandies(Fight f, List<Player> ps) {
        for (Entity c : List.copyOf(f.props)) {
            if (!c.isValid() || !c.getScoreboardTags().contains(TAG + ":candy")) continue;
            for (Player pl : ps) if (pl.getLocation().distanceSquared(c.getLocation()) < 1.3 * 1.3) { f.props.remove(c); f.candyPicked(pl, c); break; }
        }
    }

    /* ======================================================================== chavfx */

    static final class VfxMech extends Mech {
        VfxMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }
        @Override boolean run(Ctx c) {
            String id = TelegraphMech.vfxId(p.s("id", ""));
            if (id.isEmpty()) return false;
            boolean onTarget = p.s("at", "caster").toLowerCase(Locale.ROOT).startsWith("t");
            Entity who = onTarget ? c.target() : c.caster();
            int ticks = Math.clamp(p.i("ticks", 40), 1, 20 * 60);
            if (p.b("follow", false) && who != null) return Fx.follow(who, id, ticks, 0) != null;
            Location at = who != null ? who.getLocation() : c.at();
            Fx.effect(plugin, id, at, c.caster().getLocation().getYaw(), ticks);
            return true;
        }
    }

    /* ======================================================================== PortableVFX (reflection, no compile dependency) */

    static final class Fx {
        private static Object service; private static Class<?> api, request, basis;
        private static boolean resolve() {
            if (service != null) return true;
            try {
                var plugin = Bukkit.getPluginManager().getPlugin("PortableVFX"); if (plugin == null || !plugin.isEnabled()) return false;
                var loader = plugin.getClass().getClassLoader();
                api = Class.forName("dev.portablevfx.paper.api.PortableVfxService", true, loader);
                request = Class.forName("dev.portablevfx.paper.api.EffectRequest", true, loader);
                basis = Class.forName("dev.portablevfx.protocol.EffectBasis", true, loader);
                var reg = Bukkit.getServicesManager().getRegistration(api); if (reg == null) return false;
                service = reg.getProvider(); return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return false; }
        }
        private static Object req(String id, Location at, int ticks) throws ReflectiveOperationException {
            return request.getConstructor(String.class, String.class, double.class, double.class, double.class, float.class, int.class, float.class, float.class, float.class, int.class, float.class, double.class)
                .newInstance(id.toLowerCase(Locale.ROOT), at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), 1f, ticks, at.getYaw(), 0f, 0f, 0xFFFFFF, 1f, 96d);
        }

        static void effect(MagicMythicPlugin plugin, String id, Location at, float yaw, int ticks) {
            if (at == null || at.getWorld() == null) return;
            plugin.reactions().vfx().effect(id, at, 1f, yaw, ticks, 0xFFFFFF);
        }

        /** Attached system following an entity. scaleInput > 0 = runtime areaRadius (LanternLight). */
        static UUID follow(Entity e, String id, int ticks, double scaleInput) {
            if (e == null || !resolve()) return null;
            try {
                Object r = req(id, e.getLocation(), ticks), res;
                if (scaleInput > 0) res = api.getMethod("startFollowCast", request, UUID.class, long.class, double.class, double.class).invoke(service, r, e.getUniqueId(), RNG.nextLong(), 0d, scaleInput);
                else res = api.getMethod("follow", request, UUID.class).invoke(service, r, e.getUniqueId());
                return (UUID) res.getClass().getMethod("handle").invoke(res);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return null; }
        }

        static void stop(UUID handle) {
            if (handle == null || service == null) return;
            try { api.getMethod("stop", UUID.class).invoke(service, handle); } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
            try { api.getMethod("finishCast", UUID.class, boolean.class).invoke(service, handle, true); } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }

        /** Projectile-role system moved straight from `from` to `to` in `ticks` (meteors, the lollipop). */
        static void fall(MagicMythicPlugin mp, String id, Location from, Location to, int ticks) {
            Vector dir = to.toVector().subtract(from.toVector());
            GameplayVisual v = GameplayVisual.cast(null, id, from, dir.clone().normalize());
            if (v == null) return;
            mp.ticker().add(new Ticker.Tick() {
                int k;
                @Override public boolean tick() {
                    k++; Location at = from.clone().add(dir.clone().multiply(Math.min(1, k / (double) ticks)));
                    v.move(at, dir); if (k >= ticks) { v.close(); return false; } return true;
                }
                @Override public void stop() { v.close(); }
                @Override public String kind() { return "boss"; }
            });
        }

        /** Link-role system between two points, re-posed by the caller. */
        static Link link(String id, Location a, Location b) {
            if (!resolve()) return null;
            try {
                Vector d = b.toVector().subtract(a.toVector()); double len = Math.max(0.1, d.length());
                Method pb = basis.getMethod("projectile", double.class, double.class, double.class);
                Object res = api.getMethod("startCast", request, basis, long.class, double.class, double.class, double.class)
                    .invoke(service, req(id, a, 20 * 10), pb.invoke(null, d.getX(), d.getY(), d.getZ()), RNG.nextLong(), 0d, 0d, len);
                return new Link((UUID) res.getClass().getMethod("handle").invoke(res), pb,
                    api.getMethod("updateCast", UUID.class, double.class, double.class, double.class, basis, double.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return null; }
        }
        record Link(UUID handle, Method basis, Method update) {
            void move(Location a, Location b) {
                Vector d = b.toVector().subtract(a.toVector());
                try { update.invoke(service, handle, a.getX(), a.getY(), a.getZ(), basis.invoke(null, d.getX(), d.getY(), d.getZ()), Math.max(0.1, d.length())); }
                catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
            void close() { Fx.stop(handle); }
        }

        static void reset() { service = null; }
    }

    /* ======================================================================== small utils */

    static final Random RNG = new Random();
    static Vector flat(Vector v) { return new Vector(v.getX(), 0, v.getZ()); }
    static double flat2(Location a, Location b) { double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(); return dx * dx + dz * dz; }
    static Location chest(LivingEntity e) { return e.getLocation().add(0, e.getHeight() * 0.6, 0); }
    static Location ground(Location at) { Location g = at.clone(); g.setY(Telegraphs.ground(at.clone().add(0, 1, 0))); return g; }
}
