package school.magiccodex.party;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.network.ProtocolState;
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
    private static final MinecraftChannelIdentifier DISCOVERY=MinecraftChannelIdentifier.from("chacademi:party_discovery");
    private static final MinecraftChannelIdentifier VITALS=MinecraftChannelIdentifier.from("chacademi:party_vitals");
    private static final MinecraftChannelIdentifier HUD=MinecraftChannelIdentifier.from("chacademi:party_hud");
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    static ThreadPoolExecutor newWorker(){return new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"Chacademi-party");t.setDaemon(true);return t;});}
    private final ThreadPoolExecutor worker=newWorker();
    private NetworkChat network;
    private PartyHud hud;
    private final Map<UUID,Long> nextChat=new HashMap<>(),nextCommand=new HashMap<>();
    private final Set<String> trusted=new HashSet<>();
    private PartyBook book;
    private long membershipChangedAt=System.currentTimeMillis();
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
            network=new NetworkChat(trusted);hud=new PartyHud(trusted);
            store=new PartyStore(directory.resolve("parties.json"));book=store.load(capacity);ready=true;
        }catch(Exception e){logger.error("Party initialization failed; commands remain unavailable. Stored state preserved.",e);}
        proxy.getChannelRegistrar().register(IDENTITY,DISCOVERY,VITALS,HUD);
        // 파티 위젯: 0.25초마다 바뀐 파티만 보낸다 (변화 없으면 3초마다 한 번).
        proxy.getScheduler().buildTask(this,()->enqueue(this::flushHud)).repeat(250,TimeUnit.MILLISECONDS).schedule();
        var manager=proxy.getCommandManager();
        manager.register(manager.metaBuilder("party").aliases("파티").plugin(this).build(),new Commands());
        manager.register(manager.metaBuilder("chacachat").aliases("네트워크채팅").plugin(this).build(),new NetworkCommands());
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){
        ready=false;worker.shutdown();try{if(!worker.awaitTermination(10,TimeUnit.SECONDS))logger.warn("Party worker still draining during shutdown");}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Subscribe public void disconnect(DisconnectEvent event){
        UUID id=event.getPlayer().getUniqueId();enqueue(()->{if(network!=null)network.disconnect(id);if(hud!=null)hud.disconnect(id);nextChat.remove(id);nextCommand.remove(id);});
        // Disconnect/server transfer does not change durable membership.
    }
    @Subscribe public void identity(PluginMessageEvent event){
        if(!event.getIdentifier().equals(IDENTITY))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled()); // Never forward client spoofing.
        if(!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player target))return;
        if(!trusted.contains(source.getServerInfo().getName())||source.getPlayer()!=target||target.getCurrentServer().orElse(null)!=source)return;
        byte[] data=event.getData();if(data.length>256)return;
        enqueue(()->{if(network!=null&&proxy.getPlayer(target.getUniqueId()).orElse(null)==target)
            network.accept(target.getUniqueId(),source,target.getCurrentServer().orElse(null),source.getServerInfo().getName(),true,data,System.currentTimeMillis());});
    }
    @Subscribe public void vitals(PluginMessageEvent event){
        if(!event.getIdentifier().equals(VITALS))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled()); // 클라이언트가 보낸 값은 받지 않는다.
        if(!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player target))return;
        if(!trusted.contains(source.getServerInfo().getName())||source.getPlayer()!=target||target.getCurrentServer().orElse(null)!=source)return;
        byte[] data=event.getData().clone();if(data.length!=17)return;
        enqueue(()->{if(hud!=null&&proxy.getPlayer(target.getUniqueId()).orElse(null)==target)
            hud.accept(target.getUniqueId(),source,target.getCurrentServer().orElse(null),source.getServerInfo().getName(),true,data,System.currentTimeMillis());});
    }
    @Subscribe public void hudSpoof(PluginMessageEvent event){
        // 위젯 채널은 프록시만 클라이언트에게 보낸다. 어느 쪽에서 온 것이든 전달하지 않는다.
        if(event.getIdentifier().equals(HUD))event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
    @Subscribe public void serverSwitched(ServerPostConnectEvent event){
        UUID id=event.getPlayer().getUniqueId();long notBefore=System.currentTimeMillis()+1000;
        enqueue(()->{if(hud!=null)hud.resend(id,notBefore);});
    }
    private void flushHud(){
        if(!ready||hud==null)return;long now=System.currentTimeMillis();
        var byParty=new HashMap<UUID,byte[]>();
        for(Player player:proxy.getAllPlayers()){
            // 서버 이동 중(구성 단계)에는 보내지 않는다. 다음 주기에 다시 판단한다.
            if(player.getProtocolState()!=ProtocolState.PLAY||player.getCurrentServer().isEmpty())continue;
            var party=book.party(player.getUniqueId());
            byte[] payload=party==null?PartyHud.EMPTY:byParty.computeIfAbsent(party.id(),k->PartyHud.encode(hudMembers(party,now)));
            var out=hud.due(player.getUniqueId(),payload,now);
            if(out!=null)player.sendPluginMessage(HUD,out);
        }
    }
    private List<PartyHud.Member> hudMembers(PartyBook.Party party,long now){
        var list=new ArrayList<PartyHud.Member>();
        for(UUID id:party.members()){
            var online=proxy.getPlayer(id).orElse(null);
            var server=online==null?null:online.getCurrentServer().orElse(null);
            var identity=server==null||network==null?null:network.identity(id,server,now);
            if(identity!=null)hud.remember(id,identity.nickname(),identity.house());
            String remembered=hud.rememberedName(id);
            String nickname=identity!=null?identity.nickname():remembered!=null?remembered:online!=null?online.getUsername():"오프라인 파티원";
            int house=identity!=null?identity.house():hud.rememberedHouse(id);
            list.add(new PartyHud.Member(id,nickname,house,online!=null,party.leader().equals(id),server==null?null:hud.vitals(id,server,now)));
        }
        return list;
    }
    @Subscribe public void discoveryParty(PluginMessageEvent event){
        if(!event.getIdentifier().equals(DISCOVERY))return;event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!ready||!(event.getSource() instanceof ServerConnection source)||!(event.getTarget() instanceof Player player))return;
        if(!trusted.contains(source.getServerInfo().getName())||source.getPlayer()!=player||player.getCurrentServer().orElse(null)!=source)return;
        byte[] request=event.getData().clone();if(request.length!=25||request[0]!=1)return;
        enqueue(()->{if(player.getCurrentServer().orElse(null)!=source)return;try{
            var input=new DataInputStream(new ByteArrayInputStream(request));input.skipNBytes(17);long observedAt=input.readLong(),now=System.currentTimeMillis();
            var party=book.party(player.getUniqueId());var members=party==null||observedAt<=membershipChangedAt||observedAt>now||now-observedAt>2000?List.<UUID>of():party.members();
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.write(request,0,17);out.writeByte(members.size());for(UUID id:members){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}source.sendPluginMessage(DISCOVERY,bytes.toByteArray());
        }catch(IOException impossible){throw new IllegalStateException(impossible);}});
    }
    private boolean enqueue(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
    private String name(UUID id){
        var online=proxy.getPlayer(id);if(online.isEmpty())return id.toString();
        var identity=network==null?null:network.identity(id,online.get().getCurrentServer().orElse(null),System.currentTimeMillis());
        if(identity!=null)return identity.nickname();
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
    private void commit(PartyBook next)throws IOException{store.save(next);membershipChangedAt=System.currentTimeMillis();book=next;}
    private final class Commands implements SimpleCommand {
        @Override public boolean hasPermission(Invocation i){return !(i.source() instanceof Player p)||p.getPermissionValue("chacademi.party")!=Tristate.FALSE;}
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
    private boolean chatAllowed(Player p,NetworkChat.Channel channel){
        return p.getPermissionValue("chacademi.chat.muted")!=Tristate.TRUE
                &&p.getPermissionValue("chacademi.chat.send")!=Tristate.FALSE
                &&p.getPermissionValue(channel==NetworkChat.Channel.GLOBAL?"chacademi.chat.global":"chacademi.chat.house")!=Tristate.FALSE;
    }
    private Map<UUID,NetworkChat.Connection> onlineConnections(){
        var online=new LinkedHashMap<UUID,NetworkChat.Connection>();
        for(Player player:proxy.getAllPlayers())player.getCurrentServer().ifPresent(s->online.put(player.getUniqueId(),new NetworkChat.Connection(s,s.getServerInfo().getName())));
        return online;
    }
    private final class NetworkCommands implements SimpleCommand {
        @Override public void execute(Invocation invocation){
            if(!(invocation.source() instanceof Player player)){invocation.source().sendMessage(Component.text("플레이어 전용 명령입니다."));return;}
            String[] args=invocation.arguments().clone();
            if(!ready||network==null){notice(player,"통합 채팅을 사용할 수 없습니다.");return;}
            if(!enqueue(()->{
                if(proxy.getPlayer(player.getUniqueId()).orElse(null)!=player)return;
                try{networkMessage(player,args);}catch(IllegalArgumentException e){notice(player,e.getMessage());}
                catch(RuntimeException e){logger.error("Network chat failed; no fallback broadcast",e);notice(player,"통합 채팅을 보내지 못했습니다.");}
            }))notice(player,"채팅 요청이 많습니다. 잠시 후 다시 보내 주세요.");
        }
        @Override public List<String> suggest(Invocation invocation){
            String[] args=invocation.arguments();return args.length<=1?List.of("전체","기숙사").stream().filter(s->args.length==0||s.startsWith(args[0])).toList():List.of();
        }
    }
    private void networkMessage(Player player,String[] args){
        if(args.length<2)throw new IllegalArgumentException("/네트워크채팅 전체|기숙사 <내용>");
        NetworkChat.Channel channel=switch(args[0].toLowerCase(Locale.ROOT)){
            case "g","global","전체"->NetworkChat.Channel.GLOBAL;
            case "h","house","기숙사"->NetworkChat.Channel.HOUSE;
            default->throw new IllegalArgumentException("채널은 전체 또는 기숙사입니다.");
        };
        if(!chatAllowed(player,channel))throw new IllegalArgumentException("현재 이 채널에 채팅을 보낼 수 없습니다.");
        UUID actor=player.getUniqueId();long now=System.currentTimeMillis();
        if(now<nextCommand.getOrDefault(actor,0L))throw new IllegalArgumentException("잠시 후 다시 시도해 주세요.");
        nextCommand.put(actor,now+150);
        String text=PartyBook.message(String.join(" ",Arrays.copyOfRange(args,1,args.length)));
        var online=onlineConnections();var sender=network.sender(actor,online,now);var recipients=network.recipients(channel,actor,online,now);
        NetworkChat.claimChat(nextChat,actor,now);
        String tag=channel==NetworkChat.Channel.GLOBAL?"[전체] ":"[기숙사] ";
        var message=Component.text(tag,NamedTextColor.AQUA)
                .append(icon(sender.server().equals("school")?"\uE114":"\uE115"))
                .append(Component.text(" ")).append(icon(channel==NetworkChat.Channel.GLOBAL?"\uE110":"\uE111"))
                .append(Component.text(" "+sender.nickname()+" : ",NamedTextColor.AQUA))
                .append(Component.text(text,NamedTextColor.WHITE));
        for(UUID id:recipients)proxy.getPlayer(id).ifPresent(target->{
            // A transfer during audience construction must not leak a house message to the new connection.
            var original=online.get(id);long deliveryNow=System.currentTimeMillis();
            if(original==null||target.getCurrentServer().orElse(null)!=original.source()||player.getCurrentServer().orElse(null)!=sender.source())return;
            var currentSender=network.identity(actor,sender.source(),deliveryNow);if(currentSender==null)return;
            if(channel==NetworkChat.Channel.HOUSE){var currentTarget=network.identity(id,original.source(),deliveryNow);if(currentTarget==null||currentSender.house()<0||currentTarget.house()!=currentSender.house())return;}
            target.sendMessage(message);
        });
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
            NetworkChat.claimChat(nextChat,actor,now);
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
