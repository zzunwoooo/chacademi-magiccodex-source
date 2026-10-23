package school.magiccodex.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import school.magiccodex.client.HiResItemState;
import school.magiccodex.client.ItemIconTextures;

/**
 * Hand, third-person, dropped and framed items all end in ItemRenderState.render. For high-res
 * (&gt;32px) flat icons, draw one smooth card instead of vanilla's per-pixel extruded mesh.
 * require=0: a mapping mismatch leaves items vanilla instead of crashing.
 */
@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements HiResItemState {
    @Shadow boolean leftHand;
    @Shadow int layerCount;
    @Shadow public abstract Transformation getTransformation();

    @Unique private Identifier magiccodex$hiRes;

    @Override public void magiccodex$setHiResModel(Identifier model) { this.magiccodex$hiRes = model; }

    @Inject(method = "clear", at = @At("HEAD"), require = 0)
    private void magiccodex$clearHiRes(CallbackInfo ci) { this.magiccodex$hiRes = null; }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void magiccodex$renderHiRes(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay,
                                        CallbackInfo ci) {
        if (magiccodex$hiRes == null || layerCount == 0) return;
        if (ItemIconTextures.renderWorld(magiccodex$hiRes, matrices, consumers, light, overlay, getTransformation(), leftHand))
            ci.cancel();
    }
}
