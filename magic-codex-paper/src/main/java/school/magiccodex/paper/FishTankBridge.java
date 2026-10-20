package school.magiccodex.paper;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.FishTankProtocol;
import school.magiccodex.protocol.FishTankProtocol.*;

/**
 * 통발 아공간 수조 window (2026-10-20, owner spec + GPT tank art). Opened by the 통발 spell (fish_trap) or
 * /낚시관리 수조. Every action is decided here against the database (FishTank), never by the client:
 *
 *  아이템화  refused when the inventory has no empty slot; the row is deleted first, then the item (same tank id and
 *            caught date in its PDC) is given. Only a row that was really deleted gives an item.
 *  방생      deletes the row (the codex record stays).
 *  즉시 판매 shop price of that one fish; locked species are never sold (owner: 잠긴 물고기는 팔리지 않게); the row is
 *            deleted first, then paid; a failed payment puts the same fish back.
 *  넣기      the item is taken out of the inventory first, then inserted with its own tank id (primary key): a
 *            duplicated item is refused and stays consumed; a database error gives the item back. Capacity is
 *            checked in the database. Itemized fish are not sellable at the shop until they are back in the tank.
 */
final class FishTankBridge implements PluginMessageListener, Listener {
    private final MagicCodexBridge plugin;
    private final FishingBridge fishing;
    private final FishShopBridge shop;     // may be null (shop failed to load): no instant sale
    private final Set<UUID> open = new HashSet<>(), busy = new HashSet<>();
    private final Map<UUID, long[]> limits = new HashMap<>();

