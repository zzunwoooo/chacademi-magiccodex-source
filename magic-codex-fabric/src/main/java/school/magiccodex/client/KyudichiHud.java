package school.magiccodex.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import school.magiccodex.protocol.KyudichiProtocol;

/**
 * 큐디치 HUD. Placement avoids the existing widgets:
 *  - score board: top centre (vanilla boss bars are not used for players with this mod; the server sends them only as a fallback)
 *  - kill/goal feed: top right, below the vanilla status-effect icons
 *  - F key + 10 s hold ring: just left of the bottom-right magic slots (same scale as SkillHudRenderer), clear of the broom HUD above them
 *  - ball / shrine / ping markers: in the world, arrows on the screen edge when off-screen
 *  - result card: centre, 12 s
 * Art: textures/gui/kyudichi (owner's CHACADEMI_QUIDDITCH_UI_PNG_v2 atlases, hud ×0.5 and action_keys ×0.4 of the delivered size).
 */
final class KyudichiHud {
    private KyudichiHud() {}
    private static final Identifier HUD = Identifier.of("magiccodex", "textures/gui/kyudichi/hud.png"), KEYS = Identifier.of("magiccodex", "textures/gui/kyudichi/action_keys.png"),
            PANELS = Identifier.of("magiccodex", "textures/gui/kyudichi/panels.png");
    private static final int HUD_W = 836, HUD_H = 470, KEYS_W = 869, KEYS_H = 290, PANELS_W = 1536, PANELS_H = 1024;
    private static final int[] SCORE = {10, 62, 300, 102}, FEED = {310, 77, 230, 72}, LOCK = {685, 205, 110, 130};
    private static final int[][] BALL = {{50, 205, 140, 137}, {245, 205, 142, 137}, {412, 207, 230, 135}};
    private static final int[][] ARROW = {{57, 350, 117, 90}, {250, 350, 117, 90}, {477, 350, 117, 90}};
    private static final int[] KEY_X = {0, 174, 347, 521, 694};   // action_keys: disabled, catch quaffle, throw, deflect bludger, catch snitch
    private static final int[] RESULT = {1020, 80, 500, 400};
    static final String[] PING_ICONS = {"location", "ally", "enemy", "warning", "on_my_way", "help", "heal_me", "quaffle", "bludger", "snitch",
            "shrine_health", "shrine_mana", "shrine_stability", "goal", "shrine_haste"};
    private static final String[] WHEEL = {"idle", "warning", "on_my_way", "help", "heal_me"};
    private static final Identifier BOLD = Identifier.of("magiccodex", "hud_bold"), LABEL = Identifier.of("magiccodex", "label"), TITLE = Identifier.of("magiccodex", "section");
    static final int BLUE = 0xFF5AA8FF, RED = 0xFFFF6B6B, GOLD = 0xFFE7CE91;

    private static HudTextureCache images; private static CodexTypography text; private static int generation = -1;
    private static float forfeitX, forfeitY, forfeitW, forfeitH; private static boolean forfeitShown;

    static void close() { if (images != null) { images.close(); images = null; } if (text != null) { text.close(); text = null; } }

    static boolean forfeitHit(double x, double y) { return forfeitShown && x >= forfeitX && x <= forfeitX + forfeitW && y >= forfeitY && y <= forfeitY + forfeitH; }

    static void render(DrawContext ctx, RenderTickCounter ticks) {
        var c = MinecraftClient.getInstance();
        boolean active = KyudichiClient.active();
        long now = Util.getMeasuringTimeMs();
        boolean showResult = KyudichiClient.result != null && now - KyudichiClient.resultAt < 12_000;
        if (!active && !showResult) { forfeitShown = false; return; }
        if (c.player == null || c.options.hudHidden || !HudPointer.hudVisibleUnder(c.currentScreen)) return;
        if (generation != UiResources.generation()) { close(); generation = UiResources.generation(); }
        if (images == null) images = new HudTextureCache(c);
        if (text == null) text = new CodexTypography(c);
        images.beginFrame(); text.beginFrame();
        float w = c.getWindow().getScaledWidth(), h = c.getWindow().getScaledHeight();
        float s = Math.min(w / 1920f, h / 1080f);
        try {
            if (active) {
                var m = KyudichiClient.match;
                if (m.phase() != 0) { markers(ctx, c, w, h, now, ticks.getTickDelta(false)); }
                scoreboard(ctx, m, w, s, now);
                feed(ctx, w, s, now);
                if (!KyudichiClient.spectator()) keyIcon(ctx, w, h, now);
                magnetAndWheel(ctx, c, s, now);
            } else forfeitShown = false;
            if (showResult) result(ctx, KyudichiClient.result, w, h, s, now);
        } finally { images.endFrame(); }
    }

