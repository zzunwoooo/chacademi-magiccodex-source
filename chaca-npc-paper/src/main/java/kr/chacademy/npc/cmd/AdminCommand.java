package kr.chacademy.npc.cmd;

import kr.chacademy.npc.ChacaNpcPlugin;
import kr.chacademy.npc.config.PlaceRepository;
import kr.chacademy.npc.core.CharacterSheet;
import kr.chacademy.npc.data.Storage;
import kr.chacademy.npc.dialogue.DialogueSession;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /cnpc 관리자 명령어.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "spawn", "link", "unlink", "list", "place", "waypoint",
            "reload", "test", "end", "budget", "ai", "vibe", "cache");

    private final ChacaNpcPlugin plugin;

    public AdminCommand(ChacaNpcPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage("§6[ChacaNPC] §f" + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> spawn(sender, args);
            case "link" -> link(sender, args);
            case "unlink" -> unlink(sender);
            case "list" -> list(sender);
            case "place" -> place(sender, args);
            case "waypoint" -> waypoint(sender, args);
            case "reload" -> {
                plugin.reloadEverything();
                msg(sender, "다시 불러왔어요.");
            }
            case "test" -> test(sender, args);
            case "end" -> {
                if (sender instanceof Player p) {
                    plugin.dialogue().close(p, false);
                }
            }
            case "budget" -> budget(sender);
            case "ai" -> ai(sender, args);
            case "vibe" -> vibe(sender, args);
            case "cache" -> {
                plugin.dialogue().buttonCache().clear();
                msg(sender, "버튼 대답 캐시를 비웠어요.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "명령어 목록");
        s.sendMessage("§e/cnpc spawn <id> §7- 내 위치에 캐릭터 NPC 생성");
        s.sendMessage("§e/cnpc link <id> §7- 가장 가까운 Citizens NPC를 캐릭터와 연결");
        s.sendMessage("§e/cnpc unlink §7- 가장 가까운 NPC 연결 해제");
        s.sendMessage("§e/cnpc list §7- 캐릭터·연결 상태");
        s.sendMessage("§e/cnpc place <이름> [반경] [표시이름] §7- 내 위치를 장소로 등록");
        s.sendMessage("§e/cnpc waypoint add|clear <장소> §7- 장소로 가는 중간 지점");
        s.sendMessage("§e/cnpc test <id> <말> §7- 테스트 대화 (토큰·시간 표시)");
        s.sendMessage("§e/cnpc end §7- 내 대화 종료");
        s.sendMessage("§e/cnpc budget §7- 오늘 쓴 돈·남은 돈·응답 속도");
        s.sendMessage("§e/cnpc ai on|off §7- AI 전체 켜기/끄기 (끄면 고정 대사)");
        s.sendMessage("§e/cnpc vibe review|approve <번호|all>|remove <번호>|clear|run §7- 서버 분위기 노트");
        s.sendMessage("§e/cnpc cache §7- 버튼 대답 캐시 비우기");
        s.sendMessage("§e/cnpc reload §7- 설정·캐릭터·장소 다시 읽기");
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        msg(s, "게임 안에서 써 주세요.");
        return null;
    }

    private CharacterSheet character(CommandSender s, String id) {
        CharacterSheet c = plugin.characters().get(id);
        if (c == null) {
            msg(s, "캐릭터 '" + id + "'가 없어요. npcs 폴더와 /cnpc reload 를 확인하세요.");
        }
        return c;
    }

    private void spawn(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null || a.length < 2) {
            msg(s, "사용법: /cnpc spawn <id>");
            return;
        }
        CharacterSheet c = character(s, a[1]);
        if (c == null) {
            return;
        }
        NPC npc = plugin.npcs().spawn(c, p.getLocation());
        msg(s, c.name() + " NPC를 만들었어요 (Citizens id " + npc.getId() + ").");
        plugin.movement().reset();
    }

    private void link(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null || a.length < 2) {
            msg(s, "사용법: /cnpc link <id>  (연결할 NPC 옆에서)");
            return;
        }
        CharacterSheet c = character(s, a[1]);
        if (c == null) {
            return;
        }
        NPC npc = plugin.npcs().nearestAny(p, 5);
        if (npc == null) {
            msg(s, "5칸 안에 Citizens NPC가 없어요.");
            return;
        }
        int cleared = plugin.npcs().link(npc, c);
        msg(s, "Citizens NPC #" + npc.getId() + " (" + npc.getName() + ") ↔ " + c.id() + " 연결 완료."
                + (cleared > 0 ? " (같은 캐릭터에 연결돼 있던 다른 NPC " + cleared + "개의 연결은 해제했어요)" : ""));
        plugin.movement().reset();
    }

    private void unlink(CommandSender s) {
        Player p = player(s);
        if (p == null) {
            return;
        }
        NPC npc = plugin.npcs().nearestAny(p, 5);
        if (npc == null) {
            msg(s, "5칸 안에 Citizens NPC가 없어요.");
            return;
        }
        msg(s, plugin.npcs().unlink(npc) ? "연결을 해제했어요." : "연결된 NPC가 아니에요.");
    }

    private void list(CommandSender s) {
        msg(s, "캐릭터 " + plugin.characters().all().size() + "명");
        for (CharacterSheet c : plugin.characters().all()) {
            Integer id = plugin.npcs().npcIdFor(c.id());
            int slot = plugin.npcs().currentSlot(c);
            String where = slot >= 0 ? c.schedule().get(slot).place() : "-";
            s.sendMessage("§e" + c.id() + " §f" + c.name() + " §7(" + c.type() + ", "
                    + (id == null ? "§c미연결§7" : "NPC #" + id) + ", 지금 일과: " + where + ")");
        }
        s.sendMessage("§7대화 중: " + plugin.dialogue().sessions().size() + "명");
    }

    private void place(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null || a.length < 2) {
            msg(s, "사용법: /cnpc place <이름> [반경] [표시이름]");
            return;
        }
        double radius = 4;
        if (a.length >= 3) {
            try {
                radius = Double.parseDouble(a[2]);
            } catch (NumberFormatException ex) {
                msg(s, "반경은 숫자로 적어 주세요.");
                return;
            }
        }
        if (!plugin.places().setPlace(a[1], p.getLocation(), radius)) {
            msg(s, "장소 이름에는 마침표(.)나 빈칸을 쓸 수 없어요.");
            return;
        }
        if (a.length >= 4) {
            PlaceRepository.Place place = plugin.places().get(a[1]);
            if (place != null) {
                place.label = String.join(" ", java.util.Arrays.copyOfRange(a, 3, a.length));
                plugin.places().setPlace(a[1], place.location, place.radius);
            }
        }
        msg(s, "장소 '" + a[1] + "' 등록 (반경 " + radius + "칸).");
        plugin.movement().reset();
    }

    private void waypoint(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null || a.length < 3) {
            msg(s, "사용법: /cnpc waypoint add <장소>  |  /cnpc waypoint clear <장소>");
            return;
        }
        if (a[1].equalsIgnoreCase("add")) {
            boolean ok = plugin.places().addWaypoint(a[2], p.getLocation());
            PlaceRepository.Place place = plugin.places().get(a[2]);
            msg(s, ok ? "'" + a[2] + "'로 가는 중간 지점 " + place.waypoints.size() + "번 추가." : "그런 장소가 없어요. 먼저 /cnpc place");
        } else if (a[1].equalsIgnoreCase("clear")) {
            msg(s, plugin.places().clearWaypoints(a[2]) ? "중간 지점을 지웠어요." : "그런 장소가 없어요.");
        }
    }

    private void test(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null || a.length < 3) {
            msg(s, "사용법: /cnpc test <id> <말>");
            return;
        }
        CharacterSheet c = character(s, a[1]);
        if (c == null) {
            return;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
        DialogueSession cur = plugin.dialogue().session(p.getUniqueId());
        if (cur == null || !cur.character().id().equals(c.id()) || !cur.adminTest()) {
            if (cur != null) {
                plugin.dialogue().close(p, false);
            }
            plugin.dialogue().open(p, c, plugin.npcs().npcIdFor(c.id()), true);
        }
        plugin.dialogue().input(p, text, null);
    }

    private void budget(CommandSender s) {
        var b = plugin.budget();
        var st = plugin.settings();
        int day = st.dayIndex(st.today());
        String period = day < 0 ? "운영 시작 전 (테스트 한도 $" + st.testDailyUsd + "/일)"
                : day >= st.budgetDays ? "운영 기간 종료" : "운영 " + (day + 1) + "일차 / " + st.budgetDays + "일";
        msg(s, "예산 현황 — " + period);
        s.sendMessage(String.format("§7오늘: §f$%.3f §7/ 한도 $%.3f  §7상태: §f%s", b.spentToday(), b.allowance(), b.status()));
        s.sendMessage(String.format("§7전체: §f$%.3f §7/ $%.0f  §7(진행 중 예약 $%.4f)", b.spentTotal(), st.budgetTotalUsd, b.reserved()));
        s.sendMessage("§7MagicCodex 연결: " + (plugin.link().available() ? "§a연결됨" : "§c없음 (호감도 기본값, 퀘스트·선물 꺼짐)"));
        s.sendMessage("§7오늘 플레이어 대화 호출: §f" + b.totalCallsToday() + "회  §7(1인 한도 " + st.perPlayerDailyCalls + ")");
        s.sendMessage("§7응답 속도: §f" + b.latencySummary());
        s.sendMessage("§7AI 대기열: §f" + plugin.ai().queueSize() + " §7/ 처리 중 " + plugin.ai().runningCount()
                + "  §7AI: " + (b.isAiEnabled() ? "§a켜짐" : "§c꺼짐 (/cnpc ai on — 재시작해도 유지됨)"));
        s.sendMessage("§7최근 1시간 AI 실패: §f" + plugin.ai().failureSummary());
        s.sendMessage("§7AI 차단기: §f" + plugin.ai().circuitSummary());
        if (b.loadError() != null) {
            s.sendMessage("§c사용량을 불러오지 못해 AI 대화가 꺼져 있습니다 (고정 대사만): " + b.loadError()
                    + " — DB를 확인한 뒤 서버를 다시 시작하세요.");
        } else if (!b.isLoaded()) {
            s.sendMessage("§e사용량을 아직 불러오는 중입니다 (그동안은 고정 대사).");
        }
    }

    private void ai(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "AI는 지금 " + (plugin.budget().isAiEnabled() ? "켜져" : "꺼져") + " 있어요. /cnpc ai on|off");
            return;
        }
        boolean on = a[1].equalsIgnoreCase("on");
        if (!on && !a[1].equalsIgnoreCase("off")) {
            msg(s, "사용법: /cnpc ai on|off");
            return;
        }
        plugin.budget().setAiEnabled(on);
        msg(s, on ? "AI를 켰어요. (재시작해도 유지돼요)" : "AI를 껐어요. 모든 NPC가 고정 대사로 대답합니다. (재시작해도 유지돼요)");
        plugin.getLogger().info("[ChacaNPC] " + s.getName() + " 이(가) AI를 " + (on ? "켰습니다" : "껐습니다") + ".");
    }

    private void vibe(CommandSender s, String[] a) {
        String op = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "review";
        switch (op) {
            case "review" -> plugin.database().async(() -> new List[]{
                    plugin.storage().vibes("candidate"), plugin.storage().vibes("approved")}).thenAccept(lists -> sync(() -> {
                if (lists == null) {
                    return;
                }
                @SuppressWarnings("unchecked") List<Storage.VibeRow> cand = lists[0];
                @SuppressWarnings("unchecked") List<Storage.VibeRow> appr = lists[1];
                msg(s, "승인된 노트 " + appr.size() + "개 (NPC들이 알고 있음)");
                for (Storage.VibeRow r : appr) {
                    s.sendMessage("§a#" + r.id() + " §f" + r.note() + " §8(" + r.keyword() + ")");
                }
                msg(s, "후보 " + cand.size() + "개 — /cnpc vibe approve <번호|all>, /cnpc vibe remove <번호>");
                for (Storage.VibeRow r : cand) {
                    s.sendMessage("§e#" + r.id() + " §f" + r.note() + " §8(" + r.keyword() + ")");
                }
            }));
            case "approve" -> {
                if (a.length < 3) {
                    msg(s, "사용법: /cnpc vibe approve <번호|all>");
                    return;
                }
                if (a[2].equalsIgnoreCase("all")) {
                    plugin.database().run(() -> plugin.storage().setAllVibeStatus("candidate", "approved"))
                            .thenRun(() -> plugin.vibe().refreshCache());
                } else {
                    long id = parseId(s, a[2]);
                    if (id < 0) {
                        return;
                    }
                    plugin.database().run(() -> plugin.storage().setVibeStatus(id, "approved"))
                            .thenRun(() -> plugin.vibe().refreshCache());
                }
                msg(s, "승인했어요.");
            }
            case "remove" -> {
                if (a.length < 3) {
                    msg(s, "사용법: /cnpc vibe remove <번호>");
                    return;
                }
                long id = parseId(s, a[2]);
                if (id < 0) {
                    return;
                }
                plugin.database().run(() -> plugin.storage().setVibeStatus(id, "removed"))
                        .thenRun(() -> plugin.vibe().refreshCache());
                msg(s, "지웠어요.");
            }
            case "clear" -> {
                plugin.database().run(() -> {
                    plugin.storage().setAllVibeStatus("approved", "removed");
                    plugin.storage().setAllVibeStatus("candidate", "removed");
                }).thenRun(() -> plugin.vibe().refreshCache());
                msg(s, "분위기 노트를 모두 비웠어요.");
            }
            case "run" -> {
                msg(s, "분위기 노트를 지금 만듭니다...");
                plugin.vibe().run("manual-" + System.currentTimeMillis(), m -> sync(() -> msg(s, m)));
            }
            default -> msg(s, "/cnpc vibe review|approve|remove|clear|run");
        }
    }

    private long parseId(CommandSender s, String raw) {
        try {
            return Long.parseLong(raw.replace("#", ""));
        } catch (NumberFormatException ex) {
            msg(s, "번호를 숫자로 적어 주세요.");
            return -1;
        }
    }

    private void sync(Runnable r) {
        plugin.sync(r);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "spawn", "link", "test" -> {
                    for (CharacterSheet c : plugin.characters().all()) {
                        out.add(c.id());
                    }
                }
                case "waypoint" -> out.addAll(List.of("add", "clear"));
                case "ai" -> out.addAll(List.of("on", "off"));
                case "vibe" -> out.addAll(List.of("review", "approve", "remove", "clear", "run"));
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("waypoint")) {
            for (PlaceRepository.Place p : plugin.places().all()) {
                out.add(p.name);
            }
        }
        return out;
    }
}
