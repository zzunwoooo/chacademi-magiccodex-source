package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.SchoolProtocol;
import school.magiccodex.protocol.SchoolProtocol.*;
import school.magiccodex.database.DatabaseSettings;

final class SchoolBridge implements PluginMessageListener,Listener,CommandExecutor,TabCompleter,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final StatsService stats;
    private final SchoolStore store;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-school-io");t.setDaemon(true);return t;});
    private volatile SchoolStore.Snapshot data;
    SchoolStore.Snapshot data(){return data;}
    private record Spell(String name,String permission,long points){}
    private Map<String,Spell> spells=Map.of();
    private final Map<UUID,Long> limits=new HashMap<>(),viewers=new HashMap<>();
    private long revision=1;private int pending;private boolean closing;
    private boolean shared,refreshing;
    private SchoolExpansion expansion;
    SchoolBridge(MagicCodexBridge plugin,StatsService stats)throws Exception{
        this.plugin=plugin;this.stats=stats;
        if(!new File(plugin.getDataFolder(),"donation-spells.yml").exists())plugin.saveResource("donation-spells.yml",false);
        loadCatalog();
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));shared=settings.mariaDb();store=io.submit(()->new SchoolStore(plugin.getDataFolder().toPath().resolve("school.db"),settings)).get(10,TimeUnit.SECONDS);var active=shared?activePlayers():null;data=io.submit(()->store.snapshot(active)).get(10,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,SchoolProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,SchoolProtocol.RESPONSE);Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String c:List.of("기숙사점수","기증기록")){Objects.requireNonNull(plugin.getCommand(c)).setExecutor(this);plugin.getCommand(c).setTabCompleter(this);}
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new SchoolExpansion(this);expansion.register();}
        Bukkit.getScheduler().runTaskTimer(plugin,()->{long n=System.currentTimeMillis();viewers.values().removeIf(v->v<n);limits.values().removeIf(v->v<n);},200,200);
        if(shared)Bukkit.getScheduler().runTaskTimer(plugin,this::refreshShared,40,40);
    }
    private void refreshShared(){
        if(closing||refreshing)return;refreshing=true;
        var active=activePlayers();
        io.execute(()->{try{var snapshot=store.snapshot(active);main(()->{refreshing=false;if(!snapshot.equals(data)){data=snapshot;revision++;push();}});}
            catch(Exception error){plugin.getLogger().warning("공용 기숙사 기록 갱신 실패: "+error.getMessage());main(()->refreshing=false);}});
    }
    private Set<UUID> activePlayers(){var ids=new HashSet<UUID>();for(Player p:Bukkit.getOnlinePlayers())ids.add(p.getUniqueId());return Set.copyOf(ids);}
    private void loadCatalog()throws Exception{
        var y=new YamlConfiguration();y.load(new File(plugin.getDataFolder(),"donation-spells.yml"));var root=y.getConfigurationSection("spells");if(root==null||root.getKeys(false).size()>4096)throw new IllegalArgumentException("Donation catalog size");var next=new HashMap<String,Spell>();
        for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);if(s==null)throw new IllegalArgumentException(id);String name=s.getString("name",id),permission=s.getString("permission","");long points=s.getLong("points",1);
            if(!id.matches("[a-z0-9_-]{1,64}")||name.length()>100||name.isBlank()||!permission.matches("[a-z0-9_.-]{1,100}")||points<0||points>1000000)throw new IllegalArgumentException("Invalid donation: "+id);next.put(id,new Spell(name,permission,points));}
        spells=Map.copyOf(next);
    }
    int house(Player p){
        Integer saved=data.houses().get(p.getUniqueId());if(saved!=null)return saved;
        String dorm=stats.get(p.getUniqueId()).dormitory();int i=dorm==null?-1:SchoolProtocol.HOUSES.indexOf(dorm);if(i>=0)return i;
        for(i=0;i<4;i++)if(p.hasPermission("magiccodex.house."+List.of("arkeon","lumina","bestiaz","noxer").get(i)))return i;
        return -1;
    }
    void questReward(String token,int house,long points,Runnable done,Consumer<String> fail){work(()->store.questReward(token,house,points),ok->done.run(),fail);}
    String dorm(Player p){int h=house(p);return h<0?"":SchoolProtocol.HOUSES.get(h);}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(!channel.equals(SchoolProtocol.REQUEST))return;Request r;try{r=SchoolProtocol.request(bytes);}catch(IllegalArgumentException e){return;}
        long now=System.currentTimeMillis();if(limits.getOrDefault(p.getUniqueId(),0L)>now)return;limits.put(p.getUniqueId(),now+100);
        if(r.action()==SchoolProtocol.IDENTITY){reply(p,r,"");return;}
        if(!p.hasPermission("magiccodex.school")){reply(p,r,"학교 기록을 볼 권한이 없습니다.");return;}
        viewers.put(p.getUniqueId(),now+45000);
        if(r.action()!=SchoolProtocol.DONATE){reply(p,r,"");return;}
        if(!p.hasPermission("magiccodex.donate")){reply(p,r,"기증할 권한이 없습니다.");return;}
        // Duplicate status takes precedence so every later donor gets the same clear response.
        if(data.records().stream().anyMatch(e->e.spell().equals(r.spell()))){reply(p,r,"누군가 이미 기증한 마법입니다.");return;}
        Spell spell=spells.get(r.spell());if(spell==null||!p.hasPermission(spell.permission())){reply(p,r,"배운 마법만 기증할 수 있습니다.");return;}
        int h=house(p);if(h<0){reply(p,r,"소속 기숙사가 지정되지 않았습니다.");return;}
        if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR){reply(p,r,"지금은 기증할 수 없습니다.");return;}
        var record=new Donation(r.spell(),spell.name(),p.getUniqueId(),plugin.names().name(p),h,now);
        work(()->store.donate(record,spell.points()),ok->reply(p,r,ok?"학교에 기증했습니다. +"+spell.points()+"점":"누군가 이미 기증한 마법입니다."),error->reply(p,r,error));
    }
    private void reply(Player p,Request r,String message){
        if(!p.isOnline()||!p.getListeningPluginChannels().contains(SchoolProtocol.RESPONSE))return;
        var snapshot=data;List<Donation> records;
        if(r.action()==SchoolProtocol.LIST){int from=Math.min(snapshot.records().size(),r.page()*SchoolProtocol.PAGE_SIZE);records=snapshot.records().subList(from,Math.min(from+SchoolProtocol.PAGE_SIZE,snapshot.records().size()));}
        else if(r.action()==SchoolProtocol.IDENTITY)records=List.of();
        else records=snapshot.records().stream().filter(e->e.spell().equals(r.spell())).limit(1).toList();
        records=records.stream().map(e->new Donation(e.spell(),e.spellName(),e.donor(),plugin.names().name(Bukkit.getOfflinePlayer(e.donor()),e.nickname()),e.house(),e.time())).toList();
        var response=new Response(r.action(),r.sequence(),revision,r.page(),snapshot.records().size(),r.spell(),message,plugin.names().name(p),dorm(p),snapshot.scores(),records);
        p.sendPluginMessage(plugin,SchoolProtocol.RESPONSE,SchoolProtocol.encode(response));
    }
    private interface Job<T>{T run()throws Exception;}
    private <T> void work(Job<T> task,Consumer<T> done,Consumer<String> fail){
        if(closing||pending>=64){fail.accept("다른 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.");return;}pending++;
        var active=shared?activePlayers():null;
        io.execute(()->{try{T value=task.run();var snapshot=store.snapshot(active);main(()->{pending--;data=snapshot;revision++;done.accept(value);push();});}
            catch(Exception e){plugin.getLogger().warning("학교 기록 저장 실패: "+e);main(()->{pending--;fail.accept("저장하지 못했습니다. 점수 범위 또는 서버 로그를 확인해 주세요.");});}});
    }
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    private void push(){for(UUID id:List.copyOf(viewers.keySet())){Player p=Bukkit.getPlayer(id);if(p!=null)reply(p,new Request(SchoolProtocol.IDENTITY,0,0,""),"");}}
    void adminHouse(Player player,int house,Consumer<String> done){
        if(house<0||house>=SchoolProtocol.HOUSES.size()){done.accept("등록된 기숙사를 선택해 주세요.");return;}
        UUID id=player.getUniqueId();
        work(()->{store.house(id,house);return true;},ok->{reply(player,new Request(SchoolProtocol.IDENTITY,0,0,""),"");done.accept(null);},done);
    }
    void adminScore(int house,String action,long amount,Consumer<String> done){
        if(house<0||house>=SchoolProtocol.HOUSES.size())throw new IllegalArgumentException("등록된 기숙사를 선택해 주세요.");
        long delta=AdminCommandRules.scoreValue(action,amount);
        work(()->{if(!action.equals("조회"))store.change(house,delta,action.equals("설정"));return true;},ok->done.accept(SchoolProtocol.HOUSES.get(house)+" "+data.scores().get(house)+"점"),done);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
        if(a.length==0){if(sender instanceof Player p){reply(p,new Request(SchoolProtocol.LIST,0,0,""),"open");}else sender.sendMessage("/기숙사점수 <추가|설정> <기숙사> <점수>");return true;}
        if(!sender.hasPermission("magiccodex.school.admin")){sender.sendMessage("관리 권한이 없습니다.");return true;}
        try{
            if(cmd.getName().equals("기증기록")){
                if(a.length==3&&a[0].equals("초기화")&&a[2].equals("확인")){if(!a[1].equals("all")&&!a[1].matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException();work(()->{store.reset(a[1]);return true;},v->sender.sendMessage("기증 기록을 초기화했습니다. 기숙사 점수는 유지됩니다."),sender::sendMessage);return true;}
                if(a.length==1&&a[0].equals("reload")){loadCatalog();sender.sendMessage("기증 마법 설정을 다시 읽었습니다.");return true;}
                sender.sendMessage("/기증기록 초기화 <all|마법ID> 확인 · /기증기록 reload");return true;
            }
            if(a.length==2&&a[0].equals("초기화")&&a[1].equals("확인")){work(()->{store.resetPoints();return true;},v->sender.sendMessage("기숙사 점수만 초기화했습니다."),sender::sendMessage);return true;}
            if(a.length==3&&a[0].equals("소속")){Player p=plugin.names().resolve(a[1]);int h=parseHouse(a[2]);if(p==null||h<0)throw new IllegalArgumentException();UUID id=p.getUniqueId();work(()->{store.house(id,h);return true;},v->{sender.sendMessage("소속 기숙사를 저장했습니다.");reply(p,new Request(SchoolProtocol.IDENTITY,0,0,""),"");},sender::sendMessage);return true;}
            if(a.length==3&&(a[0].equals("추가")||a[0].equals("설정"))){int h=parseHouse(a[1]);long value=Long.parseLong(a[2]);if(h<0||value < -SchoolStore.MAX_SCORE||value>SchoolStore.MAX_SCORE)throw new IllegalArgumentException();boolean set=a[0].equals("설정");work(()->{store.change(h,value,set);return true;},v->sender.sendMessage(SchoolProtocol.HOUSES.get(h)+" "+data.scores().get(h)+"점"),sender::sendMessage);return true;}
        }catch(Exception e){sender.sendMessage("입력값을 확인해 주세요. "+e.getMessage());return true;}
        sender.sendMessage("/기숙사점수 <추가|설정> <기숙사> <점수> · /기숙사점수 소속 <유저> <기숙사> · /기숙사점수 초기화 확인");return true;
    }
    static int parseHouse(String value){int h=SchoolProtocol.HOUSES.indexOf(value);return h>=0?h:List.of("arkeon","lumina","bestiaz","noxer").indexOf(value.toLowerCase(Locale.ROOT));}
    public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){if(!s.hasPermission("magiccodex.school.admin"))return List.of();if(a.length==1)return c.getName().equals("기증기록")?List.of("초기화","reload"):List.of("추가","설정","소속","초기화");if(a.length==2&&(a[0].equals("추가")||a[0].equals("설정"))||a.length==3&&a[0].equals("소속"))return SchoolProtocol.HOUSES;return List.of();}
    @EventHandler public void quit(PlayerQuitEvent e){limits.remove(e.getPlayer().getUniqueId());viewers.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void join(PlayerJoinEvent e){if(shared)refreshShared();}
    public void close(){closing=true;if(expansion!=null)expansion.unregister();io.submit(()->{try{store.close();}catch(Exception e){plugin.getLogger().warning(e.toString());}});io.shutdown();try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("학교 DB 종료 대기시간 초과");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
