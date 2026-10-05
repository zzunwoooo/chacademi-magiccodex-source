package school.magiccodex.client;

import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.*;
import net.minecraft.util.hit.EntityHitResult;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.world.ClientWorld;
import school.magiccodex.protocol.TamingProtocol;
import school.magiccodex.protocol.TamingProtocol.State;

/** Smooth local interpolation; only the server can decide eligibility, odds or results. */
public final class TamingClient {
    private TamingClient(){}
    private static ClientWorld stateWorld, observedWorld;
    private static State state;private static long received,nextQuery,nextLoop,lastToken;private static int lastStatus=-1,generation=-1;
    private static HudTextureCache images;private static CodexTypography text;private static PositionedSoundInstance looping;
    private static final UUID NONE=new UUID(0,0);
    private static final Identifier FONT=Identifier.of("magiccodex","hud_bold"),BODY=Identifier.of("magiccodex","label");
    private interface Bytes{byte[] bytes();}
    public record Request(byte[] bytes) implements CustomPayload,Bytes{
        static final Id<Request> ID=new Id<>(Identifier.of(TamingProtocol.REQUEST));static final PacketCodec<RegistryByteBuf,Request> CODEC=codec(Request::new);public Id<Request> getId(){return ID;}
    }
    public record Response(byte[] bytes) implements CustomPayload,Bytes{
        static final Id<Response> ID=new Id<>(Identifier.of(TamingProtocol.RESPONSE));static final PacketCodec<RegistryByteBuf,Response> CODEC=codec(Response::new);public Id<Response> getId(){return ID;}
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf,T> codec(Function<byte[],T> f){return new PacketCodec<>(){
        public T decode(RegistryByteBuf b){if(b.readableBytes()>TamingProtocol.MAX_BYTES){b.skipBytes(b.readableBytes());return f.apply(new byte[0]);}byte[] a=new byte[b.readableBytes()];b.readBytes(a);return f.apply(a);}
        public void encode(RegistryByteBuf b,T value){b.writeBytes(value.bytes());}
    };}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Response.ID,(payload,context)->{try{var s=TamingProtocol.state(payload.bytes());var world=context.client().world;context.client().execute(()->{if(world!=null&&world==context.client().world)accept(s);});}catch(IllegalArgumentException ignored){}});
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(TamingClient::tick);HudRenderCallback.EVENT.register((ctx,t)->render(ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(TamingClient::renderChance);
    }
    private static void accept(State s){
        long n=Util.getMeasuringTimeMs();boolean changed=s.token()!=lastToken||s.status()!=lastStatus;
        if(s.status()==TamingProtocol.TARGET&&!targetRegistered())return;
        state=s;stateWorld=MinecraftClient.getInstance().world;received=n;
        if(changed){stopLoop();if(s.status()==TamingProtocol.CHANNEL){sound("channel_start",.65f);nextLoop=n+450;}
            else if(s.status()==TamingProtocol.SUCCESS){sound("capture_success",.75f);PetClient.invalidateCatalog();}
            else if(s.status()==TamingProtocol.FAIL)sound("capture_fail",.65f);
        }
        lastToken=s.token();lastStatus=s.status();
    }
    private static void tick(MinecraftClient c){
        long n=Util.getMeasuringTimeMs();if(c.player==null||c.world==null){reset();return;}
        if(observedWorld!=c.world){reset();observedWorld=c.world;}
        if(state!=null&&state.status()==TamingProtocol.CHANNEL&&n-received<1800){if(n>=nextLoop){stopLoop();looping=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("magiccodex","taming.channel_loop")),1,.3f);c.getSoundManager().play(looping);nextLoop=n+2400;}}
        else stopLoop();
        if(!targetRegistered()){if(state!=null&&state.status()==TamingProtocol.TARGET)state=null;nextQuery=0;return;}
        if(!ClientPlayNetworking.canSend(Request.ID)||n<nextQuery)return;nextQuery=n+500;
        if(state!=null&&(state.status()==TamingProtocol.CHANNEL||state.status()==TamingProtocol.PENDING)&&n-received<15000)return;
        if(state!=null&&(state.status()==TamingProtocol.SUCCESS||state.status()==TamingProtocol.FAIL||state.status()==TamingProtocol.ERROR)&&n-received<2400)return;
        UUID id=NONE;
        if(c.currentScreen==null&&c.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity)id=hit.getEntity().getUuid();
        // A bounded 12-block client ray extends vanilla's interaction targeting; server independently re-traces it.
        if(id.equals(NONE)&&c.currentScreen==null){var from=c.player.getEyePos();var dir=c.player.getRotationVec(1);var end=from.add(dir.multiply(12));double nearest=144;
            var block=c.world.raycast(new net.minecraft.world.RaycastContext(from,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,c.player));
            if(block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)nearest=from.squaredDistanceTo(block.getPos());
            for(var e:c.world.getOtherEntities(c.player,c.player.getBoundingBox().stretch(dir.multiply(12)).expand(1),e->e instanceof LivingEntity&&e.isAlive())){
                var h=e.getBoundingBox().expand(.25).raycast(from,end);if(h.isPresent()){double d=from.squaredDistanceTo(h.get());if(d<nearest){nearest=d;id=e.getUuid();}}
            }
        }
        ClientPlayNetworking.send(new Request(TamingProtocol.encode(new TamingProtocol.Request(TamingProtocol.QUERY,id))));
    }
    private static void sound(String name,float volume){MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.of("magiccodex","taming."+name)),1,volume));}
    private static void stopLoop(){if(looping!=null){MinecraftClient.getInstance().getSoundManager().stop(looping);looping=null;}}
    private static void close(){if(images!=null){images.close();images=null;}if(text!=null){text.close();text=null;}}
    private static void reset(){state=null;stateWorld=observedWorld=null;received=nextQuery=nextLoop=lastToken=0;lastStatus=-1;stopLoop();close();}
    // Keep the normal entity name; the chance label sits above it rather than replacing it.
    public static boolean hidesVanillaName(int entityId){return false;}
    private static boolean targetRegistered(){return CastingClient.state().registered("taming");}
    public static void bindingsChanged(){
        if(!targetRegistered()&&state!=null&&state.status()==TamingProtocol.TARGET)state=null;
        nextQuery=0;
    }
    private static void renderChance(WorldRenderContext context){
        var c=MinecraftClient.getInstance();
        if(!targetRegistered()||state==null||stateWorld!=c.world||c.world==null||c.player==null
                ||c.currentScreen!=null||c.options.hudHidden||!TamingChanceLabel.visible(state,Util.getMeasuringTimeMs()-received))return;
        var entity=c.world.getEntityById(state.entityId());
        if(!(entity instanceof LivingEntity)||!entity.isAlive()||entity.isInvisibleTo(c.player))return;
        var pos=entity.getLerpedPos(context.tickCounter().getTickDelta(false))
                .add(0,entity.getHeight()+.7,0).subtract(context.camera().getPos());
        if(pos.lengthSquared()>16*16)return;
        var matrices=context.matrixStack();var consumers=context.consumers();
        if(matrices==null||consumers==null)return;
        String label=TamingChanceLabel.text(state.chance());
        float scale=TamingChanceLabel.scale(pos.length());
        matrices.push();
        try{
            // Anchor the bottom of the glyphs above vanilla name tags, even at the far scale.
            matrices.translate(pos.x,pos.y,pos.z);
            matrices.multiply(context.camera().getRotation());
            matrices.scale(scale,-scale,scale);
            // NORMAL keeps terrain depth testing. There is deliberately no screen-edge clamping,
            // fallback card, icon or panel: off-camera/occluded targets remain out of view.
            c.textRenderer.draw(label,-c.textRenderer.getWidth(label)/2f,-c.textRenderer.fontHeight,0xff8deafa,true,
                    matrices.peek().getPositionMatrix(),consumers,TextRenderer.TextLayerType.NORMAL,
                    0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }finally{matrices.pop();}
    }
    private static void panel(DrawContext ctx,String file,int x,int y,int w,int h,int sw,int sh){images.drawTexture(ctx,Identifier.of("magiccodex","textures/gui/taming/"+file+".png"),x,y,0,0,w,h,sw,sh,sw,sh,0xffffffff);}
    private static void label(DrawContext ctx,String value,float x,float y,float size,int color,boolean bold){text.draw(ctx,value.replaceAll("§[0-9a-fk-orA-FK-OR]",""),x,y,size,value.startsWith("§e")?0xffffff55:color,bold?FONT:BODY,true);}
    private static void render(DrawContext ctx){
        var c=MinecraftClient.getInstance();if(state==null||stateWorld!=c.world||state.status()==0||state.status()==TamingProtocol.TARGET||c.world==null||c.player==null||c.options.hudHidden||c.currentScreen!=null)return;
        long age=Util.getMeasuringTimeMs()-received;boolean target=state.status()==TamingProtocol.TARGET;
        if(age>(target?1200:state.status()==TamingProtocol.PENDING?15000:state.status()==TamingProtocol.CHANNEL?1800:2400))return;
        if(generation!=UiResources.generation()){close();generation=UiResources.generation();}if(images==null)images=new HudTextureCache(c,true);if(text==null)text=new CodexTypography(c);
        images.beginFrame();text.beginFrame();ctx.getMatrices().push();
        try{
            int width=c.getWindow().getScaledWidth(),height=c.getWindow().getScaledHeight();float scale=Math.min(1f,Math.min(width/1100f,height/750f));scale=Math.max(.28f,scale);
            if(!target){ctx.getMatrices().translate(width/2f-240*scale,height*.68f,270);ctx.getMatrices().scale(scale,scale,1);panel(ctx,"channel_panel",0,0,480,88,480,88);images.drawCentered(ctx,"magiccodex:textures/gui/taming/taming_icon.png",45,44,50,0xffffffff);
                String message=state.message();float size=Math.min(21,21*360/Math.max(1,text.width(message,21,FONT)));label(ctx,message,270,27,size,0xffe2eef4,true);
                float progress=state.status()==TamingProtocol.CHANNEL?Math.clamp(1-(state.remainingMs()-age)/(float)Math.max(1,state.durationMs()),0,1):state.status()==TamingProtocol.SUCCESS||state.status()==TamingProtocol.PENDING?1:0;
                ctx.fill(88,54,448,62,0xff16313c);ctx.fill(88,54,88+(int)(360*progress),62,state.status()==TamingProtocol.FAIL?0xffb9818c:0xff79dce9);
                if(state.status()==TamingProtocol.SUCCESS){float flash=(float)Math.max(0,1-age/900f);for(int i=0;i<8;i++){double a=i*Math.PI/4;HudMesh.star(ctx,240+(float)Math.cos(a)*(70+age*.1f),44+(float)Math.sin(a)*(25+age*.05f),4+flash*5,((int)(200*flash)<<24)|0xb5faff);}}
            }
        }finally{ctx.getMatrices().pop();images.endFrame();}
    }
}
