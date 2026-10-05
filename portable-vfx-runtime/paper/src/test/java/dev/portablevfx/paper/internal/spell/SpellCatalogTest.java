package dev.portablevfx.paper.internal.spell;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellCatalogTest {
    private static SpellCatalog.Spell spell(String id,int n,List<String> aliases,boolean enabled) {
        return new SpellCatalog.Spell(n,id,"이름", "magic.learned."+id,"",5d,1.5d,true,enabled,
                aliases,enabled?List.of(new SpellCatalog.Phase("cast","claude:fireball/projectile","caster",0,20)):List.of());
    }
    @Test void resolvesOnlyExplicitStableIdsAndAliases() {
        SpellCatalog c=new SpellCatalog(List.of(spell("feather_fall",1,List.of("페더 폴"),true)));
        assertEquals("feather_fall",c.resolve(" FEATHER_FALL ").orElseThrow().id());
        assertTrue(c.resolve("페더   폴").isPresent());
        assertTrue(c.resolve("1").isEmpty());assertTrue(c.resolve("feather_step").isEmpty());
    }
    @Test void rejectsAliasAndIdentityCollisions() {
        assertThrows(IllegalArgumentException.class,()->new SpellCatalog(List.of(spell("a",1,List.of("같음"),false),spell("b",2,List.of("같음"),false))));
        assertThrows(IllegalArgumentException.class,()->new SpellCatalog(List.of(spell("a",1,List.of(),false),spell("b",1,List.of(),false))));
    }
    @Test void unknownCostsAllowedOnlyWhenDisabled() {
        assertDoesNotThrow(()->new SpellCatalog.Spell(83,"healing_rain","치유의 비","magic.learned.healing_rain","",null,null,true,false,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new SpellCatalog.Spell(83,"healing_rain","치유의 비","magic.learned.healing_rain","",null,null,true,true,List.of(),List.of()));
    }
    @Test void strictSchemaDoesNotInferMissingBindings() {
        Map<String,Object> row=new HashMap<>(Map.of("number",299,"name","그림자 통행","permission","magic.learned.shadow_passage","mana-cost",10,"cooldown-seconds",1.5));
        SpellCatalog c=SpellCatalog.fromMap(Map.of("schema-version",1,"spells",Map.of("shadow_passage",row)));
        assertFalse(c.resolve("shadow_passage").orElseThrow().enabled());
        assertEquals(1.5,c.resolve("shadow_passage").orElseThrow().cooldownSeconds());
        row.put("number",299.5);
        assertThrows(IllegalArgumentException.class,()->SpellCatalog.fromMap(Map.of("schema-version",1,"spells",Map.of("shadow_passage",row))));
    }
    @Test void authorityRequiredAndCalledOnceWithoutInventingMana() {
        SpellCatalog catalog=new SpellCatalog(List.of(spell("a",1,List.of(),true),spell("b",2,List.of(),false)));
        UUID player=UUID.randomUUID();
        assertEquals(SpellCastController.Status.NO_AUTHORITY,new SpellCastController(catalog,null,null).cast(player,"a").status());
        int[] calls={0,0};
        SpellCastController c=new SpellCastController(catalog,(p,s,run)->{calls[0]++;return run.get()?SpellCastController.Status.CAST:SpellCastController.Status.PLAYBACK_FAILED;},(p,s)->{calls[1]++;return true;});
        assertEquals(SpellCastController.Status.NOT_READY,c.cast(player,"b").status());
        assertArrayEquals(new int[]{0,0},calls);
        assertEquals(SpellCastController.Status.CAST,c.cast(player,"a").status());
        assertArrayEquals(new int[]{1,1},calls);
    }
    @Test void authorityCannotInvokeTwice() {
        SpellCatalog catalog=new SpellCatalog(List.of(spell("a",1,List.of(),true)));
        SpellCastController c=new SpellCastController(catalog,(p,s,run)->{run.get();run.get();return SpellCastController.Status.CAST;},(p,s)->true);
        assertThrows(IllegalStateException.class,()->c.cast(UUID.randomUUID(),"a"));
    }
    @Test void canonicalEffectsRejectWrongCaseOrTraversal() {
        assertThrows(IllegalArgumentException.class,()->new SpellCatalog.Phase("cast","claude:stormcrown/projO","caster",0,1));
        assertThrows(IllegalArgumentException.class,()->new SpellCatalog.Phase("cast","claude:x/../bad","caster",0,1));
    }
}
