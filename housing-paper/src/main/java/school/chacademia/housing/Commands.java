package school.chacademia.housing;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.HousingRules;
import school.magiccodex.protocol.SchoolProtocol;

/** /하우징관리 · /방구경 · /내방 */
final class Commands implements CommandExecutor, TabCompleter {
    private static final String P = "§6[하우징] §f";
    private static final List<String> SUB = List.of("문", "가구", "크기", "열기", "리로드", "도움말");
    private final HousingPlugin plugin;
    Commands(HousingPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        switch (command.getName()) {
            case "내방" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("게임 안에서만 쓸 수 있어요."); return true; }
                plugin.rooms().open(p, p.getUniqueId(), true);
            }
            case "방구경" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("게임 안에서만 쓸 수 있어요."); return true; }
                if (!plugin.settings().visitCommand() && !p.hasPermission("chacademi.housing.admin")) { p.sendMessage(P + "다른 학생의 방은 기숙사실에서 친구 방 목록으로 구경할 수 있어요."); return true; }
                if (a.length != 1) { p.sendMessage(P + "/방구경 <닉네임>"); return true; }
                resolve(a[0], id -> {
                    if (id == null) { p.sendMessage(P + "§c'" + a[0] + "' 학생의 방을 찾을 수 없어요."); return; }
                    if (p.isOnline()) plugin.rooms().open(p, id, false);
                });
            }
            default -> admin(sender, a);
        }
        return true;
    }

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0 || a[0].equals("도움말")) { help(s); return; }
        switch (a[0]) {
            case "문" -> door(s, a);
            case "가구" -> furniture(s, a);
            case "크기" -> {
                if (a.length != 3) { s.sendMessage(P + "/하우징관리 크기 <유저> <크기 " + HousingRules.MIN_SIZE + "~" + HousingRules.MAX_SIZE + ">"); return; }
                int size = number(a[2], HousingRules.MIN_SIZE, HousingRules.MAX_SIZE);
                if (size < 0) { s.sendMessage(P + "§c크기는 " + HousingRules.MIN_SIZE + "~" + HousingRules.MAX_SIZE); return; }
                resolve(a[1], id -> {
                    if (id == null) { s.sendMessage(P + "§c유저를 찾을 수 없어요."); return; }
                    plugin.store().setSize(id, size, plugin.catalog()::get, plugin.catalog().everything(), ok -> {
                        s.sendMessage(P + (Boolean.TRUE.equals(ok) ? a[1] + " 의 방 크기 = " + size + "×" + size + " (밖으로 나간 가구는 보관함으로)" : "§c방이 없거나 저장하지 못했어요. (그 학생이 한 번 접속해야 방이 생겨요)"));
                        if (Boolean.TRUE.equals(ok)) plugin.rooms().refresh(id);
                    });
                });
            }
            case "열기" -> {
                if (!(s instanceof Player p)) { s.sendMessage("게임 안에서만 쓸 수 있어요."); return; }
                if (a.length != 2) { s.sendMessage(P + "/하우징관리 열기 <유저>  (구경 모드)"); return; }
                resolve(a[1], id -> { if (id == null) s.sendMessage(P + "§c유저를 찾을 수 없어요."); else if (p.isOnline()) plugin.rooms().open(p, id, false); });
            }
            case "리로드" -> { plugin.reload(); s.sendMessage(P + "설정·가구 목록·문을 다시 읽었어요. 가구 " + plugin.catalog().all().size() + "개, 문 " + plugin.doors().all().size() + "개."); }
            default -> help(s);
        }
    }

    private void door(CommandSender s, String[] a) {
        if (a.length >= 2 && a[1].equals("목록")) {
            var all = plugin.doors().all();
            s.sendMessage(P + "기숙사실 문 " + all.size() + "개");
            for (var d : all) s.sendMessage(" §7- " + d.describe());
            return;
        }
        if (!(s instanceof Player p)) { s.sendMessage("게임 안에서만 쓸 수 있어요."); return; }
        if (a.length < 2 || !List.of("등록", "삭제").contains(a[1])) { s.sendMessage(P + "/하우징관리 문 <등록 [기숙사|모두]|삭제|목록>  (바라보는 블록)"); return; }
        var block = p.getTargetBlockExact(6);
        if (block == null || block.getType().isAir()) { p.sendMessage(P + "§c6칸 안의 블록을 바라봐 주세요."); return; }
        if (a[1].equals("삭제")) { p.sendMessage(P + (plugin.doors().remove(block) ? "문 등록을 지웠어요." : "§c등록된 문이 아니에요.")); return; }
        int house = -1;
        if (a.length >= 3 && !a[2].equals("모두")) {
            house = SchoolProtocol.HOUSES.indexOf(a[2]);
            if (house < 0) { p.sendMessage(P + "§c기숙사는 " + String.join("·", SchoolProtocol.HOUSES) + " 또는 모두"); return; }
        }
        plugin.doors().add(block, house);
        p.sendMessage(P + "기숙사실 문으로 등록했어요: " + block.getType().name().toLowerCase(Locale.ROOT) + " · " + (house < 0 ? "모든 기숙사" : SchoolProtocol.HOUSES.get(house)));
    }

    private void furniture(CommandSender s, String[] a) {
        if (a.length >= 2 && !a[1].equals("목록")) { s.sendMessage(P + "가구는 권한으로 줘요: " + HousingRules.permission("<가구ID>") + " (전부: chacademi.housing.furniture.*)"); return; }
        s.sendMessage(P + "가구 " + plugin.catalog().all().size() + "개 (furniture.yml) · 권한 " + HousingRules.permission("<ID>"));
        for (var d : plugin.catalog().all()) s.sendMessage(" §7- §f" + d.id() + " §7" + d.name() + " · " + category(d) + " · 최대 " + d.max() + "개");
    }

    private static String category(school.magiccodex.protocol.HousingRules.Def d) {
        return switch (d.category()) {
            case HousingRules.CAT_FURNITURE -> "가구 " + d.w() + "x" + d.d();
            case HousingRules.CAT_PROP -> "소품 " + d.w() + "x" + d.d();
            case HousingRules.CAT_WALL -> "벽걸이 " + d.w() + "x" + d.wallCells();
            default -> d.sub() == HousingRules.SURFACE_WALLPAPER ? "벽지" : "바닥재";
        };
    }

    private void help(CommandSender s) {
        s.sendMessage(P + "하우징 관리");
        s.sendMessage(" §7/하우징관리 문 등록 [기숙사|모두] §f바라보는 블록을 기숙사실 문으로");
        s.sendMessage(" §7/하우징관리 문 삭제 · 문 목록");
        s.sendMessage(" §7/하우징관리 가구 목록 §f가구 ID·최대 개수 (가구는 권한 " + HousingRules.permission("<ID>") + " 으로 지급)");
        s.sendMessage(" §7/하우징관리 크기 <유저> <크기>");
        s.sendMessage(" §7/하우징관리 열기 <유저> §f(구경 모드)");
        s.sendMessage(" §7/하우징관리 리로드 §fconfig.yml · furniture.yml · doors.yml");
    }

    /** 닉네임·영문 계정·UUID → UUID (없으면 null). 접속 중이 아니어도 DB 에서 찾는다. */
    void resolve(String input, Consumer<UUID> done) {
        try { done.accept(UUID.fromString(input)); return; } catch (IllegalArgumentException notUuid) { /* 이름 */ }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) { done.accept(online.getUniqueId()); return; }
        for (Player p : Bukkit.getOnlinePlayers()) if (plugin.rooms().person(p).name().equalsIgnoreCase(input)) { done.accept(p.getUniqueId()); return; }
        plugin.store().find(input, done);
    }

    private static int number(String s, int min, int max) {
        try { int v = Integer.parseInt(s); return v < min || v > max ? -1 : v; } catch (NumberFormatException e) { return -1; }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] a) {
        if (command.getName().equals("방구경")) return a.length == 1 ? names(a[0]) : List.of();
        if (!command.getName().equals("하우징관리")) return List.of();
        List<String> out = switch (a.length) {
            case 1 -> SUB;
            case 2 -> switch (a[0]) { case "문" -> List.of("등록", "삭제", "목록"); case "가구" -> List.of("목록"); case "크기", "열기" -> names(a[1]); default -> List.of(); };
            case 3 -> a[0].equals("문") && a[1].equals("등록") ? concat(SchoolProtocol.HOUSES, "모두") : List.of();
            default -> List.of();
        };
        String last = a[a.length - 1];
        return out.stream().filter(v -> v.startsWith(last)).toList();
    }
    private List<String> names(String prefix) {
        var list = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) list.add(plugin.rooms().person(p).name());
        return list;
    }
    private static List<String> concat(List<String> a, String b) { var l = new ArrayList<>(a); l.add(b); return l; }
}
