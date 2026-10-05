package school.magiccodex.client;

import java.awt.Font;
import java.util.Map;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.resource.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.*;
import net.minecraft.util.*;

/** Session-owned screen resources. Closing a screen never destroys shared GPU work. */
public final class UiResources {
    private static HudTextureCache images;
    private static CodexTypography text;
    private static int generation;
    private UiResources(){}
    public static HudTextureCache images(){if(images==null)images=new HudTextureCache(MinecraftClient.getInstance(),true);return images;}
    public static CodexTypography text(){if(text==null)text=new CodexTypography(MinecraftClient.getInstance());return text;}
    public static int generation(){return generation;}
    public static void reset(){generation++;ItemTooltipRenderer.reset();ItemIconTextures.reset();if(images!=null)images.close();if(text!=null)text.close();images=null;text=null;}
    static void initialize(){
        ItemIconTextures.initialize();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleResourceReloadListener<Map<String,Font>>(){
            public Identifier getFabricId(){return Identifier.of("magiccodex","screen_resources");}
            public CompletableFuture<Map<String,Font>> load(ResourceManager manager,Executor executor){return CompletableFuture.supplyAsync(()->CodexTypography.loadFonts(manager),executor);}
            public CompletableFuture<Void> apply(Map<String,Font> fonts,ResourceManager manager,Executor executor){return CompletableFuture.runAsync(()->{reset();CodexTypography.installFonts(fonts);},executor);}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(c->reset());
        // Warm only common panels; spell icons are loaded on demand, never all 200 at once.
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.world==null||c.player==null||c.getOverlay()!=null||c.currentScreen!=null)return;
            var cache=images();cache.beginFrame();
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/social/friends_panel.png"),0,0,1340,1174);
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/social/wind_message_panel.png"),0,0,2048,768);
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/codex_base.png"),0,0,1672,941);
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/stats_panel_clean.png"),0,0,1448,1086);
            StatIcons.warm(cache);
            ItemTooltipRenderer.warm(cache);
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/school/house_points.png"),0,0,1672,941);
            cache.prepareRegion(Identifier.of("magiccodex","textures/gui/equipment/panel.png"),0,0,1672,941);
            cache.endFrame();
        });
    }
    /** First-open progress covers incomplete artwork, while the game keeps rendering. */
    static final class Entrance {
        private boolean ready,waited;private int epoch=-1;private long fadeAt;
        boolean ready(){return ready&&epoch==generation;}
        void draw(DrawContext c,int w,int h){
            if(epoch!=generation){epoch=generation;ready=false;waited=false;fadeAt=0;}
            long now=Util.getMeasuringTimeMs();
            if(!ready && images().missedThisFrame())waited=true;
            if(!ready && !images().missedThisFrame()){ready=true;fadeAt=waited?now:now-140;}
            float opacity=ready?Math.max(0,1-(now-fadeAt)/140f):1;
            if(opacity<=0)return;
            c.getMatrices().push();c.getMatrices().translate(0,0,600);
            c.fill(0,0,w,h,((int)(244*opacity)<<24)|0x08141F);
            String label="화면을 준비하고 있어요";
            var renderer=MinecraftClient.getInstance().textRenderer;
            c.drawText(renderer,label,(w-renderer.getWidth(label))/2,h/2+13,((int)(255*opacity)<<24)|0xE6E0CA,false);
            for(int i=0;i<3;i++){float pulse=(float)(.35+.65*(.5+.5*Math.sin(now/180.0-i*1.2)));int color=((int)(255*opacity*pulse)<<24)|0x8FD9DE;HudMesh.disk(c,w/2f-12+i*12,h/2f-5,2.5f,color);}
            c.getMatrices().pop();
        }
    }
}
