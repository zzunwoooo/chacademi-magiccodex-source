package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.*;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * chasiege - 3인 공성전 레이드 "100년 전, 성문 방어전" 진행자 (2026-11-13). 미몹 CA_Siege_gate 의 ~onSpawn 에서
 * {@code chasiege{a=start;lane=40;gatehp=4000} @self}.
 *
 * <ul>
 *   <li>CA_Siege_gate 를 소환한 자리 = 성문, 바라본 방향 = 적이 오는 쪽. 지옥문 포탈 3개가 성문 앞 lane(40)칸, 좌우 12칸 간격으로 열린다.
 *       전장 = 성문과 포탈 사이 (반경 lane/2 + 10).</li>
 *   <li>4단계: 몽마 솜니아 → 도박 룰레타 → 천사 벨자르 → 해방된 악마. 단계마다 마수 세 번 쏟아짐(약 40초) → 큰 포탈로 보스 등장
 *       → 보스전 동안에도 마수가 조금씩 → 보스가 죽으면 8초 쉬고 다음 단계. 악마를 잡으면 승리.</li>
 *   <li>성문 체력(gatehp)이 0 이면 함락 = 실패. 깎는 것: 성문에 붙은 마수(종류별 2초마다, 불씨 두꺼비는 자폭 한 번), 보스의 성문 강타(45초마다, 5% / 악마 8%).
 *       기믹은 없다. 보스마다 짤패턴 9~10개(체력 30% 아래 광폭화 뒤에 1개 더), 마수는 미몹 스킬로 스스로 싸운다.</li>
 *   <li>마수 역할: 뿔 돌격수·공성 거수·불씨 두꺼비·가시 도마뱀 = 성문으로 직행 / 나머지 = 플레이어 추적.</li>
 * </ul>
 * 빨간 예고 = 모드 예고(Telegraphs). VFX = PortableVFX 팩 claude:siege_&lt;보스&gt;/*, siege_beasts/*, siege_portal/* (VFX 에 예고 없음).
 * 대사는 짧게만, 규칙 설명 없음. 서버 부담: 레이드 하나당 Ticker 1개 + 움직이는 패턴마다 짧은 Ticker.
 */
final class SiegeRaid extends Mech {
    static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    static final String TAG = "chacademi_siege_prop", MOB_TAG = "chacademi_siege_mob";
    static final String NAME = "§8[§c성문§8] §f";
    static final String PORTAL = "claude:siege_portal/";
    /** 보스: 미몹 이름, 모션 스킬 머리말, VFX 팩, 이름, 대사. */
    record Boss(String mob, String anim, String vfx, String title, String line, List<String> patterns, String rage) {}
    static final Boss[] BOSSES = {
        new Boss("CA_Siege_boss_somnia", "CHA_SG_SO", "claude:siege_somnia/", "§d§l몽마 솜니아", "잠들어라. 성문은 꿈속에서 열릴 테니.",
            List.of("orb", "gaze", "lullaby", "echo", "yawn", "fog", "eyelid"), "fullmoon"),
        new Boss("CA_Siege_boss_roulette", "CHA_SG_RL", "claude:siege_roulette/", "§6§l도박의 악마 룰레타", "판돈은 이 성 전부다.",
            List.of("cards", "dice", "domino", "coin", "slot", "sectors", "hands", "allin"), "jackpot"),
        new Boss("CA_Siege_boss_belzar", "CHA_SG_BZ", "claude:siege_belzar/", "§f§l천사 벨자르", "빚은 이자까지 받아 간다.",
            List.of("debt", "invert", "chain", "mines", "halo", "mirror", "spears", "interest"), "final"),
        new Boss("CA_Siege_boss_demon", "CHA_SG_FD", "claude:siege_demon/", "§4§l해방된 악마", "문을 열어라.",
            List.of("combo", "leap", "dash", "throw", "fissure", "breath", "pillars", "wings", "roar"), "doom")};
    static final List<String> PATTERN_IDS;
    static { List<String> all = new ArrayList<>(); for (Boss b : BOSSES) { all.addAll(b.patterns()); all.add(b.rage()); } all.add("gate"); PATTERN_IDS = List.copyOf(all); }
    /** 마수: 미몹 이름 꼬리, 성문 직행 여부, 성문 피해(2초마다), 큰 포탈 여부. */
    record Beast(String id, boolean rusher, double gate, boolean big) {}
    static final Map<String, Beast> BEASTS = new LinkedHashMap<>();
    static {
        for (Beast b : new Beast[]{new Beast("hound", false, 6, false), new Beast("ram", true, 16, false), new Beast("lizard", true, 8, false), new Beast("toad", true, 45, false),
            new Beast("twin", false, 12, false), new Beast("behemoth", true, 60, true), new Beast("stalker", false, 6, false), new Beast("stinger", false, 8, false)}) BEASTS.put(b.id(), b);
    }
    static final String[][] POOLS = {{"hound", "hound", "lizard", "toad"}, {"hound", "ram", "stalker", "toad"}, {"twin", "stinger", "ram", "lizard"},
        {"hound", "ram", "lizard", "toad", "twin", "stalker", "stinger"}};
    /** 방향이 거울처럼 반대로 보이면 이 부호만 바꾼다 (VFX 회전 방향 ↔ 서버 판정). */
    static final int GAZE_SIGN = 1, BREATH_SIGN = 1;

    SiegeRaid(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        String a = p.s("a", "start").toLowerCase(Locale.ROOT);
        if (a.equals("stop")) { Fight f = FIGHTS.remove(c.caster().getUniqueId()); if (f != null) f.close(); return true; }
        if (FIGHTS.containsKey(c.caster().getUniqueId())) return true;
        Fight f = new Fight(plugin, c.caster(), Math.clamp(p.d("lane", 40), 24, 64), Math.clamp(p.d("gatehp", 4000), 100, 1_000_000));
        if (!plugin.ticker().add(f)) { f.close(); return false; }
        FIGHTS.put(c.caster().getUniqueId(), f);
        return true;
    }

    static void closeAll() { for (Fight f : List.copyOf(FIGHTS.values())) f.close(); FIGHTS.clear(); }

