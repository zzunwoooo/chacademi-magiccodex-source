package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;
import school.chacademia.magicmythic.ReaperBoss.Fx;

/**
 * 악마 1인 레이드(룰레타·솜니아) 공용 진행 바탕 (2026-11-09). 예고 → 같은 모양 판정, 모델 모션, 소품 소환, 채팅 + 타이틀,
 * 플레이어 찾기 같은 것을 한 곳에 둔다. 실제 진행은 {@link RouletteBoss}, {@link SomniaBoss}.
 * 서버 부담: 보스 하나당 Ticker 1개, 예고마다 지연 작업 1개.
 */
abstract class DemonFight implements Ticker.Tick {
    final MagicMythicPlugin plugin; LivingEntity boss; final Location center; final double arena;
    final String prefix, animPrefix, propTag;
    int t; boolean closed;

    DemonFight(MagicMythicPlugin plugin, LivingEntity boss, double arena, String prefix, String animPrefix, String propTag) {
        this.plugin = plugin; this.boss = boss; this.center = boss.getLocation().clone(); this.arena = arena;
        this.prefix = prefix; this.animPrefix = animPrefix; this.propTag = propTag;
    }

    @Override public String kind() { return "boss"; }

    /* ---------------------------------------------------------------- 대상 */

    // [기능:보스대상100칸] 11/09 오너 "보스 즉사 범위 100칸 정도": 경기장 밖으로 멀리 도망가 즉사·전체 패턴을 피하던 것 막기
    static final double REACH = 100, REACH_Y = 40;
    /** 경기장 안 생존 플레이어 (가로 max(arena + 6, 100)칸, 높이 ±40). */
    List<Player> playersNear(Location c) {
        List<Player> out = new ArrayList<>();
        double r = Math.max(arena + 6, REACH);
        for (Player pl : c.getWorld().getPlayers()) {
            if (pl.isDead() || pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
            if (ReaperBoss.flat2(pl.getLocation(), c) <= r * r && Math.abs(pl.getLocation().getY() - c.getY()) < REACH_Y) out.add(pl);
        }
        return out;
    }
    abstract List<Player> players();

    Player nearest(List<Player> ps, Location from) { return ps.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from))).orElse(null); }
    Player farthest(List<Player> ps, Location from) { return ps.stream().max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from))).orElse(null); }

    double maxHp() { var a = boss.getAttribute(Attribute.MAX_HEALTH); return a == null ? boss.getHealth() : a.getValue(); }
    static double maxOf(Player p) { return ReaperBoss.Fight.maxOf(p); }

    /* ---------------------------------------------------------------- 말 */

    void say(String line) { for (Player pl : players()) pl.sendMessage(ReaperBoss.LEG.deserialize(prefix + line)); }
    void title(String main, String sub) { for (Player pl : players()) ReaperBoss.Fight.title(pl, main, sub); }
    void tell(Player pl, String main, String sub) { ReaperBoss.Fight.title(pl, main, sub); }

    /* ---------------------------------------------------------------- 예고 → 판정 */

    /**
     * 모드 빨간 예고 → 같은 모양 안 플레이어에게 피해 dmg (element). warn=false 면 예고 없이 같은 시각 판정(체술).
     * vfx 는 판정 lead 틱 전에 재생 (팩 안 판정 시각에 맞춤). after 는 판정 직후.
     */
    void strike(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks, double dmg, String element,
                String vfx, int lead, Consumer<List<Player>> after) {
        Show s = show(shape, at, yaw, size, width, angle, ticks);
        if (s == null) return;
        if (vfx != null) later(Math.max(1, ticks - lead), () -> Fx.effect(plugin, vfx, at, yaw, 60));
        plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {
            if (closed || !allowHits()) return;
            List<Player> hit = inside(show);
            if (dmg > 0) for (Player pl : hit) hurt(pl, dmg, element);
            if (after != null) after.accept(hit);
        });
    }
    /** 예고만 (판정은 호출한 쪽이 직접). */
    void warnOnly(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks) {
        Show s = show(shape, at, yaw, size, width, angle, ticks);
        if (s != null) plugin.telegraphs().start(s, boss, boss.getWorld(), true, true, show -> {});
    }
    Show show(Shape shape, Location at, float yaw, double size, double width, double angle, int ticks) {
        try {
            return new Show(plugin.telegraphs().id(), shape, at.getX(), Telegraphs.ground(at), at.getZ(), yaw,
                (float) Math.min(size, TelegraphProtocol.MAX_SIZE), (float) Math.min(width, TelegraphProtocol.MAX_WIDTH), (float) (angle <= 0 ? 60 : angle), Math.max(1, ticks), TelegraphProtocol.RED);
        } catch (IllegalArgumentException e) { return null; }
    }
    List<Player> inside(Show s) {
        List<Player> out = new ArrayList<>();
        for (Player pl : players()) if (Math.abs(pl.getLocation().getY() - s.y()) < 3.5 && TelegraphProtocol.inside(s, pl.getLocation().getX(), pl.getLocation().getZ(), 0.3)) out.add(pl);
        return out;
    }
    /** 판정 허용 (기믹·무적 중엔 짤패턴 판정 끔). */
    boolean allowHits() { return true; }
    /** 패턴 피해 = dmg × 배율. */
    double mult() { return 1.0; }
    void hurt(Player pl, double dmg, String element) {
        hurting = true;
        try { plugin.targets().hurt(boss, pl, dmg * mult(), element); } finally { hurting = false; }
    }
    /** 최대 체력 비율 피해 (기믹 실패 등, 배율 없음). */
    void hurtPct(Player pl, double pct, String element) {
        hurting = true;
        try { plugin.targets().hurt(boss, pl, maxOf(pl) * pct, element); } finally { hurting = false; }
    }
    static boolean hurting;

    void knock(Player pl, Location from, double power, double up) {
        Vector v = pl.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1e-3) v = new Vector(0, 0, 1);
        pl.setVelocity(v.normalize().multiply(power).setY(up));
    }

    /* ---------------------------------------------------------------- 모션 · 소품 · 일정 */

    boolean castAt(LivingEntity caster, String skill) {
        try { return MythicBukkit.inst().getAPIHelper().castSkill(caster, skill, caster, caster.getLocation(), List.<Entity>of(), List.of(caster.getLocation()), 1f, d -> {}); }
        catch (RuntimeException | LinkageError e) { return false; }
    }
    /** 보스 모델 모션 = {animPrefix}_a_&lt;이름&gt;. */
    void anim(String name) { castAt(boss, animPrefix + "_a_" + name); }
    void animOn(LivingEntity e, String skill) { if (e != null && e.isValid()) castAt(e, skill); }

    LivingEntity prop(String mob, Location at) {
        try {
            var am = MythicBukkit.inst().getMobManager().spawnMob(mob, at);
            if (am == null) return null;
            Entity e = am.getEntity().getBukkitEntity();
            e.addScoreboardTag(propTag); e.setPersistent(false);
            return e instanceof LivingEntity le ? le : null;
        } catch (RuntimeException | LinkageError ex) { plugin.getLogger().warning("소품 소환 실패: " + mob + " " + ex); return null; }
    }
    void removeLater(Entity e, int ticks) { plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (e != null && e.isValid()) e.remove(); }, Math.max(1, ticks)); }

    void later(int ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!closed && boss.isValid() && !boss.isDead()) r.run(); }, Math.max(1, ticks));
    }

    /* ---------------------------------------------------------------- 좌표 */

    void face(Location to) { Location l = boss.getLocation(); boss.setRotation(yawTo(l, to), 0); }
    static float yawTo(Location from, Location to) { return yawOf(to.toVector().subtract(from.toVector())); }
    static float yawOf(Vector v) { return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())); }
    static Vector dir(float yaw) { double a = Math.toRadians(yaw); return new Vector(-Math.sin(a), 0, Math.cos(a)); }
    static Location ground(Location at) { return ReaperBoss.ground(at); }
    /** 중심에서 yaw 방향 d 만큼 (바닥 높이). */
    static Location polar(Location c, float yaw, double d) { return ground(c.clone().add(dir(yaw).multiply(d))); }
    /** 중심 기준 각도 (마인크래프트 yaw). */
    static float bearing(Location c, Location p) { return yawTo(c, p); }
    static float wrap(float deg) { float d = deg % 360; return d < 0 ? d + 360 : d; }
    Location randomIn(Location c, double r) {
        double a = ReaperBoss.RNG.nextDouble() * Math.PI * 2, d = Math.sqrt(ReaperBoss.RNG.nextDouble()) * r;
        return ground(c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d));
    }
}
