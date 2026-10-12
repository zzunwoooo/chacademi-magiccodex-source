package school.magiccodex.client;

/**
 * Pure layout for the Chacademi survival inventory, measured on the 1672x941 reference image.
 * Drawing and click testing both read this class, with the same animation time, so a floating
 * orb is always clicked where it is drawn. No Minecraft types: unit-tested directly.
 *
 * Slot ids follow {@code PlayerScreenHandler}: 0 result, 1..4 crafting input (top-left,
 * top-right, bottom-left, bottom-right — the vanilla 2x2 order), 5..8 hidden armor,
 * 9..35 main inventory, 36..44 hotbar and 45 visible offhand.
 */
final class ChacaInventoryLayout {
    private ChacaInventoryLayout() {}

    static final int WIDTH = 1672, HEIGHT = 941;
    static final int RESULT = 0, INPUT_START = 1, INPUT_COUNT = 4, MAIN_START = 9, HOTBAR_START = 36, HOTBAR_END = 44, OFFHAND = 45;

    enum Kind { RESULT, INPUT, MAIN, HOTBAR, OFFHAND }

    /** One slot in reference coordinates. (cx, cy) is the resting item centre. */
    record Element(int slotId, Kind kind, float cx, float cy, float itemSize, float hitW, float hitH, boolean round) {
        /** Pedestal centre (below the item) for main/hotbar slots. */
        float pedestalY() { return cy + (kind == Kind.HOTBAR ? HOTBAR_ITEM_LIFT : MAIN_ITEM_LIFT); }
    }

    /** Pixel sizes of the shipped PNGs (cropped to their visible art); checked by a test. */
    static final int[] ORB_PNG = {582, 640}, CRYSTAL_PNG = {284, 640}, PEDESTAL_PNG = {640, 115}, BOOK_PNG = {320, 321};

    // ------------------------------------------------------------------ reference geometry
    static final float TITLE_Y = 42, CRAFT_LABEL_Y = 106, BOOK_X = 836, BOOK_Y = 420, BOOK_LABEL_Y = 466, DIVIDER_Y = 796;
    static final float CENTER_X = 836, CENTER_Y = 290;
    /** Input orbs: TL, TR, BL, BR (= crafting slots 1..4). */
    static final float[][] ORBS = {{586, 181}, {1086, 181}, {551, 337}, {1121, 337}};
    static final float ORB_SIZE = 150, ORB_HIT_RADIUS = 66, ORB_ITEM = 72;
    static final float RESULT_ORB_SIZE = 214, RESULT_HIT_RADIUS = 90, RESULT_ITEM = 96;
    static final float CRYSTAL_HEIGHT = 150;
    static final float BOOK_WIDTH = 92, BOOK_HIT_W = 104, BOOK_HIT_H = 66;

    static final float MAIN_COL0 = 176, MAIN_COL_STEP = 165, MAIN_ROW0 = 586, MAIN_ROW_STEP = 82;
    static final float MAIN_ITEM = 70, MAIN_ITEM_LIFT = 36, PEDESTAL_W = 138;
    static final float HOTBAR_COL0 = 286, HOTBAR_COL_STEP = 137.5f, HOTBAR_ROW = 864, HOTBAR_NUMBER_Y = 910;
    static final float HOTBAR_ITEM = 66, HOTBAR_ITEM_LIFT = 34, HOTBAR_PEDESTAL_W = 118;

    /** Click with a held stack here counts as "outside", like clicking past the vanilla panel. */
    static final float[][] CONTENT = {{430, 18, 1242, 488}, {60, 494, 1586, 934}};

    private static final Element[] ELEMENTS = build();

