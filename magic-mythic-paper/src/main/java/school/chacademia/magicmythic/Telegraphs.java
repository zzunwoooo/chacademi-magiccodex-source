package school.chacademia.magicmythic;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Show;

/**
 * Ground warnings for enemy area attacks ({@link TelegraphProtocol}). A warning is sent to every listening player
 * nearby, and after its ticks the callback runs with the same {@link Show} so the hit test matches what was drawn.
 * The warning is cancelled (CLEAR, no hit) when the caster dies or leaves the world first.
 */
final class Telegraphs implements AutoCloseable {
    private final MagicMythicPlugin plugin;
    private final Map<Integer, Live> live = new HashMap<>();
    private int nextId = 1;
    private double range = 64;
    private int limit = 128;

    private record Live(Show show, LivingEntity caster, String world, BukkitTask task) {}

    Telegraphs(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, TelegraphProtocol.CHANNEL);
        reload();
    }

    void reload() {
        range = Math.clamp(plugin.getConfig().getDouble("telegraph.range", 64), 16, 160);
        limit = Math.clamp(plugin.getConfig().getInt("telegraph.limit", 128), 8, 1024);
    }

    /** Next id for a warning (wraps, never 0). */
    int id() { int id = nextId++; if (nextId == Integer.MAX_VALUE) nextId = 1; return id; }

    /**
     * Shows the warning and runs `then` after its ticks (main thread). cancelOnDeath: the caster's death removes it.
     * @return false when too many are live (the skill is skipped)
     */
    boolean start(Show show, LivingEntity caster, World world, boolean cancelOnDeath, Consumer<Show> then) {
        if (live.size() >= limit) return false;
        send(world, show.x(), show.z(), TelegraphProtocol.encode(show));
        int id = show.id();
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int left = show.ticks();
            @Override public void run() {
                if (cancelOnDeath && caster != null && (!caster.isValid() || caster.isDead() || !caster.getWorld().equals(world))) { clear(id); return; }
                if (--left > 0) return;
                live.remove(id);
                cancel();
                try { then.accept(show); } catch (RuntimeException e) { plugin.getLogger().warning("예고 장판 판정 오류: " + e); }
            }
        }.runTaskTimer(plugin, 1, 1);
        live.put(id, new Live(show, caster, world.getName(), task));
        return true;
    }

    /** Removes a live warning without hitting. */
    void clear(int id) {
        Live l = live.remove(id);
        if (l == null) return;
        l.task().cancel();
        World w = Bukkit.getWorld(l.world());
        if (w != null) send(w, l.show().x(), l.show().z(), TelegraphProtocol.encode(new TelegraphProtocol.Clear(id)));
    }

    int count() { return live.size(); }
    boolean live(int id) { return live.containsKey(id); }

    private void send(World world, double x, double z, byte[] bytes) {
        double r2 = range * range;
        for (Player p : world.getPlayers()) {
            var l = p.getLocation();
            if ((l.getX() - x) * (l.getX() - x) + (l.getZ() - z) * (l.getZ() - z) > r2) continue;
            if (!p.getListeningPluginChannels().contains(TelegraphProtocol.CHANNEL)) continue;
            p.sendPluginMessage(plugin, TelegraphProtocol.CHANNEL, bytes);
        }
    }

    /**
     * Top of the ground under `at`: the first block with a collision box at or below at.y + 0.5 (down 8 blocks).
     * A mob standing on a slab gets the slab's top. Falls back to at.y over a deep drop.
     */
    static double ground(Location at) {
        World w = at.getWorld();
        if (w == null) return at.getY();
        int x = at.getBlockX(), z = at.getBlockZ(), y0 = (int) Math.floor(at.getY() + 0.5);
        for (int y = y0; y >= Math.max(w.getMinHeight(), y0 - 8); y--) {
            Block b = w.getBlockAt(x, y, z);
            if (b.isPassable() || b.isLiquid()) continue;
            var box = b.getBoundingBox();
            double top = box.getVolume() > 0 ? box.getMaxY() : y + 1;
            if (top <= at.getY() + 0.5) return top;
        }
        return at.getY();
    }

    @Override public void close() {
        for (var id : List.copyOf(live.keySet())) clear(id);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, TelegraphProtocol.CHANNEL);
    }
}
