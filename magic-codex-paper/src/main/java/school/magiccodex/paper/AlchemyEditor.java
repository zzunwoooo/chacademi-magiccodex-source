package school.magiccodex.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import school.magiccodex.protocol.AlchemyProtocol;

/**
 * In-game alchemy recipe editor for operators (/연금술관리): plain chest windows, no client mod needed.
 *
 * List window: every recipe in alchemy.yml (including switched-off ones) shown as its result item; click to edit,
 * the book makes a new one. Edit window: put real items on the ingredient / result slots (hold an item and click a
 * slot, or shift-click it in your own inventory), empty-hand left/right click changes the amount, the name tag asks for
 * the name in chat, the bottles change the experience cost, the emerald block saves. Items are only copied, never taken.
 * Saving writes alchemy.yml (comments kept) and reloads it at once; if the result does not load, the old file is put
 * back. Ingredients that the slot picker would not show (first lore line is not 재료) are added to
 * settings.material-items automatically so players can pick them.
 */
final class AlchemyEditor implements Listener {
    private static final int SIZE = 54, PAGE = 45;
    private static final int[] INGREDIENT = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int HELP = 4, LABEL_INGREDIENT = 9, LABEL_RESULT = 27, RESULT = 28, NAME = 30, ENABLED = 32, BACK = 45, DELETE = 48, SAVE = 50;
    private static final int[] EXP_SLOTS = {38, 39, 40, 42, 43, 44};
    private static final int[] EXP_STEPS = {-100, -10, -1, 1, 10, 100};
    private static final int EXP_SHOW = 41;
    private static final int LIST_PREV = 45, LIST_HELP = 47, LIST_NEW = 49, LIST_RELOAD = 51, LIST_NEXT = 53;

