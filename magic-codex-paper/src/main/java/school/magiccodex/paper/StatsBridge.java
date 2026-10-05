package school.magiccodex.paper;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.StatsProtocol;
import school.magiccodex.protocol.StatsProtocol.*;

final class StatsBridge implements PluginMessageListener,Listener,CommandExecutor,TabCompleter,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final ManaBridge mana;
    private final StatsService service=new StatsService();
    StatsService service(){return service;}
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Long> nextOpen=new HashMap<>();
    private final Map<UUID,Learned> learned=new HashMap<>();
    private long serial;
    private record Learned(long until,int count,List<String> catalog){}
    private static class Session {final long token;final UUID target;long expires,nextRefresh,nextAction;Session(long token,UUID target,long now){this.token=token;this.target=target;expires=now+20000;nextRefresh=now+3000;}}
    private static long now(){return System.nanoTime()/1_000_000;}
    StatsBridge(MagicCodexBridge plugin,ManaBridge mana){
        this.plugin=plugin;this.mana=mana;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,StatsProtocol.REQUEST,this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,StatsProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getServicesManager().register(StatsService.class,service,plugin,ServicePriority.Normal);
        Objects.requireNonNull(plugin.getCommand("스텟창")).setExecutor(this);plugin.getCommand("스텟창").setTabCompleter(this);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{long n=now();sessions.values().removeIf(s->s.expires<n);learned.values().removeIf(s->s.until()<n);nextOpen.values().removeIf(t->t<n);},200,200);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!(sender instanceof Player viewer)){sender.sendMessage("게임 안에서 사용해 주세요.");return true;}
        if(!viewer.hasPermission("magiccodex.stats.view")){viewer.sendMessage("스테이터스를 열 권한이 없습니다.");return true;}
        if(args.length>1){viewer.sendMessage("/스텟창 [유저 닉네임]");return true;}
        long n=now();if(nextOpen.getOrDefault(viewer.getUniqueId(),0L)>n)return true;nextOpen.put(viewer.getUniqueId(),n+1000);
        if(!viewer.getListeningPluginChannels().contains(StatsProtocol.RESPONSE)){viewer.sendMessage("최신 Magic Codex 모드가 필요합니다.");return true;}
        Player target=args.length==0?viewer:plugin.names().resolve(args[0]);
        if(!visible(viewer,target)){viewer.sendMessage("접속 중인 플레이어를 찾을 수 없습니다.");return true;}
        var session=new Session(++serial,target.getUniqueId(),n);sessions.put(viewer.getUniqueId(),session);
        snapshot(viewer,target,session,StatsProtocol.OPEN);return true;
    }
    private boolean visible(Player viewer,Player target){return target!=null&&target.isOnline()&&viewer.canSee(target);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player p)||args.length!=1||!p.hasPermission("magiccodex.stats.view"))return List.of();
        return Bukkit.getOnlinePlayers().stream().filter(t->visible(p,t)).map(t->plugin.names().name(t)).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
    }
    @Override public void onPluginMessageReceived(String channel,Player viewer,byte[] bytes){
        if(!channel.equals(StatsProtocol.REQUEST))return;
        Request r;try{r=StatsProtocol.decodeRequest(bytes);}catch(IllegalArgumentException e){return;}
        var s=sessions.get(viewer.getUniqueId());long n=now();
        if(s==null||s.expires<n||s.token!=r.session()||!s.target.equals(r.target()))return;
        if(r.action()==StatsProtocol.CLOSE){sessions.remove(viewer.getUniqueId());return;}
        if(r.action()==StatsProtocol.REFRESH){if(n<s.nextRefresh)return;s.nextRefresh=n+3000;}
        else {if(n<s.nextAction)return;s.nextAction=n+1000;}
        Player target=Bukkit.getPlayer(s.target);
        if(!viewer.hasPermission("magiccodex.stats.view")||!visible(viewer,target)){
            send(viewer,new Response(StatsProtocol.GONE,s.token,s.target,"상대방 정보를 더 이상 볼 수 없습니다.",null));sessions.remove(viewer.getUniqueId());return;
        }
        if(r.action()==StatsProtocol.REFRESH){s.expires=n+20000;snapshot(viewer,target,s,StatsProtocol.SNAPSHOT);return;}
        String message;
        if(viewer.getUniqueId().equals(target.getUniqueId()))message="자신에게는 사용할 수 없습니다.";
        else {
            var event=new StatsSocialRequestEvent(viewer,target,r.action()==StatsProtocol.FRIEND?StatsSocialRequestEvent.Action.FRIEND:StatsSocialRequestEvent.Action.POPULARITY);
            Bukkit.getPluginManager().callEvent(event);
            message=event.getResponse()==null?"요청은 전달됐지만 해당 기능은 아직 준비 중입니다.":event.getResponse();
        }
        send(viewer,new Response(StatsProtocol.NOTICE,s.token,s.target,message,null));
    }
    private void snapshot(Player viewer,Player target,Session s,int kind){
        var extra=service.get(target.getUniqueId());var m=mana.mana.snapshot(target.getUniqueId()).orElse(null);
        var catalog=mana.discoveryPermissions();var known=learned.get(target.getUniqueId());long n=now();
        if(known==null||known.until()<n||!known.catalog().equals(catalog)){int count=0;for(String p:catalog)if(target.hasPermission(p))count++;known=new Learned(n+10000,count,catalog);learned.put(target.getUniqueId(),known);}
        String texture="",signature="";
        for(var property:target.getPlayerProfile().getProperties())if(property.getName().equals("textures")){texture=property.getValue();signature=property.getSignature()==null?"":property.getSignature();break;}
        if(texture.length()>4096||signature.length()>2048){texture="";signature="";}
        var eq=target.getEquipment();var items=new ArrayList<byte[]>();
        for(var item:new org.bukkit.inventory.ItemStack[]{eq.getItemInMainHand(),eq.getItemInOffHand(),eq.getBoots(),eq.getLeggings(),eq.getChestplate(),eq.getHelmet()}){
            byte[] b=item==null||item.getType().isAir()?new byte[0]:item.serializeAsBytes();
            if(b.length>3072)b=new org.bukkit.inventory.ItemStack(item.getType()).serializeAsBytes();items.add(b.length<=3072?b:new byte[0]);
        }
        var hp=target.getAttribute(Attribute.MAX_HEALTH);var armor=target.getAttribute(Attribute.ARMOR);
        var profile=new Profile(target.getUniqueId(),plugin.names().name(target),plugin.circle(target),plugin.dorm(target),plugin.equipmentPower(target.getUniqueId(),extra.power()),extra.popularity(),
            hp==null?20:hp.getValue(),armor==null?0:armor.getValue(),m==null?null:m.current(),m==null?null:m.maximum(),m==null?null:m.regeneration(),known.count(),catalog.size(),texture,signature,items,m==null?null:m.haste());
        send(viewer,new Response(kind,s.token,s.target,"",profile));
    }
    private void send(Player p,Response r){if(p.getListeningPluginChannels().contains(StatsProtocol.RESPONSE))p.sendPluginMessage(plugin,StatsProtocol.RESPONSE,StatsProtocol.encodeResponse(r));}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();sessions.remove(id);nextOpen.remove(id);learned.remove(id);service.remove(id);
        var it=sessions.entrySet().iterator();while(it.hasNext()){var entry=it.next();if(entry.getValue().target.equals(id)){var p=Bukkit.getPlayer(entry.getKey());if(p!=null)send(p,new Response(StatsProtocol.GONE,entry.getValue().token,id,"상대방이 접속을 종료했습니다.",null));it.remove();}}
    }
    @Override public void close(){sessions.clear();nextOpen.clear();learned.clear();service.clear();Bukkit.getServicesManager().unregister(service);}
}
