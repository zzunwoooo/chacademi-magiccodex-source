package school.magiccodex.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopViewTest {
    static ShopView.Row row(String id, String buy, String sell) {
        return new ShopView.Row(id, id, List.of(), buy, sell, 3, true, true, null);
    }
    static ShopView.Model model(ShopView.Tab tab, List<ShopView.Row> rows, int scroll) {
        return new ShopView.Model("t", "r", "n", false, 1, "0", tab, new boolean[]{true, true, true}, rows, scroll, "",
                false, "", 1, "", true, true, true, false, "", 0, 0, 0, false, 941);
    }
    static boolean overlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    @Test void footerControlsNeverOverlap() {
        var boxes = List.of(ShopView.minusBox(), ShopView.quantityBox(), ShopView.plusBox(), ShopView.tradeBox());
        for (int i = 0; i < boxes.size(); i++) for (int j = i + 1; j < boxes.size(); j++) assertFalse(overlap(boxes.get(i), boxes.get(j)), i + "/" + j);
        assertTrue(ShopView.TRADE_X + ShopView.TRADE_W <= ShopView.CR);
        assertTrue(ShopView.FOOT_Y + ShopView.FOOT_H < 906, "footer stays inside the frame's inner border");
    }
    @Test void rowsTabsAndBarStayInsideTheFrame() {
        assertTrue(ShopView.listBottom() < ShopView.FOOT_Y);
        assertTrue(ShopView.tabX(2) + ShopView.TAB_W <= ShopView.CR);
        assertTrue(ShopView.SEARCH_X + ShopView.SEARCH_W < ShopView.TAB_X);
        assertTrue(ShopView.BAL_X + ShopView.BAL_W < ShopView.SEARCH_X);
        assertTrue(ShopView.CX + ShopView.ROW_W < ShopView.BAR_X);
    }
    @Test void bothButtonsOnlyInAllTabAndHitTheirOwnAction() {
        var rows = List.of(row("a", "10", "5"));
        var all = model(ShopView.Tab.ALL, rows, 0);
        float[] buy = ShopView.buyButton(rows.get(0), true, 0), sell = ShopView.sellButton(rows.get(0), true, 0);
        assertFalse(overlap(buy, sell));
        assertEquals(ShopView.Kind.ROW_BUY, ShopView.hit(all, buy[0] + 2, buy[1] + 2).kind());
        assertEquals(ShopView.Kind.ROW_SELL, ShopView.hit(all, sell[0] + 2, sell[1] + 2).kind());
        var sellTab = model(ShopView.Tab.SELL, rows, 0);
        var single = ShopView.sellButton(rows.get(0), false, 0);
        assertEquals(ShopView.Kind.ROW_SELL, ShopView.hit(sellTab, single[0] + 2, single[1] + 2).kind());
    }
    @Test void hitIndexFollowsScroll() {
        var rows = List.of(row("a", "1", ""), row("b", "1", ""), row("c", "1", ""), row("d", "1", ""), row("e", "1", ""), row("f", "1", ""));
        var m = model(ShopView.Tab.BUY, rows, 2);
        var h = ShopView.hit(m, ShopView.CX + 300, ShopView.rowY(0) + 60);
        assertEquals(ShopView.Kind.ROW, h.kind());
        assertEquals(2, h.index());
        assertEquals(0, ShopView.scrollAt(m, ShopView.LIST_Y));
        assertEquals(2, ShopView.scrollAt(m, ShopView.listBottom()));
    }
    @Test void moneyIsDisplayOnlyGrouping() {
        assertEquals("1,200", ShopView.money("1200"));
        assertEquals("0.5", ShopView.money("0.5"));
        assertEquals("1,000,000,000,000", ShopView.money("1000000000000"));
        assertEquals("—", ShopView.money(""));
        assertEquals("abc", ShopView.money("abc"));
    }
    @Test void loreCleaningDropsPackGlyphs() {
        assertEquals("치유 물약", ShopView.clean(" 치유  물약 "));
    }
}
