package school.magiccodex.paper;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpellRulesTest {
    @TempDir Path temp;
    @Test void legacyRoutesAreIsolatedFromTheNewCatalogIdentity()throws Exception{
        var routes=SpellRules.load(Path.of("src/main/resources/spell-runtime.yml").toFile());
        var paid=ManaSpells.load(Path.of("src/main/resources/mana-spells.yml").toFile());
        assertEquals(31,routes.size()); // Original compatibility file is preserved, not activated in catalogue mode.
        assertTrue(paid.catalogMode);
        assertFalse(paid.byId.containsKey("feather_step"));
        assertFalse(paid.byId.containsKey("wind_vault"));
        assertEquals("magic.learned.feather_fall",paid.byId.get("feather_fall").permission());
        for(var spell:paid.byId.values()){
            assertEquals("magic.learned."+spell.id(),spell.permission());
            assertTrue(spell.cost()>=0);assertTrue(spell.cooldown()>=0);
        }
    }
    @Test void powerChangesDamageButNeverTheManaCostOrCooldown(){
        var rule=new SpellRules.Rule("test","시험","CHA_test",SpellRules.Target.AIM,false,24,4,.35);
        assertEquals(4,rule.damage(0));assertEquals(12.4,rule.damage(24),.0001);assertEquals(39,rule.damage(100));
        assertEquals(4,rule.damage(-100));assertEquals(350004,rule.damage(Double.POSITIVE_INFINITY));
    }
    @Test void invalidRangeAndNonfiniteCoefficientsDoNotReplaceValidRoutes()throws Exception{
        var file=temp.resolve("rules.yml");String valid="spells:\n  test:\n    name: 시험\n    skill: CHA_test\n    target: AIM\n    range: 24\n    base-damage: 4\n    power-ratio: 0.35\n";
        Files.writeString(file,valid);assertEquals(1,SpellRules.load(file.toFile()).size());
        for(String bad:new String[]{valid.replace("range: 24","range: 100000"),valid.replace("0.35",".NaN"),valid.replace("CHA_test","op player"),valid.replace("target: AIM","target: anywhere")}){
            Files.writeString(file,bad);assertThrows(Exception.class,()->SpellRules.load(file.toFile()));
        }
    }
}
