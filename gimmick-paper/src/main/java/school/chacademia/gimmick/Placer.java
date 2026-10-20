package school.chacademia.gimmick;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Placing like an ItemsAdder furniture (owner 2026-10-20): every gimmick sits on the block grid, centred on its block,
 * turned in 90° steps. `/기믹 소환` = the block the admin stands in; the placement item (`/기믹 아이템`) = the block next to the
 * clicked face, facing the admin. Overlapping another gimmick or replacing non-air blocks is refused.
 */
final class Placer implements Listener {
    private final GimmickPlugin plugin;
    private final NamespacedKey modelKey;

    Placer(GimmickPlugin plugin) { this.plugin = plugin; this.modelKey = new NamespacedKey(plugin, "place_model"); }

    ItemStack item(String model) {
        var info = plugin.catalog().get(model);
        ItemStack it = new ItemStack(Material.ARMOR_STAND);
        var meta = it.getItemMeta();
        meta.displayName(Component.text("기믹 설치: " + info.name(), NamedTextColor.AQUA));
        meta.lore(List.of(Component.text(model, NamedTextColor.DARK_GRAY), Component.text("블록 면을 우클릭하면 그 칸에 설치 (나를 바라봄)", NamedTextColor.GRAY),
            Component.text("차지: " + info.footW() + "x" + info.footD() + "칸", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, model);
        it.setItemMeta(meta);
        return it;
    }

    boolean isItem(ItemStack it) { return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(modelKey, PersistentDataType.STRING); }

    /** Place on block `feet` (the model's feet). @return the gimmick, or null (reason sent to the player). */
    Gimmick place(Player p, String model, Block feet, int facing, String puzzle, String node) {
        var info = plugin.catalog().get(model);
        if (!plugin.catalog().known(model)) p.sendMessage("알림: models.yml 에 없는 모델입니다. 렌더러에 등록돼 있으면 그대로 소환됩니다.");
        if (!plugin.protection().pair(p, feet.getLocation())) { p.sendActionBar(Component.text("보호 구역이라 설치할 수 없습니다.", NamedTextColor.RED)); return null; }
        var probe = new Gimmick("probe", model, feet.getWorld().getName(), feet.getX(), feet.getY(), feet.getZ(), facing);
        Gimmick other = plugin.world().overlap(probe.world, probe.columns(info), probe.by, probe.layers(info), null);
        if (other != null) { p.sendActionBar(Component.text("그 자리에는 이미 다른 기믹(" + other.id + ")이 있습니다.", NamedTextColor.RED)); return null; }
        if (!info.collision().equals("none") && !info.collision().equals("floor")) {
            int layers = info.collision().equals("platform") ? 1 : probe.layers(info);
            for (int[] c : probe.columns(info)) for (int l = 0; l < layers; l++) {
                Block b = feet.getWorld().getBlockAt(c[0], feet.getY() + l, c[1]);
                if (!b.getType().isAir() && !b.isPassable()) { p.sendActionBar(Component.text("모델이 들어갈 자리가 막혀 있습니다 (" + b.getX() + ", " + b.getY() + ", " + b.getZ() + ").", NamedTextColor.RED)); return null; }
            }
        }
        Gimmick g = plugin.world().create(model, feet.getWorld(), feet.getX(), feet.getY(), feet.getZ(), facing, puzzle, node);
        plugin.tool().select(p, g);
        return g;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null || !e.getItem().hasItemMeta()) return;
        String model = e.getItem().getItemMeta().getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
        if (model == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("chacademia.gimmick.admin")) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Block feet = clicked.isPassable() && !clicked.getType().isAir() ? clicked : clicked.getRelative(e.getBlockFace());
        int facing = Gimmick.facingOf(p.getLocation().getYaw() + 180); // face the player
        Gimmick g = place(p, model, feet, facing, "", "");
        if (g == null) return;
        if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
        p.sendActionBar(Component.text(g.id + " 설치 (" + plugin.catalog().get(model).name() + ")", NamedTextColor.GREEN));
    }
}
