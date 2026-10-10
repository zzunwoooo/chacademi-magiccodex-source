package kr.chacademy.portrait.cmd;

import kr.chacademy.portrait.ChacaPortraitPlugin;
import kr.chacademy.portrait.core.CostModel;
import kr.chacademy.portrait.core.PortraitSettings;
import kr.chacademy.portrait.data.PortraitStorage;
import kr.chacademy.portrait.service.PortraitService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /portrait (관리자)
 * <pre>
 * regen &lt;플레이어&gt; [모델]                         강제 다시 생성 (적용)
 * reset &lt;플레이어&gt;                                일러스트 삭제·자동 시도 초기화
 * model [gpt-image-2.5-sunburst|gpt-image-2.5-flare|gpt-image-2|gpt-image-1.5]                 사용 모델 보기/바꾸기 (config 저장)
 * status [플레이어] · budget · reload
 * rerolls &lt;플레이어&gt;                              최근 다시 그리기 시도 (상태·실패 사유)
 * refund &lt;플레이어&gt;                               소모 처리된 다시 그리기 아이템 1개 반환 (기존 반환 경로 사용, 기록 남김)
 * budget adjust &lt;usd&gt;                             예산 장부의 사용액 보정 (기록 남김)
 * </pre>
 */
public final class AdminCommand implements TabExecutor, TabCompleter {

    private static final List<String> MODELS = PortraitSettings.IMAGE_MODELS;
    private final ChacaPortraitPlugin plugin;

    public AdminCommand(ChacaPortraitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chacaportrait.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "regen" -> regen(sender, args);
            case "reset" -> reset(sender, args);
            case "model" -> model(sender, args);
            case "status" -> status(sender, args);
            case "budget" -> budget(sender, args);
            case "rerolls" -> rerolls(sender, args);
            case "refund" -> refund(sender, args);
            case "reload" -> {
                plugin.reloadSettings();
                PortraitSettings s = plugin.settings();
                sender.sendMessage("§a[ChacaPortrait] 다시 읽음 — 모델 " + s.imageModel + ", 품질 " + s.quality
                        + (plugin.service().referenceError() == null ? "" : " §c(" + plugin.service().referenceError() + ")"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§b[ChacaPortrait] 관리자 명령어");
        s.sendMessage("§f/portrait regen <플레이어> [모델] §7유료 생성·저장·본인에게 전달 (자동 OFF에서도 가능)");
        s.sendMessage("§f/portrait reset <플레이어> §7일러스트 삭제·자동 시도 초기화 (자동 OFF면 재생성하지 않음)");
        s.sendMessage("§f/portrait model [gpt-image-2.5-sunburst|gpt-image-2.5-flare|gpt-image-2|gpt-image-1.5] §7사용 모델 보기/바꾸기");
        s.sendMessage("§f/portrait status [플레이어] §7대기열·플레이어 상태");
        s.sendMessage("§f/portrait budget §7일러스트 예산");
        s.sendMessage("§f/portrait budget adjust <usd> §7사용액 장부 보정 (+ 늘리기 / - 줄이기, 기록 남김)");
        s.sendMessage("§f/portrait rerolls <플레이어> §7최근 다시 그리기 시도 (상태·실패 사유)");
        s.sendMessage("§f/portrait refund <플레이어> §7소모 처리된 다시 그리기 아이템 1개 반환");
        s.sendMessage("§f/portrait reload §7설정·레퍼런스 다시 읽기");
    }

    private Player online(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§c플레이어 이름을 적어 주세요.");
            return null;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            s.sendMessage("§c접속 중인 플레이어만 가능합니다 (스킨을 읽어야 함): " + args[1]);
        }
        return p;
    }

    private boolean ready(CommandSender s) {
        PortraitSettings st = plugin.settings();
        if (!st.enabled) {
            s.sendMessage("§cconfig.yml enabled: false 상태입니다.");
            return false;
        }
        if (!st.hasKey()) {
            s.sendMessage("§cOpenAI 키가 없습니다 (환경변수 CHACAPORTRAIT_OPENAI_KEY 또는 CHACANPC_OPENAI_KEY, 혹은 이 플러그인의 openai.api-key).");
            return false;
        }
        if (plugin.service().referenceError() != null) {
            s.sendMessage("§c" + plugin.service().referenceError());
            return false;
        }
        return true;
    }

    private void regen(CommandSender s, String[] args) {
        Player p = online(s, args);
        if (p == null || !ready(s)) {
            return;
        }
        List<String> models = args.length >= 3 ? List.of(args[2]) : List.of();
        if (!models.isEmpty() && !PortraitSettings.supportedImageModel(models.get(0))) {
            s.sendMessage("§c지원 모델: " + String.join(", ", MODELS) + "");
            return;
        }
        // 유료 호출 없이 알 수 있는 문제 (server-id 없음·단가 없음 등)는 대기열에 넣기 전에 알려 준다
        String problem = plugin.service().staticProblem(plugin.settings(), models);
        if (problem != null) {
            s.sendMessage("§c" + problem);
            return;
        }
        PortraitService.Job job = PortraitService.jobFor(PortraitService.Kind.ADMIN, p, "", null, models, s);
        if (job.skin().url() == null) {
            s.sendMessage("§c스킨을 읽지 못했습니다.");
            return;
        }
        if (!plugin.service().submit(job, null)) {
            s.sendMessage("§c이미 그리는 중이거나 대기열이 가득 찼습니다.");
            return;
        }
        s.sendMessage("§b[ChacaPortrait] " + p.getName() + " 다시 생성 시작.");
    }

    private OfflinePlayer known(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return p;
        }
        // 메인 스레드에서 웹 조회를 하지 않도록 캐시된 플레이어만
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) {
            s.sendMessage("§c이 서버에 접속한 적 없는 플레이어입니다: " + name);
        }
        return op;
    }

