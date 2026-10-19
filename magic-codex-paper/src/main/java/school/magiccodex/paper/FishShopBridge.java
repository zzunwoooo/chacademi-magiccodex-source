package school.magiccodex.paper;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.FishShopProtocol;
import school.magiccodex.protocol.FishShopProtocol.*;
import school.magiccodex.protocol.FishingProtocol;

/**
 * 물고기 판매소 (2026-10-20, owner reference "물고기 판매소 / 물고기 매입상 에드윈").
 *
 * A shop of its own (not a ShopStore shop): it buys fish straight out of the 통발 tank. The sale tab sells every fish of
 * the checked grades except locked species (owner: 잠긴 물고기는 크기·종류 무관하고 팔리지 않게). The lock tab sets
 * the locked species (per player, shared by school and wild).
 *
 * Prices live in fish-shop.yml and can be changed at any time (/물고기상점관리 가격 …, or edit + 리로드):
 * one fish = base x size factor, base = per-fish override or grade base (x special bonus for fish with special
 * conditions), size factor = linear from size-min (smallest of the species) to size-max (largest).
 * NPCs and the portrait work like the normal shop (Citizens NPC right click, textures/gui/dialogue/&lt;portrait&gt;.png).
 * fish-shop.yml is per server (NPC ids differ per server); keep prices the same on school and wild.
 */
final class FishShopBridge implements PluginMessageListener, Listener, CommandExecutor, TabCompleter {
    private record Session(Entity anchor, String npcName) {}

    private final MagicCodexBridge plugin;
    private final FishingBridge fishing;
    private final File file;
    private YamlConfiguration yaml;
    private final double[] gradeBase = {80, 400, 2000, 10000};
    private double sizeMin = .7, sizeMax = 1.5, specialBonus = 1.3;
    private final Map<String, Double> overrides = new HashMap<>();
    private String title = "물고기 판매소", role = "물고기 매입상", portrait = "", defaultName = "물고기 매입상";
    private Set<String> npcs = Set.of();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, long[]> limits = new HashMap<>();

