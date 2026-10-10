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
 * re-opens it on the next join), pushes an undismissable screen, and runs first-nickname.on-complete after the save.
 * 두 서버 공유: 닉네임 행은 접속 시 + 45초마다 DB에서 다시 읽는다. 최초 설정 완료 여부의 기준은 DB의 first_done이며
 * (first-nickname.yml은 이 서버의 "창을 다시 열 대상" 목록일 뿐), on-complete는 first_done을 0→1로 바꾼 서버에서 한 번만 실행된다.
 * 메인 스레드는 DB·파일 작업을 기다리지 않는다: 대기 목록 파일은 메모리 상태를 먼저 바꾸고 IO 스레드가 뒤따라 기록한다(실패 시 재시도). */
final class NicknameBridge implements Listener,PluginMessageListener,org.bukkit.command.TabExecutor,AutoCloseable {
    private final MagicCodexBridge plugin;private final DisplayNames names;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-nickname-io");t.setDaemon(true);return t;});
    private final Map<UUID,NicknameStore.State> profiles=new ConcurrentHashMap<>();
    private final Map<UUID,NicknameSession> sessions=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    private final List<NicknameExpansion> expansions=new ArrayList<>();
    private final NicknameStore store;private long serial;private volatile boolean closed;
    private final FirstNicknameState firstState;private final java.io.File firstFile;
    private static final long REFRESH_TICKS=900L; // 45초: 다른 서버에서 바뀐 닉네임 반영 주기
    private static final String DUPLICATE="이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.";
    private final Set<UUID> firstBusy=new HashSet<>();
    private volatile Set<UUID> firstLatest=Set.of();
    private boolean refreshing,refreshFailed;

