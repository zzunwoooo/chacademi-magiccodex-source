package school.magiccodex.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public final class MagicCodexClient implements ClientModInitializer {
    private static boolean visible;
    private static String pendingChat;
    private static KeyBinding openKey;
    private static final CodexShortcut SHORTCUT = new CodexShortcut();
    static boolean matchesOpenKey(int key, int scan) { return openKey != null && openKey.matchesKey(key, scan); }
    public static boolean handleOpenShortcut(MinecraftClient client, int key, int scan, int action) {
        boolean ready = client.world != null && client.player != null && client.currentScreen == null
                && client.getOverlay() == null && client.isWindowFocused();
        var result = SHORTCUT.handle(key, action, matchesOpenKey(key, scan), ready);
        if (result == CodexShortcut.Result.OPEN) visible = true;
        return result != CodexShortcut.Result.PASS;
    }

    @Override
    public void onInitializeClient() {
        UiResources.initialize();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magiccodex.open", GLFW.GLFW_KEY_I, "category.magiccodex"));
        CodexCatalog.initialize();
        PermissionClient.initialize();
        WalletClient.initialize();
        TemperatureClient.initialize();
        SeasonClient.initialize();
        ManaClient.initialize();
        StatsClient.initialize();
        AscensionClient.initialize();
        AppraisalClient.initialize();EnhancementClient.initialize();ReconfigurationClient.initialize();
        RemoteStatsClient.initialize();
        DiscoveryClient.initialize();
        SocialClient.initialize();
        SchoolClient.initialize();
        NicknameClient.initialize();MailboxClient.initialize();ShopClient.initialize();
        TitleClient.initialize();
        QuestClient.initialize();QuestAdminClient.initialize();
        DialogueClient.initialize();DialogueAdminClient.initialize();
        NpcTalkClient.initialize();
        EquipmentClient.initialize();
        PetClient.initialize();
        TamingClient.initialize();
        ShinyClient.initialize();
        CastingClient.initialize();
        PlayerHudClient.initialize();
        ScreenVfxClient.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String alias : new String[]{"UI", "ui"}) {
                dispatcher.register(ClientCommandManager.literal(alias)
                        .executes(context -> setVisible(!visible))
                        .then(ClientCommandManager.literal("on").executes(context -> setVisible(true)))
                        .then(ClientCommandManager.literal("off").executes(context -> setVisible(false)))
                        .then(ClientCommandManager.literal("reload").executes(context -> CodexCatalog.reload(true))));
            }
        });

        // A command runs inside ChatScreen, which closes itself after sending. Open on the
        // next tick so ChatScreen cannot immediately close the new codex screen again.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Opening uses the raw callback above: another mod can own vanilla's key lookup.
            while (openKey.wasPressed()) { /* Drain only; never toggle twice for one press. */ }
            if (!client.isWindowFocused()) SHORTCUT.reset();
            CastingClient.tick(client);
            PermissionClient.tick(client);
            WalletClient.tick(client);
            TemperatureClient.tick(client);
            SeasonClient.tick(client);
            ManaClient.tick(client);
            if (client.world == null || client.player == null) {
                visible = false;
                pendingChat = null;
                SHORTCUT.reset();
                return;
            }
            if (pendingChat != null) {
                String text = pendingChat;
                pendingChat = null;
                if (visible && client.currentScreen instanceof CodexScreen) {
                    client.setScreen(new ChatScreen(text));
                    return;
                }
            }
            if (visible && client.currentScreen == null) {
                client.setScreen(new CodexScreen());
            } else if (!visible && client.currentScreen instanceof CodexScreen) {
                client.setScreen(null);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { dismiss(); SHORTCUT.reset(); });
    }

    private static int setVisible(boolean enabled) {
        visible = enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(enabled
                    ? "마법 도감 열기 · ESC 닫기 · / 키로 명령어 입력"
                    : "마법 도감을 닫았습니다."), false);
        }
        return 1;
    }

    static void dismiss() {
        visible = false;
        pendingChat = null;
    }

    static void openFromMenu() {
        visible=true;
        pendingChat=null;
        MinecraftClient.getInstance().setScreen(new CodexScreen());
    }

    static void requestChat(String initialText) {
        // Defer until after GLFW's character event so '/' is not inserted twice.
        pendingChat = initialText;
    }
}

