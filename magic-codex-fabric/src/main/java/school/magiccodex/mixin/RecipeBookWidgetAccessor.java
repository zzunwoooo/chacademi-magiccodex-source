package school.magiccodex.mixin;

import net.minecraft.client.gui.screen.recipebook.GhostRecipe;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla draws ghost items at slot.x/y; the Chacademi inventory reads the data and draws them itself. */
@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetAccessor {
    @Accessor("ghostRecipe") GhostRecipe magiccodex$ghostRecipe();
}
