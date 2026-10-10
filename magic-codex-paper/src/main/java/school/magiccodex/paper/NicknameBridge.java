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

/** Connection identity owns every operation. Session UUID is never supplied by a client.
 * /최초닉네임설정: marks a player as needing a first nickname (kept in first-nickname.yml so a crash or logout
 * re-opens it on the next join), pushes an undismissable screen, and runs first-nickname.on-complete after the save. */
final class NicknameBridge implements Listener,PluginMessageListener,org.bukkit.command.TabExecutor,AutoCloseable {
    private final MagicCodexBridge plugin;private final DisplayNames names;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-nickname-io");t.setDaemon(true);return t;});
    private final Map<UUID,NicknameStore.State> profiles=new ConcurrentHashMap<>();
    private final Map<UUID,NicknameSession> sessions=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    private final List<NicknameExpansion> expansions=new ArrayList<>();
    private final NicknameStore store;private long serial;private volatile boolean closed;
    private final Set<UUID> firstPending=new HashSet<>();private final java.io.File firstFile;
    
    NicknameBridge(MagicCodexBridge plugin,DisplayNames names)throws Exception{
        this.plugin=plugin;this.names=names;
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));store=io.submit(()->new NicknameStore(plugin.getDataFolder().toPath().resolve("friends.db"),settings)).get(10,TimeUnit.SECONDS);profiles.putAll(io.submit(store::all).get(10,TimeUnit.SECONDS));}catch(Exception e){io.shutdownNow();throw e;}
        firstFile=new java.io.File(plugin.getDataFolder(),"first-nickname.yml");loadFirst();
        var firstCommand=plugin.getCommand("최초닉네임설정");if(firstCommand!=null){firstCommand.setExecutor(this);firstCommand.setTabCompleter(this);}
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
        io.execute(()->{try{var state=store.save(id,account,r.nickname(),revision);main(()->{busy.remove(id);if(state!=null)profiles.put(id,state);names.invalidate(id);boolean saved=state!=null&&state.revision()==revision+1&&state.nickname().equals(r.nickname());if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,saved?"닉네임을 저장했습니다.":"닉네임 정보가 바뀌었습니다. 확인 후 다시 저장해 주세요.");if(saved)firstSaved(p);});}catch(Exception e){main(()->{busy.remove(id);if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.NOTICE,"저장하지 못했습니다. 다시 시도해 주세요.");});}});
    }
    @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());}

    // ------------------------------------------------------------------ 최초 닉네임
    private void loadFirst(){
        firstPending.clear();if(!firstFile.isFile())return;
        for(String raw:org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(firstFile).getStringList("pending"))try{firstPending.add(UUID.fromString(raw));}catch(IllegalArgumentException ignored){}
    }
    private void saveFirst(){
        var y=new org.bukkit.configuration.file.YamlConfiguration();y.options().setHeader(List.of("/최초닉네임설정 후 아직 닉네임을 저장하지 않은 플레이어 (접속하면 창이 다시 열림)"));
        y.set("pending",firstPending.stream().map(UUID::toString).sorted().toList());
        try{y.save(firstFile);}catch(java.io.IOException e){plugin.getLogger().warning("first-nickname.yml 저장 실패: "+e.getMessage());}
    }
    boolean firstPending(UUID id){return firstPending.contains(id);}
    /** Mark and open. Safe to call again (re-sends the screen). */
    void startFirst(Player p){if(firstPending.add(p.getUniqueId()))saveFirst();pushFirst(p,0);}
    /** The client registers its channels a moment after join; retry for ~15 s. */
    private void pushFirst(Player p,int attempt){
        if(closed||!p.isOnline()||!firstPending.contains(p.getUniqueId()))return;
        if(p.getListeningPluginChannels().contains(NicknameProtocol.RESPONSE)){push(p,NicknameProtocol.FIRST);return;}
        if(attempt<15)Bukkit.getScheduler().runTaskLater(plugin,()->pushFirst(p,attempt+1),20L);
        else p.sendMessage("§c닉네임 설정 창을 열지 못했습니다. MagicCodex UI 모드가 필요합니다.");
    }
    private void push(Player p,int kind){
        var state=profiles.get(p.getUniqueId());
        var r=new Response(kind,0,0,state==null?0:state.revision(),p.getUniqueId(),p.getName(),names.name(p),"","","");
        if(p.getListeningPluginChannels().contains(NicknameProtocol.RESPONSE))p.sendPluginMessage(plugin,NicknameProtocol.RESPONSE,NicknameProtocol.encode(r));
    }
    private void firstSaved(Player p){
        if(!firstPending.remove(p.getUniqueId()))return;saveFirst();
        if(p.isOnline())push(p,NicknameProtocol.FIRST_DONE);
        var config=plugin.getConfig();
        List<String> commands=config.isSet("first-nickname.on-complete")?config.getStringList("first-nickname.on-complete"):List.of("storydialogue {player} ch1-2");
        String nickname=names.name(p);
        // 한 틱 뒤: 클라가 닉네임 창을 닫은 다음 스토리 대화가 열리도록
        Bukkit.getScheduler().runTaskLater(plugin,()->{for(String raw:commands){String c=raw.replace("{player}",p.getName()).replace("{uuid}",p.getUniqueId().toString()).replace("{nickname}",nickname);if(c.startsWith("/"))c=c.substring(1);if(!c.isBlank())Bukkit.dispatchCommand(Bukkit.getConsoleSender(),c);}},10L);
    }
    @EventHandler public void joinFirst(PlayerJoinEvent e){Player p=e.getPlayer();if(firstPending.contains(p.getUniqueId()))Bukkit.getScheduler().runTaskLater(plugin,()->pushFirst(p,0),40L);}
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        boolean admin=sender.hasPermission("magiccodex.nickname.admin");
        Player target;
        if(args.length>=1){
            if(!admin){sender.sendMessage("§c다른 플레이어에게는 관리자만 쓸 수 있습니다.");return true;}
            target=Bukkit.getPlayerExact(args[0]);
            if(target==null){sender.sendMessage("§c접속 중인 플레이어가 아닙니다: "+args[0]);return true;}
        }else if(sender instanceof Player self){
            // 본인은 아직 닉네임이 없을 때만 (이미 정한 사람이 스토리를 다시 여는 것을 막음)
            if(!admin&&!stored(self.getUniqueId()).isEmpty()&&!firstPending.contains(self.getUniqueId())){sender.sendMessage("§7이미 닉네임을 정했습니다. 바꾸려면 /닉네임설정 을 써 주세요.");return true;}
            target=self;
        }else{sender.sendMessage("사용법: /최초닉네임설정 <플레이어>");return true;}
        startFirst(target);
        if(sender!=target)sender.sendMessage("§7"+target.getName()+" 님에게 최초 닉네임 설정 창을 열었습니다.");
        return true;
    }
    @Override public List<String> onTabComplete(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        if(args.length!=1||!sender.hasPermission("magiccodex.nickname.admin"))return List.of();
        String prefix=args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @EventHandler(ignoreCancelled=true) public void inspect(PlayerInteractEntityEvent e){
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||!e.getPlayer().isSneaking()||!(e.getRightClicked() instanceof Player target)||target.hasMetadata("NPC"))return;
        Player viewer=e.getPlayer();if(viewer==target||!viewer.canSee(target))return;
        var command=plugin.getCommand("스텟창");if(command!=null){e.setCancelled(true);command.execute(viewer,"스텟창",new String[]{target.getUniqueId().toString()});}
    }
    @EventHandler public void disabled(PluginDisableEvent e){if(e.getPlugin()==plugin)close();}
    @Override public void close(){if(closed)return;closed=true;sessions.clear();busy.clear();for(var expansion:expansions)expansion.unregister();io.execute(()->{try{store.close();}catch(Exception ignored){}});io.shutdown();}
}
