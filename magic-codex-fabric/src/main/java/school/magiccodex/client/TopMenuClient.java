package school.magiccodex.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;

public final class TopMenuClient {
    private static TopMenuState state=new TopMenuState();
    private static TopMenuRenderer renderer;
    private TopMenuClient(){}
    public static void season(HudSeason value){state.season(value);}
    private static TopMenuRenderer renderer(){
        if(renderer==null)renderer=new TopMenuRenderer(MinecraftClient.getInstance(),state);
        return renderer;
    }
    public static void render(DrawContext ctx,long now){
        var c=MinecraftClient.getInstance();var w=c.getWindow();
        renderer().render(ctx,w.getScaledWidth(),w.getScaledHeight(),
            c.mouse.getX()*w.getScaledWidth()/w.getWidth(),c.mouse.getY()*w.getScaledHeight()/w.getHeight(),
            c.currentScreen instanceof HudCursorScreen,now);
    }
    public static boolean click(double x,double y,int button){
        var c=MinecraftClient.getInstance();
        if(button!=0 || !PlayerHudClient.active() || c.options.hudHidden || !(c.currentScreen instanceof HudCursorScreen))return false;
        float scale=PlayerHudLayout.of(c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight()).scale();
        state.update(Util.getMeasuringTimeMs());
        int hit=state.hit((float)x/scale-6,(float)y/scale-3,renderer().baseWidth());
        if(hit==-2)return false;
        c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1.15f,.23f));
        if(hit==-1){state.toggle(Util.getMeasuringTimeMs());return true;}
        if(hit==1){MagicCodexClient.openFromMenu();return true;}
        if(hit==2){PetClient.open();return true;}
        if(hit==4){SocialClient.open();return true;}
        if(hit==5){QuestClient.open();return true;}
        if(hit==6){SchoolClient.open();return true;}
        if(hit==7){MagicCodexClient.dismiss();c.setScreen(new StatsScreen());return true;}
        if(hit==8){EquipmentClient.open();return true;}
        if(hit==9){TitleClient.open();return true;}
        // Ordinary player command: the server retains permission checks and all side effects.
        if(c.getNetworkHandler()!=null){c.setScreen(null);c.getNetworkHandler().sendChatCommand(TopMenuState.command(hit));}
        return true;
    }
    public static void verify(){if(renderer!=null)renderer.verify();}
    public static void close(){if(renderer!=null){renderer.close();renderer=null;}}
    public static void reset(){close();state=new TopMenuState();}
}
