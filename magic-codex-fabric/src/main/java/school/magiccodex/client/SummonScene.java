package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Artifact summon reveal (유물함 소환), pure timeline/drawing logic with no Minecraft types so it can be rendered
 * offline for checking. Reference space 1672 x 941 (same as the approved summon background).
 *
 * Flow (approved 6-panel reference): five identical closed chests → pick one → only that one opens with a grade
 * telegraph → result → the other four flash their (decoy) grade → result card.
 * Grade telegraph while opening: lit magic-circle rings = grade + 1, light colour = grade colour,
 * 유일 starts cyan then turns purple, 전설 adds a blackout and a falling golden star before the seal breaks.
 * Glows / rays / sparkles are additive, smoke is alpha (summon PNG pack notes).
 */
final class SummonScene {
    static final float W = 1672, H = 941;
    static final int[] GRADE = {0xFFDCE3EA, 0xFF71E5F2, 0xFFD2A8FF, 0xFFFFD582};
    static final String[] GRADE_NAME = {"일반", "고급", "유일", "전설"};
    static final float[] CHEST_X = {270, 553, 836, 1119, 1402};
    static final float BASE = 640;                       // chest bottom line in the row
    static final float CX = 836, CBASE = 735;            // chosen chest bottom when centred
    static final float CLOSED_W = 250, CLOSED_H = 177, OPEN_W = 244, OPEN_H = 217;
    static final float SEAM = 0.47f;                     // lid seam as a fraction of the closed chest height from its top

    enum State { IDLE, WAITING, PICK, OPENING, REVEAL, CARD }
    enum Face { TITLE, BOLD, LABEL, BODY }
    enum Cue { RING, LEAK, SHIFT, BLACKOUT, METEOR, IMPACT, OPEN, STAR, REVEAL_CHEST, CARD }

    /** Drawing back end (Minecraft DrawContext in game, a software rasteriser for the offline preview). */
    interface Canvas {
        void sprite(String name, float cx, float cy, float w, float h, float rotDeg, float squashY, int argb, boolean additive);
        void fillScreen(int argb);
        void rect(float cx, float cy, float w, float h, int argb);
        void text(String value, float x, float y, float size, int argb, Face face, boolean centered);
        void icon(String path, float cx, float cy, float size, int argb);
    }

    State state = State.IDLE;
    long stateStart;
    int hover = -1, picked = -1, grade;
    int[] decoys = new int[4];
    String name = "", icon = "";
    boolean isNew;
    private final float[] lift = new float[5];
    private double lastT = -1;
    private final List<Cue> fired = new ArrayList<>();

    // ------------------------------------------------------------------ timeline

    void set(State next, long now) { state = next; stateStart = now; lastT = -1; }
    double t(long now) { return (now - stateStart) / 1000.0; }

    int rings() { return grade + 1; }
    double leakStart() { return 0.6 + 0.25 * rings() + 0.2 + (grade == 3 ? 1.0 : 0); }
    double openAt() { return leakStart() + 1.0; }
    double blackout() { return 0.6 + 0.25 * rings() + 0.2; }
    double meteorStart() { return blackout() + 0.45; }
    double impact() { return blackout() + 0.9; }
    double shiftAt() { return leakStart() + 0.55; }
    /** The reveal can be skipped to this point (result fully shown). */
    double settled() { return openAt() + 1.0 + 0.15 * rings(); }
    boolean continueReady(long now) { return state == State.OPENING && t(now) >= openAt() + 1.2; }
    void skip(long now) { if (state == State.OPENING && t(now) < openAt()) stateStart = now - (long) (openAt() * 1000) - 1; }

