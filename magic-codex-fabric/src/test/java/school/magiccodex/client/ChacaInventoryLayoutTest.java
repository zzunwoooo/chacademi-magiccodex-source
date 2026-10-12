package school.magiccodex.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import school.magiccodex.client.ChacaInventoryLayout.Element;
import school.magiccodex.client.ChacaInventoryLayout.Kind;
import school.magiccodex.client.ChacaInventoryLayout.Mode;

class ChacaInventoryLayoutTest {
    private static final double[] TIMES = {0, 0.37, 1.1, 2.5, 3.9, 7.3, 12.8, 101.6};

    @Test void showsResultFourInputsTwentySevenPocketsAndNineHotbarSlots() {
        for (int id = 0; id <= 45; id++) {
            boolean shown = id <= 4 || (id >= 9 && id <= 44);
            assertEquals(shown, ChacaInventoryLayout.element(id) != null, "slot " + id);
            if (shown) assertEquals(id, ChacaInventoryLayout.element(id).slotId());
        }
        assertEquals(1 + 4 + 27 + 9, ChacaInventoryLayout.elements().length);
        assertEquals(27, Arrays.stream(ChacaInventoryLayout.elements()).filter(e -> e.kind() == Kind.MAIN).count());
        assertEquals(9, Arrays.stream(ChacaInventoryLayout.elements()).filter(e -> e.kind() == Kind.HOTBAR).count());
    }

    @Test void armorAndOffhandAreNeverHitButTheirItemsAreNotTouched() {
        for (int id : new int[]{5, 6, 7, 8, 45}) assertNull(ChacaInventoryLayout.element(id));
    }

    @Test void craftingOrbsKeepTheVanillaTwoByTwoOrder() {
        var tl = ChacaInventoryLayout.element(1); var tr = ChacaInventoryLayout.element(2);
        var bl = ChacaInventoryLayout.element(3); var br = ChacaInventoryLayout.element(4);
        assertTrue(tl.cx() < tr.cx() && bl.cx() < br.cx(), "left column before right column");
        assertTrue(tl.cy() < bl.cy() && tr.cy() < br.cy(), "top row before bottom row");
        var result = ChacaInventoryLayout.element(0);
        assertTrue(tl.cx() < result.cx() && result.cx() < tr.cx());
    }

