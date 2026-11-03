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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chatime - 3인 고난도 보스 "시간의 망령" 진행자 (2026-11-03). 미몹 CA_Boss_time_wraith 의 ~onSpawn 에서
 * {@code chatime{a=start;arena=18} @self}. 소환 위치 = 경기장 중심, 소환 때 바라본 방향 = 경기장 정면(바닥 시계 12시).
 *
 * <ol>
 *   <li>1페이즈 짤패턴 7개 (초침 베기·초침 2연타·시침 찌르기·모래 낙하·진자·정지 구슬·앞당긴 미래).</li>
 *   <li>75% 기믹1 시간 감옥: 전투 시작부터 딜이 가장 많은 사람을 가둠. 감옥 체력 = 그 사람 누적 딜 × PRISON_RATIO.
 *       나머지 둘이 20초 안에 깨야 함 (못 깨면 갇힌 사람 사망). 보스는 무적으로 계속 패턴.</li>
 *   <li>50% 기믹2 바닥의 시계: 보스가 목표 시각을 알려줌, 시작 시각은 바늘마다 목표 ±1~5 (최소 한 바늘은 뒤로).
 *       시침 = 불·땅, 분침 = 물·바람, 초침 = 빛·어둠 원소 마법. 바늘 끝을 치면 앞으로 1칸, 꼬리를 치면 뒤로 1칸. 45초, 실패 = 전멸.</li>
 *   <li>25% 기믹3 멈춘 세계: 3라운드. 한 명이 방패 (피해 없음), 빛난 문자판 방향에서 시곗바늘 탄환이 부채꼴로 →
 *       시간 정지(이동 불가 2초, 탄이 공중에 멈춤) → 풀리면 날아감. 처음 부딪힌 사람이 방패면 막힘, 아니면 최대 체력 45%.</li>
 *   <li>0%: 가짜 클리어 연출 → 되감기 부활 → 2페이즈: 체력 50% 까지 차오름(그동안도 딜 들어감),
 *       모든 패턴이 2초 전 위치를 따라오는 유저 잔상에 판정 (예고 최소 2.5초). 진짜 0% = 처치.</li>
 * </ol>
 * 기믹 성공 = 그로기 8초. 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX 팩 claude:tw_* (예고 없음). 대사·안내 = 채팅 + 타이틀.
 * 서버 부담: 보스 하나당 Ticker 1개 (판단 5틱, 기믹·잔상만 매 틱/2틱), 예고마다 지연 작업 1개.
 */
