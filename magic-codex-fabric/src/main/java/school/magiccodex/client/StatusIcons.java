package school.magiccodex.client;

import net.minecraft.client.gui.DrawContext;
import school.magiccodex.protocol.ElementProtocol;
import school.magiccodex.protocol.StatusProtocol;

/**
 * Round status icon of the owner's reference sheet (2026-10-19): dark disc, faint ring in the status color,
 * bright arc = time left (from 12 o'clock), symbol PNG in the middle. Used above heads, on the target HUD and
 * on the player's own HUD. Element marks use the same shape with their element PNG.
 */
final class StatusIcons {
    private StatusIcons() {}

    static String path(StatusProtocol.Status s) { return "magiccodex:textures/status/" + s.id + ".png"; }

    /** fraction: time left 0..1 (1 for no end); alpha 0..1. Returns the drawn diameter. */
    static void draw(DrawContext ctx, HudTextureCache images, String icon, int rgb, float cx, float cy, float diameter,
                     float fraction, float alpha, float symbolScale) {
        float r = diameter / 2;
        float ring = Math.max(1.2f, diameter * .075f);
        HudMesh.arc(ctx, cx, cy, r * 1.06f, diameter * .16f, 0, (float) (Math.PI * 2), argb(rgb, .12f * alpha));   // soft glow
        HudMesh.disk(ctx, cx, cy, r * .92f, argb(0x0E1626, .82f * alpha));                                       // disc
        HudMesh.arc(ctx, cx, cy, r * .86f, ring, 0, (float) (Math.PI * 2), argb(rgb, .30f * alpha));              // track
        float f = Math.clamp(fraction, 0, 1);
        if (f > 0) HudMesh.arc(ctx, cx, cy, r * .86f, ring * 1.1f, (float) (-Math.PI / 2), (float) (Math.PI * 2 * f), argb(lighten(rgb), alpha));
        images.drawCentered(ctx, icon, cx, cy, Math.max(1, Math.round(diameter * symbolScale)), argb(0xFFFFFF, alpha));
    }

    static void status(DrawContext ctx, HudTextureCache images, StatusClient.Line line, float cx, float cy, float d, long now, float alpha) {
        float a = alpha * line.blink(now);
        draw(ctx, images, path(line.status()), line.status().color, cx, cy, d, line.fraction(now), a, .50f);
    }

    static void element(DrawContext ctx, HudTextureCache images, ElementMarkClient.Shown mark, float cx, float cy, float d, long now, float alpha) {
        long left = mark.end() - now, total = Math.max(1, mark.end() - mark.start());
        float a = alpha * (left < 1000 ? .45f + .55f * (float) Math.abs(Math.sin(now / 110.0)) : 1f);
        draw(ctx, images, ElementMarkClient.icon(mark.element()), ElementMarkClient.color(mark.element()), cx, cy, d,
            left / (float) total, a, .62f);
    }

    /** "…" after the third status above a head. */
    static void more(DrawContext ctx, float cx, float cy, float d, float alpha) {
        float dot = Math.max(1f, d * .07f), gap = d * .2f;
        for (int i = -1; i <= 1; i++) HudMesh.disk(ctx, cx + i * gap, cy, dot, argb(0xE8EEF7, alpha));
    }

    static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + (255 - r) / 4) << 16) | ((g + (255 - g) / 4) << 8) | (b + (255 - b) / 4);
    }
    static int argb(int rgb, float alpha) { return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF); }

}
