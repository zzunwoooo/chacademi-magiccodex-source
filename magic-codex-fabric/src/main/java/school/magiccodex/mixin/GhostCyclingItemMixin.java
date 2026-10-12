package school.magiccodex.mixin;

import java.util.List;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import school.magiccodex.client.GhostItemView;

/** Read-only view so the Chacademi inventory can draw recipe-book ghost items at its own layout. */
@Mixin(targets = "net.minecraft.client.gui.screen.recipebook.GhostRecipe$CyclingItem")
public abstract class GhostCyclingItemMixin implements GhostItemView {
    @Shadow public abstract List<ItemStack> items();
    @Shadow public abstract boolean isResultSlot();
    @Override public List<ItemStack> magiccodex$stacks() { return items(); }
    @Override public boolean magiccodex$isResult() { return isResultSlot(); }
}
