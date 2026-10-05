package school.magiccodex.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * GUI-only renderer for flat item icons whose texture is larger than 32px (for example 128x128
 * pixel art from the resource pack). Vanilla shrinks those with nearest sampling, so 1px outlines
 * break and gradients alias. Matching items are drawn from a private GPU copy with an
 * {@link OutlineMipmaps outline-preserving} mip chain and trilinear filtering, like other
 * magiccodex UI images. Hand-held, dropped and framed items keep the vanilla renderer.
 *
 * Only simple definitions qualify: {@code items/<id>.json} of type {@code minecraft:model}
 * without tints, whose model chain ends in {@code item/generated} with a single {@code layer0},
 * no custom GUI display transform and no animation. Anything else, enchantment glint, and icons
 * still loading fall back to vanilla for that frame.
 */
public final class ItemIconTextures {
    private ItemIconTextures() {}

    static final int MIN_SIZE = 33;
    private static final int MAX_SIZE = 1024, MAX_TEXTURES = 256, UPLOADS_PER_TICK = 4;
    /** Slightly sharper than the exact level: keeps 48px (GUI scale 3) icons crisp, see docs. */
    private static final float LOD_BIAS = -0.5f;
    private static final ShaderProgramKey PROGRAM = new ShaderProgramKey(
            Identifier.of("magiccodex", "core/item_icon"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "magiccodex-item-icons"); t.setDaemon(true); return t;
    });

    record Decoded(Identifier texture, int width, int height, List<int[]> levels) {}
    private record Loaded(Identifier id, int width, int height, RenderLayer layer) {}

    /** Empty = this item model stays vanilla until the next resource reload. */
    private static final Map<Identifier, Optional<Loaded>> ICONS = new HashMap<>();
    private static AsyncUiLoader<Identifier, Optional<Decoded>> pending;
    private static int serial, generation;
    private static boolean announced;

    static void initialize() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> pump());
    }

    /** Called from the DrawContext mixin. @return true when the vanilla item draw must be skipped. */
    public static boolean draw(DrawContext ctx, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || stack.hasGlint()) return false;
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        if (model == null) return false;
        var known = ICONS.get(model);
        if (known == null) { request(model); return false; }
        if (known.isEmpty()) return false;
        var t = known.get();
        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate(x, y, 150);
            ctx.drawTexture(id -> t.layer(), t.id(), 0, 0, 0, 0, 16, 16, t.width(), t.height(), t.width(), t.height(), 0xFFFFFFFF);
            ctx.draw(); // vanilla flushes each GUI item too; keeps painter's order with later slot overlays
        } finally { m.pop(); }
        if (!announced) { announced = true; org.slf4j.LoggerFactory.getLogger("magiccodex").info("High-res item icons active ({})", model); }
        return true;
    }

    private static void request(Identifier model) {
        if (pending == null) {
            ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
            pending = new AsyncUiLoader<>(DECODER, 8, id -> decode(manager, id), d -> {});
        }
        pending.request(model);
    }

    /** Render thread: upload a few finished decodes per tick. */
    static void pump() {
        if (pending == null) return;
        for (int i = 0; i < UPLOADS_PER_TICK; i++) {
            var ready = pending.takeReady();
            if (ready == null) return;
            var result = ready.getValue();
            if (result.error() != null)
                org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Item icon failed: {}", ready.getKey(), result.error());
            if (ICONS.size() >= MAX_TEXTURES) clearIcons();
            ICONS.put(ready.getKey(), result.error() == null ? result.value().flatMap(ItemIconTextures::upload) : Optional.empty());
        }
    }

    /** Resource reload / disconnect / shutdown. Runs on the render thread. */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) { MinecraftClient.getInstance().execute(ItemIconTextures::reset); return; }
        if (pending != null) { pending.close(); pending = null; }
        clearIcons();
        generation++;
        announced = false;
    }
    private static void clearIcons() {
        var textures = MinecraftClient.getInstance().getTextureManager();
        for (var icon : ICONS.values()) icon.ifPresent(t -> textures.destroyTexture(t.id()));
        ICONS.clear();
    }

    // ---------------------------------------------------------------- worker thread

    static Optional<Decoded> decode(ResourceManager manager, Identifier model) throws Exception {
        var texture = resolveTexture(manager, model);
        if (texture.isEmpty()) return Optional.empty();
        Identifier png = Identifier.of(texture.get().getNamespace(), "textures/" + texture.get().getPath() + ".png");
        if (manager.getResource(Identifier.of(png.getNamespace(), png.getPath() + ".mcmeta")).isPresent()) return Optional.empty();
        var resource = manager.getResource(png);
        if (resource.isEmpty()) return Optional.empty();
        java.awt.image.BufferedImage image;
        try (InputStream in = resource.get().getInputStream()) { image = javax.imageio.ImageIO.read(in); }
        if (image == null) return Optional.empty();
        int w = image.getWidth(), h = image.getHeight();
        if (Math.max(w, h) < MIN_SIZE || Math.max(w, h) > MAX_SIZE) return Optional.empty();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        return Optional.of(new Decoded(png, w, h, OutlineMipmaps.build(argb, w, h)));
    }

    /** items/&lt;id&gt;.json → model chain → layer0, only for flat single-layer icons. */
    static Optional<Identifier> resolveTexture(ResourceManager manager, Identifier model) throws Exception {
        var definition = json(manager, Identifier.of(model.getNamespace(), "items/" + model.getPath() + ".json"));
        if (definition == null || !(definition.get("model") instanceof JsonObject root)) return Optional.empty();
        String type = string(root, "type");
        if (type == null || !(type.equals("minecraft:model") || type.equals("model"))) return Optional.empty();
        if (root.has("tints") && !root.getAsJsonArray("tints").isEmpty()) return Optional.empty();
        String next = string(root, "model");
        Map<String, String> textures = new HashMap<>();
        for (int depth = 0; next != null && depth < 8; depth++) {
            Identifier id = Identifier.of(next);
            if (isGenerated(id)) return flatLayer(textures);
            var json = json(manager, Identifier.of(id.getNamespace(), "models/" + id.getPath() + ".json"));
            if (json == null || json.has("elements")) return Optional.empty();
            if (json.get("display") instanceof JsonObject display && display.has("gui")) return Optional.empty();
            if (json.get("textures") instanceof JsonObject map)
                for (var entry : map.entrySet()) if (entry.getValue().isJsonPrimitive()) textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            next = string(json, "parent");
        }
        return Optional.empty();
    }

    private static boolean isGenerated(Identifier id) {
        return id.getNamespace().equals("minecraft") && (id.getPath().equals("item/generated") || id.getPath().equals("builtin/generated")
                || id.getPath().equals("item/handheld"));
    }
    private static Optional<Identifier> flatLayer(Map<String, String> textures) {
        if (textures.containsKey("layer1")) return Optional.empty();
        String layer = textures.get("layer0");
        for (int i = 0; layer != null && layer.startsWith("#") && i < 8; i++) layer = textures.get(layer.substring(1));
        if (layer == null || layer.startsWith("#")) return Optional.empty();
        return Optional.ofNullable(Identifier.tryParse(layer));
    }
    private static JsonObject json(ResourceManager manager, Identifier id) throws Exception {
        var resource = manager.getResource(id);
        if (resource.isEmpty()) return null;
        try (var reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(reader);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        }
    }
    private static String string(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    // ---------------------------------------------------------------- render thread

    private static Optional<Loaded> upload(Decoded d) {
        RenderSystem.assertOnRenderThread();
        var levels = d.levels();
        NativeImage base = new NativeImage(d.width(), d.height(), false);
        NativeImageBackedTexture texture = null;
        try {
            int[] l0 = levels.get(0);
            for (int y = 0; y < d.height(); y++) for (int x = 0; x < d.width(); x++)
                base.setColorArgb(x, y, PremultipliedAlpha.pixel(l0[y * d.width() + x]));
            texture = new NativeImageBackedTexture(base); base = null;
            int last = levels.size() - 1;
            texture.bindTexture();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, last);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, last);
            // Allocates every level; the generated contents are replaced below.
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            int lw = d.width(), lh = d.height();
            for (int level = 1; level <= last; level++) {
                lw = Math.max(1, lw >> 1); lh = Math.max(1, lh >> 1);
                ByteBuffer rgba = MemoryUtil.memAlloc(lw * lh * 4);
                try {
                    for (int p : levels.get(level)) {
                        int q = PremultipliedAlpha.pixel(p);
                        rgba.put((byte) (q >>> 16)).put((byte) (q >>> 8)).put((byte) q).put((byte) (q >>> 24));
                    }
                    rgba.flip();
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, lw, lh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                } finally { MemoryUtil.memFree(rgba); }
            }
            texture.setClamp(true);
            applyFilter(texture);
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, last, GL11.GL_TEXTURE_WIDTH) != 1)
                throw new IllegalStateException("Incomplete item icon mip chain");
            var id = Identifier.of("magiccodex", "runtime_item_icon/" + generation + "/" + serial++);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture = null;
            return Optional.of(new Loaded(id, d.width(), d.height(), new IconLayer(id)));
        } catch (Exception error) {
            if (texture != null) texture.close();
            if (base != null) base.close();
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Item icon upload failed: {}", d.texture(), error);
            return Optional.empty();
        }
    }

    /** Trilinear when shrinking; nearest when enlarged so pixel art stays crisp in zoomed previews. */
    private static void applyFilter(net.minecraft.client.texture.AbstractTexture texture) {
        texture.setFilter(true, true);
        texture.bindTexture();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, LOD_BIAS);
    }

    private static final class IconLayer extends RenderLayer {
        IconLayer(Identifier id) {
            super("magiccodex_item_icon", VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 1536, false, false,
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        RenderSystem.disableCull();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(true);
                        RenderSystem.setShader(PROGRAM);
                        RenderSystem.setShaderTexture(0, id);
                        applyFilter(MinecraftClient.getInstance().getTextureManager().getTexture(id));
                    }, () -> {
                        RenderSystem.enableCull();
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });
        }
    }
}
