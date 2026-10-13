package school.magiccodex.client;

import net.minecraft.client.MinecraftClient;

/**
 * The HUD pointer: the real mouse while the Alt cursor screen is open, or the magic aim cursor in magic mode.
 * Coordinates are GUI-scaled pixels.
 */
public final class HudPointer {
    private HudPointer() {}

    public static boolean active() {
        var c = MinecraftClient.getInstance();
        return c.currentScreen instanceof HudCursorScreen || AimCursor.aiming();
    }

    public static double x() {
        var c = MinecraftClient.getInstance();
        if (!(c.currentScreen instanceof HudCursorScreen) && AimCursor.aiming()) return AimCursor.screenX();
        var w = c.getWindow();
        return c.mouse.getX() * w.getScaledWidth() / Math.max(1, w.getWidth());
    }

    public static double y() {
        var c = MinecraftClient.getInstance();
        if (!(c.currentScreen instanceof HudCursorScreen) && AimCursor.aiming()) return AimCursor.screenY();
        var w = c.getWindow();
        return c.mouse.getY() * w.getScaledHeight() / Math.max(1, w.getHeight());
    }
}
