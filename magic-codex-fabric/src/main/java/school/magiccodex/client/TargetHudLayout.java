package school.magiccodex.client;

/** Pure math of the target HUD (no Minecraft classes), so it is unit-tested. Units: frame-asset px (frame.png 1237 x 101). */
final class TargetHudLayout {
    private TargetHudLayout() {}
    /** Inner HP slot x ranges, measured on frame.png (6 fixed segments, owner 2026-10-19). */
    static final float[][] SLOTS = {{16.2f, 214.8f}, {223.2f, 415.2f}, {423.6f, 615.0f}, {622.8f, 814.2f}, {822.6f, 1015.2f}, {1023.0f, 1221.6f}};

    /** Fraction of HP held by segment `i` (0..1) for a total fraction `f`. */
    static float segment(float f, int i) { return Math.clamp(f * SLOTS.length - i, 0, 1); }
    /** Index of the segment holding the tip (drawn brighter), -1 when empty. */
    static int tipSegment(float f) {
        if (!(f > 0)) return -1;
        return Math.clamp((int) Math.ceil(f * SLOTS.length - 1e-6) - 1, 0, SLOTS.length - 1);
    }
    /** HUD width in GUI px for a screen width. */
    static float width(int screenW) { return Math.clamp(screenW * .304f, 184, 400); } // owner 2026-10-19: 80 % of the first size
    /** Center x of the n-th icon counted from the right (0 = rightmost). */
    static float iconX(float right, float diameter, float step, int n) { return right - diameter / 2 - step * n; }
}
