package school.magiccodex.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Screen part of aiming: the magic cursor, the hint bar while a spell is aimed
 * (spell icon, left click = cast, right click = cancel) and the shoulder-view crosshair.
 * Text uses the HUD's own fonts (magiccodex:label / magiccodex:hud_bold) through CodexTypography.
 */
public final class AimHud {
    private static final Identifier BODY = Identifier.of("magiccodex", "label");
    private static final Identifier CROSSHAIR = Identifier.ofVanilla("hud/crosshair");
    private static final String MOUSE_LEFT = "magiccodex:textures/aim/mouse_left.png", MOUSE_RIGHT = "magiccodex:textures/aim/mouse_right.png";
    private static HudTextureCache images;
    private static CodexTypography text;
    private static int generation = -1;

    private AimHud() {}

    static void close() {
        if (images != null) { images.close(); images = null; }
        if (text != null) { text.close(); text = null; }
    }

    static void render(DrawContext ctx) {
        var c = MinecraftClient.getInstance();
        AimCursor.update();
        if (c.player == null || c.world == null || c.options.hudHidden) return;
        if (c.currentScreen != null && !(c.currentScreen instanceof HudCursorScreen)) return;
        int width = c.getWindow().getScaledWidth(), height = c.getWindow().getScaledHeight();
        boolean targeting = AimTargeting.targeting();
        int color = targeting ? AimRenderer.elementColor(AimTargeting.spell().category()) : 0xF4ECD8;
        if (AimCursor.aiming() && !(c.currentScreen instanceof HudCursorScreen)) {
            cursor(ctx, AimCursor.screenX(), AimCursor.screenY(), color, targeting);
        } else if (ShoulderCamera.active() && c.options.getPerspective() == Perspective.THIRD_PERSON_BACK && c.currentScreen == null && !c.player.isSpectator()) {
            shoulderCrosshair(ctx, c);
        }
        if (targeting) hint(ctx, c, width, height, color);
    }

    /** Thin "+" with a dark outline, matching the reference cursor. */
    private static void cursor(DrawContext ctx, float x, float y, int rgb, boolean targeting) {
        float arm = targeting ? 6.5f : 5.5f, gap = 1.6f;
        int shade = 0xA0000000, ink = 0xFF000000 | rgb;
        for (int pass = 0; pass < 2; pass++) {
            float t = pass == 0 ? 2.6f : 1.1f;
            int col = pass == 0 ? shade : ink;
            HudMesh.line(ctx, x - arm, y, x - gap, y, t, col);
            HudMesh.line(ctx, x + gap, y, x + arm, y, t, col);
            HudMesh.line(ctx, x, y - arm, x, y - gap, t, col);
            HudMesh.line(ctx, x, y + gap, x, y + arm, t, col);
        }
    }

    /** Over-the-shoulder: draw the vanilla crosshair where the character's own look ray really lands. */
    private static void shoulderCrosshair(DrawContext ctx, MinecraftClient c) {
        HitResult hit = c.crosshairTarget;
        float tickDelta = c.getRenderTickCounter().getTickDelta(true);
        Vec3d point = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getPos()
                : c.player.getCameraPosVec(tickDelta).add(c.player.getRotationVec(tickDelta).multiply(48));
        float[] s = AimView.toScreen(point);
        if (s == null) return;
        int x = Math.round(s[0]), y = Math.round(s[1]);
        ctx.drawGuiTexture(RenderLayer::getCrosshair, CROSSHAIR, x - 7, y - 7, 15, 15);
    }

    private static void hint(DrawContext ctx, MinecraftClient c, int width, int height, int rgb) {
        var spell = AimTargeting.spell();
        var preview = AimTargeting.preview();
        if (generation != UiResources.generation()) { close(); generation = UiResources.generation(); }
        if (images == null) images = new HudTextureCache(c);
        if (text == null) text = new CodexTypography(c);
        images.beginFrame();
        text.beginFrame();
        float scale = Math.clamp(Math.min(width / 1100f, height / 750f), .4f, 1f);
        float textSize = 20, icon = 34, mouse = 30, gap = 10;
        String cast = "발동", cancel = "취소";
        float castW = text.width(cast, textSize, BODY), cancelW = text.width(cancel, textSize, BODY);
        float total = icon + gap * 2 + mouse + 6 + castW + gap * 2 + mouse + 6 + cancelW;
        ctx.getMatrices().push();
        try {
            float anchorX=AimCursor.aiming()?AimCursor.screenX():width/2f,anchorY=AimCursor.aiming()?AimCursor.screenY():height/2f;
            float half=(total/2+16)*scale;
            ctx.getMatrices().translate(Math.clamp(anchorX,half,width-half),Math.clamp(anchorY+36*scale,30*scale,height-70*scale),200);
            ctx.getMatrices().scale(scale, scale, 1);
            HudMesh.quad(ctx, -total / 2 - 14, -24, total + 28, 48, 0x880B1220, 0x880B1220);
            float x = -total / 2;
            images.drawCentered(ctx, spell.icon(), x + icon / 2, 0, (int) icon, 0xFFFFFFFF);
            x += icon + gap * 2;
            images.drawCentered(ctx, MOUSE_LEFT, x + mouse / 2, 0, (int) mouse, 0xFF000000 | rgb);
            x += mouse + 6;
            text.drawOutline(ctx, cast, x, 0, textSize, 0xFFF4ECD8, BODY, false);
            x += castW + gap * 2;
            images.drawCentered(ctx, MOUSE_RIGHT, x + mouse / 2, 0, (int) mouse, 0xFFE6E0D2);
            x += mouse + 6;
            text.drawOutline(ctx, cancel, x, 0, textSize, 0xFFF4ECD8, BODY, false);
            if (preview != null && !preview.valid() && preview.reason() != null) {
                float blink = .75f + .25f * (float) Math.sin(Util.getMeasuringTimeMs() / 160.0);
                int alpha = Math.round(255 * blink) << 24;
                text.drawOutline(ctx, preview.reason(), 0, 40, 17, alpha | 0xFFB4A8, BODY, true);
            }
        } finally {
            ctx.getMatrices().pop();
            images.endFrame();
        }
    }
}
