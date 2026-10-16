package school.magiccodex.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Regions of the owner's 큐디치 window atlases (textures/gui/kyudichi/panels.png 1536×1024, controls.png 1672×941). */
final class KyudichiArt {
    private KyudichiArt() {}
    static final Identifier PANELS = Identifier.of("magiccodex", "textures/gui/kyudichi/panels.png"),
            CONTROLS = Identifier.of("magiccodex", "textures/gui/kyudichi/controls.png");
    // panels: {x, y, w, h, textureW, textureH}
    static final int[] P_WIDE = {37, 96, 474, 372, 1536, 1024}, P_WIDE2 = {530, 97, 477, 372, 1536, 1024}, P_FOOTER = {1026, 97, 475, 371, 1536, 1024},
            P_PORTRAIT = {127, 511, 292, 397, 1536, 1024}, P_SLOTS = {1100, 511, 331, 397, 1536, 1024}, P_SMALL = {537, 593, 463, 229, 1536, 1024};
    // controls
    static final int[] BTN = {53, 133, 375, 169, 1672, 941}, BTN_GLOW = {463, 131, 382, 173, 1672, 941}, BTN_PLAIN = {877, 136, 366, 165, 1672, 941},
            BTN_OFF = {1271, 134, 350, 168, 1672, 941}, ROW = {63, 394, 590, 167, 1672, 941}, FIELD = {688, 414, 343, 136, 1672, 941},
            SQUARE = {1085, 386, 205, 197, 1672, 941}, SQUARE2 = {1381, 375, 217, 208, 1672, 941},
            ICON_OK = {189, 639, 184, 186, 1672, 941}, ICON_WAIT = {551, 640, 191, 192, 1672, 941}, ICON_X = {912, 642, 186, 187, 1672, 941},
            BTN_RED = {1227, 652, 391, 166, 1672, 941};
    static final String[] TIERS = {"bronze", "silver", "gold", "platinum", "diamond", "master", "challenger_1", "challenger_2", "challenger_3"};
    static final String[] TIER_NAMES = {"브론즈", "실버", "골드", "플래티넘", "다이아몬드", "마스터", "챌린저 1", "챌린저 2", "챌린저 3"};
    static String tierIcon(int tier) { return "magiccodex:textures/gui/kyudichi/tier/" + TIERS[Math.clamp(tier, 0, 8)] + ".png"; }

    static void draw(HudTextureCache images, DrawContext ctx, Identifier tex, int[] r, float x, float y, float w, float h, int tint) {
        images.drawTexture(ctx, tex, Math.round(x), Math.round(y), r[0], r[1], Math.round(w), Math.round(h), r[2], r[3], r[4], r[5], tint);
    }
}
