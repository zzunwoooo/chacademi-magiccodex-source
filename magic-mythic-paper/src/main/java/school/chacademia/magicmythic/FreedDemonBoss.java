package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
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
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chafreed - 1인 레이드 "해방된 악마" 진행자 (2026-11-09). 미몹 CA_Boss_freed_demon 의 ~onSpawn 에서
 * {@code chafreed{a=start;arena=14} @self}. 소환 위치 = 경기장(중앙 제단) 중심, 소환 때 바라본 방향 = 정면 (석상·구역 배치 기준).
 *
 * <p>규칙: 악마는 거짓말을 한다. 거짓말할 때 눈이 보라색(모델 eyes_lie 본 + claude:freeddemon_gimmick/lie)이 된다.
 * <ol>
 *   <li>70% 기믹1 여섯 개의 고해: 석상 6개(반경 10)에 문장. 진실을 말하는 석상은 하나. 20초 뒤 그 석상 발판 위가 아니면 사망.
 *       시작할 때 귓속말로 번호 하나를 알려 주는데 항상 거짓. 속삭이는 임프 2마리가 방해.</li>
 *   <li>40% 기믹2 지옥의 줄넘기: 붉은 사슬(낮게) = 점프, 검은 사슬(높게) = 웅크리기. 15바퀴, 감속 → 역회전 페이크. 외침은 보라 눈일 때 거짓.
 *       맞으면 최대 체력 50% + 낙인 1. 낙인 2 = 사망 (계약의 집행자 낙인과 같은 낙인).</li>
 *   <li>10% 기믹3 악마의 서명: 8구역에 표식이 새겨지고(무작위), 보스 위에 표식 5개가 순서대로. 하나는 뒤집힘(그 차례는 불발), 사이에 흐린 가짜 하나(순서에 없음).
 *       그 순서대로 같은 표식 구역이 예고 없이 폭발. 맞으면 사망. 중앙(반경 3)은 모든 폭발에 걸림.</li>
 *   <li>1% 최후의 속임수: 가짜 사망 + 가짜 토벌 타이틀 + 보상 상자(미믹). 바닥으로 도망치는 그림자를 10초 안에 잡아야 진짜 사망(재봉인).
 *       상자를 열면 그 사람 사망 + 보스 15% 로 부활. 그림자를 놓쳐도 15% 로 부활.</li>
 * </ol>
 * 규칙·기믹 설명 타이틀·안내는 띄우지 않는다 (사장님 지시 2026-11-09). 보스는 짧은 대사만, 실패했을 때만 그 사람에게 짧은 단서(회색 기울임).
 * 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX claude:freeddemon_* (예고 없음). 서버 부담: 보스당 Ticker 1개 (판단 5틱, 기믹만 매 틱).
 */
