package school.magiccodex.paper;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.AscensionProtocol;
import school.magiccodex.protocol.AscensionProtocol.*;

final class AscensionBridge implements Listener,PluginMessageListener,CommandExecutor,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final StatsService stats;
    private final NamespacedKey key;
    private final AscensionGate gate=new AscensionGate();
    private final Map<UUID,Long> next=new HashMap<>();
    private final Map<UUID,Long> nextClaim=new HashMap<>();
    AscensionBridge(MagicCodexBridge plugin,StatsService stats){
        this.plugin=plugin;this.stats=stats;key=new NamespacedKey(plugin,"player_circle");
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,AscensionProtocol.REQUEST,this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,AscensionProtocol.RESPONSE);
        Objects.requireNonNull(plugin.getCommand("클래스승급")).setExecutor(this);plugin.getCommand("클래스승급").setTabCompleter((sender,command,label,args)->java.util.List.of());
    }
    int current(Player p){
        Integer stored=p.getPersistentDataContainer().get(key,PersistentDataType.INTEGER);
        Integer existing=stats.get(p.getUniqueId()).circle();
        return Math.clamp(stored!=null?stored:existing!=null?existing:1,1,9);
    }
    private String permission(int rank){return "magiccodex.ascend."+rank;}
    private boolean allowed(Player p,int rank){return rank<9&&p.hasPermission("magiccodex.ascend")&&p.hasPermission(permission(rank+1));}
    private long now(){return System.nanoTime()/1_000_000;}
    private boolean limit(Player p){long n=now();if(next.getOrDefault(p.getUniqueId(),0L)>n)return false;next.put(p.getUniqueId(),n+500);return true;}
    private void send(Player p,Response r){if(p.getListeningPluginChannels().contains(AscensionProtocol.RESPONSE))p.sendPluginMessage(plugin,AscensionProtocol.RESPONSE,AscensionProtocol.encodeResponse(r));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 /클래스승급을 사용해 주세요.");return true;}
        if(args.length!=0){p.sendMessage("/클래스승급");return true;}
        if(!p.hasPermission("magiccodex.ascend"))return true;
        if(!plugin.playerStateReady(p)){p.sendMessage("캐릭터 정보를 불러오는 중입니다.");return true;}
        if(!p.getListeningPluginChannels().contains(AscensionProtocol.RESPONSE)){p.sendMessage("Magic Codex 0.18.0 이상 모드가 필요합니다.");return true;}
        if(p.isDead()||!limit(p))return true;
        int from=current(p);var offer=gate.open(p.getUniqueId(),from,now());
        send(p,new Response(AscensionProtocol.OFFER,offer.token(),from,Math.min(9,from+1),allowed(p,from),from==9?"이미 최고 클래스에 도달했습니다.":allowed(p,from)?"새로운 클래스로 나아갈 준비가 되었습니다.":"아직 승급 조건을 충족하지 못했습니다."));
        return true;
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(!channel.equals(AscensionProtocol.REQUEST))return;
        Request r;try{r=AscensionProtocol.decodeRequest(bytes);}catch(IllegalArgumentException e){return;}
        int from=current(p);
        if(r.action()==AscensionProtocol.HELLO){if(limit(p))send(p,new Response(AscensionProtocol.SNAPSHOT,0,from,from,false,""));return;}
        long n=now();if(nextClaim.getOrDefault(p.getUniqueId(),0L)>n)return;nextClaim.put(p.getUniqueId(),n+500);
        if(!gate.consume(p.getUniqueId(),r.token(),from,!p.isDead()&&allowed(p,from),now())){
            send(p,new Response(AscensionProtocol.DENIED,r.token(),from,from,false,"승급 요청이 만료됐거나 조건이 변경됐습니다. /클래스승급으로 다시 확인해 주세요."));return;
        }
        int to=from+1;
        // Persist the authoritative rank before notifying the client. UI closing cannot undo/duplicate it.
        p.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,to);
        p.saveData();
        plugin.savePlayerState(p);
        var extra=stats.get(p.getUniqueId());stats.set(p.getUniqueId(),new StatsService.Additional(to,extra.dormitory(),extra.power(),extra.popularity()));
        send(p,new Response(AscensionProtocol.SUCCESS,r.token(),from,to,true,""));
    }
    @EventHandler public void quit(PlayerQuitEvent e){gate.remove(e.getPlayer().getUniqueId());next.remove(e.getPlayer().getUniqueId());nextClaim.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){gate.clear();next.clear();nextClaim.clear();}
}
