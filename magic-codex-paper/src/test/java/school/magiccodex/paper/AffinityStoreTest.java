package school.magiccodex.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            assertEquals(100, AffinityStore.gate(Integer.MAX_VALUE));
            assertEquals(20, AffinityStore.gate(Integer.MIN_VALUE));
        }
    }

    @Test void adminScorePreservesHeartNicknameAndBounds() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            assertEquals(20, s.adminChange(p, "ella", "set", 100, 1).score());
            s.setHeart(p, "ella", 2, 2);
            s.setNickname(p, "ella", "책벌레", 3);
            assertEquals(new AffinityStore.Row(60, 2, "책벌레"),
                    s.adminChange(p, "ella", "add", Integer.MAX_VALUE, 4));
            assertEquals(new AffinityStore.Row(0, 2, "책벌레"),
                    s.adminChange(p, "ella", "remove", Integer.MAX_VALUE, 5));
            assertEquals(17, s.adminChange(p, "ella", "set", 17, 6).score());
            assertEquals(22, s.adminChange(p, "ella", "add", 5, 7).score());
            assertEquals(15, s.adminChange(p, "ella", "remove", 7, 8).score());
            assertEquals(15, s.adminChange(p, "ella", "add", 0, 9).score());
            assertEquals(new AffinityStore.Row(15, 2, "책벌레"), s.load(p, "ella"));
            s.setHeart(p, "ella", 5, 10);
            assertEquals(100, s.adminChange(p, "ella", "set", Integer.MAX_VALUE, 11).score());
        }
    }

    @Test void adminChangesNeitherConsumeNorResetDailyCaps() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            String day = "2026-10-06";
            s.add(p, "ella", "chat", 2, 3, day, 1);
            assertEquals(12, s.adminChange(p, "ella", "add", 10, 2).score());
            assertEquals(1, s.add(p, "ella", "chat", 2, 3, day, 3).applied());
            s.adminChange(p, "ella", "set", 0, 4);
            assertEquals(0, s.add(p, "ella", "chat", 2, 3, day, 5).applied(), "관리자 변경은 사용한 한도를 복구하지 않음");
            s.adminChange(p, "ella", "add", 10, 6);
            s.adminChange(p, "ella", "remove", 10, 7);
            assertEquals(0, s.add(p, "ella", "chat", 2, 3, day, 8).applied());
            assertEquals(3, s.add(p, "ella", "quest", 3, 3, day, 9).applied(), "다른 출처 한도에도 영향 없음");
        }
    }

    @Test void invalidAdminChangesDoNotCreateRows() throws Exception {
        try (var s = new AffinityStore(SQLITE, dir.resolve("affinity.db"))) {
            assertThrows(IllegalArgumentException.class, () -> s.adminChange(p, "ella", "set", -1, 1));
            assertThrows(IllegalArgumentException.class, () -> s.adminChange(p, "ella", "unknown", 1, 1));
            assertTrue(s.loadAll(p).isEmpty());
        }
    }

    @Test void adminChangesFromTwoConnectionsDoNotLoseUpdates() throws Exception {
        Path file = dir.resolve("affinity.db");
        try (var first = new AffinityStore(SQLITE, file);
             var second = new AffinityStore(SQLITE, file);
             var workers = Executors.newFixedThreadPool(2)) {
            first.setHeart(p, "ella", 5, 1);
            first.setNickname(p, "ella", "책벌레", 2);
            CountDownLatch start = new CountDownLatch(1);
            var one = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 20; i++) first.adminChange(p, "ella", "add", 1, i + 3);
                return null;
            });
            var two = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 20; i++) second.adminChange(p, "ella", "add", 1, i + 3);
                return null;
            });
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
            assertEquals(new AffinityStore.Row(40, 5, "책벌레"), first.load(p, "ella"));
        }
    }

    @Test void adminTargetsMustBeRegisteredAndUnambiguous() {
        Map<String, String> names = Map.of("ella", "엘라", "mina", "미나");
        assertEquals("ella", NpcSocialService.registeredNpcId(names, "엘라"));
        assertEquals("ella", NpcSocialService.registeredNpcId(names, "ELLA"));
        assertEquals("mina", NpcSocialService.registeredNpcId(names, "mina"));
        assertThrows(IllegalArgumentException.class, () -> NpcSocialService.registeredNpcId(names, "arbitrary-db-id"));
        assertThrows(IllegalArgumentException.class, () -> NpcSocialService.registeredNpcId(names, "엘"));
        assertThrows(IllegalArgumentException.class, () -> NpcSocialService.registeredNpcId(names, null));
        assertThrows(IllegalArgumentException.class, () -> NpcSocialService.registeredNpcId(Map.of(), "ella"));
        Map<String, String> duplicates = Map.of("ella", "엘라", "ella_2", "엘라");
        assertThrows(IllegalArgumentException.class, () -> NpcSocialService.registeredNpcId(duplicates, "엘라"));
        assertEquals("ella_2", NpcSocialService.registeredNpcId(duplicates, "ella_2"));
    }
}
