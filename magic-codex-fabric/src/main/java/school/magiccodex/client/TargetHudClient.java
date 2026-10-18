package school.magiccodex.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.ElementProtocol;
import school.magiccodex.protocol.StatusProtocol;
import school.magiccodex.protocol.TargetHudProtocol;

/**
 * Target health HUD at the top of the screen (CHACADEMI_BOSS_HUD, approved reference 2026-10-19) and the player's
 * own status icons at the top right (where vanilla effect icons were; vanilla effect icons are hidden).
 *
 *        달빛 늑대                     name (rare/이로치 = lavender, hp_rare bar)
 *   ───────◇───────            280    title decoration · damage I dealt to it
 *   [■■■■][■■■■][■720/1000■][▒▒][  ][  ]   6 fixed HP segments, the segment holding the tip is brighter
 *   [=========160 / 240======        ]       mana (players / mobs with mana only; vanilla mobs: no mana row)
 *   ♥ 38%                      (元)(●)(●)   taming odds · element mark (left) + statuses filled from the right
 *
 * Geometry is in frame-asset pixels (frame.png 1237 x 101, slot rectangles measured on the PNG) and scaled to
 * a width of ~38 % of the screen. Text is drawn unscaled at the matching GUI size so glyphs stay sharp.
 * The server decides the target (last combat partner 10 s, else crosshair) - see TargetHudBridge.
 */
public final class TargetHudClient {
    private TargetHudClient() {}

    public record Payload(byte[] bytes) implements CustomPayload {
        static final Id<Payload> ID = new Id<>(Identifier.of(TargetHudProtocol.CHANNEL));
        static final PacketCodec<RegistryByteBuf, Payload> CODEC = new PacketCodec<>() {
            public Payload decode(RegistryByteBuf b) {
                int n = b.readableBytes();
                if (n > TargetHudProtocol.MAX_BYTES) { b.skipBytes(n); return new Payload(new byte[0]); }
                byte[] a = new byte[n]; b.readBytes(a); return new Payload(a);
            }
            public void encode(RegistryByteBuf b, Payload value) { b.writeBytes(value.bytes()); }
        };
        public Id<Payload> getId() { return ID; }
    }

    // ---------------------------------------------------------------- assets (magiccodex:textures/gui/target_hud)
    private static final String DIR = "magiccodex:textures/gui/target_hud/";
    private static final String FRAME = DIR + "frame.png", FRAME_HP = DIR + "frame_hp.png", HP = DIR + "hp_normal.png",
        HP_RARE = DIR + "hp_rare.png", MANA = DIR + "mana.png", DECOR = DIR + "title_decoration.png", HEART = DIR + "heart.png";
    private static final Identifier TITLE = Identifier.of("magiccodex", "title"), BOLD = Identifier.of("magiccodex", "hud_bold");

    // ---------------------------------------------------------------- geometry (frame-asset px)
    static final int FRAME_W = 1237, FRAME_H = 101, FRAME_HP_H = 74;
    static final float[][] SLOTS = TargetHudLayout.SLOTS;
    static final float HP_TOP = 16.8f, HP_BOTTOM = 56.4f, MANA_LEFT = 16.8f, MANA_RIGHT = 1219.2f, MANA_TOP = 69f, MANA_BOTTOM = 85.8f;
    static final float NAME_Y = -68, NAME_SIZE = 46, DECOR_Y = -23, DECOR_W = 730, DAMAGE_Y = -26, DAMAGE_SIZE = 34;
    static final float HP_TEXT = 34, MANA_TEXT = 24, HEART_DY = 14.5f, HEART_SIZE = 33, ICON_DY = 34.6f, ICON_D = 69, ICON_STEP = 83, ICON_RIGHT = 1231.5f;

    static float segment(float f, int i) { return TargetHudLayout.segment(f, i); }
    static int tipSegment(float f) { return TargetHudLayout.tipSegment(f); }
    static float width(int screenW) { return TargetHudLayout.width(screenW); }

