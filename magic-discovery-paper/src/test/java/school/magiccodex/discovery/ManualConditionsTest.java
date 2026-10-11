package school.magiccodex.discovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ManualConditionsTest {
    @TempDir Path dir;
    @Test void manualOverlayDisablesOriginalCountersAndChanceButRetainsNaturalState()throws Exception{
        var d=new Definitions(Path.of("src/main/resources/discoveries.yml").toFile());
        d.manual(Path.of("src/main/resources/manual-conditions.yml").toFile());
        assertEquals(80,d.manual.size());assertEquals("magic.condition.flight",Definitions.conditionPermission("flight"));assertFalse(d.index.containsKey("fish.caught_fish"));assertFalse(d.index.containsKey("lesson.wind.basic"));assertFalse(d.index.containsKey("cast.pressure_press.circle6"));
        assertFalse(d.index.containsKey(Definitions.conditionKey("wind_basket")));
        assertEquals(1d,d.manual.get("cross_breeze").chance());assertTrue(d.index.containsKey("submerged.survive_thirty"));
        assertTrue(d.manual.values().stream().allMatch(s->s.requirements().equals(Map.of(Definitions.conditionKey(s.id()),1d))));
        assertFalse(d.manual.containsKey("feather_step"));assertFalse(d.manual.containsKey("wind_vault"));
    }
    @Test void duplicateCommandsAndResetNeverMintAnotherFirstRewardOrNotice()throws Exception{
        var id=UUID.randomUUID();Path path=dir.resolve("manual.db");long token;
        try(var store=new DiscoveryStore(path)){
            var first=store.grantCondition(id,"wind_basket",true).get();assertTrue(first.created());token=first.acquisition().token();
            store.acknowledge(id,token).get();var duplicate=store.grantCondition(id,"wind_basket",true).get();assertFalse(duplicate.created());
            assertEquals(1,store.load(id).get().acquired().size());assertEquals(1d,store.load(id).get().progress().get(Definitions.conditionKey("wind_basket")));
            store.revokeCondition(id,"wind_basket",false).get();assertTrue(store.load(id).get().learnedOverrides().get("wind_basket"));
            store.revokeCondition(id,"wind_basket",true).get();assertFalse(store.load(id).get().learnedOverrides().get("wind_basket"));
            var again=store.grantCondition(id,"wind_basket",true).get();assertFalse(again.created());assertEquals(token,again.acquisition().token());assertTrue(again.acquisition().notified());
        }
        try(var store=new DiscoveryStore(path)){assertFalse(store.grantCondition(id,"wind_basket",true).get().created());assertEquals(token,store.load(id).get().acquired().getFirst().token());}
    }
    @Test void priorAdministrativeLearningDoesNotBecomeAFirstDiscovery()throws Exception{
        var id=UUID.randomUUID();try(var store=new DiscoveryStore(dir.resolve("admin.db"))){store.setLearned(id,"flight",true,Set.of(),Map.of()).get();assertFalse(store.grantCondition(id,"flight",true).get().created());assertTrue(store.load(id).get().acquired().isEmpty());}
    }
}
