package school.magiccodex.client;

/**
 * 아공간 배낭 inventory layout (2026-10-24). No Minecraft types: placement, animation and hit tests only.
 *
 * Design canvas 1932x1087 (16:9). Slot sizes, spacing and the crafting panel's contents are the owner's reference
 * picture's (bag + crafting panel open, 1672x941), shifted onto this canvas. The galaxy band of bag_galaxy_border.png
 * is thicker than in that picture, so the bag PNG is drawn larger (scale 0.8, found from the PNG's alpha interior so
 * every ring, number and the pager stay inside the dark area; the two round buttons sit on the band, top right).
 * The bag sits top-centre; when the crafting panel opens the bag slides left and the panel slides in on its right so
 * the pair is centred, as in the reference. Both borders keep their PNG aspect ratio (uniform scale, no nine-slice).
 */
final class GalaxyBagLayout {
    private GalaxyBagLayout() {}

    static final int CANVAS_W = 1932, CANVAS_H = 1087;

    // ---- bag border (bag_galaxy_border.png 1817x866, visible 13..1804 x 33..842) placed on the reference bag
    static final int BAG_PNG_W = 1817, BAG_PNG_H = 866;
    static final float BAG_SCALE = 0.8f, BAG_X = 9.6f, BAG_Y = -6f;
    static final float BAG_W = BAG_PNG_W * BAG_SCALE, BAG_H = BAG_PNG_H * BAG_SCALE;
    /** Visible outline of the bag on the canvas (PNG visible bbox at this scale). */
    static final float BAG_LEFT = 20, BAG_RIGHT = 1452.8f, BAG_TOP = 20.4f, BAG_BOTTOM = 667.6f;
    /** Shift that centres the bag alone (crafting closed): canvas centre minus the bag's visible centre. */
    static final float BAG_ALONE_SHIFT = CANVAS_W / 2f - (BAG_LEFT + BAG_RIGHT) / 2f;

    // ---- crafting panel (craft_panel_border.png 1108x1420, visible 34..1075 x 43..1380)
    static final int PANEL_PNG_W = 1108, PANEL_PNG_H = 1420;
    static final float PANEL_SCALE = 0.43f, PANEL_X = 1452.4f, PANEL_Y = 36.5f;
    static final float PANEL_W = PANEL_PNG_W * PANEL_SCALE, PANEL_H = PANEL_PNG_H * PANEL_SCALE;
    static final float PANEL_LEFT = 1467, PANEL_RIGHT = 1912, PANEL_TOP = 55, PANEL_BOTTOM = 633;
    /** Panel enters from the right by this much while it fades in. */
    static final float PANEL_SLIDE = 48;
    static final float OPEN_SECONDS = 0.24f;

    // ---- bag contents
    static final float MAIN_X0 = 346, MAIN_STEP_X = 97.6f, MAIN_Y0 = 195.5f, MAIN_STEP_Y = 87.75f, MAIN_R = 39, MAIN_ITEM = 62;
    static final float HOTBAR_X0 = 384.5f, HOTBAR_STEP = 89f, HOTBAR_Y = 476, HOTBAR_R = 35, HOTBAR_ITEM = 54, HOTBAR_NUMBER_Y = 526;
    static final float DIVIDER_Y = 424.5f, DIVIDER_X0 = 306, DIVIDER_X1 = 1174.5f;
    static final float PAGER_Y = 571.5f, PAGER_PREV_X = 644.5f, PAGER_NEXT_X = 825.5f, PAGER_LABEL_X = 735, PAGER_R = 22.5f;
    static final float EQUIP_X = 1201.6f, EQUIP_Y = 106, CRAFT_BTN_X = 1257.6f, CRAFT_BTN_Y = 150, ROUND_BTN_R = 29;
    // locked page (owner's lock reference, rescaled to this bag's row spacing)
    static final float LOCK_TITLE_Y = 108, LOCK_ICON_Y = 251, LOCK_ICON = 120, LOCK_TEXT_Y = 332, LOCK_HINT_Y = 363;

