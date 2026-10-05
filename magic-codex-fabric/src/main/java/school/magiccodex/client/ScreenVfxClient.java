package school.magiccodex.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.*;

/** Preserved cast audio only. World VFX is exclusively owned by the server-driven PortableVFX runtime. */
public final class ScreenVfxClient {
    private static PositionedSoundInstance sound;
    private static long soundUntil;
    private ScreenVfxClient() { }
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            ClientCommandManager.literal("codexsounddebug").then(ClientCommandManager.argument("spell",StringArgumentType.greedyString())
                .suggests((ctx,builder)->{for(var cue:SpellCastSounds.values())if(cue.id().startsWith(builder.getRemaining()))builder.suggest(cue.id());return builder.buildFuture();})
                .executes(ctx->{String name=StringArgumentType.getString(ctx,"spell");if(name.equals("stop")){stop();return 1;}
                    var cue=SpellCastSounds.find(name);if(cue==null){ctx.getSource().sendError(Text.literal("등록되지 않은 마법 효과음입니다."));return 0;}
                    play(cue);return 1;}))));
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.world==null||client.player==null||!client.player.isAlive()||Util.getMeasuringTimeMs()>=soundUntil)stop();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->stop());
    }
    public static void onCast(String id) { var cue=SpellCastSounds.byId(id);if(cue!=null)play(cue); }
    private static void play(SpellCastSounds.Cue cue) {
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null||!client.player.isAlive())return;
        stop();sound=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("magiccodex",cue.event())),1f,cue.volume());
        soundUntil=Util.getMeasuringTimeMs()+cue.durationMillis()+350;client.getSoundManager().play(sound);
    }
    private static void stop() { if(sound!=null){MinecraftClient.getInstance().getSoundManager().stop(sound);sound=null;}soundUntil=0; }
}
