package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.StatusProtocol.Status;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chaelysia - 1인 레이드 "자비의 천사 엘리시아 → 가면의 악마 벨자르" 진행자 (2026-11-09). 미몹 CA_Boss_elysia 의 ~onSpawn 에서
 * {@code chaelysia{a=start;arena=16} @self}. 소환 위치 = 경기장 중심, 소환 때 바라본 방향 = 정면 (석상·석주·봉인진 배치 기준).
 *
 * <p>규칙: 천사가 주는 것은 전부 진짜다. 받을 때마다 빚(✦)이 종류별로 쌓이고(가호·축복·구원·계시), 50%에서 정체를 드러낸 벨자르가
 * 가호 → 축복 → 구원 → 계시 순서로 회수한다. 그 종류의 빚이 0이면 회수는 통째로 생략. 수금(장부 내리찍기)에 맞으면 다음 회수 종류부터 1씩 갚는다.
 *
 * <ol>
 *   <li>85% ① 가호의 제안: 심판의 빛 3파. 금빛 원 안 = 무효 + 가호 빚, 석상 그늘 = 무효. 둘 다 아니면 사망.</li>
 *   <li>70% ② 축복의 제안: 빛의 성벽 25초. 금빛 깃털을 주우면 축복(피해 1.5배, 성벽에 3배) + 빚 2. 아니면 빛의 핵 3개를 4초 안에 모두 치기.</li>
 *   <li>1페이즈 상시 ③ 구원의 제안: 치명상 순간 천사의 손. 5초 안에 손을 우클릭하면 부활 + 빚 3 (1회).</li>
 *   <li>55% ④ 계시의 제안: 3음 찬송 → 석주 3개를 역순으로. 보스 발밑 금빛 원에 서면 답이 보이고 빚 1. 틀리거나 20초 = 사망.</li>
 *   <li>1페이즈 상시: 치유의 성역 (1초마다 6% 회복, 3번마다 가호 빚 1). 성가대 아기천사 (처치 시 은총의 깃털 = 회복 + 성역 1번).</li>
 *   <li>50% 청구서: 정체 폭로 → 벨자르 (다른 미몹으로 교체, 체력 50%). 장부 낭독. 받은 게 없으면 계약 불성립 = 체력 -10%, 회수 전부 생략.</li>
 *   <li>45% ⓐ 그림자 몰수 / 38% ⓑ 빌린 힘의 반환 / 30% ⓒ 목숨 회수 / 22% ⓓ 눈 몰수.</li>
 *   <li>15% 계약 파기: 50초, 경기장이 바깥부터 무너짐. 빚을 0으로 → 봉인진 3개를 차례로 3초씩 → 장부가 불타며 처치. 시간 초과 = 사망.</li>
 * </ol>
 * 규칙·기믹 설명 타이틀은 띄우지 않는다 (사장님 지시 2026-11-09). 보스 대사(제안)만 하고, 실패했을 때만 그 사람에게 짧은 단서를 준다.
 * 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX claude:elysia_* / claude:belzar_* (예고 없음). 서버 부담: 보스당 Ticker 1개 (판단 5틱, 기믹만 매 틱).
 */
