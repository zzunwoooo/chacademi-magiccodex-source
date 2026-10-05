package school.magiccodex.client;

/** Coordinates are measured on the unmodified PNG, including its transparent margins. */
public record CodexLayout(float x, float y, float scale) {
    public static final int WIDTH = 1672;
    public static final int HEIGHT = 941;

    public double localX(double mouseX) { return (mouseX - x) / scale; }
    public double localY(double mouseY) { return (mouseY - y) / scale; }

    public static CodexLayout fit(int viewportWidth, int viewportHeight) {
        return fit(viewportWidth, viewportHeight, 1f);
    }

    /** Keep room around the codex while the keyboard retains its original size. */
    public static CodexLayout codexFit(int viewportWidth, int viewportHeight) {
        return fit(viewportWidth, viewportHeight, 0.85f);
    }

    private static CodexLayout fit(int viewportWidth, int viewportHeight, float fraction) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        float scale = Math.min((float) viewportWidth / WIDTH, (float) viewportHeight / HEIGHT) * fraction;
        return new CodexLayout((viewportWidth - WIDTH * scale) / 2,
                (viewportHeight - HEIGHT * scale) / 2, scale);
    }
}
