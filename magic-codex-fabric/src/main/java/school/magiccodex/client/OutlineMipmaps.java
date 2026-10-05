package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Mip chain for hand-drawn item icons larger than 32px.
 * Plain averaging thins a 1px outline to a fraction of a screen pixel and gradients alias when
 * vanilla skips texels. Here each level averages only the inner colours, keeps the silhouette
 * binary, and redraws a one-texel outline in the averaged outline colour of that area. The source
 * PNG is never changed; this runs once per texture on a worker thread.
 */
public final class OutlineMipmaps {
    private OutlineMipmaps() {}

    /** Coarse texels at least this covered by the source shape stay part of the silhouette. */
    static final double COVERAGE = 0.4;
    private static final int OPAQUE = 128;

    /** @return level 0 (the source) down to 1x1, straight (non-premultiplied) ARGB. */
    public static List<int[]> build(int[] argb, int width, int height) {
        if (argb.length != width * height || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Pixel count does not match " + width + "x" + height);
        boolean[] outline = outline(argb, width, height);
        int[] outlineAverage = averageOutline(argb, outline);
        List<int[]> levels = new ArrayList<>();
        levels.add(argb.clone());
        int lw = width, lh = height;
        while (lw > 1 || lh > 1) {
            lw = Math.max(1, lw >> 1);
            lh = Math.max(1, lh >> 1);
            levels.add(level(argb, outline, outlineAverage, width, height, lw, lh));
        }
        return levels;
    }

    public static int levelCount(int width, int height) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    /** Opaque pixels that touch transparency or the image edge on one of four sides. */
    static boolean[] outline(int[] argb, int w, int h) {
        boolean[] out = new boolean[argb.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!opaque(argb[y * w + x])) continue;
            out[y * w + x] = !solid(argb, w, h, x + 1, y) || !solid(argb, w, h, x - 1, y)
                    || !solid(argb, w, h, x, y + 1) || !solid(argb, w, h, x, y - 1);
        }
        return out;
    }

    private static int[] level(int[] src, boolean[] edge, int[] fallbackOutline, int w, int h, int lw, int lh) {
        boolean[] inside = new boolean[lw * lh];
        int[] fill = new int[lw * lh];
        for (int cy = 0; cy < lh; cy++) for (int cx = 0; cx < lw; cx++) {
            int x0 = cx * w / lw, x1 = Math.max(x0 + 1, (cx + 1) * w / lw);
            int y0 = cy * h / lh, y1 = Math.max(y0 + 1, (cy + 1) * h / lh);
            long[] inner = new long[4], any = new long[4];
            int covered = 0, innerCount = 0, area = (x1 - x0) * (y1 - y0);
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int p = src[y * w + x];
                if (!opaque(p)) continue;
                covered++;
                add(any, p);
                if (!edge[y * w + x]) { add(inner, p); innerCount++; }
            }
            int i = cy * lw + cx;
            inside[i] = covered >= COVERAGE * area;
            if (inside[i]) fill[i] = innerCount > 0 ? average(inner, innerCount) : average(any, covered);
        }
        int[] out = new int[lw * lh];
        for (int cy = 0; cy < lh; cy++) for (int cx = 0; cx < lw; cx++) {
            int i = cy * lw + cx;
            if (!inside[i]) continue;
            boolean border = !in(inside, lw, lh, cx + 1, cy) || !in(inside, lw, lh, cx - 1, cy)
                    || !in(inside, lw, lh, cx, cy + 1) || !in(inside, lw, lh, cx, cy - 1);
            out[i] = border ? outlineColour(src, edge, fallbackOutline, w, h, lw, lh, cx, cy) : fill[i];
        }
        return out;
    }

    /** Outline colour of the matching source area (one block of margin), so local hue shifts survive. */
    private static int outlineColour(int[] src, boolean[] edge, int[] fallback, int w, int h, int lw, int lh, int cx, int cy) {
        int x0 = Math.max(0, (cx - 1) * w / lw), x1 = Math.min(w, (cx + 2) * w / lw);
        int y0 = Math.max(0, (cy - 1) * h / lh), y1 = Math.min(h, (cy + 2) * h / lh);
        long[] sum = new long[4];
        int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++)
            if (edge[y * w + x]) { add(sum, src[y * w + x]); n++; }
        if (n > 0) return average(sum, n);
        return fallback[0] > 0 ? average(new long[]{fallback[1], fallback[2], fallback[3], fallback[4]}, fallback[0]) : 0xFF000000;
    }

    private static int[] averageOutline(int[] src, boolean[] edge) {
        long[] sum = new long[4];
        int n = 0;
        for (int i = 0; i < src.length; i++) if (edge[i]) { add(sum, src[i]); n++; }
        return new int[]{n, (int) sum[0], (int) sum[1], (int) sum[2], (int) sum[3]};
    }

    private static void add(long[] sum, int p) {
        sum[0] += p >>> 24; sum[1] += (p >>> 16) & 255; sum[2] += (p >>> 8) & 255; sum[3] += p & 255;
    }
    /** Silhouette texels are written fully opaque; edges are softened by GPU sampling, not by alpha. */
    private static int average(long[] sum, int n) {
        int r = (int) ((sum[1] + n / 2) / n), g = (int) ((sum[2] + n / 2) / n), b = (int) ((sum[3] + n / 2) / n);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    private static boolean opaque(int p) { return p >>> 24 >= OPAQUE; }
    private static boolean solid(int[] a, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && opaque(a[y * w + x]);
    }
    private static boolean in(boolean[] a, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && a[y * w + x];
    }
}
