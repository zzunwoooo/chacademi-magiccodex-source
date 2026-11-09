package school.chacademia.magicmythic;

import java.util.*;
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
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * charoulette - 1인 레이드 보스 "도박의 악마 룰레타" 진행자 (2026-11-09). 미몹 CA_Boss_roulette 의 ~onSpawn 에서
 * {@code charoulette{a=start;arena=18;table=4} @self}. 기획서 "차카데미 악마 1인 레이드 5종 기획서" 3번.
 *
 * <ul>
 *   <li>경기장 = 소환 위치 중심 반경 arena(18). 가운데 딜러 테이블 반경 table(4) = "베팅 안 함" (틀려도 30%).
 *       바닥 6구역: 구역 k 의 가운데 방향 = 소환 때 바라본 방향 + 60·k (마인크래프트 yaw), 색은 휠과 같은 순서
 *       (0 빨강 · 1 검정 · 2 초록 · 3 파랑 · 4 보라 · 5 금색). 맵에 이 순서로 깐다.</li>
 *   <li>룰렛 35초마다 (광폭화 25초): 중앙 → 휠 회전 6초 → 멈추기 2초 전 "베팅 마감"(그때 서 있던 구역 고정) → 판정.
 *       맞힘 = 보스 최대 체력 6% + 그로기 4초(받는 피해 ×1.5) / 틀림 = 최대 체력 45% (테이블 30%).
 *       속임수 회차(30%, 광폭화 50%) = 휠 테두리 금빛 → 바늘이 한 칸 앞에서 멈춘 척하다 넘어감 (판정 0.5초 늦음).</li>
 *   <li>짤패턴 7개: 카드 날리기 · 칩 탑 낙하 · 주사위 굴리기 · 딜러의 손 · 동전 던지기 · 올인 · 칩 회수.</li>
 *   <li>50% 하우스 룰: 체력이 멈추고 무적, 룰렛 3연속(틀리면 35%), 회차 사이 카드 날리기. 맞힌 횟수 3/2/1/0 → 그로기 15/10/5/0초.</li>
 *   <li>30% 광폭화 "더블 오어 낫싱": 룰렛 25초, 속임수 50%, 패턴 25% 빠름, 이동 1.3배.
 *       (기획의 "휠 2개"는 모델 바늘이 하나라 속임수 확률·주기로 대신함.)</li>
 * </ul>
 * 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX 팩 claude:roulette_boss/* (예고 없음). 대사·안내 = 채팅 + 타이틀.
 */
