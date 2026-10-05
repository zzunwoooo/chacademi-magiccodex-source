package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.TitleProtocol;

final class TitleBridge implements Listener,CommandExecutor,TabCompleter,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;private final TitleStore store;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-title-io");t.setDaemon(true);return t;});
    private volatile Map<String,TitleDefinition> catalog=Map.of();
    private record Cached(TitleStore.State state,String nickname){}
    private record Session(Player player,String token,long until){}
    private record ViewRequest(Player player,boolean open,long sequence){}
    private final Map<UUID,Cached> cache=new ConcurrentHashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();private final Map<UUID,Long> limits=new HashMap<>();
    private final Map<UUID,ViewRequest> deferred=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();private int pending;private volatile boolean closing;private TitleExpansion expansion;
    TitleBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;if(!new File(plugin.getDataFolder(),"titles.yml").exists())plugin.saveResource("titles.yml",false);reload();
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));store=io.submit(()->new TitleStore(settings,plugin.getDataFolder().toPath().resolve("titles.db"))).get(15,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
        Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getMessenger().registerIncomingPluginChannel(plugin,TitleProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,TitleProtocol.RESPONSE);
        for(String name:List.of("칭호","칭호관리")){plugin.getCommand(name).setExecutor(this);plugin.getCommand(name).setTabCompleter(this);}
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new TitleExpansion(this,plugin.getDescription().getVersion());if(!expansion.register())plugin.getLogger().warning("chacademiatitle 플레이스홀더 등록 실패");}
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers()){var old=cache.get(p.getUniqueId());if(old!=null)cache.put(p.getUniqueId(),new Cached(old.state(),plugin.names().name(p)));}long now=System.currentTimeMillis();sessions.values().removeIf(s->s.until()<now);limits.values().removeIf(t->t<now);},40,40);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(!busy.contains(p.getUniqueId()))load(p,false,0,false);},600,600);
        for(Player p:Bukkit.getOnlinePlayers())load(p,false,0,false);
    }
    private Set<String> defaults(){return catalog.values().stream().filter(d->d.enabled()&&d.initial()).map(TitleDefinition::id).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    private void reload()throws Exception{
        var y=new YamlConfiguration();y.load(new File(plugin.getDataFolder(),"titles.yml"));var root=y.getConfigurationSection("titles");if(root==null||root.getKeys(false).size()>TitleProtocol.MAX_TITLES)throw new IllegalArgumentException("칭호 최대 96개");var next=new LinkedHashMap<String,TitleDefinition>();
        for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);if(s==null)throw new IllegalArgumentException(id);int side=switch(s.getString("side","")){case "prefix"->0;case "suffix"->1;default->throw new IllegalArgumentException("칭호 side 확인: "+id);};String hex=s.getString("color",side==0?"#96E4EC":"#DEC58E");if(!hex.matches("#[a-fA-F0-9]{6}"))throw new IllegalArgumentException("칭호 color 확인: "+id);next.put(id,new TitleDefinition(id,side,s.getString("name",id),Integer.parseInt(hex.substring(1),16),s.getBoolean("enabled",true),s.getBoolean("initial",false)));}
        catalog=Collections.unmodifiableMap(next);
    }
    private boolean current(Player p){return !closing&&p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,r);}
    private <T>void work(Callable<T> job,Consumer<T> done,Runnable fail){
        if(closing||pending>=128){fail.run();return;}pending++;
        io.execute(()->{try{T value=job.call();main(()->{pending--;done.accept(value);});}catch(Exception e){plugin.getLogger().warning("칭호 DB 처리 실패: "+e.getClass().getSimpleName()+": "+e.getMessage());main(()->{pending--;fail.run();});}});
    }
    private void load(Player p,boolean open,long sequence,boolean reply){
        UUID id=p.getUniqueId();if(!current(p))return;if(busy.contains(id)){if(reply)deferred.put(id,new ViewRequest(p,open,sequence));return;}busy.add(id);var defaults=defaults();
        work(()->store.load(id,defaults),state->{if(current(p)){busy.remove(id);cache.put(id,new Cached(state,plugin.names().name(p)));var next=deferred.remove(id);if(next!=null&&next.player()==p)reply(p,next.open(),next.sequence(),"");else if(reply)reply(p,open,sequence,"");}},()->{if(current(p)){busy.remove(id);var next=deferred.remove(id);if(reply||next!=null)p.sendMessage("칭호 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");}});
    }
    @EventHandler public void join(PlayerJoinEvent e){load(e.getPlayer(),false,0,false);}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();cache.remove(id);busy.remove(id);sessions.remove(id);limits.remove(id);deferred.remove(id);}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(!TitleProtocol.REQUEST.equals(channel)||!current(p)||!p.hasPermission("magiccodex.titles"))return;
        TitleProtocol.Request r;try{r=TitleProtocol.request(bytes);}catch(IllegalArgumentException e){return;}
        UUID id=p.getUniqueId();if(r.action()==TitleProtocol.CLOSE){var s=sessions.get(id);if(s!=null&&s.token().equals(r.token()))sessions.remove(id);return;}
        long now=System.currentTimeMillis();if(now<limits.getOrDefault(id,0L))return;limits.put(id,now+200);
        if(r.action()==TitleProtocol.OPEN){load(p,false,r.sequence(),true);return;}
        if(busy.contains(id))return;
        var session=sessions.get(id);if(session==null||session.player()!=p||session.until()<now||!session.token().equals(r.token())){p.sendMessage("칭호창을 다시 열어 주세요.");return;}
        if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR){reply(p,false,r.sequence(),"지금은 변경할 수 없습니다.");return;}
        var snapshot=catalog;var defaults=defaults();busy.add(id);
        work(()->store.select(id,r.revision(),r.prefix(),r.suffix(),snapshot,defaults),result->{if(current(p)){busy.remove(id);cache.put(id,new Cached(result.state(),plugin.names().name(p)));reply(p,false,r.sequence(),result.applied()?"칭호를 적용했습니다.":"보유 칭호가 변경되었습니다. 다시 선택해 주세요.");var next=deferred.remove(id);if(next!=null&&next.player()==p)reply(p,next.open(),next.sequence(),"");if(result.applied())p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.35f,1.3f);}},()->{if(current(p)){busy.remove(id);reply(p,false,r.sequence(),"저장하지 못했습니다. 다시 시도해 주세요.");var next=deferred.remove(id);if(next!=null&&next.player()==p)reply(p,next.open(),next.sequence(),"저장하지 못했습니다.");}});
    }
    private String selected(TitleStore.State s,String id,int side){var d=catalog.get(id);return d!=null&&d.enabled()&&d.side()==side&&s.owned().contains(id)?id:"";}
    private void reply(Player p,boolean open,long sequence,String message){
        var cached=cache.get(p.getUniqueId());if(cached==null||!current(p))return;
        if(!p.getListeningPluginChannels().contains(TitleProtocol.RESPONSE)){if(open)p.sendMessage("칭호 UI 모드를 업데이트해 주세요.");return;}
        var old=sessions.get(p.getUniqueId());String token=old!=null&&old.player()==p&&old.until()>System.currentTimeMillis()?old.token():UUID.randomUUID().toString();sessions.put(p.getUniqueId(),new Session(p,token,System.currentTimeMillis()+90000));
        var s=cached.state();var entries=catalog.values().stream().filter(d->d.enabled()&&s.owned().contains(d.id())).map(d->new TitleProtocol.Entry(d.id(),d.side(),d.name(),d.color())).toList();
        p.sendPluginMessage(plugin,TitleProtocol.RESPONSE,TitleProtocol.encode(new TitleProtocol.Response(open,sequence,token,s.revision(),plugin.names().name(p),selected(s,s.prefix(),0),selected(s,s.suffix(),1),message,entries)));
    }
    String placeholder(UUID id,String field){var c=cache.get(id);if(c==null)return "";var s=c.state();var prefix=catalog.get(selected(s,s.prefix(),0));var suffix=catalog.get(selected(s,s.suffix(),1));String a=prefix==null?"":prefix.name(),b=suffix==null?"":suffix.name();return switch(field){case "prefix"->a;case "suffix"->b;case "nickname"->c.nickname();case "full"->TitleDefinition.composed(a,c.nickname(),b);case "prefix_id"->prefix==null?"":prefix.id();case "suffix_id"->suffix==null?"":suffix.id();case "full_colored"->TitleDefinition.composed(prefix==null?"":colored(prefix),"§r"+c.nickname(),suffix==null?"":colored(suffix))+"§r";default->null;};}
    private static String colored(TitleDefinition d){var out=new StringBuilder("§x");for(char c:String.format(Locale.ROOT,"%06x",d.color()).toCharArray())out.append('§').append(c);return out.append(d.name()).append("§r").toString();}
    /** Trusted server-thread hook; return callback after durable commit. */
    void grant(UUID id,String title,boolean remove,Consumer<Boolean> done){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Use server thread");if(!catalog.containsKey(title)){done.accept(false);return;}var defaults=defaults();
        work(()->remove?store.revoke(id,title,defaults):store.grant(id,title,defaults),state->{Player p=Bukkit.getPlayer(id);if(p!=null&&current(p)){cache.put(id,new Cached(state,plugin.names().name(p)));if(sessions.containsKey(id))reply(p,false,0,remove?"칭호가 회수되었습니다.":"새 칭호를 획득했습니다.");}done.accept(true);},()->done.accept(false));
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(cmd.getName().equals("칭호")){if(sender instanceof Player p&&p.hasPermission("magiccodex.titles"))load(p,true,0,true);return true;}
        if(!sender.hasPermission("magiccodex.titles.admin"))return true;
        try{
            if(args.length==1&&args[0].equals("리로드")){reload();for(Player p:Bukkit.getOnlinePlayers())if(sessions.containsKey(p.getUniqueId()))reply(p,false,0,"");sender.sendMessage("칭호 설정을 불러왔습니다.");return true;}
            if(args.length==1&&args[0].equals("목록")){catalog.values().forEach(d->sender.sendMessage(d.id()+" · "+(d.side()==0?"접두사":"접미사")+" · "+d.name()));return true;}
            if(args.length>=2){OfflinePlayer target=Bukkit.getPlayerExact(args[1]);if(target==null)try{target=Bukkit.getOfflinePlayer(UUID.fromString(args[1]));}catch(IllegalArgumentException e){target=Bukkit.getOfflinePlayerIfCached(args[1]);}if(target==null)throw new IllegalArgumentException("온라인 이름 또는 UUID를 입력해 주세요.");UUID id=target.getUniqueId();
                if(args.length==3&&(args[0].equals("지급")||args[0].equals("회수"))){if(!catalog.containsKey(args[2]))throw new IllegalArgumentException("칭호 ID를 확인하세요. /칭호관리 목록");grant(id,args[2],args[0].equals("회수"),ok->sender.sendMessage(ok?"칭호 처리를 완료했습니다.":"칭호 처리에 실패했습니다."));return true;}
                if(args.length==2&&args[0].equals("확인")){var defaults=defaults();work(()->store.load(id,defaults),s->sender.sendMessage("보유: "+String.join(", ",new TreeSet<>(s.owned()))+" / 접두사 "+s.prefix()+" / 접미사 "+s.suffix()),()->sender.sendMessage("조회하지 못했습니다."));return true;}
            }
        }catch(Exception e){sender.sendMessage(e.getMessage());return true;}
        sender.sendMessage("/칭호관리 목록 | 리로드 | 지급 <플레이어/UUID> <ID> | 회수 <플레이어/UUID> <ID> | 확인 <플레이어/UUID>");return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){if(!c.getName().equals("칭호관리")||!s.hasPermission("magiccodex.titles.admin"))return List.of();List<String> list=args.length==1?List.of("목록","리로드","지급","회수","확인"):args.length==2?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():args.length==3?List.copyOf(catalog.keySet()):List.of();return list.stream().filter(v->v.startsWith(args[args.length-1])).toList();}
    @Override public void close(){closing=true;if(expansion!=null)expansion.unregister();io.execute(()->{try{store.close();}catch(Exception e){plugin.getLogger().warning("칭호 저장소 종료 실패");}});io.shutdown();try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("칭호 DB 종료 대기 초과");}catch(InterruptedException e){Thread.currentThread().interrupt();}cache.clear();sessions.clear();busy.clear();}
}
