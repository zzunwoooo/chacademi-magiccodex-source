package school.magiccodex.client;

import java.util.List;

/**
 * Shop window layout, drawing order and hit testing in the 1672x941 reference space
 * (CHACADEMI_SHOP_REDESIGN reference/approved.png). Pure Java: ShopScreen supplies a Minecraft
 * {@link Canvas}; the review preview supplies a Java2D one, so both run this exact code.
 *
 * Art is never stretched freely: shop_frame keeps one horizontal scale and only its plain middle
 * band grows vertically; every other panel/button is a horizontal 3-slice at a uniform height
 * scale, so corners and gold lines keep their drawn proportions.
 */
final class ShopView {
    static final int W = 1672, H = 941;
    static final int WHITE = 0xFFF0F3F5, GOLD = 0xFFE7CE91, MUTED = 0xFFA5B8C8, DARK = 0xFF2A2112,
            DISABLED = 0xFF8C9298, DISABLED_ON_GOLD = 0xFF4E5257, CYAN = 0xFF92E7F2;
    static final String ROOT = "textures/gui/shop/v2/";

    enum Face { TITLE, SECTION, BODY, LABEL, BOLD }
    enum Tab { ALL, BUY, SELL }

    interface Canvas {
        /** Draws source region (u,v,sw,sh) of a texture with size (tw,th) into the destination box. */
        void image(String texture, float x, float y, float w, float h, int u, int v, int sw, int sh, int tw, int th);
        void text(String value, float x, float centerY, float size, int color, Face face, boolean centered);
        float width(String value, float size, Face face);
        void fill(float x, float y, float w, float h, int color);
        /** Item icon (real ItemStack) drawn as a square; nothing when the row has no stack. */
        void item(Row row, float x, float y, float size);
        /** NPC illustration, full source, any size. */
        void portrait(float x, float y, float w, float h);
        void searchField(float x, float y, float w, float h);
        void quantityField(float x, float y, float w, float h);
        void pushLayer(float z);
        void popLayer();
    }

    /** One visible product. buy/sell are "" when that trade is off for the product. */
    record Row(String id, String name, List<String> lore, String buy, String sell, int owned,
               boolean canBuy, boolean canSell, Object icon) {
        boolean buys() { return !buy.isEmpty(); }
        boolean sells() { return !sell.isEmpty(); }
    }

    /** Everything one frame needs; built by the screen from the server response. */
    record Model(String title, String role, String npcName, boolean hasPortrait, float portraitAspect,
                 String balance, Tab tab, boolean[] tabEnabled, List<Row> rows, int scroll, String selected,
                 boolean selling, String empty, int max, String total, boolean minusOk, boolean plusOk,
                 boolean tradeOk, boolean busy, String notice, float noticeAlpha, double mouseX, double mouseY,
                 boolean mouseDown, float screenBottom) {}

    enum Kind { NONE, CLOSE, TAB, SEARCH, ROW, ROW_BUY, ROW_SELL, ICON, MINUS, PLUS, QUANTITY, TRADE, SCROLL }
    record Hit(Kind kind, int index) { static final Hit NONE = new Hit(Kind.NONE, -1); }

    // ---- geometry -------------------------------------------------------------------------------------------
    // shop_frame.png 1318x1118: outer gold line x8..1307, inner panel x40..1278 / y235..1089.
    static final float FRAME_X = 588, FRAME_Y = 12, FRAME_K = 1047f / 1318f, FRAME_H = 917;
    static final int FRAME_TOP = 300, FRAME_BOTTOM = 1000;           // source rows kept at uniform scale
    static final float CX = 632, CR = 1591;                           // content left / right
    static final float BAR_Y = 211, BAR_H = 52;
    static final float BAL_X = 632, BAL_W = 372, SEARCH_X = 1020, SEARCH_W = 296;
    static final float TAB_X = 1327, TAB_W = 84, TAB_GAP = 6, TAB_Y = 214, TAB_H = 46;
    static final float LIST_Y = 275, ROW_W = 938, ROW_H = 124, ROW_GAP = 8;
    static final int ROWS = 4;
    static final float BAR_X = 1579, BAR_W = 12;                      // scrollbar
    static final float FOOT_Y = 807, FOOT_H = 74;
    static final float CLOSE_X = 1556, CLOSE_Y = 90, CLOSE_S = 44;
    // nameplate.png 1707x794 at a uniform 0.247 scale
    static final float PLATE_X = 78, PLATE_Y = 722, PLATE_W = 422, PLATE_H = 422f * 794 / 1707;
    static final float NPC_CENTER = 300, NPC_MAX_H = 1000, NPC_MAX_W = 640;

