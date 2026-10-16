package school.magiccodex.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** A transparent mouse-only screen uses vanilla cursor ownership and blocks gameplay clicks. */
public final class HudCursorScreen extends Screen {
    public HudCursorScreen(){super(Text.literal("HUD cursor"));}
    @Override public boolean shouldPause(){return false;}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public boolean mouseClicked(double x,double y,int button){
        return TopMenuClient.click(x,y,button) || PartyHudClient.click(x,y,button) || KyudichiClient.cursorPress(x,y,button) || super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        return PartyHudClient.release(x,y,button) || KyudichiClient.cursorRelease(x,y,button) || super.mouseReleased(x,y,button);
    }
    @Override public void removed(){PartyHudClient.cursorClosed();KyudichiClient.gesture=null;super.removed();}
}
