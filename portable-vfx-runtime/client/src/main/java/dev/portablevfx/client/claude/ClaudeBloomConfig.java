package dev.portablevfx.client.claude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Global effect-only bloom gain. Authored cores, tint, threshold and scatter are untouched. */
public final class ClaudeBloomConfig {
    public static final double DEFAULT_STRENGTH = 0.65;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("PortableVFX");
    private static volatile double strength = DEFAULT_STRENGTH;
    private ClaudeBloomConfig() { }
    public static double strength() { return strength; }
    public static double intensity(double authoredIntensity) { return authoredIntensity * strength; }
    /** Read once at client startup; missing files are created with the balanced default. */
    public static void load(Path path) throws IOException {
        strength = DEFAULT_STRENGTH;
        if (!Files.exists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, "# Effect-only bloom multiplier; 0=off, 0.65=balanced, 1=original. Restart Minecraft after editing.\nstrength=0.65\n");
            return;
        }
        Properties properties = new Properties();
        try (var input = Files.newBufferedReader(path)) {
            properties.load(input);
            strength = parse(properties.getProperty("strength", Double.toString(DEFAULT_STRENGTH)));
        }
        catch (IllegalArgumentException error) { throw new IOException("Invalid PortableVFX bloom strength; expected a finite number (out-of-range values clamp to 0..1)", error); }
    }
    /** Finite out-of-range values clamp to [0,1] with a warning; NaN/Infinity/garbage reject (caller keeps the default). */
    static double parse(String value) {
        double parsed = Double.parseDouble(value.trim());
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("strength must be finite");
        double clamped = Math.max(0, Math.min(1, parsed));
        if (clamped != parsed) LOG.warn("PortableVFX bloom strength {} is outside 0..1; clamped to {}", parsed, clamped);
        return clamped;
    }
}
