package school.magiccodex.paper;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.TemperatureProtocol;

final class TemperatureBridge implements Listener,PluginMessageListener,CommandExecutor,TabCompleter,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final TemperatureFeed feed;
    private final TemperatureService service;
    TemperatureService service(){return service;}
    private static long now(){return System.nanoTime()/1_000_000;}
    TemperatureBridge(MagicCodexBridge plugin){
        this.plugin=plugin;float fallback=18;
        try{fallback=readDefault();}catch(IllegalArgumentException e){plugin.getLogger().warning("기본 온도 설정 오류: 18°C 사용. "+e.getMessage());}
        feed=new TemperatureFeed(fallback);service=new TemperatureService(feed);
        var messenger=plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin,TemperatureProtocol.REQUEST,this);
        messenger.registerOutgoingPluginChannel(plugin,TemperatureProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getServicesManager().register(TemperatureService.class,service,plugin,ServicePriority.Normal);
        Objects.requireNonNull(plugin.getCommand("codextemp")).setExecutor(this);
        plugin.getCommand("codextemp").setTabCompleter(this);
        // Cached reads only: no biome/world scan. Coalesce future frequent API changes to two sends/second.
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    private float readDefault(){
        float value=(float)plugin.getConfig().getDouble("temperature.default-celsius",18);
        TemperatureProtocol.validate(value);return value;
    }
    public void onPluginMessageReceived(String channel,Player player,byte[] data){
        if(!TemperatureProtocol.REQUEST.equals(channel)||!TemperatureProtocol.validRequest(data))return;
        if(!player.getListeningPluginChannels().contains(TemperatureProtocol.RESPONSE))return;
        long time=now();
        if(feed.subscribe(player.getUniqueId(),time))send(player,time);
    }
    private void send(Player player,long time){
        var snapshot=feed.poll(player.getUniqueId(),time);
        if(snapshot!=null)player.sendPluginMessage(plugin,TemperatureProtocol.RESPONSE,TemperatureProtocol.encode(snapshot));
    }
    private void tick(){
        long time=now();
        for(var id:feed.subscribers()){
            var p=Bukkit.getPlayer(id);
            if(p==null||!p.isOnline()||!p.getListeningPluginChannels().contains(TemperatureProtocol.RESPONSE)){feed.remove(id);continue;}
            send(p,time);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){feed.remove(e.getPlayer().getUniqueId());}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("magiccodex.temperature.admin")){sender.sendMessage("권한이 없습니다.");return true;}
        try{
            if(args.length==1&&args[0].equalsIgnoreCase("reload")){
                plugin.reloadConfig();feed.fallback(readDefault());sender.sendMessage("기본 온도를 다시 불러왔습니다. 플레이어별 지정값은 유지됩니다.");return true;
            }
            if(args.length<2||args.length>3)throw new IllegalArgumentException("/codextemp set <플레이어> <온도> · get|reset <플레이어> · reload");
            var p=Bukkit.getPlayerExact(args[1]);if(p==null)throw new IllegalArgumentException("접속 중인 플레이어를 지정하세요.");
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "set"->{if(args.length!=3)throw new IllegalArgumentException("온도를 입력하세요.");service.setTemperature(p,Float.parseFloat(args[2]));}
                case "reset"->{if(args.length!=2)throw new IllegalArgumentException("reset에는 플레이어 이름만 입력하세요.");service.resetTemperature(p);}
                case "get"->{if(args.length!=2)throw new IllegalArgumentException("get에는 플레이어 이름만 입력하세요.");}
                default->throw new IllegalArgumentException("사용할 수 있는 하위 명령: set, get, reset, reload");
            }
            sender.sendMessage(p.getName()+"의 온도: "+service.getTemperature(p.getUniqueId())+"°C");
        }catch(IllegalArgumentException e){sender.sendMessage("온도 설정 오류: "+e.getMessage());}
        return true;
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("magiccodex.temperature.admin")||args.length==0)return List.of();
        var choices=args.length==1?List.of("set","get","reset","reload"):
                args.length==2&&!args[0].equalsIgnoreCase("reload")?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():List.<String>of();
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    public void close(){Bukkit.getServicesManager().unregister(TemperatureService.class,service);feed.clear();}
}
