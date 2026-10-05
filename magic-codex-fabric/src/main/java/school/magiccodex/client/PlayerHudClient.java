package school.magiccodex.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import org.lwjgl.glfw.GLFW;

public final class PlayerHudClient {
    private static boolean enabled=true;
    private static int frame=1;
    private static PlayerHudRenderer renderer;
    private PlayerHudClient(){}
    public static int frame(){return frame;}
    public static void frame(int value){
        if(value<1 || value>9)throw new IllegalArgumentException("Circle must be 1..9");
        frame=value;
    }
    public static boolean active(){
        var c=MinecraftClient.getInstance();
        return enabled && c.player!=null && c.world!=null && !c.player.isSpectator()
                && c.getCameraEntity()==c.player;
    }
    public static boolean handleAlt(MinecraftClient c,int key,int action){
        if(key!=GLFW.GLFW_KEY_LEFT_ALT && key!=GLFW.GLFW_KEY_RIGHT_ALT)return false;
        if(c.currentScreen instanceof HudCursorScreen)return true;
        if(action==GLFW.GLFW_PRESS && active() && c.currentScreen==null && c.getOverlay()==null
                && c.isWindowFocused() && c.player.isAlive()){
            c.setScreen(new HudCursorScreen());
            return true;
        }
        return false;
    }
    public static void initialize(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
            dispatcher.register(ClientCommandManager.literal("hud")
                .executes(ctx->{ctx.getSource().sendFeedback(Text.literal("/hud frame 1~9 · /hud season 봄|여름|가을|겨울 · /hud on · /hud off"));return 1;})
                .then(seasonCommand())
                .then(TemperatureClient.command())
                .then(ClientCommandManager.literal("frame").then(ClientCommandManager.argument("number",IntegerArgumentType.integer(1,9))
                    .executes(ctx->{frame(IntegerArgumentType.getInteger(ctx,"number"));ctx.getSource().sendFeedback(Text.literal("HUD 프레임: "+frame+"번"));return 1;})))
                .then(ClientCommandManager.literal("on").executes(ctx->{enabled=true;return 1;}))
                .then(ClientCommandManager.literal("off").executes(ctx->{
                    enabled=false;var c=MinecraftClient.getInstance();
                    if(c.currentScreen instanceof HudCursorScreen)c.setScreen(null);
                    close();return 1;
                })));
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.currentScreen instanceof HudCursorScreen){
                long window=c.getWindow().getHandle();
                boolean held=GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_ALT)==GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window,GLFW.GLFW_KEY_RIGHT_ALT)==GLFW.GLFW_PRESS;
                if(!held || !active() || !c.isWindowFocused() || !c.player.isAlive())c.setScreen(null);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,c)->{close();TopMenuClient.reset();frame=1;});
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("magiccodex","player_hud");}
            public void reload(ResourceManager manager){close();}
        });
        HudLayerRegistrationCallback.EVENT.register(drawer->drawer.attachLayerAfter(IdentifiedLayer.SUBTITLES,Identifier.of("magiccodex","player_ui"),(context,ticks)->{
            var c=MinecraftClient.getInstance();
            if(!active() || c.options.hudHidden || (c.currentScreen!=null && !(c.currentScreen instanceof HudCursorScreen)))return;
            renderer().renderStatus(context,c.player,frame,(float)ManaClient.current(),(float)ManaClient.maximum(),c.currentScreen instanceof HudCursorScreen,Util.getMeasuringTimeMs());
            TopMenuClient.render(context,Util.getMeasuringTimeMs());
            TopMenuClient.renderTooltip(context);
        }));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> seasonCommand(){
        var command=ClientCommandManager.literal("season");
        command.executes(ctx->{ctx.getSource().sendFeedback(Text.literal(SeasonClient.status()));return 1;});
        for(var season:HudSeason.values()){
            for(String name:new String[]{season.label(),season.id()}){
                command.then(ClientCommandManager.literal(name).executes(ctx->{
                    TopMenuClient.season(season);
                    ctx.getSource().sendFeedback(Text.literal("HUD 계절: "+season.label()+" (임시 표시)"));return 1;
                }));
            }
        }
        return command;
    }
    private static PlayerHudRenderer renderer(){
        if(renderer==null)renderer=new PlayerHudRenderer(MinecraftClient.getInstance());
        return renderer;
    }
    public static void hotbar(DrawContext context){renderer().renderHotbar(context,MinecraftClient.getInstance().player);}
    public static void verifyRenderer(){if(renderer!=null)renderer.verifyGpuState();TopMenuClient.verify();}
    private static void close(){if(renderer!=null){renderer.close();renderer=null;}TopMenuClient.close();}
}