final class RouletteBoss extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_roulette_prop";
    static final String NAME = "§8[§6룰레타§8] §f";
    static final String VFX = "claude:roulette_boss/";
    static final List<String> PATTERN_IDS = List.of("cards", "chips", "dice", "dealer", "coin", "allin", "recall");
    static final String[] SECTOR = {"§c빨강", "§8검정", "§a초록", "§9파랑", "§5보라", "§6금색"};

    RouletteBoss(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("arena", 18), 10, 32), Math.clamp(p.d("table", 4), 2, 8));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /** /테스트 보스 룰레타 &lt;무엇&gt; [값] - 64칸 안 가장 가까운 룰레타. */
    static String test(Player p, String what, String arg) {
        Fight f = null; double best = 64 * 64;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "64칸 안에 룰레타가 없어요 (/mm mobs spawn CA_Boss_roulette - 소환한 자리가 경기장 중심, 바라본 방향이 0번 구역)";
        switch (what) {
            case "패턴" -> {
                if (f.players().isEmpty()) return "경기장 안에 플레이어가 없어요";
                if (!f.free()) return "지금은 룰렛·기믹 중이라 패턴을 못 써요";
                f.run(arg); f.nextAt = f.t + 100;
                return arg + " 시전 (다음 패턴은 5초 뒤)";
            }
            case "룰렛" -> {
                if (!f.free()) return "지금은 룰렛·기믹 중이에요";
                int k = arg.isEmpty() ? -1 : Math.clamp(Integer.parseInt(arg), 0, 5);
                f.roulette(k, ReaperBoss.RNG.nextFloat() < 0.3f, false);
                return "룰렛 시작" + (k >= 0 ? " (결과 " + k + "번)" : "");
            }
            case "속임수" -> { if (!f.free()) return "지금은 룰렛·기믹 중이에요"; f.roulette(-1, true, false); return "속임수 룰렛 시작"; }
            case "하우스룰" -> { f.boss.setHealth(Math.max(1, f.maxHp() * 0.5)); f.startHouse(); return "50% 하우스 룰 시작"; }
            case "광폭화" -> { f.boss.setHealth(Math.max(1, Math.min(f.boss.getHealth(), f.maxHp() * 0.3))); f.enrage(); return "30% 광폭화"; }
            default -> { f.reset(); return "처음 상태로 (체력 가득)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight extends DemonFight {
        static final int P_FIGHT = 1, P_ROULETTE = 2, P_HOUSE = 3, P_GROGGY = 4;
        final double table;
        int phase = P_FIGHT, nextAt = 80, rouletteAt = 300, emptyFor, groggyUntil, enrageFxAt, houseLeft, houseWins;
        boolean enraged, invulnerable, houseDone;
        String last = "";
        UUID spinFx, groggyFx, auraFx;
        final Deque<Location> chipSpots = new ArrayDeque<>();
        final Map<UUID, Integer> bets = new HashMap<>();   // 플레이어 → 구역 (-1 = 테이블)

        Fight(MagicMythicPlugin plugin, LivingEntity boss, double arena, double table) {
            super(plugin, boss, arena, NAME, "CHA_RL", TAG);
            this.table = table;
            anim("spawn");
            later(30, () -> {
                title("§6§l도박의 악마 룰레타", "§7판돈은 네 목숨. 자, 걸어 봐.");
                say("판돈은 네 목숨. 자, 걸어 봐.");
            });
        }

        @Override List<Player> players() { return playersNear(center); }
        @Override boolean allowHits() { return !closed; }
        @Override double mult() { return enraged ? 1.25 : 1.0; }
        boolean free() { return phase == P_FIGHT; }
        int sp(int ticks) { return enraged ? Math.max(3, Math.round(ticks * 0.75f)) : ticks; }

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            if (ps.isEmpty()) { if ((emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            if (phase == P_GROGGY && t >= groggyUntil) endGroggy();
            if (enraged && t >= enrageFxAt) { Fx.stop(auraFx); auraFx = Fx.follow(boss, VFX + "enrage_aura", 1200, 0); enrageFxAt = t + 1100; }
            if (phase == P_FIGHT) {
                if (t >= rouletteAt && t >= nextAt - 20) roulette(-1, ReaperBoss.RNG.nextFloat() < (enraged ? 0.5f : 0.3f), false);
                else if (t >= nextAt) pattern();
            }
            return true;
        }

        /* ---------------------------------------------------------------- 짤패턴 */

        void pattern() {
            List<String> pool = new ArrayList<>(PATTERN_IDS);
            pool.remove(last);
            if (chipSpots.isEmpty()) pool.remove("recall");
            run(pool.get(ReaperBoss.RNG.nextInt(pool.size())));
        }

        void run(String id) {
            List<Player> ps = players();
            Player near = nearest(ps, boss.getLocation()), far = farthest(ps, boss.getLocation());
            if (near == null) return;
            int len = switch (id) {
                case "cards" -> cards(near);
                case "chips" -> chips(ps);
                case "dice" -> dice(far);
                case "dealer" -> dealer();
                case "coin" -> coin();
                case "allin" -> allIn(near);
                case "recall" -> recall();
                default -> 20;
            };
            last = id;
            nextAt = t + len + sp(enraged ? 10 : 16);
        }

        /** 카드 날리기: 부채꼴 5갈래 직선 폭 1.5 × 18 (0.7초) → 사이 각도 4갈래 한 번 더. 각 18. */
        int cards(Player target) {
            face(target.getLocation());
            anim("card_throw");
            Location from = ground(boss.getLocation());
            float base = yawTo(from, target.getLocation());
            volley(from, base, new int[]{-30, -15, 0, 15, 30}, 0);
            later(sp(20), () -> { anim("card_throw"); volley(ground(boss.getLocation()), base, new int[]{-22, -8, 8, 22}, 0); });
            return sp(44);
        }
        void volley(Location from, float base, int[] offs, int delay) {
            int warn = sp(14);
            for (int o : offs) {
                float yaw = base + o;
                Location end = from.clone().add(dir(yaw).multiply(18));
                strike(Shape.LINE, from, yaw, 18, 1.5, 0, warn, 18, "dark", null, 0, null);
                later(Math.max(1, warn - 5), () -> Fx.fall(plugin, VFX + "card", boss.getLocation().add(0, 4.2, 0), end.clone().add(0, 0.9, 0), 6));
                later(warn + 1, () -> Fx.effect(plugin, VFX + "card_hit", end.clone().add(0, 0.9, 0), yaw, 30));
            }
        }

        /** 칩 탑 낙하: 플레이어 근처 원 반경 3 네 곳, 0.3초 간격, 예고 0.7초. 각 20. 떨어진 자리는 칩 회수 때 다시 쓴다. */
        int chips(List<Player> ps) {
            anim("chip_drop");
            for (int k = 0; k < 4; k++) {
                Player tg = ps.get(k % ps.size());
                int at = sp(6) * k;
                later(at, () -> {
                    Location c = alive(tg) ? ground(tg.getLocation().clone().add(ReaperBoss.RNG.nextGaussian() * 2, 0, ReaperBoss.RNG.nextGaussian() * 2)) : randomIn(center, arena - 3);
                    int warn = sp(14);
                    LivingEntity tower = prop("CA_Roulette_chip_tower", c);
                    animOn(tower, "CHA_RL_p_chip_drop");
                    strike(Shape.CIRCLE, c, 0, 3, 0, 0, warn, 20, "fire", VFX + "chip_drop", 2, hit -> {
                        animOn(tower, "CHA_RL_p_chip_topple");
                        removeLater(tower, 40);
                        chipSpots.addLast(c); while (chipSpots.size() > 8) chipSpots.removeFirst();
                    });
                });
            }
            return sp(6) * 3 + sp(14) + 10;
        }
        static boolean alive(Player p) { return p != null && p.isValid() && !p.isDead(); }

        /** 주사위 굴리기: 큰 주사위가 가장 먼 적 쪽으로 직선 폭 3 굴러감 (예고 0.8초) → 멈춘 눈 n 만큼 방사 직선 폭 2 × 16 (예고 0.7초). 각 22. */
        int dice(Player target) {
            anim("dice_roll");
            Location c = ground(center);
            float yaw = yawTo(c, target.getLocation()) + (ReaperBoss.RNG.nextFloat() - 0.5f) * 30;
            Location start = c.clone().add(dir(yaw + 180).multiply(arena - 2)); start = ground(start);
            double len = Math.min(arena * 2 - 6, 26);
            Location end = ground(start.clone().add(dir(yaw).multiply(len)));
            LivingEntity die = prop("CA_Roulette_dice", start);
            animOn(die, "CHA_RL_p_dice_roll");
            UUID trail = die == null ? null : Fx.follow(die, VFX + "dice_trail", 80, 0);
            int warn = sp(16), roll = 20;
            Location st = start;
            strike(Shape.LINE, start, yaw, len, 3, 0, warn, 22, "earth", null, 0, null);
            later(warn, () -> {
                if (die == null) return;
                plugin.ticker().add(new Ticker.Tick() {
                    int k;
                    @Override public boolean tick() {
                        if (closed || !die.isValid()) return false;
                        k++; Location now = ground(st.clone().add(dir(yaw).multiply(len * Math.min(1, k / (double) roll)))); now.setYaw(yaw); die.teleport(now);
                        return k < roll;
                    }
                    @Override public String kind() { return "boss"; }
                });
            });
            int n = 1 + ReaperBoss.RNG.nextInt(6);
            later(warn + roll, () -> {
                Fx.stop(trail);
                animOn(die, "CHA_RL_p_dice_land_" + n);
                title("", "§f주사위 눈 §6" + n);
            });
            int lineWarn = sp(14), at = warn + roll + 32;
            later(at - lineWarn, () -> {
                float first = ReaperBoss.RNG.nextFloat() * 360;
                Location from = die != null && die.isValid() ? ground(die.getLocation()) : end;
                later(Math.max(1, lineWarn - 2), () -> Fx.effect(plugin, VFX + "pip_" + n, from, first, 60));
                for (int i = 0; i < n; i++) strike(Shape.LINE, from, first + i * 360f / n, 16, 2, 0, lineWarn, 22, "earth", null, 0, null);
                removeLater(die, lineWarn + 30);
            });
            return at + 10;
        }

        /** 딜러의 손: 보스 중심 십자 직선 폭 3 × 16 (예고 0.7초) → 45° 돌려 X자. 각 22. */
        int dealer() {
            anim("dealer_hand");
            Location c = ground(boss.getLocation());
            float y0 = boss.getLocation().getYaw();
            cross(c, y0, "dealer_cross", 0);
            later(sp(18), () -> cross(c, y0 + 45, "dealer_x", 0));
            return sp(18) + sp(14) + 8;
        }
        void cross(Location c, float yaw, String sys, int delay) {
            int warn = sp(14);
            later(Math.max(1, warn - 1), () -> Fx.effect(plugin, VFX + sys, c, yaw, 60));
            for (int k = 0; k < 4; k++) strike(Shape.LINE, c, yaw + 90 * k, 16, 3, 0, warn, 22, "dark", null, 0, null);
        }

        /** 동전 던지기: 앞면 = 보스 주변 반경 8 / 뒷면 = 8~18 고리. 동전이 기우는 1.2초 전부터 예고. 30. */
        int coin() {
            anim("coin_flip");
            boolean heads = ReaperBoss.RNG.nextBoolean();
            Location c = ground(boss.getLocation());
            LivingEntity coin = prop("CA_Roulette_coin", c.clone().add(dir(boss.getLocation().getYaw()).multiply(2)));
            animOn(coin, heads ? "CHA_RL_p_coin_heads" : "CHA_RL_p_coin_tails");
            int hitAt = 48, warn = 24;
            later(hitAt - warn, () -> {
                title("", heads ? "§6앞면 §f— 보스 곁이 위험하다" : "§6뒷면 §f— 바깥이 위험하다");
                if (heads) warnOnly(Shape.CIRCLE, c, 0, 8, 0, 0, warn);
                else for (int s = 0; s < 12; s++) {   // 8~18 고리 = 접선 방향 띠 12개
                    float a = s * 30f; Vector radial = dir(a), tan = dir(a + 90);
                    Location st = c.clone().add(radial.clone().multiply(13)).subtract(tan.clone().multiply(3.6));
                    warnOnly(Shape.LINE, st, yawOf(tan), 7.2, 10, 0, warn);
                }
            });
            later(hitAt - 1, () -> Fx.effect(plugin, VFX + (heads ? "coin_heads" : "coin_tails"), c, 0, 60));
            later(hitAt, () -> {
                for (Player pl : players()) {
                    double d = Math.sqrt(ReaperBoss.flat2(pl.getLocation(), c));
                    if (heads ? d <= 8.3 : (d >= 7.7 && d <= arena + 0.5)) hurt(pl, 30, "light");
                }
                removeLater(coin, 20);
            });
            return hitAt + 8;
        }

        /** 올인: 가까운 적 쪽 앞 반원 반경 10, 예고 0.85초. 32 + 밀침. */
        int allIn(Player target) {
            face(target.getLocation());
            anim("all_in");
            Location c = ground(boss.getLocation());
            float yaw = yawTo(c, target.getLocation());
            strike(Shape.CONE, c, yaw, 10, 0, 180, sp(17), 32, "fire", VFX + "all_in", 1, hit -> { for (Player pl : hit) knock(pl, c, 1.3, 0.45); });
            return sp(32);
        }

        /** 칩 회수: 바닥에 남은 칩 자리 → 보스 직선 폭 1.5, 예고 0.6초. 각 14. */
        int recall() {
            anim("chip_recall");
            Location b = ground(boss.getLocation());
            int k = 0;
            for (Location s : List.copyOf(chipSpots)) {
                int at = 2 * k++;
                later(at, () -> {
                    double len = Math.sqrt(ReaperBoss.flat2(s, b));
                    if (len < 1) return;
                    int warn = sp(12);
                    strike(Shape.LINE, s, yawTo(s, b), len, 1.5, 0, warn, 14, "fire", null, 0, null);
                    later(Math.max(1, warn - 8), () -> { var l = Fx.link(VFX + "recall", s.clone().add(0, 0.3, 0), boss.getLocation().add(0, 4, 0)); later(14, () -> { if (l != null) l.close(); }); });
                });
            }
            chipSpots.clear();
            return sp(12) + 2 * k + 14;
        }

        /* ---------------------------------------------------------------- 룰렛 */

        int sectorOf(Player pl) {
            if (ReaperBoss.flat2(pl.getLocation(), center) <= table * table) return -1;
            float rel = wrap(bearing(center, pl.getLocation()) - center.getYaw() + 30);
            return Math.min(5, (int) (rel / 60));
        }

        /** 룰렛 한 판. k = 결과(-1 이면 무작위), trick = 속임수, house = 하우스 룰 회차(틀리면 35%). */
        void roulette(int k, boolean trick, boolean house) {
            if (!house) phase = P_ROULETTE;
            int res = k >= 0 ? k : ReaperBoss.RNG.nextInt(6);
            boss.teleport(center.clone()); boss.setAI(false);
            castAt(boss, "CHA_RL_clear");
            anim("spin_cast");
            castAt(boss, (trick ? "CHA_RL_needle_trick_" : "CHA_RL_needle_stop_") + res);
            Fx.stop(spinFx); spinFx = Fx.follow(boss, VFX + "spin", 160, 0);
            title("§6§l룰렛", "§f바늘이 멈출 색으로 가라 — 마감 2초 전에 고정");
            if (!house) say(trick ? "행운의 여신은 변덕스럽지." : "돌려라, 돌려라~");
            if (trick) later(40, () -> Fx.follow(boss, VFX + "trick", 40, 0));
            int lock = trick ? 90 : 80, judge = trick ? 130 : 120;
            later(lock, () -> {
                anim("call_bets");
                Fx.effect(plugin, VFX + "betlock", center, center.getYaw(), 40);
                bets.clear();
                for (Player pl : players()) {
                    int s = sectorOf(pl); bets.put(pl.getUniqueId(), s);
                    tell(pl, "§c§l베팅 마감", s < 0 ? "§7테이블 — 베팅 안 함" : "§f베팅: " + SECTOR[s]);
                }
                Fx.stop(spinFx); spinFx = null;
                // 터질 5구역 예고 (판정은 마감 때 고정된 베팅)
                for (int s = 0; s < 6; s++) if (s != res) warnOnly(Shape.CONE, center, center.getYaw() + 60 * s, arena, 0, 60, judge - lock);
            });
            later(judge, () -> judgeRoulette(res, house));
        }

        void judgeRoulette(int res, boolean house) {
            float safeYaw = center.getYaw() + 60 * res;
            Fx.effect(plugin, VFX + "lose", center, safeYaw, 60);
            Fx.effect(plugin, VFX + "table", center, 0, 40);
            int wins = 0;
            for (Player pl : players()) {
                Integer s = bets.get(pl.getUniqueId());
                if (s == null) s = sectorOf(pl);   // 마감 뒤 들어온 사람 = 지금 자리
                if (s == res) {
                    wins++;
                    Fx.follow(pl, VFX + "win", 40, 0);
                    tell(pl, "§6§l적중!", "§f" + SECTOR[res] + " §f— 보스가 휘청인다");
                    if (!house) boss.damage(maxHp() * 0.06, pl);
                } else hurtPct(pl, s < 0 ? 0.30 : house ? 0.35 : 0.45, "dark");
            }
            if (house) { houseWins += wins > 0 ? 1 : 0; return; }
            rouletteAt = t + (enraged ? 500 : 700);
            if (wins > 0) { say("이런... 운이 좋군."); groggy(80); }
            else { say("하우스의 승리다!"); phase = P_FIGHT; boss.setAI(true); castAt(boss, "CHA_RL_clear"); nextAt = t + 30; }
        }

        void groggy(int ticks) {
            phase = P_GROGGY; groggyUntil = t + ticks;
            boss.setAI(false);
            castAt(boss, "CHA_RL_clear");
            anim("groggy");
            Fx.stop(groggyFx); groggyFx = Fx.follow(boss, VFX + "groggy", ticks + 10, 0);
            title("§6§l그로기", "§f" + ticks / 20 + "초 — 받는 피해 1.5배");
        }
        void endGroggy() {
            Fx.stop(groggyFx); groggyFx = null;
            castAt(boss, "CHA_RL_clear");
            phase = P_FIGHT; boss.setAI(true); nextAt = t + 20;
        }

        /* ---------------------------------------------------------------- 50% 하우스 룰 · 30% 광폭화 */

        void damaged(EntityDamageEvent e) {
            if (invulnerable) { e.setCancelled(true); return; }
            if (phase == P_GROGGY) e.setDamage(e.getDamage() * 1.5);
            double after = boss.getHealth() - e.getFinalDamage();
            if (!houseDone && after <= maxHp() * 0.5) {
                e.setCancelled(true);
                boss.setHealth(Math.max(1, maxHp() * 0.5));
                startHouse();
            } else if (houseDone && !enraged && after <= maxHp() * 0.3) later(1, this::enrage);
        }

        void startHouse() {
            if (phase == P_HOUSE) return;
            houseDone = true;
            Fx.stop(groggyFx); groggyFx = null;
            phase = P_HOUSE; invulnerable = true; houseLeft = 3; houseWins = 0;
            title("§6§l하우스 룰", "§f룰렛 3연속 — 맞힌 만큼 그로기");
            say("이제부터는 하우스 룰이다. 판을 키우지!");
            later(30, this::houseRound);
        }
        void houseRound() {
            if (phase != P_HOUSE) return;
            if (houseLeft <= 0) { endHouse(); return; }
            houseLeft--;
            boolean trick = ReaperBoss.RNG.nextFloat() < (houseLeft == 0 ? 0.6f : houseLeft == 1 ? 0.4f : 0.2f);
            roulette(-1, trick, true);
            int judge = trick ? 130 : 120;
            later(judge + 20, () -> { if (phase != P_HOUSE) return; boss.setAI(true); Player tg = nearest(players(), boss.getLocation()); if (tg != null) cards(tg); });
            later(judge + 70, this::houseRound);
        }
        void endHouse() {
            invulnerable = false;
            rouletteAt = t + (enraged ? 500 : 700);
            int g = houseWins >= 3 ? 300 : houseWins == 2 ? 200 : houseWins == 1 ? 100 : 0;
            if (g > 0) { say("크윽... 하우스가 졌다고?"); groggy(g); }
            else { say("하우스는 언제나 이긴다."); phase = P_FIGHT; boss.setAI(true); castAt(boss, "CHA_RL_clear"); nextAt = t + 20; }
        }

        void enrage() {
            if (enraged) return;
            enraged = true;
            anim("enrage");
            Fx.effect(plugin, VFX + "enrage", ground(boss.getLocation()), 0, 60);
            title("§c§l더블 오어 낫싱", "§f룰렛이 빨라지고 속임수가 잦아진다");
            say("올인이다! 전부 걸어라!");
            AttributeInstance spd = boss.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(spd.getBaseValue() * 1.3);
            enrageFxAt = t + 40;
            rouletteAt = Math.min(rouletteAt, t + 500);
        }

        /* ---------------------------------------------------------------- 정리 */

        void reset() {
            Fx.stop(spinFx); Fx.stop(groggyFx); Fx.stop(auraFx); spinFx = groggyFx = auraFx = null;
            removeProps();
            phase = P_FIGHT; invulnerable = false; enraged = false; houseDone = false; last = "";
            chipSpots.clear(); bets.clear();
            AttributeInstance spd = boss.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(spd.getDefaultValue());
            castAt(boss, "CHA_RL_clear"); castAt(boss, "CHA_RL_needle_clear");
            boss.setAI(true); boss.setHealth(maxHp()); boss.teleport(center);
            nextAt = t + 60; rouletteAt = t + 300; emptyFor = 0;
        }
        void removeProps() {
            for (Entity e : center.getWorld().getNearbyEntities(center, arena + 8, 24, arena + 8)) if (e.getScoreboardTags().contains(TAG)) e.remove();
        }
        void close() {
            if (closed) return;
            closed = true;
            Fx.stop(spinFx); Fx.stop(groggyFx); Fx.stop(auraFx);
            removeProps();
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
            if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);   // 소품은 안 맞음
        }

        /** 카드 병정(CA_Roulette_card_soldier[_shiny]) 정면 ±60° 에서 들어온 피해 50% 감소. */
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void guard(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof LivingEntity soldier) || soldier instanceof Player) return;
            String type = Targets.mythicType(soldier);
            if (type == null || !type.startsWith("CA_Roulette_card_soldier")) return;
            Entity src = e.getDamager();
            if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
            Vector to = src.getLocation().toVector().subtract(soldier.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 1e-4) return;
            Vector face = DemonFight.dir(soldier.getLocation().getYaw());
            if (face.dot(to.normalize()) >= 0.5) e.setDamage(e.getDamage() * 0.5);
        }

        /** 보스 근접(미몹 meleeattack)도 광폭화 배율. */
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void melee(EntityDamageByEntityEvent e) {
            if (DemonFight.hurting) return;
            Fight f = FIGHTS.get(e.getDamager().getUniqueId());
            if (f != null && e.getEntity() instanceof Player) e.setDamage(e.getDamage() * f.mult());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            Fight f = FIGHTS.remove(e.getEntity().getUniqueId());
            if (f == null) return;
            Fx.effect(f.plugin, VFX + "enrage", e.getEntity().getLocation(), 0, 60);
            f.title("§6§l룰레타 처치", "§7판은 끝났다. 하우스가... 졌다.");
            f.say("말도 안 돼... 내가... 잃다니...");
            f.close();
        }
    }
}
