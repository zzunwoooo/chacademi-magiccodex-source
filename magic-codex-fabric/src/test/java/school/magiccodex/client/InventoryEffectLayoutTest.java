package school.magiccodex.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InventoryEffectLayoutTest {
    @Test void manyEffectsStayInsideSmallAndLargeScreensAndRemainReachable() {
        for (int[] size : new int[][]{{320,180},{370,240},{426,240},{480,270},{640,360},{960,540},{1920,1080}})
            for (boolean book : new boolean[]{false,true}) for (int count : new int[]{1,3,12,64}) {
                var p = InventoryEffectLayout.fit(size[0],size[1],book,count);
                assertTrue(p.x() >= 0 && p.y() >= 0);
                assertTrue(p.x()+p.width() <= size[0] && p.y()+p.height() <= size[1]);
                assertTrue(p.rows() >= 1 && p.rows() <= count);
                assertEquals(count-p.rows(),p.offset(10000));
                assertEquals(0,p.offset(-100));
            }
    }
    @Test void emptyEffectsHaveNoInteractivePanel() {
        var p=InventoryEffectLayout.fit(480,270,false,0);
        assertEquals(0,p.height()); assertEquals(0,p.width());
        assertFalse(p.contains(4,4));
    }
    @Test void fullModePanelDoesNotOverlapCraftingOrInventorySlots() {
        var p=InventoryEffectLayout.fit(480,270,false,64);
        var fit=ChacaInventoryLayout.fit(480,270,false);
        assertTrue(p.x()+p.width() < fit.screenX(430));
        assertTrue(p.y()+p.height() < fit.screenY(494));
    }
}