    @Test void mainRowsAndHotbarFollowInventoryOrder() {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            var e = ChacaInventoryLayout.element(9 + row * 9 + col);
            if (col > 0) assertTrue(e.cx() > ChacaInventoryLayout.element(9 + row * 9 + col - 1).cx());
            if (row > 0) assertTrue(e.cy() > ChacaInventoryLayout.element(9 + (row - 1) * 9 + col).cy());
        }
        for (int col = 0; col < 9; col++)
            assertTrue(ChacaInventoryLayout.element(36 + col).cy() > ChacaInventoryLayout.element(27 + col).cy(), "hotbar below pockets");
    }

    @Test void clickTargetsNeverOverlapWhileFloating() {
        for (double t : TIMES) for (float calm : new float[]{0, 0.5f, 1}) {
            for (int y = 0; y < ChacaInventoryLayout.HEIGHT; y += 3) for (int x = 0; x < ChacaInventoryLayout.WIDTH; x += 3) {
                int hits = 0;
                for (Element e : ChacaInventoryLayout.elements()) if (ChacaInventoryLayout.hit(e, x, y, t, calm)) hits++;
                assertTrue(hits <= 1, "overlap at " + x + "," + y + " t=" + t);
                if (hits == 1) assertFalse(ChacaInventoryLayout.bookHit(x, y), "book button overlaps a slot at " + x + "," + y);
            }
        }
    }

    @Test void theDrawnPositionIsTheClickedPosition() {
        for (Element e : ChacaInventoryLayout.elements()) for (double t : TIMES) {
            float y = ChacaInventoryLayout.centerY(e, t, 1);
            assertTrue(ChacaInventoryLayout.hit(e, e.cx(), y, t, 1), "item centre must be clickable: " + e.slotId());
            assertTrue(Math.abs(y - e.cy()) <= ChacaInventoryLayout.ORB_AMPLITUDE + 1e-3, "float stays small");
        }
        // Only the crafting orbs float; pockets and hotbar stay still.
        for (double t : TIMES) {
            assertEquals(0, ChacaInventoryLayout.bob(20, t, 1), 1e-9);
            assertEquals(0, ChacaInventoryLayout.bob(40, t, 1), 1e-9);
        }
    }

    @Test void orbsUseDifferentPeriodsAndStopWhileCarrying() {
        float a = ChacaInventoryLayout.bob(1, 1.3, 1), b = ChacaInventoryLayout.bob(2, 1.3, 1), c = ChacaInventoryLayout.bob(3, 1.3, 1);
        assertFalse(a == b && b == c, "orbs should not move in lockstep");
        for (double t : TIMES) for (int id = 0; id <= 4; id++) assertEquals(0, ChacaInventoryLayout.bob(id, t, 0), 1e-9);
        float calm = 1;
        for (int i = 0; i < 10; i++) calm = ChacaInventoryLayout.approachCalm(calm, true, 0.05f);
        assertEquals(0, calm, 1e-6, "settles within half a second");
        assertEquals(0.2f, ChacaInventoryLayout.approachCalm(0, false, 0.05f), 1e-6);
        assertEquals(1, ChacaInventoryLayout.approachCalm(1, false, 5), 1e-6);
    }

    @Test void everySlotSitsInsideTheContentAreaSoItIsNeverAThrowClick() {
        for (Element e : ChacaInventoryLayout.elements()) for (double t : TIMES) {
            float y = ChacaInventoryLayout.centerY(e, t, 1);
            assertTrue(ChacaInventoryLayout.insideContent(e.cx(), y), "slot " + e.slotId());
        }
        assertTrue(ChacaInventoryLayout.insideContent(ChacaInventoryLayout.BOOK_X, ChacaInventoryLayout.BOOK_Y));
        assertFalse(ChacaInventoryLayout.insideContent(40, 40), "empty corner throws like outside the vanilla panel");
    }

    @Test void layoutFitsCommonGuiSizesWithItemsAtLeastVanillaSize() {
        // Scaled GUI sizes: 1920x1080 at GUI 2/3/4, 2560x1440, 1366x768, 1280x720, 854x480, 4K, 16:10, 4:3.
        int[][] sizes = {{960, 540}, {640, 360}, {480, 270}, {640, 360}, {683, 384}, {426, 240}, {427, 240}, {960, 540}, {720, 450}, {512, 384}, {1920, 1080}};
        for (int[] s : sizes) {
            var fit = ChacaInventoryLayout.fit(s[0], s[1], false);
            assertEquals(Mode.FULL, fit.mode());
            assertTrue(fit.x() >= 0 && fit.y() >= 0, Arrays.toString(s));
            assertTrue(fit.screenX(ChacaInventoryLayout.WIDTH) <= s[0] + 0.01 && fit.screenY(ChacaInventoryLayout.HEIGHT) <= s[1] + 0.01, Arrays.toString(s));
            assertTrue(ChacaInventoryLayout.MAIN_ITEM * fit.scale() >= 16, "pocket item below 16 GUI px at " + Arrays.toString(s));
            assertEquals(200, fit.localX(fit.screenX(200)), 1e-3);
        }
    }

    @Test void openRecipeBookKeepsTheCraftingClusterReadableBesideIt() {
        // Auto GUI scale sizes: 1080p (480x270), 1440p (640x360 / 426x240 at 6), 900p, 720p, GUI 2 at 1080p.
        int[][] sizes = {{480, 270}, {640, 360}, {426, 240}, {480, 300}, {427, 240}, {960, 540}};
        for (int[] s : sizes) {
            var side = ChacaInventoryLayout.fit(s[0], s[1], true);
            assertEquals(Mode.SIDE, side.mode(), Arrays.toString(s));
            int bookRight = ChacaInventoryLayout.bookLeft(s[0], false) + ChacaInventoryLayout.BOOK_W;
            float[] r = ChacaInventoryLayout.CRAFT_RECT;
            assertTrue(side.screenX(r[0]) >= bookRight, "crafting must start right of the vanilla book " + Arrays.toString(s));
            assertTrue(side.screenX(r[2]) <= s[0] + 0.01 && side.screenY(r[1]) >= -0.01 && side.screenY(r[3]) <= s[1] + 0.01, Arrays.toString(s));
            assertTrue(ChacaInventoryLayout.ORB_ITEM * side.scale() >= 16, "orb items stay at least vanilla size " + Arrays.toString(s));
        }
        assertEquals(Mode.BOOK_ONLY, ChacaInventoryLayout.fit(370, 240, true).mode(), "vanilla narrow mode");
    }

    @Test void recipeBookModeShowsOnlyCraftingSlots() {
        for (Element e : ChacaInventoryLayout.elements()) {
            boolean crafting = e.kind() == Kind.RESULT || e.kind() == Kind.INPUT;
            assertTrue(ChacaInventoryLayout.visible(e, Mode.FULL));
            assertEquals(crafting, ChacaInventoryLayout.visible(e, Mode.SIDE), "slot " + e.slotId());
            assertFalse(ChacaInventoryLayout.visible(e, Mode.BOOK_ONLY));
            if (crafting) assertTrue(ChacaInventoryLayout.insideContent(e.cx(), e.cy(), Mode.SIDE));
        }
        assertFalse(ChacaInventoryLayout.insideContent(836, 700, Mode.SIDE), "hidden pocket area is outside while the book is open");
        assertTrue(ChacaInventoryLayout.insideContent(5, 5, Mode.BOOK_ONLY), "never throw while the book covers the screen");
    }

    @Test void countsMatchVanillaVisibility() {
        assertEquals("", ChacaInventoryLayout.count(1));
        assertEquals("", ChacaInventoryLayout.count(0));
        assertEquals("16", ChacaInventoryLayout.count(16));
        assertEquals("64", ChacaInventoryLayout.count(64));
    }

    @Test void shippedArtMatchesDeclaredSizesAndHasTransparentMargins() throws Exception {
        String[] files = {"crafting_orb", "crafting_crystal", "inventory_pedestal", "recipe_book"};
        int[][] sizes = {ChacaInventoryLayout.ORB_PNG, ChacaInventoryLayout.CRYSTAL_PNG, ChacaInventoryLayout.PEDESTAL_PNG, ChacaInventoryLayout.BOOK_PNG};
        for (int i = 0; i < files.length; i++) {
            try (var stream = getClass().getResourceAsStream("/assets/magiccodex/textures/gui/inventory/" + files[i] + ".png")) {
                assertNotNull(stream, files[i]);
                var image = ImageIO.read(stream);
                assertEquals(sizes[i][0], image.getWidth(), files[i]);
                assertEquals(sizes[i][1], image.getHeight(), files[i]);
                assertTrue(image.getColorModel().hasAlpha(), files[i]);
                // Text, items and counts are drawn at runtime: corners must be clear, not a baked backdrop.
                for (int[] p : new int[][]{{0, 0}, {image.getWidth() - 1, 0}, {0, image.getHeight() - 1}, {image.getWidth() - 1, image.getHeight() - 1}})
                    assertTrue((image.getRGB(p[0], p[1]) >>> 24) <= 8, files[i] + " corner is not transparent");
            }
        }
    }
}
