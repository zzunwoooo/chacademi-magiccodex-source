package school.chacademia.magicmythic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TelegraphMechTest {
    @Test void vfxIdGetsThePackPrefix() {
        assertEquals("claude:blazepillar/erupt", TelegraphMech.vfxId("blazepillar/erupt"));
        assertEquals("claude:blazepillar/erupt", TelegraphMech.vfxId("Claude:BlazePillar/erupt"));
        assertEquals("", TelegraphMech.vfxId(""));
        assertEquals("", TelegraphMech.vfxId("bad id!"));
    }

    @Test void tintParsesHex() {
        assertEquals(0xFFFFFF, TelegraphMech.tint("FFFFFF"));
        assertEquals(0x7FC8FF, TelegraphMech.tint("#7FC8FF"));
        assertEquals(0xFFFFFF, TelegraphMech.tint("nope"));
    }
}
