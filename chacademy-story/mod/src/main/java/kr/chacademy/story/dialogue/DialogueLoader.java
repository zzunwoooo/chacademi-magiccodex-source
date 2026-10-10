package kr.chacademy.story.dialogue;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** config/chaca_dialogue/&lt;id&gt;/dialogue.yml 을 읽는다. */
public final class DialogueLoader {
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_\\-]{1,64}");
    private static final Pattern KEY = Pattern.compile("[^\\s,=]{1,48}");

    private DialogueLoader() {}

    public static Path rootDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("chaca_dialogue");
    }

    public static Path folder(String id) {
        return rootDir().resolve(id);
    }

    public static List<String> listIds() {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(rootDir())) return ids;
        try (Stream<Path> s = Files.list(rootDir())) {
            s.filter(p -> Files.isRegularFile(p.resolve("dialogue.yml")))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> ID_PATTERN.matcher(n).matches())
                    .sorted().forEach(ids::add);
        } catch (IOException ignored) {
        }
        return ids;
    }

    public static Dialogue load(String id) throws IOException {
        if (!ID_PATTERN.matcher(id).matches()) throw new IOException("잘못된 대화 id: " + id);
        Path file = folder(id).resolve("dialogue.yml");
        if (!Files.isRegularFile(file)) throw new IOException("파일 없음: " + file);
        Object root;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
        } catch (RuntimeException e) {
            throw new IOException("yml 형식 오류: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> m)) throw new IOException("yml 이 비어 있음");

        Map<String, Dialogue.Speaker> speakers = new LinkedHashMap<>();
        if (m.get("speakers") instanceof Map<?, ?> sm) {
            for (var e : sm.entrySet()) {
                String sid = String.valueOf(e.getKey());
                Map<?, ?> v = e.getValue() instanceof Map<?, ?> x ? x : Map.of();
                Map<String, String> portraits = new LinkedHashMap<>();
                if (v.get("portraits") instanceof Map<?, ?> pm) {
                    for (var pe : pm.entrySet()) {
                        String img = String.valueOf(pe.getValue());
                        checkImage(img);
                        portraits.put(String.valueOf(pe.getKey()), img);
                    }
                }
                speakers.put(sid, new Dialogue.Speaker(sid, str(v.get("name"), sid), str(v.get("npc"), ""), portraits));
            }
        }

        Map<String, Dialogue.Scene> scenes = new LinkedHashMap<>();
        if (m.get("scenes") instanceof Map<?, ?> scm) {
            for (var e : scm.entrySet()) {
                String sid = String.valueOf(e.getKey());
                Map<?, ?> v = e.getValue() instanceof Map<?, ?> x ? x : Map.of();
                List<Dialogue.Redirect> redirects = new ArrayList<>();
                for (Object o : list(v.get("redirect"))) {
                    if (o instanceof Map<?, ?> rm) {
                        Dialogue.Need need = need(rm);
                        if (need != null) redirects.add(new Dialogue.Redirect(need, str(rm.get("to"), "")));
                    }
                }
                List<Dialogue.Line> lines = new ArrayList<>();
                for (Object o : list(v.get("lines"))) {
                    if (o instanceof Map<?, ?> lm) {
                        lines.add(new Dialogue.Line(str(lm.get("who"), ""), str(lm.get("face"), ""), str(lm.get("text"), "")));
                    }
                }
                List<Dialogue.Choice> choices = new ArrayList<>();
                for (Object o : list(v.get("choices"))) {
                    if (o instanceof Map<?, ?> cm) {
                        Dialogue.Need need = cm.get("need") instanceof Map<?, ?> nm ? need(nm) : null;
                        choices.add(new Dialogue.Choice(str(cm.get("text"), "…"), str(cm.get("to"), ""), need,
                                affinity(cm.get("affinity")), eventName(cm.get("event"))));
                    }
                }
                if (choices.size() > 6) throw new IOException("선택지는 6개까지: " + sid);
                scenes.put(sid, new Dialogue.Scene(sid, redirects, lines, choices, str(v.get("next"), ""),
                        eventName(v.get("event")), affinity(v.get("affinity"))));
            }
        }
        if (scenes.isEmpty()) throw new IOException("장면이 없음");
        String start = str(m.get("start"), scenes.keySet().iterator().next());
        if (!scenes.containsKey(start)) throw new IOException("시작 장면이 없음: " + start);

        return new Dialogue(id, str(m.get("title"), id), start,
                clamp(num(m.get("dim"), 0.35), 0, 0.9),
                clamp(num(m.get("type_speed"), 0.03), 0, 0.5),
                str(m.get("type_sound"), "default"),
                clamp(num(m.get("type_sound_volume"), 0.5), 0, 1),
                speakers, scenes);
    }

    private static Dialogue.Need need(Map<?, ?> m) {
        String npc = str(m.get("npc"), "");
        if (npc.isEmpty()) return null;
        Integer max = m.get("max") == null || String.valueOf(m.get("max")).isBlank() ? null : (int) num(m.get("max"), 100);
        return new Dialogue.Need(npc, (int) num(m.get("min"), -1000), max);
    }

    /** affinity: [{npc, add}, ...] 또는 예전 형식 {npc, add} 또는 {npc이름: 값}. 한 번에 ±20 까지. */
    private static List<Dialogue.AffinityChange> affinity(Object o) {
        List<Dialogue.AffinityChange> out = new ArrayList<>();
        if (o instanceof Map<?, ?> m && m.containsKey("npc")) o = List.of(m);
        if (o instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) add(out, String.valueOf(e.getKey()), num(e.getValue(), 0));
        } else {
            for (Object x : list(o)) if (x instanceof Map<?, ?> am) add(out, str(am.get("npc"), ""), num(am.get("add"), 0));
        }
        return List.copyOf(out);
    }

    private static void add(List<Dialogue.AffinityChange> out, String npc, double v) {
        int add = (int) Math.max(-20, Math.min(20, Math.round(v)));
        if (!npc.isBlank() && add != 0 && out.size() < 8) out.add(new Dialogue.AffinityChange(npc.strip(), add));
    }

    private static String eventName(Object o) {
        String s = str(o, "").trim();
        return KEY.matcher(s).matches() ? s : "";
    }

    private static void checkImage(String name) throws IOException {
        if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new IOException("그림 이름에 경로 불가: " + name);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("그림은 PNG만: " + name);
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(o.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
