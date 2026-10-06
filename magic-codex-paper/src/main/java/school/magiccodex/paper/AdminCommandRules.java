package school.magiccodex.paper;

import java.util.*;

/** Pure parsing and limits shared by Korean administrator commands and their tests. */
final class AdminCommandRules {
    static final List<String> STATS=List.of("현재마나","마나","마나회복","마법가속");
    static final List<String> CHANGES=List.of("설정","추가","감소");
    static final List<String> QUERIES=List.of("조회","설정","추가","감소");
    private AdminCommandRules(){}
    static double nonnegative(String value,double maximum){
        final double parsed;
        try{parsed=Double.parseDouble(value);}catch(NumberFormatException e){throw new IllegalArgumentException("숫자를 입력해 주세요.");}
        if(!Double.isFinite(parsed)||parsed<0||parsed>maximum)throw new IllegalArgumentException("수치 범위는 0~"+number(maximum)+"입니다.");
        return parsed;
    }
    static double changed(double previous,String action,double value,double maximum){
        if(!Double.isFinite(previous)||!Double.isFinite(value)||previous<0||value<0||value>maximum)throw new IllegalArgumentException("수치 범위를 확인해 주세요.");
        double next=switch(action){case "설정"->value;case "추가"->previous+value;case "감소"->Math.max(0,previous-value);default->throw new IllegalArgumentException("설정, 추가, 감소 중 선택해 주세요.");};
        if(!Double.isFinite(next)||next>maximum)throw new IllegalArgumentException("변경 후 수치가 최대 "+number(maximum)+"을 초과합니다.");
        return next;
    }
    static long scoreValue(String action,long amount){
        if(amount < -SchoolStore.MAX_SCORE||amount>SchoolStore.MAX_SCORE||(!action.equals("설정")&&amount<0))throw new IllegalArgumentException("점수 범위를 확인해 주세요. 추가·감소는 0 이상의 정수입니다.");
        return switch(action){case "조회","설정","추가"->amount;case "감소"->-amount;default->throw new IllegalArgumentException("조회, 설정, 추가, 감소 중 선택해 주세요.");};
    }
    static int affinity(String value){return switch(value){case "마력","power"->0;case "마나","mana"->1;case "마법가속","haste"->2;default->throw new IllegalArgumentException("성향은 마력, 마나, 마법가속 중 선택해 주세요.");};}
    static void validateCore(int nodes,double success,double bonus,double gain,int affinity){
        if(nodes<1||nodes>10)throw new IllegalArgumentException("코어 수는 1~10입니다.");
        for(double percent:new double[]{success,bonus})if(!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("성공률과 별잡기 보너스는 0~100%입니다.");
        if(!Double.isFinite(gain)||gain<=0||gain>10000)throw new IllegalArgumentException("능력치 상승 배율은 0 초과~10000입니다.");
        if(affinity<0||affinity>2)throw new IllegalArgumentException("등록된 코어 성향을 선택해 주세요.");
    }
    static List<String> statOptions(int position){return switch(position){case 1->STATS;case 2->CHANGES;case 3->List.of("<수치:0~1000000>","0","1","10","100");default->List.of();};}
    static List<String> itemOptions(String item,int argumentCount){
        if(item.equals("마법봉"))return switch(argumentCount){case 2->List.of("<마력:0~100000>","24");case 3->List.of("<추가마나:0~100000>","0");case 4->List.of("<마법가속:0~100000>","0");default->List.of();};
        if(item.equals("마력코어"))return switch(argumentCount){case 2->List.of("<코어수:1~10>","1","2","3","4","5","6","7","8","9","10");case 3->List.of("<성공률%:0~100>","50","100");case 4->List.of("<별잡기보너스%:0~100>","0","10");case 5->List.of("<능력치상승배율:0초과~10000>","1");case 6->List.of("마력","마나","마법가속");default->List.of();};
        return List.of();
    }
    static boolean titleAllowed(TitleDefinition title,boolean remove){return title!=null&&(remove||title.enabled());}
    /** An exact ID wins; a duplicate display name is always rejected. */
    static String resolve(Map<String,String> catalog,String input){
        if(input==null)return null;
        String key=input.strip();if(catalog.containsKey(key))return key;
        String id=null;for(var entry:catalog.entrySet())if(entry.getValue().equalsIgnoreCase(key)){if(id!=null)return null;id=entry.getKey();}return id;
    }
    static List<String> catalogChoices(Map<String,String> catalog){
        var values=new TreeSet<String>(catalog.keySet());for(String value:catalog.values())if(resolve(catalog,value)!=null)values.add(value);return List.copyOf(values);
    }
    static List<String> tailChoices(Collection<String> choices,String[] args,int from){
        if(from>=args.length)return List.of();
        String preceding=String.join(" ",Arrays.copyOfRange(args,from,args.length-1));
        if(!preceding.isEmpty())preceding+=" ";String prefix=preceding;
        return choices.stream().filter(s->s.regionMatches(true,0,prefix,0,prefix.length())).map(s->s.substring(prefix.length())).toList();
    }
    static List<String> filter(Collection<String> choices,String prefix){return choices.stream().filter(Objects::nonNull).filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).distinct().sorted().limit(256).toList();}
    static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
}
