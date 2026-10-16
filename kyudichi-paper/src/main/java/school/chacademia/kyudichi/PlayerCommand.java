package school.chacademia.kyudichi;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /큐디치 — 정보, 랭킹, 포기, 준비, 관전, 연습. */
final class PlayerCommand implements TabExecutor {
    private final KyudichiPlugin plugin;
    PlayerCommand(KyudichiPlugin plugin) { this.plugin = plugin; }

    private static void say(CommandSender s, String m, NamedTextColor c) { s.sendMessage(Component.text(m, c)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { say(s, "게임 안에서만 쓸 수 있어요.", NamedTextColor.RED); return true; }
        String sub = a.length == 0 ? "정보" : a[0];
        switch (sub) {
            case "정보" -> {
                var store = plugin.store();
                java.util.function.Consumer<RatingStore.Row> show = r -> {
                    if (r == null) { say(p, "기록이 없어요.", NamedTextColor.GRAY); return; }
                    var tier = plugin.rules().rating.tier(r.rating(), store.rank(r.id()));
                    say(p, "── " + r.name() + " · " + tier.label + " " + r.rating() + " ──", NamedTextColor.GOLD);
                    say(p, r.games() + "경기 " + r.wins() + "승 " + r.losses() + "패 " + r.draws() + "무 · 골 " + r.goals() + " · 처치 " + r.kills() + " · 데스 " + r.deaths()
                            + " · 딜 " + Math.round(r.damage()) + " · 힐 " + Math.round(r.heal()) + " · 스니치 " + r.snitch() + " · MVP " + r.mvp(), NamedTextColor.WHITE);
                };
                if (a.length > 1) store.find(a[1], show); else store.load(p.getUniqueId(), p.getName(), show);
            }
            case "랭킹" -> plugin.store().top(10, rows -> {
                if (rows == null || rows.isEmpty()) { say(p, "아직 랭킹이 없어요.", NamedTextColor.GRAY); return; }
                say(p, "── 큐디치 랭킹 ──", NamedTextColor.GOLD);
                int i = 1;
                for (var r : rows) {
                    var tier = plugin.rules().rating.tier(r.rating(), plugin.store().rank(r.id()));
                    say(p, i++ + ". " + r.name() + " · " + tier.label + " " + r.rating() + " · " + r.wins() + "승 " + r.losses() + "패", NamedTextColor.WHITE);
                }
            });
            case "포기" -> {
                Match m = plugin.matches().of(p.getUniqueId());
                if (m == null) { say(p, "경기 중이 아니에요.", NamedTextColor.RED); return true; }
                say(p, m.forfeit(p, a.length < 2 || !a[1].equals("반대")), NamedTextColor.YELLOW);
            }
            case "준비" -> {
                Match m = plugin.matches().of(p.getUniqueId());
                if (m == null) { say(p, "경기 중이 아니에요.", NamedTextColor.RED); return true; }
                MatchPlayer mp = m.get(p.getUniqueId());
                m.ready(p, !mp.ready);
                say(p, mp.ready ? "준비 완료" : "준비 취소", NamedTextColor.GREEN);
            }
            case "관전" -> {
                if (a.length > 1 && a[1].equals("나가기")) { plugin.spectate().leave(p); return true; }
                Match m = a.length > 1 ? plugin.matches().inArena(a[1]) : plugin.matches().all().stream().filter(x -> !x.opt.test()).findFirst().orElse(null);
                if (m == null) { say(p, "관전할 경기가 없어요.", NamedTextColor.RED); return true; }
                String e = plugin.spectate().enter(p, m);
                if (e != null) say(p, e, NamedTextColor.RED);
            }
            case "연습" -> {
                Arena ar = a.length > 1 ? plugin.arenas().get(a[1]) : plugin.arenas().all().stream().filter(x -> x.practice && x.problems().isEmpty() && plugin.matches().inArena(x.name) == null).findFirst().orElse(null);
                if (ar == null || !ar.practice) { say(p, "비어 있는 연습장이 없어요.", NamedTextColor.RED); return true; }
                var teams = new EnumMap<Team, List<Player>>(Team.class);
                teams.put(Team.A, List.of(p)); teams.put(Team.B, List.of());
                Rules r = plugin.rules();
                String e = plugin.matches().start(ar, teams, new Match.Options(false, true, 0, r.playTicks, 0, true, Set.of()));
                if (e != null) say(p, e, NamedTextColor.RED);
                else say(p, "연습 시작! F로 퀘이플 잡기·던지기, 블러저 쳐내기, 스니치 잡기를 해 보세요. 끝내려면 /큐디치 포기", NamedTextColor.GREEN);
            }
            default -> say(p, "/큐디치 정보 [닉] · 랭킹 · 포기 [찬성|반대] · 준비 · 관전 [경기장|나가기] · 연습", NamedTextColor.GRAY);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return List.of("정보", "랭킹", "포기", "준비", "관전", "연습").stream().filter(x -> x.startsWith(a[0])).toList();
        if (a.length == 2 && a[0].equals("포기")) return List.of("찬성", "반대");
        if (a.length == 2 && a[0].equals("관전")) { var l = new java.util.ArrayList<>(plugin.matches().all().stream().map(m -> m.arena.name).toList()); l.add("나가기"); return l; }
        return List.of();
    }
}
