package school.magiccodex.discovery;
import java.io.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
final class Definitions {
    record Spell(String id,String name,String permission,String icon,String category,String description,Map<String,Double> requirements,List<String> prerequisites,double chance){}
    final Map<String,Spell> spells=new LinkedHashMap<>();
    final Map<String,List<Spell>> index=new HashMap<>();
    Definitions(File file)throws Exception{
        var y=new YamlConfiguration();y.load(file);var root=y.getConfigurationSection("spells");
        if(root==null||root.getKeys(false).size()>256)throw new IOException("Expected up to 256 spells");
        for(String id:root.getKeys(false)){
            var s=Objects.requireNonNull(root.getConfigurationSection(id));var req=s.getConfigurationSection("requirements");Map<String,Double> metrics=new LinkedHashMap<>();
            if(req!=null)for(String key:req.getKeys(true))if(!req.isConfigurationSection(key)){
                Object raw=req.get(key);double v=raw instanceof Number number?number.doubleValue():Double.NaN;if(!key.matches("[a-z0-9_.-]{1,100}")||!Double.isFinite(v)||v<=0||v>1e9)throw new IOException("Bad requirement: "+id+"/"+key);metrics.put(key,v);
            }
            String name=s.getString("name",""),perm=s.getString("permission",""),icon=s.getString("icon","");double chance=s.getDouble("chance",1);
            var prerequisites=s.getStringList("prerequisites");
            if(!id.matches("[a-z0-9_-]{1,64}")||name.isBlank()||name.length()>80||!perm.matches("[a-z0-9_.-]{1,100}")||!icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,180}")||icon.contains("..")||!Double.isFinite(chance)||chance<=0||chance>1||metrics.size()>20||prerequisites.size()>256||prerequisites.stream().anyMatch(p->!p.matches("[a-z0-9_.-]{1,100}"))||metrics.isEmpty()&&prerequisites.isEmpty())throw new IOException("Bad spell "+id);
            var spell=new Spell(id,name,perm,icon,s.getString("category",""),s.getString("condition-text",""),Map.copyOf(metrics),List.copyOf(prerequisites),chance);
            spells.put(id,spell);for(String key:metrics.keySet())index.computeIfAbsent(key,k->new ArrayList<>()).add(spell);
        }
    }
    static boolean meets(Spell s,Map<String,Double> values){return s.requirements().entrySet().stream().allMatch(e->values.getOrDefault(e.getKey(),0d)>=e.getValue());}
}
