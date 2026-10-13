package school.magiccodex.client;

/** Settings slider math (no Minecraft types): one mapping for drawing the knob and reading the drag. */
final class SettingsSlider {
    private SettingsSlider() {}
    /** Panel units: the knob centre runs over the track's inner channel only. */
    static final float TRACK_X = 145, TRACK_W = 708, KNOB_MIN = TRACK_X + 19, KNOB_MAX = TRACK_X + TRACK_W - 19;
    static float knobX(float t) { return KNOB_MIN + (KNOB_MAX - KNOB_MIN) * Math.clamp(t, 0, 1); }
    static float fraction(double x) { return (float) Math.clamp((x - KNOB_MIN) / (KNOB_MAX - KNOB_MIN), 0, 1); }
    /** Typed value: number in the row's unit ("%" optional on percent rows); null when not a number. */
    static Float parse(String raw, boolean percent) {
        String s = raw.trim().replace("%", "").replace(',', '.');
        if (s.isEmpty() || s.equals("-") || s.equals(".")) return null;
        try { float v = Float.parseFloat(s); if (!Float.isFinite(v)) return null; return percent ? v / 100f : v; }
        catch (NumberFormatException e) { return null; }
    }
}
