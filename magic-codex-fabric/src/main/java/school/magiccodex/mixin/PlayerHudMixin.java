package school.magiccodex.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import school.magiccodex.client.PlayerHudClient;

@Mixin(InGameHud.class)
public abstract class PlayerHudMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void magiccodex$discovery(DrawContext ctx,RenderTickCounter ticks,CallbackInfo ci){school.magiccodex.client.DiscoveryClient.render(ctx);}
    @Inject(method="render",at=@At("HEAD"))
    private void magiccodex$screenVfx(DrawContext ctx,RenderTickCounter ticks,CallbackInfo ci){
        school.magiccodex.client.TemperatureVfxRenderer.render(ctx);
    }
    @Inject(method="renderHotbar",at=@At("HEAD"),cancellable=true)
    private void magiccodex$hotbar(DrawContext ctx,RenderTickCounter ticks,CallbackInfo ci){
        if(PlayerHudClient.active()){PlayerHudClient.hotbar(ctx);ci.cancel();}
    }
    // Air, mount health and status effects remain available. HUD off restores all vanilla bars.
    @Inject(method={"renderHealthBar","renderFood","renderExperienceBar","renderExperienceLevel"},at=@At("HEAD"),cancellable=true)
    private void magiccodex$replaceVitals(CallbackInfo ci){
        if(PlayerHudClient.active())ci.cancel();
    }
    @Inject(method="renderArmor",at=@At("HEAD"),cancellable=true)
    private static void magiccodex$hideArmor(CallbackInfo ci){
        if(PlayerHudClient.active())ci.cancel();
    }
}