    // ---------------------------------------------------------------- state
    private static TargetHudProtocol.Show target, preview;
    private static long receivedAt, previewUntil, shownAt;
    private static int shownId = Integer.MIN_VALUE;
    private static float health = -1, mana = -1;
    private static long lastFrame;
    private static ClientWorld world;
    private static HudTextureCache images;
    private static CodexTypography text;
    private static int generation = -1;
    private static final long TIMEOUT_MS = 1600, FADE_MS = 140;

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (payload, context) -> {
            TargetHudProtocol.Frame f;
            try { f = TargetHudProtocol.decode(payload.bytes()); } catch (IllegalArgumentException ignored) { return; }
            context.client().execute(() -> accept(f));
        });
        HudLayerRegistrationCallback.EVENT.register(drawer -> {
            drawer.attachLayerAfter(IdentifiedLayer.CROSSHAIR, Identifier.of("magiccodex", "target_hud"), TargetHudClient::render);
            // Owner 2026-10-19: vanilla effect icons (buffs too) are gone; debuffs show as 차카데미 icons top right.
            drawer.removeLayer(IdentifiedLayer.STATUS_EFFECTS);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { reset(); close(); });
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("대상체력바미리보기")
                .executes(c -> { startPreview(0); c.getSource().sendFeedback(Text.literal("대상 체력바 미리보기 15초 (가짜 데이터). /대상체력바미리보기 일반 · 이로치 · 플레이어 · 끄기")); return 1; });
            root.then(ClientCommandManager.literal("일반").executes(c -> { startPreview(0); return 1; }));
            root.then(ClientCommandManager.literal("이로치").executes(c -> { startPreview(1); return 1; }));
            root.then(ClientCommandManager.literal("플레이어").executes(c -> { startPreview(2); return 1; }));
            root.then(ClientCommandManager.literal("끄기").executes(c -> { previewUntil = 0; preview = null; return 1; }));
            dispatcher.register(root);
        });
    }

    private static void accept(TargetHudProtocol.Frame f) {
        sync();
        long now = Util.getMeasuringTimeMs();
        switch (f) {
            case TargetHudProtocol.Show s -> {
                if (target == null || target.entityId() != s.entityId() || now - receivedAt > TIMEOUT_MS) { health = -1; mana = -1; }
                target = s; receivedAt = now;
            }
            case TargetHudProtocol.Hide h -> { target = null; }
        }
    }

    private static void sync() {
        var c = MinecraftClient.getInstance();
        if (c.world != world) { reset(); world = c.world; }
    }
    private static void reset() { target = null; preview = null; previewUntil = 0; health = mana = -1; shownId = Integer.MIN_VALUE; }
    static void close() {
        if (images != null) { images.close(); images = null; }
        if (text != null) { text.close(); text = null; }
    }

    /** Name currently on the HUD (boss bar filter), "" when none. */
    static String shownName() {
        var t = current(Util.getMeasuringTimeMs());
        return t == null ? "" : name(t);
    }

    private static TargetHudProtocol.Show current(long now) {
        if (preview != null && now < previewUntil) return preview;
        if (target != null && now - receivedAt <= TIMEOUT_MS) return target;
        return null;
    }

    private static String name(TargetHudProtocol.Show t) {
        if (!t.name().isBlank()) return t.name();
        if (!t.translationKey().isBlank()) return Text.translatable(t.translationKey()).getString();
        var c = MinecraftClient.getInstance();
        var e = c.world == null ? null : c.world.getEntityById(t.entityId());
        return e == null ? "" : e.getName().getString();
    }

    // ---------------------------------------------------------------- render

    private static void render(DrawContext ctx, RenderTickCounter ticks) {
        var c = MinecraftClient.getInstance();
        sync();
        if (c.player == null || c.world == null || c.options.hudHidden || !HudPointer.hudVisibleUnder(c.currentScreen)) return;
        long now = Util.getMeasuringTimeMs();
        var t = current(now);
        boolean self = !StatusClient.of(c.player.getId()).isEmpty() || ElementMarkClient.markOf(c.player.getId()) != null;
        if (t == null && !self) { shownId = Integer.MIN_VALUE; lastFrame = now; return; }
        if (generation != UiResources.generation()) { close(); generation = UiResources.generation(); }
        if (images == null) images = new HudTextureCache(c);
        if (text == null) text = new CodexTypography(c);
        images.beginFrame(); text.beginFrame();
        try {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 120);
            if (t != null) {
                if (t.entityId() != shownId) { shownId = t.entityId(); shownAt = now; }
                float alpha = Math.clamp((now - shownAt) / (float) FADE_MS, 0, 1);
                drawTarget(ctx, c, t, now, alpha);
            } else shownId = Integer.MIN_VALUE;
            if (self) drawSelf(ctx, c, now);
            ctx.getMatrices().pop();
        } finally {
            images.endFrame();
        }
        lastFrame = now;
    }

    private static void drawTarget(DrawContext ctx, MinecraftClient c, TargetHudProtocol.Show t, long now, float alpha) {
        int sw = c.getWindow().getScaledWidth();
        float w = width(sw), s = w / FRAME_W;
        float top = 8 + (NAME_SIZE * .5f - NAME_Y) * s;
        if (KyudichiClient.active()) top += 215 * KyudichiHud.scale(sw, c.getWindow().getScaledHeight());
        float fx = (sw - w) / 2f, fy = top;
        boolean rare = t.shiny(), hasMana = t.hasMana();
        // Smooth bars: health eases toward the server value (~4 updates per second).
        float dt = Math.clamp((now - lastFrame) / 1000f, 0, .1f);
        health = health < 0 ? t.health() : health + (t.health() - health) * Math.min(1, dt * (t.health() > health ? 14 : 9));
        if (hasMana) mana = mana < 0 ? t.mana() : mana + (t.mana() - mana) * Math.min(1, dt * 12);
        float fHp = Math.clamp(health / t.maxHealth(), 0, 1);
        int a8 = Math.round(alpha * 255) << 24;

        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate(fx, fy, 0);
            m.scale(s, s, 1);
            // title decoration
            int[] dsz = images.size(DECOR);
            if (dsz != null) {
                int dh = Math.round(DECOR_W * dsz[1] / (float) dsz[0]);
                images.drawStretched(ctx, DECOR, Math.round(FRAME_W / 2f - DECOR_W / 2), Math.round(DECOR_Y - dh / 2f), Math.round(DECOR_W), dh, a8 | 0xFFFFFF);
            }
            // frame
            images.drawStretched(ctx, hasMana ? FRAME : FRAME_HP, 0, 0, FRAME_W, hasMana ? FRAME_H : FRAME_HP_H, a8 | 0xFFFFFF);
            // HP segments: one continuous strip clipped per slot, so the rare gradient runs across all six.
            String strip = rare ? HP_RARE : HP;
            int[] hsz = images.size(strip);
            int tip = tipSegment(fHp);
            if (hsz != null) {
                float span0 = SLOTS[0][0], span = SLOTS[SLOTS.length - 1][1] - span0;
                for (int i = 0; i < SLOTS.length; i++) {
                    float part = segment(fHp, i);
                    if (part <= 0) continue;
                    float x0 = SLOTS[i][0], sw0 = SLOTS[i][1] - x0, wPart = sw0 * part;
                    float u = (x0 - span0) / span * hsz[0], uw = wPart / span * hsz[0];
                    int tint = i == tip ? 0xFFFFFF : 0xD2D2D2;
                    images.drawPart(ctx, strip, Math.round(x0), Math.round(HP_TOP), Math.max(1, Math.round(wPart)), Math.round(HP_BOTTOM - HP_TOP),
                        u, 0, Math.max(1, Math.round(uw)), hsz[1], a8 | tint);
                    if (i == tip) HudMesh.quad(ctx, x0, HP_TOP, wPart, HP_BOTTOM - HP_TOP, (Math.round(alpha * 46) << 24) | 0xFFFFFF, (Math.round(alpha * 70) << 24) | 0xFFFFFF);
                }
            }
            // mana
            if (hasMana) {
                int[] msz = images.size(MANA);
                float fm = Math.clamp(mana / t.maxMana(), 0, 1), mw = (MANA_RIGHT - MANA_LEFT) * fm;
                if (msz != null && mw > 0)
                    images.drawPart(ctx, MANA, Math.round(MANA_LEFT), Math.round(MANA_TOP), Math.max(1, Math.round(mw)), Math.round(MANA_BOTTOM - MANA_TOP),
                        0, 0, Math.max(1, Math.round(msz[0] * fm)), msz[1], a8 | 0xFFFFFF);
            }
            float below = hasMana ? FRAME_H : FRAME_HP_H;
            // taming odds heart
            if (t.tameChance() >= 0) images.drawCentered(ctx, HEART, 28.7f, below + HEART_DY, Math.round(HEART_SIZE), a8 | 0xFFFFFF);
            // element mark (left) + statuses filled from the right
            var statuses = t.entityId() < 0 ? previewLines(now) : StatusClient.of(t.entityId());
            var mark = t.entityId() < 0 ? previewMark(now) : ElementMarkClient.markOf(t.entityId());
            float iconY = below + ICON_DY;
            int slot = 0;
            for (var line : statuses) StatusIcons.status(ctx, images, line, TargetHudLayout.iconX(ICON_RIGHT, ICON_D, ICON_STEP, slot++), iconY, ICON_D, now, alpha);
            if (mark != null) StatusIcons.element(ctx, images, mark, TargetHudLayout.iconX(ICON_RIGHT, ICON_D, ICON_STEP, slot), iconY, ICON_D, now, alpha);
        } finally {
            m.pop();
        }

        // ---- text (GUI px, unscaled)
        String name = name(t);
        int nameColor = rare ? 0xC9B4FF : t.kind() == TargetHudProtocol.KIND_PLAYER ? 0xF4F1EA : 0xF3EBDD;
        float nameSize = NAME_SIZE * s, nameMax = 900 * s;
        float nw = text.width(name, nameSize, TITLE);
        if (nw > nameMax) nameSize *= nameMax / nw;
        text.drawOutline(ctx, name, fx + FRAME_W / 2f * s, fy + NAME_Y * s, nameSize, a8 | nameColor, TITLE, true);
        if (t.damage() >= 0.5f) {
            String dmg = number(t.damage());
            float dSize = DAMAGE_SIZE * s;
            text.drawOutline(ctx, dmg, fx + 1232 * s - text.width(dmg, dSize, TITLE), fy + DAMAGE_Y * s, dSize, a8 | 0xF1E3C6, TITLE, false);
        }
        // Numbers show the server value (the bar eases); a new text sprite only when the value really changes.
        String hp = number(t.health()) + " / " + number(t.maxHealth());
        text.drawOutline(ctx, hp, fx + FRAME_W / 2f * s, fy + (HP_TOP + HP_BOTTOM) / 2 * s, HP_TEXT * s, a8 | 0xFFF7F2, TITLE, true);
        if (hasMana) {
            String mt = number(t.mana()) + " / " + number(t.maxMana());
            float ms = MANA_TEXT * s, mw = text.width(mt, ms, TITLE) + 28 * s, cy = fy + (MANA_TOP + MANA_BOTTOM) / 2 * s;
            HudMesh.capsule(ctx, fx + FRAME_W / 2f * s - mw / 2, cy - 14 * s, mw, 28 * s, (Math.round(alpha * 235) << 24) | 0x07172A, (Math.round(alpha * 235) << 24) | 0x07172A);
            text.drawOutline(ctx, mt, fx + FRAME_W / 2f * s, cy, ms, a8 | 0x9BE8FF, TITLE, true);
        }
        if (t.tameChance() >= 0) {
            float below = hasMana ? FRAME_H : FRAME_HP_H;
            double pct = t.tameChance() * 100;
            String odds = pct > 0 && pct < 10 ? String.format(Locale.ROOT, "%.1f%%", pct) : Math.round(pct) + "%";
            text.drawOutline(ctx, odds, fx + 55 * s, fy + (below + HEART_DY) * s, 30 * s, a8 | 0xF4F1EA, TITLE, false);
        }
    }

    /** Own statuses: top right, oldest at the right edge, element mark left of them; wraps every 10. */
    private static void drawSelf(DrawContext ctx, MinecraftClient c, long now) {
        int sw = c.getWindow().getScaledWidth(), sh = c.getWindow().getScaledHeight();
        float ui = Math.clamp(Math.min(sw / 960f, sh / 540f), .8f, 1.4f), d = 20 * ui, step = d * 1.16f;
        var lines = StatusClient.of(c.player.getId());
        var mark = ElementMarkClient.markOf(c.player.getId());
        int n = lines.size() + (mark == null ? 0 : 1);
        for (int i = 0; i < n; i++) {
            int row = i / 10, col = i % 10;
            float cx = sw - 6 - d / 2 - col * step, cy = 6 + d / 2 + row * step;
            if (i < lines.size()) StatusIcons.status(ctx, images, lines.get(i), cx, cy, d, now, 1);
            else StatusIcons.element(ctx, images, mark, cx, cy, d, now, 1);
        }
    }

    static String number(float v) {
        if (!Float.isFinite(v)) return "0";
        long r = Math.round(v);
        return r >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", v / 1_000_000f) : String.valueOf(r);
    }

    // ---------------------------------------------------------------- boss bars (vanilla + MythicMobs replaced)

    private static final Set<String> nearby = new HashSet<>();
    private static long nearbyAt;

    /**
     * True for a boss bar that belongs to a mob: vanilla boss bars (translatable entity name) or a bar whose title is
     * the name of a living entity near the player (MythicMobs boss bars use the mob's display name). Other bars
     * (큐디치 match bars, events) stay.
     */
    public static boolean hidesBossBar(ClientBossBar bar) {
        Text name = bar.getName();
        if (name.getContent() instanceof TranslatableTextContent tr && tr.getKey().startsWith("entity.")) return true;
        String plain = strip(name.getString());
        if (plain.isEmpty()) return false;
        if (plain.equals(strip(shownName()))) return true;
        long now = Util.getMeasuringTimeMs();
        var c = MinecraftClient.getInstance();
        if (now - nearbyAt > 1000 && c.world != null && c.player != null) {
            nearbyAt = now; nearby.clear();
            var box = c.player.getBoundingBox().expand(96);
            for (var e : c.world.getEntitiesByClass(LivingEntity.class, box, x -> x.hasCustomName())) {
                if (e.getCustomName() != null) nearby.add(strip(e.getCustomName().getString()));
            }
        }
        return nearby.contains(plain);
    }
    private static String strip(String s) { return s == null ? "" : s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip(); }

    // ---------------------------------------------------------------- preview (/대상체력바미리보기)

    private static long previewStart;
    private static void startPreview(int kind) {
        long now = Util.getMeasuringTimeMs();
        previewStart = now; previewUntil = now + 15000;
        preview = switch (kind) {
            case 1 -> new TargetHudProtocol.Show(-1, new UUID(0, 1), TargetHudProtocol.KIND_CUSTOM, TargetHudProtocol.FLAG_SHINY, "달빛 늑대", "",
                720, 1000, 160, 240, 280, .38f);
            case 2 -> new TargetHudProtocol.Show(-1, new UUID(0, 2), TargetHudProtocol.KIND_PLAYER, 0, "차카", "", 14, 20, 82, 120, 12, -1);
            default -> new TargetHudProtocol.Show(-1, new UUID(0, 3), TargetHudProtocol.KIND_VANILLA, 0, "달빛 늑대", "", 720, 1000, -1, 0, 280, .38f);
        };
        health = -1; mana = -1;
        var c = MinecraftClient.getInstance();
        if (c.player != null) StatusClient.preview(c.player.getId(), List.of(
            new StatusClient.Line(StatusProtocol.Status.SLOW, now, now + 8000),
            new StatusClient.Line(StatusProtocol.Status.BURN, now, now + 12000),
            new StatusClient.Line(StatusProtocol.Status.MANA_BURN, now, now + 5000)));
    }
    private static List<StatusClient.Line> previewLines(long now) {
        var all = List.of(
            new StatusClient.Line(StatusProtocol.Status.POISON, previewStart, previewStart + 14000),
            new StatusClient.Line(StatusProtocol.Status.STUN, previewStart, previewStart + 6000),
            new StatusClient.Line(StatusProtocol.Status.BURN, previewStart, previewStart + 10000),
            new StatusClient.Line(StatusProtocol.Status.SLOW, previewStart, previewStart + 15000));
        var out = new ArrayList<StatusClient.Line>();
        for (var l : all) if (l.end() > now) out.add(l);
        return out;
    }
    private static ElementMarkClient.Shown previewMark(long now) {
        long start = previewStart + ((now - previewStart) / 3000) * 3000;
        return new ElementMarkClient.Shown(ElementProtocol.Element.WATER, start, start + 3000);
    }
}
