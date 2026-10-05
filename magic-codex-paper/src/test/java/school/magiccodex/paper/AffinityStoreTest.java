package school.magiccodex.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import school.magiccodex.database.DatabaseSettings;

class AffinityStoreTest {
    @TempDir Path dir;
    private final UUID p = UUID.randomUUID();
    private static final DatabaseSettings SQLITE = new DatabaseSettings(false, "", "", "");

    @Test void dailyCapAndHeartGate() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            var r = s.add(p, "ella", "chat", 2, 3, "2026-10-05", 1);
            assertEquals(2, r.applied());
            r = s.add(p, "ella", "chat", 2, 3, "2026-10-05", 2);
            assertEquals(1, r.applied(), "하루 한도 3을 넘지 않음");
            r = s.add(p, "ella", "chat", 2, 3, "2026-10-05", 3);
            assertEquals(0, r.applied());
            r = s.add(p, "ella", "chat", 2, 3, "2026-10-06", 4);
            assertEquals(2, r.applied(), "다음 날은 다시 오름");
            r = s.add(p, "ella", "quest", 50, 0, "2026-10-06", 5);
            assertEquals(20, r.row().score(), "하트 0단계 상한 20");
            assertEquals(15, r.applied());
            s.setHeart(p, "ella", 1, 6);
            r = s.add(p, "ella", "quest", 50, 0, "2026-10-06", 7);
            assertEquals(40, r.row().score(), "하트 1단계 상한 40");
            s.setHeart(p, "ella", 0, 8);
            assertEquals(1, s.load(p, "ella").heart(), "하트 단계는 내려가지 않음");
            r = s.add(p, "ella", "strike", -3, 3, "2026-10-06", 9);
            assertEquals(37, r.row().score(), "음수는 한도 없이 적용");
            assertEquals(37, s.loadAll(p).get("ella").score());
        }
    }

    @Test void giftIsOnceOnlyAndRecoverable() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            String t1 = UUID.randomUUID().toString();
            assertEquals(AffinityStore.GiftBegin.OK, s.beginGift(t1, p, "mina", 6, "2026-10-05", 1, 10));
            assertEquals(AffinityStore.GiftBegin.DUPLICATE, s.beginGift(t1, p, "mina", 6, "2026-10-05", 1, 11));
            String t2 = UUID.randomUUID().toString();
            assertEquals(AffinityStore.GiftBegin.CAP, s.beginGift(t2, p, "mina", 6, "2026-10-05", 1, 12), "하루 1번");
            assertEquals(6, s.commitGift(t1, 13).row().score());
            assertNull(s.commitGift(t1, 14), "같은 토큰 두 번 확정 안 됨");
            assertEquals(6, s.load(p, "mina").score());

            String t3 = UUID.randomUUID().toString();
            assertEquals(AffinityStore.GiftBegin.OK, s.beginGift(t3, p, "mina", 6, "2026-10-06", 1, 20));
            assertTrue(s.cancelGift(t3));
            assertFalse(s.cancelGift(t3));
            assertNull(s.commitGift(t3, 21), "취소된 선물은 확정 안 됨");
            String t4 = UUID.randomUUID().toString();
            assertEquals(AffinityStore.GiftBegin.OK, s.beginGift(t4, p, "mina", 6, "2026-10-06", 1, 22), "취소하면 횟수 돌려받음");
            assertEquals(1, s.recoverPendingGifts(100, 200), "꺼짐 복구: 미확정은 한 번만 확정");
            assertEquals(0, s.recoverPendingGifts(100, 300));
            assertEquals(12, s.load(p, "mina").score());
        }
    }

    @Test void nicknameStored() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            assertEquals("책벌레", s.setNickname(p, "ella", "책벌레", 1).nickname());
            assertEquals("책벌레", s.load(p, "ella").nickname());
            assertEquals(20, AffinityStore.gate(0));
            assertEquals(100, AffinityStore.gate(4));
            assertEquals(100, AffinityStore.gate(5));
        }
    }
}
