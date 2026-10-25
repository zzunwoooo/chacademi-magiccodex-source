package kr.chacademy.npc;

import static org.junit.jupiter.api.Assertions.*;

import kr.chacademy.npc.core.Defs.Button;
import org.junit.jupiter.api.Test;

/** 상점 버튼 action (2026-10-25, 피피 "물건 좀 볼 수 있어요?"). */
class ButtonActionTest {
    @Test void plainButtonIsAiReply() {
        var b = new Button("daily", "요즘 뭐 해?");
        assertTrue(b.aiReply());
        assertNull(b.shop());
        assertEquals("", b.action());
    }

    @Test void shopActionParsesShopId() {
        var b = new Button("shop", "물건 좀 볼 수 있어요?", " shop:pipi ");
        assertFalse(b.aiReply());
        assertEquals("pipi", b.shop());
        assertEquals("fish_shop", new Button("s", "x", "shop:fish_shop").shop());
        assertEquals("물고기판매소", new Button("sell", "물고기 팔래요", "shop:물고기판매소").shop());
    }

    @Test void badShopIdIsRejected() {
        assertNull(new Button("s", "x", "shop:").shop());
        assertNull(new Button("s", "x", "shop:bad id!").shop());
        assertNull(new Button("s", "x", "command:say hi").shop());
        assertEquals("", new Button("s", "x", null).action());
    }
}
