package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chasomnia - 1인 레이드 보스 "몽마 솜니아" 진행자 (2026-11-09). 미몹 CA_Boss_somnia 의 ~onSpawn 에서
 * {@code chasomnia{a=start;arena=16;dx=0;dy=0;dz=120} @self}. 기획서 "차카데미 악마 1인 레이드 5종 기획서" 4번.
 *
 * <ul>
 *   <li>현실 = 소환 위치 중심 반경 arena. 꿈 = 현실 중심 + (dx, dy, dz) 에 같은 구조로 지은 맵.
 *       꿈 쪽에는 실체 몸 CA_Boss_somnia_dream 이 서 있고, 둘은 체력을 같이 쓴다 (꿈 몸이 맞으면 현실 몸이 깎임).</li>
 *   <li>현실 몸은 반투명 — 받는 피해 20%. 꿈 몸은 100%. 꿈 안에서는 졸음이 15초에 걸쳐 차고(플레이어 보스바), 가득 차면 영원한 잠(즉사).</li>
 *   <li>현실에 잠의 웅덩이 2개 (20초마다 자리 옮김). 들어가면 꿈으로. 꿈 가장자리 종탑 4곳 중 빛나는 종을 치면(때리거나 우클릭) 현실로 돌아오고 졸음 0.</li>
 *   <li>현실 패턴: 자장가(맞으면 다음 꿈 졸음 30%부터) · 날개 바람 · 깃털 비 · 베개 짓누르기. 꿈 패턴: 떨어지는 계단 · 눈꺼풀 · 악몽 분신 · 꿈 붕괴(맞으면 현실로 튕김, 졸음 유지).</li>
 *   <li>50% 깨지 않는 꿈: 무적, 모두 꿈으로 끌려가고 종 사라짐, 졸음 멈춤. 25초 동안 바닥이 바깥부터 무너짐(반경 16 → 6, 밖은 0.5초마다 5%).
 *       끝에 "일어나" → 가운데 종 하나, 치면 모두 현실로 + 보스 그로기 10초(현실에서도 피해 100%).</li>
 *   <li>30% 경계 붕괴: 꿈 맵 안 씀(꿈에 있던 사람은 현실로). 현실 경기장을 가르는 선이 0.12 rad/s 로 돌고, 한쪽이 꿈 쪽.
 *       꿈 쪽에 서 있으면 졸음 +1/15 초, 현실 쪽은 -1/30 초. 보스가 꿈 쪽에 있을 때만 피해 100% (모델도 실체로). 웅덩이·종 없음.</li>
 * </ul>
 * 빨간 예고 = 모드 예고. VFX = PortableVFX 팩 claude:somnia_boss/* (예고 없음). 대사·안내 = 채팅 + 타이틀.
 */
