package school.magiccodex.discovery;
import java.io.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
final class Definitions {
    record Spell(String id,String name,String permission,String icon,String category,String description,Map<String,Double> requirements,List<String> prerequisites,double chance){}
    static final Set<String> PENDING=Set.of("crimson_heart","feather_step","wind_vault","handful_of_snow","wet_footprints","pressure_weight","cold_storage");
    final Map<String,Spell> spells=new LinkedHashMap<>();
    final Map<String,Spell> manual=new LinkedHashMap<>();
    final Map<String,List<Spell>> index=new HashMap<>();
    final List<Spell> meta=new ArrayList<>(),stateOnly=new ArrayList<>();
    Definitions(File file)throws Exception{
        var y=new YamlConfiguration();y.load(file);var root=y.getConfigurationSection("spells");
        if(root==null||root.getKeys(false).size()>4096)throw new IOException("Expected up to 4096 spells");
        for(String id:root.getKeys(false)){
            var s=Objects.requireNonNull(root.getConfigurationSection(id));var req=s.getConfigurationSection("requirements");Map<String,Double> metrics=new LinkedHashMap<>();
            if(req!=null)for(String key:req.getKeys(true))if(!req.isConfigurationSection(key)){
                Object raw=req.get(key);double v=raw instanceof Number number?number.doubleValue():Double.NaN;if(!key.matches("[a-z0-9_.-]{1,100}")||!Double.isFinite(v)||v<=0||v>1e9)throw new IOException("Bad requirement: "+id+"/"+key);
                // 오너 결정 4 (2026-10-21): "친구 N명" = 현재 등록된 서로 다른 친구 수(상태값). 서버에 남아 있는 예전 discoveries.yml 의 friend.made 도 같은 뜻으로 읽는다.
                metrics.put(key.equals("friend.made")?"state.friends":key,v);
            }
            String name=s.getString("name",""),perm=s.getString("permission",""),icon=s.getString("icon","");double chance=s.getDouble("chance",1);
            var prerequisites=s.getStringList("prerequisites");
            if(!id.matches("[a-z0-9_-]{1,64}")||name.isBlank()||name.length()>80||!perm.matches("[a-z0-9_.-]{1,100}")||!icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,180}")||icon.contains("..")||!Double.isFinite(chance)||chance<=0||chance>1||metrics.size()>20||prerequisites.size()>4096||prerequisites.stream().anyMatch(p->!p.matches("[a-z0-9_.-]{1,100}"))||metrics.isEmpty()&&prerequisites.isEmpty())throw new IOException("Bad spell "+id);
            var spell=new Spell(id,name,perm,icon,s.getString("category",""),s.getString("condition-text",""),Map.copyOf(metrics),List.copyOf(prerequisites),chance);
            if(!PENDING.contains(id)&&metrics.isEmpty())meta.add(spell);
            else if(!PENDING.contains(id)&&metrics.keySet().stream().allMatch(key->key.startsWith("state.")))stateOnly.add(spell);
            spells.put(id,spell);if(!PENDING.contains(id))for(String key:metrics.keySet())index.computeIfAbsent(key,k->new ArrayList<>()).add(spell);
        }
    }
    void manual(File file)throws Exception{
        var overrides=new Definitions(file);
        for(var spell:overrides.spells.values()){
            if(!spell.requirements().equals(Map.of(conditionKey(spell.id()),1d))||!spell.prerequisites().isEmpty()||spell.chance()!=1)throw new IOException("Invalid manual condition "+spell.id());
            manual.put(spell.id(),spell);spells.put(spell.id(),spell);
        }
        index.clear();meta.clear();stateOnly.clear();
        for(var spell:spells.values())if(!manual.containsKey(spell.id())&&!PENDING.contains(spell.id())){
            if(spell.requirements().isEmpty())meta.add(spell);
            else if(spell.requirements().keySet().stream().allMatch(k->k.startsWith("state.")))stateOnly.add(spell);
            for(String key:spell.requirements().keySet())index.computeIfAbsent(key,k->new ArrayList<>()).add(spell);
        }
    }
    static String conditionKey(String id){return "trace.condition."+id;}
    static String conditionPermission(String id){return "magic.condition."+id;}
    static boolean meets(Spell s,Map<String,Double> values){return s.requirements().entrySet().stream().allMatch(e->values.getOrDefault(e.getKey(),0d)>=e.getValue());}
}
