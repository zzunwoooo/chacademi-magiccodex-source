package school.magiccodex.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import school.magiccodex.client.ChacaInventoryScreen;

/**
 * Survival inventory only: every path that opens the exact vanilla {@code InventoryScreen}
 * (E key, tutorial, other mods) gets the Chacademi screen instead. Creative, containers,
 * crafting tables, shops and subclasses of InventoryScreen are untouched.
 */
@Mixin(MinecraftClient.class)
public abstract class ChacaInventorySwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen magiccodex$chacaInventory(Screen screen) {
        var client = (MinecraftClient) (Object) this;
        return ChacaInventoryScreen.replaces(client, screen) ? new ChacaInventoryScreen(client.player) : screen;
    }
}
