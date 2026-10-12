package school.magiccodex.client;

import java.util.List;
import net.minecraft.item.ItemStack;

/** Implemented on vanilla's package-private GhostRecipe.CyclingItem by GhostCyclingItemMixin. */
public interface GhostItemView {
    List<ItemStack> magiccodex$stacks();
    boolean magiccodex$isResult();
}
