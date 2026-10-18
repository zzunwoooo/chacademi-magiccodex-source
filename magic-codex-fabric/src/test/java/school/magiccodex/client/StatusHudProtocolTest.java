package school.magiccodex.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import school.magiccodex.protocol.*;

/** Status icons + target HUD wire formats and HUD segment math (2026-10-19). */
class StatusHudProtocolTest {
    @Test void statusSetRoundTripKeepsOrderAndInfinite() {
        var set = new StatusProtocol.Set(42, List.of(
            new StatusProtocol.Entry(StatusProtocol.Status.BURN, 60, 100),
            new StatusProtocol.Entry(StatusProtocol.Status.SLOW, StatusProtocol.INFINITE, StatusProtocol.INFINITE),
            new StatusProtocol.Entry(StatusProtocol.Status.SLEEP, 1, 20)));
        assertEquals(set, StatusProtocol.decode(StatusProtocol.encode(set)));
        assertEquals(new StatusProtocol.Clear(7), StatusProtocol.decode(StatusProtocol.encode(new StatusProtocol.Clear(7))));
        assertTrue(set.entries().get(1).infinite());
    }
    @Test void statusRejectsBadFrames() {
        var many = new ArrayList<StatusProtocol.Entry>();
        for (int i = 0; i < StatusProtocol.MAX_ENTRIES + 1; i++) many.add(new StatusProtocol.Entry(StatusProtocol.Status.STUN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StatusProtocol.Set(1, many));
        assertThrows(IllegalArgumentException.class, () -> StatusProtocol.decode(new byte[]{1, 0, 0, 0, 0, 1, 1, 99, 0, 1, 0, 1}));
        assertThrows(IllegalArgumentException.class, () -> StatusProtocol.decode(new byte[]{2, 1, 0, 0, 0, 1}));
        assertThrows(IllegalArgumentException.class, () -> StatusProtocol.decode(new byte[]{1, 1, 0, 0, 0, 1, 5}));
    }
    @Test void allSixteenStatusesHaveIconsAndNames() {
        assertEquals(16, StatusProtocol.Status.values().length);
        assertEquals(StatusProtocol.Status.FEAR, StatusProtocol.Status.of("공포"));
        assertEquals(StatusProtocol.Status.MANA_BURN, StatusProtocol.Status.of("마나번"));
        assertEquals(StatusProtocol.Status.POTION_SEAL, StatusProtocol.Status.of("potion-seal"));
        assertNull(StatusProtocol.Status.of("nope"));
    }
    @Test void targetHudRoundTripAndClamps() {
        var show = new TargetHudProtocol.Show(5, UUID.randomUUID(), TargetHudProtocol.KIND_CUSTOM, TargetHudProtocol.FLAG_SHINY | TargetHudProtocol.FLAG_COMBAT,
            "달빛 늑대", "", 720, 1000, -1, 0, 280, .38f);
        var back = (TargetHudProtocol.Show) TargetHudProtocol.decode(TargetHudProtocol.encode(show));
        assertEquals(show, back);
        assertTrue(back.shiny()); assertTrue(back.combat()); assertFalse(back.boss()); assertFalse(back.hasMana());
        var over = new TargetHudProtocol.Show(5, UUID.randomUUID(), 0, 0, "x", "entity.minecraft.zombie", 30, 20, 5, 10, -3, 2);
        assertEquals(20f, over.health(), 1e-6); assertEquals(0f, over.damage(), 1e-6); assertEquals(1f, over.tameChance(), 1e-6);
        assertInstanceOf(TargetHudProtocol.Hide.class, TargetHudProtocol.decode(TargetHudProtocol.encode(new TargetHudProtocol.Hide())));
        assertThrows(IllegalArgumentException.class, () -> new TargetHudProtocol.Show(1, UUID.randomUUID(), 0, 0, "", "", 1, 0, -1, 0, 0, -1));
    }
    @Test void hpSegmentsAreFixedSixWithBrightTip() {
        assertEquals(-1, TargetHudLayout.tipSegment(0));
        assertEquals(0, TargetHudLayout.tipSegment(.01f));
        assertEquals(4, TargetHudLayout.tipSegment(.72f));   // 720/1000 = 4.32 of 6: segments 0-3 full, segment 4 partly
        assertEquals(1, TargetHudLayout.segment(.72f, 3), 1e-6);
        assertEquals(.32f, TargetHudLayout.segment(.72f, 4), 1e-4);
        assertEquals(0, TargetHudLayout.segment(.72f, 5), 1e-6);
        assertEquals(5, TargetHudLayout.tipSegment(1));
        assertEquals(1, TargetHudLayout.tipSegment(2 / 6f)); // exactly two full segments: tip is the second one
    }
}
