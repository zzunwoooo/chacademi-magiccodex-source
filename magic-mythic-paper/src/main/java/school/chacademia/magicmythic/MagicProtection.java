package school.chacademia.magicmythic;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import java.util.*;
/** Direct region query: deliberately never consult WorldGuard session/OP bypass. */
public final class MagicProtection implements Listener {
 private StateFlag flag; private boolean ready;
 interface Regions { Boolean state(Location at,String name); }
 private final Regions regions;
 public MagicProtection(){regions=this::query;}
 MagicProtection(Regions regions){this.regions=regions;ready=true;}
 static StateFlag claim(com.sk89q.worldguard.protection.flags.registry.FlagRegistry registry){
  if(registry.get("chacademi-magic")!=null)throw new IllegalStateException("chacademi-magic already registered; refusing ambiguous ownership");
  StateFlag owned=new StateFlag("chacademi-magic",true);registry.register(owned);return owned;
 }
 private Boolean query(Location at,String name){
  StateFlag selected=switch(name){case "pvp"->Flags.PVP;case "invincible"->Flags.INVINCIBILITY;default->flag;};
  var state=WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(at),(RegionAssociable)null,selected);
  return state==null?null:state==StateFlag.State.ALLOW;
 }
 private final Deque<Hit> hits=new ArrayDeque<>();
 private static final class Hit { final LivingEntity caster,target; EntityDamageEvent event; Hit(LivingEntity c,LivingEntity t){caster=c;target=t;} }
 public void register(){
  flag=claim(WorldGuard.getInstance().getFlagRegistry());ready=true;
 }
 public boolean at(Location location){
  if(!ready||location==null||location.getWorld()==null)return false;
  try{return !Boolean.FALSE.equals(regions.state(location,"magic"));}
  catch(RuntimeException|LinkageError e){return false;}
 }
 public boolean pair(LivingEntity caster,Location target){return caster!=null&&caster.isValid()&&at(caster.getLocation())&&at(target);}
 public boolean harm(LivingEntity caster,LivingEntity target){
  if(!pair(caster,target.getLocation()))return false;
  try{
   if(target instanceof Player){
    if(Boolean.TRUE.equals(regions.state(target.getLocation(),"invincible")))return false;
    if(caster instanceof Player && (Boolean.FALSE.equals(regions.state(target.getLocation(),"pvp"))||Boolean.FALSE.equals(regions.state(caster.getLocation(),"pvp"))))return false;
   }
   return true;
  }catch(RuntimeException|LinkageError e){return false;}
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
 public void observe(EntityDamageByEntityEvent event){
  Hit h=hits.peek();if(h!=null&&event.getEntity().equals(h.target)&&event.getDamager().equals(h.caster))h.event=event;
 }
 static boolean accepted(EntityDamageEvent event){return event!=null&&!event.isCancelled();}
 boolean damage(LivingEntity caster,LivingEntity target,double amount){
  if(!harm(caster,target))return false;
  if(amount<=0){var probe=new EntityDamageByEntityEvent(caster,target,EntityDamageEvent.DamageCause.MAGIC,0);Bukkit.getPluginManager().callEvent(probe);return accepted(probe);}
  Hit h=new Hit(caster,target);hits.push(h);
  try{target.damage(amount,caster);return accepted(h.event);}finally{hits.pop();}
 }
}
