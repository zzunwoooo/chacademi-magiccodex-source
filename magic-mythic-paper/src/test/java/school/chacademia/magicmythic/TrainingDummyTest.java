package school.chacademia.magicmythic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

/** #107: 허수아비 최근 N초 합계 · 초당 피해, 위치 줄 저장 형식. */
class TrainingDummyTest {
    @Test void windowSumsRecentHitsAndDropsOldOnes() {
        var hits = new ArrayDeque<TrainingDummies.Hit>();
        hits.add(new TrainingDummies.Hit(0, 4)); hits.add(new TrainingDummies.Hit(3000, 6)); hits.add(new TrainingDummies.Hit(4000, 10));
        double[] r = TrainingDummies.window(hits, 4000, 5000);
        assertEquals(20, r[0], 1e-9); assertEquals(5, r[1], 1e-9);          // 20 over 4 s
        r = TrainingDummies.window(hits, 6000, 5000);                       // the hit at 0 is older than 5 s
        assertEquals(16, r[0], 1e-9); assertEquals(2, hits.size());
        assertEquals(16 / 3.0, r[1], 1e-9);
    }
    @Test void singleHitCountsAsOneSecond() {
        var hits = new ArrayDeque<TrainingDummies.Hit>();
        hits.add(new TrainingDummies.Hit(1000, 7));
        double[] r = TrainingDummies.window(hits, 1000, 5000);
        assertEquals(7, r[0], 1e-9); assertEquals(7, r[1], 1e-9);
        assertEquals(0, TrainingDummies.window(new ArrayDeque<>(), 0, 5000)[1], 1e-9);
    }
    @Test void spotLineRoundTrips() {
        var s = new TrainingDummies.Spot("world", 1.5, 64, -3.5, 90);
        assertEquals(s, TrainingDummies.Spot.parse(s.line()));
        assertNull(TrainingDummies.Spot.parse("world;1;2"));
        assertNull(TrainingDummies.Spot.parse("world;a;2;3;0"));
    }
}
