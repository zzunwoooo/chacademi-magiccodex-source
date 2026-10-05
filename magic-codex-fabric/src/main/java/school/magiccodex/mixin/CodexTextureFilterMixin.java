package school.magiccodex.mixin;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Smooth UI/font assets while respecting the requested filter for pixel-art pet textures. */
@Mixin(RenderPhase.Texture.class)
public abstract class CodexTextureFilterMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static TriState magiccodex$smoothOwnTextures(TriState original, Identifier id,
                                                       TriState requested, boolean mipmap) {
        if (!id.getNamespace().equals("magiccodex")) return original;
        String path = id.getPath();
        if (path.startsWith("pet_model/") || path.startsWith("textures/vanilla_shiny/")) {
            return original;
        }
        return TriState.TRUE;
    }
}