    FishShopBridge(MagicCodexBridge plugin, FishingBridge fishing) throws Exception {
        this.plugin = plugin; this.fishing = fishing;
        file = new File(plugin.getDataFolder(), "fish-shop.yml");
        if (!file.exists()) plugin.saveResource("fish-shop.yml", false);
        reload();
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, FishShopProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, FishShopProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("물고기상점관리"));
        command.setExecutor(this); command.setTabCompleter(this);
        citizens();
    }

    void close() { sessions.clear(); }

    // ------------------------------------------------------------------ config

    void reload() throws Exception {
        var y = new YamlConfiguration();
        y.load(file);
        double[] base = gradeBase.clone();
        for (int g = 0; g < 4; g++) {
            double v = y.getDouble("prices.grade." + FishingProtocol.GRADE_LABELS[g], base[g]);
            if (v < 0 || v > 1e12) throw new IllegalArgumentException("prices.grade." + FishingProtocol.GRADE_LABELS[g] + " 는 0 이상");
            base[g] = v;
        }
        double min = y.getDouble("prices.size-min", .7), max = y.getDouble("prices.size-max", 1.5), bonus = y.getDouble("prices.special-bonus", 1.3);
        if (min < 0 || max < min) throw new IllegalArgumentException("prices.size-min ≤ size-max 이어야 함");
        var over = new HashMap<String, Double>();
        var fs = y.getConfigurationSection("prices.fish");
        if (fs != null) for (String id : fs.getKeys(false)) {
            if (!fishing.config().fishById.containsKey(id)) throw new IllegalArgumentException("prices.fish." + id + ": 없는 물고기");
            over.put(id, fs.getDouble(id));
        }
        // commit
        yaml = y; System.arraycopy(base, 0, gradeBase, 0, 4); sizeMin = min; sizeMax = max; specialBonus = bonus;
        overrides.clear(); overrides.putAll(over);
        title = cut(y.getString("title", "물고기 판매소")); role = cut(y.getString("role", "물고기 매입상"));
        defaultName = cut(y.getString("npc-name", "물고기 매입상"));
        portrait = y.getString("portrait", "");
        if (!portrait.matches("[a-z0-9_/-]{0,120}") || portrait.contains("..")) portrait = "";
        npcs = Set.copyOf(y.getStringList("npcs"));
    }

    private void save() throws Exception { yaml.save(file); reload(); }

    static boolean special(FishingConfig.Fish f) {
        return !f.weather().equals("전체") || !f.moon().isEmpty() || f.underground() || f.yMin() != null || f.yMax() != null;
    }
    double base(FishingConfig.Fish f) {
        Double o = overrides.get(f.id());
        if (o != null) return o;
        return gradeBase[f.grade()] * (special(f) ? specialBonus : 1);
    }
    long price(FishTank.Fish fish) {
        var f = fishing.config().fishById.get(fish.fish());
        if (f == null) return 0;
        return FishShopProtocol.price(base(f), fish.size(), f.minSize(), f.maxSize(), sizeMin, sizeMax);
    }

    // ------------------------------------------------------------------ opening

    private static boolean modded(Player p) { return p.getListeningPluginChannels().contains(FishShopProtocol.RESPONSE); }
    private static boolean near(Player p, Entity anchor) {
        return anchor == null || anchor.isValid() && p.getWorld() == anchor.getWorld() && p.getLocation().distanceSquared(anchor.getLocation()) <= 64;
    }

    void open(Player p, Entity anchor, String npcName) {
        if (!modded(p)) { p.sendMessage("§c물고기 판매소를 열려면 차카데미 모드를 업데이트해 주세요."); return; }
        sessions.put(p.getUniqueId(), new Session(anchor, npcName == null || npcName.isBlank() ? defaultName : cut(npcName)));
        fishing.tank().refresh(p.getUniqueId(), () -> { if (p.isOnline() && sessions.containsKey(p.getUniqueId())) send(p, FishShopProtocol.KIND_OPEN, ""); });
    }

    private List<FishTank.Fish> sellable(Player p, int mask) {
        var tank = fishing.tank().tank(p.getUniqueId());
        var out = new ArrayList<FishTank.Fish>();
        for (var f : tank.fish) {
            var def = fishing.config().fishById.get(f.fish());
            if (def == null || tank.locks.contains(f.fish()) || (mask >> def.grade() & 1) == 0) continue;
            out.add(f);
        }
        return out;
    }

    private void send(Player p, int kind, String notice) {
        var s = sessions.get(p.getUniqueId());
        if (s == null) return;
        var tank = fishing.tank().tank(p.getUniqueId());
        var grades = new ArrayList<Grade>();
        for (int g = 0; g < 4; g++) {
            int count = 0; long total = 0; String sample = ""; long best = -1;
            for (var f : sellable(p, 1 << g)) {
                long v = price(f); count++; total += v;
                if (v > best) { best = v; sample = f.fish(); }
            }
            grades.add(new Grade(count, total, sample));
        }
        var species = new ArrayList<Species>();
        if (kind == FishShopProtocol.KIND_OPEN) for (var f : fishing.config().fish) species.add(new Species(f.id(), FishingGear.cut(f.name()), f.grade()));
        var locks = new ArrayList<String>();
        for (String l : tank.locks) if (FishingProtocol.validId(l) && locks.size() < FishShopProtocol.MAX_SPECIES) locks.add(l);
        try {
            p.sendPluginMessage(plugin, FishShopProtocol.RESPONSE, FishShopProtocol.encode(new State(kind, title, role, s.npcName(), portrait, balance(p),
                    tank.fish.size(), fishing.tank().capacity(), grades, locks, species, cut(notice))));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("물고기 판매소 패킷 실패: " + e.getMessage()); }
    }

    private void result(Player p, long op, boolean ok, int sold, long total, String notice) {
        try { p.sendPluginMessage(plugin, FishShopProtocol.RESPONSE, FishShopProtocol.encode(new Result(op, ok, sold, total, cut(notice)))); }
        catch (IllegalArgumentException ignored) {}
    }

    // ------------------------------------------------------------------ requests

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!FishShopProtocol.REQUEST.equals(channel)) return;
        long[] w = limits.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        long now = System.currentTimeMillis();
        if (now - w[0] > 1000) { w[0] = now; w[1] = 0; }
        if (++w[1] > 10) return;
        Request r;
        try { r = FishShopProtocol.request(bytes); } catch (IllegalArgumentException e) { return; }
        var s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (r.action() == FishShopProtocol.CLOSE) { sessions.remove(p.getUniqueId()); return; }
        if (!near(p, s.anchor())) { sessions.remove(p.getUniqueId()); return; }
        switch (r.action()) {
            case FishShopProtocol.QUERY -> fishing.tank().refresh(p.getUniqueId(), () -> { if (p.isOnline()) send(p, FishShopProtocol.KIND_STATE, ""); });
            case FishShopProtocol.LOCKS -> {
                var set = new HashSet<String>();
                for (String id : r.locks()) if (fishing.config().fishById.containsKey(id)) set.add(id);
                fishing.tank().locks(p.getUniqueId(), set);
                send(p, FishShopProtocol.KIND_STATE, "잠금을 저장했어요. (" + set.size() + "종)");
            }
            case FishShopProtocol.SELL -> sell(p, r);
            default -> {}
        }
    }

    private void sell(Player p, Request r) {
        UUID id = p.getUniqueId();
        if (busy.contains(id)) return;
        if (economy() == null) { result(p, r.operation(), false, 0, 0, "경제 서비스 연결이 필요해요."); return; }
        busy.add(id);
        // re-read the tank first so the decision uses the database (another server may have changed it)
        fishing.tank().refresh(id, () -> {
            if (!p.isOnline()) { busy.remove(id); return; }
            var list = sellable(p, r.mask());
            long expected = 0; for (var f : list) expected += price(f);
            if (list.isEmpty()) { busy.remove(id); result(p, r.operation(), false, 0, 0, "판매할 물고기가 없어요."); send(p, FishShopProtocol.KIND_STATE, ""); return; }
            if (expected != r.expected()) { busy.remove(id); result(p, r.operation(), false, 0, 0, "판매 금액이 바뀌었어요. 다시 확인해 주세요."); send(p, FishShopProtocol.KIND_STATE, ""); return; }
            fishing.tank().remove(id, list, removed -> {
                long total = 0; for (var f : removed) total += price(f);
                boolean paid = true;
                if (total > 0) {
                    try { paid = deposit(p, total); } catch (Exception e) { paid = false; }
                }
                if (!paid) {
                    plugin.getLogger().severe("FISH SHOP PAY FAILED player=" + id + " total=" + total + " fish=" + removed.size() + " -> returning fish to the tank");
                    for (var f : removed) fishing.tank().add(id, f.fish(), f.size());
                    busy.remove(id);
                    result(p, r.operation(), false, 0, 0, "돈을 지급하지 못해서 물고기를 돌려놨어요. 관리자에게 알려 주세요.");
                    send(p, FishShopProtocol.KIND_STATE, "");
                    return;
                }
                plugin.getLogger().info("FISH SHOP sold player=" + p.getName() + " fish=" + removed.size() + " total=" + total + " op=" + r.operation());
                busy.remove(id);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .8f, 1.1f);
                result(p, r.operation(), true, removed.size(), total, removed.size() + "마리를 " + String.format("%,d", total) + " G에 팔았어요.");
                send(p, FishShopProtocol.KIND_STATE, "");
            });
        });
    }

    @EventHandler public void quit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); busy.remove(e.getPlayer().getUniqueId()); limits.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ economy (Vault through reflection, like ShopBridge)

    private record Economy(Class<?> type, Object provider) {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Economy economy() {
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices())
            if (type.getName().equals("net.milkbowl.vault.economy.Economy")) {
                var r = Bukkit.getServicesManager().getRegistration((Class) type);
                if (r != null) return new Economy(type, r.getProvider());
            }
        return null;
    }
    private String balance(Player p) {
        try {
            var e = economy();
            if (e == null) return "";
            double v = ((Number) e.type.getMethod("getBalance", OfflinePlayer.class).invoke(e.provider, p)).doubleValue();
            return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
        } catch (Exception ex) { return ""; }
    }
    private boolean deposit(Player p, long amount) throws Exception {
        var e = economy();
        if (e == null) return false;
        Object result = e.type.getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(e.provider, p, (double) amount);
        return (boolean) result.getClass().getMethod("transactionSuccess").invoke(result);
    }

    // ------------------------------------------------------------------ Citizens NPC

    @SuppressWarnings("unchecked")
    private void citizens() {
        var dep = Bukkit.getPluginManager().getPlugin("Citizens");
        if (dep == null) return;
        try {
            var loader = dep.getClass().getClassLoader();
            Class<? extends Event> type = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent", true, loader);
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (l, event) -> {
                try {
                    var npc = event.getClass().getMethod("getNPC").invoke(event);
                    int npcId = ((Number) npc.getClass().getMethod("getId").invoke(npc)).intValue();
                    if (!npcs.contains("citizens:" + npcId)) return;
                    if (plugin.dialogueBridge() != null && plugin.dialogueBridge().claimed(npcId)) return;
                    var p = (Player) event.getClass().getMethod("getClicker").invoke(event);
                    var anchor = (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
                    String name = String.valueOf(npc.getClass().getMethod("getName").invoke(npc));
                    if (event instanceof Cancellable c) c.setCancelled(true);
                    open(p, anchor, ChatColor.stripColor(name));
                } catch (Exception e) { plugin.getLogger().warning("물고기 판매소 NPC 이벤트를 처리하지 못했습니다: " + e.getClass().getSimpleName()); }
            }, plugin, true);
        } catch (Exception e) { plugin.getLogger().warning("물고기 판매소 Citizens 연결 실패"); }
    }

    // ------------------------------------------------------------------ commands

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/물고기상점관리 열기 [플레이어] | 리로드 | 가격 <일반|고급|유일|전설|물고기ID> <G|기본> | 크기보정 <최소배율> <최대배율> | 조건보정 <배율> | NPC <citizens:ID> | NPC삭제 <citizens:ID> | 초상 <초상ID> | 이름 <NPC 이름> | 가격표 [물고기ID]");
            return true;
        }
        try {
            switch (args[0]) {
                case "열기" -> {
                    Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                    if (t == null) throw new IllegalArgumentException("플레이어를 찾지 못했습니다.");
                    open(t, null, null);
                }
                case "리로드" -> { reload(); sender.sendMessage("§a물고기 판매소 설정을 다시 읽었습니다."); }
                case "가격" -> {
                    if (args.length != 3) throw new IllegalArgumentException("가격 <일반|고급|유일|전설|물고기ID> <G|기본>");
                    int g = FishingProtocol.gradeIndex(args[1]);
                    if (g >= 0) yaml.set("prices.grade." + args[1], number(args[2]));
                    else if (fishing.config().fishById.containsKey(args[1])) yaml.set("prices.fish." + args[1], args[2].equals("기본") ? null : number(args[2]));
                    else throw new IllegalArgumentException("등급이나 물고기 ID를 확인하세요.");
                    save(); sender.sendMessage("§a가격을 바꿨습니다. (" + args[1] + ")");
                }
                case "크기보정" -> {
                    if (args.length != 3) throw new IllegalArgumentException("크기보정 <최소배율> <최대배율>");
                    yaml.set("prices.size-min", number(args[1])); yaml.set("prices.size-max", number(args[2])); save();
                    sender.sendMessage("§a크기 보정을 바꿨습니다.");
                }
                case "조건보정" -> {
                    if (args.length != 2) throw new IllegalArgumentException("조건보정 <배율>");
                    yaml.set("prices.special-bonus", number(args[1])); save(); sender.sendMessage("§a조건 보정을 바꿨습니다.");
                }
                case "NPC", "NPC삭제" -> {
                    if (args.length != 2 || !args[1].matches("citizens:[0-9]{1,9}")) throw new IllegalArgumentException(args[0] + " <citizens:ID>");
                    var list = new ArrayList<>(yaml.getStringList("npcs"));
                    list.remove(args[1]);
                    if (args[0].equals("NPC")) list.add(args[1]);
                    yaml.set("npcs", list); save();
                    sender.sendMessage("§a이 서버의 물고기 판매소 NPC: " + list);
                }
                case "초상" -> {
                    if (args.length != 2 || !args[1].matches("[a-z0-9_/-]{1,120}") || args[1].contains("..")) throw new IllegalArgumentException("초상 <초상ID> (textures/gui/dialogue/<초상ID>.png)");
                    yaml.set("portrait", args[1]); save(); sender.sendMessage("§a초상을 바꿨습니다.");
                }
                case "이름" -> {
                    if (args.length < 2) throw new IllegalArgumentException("이름 <NPC 이름> (Citizens NPC 이름이 없을 때 쓰는 이름)");
                    yaml.set("npc-name", String.join(" ", Arrays.copyOfRange(args, 1, args.length))); save(); sender.sendMessage("§a이름을 바꿨습니다.");
                }
                case "가격표" -> {
                    if (args.length == 2) {
                        var f = fishing.config().fishById.get(args[1]);
                        if (f == null) throw new IllegalArgumentException("물고기 ID를 확인하세요.");
                        sender.sendMessage("§e" + f.name() + " (" + FishingProtocol.GRADE_LABELS[f.grade()] + ") 기본 " + Math.round(base(f)) + " G: "
                                + FishShopProtocol.price(base(f), f.minSize(), f.minSize(), f.maxSize(), sizeMin, sizeMax) + " G (" + f.minSize() + "cm) ~ "
                                + FishShopProtocol.price(base(f), f.maxSize(), f.minSize(), f.maxSize(), sizeMin, sizeMax) + " G (" + f.maxSize() + "cm)");
                    } else {
                        for (int g = 0; g < 4; g++) sender.sendMessage("§e" + FishingProtocol.GRADE_LABELS[g] + " 기본 " + Math.round(gradeBase[g]) + " G");
                        sender.sendMessage("§e크기 보정 ×" + sizeMin + " ~ ×" + sizeMax + ", 조건 보정 ×" + specialBonus + ", 물고기별 가격 " + overrides.size() + "종");
                    }
                }
                default -> throw new IllegalArgumentException("알 수 없는 명령입니다.");
            }
        } catch (Exception e) { sender.sendMessage("§c" + e.getMessage()); }
        return true;
    }

    private static double number(String s) {
        try { double v = Double.parseDouble(s); if (v < 0 || v > 1e12 || !Double.isFinite(v)) throw new NumberFormatException(); return v; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("숫자를 확인하세요: " + s); }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return filter(List.of("열기", "리로드", "가격", "크기보정", "조건보정", "NPC", "NPC삭제", "초상", "이름", "가격표"), args[0]);
        if (args.length == 2 && args[0].equals("열기")) return null;
        if (args.length == 2 && (args[0].equals("가격") || args[0].equals("가격표"))) {
            var all = new ArrayList<>(List.of(FishingProtocol.GRADE_LABELS)); all.addAll(fishing.config().fishById.keySet());
            return filter(all, args[1]);
        }
        if (args.length == 2 && args[0].equals("NPC삭제")) return filter(new ArrayList<>(npcs), args[1]);
        return List.of();
    }
    private static List<String> filter(List<String> all, String prefix) { return all.stream().filter(s -> s.startsWith(prefix)).limit(80).toList(); }
    private static String cut(String s) { return s == null ? "" : s.length() > FishShopProtocol.MAX_TEXT ? s.substring(0, FishShopProtocol.MAX_TEXT) : s; }
}
