package school.magiccodex.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import school.magiccodex.protocol.KyudichiQueueProtocol;
import school.magiccodex.protocol.KyudichiQueueProtocol.*;

/** Locks the 큐디치 queue/dashboard (client ↔ proxy) and link (proxy ↔ arena) wire formats. */
class KyudichiQueueProtocolTest {
    static final UUID A = new UUID(1, 2), B = new UUID(3, 4), M = new UUID(9, 9);
    static final Live LIVE = new Live("경기장1", 2, 20, 10, 300000, 3, "준우 · 서하 · 도윤", "하린 · 지우 · 시우");

    @Test void toClientRoundTrip() {
        var rank = new Rank(1, A, "준우", 1712, 6, 30, 22, 8);
        var rec = new PlayerRecord(A, "준우", 1712, 6, 1, 30, 22, 8, 0, 41, 77, 30, 12, 3, 19, 9, 51234.5f, 8000f,
                List.of(new Recent(1_760_000_000_000L, 1, 0, 80, 50, 3, 2, 0, true, 18), new Recent(1_759_000_000_000L, 2, 1, 40, 40, 0, 1, 1, false, 0)));
        for (ToClient m : List.of(new Status(1, 125, 0, 0, 0, false, 7), new Status(2, 0, 9, 4, 6, true, 3),
                new Dash(5, List.of(LIVE)), new Ranking(List.of(rank), rank), new Ranking(List.of(), null), new RecordReply(rec), new RecordReply(null)))
            assertEquals(m, KyudichiQueueProtocol.decodeToClient(KyudichiQueueProtocol.encode(m)));
    }

    @Test void fromClientRoundTrip() {
        for (FromClient m : List.of(new Join(), new Leave(), new Accept(), new Decline(), new DashRequest(), new RankingRequest(),
                new RecordRequest(""), new RecordRequest("서하"), new SpectateRequest("경기장1"), new ClientHello(), new PracticeRequest()))
            assertEquals(m, KyudichiQueueProtocol.decodeFromClient(KyudichiQueueProtocol.encode(m)));
    }

    @Test void linkRoundTrip() {
        for (ToArena m : List.of(new Assign(M, true, List.of(A), List.of(B)), new KyudichiQueueProtocol.Spectate(A, ""), new Practice(B)))
            assertEquals(m, KyudichiQueueProtocol.decodeToArena(KyudichiQueueProtocol.encode(m)));
        for (ToProxy m : List.of(new Hello(A), new LiveList(2, List.of(new LiveMatch(M, LIVE, List.of(A), List.of(B)))), new Return(List.of(A, B)), new Ended(M)))
            assertEquals(m, KyudichiQueueProtocol.decodeToProxy(KyudichiQueueProtocol.encode(m)));
    }

    static final String GOLDEN_ASSIGN = "0101000000000000000900000000000000090101000000000000000100000000000000020100000000000000030000000000000004";
    static final String GOLDEN_STATUS = "0101020000070306010004";
    static final String GOLDEN_HELLO = "010100000000000000010000000000000002";

    @Test void golden() {
        var hex = HexFormat.of();
        assertEquals(GOLDEN_ASSIGN, hex.formatHex(KyudichiQueueProtocol.encode(new Assign(M, true, List.of(A), List.of(B)))));
        assertEquals(new Status(2, 0, 7, 3, 6, true, 4), KyudichiQueueProtocol.decodeToClient(hex.parseHex(GOLDEN_STATUS)));
        assertEquals(new Hello(A), KyudichiQueueProtocol.decodeToProxy(hex.parseHex(GOLDEN_HELLO)));
    }

    @Test void strict() {
        var hex = HexFormat.of();
        assertThrows(IllegalArgumentException.class, () -> KyudichiQueueProtocol.decodeToClient(hex.parseHex(GOLDEN_STATUS + "00")));
        assertThrows(IllegalArgumentException.class, () -> KyudichiQueueProtocol.decodeFromClient(hex.parseHex("0163")));
        assertThrows(IllegalArgumentException.class, () -> KyudichiQueueProtocol.decodeToArena(hex.parseHex("0201")));
        assertThrows(IllegalArgumentException.class, () -> KyudichiQueueProtocol.decodeToProxy(hex.parseHex("01")));
    }
}
