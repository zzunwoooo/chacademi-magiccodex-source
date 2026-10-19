package school.chacademia.magicmythic;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import school.magiccodex.protocol.ElementProtocol.Reaction;

/**
 * Plays the element reaction VFX pack (PortableVFX pack folder "ElementReactions", one impact system per reaction)
 * through PortableVFX's ServicesManager API. Visual only: never changes gameplay, and a missing PortableVFX,
 * missing pack or exhausted play budget just means no effect.
 *
 * Reflection keeps this plugin free of a compile dependency on PortableVFX (softdepend only). The API is resolved
 * lazily on first use and again after /마법미몹 새로고침.
 */
final class ReactionVfx {
    private static final String SERVICE = "dev.portablevfx.paper.api.PortableVfxService";
    private static final String REQUEST = "dev.portablevfx.paper.api.EffectRequest";
    /** Authored length of the chain-lightning bolt along local +Z (ElementReactions.js BOLT_LEN). */
    static final double BOLT_LENGTH = 3;

    private final MagicMythicPlugin plugin;
    private boolean enabled = true, resolved, warned;
    private String pack = "elementreactions";
    private double range = 48;
    private Object service;
    private Method play;
    private Constructor<?> request;

    ReactionVfx(MagicMythicPlugin plugin) { this.plugin = plugin; reload(); }

    void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("reactions.vfx.enabled", true);
        pack = c.getString("reactions.vfx.pack", "elementreactions").toLowerCase(Locale.ROOT);
        if (!pack.matches("[a-z0-9_-]{1,64}")) pack = "elementreactions";
        range = Math.clamp(c.getDouble("reactions.vfx.range", 48), 8, 128);
        resolved = false; warned = false; service = null;
    }

    /** System id inside the pack (= lower-case reaction name, e.g. ember_curse). */
    static String system(Reaction reaction) { return reaction.name().toLowerCase(Locale.ROOT); }

    /** Body-sized effect at the entity's feet, scaled to its height (authored for 1.8 m). */
    void body(Reaction reaction, LivingEntity entity) { body(reaction, entity.getLocation(), entity.getHeight()); }

    void body(Reaction reaction, Location feet, double height) {
        play(system(reaction), feet, (float) Math.clamp(height / 1.8, 0.5, 1.5), 0, reaction == Reaction.VOID_GUST ? 40 : 30);
    }

    /** Area effect authored at its real radius (steam 2, rainbow 4, sandstorm 3, eclipse 3 blocks): scale 1. */
    void area(Reaction reaction, Location feet, int ticks) { play(system(reaction), feet, 1, 0, ticks); }

    /** Chain-lightning jump: bolt from `from`'s chest toward `to`'s chest (horizontal; scaled to the distance). */
    void bolt(LivingEntity from, LivingEntity to) {
        Location a = from.getLocation().add(0, from.getHeight() * 0.55, 0);
        Location b = to.getLocation().add(0, to.getHeight() * 0.55, 0);
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ(), d = Math.hypot(dx, dz);
        if (d < 0.2) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft yaw: forward = (-sin yaw, 0, cos yaw)
        play(system(Reaction.SHOCK), a, (float) Math.clamp(d / BOLT_LENGTH, 0.15, 2.5), yaw, 20);
    }

    private void play(String system, Location at, float scale, float yaw, int ticks) {
        if (!enabled) return;
        effect("claude:" + pack + "/" + system, at, scale, yaw, ticks, 0xFFFFFF);
    }

    /**
     * Any PortableVFX effect by full id (e.g. "claude:blazepillar/erupt"), tinted (0xFFFFFF = authored colours;
     * colour-variant mobs pass their own tint). Used by mob skills (chatelegraph vfx=...). Same budget/failure rules.
     */
    void effect(String id, Location at, float scale, float yaw, int ticks, int tint) {
        if (id == null || id.isBlank() || at.getWorld() == null || !resolve()) return;
        try {
            Object req = request.newInstance(id, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                    scale, ticks, yaw, 0f, 0f, tint & 0xFFFFFF, 1f, range);
            play.invoke(service, req);
        } catch (InvocationTargetException e) {
            // RejectedExecutionException = PortableVFX play budget exhausted: skip this one quietly.
            if (!(e.getCause() instanceof java.util.concurrent.RejectedExecutionException)) warn(e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
        }
    }

    private boolean resolve() {
        if (resolved) return service != null;
        resolved = true;
        Plugin vfx = Bukkit.getPluginManager().getPlugin("PortableVFX");
        if (vfx == null || !vfx.isEnabled()) return false;
        try {
            ClassLoader loader = vfx.getClass().getClassLoader();
            Class<?> serviceType = Class.forName(SERVICE, true, loader);
            Class<?> requestType = Class.forName(REQUEST, true, loader);
            var reg = Bukkit.getServicesManager().getRegistration(serviceType);
            if (reg == null) return false;
            request = requestType.getConstructor(String.class, String.class, double.class, double.class, double.class,
                    float.class, int.class, float.class, float.class, float.class, int.class, float.class, double.class);
            play = serviceType.getMethod("play", requestType);
            service = reg.getProvider();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
            service = null;
            return false;
        }
    }

    private void warn(Throwable e) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("원소 반응 VFX 재생 실패 (이후 같은 경고는 생략): " + e.getClass().getSimpleName() + " " + e.getMessage());
    }

    String summary() {
        if (!enabled) return "반응 VFX 꺼짐";
        return resolve() ? "반응 VFX claude:" + pack + "/*" : "반응 VFX 대기 (PortableVFX 없음)";
    }
}
