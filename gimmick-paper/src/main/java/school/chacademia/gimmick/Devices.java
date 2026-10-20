package school.chacademia.gimmick;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Device behaviour that does not need the magic plugin:
 *  - device state changes (mirror/dial position, water level, prism colour, facing) with a one-step history for 248 되감기,
 *  - floating platform: travels its node's `move` one way when the node turns on (riders move with it), back on puzzle reset,
 *  - wind vent: while on, throws players standing on it upward (`launch`, default config vent-launch),
 *  - water wheel: water flowing in or under its blocks = continuous input "water",
 *  - teleport gates linked by 250 차원 표식 (walk in = go to the other gate).
 */
final class Devices {
    private final GimmickPlugin plugin;
    private final Map<String, Gimmick.Snapshot> history = new HashMap<>();
    private final Map<String, String> links = new HashMap<>();   // gate id -> other gate id
    private final Map<String, Long> linkUntil = new HashMap<>();  // gate id -> tick
    private long tick;

    Devices(GimmickPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    // ------------------------------------------------------------------ state changes (+ history)
    void remember(Gimmick g) {
        Boolean nodeOn = g.inPuzzle() ? plugin.puzzles().isOn(g.puzzle, g.node) : null;
        history.put(g.id, g.snapshot(nodeOn, tick));
    }
    /** 248 되감기: undo the last change made within `seconds`. */
    boolean rewind(Gimmick g, double seconds) {
        var s = history.remove(g.id);
        if (s == null || tick - s.at() > seconds * 20) return false;
        boolean turned = s.facing() != g.facing;
        g.facing = s.facing(); g.pos = s.pos(); g.level = s.level(); g.color = s.color();
        if (turned) plugin.world().transform(g);
        plugin.world().markDirty();
        if (g.live()) plugin.world().playState(g, false);
        if (s.nodeOn() != null && g.inPuzzle() && plugin.puzzles().isOn(g.puzzle, g.node) != s.nodeOn())
            plugin.puzzles().force(g.puzzle, g.node, s.nodeOn(), null);
        changed(g);
        return true;
    }
    void turn(Gimmick g, int step) { remember(g); g.facing = Math.floorMod(g.facing + step, 4); plugin.world().transform(g); changed(g); }
    void position(Gimmick g, int step) { remember(g); g.pos = Math.floorMod(g.pos + step, 4); plugin.world().markDirty(); plugin.world().deviceLayers(g); changed(g); }
    boolean level(Gimmick g, int step) {
        int next = Math.clamp(g.level + step, 0, 3);
        if (next == g.level) return false;
        remember(g); g.level = next; plugin.world().markDirty(); plugin.world().deviceLayers(g); changed(g);
        return true;
    }
    void color(Gimmick g, String color) { remember(g); g.color = Colors.norm(color); plugin.world().markDirty(); plugin.world().deviceLayers(g); changed(g); }
    private void changed(Gimmick g) { plugin.puzzles().deviceChanged(g); plugin.mana().dirty(); }

    void forget(Gimmick g) { history.remove(g.id); String o = links.remove(g.id); if (o != null) { links.remove(o); linkUntil.remove(o); } linkUntil.remove(g.id); }

    // ------------------------------------------------------------------ platform
    private PuzzleMove move(Gimmick g) {
        if (!g.inPuzzle()) return null;
        var d = plugin.puzzles().node(g.puzzle, g.node);
        if (d == null || d.device().move() == null) return null;
        var m = d.device().move();
        return new PuzzleMove(new Vector(m[0], m[1], m[2]), d.device().speed());
    }
    private record PuzzleMove(Vector delta, double speed) {}
    /** Current offset of a floating platform from its anchor (zero for everything else). */
    Vector platformOffset(Gimmick g) {
        if (g.travel <= 0) return new Vector();
        var m = move(g);
        return m == null ? new Vector() : m.delta().clone().multiply(g.travel);
    }
    void nodeChanged(Gimmick g, boolean on) {
        // nothing to do immediately: tick() moves platforms toward their target and runs vents while on
    }
    void reset(Gimmick g) {
        if (g.travel != 0) { g.travel = 0; plugin.world().transform(g); }
        history.remove(g.id);
    }

    // ------------------------------------------------------------------ gates (250 차원 표식)
    void link(Gimmick a, Gimmick b, int seconds) {
        for (Gimmick g : List.of(a, b)) { String o = links.remove(g.id); if (o != null) { links.remove(o); linkUntil.remove(o); show(o, false); } }
        links.put(a.id, b.id); links.put(b.id, a.id);
        long until = tick + seconds * 20L;
        linkUntil.put(a.id, until); linkUntil.put(b.id, until);
        show(a.id, true); show(b.id, true);
    }
    Gimmick linked(Gimmick g) { String o = links.get(g.id); return o == null ? null : plugin.world().get(o); }
    private void show(String id, boolean on) { Gimmick g = plugin.world().get(id); if (g != null && !g.inPuzzle()) plugin.world().setFree(g, on); else if (g != null && g.live() && on) plugin.world().transition(g, true, null, null); else if (g != null && g.live()) plugin.world().playState(g, false); }

    // ------------------------------------------------------------------ tick
    private void tick() {
        tick++;
        // gate links expire
        if (!linkUntil.isEmpty()) for (var e : List.copyOf(linkUntil.entrySet())) if (e.getValue() < tick) {
            linkUntil.remove(e.getKey()); links.remove(e.getKey()); show(e.getKey(), false);
        }
        for (Gimmick g : plugin.world().all()) {
            if (g.attached() || !g.live()) continue;
            var info = plugin.catalog().get(g.model);
            switch (info.kind()) {
                case "platform" -> platform(g, info);
                case "vent" -> { if (tick % 4 == 0 && plugin.world().on(g)) vent(g, info); }
                case "wheel" -> { if (tick % 10 == 0) wheel(g, info); }
                default -> {}
            }
        }
    }

    private void platform(Gimmick g, ModelCatalog.Info info) {
        var m = move(g);
        if (m == null) return;
        boolean on = plugin.puzzles().isOn(g.puzzle, g.node);
        if (!on || g.travel >= 1) return; // one way: once it started it finishes, and stays (puzzle reset brings it back)
        double len = Math.max(0.1, m.delta().length());
        double step = Math.min(1 - g.travel, m.speed() / 20 / len);
        Vector before = platformOffset(g);
        BoundingBox top = g.box(info).expand(0, 0, 0, 0, 1.2, 0);
        g.travel += step;
        Vector after = platformOffset(g);
        Vector delta = after.clone().subtract(before);
        Location at = g.location();
        if (at != null) {
            g.x += delta.getX(); g.y += delta.getY(); g.z += delta.getZ();
            if (g.handle != null) g.handle.teleport(g.location());
            if (g.hitbox != null) g.hitbox.teleport(g.location());
            for (Player p : at.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !top.contains(p.getLocation().toVector())) continue;
                p.teleport(p.getLocation().add(delta));
                p.setFallDistance(0);
            }
        }
        if ((int) Math.round(before.getX()) != (int) Math.round(after.getX()) || (int) Math.round(before.getY()) != (int) Math.round(after.getY())
            || (int) Math.round(before.getZ()) != (int) Math.round(after.getZ()) || g.travel >= 1) {
            plugin.world().cellsChanged();
            plugin.collision().refresh(g);
        }
        plugin.world().markDirty();
    }

