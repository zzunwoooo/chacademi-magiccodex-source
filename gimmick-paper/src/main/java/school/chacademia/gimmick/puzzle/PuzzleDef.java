package school.chacademia.gimmick.puzzle;

import java.util.*;

/** Parsed puzzles.yml entry (no Bukkit types). */
public record PuzzleDef(String id, Map<String, NodeDef> nodes, boolean persist) {

    /**
     * One node.
     * on/off/toggle: trigger keys (magic:fire, magic:any, interact, attack, stand, item:x, signal)
     * whileTriggers: "stand" keeps the node on only while someone stands on it
     * hold: seconds until it turns itself off (0 = stays)   latch: once on, stays on until reset
     * when: logic expression over other nodes (derived node)
     * collect: item id -> animation played when inserted; node turns on when all are in
     */
    public record NodeDef(String name, Set<String> on, Set<String> off, Set<String> toggle, Set<String> whileTriggers,
                          double hold, Expr when, boolean latch, double cooldown, boolean resetOnFail,
                          LinkedHashMap<String, String> collect,
                          String animOn, String animOff, Solid solid, String teleport,
                          List<String> commandsOn, List<String> commandsOff, String message, Device device) {
        public boolean listens(String trigger, Set<String> set) {
            if (set.contains(trigger)) return true;
            return trigger.startsWith("magic:") && set.contains("magic:any");
        }
    }

    /**
     * Device options (2026-10-20 dungeon gimmick spells).
     * manaOut: while the node is on, its gimmicks send a mana signal into adjacent conduits / devices.
     * level / position / facing: the node is on exactly while its device shows that state (water pillar 0..3, mirror/dial 0..3,
     *   statue facing 0..3 = south/west/north/east). color: light receiver only accepts beams of this colour.
     * move [dx,dy,dz] + speed (blocks/s): floating platform travels one way when the node turns on. launch: wind vent upward speed.
     * climb: vine pot ladder height (blocks of vines in front of the pot while on).
     */
    public record Device(boolean manaOut, Integer level, Integer position, Integer facing, String color,
                         double[] move, double speed, double launch, int climb) {
        public static final Device NONE = new Device(false, null, null, null, null, null, 2, 0, 0);
        public boolean condition() { return level != null || position != null || facing != null; }
    }

    /** Barrier box that exists while the node is in `whenOn` state. Sizes in blocks, centered on the gimmick. */
    public record Solid(int width, int height, int depth, int up, int forward, boolean whenOn) {}