    // ---- crafting panel contents
    static final float CRAFT_TITLE_X = 1532, CRAFT_TITLE_Y = 103, BOOK_X = 1643, BOOK_Y = 99, BOOK_SIZE = 52;
    static final float CLOSE_X = 1861, CLOSE_Y = 103, CLOSE_R = 23;
    static final float TITLE_RULE_Y = 130, TITLE_RULE_X0 = 1499, TITLE_RULE_X1 = 1607;
    static final float[][] INPUTS = {{1555, 180}, {1640, 180}, {1555, 265}, {1640, 265}};
    static final float INPUT_R = 37.5f, INPUT_ITEM = 52;
    static final float RESULT_X = 1824, RESULT_Y = 220, RESULT_R = 51, RESULT_ITEM = 70;
    static final float ARROW_X = 1726, ARROW_Y = 220, ARROW_LEN = 55;
    static final float SECTION_RULE_Y = 318, SECTION_RULE_X0 = 1499, SECTION_RULE_X1 = 1887;
    static final float RECIPE_TITLE_X = 1534, RECIPE_TITLE_Y = 355;
    static final float SEARCH_X0 = 1597, SEARCH_X1 = 1747, SEARCH_Y0 = 335, SEARCH_Y1 = 370;
    static final float CRAFTABLE_LABEL_X = 1797, CRAFTABLE_LABEL_Y = 354;
    static final float TOGGLE_X0 = 1846, TOGGLE_X1 = 1892, TOGGLE_Y0 = 343, TOGGLE_Y1 = 367;
    static final float[] CARD_X = {1509, 1637, 1764}, CARD_Y = {389, 490};
    static final float CARD_W = 116, CARD_H = 88, CARD_ITEM = 58;
    static final int CARDS_PER_PAGE = 6;
    static final float RECIPE_PAGER_Y = 603, RECIPE_PREV_X = 1627, RECIPE_NEXT_X = 1757, RECIPE_LABEL_X = 1692, RECIPE_PAGER_R = 17;

    // ---- PlayerScreenHandler slot ids
    static final int RESULT_SLOT = 0, INPUT_START = 1, MAIN_START = 9, HOTBAR_START = 36;

    enum Kind { RESULT, INPUT, MAIN, HOTBAR }
    record Element(int slotId, Kind kind, float cx, float cy, float radius, float itemSize) {
        boolean inPanel() { return kind == Kind.RESULT || kind == Kind.INPUT; }
    }

    private static final Element[] BY_SLOT = new Element[46];
    static {
        BY_SLOT[RESULT_SLOT] = new Element(RESULT_SLOT, Kind.RESULT, RESULT_X, RESULT_Y, RESULT_R, RESULT_ITEM);
        for (int i = 0; i < 4; i++) BY_SLOT[INPUT_START + i] = new Element(INPUT_START + i, Kind.INPUT, INPUTS[i][0], INPUTS[i][1], INPUT_R, INPUT_ITEM);
        for (int i = 0; i < 27; i++)
            BY_SLOT[MAIN_START + i] = new Element(MAIN_START + i, Kind.MAIN, MAIN_X0 + (i % 9) * MAIN_STEP_X, MAIN_Y0 + (i / 9) * MAIN_STEP_Y, MAIN_R, MAIN_ITEM);
        for (int i = 0; i < 9; i++) BY_SLOT[HOTBAR_START + i] = new Element(HOTBAR_START + i, Kind.HOTBAR, HOTBAR_X0 + i * HOTBAR_STEP, HOTBAR_Y, HOTBAR_R, HOTBAR_ITEM);
    }

    /** Element for a handler slot id, or null (armour 5..8 and offhand 45 are not shown). */
    static Element element(int slotId) { return slotId >= 0 && slotId < BY_SLOT.length ? BY_SLOT[slotId] : null; }