    /** /테스트 보스 공성전 &lt;무엇&gt; [값] - 96칸 안 가장 가까운 성문. */
    static String test(Player p, String what, String arg) {
        Fight f = null; double best = 96 * 96;
        for (Fight x : FIGHTS.values()) if (x.boss.getWorld().equals(p.getWorld())) { double d = x.boss.getLocation().distanceSquared(p.getLocation()); if (d < best) { best = d; f = x; } }
        if (f == null) return "96칸 안에 성문이 없어요 (/mm mobs spawn CA_Siege_gate - 소환한 자리가 성문, 바라본 방향이 적이 오는 쪽)";
        switch (what) {
            case "단계" -> { int k = Math.clamp(Integer.parseInt(arg), 1, 4) - 1; f.clearField(); f.stage = k; f.startWave(); return (k + 1) + "단계 마수 물결부터"; }
            case "보스" -> { int k = Math.clamp(Integer.parseInt(arg), 1, 4) - 1; f.clearField(); f.stage = k; f.spawnBoss(); return (k + 1) + "단계 보스 바로 등장"; }
            case "패턴" -> {
                if (f.cur == null || !f.cur.isValid()) return "지금 보스가 없어요 (/테스트 보스 공성전 보스 1~4)";
                if (f.players().isEmpty()) return "전장 안에 플레이어가 없어요";
                if (!f.def().patterns().contains(arg) && !f.def().rage().equals(arg) && !arg.equals("gate")) return "이 보스의 패턴이 아니에요: " + f.def().patterns() + " + " + f.def().rage() + ", gate";
                f.run(arg); f.nextAt = f.t + 120;
                return arg + " 시전 (다음 패턴은 6초 뒤)";
            }
            case "마수" -> { if (!BEASTS.containsKey(arg)) return "마수 이름: " + BEASTS.keySet(); f.spawnBeast(arg, ReaperBoss.RNG.nextInt(3)); return arg + " 소환"; }
            case "성문" -> { f.gateHp = Math.clamp(Double.parseDouble(arg), 1, 100) / 100 * f.gateMax; f.bar(); return "성문 체력 " + arg + "%"; }
            case "광폭화" -> { if (f.cur == null) return "지금 보스가 없어요"; f.cur.setHealth(Math.max(1, f.bossMax() * 0.3)); f.enrage(); return "광폭화"; }
            default -> { f.reset(); return "처음 상태로 (성문 체력 가득, 플레이어가 들어오면 1단계부터)"; }
        }
    }

    /* ======================================================================== fight */

    static final class Fight extends DemonFight {
        static final int P_IDLE = 0, P_WAVE = 1, P_BOSS = 2, P_REST = 3, P_DONE = 4;
        final double lane; final float fyaw; final Location field; final Location[] portals = new Location[3];
        final double gateMax; double gateHp;
        final BossBar gateBar;
        int phase = P_IDLE, stage, phaseAt, pours, nextPour, nextAt, gateAt, emptyFor, holdAt, lastWarn = 100, bossDue;
        boolean enraged, portalsOpen, bigOpen;
        LivingEntity cur; String last = "";
        final Set<UUID> beasts = new HashSet<>();
        final Map<UUID, Integer> gateHitAt = new HashMap<>();

        Fight(MagicMythicPlugin plugin, LivingEntity gate, double lane, double gateHp) {
            super(plugin, gate, lane / 2 + 10, NAME, "CHA_SG", TAG);
            this.lane = lane; this.fyaw = gate.getLocation().getYaw(); this.gateMax = this.gateHp = gateHp;
            this.field = polar(center, fyaw, lane / 2);
            Vector side = dir(fyaw + 90);
            for (int i = 0; i < 3; i++) { portals[i] = ground(polar(center, fyaw, lane).add(side.clone().multiply((i - 1) * 12))); portals[i].setYaw(fyaw + 180); }
            gateBar = Bukkit.createBossBar("§c§l성문", BarColor.RED, BarStyle.SEGMENTED_10);
            gate.setAI(false); gate.setInvulnerable(true);
        }

        @Override List<Player> players() { return playersNear(field); }
        @Override boolean allowHits() { return !closed && phase != P_DONE; }
        @Override double mult() { return enraged ? 1.25 : 1.0; }
        Boss def() { return BOSSES[stage]; }
        double bossMax() { if (cur == null) return 1; var a = cur.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH); return a == null ? Math.max(1, cur.getHealth()) : a.getValue(); }
        float toGate() { return fyaw + 180; }
        int sp(int ticks) { return enraged ? Math.max(3, Math.round(ticks * 0.8f)) : ticks; }
        String V() { return def().vfx(); }
        void A(String name) { animOn(cur, def().anim() + "_a_" + name); }
        Location at() { return ground(cur.getLocation()); }
        void faceCur(Location to) { cur.setRotation(yawTo(cur.getLocation(), to), 0); }
        void fx(String sys, Location at, float yaw, int ticks) { Fx.effect(plugin, V() + sys, at, yaw, ticks); }
        boolean bossAlive() { return cur != null && cur.isValid() && !cur.isDead(); }
        /** 보스가 살아 있을 때만 실행 (패턴 중간에 보스가 죽으면 뒤 타격을 멈춤). */
        void then(int ticks, Runnable r) { LivingEntity b = cur; later(ticks, () -> { if (b == cur && bossAlive() && phase == P_BOSS) r.run(); }); }

        /* ---------------------------------------------------------------- 진행 */

        @Override public boolean tick() {
            if (closed) return false;
            if (!boss.isValid() || boss.isDead()) { close(); return false; }
            t++;
            if (t % 5 != 0) return true;
            List<Player> ps = players();
            for (Player pl : ps) gateBar.addPlayer(pl);
            if (t % 40 == 0) for (Player pl : List.copyOf(gateBar.getPlayers())) if (!ps.contains(pl)) gateBar.removePlayer(pl);
            if (phase == P_DONE) return true;
            if (ps.isEmpty()) { if (phase != P_IDLE && (emptyFor += 5) >= 1200) reset(); return true; }
            emptyFor = 0;
            switch (phase) {
                case P_IDLE -> { if (phaseAt == 0) { phaseAt = t + 100; title("§c§l성문을 지켜라", "§7100년 전, 봉인 전야"); } else if (t >= phaseAt) { stage = 0; startWave(); } }
                case P_WAVE -> {
                    if (t >= nextPour && pours < 3) { pour(4 + stage + Math.min(2, ps.size() - 1), ps.size()); pours++; nextPour = t + 260; }
                    if (pours >= 3 && t >= nextPour - 60) spawnBoss();
                }
                case P_BOSS -> {
                    if (cur == null && t < bossDue) break;   // 큰 포탈이 열리는 중
                    if (!bossAlive()) { bossDied(); break; }
                    if (!enraged && cur.getHealth() <= bossMax() * 0.3) enrage();
                    if (t >= nextPour) { if (beasts.size() < 5 + ps.size()) pour(2 + (stage >= 2 ? 1 : 0), ps.size()); nextPour = t + 300; }
                    if (t >= nextAt) { if (t >= gateAt) { run("gate"); gateAt = t + 900; } else pattern(); }
                }
                case P_REST -> { if (t >= phaseAt) { stage++; startWave(); } }
                default -> { }
            }
            if (portalsOpen && t >= holdAt) { for (Location p : portals) Fx.effect(plugin, PORTAL + "portal_hold", p, p.getYaw(), 170); if (bigOpen) Fx.effect(plugin, PORTAL + "portal_hold_big", portals[1], portals[1].getYaw(), 170); holdAt = t + 150; }
            if (t % 20 == 0) beastTick();
            return true;
        }

