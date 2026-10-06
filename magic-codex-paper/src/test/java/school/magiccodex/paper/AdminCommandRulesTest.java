package school.magiccodex.paper;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

final class AdminCommandRulesTest {
    @Test void statsUseBaseValuesNotEquipmentTotals(){
        var account=new ManaAccount(80,100,5);account.modifier("test:equipment",50,2);account.baseHaste=10;account.hasteModifier("test:equipment",20);
        account.baseMaximum=AdminCommandRules.changed(account.baseMaximum,"추가",20,1_000_000);
        account.baseRegen=AdminCommandRules.changed(account.baseRegen,"설정",7,1_000_000);
        account.baseHaste=AdminCommandRules.changed(account.baseHaste,"감소",3,1_000_000);
        assertEquals(120,account.baseMaximum);assertEquals(170,account.snapshot().maximum());assertEquals(9,account.snapshot().regeneration());assertEquals(27,account.snapshot().haste());
    }
    @Test void statsCannotOverflowAndDecreaseStopsAtZero(){
        assertEquals(0,AdminCommandRules.changed(10,"감소",50,1_000_000));
        assertEquals(1_000_000,AdminCommandRules.changed(0,"설정",1_000_000,1_000_000));
        for(String invalid:List.of("NaN","Infinity","-1","1000001","bad"))assertThrows(IllegalArgumentException.class,()->AdminCommandRules.nonnegative(invalid,1_000_000));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.changed(1_000_000,"추가",1,1_000_000));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.changed(1,"set",1,1_000_000));
    }
    @Test void scoresHaveExplicitDecreaseAndExistingSignedSetRange(){
        assertEquals(-10,AdminCommandRules.scoreValue("감소",10));assertEquals(10,AdminCommandRules.scoreValue("추가",10));assertEquals(-10,AdminCommandRules.scoreValue("설정",-10));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.scoreValue("감소",-1));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.scoreValue("설정",Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.scoreValue("설정",1_000_000_001));
    }
    @Test void corePercentAndGainBoundsMatchEnhancementSchema(){
        assertDoesNotThrow(()->AdminCommandRules.validateCore(10,100,0,10000,2));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.validateCore(0,50,10,1,0));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.validateCore(1,101,10,1,0));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.validateCore(1,50,Double.NaN,1,0));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.validateCore(1,50,10,0,0));
        assertThrows(IllegalArgumentException.class,()->AdminCommandRules.validateCore(1,50,10,1,3));
        assertEquals(2,AdminCommandRules.affinity("마법가속"));assertThrows(IllegalArgumentException.class,()->AdminCommandRules.affinity("임의성향"));
    }
    @Test void onlyRegisteredEnabledTitlesCanBeGranted(){
        var enabled=new TitleDefinition("good",0,"별빛",1,true,false);var disabled=new TitleDefinition("old",0,"옛 칭호",1,false,false);
        assertTrue(AdminCommandRules.titleAllowed(enabled,false));assertFalse(AdminCommandRules.titleAllowed(disabled,false));assertFalse(AdminCommandRules.titleAllowed(null,false));assertFalse(AdminCommandRules.titleAllowed(null,true));
        assertTrue(AdminCommandRules.titleAllowed(disabled,true));
    }
    @Test void catalogNamesResolveStrictlyAndAmbiguityIsNeverGuessed(){
        var catalog=Map.of("first","별빛","second","별빛","wind","바람 전령");
        assertNull(AdminCommandRules.resolve(catalog,"별빛"));assertNull(AdminCommandRules.resolve(catalog,"임의 칭호"));assertEquals("first",AdminCommandRules.resolve(catalog,"first"));assertEquals("wind",AdminCommandRules.resolve(catalog,"바람 전령"));
        assertFalse(AdminCommandRules.catalogChoices(catalog).contains("별빛"));assertTrue(AdminCommandRules.catalogChoices(catalog).contains("바람 전령"));
    }
    @Test void completionExplainsEachItemParameterAndStopsAfterLast(){
        for(int position=2;position<=4;position++)assertTrue(AdminCommandRules.itemOptions("마법봉",position).stream().anyMatch(s->s.startsWith("<")));
        for(int position=2;position<=5;position++)assertTrue(AdminCommandRules.itemOptions("마력코어",position).stream().anyMatch(s->s.startsWith("<")));
        assertEquals(List.of("마력","마나","마법가속"),AdminCommandRules.itemOptions("마력코어",6));assertTrue(AdminCommandRules.itemOptions("마력코어",7).isEmpty());assertTrue(AdminCommandRules.itemOptions("마법봉",5).isEmpty());
        assertEquals(AdminCommandRules.STATS,AdminCommandRules.statOptions(1));assertEquals(AdminCommandRules.CHANGES,AdminCommandRules.statOptions(2));assertTrue(AdminCommandRules.statOptions(3).contains("<수치:0~1000000>"));assertTrue(AdminCommandRules.statOptions(4).isEmpty());
    }
    @Test void multiwordCompletionDoesNotDuplicateAlreadyTypedWords(){
        var args=new String[]{"유저","마법","습득","바람","전"};
        assertEquals(List.of("전령"),AdminCommandRules.filter(AdminCommandRules.tailChoices(List.of("바람 전령","바람 칼날","불꽃"),args,3),"전"));
    }
    @Test void adminCatalogIncludesEveryRegisteredSpellWithoutChangingCastingEnablement()throws Exception{
        var catalog=ManaSpells.load(java.nio.file.Path.of("src/main/resources/mana-spells.yml").toFile());
        assertEquals(299,catalog.registeredCatalog.size());
        assertTrue(catalog.registeredCatalog.containsKey("aerial_haul"));assertFalse(catalog.byId.containsKey("aerial_haul"));
        assertEquals("magic.learned.aerial_haul",catalog.registeredCatalog.get("aerial_haul").get("permission"));
        catalog.byId.forEach((id,spell)->assertEquals(spell.permission(),catalog.registeredCatalog.get(id).get("permission")));
        assertThrows(UnsupportedOperationException.class,()->catalog.registeredCatalog.put("forged",Map.of()));
    }
    @Test void publicDescriptorContainsNewKoreanCommandsWithoutCodexAliases()throws Exception{
        try(var input=getClass().getResourceAsStream("/plugin.yml")){
            assertNotNull(input);Map<?,?> root=new Yaml().load(input);Map<?,?> commands=(Map<?,?>)root.get("commands");
            for(String required:List.of("유저관리","점수관리","아이템관리","칭호추가","칭호삭제","클래스승급","계절관리","펫관리","온도관리","마나관리"))assertTrue(commands.containsKey(required),required);
            assertFalse(commands.containsKey("서클승급"));
            for(var entry:commands.entrySet()){
                assertFalse(entry.getKey().toString().startsWith("codex"));Object aliases=((Map<?,?>)entry.getValue()).get("aliases");
                if(aliases instanceof Collection<?> list)for(Object alias:list)assertFalse(alias.toString().startsWith("codex"));
            }
            Map<?,?> permissions=(Map<?,?>)root.get("permissions");assertTrue(permissions.containsKey("magiccodex.mana.admin"));assertTrue(permissions.containsKey("magiccodex.ascend"));
        }
    }
}