    private void vent(Gimmick g, ModelCatalog.Info info) {
        double launch = plugin.getConfig().getDouble("vent-launch", 1.3);
        if (g.inPuzzle()) { var d = plugin.puzzles().node(g.puzzle, g.node); if (d != null && d.device().launch() > 0) launch = d.device().launch(); }
        var box = g.box(info).expand(0, 0, 0, 0, 2.5, 0);
        var at = g.location();
        if (at == null) return;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || !box.contains(p.getLocation().toVector())) continue;
            var v = p.getVelocity();
            if (v.getY() < launch * 0.8) p.setVelocity(v.setY(launch));
            p.setFallDistance(0);
        }
    }

    private void wheel(Gimmick g, ModelCatalog.Info info) {
        World w = Bukkit.getWorld(g.world);
        if (w == null) return;
        boolean water = false;
        for (int[] c : g.columns(info)) for (int dy = -1; dy <= 0 && !water; dy++) {
            if (!w.isChunkLoaded(c[0] >> 4, c[1] >> 4)) continue;
            Block b = w.getBlockAt(c[0], g.by + dy, c[1]);
            water = b.getType() == Material.WATER || b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
        }
        if (g.inPuzzle()) plugin.puzzles().held(g.puzzle, g.node, "water", water, null);
        else plugin.world().setFree(g, water);
    }
}
