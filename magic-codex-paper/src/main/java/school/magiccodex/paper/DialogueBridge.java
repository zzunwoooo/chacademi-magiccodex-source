package school.magiccodex.paper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.*;

/**
 * Main-thread sessions, one bounded IO queue, cached immutable catalogs. No per-tick DB polling.
 *
 * 선택지의 외부 동작(quest/event/command/custom)은 진행 상태(story/flag)와 한 트랜잭션으로 기록한 뒤 실행한다.
 * 기록 상태와 자동/관리자 처리 규칙은 DialogueStore 주석 참고. 요약:
 *  - 유저가 나갔거나 서버가 꺼져 실행하지 못한 동작(queued)은 다음 접속(준비된 뒤) 또는 다음 대화 열기 때 이어서 실행한다.
 *  - 실패한 동작(review)·실행 여부를 알 수 없는 동작(running, 옛 pending)만 관리자 확인으로 가며, 그 대화만 막는다.
 */
final class DialogueBridge implements Listener,CommandExecutor,TabCompleter,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-dialogue-io");t.setDaemon(true);return t;});
    private final DialogueStore store;private final Path directory;
    private Map<String,DialogueDefinition> catalog=Map.of();private Map<String,List<String>> bindings=Map.of();
    /** 대화 ID -> 저장된 revision. 카탈로그를 읽을 때 DB 값으로 함께 채운다(클릭마다 YAML 인코딩+SHA-256 을 하지 않는다). */
    private Map<String,String> revisions=Map.of();private String signature="";
    /** 실행 중인 외부 동작 묶음 (token -> 묶음). remaining 은 "아직 실행하지 않은 동작"이며 메인 스레드에서만 바뀐다. */
    private final Map<String,Run> runs=new HashMap<>();
    private static final class Run {
        /** armed: DB 기록이 running 인 것이 확인됨. IO 스레드(queued->running 성공 시)에서도 쓰므로 volatile. */
        final UUID player;final String key,token;final ArrayList<String> remaining;volatile boolean armed;boolean eventInFlight;
        Run(UUID player,String key,String token,List<String> actions,boolean armed){this.player=player;this.key=key;this.token=token;remaining=new ArrayList<>(actions);this.armed=armed;}
    }
    /** running 으로 기록을 요청했지만 아직 결과를 받지 못한 선택 (유저 -> effect_key). 종료 시 실행 전인 것이 확실하므로 queued 로 되돌린다. */
    private final Map<UUID,String> arming=new HashMap<>();
    /** 관리자가 방금 audit 로 본 토큰 (탭 완성용). */
    private final Map<String,List<String>> listed=new HashMap<>();
    private static final String REVIEW="이 대화의 이전 실행 기록은 관리자 확인이 필요합니다. 관리자에게 문의해 주세요.";
    private final Map<UUID,Session> sessions=new HashMap<>();private final Map<UUID,Long> limits=new HashMap<>();private final Set<UUID> busy=new HashSet<>();
    private final Map<String,BiConsumer<Player,String>> handlers=new HashMap<>();
    private final Map<String,BiPredicate<Player,String>> conditions=new HashMap<>();
    void registerCondition(String id,BiPredicate<Player,String> predicate){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");DialogueDefinition.id(id);conditions.put(id,Objects.requireNonNull(predicate));}
    private volatile boolean closing;private boolean refreshing;private int pending;
    /** 외부 플러그인(ChacaNPC)이 클릭을 직접 처리하는 Citizens NPC id. 그 NPC는 아래 Citizens 처리에서 제외한다. */
    private volatile java.util.function.IntPredicate claimed=id->false;
    void claim(java.util.function.IntPredicate predicate){claimed=predicate==null?id->false:predicate;}
    boolean claimed(int id){try{return claimed.test(id);}catch(RuntimeException e){return false;}}
    private static final class Session {
        final String token=UUID.randomUUID().toString();final DialogueDefinition definition;final boolean preview;final Entity anchor;boolean journal;
        /** 이 세션을 연 시점의 공개본 revision (미리보기·기록 보기는 빈 값). */
        final String revision;
        DialogueStore.State state;String node;int sequence;long expires=System.currentTimeMillis()+300000;
        Session(DialogueDefinition d,DialogueStore.State s,boolean preview,Entity anchor,String revision){definition=d;state=s;this.preview=preview;this.anchor=anchor;this.revision=revision;node=d.start();}
    }
    DialogueBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;directory=plugin.getDataFolder().toPath().resolve("dialogues");Files.createDirectories(directory);
        if(!Files.exists(directory.resolve("elena.yml")))plugin.saveResource("dialogues/elena.yml",false);
        if(!Files.exists(directory.resolve("arden.yml")))plugin.saveResource("dialogues/arden.yml",false);
        var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));
        try{store=io.submit(()->new DialogueStore(settings,plugin.getDataFolder().toPath().resolve("dialogue.db"))).get(15,TimeUnit.SECONDS);
            var initial=io.submit(()->{var map=store.catalog();if(map.isEmpty()){for(String id:List.of("elena","arden"))store.edit(id,"",read(id));}return store.snapshot(null);}).get(15,TimeUnit.SECONDS);install(initial);
        }catch(Exception e){io.shutdownNow();throw e;}
        for(String channel:List.of(DialogueProtocol.REQUEST,DialogueAdminProtocol.REQUEST))Bukkit.getMessenger().registerIncomingPluginChannel(plugin,channel,this);
        for(String channel:List.of(DialogueProtocol.RESPONSE,DialogueAdminProtocol.RESPONSE))Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,channel);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String c:List.of("대화","대화관리","메인퀘스트")){plugin.getCommand(c).setExecutor(this);plugin.getCommand(c).setTabCompleter(this);}
        citizens();
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            sessions.entrySet().removeIf(e->System.currentTimeMillis()>e.getValue().expires);
            // id=revision 목록만 비교해서, 바뀐 때에만 정의 전체를 다시 읽고 푼다.
            if(!refreshing&&!closing){refreshing=true;String known=signature;work(()->store.snapshot(known),v->{refreshing=false;if(v!=null)install(v);},m->refreshing=false);}
        },200,200);
        for(Player p:Bukkit.getOnlinePlayers())resumeLater(p,0);
    }
    void register(String id,BiConsumer<Player,String> handler){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");DialogueDefinition.id(id);handlers.put(id,Objects.requireNonNull(handler));}
    private void install(DialogueStore.Snapshot snapshot){var definitions=snapshot.definitions();catalog=definitions;revisions=snapshot.revisions();signature=snapshot.signature();var index=new HashMap<String,List<String>>();definitions.values().stream().sorted(Comparator.comparing(DialogueDefinition::id)).filter(DialogueDefinition::enabled).forEach(d->d.npcs().forEach(n->index.computeIfAbsent(n,k->new ArrayList<>()).add(d.id())));bindings=Map.copyOf(index);}
    private DialogueDefinition read(String id)throws Exception{DialogueDefinition.id(id);return DialogueDefinition.decode(id,Files.readString(directory.resolve(id+".yml")));}
    private void export(DialogueDefinition d)throws Exception{Path file=directory.resolve(d.id()+".yml"),tmp=directory.resolve(d.id()+".yml.tmp");if(Files.exists(file))Files.copy(file,directory.resolve(d.id()+".yml.bak"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(tmp,d.encode());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
    @SuppressWarnings("unchecked") private void citizens(){var dep=Bukkit.getPluginManager().getPlugin("Citizens");if(dep==null)return;try{Class<? extends Event> type=(Class<? extends Event>)Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent",true,dep.getClass().getClassLoader());Bukkit.getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(l,event)->{try{Player p=(Player)event.getClass().getMethod("getClicker").invoke(event);Object npc=event.getClass().getMethod("getNPC").invoke(event);Entity e=(Entity)npc.getClass().getMethod("getEntity").invoke(npc);int nid=((Number)npc.getClass().getMethod("getId").invoke(npc)).intValue();if(claimed(nid))return;String id="citizens:"+nid;bound(p,id,e);}catch(Exception ex){plugin.getLogger().warning("Dialogue NPC hook: "+ex.getClass().getSimpleName());}},plugin,true);}catch(Exception e){plugin.getLogger().warning("Dialogue Citizens hook unavailable: "+e.getClass().getSimpleName());}}
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEntityEvent e){if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||e.getRightClicked().hasMetadata("NPC"))return;for(String tag:e.getRightClicked().getScoreboardTags())if(bindings.containsKey("tag:"+tag)){bound(e.getPlayer(),"tag:"+tag,e.getRightClicked());break;}}
    private void bound(Player p,String key,Entity anchor){if(!near(p,anchor))return;for(String id:bindings.getOrDefault(key,List.of())){var d=catalog.get(id);if(d!=null&&(d.permission().isEmpty()||p.hasPermission(d.permission()))){open(p,d,false,anchor);return;}}}
    @EventHandler public void quit(PlayerQuitEvent e){listed.remove(e.getPlayer().getName());sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());/* busy remains until queued work finishes */}
    private boolean ready(Player p){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.playerStateReady(p);}
    private boolean current(Player p){return p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
    private boolean near(Player p,Entity e){return e==null||(e.isValid()&&p.getWorld().equals(e.getWorld())&&p.getLocation().distanceSquared(e.getLocation())<=64);}
    private boolean gate(Player p){UUID id=p.getUniqueId();long now=System.currentTimeMillis();if(closing||busy.contains(id)||now<limits.getOrDefault(id,0L))return false;limits.put(id,now+150);return true;}
    private void open(Player p,DialogueDefinition d,boolean preview,Entity anchor){
        if(!p.hasPermission("magiccodex.dialogue")||!ready(p)||!gate(p))return;
        if(!p.getListeningPluginChannels().contains(DialogueProtocol.RESPONSE)){p.sendMessage("대화 모드를 업데이트해 주세요.");return;}
        if(!preview&&(!d.enabled()||(!d.permission().isEmpty()&&!p.hasPermission(d.permission()))))return;
        UUID id=p.getUniqueId();busy.add(id);sessions.remove(id);String revision=preview?"":revisions.getOrDefault(d.id(),"");
        work(id,()->new Opening(store.state(id),preview?List.<DialogueStore.Effect>of():store.effects(id)),o->{
            var blocked=new HashMap<String,String>();
            // 이전에 끝내지 못한 동작을 먼저 이어서 실행한다. 그래도 남은 것이 이 대화의 것이면 이 대화만 막는다.
            resume(p,o.effects(),0,blocked,()->{
                busy.remove(id);if(!current(p)||!ready(p)||!near(p,anchor))return;
                if(blocked.containsKey(d.id())){p.sendMessage(blocked.get(d.id()));return;}
                var s=new Session(d,o.state(),preview,anchor,revision);if(!allowed(p,s,d.nodes().get(s.node).conditions())){p.sendMessage("아직 이 대화를 시작할 수 없습니다.");return;}sessions.put(id,s);send(p,s,"");
            });
        },m->{busy.remove(id);if(current(p))p.sendMessage(m);});
    }
    private record Opening(DialogueStore.State state,List<DialogueStore.Effect> effects){}
    /**
     * 외부 진입점(ChacaNPC)용: 이 Citizens NPC에 묶인 고정 대화 중 지금 열 수 있는 첫 대화를 연다.
     * 권한·공개 여부·시작 노드 조건·진행 상태 검사는 open()과 같다. 처리 중(busy)이면 NONE이 아니라 BUSY.
     */
    java.util.concurrent.CompletableFuture<String> openBoundStory(Player p,int npcId,Entity anchor){
        var shop=Bukkit.getServicesManager().load(ShopBridge.class);
        if(shop!=null){String status=shop.openBound(p,npcId,anchor);if(!status.equals("NONE"))return java.util.concurrent.CompletableFuture.completedFuture(status);}
        var f=new java.util.concurrent.CompletableFuture<String>();
        var candidates=bindings.getOrDefault("citizens:"+npcId,List.of()).stream().map(catalog::get)
                .filter(d->d!=null&&d.enabled()&&(d.permission().isEmpty()||p.hasPermission(d.permission()))).toList();
        if(candidates.isEmpty()||!p.hasPermission("magiccodex.dialogue")){f.complete("NONE");return f;}
        UUID id=p.getUniqueId();
        if(closing||!ready(p)||busy.contains(id)||!near(p,anchor)){f.complete("BUSY");return f;}
        if(!p.getListeningPluginChannels().contains(DialogueProtocol.RESPONSE)){p.sendMessage("대화 모드를 업데이트해 주세요.");f.complete("BLOCKED");return f;}
        if(!gate(p)){f.complete("BUSY");return f;}
        busy.add(id);sessions.remove(id);var known=revisions;
        work(id,()->new Opening(store.state(id),store.effects(id)),o->{
            var blocked=new HashMap<String,String>();
            resume(p,o.effects(),0,blocked,()->{
                busy.remove(id);
                if(!current(p)||!ready(p)||!near(p,anchor)){f.complete("BUSY");return;}
                String held=null;
                for(var d:candidates){
                    if(blocked.containsKey(d.id())){if(held==null)held=blocked.get(d.id());continue;}
                    var s=new Session(d,o.state(),false,anchor,known.getOrDefault(d.id(),""));if(allowed(p,s,d.nodes().get(s.node).conditions())){sessions.put(id,s);send(p,s,"");f.complete("OPENED");return;}
                }
                if(held!=null){p.sendMessage(held);f.complete("BLOCKED");}else f.complete("NONE");
            });
        },m->{busy.remove(id);if(current(p))p.sendMessage(m);f.complete("BLOCKED");});
        return f;
    }
    /** 메인 퀘스트 진행 요약 (story.* 상태 값). */
    java.util.concurrent.CompletableFuture<String> storySummary(UUID uid){
        var f=new java.util.concurrent.CompletableFuture<String>();
        work(()->store.state(uid),state->{
            var parts=new ArrayList<String>();
            state.values().entrySet().stream().filter(e->e.getKey().startsWith("story.")).sorted(Map.Entry.comparingByKey()).forEach(e->{
                String sid=e.getKey().substring(6);
                var d=catalog.values().stream().filter(v->v.storyId().equals(sid)).sorted(Comparator.comparing(DialogueDefinition::id)).findFirst().orElse(null);
                parts.add((d==null?sid:d.storyTitle())+": "+(d!=null&&d.stages().containsKey(e.getValue())?d.stages().get(e.getValue()):e.getValue()));
            });
            f.complete(String.join(", ",parts));
        },m->f.complete(""));
        return f;
    }
    private boolean allowed(Player p,Session s,List<String> conditions){return s.preview||DialogueDefinition.matches(conditions,s.state.values(),p::hasPermission,(key,arg)->{if(key.startsWith("quest:"))return plugin.dialogueQuestCondition(p,key.substring(6),arg);var handler=this.conditions.get(key.substring(7));try{return handler!=null&&handler.test(p,arg);}catch(Exception e){return false;}});}
    private void send(Player p,Session s,String message){if(!current(p))return;var n=s.definition.nodes().get(s.node);var cs=n.choices().stream().filter(c->allowed(p,s,c.conditions())).map(c->new DialogueProtocol.Choice(c.id(),c.text())).toList();p.sendPluginMessage(plugin,DialogueProtocol.RESPONSE,DialogueProtocol.encode(new DialogueProtocol.Response(s.token,s.sequence,false,s.preview,(s.definition.storyId().isEmpty()?"":"[메인 퀘스트] ")+s.definition.title(),n.speaker(),n.portrait(),n.text(),cs,message)));}
    private void end(Player p,Session s,String message){sessions.remove(p.getUniqueId(),s);if(current(p))p.sendPluginMessage(plugin,DialogueProtocol.RESPONSE,DialogueProtocol.encode(new DialogueProtocol.Response(s.token,s.sequence,true,s.preview,"","","","",List.of(),message)));}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(channel.equals(DialogueAdminProtocol.REQUEST)){admin(p,bytes);return;}
        if(!channel.equals(DialogueProtocol.REQUEST)||!p.hasPermission("magiccodex.dialogue"))return;
        try{var r=DialogueProtocol.request(bytes);var s=sessions.get(p.getUniqueId());if(s==null||!s.token.equals(r.session())||s.sequence!=r.sequence())return;
            if(r.close()){sessions.remove(p.getUniqueId(),s);return;}
            if(!gate(p))return;
            if(!ready(p)||!near(p,s.anchor)||System.currentTimeMillis()>s.expires){end(p,s,"대화를 다시 시작해 주세요.");return;}
            if(s.preview&&!p.hasPermission("magiccodex.dialogue.admin")){end(p,s,"");return;}
            if(!s.preview&&!s.journal){var published=catalog.get(s.definition.id());if(published==null||!published.enabled()||!revisions.getOrDefault(published.id(),"").equals(s.revision)||(!published.permission().isEmpty()&&!p.hasPermission(published.permission()))){end(p,s,"대화가 수정되었습니다. 다시 열어 주세요.");return;}}
            var n=s.definition.nodes().get(s.node);var choice=n.choices().stream().filter(c->c.id().equals(r.choice())).findFirst().orElse(null);
            if(choice==null||!allowed(p,s,n.conditions())||!allowed(p,s,choice.conditions())){send(p,s,"선택 조건을 확인해 주세요.");return;}
            if(s.preview||s.journal){advance(p,s,choice);return;}
            for(String a:choice.actions())if(a.startsWith("custom ")&&!handlers.containsKey(a.split(" ",3)[1])){send(p,s,"이 대화의 동작을 관리자에게 확인해 주세요.");return;}
            // (a) 받을 수 없는 의뢰가 들어 있으면 진행 상태를 바꾸기 전에 멈추고 이유를 알려 준다.
            for(String a:choice.actions())if(a.startsWith("quest ")){String why=questRefusal(p,a.split(" ",3)[1]);if(!why.isEmpty()){send(p,s,why);return;}}
            UUID uid=p.getUniqueId();busy.add(uid);String key=s.definition.id()+":"+s.node+":"+choice.id();
            String first=choice.actions().stream().filter(a->!a.startsWith("flag ")&&!a.startsWith("story ")).findFirst().orElse("");boolean arm=!first.isEmpty()&&!first.startsWith("quest ");
            if(arm)arming.put(uid,key);
            work(uid,()->store.transition(uid,s.state,key,choice.actions(),arm),tx->{
                arming.remove(uid);s.state=tx.state();
                if(tx.receipt().isEmpty()){busy.remove(uid);if(current(p)&&sessions.get(uid)==s)advance(p,s,choice);return;}
                var run=new Run(uid,key,tx.receipt(),tx.external(),arm);runs.put(run.token,run);
                step(p,run,m->{busy.remove(uid);if(!current(p))return;if(!m.isEmpty())end(p,s,m);else if(sessions.get(uid)==s)advance(p,s,choice);});
            },m->{arming.remove(uid);busy.remove(uid);end(p,s,m);});
        }catch(IllegalArgumentException ignored){}
    }
    private String questRefusal(Player p,String quest){var q=plugin.questBridge();return q==null?"의뢰 기능을 사용할 수 없습니다.":q.acceptRefusal(p,quest);}
    private void record(Run run,String note){plugin.getLogger().warning("DIALOGUE EFFECT "+note+" player="+run.player+" key="+run.key+" token="+run.token+" remaining="+run.remaining);}
    /**
     * 남은 동작을 순서대로 실행한다. finished 에는 전부 끝났으면 빈 문자열, 아니면 유저에게 보여 줄 안내가 온다.
     * quest 는 멱등이라 기록 상태와 무관하게 실행하고, event/command/custom 은 running 이 기록된 뒤에만 실행한다.
     */
    private void step(Player p,Run run,Consumer<String> finished){
        UUID uid=run.player;
        if(run.remaining.isEmpty()){runs.remove(run.token);must(uid,()->store.move(uid,run.token,List.of("queued","running"),"done",List.of()),ok->finished.accept(""),m->finished.accept(""));return;}
        if(!current(p)||!ready(p)){park(run,"queued",()->finished.accept("접속 상태가 바뀌어 남은 처리는 다음에 이어서 합니다."));return;}
        String action=run.remaining.get(0);String[] a=action.split(" ",3);
        if(a[0].equals("quest")){plugin.acceptDialogueQuest(p,a[1],ok->guard(uid,()->{
            if(ok){run.remaining.remove(0);step(p,run,finished);return;}
            String why=current(p)?questRefusal(p,a[1]):"";park(run,"queued",()->finished.accept((why.isEmpty()?"의뢰를 지금 받을 수 없습니다.":why)+" 조건이 풀리면 다시 말을 걸어 주세요."));}));return;}
        if(!run.armed){
            // 이 서버에 없는 custom 처리기가 남아 있으면 실행을 시작하지 않는다(기록은 queued 그대로).
            for(String next:run.remaining)if(next.startsWith("custom ")&&!handlers.containsKey(next.split(" ",3)[1])){runs.remove(run.token);finished.accept("이 대화의 남은 처리는 이 서버에서 할 수 없습니다. 원래 서버에서 다시 말을 걸어 주세요.");return;}
            var snapshot=List.copyOf(run.remaining);
            must(uid,()->{boolean won=store.move(uid,run.token,List.of("queued"),"running",snapshot);if(won)run.armed=true;return won;},ok->{
                if(!ok){runs.remove(run.token);finished.accept("대화 실행 기록이 다른 곳에서 바뀌었습니다. 대화를 다시 열어 주세요.");return;}
                step(p,run,finished);
            },m->park(run,"queued",()->finished.accept(m)));
            return;
        }
        if(a[0].equals("event")){run.eventInFlight=true;plugin.dialogueQuestEvent(p,a[1],ok->guard(uid,()->{
            run.eventInFlight=false;
            // false 는 의뢰 저장소가 기록을 거절/롤백한 경우다: 반영되지 않았으므로 다음에 다시 시도한다.
            if(!ok){park(run,"queued",()->finished.accept("이벤트 기록이 지연되고 있습니다. 잠시 뒤 다시 말을 걸어 주세요."));return;}
            run.remaining.remove(0);checkpoint(p,run,finished);}));return;}
        // command/custom 은 같은 틱에 이어서 실행한다. 실패하면 그 동작부터 남겨 관리자 확인으로 보낸다.
        while(!run.remaining.isEmpty()){
            String next=run.remaining.get(0);String[] b=next.split(" ",3);if(!b[0].equals("command")&&!b[0].equals("custom"))break;
            try{
                if(b[0].equals("command")){if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),next.substring(8).replace("{player}",p.getName()).replace("{uuid}",p.getUniqueId().toString())))throw new IllegalStateException("등록되지 않은 명령어");}
                else{var handler=handlers.get(b[1]);if(handler==null)throw new IllegalStateException("등록되지 않은 처리기");handler.accept(p,b[2]);}
            }catch(Exception e){
                record(run,"FAILED ("+e.getClass().getSimpleName()+": "+e.getMessage()+") action=["+next+"]");
                park(run,"review",()->finished.accept("대화 동작을 실행하지 못했습니다. 관리자에게 문의해 주세요."));return;
            }
            run.remaining.remove(0);
        }
        try{p.saveData();}catch(RuntimeException e){plugin.getLogger().warning("Dialogue saveData: "+e.getClass().getSimpleName());}
        checkpoint(p,run,finished);
    }
    /** 실행한 만큼을 기록한다. 이 기록이 실패하면 running 으로 남아(실행은 했음) 관리자 확인 대상이 된다. */
    private void checkpoint(Player p,Run run,Consumer<String> finished){
        UUID uid=run.player;boolean done=run.remaining.isEmpty();var snapshot=List.copyOf(run.remaining);if(done)runs.remove(run.token);
        must(uid,()->store.move(uid,run.token,List.of("running"),done?"done":"running",snapshot),ok->{
            if(done){finished.accept("");return;}
            if(!ok){runs.remove(run.token);record(run,"CHECKPOINT LOST");finished.accept(REVIEW);return;}
            step(p,run,finished);
        },m->{runs.remove(run.token);record(run,"CHECKPOINT FAILED (실행은 했으나 기록 실패)");finished.accept(done?"":REVIEW);});
    }
    /**
     * 실행을 멈추고 남은 동작과 상태를 기록한다. queued: 다음에 자동으로 이어서 실행. review: 관리자 확인.
     * 이 묶음이 알고 있는 상태(armed 면 running, 아니면 queued)에서만 바꾼다: 유저가 서버를 옮겨 다른 서버가 이미 이어서 실행 중(running)인 기록을
     * 이 서버의 늦은 응답이 queued 로 되돌려 같은 명령어가 두 번 실행되는 일을 막는다.
     */
    private void park(Run run,String status,Runnable then){
        runs.remove(run.token);var snapshot=List.copyOf(run.remaining);UUID uid=run.player;
        must(uid,()->store.move(uid,run.token,List.of(run.armed?"running":"queued"),status,snapshot),ok->then.run(),m->{record(run,"PARK FAILED status="+status);then.run();});
    }
    /** 끝내지 못한 동작(queued)을 차례로 이어서 실행한다. 끝내지 못했거나 관리자 확인이 필요한 대화는 blocked(대화 ID -> 안내)에 담는다. */
    private void resume(Player p,List<DialogueStore.Effect> effects,int index,Map<String,String> blocked,Runnable then){
        if(index>=effects.size()){then.run();return;}
        var e=effects.get(index);String scope=e.dialogue();Runnable next=()->resume(p,effects,index+1,blocked,then);
        if(!e.status().equals("queued")||runs.containsKey(e.token())){blocked.putIfAbsent(scope,REVIEW);next.run();return;}
        if(blocked.containsKey(scope)||!current(p)||!ready(p)){blocked.putIfAbsent(scope,"이 대화의 이전 처리가 아직 끝나지 않았습니다. 잠시 뒤 다시 말을 걸어 주세요.");next.run();return;}
        var run=new Run(p.getUniqueId(),e.key(),e.token(),e.actions(),false);runs.put(run.token,run);
        step(p,run,m->{if(!m.isEmpty())blocked.putIfAbsent(scope,m);next.run();});
    }
    /** 접속 뒤 준비가 끝나면(캐릭터 정보 로드) 끝내지 못한 동작을 조용히 이어서 실행한다. */
    private void resumeLater(Player p,int attempt){
        if(closing)return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(closing||!current(p))return;UUID id=p.getUniqueId();
            if(!ready(p)||busy.contains(id)||sessions.containsKey(id)){if(attempt<8)resumeLater(p,attempt+1);return;}
            busy.add(id);work(id,()->store.effects(id),list->resume(p,list,0,new HashMap<>(),()->busy.remove(id)),m->busy.remove(id));
        },100L);
    }
    @EventHandler public void join(PlayerJoinEvent e){resumeLater(e.getPlayer(),0);}
    private void advance(Player p,Session s,DialogueDefinition.Choice c){if(c.next().isEmpty()){end(p,s,"");return;}s.node=c.next();s.sequence++;s.expires=System.currentTimeMillis()+300000;if(!allowed(p,s,s.definition.nodes().get(s.node).conditions())){end(p,s,"");return;}send(p,s,"");}
    private void admin(Player p,byte[] bytes){if(!p.hasPermission("magiccodex.dialogue.admin")||!gate(p))return;try{var r=DialogueAdminProtocol.request(bytes);if(r.action()==4){var d=DialogueDefinition.fromFields(r.id(),r.fields());limits.remove(p.getUniqueId());open(p,d,true,null);return;}
        if(r.action()<2){adminReply(p,"",r.action()==1?r.id():"");return;}
        var d=r.action()==2?DialogueDefinition.fromFields(r.id(),r.fields()):null;UUID uid=p.getUniqueId();busy.add(uid);
        work(()->{if(!store.edit(r.id(),r.revision(),d))return false;if(d!=null)export(d);return true;},ok->{busy.remove(uid);adminReply(p,ok?(d==null?"대화를 삭제했습니다.":"저장했습니다."):"다른 관리자가 수정했습니다. 다시 불러와 주세요.",ok&&d!=null?r.id():"");},m->{busy.remove(uid);adminReply(p,m,"");});
    }catch(Exception e){adminReply(p,"입력 확인: "+e.getMessage(),"");}}
    private void adminReply(Player p,String message,String id){work(()->store.snapshot(null),snapshot->{install(snapshot);var map=snapshot.definitions();if(!current(p)||!p.hasPermission("magiccodex.dialogue.admin"))return;var list=map.values().stream().sorted(Comparator.comparing(DialogueDefinition::id)).limit(DialogueAdminProtocol.MAX_ENTRIES).map(d->new DialogueAdminProtocol.Summary(d.id(),d.title(),"",d.enabled()?"공개":"비공개")).toList();var d=map.get(id);String note=map.size()>list.size()&&message.isEmpty()?"목록은 앞의 "+list.size()+"개만 표시합니다. 나머지는 /대화관리 preview|reload|pull <ID>로 다뤄 주세요.":message;try{p.sendPluginMessage(plugin,DialogueAdminProtocol.RESPONSE,DialogueAdminProtocol.encode(new DialogueAdminProtocol.Response(note,list,d==null?"":d.id(),d==null?"":snapshot.revisions().getOrDefault(d.id(),""),d==null?Map.of():d.fields())));}catch(Exception e){p.sendMessage("대화 목록/문서가 너무 큽니다. YAML 파일로 편집해 주세요.");}},p::sendMessage);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
        if(command.getName().equals("메인퀘스트")){if(sender instanceof Player p)journal(p);return true;}
        if(!sender.hasPermission("magiccodex.dialogue.admin"))return true;
        if(args.length==0&&sender instanceof Player p){if(gate(p))adminReply(p,"","");return true;}
        if(command.getName().equals("대화")&&args.length==1&&sender instanceof Player p){var d=catalog.get(args[0]);if(d!=null)open(p,d,false,null);return true;}
        if(args.length==2&&args[0].equals("preview")&&sender instanceof Player p){var d=catalog.get(args[1]);if(d!=null)open(p,d,true,null);return true;}
        if(args.length==2&&args[0].equals("reload")){String id=args[1];DialogueDefinition.id(id);String expected=revisions.getOrDefault(id,"");work(()->store.edit(id,expected,read(id)),ok->{sender.sendMessage(ok?"대화 파일 반영 완료":"다른 곳에서 변경되었습니다. pull로 확인하세요.");refresh();},sender::sendMessage);return true;}
        if(args.length==2&&args[0].equals("pull")){String id=args[1];DialogueDefinition.id(id);work(()->{var d=store.catalog().get(id);if(d==null)throw new IllegalArgumentException("대화 없음");export(d);return true;},ok->sender.sendMessage("공용 대화를 YAML로 저장했습니다. 이전 파일은 .bak 백업."),sender::sendMessage);return true;}
        if(args.length==2&&args[0].equals("audit")){UUID uid=target(args[1]);work(()->store.effects(uid),rows->{
            sender.sendMessage("끝나지 않은 대화 동작 "+rows.size()+"건 ("+uid+")"+(rows.isEmpty()?"":" — /대화관리 resolve "+args[1]+" <토큰> rerun|done"));
            listed.put(sender.getName(),rows.stream().map(DialogueStore.Effect::token).toList());
            for(var e:rows){sender.sendMessage(e.token()+" | "+e.key()+" | "+statusText(e));for(String action:e.actions())sender.sendMessage("    남은 동작: "+action);}
        },sender::sendMessage);return true;}
        if((args.length==3||args.length==4)&&args[0].equals("resolve")){
            UUID uid=target(args[1]);String token=UUID.fromString(args[2]).toString();
            if(args.length==3||!List.of("rerun","done").contains(args[3])){
                // 무엇을 처리하는지 먼저 보여 주고, 방법을 고르게 한다.
                work(()->store.effects(uid),rows->{var e=rows.stream().filter(v->v.token().equals(token)).findFirst().orElse(null);if(e==null){sender.sendMessage("끝나지 않은 기록 중에 그 토큰이 없습니다. audit 로 확인하세요.");return;}
                    sender.sendMessage(e.key()+" | "+statusText(e));for(String action:e.actions())sender.sendMessage("    남은 동작: "+action);
                    sender.sendMessage("처리 방법을 고르세요: /대화관리 resolve "+args[1]+" "+token+" rerun (남은 동작을 다시 실행) | done (실행하지 않고 완료로 닫기)");},sender::sendMessage);
                return true;
            }
            if(runs.containsKey(token)||busy.contains(uid)){sender.sendMessage("지금 처리 중인 기록입니다. 잠시 뒤 다시 시도하세요.");return true;}
            boolean rerun=args[3].equals("rerun");String admin=sender.getName();
            work(()->{
                var before=store.effects(uid).stream().filter(e->e.token().equals(token)).findFirst().orElseThrow(()->new IllegalArgumentException("끝나지 않은 기록 중에 그 토큰이 없습니다. audit 로 확인하세요."));
                if(!rerun)store.finish(uid,token);
                else if(!before.status().equals("queued")&&!store.move(uid,token,List.of("review","running","pending"),"queued",null))throw new IllegalStateException("기록이 방금 바뀌었습니다. audit 로 다시 확인하세요.");
                return before;
            },before->{
                plugin.getLogger().warning("DIALOGUE ADMIN admin="+admin+" player="+uid+" key="+before.key()+" token="+token+" action="+args[3]+" from="+before.status()+" actions="+before.actions());
                if(!rerun){sender.sendMessage("실행하지 않고 완료로 닫았습니다. 실행하지 않은 동작 "+before.actions().size()+"개: "+String.join(" ; ",before.actions()));return;}
                Player online=Bukkit.getPlayer(uid);
                if(online==null||!current(online)||!ready(online)||busy.contains(uid)||sessions.containsKey(uid)){sender.sendMessage("다시 실행 대기로 바꿨습니다. 유저가 다음에 접속하거나 대화를 열 때 남은 동작을 실행합니다.");return;}
                busy.add(uid);work(uid,()->store.effects(uid),list->{var blocked=new HashMap<String,String>();resume(online,list,0,blocked,()->{busy.remove(uid);sender.sendMessage(blocked.containsKey(before.dialogue())?"다시 실행했지만 끝나지 않았습니다: "+blocked.get(before.dialogue()):"남은 동작을 다시 실행해 완료했습니다.");});},m->{busy.remove(uid);sender.sendMessage(m);});
            },sender::sendMessage);return true;
        }
    }catch(Exception e){sender.sendMessage("대화 설정 확인: "+e.getMessage());return true;}
        sender.sendMessage("/대화 <ID> · /대화관리 · /대화관리 preview|reload|pull <ID> · audit <유저|UUID> · resolve <유저|UUID> <토큰> rerun|done");return true;
    }
    private UUID target(String input){try{return UUID.fromString(input);}catch(IllegalArgumentException ignored){}Player online=plugin.names().resolve(input);if(online==null)throw new IllegalArgumentException("접속 중인 유저의 닉네임·영문 계정명 또는 UUID를 입력하세요.");return online.getUniqueId();}
    private String statusText(DialogueStore.Effect e){return switch(e.status()){case "queued"->runs.containsKey(e.token())?"실행 중":"대기 — 유저 접속·대화 열기 때 자동 실행";case "running"->runs.containsKey(e.token())?"실행 중":"실행 여부 불명 — 실행 도중 서버가 멈춤 (관리자 확인)";case "review"->"동작 실패 — 맨 위 동작부터 실행되지 않음 (관리자 확인)";case "pending"->"옛 기록 — 실행 여부 불명 (관리자 확인)";default->e.status();};}
    private void journal(Player p){if(!ready(p)||!gate(p))return;work(()->store.state(p.getUniqueId()),state->{
        if(!current(p))return;
        var rows=state.values().entrySet().stream().filter(e->e.getKey().startsWith("story.")).sorted(Map.Entry.comparingByKey()).map(e->{String id=e.getKey().substring(6);var d=catalog.values().stream().filter(v->v.storyId().equals(id)).sorted(Comparator.comparing(DialogueDefinition::id)).findFirst().orElse(null);return "[메인 퀘스트] "+(d==null?id:d.storyTitle())+"\n"+(d==null?e.getValue():d.stages().getOrDefault(e.getValue(),e.getValue()));}).toList();
        int pages=Math.max(1,(rows.size()+3)/4);var nodes=new LinkedHashMap<String,DialogueDefinition.Node>();
        for(int page=0;page<pages;page++){var choices=new ArrayList<DialogueDefinition.Choice>();if(page>0)choices.add(new DialogueDefinition.Choice("previous","이전 이야기","p"+(page-1),List.of(),List.of()));if(page+1<pages)choices.add(new DialogueDefinition.Choice("next","다음 이야기","p"+(page+1),List.of(),List.of()));String text=rows.isEmpty()?"아직 시작한 이야기가 없습니다.":String.join("\n\n",rows.subList(page*4,Math.min(rows.size(),page*4+4)));nodes.put("p"+page,new DialogueDefinition.Node("이야기 기록","",text,List.of(),List.copyOf(choices)));}
        var d=new DialogueDefinition("journal","메인 퀘스트","p0",true,"",List.of(),Map.copyOf(nodes),"","",Map.of());var session=new Session(d,state,false,null,"");session.journal=true;sessions.put(p.getUniqueId(),session);send(p,session,"");
    },p::sendMessage);}
    private void refresh(){work(()->store.snapshot(null),this::install,m->plugin.getLogger().warning(m));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){
        if(!s.hasPermission("magiccodex.dialogue.admin")||a.length==0||c.getName().equals("메인퀘스트"))return List.of();String prefix=a[a.length-1];
        if(c.getName().equals("대화"))return a.length==1?AdminCommandRules.filter(catalog.keySet(),prefix):List.of();
        List<String> options=switch(a.length){
            case 1->List.of("preview","reload","pull","audit","resolve");
            case 2->Set.of("audit","resolve").contains(a[0])?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():Set.of("preview","reload","pull").contains(a[0])?List.copyOf(catalog.keySet()):List.<String>of();
            case 3->a[0].equals("resolve")?listed.getOrDefault(s.getName(),List.of()):List.<String>of();
            case 4->a[0].equals("resolve")?List.of("rerun","done"):List.<String>of();
            default->List.<String>of();};
        return AdminCommandRules.filter(options,prefix);
    }
    private interface Job<T>{T run()throws Exception;}
    /** 메인 스레드 후속 처리는 여기를 거친다: 예외가 나도 busy 가 풀리고, 실행 중이던 묶음은 남은 동작 그대로 기록해 다음에 이어서 실행한다. */
    private void guard(UUID owner,Runnable r){
        try{r.run();}catch(Throwable t){
            plugin.getLogger().log(Level.SEVERE,"Dialogue continuation failed"+(owner==null?"":" for "+owner),t);
            if(owner!=null){busy.remove(owner);arming.remove(owner);for(var run:new ArrayList<>(runs.values()))if(run.player.equals(owner)&&!run.eventInFlight)park(run,"queued",()->{});}
        }
    }
    private <T>void work(Job<T> job,Consumer<T> done,Consumer<String> fail){work(null,job,done,fail);}
    /** 새 요청: 밀려 있으면 아무 것도 바꾸지 않고 거절한다. */
    private <T>void work(UUID owner,Job<T> job,Consumer<T> done,Consumer<String> fail){if(closing||pending>=256){fail.accept("대화 처리가 밀리고 있습니다. 잠시 후 다시 시도해 주세요.");return;}submit(owner,job,done,fail);}
    /** 이미 시작한 동작의 후속 기록: 대기 수 한도로 거절하지 않는다. */
    private <T>void must(UUID owner,Job<T> job,Consumer<T> done,Consumer<String> fail){if(closing){fail.accept("서버가 종료 중입니다.");return;}submit(owner,job,done,fail);}
    private <T>void submit(UUID owner,Job<T> job,Consumer<T> done,Consumer<String> fail){pending++;io.execute(()->{
        T result;try{result=job.run();}catch(Exception e){plugin.getLogger().warning("Dialogue storage: "+e.getClass().getSimpleName()+": "+e.getMessage());String m=e instanceof IllegalArgumentException||e instanceof IllegalStateException?String.valueOf(e.getMessage()):"대화 저장을 확인할 수 없습니다. 다시 시도해 주세요.";main(()->{pending--;guard(owner,()->fail.accept(m));});return;}
        main(()->{pending--;guard(owner,()->done.accept(result));});});}
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    @Override public void close(){closing=true;
        // 여기부터 메인 스레드 후속 처리는 실행되지 않는다. 실행 중이던 묶음은 "아직 실행하지 않은 동작"이 정확히 알려져 있으므로 그대로 기록해 다음 접속 때 이어서 실행한다.
        // (event 응답을 기다리던 묶음만 running 으로 남겨 관리자 확인 대상으로 둔다.)
        for(var run:runs.values())if(!run.eventInFlight){var remaining=List.copyOf(run.remaining);try{io.execute(()->{try{store.move(run.player,run.token,List.of(run.armed?"running":"queued"),remaining.isEmpty()?"done":"queued",remaining);}catch(Exception e){plugin.getLogger().warning("Dialogue shutdown park failed: "+run.token);}});}catch(RejectedExecutionException ignored){}}
        for(var entry:arming.entrySet())try{io.execute(()->{try{store.requeue(entry.getKey(),entry.getValue());}catch(Exception e){plugin.getLogger().warning("Dialogue shutdown requeue failed: "+entry.getValue());}});}catch(RejectedExecutionException ignored){}
        runs.clear();arming.clear();sessions.clear();io.shutdown();try{if(io.awaitTermination(15,TimeUnit.SECONDS))store.close();else plugin.getLogger().warning("Dialogue worker shutdown timeout");}catch(Exception e){plugin.getLogger().warning("Dialogue close failed");}HandlerList.unregisterAll(this);}
}
