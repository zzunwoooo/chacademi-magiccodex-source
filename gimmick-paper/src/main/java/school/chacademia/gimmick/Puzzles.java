package school.chacademia.gimmick;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import school.chacademia.gimmick.puzzle.PuzzleDef;
import school.chacademia.gimmick.puzzle.PuzzleEngine;

/** Loads puzzles.yml (+ puzzles/*.yml), runs one engine per puzzle, turns state changes into model/world effects. */
final class Puzzles {
    private final GimmickPlugin plugin;
    private final File stateFile;
    private final Map<String, PuzzleEngine> engines = new LinkedHashMap<>();
    private long tick;
    private boolean dirty;
    private final List<Runnable> pendingEffects=new ArrayList<>();

    Puzzles(GimmickPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "puzzle-state.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (var e : engines.values()) e.tick(tick);
            if (dirty) saveState();
        }, 1, 1);
    }

    long now() { return tick; }

    /** @return warnings */
    @SuppressWarnings("unchecked")
    List<String> load() {
        if (!engines.isEmpty()) saveState();
        engines.clear();
        var warnings = new ArrayList<String>();
        var files = new ArrayList<File>();
        files.add(new File(plugin.getDataFolder(), "puzzles.yml"));
        File dir = new File(plugin.getDataFolder(), "puzzles");
        File[] extra = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (extra != null) { Arrays.sort(extra); files.addAll(List.of(extra)); }
        for (File f : files) {
            if (!f.exists()) continue;
            var yaml = AtomicYaml.load(f);
            var root = yaml.getConfigurationSection("puzzles");
            if (root == null) continue;
            for (String id : root.getKeys(false)) {
                var sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                if (engines.containsKey(id)) { warnings.add(f.getName() + ": 퍼즐 이름 중복 " + id); continue; }
                Map<String, Object> raw = toPlain(sec.getValues(true), sec);
                var def = PuzzleDef.parse(id, raw, warnings);
                engines.put(id, new PuzzleEngine(def, new Effects(id)));
            }
        }
        var state = AtomicYaml.load(stateFile);
        for (var e : engines.entrySet()) {
            var sec = state.getConfigurationSection(e.getKey());
            if (sec != null && e.getValue().def().persist()) e.getValue().load(toPlain(sec.getValues(true), sec));
        }
        return warnings;
    }

    /** ConfigurationSection -> nested plain maps (the engine parser is Bukkit-free). */
    private static Map<String, Object> toPlain(Map<String, Object> ignored, org.bukkit.configuration.ConfigurationSection sec) {
        var out = new LinkedHashMap<String, Object>();
        for (String k : sec.getKeys(false)) {
            Object v = sec.get(k);
            out.put(k, v instanceof org.bukkit.configuration.ConfigurationSection c ? toPlain(null, c) : v);
        }
        return out;
    }

    boolean saveState(){return saveState(true);}
    boolean saveState(boolean executeEffects) {
        var yaml = new YamlConfiguration();
        for (var e : engines.entrySet()) if (e.getValue().def().persist()) {
            var s = e.getValue().save();
            if (!s.isEmpty()) yaml.createSection(e.getKey(), s);
        }
        try { plugin.getDataFolder().mkdirs(); AtomicYaml.save(yaml,stateFile);dirty=false;var effects=List.copyOf(pendingEffects);pendingEffects.clear();if(executeEffects)for(var effect:effects)try{effect.run();}catch(RuntimeException failure){plugin.getLogger().warning("Committed puzzle output failed: "+failure.getClass().getSimpleName());}return true; }
        catch (IOException ex) { plugin.getLogger().warning("puzzle-state.yml 저장 실패: " + ex.getMessage());return false; }
    }

    Set<String> ids() { return engines.keySet(); }
    PuzzleEngine engine(String id) { return engines.get(id); }
    Set<String> nodes(String id) { var e = engines.get(id); return e == null ? Set.of() : e.def().nodes().keySet(); }
    boolean isOn(String puzzle, String node) { var e = engines.get(puzzle); return e != null && e.on(node); }
    PuzzleDef.NodeDef node(String puzzle, String node) { var e = engines.get(puzzle); return e == null ? null : e.def().nodes().get(node); }
    /** Animations of items already inserted (replayed as layers when the model respawns). */
    List<String> collectedAnims(String puzzle, String node) {
        var e = engines.get(puzzle); var d = node(puzzle, node);
        if (e == null || d == null) return List.of();
        var out = new ArrayList<String>();
        for (String item : e.collected(node)) { String a = d.collect().get(item); if (a != null && !a.isBlank()) out.add(a); }
        return out;
    }
    PuzzleDef.Solid solid(String puzzle, String node) { var n = node(puzzle, node); return n == null ? null : n.solid(); }

    // ------------------------------------------------------------------ inputs
    boolean trigger(Gimmick g, String trigger, Player who) {
        if(!allowed(g.puzzle,who))return false;
        var e = g.inPuzzle() ? engines.get(g.puzzle) : null;
        return e != null && e.trigger(g.node, trigger, who == null ? null : who.getName(), tick);
    }
    boolean collect(Gimmick g, String item, Player who) {
        if(!allowed(g.puzzle,who))return false;
        var e = g.inPuzzle() ? engines.get(g.puzzle) : null;
        if(e==null)return false;
        var before=e.save();int effectsBefore=pendingEffects.size();
        boolean accepted=e.collect(g.node,item,who==null?null:who.getName(),tick);
        if(!accepted)return false;
        if(saveState())return true;
        // No item is consumed on failure, and no output runs before durable state exists.
        var restored=new PuzzleEngine(e.def(),new Effects(g.puzzle));restored.load(before);engines.put(g.puzzle,restored);
        while(pendingEffects.size()>effectsBefore)pendingEffects.removeLast();return false;
    }
    void standing(String puzzle, String node, boolean standing, Player who) { held(puzzle, node, "stand", standing, who); }

    /** puzzle:node:key -> last value, for rising-edge triggers of continuous inputs. */
    private final Map<String, Boolean> heldInputs = new HashMap<>();
    /**
     * Continuous input (stand, mana, light, water): `while: [key]` nodes follow it; `turn-on` / `toggle` nodes get one trigger
     * when it starts. Key "state" = device condition (level / position / facing) matched.
     */
    void held(String puzzle, String node, String key, boolean on, Player who) {
        if (on && !allowed(puzzle, who)) return;
        var e = engines.get(puzzle);
        if (e == null) return;
        Boolean before = heldInputs.put(puzzle + ":" + node + ":" + key, on);
        String name = who == null ? null : who.getName();
        boolean wasOn = e.on(node);
        e.held(node, key, on, name, tick);
        // rising edge; an input already present when the server starts (first 5 s) is a state, not an event (no timed door opening at boot)
        boolean first = before == null;
        if (on && !Boolean.TRUE.equals(before) && !key.equals("state") && !(first && (tick < 100 || wasOn))) e.trigger(node, key, name, tick);
    }
    /** Re-check a device condition (water level, mirror/dial position, statue facing) after the device changed. */
    void deviceChanged(Gimmick g) {
        if (!g.inPuzzle()) return;
        var d = node(g.puzzle, g.node);
        if (d == null || !d.device().condition()) return;
        var dev = d.device();
        boolean match = (dev.level() == null || dev.level() == g.level) && (dev.position() == null || dev.position() == g.pos)
            && (dev.facing() == null || dev.facing() == g.facing);
        held(g.puzzle, g.node, "state", match, null);
    }
    boolean force(String puzzle, String node, Boolean on, String who) {
        if(!allowed(puzzle,who==null?null:Bukkit.getPlayerExact(who)))return false;
        var e = engines.get(puzzle);
        if (e == null || !e.def().nodes().containsKey(node)) return false;
        return e.force(node, on == null ? !e.on(node) : on, who, tick);
    }
    boolean reset(String puzzle) {
        if(!allowed(puzzle,null))return false;
        var e = engines.get(puzzle);
        if (e == null) return false;
        e.reset();
        heldInputs.keySet().removeIf(k -> k.startsWith(puzzle + ":"));
        for (Gimmick g : plugin.world().all()) if (g.puzzle.equals(puzzle)) plugin.devices().reset(g);
        for (Gimmick g : plugin.world().all()) if (g.puzzle.equals(puzzle)) deviceChanged(g); // devices keep their pose: re-check level/position/facing
        plugin.mana().dirty();
        dirty = true;
        return true;
    }

    private boolean allowed(String puzzle,Player who){
        if(who!=null&&!plugin.protection().pair(who,who.getLocation()))return false;
        for(Gimmick g:plugin.world().all())if(g.puzzle.equals(puzzle)&&!plugin.protection().at(g.location()))return false;
        return true;
    }
    // ------------------------------------------------------------------ outputs
    private final class Effects implements PuzzleEngine.Listener {
        final String puzzle;
        Effects(String puzzle) { this.puzzle = puzzle; }

        public void changed(String node, boolean on, String cause, String who) {
            dirty=true;pendingEffects.add(()->applyChanged(node,on,cause,who));
        }
        private void applyChanged(String node, boolean on, String cause, String who) {
            if(!allowed(puzzle,who==null?null:Bukkit.getPlayerExact(who)))return;
            var def = node(puzzle, node);
            if (def == null) return;
            for (Gimmick g : plugin.world().inNode(puzzle, node)) {
                if (g.live()) plugin.world().transition(g, on, def.animOn(), def.animOff());
                plugin.solids().refresh(g);
                plugin.collision().refresh(g);
                plugin.devices().nodeChanged(g, on);
            }
            if (def.device().manaOut() || plugin.world().inNode(puzzle, node).stream().anyMatch(g -> plugin.catalog().get(g.model).is("conduit"))) plugin.mana().dirty();
            for (String cmd : on ? def.commandsOn() : def.commandsOff()) {
                if (cmd.contains("{player}") && who == null) continue;
                String line = cmd.replace("{player}", who == null ? "" : who).replace("{puzzle}", puzzle).replace("{node}", node);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
            }
            if (on && def.message() != null && !def.message().isBlank()) {
                for (Gimmick g : plugin.world().inNode(puzzle, node)) {
                    Location l = g.location();
                    if (l == null) continue;
                    for (Player p : l.getWorld().getPlayers())
                        if (p.getLocation().distanceSquared(l) < 24 * 24) ChacaLink.notify(p, "system", "puzzle:" + puzzle + "." + node, def.message(), "", 3000);
                    break;
                }
            }
            if (plugin.debug()) plugin.getLogger().info("[퍼즐] " + puzzle + "." + node + (on ? " 켜짐" : " 꺼짐") + " (" + cause + (who == null ? "" : ", " + who) + ")");
        }

        public void collected(String node, String item, String anim, String who) {
            dirty=true;pendingEffects.add(()->applyCollected(node,item,anim,who));
        }
        private void applyCollected(String node, String item, String anim, String who) {
            if (anim == null || anim.isBlank()) return;
            for (Gimmick g : plugin.world().inNode(puzzle, node)) if (g.live()) plugin.world().overlay(g, anim);
        }

        public void reset(String node) {
            for (Gimmick g : plugin.world().inNode(puzzle, node)) if (g.live()) plugin.world().playState(g, false);
        }
    }
}
