package school.magiccodex.client;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import school.magiccodex.protocol.TravelProtocol;

/**
 * 공개 이동 지점의 포탈 표시 (2026-10-23, owner: 포탈 형태 VFX, 멀리 있는 지점까지 계속 재생하지 않기).
 *
 * Reuses the story mod's approved portal look (its ring / glow / band textures, copied to textures/gui/travel/world)
 * as a client-only mark: ground glow, two counter-rotating rings and light rising along the rim, plus the point name.
 * Only the points of the world the player is in (sent by the server) within {@value #MAX_DIST} blocks are drawn,
 * nothing at all beyond that and no server work per tick. Found points glow blue-gold; 미발견 points are a dim grey ring.
 * When a point gets unlocked it flashes once.
 */
final class TravelWorldMarks {
    private TravelWorldMarks() {}

    static final double MAX_DIST = 64, LABEL_DIST = 24;
    private static final Identifier RING = tex("portal_ring"), RING_GLOW = tex("portal_ring_glow"), GROUND = tex("portal_glow"), BAND = tex("portal_band");
    private static Identifier tex(String n) { return Identifier.of("magiccodex", "textures/gui/travel/world/" + n + ".png"); }
    /** portal_ring.png: the ring radius is this fraction of half the image (same art as the story portal). */
    private static final float RING_IN_IMAGE = .66f;
    private static final int SEGMENTS = 20;
    private static final Map<String, Long> flashAt = new HashMap<>();

    static void initialize() { WorldRenderEvents.AFTER_TRANSLUCENT.register(TravelWorldMarks::render); }

    /** A point that just became unlocked flashes once. */
    static void changed(TravelProtocol.Markers before, TravelProtocol.Markers after) {
        if (!before.world().equals(after.world())) { flashAt.clear(); return; }
        var was = new HashMap<String, Boolean>();
        before.markers().forEach(m -> was.put(m.id(), m.unlocked()));
        long now = Util.getMeasuringTimeMs();
        for (var m : after.markers()) if (m.unlocked() && Boolean.FALSE.equals(was.get(m.id()))) flashAt.put(m.id(), now);
    }

