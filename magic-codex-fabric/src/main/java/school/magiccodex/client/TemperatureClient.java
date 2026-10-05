package school.magiccodex.client;

import java.util.function.Function;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import school.magiccodex.protocol.TemperatureProtocol;

public final class TemperatureClient {
    private static final TemperatureState STATE=new TemperatureState();
    private static long nextRequest;
    private static float shown=Float.NaN;
    private static String label="—°C";
    private static boolean effects=true;
    private TemperatureClient(){}
    private interface Bytes{byte[] bytes();}
    public record Request(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Request> ID=new Id<>(Identifier.of(TemperatureProtocol.REQUEST));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=codec(Request::new,4);
        public Id<Request> getId(){return ID;}
    }
    public record Response(byte[] bytes) implements CustomPayload,Bytes{
        public static final Id<Response> ID=new Id<>(Identifier.of(TemperatureProtocol.RESPONSE));
        public static final PacketCodec<RegistryByteBuf,Response> CODEC=codec(Response::new,9);
        public Id<Response> getId(){return ID;}
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf,T> codec(Function<byte[],T> factory,int size){
        return new PacketCodec<>(){
            public T decode(RegistryByteBuf buffer){
                if(buffer.readableBytes()!=size){buffer.skipBytes(buffer.readableBytes());return factory.apply(new byte[0]);}
                byte[] bytes=new byte[size];buffer.readBytes(bytes);return factory.apply(bytes);
            }
            public void encode(RegistryByteBuf buffer,T value){
                if(value.bytes().length!=size)throw new IllegalArgumentException("Invalid temperature payload");
                buffer.writeBytes(value.bytes());
            }
        };
    }
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Response.ID,(payload,context)->{
            try{STATE.accept(TemperatureProtocol.decode(payload.bytes()),Util.getMeasuringTimeMs());refreshLabel();}
            catch(IllegalArgumentException ignored){/* Malformed packets cannot activate an effect. */}
        });
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
    }
    public static float current(){return STATE.current(Util.getMeasuringTimeMs());}
    public static String label(){return label;}
    public static boolean previewing(){return STATE.previewing();}
    public static boolean effects(){return effects;}
    private static void refreshLabel(){float value=current();if(Float.compare(value,shown)!=0){shown=value;label=TemperatureState.format(value);}}
    private static void reset(){STATE.reset();nextRequest=0;shown=Float.NaN;label="—°C";TemperatureVfxRenderer.reset();}
    public static void tick(MinecraftClient c){
        if(c.player==null||c.world==null)return;
        long now=Util.getMeasuringTimeMs();
        if(!ClientPlayNetworking.canSend(Request.ID)){STATE.clearServer();nextRequest=0;refreshLabel();return;}
        refreshLabel();
        if(now>=nextRequest&&PlayerHudClient.active()&&!c.options.hudHidden){
            ClientPlayNetworking.send(new Request(TemperatureProtocol.request()));nextRequest=now+20000;
        }
    }
    static LiteralArgumentBuilder<FabricClientCommandSource> command(){
        return ClientCommandManager.literal("temperature")
            .executes(ctx->{ctx.getSource().sendFeedback(Text.literal("온도: "+label+" · /hud temperature <온도> 또는 auto · effects on|off"));return 1;})
            .then(ClientCommandManager.argument("celsius",FloatArgumentType.floatArg(-100,100)).executes(ctx->{
                STATE.preview(FloatArgumentType.getFloat(ctx,"celsius"));refreshLabel();
                ctx.getSource().sendFeedback(Text.literal("온도 연출 미리보기: "+label+" · /hud temperature auto로 서버 값 복귀"));return 1;
            }))
            .then(ClientCommandManager.literal("auto").executes(ctx->{STATE.live();refreshLabel();nextRequest=0;ctx.getSource().sendFeedback(Text.literal("서버 온도 표시로 복귀했습니다."));return 1;}))
            .then(ClientCommandManager.literal("effects")
                .then(ClientCommandManager.literal("on").executes(ctx->{effects=true;ctx.getSource().sendFeedback(Text.literal("온도 화면 효과 ON"));return 1;}))
                .then(ClientCommandManager.literal("off").executes(ctx->{effects=false;TemperatureVfxRenderer.reset();ctx.getSource().sendFeedback(Text.literal("온도 화면 효과 OFF"));return 1;})));
    }
}
