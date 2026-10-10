package kr.chacademy.storyplugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 호감도 (플레이어 × NPC id). plugins/ChacademyStory/affinity.yml 에 저장.
 * ChacaNPC 가 DB 스레드에서도 읽으므로 스레드 안전하게.
 */
public final class AffinityStore {
    private final File file;
    private final Map<UUID, Map<String, Integer>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private int def = 25, min = -100, max = 100;

    public AffinityStore(File file) {
        this.file = file;
    }

    public void limits(int def, int min, int max) {
        this.def = def;
        this.min = min;
        this.max = max;
    }

    public int defaultScore() {
        return def;
    }

    public void load() {
        data.clear();
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                var sec = y.getConfigurationSection(key);
                if (sec == null) continue;
                Map<String, Integer> m = new ConcurrentHashMap<>();
                for (String npc : sec.getKeys(false)) m.put(npc, sec.getInt(npc));
                data.put(id, m);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var e : data.entrySet()) {
            for (var n : new TreeMap<>(e.getValue()).entrySet()) y.set(e.getKey() + "." + n.getKey(), n.getValue());
        }
        try {
            y.save(file);
        } catch (IOException ignored) {
            dirty = true;
        }
    }

    public int get(UUID player, String npc) {
        var m = data.get(player);
        Integer v = m == null ? null : m.get(npc);
        return v == null ? def : v;
    }

    public Map<String, Integer> all(UUID player) {
        var m = data.get(player);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    public int add(UUID player, String npc, int amount) {
        return set(player, npc, get(player, npc) + amount);
    }

    public int set(UUID player, String npc, int value) {
        int v = Math.max(min, Math.min(max, value));
        data.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(npc, v);
        dirty = true;
        return v;
    }

    /** 모드로 보낼 문자열 "npc=점수,npc=점수". */
    public String encode(UUID player) {
        StringBuilder sb = new StringBuilder();
        for (var e : new TreeMap<>(all(player)).entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
