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
    /** 파티 명단 → 게임 서버 (아티팩트·마법의 파티 판정). 프록시만 보낸다. */
    private static final MinecraftChannelIdentifier ROSTER=MinecraftChannelIdentifier.from("chacademi:party_roster");
    /** 파티 창 ↔ 클라이언트 (PartyUi). 클라이언트가 보낸 요청은 이 프록시가 처리하고 게임 서버로 넘기지 않는다. */
    private static final MinecraftChannelIdentifier UI=MinecraftChannelIdentifier.from("chacademi:party_ui");
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    static ThreadPoolExecutor newWorker(){return new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"Chacademi-party");t.setDaemon(true);return t;});}
    private final ThreadPoolExecutor worker=newWorker();
    private NetworkChat network;
    private PartyHud hud;
    private final PartyRoster roster=new PartyRoster();
    private final PartyUi ui=new PartyUi();
    /** 마지막으로 본 스킨 (오프라인 파티원 얼굴용). 작업 스레드 전용. */
    private final Map<UUID,PartyUi.Skin> skins=new HashMap<>();
    private final Map<UUID,Long> nextChat=new HashMap<>(),nextCommand=new HashMap<>();
    private final Set<String> trusted=new HashSet<>();
    private PartyBook book;
    private long membershipChangedAt=System.currentTimeMillis();
    private PartyStore store;
    private volatile boolean ready;
    private KyudichiQueue kyudichi;

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
        proxy.getChannelRegistrar().register(IDENTITY,DISCOVERY,VITALS,HUD,ROSTER,UI);
        // 파티 위젯: 0.25초마다 바뀐 파티만 보낸다 (변화 없으면 3초마다 한 번).
        proxy.getScheduler().buildTask(this,()->enqueue(this::flushHud)).repeat(250,TimeUnit.MILLISECONDS).schedule();
        var manager=proxy.getCommandManager();
        manager.register(manager.metaBuilder("party").aliases("파티").plugin(this).build(),new Commands());
        manager.register(manager.metaBuilder("chacachat").aliases("네트워크채팅").plugin(this).build(),new NetworkCommands());
        // 큐디치 대기열: 같은 작업 스레드에서 파티 정보를 읽는다.
        kyudichi=new KyudichiQueue(proxy,logger,new KyudichiQueue.Host(){
            @Override public boolean enqueue(Runnable task){return PartyPlugin.this.enqueue(task);}
            @Override public PartyBook.Party party(UUID member){return ready?book.party(member):null;}
            // MagicCodex 한글 닉네임 (identity, then the remembered one); the account name only when neither is known
            @Override public String name(UUID id){return person(id,0).nickname();}
            @Override public void notice(Player p,String text){PartyPlugin.this.notice(p,text);}
            @Override public boolean trusted(String server){return trusted.contains(server);}
            @Override public int house(UUID id){return person(id,0).house();}
            @Override public String skin(UUID id){return person(id,0).skin().hash();}
        },this);
        try{kyudichi.initialize(directory);}catch(RuntimeException e){logger.error("Kyudichi queue initialization failed; queue unavailable",e);}
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){
        ready=false;if(kyudichi!=null)kyudichi.shutdown();worker.shutdown();try{if(!worker.awaitTermination(10,TimeUnit.SECONDS))logger.warn("Party worker still draining during shutdown");}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Subscribe public void disconnect(DisconnectEvent event){
        UUID id=event.getPlayer().getUniqueId();enqueue(()->{if(network!=null)network.disconnect(id);if(hud!=null)hud.disconnect(id);roster.disconnect(id);ui.disconnect(id);nextChat.remove(id);nextCommand.remove(id);});
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
        if(event.getIdentifier().equals(HUD)||event.getIdentifier().equals(ROSTER))event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
    @Subscribe public void serverSwitched(ServerPostConnectEvent event){
        UUID id=event.getPlayer().getUniqueId();long notBefore=System.currentTimeMillis()+1000;
        enqueue(()->{if(hud!=null)hud.resend(id,notBefore);roster.resend(id,notBefore);ui.resend(id,notBefore);});
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
            // 같은 파티 명단을 플레이어가 있는 게임 서버에도 보낸다 (신뢰 서버만).
            // 파티 창 상태: 바뀌었을 때만 보낸다.
            var state=ui.stateDue(player.getUniqueId(),uiState(player.getUniqueId(),party,false,now),now);
            if(state!=null)player.sendPluginMessage(UI,state);
            var server=player.getCurrentServer().get();
            if(trusted.contains(server.getServerInfo().getName())){
                var list=roster.due(player.getUniqueId(),PartyRoster.encode(party),now);
                if(list!=null)server.sendPluginMessage(ROSTER,list);
            }
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
            if(a.length<=1){String prefix=a.length==0?"":a[0];return List.of("생성","초대","수락","거절","탈퇴","위임","강퇴","해산","목록","채팅","도움말").stream().filter(s->s.startsWith(prefix)).toList();}
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
        // 인수 없이 /파티 → 파티 창을 연다 (명령어 하위 동작은 그대로 남겨 둔다).
        if(args.length==0){openUi(p,now);return;}
        String action=switch(args[0].toLowerCase(Locale.ROOT)){case "생성"->"create";case "초대"->"invite";case "수락"->"accept";case "거절"->"deny";case "탈퇴"->"leave";case "위임"->"promote";case "강퇴"->"kick";case "해산"->"disband";case "목록"->"list";case "채팅"->"chat";case "도움말"->"help";default->args[0].toLowerCase(Locale.ROOT);};
        if(action.equals("help")){notice(p,"/파티 (창 열기) | 생성 | 초대 <이름> | 수락/거절 [초대한 사람] | 탈퇴 | 위임/강퇴 <이름 또는 UUID> | 해산 | 목록 | 채팅 <내용>");return;}
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
        UUID target=null;
        switch(action){
            case "create","leave","disband"->arity(args,1);
            case "invite"->{arity(args,2);target=resolve(args[1],true);}
            case "promote","kick"->{arity(args,2);target=resolve(args[1],false);}
            case "accept","deny"->{optional(args);target=args.length==2?resolve(args[1],false):null;}
            default->throw new IllegalArgumentException("알 수 없는 파티 명령입니다. /파티 도움말 로 사용법을 확인하세요.");
        }
        notice(p,act(p,action,target,now,false));
    }
    /**
     * 명령어와 파티 창이 함께 쓰는 실제 동작. 실패하면 IllegalArgumentException(사용자에게 보일 문구).
     * @return 실행한 사람에게 보여 줄 문구
     */
    private String act(Player p,String action,UUID target,long now,boolean fromUi)throws IOException{
        UUID actor=p.getUniqueId();var next=book.copy();
        switch(action){
            case "create"->{next.create(actor);commit(next);return "파티를 만들었습니다.";}
            case "invite"->{
                if(target==null||proxy.getPlayer(target).isEmpty())throw new IllegalArgumentException("접속 중인 플레이어만 초대할 수 있습니다.");
                // 파티 창에서는 파티가 없어도 초대할 수 있다: 먼저 파티를 만들고(내가 파티장) 초대한다.
                boolean created=false;
                if(fromUi&&next.party(actor)==null){next.create(actor);created=true;}
                next.invite(actor,target,now);
                if(created)commit(next);else book=next;
                var inviter=person(actor,0);
                proxy.getPlayer(target).ifPresent(t->{
                    notice(t,name(actor)+" 님이 파티에 초대했어요. 알림이나 /파티 창에서 수락할 수 있어요. (2분)");
                    t.sendPluginMessage(UI,PartyUi.invite(inviter));
                });
                return (created?"파티를 만들고 ":"")+name(target)+" 님을 초대했어요.";
            }
            case "accept"->{var party=next.accept(actor,target,now);commit(next);broadcast(party.members(),name(actor)+" 님이 파티에 참가했습니다.");return "파티에 참가했어요.";}
            case "deny"->{var invite=next.deny(actor,target,now);book=next;proxy.getPlayer(invite.inviter()).ifPresent(t->notice(t,name(actor)+" 님이 초대를 거절했습니다."));return "초대를 거절했어요.";}
            case "leave"->{var old=next.leave(actor);commit(next);broadcast(old.members(),name(actor)+" 님이 파티에서 나갔습니다.");var after=book.parties.get(old.id());if(after!=null&&old.leader().equals(actor))broadcast(after.members(),name(after.leader())+" 님이 파티장이 되었습니다.");return "파티에서 나왔어요.";}
            case "promote"->{var party=next.promote(actor,target);commit(next);broadcast(party.members(),name(target)+" 님에게 파티장을 위임했습니다.");return name(target)+" 님이 파티장이 되었어요.";}
            case "kick"->{var old=next.kick(actor,target);commit(next);broadcast(old.members(),name(target)+" 님이 파티에서 제외되었습니다.");return name(target)+" 님을 내보냈어요.";}
            case "disband"->{var old=next.disband(actor);commit(next);broadcast(old.members(),"파티가 해산되었습니다.");return "파티를 해산했어요.";}
            default->throw new IllegalArgumentException("알 수 없는 파티 명령입니다.");
        }
    }
    private static void arity(String[] args,int n){if(args.length!=n)throw new IllegalArgumentException("인수가 맞지 않습니다. /파티 도움말 로 사용법을 확인하세요.");}
    private static void optional(String[] args){if(args.length>2)throw new IllegalArgumentException("초대한 사람 한 명만 지정해 주세요.");}

    // ---------- 파티 창 ----------
    @Subscribe public void uiRequest(PluginMessageEvent event){
        if(!event.getIdentifier().equals(UI))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled()); // 어느 쪽에서 온 것이든 게임 서버·클라이언트로 넘기지 않는다.
        if(!(event.getSource() instanceof Player p))return; // 클라이언트가 보낸 요청만 받는다.
        byte[] data=event.getData().clone();
        if(!enqueue(()->{
            if(proxy.getPlayer(p.getUniqueId()).orElse(null)!=p)return;
            long now=System.currentTimeMillis();
            PartyUi.Request request;
            try{request=PartyUi.decode(data);}catch(IOException malformed){return;}
            if(!ready){p.sendPluginMessage(UI,PartyUi.result(false,"파티 서비스를 사용할 수 없습니다."));return;}
            if(p.getPermissionValue("chacademi.party")==Tristate.FALSE){p.sendPluginMessage(UI,PartyUi.result(false,"파티 기능을 사용할 권한이 없습니다."));return;}
            try{
                switch(request.action()){
                    case PartyUi.OPEN->{var payload=uiState(p.getUniqueId(),book.party(p.getUniqueId()),false,now);ui.sentNow(p.getUniqueId(),payload);p.sendPluginMessage(UI,payload);}
                    case PartyUi.CANDIDATES->{if(ui.candidatesAllowed(p.getUniqueId(),now))p.sendPluginMessage(UI,PartyUi.candidates(candidates(p.getUniqueId(),now)));}
                    default->{
                        if(now<nextCommand.getOrDefault(p.getUniqueId(),0L))throw new IllegalArgumentException("잠시 후 다시 시도해 주세요.");
                        nextCommand.put(p.getUniqueId(),now+150);
                        String action=switch(request.action()){case PartyUi.INVITE->"invite";case PartyUi.ACCEPT->"accept";case PartyUi.DENY->"deny";case PartyUi.LEAVE->"leave";case PartyUi.PROMOTE->"promote";default->"kick";};
                        String message=act(p,action,request.target(),now,true);
                        // 바뀐 상태를 결과보다 먼저 보내 창이 바로 맞춰지게 한다.
                        var payload=uiState(p.getUniqueId(),book.party(p.getUniqueId()),false,now);ui.sentNow(p.getUniqueId(),payload);p.sendPluginMessage(UI,payload);
                        if(request.action()==PartyUi.INVITE)p.sendPluginMessage(UI,PartyUi.candidates(candidates(p.getUniqueId(),now)));
                        p.sendPluginMessage(UI,PartyUi.result(true,message));
                    }
                }
            }catch(IllegalArgumentException e){p.sendPluginMessage(UI,PartyUi.result(false,e.getMessage()));}
            catch(Exception e){logger.error("Party UI operation failed; no success acknowledgment sent",e);p.sendPluginMessage(UI,PartyUi.result(false,"파티 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."));}
        }))p.sendPluginMessage(UI,PartyUi.result(false,"파티 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
    }
    private void openUi(Player p,long now){
        var party=book.party(p.getUniqueId());
        ui.sentNow(p.getUniqueId(),uiState(p.getUniqueId(),party,false,now));
        p.sendPluginMessage(UI,uiState(p.getUniqueId(),party,true,now));
    }
    private byte[] uiState(UUID viewer,PartyBook.Party party,boolean open,long now){
        var members=new ArrayList<PartyUi.Person>();
        if(party!=null)for(UUID id:party.members())members.add(person(id,(proxy.getPlayer(id).isPresent()?PartyUi.P_ONLINE:0)|(party.leader().equals(id)?PartyUi.P_LEADER:0)));
        var invites=new ArrayList<PartyUi.Person>();
        book.expire(now);
        for(var invite:book.invites)if(invite.target().equals(viewer))invites.add(person(invite.inviter(),0));
        return PartyUi.state(open,book.capacity,members,invites);
    }
    /** 초대할 수 있는 사람: 학교·야생(신뢰 서버)에 접속 중이고 파티가 없는 다른 플레이어. */
    private List<PartyUi.Person> candidates(UUID viewer,long now){
        book.expire(now);var mine=book.party(viewer);var list=new ArrayList<PartyUi.Person>();
        for(Player player:proxy.getAllPlayers()){
            UUID id=player.getUniqueId();if(id.equals(viewer)||book.party(id)!=null)continue;
            var server=player.getCurrentServer().orElse(null);if(server==null||!trusted.contains(server.getServerInfo().getName()))continue;
            boolean invited=mine!=null&&book.invites.stream().anyMatch(i->i.party().equals(mine.id())&&i.target().equals(id));
            list.add(person(id,invited?PartyUi.P_INVITED:0));
        }
        list.sort(Comparator.comparing(PartyUi.Person::nickname,String.CASE_INSENSITIVE_ORDER));
        return list.size()>PartyUi.MAX_CANDIDATES?list.subList(0,PartyUi.MAX_CANDIDATES):list;
    }
    private PartyUi.Person person(UUID id,int flags){
        var online=proxy.getPlayer(id).orElse(null);long now=System.currentTimeMillis();
        var identity=online==null||network==null?null:network.identity(id,online.getCurrentServer().orElse(null),now);
        if(identity!=null&&hud!=null)hud.remember(id,identity.nickname(),identity.house());
        String remembered=hud==null?null:hud.rememberedName(id);
        String nickname=identity!=null?identity.nickname():remembered!=null?remembered:online!=null?online.getUsername():"오프라인 파티원";
        int house=identity!=null?identity.house():hud==null?-1:hud.rememberedHouse(id);
        if(online!=null){
            for(var property:online.getGameProfile().getProperties())
                if(property.getName().equals("textures")){var skin=PartyUi.skin(property.getValue());if(!skin.hash().isEmpty())skins.put(id,skin);break;}
        }
        return new PartyUi.Person(id,nickname,house,skins.getOrDefault(id,PartyUi.Skin.NONE),flags);
    }
}
