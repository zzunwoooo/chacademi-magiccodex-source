package school.magiccodex.paper;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.PetProtocol;
import school.magiccodex.protocol.PetProtocol.*;

/** Optional MCPets 4.1.11 integration. No commands dispatched and no per-tick pet scanning. */
final class PetBridge implements PluginMessageListener,Listener,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final Map<UUID,Long> requests=new HashMap<>(),actions=new HashMap<>();
    private final Path settings;
    private YamlConfiguration config;
    private Hook hook;private long retryAt,catalogAt;
    private List<Descriptor> catalog=List.of();private List<?> objects=List.of();
    private record Descriptor(String id,String name,String permission,boolean checked,byte[] icon){}
    PetBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;settings=plugin.getDataFolder().toPath().resolve("pets.yml");
        if(!Files.exists(settings))Files.writeString(settings,"# MCPets ID -> 1~3성. 등록하지 않은 펫은 default-stars 사용\ndefault-stars: 1\nstars: {}\n");
        reload();
        var m=plugin.getServer().getMessenger();m.registerIncomingPluginChannel(plugin,PetProtocol.REQUEST,this);m.registerOutgoingPluginChannel(plugin,PetProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,plugin);registerCommands();
    }
    private void reload(){try{config=CreatureConfigFiles.load(plugin.getDataFolder(),"pets","stars");catalogAt=0;}catch(Exception e){throw new IllegalArgumentException("펫 등급 설정을 읽지 못했습니다",e);}}
    private static long now(){return System.nanoTime()/1_000_000;}
    boolean capturable(String id){try{capturePermission(id);return true;}catch(Exception|LinkageError ignored){return false;}}
    String capturePermission(String petId)throws Exception{
        if(!connect())throw new IllegalStateException("MCPets 연결이 필요합니다.");
        Object pet=hook.object.invoke(null,petId);
        if(pet==null)throw new IllegalStateException("MCPets에 펫을 등록해 주세요: "+petId);
        String permission=(String)hook.permission.invoke(pet);
        if(!(boolean)hook.checked.invoke(pet)||permission==null||!permission.matches("[a-z0-9_.-]{1,100}"))throw new IllegalStateException("펫의 CheckPermission과 Permission 설정을 확인해 주세요.");
        return permission;
    }
    boolean activePet(org.bukkit.entity.Entity entity){
        if(!connect())return false;
        try{return hook.activeEntity.invoke(null,entity)!=null;}catch(Exception e){return true;}
    }
    /** True when `entity` is one of `owner`'s summoned MCPets pets (target HUD skips your own pet). */
    boolean ownedBy(org.bukkit.entity.Entity entity,UUID owner){
        if(owner==null||!connect())return false;
        try{
            Object pet=hook.activeEntity.invoke(null,entity);if(pet==null)return false;
            for(Object mine:hook.active(owner))if(mine==pet||mine.equals(pet))return true;
            return false;
        }catch(Exception|LinkageError e){return false;}
    }
    private boolean connect(){
        var p=Bukkit.getPluginManager().getPlugin("MCPets");
        if(p==null||!p.isEnabled()){hook=null;return false;}
        if(hook!=null&&hook.loader==p.getClass().getClassLoader())return true;
        if(now()<retryAt)return false;retryAt=now()+15000;
        try{hook=new Hook(p.getClass().getClassLoader());catalogAt=0;plugin.getLogger().info("펫 도감: MCPets "+p.getPluginMeta().getVersion()+" API 연결");return true;}
        catch(Exception|LinkageError e){plugin.getLogger().warning("MCPets API 연결 실패: "+e.getMessage());hook=null;return false;}
    }
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] data){
        if(!PetProtocol.REQUEST.equals(channel)||!player.getListeningPluginChannels().contains(PetProtocol.RESPONSE))return;
        Request r;try{r=PetProtocol.request(data);}catch(IllegalArgumentException e){return;}
        long time=now();UUID id=player.getUniqueId();
        long gap=r.action()==PetProtocol.LIST?1200:700;
        var limiter=r.action()==PetProtocol.LIST?requests:actions;
        if(time-limiter.getOrDefault(id,Long.MIN_VALUE/2)<gap)return;limiter.put(id,time);
        if(!player.hasPermission("magiccodex.pets")){send(player,r.sequence(),"펫 도감을 사용할 권한이 없습니다.",List.of());return;}
        if(!connect()){send(player,r.sequence(),"서버의 MCPets 연결을 기다리고 있습니다.",List.of());return;}
        try{
            String notice="";
            if(r.action()!=PetProtocol.LIST){
                Object pet=hook.object.invoke(null,r.id());
                if(pet==null){send(player,r.sequence(),"등록되지 않은 펫입니다.",snapshot(player));return;}
                boolean checked=(boolean)hook.checked.invoke(pet);String permission=(String)hook.permission.invoke(pet);
                if(r.action()==PetProtocol.SUMMON){
                    if(checked&&(permission==null||!player.hasPermission(permission))){send(player,r.sequence(),"아직 소환할 수 없는 펫입니다.",snapshot(player));return;}
                    // Spawn on behalf of this authenticated player only. MCPets applies region/limit/event checks.
                    int result=(int)hook.spawn.invoke(pet,player,player.getLocation());
                    notice=result==0||result==1?"펫을 소환했습니다.":"지금은 소환할 수 없습니다.";if(result!=0&&result!=1)plugin.getLogger().info("MCPets 소환 거부 "+player.getName()+" "+r.id()+" 코드 "+result);
                }else{
                    Object active=hook.active(player.getUniqueId()).stream().filter(p->hook.idOf(p).equals(r.id())).findFirst().orElse(null);
                    if(active==null)notice="소환 중인 펫이 아닙니다.";
                    else notice=(boolean)hook.despawn.invoke(active,hook.revoke)?"소환을 해제했습니다.":"소환 해제를 확인해 주세요.";
                }
            }
            send(player,r.sequence(),notice,snapshot(player));
        }catch(Exception|LinkageError e){plugin.getLogger().warning("펫 요청 실패: "+e.getMessage());send(player,r.sequence(),"펫 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",List.of());}
    }
    private List<Entry> snapshot(Player player)throws Exception{
        List<?> current=(List<?>)hook.list.invoke(null);
        if(now()>=catalogAt||current.size()!=objects.size()||!current.equals(objects)){
            var list=new ArrayList<Descriptor>();var seen=new HashSet<String>();
            for(Object pet:current){
                String id=hook.idOf(pet);if(!PetProtocol.validId(id)||!seen.add(id))continue;
                if(list.size()>=PetProtocol.MAX_PETS)break;
                var icon=(org.bukkit.inventory.ItemStack)hook.icon.invoke(pet);String name=id;byte[] raw=new byte[0];
                if(icon!=null&&!icon.getType().isAir()){
                    var meta=icon.getItemMeta();
                    if(meta!=null&&meta.hasDisplayName())name=ChatColor.stripColor(meta.getDisplayName());
                    raw=icon.serializeAsBytes();if(raw.length>PetProtocol.MAX_ICON)raw=new byte[0];
                }
                if(name==null||name.isBlank())name=id;if(name.length()>96)name=name.substring(0,96);
                list.add(new Descriptor(id,name,(String)hook.permission.invoke(pet),(boolean)hook.checked.invoke(pet),raw));
            }
            catalog=List.copyOf(list);objects=List.copyOf(current);catalogAt=now()+30000;
        }
        var active=new HashSet<String>();for(Object pet:hook.active(player.getUniqueId()))active.add(hook.idOf(pet));
        var result=new ArrayList<Entry>();
        var stars=config.getConfigurationSection("stars");int fallback=Math.clamp(config.getInt("default-stars",1),1,3);
        for(var p:catalog){
            Object configured=stars==null?null:stars.getValues(false).get(p.id);
            int grade=configured instanceof Number n?Math.clamp(n.intValue(),1,3):fallback;
            boolean owned=!p.checked||(p.permission!=null&&player.hasPermission(p.permission));
            if(owned)result.add(new Entry(p.id,p.name,grade,true,active.contains(p.id),p.icon));
        }
        return result;
    }
    private void send(Player p,long seq,String msg,List<Entry> list){for(var part:PetProtocol.split(seq,msg,list))p.sendPluginMessage(plugin,PetProtocol.RESPONSE,PetProtocol.encode(part));}
    @EventHandler public void quit(PlayerQuitEvent e){requests.remove(e.getPlayer().getUniqueId());actions.remove(e.getPlayer().getUniqueId());}
    // ------------------------------------------------------------------ 명령 (2026-10-20 명령어 정리)
    /** 펫 ID (MCPets) → 보유 권한 노드. CheckPermission 이 꺼진 펫은 누구나 가진 것으로 보고 빠진다. */
    private Map<String,String> ownable(){
        var out=new TreeMap<String,String>();if(!connect())return out;
        try{for(Object pet:(List<?>)hook.list.invoke(null)){String id=hook.idOf(pet);String perm=(String)hook.permission.invoke(pet);if(PetProtocol.validId(id)&&(boolean)hook.checked.invoke(pet)&&perm!=null&&perm.matches("[a-z0-9_.-]{1,100}"))out.put(id,perm);}}
        catch(Exception|LinkageError e){plugin.getLogger().warning("MCPets 펫 목록 읽기 실패: "+e);}
        return out;
    }
    /** 변신 가면 (ArtifactShapeshift): 이 플레이어가 보유한 펫 (권한 확인한 것만) — ID·이름·MythicMobs 몹 이름(모르면 null). 메인 스레드. */
    record Owned(String id,String name,String mythic){}
    List<Owned> owned(Player player){
        var out=new ArrayList<Owned>();if(!connect())return out;
        try{for(Object pet:(List<?>)hook.list.invoke(null)){
            String id=hook.idOf(pet);String perm=(String)hook.permission.invoke(pet);
            if(!PetProtocol.validId(id)||!(boolean)hook.checked.invoke(pet)||perm==null||!player.hasPermission(perm))continue;
            String mythic=null;if(hook.mythic!=null)try{mythic=(String)hook.mythic.invoke(pet);}catch(Exception ignored){}
            out.add(new Owned(id,petName(id),mythic));
        }}catch(Exception|LinkageError e){plugin.getLogger().warning("MCPets 보유 펫 읽기 실패: "+e);}
        return out;
    }
    private String petName(String id){for(var d:catalog)if(d.id.equals(id))return d.name;return id;}
    private void registerCommands(){
        if(!CodexCommands.ready(plugin,"펫"))return;
        ChacaApi.reload(plugin,"펫","펫 등급(pets.yml)·MCPets 다시 연결",()->{reload();hook=null;retryAt=0;connect();});
        ChacaApi.status(plugin,"MCPets",()->connect()?"정상 · 펫 "+ownable().size()+"종 (보유 권한 있는 펫)":"대기 · MCPets가 없거나 연결 중");
        var node=Commands.literal("펫");
        node.then(Commands.literal("보기").executes(ctx->{
            UUID id=ChacaApi.target(ctx);var sender=CodexCommands.sender(ctx);var perms=ownable();
            if(perms.isEmpty())throw ChacaApi.error("MCPets가 연결되지 않았거나 보유 권한이 있는 펫이 없어요");
            ChacaApi.permissions(id,"").whenComplete((nodes,error)->{
                if(error!=null){ChacaApi.fail(sender,"보유 펫을 읽지 못했어요",error);return;}
                var have=new HashSet<>(nodes);var rows=new ArrayList<String>();
                for(var e:perms.entrySet())if(have.contains(e.getValue()))rows.add(" "+petName(e.getKey())+" ("+e.getKey()+")");
                int owned=rows.size();if(owned==0)rows.add(" 보유한 펫이 없어요");
                rows.add(" 보유 "+owned+" / "+perms.size()+"종");
                CodexCommands.lines(sender,"── "+ChacaApi.name(id)+" 님의 펫 ──",rows);
            });return Command.SINGLE_SUCCESS;
        }));
        for(String action:List.of("주기","회수"))node.then(Commands.literal(action).then(Commands.argument("펫",ChacaApi.word(()->ownable().keySet())).executes(ctx->{
            UUID id=ChacaApi.target(ctx);String pet=ctx.getArgument("펫",String.class);var sender=CodexCommands.sender(ctx);
            String perm=ownable().get(pet);if(perm==null)throw ChacaApi.error(pet+" 펫을 찾을 수 없어요 (MCPets 보유 권한이 있는 펫만)");
            boolean grant=action.equals("주기");
            ChacaApi.permission(id,perm,grant).whenComplete((v,error)->{
                if(error!=null){ChacaApi.fail(sender,"펫 보유를 바꾸지 못했어요",error);return;}
                ChacaApi.ok(sender,ChacaApi.name(id)+" 님에게 "+petName(pet)+(grant?" 펫을 주었어요":" 펫을 회수했어요"));
            });return Command.SINGLE_SUCCESS;
        })));
        ChacaApi.branch(plugin,ChacaApi.유저관리,node,"펫 보유 보기·주기·회수 (MCPets 권한)","펫 보기");
    }
    public void close(){catalog=List.of();objects=List.of();requests.clear();actions.clear();hook=null;}
    private static final class Hook{
        final ClassLoader loader;final Method list,object,active,activeEntity,id,icon,checked,permission,spawn,despawn;final Object revoke;
        /** Pet#getMythicMobName (변신 가면용, 없는 버전이면 null). */
        Method mythic;
        @SuppressWarnings({"unchecked","rawtypes"}) Hook(ClassLoader loader)throws Exception{
            this.loader=loader;var api=Class.forName("fr.nocsy.mcpets.api.MCPetsAPI",true,loader);
            var pet=Class.forName("fr.nocsy.mcpets.data.Pet",true,loader);var reason=Class.forName("fr.nocsy.mcpets.data.PetDespawnReason",true,loader);
            list=api.getMethod("getObjectPets");object=api.getMethod("getObjectPet",String.class);active=api.getMethod("getActivePetsForPlayer",UUID.class);
            activeEntity=pet.getMethod("getFromEntity",org.bukkit.entity.Entity.class);
            id=pet.getMethod("getId");icon=pet.getMethod("getIcon");checked=pet.getMethod("isCheckPermission");permission=pet.getMethod("getPermission");
            spawn=pet.getMethod("spawn",Player.class,Location.class);despawn=pet.getMethod("despawn",reason);revoke=Enum.valueOf((Class)reason,"REVOKE");
            try{mythic=pet.getMethod("getMythicMobName");}catch(NoSuchMethodException ignored){mythic=null;}
        }
        List<?> active(UUID uuid)throws Exception{return (List<?>)active.invoke(null,uuid);}
        String idOf(Object pet){try{return (String)id.invoke(pet);}catch(Exception e){throw new IllegalStateException(e);}}
    }
}
