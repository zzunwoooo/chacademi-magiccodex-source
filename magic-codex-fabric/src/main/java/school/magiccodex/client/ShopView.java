package school.magiccodex.client;

import java.util.List;

/**
 * NPC shop window, v3 (일반 NPC 상점 12칸 UI v2 묶음: reference.png 승인 시안), in the 1672x941 reference space.
 * Pure Java: ShopScreen supplies a Minecraft {@link Canvas}; the review preview supplies a Java2D one, so both
 * run this exact code.
 *
 * Drawing order from the bundle README: game screen → light blur (screen) → navy backing (code, alpha ~0.8)
 * → shop_frame.png (alpha 1) → products / text / buttons (alpha 1). No overall opacity is applied.
 * Only the frame and the gold trade button are textures; card borders, the selection line, arrows, the search
 * field and secondary buttons are drawn as geometry. The NPC illustration stays left of the frame so it never
 * covers products.
 */
final class ShopView {
    static final int W = 1672, H = 941;
    static final int WHITE = 0xFFF0F3F5, GOLD = 0xFFE7CE91, MUTED = 0xFFA5B8C8, DARK = 0xFF2A2112,
            DISABLED = 0xFF8C9298, DISABLED_ON_GOLD = 0xFF4E5257, CYAN = 0xFF92E7F2;
    static final String ROOT = "textures/gui/shop/v3/", OLD = "textures/gui/shop/v2/";
    // geometry colours
    static final int BACKING = 0xCC0B1828, CARD = 0x8C10223A, CARD_HOVER = 0xB3182E4A, LINE = 0x59E7CE91,
            LINE_HOVER = 0x99E7CE91, LINE_SELECTED = 0xFFE2C27E, GLOW = 0x26E7CE91, FIELD = 0x8C0A1626,
            TAB_GOLD = 0xFFDDBD78, TAB_GOLD_EDGE = 0xFFF6E2AE;

    enum Face { TITLE, SECTION, BODY, LABEL, BOLD }
    enum Tab { ALL, BUY, SELL }
    /** 정렬: 기본순 = 상점에 등록된 순서(분류·순서), 나머지는 화면에서만 다시 정렬한다. */
    enum Sort {
        DEFAULT("기본순"), PRICE_LOW("가격 낮은순"), PRICE_HIGH("가격 높은순"), NAME("이름순");
        final String label;
        Sort(String label) { this.label = label; }
    }