    FishTankBridge(MagicCodexBridge plugin, FishingBridge fishing, FishShopBridge shop) {
        this.plugin = plugin; this.fishing = fishing; this.shop = shop;
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, FishTankProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, FishTankProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    void close() { open.clear(); busy.clear(); }

    static boolean modded(Player p) { return p.getListeningPluginChannels().contains(FishTankProtocol.RESPONSE); }

    /** The 통발 spell: false (no mana spent) when the window cannot open. */
    boolean cast(Player p) {
        if (!modded(p)) { p.sendMessage("§c통발 수조를 열려면 차카데미 모드를 업데이트해 주세요."); return false; }
        if (fishing.fishingNow(p.getUniqueId())) { p.sendActionBar(FishingGear.plain("낚시 중에는 통발 수조를 열 수 없어요.", 0xFF9E9E)); return false; }
        open(p);
        return true;
    }
    void open(Player p) {
        open.add(p.getUniqueId());
        fishing.tank().refresh(p.getUniqueId(), () -> { if (p.isOnline() && open.contains(p.getUniqueId())) send(p, FishTankProtocol.KIND_OPEN, ""); });
    }

    // ------------------------------------------------------------------ state

    private long price(FishTank.Fish f) { return shop == null ? 0 : shop.price(f); }

    private void send(Player p, int kind, String notice) {
        if (!open.contains(p.getUniqueId())) return;
        var tank = fishing.tank().tank(p.getUniqueId());
        var config = fishing.config();
        var fish = new ArrayList<Fish>(); var species = new LinkedHashMap<String, Info>();
        for (var f : tank.fish) {
            var def = config.fishById.get(f.fish());
            if (def == null || fish.size() >= FishTankProtocol.MAX_FISH) continue;
            fish.add(new Fish(f.id(), f.fish(), (float) f.size(), f.caughtAt(), price(f), tank.locks.contains(f.fish())));
            species.computeIfAbsent(def.id(), k -> info(def));
        }
        var held = new ArrayList<Held>();
        var inv = p.getInventory();
        for (int slot = 0; slot < FishTankProtocol.MAX_INVENTORY; slot++) {
            if (slot > 35 && slot != 40) continue;   // armour slots never hold fish
            var h = fishing.gear().heldFish(inv.getItem(slot));
            if (h == null) continue;
            var def = config.fishById.get(h.fish());
            if (def == null || !FishTankProtocol.validTankId(h.tankId())) continue;
            held.add(new Held(slot, h.tankId(), def.id(), h.size(), h.caughtAt()));
            species.computeIfAbsent(def.id(), k -> info(def));
        }
        try {
            p.sendPluginMessage(plugin, FishTankProtocol.RESPONSE, FishTankProtocol.encode(new State(kind, fishing.tank().capacity(), inv.firstEmpty() < 0,
                    fish, held, List.copyOf(species.values()), cut(notice))));
        } catch (IllegalArgumentException e) { plugin.getLogger().warning("통발 수조 패킷 실패: " + e.getMessage()); }
    }
    private static Info info(FishingConfig.Fish f) {
        String d = f.description() == null ? "" : f.description();
        if (d.length() > FishTankProtocol.MAX_DESCRIPTION) d = d.substring(0, FishTankProtocol.MAX_DESCRIPTION);
        return new Info(f.id(), FishingGear.cut(f.name()), f.grade(), Math.clamp(f.shape(), 0, 255), d);
    }
    private void result(Player p, Request r, boolean ok, String notice) {
        try { p.sendPluginMessage(plugin, FishTankProtocol.RESPONSE, FishTankProtocol.encode(new Result(r.operation(), r.action(), ok, r.tankId(), cut(notice)))); }
        catch (IllegalArgumentException ignored) {}
    }
    private static String cut(String s) { return s == null ? "" : s.length() > FishTankProtocol.MAX_TEXT ? s.substring(0, FishTankProtocol.MAX_TEXT) : s; }

    // ------------------------------------------------------------------ requests

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!FishTankProtocol.REQUEST.equals(channel)) return;
        long[] w = limits.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        long now = System.currentTimeMillis();
        if (now - w[0] > 1000) { w[0] = now; w[1] = 0; }
        if (++w[1] > 10) return;
        Request r;
        try { r = FishTankProtocol.request(bytes); } catch (IllegalArgumentException e) { return; }
        UUID id = p.getUniqueId();
        if (!open.contains(id)) return;
        if (r.action() == FishTankProtocol.CLOSE) { open.remove(id); return; }
        if (r.action() == FishTankProtocol.QUERY) { fishing.tank().refresh(id, () -> { if (p.isOnline()) send(p, FishTankProtocol.KIND_STATE, ""); }); return; }
        if (busy.contains(id)) { result(p, r, false, "앞의 요청을 처리하고 있어요."); return; }
        busy.add(id);
        // every change is decided on a fresh read (the other server may have changed the tank)
        fishing.tank().refresh(id, () -> {
            if (!p.isOnline()) { busy.remove(id); return; }
            switch (r.action()) {
                case FishTankProtocol.ITEMIZE -> itemize(p, r);
                case FishTankProtocol.RELEASE -> release(p, r);
                case FishTankProtocol.SELL -> sell(p, r);
                case FishTankProtocol.INSERT -> insert(p, r);
                default -> busy.remove(id);
            }
        });
    }

    private FishTank.Fish find(Player p, String tankId) {
        for (var f : fishing.tank().tank(p.getUniqueId()).fish) if (f.id().equals(tankId)) return f;
        return null;
    }
    private void done(Player p, Request r, boolean ok, String notice) {
        busy.remove(p.getUniqueId());
        result(p, r, ok, notice);
        send(p, FishTankProtocol.KIND_STATE, "");
    }
    private void gone(Player p, Request r) { done(p, r, false, "이미 수조에 없는 물고기예요."); }

    private void itemize(Player p, Request r) {
        var f = find(p, r.tankId());
        if (f == null) { gone(p, r); return; }
        if (p.getInventory().firstEmpty() < 0) { done(p, r, false, "인벤토리에 빈 칸이 없어서 꺼낼 수 없어요."); return; }
        ItemStack item = fishing.fishItem(f);
        if (item == null) { done(p, r, false, "더 이상 없는 물고기라서 꺼낼 수 없어요."); return; }
        fishing.tank().remove(p.getUniqueId(), List.of(f), removed -> {
            if (removed.isEmpty()) { gone(p, r); return; }
            // a slot was free a moment ago; if it filled meanwhile the item goes by mail instead of being lost
            fishing.give(p, List.of(item), "fish-tank:" + f.id());
            plugin.getLogger().info("FISH TANK itemize player=" + p.getName() + " fish=" + f.fish() + " id=" + f.id());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, .7f, 1.2f);
            done(p, r, true, "아이템으로 꺼냈어요.");
        });
    }

    private void release(Player p, Request r) {
        var f = find(p, r.tankId());
        if (f == null) { gone(p, r); return; }
        fishing.tank().remove(p.getUniqueId(), List.of(f), removed -> {
            if (removed.isEmpty()) { gone(p, r); return; }
            plugin.getLogger().info("FISH TANK release player=" + p.getName() + " fish=" + f.fish() + " id=" + f.id());
            p.playSound(p.getLocation(), Sound.ENTITY_FISH_SWIM, .8f, 1.1f);
            done(p, r, true, "물고기를 놓아줬어요.");
        });
    }

    private void sell(Player p, Request r) {
        var f = find(p, r.tankId());
        if (f == null) { gone(p, r); return; }
        if (shop == null || !shop.ready()) { done(p, r, false, "지금은 판매할 수 없어요."); return; }
        if (fishing.tank().tank(p.getUniqueId()).locks.contains(f.fish())) { done(p, r, false, "잠긴 종류라서 팔 수 없어요. 판매소에서 잠금을 풀어 주세요."); return; }
        long price = price(f);
        if (price != r.expected()) { done(p, r, false, "가격이 바뀌었어요. 다시 확인해 주세요."); return; }
        fishing.tank().remove(p.getUniqueId(), List.of(f), removed -> {
            if (removed.isEmpty()) { gone(p, r); return; }
            boolean paid;
            try { paid = price == 0 || shop.deposit(p, price); } catch (Exception e) { paid = false; }
            if (!paid) {
                plugin.getLogger().severe("FISH TANK PAY FAILED player=" + p.getUniqueId() + " price=" + price + " fish=" + f + " -> returning it");
                fishing.tank().put(p.getUniqueId(), f, null);
                done(p, r, false, "돈을 지급하지 못해서 물고기를 돌려놨어요.");
                return;
            }
            plugin.getLogger().info("FISH TANK sold player=" + p.getName() + " fish=" + f.fish() + " id=" + f.id() + " price=" + price);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .8f, 1.1f);
            done(p, r, true, String.format("%,d G에 팔았어요.", price));
        });
    }

    private void insert(Player p, Request r) {
        var inv = p.getInventory();
        int slot = r.slot();
        if (slot > 35 && slot != 40) { done(p, r, false, "물고기를 찾지 못했어요."); return; }
        ItemStack item = inv.getItem(slot);
        var h = fishing.gear().heldFish(item);
        var def = h == null ? null : fishing.config().fishById.get(h.fish());
        if (h == null || def == null || !h.tankId().equals(r.tankId())) { done(p, r, false, "그 칸의 물고기가 바뀌었어요. 다시 골라 주세요."); return; }
        if (fishing.tank().full(p.getUniqueId())) { done(p, r, false, "수조가 가득 찼어요."); return; }
        // old fish items (before the tank) have no id: they get one now; stacked copies go in one at a time
        var fish = new FishTank.Fish(h.tankId().isEmpty() ? UUID.randomUUID().toString() : h.tankId(), def.id(),
                Math.round(h.size() * 10) / 10.0, h.caughtAt() > 0 ? h.caughtAt() : System.currentTimeMillis());
        ItemStack taken = item.asOne();
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1); else inv.setItem(slot, null);
        fishing.tank().put(p.getUniqueId(), fish, put -> {
            switch (put) {
                case OK -> {
                    plugin.getLogger().info("FISH TANK insert player=" + p.getName() + " fish=" + fish.fish() + " id=" + fish.id());
                    p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY_FISH, .8f, 1.1f);
                    done(p, r, true, "수조에 넣었어요.");
                }
                case DUPLICATE -> {
                    plugin.getLogger().warning("FISH TANK duplicate item refused player=" + p.getName() + " id=" + fish.id());
                    done(p, r, false, "이미 수조에 있는 물고기예요. (복제된 아이템)");
                }
                case FULL -> { fishing.give(p, List.of(taken), "fish-tank-full:" + fish.id()); done(p, r, false, "수조가 가득 찼어요."); }
                case FAILED -> { fishing.give(p, List.of(taken), "fish-tank-failed:" + fish.id()); done(p, r, false, "수조에 넣지 못했어요. 잠시 뒤 다시 해 주세요."); }
            }
        });
    }

    @EventHandler public void quit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); open.remove(id); busy.remove(id); limits.remove(id); }
}
