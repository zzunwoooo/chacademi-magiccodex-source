package school.magiccodex.discovery;
import java.util.*;
import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.DiscoveryProtocol;
import school.magiccodex.database.DatabaseSettings;

public final class MagicDiscovery extends JavaPlugin implements Listener,CommandExecutor,TabCompleter,PluginMessageListener {
    Definitions definitions;DiscoveryStore store;NativeConditions nativeConditions;TitleRewards rewards;
    final Map<UUID,Session> sessions=new HashMap<>();private DiscoveryService api;
    private record LearnedKey(UUID player,String spell){}
    private final Map<LearnedKey,Long> learnedVersions=new HashMap<>(),adminPending=new HashMap<>();
    private final LuckPermsGrant permissionWrites=new LuckPermsGrant();private final AdminSpellCatalog adminCatalog=new AdminSpellCatalog();private long nextLearnedVersion;
    static final class Session {
        final UUID id;final Map<String,Double> progress=new HashMap<>();final Map<String,DiscoveryStore.Acquisition> acquired=new HashMap<>();final Set<String> pending=new HashSet<>();
        final LearnedSpells learned=new LearnedSpells();
        PermissionAttachment attachment;boolean loaded,dirty,saving,noticeReady;long nextHello;int acks;long ackWindow;
        Session(UUID id){this.id=id;}
    }
    static void main(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Use server thread");}
    @Override public void onEnable(){
        try{
            saveDefaultConfig();if(!new File(getDataFolder(),"discoveries.yml").exists())saveResource("discoveries.yml",false);
            definitions=new Definitions(new File(getDataFolder(),"discoveries.yml"));store=new DiscoveryStore(getDataFolder().toPath().resolve("discoveries.db"),DatabaseSettings.load(getDataFolder().toPath().resolve("database.properties")));
            refreshAdminCatalog();rewards=new TitleRewards(this);nativeConditions=new NativeConditions(this);api=new DiscoveryService(this);
            Bukkit.getServicesManager().register(DiscoveryService.class,api,this,ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getPluginManager().registerEvents(nativeConditions,this);
            Bukkit.getMessenger().registerIncomingPluginChannel(this,DiscoveryProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(this,DiscoveryProtocol.RESPONSE);
            Objects.requireNonNull(getCommand("마법발견관리")).setExecutor(this);getCommand("마법발견관리").setTabCompleter(this);Objects.requireNonNull(getCommand("칭호수령")).setExecutor(this);
            for(var p:Bukkit.getOnlinePlayers())load(p);
            long period=Math.clamp(getConfig().getInt("progress-save-seconds",5),1,60)*20L;
            Bukkit.getScheduler().runTaskTimer(this,()->{for(var s:List.copyOf(sessions.values())){save(s);if(s.loaded)checkMeta(s);}},period,period);
            getLogger().info("마법 획득 조건 "+definitions.spells.size()+"종 준비. 신규 외부 시스템은 DiscoveryService/서버 명령어로 연결하세요.");
        }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"획득 시스템 시작 실패",e);Bukkit.getPluginManager().disablePlugin(this);}
    }
    private void load(Player p){
        var s=new Session(p.getUniqueId());sessions.put(s.id,s);
        store.load(s.id).whenComplete((data,error)->later(()->{
            if(error!=null){failure(error);return;}if(sessions.get(s.id)!=s)return;
            s.progress.putAll(data.progress());for(var a:data.acquired())s.acquired.put(a.spell(),a);s.learned.restore(data.learnedOverrides(),data.retryProgress());s.loaded=true;
            // Environmental states must be republished after reconnect; circle is persistent, temperature/regions are not.
            s.progress.put("state.temperature",-100d);s.progress.put("state.flight_prohibited",0d);
            refreshAdminCatalog();for(var spell:adminCatalog.permissionEntries().values())if(s.learned.learned(spell.id(),s.acquired.containsKey(spell.id()))||s.learned.retrying(spell.id()))syncPermission(s.id,spell.permission(),s.learned.learned(spell.id(),s.acquired.containsKey(spell.id())));
            nativeConditions.join(p);checkMeta(s);if(s.noticeReady)sendPending(p,s);rewards.claim(p,s,false);
        }));
    }
    void later(Runnable task){if(isEnabled())Bukkit.getScheduler().runTask(this,task);}
    void failure(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"획득 데이터 처리 실패 — 지급 완료로 처리하지 않습니다.",error);}
    Session session(Player p){var s=sessions.get(p.getUniqueId());return s!=null&&s.loaded?s:null;}
    boolean eligible(Player p){return p!=null&&p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    boolean needed(Player p,String id){var s=session(p);var spell=definitions.spells.get(id);return s!=null&&spell!=null&&!s.learned.learned(id,s.acquired.containsKey(id))&&!s.pending.contains(id)&&!adminPending.containsKey(new LearnedKey(s.id,id))&&(s.learned.retrying(id)||!p.hasPermission(spell.permission()));}
    Map<String,String> registeredSpells(){main();refreshAdminCatalog();return adminCatalog.names();}
    private void refreshAdminCatalog(){
        Map<?,?> casts=null;var bridge=Bukkit.getPluginManager().getPlugin("MagicCodexBridge");
        if(bridge!=null&&bridge.isEnabled())try{
            Object catalog=bridge.getClass().getMethod("registeredSpellCatalog").invoke(bridge);
            if(catalog instanceof Map<?,?> values)casts=values;
        }catch(NoSuchMethodException ignored){
            // Older bridge versions still support the original natural-discovery catalog.
        }catch(ReflectiveOperationException|RuntimeException error){failure(error);return;}
        adminCatalog.replace(definitions.spells,casts);
    }
    void adminSetLearned(UUID id,String spellId,boolean learned,Consumer<String> completion){
        main();Objects.requireNonNull(completion,"completion");refreshAdminCatalog();var spell=adminCatalog.get(spellId);
        if(id==null||spell==null){completion.accept("등록된 플레이어 UUID와 마법 ID를 확인해 주세요.");return;}
        if(!isEnabled()||store==null){completion.accept("마법 발견 시스템이 준비되지 않았습니다.");return;}
        var key=new LearnedKey(id,spellId);long version=++nextLearnedVersion;learnedVersions.put(key,version);adminPending.put(key,version);
        var current=sessions.get(id);var progress=current!=null&&current.loaded?Map.copyOf(current.progress):Map.<String,Double>of();
        store.setLearned(id,spellId,learned,definitions.spells.containsKey(spellId)?definitions.spells.get(spellId).requirements().keySet():Set.of(),progress).whenComplete((baseline,error)->later(()->{
            if(error!=null){failure(error);recoverAdminFailure(key,version,spell,completion);return;}
            if(!Objects.equals(learnedVersions.get(key),version)){completion.accept("더 최근의 관리자 변경이 적용되어 이 요청은 대체되었습니다.");return;}
            adminPending.remove(key,version);var session=sessions.get(id);if(session!=null){session.learned.set(spellId,learned,baseline);var player=Bukkit.getPlayer(id);if(!learned&&session.loaded&&player!=null)nativeConditions.restart(player,spellId);}
            syncPermission(id,spell.permission(),learned).whenComplete((ok,permissionError)->later(()->{
                if(permissionError!=null){failure(permissionError);completion.accept("습득 상태는 저장했지만 권한 동기화에 실패했습니다. 재접속하거나 다시 시도해 주세요.");return;}
                if(!Objects.equals(learnedVersions.get(key),version)){completion.accept("더 최근의 관리자 변경이 적용되어 이 요청은 대체되었습니다.");return;}
                completion.accept(null);
                var latest=sessions.get(id);if(learned&&latest!=null&&latest.loaded&&Objects.equals(learnedVersions.get(key),version))checkMeta(latest,true);
            }));
        }));
    }
    private void recoverAdminFailure(LearnedKey key,long version,AdminSpellCatalog.Entry spell,Consumer<String> completion){
        if(!Objects.equals(learnedVersions.get(key),version)){completion.accept("습득 상태를 저장하지 못했습니다. 서버 로그를 확인해 주세요.");return;}
        // An earlier acquisition may have committed while this failed admin request was pending.
        store.load(key.player()).whenComplete((data,error)->later(()->{
            if(Objects.equals(learnedVersions.get(key),version)){
                adminPending.remove(key,version);
                if(error==null){var session=sessions.get(key.player());var acquisition=data.acquired().stream().filter(a->a.spell().equals(key.spell())).findFirst();boolean learned=data.learnedOverrides().getOrDefault(key.spell(),acquisition.isPresent());
                    if(session!=null){acquisition.ifPresent(a->session.acquired.putIfAbsent(a.spell(),a));session.learned.set(key.spell(),learned,data.retryProgress().getOrDefault(key.spell(),Map.of()));}syncPermission(key.player(),spell.permission(),learned);
                }else failure(error);
            }
            completion.accept("습득 상태를 저장하지 못했습니다. 서버 로그를 확인한 뒤 다시 시도해 주세요.");
        }));
    }
    public boolean signal(UUID id,String key,double amount){
        main();if(!definitions.index.containsKey(key)||key.startsWith("state.")||!Double.isFinite(amount)||amount<=0||amount>1e9)return false;
        var s=sessions.get(id);var p=Bukkit.getPlayer(id);boolean death=key.startsWith("death.")&&p!=null&&p.isOnline()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);if(s==null||!s.loaded||!eligible(p)&&!death)return false;
        if(definitions.index.get(key).stream().noneMatch(sp->needed(p,sp.id())))return true;
        double cap=definitions.index.get(key).stream().mapToDouble(sp->sp.requirements().get(key)+s.learned.baseline(sp.id(),key)).max().orElse(1e9);
        double before=s.progress.getOrDefault(key,0d),after=Math.max(before,Math.min(cap,before+amount));s.progress.put(key,after);if(after!=before)s.dirty=true;
        for(var spell:definitions.index.get(key))if(s.learned.meets(spell,s.progress)&&spell.prerequisites().stream().allMatch(p::hasPermission)&&ThreadLocalRandom.current().nextDouble()<spell.chance())acquire(p,s,spell);
        return true;
    }
    public boolean state(UUID id,String key,double value){return state(id,key,value,false);}
    boolean state(UUID id,String key,double value,boolean verifiedAction){
        main();if(!Set.of("state.circle","state.temperature","state.flight_prohibited").contains(key)||!Double.isFinite(value)||Math.abs(value)>1e6)return false;
        if(key.equals("state.circle")&&(value<1||value>9||value!=Math.floor(value)))return false;
        var s=sessions.get(id);if(s==null||!s.loaded)return false;Double previous=s.progress.put(key,value);if(key.equals("state.circle")&&!Objects.equals(previous,value))s.dirty=true;var p=Bukkit.getPlayer(id);
        if(p!=null)for(var spell:definitions.index.getOrDefault(key,List.of()))if(s.learned.meets(spell,s.progress,verifiedAction))acquire(p,s,spell);return true;
    }
    public void cast(UUID id,String spell,double spent){
        main();var s=sessions.get(id);var p=Bukkit.getPlayer(id);if(s==null||!s.loaded||!eligible(p)||spell==null||!spell.matches("[a-z0-9_-]{1,64}")||!Double.isFinite(spent)||spent<0)return;
        signal(id,"cast."+spell,1);if(spent>0)signal(id,"mana."+spell,spent);
        if(spell.equals("pressure_press")&&s.progress.getOrDefault("state.circle",0d)>=6)signal(id,"cast.pressure_press.circle6",1);
        if(spell.equals("flight")&&s.progress.getOrDefault("state.flight_prohibited",0d)>0)signal(id,"cast.flight.prohibited",1);
        if(spell.equals("light_leap")&&s.progress.getOrDefault("state.temperature",-100d)>=30)nativeConditions.armHotRotation(p);
        // A fresh verified action permits retrying already-satisfied state/meta conditions, even at circle 9.
        for(var definition:definitions.spells.values())if(s.learned.retrying(definition.id())&&!definition.requirements().isEmpty()&&definition.requirements().keySet().stream().allMatch(key->key.startsWith("state."))&&s.learned.meets(definition,s.progress,true))acquire(p,s,definition);
        checkMeta(s,true);
    }
    private void checkMeta(Session s){checkMeta(s,false);}
    private void checkMeta(Session s,boolean verifiedAction){var p=Bukkit.getPlayer(s.id);if(!eligible(p))return;
        for(var spell:definitions.spells.values())if(spell.requirements().isEmpty()&&!spell.prerequisites().isEmpty()&&s.learned.canCheckMeta(spell,verifiedAction)&&spell.prerequisites().stream().allMatch(p::hasPermission))acquire(p,s,spell);
    }
    private void acquire(Player p,Session s,Definitions.Spell spell){
        if(!needed(p,spell.id())||!spell.prerequisites().stream().allMatch(p::hasPermission))return;s.pending.add(spell.id());var key=new LearnedKey(s.id,spell.id());long version=learnedVersions.getOrDefault(key,0L);
        store.acquire(s.id,spell.id(),Map.copyOf(s.progress),getConfig().getString("title-scope","server-first").equals("player-first")).whenComplete((award,error)->later(()->{
            s.pending.remove(spell.id());if(error!=null){failure(error);return;}if(sessions.get(s.id)!=s)return;
            s.acquired.putIfAbsent(spell.id(),award.acquisition());
            if(learnedVersions.getOrDefault(key,0L)!=version)return;
            s.learned.set(spell.id(),true,Map.of());syncPermission(s.id,spell.permission(),true);
            if(award.created()){if(s.noticeReady)sendPending(p,s);else p.sendMessage("새로운 마법 발견: "+spell.name());rewards.claim(p,s,false);}
            else p.sendMessage("마법을 다시 습득했습니다: "+spell.name());
            checkMeta(s,true);
        }));
    }
    private CompletableFuture<Void> syncPermission(UUID id,String permission,boolean learned){
        var p=Bukkit.getPlayer(id);var s=sessions.get(id);
        if(p!=null&&p.isOnline()&&s!=null){if(s.attachment==null)s.attachment=p.addAttachment(this);s.attachment.setPermission(permission,learned);}
        if(Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))try{
            var result=permissionWrites.set(this,id,permission,learned);result.exceptionally(error->{failure(error);return null;});return result;
        }catch(LinkageError|RuntimeException error){failure(error);return CompletableFuture.failedFuture(error);}
        return CompletableFuture.completedFuture(null);
    }
    private void save(Session s){if(!s.loaded||!s.dirty||s.saving)return;s.dirty=false;s.saving=true;store.save(s.id,Map.copyOf(s.progress)).whenComplete((ok,error)->later(()->{s.saving=false;if(error!=null){s.dirty=true;failure(error);}}));}
    void sendPending(Player p,Session s){if(!p.getListeningPluginChannels().contains(DiscoveryProtocol.RESPONSE))return;
        s.acquired.values().stream().filter(a->!a.notified()&&s.learned.learned(a.spell(),true)).sorted(Comparator.comparingLong(DiscoveryStore.Acquisition::token)).limit(8).forEach(a->{var spell=definitions.spells.get(a.spell());if(spell!=null)p.sendPluginMessage(this,DiscoveryProtocol.RESPONSE,DiscoveryProtocol.encode(new DiscoveryProtocol.Notice(a.token(),spell.id(),spell.name(),spell.icon(),a.first())));});
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] data){
        if(!channel.equals(DiscoveryProtocol.REQUEST))return;long token;try{token=DiscoveryProtocol.request(data);}catch(IllegalArgumentException e){return;}
        var s=sessions.get(p.getUniqueId());if(s==null)return;long now=System.nanoTime()/1_000_000;
        if(token==0){if(now<s.nextHello)return;s.nextHello=now+5000;s.noticeReady=true;if(s.loaded)sendPending(p,s);return;}
        if(now-s.ackWindow>=1000){s.ackWindow=now;s.acks=0;}if(++s.acks>8)return;
        var match=s.acquired.values().stream().filter(a->a.token()==token&&!a.notified()).findFirst().orElse(null);if(match==null)return;
        store.acknowledge(s.id,token).whenComplete((ok,error)->later(()->{if(error!=null){failure(error);return;}var latest=s.acquired.get(match.spell());s.acquired.put(match.spell(),new DiscoveryStore.Acquisition(match.token(),match.spell(),match.first(),latest.reward(),true));}));
    }
    @EventHandler public void join(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent e){later(()->{var p=e.getPlayer();var s=session(p);if(s!=null)rewards.claim(p,s,false);});}
    @EventHandler public void quit(PlayerQuitEvent e){var s=sessions.remove(e.getPlayer().getUniqueId());if(s!=null&&s.loaded)store.save(s.id,Map.copyOf(s.progress)).exceptionally(error->{failure(error);return null;});nativeConditions.quit(e.getPlayer());}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(cmd.getName().equals("칭호수령")){if(sender instanceof Player p){var s=session(p);if(s!=null)rewards.claim(p,s,true);}return true;}
        if(!sender.hasPermission("magicdiscovery.admin"))return true;
        try{
            if(args.length==2&&args[0].equals("칭호")&&sender instanceof Player p){rewards.configure(p,args[1]);return true;}
            if((args.length==3&&args[0].equals("이벤트"))||(args.length==4&&Set.of("이벤트","상태").contains(args[0]))){var p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("접속 중인 플레이어를 찾을 수 없습니다.");return true;}
                double value=args.length==4?Double.parseDouble(args[3]):1;boolean ok=args[0].equals("상태")?state(p.getUniqueId(),args[2],value,true):signal(p.getUniqueId(),args[2],value);sender.sendMessage(ok?"조건 정보 전달 완료":"이벤트 이름/수치 또는 플레이어 로딩 상태를 확인해 주세요.");return true;}
            sender.sendMessage("/마법발견관리 이벤트 <닉네임> <이벤트> [수량]\n/마법발견관리 상태 <닉네임> <상태> <값>\n/마법발견관리 칭호 <마법ID> : 손에 든 아이템을 칭호 보상으로 등록");
        }catch(Exception e){sender.sendMessage("설정 또는 입력을 확인해 주세요: "+e.getMessage());}return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("magicdiscovery.admin")||args.length==0)return List.of();
        Collection<String> choices;
        if(args.length==1)choices=List.of("이벤트","상태","칭호");
        else if(args.length==2&&args[0].equals("칭호"))choices=definitions.spells.keySet();
        else if(args.length==2&&Set.of("이벤트","상태").contains(args[0]))choices=Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        else if(args.length==3&&args[0].equals("상태"))choices=List.of("state.circle","state.temperature","state.flight_prohibited");
        else if(args.length==3&&args[0].equals("이벤트"))choices=definitions.index.keySet().stream().filter(key->!key.startsWith("state.")).toList();
        else if(args.length==4&&args[0].equals("이벤트"))choices=List.of("<이벤트수량>");
        else if(args.length==4&&args[0].equals("상태"))choices=switch(args[2]){case "state.circle"->List.of("<클래스1~9>");case "state.temperature"->List.of("<온도값>");case "state.flight_prohibited"->List.of("<비행금지여부0또는1>");default->List.of("<상태값>");};
        else choices=List.of();
        String part=args[args.length-1];return choices.stream().filter(value->value.startsWith(part)).sorted().toList();
    }
    @Override public void onDisable(){
        if(nativeConditions!=null)nativeConditions.close();Bukkit.getScheduler().cancelTasks(this);Bukkit.getServicesManager().unregisterAll(this);
        for(var s:sessions.values()){if(store!=null&&s.loaded)store.save(s.id,Map.copyOf(s.progress)).exceptionally(error->{failure(error);return null;});if(s.attachment!=null){var p=Bukkit.getPlayer(s.id);if(p!=null)p.removeAttachment(s.attachment);}}
        sessions.clear();if(store!=null)try{store.close();}catch(Exception e){failure(e);}
    }
}
