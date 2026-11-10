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
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

// [기능:빼빼로데이보스 시작]
/**
 * chapepero - 빼빼로데이 3인 이벤트 보스 "외톨이 마녀 쇼콜라" 진행자 (2026-11-11). 미몹 CA_Boss_pep_chocola(_shiny) 의 ~onSpawn 에서
 * {@code chapepero{a=start;arena=16;white=false} @self}. 소환 위치 = 경기장 중심.
 *
 * <p>짤패턴 6개 (휘두르기·막대 비·웅덩이·아몬드 폭탄 / 55% 아래 십자 베기·퐁듀 대폭발) + 졸개 소환. 2초마다 (마지막 한 입 1.5초).
 * <p>기믹 (체력 기준선, 기준선에서 피해가 멈추고 기믹 시작):
 * <ol>
 *   <li>85% 나눠 먹기 - 분홍(피해 180% 를 원 안 인원으로 나눔) + 초코(혼자 맞아야 함). 맞은 사람 20초 배부름 (배부른 채 또 나눠 먹으면 즉사).</li>
 *   <li>70% 빼빼로 게임 - 둘을 막대로 연결, 8초 동안 막대가 20칸 → 1칸. 막대보다 멀면/너무 일찍 붙으면/보스 몸 통과하면 실패.
 *       남은 한 명은 보스·병정이 집중. 실패 = 둘 60% + 보스 5% 회복.</li>
 *   <li>55% 선물 받아주기 - 세 사람을 이은 삼각형 안에 떨어지는 선물 상자를 받기 (표식이 움직임). 밖 = 전원 45% + 아몬드 3. 반복 때 상자 2개.</li>
 *   <li>40% 초콜릿 판 깨물기 - 5×5 조각, 깨물 순서를 보여 주고 숨김 → 그 순서대로 깨묾 (조각 위 = 30% + 튕김). 2라운드.</li>
 *   <li>30% 혼자는 싫어 - 한 명을 하트 우리에 가둠 (초당 5%, 20초 = 사망). 우리 양쪽 하트 자물쇠 둘을 2초 안에 같이 부숴야 풀림.
 *       풀린 사람에게 바로 아몬드 폭탄.</li>
 *   <li>20% 진짜 쇼콜라 찾기 - 넷으로 흩어짐 (진짜만 가끔 눈 깜빡임). 진짜는 3초 안에 전원이 때려야 피해가 들어감.
 *       가짜 = 반사 + 둔화. 10초마다 자리 섞기, 세 번 섞이면 넷이 동시에 터짐 (원 r 9). 진짜에게 최대 체력 5% 넣으면 성공.</li>
 *   <li>10% 마지막 한 입 - 초코가 가장자리부터 10초마다 2칸씩 차오름 (밖 = 초당 8% + 둔화), 60초 안에 못 잡으면 전멸.
 *       그동안 나눠 먹기·빼빼로 게임·초콜릿 판(짧게) 이 15초마다.</li>
 * </ol>
 * 55% 아래(2페이즈)는 기믹 없는 동안 35초마다 나눠 먹기·빼빼로 게임·선물 받아주기(상자 2개) 중 하나.
 * 공짜 딜 시간(그로기) 없음. 빨간 예고 = 모드 예고(Telegraphs). VFX = claude:pepchocola/* · pepsweet/* · pepgimmick/*.
 */
