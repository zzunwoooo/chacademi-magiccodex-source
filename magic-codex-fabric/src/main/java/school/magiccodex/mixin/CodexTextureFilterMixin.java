package school.magiccodex.mixin;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla text layers force nearest sampling even for oversampled TTF glyphs. */
@Mixin(RenderPhase.Texture.class)
public abstract class CodexTextureFilterMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static TriState magiccodex$smoothOwnTextures(TriState original, Identifier id,
                                                       TriState requested, boolean mipmap) {
        return id.getNamespace().equals("magiccodex") ? TriState.TRUE : original;
    }
}