    private enum Kind { LIST, EDIT }
    private static final class Screen implements InventoryHolder {
        final Kind kind; final int page; Inventory inventory;
        Screen(Kind kind, int page) { this.kind = kind; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    /** A recipe as written in alchemy.yml (switched-off ones too). */
    private record Row(String id, String name, String result, int amount, int exp, boolean enabled, int order, Map<String, Integer> ingredients) {}
    /** One slot of the draft: the item key plus a display copy. */
    private record Part(String key, ItemStack item, int amount) {}
    private static final class Draft {
        String id; String name = ""; Part result; int exp; boolean enabled = true; int order;
        final Part[] ingredients = new Part[INGREDIENT.length];
        boolean deleteArmed; int page;
    }

    private final MagicCodexBridge plugin;
    private final AlchemyBridge bridge;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Set<UUID> naming = new HashSet<>();

    AlchemyEditor(MagicCodexBridge plugin, AlchemyBridge bridge) {
        this.plugin = plugin; this.bridge = bridge;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static boolean admin(Player p) { return p.hasPermission("magiccodex.alchemy.admin"); }
    private static boolean empty(ItemStack i) { return i == null || i.getType().isAir() || i.getAmount() <= 0; }

    // ------------------------------------------------------------------ alchemy.yml

    private YamlConfiguration load() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(bridge.file());
        return yaml;
    }

    private List<Row> rows() {
        var list = new ArrayList<Row>();
        try {
            ConfigurationSection root = load().getConfigurationSection("recipes");
            if (root != null) for (String id : root.getKeys(false)) {
                var s = root.getConfigurationSection(id);
                if (s == null) continue;
                var ingredients = new LinkedHashMap<String, Integer>();
                var is = s.getConfigurationSection("ingredients");
                if (is != null) for (String k : is.getKeys(false)) ingredients.merge(normalizeKey(k), is.getInt(k, 0), Integer::sum);
                list.add(new Row(id, s.getString("name", id), normalizeKey(s.getString("result", "")), s.getInt("amount", 1), s.getInt("exp", 0),
                        s.getBoolean("enabled", true), s.getInt("order", 0), ingredients));
            }
        } catch (Exception e) { plugin.getLogger().warning("연금술 편집: alchemy.yml 읽기 실패: " + e.getMessage()); }
        list.sort(Comparator.comparingInt(Row::order).thenComparing(Row::id));
        return list;
    }

    private static String normalizeKey(String raw) {
        String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return v.isEmpty() || v.contains(":") ? v : "minecraft:" + v;
    }

    // ------------------------------------------------------------------ items

    private static Component plain(String text, TextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }
    private static ItemStack button(Material m, String name, NamedTextColor color, String... lore) {
        var item = new ItemStack(m);
        var meta = item.getItemMeta();
        meta.displayName(plain(name, color));
        var lines = new ArrayList<Component>();
        for (String l : lore) lines.add(plain(l, NamedTextColor.GRAY));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
    private static ItemStack pane(Material m) { return button(m, " ", NamedTextColor.GRAY); }
    /** Display copy of a part with an explanatory lore (the real item stays as configured: only its key is saved). */
    private ItemStack show(Part part, String role, boolean result) {
        ItemStack item = part.item().clone();
        item.setAmount(Math.max(1, Math.min(part.amount(), item.getMaxStackSize())));
        var meta = item.getItemMeta();
        var lore = new ArrayList<Component>();
        lore.add(plain(role + " · " + part.amount() + "개", NamedTextColor.GOLD));
        lore.add(plain("아이템: " + part.key(), NamedTextColor.DARK_GRAY));
        if (!result && !bridge.material(part.item(), part.key()))
            lore.add(plain("※ 재료 카테고리가 아님 → 저장하면 재료로 자동 등록", NamedTextColor.YELLOW));
        lore.add(plain("빈손 좌클릭 +1 · 우클릭 -1 · 쉬프트+클릭 비우기", NamedTextColor.GRAY));
        lore.add(plain("다른 아이템을 들고 클릭하면 바꾸기", NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private Part part(String key, int amount) {
        ItemStack item = bridge.create(key, 1);
        if (item == null) item = button(Material.BARRIER, "찾을 수 없는 아이템", NamedTextColor.RED, key);
        return new Part(key, item, amount);
    }

    // ------------------------------------------------------------------ list window

    void openList(Player p, int page) {
        if (!admin(p)) { p.sendMessage("§c관리자만 사용할 수 있습니다."); return; }
        var rows = rows();
        int pages = Math.max(1, (rows.size() + PAGE - 1) / PAGE);
        page = Math.clamp(page, 0, pages - 1);
        var screen = new Screen(Kind.LIST, page);
        var inv = Bukkit.createInventory(screen, SIZE, Component.text("연금술 레시피 (" + rows.size() + "개) " + (page + 1) + "/" + pages + "쪽"));
        screen.inventory = inv;
        for (int i = 0; i < PAGE && page * PAGE + i < rows.size(); i++) inv.setItem(i, rowItem(rows.get(page * PAGE + i)));
        for (int i = PAGE; i < SIZE; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        if (page > 0) inv.setItem(LIST_PREV, button(Material.ARROW, "이전 쪽", NamedTextColor.WHITE));
        if (page + 1 < pages) inv.setItem(LIST_NEXT, button(Material.ARROW, "다음 쪽", NamedTextColor.WHITE));
        inv.setItem(LIST_NEW, button(Material.WRITABLE_BOOK, "＋ 새 레시피 만들기", NamedTextColor.GREEN, "클릭하면 빈 레시피 편집 창이 열립니다."));
        inv.setItem(LIST_HELP, button(Material.BOOK, "사용법", NamedTextColor.AQUA,
                "레시피를 클릭하면 편집합니다.", "＋ 새 레시피 만들기로 새로 만듭니다.",
                "저장하면 바로 게임에 적용됩니다.", "※ 스쿨·야생은 파일이 따로라 서버마다 따로 만들어야 합니다."));
        inv.setItem(LIST_RELOAD, button(Material.CLOCK, "파일 다시 읽기", NamedTextColor.WHITE, "alchemy.yml 을 직접 고쳤을 때 누르세요."));
        drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft()).page = page;
        p.openInventory(inv);
    }

    private ItemStack rowItem(Row r) {
        ItemStack item = bridge.create(r.result(), 1);
        if (item == null) item = new ItemStack(Material.BARRIER);
        var meta = item.getItemMeta();
        meta.displayName(plain(r.name() + (r.enabled() ? "" : " (꺼짐)"), r.enabled() ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        var lore = new ArrayList<Component>();
        lore.add(plain("결과: " + displayKey(r.result()) + " ×" + r.amount(), NamedTextColor.WHITE));
        lore.add(plain("소모 경험치: " + r.exp(), NamedTextColor.GREEN));
        lore.add(plain("재료:", NamedTextColor.WHITE));
        r.ingredients().forEach((k, n) -> lore.add(plain("  · " + displayKey(k) + " ×" + n, NamedTextColor.GRAY)));
        lore.add(plain("아이디: " + r.id(), NamedTextColor.DARK_GRAY));
        lore.add(plain("클릭: 편집", NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private String displayKey(String key) {
        ItemStack item = bridge.create(key, 1);
        return item == null ? key + " (없음)" : AlchemyBridge.displayName(item);
    }

    // ------------------------------------------------------------------ edit window

    private void edit(Player p, Row r) {
        var d = new Draft();
        d.page = drafts.getOrDefault(p.getUniqueId(), new Draft()).page;
        if (r != null) {
            d.id = r.id(); d.name = r.name(); d.exp = r.exp(); d.enabled = r.enabled(); d.order = r.order();
            d.result = r.result().isEmpty() ? null : part(r.result(), r.amount());
            int i = 0;
            for (var e : r.ingredients().entrySet()) if (i < INGREDIENT.length) d.ingredients[i++] = part(e.getKey(), e.getValue());
        } else {
            d.order = rows().stream().mapToInt(Row::order).max().orElse(0) + 1;
        }
        drafts.put(p.getUniqueId(), d);
        openEdit(p);
    }

    private void openEdit(Player p) {
        var d = drafts.get(p.getUniqueId());
        if (d == null) { openList(p, 0); return; }
        var screen = new Screen(Kind.EDIT, 0);
        var inv = Bukkit.createInventory(screen, SIZE, Component.text(d.id == null ? "새 레시피 만들기" : "레시피 편집: " + (d.name.isBlank() ? d.id : d.name)));
        screen.inventory = inv;
        draw(inv, d);
        p.openInventory(inv);
    }

    private void draw(Inventory inv, Draft d) {
        for (int i = 0; i < SIZE; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(HELP, button(Material.BOOK, "사용법", NamedTextColor.AQUA,
                "① 재료: 아이템을 손(커서)에 들고 위쪽 빈칸을 클릭",
                "   또는 내 인벤토리에서 아이템을 쉬프트+클릭",
                "   빈손 좌클릭 +1개 · 우클릭 -1개 · 쉬프트+클릭 비우기",
                "② 결과: 만들어질 아이템을 같은 방법으로 넣기",
                "③ 이름표: 클릭 후 채팅에 레시피 이름 입력",
                "④ 경험치 병으로 소모 경험치 조절 (레벨 아님)",
                "⑤ 에메랄드 블록: 저장하면 바로 적용",
                "아이템은 복사만 되고 인벤토리에서 빠지지 않습니다.",
                "재료 넣는 순서는 상관없습니다."));
        inv.setItem(LABEL_INGREDIENT, button(Material.PAPER, "재료 →", NamedTextColor.WHITE, "오른쪽 칸에 재료를 넣으세요.", "종류는 최대 " + bridge.slots() + "개"));
        for (int i = 0; i < INGREDIENT.length; i++) {
            if (i >= bridge.slots()) { inv.setItem(INGREDIENT[i], button(Material.BLACK_STAINED_GLASS_PANE, "사용 안 하는 칸", NamedTextColor.DARK_GRAY, "settings.slots 개수만큼만 씁니다.")); continue; }
            var part = d.ingredients[i];
            inv.setItem(INGREDIENT[i], part == null ? button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "빈 재료 칸", NamedTextColor.WHITE, "재료 아이템을 들고 클릭하세요.") : show(part, "재료", false));
        }
        inv.setItem(LABEL_RESULT, button(Material.PAPER, "결과 →", NamedTextColor.WHITE, "오른쪽 칸에 만들어질 아이템을 넣으세요."));
        inv.setItem(RESULT, d.result == null ? button(Material.LIME_STAINED_GLASS_PANE, "빈 결과 칸", NamedTextColor.GREEN, "결과 아이템을 들고 클릭하세요.") : show(d.result, "결과", true));
        inv.setItem(NAME, button(Material.NAME_TAG, "이름: " + (d.name.isBlank() ? "(비어 있음 · 결과 아이템 이름 사용)" : d.name), NamedTextColor.GOLD, "클릭 후 채팅에 이름을 입력하세요."));
        inv.setItem(ENABLED, d.enabled
                ? button(Material.LIME_DYE, "켜짐", NamedTextColor.GREEN, "플레이어가 발견·제작할 수 있습니다.", "클릭하면 끕니다.")
                : button(Material.GRAY_DYE, "꺼짐", NamedTextColor.DARK_GRAY, "게임에서 사라집니다 (지우지는 않음).", "클릭하면 켭니다."));
        for (int i = 0; i < EXP_SLOTS.length; i++) {
            int step = EXP_STEPS[i];
            inv.setItem(EXP_SLOTS[i], button(step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    (step > 0 ? "+" : "") + step, step < 0 ? NamedTextColor.RED : NamedTextColor.GREEN, "소모 경험치 " + (step > 0 ? "늘리기" : "줄이기")));
        }
        inv.setItem(EXP_SHOW, button(Material.EXPERIENCE_BOTTLE, "소모 경험치: " + d.exp, NamedTextColor.GREEN,
                "바닐라 경험치 포인트입니다 (레벨 아님).", "참고: 레벨 0→10 = 160, 0→30 = 1395"));
        inv.setItem(BACK, button(Material.ARROW, "목록으로", NamedTextColor.WHITE, "저장하지 않은 변경은 사라집니다."));
        if (d.id != null) inv.setItem(DELETE, d.deleteArmed
                ? button(Material.TNT, "정말 삭제할까요? 한 번 더 클릭", NamedTextColor.RED, "레시피를 파일에서 지웁니다.")
                : button(Material.BARRIER, "레시피 삭제", NamedTextColor.RED, "두 번 클릭하면 삭제됩니다.", "잠깐 숨기려면 '켜짐'을 끄세요."));
        inv.setItem(SAVE, button(Material.EMERALD_BLOCK, "저장", NamedTextColor.GREEN, "alchemy.yml 에 저장하고 바로 적용합니다."));
    }

    private void redraw(Player p) {
        var top = p.getOpenInventory().getTopInventory();
        var d = drafts.get(p.getUniqueId());
        if (top.getHolder() instanceof Screen s && s.kind == Kind.EDIT && d != null) draw(top, d);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Screen screen) || !(e.getWhoClicked() instanceof Player p)) return;
        boolean top = e.getRawSlot() >= 0 && e.getRawSlot() < SIZE;
        if (!top) {
            // own inventory: normal clicks allowed (to pick an item up); shift-click = copy into the editor
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
            if (e.isShiftClick()) { e.setCancelled(true); if (screen.kind == Kind.EDIT && admin(p)) shiftAdd(p, e.getCurrentItem()); }
            return;
        }
        e.setCancelled(true);
        if (!admin(p)) { p.closeInventory(); return; }
        if (screen.kind == Kind.LIST) listClick(p, screen, e.getRawSlot());
        else editClick(p, e.getRawSlot(), e.getCursor(), e.isLeftClick(), e.isRightClick(), e.isShiftClick());
    }

    @EventHandler public void drag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Screen)) return;
        for (int slot : e.getRawSlots()) if (slot < SIZE) { e.setCancelled(true); return; }
    }

    private void listClick(Player p, Screen screen, int slot) {
        switch (slot) {
            case LIST_PREV -> { if (screen.page > 0) openList(p, screen.page - 1); }
            case LIST_NEXT -> openList(p, screen.page + 1);
            case LIST_NEW -> { sound(p, Sound.ITEM_BOOK_PAGE_TURN); edit(p, null); }
            case LIST_RELOAD -> {
                String error = bridge.reloadAndPush();
                p.sendMessage(error == null ? "§a[연금술] alchemy.yml 을 다시 불러왔습니다." : "§c[연금술] alchemy.yml 오류: " + error);
                openList(p, screen.page);
            }
            default -> {
                if (slot >= PAGE) return;
                var rows = rows(); int index = screen.page * PAGE + slot;
                if (index < rows.size()) { sound(p, Sound.ITEM_BOOK_PAGE_TURN); edit(p, rows.get(index)); }
            }
        }
    }

    private void editClick(Player p, int slot, ItemStack cursor, boolean left, boolean right, boolean shift) {
        var d = drafts.get(p.getUniqueId());
        if (d == null) { openList(p, 0); return; }
        if (slot != DELETE) d.deleteArmed = false;
        int ingredient = -1;
        for (int i = 0; i < Math.min(bridge.slots(), INGREDIENT.length); i++) if (INGREDIENT[i] == slot) ingredient = i;
        if (ingredient >= 0 || slot == RESULT) {
            boolean result = slot == RESULT;
            Part current = result ? d.result : d.ingredients[ingredient];
            Part next = current;
            int max = result ? 576 : AlchemyProtocol.MAX_AMOUNT;
            if (!empty(cursor)) {
                String key = keyFor(p, cursor);
                if (key == null) return;
                if (!result && duplicateIngredient(d, key, ingredient)) { p.sendMessage("§e[연금술] 같은 재료가 이미 다른 칸에 있습니다. 그 칸의 개수를 늘려 주세요."); return; }
                var copy = cursor.clone(); copy.setAmount(1);
                next = new Part(key, copy, Math.min(max, cursor.getAmount()));
            } else if (current != null) {
                if (shift) next = null;
                else if (left) next = new Part(current.key(), current.item(), Math.min(max, current.amount() + 1));
                else if (right) next = current.amount() <= 1 ? null : new Part(current.key(), current.item(), current.amount() - 1);
            }
            if (result) d.result = next; else d.ingredients[ingredient] = next;
            sound(p, Sound.UI_BUTTON_CLICK);
            redraw(p);
            return;
        }
        for (int i = 0; i < EXP_SLOTS.length; i++) if (EXP_SLOTS[i] == slot) {
            d.exp = Math.clamp((long) d.exp + EXP_STEPS[i], 0, AlchemyProtocol.MAX_EXP);
            sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP); redraw(p); return;
        }
        switch (slot) {
            case NAME -> {
                naming.add(p.getUniqueId());
                p.closeInventory();
                p.sendMessage("§6[연금술] 채팅창에 레시피 이름을 입력하세요. (최대 " + AlchemyProtocol.MAX_NAME + "자, 그만두려면 '취소')");
            }
            case ENABLED -> { d.enabled = !d.enabled; sound(p, Sound.UI_BUTTON_CLICK); redraw(p); }
            case BACK -> openList(p, d.page);
            case DELETE -> {
                if (d.id == null) return;
                if (!d.deleteArmed) { d.deleteArmed = true; sound(p, Sound.BLOCK_NOTE_BLOCK_BASS); redraw(p); return; }
                delete(p, d);
            }
            case SAVE -> save(p, d);
            default -> { }
        }
    }

    private boolean duplicateIngredient(Draft d, String key, int except) {
        for (int i = 0; i < d.ingredients.length; i++) if (i != except && d.ingredients[i] != null && d.ingredients[i].key().equals(key)) return true;
        return false;
    }

    private String keyFor(Player p, ItemStack item) {
        String key = bridge.keyOf(item);
        if (key == null || !AlchemyProtocol.validKey(key) || key.contains(".")) {
            p.sendMessage("§c[연금술] 이 아이템은 레시피에 쓸 수 없습니다: " + key);
            return null;
        }
        return key;
    }

    private void shiftAdd(Player p, ItemStack item) {
        var d = drafts.get(p.getUniqueId());
        if (d == null || empty(item)) return;
        String key = keyFor(p, item);
        if (key == null) return;
        int free = -1;
        for (int i = 0; i < Math.min(bridge.slots(), INGREDIENT.length); i++) {
            if (d.ingredients[i] != null && d.ingredients[i].key().equals(key)) {   // already there: one more
                d.ingredients[i] = new Part(key, d.ingredients[i].item(), Math.min(AlchemyProtocol.MAX_AMOUNT, d.ingredients[i].amount() + 1));
                sound(p, Sound.UI_BUTTON_CLICK); redraw(p); return;
            }
            if (free < 0 && d.ingredients[i] == null) free = i;
        }
        if (free < 0) { p.sendMessage("§e[연금술] 재료 칸이 가득 찼습니다."); return; }
        var copy = item.clone(); copy.setAmount(1);
        d.ingredients[free] = new Part(key, copy, 1);
        sound(p, Sound.UI_BUTTON_CLICK); redraw(p);
    }

    // ------------------------------------------------------------------ name from chat

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!naming.remove(id)) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            var d = drafts.get(id);
            if (p == null || d == null || !admin(p)) return;
            if (text.equals("취소")) p.sendMessage("§7[연금술] 이름 변경을 취소했습니다.");
            else if (text.isEmpty() || text.length() > AlchemyProtocol.MAX_NAME) p.sendMessage("§c[연금술] 이름은 1~" + AlchemyProtocol.MAX_NAME + "자로 입력하세요.");
            else { d.name = text; p.sendMessage("§a[연금술] 이름: " + text + " (저장을 눌러야 적용됩니다)"); }
            openEdit(p);
        });
    }

