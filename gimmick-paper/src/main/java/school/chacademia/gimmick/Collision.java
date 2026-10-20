package school.chacademia.gimmick;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.util.BoundingBox;

/**
 * 2026-10-20 owner: "모델이 설치된 곳은 배리어로 막아 못 지나가게". Every placed gimmick fills the blocks it takes with
 * barriers (models.yml `collision`):
 *  solid    - footprint x model height (rounded up)       platform - one layer at the anchor (model top = block top, walk on it)
 *  floor    - nothing (thin plates, conduits, vents)       none     - nothing (water wheel over a channel, teleport gate)
 * `open: on|off` lifts the barrier while the puzzle node is in that state (doors, sluice, thawed ice seal; light bridge = off).
 * A vine pot whose node is on (and has `climb`) puts twisting vines in front of it to climb.
 *
 * Only air is ever replaced, and only blocks we placed (recorded in collision.yml) are removed, so the build is never touched.
 * A block a player stands in is skipped and retried every second (no one is shut inside a barrier).
 */
final class Collision implements Listener {
    private final GimmickPlugin plugin;
    private final File file;
    /** owner gimmick id -> block key -> material we placed. */
    private final Map<String, Map<String, Material>> placed = new LinkedHashMap<>();
    private final Set<String> pending = new LinkedHashSet<>();
    private boolean dirty;

    Collision(GimmickPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "collision.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!pending.isEmpty()) for (String id : List.copyOf(pending)) { pending.remove(id); Gimmick g = plugin.world().get(id); if (g != null) refresh(g); }
            if (dirty) save();
        }, 20, 20);
    }

    void load() {
        placed.clear();
        var yaml = AtomicYaml.load(file);
        for (String owner : yaml.getKeys(false)) {
            var sec = yaml.getConfigurationSection(owner);
            if (sec == null) continue;
            var m = new LinkedHashMap<String, Material>();
            for (String k : sec.getKeys(false)) { Material mat = Material.matchMaterial(String.valueOf(sec.getString(k))); if (mat != null) m.put(k.replace('|', ':'), mat); }
            placed.put(owner, m);
        }
        // owners that no longer exist: take their blocks back
        for (String owner : List.copyOf(placed.keySet())) if (plugin.world().get(owner) == null) reconcile(owner, Map.of());
    }

    private void save() {
        var yaml = new YamlConfiguration();
        placed.forEach((owner, m) -> m.forEach((k, mat) -> yaml.set(owner + "." + k.replace(':', '|'), mat.name())));
        try { AtomicYaml.save(yaml, file); dirty = false; }
        catch (IOException e) { plugin.getLogger().warning("collision.yml 저장 실패: " + e.getMessage()); }
    }

    /** Blocks this gimmick should hold right now. */
    Map<String, Material> wanted(Gimmick g) {
        if (g.attached()) return Map.of();
        var info = plugin.catalog().get(g.model);
        var out = new LinkedHashMap<String, Material>();
        boolean on = plugin.world().on(g);
        boolean open = info.open().equals("on") && on || info.open().equals("off") && !on;
        var off = plugin.devices().platformOffset(g);
        int dx = (int) Math.round(off.getX()), dy = (int) Math.round(off.getY()), dz = (int) Math.round(off.getZ());
        if (!open) {
            switch (info.collision()) {
                case "solid" -> { for (int[] c : g.columns(info)) for (int l = 0; l < info.layers(); l++) out.put(key(g.world, c[0], g.by + l, c[1]), Material.BARRIER); }
                case "platform" -> { for (int[] c : g.columns(info, dx, dz)) out.put(key(g.world, c[0], g.by + dy, c[1]), Material.BARRIER); }
                default -> {}
            }
        }
        if (info.is("pot") && on && g.inPuzzle()) {
            var d = plugin.puzzles().node(g.puzzle, g.node);
            int climb = d != null && d.device().climb() > 0 ? d.device().climb() : plugin.getConfig().getInt("vine-pot-climb", 5);
            int[] f = Gimmick.forward(g.facing);
            for (int l = 0; l < climb; l++) out.put(key(g.world, g.bx + f[0], g.by + l, g.bz + f[1]), Material.TWISTING_VINES_PLANT);
            if (climb > 0) out.put(key(g.world, g.bx + f[0], g.by + climb - 1, g.bz + f[1]), Material.TWISTING_VINES);
        }
        return out;
    }

    void refresh(Gimmick g) { reconcile(g.id, wanted(g)); }
    void clear(Gimmick g) { reconcile(g.id, Map.of()); }
    void clearAll() { for (String owner : List.copyOf(placed.keySet())) reconcile(owner, Map.of()); }

    private void reconcile(String owner, Map<String, Material> want) {
        var have = placed.getOrDefault(owner, Map.of());
        var next = new LinkedHashMap<String, Material>();
        // remove what is no longer wanted (only if the block is still what we put there)
        for (var e : have.entrySet()) {
            if (e.getValue() == want.get(e.getKey())) { next.put(e.getKey(), e.getValue()); continue; }
            Block b = block(e.getKey());
            if (b == null) { next.put(e.getKey(), e.getValue()); continue; } // unloaded: keep the record, retry later
            if (b.getType() == e.getValue()) b.setType(Material.AIR, false);
            dirty = true;
        }
        boolean waiting = false;
        for (var e : want.entrySet()) {
            if (next.containsKey(e.getKey())) continue;
            Block b = block(e.getKey());
            if (b == null) { waiting = true; continue; }
            if (!b.getType().isAir() || !plugin.protection().at(b.getLocation())) continue; // never replace the build
            if (occupied(b)) { waiting = true; continue; }
            b.setType(e.getValue(), false);
            next.put(e.getKey(), e.getValue());
            dirty = true;
        }
        if (next.isEmpty()) placed.remove(owner); else placed.put(owner, next);
        if (waiting) pending.add(owner);
    }

    private static boolean occupied(Block b) {
        var box = BoundingBox.of(b);
        for (Player p : b.getWorld().getPlayers()) if (p.getGameMode() != GameMode.SPECTATOR && p.getBoundingBox().overlaps(box)) return true;
        return false;
    }

    static String key(String world, int x, int y, int z) { return world + ":" + x + ":" + y + ":" + z; }
    private static Block block(String key) {
        String[] f = key.split(":");
        if (f.length != 4) return null;
        World w = Bukkit.getWorld(f[0]);
        if (w == null) return null;
        try {
            int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]);
            return w.isChunkLoaded(x >> 4, z >> 4) ? w.getBlockAt(x, y, z) : null;
        } catch (NumberFormatException e) { return null; }
    }

    /** Is this block one of ours (barrier / vine)? Inputs uses it to ignore our own barriers when looking for gimmicks. */
    boolean ours(Block b) {
        String k = key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        for (var m : placed.values()) if (m.containsKey(k)) return true;
        return false;
    }

    @EventHandler public void chunkLoad(ChunkLoadEvent e) {
        String w = e.getWorld().getName();
        for (Gimmick g : plugin.world().all())
            if (!g.attached() && g.world.equals(w) && g.chunkX() == e.getChunk().getX() && g.chunkZ() == e.getChunk().getZ()) pending.add(g.id);
    }
}
