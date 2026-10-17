package school.magiccodex.client.kui;

/** Colours and names shared by the 큐디치 views (sampled from the approved reference images). */
public final class Theme {
    private Theme() {}
    public static final int GOLD = 0xFFF2D28A, GOLD_DEEP = 0xFFE3BC6A, WHITE = 0xFFF3EEE3, CREAM = 0xFFE6DCC6, MUTED = 0xFFA9B4C2,
            BLUE = 0xFF79B6FF, RED = 0xFFFF7A72, TEAL = 0xFF79E8D6, GREEN = 0xFF7EE08D, DARK_TEXT = 0xFF2A1C08, LOSS = 0xFFFF8A80;
    /** Houses in Bridge order: 아르케온 · 루미나 · 베스티아즈 · 노크세르. */
    public static final String[] HOUSE_NAMES = {"아르케온", "루미나", "베스티아즈", "노크세르"};
    public static final String[] HOUSE_PARTS = {"houses/star", "houses/moon", "houses/leaf", "houses/sun"};
    public static final int[] HOUSE_COLORS = {0xFFFF6A5C, 0xFF7FB8FF, 0xFF79DB72, 0xFFFFD45A};
    public static final String[] TIERS = {"bronze", "silver", "gold", "platinum", "diamond", "master", "challenger_1", "challenger_2", "challenger_3"};
    public static final String[] TIER_NAMES = {"브론즈", "실버", "골드", "플래티넘", "다이아몬드", "마스터", "챌린저 1", "챌린저 2", "챌린저 3"};
    /** House crest whose drawn shield is about artHeight tall, centred on (cx, cy); the PNGs carry ~20% transparent padding. */
    public static void crest(Pen p, int house, float cx, float cy, float artHeight) {
        if (house < 0 || house > 3) return;
        float size = artHeight * 192f / 152f;
        p.image(HOUSE_PARTS[house], cx - size / 2, cy - size / 2, size, size);
    }
    public static String tier(int t) { return "tiers/" + TIERS[Math.clamp(t, 0, 8)]; }
    public static String time(int ms) { int s = Math.max(0, ms) / 1000; return s / 60 + ":" + String.format("%02d", s % 60); }
    public static String time2(int ms) { int s = Math.max(0, ms) / 1000; return String.format("%02d:%02d", s / 60, s % 60); }
    public static String number(long v) { return String.format("%,d", v); }
}
