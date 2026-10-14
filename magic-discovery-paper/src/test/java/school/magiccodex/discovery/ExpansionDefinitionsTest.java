package school.magiccodex.discovery;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 2026-10-14 spells 101-167: natural keys exist, lessons/books are manual, low-chance lessons roll at 0.1, undecided ones stay unmapped. */
class ExpansionDefinitionsTest {
    @Test void expansionConditionsAreMappedAsApproved() throws Exception {
        var d = new Definitions(Path.of("src/main/resources/discoveries.yml").toFile());
        d.manual(Path.of("src/main/resources/manual-conditions.yml").toFile());
        for (String id : List.of("stone_wall", "earth_elemental", "earthquake", "forest_throne", "minor_heal", "protective_ward", "companion_return", "starlight_survey", "shadow_bind"))
            assertEquals(Map.of(Definitions.conditionKey(id), 1d), d.manual.get(id).requirements(), id);
        assertEquals(100d, d.spells.get("earth_shaping").requirements().get("place.dirt_family"));
        assertEquals(Set.of("breed.cow", "breed.sheep", "breed.pig"), d.spells.get("pollen_matchmaking").requirements().keySet());
        assertEquals(Set.of("season.spring", "season.summer", "season.autumn", "season.winter"), d.spells.get("climate_control").requirements().keySet());
        assertEquals(0.1, d.spells.get("primordial_stone").chance());
        assertEquals(0.1, d.spells.get("merciful_arrow").chance());
        // one key may feed two spells (harvest 100 / 300)
        assertEquals(2, d.index.get("harvest.crops").size());
        for (String undecided : List.of("moss_coating", "flint_sorting", "dormant_seed", "material_exchange", "luminous_archive", "constellation_blessing", "mana_deposit"))
            assertFalse(d.spells.containsKey(undecided) || d.manual.containsKey(undecided), undecided);
    }
}
