package kr.chacademy.portrait.cmd;

import kr.chacademy.portrait.ChacaPortraitPlugin;
import kr.chacademy.portrait.core.CostModel;
import kr.chacademy.portrait.core.PortraitSettings;
import kr.chacademy.portrait.service.PortraitService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /portrait (관리자)
 * <pre>
 * test &lt;플레이어&gt; [gpt-image-2|gpt-image-1.5|both]  시험 생성 (저장 안 함, tests/ 폴더에 파일 + 비용 보고)
 * regen &lt;플레이어&gt; [모델]                         강제 다시 생성 (적용)
 * reset &lt;플레이어&gt;                                일러스트 삭제·자동 시도 초기화
 * model [gpt-image-2|gpt-image-1.5]                 사용 모델 보기/바꾸기 (config 저장)
 * status [플레이어] · budget · reload
 * </pre>
 */
public final class AdminCommand implements TabExecutor, TabCompleter {

    private static final List<String> MODELS = List.of("gpt-image-2", "gpt-image-1.5");
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
            case "test" -> test(sender, args);
            case "regen" -> regen(sender, args);
            case "reset" -> reset(sender, args);
            case "model" -> model(sender, args);
            case "status" -> status(sender, args);
            case "budget" -> budget(sender);
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
        s.sendMessage("§f/portrait test <플레이어> [gpt-image-2|gpt-image-1.5|both] §7모델 비교 파일만 생성 (유료·예산 DB 기록, 초상화 미적용)");
        s.sendMessage("§f/portrait regen <플레이어> [모델] §7유료 생성·저장·본인에게 전달 (자동 OFF에서도 가능)");
        s.sendMessage("§f/portrait reset <플레이어> §7일러스트 삭제·자동 시도 초기화 (자동 OFF면 재생성하지 않음)");
        s.sendMessage("§f/portrait model [gpt-image-2|gpt-image-1.5] §7사용 모델 보기/바꾸기");
        s.sendMessage("§f/portrait status [플레이어] §7대기열·플레이어 상태");
        s.sendMessage("§f/portrait budget §7일러스트 예산");
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

    private void test(CommandSender s, String[] args) {
        Player p = online(s, args);
        if (p == null || !ready(s)) {
            return;
        }
        String which = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "both";
        List<String> models = which.equals("both") ? MODELS : List.of(which);
        for (String m : models) {
            if (!PortraitSettings.supportedImageModel(m)) {
                s.sendMessage("§c지원 모델: gpt-image-2, gpt-image-1.5, both");
                return;
            }
        }
        PortraitService.Job job = PortraitService.jobFor(PortraitService.Kind.TEST, p, "", null, models, s);
        if (job.skin().url() == null) {
            s.sendMessage("§c" + p.getName() + "의 스킨을 읽지 못했습니다 (기본 스킨이거나 프로필에 스킨 없음).");
            return;
        }
        if (!plugin.service().submit(job, () -> s.sendMessage("§c대기열이 가득 찼습니다."))) {
            s.sendMessage("§c지금은 시험 생성을 넣을 수 없습니다 (대기열 확인: /portrait status).");
            return;
        }
        s.sendMessage("§b[ChacaPortrait] " + p.getName() + " 시험 생성 시작 (" + String.join(", ", models) + ", 품질 "
                + plugin.settings().quality + "). 1~3분 걸릴 수 있어요. 결과는 plugins/ChacaPortrait/tests/ 에 저장됩니다.");
    }

    private void regen(CommandSender s, String[] args) {
        Player p = online(s, args);
        if (p == null || !ready(s)) {
            return;
        }
        List<String> models = args.length >= 3 ? List.of(args[2]) : List.of();
        if (!models.isEmpty() && !PortraitSettings.supportedImageModel(models.get(0))) {
            s.sendMessage("§c지원 모델: gpt-image-2, gpt-image-1.5");
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
            s.sendMessage("§b[ChacaPortrait] 현재 모델 " + st.imageModel + ", 품질 " + st.quality + " (바꾸기: /portrait model <gpt-image-2|gpt-image-1.5>)");
            return;
        }
        String m = args[1];
        if (!PortraitSettings.supportedImageModel(m)) {
            s.sendMessage("§c지원 모델: gpt-image-2, gpt-image-1.5");
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

    private void budget(CommandSender s) {
        PortraitSettings st = plugin.settings();
        plugin.service().then(plugin.db().call(() -> plugin.storage().budget()), b -> {
            long cap = (long) Math.floor(st.budgetUsd * 1_000_000L);
            s.sendMessage("§b[ChacaPortrait] 예산 " + CostModel.usd(cap) + " · 사용 " + CostModel.usd(b.spent()) + " · 예약 중 "
                    + CostModel.usd(b.reserved()) + " · 남음 " + CostModel.usd(Math.max(0, cap - b.spent() - b.reserved())));
            CostModel cm = new CostModel(st.prices, st.estimate);
            for (String m : MODELS) {
                s.sendMessage("§7  " + m + " 1장 예약액(" + st.quality + "): " + CostModel.usd(cm.reserveImage(m, st.quality))
                        + " (실제는 사용량으로 정산)");
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
            for (String o : List.of("test", "regen", "reset", "model", "status", "budget", "reload")) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        } else if (args.length == 2 && List.of("test", "regen", "reset", "status").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("model")) {
            out.addAll(MODELS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            out.addAll(List.of("both", "gpt-image-2", "gpt-image-1.5"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("regen")) {
            out.addAll(MODELS);
        }
        return out.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
