package school.magiccodex.discovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EventIndexTest {
    @TempDir Path dir;
    @Test void threeReviewedEventsOnlyReachTheirIntendedSpell()throws Exception{
        var d=new Definitions(Path.of("src/main/resources/discoveries.yml").toFile());
        assertEquals(List.of("fish_lure"),d.index.get("fish.caught_fish").stream().map(Definitions.Spell::id).toList());
        assertEquals(30d,d.spells.get("fish_lure").requirements().get("fish.caught_fish"));
        assertEquals(50d,d.spells.get("frost_rupture").requirements().get("hit.snowball_monster"));
        assertEquals(5d,d.spells.get("quiet_mooring").requirements().get("place.boat"));
        assertEquals(List.of("falling_star"),d.meta.stream().map(Definitions.Spell::id).toList());
        // Travel's new state-only condition must not broaden any of the reviewed native event routes.
        assertEquals(Set.of("aerial_haul","crimson_breath","guardian_oath"),new HashSet<>(d.stateOnly.stream().map(Definitions.Spell::id).filter(id->!id.equals("spatial_leap")).toList()));
        assertEquals(List.of("spatial_leap"),d.index.get("state.travel_waypoints").stream().map(Definitions.Spell::id).toList());
        assertTrue(d.stateOnly.stream().anyMatch(spell->spell.id().equals("spatial_leap")));
        assertFalse(d.meta.stream().anyMatch(spell->spell.id().equals("spatial_leap")));
    }
    @Test void full299CatalogCanBeIndexedWithoutBroadcastingAnEvent()throws Exception{
        var yaml=new StringBuilder("spells:\n");
        for(int i=0;i<299;i++)yaml.append("  spell_").append(i).append(":\n    name: Test\n    permission: magic.test\n    icon: magic:test.png\n    requirements:\n      event_").append(i).append(": 1\n");
        Path f=dir.resolve("catalog.yml");Files.writeString(f,yaml);var d=new Definitions(f.toFile());
        assertEquals(299,d.spells.size());assertEquals(1,d.index.get("event_298").size());assertFalse(d.index.containsKey("unregistered"));assertTrue(d.meta.isEmpty());
    }
}
