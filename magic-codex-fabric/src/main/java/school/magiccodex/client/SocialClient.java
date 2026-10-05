package school.magiccodex.client;

import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.SocialProtocol;
import school.magiccodex.protocol.SocialProtocol.*;

public final class SocialClient {
    private static long sequence;
    private static String pendingOpen;
    private record Pending(Screen screen,int action,long until){}
    private static final Map<Long,Pending> pending=new HashMap<>();
    private static List<Entry> cached=List.of();
    private static boolean cacheKnown;
    private static long prefetchAt;
    private static int prefetchAttempts;
    private static long nextListRequest;
    private interface Bytes{byte[] bytes();}
    public record Query(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Query> ID=new Id<>(Identifier.of(SocialProtocol.REQUEST));
        public static final PacketCodec<RegistryByteBuf,Query> CODEC=codec(Query::new);
        public Id<Query> getId(){return ID;}
    }
    public record Reply(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Reply> ID=new Id<>(Identifier.of(SocialProtocol.RESPONSE));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=codec(Reply::new);
        public Id<Reply> getId(){return ID;}
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf,T> codec(Function<byte[],T> f){return new PacketCodec<>(){
        public T decode(RegistryByteBuf b){int n=b.readableBytes();if(n<4||n>SocialProtocol.MAX_BYTES){b.skipBytes(n);return f.apply(new byte[0]);}byte[] bytes=new byte[n];b.readBytes(bytes);return f.apply(bytes);}
        public void encode(RegistryByteBuf b,T v){if(v.bytes().length>SocialProtocol.MAX_BYTES)throw new IllegalArgumentException("Social size");b.writeBytes(v.bytes());}
    };}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Query.ID,Query.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Reply.ID,(p,c)->{try{var r=SocialProtocol.response(p.bytes());c.client().execute(()->receive(r));}catch(IllegalArgumentException ignored){}});
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
        ClientCommandRegistrationCallback.EVENT.register((d,a)->{for(String alias:new String[]{"친구","친구창","codexfriends"})d.register(ClientCommandManager.literal(alias).executes(c->{open();return 1;}));});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.player==null||c.world==null){reset();return;}
            if(pendingOpen!=null&&c.getOverlay()==null){String message=pendingOpen;pendingOpen=null;MagicCodexClient.dismiss();var screen=new FriendsScreen();c.setScreen(screen);screen.notice(message);}
            long now=Util.getMeasuringTimeMs();var it=pending.entrySet().iterator();
            while(it.hasNext()){var e=it.next();if(e.getValue().until()<now){var request=e.getValue();it.remove();if(c.currentScreen==request.screen()&&request.screen() instanceof SocialScreen s){s.busy=false;s.notice("서버 응답이 늦습니다. 잠시 후 다시 시도해 주세요.");}}}
            if(prefetchAt==0)prefetchAt=now+1500;
            if(!cacheKnown && prefetchAttempts<3 && now>=prefetchAt && supported()
                    && !(c.currentScreen instanceof SocialScreen) && !waiting(null,SocialProtocol.LIST)){
                prefetchAttempts++;prefetchAt=now+7000;request(null,SocialProtocol.LIST,SocialProtocol.NONE,0,"");
            }
        });
    }
    private static void reset(){sequence=0;cached=List.of();cacheKnown=false;prefetchAt=nextListRequest=0;prefetchAttempts=0;pending.clear();pendingOpen=null;}
    public static boolean supported(){return MinecraftClient.getInstance().getNetworkHandler()!=null&&ClientPlayNetworking.canSend(Query.ID);}
    public static void open(){pendingOpen="";}
    static List<Entry> entries(){return cached;}
    static boolean cacheKnown(){return cacheKnown;}
    static boolean waiting(Screen s,int action){return pending.values().stream().anyMatch(p->p.screen()==s&&p.action()==action);}
    static boolean request(Screen s,int action,UUID target,long ticket,String text){
        if(!supported()){if(s instanceof SocialScreen social)social.notice("서버에 최신 친구 연동 플러그인이 필요합니다.");return false;}
        if(action==SocialProtocol.LIST && (Util.getMeasuringTimeMs()<nextListRequest || pending.values().stream().anyMatch(p->p.action()==SocialProtocol.LIST)))return false;
        if(pending.size()>=8)return false;long seq=++sequence;
        if(action==SocialProtocol.LIST)nextListRequest=Util.getMeasuringTimeMs()+1100;
        if(action!=SocialProtocol.CANCEL&&action!=SocialProtocol.CLOSE)pending.put(seq,new Pending(s,action,Util.getMeasuringTimeMs()+7000));
        ClientPlayNetworking.send(new Query(SocialProtocol.encode(new Request(action,seq,target,ticket,text))));return true;
    }
    private static void receive(Response r){
        var c=MinecraftClient.getInstance();
        if(r.kind()==SocialProtocol.OPEN){pendingOpen=r.text();return;}
        if(r.kind()==SocialProtocol.RECEIVED){c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.35f,.35f));return;}
        var p=pending.remove(r.sequence());
        if(r.kind()==SocialProtocol.SNAPSHOT){cached=r.entries();cacheKnown=true;}
        if(r.sequence()==0){if(!r.text().isEmpty()){if(c.currentScreen instanceof StatsScreen stats)stats.showNotice(r.text());else if(c.currentScreen instanceof SocialScreen social)social.notice(r.text());else if(c.player!=null)c.player.sendMessage(Text.literal(r.text()),true);}return;}
        if(p==null||c.currentScreen!=p.screen()){
            if(r.kind()==SocialProtocol.COMPOSE)request(null,SocialProtocol.CANCEL,r.target(),r.ticket(),"");return;
        }
        if(p.screen() instanceof SocialScreen screen){if(p.action()!=SocialProtocol.LIST)screen.busy=false;screen.notice(r.text());}
        if(r.kind()==SocialProtocol.SNAPSHOT&&p.screen() instanceof FriendsScreen f)f.updated(p.action());
        if(r.kind()==SocialProtocol.COMPOSE){
            if(r.text().equals("cast")){CastingClient.state().serverResult("wind_message",r.cooldown(),Util.getMeasuringTimeMs());ScreenVfxClient.onCast("wind_message");}
            c.setScreen(new WindMessageScreen(r));
        }else if(r.kind()==SocialProtocol.SENT&&p.screen() instanceof WindMessageScreen w)w.sent();
    }
}
