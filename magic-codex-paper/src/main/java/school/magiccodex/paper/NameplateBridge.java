package school.magiccodex.paper;
import java.util.*;
import school.magiccodex.paper.NameplateStore.Label;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import school.magiccodex.protocol.NameplateProtocol;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
/** MagicCodex owns storage and commands; Citizens/ChacaNPC remain optional. Main-thread access only. */
final class NameplateBridge {
 private final MagicCodexBridge plugin;
 private final Map<String,Label> labels=new HashMap<>();

 record Npc(Entity entity,String key,String name){}
 NameplateBridge(MagicCodexBridge plugin){this(plugin,true);}
 NameplateBridge(MagicCodexBridge plugin,boolean activate){
  this.plugin=plugin;
  try{labels.putAll(NameplateStore.load(plugin.getDataFolder().toPath().resolve("nameplates.yml")));}catch(IllegalArgumentException ex){throw new IllegalStateException("명찰 저장소가 잘못되어 로딩을 중단했어요. 원본을 보존하고 수정해 주세요",ex);}
  if(!activate)return;
  plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,NameplateProtocol.CHANNEL);
  plugin.getServer().getScheduler().runTaskTimer(plugin,this::push,20,20);
  commands();
 }
 Npc npc(Entity entity){
  if(!(entity instanceof LivingEntity))return null;
  if(Bukkit.getPluginManager().isPluginEnabled("Citizens"))try{
   Class<?> api=Class.forName("net.citizensnpcs.api.CitizensAPI");Object registry=api.getMethod("getNPCRegistry").invoke(null);
   Class<?> registryType=Class.forName("net.citizensnpcs.api.npc.NPCRegistry"),npcType=Class.forName("net.citizensnpcs.api.npc.NPC");
   Object n=registryType.getMethod("getNPC",Entity.class).invoke(registry,entity);
   if(n!=null){Object data=npcType.getMethod("data").invoke(n);
    String key="citizens-"+npcType.getMethod("getUniqueId").invoke(n);
    // ChacaNPC's durable character identity also survives NPC deletion/recreation and entity UUID changes.
    Class<?> store=npcType.getMethod("data").getReturnType();
    if(Boolean.TRUE.equals(store.getMethod("has",String.class).invoke(data,"chacanpc-id"))){Object id=store.getMethod("get",String.class).invoke(data,"chacanpc-id");if(id!=null)key="character-"+HexFormat.of().formatHex(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    return new Npc(entity,key,(String)npcType.getMethod("getName").invoke(n));
   }
  }catch(ReflectiveOperationException|LinkageError ex){/* optional API unavailable: explicit tagged entities still work */}
  if(entity instanceof Player)return null;
  if(!entity.hasMetadata("NPC")&&!entity.getScoreboardTags().contains("magiccodex_npc"))return null;
  var pdc=entity.getPersistentDataContainer();var key=new NamespacedKey(plugin,"nameplate_id");
  String stable=pdc.get(key,PersistentDataType.STRING);if(stable==null){stable=entity.getUniqueId().toString();pdc.set(key,PersistentDataType.STRING,stable);}
  return new Npc(entity,"entity-"+stable,entity.getCustomName()==null?entity.getName():entity.getCustomName());
 }
 void push(){
  for(Player viewer:Bukkit.getOnlinePlayers()){
   if(!viewer.getListeningPluginChannels().contains(NameplateProtocol.CHANNEL))continue;
   var out=new ArrayList<NameplateProtocol.Entry>();
   var entities=new ArrayList<Entity>(viewer.getNearbyEntities(48,48,48));entities.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(viewer.getLocation())));
   for(Entity e:entities){if(out.size()==NameplateProtocol.MAX_ENTRIES)break;if(e.equals(viewer)||e.getLocation().distanceSquared(viewer.getLocation())>48*48)continue;
    try{Npc n=npc(e);Label label=n==null?null:labels.get(n.key);if(label!=null){out.add(new NameplateProtocol.Entry(e.getEntityId(),e.getUniqueId(),4,label.name(),label.title()));continue;}
     if(n!=null||!(e instanceof Player p)||p.hasMetadata("NPC")||!viewer.canSee(p))continue;
     int h=plugin.questHouse(p);if(h>=0&&h<4)out.add(new NameplateProtocol.Entry(e.getEntityId(),e.getUniqueId(),h,plugin.names().name(p),""));
    }catch(IllegalArgumentException ignored){/* malformed authority value falls back to vanilla */}
   }
   viewer.sendPluginMessage(plugin,NameplateProtocol.CHANNEL,NameplateProtocol.encode(out));
  }
 }
 private List<Npc> nearby(Player p){var list=new ArrayList<Npc>();for(Entity e:p.getNearbyEntities(24,24,24)){if(e.getLocation().distanceSquared(p.getLocation())>24*24)continue;Npc n=npc(e);if(n!=null)list.add(n);}list.sort(Comparator.comparingDouble(n->n.entity.getLocation().distanceSquared(p.getLocation())));return list;}
 Npc target(Player p,String query)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  var all=nearby(p);List<Npc> matches;
  if(query.equals("근처")){if(all.isEmpty())throw ChacaApi.error("24블록 안에 NPC가 없어요");double first=all.getFirst().entity.getLocation().distanceSquared(p.getLocation());matches=all.stream().filter(n->Math.abs(n.entity.getLocation().distanceSquared(p.getLocation())-first)<.01).toList();}
  else matches=all.stream().filter(n->query.equals(n.name)||query.equals(n.key)||query.equals(n.entity.getUniqueId().toString())||(labels.containsKey(n.key)&&query.equals(labels.get(n.key).name()))).toList();
  if(matches.isEmpty())throw ChacaApi.error("24블록 안에서 이름/명찰/ID가 일치하는 NPC를 찾지 못했어요");
  if(matches.size()!=1)throw ChacaApi.error("대상이 여러 명이에요. ID로 지정해 주세요: "+String.join(", ",matches.stream().limit(8).map(n->n.name+" ["+n.key+"]").toList()));return matches.getFirst();
 }
 private void save(Map<String,Label> next)throws Exception{
  NameplateStore.save(plugin.getDataFolder().toPath().resolve("nameplates.yml"),next);labels.clear();labels.putAll(next);
 }
 private void commands(){
  var branch=Commands.literal("명찰").requires(s->s.getSender().hasPermission("magiccodex.nameplates.admin"));
  for(String action:List.of("설정","조회","초기화")){
   var target=Commands.argument("대상",StringArgumentType.string()).suggests((ctx,b)->{if(ctx.getSource().getSender() instanceof Player p){b.suggest("근처");for(var n:nearby(p)){b.suggest(StringArgumentType.escapeIfRequired(n.name));b.suggest(n.key);Label l=labels.get(n.key);if(l!=null)b.suggest(StringArgumentType.escapeIfRequired(l.name()));}}return b.buildFuture();});
   if(action.equals("설정"))target.then(Commands.argument("이름",StringArgumentType.string()).then(Commands.argument("직책",StringArgumentType.string()).executes(c->run(c,action))));
   else target.executes(c->run(c,action));branch.then(Commands.literal(action).then(target));
  }
  ChacaApi.branch(plugin,ChacaApi.NPC관리,branch,"NPC 고화질 명찰 설정·조회·초기화 (24블록)","/NPC관리 명찰 설정 근처 \"엘레나\" \"교수\"");
 }
 int run(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> c,String action)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  if(!(c.getSource().getSender() instanceof Player p))throw ChacaApi.error("게임 안의 관리자만 사용할 수 있어요");
  if(!p.hasPermission("magiccodex.nameplates.admin"))throw ChacaApi.error("명찰 관리자 권한이 필요해요");
  Npc n=target(p,StringArgumentType.getString(c,"대상"));Label current=labels.get(n.key);
  if(action.equals("조회")){p.sendMessage(n.name+" ["+n.key+"] · "+(current==null?"미설정 (기존 이름표)":current.name()+" / "+current.title()));return 1;}
  var next=new HashMap<>(labels);
  if(action.equals("설정")){String name=StringArgumentType.getString(c,"이름"),title=StringArgumentType.getString(c,"직책");try{NameplateProtocol.text(name,48,false);NameplateProtocol.text(title,24,true);}catch(IllegalArgumentException ex){throw ChacaApi.error(ex.getMessage());}next.put(n.key,new Label(name,title));}else next.remove(n.key);
  try{save(next);}catch(Exception ex){plugin.getLogger().warning("Nameplate save failed: "+ex);throw ChacaApi.error("명찰 저장에 실패했어요. 기존 설정을 유지했어요");}push();p.sendMessage("명찰 "+action+" 완료: "+n.name+" ["+n.key+"]");return 1;
 }
}
