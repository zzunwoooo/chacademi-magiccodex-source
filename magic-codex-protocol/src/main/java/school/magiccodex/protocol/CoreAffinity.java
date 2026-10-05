package school.magiccodex.protocol;

/** Relative stat budget: one primary share and two half shares. Legacy cores use power. */
public final class CoreAffinity {
    private CoreAffinity() {}
    public static int valid(int value) { return value >= 0 && value < 3 ? value : 0; }
    public static String label(int stat, int primary) {
        return new String[]{"마력", "마나", "마법 가속"}[stat] + (stat == valid(primary) ? " 중심" : " 보조");
    }
    public static double multiplier(int stat, int primary) {
        double originalShare = stat == 0 ? 1 : .5;
        return (stat == valid(primary) ? 1 : .5) / originalShare;
    }
    public static int changed(int previous, int choice) {
        if (choice < 0 || choice > 1) throw new IllegalArgumentException();
        return (valid(previous) + 1 + choice) % 3;
    }
}
