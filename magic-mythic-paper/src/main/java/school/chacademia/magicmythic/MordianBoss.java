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
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chamord - 3인 파티 기믹 보스 "모르디안의 원령" 진행자 (2026-11-03). 미몹 CA_Boss_mordian_wraith 의 ~onSpawn 에서
 * {@code chamord{a=start;arena=16} @self}. 소환 위치 = 경기장 중심, 소환 때 바라본 방향 = 경기장 정면.
 *
 * <ol>
 *   <li>짤패턴 11개 (할퀴기 연속·내려찍기·그림자 잠행·사슬 끌어당기기·검은 손아귀·저주 화살·개인 낙인·금지된 원·어둠의 비·사슬 회전·그림자 돌진).
 *       체력 30% 이하 = 할퀴기 3연타(광폭).</li>
 *   <li>85% ① 결박의 사슬: 2명 사슬 결박(8~14칸 유지), 남은 1명이 그림자 말뚝 3개를 20초 안에. 실패 = 묶인 둘 사망.</li>
 *   <li>70% ② 금지된 의식: 룬 5개를 그린 순서의 역순으로 1.5초씩 밟아 지움. 틀리면 남은 시간 -5초, 두 번 틀리면 전멸. 30초.</li>
 *   <li>55% ③ 각자의 그림자: 플레이어마다 자기 그림자. 자기 그림자는 못 때리고 동료 것만. 25초 뒤 남은 그림자의 주인 사망.</li>
 *   <li>40% ④ 제물의 마법진: 숫자(1~3)만큼 마법진 안에. 5라운드. 틀린 마법진 안 사람 사망, 빈 채 터지면 전원 최대 체력 50%.</li>
 *   <li>25% ⑤ 긁힌 기록 낭독: 명령 8번, 취소선 명령은 반대로. 첫 실수 = 망각 표식(큰 피해), 표식 상태에서 또 틀리면 사망.</li>
 *   <li>10% ⑥ 대마법사의 봉인: 봉인석 기둥 3개 중 2개로 가는 광선을 몸으로 막고 교대. 균열 3초 = 전멸. 30초 버티면
 *       재봉인 연출(sealed 5초) → 남은 10% 없이 처치.</li>
 * </ol>
 * 그로기 없음 (기믹 성공 = 바로 패턴 복귀). 기믹 중 보스 무적. 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX claude:mordian* (예고 없음).
 * 대사·안내 = 채팅 + 타이틀. 서버 부담: 보스 하나당 Ticker 1개 (판단 5틱, 기믹만 매 틱), 예고마다 지연 작업 1개.
 */
