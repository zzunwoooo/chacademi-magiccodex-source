package school.magiccodex.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** 아티팩트 뽑기 천장 (#98): 공유 카운터, 원자적 갱신, 재시도 안전. */
class ArtifactPityTest {
    String url; ArtifactOwnershipStore school, wild; UUID player = UUID.randomUUID(); @TempDir Path temp;
    static final ArtifactOwnershipStore.Pity PITY = new ArtifactOwnershipStore.Pity(2, 3); // 유일 이상, 3회째 확정

    @BeforeEach void setup() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        school = new ArtifactOwnershipStore(() -> DriverManager.getConnection(url));
        wild = new ArtifactOwnershipStore(() -> DriverManager.getConnection(url));
        school.initialize(); wild.ensurePityTable();
    }
    @AfterEach void close() throws Exception { try (var c = DriverManager.getConnection(url); var s = c.createStatement()) { s.execute("SHUTDOWN"); } }
    String op() { return UUID.randomUUID().toString(); }
    /** A low pull (common) with a pre-rolled unique replacement. */
    ArtifactOwnershipStore.Choice low(String id) { return new ArtifactOwnershipStore.Choice(id, 0, 1, false, new ArtifactOwnershipStore.Choice("hi_" + id, 2, 1)); }
    ArtifactOwnershipStore.Choice high(String id) { return new ArtifactOwnershipStore.Choice(id, 2, 1); }

    @Test void guaranteeOnThresholdPullThenReset() throws Exception {
        assertEquals("a", school.purchase(op(), player, List.of(low("a")), "item:shard", PITY).getFirst().id());
        assertEquals("b", wild.purchase(op(), player, List.of(low("b")), "item:shard", PITY).getFirst().id());
        assertEquals(2, school.pity(player));
        var third = school.purchase(op(), player, List.of(low("c")), "item:shard", PITY).getFirst();
        assertEquals("hi_c", third.id()); assertEquals(2, third.grade());
        assertEquals(0, wild.pity(player));
    }

    @Test void naturalHighGradeResetsCounter() throws Exception {
        school.purchase(op(), player, List.of(low("a"), low("b")), "item:shard", PITY);
        assertEquals(2, school.pity(player));
        school.purchase(op(), player, List.of(high("u")), "item:shard", PITY);
        assertEquals(0, school.pity(player));
    }

    @Test void fivePullCountsEachPullInOrder() throws Exception {
        var rows = school.purchase(op(), player, List.of(low("a"), low("b"), low("c"), low("d"), low("e")), "item:shard", PITY);
        assertEquals(List.of("a", "b", "hi_c", "d", "e"), rows.stream().map(ArtifactOwnershipStore.Pull::id).toList());
        assertEquals(2, school.pity(player));
    }

    @Test void concurrentServersCountEveryPullOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(6)) {
            var work = new ArrayList<Callable<String>>();
            for (int i = 0; i < 12; i++) { var store = i % 2 == 0 ? school : wild; int n = i; work.add(() -> store.purchase(op(), player, List.of(low("x" + n)), "item:shard", PITY).getFirst().id()); }
            int guaranteed = 0; for (var f : pool.invokeAll(work)) if (f.get().startsWith("hi_")) guaranteed++;
            assertEquals(4, guaranteed);
        }
        assertEquals(0, school.pity(player));
    }

    @Test void retrySameOperationDoesNotCountTwice() throws Exception {
        String operation = op();
        school.purchase(operation, player, List.of(low("a")), "item:shard", PITY);
        wild.purchase(operation, player, List.of(low("a")), "item:shard", PITY);
        assertEquals(1, school.pity(player));
    }

    @Test void pityOffLeavesCounterAndResultsAlone() throws Exception {
        school.purchase(op(), player, List.of(low("a")), "item:shard", PITY);
        var rows = school.purchase(op(), player, List.of(low("b"), low("c"), low("d")), "item:shard");
        assertEquals(List.of("b", "c", "d"), rows.stream().map(ArtifactOwnershipStore.Pull::id).toList());
        assertEquals(1, school.pity(player));
    }

    @Test void journalKeepsReplacementAndPitySetting() throws Exception {
        var journal = new ArtifactPurchaseJournal(temp);
        var request = new ArtifactPurchaseJournal.Purchase(op(), player, List.of(low("a"), high("u")), "item:shard", PITY);
        journal.prepare(request); journal.paid(request);
        var back = new ArtifactPurchaseJournal(temp).paid().get(request.operation());
        assertEquals(request, back);
        var legacy = new ArtifactPurchaseJournal.Purchase(op(), player, List.of(new ArtifactOwnershipStore.Choice("a", 0, 1)), "item:shard");
        journal.prepare(legacy); journal.paid(legacy);
        assertFalse(new ArtifactPurchaseJournal(temp).paid().get(legacy.operation()).pity().on());
    }

    @Test void pollReportsCounters() throws Exception {
        UUID other = UUID.randomUUID();
        school.purchase(op(), player, List.of(low("a")), "item:shard", PITY);
        var poll = school.poll(List.of(player, other));
        assertEquals(Map.of(player, 1, other, 0), poll.pity());
    }
}
