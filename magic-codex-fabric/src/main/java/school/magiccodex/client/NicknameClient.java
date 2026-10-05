package school.magiccodex.client;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
import school.magiccodex.protocol.NicknameProtocol;
import school.magiccodex.protocol.NicknameProtocol.*;

public final class NicknameClient {
    private static long sequence,waiting,deadline,identityAt;
    private static NicknameScreen owner;
    private static Response identity;
    private interface Bytes{byte[] bytes();}
    public record Query(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Query> ID=new Id<>(Identifier.of(NicknameProtocol.REQUEST));
        public static final PacketCodec<RegistryByteBuf,Query> CODEC=codec(Query::new);
        public Id<Query> getId(){return ID;}
    }
    public record Reply(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Reply> ID=new Id<>(Identifier.of(NicknameProtocol.RESPONSE));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=codec(Reply::new);
        public Id<Reply> getId(){return ID;}
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf,T> codec(Function<byte[],T> make){return new PacketCodec<>(){
        public T decode(RegistryByteBuf b){int n=b.readableBytes();if(n<4||n>NicknameProtocol.MAX_BYTES){b.skipBytes(n);return make.apply(new byte[0]);}byte[] bytes=new byte[n];b.readBytes(bytes);return make.apply(bytes);}
        public void encode(RegistryByteBuf b,T value){if(value.bytes().length>NicknameProtocol.MAX_BYTES)throw new IllegalArgumentException("nickname size");b.writeBytes(value.bytes());}
    };}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Query.ID,Query.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Reply.ID,(p,c)->{try{var r=NicknameProtocol.response(p.bytes());c.client().execute(()->receive(r));}catch(IllegalArgumentException ignored){}});
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
        ClientCommandRegistrationCallback.EVENT.register((d,a)->{for(String name:new String[]{"닉네임","닉네임설정","codexnickname"})d.register(ClientCommandManager.literal(name).executes(c->{open(null);return 1;}));});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.player==null||c.world==null){reset();return;}long now=Util.getMeasuringTimeMs();
            if(waiting!=0&&now>deadline){waiting=0;if(owner!=null&&c.currentScreen==owner)owner.failed("응답이 늦습니다. 다시 불러온 뒤 저장해 주세요.");owner=null;}
            if(identityAt==0)identityAt=now+1500;
            if(identity==null&&waiting==0&&now>=identityAt&&supported()){identityAt=now+10000;request(null,NicknameProtocol.OPEN,0,0,"");}
        });
    }
    private static void reset(){sequence=waiting=deadline=identityAt=0;owner=null;identity=null;}
    public static boolean supported(){return MinecraftClient.getInstance().getNetworkHandler()!=null&&ClientPlayNetworking.canSend(Query.ID);}
    public static String display(String fallback){return identity==null?fallback:identity.nickname();}
    public static void open(Screen parent){var c=MinecraftClient.getInstance();if(c.player==null||c.world==null)return;MagicCodexClient.dismiss();c.setScreen(new NicknameScreen(parent));}
    static boolean waiting(NicknameScreen screen){return waiting!=0&&owner==screen;}
    static boolean request(NicknameScreen screen,int action,long session,long revision,String value){
        if(!supported()){if(screen!=null)screen.failed("닉네임 서버 연결이 필요합니다.");return false;}
        if(waiting!=0)return false;long seq=++sequence;
        if(action!=NicknameProtocol.CLOSE){waiting=seq;deadline=Util.getMeasuringTimeMs()+7000;owner=screen;}
        ClientPlayNetworking.send(new Query(NicknameProtocol.encode(new Request(action,seq,session,revision,value))));return true;
    }
    private static void receive(Response r){
        var c=MinecraftClient.getInstance();if(c.player==null||!c.player.getUuid().equals(r.owner())||r.sequence()!=waiting)return;
        var screen=owner;waiting=0;owner=null;identity=r;
        if(screen!=null&&c.currentScreen==screen)screen.receive(r);
        else if(r.session()!=0&&supported())request(null,NicknameProtocol.CLOSE,r.session(),r.revision(),"");
    }
    static void closed(NicknameScreen screen,Response data){
        if(owner==screen)owner=null;
        // Pending saves still receive their committed identity; only the UI callback is detached.
        if(waiting==0&&data!=null&&supported())request(null,NicknameProtocol.CLOSE,data.session(),data.revision(),"");
    }
}
