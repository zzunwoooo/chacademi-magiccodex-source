package kr.chacademy.npc.config;

import kr.chacademy.npc.core.CharacterSheet;
import kr.chacademy.npc.core.ScheduleEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * plugins/ChacaNPC/npcs/*.yml 캐릭터 파일을 읽는다.
 */
public final class CharacterRepository {

    private final File folder;
    private final Logger log;
    private volatile Map<String, CharacterSheet> byId = Map.of();

    public CharacterRepository(File folder, Logger log) {
        this.folder = folder;
        this.log = log;
    }

    public void load() {
        Map<String, CharacterSheet> map = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            for (File f : files) {
                try {
                    CharacterSheet c = read(YamlConfiguration.loadConfiguration(f), f.getName());
                    if (map.containsKey(c.id())) {
                        log.warning("[ChacaNPC] 캐릭터 id 중복: " + c.id() + " (" + f.getName() + ") — 건너뜀");
                        continue;
                    }
                    map.put(c.id(), c);
                } catch (Exception ex) {
                    log.warning("[ChacaNPC] 캐릭터 파일 읽기 실패: " + f.getName() + " — " + ex.getMessage());
                }
            }
        }
        byId = map;
        log.info("[ChacaNPC] 캐릭터 " + map.size() + "명 불러옴");
    }

    public CharacterSheet get(String id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<CharacterSheet> all() {
        return byId.values();
    }

    private static Map<String, List<String>> giftLikes(YamlConfiguration y) {
        Map<String, List<String>> out = new HashMap<>();
        ConfigurationSection g = y.getConfigurationSection("gift_likes");
        if (g != null) {
            for (String k : List.of("loved", "liked", "disliked")) {
                List<String> items = new ArrayList<>();
                for (String it : g.getStringList(k)) {
                    items.add(it.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", ""));
                }
                out.put(k, items);
            }
        }
        return out;
    }

    static CharacterSheet read(YamlConfiguration y, String fileName) {
        String id = y.getString("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id가 없습니다");
        }
        String name = y.getString("name", id);
        ConfigurationSection p = y.getConfigurationSection("persona");
        CharacterSheet.Persona persona = new CharacterSheet.Persona(
                p == null ? null : p.getString("role"),
                p == null ? null : p.getString("personality"),
                p == null ? null : p.getString("speech"),
                p == null ? List.of() : p.getStringList("likes"),
                p == null ? List.of() : p.getStringList("dislikes"),
                p == null ? List.of() : p.getStringList("knows"),
                p == null ? List.of() : p.getStringList("never_knows"),
                p == null ? List.of() : p.getStringList("sample_lines"));

        List<ScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("schedule")) {
            Object place = m.get("place");
            if (place == null) {
                continue;
            }
            Object activity = m.get("activity");
            schedule.add(new ScheduleEntry(ScheduleEntry.parseTime(m.get("time")), place.toString(),
                    activity == null ? null : activity.toString()));
        }

        Map<String, CharacterSheet.Relation> relations = new LinkedHashMap<>();
        ConfigurationSection rel = y.getConfigurationSection("relations");
        if (rel != null) {
            for (String other : rel.getKeys(false)) {
                ConfigurationSection r = rel.getConfigurationSection(other);
                if (r != null) {
                    relations.put(other, new CharacterSheet.Relation(r.getString("type", "friend"),
                            Math.max(0, Math.min(1, r.getDouble("gossip", 0.5)))));
                } else {
                    relations.put(other, new CharacterSheet.Relation(rel.getString(other, "friend"), 0.5));
                }
            }
        }

        Map<String, List<String>> fallback = new HashMap<>();
        ConfigurationSection fb = y.getConfigurationSection("fallback_lines");
        if (fb != null) {
            for (String k : fb.getKeys(false)) {
                fallback.put(k, fb.getStringList(k));
            }
        }

        Map<Integer, List<String>> byStage = new HashMap<>();
        ConfigurationSection st = y.getConfigurationSection("sample_lines_by_stage");
        if (st != null) {
            for (String k : st.getKeys(false)) {
                try {
                    byStage.put(Integer.parseInt(k), st.getStringList(k));
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 키는 무시
                }
            }
        }

        return new CharacterSheet(
                id,
                name,
                y.getString("type", "sub"),
                y.getString("gender", ""),
                y.getString("skin", ""),
                y.getString("color", null),
                y.getString("portrait", ""),
                y.getBoolean("romanceable", false),
                y.getBoolean("open-on-right-click", true),
                persona,
                schedule,
                y.getStringList("quests"),
                y.getStringList("hint_topics"),
                relations,
                fallback,
                byStage,
                giftLikes(y));
    }
}
