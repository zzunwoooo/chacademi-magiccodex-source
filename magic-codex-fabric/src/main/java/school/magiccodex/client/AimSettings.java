package school.magiccodex.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Camera + aiming preferences (client only, config/magiccodex/aim.properties).
 * Every value is clamped so a hand-edited file can never break the camera.
 */
public final class AimSettings {
    /** Over-the-shoulder third person (replaces Shoulder Surfing Reloaded). F5 front view stays vanilla. */
    public boolean shoulder = true;
    /** 1 = camera over the right shoulder (character on the left of the screen), -1 = left shoulder. */
    public int side = 1;
    /** Blocks behind the eyes (vanilla third person is 4). */
    public float distance = 3.4f;
    /** Blocks to the side of the eyes. */
    public float sideOffset = 0.85f;
    /** Blocks above the eyes. */
    public float height = 0.2f;
    /** Magic mode in third person: freeze the view and aim with a separate cursor. */
    public boolean aimCursor = true;
    /** Aim cursor speed multiplier (1 = the cursor moves as far as the view would have turned). */
    public float cursorSpeed = 1f;
    /** LoL "smart cast": cast at the cursor on key press instead of showing the preview first. */
    public boolean smartCast = false;

    public static final float MIN_DISTANCE = 1.5f, MAX_DISTANCE = 6f, MAX_SIDE = 2f, MIN_SPEED = .2f, MAX_SPEED = 3f;

    private static AimSettings current;
    private static Path path;

    public static AimSettings get() {
        if (current == null) {
            try { path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("magiccodex/aim.properties"); }
            catch (RuntimeException | LinkageError noLoader) { path = null; }
            current = path == null ? new AimSettings() : load(path);
        }
        return current;
    }

    public static AimSettings load(Path file) {
        var s = new AimSettings();
        if (file == null || !Files.isRegularFile(file)) return s;
        var p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
        catch (IOException | IllegalArgumentException error) { return s; }
        s.shoulder = bool(p, "shoulder", s.shoulder);
        s.side = "left".equalsIgnoreCase(p.getProperty("side", "right").trim()) ? -1 : 1;
        s.distance = num(p, "distance", s.distance, MIN_DISTANCE, MAX_DISTANCE);
        s.sideOffset = num(p, "side-offset", s.sideOffset, 0, MAX_SIDE);
        s.height = num(p, "height", s.height, -.5f, 1.5f);
        s.aimCursor = bool(p, "aim-cursor", s.aimCursor);
        s.cursorSpeed = num(p, "cursor-speed", s.cursorSpeed, MIN_SPEED, MAX_SPEED);
        s.smartCast = bool(p, "smart-cast", s.smartCast);
        return s;
    }

    public void save() {
        if (path == null) return;
        var p = new Properties();
        p.setProperty("shoulder", Boolean.toString(shoulder));
        p.setProperty("side", side < 0 ? "left" : "right");
        p.setProperty("distance", Float.toString(distance));
        p.setProperty("side-offset", Float.toString(sideOffset));
        p.setProperty("height", Float.toString(height));
        p.setProperty("aim-cursor", Boolean.toString(aimCursor));
        p.setProperty("cursor-speed", Float.toString(cursorSpeed));
        p.setProperty("smart-cast", Boolean.toString(smartCast));
        try {
            Files.createDirectories(path.getParent());
            try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { p.store(out, "Chacademi camera / aim (client only)"); }
        } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("aim settings save failed", error);
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }
    private static float num(Properties p, String key, float fallback, float min, float max) {
        try {
            String v = p.getProperty(key);
            float f = v == null ? fallback : Float.parseFloat(v.trim());
            return Float.isFinite(f) ? Math.clamp(f, min, max) : fallback;
        } catch (NumberFormatException bad) { return fallback; }
    }
}
