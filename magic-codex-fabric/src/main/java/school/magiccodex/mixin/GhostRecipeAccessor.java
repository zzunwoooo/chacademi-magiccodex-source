package school.magiccodex.mixin;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.screen.recipebook.GhostRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot → GhostRecipe.CyclingItem (seen through {@link school.magiccodex.client.GhostItemView}). */
@Mixin(GhostRecipe.class)
public interface GhostRecipeAccessor {
    @SuppressWarnings("rawtypes")
    @Accessor("items") Reference2ObjectMap magiccodex$items();
}
