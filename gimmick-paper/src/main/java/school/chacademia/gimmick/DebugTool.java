package school.chacademia.gimmick;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Admin wand. Click a model (or look at it and click air) to select and edit it.
 *  right click = + / next      left click = - / previous
 *  sneak + right click = next mode      sneak + left click = previous mode
 * Modes: 정보, 회전, 크기, 이동(바라보는 방향 축), 높이, 애니메이션, 삭제
 */
final class DebugTool implements Listener {
    enum Mode { INFO("정보"), ROTATE("회전"), SCALE("크기"), MOVE("이동"), HEIGHT("높이"), ANIM("애니메이션"), DELETE("삭제");
        final String label; Mode(String l) { label = l; } }

    private final GimmickPlugin plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Integer> animIndex = new HashMap<>();
    private final Map<UUID, Long> deleteArm = new HashMap<>();

    DebugTool(GimmickPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "debug_tool");
    }

    ItemStack create() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("tool.material", "BLAZE_ROD"));
        ItemStack item = new ItemStack(m == null ? Material.BLAZE_ROD : m);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("기믹 도구", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("우클릭 + / 좌클릭 -", NamedTextColor.GRAY),
            Component.text("웅크리고 클릭: 모드 바꾸기", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    boolean isTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    Gimmick selection(Player p) {
        String id = selected.get(p.getUniqueId());
        Gimmick g = id == null ? null : plugin.world().get(id);
        return g != null ? g : plugin.world().looking(p, 12);
    }
    void select(Player p, Gimmick g) { if (g != null) selected.put(p.getUniqueId(), g.id); }

    /** Clicks in the air / on blocks: pick what the player looks at, else the current selection. */
    @EventHandler(priority = EventPriority.HIGH)
    public void click(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isTool(e.getItem())) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("chacademia.gimmick.admin")) return;
        e.setCancelled(true);
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        Gimmick g = plugin.world().looking(p, 12);
        if (g == null) {
            String id = selected.get(p.getUniqueId());
            g = id == null ? null : plugin.world().get(id);
        }
        if (p.isSneaking()) { cycle(p, right); return; }
        if (g == null) { bar(p, "선택된 기믹이 없습니다. 모델을 바라보고 클릭하세요."); return; }
        apply(p, g, right);
    }

    /** Called by Inputs when a gimmick entity itself was clicked with the tool. */
    void use(Player p, Gimmick g, boolean right) {
        if (!p.hasPermission("chacademia.gimmick.admin")) return;
        if (p.isSneaking()) { cycle(p, right); return; }
        apply(p, g, right);
    }

    private void cycle(Player p, boolean forward) {
        Mode[] all = Mode.values();
        Mode m = modes.getOrDefault(p.getUniqueId(), Mode.INFO);
        Mode next = all[(m.ordinal() + (forward ? 1 : all.length - 1)) % all.length];
        modes.put(p.getUniqueId(), next);
        deleteArm.remove(p.getUniqueId());
        bar(p, "모드: " + next.label);
    }

    private void apply(Player p, Gimmick g, boolean right) {
        boolean changedSelection = !g.id.equals(selected.get(p.getUniqueId()));
        select(p, g);
        Mode m = modes.getOrDefault(p.getUniqueId(), Mode.INFO);
        var cfg = plugin.getConfig();
        int sign = right ? 1 : -1;
        switch (m) {
            case INFO -> { info(p, g); return; }
            case ROTATE -> {
                g.yaw = (float) normalize(g.yaw + sign * cfg.getDouble("tool.rotate-degrees", 15));
                if (g.attached()) { var l = g.location(); if (l != null) { var host = Bukkit.getEntity(g.host); if (host != null) host.setRotation(g.yaw, 0); } }
            }
            case SCALE -> g.scale = Math.clamp(Math.round((g.scale + sign * cfg.getDouble("tool.scale-step", 0.1)) * 1000) / 1000.0, 0.1, 8);
            case MOVE -> {
                if (g.attached()) { bar(p, "몹에 씌운 모델은 이동할 수 없습니다."); return; }
                Vector axis = axis(p.getLocation().getDirection()).multiply(sign * cfg.getDouble("tool.move-step", 0.25));
                g.x += axis.getX(); g.z += axis.getZ();
            }
            case HEIGHT -> {
                if (g.attached()) { bar(p, "몹에 씌운 모델은 이동할 수 없습니다."); return; }
                g.y += sign * cfg.getDouble("tool.height-step", 0.125);
            }
            case ANIM -> { cycleAnim(p, g, sign); return; }
            case DELETE -> {
                long now = plugin.puzzles().now();
                Long armed = deleteArm.get(p.getUniqueId());
                if (!right) { deleteArm.remove(p.getUniqueId()); bar(p, "삭제 취소"); return; }
                if (armed == null || armed < now || changedSelection) {
                    deleteArm.put(p.getUniqueId(), now + 60);
                    bar(p, g.id + " 삭제하려면 3초 안에 한 번 더 우클릭");
                    return;
                }
                deleteArm.remove(p.getUniqueId());
                plugin.world().remove(g);
                selected.remove(p.getUniqueId());
                bar(p, g.id + " 삭제됨");
                return;
            }
        }
        plugin.world().transform(g);
        bar(p, String.format(Locale.ROOT, "%s  %s  방향 %.0f°  크기 %.2f  위치 %.2f, %.2f, %.2f", m.label, g.id, g.yaw, g.scale, g.x, g.y, g.z));
    }

    private void cycleAnim(Player p, Gimmick g, int sign) {
        var anims = new ArrayList<>(plugin.catalog().get(g.model).anims().keySet());
        if (anims.isEmpty()) { bar(p, "애니메이션 목록이 없는 모델입니다 (models.yml)"); return; }
        int i = Math.floorMod(animIndex.getOrDefault(p.getUniqueId(), -1) + sign, anims.size());
        animIndex.put(p.getUniqueId(), i);
        plugin.world().play(g, anims.get(i), null);
        bar(p, "재생: " + anims.get(i) + " (" + (i + 1) + "/" + anims.size() + ")");
    }

    void info(Player p, Gimmick g) {
        var info = plugin.catalog().get(g.model);
        String state = g.inPuzzle() ? (plugin.puzzles().isOn(g.puzzle, g.node) ? "켜짐" : "꺼짐") : "-";
        p.sendMessage(Component.text("[기믹] " + g.id + " " + info.name() + " (" + g.model + ")", NamedTextColor.GOLD));
        p.sendMessage(Component.text(String.format(Locale.ROOT, " 위치 %s %.2f, %.2f, %.2f  방향 %.1f°  크기 %.2f%s",
            g.attached() ? "(몹에 씌움)" : g.world, g.x, g.y, g.z, g.yaw, g.scale, ""), NamedTextColor.GRAY));
        p.sendMessage(Component.text(" 퍼즐 " + (g.puzzle.isEmpty() ? "-" : g.puzzle) + " / 노드 " + (g.node.isEmpty() ? "-" : g.node)
            + "  상태 " + state + "  재생 중 " + (g.lastAnim.isEmpty() ? "-" : g.lastAnim), NamedTextColor.GRAY));
    }

    private static Vector axis(Vector look) {
        if (Math.abs(look.getX()) > Math.abs(look.getZ())) return new Vector(Math.signum(look.getX()), 0, 0);
        return new Vector(0, 0, Math.signum(look.getZ()));
    }
    private static double normalize(double yaw) { yaw %= 360; if (yaw > 180) yaw -= 360; if (yaw <= -180) yaw += 360; return yaw; }
    private static void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.YELLOW)); }

    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        modes.remove(id); selected.remove(id); animIndex.remove(id); deleteArm.remove(id);
    }
}