        void startWave() {
            phase = P_WAVE; pours = 0; nextPour = t + 50; enraged = false; cur = null;
            openPortals(false);
            if (stage > 0) say("§7포탈이 다시 열린다.");
        }
        void openPortals(boolean big) {
            if (!portalsOpen) for (Location p : portals) Fx.effect(plugin, PORTAL + "portal_open", p, p.getYaw(), 40);
            if (big && !bigOpen) Fx.effect(plugin, PORTAL + "portal_open_big", portals[1], portals[1].getYaw(), 40);
            if (!portalsOpen) holdAt = t + 16;
            portalsOpen = true; bigOpen |= big;
        }
        void closePortals() {
            if (portalsOpen) for (Location p : portals) Fx.effect(plugin, PORTAL + "portal_close", p, p.getYaw(), 40);
            if (bigOpen) Fx.effect(plugin, PORTAL + "portal_close_big", portals[1], portals[1].getYaw(), 40);
            portalsOpen = bigOpen = false;
        }

        /** 마수 n 마리를 포탈 세 곳에서 0.35초 간격으로 쏟아낸다. 3단계부터 물결마다 공성 거수 하나. */
        void pour(int n, int players) {
            String[] pool = POOLS[stage];
            for (int i = 0; i < n; i++) {
                String id = pool[ReaperBoss.RNG.nextInt(pool.length)]; int at = i * 7, gate = ReaperBoss.RNG.nextInt(3);
                later(at, () -> spawnBeast(id, gate));
            }
            if (stage >= 2 && phase == P_WAVE && pours == 1) later(n * 7 + 20, () -> spawnBeast("behemoth", 1));
        }
        void spawnBeast(String id, int gate) {
            Beast b = BEASTS.get(id); if (b == null || closed || phase == P_DONE) return;
            if (b.big()) openPortals(true);
            Location p = portals[b.big() ? 1 : gate].clone().add(dir(toGate()).multiply(b.big() ? 3 : 1.5));
            p.setYaw(toGate());
            LivingEntity e = spawn("CA_Siege_" + id, p, MOB_TAG);
            if (e == null) return;
            beasts.add(e.getUniqueId());
            castAt(e, "CHA_SGB_portal_out");
            Fx.effect(plugin, PORTAL + (b.big() ? "spawn_burst_big" : "spawn_burst"), portals[b.big() ? 1 : gate], toGate(), 30);
        }
        LivingEntity spawn(String mob, Location at, String tag) {
            try {
                var am = MythicBukkit.inst().getMobManager().spawnMob(mob, at);
                if (am == null) return null;
                Entity e = am.getEntity().getBukkitEntity();
                e.addScoreboardTag(tag); e.setPersistent(false); e.setRotation(at.getYaw(), 0);
                return e instanceof LivingEntity le ? le : null;
            } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("공성전 소환 실패: " + mob + " " + ex); return null; }
        }

        /** 1초마다: 죽은 마수 정리, 성문 직행 마수 길 안내, 성문에 붙은 마수의 성문 공격. */
        void beastTick() {
            Location gate = ground(center);
            for (Iterator<UUID> it = beasts.iterator(); it.hasNext(); ) {
                UUID id = it.next(); Entity e = Bukkit.getEntity(id);
                if (!(e instanceof LivingEntity le) || !e.isValid() || le.isDead()) { it.remove(); gateHitAt.remove(id); continue; }
                String type = Targets.mythicType(le); Beast b = type == null ? null : BEASTS.get(type.replace("CA_Siege_", "").replace("_shiny", ""));
                if (b == null) continue;
                double d2 = ReaperBoss.flat2(le.getLocation(), gate), reach = 5 + le.getWidth();
                if (b.rusher() && d2 > reach * reach && le instanceof Mob m) { m.setTarget(null); m.getPathfinder().moveTo(gate, 1.15); }
                if (d2 <= reach * reach && t - gateHitAt.getOrDefault(id, -100) >= 40) {
                    gateHitAt.put(id, t);
                    le.setRotation(yawTo(le.getLocation(), gate), 0);
                    castAt(le, "CHA_SGB_" + b.id() + "_gate");
                    later(12, () -> gateDamage(b.gate()));
                }
            }
        }
        void gateDamage(double amount) {
            if (phase == P_DONE || phase == P_IDLE) return;
            gateHp = Math.max(0, gateHp - amount); bar();
            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.7f);
            int pct = (int) (gateHp / gateMax * 100);
            if (pct <= 50 && lastWarn > 50 || pct <= 25 && lastWarn > 25 || pct <= 10 && lastWarn > 10) title("", "§c성문이 " + pct + "% 남았다");
            lastWarn = pct;
            if (gateHp <= 0) lose();
        }
        void bar() { gateBar.setProgress(Math.clamp(gateHp / gateMax, 0, 1)); gateBar.setTitle("§c§l성문 §f" + (int) Math.ceil(gateHp / gateMax * 100) + "%"); }

        void spawnBoss() {
            phase = P_BOSS; enraged = false; last = ""; cur = null; bossDue = t + 40;
            openPortals(true);
            Boss b = def();
            later(24, () -> {
                Location p = portals[1].clone().add(dir(toGate()).multiply(4)); p.setYaw(toGate());
                cur = spawn(b.mob(), p, MOB_TAG);
                if (cur == null) { say("§c보스 소환 실패: " + b.mob()); return; }
                Fx.effect(plugin, PORTAL + "spawn_burst_big", portals[1], toGate(), 30);
                A("spawn");
                title(b.title(), "§7" + b.line()); say(b.line());
                nextAt = t + 90; gateAt = t + 700; nextPour = t + 240;
            });
        }
        void bossDied() {
            cur = null; enraged = false;
            if (stage >= BOSSES.length - 1) { win(); return; }
            phase = P_REST; phaseAt = t + 160;
            closePortals();
            for (UUID id : beasts) if (Bukkit.getEntity(id) instanceof LivingEntity le) le.setHealth(0);
            title("", "§7…다음이 온다.");
        }
        void enrage() {
            if (enraged || !bossAlive()) return;
            enraged = true; A("enrage"); fx("enrage", at(), cur.getLocation().getYaw(), 60);
            nextAt = Math.max(nextAt, t + 50);
        }
        void win() {
            phase = P_DONE; closePortals(); clearMobs();
            title("§6§l성문을 지켜 냈다", "§7악마는 봉인되었다. 100년 동안은.");
            plugin.getLogger().info("[siege] 승리 - 남은 성문 " + (int) (gateHp / gateMax * 100) + "% / " + players().stream().map(Player::getName).toList());
        }
        void lose() {
            phase = P_DONE; closePortals(); clearMobs();
            title("§4§l성문 함락", "§7…문이 열렸다.");
            later(100, this::reset);
        }
        void clearMobs() {
            for (Entity e : field.getWorld().getNearbyEntities(field, arena + 30, 40, arena + 30)) if (e.getScoreboardTags().contains(MOB_TAG) || e.getScoreboardTags().contains(TAG)) e.remove();
            beasts.clear(); gateHitAt.clear(); cur = null;
        }
        void clearField() { clearMobs(); closePortals(); enraged = false; }
        void reset() {
            clearField(); phase = P_IDLE; phaseAt = 0; stage = 0; emptyFor = 0; gateHp = gateMax; lastWarn = 100; bar();
        }
        void close() {
            if (closed) return;
            closed = true; clearField(); gateBar.removeAll(); FIGHTS.remove(boss.getUniqueId());
        }
        @Override public void stop() { close(); }

        /* ---------------------------------------------------------------- 판정 도우미 */

        int T(double sec) { return (int) Math.round(sec * 20); }
        void circle(Location at, double r, int warn, double dmg, String el, String sys, double hitSec) { strike(Shape.CIRCLE, at, 0, r, 0, 0, warn, dmg, el, sys == null ? null : V() + sys, T(hitSec), null); }
        void line(Location at, float yaw, double len, double w, int warn, double dmg, String el, String sys, double hitSec) { strike(Shape.LINE, at, yaw, len, w, 0, warn, dmg, el, sys == null ? null : V() + sys, T(hitSec), null); }
        void cone(Location at, float yaw, double r, double angle, int warn, double dmg, String el, String sys, double hitSec) { strike(Shape.CONE, at, yaw, r, 0, angle, warn, dmg, el, sys == null ? null : V() + sys, T(hitSec), null); }
        /** 도넛(r0~r1): 예고는 바깥 원, 판정은 r0 안쪽을 뺀다. */
        void donut(Location at, double r0, double r1, int warn, double dmg, String el, String sys, double hitSec) {
            strike(Shape.CIRCLE, at, 0, r1, 0, 0, warn, 0, el, sys == null ? null : V() + sys, T(hitSec), hit -> { for (Player pl : hit) if (ReaperBoss.flat2(pl.getLocation(), at) > r0 * r0) hurt(pl, dmg, el); });
        }
        /** 사각(가로 w × 세로 d, 가운데 c, 세로축 = yaw): LINE 예고로 그린다. VFX 는 가운데에서 재생. */
        void rect(Location c, float yaw, double w, double d, int warn, double dmg, String el, String sys, double hitSec) {
            Location from = ground(c.clone().subtract(dir(yaw).multiply(d / 2)));
            strike(Shape.LINE, from, yaw, d, w, 0, warn, dmg, el, null, 0, null);
            if (sys != null) later(Math.max(1, warn - T(hitSec)), () -> fx(sys, c, yaw, 60));
        }
        /** 퍼지거나 좁혀드는 고리: 시작 delay 틱 뒤 dur 틱 동안 r0 → r1. 고리 두께 ±1 에 닿고 땅에 서 있으면 피해 (뛰어넘기). */
        void ring(Location c, double r0, double r1, int delay, int dur, double dmg, String el) {
            LivingEntity b = cur; Set<UUID> done = new HashSet<>(); int[] k = {0};
            plugin.ticker().add(() -> {
                if (closed || b != cur || !bossAlive() || ++k[0] > delay + dur) return false;
                if (k[0] < delay || k[0] % 2 != 0) return true;
                double r = r0 + (r1 - r0) * (k[0] - delay) / (double) dur;
                for (Player pl : players()) {
                    if (done.contains(pl.getUniqueId()) || !pl.isOnGround()) continue;
                    if (Math.abs(Math.sqrt(ReaperBoss.flat2(pl.getLocation(), c)) - r) <= 1.0) { done.add(pl.getUniqueId()); hurt(pl, dmg, el); }
                }
                return true;
            });
        }
        /** 달려가는 직선(균열·도미노): delay 틱 뒤 dur 틱 동안 앞머리가 0 → len. 앞머리 뒤 3칸 안, 폭 w 안에 있으면 피해. */
        void runLane(Location from, float yaw, double len, double w, int delay, int dur, double dmg, String el) {
            LivingEntity b = cur; Set<UUID> done = new HashSet<>(); int[] k = {0}; Vector f = dir(yaw);
            plugin.ticker().add(() -> {
                if (closed || b != cur || !bossAlive() || ++k[0] > delay + dur) return false;
                if (k[0] < delay) return true;
                double head = len * (k[0] - delay) / (double) dur;
                for (Player pl : players()) {
                    if (done.contains(pl.getUniqueId())) continue;
                    Vector v = pl.getLocation().toVector().subtract(from.toVector()); double along = v.getX() * f.getX() + v.getZ() * f.getZ(), side = Math.abs(v.getX() * f.getZ() - v.getZ() * f.getX());
                    if (side <= w / 2 + 0.3 && along <= head && along >= head - 3) { done.add(pl.getUniqueId()); hurt(pl, dmg, el); }
                }
                return true;
            });
        }
        /** dur 틱 동안 남는 구역 (10틱마다 피해). */
        void zone(Location c, float yaw, double len, double w, double r, int dur, double dmg, String el, boolean slow) {
            LivingEntity b = cur; int[] k = {0}; Vector f = dir(yaw);
            plugin.ticker().add(() -> {
                if (closed || b != cur || ++k[0] > dur) return false;
                if (k[0] % 10 != 0) return true;
                for (Player pl : players()) {
                    boolean in;
                    if (r > 0) in = ReaperBoss.flat2(pl.getLocation(), c) <= r * r;
                    else { Vector v = pl.getLocation().toVector().subtract(c.toVector()); double along = v.getX() * f.getX() + v.getZ() * f.getZ(), side = Math.abs(v.getX() * f.getZ() - v.getZ() * f.getX()); in = along >= 0 && along <= len && side <= w / 2; }
                    if (!in) continue;
                    hurt(pl, dmg, el);
                    if (slow) pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                }
                return true;
            });
        }
        Player pick(List<Player> ps) { return ps.get(ReaperBoss.RNG.nextInt(ps.size())); }
        static boolean alive(Player p) { return p != null && p.isValid() && !p.isDead(); }
        void step(double d) { Location to = ground(cur.getLocation().add(dir(cur.getLocation().getYaw()).multiply(d))); to.setYaw(cur.getLocation().getYaw()); if (to.getBlock().isPassable()) cur.teleport(to); }

        /* ---------------------------------------------------------------- 짤패턴 */

        void pattern() {
            List<String> pool = new ArrayList<>(def().patterns());
            if (enraged) { pool.add(def().rage()); pool.add(def().rage()); }
            pool.remove(last);
            run(pool.get(ReaperBoss.RNG.nextInt(pool.size())));
        }

        void run(String id) {
            List<Player> ps = players();
            if (ps.isEmpty() || !bossAlive()) return;
            Player near = nearest(ps, cur.getLocation()), far = farthest(ps, cur.getLocation()), any = pick(ps);
            int len = switch (id) {
                case "gate" -> gateStrike();
                // 솜니아
                case "orb" -> orb(ps); case "gaze" -> gaze(); case "lullaby" -> lullaby(); case "echo" -> echo(any); case "yawn" -> yawn();
                case "fog" -> fog(ps); case "eyelid" -> eyelid(); case "fullmoon" -> fullMoon(far);
                // 룰레타
                case "cards" -> cards(near); case "dice" -> dice(far); case "domino" -> domino(near); case "coin" -> coin(); case "slot" -> slot();
                case "sectors" -> sectors(); case "hands" -> hands(any); case "allin" -> allIn(near); case "jackpot" -> jackpot();
                // 벨자르
                case "debt" -> debt(ps); case "invert" -> invert(); case "chain" -> chain(far); case "mines" -> mines(ps); case "halo" -> halo();
                case "mirror" -> mirror(any); case "spears" -> spears(any); case "interest" -> interest(near); case "final" -> finalClaim();
                // 해방된 악마
                case "combo" -> combo(near); case "leap" -> leap(far); case "dash" -> dash(any); case "throw" -> swordThrow(far); case "fissure" -> fissure(near);
                case "breath" -> breath(near); case "pillars" -> pillars(any); case "wings" -> wings(near); case "roar" -> roar(); case "doom" -> doom();
                default -> 20;
            };
            if (!id.equals("gate")) last = id;
            nextAt = t + len + sp(enraged ? 14 : 24);
        }

        /** 성문 강타 (모든 보스): 성문 앞 20칸으로 물러나 1.5초 뒤 성문 쪽 직선 24 × 5(악마 6). 성문 체력 5% (악마 8%), 길 위 플레이어 40. */
        int gateStrike() {
            Location p = polar(center, fyaw, 20); p.setYaw(toGate()); cur.teleport(p);
            A("gate_strike");
            boolean demon = stage == 3;
            line(ground(p), toGate(), 24, demon ? 6 : 5, 30, 40, demon ? "fire" : "dark", "gate_strike", 1.5);
            then(30, () -> gateDamage(gateMax * (demon ? 0.08 : 0.05)));
            return 60;
        }

        /* ---------- 몽마 솜니아: 느리고, 따라오고, 오래 남는다 ---------- */

        /** 꿈방울 셋이 4초 동안 쫓아와 닿으면 터짐(반경 2.5, 16). */
        int orb(List<Player> ps) {
            A("orb_summon");
            then(16, () -> {
                for (int i = 0; i < 3; i++) {
                    Player tg = ps.get(i % ps.size());
                    LivingEntity m = prop("CA_Siege_marker", polar(at(), cur.getLocation().getYaw() + (i - 1) * 70, 3));
                    if (m == null) continue;
                    UUID h = Fx.follow(m, V() + "dream_orb", 100, 0); int[] k = {0}; LivingEntity b = cur;
                    plugin.ticker().add(() -> {
                        boolean end = closed || b != cur || !m.isValid() || ++k[0] > 80;
                        if (!end && alive(tg)) {
                            Vector v = tg.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
                            if (v.lengthSquared() < 1.6 * 1.6) end = true; else m.teleport(ground(m.getLocation().add(v.normalize().multiply(0.22))));
                        }
                        if (!end) return true;
                        Fx.stop(h);
                        if (m.isValid()) { Location c = ground(m.getLocation()); m.remove(); if (!closed && b == cur) circle(c, 2.5, 3, 16, "dark", "dream_orb_pop", 0.15); }
                        return false;
                    });
                }
            });
            return 110;
        }
        /** 돌아가는 시선: 광선 3개(26 × 1.6)가 1초 뒤부터 5초 동안 초당 40° 회전. 닿으면 14 (0.75초마다). */
        int gaze() {
            A("gaze_sweep");
            Location c = at(); float base = cur.getLocation().getYaw(); cur.setAI(false);
            for (int k = 0; k < 3; k++) { fx("gaze_sweep", c, base + k * 120, 140); warnOnly(Shape.LINE, c, base + k * 120, 26, 1.6, 0, 20); }
            LivingEntity b = cur; int[] n = {0}; Map<UUID, Integer> hitAt = new HashMap<>();
            plugin.ticker().add(() -> {
                if (closed || b != cur || !bossAlive() || ++n[0] > 120) { if (b.isValid()) b.setAI(true); return false; }
                if (n[0] < 20 || n[0] % 2 != 0) return true;
                double turn = GAZE_SIGN * 40.0 * (n[0] - 20) / 20;
                for (Player pl : players()) {
                    if (n[0] - hitAt.getOrDefault(pl.getUniqueId(), -100) < 15) continue;
                    Vector v = pl.getLocation().toVector().subtract(c.toVector());
                    for (int k = 0; k < 3; k++) {
                        Vector f = dir((float) (base + k * 120 + turn)); double along = v.getX() * f.getX() + v.getZ() * f.getZ(), side = Math.abs(v.getX() * f.getZ() - v.getZ() * f.getX());
                        if (along >= 0.8 && along <= 26 && side <= 1.2) { hitAt.put(pl.getUniqueId(), n[0]); hurt(pl, 14, "light"); break; }
                    }
                }
                return true;
            });
            return 130;
        }
        /** 자장가 물결: 느린 고리 세 번 (반경 2 → 18, 2.2초). 뛰어넘는다. 각 14. */
        int lullaby() {
            A("lullaby_wave");
            Location c = at();
            for (int i = 0; i < 3; i++) { int d = i * 22; then(d + 1, () -> { fx("lullaby_wave", c, 0, 70); ring(c, 2, 18, 14, 44, 14, "dark"); }); }
            return 110;
        }
        /** 악몽의 잔상: 3초 동안 지나온 자리(0.3초마다)가 1.5초 뒤에 터짐 (반경 2, 16). */
        int echo(Player tg) {
            A("echo_cast");
            for (int i = 0; i < 10; i++) then(8 + i * 6, () -> { if (alive(tg)) circle(ground(tg.getLocation()), 2, 30, 16, "dark", "nightmare_echo", 0.5); });
            return 100;
        }
        /** 하품: 2초 동안 16칸 안을 끌어당기고 2.3초에 반경 7 폭발 (30). */
        int yawn() {
            A("yawn");
            Location c = at(); fx("yawn_pull", c, 0, 60);
            circle(c, 7, 46, 30, "dark", "yawn_burst", 0.3);
            LivingEntity b = cur; int[] k = {0};
            plugin.ticker().add(() -> {
                if (closed || b != cur || ++k[0] > 40) return false;
                if (k[0] % 2 == 0) for (Player pl : players()) {
                    Vector v = c.toVector().subtract(pl.getLocation().toVector()).setY(0); double d = v.length();
                    if (d > 1.5 && d <= 16) pl.setVelocity(pl.getVelocity().add(v.normalize().multiply(0.11)));
                }
                return true;
            });
            return 70;
        }
        /** 잠의 안개: 플레이어 발밑에 8초 남는 구역 셋 (반경 4.6, 0.5초마다 5 + 둔화). */
        int fog(List<Player> ps) {
            A("fog_cast");
            for (int i = 0; i < 3; i++) { Player tg = ps.get(i % ps.size()); then(12 + i * 12, () -> { Location c = alive(tg) ? ground(tg.getLocation()) : randomIn(field, arena - 6); warnOnly(Shape.CIRCLE, c, 0, 4.6, 0, 0, 14); fx("sleep_fog", c, 0, 170); later(14, () -> zone(c, 0, 0, 0, 4.6, 150, 5, "dark", true)); }); }
            return 56;
        }
        /** 눈꺼풀: 전장 양끝에서 가운데로 띠(36 × 6)가 세 번에 걸쳐 닫힘. 가운데(±6)가 안전. 각 22. */
        int eyelid() {
            A("eyelid");
            int[] at = {21, 15, 9};
            for (int i = 0; i < 3; i++) { int z = at[i], d = 2 + i * 14; then(d, () -> { for (int s = -1; s <= 1; s += 2) rect(ground(field.clone().add(dir(fyaw).multiply(s * z))), fyaw, 36, 6, 16, 22, "dark", "eyelid_band", 0.8); }); }
            return 70;
        }
        /** 만월 낙하 (광폭화 뒤): 가장 먼 사람 자리에 반경 8, 2초 (45). */
        int fullMoon(Player tg) {
            A("full_moon");
            circle(ground(tg.getLocation()), 8, 40, 45, "dark", "full_moon", 2.0);
            return 64;
        }

        /* ---------- 도박의 악마 룰레타: 운과 속임수, 던지고 튀고 되돌아온다 ---------- */

        /** 카드 부메랑: 5갈래 직선(18 × 1.5)이 나갔다가 1.25초에 되돌아온다. 각 14. */
        int cards(Player tg) {
            faceCur(tg.getLocation()); A("card_boomerang");
            Location from = at(); float base = yawTo(from, tg.getLocation());
            for (int o = -30; o <= 30; o += 15) {
                float yaw = base + o; Location end = ground(from.clone().add(dir(yaw).multiply(18)));
                line(from, yaw, 18, 1.5, 8, 14, "dark", "card_out", 0.4);
                then(14, () -> line(end, yaw + 180, 18, 1.5, 11, 14, "dark", "card_back", 0.25));
            }
            return 40;
        }
        /** 튕기는 주사위: 8칸씩 세 번 튄 뒤 눈에 따라 폭발 (1~2 반경 2 / 3~4 반경 4 / 5~6 반경 6.5). */
        int dice(Player tg) {
            faceCur(tg.getLocation()); A("dice_roll");
            Location from = at(); float yaw = yawTo(from, tg.getLocation()); int n = 1 + ReaperBoss.RNG.nextInt(6);
            for (int h = 0; h < 3; h++) { Location p = ground(from.clone().add(dir(yaw).multiply(h * 8))); then(4 + h * 10, () -> fx("dice_hop", p, yaw, 20)); }
            Location land = ground(from.clone().add(dir(yaw).multiply(24)));
            double r = n <= 2 ? 2 : n <= 4 ? 4 : 6.5; String sys = n <= 2 ? "dice_pop_s" : n <= 4 ? "dice_pop_m" : "dice_pop_l";
            then(20, () -> circle(land, r, 20, 14 + n * 4, "dark", sys, 0.3));
            return 56;
        }
        /** 칩 도미노: 두 줄(20 × 3)이 차례로 쓰러져 달려감. 각 16. */
        int domino(Player tg) {
            faceCur(tg.getLocation()); A("chip_domino");
            Location from = at(); float base = yawTo(from, tg.getLocation());
            for (int s = -1; s <= 1; s += 2) { float yaw = base + s * 20; int d = s < 0 ? 1 : 7; then(d, () -> { warnOnly(Shape.LINE, from, yaw, 20, 3, 0, 10); fx("chip_domino", from, yaw, 40); runLane(from, yaw, 20, 3, 10, 16, 16, "earth"); }); }
            return 44;
        }
        /** 동전 던지기: 1.35초에 앞면 = 안쪽 반경 8 / 뒷면 = 바깥 8~16. 결과는 떨어지기 0.6초 전에야 보인다. 26. */
        int coin() {
            A("coin_flip");
            Location c = at(); fx("coin_toss", c, 0, 40); boolean heads = ReaperBoss.RNG.nextBoolean();
            then(15, () -> { if (heads) circle(c, 8, 12, 26, "dark", "coin_heads_in", 0.3); else donut(c, 8, 16, 12, 26, "dark", "coin_tails_out", 0.3); });
            return 50;
        }
        /** 슬롯머신: 전장을 가로지르는 다섯 줄(40 × 4) 가운데 세 줄. 각 24. */
        int slot() {
            A("slot_pull");
            List<Integer> lanes = new ArrayList<>(List.of(-14, -7, 0, 7, 14)); Collections.shuffle(lanes, ReaperBoss.RNG);
            for (int i = 0; i < 3; i++) {
                Location from = ground(field.clone().add(dir(fyaw + 90).multiply(lanes.get(i))).subtract(dir(fyaw).multiply(20))); int d = 1 + i * 3;
                then(d, () -> line(from, fyaw, 40, 4, 20, 24, "dark", "slot_lane", 1.0));
            }
            return 50;
        }
        /** 룰렛 난사: 60° 부채꼴(반경 16) 세 구역이 0.6초 간격으로. 각 24. */
        int sectors() {
            A("call_bets");
            Location c = at(); float base = ReaperBoss.RNG.nextInt(6) * 60; String[] sys = {"sector_red", "sector_gold", "sector_violet"};
            for (int i = 0; i < 3; i++) { int k = i; then(1 + i * 12, () -> cone(c, base + k * 120, 16, 60, 20, 24, "dark", sys[k], 1.0)); }
            return 60;
        }
        /** 딜러의 여섯 손: 한 사람을 따라가는 작은 내려찍기 6연타 (반경 2.5, 0.35초 예고). 각 12. */
        int hands(Player tg) {
            A("six_hands");
            for (int i = 0; i < 6; i++) then(4 + (int) Math.round(i * 6.4), () -> { if (alive(tg)) circle(ground(tg.getLocation()), 2.5, 7, 12, "earth", "hand_slam", 0.35); });
            return 60;
        }
        /** 올인: 앞 반원 반경 12 (34). */
        int allIn(Player tg) {
            faceCur(tg.getLocation()); A("all_in");
            cone(at(), cur.getLocation().getYaw(), 12, 180, 17, 34, "dark", "all_in", 0.9);
            return 44;
        }
        /** 잭팟 (광폭화 뒤): 동전 24개가 나선으로 쏟아짐 (반경 2.5, 각 16). */
        int jackpot() {
            A("jackpot");
            Location c = ground(field);
            for (int i = 0; i < 24; i++) { double a = i * 2.4, d = 2 + i * 0.6; Location p = ground(c.clone().add(Math.sin(a) * d, 0, Math.cos(a) * d)); then(6 + i * 2, () -> circle(p, 2.5, 14, 16, "earth", "jackpot", 0.7)); }
            return 80;
        }

        /* ---------- 천사 벨자르: 흰색과 검은색, 받은 만큼 되돌려 받는다 ---------- */

        /** 청구서: 모두에게 표식 → 3초 뒤 그때 서 있는 자리에서 터짐 (반경 4, 22). 흩어져야 한다. */
        int debt(List<Player> ps) {
            A("debt_mark");
            for (Player pl : ps) { Fx.follow(pl, V() + "debt_mark", 60, 0); then(54, () -> { if (alive(pl)) circle(ground(pl.getLocation()), 4, 6, 22, "dark", "debt_due", 0.15); }); }
            return 80;
        }
        /** 흑백 반전: 흰 안쪽(반경 8) → 검은 바깥(8~18), 또는 그 반대. 각 26. */
        int invert() {
            boolean whiteIn = ReaperBoss.RNG.nextBoolean(); A(whiteIn ? "invert_white_first" : "invert_black_first");
            Location c = at();
            if (whiteIn) { circle(c, 8, 18, 26, "light", "white_in", 0.9); then(16, () -> donut(c, 8, 18, 18, 26, "dark", "black_out", 0.9)); }
            else { donut(c, 8, 18, 18, 26, "light", "white_out", 0.9); then(16, () -> circle(c, 8, 18, 26, "dark", "black_in", 0.9)); }
            return 60;
        }
        /** 사슬 포박: 직선 24 × 2 에 맞으면 끌려와 → 앞 부채꼴 100° 반경 5 할퀴기 (24). */
        int chain(Player tg) {
            faceCur(tg.getLocation()); A("chain_pull");
            Location from = at(); float yaw = yawTo(from, tg.getLocation());
            strike(Shape.LINE, from, yaw, 24, 2, 0, 14, 10, "dark", V() + "debt_chain", T(0.7), hit -> { for (Player pl : hit) { Location to = ground(from.clone().add(dir(yaw).multiply(3))); to.setYaw(yaw + 180); pl.teleport(to); } });
            then(20, () -> cone(from, yaw, 5, 100, 6, 24, "dark", "claw", 0.3));
            return 50;
        }
        /** 깃털 지뢰: 다섯 곳에 박힌 뒤 2.5초에 검은 파동과 함께 전부 터짐 (반경 3.5, 22). */
        int mines(List<Player> ps) {
            A("feather_scatter");
            List<Location> spots = new ArrayList<>();
            for (int i = 0; i < 5; i++) { Player tg = ps.get(i % ps.size()); Location p = ground(tg.getLocation().clone().add(ReaperBoss.RNG.nextGaussian() * 4, 0, ReaperBoss.RNG.nextGaussian() * 4)); spots.add(p); then(8 + i * 2, () -> fx("feather_mine", p, 0, 130)); }
            then(40, () -> { A("black_wave"); for (Location p : spots) circle(p, 3.5, 10, 22, "dark", "mine_burst", 0.2); });
            return 80;
        }
        /** 좁혀드는 후광: 고리가 바깥(18)에서 안(1)으로 1.6초. 뛰어넘는다. 20. */
        int halo() {
            A("halo_close");
            Location c = at(); fx("halo_close", c, 0, 60); ring(c, 18, 1, 16, 32, 20, "light");
            return 60;
        }
        /** 거울 일격: 한 사람을 사이에 두고 마주 보는 두 줄(20 × 5)이 동시에. 24. */
        int mirror(Player tg) {
            faceCur(tg.getLocation()); A("mirror_strike");
            Location c = ground(tg.getLocation()); float yaw = yawTo(at(), c);
            Location a = ground(c.clone().subtract(dir(yaw).multiply(10))), b = ground(c.clone().add(dir(yaw).multiply(10)));
            line(a, yaw, 20, 5, 16, 24, "light", "mirror_white", 0.8);
            strike(Shape.LINE, b, yaw + 180, 20, 5, 0, 16, 0, "dark", V() + "mirror_black", T(0.8), null);
            return 44;
        }
        /** 행진하는 창: 한 사람을 따라 0.25초마다 창 8개 (반경 2, 14). */
        int spears(Player tg) {
            A("spear_march");
            for (int i = 0; i < 8; i++) then(4 + i * 5, () -> { if (alive(tg)) circle(ground(tg.getLocation()), 2, 9, 14, "light", "spear_step", 0.45); });
            return 60;
        }
        /** 복리: 같은 자리에 세 번, 터질 때마다 커진다 (반경 3 → 5.5 → 8, 14 / 20 / 28). */
        int interest(Player tg) {
            A("interest");
            Location c = ground(tg.getLocation()); double[] r = {3, 5.5, 8}; double[] dmg = {14, 20, 28};
            for (int i = 0; i < 3; i++) { int k = i; then(1 + i * 20, () -> circle(c, r[k], 16, dmg[k], "dark", "interest_" + (k + 1), 0.8)); }
            return 70;
        }
        /** 최후 청구 (광폭화 뒤): 전장을 12칸 체스판으로 나눠 흰 칸 → 1.2초 뒤 검은 칸. 각 40. */
        int finalClaim() {
            A("final_claim");
            for (int i = 0; i < 12; i++) {
                int col = i % 3, row = i / 3; boolean white = (col + row) % 2 == 0;
                Location c = ground(field.clone().add(dir(fyaw + 90).multiply((col - 1) * 12)).add(dir(fyaw).multiply(-18 + row * 12)));
                then(white ? 1 : 25, () -> rect(c, fyaw, 12, 12, 20, 40, white ? "light" : "dark", white ? "final_white" : "final_black", 1.0));
            }
            return 80;
        }

        /* ---------- 해방된 악마: 크고, 빠르고, 몸으로 직접 온다 ---------- */

        /** 대검 3연격: 오른쪽 → 왼쪽 휘두르기(부채꼴 150° 반경 10, 24) → 정면 내려베기(18 × 5, 32). 벨 때마다 3칸 다가온다. */
        int combo(Player tg) {
            faceCur(tg.getLocation()); A("swing_r");
            cone(at(), cur.getLocation().getYaw(), 10, 150, 9, 24, "fire", "swing_r", 0.45);
            then(13, () -> { if (alive(tg)) faceCur(tg.getLocation()); step(3); A("swing_l"); cone(at(), cur.getLocation().getYaw(), 10, 150, 9, 24, "fire", "swing_l", 0.45); });
            then(26, () -> { if (alive(tg)) faceCur(tg.getLocation()); step(3); A("cleave"); line(at(), cur.getLocation().getYaw(), 18, 5, 10, 32, "fire", "cleave", 0.5); });
            return 52;
        }
        /** 도약 강타: 가장 먼 사람 자리로 뛰어 반경 6 (34) → 퍼지는 충격파 (반경 6 → 24, 뛰어넘기, 18). */
        int leap(Player tg) {
            faceCur(tg.getLocation()); A("leap");
            Location c = ground(tg.getLocation()); c.setYaw(yawTo(at(), c));
            circle(c, 6, 14, 34, "fire", "leap_crater", 0.7);
            then(12, () -> cur.teleport(c));
            then(14, () -> { fx("leap_wave", c, 0, 40); ring(c, 6, 24, 0, 24, 18, "fire"); });
            return 60;
        }
        /** 지옥 돌진: 직선 32 × 6 (36) → 지나간 길이 5초 동안 불탄다 (0.5초마다 6). */
        int dash(Player tg) {
            faceCur(tg.getLocation()); A("dash");
            Location from = at(); float yaw = yawTo(from, tg.getLocation());
            line(from, yaw, 32, 6, 16, 36, "fire", "dash", 0.8);
            then(16, () -> { Location end = ground(from.clone().add(dir(yaw).multiply(30))); end.setYaw(yaw); if (end.getBlock().isPassable()) cur.teleport(end); fx("dash_fire", from, yaw, 110); zone(from, yaw, 32, 6, 0, 100, 6, "fire", false); });
            return 50;
        }
        /** 마검 투척: 가장 먼 사람 자리에 꽂혀 1.9초 뒤 반경 7 폭발 (40) → 마검이 주인에게 되돌아오며 벰 (직선 폭 3, 26). */
        int swordThrow(Player tg) {
            faceCur(tg.getLocation()); A("sword_throw");
            Location c = ground(tg.getLocation());
            circle(c, 7, 38, 40, "dark", "sword_throw", 1.9);
            then(44, () -> { A("sword_recall"); float back = yawTo(c, at()); double len = Math.min(22, Math.sqrt(ReaperBoss.flat2(c, at()))); line(c, back, Math.max(4, len), 3, 6, 26, "dark", "sword_recall", 0.3); });
            return 80;
        }
        /** 지면 가르기: 균열 세 줄(28 × 3)이 0.25초 간격으로 달려감. 각 26. */
        int fissure(Player tg) {
            faceCur(tg.getLocation()); A("fissure");
            Location from = at(); float base = yawTo(from, tg.getLocation());
            for (int i = -1; i <= 1; i++) { float yaw = base + i * 18; then(1 + (i + 1) * 5, () -> { warnOnly(Shape.LINE, from, yaw, 28, 3, 0, 10); fx("fissure_run", from, yaw, 44); runLane(from, yaw, 28, 3, 10, 18, 26, "earth"); }); }
            return 50;
        }
        /** 지옥불 숨결: 30° 부채꼴(반경 16) 일곱 칸을 한쪽에서 반대쪽으로 훑는다. 각 22. */
        int breath(Player tg) {
            faceCur(tg.getLocation()); A("breath");
            Location from = at(); float base = cur.getLocation().getYaw();
            for (int i = 0; i < 7; i++) { float yaw = base + BREATH_SIGN * (-90 + i * 30); then(4 + (int) Math.round(i * 4.4), () -> cone(from, yaw, 16, 30, 5, 22, "fire", "breath_seg", 0.25)); }
            return 60;
        }
        /** 추적 불기둥: 한 사람 발밑에 0.4초마다 여섯 번 (반경 2.5, 20). */
        int pillars(Player tg) {
            A("pillar_cast");
            for (int i = 0; i < 6; i++) then(2 + i * 8, () -> { if (alive(tg)) circle(ground(tg.getLocation()), 2.5, 12, 20, "fire", "hell_pillar", 0.6); });
            return 70;
        }
        /** 네 날개 강풍: 앞 부채꼴 160° 반경 18, 맞으면 크게 밀려난다 (20). */
        int wings(Player tg) {
            faceCur(tg.getLocation()); A("wing_gust");
            Location from = at();
            strike(Shape.CONE, from, cur.getLocation().getYaw(), 18, 0, 160, 16, 20, "wind", V() + "wing_storm", T(0.8), hit -> { for (Player pl : hit) knock(pl, from, 1.6, 0.5); });
            return 44;
        }
        /** 포효: 느린 파동 세 번 (반경 2 → 28, 1.6초). 뛰어넘는다. 각 16. */
        int roar() {
            A("roar");
            Location c = at();
            for (int i = 0; i < 3; i++) then(12 + i * 18, () -> { fx("roar_wave", c, 0, 56); ring(c, 2, 28, 10, 32, 16, "dark"); });
            return 90;
        }
        /** 종말의 일격 (광폭화 뒤): 전장 절반(18 × 48) → 1.6초 뒤 반대쪽 절반. 각 60. */
        int doom() {
            cur.teleport(polar(center, fyaw, lane - 6)); cur.setRotation(toGate(), 0); A("doom");
            int first = ReaperBoss.RNG.nextBoolean() ? 1 : -1;
            for (int i = 0; i < 2; i++) { int s = i == 0 ? first : -first; Location c = ground(field.clone().add(dir(fyaw + 90).multiply(s * 9))); then(1 + i * 32, () -> rect(c, fyaw, 18, 48, 30, 60, "fire", "doom_half", 1.5)); }
            return 100;
        }
    }

    /* ======================================================================== events */

    static final class Hooks implements Listener {
        /** 성문 몸·소품은 안 맞는다. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void damage(EntityDamageEvent e) {
            if (FIGHTS.containsKey(e.getEntity().getUniqueId()) || e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
        }
        /** 보스가 죽으면 바로 다음 단계로 (5틱 판단을 기다리지 않음). */
        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent e) {
            if (!e.getEntity().getScoreboardTags().contains(MOB_TAG)) return;
            for (Fight f : FIGHTS.values()) if (f.cur != null && f.cur.getUniqueId().equals(e.getEntity().getUniqueId()) && f.phase == Fight.P_BOSS) { f.bossDied(); return; }
        }
    }
}
