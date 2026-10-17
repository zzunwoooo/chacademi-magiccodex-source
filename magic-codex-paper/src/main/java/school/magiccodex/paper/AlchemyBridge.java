package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.AlchemyProtocol;
import school.magiccodex.protocol.AlchemyProtocol.*;
import school.magiccodex.protocol.AlchemyRules;

/**
 * 연금술 (2026-10-16): crafting that costs vanilla experience points, with hidden recipes.
 *
 * - Recipes live in plugins/MagicCodexBridge/alchemy.yml (/연금술관리 새로고침). Order of ingredients never matters:
 *   a recipe is "which items, how many in total".
 * - The slot picker only offers items whose first lore line is a material category (settings.material-categories,
 *   default 재료) or that are listed in settings.material-items.
 * - Pressing 합성 with a recipe's exact ingredients and enough experience crafts it: ingredients and experience are
 *   taken, the result goes to the inventory and
 *   whatever does not fit is sent to the mailbox. A wrong mix costs nothing; if it is one ingredient away from a
 *   recipe (or has the right ingredients in the wrong amounts) the player gets a hint instead.
 * - A recipe is discovered (LuckPerms magiccodex.alchemy.recipe.&lt;id&gt;) only by its first successful craft. Without
 *   enough experience nothing happens: no discovery, nothing spent (owner decision 2026-10-16).
 * - Undiscovered recipes never reach the client (it only learns how many are still locked).
 * Everything runs on the main thread (plugin messages, commands, the refresh timer).
 */
final class AlchemyBridge implements PluginMessageListener, Listener, CommandExecutor, TabCompleter {
    private record Recipe(String id, String name, String result, int amount, int exp, Map<String, Integer> ingredients, int order) {}
    private record Inventory(Map<String, Integer> counts, Map<String, ItemStack> samples, Set<String> materials) {}

    private static final String PERMISSION = "magiccodex.alchemy.recipe.";
    private final MagicCodexBridge plugin;
    private final ItemsAdderItems itemsAdder;
    private volatile List<Recipe> recipes = List.of();
    private Set<String> categories = Set.of("재료"), materialItems = Set.of();
    private int slots = 6;
    private boolean nearHint = true;
    private String mailTitle = "연금술 결과물", mailBody = "인벤토리가 가득 차서 연금술 결과물을 우편으로 보내 드립니다.";
    private final Map<String, String> messages = new HashMap<>();
    /** key -> serialized icon (canonical item from ItemsAdder / vanilla, else the first stack seen). Cleared on reload. */
    private final Map<String, byte[]> previews = new HashMap<>();
    /** Discoveries sent to LuckPerms but maybe not visible as a permission yet. */
    private final Map<UUID, Set<String>> pendingGrants = new HashMap<>();
    /** Players with the window open -> last state signature (resend only when it changes). */
    private final Map<UUID, Integer> viewers = new HashMap<>();
    private final Map<UUID, Long> limits = new HashMap<>();
    private AlchemyEditor editor;

