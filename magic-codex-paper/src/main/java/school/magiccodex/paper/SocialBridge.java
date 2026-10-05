package school.magiccodex.paper;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import school.magiccodex.protocol.SocialProtocol;
import school.magiccodex.protocol.SocialProtocol.*;
import school.magiccodex.protocol.ManaProtocol;
import school.magiccodex.database.DatabaseSettings;

/** Event driven social state. No global player polling or disk access in packet handlers. */
final class SocialBridge implements Listener,PluginMessageListener,CommandExecutor,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final ManaBridge mana;
    private final StatsService stats;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-friends-io");t.setDaemon(true);return t;});
    private final FriendStore store;
    private final boolean shared;
    private final Map<UUID,Book> books=new HashMap<>();
    private final WhisperTickets tickets=new WhisperTickets();
    private volatile boolean closing;
    private static class Book {List<Entry> entries=List.of();boolean ready,busy,checkingSignal;long sequence,nextAction,nextList,window;int requests;
        boolean allow(long n){if(n-window>=1000){window=n;requests=0;}return ++requests<=10;}}
    private static long now(){return System.nanoTime()/1_000_000;}
    SocialBridge(MagicCodexBridge plugin,ManaBridge mana,StatsService stats)throws Exception{
        this.plugin=plugin;this.mana=mana;this.stats=stats;
        try{var settings=DatabaseSettings.load(plugin.getDataFolder().toPath().resolve("database.properties"));shared=settings.mariaDb();store=io.submit(()->new FriendStore(plugin.getDataFolder().toPath().resolve("friends.db"),settings)).get(10,TimeUnit.SECONDS);}
        catch(Exception e){io.shutdownNow();throw e;}
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,SocialProtocol.REQUEST,this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,SocialProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("친구")).setExecutor(this);
        mana.windCast=this::castOpened;
        for(Player p:Bukkit.getOnlinePlayers())load(p);
        Bukkit.getScheduler().runTaskTimer(plugin,()->tickets.expire(now()),200,200);
    }
    private interface Job<T>{T run()throws Exception;}
    private <T> void work(Job<T> task,Consumer<T> done,Consumer<Exception> fail){
        if(closing)return;
        io.execute(()->{try{T value=task.run();main(()->done.accept(value));}catch(Exception e){plugin.getLogger().warning("친구 저장 처리 실패: "+e);main(()->fail.accept(e));}});
    }
    private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closing)r.run();});}
    private void load(Player p){UUID id=p.getUniqueId();var b=new Book();books.put(id,b);work(()->store.load(id),list->{if(books.get(id)==b){b.entries=list;b.ready=true;deliverFirstFriend(p,b,0);}},e->{});}
    private void deliverFirstFriend(Player p,Book book,int retry){
        if(book.checkingSignal)return;book.checkingSignal=true;
        UUID id=p.getUniqueId();work(()->store.pendingSignal(id),pending->{
            if(!pending||books.get(id)!=book||!p.isOnline()){book.checkingSignal=false;return;}
            if(DiscoveryLink.signal(id,"friend.added",1))work(()->{store.signalDelivered(id);return true;},v->book.checkingSignal=false,e->book.checkingSignal=false);
            else {book.checkingSignal=false;if(retry<3)Bukkit.getScheduler().runTaskLater(plugin,()->{if(books.get(id)==book)deliverFirstFriend(p,book,retry+1);},40);}
        },e->book.checkingSignal=false);
    }
    private String dorm(Player p){return plugin.dorm(p);}
    private boolean visible(Player viewer,Player target){return target!=null&&target.isOnline()&&viewer.canSee(target);}
    private void packet(Player p,Response r){if(p.isOnline()&&p.getListeningPluginChannels().contains(SocialProtocol.RESPONSE))p.sendPluginMessage(plugin,SocialProtocol.RESPONSE,SocialProtocol.encode(r));}
    private void notice(Player p,long seq,String text){packet(p,new Response(SocialProtocol.NOTICE,seq,SocialProtocol.NONE,0,"","",text,0,List.of()));}
    private void snapshot(Player p,long seq,String message){
        var b=books.get(p.getUniqueId());if(b==null||!b.ready){notice(p,seq,"친구 목록을 불러오는 중입니다. 잠시 후 다시 시도해 주세요.");return;}
        var current=new ArrayList<Entry>();
        for(var e:b.entries){var target=Bukkit.getPlayer(e.id());boolean online=visible(p,target);current.add(online?new Entry(e.id(),plugin.names().name(target),dorm(target),true):new Entry(e.id(),plugin.names().name(Bukkit.getOfflinePlayer(e.id()),e.name()),e.dormitory(),false));}
        packet(p,new Response(SocialProtocol.SNAPSHOT,seq,SocialProtocol.NONE,0,"","",message,0,current));
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] data){
        if(!channel.equals(SocialProtocol.REQUEST))return;
        var b=books.get(p.getUniqueId());if(b==null||!b.allow(now()))return;
        Request r;try{r=SocialProtocol.request(data);}catch(IllegalArgumentException e){return;}
        if(r.sequence()<=b.sequence)return;b.sequence=r.sequence();
        long n=now();
        if(r.action()==SocialProtocol.CLOSE)return;
        if(r.action()==SocialProtocol.CANCEL){tickets.cancel(p.getUniqueId(),r.ticket());return;}
        if(!p.hasPermission("magiccodex.friends")){notice(p,r.sequence(),"친구 기능을 사용할 권한이 없습니다.");return;}
        if(r.action()==SocialProtocol.LIST){if(n<b.nextList){notice(p,r.sequence(),"잠시 후 다시 시도해 주세요.");return;}b.nextList=n+1000;
            if(shared)work(()->store.load(p.getUniqueId()),list->{if(books.get(p.getUniqueId())==b){b.entries=list;b.ready=true;snapshot(p,r.sequence(),"");}},e->notice(p,r.sequence(),"친구 목록을 불러오지 못했습니다."));
            else snapshot(p,r.sequence(),"");return;}
        if(r.action()!=SocialProtocol.SEND&&n<b.nextAction){notice(p,r.sequence(),"잠시 후 다시 시도해 주세요.");return;}b.nextAction=n+400;
        if(!b.ready||b.busy){notice(p,r.sequence(),"친구 목록을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");return;}
        switch(r.action()){
            case SocialProtocol.ADD->{if(r.text().isBlank()||r.text().length()>16){notice(p,r.sequence(),"정확한 닉네임을 입력해 주세요.");return;}Player target=plugin.names().resolve(r.text());add(p,target,r.sequence());}
            case SocialProtocol.REMOVE->remove(p,r.target(),r.sequence());
            case SocialProtocol.WHISPER->whisper(p,r.target(),r.sequence());
            case SocialProtocol.SEND->sendMessage(p,r);
            default->{}
        }
    }
    private void add(Player owner,Player target,long seq){
        var b=books.get(owner.getUniqueId());
        if(b==null||!b.ready||b.busy){notice(owner,seq,"친구 목록을 처리하고 있습니다.");return;}
        if(!visible(owner,target)){notice(owner,seq,"접속 중인 플레이어를 찾을 수 없습니다.");return;}
        UUID id=owner.getUniqueId();
        if(id.equals(target.getUniqueId())){notice(owner,seq,"자신을 친구로 추가할 수 없습니다.");return;}
        if(b.entries.stream().anyMatch(e->e.id().equals(target.getUniqueId()))){notice(owner,seq,"이미 친구 목록에 있습니다.");return;}
        if(b.entries.size()>=SocialProtocol.LIMIT){notice(owner,seq,"친구는 최대 50명까지 등록할 수 있습니다.");return;}
        var entry=new Entry(target.getUniqueId(),plugin.names().name(target),dorm(target),false);b.busy=true;
        work(()->store.add(id,entry),added->{
            if(books.get(id)!=b)return;b.busy=false;
            if(!added){notice(owner,seq,"이미 등록되었거나 친구 목록이 가득 찼습니다.");return;}
            var next=new ArrayList<>(b.entries);next.add(entry);b.entries=List.copyOf(next);
            snapshot(owner,seq,entry.name()+"님을 친구로 추가했습니다.");
            deliverFirstFriend(owner,b,0);
        },e->{if(books.get(id)==b){b.busy=false;notice(owner,seq,"저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");}});
    }
    private void remove(Player owner,UUID target,long seq){
        var b=books.get(owner.getUniqueId());if(b.entries.stream().noneMatch(e->e.id().equals(target))){notice(owner,seq,"친구 목록에 없는 플레이어입니다.");return;}b.busy=true;
        work(()->store.remove(owner.getUniqueId(),target),removed->{if(books.get(owner.getUniqueId())!=b)return;b.busy=false;b.entries=b.entries.stream().filter(e->!e.id().equals(target)).toList();tickets.remove(owner.getUniqueId());snapshot(owner,seq,"친구 목록에서 삭제했습니다.");},e->{if(books.get(owner.getUniqueId())==b){b.busy=false;notice(owner,seq,"삭제 내용을 저장하지 못했습니다.");}});
    }
    private boolean isFriend(Player p,UUID target){var b=books.get(p.getUniqueId());return b!=null&&b.ready&&b.entries.stream().anyMatch(e->e.id().equals(target));}
    private void whisper(Player p,UUID id,long seq){
        Player target=Bukkit.getPlayer(id);
        if(!isFriend(p,id)||!visible(p,target)){notice(p,seq,"접속 중인 친구에게만 전언을 보낼 수 있습니다.");return;}
        var ticket=tickets.bind(p.getUniqueId(),id,now());int cooldown=0;boolean fresh=ticket==null;
        if(ticket==null){
            var cast=mana.castWindForFriend(p);
            if(cast.status()!=ManaProtocol.OK){notice(p,seq,ManaBridge.message(cast.status()));return;}
            cooldown=cast.cooldownMillis();ticket=tickets.issue(p.getUniqueId(),id,now());
        }
        packet(p,new Response(SocialProtocol.COMPOSE,seq,id,ticket.token(),plugin.names().name(target),dorm(target),fresh?"cast":"",cooldown,List.of()));
    }
    private void sendMessage(Player sender,Request r){
        String text;try{text=SocialProtocol.cleanMessage(r.text());}catch(IllegalArgumentException e){notice(sender,r.sequence(),"전언은 1~240자의 일반 글자로 입력해 주세요.");return;}
        var target=Bukkit.getPlayer(r.target());
        if(sender.isDead()||!isFriend(sender,r.target())||!visible(sender,target)){notice(sender,r.sequence(),"상대방이 접속 중인지 확인해 주세요.");return;}
        if(!tickets.consume(sender.getUniqueId(),r.target(),r.ticket(),now())){notice(sender,r.sequence(),"전언 시간이 만료되었습니다. 마법을 다시 사용해 주세요.");return;}
        // Literal text only: never dispatch a chat command or parse markup from the message.
        target.sendMessage(Component.text("[바람의 전언] ",NamedTextColor.AQUA).append(Component.text(plugin.names().name(sender)+" → 나: ",NamedTextColor.WHITE)).append(Component.text(text,NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("[바람의 전언] ",NamedTextColor.AQUA).append(Component.text("나 → "+plugin.names().name(target)+": "+text,NamedTextColor.WHITE)));
        packet(target,new Response(SocialProtocol.RECEIVED,0,sender.getUniqueId(),0,plugin.names().name(sender),dorm(sender),text,0,List.of()));
        packet(sender,new Response(SocialProtocol.SENT,r.sequence(),target.getUniqueId(),r.ticket(),plugin.names().name(target),dorm(target),"전언을 보냈습니다.",0,List.of()));
    }
    private void castOpened(Player p){tickets.issue(p.getUniqueId(),SocialProtocol.NONE,now());packet(p,new Response(SocialProtocol.OPEN,0,SocialProtocol.NONE,0,"","","전언을 보낼 친구를 선택해 주세요.",0,List.of()));}
    @EventHandler public void social(StatsSocialRequestEvent e){
        if(e.getAction()!=StatsSocialRequestEvent.Action.FRIEND)return;
        Player p=e.getViewer();var b=books.get(p.getUniqueId());
        if(!p.hasPermission("magiccodex.friends")){e.setResponse("친구 기능을 사용할 권한이 없습니다.");return;}
        if(b==null||now()<b.nextAction){e.setResponse("잠시 후 다시 시도해 주세요.");return;}b.nextAction=now()+400;
        Player target=e.getTarget();Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())add(p,target,0);});e.setResponse("친구 추가 요청을 확인하고 있습니다.");
    }
    @Override public boolean onCommand(CommandSender s,Command command,String label,String[] args){
        if(!(s instanceof Player p)){s.sendMessage("게임 안에서 사용해 주세요.");return true;}
        if(!p.getListeningPluginChannels().contains(SocialProtocol.RESPONSE)){p.sendMessage("최신 Magic Codex 모드가 필요합니다.");return true;}
        if(args.length!=0){p.sendMessage("/친구 로 친구창을 열어 주세요.");return true;}
        packet(p,new Response(SocialProtocol.OPEN,0,SocialProtocol.NONE,0,"","","",0,List.of()));return true;
    }
    @EventHandler public void join(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){
        Player p=e.getPlayer();UUID id=p.getUniqueId();String name=plugin.names().name(p),dorm=dorm(p);books.remove(id);tickets.remove(id);
        work(()->{store.profile(id,name,dorm);return true;},v->{for(var b:books.values())b.entries=b.entries.stream().map(f->f.id().equals(id)?new Entry(id,name,dorm,false):f).toList();},error->{});
    }
    @Override public void close(){
        closing=true;mana.windCast=null;books.clear();tickets.clear();
        io.submit(()->{try{store.close();}catch(Exception e){plugin.getLogger().warning(e.toString());}});io.shutdown();
        try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("친구 저장 작업 종료 대기시간을 초과했습니다.");}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
