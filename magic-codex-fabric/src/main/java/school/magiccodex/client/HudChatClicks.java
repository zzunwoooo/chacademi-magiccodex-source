package school.magiccodex.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ChatScreen;

/** With chat open the HUD stays visible; a click on the top-left menu goes to the menu instead of the chat. */
final class HudChatClicks {
    private HudChatClicks() {}
    static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ChatScreen)
                ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !TopMenuClient.click(x, y, button));
        });
    }
}
