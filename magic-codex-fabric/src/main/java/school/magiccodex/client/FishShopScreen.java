package school.magiccodex.client;

import java.io.InputStream;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import school.magiccodex.protocol.FishShopProtocol;
import school.magiccodex.protocol.FishShopProtocol.*;

/**
 * 물고기 판매소 window (2026-10-20, GPT's approved sale / lock references and PNG set textures/gui/fish_shop).
 * Geometry is the reference's guides/layout_1672x941.json. Left: the NPC portrait (shop portrait setting, like the
 * normal shop) and the nameplate. Right: the frame with two tabs.
 *  판매     four grade rows with check boxes; the footer sells the checked grades (전체 판매 / 선택 판매).
 *  잠금 설정 all species as cards; clicking toggles the lock, 적용 saves (locked species are never sold).
 * Text is drawn with the MagicCodex fonts, never baked into the PNGs.
 */
final class FishShopScreen extends Screen {
    static final String ROOT = "textures/gui/fish_shop/";
    static final Identifier SECTION = Identifier.of("magiccodex", "section"), BODY = Identifier.of("magiccodex", "body"),
            BOLD = Identifier.of("magiccodex", "hud_bold"), LABEL = Identifier.of("magiccodex", "label");
    static final int CREAM = 0xFFF4E7C6, GOLD = 0xFFE9C878, TEXT = 0xFFE4E9EF, MUTED = 0xFFA9B6C8, DIM = 0xFF6E7D93, DARK = 0xFF3A2A10, WARN = 0xFFFF9E9E;
    static final int[] GRADE = FishingArt.GRADE;
    static final String[] GRADES = {"일반", "고급", "유일", "전설"};
    private static final Map<Identifier, float[]> SIZES = new HashMap<>();

    // ---- layout (guides/layout_1672x941.json)
    static final float FRAME_X = 594, FRAME_Y = 31, FRAME_W = 1034, FRAME_H = 877;
    static final float TAB_Y = 190, TAB_W = 178, TAB_H = 47, TAB_SALE_X = 628, TAB_LOCK_X = 814;
    static final float CLOSE_X = 1550, CLOSE_Y = 101, CLOSE_S = 40;
    static final float BAL_X = 1110, BAL_W = 482;
    static final float ALL_X = 1210, CLEAR_X = 1403, BTN_Y = 250, BTN_W = 182, BTN_H = 45;
    static final float ROW_X = 622, ROW_Y = 311, ROW_W = 970, ROW_H = 116, ROW_PITCH = 123;
    static final float FOOT_X = 621, FOOT_Y = 817, FOOT_W = 973, FOOT_H = 78;
    static final float SEARCH_X = 627, SEARCH_Y = 251, SEARCH_W = 714, SEARCH_H = 53, FILTER_X = 1355, FILTER_W = 235;
    static final float GRID_X = 622, GRID_Y = 343, CARD_W = 232, CARD_H = 142, PITCH_X = 243, PITCH_Y = 151;
    static final int COLS = 4, ROWS = 3;
    static final float BAR_X = 1601, BAR_Y = 342, BAR_H = 445;
    static final float PLATE_X = 78, PLATE_Y = 727, PLATE_W = 438, PLATE_H = 438f * 794 / 1707;

    private final UiResources.Entrance entrance = new UiResources.Entrance();
    private State state;
    private List<Species> species = List.of();
    private boolean lockTab, lockedOnly, busy;
    private final boolean[] checked = {true, true, true, true};
    private final Set<String> pending = new HashSet<>();     // lock tab edits until 적용
    private boolean dirty;
    private int scroll;
    private TextFieldWidget search;
    private String query = "", notice = "";
    private long noticeAt, pendingOp, pendingAt;
    private boolean mouseDown;

    FishShopScreen() { super(Text.literal("물고기 판매소")); }

