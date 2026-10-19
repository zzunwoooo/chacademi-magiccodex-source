package school.magiccodex.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import school.magiccodex.protocol.FishingProtocol;
import school.magiccodex.protocol.FishingProtocol.*;

/** Round trip of every 낚시 frame and request. */
class FishingProtocolTest {
    static Gear gear() {
        return new Gear(List.of(new Piece(0, "bamboo_common", "대나무 낚싯대", 0, 49, 50, 0, false, ""),
                new Piece(1, "cork_common", "코르크 찌", 0, -1, -1, 0, true, ""),
                new Piece(2, "cotton_common", "무명실 낚싯줄", 0, -1, -1, 0, true, ""),
                new Piece(3, "earthworm_common", "지렁이", 0, -1, -1, -1, true, "")), 10, .6f, 1.5f, .2f, 15, 100);
    }

    @Test void roundTrip() {
        var frames = List.<Frame>of(
                new Session(7, FishingProtocol.PHASE_WATER, FishingProtocol.BG_OCEAN, 15, 3, 4096, gear(), ""),
                new Bite(7, 99, FishingProtocol.SHAPE_EEL, .4f, -1, 3000, 2, 600, 3, FishingProtocol.DIVE, 1234L, 12.5f),
                new Result(7, FishingProtocol.CAUGHT, "fish_001", "붕어", 0, 0, 21.4f, true, false,
                        List.of(new Reward("붕어", 1, new byte[]{1, 2, 3})), false, gear(), "알림"),
                new Close(7, "끝"),
                new GearList(gear(), false, List.of(new Owned(1, 5, "feather_common", "깃털 찌", 0, 40, 40, 1, "가라앉는 속도 2", "")),
                        List.of("a", "b", "c", "d"), ""),
                new Codex(List.of(new Entry("fish_001", "붕어", 0, 0, true, 21.4f, 3, "설명", "사계절", "민물", "0~10m", "언제나")),
                        new int[]{1, 0, 0, 0}, 1, 2, ""));
        for (Frame f : frames) {
            var back = FishingProtocol.decode(FishingProtocol.encode(f));
            if (f instanceof Codex c) {
                var b = (Codex) back;
                assertEquals(c.entries(), b.entries());
                assertArrayEquals(c.treasures(), b.treasures());
            } else if (f instanceof Result r) {
                var b = (Result) back;
                assertEquals(r.name(), b.name()); assertEquals(r.sizeCm(), b.sizeCm()); assertEquals(r.gear(), b.gear());
                assertArrayEquals(r.rewards().getFirst().preview(), b.rewards().getFirst().preview());
            } else assertEquals(f, back);
        }
        for (Request r : List.of(Request.stop(7, 12.5f), Request.result(7, 99, FishingProtocol.SNAPPED, 4500), Request.of(FishingProtocol.RECAST, 7),
                Request.equip(2, 17), Request.claim(3), Request.of(FishingProtocol.CODEX_OPEN, 0)))
            assertEquals(r, FishingProtocol.request(FishingProtocol.request(r)));
    }

    @Test void tiers() {
        assertEquals(0, FishingProtocol.tiersReached(49, 200));
        assertEquals(1, FishingProtocol.tiersReached(50, 200));
        assertEquals(4, FishingProtocol.tiersReached(200, 200));
    }
}
