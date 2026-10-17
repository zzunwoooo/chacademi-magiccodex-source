package school.magiccodex.client.kui;

import java.util.List;
import school.magiccodex.client.kui.Canvas.Face;

/**
 * 마법 선택 창 (spell_select_popup; 1536×1024). Top: the six match slots (key under the icon, flight marked 고정).
 * Below: learned spells 8×3 per page (wheel scrolls rows). Click a slot, then a spell; drag slot onto slot to swap.
 */
public final class SpellPickView {
    private SpellPickView() {}
    public static final float W = 1536, H = 1024;
    public static final int COLS = 8, ROWS = 3;

    public record Cell(String id, String icon, String name, String detail) {}
    public record Data(List<Cell> equipped, List<String> keys, int flightSlot, List<Cell> catalog, int selected, int scrollRow,
                       int dragFrom, String notice) {}

    public static final String CLOSE = "close", CONFIRM = "confirm";
    public static String slotId(int i) { return "slot:" + i; }
    public static String spellId(int i) { return "spell:" + i; }

    public static float slotX(int i) { return 254 + i * 176; }
    public static final float SLOT_Y = 215;

    public static void paint(Pen p, Data d) {
        p.image("skills/panel", 120, 29, 1298, 951);
        p.text("마법 세팅", 768, 112, 40, Theme.GOLD, Face.TITLE, Canvas.CENTER);
        p.button(CLOSE, "skills/close", 1330, 68, 47, 45, true);
        for (int i = 0; i < 6; i++) {
            float x = slotX(i), y = SLOT_Y;
            boolean sel = i == d.selected();
            p.image(sel ? "skills/equipped_slot_selected" : "skills/equipped_slot", x, y, 151, 152);
            p.hit(slotId(i), x, y, 151, 152);
            Cell c = i < d.equipped().size() ? d.equipped().get(i) : null;
            if (c != null && c.icon() != null && i != d.dragFrom()) p.c.texture(c.icon(), x + 32.5f, y + 16, 86, 86, 1);
            String key = i < d.keys().size() ? d.keys().get(i) : "";
            if (!key.isEmpty()) p.text(key, x + 75.5f, y + 120, 16, Theme.CREAM, Face.SANS_BOLD, Canvas.CENTER);
            if (i == d.flightSlot()) p.text("고정", x + 128, y + 24, 14, Theme.TEAL, Face.SANS_BOLD, Canvas.RIGHT);
        }
        Cell hover = null; float hx = 0, hy = 0;
        for (int k = 0; k < COLS * ROWS; k++) {
            int idx = (d.scrollRow() + k / COLS) * COLS + k % COLS;
            float x = 174 + (k % COLS) * 150.7f, y = 416 + (k / COLS) * 133.5f;
            Cell c = idx < d.catalog().size() ? d.catalog().get(idx) : null;
            boolean equipped = c != null && d.equipped().stream().anyMatch(e -> e != null && e.id().equals(c.id()));
            p.image(equipped ? "skills/catalog_slot_selected" : "skills/catalog_slot", x, y, 136, 123);
            if (c == null) continue;
            p.hit(spellId(idx), x, y, 136, 123);
            p.c.texture(c.icon(), x + 32, y + 12, 72, 72, equipped ? .45f : 1);
            p.fit(c.name(), x + 68, y + 100, 15, 112, equipped ? Theme.MUTED : Theme.CREAM, Face.SERIF, Canvas.CENTER);
            if (p.over(x, y, 136, 123)) { hover = c; hx = x; hy = y; }
        }
        int rows = (d.catalog().size() + COLS - 1) / COLS;
        if (rows > ROWS) p.text((d.scrollRow() + 1) + " / " + (rows - ROWS + 1) + "  ·  휠로 넘기기", 1360, 812, 15, Theme.MUTED, Face.SANS, Canvas.RIGHT);
        p.button(CONFIRM, "skills/confirm", 481, 831, 578, 105, true);
        p.text("확인", 770, 883, 36, Theme.CREAM, Face.TITLE, Canvas.CENTER);
        if (d.notice() != null && !d.notice().isEmpty()) p.text(d.notice(), 768, 175, 18, 0xFFFFB0A8, Face.SANS, Canvas.CENTER);
        if (hover != null) {
            float tw = Math.max(186, Math.max(p.width(hover.name(), 20, Face.SERIF), p.width(hover.detail(), 15, Face.SANS)) + 36);
            float tx = hx + 146 + tw > 1400 ? hx - tw - 10 : hx + 146, ty = hy + 22;
            p.image("skills/tooltip_background", tx, ty, tw, 80);
            p.text(hover.name(), tx + 18, ty + 28, 20, Theme.GOLD, Face.SERIF, Canvas.LEFT);
            p.text(hover.detail(), tx + 18, ty + 56, 15, Theme.CREAM, Face.SANS, Canvas.LEFT);
        }
        if (d.dragFrom() >= 0 && d.dragFrom() < d.equipped().size() && d.equipped().get(d.dragFrom()) != null)
            p.c.texture(d.equipped().get(d.dragFrom()).icon(), p.mx - 45, p.my - 45, 90, 90, .85f);
    }
}
