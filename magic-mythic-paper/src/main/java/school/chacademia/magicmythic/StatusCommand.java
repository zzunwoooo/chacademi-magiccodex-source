package school.chacademia.magicmythic;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.StatusProtocol.Status;

/**
 * 테스트용: /마법미몹 상태이상 <종류|해제|목록> [초=5] [나|바라봄|플레이어=바라봄] [세기]
 * 예) /마법미몹 상태이상 기절 3 바라봄 · /마법미몹 상태이상 화상 5 나 · /마법미몹 상태이상 둔화 4 바라봄 0.5 · /마법미몹 상태이상 해제 바라봄
 * 보스 규칙·면역은 실제와 같다 (보스 기절 -> 둔화). 크리에이티브·관전자에게는 걸리지 않는다.
 */
final class StatusCommand {
    private StatusCommand() {}

    static String run(MagicMythicPlugin plugin, CommandSender sender, String[] a) {
        if (a.length == 0 || a[0].equals("목록") || a[0].equalsIgnoreCase("list")) {
            var sb = new StringBuilder("상태이상: ");
            for (Status s : Status.values()) sb.append(s.label).append('(').append(s.id).append(") ");
            return sb.toString();
        }
        boolean clear = a[0].equals("해제") || a[0].equalsIgnoreCase("clear");
        Status s = clear ? null : Status.of(a[0]);
        if (!clear && s == null) return "모르는 상태이상입니다. /마법미몹 상태이상 목록";
        int at = clear ? 1 : 2;
        double seconds = 5;
        if (!clear && a.length > 1) try { seconds = Double.parseDouble(a[1]); } catch (NumberFormatException e) { return "초는 숫자로 적어 주세요."; }
        String who = a.length > at ? a[at] : "바라봄";
        double power = 0;
        if (!clear && a.length > at + 1) try { power = Double.parseDouble(a[at + 1]); } catch (NumberFormatException e) { return "세기는 숫자로 적어 주세요."; }
        LivingEntity target = target(sender, who);
        if (target == null) return "대상을 찾지 못했습니다 (나 / 바라봄 / 접속 중인 플레이어 이름).";
        if (clear) { plugin.status().clear(target); return name(target) + " 의 상태이상을 모두 지웠습니다."; }
        LivingEntity source = sender instanceof Player p && !p.equals(target) ? p : null;
        boolean ok = plugin.status().apply(source, target, s, (int) Math.round(seconds * 20), power);
        return ok ? name(target) + " 에게 " + s.label + " " + seconds + "초" : name(target) + " 에게 " + s.label + " 을(를) 걸 수 없습니다 (면역·보스·크리에이티브·마나번 내부쿨).";
    }

    private static String name(LivingEntity e) { return e instanceof Player p ? p.getName() : e.getType().name().toLowerCase(Locale.ROOT); }

    private static LivingEntity target(CommandSender sender, String who) {
        if (who.equals("나") || who.equalsIgnoreCase("me") || who.equalsIgnoreCase("self")) return sender instanceof Player p ? p : null;
        if (who.equals("바라봄") || who.equalsIgnoreCase("look")) {
            if (!(sender instanceof Player p)) return null;
            var hit = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 32, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity && !e.equals(p));
            return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
        }
        return Bukkit.getPlayerExact(who);
    }
}
