package school.magiccodex.paper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.*;

/** Main-thread sessions, one bounded IO queue, cached immutable catalogs. No per-tick DB polling. */
final class DialogueBridge implements Listener,CommandExecutor,TabCompleter,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-dialogue-io");t.setDaemon(true);return t;});
    private final DialogueStore store;private final Path directory;
    private Map<String,DialogueDefinition> catalog=Map.of();private Map<String,List<String>> bindings=Map.of();
    private final Map<UUID,Session> sessions=new HashMap<>();private final Map<UUID,Long> limits=new HashMap<>();private final Set<UUID> busy=new HashSet<>();
    private final Map<String,BiConsumer<Player,String>> handlers=new HashMap<>();
    private final Map<String,BiPredicate<Player,String>> conditions=new HashMap<>();
    void registerCondition(String id,BiPredicate<Player,String> predicate){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");DialogueDefinition.id(id);conditions.put(id,Objects.requireNonNull(predicate));}
    private boolean closing,refreshing;private int pending;
    /** 외부 플러그인(ChacaNPC)이 클릭을 직접 처리하는 Citizens NPC id. 그 NPC는 아래 Citizens 처리에서 제외한다. */
    private volatile java.util.function.IntPredicate claimed=id->false;
    void claim(java.util.function.IntPredicate predicate){claimed=predicate==null?id->false:predicate;}
    private boolean claimed(int id){try{return claimed.test(id);}catch(RuntimeException e){return false;}}
    private static final class Session {
        final String token=UUID.randomUUID().toString();final DialogueDefinition definition;final boolean preview;final Entity anchor;boolean journal;
        DialogueStore.State state;String node;int sequence;long expires=System.currentTimeMillis()+300000;
        Session(DialogueDefinition d,DialogueStore.State s,boolean preview,Entity anchor){definition=d;state=s;this.preview=preview;this.anchor=anchor;node=d.start();}
    }
    DialogueBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;directory=plugin.getDataFolder().toPath().resolve("dialogues");Files.createDirectories(directory);
        if(!Files.exists(directory.resolve("elena.yml")))plugin.saveResource("dialogues/elena.yml",false);
        if(!Files.exists(directory.resolve("arden.yml")))plugin.saveResource("dialogues/arden.yml",false);
        var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));
        try{store=io.submit(()->new DialogueStore(settings,plugin.getDataFolder().toPath().resolve("dialogue.db"))).get(15,TimeUnit.SECONDS);
            var initial=io.submit(()->{var map=store.catalog();if(map.isEmpty()){for(String id:List.of("elena","arden"))store.edit(id,"",read(id));}return store.catalog();}).get(15,TimeUnit.SECONDS);install(initial);
        }catch(Exception e){io.shutdownNow();throw e;}
        for(String channel:List.of(DialogueProtocol.REQUEST,DialogueAdminProtocol.REQUEST))Bukkit.getMessenger().registerIncomingPluginChannel(plugin,channel,this);
        for(String channel:List.of(DialogueProtocol.RESPONSE,DialogueAdminProtocol.RESPONSE))Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,channel);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String c:List.of("대화","대화관리","메인퀘스트")){plugin.getCommand(c).setExecutor(this);plugin.getCommand(c).setTabCompleter(this);}
        citizens();
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            sessions.entrySet().removeIf(e->System.currentTimeMillis()>e.getValue().expires);
            if(!refreshing&&!closing){refreshing=true;work(store::catalog,v->{install(v);refreshing=false;},m->refreshing=false);}
        },200,200);
    }
    void register(String id,BiConsumer<Player,String> handler){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");DialogueDefinition.id(id);handlers.put(id,Objects.requireNonNull(handler));}
    private void install(Map<String,DialogueDefinition> definitions){catalog=definitions;var index=new HashMap<String,List<String>>();definitions.values().stream().sorted(Comparator.comparing(DialogueDefinition::id)).filter(DialogueDefinition::enabled).forEach(d->d.npcs().forEach(n->index.computeIfAbsent(n,k->new ArrayList<>()).add(d.id())));bindings=Map.copyOf(index);}
    private DialogueDefinition read(String id)throws Exception{DialogueDefinition.id(id);return DialogueDefinition.decode(id,Files.readString(directory.resolve(id+".yml")));}
    private void export(DialogueDefinition d)throws Exception{Path file=directory.resolve(d.id()+".yml"),tmp=directory.resolve(d.id()+".yml.tmp");if(Files.exists(file))Files.copy(file,directory.resolve(d.id()+".yml.bak"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(tmp,d.encode());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
    @SuppressWarnings("unchecked") private void citizens(){var dep=Bukkit.getPluginManager().getPlugin("Citizens");if(dep==null)return;try{Class<? extends Event> type=(Class<? extends Event>)Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent",true,dep.getClass().getClassLoader());Bukkit.getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(l,event)->{try{Player p=(Player)event.getClass().getMethod("getClicker").invoke(event);Object npc=event.getClass().getMethod("getNPC").invoke(event);Entity e=(Entity)npc.getClass().getMethod("getEntity").invoke(npc);int nid=((Number)npc.getClass().getMethod("getId").invoke(npc)).intValue();if(claimed(nid))return;String id="citizens:"+nid;bound(p,id,e);}catch(Exception ex){plugin.getLogger().warning("Dialogue NPC hook: "+ex.getClass().getSimpleName());}},plugin,true);}catch(Exception e){plugin.getLogger().warning("Dialogue Citizens hook unavailable: "+e.getClass().getSimpleName());}}
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEntityEvent e){if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||e.getRightClicked().hasMetadata("NPC"))return;for(String tag:e.getRightClicked().getScoreboardTags())if(bindings.containsKey("tag:"+tag)){bound(e.getPlayer(),"tag:"+tag,e.getRightClicked());break;}}
    private void bound(Player p,String key,Entity anchor){if(!near(p,anchor))return;for(String id:bindings.getOrDefault(key,List.of())){var d=catalog.get(id);if(d!=null&&(d.permission().isEmpty()||p.hasPermission(d.permission()))){open(p,d,false,anchor);return;}}}
    @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());/* busy remains until queued work finishes */}
    private boolean ready(Player p){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.playerStateReady(p);}
    private boolean current(Player p){return p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
    private boolean near(Player p,Entity e){return e==null||(e.isValid()&&p.getWorld().equals(e.getWorld())&&p.getLocation().distanceSquared(e.getLocation())<=64);}
    private boolean gate(Player p){UUID id=p.getUniqueId();long now=System.currentTimeMillis();if(closing||busy.contains(id)||now<limits.getOrDefault(id,0L))return false;limits.put(id,now+150);return true;}
    private void open(Player p,DialogueDefinition d,boolean preview,Entity anchor){
        if(!p.hasPermission("magiccodex.dialogue")||!ready(p)||!gate(p))return;
        if(!p.getListeningPluginChannels().contains(DialogueProtocol.RESPONSE)){p.sendMessage("대화 모드를 업데이트해 주세요.");return;}
        if(!preview&&(!d.enabled()||(!d.permission().isEmpty()&&!p.hasPermission(d.permission()))))return;
        UUID id=p.getUniqueId();busy.add(id);sessions.remove(id);
        work(()->{if(!preview&&!store.audit(id).isEmpty())throw new IllegalStateException("대화 실행 기록은 관리자 확인이 필요합니다.");return store.state(id);},state->{busy.remove(id);if(!current(p)||!ready(p)||!near(p,anchor))return;var s=new Session(d,state,preview,anchor);if(!allowed(p,s,d.nodes().get(s.node).conditions())){p.sendMessage("아직 이 대화를 시작할 수 없습니다.");return;}sessions.put(id,s);send(p,s,"");},m->{busy.remove(id);if(current(p))p.sendMessage(m);});
    }
    /**
     * 외부 진입점(ChacaNPC)용: 이 Citizens NPC에 묶인 고정 대화 중 지금 열 수 있는 첫 대화를 연다.
     * 권한·공개 여부·시작 노드 조건·진행 상태 검사는 open()과 같다. 처리 중(busy)이면 NONE이 아니라 BUSY.
     */
    java.util.concurrent.CompletableFuture<String> openBoundStory(Player p,int npcId,Entity anchor){
        var f=new java.util.concurrent.CompletableFuture<String>();
        var candidates=bindings.getOrDefault("citizens:"+npcId,List.of()).stream().map(catalog::get)
                .filter(d->d!=null&&d.enabled()&&(d.permission().isEmpty()||p.hasPermission(d.permission()))).toList();
        if(candidates.isEmpty()||!p.hasPermission("magiccodex.dialogue")){f.complete("NONE");return f;}
        UUID id=p.getUniqueId();
        if(closing||!ready(p)||busy.contains(id)||!near(p,anchor)){f.complete("BUSY");return f;}
        if(!p.getListeningPluginChannels().contains(DialogueProtocol.RESPONSE)){p.sendMessage("대화 모드를 업데이트해 주세요.");f.complete("BLOCKED");return f;}
        if(!gate(p)){f.complete("BUSY");return f;}
        busy.add(id);sessions.remove(id);
        work(()->{if(!store.audit(id).isEmpty())throw new IllegalStateException("대화 실행 기록은 관리자 확인이 필요합니다.");return store.state(id);},state->{
            busy.remove(id);
            if(!current(p)||!ready(p)||!near(p,anchor)){f.complete("BUSY");return;}
            for(var d:candidates){var s=new Session(d,state,false,anchor);if(allowed(p,s,d.nodes().get(s.node).conditions())){sessions.put(id,s);send(p,s,"");f.complete("OPENED");return;}}
            f.complete("NONE");
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
            if(!s.preview&&!s.journal){var published=catalog.get(s.definition.id());if(published==null||!published.enabled()||!DialogueDefinition.revision(published).equals(DialogueDefinition.revision(s.definition))||(!published.permission().isEmpty()&&!p.hasPermission(published.permission()))){end(p,s,"대화가 수정되었습니다. 다시 열어 주세요.");return;}}
            var n=s.definition.nodes().get(s.node);var choice=n.choices().stream().filter(c->c.id().equals(r.choice())).findFirst().orElse(null);
            if(choice==null||!allowed(p,s,n.conditions())||!allowed(p,s,choice.conditions())){send(p,s,"선택 조건을 확인해 주세요.");return;}
            if(s.preview||s.journal){advance(p,s,choice);return;}
            for(String a:choice.actions())if(a.startsWith("custom ")&&!handlers.containsKey(a.split(" ",3)[1])){send(p,s,"이 대화의 동작을 관리자에게 확인해 주세요.");return;}
            UUID uid=p.getUniqueId();busy.add(uid);String key=s.definition.id()+":"+s.node+":"+choice.id();
            work(()->store.transition(uid,s.state,key,choice.actions()),tx->{
                s.state=tx.state();
                if(!current(p)||!ready(p)){busy.remove(uid);return;}
                effects(p,tx.external(),0,()->{
                    if(tx.receipt().isEmpty()){busy.remove(uid);if(sessions.get(uid)==s)advance(p,s,choice);}
                    else work(()->{store.finish(uid,tx.receipt());return true;},ok->{busy.remove(uid);if(current(p)&&sessions.get(uid)==s)advance(p,s,choice);},m->{busy.remove(uid);end(p,s,m);});
                },m->{busy.remove(uid);plugin.getLogger().warning("DIALOGUE REVIEW "+uid+" "+tx.receipt()+" "+key);end(p,s,m);});
            },m->{busy.remove(uid);end(p,s,m);});
        }catch(IllegalArgumentException ignored){}
    }
    private void effects(Player p,List<String> actions,int index,Runnable done,Consumer<String> fail){
        if(index>=actions.size()){done.run();return;}if(!current(p)||!ready(p)){fail.accept("대화 실행 기록을 관리자에게 확인해 주세요.");return;}
        String action=actions.get(index);String[] a=action.split(" ",3);Runnable next=()->effects(p,actions,index+1,done,fail);
        try{switch(a[0]){
            case "quest"->plugin.acceptDialogueQuest(p,a[1],ok->{if(ok)next.run();else fail.accept("의뢰 수락 기록을 관리자에게 확인해 주세요.");});
            case "event"->plugin.dialogueQuestEvent(p,a[1],ok->{if(ok)next.run();else fail.accept("이벤트 기록을 관리자에게 확인해 주세요.");});
            case "command"->{if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),action.substring(8).replace("{player}",p.getName()).replace("{uuid}",p.getUniqueId().toString())))throw new IllegalStateException();p.saveData();next.run();}
            case "custom"->{handlers.get(a[1]).accept(p,a[2]);next.run();}
            default->throw new IllegalArgumentException();
        }}catch(Exception e){plugin.getLogger().warning("Dialogue effect failed: "+e.getClass().getSimpleName());fail.accept("대화 실행 기록을 관리자에게 확인해 주세요.");}
    }
    private void advance(Player p,Session s,DialogueDefinition.Choice c){if(c.next().isEmpty()){end(p,s,"");return;}s.node=c.next();s.sequence++;s.expires=System.currentTimeMillis()+300000;if(!allowed(p,s,s.definition.nodes().get(s.node).conditions())){end(p,s,"");return;}send(p,s,"");}
    private void admin(Player p,byte[] bytes){if(!p.hasPermission("magiccodex.dialogue.admin")||!gate(p))return;try{var r=DialogueAdminProtocol.request(bytes);if(r.action()==4){var d=DialogueDefinition.fromFields(r.id(),r.fields());limits.remove(p.getUniqueId());open(p,d,true,null);return;}
        if(r.action()<2){adminReply(p,"",r.action()==1?r.id():"");return;}
        var d=r.action()==2?DialogueDefinition.fromFields(r.id(),r.fields()):null;UUID uid=p.getUniqueId();busy.add(uid);
        work(()->{if(!store.edit(r.id(),r.revision(),d))return false;if(d!=null)export(d);return true;},ok->{busy.remove(uid);adminReply(p,ok?(d==null?"대화를 삭제했습니다.":"저장했습니다."):"다른 관리자가 수정했습니다. 다시 불러와 주세요.",ok&&d!=null?r.id():"");},m->{busy.remove(uid);adminReply(p,m,"");});
    }catch(Exception e){adminReply(p,"입력 확인: "+e.getMessage(),"");}}
    private void adminReply(Player p,String message,String id){work(store::catalog,map->{install(map);if(!current(p)||!p.hasPermission("magiccodex.dialogue.admin"))return;var list=map.values().stream().sorted(Comparator.comparing(DialogueDefinition::id)).limit(DialogueAdminProtocol.MAX_ENTRIES).map(d->new DialogueAdminProtocol.Summary(d.id(),d.title(),"",d.enabled()?"공개":"비공개")).toList();var d=map.get(id);String note=map.size()>list.size()&&message.isEmpty()?"목록은 앞의 "+list.size()+"개만 표시합니다. 나머지는 /대화관리 preview|reload|pull <ID>로 다뤄 주세요.":message;try{p.sendPluginMessage(plugin,DialogueAdminProtocol.RESPONSE,DialogueAdminProtocol.encode(new DialogueAdminProtocol.Response(note,list,d==null?"":d.id(),d==null?"":DialogueDefinition.revision(d),d==null?Map.of():d.fields())));}catch(Exception e){p.sendMessage("대화 목록/문서가 너무 큽니다. YAML 파일로 편집해 주세요.");}},p::sendMessage);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
        if(command.getName().equals("메인퀘스트")){if(sender instanceof Player p)journal(p);return true;}
        if(!sender.hasPermission("magiccodex.dialogue.admin"))return true;
        if(args.length==0&&sender instanceof Player p){if(gate(p))adminReply(p,"","");return true;}
        if(command.getName().equals("대화")&&args.length==1&&sender instanceof Player p){var d=catalog.get(args[0]);if(d!=null)open(p,d,false,null);return true;}
        if(args.length==2&&args[0].equals("preview")&&sender instanceof Player p){var d=catalog.get(args[1]);if(d!=null)open(p,d,true,null);return true;}
        if(args.length==2&&args[0].equals("reload")){String id=args[1];DialogueDefinition.id(id);String expected=catalog.containsKey(id)?DialogueDefinition.revision(catalog.get(id)):"";work(()->store.edit(id,expected,read(id)),ok->{sender.sendMessage(ok?"대화 파일 반영 완료":"다른 곳에서 변경되었습니다. pull로 확인하세요.");refresh();},sender::sendMessage);return true;}
        if(args.length==2&&args[0].equals("pull")){String id=args[1];DialogueDefinition.id(id);work(()->{var d=store.catalog().get(id);if(d==null)throw new IllegalArgumentException("대화 없음");export(d);return true;},ok->sender.sendMessage("공용 대화를 YAML로 저장했습니다. 이전 파일은 .bak 백업."),sender::sendMessage);return true;}
        if(args.length==2&&args[0].equals("audit")){UUID uid=UUID.fromString(args[1]);work(()->store.audit(uid),rows->{sender.sendMessage("검토 대기 "+rows.size()+"건");rows.forEach(sender::sendMessage);},sender::sendMessage);return true;}
        if(args.length==3&&args[0].equals("resolve")){UUID uid=UUID.fromString(args[1]);String token=UUID.fromString(args[2]).toString();work(()->{store.finish(uid,token);return true;},ok->sender.sendMessage("실행 확인 처리 완료. 명령어를 재실행하지 않습니다."),sender::sendMessage);return true;}
    }catch(Exception e){sender.sendMessage("대화 설정 확인: "+e.getMessage());return true;}
        sender.sendMessage("/대화 <ID> · /대화관리 · /대화관리 preview|reload|pull <ID> · audit <UUID> · resolve <UUID> <토큰>");return true;
    }
    private void journal(Player p){if(!ready(p)||!gate(p))return;work(()->store.state(p.getUniqueId()),state->{
        if(!current(p))return;
        var rows=state.values().entrySet().stream().filter(e->e.getKey().startsWith("story.")).sorted(Map.Entry.comparingByKey()).map(e->{String id=e.getKey().substring(6);var d=catalog.values().stream().filter(v->v.storyId().equals(id)).sorted(Comparator.comparing(DialogueDefinition::id)).findFirst().orElse(null);return "[메인 퀘스트] "+(d==null?id:d.storyTitle())+"\n"+(d==null?e.getValue():d.stages().getOrDefault(e.getValue(),e.getValue()));}).toList();
        int pages=Math.max(1,(rows.size()+3)/4);var nodes=new LinkedHashMap<String,DialogueDefinition.Node>();
        for(int page=0;page<pages;page++){var choices=new ArrayList<DialogueDefinition.Choice>();if(page>0)choices.add(new DialogueDefinition.Choice("previous","이전 이야기","p"+(page-1),List.of(),List.of()));if(page+1<pages)choices.add(new DialogueDefinition.Choice("next","다음 이야기","p"+(page+1),List.of(),List.of()));String text=rows.isEmpty()?"아직 시작한 이야기가 없습니다.":String.join("\n\n",rows.subList(page*4,Math.min(rows.size(),page*4+4)));nodes.put("p"+page,new DialogueDefinition.Node("이야기 기록","",text,List.of(),List.copyOf(choices)));}
        var d=new DialogueDefinition("journal","메인 퀘스트","p0",true,"",List.of(),Map.copyOf(nodes),"","",Map.of());var session=new Session(d,state,false,null);session.journal=true;sessions.put(p.getUniqueId(),session);send(p,session,"");
    },p::sendMessage);}
    private void refresh(){work(store::catalog,this::install,m->plugin.getLogger().warning(m));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(!s.hasPermission("magiccodex.dialogue.admin"))return List.of();return (c.getName().equals("대화")||a.length==2?catalog.keySet().stream():List.of("preview","reload","pull","audit","resolve").stream()).filter(v->v.startsWith(a[a.length-1])).sorted().toList();}
    private interface Job<T>{T run()throws Exception;}
    private <T>void work(Job<T> job,Consumer<T> done,Consumer<String> fail){if(closing||pending>=256){fail.accept("대화 처리가 밀리고 있습니다. 잠시 후 다시 시도해 주세요.");return;}pending++;io.execute(()->{try{T result=job.run();main(()->{pending--;done.accept(result);});}catch(Exception e){plugin.getLogger().warning("Dialogue storage: "+e.getClass().getSimpleName()+": "+e.getMessage());main(()->{pending--;fail.accept(e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"대화 저장을 확인할 수 없습니다. 다시 시도해 주세요.");});}});}
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    @Override public void close(){closing=true;sessions.clear();io.shutdown();try{if(io.awaitTermination(15,TimeUnit.SECONDS))store.close();else plugin.getLogger().warning("Dialogue worker shutdown timeout");}catch(Exception e){plugin.getLogger().warning("Dialogue close failed");}HandlerList.unregisterAll(this);}
}
