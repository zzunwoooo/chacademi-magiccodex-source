package school.chacademia.gimmick;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import school.chacademia.magicmythic.MagicImpactEvent;

/** Registered only when ChAcademiaMagicMythic is enabled: spell hits become "magic:<element>" triggers. */
final class MagicHook implements Listener {
    private final GimmickPlugin plugin;
    private final Map<String, Long> lastHit = new HashMap<>(); // gimmick id -> tick

    MagicHook(GimmickPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void impact(MagicImpactEvent e) {
        var hits = plugin.world().near(e.getLocation(), Math.max(0.3, e.getRadius()));
        if (hits.isEmpty()) return;
        long now = plugin.puzzles().now();
        long gap = plugin.getConfig().getLong("magic-hit-gap-ticks", 10);
        if (lastHit.size() > 4096) lastHit.values().removeIf(t -> t < now - 200);
        Player who = e.getCaster() instanceof Player p ? p : null;
        String element = e.getElement() == null || e.getElement().isBlank() ? "any" : e.getElement().toLowerCase(Locale.ROOT);
        for (Gimmick g : hits) {
            if (e.getKind() == MagicImpactEvent.Kind.BOLT && plugin.getConfig().getBoolean("magic-blocks-bolts", true)) e.setCancelled(true);
            Long last = lastHit.get(g.id);
            if (last != null && now - last < gap) continue;
            lastHit.put(g.id, now);
            plugin.puzzles().trigger(g, "magic:" + element, who);
            if (plugin.debug()) plugin.getLogger().info("[마법] " + g.id + " <- " + element + " (" + e.getKind() + ")");
        }
    }
}
