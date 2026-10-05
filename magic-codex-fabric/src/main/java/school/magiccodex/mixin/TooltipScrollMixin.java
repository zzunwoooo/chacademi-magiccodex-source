package school.magiccodex.mixin;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mouse.class)
public abstract class TooltipScrollMixin {
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void magiccodex$scroll(long window,double horizontal,double vertical,CallbackInfo ci){
        if(school.magiccodex.client.ItemTooltipRenderer.scroll(window,vertical))ci.cancel();
    }
}
