package kr.chacademy.npc.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * places.yml — 장소 이름, 좌표, 배회 반경, 가는 길 중간 지점.
 */
public final class PlaceRepository {

    public static final class Place {
        public final String name;
        public String label;          // 프롬프트에 보이는 이름 (없으면 name)
        public Location location;
        public double radius;
        public final List<Location> waypoints = new ArrayList<>();

        Place(String name) {
            this.name = name;
        }

        public String label() {
            return label == null || label.isBlank() ? name : label;
        }
    }

    private final File file;
    private final Logger log;
    private final Map<String, Place> places = new LinkedHashMap<>();

    public PlaceRepository(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public synchronized void load() {
        places.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("places");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Location loc = readLoc(s);
            if (loc == null) {
                log.warning("[ChacaNPC] 장소 '" + name + "'의 월드를 찾을 수 없어 건너뜀");
                continue;
            }
            Place p = new Place(name);
            p.label = s.getString("label", null);
            p.location = loc;
            p.radius = s.getDouble("radius", 4);
            for (Map<?, ?> m : s.getMapList("waypoints")) {
                Location w = readLoc(m, loc.getWorld());
                if (w != null) {
                    p.waypoints.add(w);
                }
            }
            places.put(name, p);
        }
        log.info("[ChacaNPC] 장소 " + places.size() + "곳 불러옴");
    }

    public synchronized Place get(String name) {
        return name == null ? null : places.get(name);
    }

    public synchronized Collection<Place> all() {
        return new ArrayList<>(places.values());
    }

    public synchronized void setPlace(String name, Location loc, double radius) {
        Place p = places.computeIfAbsent(name, Place::new);
        p.location = loc.clone();
        p.radius = radius;
        save();
    }

    public synchronized boolean addWaypoint(String name, Location loc) {
        Place p = places.get(name);
        if (p == null) {
            return false;
        }
        p.waypoints.add(loc.clone());
        save();
        return true;
    }

    public synchronized boolean clearWaypoints(String name) {
        Place p = places.get(name);
        if (p == null) {
            return false;
        }
        p.waypoints.clear();
        save();
        return true;
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Place p : places.values()) {
            String base = "places." + p.name + ".";
            if (p.label != null) {
                y.set(base + "label", p.label);
            }
            y.set(base + "world", p.location.getWorld().getName());
            y.set(base + "x", round(p.location.getX()));
            y.set(base + "y", round(p.location.getY()));
            y.set(base + "z", round(p.location.getZ()));
            y.set(base + "yaw", round(p.location.getYaw()));
            y.set(base + "radius", p.radius);
            List<Map<String, Object>> wps = new ArrayList<>();
            for (Location w : p.waypoints) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", round(w.getX()));
                m.put("y", round(w.getY()));
                m.put("z", round(w.getZ()));
                wps.add(m);
            }
            y.set(base + "waypoints", wps);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            log.warning("[ChacaNPC] places.yml 저장 실패: " + ex.getMessage());
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Location readLoc(ConfigurationSection s) {
        World w = Bukkit.getWorld(s.getString("world", "world"));
        if (w == null) {
            return null;
        }
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
    }

    private static Location readLoc(Map<?, ?> m, World defaultWorld) {
        World w = defaultWorld;
        Object wn = m.get("world");
        if (wn != null) {
            World found = Bukkit.getWorld(wn.toString());
            if (found != null) {
                w = found;
            }
        }
        try {
            return new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }
}
