package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.io.File;
import java.util.*;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * /마법미몹 시험 <id> (admin): run a spell's gameplay skill directly, skipping mana, cooldown, permission and the VFX
 * catalogue - for testing new spells before their VFX are enabled. Reads the route (skill, target, range, damage) from
 * plugins/MagicCodexBridge/spell-runtime.yml, picks the target like the Bridge does.
 */
final class TestCast {
    private TestCast() {}

    static String cast(MagicMythicPlugin plugin, Player p, String id) {
        File file = new File(plugin.getDataFolder().getParentFile(), "MagicCodexBridge/spell-runtime.yml");
        if (!file.isFile()) return "spell-runtime.yml 이 없습니다: " + file;
        var route = YamlConfiguration.loadConfiguration(file).getConfigurationSection("spells." + id);
        if (route == null) return "라우트가 없는 마법입니다: " + id;
        String skill = route.getString("skill", ""), target = route.getString("target", "SELF");
        double range = route.getDouble("range", 24), damage = route.getDouble("base-damage", 0) + route.getDouble("power-ratio", 0) * Links.power(p);
        Entity chosen = null;
        Location at = p.getLocation();
        if (!target.equals("SELF") && !target.equals("BLOCK")) {
            var eye = p.getEyeLocation();
            var hit = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.55, e -> !e.equals(p) && plugin.canTarget(p, e));
            at = hit != null ? hit.getHitPosition().toLocation(p.getWorld()) : eye.clone().add(eye.getDirection().multiply(range));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity le) chosen = le;
            if (target.endsWith("ENTITY") && chosen == null) return "대상이 필요합니다.";
        }
        final double dmg = damage;
        try {
            boolean ok = MythicBukkit.inst().getAPIHelper().castSkill(p, skill, p, p.getLocation(), chosen == null ? List.of() : List.of(chosen), List.of(at), 1f,
                data -> { data.getVariables().putDouble("damage", dmg); data.getVariables().putDouble("magic_power", Links.power(p)); });
            return (ok ? "시험 시전: " : "시전 실패: ") + id + " (" + skill + ", 피해값 " + String.format("%.1f", dmg) + ")";
        } catch (RuntimeException | LinkageError e) {
            return "시전 오류: " + e.getClass().getSimpleName();
        }
    }
}
