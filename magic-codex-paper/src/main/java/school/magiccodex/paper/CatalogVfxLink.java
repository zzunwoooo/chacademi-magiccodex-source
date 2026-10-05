package school.magiccodex.paper;

import java.lang.reflect.InvocationTargetException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Narrow optional bridge, invoked inside ManaCasting's existing charge/refund transaction. */
final class CatalogVfxLink {
    private CatalogVfxLink() {}
    static Boolean cast(MagicCodexBridge owner,Player player,String spellId) {
        var plugin=Bukkit.getPluginManager().getPlugin("PortableVFX");
        if(plugin==null||!plugin.isEnabled())return owner.requiresCatalogVisual(spellId)?Boolean.FALSE:null;
        try {
            Object result=plugin.getClass().getMethod("castAuthorizedSpell",Player.class,String.class).invoke(plugin,player,spellId);
            if(result==null)return owner.requiresCatalogVisual(spellId)?Boolean.FALSE:null;
            if(result instanceof Boolean)return (Boolean)result;
            owner.getLogger().warning("PortableVFX catalog bridge returned an invalid result");return false;
        } catch(NoSuchMethodException unavailable) {
            return owner.requiresCatalogVisual(spellId)?Boolean.FALSE:null; // Never fall back to legacy effects for the new catalogue.
        } catch(ReflectiveOperationException | RuntimeException error) {
            Throwable cause=error instanceof InvocationTargetException e?e.getCause():error;
            owner.getLogger().warning("Catalog VFX dispatch failed: "+cause.getClass().getSimpleName());
            return false;
        }
    }
}
