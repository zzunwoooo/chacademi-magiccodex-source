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
    try{Npc n=npc(e);Label label=n==null?null:labels.get(n.key);if(label!=null){var titles=NameplateTitles.npc(label,plugin.titleService());out.add(new NameplateProtocol.Entry(e.getEntityId(),e.getUniqueId(),4,NameplateTitles.plainName(label.name(),titles),NameplateTitles.runs(titles)));continue;}
     if(n!=null||!(e instanceof Player p)||p.hasMetadata("NPC")||!viewer.canSee(p))continue;
     int h=plugin.questHouse(p);if(h>=0&&h<4){var service=plugin.titleService();var titles=service==null?List.<TitleDefinition>of():service.nameplateTitles(p.getUniqueId());out.add(new NameplateProtocol.Entry(e.getEntityId(),e.getUniqueId(),h,NameplateTitles.plainName(plugin.names().name(p),titles),NameplateTitles.runs(titles)));}
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
 private com.mojang.brigadier.builder.RequiredArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack,String> targetArgument(){
  return Commands.argument("대상",new NameplateArgument()).suggests((ctx,b)->{if(ctx.getSource().getSender() instanceof Player p){b.suggest("근처");for(var n:nearby(p)){b.suggest(StringArgumentType.escapeIfRequired(n.name));b.suggest(n.key);Label l=labels.get(n.key);if(l!=null)b.suggest(StringArgumentType.escapeIfRequired(l.name()));}}return b.buildFuture();});
 }
 Map<String,String> titleChoices(int side){var service=plugin.titleService();if(service==null)return Map.of();var out=new TreeMap<String,String>();for(var e:service.registeredTitles(false).entrySet()){var d=service.nameplateDefinition(e.getKey());if(d!=null&&(side<0||d.side()==side))out.put(e.getKey(),e.getValue());}return Map.copyOf(out);}
 private com.mojang.brigadier.builder.RequiredArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack,String> titleArgument(int side){return Commands.argument("칭호",new NameplateArgument()).suggests((c,b)->{titleChoices(side).forEach((id,name)->b.suggest(id,new com.mojang.brigadier.LiteralMessage(name)));return b.buildFuture();});}
 com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack> commandTree(){
  var branch=Commands.literal("명찰").requires(s->s.getSender().hasPermission("magiccodex.nameplates.admin"));
  for(String action:List.of("설정","조회","초기화","이름")){
   var target=targetArgument();if(action.equals("설정"))target.then(Commands.argument("이름",new NameplateArgument()).then(titleArgument(-1).executes(c->run(c,action))));
   else if(action.equals("이름"))target.then(Commands.argument("이름",new NameplateArgument()).executes(c->run(c,action)));else target.executes(c->run(c,action));branch.then(Commands.literal(action).then(target));
  }
  var target=targetArgument().executes(c->run(c,"조회"));for(String side:List.of("앞","뒤"))target.then(Commands.literal(side).then(Commands.literal("비우기").executes(c->run(c,"칭호"+side+"비우기"))).then(titleArgument(side.equals("앞")?0:1).executes(c->run(c,"칭호"+side))));
  target.then(Commands.literal("초기화").executes(c->run(c,"칭호초기화")));branch.then(Commands.literal("칭호").then(target));return branch;
 }
 private void commands(){ChacaApi.branch(plugin,ChacaApi.NPC관리,commandTree(),"NPC 명찰 이름·기존 앞/뒤 칭호 ID 설정 (24블록)","/NPC관리 명찰 설정 근처 \"엘레나\" suffix_researcher");}
 TitleDefinition requireTitle(String query,int side)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  var service=plugin.titleService();if(service==null)throw ChacaApi.error("칭호 서비스를 사용할 수 없어요");var d=service.nameplateDefinition(query);
  if(d==null){var matches=titleChoices(side).keySet().stream().map(service::nameplateDefinition).filter(x->x.name().equals(query)).toList();if(matches.size()>1)throw ChacaApi.error("같은 이름의 칭호가 여러 개예요. ID로 지정해 주세요: "+String.join(", ",matches.stream().map(TitleDefinition::id).toList()));if(matches.size()==1)d=matches.getFirst();}
  if(d==null)throw ChacaApi.error("등록된 활성 칭호 ID가 아니에요. /컨텐츠관리 칭호 목록에서 확인해 주세요");if(side>=0&&d.side()!=side)throw ChacaApi.error("이 칭호는 "+(d.side()==0?"앞":"뒤")+" 슬롯 칭호예요");return d;
 }
 int run(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> c,String action)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  if(!(c.getSource().getSender() instanceof Player p))throw ChacaApi.error("게임 안의 관리자만 사용할 수 있어요");if(!p.hasPermission("magiccodex.nameplates.admin"))throw ChacaApi.error("명찰 관리자 권한이 필요해요");
  Npc n=target(p,StringArgumentType.getString(c,"대상"));Label current=labels.get(n.key);if(action.equals("조회")){p.sendMessage(n.name+" ["+n.key+"] · "+(current==null?"미설정 (기존 이름표)":current.name()+" · 앞="+current.prefixId()+" 뒤="+current.suffixId()+" · "+NameplateTitles.combined(NameplateTitles.npc(current,plugin.titleService()))+(current.legacyTitle().isEmpty()?"":" · 구형 직책 보관됨: 칭호 ID 지정 필요")));return 1;}
  var next=new HashMap<>(labels);if(action.equals("초기화"))next.remove(n.key);else{
   if(current==null)current=new Label(ChatColor.stripColor(n.name),"","");String name=current.name(),prefix=current.prefixId(),suffix=current.suffixId();
   if(action.equals("설정")||action.equals("이름")){name=StringArgumentType.getString(c,"이름");try{NameplateProtocol.text(name,48,false);}catch(IllegalArgumentException ex){throw ChacaApi.error(ex.getMessage());}}
   if(action.equals("칭호초기화")){prefix=suffix="";}else if(action.endsWith("비우기")){if(action.contains("앞"))prefix="";else suffix="";}
   else if(action.equals("설정")||action.equals("칭호앞")||action.equals("칭호뒤")){String input=StringArgumentType.getString(c,"칭호");if(action.equals("설정")&&input.isEmpty())prefix=suffix="";else{var d=requireTitle(input,action.equals("설정")?-1:action.equals("칭호앞")?0:1);if(d.side()==0)prefix=d.id();else suffix=d.id();}}
   next.put(n.key,new Label(name,prefix,suffix,action.equals("이름")?current.legacyTitle():""));
  }
  try{save(next);}catch(Exception ex){plugin.getLogger().warning("Nameplate save failed: "+ex);throw ChacaApi.error("명찰 저장에 실패했어요. 기존 설정을 유지했어요");}push();p.sendMessage("명찰 "+action+" 완료: "+n.name+" ["+n.key+"]");return 1;
 }
}
