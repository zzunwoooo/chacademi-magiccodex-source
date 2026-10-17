package school.magiccodex.client.kui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Sample data copied from the reference images, one entry per view. */
final class Samples {
    record View(String name, int w, int h, float mx, float my, Consumer<Pen> paint) {}
    static UUID id(int i) { return new UUID(0x51, i); }
    static final List<View> ALL = new ArrayList<>();
    static {
        ALL.add(new View("hud", 1672, 941, -1, -1, p -> {
            HudView.score(p, new HudView.Score("우리 팀", 30, Theme.BLUE, "상대 팀", 20, Theme.RED, "06:12", "", false));
            HudView.feed(p, List.of(new HudView.FeedLine(1, "준우", "", 10, Theme.WHITE, 0, 1),
                    new HudView.FeedLine(2, "루나", "이안", 3, Theme.WHITE, Theme.RED, 1),
                    new HudView.FeedLine(1, "서하", "", 10, Theme.WHITE, 0, 1)));
            HudView.keys(p, new HudView.Keys(3, 0, 0, 7_400, 10_000));
        }));
        ALL.add(new View("application", 1672, 941, -1, -1, p -> {
            ApplicationView.paint(p, new ApplicationView.Data(1250, 18, 12, 1250, List.of(
                    new ApplicationView.Member(id(1), "준우", "", 0, 1250, true),
                    new ApplicationView.Member(id(2), "루나", "", 1, 1280, false),
                    new ApplicationView.Member(id(3), "하린", "", 2, 1220, false)), true, "파티장만 시작할 수 있습니다.", false));
            ApplicationView.queue(p, new ApplicationView.Queue(42, "±250", false), 0);
        }));
        ALL.add(new View("prepare", 1672, 941, -1, -1, p -> {
            var mine = List.of(new PrepareView.Row(id(1), "준우", "", true, 0, "b", "별빛 빗자루", 1250),
                    new PrepareView.Row(id(2), "루나", "", false, 1, "b", "은하수", 1280),
                    new PrepareView.Row(id(3), "하린", "", true, 2, "b", "바이올렛 트윈테일", 1220));
            var enemy = List.of(new PrepareView.Row(id(4), "서하", "", true, 0, "b", "별빛 빗자루", 1260),
                    new PrepareView.Row(id(5), "이안", "", false, 3, "b", "은하수", 1240),
                    new PrepareView.Row(id(6), "유진", "", true, 1, "b", "바이올렛 트윈테일", 1250));
            var spells = List.of(new PrepareView.Spell("x", "가속 마법", "Q"), new PrepareView.Spell("x", "보호막", "E"), new PrepareView.Spell("x", "흑요석 구체", "R"),
                    new PrepareView.Spell("x", "바람 제어", "T"), new PrepareView.Spell("x", "섬광", "G"), new PrepareView.Spell("x", "순간이동", "V"));
            PrepareView.paint(p, new PrepareView.Data(45_000, mine, enemy, spells, false, true));
        }));
        ALL.add(new View("spellpick", 1536, 1024, 400, 470, p -> {
            var eq = new ArrayList<SpellPickView.Cell>();
            String[] n = {"가속 마법", "보호막", "흑요석 구체", "바람 제어", "섬광", "비행"};
            for (int i = 0; i < 6; i++) eq.add(new SpellPickView.Cell("s" + i, "x", n[i], ""));
            var cat = new ArrayList<SpellPickView.Cell>();
            for (int i = 0; i < 30; i++) cat.add(new SpellPickView.Cell(i < 6 ? "s" + i : "c" + i, "x", i < 6 ? n[i] : "마법 " + (i + 1), "재사용 8초 · 마나 20"));
            SpellPickView.paint(p, new SpellPickView.Data(eq, List.of("Q", "E", "R", "T", "G", "V"), 5, cat, 1, 0, -1, ""));
        }));
        ALL.add(new View("result", 1672, 941, -1, -1, p -> {
            var mine = List.of(new ResultView.Line(id(1), "준우", "", 3, 2, 0, true), new ResultView.Line(id(2), "루나", "", 2, 1, 1, false), new ResultView.Line(id(3), "하린", "", 1, 3, 0, false));
            var enemy = List.of(new ResultView.Line(id(4), "서하", "", 2, 1, 0, false), new ResultView.Line(id(5), "이안", "", 2, 2, 0, false), new ResultView.Line(id(6), "유진", "", 1, 1, 0, false));
            ResultView.paint(p, new ResultView.Data(1, "우리 팀", "상대 팀", 80, 50, mine, enemy, 1250, 1268, true));
        }));
        ALL.add(new View("accept", 1586, 992, -1, -1, p -> AcceptView.paint(p, new AcceptView.Data(8, 3, 6, false))));
        for (var tab : DashboardView.Tab.values()) ALL.add(new View("dashboard_" + tab.name().toLowerCase(), 1672, 941, -1, -1, p -> DashboardView.paint(p, dash(tab))));
        ALL.add(new View("dashboard_ranking_scrolled", 1672, 941, -1, -1, p -> { var d = dash(DashboardView.Tab.RANKING);
            DashboardView.paint(p, new DashboardView.Data(d.tab(), 4, d.live(), 0, d.ranking(), d.mine(), 18, d.record(), true, "", false, false, "")); }));
    }
    static DashboardView.Who who(int i, String n) { return new DashboardView.Who(id(i), n, ""); }
    static DashboardView.Data dash(DashboardView.Tab tab) {
        var live = List.of(
                new DashboardView.Live("별빛 경기장", 2, 30, 20, 372_000, 3, List.of(who(1, "준우"), who(2, "루나"), who(3, "하린")), List.of(who(4, "서하"), who(5, "이안"), who(6, "유진"))),
                new DashboardView.Live("하늘 경기장", 2, 50, 40, 228_000, 3, List.of(who(2, "루나"), who(3, "하린"), who(1, "준우")), List.of(who(5, "이안"), who(4, "서하"), who(6, "유진"))),
                new DashboardView.Live("새벽 경기장", 0, 0, 0, 32_000, 0, List.of(who(3, "하린"), who(1, "준우"), who(2, "루나")), List.of(who(6, "유진"), who(5, "이안"), who(4, "서하"))));
        var rank = new ArrayList<DashboardView.Rank>();
        String[] n = {"도윤", "서하", "루나", "이안", "유진", "하린", "지우"}; int[] r = {1920, 1884, 1812, 1764, 1720, 1688, 1624}; int[] wr = {72, 69, 67, 64, 62, 60, 58};
        for (int i = 0; i < 7; i++) rank.add(new DashboardView.Rank(i + 1, id(10 + i), n[i], "", r[i], i < 3 ? 6 + i : i < 5 ? 5 : 4, 100, wr[i], 100 - wr[i]));
        for (int i = 7; i < 40; i++) rank.add(new DashboardView.Rank(i + 1, id(10 + i), "플레이어" + (i + 1), "", 1600 - i * 5, 3, 50, 25, 25));
        var mine = new DashboardView.Rank(47, id(1), "준우", "", 1342, 3, 50, 27, 21);
        int[][] g = {{1, 80, 50, 3, 5, 2}, {0, 40, 60, 1, 4, 5}, {1, 70, 30, 4, 6, 3}, {0, 20, 50, 0, 2, 6}, {1, 90, 70, 5, 8, 4}, {0, 30, 60, 1, 3, 7}, {1, 60, 40, 2, 5, 3}, {2, 50, 50, 2, 4, 4}, {1, 80, 60, 3, 6, 3}, {0, 30, 40, 1, 2, 5}};
        var recent = new ArrayList<DashboardView.Recent>();
        for (var x : g) recent.add(new DashboardView.Recent(x[0], 0, x[1], x[2], x[3], x[4], x[5]));
        var rec = new DashboardView.PlayerRec(id(1), "준우", "", 1342, 3, 47, 50, 27, 21, 2, 128, 96, 72, 24_580, 8_240, 9, 147, 12, recent);
        return new DashboardView.Data(tab, 4, live, 0, rank, mine, 0, rec, true, "", false, false, "");
    }
}