final class SomniaBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_somnia_prop", BELL_TAG = "chacademi_somnia_bell", DREAM_TAG = "chacademi_somnia_dream";
    static final String NAME = "§8[§d솜니아§8] §f";
    static final String VFX = "claude:somnia_boss/";
    // [기능:보스VFX팩나눔] 11/09 클라 상한(팩당 시스템 16)으로 Somnia_Boss 를 둘로 나눔
    static final java.util.Set<String> PACK2 = java.util.Set.of("groggy", "eternal", "dreamfall", "wake", "boundary", "enrage");
    static String fx(String sys) { return (PACK2.contains(sys) ? "claude:somnia_boss2/" : VFX) + sys; }
    static final List<String> PATTERN_IDS = List.of("lullaby", "gust", "feather", "pillow", "stairs", "eyelid", "clone", "collapse");
    static final double BOUNDARY_DEG_PER_S = Math.toDegrees(0.12);

    SomniaBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Vector off = new Vector(p.d("dx", 0), p.d("dy", 0), p.d("dz", 120));
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 16), 10, 32), off);
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    static Fight near(Player p) {
        Fight f = null; double best = 200 * 200;
        for (Fight x : FIGHTS.values()) if (x.center.getWorld().equals(p.getWorld())) {
            double d = Math.min(x.center.distanceSquared(p.getLocation()), x.dream.distanceSquared(p.getLocation()));
            if (d < best) { best = d; f = x; }
        }
        return f;
    }

    /** /테스트 보스 솜니아 &lt;무엇&gt; [값]. */
    static String test(Player p, String what, String arg) {
        Fight f = near(p);
        if (f == null) return "근처에 솜니아가 없어요 (/mm mobs spawn CA_Boss_somnia - 소환한 자리가 현실 경기장 중심, 꿈 맵은 그 + (0,0,120))";
        switch (what) {
            case "패턴" -> { if (f.phase != Fight.P_FIGHT) return "지금은 기믹 중이에요"; f.run(arg); return arg + " 시전"; }
            case "꿈" -> { f.enterDream(p, true); return "꿈으로 들어감"; }
            case "현실" -> { f.exitDream(p, true); return "현실로 돌아옴 (졸음 0)"; }
            case "졸음" -> { f.sleeper(p).gauge = Math.clamp(Integer.parseInt(arg) / 100.0, 0, 1); return "졸음 " + arg + "%"; }
            case "깨지않는꿈" -> { f.boss.setHealth(Math.max(1, f.maxHp() * 0.5)); f.startNightmare(); return "50% 깨지 않는 꿈 시작"; }
            case "광폭화" -> { f.nightmareDone = true; f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * 0.3))); f.enrage(); return "30% 경계 붕괴"; }
            default -> { f.reset(); return "처음 상태로"; }
        }
    }

    /* ======================================================================== fight */

    static final class Sleeper {
        double gauge, pending; boolean inDream; int cooldownUntil; BossBar bar;
    }

    static final class Fight extends DemonFight {
        static final int P_FIGHT = 1, P_NIGHTMARE = 2, P_BELL = 3, P_GROGGY = 4;
        final Location dream;
        LivingEntity dreamBody;
        final LivingEntity[] bells = new LivingEntity[4];
        int glowBell = -1; UUID bellFx; LivingEntity wakeBell; UUID wakeBellFx;
        final List<Location> pools = new ArrayList<>(); final List<UUID> poolFx = new ArrayList<>();
        final Map<UUID, Sleeper> sleepers = new HashMap<>();
        int phase = P_FIGHT, realNextAt = 80, dreamNextAt = 120, poolMoveAt, emptyFor, phaseAt, groggyUntil;
        boolean enraged, nightmareDone, invulnerable, routing, solid;
        String lastReal = "", lastDream = "";
        UUID boundaryFx, groggyFx, cloneFx; float boundaryYaw0; int boundaryAt;

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena, Vector off) {
            super(plugin, boss, arena, NAME, "CHA_SN", TAG);
            this.dream = center.clone().add(off);
            anim("spawn");
            later(10, () -> {
                dreamBody = prop("CA_Boss_somnia_dream", dream.clone());
                if (dreamBody != null) {
                    dreamBody.addScoreboardTag(DREAM_TAG); dreamBody.removeScoreboardTag(TAG);
                    AttributeInstance mh = dreamBody.getAttribute(Attribute.MAX_HEALTH);
                    if (mh != null) mh.setBaseValue(maxHp());
                    dreamBody.setHealth(Math.min(maxHp(), boss.getHealth()));
                    castAt(dreamBody, "CHA_SN_a_spawn");
                }
                spawnBells(); spawnPools();
            });
            later(30, () -> { say("눈을 감아. 나는 거기서 기다릴게."); });
        }

        /* ---------------------------------------------------------------- 대상 */

        @Override List<Player> players() { List<Player> out = new ArrayList<>(real()); out.addAll(dreamers()); return out; }
        List<Player> real() { return playersNear(center); }
        List<Player> dreamers() { return playersNear(dream); }
        Sleeper sleeper(Player pl) { return sleepers.computeIfAbsent(pl.getUniqueId(), k -> new Sleeper()); }
        boolean inDream(Player pl) { return ReaperBoss.flat2(pl.getLocation(), dream) <= (arena + 6) * (arena + 6) && Math.abs(pl.getLocation().getY() - dream.getY()) < 24; }

        /* ---------------------------------------------------------------- tick */

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (t % 5 != 0) return true;
            List<Player> all = players();
            if (all.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            if (t % 10 == 0) half(all);
            if (t % 20 == 0) second(all);
            if (phase == P_GROGGY && t >= groggyUntil) endGroggy();
            if (phase == P_NIGHTMARE && t - phaseAt >= 500) wakeCall();
            if (phase == P_FIGHT || phase == P_NIGHTMARE) {
                List<Player> r = real(), d = dreamers();
                if (phase == P_FIGHT && !r.isEmpty() && t >= realNextAt) realPattern(r);
                if (!enraged && !d.isEmpty() && t >= dreamNextAt) dreamPattern(d);
            }
            return true;
        }

        /** 0.5초마다: 웅덩이 들어가기, 깨지 않는 꿈 바깥 무너짐. */
        void half(List<Player> all) {
            if (phase == P_FIGHT && !enraged) for (Player pl : real()) {
                Sleeper s = sleeper(pl);
                if (t < s.cooldownUntil) continue;
                for (Location pool : pools) if (ReaperBoss.flat2(pl.getLocation(), pool) <= 1.6 * 1.6 && Math.abs(pl.getLocation().getY() - pool.getY()) < 2) { enterDream(pl, false); break; }
            }
            if (phase == P_NIGHTMARE) {
                double r = Math.max(6, arena - (arena - 6) * (t - phaseAt) / 500.0);
                for (Player pl : dreamers()) if (ReaperBoss.flat2(pl.getLocation(), dream) > r * r) {
                    hurtPct(pl, 0.05, "dark");
                    Vector in = dream.toVector().subtract(pl.getLocation().toVector()).setY(0);
                    if (in.lengthSquared() > 1e-3) pl.setVelocity(in.normalize().multiply(0.8).setY(0.25));
                }
            }
        }

        /** 1초마다: 졸음, 보스바, 꿈 몸 체력 맞춤, 웅덩이 옮김, 경계선. */
        void second(List<Player> all) {
            for (Player pl : all) {
                Sleeper s = sleeper(pl);
                boolean dreamSide;
                if (enraged) dreamSide = onDreamSide(pl.getLocation());
                else dreamSide = inDream(pl) && phase != P_NIGHTMARE && phase != P_BELL;
                if (dreamSide) s.gauge += 1 / 15.0;
                else if (enraged) s.gauge = Math.max(0, s.gauge - 1 / 30.0);
                if (s.gauge >= 1) { s.gauge = 0; eternal(pl); }
                showBar(pl, s);
            }
            if (dreamBody != null && dreamBody.isValid()) dreamBody.setHealth(Math.max(1, Math.min(boss.getHealth(), maxHp())));
            if (phase == P_FIGHT && !enraged && t >= poolMoveAt) spawnPools();
            if (enraged) {
                if (t - boundaryAt >= 1180) startBoundary(boundaryYaw());
                boolean side = onDreamSide(boss.getLocation());
                if (side != solid) { solid = side; castAt(boss, side ? "CHA_SN_solid" : "CHA_SN_veil"); }
                dreamSideParticles();
            }
        }

        void showBar(Player pl, Sleeper s) {
            boolean show = s.gauge > 0.001 || inDream(pl) || enraged;
            if (show && s.bar == null) { s.bar = Bukkit.createBossBar("§d졸음", BarColor.PURPLE, BarStyle.SEGMENTED_10); s.bar.addPlayer(pl); }
            if (s.bar != null) { s.bar.setProgress(Math.clamp(s.gauge, 0, 1)); s.bar.setTitle("§d졸음 §f" + Math.round(s.gauge * 100) + "%"); s.bar.setVisible(show); }
        }

        /* ---------------------------------------------------------------- 꿈 들어가기 · 나오기 */

        void enterDream(Player pl, boolean forced) {
            Sleeper s = sleeper(pl);
            if (inDream(pl)) return;
            s.cooldownUntil = t + 60;
            Fx.follow(pl, fx("enter"), 30, 0);
            tell(pl, "§d§l꿈속으로", forced ? "§7빠져나갈 수 없다" : "§7어딘가에서 종소리가 들린다");
            later(14, () -> {
                if (!pl.isValid() || pl.isDead()) return;
                Location to = randomIn(dream, 5); to.setYaw(yawTo(to, dream)); pl.teleport(to.add(0, 0.1, 0));
                s.gauge = Math.max(s.gauge, s.pending); s.pending = 0; s.inDream = true;
            });
        }
        void exitDream(Player pl, boolean resetGauge) {
            Sleeper s = sleeper(pl);
            s.cooldownUntil = t + 60; s.inDream = false;
            if (resetGauge) s.gauge = 0;
            double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, d = 6 + ReaperBoss.RNG.nextDouble() * (arena - 9);
            Location to = ground(center.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d)); to.setYaw(yawTo(to, center));
            pl.teleport(to.add(0, 0.1, 0));
            later(1, () -> Fx.follow(pl, fx("exit"), 20, 0));
        }
        void eternal(Player pl) {
            Fx.follow(pl, fx("eternal"), 40, 0);
            tell(pl, "§5§l영원한 잠", "§7졸음을 이기지 못했다");
            say("쉿... 이제 푹 자렴.");
            later(30, () -> { if (pl.isValid() && !pl.isDead()) hurtPct(pl, 10, "dark"); });
        }

        /* ---------------------------------------------------------------- 종 · 웅덩이 */

        Location bellSpot(int i) { return ground(dream.clone().add(dir(center.getYaw() + 90 * i).multiply(arena - 2))); }
        void spawnBells() {
            for (int i = 0; i < 4; i++) {
                if (bells[i] != null && bells[i].isValid()) continue;
                bells[i] = prop("CA_Somnia_bell", bellSpot(i));
                if (bells[i] != null) bells[i].addScoreboardTag(BELL_TAG);
            }
            pickGlowBell();
        }
        void pickGlowBell() {
            Fx.stop(bellFx); bellFx = null;
            if (glowBell >= 0 && bells[glowBell] != null) castAt(bells[glowBell], "CHA_SN_p_bell_ring");
            int next; do { next = ReaperBoss.RNG.nextInt(4); } while (next == glowBell);
            glowBell = next;
            LivingEntity b = bells[glowBell];
            if (b == null) return;
            castAt(b, "CHA_SN_p_bell_glow");
            bellFx = Fx.held(fx("bell_glow"), ground(b.getLocation()), 0, 1200);
        }
        void bellsOff() { Fx.stop(bellFx); bellFx = null; glowBell = -1; }

        void hitBell(Player pl, Entity bell) {
            if (phase == P_BELL && bell.equals(wakeBell)) { ringWake(); return; }
            if (phase != P_FIGHT || enraged || glowBell < 0 || !bell.equals(bells[glowBell])) { tell(pl, "", "§7이 종은 울리지 않는다"); return; }
            castAt((LivingEntity) bell, "CHA_SN_p_bell_ring");
            Fx.effect(plugin, fx("bell_ring"), ground(bell.getLocation()), 0, 40);
            exitDream(pl, true);
            later(10, this::pickGlowBell);
        }

        void spawnPools() {
            for (UUID h : poolFx) Fx.stop(h);
            for (Location l : pools) Fx.effect(plugin, fx("pool_close"), l, 0, 30);
            pools.clear(); poolFx.clear();
            for (int k = 0; k < 2; k++) {
                Location l = randomIn(center, arena - 4);
                pools.add(l);
                Fx.effect(plugin, fx("pool_open"), l, 0, 30);
                poolFx.add(Fx.held(fx("pool"), l, 0, 420));
            }
            poolMoveAt = t + 400;
        }
        void poolsOff() { for (UUID h : poolFx) Fx.stop(h); for (Location l : pools) Fx.effect(plugin, fx("pool_close"), l, 0, 30); pools.clear(); poolFx.clear(); }

        /* ---------------------------------------------------------------- 패턴 */

        void realPattern(List<Player> r) {
            List<String> pool = new ArrayList<>(List.of("lullaby", "gust", "feather", "pillow")); pool.remove(lastReal);
            String id = pool.get(ReaperBoss.RNG.nextInt(pool.size()));
            lastReal = id; realNextAt = t + realRun(id, r) + (enraged ? 10 : 18);
        }
        void dreamPattern(List<Player> d) {
            List<String> pool = new ArrayList<>(List.of("stairs", "eyelid", "clone", "collapse")); pool.remove(lastDream);
            if (phase == P_NIGHTMARE) pool.remove("collapse");
            String id = pool.get(ReaperBoss.RNG.nextInt(pool.size()));
            lastDream = id; dreamNextAt = t + dreamRun(id, d) + (phase == P_NIGHTMARE ? 8 : 16);
        }
        void run(String id) {
            if (List.of("lullaby", "gust", "feather", "pillow").contains(id)) { List<Player> r = real(); if (!r.isEmpty()) realRun(id, r); }
            else { List<Player> d = dreamers(); if (!d.isEmpty()) dreamRun(id, d); }
        }
        int sp(int ticks) { return enraged ? Math.max(3, Math.round(ticks * 0.8f)) : ticks; }

        int realRun(String id, List<Player> r) {
            Player near = nearest(r, boss.getLocation());
            return switch (id) {
                case "lullaby" -> lullaby();
                case "gust" -> gust(near);
                case "feather" -> feathers();
                case "pillow" -> pillow(near);
                default -> 20;
            };
        }
        int dreamRun(String id, List<Player> d) {
            Player near = nearest(d, dream);
            return switch (id) {
                case "stairs" -> stairs();
                case "eyelid" -> eyelid();
                case "clone" -> clone(near);
                case "collapse" -> collapse();
                default -> 20;
            };
        }

        /** 자장가: 고리 3겹이 0 / 0.5 / 1.0초에 출발해 1.6초 만에 반경 16. 맞으면 16 + 다음 꿈 졸음 30%부터 (뛰어넘기 가능). */
        int lullaby() {
            castAt(boss, "CHA_SN_clear"); anim("lullaby");
            Location c = ground(boss.getLocation());
            Fx.effect(plugin, fx("lullaby"), c, 0, 80);
            Map<UUID, Integer> hitMask = new HashMap<>();
            plugin.ticker().add(new Ticker.Tick() {
                int age;
                @Override public boolean tick() {
                    if (closed) return false;
                    age++;
                    for (int ring = 0; ring < 3; ring++) {
                        int a = age - ring * 10;
                        if (a < 0 || a > 32) continue;
                        double rr = 1.5 + (arena - 1.5) * a / 32.0;
                        for (Player pl : real()) {
                            int bit = 1 << ring, mask = hitMask.getOrDefault(pl.getUniqueId(), 0);
                            if ((mask & bit) != 0) continue;
                            double d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), c));
                            if (Math.abs(d - rr) > 0.9 || pl.getLocation().getY() - c.getY() > 1.1) continue;
                            hitMask.put(pl.getUniqueId(), mask | bit);
                            hurt(pl, 16, "dark");
                            Sleeper s = sleeper(pl); s.pending = Math.max(s.pending, 0.3);
                            Fx.follow(pl, fx("drowsy"), 50, 0);
                        }
                    }
                    return age < 54;
                }
                @Override public String kind() { return "boss"; }
            });
            later(56, () -> castAt(boss, "CHA_SN_clear"));
            return 60;
        }

        /** 날개 바람: 가까운 적 쪽 앞 부채꼴 120° 반경 12, 예고 0.8초. 24 + 밀침. */
        int gust(Player target) {
            face(target.getLocation());
            anim("wing_gust");
            Location c = ground(boss.getLocation());
            float yaw = yawTo(c, target.getLocation());
            strike(Shape.CONE, c, yaw, 12, 0, 120, sp(16), 24, "wind", fx("gust"), 0, hit -> { for (Player pl : hit) knock(pl, c, 1.4, 0.35); });
            return sp(28);
        }

        /** 깃털 비: 맵 곳곳 원 반경 2 열네 곳, 세 번에 나눠, 예고 0.9초. 각 18. */
        int feathers() {
            anim("feather_rain");
            for (int w = 0; w < 3; w++) {
                int n = w == 0 ? 4 : 5;
                later(1 + 8 * w, () -> {
                    List<Player> r = real();
                    for (int k = 0; k < n; k++) {
                        Location at = (k == 0 && !r.isEmpty()) ? ground(r.get(ReaperBoss.RNG.nextInt(r.size())).getLocation()) : randomIn(center, arena - 1);
                        strike(Shape.CIRCLE, at, 0, 2, 0, 0, sp(18), 18, "light", fx("feather"), 6, null);
                    }
                });
            }
            return sp(18) + 20;
        }

        /** 베개 짓누르기: 대상 발밑 원 반경 5, 예고 1초. 30. */
        int pillow(Player target) {
            anim("pillow_press");
            Location at = ground(target.getLocation());
            int warn = sp(20);
            later(Math.max(1, warn - 12), () -> {
                LivingEntity pw = prop("CA_Somnia_pillow", at.clone().add(0, 0.1, 0));
                animOn(pw, "CHA_SN_p_pillow_drop");
                later(32, () -> { animOn(pw, "CHA_SN_p_pillow_vanish"); removeLater(pw, 20); });
            });
            strike(Shape.CIRCLE, at, 0, 5, 0, 0, warn, 30, "dark", fx("pillow"), 12, null);
            return warn + 16;
        }

        /** [꿈] 떨어지는 계단: 평행 직선 3줄 (폭 3 × 18, 5칸 간격) 0.3초 차, 예고 0.6초. 각 26. */
        int stairs() {
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_dream_cast");
            float yaw = ReaperBoss.RNG.nextFloat() * 360;
            Vector side = dir(yaw + 90);
            int n = phase == P_NIGHTMARE ? 4 : 3;
            for (int k = 0; k < n; k++) {
                double off = (k - (n - 1) / 2.0) * 5 + (ReaperBoss.RNG.nextDouble() - 0.5) * 2;
                Location start = ground(dream.clone().add(side.clone().multiply(off)).subtract(dir(yaw).multiply(9)));
                later(1 + 6 * k, () -> strike(Shape.LINE, start, yaw, 18, 3, 0, 12, 26, "dark", fx("stairs"), 8, null));
            }
            return 12 + 6 * n + 8;
        }

        /** [꿈] 눈꺼풀: 가운데 폭 3 띠만 남기고 양쪽이 닫힘, 예고 0.9초. 34. */
        int eyelid() {
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_eyelid");
            float yaw = ReaperBoss.RNG.nextFloat() * 180;
            Vector f = dir(yaw), side = dir(yaw + 90);
            Location c = ground(dream);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Location start = ground(c.clone().add(side.clone().multiply(sgn * (1.5 + 7.25))).subtract(f.clone().multiply(arena)));
                strike(Shape.LINE, start, yaw, arena * 2, 14.5, 0, 18, 34, "dark", null, 0, null);
            }
            later(17, () -> Fx.effect(plugin, fx("eyelid"), c, yaw, 40));
            return 34;
        }

        /** [꿈] 악몽 분신: 그림자 둘이 가까운 적이 1.5초 / 2초 전에 지나간 길을 5초 동안 따라옴. 닿으면 20 (1초에 한 번). */
        int clone(Player target) {
            if (target == null) return 10;
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_dream_cast");
            List<Location> trail = new ArrayList<>();
            ArmorStand[] shades = new ArmorStand[2]; UUID[] fx = new UUID[2];
            for (int i = 0; i < 2; i++) {
                shades[i] = dream.getWorld().spawn(target.getLocation(), ArmorStand.class, a -> { a.setInvisible(true); a.setMarker(true); a.setGravity(false); a.setPersistent(false); a.addScoreboardTag(TAG); });
                fx[i] = Fx.follow(shades[i], fx("clone"), 120, 0);
            }
            Map<UUID, Integer> cd = new HashMap<>();
            plugin.ticker().add(new Ticker.Tick() {
                int age;
                @Override public boolean tick() {
                    if (closed || !target.isValid() || target.isDead()) { end(); return false; }
                    age++;
                    trail.add(target.getLocation().clone());
                    int[] delay = {30, 40};
                    for (int i = 0; i < 2; i++) {
                        int idx = trail.size() - 1 - delay[i];
                        if (idx < 0) continue;
                        Location at = trail.get(idx); shades[i].teleport(at);
                        for (Player pl : dreamers()) if (pl.getLocation().distanceSquared(at) < 1.2 * 1.2 && cd.getOrDefault(pl.getUniqueId(), 0) <= age) {
                            cd.put(pl.getUniqueId(), age + 20); hurt(pl, 20, "dark");
                        }
                    }
                    if (age >= 140) { end(); return false; }
                    return true;
                }
                void end() { for (int i = 0; i < 2; i++) { Fx.stop(fx[i]); if (shades[i] != null) shades[i].remove(); } }
                @Override public void stop() { end(); }
                @Override public String kind() { return "boss"; }
            });
            return 70;
        }

        /** [꿈] 꿈 붕괴: 꿈 바닥 절반(반원 반경 16), 예고 1초. 20 + 현실로 튕김 (졸음 유지). */
        int collapse() {
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_eyelid");
            float yaw = ReaperBoss.RNG.nextFloat() * 360;
            Location c = ground(dream);
            strike(Shape.CONE, c, yaw, arena, 0, 180, 20, 20, "dark", fx("collapse"), 0, hit -> { for (Player pl : hit) later(6, () -> exitDream(pl, false)); });
            return 34;
        }

        /* ---------------------------------------------------------------- 50% 깨지 않는 꿈 */

        void damaged(EntityDamageEvent e) {
            if (invulnerable) { e.setCancelled(true); return; }
            boolean full = routing || phase == P_GROGGY || (enraged && onDreamSide(boss.getLocation()));
            if (!full) e.setDamage(e.getDamage() * 0.2);
            double after = boss.getHealth() - e.getFinalDamage();
            if (!nightmareDone && after <= maxHp() * 0.5) {
                e.setCancelled(true);
                boss.setHealth(Math.max(1, maxHp() * 0.5));
                startNightmare();
            } else if (nightmareDone && !enraged && after <= maxHp() * 0.3) later(1, this::enrage);
        }
        /** 꿈 몸이 맞음 → 현실 몸 체력이 깎임 (꿈 몸은 그대로). */
        void dreamHit(EntityDamageEvent e) {
            double dmg = e.getDamage();
            e.setDamage(0); e.setCancelled(true);
            if (invulnerable || phase == P_BELL) return;
            routing = true;
            try {
                Entity src = e instanceof EntityDamageByEntityEvent be ? be.getDamager() : null;
                if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
                if (src != null) boss.damage(dmg, src); else boss.damage(dmg);
            } finally { routing = false; }
        }

        void startNightmare() {
            if (phase == P_NIGHTMARE) return;
            nightmareDone = true;
            phase = P_NIGHTMARE; phaseAt = t; invulnerable = true; dreamNextAt = t + 40;
            castAt(boss, "CHA_SN_clear");
            poolsOff(); bellsOff();
            for (Player pl : real()) enterDream(pl, true);
            Fx.effect(plugin, fx("dreamfall"), ground(dream), 0, 60);
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_dream_cast");
            say("이번엔... 깨지 못할 거야.");
        }
        void wakeCall() {
            phase = P_BELL; phaseAt = t;
            if (dreamBody != null) castAt(dreamBody, "CHA_SN_a_wake");
            Fx.effect(plugin, fx("wake"), ground(dream), 0, 60);
            wakeBell = prop("CA_Somnia_bell", ground(dream));
            if (wakeBell != null) { wakeBell.addScoreboardTag(BELL_TAG); castAt(wakeBell, "CHA_SN_p_bell_glow"); wakeBellFx = Fx.held(fx("bell_glow"), ground(dream), 0, 1200); }
            say("...일어나.");
        }
        void ringWake() {
            Fx.stop(wakeBellFx); wakeBellFx = null;
            if (wakeBell != null) { castAt(wakeBell, "CHA_SN_p_bell_ring"); Fx.effect(plugin, fx("bell_ring"), ground(wakeBell.getLocation()), 0, 40); removeLater(wakeBell, 40); wakeBell = null; }
            for (Player pl : dreamers()) exitDream(pl, true);
            invulnerable = false;
            later(10, () -> {
                Fx.effect(plugin, fx("wake"), ground(boss.getLocation()), 0, 60);
                groggy(200);
                spawnBells(); spawnPools();
            });
        }
        void groggy(int ticks) {
            phase = P_GROGGY; groggyUntil = t + ticks;
            boss.setAI(false);
            castAt(boss, "CHA_SN_clear"); anim("groggy");
            castAt(boss, "CHA_SN_solid"); solid = true;
            Fx.stop(groggyFx); groggyFx = Fx.follow(boss, fx("groggy"), ticks + 10, 0);
        }
        void endGroggy() {
            Fx.stop(groggyFx); groggyFx = null;
            castAt(boss, "CHA_SN_clear");
            if (!enraged) { castAt(boss, "CHA_SN_veil"); solid = false; }
            phase = P_FIGHT; boss.setAI(true); realNextAt = t + 20; dreamNextAt = t + 40;
        }

        /* ---------------------------------------------------------------- 30% 경계 붕괴 */

        void enrage() {
            if (enraged) return;
            enraged = true;
            if (phase != P_FIGHT) { Fx.stop(groggyFx); groggyFx = null; phase = P_FIGHT; boss.setAI(true); invulnerable = false; }
            poolsOff(); bellsOff();
            for (Player pl : dreamers()) exitDream(pl, false);
            castAt(boss, "CHA_SN_clear"); anim("enrage");
            Fx.effect(plugin, fx("enrage"), ground(boss.getLocation()), 0, 60);
            say("꿈과 현실의 경계 따위... 이제 없어.");
            later(44, () -> startBoundary(ReaperBoss.RNG.nextFloat() * 360));
            realNextAt = t + 60;
        }
        void startBoundary(float yaw) {
            Fx.stop(boundaryFx);
            boundaryYaw0 = yaw; boundaryAt = t;
            boundaryFx = Fx.held(fx("boundary"), ground(center), yaw, 1220);
        }
        /** 지금 경계선 방향 (VFX +Z 축과 같은 방향으로 돎). 회전 방향이 VFX 와 반대로 보이면 부호만 바꾼다. */
        float boundaryYaw() { return boundaryYaw0 + (float) (BOUNDARY_DEG_PER_S * (t - boundaryAt) / 20.0); }
        boolean onDreamSide(Location p) {
            if (!enraged || boundaryFx == null) return false;
            Vector d = dir(boundaryYaw()), v = p.toVector().subtract(center.toVector());
            return d.getX() * v.getZ() - d.getZ() * v.getX() > 0;
        }
        void dreamSideParticles() {
            World w = center.getWorld();
            for (int k = 0; k < 24; k++) {
                Location l = randomIn(center, arena);
                if (onDreamSide(l)) w.spawnParticle(Particle.END_ROD, l.add(0, 0.3 + ReaperBoss.RNG.nextDouble() * 1.5, 0), 1, 0, 0.2, 0, 0.01);
            }
        }

        /* ---------------------------------------------------------------- 정리 */

        void reset() {
            for (Player pl : dreamers()) exitDream(pl, true);
            Fx.stop(boundaryFx); Fx.stop(groggyFx); boundaryFx = groggyFx = null;
            poolsOff(); bellsOff(); removeProps();
            phase = P_FIGHT; enraged = false; nightmareDone = false; invulnerable = false; solid = false;
            for (Sleeper s : sleepers.values()) { s.gauge = 0; s.pending = 0; if (s.bar != null) s.bar.removeAll(); s.bar = null; }
            sleepers.clear();
            castAt(boss, "CHA_SN_clear"); castAt(boss, "CHA_SN_veil");
            boss.setAI(true); boss.setHealth(maxHp()); boss.teleport(center);
            realNextAt = t + 80; dreamNextAt = t + 120; emptyFor = 0;
            later(10, () -> { if (dreamBody == null || !dreamBody.isValid()) { dreamBody = prop("CA_Boss_somnia_dream", dream.clone()); if (dreamBody != null) { dreamBody.addScoreboardTag(DREAM_TAG); dreamBody.removeScoreboardTag(TAG); } } spawnBells(); spawnPools(); });
        }
        void removeProps() {
            for (Location c : List.of(center, dream))
                for (Entity e : c.getWorld().getNearbyEntities(c, arena + 8, 24, arena + 8))
                    if (e.getScoreboardTags().contains(TAG) || e.getScoreboardTags().contains(BELL_TAG)) e.remove();
            for (int i = 0; i < 4; i++) bells[i] = null;
            wakeBell = null; Fx.stop(wakeBellFx); wakeBellFx = null;
        }
        void close() {
            if (closed) return;
            for (Player pl : dreamers()) exitDream(pl, false);   // 꿈에 갇히지 않게
            closed = true;
            Fx.stop(boundaryFx); Fx.stop(groggyFx); Fx.stop(bellFx);
            for (UUID h : poolFx) Fx.stop(h);
            removeProps();
            if (dreamBody != null && dreamBody.isValid()) dreamBody.remove();
            for (Sleeper s : sleepers.values()) if (s.bar != null) s.bar.removeAll();
            FIGHTS.remove(boss.getUniqueId());
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== events */

    static Fight byDreamBody(Entity e) {
        if (!e.getScoreboardTags().contains(DREAM_TAG)) return null;
        for (Fight f : FIGHTS.values()) if (e.equals(f.dreamBody)) return f;
        return null;
    }
    static Fight byBell(Entity e) {
        if (!e.getScoreboardTags().contains(BELL_TAG)) return null;
        for (Fight f : FIGHTS.values()) { if (e.equals(f.wakeBell)) return f; for (LivingEntity b : f.bells) if (e.equals(b)) return f; }
        return null;
    }

    static final class Hooks implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            Fight d = byDreamBody(e.getEntity());
            if (d != null) { d.dreamHit(e); return; }
            Fight b = byBell(e.getEntity());
            if (b != null) {
                e.setCancelled(true);
                if (e instanceof EntityDamageByEntityEvent be) {
                    Entity src = be.getDamager();
                    if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
                    if (src instanceof Player pl) b.hitBell(pl, e.getEntity());
                }
                return;
            }
            if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void interact(PlayerInteractEntityEvent e) {
            Fight b = byBell(e.getRightClicked());
            if (b != null) { e.setCancelled(true); b.hitBell(e.getPlayer(), e.getRightClicked()); }
        }

        /** 광폭화 보스 근접 피해 1.25배. */
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void melee(EntityDamageByEntityEvent e) {
            if (DemonFight.hurting) return;
            Fight f = FIGHTS.get(e.getDamager().getUniqueId());
            if (f != null && f.enraged && e.getEntity() instanceof Player) e.setDamage(e.getDamage() * 1.25);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void playerDeath(PlayerDeathEvent e) {
            for (Fight f : FIGHTS.values()) { Sleeper s = f.sleepers.get(e.getEntity().getUniqueId()); if (s != null) { s.gauge = 0; s.pending = 0; s.inDream = false; } }
        }
        @EventHandler
        public void quit(PlayerQuitEvent e) {
            for (Fight f : FIGHTS.values()) { Sleeper s = f.sleepers.remove(e.getPlayer().getUniqueId()); if (s != null && s.bar != null) s.bar.removeAll(); }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f == null) return;
            Fx.effect(f.plugin, fx("wake"), e.getEntity().getLocation(), 0, 60);
            f.say("아... 아침이... 오는구나...");
            f.close();
        }
    }
}
