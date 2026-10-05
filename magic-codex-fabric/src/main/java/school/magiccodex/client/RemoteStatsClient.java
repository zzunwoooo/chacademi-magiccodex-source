package school.magiccodex.client;

import java.util.*;
import java.util.function.*;
import java.io.ByteArrayInputStream;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.StatsProtocol;
import school.magiccodex.protocol.StatsProtocol.*;

/** Remote snapshots never write to ManaClient or the local permission catalog. */
public final class RemoteStatsClient {
    private static View active;
    private static Response pendingOpen;
    private interface Bytes {byte[] bytes();}
    public record Query(byte[] bytes) implements CustomPayload,Bytes {
        public static final Id<Query> ID=new Id<>(Identifier.of(StatsProtocol.REQUEST));
        public static final PacketCodec<RegistryByteBuf,Query> CODEC=codec(Query::new);
        public Id<Query> getId(){return ID;}
    }
    public record Reply(byte[] bytes) implements CustomPayload,Bytes {
        public static final Id<Reply> ID=new Id<>(Identifier.of(StatsProtocol.RESPONSE));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=codec(Reply::new);
        public Id<Reply> getId(){return ID;}
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf,T> codec(Function<byte[],T> factory){return new PacketCodec<>(){
        public T decode(RegistryByteBuf b){int size=b.readableBytes();if(size<4||size>StatsProtocol.MAX_BYTES){b.skipBytes(size);return factory.apply(new byte[0]);}byte[] bytes=new byte[size];b.readBytes(bytes);return factory.apply(bytes);}
        public void encode(RegistryByteBuf b,T value){if(value.bytes().length>StatsProtocol.MAX_BYTES)throw new IllegalArgumentException("Large stats payload");b.writeBytes(value.bytes());}
    };}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Query.ID,Query.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Reply.ID,(payload,context)->{
            try{var response=StatsProtocol.decodeResponse(payload.bytes());context.client().execute(()->receive(response));}catch(IllegalArgumentException ignored){}
        });
        ClientPlayConnectionEvents.JOIN.register((handler,sender,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,c)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.player==null||c.world==null){reset();return;}
            if(pendingOpen!=null&&c.getOverlay()==null){var r=pendingOpen;pendingOpen=null;MagicCodexClient.dismiss();
                var view=new View(r);c.setScreen(new StatsScreen(view));active=view;
            }
            if(active!=null)active.tick(c);
        });
    }
    private static void reset(){active=null;pendingOpen=null;}
    private static void receive(Response r){
        if(r.kind()==StatsProtocol.OPEN){pendingOpen=r;return;}
        // A newer open can arrive in the same tick; keep its latest snapshot.
        if(pendingOpen!=null&&pendingOpen.session()==r.session()&&pendingOpen.target().equals(r.target())){
            if(r.kind()==StatsProtocol.GONE)pendingOpen=null;
            else if(r.kind()==StatsProtocol.SNAPSHOT)pendingOpen=new Response(StatsProtocol.OPEN,r.session(),r.target(),r.message(),r.profile());
            return;
        }
        if(active==null||active.session!=r.session()||!active.id().equals(r.target()))return;
        var c=MinecraftClient.getInstance();if(!(c.currentScreen instanceof StatsScreen s)||!s.displays(active))return;
        if(r.kind()==StatsProtocol.SNAPSHOT)active.update(r.profile());
        else {active.waiting=0;s.showNotice(r.message());if(r.kind()==StatsProtocol.GONE)active.online=false;}
    }
    private static void send(int action,View view){if(ClientPlayNetworking.canSend(Query.ID))ClientPlayNetworking.send(new Query(StatsProtocol.encodeRequest(new Request(action,view.session,view.id()))));}
    public static final class View {
        private final long session;
        private Profile profile;
        private OtherClientPlayerEntity avatar;
        private long received,nextRefresh,nextAction,waiting;
        private boolean online=true,closed;
        private View(Response response){session=response.session();update(response.profile());}
        public UUID id(){return profile.id();}
        public StatsValues values(){var p=profile;return new StatsValues(p.name(),p.circle(),p.dormitory(),p.power(),p.health(),p.mana(),p.maximum(),p.regeneration(),p.armor(),p.learned(),p.total(),p.popularity(),p.haste());}
        public OtherClientPlayerEntity avatar(){return avatar;}
        private void update(Profile value){
            boolean changed=profile==null||!profile.texture().equals(value.texture())||!profile.signature().equals(value.signature());
            profile=value;received=Util.getMeasuringTimeMs();nextRefresh=received+5000;online=true;
            var c=MinecraftClient.getInstance();if(c.world==null)return;
            if(changed||avatar==null){
                GameProfile gameProfile=new GameProfile(profile.id(),"CodexPlayer");
                if(!profile.texture().isEmpty())gameProfile.getProperties().put("textures",new Property("textures",profile.texture(),profile.signature().isEmpty()?null:profile.signature()));
                Supplier<SkinTextures> skins=c.getSkinProvider().getSkinTexturesSupplier(gameProfile);
                avatar=new OtherClientPlayerEntity(c.world,gameProfile){
                    @Override public SkinTextures getSkinTextures(){return skins.get();}
                    @Override public boolean isPartVisible(PlayerModelPart part){return true;}
                };
            }
            EquipmentSlot[] slots={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
            for(int i=0;i<slots.length;i++){
                ItemStack item=ItemStack.EMPTY;byte[] bytes=profile.equipment().get(i);
                if(bytes.length>0)try{var nbt=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(262144));item=ItemStack.fromNbt(c.world.getRegistryManager(),nbt).orElse(ItemStack.EMPTY);}catch(Exception ignored){}
                avatar.equipStack(slots[i],item);
            }
        }
        public String action(StatsSocialActions.Action action){
            long now=Util.getMeasuringTimeMs();var c=MinecraftClient.getInstance();
            if(closed||!online||now-received>15000||!ClientPlayNetworking.canSend(Query.ID))return "상대방 정보를 다시 열어 주세요.";
            if(c.player!=null&&c.player.getUuid().equals(id()))return "자신에게는 사용할 수 없습니다.";
            if(now<nextAction||waiting!=0)return "요청을 확인하고 있습니다.";
            nextAction=now+1000;waiting=now;send(action==StatsSocialActions.Action.FRIEND?StatsProtocol.FRIEND:StatsProtocol.POPULARITY,this);
            return "요청을 확인하고 있습니다.";
        }
        private void tick(MinecraftClient c){
            if(!(c.currentScreen instanceof StatsScreen screen)||!screen.displays(this)){close();return;}
            long now=Util.getMeasuringTimeMs();
            if(waiting!=0&&now-waiting>4000){waiting=0;screen.showNotice("응답이 없습니다. 잠시 후 다시 시도해 주세요.");}
            if(online&&now>=nextRefresh){nextRefresh=now+5000;send(StatsProtocol.REFRESH,this);}
            if(online&&now-received>15000){online=false;screen.showNotice("연결이 끊겼습니다. 스테이터스를 다시 열어 주세요.");}
        }
        public void close(){if(closed)return;closed=true;send(StatsProtocol.CLOSE,this);avatar=null;if(active==this)active=null;}
    }
}
