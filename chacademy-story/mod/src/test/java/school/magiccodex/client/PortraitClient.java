package school.magiccodex.client;
import net.minecraft.client.gui.GuiGraphics;
public final class PortraitClient {
    public static int draws;
    static boolean ready(){return true;}
    static void drawTurn(GuiGraphics graphics){draws++;}
}
