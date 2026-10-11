package school.chacademia.gimmick;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import school.chacademia.gimmick.puzzle.PuzzleDef;

/**
 * Invisible barrier boxes for doors, gates, pillars and bridges (models have no collision).
 * Placed only into air, removed only if still a barrier, and journaled to solids.yml (kept across restarts,
 * reconciled when the gimmick spawns) so walls are never orphaned.
 */
final class Solids {
    private final GimmickPlugin plugin;
    private final File file;
    private final Map<String, Set<String>> placed = new HashMap<>(); // gimmick id -> "world:x:y:z"

    Solids(GimmickPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "solids.yml");
    }

    /** Read the journal. Blocks are reconciled when each gimmick spawns (refresh); orphans are cleared here. */
    void load(Set<String> knownIds) {
        placed.clear();
        if (!file.exists()) return;
        var yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            var list = new LinkedHashSet<>(yaml.getStringList(key));
            if (knownIds.contains(key)) placed.put(key, list);
            else list.forEach(this::clearBlock); // gimmick no longer exists
        }
        save();
    }

    /** Make the barrier match the gimmick's node state. */
    void refresh(Gimmick g) {
        PuzzleDef.Solid solid = g.inPuzzle() ? plugin.puzzles().solid(g.puzzle, g.node) : null;
        if (!g.live()) return; // unloaded: reconciled on next spawn
        if (solid == null) { clear(g); return; }
        boolean on = plugin.puzzles().isOn(g.puzzle, g.node);
        Set<String> want = on == solid.whenOn() ? blocks(g, solid) : Set.of();
        Set<String> have = placed.getOrDefault(g.id, Set.of());
        var keep = new HashSet<String>();
        for (String b : have) if (want.contains(b)) keep.add(b); else clearBlock(b);
        for (String b : want) {
            if (keep.contains(b)) continue;
            Block block = block(b);
            if (block != null && block.getType().isAir()) { block.setType(Material.BARRIER, false); keep.add(b); }
        }
        if (keep.isEmpty()) placed.remove(g.id); else placed.put(g.id, keep);
        save();
    }

    void clear(Gimmick g) {
        Set<String> have = placed.remove(g.id);
        if (have == null) return;
        have.forEach(this::clearBlock);
        save();
    }

    void clearAll() {
        for (Set<String> s : placed.values()) s.forEach(this::clearBlock);
        placed.clear();
        save();
    }

    /** Box centered on the gimmick's block, turned to the nearest 90 degrees of its yaw. */
    private Set<String> blocks(Gimmick g, PuzzleDef.Solid s) {
        Location at = g.location();
        if (at == null) return Set.of();
        Vector f = g.forward();
        int fx, fz;
        if (Math.abs(f.getX()) > Math.abs(f.getZ())) { fx = (int) Math.signum(f.getX()); fz = 0; } else { fx = 0; fz = (int) Math.signum(f.getZ()); }
        int rx = -fz, rz = fx; // right
        int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
        var out = new LinkedHashSet<String>();
        int w0 = -(s.width() - 1) / 2, d0 = -(s.depth() - 1) / 2;
        for (int w = 0; w < s.width(); w++) for (int d = 0; d < s.depth(); d++) for (int h = 0; h < s.height(); h++) {
            int ow = w0 + w, od = d0 + d + s.forward();
            out.add(at.getWorld().getName() + ":" + (bx + rx * ow + fx * od) + ":" + (by + s.up() + h) + ":" + (bz + rz * ow + fz * od));
        }
        return out;
    }

    private Block block(String key) {
        String[] f = key.split(":");
        World w = Bukkit.getWorld(f[0]);
        if (w == null) return null;
        int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }

    private void clearBlock(String key) {
        Block b = block(key);
        if (b != null && b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
    }

    private void save() {
        var yaml = new YamlConfiguration();
        for (var e : placed.entrySet()) yaml.set(e.getKey(), new ArrayList<>(e.getValue()));
        try { plugin.getDataFolder().mkdirs(); yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("solids.yml 저장 실패: " + ex.getMessage()); }
    }
}
