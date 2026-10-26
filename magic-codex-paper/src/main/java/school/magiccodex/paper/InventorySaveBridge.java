package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 인벤세이브 + 사망 시 아공간 배낭 다른 쪽 떨어뜨리기 (오너 2026-10-26).
 *
 * <p><b>인벤세이브</b> (item:inventory_save): 인벤토리(핫바·지금 쪽 27칸·갑옷·왼손)에 있으면 죽어도 인벤토리와 경험치를 그대로
 * 두고 1개만 자동으로 쓴다. 아공간 배낭의 다른 쪽도 그대로. 지금 보이지 않는 배낭 쪽에 넣어 둔 인벤세이브는 세지 않는다.
 *
 * <p><b>인벤세이브 없이 죽으면</b>: 바닐라가 인벤토리(지금 쪽 포함)를 떨어뜨리고, 이어서 아공간 배낭의 다른 쪽(잠금 해제된 쪽 전부,
 * 혹시 남은 잠긴 쪽 포함)도 죽은 자리에 떨어진다 — 공유 DB 에서 먼저 비운(CAS) 뒤에만 떨어뜨림 (BagHuskCoordinator.dropOnDeath).
 * 배낭이 그 순간 동기화 중·쪽 넘기는 중이면 1초마다 최대 {@link #RETRIES}번 다시 해 보고(그동안 쪽 넘기기·정리는 막음), 그래도 안 되면
 * 이번에는 떨어뜨리지 않는다 (아이템은 배낭에 그대로 — 복제·분실 대신 벌칙을 한 번 놓침, 기록은 서버 로그·운영 기록).
 *
 * <p>둘 다 건너뛰는 사망: 경기(큐디치·결투) 중 사망(GeneralPresetBridge.combat — 경기 규칙대로, 인벤세이브 안 씀), keepInventory
 * 가 이미 켜진 사망(게임룰·다른 플러그인 — 인벤세이브 안 씀, 배낭도 그대로), 이 기능을 쓰지 않는 서버(inventory-save.yml servers,
 * 기본 학교·야생 — 아레나 아님). 순서: 이것(HIGH) → EquipmentBridge 장신구 드롭(HIGHEST, keepInventory 면 건너뜀) → 다음 틱 배낭.
 *
 * <p>부담: 사망 때만 일한다 (인벤토리 41칸 한 번 훑기 + 배낭은 쪽 넘기기 한 번과 같은 일).
 */
final class InventorySaveBridge implements Listener {
    static final String FILE = "inventory-save.yml";
    /** 1초마다 다시 해 보는 횟수. 사망 귀환(TravelBridge, 2초마다 5번)이 그 안에 끝나도록 8번. */
    static final int RETRIES = 8;

    private final MagicCodexBridge plugin;
    private final ItemsAdderItems itemsAdder;
    private final NamespacedKey key;
    private String item = "item:inventory_save", name = "인벤세이브";
    private Set<String> servers = Set.of("school", "wild", "wild2");
    private boolean bagDrop = true;
    /** 이번 사망에서 인벤세이브를 쓴 사람 (부활하면 알림). */
    private final Map<UUID, Integer> saved = new HashMap<>();

    InventorySaveBridge(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        this.itemsAdder = new ItemsAdderItems(plugin.getLogger());
        this.key = new NamespacedKey(plugin, "inventory_save");
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ChacaApi.reload(plugin, "인벤세이브", FILE + " (인벤세이브 아이템·쓰는 서버·배낭 다른 쪽 떨어뜨리기)", this::reload);
    }

    void reload() throws Exception {
        var file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) plugin.saveResource(FILE, false);
        var y = new YamlConfiguration();
        y.load(file);
        item = TravelItemRules.item(y.getString("item", item), "item:inventory_save");
        name = TravelItemRules.name(y.getString("item-name", name), "인벤세이브");
        servers = TravelItemRules.ids(y.isList("servers") ? y.getStringList("servers") : null, Set.of("school", "wild", "wild2"));
        bagDrop = y.getBoolean("drop-bag-pages-on-death", true);
    }

    private String self() {
        var travel = plugin.travel();
        try { return travel == null || travel.config() == null ? "" : travel.config().self(); } catch (RuntimeException e) { return ""; }
    }
    /** 서버 ID 를 모르면(travel.yml 없음) 쓰지 않는다 — 아레나일 수 있음. */
    private boolean here() { String s = self(); return !s.isEmpty() && servers.contains(s); }

    private boolean isSave(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        var meta = it.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return true;
        return item.equals(itemsAdder.idOf(it));
    }

    // ------------------------------------------------------------------ 인벤세이브 (HIGH: EquipmentBridge HIGHEST 보다 먼저 keepInventory)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void death(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory() || GeneralPresetBridge.combat(p.getUniqueId()) || !here()) return;
        if (!plugin.playerStateReady(p)) return;   // 캐릭터 정보를 불러오기 전: 예전 인벤토리일 수 있어 건드리지 않음
        var inv = p.getInventory();
        ItemStack[] all = inv.getContents();
        int slot = -1, left = 0;
        for (int i = 0; i < all.length; i++) if (isSave(all[i])) { if (slot < 0) slot = i; left += all[i].getAmount(); }
        if (slot < 0) return;
        ItemStack it = all[slot];
        it.setAmount(it.getAmount() - 1);
        inv.setItem(slot, it.getAmount() <= 0 ? null : it);
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        saved.put(p.getUniqueId(), left - 1);
        plugin.getLogger().info(name + " 사용: " + p.getName() + " (남은 " + (left - 1) + "개) " + AuditLog.where(p.getLocation()));
        var detail = new LinkedHashMap<String, String>();
        detail.put("위치", AuditLog.where(p.getLocation()));
        detail.put("남은 개수", String.valueOf(left - 1));
        ChacaApi.audit(p.getUniqueId(), AuditLog.사망, "사망 · " + name + " 1개 사용 (인벤토리·경험치 유지)", detail);
    }

    @EventHandler public void respawn(PlayerRespawnEvent e) {
        Integer left = saved.remove(e.getPlayer().getUniqueId());
        if (left == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) CmdKit.notify(p, "system", "inventory-save", name + "이 인벤토리를 지켜 줬어요", "1개 사용 · 남은 " + Math.max(0, left) + "개", 0);
        }, 5);
    }

    // ------------------------------------------------------------------ 배낭 다른 쪽 (인벤세이브 없음)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dropBag(PlayerDeathEvent e) {
        if (!bagDrop || e.getKeepInventory()) return;   // 인벤세이브·게임룰·경기(keepInventory) = 배낭도 그대로
        Player p = e.getEntity();
        if (GeneralPresetBridge.combat(p.getUniqueId()) || !here() || !plugin.playerStateReady(p)) return;
        var bags = plugin.bagPagesBridge();
        if (bags == null) return;   // 아공간 배낭이 없는 서버
        Location at = p.getLocation().clone();
        UUID id = p.getUniqueId();
        bags.deathPending(id, true);
        // 다음 틱: HuskSync 사망 저장·바닐라 드롭이 끝난 뒤
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryDrop(p, at, 0), 1);
    }

    private void tryDrop(Player p, Location at, int tries) {
        var bags = plugin.bagPagesBridge();
        UUID id = p.getUniqueId();
        if (bags == null) return;
        if (!p.isOnline()) { bags.deathPending(id, false); return; }
        var r = bags.dropOnDeath(p, at);
        if (r == BagHuskCoordinator.DeathDrop.NOT_READY && tries < RETRIES) { Bukkit.getScheduler().runTaskLater(plugin, () -> tryDrop(p, at, tries + 1), 20); return; }
        if (r == BagHuskCoordinator.DeathDrop.STARTED) return;   // 적용이 끝나면 coordinator 가 풂
        bags.deathPending(id, false);
        if (r == BagHuskCoordinator.DeathDrop.NOT_READY || r == BagHuskCoordinator.DeathDrop.FAILED) {
            plugin.getLogger().warning("아공간 배낭 사망 떨어뜨리기 못 함: " + p.getName() + " (" + r + ") — 다른 쪽 아이템은 배낭에 그대로");
            var detail = new LinkedHashMap<String, String>();
            detail.put("위치", AuditLog.where(at));
            detail.put("이유", r == BagHuskCoordinator.DeathDrop.NOT_READY ? "배낭 동기화 중" : "배낭 처리 실패");
            ChacaApi.audit(id, AuditLog.사망, "사망 · 아공간 배낭 다른 쪽 못 떨굼 (배낭에 그대로)", detail);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        saved.remove(id);
        var bags = plugin.bagPagesBridge();
        if (bags != null) bags.deathPending(id, false);
    }
}
