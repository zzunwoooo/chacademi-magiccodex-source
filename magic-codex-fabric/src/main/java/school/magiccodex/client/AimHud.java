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
        if (c.player == null || c.world == null || c.options.hudHidden) return;
        if (c.currentScreen != null && !(c.currentScreen instanceof HudCursorScreen)) return;
        int width = c.getWindow().getScaledWidth(), height = c.getWindow().getScaledHeight();
        boolean targeting = AimTargeting.targeting();
        int color = targeting ? AimRenderer.elementColor(AimTargeting.spell().category()) : 0xF4ECD8;
        if (ShoulderCamera.active() && c.options.getPerspective() == Perspective.THIRD_PERSON_BACK && c.currentScreen == null && !c.player.isSpectator())
            ctx.drawGuiTexture(RenderLayer::getCrosshair,CROSSHAIR,width/2-7,height/2-7,15,15);
        if (targeting) hint(ctx, c, width, height, color);
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
        float textSize = 17, icon = 26, mouse = 22, gap = 7;
        String cast = "발동", cancel = "취소";
        float castW = text.width(cast, textSize, BODY), cancelW = text.width(cancel, textSize, BODY);
        float total = icon + gap * 2 + mouse + 6 + castW + gap * 2 + mouse + 6 + cancelW;
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(width/2f,Math.max(24,height-74),200);
            ctx.getMatrices().scale(scale, scale, 1);
            float x = -total / 2;
            images.drawCentered(ctx, spell.icon(), x + icon / 2, 0, (int) icon, 0xFFFFFFFF);
            x += icon + gap * 2;
            images.drawCentered(ctx, MOUSE_LEFT, x + mouse / 2, 0, (int) mouse, 0xFF000000 | rgb);
            x += mouse + 6;
            text.draw(ctx, cast, x, 0, textSize, 0xFFF4ECD8, BODY, false);
            x += castW + gap * 2;
            images.drawCentered(ctx, MOUSE_RIGHT, x + mouse / 2, 0, (int) mouse, 0xFFE6E0D2);
            x += mouse + 6;
            text.draw(ctx, cancel, x, 0, textSize, 0xFFF4ECD8, BODY, false);
            if(preview!=null){double reach=preview.shape().mode()==SpellTargets.Mode.LINE||preview.shape().mode()==SpellTargets.Mode.CONE?preview.shape().length():preview.shape().range();text.draw(ctx,"최대 사거리 "+String.format(java.util.Locale.ROOT,"%.1f",reach)+"m",0,-25,15,0xFF000000|rgb,BODY,true);}
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
