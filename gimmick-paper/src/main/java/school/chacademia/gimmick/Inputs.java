package school.chacademia.gimmick;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Player inputs: right click (interact / insert item / use portal), left click (attack), standing on, walking into portals. */
final class Inputs implements Listener {
    private final GimmickPlugin plugin;
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private final Map<String, Boolean> standingNodes = new HashMap<>(); // puzzle:node -> last state
    private Method iaByItem, iaId;
    private boolean iaResolved;

    Inputs(GimmickPlugin plugin) {
        this.plugin = plugin;
        int every = Math.max(1, plugin.getConfig().getInt("stand-check-ticks", 4));
        Bukkit.getScheduler().runTaskTimer(plugin, this::standCheck, every, every);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void rightClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Gimmick g = plugin.world().byEntity(e.getRightClicked());
        if (g == null) return;
        Player p = e.getPlayer();
        if (plugin.tool().isTool(p.getInventory().getItemInMainHand())) { e.setCancelled(true); plugin.tool().use(p, g, true); return; }
        if (!g.inPuzzle()) return;
        e.setCancelled(true);
        var node = plugin.puzzles().node(g.puzzle, g.node);
        if (node == null) return;
        // 1) item insert
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!node.collect().isEmpty() && !hand.getType().isAir()) {
            String item = itemId(hand);
            if (plugin.puzzles().collect(g, item, p)) {
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                return;
            }
        }
        // 2) portal
        if (node.teleport() != null && plugin.puzzles().isOn(g.puzzle, g.node) && teleport(p, g.puzzle, node.teleport())) return;
        // 3) plain interact
        plugin.puzzles().trigger(g, "interact", p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void leftClick(PrePlayerAttackEntityEvent e) {
        Gimmick g = plugin.world().byEntity(e.getAttacked());
        if (g == null) return;
        Player p = e.getPlayer();
        if (plugin.tool().isTool(p.getInventory().getItemInMainHand())) { e.setCancelled(true); plugin.tool().use(p, g, false); return; }
        if (g.attached()) return; // a real mob: let the hit through
        e.setCancelled(true);
        plugin.puzzles().trigger(g, "attack", p);
    }

    /** ItemsAdder id (namespace:id) when it is a custom item, else minecraft:material. */
    String itemId(ItemStack item) {
        if (!iaResolved) {
            iaResolved = true;
            try {
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaByItem = cs.getMethod("byItemStack", ItemStack.class);
                iaId = cs.getMethod("getNamespacedID");
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        if (iaByItem != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Object stack = iaByItem.invoke(null, item);
                if (stack != null) return String.valueOf(iaId.invoke(stack)).toLowerCase(Locale.ROOT);
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return item.getType().getKey().toString();
    }

    /** Teleport to the first gimmick of the target node, in front of it. */
    boolean teleport(Player p, String puzzle, String targetNode) {
        long now = plugin.puzzles().now();
        if (teleportCooldown.getOrDefault(p.getUniqueId(), -1L) > now) return false;
        var targets = plugin.world().inNode(puzzle, targetNode);
        if (targets.isEmpty()) return false;
        Gimmick t = targets.getFirst();
        Location to = t.location();
        if (to == null) return false;
        Vector f = t.forward().multiply(1.5);
        to = to.clone().add(f.getX(), 0.1, f.getZ());
        to.setYaw(t.yaw); to.setPitch(0);
        teleportCooldown.put(p.getUniqueId(), now + plugin.getConfig().getLong("teleport-cooldown-ticks", 60));
        p.teleport(to);
        return true;
    }

    /** Pressure plates ("while: stand" / "turn-on: stand") and walk-in portals. */
    private void standCheck() {
        var seen = new HashMap<String, Player>();
        var anyStandNode = new HashSet<String>();
        for (Gimmick g : plugin.world().all()) {
            if (!g.live() || !g.inPuzzle()) continue;
            var node = plugin.puzzles().node(g.puzzle, g.node);
            if (node == null) continue;
            boolean plate = node.whileTriggers().contains("stand") || node.on().contains("stand") || node.toggle().contains("stand");
            boolean portal = node.teleport() != null && plugin.puzzles().isOn(g.puzzle, g.node);
            if (!plate && !portal) continue;
            String key = g.puzzle + ":" + g.node;
            if (plate) anyStandNode.add(key);
            Location l = g.location();
            if (l == null) continue;
            var info = plugin.catalog().get(g.model);
            BoundingBox box = g.box(info);
            BoundingBox feet = plate ? new BoundingBox(box.getMinX() - 0.2, box.getMinY() - 0.3, box.getMinZ() - 0.2, box.getMaxX() + 0.2, box.getMaxY() + 1.0, box.getMaxZ() + 0.2) : box;
            for (Player p : l.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !feet.contains(p.getLocation().toVector())) continue;
                if (plate) seen.putIfAbsent(key, p);
                if (portal && teleport(p, g.puzzle, node.teleport())) break;
            }
        }
        for (String key : anyStandNode) {
            boolean now = seen.containsKey(key);
            Boolean before = standingNodes.put(key, now);
            if (before != null && before == now) continue;
            String[] pn = key.split(":", 2);
            Player who = seen.get(key);
            plugin.puzzles().standing(pn[0], pn[1], now, who);
            if (now) {
                for (Gimmick g : plugin.world().inNode(pn[0], pn[1])) { plugin.puzzles().trigger(g, "stand", who); break; }
            }
        }
        standingNodes.keySet().retainAll(anyStandNode);
    }
}
