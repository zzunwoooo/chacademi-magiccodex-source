package kr.chacademy.motionplugin.broom;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /빗자루 (별칭 /broom)
 * <pre>
 *   /빗자루                     내 빗자루·숙련도 보기
 *   /빗자루 목록                 빗자루 10종과 수치
 *   /빗자루 장착 &lt;ID&gt;            가진 빗자루 장착 (장비창에서도 가능)
 *   /빗자루 선택 &lt;ID&gt; [플레이어] (관리자) 다른 플레이어의 장착 빗자루 변경 (보유 권한이 있어야 적용)
 *   /빗자루 해제 [플레이어]       (관리자) 장착 해제 — 가진 빗자루 중 가장 좋은 것, 없으면 연습용 빗자루(10초)
 * 보유 = 권한 chacademy.broom.own.&lt;ID&gt; (LuckPerms 등으로 지급, chacademy.broom.own.* = 전부)
 *   /빗자루 타기 [플레이어]       (관리자) 마법 없이 바로 타기/내리기 (테스트용)
 *   /빗자루 숙련도 &lt;플레이어&gt; &lt;0~100&gt; (관리자)
 *   /빗자루 새로고침             (관리자) brooms.yml 다시 읽기
 * </pre>
 */
public final class BroomCommand implements TabExecutor {
    private static final String ADMIN = "chacademy.broom.admin";
    private final BroomService service;

