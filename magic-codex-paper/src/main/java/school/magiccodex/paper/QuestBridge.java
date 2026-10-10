package school.magiccodex.paper;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.QuestProtocol;
import school.magiccodex.protocol.QuestAdminProtocol;
import static school.magiccodex.paper.QuestDefinition.*;

final class QuestBridge implements Listener,CommandExecutor,TabCompleter,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;private final File file;private final QuestStore store;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-quest-io");t.setDaemon(true);return t;});
    private Map<String,QuestDefinition> catalog=Map.of();private final Map<UUID,List<QuestStore.Entry>> cache=new HashMap<>();
    private final Map<UUID,Set<String>> completed=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();private final Map<UUID,Long> limits=new HashMap<>(),viewers=new HashMap<>();
    private final Map<UUID,Integer> tabs=new HashMap<>();private final Map<UUID,Integer> pages=new HashMap<>();private String server;private int pending;private volatile boolean closing;private boolean refreshing;
    /** 공용 카탈로그의 변경 번호(codex_quest_lock 의 catalog 행). 바뀐 때에만 정의 전체를 다시 읽는다. */
    private long catalogRevision=-1;
    /** 처치·이벤트 진행도는 메모리에 모았다가 FLUSH_TICKS 마다(또는 퇴장·종료·보상 수령·목록 열기 때) 유저당 한 트랜잭션으로 기록한다. 버리지 않는다. */
    private static final long FLUSH_TICKS=60;
    private record DeltaKey(Type type,String target,String world){}
    private static final class Batch {final Map<DeltaKey,Integer> counts=new LinkedHashMap<>();long since;Batch(long since){this.since=since;}}
    private final Map<UUID,Batch> deltas=new HashMap<>();private final Map<UUID,Integer> flushing=new HashMap<>();private final Map<UUID,Long> notices=new HashMap<>();
    QuestBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"quests.yml");if(!file.exists())plugin.saveResource("quests.yml",false);
        var local=read();var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));
        try{store=io.submit(()->new QuestStore(settings,plugin.getDataFolder().toPath().resolve("quests.db"))).get(15,TimeUnit.SECONDS);io.submit(()->{store.seed(local);return null;}).get(15,TimeUnit.SECONDS);catalog=io.submit(store::catalog).get(15,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
        Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getMessenger().registerIncomingPluginChannel(plugin,QuestProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,QuestProtocol.RESPONSE);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,QuestAdminProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,QuestAdminProtocol.RESPONSE);
        for(String c:List.of("의뢰","의뢰관리")){plugin.getCommand(c).setExecutor(this);plugin.getCommand(c).setTabCompleter(this);}
        hook("Citizens","net.citizensnpcs.api.event.NPCRightClickEvent",event->{Player p=(Player)invoke(event,"getClicker");Object npc=invoke(event,"getNPC");Entity e=(Entity)invoke(npc,"getEntity");if(e!=null&&e.getWorld().equals(p.getWorld())&&e.getLocation().distanceSquared(p.getLocation())<=36)event(p,Type.NPC,String.valueOf(invoke(npc,"getId")),1);});
        hook("MythicMobs","io.lumine.mythic.bukkit.events.MythicMobDeathEvent",event->{Object killer=invoke(event,"getKiller");if(killer instanceof Player p&&invoke(event,"getEntity") instanceof Entity e&&!e.getScoreboardTags().contains("chacademia_pet"))event(p,Type.MYTHIC_KILL,(String)invoke(invoke(event,"getMobType"),"getInternalName"),1);});
        Bukkit.getScheduler().runTaskTimer(plugin,()->{if(!refreshing&&!closing){refreshing=true;long known=catalogRevision;work(()->{long revision=store.catalogRevision();return revision==known?null:new Published(revision,store.catalog());},v->{refreshing=false;if(v!=null){catalog=v.catalog();catalogRevision=v.revision();}},m->refreshing=false);}for(Player p:Bukkit.getOnlinePlayers())if(viewers.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis())load(p,false,"");},100,100);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers()){var entries=cache.get(p.getUniqueId());if(entries==null||entries.stream().anyMatch(e->e.status().equals("active")&&e.quest().objectives().stream().anyMatch(g->g.type()==Type.BIOME)))event(p,Type.BIOME,p.getWorld().getBiome(p.getLocation()).getKey().toString(),1);}},40,40);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(UUID id:new ArrayList<>(deltas.keySet()))flush(id,false);},FLUSH_TICKS,FLUSH_TICKS);
        for(Player p:Bukkit.getOnlinePlayers())load(p,false,"");
    }
    private record Published(long revision,Map<String,QuestDefinition> catalog){}
    boolean dialogueCondition(Player p,String id,String status){return cache.getOrDefault(p.getUniqueId(),List.of()).stream().anyMatch(e->e.quest().id().equals(id)&&e.current(System.currentTimeMillis())&&(status.equals("completed")?e.completions()>0:e.status().equals("active")));}
    /** 외부(ChacaNPC) 제안 가능 여부: 존재·공개·권한, 그리고 진행 중이 아니고 일회성 의뢰는 완료 전. 캐시만 본다(메인 스레드). */
    boolean npcOfferable(Player p,String id){
        var q=catalog.get(id);long now=System.currentTimeMillis();
        if(q==null||!q.available(now)||!QuestStore.unlocked(q,completed.getOrDefault(p.getUniqueId(),Set.of()))||(!q.permission().isEmpty()&&!p.hasPermission(q.permission()))||!p.hasPermission("magiccodex.quests"))return false;
        return cache.getOrDefault(p.getUniqueId(),List.of()).stream().noneMatch(e->e.quest().id().equals(id)&&e.current(now)&&(e.status().equals("active")||(!q.daily()&&e.completions()>0)));
    }
    /**
     * 대화의 quest 동작을 실행하기 전에 확인한다(캐시만 봄, 메인 스레드). 받을 수 있거나 이미 진행 중이면 빈 문자열,
     * 아니면 유저에게 보여 줄 이유. QuestStore.accept 의 조건과 같은 순서다.
     */
    String acceptRefusal(Player p,String id){
        var q=catalog.get(id);UUID uid=p.getUniqueId();long now=System.currentTimeMillis();
        if(q==null||!q.available(now))return "지금은 받을 수 없는 의뢰입니다.";
        if(!p.hasPermission("magiccodex.quests")||(!q.permission().isEmpty()&&!p.hasPermission(q.permission())))return "이 의뢰를 받을 권한이 없습니다.";
        var entries=cache.get(uid);if(entries==null)return "의뢰 정보를 불러오는 중입니다. 잠시 뒤 다시 시도해 주세요.";
        if(entries.stream().anyMatch(e->e.quest().id().equals(id)&&e.current(now)&&e.status().equals("active")))return "";
        if(entries.stream().anyMatch(e->e.quest().id().equals(id)&&e.status().equals("paying")))return "이 의뢰의 보상 처리가 아직 끝나지 않았습니다.";
        if(!QuestStore.unlocked(q,completed.getOrDefault(uid,Set.of())))return "먼저 끝내야 하는 의뢰가 있습니다.";
        if(!q.main()&&QuestStore.activeSubquests(entries,now)>=3)return "진행 중인 의뢰가 3개입니다. 하나를 완료하거나 포기한 뒤 다시 시도해 주세요.";
        String cycle=q.cycle(now);
        if(entries.stream().anyMatch(e->e.quest().id().equals(id)&&e.cycle().equals(cycle)&&e.completions()>=q.completionLimit()))return q.daily()?"오늘은 이 의뢰를 더 받을 수 없습니다.":"이미 완료한 의뢰입니다.";
        return "";
    }
    int activeCount(Player p){long now=System.currentTimeMillis();return QuestStore.activeSubquests(cache.getOrDefault(p.getUniqueId(),List.of()),now);}
    String title(String id){var q=catalog.get(id);return q==null?"":q.title();}
    void acceptFromDialogue(Player p,String id,Consumer<Boolean> done){
        var q=catalog.get(id);UUID uid=p.getUniqueId();long now=System.currentTimeMillis();
        if(q==null||!q.available(now)||(!q.permission().isEmpty()&&!p.hasPermission(q.permission()))||!p.hasPermission("magiccodex.quests")){done.accept(false);return;}
        flush(uid,true);/* 수락 전에 모인 진행도가 새 의뢰에 들어가지 않게 먼저 기록한다 */
        work(()->{var all=store.entries(uid);if(all.stream().anyMatch(e->e.quest().id().equals(id)&&e.current(now)&&e.status().equals("active")))return true;return store.accept(uid,q,now);},ok->{if(current(p))load(p,false,"");done.accept(ok);},m->done.accept(false));
    }
    void eventFromDialogue(Player p,String id,Consumer<Boolean> done){
        UUID uid=p.getUniqueId();String world=p.getWorld().getName(),sid=server;long now=System.currentTimeMillis();
        flush(uid,true);
        work(()->{store.advance(uid,Type.EVENT,id,sid,world,1,now);return store.entries(uid);},v->{if(current(p)){var later=deltas.get(uid);if(later!=null)for(var e:later.counts.entrySet())apply(v,e.getKey().type(),e.getKey().target(),e.getKey().world(),e.getValue(),System.currentTimeMillis());cache.put(uid,v);}done.accept(true);},m->done.accept(false));
    }
    private interface Checked{void run(Object e)throws Exception;}
    @SuppressWarnings("unchecked") private void hook(String owner,String cls,Checked callback){var dep=Bukkit.getPluginManager().getPlugin(owner);if(dep==null||!dep.isEnabled())return;try{Class<? extends Event> type=(Class<? extends Event>)Class.forName(cls,true,dep.getClass().getClassLoader());Bukkit.getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(l,e)->{try{callback.run(e);}catch(Exception ex){plugin.getLogger().warning("Quest hook: "+ex.getClass().getSimpleName());}},plugin,true);}catch(Exception e){plugin.getLogger().warning("의뢰 "+owner+" 연결 실패: "+e.getClass().getSimpleName());}}
    private static Object invoke(Object o,String method)throws Exception{return o.getClass().getMethod(method).invoke(o);}
    private Map<String,QuestDefinition> read()throws Exception{
        var y=new YamlConfiguration();y.load(file);String sid=y.getString("server-id","school");if(!sid.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("server-id");
        var result=new TreeMap<String,QuestDefinition>();var root=y.getConfigurationSection("quests");if(root==null)throw new IllegalArgumentException("quests 항목 없음");if(root.getKeys(false).size()>QuestAdminProtocol.MAX_ENTRIES)throw new IllegalArgumentException("최대 "+QuestAdminProtocol.MAX_ENTRIES+"개 의뢰");
        for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);var goals=new ArrayList<Objective>();for(var m:s.getMapList("objectives")){
            Type t=Type.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));String target=String.valueOf(m.get("target"));int amount=Integer.parseInt(String.valueOf(m.get("amount")));
            if(t==Type.SUBMIT)submissionItem(target);
            if(t==Type.KILL&&!Arrays.stream(EntityType.values()).anyMatch(e->e.name().equals(target)&&e.isAlive()))throw new IllegalArgumentException(id+" 대상 오류");
            goals.add(new Objective(t,target,amount,String.valueOf(m.containsKey("label")?m.get("label"):target),String.valueOf(m.containsKey("server")?m.get("server"):""),String.valueOf(m.containsKey("world")?m.get("world"):"")));
        }
        var q=new QuestDefinition(id,s.getString("title",id),s.getString("description",""),s.getString("permission",""),s.getBoolean("daily",true),s.getBoolean("enabled",true),goals,s.getString("reward.label",""),s.getStringList("reward.commands"),s.getStringList("reward.items"),s.getString("rank","F").toUpperCase(Locale.ROOT),s.getInt("completion-limit",1),parseTime(s.getString("opens-at","")),s.getDouble("reward.money",0),s.getInt("reward.house-points",0),s.getBoolean("main",false),s.getStringList("requires"));
        for(String item:q.items())if(Material.matchMaterial(item.split(":")[0])==null)throw new IllegalArgumentException("보상 아이템 오류");if(q.encode().length()>60000)throw new IllegalArgumentException("의뢰 데이터가 너무 큽니다: "+id);result.put(id,q);}
        server=sid;return Map.copyOf(result);
    }
    @EventHandler public void join(PlayerJoinEvent e){load(e.getPlayer(),false,"");}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();flush(id,true);notices.remove(id);cache.remove(id);completed.remove(id);busy.remove(id);limits.remove(id);viewers.remove(id);pages.remove(id);tabs.remove(id);adminLimits.remove(id);adminBusy.remove(id);}
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){Player p=e.getEntity().getKiller();if(p!=null&&!(e.getEntity() instanceof Player)&&!e.getEntity().hasMetadata("NPC")&&!e.getEntity().getScoreboardTags().contains("chacademia_pet"))event(p,Type.KILL,e.getEntityType().name(),1);}
    /** Called only by trusted server integrations (never client progress packets). */
    void event(Player p,Type type,String target,int amount){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Use the server thread");if(closing||!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||amount<1||amount>100000||type==Type.SUBMIT)return;
        UUID id=p.getUniqueId();String world=p.getWorld().getName();long time=System.currentTimeMillis();
        var entries=cache.get(id);if(entries!=null&&entries.stream().noneMatch(e->tracks(e,type,target,world)))return;
        // DB 에는 바로 쓰지 않는다. 화면용 캐시에는 즉시 반영하고(목표 수치에서 멈춤), 기록은 flush 가 묶어서 한다.
        deltas.computeIfAbsent(id,k->new Batch(time)).counts.merge(new DeltaKey(type,target,world),amount,(a,b)->(int)Math.min(1_000_000L,(long)a+b));
        if(entries!=null&&apply(entries,type,target,world,amount,time)&&viewers.getOrDefault(id,0L)>time)reply(p,false,"");
    }
    /** 캐시에 진행도를 더한다(목표 수치까지만). 바뀐 것이 있으면 true. */
    private boolean apply(List<QuestStore.Entry> entries,Type type,String target,String world,int amount,long now){
        boolean changed=false;if(type==Type.SUBMIT)return false;
        for(var e:entries){if(!e.status().equals("active")||!e.current(now))continue;for(int i=0;i<e.progress().length;i++){var g=e.quest().objectives().get(i);if(g.matches(type,target,server,world)){int v=(int)Math.min(g.amount(),(long)e.progress()[i]+amount);if(v!=e.progress()[i]){e.progress()[i]=v;changed=true;}}}}
        return changed;
    }
    /**
     * 모아 둔 진행도를 한 트랜잭션으로 기록한다. force=false(주기 기록)는 그 유저의 기록이 진행 중이면 다음 주기로 미룬다.
     * 실패하면 수치를 다시 모아 두고 다음 주기에 재시도한다. 대기 수 한도(pending)와 무관하게 접수한다(유저당 묶음 하나라 유저 수로 제한됨).
     */
    private void flush(UUID id,boolean force){
        var batch=deltas.get(id);if(batch==null||closing||(!force&&flushing.containsKey(id)))return;
        deltas.remove(id);flushing.merge(id,1,Integer::sum);
        var list=batch.counts.entrySet().stream().map(e->new QuestStore.Delta(e.getKey().type(),e.getKey().target(),e.getKey().world(),e.getValue())).toList();String sid=server;long since=batch.since;
        // 기록(advanceAll)이 끝난 뒤의 다시 읽기 실패는 기록 실패가 아니다: 수치를 다시 모아 두면 두 번 반영되므로 캐시 갱신만 건너뛴다.
        submit(()->{store.advanceAll(id,list,sid,since);try{return store.entries(id);}catch(Exception e){return null;}},v->{
            flushing.computeIfPresent(id,(k,n)->n>1?n-1:null);Player p=Bukkit.getPlayer(id);if(v==null||p==null||!current(p))return;
            // 기록하는 사이에 새로 모인 수치는 아직 DB에 없으므로 화면용 캐시에 다시 더해 둔다.
            var later=deltas.get(id);if(later!=null)for(var e:later.counts.entrySet())apply(v,e.getKey().type(),e.getKey().target(),e.getKey().world(),e.getValue(),System.currentTimeMillis());
            cache.put(id,v);if(viewers.getOrDefault(id,0L)>System.currentTimeMillis())reply(p,false,"");
        },m->{
            flushing.computeIfPresent(id,(k,n)->n>1?n-1:null);
            var back=deltas.computeIfAbsent(id,k->new Batch(since));back.since=Math.min(back.since,since);for(var d:list)back.counts.merge(new DeltaKey(d.type(),d.target(),d.world()),d.amount(),(a,b)->(int)Math.min(1_000_000L,(long)a+b));
            long now=System.currentTimeMillis();Player p=Bukkit.getPlayer(id);
            if(p!=null&&current(p)&&now>=notices.getOrDefault(id,0L)){notices.put(id,now+30000);p.sendMessage("의뢰 진행도 저장이 지연되고 있습니다. 진행도는 보관 중이며 자동으로 다시 저장합니다.");}
        });
    }
    private boolean tracks(QuestStore.Entry e,Type type,String target,String world){if(!e.status().equals("active")||!e.current(System.currentTimeMillis()))return false;for(int i=0;i<e.progress().length;i++){var g=e.quest().objectives().get(i);if(e.progress()[i]<g.amount()&&g.matches(type,target,server,world))return true;}return false;}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){if(QuestAdminProtocol.REQUEST.equals(channel)){adminPacket(p,bytes);return;}if(!QuestProtocol.REQUEST.equals(channel))return;try{var r=QuestProtocol.request(bytes);tabs.put(p.getUniqueId(),r.tab());request(p,r.action(),r.id(),r.page(),false);}catch(IllegalArgumentException ignored){}}
    private void request(Player p,int action,String id,int page,boolean open){
        UUID uid=p.getUniqueId();long now=System.currentTimeMillis();if(!p.hasPermission("magiccodex.quests")||now<limits.getOrDefault(uid,0L)||busy.contains(uid))return;
        limits.put(uid,now+250);pages.put(uid,page);viewers.put(uid,now+15000);
        if(action==0){load(p,open,"");return;}if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!plugin.playerStateReady(p)){reply(p,false,"지금은 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");return;}
        if(action==1){var q=catalog.get(id);if(q==null||!q.available(now)||(!q.permission().isEmpty()&&!p.hasPermission(q.permission()))){reply(p,false,"수락 조건을 충족하지 않습니다.");return;}busy.add(uid);flush(uid,true);work(uid,()->store.accept(uid,q,now),ok->{busy.remove(uid);load(p,false,ok?"의뢰를 수락했습니다.":"수락 한도 또는 클리어 가능 횟수를 확인해 주세요.");},m->{busy.remove(uid);reply(p,false,m);});}
        if(action==3){busy.add(uid);flush(uid,true);work(uid,()->store.abandon(uid,id,now),ok->{busy.remove(uid);load(p,false,ok?"의뢰를 포기했습니다.":"포기할 수 없는 의뢰입니다.");},m->{busy.remove(uid);reply(p,false,m);});}
        if(action==2)claim(p,id);
    }
    private void claim(Player p,String id){UUID uid=p.getUniqueId();var e=cache.getOrDefault(uid,List.of()).stream().filter(v->v.quest().id().equals(id)&&v.current(System.currentTimeMillis())&&v.status().equals("active")).findFirst().orElse(null);
        if(e==null||!e.complete()){reply(p,false,"의뢰 목표를 먼저 완료해 주세요.");return;}
        int rewardHouse=plugin.questHouse(p);if(e.quest().housePoints()>0&&rewardHouse<0){reply(p,false,"기숙사 소속을 먼저 정해 주세요.");return;}
        if(e.quest().money()>0&&economy()==null){reply(p,false,"보상 지급 준비가 되지 않았습니다. 관리자에게 문의해 주세요.");return;}
        if(plan(p,e.quest())==null){reply(p,false,"제출 아이템·제출 장소 또는 보상 공간을 확인해 주세요.");return;}
        // 모아 둔 진행도를 먼저 기록한다(IO 는 한 줄이라 예약보다 반드시 앞선다).
        busy.add(uid);flush(uid,true);work(uid,()->store.reserve(uid,id,e.cycle(),System.currentTimeMillis()),reserved->{
            if(reserved==null){busy.remove(uid);load(p,false,"이미 처리했거나 완료할 수 없는 의뢰입니다.");return;}
            ItemStack[] contents=current(p)&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.playerStateReady(p)?plan(p,reserved.quest()):null;
            if(contents==null){submit(uid,()->{store.finish(uid,reserved,false);return true;},v->{busy.remove(uid);if(current(p))load(p,false,"조건이 변경되어 취소했습니다.");},m->{busy.remove(uid);});return;}
            pay(p,reserved,0,rewardHouse,contents,()->{busy.remove(uid);if(current(p)){p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.45f,1.4f);load(p,false,"의뢰 보상을 받았습니다.");}},m->{busy.remove(uid);if(current(p))load(p,false,m);});
        },m->{busy.remove(uid);reply(p,false,m);});
    }
    private static String stageText(int stage){
        if((stage&QuestStore.RECORDED)==0)return "지급 단계 기록 없음 — 지급 도중 서버가 멈췄을 수 있습니다. 로그의 QUEST DELIVERED/QUEST REVIEW 줄과 유저 인벤토리·잔액을 확인하세요 (retry 불가: delivered 또는 reset)";
        var given=new ArrayList<String>();var left=new ArrayList<String>();String[] names={"아이템(제출·보상)","돈","보상 명령어","기숙사 점수"};
        for(int i=0;i<4;i++)((stage&(1<<i))!=0?given:left).add(names[i]);
        return "지급함: "+(given.isEmpty()?"없음":String.join(", ",given))+" / 남음: "+(left.isEmpty()?"없음":String.join(", ",left))+" (retry 는 남은 것만 실행)";
    }
    /**
     * 보상 지급. already 에 없는 단계만 실행한다: 아이템(제출 차감+보상 아이템) -> 돈 -> 보상 명령어 -> 기숙사 점수(토큰으로 멱등) -> 완료 기록.
     * 보상 명령어는 실패해도 경고만 남기고 완료한다. 아이템·돈 단계가 실패하면 어디까지 줬는지 기록하고 paying 으로 남긴다(관리자 retry 는 남은 단계만 실행).
     */
    private void pay(Player p,QuestStore.Entry reserved,int already,int rewardHouse,ItemStack[] contents,Runnable ok,Consumer<String> held){
        UUID uid=p.getUniqueId();var q=reserved.quest();String ref=uid+" "+q.id()+" "+reserved.cycle()+" "+reserved.token();int stage=already&15;
        try{
            // Reservation is durable BEFORE inventory or command effects. Ambiguous crashes stay in review, never automatic retry.
            if((stage&QuestStore.ITEMS)==0){p.getInventory().setStorageContents(contents);stage|=QuestStore.ITEMS;/* 인벤토리는 이미 바뀌었다: 저장(saveData)이 실패해도 retry 가 아이템을 다시 주지 않게 먼저 표시한다 */p.saveData();}
            if((stage&QuestStore.MONEY)==0){if(q.money()>0)deposit(p,q.money());stage|=QuestStore.MONEY;}
            if((stage&QuestStore.COMMANDS)==0){
                for(String command:q.commands()){
                    String line=command.replace("{player}",p.getName()).replace("{uuid}",uid.toString());
                    try{if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),line))plugin.getLogger().warning("QUEST REWARD COMMAND FAILED quest="+q.id()+" player="+p.getName()+" ("+uid+") command=["+line+"]: 등록되지 않은 명령어");}
                    catch(RuntimeException ex){plugin.getLogger().log(Level.WARNING,"QUEST REWARD COMMAND FAILED quest="+q.id()+" player="+p.getName()+" ("+uid+") command=["+line+"]",ex);}
                }
                stage|=QuestStore.COMMANDS;
            }
            p.saveData();
        }catch(Exception ex){
            int reached=stage;plugin.getLogger().log(Level.SEVERE,"QUEST REVIEW "+ref+" 지급 중단 — "+stageText(reached|QuestStore.RECORDED),ex);
            submit(uid,()->{store.stage(uid,reserved,reached|QuestStore.RECORDED);return true;},v->held.accept("보상 처리 확인이 필요합니다. 관리자에게 문의해 주세요."),m->held.accept("보상 처리 확인이 필요합니다. 관리자에게 문의해 주세요."));return;
        }
        int reached=stage;plugin.getLogger().info("QUEST DELIVERED "+ref);
        Runnable finish=()->submit(uid,()->{store.finish(uid,reserved,true);return true;},v->ok.run(),m->{
            plugin.getLogger().severe("QUEST REVIEW "+ref+" 보상은 모두 지급했으나 완료 기록에 실패했습니다. /의뢰관리 resolve "+uid+" "+q.id()+" "+reserved.cycle()+" delivered 로 닫으세요.");
            submit(uid,()->{store.stage(uid,reserved,15|QuestStore.RECORDED);return true;},v->{},x->{});held.accept("보상 기록 확인이 필요합니다. 관리자에게 문의해 주세요.");});
        if(q.housePoints()>0&&(reached&QuestStore.HOUSE)==0)plugin.questHouseReward(reserved.token(),rewardHouse,q.housePoints(),finish,m->{
            plugin.getLogger().severe("QUEST REVIEW "+ref+" 기숙사 점수 지급 실패 — "+stageText(reached|QuestStore.RECORDED));
            submit(uid,()->{store.stage(uid,reserved,reached|QuestStore.RECORDED);return true;},v->{},x->{});held.accept("기숙사 점수 지급 확인이 필요합니다. 관리자에게 문의해 주세요.");});
        else finish.run();
    }
    /** 보상 명령어의 첫 단어가 이 서버에 등록되어 있는지 확인한다. 다른 서버 전용 명령어일 수 있으므로 막지 않고 경고만 한다. */
    private List<String> commandWarnings(Collection<QuestDefinition> quests){
        var out=new ArrayList<String>();
        for(var q:quests)for(String command:q.commands()){String label=command.split(" ",2)[0];if(out.size()<10&&Bukkit.getCommandMap().getCommand(label)==null)out.add("경고: 의뢰 "+q.id()+" 의 보상 명령어 '"+label+"' 을(를) 이 서버에서 찾을 수 없습니다. 오타인지 확인하세요. (실패해도 의뢰는 완료되고 로그에 QUEST REWARD COMMAND FAILED 로 남습니다)");}
        return out;
    }
    private ItemStack[] plan(Player p,QuestDefinition q){
        ItemStack[] slots=Arrays.stream(p.getInventory().getStorageContents()).map(s->s==null?null:s.clone()).toArray(ItemStack[]::new);
        for(var g:q.objectives())if(g.type()==Type.SUBMIT){if(!g.matches(Type.SUBMIT,g.target(),server,p.getWorld().getName()))return null;ItemStack required=submissionItem(g.target());int left=g.amount();for(int i=0;i<slots.length&&left>0;i++){var s=slots[i];if(s!=null&&s.isSimilar(required)){int n=Math.min(left,s.getAmount());left-=n;s.setAmount(s.getAmount()-n);if(s.getAmount()==0)slots[i]=null;}}if(left>0)return null;}
        for(String spec:q.items()){String[] parts=spec.split(":");ItemStack reward=new ItemStack(Objects.requireNonNull(Material.matchMaterial(parts[0])));int left=Integer.parseInt(parts[1]);for(int i=0;i<slots.length&&left>0;i++)if(slots[i]!=null&&slots[i].isSimilar(reward)){int n=Math.min(left,slots[i].getMaxStackSize()-slots[i].getAmount());slots[i].setAmount(slots[i].getAmount()+n);left-=n;}for(int i=0;i<slots.length&&left>0;i++)if(slots[i]==null||slots[i].isEmpty()){int n=Math.min(left,reward.getMaxStackSize());slots[i]=reward.clone();slots[i].setAmount(n);left-=n;}if(left>0)return null;}
        return slots;
    }
    static ItemStack submissionItem(String target){ItemStack item=target.startsWith("stack:")?ItemStack.deserializeBytes(Base64.getDecoder().decode(target.substring(6))):new ItemStack(Objects.requireNonNull(Material.matchMaterial(target),"제출 아이템 종류 오류"));if(item.isEmpty())throw new IllegalArgumentException("빈 아이템");item.setAmount(1);return item;}
    private record Journal(List<QuestStore.Entry> entries,Set<String> completed){}
    private void load(Player p,boolean open,String message){UUID id=p.getUniqueId();flush(id,true);work(()->new Journal(store.entries(id),store.completed(id)),v->{if(current(p)){var later=deltas.get(id);if(later!=null)for(var e:later.counts.entrySet())apply(v.entries(),e.getKey().type(),e.getKey().target(),e.getKey().world(),e.getValue(),System.currentTimeMillis());cache.put(id,v.entries());completed.put(id,v.completed());if(open)viewers.put(id,System.currentTimeMillis()+15000);reply(p,open,message);}},m->{if(current(p))p.sendMessage(m);});}
    private void reply(Player p,boolean open,String message){if(!current(p))return;if(!p.getListeningPluginChannels().contains(QuestProtocol.RESPONSE)){if(open)p.sendMessage("의뢰 게시판 모드를 업데이트해 주세요.");else if(!message.isEmpty())p.sendMessage(message);return;}
        long now=System.currentTimeMillis();var all=new TreeMap<String,QuestDefinition>();catalog.values().stream().filter(q->q.available(now)&&QuestStore.unlocked(q,completed.getOrDefault(p.getUniqueId(),Set.of()))&&(q.permission().isEmpty()||p.hasPermission(q.permission()))).forEach(q->all.put(q.id(),q));var states=new HashMap<String,QuestStore.Entry>();
        for(var e:cache.getOrDefault(p.getUniqueId(),List.of()))if((e.current(now)&&(e.status().equals("active")||all.containsKey(e.quest().id())))||e.status().equals("paying")){states.put(e.quest().id(),e);if(e.status().equals("active")||e.status().equals("paying"))all.put(e.quest().id(),e.quest());}
        int tab=tabs.getOrDefault(p.getUniqueId(),0);
        var list=all.values().stream().filter(q->tab==tabFor(displayState(q,states.get(q.id())))).sorted(Comparator.comparing(QuestDefinition::main).reversed().thenComparing(QuestDefinition::id)).toList();int page=Math.clamp(pages.getOrDefault(p.getUniqueId(),0),0,Math.max(0,(list.size()-1)/6));pages.put(p.getUniqueId(),page);var cards=new ArrayList<QuestProtocol.Card>();
        for(var q:list.subList(Math.min(page*6,list.size()),Math.min(page*6+6,list.size()))){var e=states.get(q.id());String state=displayState(q,e);var goals=new ArrayList<QuestProtocol.Goal>();for(int i=0;i<q.objectives().size();i++){var g=q.objectives().get(i);int count=e==null||!state.equals("active")?0:e.progress()[i];if(g.type()==Type.SUBMIT){count=0;var required=submissionItem(g.target());for(var item:p.getInventory().getStorageContents())if(item!=null&&item.isSimilar(required))count+=item.getAmount();}if(state.equals("claimed"))count=g.amount();goals.add(new QuestProtocol.Goal(g.label(),Math.min(g.amount(),count),g.amount()));}cards.add(new QuestProtocol.Card(q.id(),q.title(),q.description(),rewardText(q),state,goals,q.rank(),e==null?0:e.completions(),q.completionLimit(),q.main()));}
        try{p.sendPluginMessage(plugin,QuestProtocol.RESPONSE,QuestProtocol.encode(new QuestProtocol.Response(open,page,Math.min(list.size(),QuestProtocol.MAX_TOTAL),message,cards,Math.max(0,3-QuestStore.activeSubquests(cache.getOrDefault(p.getUniqueId(),List.of()),now)))));}catch(IllegalArgumentException ex){p.sendMessage("의뢰 게시판 데이터가 너무 큽니다. 관리자에게 알려 주세요.");plugin.getLogger().warning("Quest board encode: "+ex.getMessage());}
    }

    static long parseTime(String value){if(value.isBlank()||value.equals("0"))return 0;return java.time.LocalDateTime.parse(value.trim().replace(' ','T')).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();}
    static String timeText(long value){return value==0?"":java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDateTime().withNano(0).toString().replace('T',' ');}
    static String displayState(QuestDefinition q,QuestStore.Entry e){if(e==null)return "available";if(Set.of("active","paying").contains(e.status()))return e.status();return e.completions()>=q.completionLimit()?"claimed":"available";}
    static int tabFor(String state){return switch(state){case "claimed"->1;case "active","paying"->2;default->0;};}
    private static String rewardText(QuestDefinition q){var labels=new ArrayList<String>();if(q.housePoints()>0)labels.add("기숙사 점수  +"+q.housePoints());if(q.money()>0)labels.add("돈  +"+new java.text.DecimalFormat("#,##0.##").format(q.money()));if(!q.items().isEmpty())labels.add("아이템  "+String.join(", ",q.items()).replace(":"," × "));if(!q.rewardLabel().isBlank())labels.add(q.rewardLabel());return String.join("\n",labels);}
    private record Economy(Object provider,Class<?> type){}
    @SuppressWarnings({"rawtypes","unchecked"}) private Economy economy(){for(Class<?> type:Bukkit.getServicesManager().getKnownServices())if(type.getName().equals("net.milkbowl.vault.economy.Economy")){var r=Bukkit.getServicesManager().getRegistration((Class)type);if(r!=null)return new Economy(r.getProvider(),type);}return null;}
    private void deposit(Player p,double money)throws Exception{var e=economy();if(e==null)throw new IllegalStateException("Vault economy unavailable");Object response=e.type().getMethod("depositPlayer",OfflinePlayer.class,double.class).invoke(e.provider(),p,money);if(!Boolean.TRUE.equals(response.getClass().getMethod("transactionSuccess").invoke(response)))throw new IllegalStateException("Vault deposit rejected");}
    private final Map<UUID,Long> adminLimits=new HashMap<>();private final Set<UUID> adminBusy=new HashSet<>();
    private void adminPacket(Player p,byte[] bytes){
        if(!p.hasPermission("magiccodex.quests.admin"))return;long now=System.currentTimeMillis();UUID uid=p.getUniqueId();if(now<adminLimits.getOrDefault(uid,0L)||adminBusy.contains(uid))return;adminLimits.put(uid,now+300);
        try{var r=QuestAdminProtocol.request(bytes);if(r.action()==0){adminReply(p,"",null);return;}if(r.action()==1){adminReply(p,"",r.id());return;}
            if(r.id().isBlank())throw new IllegalArgumentException("의뢰 ID를 입력하세요");var q=r.action()==2?QuestAdminDocument.parse(r.id(),r.fields()):null;
            // Bound the return document before committing, not after a successful save.
            if(q!=null)QuestAdminProtocol.encode(new QuestAdminProtocol.Response("",List.of(),q.id(),"",QuestAdminDocument.fields(q)));
            adminBusy.add(uid);work(()->store.edit(r.id(),r.revision(),q),ok->{adminBusy.remove(uid);if(!current(p))return;if(!ok){adminReply(p,"다른 곳에서 수정했습니다. 다시 열어 확인해 주세요.",null);return;}try{saveDraft(r.id(),q);}catch(Exception ex){plugin.getLogger().warning("Quest draft export: "+ex.getClass().getSimpleName());}adminReply(p,q==null?"의뢰를 삭제했습니다.":"저장하여 게시판에 반영했습니다.",q==null?null:q.id());if(q!=null)commandWarnings(List.of(q)).forEach(p::sendMessage);},m->{adminBusy.remove(uid);if(current(p))adminReply(p,m,null);});
        }catch(Exception e){adminReply(p,"입력 확인: "+(e.getMessage()==null?"형식 오류":e.getMessage()),null);}
    }
    private void adminReply(Player p,String message,String id){if(!p.hasPermission("magiccodex.quests.admin")||!p.getListeningPluginChannels().contains(QuestAdminProtocol.RESPONSE))return;
        work(store::catalog,map->{catalog=map;if(!current(p)||!p.hasPermission("magiccodex.quests.admin"))return;var rows=map.values().stream().sorted(Comparator.comparing(QuestDefinition::id)).limit(QuestAdminProtocol.MAX_ENTRIES).map(q->new QuestAdminProtocol.Summary(q.id(),q.title(),q.rank(),!q.enabled()?"비공개":q.opensAt()>System.currentTimeMillis()?"예약 "+timeText(q.opensAt()):"공개")).toList();var q=id==null?null:map.get(id);String note=map.size()>rows.size()&&message.isEmpty()?"목록은 앞의 "+rows.size()+"개만 표시합니다. 나머지는 quests.yml에서 수정해 주세요.":message;try{p.sendPluginMessage(plugin,QuestAdminProtocol.RESPONSE,QuestAdminProtocol.encode(new QuestAdminProtocol.Response(note,rows,q==null?"":q.id(),q==null?"":QuestStore.fingerprint(q),q==null?Map.of():QuestAdminDocument.fields(q))));}catch(IllegalArgumentException ex){p.sendMessage("편집 문서가 너무 큽니다. quests.yml에서 수정해 주세요.");}},p::sendMessage);
    }
    private void saveDraft(String id,QuestDefinition q)throws Exception{var y=new YamlConfiguration();y.load(file);String path="quests."+id;y.set(path,null);if(q!=null){var f=QuestAdminDocument.fields(q);for(var e:f.entrySet())if(!e.getKey().startsWith("goal")){Object v=switch(e.getKey()){case "daily","enabled","main"->Boolean.parseBoolean(e.getValue());case "requires"->q.requires();case "completion-limit","reward.house-points"->Integer.parseInt(e.getValue());case "reward.money"->Double.parseDouble(e.getValue());case "reward.items"->q.items();case "reward.commands"->q.commands();default->e.getValue();};y.set(path+"."+e.getKey(),v);}y.set(path+".objectives",q.objectives().stream().map(g->Map.of("type",g.type().name(),"target",g.target(),"amount",g.amount(),"label",g.label(),"server",g.server(),"world",g.world())).toList());}y.save(file);}
    private void release(CommandSender sender,String[] a){
        boolean schedule=a[0].equals("schedule");long spacing=schedule?Long.parseLong(a[1]):0;if(schedule&&(a.length<3||spacing<1||spacing>31536000))throw new IllegalArgumentException("schedule <간격 초> <의뢰ID...>");
        var ids=schedule?List.of(Arrays.copyOfRange(a,2,a.length)):List.of(a[1]);long start=System.currentTimeMillis();boolean enabled=!a[0].equals("close");
        work(()->store.release(ids,start,spacing*1000,enabled),map->{catalog=map;for(String id:ids)try{saveDraft(id,map.get(id));}catch(Exception ex){plugin.getLogger().warning("Quest draft export failed");}sender.sendMessage(schedule?"지정 순서대로 일정 간격 공개를 예약했습니다.":enabled?"의뢰를 공개했습니다.":"신규 수락을 닫았습니다.");},sender::sendMessage);
    }
    private boolean current(Player p){return p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
    private interface Job<T>{T run()throws Exception;}
    /** 메인 스레드 후속 처리는 여기를 거친다: 예외가 나도 busy 가 풀린다(보상 지급 기록은 paying 그대로 남아 관리자 audit 에 보인다). */
    private void guard(UUID owner,Runnable r){try{r.run();}catch(Throwable t){plugin.getLogger().log(Level.SEVERE,"Quest continuation failed"+(owner==null?"":" for "+owner),t);if(owner!=null)busy.remove(owner);}}
    private <T>void work(Job<T> job,Consumer<T> done,Consumer<String> fail){work(null,job,done,fail);}
    /** 새 요청: 밀려 있으면 아무 것도 바꾸지 않고 거절한다. */
    private <T>void work(UUID owner,Job<T> job,Consumer<T> done,Consumer<String> fail){if(closing||pending>=512){fail.accept("의뢰 처리가 밀리고 있습니다. 잠시 후 다시 시도해 주세요.");return;}submit(owner,job,done,fail);}
    private <T>void submit(Job<T> job,Consumer<T> done,Consumer<String> fail){submit(null,job,done,fail);}
    /** 이미 시작한 처리의 후속 기록과 진행도 묶음 기록: 대기 수 한도로 거절하지 않는다. */
    private <T>void submit(UUID owner,Job<T> job,Consumer<T> done,Consumer<String> fail){
        if(closing){fail.accept("서버가 종료 중입니다.");return;}
        pending++;io.execute(()->{
            T value;try{value=job.run();}catch(Exception ex){plugin.getLogger().warning("Quest storage: "+ex);String m=ex instanceof IllegalArgumentException||ex instanceof IllegalStateException?String.valueOf(ex.getMessage()):"의뢰 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.";main(()->{pending--;guard(owner,()->fail.accept(m));});return;}
            main(()->{pending--;guard(owner,()->done.accept(value));});});
    }
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(c.getName().equals("의뢰")){if(s instanceof Player p)request(p,0,"",0,true);return true;}
        if(!s.hasPermission("magiccodex.quests.admin"))return true;
        try{
            if(a.length==0&&s instanceof Player p){adminReply(p,"",null);return true;}

            if(a.length>=2&&Set.of("open","close","schedule").contains(a[0])){release(s,a);return true;}
            if(a.length==1&&a[0].equals("pull")){work(store::catalog,v->{try{var y=new YamlConfiguration();y.load(file);java.nio.file.Files.copy(file.toPath(),file.toPath().resolveSibling("quests-before-pull-"+System.currentTimeMillis()+".yml"));y.set("quests",null);for(var q:v.values()){String path="quests."+q.id();y.set(path+".rank",q.rank());y.set(path+".main",q.main());y.set(path+".requires",q.requires());y.set(path+".completion-limit",q.completionLimit());y.set(path+".opens-at",timeText(q.opensAt()));y.set(path+".reward.money",q.money());y.set(path+".reward.house-points",q.housePoints());y.set(path+".title",q.title());y.set(path+".description",q.description());y.set(path+".permission",q.permission());y.set(path+".enabled",q.enabled());y.set(path+".daily",q.daily());y.set(path+".objectives",q.objectives().stream().map(g->Map.of("type",g.type().name(),"target",g.target(),"amount",g.amount(),"label",g.label(),"server",g.server(),"world",g.world())).toList());y.set(path+".reward.label",q.rewardLabel());y.set(path+".reward.items",q.items());y.set(path+".reward.commands",q.commands());}y.save(file);s.sendMessage("공용 의뢰를 로컬 초안에 가져왔습니다. 이전 파일은 백업했습니다.");}catch(Exception ex){s.sendMessage("의뢰 초안 저장 실패");}},s::sendMessage);return true;}
            if(a.length==4&&a[0].equals("submititem")&&s instanceof Player p){String id=a[1];if(!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("의뢰 ID");var y=new YamlConfiguration();y.load(file);String path="quests."+id+".objectives";var goals=new ArrayList<Map<?,?>>(y.getMapList(path));int index=Integer.parseInt(a[2])-1,amount=Integer.parseInt(a[3]);if(index<0||index>=goals.size()||amount<1||amount>100000)throw new IllegalArgumentException("목표 번호/수량");var item=p.getInventory().getItemInMainHand().clone();if(item.isEmpty())throw new IllegalArgumentException("아이템을 주 손에 들어 주세요");item.setAmount(1);String target="stack:"+Base64.getEncoder().encodeToString(item.serializeAsBytes());if(target.length()>16000)throw new IllegalArgumentException("아이템 데이터가 너무 큽니다");goals.set(index,Map.of("type","SUBMIT","target",target,"amount",amount,"label","지정 아이템 제출"));y.set(path,goals);y.save(file);s.sendMessage("주 손 아이템과 동일한 데이터의 제출 조건 저장. /의뢰관리 reload 로 반영하세요.");return true;}
            if(a.length==1&&a[0].equals("reload")){var next=read();work(()->{store.publish(next);return store.catalog();},v->{catalog=v;s.sendMessage("공용 의뢰 설정 반영 완료. 수락한 의뢰는 기존 조건을 유지합니다.");commandWarnings(v.values()).forEach(s::sendMessage);},s::sendMessage);return true;}
            if(a.length>=3&&a[0].equals("event")){Player p=Bukkit.getPlayerExact(a[1]);if(p==null)throw new IllegalArgumentException("온라인 플레이어 없음");event(p,Type.EVENT,a[2],a.length>3?Integer.parseInt(a[3]):1);return true;}
            if(a.length==2&&a[0].equals("audit")){UUID id=target(a[1]);work(()->store.entries(id),v->{var rows=v.stream().filter(e->e.status().equals("paying")).toList();s.sendMessage("보상 검토 대기 "+rows.size()+"건 ("+id+")"+(rows.isEmpty()?"":" — /의뢰관리 resolve "+a[1]+" <id> <cycle> delivered|retry|reset"));for(var e:rows){s.sendMessage(e.quest().id()+" "+e.cycle()+" "+e.token()+" | "+e.quest().title());s.sendMessage("  → "+stageText(e.stage()));}},s::sendMessage);return true;}
            if(a.length==5&&a[0].equals("resolve")){
                UUID id=target(a[1]);String action=a[4],admin=s.getName();
                if(!Set.of("delivered","retry","reset").contains(action))throw new IllegalArgumentException("delivered|retry|reset 중 선택 — delivered: 지급 완료로 닫기 / retry: 기록상 아직 주지 않은 보상만 지급 / reset: 수령 전으로 되돌림(유저가 전부 다시 받을 수 있음)");
                if(busy.contains(id)){s.sendMessage("이 유저의 의뢰를 지금 처리하는 중입니다. 잠시 뒤 다시 시도하세요.");return true;}
                if(!action.equals("retry")){
                    work(()->{store.resolve(id,a[2],a[3],action.equals("delivered"));return true;},v->{
                        plugin.getLogger().warning("QUEST ADMIN admin="+admin+" player="+id+" quest="+a[2]+" cycle="+a[3]+" action="+action);
                        s.sendMessage(action.equals("delivered")?"지급 완료로 닫았습니다. (추가 지급 없음)":"수령 전 상태로 되돌렸습니다. 유저가 보상을 처음부터 다시 받을 수 있습니다.");
                        Player online=Bukkit.getPlayer(id);if(online!=null&&current(online))load(online,false,"");
                    },s::sendMessage);return true;
                }
                // retry: 기록(stage)에 없는 단계만 실행한다. 유저가 이 서버에 있어야 한다.
                Player t=Bukkit.getPlayer(id);if(t==null||!current(t)||t.isDead()||t.getGameMode()==GameMode.SPECTATOR||!plugin.playerStateReady(t))throw new IllegalArgumentException("retry 는 유저가 이 서버에 접속해 있고 보상을 받을 수 있는 상태일 때만 할 수 있습니다.");
                busy.add(id);work(id,()->store.paying(id,a[2],a[3]),e->{
                    if(e==null){busy.remove(id);s.sendMessage("검토 대기 기록 없음");return;}
                    if((e.stage()&QuestStore.RECORDED)==0){busy.remove(id);s.sendMessage("지급 단계 기록이 없어 retry 할 수 없습니다(지급 도중 서버가 멈춘 기록). 유저가 받은 것을 확인한 뒤 delivered 또는 reset 을 쓰세요.");return;}
                    if(!current(t)||t.isDead()||!plugin.playerStateReady(t)){busy.remove(id);s.sendMessage("유저 상태가 바뀌었습니다. 다시 시도하세요.");return;}
                    int house=plugin.questHouse(t);
                    if(e.quest().housePoints()>0&&(e.stage()&QuestStore.HOUSE)==0&&house<0){busy.remove(id);s.sendMessage("유저의 기숙사 소속이 없어 기숙사 점수를 줄 수 없습니다.");return;}
                    if(e.quest().money()>0&&(e.stage()&QuestStore.MONEY)==0&&economy()==null){busy.remove(id);s.sendMessage("경제 서비스가 연결되지 않아 돈을 줄 수 없습니다.");return;}
                    ItemStack[] contents=(e.stage()&QuestStore.ITEMS)!=0?null:plan(t,e.quest());
                    if((e.stage()&QuestStore.ITEMS)==0&&contents==null){busy.remove(id);s.sendMessage("유저의 제출 아이템·제출 장소 또는 인벤토리 공간이 맞지 않아 아이템 단계를 실행할 수 없습니다.");return;}
                    plugin.getLogger().warning("QUEST ADMIN admin="+admin+" player="+id+" quest="+a[2]+" cycle="+a[3]+" action=retry token="+e.token()+" — "+stageText(e.stage()));
                    pay(t,e,e.stage(),house,contents,()->{busy.remove(id);s.sendMessage("남은 보상을 지급하고 완료 처리했습니다.");if(current(t))load(t,false,"의뢰 보상을 받았습니다.");},m->{busy.remove(id);s.sendMessage("다시 멈췄습니다. audit 로 확인하세요: "+m);});
                },m->{busy.remove(id);s.sendMessage(m);});return true;
            }
            if(a.length>=2&&Set.of("create","set","goal","rewarditem","rewardcommand","delete").contains(a[0])){edit(s,a);return true;}
        }catch(Exception e){s.sendMessage("설정 오류: "+e.getMessage());return true;}
        s.sendMessage("/의뢰관리 create <id> | set <id> <title|description|daily|enabled|permission|reward.label> <내용>\n/의뢰관리 goal <id> <번호1~6> <KILL|MYTHIC_KILL|SUBMIT|NPC|BIOME|EVENT> <대상> <수량> <표시문구>\n/의뢰관리 rewarditem <id> <MATERIAL:수량> | rewardcommand <id> <명령어>\n/의뢰관리 delete <id> | reload | event <플레이어> <이벤트ID> [수량]\n/의뢰관리 audit <유저|UUID> | resolve <유저|UUID> <id> <cycle> <delivered|retry|reset>");return true;
    }
    private UUID target(String input){try{return UUID.fromString(input);}catch(IllegalArgumentException ignored){}Player online=plugin.names().resolve(input);if(online==null)throw new IllegalArgumentException("접속 중인 유저의 닉네임·영문 계정명 또는 UUID를 입력하세요.");return online.getUniqueId();}
    private void edit(CommandSender s,String[] a)throws Exception{
        String id=a[1];if(!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("ID는 영문 소문자/숫자/_/-");
        var y=new YamlConfiguration();y.load(file);String path="quests."+id;
        if(a[0].equals("create")){if(y.contains(path))throw new IllegalArgumentException("이미 있는 ID");y.set(path+".title",id);y.set(path+".description","");y.set(path+".enabled",false);y.set(path+".daily",true);y.set(path+".objectives",List.of(Map.of("type","KILL","target","ZOMBIE","amount",10,"label","좀비 처치")));y.set(path+".reward.label","에메랄드 3개");y.set(path+".reward.items",List.of("EMERALD:3"));}
        else {if(!y.contains(path))throw new IllegalArgumentException("없는 의뢰");switch(a[0]){
            case "delete"->y.set(path,null);
            case "set"->{if(a.length<4||!Set.of("title","description","daily","enabled","permission","reward.label","rank","completion-limit","opens-at","reward.money","reward.house-points").contains(a[2]))throw new IllegalArgumentException("수정 항목 확인");String v=String.join(" ",Arrays.copyOfRange(a,3,a.length));if(Set.of("daily","enabled").contains(a[2])&&!Set.of("true","false").contains(v))throw new IllegalArgumentException("true 또는 false");Object value=switch(a[2]){case "daily","enabled"->Boolean.parseBoolean(v);case "completion-limit","reward.house-points"->Integer.parseInt(v);case "reward.money"->Double.parseDouble(v);default->v;};y.set(path+"."+a[2],value);}
            case "goal"->{if(a.length<7)throw new IllegalArgumentException("goal 인자 확인");int index=Integer.parseInt(a[2])-1;var goals=new ArrayList<Map<?,?>>(y.getMapList(path+".objectives"));if(index<0||index>goals.size()||index>=6)throw new IllegalArgumentException("목표 번호");var g=Map.of("type",a[3].toUpperCase(Locale.ROOT),"target",a[4],"amount",Integer.parseInt(a[5]),"label",String.join(" ",Arrays.copyOfRange(a,6,a.length)));if(index==goals.size())goals.add(g);else goals.set(index,g);y.set(path+".objectives",goals);}
            case "rewarditem","rewardcommand"->{if(a.length<3)throw new IllegalArgumentException();y.set(path+".reward."+(a[0].equals("rewarditem")?"items":"commands"),List.of(String.join(" ",Arrays.copyOfRange(a,2,a.length))));}
        }}
        // Edits are drafts. Explicit reload validates and publishes the complete catalog atomically.
        y.save(file);s.sendMessage("quests.yml 초안 저장 완료. /의뢰관리 reload 로 검증·공용 반영하세요.");
        if(a[0].equals("rewardcommand")){String label=a[2].startsWith("/")?a[2].substring(1):a[2];if(Bukkit.getCommandMap().getCommand(label)==null)s.sendMessage("경고: 보상 명령어 '"+a[2]+"' 을(를) 이 서버에서 찾을 수 없습니다. 오타인지 확인하세요. (명령어는 / 없이 적습니다)");}
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){if(!s.hasPermission("magiccodex.quests.admin"))return List.of();if(a.length==1)return List.of("create","set","goal","rewarditem","rewardcommand","delete","reload","pull","open","close","schedule","event","audit","resolve").stream().filter(v->v.startsWith(a[0])).toList();if(a.length==2)return Set.of("audit","resolve","event").contains(a[0])?AdminCommandRules.filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),a[1]):AdminCommandRules.filter(catalog.keySet(),a[1]);if(a.length==3&&a[0].equals("resolve"))return AdminCommandRules.filter(catalog.keySet(),a[2]);if(a.length==5&&a[0].equals("resolve"))return AdminCommandRules.filter(List.of("delivered","retry","reset"),a[4]);if(a.length==3&&a[0].equals("set"))return List.of("title","description","daily","enabled","permission","reward.label","rank","completion-limit","opens-at","reward.money","reward.house-points");if(a.length==4&&a[0].equals("goal"))return Arrays.stream(Type.values()).map(Enum::name).toList();return List.of();}
    @Override public void close(){
        // 모아 둔 진행도를 마지막으로 기록한다. (closing 을 올리기 전에 IO 대기열에 넣고, 아래에서 끝날 때까지 기다린다.)
        for(var entry:deltas.entrySet()){
            UUID id=entry.getKey();String sid=server;long since=entry.getValue().since;
            var list=entry.getValue().counts.entrySet().stream().map(e->new QuestStore.Delta(e.getKey().type(),e.getKey().target(),e.getKey().world(),e.getValue())).toList();
            try{io.execute(()->{try{store.advanceAll(id,list,sid,since);}catch(Exception e){plugin.getLogger().severe("Quest progress not saved at shutdown for "+id+": "+list+" ("+e+")");}});}catch(RejectedExecutionException ignored){}
        }
        deltas.clear();closing=true;io.shutdown();try{if(!io.awaitTermination(15,TimeUnit.SECONDS)){plugin.getLogger().severe("Quest worker shutdown timeout; pending payout records require review");return;}store.close();}catch(Exception e){plugin.getLogger().warning("Quest close: "+e);}HandlerList.unregisterAll(this);}
}