    // ================================================================== score board (top centre)
    private static void scoreboard(DrawContext ctx, KyudichiProtocol.Match m, float w, float s, long now) {
        float bw = 420, bh = bw * SCORE[3] / SCORE[2];
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(w / 2f - bw * s / 2f, 8 * s, 140);
            ctx.getMatrices().scale(s, s, 1);
            atlas(ctx, HUD, SCORE, 0, 0, bw, bh, HUD_W, HUD_H, 0xFFFFFFFF);
            float cy = bh / 2f;
            int mine = m.myTeam();
            teamBox(ctx, "파랑 팀", m.scoreA(), 84, cy, BLUE, mine == 0);
            teamBox(ctx, "빨강 팀", m.scoreB(), 336, cy, RED, mine == 1);
            int left = Math.max(0, m.timeLeftMs() - (int) (now - KyudichiClient.matchAt));
            String time = String.format("%02d:%02d", left / 60000, (left / 1000) % 60);
            String sub = switch (m.phase()) { case 0 -> "준비"; case 1 -> "곧 시작"; case 3 -> "골!"; default -> (m.flags() & 2) != 0 ? "골든골" : ""; };
            outlined(ctx, time, 210, cy - (sub.isEmpty() ? 0 : 6), 30, 0xFFFFFFFF, BOLD, true);
            if (!sub.isEmpty()) outlined(ctx, sub, 210, cy + 20, 14, GOLD, LABEL, true);
            // snitch below the centre box: locked until it appears
            boolean out = (m.flags() & 1) != 0;
            float sw = 64, sh = sw * BALL[2][3] / BALL[2][2];
            float pulse = out ? 1 + .06f * (float) Math.sin(now / 160.0) : 1;
            atlas(ctx, HUD, BALL[2], 210 - sw * pulse / 2, bh + 2, sw * pulse, sh * pulse, HUD_W, HUD_H, out ? 0xFFFFFFFF : 0x99FFFFFF);
            if (!out) atlas(ctx, HUD, LOCK, 210 - 9, bh + 6, 18, 21, HUD_W, HUD_H, 0xFFFFFFFF);
            // forfeit button: only with the Alt cursor, or while my team votes
            boolean vote = (m.flags() & 8) != 0;
            forfeitShown = (HudPointer.active() || vote) && mine != 2 && m.phase() == 2;
            if (forfeitShown) {
                float fx = bw + 12, fy = cy - 15, fw = vote ? 120 : 64, fh = 30;
                HudMesh.capsule(ctx, fx, fy, fw, fh, vote ? 0xFFE36571 : 0xFF6E7D84, vote ? 0xFFB83A48 : 0xFF46525A);
                HudMesh.capsule(ctx, fx + 1.5f, fy + 1.5f, fw - 3, fh - 3, 0xF2132433, 0xF20C1F32);
                outlined(ctx, vote ? "포기 투표 중 (찬성)" : "포기", fx + fw / 2, fy + fh / 2, 14, vote ? 0xFFFFC2C8 : 0xFFE0E4E8, LABEL, true);
                forfeitX = w / 2f - bw * s / 2f + fx * s; forfeitY = 8 * s + fy * s; forfeitW = fw * s; forfeitH = fh * s;
            }
        } finally { ctx.getMatrices().pop(); }
    }

    private static void teamBox(DrawContext ctx, String label, int score, float cx, float cy, int color, boolean mine) {
        outlined(ctx, label + (mine ? " (우리)" : ""), cx, cy - 22, 13, mine ? GOLD : 0xFFB8C4CF, LABEL, true);
        outlined(ctx, String.valueOf(score), cx, cy + 6, 34, color, BOLD, true);
    }

    // ================================================================== feed (top right, under status effects)
    private static void feed(DrawContext ctx, float w, float s, long now) {
        KyudichiClient.feed.removeIf(f -> now - f.at() > 6000);
        float rw = 300, rh = rw * FEED[3] / FEED[2];
        int i = 0;
        for (int k = KyudichiClient.feed.size() - 1; k >= 0 && i < 4; k--, i++) {
            var line = KyudichiClient.feed.get(k);
            long age = now - line.at();
            float alpha = age > 5000 ? 1 - (age - 5000) / 1000f : Math.min(1, age / 150f);
            float slide = age < 150 ? (1 - age / 150f) * 40 : 0;
            int a = Math.round(alpha * 255) << 24;
            ctx.getMatrices().push();
            try {
                ctx.getMatrices().translate(w - (rw + 16 - slide) * s, (120 + i * (rh + 4)) * s, 140);
                ctx.getMatrices().scale(s, s, 1);
                atlas(ctx, HUD, FEED, 0, 0, rw, rh, HUD_W, HUD_H, a | 0xFFFFFF);
                var f = line.feed();
                int team = f.team() == 0 ? BLUE : RED;
                String body = switch (f.type()) {
                    case 1 -> f.actor() + "  골 +" + f.points();
                    case 2 -> f.actor().isEmpty() ? f.target() + " 쓰러짐" : f.actor() + " → " + f.target() + "  처치";
                    case 3 -> f.actor() + "  스니치 +" + f.points();
                    case 4 -> "골든골 연장!";
                    default -> f.actor().isEmpty() ? (f.team() == 0 ? "파랑 팀" : "빨강 팀") + " 포기" : f.actor() + " 나감";
                };
                int color = f.type() == 3 || f.type() == 4 ? GOLD : team;
                outlined(ctx, body, rw / 2, rh / 2, 17, (color & 0xFFFFFF) | a, BOLD, true);
            } finally { ctx.getMatrices().pop(); }
        }
    }

    // ================================================================== F key (left of the magic slots)
    private static void keyIcon(DrawContext ctx, float w, float h, long now) {
        var k = KyudichiClient.key;
        float s1 = (PlayerHudClient.active() ? 1f : 1.25f) * Math.min(w / 1672f, h / 941f);
        float left = w - 402 * s1, top = h - 76 * s1;
        if (!PlayerHudClient.active() && left < w / 2f + 98) top = Math.min(top, h - 38 - 58 * s1);
        float cx = left - 44 * s1, cy = top + 26 * s1;
        int action = KyudichiClient.live() ? k.action() : 0;
        float lit = action != 0 ? Math.max(0, 1 - (now - KyudichiClient.keyLitAt) / 220f) : 0;
        float ih = 64 * s1 * (1 + .12f * lit), iw = ih * 174f / 200f;
        long age = now - KyudichiClient.keyAt;
        float cooldown = k.cooldownMs() > 0 ? Math.max(0, 1 - age / (float) k.cooldownMs()) : 0;
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(0, 0, 140);
            images.drawTexture(ctx, KEYS, Math.round(cx - iw / 2), Math.round(cy - ih / 2), KEY_X[action], 36, Math.round(iw), Math.round(ih), 174, 200, KEYS_W, KEYS_H,
                    cooldown > 0 ? 0xFF8A8A8A : 0xFFFFFFFF);
            if (cooldown > 0) HudMesh.arc(ctx, cx, cy, 24 * s1, 4 * s1, (float) (-Math.PI / 2), (float) (Math.PI * 2 * cooldown), 0xCC9AA3AB);
            int hold = Math.max(0, k.holdLeftMs() - (int) age);
            if (hold > 0) {
                float f = hold / 10_000f;
                HudMesh.arc(ctx, cx, cy, 36 * s1, 4.5f * s1, 0, (float) (Math.PI * 2), 0x88101925);
                HudMesh.arc(ctx, cx, cy, 36 * s1, 4.5f * s1, (float) (-Math.PI / 2), (float) (Math.PI * 2 * f), hold < 3000 && (now / 150) % 2 == 0 ? 0xFFFF4040 : 0xFFFF9A2E);
                outlined(ctx, String.valueOf((hold + 999) / 1000), cx + 30 * s1, cy - 30 * s1, Math.max(9, 22 * s1), 0xFFFFFFFF, BOLD, true);
            }
            String label = KyudichiClient.keyLabel();
            if (!label.equalsIgnoreCase("F")) outlined(ctx, label, cx, cy - ih / 2 - 6 * s1, 12, GOLD, BOLD, true);
        } finally { ctx.getMatrices().pop(); }
    }

    // ================================================================== world markers
    private static void markers(DrawContext ctx, MinecraftClient c, float w, float h, long now, float delta) {
        if (!AimView.valid()) return;
        Vec3d cam = AimView.camera();
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(0, 0, 130);
            for (var sh : KyudichiClient.shrines) {
                Vec3d p = new Vec3d(sh.x(), sh.y(), sh.z());
                float[] sp = AimView.toScreen(p);
                if (sp == null || !onScreen(sp, w, h, 8)) continue;
                double d = p.distanceTo(cam);
                int size = Math.round((float) Math.clamp(26 - d * 0.15, 14, 26));
                images.drawCentered(ctx, "magiccodex:textures/gui/kyudichi/ping/" + PING_ICONS[sh.type() == 3 ? 14 : 10 + sh.type()] + ".png", sp[0], sp[1], size, 0xE6FFFFFF);
            }
            var balls = KyudichiClient.balls;
            if (balls != null) for (var b : balls.balls()) {
                Vec3d p = KyudichiClient.ball(b);
                boolean danger = b.kind() == 1 && (b.flags() & 2) != 0;
                int tint = danger && (now / 140) % 2 == 0 ? 0xFFFF5050 : 0xFFFFFFFF;
                float[] sp = AimView.toScreen(p);
                double d = p.distanceTo(cam);
                if (sp != null && onScreen(sp, w, h, 24)) {
                    float size = (float) Math.clamp(24 - d * 0.12, 14, 24) * (b.kind() == 2 ? 1.4f : 1);
                    var r = BALL[b.kind()];
                    float bw = size, bh = size * r[3] / r[2];
                    atlas(ctx, HUD, r, sp[0] - bw / 2, sp[1] - bh / 2 - size, bw, bh, HUD_W, HUD_H, tint);
                    outlined(ctx, Math.round(d) + "m", sp[0], sp[1] - size * .3f + 2, 9, 0xFFFFFFFF, LABEL, true);
                } else edgeArrow(ctx, c, p, sp, w, h, ARROW[b.kind()], BALL[b.kind()], tint, delta);
            }
            KyudichiClient.pings.removeIf(pm -> now - pm.at() > 3000);
            for (var pm : KyudichiClient.pings) {
                var pg = pm.ping();
                Vec3d p = new Vec3d(pg.x(), pg.y(), pg.z());
                if (pg.target() != null) { var e = KyudichiClient.player(pg.target()); if (e != null) p = e.getLerpedPos(delta).add(0, e.getHeight() + .6, 0); }
                long age = now - pm.at();
                float alpha = age > 2400 ? 1 - (age - 2400) / 600f : 1;
                float pop = age < 180 ? 1.4f - .4f * age / 180f : 1;
                int tint = (Math.round(alpha * 255) << 24) | 0xFFFFFF;
                String icon = "magiccodex:textures/gui/kyudichi/ping/" + PING_ICONS[pg.kind()] + ".png";
                float[] sp = AimView.toScreen(p);
                if (sp != null && onScreen(sp, w, h, 20)) {
                    images.drawCentered(ctx, icon, sp[0], sp[1] - 10, Math.round(22 * pop), tint);
                    outlined(ctx, pg.nick(), sp[0], sp[1] + 6, 9, (tint & 0xFF000000) | 0xE7CE91, LABEL, true);
                } else {
                    float[] e = edge(c, p, sp, w, h, 34, delta);
                    images.drawCentered(ctx, icon, e[0], e[1], 22, tint);
                }
            }
        } finally { ctx.getMatrices().pop(); }
    }

    private static boolean onScreen(float[] sp, float w, float h, float m) { return sp[0] >= m && sp[0] <= w - m && sp[1] >= m && sp[1] <= h - m; }

    /** Screen-edge point (and angle in [2]) in the direction of a world point; works for points behind the camera. */
    private static float[] edge(MinecraftClient c, Vec3d world, float[] sp, float w, float h, float inset, float delta) {
        double dx, dy;
        if (sp != null) { dx = sp[0] - w / 2.0; dy = sp[1] - h / 2.0; }
        else {
            Vec3d f = c.player.getRotationVec(delta);
            if (c.options.getPerspective().isFrontView()) f = f.multiply(-1);
            Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
            if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
            right = right.normalize();
            Vec3d up = right.crossProduct(f).normalize();
            Vec3d rel = world.subtract(AimView.camera());
            dx = rel.dotProduct(right); dy = -rel.dotProduct(up);
            if (Math.abs(dx) < 1e-3 && Math.abs(dy) < 1e-3) dy = 1;   // straight behind: bottom edge
        }
        double len = Math.hypot(dx, dy); if (len < 1e-6) { dx = 0; dy = 1; len = 1; }
        dx /= len; dy /= len;
        double rx = w / 2.0 - inset, ry = h / 2.0 - inset;
        double t = 1 / Math.sqrt((dx / rx) * (dx / rx) + (dy / ry) * (dy / ry));
        return new float[]{(float) (w / 2.0 + dx * t), (float) (h / 2.0 + dy * t), (float) Math.atan2(dy, dx)};
    }

    private static void edgeArrow(DrawContext ctx, MinecraftClient c, Vec3d p, float[] sp, float w, float h, int[] arrow, int[] icon, int tint, float delta) {
        float[] e = edge(c, p, sp, w, h, 30, delta);
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(e[0], e[1], 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(e[2] + (float) (Math.PI / 2)));   // arrow art points up
            atlas(ctx, HUD, arrow, -9, -26, 18, 14, HUD_W, HUD_H, tint);
        } finally { ctx.getMatrices().pop(); }
        float iw = icon == BALL[2] ? 26 : 18, ih = iw * icon[3] / icon[2];
        atlas(ctx, HUD, icon, e[0] - iw / 2, e[1] - ih / 2, iw, ih, HUD_W, HUD_H, tint);
    }

    // ================================================================== ping preview (Alt): magnet ring, direction menu
    private static void magnetAndWheel(DrawContext ctx, MinecraftClient c, float s, long now) {
        if (!(c.currentScreen instanceof HudCursorScreen) || KyudichiClient.spectator()) return;
        double mx = HudPointer.x(), my = HudPointer.y();
        var g = KyudichiClient.gesture;
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(0, 0, 160);
            if (g == null || KyudichiClient.direction(mx - g.x(), my - g.y()) < 0) {
                var t = KyudichiClient.Magnet.find(g == null ? mx : g.x(), g == null ? my : g.y());
                if (t != null) {
                    float r = 13 + (float) Math.sin(now / 120.0);
                    HudMesh.arc(ctx, t.sx(), t.sy(), r, 2f, 0, (float) (Math.PI * 2), 0xFFE7CE91);
                }
            }
            if (g != null) {
                int dir = KyudichiClient.direction(mx - g.x(), my - g.y());
                int state = switch (dir) { case 3 -> 1; case 4 -> 2; case 5 -> 3; case 6 -> 4; default -> 0; };
                images.drawCentered(ctx, "magiccodex:textures/gui/kyudichi/ping/wheel_" + WHEEL[state] + ".png", (float) g.x(), (float) g.y(), Math.round(150 * s * 1.6f), 0xF2FFFFFF);
            }
        } finally { ctx.getMatrices().pop(); }
    }

    // ================================================================== result card
    private static void result(DrawContext ctx, KyudichiProtocol.Result r, float w, float h, float s, long now) {
        float pw = 760, ph = pw * RESULT[3] / RESULT[2];
        long age = now - KyudichiClient.resultAt;
        float alpha = age > 11_000 ? 1 - (age - 11_000) / 1000f : Math.min(1, age / 250f);
        int a = Math.round(alpha * 255) << 24;
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(w / 2f - pw * s / 2f, h / 2f - ph * s / 2f, 200);
            ctx.getMatrices().scale(s, s, 1);
            atlas(ctx, PANELS, RESULT, 0, 0, pw, ph, PANELS_W, PANELS_H, a | 0xFFFFFF);
            String head = switch (r.outcome()) { case 1 -> "승리"; case 0 -> "패배"; case 2 -> "무승부"; default -> "경기 무효"; };
            int headColor = r.outcome() == 1 ? 0xF6D47A : r.outcome() == 0 ? 0xFF7A7A : 0xC9D1D9;
            outlined(ctx, head, pw / 2, 64, 44, a | headColor, TITLE, true);
            outlined(ctx, r.scoreA() + "  :  " + r.scoreB(), pw / 2, 132, 38, a | 0xFFFFFF, BOLD, true);
            outlined(ctx, "파랑 팀", pw / 2 - 150, 132, 16, a | (BLUE & 0xFFFFFF), LABEL, true);
            outlined(ctx, "빨강 팀", pw / 2 + 150, 132, 16, a | (RED & 0xFFFFFF), LABEL, true);
            float y = 186;
            outlined(ctx, "골   처치   데스   스니치", pw - 190, y, 13, a | 0x9FB0BF, LABEL, true);
            y += 26;
            for (int team = 0; team < 2; team++) for (var l : r.lines()) {
                if (l.team() != team) continue;
                int color = team == 0 ? BLUE : RED;
                outlined(ctx, (l.mvp() ? "♛ " : "") + l.nick(), 70, y, 18, a | (l.mvp() ? 0xF6D47A : color & 0xFFFFFF), BOLD, false);
                outlined(ctx, l.goals() + "      " + l.kills() + "       " + l.deaths() + "        " + l.snitch(), pw - 190, y, 17, a | 0xFFFFFF, LABEL, true);
                y += 28;
            }
            if (r.outcome() != 3) {
                String delta = (r.delta() >= 0 ? "+" : "") + r.delta();
                String text = r.delta() == 0 && r.outcome() != 2 ? "레이팅 변동 없음 (테스트·비레이팅 경기)" : "레이팅 " + (r.rating() - r.delta()) + " → " + r.rating() + "  (" + delta + ")";
                outlined(ctx, text, pw / 2 + 24, ph - 54, 20, a | (r.delta() >= 0 ? 0x7FE3A5 : 0xFF8F8F), BOLD, true);
                String[] tiers = {"bronze", "silver", "gold", "platinum", "diamond", "master", "challenger_1", "challenger_2", "challenger_3"};
                images.drawCentered(ctx, "magiccodex:textures/gui/kyudichi/tier/" + tiers[Math.clamp(r.tierAfter(), 0, 8)] + ".png", pw / 2 - 200, ph - 56, 44, a | 0xFFFFFF);
            }
        } finally { ctx.getMatrices().pop(); }
    }

    // ================================================================== helpers
    private static void atlas(DrawContext ctx, Identifier tex, int[] r, float x, float y, float w, float h, int tw, int th, int tint) {
        images.drawTexture(ctx, tex, Math.round(x), Math.round(y), r[0], r[1], Math.round(w), Math.round(h), r[2], r[3], tw, th, tint);
    }

    private static void outlined(DrawContext ctx, String value, float x, float y, float size, int color, Identifier face, boolean centered) {
        text.drawOutline(ctx, value, x, y, size, (color & 0xFF000000) | 0x10141A, face, centered);
        text.draw(ctx, value, x, y, size, color, face, centered);
    }
}
