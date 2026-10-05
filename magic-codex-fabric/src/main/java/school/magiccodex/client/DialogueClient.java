package school.magiccodex.client;

import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import school.magiccodex.protocol.DialogueProtocol;

public final class DialogueClient {
    private interface Bytes{byte[] bytes();}
    public record Query(byte[] bytes)implements CustomPayload,Bytes{static final Id<Query>ID=new Id<>(Identifier.of(DialogueProtocol.REQUEST));static final PacketCodec<RegistryByteBuf,Query>CODEC=codec(Query::new);public Id<Query>getId(){return ID;}}
    public record Reply(byte[] bytes)implements CustomPayload,Bytes{static final Id<Reply>ID=new Id<>(Identifier.of(DialogueProtocol.RESPONSE));static final PacketCodec<RegistryByteBuf,Reply>CODEC=codec(Reply::new);public Id<Reply>getId(){return ID;}}
    private static<T extends Bytes>PacketCodec<RegistryByteBuf,T>codec(Function<byte[],T>f){return new PacketCodec<>(){public T decode(RegistryByteBuf b){int n=b.readableBytes();if(n<4||n>DialogueProtocol.MAX_BYTES){b.skipBytes(n);return f.apply(new byte[0]);}byte[] bytes=new byte[n];b.readBytes(bytes);return f.apply(bytes);}public void encode(RegistryByteBuf b,T p){b.writeBytes(p.bytes());}};}
    public static void initialize(){PayloadTypeRegistry.playC2S().register(Query.ID,Query.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Reply.ID,(p,c)->{try{var r=DialogueProtocol.response(p.bytes());c.client().execute(()->{
            if(r.close()){if(c.client().currentScreen instanceof DialogueScreen screen&&screen.session().equals(r.session()))screen.serverClose();if(!r.message().isEmpty()&&c.client().player!=null)c.client().player.sendMessage(net.minecraft.text.Text.literal(r.message()),false);return;}
            if(c.client().currentScreen instanceof DialogueScreen screen&&screen.session().equals(r.session()))screen.receive(r);
            else{var parent=r.preview()&&c.client().currentScreen instanceof DialogueAdminScreen?c.client().currentScreen:null;if(parent instanceof DialogueAdminScreen admin)admin.previewOpened();MagicCodexClient.dismiss();c.client().setScreen(new DialogueScreen(r,parent));}
        });}catch(IllegalArgumentException ignored){}});
    }
    static boolean send(String session,int sequence,String choice,boolean close){if(MinecraftClient.getInstance().getNetworkHandler()==null||!ClientPlayNetworking.canSend(Query.ID))return false;ClientPlayNetworking.send(new Query(DialogueProtocol.encode(new DialogueProtocol.Request(session,sequence,choice,close))));return true;}
}
