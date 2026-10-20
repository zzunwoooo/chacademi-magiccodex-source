package school.magiccodex.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.*;

/** Preserved cast audio only. World VFX is exclusively owned by the server-driven PortableVFX runtime. */
public final class ScreenVfxClient {
    private static PositionedSoundInstance sound;
    private static long soundUntil;
    private ScreenVfxClient() { }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.world==null||client.player==null||!client.player.isAlive()||Util.getMeasuringTimeMs()>=soundUntil)stop();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->stop());
    }
    /** Server preview (/테스트 마법 효과음 → magiccodex:preview "sound &lt;id|stop&gt;"). False when the cue is unknown. */
    static boolean previewSound(String name){if(name.equals("stop")){stop();return true;}var cue=SpellCastSounds.find(name);if(cue==null)return false;play(cue);return true;}
    public static void onCast(String id) { var cue=SpellCastSounds.byId(id);if(cue!=null)play(cue); }
    private static void play(SpellCastSounds.Cue cue) {
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null||!client.player.isAlive())return;
        stop();sound=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("magiccodex",cue.event())),1f,cue.volume());
        soundUntil=Util.getMeasuringTimeMs()+cue.durationMillis()+350;client.getSoundManager().play(sound);
    }
    private static void stop() { if(sound!=null){MinecraftClient.getInstance().getSoundManager().stop(sound);sound=null;}soundUntil=0; }
}
