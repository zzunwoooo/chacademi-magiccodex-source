package school.magiccodex.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/**
 * Magic-mode aim cursor (third person only).
 * While magic mode is on the view stays where it was when the mode started; the mouse moves a cursor.
 * Holding Alt hands the mouse back to the vanilla view (yaw, limited pitch); releasing Alt re-centres the cursor.
 */
public final class AimCursor {
    /** Pitch range while turning with Alt: keeps the shoulder camera from diving into the character. */
    public static final float FREE_LOOK_MIN_PITCH = -30f, FREE_LOOK_MAX_PITCH = 50f;
    private static double x = Double.NaN, y = Double.NaN; // framebuffer pixels
    private static boolean wasActive, freeLook;

    private AimCursor() {}

    /** Cursor mode: magic mode on, F5 back view, playing (no screen), setting enabled, not riding a broom. */
    public static boolean active() {
        var c = MinecraftClient.getInstance();
        return AimSettings.get().aimCursor && CastingClient.enabled() && c.options.getPerspective() == Perspective.THIRD_PERSON_BACK
            && CastingClient.gameplay(c) && c.getCameraEntity() == c.player
            && !BroomClientLink.riding(); // on a broom the mouse steers; spells then aim at the screen centre
    }

    /** Active and Alt is not held: the cursor, not the view, follows the mouse. */
    public static boolean aiming() { return active() && !altHeld(); }

    public static boolean altHeld() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    /** Alt in cursor mode means "turn the view"; the HUD pointer screen is not opened then. */
    public static boolean capturesAlt() { return active(); }

    /** @return true when the look delta was turned into cursor movement (view stays frozen). */
    public static boolean consumeLook(double dx, double dy) {
        update();
        if (!active()) return false;
        if (altHeld()) { freeLook = true; return false; }
        var c = MinecraftClient.getInstance();
        var w = c.getWindow();
        double speed = AimSettings.get().cursorSpeed, fov = AimView.fovY();
        x = clamp(x + AimMath.cursorStep(dx, w.getFramebufferHeight(), fov, speed), w.getFramebufferWidth());
        y = clamp(y + AimMath.cursorStep(dy, w.getFramebufferHeight(), fov, speed), w.getFramebufferHeight());
        return true;
    }

    /** After a vanilla turn while Alt is held: keep the pitch in a comfortable band. */
    public static void afterLook(ClientPlayerEntity player) {
        if (!freeLook || !active() || player == null) return;
        float pitch = Math.clamp(player.getPitch(), FREE_LOOK_MIN_PITCH, FREE_LOOK_MAX_PITCH);
        if (pitch != player.getPitch()) { player.setPitch(pitch); player.prevPitch = pitch; }
    }

    /** Called every frame (HUD) and on input: enter/leave and Alt release handling. */
    public static void update() {
        boolean now = active();
        if (now && (!wasActive || Double.isNaN(x))) center();
        if (now && freeLook && !altHeld()) { freeLook = false; center(); }
        if (!now) freeLook = false;
        wasActive = now;
    }

    public static void center() {
        var w = MinecraftClient.getInstance().getWindow();
        x = w.getFramebufferWidth() / 2.0;
        y = w.getFramebufferHeight() / 2.0;
    }

    /** Aim point on screen in GUI-scaled pixels: the cursor in cursor mode, otherwise the screen centre. */
    public static float screenX() {
        var w = MinecraftClient.getInstance().getWindow();
        if (!aiming() || Double.isNaN(x)) return w.getScaledWidth() / 2f;
        return (float) (x * w.getScaledWidth() / Math.max(1, w.getFramebufferWidth()));
    }
    public static float screenY() {
        var w = MinecraftClient.getInstance().getWindow();
        if (!aiming() || Double.isNaN(y)) return w.getScaledHeight() / 2f;
        return (float) (y * w.getScaledHeight() / Math.max(1, w.getFramebufferHeight()));
    }

    private static double clamp(double v, int size) { return Math.clamp(v, 2, Math.max(2, size - 2)); }
}