    static java.util.List<Element> elements() {
        var out = new java.util.ArrayList<Element>();
        for (Element e : BY_SLOT) if (e != null) out.add(e);
        return out;
    }

    // ---- window fit: whole canvas, uniform, top aligned (the bag is a top-centre window)

    record Fit(float x, float y, float scale) {
        double localX(double screenX) { return (screenX - x) / scale; }
        double localY(double screenY) { return (screenY - y) / scale; }
    }

    static Fit fit(int width, int height) {
        float scale = Math.min((float) width / CANVAS_W, (float) height / CANVAS_H);
        if (!(scale > 0)) scale = 1;
        return new Fit((width - CANVAS_W * scale) / 2f, 0, scale);
    }

    // ---- crafting panel animation

    static float approach(float open, boolean wanted, float dt) {
        float step = dt / OPEN_SECONDS;
        return wanted ? Math.min(1, open + step) : Math.max(0, open - step);
    }

    /** Smooth step 0..1. */
    static float ease(float t) { t = Math.clamp(t, 0f, 1f); return t * t * (3 - 2 * t); }

    /** Horizontal offset of everything in the bag for panel progress t (0 closed .. 1 open). */
    static float bagShift(float t) { return BAG_ALONE_SHIFT * (1 - ease(t)); }

    /** Horizontal offset of the panel (slides in from the right). */
    static float panelShift(float t) { return PANEL_SLIDE * (1 - ease(t)); }

    /** Panel slots take clicks only once the panel is mostly in place. */
    static boolean panelInteractive(float t) { return t > 0.85f; }

    static float shiftOf(Element e, float t) { return e.inPanel() ? panelShift(t) : bagShift(t); }

    // ---- hit tests (canvas coordinates; mx, my already converted with Fit)

    static boolean circle(double mx, double my, float cx, float cy, float r) {
        double dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    static boolean rect(double mx, double my, float x0, float y0, float x1, float y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** Slot hit: a slightly generous circle so the gaps between rings are not dead zones on small windows. */
    static boolean hit(Element e, double mx, double my, float t) {
        return circle(mx, my, e.cx() + shiftOf(e, t), e.cy(), e.radius() + 4);
    }

    /** Inside the bag outline (rounded box of the visible border, a little inside the glow). */
    static boolean insideBag(double mx, double my, float t) {
        float s = bagShift(t);
        return roundedBox(mx - s, my, BAG_LEFT + 10, BAG_TOP + 10, BAG_RIGHT - 10, BAG_BOTTOM - 10, 150);
    }

    static boolean insidePanel(double mx, double my, float t) {
        if (t <= 0.01f) return false;
        float s = panelShift(t);
        return roundedBox(mx - s, my, PANEL_LEFT, PANEL_TOP, PANEL_RIGHT, PANEL_BOTTOM, 40);
    }

    static boolean roundedBox(double x, double y, float x0, float y0, float x1, float y1, float r) {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false;
        double cx = Math.clamp(x, x0 + r, x1 - r), cy = Math.clamp(y, y0 + r, y1 - r);
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    /** Recipe card index 0..5 under the point, or -1. */
    static int card(double mx, double my, float t) {
        float s = panelShift(t);
        for (int i = 0; i < CARDS_PER_PAGE; i++) {
            float x = CARD_X[i % 3] + s, y = CARD_Y[i / 3];
            if (rect(mx, my, x, y, x + CARD_W, y + CARD_H)) return i;
        }
        return -1;
    }

    /** "1 / 3" style pager text. */
    static String pager(int page, int pages) { return page + " / " + Math.max(1, pages); }

    /** Number of pages for n recipes (at least 1). */
    static int recipePages(int n) { return Math.max(1, (n + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE); }

    /** Vanilla count text: nothing for a single item. */
    static String count(int n) { return n <= 1 ? "" : Integer.toString(n); }
}