    AlchemyBridge(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        itemsAdder = new ItemsAdderItems(plugin.getLogger());
        if (!new File(plugin.getDataFolder(), "alchemy.yml").exists()) plugin.saveResource("alchemy.yml", false);
        reload();
        editor = new AlchemyEditor(plugin, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, AlchemyProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, AlchemyProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String name : List.of("연금술", "연금술관리")) {
            var command = Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
        // Inventory or experience changed while the window is open (pickups, other plugins): resend, at most once a second.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var id : List.copyOf(viewers.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) { viewers.remove(id); continue; }
                var inv = inventory(p);
                if (signature(p, inv) != viewers.get(id)) send(p, AlchemyProtocol.KIND_STATE, "", inv);
            }
        }, 20, 20);
    }

    // ------------------------------------------------------------------ config

    void reload() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "alchemy.yml"));
        int count = yaml.getInt("settings.slots", 6);
        if (count < 1 || count > AlchemyProtocol.MAX_SLOTS) throw new IllegalArgumentException("settings.slots 는 1~" + AlchemyProtocol.MAX_SLOTS);
        var cats = new HashSet<String>();
        for (String c : yaml.getStringList("settings.material-categories")) if (!normalize(c).isEmpty()) cats.add(normalize(c));
        var items = new HashSet<String>();
        for (String k : yaml.getStringList("settings.material-items")) items.add(key(k, "settings.material-items"));
        var msg = new HashMap<String, String>();
        var ms = yaml.getConfigurationSection("messages");
        for (String k : List.of("near", "near-amount", "nothing", "crafted", "discovered", "mailed", "no-exp", "no-exp-locked", "missing", "empty"))
            msg.put(k, ms == null ? "" : ms.getString(k, ""));
        var list = new ArrayList<Recipe>();
        var seen = new HashMap<Map<String, Integer>, String>();
        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        if (root != null) for (String id : root.getKeys(false)) {
            var s = root.getConfigurationSection(id);
            if (s == null) throw new IllegalArgumentException("recipes." + id + " 형식 오류");
            if (!s.getBoolean("enabled", true)) continue;
            if (!AlchemyProtocol.validId(id)) throw new IllegalArgumentException("레시피 아이디는 영문 소문자·숫자·_ 만: " + id);
            String name = s.getString("name", "").strip();
            if (name.isEmpty() || name.length() > AlchemyProtocol.MAX_NAME) throw new IllegalArgumentException(id + ".name 은 1~" + AlchemyProtocol.MAX_NAME + "자");
            String result = key(s.getString("result", ""), id + ".result");
            int amount = s.getInt("amount", 1);
            if (amount < 1 || amount > 576) throw new IllegalArgumentException(id + ".amount 는 1~576");
            int exp = s.getInt("exp", 0);
            if (exp < 0 || exp > AlchemyProtocol.MAX_EXP) throw new IllegalArgumentException(id + ".exp 는 0~" + AlchemyProtocol.MAX_EXP);
            var is = s.getConfigurationSection("ingredients");
            if (is == null || is.getKeys(false).isEmpty()) throw new IllegalArgumentException(id + ".ingredients 가 비어 있음");
            var ingredients = new LinkedHashMap<String, Integer>();
            for (String raw : is.getKeys(false)) {
                int n = is.getInt(raw, 0);
                if (n < 1 || n > AlchemyProtocol.MAX_AMOUNT) throw new IllegalArgumentException(id + ".ingredients." + raw + " 개수는 1~" + AlchemyProtocol.MAX_AMOUNT);
                ingredients.merge(key(raw, id + ".ingredients"), n, Integer::sum);
            }
            if (ingredients.size() > count) throw new IllegalArgumentException(id + ": 재료 종류(" + ingredients.size() + ")가 칸 수(" + count + ")보다 많음");
            for (int n : ingredients.values()) if (n > AlchemyProtocol.MAX_AMOUNT) throw new IllegalArgumentException(id + ": 한 재료는 최대 " + AlchemyProtocol.MAX_AMOUNT + "개");
            String clash = seen.putIfAbsent(Map.copyOf(ingredients), id);
            if (clash != null) throw new IllegalArgumentException(id + ": " + clash + " 와 재료가 똑같음");
            list.add(new Recipe(id, name, result, amount, exp, Collections.unmodifiableMap(ingredients), s.getInt("order", 0)));
        }
        if (list.size() > AlchemyProtocol.MAX_RECIPES) throw new IllegalArgumentException("레시피는 최대 " + AlchemyProtocol.MAX_RECIPES + "개");
        list.sort(Comparator.comparingInt(Recipe::order).thenComparing(Recipe::id));
        // commit only after everything parsed
        recipes = List.copyOf(list); slots = count; categories = Set.copyOf(cats); materialItems = Set.copyOf(items);
        nearHint = yaml.getBoolean("settings.near-hint", true);
        mailTitle = yaml.getString("settings.mail-title", mailTitle); mailBody = yaml.getString("settings.mail-body", mailBody);
        messages.clear(); messages.putAll(msg);
        previews.clear();
    }

    /** "item:lily" (ItemsAdder) or "minecraft:sugar"; a bare "sugar" means minecraft:sugar. */
    private static String key(String raw, String where) {
        String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!v.contains(":")) v = "minecraft:" + v;
        if (!AlchemyProtocol.validKey(v)) throw new IllegalArgumentException(where + ": 아이템 표기 오류 '" + raw + "' (예: item:lily, minecraft:sugar)");
        return v;
    }

    private static String normalize(String category) {
        return category == null ? "" : category.replaceAll("[\\[\\]()<>{}「」『』【】〔〕]", "").strip();
    }

    private String message(String key, Recipe r, int amount, String item) {
        String m = messages.getOrDefault(key, "");
        if (m.isEmpty() && key.equals("no-exp-locked")) m = "경험치가 부족해 마법진이 반응하지 않는다.";
        if (r != null) m = m.replace("{name}", r.name()).replace("{exp}", Integer.toString(r.exp()));
        m = m.replace("{amount}", Integer.toString(amount)).replace("{item}", item == null ? "" : item);
        return m.length() > AlchemyProtocol.MAX_NOTICE ? m.substring(0, AlchemyProtocol.MAX_NOTICE) : m;
    }

    // ------------------------------------------------------------------ items

    private static boolean empty(ItemStack i) { return i == null || i.getType().isAir() || i.getAmount() <= 0; }

    /** The item's alchemy key (ItemsAdder id first, else the vanilla id), or null for air. */
    String keyOf(ItemStack item) {
        if (empty(item)) return null;
        String ia = itemsAdder.idOf(item);
        if (ia != null) { String k = ia.toLowerCase(Locale.ROOT); if (AlchemyProtocol.validKey(k)) return k; }
        return item.getType().getKey().toString();
    }

    /** Category 재료: first lore line (brackets ignored) is a material category, or the key is listed in material-items. */
    boolean material(ItemStack item, String key) {
        if (materialItems.contains(key)) return true;
        var meta = item.getItemMeta();
        var lore = meta == null ? null : meta.lore();
        if (lore == null || lore.isEmpty() || lore.getFirst() == null) return false;
        return categories.contains(normalize(PlainTextComponentSerializer.plainText().serialize(lore.getFirst())));
    }

    /** A new stack of this key (ItemsAdder or vanilla), or null when it does not exist. */
    ItemStack create(String key, int amount) {
        if (key.startsWith("minecraft:")) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(key);
            if (m == null || m.isAir() || !m.isItem()) return null;
            return new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        }
        return itemsAdder.create(key, amount);
    }

    byte[] preview(String key, ItemStack fallback) {
        byte[] cached = previews.get(key);
        if (cached != null) return cached;
        ItemStack canonical = create(key, 1);
        ItemStack source = canonical != null ? canonical : fallback;
        byte[] bytes = source == null ? new byte[0] : serialize(source);
        if (canonical != null || fallback != null) previews.put(key, bytes);
        return bytes;
    }

    /** Icon bytes within MAX_PREVIEW: full stack, then without lore/data, then the bare material. */
    private static byte[] serialize(ItemStack item) {
        try {
            ItemStack one = item.clone(); one.setAmount(1);
            byte[] b = one.serializeAsBytes();
            if (b.length <= AlchemyProtocol.MAX_PREVIEW) return b;
            var meta = one.getItemMeta();
            if (meta != null) {
                meta.lore(null);
                var pdc = meta.getPersistentDataContainer();
                for (var k : List.copyOf(pdc.getKeys())) pdc.remove(k);
                one.setItemMeta(meta);
                b = one.serializeAsBytes();
                if (b.length <= AlchemyProtocol.MAX_PREVIEW) return b;
            }
            b = new ItemStack(item.getType()).serializeAsBytes();
            return b.length <= AlchemyProtocol.MAX_PREVIEW ? b : new byte[0];
        } catch (RuntimeException e) { return new byte[0]; }
    }

    static String displayName(ItemStack item) {
        if (item == null) return "";
        var meta = item.getItemMeta();
        String n = meta != null && meta.hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : meta != null && meta.hasItemName() ? PlainTextComponentSerializer.plainText().serialize(meta.itemName())
                : item.getType().getKey().getKey().replace('_', ' ');
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    /** Main storage only (hotbar + 27): armor, offhand and the equipment window's accessories are never used. */
    private Inventory inventory(Player p) {
        var counts = new LinkedHashMap<String, Integer>(); var samples = new HashMap<String, ItemStack>(); var materials = new LinkedHashSet<String>();
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            String key = keyOf(item);
            if (key == null) continue;
            counts.merge(key, item.getAmount(), Integer::sum);
            samples.putIfAbsent(key, item);
            if (material(item, key)) materials.add(key);
        }
        return new Inventory(counts, samples, materials);
    }

    // ------------------------------------------------------------------ discovery

    private boolean discovered(Player p, String id) {
        String node = PERMISSION + id;
        // isPermissionSet: an operator does not count as having discovered everything (unregistered nodes default to op).
        return (p.isPermissionSet(node) && p.hasPermission(node)) || (p.isPermissionSet(PERMISSION + "*") && p.hasPermission(PERMISSION + "*"))
                || pendingGrants.getOrDefault(p.getUniqueId(), Set.of()).contains(id);
    }

    private void discover(Player p, Recipe r) {
        UUID uid = p.getUniqueId();
        pendingGrants.computeIfAbsent(uid, k -> new HashSet<>()).add(r.id());
        var future = ArtifactGrants.grant(uid, PERMISSION + r.id());
        if (future == null) { plugin.getLogger().warning("ALCHEMY DISCOVERY NOT SAVED (LuckPerms 없음) player=" + p.getName() + " recipe=" + r.id()); return; }
        future.whenComplete((ok, error) -> {
            if (error != null) plugin.getLogger().warning("ALCHEMY DISCOVERY NOT SAVED player=" + uid + " recipe=" + r.id() + " " + error);
        });
    }

    // ------------------------------------------------------------------ state

    private int signature(Player p, Inventory inv) {
        int known = 0;
        for (Recipe r : recipes) if (discovered(p, r.id())) known = known * 31 + r.id().hashCode();
        return Objects.hash(inv.counts(), inv.materials(), known, recipes.size(), System.identityHashCode(recipes));
    }

    private void send(Player p, int kind, String notice) { send(p, kind, notice, inventory(p)); }

    private void send(Player p, int kind, String notice, Inventory inv) {
        if (!p.getListeningPluginChannels().contains(AlchemyProtocol.RESPONSE)) return;
        var known = new ArrayList<AlchemyProtocol.Recipe>(); int locked = 0;
        var keys = new LinkedHashSet<String>(inv.materials());
        for (Recipe r : recipes) {
            if (!discovered(p, r.id())) { locked++; continue; }
            var ingredients = new ArrayList<AlchemyProtocol.Stack>();
            r.ingredients().forEach((k, n) -> { ingredients.add(new AlchemyProtocol.Stack(k, n)); keys.add(k); });
            keys.add(r.result());
            known.add(new AlchemyProtocol.Recipe(r.id(), r.name(), r.result(), r.amount(), r.exp(), ingredients));
        }
        var items = new ArrayList<Item>(); var materials = new ArrayList<AlchemyProtocol.Material>();
        for (String k : keys) {
            items.add(new Item(k, preview(k, inv.samples().get(k))));
            if (inv.materials().contains(k) || inv.counts().containsKey(k)) materials.add(new AlchemyProtocol.Material(k, inv.counts().getOrDefault(k, 0), inv.materials().contains(k)));
        }
        byte[] frame;
        try { frame = AlchemyProtocol.encode(new State(kind, slots, items, materials, known, locked, notice)); }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("연금술 상태 전송 실패 (" + p.getName() + "): " + e.getMessage()); return; }
        p.sendPluginMessage(plugin, AlchemyProtocol.RESPONSE, frame);
        if (viewers.containsKey(p.getUniqueId()) || kind == AlchemyProtocol.KIND_OPEN) viewers.put(p.getUniqueId(), signature(p, inv));
    }

    private void result(Player p, long sequence, int outcome, Recipe r, int amount, boolean mailed, String notice) {
        if (!p.getListeningPluginChannels().contains(AlchemyProtocol.RESPONSE)) return;
        p.sendPluginMessage(plugin, AlchemyProtocol.RESPONSE, AlchemyProtocol.encode(new Result(sequence, outcome,
                r == null ? "" : r.id(), r == null ? "" : r.result(), amount, mailed, notice == null ? "" : notice)));
    }

    // ------------------------------------------------------------------ requests

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!AlchemyProtocol.REQUEST.equals(channel) || !p.getListeningPluginChannels().contains(AlchemyProtocol.RESPONSE)) return;
        Request r;
        try { r = AlchemyProtocol.request(bytes); } catch (IllegalArgumentException e) { return; }
        if (r.action() == AlchemyProtocol.CLOSE) { viewers.remove(p.getUniqueId()); return; }
        long now = System.currentTimeMillis();
        if (now < limits.getOrDefault(p.getUniqueId(), 0L)) {
            if (r.action() == AlchemyProtocol.CRAFT) result(p, r.sequence(), AlchemyProtocol.REFUSED, null, 0, false, "잠시 뒤 다시 시도해 주세요.");
            return;
        }
        limits.put(p.getUniqueId(), now + (r.action() == AlchemyProtocol.CRAFT ? 400 : 200));
        if (r.action() == AlchemyProtocol.QUERY) { viewers.put(p.getUniqueId(), 0); send(p, AlchemyProtocol.KIND_STATE, ""); return; }
        craft(p, r);
    }

    private void craft(Player p, Request r) {
        long seq = r.sequence();
        if (!plugin.playerStateReady(p)) { result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, "캐릭터 정보를 불러오는 중입니다."); return; }
        if (!p.hasPermission("magiccodex.alchemy.use") || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, "지금은 연금술을 할 수 없습니다."); return; }
        // Never touch the inventory while a container or the vanilla cursor holds an item (same rule as the equipment window).
        if (p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING || !empty(p.getItemOnCursor())) {
            result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, "다른 보관함을 닫은 뒤 다시 시도해 주세요."); return;
        }
        if (r.slots().size() > slots) { result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, "재료 칸이 바뀌었습니다. 창을 다시 열어 주세요."); return; }
        var want = AlchemyRules.merge(r.slots());
        if (want.isEmpty()) { result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, message("empty", null, 0, "")); return; }
        var inv = inventory(p);
        for (var e : want.entrySet()) if (inv.counts().getOrDefault(e.getKey(), 0) < e.getValue()) {
            ItemStack sample = inv.samples().get(e.getKey()); if (sample == null) sample = create(e.getKey(), 1);
            result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, message("missing", null, 0, sample == null ? e.getKey() : displayName(sample)));
            send(p, AlchemyProtocol.KIND_STATE, "", inv); return;
        }
        Recipe match = null;
        for (Recipe recipe : recipes) if (AlchemyRules.matches(recipe.ingredients(), want)) { match = recipe; break; }
        if (match == null) {
            int hint = nearHint ? AlchemyRules.hint(recipes.stream().map(Recipe::ingredients).toList(), want) : AlchemyProtocol.NOTHING;
            String key = hint == AlchemyProtocol.NEAR_AMOUNT ? "near-amount" : hint == AlchemyProtocol.NEAR ? "near" : "nothing";
            result(p, seq, hint, null, 0, false, message(key, null, 0, "")); return;
        }
        boolean isNew = !discovered(p, match.id());
        long total = AlchemyRules.total(p.getLevel(), p.getExp());
        // Not enough experience: nothing happens at all (no discovery, nothing spent). A recipe is discovered only by a
        // successful craft. An undiscovered recipe's cost is not revealed.
        if (total < match.exp()) {
            plugin.getLogger().info("ALCHEMY NO-EXP player=" + p.getName() + " recipe=" + match.id() + " exp=" + total + "/" + match.exp() + (isNew ? " undiscovered" : ""));
            result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, message(isNew ? "no-exp-locked" : "no-exp", isNew ? null : match, 0, ""));
            send(p, AlchemyProtocol.KIND_STATE, ""); return;
        }
        // results first: an unknown result item must not eat the ingredients
        var outputs = new ArrayList<ItemStack>();
        for (int left = match.amount(); left > 0; ) {
            ItemStack stack = create(match.result(), left);
            if (stack == null) { plugin.getLogger().warning("ALCHEMY RESULT MISSING recipe=" + match.id() + " result=" + match.result()); result(p, seq, AlchemyProtocol.REFUSED, null, 0, false, "결과 아이템을 만들 수 없습니다. 관리자에게 문의해 주세요."); return; }
            outputs.add(stack); left -= stack.getAmount();
        }
        // take ingredients (verified above) and experience
        var storage = p.getInventory();
        for (var e : want.entrySet()) {
            int need = e.getValue();
            for (int i = 0; i < 36 && need > 0; i++) {
                ItemStack item = storage.getItem(i);
                if (!e.getKey().equals(keyOf(item))) continue;
                int take = Math.min(need, item.getAmount());
                need -= take;
                if (take == item.getAmount()) storage.setItem(i, null); else item.setAmount(item.getAmount() - take);
            }
        }
        long after = total - match.exp();
        float[] level = AlchemyRules.fromTotal(after);
        p.setLevel((int) level[0]); p.setExp(Math.min(level[1], 0.9999f)); p.setTotalExperience((int) Math.min(Integer.MAX_VALUE, after));
        var leftover = new ArrayList<ItemStack>(storage.addItem(outputs.toArray(ItemStack[]::new)).values());
        boolean mailed = !leftover.isEmpty();
        String craftId = UUID.randomUUID().toString();
        if (mailed) mail(p, leftover, craftId, match);
        p.updateInventory();
        plugin.getLogger().info("ALCHEMY CRAFT player=" + p.getName() + " recipe=" + match.id() + " amount=" + match.amount() + " exp=" + match.exp()
                + (isNew ? " new" : "") + (mailed ? " mailed=" + leftover.stream().mapToInt(ItemStack::getAmount).sum() : "") + " craft=" + craftId);
        String notice = message(isNew ? "discovered" : "crafted", match, match.amount(), "");
        if (mailed) { String m = message("mailed", match, match.amount(), ""); notice = (notice + " " + m).strip(); if (notice.length() > AlchemyProtocol.MAX_NOTICE) notice = notice.substring(0, AlchemyProtocol.MAX_NOTICE); }
        if (isNew) discover(p, match);
        result(p, seq, isNew ? AlchemyProtocol.DISCOVERED : AlchemyProtocol.CRAFTED, match, match.amount(), mailed, notice);
        send(p, AlchemyProtocol.KIND_STATE, "");
    }

    /** Results that did not fit: mailbox (9 attachments per mail). Without a mailbox they drop at the player's feet. */
    private void mail(Player p, List<ItemStack> items, String craftId, Recipe r) {
        var service = Bukkit.getServicesManager().load(MailService.class);
        if (service == null) { drop(p, items, "우편 기능 없음", craftId); return; }
        for (int part = 0; part * 9 < items.size(); part++) {
            var chunk = List.copyOf(items.subList(part * 9, Math.min(items.size(), part * 9 + 9)));
            String body = mailBody + " (" + r.name() + ")";
            try {
                service.sendSystem("alchemy:" + craftId + ":" + part, p.getUniqueId(), mailTitle, body, chunk).whenComplete((id, error) -> {
                    if (error != null) Bukkit.getScheduler().runTask(plugin, () -> drop(p, chunk, "우편 저장 실패 " + error.getClass().getSimpleName(), craftId));
                });
            } catch (RuntimeException e) { drop(p, chunk, "우편 거절 " + e.getMessage(), craftId); }
        }
    }

    private void drop(Player p, List<ItemStack> items, String why, String craftId) {
        plugin.getLogger().warning("ALCHEMY MAIL FAILED (" + why + ") player=" + p.getUniqueId() + " craft=" + craftId + " items=" + items);
        if (!p.isOnline()) return;
        for (ItemStack item : items) p.getWorld().dropItemNaturally(p.getLocation(), item);
        p.sendMessage("§e[연금술] 우편으로 보내지 못한 결과물을 발밑에 떨어뜨렸습니다.");
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        viewers.remove(id); limits.remove(id); pendingGrants.remove(id);
    }

    // ------------------------------------------------------------------ commands

    int slots() { return slots; }
    File file() { return new File(plugin.getDataFolder(), "alchemy.yml"); }

    /** Reload alchemy.yml and refresh every open alchemy window; the error text, or null on success. */
    String reloadAndPush() {
        try { reload(); } catch (Exception e) { return String.valueOf(e.getMessage()); }
        for (var id : List.copyOf(viewers.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) send(p, AlchemyProtocol.KIND_STATE, ""); }
        return null;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("연금술")) return open(sender, args);
        String sub = args.length == 0 ? "편집" : args[0];
        switch (sub) {
            case "편집", "edit" -> {
                if (sender instanceof Player p) editor.open(p);
                else sender.sendMessage("레시피 편집 창은 게임 안에서 /연금술관리 로 엽니다. (콘솔: 새로고침|목록|발견|잊기)");
            }
            case "새로고침", "reload" -> {
                String error = reloadAndPush();
                if (error != null) { sender.sendMessage("§calchemy.yml 오류: " + error); return true; }
                sender.sendMessage("§a연금술 레시피 " + recipes.size() + "개를 다시 불러왔습니다. (재료 칸 " + slots + "개, 재료 카테고리 " + categories + ")");
            }
            case "목록", "list" -> {
                for (Recipe r : recipes) sender.sendMessage("§f" + r.name() + " §8" + r.id() + " §7→ " + r.result() + " ×" + r.amount() + ", 경험치 " + r.exp() + ", 재료 " + r.ingredients());
                sender.sendMessage("§7총 " + recipes.size() + "개");
            }
            case "발견", "잊기" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) { sender.sendMessage("/연금술관리 " + sub + " <플레이어> <레시피아이디|전체>"); return true; }
                boolean all = args[2].equals("전체") || args[2].equals("*");
                var ids = all ? recipes.stream().map(Recipe::id).toList() : recipes.stream().map(Recipe::id).filter(args[2]::equals).toList();
                if (ids.isEmpty()) { sender.sendMessage("§c없는 레시피: " + args[2]); return true; }
                boolean grant = sub.equals("발견");
                for (String id : ids) {
                    var future = grant ? ArtifactGrants.grant(target.getUniqueId(), PERMISSION + id) : ArtifactGrants.revoke(target.getUniqueId(), PERMISSION + id);
                    if (future == null) { sender.sendMessage("§cLuckPerms 가 없어 저장할 수 없습니다."); return true; }
                    var pending = pendingGrants.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());
                    if (grant) pending.add(id); else pending.remove(id);
                }
                sender.sendMessage("§a" + target.getName() + ": 레시피 " + ids.size() + "개 " + (grant ? "발견 처리" : "잊게 함") + " (LuckPerms 반영까지 잠시 걸릴 수 있음)");
                if (viewers.containsKey(target.getUniqueId())) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (target.isOnline()) send(target, AlchemyProtocol.KIND_STATE, ""); }, 20);
            }
            default -> sender.sendMessage("/연금술관리 (편집 창) | 새로고침 | 목록 | 발견 <플레이어> <레시피|전체> | 잊기 <플레이어> <레시피|전체>");
        }
        return true;
    }

    private boolean open(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("magiccodex.alchemy.admin")) { sender.sendMessage("§c다른 플레이어의 연금술 창은 관리자만 열 수 있습니다."); return true; }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c접속 중인 플레이어가 아닙니다: " + args[0]); return true; }
        } else if (sender instanceof Player p) target = p;
        else { sender.sendMessage("/연금술 <플레이어>"); return true; }
        if (!target.getListeningPluginChannels().contains(AlchemyProtocol.RESPONSE)) { sender.sendMessage("§c차카데미 모드가 있어야 연금술 창을 열 수 있습니다."); return true; }
        send(target, AlchemyProtocol.KIND_OPEN, "");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        var players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (command.getName().equals("연금술")) return args.length == 1 && sender.hasPermission("magiccodex.alchemy.admin") ? players : List.of();
        if (args.length == 1) return List.of("편집", "새로고침", "목록", "발견", "잊기");
        if (args.length == 2 && (args[0].equals("발견") || args[0].equals("잊기"))) return players;
        if (args.length == 3 && (args[0].equals("발견") || args[0].equals("잊기"))) {
            var ids = new ArrayList<String>(); ids.add("전체"); recipes.forEach(r -> ids.add(r.id())); return ids;
        }
        return List.of();
    }
}