    NicknameBridge(MagicCodexBridge plugin,DisplayNames names)throws Exception{
        this.plugin=plugin;this.names=names;
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));store=io.submit(()->new NicknameStore(plugin.getDataFolder().toPath().resolve("friends.db"),settings)).get(10,TimeUnit.SECONDS);profiles.putAll(io.submit(store::all).get(10,TimeUnit.SECONDS));}catch(Exception e){io.shutdownNow();throw e;}
        for(String warning:store.warnings())plugin.getLogger().warning("[닉네임] "+warning);
        firstFile=new java.io.File(plugin.getDataFolder(),"first-nickname.yml");
        Set<UUID> initial;try{initial=io.submit(this::loadFirst).get(10,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
        // 시작 시 한 번만 기다린다. 이후 기록은 비동기(queueFirst)라 메인 스레드를 막지 않는다.
        firstLatest=Set.copyOf(initial);firstState=new FirstNicknameState(initial,this::queueFirst);
        Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,REFRESH_TICKS,REFRESH_TICKS);
        var firstCommand=plugin.getCommand("최초닉네임설정");if(firstCommand!=null){firstCommand.setExecutor(this);firstCommand.setTabCompleter(this);}
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,NicknameProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,NicknameProtocol.RESPONSE);Bukkit.getPluginManager().registerEvents(this,plugin);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){var manager=me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();for(String id:List.of("magiccodex","user")){if(manager.getExpansion(id)!=null)continue;var expansion=new NicknameExpansion(this,id,plugin.getDescription().getVersion());if(expansion.register())expansions.add(expansion);}}
    }
    String stored(UUID id){var state=profiles.get(id);return state==null?"":state.nickname();}
    String placeholder(OfflinePlayer p){String value=stored(p.getUniqueId());return value.isEmpty()?(p.getName()==null?"":p.getName()):value;}
    /** nickname/account 자리 표시자 값. 그 밖의 키는 null. */
    String answer(OfflinePlayer p,String params){if(p==null)return "";return params.equals("nickname")?placeholder(p):params.equals("account")?(p.getName()==null?"":p.getName()):null;}
    /** "magiccodex" 식별자는 닉네임 확장이 소유하고, 마나 자리 표시자는 여기로 넘겨받은 함수가 답한다. 소유하지 못했으면 false. */
    boolean delegatePlaceholders(java.util.function.BiFunction<OfflinePlayer,String,String> delegate){
        for(var expansion:expansions)if(expansion.getIdentifier().equals("magiccodex")){expansion.delegate(delegate);return true;}
        return false;
    }
    /** DB에서 읽은 행을 캐시에 반영 (메인 스레드). 더 오래된 revision으로는 덮어쓰지 않는다. */
    private void apply(UUID id,NicknameStore.State state){
        var old=profiles.get(id);
        if(state==null){if(old!=null){profiles.remove(id);names.invalidate(id);}return;}
        if(old!=null&&old.revision()>state.revision())return;
        if(!state.equals(old)){profiles.put(id,state);names.invalidate(id);}
    }
    /** 다른 서버에서 바뀐 닉네임을 반영: 쿼리 한 번, IO 스레드. 실패하면 마지막 캐시를 그대로 쓴다. */
    private void refresh(){
        if(closed||refreshing)return;refreshing=true;
        try{io.execute(()->{
            Map<UUID,NicknameStore.State> all=null;String failure=null;
            try{all=store.all();}catch(Exception e){failure=String.valueOf(e.getMessage());}
            final var snapshot=all;final String error=failure;
            main(()->{
                refreshing=false;
                if(snapshot==null){if(!refreshFailed){refreshFailed=true;plugin.getLogger().warning("닉네임 새로고침 실패 (마지막 값 유지, 계속 재시도): "+error);}return;}
                refreshFailed=false;snapshot.forEach((id,state)->{if(!busy.contains(id))apply(id,state);});
            });
        });}catch(RejectedExecutionException e){refreshing=false;}
    }
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
        String key;try{NicknameProtocol.validate(r.nickname());key=NicknameStore.key(r.nickname());}catch(IllegalArgumentException e){reply(p,s,r.sequence(),NicknameProtocol.NOTICE,e.getMessage());return;}
        // 접속 중인 다른 플레이어의 계정 이름과 같은 닉네임은 사칭이 되므로 막는다 (닉네임을 정한 적 있는 계정은 저장소가 DB에서 한 번 더 확인).
        for(Player other:Bukkit.getOnlinePlayers())if(other!=p&&!other.getUniqueId().equals(id)&&other.getName().toLowerCase(Locale.ROOT).equals(key)){reply(p,s,r.sequence(),NicknameProtocol.NOTICE,DUPLICATE);return;}
        var before=profiles.get(id);long revision=before==null?0:before.revision();if(revision!=r.revision()){reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,"닉네임 정보가 바뀌었습니다. 확인 후 다시 저장해 주세요.");return;}
        busy.add(id);String account=p.getName();
        io.execute(()->{try{var result=store.save(id,account,r.nickname(),revision);main(()->{
            busy.remove(id);var state=result.state();if(state!=null)profiles.put(id,state);names.invalidate(id);
            boolean saved=result.outcome()==NicknameStore.Outcome.SAVED;
            if(!current(p,s))return;
            // 중복은 NOTICE: 클라이언트가 입력한 글자를 지우지 않고 안내만 보여 준다.
            if(result.outcome()==NicknameStore.Outcome.DUPLICATE)reply(p,s,r.sequence(),NicknameProtocol.NOTICE,DUPLICATE);
            else reply(p,s,r.sequence(),NicknameProtocol.SNAPSHOT,saved?"닉네임을 저장했습니다.":"닉네임 정보가 바뀌었습니다. 확인 후 다시 저장해 주세요.");
            if(saved)firstSaved(p,s);
        });}catch(Exception e){main(()->{busy.remove(id);if(current(p,s))reply(p,s,r.sequence(),NicknameProtocol.NOTICE,"저장하지 못했습니다. 다시 시도해 주세요.");});}});
    }
    @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());}

    // ------------------------------------------------------------------ 최초 닉네임
    private Set<UUID> loadFirst() throws Exception {
        Set<UUID> pending=new HashSet<>();if(!firstFile.isFile())return pending;
        var y=new org.bukkit.configuration.file.YamlConfiguration();y.load(firstFile);
        for(String raw:y.getStringList("pending"))pending.add(UUID.fromString(raw));
        return pending;
    }
    private void saveFirst(Set<UUID> pending) throws Exception {
        var y=new org.bukkit.configuration.file.YamlConfiguration();
        y.set("pending",pending.stream().map(UUID::toString).sorted().toList());
        var target=firstFile.toPath();java.nio.file.Files.createDirectories(target.getParent());
        var temporary=java.nio.file.Files.createTempFile(target.getParent(),"first-nickname-",".tmp");
        try {
            java.nio.file.Files.writeString(temporary,y.saveToString(),java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {java.nio.file.Files.deleteIfExists(temporary);}
    }
    /** 대기 목록 기록 예약 (메인 스레드에서 호출, 기다리지 않음). IO 스레드는 순서대로 실행하므로 마지막 상태가 파일에 남는다. */
    private void queueFirst(Set<UUID> next){firstLatest=next;writeFirst(0);}
    private void writeFirst(int attempt){
        try{io.execute(()->{
            try{saveFirst(firstLatest);}
            catch(Exception e){
                plugin.getLogger().warning("first-nickname.yml 저장 실패 ("+(attempt+1)+"번째, 메모리 상태는 유지): "+e.getMessage());
                // 5초 뒤 최신 상태로 다시 기록 (최대 5회). 그래도 실패하면 재시작 후 창이 자동으로 다시 열리지 않을 수 있다.
                if(attempt<5&&!closed&&plugin.isEnabled())try{Bukkit.getScheduler().runTaskLater(plugin,()->writeFirst(attempt+1),100L);}catch(RuntimeException ignored){}
            }
        });}catch(RejectedExecutionException ignored){}
    }
    private void clearFirst(UUID id){try{firstState.complete(id,()->true,()->{});}catch(Exception ignored){}}
    boolean firstPending(UUID id){return firstState.pending(id);}
    /** 대기 등록 후 창을 연다; repeated commands do not create multiple pending entries. */
    void startFirst(Player p){
        try{firstState.start(p.getUniqueId());pushFirst(p,0);}
        catch(Exception e){plugin.getLogger().warning("Could not persist first nickname state.");p.sendMessage("§c닉네임 설정 상태를 저장하지 못했습니다. 다시 시도해 주세요.");}
    }
    /** The client registers its channels a moment after join; retry for ~15 s. */
    private void pushFirst(Player p,int attempt){
        if(closed||!p.isOnline()||!firstState.pending(p.getUniqueId()))return;
        if(p.getListeningPluginChannels().contains(NicknameProtocol.RESPONSE)){push(p,NicknameProtocol.FIRST);return;}
        if(attempt<15)Bukkit.getScheduler().runTaskLater(plugin,()->pushFirst(p,attempt+1),20L);
        else p.sendMessage("§c닉네임 설정 창을 열지 못했습니다. MagicCodex UI 모드가 필요합니다.");
    }
    private void push(Player p,int kind){
        var state=profiles.get(p.getUniqueId());
        var r=new Response(kind,0,0,state==null?0:state.revision(),p.getUniqueId(),p.getName(),names.name(p),"","","");
        if(p.getListeningPluginChannels().contains(NicknameProtocol.RESPONSE))p.sendPluginMessage(plugin,NicknameProtocol.RESPONSE,NicknameProtocol.encode(r));
    }
    private void firstSaved(Player p,NicknameSession session){
        if(!firstState.pending(p.getUniqueId()))return;
        // Keep pending through logout or replacement sessions. Never run story for a stale save callback.
        UUID id=p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!current(p,session)||!firstState.pending(id)||!firstBusy.add(id))return;
            // 완료 기록은 DB(first_done)가 기준: 0→1로 바꾼 서버만 스토리 명령을 실행한다. 메인 스레드는 기다리지 않는다.
            try{io.execute(()->{
                boolean transitioned;
                try{transitioned=store.completeFirst(id);}
                catch(Exception e){main(()->{firstBusy.remove(id);plugin.getLogger().warning("Could not complete first nickname state: "+e.getMessage());if(current(p,session))reply(p,session,session.sequence(),NicknameProtocol.NOTICE,"닉네임 설정 완료 상태를 저장하지 못했습니다. 다시 저장해 주세요.");});return;}
                main(()->{
                    firstBusy.remove(id);
                    if(!current(p,session)){
                        // 기록 직후 접속이 끊김: 스토리를 전달하지 못했으므로 되돌려 다음 접속에서 다시 진행한다 (대기 목록은 그대로).
                        if(transitioned)try{io.execute(()->{try{store.reopenFirst(id);}catch(Exception e){plugin.getLogger().warning("Could not reopen first nickname state for "+id+": "+e.getMessage());}});}catch(RejectedExecutionException ignored){}
                        return;
                    }
                    var cached=profiles.get(id);if(cached!=null&&!cached.firstDone())profiles.put(id,new NicknameStore.State(cached.account(),cached.nickname(),cached.revision(),true));
                    try{
                        firstState.complete(id,()->true,()->{
                            push(p,NicknameProtocol.FIRST_DONE);
                            if(!transitioned){plugin.getLogger().info("First nickname already completed elsewhere for "+id+"; on-complete skipped.");return;}
                            var config=plugin.getConfig();
                            List<String> commands=config.isSet("first-nickname.on-complete")?config.getStringList("first-nickname.on-complete"):List.of("storydialogue {player} ch1-2");
                            String nickname=names.name(p);
                            for(String raw:commands){
                                String c=raw.replace("{player}",p.getName()).replace("{uuid}",p.getUniqueId().toString()).replace("{nickname}",nickname);
                                if(c.startsWith("/"))c=c.substring(1);
                                if(!c.isBlank())Bukkit.dispatchCommand(Bukkit.getConsoleSender(),c);
                            }
                        });
                    }catch(Exception e){plugin.getLogger().warning("Could not complete first nickname state.");}
                });
            });}catch(RejectedExecutionException e){firstBusy.remove(id);}
        },10L);
    }
    /** 접속 시 이 플레이어의 행을 DB에서 읽어 캐시에 반영한다 (다른 서버에서 정한 닉네임). 최초 설정 대기 중이면 그 뒤에 창을 연다. */
    @EventHandler public void joinFirst(PlayerJoinEvent e){
        Player p=e.getPlayer();UUID id=p.getUniqueId();
        try{io.execute(()->{
            NicknameStore.State loaded=null;boolean ok=false;
            try{loaded=store.load(id);ok=true;}catch(Exception ignored){}
            final var state=loaded;final boolean success=ok;
            main(()->{
                if(success&&!busy.contains(id))apply(id,state);
                if(!p.isOnline()||!firstState.pending(id))return;
                // 다른 서버에서 이미 최초 설정을 마쳤다면 이 서버의 대기만 지운다 (창·스토리 다시 열지 않음).
                var now=profiles.get(id);if(success&&now!=null&&now.firstDone()){clearFirst(id);return;}
                Bukkit.getScheduler().runTaskLater(plugin,()->pushFirst(p,0),40L);
            });
        });}catch(RejectedExecutionException ignored){}
    }
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        boolean admin=sender.hasPermission("magiccodex.nickname.admin");
        Player target;
        if(args.length>=1){
            if(!admin){sender.sendMessage("§c다른 플레이어에게는 관리자만 쓸 수 있습니다.");return true;}
            target=Bukkit.getPlayerExact(args[0]);
            if(target==null){sender.sendMessage("§c접속 중인 플레이어가 아닙니다: "+args[0]);return true;}
        }else if(sender instanceof Player self)target=self;
        else{sender.sendMessage("사용법: /최초닉네임설정 <플레이어> [강제]");return true;}
        boolean force=admin&&args.length>=2&&(args[1].equals("강제")||args[1].equalsIgnoreCase("force"));
        UUID id=target.getUniqueId();
        // 이미 대기 중이면 창만 다시 연다.
        if(firstState.pending(id)&&!force){pushFirst(target,0);if(sender!=target)sender.sendMessage("§7"+target.getName()+" 님에게 최초 닉네임 설정 창을 다시 열었습니다.");return true;}
        if(!firstBusy.add(id)){sender.sendMessage("§7처리 중입니다. 잠시 후 다시 시도해 주세요.");return true;}
        // 판단 기준은 캐시가 아니라 DB 행 (다른 서버에서 이미 정했을 수 있다). 조회는 IO 스레드에서 한다.
        try{io.execute(()->{
            NicknameStore.State loaded=null;boolean ok=false;
            try{if(force)store.reopenFirst(id);loaded=store.load(id);ok=true;}catch(Exception ignored){}
            final var state=loaded;final boolean success=ok;
            main(()->{
                firstBusy.remove(id);
                if(success&&!busy.contains(id))apply(id,state);
                if(!target.isOnline()){sender.sendMessage("§c플레이어가 접속을 종료했습니다: "+target.getName());return;}
                // DB를 읽지 못하면 마지막 캐시로 판단한다 (스토리 진행이 DB 순간 장애로 멈추지 않도록).
                var known=success?state:profiles.get(id);
                if(!admin){
                    // 본인은 아직 닉네임이 없을 때만 (이미 정한 사람이 스토리를 다시 여는 것을 막음)
                    if(known!=null&&(!known.nickname().isEmpty()||known.firstDone())){sender.sendMessage("§7이미 닉네임을 정했습니다. 바꾸려면 /닉네임설정 을 써 주세요.");return;}
                }else if(!force&&known!=null&&known.firstDone()){
                    sender.sendMessage("§7"+target.getName()+" 님은 이미 최초 닉네임 설정을 마쳤습니다. 다시 진행하려면 /최초닉네임설정 "+target.getName()+" 강제");return;
                }
                startFirst(target);
                if(sender!=target)sender.sendMessage("§7"+target.getName()+" 님에게 최초 닉네임 설정 창을 열었습니다.");
            });
        });}catch(RejectedExecutionException e){firstBusy.remove(id);}
        return true;
    }
    @Override public List<String> onTabComplete(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        if(args.length==2&&sender.hasPermission("magiccodex.nickname.admin"))return "강제".startsWith(args[1])?List.of("강제"):List.of();
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
    @Override public void close(){
        if(closed)return;closed=true;sessions.clear();busy.clear();firstBusy.clear();for(var expansion:expansions)expansion.unregister();
        // 대기 중인 파일 기록을 마친 뒤 연결을 닫는다 (종료 시에만 잠깐 기다림).
        io.execute(()->{try{store.close();}catch(Exception ignored){}});io.shutdown();
        try{io.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
