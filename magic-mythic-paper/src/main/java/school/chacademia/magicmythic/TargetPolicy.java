package school.chacademia.magicmythic;
import java.util.*;
final class TargetPolicy {
 static boolean excluded(boolean armor,boolean npc,boolean tamed,Set<String> tags,Set<String> deniedTags,String mythic,List<String> prefixes){
  return armor||npc||tamed||tags.stream().anyMatch(deniedTags::contains)||(mythic!=null&&prefixes.stream().anyMatch(mythic::startsWith));
 }
}
