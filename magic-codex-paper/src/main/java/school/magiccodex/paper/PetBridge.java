package school.magiccodex.paper;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.PetProtocol;
import school.magiccodex.protocol.PetProtocol.*;

/** Optional MCPets 4.1.11 integration. No commands dispatched and no per-tick pet scanning. */
final class PetBridge implements PluginMessageListener,Listener,CommandExecutor,AutoCloseable {
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
        Bukkit.getPluginManager().registerEvents(this,plugin);Objects.requireNonNull(plugin.getCommand("codexpets")).setExecutor(this);
    }
    private void reload(){try{config=CreatureConfigFiles.load(plugin.getDataFolder(),"pets","stars");catalogAt=0;}catch(Exception e){throw new IllegalArgumentException("펫 등급 설정을 읽지 못했습니다",e);}}
    private static long now(){return System.nanoTime()/1_000_000;}
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
                    notice=result==0||result==1?"펫을 소환했습니다.":"지금은 소환할 수 없습니다. (MCPets "+result+")";
                }else{
                    Object active=hook.active(player.getUniqueId()).stream().filter(p->hook.idOf(p).equals(r.id())).findFirst().orElse(null);
                    if(active==null)notice="소환 중인 펫이 아닙니다.";
                    else notice=(boolean)hook.despawn.invoke(active,hook.revoke)?"소환을 해제했습니다.":"소환 해제를 확인해 주세요.";
                }
            }
            send(player,r.sequence(),notice,snapshot(player));
        }catch(Exception|LinkageError e){plugin.getLogger().warning("펫 요청 실패: "+e.getMessage());send(player,r.sequence(),"펫 정보를 처리하지 못했습니다. 서버 로그를 확인해 주세요.",List.of());}
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
            result.add(new Entry(p.id,p.name,grade,!p.checked||(p.permission!=null&&player.hasPermission(p.permission)),active.contains(p.id),p.icon));
        }
        return result;
    }
    private void send(Player p,long seq,String msg,List<Entry> list){for(var part:PetProtocol.split(seq,msg,list))p.sendPluginMessage(plugin,PetProtocol.RESPONSE,PetProtocol.encode(part));}
    @EventHandler public void quit(PlayerQuitEvent e){requests.remove(e.getPlayer().getUniqueId());actions.remove(e.getPlayer().getUniqueId());}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("magiccodex.pets.admin")){sender.sendMessage("권한이 없습니다.");return true;}
        reload();hook=null;retryAt=0;sender.sendMessage("펫 등급 설정을 다시 불러왔습니다. MCPets 연결: "+(connect()?"정상":"대기"));return true;
    }
    public void close(){catalog=List.of();objects=List.of();requests.clear();actions.clear();hook=null;}
    private static final class Hook{
        final ClassLoader loader;final Method list,object,active,activeEntity,id,icon,checked,permission,spawn,despawn;final Object revoke;
        @SuppressWarnings({"unchecked","rawtypes"}) Hook(ClassLoader loader)throws Exception{
            this.loader=loader;var api=Class.forName("fr.nocsy.mcpets.api.MCPetsAPI",true,loader);
            var pet=Class.forName("fr.nocsy.mcpets.data.Pet",true,loader);var reason=Class.forName("fr.nocsy.mcpets.data.PetDespawnReason",true,loader);
            list=api.getMethod("getObjectPets");object=api.getMethod("getObjectPet",String.class);active=api.getMethod("getActivePetsForPlayer",UUID.class);
            activeEntity=pet.getMethod("getFromEntity",org.bukkit.entity.Entity.class);
            id=pet.getMethod("getId");icon=pet.getMethod("getIcon");checked=pet.getMethod("isCheckPermission");permission=pet.getMethod("getPermission");
            spawn=pet.getMethod("spawn",Player.class,Location.class);despawn=pet.getMethod("despawn",reason);revoke=Enum.valueOf((Class)reason,"REVOKE");
        }
        List<?> active(UUID uuid)throws Exception{return (List<?>)active.invoke(null,uuid);}
        String idOf(Object pet){try{return (String)id.invoke(pet);}catch(Exception e){throw new IllegalStateException(e);}}
    }
}
