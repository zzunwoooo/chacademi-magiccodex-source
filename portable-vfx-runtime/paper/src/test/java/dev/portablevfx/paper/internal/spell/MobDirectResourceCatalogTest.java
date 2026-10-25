package dev.portablevfx.paper.internal.spell;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobDirectResourceCatalogTest {
    private static Map<String,Object> values(ConfigurationSection section){
        Map<String,Object> result=new LinkedHashMap<>();
        section.getValues(false).forEach((key,value)->result.put(key,value instanceof ConfigurationSection child?values(child):value));
        return result;
    }
    @Test void shippedMobEntriesArePlayableOnlyWithTheirExactMechanicEffects()throws Exception{
        var yaml=new YamlConfiguration();
        try(var in=getClass().getResourceAsStream("/spell-catalog.yml")){
            assertNotNull(in);yaml.load(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
        var problems=new LinkedHashMap<String,String>();
        var catalog=SpellCatalog.fromMapLenient(values(yaml),problems);
        assertTrue(problems.isEmpty(),problems.toString());assertEquals(287,catalog.entries().size()); // 2026-10-25: +63 new, -12 dropped
        final int[] NUMBERS={63,64,114,165,166,208,216,203,209,204,255,246,256,257,258,26,167,13,21,22,168,23,174,169}; // 2026-10-25 renumber (magic-mythic-paper/tools/spell_order.py)
        var expected=List.of("fox_orbit","curving_plumes","crystal_orbit","venom_spit","smoke_veil","domain_stomp","seal_circle","seal_talismans","shield_slam","chain_arrow","mana_drain","soul_scythe","oblivion_orbs","bone_grasp","shadow_leap","binding_pull","fairy_dust","moon_wind","gale_wings","feather_storm","hunting_pounce","roar_leap","gravity_slam","tail_spikes");
        for(int offset=0;offset<expected.size();offset++){
            var spell=catalog.resolve(expected.get(offset)).orElseThrow();
            assertEquals(NUMBERS[offset],spell.number());assertTrue(spell.reviewed());assertTrue(spell.enabled());
            assertTrue(spell.mechanicVfx());assertTrue(spell.phases().isEmpty());
            assertFalse(spell.icon().isBlank());assertNotNull(spell.manaCost());assertNotNull(spell.cooldownSeconds());
            assertTrue(catalog.mechanicEffects().containsAll(spell.mechanicEffects()));
        }
        assertEquals(24,catalog.entries().stream().filter(SpellCatalog.Spell::mechanicVfx).count());
    }
}
