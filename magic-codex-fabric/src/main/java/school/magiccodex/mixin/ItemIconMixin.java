package school.magiccodex.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import school.magiccodex.client.ItemIconTextures;

/**
 * Every GUI item draw (inventory, hotbar, magiccodex screens) ends in this private overload.
 * require=0: if a future mapping renames it, items simply stay vanilla instead of crashing.
 */
@Mixin(DrawContext.class)
public abstract class ItemIconMixin {
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void magiccodex$highResIcon(LivingEntity entity, World world, ItemStack stack,
                                        int x, int y, int seed, int z, CallbackInfo ci) {
        if (ItemIconTextures.draw((DrawContext) (Object) this, stack, x, y)) ci.cancel();
    }
}