    public BroomCommand(BroomService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "정보" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "정보", "info" -> info(sender, args.length >= 2 && sender.hasPermission(ADMIN) ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null);
            case "목록", "list" -> list(sender);
            case "장착", "equip" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage("§e/빗자루 장착 <ID>");
                    return true;
                }
                var type = service.brooms().get(args[1].toLowerCase(Locale.ROOT));
                if (type == null || !service.owns(p, type)) {
                    sender.sendMessage("§c가지고 있지 않은 빗자루예요.");
                    return true;
                }
                service.equip(p, type);
                sender.sendMessage("§a장착: " + type.name());
            }
            case "선택", "select", "set" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("§e/빗자루 선택 <ID> [플레이어]  §7ID 는 /빗자루 목록 에서 확인");
                    return true;
                }
                var type = service.brooms().get(args[1].toLowerCase(Locale.ROOT));
                if (type == null) {
                    sender.sendMessage("§c없는 빗자루예요: " + args[1]);
                    return true;
                }
                Player target = target(sender, args, 2);
                if (target == null) return true;
                if (!service.owns(target, type)) {
                    service.store().setBroom(target.getUniqueId(), type.id());
                    sender.sendMessage("§e" + target.getName() + " 은(는) 이 빗자루 보유 권한이 없어요. 권한 chacademy.broom.own." + type.id()
                            + " 을 주면 그때부터 적용됩니다 (장착만 미리 해 둠).");
                    return true;
                }
                service.equip(target, type);
                sender.sendMessage("§a" + target.getName() + " 장착 빗자루: " + type.name() + " §7(" + type.id() + ")");
            }
            case "해제", "clear" -> {
                if (!admin(sender)) return true;
                Player target = target(sender, args, 1);
                if (target == null) return true;
                service.store().setBroom(target.getUniqueId(), "");
                service.sendList(target);
                var fallback = service.ownedBroom(target);
                sender.sendMessage("§a" + target.getName() + " 장착 해제. 이제 비행 마법: "
                        + (fallback == null ? "연습용 빗자루(10초)" : fallback.name() + " (가진 것 중 가장 좋은 빗자루)"));
            }
            case "타기", "ride" -> {
                if (!admin(sender)) return true;
                Player target = target(sender, args, 1);
                if (target == null) return true;
                if (service.isRiding(target)) {
                    service.stop(target);
                    sender.sendMessage("§a" + target.getName() + " 빗자루에서 내림");
                    return true;
                }
                if (!service.hasMod(target)) {
                    sender.sendMessage("§c" + target.getName() + " 은(는) 차카데미 모션 모드가 없어서 빗자루를 탈 수 없어요.");
                    return true;
                }
                var type = service.ownedBroom(target);
                if (type == null) type = service.brooms().get(service.practiceBroomId());
                if (type == null || !service.start(target, type, 0)) {
                    sender.sendMessage("§c지금은 탈 수 없어요 (탈것 탑승·관전 모드·금지 월드 등).");
                    return true;
                }
                sender.sendMessage("§a" + target.getName() + " 빗자루 탑승: " + type.name());
            }
            case "숙련도", "mastery" -> {
                if (!admin(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage("§e/빗자루 숙련도 <플레이어> <0~100>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없어요: " + args[1]);
                    return true;
                }
                double pct;
                try {
                    pct = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c0~100 사이 숫자를 적어 주세요.");
                    return true;
                }
                service.store().setSeconds(target.getUniqueId(), BroomMastery.secondsForPercent(pct, service.masteryFullSeconds()));
                float m = service.mastery(target.getUniqueId());
                sender.sendMessage("§a" + args[1] + " 비행 숙련도: " + Math.round(m * 100) + "% (" + BroomMastery.level(m) + ") §7- 다음 탑승부터 적용");
            }
            case "새로고침", "reload" -> {
                if (!admin(sender)) return true;
                service.reload();
                sender.sendMessage("§abrooms.yml 다시 읽음 (빗자루 " + service.brooms().size() + "종, 지금 타는 사람 " + service.ridingCount() + "명은 다음 탑승부터 적용)");
            }
            default -> help(sender);
        }
        return true;
    }

    private void info(CommandSender sender, Player p) {
        if (p == null) {
            help(sender);
            return;
        }
        var owned = service.ownedBroom(p);
        float m = service.mastery(p.getUniqueId());
        sender.sendMessage("§6[빗자루] §f" + p.getName());
        sender.sendMessage("§7 장착 빗자루: §f" + (owned == null ? "없음 (비행 마법 = 연습용 빗자루 10초)" : owned.name() + " §7(" + owned.id() + ")"));
        var all = service.ownedBrooms(p);
        sender.sendMessage("§7 보유: §f" + (all.isEmpty() ? "없음" : String.join(", ", all.stream().map(BroomType::name).toList())));
        sender.sendMessage("§7 비행 숙련도: §f" + Math.round(m * 100) + "% (" + BroomMastery.level(m) + ")");
        var riding = service.ridingType(p);
        if (riding != null) sender.sendMessage("§7 지금 타는 중: §f" + riding.name());
    }

    private void list(CommandSender sender) {
        sender.sendMessage("§6[빗자루 목록] §7속도 = 초당 블록, 회전·안정은 클수록 좋음");
        for (var t : service.brooms().values()) {
            sender.sendMessage(String.format("§f%s §7(%s) %s §7속도 §f%.0f §7가속 §f%.0f §7회전 §f%.1f §7안정 §f%.2f",
                    t.name(), t.id(), t.grade().isEmpty() ? "" : "§d" + t.grade(), t.maxSpeed() * 20, t.accel() * 1000, t.turn(), t.stability()));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§e/빗자루 §7- 내 빗자루·숙련도   §e/빗자루 목록   §e/빗자루 장착 <ID>");
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage("§e/빗자루 선택 <ID> [플레이어] §7| §e해제 [플레이어] §7| §e타기 [플레이어]");
            sender.sendMessage("§e/빗자루 숙련도 <플레이어> <0~100> §7| §e새로고침");
        }
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        sender.sendMessage("§c관리자만 쓸 수 있어요.");
        return false;
    }

    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) sender.sendMessage("§c접속 중인 플레이어가 아니에요: " + args[index]);
            return p;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage("§c플레이어 이름을 적어 주세요.");
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("정보", "목록", "장착"));
            if (admin) subs.addAll(List.of("선택", "해제", "타기", "숙련도", "새로고침"));
            return filter(subs, args[0]);
        }
        String sub = args[0];
        if (args.length == 2 && (sub.equals("장착") || sub.equals("equip")) && sender instanceof Player p) {
            return filter(service.ownedBrooms(p).stream().map(BroomType::id).toList(), args[1]);
        }
        if (!admin) return List.of();
        if (args.length == 2 && (sub.equals("선택") || sub.equals("select") || sub.equals("set"))) {
            return filter(new ArrayList<>(service.brooms().keySet()), args[1]);
        }
        boolean playerArg = (args.length == 2 && List.of("해제", "타기", "숙련도", "정보", "clear", "ride", "mastery", "info").contains(sub))
                || (args.length == 3 && List.of("선택", "select", "set").contains(sub));
        if (playerArg) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
