package school.chacademia.gimmick;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Owns every gimmick: saved placements (gimmicks.yml), live entities per loaded chunk, mob attachments,
 * lookups (by entity, by ray, by area) and animation playback.
 * Spawned entities are non-persistent, so the world save never keeps them; gimmicks.yml is the only truth.
 */
final class GimmickWorld implements Listener {
    static final String TAG = "chaca_gimmick";
    private final GimmickPlugin plugin;
    private final File file;
    private final Map<String, Gimmick> gimmicks = new LinkedHashMap<>();
    private final Map<UUID, Gimmick> byEntity = new HashMap<>();
    private final NamespacedKey idKey, modelKey, puzzleKey, nodeKey, scaleKey;
    private int nextId = 1;
    private boolean dirty;

    GimmickWorld(GimmickPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gimmicks.yml");
        idKey = new NamespacedKey(plugin, "gimmick_id");
        modelKey = new NamespacedKey(plugin, "model");
        puzzleKey = new NamespacedKey(plugin, "puzzle");
        nodeKey = new NamespacedKey(plugin, "node");
        scaleKey = new NamespacedKey(plugin, "scale");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 40, 40);
    }

    /** Settings shared by every base entity we create. */
    static void prepareBase(Entity e) {
        e.setPersistent(false);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.setGravity(false);
        e.addScoreboardTag(TAG);
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setCollidable(false);
            le.setRemoveWhenFarAway(false);
        }
    }

    // ------------------------------------------------------------------ store
    void load() {
        gimmicks.clear();
        var yaml = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, yaml.getInt("next-id", 1));
        var sec = yaml.getConfigurationSection("gimmicks");
        if (sec != null) for (String id : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(id);
            if (s != null) gimmicks.put(id, Gimmick.load(id, s.getValues(false)));
        }
    }

    void save() {
        dirty = false;
        var yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        for (Gimmick g : gimmicks.values()) if (!g.attached()) yaml.createSection("gimmicks." + g.id, g.save());
        try { plugin.getDataFolder().mkdirs(); yaml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("gimmicks.yml 저장 실패: " + e.getMessage()); }
    }
    void markDirty() { dirty = true; }

    Collection<Gimmick> all() { return Collections.unmodifiableCollection(gimmicks.values()); }
    Gimmick get(String id) { return gimmicks.get(id); }
    Gimmick byEntity(Entity e) { return e == null ? null : byEntity.get(e.getUniqueId()); }
    List<Gimmick> inNode(String puzzle, String node) {
        var out = new ArrayList<Gimmick>();
        for (Gimmick g : gimmicks.values()) if (g.puzzle.equals(puzzle) && g.node.equals(node)) out.add(g);
        return out;
    }

    // ------------------------------------------------------------------ create / remove
    Gimmick create(String model, Location at, float yaw, String puzzle, String node) {
        var g = new Gimmick("g" + nextId++, model, at, yaw);
        g.puzzle = puzzle == null ? "" : puzzle;
        g.node = node == null ? "" : node;
        gimmicks.put(g.id, g);
        markDirty();
        if (at.getWorld().isChunkLoaded(g.chunkX(), g.chunkZ())) spawn(g, true);
        return g;
    }

    void remove(Gimmick g) {
        despawn(g);
        gimmicks.remove(g.id);
        if (g.attached()) {
            Entity host = Bukkit.getEntity(g.host);
            if (host != null) {
                var pdc = host.getPersistentDataContainer();
                for (var k : List.of(modelKey, puzzleKey, nodeKey, scaleKey)) pdc.remove(k);
            }
        }
        plugin.solids().clear(g);
        markDirty();
    }

    /** Put a model on a mob. The choice is stored on the mob so it comes back after a restart. */
    Gimmick attach(LivingEntity host, String model, double scale) {
        Gimmick old = byEntity(host);
        if (old != null) remove(old);
        var pdc = host.getPersistentDataContainer();
        pdc.set(modelKey, PersistentDataType.STRING, model);
        pdc.set(scaleKey, PersistentDataType.DOUBLE, scale);
        return attachFromData(host);
    }

    private Gimmick attachFromData(LivingEntity host) {
        var pdc = host.getPersistentDataContainer();
        String model = pdc.get(modelKey, PersistentDataType.STRING);
        if (model == null) return null;
        var g = new Gimmick("m" + host.getUniqueId().toString().substring(0, 8), model, host.getUniqueId());
        g.scale = Optional.ofNullable(pdc.get(scaleKey, PersistentDataType.DOUBLE)).orElse(1.0);
        g.puzzle = Optional.ofNullable(pdc.get(puzzleKey, PersistentDataType.STRING)).orElse("");
        g.node = Optional.ofNullable(pdc.get(nodeKey, PersistentDataType.STRING)).orElse("");
        var r = plugin.renderer();
        g.handle = r.hasModel(host) ? null : r.attach(plugin.fullId(model), host, g.scale);
        if (g.handle == null) { plugin.getLogger().warning("모델을 씌우지 못했습니다: " + model + " (렌더러 " + r.name() + ")"); return null; }
        gimmicks.put(g.id, g);
        byEntity.put(host.getUniqueId(), g);
        playState(g, false);
        return g;
    }

    void setPuzzle(Gimmick g, String puzzle, String node) {
        plugin.solids().clear(g);
        g.puzzle = puzzle == null ? "" : puzzle;
        g.node = node == null ? "" : node;
        if (g.attached()) {
            Entity host = Bukkit.getEntity(g.host);
            if (host != null) {
                var pdc = host.getPersistentDataContainer();
                if (g.puzzle.isEmpty()) { pdc.remove(puzzleKey); pdc.remove(nodeKey); }
                else { pdc.set(puzzleKey, PersistentDataType.STRING, g.puzzle); pdc.set(nodeKey, PersistentDataType.STRING, g.node); }
            }
        }
        markDirty();
        if (g.live()) playState(g, false);
    }

    // ------------------------------------------------------------------ live entities
    void spawn(Gimmick g, boolean fresh) {
        if (g.live() || g.attached()) return;
        Location at = g.location();
        if (at == null) return;
        var info = plugin.catalog().get(g.model);
        g.handle = plugin.renderer().spawn(plugin.fullId(g.model), at, g.scale);
        if (g.handle == null) {
            plugin.getLogger().warning("모델 소환 실패: " + g.id + " " + plugin.fullId(g.model) + " (렌더러 " + plugin.renderer().name() + " 에 등록됐는지 확인)");
            return;
        }
        byEntity.put(g.handle.base().getUniqueId(), g);
        g.hitbox = at.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth((float) (info.width() * g.scale));
            i.setInteractionHeight((float) (info.height() * g.scale));
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(TAG);
            i.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, g.id);
        });
        byEntity.put(g.hitbox.getUniqueId(), g);
        playState(g, fresh);
        plugin.solids().refresh(g);
    }

    void despawn(Gimmick g) {
        g.animToken++;
        if (g.handle != null) {
            byEntity.remove(g.handle.base().getUniqueId());
            try { g.handle.remove(); } catch (RuntimeException ignored) {}
            g.handle = null;
        }
        if (g.hitbox != null) { byEntity.remove(g.hitbox.getUniqueId()); g.hitbox.remove(); g.hitbox = null; }
        if (g.attached()) byEntity.remove(g.host);
    }

    /** Move/rotate/scale applied to the live entities and saved. */
    void transform(Gimmick g) {
        if (g.attached()) {
            if (g.handle != null) g.handle.scale(g.scale);
            Entity host = Bukkit.getEntity(g.host);
            if (host != null) host.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, g.scale);
            return;
        }
        markDirty();
        Location at = g.location();
        if (at == null || !g.live()) return;
        g.handle.teleport(at);
        g.handle.scale(g.scale);
        if (g.hitbox != null) {
            var info = plugin.catalog().get(g.model);
            g.hitbox.teleport(at);
            g.hitbox.setInteractionWidth((float) (info.width() * g.scale));
            g.hitbox.setInteractionHeight((float) (info.height() * g.scale));
        }
        plugin.solids().refresh(g);
    }

    void spawnLoaded() {
        for (Gimmick g : gimmicks.values()) {
            World w = Bukkit.getWorld(g.world);
            if (!g.attached() && w != null && w.isChunkLoaded(g.chunkX(), g.chunkZ())) spawn(g, false);
        }
        for (World w : Bukkit.getWorlds()) for (LivingEntity e : w.getLivingEntities()) reattach(e);
    }

    void despawnAll() {
        for (Gimmick g : new ArrayList<>(gimmicks.values())) {
            despawn(g);
            if (g.attached()) gimmicks.remove(g.id);
        }
    }

    private void reattach(Entity e) {
        if (!(e instanceof LivingEntity le) || byEntity.containsKey(e.getUniqueId())) return;
        if (le.getPersistentDataContainer().has(modelKey, PersistentDataType.STRING)) attachFromData(le);
    }

    @EventHandler public void chunkLoad(ChunkLoadEvent e) {
        String w = e.getWorld().getName();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        for (Gimmick g : gimmicks.values())
            if (!g.attached() && !g.live() && g.world.equals(w) && g.chunkX() == cx && g.chunkZ() == cz) spawn(g, false);
    }

    @EventHandler public void chunkUnload(ChunkUnloadEvent e) {
        String w = e.getWorld().getName();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        for (Gimmick g : gimmicks.values())
            if (!g.attached() && g.live() && g.world.equals(w) && g.chunkX() == cx && g.chunkZ() == cz) despawn(g);
    }

    @EventHandler public void entitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            // Leftovers from a crash (should not happen: our entities are non-persistent).
            if (en.getScoreboardTags().contains(TAG) && !byEntity.containsKey(en.getUniqueId())) { en.remove(); continue; }
            reattach(en);
        }
    }

    @EventHandler public void entitiesUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) {
            Gimmick g = byEntity.get(en.getUniqueId());
            if (g != null && g.attached() && en.getUniqueId().equals(g.host)) { despawn(g); gimmicks.remove(g.id); }
        }
    }

    // ------------------------------------------------------------------ lookups
    /** Gimmick the player is looking at (model box), within `max` blocks. */
    Gimmick looking(Player p, double max) {
        Vector start = p.getEyeLocation().toVector(), dir = p.getEyeLocation().getDirection();
        Gimmick best = null; double bestDist = max;
        for (Gimmick g : gimmicks.values()) {
            if (!g.live()) continue;
            Location l = g.location();
            if (l == null || l.getWorld() != p.getWorld() || l.distanceSquared(p.getLocation()) > (max + 8) * (max + 8)) continue;
            BoundingBox box = g.box(plugin.catalog().get(g.model)).expand(0.1);
            RayTraceResult hit = box.rayTrace(start, dir, max);
            if (hit == null) continue;
            double d = hit.getHitPosition().distance(start);
            if (d < bestDist) { bestDist = d; best = g; }
        }
        return best;
    }

    /** Live gimmicks whose box comes within `radius` of a point. */
    List<Gimmick> near(Location at, double radius) {
        var out = new ArrayList<Gimmick>();
        for (Gimmick g : gimmicks.values()) {
            if (!g.live()) continue;
            Location l = g.location();
            if (l == null || l.getWorld() != at.getWorld()) continue;
            BoundingBox box = g.box(plugin.catalog().get(g.model));
            if (distance(box, at.toVector()) <= radius) out.add(g);
        }
        return out;
    }

    static double distance(BoundingBox b, Vector p) {
        double dx = Math.max(0, Math.max(b.getMinX() - p.getX(), p.getX() - b.getMaxX()));
        double dy = Math.max(0, Math.max(b.getMinY() - p.getY(), p.getY() - b.getMaxY()));
        double dz = Math.max(0, Math.max(b.getMinZ() - p.getZ(), p.getZ() - b.getMaxZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ animation
    /** Show the puzzle state: on -> active (or activate held), off -> idle. `fresh` plays spawn first. */
    void playState(Gimmick g, boolean fresh) {
        if (g.handle != null) g.handle.stop();
        boolean on = g.inPuzzle() && plugin.puzzles().isOn(g.puzzle, g.node);
        var info = plugin.catalog().get(g.model);
        String steady = on ? (info.has("active") ? "active" : "activate") : "idle";
        if (fresh && info.has("spawn")) play(g, "spawn", steady);
        else play(g, steady, null);
        if (g.inPuzzle() && !on) for (String a : plugin.puzzles().collectedAnims(g.puzzle, g.node)) overlay(g, a);
    }

    /** Play `anim`, then `then` when it finishes (for hold/once animations). */
    void play(Gimmick g, String anim, String then) {
        if (g.handle == null) return;
        var info = plugin.catalog().get(g.model);
        int token = ++g.animToken;
        if (anim != null && (info.has(anim) || !plugin.catalog().known(g.model))) { g.handle.play(anim); g.lastAnim = anim; }
        else if (then != null) { g.handle.play(then); g.lastAnim = then; return; }
        if (then == null || anim == null || !info.has(anim)) return;
        long ticks = info.anims().get(anim).ticks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (g.animToken == token && g.handle != null && (info.has(then) || !plugin.catalog().known(g.model))) { g.handle.play(then); g.lastAnim = then; }
        }, ticks);
    }

    /** Layered animation (kept while the main state animation changes). */
    void overlay(Gimmick g, String anim) {
        if (g.handle != null && anim != null) { g.handle.overlay(anim); g.lastAnim = anim; }
    }

    /** Node turned on/off. */
    void transition(Gimmick g, boolean on, String animOn, String animOff) {
        var info = plugin.catalog().get(g.model);
        if (on) play(g, animOn != null ? animOn : "activate", info.has("active") ? "active" : null);
        else play(g, animOff != null ? animOff : (info.has("deactivate") ? "deactivate" : "idle"), "idle");
    }
}