    private void reset(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§c플레이어 이름을 적어 주세요.");
            return;
        }
        OfflinePlayer op = known(s, args[1]);
        if (op == null) {
            return;
        }
        UUID id = op.getUniqueId();
        plugin.service().then(plugin.db().call(() -> {
            boolean had = plugin.storage().deletePortrait(id);
            plugin.storage().resetState(id);
            return had;
        }), had -> {
            plugin.service().clearBackoff(id);
            s.sendMessage(had ? "§a[ChacaPortrait] " + args[1] + " 일러스트를 지웠습니다. 자동생성이 켜진 학교 서버에서만 다음 접속 때 다시 그립니다."
                    : "§e[ChacaPortrait] " + args[1] + " 일러스트가 없습니다 (자동 시도 횟수만 초기화).");
            Player p = op.getPlayer();
            if (p != null) {
                plugin.channel().cleared(p);
            }
        }, ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
    }

    private void model(CommandSender s, String[] args) {
        if (args.length < 2) {
            PortraitSettings st = plugin.settings();
            s.sendMessage("§b[ChacaPortrait] 현재 모델 " + st.imageModel + ", 품질 " + st.quality + " (바꾸기: /portrait model <gpt-image-2.5-sunburst|gpt-image-2.5-flare|gpt-image-2|gpt-image-1.5>)");
            return;
        }
        String m = args[1];
        if (!PortraitSettings.supportedImageModel(m)) {
            s.sendMessage("§c지원 모델: " + String.join(", ", MODELS) + "");
            return;
        }
        if (!PortraitSettings.supportsQuality(m, plugin.settings().quality)) {
            s.sendMessage("§c현재 품질은 이 모델에서 지원하지 않습니다. image.quality 설정을 확인하세요.");
            return;
        }
        plugin.getConfig().set("image.model", m);
        plugin.saveConfig();
        plugin.reloadSettings();
        s.sendMessage("§a[ChacaPortrait] 이미지 모델을 " + m + "(으)로 바꿨습니다. 다음 생성부터 적용됩니다.");
    }