final class ElysiaBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_elysia_prop";
    static final String N_ELYSIA = "§e[§f엘리시아§e] §f", N_BELZAR = "§4[§c벨자르§4] §f";
    static boolean spellHit, test;

    ElysiaBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

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

    static final List<String> PATTERN_IDS = List.of("spear", "judgment", "sweep", "radiance", "sanctuary", "claw", "tail", "hellfire", "collect");
    static final String[] DEBT_NAME = {"가호", "축복", "구원", "계시"};

    /** /테스트 보스 엘리시아 &lt;무엇&gt; [값] [값2] - 64칸 안 가장 가까운 엘리시아/벨자르. */
    static String test(Player p, String what, String arg, String arg2) {
        if (what.equals("시험")) {
            test = arg.equals("켜기");
            return test ? "시험 모드 켬: 즉사 대신 알림 (단서도 같이 보여 줌)" : "시험 모드 끔";
        }
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 엘리시아가 없어요 (/mm mobs spawn CA_Boss_elysia - 소환한 자리가 경기장 중심, 바라본 방향이 정면)";
        switch (what) {
            case "패턴" -> {
                List<Player> ps = f.players();
                if (ps.isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 기믹·연출 중이라 패턴을 못 써요";
                boolean p2id = Set.of("claw", "tail", "hellfire", "collect").contains(arg);
                if (p2id != f.demon) return f.demon ? "벨자르 패턴만 쓸 수 있어요 (claw·tail·hellfire·collect)" : "엘리시아 패턴만 쓸 수 있어요 (spear·judgment·sweep·radiance·sanctuary)";
                f.run(arg, ps); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "기믹" -> {
                int k = Math.clamp(Integer.parseInt(arg), 1, 9) - 1;
                if (f.gimmick != null || f.phase != Fight.FIGHTING) return "지금은 기믹·연출 중이에요";
                if (k >= 4 && !f.demon) return "회수 기믹(5~9)은 정체 폭로 뒤에만 (먼저 /테스트 보스 엘리시아 폭로)";
                if (k <= 3 && f.demon) return "제안 기믹(1~4)은 엘리시아 때만 (초기화하고 다시)";
                for (int i = 0; i < k; i++) f.gateDone[i] = true;
                f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * Fight.GATES[k])));
                f.startGimmick(k);
                return "기믹 " + (k + 1) + " 시작";
            }
            case "폭로" -> { if (f.demon) return "이미 벨자르예요"; for (int i = 0; i < 3; i++) f.gateDone[i] = true; f.startGimmick(3); return "정체 폭로 (빚 그대로)"; }
            case "빚" -> {
                int type = Arrays.asList(DEBT_NAME).indexOf(arg);
                if (type < 0) return "종류: 가호 · 축복 · 구원 · 계시";
                f.debt(p)[type] = Math.max(0, Integer.parseInt(arg2)); f.refresh(p);
                return arg + " 빚을 " + f.debt(p)[type] + " 으로";
            }
            default -> { f.reset(); return "처음 상태로 (엘리시아, 체력 가득, 빚 0)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight implements Ticker.Tick {
        static final int FIGHTING = 1, P_GIM = 2, P_END = 3;
        /** 0 가호 · 1 축복 · 2 계시 · 3 정체 폭로 · 4 그림자 몰수 · 5 빌린 힘의 반환 · 6 목숨 회수 · 7 눈 몰수 · 8 계약 파기 */
        static final double[] GATES = {0.85, 0.70, 0.55, 0.50, 0.45, 0.38, 0.30, 0.22, 0.15};

        final MagicMythicPlugin plugin; LivingEntity boss; final Location center; final double arena; final float front;
        int t, nextAt = 70, emptyFor, phase = FIGHTING, sanctuaryAt = 300, minionAt = 400;
        final boolean[] gateDone = new boolean[9];
        boolean invulnerable, closed, demon, salvOffered, pure;
        String last = "";
        Gimmick gimmick;
        final List<Entity> props = new ArrayList<>();
        final List<Entity> statues = new ArrayList<>();

        /* 빚 (플레이어별, 종류별) */
        final Map<UUID, int[]> debts = new HashMap<>();
        final Map<UUID, Integer> graceSteps = new HashMap<>();
        final Map<UUID, TextDisplay> holos = new HashMap<>();
        final Set<UUID> blessed = new HashSet<>();
        final Map<UUID, UUID> blessFx = new HashMap<>();
        final Set<UUID> drained = new HashSet<>();
        final Map<UUID, Fx.Link> chains = new HashMap<>();
        double blessDealt;
        int chainAge;
        Sanctuary sanctuary;
        UUID shieldFx; double shieldHp; int shieldUntil;

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena) {
            this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena; this.front = center.getYaw();
            Fx.effect(plugin, "claude:elysia_judgment/judgment", ground(center), front, 60);
            anim("spawn");
            later(30, this::placeStatues);
            later(50, () -> {
                say("길 잃은 아이야, 두려워 말거라. 나는 그대를 도우러 왔으니.");
            });
        }

        void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize((demon ? N_BELZAR : N_ELYSIA) + line)); }
        void tell(Player pl, String line) { pl.sendMessage(ReaperBoss.LEG.deserialize((demon ? N_BELZAR : N_ELYSIA) + line)); }
        void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }
        /** 실패한 사람에게만 주는 짧은 단서 (규칙 설명은 하지 않는다). */
        void hint(Player pl, String clue) { pl.sendMessage(ReaperBoss.LEG.deserialize("§8§o" + clue)); }

        @Override public String kind() { return "boss"; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (gimmick != null && !gimmick.step()) { Gimmick g = gimmick; gimmick = null; g.cleanup(); }
            if (sanctuary != null && !sanctuary.step()) { sanctuary.close(); sanctuary = null; }
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            pickups(ps);
            holoFollow(ps);
            if (demon) { chains(ps); shieldTick(); }
            if (phase == FIGHTING) {
                if (!demon && sanctuary == null && t >= sanctuaryAt) { sanctuary = new Sanctuary(this, ps); sanctuaryAt = t + 280; }
                if (t >= minionAt) { minions(); minionAt = t + (demon ? 420 : 500); }
            }
            if (free() && t >= nextAt) pattern(ps);
            return true;
        }

        double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
        boolean free() { return !closed && (phase == FIGHTING || (phase == P_GIM && gimmick != null && gimmick.patterns())); }

        List<Player> players() {
            List<Player> out = new ArrayList<>();
            double r = arena + 6;
            for (Player pl : boss.getWorld().getPlayers()) {
                if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
                if (ReaperBoss.flat2(pl.getLocation(), center) <= r * r && Math.abs(pl.getLocation().getY() - center.getY()) < 24) out.add(pl);
            }
            return out;
        }

        /* ---------------------------------------------------------------- 빚 */

        int[] debt(Player pl) { return debts.computeIfAbsent(pl.getUniqueId(), u -> new int[4]); }
        int total(Player pl) { int s = 0; for (int v : debt(pl)) s += v; return s; }
        void gain(Player pl, int type, int n) {
            debt(pl)[type] += n;
            Fx.follow(pl, "claude:elysia_debtmark/gain", 30, 0);
            pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            refresh(pl);
        }
        /** 다음에 올 회수 종류부터 1 갚는다 (회수가 다 지나갔으면 남은 아무 종류). */
        boolean pay(Player pl) {
            int[] d = debt(pl);
            int type = -1;
            for (int k = 4; k <= 7 && type < 0; k++) if (!gateDone[k] && d[k - 4] > 0) type = k - 4;
            for (int k = 0; k < 4 && type < 0; k++) if (d[k] > 0) type = k;
            if (type < 0) return false;
            d[type]--;
            Fx.follow(pl, "claude:elysia_debtmark/pay", 24, 0);
            pl.playSound(pl.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.8f);
            refresh(pl);
            return true;
        }
        /** 머리 위 ✦ 표시 (1페이즈 금색, 2페이즈 붉은색). */
        void refresh(Player pl) {
            int n = total(pl);
            TextDisplay td = holos.get(pl.getUniqueId());
            if (n <= 0) { if (td != null) { td.remove(); holos.remove(pl.getUniqueId()); } return; }
            if (td == null || !td.isValid()) {
                td = pl.getWorld().spawn(pl.getLocation().add(0, 2.35, 0), TextDisplay.class, d -> {
                    d.setBillboard(Display.Billboard.CENTER); d.setPersistent(false); d.setShadowed(true);
                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); d.addScoreboardTag(TAG);
                });
                holos.put(pl.getUniqueId(), td);
            }
            String star = (demon ? "§c" : "§6") + "✦".repeat(Math.min(n, 12)) + (n > 12 ? " §7×" + n : "");
            td.text(ReaperBoss.LEG.deserialize(star));
        }
        void holoFollow(List<Player> ps) {
            for (Player pl : ps) { TextDisplay td = holos.get(pl.getUniqueId()); if (td != null && td.isValid()) td.teleport(pl.getLocation().add(0, 2.35, 0)); }
        }

        /** 빚의 사슬: 빚이 있으면 보스와 이어짐. 빚이 많을수록 짧다 (16 - 1.5 × 빚, 최소 5). 넘으면 끌려옴 + 작은 피해. */
        void chains(List<Player> ps) {
            chainAge += 5;
            for (Player pl : ps) {
                int n = total(pl);
                Fx.Link l = chains.get(pl.getUniqueId());
                if (n <= 0) { if (l != null) { l.close(); chains.remove(pl.getUniqueId()); } continue; }
                if (l == null || chainAge >= 180) { if (l != null) l.close(); l = Fx.link("claude:elysia_debtmark/chain", chest(pl), chest(boss)); if (l != null) chains.put(pl.getUniqueId(), l); }
                else l.move(chest(pl), chest(boss));
                double max = Math.max(5, 16 - 1.5 * n), d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), boss.getLocation()));
                if (d > max) {
                    Vector to = boss.getLocation().toVector().subtract(pl.getLocation().toVector()).setY(0).normalize();
                    pl.setVelocity(to.multiply(Math.min(1.2, 0.35 + (d - max) * 0.15)).setY(0.12));
                    if (t % 20 == 0) hurtPct(pl, 0.03);
                }
            }
            if (chainAge >= 180) chainAge = 0;
        }
        void dropChains() { for (Fx.Link l : chains.values()) if (l != null) l.close(); chains.clear(); }

        /* ---------------------------------------------------------------- 패턴 */

        record Pat(String id, int weight) {}
        static final List<Pat> ELY = List.of(new Pat("spear", 3), new Pat("judgment", 3), new Pat("sweep", 2), new Pat("radiance", 2));
        static final List<Pat> BEL = List.of(new Pat("claw", 3), new Pat("tail", 2), new Pat("hellfire", 3), new Pat("collect", 2));

        void pattern(List<Player> ps) {
            List<Pat> pool = new ArrayList<>();
            boolean anyDebt = ps.stream().anyMatch(pl -> total(pl) > 0);
            for (Pat pt : demon ? BEL : ELY) if (!pt.id().equals(last)) pool.add(pt.id().equals("collect") && anyDebt ? new Pat("collect", 4) : pt);
            if (pool.isEmpty()) return;
            int sum = pool.stream().mapToInt(Pat::weight).sum(), roll = ReaperBoss.RNG.nextInt(sum);
            Pat pick = pool.get(0);
            for (Pat pt : pool) { roll -= pt.weight(); if (roll < 0) { pick = pt; break; } }
            run(pick.id(), ps);
            if (shieldFx != null) nextAt = t + (int) ((nextAt - t) * 0.6);   // 빌린 힘이 남아 있는 동안 더 빠르게
        }

        void run(String id, List<Player> ps) {
            if (ps.isEmpty()) { nextAt = t + 20; return; }
            Player near = nearest(ps);
            int len = switch (id) {
                case "spear" -> spear(near);
                case "judgment" -> judgment(ps, "claude:elysia_judgment/judgment", 24);
                case "sweep" -> sweep(near);
                case "radiance" -> radiance();
                case "sanctuary" -> { if (sanctuary != null) sanctuary.close(); sanctuary = new Sanctuary(this, ps); yield 20; }
                case "claw" -> behind(near) ? tail(near) : claw(near);
                case "tail" -> tail(near);
                case "hellfire" -> judgment(ps, "claude:belzar_hellfire/hellfire", 26);
                case "collect" -> collect(near);
                default -> 20;
            };
            last = id;
            nextAt = t + len + 16;
        }

        /** 빛의 창 3자루: 직선 18 · 폭 1.6 (가운데 + ±14°), 예고 0.6초. 손에서 0.2초에 던짐. */
        int spear(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("attack");
            Location from = ground(boss.getLocation()); float base = yawTo(from, target.getLocation());
            for (int i = -1; i <= 1; i++) {
                float yaw = base + i * 14;
                double len = clampLen(from, dir(yaw), 18);
                Location end = ground(from.clone().add(dir(yaw).multiply(len))).add(0, 0.2, 0);
                later(4, () -> Fx.fall(plugin, "claude:elysia_lightspear/projectile", hand(), end, 8));
                strike(Shape.LINE, from, yaw, len, 1.6, 0, 12, 22, null, 0, s -> {
                    List<Player> hit = inside(s);
                    Location at = hit.isEmpty() ? ground(end) : ground(hit.get(0).getLocation());
                    Fx.effect(plugin, "claude:elysia_lightspear/impact", at, yaw, 40);
                });
            }
            return 30;
        }
        /** 낙하 3번: 그때 플레이어 발밑 원 r 2.5, 0.6초 간격. VFX 는 0.35초 뒤 떨어지므로 판정 7틱 전에 시작. */
        int judgment(List<Player> ps, String vfx, double dmg) {
            anim("cast");
            for (int k = 0; k < 3; k++) {
                later(1 + 12 * k, () -> {
                    for (Player pl : players()) strike(Shape.CIRCLE, ground(pl.getLocation()), 0, 2.5, 0, 0, 18, dmg, vfx, 7, null);
                });
            }
            return 50;
        }
        /** 날개 베기: 부채꼴 r 6.5 · 140°, 예고 0.5초. */
        int sweep(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("attack");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 6.5, 0, 140, 10, 26, null, 0, s -> Fx.effect(plugin, "claude:elysia_lightspear/impact", ground(at.clone().add(dir(yaw).multiply(3))), yaw, 40));
            return 26;
        }
        /** 광휘: 보스 중심 원 r 5.5, 예고 0.8초 (빛의 핵 폭발 VFX 를 몸에서). */
        int radiance() {
            anim("cast");
            Location c = ground(boss.getLocation());
            strike(Shape.CIRCLE, c, 0, 5.5, 0, 0, 16, 28, null, 0, s -> Fx.effect(plugin, "claude:elysia_lightcore/break", boss.getLocation().add(0, 2.0, 0), 0, 40));
            later(14, () -> Fx.effect(plugin, "claude:elysia_lightcore/hit", boss.getLocation().add(0, 2.0, 0), 0, 20));
            return 34;
        }
        /** 할퀴기: 앞 부채꼴 r 7 · 150°, 예고 0.45초. */
        int claw(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("attack");
            Location at = ground(boss.getLocation()); float yaw = yawTo(at, target.getLocation());
            strike(Shape.CONE, at, yaw, 7, 0, 150, 9, 26, "claude:belzar_strikes/claw", 2, null);
            return 26;
        }
        /** 꼬리 휩쓸기: 뒤쪽 부채꼴 r 6.5 · 160°, 예고 0.5초 (뒤잡는 사람용). */
        int tail(Player target) {
            anim("tail_swing");
            Location at = ground(boss.getLocation()); float yaw = boss.getLocation().getYaw() + 180;
            strike(Shape.CONE, at, yaw, 6.5, 0, 160, 10, 24, null, 0, s -> Fx.effect(plugin, "claude:belzar_strikes/tail", at, boss.getLocation().getYaw(), 40));
            return 28;
        }
        /** 수금: 그 자리 원 r 3, 예고 1초. 빚이 있는 사람이 안에서 맞으면 최대 체력 18% + 빚 1 상환, 없으면 그냥 24. */
        int collect(Player target) {
            if (target == null) return 10;
            face(target.getLocation()); anim("collect");
            Location at = ground(target.getLocation());
            strike(Shape.CIRCLE, at, 0, 3, 0, 0, 20, 0, null, 0, s -> {
                Fx.effect(plugin, "claude:belzar_collection/collect", at, 0, 40);
                spellHit = true;
                try {
                    for (Player pl : inside(s)) {
                        if (total(pl) > 0) { hurtPct(pl, 0.18); pay(pl); }
                        else plugin.targets().hurt(boss, pl, 24, "dark");
                    }
                } finally { spellHit = false; }
            });
            return 40;
        }
        boolean behind(Player pl) {
            if (pl == null) return false;
            float toPl = yawTo(boss.getLocation(), pl.getLocation()), facing = boss.getLocation().getYaw();
            float diff = Math.abs(((toPl - facing) % 360 + 540) % 360 - 180);
            return diff > 110 && pl.getLocation().distanceSquared(boss.getLocation()) < 49;
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
                if (closed || (!force && !free())) return;
                if (vfx != null && lead <= 0) Fx.effect(plugin, vfx, at, yaw, 50);
                if (dmg > 0) {
                    spellHit = true;
                    try { for (Player pl : inside(show)) plugin.targets().hurt(boss, pl, dmg, demon ? "dark" : "light"); } finally { spellHit = false; }
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
            try { plugin.targets().hurt(boss, pl, ReaperBoss.Fight.maxOf(pl) * pct, demon ? "dark" : "light"); } finally { spellHit = false; }
        }
        double clampLen(Location from, Vector dir, double want) {
            for (double s = 1; s <= want; s += 1) if (ReaperBoss.flat2(from.clone().add(dir.clone().multiply(s)), center) > (arena - 0.5) * (arena - 0.5)) return Math.max(4, s - 1);
            return want;
        }

        /* ---------------------------------------------------------------- 상시: 치유의 성역 · 잡몹 · 줍기 */

        /** 치유의 성역: 무작위 위치 반경 2, 7초. 안에 있으면 1초마다 최대 체력 6% 회복, 3번마다 가호 빚 1 (조용히). */
        static final class Sanctuary {
            final Fight f; final Location at; final UUID hold; int age;
            Sanctuary(Fight f, List<Player> ps) {
                this.f = f;
                double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, r = 4 + ReaperBoss.RNG.nextDouble() * (f.arena - 7);
                at = ground(f.center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                Fx.effect(f.plugin, "claude:elysia_sanctuary/begin", at, 0, 50);
                hold = Fx.held("claude:elysia_sanctuary/sustain", at, 0, 200);
                if (!f.gateDone[0] && f.t < 400) f.say("지친 이여, 쉬어 가거라.");
            }
            boolean step() {
                if (++age >= 140 || f.phase != FIGHTING || f.demon) return false;
                if (age % 20 != 0) return true;
                for (Player pl : f.players()) {
                    if (ReaperBoss.flat2(pl.getLocation(), at) > 2.2 * 2.2) continue;
                    double max = ReaperBoss.Fight.maxOf(pl);
                    pl.setHealth(Math.min(max, pl.getHealth() + max * 0.06));
                    f.step(pl);
                }
                return true;
            }
            void close() { Fx.stop(hold); Fx.effect(f.plugin, "claude:elysia_sanctuary/end", at, 0, 40); }
        }
        /** 성역 한 번 = 걸음 1. 3걸음마다 가호 빚 1. */
        void step(Player pl) {
            int n = graceSteps.merge(pl.getUniqueId(), 1, Integer::sum);
            if (n >= 3) { graceSteps.put(pl.getUniqueId(), 0); gain(pl, 0, 1); }
        }

        /** 1페이즈 아기천사 2마리 / 2페이즈 임프 2마리 + 망령 1마리 (각 종류 최대 3). */
        void minions() {
            if (demon) {
                for (int i = 0; i < 2 && count("imp") < 3; i++) minion(ReaperBoss.RNG.nextInt(8) == 0 ? "CA_Belzar_imp_shiny" : "CA_Belzar_imp", "imp");
                if (count("wraith") < 2) minion(ReaperBoss.RNG.nextInt(8) == 0 ? "CA_Belzar_wraith_shiny" : "CA_Belzar_wraith", "wraith");
            } else {
                for (int i = 0; i < 2 && count("cherub") < 3; i++) minion(ReaperBoss.RNG.nextInt(8) == 0 ? "CA_Elysia_cherub_shiny" : "CA_Elysia_cherub", "cherub");
            }
        }
        int count(String kind) { int n = 0; for (Entity e : props) if (e.isValid() && e.getScoreboardTags().contains(TAG + ":" + kind)) n++; return n; }
        void minion(String mob, String kind) {
            double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2;
            Location at = ground(boss.getLocation().add(Math.cos(a) * 3, 0, Math.sin(a) * 3));
            Entity e = prop(mob, at, kind);
            if (e != null) e.addScoreboardTag(TAG + ":minion");
        }
        /** 잡몹 처치: 아기천사 → 은총의 깃털, 망령 → 상환 증서 (바닥에 떠 있다가 가까이 가면 주움). */
        void minionDied(Entity e) {
            Location at = ground(e.getLocation()).add(0, 0.3, 0);
            if (e.getScoreboardTags().contains(TAG + ":cherub")) prop("CA_Elysia_grace_feather", at, "drop_feather");
            if (e.getScoreboardTags().contains(TAG + ":wraith")) prop("CA_Elysia_deed", at, "drop_deed");
        }
        void pickups(List<Player> ps) {
            for (Entity e : List.copyOf(props)) {
                if (!e.isValid()) continue;
                boolean feather = e.getScoreboardTags().contains(TAG + ":drop_feather"), deed = e.getScoreboardTags().contains(TAG + ":drop_deed"),
                        offer = e.getScoreboardTags().contains(TAG + ":bless_feather");
                if (!feather && !deed && !offer) continue;
                for (Player pl : ps) {
                    if (pl.getLocation().distanceSquared(e.getLocation()) > 1.6 * 1.6) continue;
                    propAnim(e, "CHA_EL_p_pickup");
                    if (feather) { double max = ReaperBoss.Fight.maxOf(pl); pl.setHealth(Math.min(max, pl.getHealth() + max * 0.10)); step(pl); }
                    if (deed) pay(pl);
                    if (offer && gimmick instanceof Blessing b) b.accept(pl);
                    Entity gone = e; later(12, () -> removeProp(gone));
                    e.removeScoreboardTag(TAG + ":drop_feather"); e.removeScoreboardTag(TAG + ":drop_deed"); e.removeScoreboardTag(TAG + ":bless_feather");
                    break;
                }
            }
        }
        /** 임프에게 닿음 = 이자 (빚이 있는 종류에만 +1, 빚이 없으면 피해만). */
        void interest(Player pl) {
            int[] d = debt(pl);
            for (int k = 4; k <= 7; k++) if (!gateDone[k] && d[k - 4] > 0) { gain(pl, k - 4, 1); return; }
            for (int k = 0; k < 4; k++) if (d[k] > 0) { gain(pl, k, 1); return; }
        }

        /* ---------------------------------------------------------------- 석상 (가호 · 그림자 몰수) */

        void placeStatues() {
            for (Entity e : statues) removeProp(e);
            statues.clear();
            for (int i = 0; i < 4; i++) {
                float yaw = front + 45 + i * 90;
                Location at = ground(center.clone().add(dir(yaw).multiply(8))); at.setYaw(yaw + 180);
                Entity e = prop("CA_Elysia_statue", at, "statue");
                if (e != null) statues.add(e);
            }
        }
        /** 빛(보스 위 하늘)에서 플레이어로 가는 선을 석상이 가리면 그늘. */
        boolean shaded(Player pl, Location light) {
            Location p = pl.getLocation();
            for (Entity s : statues) {
                if (!s.isValid() || s.getScoreboardTags().contains(TAG + ":broken")) continue;
                Location c = s.getLocation();
                double bx = light.getX(), bz = light.getZ(), px = p.getX(), pz = p.getZ();
                double dx = px - bx, dz = pz - bz, len2 = dx * dx + dz * dz;
                if (len2 < 1e-6) continue;
                double tt = ((c.getX() - bx) * dx + (c.getZ() - bz) * dz) / len2;
                if (tt <= 0 || tt >= 1) continue;
                double qx = bx + dx * tt, qz = bz + dz * tt, perp = Math.hypot(c.getX() - qx, c.getZ() - qz);
                if (perp < 1.15 && Math.hypot(px - c.getX(), pz - c.getZ()) < 4.5) return true;
            }
            return false;
        }

        /* ---------------------------------------------------------------- 체력 기준선 · 기믹 */

        void damaged(EntityDamageEvent e) {
            if (phase == P_END || invulnerable) { e.setCancelled(true); if (gimmick != null) gimmick.bossHit(e); return; }
            Player by = e instanceof EntityDamageByEntityEvent be ? Hooks.attacker(be) : null;
            if (by == null) { List<Player> ps = players(); if (ps.size() == 1) by = ps.get(0); }
            if (shieldFx != null) {   // ⓑ 빌린 힘: 보호막이 먼저 받는다
                e.setCancelled(true);
                shieldHp -= e.getDamage();
                Fx.effect(plugin, "claude:elysia_lightcore/hit", boss.getLocation().add(0, 2, 0), 0, 20);
                if (shieldHp <= 0) breakShield(true);
                return;
            }
            if (!demon && by != null && blessed.contains(by.getUniqueId())) { double bonus = e.getDamage() * 0.5; e.setDamage(e.getDamage() * 1.5); blessDealt += bonus; }
            double after = boss.getHealth() - e.getFinalDamage();
            for (int k = 0; k < GATES.length; k++) {
                if (gateDone[k]) continue;
                double line = GATES[k] * maxHp();
                if (after <= line) { e.setCancelled(true); boss.setHealth(Math.max(1, line)); startGimmick(k); }
                return;
            }
            if (after <= 1) { e.setCancelled(true); boss.setHealth(1); }   // 15% 아래는 계약 파기로만 끝남
        }

        void startGimmick(int k) {
            gateDone[k] = true; phase = P_GIM; invulnerable = true; nextAt = t + 60;
            if (sanctuary != null && k <= 3) { sanctuary.close(); sanctuary = null; }
            Player any = players().isEmpty() ? null : players().get(0);
            switch (k) {
                case 0 -> gimmick = new Grace(this);
                case 1 -> gimmick = new Blessing(this);
                case 2 -> gimmick = new Revelation(this);
                case 3 -> { gimmick = null; reveal(); }
                case 4 -> gimmick = skip(4, 0, "…그늘은 빌린 적이 없군.") ? null : new Confiscate(this);
                case 5 -> { gimmick = null; if (!skip(5, 1, "…힘은 빌린 적이 없군.")) borrowedPower(); else gimmickDone(5, true); }
                case 6 -> gimmick = skip(6, 2, "…목숨은 빌린 적이 없군.") ? null : new Seize(this, false);
                case 7 -> gimmick = skip(7, 3, "…눈은 빌린 적이 없군.") ? null : new Seize(this, true);
                default -> gimmick = new Contract(this);
            }
            if (gimmick == null && k >= 4 && k != 5 && phase == P_GIM) gimmickDone(k, true);
        }
        /** 회수 생략: 아무도 그 빚이 없거나 계약 불성립이면 한 줄만 하고 넘어감. */
        boolean skip(int k, int type, String line) {
            if (pure) return true;
            for (Player pl : players()) if (debt(pl)[type] > 0) return false;
            say("§7" + line);
            return true;
        }
        void gimmickDone(int k, boolean success) {
            invulnerable = false;
            boss.setAI(true);
            if (phase != P_END) { phase = FIGHTING; nextAt = t + 30; }
        }

        /** 기믹 시작 공통: 보스를 중앙으로 (빛기둥 / 지옥불 속에서 내려섬), 제자리 무적. */
        void toCenter(Runnable then) {
            boss.setAI(false);
            Fx.effect(plugin, demon ? "claude:belzar_hellfire/hellfire" : "claude:elysia_judgment/judgment", ground(boss.getLocation()), 0, 40);
            later(8, () -> {
                Location home = center.clone(); boss.teleport(home);
                Fx.effect(plugin, demon ? "claude:belzar_hellfire/hellfire" : "claude:elysia_judgment/judgment", ground(home), front, 40);
                later(14, then);
            });
        }

        /* ---------------------------------------------------------------- 50% 청구서 (정체 폭로) */

        void reveal() {
            phase = P_END; invulnerable = true; nextAt = Integer.MAX_VALUE;
            boss.setAI(false);
            for (Entity e : List.copyOf(props)) if (e.getScoreboardTags().contains(TAG + ":minion")) removeProp(e);
            Location c = center.clone(); boss.teleport(c);
            say("…연극은 여기까지다.");
            anim("reveal");
            Fx.effect(plugin, "claude:elysia_reveal/reveal", ground(c), front, 100);
            for (UUID u : List.copyOf(blessFx.keySet())) { Player pl = Bukkit.getPlayer(u); Fx.stop(blessFx.get(u)); if (pl != null) Fx.follow(pl, "claude:elysia_blessing/end", 30, 0); }
            blessFx.clear(); blessed.clear();
            later(56, this::swapToBelzar);
        }
        void swapToBelzar() {
            Location at = boss.getLocation().clone();
            LivingEntity old = boss, nb = null;
            try {
                var am = MythicBukkit.inst().getMobManager().spawnMob("CA_Boss_belzar", at);
                if (am != null && am.getEntity().getBukkitEntity() instanceof LivingEntity le) nb = le;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("벨자르 소환 실패: " + ex); }
            if (nb == null) { phase = FIGHTING; invulnerable = false; boss.setAI(true); return; }
            FIGHTS.remove(old.getUniqueId());
            boss = nb; demon = true;
            FIGHTS.put(boss.getUniqueId(), this);
            setYaw(boss, front);
            boss.setHealth(Math.max(1, maxHp() * GATES[3]));
            boss.addScoreboardTag("chaca_boss_next:" + old.getUniqueId());   // 11/09 던전이 새 몸(벨자르)을 이 판 보스로 이어 받게 (ChacaDungeon 던전보스이어받기)
            old.remove();
            boss.setAI(false);
            anim("spawn");
            List<Player> ps = players();
            for (Player pl : ps) refresh(pl);
            later(30, () -> {
                say("자, 장부를 펼쳐 볼까.");
            });
            later(70, () -> {
                boolean nothing = true;
                for (Player pl : players()) {
                    int[] d = debt(pl);
                    if (total(pl) > 0) nothing = false;
                    StringBuilder sb = new StringBuilder("§e" + ChacaLink.name(pl.getUniqueId()) + "§f. ")   /* 한글 닉네임 */;
                    for (int i = 0; i < 4; i++) if (d[i] > 0) sb.append(DEBT_NAME[i]).append(" ").append(d[i]).append(", ");
                    sb.append("합계 빚 §c").append(total(pl)).append("§f.");
                    if (total(pl) > 0) say(sb.toString());
                }
                if (nothing) {
                    pure = true;
                    say("…아무것도, 받지 않았다고? 계약이… 성립하지 않는군.");
                    boss.setHealth(Math.max(1, maxHp() * (GATES[3] - 0.10)));
                    for (int k = 4; k <= 7; k++) gateDone[k] = true;
                    for (Player pl : players()) plugin.getLogger().info("[엘리시아] 청렴 클리어 조건 달성 후보: " + ChacaLink.name(pl.getUniqueId()));
                } else later(30, () -> say("이제 갚을 시간이다."));
            });
            later(120, () -> { phase = FIGHTING; invulnerable = false; boss.setAI(true); nextAt = t + 20; minionAt = t + 100; });
        }

        /* ---------------------------------------------------------------- ⓑ 빌린 힘의 반환 (기믹 객체 없이 상시 상태) */

        void borrowedPower() {
            say("빌려준 힘, 깎아 낸 만큼 돌려받겠다.");
            anim("cast");
            shieldHp = Math.clamp(blessDealt, 300, 3000);
            Fx.follow(boss, "claude:belzar_debtshield/begin", 40, 0);
            shieldFx = Fx.follow(boss, "claude:belzar_debtshield/sustain", 700, 0);
            shieldUntil = t + 600;
            gimmick = new Cores(this, true);
            invulnerable = false; phase = P_GIM;
            for (Player pl : players()) debt(pl)[1] = 0;
            for (Player pl : players()) refresh(pl);
        }
        void shieldTick() {
            if (shieldFx == null) return;
            if (t >= shieldUntil) {   // 끝까지 못 깸: 보호막이 터지며 큰 피해 (사망은 아님)
                Fx.effect(plugin, "claude:elysia_lightcore/break", boss.getLocation().add(0, 2, 0), 0, 40);
                for (Player pl : players()) { hurtPct(pl, 0.40); hint(pl, "(빌린 힘은 쳐서 되돌려 주거나… 처음처럼 세 빛을 한꺼번에 꺼야 했다.)"); }
                breakShield(false);
            }
        }
        void breakShield(boolean byPlayer) {
            if (shieldFx == null) return;
            Fx.stop(shieldFx); shieldFx = null;
            Fx.follow(boss, "claude:belzar_debtshield/end", 40, 0);
            if (gimmick instanceof Cores c) c.finish(byPlayer);
            else gimmickDone(5, byPlayer);
        }

        /* ---------------------------------------------------------------- 기믹 공통 */

        abstract static class Gimmick {
            final Fight f; final int k; int age; boolean over;
            Gimmick(Fight f, int k) { this.f = f; this.k = k; }
            boolean step() { age++; return !over; }
            boolean patterns() { return false; }
            /** 소품을 플레이어가 침 (피해는 항상 취소). */
            void propHit(Entity prop, Player by) {}
            /** 무적 중 보스가 맞음. */
            void bossHit(EntityDamageEvent e) {}
            void cleanup() {}
            void finish(boolean ok) {
                if (over) return;
                over = true;
                f.later(1, () -> f.gimmickDone(k, ok));
            }
        }

        /* ---------------------------------------------------------------- ① 85% 가호의 제안 */

        static final class Grace extends Gimmick {
            int wave, nextWave = 70; Location light, circle; UUID circleFx;
            Grace(Fight f) {
                super(f, 0);
                f.toCenter(() -> {
                    f.anim("offer");
                    f.say("하늘의 심판이 내린다. 빛의 품으로 오거라, 내가 지켜 주마.");
                    f.later(30, () -> f.say("§7…대가는 훗날 치르게 되리라."));
                    newCircle();
                });
            }
            void newCircle() {
                if (circleFx != null) { Fx.stop(circleFx); Fx.effect(f.plugin, "claude:elysia_sanctuary/end", circle, 0, 30); }
                double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, r = 4 + ReaperBoss.RNG.nextDouble() * 6;
                circle = ground(f.center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                Fx.effect(f.plugin, "claude:elysia_sanctuary/begin", circle, 0, 40);
                circleFx = Fx.held("claude:elysia_sanctuary/sustain", circle, 0, 200);
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (age == nextWave) {
                    float yaw = f.front + ReaperBoss.RNG.nextInt(360);
                    light = f.center.clone().add(dir(yaw).multiply(f.arena + 4)).add(0, 10, 0);
                    f.anim("cast");
                    Fx.effect(f.plugin, "claude:elysia_lightcore/break", light, 0, 30);
                    try {
                        Show s = new Show(f.plugin.telegraphs().id(), Shape.CIRCLE, f.center.getX(), Telegraphs.ground(f.center), f.center.getZ(), 0, (float) (f.arena + 4), 0, 60, 40, TelegraphProtocol.RED);
                        f.plugin.telegraphs().start(s, f.boss, f.boss.getWorld(), true, true, show -> fall());
                    } catch (IllegalArgumentException ex) { f.later(40, this::fall); }
                }
                return true;
            }
            void fall() {
                if (over || f.closed) return;
                for (Player pl : f.players()) {
                    boolean inCircle = ReaperBoss.flat2(pl.getLocation(), circle) <= 2.3 * 2.3;
                    Fx.effect(f.plugin, "claude:elysia_judgment/judgment", ground(pl.getLocation()), 0, 50);
                    if (inCircle) { f.gain(pl, 0, 1); continue; }
                    if (f.shaded(pl, light)) continue;
                    f.later(7, () -> f.kill(pl, "(빛은 모든 곳을 비추지만… 그늘까지 닿지는 못한다.)"));
                }
                for (int i = 0; i < 6; i++) {
                    double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, r = ReaperBoss.RNG.nextDouble() * f.arena;
                    Fx.effect(f.plugin, "claude:elysia_judgment/judgment", ground(f.center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r)), 0, 50);
                }
                wave++;
                if (wave >= 3) { f.later(30, () -> finish(true)); return; }
                newCircle();
                nextWave = age + 80;
            }
            @Override void cleanup() { if (circleFx != null) { Fx.stop(circleFx); Fx.effect(f.plugin, "claude:elysia_sanctuary/end", circle, 0, 30); } }
        }

        /* ---------------------------------------------------------------- ② 70% 축복의 제안 (빛의 성벽 + 빛의 핵 3개) */

        /** 빛의 핵 3개를 4초(80틱) 안에 모두 치면 깨짐. 축복 성벽과 ⓑ 빌린 힘 보호막에서 같이 씀. */
        static class Cores extends Gimmick {
            static final int WINDOW = 80;
            final List<Entity> cores = new ArrayList<>(); final Map<Entity, Integer> lit = new HashMap<>();
            final boolean shield;
            Cores(Fight f, boolean shield) {
                super(f, shield ? 5 : 1);
                this.shield = shield;
                for (int i = 0; i < 3; i++) {
                    float yaw = f.front + i * 120 + (shield ? 60 : 0);
                    Location at = ground(f.center.clone().add(dir(yaw).multiply(10)));
                    Entity e = f.prop("CA_Elysia_core", at, "core");
                    if (e != null) cores.add(e);
                }
            }
            @Override boolean patterns() { return shield; }
            @Override void propHit(Entity prop, Player by) {
                if (!cores.contains(prop) || over) return;
                f.propAnim(prop, "CHA_EL_p_core_hit");
                Fx.effect(f.plugin, "claude:elysia_lightcore/hit", prop.getLocation().add(0, 1.4, 0), 0, 20);
                lit.put(prop, age);
                int n = 0; for (Entity c : cores) { Integer at = lit.get(c); if (at != null && age - at <= WINDOW) n++; }
                if (n >= cores.size() && !cores.isEmpty()) {
                    for (Entity c : cores) { f.propAnim(c, "CHA_EL_p_core_break"); Fx.effect(f.plugin, "claude:elysia_lightcore/break", c.getLocation().add(0, 1.4, 0), 0, 40); }
                    broken();
                }
            }
            void broken() { if (shield) f.breakShield(true); else finish(true); }
            @Override void cleanup() { for (Entity e : cores) f.removeProp(e); }
        }

        static final class Blessing extends Cores {
            static final int LIMIT = 500;
            UUID wallFx; Entity feather; double wallHp = 450; boolean taken;
            Blessing(Fight f) {
                super(f, false);
                f.toCenter(() -> {
                    f.anim("offer");
                    Fx.effect(f.plugin, "claude:elysia_lightbarrier/begin", ground(f.center), f.front, 40);
                    wallFx = Fx.held("claude:elysia_lightbarrier/sustain", ground(f.center), f.front, LIMIT + 40);
                    f.say("이 성벽은 그대의 힘으로는 무너지지 않으리라. 나의 깃털을 받거라.");
                    f.later(30, () -> f.say("§7…대가는 훗날 치르게 되리라."));
                    Location at = ground(f.center.clone().add(dir(f.front).multiply(4.5))).add(0, 0.3, 0);
                    feather = f.prop("CA_Elysia_grace_feather", at, "bless_feather");
                });
            }
            void accept(Player pl) {
                if (taken) return;
                taken = true;
                f.blessed.add(pl.getUniqueId());
                f.gain(pl, 1, 2);
                Fx.follow(pl, "claude:elysia_blessing/begin", 30, 0);
                f.blessFx.put(pl.getUniqueId(), Fx.follow(pl, "claude:elysia_blessing/sustain", 20 * 300, 0));
                f.tell(pl, "§6그대에게 축복을.");
            }
            @Override void bossHit(EntityDamageEvent e) {
                if (over) return;
                Player by = e instanceof EntityDamageByEntityEvent be ? Hooks.attacker(be) : null;
                if (by == null) { List<Player> ps = f.players(); if (ps.size() == 1) by = ps.get(0); }
                if (by == null || !f.blessed.contains(by.getUniqueId())) return;
                wallHp -= e.getDamage() * 3;
                if (wallHp <= 0) broken();
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (age >= LIMIT + 60) {
                    for (Player pl : f.players()) f.kill(pl, "(성벽을 받치던 빛들이… 한꺼번에 흔들린 적은 없었다.)");
                    finish(false);
                }
                return true;
            }
            @Override void broken() {
                if (over) return;
                Fx.stop(wallFx); wallFx = null;
                Fx.effect(f.plugin, "claude:elysia_lightbarrier/end", ground(f.center), f.front, 40);
                finish(true);
            }
            @Override void cleanup() {
                super.cleanup();
                if (wallFx != null) { Fx.stop(wallFx); Fx.effect(f.plugin, "claude:elysia_lightbarrier/end", ground(f.center), f.front, 40); }
                if (feather != null) f.removeProp(feather);
            }
        }

        /* ---------------------------------------------------------------- ③ 구원의 제안 (1페이즈 치명상, 1회) */

        Entity hand; Player saved; int handUntil;
        /** 치명상 순간: 피해 취소, 5초 정지, 천사의 손. true = 가로챔. */
        boolean lethal(Player pl, EntityDamageEvent e) {
            if (demon || salvOffered || phase == P_END || closed) return false;
            if (e.getFinalDamage() < pl.getHealth()) return false;
            salvOffered = true; saved = pl;
            e.setCancelled(true);
            pl.setHealth(1);
            pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 9, false, false, false));
            Location at = pl.getLocation().clone(); at.add(dir(at.getYaw()).multiply(1.2)); at.setYaw(at.getYaw() + 180);
            hand = prop("CA_Elysia_hand", at, "hand");
            tell(pl, "아직 그대의 때가 아니다. 손을 잡겠느냐?");
            handUntil = t + 100;
            later(100, () -> {
                if (hand != null && saved != null) {   // 잡지 않음
                    Player p0 = saved; saved = null;
                    propAnim(hand, "CHA_EL_p_hand_vanish");
                    Entity h = hand; hand = null; later(30, () -> removeProp(h));
                    if (alive(p0)) { p0.removePotionEffect(PotionEffectType.SLOWNESS); spellHit = true; try { p0.setHealth(0); } finally { spellHit = false; } }
                }
            });
            return true;
        }
        void handTaken(Player pl) {
            if (hand == null || saved == null || !saved.equals(pl)) return;
            saved = null;
            propAnim(hand, "CHA_EL_p_hand_grab");
            Fx.effect(plugin, "claude:elysia_salvation/revive", ground(pl.getLocation()), 0, 60);
            Entity h = hand; hand = null;
            later(28, () -> {
                if (!alive(pl)) return;
                pl.removePotionEffect(PotionEffectType.SLOWNESS);
                pl.setHealth(ReaperBoss.Fight.maxOf(pl));
                gain(pl, 2, 3);
                propAnim(h, "CHA_EL_p_hand_vanish");
                later(30, () -> removeProp(h));
            });
        }

        /* ---------------------------------------------------------------- ④ 55% 계시의 제안 (역순 찬송) */

        static final class Revelation extends Gimmick {
            static final float[] PITCH = {0.7f, 1.0f, 1.41f};
            static final int LIMIT = 400;
            final Entity[] pillar = new Entity[3]; final int[] order = new int[3];
            int step, startAt = Integer.MAX_VALUE; boolean sung; Location foot; UUID footFx; final Set<UUID> told = new HashSet<>();
            Revelation(Fight f) {
                super(f, 2);
                List<Integer> o = new ArrayList<>(List.of(0, 1, 2)); Collections.shuffle(o, ReaperBoss.RNG);
                for (int i = 0; i < 3; i++) order[i] = o.get(i);
                String[] mob = {"CA_Elysia_pillar_low", "CA_Elysia_pillar_mid", "CA_Elysia_pillar_high"};
                for (int i = 0; i < 3; i++) {
                    float yaw = f.front - 40 + i * 40;
                    Location at = ground(f.center.clone().add(dir(yaw).multiply(11.5))); at.setYaw(yaw + 180);
                    pillar[i] = f.prop(mob[i], at, "pillar");
                }
                f.toCenter(() -> {
                    f.anim("offer");
                    f.say("들어 보거라, 하늘의 찬송을.");
                    for (int i = 0; i < 3; i++) {
                        int idx = order[i];
                        f.later(20 + i * 16, () -> sing(idx));
                    }
                    f.later(20 + 3 * 16 + 4, () -> { for (Player pl : f.players()) pl.playSound(f.boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f); });
                    f.later(90, () -> {
                        f.say("답을 원하느냐? 내 곁으로 오거라.");
                        f.later(30, () -> f.say("§7…대가는 훗날 치르게 되리라."));
                        foot = ground(f.center.clone().add(dir(f.front).multiply(2.5)));
                        Fx.effect(f.plugin, "claude:elysia_sanctuary/begin", foot, 0, 40);
                        footFx = Fx.held("claude:elysia_sanctuary/sustain", foot, 0, LIMIT + 40);
                        sung = true; startAt = age;
                    });
                });
            }
            void sing(int idx) {
                if (pillar[idx] != null) { f.propAnim(pillar[idx], "CHA_EL_p_pillar_ring"); Fx.effect(f.plugin, "claude:elysia_hymn/ring", pillar[idx].getLocation().add(0, 2.8, 0), 0, 30); }
                for (Player pl : f.players()) pl.playSound(pillar[idx] != null ? pillar[idx].getLocation() : f.center, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, PITCH[idx]);
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (!sung) return true;
                if (foot != null && age % 5 == 0) for (Player pl : f.players()) {
                    if (told.contains(pl.getUniqueId()) || ReaperBoss.flat2(pl.getLocation(), foot) > 2.0 * 2.0) continue;
                    told.add(pl.getUniqueId());
                    f.gain(pl, 3, 1);
                    String[] nm = {"§9낮은", "§a가운데", "§b높은"};
                    ReaperBoss.Fight.title(pl, "§6✦", nm[order[2]] + " §7→ " + nm[order[1]] + " §7→ " + nm[order[0]]);
                }
                if (age - startAt > LIMIT) {
                    for (Player pl : f.players()) f.kill(pl, "(찬송은 끝까지… 그 뒤의 메아리까지 들어야 했다.)");
                    finish(false);
                }
                return true;
            }
            @Override void propHit(Entity prop, Player by) {
                if (!sung || over) return;
                int idx = -1; for (int i = 0; i < 3; i++) if (prop.equals(pillar[i])) idx = i;
                if (idx < 0) return;
                int want = order[2 - step];
                if (idx == want) {
                    sing(idx);
                    if (++step >= 3) { f.later(10, () -> finish(true)); }
                } else {
                    f.propAnim(prop, "CHA_EL_p_pillar_wrong");
                    Fx.effect(f.plugin, "claude:elysia_hymn/wrong", prop.getLocation().add(0, 2.8, 0), 0, 30);
                    for (Player pl : f.players()) pl.playSound(prop.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.6f);
                    if (by != null) {
                        Fx.effect(f.plugin, "claude:elysia_judgment/judgment", ground(by.getLocation()), 0, 50);
                        Player v = by; f.later(7, () -> f.kill(v, "(마지막에 낮게 울리던 메아리… 노래는 그쪽에서부터였다.)"));
                    }
                    finish(false);
                }
            }
            @Override void cleanup() {
                for (Entity e : pillar) f.removeProp(e);
                if (footFx != null) { Fx.stop(footFx); Fx.effect(f.plugin, "claude:elysia_sanctuary/end", foot, 0, 30); }
            }
        }

        /* ---------------------------------------------------------------- ⓐ 45% 그림자 몰수 */

        static final class Confiscate extends Gimmick {
            int wave, nextWave = 90; Location light;
            Confiscate(Fight f) {
                super(f, 4);
                int need = 0; for (Player pl : f.players()) need = Math.max(need, f.debt(pl)[0]);
                final int broken = Math.min(4, need);
                f.toCenter(() -> {
                    f.anim("cast");
                    f.say("빌려준 그늘을 회수하겠다.");
                    List<Entity> st = new ArrayList<>(f.statues); Collections.shuffle(st, ReaperBoss.RNG);
                    for (int i = 0; i < broken && i < st.size(); i++) {
                        Entity s = st.get(i);
                        f.later(10 + i * 8, () -> { s.addScoreboardTag(TAG + ":broken"); f.propAnim(s, "CHA_EL_p_statue_crumble"); Fx.effect(f.plugin, "claude:belzar_hellfire/hellfire", ground(s.getLocation()), 0, 40); });
                    }
                });
            }
            @Override boolean step() {
                if (!super.step()) return false;
                if (age == nextWave) {
                    float yaw = f.front + ReaperBoss.RNG.nextInt(360);
                    light = f.center.clone().add(dir(yaw).multiply(f.arena + 4)).add(0, 10, 0);
                    Fx.effect(f.plugin, "claude:belzar_collection/collect", ground(f.center.clone().add(dir(yaw).multiply(f.arena))), 0, 30);
                    try {
                        Show s = new Show(f.plugin.telegraphs().id(), Shape.CIRCLE, f.center.getX(), Telegraphs.ground(f.center), f.center.getZ(), 0, (float) (f.arena + 4), 0, 60, 40, TelegraphProtocol.RED);
                        f.plugin.telegraphs().start(s, f.boss, f.boss.getWorld(), true, true, show -> fall());
                    } catch (IllegalArgumentException ex) { f.later(40, this::fall); }
                }
                return true;
            }
            void fall() {
                if (over || f.closed) return;
                for (Player pl : f.players()) {
                    Fx.effect(f.plugin, "claude:belzar_hellfire/hellfire", ground(pl.getLocation()), 0, 50);
                    if (f.shaded(pl, light)) continue;
                    f.later(7, () -> f.kill(pl, "(부서진 석상의 수… 그건 그대가 빌린 그늘의 수였다.)"));
                }
                if (++wave >= 3) { f.later(20, () -> { for (Player pl : f.players()) { f.debt(pl)[0] = 0; f.refresh(pl); } finish(true); }); return; }
                nextWave = age + 80;
            }
            @Override void cleanup() { f.later(40, f::placeStatues); }
        }

        /* ---------------------------------------------------------------- ⓒ 30% 목숨 회수 / ⓓ 22% 눈 몰수 */

        static final class Seize extends Gimmick {
            final boolean eyes; final List<UUID> drains = new ArrayList<>();
            Seize(Fight f, boolean eyes) {
                super(f, eyes ? 7 : 6);
                this.eyes = eyes;
                f.boss.setAI(true); f.nextAt = Integer.MAX_VALUE;
                f.say(eyes ? "보여 준 것을 돌려받겠다." : "빌려준 목숨을 돌려받겠다.");
                f.anim("cast");
                for (Player pl : f.players()) {
                    if (f.debt(pl)[eyes ? 3 : 2] <= 0) continue;
                    if (eyes) {
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 240, 0, false, false, true));
                    } else {
                        Fx.follow(pl, "claude:elysia_salvation/seize", 40, 0);
                        f.later(18, () -> { if (alive(pl)) pl.setHealth(1); });
                        f.drained.add(pl.getUniqueId());
                        drains.add(Fx.follow(pl, "claude:elysia_salvation/drain", 260, 0));
                    }
                }
                List<Player> ps = f.players();
                String[] seq = eyes ? new String[]{"hellfire", "claw", "collect"} : new String[]{"claw", "hellfire", "tail"};
                for (int i = 0; i < 3; i++) {
                    String id = seq[i];
                    f.later(40 + i * 60, () -> { if (!over) { f.run(id, f.players()); f.nextAt = Integer.MAX_VALUE; } });
                }
            }
            @Override boolean patterns() { return true; }
            @Override boolean step() {
                if (!super.step()) return false;
                if (age >= 260) {
                    for (Player pl : f.players()) { f.debt(pl)[eyes ? 3 : 2] = 0; f.refresh(pl); }
                    finish(true);
                }
                return true;
            }
            @Override void cleanup() { for (UUID h : drains) Fx.stop(h); f.drained.clear(); }
        }

        /* ---------------------------------------------------------------- 15% 계약 파기 */

        static final class Contract extends Gimmick {
            static final int LIMIT = 1000;
            Entity ledger, seal; int sealIdx = -1, standFor, collectAt = 60; boolean paidAll, burning;
            Location sealAt;
            Contract(Fight f) {
                super(f, 8);
                f.nextAt = Integer.MAX_VALUE;
                f.toCenter(() -> {
                    f.anim("cast");
                    f.say("담보를 집행하겠다. 마지막 한 푼까지.");
                    Location at = f.center.clone().add(0, 6, 0); at.setYaw(f.front);
                    ledger = f.prop("CA_Elysia_ledger", at, "ledger");
                });
            }
            @Override boolean patterns() { return true; }
            double radius() { return Math.max(6, f.arena - (f.arena - 6) * Math.min(1, age / (double) LIMIT)); }
            @Override boolean step() {
                if (!super.step()) return false;
                if (burning) return true;
                double r = radius();
                if (age % 10 == 0) for (Player pl : f.players()) {
                    if (ReaperBoss.flat2(pl.getLocation(), f.center) > r * r) { f.hurtPct(pl, 0.05); Fx.effect(f.plugin, "claude:belzar_hellfire/hellfire", ground(pl.getLocation()), 0, 40); }
                }
                if (age % 40 == 0) for (int i = 0; i < 4; i++) {
                    double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2;
                    Fx.effect(f.plugin, "claude:belzar_hellfire/hellfire", ground(f.center.clone().add(Math.cos(a) * (r + 1.5), 0, Math.sin(a) * (r + 1.5))), 0, 40);
                }
                boolean debtLeft = f.players().stream().anyMatch(pl -> f.total(pl) > 0);
                if (debtLeft && age >= collectAt) { collectAt = age + 80; Player p0 = f.nearest(f.players()); if (p0 != null) f.collect(p0); f.nextAt = Integer.MAX_VALUE; }
                if (!debtLeft && !paidAll && age > 40) { paidAll = true; f.say("…다 갚았다고? 그럴 리가—"); nextSeal(); }
                if (paidAll && sealAt != null && age % 5 == 0) {
                    boolean on = f.players().stream().anyMatch(pl -> ReaperBoss.flat2(pl.getLocation(), sealAt) <= 1.6 * 1.6);
                    standFor = on ? standFor + 5 : 0;
                    if (standFor >= 60) {
                        Fx.effect(f.plugin, "claude:belzar_contractburn/seal", sealAt, 0, 40);
                        f.propAnim(seal, "CHA_EL_p_seal_activate");
                        Entity done = seal; f.later(60, () -> f.removeProp(done));
                        standFor = 0; nextSeal();
                    }
                }
                if (age >= LIMIT && !burning) {
                    for (Player pl : f.players()) f.kill(pl, "(장부는… 빚이 남은 채로는 타지 않는다.)");
                    f.say("담보 집행 완료.");
                    finish(false);
                }
                return true;
            }
            void nextSeal() {
                sealIdx++;
                if (sealIdx >= 3) { burn(); return; }
                float yaw = f.front + 180 + (sealIdx - 1) * 110;
                sealAt = ground(f.center.clone().add(dir(yaw).multiply(5)));
                seal = f.prop("CA_Elysia_seal", sealAt, "seal");
            }
            void burn() {
                burning = true; sealAt = null;
                if (ledger != null) f.propAnim(ledger, "CHA_EL_p_ledger_burn");
                Fx.effect(f.plugin, "claude:belzar_contractburn/burn", f.center.clone().add(0, 6, 0), 0, 100);
                f.anim("death");
                f.later(44, () -> { for (Player pl : f.players()) pl.sendMessage(ReaperBoss.LEG.deserialize("§f§o…고마워요. 이제야, 돌아갈 수 있겠네요.")); });
                f.later(90, () -> {
                    f.phase = P_END; f.invulnerable = false;
                    spellHit = true;
                    try { f.boss.setHealth(0); } finally { spellHit = false; }
                });
            }
            @Override void finish(boolean ok) {
                if (!ok) { super.finish(false); f.gateDone[8] = false; f.boss.setHealth(Math.max(1, f.maxHp() * 0.16)); }
            }
            @Override void cleanup() { f.removeProp(ledger); f.removeProp(seal); }
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
        static Location ground(Location at) { return ReaperBoss.ground(at); }
        static Location chest(LivingEntity e) { return e.getLocation().add(0, e.getHeight() * 0.6, 0); }
        /** 보스 오른손 근처 (발밑 + 앞 1.2 + 오른쪽 1.2 + 높이 2.4). */
        Location hand() {
            Location l = boss.getLocation(); float yaw = l.getYaw();
            return l.clone().add(dir(yaw).multiply(1.2)).add(dir(yaw + 90).multiply(1.2)).add(0, 2.4, 0);
        }
        static void setYaw(Entity e, float yaw) {
            e.setRotation(yaw, 0);
            if (e instanceof LivingEntity le) { try { le.setBodyYaw(yaw); } catch (RuntimeException | LinkageError ignored) {} }
        }
        Player nearest(List<Player> ps) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation()))).orElse(null); }

        boolean castAt(String skill, Location at) {
            try { return MythicBukkit.inst().getAPIHelper().castSkill(boss, skill, boss, boss.getLocation(), List.<Entity>of(), List.of(at), 1f, d -> {}); }
            catch (RuntimeException | LinkageError e) { return false; }
        }
        /** 보스 모델 모션: 엘리시아 CHA_EL_a_&lt;이름&gt;, 벨자르 CHA_EL_b_&lt;이름&gt;. */
        void anim(String name) { castAt((demon ? "CHA_EL_b_" : "CHA_EL_a_") + name, boss.getLocation()); }
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
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("엘리시아 소품 소환 실패: " + mob + " " + ex); return null; }
        }
        void removeProp(Entity e) { if (e != null) { props.remove(e); if (e.isValid()) e.remove(); } }

        void clearState() {
            if (gimmick != null) { gimmick.cleanup(); gimmick = null; }
            if (sanctuary != null) { sanctuary.close(); sanctuary = null; }
            if (shieldFx != null) { Fx.stop(shieldFx); shieldFx = null; }
            for (UUID h : blessFx.values()) Fx.stop(h);
            blessFx.clear(); blessed.clear(); drained.clear();
            dropChains();
            for (TextDisplay td : holos.values()) if (td.isValid()) td.remove();
            holos.clear();
            for (Entity e : List.copyOf(props)) if (e.isValid()) e.remove();
            props.clear(); statues.clear();
            if (hand != null) { hand = null; saved = null; }
        }
        void reset() {
            clearState();
            debts.clear(); graceSteps.clear(); blessDealt = 0; salvOffered = false; pure = false;
            Arrays.fill(gateDone, false);
            phase = FIGHTING; invulnerable = false; last = "";
            if (demon) {   // 벨자르 → 엘리시아로 되돌림
                LivingEntity old = boss;
                try {
                    var am = MythicBukkit.inst().getMobManager().spawnMob("CA_Boss_elysia_reset", center);
                    if (am != null && am.getEntity().getBukkitEntity() instanceof LivingEntity le) {
                        FIGHTS.remove(old.getUniqueId()); boss = le; FIGHTS.put(boss.getUniqueId(), this); old.remove(); demon = false;
                    }
                } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("엘리시아 되돌리기 실패: " + ex); }
            }
            boss.setAI(true); boss.setHealth(maxHp()); boss.teleport(center);
            nextAt = t + 60; emptyFor = 0; sanctuaryAt = t + 300; minionAt = t + 400;
            later(20, this::placeStatues);
        }
        void close() {
            if (closed) return;
            closed = true;
            clearState();
            for (UUID u : List.copyOf(FIGHTS.keySet())) if (FIGHTS.get(u) == this) FIGHTS.remove(u);
        }
        @Override public void stop() { close(); }
    }

    /* ======================================================================== events */

    static Fight fightOf(Entity e) {
        for (Fight f : FIGHTS.values()) if (f.props.contains(e)) return f;
        return null;
    }
    static Fight fightNear(Player pl) {
        for (Fight f : FIGHTS.values()) if (f.players().contains(pl)) return f;
        return null;
    }

    static final class Hooks implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            Fight f = FIGHTS.get(e.getEntity().getUniqueId());
            if (f != null) { f.damaged(e); return; }
            // 플레이어: 1페이즈 치명상 = 구원의 제안 / 임프 = 이자
            if (e.getEntity() instanceof Player pl) {
                if (e instanceof EntityDamageByEntityEvent be && be.getDamager().getScoreboardTags().contains(TAG + ":imp")) {
                    Fight fi = fightOf(be.getDamager()); if (fi != null) fi.interest(pl);
                }
                Fight fp = fightNear(pl);
                if (fp != null) fp.lethal(pl, e);
                return;
            }
            if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
            if (e.getEntity().getScoreboardTags().contains(TAG + ":minion")) return;   // 잡몹은 정상 피해
            Player by = e instanceof EntityDamageByEntityEvent be ? attacker(be) : null;
            Fight fight = fightOf(e.getEntity());
            if (fight != null && fight.gimmick != null && by != null) fight.gimmick.propHit(e.getEntity(), by);
            e.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void regain(EntityRegainHealthEvent e) {
            if (!(e.getEntity() instanceof Player pl)) return;
            for (Fight f : FIGHTS.values()) if (f.drained.contains(pl.getUniqueId())) { e.setCancelled(true); return; }
        }

        @EventHandler(ignoreCancelled = true)
        public void interact(PlayerInteractEntityEvent e) { touch(e.getPlayer(), e.getRightClicked()); }
        @EventHandler(ignoreCancelled = true)
        public void interactAt(PlayerInteractAtEntityEvent e) { touch(e.getPlayer(), e.getRightClicked()); }
        void touch(Player pl, Entity clicked) {
            if (!clicked.getScoreboardTags().contains(TAG + ":hand")) return;
            Fight f = fightOf(clicked); if (f != null) f.handTaken(pl);
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
            if (fight != null) { fight.minionDied(e.getEntity()); fight.props.remove(e.getEntity()); }
        }
    }
}