    /** Chat plugins that still listen to the legacy chat event must not relay the typed name either. */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void legacyChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        if (naming.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler public void quit(PlayerQuitEvent e) { drafts.remove(e.getPlayer().getUniqueId()); naming.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ save / delete

    private void save(Player p, Draft d) {
        if (d.result == null) { fail(p, "결과 아이템을 넣어 주세요."); return; }
        var ingredients = new LinkedHashMap<String, Integer>();
        var samples = new HashMap<String, ItemStack>();
        for (Part part : d.ingredients) if (part != null) { ingredients.merge(part.key(), part.amount(), Integer::sum); samples.putIfAbsent(part.key(), part.item()); }
        if (ingredients.isEmpty()) { fail(p, "재료를 하나 이상 넣어 주세요."); return; }
        if (ingredients.size() > bridge.slots()) { fail(p, "재료 종류는 최대 " + bridge.slots() + "개입니다."); return; }
        if (bridge.create(d.result.key(), 1) == null) { fail(p, "결과 아이템(" + d.result.key() + ")을 서버에서 만들 수 없습니다."); return; }
        String name = d.name.isBlank() ? AlchemyBridge.displayName(d.result.item()) : d.name;
        if (name.isBlank()) name = d.result.key();
        if (name.length() > AlchemyProtocol.MAX_NAME) name = name.substring(0, AlchemyProtocol.MAX_NAME);
        for (Row r : rows()) if (!r.id().equals(d.id) && r.ingredients().equals(ingredients)) { fail(p, "'" + r.name() + "' 레시피와 재료가 완전히 같습니다. 재료나 개수를 바꿔 주세요."); return; }

        File file = bridge.file();
        String before;
        YamlConfiguration yaml;
        try { before = Files.readString(file.toPath(), StandardCharsets.UTF_8); yaml = load(); }
        catch (Exception e) { fail(p, "alchemy.yml 을 읽지 못했습니다: " + e.getMessage()); return; }
        String id = d.id;
        if (id == null) { int n = 1; while (yaml.contains("recipes.recipe_" + n)) n++; id = "recipe_" + n; }
        String path = "recipes." + id;
        yaml.set(path, null);
        yaml.set(path + ".name", name);
        yaml.set(path + ".result", d.result.key());
        yaml.set(path + ".amount", d.result.amount());
        yaml.set(path + ".exp", d.exp);
        yaml.set(path + ".order", d.order);
        yaml.set(path + ".enabled", d.enabled);
        var section = yaml.createSection(path + ".ingredients");
        ingredients.forEach(section::set);
        // ingredients the slot picker would not offer: register them as materials so players can put them in
        var materialItems = new ArrayList<>(yaml.getStringList("settings.material-items"));
        var added = new ArrayList<String>();
        for (var e : ingredients.keySet()) if (!bridge.material(samples.get(e), e) && !materialItems.contains(e)) { materialItems.add(e); added.add(e); }
        if (!added.isEmpty()) yaml.set("settings.material-items", materialItems);
        try { yaml.save(file); }
        catch (Exception e) { fail(p, "저장하지 못했습니다: " + e.getMessage()); return; }
        String error = bridge.reloadAndPush();
        if (error != null) {
            try { Files.writeString(file.toPath(), before, StandardCharsets.UTF_8); } catch (Exception ignored) { }
            bridge.reloadAndPush();
            fail(p, "저장 내용을 불러오지 못해 되돌렸습니다: " + error);
            return;
        }
        d.id = id; d.name = name;
        plugin.getLogger().info("ALCHEMY EDIT save by=" + p.getName() + " recipe=" + id);
        p.sendMessage("§a[연금술] '" + name + "' 저장 완료 · 바로 적용되었습니다." + (d.enabled ? "" : " (꺼짐 상태)"));
        if (!added.isEmpty()) p.sendMessage("§7[연금술] 재료 카테고리가 아닌 아이템을 재료로 등록했습니다: " + added);
        sound(p, Sound.ENTITY_PLAYER_LEVELUP);
        openList(p, d.page);
    }

    private void delete(Player p, Draft d) {
        File file = bridge.file();
        try {
            String before = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            var yaml = load();
            yaml.set("recipes." + d.id, null);
            yaml.save(file);
            String error = bridge.reloadAndPush();
            if (error != null) { Files.writeString(file.toPath(), before, StandardCharsets.UTF_8); bridge.reloadAndPush(); fail(p, "삭제 후 불러오기 오류로 되돌렸습니다: " + error); return; }
        } catch (Exception e) { fail(p, "삭제하지 못했습니다: " + e.getMessage()); return; }
        plugin.getLogger().info("ALCHEMY EDIT delete by=" + p.getName() + " recipe=" + d.id);
        p.sendMessage("§a[연금술] '" + d.name + "' 레시피를 삭제했습니다.");
        sound(p, Sound.ENTITY_ITEM_BREAK);
        openList(p, d.page);
    }

    private void fail(Player p, String message) {
        p.sendMessage("§c[연금술] " + message);
        sound(p, Sound.BLOCK_NOTE_BLOCK_BASS);
    }

    private static void sound(Player p, Sound s) { p.playSound(p.getLocation(), s, .6f, 1.2f); }
}
