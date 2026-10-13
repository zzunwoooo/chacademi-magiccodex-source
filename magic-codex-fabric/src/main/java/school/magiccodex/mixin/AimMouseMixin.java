package school.magiccodex.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import school.magiccodex.client.AimCursor;

/** Magic mode (third person): mouse motion moves the aim cursor instead of the view, unless Alt is held. */
@Mixin(Mouse.class)
public abstract class AimMouseMixin {
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void magiccodex$aimCursor(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
        if (AimCursor.consumeLook(dx, dy)) return;
        original.call(player, dx, dy);
        AimCursor.afterLook(player);
    }
}
