package school.magiccodex.discovery;
import java.util.*;
import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
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
    static final class Session {
        final UUID id;final Map<String,Double> progress=new HashMap<>();final Map<String,DiscoveryStore.Acquisition> acquired=new HashMap<>();final Set<String> pending=new HashSet<>();
        PermissionAttachment attachment;boolean loaded,dirty,saving,noticeReady;long nextHello;int acks;long ackWindow;
        Session(UUID id){this.id=id;}
    }
    static void main(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Use server thread");}
    @Override public void onEnable(){
        try{
            saveDefaultConfig();if(!new File(getDataFolder(),"discoveries.yml").exists())saveResource("discoveries.yml",false);
            definitions=new Definitions(new File(getDataFolder(),"discoveries.yml"));store=new DiscoveryStore(getDataFolder().toPath().resolve("discoveries.db"),DatabaseSettings.load(getDataFolder().toPath().resolve("database.properties")));
            rewards=new TitleRewards(this);nativeConditions=new NativeConditions(this);api=new DiscoveryService(this);
            Bukkit.getServicesManager().register(DiscoveryService.class,api,this,ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getPluginManager().registerEvents(nativeConditions,this);
            Bukkit.getMessenger().registerIncomingPluginChannel(this,DiscoveryProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(this,DiscoveryProtocol.RESPONSE);
            Objects.requireNonNull(getCommand("magicdiscovery")).setExecutor(this);getCommand("magicdiscovery").setTabCompleter(this);Objects.requireNonNull(getCommand("칭호수령")).setExecutor(this);
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
            s.progress.putAll(data.progress());for(var a:data.acquired())s.acquired.put(a.spell(),a);s.loaded=true;
            // Environmental states must be republished after reconnect; circle is persistent, temperature/regions are not.
            s.progress.put("state.temperature",-100d);s.progress.put("state.flight_prohibited",0d);
            for(var a:data.acquired()){var spell=definitions.spells.get(a.spell());if(spell!=null)grant(p,s,spell.permission());}
            nativeConditions.join(p);checkMeta(s);if(s.noticeReady)sendPending(p,s);rewards.claim(p,s,false);
        }));
    }
    void later(Runnable task){if(isEnabled())Bukkit.getScheduler().runTask(this,task);}
    void failure(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"획득 데이터 처리 실패 — 지급 완료로 처리하지 않습니다.",error);}
    Session session(Player p){var s=sessions.get(p.getUniqueId());return s!=null&&s.loaded?s:null;}
    boolean eligible(Player p){return p!=null&&p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    boolean needed(Player p,String id){var s=session(p);var spell=definitions.spells.get(id);return s!=null&&spell!=null&&!s.acquired.containsKey(id)&&!s.pending.contains(id)&&!p.hasPermission(spell.permission());}
    public boolean signal(UUID id,String key,double amount){
        main();if(!definitions.index.containsKey(key)||key.startsWith("state.")||!Double.isFinite(amount)||amount<=0||amount>1e9)return false;
        var s=sessions.get(id);var p=Bukkit.getPlayer(id);boolean death=key.startsWith("death.")&&p!=null&&p.isOnline()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);if(s==null||!s.loaded||!eligible(p)&&!death)return false;
        if(definitions.index.get(key).stream().noneMatch(sp->needed(p,sp.id())))return true;
        double cap=definitions.index.get(key).stream().flatMap(sp->sp.requirements().entrySet().stream()).filter(e->e.getKey().equals(key)).mapToDouble(Map.Entry::getValue).max().orElse(1e9);
        double before=s.progress.getOrDefault(key,0d),after=Math.min(cap,before+amount);s.progress.put(key,after);if(after!=before)s.dirty=true;
        for(var spell:definitions.index.get(key))if(Definitions.meets(spell,s.progress)&&spell.prerequisites().stream().allMatch(p::hasPermission)&&ThreadLocalRandom.current().nextDouble()<spell.chance())acquire(p,s,spell);
        return true;
    }
    public boolean state(UUID id,String key,double value){
        main();if(!Set.of("state.circle","state.temperature","state.flight_prohibited").contains(key)||!Double.isFinite(value)||Math.abs(value)>1e6)return false;
        if(key.equals("state.circle")&&(value<1||value>9||value!=Math.floor(value)))return false;
        var s=sessions.get(id);if(s==null||!s.loaded)return false;Double previous=s.progress.put(key,value);if(key.equals("state.circle")&&!Objects.equals(previous,value))s.dirty=true;var p=Bukkit.getPlayer(id);
        if(p!=null)for(var spell:definitions.index.getOrDefault(key,List.of()))if(Definitions.meets(spell,s.progress))acquire(p,s,spell);return true;
    }
    public void cast(UUID id,String spell,double spent){
        main();var s=sessions.get(id);var p=Bukkit.getPlayer(id);if(s==null||!s.loaded||!eligible(p)||spell==null||!spell.matches("[a-z0-9_-]{1,64}")||!Double.isFinite(spent)||spent<0)return;
        signal(id,"cast."+spell,1);if(spent>0)signal(id,"mana."+spell,spent);
        if(spell.equals("pressure_press")&&s.progress.getOrDefault("state.circle",0d)>=6)signal(id,"cast.pressure_press.circle6",1);
        if(spell.equals("flight")&&s.progress.getOrDefault("state.flight_prohibited",0d)>0)signal(id,"cast.flight.prohibited",1);
        if(spell.equals("light_leap")&&s.progress.getOrDefault("state.temperature",-100d)>=30)nativeConditions.armHotRotation(p);
    }
    private void checkMeta(Session s){var p=Bukkit.getPlayer(s.id);if(!eligible(p))return;
        for(var spell:definitions.spells.values())if(spell.requirements().isEmpty()&&!spell.prerequisites().isEmpty()&&spell.prerequisites().stream().allMatch(p::hasPermission))acquire(p,s,spell);
    }
    private void acquire(Player p,Session s,Definitions.Spell spell){
        if(!needed(p,spell.id()))return;s.pending.add(spell.id());
        store.acquire(s.id,spell.id(),Map.copyOf(s.progress),getConfig().getString("title-scope","server-first").equals("player-first")).whenComplete((award,error)->later(()->{
            s.pending.remove(spell.id());if(error!=null){failure(error);return;}if(sessions.get(s.id)!=s)return;
            s.acquired.put(spell.id(),award.acquisition());grant(p,s,spell.permission());
            if(award.created()){if(s.noticeReady)sendPending(p,s);else p.sendMessage("새로운 마법 발견: "+spell.name());rewards.claim(p,s,false);checkMeta(s);}
        }));
    }
    private void grant(Player p,Session s,String permission){
        if(!p.isOnline())return;if(s.attachment==null)s.attachment=p.addAttachment(this);s.attachment.setPermission(permission,true);
        if(Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))try{LuckPermsGrant.grant(this,s.id,permission);}catch(LinkageError|RuntimeException e){failure(e);}
    }
    private void save(Session s){if(!s.loaded||!s.dirty||s.saving)return;s.dirty=false;s.saving=true;store.save(s.id,Map.copyOf(s.progress)).whenComplete((ok,error)->later(()->{s.saving=false;if(error!=null){s.dirty=true;failure(error);}}));}
    void sendPending(Player p,Session s){if(!p.getListeningPluginChannels().contains(DiscoveryProtocol.RESPONSE))return;
        s.acquired.values().stream().filter(a->!a.notified()).sorted(Comparator.comparingLong(DiscoveryStore.Acquisition::token)).limit(8).forEach(a->{var spell=definitions.spells.get(a.spell());if(spell!=null)p.sendPluginMessage(this,DiscoveryProtocol.RESPONSE,DiscoveryProtocol.encode(new DiscoveryProtocol.Notice(a.token(),spell.id(),spell.name(),spell.icon(),a.first())));});
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
            if(args.length==2&&args[0].equalsIgnoreCase("title")&&sender instanceof Player p){rewards.configure(p,args[1]);return true;}
            if(args.length>=3&&Set.of("signal","state").contains(args[0])){var p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("접속 중인 플레이어를 찾을 수 없습니다.");return true;}
                double value=args.length==4?Double.parseDouble(args[3]):1;boolean ok=args[0].equals("state")?state(p.getUniqueId(),args[2],value):signal(p.getUniqueId(),args[2],value);sender.sendMessage(ok?"조건 정보 전달 완료":"이벤트 이름/수치 또는 플레이어 로딩 상태를 확인해 주세요.");return true;}
            sender.sendMessage("/magicdiscovery signal <닉네임> <이벤트> [수량]\n/magicdiscovery state <닉네임> <상태> <값>\n/magicdiscovery title <마법ID> : 손에 든 아이템을 칭호 보상으로 등록");
        }catch(Exception e){sender.sendMessage("설정 또는 입력을 확인해 주세요: "+e.getMessage());}return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(!s.hasPermission("magicdiscovery.admin"))return List.of();Collection<String> choices=args.length==1?List.of("signal","state","title"):args.length==2&&args[0].equals("title")?definitions.spells.keySet():args.length==2?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():args.length==3&&args[0].equals("state")?List.of("state.circle","state.temperature","state.flight_prohibited"):args.length==3?definitions.index.keySet():List.of();String part=args[args.length-1];return choices.stream().filter(v->v.startsWith(part)).sorted().toList();}
    @Override public void onDisable(){
        if(nativeConditions!=null)nativeConditions.close();Bukkit.getScheduler().cancelTasks(this);Bukkit.getServicesManager().unregisterAll(this);
        for(var s:sessions.values()){if(store!=null&&s.loaded)store.save(s.id,Map.copyOf(s.progress)).exceptionally(error->{failure(error);return null;});if(s.attachment!=null){var p=Bukkit.getPlayer(s.id);if(p!=null)p.removeAttachment(s.attachment);}}
        sessions.clear();if(store!=null)try{store.close();}catch(Exception e){failure(e);}
    }
}
