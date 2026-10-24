package school.magiccodex.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalaxyBagLayoutTest {
    @Test void everyShownSlotHasOneElementAndArmourIsHidden() {
        assertEquals(1 + 4 + 27 + 9, GalaxyBagLayout.elements().size());
        for (int armour = 5; armour <= 8; armour++) assertNull(GalaxyBagLayout.element(armour));
        assertNull(GalaxyBagLayout.element(45));
        assertEquals(GalaxyBagLayout.Kind.HOTBAR, GalaxyBagLayout.element(36).kind());
        assertEquals(GalaxyBagLayout.Kind.MAIN, GalaxyBagLayout.element(35).kind());
    }

    @Test void slotRingsNeverOverlapSoOneClickIsOneSlot() {
        var all = GalaxyBagLayout.elements();
        for (var a : all) for (var b : all) {
            if (a == b || a.inPanel() != b.inPanel()) continue;
            double d = Math.hypot(a.cx() - b.cx(), a.cy() - b.cy());
            assertTrue(d > a.radius() + b.radius() + 8, a.slotId() + " vs " + b.slotId());   // hit circles are radius+4
        }
    }

    @Test void bagAloneIsCentredAndPairIsCentredWhenPanelOpens() {
        float closedCentre = (GalaxyBagLayout.BAG_LEFT + GalaxyBagLayout.BAG_RIGHT) / 2 + GalaxyBagLayout.bagShift(0);
        assertEquals(GalaxyBagLayout.CANVAS_W / 2f, closedCentre, 0.01);
        float pairCentre = (GalaxyBagLayout.BAG_LEFT + GalaxyBagLayout.PANEL_RIGHT) / 2 + GalaxyBagLayout.bagShift(1);
        assertEquals(GalaxyBagLayout.CANVAS_W / 2f, pairCentre, 4);
        assertEquals(0, GalaxyBagLayout.panelShift(1), 1e-6);
        assertTrue(GalaxyBagLayout.PANEL_LEFT > GalaxyBagLayout.BAG_RIGHT, "panel never covers the bag");
    }

    @Test void bordersLieOnTheReferenceOutlines() {
        // PNG visible boxes (bag 13..1804 x 33..842, panel 34..1075 x 43..1380) land on the reference outlines.
        assertEquals(45, GalaxyBagLayout.BAG_X + 13 * GalaxyBagLayout.BAG_SX, 0.01);
        assertEquals(1182, GalaxyBagLayout.BAG_X + 1804 * GalaxyBagLayout.BAG_SX, 0.01);
        assertEquals(580, GalaxyBagLayout.BAG_Y + 842 * GalaxyBagLayout.BAG_SY, 0.01);
        assertEquals(1630, GalaxyBagLayout.PANEL_X + 1075 * GalaxyBagLayout.PANEL_SX, 0.01);
        assertEquals(630, GalaxyBagLayout.PANEL_Y + 1380 * GalaxyBagLayout.PANEL_SY, 0.01);
    }

    @Test void fitIsUniformTopAlignedAndInsideTheWindow() {
        for (int[] s : new int[][]{{960, 540}, {640, 360}, {427, 240}, {800, 600}, {1280, 400}}) {
            var f = GalaxyBagLayout.fit(s[0], s[1]);
            assertEquals(0, f.y(), 1e-6);
            assertTrue(f.x() >= -0.01 && f.x() + GalaxyBagLayout.CANVAS_W * f.scale() <= s[0] + 0.01);
            assertTrue(GalaxyBagLayout.CANVAS_H * f.scale() <= s[1] + 0.01);
        }
    }

    @Test void panelAnimationIsClampedAndSmooth() {
        float t = 0;
        for (int i = 0; i < 100; i++) t = GalaxyBagLayout.approach(t, true, 0.016f);
        assertEquals(1, t, 1e-6);
        assertEquals(0.5f, GalaxyBagLayout.ease(0.5f), 1e-6);
        assertFalse(GalaxyBagLayout.panelInteractive(0.5f));
        assertTrue(GalaxyBagLayout.panelInteractive(1));
    }

    @Test void hitTestsFollowTheSlide() {
        var e = GalaxyBagLayout.element(9);
        assertTrue(GalaxyBagLayout.hit(e, e.cx() + GalaxyBagLayout.bagShift(0), e.cy(), 0));
        assertFalse(GalaxyBagLayout.hit(e, e.cx(), e.cy(), 0));
        assertTrue(GalaxyBagLayout.insideBag(GalaxyBagLayout.CANVAS_W / 2.0, 300, 0));
        assertFalse(GalaxyBagLayout.insideBag(10, 900, 0));
        assertFalse(GalaxyBagLayout.insidePanel(1400, 300, 0));
        assertTrue(GalaxyBagLayout.insidePanel(1400, 300, 1));
        assertEquals(0, GalaxyBagLayout.card(GalaxyBagLayout.CARD_X[0] + 5, GalaxyBagLayout.CARD_Y[0] + 5, 1));
        assertEquals(5, GalaxyBagLayout.card(GalaxyBagLayout.CARD_X[2] + 5, GalaxyBagLayout.CARD_Y[1] + 5, 1));
        assertEquals(-1, GalaxyBagLayout.card(GalaxyBagLayout.CARD_X[0] - 6, GalaxyBagLayout.CARD_Y[0], 1));
    }

    @Test void pagerTextAndRecipePages() {
        assertEquals("1 / 3", GalaxyBagLayout.pager(1, 3));
        assertEquals(1, GalaxyBagLayout.recipePages(0));
        assertEquals(1, GalaxyBagLayout.recipePages(6));
        assertEquals(2, GalaxyBagLayout.recipePages(7));
        assertEquals("", GalaxyBagLayout.count(1));
        assertEquals("64", GalaxyBagLayout.count(64));
    }
}
