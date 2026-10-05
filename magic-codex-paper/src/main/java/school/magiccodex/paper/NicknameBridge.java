package school.magiccodex.paper;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.NicknameProtocol;
import school.magiccodex.protocol.NicknameProtocol.*;

/** Connection identity owns every operation. Session UUID is never supplied by a client. */
final class NicknameBridge implements Listener,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;private final DisplayNames names;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-nickname-io");t.setDaemon(true);return t;});
    private final Map<UUID,NicknameStore.State> profiles=new ConcurrentHashMap<>();
    private final Map<UUID,NicknameSession> sessions=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    private final List<NicknameExpansion> expansions=new ArrayList<>();
    private final NicknameStore store;private long serial;private volatile boolean closed;
    
    NicknameBridge(MagicCodexBridge plugin,DisplayNames names)throws Exception{
        this.plugin=plugin;this.names=names;
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));store=io.submit(()->new NicknameStore(plugin.getDataFolder().toPath().resolve("friends.db"),settings)).get(10,TimeUnit.SECONDS);profiles.putAll(io.submit(store::all).get(10,TimeUnit.SECONDS));}catch(Exception e){io.shutdownNow();throw e;}
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,NicknameProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,NicknameProtocol.RESPONSE);Bukkit.getPluginManager().registerEvents(this,plugin);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){var manager=me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();for(String id:List.of("magiccodex","user")){if(manager.getExpansion(id)!=null)continue;var expansion=new NicknameExpansion(this,id,plugin.getDescription().getVersion());if(expansion.register())expansions.add(expansion);}}
    }
    String stored(UUID id){var state=profiles.get(id);return state==null?"":state.nickname();}
    String placeholder(OfflinePlayer p){String value=stored(p.getUniqueId());return value.isEmpty()?(p.getName()==null?"":p.getName()):value;}
    private void main(Runnable task){if(!closed)Bukkit.getScheduler().runTask(plugin,()->{if(!closed)task.run();});}
    private boolean current(Player p,NicknameSession session){return !closed&&p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p&&sessions.get(p.getUniqueId())==session;}
    private String title(Player p,String side){if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return "";String value=me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p,"%chacademiatitle_"+side+"%");if(value.contains("%"))return "";value=org.bukkit.ChatColor.stripColor(value).replaceAll("[\\p{Cntrl}\\p{Cf}]","");return value.length()>64?value.substring(0,64):value;}
    private void reply(Player p,NicknameSession s,long seq,int kind,String message){var state=profiles.get(p.getUniqueId());var r=new Response(kind,seq,s.token(),state==null?0:state.revision(),p.getUniqueId(),p.getName(),names.name(p),title(p,"prefix"),title(p,"suffix"),message);if(p.getListeningPluginChannels().contains(NicknameProtocol.RESPONSE))p.sendPluginMessage(plugin,NicknameProtocol.RESPONSE,NicknameProtocol.encode(r));}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(!channel.equals(NicknameProtocol.REQUEST)||closed)return;Request r;try{r=NicknameProtocol.request(bytes);}catch(IllegalArgumentException e){return;}UUID id=p.getUniqueId();
        if(r.action()==NicknameProtocol.OPEN){
            if(r.session()!=0||busy.contains(id))return;var old=sessions.get(id);if(old!=null&&old.connection()==p&&r.sequence()<=old.sequence())return;
            var s=new NicknameSession(id,p,++serial,r.sequence());sessions.put(id,s);busy.add(id);
            io.execute(()->{try{var state=store.load(id);main(()->{busy.remove(id);if(!current(p,s))return;if(state==null)profiles.remove(id);else profiles.put(id,state);names.invalidate(id);reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,"");});}catch(Exception e){main(()->{busy.remove(id);if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.NOTICE,"닉네임을 불러오지 못했습니다. 다시 시도해 주세요.");});}});return;
        }
        var old=sessions.get(id);if(old==null||!old.accepts(id,p,r))return;
        if(r.action()==NicknameProtocol.CLOSE){sessions.remove(id);return;}if(busy.contains(id))return;
        var s=new NicknameSession(id,p,old.token(),r.sequence());sessions.put(id,s);
        try{NicknameProtocol.validate(r.nickname());}catch(IllegalArgumentException e){reply(p,s,r.sequence(),NicknameProtocol.NOTICE,e.getMessage());return;}
        var before=profiles.get(id);long revision=before==null?0:before.revision();if(revision!=r.revision()){reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,"닉네임 정보가 바뀌었습니다. 확인 후 다시 저장해 주세요.");return;}
        busy.add(id);String account=p.getName();
        io.execute(()->{try{var state=store.save(id,account,r.nickname(),revision);main(()->{busy.remove(id);if(state!=null)profiles.put(id,state);names.invalidate(id);if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,state!=null&&state.revision()==revision+1&&state.nickname().equals(r.nickname())?"닉네임을 저장했습니다.":"닉네임 정보가 바뀌었습니다. 확인 후 다시 저장해 주세요.");});}catch(Exception e){main(()->{busy.remove(id);if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.NOTICE,"저장하지 못했습니다. 다시 시도해 주세요.");});}});
    }
    @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());}
    @EventHandler(ignoreCancelled=true) public void inspect(PlayerInteractEntityEvent e){
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||!e.getPlayer().isSneaking()||!(e.getRightClicked() instanceof Player target)||target.hasMetadata("NPC"))return;
        Player viewer=e.getPlayer();if(viewer==target||!viewer.canSee(target))return;
        var command=plugin.getCommand("스텟창");if(command!=null){e.setCancelled(true);command.execute(viewer,"스텟창",new String[]{target.getUniqueId().toString()});}
    }
    @EventHandler public void disabled(PluginDisableEvent e){if(e.getPlugin()==plugin)close();}
    @Override public void close(){if(closed)return;closed=true;sessions.clear();busy.clear();for(var expansion:expansions)expansion.unregister();io.execute(()->{try{store.close();}catch(Exception ignored){}});io.shutdown();}
}