final class MordianBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_mordian_prop";
    static final String NAME = "§5[§d모르디안의 원령§5] §f";
    static final String EL = "dark";
    static boolean spellHit, solo;

    MordianBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 16), 10, 32));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    static final List<String> PATTERN_IDS = List.of("claw", "slam", "sink", "pull", "grasp", "bolt", "brand", "ring", "rain", "spin", "dash");

    /** /테스트 보스 모르디안 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 모르디안의 원령. */
    static String test(Player p, String what, String arg) {
        if (what.equals("혼자")) {
            solo = arg.equals("켜기");
            return solo ? "혼자 시험 모드 켬: 즉사 대신 알림 · 말뚝·그림자는 본인도 칠 수 있음 · 봉인 광선은 한 줄씩" : "혼자 시험 모드 끔";
        }
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 모르디안의 원령이 없어요 (/mm mobs spawn CA_Boss_mordian_wraith - 소환한 자리가 경기장 중심, 바라본 방향이 정면)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 기믹·연출 중이라 패턴을 못 써요";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "기믹" -> {
                int k = Math.clamp(Integer.parseInt(arg), 1, 6) - 1;
                if (f.gimmick != null || f.phase != Fight.P1) return "지금은 기믹·연출 중이에요";
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * Fight.GATES[k])));
                f.startGimmick(k);
                return "기믹 " + (k + 1) + " 시작";
            }
            case "재봉인" -> { if (f.gimmick != null) { f.gimmick.cleanup(); f.gimmick = null; } f.reseal(); return "재봉인 연출 → 처치"; }
            default -> { f.reset(); return "처음 상태로 (체력 가득)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final int P1 = 1, P_GIM = 2, P_END = 3;
        static final double[] GATES = {0.85, 0.70, 0.55, 0.40, 0.25, 0.10};
        static final String[] GIMMICK_NAME = {"결박의 사슬", "금지된 의식", "각자의 그림자", "제물의 마법진", "긁힌 기록 낭독", "대마법사의 봉인"};

        final MagicMythicPlugin plugin; final LivingEntity boss; final Location center; final double arena; final float front;
        int t, nextAt = 60, emptyFor, phase = P1;
        final boolean[] gateDone = new boolean[6];
        boolean invulnerable, closed;
        String last = "";
        Gimmick gimmick;
        final List<Entity> props = new ArrayList<>();

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena; this.front = center.getYaw();
            Fx.effect(plugin, "claude:mordianshadowgate/rise", ground(center), front, 40);
            anim("spawn");
            later(36, () -> {
                title("§5§l모르디안의 원령", "§7기록은... 나를 그렇게 기억하더군");
                say("기록은... 나를 그렇게 기억하더군.");
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
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            if (free() && t >= nextAt) pattern(ps);
            return true;
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        /** 패턴을 쓸 수 있는 상태 (① 결박 중에는 보스가 무적으로 가벼운 패턴만). */
        boolean free() { return !closed && (phase == P1 || (phase == P_GIM && gimmick != null && gimmick.patterns())); }
        boolean berserk() { return boss.getHealth() <= maxHp() * 0.30; }

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
        static final List<Pat> PATTERNS = List.of(new Pat("claw", 3), new Pat("slam", 3), new Pat("sink", 2), new Pat("pull", 2), new Pat("grasp", 2),
            new Pat("bolt", 2), new Pat("brand", 2), new Pat("ring", 2), new Pat("rain", 2), new Pat("spin", 2), new Pat("dash", 2));
        /** ① 결박 중엔 원거리 가벼운 패턴만, 느리게. */
        static final Set<String> LIGHT = Set.of("bolt", "rain", "grasp");

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

        /** 패턴 실행. 대상: 가까운 적(할퀴기·내려찍기·사슬 회전), 먼 적(잠행·화살·돌진), 체력 낮은 적(끌어당기기), 전원(손아귀·비), 무작위(낙인). */
        void run(String id, List<Player> ps) {
            if (ps.isEmpty()) { nextAt = t + 20; return; }
            Player near = nearest(ps), far = farthest(ps), weak = weakest(ps);
            int len = switch (id) {
                case "claw" -> claw(near);
                case "slam" -> slam(near);
                case "sink" -> sink(far);
                case "pull" -> pull(weak);
                case "grasp" -> grasp(ps);
                case "bolt" -> bolt(far, 5);
                case "brand" -> brand(ps.get(ReaperBoss.RNG.nextInt(ps.size())));
                case "ring" -> ring();
                case "rain" -> rain(ps);
                case "spin" -> spin();
                case "dash" -> dash(far);
                default -> 20;
            };
            last = id;
            nextAt = t + len + 14;
        }

        /** 1 할퀴기 연속: 부채꼴 r 7 · 150°, 오른손 → 왼손 (매 타 재조준). 체력 30% 이하 = 3연타·예고 0.35초. */
        int claw(Player target) {
            if (target == null) return 10;
            int hits = berserk() ? 3 : 2, warn = hits == 3 ? 7 : 9;
            for (int i = 0; i < hits; i++) {
                boolean right = i % 2 == 0;
                later(1 + i * 18, () -> {
                    Player tg = alive(target) && in(target) ? target : nearest(players());
                    if (tg == null || !free()) return;
                    face(tg.getLocation()); anim(right ? "attack" : "attack2");
                    Location at = ground(boss.getLocation()); float yaw = yawTo(at, tg.getLocation());
                    strike(Shape.CONE, at, yaw, 7, 0, 150, warn, 26, right ? "claude:mordianclaw/claw_right" : "claude:mordianclaw/claw_left", 2, null);
                });
            }
            return 18 * hits + 12;
        }
        /** 2 내려찍기: 전방 직선 9 · 폭 6 (0.95초) → 그 자리 충격파 원 r 8 (0.5초). */
        int slam(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("slam");
            Location from = ground(boss.getLocation()); float yaw = yawTo(from, target.getLocation());
            Location hit = ground(from.clone().add(dir(yaw).multiply(5)));
            strike(Shape.LINE, from, yaw, 9, 6, 0, 19, 30, null, 0, s -> {
                Fx.effect(plugin, "claude:mordianslam/impact", hit, yaw, 50);
                strike(Shape.CIRCLE, hit, 0, 8, 0, 0, 10, 16, null, 0, null);
            });
            return 48;
        }
        /** 3 그림자 잠행: 바닥으로 녹아 사라짐 → 먼 적 발밑 원 r 4.5 에서 솟구침 (예고 0.9초 = emerge 정점). */
        int sink(Player target) {
            if (target == null) return 10;
            anim("vanish");
            Fx.effect(plugin, "claude:mordianshadowgate/sink", ground(boss.getLocation()), boss.getLocation().getYaw(), 40);
            boss.setAI(false);
            later(24, () -> {
                Player tg = alive(target) && in(target) ? target : farthest(players());
                if (tg == null) { boss.setAI(true); anim("emerge"); return; }
                Location at = ground(tg.getLocation()); at.setYaw(yawTo(at, center));
                boss.teleport(at);
                Fx.effect(plugin, "claude:mordianshadowgate/rise", at, at.getYaw(), 40);
                anim("emerge");
                strike(Shape.CIRCLE, at, 0, 4.5, 0, 0, 18, 30, null, 0, s -> boss.setAI(true));
            });
            later(70, () -> boss.setAI(true));
            return 64;
        }
        /** 4 사슬 끌어당기기: 체력 낮은 적 방향 직선 (폭 2.5, 0.75초). 맞은 사람을 0.85초 끌어온 뒤 할퀴기 한 번. */
        int pull(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("tether");
            Location from = ground(boss.getLocation()); float yaw = yawTo(from, target.getLocation());
            double len = Math.min(18, Math.sqrt(ReaperBoss.flat2(from, target.getLocation())) + 2);
            strike(Shape.LINE, from, yaw, Math.max(4, len), 2.5, 0, 15, 14, null, 0, s -> {
                List<Player> caught = inside(s);
                if (caught.isEmpty()) {   // 빗나감: 사슬만 던졌다 흩어짐
                    Location end = from.clone().add(dir(yaw).multiply(len)).add(0, 1.1, 0);
                    Fx.Link miss = Fx.link("claude:mordianchainpull/throw", hand(), end);
                    later(6, () -> { if (miss != null) miss.close(); Fx.Link br = Fx.link("claude:mordianchainpull/break", hand(), end); later(4, () -> { if (br != null) br.close(); }); });
                    return;
                }
                Player v = caught.get(0);
                drag(v);
                later(22, () -> { if (free()) claw1(v); });
            });
            return 52;
        }
        /** 끌어당기기 연출 + 실제 이동 (17틱). */
        void drag(Player v) {
            Fx.Link th = Fx.link("claude:mordianchainpull/throw", hand(), chest(v));
            later(4, () -> {
                if (th != null) th.close();
                Fx.Link hold = Fx.link("claude:mordianchainpull/hold", hand(), chest(v));
                UUID wrap = Fx.follow(v, "claude:mordianchainpull/wrap", 30, 0);
                plugin.ticker().add(new Ticker.Tick() {
                    int k;
                    @Override public boolean tick() {
                        if (closed || !alive(v)) { end(); return false; }
                        k++;
                        Vector to = boss.getLocation().toVector().subtract(v.getLocation().toVector()).setY(0);
                        double d = to.length();
                        if (d > 3.2) v.setVelocity(to.normalize().multiply(Math.min(1.1, d / 6)).setY(0.08));
                        if (hold != null) hold.move(hand(), chest(v));
                        if (k >= 17 || d <= 3.2) { end(); return false; }
                        return true;
                    }
                    void end() {
                        if (hold != null) hold.close(); Fx.stop(wrap);
                        Fx.Link br = Fx.link("claude:mordianchainpull/break", hand(), chest(v));
                        Fx.follow(v, "claude:mordianchainpull/wrapBreak", 10, 0);
                        later(4, () -> { if (br != null) br.close(); });
                    }
                    @Override public void stop() { if (hold != null) hold.close(); Fx.stop(wrap); }
                    @Override public String kind() { return "boss"; }
                });
            });
        }
        /** 할퀴기 한 번 (끌어당긴 뒤 연계). */
        void claw1(Player tg) {
            face(tg.getLocation()); anim("attack");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, tg.getLocation());
            strike(Shape.CONE, at, yaw, 7, 0, 150, 8, 24, "claude:mordianclaw/claw_right", 2, null);
        }
        /** 5 검은 손아귀: 모두의 발밑 원 r 2, 0.8초 간격 3번 (예고 0.8초). 맞으면 둔화 1초. */
        int grasp(List<Player> ps) {
            anim("cast_short");
            for (int k = 0; k < 3; k++) {
                later(1 + 16 * k, () -> {
                    for (Player pl : players()) {
                        Location at = ground(pl.getLocation());
                        later(8, () -> Fx.effect(plugin, "claude:mordiangrasphand/grasp", at, 0, 24));   // 손이 닫히는 0.42초 = 판정
                        strike(Shape.CIRCLE, at, 0, 2, 0, 0, 16, 16, null, 0, s -> {
                            for (Player x : inside(s)) if (plugin.status() != null) plugin.status().apply(boss, x, Status.SLOW, 20, 0);
                        });
                    }
                });
            }
            return 58;
        }
        /** 6 저주 화살: 먼 적에게 n발 부채꼴 (±40°), 직선 20 · 폭 1.6, 예고 0.6초. 손에서 0.4초에 발사. */
        int bolt(Player target, int n) {
            if (target == null) return 10;
            face(target.getLocation()); anim("brand");
            Location from = ground(boss.getLocation()); float base = yawTo(from, target.getLocation());
            for (int i = 0; i < n; i++) {
                float yaw = n == 1 ? base : base - 40 + 80f * i / (n - 1);
                double len = clampLen(from, dir(yaw), 20);
                Location end = ground(from.clone().add(dir(yaw).multiply(len))).add(0, 1.2, 0);
                later(8, () -> Fx.fall(plugin, "claude:mordiancursebolt/projectile", hand(), end, 4));
                strike(Shape.LINE, from, yaw, len, 1.6, 0, 12, 20, null, 0, s -> {
                    List<Player> hit = inside(s);
                    Location at = hit.isEmpty() ? end : hit.get(0).getLocation().add(0, 1, 0);
                    Fx.effect(plugin, "claude:mordiancursebolt/impact", hit.isEmpty() ? ground(end) : ground(at), yaw, 50);
                });
            }
            return 30;
        }
        /** 7 개인 낙인 폭발: 한 명에게 낙인 3초 → 마지막 1초에 그 자리 원 r 4 예고 → 폭발. 동료와 떨어져야 함. */
        int brand(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("brand");
            later(8, () -> {
                if (!alive(target)) return;
                Fx.follow(target, "claude:mordianbrand/mark", 64, 0);
                tell(target, "§d낙인§f이 찍혔다 — 3초 뒤 터진다. 동료에게서 떨어져라!");
                ReaperBoss.Fight.title(target, "§5§l낙인", "§f3초 — 동료에게서 떨어져라");
            });
            later(48, () -> {
                if (!alive(target)) return;
                Location at = ground(target.getLocation());
                strike(Shape.CIRCLE, at, 0, 4, 0, 0, 20, 34, "claude:mordianbrand/burst", 0, null);
            });
            return 74;
        }
        /** 8 금지된 원: 포효 0.8초 → 도넛 r 5~13 (직선 8개로) 폭발 → 1.0초 뒤 안쪽 원 r 5 폭발. */
        int ring() {
            anim("roar");
            Location c = ground(boss.getLocation());
            float yaw = boss.getLocation().getYaw();
            later(16, () -> Fx.effect(plugin, "claude:mordianforbiddenring/forbidden_ring", c, yaw, 60));
            for (int s = 0; s < 8; s++) {
                double a = Math.toRadians(s * 45 + 22.5);
                Vector radial = new Vector(Math.cos(a), 0, Math.sin(a)), tan = new Vector(-Math.sin(a), 0, Math.cos(a));
                Location start = c.clone().add(radial.clone().multiply(9)).subtract(tan.clone().multiply(4));
                strike(Shape.LINE, start, yawOf(tan), 8, 8, 0, 20, 28, null, 0, null);
            }
            later(20, () -> strike(Shape.CIRCLE, c, 0, 5, 0, 0, 16, 32, null, 0, null));
            return 50;
        }
        /** 9 어둠의 비: 8발, 플레이어 근처·경기장 전역에 원 r 3, 0.2초 간격 (예고 1초). */
        int rain(List<Player> ps) {
            anim("cast_short");
            List<Location> spots = new ArrayList<>();
            for (Player pl : ps) spots.add(ground(pl.getLocation()));
            while (spots.size() < 8) {
                double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, r = 3 + ReaperBoss.RNG.nextDouble() * (arena - 4);
                spots.add(ground(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            for (int i = 0; i < spots.size(); i++) {
                Location at = spots.get(i);
                later(1 + 4 * i, () -> {
                    later(3, () -> Fx.fall(plugin, "claude:mordiandarkrain/projectile", at.clone().add(0, 18, 0), at.clone().add(0, 0.3, 0), 17));
                    strike(Shape.CIRCLE, at, 0, 3, 0, 0, 20, 22, "claude:mordiandarkrain/impact", 0, null);
                });
            }
            return 60;
        }
        /** 10 사슬 회전: 0.6초에 원 r 6 (1바퀴) → 0.7초 뒤 한 번 더 (2바퀴). */
        int spin() {
            anim("chain_spin");
            Location c = ground(boss.getLocation());
            later(12, () -> Fx.follow(boss, "claude:mordianchainspin/spin", 30, 0));
            strike(Shape.CIRCLE, c, 0, 6, 0, 0, 12, 20, null, 0, s -> strike(Shape.CIRCLE, ground(boss.getLocation()), 0, 6, 0, 0, 14, 20, null, 0, null));
            return 50;
        }
        /** 11 그림자 돌진: 먼 적 방향 직선 (최대 12 · 폭 3.5, 예고 0.42초) → 끝까지 미끄러짐. 3번 연속 (매번 재조준). */
        int dash(Player target) {
            if (target == null) return 10;
            for (int k = 0; k < 3; k++) {
                later(1 + 22 * k, () -> {
                    Player tg = alive(target) && in(target) ? target : farthest(players());
                    if (tg == null || !free()) return;
                    Location from = ground(boss.getLocation()); float yaw = yawTo(from, tg.getLocation()); face(tg.getLocation());
                    anim("dash");
                    Fx.follow(boss, "claude:mordianshadowdash/dash", 24, 0);
                    double len = clampLen(from, dir(yaw), 12);
                    strike(Shape.LINE, from, yaw, len, 3.5, 0, 8, 26, null, 0, s -> slide(from, dir(yaw), len));
                });
            }
            return 72;
        }

        /* ---------------------------------------------------------------- 판정 공통 */

        /** 모드 예고 → 같은 모양 판정. vfx 는 판정 lead 틱 전에 재생 (0 = 판정 순간). */
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
                if (closed || (!force && !free())) return;
                if (vfx != null && lead <= 0) Fx.effect(plugin, vfx, at, yaw, 50);
                if (dmg > 0) {
                    spellHit = true;
                    try { for (Player pl : inside(show)) plugin.targets().hurt(boss, pl, dmg, EL); } finally { spellHit = false; }
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

        void slide(Location from, Vector dir, double len) {
            plugin.ticker().add(new Ticker.Tick() {
                int k;
                @Override public boolean tick() {
                    if (closed || !boss.isValid() || !free()) return false;
                    k++; Location now = ground(from.clone().add(dir.clone().multiply(len * Math.min(1, k / 7.0)))); now.setDirection(dir); boss.teleport(now);
                    return k < 7;
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
            if (invulnerable || phase != P1) { e.setCancelled(true); return; }
            double after = boss.getHealth() - e.getFinalDamage();
            for (int k = 0; k < GATES.length; k++) {
                if (gateDone[k]) continue;
                double line = GATES[k] * maxHp();
                if (after <= line) { e.setCancelled(true); boss.setHealth(Math.max(1, line)); startGimmick(k); }
                return;   // 기믹은 순서대로
            }
            if (after <= 1) { e.setCancelled(true); boss.setHealth(1); }   // 10% 아래는 재봉인으로만 끝남
        }

        void startGimmick(int k) {
            gateDone[k] = true; phase = P_GIM; invulnerable = true; nextAt = t + 60;
            say("§d" + GIMMICK_NAME[k]);
            switch (k) {
                case 0 -> gimmick = new Bind(this);
                case 1 -> gimmick = new Ritual(this);
                case 2 -> gimmick = new Shadows(this);
                case 3 -> gimmick = new Offering(this);
                case 4 -> gimmick = new Reading(this);
                default -> gimmick = new Seal(this);
            }
        }
        /** 기믹 끝 (그로기 없음: 성공·실패 모두 바로 패턴 복귀). ⑥ 실패는 다시 시도할 수 있게 기준선 되돌림. */
        void gimmickDone(int k, boolean success) {
            invulnerable = false;
            boss.setAI(true); boss.setInvisible(false);
            if (k == 5) { if (success) { reseal(); return; } gateDone[5] = false; }
            phase = P1; nextAt = t + 30;
        }

        /** 기믹 시작 공통: 보스를 중앙으로 (사라졌다 나타남), 제자리 무적. */
        void toCenter(Runnable then) {
            boss.setAI(false);
            anim("vanish");
            Fx.effect(plugin, "claude:mordianshadowgate/sink", ground(boss.getLocation()), boss.getLocation().getYaw(), 40);
            later(24, () -> {
                Location home = center.clone(); boss.teleport(home);
                Fx.effect(plugin, "claude:mordianshadowgate/rise", ground(home), front, 40);
                anim("emerge");
                later(20, then);
            });
        }

        /* ---------------------------------------------------------------- ⑥ 성공 → 재봉인 → 처치 */

        void reseal() {
            phase = P_END; invulnerable = true; nextAt = Integer.MAX_VALUE;
            boss.setAI(false);
            Location c = center.clone(); boss.teleport(c);
            anim("sealed");
            Fx.effect(plugin, "claude:mordianreseal/reseal", ground(c), front, 160);
            title("§f§l대마법사의 봉인", "§7봉인이... 다시 닫힌다");
            later(64, () -> say("크으윽... 또, 이렇게... 기록 속으로..."));
            later(88, () -> { title("§7…나는,", "§f부르지 않았다."); say("§7…나는, 부르지 않았다."); });
            later(100, () -> {
                invulnerable = false; phase = P1;   // death 이벤트가 보스를 처치로 처리
                boss.setInvisible(false);
                spellHit = true;
                try { boss.setHealth(0); } finally { spellHit = false; }
            });
        }

        /* ---------------------------------------------------------------- 기믹 공통 */

        abstract static class Gimmick {
            final Fight f; final int k; int age; boolean over;
            Gimmick(Fight f, int k) { this.f = f; this.k = k; }
            /** 매 틱. false = 끝. */
            boolean step() { age++; return !over; }
            boolean patterns() { return false; }
            /** 소품 피해 허용 여부 (by = 때린 플레이어, 없으면 null). */
            boolean canHurt(Entity prop, Player by) { return false; }
            void propGone(Entity prop) {}
            void cleanup() {}
            void finish(boolean ok) {
                if (over) return;
                over = true;
                f.later(1, () -> f.gimmickDone(k, ok));
            }
        }

        /* ---------------------------------------------------------------- ① 결박의 사슬 (85%) */

        static final class Bind extends Gimmick {
            static final int LIMIT = 400, NEAR = 8, FAR = 14;
            Player a, b, free; Fx.Link chain, slack, taut; final List<Entity> stakes = new ArrayList<>();
            int hurtAt = -99; boolean started;
            Bind(Fight f) {
                super(f, 0);
                List<Player> ps = new ArrayList<>(f.players()); Collections.shuffle(ps, ReaperBoss.RNG);
                if (ps.isEmpty()) { finish(false); return; }
                a = ps.get(0); b = ps.size() > 1 ? ps.get(1) : null; free = ps.size() > 2 ? ps.get(2) : null;
                f.face(a.getLocation()); f.anim("tether");
                f.title("§5§l결박의 사슬", "§f묶인 둘은 8~14칸을 유지 · 남은 한 명이 말뚝 3개를 부숴라 (20초)");
                f.say("§e" + a.getName() + (b != null ? "§f 와(과) §e" + b.getName() : "") + "§f 을(를) 묶는다. "
                    + (free != null ? "§e" + free.getName() + "§f, 그림자 말뚝을 부숴라." : "그림자 말뚝을 부숴라."));
                f.later(15, () -> {
                    if (over) return;
                    chain = Fx.link("claude:mordianchainbind/bind_hold", chest(a), b != null ? chest(b) : f.hand());
                    for (int i = 0; i < 3; i++) {
                        float yaw = f.front + 60 + i * 120;
                        Location at = ground(f.center.clone().add(dir(yaw).multiply(f.arena - 2))); at.setYaw(yaw + 180);
                        Fx.effect(f.plugin, "claude:mordianshadowgate/rise", at, 0, 30);
                        Entity e = f.prop("CA_Mordian_stake", at, "stake");
                        if (e != null) stakes.add(e);
                    }
                    started = true;
                });
            }
            @Override boolean patterns() { return started; }
            @Override boolean canHurt(Entity prop, Player by) {
                if (!stakes.contains(prop) || by == null) return false;
                return solo || free == null || by.equals(free);
            }
            Location other() { return b != null ? chest(b) : f.hand(); }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return true;
                if (!alive(a) || (b != null && !alive(b))) { fail("묶인 사람이 쓰러졌다"); return false; }
                if (age % 180 == 0) {   // Fx.link 요청 길이(10초) 전에 다시 잇기
                    if (chain != null) { chain.close(); chain = Fx.link("claude:mordianchainbind/bind_hold", chest(a), other()); }
                    if (slack != null) { slack.close(); slack = null; }
                    if (taut != null) { taut.close(); taut = null; }
                }
                if (chain != null) chain.move(chest(a), other());
                if (slack != null) slack.move(chest(a), other());
                if (taut != null) taut.move(chest(a), other());
                double d = Math.sqrt(ReaperBoss.flat2(a.getLocation(), b != null ? b.getLocation() : f.boss.getLocation()));
                boolean near = d < NEAR, far = d > FAR;
                if (near && slack == null) slack = Fx.link("claude:mordianchainbind/bind_slack", chest(a), other());
                if (!near && slack != null) { slack.close(); slack = null; }
                if (far && taut == null) taut = Fx.link("claude:mordianchainbind/bind_taut", chest(a), other());
                if (!far && taut != null) { taut.close(); taut = null; }
                if ((near || far) && f.t - hurtAt >= 10) {
                    hurtAt = f.t;
                    for (Player p : b != null ? List.of(a, b) : List.of(a)) {
                        spellHit = true;
                        try { f.plugin.targets().hurt(f.boss, p, ReaperBoss.Fight.maxOf(p) * (near ? 0.06 : 0.05), EL); } finally { spellHit = false; }
                        if (near && f.plugin.status() != null) f.plugin.status().apply(f.boss, p, Status.SLOW, 12, 0);
                    }
                }
                if (age % 100 == 0 && age < LIMIT) f.title("", "§d남은 말뚝 §f" + stakes.stream().filter(Entity::isValid).count() + "개 §7· §f" + (LIMIT - age) / 20 + "초");
                if (age >= LIMIT) {
                    f.kill(a, "결박의 사슬이 조여들었다"); if (b != null) f.kill(b, "결박의 사슬이 조여들었다");
                    fail("말뚝을 다 부수지 못했다");
                    return false;
                }
                return true;
            }
            @Override void propGone(Entity prop) {
                if (!stakes.remove(prop)) return;
                Fx.effect(f.plugin, "claude:mordianshadowgate/sink", ground(prop.getLocation()), 0, 30);
                if (stakes.isEmpty()) {
                    f.title("§a§l사슬이 끊어졌다", "§f결박에서 풀려났다");
                    f.say("감히... 내 사슬을...");
                    breakChain();
                    finish(true);
                } else f.say("§7남은 말뚝 §f" + stakes.size() + "개");
            }
            void fail(String why) { f.title("§c§l결박의 사슬", "§f" + why); breakChain(); finish(false); }
            void breakChain() {
                if (chain == null || a == null) return;
                Fx.Link br = Fx.link("claude:mordianchainbind/bind_break", chest(a), other());
                f.later(6, () -> { if (br != null) br.close(); });
            }
            @Override void cleanup() {
                if (chain != null) chain.close(); if (slack != null) slack.close(); if (taut != null) taut.close();
                for (Entity e : stakes) f.removeProp(e);
                stakes.clear();
            }
        }

        /* ---------------------------------------------------------------- ② 금지된 의식 — 역순 해제 (70%) */

        static final class Ritual extends Gimmick {
            static final int LIMIT = 600, ERASE = 30, WRONG_GRACE = 10; static final double R = 1.5;
            final Location[] rune = new Location[5]; final int[] order = new int[5];
            final UUID[] hold = new UUID[5]; final boolean[] erased = new boolean[5];
            int next = 4, wrongs, penalty, drawnAt = -1, onFor, onRune = -1, wrongFor; boolean ready, wrongLatch;
            Ritual(Fight f) {
                super(f, 1);
                f.title("§5§l금지된 의식", "§f그려지는 순서를 기억하라");
                f.say("그날 밤의 의식을... 다시 보여 주마.");
                List<Integer> perm = new ArrayList<>(List.of(0, 1, 2, 3, 4)); Collections.shuffle(perm, ReaperBoss.RNG);
                for (int i = 0; i < 5; i++) order[i] = perm.get(i);
                for (int i = 0; i < 5; i++) { rune[i] = ground(f.center.clone().add(dir(f.front + i * 72).multiply(9))); rune[i].setYaw(f.front + i * 72); }
                f.toCenter(() -> {
                    f.anim("cast");
                    for (int i = 0; i < 5; i++) {
                        int idx = order[i];
                        f.later(1 + i * 24, () -> {
                            if (over) return;
                            Fx.effect(f.plugin, "claude:mordianritualrune/draw" + (idx + 1), rune[idx], rune[idx].getYaw(), 20);
                            f.later(17, () -> { if (!over) hold[idx] = Fx.held("claude:mordianritualrune/hold" + (idx + 1), rune[idx], rune[idx].getYaw(), 640); });
                        });
                    }
                    f.later(5 * 24 + 10, () -> {
                        if (over) return;
                        ready = true; drawnAt = age;
                        f.title("§d§l역순으로 지워라", "§f마지막에 그려진 룬부터 — 1.5초씩 밟기 (30초)");
                        f.say("§7그려진 순서의 §d반대§7로, 룬 위에 1.5초 서서 지워라. 두 번 틀리면 끝이다.");
                    });
                });
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!ready) return age < 400 || fail("의식이 시작되지 않았다");
                int left = LIMIT - (age - drawnAt) - penalty;
                if (left <= 0) { for (Player p : f.players()) f.kill(p, "금지된 의식이 완성되었다"); return fail("시간 초과 — 의식이 완성되었다"); }
                if ((age - drawnAt) % 100 == 0) f.title("", "§d남은 룬 §f" + (next + 1) + "개 §7· §f" + left / 20 + "초");
                // 누가 어느 룬 위에 서 있나
                int stand = -1;
                for (Player p : f.players()) for (int i = 0; i < 5; i++) if (!erased[i] && ReaperBoss.flat2(p.getLocation(), rune[i]) <= R * R) stand = i;
                int want = order[next];
                if (stand == want) {
                    wrongFor = 0; wrongLatch = false;
                    if (onRune != want) { onRune = want; onFor = 0; Fx.stop(hold[want]); hold[want] = null; Fx.effect(f.plugin, "claude:mordianritualrune/erase" + (want + 1), rune[want], rune[want].getYaw(), 40); }
                    if (++onFor >= ERASE) {
                        erased[want] = true; onRune = -1; onFor = 0;
                        if (--next < 0) {
                            f.title("§a§l의식 해제", "§f금지된 의식이 풀렸다");
                            f.say("어떻게... 그날 밤을... 되돌려...?");
                            finish(true); return false;
                        }
                        f.say("§7룬 하나가 지워졌다. 남은 §f" + (next + 1) + "개");
                    }
                } else {
                    if (onRune >= 0) {   // 1.5초 전에 내려옴 → 다시 유지
                        int r = onRune; onRune = -1; onFor = 0;
                        hold[r] = Fx.held("claude:mordianritualrune/hold" + (r + 1), rune[r], rune[r].getYaw(), 600);
                    }
                    if (stand >= 0) {
                        if (++wrongFor >= WRONG_GRACE && !wrongLatch) {
                            wrongLatch = true; wrongs++;
                            Fx.effect(f.plugin, "claude:mordianritualrune/wrong" + (stand + 1), rune[stand], rune[stand].getYaw(), 20);
                            if (wrongs >= 2) { for (Player p : f.players()) f.kill(p, "금지된 의식의 순서를 두 번 어겼다"); return fail("순서를 두 번 어겼다"); }
                            penalty += 100;
                            f.title("§c§l순서가 틀렸다", "§f남은 시간 -5초 · 한 번 더 틀리면 끝");
                        }
                    } else { wrongFor = 0; wrongLatch = false; }
                }
                return true;
            }
            boolean fail(String why) { f.title("§c§l금지된 의식", "§f" + why); finish(false); return false; }
            @Override void cleanup() { for (UUID h : hold) Fx.stop(h); }
        }

        /* ---------------------------------------------------------------- ③ 각자의 그림자 (55%) */

        static final class Shadows extends Gimmick {
            static final int LIMIT = 500;
            final Map<Entity, Player> owner = new HashMap<>(); final Map<UUID, Integer> touchAt = new HashMap<>();
            boolean started;
            Shadows(Fight f) {
                super(f, 2);
                f.boss.setAI(false);
                f.anim("vanish");
                Fx.effect(f.plugin, "claude:mordianshadowgate/sink", ground(f.boss.getLocation()), 0, 40);
                f.title("§5§l각자의 그림자", "§f자기 그림자는 벨 수 없다 — 동료의 그림자를 베어라 (25초)");
                f.say("너희 각자의 그림자가... 너희를 기록하리라.");
                f.later(24, () -> {
                    f.boss.teleport(f.center.clone()); f.boss.setInvisible(true);
                    for (Player p : f.players()) {
                        double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2;
                        Location at = ground(p.getLocation().add(Math.cos(a) * 7, 0, Math.sin(a) * 7));
                        if (ReaperBoss.flat2(at, f.center) > (f.arena - 1) * (f.arena - 1)) at = ground(f.center.clone().add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
                        f.plugin.reactions().vfx().areaEffect("claude:mordianshadowgate/rise", at, 1.0, 0, 40, 0xFFFFFF);
                        Location spawnAt = at;
                        f.later(18, () -> {
                            if (over) return;
                            Entity e = f.prop("CA_Mordian_shadow", spawnAt, "shadow");
                            if (e != null) { owner.put(e, p); e.customName(ReaperBoss.LEG.deserialize("§8" + p.getName() + " 의 그림자")); e.setCustomNameVisible(true); }
                        });
                    }
                    f.later(20, () -> started = true);
                });
            }
            @Override boolean canHurt(Entity prop, Player by) {
                Player o = owner.get(prop);
                if (o == null || by == null) return false;
                return solo || !by.equals(o);
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return age < 200;
                if (age % 20 == 0) for (var en : owner.entrySet()) if (en.getKey() instanceof Mob m && alive(en.getValue())) m.setTarget(en.getValue());
                // 그림자가 주인에게 닿으면: 최대 체력 25% + 둔화 (1초 쿨다운)
                for (var en : owner.entrySet()) {
                    Entity s = en.getKey(); Player o = en.getValue();
                    if (!s.isValid() || !alive(o)) continue;
                    if (s.getLocation().distanceSquared(o.getLocation()) <= 2.25 && f.t - touchAt.getOrDefault(o.getUniqueId(), -99) >= 20) {
                        touchAt.put(o.getUniqueId(), f.t);
                        f.propAnim(s, "CHA_MD_p_shadow_attack");
                        spellHit = true;
                        try { f.plugin.targets().hurt(f.boss, o, ReaperBoss.Fight.maxOf(o) * 0.25, EL); } finally { spellHit = false; }
                        if (f.plugin.status() != null) f.plugin.status().apply(f.boss, o, Status.SLOW, 20, 0);
                    }
                }
                if ((age - 44) % 100 == 0 && age < LIMIT) f.title("", "§8남은 그림자 §f" + owner.size() + " §7· §f" + (LIMIT - age) / 20 + "초");
                if (age >= LIMIT) {
                    for (var en : owner.entrySet()) f.kill(en.getValue(), "자신의 그림자에 삼켜졌다");
                    f.title("§c§l그림자에 삼켜졌다", "§f남은 그림자의 주인이 쓰러진다");
                    emerge(); finish(false); return false;
                }
                return true;
            }
            @Override void propGone(Entity prop) {
                if (owner.remove(prop) == null) return;
                Fx.effect(f.plugin, "claude:mordianshadowgate/sink", ground(prop.getLocation()), 0, 30);
                if (owner.isEmpty()) {
                    f.title("§a§l그림자를 걷어냈다", "§f원령이 다시 모습을 드러낸다");
                    f.say("서로의 그림자를... 베어 내다니.");
                    emerge(); finish(true);
                }
            }
            void emerge() {
                f.boss.setInvisible(false);
                Fx.effect(f.plugin, "claude:mordianshadowgate/rise", ground(f.center), f.front, 40);
                f.anim("emerge");
            }
            @Override void cleanup() { for (Entity e : owner.keySet()) f.removeProp(e); owner.clear(); f.boss.setInvisible(false); }
        }

        /* ---------------------------------------------------------------- ④ 제물의 마법진 (40%) */

        static final class Offering extends Gimmick {
            static final int ROUNDS = 5; static final double R = 2.5;
            int round = -1, judgeAt, restUntil; boolean started;
            final List<Location> circles = new ArrayList<>(); final List<Integer> need = new ArrayList<>();
            final List<TextDisplay> labels = new ArrayList<>(); final List<UUID> fx = new ArrayList<>();
            Offering(Fight f) {
                super(f, 3);
                f.title("§5§l제물의 마법진", "§f숫자만큼 마법진 안에 서라 — 5라운드");
                f.say("제물을 바쳐라. 정확히... 그 수만큼.");
                f.toCenter(() -> { f.anim("cast"); started = true; restUntil = age + 10; });
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return age < 400 || fin(false);
                if (round >= 0 && age == judgeAt) judge();
                if (judgeAt < age && age >= restUntil && restUntil > 0) {
                    restUntil = 0;
                    if (round + 1 >= ROUNDS) return fin(true);
                    nextRound();
                }
                return true;
            }
            void nextRound() {
                round++; clearRound();
                List<Player> ps = f.players();
                int alive = Math.max(1, ps.size());
                List<Integer> split = new ArrayList<>();
                if (alive == 1) split.add(1);
                else if (alive == 2) { split.add(1); split.add(1); }
                else { if (ReaperBoss.RNG.nextBoolean()) { split.add(1); split.add(1); split.add(1); } else { split.add(1); split.add(2); } }
                if (alive > 3) { split.clear(); int left = alive; while (left > 0) { int n = Math.min(3, left); if (left - n == 1 && n > 1) n--; split.add(n); left -= n; } }
                Collections.shuffle(split, ReaperBoss.RNG);
                double minSep = 6 + round * 1.5;
                for (int n : split) {
                    Location at = null;
                    for (int tries = 0; tries < 40 && at == null; tries++) {
                        double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, r = 4 + ReaperBoss.RNG.nextDouble() * (f.arena - 6.5);
                        Location c = ground(f.center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                        boolean ok = true; for (Location o : circles) if (Math.sqrt(ReaperBoss.flat2(o, c)) < minSep) ok = false;
                        if (ok) at = c;
                    }
                    if (at == null) at = ground(f.center.clone().add(dir(f.front + circles.size() * 120).multiply(9)));
                    circles.add(at); need.add(n);
                    boolean fast = round >= 3;
                    fx.add(Fx.held(fast ? "claude:mordianofferingcircle/circle3" : "claude:mordianofferingcircle/circle", at, 0, fast ? 70 : 90));
                    Location la = at.clone().add(0, 2.2, 0);
                    labels.add(at.getWorld().spawn(la, TextDisplay.class, td -> {
                        td.setBillboard(Display.Billboard.CENTER); td.setPersistent(false); td.addScoreboardTag(TAG);
                        td.setBackgroundColor(Color.fromARGB(110, 20, 6, 30)); td.setShadowed(true);
                        td.text(ReaperBoss.LEG.deserialize("§d§l" + n));
                        var tr = td.getTransformation(); tr.getScale().set(2.2f, 2.2f, 2.2f); td.setTransformation(tr);
                    }));
                }
                judgeAt = age + (round >= 3 ? 60 : 80);
                f.title("§d" + (round + 1) + "/5", "§f숫자만큼 마법진 안에 — " + (round >= 3 ? "3" : "4") + "초");
            }
            void judge() {
                List<Player> ps = f.players();
                boolean anyEmpty = false;
                for (int i = 0; i < circles.size(); i++) {
                    Location c = circles.get(i); List<Player> in = new ArrayList<>();
                    for (Player p : ps) if (ReaperBoss.flat2(p.getLocation(), c) <= R * R && Math.abs(p.getLocation().getY() - c.getY()) < 3) in.add(p);
                    Fx.stop(fx.get(i));
                    if (in.size() == need.get(i)) { Fx.effect(f.plugin, "claude:mordianofferingcircle/success", c, 0, 20); continue; }
                    Fx.effect(f.plugin, "claude:mordianofferingcircle/fail", c, 0, 40);
                    if (in.isEmpty()) anyEmpty = true;
                    for (Player p : in) f.kill(p, "제물의 수가 맞지 않았다 (" + need.get(i) + "명 필요, " + in.size() + "명)");
                }
                if (anyEmpty) {
                    f.title("§c§l빈 마법진", "§f제물이 없는 마법진이 터졌다");
                    spellHit = true;
                    try { for (Player p : ps) f.plugin.targets().hurt(f.boss, p, ReaperBoss.Fight.maxOf(p) * 0.5, EL); } finally { spellHit = false; }
                }
                for (TextDisplay td : labels) td.remove();
                labels.clear();
                restUntil = age + 40;
                if (round + 1 < ROUNDS) {   // 라운드 사이 그림자 탄 한 발 (가장 먼 적)
                    Player far = f.farthest(f.players());
                    f.later(8, () -> { if (!over && far != null) f.boltForce(far); });
                }
            }
            void clearRound() { for (UUID h : fx) Fx.stop(h); fx.clear(); for (TextDisplay td : labels) td.remove(); labels.clear(); circles.clear(); need.clear(); }
            boolean fin(boolean ok) {
                clearRound();
                f.title(ok ? "§a§l의식 종료" : "§7의식 종료", ok ? "§f제물의 의식을 견뎌냈다" : "");
                if (ok) f.say("...제물이 모자라는군.");
                finish(ok); return false;
            }
            @Override void cleanup() { clearRound(); }
        }

        /** 기믹 중 쓰는 저주 화살 한 발 (무적·패턴 정지 상태에서도). */
        void boltForce(Player target) {
            face(target.getLocation()); anim("brand");
            Location from = ground(boss.getLocation()); float yaw = yawTo(from, target.getLocation());
            double len = clampLen(from, dir(yaw), 22);
            Location end = ground(from.clone().add(dir(yaw).multiply(len))).add(0, 1.2, 0);
            later(8, () -> Fx.fall(plugin, "claude:mordiancursebolt/projectile", hand(), end, 4));
            strike(Shape.LINE, from, yaw, len, 1.6, 0, 12, 20, null, 0, s -> {
                List<Player> hit = inside(s);
                Fx.effect(plugin, "claude:mordiancursebolt/impact", hit.isEmpty() ? ground(end) : ground(hit.get(0).getLocation()), yaw, 50);
            }, true);
        }

        /* ---------------------------------------------------------------- ⑤ 긁힌 기록 낭독 (25%) */

        static final class Reading extends Gimmick {
            enum Cmd {
                GATHER("모여라", 0), SCATTER("흩어져라", 0), LOOK("나를 보라", 1), TURN("등을 돌려라", 1),
                STOP("멈춰라", 2), MOVE("움직여라", 2), RISE("떠올라라", 3), KNEEL("엎드려라", 3);
                final String ko; final int group;
                Cmd(String ko, int group) { this.ko = ko; this.group = group; }
                Cmd opposite() { return values()[ordinal() ^ 1]; }
            }
            record Order(Cmd cmd, boolean struck) { Cmd real() { return struck ? cmd.opposite() : cmd; } }
            static final int READS = 8;
            int read = -1, judgeAt; boolean started;
            final List<Order> now = new ArrayList<>(); final Set<UUID> marked = new HashSet<>();
            final Map<UUID, Location> moved = new HashMap<>();
            Reading(Fight f) {
                super(f, 4);
                f.title("§5§l긁힌 기록", "§f긁힌(취소선) 명령은 §d반대로§f 행동하라");
                f.say("기록을 읽어 주마. 긁힌 글자는... 거짓이다.");
                f.toCenter(() -> { f.anim("cast"); started = true; judgeAt = age + 30; });
            }
            int interval() { return Math.max(40, 80 - (read + 1) * 6); }   // 4초 → 2초
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return age < 400 || end();
                if (age == judgeAt - 20) for (Player p : f.players()) moved.put(p.getUniqueId(), p.getLocation().clone());   // 움직임 판정 기준 (판정 1초 전)
                if (age < judgeAt) return true;
                if (read >= 0) judge();
                if (read + 1 >= READS) return end();
                announce();
                judgeAt = age + interval();
                return true;
            }
            void announce() {
                read++; now.clear();
                Cmd[] all = Cmd.values();
                Cmd c1 = all[ReaperBoss.RNG.nextInt(all.length)];
                if (read >= READS - 2) {   // 마지막 두 번: 두 명령, 하나만 긁힘 (다른 종류에서)
                    Cmd c2; do { c2 = all[ReaperBoss.RNG.nextInt(all.length)]; } while (c2.group == c1.group);
                    boolean firstStruck = ReaperBoss.RNG.nextBoolean();
                    now.add(new Order(c1, firstStruck)); now.add(new Order(c2, !firstStruck));
                } else now.add(new Order(c1, ReaperBoss.RNG.nextInt(100) < 55));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < now.size(); i++) {
                    Order o = now.get(i);
                    if (i > 0) sb.append(" §7그리고 ");
                    sb.append(o.struck() ? "§d§m" + o.cmd().ko + "§r" : "§f§l" + o.cmd().ko);
                }
                f.title(sb.toString(), "§7" + (read + 1) + "/" + READS + " · " + String.format(Locale.ROOT, "%.1f", interval() / 20.0) + "초");
                f.say(sb.toString());
            }
            void judge() {
                List<Player> ps = f.players();
                for (Player p : ps) {
                    boolean ok = true;
                    for (Order o : now) ok &= pass(p, o.real(), ps);
                    if (ok) continue;
                    if (marked.contains(p.getUniqueId())) { f.kill(p, "긁힌 기록을 두 번 어겼다"); continue; }
                    marked.add(p.getUniqueId());
                    Fx.follow(p, "claude:mordianbrand/mark", 40, 0);
                    spellHit = true;
                    try { f.plugin.targets().hurt(f.boss, p, ReaperBoss.Fight.maxOf(p) * 0.4, EL); } finally { spellHit = false; }
                    ReaperBoss.Fight.title(p, "§5§l망각 표식", "§f한 번 더 틀리면 기록에서 지워진다");
                }
            }
            boolean pass(Player p, Cmd c, List<Player> ps) {
                Location l = p.getLocation();
                return switch (c) {
                    case GATHER -> ps.stream().allMatch(o -> o == p || o.getLocation().distanceSquared(l) <= 16);
                    case SCATTER -> ps.stream().allMatch(o -> o == p || o.getLocation().distanceSquared(l) >= 64);
                    case LOOK -> facing(p) < 60;
                    case TURN -> facing(p) > 120;
                    case STOP -> { Location m = moved.get(p.getUniqueId()); yield m != null && ReaperBoss.flat2(m, l) < 0.25; }
                    case MOVE -> { Location m = moved.get(p.getUniqueId()); yield m == null || ReaperBoss.flat2(m, l) >= 1.0; }
                    case RISE -> !p.isOnGround();
                    case KNEEL -> p.isSneaking();
                };
            }
            /** 플레이어 시선과 보스 방향 사이 각도 (수평, 도). */
            double facing(Player p) {
                Vector look = p.getLocation().getDirection().setY(0), to = f.boss.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (look.lengthSquared() < 1e-6 || to.lengthSquared() < 1e-6) return 0;
                return Math.toDegrees(look.normalize().angle(to.normalize()));
            }
            boolean end() {
                f.title("§a§l낭독 끝", "§f긁힌 기록을 견뎌냈다");
                f.say("기록은... 거짓말을 하지. 늘.");
                finish(true); return false;
            }
        }

        /* ---------------------------------------------------------------- ⑥ 대마법사의 봉인 (10%) */

        static final class Seal extends Gimmick {
            static final int LIMIT = 600, CRACK_MAX = 60, WARN = 40;
            final Entity[] pillar = new Entity[3]; final Location[] face = new Location[3]; final int[] crack = new int[3];
            final Fx.Link[] beam = new Fx.Link[3]; final Map<UUID, Integer> blockFor = new HashMap<>(); final Map<UUID, UUID> blockFx = new HashMap<>();
            final Set<Integer> active = new HashSet<>(); int switchAt, startAt = -1; boolean started;
            Seal(Fight f) {
                super(f, 5);
                f.title("§f§l대마법사의 봉인", "§7봉인석으로 가는 광선을 몸으로 막아라 — 30초");
                f.say("이 봉인만... 이 봉인만 깨지면...!");
                f.toCenter(() -> {
                    for (int i = 0; i < 3; i++) {
                        float yaw = f.front + i * 120;
                        Location at = ground(f.center.clone().add(dir(yaw).multiply(11))); at.setYaw(yaw + 180);
                        pillar[i] = f.prop("CA_Mordian_seal_pillar", at, "pillar");
                        face[i] = at.clone().add(dir(yaw).multiply(-1.0)).add(0, 1.4, 0);
                    }
                    f.anim("seal_struggle");
                    started = true; startAt = age; switchAt = age;
                });
            }
            Location src() { return f.boss.getLocation().add(0, 3.2, 0); }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!started) return age < 400 || fail("봉인이 시작되지 않았다");
                int el = age - startAt;
                if (el >= LIMIT) { closeBeams(); f.title("§a§l봉인 유지", "§f대마법사의 봉인이 원령을 덮친다"); finish(true); return false; }
                if (age == switchAt) pick();
                if (age == switchAt + WARN) fire();
                if (el % 100 == 0) f.title("", "§f봉인까지 §e" + (LIMIT - el) / 20 + "초");
                if (age % 5 == 0 && age > switchAt + WARN) judge();
                return true;
            }
            /** 새 대상 2개 고르고 2초 예고 (빨간 직선 = 모드 예고). */
            void pick() {
                closeBeams();
                List<Integer> idx = new ArrayList<>(List.of(0, 1, 2)); Collections.shuffle(idx, ReaperBoss.RNG);
                active.clear(); active.add(idx.get(0)); if (!solo && f.players().size() > 1) active.add(idx.get(1));
                Location from = ground(f.boss.getLocation());
                for (int i : active) {
                    float yaw = yawTo(from, face[i]);
                    double len = Math.sqrt(ReaperBoss.flat2(from, face[i]));
                    f.strike(Shape.LINE, from, yaw, len, 2, 0, WARN, 0, null, 0, null, true);
                }
                switchAt = age + WARN + 120 + ReaperBoss.RNG.nextInt(41);   // 6~8초 유지
            }
            void fire() { for (int i : active) beam[i] = Fx.link("claude:mordiansealbeam/beam", src(), face[i]); }
            /** 5틱마다: 광선 위에 선 사람 = 막음(피해 점점 증가), 아무도 없으면 기둥 균열 +5틱. */
            void judge() {
                List<Player> ps = f.players(); Location from = src();
                Set<UUID> blocking = new HashSet<>();
                for (int i : active) {
                    Vector d = face[i].toVector().subtract(from.toVector()).setY(0); double len = d.length(); d.normalize();
                    Player blocker = null; double best = len;
                    for (Player p : ps) {
                        Vector rel = p.getLocation().toVector().subtract(from.toVector()).setY(0);
                        double along = rel.dot(d); if (along <= 1 || along >= best) continue;
                        if (rel.clone().subtract(d.clone().multiply(along)).length() <= 1.0) { best = along; blocker = p; }
                    }
                    if (blocker != null) {
                        blocking.add(blocker.getUniqueId());
                        if (beam[i] != null) beam[i].move(from, chest(blocker));
                        int n = blockFor.merge(blocker.getUniqueId(), 1, Integer::sum);
                        if (!blockFx.containsKey(blocker.getUniqueId())) blockFx.put(blocker.getUniqueId(), Fx.follow(blocker, "claude:mordiansealbeam/beam_blocked_hit", 400, 0));
                        spellHit = true;
                        try { f.plugin.targets().hurt(f.boss, blocker, ReaperBoss.Fight.maxOf(blocker) * Math.min(0.12, 0.02 + 0.006 * n), EL); } finally { spellHit = false; }
                    } else {
                        if (beam[i] != null) beam[i].move(from, face[i]);
                        crack[i] += 5;
                        if (crack[i] % 20 == 0) Fx.effect(f.plugin, "claude:mordiansealbeam/pillar_crack", face[i], yawTo(face[i], from), 30);
                        if (pillar[i] != null) f.propAnim(pillar[i], "CHA_MD_p_pillar_hit");
                    }
                }
                for (int i : active) if (crack[i] >= CRACK_MAX) {
                    for (Player p : ps) f.kill(p, "봉인석이 깨졌다");
                    fail("봉인석 하나가 깨졌다"); return;
                }
                for (UUID u : List.copyOf(blockFx.keySet())) if (!blocking.contains(u)) { Fx.stop(blockFx.remove(u)); blockFor.remove(u); }   // 내려오면 피해 누적 초기화
            }
            void closeBeams() {
                for (int i = 0; i < 3; i++) if (beam[i] != null) {
                    Location to = face[i]; beam[i].close(); beam[i] = null;
                    Fx.Link sw = Fx.link("claude:mordiansealbeam/beam_switch", src(), to);
                    f.later(5, () -> { if (sw != null) sw.close(); });
                }
                for (UUID h : blockFx.values()) Fx.stop(h);
                blockFx.clear(); blockFor.clear();
            }
            boolean fail(String why) { closeBeams(); f.title("§c§l봉인이 깨졌다", "§f" + why); f.say("하하... 드디어...!"); finish(false); return false; }
            @Override void cleanup() { closeBeams(); for (Entity e : pillar) f.removeProp(e); }
        }

        /* ---------------------------------------------------------------- helpers */

        void kill(Player pl, String why) {
            if (pl == null || !alive(pl)) return;
            if (solo) { tell(pl, "§c[시험] 즉사 판정 (" + why + ") — 시험 모드라 살려 둠"); return; }
            tell(pl, "§7" + why);
            later(4, () -> { if (alive(pl)) pl.setHealth(0); });
        }
        static boolean alive(Player p) { return p != null && p.isValid() && !p.isDead(); }
        boolean in(Player p) { return players().contains(p); }

        void face(Location to) { Location l = boss.getLocation(); boss.setRotation(yawTo(l, to), 0); }
        static float yawTo(Location from, Location to) { return yawOf(to.toVector().subtract(from.toVector())); }
        static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }
        static Vector dir(float yaw) { double a = Math.toRadians(yaw); return new Vector(-Math.sin(a), 0, Math.cos(a)); }
        static Location ground(Location at) { return ReaperBoss.ground(at); }
        static Location chest(LivingEntity e) { return e.getLocation().add(0, 1.1, 0); }
        /** 보스 오른손 근처 (발밑 + 앞 1.5 + 오른쪽 1.5 + 높이 3). */
        Location hand() {
            Location l = boss.getLocation(); float yaw = l.getYaw();
            Vector fwd = dir(yaw), right = dir(yaw + 90);
            return l.clone().add(fwd.multiply(1.5)).add(right.multiply(1.5)).add(0, 3.0, 0);
        }
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
        /** 보스 모델 모션 (CHA_MD_a_&lt;이름&gt;). */
        void anim(String name) { castAt("CHA_MD_a_" + name, boss.getLocation()); }
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
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("모르디안 소품 소환 실패: " + mob + " " + ex); return null; }
        }
        void removeProp(Entity e) { if (e != null) { props.remove(e); if (e.isValid()) e.remove(); } }

        void reset() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear(); Arrays.fill(gateDone, false);
            phase = P1; invulnerable = false; boss.setAI(true); boss.setInvisible(false); last = "";
            boss.setHealth(maxHp()); boss.teleport(center); nextAt = t + 60; emptyFor = 0;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
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
            // 그림자 분신의 몸통 박치기는 무시 (판정은 애드온이 거리로)
            if (e instanceof EntityDamageByEntityEvent be && be.getDamager().getScoreboardTags().contains(TAG)) { e.setCancelled(true); return; }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null && fight.props.contains(e.getEntity())) {
                if (!fight.gimmick.canHurt(e.getEntity(), by)) {
                    e.setCancelled(true);
                    if (by != null && e.getEntity().getScoreboardTags().contains(TAG + ":shadow")) fight.tell(by, "§7자기 그림자는 벨 수 없다 — 동료의 그림자를 노려라");
                    if (by != null && e.getEntity().getScoreboardTags().contains(TAG + ":stake")) fight.tell(by, "§7말뚝은 묶이지 않은 사람만 부술 수 있다");
                } else if (e.getEntity().getScoreboardTags().contains(TAG + ":stake")) fight.propAnim(e.getEntity(), "CHA_MD_p_stake_hit");
                return;
            }
            e.setCancelled(true);
        }

        /** 그림자 분신은 주인만 노림. */
        @EventHandler(ignoreCancelled = true)
        public void target(EntityTargetEvent e) {
            if (!e.getEntity().getScoreboardTags().contains(TAG + ":shadow")) return;
            for (Fight f : FIGHTS.values()) if (f.gimmick instanceof Fight.Shadows s) {
                Player o = s.owner.get(e.getEntity());
                if (o != null && e.getTarget() != null && !e.getTarget().equals(o)) e.setTarget(o);
            }
        }

        static Player attacker(EntityDamageByEntityEvent e) {
            return e.getDamager() instanceof Player pl ? pl : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f != null) {
                f.title("§5§l모르디안의 원령 재봉인", "§7긁힌 가면만이 바닥에 남았다");
                f.close();
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            e.getDrops().clear(); e.setDroppedExp(0);
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null) fight.gimmick.propGone(e.getEntity());
        }
    }
}
