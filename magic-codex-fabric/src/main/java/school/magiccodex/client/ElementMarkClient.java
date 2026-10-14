package school.magiccodex.client;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import school.magiccodex.protocol.ElementProtocol;
import school.magiccodex.protocol.ElementProtocol.Element;

/**
 * Element marks above mobs. The server (MagicMythic) owns every rule; this only draws what it was told.
 *
 * Drawn as HUD icons, exactly like the other MagicCodex UI images: the original PNG
 * (assets/magiccodex/textures/element/<id>.png, 256 px) goes through HudTextureCache, which keeps the file unchanged
 * and scales it down with alpha-correct trilinear filtering. No world-space texture, no vanilla pixel scaling.
 * The mob's head position is projected with AimView (captured every frame by AimRenderer).
 *
 * Around the icon: a ring showing the time left of the mark, blinking in its last second.
 * When the server reports a reaction, its name floats up for one second.
 * Cost: nothing without marks; otherwise one projection + one visibility ray per marked mob in range (cap 24).
 */
public final class ElementMarkClient {
    private ElementMarkClient() {}

    public record Payload(byte[] bytes) implements CustomPayload {
        static final Id<Payload> ID = new Id<>(Identifier.of(ElementProtocol.CHANNEL));
        static final PacketCodec<RegistryByteBuf, Payload> CODEC = new PacketCodec<>() {
            public Payload decode(RegistryByteBuf b) {
                int n = b.readableBytes();
                if (n > ElementProtocol.MAX_BYTES) { b.skipBytes(n); return new Payload(new byte[0]); }
                byte[] a = new byte[n]; b.readBytes(a); return new Payload(a);
            }
            public void encode(RegistryByteBuf b, Payload value) { b.writeBytes(value.bytes()); }
        };
        public Id<Payload> getId() { return ID; }
    }

    private record Mark(Element element, long start, long end) {}
    private record Popup(int entityId, String label, int color, long start) {}

    private static final Map<Integer, Mark> marks = new HashMap<>();
    private static final List<Popup> popups = new ArrayList<>();
    private static ClientWorld world;
    private static HudTextureCache images;
    private static CodexTypography text;
    private static int generation = -1;

