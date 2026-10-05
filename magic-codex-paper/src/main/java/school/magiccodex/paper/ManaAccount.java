package school.magiccodex.paper;

import java.util.*;
import school.magiccodex.protocol.ManaProtocol.Snapshot;

/** Main-thread account. Immutable snapshots are published separately for asynchronous readers. */
final class ManaAccount {
    double current,baseMaximum,baseRegen,baseHaste;
    private final Map<String,Double> hasteModifiers=new HashMap<>();
    private final Map<String,double[]> modifiers=new HashMap<>();
    private final Map<String,Double> regenerationMultipliers=new HashMap<>();
    final Map<String,Long> cooldowns=new HashMap<>();
    ManaAccount(double current,double maximum,double regeneration){
        valid(maximum);valid(regeneration);valid(current);
        baseMaximum=maximum;baseRegen=regeneration;this.current=Math.min(current,maximum);
    }
    static void valid(double value){if(!Double.isFinite(value)||value<0||value>1_000_000)throw new IllegalArgumentException("Expected 0..1000000");}
    Snapshot snapshot(){
        double max=baseMaximum,regen=baseRegen;
        for(var m:modifiers.values()){max+=m[0];regen+=m[1];}
        for(double factor:regenerationMultipliers.values())regen*=factor;
        max=Math.clamp(max,0,1_000_000);regen=Math.clamp(regen,0,1_000_000);
        double haste=baseHaste;for(double bonus:hasteModifiers.values())haste+=bonus;
        return new Snapshot(Math.min(current,max),max,regen,Math.clamp(haste,0,1_000_000));
    }
    void hasteModifier(String key,double value){
        if(key==null||!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid modifier key");
        school.magiccodex.protocol.MagicHaste.valid(value);
        if(!hasteModifiers.containsKey(key)&&hasteModifiers.size()>=128)throw new IllegalArgumentException("Too many modifiers");
        if(value==0)hasteModifiers.remove(key);else hasteModifiers.put(key,value);
    }
    void normalize(){current=snapshot().current();}
    void modifier(String key,double max,double regen){
        if(key==null||!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!Double.isFinite(max)||!Double.isFinite(regen)
                ||Math.abs(max)>1_000_000||Math.abs(regen)>1_000_000)throw new IllegalArgumentException("Invalid modifier");
        if(!modifiers.containsKey(key)&&modifiers.size()>=128)throw new IllegalArgumentException("Too many mana modifiers");
        modifiers.put(key,new double[]{max,regen});normalize();
    }
    void removeModifier(String key){modifiers.remove(key);normalize();}
    void regenerationMultiplier(String key,double value){
        if(key==null||!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!Double.isFinite(value)||value<0||value>10)throw new IllegalArgumentException("Invalid regeneration multiplier");
        if(value==1){regenerationMultipliers.remove(key);return;}
        if(!regenerationMultipliers.containsKey(key)&&regenerationMultipliers.size()>=128)throw new IllegalArgumentException("Too many modifiers");
        regenerationMultipliers.put(key,value);
    }
    void regenerate(){var s=snapshot();current=Math.min(s.maximum(),s.current()+s.regeneration());}
    boolean consume(double cost){valid(cost);normalize();if(current<cost)return false;current-=cost;return true;}
    void add(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid mana delta");current=Math.clamp(current+value,0,snapshot().maximum());}
    long remaining(String id,long now){return Math.max(0,cooldowns.getOrDefault(id,0L)-now);}
    void cooldown(String id,long until,long now){cooldowns.entrySet().removeIf(e->e.getValue()<=now);cooldowns.put(id,until);}
}
