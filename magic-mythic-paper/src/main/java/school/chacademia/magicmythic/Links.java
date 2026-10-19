package school.chacademia.magicmythic;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Optional links to other plugins, all by reflection (no compile dependency, nothing breaks when one is missing).
 *  - Mana:  MagicCodexBridge ManaService (ServicesManager). Mana stays owned by the Bridge; we only call its public API.
 *  - VFX:   PortableVFX emitSpellVisualEvent (external:* events for the caster's running cast of that spell).
 *  - Items: ItemsAdder CustomStack (item:mana_potion).
 */
final class Links {
    private Links() {}

    // ------------------------------------------------------------------ mana (MagicCodexBridge)
    private static Object manaService;
    private static Class<?> manaClass;

    private static Object mana() {
        try {
            if (manaService != null) return manaService;
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (!c.getName().equals("school.magiccodex.paper.ManaService")) continue;
                var reg = Bukkit.getServicesManager().getRegistration(c);
                if (reg == null) return null;
                manaClass = c; manaService = reg.getProvider();
                return manaService;
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return null;
    }
    static void resetMana() { manaService = null; manaClass = null; }

    /** Current mana or null (Bridge missing / player not loaded). */
    static Double mana(Player p) {
        Object m = mana();
        if (m == null) return null;
        try {
            Optional<?> snap = (Optional<?>) manaClass.getMethod("snapshot", UUID.class).invoke(m, p.getUniqueId());
            if (snap.isEmpty()) return null;
            Object s = snap.get();
            return ((Number) s.getClass().getMethod("current").invoke(s)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }
    static Double maxMana(Player p) {
        Object m = mana();
        if (m == null) return null;
        try {
            Optional<?> snap = (Optional<?>) manaClass.getMethod("snapshot", UUID.class).invoke(m, p.getUniqueId());
            if (snap.isEmpty()) return null;
            Object s = snap.get();
            return ((Number) s.getClass().getMethod("maximum").invoke(s)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }
    /** Spend mana; false when the Bridge is missing or the player has too little. */
    static boolean consume(Player p, double amount) {
        if (amount <= 0) return true;
        Object m = mana();
        if (m == null) return false;
        try { return Boolean.TRUE.equals(manaClass.getMethod("tryConsume", UUID.class, double.class).invoke(m, p.getUniqueId(), amount)); }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    /** Set by the plugin: 회복봉인 refuses mana gains that go through here (mana potion, conduit, ...). */
    static java.util.function.Predicate<Player> manaSealed;
    static boolean add(Player p, double amount) {
        Object m = mana();
        if (m == null || amount <= 0) return false;
        if (manaSealed != null && manaSealed.test(p)) return false;
        try { manaClass.getMethod("add", UUID.class, double.class).invoke(m, p.getUniqueId(), amount); return true; }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    /** Named regeneration multiplier (1 removes it). Same key never stacks. */
    static boolean regen(UUID id, String key, double multiplier) {
        Object m = mana();
        if (m == null) return false;
        try { manaClass.getMethod("setRegenerationMultiplier", UUID.class, String.class, double.class).invoke(m, id, key, multiplier); return true; }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    /**
     * Cooldown API proposed for the Bridge (docs/handoff/proposals/BRIDGE_SPELL_COOLDOWN_API_20261014.patch).
     * Returns null when this Bridge build does not have it yet, so callers can fall back.
     */
    static Boolean clearCooldown(Player p, String spell) {
        Object m = mana();
        if (m == null) return null;
        try {
            Method method = manaClass.getMethod("clearCooldown", UUID.class, String.class);
            return Boolean.TRUE.equals(method.invoke(m, p.getUniqueId(), spell));
        } catch (NoSuchMethodException missing) { return null; }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    static boolean hasCooldownApi() {
        Object m = mana();
        if (m == null) return false;
        try { manaClass.getMethod("clearCooldown", UUID.class, String.class); return true; }
        catch (NoSuchMethodException e) { return false; }
    }

    /** 마력 (MagicCodexBridge.magicPower), 0 when the Bridge is missing. */
    static double power(Player p) {
        var plugin = Bukkit.getPluginManager().getPlugin("MagicCodexBridge");
        if (plugin == null || !plugin.isEnabled()) return 0;
        try {
            Object r = plugin.getClass().getMethod("magicPower", Player.class).invoke(plugin, p);
            return r instanceof Number n && Double.isFinite(n.doubleValue()) ? Math.max(0, n.doubleValue()) : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return 0; }
    }

    // ------------------------------------------------------------------ VFX (PortableVFX)
    /**
     * Sends a named event (e.g. "external:hit") to the caster's running cast of that spell. Harmless when the spell has
     * no running cast or no phase listens to that event. Visual only - gameplay never waits for it.
     */
    static int vfx(Player caster, String spell, String event, Location at, UUID target) {
        var plugin = Bukkit.getPluginManager().getPlugin("PortableVFX");
        if (plugin == null || !plugin.isEnabled() || caster == null) return 0;
        try {
            Object r = plugin.getClass().getMethod("emitSpellVisualEvent", Player.class, String.class, String.class, Location.class, UUID.class)
                .invoke(plugin, caster, spell, event, at, target);
            return r instanceof Number n ? n.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return 0; }
    }

    // ------------------------------------------------------------------ ItemsAdder
    /** ItemsAdder custom item by namespaced id, or null when ItemsAdder or the item is missing. */
    static ItemStack customItem(String id) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return null;
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("getInstance", String.class).invoke(null, id);
            if (stack == null) return null;
            return (ItemStack) cs.getMethod("getItemStack").invoke(stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return null; }
    }
    /** ItemsAdder id of an item stack ("namespace:id") or null. */
    static String customId(ItemStack item) {
        if (item == null || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return null;
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            return stack == null ? null : (String) cs.getMethod("getNamespacedID").invoke(stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return null; }
    }
}