    static float rowY(int i) { return LIST_Y + i * (ROW_H + ROW_GAP); }
    static float listBottom() { return rowY(ROWS - 1) + ROW_H; }
    static float tabX(int i) { return TAB_X + i * (TAB_W + TAB_GAP); }
    // row-relative
    static final float SLOT_DX = 10, SLOT_DY = 8, SLOT_W = 132, SLOT_H = 108, ICON = 96, TEXT_DX = 162,
            LABEL_DX = 500, COIN_DX = 562, AMOUNT_RIGHT = ROW_W - 14 - 144 - 22, BTN_W = 144, BTN_DX = ROW_W - 14 - BTN_W;
    // footer
    static final float QTY_LABEL_X = 656, QTY_X = 706, QTY_W = 226, QTY_H = 50, STEP = 44,
            MAX_X = 942, DIVIDER_X = 1012, SUM_LABEL_X = 1030, SUM_COIN_X = 1080, SUM_X = 1118, SUM_MAX = 184,
            TRADE_X = 1316, TRADE_W = 263, TRADE_H = 58;

    static float qtyY() { return FOOT_Y + (FOOT_H - QTY_H) / 2; }
    static float footCenter() { return FOOT_Y + FOOT_H / 2; }
    static float[] minusBox() { return new float[]{QTY_X + 17, qtyY() + 3, STEP, STEP}; }
    static float[] plusBox() { return new float[]{QTY_X + QTY_W - 17 - STEP, qtyY() + 3, STEP, STEP}; }
    static float[] quantityBox() { return new float[]{QTY_X + 21 + STEP, qtyY() + 3, QTY_W - 42 - 2 * STEP, STEP}; }
    static float[] tradeBox() { return new float[]{TRADE_X, FOOT_Y + (FOOT_H - TRADE_H) / 2, TRADE_W, TRADE_H}; }
    static float[] searchBox() { return new float[]{SEARCH_X, BAR_Y, SEARCH_W, BAR_H}; }

    /** Row action buttons: one centred button, or buy over sell when the 전체 tab shows both. */
    static float[] buyButton(Row r, boolean both, int i) {
        float y = rowY(i);
        return both ? new float[]{CX + BTN_DX, y + 17, BTN_W, 42} : new float[]{CX + BTN_DX, y + 35, BTN_W, 54};
    }
    static float[] sellButton(Row r, boolean both, int i) {
        float y = rowY(i);
        return both ? new float[]{CX + BTN_DX, y + 65, BTN_W, 42} : new float[]{CX + BTN_DX, y + 35, BTN_W, 54};
    }
    static boolean both(Model m, Row r) { return m.tab() == Tab.ALL && r.buys() && r.sells(); }
    /** Which single action a row offers outside the "both" case. */
    static boolean rowSells(Model m, Row r) { return m.tab() == Tab.SELL || m.tab() == Tab.ALL && !r.buys(); }

