package school.chacademia.gimmick;

import java.util.*;
import org.bukkit.Bukkit;

/**
 * Mana signal (2026-10-20): a puzzle node with `mana: out` that is on (wind turbine spun by wind, steam valve under pressure,
 * pressure plate pressed by 무게추, lever pulled, light receiver lit ...) powers what touches it. Conduits pass it on through
 * their ports (models.yml `ports`, turned with the gimmick / 249 도관 회전); any other gimmick reached that way is powered.
 * Powered puzzle nodes receive the continuous input "mana" (`while: [mana]` follows it, `turn-on: [mana]` fires once when it
 * starts - e.g. a timed door with `hold`). Conduits outside puzzles light up while powered.
 *
 * Blocks: a gimmick's footprint columns from its anchor through its height. Sources push into all 6 neighbours.
 */
final class ManaGrid {
    private final GimmickPlugin plugin;
    private boolean dirty = true;
    private Set<String> powered = Set.of();

    ManaGrid(GimmickPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) { dirty = false; recompute(); } }, 2, 2);
    }

    void dirty() { dirty = true; }
    boolean powered(Gimmick g) { return powered.contains(g.id); }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** World direction of a conduit port. */
    static int[] port(String name, int facing) {
        int[] f = Gimmick.forward(facing), r = Gimmick.right(facing);
        return switch (name) {
            case "front" -> new int[]{f[0], 0, f[1]}; case "back" -> new int[]{-f[0], 0, -f[1]};
            case "right" -> new int[]{r[0], 0, r[1]}; case "left" -> new int[]{-r[0], 0, -r[1]};
            case "up" -> new int[]{0, 1, 0}; case "down" -> new int[]{0, -1, 0};
            default -> null;
        };
    }

    private void recompute() {
        var world = plugin.world();
        var next = new HashSet<String>();
        var visited = new HashSet<String>(); // conduit id + entry dir
        var queue = new ArrayDeque<Object[]>(); // {worldName, x, y, z, fromDx, fromDy, fromDz}
        var sources = new HashSet<String>();
        for (Gimmick g : world.all()) {
            if (g.attached() || !g.inPuzzle()) continue;
            var d = plugin.puzzles().node(g.puzzle, g.node);
            if (d == null || !d.device().manaOut() || !plugin.puzzles().isOn(g.puzzle, g.node)) continue;
            sources.add(g.id);
            var info = plugin.catalog().get(g.model);
            for (int[] c : g.columns(info)) for (int l = 0; l < info.layers(); l++)
                for (int[] dir : DIRS) queue.add(new Object[]{g.world, c[0] + dir[0], g.by + l + dir[1], c[1] + dir[2], -dir[0], -dir[1], -dir[2]});
        }
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 20000) {
            Object[] q = queue.poll();
            String w = (String) q[0]; int x = (int) q[1], y = (int) q[2], z = (int) q[3];
            Gimmick g = world.at(w, x, y, z);
            if (g == null || sources.contains(g.id)) continue;
            var info = plugin.catalog().get(g.model);
            if (!info.is("conduit")) { next.add(g.id); continue; }
            int fx = (int) q[4], fy = (int) q[5], fz = (int) q[6]; // direction from this conduit toward the sender
            boolean accepts = false;
            for (String p : info.ports()) { int[] d = port(p, g.facing); if (d != null && d[0] == fx && d[1] == fy && d[2] == fz) { accepts = true; break; } }
            if (!accepts || !visited.add(g.id + ":" + fx + ":" + fy + ":" + fz)) continue;
            next.add(g.id);
            for (String p : info.ports()) {
                int[] d = port(p, g.facing);
                if (d == null || d[0] == fx && d[1] == fy && d[2] == fz) continue;
                queue.add(new Object[]{w, x + d[0], y + d[1], z + d[2], -d[0], -d[1], -d[2]});
            }
        }
        var before = powered;
        powered = Set.copyOf(next);
        // puzzle nodes: powered while any of their gimmicks is
        var nodes = new HashMap<String, Boolean>();
        for (Gimmick g : world.all()) {
            if (!g.inPuzzle()) {
                if (plugin.catalog().get(g.model).is("conduit") && before.contains(g.id) != powered.contains(g.id)) world.setFree(g, powered.contains(g.id));
                continue;
            }
            nodes.merge(g.puzzle + "\u0000" + g.node, powered.contains(g.id), Boolean::logicalOr);
        }
        for (var e : nodes.entrySet()) {
            String[] pn = e.getKey().split("\u0000", 2);
            plugin.puzzles().held(pn[0], pn[1], "mana", e.getValue(), null);
        }
    }
}
