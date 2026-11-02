package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chaash - 1인 던전 보스 "재의 그림자" 진행자 (2026-11-03). 미몹 CA_Boss_ash_shadow 의 ~onSpawn 에서
 * {@code chaash{a=start;arena=16} @self}.
 *
 * <ol>
 *   <li>1페이즈 (100~50%): 경기장에 빛 발산 장치 3개 (CA_Ash_light_device, 모델 gm_light_receiver 재사용).
 *       장치 수에 따라 보스 초당 회복 3개 4% / 2개 1.5% / 1개 0.5%, 공격력 ×2.0 / ×1.7 / ×1.4 / ×1.0.
 *       유도 패턴(돌진·손 내려찍기·추적하는 재·잠행→솟구침) 판정 안에 장치가 있으면 부서짐. 견제 패턴은 장치를 못 부숨.
 *       부서진 장치는 40초마다 하나씩 다시 켜짐. 장치가 0개가 되는 순간 다음 패턴 = 발악.</li>
 *   <li>50%: 체력이 50% 에서 멈춤 → 중앙 귀환 → 그림자 탄막 25초 (무적, 회복 없음, 높이 1칸 고정 탄, 맞으면 최대 체력 55%, 피격 후 0.5초 무적).</li>
 *   <li>탄막을 버티면 그로기 10초 (맞음, 공격 안 함) → 변신: 남은 장치를 하나씩 흡수 (무적) → 광폭화.
 *       광폭화 = 장치 재소환 없음, 패턴 25% 빠름, 이동 1.5배, 공격력 흡수 0개 ×1.5 / 1 ×1.7 / 2 ×1.9 / 3 ×2.2 (+ 3개면 유도 연타 +1).</li>
 * </ol>
 * 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX 팩 claude:as_* (예고 없음). 대사·기믹 안내 = 채팅 + 타이틀.
 * 서버 부담: 보스 하나당 Ticker 1개 (판단 5틱마다, 탄막 중에만 매 틱 탄 이동·판정), 예고마다 지연 작업 1개.
 */
