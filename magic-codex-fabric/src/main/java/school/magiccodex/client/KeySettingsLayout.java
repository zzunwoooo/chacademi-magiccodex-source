package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import school.magiccodex.client.CodexHitboxes.Rect;

/** Measurements on key_settings_base.png, shared by drawing and hit testing. */
public final class KeySettingsLayout {
    private KeySettingsLayout() {}
    public record Key(int code, String label, Rect box) {}
    public static final Rect ENTRY = new Rect(865, 74, 200, 48);
    public static final Rect CLOSE = new Rect(1478, 65, 54, 50);
    public static final Rect SAVE = new Rect(1206, 831, 150, 52);
    public static final Rect RESET = new Rect(1370, 831, 143, 52);
    public static final Rect PREVIOUS = new Rect(388, 742, 95, 40);
    public static final Rect NEXT = new Rect(1190, 742, 95, 40);
    public static final Rect PICKER_CLOSE = new Rect(1248, 170, 48, 48);
    public static final Rect DISCARD = new Rect(845, 510, 220, 50);
    public static final Rect CONTINUE = new Rect(605, 510, 220, 50);
    public static final List<Key> KEYS = keys();
    private static final Set<Integer> RESERVED = Set.of(87, 65, 83, 68, 73, 90, 80, 79,
            340, 344, 341, 345, 342, 346, 343, 347, 348, 280);
    private static java.util.function.IntPredicate equipmentKey=code->false;
    static void equipmentKey(java.util.function.IntPredicate predicate){equipmentKey=predicate;}
    static boolean equipmentReserved(int code){return code==79||equipmentKey.test(code);}
    public static boolean reservedShortcut(int code){return code==87||code==65||code==83||code==68||code==73||code==90||code==80||code==79||equipmentKey.test(code);}
    public static boolean allowed(int code) {
        return !RESERVED.contains(code) && !equipmentKey.test(code) && KEYS.stream().anyMatch(k -> k.code() == code);
    }
    public static String name(int code) {
        return KEYS.stream().filter(k -> k.code() == code).map(Key::label).findFirst().orElse("미지정");
    }
    public static Rect slot(int index) { return new Rect(375 + index * 166,622,92,88); }
    public static Rect slotKey(int index) { return new Rect(376 + index * 166,729,90,30); }
    public static Rect choice(int index) { return new Rect(386 + index % 3 * 303, 257 + index / 3 * 148, 286,132); }
    private static List<Key> keys() {
        var keys = new ArrayList<Key>();
        row(keys,179,69,new int[]{251,351,437,522,608,692,778,862,948,1033,1118,1204,1290},
                new int[]{89,75,75,73,73,73,72,74,74,74,76,75,151},
                new String[]{"1","2","3","4","5","6","7","8","9","0","-","=","Backspace"},
                new int[]{49,50,51,52,53,54,55,56,57,48,45,61,259});
        row(keys,260,66,new int[]{232,332,410,492,574,656,740,824,907,991,1074,1158,1242,1326},
                new int[]{90,67,72,72,72,73,73,73,73,73,73,72,72,115},
                new String[]{"Tab","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"},
                new int[]{258,81,87,69,82,84,89,85,73,79,80,91,93,92});
        row(keys,336,66,new int[]{232,361,444,527,610,694,778,862,947,1031,1114,1199,1286},
                new int[]{118,73,73,72,73,72,73,73,72,71,74,73,155},
                new String[]{"Caps Lock","A","S","D","F","G","H","J","K","L",";","'","Enter"},
                new int[]{280,65,83,68,70,71,72,74,75,76,59,39,257});
        row(keys,413,65,new int[]{232,406,490,574,658,742,826,910,994,1078,1162,1248},
                new int[]{162,73,73,73,73,73,73,73,73,74,73,193},
                new String[]{"Shift","Z","X","C","V","B","N","M",",",".","/","Shift"},
                new int[]{340,90,88,67,86,66,78,77,44,46,47,344});
        row(keys,488,65,new int[]{232,344,439,538,1041,1145,1241,1344},
                new int[]{100,83,88,493,93,84,91,97},
                new String[]{"Ctrl","Win","Alt","Space","Alt","Win","Menu","Ctrl"},
                new int[]{341,343,342,32,346,347,348,345});
        return List.copyOf(keys);
    }
    private static void row(List<Key> keys,int y,int h,int[] xs,int[] ws,String[] labels,int[] codes) {
        for(int i=0;i<xs.length;i++) keys.add(new Key(codes[i],labels[i],new Rect(xs[i],y,ws[i],h)));
    }
}
