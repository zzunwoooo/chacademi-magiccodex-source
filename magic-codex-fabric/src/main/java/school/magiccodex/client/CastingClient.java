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
    /** The saved normal six. During a 큐디치 match the HUD shows {@link KyudichiLoadout} on the same keys instead. */
    private static SpellKeySettings base=new SpellKeySettings();
    private static boolean matchLoadout;
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
                    || !HudPointer.hudVisibleUnder(client.currentScreen)) return;
            if(hud==null) hud=new SkillHudRenderer(client);
            hud.render(ctx,state(),client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight(),Util.getMeasuringTimeMs(),toggleLabel());
        });
    }
    private static void closeHud() { if(hud!=null) { hud.close(); hud=null; } }
    public static void reloadBindings() {
        try { base=SpellKeySettings.load(PATH); refreshBindings(); TamingClient.bindingsChanged(); }
        catch(Exception error) { org.slf4j.LoggerFactory.getLogger("magiccodex").warn("마법 단축키 읽기 실패; 기존 배치 유지",error); }
    }
    public static void saved(SpellKeySettings settings) { base=settings.copy(); refreshBindings(); TamingClient.bindingsChanged(); }
    public static SpellKeySettings base() { return base; }
    public static boolean matchLoadout() { return matchLoadout; }
    /** KyudichiClient: entering / leaving a match as a player. */
    public static void setMatchLoadout(boolean on) { if(matchLoadout!=on){ matchLoadout=on; refreshBindings(); } }
    public static void refreshBindings() {
        if(matchLoadout) STATE.setBindingsDirect(KyudichiLoadout.get().bindings(base.orderedSlots()));
        else STATE.setBindings(base);
    }
    /**
     * Ctrl / chat: a HUD slot dragged onto another. Normal six: the two positions swap (spell and key together, saved).
     * Match: the two match spells swap, keys stay with the positions (saved for the next match too).
     */
    public static void swapHudSlots(int a,int b) {
        if(a==b) return;
        if(matchLoadout) { var l=KyudichiLoadout.get(); l.swap(a,b); l.save(); refreshBindings(); return; }
        var next=base.copy(); next.swapOrder(a,b);
        try { next.save(PATH); base=next; refreshBindings(); }
        catch(java.io.IOException error) {
            var c=MinecraftClient.getInstance();
            if(c.player!=null) c.player.sendMessage(Text.literal("순서를 저장하지 못했습니다: "+error.getMessage()),true);
        }
    }
    private static String toggleLabel() { return toggleKey==null?"Z":toggleKey.getBoundKeyLocalizedText().getString(); }
    public static boolean gameplay(MinecraftClient client) {
        return client.world!=null && client.player!=null && client.player.isAlive() && !client.player.isSpectator()
                && client.currentScreen==null && client.getOverlay()==null && client.isWindowFocused();
    }
    public static boolean handleMouse(MinecraftClient client,int button,int action){
        if(BroomClientLink.reserve(client,client.options.sneakKey.matchesMouse(button),action)){STATE.releaseInputs();return false;}
        int code=KeySettingsLayout.mouseCode(button);
        return code>=0 && handleKey(client,code,0,action);
    }
    public static boolean handleKey(MinecraftClient client,int key,int scan,int action) {
        if(BroomClientLink.reserve(client,client.options.sneakKey.matchesKey(key,scan),action)){STATE.releaseInputs();return false;}
        state();
        boolean toggle=toggleKey!=null && toggleKey.matchesKey(key,scan);
        CastingState.Result result;
        try {
            result=STATE.input(key,action,toggle,gameplay(client) && client.getNetworkHandler()!=null,Util.getMeasuringTimeMs(),
                    ManaClient.supported(),AimTargeting::begin,sender(client));
        } catch(RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("마법 명령 전송 실패",error);
            if(client.player!=null) client.player.sendMessage(Text.literal("마법 명령을 전송하지 못했습니다."),true);
            return true;
        }
        report(client,result);
        return result.consumed();
    }
    /** Cast confirmed from the aim preview (left click). Same checks and messages as a key press. */
    public static void fire(CodexData.Spell spell) {
        var client=MinecraftClient.getInstance();
        if(spell==null || !gameplay(client) || client.getNetworkHandler()==null) return;
        state();
        // Taming traces the creature on the server from the eyes; aim that ray at the shoulder crosshair's creature.
        boolean aimed=(spell.id().equals("taming")||spell.id().equals("rainbow_collar"))&&CrosshairAim.aim(client,25);
        try { report(client,STATE.fire(spell.id(),Util.getMeasuringTimeMs(),ManaClient.supported(),sender(client))); }
        catch(RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("마법 명령 전송 실패",error);
            if(client.player!=null) client.player.sendMessage(Text.literal("마법 명령을 전송하지 못했습니다."),true);
        }
        finally { if(aimed) CrosshairAim.restore(client); }
    }
    private static java.util.function.Consumer<CodexData.Spell> sender(MinecraftClient client) {
        return spell->{
            if(ManaClient.supported())ManaClient.cast(spell);
            else client.getNetworkHandler().sendChatCommand(spell.command());
        };
    }
    private static void report(MinecraftClient client,CastingState.Result result) {
        if(result.kind()==CastingState.Kind.CAST || result.kind()==CastingState.Kind.LOCAL_EFFECT)
            ScreenVfxClient.onCast(result.spellId());
        if(result.kind()==CastingState.Kind.MODE) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),STATE.enabled()?1.2f:0.85f,0.22f));
            if(!STATE.enabled()) AimTargeting.cancel(false);
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
    }
    /** Covers vanilla continuous inputs and queued actions, including focus/GUI-close key resampling. */
    public static boolean suppressed(KeyBinding key) {
        if(key==MinecraftClient.getInstance().options.sneakKey&&BroomClientLink.reserved())return false;
        if(!STATE.enabled() || !gameplay(MinecraftClient.getInstance()) || key==toggleKey) return false;
        if(key.getTranslationKey().startsWith("key.magiccodex.")) return false;
        for(var binding:STATE.bindings()) if(!binding.empty() && KeySettingsLayout.allowed(binding.keyCode()) && (KeySettingsLayout.mouse(binding.keyCode())?key.matchesMouse(KeySettingsLayout.mouseButton(binding.keyCode())):key.matchesKey(binding.keyCode(),0))) return true;
        return false;
    }
    public static void tick(MinecraftClient client) {
        if(toggleKey!=null) while(toggleKey.wasPressed()) { }
        if(!client.isWindowFocused()){STATE.releaseInputs();BroomClientLink.clear();}
        if(client.world==null||client.player==null)BroomClientLink.clear();
        if(client.world==null || client.player==null) STATE.reset();
        else if(!client.player.isAlive() || client.player.isSpectator()) STATE.disable();
        if(!STATE.enabled()) AimTargeting.cancel(false);
    }
}
