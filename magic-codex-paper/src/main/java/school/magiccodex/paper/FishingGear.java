package school.magiccodex.paper;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import school.magiccodex.protocol.FishingProtocol;
import school.magiccodex.protocol.FishingProtocol.Piece;

/**
 * Fishing gear lives in item PDC (owner 2026-10-20: "내구도 같은건 아이템 nbt에 넣으면 되지").
 *
 * Every gear item: fishing_kind (0 rod, 1 float, 2 line, 3 bait), fishing_id, fishing_dur (rods/floats/lines).
 * The rod additionally carries what is fitted to it: fishing_float/_dur, fishing_line/_dur, fishing_bait/_count.
 * A vanilla fishing rod without any of this is the default set (fishing-gear.yml default: true), which never wears out.
 * Lore is rebuilt from the PDC whenever something changes, so the numbers on the item are always the real ones.
 */
final class FishingGear {
    static final int[] GRADE_RGB = {0xDCE3EA, 0x71E5F2, 0xD2A8FF, 0xFFD582};
    private final NamespacedKey kind, id, dur, fFloat, fFloatDur, fLine, fLineDur, fBait, fBaitCount, fish, size, tankId, caught, itemToken;
    private FishingConfig config;

    /** One fitted piece: gear + durability (-1 = endless) or bait count (-1 = endless default). fallback = not a real item. */
    record Part(FishingConfig.Gear gear, int value, boolean fallback) {}
    /** What the held rod carries. */
    record Loadout(Part rod, Part flt, Part line, Part bait) {
        Part part(int k) { return switch (k) { case 0 -> rod; case 1 -> flt; case 2 -> line; default -> bait; }; }
    }

    FishingGear(Plugin plugin, FishingConfig config) {
        this.config = config;
        kind = new NamespacedKey(plugin, "fishing_kind"); id = new NamespacedKey(plugin, "fishing_id"); dur = new NamespacedKey(plugin, "fishing_dur");
        fFloat = new NamespacedKey(plugin, "fishing_float"); fFloatDur = new NamespacedKey(plugin, "fishing_float_dur");
        fLine = new NamespacedKey(plugin, "fishing_line"); fLineDur = new NamespacedKey(plugin, "fishing_line_dur");
        fBait = new NamespacedKey(plugin, "fishing_bait"); fBaitCount = new NamespacedKey(plugin, "fishing_bait_count");
        fish = new NamespacedKey(plugin, "fishing_fish"); size = new NamespacedKey(plugin, "fishing_size");
        tankId = new NamespacedKey(plugin, "fishing_tank_id"); caught = new NamespacedKey(plugin, "fishing_caught"); itemToken = new NamespacedKey(plugin, "fishing_item_token");
    }
    void config(FishingConfig c) { config = c; }

    // ------------------------------------------------------------------ reading

    static boolean isRod(ItemStack item) { return item != null && item.getType() == Material.FISHING_ROD; }

