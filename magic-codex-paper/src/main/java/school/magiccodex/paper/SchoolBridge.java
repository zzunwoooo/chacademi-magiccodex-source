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
        for(String c:List.of("기숙사점수","기증기록","기숙사분류")){Objects.requireNonNull(plugin.getCommand(c)).setExecutor(this);plugin.getCommand(c).setTabCompleter(this);}
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
        if(data.blocked().contains(r.spell())){reply(p,r,"관리자가 기증을 금지한 마법입니다.");return;}
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
        io.execute(()->{try{T value=task.run();SchoolStore.Snapshot refreshed=null;
                try{refreshed=store.snapshot(active);}catch(Exception refreshError){plugin.getLogger().warning("학교 기록 처리 완료, 화면 갱신 실패: "+refreshError);}
                var snapshot=refreshed;main(()->{pending--;if(snapshot!=null){data=snapshot;revision++;}done.accept(value);push();});}
            catch(Exception e){plugin.getLogger().warning("학교 기록 저장 실패: "+e);main(()->{pending--;fail.accept(e instanceof SchoolStore.DonationBlocked?"관리자가 기증을 금지한 마법입니다.":"저장하지 못했습니다. 점수 범위 또는 서버 로그를 확인해 주세요.");});}});
    }
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    private void push(){for(UUID id:List.copyOf(viewers.keySet())){Player p=Bukkit.getPlayer(id);if(p!=null)reply(p,new Request(SchoolProtocol.IDENTITY,0,0,""),"");}}
    void adminHouse(Player player,int house,Consumer<String> done){
        if(house<0||house>=SchoolProtocol.HOUSES.size()){done.accept("등록된 기숙사를 선택해 주세요.");return;}
        UUID id=player.getUniqueId();
        work(()->{store.house(id,house);return true;},ok->{DiscoveryLink.signal(id,"dormitory.assigned",1);reply(player,new Request(SchoolProtocol.IDENTITY,0,0,""),"");done.accept(null);},done);
    }
    void adminScore(int house,String action,long amount,Consumer<String> done){
        if(house<0||house>=SchoolProtocol.HOUSES.size())throw new IllegalArgumentException("등록된 기숙사를 선택해 주세요.");
        long delta=AdminCommandRules.scoreValue(action,amount);
        work(()->{if(!action.equals("조회"))store.change(house,delta,action.equals("설정"));return true;},ok->done.accept(SchoolProtocol.HOUSES.get(house)+" "+data.scores().get(house)+"점"),done);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
        if(cmd.getName().equals("기숙사분류"))return sortingCommand(sender,a);
        if(a.length==0){if(sender instanceof Player p){reply(p,new Request(SchoolProtocol.LIST,0,0,""),"open");}else sender.sendMessage("/기숙사점수 <추가|설정> <기숙사> <점수>");return true;}
        if(!sender.hasPermission("magiccodex.school.admin")){sender.sendMessage("관리 권한이 없습니다.");return true;}
        try{
            if(cmd.getName().equals("기증기록")){
                if(a.length>=3&&a[0].equals("초기화")&&a[a.length-1].equals("확인")){String input=String.join(" ",Arrays.copyOfRange(a,1,a.length-1));String id=input.equals("all")?"all":resolveDonation(input);work(()->{store.reset(id);return true;},v->sender.sendMessage("기증 기록을 초기화했습니다. 기숙사 점수는 유지됩니다."),sender::sendMessage);return true;}
                if(a.length>=2&&Set.of("상태","금지","해제").contains(a[0])){
                    String id=resolveDonation(String.join(" ",Arrays.copyOfRange(a,1,a.length)));
                    if(a[0].equals("상태")){work(()->true,ok->sender.sendMessage(spells.get(id).name()+" ("+id+"): "+(data.records().stream().anyMatch(record->record.spell().equals(id))?"기증 완료":"기증 기록 없음")+" · 기증 "+(data.blocked().contains(id)?"금지":"허용")),sender::sendMessage);}
                    else work(()->{store.setBlocked(id,a[0].equals("금지"));return true;},ok->sender.sendMessage(spells.get(id).name()+" 기증 "+a[0]+" 완료. 기존 기록과 점수는 유지됩니다."),sender::sendMessage);
                    return true;
                }
                if(a.length==1&&a[0].equals("reload")){loadCatalog();sender.sendMessage("기증 마법 설정을 다시 읽었습니다.");return true;}
                sender.sendMessage("/기증기록 초기화 <all|마법ID|한글명> 확인 · /기증기록 상태|금지|해제 <마법ID|한글명> · /기증기록 reload");return true;
            }
            if(a.length==2&&a[0].equals("초기화")&&a[1].equals("확인")){work(()->{store.resetPoints();return true;},v->sender.sendMessage("기숙사 점수만 초기화했습니다."),sender::sendMessage);return true;}
            if(a.length==3&&a[0].equals("소속")){Player p=plugin.names().resolve(a[1]);int h=parseHouse(a[2]);if(p==null||h<0)throw new IllegalArgumentException();UUID id=p.getUniqueId();work(()->{store.house(id,h);return true;},v->{DiscoveryLink.signal(id,"dormitory.assigned",1);sender.sendMessage("소속 기숙사를 저장했습니다.");reply(p,new Request(SchoolProtocol.IDENTITY,0,0,""),"");},sender::sendMessage);return true;}
            if(a.length==3&&(a[0].equals("추가")||a[0].equals("설정"))){int h=parseHouse(a[1]);long value=Long.parseLong(a[2]);if(h<0||value < -SchoolStore.MAX_SCORE||value>SchoolStore.MAX_SCORE)throw new IllegalArgumentException();boolean set=a[0].equals("설정");work(()->{store.change(h,value,set);return true;},v->sender.sendMessage(SchoolProtocol.HOUSES.get(h)+" "+data.scores().get(h)+"점"),sender::sendMessage);return true;}
        }catch(Exception e){sender.sendMessage("입력값을 확인해 주세요. "+e.getMessage());return true;}
        sender.sendMessage("/기숙사점수 <추가|설정> <기숙사> <점수> · /기숙사점수 소속 <유저> <기숙사> · /기숙사점수 초기화 확인");return true;
    }
    private boolean sortingAdmin(CommandSender sender){return sender instanceof ConsoleCommandSender||sender instanceof Player&&sender.hasPermission("magiccodex.school.admin");}
    private int sortingTolerance(){
        Object raw=plugin.getConfig().get("house-sorting.population-tolerance",2);
        if(!(raw instanceof Integer value))throw new IllegalArgumentException("house-sorting.population-tolerance은 정수여야 합니다.");
        HouseSorting.tolerance(value);return value;
    }
    private boolean sortingCommand(CommandSender sender,String[] a){
        if(!sortingAdmin(sender)){sender.sendMessage("관리자와 콘솔만 사용할 수 있습니다.");return true;}
        try{
            if(a.length==2&&a[0].equals("허용치")){
                int value=Integer.parseInt(a[1]);HouseSorting.tolerance(value);var config=plugin.getConfig();
                Object before=config.get("house-sorting.population-tolerance");config.set("house-sorting.population-tolerance",value);
                try{config.save(new File(plugin.getDataFolder(),"config.yml"));}catch(Exception error){config.set("house-sorting.population-tolerance",before);throw error;}
                sender.sendMessage("배정 전 최소 인원 + "+value+"명까지 허용합니다. (배정 후 차이는 허용치+1일 수 있습니다.)");return true;
            }
            if(a.length==1&&a[0].equals("현황")){
                work(store::houseCounts,counts->{for(int h=0;h<4;h++)sender.sendMessage(SchoolProtocol.HOUSES.get(h)+": "+counts[h]+"명");
                    long gap=Arrays.stream(counts).max().orElseThrow()-Arrays.stream(counts).min().orElseThrow();
                    sender.sendMessage("최대-최소 차이: "+gap+"명"+(gap>5?" · 경고: 기숙사 인원 차이가 5명을 초과했습니다.":""));},sender::sendMessage);return true;
            }
            if(a.length==2&&a[0].equals("시작")||a.length==3&&a[0].equals("결정")){
                Player player=plugin.names().resolve(a[1]);if(player==null||!player.isOnline())throw new IllegalArgumentException("현재 서버에 접속한 유저를 지정해 주세요.");
                UUID id=player.getUniqueId();
                if(a[0].equals("시작"))work(()->store.savedHouse(id),h->sortingDialogue(sender,player,h==null?"ch1-14":"ch1-14f-"+HouseSorting.IDS.get(h),false),sender::sendMessage);
                else{
                    if(!a[2].matches("p_[0-3][0-3]"))throw new IllegalArgumentException("결과는 p_00~p_33 형식이며 T,D는 각각 0~3입니다.");
                    int t=a[2].charAt(2)-'0',d=a[2].charAt(3)-'0',tolerance=sortingTolerance();
                    work(()->store.assignHouse(id,t,d,tolerance),result->{
                        // Database has committed before signals or Bukkit commands run on the main thread.
                        if(result.created())try{DiscoveryLink.signal(id,"dormitory.assigned",1);}catch(RuntimeException error){plugin.getLogger().warning("기숙사 저장 완료, 배정 신호 실패: "+error);sender.sendMessage("소속은 저장됐지만 배정 신호 처리에 실패했습니다. 로그를 확인해 주세요.");}
                        reply(player,new Request(SchoolProtocol.IDENTITY,0,0,""),"");
                        sortingDialogue(sender,player,"ch1-14"+(!result.created()||result.house()==result.preferred()?"r-":"m-")+HouseSorting.IDS.get(result.house()),true);
                    },sender::sendMessage);
                }return true;
            }
        }catch(Exception error){sender.sendMessage("입력 또는 설정 저장 실패: "+error.getMessage());return true;}
        sender.sendMessage("/기숙사분류 시작 <유저> · 결정 <유저> <p_TD> · 현황 · 허용치 <0~1000000>");return true;
    }
    private void sortingDialogue(CommandSender sender,Player player,String dialogue,boolean saved){
        String state=saved?"소속은 저장되어 있습니다. 결정 명령을 다시 실행하면 기존 소속으로 결과 대사를 재시도합니다.":"";
        if(!player.isOnline()){sender.sendMessage("유저가 접속을 종료하여 대사를 실행하지 못했습니다. "+state);return;}
        try{if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"storydialogue "+player.getName()+" "+dialogue))sender.sendMessage("스토리 명령을 실행하지 못했습니다. "+state);}
        catch(RuntimeException error){plugin.getLogger().warning("기숙사 대사 실행 실패: "+error);sender.sendMessage("기숙사 대사를 실행하지 못했습니다. "+state);}
    }
    private List<String> sortingTabs(CommandSender sender,String[] a){
        if(!sortingAdmin(sender)||a.length==0)return List.of();Collection<String> choices=List.of();
        if(a.length==1)choices=List.of("시작","결정","현황","허용치");
        else if(a.length==2&&Set.of("시작","결정").contains(a[0]))choices=Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        else if(a.length==2&&a[0].equals("허용치"))choices=List.of("0","1","2","3","5");
        else if(a.length==3&&a[0].equals("결정")){var values=new ArrayList<String>();for(int t=0;t<4;t++)for(int d=0;d<4;d++)values.add("p_"+t+d);choices=values;}
        String prefix=a[a.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
    private String resolveDonation(String input){
        if(spells.containsKey(input))return input;
        var matches=spells.entrySet().stream().filter(e->e.getValue().name().replace(" ","").equals(input.replace(" ",""))).map(Map.Entry::getKey).toList();
        if(matches.size()!=1)throw new IllegalArgumentException(matches.isEmpty()?"등록된 마법 이름 또는 ID를 입력해 주세요.":"이름이 중복됩니다. 마법 ID를 입력해 주세요.");
        return matches.getFirst();
    }
    static int parseHouse(String value){int h=SchoolProtocol.HOUSES.indexOf(value);return h>=0?h:List.of("arkeon","lumina","bestiaz","noxer").indexOf(value.toLowerCase(Locale.ROOT));}
    public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){
        if(c.getName().equals("기숙사분류"))return sortingTabs(s,a);
        if(!s.hasPermission("magiccodex.school.admin")||a.length==0)return List.of();
        if(c.getName().equals("기증기록")){
            Collection<String> choices=List.of();
            if(a.length==1)choices=List.of("초기화","상태","금지","해제","reload");
            else if(a.length==2&&Set.of("초기화","상태","금지","해제").contains(a[0])){var names=new TreeSet<String>(spells.keySet());spells.values().forEach(v->names.add(v.name().replace(" ","")));if(a[0].equals("초기화"))names.add("all");choices=names;}
            else if(a.length>=3&&a[0].equals("초기화"))choices=List.of("확인");
            String prefix=a[a.length-1];return choices.stream().filter(v->v.startsWith(prefix)).sorted().toList();
        }
        if(a.length==1)return List.of("추가","설정","소속","초기화");
        if(a.length==2&&(a[0].equals("추가")||a[0].equals("설정"))||a.length==3&&a[0].equals("소속"))return SchoolProtocol.HOUSES;return List.of();
    }
    @EventHandler public void quit(PlayerQuitEvent e){limits.remove(e.getPlayer().getUniqueId());viewers.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void join(PlayerJoinEvent e){if(shared)refreshShared();}
    public void close(){closing=true;if(expansion!=null)expansion.unregister();io.submit(()->{try{store.close();}catch(Exception e){plugin.getLogger().warning(e.toString());}});io.shutdown();try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("학교 DB 종료 대기시간 초과");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
