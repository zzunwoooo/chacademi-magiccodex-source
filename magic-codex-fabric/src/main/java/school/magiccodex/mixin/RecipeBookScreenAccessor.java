package school.magiccodex.mixin;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
/** The custom inventory lays out crafting beside the centered book even in a small window. */
@Mixin(RecipeBookScreen.class)
public interface RecipeBookScreenAccessor { @Accessor("narrow") void magiccodex$setNarrow(boolean value); }
