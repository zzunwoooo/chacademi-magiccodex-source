package school.chacademia.gimmick;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/**
 * /던전관리 … (명령어 허브 갈래). 예전 /기믹.
 * <pre>
 * 소환 &lt;모델&gt; [퍼즐] [노드] · 아이템 &lt;모델&gt; [개수] · 씌우기 &lt;모델&gt; [크기] · 벗기기 · 도구 · 선택 [번호] · 정보 [번호] · 근처 [반경]
 * 목록 [검색] · 모델 [검색] · 설정 &lt;방향|크기|높이|위치|상태&gt; … · 애니메이션 &lt;이름&gt; · 노드 &lt;퍼즐|없음&gt; &lt;노드&gt; · 제거 [번호]
 * 퍼즐 [이름] · 퍼즐신호 &lt;퍼즐&gt; &lt;노드&gt; [켜기|끄기|전환] · 퍼즐초기화 &lt;퍼즐&gt;
 * </pre>
 */
final class GimmickCommand {
    private static final Map<String, Integer> FACINGS = new LinkedHashMap<>();
    static { FACINGS.put("남", 0); FACINGS.put("서", 1); FACINGS.put("북", 2); FACINGS.put("동", 3); }
    private static final List<String> STATES = List.of("0", "1", "2", "3", "흰색", "빨강", "노랑", "초록", "파랑", "보라");

    private final GimmickPlugin plugin;
    GimmickCommand(GimmickPlugin plugin) { this.plugin = plugin; }