    /** Sound cues crossed since the last call (the screen maps them to vanilla sounds). */
    List<Cue> cues(long now) {
        fired.clear();
        double t = t(now);
        if (state == State.OPENING) {
            for (int i = 0; i < rings(); i++) cross(t, 0.6 + 0.25 * i, Cue.RING);
            if (grade == 3) { cross(t, blackout(), Cue.BLACKOUT); cross(t, meteorStart(), Cue.METEOR); cross(t, impact(), Cue.IMPACT); }
            cross(t, leakStart(), Cue.LEAK);
            if (grade == 2) cross(t, shiftAt(), Cue.SHIFT);
            cross(t, openAt(), Cue.OPEN);
            for (int i = 0; i < rings(); i++) cross(t, openAt() + 1.0 + 0.15 * i, Cue.STAR);
        } else if (state == State.REVEAL) {
            int k = 0;
            for (int i = 0; i < 5; i++) if (i != picked) cross(t, 0.25 + 0.14 * k++, Cue.REVEAL_CHEST);
        } else if (state == State.CARD) cross(t, 0.0, Cue.CARD);
        lastT = t;
        return fired;
    }
    private void cross(double t, double at, Cue cue) { if (lastT < at && t >= at) fired.add(cue); }

    /** Chest under the mouse in the row (PICK / IDLE), or -1. */
    int chestAt(double x, double y) {
        for (int i = 0; i < 5; i++) {
            float cx = CHEST_X[i];
            if (x >= cx - CLOSED_W / 2 && x <= cx + CLOSED_W / 2 && y >= BASE - CLOSED_H - 30 && y <= BASE + 10) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    void draw(Canvas c, long now, long clock) {
        double t = t(now), time = clock / 1000.0;
        motes(c, time);
        switch (state) {
            case IDLE, WAITING, PICK -> row(c, time, t);
            case OPENING -> opening(c, t, time);
            case REVEAL -> reveal(c, t, time);
            case CARD -> card(c, t, time);
        }
    }

    private void motes(Canvas c, double time) {
        for (int i = 0; i < 18; i++) {
            float x = (float) ((hash(i, 1) * W + time * 6 * (hash(i, 2) - .5)) % W);
            float y = (float) (H - ((time * (12 + 18 * hash(i, 3)) + hash(i, 4) * H) % (H + 60)));
            float a = (float) (.25 + .2 * Math.sin(time * 2 + i));
            c.sprite("fx_mote", x, y, 14 + 10 * hash(i, 5), 14 + 10 * hash(i, 5), 0, 1, rgba(0xFFB9D4FF, a), true);
        }
    }

    private void platform(Canvas c, float x, float base, float width, float time, int color, float alpha) {
        c.sprite("fx_ring_rune", x, base + 6, width, width, (float) (time * 14), .27f, rgba(color, alpha), true);
        c.sprite("fx_glow_wide", x, base + 4, width * .9f, width * .9f, 0, .22f, rgba(color, alpha * .35f), true);
    }

    private void closedChest(Canvas c, float x, float base, float scale, int argb) {
        c.sprite("chest_closed", x, base - CLOSED_H * scale / 2, CLOSED_W * scale, CLOSED_H * scale, 0, 1, argb, false);
    }
    private void openChest(Canvas c, float x, float base, float scale, int argb) {
        c.sprite("chest_open", x, base - OPEN_H * scale / 2, OPEN_W * scale, OPEN_H * scale, 0, 1, argb, false);
    }

    private void row(Canvas c, double time, double t) {
        for (int i = 0; i < 5; i++) {
            boolean over = state == State.PICK && hover == i;
            lift[i] += ((over ? 1f : 0f) - lift[i]) * .25f;
            float l = lift[i];
            float bob = (float) Math.sin(time * 1.8 + i * 1.3) * 5 - 14 * l;
            float x = CHEST_X[i];
            float pulse = state == State.WAITING ? (float) (.5 + .5 * Math.sin(t * 6 + i)) : 0;
            platform(c, x, BASE, 250, (float) time + i, 0xFF8FC8FF, .38f + .45f * l + .2f * pulse);
            if (l > .02f || pulse > 0) {
                c.sprite("fx_glow_wide", x, BASE - CLOSED_H / 2 + bob, 360, 360, 0, 1, rgba(0xFFFFE1A8, .45f * l + .18f * pulse), true);
                for (int k = 0; k < 3; k++) {
                    double a = time * 1.6 + k * 2.09;
                    c.sprite("fx_spark4", x + (float) Math.cos(a) * 120, BASE - CLOSED_H / 2 + bob + (float) Math.sin(a) * 50, 26, 26,
                            (float) (time * 40), 1, rgba(0xFFFFE9C0, .8f * l), true);
                }
            }
            closedChest(c, x, BASE + bob, 1 + .05f * l, 0xFFFFFFFF);
            if (l > .02f) c.sprite("fx_glint", x, BASE - CLOSED_H * SEAM + bob, 200 * l, 60, 0, 1, rgba(0xFFFFF0D0, .55f * l), true);
        }
        if (state == State.PICK) c.text("유물함을 하나 선택하세요", CX, 150, 30, 0xFFF1E3C2, Face.BOLD, true);
    }

    /** Light colour while opening: 유일 starts cyan and turns purple at shiftAt. */
    private int lightColor(double t) { return grade == 2 && t < shiftAt() ? GRADE[1] : GRADE[grade]; }

    private void opening(Canvas c, double t, double time) {
        float gather = ease(clamp01(t / .6));
        // others leave
        for (int i = 0; i < 5; i++) if (i != picked) {
            float a = 1 - clamp01((float) t / .4f);
            if (a <= 0) continue;
            float push = (CHEST_X[i] - CX) * .12f * (1 - a);
            closedChest(c, CHEST_X[i] + push, BASE, 1, rgba(0xFFFFFFFF, a));
        }
        float dim = .45f * gather;
        if (grade == 3) {
            double b = t - blackout();
            if (b > 0) dim = b < .3 ? lerp(.45f, .88f, (float) (b / .3)) : t < impact() ? .88f : lerp(.88f, .5f, clamp01((float) ((t - impact()) / .4)));
        }
        c.fillScreen(rgba(0xFF02050B, dim));

        float x = lerp(CHEST_X[picked], CX, gather), base = lerp(BASE, CBASE, gather), scale = lerp(1, 1.55f, gather);
        int color = lightColor(t);
        // magic circle
        float circleA = clamp01((float) (t - .3) / .4f);
        c.sprite("fx_ring_rune", CX, CBASE + 8, 700, 700, (float) (time * 8), .3f, rgba(0xFF9CC7FF, .28f * circleA), true);
        for (int i = 0; i < rings(); i++) {
            double lit = t - (0.6 + 0.25 * i);
            if (lit < 0) continue;
            float flash = 1 - clamp01((float) lit / .35f);
            float w = 360 + 130 * i;
            int ringColor = grade == 2 && t < shiftAt() ? GRADE[1] : GRADE[grade];
            c.sprite(i % 2 == 0 ? "fx_ring_rune" : "fx_ring", CX, CBASE + 8, w, w, (float) ((i % 2 == 0 ? 1 : -1) * time * (14 + 6 * i)), .3f,
                    rgba(ringColor, .55f + .45f * flash), true);
        }
        if (t > .6) c.sprite("fx_glow_wide", CX, CBASE + 8, 520, 520, 0, .3f, rgba(lightColor(t), .3f * clamp01((float) (t - .6) / .3f)), true);
        if (grade == 3 && t > 0.6 + 0.25 * 4) {
            float a = clamp01((float) (t - (0.6 + 0.25 * 4)) / .4f);
            c.sprite("fx_ring_rune", CX, CBASE + 8, 1150, 1150, (float) (-time * 6), .3f, rgba(GRADE[3], .45f * a), true);
            for (int k = 0; k < 10; k++) {
                double ang = k * Math.PI / 5 - time * .1;
                c.sprite("fx_spark4", CX + (float) Math.cos(ang) * 575, CBASE + 8 + (float) Math.sin(ang) * 172, 34, 34, 0, 1,
                        rgba(GRADE[3], a * (.6f + .4f * (float) Math.sin(time * 5 + k))), true);
            }
        }

        double leak = t - leakStart();
        boolean open = t >= openAt();
        float seamY = base - CLOSED_H * scale * SEAM;
        if (grade == 3) meteor(c, t, x, seamY);
        if (!open) {
            float p = clamp01((float) leak);
            float shake = leak > 0 ? (float) Math.sin(t * 70) * 7 * p * p : 0;
            if (leak > 0) {
                float sp = (float) Math.sqrt(p);
                c.sprite("fx_glow_wide", x, seamY, 320 + 300 * p, 320 + 300 * p, 0, 1, rgba(color, .75f * sp), true);
                for (int k = 0; k < 6; k++) smoke(c, k, x, seamY, time, p, color);
                for (int k = 0; k < 7; k++) ray(c, x + (k - 3) * 22, seamY, -48 + k * 16 + (float) Math.sin(time * 3 + k) * 4, 80, 120 + 300 * p * (.6f + .4f * hash(k, 9)), rgba(color, .85f * sp));
                for (int k = 0; k < 10; k++) {
                    float u = (float) ((time * .7 + hash(k, 11)) % 1);
                    c.sprite("fx_spark4", x + (hash(k, 12) - .5f) * 260, seamY - u * 220, 22, 22, 0, 1, rgba(color, p * (1 - u)), true);
                }
            }
            closedChest(c, x + shake, base, scale, 0xFFFFFFFF);
            if (leak > 0) c.sprite("fx_glint", x + shake, seamY, CLOSED_W * scale * (.7f + .5f * p), 70, 0, 1, rgba(color, .9f * p), true);
            if (grade == 2 && t >= shiftAt()) flash(c, x, seamY, t - shiftAt(), GRADE[2], 700);
            if (grade == 3 && t >= impact()) flash(c, x, seamY, t - impact(), GRADE[3], 900);
        } else {
            double o = t - openAt();
            float pillar = .55f + .25f * (float) Math.sin(time * 4);
            c.sprite("fx_glow_wide", x, seamY - 80, 700, 700, 0, 1, rgba(color, .45f), true);
            float ph = 760 * ease(clamp01((float) o / .35f));
            c.sprite("fx_ray", x, seamY - ph / 2 + 30, 260, ph, 0, 1, rgba(color, pillar), true);
            c.sprite("fx_burst", x, seamY - 40, 620, 620, (float) (time * 12), 1, rgba(color, .35f), true);
            openChest(c, x, base, scale, 0xFFFFFFFF);
            for (int k = 0; k < 26; k++) {               // burst sparks
                double life = o / .9;
                if (life >= 1) break;
                double ang = hash(k, 21) * Math.PI * 2, sp = 300 + 400 * hash(k, 22);
                float d = (float) (sp * (1 - Math.pow(1 - life, 3)) * .9);
                c.sprite(k % 3 == 0 ? "fx_spark_multi" : "fx_spark4", x + (float) Math.cos(ang) * d, seamY - 60 + (float) Math.sin(ang) * d * .6f,
                        30, 30, (float) (time * 90), 1, rgba(color, (float) (1 - life)), true);
            }
            // artifact rises out of the light
            float rise = ease(clamp01((float) (o - .15) / .7f));
            float iy = lerp(seamY, 330, rise), is = lerp(.3f, 1, rise);
            if (o > .15) {
                c.sprite("fx_glow_wide", x, iy, 380 * is, 380 * is, 0, 1, rgba(color, .75f), true);
                c.sprite("fx_ring_rune", x, iy, 300 * is, 300 * is, (float) (time * 25), 1, rgba(color, .6f), true);
                c.sprite("fx_spark_multi", x + 90 * is, iy - 80 * is, 48, 48, (float) (time * 30), 1, rgba(0xFFFFFFFF, .5f + .5f * (float) Math.sin(time * 6)), true);
                c.icon(icon, x, iy, 190 * is, 0xFFFFFFFF);
            }
            flash(c, x, seamY, o, brighten(color), 1000);
            // result text
            float ta = clamp01((float) (o - .9) / .3f);
            if (ta > 0) {
                float tx = 1260;
                c.text(GRADE_NAME[grade], tx, 300, 64, rgba(GRADE[grade], ta), Face.TITLE, true);
                for (int s = 0; s < rings(); s++) {
                    double st = o - (1.0 + .15 * s);
                    if (st < 0) continue;
                    float pop = 1 + .6f * (1 - clamp01((float) st / .2f));
                    c.sprite("fx_spark4", tx + (s - (rings() - 1) / 2f) * 48, 372, 44 * pop, 44 * pop, 0, 1, rgba(0xFFFFD582, ta), true);
                }
                c.text(name, tx, 432, 30, rgba(0xFFF3E9D3, ta), Face.BOLD, true);
                c.text(isNew ? "새로운 아티팩트 획득" : "이미 가진 아티팩트", tx, 476, 20, rgba(isNew ? 0xFF9FE6B0 : 0xFFB7C6D1, ta), Face.BODY, true);
            }
            if (o > 1.2) c.text("클릭하여 계속", CX, 880, 20, rgba(0xFFD8E2EA, .45f + .35f * (float) Math.sin(time * 4)), Face.BODY, true);
        }
    }

    private void meteor(Canvas c, double t, float x, float y) {
        double m = (t - meteorStart()) / .45;
        if (m < 0 || m > 1.2) return;
        float k = (float) Math.min(1, m * m);
        float sx = 1580, sy = -60;
        float hx = lerp(sx, x, k), hy = lerp(sy, y, k);
        float ang = (float) Math.toDegrees(Math.atan2(y - sy, x - sx));
        float a = m > 1 ? 1 - (float) ((m - 1) / .2) : 1;
        c.sprite("fx_glint", hx - (float) Math.cos(Math.toRadians(ang)) * 160, hy - (float) Math.sin(Math.toRadians(ang)) * 160, 520, 90, ang, 1, rgba(GRADE[3], .9f * a), true);
        c.sprite("fx_glow_core", hx, hy, 150, 150, 0, 1, rgba(0xFFFFF4D0, a), true);
        for (int i = 0; i < 6; i++) {
            float back = 60 + i * 55;
            c.sprite("fx_spark4", hx - (float) Math.cos(Math.toRadians(ang)) * back + (hash(i, 31) - .5f) * 30,
                    hy - (float) Math.sin(Math.toRadians(ang)) * back + (hash(i, 32) - .5f) * 30, 26, 26, 0, 1, rgba(GRADE[3], a * (1 - i / 6f)), true);
        }
    }

    private void smoke(Canvas c, int k, float x, float y, double time, float p, int color) {
        double ang = time * (.6 + .2 * hash(k, 41)) + k * 1.05;
        float r = 150 + 40 * hash(k, 42);
        float rise = (float) ((time * .25 + hash(k, 43)) % 1);
        c.sprite("fx_smoke_" + (k % 4), x + (float) Math.cos(ang) * r, y + (float) Math.sin(ang) * r * .35f - rise * 90, 190, 170,
                (float) (time * 20 * (k % 2 == 0 ? 1 : -1)), 1, rgba(darken(color), .6f * p * (1 - rise * .6f)), false);
    }

    private void ray(Canvas c, float x, float seamY, float angDeg, float w, float len, int argb) {
        double a = Math.toRadians(angDeg);
        c.sprite("fx_ray", x + (float) Math.sin(a) * len / 2, seamY - (float) Math.cos(a) * len / 2, w, len, angDeg, 1, argb, true);
    }

    private void flash(Canvas c, float x, float y, double since, int color, float size) {
        if (since < 0 || since > .45) return;
        float a = 1 - (float) (since / .45);
        c.sprite("fx_glow_core", x, y, size * (.6f + .4f * (1 - a)), size * (.6f + .4f * (1 - a)), 0, 1, rgba(color, a), true);
        c.sprite("fx_ring", x, y, size * 1.2f * (1 - a * .7f), size * 1.2f * (1 - a * .7f), 0, 1, rgba(color, a * .8f), true);
    }

    private void reveal(Canvas c, double t, double time) {
        c.fillScreen(rgba(0xFF02050B, .45f));
        int k = 0;
        for (int i = 0; i < 5; i++) {
            float x = CHEST_X[i];
            boolean mine = i == picked;
            int g = mine ? grade : decoys[k];
            double at = mine ? 0 : .25 + .14 * k;
            if (!mine) k++;
            float on = clamp01((float) ((t - at) / .25)), fade = t > 2.0 ? clamp01((float) (2.6 - t) / .6f) : 1;
            float glow = on * fade * (mine ? .6f : 1);
            platform(c, x, BASE, 250, (float) time + i, GRADE[g], .3f + .5f * glow);
            c.sprite("fx_glow_wide", x, BASE - CLOSED_H / 2, 340, 340, 0, 1, rgba(GRADE[g], .55f * glow), true);
            if (!mine) ray(c, x, BASE - CLOSED_H * SEAM, 0, 70, 220 * glow, rgba(GRADE[g], .5f * glow));
            if (mine) openChest(c, x, BASE, 1, 0xFFFFFFFF); else closedChest(c, x, BASE, 1, 0xFFFFFFFF);
            if (!mine) c.sprite("fx_glint", x, BASE - CLOSED_H * SEAM, 220, 60, 0, 1, rgba(GRADE[g], .8f * glow), true);
            if (mine) c.icon(icon, x, BASE - OPEN_H - 50, 90, 0xFFFFFFFF);
        }
        c.text("남은 유물함의 등급을 잠깐 보여 드립니다", CX, 760, 22, 0xFFD8E2EA, Face.BODY, true);
    }

    private void card(Canvas c, double t, double time) {
        float a = ease(clamp01((float) t / .35f));
        c.fillScreen(rgba(0xFF02050B, .62f * a));
        float y = 360 - 30 * (1 - a), size = 330 * (.85f + .15f * a);
        int color = GRADE[grade];
        c.sprite("fx_glow_wide", CX, y, 620, 620, 0, 1, rgba(color, .35f * a), true);
        c.sprite("fx_ring_rune", CX, y, 520, 520, (float) (time * 10), 1, rgba(color, .3f * a), true);
        c.rect(CX, y, size * .78f, size * .78f, rgba(0xF0101C2E, a));
        c.icon(icon, CX, y, 190 * (.85f + .15f * a), rgba(0xFFFFFFFF, a));
        c.sprite("frame_" + new String[]{"common", "rare", "unique", "legendary"}[grade], CX, y, size, size, 0, 1, rgba(0xFFFFFFFF, a), false);
        c.text(GRADE_NAME[grade], CX, y + size / 2 + 40, 40, rgba(color, a), Face.TITLE, true);
        for (int s = 0; s < rings(); s++)
            c.sprite("fx_spark4", CX + (s - (rings() - 1) / 2f) * 40, y + size / 2 + 86, 36, 36, 0, 1, rgba(0xFFFFD582, a), true);
        c.text(name, CX, y + size / 2 + 128, 28, rgba(0xFFF3E9D3, a), Face.BOLD, true);
        if (isNew) {
            c.sprite("fx_spark_multi", CX + size / 2 - 6, y - size / 2 + 10, 70, 70, (float) (time * 40), 1, rgba(0xFF9FE6B0, a), true);
            c.text("NEW", CX + size / 2 - 6, y - size / 2 + 10, 22, rgba(0xFFEFFFF2, a), Face.BOLD, true);
        }
    }

    // ------------------------------------------------------------------ math

    static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static float clamp01(double v) { return clamp01((float) v); }
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float ease(float t) { float u = 1 - t; return 1 - u * u * u; }
    static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
    static int rgba(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 255) * clamp01(alpha));
        return (a << 24) | (argb & 0xFFFFFF);
    }
    static int darken(int argb) {
        int r = ((argb >> 16) & 255) * 45 / 100, g = ((argb >> 8) & 255) * 45 / 100, b = (argb & 255) * 55 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    static int brighten(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return 0xFF000000 | ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
    }
}
