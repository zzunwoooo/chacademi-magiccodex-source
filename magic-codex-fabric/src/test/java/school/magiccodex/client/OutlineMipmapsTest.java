package school.magiccodex.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OutlineMipmapsTest {
    private static final int FILL = 0xFF8040C0, LINE = 0xFF301050, CLEAR = 0;

    /** size x size image: transparent margin, 1px outline ring, filled inside. */
    private static int[] framedSquare(int size, int margin) {
        int[] p = new int[size * size];
        for (int y = margin; y < size - margin; y++) for (int x = margin; x < size - margin; x++) {
            boolean edge = x == margin || y == margin || x == size - margin - 1 || y == size - margin - 1;
            p[y * size + x] = edge ? LINE : FILL;
        }
        return p;
    }

    @Test void buildsFullChainAndKeepsSourceUntouched() {
        int[] src = framedSquare(128, 4);
        int[] copy = src.clone();
        List<int[]> levels = OutlineMipmaps.build(src, 128, 128);
        assertEquals(8, levels.size());
        assertEquals(OutlineMipmaps.levelCount(128, 128), levels.size());
        assertArrayEquals(copy, src);
        assertArrayEquals(src, levels.get(0));
        int size = 128;
        for (int[] level : levels) { assertEquals(size * size, level.length); size = Math.max(1, size / 2); }
    }

    @Test void everyLevelKeepsAnOpaqueOneTexelOutlineAndCleanFill() {
        List<int[]> levels = OutlineMipmaps.build(framedSquare(128, 4), 128, 128);
        for (int l = 1; l <= 4; l++) {               // 64, 32, 16, 8
            int n = 128 >> l, m = 4 >> l;           // margin shrinks with the level
            int[] p = levels.get(l);
            int mid = n / 2;
            assertEquals(LINE, p[mid * n + m], "left outline at level " + l);
            assertEquals(LINE, p[m * n + mid], "top outline at level " + l);
            assertEquals(FILL, p[mid * n + m + 1], "fill must not be darkened by the outline at level " + l);
            assertEquals(FILL, p[mid * n + mid]);
            if (m > 0) assertEquals(CLEAR, p[mid * n], "margin stays transparent at level " + l);
            for (int v : p) assertTrue(v >>> 24 == 0 || v >>> 24 == 255, "no half-transparent texels");
        }
    }

    @Test void plainBoxFilterWouldFadeTheOutlineButThisDoesNot() {
        int[] p = OutlineMipmaps.build(framedSquare(128, 4), 128, 128).get(2); // 32x32
        // A 4x4 box average around a 1px outline would be 1/4 outline colour; here it stays exact.
        assertEquals(LINE, p[16 * 32 + 1]);
    }

    @Test void gradientsAreAveragedNotSkipped() {
        int w = 64, h = 64;
        int[] src = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
            src[y * w + x] = edge ? LINE : 0xFF000000 | (x * 4) << 16;
        }
        int[] half = OutlineMipmaps.build(src, w, h).get(1);
        int r = (half[10 * 32 + 10] >>> 16) & 255;
        assertEquals((20 * 4 + 21 * 4) / 2, r, 1, "2x2 average of neighbouring red values");
    }

    @Test void localOutlineHueIsKept() {
        int[] src = framedSquare(64, 2);
        for (int y = 2; y < 32; y++) src[y * 64 + 2] = 0xFF105030; // greenish outline on the upper left edge
        int[] p = OutlineMipmaps.build(src, 64, 64).get(1);
        assertEquals(0xFF105030, p[5 * 32 + 1]);
        assertEquals(LINE, p[27 * 32 + 1]);
    }

    @Test void rejectsMismatchedSize() {
        assertThrows(IllegalArgumentException.class, () -> OutlineMipmaps.build(new int[10], 4, 4));
    }

    @Test void nonSquareAndOddSizesReachOneByOne() {
        var levels = OutlineMipmaps.build(framedSquare(48, 2), 48, 48);
        assertEquals(6, levels.size());
        assertEquals(1, levels.get(levels.size() - 1).length);
        var wide = OutlineMipmaps.build(new int[96 * 40], 96, 40);
        assertEquals(1, wide.get(wide.size() - 1).length);
    }
}
