package school.chacademia.housing;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import school.magiccodex.protocol.HousingRules;

/** config.yml 값. */
record Settings(String server, int startSize, TreeMap<Integer, Long> expansion, boolean visitCommand, long guestbookCooldownMs, int guestbookKeep, int presenceTicks) {
    static Settings from(ConfigurationSection c, Logger log) {
        String server = c.getString("server-name", "school").strip();
        if (!server.matches("[A-Za-z0-9_-]{1,32}")) { log.warning("server-name 은 영문·숫자 32자 이하: school 로 씁니다."); server = "school"; }
        int start = Math.clamp(c.getInt("start-size", 3), HousingRules.MIN_SIZE, HousingRules.MAX_SIZE);
        var steps = new TreeMap<Integer, Long>();
        var section = c.getConfigurationSection("expansion");
        if (section != null) for (String key : section.getKeys(false)) {
            try {
                int size = Integer.parseInt(key.strip());
                long cost = section.getLong(key, -1);
                if (size <= start || size > HousingRules.MAX_SIZE || cost < 0) { log.warning("expansion " + key + ": 크기는 start-size 보다 크고 " + HousingRules.MAX_SIZE + " 이하, 비용은 0 이상"); continue; }
                steps.put(size, cost);
            } catch (NumberFormatException e) { log.warning("expansion " + key + ": 크기는 숫자"); }
        }
        return new Settings(server, start, steps, c.getBoolean("visit-command", true),
                Math.max(0, c.getLong("guestbook.cooldown-seconds", 10)) * 1000, Math.clamp(c.getInt("guestbook.keep", 100), 10, 1000),
                Math.clamp(c.getInt("presence-interval-ticks", 40), 20, 200));
    }

    /** 지금 크기 다음 단계 (없으면 null). */
    Map.Entry<Integer, Long> next(int size) { return expansion.higherEntry(size); }
}
