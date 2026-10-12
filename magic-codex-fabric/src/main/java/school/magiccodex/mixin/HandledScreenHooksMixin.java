package school.magiccodex.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import school.magiccodex.client.ChacaInventoryScreen;

/**
 * Chacademi inventory only. Vanilla draws the slot highlight and the carried stack at the
 * vanilla 16px slot grid; that screen draws both at its own layout. Other screens are untouched.
 * require=0: if a mapping changes, the screen still works with vanilla highlight squares.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenHooksMixin {
    @Inject(method = "drawSlotHighlightBack", at = @At("HEAD"), cancellable = true, require = 0)
    private void magiccodex$highlightBack(DrawContext context, CallbackInfo ci) {
        if ((Object) this instanceof ChacaInventoryScreen) ci.cancel();
    }

    @Inject(method = "drawSlotHighlightFront", at = @At("HEAD"), cancellable = true, require = 0)
    private void magiccodex$highlightFront(DrawContext context, CallbackInfo ci) {
        if ((Object) this instanceof ChacaInventoryScreen) ci.cancel();
    }

    @Inject(method = "drawItem(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void magiccodex$cursorStack(DrawContext context, ItemStack stack, int x, int y, String amount, CallbackInfo ci) {
        if ((Object) this instanceof ChacaInventoryScreen screen) { screen.drawCursorStack(context, stack, x, y, amount); ci.cancel(); }
    }
}