    interface Canvas {
        /** Draws source region (u,v,sw,sh) of a texture with size (tw,th) into the destination box, tinted (ARGB). */
        void image(String texture, float x, float y, float w, float h, int u, int v, int sw, int sh, int tw, int th, int tint);
        default void image(String texture, float x, float y, float w, float h, int u, int v, int sw, int sh, int tw, int th) {
            image(texture, x, y, w, h, u, v, sw, sh, tw, th, 0xFFFFFFFF);
        }
        void text(String value, float x, float centerY, float size, int color, Face face, boolean centered);
        float width(String value, float size, Face face);
        void fill(float x, float y, float w, float h, int color);
        void line(float ax, float ay, float bx, float by, float thickness, int color);
        /** Diamond with half-width rx and half-height ry (thin ones make star points). */
        void diamond(float cx, float cy, float rx, float ry, int color);
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
                 String balance, Tab tab, boolean[] tabEnabled, List<Row> rows, int page, String selected,
                 boolean selling, String empty, int max, String total, boolean minusOk, boolean plusOk,
                 boolean tradeOk, boolean switchOk, Sort sort, boolean sortOpen, boolean busy, String notice,
                 float noticeAlpha, double mouseX, double mouseY, boolean mouseDown, float screenBottom) {
        int pages() { return Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE); }
        Row chosen() { return rows.stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null); }
    }

    enum Kind { NONE, TAB, SEARCH, SORT, SORT_OPTION, CARD, ICON, PREV, NEXT, MINUS, PLUS, QUANTITY, SWITCH, TRADE }
    record Hit(Kind kind, int index) { static final Hit NONE = new Hit(Kind.NONE, -1); }

    // ---- geometry -------------------------------------------------------------------------------------------
    // shop_frame.png 1440x1092: outer line x37..1402 / y62..1017, ornament line y140 (star x720),
    // header line y176, footer line y887. Drawn at one uniform scale K so lines and stars keep their shape.
    static final float K = .9f, OUT_L = 421, OUT_T = 41;
    static float sx(float u) { return OUT_L + (u - 37) * K; }
    static float sy(float v) { return OUT_T + (v - 62) * K; }
    static final float OUT_R = sx(1402), OUT_B = sy(1017);
    static final float CX = 449, CR = 1622;                              // content left / right
    static final float HEAD_Y = (sy(66) + sy(140)) / 2;                  // title / gold row centre
    static final float BAR_Y = 160, BAR_H = 48;
    static final float SORT_W = 150, SORT_X = CR - SORT_W, DIVIDER_X = SORT_X - 22;
    static final float TAB_W = 116, TAB_GAP = 8, TAB_X = DIVIDER_X - 22 - 3 * TAB_W - 2 * TAB_GAP;
    static final float SEARCH_X = CX, SEARCH_W = TAB_X - 24 - CX;
    static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;
    static final float GRID_Y = 222, CARD_GAP_X = 12, CARD_GAP_Y = 10, CARD_W = (CR - CX - (COLS - 1) * CARD_GAP_X) / COLS, CARD_H = 160;
    static final float CARD_ICON = 84;
    static final float PAGER_Y = 752, ARROW_DX = 74, ARROW_S = 36;
    static final float FOOT_TOP = sy(889), FOOT_BOTTOM = sy(1012), FOOT_CY = (FOOT_TOP + FOOT_BOTTOM) / 2;
    // footer columns
    static final float F_ICON_X = CX + 6, F_ICON = 78, F_TEXT_X = CX + 102, F_TEXT_MAX = 186,
            F_DIV1 = 750, F_LABEL_X = 768, F_VALUE_RIGHT = 900, F_DIV2 = 918,
            QTY_LABEL_X = 934, QTY_X = 984, QTY_W = 188, QTY_H = 50, STEP = 44,
            F_DIV3 = 1190, SUM_X = 1206, SUM_MAX = 118,
            TRADE_H = 58, TRADE_W = TRADE_H * 2131f / 633f, TRADE_X = CR - TRADE_W,
            SWITCH_W = 88, SWITCH_H = 48, SWITCH_X = TRADE_X - 10 - SWITCH_W;
    // NPC: drawn first, so the navy backing and the cards sit over any part that reaches the frame.
    static final float NPC_CENTER = 224, NPC_MAX_H = 940, NPC_MAX_W = 448;
    // role / name label bottom-left (reference: small star, gold role, white name, thin line)
    static final float NAME_X = 104, ROLE_Y = 826, NAME_Y = 866, NAME_LINE_Y = 896, NAME_BAND_Y = 796;

    static float cardX(int slot) { return CX + (slot % COLS) * (CARD_W + CARD_GAP_X); }
    static float cardY(int slot) { return GRID_Y + (slot / COLS) * (CARD_H + CARD_GAP_Y); }
    static float gridBottom() { return cardY(PER_PAGE - 1) + CARD_H; }
    static float tabX(int i) { return TAB_X + i * (TAB_W + TAB_GAP); }
    static float qtyY() { return FOOT_CY - QTY_H / 2; }
    static float[] minusBox() { return new float[]{QTY_X + 3, qtyY() + 3, STEP, STEP}; }
    static float[] plusBox() { return new float[]{QTY_X + QTY_W - 3 - STEP, qtyY() + 3, STEP, STEP}; }
    static float[] quantityBox() { return new float[]{QTY_X + 6 + STEP, qtyY() + 3, QTY_W - 12 - 2 * STEP, STEP}; }
    static float[] tradeBox() { return new float[]{TRADE_X, FOOT_CY - TRADE_H / 2, TRADE_W, TRADE_H}; }
    static float[] switchBox() { return new float[]{SWITCH_X, FOOT_CY - SWITCH_H / 2, SWITCH_W, SWITCH_H}; }
    static float[] searchBox() { return new float[]{SEARCH_X, BAR_Y, SEARCH_W, BAR_H}; }
    static float[] sortBox() { return new float[]{SORT_X, BAR_Y, SORT_W, BAR_H}; }
    static float[] sortOption(int i) { return new float[]{SORT_X, BAR_Y + BAR_H + 6 + i * 44, SORT_W, 44}; }
    static float[] prevBox() { return new float[]{(CX + CR) / 2 - ARROW_DX - ARROW_S / 2, PAGER_Y - ARROW_S / 2, ARROW_S, ARROW_S}; }
    static float[] nextBox() { return new float[]{(CX + CR) / 2 + ARROW_DX - ARROW_S / 2, PAGER_Y - ARROW_S / 2, ARROW_S, ARROW_S}; }
    static float[] iconBox(int slot) { return new float[]{cardX(slot) + (CARD_W - CARD_ICON) / 2, cardY(slot) + 14, CARD_ICON, CARD_ICON}; }

    static boolean in(double x, double y, float[] b) { return in(x, y, b[0], b[1], b[2], b[3]); }
    static boolean in(double x, double y, float bx, float by, float bw, float bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    // ---- art helpers ----------------------------------------------------------------------------------------
    static void frame(Canvas c) {
        c.image(ROOT + "shop_frame.png", sx(0), sy(0), 1440 * K, 1092 * K, 0, 0, 1440, 1092, 1440, 1092);
    }
    static void outline(Canvas c, float x, float y, float w, float h, float t, int color) {
        c.fill(x, y, w, t, color);
        c.fill(x, y + h - t, w, t, color);
        c.fill(x, y + t, t, h - 2 * t, color);
        c.fill(x + w - t, y + t, t, h - 2 * t, color);
    }
    /** Runtime box: filled body + thin line (search field, secondary buttons, cards). */
    static void box(Canvas c, float x, float y, float w, float h, int body, int edge, float t) {
        c.fill(x + t, y + t, w - 2 * t, h - 2 * t, body);
        outline(c, x, y, w, h, t, edge);
    }
    static void icon(Canvas c, String path, float cx, float cy, float size, int tw, int th) {
        c.image(path, cx - size / 2, cy - size / 2, size, size, 0, 0, tw, th, tw, th);
    }
    /** coin.png's disc fills ~53% of its 128px canvas, so the box is enlarged to give a disc of {@code size}. */
    static void coin(Canvas c, float cx, float cy, float size) { icon(c, OLD + "coin.png", cx, cy, size * 1.9f, 128, 128); }
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
    static boolean hover(Model m, float[] b) { return in(m.mouseX(), m.mouseY(), b); }
    /** Price a card shows: the 구매 tab and buyable products show the buy price, otherwise the sell price. */
    static boolean cardSells(Model m, Row r) { return m.tab() == Tab.SELL || !r.buys(); }

    // ---- frame ----------------------------------------------------------------------------------------------
    static void draw(Canvas c, Model m) {
        if (m.hasPortrait()) {
            // Left of the frame only; the bottom sits at (or below) the real screen bottom so its cut edge never shows.
            float h = Math.min(NPC_MAX_H, NPC_MAX_W / Math.max(.1f, m.portraitAspect()));
            float w = h * m.portraitAspect();
            float bottom = Math.max(H, m.screenBottom());
            c.portrait(NPC_CENTER - w / 2, bottom - h, w, h);
        }

        c.fill(OUT_L, OUT_T, OUT_R - OUT_L, OUT_B - OUT_T, BACKING);
        frame(c);
        // role / name, over the illustration's lower left
        // soft dark band so the label stays readable over any illustration (fades in from above)
        for (int i = 0; i < 6; i++) c.fill(0, NAME_BAND_Y - 36 + i * 6, OUT_L - 4, 6, ((i + 1) * 0x18) << 24 | 0x08111E);
        c.fill(0, NAME_BAND_Y, OUT_L - 4, H - NAME_BAND_Y, 0xA808111E);
        fitted(c, m.role(), NAME_X, ROLE_Y, 19, 200, GOLD, Face.LABEL, false);
        compass(c, NAME_X - 34, NAME_Y + 2, 18);
        fitted(c, m.npcName(), NAME_X, NAME_Y, 40, OUT_L - 30 - NAME_X, WHITE, Face.SECTION, false);
        c.fill(NAME_X - 50, NAME_LINE_Y, 300, 1.5f, 0x99E7CE91);

        // title · gold
        compass(c, CX + 20, HEAD_Y, 26);
        String gold = m.balance().isEmpty() ? "—" : money(m.balance()) + " G";
        float gs = shrink(c, gold, 30, Face.BOLD, 260);
        float goldX = CR - c.width(gold, gs, Face.BOLD);
        c.text(gold, goldX, HEAD_Y, gs, WHITE, Face.BOLD, false);
        float labelX = goldX - 22 - c.width("보유 골드", 20, Face.LABEL);
        c.text("보유 골드", labelX, HEAD_Y, 20, WHITE, Face.LABEL, false);
        coin(c, labelX - 26, HEAD_Y, 30);
        fitted(c, m.title(), CX + 50, HEAD_Y, 40, labelX - 60 - (CX + 50), GOLD, Face.SECTION, false);

        // search · tabs · sort
        box(c, SEARCH_X, BAR_Y, SEARCH_W, BAR_H, FIELD, hover(m, searchBox()) ? LINE_HOVER : LINE, 1.5f);
        icon(c, OLD + "search.png", SEARCH_X + 30, BAR_Y + BAR_H / 2, 30, 128, 128);
        c.searchField(SEARCH_X + 52, BAR_Y + 4, SEARCH_W - 64, BAR_H - 8);
        String[] tabs = {"전체", "구매", "판매"};
        for (int i = 0; i < 3; i++) {
            float[] b = {tabX(i), BAR_Y, TAB_W, BAR_H};
            boolean sel = m.tab().ordinal() == i, ok = m.tabEnabled()[i];
            secondary(c, m, b, tabs[i], sel, ok);
        }
        c.fill(DIVIDER_X, BAR_Y + 4, 1.5f, BAR_H - 8, LINE);
        float[] sb = sortBox();
        box(c, sb[0], sb[1], sb[2], sb[3], FIELD, m.sortOpen() || hover(m, sb) ? LINE_HOVER : LINE, 1.5f);
        fitted(c, m.sort().label, sb[0] + 18, BAR_Y + BAR_H / 2, 19, SORT_W - 56, WHITE, Face.LABEL, false);
        chevron(c, sb[0] + sb[2] - 24, BAR_Y + BAR_H / 2, m.sortOpen(), GOLD);

        // catalogue: 4 x 3 cards, one page
        int from = m.page() * PER_PAGE;
        for (int s = 0; s < PER_PAGE && from + s < m.rows().size(); s++) card(c, m, m.rows().get(from + s), s);
        if (m.rows().isEmpty()) c.text(m.empty(), (CX + CR) / 2, (GRID_Y + gridBottom()) / 2, 24, MUTED, Face.LABEL, true);
        pager(c, m);

        footer(c, m);

        if (m.sortOpen()) {
            c.pushLayer(300);
            for (int i = 0; i < Sort.values().length; i++) {
                float[] b = sortOption(i);
                boolean on = m.sort().ordinal() == i, h = hover(m, b);
                box(c, b[0], b[1], b[2], b[3], h ? 0xF2182E4A : 0xF20C1A2C, on ? LINE_SELECTED : LINE_HOVER, 1.5f);
                fitted(c, Sort.values()[i].label, b[0] + 18, b[1] + b[3] / 2, 18, b[2] - 30, on ? GOLD : WHITE, Face.LABEL, false);
            }
            c.popLayer();
        }
        if (!m.notice().isEmpty() && m.noticeAlpha() > 0) {
            c.pushLayer(400);
            float y = gridBottom() - 70, w = 760, x = (CX + CR) / 2 - w / 2;
            int a = Math.round(255 * Math.min(1, m.noticeAlpha()));
            box(c, x, y, w, 52, (Math.round(a * .94f) << 24) | 0x0C1A2C, (a << 24) | (LINE_HOVER & 0xFFFFFF), 1.5f);
            fitted(c, m.notice(), x + w / 2, y + 26, 21, w - 50, (a << 24) | (WHITE & 0xFFFFFF), Face.LABEL, true);
            c.popLayer();
        }
    }

    /** Title emblem from the reference: four long points, four short diagonal points, a light core. */
    static void compass(Canvas c, float cx, float cy, float r) {
        float d = r * .42f;
        c.line(cx - d, cy - d, cx + d, cy + d, 2.2f, 0xCCE7CE91);
        c.line(cx - d, cy + d, cx + d, cy - d, 2.2f, 0xCCE7CE91);
        c.diamond(cx, cy, r * .2f, r, GOLD);
        c.diamond(cx, cy, r, r * .2f, GOLD);
        c.diamond(cx, cy, r * .09f, r * .55f, 0xFFFFF4D6);
        c.diamond(cx, cy, r * .55f, r * .09f, 0xFFFFF4D6);
    }
    static void chevron(Canvas c, float cx, float cy, boolean up, int color) {
        float d = up ? -1 : 1;
        c.line(cx - 7, cy - 3.5f * d, cx, cy + 3.5f * d, 2, color);
        c.line(cx, cy + 3.5f * d, cx + 7, cy - 3.5f * d, 2, color);
    }

    /** Tabs and the footer switch: gold when selected, navy outline otherwise. */
    static void secondary(Canvas c, Model m, float[] b, String label, boolean selected, boolean ok) {
        boolean h = ok && hover(m, b) && !m.sortOpen();
        if (selected) {
            box(c, b[0], b[1], b[2], b[3], h ? 0xFFE8CB88 : TAB_GOLD, TAB_GOLD_EDGE, 1.5f);
            outline(c, b[0] + 3, b[1] + 3, b[2] - 6, b[3] - 6, 1, 0x66FFF4D6);
        } else box(c, b[0], b[1], b[2], b[3], h ? CARD_HOVER : FIELD, h ? LINE_HOVER : LINE, 1.5f);
        c.text(label, b[0] + b[2] / 2, b[1] + b[3] / 2, 20, !ok ? DISABLED : selected ? DARK : WHITE, Face.BOLD, true);
    }

    static void card(Canvas c, Model m, Row r, int slot) {
        float x = cardX(slot), y = cardY(slot);
        boolean selected = r.id().equals(m.selected()), h = !m.sortOpen() && in(m.mouseX(), m.mouseY(), x, y, CARD_W, CARD_H);
        box(c, x, y, CARD_W, CARD_H, h ? CARD_HOVER : CARD, selected ? LINE_SELECTED : h ? LINE_HOVER : LINE, selected ? 2.5f : 1.5f);
        if (selected) outline(c, x + 4, y + 4, CARD_W - 8, CARD_H - 8, 3, GLOW);
        float[] ib = iconBox(slot);
        c.item(r, ib[0], ib[1], ib[2]);
        fitted(c, r.name(), x + CARD_W / 2, y + 118, 21, CARD_W - 24, WHITE, Face.BOLD, true);
        boolean sells = cardSells(m, r);
        String v = money(sells ? r.sell() : r.buy()) + " G";
        String tag = sells && m.tab() != Tab.SELL ? "판매 " : "";
        float size = shrink(c, v, 21, Face.BOLD, CARD_W - 80);
        float tw = c.width(tag, 16, Face.LABEL), vw = c.width(v, size, Face.BOLD), total = 26 + tw + vw;
        float left = x + CARD_W / 2 - total / 2, cy = y + 145;
        coin(c, left + 10, cy, 20);
        if (!tag.isEmpty()) c.text(tag, left + 26, cy, 16, CYAN, Face.LABEL, false);
        c.text(v, left + 26 + tw, cy, size, WHITE, Face.BOLD, false);
        if (sells) {
            String owned = "보유 " + money(Integer.toString(r.owned()));
            c.text(owned, x + CARD_W - 12 - c.width(owned, 15, Face.LABEL), y + 18, 15, MUTED, Face.LABEL, false);
        }
    }

    static void pager(Canvas c, Model m) {
        float mid = (CX + CR) / 2;
        boolean prev = m.page() > 0, next = m.page() < m.pages() - 1;
        float[] p = prevBox(), n = nextBox();
        int pc = !prev ? 0x55E7CE91 : hover(m, p) ? CYAN : GOLD, nc = !next ? 0x55E7CE91 : hover(m, n) ? CYAN : GOLD;
        float pcx = p[0] + p[2] / 2, ncx = n[0] + n[2] / 2;
        c.line(pcx + 4, PAGER_Y - 9, pcx - 5, PAGER_Y, 2.4f, pc);
        c.line(pcx - 5, PAGER_Y, pcx + 4, PAGER_Y + 9, 2.4f, pc);
        c.line(ncx - 4, PAGER_Y - 9, ncx + 5, PAGER_Y, 2.4f, nc);
        c.line(ncx + 5, PAGER_Y, ncx - 4, PAGER_Y + 9, 2.4f, nc);
        c.text((m.page() + 1) + " / " + m.pages(), mid, PAGER_Y, 19, WHITE, Face.LABEL, true);
    }

    static void footer(Canvas c, Model m) {
        float cy = FOOT_CY;
        Row r = m.chosen();
        if (r != null) {
            box(c, F_ICON_X, cy - F_ICON / 2, F_ICON, F_ICON, CARD, LINE, 1.5f);
            c.item(r, F_ICON_X + 9, cy - F_ICON / 2 + 9, F_ICON - 18);
            boolean lore = !r.lore().isEmpty();
            fitted(c, r.name(), F_TEXT_X, cy - (lore ? 16 : 0), 26, F_TEXT_MAX, WHITE, Face.BOLD, false);
            if (lore) fitted(c, r.lore().getFirst(), F_TEXT_X, cy + 20, 17, F_TEXT_MAX, MUTED, Face.BODY, false);
            c.text("구매", F_LABEL_X, cy - 18, 18, MUTED, Face.LABEL, false);
            c.text("판매", F_LABEL_X, cy + 18, 18, MUTED, Face.LABEL, false);
            footPrice(c, r.buys() ? money(r.buy()) + " G" : "—", cy - 18);
            footPrice(c, r.sells() ? money(r.sell()) + " G" : "—", cy + 18);
        } else c.text(m.rows().isEmpty() ? "" : "상품을 골라 주세요", F_TEXT_X, cy, 20, MUTED, Face.LABEL, false);
        c.fill(F_DIV1, cy - 30, 1.5f, 60, LINE);
        c.fill(F_DIV2, cy - 30, 1.5f, 60, LINE);

        c.text("수량", QTY_LABEL_X, cy, 20, WHITE, Face.LABEL, false);
        box(c, QTY_X, qtyY(), QTY_W, QTY_H, FIELD, LINE, 1.5f);
        step(c, m, minusBox(), "−", m.minusOk());
        step(c, m, plusBox(), "+", m.plusOk());
        float[] q = quantityBox();
        c.quantityField(q[0], q[1], q[2], q[3]);
        c.fill(F_DIV3, cy - 30, 1.5f, 60, LINE);

        c.text("합계", SUM_X, cy - 20, 18, GOLD, Face.LABEL, false);
        coin(c, SUM_X + 13, cy + 15, 26);
        String sum = m.total().isEmpty() ? "—" : money(m.total()) + " G";
        c.text(sum, SUM_X + 34, cy + 15, shrink(c, sum, 28, Face.BOLD, SUM_MAX - 34), GOLD, Face.BOLD, false);

        // switch: the other direction for the chosen product (판매 while buying, 구매 while selling)
        float[] sw = switchBox();
        secondary(c, m, sw, m.selling() ? "구매" : "판매", false, m.switchOk() && !m.busy());
        float[] b = tradeBox();
        boolean ok = m.tradeOk() && !m.busy(), h = ok && hover(m, b);
        int tint = !ok ? 0xFF7C7C7C : h && m.mouseDown() ? 0xFFD2D2D2 : h ? 0xFFFFFFFF : 0xFFEDEDED;
        c.image(ROOT + "button_primary.png", b[0], b[1], b[2], b[3], 38, 23, 2131, 633, 2206, 713, tint);
        String label = m.busy() ? "처리 중…" : m.selling() ? "판매하기" : "구매하기";
        c.text(label, b[0] + b[2] / 2, b[1] + b[3] / 2, 24, ok ? DARK : DISABLED_ON_GOLD, Face.SECTION, true);
    }
    static void footPrice(Canvas c, String v, float cy) {
        float size = shrink(c, v, 21, Face.BOLD, F_VALUE_RIGHT - F_LABEL_X - 50);
        c.text(v, F_VALUE_RIGHT - c.width(v, size, Face.BOLD), cy, size, WHITE, Face.BOLD, false);
    }
    static void step(Canvas c, Model m, float[] b, String glyph, boolean ok) {
        ok = ok && !m.busy();
        boolean h = ok && hover(m, b);
        box(c, b[0], b[1], b[2], b[3], h ? CARD_HOVER : 0x00000000, h ? LINE_HOVER : LINE, 1.5f);
        c.text(glyph, b[0] + b[2] / 2, b[1] + b[3] / 2, 26, ok ? WHITE : DISABLED, Face.BOLD, true);
    }

    // ---- input ----------------------------------------------------------------------------------------------
    static Hit hit(Model m, double x, double y) {
        if (m.sortOpen()) {
            for (int i = 0; i < Sort.values().length; i++) if (in(x, y, sortOption(i))) return new Hit(Kind.SORT_OPTION, i);
            if (in(x, y, sortBox())) return new Hit(Kind.SORT, 0);
            return Hit.NONE;                       // any other click just closes the list
        }
        for (int i = 0; i < 3; i++) if (in(x, y, tabX(i), BAR_Y, TAB_W, BAR_H)) return new Hit(Kind.TAB, i);
        if (in(x, y, searchBox())) return new Hit(Kind.SEARCH, 0);
        if (in(x, y, sortBox())) return new Hit(Kind.SORT, 0);
        int from = m.page() * PER_PAGE;
        for (int s = 0; s < PER_PAGE && from + s < m.rows().size(); s++) {
            if (in(x, y, iconBox(s))) return new Hit(Kind.ICON, from + s);
            if (in(x, y, cardX(s), cardY(s), CARD_W, CARD_H)) return new Hit(Kind.CARD, from + s);
        }
        if (in(x, y, prevBox())) return new Hit(Kind.PREV, 0);
        if (in(x, y, nextBox())) return new Hit(Kind.NEXT, 0);
        if (in(x, y, minusBox())) return new Hit(Kind.MINUS, 0);
        if (in(x, y, plusBox())) return new Hit(Kind.PLUS, 0);
        if (in(x, y, quantityBox())) return new Hit(Kind.QUANTITY, 0);
        if (in(x, y, switchBox())) return new Hit(Kind.SWITCH, 0);
        if (in(x, y, tradeBox())) return new Hit(Kind.TRADE, 0);
        return Hit.NONE;
    }
}