final class FreedDemonBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_freed_prop";
    static final String NAME = "§4[§c재의 군주 아스칼론§4] §f";
    static boolean spellHit, test;
    /** 구역 k 의 가운데 방향 = 정면 + SECTOR_SIGN × 45k. VFX sector0~7 은 +Z 에서 시계 방향으로 구워져 있음 — 서버에서 거울 반대로 보이면 -1 ↔ 1. */
    static int SECTOR_SIGN = -1;
    /** 사슬 모델은 Blockbench +X 로 뻗어 있음. 엔티티 yaw 에 더할 값 (서버에서 사슬이 판정과 어긋나면 조정). */
    static float CHAIN_YAW_OFFSET = -90f;

    FreedDemonBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 14), 10, 30));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    static final List<String> PATTERN_IDS = List.of("claw", "combo", "slam", "dual", "cross", "chain", "execution", "sulfur", "mirror",
        "ringdonut", "comet", "brand", "gust", "hands", "taunt", "frenzy", "fakesafe", "darkness");

    /** /테스트 보스 해방된악마 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 해방된 악마. */
    static String test(Player p, String what, String arg) {
        if (what.equals("시험")) {
            test = arg.equals("켜기");
            return test ? "시험 모드 켬: 즉사 대신 알림 (단서도 같이 보여 줌)" : "시험 모드 끔";
        }
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 해방된 악마가 없어요 (/mm mobs spawn CA_Boss_freed_demon - 소환한 자리가 경기장 중심, 바라본 방향이 정면)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 기믹·연출 중이라 패턴을 못 써요";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "기믹" -> {
                int k = Math.clamp(Integer.parseInt(arg), 1, 4) - 1;
                if (f.gimmick != null || f.phase != Fight.FIGHTING) return "지금은 기믹·연출 중이에요";
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * Fight.GATES[k])));
                f.startGimmick(k);
                return "기믹 " + (k + 1) + " 시작";
            }
            case "낙인" -> { int n = Math.clamp(Integer.parseInt(arg), 0, 1); f.stacks.put(p.getUniqueId(), n); f.stackFx(p); return "낙인 " + n; }
            default -> { f.reset(); return "처음 상태로 (체력 가득, 낙인 0)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final int FIGHTING = 1, P_GIM = 2, P_END = 3;
        /** 0 고해 · 1 줄넘기 · 2 서명 · 3 최후의 속임수 */
        static final double[] GATES = {0.70, 0.40, 0.10, 0.01};

        final MagicMythicPlugin plugin; final LivingEntity boss; final Location center; final double arena; final float front;
        int t, nextAt = 70, emptyFor, phase = FIGHTING, impAt = Integer.MAX_VALUE, grailAt = Integer.MAX_VALUE, decayAt = 500;
        final boolean[] gateDone = new boolean[4];
        boolean invulnerable, closed, enforcer55, enforcer25, lying;
        String last = "";
        Gimmick gimmick;
        final List<Entity> props = new ArrayList<>();
        Entity altar;

        /* 낙인 (집행자·줄넘기 공용) */
        final Map<UUID, Integer> stacks = new HashMap<>();
        final Map<UUID, UUID> stackFx = new HashMap<>();
        /* 상시 상태 */
        int tauntUntil, firewallUntil, sulfurUntil; boolean tauntReal; UUID firewallFx, sulfurFx;
        final List<Location> trail = new ArrayList<>(); final List<Integer> trailUntil = new ArrayList<>();
        final Set<UUID> touched = new HashSet<>();

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena; this.front = center.getYaw();
            Location a = ground(center); a.setYaw(front);
            altar = prop("CA_Freed_altar", a, "altar");
            boss.setAI(false);
            anim("spawn");
            Fx.effect(plugin, "claude:freeddemon_story/spawn", ground(center), front, 100);
            later(40, () -> say("……사슬이, 가볍구나."));
            later(80, () -> { boss.setAI(true); nextAt = t + 20; });
        }

        void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void tell(Player pl, String line) { pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }
        /** 실패한 사람에게만 주는 짧은 단서 (규칙 설명은 하지 않는다). */
        void hint(Player pl, String clue) { pl.sendMessage(ReaperBoss.LEG.deserialize("§8§o" + clue)); }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (gimmick != null && !gimmick.step()) { Gimmick g = gimmick; gimmick = null; g.cleanup(); }
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            hazards(ps);
            grails(ps);
            if (t >= decayAt) { decayAt = t + 500; if (!(gimmick instanceof JumpRope)) for (Player pl : ps) { int n = stacks.getOrDefault(pl.getUniqueId(), 0); if (n > 0) { stacks.put(pl.getUniqueId(), n - 1); stackFx(pl); } } }
            if (phase == FIGHTING) {
                double hp = boss.getHealth() / maxHp();
                if (!enforcer55 && hp <= 0.55) { enforcer55 = true; minion("CA_Freed_enforcer", "enforcer"); }
                if (!enforcer25 && hp <= 0.25) { enforcer25 = true; minion("CA_Freed_enforcer", "enforcer"); }
                if (t >= impAt) { impAt = t + 600; if (count("imp") < 2) minion("CA_Freed_imp", "imp"); }
                if (t >= grailAt) { grailAt = t + 700; if (count("grail") == 0) spawnGrails(); }
            }
            if (free() && t >= nextAt) pattern(ps);
            return true;
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        boolean free() { return !closed && phase == FIGHTING && tauntUntil <= t; }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = Math.max(arena + 6, DemonFight.REACH);   // [기능:보스대상100칸]
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (ReaperBoss.flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < DemonFight.REACH_Y) out.add(pl);
            }
            return out;
        }

        /* ---------------------------------------------------------------- 거짓말 (보라 눈) */

        void lie(boolean on, int ticks) {
            lying = on;
            castAt(on ? "CHA_FD_lie_on" : "CHA_FD_lie_off", boss.getLocation());
            if (on) {
                Fx.effect(plugin, "claude:freeddemon_gimmick/lie", boss.getLocation().add(0, 6.2, 0), 0, 30);
                if (ticks > 0) later(ticks, () -> lie(false, 0));
            }
        }

        /* ---------------------------------------------------------------- 패턴 */

        record Pat(String id, int weight) {}
        static final List<Pat> POOL = List.of(new Pat("claw", 4), new Pat("combo", 3), new Pat("slam", 3), new Pat("dual", 2), new Pat("cross", 3),
            new Pat("chain", 2), new Pat("execution", 2), new Pat("sulfur", 2), new Pat("mirror", 2), new Pat("ringdonut", 3), new Pat("comet", 3),
            new Pat("brand", 2), new Pat("gust", 2), new Pat("hands", 3), new Pat("taunt", 1), new Pat("frenzy", 2), new Pat("fakesafe", 1), new Pat("darkness", 1));

        double pace() {
            double hp = boss.getHealth() / maxHp();
            return hp > 0.70 ? 1.0 : hp > 0.40 ? 0.8 : hp > 0.10 ? 0.65 : 0.5;
        }

        void pattern(List<Player> ps) {
            List<Pat> pool = new ArrayList<>();
            boolean far = ps.stream().anyMatch(pl -> pl.getLocation().distanceSquared(boss.getLocation()) > 100);
            boolean low = ps.stream().anyMatch(pl -> pl.getHealth() < ReaperBoss.Fight.maxOf(pl) * 0.4);
            double hp = boss.getHealth() / maxHp();
            for (Pat pt : POOL) {
                if (pt.id().equals(last)) continue;
                if (pt.id().equals("execution") && !low) continue;
                if (pt.id().equals("chain") && !far) continue;
                if (pt.id().equals("fakesafe") && hp > 0.70) continue;
                pool.add(pt);
            }
            int sum = pool.stream().mapToInt(Pat::weight).sum(), roll = ReaperBoss.RNG.nextInt(Math.max(1, sum));
            Pat pick = pool.get(0);
            for (Pat pt : pool) { roll -= pt.weight(); if (roll < 0) { pick = pt; break; } }
            run(pick.id(), ps);
            // 40% 아래: 가끔 두 패턴을 이어서
            if (hp <= 0.40 && ReaperBoss.RNG.nextInt(3) == 0 && !Set.of("taunt", "fakesafe", "sulfur", "brand").contains(pick.id())) {
                String chainId = ReaperBoss.RNG.nextBoolean() ? "ringdonut" : "hands";
                if (!chainId.equals(pick.id())) { int gap = nextAt - t; later(Math.max(10, gap - 20), () -> { if (free()) run(chainId, players()); }); }
            }
        }

        void run(String id, List<Player> ps) {
            if (ps.isEmpty()) { nextAt = t + 20; return; }
            Player near = nearest(ps);
            int len = switch (id) {
                case "claw" -> claw(near, false);
                case "combo" -> claw(near, true);
                case "slam" -> slam(near);
                case "dual" -> dual(near);
                case "cross" -> cross(near);
                case "chain" -> soulChain(farthest(ps));
                case "execution" -> execution(weakest(ps));
                case "sulfur" -> sulfur(near);
                case "mirror" -> mirror(near);
                case "ringdonut" -> ringDonut();
                case "comet" -> comet(near);
                case "brand" -> brand(near);
                case "gust" -> gust(near);
                case "hands" -> hands(near);
                case "taunt" -> taunt();
                case "frenzy" -> frenzy(near);
                case "fakesafe" -> fakeSafe();
                case "darkness" -> darkness(near);
                default -> 20;
            };
            last = id;
            nextAt = t + (int) Math.round((len + 18) * pace());
        }

        /** 할퀴기: 앞 부채꼴 r 7 · 150°, 예고 0.45초. combo = 0.9초 뒤 지금 위치로 반대 손 2타 (r 7 · 160°, 예고 0.35초). */
        int claw(Player target, boolean combo) {
            if (target == null) return 10;
            face(target.getLocation()); anim(combo ? "claw_combo" : "attack");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 7, 0, 150, combo ? 8 : 9, 26, "claude:freeddemon_claws/claw", 1, null);
            if (combo) later(18, () -> {
                if (!alive(target)) return;
                Location a2 = ground(boss.getLocation()); float y2 = yawTo(a2, target.getLocation()) + (ReaperBoss.RNG.nextBoolean() ? 25 : -25);
                face(target.getLocation());
                strike(Shape.CONE, a2, y2, 7, 0, 160, 7, 26, "claude:freeddemon_claws/claw_rev", 1, null);
            });
            return combo ? 34 : 24;
        }
        /** 지연 내려찍기: 보스 앞 3.5 지점 원 r 3.5. 손을 든 채 멈칫 → 예고 1.1초 (보통보다 0.4초 늦게). */
        int slam(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("slam");
            Location b = ground(boss.getLocation()); float yaw = yawTo(b, target.getLocation());
            Location at = ground(b.clone().add(dir(yaw).multiply(3.5)));
            strike(Shape.CIRCLE, at, yaw, 3.5, 0, 0, 22, 32, "claude:freeddemon_slam/slam", 0, null);
            return 38;
        }
        /** 양손 위협: 앞·뒤 부채꼴 둘 다 예고 (r 7 · 120°, 0.7초). 손이 빛나는 쪽만 진짜. */
        int dual(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("cast");
            Location at = ground(boss.getLocation()); float yaw = boss.getLocation().getYaw();
            boolean frontReal = ReaperBoss.RNG.nextBoolean();
            float real = frontReal ? yaw : yaw + 180;
            Location glow = frontReal ? hand() : boss.getLocation().add(dir(yaw).multiply(-1.6)).add(0, 3.0, 0);
            Fx.effect(plugin, "claude:freeddemon_gimmick/rope_hit", glow, yaw, 20);
            later(6, () -> Fx.effect(plugin, "claude:freeddemon_gimmick/rope_hit", glow, yaw, 20));
            strike(Shape.CONE, at, yaw, 7, 0, 120, 14, frontReal ? 28 : 0, frontReal ? "claude:freeddemon_claws/claw" : null, 1, null);
            strike(Shape.CONE, at, yaw + 180, 7, 0, 120, 14, frontReal ? 0 : 28, null, 0, s -> { if (!frontReal) Fx.effect(plugin, "claude:freeddemon_claws/claw_rev", at, real, 40); });
            return 30;
        }
        /** 지옥불 십자: 십자(길이 24 · 폭 2.4) 예고 0.6초 → 발동 직전 45° 돌아간 X자로 0.4초 예고 → 분출. */
        int cross(Player target) {
            anim("cast");
            Location c = ground(boss.getLocation());
            float yaw = target == null ? front : yawTo(c, target.getLocation());
            for (float y : new float[]{yaw, yaw + 90}) strike(Shape.LINE, c.clone().add(dir(y).multiply(-12)), y, 24, 2.4, 0, 12, 0, null, 0, null);
            later(12, () -> {
                float y2 = yaw + 45;
                for (float y : new float[]{y2, y2 + 90}) strike(Shape.LINE, c.clone().add(dir(y).multiply(-12)), y, 24, 2.4, 0, 8, 30, null, 0, null, true);
                later(8, () -> Fx.effect(plugin, "claude:freeddemon_hellfire/cross", c, y2, 50));
            });
            return 40;
        }
        /** 영혼 사슬: 10칸보다 멀면 끌어당긴 뒤 앞 부채꼴 r 6 · 140° (예고 0.5초). */
        int soulChain(Player target) {
            if (target == null) return claw(nearest(players()), false);
            face(target.getLocation()); anim("cast");
            Fx.Link l = Fx.link("claude:freeddemon_contract/soulchain", hand(), chest(target));
            final int[] k = {0};
            plugin.ticker().add(new Ticker.Tick() {
                @Override public boolean tick() {
                    if (closed || !alive(target) || ++k[0] > 16) { if (l != null) l.close(); return false; }
                    if (l != null) l.move(hand(), chest(target));
                    if (k[0] == 6) { Vector to = boss.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0); target.setVelocity(to.normalize().multiply(Math.min(2.6, to.length() * 0.22)).setY(0.35)); }
                    return true;
                }
                @Override public void stop() { if (l != null) l.close(); }
                @Override public String kind() { return "boss"; }
            });
            later(14, () -> {
                Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation()); face(target.getLocation()); anim("attack");
                strike(Shape.CONE, at, yaw, 6, 0, 140, 10, 26, "claude:freeddemon_claws/claw", 1, null);
            });
            return 40;
        }
        /** 처형: 체력 40% 아래인 사람 등 뒤로 순간이동 → 찌르기 3번 (직선 5 · 폭 1.6, 각 예고 0.3초). */
        int execution(Player target) {
            if (target == null) return 10;
            Location behind = ground(target.getLocation().clone().add(dir(target.getLocation().getYaw()).multiply(-2.2)));
            if (ReaperBoss.flat2(behind, center) > (arena - 1) * (arena - 1)) behind = ground(target.getLocation().clone().add(target.getLocation().toVector().subtract(center.toVector()).setY(0).normalize().multiply(-2.2)));
            Location from = behind;
            Fx.effect(plugin, "claude:freeddemon_tricks/mirror", boss.getLocation(), 0, 30);
            boss.teleport(from.clone()); face(target.getLocation()); anim("dash");
            Fx.effect(plugin, "claude:freeddemon_contract/execution", ground(target.getLocation()), yawTo(from, target.getLocation()), 40);
            for (int i = 0; i < 3; i++) later(4 + i * 6, () -> {
                if (!alive(target)) return;
                Location b = ground(boss.getLocation()); float y = yawTo(b, target.getLocation()); face(target.getLocation());
                strike(Shape.LINE, b, y, 5, 1.6, 0, 6, 16, null, 0, null, true);
            });
            return 34;
        }
        /** 유황 행진: 6초 동안 대상에게 걸어가며 불바다를 남김. 3.5초 동안 남는 불길 위 = 0.5초마다 5%. */
        int sulfur(Player target) {
            if (target == null) return 10;
            boss.setAI(true); anim("walk_fast");
            sulfurUntil = t + 120;
            sulfurFx = Fx.follow(boss, "claude:freeddemon_tricks/sulfur", 130, 0);
            return 110;
        }
        /** 거울 돌진: 분신 둘 + 본체가 대상에게 동시 돌진 (직선 16 · 폭 3, 예고 0.8초). 본체 선 30, 분신 선 8. */
        int mirror(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("dash");
            Location b = ground(boss.getLocation()); float yaw = yawTo(b, target.getLocation());
            List<Entity> clones = new ArrayList<>();
            for (int s = -1; s <= 1; s += 2) {
                Location at = ground(b.clone().add(dir(yaw + 90).multiply(4.5 * s))); at.setYaw(yawTo(at, target.getLocation()));
                Entity c = prop("CA_Freed_mirror", at, "mirror");
                if (c != null) { clones.add(c); propAnim(c, "CHA_FD_p_mirror_dash"); }
                double len = clampLen(at, dir(at.getYaw()), 16);
                strike(Shape.LINE, at, at.getYaw(), len, 3, 0, 16, 8, null, 0, sh -> { for (Entity e : clones) { Fx.effect(plugin, "claude:freeddemon_tricks/mirror", e.getLocation(), 0, 30); } });
            }
            double len = clampLen(b, dir(yaw), 16);
            strike(Shape.LINE, b, yaw, len, 3, 0, 16, 30, null, 0, sh -> {
                UUID h = Fx.follow(boss, "claude:freeddemon_slam/dash", 20, 0);
                Location end = ground(b.clone().add(dir(yaw).multiply(Math.max(0, len - 1)))); end.setYaw(yaw);
                boss.teleport(end);
                later(10, () -> Fx.stop(h));
                later(6, () -> { for (Entity e : clones) removeProp(e); });
            });
            return 36;
        }
        /** 원 · 도넛: 보스 중심 원 r 5 / 고리 r 5~12 를 무작위 순서로 이어서 (각 예고 0.8 · 0.6초). */
        int ringDonut() {
            anim("cast");
            boolean ringFirst = ReaperBoss.RNG.nextBoolean();
            if (ringFirst) { ring(16); later(26, () -> donut(12)); } else { donut(16); later(26, () -> ring(12)); }
            return 48;
        }
        void ring(int ticks) {
            Location c = ground(boss.getLocation());
            strike(Shape.CIRCLE, c, 0, 5, 0, 0, ticks, 28, "claude:freeddemon_hellfire/ring", 0, null, true);
        }
        void donut(int ticks) {
            Location c = ground(boss.getLocation());
            for (int i = 0; i < 12; i++) {   // 예고: 고리 위 원 12개 (모드 예고에 도넛 모양이 없어서), 판정은 실제 고리 r 5~12
                Location at = c.clone().add(dir(i * 30f).multiply(8.6));
                strike(Shape.CIRCLE, at, 0, 3.6, 0, 0, ticks, 0, null, 0, null, true);
            }
            later(ticks, () -> {
                Fx.effect(plugin, "claude:freeddemon_hellfire/donut", c, 0, 50);
                spellHit = true;
                try { for (Player pl : players()) { double d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), c)); if (d >= 4.7 && d <= 12.3) plugin.targets().hurt(boss, pl, 28, "fire"); } } finally { spellHit = false; }
            });
        }
        /** 지옥 혜성: 대상 위치에 3번 (원 r 2.5, 3번째 r 5), 예고 0.8초, 0.7초 간격. */
        int comet(Player target) {
            if (target == null) return 10;
            anim("cast");
            for (int k = 0; k < 3; k++) {
                final boolean big = k == 2;
                later(1 + 14 * k, () -> {
                    if (!alive(target)) return;
                    Location at = ground(target.getLocation());
                    Location sky = at.clone().add(-6 + ReaperBoss.RNG.nextDouble() * 12, 16, -6 + ReaperBoss.RNG.nextDouble() * 12);
                    later(2, () -> Fx.fall(plugin, "claude:freeddemon_comet/projectile", sky, at.clone().add(0, 0.4, 0), 14));
                    strike(Shape.CIRCLE, at, 0, big ? 5 : 2.5, 0, 0, 16, big ? 30 : 22, null, 0,
                        s -> Fx.effect(plugin, big ? "claude:freeddemon_comet/impact_big" : "claude:freeddemon_comet/impact", at, 0, 40), true);
                });
            }
            return 52;
        }
        /** 계약의 낙인: 붉은 낙인 = 3초 뒤 그 자리 폭발(벗어나야 함), 보라 낙인 = 2칸 넘게 움직이면 폭발. 대사는 보라 눈일 때 반대. */
        int brand(Player target) {
            if (target == null) return 10;
            anim("cast");
            boolean red = ReaperBoss.RNG.nextBoolean(), liar = ReaperBoss.RNG.nextBoolean();
            Location mark = ground(target.getLocation());
            Fx.follow(target, red ? "claude:freeddemon_contract/brand_red" : "claude:freeddemon_contract/brand_violet", 70, 0);
            boolean sayRun = red != liar;
            later(8, () -> { if (liar) lie(true, 50); say(sayRun ? "도망쳐라." : "움직이지 마라."); });
            later(66, () -> {
                if (!alive(target)) return;
                boolean moved = ReaperBoss.flat2(target.getLocation(), mark) > 4;
                boolean hit = red ? ReaperBoss.flat2(target.getLocation(), mark) <= 9 : moved;
                if (red) Fx.effect(plugin, "claude:freeddemon_hellfire/cell", mark, 0, 40);
                if (hit) { Fx.follow(target, "claude:freeddemon_contract/brand_burst", 30, 0); hurtPct(target, 0.45); }
            });
            return 60;
        }
        /** 날개 폭풍: 앞 부채꼴 r 10 · 110° 밀쳐내기 (예고 0.7초) + 4초 동안 외곽 불벽 (가장자리 1.5칸 = 0.5초마다 6%). */
        int gust(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("wing_gust");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 10, 0, 110, 14, 20, "claude:freeddemon_slam/gust", 0, s -> {
                for (Player pl : inside(s)) pl.setVelocity(pl.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(1.6).setY(0.45));
                firewallUntil = t + 80;
                Fx.stop(firewallFx);
                firewallFx = Fx.held("claude:freeddemon_slam/firewall", ground(center), front, 90);
            });
            return 34;
        }
        /** 지옥의 손: 1파 = 대상 주변 5곳 + 발밑 (원 r 1.8, 예고 0.7초), 2파 = 1파 사이(안전했던 곳) + 그때 발밑. */
        int hands(Player target) {
            if (target == null) return 10;
            anim("cast");
            Location c = ground(target.getLocation());
            float base = ReaperBoss.RNG.nextFloat() * 360;
            List<Location> w1 = new ArrayList<>(); w1.add(c);
            for (int i = 0; i < 5; i++) w1.add(ground(c.clone().add(dir(base + i * 72).multiply(3.2))));
            for (Location at : w1) strike(Shape.CIRCLE, at, 0, 1.8, 0, 0, 14, 24, "claude:freeddemon_hellfire/hand", 3, null, true);
            later(18, () -> {
                if (!alive(target)) return;
                List<Location> w2 = new ArrayList<>(); w2.add(ground(target.getLocation()));
                for (int i = 0; i < 5; i++) w2.add(ground(c.clone().add(dir(base + 36 + i * 72).multiply(3.2))));
                for (Location at : w2) strike(Shape.CIRCLE, at, 0, 1.8, 0, 0, 12, 24, "claude:freeddemon_hellfire/hand", 3, null, true);
            });
            return 44;
        }
        /** 도발: 3초 동안 멈춰 "때려 봐라". 붉은 눈 = 때리면 반격(앞 반원 r 5 즉시, 최대 체력 40%) / 보라 눈 = 거짓 도발, 그동안 1.5배 피해. */
        int taunt() {
            anim("taunt");
            boss.setAI(false);
            tauntReal = ReaperBoss.RNG.nextBoolean();
            tauntUntil = t + 60;
            UUID aura = Fx.follow(boss, "claude:freeddemon_tricks/taunt", 64, 0);
            if (!tauntReal) lie(true, 60);
            say("…때려 봐라.");
            later(60, () -> { Fx.stop(aura); castAt("CHA_FD_clear", boss.getLocation()); boss.setAI(true); });
            return 64;
        }
        void counter(Player by) {
            tauntUntil = 0;
            castAt("CHA_FD_clear", boss.getLocation()); anim("attack");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, by.getLocation());
            Fx.effect(plugin, "claude:freeddemon_tricks/counter", at, yaw, 40);
            strike(Shape.CONE, at, yaw, 5, 0, 180, 4, 0, null, 0, s -> { for (Player pl : inside(s)) hurtPct(pl, 0.40); }, true);
            boss.setAI(true);
            nextAt = t + 30;
        }
        /** 광란 5연격: 좌우로 꺾이는 부채꼴 4번 (r 6 · 120°, 각 예고 0.35초) → 보스 중심 원 r 5 (예고 0.6초). */
        int frenzy(Player target) {
            if (target == null) return 10;
            anim("frenzy");
            for (int i = 0; i < 4; i++) {
                final int k = i;
                later(1 + i * 9, () -> {
                    if (!alive(target)) return;
                    Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation()) + (k % 2 == 0 ? -25 : 25); face(target.getLocation());
                    strike(Shape.CONE, at, yaw, 6, 0, 120, 7, 14, k % 2 == 0 ? "claude:freeddemon_claws/claw" : "claude:freeddemon_claws/claw_rev", 1, null, true);
                });
            }
            later(38, () -> strike(Shape.CIRCLE, ground(boss.getLocation()), 0, 5, 0, 0, 12, 30, "claude:freeddemon_claws/frenzy", 0, null, true));
            return 56;
        }
        /** 가짜 안전지대: 경기장 전체 예고 2초 + 빈 자리 후보 둘(마법진). 0.6초 뒤 문양이 꺼지는 쪽이 진짜. 진짜 자리(r 1.8) 밖 = 최대 체력 60%. */
        int fakeSafe() {
            anim("cast");
            boss.setAI(false);
            Location c = ground(center);
            Location[] spots = new Location[2];
            float base = ReaperBoss.RNG.nextFloat() * 360;
            for (int i = 0; i < 2; i++) spots[i] = ground(c.clone().add(dir(base + i * 140 + ReaperBoss.RNG.nextInt(40)).multiply(5 + ReaperBoss.RNG.nextDouble() * (arena - 8))));
            int real = ReaperBoss.RNG.nextInt(2);
            for (int i = 0; i < 2; i++) Fx.effect(plugin, "claude:freeddemon_hellfire/cell", spots[i], 0, 30);
            for (int k = 1; k <= 3; k++) later(12 * k, () -> Fx.effect(plugin, "claude:freeddemon_hellfire/cell", spots[1 - real], 0, 30));   // 가짜 쪽만 계속 켜져 있음
            strike(Shape.CIRCLE, c, 0, arena, 0, 0, 40, 0, null, 0, s -> {
                Fx.effect(plugin, "claude:freeddemon_hellfire/ring", c, 0, 50);
                Fx.effect(plugin, "claude:freeddemon_hellfire/donut", c, 0, 50);
                for (int i = 0; i < 6; i++) Fx.effect(plugin, "claude:freeddemon_hellfire/cell", ground(c.clone().add(dir(i * 60f + 30).multiply(arena - 1.2))), 0, 40);
                for (Player pl : players()) if (ReaperBoss.flat2(pl.getLocation(), spots[real]) > 1.9 * 1.9) hurtPct(pl, 0.60);
                boss.setAI(true);
            }, true);
            return 56;
        }
        /** 어둠의 장막: 대상 실명 2.5초 + 그 자리 원 r 3 (예고 1.1초). 쿵 소리가 보스 쪽에서 난다. */
        int darkness(Player target) {
            if (target == null) return 10;
            anim("slam");
            Location at = ground(target.getLocation());
            Fx.effect(plugin, "claude:freeddemon_tricks/darkness", at, 0, 40);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, true));
            later(10, () -> boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_STEP, 2f, 0.6f));
            strike(Shape.CIRCLE, at, 0, 3, 0, 0, 22, 30, "claude:freeddemon_slam/slam", 0, null);
            return 36;
        }

        /* ---------------------------------------------------------------- 상시: 불벽 · 유황 불길 · 성배 */

        void hazards(List<Player> ps) {
            if (firewallUntil > t && t % 10 == 0) {
                for (Player pl : ps) if (ReaperBoss.flat2(pl.getLocation(), center) > (arena - 1.5) * (arena - 1.5)) {
                    hurtPct(pl, 0.06);
                    pl.setVelocity(center.toVector().subtract(pl.getLocation().toVector()).setY(0).normalize().multiply(0.6).setY(0.2));
                }
            } else if (firewallFx != null && firewallUntil <= t) { Fx.stop(firewallFx); firewallFx = null; }
            if (sulfurUntil > t) { trail.add(ground(boss.getLocation())); trailUntil.add(t + 70); }
            else if (sulfurFx != null) { Fx.stop(sulfurFx); sulfurFx = null; }
            for (int i = trail.size() - 1; i >= 0; i--) if (trailUntil.get(i) <= t) { trail.remove(i); trailUntil.remove(i); }
            if (!trail.isEmpty() && t % 10 == 0) for (Player pl : ps) for (Location p : trail) if (ReaperBoss.flat2(pl.getLocation(), p) < 1.5 * 1.5) { hurtPct(pl, 0.05); break; }
        }
        void spawnGrails() {
            float base = ReaperBoss.RNG.nextFloat() * 360;
            boolean swap = ReaperBoss.RNG.nextBoolean();
            for (int i = 0; i < 2; i++) {
                Location at = ground(center.clone().add(dir(base + i * 180).multiply(arena - 2.5)));
                Entity g = prop((i == 0) != swap ? "CA_Freed_true_grail" : "CA_Freed_false_grail", at, (i == 0) != swap ? "grail_true" : "grail_false");
                if (g != null) g.addScoreboardTag(TAG + ":grail");
            }
        }
        void grails(List<Player> ps) {
            for (Entity e : List.copyOf(props)) {
                if (!e.isValid() || !e.getScoreboardTags().contains(TAG + ":grail") || touched.contains(e.getUniqueId())) continue;
                boolean real = e.getScoreboardTags().contains(TAG + ":grail_true");
                for (Player pl : ps) {
                    if (pl.getLocation().distanceSquared(e.getLocation()) > 1.6 * 1.6) continue;
                    touched.add(e.getUniqueId());
                    Location at = ground(e.getLocation());
                    if (real) {
                        propAnim(e, "CHA_FD_p_grail_pour");
                        Fx.effect(plugin, "claude:freeddemon_minions/grail_heal", at, 0, 50);
                        double max = ReaperBoss.Fight.maxOf(pl); pl.setHealth(Math.min(max, pl.getHealth() + max * 0.30));
                        later(40, () -> removeProp(e));
                    } else {
                        propAnim(e, "CHA_FD_p_grail_burst");
                        later(57, () -> {
                            Fx.effect(plugin, "claude:freeddemon_minions/grail_burst", at.clone().add(0, 0.2, 0), 0, 40);
                            for (Player p2 : players()) if (p2.getLocation().distanceSquared(at) <= 3.2 * 3.2) hurtPct(p2, 0.40);
                            removeProp(e);
                        });
                    }
                    break;
                }
            }
        }

        /* ---------------------------------------------------------------- 낙인 (집행자 · 줄넘기) */

        /** @return true = 2중첩 (즉사 대상) */
        boolean addStack(Player pl) {
            int n = stacks.getOrDefault(pl.getUniqueId(), 0) + 1;
            stacks.put(pl.getUniqueId(), n);
            decayAt = t + 500;
            if (n >= 2) { stacks.put(pl.getUniqueId(), 0); stackFx(pl); Fx.follow(pl, "claude:freeddemon_contract/brand_burst", 30, 0); return true; }
            stackFx(pl);
            return false;
        }
        void stackFx(Player pl) {
            int n = stacks.getOrDefault(pl.getUniqueId(), 0);
            UUID h = stackFx.get(pl.getUniqueId());
            if (n <= 0) { if (h != null) { Fx.stop(h); stackFx.remove(pl.getUniqueId()); } return; }
            if (h == null) { UUID nh = Fx.follow(pl, "claude:freeddemon_contract/brand_stack", 20 * 600, 0); if (nh != null) stackFx.put(pl.getUniqueId(), nh); }
        }
        /** 집행자에게 맞음. */
        void enforcerHit(Player pl) {
            if (addStack(pl)) kill(pl, "(낙인은 겹쳐지면 타오른다… 먼저 찍는 자를 없앴어야 했다.)");
        }

        /* ---------------------------------------------------------------- 판정 공통 */

        void strike(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks, double dmg,
                    String vfx, int lead, Consumer<Show> after) { strike(shape, at, yaw, size, width, angle, ticks, dmg, vfx, lead, after, false); }
        void strike(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks, double dmg,
                    String vfx, int lead, Consumer<Show> after, boolean force) {
            if (!force && !free()) return;
            Show s;
            try {
                s = new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw, (float) size, (float) width, (float) (angle <= 0 ? 60 : angle), ticks, TelegraphProtocol.RED);
            } catch (IllegalArgumentException e) { return; }
            if (vfx != null && lead > 0) later(Math.max(1, ticks - lead), () -> Fx.effect(plugin, vfx, at, yaw, 50));
            plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {
                if (closed || phase == P_END) return;
                if (vfx != null && lead <= 0) Fx.effect(plugin, vfx, at, yaw, 50);
                if (dmg > 0) {
                    spellHit = true;
                    try { for (Player pl : inside(show)) plugin.targets().hurt(boss, pl, dmg, "fire"); } finally { spellHit = false; }
                }
                if (after != null) after.accept(show);
            });
        }
        List<Player> inside(Show s) {
            List<Player> out = new ArrayList<>();
            for (Player pl : players()) {
                Location j = pl.getLocation();
                if (Math.abs(j.getY() - s.y()) < 3.5 && TelegraphProtocol.inside(s, j.getX(), j.getZ(), 0.3)) out.add(pl);
            }
            return out;
        }
        void hurtPct(Player pl, double pct) {
            spellHit = true;
            try { plugin.targets().hurt(boss, pl, ReaperBoss.Fight.maxOf(pl) * pct, "fire"); } finally { spellHit = false; }
        }
        double clampLen(Location from, Vector dir, double want) {
            for (double s = 1; s <= want; s += 1) if (ReaperBoss.flat2(from.clone().add(dir.clone().multiply(s)), center) > (arena - 0.5) * (arena - 0.5)) return Math.max(4, s - 1);
            return want;
        }

        /* ---------------------------------------------------------------- 잡몹 */

        int count(String kind) { int n = 0; for (Entity e : props) if (e.isValid() && e.getScoreboardTags().contains(TAG + ":" + kind)) n++; return n; }
        Entity minion(String mob, String kind) {
            double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2;
            Location at = ground(boss.getLocation().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5));
            boolean shiny = ReaperBoss.RNG.nextInt(8) == 0 && !mob.contains("grail");
            Entity e = prop(shiny ? mob + "_shiny" : mob, at, kind);
            if (e != null) e.addScoreboardTag(TAG + ":minion");
            return e;
        }

        /* ---------------------------------------------------------------- 체력 기준선 · 기믹 */

        void damaged(EntityDamageEvent e) {
            if (phase == P_END || invulnerable) { e.setCancelled(true); if (gimmick != null) gimmick.bossHit(e); return; }
            Player by = e instanceof EntityDamageByEntityEvent be ? Hooks.attacker(be) : null;
            if (by == null) { List<Player> ps = players(); if (ps.size() == 1 && e instanceof EntityDamageByEntityEvent) by = ps.get(0); }
            if (tauntUntil > t && by != null) {
                if (tauntReal) { e.setCancelled(true); counter(by); return; }
                e.setDamage(e.getDamage() * 1.5);
            }
            double after = boss.getHealth() - e.getFinalDamage();
            for (int k = 0; k < GATES.length; k++) {
                if (gateDone[k]) continue;
                double line = GATES[k] * maxHp();
                if (after <= line) { e.setCancelled(true); boss.setHealth(Math.max(1, line)); startGimmick(k); }
                return;
            }
            if (after <= 1) { e.setCancelled(true); boss.setHealth(1); }   // 끝은 재봉인으로만
        }

        void startGimmick(int k) {
            gateDone[k] = true; phase = P_GIM; invulnerable = true; nextAt = t + 60; tauntUntil = 0;
            for (Entity e : List.copyOf(props)) if (e.getScoreboardTags().contains(TAG + ":minion") && !e.getScoreboardTags().contains(TAG + ":enforcer")) removeProp(e);
            switch (k) {
                case 0 -> gimmick = new Confession(this);
                case 1 -> gimmick = new JumpRope(this);
                case 2 -> gimmick = new Signature(this);
                default -> gimmick = new FakeDeath(this);
            }
        }
        void gimmickDone(int k, boolean success) {
            invulnerable = false;
            castAt("CHA_FD_clear", boss.getLocation());
            boss.setAI(true);
            if (k == 0) impAt = t + 200;
            if (k == 1) grailAt = t + 100;
            if (phase != P_END) { phase = FIGHTING; nextAt = t + 30; }
        }

        /** 기믹 시작 공통: 보스를 중앙 제단으로 (지옥불 속에서 내려섬), 제자리 무적. */
        void toCenter(Runnable then) {
            boss.setAI(false);
            Fx.effect(plugin, "claude:freeddemon_tricks/mirror", boss.getLocation(), 0, 30);
            later(8, () -> {
                Location home = center.clone(); boss.teleport(home);
                Fx.effect(plugin, "claude:freeddemon_hellfire/cell", ground(home), front, 40);
                later(14, then);
            });
        }

        /* ---------------------------------------------------------------- 기믹 공통 */

        abstract static class Gimmick {
            final Fight f; final int k; int age; boolean over;
            Gimmick(Fight f, int k) { this.f = f; this.k = k; }
            boolean step() { age++; return !over; }
            void bossHit(EntityDamageEvent e) {}
            void propHit(Entity prop, Player by) {}
            void cleanup() {}
            void finish(boolean ok) {
                if (over) return;
                over = true;
                f.later(1, () -> f.gimmickDone(k, ok));
            }
        }

        /* ---------------------------------------------------------------- 70% 기믹1 여섯 개의 고해 */

        /** 문장 하나: 글 + "진실 석상이 n번이라면 이 문장은 참인가". */
        record Stmt(String text, IntPredicate truth) {}

        static final class Confession extends Gimmick {
            static final int LIMIT = 400;
            final List<Entity> statues = new ArrayList<>(); final List<TextDisplay> holos = new ArrayList<>();
            final Location[] pads = new Location[6];
            int truth; final Map<UUID, Integer> whispered = new HashMap<>(); boolean judged;
            Confession(Fight f) {
                super(f, 0);
                f.toCenter(() -> {
                    f.anim("cast");
                    f.say("…말해 보아라. 누구를 믿을 테냐.");
                    List<Stmt> set = puzzle();
                    for (int i = 0; i < 6; i++) {
                        float yaw = f.front + 30 + i * 60;
                        Location at = ground(f.center.clone().add(dir(yaw).multiply(10))); at.setYaw(yaw + 180);
                        Entity s = f.prop("CA_Freed_statue", at, "statue");
                        if (s != null) statues.add(s);
                        pads[i] = ground(f.center.clone().add(dir(yaw).multiply(8)));
                        final int n = i + 1; final String txt = set.get(i).text();
                        TextDisplay td = at.getWorld().spawn(at.clone().add(0, 3.9, 0), TextDisplay.class, d -> {
                            d.setBillboard(Display.Billboard.CENTER); d.setPersistent(false); d.setShadowed(true); d.setLineWidth(160);
                            d.setBackgroundColor(Color.fromARGB(110, 10, 4, 8)); d.addScoreboardTag(TAG);
                            d.text(ReaperBoss.LEG.deserialize("§6§l" + ROMAN[n] + "\n§f" + txt));
                        });
                        holos.add(td);
                    }
                    for (int i = 0; i < 2; i++) f.minion("CA_Freed_imp", "imp");
                    f.later(40, () -> {
                        for (Player pl : f.players()) {
                            int lie; do lie = 1 + ReaperBoss.RNG.nextInt(6); while (lie == truth);
                            whispered.put(pl.getUniqueId(), lie);
                            Fx.follow(pl, "claude:freeddemon_gimmick/whisper", 40, 0);
                            f.tell(pl, "§d§o…진실은 " + lie + "번이다.");
                        }
                        f.lie(true, 60);
                    });
                });
            }
            static final String[] ROMAN = {"", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ"};
            /** 진실 석상 하나(truth)만 참인 문장 6개. 다른 번호를 진실로 가정하면 반드시 어긋나는 세트만 쓴다. */
            List<Stmt> puzzle() {
                for (int tries = 0; tries < 4000; tries++) {
                    truth = 1 + ReaperBoss.RNG.nextInt(6);
                    List<Stmt> set = new ArrayList<>();
                    for (int i = 1; i <= 6; i++) set.add(stmt(i, i == truth));
                    int ok = 0;
                    for (int cand = 1; cand <= 6; cand++) {
                        boolean consistent = true;
                        for (int i = 1; i <= 6 && consistent; i++) consistent = set.get(i - 1).truth().test(cand) == (i == cand);
                        if (consistent) ok++;
                    }
                    if (ok == 1 && set.get(truth - 1).truth().test(truth)) return set;
                }
                truth = 1;   // 안전망 (사실상 오지 않음)
                List<Stmt> set = new ArrayList<>();
                set.add(new Stmt("나만이 진실을 말한다.", c -> c == 1));
                for (int i = 2; i <= 6; i++) { final int k2 = i; set.add(new Stmt("진실은 " + k2 + "번에 있다.", c -> c == k2)); }
                return set;
            }
            Stmt stmt(int me, boolean need) {
                for (int tries = 0; tries < 60; tries++) {
                    int k2 = 1 + ReaperBoss.RNG.nextInt(6);
                    Stmt s = switch (ReaperBoss.RNG.nextInt(9)) {
                        case 0 -> new Stmt("진실은 " + k2 + "번에 있다.", c -> c == k2);
                        case 1 -> new Stmt("진실은 " + k2 + "번에 없다.", c -> c != k2);
                        case 2 -> new Stmt("진실은 짝수 번호에 있다.", c -> c % 2 == 0);
                        case 3 -> new Stmt("진실은 홀수 번호에 있다.", c -> c % 2 == 1);
                        case 4 -> new Stmt("진실은 " + k2 + "번보다 큰 번호에 있다.", c -> c > k2);
                        case 5 -> new Stmt("진실은 " + k2 + "번보다 작은 번호에 있다.", c -> c < k2);
                        case 6 -> k2 == me ? null : new Stmt(k2 + "번은 거짓을 말한다.", c -> c != k2);
                        case 7 -> k2 == me ? null : new Stmt(k2 + "번의 말은 진실이다.", c -> c == k2);
                        default -> new Stmt("진실은 내 바로 옆 번호에 있다.", c -> Math.abs(c - me) == 1);
                    };
                    if (s != null && s.truth().test(truth) == need) return s;
                }
                return need ? new Stmt("진실은 " + truth + "번에 있다.", c -> c == truth) : new Stmt("진실은 " + me + "번에 있다.", c -> c == me);
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (age % 20 == 0 && age > 60 && age < LIMIT) for (Player pl : f.players()) pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.6f, age > LIMIT - 100 ? 1.2f : 0.7f);
                if (age >= LIMIT && !judged) judge();
                return true;
            }
            void judge() {
                judged = true;
                Location pad = pads[truth - 1];
                for (int i = 0; i < 6; i++) {
                    if (i == truth - 1) { if (i < statues.size()) f.propAnim(statues.get(i), "CHA_FD_p_statue_reveal"); Fx.effect(f.plugin, "claude:freeddemon_gimmick/statue_truth", pads[i], 0, 60); }
                    else if (i < statues.size()) { f.propAnim(statues.get(i), "CHA_FD_p_statue_crumble"); Fx.effect(f.plugin, "claude:freeddemon_gimmick/statue_false", ground(statues.get(i).getLocation()), 0, 40); }
                }
                boolean anyFail = false;
                for (Player pl : f.players()) {
                    if (pad != null && ReaperBoss.flat2(pl.getLocation(), pad) <= 1.8 * 1.8) continue;
                    anyFail = true;
                    Integer w = whispered.get(pl.getUniqueId());
                    boolean onWhisper = w != null && ReaperBoss.flat2(pl.getLocation(), pads[w - 1]) <= 1.8 * 1.8;
                    Fx.effect(f.plugin, "claude:freeddemon_gimmick/doom", ground(pl.getLocation()), 0, 40);
                    f.kill(pl, onWhisper ? "(속삭임은 언제나 달콤했다. …그리고 언제나 틀렸다.)"
                                         : "(여섯 중 다섯은 거짓이었다. 남은 하나는… 다른 다섯과 어긋나지 않는 말이었다.)");
                }
                if (!anyFail) f.say("…흥.");
                final boolean passed = !anyFail;   // 람다 안에서는 바뀌는 변수를 못 씀 (컴파일 오류 수정)
                f.later(60, () -> finish(passed));
            }
            @Override void cleanup() {
                for (Entity s : statues) f.removeProp(s);
                for (TextDisplay td : holos) if (td.isValid()) td.remove();
            }
        }

        /* ---------------------------------------------------------------- 40% 기믹2 지옥의 줄넘기 */

        static final class JumpRope extends Gimmick {
            static final double TOTAL = 360 * 15;
            Entity red, black; double angle, swept, speed = 5.5; int dirSign = 1, slowUntil = -1, nextShout = 120, reversals;
            boolean started;
            final Map<UUID, Double> lastPass = new HashMap<>();
            JumpRope(Fight f) {
                super(f, 1);
                f.toCenter(() -> {
                    f.say("자, 뛰어 보아라.");
                    f.anim("chain_spin");
                    angle = f.front;
                    Location at = ground(f.center).add(0, 0.25, 0);
                    red = f.prop("CA_Freed_chain_red", at.clone(), "chain");
                    black = f.prop("CA_Freed_chain_black", at.clone().add(0, 1.6, 0), "chain");
                    started = true;
                });
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return true;
                // 속도: 점점 빨라지다가 정해진 바퀴에서 느려졌다 → 역회전
                double turns = swept / 360;
                double target = 5.5 + Math.min(5.5, turns * 0.45);
                if (slowUntil < 0 && reversals < 3 && (turns > 3.6 + reversals * 4.1)) { slowUntil = age + 18; }
                if (slowUntil >= 0) {
                    speed = Math.max(1.2, speed * 0.86);
                    if (age >= slowUntil) { dirSign = -dirSign; reversals++; slowUntil = -1; speed = target + 2.5; }
                } else speed += (target - speed) * 0.08;
                double prev = angle;
                angle += dirSign * speed; swept += speed;
                pose(red, angle); pose(black, angle + 180);
                for (Player pl : f.players()) {
                    double d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), f.center));
                    if (d < 1.6) continue;
                    double phi = yawTo(f.center, pl.getLocation());
                    if (crossed(prev, angle, phi)) pass(pl, false);
                    if (crossed(prev + 180, angle + 180, phi)) pass(pl, true);
                }
                if (age >= nextShout) { nextShout = age + 70 + ReaperBoss.RNG.nextInt(40); shout(); }
                if (swept >= TOTAL) { f.castAt("CHA_FD_clear", f.boss.getLocation()); finish(true); }
                return true;
            }
            void pose(Entity e, double yaw) { if (e != null && e.isValid()) setYaw(e, (float) (yaw + CHAIN_YAW_OFFSET)); }
            static boolean crossed(double a0, double a1, double phi) {
                double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
                double p = phi; while (p < lo) p += 360; while (p - 360 >= lo) p -= 360;
                return p <= hi;
            }
            /** 사슬이 그 사람 자리를 지나감. 붉은(낮은) = 공중이어야, 검은(높은) = 웅크려야. */
            void pass(Player pl, boolean high) {
                boolean safe = high ? (pl.isSneaking() || pl.isSwimming() || pl.isGliding()) : (!((Entity) pl).isOnGround() && pl.getLocation().getY() - ground(pl.getLocation()).getY() > 0.25);
                if (safe) return;
                Fx.follow(pl, "claude:freeddemon_gimmick/rope_hit", 20, 0);
                f.hurtPct(pl, 0.5);
                pl.setVelocity(new Vector(0, 0.35, 0));
                if (f.addStack(pl)) f.kill(pl, "(붉은 것은 낮게, 검은 것은 높게 지나갔다… 들어야 할 것은 외침이 아니었다.)");
            }
            /** 외침: 가장 가까운 사람에게 다음에 올 사슬을 알려 줌. 보라 눈이면 반대로. */
            void shout() {
                Player p = f.nearest(f.players());
                if (p == null) return;
                double phi = yawTo(f.center, p.getLocation());
                double dRed = ahead(angle, phi), dBlack = ahead(angle + 180, phi);
                boolean nextHigh = dBlack < dRed;
                boolean liar = ReaperBoss.RNG.nextInt(5) < 2;
                if (liar) f.lie(true, 40);
                boolean sayJump = !nextHigh != liar;
                f.say(sayJump ? "뛰어라!" : "엎드려라!");
            }
            double ahead(double a, double phi) { double d = (phi - a) * dirSign; d %= 360; if (d < 0) d += 360; return d; }
            @Override void cleanup() { f.removeProp(red); f.removeProp(black); }
        }

        /* ---------------------------------------------------------------- 10% 기믹3 악마의 서명 */

        static final class Signature extends Gimmick {
            final int[] sectorMark = new int[8];   // 0..7 → 표식 1..6
            final List<int[]> shown = new ArrayList<>();   // {표식, 종류 0 진짜 · 1 뒤집힘 · 2 가짜}
            int showAt = 70, idx, boomAt = -1, step; boolean ready;
            Signature(Fight f) {
                super(f, 2);
                f.toCenter(() -> {
                    f.anim("cast");
                    f.say("내 이름을… 기억할 수 있겠느냐.");
                    List<Integer> marks = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6)); Collections.shuffle(marks, ReaperBoss.RNG);
                    List<Integer> secs = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7)); Collections.shuffle(secs, ReaperBoss.RNG);
                    for (int i = 0; i < 6; i++) sectorMark[secs.get(i)] = marks.get(i);
                    sectorMark[secs.get(6)] = marks.get(ReaperBoss.RNG.nextInt(6)); sectorMark[secs.get(7)] = marks.get(ReaperBoss.RNG.nextInt(6));
                    List<Integer> seq = new ArrayList<>(marks.subList(0, 5)); Collections.shuffle(seq, ReaperBoss.RNG);
                    int fake = marks.get(5), rev = 1 + ReaperBoss.RNG.nextInt(3), fakeAt = 1 + ReaperBoss.RNG.nextInt(4);
                    for (int i = 0; i < 5; i++) { if (i == fakeAt) shown.add(new int[]{fake, 2}); shown.add(new int[]{seq.get(i), i == rev ? 1 : 0}); }
                    ready = true; showAt = age + 40;
                });
            }
            float sectorYaw(int k) { return f.front + SECTOR_SIGN * 45f * k; }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!ready) return true;
                if (age % 24 == 0) for (int k = 0; k < 8; k++)   // 구역마다 새겨진 표식 (계속 보임)
                    Fx.effect(f.plugin, "claude:freeddemon_signature2/mark" + sectorMark[k], ground(f.center.clone().add(dir(sectorYaw(k)).multiply(8))).add(0, 1.6, 0), 0, 30);
                if (idx < shown.size() && age >= showAt) {
                    int[] m = shown.get(idx++);
                    String sys = (m[1] == 1 ? "rev" : m[1] == 2 ? "fake" : "mark") + m[0];
                    Fx.effect(f.plugin, (sys.startsWith("fake") ? "claude:freeddemon_signature3/" : "claude:freeddemon_signature2/") + sys,   /* 보스VFX팩나눔: mark·rev → 2, fake → 3 */ f.boss.getLocation().add(0, 8.5, 0), 0, 30);
                    showAt = age + 28;
                    if (idx == shown.size()) boomAt = age + 50;
                }
                if (boomAt >= 0 && age >= boomAt) {
                    while (step < shown.size() && shown.get(step)[1] == 2) step++;   // 가짜는 순서에 없음
                    if (step >= shown.size()) { finish(true); return true; }
                    int[] m = shown.get(step++);
                    boolean fizzle = m[1] == 1;
                    for (int k = 0; k < 8; k++) if (sectorMark[k] == m[0])
                        Fx.effect(f.plugin, "claude:freeddemon_signature/" + (fizzle ? "fizzle" : "sector") + k, ground(f.center), f.front, 50);
                    if (!fizzle) for (Player pl : f.players()) if (inSectorOf(pl, m[0]))
                        f.kill(pl, "(보여 준 순서 그대로였다. …거꾸로 선 표식의 차례에는 아무 일도 없었다.)");
                    boomAt = age + 18;
                }
                return true;
            }
            boolean inSectorOf(Player pl, int mark) {
                double d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), f.center));
                if (d < 3) return true;   // 중앙은 모든 폭발에 걸림
                if (d > f.arena + 1) return false;
                float phi = yawTo(f.center, pl.getLocation());
                for (int k = 0; k < 8; k++) {
                    if (sectorMark[k] != mark) continue;
                    double diff = Math.abs(((phi - sectorYaw(k)) % 360 + 540) % 360 - 180);
                    if (diff <= 22.5) return true;
                }
                return false;
            }
        }

        /* ---------------------------------------------------------------- 1% 최후의 속임수 (가짜 사망) */

        static final class FakeDeath extends Gimmick {
            static final int LIMIT = 200;
            Entity mimic, shadow; int shadowAt = -1; boolean resolved; UUID shadowFx;
            FakeDeath(Fight f) {
                super(f, 3);
                f.boss.setAI(false);
                f.anim("fake_death");
                Fx.effect(f.plugin, "claude:freeddemon_story/fake_death", ground(f.boss.getLocation()), f.boss.getLocation().getYaw(), 80);
                f.later(48, () -> {
                    Location c = ground(f.boss.getLocation().clone().add(dir(f.boss.getLocation().getYaw()).multiply(4))); c.setYaw(f.boss.getLocation().getYaw() + 180);
                    mimic = f.prop("CA_Freed_mimic", c, "mimic");
                });
                f.later(52, () -> {
                    Location at = ground(f.boss.getLocation().clone().add(dir(f.boss.getLocation().getYaw()).multiply(2.5)));
                    shadow = f.prop("CA_Freed_shadow", at, "shadow");
                    if (shadow != null) { shadow.addScoreboardTag(TAG + ":minion"); shadowFx = Fx.follow(shadow, "claude:freeddemon_story/shadow", LIMIT + 40, 0); }
                    shadowAt = age;
                });
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (resolved) return true;
                if (mimic != null && mimic.isValid() && age % 2 == 0) for (Player pl : f.players())
                    if (pl.getLocation().distanceSquared(mimic.getLocation()) <= 1.6 * 1.6) { bite(pl); return true; }
                if (shadowAt >= 0 && shadow != null) {
                    if (!shadow.isValid() || shadow.isDead()) { seal(); return true; }
                    flee();
                    if (age - shadowAt >= LIMIT) { escape(); return true; }
                }
                return true;
            }
            /** 그림자는 가장 가까운 사람 반대쪽으로, 경기장 가장자리를 따라 도망. */
            void flee() {
                Player p = f.nearest(f.players());
                Location s = shadow.getLocation();
                Vector away = p == null ? dir(f.front) : s.toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) away = dir(f.front);
                away.normalize();
                Vector out = s.toVector().subtract(f.center.toVector()).setY(0);
                if (out.length() > f.arena - 2) away.add(new Vector(-out.getZ(), 0, out.getX()).normalize().multiply(1.2)).add(out.normalize().multiply(-0.8));
                shadow.setVelocity(away.normalize().multiply(0.34).setY(shadow.getVelocity().getY()));
                setYaw(shadow, yawOf(away));
            }
            void bite(Player pl) {
                resolved = true;
                f.propAnim(mimic, "CHA_FD_p_mimic_bite");
                Fx.effect(f.plugin, "claude:freeddemon_story/mimic_bite", ground(mimic.getLocation()), mimic.getLocation().getYaw(), 40);
                f.kill(pl, "(…상자는 숨을 쉬고 있었다.)");
                f.later(20, this::revive);
            }
            void escape() {
                resolved = true;
                for (Player pl : f.players()) f.hint(pl, "(쓰러진 것은 껍데기뿐이었다. …무언가 바닥을 타고 빠져나갔다.)");
                f.removeProp(shadow);
                revive();
            }
            void revive() {
                f.removeProp(mimic); f.removeProp(shadow); Fx.stop(shadowFx);
                f.anim("spawn");
                Fx.effect(f.plugin, "claude:freeddemon_story/spawn", ground(f.boss.getLocation()), f.front, 80);
                f.later(30, () -> f.say("크크… 속았구나."));
                f.later(60, () -> {
                    f.boss.setHealth(Math.max(1, f.maxHp() * 0.15));
                    f.gateDone[3] = false;
                    finish(false);
                });
            }
            /** 진짜 사망: 다시 봉인됨. */
            void seal() {
                resolved = true;
                f.removeProp(mimic); Fx.stop(shadowFx);
                f.phase = P_END;
                f.anim("death");
                Fx.effect(f.plugin, "claude:freeddemon_story/seal", ground(f.center), f.front, 100);
                f.later(84, () -> {
                    f.invulnerable = false;
                    spellHit = true;
                    try { f.boss.setHealth(0); } finally { spellHit = false; }
                });
            }
            @Override void cleanup() { f.removeProp(mimic); f.removeProp(shadow); Fx.stop(shadowFx); }
        }

        /* ---------------------------------------------------------------- helpers */

        void kill(Player pl, String clue) {
            if (pl == null || !alive(pl)) return;
            if (test) { tell(pl, "§c[시험] 즉사 판정 — 시험 모드라 살려 둠"); hint(pl, clue); return; }
            hint(pl, clue);
            later(2, () -> { if (alive(pl)) { spellHit = true; try { pl.setHealth(0); } finally { spellHit = false; } } });
        }
        static boolean alive(Player p) { return p != null && p.isValid() && !p.isDead(); }

        void face(Location to) { Location l = boss.getLocation(); boss.setRotation(yawTo(l, to), 0); }
        static float yawTo(Location from, Location to) { return yawOf(to.toVector().subtract(from.toVector())); }
        static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }
        static Vector dir(float yaw) { double a = Math.toRadians(yaw); return new Vector(-Math.sin(a), 0, Math.cos(a)); }
        static Vector dir(double yaw) { return dir((float) yaw); }
        static Location ground(Location at) { return ReaperBoss.ground(at); }
        static Location chest(LivingEntity e) { return e.getLocation().add(0, e.getHeight() * 0.6, 0); }
        /** 보스 오른손 근처 (발밑 + 앞 2.4 + 오른쪽 1.6 + 높이 3.2). 보스 키 약 6. */
        Location hand() {
            Location l = boss.getLocation(); float yaw = l.getYaw();
            return l.clone().add(dir(yaw).multiply(2.4)).add(dir(yaw + 90).multiply(1.6)).add(0, 3.2, 0);
        }
        static void setYaw(Entity e, float yaw) {
            e.setRotation(yaw, 0);
            if (e instanceof LivingEntity le) { try { le.setBodyYaw(yaw); } catch (RuntimeException | LinkageError ignored) {} }
        }
        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player farthest(List<Player> ps) { return ps.stream().filter(p -> p.getLocation().distanceSquared(boss.getLocation()) > 100).max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }
        Player weakest(List<Player> ps) { return ps.stream().filter(p -> p.getHealth() < ReaperBoss.Fight.maxOf(p) * 0.4).min(Comparator.comparingDouble(p -> p.getHealth() / ReaperBoss.Fight.maxOf(p))).orElse(null); }

        boolean castAt(String skill, Location at) {
            try { return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {}); }
            catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 보스 모델 모션: CHA_FD_a_&lt;이름&gt;. */
        void anim(String name) { castAt("CHA_FD_a_" + name, boss.getLocation()); }
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
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("해방된 악마 소품 소환 실패: " + mob + " " + ex); return null; }
        }
        void removeProp(Entity e) { if (e != null) { props.remove(e); if (e.isValid()) e.remove(); } }

        void clearState() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            Fx.stop(firewallFx); firewallFx = null; Fx.stop(sulfurFx); sulfurFx = null;
            trail.clear(); trailUntil.clear(); touched.clear();
            for (UUID h : stackFx.values()) Fx.stop(h);
            stackFx.clear(); stacks.clear();
            for (Entity e : List.copyOf(props)) if (e.isValid() && e != altar) e.remove();
            props.removeIf(e -> e != altar);
        }
        void reset() {
            clearState();
            Arrays.fill(gateDone, false);
            phase = FIGHTING; invulnerable = false; last = ""; enforcer55 = enforcer25 = false; tauntUntil = firewallUntil = sulfurUntil = 0;
            lie(false, 0);
            castAt("CHA_FD_clear", boss.getLocation());
            boss.setAI(true); boss.setHealth(maxHp()); boss.teleport(center);
            nextAt = t + 60; emptyFor = 0; impAt = grailAt = Integer.MAX_VALUE; decayAt = t + 500;
        }
        void close() {
            if (closed) return;
            closed = true;
            clearState();
            removeProp(altar);
            for (UUID u : List.copyOf(FIGHTS.keySet())) if (FIGHTS.get(u) == this) FIGHTS.remove(u);
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== events */

    static Fight fightOf(Entity e) {
        for (Fight f : FIGHTS.values()) if (f.props.contains(e)) return f;
        return null;
    }

    static final class Hooks implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            if (e.getEntity() instanceof Player pl) {   // 집행자에게 맞음 = 낙인 1
                if (!spellHit && e instanceof EntityDamageByEntityEvent be && be.getDamager().getScoreboardTags().contains(TAG + ":enforcer")) {
                    Fight fe = fightOf(be.getDamager()); if (fe != null) fe.enforcerHit(pl);
                }
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            if (e.getEntity().getScoreboardTags().contains(TAG + ":minion")) return;   // 잡몹·그림자는 정상 피해
            Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
            Fight fight = fightOf(e.getEntity());
            if (fight != null && fight.gimmick != null && by != null) fight.gimmick.propHit(e.getEntity(), by);
            e.setCancelled(true);
        }

        static Player attacker(EntityDamageByEntityEvent e) {
            return e.getDamager() instanceof Player pl ? pl : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) {
                FIGHTS.remove(e.getEntity().getUniqueId());
                f.close();
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            e.getDrops().clear(); e.setDroppedExp(0);
            Fight fight = fightOf(e.getEntity());
            if (fight != null) {
                if (e.getEntity().getScoreboardTags().contains(TAG + ":imp")) Fx.effect(fight.plugin, "claude:freeddemon_minions/imp_death", ground(e.getEntity().getLocation()), 0, 30);
                fight.props.remove(e.getEntity());
            }
        }
        static Location ground(Location l) { return ReaperBoss.ground(l); }
    }
}
