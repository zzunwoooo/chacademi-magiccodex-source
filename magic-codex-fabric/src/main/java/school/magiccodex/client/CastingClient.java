package school.magiccodex.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.List;

public final class CastingClient {
    private CastingClient() {}
    private static final CastingState STATE=new CastingState();
    private static final Path PATH=FabricLoader.getInstance().getConfigDir().resolve("magiccodex/key-settings.yml");
    private static KeyBinding toggleKey;
    private static SkillHudRenderer hud;
    private static long lastNotice;
    public static CastingState state() { STATE.catalog(CodexCatalog.spells()); return STATE; }
    public static boolean enabled() { return STATE.enabled(); }
    public static List<String> permissions() { return state().permissions(); }
    public static void initialize() {
        toggleKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.magiccodex.magic_mode",GLFW.GLFW_KEY_Z,"category.magiccodex"));
        reloadBindings();
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{ STATE.reset(); reloadBindings(); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ STATE.reset(); closeHud(); });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.of("magiccodex","skill_hud"); }
            @Override public void reload(ResourceManager manager) {
                closeHud();
                var screen=MinecraftClient.getInstance().currentScreen;
                if(screen instanceof CodexScreen codex) codex.invalidateImageCache();
                if(screen instanceof KeySettingsScreen keys) keys.invalidateImageCache();
                if(screen instanceof StatsScreen stats) stats.invalidateResources();
                if(screen instanceof SocialScreen social) social.release();
            }
        });
        HudRenderCallback.EVENT.register((ctx,tickCounter)->{
            var client=MinecraftClient.getInstance();
            if(client.world==null || client.player==null || client.options.hudHidden
                    || (client.currentScreen!=null && !(client.currentScreen instanceof HudCursorScreen))) return;
            if(hud==null) hud=new SkillHudRenderer(client);
            hud.render(ctx,state(),client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight(),Util.getMeasuringTimeMs(),toggleLabel());
        });
    }
    private static void closeHud() { if(hud!=null) { hud.close(); hud=null; } }
    public static void reloadBindings() {
        try { STATE.setBindings(SpellKeySettings.load(PATH)); TamingClient.bindingsChanged(); }
        catch(Exception error) { org.slf4j.LoggerFactory.getLogger("magiccodex").warn("마법 단축키 읽기 실패; 기존 배치 유지",error); }
    }
    public static void saved(SpellKeySettings settings) { STATE.setBindings(settings); TamingClient.bindingsChanged(); }
    private static String toggleLabel() { return toggleKey==null?"Z":toggleKey.getBoundKeyLocalizedText().getString(); }
    public static boolean gameplay(MinecraftClient client) {
        return client.world!=null && client.player!=null && client.player.isAlive() && !client.player.isSpectator()
                && client.currentScreen==null && client.getOverlay()==null && client.isWindowFocused();
    }
    public static boolean handleKey(MinecraftClient client,int key,int scan,int action) {
        state();
        boolean toggle=toggleKey!=null && toggleKey.matchesKey(key,scan);
        CastingState.Result result;
        try {
            result=STATE.input(key,action,toggle,gameplay(client) && client.getNetworkHandler()!=null,Util.getMeasuringTimeMs(),
                    ManaClient.supported(),spell->{
                        if(ManaClient.supported())ManaClient.cast(spell);
                        else client.getNetworkHandler().sendChatCommand(spell.command());
                    });
        } catch(RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("마법 명령 전송 실패",error);
            if(client.player!=null) client.player.sendMessage(Text.literal("마법 명령을 전송하지 못했습니다."),true);
            return true;
        }
        if(result.kind()==CastingState.Kind.CAST || result.kind()==CastingState.Kind.LOCAL_EFFECT)
            ScreenVfxClient.onCast(result.spellId());
        if(result.kind()==CastingState.Kind.MODE) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),STATE.enabled()?1.2f:0.85f,0.22f));
            // Remove queued vanilla actions for the newly captured keys without interrupting WASD.
            if(STATE.enabled()) for(var binding:client.options.allKeys) if(suppressed(binding)) {
                binding.setPressed(false); while(binding.wasPressed()) { }
            }
        } else {
            String message=switch(result.kind()) {
                case UNKNOWN -> "마법 권한을 확인하고 있습니다.";
                case LOCKED -> "아직 배우지 않은 마법입니다.";
                case MISSING -> "등록한 마법 파일을 찾을 수 없습니다.";
                case NO_COMMAND -> "이 마법에 사용 명령어가 없습니다.";
                default -> "";
            };
            long now=Util.getMeasuringTimeMs();
            if(!message.isEmpty() && client.player!=null && now-lastNotice>1000) {
                client.player.sendMessage(Text.literal(message),true); lastNotice=now;
            }
        }
        return result.consumed();
    }
    /** Covers vanilla continuous inputs and queued actions, including focus/GUI-close key resampling. */
    public static boolean suppressed(KeyBinding key) {
        if(!STATE.enabled() || !gameplay(MinecraftClient.getInstance()) || key==toggleKey) return false;
        if(key.getTranslationKey().startsWith("key.magiccodex.")) return false;
        for(var binding:STATE.bindings()) if(!binding.empty() && KeySettingsLayout.allowed(binding.keyCode()) && key.matchesKey(binding.keyCode(),0)) return true;
        return false;
    }
    public static void tick(MinecraftClient client) {
        if(toggleKey!=null) while(toggleKey.wasPressed()) { }
        if(!client.isWindowFocused()) STATE.releaseInputs();
        if(client.world==null || client.player==null) STATE.reset();
        else if(!client.player.isAlive() || client.player.isSpectator()) STATE.disable();
    }
}