    private static final Identifier BOLD = Identifier.of("magiccodex", "hud_bold");
    private static final double MAX_DISTANCE = 32;
    private static final int MAX_DRAWN = 24;
    private static final long POPUP_MS = 1000;

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (payload, context) -> {
            ElementProtocol.Frame frame;
            try { frame = ElementProtocol.decode(payload.bytes()); } catch (IllegalArgumentException ignored) { return; }
            context.client().execute(() -> accept(frame));
        });
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(IdentifiedLayer.CROSSHAIR,
                Identifier.of("magiccodex", "element_marks"), ElementMarkClient::render));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { reset(); close(); });
    }

    private static void accept(ElementProtocol.Frame frame) {
        long now = Util.getMeasuringTimeMs();
        switch (frame) {
            case ElementProtocol.Mark m -> marks.put(m.entityId(), new Mark(m.element(), now, now + m.ticksLeft() * 50L));
            case ElementProtocol.Clear c -> marks.remove(c.entityId());
            case ElementProtocol.Pop p -> {
                popups.removeIf(old -> old.entityId() == p.entityId());
                if (popups.size() < 32) popups.add(new Popup(p.entityId(), p.reaction().label, color(p.reaction().a), now));
            }
        }
    }

    static int color(Element e) {
        return switch (e) {
            case FIRE -> 0xFF6A2B; case WATER -> 0x3FA9F5; case WIND -> 0x5FE3B5;
            case EARTH -> 0xC9A14A; case LIGHT -> 0xFFE680; case DARK -> 0x8A5CF5;
        };
    }

    static String icon(Element e) { return "magiccodex:textures/element/" + e.id + ".png"; }

    private static void reset() { marks.clear(); popups.clear(); world = null; }

    static void close() {
        if (images != null) { images.close(); images = null; }
        if (text != null) { text.close(); text = null; }
    }

    private record Draw(Entity entity, Mark mark, double distance) {}

    private static void render(DrawContext ctx, RenderTickCounter ticks) {
        var c = MinecraftClient.getInstance();
        if (c.world != world) { reset(); world = c.world; }
        if (marks.isEmpty() && popups.isEmpty()) return;
        if (c.player == null || c.world == null || c.options.hudHidden || !AimView.valid()) return;
        if (!HudPointer.hudVisibleUnder(c.currentScreen)) return;
        if (generation != UiResources.generation()) { close(); generation = UiResources.generation(); }
        if (images == null) images = new HudTextureCache(c);
        if (text == null) text = new CodexTypography(c);
        long now = Util.getMeasuringTimeMs();
        float delta = ticks.getTickDelta(false);
        Vec3d eye = c.player.getCameraPosVec(delta);

        var visible = new ArrayList<Draw>();
        for (var it = marks.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Mark m = e.getValue();
            Entity entity = c.world.getEntityById(e.getKey());
            if (now >= m.end() || entity == null || entity.isRemoved()) { it.remove(); continue; }
            double d = entity.getLerpedPos(delta).distanceTo(eye);
            if (d > MAX_DISTANCE || !c.player.canSee(entity)) continue;
            visible.add(new Draw(entity, m, d));
        }
        popups.removeIf(p -> now - p.start() > POPUP_MS);

        images.beginFrame();
        text.beginFrame();
        try {
            visible.sort(Comparator.comparingDouble(Draw::distance));
            for (int i = 0; i < Math.min(MAX_DRAWN, visible.size()); i++) drawMark(ctx, visible.get(i), delta, now);
            for (Popup p : popups) drawPopup(ctx, c, p, delta, now, eye);
        } finally {
            images.endFrame();
        }
    }

    /** Head-top anchor, lifted above a visible name tag. */
    private static Vec3d anchor(Entity entity, float delta) {
        double lift = entity.hasCustomName() && entity.isCustomNameVisible() ? 0.85 : 0.5;
        return entity.getLerpedPos(delta).add(0, entity.getHeight() + lift, 0);
    }

    /** GUI px: 20 up close, shrinking to 13 at the edge of the range. */
    private static float size(double distance) {
        return (float) (20 - 7 * Math.clamp((distance - 6) / (MAX_DISTANCE - 6), 0, 1));
    }

    private static void drawMark(DrawContext ctx, Draw d, float delta, long now) {
        float[] s = AimView.toScreen(anchor(d.entity(), delta));
        if (s == null) return;
        float size = size(d.distance()), x = s[0], y = s[1];
        Mark m = d.mark();
        long left = m.end() - now, total = Math.max(1, m.end() - m.start());
        float fraction = Math.clamp(left / (float) total, 0, 1);
        // Last second: blink so the reaction window is easy to read.
        float alpha = left < 1000 ? .45f + .55f * (float) Math.abs(Math.sin(now / 110.0)) : 1f;
        int rgb = color(m.element());
        float radius = size * .62f, thickness = Math.max(1.6f, size * .1f);
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(0, 0, 150);
            HudMesh.arc(ctx, x, y, radius, thickness + 1.2f, 0, (float) (Math.PI * 2), argb(0x101925, .55f * alpha));
            HudMesh.arc(ctx, x, y, radius, thickness, (float) (-Math.PI / 2), (float) (Math.PI * 2 * fraction), argb(rgb, .95f * alpha));
            images.drawCentered(ctx, icon(m.element()), x, y, Math.round(size), argb(0xFFFFFF, alpha));
        } finally {
            ctx.getMatrices().pop();
        }
    }

    private static void drawPopup(DrawContext ctx, MinecraftClient c, Popup p, float delta, long now, Vec3d eye) {
        Entity entity = c.world.getEntityById(p.entityId());
        if (entity == null) return;
        double d = entity.getLerpedPos(delta).distanceTo(eye);
        if (d > MAX_DISTANCE) return;
        float[] s = AimView.toScreen(anchor(entity, delta));
        if (s == null) return;
        float t = (now - p.start()) / (float) POPUP_MS;
        float alpha = t < .7f ? 1 : 1 - (t - .7f) / .3f;
        float rise = 10 * t;
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(0, 0, 160);
            text.drawOutline(ctx, p.label(), s[0], s[1] - size(d) - 6 - rise, 12, argb(lighten(p.color()), alpha), BOLD, true);
        } finally {
            ctx.getMatrices().pop();
        }
    }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
    }

    private static int argb(int rgb, float alpha) { return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF); }
}