    static boolean in(double x, double y, float[] b) { return in(x, y, b[0], b[1], b[2], b[3]); }
    static boolean in(double x, double y, float bx, float by, float bw, float bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    // ---- art ------------------------------------------------------------------------------------------------
    /** name, width, height, left cap, right cap (source pixels). Caps measured from the PNG ornaments. */
    record Art(String name, int w, int h, int left, int right) {
        String path() { return ROOT + name + ".png"; }
    }
    static final Art ROW = new Art("item_row", 2080, 306, 90, 90), ROW_HOVER = new Art("item_row_hover", 2080, 306, 90, 90),
            ROW_SELECTED = new Art("item_row_selected", 2080, 306, 90, 90), SLOT = new Art("item_slot", 993, 928, 130, 130),
            FOOTER = new Art("footer_panel", 1328, 154, 70, 70), BALANCE = new Art("balance_field", 1958, 262, 80, 80),
            SEARCH = new Art("search_field", 2039, 266, 90, 90);
    static Art gold(String state) { return new Art("gold_button" + state, 1793, 468, 180, 180); }
    static Art navy(String state) { return new Art("navy_button" + state, 712, 672, 130, 130); }
    static Art tab(boolean selected, String state) {
        return selected ? new Art("tab_selected" + state, 190, 64, 22, 22) : new Art("tab_idle" + state, 180, 62, 21, 21);
    }

    /** Horizontal 3-slice at a uniform height scale (vertical proportions never change). */
    static void slice(Canvas c, Art a, float x, float y, float w, float h) {
        float s = h / a.h();
        float l = a.left() * s, r = a.right() * s;
        if (l + r > w) { float k = w / (l + r); l *= k; r *= k; }
        c.image(a.path(), x, y, l, h, 0, 0, a.left(), a.h(), a.w(), a.h());
        c.image(a.path(), x + l, y, w - l - r, h, a.left(), 0, a.w() - a.left() - a.right(), a.h(), a.w(), a.h());
        c.image(a.path(), x + w - r, y, r, h, a.w() - a.right(), 0, a.right(), a.h(), a.w(), a.h());
    }
    static void slice(Canvas c, Art a, float[] b) { slice(c, a, b[0], b[1], b[2], b[3]); }

    static void frame(Canvas c) {
        String p = ROOT + "shop_frame.png";
        float w = 1318 * FRAME_K, top = FRAME_TOP * FRAME_K, bottom = (1118 - FRAME_BOTTOM) * FRAME_K;
        c.image(p, FRAME_X, FRAME_Y, w, top, 0, 0, 1318, FRAME_TOP, 1318, 1118);
        c.image(p, FRAME_X, FRAME_Y + top, w, FRAME_H - top - bottom, 0, FRAME_TOP, 1318, FRAME_BOTTOM - FRAME_TOP, 1318, 1118);
        c.image(p, FRAME_X, FRAME_Y + FRAME_H - bottom, w, bottom, 0, FRAME_BOTTOM, 1318, 1118 - FRAME_BOTTOM, 1318, 1118);
    }
    static void icon(Canvas c, String name, float cx, float cy, float size, int tw, int th) {
        c.image(ROOT + name + ".png", cx - size / 2, cy - size / 2, size, size, 0, 0, tw, th, tw, th);
    }
    /** coin.png's disc fills ~53% of its 128px canvas, so the box is enlarged to give a disc of {@code size}. */
    static void coin(Canvas c, float cx, float cy, float size) { icon(c, "coin", cx, cy, size * 1.9f, 128, 128); }
    /** Display-only grouping ("1200" -> "1,200"); the server value itself is never changed or re-sent. */
    static String money(String plain) {
        if (plain == null || plain.isEmpty()) return "—";
        try {
            var d = new java.math.BigDecimal(plain);
            var f = new java.text.DecimalFormat("#,##0.######", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
            return f.format(d);
        } catch (RuntimeException e) { return plain; }
    }
    /** Lore text without resource-pack glyphs (private-use code points) the UI font cannot draw. */
    static String clean(String s) {
        var b = new StringBuilder();
        s.codePoints().filter(cp -> !(cp >= 0xE000 && cp <= 0xF8FF) && cp < 0xF0000 && cp != '§' && !Character.isISOControl(cp))
                .forEach(b::appendCodePoint);
        return b.toString().replaceAll("\\s+", " ").strip();
    }
    /** Numbers are never cut: they only shrink (down to 45%) to stay inside their box. */
    static float shrink(Canvas c, String s, float size, Face f, float max) {
        float w = c.width(s, size, f);
        return w <= max ? size : Math.max(size * .45f, size * max / w);
    }

    /** Shrinks to 82% first, then cuts with an ellipsis so text never leaves its box. */
    static String fit(Canvas c, String s, float size, Face f, float max, float[] out) {
        out[0] = size;
        if (s.isEmpty() || c.width(s, size, f) <= max) return s;
        float small = size * .82f;
        out[0] = Math.max(small, size * max / c.width(s, size, f));
        if (c.width(s, out[0], f) <= max) return s;
        out[0] = small;
        int n = s.length();
        while (n > 0 && c.width(s.substring(0, n) + "…", small, f) > max) n--;
        return n == 0 ? "…" : s.substring(0, n).stripTrailing() + "…";
    }
    static void fitted(Canvas c, String s, float x, float cy, float size, float max, int color, Face f, boolean centered) {
        float[] k = new float[1];
        String v = fit(c, s, size, f, max, k);
        c.text(v, x, cy, k[0], color, f, centered);
    }

    static String state(Model m, float[] b, boolean enabled) {
        if (!enabled) return "_disabled";
        if (!in(m.mouseX(), m.mouseY(), b)) return "";
        return m.mouseDown() ? "_pressed" : "_hover";
    }

    // ---- frame ----------------------------------------------------------------------------------------------
    static void draw(Canvas c, Model m) {
        if (m.hasPortrait()) {
            // Bottom of the illustration sits at (or below) the real screen bottom, so its own cut edge
            // is never visible; the nameplate covers the waist like the reference.
            float h = Math.min(NPC_MAX_H, NPC_MAX_W / Math.max(.1f, m.portraitAspect()));
            float w = h * m.portraitAspect();
            float bottom = Math.max(H, m.screenBottom());
            c.portrait(NPC_CENTER - w / 2, bottom - h, w, h);
        }
        c.image(ROOT + "nameplate.png", PLATE_X, PLATE_Y, PLATE_W, PLATE_H, 0, 0, 1707, 794, 1707, 794);
        float plateCx = PLATE_X + PLATE_W / 2;
        // role sits between the top star's tip and the divider line, the name below the line
        fitted(c, m.role(), plateCx, PLATE_Y + 85, 18, 230, GOLD, Face.LABEL, true);
        fitted(c, m.npcName(), plateCx, PLATE_Y + 131, 38, 270, WHITE, Face.SECTION, true);

        frame(c);
        fitted(c, m.title(), FRAME_X + 1318 * FRAME_K / 2, 131, 46, 300, GOLD, Face.SECTION, true);
        float[] close = {CLOSE_X, CLOSE_Y, CLOSE_S, CLOSE_S};
        String cs = in(m.mouseX(), m.mouseY(), close) ? m.mouseDown() ? "_pressed" : "_hover" : "";
        icon(c, "close_button" + cs, CLOSE_X + CLOSE_S / 2, CLOSE_Y + CLOSE_S / 2, 116, 96, 96);

        // balance · search · tabs
        slice(c, BALANCE, BAL_X, BAR_Y, BAL_W, BAR_H);
        float mid = BAR_Y + BAR_H / 2;
        coin(c, BAL_X + 44, mid, 28);
        c.text("보유 골드", BAL_X + 68, mid, 21, WHITE, Face.LABEL, false);
        float labelEnd = BAL_X + 68 + c.width("보유 골드", 21, Face.LABEL) + 18;
        String bal = m.balance().isEmpty() ? "—" : money(m.balance()) + " G";
        float bs = shrink(c, bal, 26, Face.BOLD, BAL_X + BAL_W - 34 - labelEnd);
        c.text(bal, BAL_X + BAL_W - 34 - c.width(bal, bs, Face.BOLD), mid, bs, WHITE, Face.BOLD, false);
        slice(c, SEARCH, searchBox());
        icon(c, "search", SEARCH_X + 36, mid, 32, 128, 128);
        c.searchField(SEARCH_X + 54, BAR_Y + 5, SEARCH_W - 70, BAR_H - 10);
        String[] tabs = {"전체", "구매", "판매"};
        for (int i = 0; i < 3; i++) {
            float[] b = {tabX(i), TAB_Y, TAB_W, TAB_H};
            boolean sel = m.tab().ordinal() == i, ok = m.tabEnabled()[i];
            slice(c, tab(sel, state(m, b, ok)), b);
            c.text(tabs[i], b[0] + TAB_W / 2, TAB_Y + TAB_H / 2, 19, !ok ? DISABLED : sel ? DARK : GOLD, Face.BOLD, true);
        }

        // rows
        for (int i = 0; i < ROWS && i + m.scroll() < m.rows().size(); i++) row(c, m, m.rows().get(i + m.scroll()), i);
        if (m.rows().isEmpty()) c.text(m.empty(), CX + ROW_W / 2, (LIST_Y + listBottom()) / 2, 24, MUTED, Face.LABEL, true);
        scrollbar(c, m);

        footer(c, m);
        if (!m.notice().isEmpty() && m.noticeAlpha() > 0) {
            c.pushLayer(400);
            float y = listBottom() - 150;
            slice(c, BALANCE, CX + 40, y, ROW_W - 80, 52);
            int a = Math.round(255 * Math.min(1, m.noticeAlpha()));
            fitted(c, m.notice(), CX + ROW_W / 2, y + 26, 21, ROW_W - 130, (a << 24) | (WHITE & 0xFFFFFF), Face.LABEL, true);
            c.popLayer();
        }
    }

    static void row(Canvas c, Model m, Row r, int i) {
        float x = CX, y = rowY(i);
        boolean selected = r.id().equals(m.selected()), hover = in(m.mouseX(), m.mouseY(), x, y, ROW_W, ROW_H);
        slice(c, selected ? ROW_SELECTED : hover ? ROW_HOVER : ROW, x, y, ROW_W, ROW_H);
        slice(c, SLOT, x + SLOT_DX, y + SLOT_DY, SLOT_W, SLOT_H);
        c.item(r, x + SLOT_DX + (SLOT_W - ICON) / 2, y + SLOT_DY + (SLOT_H - ICON) / 2, ICON);

        boolean both = both(m, r), sells = !both && rowSells(m, r);
        float tx = x + TEXT_DX, textMax = x + (both ? LABEL_DX - 14 : COIN_DX - 32) - tx;
        boolean lore = !r.lore().isEmpty();
        fitted(c, r.name(), tx, y + (lore ? 37 : 62), 30, textMax, WHITE, Face.SECTION, false);
        for (int l = 0; l < Math.min(2, r.lore().size()); l++)
            fitted(c, r.lore().get(l), tx, y + 74 + l * 25, 18, textMax, MUTED, Face.BODY, false);

        // Price column: label and coin left-aligned on fixed columns, amount right-aligned before the button.
        if (both) {
            c.text("구매가", x + LABEL_DX, y + 38, 16, MUTED, Face.LABEL, false);
            c.text("판매가", x + LABEL_DX, y + 86, 16, MUTED, Face.LABEL, false);
            price(c, r.buy(), x, y + 38, 23);
            price(c, r.sell(), x, y + 86, 23);
            action(c, m, buyButton(r, true, i), "구매", true, r.canBuy());
            action(c, m, sellButton(r, true, i), "판매", false, r.canSell());
        } else if (sells) {
            price(c, r.sell(), x, y + 50, 26);
            String owned = "보유 " + money(Integer.toString(r.owned())) + "개";
            c.text(owned, x + AMOUNT_RIGHT - c.width(owned, 16, Face.LABEL), y + 84, 16, MUTED, Face.LABEL, false);
            action(c, m, sellButton(r, false, i), "판매", m.tab() == Tab.SELL, r.canSell());
        } else {
            price(c, r.buy(), x, y + 62, 26);
            action(c, m, buyButton(r, false, i), "구매", true, r.canBuy());
        }
    }
    /** Coin on the fixed left column, amount right-aligned; long amounts only shrink (never cut). */
    static void price(Canvas c, String value, float x, float cy, float size) {
        String v = money(value) + " G";
        coin(c, x + COIN_DX, cy, size);
        float left = x + COIN_DX + size / 2 + 10, right = x + AMOUNT_RIGHT;
        float k = shrink(c, v, size, Face.BOLD, right - left);
        c.text(v, right - c.width(v, k, Face.BOLD), cy, k, WHITE, Face.BOLD, false);
    }
    static void action(Canvas c, Model m, float[] b, String label, boolean goldStyle, boolean ok) {
        ok = ok && !m.busy();
        String st = state(m, b, ok);
        slice(c, goldStyle ? gold(st) : navy(st), b);
        c.text(label, b[0] + b[2] / 2, b[1] + b[3] / 2, b[3] > 48 ? 22 : 20, !ok ? goldStyle ? DISABLED_ON_GOLD : DISABLED : goldStyle ? DARK : GOLD, Face.BOLD, true);
    }

    static float[] track() { return new float[]{BAR_X, LIST_Y, BAR_W, listBottom() - LIST_Y}; }
    static float[] thumb(Model m) {
        float[] t = track();
        int n = m.rows().size();
        float inner = t[3] - 8, len = n <= ROWS ? inner : Math.max(40, inner * ROWS / n);
        int max = Math.max(0, n - ROWS);
        float y = t[1] + 4 + (max == 0 ? 0 : (inner - len) * m.scroll() / max);
        return new float[]{t[0], y, t[2], len};
    }
    /** scrollbar.png 25x512: caps 0..4 / 488..512, thumb 4..132, track 132..488. */
    static void scrollbar(Canvas c, Model m) {
        String p = ROOT + "scrollbar.png";
        float[] t = track();
        float k = t[2] / 25f;
        c.image(p, t[0], t[1], t[2], 4 * k, 0, 0, 25, 4, 25, 512);
        c.image(p, t[0], t[1] + 4 * k, t[2], t[3] - 28 * k, 0, 132, 25, 356, 25, 512);
        c.image(p, t[0], t[1] + t[3] - 24 * k, t[2], 24 * k, 0, 488, 25, 24, 25, 512);
        float[] h = thumb(m);
        float cap = Math.min(18 * k, h[3] / 2);
        c.image(p, h[0], h[1], h[2], cap, 0, 4, 25, 18, 25, 512);
        c.image(p, h[0], h[1] + cap, h[2], h[3] - 2 * cap, 0, 22, 25, 90, 25, 512);
        c.image(p, h[0], h[1] + h[3] - cap, h[2], cap, 0, 112, 25, 20, 25, 512);
    }

    static void footer(Canvas c, Model m) {
        slice(c, FOOTER, CX, FOOT_Y, CR - CX, FOOT_H);
        float cy = footCenter();
        c.text("수량", QTY_LABEL_X, cy, 22, WHITE, Face.LABEL, false);
        slice(c, BALANCE, QTY_X, qtyY(), QTY_W, QTY_H);
        step(c, m, minusBox(), "−", m.minusOk());
        step(c, m, plusBox(), "+", m.plusOk());
        float[] q = quantityBox();
        c.quantityField(q[0], q[1], q[2], q[3]);
        fitted(c, "최대 " + money(Integer.toString(m.max())), MAX_X, cy, 15, DIVIDER_X - MAX_X - 8, MUTED, Face.LABEL, false);
        c.fill(DIVIDER_X, cy - 22, 1.5f, 44, 0x66E7CE91);
        c.text("합계", SUM_LABEL_X, cy, 22, WHITE, Face.LABEL, false);
        coin(c, SUM_COIN_X + 14, cy, 30);
        String sum = m.total().isEmpty() ? "—" : money(m.total()) + " G";
        c.text(sum, SUM_X, cy, shrink(c, sum, 30, Face.BOLD, SUM_MAX), GOLD, Face.BOLD, false);
        float[] b = tradeBox();
        boolean ok = m.tradeOk() && !m.busy();
        slice(c, gold(state(m, b, ok)), b);
        String label = m.busy() ? "처리 중…" : m.selling() ? "판매하기" : "구매하기";
        c.text(label, b[0] + b[2] / 2, b[1] + b[3] / 2, 26, ok ? DARK : DISABLED_ON_GOLD, Face.SECTION, true);
    }
    static void step(Canvas c, Model m, float[] b, String glyph, boolean ok) {
        ok = ok && !m.busy();
        slice(c, navy(state(m, b, ok)), b);
        c.text(glyph, b[0] + b[2] / 2, b[1] + b[3] / 2, 26, ok ? GOLD : DISABLED, Face.BOLD, true);
    }

    // ---- input ----------------------------------------------------------------------------------------------
    static Hit hit(Model m, double x, double y) {
        if (in(x, y, CLOSE_X, CLOSE_Y, CLOSE_S, CLOSE_S)) return new Hit(Kind.CLOSE, 0);
        for (int i = 0; i < 3; i++) if (in(x, y, tabX(i), TAB_Y, TAB_W, TAB_H)) return new Hit(Kind.TAB, i);
        if (in(x, y, searchBox())) return new Hit(Kind.SEARCH, 0);
        for (int i = 0; i < ROWS && i + m.scroll() < m.rows().size(); i++) {
            int index = i + m.scroll();
            Row r = m.rows().get(index);
            boolean both = both(m, r);
            if (both) {
                if (in(x, y, buyButton(r, true, i))) return new Hit(Kind.ROW_BUY, index);
                if (in(x, y, sellButton(r, true, i))) return new Hit(Kind.ROW_SELL, index);
            } else if (rowSells(m, r) ? in(x, y, sellButton(r, false, i)) : in(x, y, buyButton(r, false, i)))
                return new Hit(rowSells(m, r) ? Kind.ROW_SELL : Kind.ROW_BUY, index);
            if (in(x, y, CX + SLOT_DX, rowY(i) + SLOT_DY, SLOT_W, SLOT_H)) return new Hit(Kind.ICON, index);
            if (in(x, y, CX, rowY(i), ROW_W, ROW_H)) return new Hit(Kind.ROW, index);
        }
        if (in(x, y, BAR_X - 4, LIST_Y, BAR_W + 8, listBottom() - LIST_Y)) return new Hit(Kind.SCROLL, 0);
        if (in(x, y, minusBox())) return new Hit(Kind.MINUS, 0);
        if (in(x, y, plusBox())) return new Hit(Kind.PLUS, 0);
        if (in(x, y, quantityBox())) return new Hit(Kind.QUANTITY, 0);
        if (in(x, y, tradeBox())) return new Hit(Kind.TRADE, 0);
        return Hit.NONE;
    }
    /** Scroll position for a thumb dragged / track clicked at logical y. */
    static int scrollAt(Model m, double y) {
        int max = Math.max(0, m.rows().size() - ROWS);
        if (max == 0) return 0;
        float[] t = track(), h = thumb(m);
        float inner = t[3] - 8 - h[3];
        double f = (y - t[1] - 4 - h[3] / 2) / Math.max(1, inner);
        return (int) Math.round(Math.max(0, Math.min(1, f)) * max);
    }
}
