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
        renderer().render(ctx,w.getScaledWidth(),w.getScaledHeight(),HudPointer.x(),HudPointer.y(),HudPointer.active(),now);
    }
    public static void renderTooltip(DrawContext ctx){
        var c=MinecraftClient.getInstance();var w=c.getWindow();
        renderer().renderTooltip(ctx,w.getScaledWidth(),w.getScaledHeight(),HudPointer.x(),HudPointer.y(),HudPointer.active());
    }
    public static boolean click(double x,double y,int button){
        var c=MinecraftClient.getInstance();
        if(button!=0 || !PlayerHudClient.active() || c.options.hudHidden || !HudPointer.active())return false;
        float scale=PlayerHudLayout.of(c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight()).scale();
        state.update(Util.getMeasuringTimeMs());
        float mx=(float)x/scale-6,my=(float)y/scale-3,base=renderer().baseWidth();
        int hit=state.hit(mx,my,base);
        if(hit==-2){
            if(state.insideDropdown(mx,my,base))return true;
            if(state.group()>=0){state.closeGroup();return true;}
            return false;
        }
        c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1.15f,.23f));
        if(hit==-1){state.toggle(Util.getMeasuringTimeMs());return true;}
        if(TopMenuState.isGroup(hit)){state.toggleGroup(hit,Util.getMeasuringTimeMs());return true;}
        state.closeGroup();
        switch(hit){
            case 110 -> {MagicCodexClient.dismiss();c.setScreen(new StatsScreen());}
            case 111 -> EquipmentClient.open();
            case 112 -> TitleClient.open();
            case 120 -> MagicCodexClient.openFromMenu();
            case 121 -> PetClient.open();
            case 122 -> {MagicCodexClient.dismiss();c.setScreen(new ArtifactCodexScreen(null));}
            case 130 -> SocialClient.open();
            case 131 -> {MagicCodexClient.dismiss();c.setScreen(new PartyScreen());}
            case 132 -> SchoolClient.open();
            case 4 -> QuestClient.open();
            case 5 -> MailboxClient.open(null);
            case 6 -> {MagicCodexClient.dismiss();c.setScreen(new SettingsScreen(null));}
            case 0 -> {if(c.getNetworkHandler()!=null){c.setScreen(null);c.getNetworkHandler().sendChatCommand("캐시샵");}}
            default -> {return false;}
        }
        return true;
    }
    public static void verify(){if(renderer!=null)renderer.verify();}
    public static void close(){state.closeGroup();if(renderer!=null){renderer.close();renderer=null;}}
    public static void reset(){close();state=new TopMenuState();}
}
