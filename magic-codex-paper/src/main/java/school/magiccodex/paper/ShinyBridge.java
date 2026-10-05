package school.magiccodex.paper;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import school.magiccodex.protocol.ShinyProtocol;

/** Server-owned identity, persisted on the entity; no client-supplied rarity decisions. */
final class ShinyBridge implements Listener,CommandExecutor,AutoCloseable {
 private final MagicCodexBridge plugin;private final TamingBridge taming;private final NamespacedKey key;
 private final Map<UUID,LivingEntity> loaded=new HashMap<>();private YamlConfiguration config;private int tick;private org.bukkit.scheduler.BukkitTask task;
 ShinyBridge(MagicCodexBridge p,TamingBridge t){plugin=p;taming=t;key=new NamespacedKey(p,"shiny");if(!new java.io.File(p.getDataFolder(),"shiny.yml").exists())p.saveResource("shiny.yml",false);reload();
  p.getServer().getMessenger().registerOutgoingPluginChannel(p,ShinyProtocol.CHANNEL);Bukkit.getPluginManager().registerEvents(this,p);p.getCommand("이로치관리").setExecutor(this);
  for(var world:Bukkit.getWorlds())for(var e:world.getLivingEntities())remember(e);
  task=Bukkit.getScheduler().runTaskTimer(p,this::tick,10,10);
 }
 private void reload(){config=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"shiny.yml"));}
 boolean isShiny(LivingEntity e){return e.getPersistentDataContainer().getOrDefault(key,PersistentDataType.BYTE,(byte)0)==1||e.getScoreboardTags().contains("chacademia_shiny");}
 private void appearance(LivingEntity e,boolean shiny){
  boolean custom=ShinyModelSwap.customAppearance(e.getScoreboardTags().contains("chacademia_custom"),taming.profileId(e)!=null&&taming.configuredMythic(e));
  if(custom){
   if(Bukkit.getPluginManager().isPluginEnabled("ModelEngine"))CustomShinyModels.apply(e,shiny);
   else if(e.getScoreboardTags().contains("chacademia_custom"))throw new IllegalStateException("ModelEngine is unavailable");
   if(e.getCustomName()!=null)e.setCustomName((shiny?"§e":"§f")+ChatColor.stripColor(e.getCustomName()));
  }
 }
 private void remember(LivingEntity e){if(isShiny(e)){e.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);loaded.put(e.getUniqueId(),e);try{appearance(e,true);}catch(RuntimeException|LinkageError ex){plugin.getLogger().warning("이로치 모델 연결: "+ex.getMessage());}}}
 void mark(LivingEntity e,boolean shiny){appearance(e,shiny);e.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)(shiny?1:0));if(shiny){e.addScoreboardTag("chacademia_shiny");loaded.put(e.getUniqueId(),e);}else{e.removeScoreboardTag("chacademia_shiny");loaded.remove(e.getUniqueId());}for(var p:e.getTrackedBy())send(p,e,shiny);}
 private void send(Player p,LivingEntity e,boolean value){if(p.getListeningPluginChannels().contains(ShinyProtocol.CHANNEL))p.sendPluginMessage(plugin,ShinyProtocol.CHANNEL,ShinyProtocol.encode(new ShinyProtocol.State(e.getEntityId(),e.getUniqueId(),value)));}
 @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void spawn(CreatureSpawnEvent event){
  var e=event.getEntity();var reason=event.getSpawnReason().name();
  Bukkit.getScheduler().runTaskLater(plugin,()->{
   if(!e.isValid())return;if(isShiny(e)){remember(e);return;}
   if(e.getPersistentDataContainer().has(key,PersistentDataType.BYTE)||!config.getBoolean("enabled",true)||!config.getStringList("spawn-reasons").contains(reason))return;
   if(config.getStringList("excluded-worlds").contains(e.getWorld().getName()))return;
   if(reason.equals("DEFAULT")&&e.getType()!=EntityType.ENDER_DRAGON)return;
   String profile=taming.profileId(e);if(profile==null||!taming.allowsShinyRoll(profile))return;
   // CUSTOM is restricted to our configured MythicMobs IDs by profileId.
   if(reason.equals("CUSTOM")&&!taming.configuredMythic(e))return;
   double chance=config.getDouble("chances."+profile,config.getDouble("default-chance",1d/4096));
   if(!Double.isFinite(chance))chance=0;mark(e,ThreadLocalRandom.current().nextDouble()<Math.clamp(chance,0,1));
  },3);
 }
 @EventHandler public void load(EntitiesLoadEvent event){for(var e:event.getEntities())if(e instanceof LivingEntity living)remember(living);}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void transform(EntityTransformEvent event){
  if(!(event.getEntity() instanceof LivingEntity before)||!before.getPersistentDataContainer().has(key,PersistentDataType.BYTE))return;
  boolean value=isShiny(before);for(var after:event.getTransformedEntities())if(after instanceof LivingEntity living)Bukkit.getScheduler().runTask(plugin,()->{if(living.isValid())mark(living,value);});
 }
 @EventHandler public void unload(EntitiesUnloadEvent event){for(var e:event.getEntities())loaded.remove(e.getUniqueId());}
 @EventHandler public void track(PlayerTrackEntityEvent event){if(event.getEntity() instanceof LivingEntity e)send(event.getPlayer(),e,isShiny(e));}
 /** Entities tracked during login were announced before the client registered our channel; resend once it does. */
 @EventHandler public void channel(PlayerRegisterChannelEvent event){
  if(!ShinyProtocol.CHANNEL.equals(event.getChannel()))return;var p=event.getPlayer();
  Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline())return;for(var e:List.copyOf(loaded.values()))if(e.isValid()&&e.getTrackedBy().contains(p))send(p,e,true);});
 }
 private void tick(){tick++;
  for(var e:List.copyOf(loaded.values())){
   if(!e.isValid()||e.isDead()){loaded.remove(e.getUniqueId());continue;}
   var viewers=e.getTrackedBy();if(viewers.isEmpty())continue;
   var loc=e.getLocation().add(0,Math.min(e.getHeight()*.6,3),0);
   if(config.getBoolean("particles",true))e.getWorld().spawnParticle(Particle.END_ROD,loc,2,Math.max(.2,Math.min(e.getWidth(),2))*.5,.35,.35,.012);
   if(tick%10==0)for(var p:viewers)send(p,e,true);
   int interval=Math.clamp(config.getInt("sound-interval-seconds",12),5,300)*2;
   if(config.getBoolean("sound",true)&&tick%interval==0){
    for(var p:viewers)if(p.getLocation().distanceSquared(loc)<24*24){p.playSound(loc,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.5f,1.3f);p.playSound(loc,Sound.BLOCK_NOTE_BLOCK_CHIME,.25f,1.8f);}
   }
  }
 }
 public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
  if(!sender.hasPermission("magiccodex.shiny.admin"))return true;
  if(args.length==1&&args[0].equalsIgnoreCase("reload")){reload();sender.sendMessage("이로치 설정을 다시 불러왔습니다.");return true;}
  if(!(sender instanceof Player p)||args.length!=1||!(args[0].equals("지정")||args[0].equals("해제"))){sender.sendMessage("/이로치관리 지정 | 해제 | reload");return true;}
  var target=p.getTargetEntity(16);if(!(target instanceof LivingEntity e)||taming.profileId(e)==null){p.sendMessage("등록된 야생 교화 대상을 바라봐 주세요.");return true;}
  if(taming.captureLocked(e)){p.sendMessage("교화가 끝난 뒤 변경해 주세요.");return true;}
  try{mark(e,args[0].equals("지정"));p.sendMessage(args[0].equals("지정")?"이로치로 지정했습니다.":"일반 개체로 변경했습니다.");}catch(RuntimeException|LinkageError ex){p.sendMessage("이로치 전용 모델을 확인해 주세요.");plugin.getLogger().warning("이로치 모델 변경 실패: "+ex.getMessage());}return true;
 }
 public void close(){if(task!=null)task.cancel();loaded.clear();}
}