    // ------------------------------------------------------------------ parsing from plain maps (YAML)
    @SuppressWarnings("unchecked")
    public static PuzzleDef parse(String id, Map<String, Object> raw, List<String> warnings) {
        var nodes = new LinkedHashMap<String, NodeDef>();
        Object rawNodes = raw.get("nodes");
        if (rawNodes instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                String name = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map<?, ?> nm)) { warnings.add(id + "." + name + ": 설정이 비어 있음"); continue; }
                try { nodes.put(name, node(name, (Map<String, Object>) (Map<?, ?>) nm)); }
                catch (RuntimeException ex) { warnings.add(id + "." + name + ": " + ex.getMessage()); }
            }
        }
        for (NodeDef n : nodes.values()) if (n.when() != null) {
            var refs = new HashSet<String>(); n.when().names(refs);
            for (String r : refs) if (!nodes.containsKey(r)) warnings.add(id + "." + n.name() + ": when 에 없는 노드 " + r);
        }
        boolean persist = !Boolean.FALSE.equals(raw.get("persist"));
        return new PuzzleDef(id, Collections.unmodifiableMap(nodes), persist);
    }

    private static NodeDef node(String name, Map<String, Object> m) {
        Expr when = m.get("when") == null ? null : Expr.parse(String.valueOf(m.get("when")));
        var collect = new LinkedHashMap<String, String>();
        Object c = m.get("collect");
        if (c instanceof Map<?, ?> cm) for (var e : cm.entrySet()) collect.put(key(e.getKey()), String.valueOf(e.getValue()));
        else if (c instanceof List<?> cl) for (Object o : cl) collect.put(key(o), "");
        Solid solid = null;
        if (m.get("solid") instanceof Map<?, ?> sm) {
            solid = new Solid(i(sm.get("width"), 1), i(sm.get("height"), 1), i(sm.get("depth"), 1), i(sm.get("up"), 0), i(sm.get("forward"), 0),
                Set.of("on", "true", "켜짐").contains(String.valueOf(sm.get("when")).toLowerCase(Locale.ROOT)));
        }
        boolean hasSeq = when != null && !Expr.seqs(when).isEmpty();
        // YAML 1.1 reads bare `on:`/`off:` keys as booleans, so the documented keys are turn-on / turn-off.
        Object onRaw = first(m, "turn-on", "켜기", Boolean.TRUE), offRaw = first(m, "turn-off", "끄기", Boolean.FALSE);
        return new NodeDef(name, set(onRaw), set(offRaw), set(first(m, "toggle", "전환", null)), set(first(m, "while", "동안", null)),
            d(m.get("hold"), 0), when, Boolean.TRUE.equals(m.get("latch")), d(m.get("cooldown"), 0.5),
            m.containsKey("reset-on-fail") ? Boolean.TRUE.equals(m.get("reset-on-fail")) : hasSeq,
            collect, str(m.get("anim-on")), str(m.get("anim-off")), solid, str(m.get("teleport")),
            list(m.get("command-on")), list(m.get("command-off")), str(m.get("message")), device(m));
    }

    private static Device device(Map<String, Object> m) {
        Object mana = first(m, "mana", "마력", null);
        boolean out = mana != null && Set.of("out", "true", "출력", "내보냄").contains(String.valueOf(mana).toLowerCase(Locale.ROOT));
        double[] move = null;
        if (m.get("move") instanceof List<?> l && l.size() == 3) move = new double[]{d(l.get(0), 0), d(l.get(1), 0), d(l.get(2), 0)};
        return new Device(out, opt(m.get("level"), 0, 3), opt(m.get("position"), 0, 3), facing(m.get("facing")),
            m.get("color") == null ? null : String.valueOf(m.get("color")).trim().toLowerCase(Locale.ROOT),
            move, Math.max(0.1, d(m.get("speed"), 2)), d(m.get("launch"), 0), Math.clamp(i(m.get("climb"), 0), 0, 32));
    }
    private static Integer opt(Object o, int min, int max) { return o == null ? null : Math.clamp(i(o, min), min, max); }
    /** 0..3 or south/west/north/east (남/서/북/동), or degrees. */
    public static Integer facingOf(Object o) { return facing(o); }
    static Integer facing(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "south", "s", "남", "남쪽" -> 0; case "west", "w", "서", "서쪽" -> 1; case "north", "n", "북", "북쪽" -> 2; case "east", "e", "동", "동쪽" -> 3;
            default -> { double v = d(o, 0); yield v > 3 || v < 0 ? Math.floorMod((int) Math.round(v / 90), 4) : Math.floorMod((int) Math.round(v), 4); }
        };
    }

    private static Object first(Map<?, ?> m, Object a, Object b, Object c) {
        if (m.containsKey(a)) return m.get(a);
        if (m.containsKey(b)) return m.get(b);
        if (c == null) return null;
        return m.containsKey(c) ? m.get(c) : m.get(String.valueOf(c)); // Bukkit turns boolean keys into "true"/"false"

    }
    static String key(Object o) { return String.valueOf(o).trim().toLowerCase(Locale.ROOT); }
    private static Set<String> set(Object o) {
        var out = new LinkedHashSet<String>();
        if (o instanceof Collection<?> c) for (Object x : c) out.add(key(x));
        else if (o != null) for (String s : String.valueOf(o).split(",")) if (!s.isBlank()) out.add(key(s));
        return Collections.unmodifiableSet(out);
    }
    private static List<String> list(Object o) {
        if (o instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
        return o == null ? List.of() : List.of(String.valueOf(o));
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static double d(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? def : Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
    private static int i(Object o, int def) { return (int) Math.round(d(o, def)); }
}