    private static void render(WorldRenderContext ctx) {
        var mc = MinecraftClient.getInstance();
        var data = TravelClient.markers;
        if (mc.world == null || mc.player == null || data.markers().isEmpty() || mc.options.hudHidden) return;
        // the server names its own world (same name the plugin uses); the client only knows the dimension, so the server
        // re-sends on every world change and an empty / stale list just draws nothing
        var consumers = ctx.consumers(); var matrices = ctx.matrixStack();
        if (consumers == null || matrices == null) return;
        var cam = ctx.camera().getPos();
        long now = Util.getMeasuringTimeMs();
        float t = (now % 3_600_000L) / 1000f;
        for (var m : data.markers()) {
            double dx = m.x() - cam.x, dy = m.y() - cam.y, dz = m.z() - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > MAX_DIST * MAX_DIST) continue;
            float fade = (float) Math.clamp((MAX_DIST - Math.sqrt(d2)) / 12, 0, 1);
            Long flash = flashAt.get(m.id());
            float burst = 0;
            if (flash != null) { float k = (now - flash) / 1200f; if (k >= 1) flashAt.remove(m.id()); else burst = (1 - k) * (1 - k); }
            matrices.push();
            try {
                matrices.translate(dx, dy, dz);
                portal(matrices, consumers, m.radius(), t, fade, m.unlocked(), burst);
                if (d2 < LABEL_DIST * LABEL_DIST) label(mc, matrices, consumers, ctx, m, t, fade * (float) Math.clamp((LABEL_DIST - Math.sqrt(d2)) / 6, 0, 1));
            } finally { matrices.pop(); }
        }
    }

    private static void portal(MatrixStack ms, VertexConsumerProvider consumers, float radius, float t, float alpha, boolean found, float burst) {
        float r = Math.max(.8f, Math.min(radius, 6)) * (1 + .3f * burst), half = r / RING_IN_IMAGE;
        float breath = .86f + .14f * (float) Math.sin(t * 1.7f);
        int glowRgb = found ? 0x8CD9FF : 0x8A93A0, ringRgb = found ? 0xFFFFFF : 0xA9B2BE;
        float strength = found ? 1 : .45f;
        ms.push();
        ms.translate(0, .04, 0);
        flat(ms, consumers, GROUND, half * 1.02f, argb(alpha * (.45f * strength + .5f * burst), glowRgb));
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 12f));
        flat(ms, consumers, RING_GLOW, half * 1.03f, argb(alpha * (.4f * strength + .5f * burst), glowRgb));
        ms.translate(0, .005, 0);
        flat(ms, consumers, RING, half, argb(alpha * breath * (found ? 1 : .7f), ringRgb));
        ms.pop();
        ms.push();
        ms.translate(0, .01, 0);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-t * 20f + 45f));
        flat(ms, consumers, RING, half * .78f, argb(alpha * .4f * strength, ringRgb));
        ms.pop();
        if (found || burst > 0) for (int k = 0; k < 2; k++) {
            float phase = ((t * .32f) + k * .5f) % 1f;
            float a = (float) Math.sin(phase * Math.PI) * alpha * (.75f + burst);
            ms.push();
            ms.translate(0, phase * .45f, 0);
            band(ms, consumers, r * .93f, 1.6f, a, glowRgb);
            ms.pop();
        }
        ms.pop();
    }

    private static void flat(MatrixStack ms, VertexConsumerProvider consumers, Identifier id, float half, int color) {
        if ((color >>> 24) == 0) return;
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(id));
        Matrix4f m = ms.peek().getPositionMatrix();
        // both faces (seen from above and, on a slope, from below)
        emit(vc, m, -half, 0, -half, 0, 0, color, 1); emit(vc, m, -half, 0, half, 0, 1, color, 1); emit(vc, m, half, 0, half, 1, 1, color, 1); emit(vc, m, half, 0, -half, 1, 0, color, 1);
        emit(vc, m, half, 0, -half, 1, 0, color, -1); emit(vc, m, half, 0, half, 1, 1, color, -1); emit(vc, m, -half, 0, half, 0, 1, color, -1); emit(vc, m, -half, 0, -half, 0, 0, color, -1);
    }
    /** Light along the rim: strong at the bottom, gone at the top; the texture repeats twice around. */
    private static void band(MatrixStack ms, VertexConsumerProvider consumers, float radius, float height, float alpha, int rgb) {
        if (alpha <= .01f) return;
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BAND));
        Matrix4f m = ms.peek().getPositionMatrix();
        int bottom = argb(Math.min(1, alpha), rgb), top = argb(0, rgb);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius, x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float u0 = (float) i / SEGMENTS * 2, u1 = (float) (i + 1) / SEGMENTS * 2;
            emit(vc, m, x0, 0, z0, u0, 1, bottom, 1); emit(vc, m, x1, 0, z1, u1, 1, bottom, 1); emit(vc, m, x1, height, z1, u1, 0, top, 1); emit(vc, m, x0, height, z0, u0, 0, top, 1);
            emit(vc, m, x0, height, z0, u0, 0, top, 1); emit(vc, m, x1, height, z1, u1, 0, top, 1); emit(vc, m, x1, 0, z1, u1, 1, bottom, 1); emit(vc, m, x0, 0, z0, u0, 1, bottom, 1);
        }
    }
    private static void emit(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int color, float ny) {
        vc.vertex(m, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, ny, 0);
    }

    /** Point name above the portal, facing the camera (vanilla font in the world; MagicCodex font is for screens). */
    private static void label(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider consumers, WorldRenderContext ctx, TravelProtocol.Marker m, float t, float alpha) {
        if (alpha <= .03f) return;
        ms.push();
        try {
            ms.translate(0, 2.2 + Math.sin(t * 1.6f) * .05, 0);
            ms.multiply(ctx.camera().getRotation());
            ms.scale(.025f, -.025f, .025f);
            TextRenderer font = mc.textRenderer;
            String name = m.unlocked() ? m.name() : m.name() + " (미발견)";
            float x = -font.getWidth(name) / 2f;
            int color = argb(alpha, m.unlocked() ? 0xFFF2D8 : 0xC3CBD6);
            font.draw(name, x, 0, color, false, ms.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, argb(alpha * .45f, 0x061428), LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } finally { ms.pop(); }
    }

    private static int argb(float alpha, int rgb) { return (Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF); }
}
