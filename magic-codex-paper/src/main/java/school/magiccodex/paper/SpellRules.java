package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Operator-owned runtime routes. No arbitrary command or client-supplied statistic is accepted. */
final class SpellRules {
    enum Target {SELF, AIM, ENTITY, AIR_ENTITY, BURNING_ENTITY, BLOCK}
    record Rule(String id,String name,String skill,Target target,boolean utility,int range,double base,double ratio){
        double damage(double power){return Math.clamp(base+ratio*Math.clamp(power,0,1_000_000),0,1_000_000);}
    }
    static Map<String,Rule> load(File file)throws Exception {
        var yaml=new YamlConfiguration();yaml.load(file);var root=yaml.getConfigurationSection("spells");
        if(root==null||root.getKeys(false).size()>512)throw new IllegalArgumentException("spells 목록을 확인하세요.");
        var result=new LinkedHashMap<String,Rule>();
        for(String id:root.getKeys(false)){
            var s=root.getConfigurationSection(id);if(s==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException(id);
            String name=s.getString("name",""),skill=s.getString("skill","");
            if(name.isBlank()||name.length()>64||!skill.matches("CHA_[a-z0-9_]{1,80}"))throw new IllegalArgumentException(id+" name/skill");
            var target=Target.valueOf(s.getString("target","SELF"));int range=s.getInt("range",24);
            double base=s.getDouble("base-damage",0),ratio=s.getDouble("power-ratio",0);
            if(range<1||range>48||!Double.isFinite(base)||!Double.isFinite(ratio)||base<0||base>10000||ratio<0||ratio>100)throw new IllegalArgumentException(id+" 수치");
            result.put(id,new Rule(id,name,skill,target,s.getBoolean("utility",false),range,base,ratio));
        }
        return Collections.unmodifiableMap(result);
    }
}
