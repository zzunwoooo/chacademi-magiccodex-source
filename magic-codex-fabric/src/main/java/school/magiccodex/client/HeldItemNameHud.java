package school.magiccodex.client;

import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.Style;

/** Retains vanilla rich text and fade; only its HUD placement and size change. */
public final class HeldItemNameHud {
    private HeldItemNameHud(){}
    public static int draw(DrawContext c,TextRenderer renderer,Text name,int color){
        var window=MinecraftClient.getInstance().getWindow();
        var layout=PlayerHudLayout.of(window.getScaledWidth(),window.getScaledHeight());
        float scale=layout.scale()*1.2f;
        int max=Math.max(1,(int)Math.min(600,(window.getScaledWidth()-32)/scale));
        Text display=name;
        if(renderer.getWidth(name)>max){
            var trimmed=renderer.trimToWidth(name,Math.max(1,max-renderer.getWidth("…")));
            var out=Text.empty();
            trimmed.visit((style,value)->{out.append(Text.literal(value).setStyle(style));return Optional.empty();},Style.EMPTY);
            display=out.append(Text.literal("…").setStyle(name.getStyle()));
        }
        int width=renderer.getWidth(display);
        float x=(window.getScaledWidth()/scale-width)/2;
        // Chips extend 14 logical pixels above hotbarTop-25; keep the whole text above them.
        float y=(layout.hotbarTop()-39)*layout.scale()/scale-14;
        c.getMatrices().push();
        try{
            c.getMatrices().scale(scale,scale,1);
            return c.drawTextWithBackground(renderer,display,Math.round(x),Math.round(y),width,color);
        }finally{c.getMatrices().pop();}
    }
}