    @Override protected void init() {
        search = new TextFieldWidget(textRenderer, 0, 0, 300, 20, Text.literal("물고기 이름 검색"));
        search.setMaxLength(16); search.setText(query); search.setDrawsBackground(false);
        search.setChangedListener(v -> { query = v; scroll = 0; });
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {}
    @Override public void removed() { FishShopClient.close(); }

    void onState(State s) {
        state = s;
        if (!s.species().isEmpty()) species = s.species();
        if (!dirty) { pending.clear(); pending.addAll(s.locks()); }
        if (!s.notice().isBlank()) say(s.notice());
    }
    void onResult(Result r) {
        if (r.operation() != pendingOp) return;
        busy = false; pendingOp = 0;
        say(r.notice());
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(r.ok() ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), r.ok() ? 1.2f : .7f, .5f));
    }
    private void say(String s) { notice = s; noticeAt = Util.getMeasuringTimeMs(); }

    @Override public void tick() {
        if (busy && Util.getMeasuringTimeMs() - pendingAt > 10000) { busy = false; pendingOp = 0; say("서버 응답이 늦어요. 다시 시도해 주세요."); FishShopClient.query(); }
    }

    // ------------------------------------------------------------------ model

    private int selectedCount() { int n = 0; if (state != null) for (int g = 0; g < 4; g++) if (checked[g]) n += state.grades().get(g).count(); return n; }
    private long selectedTotal() { long t = 0; if (state != null) for (int g = 0; g < 4; g++) if (checked[g]) t += state.grades().get(g).total(); return t; }
    private int mask() { int m = 0; for (int g = 0; g < 4; g++) if (checked[g]) m |= 1 << g; return m; }
    private boolean allChecked() { return checked[0] && checked[1] && checked[2] && checked[3]; }

    private List<Species> cards() {
        String q = query.strip();
        var out = new ArrayList<Species>();
        for (var s : species) {
            if (!q.isEmpty() && !s.name().contains(q)) continue;
            if (lockedOnly && !pending.contains(s.id())) continue;
            out.add(s);
        }
        return out;
    }
    private int maxScroll() { return Math.max(0, (cards().size() + COLS - 1) / COLS - ROWS); }

    static String money(long v) { return String.format(Locale.ROOT, "%,d", v); }
    static String money(String raw) {
        try { return String.format(Locale.ROOT, "%,d", new java.math.BigDecimal(raw).setScale(0, java.math.RoundingMode.DOWN).longValueExact()); }
        catch (Exception e) { return raw; }
    }

    // ------------------------------------------------------------------ input

    private CodexLayout layout() { return CodexLayout.fit(width, height); }
    private static boolean in(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void click() { if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, .25f)); }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseDown = true;
        var l = layout();
        double mx = l.localX(mouseX), my = l.localY(mouseY);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (in(mx, my, CLOSE_X, CLOSE_Y, CLOSE_S, CLOSE_S)) { close(); return true; }
        if (in(mx, my, TAB_SALE_X, TAB_Y, TAB_W, TAB_H) && lockTab) { lockTab = false; search.setFocused(false); click(); return true; }
        if (in(mx, my, TAB_LOCK_X, TAB_Y, TAB_W, TAB_H) && !lockTab) { lockTab = true; scroll = 0; click(); return true; }
        if (!lockTab) {
            if (in(mx, my, ALL_X, BTN_Y, BTN_W, BTN_H)) { Arrays.fill(checked, true); click(); return true; }
            if (in(mx, my, CLEAR_X, BTN_Y, BTN_W, BTN_H)) { Arrays.fill(checked, false); click(); return true; }
            for (int g = 0; g < 4; g++) if (in(mx, my, ROW_X, ROW_Y + g * ROW_PITCH, ROW_W, ROW_H)) { checked[g] = !checked[g]; click(); return true; }
            if (in(mx, my, 1322, 830, 250, 52)) { sell(); return true; }
        } else {
            boolean searchHit = in(mx, my, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H);
            search.setFocused(searchHit);
            if (searchHit) return true;
            if (in(mx, my, FILTER_X, SEARCH_Y, FILTER_W, SEARCH_H)) { lockedOnly = !lockedOnly; scroll = 0; click(); return true; }
            var list = cards();
            for (int i = 0; i < COLS * ROWS; i++) {
                int index = scroll * COLS + i;
                if (index >= list.size()) break;
                float x = GRID_X + (i % COLS) * PITCH_X, y = GRID_Y + (i / COLS) * PITCH_Y;
                if (in(mx, my, x, y, CARD_W, CARD_H)) {
                    String id = list.get(index).id();
                    if (!pending.remove(id)) pending.add(id);
                    dirty = true; click(); return true;
                }
            }
            if (in(mx, my, BAR_X - 8, BAR_Y, 20, BAR_H)) { scroll = Math.round((float) ((my - BAR_Y) / BAR_H) * maxScroll()); return true; }
            if (in(mx, my, 1196, 833, 176, 50)) { close(); return true; }
            if (in(mx, my, 1392, 833, 180, 50)) {
                FishShopClient.locks(pending); dirty = false; click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { mouseDown = false; return super.mouseReleased(mouseX, mouseY, button); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (lockTab) scroll = Math.clamp(scroll - (int) Math.signum(v), 0, maxScroll());
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(key, scan, mods);
        if (search.isFocused()) { search.setFocused(false); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char chr, int mods) { return search.isFocused() ? search.charTyped(chr, mods) : super.charTyped(chr, mods); }

    private void sell() {
        if (busy || state == null) return;
        if (selectedCount() == 0) { say("판매할 물고기가 없어요."); return; }
        long op = FishShopClient.sell(mask(), selectedTotal());
        if (op == 0) { say("서버에 연결되어 있지 않아요."); return; }
        busy = true; pendingOp = op; pendingAt = Util.getMeasuringTimeMs();
        click();
    }

    // ------------------------------------------------------------------ drawing helpers

    private void image(DrawContext c, String name, float x, float y, float w, float h, int u, int v, int sw, int sh, int tw, int th, int tint) {
        int x0 = Math.round(x), y0 = Math.round(y);
        int w0 = Math.round(x + w) - x0, h0 = Math.round(y + h) - y0;
        if (w0 <= 0 || h0 <= 0 || sw <= 0 || sh <= 0) return;
        UiResources.images().drawTexture(c, Identifier.of("magiccodex", ROOT + name + ".png"), x0, y0, u, v, w0, h0, sw, sh, tw, th, tint);
    }
    private void whole(DrawContext c, String name, int tw, int th, float x, float y, float w, float h, int tint) {
        image(c, name, x, y, w, h, 0, 0, tw, th, tw, th, tint);
    }
    /** Nine-slice: source insets [l,t,r,b] drawn at scale min(w/tw, h/th) so ornaments keep their shape. */
    private void nine(DrawContext c, String name, int tw, int th, int[] in, float x, float y, float w, float h, int tint) {
        float k = Math.min(w / tw, h / th);
        float l = in[0] * k, t = in[1] * k, r = in[2] * k, b = in[3] * k;
        int[] sx = {0, in[0], tw - in[2], tw}; int[] sy = {0, in[1], th - in[3], th};
        float[] dx = {x, x + l, x + w - r, x + w}; float[] dy = {y, y + t, y + h - b, y + h};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            image(c, name, dx[col], dy[row], dx[col + 1] - dx[col], dy[row + 1] - dy[row], sx[col], sy[row], sx[col + 1] - sx[col], sy[row + 1] - sy[row], tw, th, tint);
    }
    private static void text(DrawContext c, String v, float x, float y, float size, int color, Identifier face, boolean centered) {
        if (v != null && !v.isBlank()) UiResources.text().draw(c, v, x, y, size, color, face, centered);
    }
    private static float width(String v, float size, Identifier face) { return UiResources.text().width(v, size, face); }
    private static void right(DrawContext c, String v, float right, float y, float size, int color, Identifier face) {
        text(c, v, right - width(v, size, face), y, size, color, face, false);
    }
    private static float fit(String v, float size, Identifier face, float max) { float w = width(v, size, face); return w <= max ? size : Math.max(10, size * max / w); }
    private static int[] i(int... v) { return v; }

    private String hover(double mx, double my, float x, float y, float w, float h) {
        return in(mx, my, x, y, w, h) ? mouseDown ? "_pressed" : "_hover" : "";
    }
    private void goldButton(DrawContext c, double mx, double my, float x, float y, float w, float h, String label, boolean enabled) {
        String s = enabled ? hover(mx, my, x, y, w, h) : "_disabled";
        nine(c, "gold_button" + s, 1793, 468, i(180, 85, 180, 85), x, y, w, h, 0xFFFFFFFF);
        text(c, label, x + w / 2, y + h / 2 + 1, fit(label, 21, SECTION, w - 60), enabled ? DARK : 0xFF6A5A40, SECTION, true);
    }
    private void navyButton(DrawContext c, double mx, double my, float x, float y, float w, float h, String label) {
        nine(c, "navy_button" + hover(mx, my, x, y, w, h), 712, 672, i(85, 85, 85, 85), x, y, w, h, 0xFFFFFFFF);
        text(c, label, x + w / 2, y + h / 2 + 1, fit(label, 21, SECTION, w - 30), CREAM, SECTION, true);
    }

    // ------------------------------------------------------------------ render

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        var l = layout();
        double mx = l.localX(mouseX), my = l.localY(mouseY);
        var images = UiResources.images(); var type = UiResources.text();
        images.beginFrame(); type.beginFrame();
        c.fill(0, 0, width, height, 0x66050A12);
        var m = c.getMatrices(); m.push();
        try {
            m.translate(l.x(), l.y(), 0); m.scale(l.scale(), l.scale(), 1);
            npc(c, (float) l.localY(height));
            whole(c, "shop_frame_full", 1318, 1118, FRAME_X, FRAME_Y, FRAME_W, FRAME_H, 0xFFFFFFFF);
            text(c, state == null ? "물고기 판매소" : state.title(), 1105, 131, fit(state == null ? "물고기 판매소" : state.title(), 48, SECTION, 330), CREAM, SECTION, true);
            whole(c, "close_button" + hover(mx, my, CLOSE_X, CLOSE_Y, CLOSE_S, CLOSE_S), 39, 39, CLOSE_X, CLOSE_Y, CLOSE_S, CLOSE_S, 0xFFFFFFFF);
            tab(c, mx, my, TAB_SALE_X, "판매", !lockTab);
            tab(c, mx, my, TAB_LOCK_X, "잠금 설정", lockTab);
            if (state == null) text(c, "불러오는 중…", 1105, 520, 22, MUTED, BODY, true);
            else if (lockTab) lockTab(c, mx, my);
            else saleTab(c, mx, my);
            long age = Util.getMeasuringTimeMs() - noticeAt;
            if (!notice.isBlank() && age < 4500) {
                float a = age > 4000 ? (4500 - age) / 500f : 1;
                float s = fit(notice, 18, BODY, 900), w = width(notice, s, BODY) + 40;
                FishingArt.card(c, 1105 - w / 2, 772, w, 36, FishingArt.argb(.85f * a, 0x0A1428), FishingArt.argb(.7f * a, 0xE9C878), 1.2f);
                text(c, notice, 1105, 790, s, FishingArt.argb(a, 0xF4E7C6), BODY, true);
            }
        } finally { m.pop(); images.endFrame(); }
        entrance.draw(c, width, height);
    }

    private void tab(DrawContext c, double mx, double my, float x, String label, boolean selected) {
        String s = hover(mx, my, x, TAB_Y, TAB_W, TAB_H);
        if (selected) nine(c, "tab_selected" + s, 190, 64, i(21, 21, 21, 21), x, TAB_Y, TAB_W, TAB_H, 0xFFFFFFFF);
        else nine(c, "tab_idle" + s, 180, 62, i(20, 20, 20, 20), x, TAB_Y, TAB_W, TAB_H, 0xFFFFFFFF);
        text(c, label, x + TAB_W / 2, TAB_Y + TAB_H / 2 + 1, 22, selected ? DARK : CREAM, SECTION, true);
    }

    private void npc(DrawContext c, float screenBottom) {
        Identifier id = portrait();
        if (id != null) {
            float[] s = size(id);
            float aspect = s[0] / s[1];
            float h = Math.min(1000, 640 / Math.max(.1f, aspect)), w = h * aspect;
            float bottom = Math.max(941, screenBottom);
            int x0 = Math.round(300 - w / 2), y0 = Math.round(bottom - h);
            UiResources.images().drawTexture(c, id, x0, y0, 0, 0, Math.round(w), Math.round(h), (int) s[0], (int) s[1], (int) s[0], (int) s[1], 0xFFFFFFFF);
        }
        whole(c, "npc_nameplate", 1707, 794, PLATE_X, PLATE_Y, PLATE_W, PLATE_H, 0xFFFFFFFF);
        float cx = PLATE_X + PLATE_W / 2;
        String role = state == null ? "물고기 매입상" : state.role(), name = state == null ? "" : state.npcName();
        // role between the top star's tip and the divider line, the name under the line (reference nameplate)
        text(c, role, cx, PLATE_Y + 86, fit(role, 18, LABEL, 230), GOLD, LABEL, true);
        text(c, name, cx, PLATE_Y + 126, fit(name, 40, SECTION, 270), 0xFFFFFFFF, SECTION, true);
    }
    private Identifier portrait() {
        if (state == null || state.portrait().isBlank() || client == null) return null;
        var id = Identifier.tryParse("magiccodex:textures/gui/dialogue/" + state.portrait() + ".png");
        return id != null && client.getResourceManager().getResource(id).isPresent() ? id : null;
    }
    private float[] size(Identifier id) {
        return SIZES.computeIfAbsent(id, k -> {
            try (InputStream in = client.getResourceManager().getResourceOrThrow(k).getInputStream()) {
                byte[] h = in.readNBytes(24);
                if (h.length < 24 || h[12] != 'I' || h[13] != 'H' || h[14] != 'D' || h[15] != 'R') return new float[]{1024, 1536};
                int w = ((h[16] & 255) << 24) | ((h[17] & 255) << 16) | ((h[18] & 255) << 8) | (h[19] & 255);
                int t = ((h[20] & 255) << 24) | ((h[21] & 255) << 16) | ((h[22] & 255) << 8) | (h[23] & 255);
                return w > 0 && t > 0 ? new float[]{w, t} : new float[]{1024, 1536};
            } catch (Exception e) { return new float[]{1024, 1536}; }
        });
    }

    // ---- 판매

    private void saleTab(DrawContext c, double mx, double my) {
        nine(c, "balance_field", 1958, 262, i(90, 55, 90, 55), BAL_X, TAB_Y, BAL_W, TAB_H, 0xFFFFFFFF);
        whole(c, "coin", 75, 77, BAL_X + 22, TAB_Y + 9, 29, 30, 0xFFFFFFFF);
        text(c, "보유 골드", BAL_X + 62, TAB_Y + TAB_H / 2 + 1, 19, CREAM, BODY, false);
        right(c, state.balance().isBlank() ? "-" : money(state.balance()) + " G", BAL_X + BAL_W - 26, TAB_Y + TAB_H / 2 + 1, 21, CREAM, BOLD);
        text(c, "등급을 선택해 한 번에 판매하세요", 636, 276, 19, TEXT, BODY, false);
        int locks = state.locks().size();
        if (locks > 0) {
            whole(c, "lock_closed_gold", 128, 160, 1020, 262, 16, 20, 0xFFFFFFFF);
            text(c, "잠금 " + locks + "종 제외", 1042, 274, 17, 0xFFD9C8A0, BODY, false);
        }
        goldButton(c, mx, my, ALL_X, BTN_Y, BTN_W, BTN_H, "전체 선택", true);
        navyButton(c, mx, my, CLEAR_X, BTN_Y, BTN_W, BTN_H, "선택 해제");
        for (int g = 0; g < 4; g++) row(c, mx, my, g);
        // footer
        nine(c, "footer_panel", 1328, 154, i(60, 45, 60, 45), FOOT_X, FOOT_Y, FOOT_W, FOOT_H, 0xFFFFFFFF);
        float cy = FOOT_Y + FOOT_H / 2 + 1;
        text(c, "선택", 649, cy, 19, TEXT, BODY, false);
        text(c, selectedCount() + "마리", 712, cy, 28, CREAM, SECTION, false);
        FishingArt.vline(c, 862, FOOT_Y + 18, FOOT_Y + FOOT_H - 18, 0x60E9C878);
        text(c, "예상 판매금", 885, cy, 19, TEXT, BODY, false);
        whole(c, "coin", 75, 77, 1040, cy - 15, 29, 30, 0xFFFFFFFF);
        text(c, money(selectedTotal()) + " G", 1082, cy, fit(money(selectedTotal()) + " G", 28, SECTION, 220), GOLD, SECTION, false);
        int count = selectedCount();
        goldButton(c, mx, my, 1322, 830, 250, 52, busy ? "판매 중…" : allChecked() ? "전체 판매" : "선택 판매", count > 0 && !busy);
    }

    private void row(DrawContext c, double mx, double my, int g) {
        float y = ROW_Y + g * ROW_PITCH;
        var grade = state.grades().get(g);
        boolean over = in(mx, my, ROW_X, y, ROW_W, ROW_H);
        String panel = checked[g] ? "item_row_selected" : over ? "item_row_hover" : "item_row";
        nine(c, panel, 2080, 306, i(65, 60, 65, 60), ROW_X, y, ROW_W, ROW_H, 0xFFFFFFFF);
        // fish of this grade (the most valuable one in the tank), clipped to the row
        float fx = ROW_X + 150, fy = y + ROW_H / 2;
        var art = grade.sample().isEmpty() ? null : FishingArt.fish(grade.sample());
        c.enableScissor(Math.round(ROW_X + 8), Math.round(y + 6), Math.round(ROW_X + 300), Math.round(y + ROW_H - 6));
        try {
            if (art != null) FishingArt.drawCentered(c, art, fx, fy, 210, 210, 0xFFFFFFFF, false, 0, false);
            else FishingArt.drawCentered(c, FishingArt.SHAPES[0], fx, fy, 170, 170, 0x55000000, false, 0, false);
        } finally { c.disableScissor(); }
        text(c, GRADES[g], ROW_X + 310, y + 40, 28, GRADE[g], SECTION, false);
        text(c, "판매 가능 " + grade.count() + "마리", ROW_X + 312, y + 78, 18, TEXT, BODY, false);
        whole(c, "coin", 75, 77, ROW_X + 630, y + ROW_H / 2 - 13, 26, 27, grade.count() > 0 ? 0xFFFFFFFF : 0xFF8890A0);
        right(c, money(grade.total()) + " G", ROW_X + 860, y + ROW_H / 2 + 6, 24, grade.count() > 0 ? CREAM : DIM, SECTION);
        float bx = ROW_X + 900, by = y + ROW_H / 2 - 23;
        whole(c, checked[g] ? "checkbox_frame_selected" : "checkbox_frame_empty", 56, 58, bx, by, 45, 47, 0xFFFFFFFF);
        if (checked[g]) whole(c, "check_selected_gold", 160, 144, bx + 4, by + 5, 37, 34, 0xFFFFFFFF);
    }

    // ---- 잠금 설정

    private void lockTab(DrawContext c, double mx, double my) {
        right(c, "잠금 " + pending.size() + "종", 1582, 214, 20, GOLD, SECTION);
        nine(c, "search_field", 2039, 266, i(75, 55, 75, 55), SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, 0xFFFFFFFF);
        whole(c, "search", 128, 128, SEARCH_X + 22, SEARCH_Y + 13, 27, 27, 0xFFFFFFFF);
        boolean focused = search.isFocused();
        String shown = query.isEmpty() && !focused ? "물고기 이름 검색" : query + (focused && Util.getMeasuringTimeMs() / 500 % 2 == 0 ? "|" : "");
        text(c, shown, SEARCH_X + 62, SEARCH_Y + SEARCH_H / 2 + 1, 20, query.isEmpty() && !focused ? DIM : TEXT, BODY, false);
        nine(c, "search_field", 2039, 266, i(75, 55, 75, 55), FILTER_X, SEARCH_Y, FILTER_W, SEARCH_H, 0xFFFFFFFF);
        float tx = FILTER_X + 14, ty = SEARCH_Y + 7;
        whole(c, lockedOnly ? "filter_toggle_track_on" : "filter_toggle_track_off", 78, 40, tx, ty, 78, 40, 0xFFFFFFFF);
        whole(c, "filter_toggle_knob", 40, 40, lockedOnly ? tx + 38 : tx, ty, 40, 40, 0xFFFFFFFF);
        text(c, "잠금만 보기", FILTER_X + 104, SEARCH_Y + SEARCH_H / 2 + 1, 20, CREAM, BODY, false);
        var list = cards();
        text(c, (lockedOnly ? "잠금 " : "전체 ") + list.size() + "종", 636, 328, 18, TEXT, BODY, false);
        FishingArt.hline(c, 780, 1578, 328, 0x40E9C878);
        scroll = Math.clamp(scroll, 0, maxScroll());
        for (int k = 0; k < COLS * ROWS; k++) {
            int index = scroll * COLS + k;
            if (index >= list.size()) break;
            card(c, mx, my, list.get(index), GRID_X + (k % COLS) * PITCH_X, GRID_Y + (k / COLS) * PITCH_Y);
        }
        if (list.isEmpty()) text(c, lockedOnly ? "잠긴 물고기가 없어요." : "검색 결과가 없어요.", 1105, 560, 20, MUTED, BODY, true);
        // scrollbar (code-drawn)
        FishingArt.rect(c, BAR_X, BAR_Y, 3, BAR_H, 0x40E9C878);
        int max = maxScroll();
        float thumb = Math.max(50, BAR_H * ROWS / Math.max(ROWS, (list.size() + COLS - 1) / COLS));
        float ty0 = BAR_Y + (max == 0 ? 0 : (BAR_H - thumb) * scroll / max);
        FishingArt.rect(c, BAR_X - 1.5f, ty0, 6, thumb, 0xC0E9C878);
        // footer
        nine(c, "footer_panel", 1328, 154, i(60, 45, 60, 45), FOOT_X, FOOT_Y, FOOT_W, FOOT_H, 0xFFFFFFFF);
        whole(c, "lock_closed_gold", 128, 160, 645, FOOT_Y + 26, 21, 27, 0xFFFFFFFF);
        text(c, dirty ? "바뀐 잠금은 「적용」을 눌러야 저장돼요" : "잠긴 종류는 판매에서 제외됩니다", 680, FOOT_Y + FOOT_H / 2 + 1, 19, dirty ? GOLD : TEXT, BODY, false);
        navyButton(c, mx, my, 1196, 833, 176, 50, "닫기");
        goldButton(c, mx, my, 1392, 833, 180, 50, "적용", dirty);
    }

    private void card(DrawContext c, double mx, double my, Species s, float x, float y) {
        boolean locked = pending.contains(s.id()), over = in(mx, my, x, y, CARD_W, CARD_H);
        nine(c, locked ? "species_card_locked" : over ? "species_card_hover" : "species_card_default", 960, 492, i(72, 72, 72, 72), x, y, CARD_W, CARD_H, 0xFFFFFFFF);
        var art = FishingArt.fish(s.id());
        c.enableScissor(Math.round(x + 4), Math.round(y + 4), Math.round(x + CARD_W - 4), Math.round(y + CARD_H - 4));
        try {
            if (art != null) FishingArt.drawCentered(c, art, x + CARD_W / 2, y + 50, 132, 132, 0xFFFFFFFF, false, 0, false);
            else FishingArt.drawCentered(c, FishingArt.SHAPES[0], x + CARD_W / 2, y + 50, 116, 116, 0x88000000, false, 0, false);
        } finally { c.disableScissor(); }
        text(c, s.name(), x + 12, y + 100, fit(s.name(), 20, SECTION, CARD_W - 100), TEXT, SECTION, false);
        text(c, GRADES[s.grade()], x + 13, y + 122, 12, GRADE[s.grade()], BODY, false);
        if (locked) {
            float bw = 72, bh = 30, bx = x + CARD_W - bw - 10, by = y + CARD_H - bh - 10;
            nine(c, "lock_button_frame_gold", 94, 54, i(9, 9, 9, 9), bx, by, bw, bh, 0xFFFFFFFF);
            whole(c, "lock_closed_gold", 128, 160, bx + 10, by + 5, 16, 20, 0xFFFFFFFF);
            text(c, "잠김", bx + 32, by + bh / 2 + 1, 15, GOLD, BOLD, false);
        } else {
            float b = 30, bx = x + CARD_W - b - 10, by = y + CARD_H - b - 10;
            nine(c, "lock_button_frame_neutral", 52, 54, i(9, 9, 9, 9), bx, by, b, b, 0xFFFFFFFF);
            whole(c, "lock_open_blue", 128, 160, bx + 7, by + 5, 16, 20, 0xFFFFFFFF);
        }
    }
}
