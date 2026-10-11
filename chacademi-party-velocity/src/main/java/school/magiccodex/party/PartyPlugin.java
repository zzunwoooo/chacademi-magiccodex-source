package school.magiccodex.party;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** One authoritative party owner per Velocity. Never intercepts signed public chat. */
public final class PartyPlugin {
    private static final MinecraftChannelIdentifier IDENTITY=MinecraftChannelIdentifier.from("chacademi:party_identity");
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"Chacademi-party");t.setDaemon(true);return t;});
    private final Map<UUID,Identity> identities=new HashMap<>();
    private final Map<UUID,Long> nextChat=new HashMap<>(),nextCommand=new HashMap<>();
    private final Set<String> trusted=new HashSet<>();
    private record Identity(String name,ServerConnection source,long until){}
    private PartyBook book;
    private PartyStore store;
    private volatile boolean ready;

    @Inject public PartyPlugin(ProxyServer proxy,Logger logger,@DataDirectory Path directory){this.proxy=proxy;this.logger=logger;this.directory=directory;}
    @Subscribe public void initialize(ProxyInitializeEvent event){
        try{
            Files.createDirectories(directory);var path=directory.resolve("party.properties");var properties=new Properties();
            if(Files.exists(path)){try(var reader=Files.newBufferedReader(path)){properties.load(reader);}}
            else {properties.setProperty("max-members","8");properties.setProperty("trusted-servers","school,wild");try(var writer=Files.newBufferedWriter(path)){properties.store(writer,"Single Velocity authority; restart after editing.");}}
            int capacity=Integer.parseInt(properties.getProperty("max-members","8"));
            for(var s:properties.getProperty("trusted-servers","school,wild").split(","))if(!s.isBlank())trusted.add(s.strip());
            store=new PartyStore(directory.resolve("parties.json"));book=store.load(capacity);ready=true;
        }catch(Exception e){logger.error("Party initialization failed; commands remain unavailable. Stored state preserved.",e);}
        proxy.getChannelRegistrar().register(IDENTITY);
        var manager=proxy.getCommandManager();
        manager.register(manager.metaBuilder("party").aliases("파티").plugin(this).build(),new Commands());
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){
        ready=false;worker.shutdown();try{if(!worker.awaitTermination(10,TimeUnit.SECONDS))logger.warn("Party worker still draining during shutdown");}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Subscribe public void disconnect(DisconnectEvent event){
        UUID id=event.getPlayer().getUniqueId();enqueue(()->{identities.remove(id);nextChat.remove(id);nextCommand.remove(id);});
        // Disconnect/server transfer does not change durable membership.
    }
    @Subscribe public void identity(PluginMessageEvent event){
        if(!event.getIdentifier().equals(IDENTITY))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled()); // Never forward client spoofing.
        if(!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player target))return;
        if(!trusted.contains(source.getServerInfo().getName())||source.getPlayer()!=target||target.getCurrentServer().orElse(null)!=source)return;
        byte[] data=event.getData();if(data.length>256)return;
        try(var in=new DataInputStream(new ByteArrayInputStream(data))){
            if(in.readUnsignedByte()!=1)return;String name=in.readUTF();
            if(in.available()!=0||name.isBlank()||name.length()>32||name.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c==0xA7))return;
            enqueue(()->{if(target.getCurrentServer().orElse(null)==source)identities.put(target.getUniqueId(),new Identity(name,source,System.currentTimeMillis()+10_000));});
        }catch(IOException ignored){}
    }
    private boolean enqueue(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
    private String name(UUID id){
        var online=proxy.getPlayer(id);if(online.isEmpty())return id.toString();
        var identity=identities.get(id);
        if(identity!=null&&identity.until()>System.currentTimeMillis()&&online.get().getCurrentServer().orElse(null)==identity.source())return identity.name();
        return online.get().getUsername();
    }
    private UUID resolve(String text,boolean onlineOnly){
        try{UUID id=UUID.fromString(text);if(!onlineOnly||proxy.getPlayer(id).isPresent())return id;}catch(IllegalArgumentException ignored){}
        var account=proxy.getPlayer(text);if(account.isPresent())return account.get().getUniqueId();
        var candidates=proxy.getAllPlayers().stream().filter(p->name(p.getUniqueId()).equals(text)).toList();
        if(candidates.size()!=1)throw new IllegalArgumentException("접속한 계정명 또는 중복되지 않는 한글 닉네임을 입력해 주세요. 오프라인 파티원은 목록의 UUID로 지정할 수 있습니다.");
        return candidates.getFirst().getUniqueId();
    }
    private void notice(Player p,String text){p.sendMessage(Component.text("[시스템] ",NamedTextColor.GOLD).append(icon("\uE113")).append(Component.text(" "+text,NamedTextColor.YELLOW)));}
    private Component icon(String glyph){return Component.text(glyph).font(Key.key("magiccodex:chat"));}
    private void broadcast(Collection<UUID> members,String message){for(UUID id:members)proxy.getPlayer(id).ifPresent(p->notice(p,message));}
    private void commit(PartyBook next)throws IOException{store.save(next);book=next;}
    private final class Commands implements SimpleCommand {
        @Override public boolean hasPermission(Invocation i){return !(i.source() instanceof Player p)||p.getPermissionValue("chacademi.party").asBoolean()!=Boolean.FALSE;}
        @Override public void execute(Invocation invocation){
            if(!(invocation.source() instanceof Player p)){invocation.source().sendMessage(Component.text("플레이어 전용 명령입니다."));return;}
            String[] args=invocation.arguments().clone();
            if(!ready){notice(p,"파티 서비스를 사용할 수 없습니다.");return;}
            if(!enqueue(()->{if(proxy.getPlayer(p.getUniqueId()).orElse(null)!=p)return;try{run(p,args);}catch(IllegalArgumentException e){notice(p,e.getMessage());}catch(Exception e){logger.error("Party operation failed; no success acknowledgment sent",e);notice(p,"파티 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");}}))notice(p,"파티 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        @Override public List<String> suggest(Invocation invocation){
            String[] a=invocation.arguments();
            if(a.length<=1){String prefix=a.length==0?"":a[0];return List.of("생성","초대","수락","거절","탈퇴","위임","강퇴","해산","목록","채팅").stream().filter(s->s.startsWith(prefix)).toList();}
            if(a.length==2&&!Set.of("chat","채팅").contains(a[0]))return proxy.getAllPlayers().stream().map(Player::getUsername).filter(n->n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).sorted().limit(30).toList();
            return List.of();
        }
    }
    private void run(Player p,String[] args)throws IOException{
        UUID actor=p.getUniqueId();long now=System.currentTimeMillis();
        if(now<nextCommand.getOrDefault(actor,0L))throw new IllegalArgumentException("잠시 후 다시 시도해 주세요.");
        nextCommand.put(actor,now+150);
        if(args.length==0){notice(p,"/파티 생성 | 초대 <이름> | 수락/거절 [초대한 사람] | 탈퇴 | 위임/강퇴 <이름 또는 UUID> | 해산 | 목록 | 채팅 <내용>");return;}
        String action=switch(args[0].toLowerCase(Locale.ROOT)){case "생성"->"create";case "초대"->"invite";case "수락"->"accept";case "거절"->"deny";case "탈퇴"->"leave";case "위임"->"promote";case "강퇴"->"kick";case "해산"->"disband";case "목록"->"list";case "채팅"->"chat";default->args[0].toLowerCase(Locale.ROOT);};
        if(action.equals("chat")){
            if(args.length<2)throw new IllegalArgumentException("/파티 채팅 <내용>");
            String text=PartyBook.message(String.join(" ",Arrays.copyOfRange(args,1,args.length)));
            var members=book.recipients(actor);
            if(now<nextChat.getOrDefault(actor,0L))throw new IllegalArgumentException("채팅을 너무 빠르게 보내고 있습니다.");
            nextChat.put(actor,now+700);
            var message=Component.text("[파티] ",NamedTextColor.AQUA).append(icon("\uE112")).append(Component.text(" "+name(actor)+" : ",NamedTextColor.AQUA)).append(Component.text(text,NamedTextColor.WHITE));
            for(UUID id:members)proxy.getPlayer(id).ifPresent(member->member.sendMessage(message));
            return;
        }
        if(action.equals("list")){
            if(args.length!=1)throw new IllegalArgumentException("/파티 목록");
            var party=book.require(actor);notice(p,"파티원 "+party.members().size()+"/"+book.capacity);
            for(UUID id:party.members())notice(p,(party.leader().equals(id)?"★ ":"")+name(id)+(proxy.getPlayer(id).isPresent()?"":" (오프라인)"));return;
        }
        var next=book.copy();
        switch(action){
            case "create"->{arity(args,1);next.create(actor);commit(next);notice(p,"파티를 만들었습니다.");}
            case "invite"->{arity(args,2);UUID target=resolve(args[1],true);next.invite(actor,target,now);book=next;notice(p,name(target)+" 님을 초대했습니다.");proxy.getPlayer(target).ifPresent(t->notice(t,name(actor)+" 님의 파티 초대 (2분). /파티 수락 "+p.getUsername()+" 또는 /파티 거절 "+p.getUsername()));}
            case "accept"->{optional(args);UUID inviter=args.length==2?resolve(args[1],false):null;var party=next.accept(actor,inviter,now);commit(next);broadcast(party.members(),name(actor)+" 님이 파티에 참가했습니다.");}
            case "deny"->{optional(args);UUID inviter=args.length==2?resolve(args[1],false):null;var invite=next.deny(actor,inviter,now);book=next;notice(p,"초대를 거절했습니다.");proxy.getPlayer(invite.inviter()).ifPresent(t->notice(t,name(actor)+" 님이 초대를 거절했습니다."));}
            case "leave"->{arity(args,1);var old=next.leave(actor);commit(next);broadcast(old.members(),name(actor)+" 님이 파티에서 나갔습니다.");var after=book.parties.get(old.id());if(after!=null&&old.leader().equals(actor))broadcast(after.members(),name(after.leader())+" 님이 파티장이 되었습니다.");}
            case "promote"->{arity(args,2);UUID target=resolve(args[1],false);var party=next.promote(actor,target);commit(next);broadcast(party.members(),name(target)+" 님에게 파티장을 위임했습니다.");}
            case "kick"->{arity(args,2);UUID target=resolve(args[1],false);var old=next.kick(actor,target);commit(next);broadcast(old.members(),name(target)+" 님이 파티에서 제외되었습니다.");}
            case "disband"->{arity(args,1);var old=next.disband(actor);commit(next);broadcast(old.members(),"파티가 해산되었습니다.");}
            default->throw new IllegalArgumentException("알 수 없는 파티 명령입니다. /파티 로 사용법을 확인하세요.");
        }
    }
    private static void arity(String[] args,int n){if(args.length!=n)throw new IllegalArgumentException("인수가 맞지 않습니다. /파티 로 사용법을 확인하세요.");}
    private static void optional(String[] args){if(args.length>2)throw new IllegalArgumentException("초대한 사람 한 명만 지정해 주세요.");}
}
