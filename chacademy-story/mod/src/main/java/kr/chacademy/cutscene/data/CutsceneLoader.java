package kr.chacademy.cutscene.data;

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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** config/chaca_cutscene/&lt;id&gt;/cutscene.yml 을 읽는다. */
public final class CutsceneLoader {
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_\\-]{1,64}");

    private CutsceneLoader() {}

    public static Path rootDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("chaca_cutscene");
    }

    public static Path folder(String id) {
        return rootDir().resolve(id);
    }

    /** 폴더 안에 cutscene.yml 이 있는 컷신 id 목록. */
    public static List<String> listIds() {
        List<String> ids = new ArrayList<>();
        Path root = rootDir();
        if (!Files.isDirectory(root)) return ids;
        try (Stream<Path> s = Files.list(root)) {
            s.filter(p -> Files.isRegularFile(p.resolve("cutscene.yml")))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> ID_PATTERN.matcher(n).matches())
                    .sorted()
                    .forEach(ids::add);
        } catch (IOException ignored) {
        }
        return ids;
    }

    public static Cutscene load(String id) throws IOException {
        if (!ID_PATTERN.matcher(id).matches()) throw new IOException("잘못된 컷신 id: " + id);
        Path file = folder(id).resolve("cutscene.yml");
        if (!Files.isRegularFile(file)) throw new IOException("파일 없음: " + file);

        Object root;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
        } catch (RuntimeException e) {
            throw new IOException("yml 형식 오류: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> m)) throw new IOException("yml 최상위가 비어 있음");

        List<Cutscene.Scene> scenes = new ArrayList<>();
        for (Object o : list(m.get("scenes"))) {
            if (!(o instanceof Map<?, ?> sm)) continue;
            List<String> images = new ArrayList<>();
            for (Object img : list(sm.get("images"))) {
                String name = String.valueOf(img);
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new IOException("이미지 이름에 경로를 넣을 수 없음: " + name);
                }
                images.add(name);
            }
            if (images.isEmpty() && sm.get("image") != null) images.add(String.valueOf(sm.get("image")));
            if (images.isEmpty()) throw new IOException("이미지가 없는 장면이 있음");

            Map<?, ?> zoom = sm.get("zoom") instanceof Map<?, ?> z ? z : Map.of();
            List<Cutscene.Line> lines = new ArrayList<>();
            for (Object lo : list(sm.get("lines"))) {
                if (!(lo instanceof Map<?, ?> lm)) continue;
                double start = num(lm.get("start"), 0);
                double end = num(lm.get("end"), start + 3);
                lines.add(new Cutscene.Line(str(lm.get("text"), ""), start, Math.max(end, start + 0.1)));
            }
            scenes.add(new Cutscene.Scene(
                    images,
                    clamp(num(sm.get("fps"), 6), 0.5, 30),
                    clamp(num(sm.get("duration"), 5), 0.2, 600),
                    clamp(num(zoom.get("x"), 0.5), 0, 1),
                    clamp(num(zoom.get("y"), 0.5), 0, 1),
                    clamp(num(zoom.get("from"), 1.0), 1, 4),
                    clamp(num(zoom.get("to"), 1.0), 1, 4),
                    str(sm.get("transition"), "ink"),
                    clamp(num(sm.get("transition_time"), 1.2), 0, 10),
                    lines));
        }
        if (scenes.isEmpty()) throw new IOException("장면이 하나도 없음");

        return new Cutscene(
                id,
                str(m.get("title"), id),
                str(m.get("font"), "medium"),
                clamp(num(m.get("font_size"), 1.0), 0.4, 2.5),
                color(m.get("text_color"), 0xEFE3C8),
                clamp(num(m.get("text_x"), 0.5), 0, 1),
                textY(m.get("text_y")),
                bool(m.get("text_shadow"), false),
                bool(m.get("italic"), true),
                str(m.get("prefix"), "- "),
                clamp(num(m.get("type_speed"), 0.06), 0, 1),
                clamp(num(m.get("letterbox"), 0.14), 0, 0.3),
                clamp(num(m.get("letterbox_time"), 1.6), 0, 10),
                clamp(num(m.get("end_fade"), 1.2), 0, 10),
                str(m.get("type_sound"), "default").trim(),
                clamp(num(m.get("type_sound_volume"), 0.5), 0, 1),
                bgmName(m.get("bgm")),
                clamp(num(m.get("bgm_volume"), 0.6), 0, 1),
                clamp(num(m.get("bgm_start"), 0), 0, 3600),
                clamp(num(m.get("bgm_fade_in"), 3.0), 0, 30),
                clamp(num(m.get("bgm_fade_out"), 4.0), 0, 30),
                scenes);
    }

    /** 컷신 폴더 안의 ogg 파일 이름. 비우면 배경음 없음. */
    private static String bgmName(Object o) throws IOException {
        if (o == null) return "";
        String name = o.toString().trim();
        if (name.isEmpty() || name.equalsIgnoreCase("none")) return "";
        if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new IOException("bgm 이름에 경로를 넣을 수 없음: " + name);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")) throw new IOException("bgm은 ogg 파일만 쓸 수 있어요: " + name);
        return name;
    }

    /** "#RRGGBB" 또는 "RRGGBB". 틀리면 기본값. */
    private static int color(Object o, int def) {
        if (o == null) return def;
        String t = o.toString().trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9a-fA-F]{6}")) return def;
        return Integer.parseInt(t, 16);
    }

    /** "auto" 또는 비우면 아래 띠 가운데, 숫자면 화면 높이 비율 (0 위 ~ 1 아래). */
    private static double textY(Object o) {
        if (o == null || "auto".equalsIgnoreCase(o.toString().trim())) return Cutscene.AUTO;
        return clamp(num(o, Cutscene.AUTO), 0, 1);
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

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(o.toString());
        return def;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