    /** Gear kind of an item (0..3), -1 = not fishing gear. A plain fishing rod counts as a rod. */
    int kindOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;
        var meta = item.getItemMeta();
        Integer k = meta == null ? null : meta.getPersistentDataContainer().get(kind, PersistentDataType.INTEGER);
        if (k != null && k >= 0 && k <= 3) return k;
        return isRod(item) ? FishingProtocol.ROD : -1;
    }
    /** The gear definition of an item (a plain rod = the default rod), or null. */
    FishingConfig.Gear gearOf(ItemStack item) {
        int k = kindOf(item);
        if (k < 0) return null;
        String i = item.getItemMeta().getPersistentDataContainer().get(id, PersistentDataType.STRING);
        if (i == null) return k == FishingProtocol.ROD ? config.fallback[0] : null;
        return config.gear(k, i);
    }
    boolean plainRod(ItemStack item) {
        return isRod(item) && (item.getItemMeta() == null || !item.getItemMeta().getPersistentDataContainer().has(id, PersistentDataType.STRING));
    }
    /** Durability stored on a gear item (-1 = none/endless). */
    int durabilityOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return -1;
        Integer d = item.getItemMeta().getPersistentDataContainer().get(dur, PersistentDataType.INTEGER);
        return d == null ? -1 : d;
    }

    Loadout read(ItemStack rod) {
        var fb = config.fallback;
        if (!isRod(rod)) return new Loadout(new Part(fb[0], -1, true), new Part(fb[1], -1, true), new Part(fb[2], -1, true), new Part(fb[3], -1, true));
        var pdc = rod.getItemMeta().getPersistentDataContainer();
        Part r;
        var rg = gearOf(rod);
        if (plainRod(rod) || rg == null) r = new Part(fb[0], -1, true);
        else r = new Part(rg, Math.max(0, intOr(pdc, dur, rg.durability())), false);
        return new Loadout(r, part(pdc, 1, fFloat, fFloatDur), part(pdc, 2, fLine, fLineDur), part(pdc, 3, fBait, fBaitCount));
    }
    private Part part(PersistentDataContainer pdc, int k, NamespacedKey idKey, NamespacedKey valueKey) {
        String i = pdc.get(idKey, PersistentDataType.STRING);
        var g = config.gear(k, i);
        if (g == null) return new Part(config.fallback[k], -1, true);
        int v = intOr(pdc, valueKey, k == 3 ? 0 : g.durability());
        if (k == 3 && v <= 0) return new Part(config.fallback[k], -1, true);
        return new Part(g, Math.max(0, v), false);
    }
    private static int intOr(PersistentDataContainer pdc, NamespacedKey key, int otherwise) {
        Integer v = pdc.get(key, PersistentDataType.INTEGER); return v == null ? otherwise : v;
    }

    // ------------------------------------------------------------------ writing

    /** Writes the loadout into the rod (a plain rod becomes the default rod with full durability) and rebuilds its lore. */
    void write(ItemStack rod, Loadout l) {
        var meta = rod.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(kind, PersistentDataType.INTEGER, 0);
        pdc.set(id, PersistentDataType.STRING, l.rod().gear().id());
        pdc.set(dur, PersistentDataType.INTEGER, l.rod().fallback() ? l.rod().gear().durability() : l.rod().value());
        put(pdc, fFloat, fFloatDur, l.flt()); put(pdc, fLine, fLineDur, l.line()); put(pdc, fBait, fBaitCount, l.bait());
        rod.setItemMeta(meta);
        decorate(rod);
    }
    private static void put(PersistentDataContainer pdc, NamespacedKey idKey, NamespacedKey valueKey, Part p) {
        if (p.fallback()) { pdc.remove(idKey); pdc.remove(valueKey); return; }
        pdc.set(idKey, PersistentDataType.STRING, p.gear().id()); pdc.set(valueKey, PersistentDataType.INTEGER, p.value());
    }

    /** A new gear item. durability -1 = full. Baits stack (amount), floats/lines/rods are single. */
    ItemStack create(FishingConfig.Gear g, int durability, int amount) {
        Material base = switch (g.kind()) { case 0 -> Material.FISHING_ROD; case 3 -> Material.SLIME_BALL; default -> Material.PAPER; };
        var item = new ItemStack(base, g.kind() == 3 ? Math.clamp(amount, 1, 64) : 1);
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(kind, PersistentDataType.INTEGER, g.kind());
        pdc.set(id, PersistentDataType.STRING, g.id());
        if (g.kind() != 3) pdc.set(dur, PersistentDataType.INTEGER, durability < 0 ? g.durability() : durability);
        item.setItemMeta(meta);
        decorate(item);
        return item;
    }

    /** Name, lore, model, unbreakable rod. Safe to call again any time. */
    void decorate(ItemStack item) {
        var g = gearOf(item);
        if (g == null) return;
        boolean rod = item.getType() == Material.FISHING_ROD;
        var meta = item.getItemMeta();
        meta.displayName(plain(g.name(), GRADE_RGB[g.grade()]));
        var lore = new ArrayList<Component>();
        lore.add(plain(FishingProtocol.GRADE_LABELS[g.grade()] + " " + FishingProtocol.KIND_LABELS[g.kind()], GRADE_RGB[g.grade()]));
        lore.add(plain(stats(g), 0xAFC2D6));
        if (g.kind() != 3) {
            int d = durabilityOf(item);
            if (d < 0) d = g.durability();
            lore.add(plain(d <= 0 ? "고장 (수리 필요)" : "내구도 " + d + " / " + g.durability(), d <= 0 ? 0xFF9E9E : 0xAFC2D6));
        }
        if (!g.specialText().isBlank()) lore.add(plain(g.specialText(), 0xF2D9A0));
        if (!g.description().isBlank()) lore.add(plain(g.description(), 0x8D9AAD));
        if (rod) {
            var l = read(item);
            for (int k = 1; k <= 3; k++) {
                var p = l.part(k);
                String v = p.fallback() ? "기본 " + p.gear().name() : p.gear().name() + (k == 3 ? " ×" + p.value() : " (" + p.value() + "/" + p.gear().durability() + ")");
                lore.add(plain(FishingProtocol.KIND_LABELS[k] + ": " + v, 0x9AA8BB));
            }
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        } else if (g.kind() != 3) {
            meta.setMaxStackSize(1);
        }
        meta.lore(lore);
        if (config.gearModels) meta.setItemModel(new NamespacedKey("magiccodex", "fishing/" + FishingProtocol.KIND_FOLDERS[g.kind()] + "/" + g.id()));
        item.setItemMeta(meta);
    }

    static String stats(FishingConfig.Gear g) {
        return switch (g.kind()) {
            case 0 -> "감는 힘 " + num(g.a()) + " · 챔질 " + num(g.b()) + "초";
            case 1 -> "가라앉는 속도 " + num(g.a()) + "m/초 · 감도 " + num(g.b()) + "초";
            case 2 -> "최대 길이 " + num(g.a()) + "m · 최대 장력 " + num(g.b());
            default -> "희귀 보정 +" + num(g.a()) + "% · 입질 대기 -" + num(g.b()) + "%";
        };
    }
    static String num(float v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10f); }

    static Component plain(String text, int rgb) {
        return Component.text(text).color(TextColor.color(rgb)).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------ fish items

    /** A fresh fish item (admin give): it gets its own tank id now, so copies of it can never enter a tank twice. */
    ItemStack fish(FishingConfig.Fish f, float cm) { return fish(f, cm, UUID.randomUUID().toString(), System.currentTimeMillis()); }
    /**
     * A fish item for one tank fish. It keeps the tank row id and the caught date (owner 2026-10-20: 개체 정보), so
     * putting it back restores the same fish, and the id (the table's primary key) refuses a duplicated item.
     */
    ItemStack fish(FishingConfig.Fish f, float cm, String tank, long caughtAt) { return fish(f,cm,tank,caughtAt,""); }
    ItemStack fish(FishingConfig.Fish f, float cm, String tank, long caughtAt, String token) {
        Material base = Material.matchMaterial(f.item());
        if (base == null || !base.isItem()) base = Material.COD;
        var item = new ItemStack(base);
        var meta = item.getItemMeta();
        meta.displayName(plain(f.name(), GRADE_RGB[f.grade()]));
        meta.lore(List.of(plain(FishingProtocol.GRADE_LABELS[f.grade()] + " 물고기", GRADE_RGB[f.grade()]),
                plain("크기 " + String.format(Locale.ROOT, "%.1f", cm) + "cm", 0xAFC2D6),
                plain("잡은 날짜 " + new java.text.SimpleDateFormat("yyyy.MM.dd").format(new Date(caughtAt)), 0xAFC2D6),
                plain(f.description(), 0x8D9AAD),
                plain("통발 수조에 다시 넣을 수 있어요 (판매소에서는 팔 수 없어요)", 0x7E8BA0)));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(fish, PersistentDataType.STRING, f.id());
        pdc.set(size, PersistentDataType.FLOAT, cm);
        pdc.set(tankId, PersistentDataType.STRING, tank);
        pdc.set(caught, PersistentDataType.LONG, caughtAt);
        if(!token.isEmpty())pdc.set(itemToken,PersistentDataType.STRING,token);
        if (config.fishModels) meta.setItemModel(new NamespacedKey("magiccodex", "fishing/fish/" + f.id()));
        item.setItemMeta(meta);
        return item;
    }
    /** The fish id of a caught fish item, or null (for shops / the housing aquarium later). */
    String fishOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(fish, PersistentDataType.STRING);
    }
    /** Fish item data: species, size, tank id ("" for old items), caught date (0 = unknown). Null when not a fish item. */
    record HeldFish(String fish, float size, String tankId, long caughtAt, String token) {}
    HeldFish heldFish(ItemStack item) {
        if (item == null || item.getAmount() < 1 || item.getItemMeta() == null) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String f = pdc.get(fish, PersistentDataType.STRING);
        if (f == null) return null;
        Float cm = pdc.get(size, PersistentDataType.FLOAT);
        String id = pdc.get(tankId, PersistentDataType.STRING);
        Long at = pdc.get(caught, PersistentDataType.LONG);
        if(cm==null || !Float.isFinite(cm) || cm<=0)return null;
        return new HeldFish(f, cm, id == null ? "" : id, at == null ? 0 : at, pdc.getOrDefault(itemToken,PersistentDataType.STRING,""));
    }

    // ------------------------------------------------------------------ protocol

    Piece piece(int k, Part p) {
        var g = p.gear();
        int max = k == 3 || p.fallback() ? -1 : g.durability();
        int d = k == 3 || p.fallback() ? -1 : p.value();
        int count = k == 3 ? (p.fallback() ? -1 : p.value()) : 0;
        String special = g.special().length() > FishingProtocol.MAX_SPECIAL ? g.special().substring(0, FishingProtocol.MAX_SPECIAL) : g.special();
        return new Piece(k, g.id(), cut(g.name()), g.grade(), d, max, count, p.fallback(), special);
    }
    FishingProtocol.Gear protocol(Loadout l) {
        var pieces = new ArrayList<Piece>();
        for (int k = 0; k < 4; k++) pieces.add(piece(k, l.part(k)));
        return new FishingProtocol.Gear(pieces, l.rod().gear().a(), l.rod().gear().b(), l.flt().gear().a(), l.flt().gear().b(),
                l.line().gear().a(), l.line().gear().b());
    }
    static String cut(String s) { return s.length() > FishingProtocol.MAX_NAME ? s.substring(0, FishingProtocol.MAX_NAME) : s; }
}
