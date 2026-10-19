package school.chacademia.magicmythic;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;

/**
 * chatelegraph - enemy area attack with a red ground warning (예고 장판) first. Owner 2026-10-19: area attacks that
 * are not body strikes always warn; enemy warnings are red.
 *
 *   shape=circle|line|cone   at=target|caster (circle default target; line/cone start at the caster, aimed at the target)
 *   r=3 (circle/cone radius)  length=8 width=2 (line)  angle=60 (cone, full degrees)  ticks=20 (warning)
 *   flat=6 / mult (damage, Ctx.dmg)  element=fire  ignite=60  status=burn:60,slow:40:0.3 (Targets.sideEffects)
 *   hits=players|enemies (default players: mob skills hit players only)  height=3 (vertical reach above the ground)
 *   vfx=blazepillar/erupt (PortableVFX id; "claude:" added when missing)  vfxlead=2 (ticks before the hit)
 *   vfxref=3 (authored radius: scale = r / vfxref)  tint=FFFFFF (colour-variant mobs)  cancel=true (caster death cancels)
 *
 * Shape, origin and yaw are fixed when the warning starts; the hit uses the same {@link Show}.
 */
final class TelegraphMech extends Mech {
    TelegraphMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        Show show = show(c);
        if (show == null) return false;
        var world = c.caster().getWorld();
        int ticks = show.ticks();
        String vfx = vfxId(p.s("vfx", ""));
        if (!vfx.isEmpty()) {
            int lead = Math.clamp(p.i("vfxlead", 2), 0, ticks);
            float scale = (float) Math.clamp(show.size() / Math.max(0.1, p.d("vfxref", show.size())), 0.25, 4);
            int tint = tint(p.s("tint", "FFFFFF"));
            var at = new Location(world, show.x(), show.y(), show.z());
            int id = show.id();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.telegraphs().live(id)) plugin.reactions().vfx().effect(vfx, at, scale, show.yaw(), 40, tint);
            }, Math.max(1, ticks - lead));
        }
        return plugin.telegraphs().start(show, c.caster(), world, p.b("cancel", true), s -> hit(c.caster(), s, c.dmg(p)));
    }

    Show show(Ctx c) {
        Shape shape = Shape.of(p.s("shape", "circle"));
        if (shape == null) return null;
        LivingEntity caster = c.caster();
        boolean atTarget = p.s("at", shape == Shape.CIRCLE ? "target" : "caster").toLowerCase(Locale.ROOT).startsWith("t");
        Location origin = (atTarget ? c.at() : caster.getLocation()).clone();
        Location aim = c.target() != null ? c.target().getLocation() : c.at();
        float yaw = caster.getLocation().getYaw();
        double dx = aim.getX() - caster.getLocation().getX(), dz = aim.getZ() - caster.getLocation().getZ();
        if (!atTarget && dx * dx + dz * dz > 0.04) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float size = (float) Math.clamp(shape == Shape.LINE ? p.d("length", 8) : p.d("r", 3), 0.5, TelegraphProtocol.MAX_SIZE);
        float width = (float) Math.clamp(p.d("width", 2), 0.3, TelegraphProtocol.MAX_WIDTH);
        try {
            return new Show(plugin.telegraphs().id(), shape, origin.getX(), Telegraphs.ground(origin), origin.getZ(), yaw, size, width,
                (float) p.d("angle", 60), Math.clamp(p.i("ticks", 20), 1, TelegraphProtocol.MAX_TICKS), TelegraphProtocol.RED);
        } catch (IllegalArgumentException e) { return null; }
    }

    private void hit(LivingEntity caster, Show s, double dmg) {
        if (caster == null || !caster.isValid()) return;
        var world = caster.getWorld();
        var center = new Location(world, s.x(), s.y(), s.z());
        boolean playersOnly = !p.s("hits", "players").toLowerCase(Locale.ROOT).startsWith("e");
        double height = Math.clamp(p.d("height", 3), 0.5, 16);
        double reach = (s.shape() == Shape.LINE ? Math.hypot(s.size(), s.width() / 2) : s.size()) + 2;
        for (Entity e : world.getNearbyEntities(center, reach, height + 2, reach)) {
            if (!(e instanceof LivingEntity t) || (playersOnly && !(e instanceof Player))) continue;
            if (!plugin.targets().enemy(caster, t)) continue;
            double feet = t.getLocation().getY();
            if (feet < s.y() - 1 || feet > s.y() + height) continue;
            if (!TelegraphProtocol.inside(s, t.getLocation().getX(), t.getLocation().getZ(), t.getWidth() / 2)) continue;
            if (!plugin.targets().hurt(caster, t, dmg, p.s("element", ""))) continue; // 0 = protection probe only
            plugin.targets().sideEffects(caster, t, p);
        }
    }

    static String vfxId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "claude:" + id;
        return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? id : "";
    }

    static int tint(String raw) {
        try { return Integer.parseInt(raw.trim().replace("#", ""), 16) & 0xFFFFFF; }
        catch (RuntimeException e) { return 0xFFFFFF; }
    }
}
