package school.magiccodex.client.kui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import school.magiccodex.client.kui.Canvas.Face;

/**
 * A Canvas plus what the views need around it: the pointer (in design pixels), the pressed control, named click areas
 * (kept in top-level design pixels even inside scaled groups) and small helpers (fitted text, buttons with
 * hover / pressed / disabled art of identical size).
 */
public final class Pen {
    public final Canvas c;
    public final float mx, my;
    public final String pressed;
    public record Hit(String id, float x, float y, float w, float h) {
        public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();
    private final ArrayList<float[]> stack = new ArrayList<>();
    private float tx, ty, ts = 1;

    public Pen(Canvas c, float mx, float my, String pressed) { this.c = c; this.mx = mx; this.my = my; this.pressed = pressed; }

    public List<Hit> hits() { return hits; }
    public String hitAt(double x, double y) {
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(x, y)) return hits.get(i).id();
        return null;
    }

    // ------------------------------------------------------------------ groups
    public void push(float x, float y, float s) { stack.add(new float[]{tx, ty, ts}); tx += x * ts; ty += y * ts; ts *= s; c.push(x, y, s); }
    public void pop() { float[] p = stack.remove(stack.size() - 1); tx = p[0]; ty = p[1]; ts = p[2]; c.pop(); }

    // ------------------------------------------------------------------ pointer
    /** Pointer over a rectangle given in the current group's coordinates. */
    public boolean over(float x, float y, float w, float h) {
        float ax = tx + x * ts, ay = ty + y * ts;
        return mx >= ax && mx < ax + w * ts && my >= ay && my < ay + h * ts;
    }
    public void hit(String id, float x, float y, float w, float h) { hits.add(new Hit(id, tx + x * ts, ty + y * ts, w * ts, h * ts)); }

    // ------------------------------------------------------------------ drawing helpers
    public void image(String part, float x, float y, float w, float h) { c.image(part, x, y, w, h, 1); }
    /** Part at its own pixel size. */
    public void image(String part, float x, float y) { int[] s = Parts.size(part); c.image(part, x, y, s[0], s[1], 1); }
    public void text(String s, float x, float y, float size, int color, Face f, int align) { if (s != null && !s.isEmpty()) c.text(s, x, y, size, color, f, align); }
    /** Shrinks the size so the text fits in maxWidth. */
    public void fit(String s, float x, float y, float size, float maxWidth, int color, Face f, int align) {
        if (s == null || s.isEmpty()) return;
        float w = c.width(s, size, f);
        c.text(s, x, y, w > maxWidth ? size * maxWidth / w : size, color, f, align);
    }
    public float width(String s, float size, Face f) { return s == null ? 0 : c.width(s, size, f); }
    public void head(UUID id, String skin, float x, float y, float size) { if (id != null) c.head(id, skin == null ? "" : skin, x, y, size); }

    /**
     * Button: base part at its rectangle; hover / pressed / disabled variants ("_hover", "_pressed", "_disabled") when they exist.
     * @return true when the pointer is over it (enabled)
     */
    public boolean button(String id, String part, float x, float y, float w, float h, boolean enabled) {
        boolean over = enabled && over(x, y, w, h);
        String variant = !enabled ? "_disabled" : id.equals(pressed) && over ? "_pressed" : over ? "_hover" : "";
        String use = !variant.isEmpty() && Parts.exists(part + variant) ? part + variant : part;
        c.image(use, x, y, w, h, 1);
        if (enabled) hit(id, x, y, w, h);
        return over;
    }
}
