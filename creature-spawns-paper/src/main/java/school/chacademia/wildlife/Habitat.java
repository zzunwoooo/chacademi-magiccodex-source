package school.chacademia.wildlife;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** habitats.yml 한 항목을 로드/리로드 때 한 번만 해석해 둔 불변 값. 스폰 후보마다 설정을 다시 읽지 않는다. */
record Habitat(String id,Set<String> biomes,Set<String> seasons,boolean water,int minY,int maxY,int time,
               boolean thunder,boolean fullMoon,boolean nearWater,boolean naturalGround,
               int clearanceRadius,int clearanceHeight,int maxBlockLight,
               Set<String> structures,double structureRadius,int groupMin,int groupMax,int cap,double capRadius) {
    static final int ANY=0,DAY=1,NIGHT=2;

    /** 필수 값이 잘못되면 예외, medium/time 오타는 경고 후 안전한 기본값(land/any)으로 둔다. */
    static Habitat parse(String id,ConfigurationSection s,List<String> warnings) {
        if(s==null) throw new IllegalArgumentException("잘못된 서식지: "+id);
        if(s.getStringList("biomes").isEmpty() || s.getInt("group-min")<1 || s.getInt("group-max")>6
            || s.getInt("group-max")<s.getInt("group-min") || s.getInt("cap")<s.getInt("group-max")
            || s.getInt("min-y")>s.getInt("max-y")) throw new IllegalArgumentException("잘못된 서식지: "+id);
        Object seasons=s.get("seasons");
        if(seasons!=null && !(seasons instanceof List<?>)) throw new IllegalArgumentException("seasons는 목록으로 지정하세요: "+id);
        var normalized=new LinkedHashSet<String>();
        if(seasons instanceof List<?> values) for(Object value:values) normalized.add(WildlifePlugin.seasonId(value,id));
        String medium=String.valueOf(s.getString("medium","land")).toLowerCase(Locale.ROOT).trim();
        if(!medium.equals("land")&&!medium.equals("water")) { warnings.add(id+": medium '"+medium+"' → land"); medium="land"; }
        String period=String.valueOf(s.getString("time","any")).toLowerCase(Locale.ROOT).trim();
        int time=switch(period) { case "day" -> DAY; case "night" -> NIGHT; default -> ANY; };
        if(time==ANY&&!period.equals("any")) warnings.add(id+": time '"+period+"' → any");
        return new Habitat(id,Set.copyOf(s.getStringList("biomes")),Collections.unmodifiableSet(normalized),medium.equals("water"),
            s.getInt("min-y"),s.getInt("max-y"),time,s.getBoolean("thunder"),s.getBoolean("full-moon"),
            s.getBoolean("near-water"),s.getBoolean("natural-ground"),
            (s.getInt("clearance-width",1)-1)/2,s.getInt("clearance-height",2),s.getInt("max-block-light",7),
            Set.copyOf(s.getStringList("structures")),s.getDouble("structure-radius",48),
            s.getInt("group-min"),s.getInt("group-max"),s.getInt("cap"),s.getDouble("cap-radius",64));
    }
}