    private static Element[] build() {
        var list = new Element[1 + INPUT_COUNT + 27 + 9 + 1];
        int n = 0;
        list[n++] = new Element(RESULT, Kind.RESULT, CENTER_X, CENTER_Y, RESULT_ITEM, 0, 0, true);
        for (int i = 0; i < INPUT_COUNT; i++)
            list[n++] = new Element(INPUT_START + i, Kind.INPUT, ORBS[i][0], ORBS[i][1], ORB_ITEM, 0, 0, true);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            float pedestal = MAIN_ROW0 + row * MAIN_ROW_STEP;
            list[n++] = new Element(MAIN_START + row * 9 + col, Kind.MAIN, MAIN_COL0 + col * MAIN_COL_STEP,
                    pedestal - MAIN_ITEM_LIFT, MAIN_ITEM, 150, 80, false);
        }
        for (int col = 0; col < 9; col++)
            list[n++] = new Element(HOTBAR_START + col, Kind.HOTBAR, HOTBAR_COL0 + col * HOTBAR_COL_STEP,
                    HOTBAR_ROW - HOTBAR_ITEM_LIFT, HOTBAR_ITEM, 128, 82, false);
        list[n] = new Element(OFFHAND, Kind.OFFHAND, 132, HOTBAR_ROW - MAIN_ITEM_LIFT, HOTBAR_ITEM, 128, 82, false);
        return list;
    }

    static Element[] elements() { return ELEMENTS.clone(); }

    /** @return the element for a player-handler slot id, or null when the slot is not shown. */
    static Element element(int slotId) {
        if (slotId == RESULT) return ELEMENTS[0];
        if (slotId >= INPUT_START && slotId < INPUT_START + INPUT_COUNT) return ELEMENTS[slotId];
        if (slotId >= MAIN_START && slotId <= HOTBAR_END) return ELEMENTS[1 + INPUT_COUNT + slotId - MAIN_START];
        if (slotId == OFFHAND) return ELEMENTS[ELEMENTS.length - 1];
        return null;
    }

    // ------------------------------------------------------------------ floating motion

    private static final double[] PERIODS = {3.7, 4.3, 4.0, 4.7, 5.6}; // four inputs + result
    private static final double[] PHASES = {0.0, 1.9, 3.4, 5.1, 0.8};
    static final float ORB_AMPLITUDE = 4.5f, RESULT_AMPLITUDE = 2.5f;

    /**
     * Vertical float offset (reference px). {@code calm} is 0..1: 1 floats normally, 0 holds still
     * (used while an item is carried so the target never slides away under the cursor).
     */
    static float bob(int slotId, double seconds, float calm) {
        int index;
        float amplitude;
        if (slotId == RESULT) { index = 4; amplitude = RESULT_AMPLITUDE; }
        else if (slotId >= INPUT_START && slotId < INPUT_START + INPUT_COUNT) { index = slotId - INPUT_START; amplitude = ORB_AMPLITUDE; }
        else return 0;
        return (float) (Math.sin(seconds * 2 * Math.PI / PERIODS[index] + PHASES[index]) * amplitude * clamp01(calm));
    }

    /** Eases the float amplitude toward 1 (idle) or 0 (carrying/dragging) at most 4/s. */
    static float approachCalm(float calm, boolean holding, float seconds) {
        float target = holding ? 0 : 1, step = Math.max(0, seconds) * 4f;
        return calm < target ? Math.min(target, calm + step) : Math.max(target, calm - step);
    }

    static float centerY(Element e, double seconds, float calm) { return e.cy() + bob(e.slotId(), seconds, calm); }

    /** Point test in reference coordinates, against the animated position. */
    static boolean hit(Element e, double x, double y, double seconds, float calm) {
        double cy = centerY(e, seconds, calm);
        if (e.round()) {
            double r = e.kind() == Kind.RESULT ? RESULT_HIT_RADIUS : ORB_HIT_RADIUS, dx = x - e.cx(), dy = y - cy;
            return dx * dx + dy * dy <= r * r;
        }
        // Item above, pedestal below: the box hangs from just above the item to just below the pedestal.
        double top = cy - e.itemSize() / 2 - 6, bottom = top + e.hitH();
        return x >= e.cx() - e.hitW() / 2 && x < e.cx() + e.hitW() / 2 && y >= top && y < bottom;
    }

    static boolean bookHit(double x, double y) {
        return Math.abs(x - BOOK_X) <= BOOK_HIT_W / 2 && Math.abs(y - BOOK_Y) <= BOOK_HIT_H / 2;
    }

    static boolean insideContent(double x, double y) {
        for (var r : CONTENT) if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) return true;
        return false;
    }

    // ------------------------------------------------------------------ fitting to the window

    /**
     * FULL: whole layout on screen. SIDE: recipe book open — only the crafting cluster, scaled up
     * beside the vanilla book, so a clicked recipe is visible and its result can be taken.
     * BOOK_ONLY: vanilla narrow mode, the book covers the screen.
     */
    enum Mode { FULL, SIDE, BOOK_ONLY }

    record Fit(Mode mode, float x, float y, float scale) {
        double localX(double screenX) { return (screenX - x) / scale; }
        double localY(double screenY) { return (screenY - y) / scale; }
        float screenX(double localX) { return (float) (x + localX * scale); }
        float screenY(double localY) { return (float) (y + localY * scale); }
    }

    static final float FILL = 0.97f;
    /** Crafting cluster (orbs, result, recipe button and labels) in reference coordinates. */
    static final float[] CRAFT_RECT = {430, 18, 1242, 488};
    /** Below this the 72px reference orb items would be smaller than a vanilla 16px item. */
    static final float MIN_SIDE_SCALE = 0.23f;
    static final int BOOK_W = 147, BOOK_H = 166, BOOK_WIDE_OFFSET = 86, NARROW_WIDTH = 379;

    /** Same placement as vanilla {@code RecipeBookWidget#getLeft}. */
    static int bookLeft(int width, boolean narrow) { return (width - BOOK_W) / 2 - (narrow ? 0 : BOOK_WIDE_OFFSET); }
    static int bookTop(int height) { return (height - BOOK_H) / 2; }
    static boolean narrow(int width) { return width < NARROW_WIDTH; }

    static Fit fit(int width, int height, boolean bookOpen) {
        if (width <= 0 || height <= 0) return new Fit(Mode.FULL, 0, 0, 1);
        if (!bookOpen) {
            float scale = Math.min((float) width / WIDTH, (float) height / HEIGHT) * FILL;
            return new Fit(Mode.FULL, (width - WIDTH * scale) / 2, (height - HEIGHT * scale) / 2, scale);
        }
        if (narrow(width)) return new Fit(Mode.BOOK_ONLY, 0, 0, 1);
        float left = bookLeft(width, false) + BOOK_W + 4, w = width - left - 4;
        float cw = CRAFT_RECT[2] - CRAFT_RECT[0], ch = CRAFT_RECT[3] - CRAFT_RECT[1];
        float scale = Math.min(w / cw, (float) height / ch) * FILL;
        if (scale < MIN_SIDE_SCALE) return new Fit(Mode.BOOK_ONLY, 0, 0, 1);
        return new Fit(Mode.SIDE, left + (w - cw * scale) / 2 - CRAFT_RECT[0] * scale,
                (height - ch * scale) / 2 - CRAFT_RECT[1] * scale, scale);
    }

    /** Whether the element is drawn and clickable in this mode. */
    static boolean visible(Element e, Mode mode) {
        return mode == Mode.FULL || (mode == Mode.SIDE && (e.kind() == Kind.RESULT || e.kind() == Kind.INPUT));
    }

    /** Content area for "click outside throws the carried stack", like the vanilla panel edge. */
    static boolean insideContent(double x, double y, Mode mode) {
        if (mode == Mode.BOOK_ONLY) return true;
        if (mode == Mode.SIDE) return x >= CRAFT_RECT[0] && x < CRAFT_RECT[2] && y >= CRAFT_RECT[1] && y < CRAFT_RECT[3];
        return insideContent(x, y);
    }

    static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    /** Stack counts as shown on the pedestal (vanilla shows nothing for 1). */
    static String count(int count) { return count <= 1 ? "" : Integer.toString(count); }

    /** Mirrors only vanilla's auto-armor branch; crafting input/output quick moves run before it. */
    static boolean blocksArmorQuickMove(int sourceSlot, boolean humanoidArmor, boolean destinationEmpty) {
        return sourceSlot >= MAIN_START && sourceSlot <= OFFHAND && humanoidArmor && destinationEmpty;
    }
}
