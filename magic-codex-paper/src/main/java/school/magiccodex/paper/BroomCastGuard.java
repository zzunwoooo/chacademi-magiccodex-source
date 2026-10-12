package school.magiccodex.paper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Set;
/** Optional Motion link. No mana or cooldown changes here. */
final class BroomCastGuard {
 private static final Set<String> MOVES_CASTER=Set.of("gale_dash","light_leap");
 static boolean blocked(String spell,boolean riding){return riding&&(MOVES_CASTER.contains(spell)||"flight".equals(spell));}
 static boolean blocked(Player player,String spell){
  if(!MOVES_CASTER.contains(spell)&&!"flight".equals(spell))return false;
  var plugin=Bukkit.getPluginManager().getPlugin("ChacademyMotion");
  if(plugin==null||!plugin.isEnabled())return false;
  try{return blocked(spell,Boolean.TRUE.equals(plugin.getClass().getMethod("isRidingBroom",Player.class).invoke(plugin,player)));}
  catch(ReflectiveOperationException|LinkageError e){return false;}
 }
}
