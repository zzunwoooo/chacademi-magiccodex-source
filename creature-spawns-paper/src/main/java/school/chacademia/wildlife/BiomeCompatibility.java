package school.chacademia.wildlife;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
/** Explicit ecological aliases for vanilla terrain; never an allow-all or prefix match. */
final class BiomeCompatibility {
    private final Map<String,Set<String>> aliases;
    BiomeCompatibility(Map<String,Set<String>> aliases){this.aliases=Map.copyOf(aliases);}
    static BiomeCompatibility parse(ConfigurationSection section){
        if(section==null)throw new IllegalArgumentException("vanilla-biomes section missing");
        var rows=new LinkedHashMap<String,Set<String>>();
        for(String biome:section.getKeys(false)){
            Object raw=section.get(biome);
            if(!biome.matches("minecraft:[a-z0-9_]+")||!(raw instanceof List<?> list)||list.isEmpty()||list.size()>16)
                throw new IllegalArgumentException("Invalid vanilla biome compatibility: "+biome);
            var values=new LinkedHashSet<String>();
            for(Object value:list){
                if(!(value instanceof String id)||!id.matches("terralith:[a-z0-9_/]+")||!values.add(id))
                    throw new IllegalArgumentException("Invalid ecological counterpart: "+biome);
            }
            rows.put(biome,Set.copyOf(values));
        }
        return new BiomeCompatibility(rows);
    }
    boolean matches(Set<String> habitat,String actual){
        if(habitat.contains(actual))return true;
        var equivalents=aliases.get(actual);
        return equivalents!=null&&equivalents.stream().anyMatch(habitat::contains);
    }
    static boolean cave(String biome){
        return biome.startsWith("terralith:cave/")||Set.of("minecraft:lush_caves","minecraft:dripstone_caves","minecraft:deep_dark").contains(biome);
    }
}