    void register() {
        var root = ChacaLink.던전관리;
        ChacaLink.branch(plugin, root, spawn(), "내가 서 있는 칸에 기믹 설치 (보는 방향)", "소환 ");
        ChacaLink.branch(plugin, root, item(), "설치 아이템 받기 (블록 면 우클릭 = 그 칸에 설치, 나를 바라봄)", "아이템 ");
        ChacaLink.branch(plugin, root, attach(), "바라보는 몹에 모델 씌우기", "씌우기 ");
        ChacaLink.branch(plugin, root, Commands.literal("벗기기").executes(this::detach), "바라보는 몹의 모델 벗기기", "벗기기");
        ChacaLink.branch(plugin, root, Commands.literal("도구").executes(this::tool), "디버그 도구 (회전·크기·이동·높이·애니메이션·삭제)", "도구");
        ChacaLink.branch(plugin, root, pick("선택", false), "기믹 선택 (바라보는 것 또는 번호)", "선택");
        ChacaLink.branch(plugin, root, pick("정보", true), "선택한 기믹 정보", "정보");
        ChacaLink.branch(plugin, root, near(), "주변 기믹 목록", "근처");
        ChacaLink.branch(plugin, root, list(), "설치된 기믹 목록 (퍼즐·모델 검색)", "목록");
        ChacaLink.branch(plugin, root, models(), "모델 목록", "모델");
        ChacaLink.branch(plugin, root, settings(), "선택한 기믹의 방향·크기·높이·위치·상태", "설정 방향 남");
        ChacaLink.branch(plugin, root, animation(), "선택한 기믹 애니메이션 재생", "애니메이션 ");
        ChacaLink.branch(plugin, root, node(), "선택한 기믹을 퍼즐 노드에 연결 (없음 = 해제)", "노드 ");
        ChacaLink.branch(plugin, root, remove(), "기믹 제거 (바라보는 것 또는 번호)", "제거");
        ChacaLink.branch(plugin, root, puzzle(), "퍼즐 목록·노드 상태", "퍼즐");
        ChacaLink.branch(plugin, root, signal(), "퍼즐 노드 신호 켜기·끄기·전환", "퍼즐신호 ");
        ChacaLink.branch(plugin, root, Commands.literal("퍼즐초기화").then(Commands.argument("퍼즐", puzzleArg())
            .executes(ctx -> {
                String id = ctx.getArgument("퍼즐", String.class);
                if (!plugin.puzzles().reset(id)) throw ChacaLink.error("없는 퍼즐이거나 보호 구역 안의 퍼즐이에요: " + id);
                ChacaLink.ok(ctx.getSource().getSender(), id + " 퍼즐을 처음 상태로 되돌렸어요");
                return 1;
            })), "퍼즐을 처음 상태로", "퍼즐초기화 ");
        ChacaLink.reload(plugin, "던전", "config·models·puzzles(.yml) 다시 읽기 (기믹 모델 다시 그림)", () -> {
            var warnings = plugin.reloadAll();
            if (!warnings.isEmpty() && warnings.get(0).startsWith("Reload rejected")) throw new IllegalStateException(warnings.get(0));
            for (String w : warnings) plugin.getLogger().warning("퍼즐 설정: " + w);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static Player self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (ctx.getSource().getSender() instanceof Player p) return p;
        throw ChacaLink.error("게임 안에서만 쓸 수 있어요");
    }

    private com.mojang.brigadier.arguments.ArgumentType<String> modelArg() { return ChacaLink.word(() -> plugin.catalog().ids()); }
    private com.mojang.brigadier.arguments.ArgumentType<String> puzzleArg() { return ChacaLink.word(() -> plugin.puzzles().ids()); }
    private com.mojang.brigadier.arguments.ArgumentType<String> idArg() { return ChacaLink.word(() -> plugin.world().all().stream().map(g -> g.id).toList()); }

    /** 퍼즐 칸 다음의 노드 칸: 앞 칸의 퍼즐에 있는 노드를 후보로. */
    private RequiredArgumentBuilder<CommandSourceStack, String> nodeArg() {
        return Commands.argument("노드", ChacaLink.word(List::of)).suggests((ctx, b) -> {
            String puzzle;
            try { puzzle = ctx.getArgument("퍼즐", String.class); } catch (IllegalArgumentException e) { return b.buildFuture(); }
            String typed = b.getRemaining();
            for (String n : plugin.puzzles().nodes(puzzle)) if (n.startsWith(typed)) b.suggest(n);
            return b.buildFuture();
        });
    }

    private static <T> T opt(CommandContext<CommandSourceStack> ctx, String name, Class<T> type, T fallback) {
        try { return ctx.getArgument(name, type); } catch (IllegalArgumentException missing) { return fallback; }
    }

    private Gimmick usable(Player p, Gimmick g, String missing) throws CommandSyntaxException {
        if (g == null) throw ChacaLink.error(missing);
        if (!plugin.protection().pair(p, g.location())) throw ChacaLink.error("보호 구역 안의 기믹이에요");
        return g;
    }

    /** 번호를 적으면 그 기믹, 아니면 선택 → 바라보는 기믹. */
    private Gimmick target(Player p, String id) {
        if (id != null) { Gimmick g = plugin.world().get(id); if (g != null) return g; }
        return plugin.tool().selection(p);
    }

    private static LivingEntity lookingMob(Player p) {
        var eye = p.getEyeLocation(); var block = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), 12, FluidCollisionMode.NEVER, true);
        double range = block == null ? 12 : Math.min(12, block.getHitPosition().distance(eye.toVector()));
        var hit = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.3,
            e -> e instanceof LivingEntity && !(e instanceof Player) && !e.getScoreboardTags().contains(GimmickWorld.TAG));
        return hit == null ? null : (LivingEntity) hit.getHitEntity();
    }

    // ------------------------------------------------------------------ 설치

    private LiteralArgumentBuilder<CommandSourceStack> spawn() {
        var model = Commands.argument("모델", modelArg()).executes(this::spawn);
        var puzzle = Commands.argument("퍼즐", puzzleArg()).executes(this::spawn);
        puzzle.then(nodeArg().executes(this::spawn));
        return Commands.literal("소환").then(model.then(puzzle));
    }

    private int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = self(ctx);
        String model = ctx.getArgument("모델", String.class);
        var feet = p.getLocation().getBlock();
        var refused = new String[1];
        Gimmick g = plugin.placer().place(p, model, feet, Gimmick.facingOf(p.getLocation().getYaw()),
            opt(ctx, "퍼즐", String.class, ""), opt(ctx, "노드", String.class, ""), r -> refused[0] = r);
        if (g == null) throw ChacaLink.error(refused[0] == null ? "설치하지 못했어요" : refused[0]);
        ChacaLink.ok(p, g.id + " 설치 (" + plugin.catalog().get(model).name() + ")  블록 " + g.bx + ", " + g.by + ", " + g.bz
            + (g.live() ? "" : " · 모델을 그리지 못했어요 (콘솔 확인)"));
        return 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> item() {
        return Commands.literal("아이템").then(Commands.argument("모델", modelArg()).executes(this::item)
            .then(Commands.argument("개수", IntegerArgumentType.integer(1, 64)).executes(this::item)));
    }

    private int item(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = self(ctx);
        String model = ctx.getArgument("모델", String.class);
        if (!plugin.catalog().known(model)) throw ChacaLink.error("models.yml에 없는 모델이에요: " + model);
        var stack = plugin.placer().item(model);
        stack.setAmount(opt(ctx, "개수", Integer.class, 1));
        p.getInventory().addItem(stack).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        ChacaLink.ok(p, plugin.catalog().get(model).name() + " 설치 아이템 " + stack.getAmount() + "개를 받았어요");
        return 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> attach() {
        return Commands.literal("씌우기").then(Commands.argument("모델", modelArg()).executes(this::attach)
            .then(Commands.argument("크기", DoubleArgumentType.doubleArg(0.1, 8)).executes(this::attach)));
    }

    private int attach(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = self(ctx);
        String model = ctx.getArgument("모델", String.class);
        LivingEntity mob = lookingMob(p);
        if (mob == null || GimmickProtection.excluded(mob) || !plugin.protection().pair(p, mob.getLocation())) throw ChacaLink.error("바라보는 몹이 없어요");
        Gimmick g = plugin.world().attach(mob, model, opt(ctx, "크기", Double.class, 1d));
        if (g == null) throw new IllegalStateException("기믹 모델 씌우기 실패: " + model + " → " + mob.getType());
        if (!plugin.protection().pair(p, g.location())) throw ChacaLink.error("보호 구역 안이에요");
        plugin.tool().select(p, g);
        ChacaLink.ok(p, mob.getType().name() + "에 " + plugin.catalog().get(model).name() + " 씌움 (" + g.id + ")");
        return 1;
    }

    private int detach(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = self(ctx);
        LivingEntity mob = lookingMob(p);
        Gimmick g = mob == null ? null : plugin.world().byEntity(mob);
        if (g == null || !g.attached()) throw ChacaLink.error("모델을 씌운 몹을 바라봐 주세요");
        plugin.world().remove(g);
        ChacaLink.ok(p, "모델을 벗겼어요");
        return 1;
    }

    private int tool(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = self(ctx);
        p.getInventory().addItem(plugin.tool().create());
        ChacaLink.ok(p, "기믹 도구를 받았어요. 우클릭 +, 좌클릭 -, 웅크리고 클릭하면 모드 변경");
        return 1;
    }

    // ------------------------------------------------------------------ 고르기·보기

    private LiteralArgumentBuilder<CommandSourceStack> pick(String literal, boolean infoOnly) {
        com.mojang.brigadier.Command<CommandSourceStack> run = ctx -> {
            Player p = self(ctx);
            String id = opt(ctx, "번호", String.class, null);
            Gimmick g = infoOnly ? target(p, id) : id != null ? plugin.world().get(id) : plugin.world().looking(p, 16);
            usable(p, g, infoOnly ? "선택된 기믹이 없어요" : "기믹을 바라보거나 번호를 적어 주세요");
            if (!infoOnly) plugin.tool().select(p, g);
            plugin.tool().info(p, g);
            return 1;
        };
        return Commands.literal(literal).executes(run).then(Commands.argument("번호", idArg()).executes(run));
    }

    private LiteralArgumentBuilder<CommandSourceStack> near() {
        com.mojang.brigadier.Command<CommandSourceStack> run = ctx -> {
            Player p = self(ctx);
            double r = opt(ctx, "반경", Double.class, 16d);
            var list = plugin.world().near(p.getLocation(), r);
            var out = new ArrayList<String>();
            out.add("── 반경 " + (int) r + "칸 기믹 " + list.size() + "개 ──");
            for (Gimmick g : list) out.add(" " + g.id + " " + plugin.catalog().get(g.model).name() + " (" + g.model + ")"
                + (g.inPuzzle() ? "  " + g.puzzle + "." + g.node : "") + (g.lastAnim.isEmpty() ? "" : "  재생 " + g.lastAnim));
            ChacaLink.lines(p, out);
            return 1;
        };
        return Commands.literal("근처").executes(run).then(Commands.argument("반경", DoubleArgumentType.doubleArg(1, 256)).executes(run));
    }

    private LiteralArgumentBuilder<CommandSourceStack> list() {
        com.mojang.brigadier.Command<CommandSourceStack> run = ctx -> {
            String q = opt(ctx, "검색", String.class, null);
            var list = plugin.world().all().stream().filter(g -> q == null || g.puzzle.equals(q) || g.model.contains(q) || g.id.equals(q)).toList();
            var out = new ArrayList<String>();
            out.add("── 기믹 " + list.size() + "개" + (q == null ? "" : " (" + q + ")") + " ──");
            for (Gimmick g : list.subList(0, Math.min(40, list.size())))
                out.add(String.format(Locale.ROOT, " %s %s %s %.0f %.0f %.0f %s", g.id, g.model, g.attached() ? "(몹)" : g.world, g.x, g.y, g.z,
                    g.inPuzzle() ? g.puzzle + "." + g.node : ""));
            if (list.size() > 40) out.add(" … 외 " + (list.size() - 40) + "개 (검색어로 좁혀 보세요)");
            ChacaLink.lines(ctx.getSource().getSender(), out);
            return 1;
        };
        return Commands.literal("목록").executes(run).then(Commands.argument("검색", ChacaLink.word(() -> {
            var all = new ArrayList<String>(plugin.puzzles().ids()); all.addAll(plugin.catalog().ids()); return all;
        })).executes(run));
    }

    private LiteralArgumentBuilder<CommandSourceStack> models() {
        com.mojang.brigadier.Command<CommandSourceStack> run = ctx -> {
            String q = opt(ctx, "검색", String.class, "");
            var ids = plugin.catalog().ids().stream().filter(i -> i.contains(q) || plugin.catalog().get(i).name().contains(q)).toList();
            var out = new ArrayList<String>();
            out.add("── 모델 " + ids.size() + "개" + (q.isEmpty() ? "" : " (" + q + ")") + " ──");
            for (String i : ids) { var info = plugin.catalog().get(i); out.add(" " + i + "  " + info.name() + "  " + info.footW() + "x" + info.footD() + "칸"); }
            ChacaLink.lines(ctx.getSource().getSender(), out);
            return 1;
        };
        return Commands.literal("모델").executes(run).then(Commands.argument("검색", ChacaLink.word(() -> plugin.catalog().ids())).executes(run));
    }

    // ------------------------------------------------------------------ 설정

    private LiteralArgumentBuilder<CommandSourceStack> settings() {
        var facing = Commands.literal("방향");
        for (var e : FACINGS.entrySet()) {
            int v = e.getValue();
            facing.then(Commands.literal(e.getKey()).executes(ctx -> change(ctx, g -> { if (g.attached()) g.yaw = v * 90f; else g.facing = v; })));
        }
        return Commands.literal("설정")
            .then(facing)
            .then(Commands.literal("크기").then(Commands.argument("크기", DoubleArgumentType.doubleArg(0.1, 8))
                .executes(ctx -> change(ctx, g -> g.scale = DoubleArgumentType.getDouble(ctx, "크기")))))
            .then(Commands.literal("높이").then(Commands.argument("높이", IntegerArgumentType.integer(-2048, 4096))
                .executes(ctx -> change(ctx, g -> {
                    if (g.attached()) throw ChacaLink.error("몹에 씌운 기믹은 높이를 바꿀 수 없어요");
                    g.by = IntegerArgumentType.getInteger(ctx, "높이");
                }))))
            .then(Commands.literal("위치").then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 4096)).then(Commands.argument("z", IntegerArgumentType.integer())
                    .executes(ctx -> change(ctx, g -> {
                        if (g.attached()) throw ChacaLink.error("몹에 씌운 기믹은 위치를 바꿀 수 없어요");
                        g.bx = IntegerArgumentType.getInteger(ctx, "x"); g.by = IntegerArgumentType.getInteger(ctx, "y"); g.bz = IntegerArgumentType.getInteger(ctx, "z");
                    }))))))
            .then(Commands.literal("상태").then(Commands.argument("상태", ChacaLink.word(() -> STATES))
                .executes(ctx -> change(ctx, g -> state(g, ctx.getArgument("상태", String.class))))));
    }

    /** 0~3 = 거울·다이얼 위치 / 수위 기둥 수위, 색 이름 = 프리즘. */
    private void state(Gimmick g, String raw) throws CommandSyntaxException {
        var info = plugin.catalog().get(g.model);
        if (info.is("prism")) {
            String c = Colors.norm(raw);
            if (!Colors.ko(c).equals(raw.trim()) && !c.equalsIgnoreCase(raw.trim())) throw ChacaLink.error("프리즘 색은 흰색·빨강·노랑·초록·파랑·보라 중에서 골라 주세요");
            plugin.devices().color(g, c);
            return;
        }
        int v;
        try { v = Integer.parseInt(raw.trim()); } catch (NumberFormatException e) { throw ChacaLink.error("상태는 0~3 숫자로 적어 주세요 (프리즘만 색 이름)"); }
        if (v < 0 || v > 3) throw ChacaLink.error("상태는 0~3 숫자로 적어 주세요");
        if (info.is("pillar")) plugin.devices().level(g, v - g.level);
        else if (info.is("mirror") || info.is("dial")) plugin.devices().position(g, v - g.pos);
        else throw ChacaLink.error("거울·다이얼·수위 기둥·프리즘만 상태가 있어요");
    }

    @FunctionalInterface private interface Edit { void apply(Gimmick g) throws CommandSyntaxException; }

    private int change(CommandContext<CommandSourceStack> ctx, Edit edit) throws CommandSyntaxException {
        Player p = self(ctx);
        Gimmick g = usable(p, target(p, null), "선택된 기믹이 없어요. 도구로 고르거나 /던전관리 선택 을 써 주세요");
        int[] old = {g.bx, g.by, g.bz, g.facing};
        float oldYaw = g.yaw; double oldScale = g.scale;
        edit.apply(g);
        if (!g.attached() && plugin.tool().blocked(p, g, plugin.catalog().get(g.model))) {
            g.bx = old[0]; g.by = old[1]; g.bz = old[2]; g.facing = old[3]; g.yaw = oldYaw; g.scale = oldScale;
            throw ChacaLink.error("다른 기믹과 겹쳐서 바꾸지 않았어요");
        }
        plugin.world().transform(g);
        ChacaLink.ok(p, g.attached() ? String.format(Locale.ROOT, "%s 방향 %.0f° 크기 %.2f", g.id, g.yaw, g.scale)
            : String.format(Locale.ROOT, "%s 블록 %d %d %d  방향 %s  크기 %.2f", g.id, g.bx, g.by, g.bz, DebugTool.FACING[g.facing], g.scale));
        return 1;
    }

    private LiteralArgumentBuilder<CommandSourceStack> animation() {
        return Commands.literal("애니메이션").then(Commands.argument("이름", ChacaLink.word(List::of)).suggests((ctx, b) -> {
                if (ctx.getSource().getSender() instanceof Player p) {
                    Gimmick g = plugin.tool().selection(p);
                    if (g != null) for (String a : plugin.catalog().get(g.model).anims().keySet()) if (a.startsWith(b.getRemaining())) b.suggest(a);
                }
                return b.buildFuture();
            })
            .executes(ctx -> {
                Player p = self(ctx);
                Gimmick g = usable(p, target(p, null), "선택된 기믹이 없어요");
                if (!g.live()) throw ChacaLink.error("모델이 그려지지 않은 기믹이에요");
                String anim = ctx.getArgument("이름", String.class);
                plugin.world().play(g, anim, null);
                ChacaLink.ok(p, g.id + " 애니메이션 재생: " + anim);
                return 1;
            }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("노드")
            .then(Commands.literal("없음").executes(ctx -> {
                Player p = self(ctx);
                Gimmick g = usable(p, target(p, null), "선택된 기믹이 없어요");
                plugin.world().setPuzzle(g, "", "");
                ChacaLink.ok(p, g.id + " 퍼즐 연결을 풀었어요");
                return 1;
            }))
            .then(Commands.argument("퍼즐", puzzleArg()).then(nodeArg().executes(ctx -> {
                Player p = self(ctx);
                Gimmick g = usable(p, target(p, null), "선택된 기믹이 없어요");
                String puzzle = ctx.getArgument("퍼즐", String.class), node = ctx.getArgument("노드", String.class);
                plugin.world().setPuzzle(g, puzzle, node);
                ChacaLink.ok(p, g.id + " → " + puzzle + "." + node + (plugin.puzzles().node(puzzle, node) == null ? " (puzzles.yml에 아직 없는 노드)" : ""));
                return 1;
            })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> remove() {
        com.mojang.brigadier.Command<CommandSourceStack> run = ctx -> {
            Player p = self(ctx);
            String id = opt(ctx, "번호", String.class, null);
            Gimmick g = usable(p, id != null ? plugin.world().get(id) : plugin.world().looking(p, 16), "기믹을 바라보거나 번호를 적어 주세요");
            plugin.world().remove(g);
            ChacaLink.ok(p, g.id + " 기믹을 제거했어요");
            return 1;
        };
        return Commands.literal("제거").executes(run).then(Commands.argument("번호", idArg()).executes(run));
    }

    // ------------------------------------------------------------------ 퍼즐

    private LiteralArgumentBuilder<CommandSourceStack> puzzle() {
        return Commands.literal("퍼즐")
            .executes(ctx -> {
                var ids = new ArrayList<>(plugin.puzzles().ids());
                var out = new ArrayList<String>();
                out.add("── 퍼즐 " + ids.size() + "개 ──");
                for (String id : ids) out.add(" " + id + "  노드 " + plugin.puzzles().nodes(id).size() + "개");
                ChacaLink.lines(ctx.getSource().getSender(), out);
                return 1;
            })
            .then(Commands.argument("퍼즐", puzzleArg()).executes(ctx -> {
                String id = ctx.getArgument("퍼즐", String.class);
                var e = plugin.puzzles().engine(id);
                if (e == null) throw ChacaLink.error("없는 퍼즐이에요: " + id);
                var out = new ArrayList<String>();
                out.add("── 퍼즐 " + id + " ──");
                for (String n : e.def().nodes().keySet()) {
                    var col = e.collected(n);
                    out.add(" " + n + ": " + (e.on(n) ? "켜짐" : "꺼짐") + "  기믹 " + plugin.world().inNode(id, n).size() + "개" + (col.isEmpty() ? "" : "  넣은 것 " + col));
                }
                ChacaLink.lines(ctx.getSource().getSender(), out);
                return 1;
            }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> signal() {
        var node = nodeArg().executes(ctx -> signal(ctx, null));
        node.then(Commands.literal("켜기").executes(ctx -> signal(ctx, true)));
        node.then(Commands.literal("끄기").executes(ctx -> signal(ctx, false)));
        node.then(Commands.literal("전환").executes(ctx -> signal(ctx, null)));
        return Commands.literal("퍼즐신호").then(Commands.argument("퍼즐", puzzleArg()).then(node));
    }

    private int signal(CommandContext<CommandSourceStack> ctx, Boolean on) throws CommandSyntaxException {
        String puzzle = ctx.getArgument("퍼즐", String.class), node = ctx.getArgument("노드", String.class);
        String who = ctx.getSource().getSender() instanceof Player p ? p.getName() : null;
        if (!plugin.puzzles().force(puzzle, node, on, who)) throw ChacaLink.error("변화가 없어요 (퍼즐·노드 이름, 이미 같은 상태, 고정(latch) 노드인지 확인)");
        ChacaLink.ok(ctx.getSource().getSender(), puzzle + "." + node + " → " + (plugin.puzzles().isOn(puzzle, node) ? "켜짐" : "꺼짐"));
        return 1;
    }
}