final class AshShadowBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_ash_prop";
    static final String NAME = "§8[§7재의 그림자§8] §f";
    /** 패턴 판정 중 = 이미 배율이 곱해진 피해 (근접 배율 훅이 두 번 곱하지 않게). */
    static boolean spellHit;

    AshShadowBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 16), 8, 32));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /** /테스트 보스 재의그림자 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 재의 그림자. */
    static String test(Player p, String what, String arg) {
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 재의 그림자가 없어요 (/mm mobs spawn CA_Boss_ash_shadow - 소환한 자리가 경기장 중심)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 기믹 중이라 패턴을 못 써요";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "장치" -> { int n = Math.clamp(Integer.parseInt(arg), 0, 3); f.setDevices(n); return "장치 " + n + "개"; }
            case "탄막" -> { f.boss.setHealth(Math.max(1, f.maxHp() * 0.5)); f.startBarrage(); return "50% 탄막 시작"; }
            case "그로기" -> { f.endBarrage(true); return "그로기 시작"; }
            case "변신" -> { f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * 0.45))); f.startTransform(); return "변신 시작"; }
            default -> { f.reset(); return "처음 상태로 (체력 가득, 장치 3개)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final int P_DEVICES = 1, P_BARRAGE = 2, P_GROGGY = 3, P_TRANSFORM = 4, P_ENRAGED = 5;
        static final double[] HEAL = {0, 0.005, 0.015, 0.04};           // 장치 0~3개 초당 회복 (최대 체력 비율)
        static final double[] DEVICE_MULT = {1.0, 1.4, 1.7, 2.0};        // 장치 0~3개 공격력
        static final double[] ENRAGE_MULT = {1.5, 1.7, 1.9, 2.2};        // 흡수 0~3개 광폭화 공격력
        static final int RESPAWN = 800, BARRAGE = 500, GROGGY = 200;

        final MagicMythicPlugin plugin; LivingEntity boss; final Location center; final double arena;
        final Device[] devices = new Device[3];
        int t, nextAt = 60, emptyFor, phase = P_DEVICES, phaseAt, respawnAt = -1, absorbed;
        boolean invulnerable, closed, desperateQueued, barrageDeath;
        String last = "", lastTarget = "";
        UUID auraFx, stunFx, enrageFx; int enrageFxAt;
        Barrage barrage;
        final Map<UUID, Integer> iframe = new HashMap<>();

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena;
            Fx.effect(plugin, "claude:as_fate/intro", center, center.getYaw(), 60);
            anim("spawn");
            later(20, () -> {
                title("§8§l재의 그림자", "§7빛이 있는 한, 그림자는 사라지지 않는다");
                say("빛이 있는 한... 그림자는 사라지지 않는다.");
            });
            later(30, () -> { for (int i = 0; i < 3; i++) spawnDevice(i, false); });
        }

        /* ---------------------------------------------------------------- 채팅 + 타이틀 */
        void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (barrage != null) barrage.step();
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty() && phase == P_BARRAGE) { say("§7재 속에 아무도 남지 않았다."); reset(); return true; }   // 탄막 중 전멸 = 처음부터
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            if (t % 20 == 0) second(ps);
            switch (phase) {
                case P_DEVICES, P_ENRAGED -> { if (t >= nextAt) pattern(ps); }
                case P_BARRAGE -> { if (barrage != null && barrage.done()) endBarrage(!barrageDeath); }
                case P_GROGGY -> { if (t - phaseAt >= GROGGY) startTransform(); }
                default -> {}
            }
            return true;
        }

        /** 1초마다: 장치 회복, 장치 재점화, 광폭화 오라 다시 붙이기. */
        void second(List<Player> ps) {
            if (phase == P_DEVICES) {
                for (int i = 0; i < 3; i++) if (devices[i] != null && !devices[i].e.isValid()) { devices[i].off(); devices[i] = null; if (respawnAt < 0) respawnAt = t + RESPAWN; }
                int n = deviceCount();
                if (n > 0 && !invulnerable) boss.setHealth(Math.min(maxHp(), boss.getHealth() + maxHp() * HEAL[n]));
                if (n < 3 && respawnAt >= 0 && t >= respawnAt) {
                    for (int i = 0; i < 3; i++) if (devices[i] == null) { spawnDevice(i, true); break; }
                    respawnAt = deviceCount() < 3 ? t + RESPAWN : -1;
                    say("꺼진 빛은... 다시 타오른다.");
                }
                for (Device d : devices) if (d != null) { if (t >= d.relightAt) d.relight(); else if (t >= d.relinkAt) d.relink(); else if (d.tether != null) d.tether.move(d.e.getLocation().add(0, 1.2, 0), boss.getLocation().add(0, 3, 0)); }
            }
            if (phase == P_ENRAGED && t >= enrageFxAt) { Fx.stop(enrageFx); enrageFx = Fx.follow(boss, "claude:as_absorb/enrage", 1200, 0); enrageFxAt = t + 1100; }
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        boolean free() { return phase == P_DEVICES || phase == P_ENRAGED; }
        boolean enraged() { return phase == P_ENRAGED; }
        /** 지금 공격력 배율. */
        double mult() { return phase == P_ENRAGED ? ENRAGE_MULT[Math.min(3, absorbed)] : phase == P_DEVICES ? DEVICE_MULT[deviceCount()] : 1.0; }
        /** 광폭화는 예고·간격 25% 짧게. */
        int sp(int ticks) { return enraged() ? Math.max(3, Math.round(ticks * 0.75f)) : ticks; }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = arena + 6;
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (ReaperBoss.flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < 24) out.add(pl);
            }
            return out;
        }
        static boolean anyAlive(List<Player> ps) { for (Player p : ps) if (!p.isDead()) return true; return false; }

        /* ---------------------------------------------------------------- 패턴 고르기 */

        record Pat(String id, int weight, boolean lure) {}
        static final List<Pat> PATTERNS = List.of(
            new Pat("claw", 2, false),
            new Pat("dash", 3, true), new Pat("slam", 3, true), new Pat("rain", 3, true), new Pat("sink", 3, true),
            new Pat("grasp", 2, false), new Pat("afterimage", 1, false), new Pat("veil", 2, false), new Pat("ring", 2, false),
            new Pat("ward", 1, false));

        void pattern(List<Player> ps) {
            if (desperateQueued) { desperateQueued = false; run("desperate", ps); return; }
            List<Pat> pool = new ArrayList<>();
            for (Pat pt : PATTERNS) {
                if (pt.id().equals(last)) continue;
                if (pt.id().equals("afterimage") && (last.isEmpty() || last.equals("ward") || last.equals("desperate"))) continue;
                if (pt.id().equals("ward") && (phase != P_DEVICES || deviceCount() == 0 || deviceCount() == 3)) continue;
                pool.add(pt);
            }
            int sum = pool.stream().mapToInt(Pat::weight).sum(), roll = ReaperBoss.RNG.nextInt(sum);
            Pat pick = pool.get(0);
            for (Pat pt : pool) { roll -= pt.weight(); if (roll < 0) { pick = pt; break; } }
            run(pick.id(), ps);
        }

        /** 패턴 실행. 길이(틱)를 돌려줘 다음 패턴 시각을 정한다. */
        void run(String id, List<Player> ps) {
            Player near = nearest(ps), far = farthest(ps), weak = weakest(ps);
            int len = switch (id) {
                case "claw" -> claw(near);
                case "dash" -> dash(far);
                case "slam" -> slam(near);
                case "rain" -> rain(weak);
                case "sink" -> sink(far);
                case "grasp" -> grasp(far);
                case "afterimage" -> afterimage(ps);
                case "veil" -> veil(ps);
                case "ring" -> ring();
                case "ward" -> ward();
                case "desperate" -> desperate();
                default -> 20;
            };
            if (!id.equals("afterimage")) last = id;
            nextAt = t + len + sp(enraged() ? 8 : 14);
        }

        /* ---------------------------------------------------------------- 기본 공격 · 유도 패턴 (장치 파괴 가능) */

        /** 기본 공격 앞팔 할퀴기: 가까운 적 쪽 부채꼴 반경 6 · 150°, 예고 0.4초. */
        int claw(Player target) {
            if (target == null) return 10;
            face(target.getLocation());
            anim("attack");
            Location at = ground(boss.getLocation());
            float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 6, 0, 150, sp(8), 22, "dark", false, "claude:as_clawswipe/swipe", 6, null);
            return sp(24);
        }

        /** 1 잿빛 돌진: 2번. 직선 18 · 폭 4, 예고 0.6초 → 끝까지 미끄러지듯 이동. 첫 돌진은 가장 먼 적, 두 번째는 다시 조준. */
        int dash(Player target) {
            if (target == null) return 10;
            anim("skill_dash");
            dashOnce(target, 0);
            later(sp(30), () -> { Player again = target.isValid() && !target.isDead() ? target : nearest(players()); if (again != null) { anim("skill_dash"); dashOnce(again, 0); } });
            return sp(64);
        }
        void dashOnce(Player target, int delay) {
            Location from = ground(boss.getLocation());
            float yaw = yawTo(from, target.getLocation());
            face(target.getLocation());
            Fx.effect(plugin, "claude:as_ashdash/windup", from, yaw, 20);
            double len = clampLen(from, dir(yaw), 18);
            strike(Shape.LINE, from, yaw, len, 4, 0, sp(12), 28, "dark", true, "claude:as_ashdash/dash", 18, s -> slide(from, dir(yaw), len));
        }

        /** 2 그림자 손 내려찍기: 등손 좌우 번갈아 3연타 (광폭화 4, 흡수 3개면 +1). 직선 14 · 폭 4, 예고 0.45초. */
        int slam(Player target) {
            if (target == null) return 10;
            anim("skill_slam");
            int hits = enraged() ? 4 : 3;
            if (enraged() && absorbed >= 3) hits++;
            int[] at = enraged() ? new int[]{17, 33, 49, 65, 81} : new int[]{11, 23, 35};
            for (int k = 0; k < hits && k < at.length; k++) {
                int hitT = enraged() ? Math.round(at[k] * 0.75f) : at[k];
                int warnT = sp(9);
                later(Math.max(1, hitT - warnT), () -> {
                    Player tg = target.isValid() && !target.isDead() ? target : nearest(players());
                    if (tg == null) return;
                    Location from = ground(boss.getLocation());
                    float yaw = yawTo(from, tg.getLocation());
                    face(tg.getLocation());
                    strike(Shape.LINE, from, yaw, clampLen(from, dir(yaw), 14), 4, 0, warnT, 24, "dark", true, "claude:as_handslam/slam", 14, null);
                });
            }
            return (enraged() ? Math.round(at[Math.min(hits, at.length) - 1] * 0.75f) : at[hits - 1]) + 12;
        }

        /** 3 추적하는 재: 손가락 튕김 4번 (0.6/0.9/1.2/1.5초) → 그때 체력 낮은 적 위치에 원 반경 4, 예고 0.5초. */
        int rain(Player target) {
            if (target == null) return 10;
            anim("skill_ash_rain");
            Fx.effect(plugin, "claude:as_ashrain/cast", ground(boss.getLocation()), boss.getLocation().getYaw(), 50);
            int drops = enraged() && absorbed >= 3 ? 5 : 4;
            for (int k = 0; k < drops; k++) {
                later(sp(12 + 6 * k), () -> {
                    Player tg = target.isValid() && !target.isDead() ? target : weakest(players());
                    if (tg == null) return;
                    Location at = ground(tg.getLocation());
                    int warnT = sp(10);
                    later(Math.max(1, warnT - 5), () -> areaFx("claude:as_ashrain/fall", at, 4.0, 0f, 4.0));
                    strike(Shape.CIRCLE, at, 0, 4, 0, 0, warnT, 18, "fire", true, null, 4, null);
                });
            }
            return sp(12 + 6 * drops + 14);
        }

        /** 4 잠행 → 솟구침: 녹아내림(1초) 동안 대상 발밑 원 반경 5 예고 → 그 자리에서 솟구침, 곧이어 원 반경 8 2차 폭발. */
        int sink(Player target) {
            if (target == null) return 10;
            anim("skill_sink");
            Fx.effect(plugin, "claude:as_shadowsink/sink", ground(boss.getLocation()), 0, 30);
            Location at = ground(target.getLocation());
            later(sp(12), () -> { if (free()) boss.setInvisible(true); });
            int warnT = sp(22);
            later(warnT + 2, () -> boss.setInvisible(false));   // 판정이 건너뛰어져도 다시 보이게
            strike(Shape.CIRCLE, at, 0, 5, 0, 0, warnT, 26, "dark", true, "claude:as_shadowsink/emerge", 5, s -> {
                Location to = at.clone(); to.setYaw(boss.getLocation().getYaw()); boss.teleport(to); boss.setInvisible(false);
                anim("skill_emerge");
                strike(Shape.CIRCLE, at, 0, 8, 0, 0, sp(8), 18, "dark", true, null, 8, null);
            });
            return warnT + sp(30);
        }

        /* ---------------------------------------------------------------- 견제 패턴 (장치 못 부숨) */

        /** 5 그림자 손아귀: 대상 발밑 원 반경 6, 예고 0.5초 → 1초 속박 + 보스 쪽으로 끌어당김. */
        int grasp(Player target) {
            if (target == null) return 10;
            face(target.getLocation());
            anim("skill_grasp");
            Location at = ground(target.getLocation());
            strike(Shape.CIRCLE, at, 0, 6, 0, 0, sp(10), 12, "dark", false, "claude:as_grasp/grasp", 6, s -> {
                for (Player pl : inside(s)) {
                    if (plugin.status() != null) plugin.status().apply(boss, pl, Status.ROOT, 20, 0);
                    later(20, () -> {
                        if (!pl.isValid() || pl.isDead()) return;
                        Vector v = boss.getLocation().toVector().subtract(pl.getLocation().toVector()).setY(0);
                        if (v.lengthSquared() > 1e-3) pl.setVelocity(v.normalize().multiply(1.4).setY(0.35));
                    });
                }
            });
            return sp(34);
        }

        /** 6 잔상: 고개가 꺾이는 경련 → 0.5초 뒤 직전 패턴을 한 번 더 (같은 대상 규칙). */
        int afterimage(List<Player> ps) {
            anim("skill_afterimage");
            Fx.effect(plugin, "claude:as_afterimage/flicker", ground(boss.getLocation()), boss.getLocation().getYaw(), 14);
            String again = last;
            later(sp(10), () -> { List<Player> now = players(); if (!now.isEmpty() && free()) run(again, now); });
            return sp(14);   // 실제 다음 시각은 다시 쓴 패턴이 정함
        }

        /** 7 재의 장막: 1.5초 시야 차단 → 각 플레이어 발밑 원 반경 7 두 번 (0.7초 예고, 0.5초 간격). */
        int veil(List<Player> ps) {
            anim("skill_veil");
            Fx.effect(plugin, "claude:as_ashveil/wrap", ground(boss.getLocation()), 0, 30);
            for (Player pl : ps) if (plugin.status() != null) plugin.status().apply(boss, pl, Status.BLINDNESS, 30, 0);
            for (int w = 0; w < 2; w++) {
                later(sp(12 + 10 * w), () -> { for (Player pl : players()) strike(Shape.CIRCLE, ground(pl.getLocation()), 0, 7, 0, 0, sp(14), 20, "dark", false, "claude:as_ashveil/burst", 7, null); });
            }
            return sp(52);
        }

        /** 8 화장의 고리: 안쪽 원 r 5 → 바깥 팔각 고리 (r 5~12, 직선 8개) → 안쪽. 각 예고 0.6초. */
        int ring() {
            anim("skill_ring");
            Location c = ground(boss.getLocation());
            int[] at = {sp(1), sp(13), sp(25)};
            for (int k = 0; k < 3; k++) {
                boolean outer = k == 1;
                later(at[k], () -> {
                    if (!outer) { strike(Shape.CIRCLE, c, 0, 5, 0, 0, sp(12), 22, "fire", false, "claude:as_cremationring/inner", 5, null); return; }
                    areaFxLater(sp(12), "claude:as_cremationring/outer", c, 12, 0);
                    for (int s = 0; s < 8; s++) {
                        double a = Math.toRadians(s * 45 + 22.5);
                        Vector radial = new Vector(Math.cos(a), 0, Math.sin(a)), tan = new Vector(-Math.sin(a), 0, Math.cos(a));
                        Location start = c.clone().add(radial.clone().multiply(8.5)).subtract(tan.clone().multiply(5));
                        strike(Shape.LINE, start, yawOf(tan), 10, 7, 0, sp(12), 22, "fire", false, null, 0, null);
                    }
                });
            }
            return sp(46);
        }

        /** 9 빛의 수호: 남은 장치 하나에 2초 보호막 (그동안 유도 패턴에 맞아도 안 부서짐). */
        int ward() {
            List<Device> live = new ArrayList<>();
            for (Device d : devices) if (d != null && t >= d.shieldUntil) live.add(d);
            if (live.isEmpty()) return 10;
            Device d = live.get(ReaperBoss.RNG.nextInt(live.size()));
            anim("skill_ward");
            face(d.e.getLocation());
            later(8, () -> { d.shieldUntil = t + 40; Fx.follow(d.e, "claude:as_ward/shield", 44, 0); });
            return 26;
        }

        /** 10 발악 (장치가 0개가 된 직후 1번): 8방향 균열 직선 10 · 폭 3, 예고 0.5초, 22.5° 돌려서 한 번 더. */
        int desperate() {
            anim("skill_desperate");
            title("§c§l소등", "§f빛이 모두 꺼졌다 — 지금이다");
            say("빛이... 빛이 없어...!");
            Location c = ground(boss.getLocation());
            for (int w = 0; w < 2; w++) {
                double off = w * 22.5;
                later(6 + 12 * w, () -> {
                    areaFxLater(10, "claude:as_desperate/scream", c, 10, (float) off);
                    for (int s = 0; s < 8; s++) strike(Shape.LINE, c, (float) (s * 45 + off), 10, 3, 0, 10, 28, "dark", false, null, 0, null);
                });
            }
            return 46;
        }

        /* ---------------------------------------------------------------- 판정 공통 */

        /**
         * 모드 예고 → 같은 모양 판정. dmg × 지금 배율. lure = 판정 안 장치 파괴. vfx 는 판정 순간 재생 (vfxSize = 그 팩 기준 크기).
         */
        void strike(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks, double dmg, String element,
                    boolean lure, String vfx, double vfxSize, Consumer<Show> after) {
            if (!free()) return;
            Show s;
            try {
                s = new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw, (float) size, (float) width, (float) (angle <= 0 ? 60 : angle), ticks, TelegraphProtocol.RED);
            } catch (IllegalArgumentException e) { return; }
            plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {
                if (closed || !free()) return;
                if (vfx != null) areaFx(vfx, at, size, yaw, vfxSize);
                double d = dmg * mult();
                spellHit = true;
                try { for (Player pl : inside(show)) plugin.targets().hurt(boss, pl, d, element); } finally { spellHit = false; }
                if (lure) breakDevicesIn(show);
                if (after != null) after.accept(show);
            });
        }
        List<Player> inside(Show s) {
            List<Player> out = new ArrayList<>();
            for (Player pl : players()) if (Math.abs(pl.getLocation().getY() - s.y()) < 3.5 && TelegraphProtocol.inside(s, pl.getLocation().getX(), pl.getLocation().getZ(), 0.3)) out.add(pl);
            return out;
        }
        /** 팩 기준 크기로 만든 static VFX 를 실제 크기로 (같으면 그대로). */
        void areaFx(String id, Location at, double size, float yaw, double ref) {
            if (id == null) return;
            if (ref <= 0 || Math.abs(size - ref) < 0.05) Fx.effect(plugin, id, at, yaw, 60);
            else plugin.reactions().vfx().areaEffect(id, at, size, yaw, 60, 0xFFFFFF);
        }
        void areaFxLater(int ticks, String id, Location at, double size, float yaw) { later(ticks, () -> areaFx(id, at, size, yaw, size)); }

        /** 돌진 이동: 6틱에 끝까지. */
        void slide(Location from, Vector dir, double len) {
            plugin.ticker().add(new Ticker.Tick() {
                int k;
                @Override public boolean tick() {
                    if (closed || !boss.isValid() || !free()) return false;
                    k++; Location now = ground(from.clone().add(dir.clone().multiply(len * Math.min(1, k / 6.0)))); now.setDirection(dir); boss.teleport(now);
                    return k < 6;
                }
                @Override public String kind() { return "boss"; }
            });
        }
        double clampLen(Location from, Vector dir, double want) {
            for (double s = 1; s <= want; s += 1) if (ReaperBoss.flat2(from.clone().add(dir.clone().multiply(s)), center) > (arena - 0.5) * (arena - 0.5)) return Math.max(4, s - 1);
            return want;
        }

        /* ---------------------------------------------------------------- 빛 발산 장치 */

        final class Device {
            final int slot; final Entity e; UUID light; Fx.Link tether; int shieldUntil, relightAt, relinkAt;
            Device(int slot, Entity e) { this.slot = slot; this.e = e; relight(); }
            void relight() { Fx.stop(light); light = Fx.follow(e, "claude:as_device/light", 1200, 0); relightAt = t + 1100; relink(); }
            /** 회복 빛줄기: 링크 캐스트 수명이 10초라 9초마다 다시 붙임. */
            void relink() { if (tether != null) tether.close(); tether = Fx.link("claude:as_device/tether", e.getLocation().add(0, 1.2, 0), boss.getLocation().add(0, 3, 0)); relinkAt = t + 180; }
            void off() { Fx.stop(light); light = null; if (tether != null) tether.close(); tether = null; }
            void remove() { off(); if (e.isValid()) e.remove(); }
        }

        Location slot(int i) {
            double a = Math.toRadians(center.getYaw() + 90 + i * 120);
            return ground(center.clone().add(-Math.sin(a) * arena * 0.56, 0, Math.cos(a) * arena * 0.56));
        }
        void spawnDevice(int i, boolean respawn) {
            if (devices[i] != null) return;
            Location at = slot(i);
            Fx.effect(plugin, respawn ? "claude:as_device/respawn" : "claude:as_device/respawn", at, 0, 40);
            Entity e = prop("CA_Ash_light_device", at);
            if (e != null) devices[i] = new Device(i, e);
        }
        int deviceCount() { int n = 0; for (Device d : devices) if (d != null && d.e.isValid()) n++; return n; }
        void setDevices(int n) {
            for (int i = 0; i < 3; i++) { if (i < n && devices[i] == null) spawnDevice(i, true); else if (i >= n && devices[i] != null) { devices[i].remove(); devices[i] = null; } }
            respawnAt = deviceCount() < 3 ? t + RESPAWN : -1;
        }

        void breakDevicesIn(Show s) {
            if (phase != P_DEVICES) return;
            for (int i = 0; i < 3; i++) {
                Device d = devices[i];
                if (d == null || !d.e.isValid()) continue;
                if (!TelegraphProtocol.inside(s, d.e.getLocation().getX(), d.e.getLocation().getZ(), 0.8)) continue;
                if (t < d.shieldUntil) { Fx.effect(plugin, "claude:as_ward/shield", d.e.getLocation(), 0, 20); continue; }
                Fx.effect(plugin, "claude:as_device/break", d.e.getLocation(), 0, 30);
                d.remove(); devices[i] = null;
                int left = deviceCount();
                if (respawnAt < 0) respawnAt = t + RESPAWN;
                if (left == 0) { desperateQueued = true; }
                else { title("", "§e빛 발산 장치가 부서졌다 §7(" + left + "개 남음)"); say(left == 2 ? "감히... 내 빛을." : "아직 빛은 남아 있다."); }
            }
        }

        /* ---------------------------------------------------------------- 50% 탄막 → 그로기 → 변신 → 광폭화 */

        void damaged(EntityDamageEvent e) {
            if (invulnerable) { e.setCancelled(true); return; }
            if (phase == P_DEVICES && boss.getHealth() - e.getFinalDamage() <= maxHp() * 0.5) {
                e.setCancelled(true);
                boss.setHealth(Math.max(1, maxHp() * 0.5));
                startBarrage();
            }
        }

        void startBarrage() {
            if (phase == P_BARRAGE) return;
            phase = P_BARRAGE; phaseAt = t; invulnerable = true; nextAt = Integer.MAX_VALUE; barrageDeath = false;
            boss.setInvisible(false);
            for (Device d : devices) if (d != null) { if (d.tether != null) d.tether.close(); d.tether = null; }   // 빛은 남되 회복 끊김
            Fx.effect(plugin, "claude:as_fate/vanish", boss.getLocation(), 0, 20);
            later(14, () -> {
                if (phase != P_BARRAGE) return;
                boss.teleport(center.clone()); boss.setAI(false);
                Fx.effect(plugin, "claude:as_fate/appear", center, center.getYaw(), 20);
                anim("barrage_start");
                Fx.effect(plugin, "claude:as_barrage/start", ground(center), center.getYaw(), 40);
                auraFx = Fx.follow(boss, "claude:as_barrage/aura", 560, 0);
                title("§8§l그림자 탄막", "§f25초를 버텨라 — 두 번 맞으면 죽는다");
                say("타오르는 재 속에서... 춤을 춰 봐라.");
                later(36, () -> { if (phase == P_BARRAGE && barrage == null) barrage = new Barrage(this); });
            });
        }

        void endBarrage(boolean survived) {
            if (barrage != null) { barrage.close(); barrage = null; }
            Fx.stop(auraFx); auraFx = null;
            if (!survived) return;   // 다 죽으면 60초 뒤 처음 상태로 (tick)
            phase = P_GROGGY; phaseAt = t; invulnerable = false; nextAt = Integer.MAX_VALUE;
            boss.setAI(false);
            anim("groggy_start");
            Fx.effect(plugin, "claude:as_groggy/fall", ground(center), 0, 30);
            later(22, () -> { if (phase != P_GROGGY) return; anim("groggy_loop"); stunFx = Fx.follow(boss, "claude:as_groggy/stun", GROGGY, 0); });
            title("§6§l그로기", "§f10초 — 지금 쏟아부어라");
            say("크윽... 재가... 흩어진다...");
        }

        void startTransform() {
            if (phase == P_TRANSFORM || phase == P_ENRAGED) return;
            phase = P_TRANSFORM; phaseAt = t; invulnerable = true;
            boss.setInvisible(false);
            Fx.stop(stunFx); stunFx = null;
            boss.setAI(false);
            anim("transform");
            say("남은 빛을... 전부 삼키겠다.");
            int k = 0;
            for (int i = 0; i < 3; i++) {
                Device d = devices[i]; if (d == null) continue;
                int idx = i, at = 12 + 12 * k++;
                later(at, () -> {
                    Device dd = devices[idx]; if (dd == null) return;
                    Fx.Link beam = Fx.link("claude:as_absorb/beam", dd.e.getLocation().add(0, 1.2, 0), boss.getLocation().add(0, 3, 0));
                    later(18, () -> { if (beam != null) beam.close(); });
                    Fx.effect(plugin, "claude:as_device/break", dd.e.getLocation(), 0, 30);
                    dd.remove(); devices[idx] = null; absorbed++;
                    title("", "§c빛이 그림자에 삼켜진다 §7(" + absorbed + ")");
                });
            }
            later(60, () -> {
                swapModel();
                Fx.effect(plugin, "claude:as_absorb/burst", ground(boss.getLocation()), 0, 40);
                later(2, () -> anim("spawn"));   // 새 모델이 붙은 다음 틱에 광폭화 등장 모션
                title("§4§l광폭화", absorbed == 0 ? "§f빛 없이 드러난 그림자" : "§f삼킨 빛 " + absorbed + "개 — 그만큼 강해졌다");
                say(absorbed >= 2 ? "이 빛으로... 너를 태운다!" : "빛이 없어도... 그림자는 남는다.");
                later(50, () -> {
                    phase = P_ENRAGED; invulnerable = false; boss.setAI(true); nextAt = t + 10;
                    AttributeInstance spd = boss.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (spd != null) spd.setBaseValue(spd.getBaseValue() * 1.5);
                    enrageFxAt = t;
                });
            });
        }

        /** 기본형 모델 → 광폭화 모델 (CHA_ASH_swap: model 지우고 새 모델). */
        void swapModel() { castAt("CHA_ASH_swap", boss.getLocation()); }

        /* ---------------------------------------------------------------- 탄막 */

        /** 높이 1칸 고정 탄. 기하학 패턴 6개를 이어서 약 25초. 맞으면 최대 체력 55%, 피격 후 10틱 무적. */
        static final class Barrage {
            record Shot(double[] p, double[] v, int until, GameplayVisual vis) {}
            final Fight f; final List<Shot> shots = new ArrayList<>(); final double y; int age;
            Barrage(Fight f) { this.f = f; this.y = Telegraphs.ground(f.center) + 1.0; }
            boolean done() { return age >= BARRAGE && shots.isEmpty(); }

            void step() {
                age++;
                if (age <= BARRAGE - 40) emit();
                List<Player> ps = f.players();
                for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
                    Shot s = it.next();
                    s.p()[0] += s.v()[0]; s.p()[1] += s.v()[1];
                    boolean gone = age >= s.until() || ReaperBoss.flat2(at(s), f.center) > (f.arena + 1) * (f.arena + 1);
                    boolean hit = false;
                    for (Player pl : ps) {
                        if (Math.abs(pl.getLocation().getY() + 0.9 - y) > 1.3) continue;
                        double dx = pl.getLocation().getX() - s.p()[0], dz = pl.getLocation().getZ() - s.p()[1];
                        if (dx * dx + dz * dz > 0.8 * 0.8) continue;
                        Integer until = f.iframe.get(pl.getUniqueId());
                        if (until != null && f.t < until) continue;
                        f.iframe.put(pl.getUniqueId(), f.t + 10);
                        spellHit = true;
                        try { f.plugin.targets().hurt(f.boss, pl, ReaperBoss.Fight.maxOf(pl) * 0.55, "dark"); } finally { spellHit = false; }
                        hit = true; break;
                    }
                    if (hit || gone) {
                        if (hit) Fx.effect(f.plugin, "claude:as_barrage/pop", at(s), 0, 10);
                        if (s.vis() != null) s.vis().close();
                        it.remove();
                    } else if (s.vis() != null && age % 2 == 0) s.vis().move(at(s), new Vector(s.v()[0], 0, s.v()[1]));
                }
            }
            Location at(Shot s) { return new Location(f.center.getWorld(), s.p()[0], y, s.p()[1]); }

            void fire(double x, double z, double deg, double speed) {
                double a = Math.toRadians(deg), vx = -Math.sin(a) * speed, vz = Math.cos(a) * speed;
                Location at = new Location(f.center.getWorld(), x, y, z);
                GameplayVisual vis = GameplayVisual.cast(null, "claude:as_barrage/shard", at, new Vector(vx, 0, vz));
                shots.add(new Shot(new double[]{x, z}, new double[]{vx, vz}, age + 160, vis));
            }
            void fromCenter(double deg, double speed) { fire(f.center.getX(), f.center.getZ(), deg, speed); }

            /** 0~70 별(5갈래 회전) · 80~160 동심 고리 · 170~260 2중 나선 · 270~340 십자 휩쓸기 · 350~420 좁혀오는 육각 · 430~460 마무리 고리 3번. */
            void emit() {
                int a = age;
                if (a <= 70 && a % 6 == 0) for (int k = 0; k < 5; k++) fromCenter(k * 72 + a * 1.5, 0.30);
                else if (a >= 80 && a <= 160 && (a - 80) % 20 == 0) { int n = 16; double off = ((a - 80) / 20) % 2 == 0 ? 0 : 360.0 / n / 2; for (int k = 0; k < n; k++) fromCenter(k * 360.0 / n + off, 0.26); }
                else if (a >= 170 && a <= 260 && a % 4 == 0) { double b = (a - 170) * 3.5; fromCenter(b, 0.30); fromCenter(b + 180, 0.30); }
                else if (a >= 270 && a <= 340 && (a - 270) % 10 == 0) { double b = (a - 270) * 1.5; for (int k = 0; k < 4; k++) for (int j = 0; j < 3; j++) later(j * 3, k * 90 + b, 0.34); }
                else if (a >= 350 && a <= 420 && (a - 350) % 35 == 0) hexagon(((a - 350) / 35) * 30);
                else if (a >= 430 && a <= 460 && (a - 430) % 15 == 0) { int n = 24; double off = (a - 430) / 15 * 5.0; for (int k = 0; k < n; k++) fromCenter(k * 15 + off, 0.36); }
            }
            /** 같은 각도로 j틱 늦게 (십자 줄 3발). */
            void later(int ticks, double deg, double speed) { f.later(Math.max(1, ticks), () -> fromCenter(deg, speed)); }
            /** 경기장 가장자리 육각형 테두리에서 안쪽으로 (한 변은 비움 = 피할 자리). */
            void hexagon(double rot) {
                double r = f.arena - 1; int gap = ReaperBoss.RNG.nextInt(6);
                for (int side = 0; side < 6; side++) {
                    if (side == gap) continue;
                    double a0 = Math.toRadians(rot + side * 60), a1 = Math.toRadians(rot + side * 60 + 60);
                    for (int j = 0; j < 6; j++) {
                        double u = (j + 0.5) / 6, ax = Math.cos(a0) * (1 - u) + Math.cos(a1) * u, az = Math.sin(a0) * (1 - u) + Math.sin(a1) * u;
                        double x = f.center.getX() + ax * r, z = f.center.getZ() + az * r;
                        double deg = Math.toDegrees(Math.atan2(-(f.center.getX() - x), f.center.getZ() - z));
                        fire(x, z, deg, 0.22);
                    }
                }
            }
            void close() { for (Shot s : shots) if (s.vis() != null) s.vis().close(); shots.clear(); }
        }

        /* ---------------------------------------------------------------- helpers */

        void face(Location to) { Location l = boss.getLocation(); l.setYaw(yawTo(l, to)); boss.setRotation(l.getYaw(), 0); }
        static float yawTo(Location from, Location to) { return yawOf(to.toVector().subtract(from.toVector())); }
        static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }
        static Vector dir(float yaw) { double a = Math.toRadians(yaw); return new Vector(-Math.sin(a), 0, Math.cos(a)); }
        static Location ground(Location at) { return ReaperBoss.ground(at); }

        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player farthest(List<Player> ps) { return ps.stream().max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player weakest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getHealth() / Math.max(1, ReaperBoss.Fight.maxOf(p)))).orElse(null); }

        boolean castAt(String skill, Location at) {
            try { return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {}); }
            catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 모델 모션 (CHA_ASH_a_&lt;이름&gt; = state{s=&lt;이름&gt;}). 기본형·광폭화 모델이 같은 이름을 씀. */
        void anim(String name) { castAt("CHA_ASH_a_" + name, boss.getLocation()); }

        void later(int ticks, Runnable r) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!closed && boss.isValid() && !boss.isDead()) r.run(); }, Math.max(1, ticks));
        }

        Entity prop(String mob, Location at) {
            try {
                var am = MythicBukkit.inst().getMobManager().spawnMob(mob, at);
                if (am == null) return null;
                Entity e = am.getEntity().getBukkitEntity();
                e.addScoreboardTag(TAG); e.setPersistent(false);
                return e;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("재의 그림자 장치 소환 실패: " + mob + " " + ex); return null; }
        }

        void reset() {
            if (phase >= P_TRANSFORM) {   // 모델이 바뀐 뒤면 새 보스로 다시
                Location c = center.clone();
                close(); boss.remove();
                try { MythicBukkit.inst().getMobManager().spawnMob("CA_Boss_ash_shadow", c); } catch (RuntimeException | LinkageError ignored) {}
                return;
            }
            if (barrage != null) { barrage.close(); barrage = null; }
            Fx.stop(auraFx); Fx.stop(stunFx); auraFx = stunFx = null;
            for (int i = 0; i < 3; i++) if (devices[i] != null) { devices[i].remove(); devices[i] = null; }
            phase = P_DEVICES; invulnerable = false; boss.setAI(true); boss.setInvisible(false); absorbed = 0; desperateQueued = false; last = "";
            boss.setHealth(maxHp()); boss.teleport(center); nextAt = t + 60; emptyFor = 0; respawnAt = -1;
            for (int i = 0; i < 3; i++) spawnDevice(i, false);
        }

        void close() {
            if (closed) return;
            closed = true;
            if (barrage != null) { barrage.close(); barrage = null; }
            Fx.stop(auraFx); Fx.stop(stunFx); Fx.stop(enrageFx);
            for (int i = 0; i < 3; i++) if (devices[i] != null) { devices[i].remove(); devices[i] = null; }
            FIGHTS.remove(boss.getUniqueId());
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== events */

    static final class Hooks implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);   // 장치는 직접 공격으로 안 부서짐
        }

        /** 보스 근접 공격(미몹 meleeattack)도 장치·광폭화 배율. */
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void melee(EntityDamageByEntityEvent e) {
            if (spellHit) return;
            Fight f = FIGHTS.get(e.getDamager().getUniqueId());
            if (f != null && e.getEntity() instanceof Player) e.setDamage(e.getDamage() * f.mult());
        }

        /** 탄막 중 죽은 사람이 있으면 다시 살아 들어와도 그로기로 안 넘어감. */
        @EventHandler(priority = EventPriority.MONITOR)
        public void playerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
            for (Fight f : FIGHTS.values()) if (f.phase == Fight.P_BARRAGE && f.boss.getWorld().equals(e.getEntity().getWorld())
                && ReaperBoss.flat2(e.getEntity().getLocation(), f.center) <= (f.arena + 6) * (f.arena + 6)) f.barrageDeath = true;
        }
        
        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f == null) return;
            Fx.effect(f.plugin, "claude:as_fate/death", e.getEntity().getLocation(), 0, 60);
            f.title("§8§l재의 그림자 처치", "§7재가 흩어지고, 촛불이 꺼졌다");
            f.say("그림자는... 빛이 사라지면... 다시...");
            f.close();
        }
    }
}
