package school.magiccodex.paper;

import java.nio.file.*;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DonationCatalogTest {
    // Retired spell with no current successor; kept so historic donation records still resolve to a name.
    private static final Set<String> KNOWN_ORPHANS=Set.of("wind_vault");

    /** Every catalog id, enabled or not (ManaSpells.byId keeps only enabled spells). */
    private static Set<String> catalogIds()throws Exception{
        var y=new YamlConfiguration();y.load(Path.of("src/main/resources/mana-spells.yml").toFile());
        var spells=y.getConfigurationSection("spells");assertNotNull(spells);return spells.getKeys(false);
    }
    @Test void everyDonationEntryUsesACurrentCatalogId()throws Exception{
        var all=catalogIds();
        var y=new YamlConfiguration();y.load(Path.of("src/main/resources/donation-spells.yml").toFile());
        var root=y.getConfigurationSection("spells");assertNotNull(root);
        for(String id:root.getKeys(false)){
            assertEquals("magic.learned."+id,root.getString(id+".permission"),id);
            if(KNOWN_ORPHANS.contains(id))continue;
            assertTrue(all.contains(id),"donation-spells.yml uses retired id: "+id);
        }
        for(String id:all)assertTrue(root.contains(id),"mana spell missing from donation catalog: "+id);
        for(String retired:Set.of("feather_step","dampen_powder","handful_of_snow","wet_footprints","pressure_weight","cold_storage","liquid_barrier","water_rebound","thaw_pulse"))
            assertFalse(root.contains(retired),retired);
    }
    @Test void runtimeRoutesUseCurrentCatalogIds()throws Exception{
        var all=catalogIds();
        for(String id:SpellRules.load(Path.of("src/main/resources/spell-runtime.yml").toFile()).keySet())
            assertTrue(all.contains(id),"spell-runtime.yml uses retired id: "+id);
    }
}
