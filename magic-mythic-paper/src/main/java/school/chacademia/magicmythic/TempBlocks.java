package school.chacademia.magicmythic;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Temporary blocks placed by spells (air_step platforms). Each one is journaled to temp-blocks.yml so a crash or
 * restart puts the original block back on the next start instead of leaving barriers in the world.
 */
final class TempBlocks {
    private record Entry(String world, int x, int y, int z, Material placed, String original) {
        String key() { return world + ":" + x + ":" + y + ":" + z; }
    }
    private final MagicMythicPlugin plugin;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    TempBlocks(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "temp-blocks.yml");
    }
    int count() { return entries.size(); }

    /** Place only into air; returns false when the spot is not free. */
    boolean place(Block b, Material material) {
        if (!b.getType().isAir()) return false;
        var e = new Entry(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), material, b.getBlockData().getAsString());
        b.setType(material, false);
        entries.put(e.key(), e);
        save();
        return true;
    }

    void remove(Block b) {
        var e = entries.remove(b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ());
        if (e != null) { restore(e); save(); }
    }

    void removeAll() {
        for (Entry e : entries.values()) restore(e);
        entries.clear();
        save();
    }

    void restoreLeftovers() {
        if (!file.exists()) return;
        var yaml = YamlConfiguration.loadConfiguration(file);
        for (String s : yaml.getStringList("blocks")) {
            String[] f = s.split("\\|", 6);
            if (f.length < 6) continue;
            try {
                restore(new Entry(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Material.valueOf(f[4]), f[5]));
            } catch (IllegalArgumentException ignored) {}
        }
        entries.clear();
        save();
    }

    private void restore(Entry e) {
        World w = Bukkit.getWorld(e.world());
        if (w == null) return;
        Block b = w.getBlockAt(e.x(), e.y(), e.z());
        if (b.getType() != e.placed()) return; // someone already changed it; leave it alone
        try { b.setBlockData(Bukkit.createBlockData(e.original()), false); }
        catch (IllegalArgumentException ex) { b.setType(Material.AIR, false); }
    }

    private void save() {
        var yaml = new YamlConfiguration();
        var list = new ArrayList<String>();
        for (Entry e : entries.values()) list.add(String.join("|", e.world(), "" + e.x(), "" + e.y(), "" + e.z(), e.placed().name(), e.original()));
        yaml.set("blocks", list);
        try { plugin.getDataFolder().mkdirs(); yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("temp-blocks.yml 저장 실패: " + ex.getMessage()); }
    }
}