final class TimeWraithBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_time_prop";
    static final String NAME = "§3[§b시간의 망령§3] §f";
    static final double PRISON_RATIO = 1.0;   // 감옥 체력 = 누적 딜 × 이 값
    static boolean spellHit, solo;

    TimeWraithBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 18), 10, 32));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /** /테스트 보스 시간의망령 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 시간의 망령. */
    static String test(Player p, String what, String arg) {
        if (what.equals("혼자")) {
            solo = arg.equals("켜기");
            return solo ? "혼자 시험 모드 켬: 즉사 대신 알림 · 감옥은 갇힌 본인도 칠 수 있음 · 방패는 매번 본인" : "혼자 시험 모드 끔";
        }
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 시간의 망령이 없어요 (/mm mobs spawn CA_Boss_time_wraith - 소환한 자리가 경기장 중심, 바라본 방향이 12시)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 기믹·연출 중이라 패턴을 못 써요";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "기믹" -> {
                int k = Math.clamp(Integer.parseInt(arg), 1, 3) - 1;
                if (f.gimmick != null || f.phase != Fight.P1) return "지금은 1페이즈가 아니거나 기믹 중이에요";
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * Fight.GATES[k])));
                f.startGimmick(k);
                return "기믹 " + (k + 1) + " 시작";
            }
            case "2페이즈" -> { Arrays.fill(f.gateDone, true); if (f.gimmick != null) { f.gimmick.cleanup(); f.gimmick = null; } f.boss.setHealth(1); f.startFake(); return "가짜 클리어 → 2페이즈"; }
            default -> { f.reset(); return "처음 상태로 (체력 가득, 1페이즈)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final int P1 = 1, P_GIM = 2, P_GROGGY = 3, P_FAKE = 4, P2 = 5;
        static final double[] GATES = {0.75, 0.50, 0.25};
        static final int GROGGY = 160, ECHO_LAG = 40;

        final MagicMythicPlugin plugin; final LivingEntity boss; final Location center; final double arena; final float front;
        int t, nextAt = 60, emptyFor, phase = P1, phaseAt, riseUntil;
        final boolean[] gateDone = new boolean[3];
        boolean invulnerable, closed;
        String last = "";
        Gimmick gimmick;
        UUID guardFx, stunFx, p2Fx; int p2FxAt;
        final Map<UUID, Double> dealt = new HashMap<>();
        final List<Entity> props = new ArrayList<>();
        /** 2페이즈 유저 잔상: 2틱마다 위치 기록, 40틱 전 위치에 잔상 모델. */
        final Map<UUID, ArrayDeque<Location>> trail = new HashMap<>();
        final Map<UUID, Entity> echoes = new HashMap<>();

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena; this.front = center.getYaw();
            Fx.effect(plugin, "claude:tw_fate/intro", ground(center), front, 60);
            anim("spawn");
            later(30, () -> {
                title("§3§l시간의 망령", "§7너희의 시간은 이미 내 것이다");
                say("째깍... 째깍... 너희의 시간은 이미 내 것이다.");
            });
        }

        void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void tell(Player pl, String line) { pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (gimmick != null && !gimmick.step()) { Gimmick g = gimmick; gimmick = null; g.cleanup(); }
            if (phase == P2 && t % 2 == 0) echoStep();
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            if (phase == P2 && t < riseUntil && boss.getHealth() < maxHp() * 0.5) boss.setHealth(Math.min(maxHp() * 0.5, boss.getHealth() + maxHp() * 0.0125));   // 10초에 50%
            if (phase == P2 && t >= p2FxAt) { Fx.stop(p2Fx); p2Fx = Fx.follow(boss, "claude:tw_fate/phase2", 1200, 0); p2FxAt = t + 1100; }
            if (phase == P_GROGGY && t - phaseAt >= GROGGY) endGroggy();
            if (free() && t >= nextAt) pattern(ps);
            return true;
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        /** 패턴을 쓸 수 있는 상태 (감옥·시계 기믹 중에도 보스는 무적으로 계속 공격). */
        boolean free() { return (phase == P1 || phase == P2 || (phase == P_GIM && gimmick != null && gimmick.patterns())) && !closed; }
        double mult() { return phase == P2 ? 1.2 : 1.0; }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = arena + 6;
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (ReaperBoss.flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < 24) out.add(pl);
            }
            return out;
        }

        /* ---------------------------------------------------------------- 패턴 고르기 */

        record Pat(String id, int weight) {}
        static final List<Pat> PATTERNS = List.of(new Pat("attack", 2), new Pat("second", 3), new Pat("thrust", 3), new Pat("sand", 3),
            new Pat("pendulum", 2), new Pat("stasis", 2), new Pat("foresight", 2));
        /** 감옥·시계 기믹 중엔 가벼운 원거리 패턴만, 더 느리게. */
        static final Set<String> LIGHT = Set.of("sand", "stasis", "foresight");

        void pattern(List<Player> ps) {
            boolean light = gimmick != null;
            List<Pat> pool = new ArrayList<>();
            for (Pat pt : PATTERNS) if (!pt.id().equals(last) && (!light || LIGHT.contains(pt.id()))) pool.add(pt);
            if (pool.isEmpty()) return;
            int sum = pool.stream().mapToInt(Pat::weight).sum(), roll = ReaperBoss.RNG.nextInt(sum);
            Pat pick = pool.get(0);
            for (Pat pt : pool) { roll -= pt.weight(); if (roll < 0) { pick = pt; break; } }
            run(pick.id(), ps);
            if (light) nextAt += 50;
        }

        /** 패턴 실행. 길이(틱)로 다음 패턴 시각. 대상: 가까운 적(기본·초침), 먼 적(시침·정지 구슬), 체력 낮은 적(모래), 전원(미래). */
        void run(String id, List<Player> ps) {
            List<Player> targets = targetable(ps);
            if (targets.isEmpty()) { nextAt = t + 20; return; }
            Player near = nearest(targets), far = farthest(targets), weak = weakest(targets);
            int len = switch (id) {
                case "attack" -> attack(near);
                case "second" -> second(near);
                case "thrust" -> thrust(far);
                case "sand" -> sand(weak);
                case "pendulum" -> pendulum();
                case "stasis" -> stasis(far);
                case "foresight" -> foresight(targets);
                default -> 20;
            };
            last = id;
            nextAt = t + len + (phase == P2 ? 10 : 14);
        }
        /** 감옥에 갇힌 사람은 대상에서 빠짐. */
        List<Player> targetable(List<Player> ps) {
            if (!(gimmick instanceof Prison pr) || pr.trapped == null) return ps;
            List<Player> out = new ArrayList<>(ps); out.remove(pr.trapped); return out;
        }
        /** 2페이즈 판정 기준점 = 그 사람의 잔상 (없으면 본인). */
        Location judgeAt(Player pl) { return phase == P2 ? echoOf(pl) : pl.getLocation(); }

        /** 기본 공격 초침 베기: 부채꼴 r 6 · 150°, 예고 0.4초. */
        int attack(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("attack");
            Location at = ground(boss.getLocation());
            strike(Shape.CONE, at, yawTo(at, target.getLocation()), 6, 0, 150, 8, 26, "light", "claude:tw_slash/swipe", 6, null);
            return 24;
        }
        /** 1 초침 2연타: 부채꼴 r 8 · 160°, 오른→왼 (0.45초) 왼→오른 (0.9초). 매 타 다시 조준. */
        int second(Player target) {
            if (target == null) return 10;
            anim("skill_second_slash");
            for (int k = 0; k < 2; k++) {
                String vfx = k == 0 ? "claude:tw_slash/second_a" : "claude:tw_slash/second_b";
                later(Math.max(1, 9 + 9 * k - 9), () -> {
                    Player tg = alive(target) ? target : nearest(targetable(players()));
                    if (tg == null) return;
                    Location at = ground(boss.getLocation()); face(tg.getLocation());
                    strike(Shape.CONE, at, yawTo(at, tg.getLocation()), 8, 0, 160, 9, 28, "light", vfx, 8, null);
                });
            }
            return 34;
        }
        /** 2 시침 찌르기: 직선 16 · 폭 4, 예고 0.6초 → 끝까지 돌진. */
        int thrust(Player target) {
            if (target == null) return 10;
            anim("skill_hour_thrust");
            Location from = ground(boss.getLocation()); float yaw = yawTo(from, target.getLocation()); face(target.getLocation());
            Fx.effect(plugin, "claude:tw_thrust/windup", from, yaw, 20);
            double len = clampLen(from, dir(yaw), 16);
            strike(Shape.LINE, from, yaw, len, 4, 0, 12, 32, "earth", "claude:tw_thrust/thrust", 16, s -> slide(from, dir(yaw), len));
            return 30;
        }
        /** 3 모래 낙하: 3번 (0.6/1.0/1.4초) 그때 체력 낮은 적 위치 원 r 4, 예고 0.5초. */
        int sand(Player target) {
            if (target == null) return 10;
            anim("skill_sand_fall");
            Fx.effect(plugin, "claude:tw_sand/cast", ground(boss.getLocation()), boss.getLocation().getYaw(), 40);
            for (int k = 0; k < 3; k++) {
                later(12 + 8 * k - 10 + 1, () -> {
                    Player tg = alive(target) ? target : weakest(targetable(players()));
                    if (tg == null) return;
                    Location at = ground(tg.getLocation());
                    int warn = warnTicks(10);
                    later(Math.max(1, warn - 5), () -> areaFx("claude:tw_sand/fall", at, 4, 0f, 4));
                    strike(Shape.CIRCLE, at, 0, 4, 0, 0, 10, 22, "earth", null, 4, null);
                });
            }
            return 40;
        }
        /** 4 진자 휘두르기: 안쪽 원 r 6 (0.6초) → 바깥 고리 r 6~12 (직선 8개로, 0.6초). */
        int pendulum() {
            anim("skill_pendulum");
            Location c = ground(boss.getLocation());
            strike(Shape.CIRCLE, c, 0, 6, 0, 0, 12, 26, "water", "claude:tw_pendulum/inner", 6, null);
            later(18, () -> {
                areaFxLater(warnTicks(12), "claude:tw_pendulum/outer", c, 12, 0);
                for (int s = 0; s < 8; s++) {
                    double a = Math.toRadians(s * 45 + 22.5);
                    Vector radial = new Vector(Math.cos(a), 0, Math.sin(a)), tan = new Vector(-Math.sin(a), 0, Math.cos(a));
                    Location start = c.clone().add(radial.clone().multiply(9)).subtract(tan.clone().multiply(5));
                    strike(Shape.LINE, start, yawOf(tan), 10, 6, 0, 12, 26, "water", null, 0, null);
                }
            });
            return 40;
        }
        /** 5 정지 구슬: 먼 적 발밑 원 r 3, 예고 0.7초 → 피해 + 둔화 1.5초. */
        int stasis(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("skill_stasis_orb");
            Location at = ground(target.getLocation());
            strike(Shape.CIRCLE, at, 0, 3, 0, 0, 14, 18, "water", "claude:tw_stasis/orb", 3, s -> {
                for (Player pl : inside(s)) if (plugin.status() != null) plugin.status().apply(boss, pl, Status.SLOW, 30, 0);
            });
            return 26;
        }
        /** 6 앞당긴 미래: 모든 대상의 0.8초 뒤 예상 위치에 원 r 4.5, 예고 0.6초. */
        int foresight(List<Player> ps) {
            anim("skill_foresight");
            for (Player pl : ps) {
                Vector v = pl.getVelocity().clone().setY(0);
                Location at = ground(pl.getLocation().add(v.multiply(16)));   // 속도(블록/틱) × 16틱
                if (ReaperBoss.flat2(at, center) > arena * arena) at = ground(pl.getLocation());
                Location fat = at;
                strike(Shape.CIRCLE, fat, 0, 4.5, 0, 0, 12, 26, "dark", "claude:tw_stasis/foresight", 4.5, null);
            }
            return 30;
        }

        /* ---------------------------------------------------------------- 판정 공통 */

        /** 2페이즈는 예고 최소 2.5초 (잔상이 2초 늦게 따라오므로 2초 일찍 빠져야 함). */
        int warnTicks(int ticks) { return phase == P2 ? Math.max(ticks, 50) : ticks; }

        /** 모드 예고 → 같은 모양 판정. 1페이즈 = 본인 위치, 2페이즈 = 잔상 위치. vfx 는 판정 순간 재생. */
        void strike(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks, double dmg, String element,
                    String vfx, double vfxSize, Consumer<Show> after) {
            if (!free()) return;
            int warn = warnTicks(ticks);
            Show s;
            try {
                s = new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw, (float) size, (float) width, (float) (angle <= 0 ? 60 : angle), warn, TelegraphProtocol.RED);
            } catch (IllegalArgumentException e) { return; }
            plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {
                if (closed || !free()) return;
                if (vfx != null) areaFx(vfx, at, size, yaw, vfxSize);
                double d = dmg * mult();
                spellHit = true;
                try {
                    for (Player pl : inside(show)) {
                        if (phase == P2) Fx.effect(plugin, "claude:tw_fate/echo_hit", judgeAt(pl), 0, 14);
                        plugin.targets().hurt(boss, pl, d, element);
                    }
                } finally { spellHit = false; }
                if (after != null) after.accept(show);
            });
        }
        List<Player> inside(Show s) {
            List<Player> out = new ArrayList<>();
            for (Player pl : targetable(players())) {
                Location j = judgeAt(pl);
                if (Math.abs(j.getY() - s.y()) < 3.5 && TelegraphProtocol.inside(s, j.getX(), j.getZ(), 0.3)) out.add(pl);
            }
            return out;
        }
        void areaFx(String id, Location at, double size, float yaw, double ref) {
            if (id == null) return;
            if (ref <= 0 || Math.abs(size - ref) < 0.05) Fx.effect(plugin, id, at, yaw, 60);
            else plugin.reactions().vfx().areaEffect(id, at, size, yaw, 60, 0xFFFFFF);
        }
        void areaFxLater(int ticks, String id, Location at, double size, float yaw) { later(ticks, () -> areaFx(id, at, size, yaw, size)); }

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

        /* ---------------------------------------------------------------- 체력 기준선 · 기믹 */

        void damaged(EntityDamageEvent e) {
            if (invulnerable) { e.setCancelled(true); return; }
            double after = boss.getHealth() - e.getFinalDamage();
            if (phase == P1 || phase == P_GROGGY) {
                for (int k = 0; k < 3; k++) {
                    if (gateDone[k]) continue;
                    double line = GATES[k] * maxHp();
                    if (after <= line) { e.setCancelled(true); boss.setHealth(Math.max(1, line)); if (phase == P_GROGGY) endGroggy(); startGimmick(k); }
                    return;   // 기믹은 순서대로
                }
                if (after <= 1) { e.setCancelled(true); boss.setHealth(1); if (phase == P_GROGGY) endGroggy(); startFake(); }
            }
        }

        void startGimmick(int k) {
            gateDone[k] = true; phase = P_GIM; phaseAt = t; invulnerable = true; nextAt = t + 60;
            switch (k) {
                case 0 -> gimmick = new Prison(this);
                case 1 -> gimmick = new FloorClock(this);
                default -> gimmick = new WorldStop(this);
            }
        }
        /** 기믹이 끝날 때 (성공 = 그로기, 실패 = 바로 1페이즈 재개). */
        void gimmickDone(boolean success) {
            invulnerable = false;
            boss.setAI(true); boss.setInvisible(false);
            if (success) startGroggy(); else { phase = P1; nextAt = t + 40; }
        }

        void startGroggy() {
            phase = P_GROGGY; phaseAt = t; nextAt = Integer.MAX_VALUE;
            boss.setAI(false);
            anim("groggy_start");
            Fx.effect(plugin, "claude:tw_fate/groggy", ground(boss.getLocation()), 0, 30);
            later(28, () -> { if (phase != P_GROGGY) return; anim("groggy_loop"); stunFx = Fx.follow(boss, "claude:tw_fate/stun", GROGGY, 0); });
            title("§6§l그로기", "§f8초 — 시간이 멈춘 지금 쏟아부어라");
            say("크윽... 톱니가... 어긋났다...");
        }
        void endGroggy() {
            Fx.stop(stunFx); stunFx = null;
            castAt("CHA_TW_a_groggy_end", boss.getLocation());
            boss.setAI(true);
            if (phase == P_GROGGY) { phase = P1; nextAt = t + 30; }
        }

        /* ---------------------------------------------------------------- 가짜 클리어 → 2페이즈 */

        void startFake() {
            if (phase == P_FAKE || phase == P2) return;
            phase = P_FAKE; phaseAt = t; invulnerable = true; nextAt = Integer.MAX_VALUE;
            boss.setAI(false);
            anim("fake_death");
            Fx.effect(plugin, "claude:tw_fate/fakeclear", ground(boss.getLocation()), boss.getLocation().getYaw(), 60);
            later(32, () -> { title("§a§l클리어!", "§f시간의 망령을 쓰러뜨렸다"); say("§7째깍... 째...깍......"); });
            later(90, () -> {
                title("§4§l...아니.", "§7너희의 승리도, 내 시간 안에서는 아직 일어나지 않았다");
                say("너희의 승리도... 내 시간 안에서는 아직 일어나지 않았다.");
                Fx.effect(plugin, "claude:tw_fate/rewind", ground(boss.getLocation()), boss.getLocation().getYaw(), 60);
                anim("revive");
            });
            later(90 + 52, () -> {
                anim("phase2_roar");
                title("§3§l되감긴 시간", "§f2초 전의 너 자신이 공격받는다 — 2초 일찍 피하라");
                say("과거의 너희를... 베어 주마.");
                phase = P2; phaseAt = t; invulnerable = false; riseUntil = t + 200; p2FxAt = t;
                AttributeInstance spd = boss.getAttribute(Attribute.MOVEMENT_SPEED);
                if (spd != null) spd.setBaseValue(spd.getBaseValue() * 1.2);
                boss.setAI(true); nextAt = t + 60;
                for (Player pl : players()) trail.computeIfAbsent(pl.getUniqueId(), u -> new ArrayDeque<>());
            });
        }

        /** 2틱마다: 위치 기록, 잔상을 40틱 전 위치로. */
        void echoStep() {
            List<Player> ps = players();
            Set<UUID> here = new HashSet<>();
            for (Player pl : ps) {
                UUID u = pl.getUniqueId(); here.add(u);
                ArrayDeque<Location> q = trail.computeIfAbsent(u, k -> new ArrayDeque<>());
                q.addLast(pl.getLocation().clone());
                while (q.size() > ECHO_LAG / 2 + 1) q.removeFirst();
                Entity e = echoes.get(u);
                if (e == null || !e.isValid()) { e = prop("CA_Time_player_echo", q.peekFirst(), "echo"); if (e != null) echoes.put(u, e); }
                if (e != null) e.teleport(q.peekFirst());
            }
            for (UUID u : List.copyOf(echoes.keySet())) if (!here.contains(u)) { Entity e = echoes.remove(u); if (e != null) e.remove(); trail.remove(u); }
        }
        Location echoOf(Player pl) {
            ArrayDeque<Location> q = trail.get(pl.getUniqueId());
            return q == null || q.isEmpty() ? pl.getLocation() : q.peekFirst();
        }

        /* ---------------------------------------------------------------- 기믹 공통 */

        abstract static class Gimmick {
            final Fight f; int age;
            Gimmick(Fight f) { this.f = f; }
            /** 매 틱. false = 끝. */
            boolean step() { age++; return true; }
            boolean patterns() { return false; }
            void propHit(Player by, Entity prop, String element) {}
            void propGone(Entity prop) {}
            void cleanup() {}
        }

        /* ---------------------------------------------------------------- 기믹1 시간 감옥 */

        static final class Prison extends Gimmick {
            static final int LIMIT = 400;
            Player trapped; Entity cage; Location spot; UUID holdFx; boolean over;
            Prison(Fight f) {
                super(f);
                List<Player> ps = f.players();
                trapped = ps.stream().max(Comparator.comparingDouble(p -> f.dealt.getOrDefault(p.getUniqueId(), 0d))).orElse(null);
                if (trapped == null) { finish(false, null); return; }
                double hp = Math.max(300, f.dealt.getOrDefault(trapped.getUniqueId(), 0d) * PRISON_RATIO);
                f.face(trapped.getLocation()); f.anim("skill_prison");
                Fx.effect(f.plugin, "claude:tw_prison/cast", ground(f.boss.getLocation()), f.boss.getLocation().getYaw(), 30);
                f.title("§6§l시간 감옥", "§f" + trapped.getName() + " — 가장 많은 시간을 빼앗은 자");
                f.say("가장 많은 시간을 빼앗아 간 자여... 네 시간을 돌려받겠다.");
                f.later(18, () -> {
                    if (over) return;
                    if (!alive(trapped)) { finish(false, null); return; }
                    spot = ground(trapped.getLocation()); spot.setYaw(f.front);
                    Fx.effect(f.plugin, "claude:tw_prison/trap", spot, 0, 40);
                    cage = f.prop("CA_Time_prison", spot, "prison");
                    if (cage instanceof LivingEntity le) {
                        AttributeInstance a = le.getAttribute(Attribute.MAX_HEALTH);
                        if (a != null) a.setBaseValue(hp);
                        le.setHealth(hp);
                    }
                    holdFx = cage == null ? null : Fx.follow(cage, "claude:tw_prison/hold", LIMIT + 20, 0);
                    if (cage != null) f.propAnim(cage, "CHA_TW_p_prison_drain");
                    ReaperBoss.Fight.title(trapped, "§6§l갇혔다", "§f모래가 다 떨어지기 전에 동료가 깨야 한다 (20초)");
                    f.say("§e" + trapped.getName() + "§f 이(가) 시간 감옥에 갇혔다! 감옥 체력 §6" + Math.round(hp) + "§f — 20초 안에 깨라.");
                });
            }
            @Override boolean patterns() { return true; }
            @Override boolean step() {
                super.step();
                if (over) return false;
                if (cage == null) return age < 40 || finish(false, "감옥이 만들어지지 않았다");
                if (!alive(trapped)) return finish(false, trapped.getName() + " 의 시간이 다했다");
                if (trapped.getLocation().distanceSquared(spot) > 1.0) { Location back = spot.clone(); back.setYaw(trapped.getLocation().getYaw()); back.setPitch(trapped.getLocation().getPitch()); trapped.teleport(back); }
                if (age % 100 == 0 && age < LIMIT + 18) { int left = (LIMIT + 18 - age) / 20; f.title("", "§6감옥 모래 §f" + left + "초"); }
                if (age >= LIMIT + 18) {
                    Fx.effect(f.plugin, "claude:tw_prison/fail", spot, 0, 40);
                    f.kill(trapped, "시간 감옥의 모래가 다 떨어졌다");
                    return finish(false, "모래가 다 떨어졌다");
                }
                return true;
            }
            @Override void propGone(Entity prop) {
                if (prop != cage) return;
                Fx.effect(f.plugin, "claude:tw_prison/break", spot, 0, 40);
                f.title("§a§l감옥 파괴", "§f" + trapped.getName() + " 을(를) 구해냈다");
                f.say("내 시간을... 빼앗다니...!");
                finish(true, null);
            }
            boolean finish(boolean ok, String why) {
                if (over) return false;
                over = true;
                if (!ok && why != null) { f.title("§c§l시간 감옥", "§f" + why); }
                f.later(1, () -> f.gimmickDone(ok));
                return false;
            }
            @Override void cleanup() {
                Fx.stop(holdFx);
                if (cage != null && cage.isValid()) { f.props.remove(cage); cage.remove(); }
            }
        }

        /* ---------------------------------------------------------------- 기믹2 바닥의 시계 */

        static final class FloorClock extends Gimmick {
            static final int LIMIT = 900;   // 45초
            static final String[] NAME = {"시침", "분침", "초침"};
            static final String[][] ELEMENTS = {{"fire", "earth"}, {"water", "wind"}, {"light", "dark"}};
            static final String[] ELEM_KO = {"불·땅", "물·바람", "빛·어둠"};
            static final String[] MODEL = {"CA_Time_hand_hour", "CA_Time_hand_minute", "CA_Time_hand_second"};
            static final double[] TIP = {3.6, 5.5, 6.4}, TAIL = {1.1, 1.45, 1.8};
            final int[] goal = new int[3], now = new int[3], hitAt = {-99, -99, -99, -99, -99, -99};
            final Entity[] hand = new Entity[3], tip = new Entity[3], tail = new Entity[3];
            Entity dial; TextDisplay board; boolean over, ready, ok; int endAt = -1;

            FloorClock(Fight f) {
                super(f);
                Random r = ReaperBoss.RNG;
                goal[0] = 1 + r.nextInt(12); goal[1] = r.nextInt(60); goal[2] = r.nextInt(60);
                int[] off = new int[3]; boolean back;
                do { back = false; for (int k = 0; k < 3; k++) { off[k] = (1 + r.nextInt(5)) * (r.nextBoolean() ? 1 : -1); if (off[k] > 0) back = true; } } while (!back);
                for (int k = 0; k < 3; k++) now[k] = wrap(k, goal[k] + off[k]);
                // 보스는 12시 방향 가장자리로 물러나 무적 (제자리), 가벼운 패턴만
                f.boss.setAI(false);
                Fx.effect(f.plugin, "claude:tw_fate/vanish", f.boss.getLocation(), 0, 20);
                f.later(14, () -> {
                    Location edge = ground(f.center.clone().add(dir(f.front).multiply(f.arena - 3))); edge.setYaw(f.front + 180);
                    f.boss.teleport(edge);
                    Fx.effect(f.plugin, "claude:tw_fate/appear", edge, edge.getYaw(), 20);
                    f.anim("skill_clock_summon");
                });
                f.later(30, () -> {
                    Location c = ground(f.center); c.setYaw(f.front); c.setPitch(0);
                    Fx.effect(f.plugin, "claude:tw_clock/summon", c, f.front, 40);
                    dial = f.prop("CA_Time_floor_clock", c, "dial");
                    for (int k = 0; k < 3; k++) {
                        hand[k] = f.prop(MODEL[k], c, "hand");
                        tip[k] = f.prop("CA_Time_hand_node", c, "node"); tail[k] = f.prop("CA_Time_hand_node", c, "node");
                    }
                    board = c.getWorld().spawn(c.clone().add(0, 4.2, 0), TextDisplay.class, td -> {
                        td.setBillboard(Display.Billboard.CENTER); td.setPersistent(false); td.addScoreboardTag(TAG);
                        td.setBackgroundColor(Color.fromARGB(96, 0, 0, 0)); td.setShadowed(true);
                    });
                    for (int k = 0; k < 3; k++) place(k);
                    showBoard();
                    ready = true;
                    String goalTxt = fmt(goal);
                    f.title("§b§l" + goalTxt, "§f이 시각에 문이 열린다 — 바늘을 맞춰라 (45초)");
                    f.say(goalTxt + " 에... 문이 열린다.");
                    f.say("§7시침 = §c불·땅§7  분침 = §9물·바람§7  초침 = §e빛·어둠§7 원소 마법. 바늘 끝을 치면 앞으로, 꼬리(추)를 치면 뒤로 한 칸.");
                });
            }
            static int wrap(int k, int v) { return k == 0 ? ((v - 1) % 12 + 12) % 12 + 1 : (v % 60 + 60) % 60; }
            static String fmt(int[] v) { return String.format(Locale.ROOT, "%d시 %02d분 %02d초", v[0], v[1], v[2]); }
            float yawOfHand(int k) { double a = k == 0 ? (now[0] % 12) * 30 : now[k] * 6; return (float) (f.front + a); }
            void place(int k) {
                float yaw = yawOfHand(k); Location c = ground(f.center);
                if (hand[k] != null) { Location l = c.clone(); l.setYaw(yaw); l.setPitch(0); hand[k].teleport(l); setYaw(hand[k], yaw); }
                if (tip[k] != null) tip[k].teleport(ground(c.clone().add(dir(yaw).multiply(TIP[k]))));
                if (tail[k] != null) tail[k].teleport(ground(c.clone().add(dir(yaw).multiply(-TAIL[k]))));
            }
            void showBoard() {
                if (board == null) return;
                StringBuilder sb = new StringBuilder("§f목표 §e").append(fmt(goal)).append("\n§7지금 ");
                for (int k = 0; k < 3; k++) sb.append(now[k] == goal[k] ? "§a" : "§b").append(k == 0 ? String.valueOf(now[0]) : String.format(Locale.ROOT, "%02d", now[k])).append(k == 0 ? "시 " : k == 1 ? "분 " : "초");
                board.text(ReaperBoss.LEG.deserialize(sb.toString()));
            }
            @Override boolean patterns() { return ready && endAt < 0; }
            @Override boolean step() {
                super.step();
                if (endAt >= 0) { if (age >= endAt) { f.gimmickDone(ok); return false; } return true; }
                if (!ready) { if (age > 200) { endAt = age; ok = false; } return true; }
                if (age % 200 == 0 && age < LIMIT) f.title("", "§b" + fmt(goal) + " §7· 남은 시간 §f" + (LIMIT - age) / 20 + "초");
                if (age >= LIMIT) {
                    over = true; ok = false; endAt = age + 20;
                    Fx.effect(f.plugin, "claude:tw_clock/fail", ground(f.center), f.front, 40);
                    f.title("§c§l시간 초과", "§f문은 영원히 닫혔다");
                    f.say("시간은... 기다려 주지 않는다.");
                    for (Player pl : f.players()) f.kill(pl, "바닥의 시계를 맞추지 못했다");
                    return true;
                }
                return true;
            }
            @Override void propHit(Player by, Entity prop, String element) {
                if (!ready || over) return;
                for (int k = 0; k < 3; k++) {
                    int dirn = prop == tip[k] ? 1 : prop == tail[k] ? -1 : 0;
                    if (dirn == 0) continue;
                    int slot = k * 2 + (dirn > 0 ? 0 : 1);
                    if (f.t - hitAt[slot] < 8) return;   // 한 마법이 여러 번 맞아도 한 칸
                    hitAt[slot] = f.t;
                    if (element == null || element.isEmpty()) { f.tell(by, "§7바늘은 원소 마법에만 반응한다 (" + NAME[k] + " = " + ELEM_KO[k] + ")"); return; }
                    if (!Arrays.asList(ELEMENTS[k]).contains(element)) {
                        Fx.effect(f.plugin, "claude:tw_clock/deny", prop.getLocation(), 0, 10);
                        f.tell(by, "§7" + NAME[k] + " 은(는) " + ELEM_KO[k] + " 원소에만 움직인다");
                        return;
                    }
                    now[k] = wrap(k, now[k] + dirn);
                    Fx.effect(f.plugin, "claude:tw_clock/tick", prop.getLocation(), 0, 10);
                    f.propAnim(hand[k], dirn > 0 ? "CHA_TW_p_hand_forward" : "CHA_TW_p_hand_back");
                    place(k); showBoard();
                    if (now[0] == goal[0] && now[1] == goal[1] && now[2] == goal[2]) success();
                    return;
                }
            }
            void success() {
                over = true; ok = true; endAt = age + 34;
                for (Entity h : hand) if (h != null) f.propAnim(h, "CHA_TW_p_hand_locked");
                if (dial != null) f.propAnim(dial, "CHA_TW_p_clock_success");
                Fx.effect(f.plugin, "claude:tw_clock/success", ground(f.center), f.front, 40);
                f.title("§a§l" + fmt(goal), "§f시계가 울린다 — 문이 열렸다");
                f.say("이 소리는... 안 돼, 멈춰라...!");
                f.later(30, () -> {
                    Location home = f.center.clone(); f.boss.teleport(home);
                    Fx.effect(f.plugin, "claude:tw_fate/appear", home, f.front, 20);
                });
            }
            @Override void cleanup() {
                for (Entity e : hand) remove(e); for (Entity e : tip) remove(e); for (Entity e : tail) remove(e); remove(dial);
                if (board != null) board.remove();
            }
            void remove(Entity e) { if (e != null) { f.props.remove(e); if (e.isValid()) e.remove(); } }
        }

        /* ---------------------------------------------------------------- 기믹3 멈춘 세계 */

        static final class WorldStop extends Gimmick {
            static final int RAYS = 31, FREEZE = 40; static final double SPREAD = 55, HIT_R = 0.75, SPEED = 1.6;
            final Set<UUID> used = new HashSet<>();
            int round = -1, phaseT, warn; boolean anyHit, over;
            Player shield; UUID shieldFx; final List<Double> origins = new ArrayList<>();
            final List<Shot> shots = new ArrayList<>(); final Map<UUID, Integer> hitsThisRound = new HashMap<>();
            record Shot(Location from, Vector dir, double stopAt, Player victim, boolean blocked, GameplayVisual vis, int[] k) {}

            WorldStop(Fight f) {
                super(f);
                f.boss.setAI(false);
                Fx.effect(f.plugin, "claude:tw_fate/vanish", f.boss.getLocation(), 0, 20);
                f.later(14, () -> { Location home = f.center.clone(); f.boss.teleport(home); Fx.effect(f.plugin, "claude:tw_fate/appear", home, f.front, 20); });
                f.title("§b§l멈춘 세계", "§f시간이 멈추면... 누가 너희를 지켜 줄까?");
                f.say("시간이 멈추면... 누가 너희를 지켜 줄까?");
                phaseT = -40;
            }
            @Override boolean step() {
                super.step();
                if (over) return false;
                phaseT++;
                if (round < 0) { if (phaseT >= 0) nextRound(); return true; }
                if (phaseT == warn) freeze();
                if (phaseT == warn + FREEZE) release();
                if (phaseT > warn + FREEZE) {
                    boolean moving = false;
                    for (Shot s : shots) moving |= fly(s);
                    if (!moving && phaseT > warn + FREEZE + 4) { endRound(); }
                }
                return !over;
            }
            void nextRound() {
                round++; phaseT = 0; shots.clear(); hitsThisRound.clear();
                List<Player> ps = f.players();
                if (ps.isEmpty()) { finish(); return; }
                List<Player> pool = new ArrayList<>(); for (Player p : ps) if (!used.contains(p.getUniqueId())) pool.add(p);
                if (pool.isEmpty() || solo) pool = ps;
                shield = pool.get(ReaperBoss.RNG.nextInt(pool.size())); used.add(shield.getUniqueId());
                origins.clear();
                int n = ReaperBoss.RNG.nextInt(12);
                origins.add(n * 30.0); if (round == 1) origins.add(n * 30.0 + 60);
                warn = round == 2 ? 30 : 50;
                f.anim("world_stop_start");
                Fx.effect(f.plugin, "claude:tw_worldstop/start", ground(f.boss.getLocation()), f.front, 30);
                int showShield = round == 2 ? 14 : 0;   // 3라운드는 방패 표식이 늦게 뜸
                f.later(Math.max(1, showShield), () -> {
                    if (over) return;
                    shieldFx = Fx.follow(shield, "claude:tw_worldstop/shield", warn + FREEZE + 40, 0);
                    String where = clockName(origins.get(0)) + (origins.size() > 1 ? " · " + clockName(origins.get(1)) : "");
                    for (Player pl : f.players()) ReaperBoss.Fight.title(pl, pl.equals(shield) ? "§6§l당신이 방패다" : "§f" + shield.getName() + " 뒤로!", "§b" + where + " 방향 §7— " + (round + 1) + "/3");
                    f.say("§e[방패] §f" + shield.getName() + " §7· 탄환 방향 §b" + where);
                });
                for (double a : origins) Fx.effect(f.plugin, "claude:tw_worldstop/origin", originAt(a), 0, warn + FREEZE);
            }
            String clockName(double a) { int h = (int) Math.round(((a % 360) + 360) % 360 / 30) % 12; return (h == 0 ? 12 : h) + "시"; }
            Location originAt(double a) { return ground(f.center.clone().add(dir((float) (f.front + a)).multiply(f.arena))); }

            void freeze() {
                f.anim("world_stop_loop");
                Fx.effect(f.plugin, "claude:tw_worldstop/freeze", ground(f.center), f.front, 34);
                List<Player> ps = f.players();
                for (Player pl : ps) if (f.plugin.status() != null) f.plugin.status().apply(f.boss, pl, Status.MOVEMENT_LOCK, FREEZE, 0);
                // 위치가 굳은 지금 각 탄의 처음 부딪히는 사람을 정함
                for (double a : origins) {
                    Location o = originAt(a); o.setY(Telegraphs.ground(f.center) + 1.2);
                    Vector toC = f.center.toVector().subtract(o.toVector()).setY(0).normalize();
                    for (int r = 0; r < RAYS; r++) {
                        double off = Math.toRadians(-SPREAD + 2 * SPREAD * r / (RAYS - 1));
                        Vector d = toC.clone().rotateAroundY(off);
                        double len = 2 * f.arena; Player first = null; double best = len;
                        for (Player pl : ps) {
                            Vector rel = pl.getLocation().toVector().subtract(o.toVector()).setY(0);
                            double along = rel.dot(d); if (along <= 0 || along >= best) continue;
                            double perp = rel.clone().subtract(d.clone().multiply(along)).length();
                            if (perp <= HIT_R) { best = along; first = pl; }
                        }
                        boolean blocked = first != null && (first.equals(shield));
                        Location start = o.clone().add(d.clone().multiply(2.5));
                        GameplayVisual vis = GameplayVisual.cast(null, "claude:tw_worldstop/bullet", start, d);
                        shots.add(new Shot(start, d, Math.max(0.5, best - 2.5), first, blocked, vis, new int[]{0}));
                    }
                }
            }
            void release() {
                f.anim("world_stop_release");
                Fx.effect(f.plugin, "claude:tw_worldstop/release", ground(f.center), f.front, 20);
                Fx.stop(shieldFx); shieldFx = Fx.follow(shield, "claude:tw_worldstop/shield", 40, 0);
            }
            /** 탄 한 칸 이동. 도착하면 막힘/피격 처리. 아직 날아가면 true. */
            boolean fly(Shot s) {
                if (s.k()[0] < 0) return false;
                s.k()[0]++;
                double dist = Math.min(s.stopAt(), s.k()[0] * SPEED);
                Location at = s.from().clone().add(s.dir().clone().multiply(dist));
                if (s.vis() != null) s.vis().move(at, s.dir());
                if (dist < s.stopAt()) return true;
                s.k()[0] = -1;
                if (s.vis() != null) s.vis().close();
                Player v = s.victim();
                if (v == null) return false;
                if (s.blocked()) { Fx.effect(f.plugin, "claude:tw_worldstop/block", ground(at), 0, 10); return false; }
                int n = hitsThisRound.merge(v.getUniqueId(), 1, Integer::sum);
                if (n > 2) return false;
                anyHit = true;
                Fx.effect(f.plugin, "claude:tw_worldstop/hit", ground(v.getLocation()), 0, 12);
                spellHit = true;
                try { f.plugin.targets().hurt(f.boss, v, ReaperBoss.Fight.maxOf(v) * 0.45, "light"); } finally { spellHit = false; }
                return false;
            }
            void endRound() {
                Fx.stop(shieldFx); shieldFx = null;
                for (Shot s : shots) if (s.vis() != null) s.vis().close();
                shots.clear();
                if (round >= 2) { finish(); return; }
                phaseT = Integer.MIN_VALUE / 2;   // 1.5초 쉬고 다음 라운드
                f.later(30, () -> { if (!over) nextRound(); });
            }
            void finish() {
                over = true;
                if (!anyHit) { f.title("§a§l시간이 다시 흐른다", "§f아무도 맞지 않았다"); f.say("어째서... 멈춘 시간 속에서 서로를...!"); }
                else { f.title("§7시간이 다시 흐른다", "§f누군가 탄에 맞았다 — 그로기 없음"); f.say("그래, 시간은 결국 너희를 찌른다."); }
                boolean ok = !anyHit;
                f.later(10, () -> f.gimmickDone(ok));
            }
            @Override void cleanup() { Fx.stop(shieldFx); for (Shot s : shots) if (s.vis() != null) s.vis().close(); shots.clear(); }
        }

        /* ---------------------------------------------------------------- helpers */

        void kill(Player pl, String why) {
            if (pl == null || !alive(pl)) return;
            if (solo) { tell(pl, "§c[시험] 즉사 판정 (" + why + ") — 시험 모드라 살려 둠"); return; }
            later(4, () -> { if (alive(pl)) pl.setHealth(0); });
        }
        static boolean alive(Player p) { return p != null && p.isValid() && !p.isDead(); }

        void face(Location to) { Location l = boss.getLocation(); boss.setRotation(yawTo(l, to), 0); }
        static float yawTo(Location from, Location to) { return yawOf(to.toVector().subtract(from.toVector())); }
        static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }
        static Vector dir(float yaw) { double a = Math.toRadians(yaw); return new Vector(-Math.sin(a), 0, Math.cos(a)); }
        static Location ground(Location at) { return ReaperBoss.ground(at); }
        static void setYaw(Entity e, float yaw) {
            e.setRotation(yaw, 0);
            if (e instanceof LivingEntity le) { try { le.setBodyYaw(yaw); } catch (RuntimeException | LinkageError ignored) {} }
        }

        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player farthest(List<Player> ps) { return ps.stream().max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player weakest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getHealth() / Math.max(1, ReaperBoss.Fight.maxOf(p)))).orElse(null); }

        boolean castAt(String skill, Location at) {
            try { return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {}); }
            catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 보스 모델 모션 (CHA_TW_a_&lt;이름&gt; = state{s=&lt;이름&gt;}). */
        void anim(String name) { castAt("CHA_TW_a_" + name, boss.getLocation()); }
        /** 소품 모델 모션 (소품이 시전자). */
        void propAnim(Entity e, String skill) {
            if (e == null || !e.isValid()) return;
            try { MythicBukkit.inst().getAPIHelper().castSkill(e, skill, e, e.getLocation(), List.<Entity>of(), List.of(e.getLocation()), 1f, d -> {}); }
            catch (RuntimeException | LinkageError ignored) {}
        }

        void later(int ticks, Runnable r) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!closed && boss.isValid() && !boss.isDead()) r.run(); }, Math.max(1, ticks));
        }

        Entity prop(String mob, Location at, String kind) {
            try {
                var am = MythicBukkit.inst().getMobManager().spawnMob(mob, at);
                if (am == null) return null;
                Entity e = am.getEntity().getBukkitEntity();
                e.addScoreboardTag(TAG); e.addScoreboardTag(TAG + ":" + kind); e.setPersistent(false);
                if (at.getYaw() != 0) setYaw(e, at.getYaw());
                props.add(e);
                return e;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("시간의 망령 소품 소환 실패: " + mob + " " + ex); return null; }
        }

        void clearEchoes() { for (Entity e : echoes.values()) if (e != null) e.remove(); echoes.clear(); trail.clear(); }

        void reset() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            Fx.stop(guardFx); Fx.stop(stunFx); Fx.stop(p2Fx); guardFx = stunFx = p2Fx = null;
            clearEchoes();
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear(); dealt.clear(); Arrays.fill(gateDone, false);
            if (phase == P2) { AttributeInstance spd = boss.getAttribute(Attribute.MOVEMENT_SPEED); if (spd != null) spd.setBaseValue(spd.getBaseValue() / 1.2); }
            phase = P1; invulnerable = false; boss.setAI(true); boss.setInvisible(false); last = "";
            boss.setHealth(maxHp()); boss.teleport(center); nextAt = t + 60; emptyFor = 0;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            Fx.stop(guardFx); Fx.stop(stunFx); Fx.stop(p2Fx);
            clearEchoes();
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear();
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
            if (e instanceof EntityDamageByEntityEvent be && FIGHTS.get(be.getDamager().getUniqueId()) instanceof Fight bf
                && bf.gimmick instanceof Fight.Prison pr && e.getEntity().equals(pr.trapped)) { e.setCancelled(true); return; }   // 갇힌 사람은 보스 근접도 안 맞음
            Set<String> tags = e.getEntity().getScoreboardTags();
            if (!tags.contains(TAG)) return;
            if (tags.contains(TAG + ":prison")) {   // 감옥만 맞음 (갇힌 본인 공격은 막음, 혼자 시험은 허용)
                Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
                if (by == null) { e.setCancelled(true); return; }
                for (Fight fight : FIGHTS.values()) if (fight.gimmick instanceof Fight.Prison pr && pr.cage == e.getEntity() && by.equals(pr.trapped) && !solo) e.setCancelled(true);
                return;
            }
            e.setCancelled(true);   // 나머지 소품은 안 맞음 (바늘 끝/꼬리 판정은 아래 magic / melee)
        }

        /** 바늘 끝/꼬리: 원소 마법만. 근접·원소 없는 피해는 안내만. */
        @EventHandler(priority = EventPriority.NORMAL)
        public void magic(MagicDamageEvent e) {
            if (!e.getTarget().getScoreboardTags().contains(TAG + ":node")) return;
            if (e.getCaster() instanceof Player by) nodeHit(by, e.getTarget(), e.getElement());
            e.setDamage(0);
        }
        @EventHandler(priority = EventPriority.LOW)
        public void melee(EntityDamageByEntityEvent e) {
            if (!e.getEntity().getScoreboardTags().contains(TAG + ":node") || spellHit) return;
            if (e.getDamager() instanceof Player by) nodeHit(by, e.getEntity(), "");   // 같은 타격의 마법 판정이 먼저 오면 8틱 쿨다운에 걸려 무시됨
        }
        static void nodeHit(Player by, Entity node, String element) {
            for (Fight f : FIGHTS.values()) if (f.gimmick instanceof Fight.FloorClock fc) fc.propHit(by, node, element);
        }

        /** 누적 딜 (감옥 대상 고르기). 취소 안 된 보스 피해만. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void dealt(EntityDamageByEntityEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f == null) return;
            Player by = attacker(e);
            if (by != null) f.dealt.merge(by.getUniqueId(), e.getFinalDamage(), Double::sum);
        }

        static Player attacker(EntityDamageByEntityEvent e) {
            return e.getDamager() instanceof Player pl ? pl : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f != null) {
                Fx.effect(f.plugin, "claude:tw_fate/death", e.getEntity().getLocation(), 0, 60);
                f.title("§3§l시간의 망령 처치", "§7멈춰 있던 시계가, 다시 흐르기 시작했다");
                f.say("째깍......");
                f.close();
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            e.getDrops().clear(); e.setDroppedExp(0);
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null) fight.gimmick.propGone(e.getEntity());
        }

        @EventHandler public void quit(PlayerQuitEvent e) {
            for (Fight f : FIGHTS.values()) { Entity echo = f.echoes.remove(e.getPlayer().getUniqueId()); if (echo != null) echo.remove(); f.trail.remove(e.getPlayer().getUniqueId()); }
        }
    }
}
