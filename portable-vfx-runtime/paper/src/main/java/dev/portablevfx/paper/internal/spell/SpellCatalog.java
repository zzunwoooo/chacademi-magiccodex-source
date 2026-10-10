package dev.portablevfx.paper.internal.spell;

import java.util.*;
import java.util.regex.Pattern;

/** Immutable server catalogue. It never interprets display order as spell identity. */
public final class SpellCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");
    private static final Pattern EFFECT = Pattern.compile("claude:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)+");

    /** Only an explicit trigger starts a phase; no inferred timeline from effect names/demo data. */
    public record Phase(String trigger, String effect, String anchor, int delayTicks, int durationTicks) {
        public Phase {
            if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("Missing phase trigger");
            if (effect == null || !EFFECT.matcher(effect).matches() || effect.contains(".."))
                throw new IllegalArgumentException("Invalid canonical Claude effect ID: " + effect);
            if (!Set.of("caster", "target", "projectile", "impact", "external").contains(anchor))
                throw new IllegalArgumentException("Unknown phase anchor: " + anchor);
            if (delayTicks < 0 || delayTicks > 12000 || durationTicks < 1 || durationTicks > dev.portablevfx.protocol.VfxProtocol.MAX_DURATION_TICKS)
                throw new IllegalArgumentException("Invalid phase timing");
        }
    }

    public record Spell(int number, String id, String name, String permission, String icon,
                        Double manaCost, Double cooldownSeconds, boolean reviewed,
                        boolean enabled, List<String> aliases, List<Phase> phases) {
        public Spell {
            if (number < 1 || number > 10000 || id == null || !ID.matcher(id).matches())
                throw new IllegalArgumentException("Invalid spell identity");
            if (name == null || name.isBlank() || permission == null || permission.isBlank())
                throw new IllegalArgumentException("Missing spell name/permission: " + id);
            icon = icon == null ? "" : icon;
            checkCost(manaCost, "mana"); checkCost(cooldownSeconds, "cooldown");
            aliases = List.copyOf(aliases); phases = List.copyOf(phases);
            if (enabled && (!reviewed || manaCost == null || cooldownSeconds == null || phases.isEmpty()))
                throw new IllegalArgumentException("Enabled spell needs review, known costs and explicit VFX phases: " + id);
        }
        private static void checkCost(Double value, String field) {
            if (value != null && (!Double.isFinite(value) || value < 0))
                throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private final Map<String, Spell> spells;
    private final Map<String, Spell> names;

    public SpellCatalog(Collection<Spell> entries) {
        Map<String, Spell> ids = new LinkedHashMap<>();
        Map<String, Spell> lookup = new HashMap<>();
        Set<Integer> numbers = new HashSet<>();
        for (Spell spell : entries) {
            if (ids.putIfAbsent(spell.id(), spell) != null || !numbers.add(spell.number()))
                throw new IllegalArgumentException("Duplicate spell ID/number: " + spell.id());
            addName(lookup, spell.id(), spell);
            for (String alias : spell.aliases()) addName(lookup, alias, spell);
        }
        spells = Collections.unmodifiableMap(ids); names = Map.copyOf(lookup);
    }

    private static void addName(Map<String, Spell> names, String value, Spell spell) {
        String key = normalize(value);
        if (key.isEmpty()) throw new IllegalArgumentException("Empty alias for " + spell.id());
        Spell previous = names.putIfAbsent(key, spell);
        if (previous != null && previous != spell)
            throw new IllegalArgumentException("Ambiguous command alias: " + value);
    }

    public Optional<Spell> resolve(String value) { return Optional.ofNullable(names.get(normalize(value))); }
    public Collection<Spell> entries() { return spells.values(); }
    public List<String> complete(String prefix) {
        String key = normalize(prefix);
        return spells.keySet().stream().filter(s -> s.startsWith(key)).sorted().toList();
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Plain maps keep the validated domain independent from a particular YAML implementation. */
    public static SpellCatalog fromMap(Map<String, ?> root) {
        if (!(root.get("schema-version") instanceof Number version) || version.doubleValue() != 1)
            throw new IllegalArgumentException("spell-catalog schema-version must be 1");
        Object raw = root.get("spells");
        if (!(raw instanceof Map<?, ?> entries)) throw new IllegalArgumentException("Missing spells map");
        List<Spell> result = new ArrayList<>();
        for (var entry : entries.entrySet()) result.add(parseSpell(String.valueOf(entry.getKey()), entry.getValue()));
        return new SpellCatalog(result);
    }

    /**
     * 마법별 검증. 파일 구조(schema-version, spells 맵)가 잘못되면 fromMap 과 같이 예외를 던지지만,
     * 개별 마법이 잘못됐거나 번호/별칭이 앞선 마법과 겹치면 그 마법만 건너뛰고 problems 에 "ID -> 사유"를 남긴다.
     */
    public static SpellCatalog fromMapLenient(Map<String, ?> root, Map<String, String> problems) {
        if (!(root.get("schema-version") instanceof Number version) || version.doubleValue() != 1)
            throw new IllegalArgumentException("spell-catalog schema-version must be 1");
        Object raw = root.get("spells");
        if (!(raw instanceof Map<?, ?> entries)) throw new IllegalArgumentException("Missing spells map");
        List<Spell> accepted = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        Map<String, String> owners = new HashMap<>();
        for (var entry : entries.entrySet()) {
            String id = String.valueOf(entry.getKey());
            try {
                Spell spell = parseSpell(id, entry.getValue());
                if (numbers.contains(spell.number())) throw new IllegalArgumentException("Duplicate spell number: " + spell.number());
                List<String> keys = new ArrayList<>();
                keys.add(normalize(spell.id()));
                for (String alias : spell.aliases()) keys.add(normalize(alias));
                for (String key : keys) {
                    if (key.isEmpty()) throw new IllegalArgumentException("Empty alias");
                    String owner = owners.get(key);
                    if (owner != null && !owner.equals(spell.id())) throw new IllegalArgumentException("Name or alias '" + key + "' already belongs to " + owner);
                }
                numbers.add(spell.number());
                for (String key : keys) owners.put(key, spell.id());
                accepted.add(spell);
            } catch (RuntimeException invalid) {
                problems.put(id, invalid.getClass().getSimpleName() + ": " + invalid.getMessage());
            }
        }
        return new SpellCatalog(accepted);
    }

    private static Spell parseSpell(String id, Object value) {
        Map<?, ?> s = map(value, id);
        List<String> aliases = strings(s.get("aliases"));
        List<Phase> phases = new ArrayList<>();
        Object rawPhases = s.get("phases");
        if (rawPhases != null && !(rawPhases instanceof List<?>)) throw new IllegalArgumentException("phases must be a list");
        if (rawPhases instanceof List<?> list) for (Object item : list) {
            Map<?, ?> p = map(item, "phase");
            phases.add(new Phase(string(p,"trigger"), string(p,"effect"), string(p,"anchor"),
                    integer(p,"delay-ticks",0), integer(p,"duration-ticks",-1)));
        }
        return new Spell(integer(s,"number",-1), id, string(s,"name"), string(s,"permission"),
                optionalString(s,"icon",""), decimal(s,"mana-cost"), decimal(s,"cooldown-seconds"),
                bool(s,"reviewed"), bool(s,"enabled"), aliases, phases);
    }
    private static Map<?, ?> map(Object value,String field) {
        if (!(value instanceof Map<?, ?> m)) throw new IllegalArgumentException(field + " must be a map");
        return m;
    }
    private static String string(Map<?, ?> m,String k) {
        Object v=m.get(k); if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException("Missing " + k); return s;
    }
    private static String optionalString(Map<?, ?> m,String k,String fallback) {
        Object v=m.get(k); if(v==null)return fallback;
        if(!(v instanceof String s))throw new IllegalArgumentException(k+" must be text"); return s;
    }
    private static Double decimal(Map<?, ?> m,String k) {
        Object v=m.get(k); if(v==null)return null;
        if(!(v instanceof Number n))throw new IllegalArgumentException(k+" must be numeric"); return n.doubleValue();
    }
    private static int integer(Map<?, ?> m,String k,int fallback) {
        Double v=decimal(m,k); if(v==null)return fallback;
        if(!Double.isFinite(v)||v!=Math.rint(v)||v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)throw new IllegalArgumentException(k+" must be integral"); return v.intValue();
    }
    private static boolean bool(Map<?, ?> m,String k) {
        Object v=m.get(k);if(v==null)return false;
        if(!(v instanceof Boolean b))throw new IllegalArgumentException(k+" must be boolean");return b;
    }
    private static List<String> strings(Object v) {
        if(v==null)return List.of();if(!(v instanceof List<?> list))throw new IllegalArgumentException("aliases must be a list");
        List<String> out=new ArrayList<>();for(Object e:list){if(!(e instanceof String s))throw new IllegalArgumentException("alias must be text");out.add(s);}return out;
    }
}
