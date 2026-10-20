package school.chacademia.gimmick;

import java.util.*;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;

/** /기믹 ... (alias /gimmick). Korean sub-commands with English aliases. */
final class GimmickCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("도움", "소환", "씌우기", "벗기기", "도구", "선택", "정보", "노드", "설정", "재생",
        "제거", "목록", "모델", "근처", "신호", "초기화", "퍼즐", "리로드", "아이템");
    private static final Map<String, String> ALIAS = Map.ofEntries(Map.entry("help", "도움"), Map.entry("spawn", "소환"), Map.entry("attach", "씌우기"),
        Map.entry("detach", "벗기기"), Map.entry("tool", "도구"), Map.entry("select", "선택"), Map.entry("info", "정보"), Map.entry("node", "노드"),
        Map.entry("set", "설정"), Map.entry("play", "재생"), Map.entry("remove", "제거"), Map.entry("list", "목록"), Map.entry("models", "모델"),
        Map.entry("near", "근처"), Map.entry("signal", "신호"), Map.entry("reset", "초기화"), Map.entry("puzzle", "퍼즐"), Map.entry("reload", "리로드"), Map.entry("item", "아이템"));

    private final GimmickPlugin plugin;
    GimmickCommand(GimmickPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("chacademia.gimmick.admin")) { s.sendMessage("권한이 없습니다."); return true; }
        String sub = a.length == 0 ? "도움" : ALIAS.getOrDefault(a[0].toLowerCase(Locale.ROOT), a[0]);
        Player p = s instanceof Player pl ? pl : null;
        switch (sub) {
            case "소환" -> {
                if (p == null || a.length < 2) return usage(s, "/기믹 소환 <모델> [퍼즐] [노드]");
                String model = a[1];
                if (!plugin.catalog().known(model)) s.sendMessage("알림: models.yml 에 없는 모델입니다. 렌더러에 등록돼 있으면 그대로 소환됩니다.");
                // 2026-10-20: the block the admin stands in, model centred on it, facing where the admin looks (90° steps)
                var feet = p.getLocation().getBlock();
                Gimmick g = plugin.placer().place(p, model, feet, Gimmick.facingOf(p.getLocation().getYaw()), a.length > 2 ? a[2] : "", a.length > 3 ? a[3] : "");
                if (g == null) return true;
                ok(s, g.id + " 설치 (" + model + ")  블록 " + g.bx + ", " + g.by + ", " + g.bz + (g.live() ? "" : " - 모델을 그리지 못했습니다. 콘솔을 확인하세요."));
            }
            case "아이템" -> {
                if (p == null || a.length < 2) return usage(s, "/기믹 아이템 <모델> [개수]   (블록 면을 우클릭하면 그 칸에 설치, 나를 바라봄)");
                if (!plugin.catalog().known(a[1])) return fail(s, "models.yml 에 없는 모델입니다.");
                var item = plugin.placer().item(a[1]);
                item.setAmount((int) Math.clamp(a.length > 2 ? parse(a[2], 1) : 1, 1, 64));
                p.getInventory().addItem(item);
                ok(s, plugin.catalog().get(a[1]).name() + " 설치 아이템 지급");
            }
            case "씌우기" -> {
                if (p == null || a.length < 2) return usage(s, "/기믹 씌우기 <모델> [크기]  (바라보는 몹에)");
                LivingEntity mob = lookingMob(p);
                if (mob == null||GimmickProtection.excluded(mob)||!plugin.protection().pair(p,mob.getLocation())) return fail(s, "바라보는 몹이 없습니다.");
                double scale = a.length > 2 ? parse(a[2], 1) : 1;
                Gimmick g = plugin.world().attach(mob, a[1], scale);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "씌우지 못했습니다. 콘솔을 확인하세요.");
                plugin.tool().select(p, g);
                ok(s, mob.getType().name() + " 에 " + a[1] + " 씌움 (" + g.id + ")");
            }
            case "벗기기" -> {
                if (p == null) return true;
                LivingEntity mob = lookingMob(p);
                Gimmick g = mob == null ? null : plugin.world().byEntity(mob);
                if (g == null || !g.attached()) return fail(s, "모델을 씌운 몹을 바라보세요.");
                plugin.world().remove(g);
                ok(s, "모델을 벗겼습니다.");
            }
            case "도구" -> {
                if (p == null) return true;
                p.getInventory().addItem(plugin.tool().create());
                ok(s, "기믹 도구 지급. 우클릭 +, 좌클릭 -, 웅크리고 클릭하면 모드 변경 (정보·회전 90°·크기·이동 1칸·높이 1칸·애니메이션·삭제)");
            }
            case "선택" -> {
                if (p == null) return true;
                Gimmick g = a.length > 1 ? plugin.world().get(a[1]) : plugin.world().looking(p, 16);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "기믹을 바라보거나 id 를 적으세요.");
                plugin.tool().select(p, g);
                plugin.tool().info(p, g);
            }
            case "정보" -> {
                if (p == null) return true;
                Gimmick g = target(p, a, 1);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "선택된 기믹이 없습니다.");
                plugin.tool().info(p, g);
            }
            case "노드" -> {
                if (p == null || a.length < 3) return usage(s, "/기믹 노드 <퍼즐> <노드>   (선택한 기믹을 퍼즐에 연결, '없음' 으로 해제)");
                Gimmick g = target(p, a, 99);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "선택된 기믹이 없습니다.");
                if (a[1].equals("없음")) plugin.world().setPuzzle(g, "", "");
                else {
                    if (plugin.puzzles().node(a[1], a[2]) == null) s.sendMessage("알림: puzzles.yml 에 아직 없는 퍼즐/노드입니다.");
                    plugin.world().setPuzzle(g, a[1], a[2]);
                }
                ok(s, g.id + " → " + (g.puzzle.isEmpty() ? "연결 해제" : g.puzzle + "." + g.node));
            }
            case "설정" -> {
                if (p == null || a.length < 3) return usage(s, "/기믹 설정 <방향|크기|높이|위치> <값>   (위치는 x y z)");
                Gimmick g = target(p, a, 99);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "선택된 기믹이 없습니다.");
                int[] old = {g.bx, g.by, g.bz, g.facing};
                switch (a[1]) {
                    case "방향", "yaw" -> { Integer v = facingArg(a[2]); if (v == null) return usage(s, "방향: 남/서/북/동 또는 0/90/180/270"); if (g.attached()) g.yaw = v * 90f; else g.facing = v; }
                    case "크기", "scale" -> g.scale = Math.clamp(parse(a[2], g.scale), 0.1, 8);
                    case "높이", "y" -> { if (!g.attached()) g.by = (int) Math.floor(parse(a[2], g.by)); }
                    case "위치", "pos" -> {
                        if (g.attached() || a.length < 5) return usage(s, "/기믹 설정 위치 <x> <y> <z>   (블록 좌표)");
                        g.bx = (int) Math.floor(parse(a[2], g.bx)); g.by = (int) Math.floor(parse(a[3], g.by)); g.bz = (int) Math.floor(parse(a[4], g.bz));
                    }
                    case "상태", "state" -> {
                        if (a.length < 3) return usage(s, "/기믹 설정 상태 <0~3>   (거울·다이얼 위치 / 수위 기둥 수위) 또는 색 이름 (프리즘)");
                        var info = plugin.catalog().get(g.model);
                        if (info.is("prism")) plugin.devices().color(g, a[2]);
                        else if (info.is("pillar")) plugin.devices().level(g, (int) parse(a[2], g.level) - g.level);
                        else plugin.devices().position(g, (int) parse(a[2], g.pos) - g.pos);
                    }
                    default -> { return usage(s, "/기믹 설정 <방향|크기|높이|위치|상태> <값>"); }
                }
                if (!g.attached() && p != null && plugin.tool().blocked(p, g, plugin.catalog().get(g.model))) {
                    g.bx = old[0]; g.by = old[1]; g.bz = old[2]; g.facing = old[3];
                    return fail(s, "다른 기믹과 겹쳐서 바꾸지 않았습니다.");
                }
                plugin.world().transform(g);
                ok(s, g.attached() ? String.format(Locale.ROOT, "%s 방향 %.0f° 크기 %.2f", g.id, g.yaw, g.scale)
                    : String.format(Locale.ROOT, "%s 블록 %d %d %d  방향 %s  크기 %.2f", g.id, g.bx, g.by, g.bz, DebugTool.FACING[g.facing], g.scale));
            }
            case "재생" -> {
                if (p == null || a.length < 2) return usage(s, "/기믹 재생 <애니메이션>   (선택한 기믹)");
                Gimmick g = target(p, a, 99);
                if (g == null || !g.live()) return fail(s, "선택된 기믹이 없습니다.");
                plugin.world().play(g, a[1], null);
                ok(s, g.id + " 재생: " + a[1]);
            }
            case "제거" -> {
                if (p == null) return true;
                Gimmick g = a.length > 1 ? plugin.world().get(a[1]) : plugin.world().looking(p, 16);
                if (g == null||p!=null&&!plugin.protection().pair(p,g.location())) return fail(s, "기믹을 바라보거나 id 를 적으세요.");
                plugin.world().remove(g);
                ok(s, g.id + " 제거");
            }
            case "목록" -> {
                String filter = a.length > 1 ? a[1] : null;
                var list = plugin.world().all().stream().filter(g -> filter == null || g.puzzle.equals(filter) || g.model.equals(filter)).toList();
                s.sendMessage("기믹 " + list.size() + "개" + (filter == null ? "" : " (" + filter + ")"));
                for (Gimmick g : list.subList(0, Math.min(40, list.size())))
                    s.sendMessage(String.format(Locale.ROOT, " %s %s %s %.0f %.0f %.0f %s", g.id, g.model, g.attached() ? "(몹)" : g.world, g.x, g.y, g.z,
                        g.inPuzzle() ? g.puzzle + "." + g.node : ""));
            }
            case "모델" -> {
                String q = a.length > 1 ? a[1] : "";
                var ids = plugin.catalog().ids().stream().filter(i -> i.contains(q) || plugin.catalog().get(i).name().contains(q)).toList();
                s.sendMessage("모델 " + ids.size() + "개: " + ids.stream().map(i -> i + "(" + plugin.catalog().get(i).name() + ")").collect(Collectors.joining(", ")));
            }
            case "근처" -> {
                if (p == null) return true;
                double r = a.length > 1 ? parse(a[1], 16) : 16;
                var list = plugin.world().near(p.getLocation(), r);
                s.sendMessage("반경 " + (int) r + " 기믹 " + list.size() + "개");
                for (Gimmick g : list) s.sendMessage(" " + g.id + " " + g.model + " " + (g.inPuzzle() ? g.puzzle + "." + g.node : "") + " " + g.lastAnim);
            }
            case "신호" -> {
                if (a.length < 3) return usage(s, "/기믹 신호 <퍼즐> <노드> [켜기|끄기|전환]");
                Boolean on = a.length < 4 || a[3].equals("전환") || a[3].equals("toggle") ? null : (a[3].equals("켜기") || a[3].equals("on"));
                if (!plugin.puzzles().force(a[1], a[2], on, s.getName())) return fail(s, "변화 없음 (퍼즐/노드 이름 또는 latch 확인)");
                ok(s, a[1] + "." + a[2] + " → " + (plugin.puzzles().isOn(a[1], a[2]) ? "켜짐" : "꺼짐"));
            }
            case "초기화" -> {
                if (a.length < 2) return usage(s, "/기믹 초기화 <퍼즐>");
                if (!plugin.puzzles().reset(a[1])) return fail(s, "없는 퍼즐입니다.");
                ok(s, a[1] + " 초기화");
            }
            case "퍼즐" -> {
                if (a.length < 2) { s.sendMessage("퍼즐: " + String.join(", ", plugin.puzzles().ids())); return true; }
                var e = plugin.puzzles().engine(a[1]);
                if (e == null) return fail(s, "없는 퍼즐입니다.");
                for (String n : e.def().nodes().keySet()) {
                    int count = plugin.world().inNode(a[1], n).size();
                    var col = e.collected(n);
                    s.sendMessage(" " + n + ": " + (e.on(n) ? "켜짐" : "꺼짐") + "  기믹 " + count + "개" + (col.isEmpty() ? "" : "  넣은 것 " + col));
                }
            }
            case "리로드" -> {
                var warnings = plugin.reloadAll();
                ok(s, "다시 불러옴. 경고 " + warnings.size() + "건");
                warnings.stream().limit(15).forEach(w -> s.sendMessage(" - " + w));
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Component.text("[기믹] 명령어", NamedTextColor.GOLD));
        for (String line : List.of(
            "/기믹 모델 [검색]  - 모델 목록", "/기믹 소환 <모델> [퍼즐] [노드]  - 내가 서 있는 칸 중앙에 설치 (내가 보는 방향)",
            "/기믹 아이템 <모델> [개수]  - 설치 아이템 (블록 면 우클릭 = 그 칸에 설치, 나를 바라봄)",
            "/기믹 씌우기 <모델> [크기] / 벗기기  - 바라보는 몹에 모델 씌우기", "/기믹 도구  - 클릭으로 회전·크기·이동·높이·애니메이션·삭제",
            "/기믹 선택 [id] · 정보 · 제거 [id] · 목록 [퍼즐|모델] · 근처 [반경]", "/기믹 노드 <퍼즐> <노드>  - 선택한 기믹을 퍼즐 노드에 연결",
            "/기믹 설정 <방향|크기|높이|위치|상태> <값> · 재생 <애니메이션>", "/기믹 퍼즐 [이름] · 신호 <퍼즐> <노드> [켜기|끄기|전환] · 초기화 <퍼즐>",
            "/기믹 리로드  - config·models·puzzles 다시 읽기"))
            s.sendMessage(Component.text(" " + line, NamedTextColor.GRAY));
    }

    private Gimmick target(Player p, String[] a, int idArg) {
        if (idArg < a.length) { Gimmick g = plugin.world().get(a[idArg]); if (g != null) return g; }
        return plugin.tool().selection(p);
    }

    private static Integer facingArg(String v) { return school.chacademia.gimmick.puzzle.PuzzleDef.facingOf(v); }

    private static LivingEntity lookingMob(Player p) {
        var eye=p.getEyeLocation();var block=p.getWorld().rayTraceBlocks(eye,eye.getDirection(),12,FluidCollisionMode.NEVER,true);
        double range=block==null?12:Math.min(12,block.getHitPosition().distance(eye.toVector()));
        var hit = p.getWorld().rayTraceEntities(eye,eye.getDirection(),range,0.3,
            e -> e instanceof LivingEntity && !(e instanceof Player) && !e.getScoreboardTags().contains(GimmickWorld.TAG));
        return hit == null ? null : (LivingEntity) hit.getHitEntity();
    }

    private static double parse(String s, double def) { try { double value=Double.parseDouble(s);return Double.isFinite(value)?value:def; } catch (NumberFormatException e) { return def; } }
    private static boolean usage(CommandSender s, String text) { s.sendMessage(Component.text(text, NamedTextColor.YELLOW)); return true; }
    private static boolean fail(CommandSender s, String text) { s.sendMessage(Component.text(text, NamedTextColor.RED)); return true; }
    private static void ok(CommandSender s, String text) { s.sendMessage(Component.text(text, NamedTextColor.GREEN)); }

    @Override public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("chacademia.gimmick.admin")) return List.of();
        if (a.length == 1) return match(SUBS, a[0]);
        String sub = ALIAS.getOrDefault(a[0].toLowerCase(Locale.ROOT), a[0]);
        var puzzles = plugin.puzzles().ids();
        return switch (sub) {
            case "소환" -> a.length == 2 ? match(plugin.catalog().ids(), a[1]) : a.length == 3 ? match(puzzles, a[2]) : a.length == 4 ? match(plugin.puzzles().nodes(a[2]), a[3]) : List.of();
            case "씌우기" -> a.length == 2 ? match(plugin.catalog().ids(), a[1]) : List.of();
            case "노드" -> a.length == 2 ? match(concat(puzzles, List.of("없음")), a[1]) : a.length == 3 ? match(plugin.puzzles().nodes(a[1]), a[2]) : List.of();
            case "신호" -> a.length == 2 ? match(puzzles, a[1]) : a.length == 3 ? match(plugin.puzzles().nodes(a[1]), a[2]) : a.length == 4 ? match(List.of("켜기", "끄기", "전환"), a[3]) : List.of();
            case "초기화", "퍼즐" -> a.length == 2 ? match(puzzles, a[1]) : List.of();
            case "설정" -> a.length == 2 ? match(List.of("방향", "크기", "높이", "위치", "상태"), a[1]) : a.length == 3 && a[1].equals("방향") ? match(List.of("남", "서", "북", "동"), a[2]) : List.of();
            case "아이템" -> a.length == 2 ? match(plugin.catalog().ids(), a[1]) : List.of();
            case "재생" -> {
                Gimmick g = s instanceof Player p ? plugin.tool().selection(p) : null;
                yield a.length == 2 && g != null ? match(plugin.catalog().get(g.model).anims().keySet(), a[1]) : List.of();
            }
            case "선택", "제거" -> a.length == 2 ? match(plugin.world().all().stream().map(g -> g.id).toList(), a[1]) : List.of();
            case "목록" -> a.length == 2 ? match(puzzles, a[1]) : List.of();
            default -> List.of();
        };
    }
    private static List<String> concat(Collection<String> a, Collection<String> b) { var l = new ArrayList<>(a); l.addAll(b); return l; }
    private static List<String> match(Collection<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix)).limit(80).toList();
    }
}
