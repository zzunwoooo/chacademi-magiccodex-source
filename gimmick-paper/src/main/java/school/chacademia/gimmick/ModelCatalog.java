package school.chacademia.gimmick;

import java.io.File;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** models.yml: size and animation lengths per model (generated from dungeon-gimmicks). Unknown models still work. */
final class ModelCatalog {
    record Anim(double seconds, String loop) { long ticks() { return Math.max(1, Math.round(seconds * 20)); } }
    record Info(String id, String name, String category, double width, double height, Map<String, Anim> anims) {
        boolean has(String anim) { return anim != null && anims.containsKey(anim); }
    }
    private final Map<String, Info> models = new TreeMap<>();

    void load(File file) {
        models.clear();
        var yaml = AtomicYaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("models");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            var s = root.getConfigurationSection(id);
            if (s == null) continue;
            var anims = new LinkedHashMap<String, Anim>();
            var as = s.getConfigurationSection("animations");
            if (as != null) for (String a : as.getKeys(false))
                anims.put(a, new Anim(as.getDouble(a + ".length", 1), as.getString(a + ".loop", "once")));
            models.put(id, new Info(id, s.getString("name", id), s.getString("category", "?"),
                Math.max(0.2, s.getDouble("width", 1)), Math.max(0.1, s.getDouble("height", 1)), Collections.unmodifiableMap(anims)));
        }
    }
    Info get(String id) {
        Info i = models.get(id);
        return i != null ? i : new Info(id, id, "?", 1, 1, Map.of());
    }
    boolean known(String id) { return models.containsKey(id); }
    Set<String> ids() { return models.keySet(); }
}
