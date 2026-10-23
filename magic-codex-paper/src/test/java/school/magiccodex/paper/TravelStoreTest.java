package school.magiccodex.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 지도 이동 공유 저장소 (2026-10-23): first-visit unlock exactly once (across "servers" = two stores on one DB), one
 * personal point per player replaced on every save, one live move per player, and a failed move never recorded as done.
 * Uses the test-only local SQLite file (two stores on the same file stand in for school + wild on MariaDB).
 */
class TravelStoreTest {
    @TempDir Path dir;

    TravelStore store() { return new TravelStore(Logger.getLogger("travel-test"), dir, true, Runnable::run, () -> true); }
    static <T> T await(Consumer<Consumer<T>> call) throws Exception {
        var f = new CompletableFuture<T>(); call.accept(f::complete); return f.get(10, TimeUnit.SECONDS);
    }
    static TravelStore.Ticket ticket(long id, String to, long expires, String state) {
        return new TravelStore.Ticket(id, "camp", "school", to, "world", "", 1, 64, 2, 0, 0, System.currentTimeMillis(), expires, state, "");
    }

    @Test void unlockOnceAcrossServers() throws Exception {
        UUID p = UUID.randomUUID();
        try (var school = store(); var wild = store()) {
            assertTrue(school.usable());
            await((Consumer<Consumer<Set<String>>>) d -> school.unlocks(p, d));   // tables ready
            assertEquals(TravelStore.Claim.CLAIMED, await(d -> school.unlock(p, "harbor", "school", d)));
            assertEquals(TravelStore.Claim.ALREADY, await(d -> school.unlock(p, "harbor", "school", d)));   // revisit
            assertEquals(TravelStore.Claim.ALREADY, await(d -> wild.unlock(p, "harbor", "wild", d)));       // other server
            assertEquals(TravelStore.Claim.CLAIMED, await(d -> wild.unlock(p, "camp", "wild", d)));
            assertEquals(Set.of("harbor", "camp"), await(d -> school.unlocks(p, d)));                     // what the discovery count reads
        }
    }

    @Test void onePersonalPointReplaced() throws Exception {
        UUID p = UUID.randomUUID();
        try (var school = store(); var wild = store()) {
            assertEquals(Optional.empty(), await(d -> school.personal(p, d)));
            assertTrue(await(d -> school.savePersonal(p, new TravelModel.Personal("school", "world", "u1", 1, 64, 1, 0, 0, 1, "spell:spatial_leap"), d)));
            assertTrue(await(d -> wild.savePersonal(p, new TravelModel.Personal("wild", "world", "u2", 9, 70, 9, 0, 0, 2, "spell:spatial_leap"), d)));
            var now = await((Consumer<Consumer<Optional<TravelModel.Personal>>>) d -> school.personal(p, d)).orElseThrow();
            assertEquals("wild", now.server()); assertEquals(9, now.x());   // the old one is gone, one row per player
        }
    }

    @Test void oneLiveMoveAndFailuresNeverBecomeDone() throws Exception {
        UUID p = UUID.randomUUID();
        long later = System.currentTimeMillis() + 60_000;
        try (var school = store(); var wild = store()) {
            assertTrue(await(d -> school.openTicket(p, ticket(1, "wild", later, TravelStore.PENDING), d)));
            assertFalse(await(d -> school.openTicket(p, ticket(2, "wild", later, TravelStore.PENDING), d)));   // double click / second server
            // the wrong destination does not take it
            assertEquals(Optional.empty(), await(d -> school.claimArrival(p, "school", d)));
            var t = await((Consumer<Consumer<Optional<TravelStore.Ticket>>>) d -> wild.claimArrival(p, "wild", d)).orElseThrow();
            assertEquals(1, t.ticket());
            assertEquals(Optional.empty(), await(d -> wild.claimArrival(p, "wild", d)));                       // taken once
            assertTrue(await(d -> wild.finish(p, t, TravelStore.ARRIVING, TravelStore.DONE, "", d)));
            // a late origin timeout cannot turn the arrival into a failure
            assertFalse(await(d -> school.fail(p, 1, "서버 연결 응답이 없어요", d)));
            // after it finished a new move may start
            assertTrue(await(d -> school.openTicket(p, ticket(3, "wild", later, TravelStore.PENDING), d)));
            assertTrue(await(d -> school.fail(p, 3, "도착 서버가 닫혀 있어요", d)));
            // a failed ticket is never claimed as an arrival
            assertEquals(Optional.empty(), await(d -> wild.claimArrival(p, "wild", d)));
            var log = await((Consumer<Consumer<List<String>>>) d -> school.history(p, 10, d));
            assertEquals(2, log.size());
            assertTrue(log.get(0).contains("실패")); assertTrue(log.get(1).contains("성공"));
        }
    }

    @Test void expiredTicketFailsInsteadOfArriving() throws Exception {
        UUID p = UUID.randomUUID();
        try (var school = store(); var wild = store()) {
            assertTrue(await(d -> school.openTicket(p, ticket(5, "wild", System.currentTimeMillis() - 1, TravelStore.PENDING), d)));
            assertEquals(Optional.empty(), await(d -> wild.claimArrival(p, "wild", d)));   // logged out on the way
            var log = await((Consumer<Consumer<List<String>>>) d -> school.history(p, 10, d));
            assertEquals(1, log.size()); assertTrue(log.getFirst().contains("실패"));
            // an expired live ticket does not block a new move
            assertTrue(await(d -> school.openTicket(p, ticket(6, "wild", System.currentTimeMillis() + 60_000, TravelStore.PENDING), d)));
        }
    }

    @Test void refusesWithoutSharedDatabase() {
        try (var s = new TravelStore(Logger.getLogger("travel-test"), dir, false, Runnable::run, () -> true)) {
            assertFalse(s.usable());   // no MariaDB file: off, never a silent local SQLite
        }
    }
}
