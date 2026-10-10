package kr.chacademy.storyplugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 호감도 (플레이어 × NPC id). plugins/ChacademyStory/affinity.yml 에 저장. MagicCodexBridge 가 없을 때만 쓴다.
 * 값 읽기·쓰기는 메인 스레드, 저장은 비동기 타이머에서 하므로 스레드 안전하게.
 * <p>파일은 이 서버만의 것이다 (서버가 둘이면 서로 다른 점수). 여러 서버가 같은 점수를 보려면 MagicCodexBridge 를 쓴다.
 */
public final class AffinityStore {
    private final File file;
    private final Logger log;
    private final Map<UUID, Map<String, Integer>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    /** 파일을 읽지 못했으면 true: 덮어쓰지 않는다 (고친 뒤 /cutscene reload). */
    private volatile boolean readFailed = false;
    private long lastRefuseLog;
    private int def = 25, min = -100, max = 100;

    public AffinityStore(File file) {
        this(file, Logger.getLogger("ChacademyStory"));
    }

    public AffinityStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void limits(int def, int min, int max) {
        this.def = def;
        this.min = min;
        this.max = max;
    }

    public int defaultScore() {
        return def;
    }

    public boolean readFailed() {
        return readFailed;
    }

    /**
     * 파일을 읽는다. 읽을 수 없거나 깨져 있으면 그 파일을 .corrupt-시각 으로 복사해 두고,
     * 메모리의 마지막 정상 값을 그대로 쓰며, 파일은 덮어쓰지 않는다.
     */
    public synchronized boolean load() {
        if (!file.isFile()) {
            data.clear();
            readFailed = false;
            return true;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (Exception e) {
            readFailed = true;
            File copy = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
            try {
                Files.copy(file.toPath(), copy.toPath());
            } catch (IOException | RuntimeException copyFailed) {
                log.log(Level.SEVERE, "깨진 affinity.yml 을 복사해 두지 못했습니다", copyFailed);
            }
            log.log(Level.SEVERE, "affinity.yml 을 읽을 수 없습니다. 파일은 덮어쓰지 않고 그대로 둡니다 (복사본: " + copy.getName()
                    + "). 메모리의 마지막 값으로 계속하지만 저장되지 않습니다. 파일을 고친 뒤 /cutscene reload 하세요.", e);
            return false;
        }
        Map<UUID, Map<String, Integer>> loaded = new HashMap<>();
        for (String key : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                var sec = y.getConfigurationSection(key);
                if (sec == null) continue;
                Map<String, Integer> m = new ConcurrentHashMap<>();
                for (String npc : sec.getKeys(false)) m.put(npc, sec.getInt(npc));
                loaded.put(id, m);
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.clear();
        data.putAll(loaded);
        readFailed = false;
        dirty = false;
        return true;
    }

    /** 비동기 타이머와 onDisable 에서 부른다. 임시 파일에 쓰고 바꿔치기해서 쓰다 꺼져도 파일이 반쪽이 되지 않는다. */
    public synchronized void saveIfDirty() {
        if (!dirty) return;
        if (readFailed) {
            long now = System.currentTimeMillis();
            if (now - lastRefuseLog > 300_000) {
                lastRefuseLog = now;
                log.severe("affinity.yml 을 읽지 못한 상태라 호감도를 저장하지 않습니다 (기존 파일 보호). 파일을 고친 뒤 /cutscene reload 하세요.");
            }
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var e : data.entrySet()) {
            for (var n : new TreeMap<>(e.getValue()).entrySet()) y.set(e.getKey() + "." + n.getKey(), n.getValue());
        }
        try {
            StorySafety.atomicWrite(file.toPath(), y.saveToString());
        } catch (IOException | RuntimeException e) {
            dirty = true;
            long now = System.currentTimeMillis();
            if (now - lastRefuseLog > 60_000) {
                lastRefuseLog = now;
                log.log(Level.WARNING, "affinity.yml 저장 실패 (다시 시도합니다)", e);
            }
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
