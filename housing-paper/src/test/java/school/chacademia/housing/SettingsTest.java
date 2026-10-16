package school.chacademia.housing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class SettingsTest {
    @Test void nextStep() {
        var steps = new TreeMap<Integer, Long>(); steps.put(4, 100L); steps.put(6, 300L);
        var s = new Settings("school", 3, steps, true, 10_000, 100, 40);
        assertEquals(4, (int) s.next(3).getKey());
        assertEquals(6, (int) s.next(4).getKey());
        assertEquals(6, (int) s.next(5).getKey());
        assertNull(s.next(6));
    }
}