final class PeperoBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_pepero_prop";
    static final String NAME = "§d[§f쇼콜라§d] §f";
    static boolean spellHit, solo;

    PeperoBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 16), 10, 30), p.b("white", false));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /** /테스트 보스 쇼콜라 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 쇼콜라. */
    static String test(Player p, String what, String arg) {
        if (what.equals("혼자")) {
            solo = arg.equals("켜기");
            return solo ? "혼자 시험 모드 켬: 즉사 대신 알림 · 혼자여도 빼빼로 게임·삼각형 판정을 흉내" : "혼자 시험 모드 끔";
        }
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 쇼콜라가 없어요 (/mm mobs spawn CA_Boss_pep_chocola - 소환한 자리가 경기장 중심)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (f.gimmick != null) return "지금은 기믹 중이에요";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "기믹" -> {
                int k = Math.clamp(Integer.parseInt(arg), 1, 7) - 1;
                if (f.gimmick != null) return "지금 기믹 중이에요";
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * Fight.GATES[k])));
                f.startGate(k);
                return "기믹 " + (k + 1) + " 시작";
            }
            default -> { f.reset(); return "처음 상태로 (체력 가득)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final double[] GATES = {0.85, 0.70, 0.55, 0.40, 0.30, 0.20, 0.10};
        static final int SHARE = 0, POCKY = 1, CATCH = 2, BITE = 3, CAGE = 4, CLONES = 5, LAST = 6;

        final MagicMythicPlugin plugin; final LivingEntity boss; final Location center; final double arena; final boolean white;
        int t, nextAt = 70, emptyFor, rotateAt = Integer.MAX_VALUE, summonAt = 400;
        final boolean[] gateDone = new boolean[7];
        boolean invulnerable, closed, phase2, enraged;
        String last = "";
        Gimmick gimmick;
        Last finale;
        final Map<UUID, Integer> fullUntil = new HashMap<>();
        final Map<UUID, UUID> fullFx = new HashMap<>();
        final List<Entity> props = new ArrayList<>();
        /** 빼빼로 게임 중 보스가 노리는 사람 (나머지 한 명). */
        Player focus;

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena, boolean white) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena; this.white = white;
            anim("spawn");
            Fx.effect(plugin, "claude:pepsweet/phase", ground(center), 0, 40);
            later(40, () -> {
                say("빼빼로? 흥. 올해도 나한테 주는 사람은 없을 거잖아. 그럼 아무도 못 먹게 할 거야!");
            });
        }

        void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void tell(Player pl, String line) { pl.sendMessage(ReaperBoss.LEG.deserialize(NAME + line)); }
        void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }
        void titleTo(Player pl, String main, String sub) { ReaperBoss.Fight.title(pl, main, sub); }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            // 쓰러져 교화 봉인(30초) 중에는 기믹·마지막 한 입·패턴 전부 멈춤 (10/31 하베스트 봉인 정지와 같은 사고 막기)
            if (sealed()) {
                if (gimmick != null) { Gimmick g = gimmick; gimmick = null; g.cleanup(); }
                finale = null; focus = null; clearFull();
                nextAt = Math.max(nextAt, t + 40);
                return true;
            }
            if (gimmick != null && !gimmick.step()) { Gimmick g = gimmick; gimmick = null; g.cleanup(); afterGimmick(g); }
            if (finale != null) finale.step();
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            for (var e : List.copyOf(fullUntil.entrySet())) if (e.getValue() <= t) { fullUntil.remove(e.getKey()); Fx.stop(fullFx.remove(e.getKey())); }
            if (gimmick == null && t >= rotateAt) { rotate(); return true; }
            if (patternsAllowed() && t >= nextAt) pattern(ps);
            if (gimmick == null && t >= summonAt) { summon(); summonAt = t + (enraged ? 300 : 400); }
            return true;
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        boolean patternsAllowed() { return !closed && (gimmick == null || gimmick.patterns()); }
        /** MagicCodexBridge 교화 봉인 표시 (TamingBridge.seal: PDC magiccodexbridge:taming_grace + AI 끔 + 무적). */
        static final NamespacedKey SEAL = NamespacedKey.fromString("magiccodexbridge:taming_grace");
        boolean sealed() { return boss.getPersistentDataContainer().has(SEAL, org.bukkit.persistence.PersistentDataType.BYTE) || (!boss.hasAI() && boss.isInvulnerable()); }
        /** 보스가 기믹 때문에 제자리여야 하는 동안 (패턴이 끝나도 AI 를 켜면 안 됨). */
        boolean holdStill() { return gimmick != null && (gimmick.frozen() || gimmick instanceof Clones); }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = arena + 6;
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (ReaperBoss.flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < 24) out.add(pl);
            }
            return out;
        }
        /** 우리에 갇힌 사람은 패턴 대상에서 빠짐. 빼빼로 게임 중엔 남은 한 명만. */
        List<Player> targetable(List<Player> ps) {
            List<Player> out = new ArrayList<>(ps);
            if (gimmick instanceof Cage cg && cg.trapped != null) out.remove(cg.trapped);
            if (focus != null && alive(focus) && out.contains(focus)) return List.of(focus);
            return out;
        }
        boolean full(Player pl) { Integer u = fullUntil.get(pl.getUniqueId()); return u != null && u > t; }
        void makeFull(Player pl) {
            fullUntil.put(pl.getUniqueId(), t + 400);
            Fx.stop(fullFx.remove(pl.getUniqueId()));
            UUID h = Fx.follow(pl, "claude:pepgimmick/full", 400, 0); if (h != null) fullFx.put(pl.getUniqueId(), h);
            titleTo(pl, "", "§6배부름 §f20초");
        }

        /* ---------------------------------------------------------------- 패턴 */

        static final List<String> P1 = List.of("swing", "rain", "puddle", "almond");
        static final List<String> P2 = List.of("swing", "rain", "puddle", "almond", "cross", "fondue");
        /** 기믹 중엔 가벼운 패턴만. */
        static final Set<String> LIGHT = Set.of("rain", "puddle", "almond");

        void pattern(List<Player> ps) {
            boolean light = gimmick != null;
            List<String> pool = new ArrayList<>();
            for (String id : phase2 ? P2 : P1) if (!id.equals(last) && (!light || LIGHT.contains(id))) pool.add(id);
            if (pool.isEmpty()) return;
            run(pool.get(ReaperBoss.RNG.nextInt(pool.size())), ps);
            if (light) nextAt += 30;
        }

        void run(String id, List<Player> ps) {
            List<Player> tg = targetable(ps);
            if (tg.isEmpty()) { nextAt = t + 20; return; }
            Player near = nearest(tg), rnd = tg.get(ReaperBoss.RNG.nextInt(tg.size()));
            int len = switch (id) {
                case "swing" -> swing(near);
                case "rain" -> rain(tg);
                case "puddle" -> puddle(rnd);
                case "almond" -> almond(rnd, 21, true);
                case "cross" -> cross();
                case "fondue" -> fondue();
                default -> 20;
            };
            last = id;
            nextAt = t + len + (enraged ? 4 : 14);
            // 공격하는 동안 제자리 (11/11 아스칼론 "예고 뒤에도 걸어 다님" 과 같은 사고 막기)
            if (!holdStill()) { boss.setAI(false); int until = len; later(until, () -> { if (!holdStill() && t >= nextAt - (enraged ? 4 : 14) - 1) boss.setAI(true); }); }
        }
        String vfx(String sys) { return "claude:pepchocola/" + sys + (white ? "_w" : ""); }

        /** 1 휘두르기: 부채꼴 r 6 · 200°, 예고 0.6초, 14 + 넉백. */
        int swing(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("swing");
            Location at = ground(boss.getLocation());
            float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 6, 0, 200, 12, 14, vfx("swing"), 6, s -> {
                for (Player pl : inside(s)) pl.setVelocity(flat(pl.getLocation().toVector().subtract(at.toVector())).multiply(0.9).setY(0.35));
            });
            return 26;
        }
        /** 2 막대 비: 대상 전원 발밑 원 r 2.5, 예고 1.2초, 15. 마지막 한 입 땐 두 번. */
        int rain(List<Player> ps) {
            anim("stick_rain");
            int waves = enraged ? 2 : 1;
            for (int w = 0; w < waves; w++) later(1 + w * 22, () -> {
                for (Player pl : targetable(players())) {
                    Location at = ground(pl.getLocation());
                    strike(Shape.CIRCLE, at, 0, 2.5, 0, 0, 24, 15, vfx("stickrain"), 2.5, null);
                }
            });
            return enraged ? 46 : 34;
        }
        /** 3 녹은 초코 웅덩이: 대상 발밑 원 r 4, 예고 0.9초 → 0.5초마다 4번 6 + 둔화. */
        int puddle(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("puddle");
            Location at = ground(target.getLocation());
            strike(Shape.CIRCLE, at, 0, 4, 0, 0, 18, 6, vfx("puddle"), 4, s -> {
                for (int k = 1; k < 4; k++) later(10 * k, () -> {
                    spellHit = true;
                    try { for (Player pl : inside(s)) { plugin.targets().hurt(boss, pl, 6, "earth"); if (plugin.status() != null) plugin.status().apply(boss, pl, Status.SLOW, 30, 0); } }
                    finally { spellHit = false; }
                });
            });
            return 28;
        }
        /** 4 아몬드 폭탄: 4초 따라다니는 표식 → 그 자리 원 r 4 (근처 사람도 맞음). */
        int almond(Player target, double dmg, boolean animate) {
            if (target == null) return 10;
            if (animate) { face(target.getLocation()); anim("almond_throw"); }
            UUID mark = Fx.follow(target, "claude:pepchocola/almond_mark", 80, 0);
            later(60, () -> {
                Fx.stop(mark);
                if (!alive(target)) return;
                Location at = ground(target.getLocation());
                strikeRaw(Shape.CIRCLE, at, 0, 4, 0, 0, 20, dmg, "claude:pepchocola/almond_boom", 4, null);
            });
            return 24;
        }
        /** 5 십자 막대 베기: 직선 14 × 2.5 네 방향 (0.8초) → 45° 돌려 (0.5초). */
        int cross() {
            anim("cross");
            Location c = ground(boss.getLocation());
            float base = boss.getLocation().getYaw();
            for (int k = 0; k < 4; k++) strike(Shape.LINE, c, base + 90 * k, 14, 2.5f, 0, 16, 17, k == 0 ? vfx("cross") : null, 14, null);
            later(18, () -> { for (int k = 0; k < 4; k++) strike(Shape.LINE, c, base + 45 + 90 * k, 14, 2.5f, 0, 10, 17, k == 0 ? vfx("cross") : null, 14, null); });
            return 36;
        }
        /** 6 초코 퐁듀 대폭발: 보스 중심 원 r 9, 예고 2초, 33 + 기절 0.8초. */
        int fondue() {
            anim("fondue_charge");
            Location c = ground(boss.getLocation());
            boss.setAI(false);
            strike(Shape.CIRCLE, c, 0, 9, 0, 0, 40, 33, vfx("fondue"), 9, s -> {
                anim("fondue_burst");
                for (Player pl : inside(s)) if (plugin.status() != null) plugin.status().apply(boss, pl, Status.STUN, 16, 0);
            });
            later(44, () -> { if (!holdStill()) boss.setAI(true); });
            return 52;
        }
        /** 공방 일꾼 부르기: 졸개 6마리 넘으면 안 함. */
        void summon() {
            long alive = props.stream().filter(e -> e.isValid() && e.getScoreboardTags().contains(TAG + ":minion")).count();
            if (alive >= 6 || players().isEmpty()) return;
            anim("gift_summon");
            String[] kinds = {"CA_Pep_stick_soldier", "CA_Pep_sprinkle_fairy", "CA_Pep_almond_roller"};
            for (int k = 0; k < 3; k++) {
                Location at = ground(center.clone().add(dir(ReaperBoss.RNG.nextFloat() * 360).multiply(4 + ReaperBoss.RNG.nextDouble() * (arena - 6))));
                Fx.effect(plugin, "claude:pepsweet/summon", at, 0, 20);
                String kind = kinds[k];
                later(16, () -> prop(kind, at, "minion"));
            }
        }

        /* ---------------------------------------------------------------- 판정 공통 */

        void strike(Shape shape, Location at, float yaw, double size, float width, double angle, int ticks, double dmg, String vfx, double vfxSize, Consumer<Show> after) {
            if (!patternsAllowed()) return;
            strikeRaw(shape, at, yaw, size, width, angle, ticks, dmg, vfx, vfxSize, after);
        }
        /** 기믹용: 패턴 허용 여부와 상관없이. dmg 는 고정값 (음수 = 최대 체력 비율). */
        void strikeRaw(Shape shape, Location at, float yaw, double size, float width, double angle, int ticks, double dmg, String vfx, double vfxSize, Consumer<Show> after) {
            Show s;
            try {
                s = new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw, (float) size, width, (float) (angle <= 0 ? 60 : angle), Math.max(2, ticks), TelegraphProtocol.RED);
            } catch (IllegalArgumentException e) { return; }
            plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {
                if (closed) return;
                if (vfx != null) areaFx(vfx, at, size, yaw, vfxSize);
                spellHit = true;
                try { if (dmg != 0) for (Player pl : inside(show)) hurt(pl, dmg); }
                finally { spellHit = false; }
                if (after != null) after.accept(show);
            });
        }
        /** dmg &lt; 0 = 최대 체력 비율 (-0.3 = 30%). */
        void hurt(Player pl, double dmg) {
            double d = dmg < 0 ? ReaperBoss.Fight.maxOf(pl) * -dmg : dmg;
            spellHit = true;
            try { plugin.targets().hurt(boss, pl, d, "earth"); } finally { spellHit = false; }
        }
        List<Player> inside(Show s) {
            List<Player> out = new ArrayList<>();
            for (Player pl : players()) {
                if (gimmick instanceof Cage cg && pl.equals(cg.trapped)) continue;
                Location j = pl.getLocation();
                if (Math.abs(j.getY() - s.y()) < 3.5 && TelegraphProtocol.inside(s, j.getX(), j.getZ(), 0.3)) out.add(pl);
            }
            return out;
        }
        void areaFx(String id, Location at, double size, float yaw, double ref) {
            if (id == null) return;
            if (ref <= 0 || Math.abs(size - ref) < 0.05) Fx.effect(plugin, id, at, yaw, 60);
            else plugin.reactions().vfx().areaEffect(id, at, size, yaw, 60, 0xFFFFFF);
        }

        /* ---------------------------------------------------------------- 기준선 · 기믹 */

        void damaged(EntityDamageEvent e) {
            if (invulnerable) { e.setCancelled(true); return; }
            if (gimmick instanceof Clones cl) { cl.realHit(e); if (e.isCancelled()) return; }
            double after = boss.getHealth() - e.getFinalDamage();
            for (int k = 0; k < GATES.length; k++) {
                if (gateDone[k]) continue;
                double line = GATES[k] * maxHp();
                if (after <= line) {
                    if (gimmick != null && !(gimmick instanceof Share)) { e.setCancelled(true); boss.setHealth(Math.max(1, line)); return; }   // 기믹 끝날 때까지 기준선에서 멈춤
                    e.setCancelled(true); boss.setHealth(Math.max(1, line)); startGate(k);
                }
                return;   // 기준선은 순서대로
            }
        }

        void startGate(int k) {
            gateDone[k] = true;
            if (k >= CATCH) phase2 = true;
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            switch (k) {
                case SHARE -> gimmick = new Share(this);
                case POCKY -> gimmick = new Pocky(this);
                case CATCH -> gimmick = new Catch(this, 1);
                case BITE -> gimmick = new Bite(this, 8, 2);
                case CAGE -> gimmick = new Cage(this);
                case CLONES -> gimmick = new Clones(this);
                default -> startLast();
            }
            if (gimmick != null && gimmick.frozen()) { invulnerable = true; boss.setAI(false); }
            nextAt = Math.max(nextAt, t + 40);
        }
        void afterGimmick(Gimmick g) {
            invulnerable = false; focus = null;
            if (!closed) { boss.setAI(true); boss.setInvisible(false); }
            nextAt = t + 30;
            if (phase2 && finale == null) rotateAt = t + 700;
        }
        /** 2페이즈 반복 기믹 (35초마다). */
        void rotate() {
            rotateAt = Integer.MAX_VALUE;
            List<Player> ps = players();
            int pick = ReaperBoss.RNG.nextInt(3);
            gimmick = switch (pick) { case 0 -> new Share(this); case 1 -> new Pocky(this); default -> new Catch(this, 2); };
            if (gimmick.frozen()) { invulnerable = true; boss.setAI(false); }
        }

        abstract static class Gimmick {
            final Fight f; int age;
            Gimmick(Fight f) { this.f = f; }
            boolean step() { age++; return true; }
            /** 그동안 보스 짤패턴 (가벼운 것만). */
            boolean patterns() { return false; }
            /** 보스 무적 + 제자리. */
            boolean frozen() { return true; }
            void propGone(Entity prop) {}
            void cleanup() {}
        }

        /* ---------------------------------------------------------------- 기믹1 나눠 먹기 */

        static final class Share extends Gimmick {
            static final int WAIT = 80;
            Player pink, choco; UUID pinkFx, chocoFx; Location pinkAt, chocoAt;
            Share(Fight f) {
                super(f);
                List<Player> ps = new ArrayList<>(f.players());
                Collections.shuffle(ps, ReaperBoss.RNG);
                if (ps.isEmpty()) return;
                pink = ps.get(0); choco = ps.size() > 1 ? ps.get(1) : null;
                f.anim("pair_call");
                f.title("§d§l나눠 먹기", "");
                f.say("이렇게 큰 빼빼로, 혼자 먹으면 배 터질걸?");
                pinkFx = Fx.follow(pink, "claude:pepgimmick/share_pink", WAIT + 10, 0);
                if (choco != null) chocoFx = Fx.follow(choco, "claude:pepgimmick/share_choco", WAIT + 10, 0);
            }
            @Override boolean patterns() { return true; }
            @Override boolean frozen() { return false; }
            @Override boolean step() {
                super.step();
                if (pink == null) return false;
                if (age == WAIT - 20) {   // 마지막 1초: 그 자리에 예고
                    if (alive(pink)) { pinkAt = ground(pink.getLocation()); f.strikeRaw(Shape.CIRCLE, pinkAt, 0, 3, 0, 0, 20, 0, null, 0, null); }
                    if (alive(choco)) { chocoAt = ground(choco.getLocation()); f.strikeRaw(Shape.CIRCLE, chocoAt, 0, 3, 0, 0, 20, 0, null, 0, null); }
                }
                if (age == WAIT) { resolve(); return false; }
                return true;
            }
            void resolve() {
                if (pinkAt != null) {
                    List<Player> in = near(pinkAt, 3);
                    Fx.effect(f.plugin, "claude:pepgimmick/share_hit", pinkAt, 0, 30);
                    double each = 1.8 / Math.max(1, in.size());
                    for (Player pl : in) {
                        if (f.full(pl)) { f.kill(pl, "배부른 채로 또 먹었다"); continue; }
                        f.hurt(pl, -each); f.makeFull(pl);
                    }
                    if (in.size() <= 1) f.say("거봐, 혼자서는 다 못 먹지?");
                }
                if (chocoAt != null) {
                    List<Player> in = near(chocoAt, 3);
                    Fx.effect(f.plugin, "claude:pepchocola/almond_boom", chocoAt, 0, 30);
                    boolean alone = in.size() <= 1;
                    for (Player pl : in) f.hurt(pl, alone ? -0.25 : -0.7);
                }
            }
            List<Player> near(Location at, double r) {
                List<Player> out = new ArrayList<>();
                for (Player pl : f.players()) if (ReaperBoss.flat2(pl.getLocation(), at) <= r * r && Math.abs(pl.getLocation().getY() - at.getY()) < 3.5) out.add(pl);
                return out;
            }
            @Override void cleanup() { Fx.stop(pinkFx); Fx.stop(chocoFx); }
        }

        /* ---------------------------------------------------------------- 기믹2 빼빼로 게임 */

        static final class Pocky extends Gimmick {
            static final int GRACE = 30, EAT = 160;
            static final double START = 20, END = 1.5, BAND = 5;
            Player a, b; boolean over; UUID fa, fb;
            Pocky(Fight f) {
                super(f);
                List<Player> ps = new ArrayList<>(f.players());
                Collections.shuffle(ps, ReaperBoss.RNG);
                if (ps.size() < 2 && !solo) { over = true; return; }
                a = ps.get(0); b = ps.size() > 1 ? ps.get(1) : null;
                if (ps.size() > 2) f.focus = ps.get(2);
                f.anim("pair_call");
                f.title("§d§l빼빼로 게임", "");
                f.say("둘이서 한 막대를 끝까지 먹어 봐. 너무 서두르면… 부끄럽잖아?");
                fa = Fx.follow(a, "claude:pepsweet/pair_mark", GRACE + EAT + 20, 0);
                if (b != null) fb = Fx.follow(b, "claude:pepsweet/pair_mark", GRACE + EAT + 20, 0);
                if (f.focus != null) {
                    for (int k = 0; k < 2; k++) { Location at = ground(f.focus.getLocation().add(dir(ReaperBoss.RNG.nextFloat() * 360).multiply(5))); f.later(10, () -> f.prop("CA_Pep_stick_soldier", at, "minion")); }
                }
            }
            @Override boolean patterns() { return true; }
            @Override boolean frozen() { return false; }
            double len() { int k = Math.max(0, age - GRACE); return START + (END - START) * Math.min(1, k / (double) EAT); }
            Location other() { return b != null ? b.getLocation() : a.getLocation().clone().add(dir(f.t * 3f).multiply(len() - 1)); }   // 혼자 시험: 가상 상대가 막대 끝
            @Override boolean step() {
                super.step();
                if (over) return false;
                if (!alive(a) || (b != null && !alive(b))) return finish(false, "한쪽이 쓰러졌다");
                Location pa = a.getLocation(), pb = other();
                double d = Math.sqrt(ReaperBoss.flat2(pa, pb)), L = len();
                if (age % 2 == 0) stick(pa, pb, L);
                if (age <= GRACE) return true;
                if (d > L + 0.5) return finish(false, "막대가 부러졌다");
                if (d < L - BAND && age < GRACE + EAT - 10) return finish(false, "너무 서둘렀다");
                if (throughBoss(pa, pb)) return finish(false, "막대가 쇼콜라에 걸려 부러졌다");
                if (age >= GRACE + EAT) return finish(d <= 2.5, d <= 2.5 ? null : "끝까지 먹지 못했다");
                return true;
            }
            boolean throughBoss(Location pa, Location pb) {
                Vector A = pa.toVector().setY(0), B = pb.toVector().setY(0), P = f.boss.getLocation().toVector().setY(0), AB = B.clone().subtract(A);
                double l2 = AB.lengthSquared(); if (l2 < 1e-6) return false;
                double tt = Math.clamp(P.clone().subtract(A).dot(AB) / l2, 0, 1);
                return A.clone().add(AB.multiply(tt)).distanceSquared(P) < 1.0;
            }
            /** 막대: 두 사람 사이 입자 선 (초코 쪽 → 딸기 쪽), 남은 길이만큼 진하게. */
            void stick(Location pa, Location pb, double L) {
                World w = pa.getWorld(); Vector d = pb.toVector().subtract(pa.toVector()); double n = d.length(); if (n < 0.1) return;
                d.multiply(1 / n);
                Particle.DustOptions choco = new Particle.DustOptions(f.white ? Color.fromRGB(0xF4EDE4) : Color.fromRGB(0x5B3222), 1.2f), pink = new Particle.DustOptions(Color.fromRGB(0xF2779F), 1.2f);
                for (double s = 0; s <= Math.min(n, 30); s += 0.5) w.spawnParticle(Particle.DUST, pa.clone().add(0, 1.3, 0).add(d.clone().multiply(s)), 1, 0, 0, 0, 0, s < n / 2 ? choco : pink);
            }
            boolean finish(boolean ok, String why) {
                if (over) return false;
                over = true;
                Location mid = a.getLocation().clone().add(other()).multiply(0.5);
                if (ok) {
                    Fx.effect(f.plugin, "claude:pepsweet/pair_success", ground(mid), 0, 30);
                    f.say("뭐, 뭐야… 진짜로 끝까지 먹었어…?");
                } else {
                    Fx.effect(f.plugin, "claude:pepgimmick/pocky_break", ground(mid), 0, 30);
                    f.say("§7(" + why + ")");
                    f.say("거봐. 결국 다들 혼자잖아.");
                    for (Player pl : new Player[]{a, b}) if (alive(pl)) { Fx.effect(f.plugin, "claude:pepsweet/pair_fail", ground(pl.getLocation()), 0, 20); f.hurt(pl, -0.6); }
                    f.boss.setHealth(Math.min(f.maxHp(), f.boss.getHealth() + f.maxHp() * 0.05));
                }
                return false;
            }
            @Override void cleanup() { Fx.stop(fa); Fx.stop(fb); f.focus = null; }
        }

        /* ---------------------------------------------------------------- 기믹3 선물 받아주기 */

        static final class Catch extends Gimmick {
            static final int DRIFT = 110, FALL = 20;
            final int n; final Location[] pt; final Vector[] vel; final UUID[] mark; boolean over;
            Catch(Fight f, int n) {
                super(f);
                this.n = n; pt = new Location[n]; vel = new Vector[n]; mark = new UUID[n];
                for (int i = 0; i < n; i++) {
                    pt[i] = ground(f.center.clone().add(dir(ReaperBoss.RNG.nextFloat() * 360).multiply(ReaperBoss.RNG.nextDouble() * (f.arena - 5))));
                    vel[i] = dir(ReaperBoss.RNG.nextFloat() * 360).multiply(0.11 + 0.04 * ReaperBoss.RNG.nextDouble());
                    mark[i] = Fx.held("claude:pepgimmick/gift_mark", pt[i], 0, 14);
                }
                f.anim("gift_summon");
                f.title("§d§l선물 받아주기", "");
                f.say("자, 선물이야. …받아 줄 사람, 있으면 말이지.");
            }
            @Override boolean patterns() { return true; }
            @Override boolean step() {
                super.step();
                if (over) return false;
                List<Player> ps = f.players();
                if (age % 2 == 0) ribbons(ps);
                if (age < DRIFT) {
                    for (int i = 0; i < n; i++) {
                        Location nx = pt[i].clone().add(vel[i]);
                        if (ReaperBoss.flat2(nx, f.center) > (f.arena - 4) * (f.arena - 4)) { Vector toC = f.center.toVector().subtract(pt[i].toVector()).setY(0).normalize(); vel[i] = toC.rotateAroundY((ReaperBoss.RNG.nextDouble() - 0.5) * 1.2).multiply(vel[i].length()); nx = pt[i].clone().add(vel[i]); }
                        pt[i] = ground(nx);
                        if (age % 10 == 0) { Fx.stop(mark[i]); mark[i] = Fx.held("claude:pepgimmick/gift_mark", pt[i], 0, 14); }
                    }
                    return true;
                }
                if (age == DRIFT) for (int i = 0; i < n; i++) { Fx.stop(mark[i]); f.strikeRaw(Shape.CIRCLE, pt[i], 0, 2, 0, 0, FALL, 0, null, 0, null); Fx.effect(f.plugin, "claude:pepgimmick/gift_fall", pt[i], 0, FALL + 20); }
                if (age < DRIFT + FALL) return true;
                boolean all = true;
                for (int i = 0; i < n; i++) {
                    boolean caught = caught(ps, pt[i]);
                    Fx.effect(f.plugin, caught ? "claude:pepgimmick/gift_catch" : "claude:pepsweet/gift_break", pt[i], 0, 30);
                    if (!caught) {
                        all = false;
                        for (Player pl : ps) f.hurt(pl, -0.45);
                        for (int k = 0; k < 3; k++) { Location at = ground(pt[i].clone().add(dir(k * 120f).multiply(1.5))); f.prop("CA_Pep_almond_roller", at, "minion"); }
                    }
                }
                if (all) f.say("…받아 준 거야? 진짜로?");
                else f.say("역시 아무도 안 받아 주잖아!");
                over = true;
                return false;
            }
            /** 세 사람 삼각형 안 (둘이면 두 사람 잇는 선에서 2칸, 혼자면 3칸 안). */
            boolean caught(List<Player> ps, Location p) {
                if (ps.size() >= 3) {
                    for (int i = 0; i < ps.size(); i++) for (int j = i + 1; j < ps.size(); j++) for (int k = j + 1; k < ps.size(); k++)
                        if (inTri(p, ps.get(i).getLocation(), ps.get(j).getLocation(), ps.get(k).getLocation())) return true;
                    return false;
                }
                if (ps.size() == 2) return segDist(p, ps.get(0).getLocation(), ps.get(1).getLocation()) <= 2;
                return !ps.isEmpty() && ReaperBoss.flat2(p, ps.get(0).getLocation()) <= 9;
            }
            static boolean inTri(Location p, Location a, Location b, Location c) {
                double d1 = cross(p, a, b), d2 = cross(p, b, c), d3 = cross(p, c, a);
                boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                return !(neg && pos);
            }
            static double cross(Location p, Location a, Location b) { return (p.getX() - b.getX()) * (a.getZ() - b.getZ()) - (a.getX() - b.getX()) * (p.getZ() - b.getZ()); }
            static double segDist(Location p, Location a, Location b) {
                double ax = a.getX(), az = a.getZ(), bx = b.getX() - ax, bz = b.getZ() - az, px = p.getX() - ax, pz = p.getZ() - az, l2 = bx * bx + bz * bz;
                double tt = l2 < 1e-6 ? 0 : Math.clamp((px * bx + pz * bz) / l2, 0, 1);
                return Math.hypot(px - bx * tt, pz - bz * tt);
            }
            void ribbons(List<Player> ps) {
                if (ps.size() < 2) return;
                var dust = new Particle.DustOptions(Color.fromRGB(0xF2779F), 1.0f);
                for (int i = 0; i < ps.size(); i++) {
                    Location a = ps.get(i).getLocation().add(0, 1, 0), b = ps.get((i + 1) % ps.size()).getLocation().add(0, 1, 0);
                    if (ps.size() == 2 && i == 1) break;
                    Vector d = b.toVector().subtract(a.toVector()); double len = d.length(); if (len < 0.1) continue; d.multiply(1 / len);
                    for (double s = 0; s <= Math.min(len, 40); s += 0.7) a.getWorld().spawnParticle(Particle.DUST, a.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0, dust);
                }
            }
            @Override void cleanup() { for (UUID u : mark) Fx.stop(u); }
        }

        /* ---------------------------------------------------------------- 기믹4 초콜릿 판 깨물기 */

        static final class Bite extends Gimmick {
            static final int GRID = 5; static final double TILE = 4;
            final int count, rounds; int round, phaseT; final List<int[]> order = new ArrayList<>(); boolean over;
            Bite(Fight f, int count, int rounds) {
                super(f);
                this.count = count; this.rounds = rounds;
                f.anim("fondue_charge");
                f.title("§6§l초콜릿 판 깨물기", "");
                f.say("이 판 초콜릿, 내가 먼저 먹을 거야. 어디부터 먹을지… 잘 봐 둬.");
                phaseT = -40;
                pick();
            }
            @Override boolean frozen() { return f.finale == null; }
            void pick() {
                order.clear();
                List<int[]> all = new ArrayList<>();
                for (int x = 0; x < GRID; x++) for (int z = 0; z < GRID; z++) all.add(new int[]{x, z});
                Collections.shuffle(all, ReaperBoss.RNG);
                for (int i = 0; i < Math.min(count + round * 2, all.size()); i++) order.add(all.get(i));
            }
            int show() { return round == 0 ? 12 : 9; }
            Location tile(int[] c) { double o = -(GRID * TILE) / 2 + TILE / 2; return ground(f.center.clone().add(o + c[0] * TILE, 0, o + c[1] * TILE)); }
            @Override boolean step() {
                super.step();
                if (over) return false;
                phaseT++;
                if (age % 10 == 0) grid();
                if (phaseT < 0) return true;
                int s = show(), showEnd = order.size() * s, gap = 40, bite = round == 0 ? 8 : 6;
                if (phaseT < showEnd) { if (phaseT % s == 0) flash(order.get(phaseT / s)); return true; }
                int k = phaseT - showEnd - gap;
                if (k >= 0 && k % bite == 0 && k / bite < order.size()) chomp(order.get(k / bite), bite);
                if (k >= order.size() * bite + 10) {
                    round++;
                    if (round >= rounds) { over = true; f.say("…다 먹었다. 맛없어."); return false; }
                    pick(); phaseT = -30;
                }
                return true;
            }
            void flash(int[] c) {
                Location at = tile(c);
                Fx.effect(f.plugin, "claude:pepgimmick/bite_flash", at, 0, 12);
                square(at, Color.fromRGB(0xFFD36B), 1.4f, 0.4);
            }
            void chomp(int[] c, int warn) {
                Location at = tile(c);
                Location start = at.clone().add(0, 0, -TILE / 2);
                f.strikeRaw(Shape.LINE, start, 0, TILE, (float) TILE, 0, Math.max(4, warn - 2), -0.3, null, 0, s -> {
                    Fx.effect(f.plugin, "claude:pepgimmick/bite", at, 0, 20);   // 조각 가운데 (예고 시작점은 조각 가장자리)
                    for (Player pl : f.inside(s)) pl.setVelocity(new Vector(0, 0.9, 0).add(flat(pl.getLocation().toVector().subtract(f.center.toVector())).multiply(0.4)));
                });
            }
            void grid() {
                var dust = new Particle.DustOptions(f.white ? Color.fromRGB(0xE3D6C6) : Color.fromRGB(0x3F2218), 1.0f);
                double half = GRID * TILE / 2; World w = f.center.getWorld(); double y = ground(f.center).getY() + 0.15;
                for (int i = 0; i <= GRID; i++) {
                    double o = -half + i * TILE;
                    for (double s = -half; s <= half; s += 0.8) {
                        w.spawnParticle(Particle.DUST, new Location(w, f.center.getX() + o, y, f.center.getZ() + s), 1, 0, 0, 0, 0, dust);
                        w.spawnParticle(Particle.DUST, new Location(w, f.center.getX() + s, y, f.center.getZ() + o), 1, 0, 0, 0, 0, dust);
                    }
                }
            }
            void square(Location at, Color c, float size, double step) {
                var dust = new Particle.DustOptions(c, size); World w = at.getWorld(); double h = TILE / 2 - 0.2;
                for (double x = -h; x <= h; x += step) for (double z = -h; z <= h; z += step) w.spawnParticle(Particle.DUST, at.clone().add(x, 0.2, z), 1, 0, 0, 0, 0, dust);
            }
        }

        /* ---------------------------------------------------------------- 기믹5 혼자는 싫어 */

        static final class Cage extends Gimmick {
            static final int LIMIT = 400, SYNC = 40;
            Player trapped; Location spot; UUID cageFx; Entity lockA, lockB; int deadAt = -999; Entity firstDead; boolean over;
            Cage(Fight f) {
                super(f);
                List<Player> ps = new ArrayList<>(f.players());
                if (ps.isEmpty()) { over = true; return; }
                trapped = ps.get(ReaperBoss.RNG.nextInt(ps.size()));
                f.anim("shield");
                f.title("§d§l혼자는 싫어", "§f" + trapped.getName());
                f.say("너는 여기 있어. 나랑 같이. …혼자는 싫단 말이야.");
                spot = ground(trapped.getLocation());
                cageFx = Fx.held("claude:pepgimmick/cage", spot, 0, LIMIT + 20);
                float yaw = ReaperBoss.RNG.nextFloat() * 360;
                lockA = spawnLock(ground(spot.clone().add(dir(yaw).multiply(6))));
                lockB = spawnLock(ground(spot.clone().add(dir(yaw + 180).multiply(6))));
            }
            Entity spawnLock(Location at) { if (ReaperBoss.flat2(at, f.center) > (f.arena - 1) * (f.arena - 1)) at = ground(f.center.clone().add(at.toVector().subtract(f.center.toVector()).setY(0).normalize().multiply(f.arena - 2))); return f.prop("CA_Pep_heart_lock", at, "lock"); }
            @Override boolean patterns() { return true; }
            @Override boolean step() {
                super.step();
                if (over) return false;
                if (!alive(trapped)) return finish(false);
                if (trapped.getLocation().distanceSquared(spot) > 1.0) { Location back = spot.clone(); back.setYaw(trapped.getLocation().getYaw()); back.setPitch(trapped.getLocation().getPitch()); trapped.teleport(back); }
                if (age % 20 == 0) { f.hurt(trapped, -0.05); f.titleTo(trapped, "", "§d" + Math.max(0, (LIMIT - age) / 20) + "초"); }
                if (age >= LIMIT) { f.kill(trapped, "하트 우리에서 나오지 못했다"); return finish(false); }
                if (firstDead != null && f.t - deadAt > SYNC) {   // 하나만 부서짐 → 다시 붙음
                    Location at = firstDead.getLocation();
                    Entity again = f.prop("CA_Pep_heart_lock", at, "lock");
                    if (firstDead == lockA) lockA = again; else lockB = again;
                    Fx.effect(f.plugin, "claude:pepsweet/summon", at, 0, 20);
                    firstDead = null;
                }
                return true;
            }
            @Override void propGone(Entity prop) {
                if (over || (prop != lockA && prop != lockB)) return;
                Fx.effect(f.plugin, "claude:pepgimmick/lock_break", prop.getLocation(), 0, 20);
                if (firstDead == null || firstDead == prop) { firstDead = prop; deadAt = f.t; return; }
                if (f.t - deadAt <= SYNC) finish(true);
            }
            boolean finish(boolean ok) {
                if (over) return false;
                over = true;
                Fx.stop(cageFx);
                if (ok) {
                    Fx.effect(f.plugin, "claude:pepgimmick/cage_break", spot, 0, 30);
                    f.say("왜… 왜 다들 데리러 오는 건데.");
                    Player p = trapped;
                    f.later(5, () -> { if (alive(p)) f.almond(p, -0.35, false); });
                }
                return false;
            }
            @Override void cleanup() {
                Fx.stop(cageFx);
                for (Entity e : new Entity[]{lockA, lockB}) if (e != null && e.isValid()) { f.props.remove(e); e.remove(); }
            }
        }

        /* ---------------------------------------------------------------- 기믹6 진짜 쇼콜라 찾기 */

        static final class Clones extends Gimmick {
            static final int SHUFFLE = 200, WINDOW = 60;
            final List<Entity> fakes = new ArrayList<>(); final Map<UUID, Integer> lastHit = new HashMap<>();
            double dealt; int shuffles, nextShuffle, nextBlink = 40, nextCast = 40; boolean over;
            Clones(Fight f) {
                super(f);
                f.anim("enrage");
                f.title("§d§l진짜 쇼콜라 찾기", "");
                f.say("나는 어디에도 있고, 어디에도 없어. …진짜 나를 찾아 줄 수 있어?");
                f.later(20, this::spread);
                nextShuffle = SHUFFLE + 20;
            }
            @Override boolean frozen() { return false; }
            void spread() {
                for (Entity e : fakes) if (e.isValid()) { f.props.remove(e); e.remove(); }
                fakes.clear();
                List<Location> spots = new ArrayList<>();
                float base = ReaperBoss.RNG.nextFloat() * 360;
                for (int k = 0; k < 4; k++) spots.add(ground(f.center.clone().add(dir(base + 90 * k).multiply(8))));
                Collections.shuffle(spots, ReaperBoss.RNG);
                Location real = spots.get(0); real.setYaw(yawTo(real, f.center)); f.boss.teleport(real);
                f.boss.setAI(false);
                Fx.effect(f.plugin, "claude:pepgimmick/clone_poof", real, 0, 20);
                for (int k = 1; k < 4; k++) {
                    Location at = spots.get(k); at.setYaw(yawTo(at, f.center));
                    Fx.effect(f.plugin, "claude:pepgimmick/clone_poof", at, 0, 20);
                    Entity e = f.prop(f.white ? "CA_Pep_clone_shiny" : "CA_Pep_clone", at, "clone");
                    if (e != null) fakes.add(e);
                }
                lastHit.clear();
            }
            @Override boolean step() {
                super.step();
                if (over) return false;
                if (age >= nextBlink) { f.anim("blink"); nextBlink = age + 50 + ReaperBoss.RNG.nextInt(30); }
                if (age >= nextCast) { cast(); nextCast = age + 60; }
                if (age >= nextShuffle) {
                    shuffles++;
                    if (shuffles >= 3) { boom(); return false; }
                    f.say("§7(자리를 섞었다 " + shuffles + "/3)");
                    spread(); nextShuffle = age + SHUFFLE;
                }
                return true;
            }
            /** 넷 다 (진짜·가짜) 가까운 사람 발밑에 원 r 4. */
            void cast() {
                List<Player> ps = f.targetable(f.players()); if (ps.isEmpty()) return;
                List<Entity> all = new ArrayList<>(fakes); all.add(f.boss);
                for (Entity e : all) {
                    if (!e.isValid()) continue;
                    Player near = ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(e.getLocation()))).orElse(null);
                    if (near == null) continue;
                    if (e == f.boss) f.anim("puddle"); else f.propAnim(e, "CHA_PEP_a_puddle");
                    f.strikeRaw(Shape.CIRCLE, ground(near.getLocation()), 0, 4, 0, 0, 22, 16, f.vfx("puddle"), 4, null);   // VFX 크기 고정 → 예고도 r4
                }
            }
            /** 진짜가 맞을 때: 3초 안에 전원이 때렸을 때만 피해. */
            void realHit(EntityDamageEvent e) {
                Player by = e instanceof EntityDamageByEntityEvent be ? Hooks.attacker(be) : null;
                if (by == null) { e.setCancelled(true); return; }
                lastHit.put(by.getUniqueId(), f.t);
                List<Player> ps = f.players();
                boolean all = !ps.isEmpty();
                for (Player pl : ps) { Integer h = lastHit.get(pl.getUniqueId()); if (h == null || f.t - h > WINDOW) { all = false; break; } }
                if (!all) { e.setCancelled(true); Fx.effect(f.plugin, "claude:pepgimmick/reflect", f.boss.getLocation(), 0, 10); return; }
                dealt += e.getFinalDamage();
                if (dealt >= f.maxHp() * 0.05) { over = true; f.later(1, this::found); }
            }
            void fakeHit(Player by, Entity fake) {
                Fx.effect(f.plugin, "claude:pepgimmick/reflect", fake.getLocation(), 0, 10);
                f.hurt(by, -0.15);
                if (f.plugin.status() != null) f.plugin.status().apply(f.boss, by, Status.SLOW, 40, 1);
                f.tell(by, "§7그건 내가 아니야.");
            }
            void found() {
                for (Entity e : fakes) if (e.isValid()) { Fx.effect(f.plugin, "claude:pepgimmick/clone_poof", e.getLocation(), 0, 20); f.props.remove(e); e.remove(); }
                fakes.clear();
                f.say("…찾았네. 진짜 나를.");
            }
            void boom() {
                over = true;
                List<Location> at = new ArrayList<>(); for (Entity e : fakes) if (e.isValid()) at.add(ground(e.getLocation())); at.add(ground(f.boss.getLocation()));
                f.say("아무도 진짜 나를 못 찾았어!");
                for (Location l : at) f.strikeRaw(Shape.CIRCLE, l, 0, 9, 0, 0, 24, -0.6, f.vfx("fondue"), 9, null);   // VFX 크기 고정 → 예고도 r9
                f.later(30, () -> { for (Entity e : fakes) if (e.isValid()) { f.props.remove(e); e.remove(); } fakes.clear(); });
            }
            @Override void cleanup() { for (Entity e : fakes) if (e.isValid()) { f.props.remove(e); e.remove(); } fakes.clear(); }
        }

        /* ---------------------------------------------------------------- 기믹7 마지막 한 입 (광폭) */

        void startLast() {
            if (finale != null) return;
            enraged = true; phase2 = true; rotateAt = Integer.MAX_VALUE;
            anim("enrage");
            Fx.effect(plugin, "claude:pepsweet/enrage", ground(boss.getLocation()), 0, 40);
            title("§4§l마지막 한 입", "§f60초");
            say("싫어… 혼자는 싫단 말이야! 다 같이 초콜릿에 잠겨 버려!");
            finale = new Last(this);
        }
        static final class Last {
            static final int LIMIT = 1200;
            final Fight f; int age, nextMini = 300;
            Last(Fight f) { this.f = f; }
            double safe() { return Math.max(4, f.arena - 2 * (age / 200)); }
            void step() {
                age++;
                double r = safe();
                if (age % 10 == 0) DemonFight.ringFx(f.center, r, f.white ? Color.fromRGB(0xF3EBE1) : Color.fromRGB(0x5B3222));
                if (age % 200 == 0 && age < LIMIT) { Fx.effect(f.plugin, "claude:pepgimmick/flood", ground(f.center), 0, 40); f.title("", "§6초코가 차오른다 §f" + (LIMIT - age) / 20 + "초"); }
                if (age % 20 == 0) for (Player pl : f.players()) if (ReaperBoss.flat2(pl.getLocation(), f.center) > r * r) {
                    f.hurt(pl, -0.08); if (f.plugin.status() != null) f.plugin.status().apply(f.boss, pl, Status.SLOW, 25, 1);
                }
                if (f.gimmick == null && age >= nextMini && age < LIMIT - 100) {
                    int k = ReaperBoss.RNG.nextInt(3);
                    f.gimmick = k == 0 ? new Share(f) : k == 1 && f.players().size() >= 2 ? new Pocky(f) : new Bite(f, 5, 1);
                    nextMini = age + 300;
                }
                if (age == LIMIT) {
                    Fx.effect(f.plugin, "claude:pepgimmick/flood", ground(f.center), 0, 60);
                    f.say("…이제 아무 데도 못 가. 다 같이, 여기 있자.");
                    for (Player pl : f.players()) f.kill(pl, "초콜릿에 잠겼다");
                }
            }
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
        /** 수평 단위 벡터 (0 이면 +X — 정확히 같은 자리에서 normalize 하면 NaN → setVelocity 오류). */
        static Vector flat(Vector v) { v.setY(0); return v.lengthSquared() < 1e-4 ? new Vector(1, 0, 0) : v.normalize(); }
        static Location ground(Location at) { return ReaperBoss.ground(at); }

        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }

        boolean castAt(String skill, Location at) {
            try { return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {}); }
            catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 보스 모델 모션 (CHA_PEP_a_&lt;이름&gt; = state{s=&lt;이름&gt;}). */
        void anim(String name) { castAt("CHA_PEP_a_" + name, boss.getLocation()); }
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
                props.add(e);
                return e;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("쇼콜라 소품 소환 실패: " + mob + " " + ex); return null; }
        }

        void clearFull() { for (UUID h : fullFx.values()) Fx.stop(h); fullFx.clear(); fullUntil.clear(); }

        void reset() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            finale = null; focus = null; clearFull();
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear(); Arrays.fill(gateDone, false);
            phase2 = false; enraged = false; invulnerable = false; rotateAt = Integer.MAX_VALUE; summonAt = t + 400;
            boss.setAI(true); boss.setInvisible(false); last = "";
            boss.setHealth(maxHp()); boss.teleport(center); nextAt = t + 60; emptyFor = 0;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            finale = null; clearFull();
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear();
            FIGHTS.remove(boss.getUniqueId());
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== events */

    static final class Hooks implements Listener {
        /** 3인 던전 보스 공격은 방패로 못 막음 (11/04 호로겐·모르디안 [기능:3인방패관통] 과 같게, 쇼콜라·졸개 모두). */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void pierceShield(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof Player pl) || !pl.isBlocking()) return;
            Entity by = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : e.getDamager();
            if (!FIGHTS.containsKey(by.getUniqueId()) && !by.getScoreboardTags().contains(TAG)) return;
            if (e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            Set<String> tags = e.getEntity().getScoreboardTags();
            if (!tags.contains(TAG)) return;
            if (tags.contains(TAG + ":clone")) {   // 가짜 = 반사
                e.setCancelled(true);
                Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
                if (by == null || spellHit) return;
                for (Fight fight : FIGHTS.values()) if (fight.gimmick instanceof Fight.Clones cl && cl.fakes.contains(e.getEntity())) cl.fakeHit(by, e.getEntity());
                return;
            }
            if (tags.contains(TAG + ":lock")) {   // 갇힌 본인은 자물쇠를 못 침
                Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
                if (by == null) { e.setCancelled(true); return; }
                for (Fight fight : FIGHTS.values()) if (fight.gimmick instanceof Fight.Cage cg && by.equals(cg.trapped) && !solo) e.setCancelled(true);
            }
        }

        /** 갇힌 사람은 보스·졸개 근접에 안 맞음. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void trappedSafe(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof Player pl) || spellHit) return;
            for (Fight f : FIGHTS.values()) if (f.gimmick instanceof Fight.Cage cg && pl.equals(cg.trapped)) { e.setCancelled(true); return; }
        }

        static Player attacker(EntityDamageByEntityEvent e) {
            return e.getDamager() instanceof Player pl ? pl : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f != null) {
                Fx.effect(f.plugin, "claude:pepsweet/death", e.getEntity().getLocation(), 0, 60);
                f.say(f.white ? "…다음 화이트데이엔, 나도 줄게." : "…나한테도, 하나 주는 거야?");
                f.close();
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            e.getDrops().clear(); e.setDroppedExp(0);
            for (Fight fight : FIGHTS.values()) if (fight.gimmick != null) fight.gimmick.propGone(e.getEntity());
        }
    }
}
// [기능:빼빼로데이보스 끝]
