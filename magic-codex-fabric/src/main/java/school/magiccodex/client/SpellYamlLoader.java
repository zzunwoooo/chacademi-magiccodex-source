package school.magiccodex.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import school.magiccodex.client.CodexData.Category;
import school.magiccodex.client.CodexData.Spell;

/** UTF-8 local catalog. A failed reload never publishes a partial catalog. */
public final class SpellYamlLoader {
    public static final int MAX_SPELLS = school.magiccodex.protocol.PermissionProtocol.MAX_PERMISSIONS;
    public record Result(List<Spell> spells, List<String> errors) {
        public Result { spells = List.copyOf(spells); errors = List.copyOf(errors); }
        public boolean success() { return errors.isEmpty(); }
    }
    public void installDefaults(Path directory) throws IOException {
        if (Files.exists(directory)) return;
        Files.createDirectories(directory);
        try (var stream = SpellYamlLoader.class.getResourceAsStream("/assets/magiccodex/default-spells/index.txt")) {
            if (stream == null) throw new IOException("기본 마법 목록을 찾지 못했습니다.");
            for (String id : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().filter(s -> !s.isBlank()).toList()) {
                try (var file = SpellYamlLoader.class.getResourceAsStream("/assets/magiccodex/default-spells/" + id + ".yml")) {
                    if (file == null) throw new IOException("기본 마법 파일 누락: " + id);
                    Files.copy(file, directory.resolve(id + ".yml"));
                }
            }
        }
    }
    public Result load(Path directory) {
        var errors = new ArrayList<String>();
        var spells = new ArrayList<Spell>();
        var ids = new HashSet<String>();
        try (var entries = Files.list(directory)) {
            var files = entries.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).matches(".*\\.ya?ml$"))
                    .sorted().limit(MAX_SPELLS + 1L).toList();
            if (files.size() > MAX_SPELLS)
                return new Result(List.of(), List.of("마법 파일은 최대 " + MAX_SPELLS + "개까지 읽을 수 있습니다."));
            for (Path file : files) {
                try {
                    if (Files.size(file) > 65536) throw new IllegalArgumentException("파일은 64KB 이하여야 합니다.");
                    Spell spell = parse(Files.readString(file, StandardCharsets.UTF_8));
                    if (!ids.add(spell.id())) throw new IllegalArgumentException("중복 id: " + spell.id());
                    spells.add(spell);
                } catch (Exception error) {
                    errors.add(file.getFileName() + ": " + error.getMessage());
                }
            }
        } catch (IOException error) { errors.add("마법 폴더 읽기 실패: " + error.getMessage()); }
        spells.sort(Comparator.comparingInt(Spell::order).thenComparing(Spell::id));
        return new Result(errors.isEmpty() ? spells : List.of(), errors);
    }
    public Spell parse(String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(12);
        options.setCodePointLimit(65536);
        Map<?, ?> root = mapping(new Yaml(new SafeConstructor(options)).load(source), "마법");
        // Retired rank/circle metadata is accepted only for old file compatibility; never used.
        keys(root, Set.of("id", "name", "description", "category", "rank", "circle", "icon", "permission", "discovery", "research", "cast", "order"));
        String id = string(root, "id", null, 64);
        if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("id는 영문 소문자·숫자·_·-만 사용할 수 있습니다.");
        Category category;
        try { category = Category.valueOf(string(root, "category", null, 16).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("category: wind, fire, water, earth, light, dark 중 선택하세요."); }
        if (category == Category.ALL) throw new IllegalArgumentException("category에 all은 사용할 수 없습니다.");
        String icon = string(root, "icon", "", 256);
        if (!icon.isBlank() && (!icon.matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png") || icon.contains("..")))
            throw new IllegalArgumentException("icon은 namespace:textures/.../image.png 형식이어야 합니다.");
        String permission = string(root, "permission", null, 100);
        if (!permission.matches("[a-z0-9_.-]{1,100}")) throw new IllegalArgumentException("permission 형식이 올바르지 않습니다.");
        Map<?, ?> discovery = mapping(root.get("discovery"), "discovery");
        keys(discovery, Set.of("description"));
        Map<?, ?> cast = root.containsKey("cast") ? mapping(root.get("cast"), "cast") : Map.of();
        keys(cast, Set.of("command", "cooldown-seconds", "mana-cost"));
        String command = string(cast, "command", "", 256);
        if (command.startsWith("/") || command.contains("\n") || command.contains("\r"))
            throw new IllegalArgumentException("cast.command는 / 없이 한 줄로 작성하세요.");
        return new Spell(id, string(root, "name", null, 64), category,
                string(root, "description", "", 1024), string(discovery, "description", null, 512),
                string(root, "research", "", 512), decimal(cast, "cooldown-seconds", 0, 0, 86400), false,
                icon, permission, command, number(root, "order", 0, -1000000, 1000000), false,
                number(cast, "mana-cost", 0, 0, 1000000));
    }
    private static Map<?, ?> mapping(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(field + " 항목은 key: value 형식이어야 합니다.");
        return map;
    }
    private static void keys(Map<?, ?> map, Set<String> allowed) {
        for (Object key : map.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("알 수 없는 항목: " + key);
    }
    private static String string(Map<?, ?> map, String key, String fallback, int max) {
        Object raw = map.get(key);
        if (raw == null && !map.containsKey(key) && fallback != null) return fallback;
        if (!(raw instanceof String value)) throw new IllegalArgumentException(key + ": 문자열을 입력하세요.");
        value = value.strip();
        if ((value.isEmpty() && fallback == null) || value.length() > max || value.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r'))
            throw new IllegalArgumentException(key + ": 길이 또는 문자를 확인하세요 (최대 " + max + "자).");
        if (!Set.of("description", "research").contains(key) && (value.contains("\n") || value.contains("\r")))
            throw new IllegalArgumentException(key + ": 한 줄로 입력하세요.");
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
    private static double decimal(Map<?, ?> map,String key,double fallback,double min,double max) {
        if(!map.containsKey(key))return fallback;Object raw=map.get(key);
        if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)
            throw new IllegalArgumentException(key+": "+min+"~"+max+" 범위의 수를 입력하세요.");
        return n.doubleValue();
    }
    private static int number(Map<?, ?> map, String key, int fallback, int min, int max) {
        if (!map.containsKey(key)) return fallback;
        Object raw = map.get(key);
        if (!(raw instanceof Integer value) || value < min || value > max)
            throw new IllegalArgumentException(key + ": " + min + "~" + max + " 범위의 정수를 입력하세요.");
        return value;
    }
}