    private void status(CommandSender s, String[] args) {
        PortraitSettings st = plugin.settings();
        s.sendMessage("§b[ChacaPortrait] 모델 " + st.imageModel + " · 품질 " + st.quality + " · 진행 " + plugin.service().runningCount()
                + " · 대기 " + plugin.service().waitingCount() + " · 레퍼런스 "
                + (plugin.service().referenceError() == null ? "정상" : "§c" + plugin.service().referenceError()));
        String problem = plugin.service().staticProblem();
        if (problem != null) {
            s.sendMessage("§e  생성 불가: " + problem);
        }
        if (plugin.service().paused()) {
            s.sendMessage("§e  OpenAI 일시적 실패가 이어져 새 작업을 잠시 쉬는 중입니다 (자동으로 다시 시작).");
        }
        if (args.length >= 2) {
            OfflinePlayer op = known(s, args[1]);
            if (op == null) {
                return;
            }
            UUID id = op.getUniqueId();
            plugin.service().then(plugin.db().call(() -> {
                var portrait = plugin.storage().portrait(id);
                String state = plugin.storage().lastError(id);
                return (portrait == null ? "일러스트 없음" : "일러스트 있음 (" + portrait.model() + ", " + portrait.source() + ")")
                        + (state == null ? "" : " · " + state);
            }), line -> s.sendMessage("§f" + args[1] + ": " + line + (plugin.service().busy(id) ? " · 이 서버에서 그리는 중" : "")),
                    ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
        }
    }

    private void budget(CommandSender s, String[] args) {
        if (args.length >= 2) {
            if (args.length == 3 && args[1].equalsIgnoreCase("adjust")) {
                budgetAdjust(s, args[2]);
            } else {
                s.sendMessage("§c사용법: /portrait budget · /portrait budget adjust <usd>");
            }
            return;
        }
        PortraitSettings st = plugin.settings();
        plugin.service().then(plugin.db().call(() -> plugin.storage().budget()), b -> {
            long cap = (long) Math.floor(st.budgetUsd * 1_000_000L);
            s.sendMessage("§b[ChacaPortrait] 예산 " + CostModel.usd(cap) + " · 사용 " + CostModel.usd(b.spent()) + " · 예약 중 "
                    + CostModel.usd(b.reserved()) + " · 남음 " + CostModel.usd(Math.max(0, cap - b.spent() - b.reserved())));
            CostModel cm = new CostModel(st.prices, st.estimate, st.estimate25);
            for (String m : MODELS) {
                s.sendMessage("§7  " + m + " 1장 추정 예약액(" + st.quality + "): " + CostModel.usd(cm.reserveImage(m, st.quality))
                        + " (비용 상한 아님; 실제 usage 정산, 캐시 세부 없으면 할인 미적용)");
            }
        }, ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
    }

    /** 한 번에 보정할 수 있는 최대 금액 (USD). 오타로 장부가 크게 틀어지는 일 방지. */
    private static final double MAX_ADJUST_USD = 100.0;

    /**
     * 사용액 장부 보정. 시간 초과·5xx처럼 과금 여부를 몰라 예약액으로 잡힌 금액을 OpenAI 청구서와 맞출 때 쓴다.
     * +는 사용액을 늘리고 -는 줄인다 (0 아래로는 내려가지 않음). 한 번에 ±100 USD까지. 콘솔 로그에 남는다.
     */
    private void budgetAdjust(CommandSender s, String raw) {
        double usd;
        try {
            usd = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            s.sendMessage("§c금액은 숫자로 적어 주세요. 예: /portrait budget adjust -1.25");
            return;
        }
        if (Double.isNaN(usd) || Double.isInfinite(usd) || usd == 0 || Math.abs(usd) > MAX_ADJUST_USD) {
            s.sendMessage("§c한 번에 보정할 수 있는 범위는 0이 아닌 ±" + (int) MAX_ADJUST_USD + " USD입니다.");
            return;
        }
        long delta = Math.round(usd * 1_000_000L);
        if (delta == 0) {
            s.sendMessage("§c금액이 너무 작습니다.");
            return;
        }
        String who = s.getName();
        plugin.service().then(plugin.db().call(() -> {
            long before = plugin.storage().budget().spent();
            long after = plugin.storage().adjustSpent(delta).spent();
            return new long[]{before, after};
        }), r -> {
            plugin.getLogger().warning("[ChacaPortrait] 예산 사용액 보정: " + who + " 이(가) " + CostModel.usd(r[0]) + " → "
                    + CostModel.usd(r[1]) + " (요청 " + (delta > 0 ? "+" : "-") + CostModel.usd(Math.abs(delta)) + ")");
            s.sendMessage("§a[ChacaPortrait] 사용액 " + CostModel.usd(r[0]) + " → " + CostModel.usd(r[1]) + " (기록 남김)");
        }, ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
    }

    private static String stateLabel(String state) {
        return switch (state == null ? "" : state) {
            case PortraitStorage.R_PREPARED -> "차감 준비";
            case PortraitStorage.R_CANCELLED -> "취소(차감 없음)";
            case PortraitStorage.R_PENDING -> "대기 중";
            case PortraitStorage.R_STARTED -> "생성 중";
            case PortraitStorage.R_DONE -> "성공";
            case PortraitStorage.R_CONSUMED -> "실패(아이템 소모)";
            case PortraitStorage.R_REFUND -> "반환 예정";
            case PortraitStorage.R_REFUNDED -> "반환 완료";
            default -> String.valueOf(state);
        };
    }

    /** 최근 다시 그리기 시도 (모든 서버, 최신순 10건). 문의 대응용. */
    private void rerolls(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§c플레이어 이름을 적어 주세요.");
            return;
        }
        OfflinePlayer op = known(s, args[1]);
        if (op == null) {
            return;
        }
        UUID id = op.getUniqueId();
        plugin.service().then(plugin.db().call(() -> plugin.storage().recentRerolls(id, 10)), rows -> {
            if (rows.isEmpty()) {
                s.sendMessage("§e[ChacaPortrait] " + args[1] + " 다시 그리기 기록이 없습니다.");
                return;
            }
            s.sendMessage("§b[ChacaPortrait] " + args[1] + " 최근 다시 그리기 " + rows.size() + "건 (최신순)");
            DateTimeFormatter time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(PortraitService.ZONE);
            for (PortraitStorage.Reroll r : rows) {
                String prompt = r.prompt() == null || r.prompt().isEmpty() ? "(기본)" : r.prompt();
                s.sendMessage("§f" + time.format(Instant.ofEpochMilli(r.createdAt())) + " §7[" + r.server() + "] §f" + stateLabel(r.state())
                        + " §7요청: " + (prompt.length() > 40 ? prompt.substring(0, 40) + "…" : prompt)
                        + (r.error() == null || r.error().isEmpty() ? "" : " §c사유: "
                        + (r.error().length() > 160 ? r.error().substring(0, 160) + "…" : r.error())));
            }
        }, ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
    }

    /**
     * 소모 처리된(생성 시작 후 실패) 다시 그리기 1건을 반환 대상으로 되돌린다. 실제 지급은 기존 반환 경로가 한다
     * (플레이어 데이터의 차감 영수증 확인 → 원래 아이템 1개 지급 → 반환 영수증). 이 서버에서 차감한 기록만 대상이다.
     */
    private void refund(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("§c플레이어 이름을 적어 주세요.");
            return;
        }
        if (plugin.generationBlocked()) {
            s.sendMessage("§cserver-id가 비어 있어 반환을 처리할 수 없습니다 (config.yml 설정 후 재시작).");
            return;
        }
        OfflinePlayer op = known(s, args[1]);
        if (op == null) {
            return;
        }
        UUID id = op.getUniqueId();
        String server = plugin.settings().serverId;
        String who = s.getName();
        plugin.service().then(plugin.db().call(() -> plugin.storage().refundConsumed(id, server)), token -> {
            if (token == null) {
                s.sendMessage("§e[ChacaPortrait] " + args[1] + " — 이 서버(" + server + ")에서 소모 처리된 다시 그리기 기록이 없습니다. "
                        + "/portrait rerolls 로 기록을 확인하세요 (다른 서버에서 쓴 아이템은 그 서버에서 반환).");
                return;
            }
            plugin.getLogger().warning("[ChacaPortrait] 관리자 반환: " + who + " → " + args[1] + " (" + id + "), 기록 " + token);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.rerolls().deliverRefunds(p, false);
                s.sendMessage("§a[ChacaPortrait] " + args[1] + " 다시 그리기 아이템 1개를 반환 처리했습니다 (인벤토리가 가득 차 있으면 빈 칸이 생길 때 지급).");
            } else {
                s.sendMessage("§a[ChacaPortrait] " + args[1] + " 반환 예약 완료 — 이 서버에 다음 접속할 때 아이템 1개를 돌려줍니다.");
            }
        }, ex -> s.sendMessage("§cDB 오류: " + ex.getMessage()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("chacaportrait.admin")) {
            return out.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 1) {
            for (String o : List.of("regen", "reset", "model", "status", "budget", "rerolls", "refund", "reload")) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        } else if (args.length == 2 && List.of("regen", "reset", "status", "rerolls", "refund").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("model")) {
            out.addAll(MODELS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("budget")) {
            out.add("adjust");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("regen")) {
            out.addAll(MODELS);
        }
        return out.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
