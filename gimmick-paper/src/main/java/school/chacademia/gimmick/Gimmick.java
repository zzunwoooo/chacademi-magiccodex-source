package school.chacademia.gimmick;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** One placed (or mob-attached) model. Placed ones are saved in gimmicks.yml; the entities are rebuilt on chunk load. */
final class Gimmick {
    final String id;
    String model;
    String world;
    double x, y, z;
    float yaw;
    double scale = 1;
    String puzzle = "";
    String node = "";
    /** Mob-attached: no saved position, follows the host. */
    final UUID host;

    // live (not saved)
    Renderer.Handle handle;
    Interaction hitbox;
    int animToken;
    String lastAnim = "";

    Gimmick(String id, String model, Location at, float yaw) {
        this.id = id; this.model = model; this.host = null; setLocation(at); this.yaw = yaw;
    }
    private Gimmick(String id, String model, String world, double x, double y, double z, float yaw) {
        this.id = id; this.model = model; this.host = null; this.world = world; this.x = x; this.y = y; this.z = z; this.yaw = yaw;
    }
    Gimmick(String id, String model, UUID host) {
        this.id = id; this.model = model; this.host = host; this.world = "";
    }

    boolean attached() { return host != null; }
    boolean live() { return handle != null; }
    boolean inPuzzle() { return !puzzle.isEmpty() && !node.isEmpty(); }

    void setLocation(Location at) {
        world = at.getWorld().getName(); x = at.getX(); y = at.getY(); z = at.getZ();
    }

    /** Current position: saved spot, or the host mob. */
    Location location() {
        if (attached()) {
            Entity e = Bukkit.getEntity(host);
            return e == null ? null : e.getLocation();
        }
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, 0);
    }

    int chunkX() { return (int) Math.floor(x) >> 4; }
    int chunkZ() { return (int) Math.floor(z) >> 4; }

    /** Horizontal facing as a unit vector (yaw 0 = south, Minecraft convention). */
    Vector forward() {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    BoundingBox box(ModelCatalog.Info info) {
        Location l = location();
        if (l == null) return new BoundingBox();
        double hw = info.width() * scale / 2, h = info.height() * scale;
        return new BoundingBox(l.getX() - hw, l.getY(), l.getZ() - hw, l.getX() + hw, l.getY() + h, l.getZ() + hw);
    }

    Map<String, Object> save() {
        var m = new LinkedHashMap<String, Object>();
        m.put("model", model); m.put("world", world);
        m.put("x", round(x)); m.put("y", round(y)); m.put("z", round(z));
        m.put("yaw", (double) Math.round(yaw * 100) / 100.0); m.put("scale", round(scale));
        if (!puzzle.isEmpty()) m.put("puzzle", puzzle);
        if (!node.isEmpty()) m.put("node", node);
        return m;
    }
    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }

    static Gimmick load(String id, Map<?, ?> m) {
        // world kept by name even if that world is not loaded right now
        var g = new Gimmick(id, String.valueOf(m.get("model")), String.valueOf(m.get("world")), num(m, "x"), num(m, "y"), num(m, "z"), (float) num(m, "yaw"));
        g.scale = m.containsKey("scale") ? num(m, "scale") : 1;
        g.puzzle = m.get("puzzle") == null ? "" : String.valueOf(m.get("puzzle"));
        g.node = m.get("node") == null ? "" : String.valueOf(m.get("node"));
        return g;
    }
    private static double num(Map<?, ?> m, String k) {
        Object o = m.get(k);
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? 0 : Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return 0; }
    }
}
