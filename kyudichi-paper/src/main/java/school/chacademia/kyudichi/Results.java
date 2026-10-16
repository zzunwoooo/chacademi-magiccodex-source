package school.chacademia.kyudichi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import school.magiccodex.protocol.KyudichiProtocol;

/** End of a match: MVP, rating changes (rated only), DB write, result screen / chat summary. */
final class Results {
    private final KyudichiPlugin plugin;
    Results(KyudichiPlugin plugin) { this.plugin = plugin; }

    void finish(Match match, Team winner, Match.EndReason reason) {
        Rules rules = match.rules;
        boolean voided = reason == Match.EndReason.VOID;
        // MVP per team (tie: fewer deaths)
        Map<Team, Double> totals = new EnumMap<>(Team.class);
        for (Team t : Team.values()) totals.put(t, 0.0);
        for (var m : match.players.values()) { m.mvpPoints = m.mvpScore(rules.mvp); totals.merge(m.team, m.mvpPoints, Double::sum); }
        for (Team t : Team.values()) {
            match.players.values().stream().filter(m -> m.team == t && m.mvpPoints > 0)
                    .max(Comparator.<MatchPlayer>comparingDouble(m -> m.mvpPoints).thenComparing(m -> -m.deaths)).ifPresent(m -> m.mvp = true);
        }
        double[] avg = new double[2]; int[] count = new int[2];
        for (var m : match.players.values()) { avg[m.team.ordinal()] += m.ratingBefore; count[m.team.ordinal()]++; }
        for (int i = 0; i < 2; i++) avg[i] = count[i] == 0 ? rules.rating.start() : avg[i] / count[i];

        var deltas = new ArrayList<RatingStore.Delta>();
        Map<UUID, Integer> change = new java.util.HashMap<>();
        for (var m : match.players.values()) {
            RatingMath.Result res = winner == null ? RatingMath.Result.DRAW : winner == m.team ? RatingMath.Result.WIN : RatingMath.Result.LOSS;
            double total = totals.get(m.team);
            int d = 0;
            if (match.opt.rated() && !voided) {
                d = rules.rating.change(avg[m.team.ordinal()], avg[m.team.other().ordinal()], res, total <= 0 ? Double.NaN : m.mvpPoints / total, count[m.team.ordinal()], m.left);
                deltas.add(new RatingStore.Delta(m.id, m.nick, (int) m.ratingBefore, res == RatingMath.Result.WIN && !m.left, res == RatingMath.Result.DRAW && !m.left,
                        res == RatingMath.Result.LOSS && !m.left, m.left, m.goals, m.kills, m.deaths, m.assists, m.damage, m.heal, m.snitch, m.deflectHits, m.mvp,
                        m.team.ordinal(), d));
            }
            change.put(m.id, d);
        }
        int win = winner == null ? -1 : winner.ordinal();
        if (!deltas.isEmpty()) plugin.store().record(match.id, match.arena.name, match.score[0], match.score[1], win, reason.name(), deltas, rows -> {
            if (rows == null) return;
            for (var m : match.players.values()) {
                Player p = Bukkit.getPlayer(m.id);
                var row = rows.get(m.id);
                if (p != null && row != null) sendResult(match, p, m, winner, voided, change.get(m.id), row.rating());
            }
        });
        else for (var m : match.players.values()) {
            Player p = Bukkit.getPlayer(m.id);
            if (p != null) sendResult(match, p, m, winner, voided, 0, (int) m.ratingBefore);
        }
        plugin.getLogger().info("큐디치 경기 종료 " + match.arena.name + " " + match.score[0] + ":" + match.score[1] + " 승리=" + (winner == null ? "무승부" : winner.key) + " 사유=" + reason
                + (match.opt.rated() ? "" : " (레이팅 미반영)"));
    }

    private void sendResult(Match match, Player p, MatchPlayer m, Team winner, boolean voided, int delta, int ratingAfter) {
        var r = match.rules.rating;
        int outcome = voided ? 3 : winner == null ? 2 : winner == m.team ? 1 : 0;
        var before = r.tier(m.ratingBefore, 0); var after = r.tier(ratingAfter, plugin.store().rank(m.id));
        if (plugin.net().hasMod(p)) {
            var lines = new ArrayList<KyudichiProtocol.Line>();
            for (var o : match.players.values()) lines.add(new KyudichiProtocol.Line(o.id, o.nick, o.team.ordinal(), o.goals, o.kills, o.deaths, o.snitch, o.mvp));
            plugin.net().send(p, new KyudichiProtocol.Result(outcome, match.score[0], match.score[1], delta, ratingAfter, before.ordinal(), after.ordinal(), lines));
            return;
        }
        String head = switch (outcome) { case 1 -> "승리"; case 0 -> "패배"; case 2 -> "무승부"; default -> "경기 무효"; };
        NamedTextColor color = outcome == 1 ? NamedTextColor.GOLD : outcome == 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
        p.showTitle(Title.title(Component.text(head, color), Component.text(match.score[0] + " : " + match.score[1])));
        p.sendMessage(Component.text("── 큐디치 " + head + " " + Team.A.label + " " + match.score[0] + " : " + match.score[1] + " " + Team.B.label + " ──", color));
        for (Team t : Team.values()) for (var o : match.players.values()) if (o.team == t)
            p.sendMessage(Component.text((o.mvp ? "♛ " : "  ") + o.nick + "  골 " + o.goals + " · 처치 " + o.kills + " · 데스 " + o.deaths + (o.snitch > 0 ? " · 스니치" : ""), t.color));
        if (match.opt.rated() && !voided)
            p.sendMessage(Component.text("레이팅 " + (int) m.ratingBefore + " → " + ratingAfter + " (" + (delta >= 0 ? "+" : "") + delta + ")  " + after.label, NamedTextColor.AQUA));
        else if (!voided) p.sendMessage(Component.text("테스트 경기라 레이팅은 바뀌지 않았어요.", NamedTextColor.GRAY));
    }

    static List<String> describeTiers(RatingMath r) {
        int[] f = r.tierFloors();
        return List.of("브론즈 ~" + (f[0] - 1), "실버 " + f[0] + "~", "골드 " + f[1] + "~", "플래티넘 " + f[2] + "~", "다이아몬드 " + f[3] + "~", "마스터 " + f[4] + "~");
    }
}
