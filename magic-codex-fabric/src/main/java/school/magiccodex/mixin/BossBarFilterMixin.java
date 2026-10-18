package school.magiccodex.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import school.magiccodex.client.TargetHudClient;

/** Mob boss bars (vanilla + MythicMobs) are replaced by the 차카데미 target HUD; other bars still draw. */
@Mixin(BossBarHud.class)
public abstract class BossBarFilterMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<ClientBossBar> magiccodex$withoutMobBars(Map<java.util.UUID, ClientBossBar> bars) {
        var out = new ArrayList<ClientBossBar>(bars.size());
        for (ClientBossBar bar : bars.values()) if (!TargetHudClient.hidesBossBar(bar)) out.add(bar);
        return out;
    }
}
