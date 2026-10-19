package school.chacademia.magicmythic;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.TelegraphProtocol;
import school.magiccodex.protocol.TelegraphProtocol.Shape;
import school.magiccodex.protocol.TelegraphProtocol.Show;

/**
 * 테스트용 (피해 없음, 보이기만): /마법미몹 예고 <원형|직선|부채꼴|전부|봉인진|봉인진이로치|빙핵레이저|빙핵레이저이로치> [초=1]
 *  원형 = 바라보는 블록(최대 24칸) 중심 반경 3, 직선 = 내 발에서 바라보는 방향 길이 8 폭 2, 부채꼴 = 반경 6 각도 70.
 *  빙핵레이저(이로치) = 빙핵의 수호자 스킬과 같은 직선 16x2 예고 1.2초 + FrostLaser VFX (내 발에서 바라보는 방향).
 *  봉인진(이로치) = 봉인의 술사 스킬과 같은 반경 4 예고 1.2초 + SealCircle VFX (PortableVFX에 sealcircle 팩이 있어야 보임).
 */
final class TelegraphCommand {
    private TelegraphCommand() {}

    static String run(MagicMythicPlugin plugin, CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) return "플레이어만 쓸 수 있습니다.";
        String kind = a.length == 0 ? "전부" : a[0];
        double seconds = 1;
        if (a.length > 1) try { seconds = Math.clamp(Double.parseDouble(a[1]), 0.1, 30); } catch (NumberFormatException e) { return "초는 숫자로 적어 주세요."; }
        int ticks = (int) Math.round(seconds * 20);
        var feet = p.getLocation();
        float yaw = feet.getYaw();
        var hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 24, FluidCollisionMode.NEVER, true);
        Location look = hit == null ? feet.clone().add(feet.getDirection().setY(0).normalize().multiply(6)) : hit.getHitPosition().toLocation(p.getWorld());
        var t = plugin.telegraphs();
        switch (kind) {
            case "원형", "circle" -> show(plugin, p, circle(t, look, 3, ticks));
            case "직선", "line" -> show(plugin, p, new Show(t.id(), Shape.LINE, feet.getX(), Telegraphs.ground(feet), feet.getZ(), yaw, 8, 2, 0, ticks, TelegraphProtocol.RED));
            case "부채꼴", "cone" -> show(plugin, p, new Show(t.id(), Shape.CONE, feet.getX(), Telegraphs.ground(feet), feet.getZ(), yaw, 6, 0, 70, ticks, TelegraphProtocol.RED));
            case "전부", "all" -> {
                var side = feet.getDirection().setY(0).normalize();
                var left = feet.clone().add(side.getZ() * 5, 0, -side.getX() * 5);
                show(plugin, p, circle(t, look, 3, ticks));
                show(plugin, p, new Show(t.id(), Shape.LINE, feet.getX(), Telegraphs.ground(feet), feet.getZ(), yaw + 35, 8, 2, 0, ticks, TelegraphProtocol.RED));
                show(plugin, p, new Show(t.id(), Shape.CONE, left.getX(), Telegraphs.ground(left), left.getZ(), yaw, 6, 0, 70, ticks, TelegraphProtocol.RED));
            }
            case "봉인진", "seal", "봉인진이로치", "seal_ruby" -> {
                int warn = a.length < 2 ? 24 : ticks; // 초를 안 적으면 실제 스킬과 같은 1.2초
                var s = circle(t, look, 4, warn);
                show(plugin, p, s);
                var at = new Location(p.getWorld(), s.x(), s.y(), s.z());
                String id = kind.endsWith("이로치") || kind.endsWith("ruby") ? "claude:sealcircle/seal_ruby" : "claude:sealcircle/seal";
                plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.reactions().vfx().effect(id, at, 1, 0, 40, 0xFFFFFF), Math.max(1, warn - 2));
            }
            case "빙핵레이저", "frost_laser", "빙핵레이저이로치", "frost_laser_obsidian" -> {
                int warn = a.length < 2 ? 24 : ticks; // 초를 안 적으면 실제 스킬과 같은 1.2초
                var s = new Show(t.id(), Shape.LINE, feet.getX(), Telegraphs.ground(feet), feet.getZ(), yaw, 16, 2, 0, warn, TelegraphProtocol.RED);
                show(plugin, p, s);
                var at = new Location(p.getWorld(), s.x(), s.y(), s.z());
                String id = kind.endsWith("이로치") || kind.endsWith("obsidian") ? "claude:frostlaser/laser_obsidian" : "claude:frostlaser/laser";
                plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.reactions().vfx().effect(id, at, 1, yaw, 40, 0xFFFFFF), Math.max(1, warn - 2));
            }
            default -> { return "/마법미몹 예고 <원형|직선|부채꼴|전부|봉인진|봉인진이로치|빙핵레이저|빙핵레이저이로치> [초]"; }
        }
        return "예고 장판 " + kind + " " + seconds + "초 (피해 없음)";
    }

    private static Show circle(Telegraphs t, Location at, float r, int ticks) {
        return new Show(t.id(), Shape.CIRCLE, at.getX(), Telegraphs.ground(at.clone().add(0, 0.1, 0)), at.getZ(), 0, r, 0, 0, ticks, TelegraphProtocol.RED);
    }

    private static void show(MagicMythicPlugin plugin, Player p, Show s) {
        plugin.telegraphs().start(s, p, p.getWorld(), false, x -> {});
    }
}
