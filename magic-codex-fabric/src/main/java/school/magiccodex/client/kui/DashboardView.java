package school.magiccodex.client.kui;

import java.util.List;
import java.util.UUID;
import school.magiccodex.client.kui.Canvas.Face;

/**
 * 큐디치 대시보드 (dashboard_*_layout, references e404802e / caeb9f6b / 60b1c202; 1672×941).
 * One window frame (dashboard/main, from the 경기 현황 layout) and the same tab row for all tabs. The 랭킹 and 플레이어 기록
 * references use a smaller frame, so their content is drawn in its reference coordinates and scaled uniformly into the
 * frame's content area (no element moved relative to another).
 * Not in the pack yet (left out): 대기 중인 파티 pill + people icon, clock / hourglass / arena icons on the cards,
 * 1·2·3위 medals, magnifier, 최근 10경기 bar icon. The close X reuses skills/close.
 */
public final class DashboardView {
    private DashboardView() {}
    public static final float W = 1672, H = 941;
    /** Content area inside the frame, under the tabs. */
    static final float AX = 150, AY = 333, AW = 1372, AH = 552;

    public enum Tab { LIVE, RANKING, RECORD }
    public record Who(UUID id, String nick, String skin) {}
    public record Live(String arena, int phase, int scoreA, int scoreB, int timeLeftMs, int spectators, List<Who> teamA, List<Who> teamB) {}
    public record Rank(int rank, UUID id, String nick, String skin, int rating, int tier, int games, int wins, int losses) {}
    public record Recent(int outcome, int team, int scoreA, int scoreB, int goals, int kills, int deaths) {}
    public record PlayerRec(UUID id, String nick, String skin, int rating, int tier, int rank, int games, int wins, int losses, int draws,
                         int goals, int kills, int deaths, float damage, float heal, int snitch, int deflectHits, int mvp, List<Recent> recent) {}
    public record Data(Tab tab, int waitingParties, List<Live> live, int liveScroll, List<Rank> ranking, Rank mine, int rankScroll,
                       PlayerRec record, boolean recordLoaded, String search, boolean searchFocused, boolean caret, String notice) {}

    public static final String CLOSE = "close", SEARCH = "search", SEARCH_FIELD = "search_field";
    public static String tabId(Tab t) { return "tab:" + t.ordinal(); }
    public static String spectateId(String arena) { return "spectate:" + arena; }
    public static final int LIVE_VISIBLE = 3;

    /**
     * Per tab: frame part, its rectangle, tab rectangles, title / close / waiting-count positions — each from that tab's layout.
     * dashboard/frame_live · frame_ranking · frame_record (empty windows) are requested from the owner; until they exist
     * every tab uses dashboard/main (the 경기 현황 window, cards baked in) and the 랭킹 / 기록 content is scaled into it.
     */
    record Chrome(String frame, float[] rect, float[][] tabs, float titleY, float titleSize, float[] close, float waitX, float waitY, float tabText) {}
    static final Chrome LIVE_CHROME = new Chrome("dashboard/frame_live", new float[]{118, 33, 1439, 866},
            new float[][]{{318, 251, 348, 72}, {666, 251, 343, 72}, {1012, 251, 343, 72}}, 172, 80, new float[]{1466, 120, 47, 45}, 1440, 190, 28);
    static final Chrome RANKING_CHROME = new Chrome("dashboard/frame_ranking", new float[]{0, 0, 1672, 941},
            new float[][]{{322, 178, 331, 55}, {663, 178, 344, 55}, {1016, 178, 332, 55}}, 128, 72, new float[]{1409, 89, 47, 45}, -1, 0, 25);
    static final Chrome RECORD_CHROME = new Chrome("dashboard/frame_record", new float[]{0, 0, 1672, 941},
            new float[][]{{496, 128, 221, 43}, {720, 128, 233, 43}, {955, 128, 227, 43}}, 90, 56, new float[]{1438, 81, 47, 45}, -1, 0, 20);

    static Chrome chromeOf(Tab t) { return t == Tab.LIVE ? LIVE_CHROME : t == Tab.RANKING ? RANKING_CHROME : RECORD_CHROME; }
    public static boolean ownFrame(Tab t) { return Parts.exists((t == Tab.LIVE ? LIVE_CHROME : t == Tab.RANKING ? RANKING_CHROME : RECORD_CHROME).frame()); }

    public static void paint(Pen p, Data d) {
        Chrome ch = d.tab() == Tab.LIVE || !ownFrame(d.tab()) ? LIVE_CHROME : d.tab() == Tab.RANKING ? RANKING_CHROME : RECORD_CHROME;
        if (Parts.exists(ch.frame())) p.image(ch.frame(), ch.rect()[0], ch.rect()[1], ch.rect()[2], ch.rect()[3]);
        else p.image("dashboard/main", 118, 33, 1439, 866);
        p.text("큐디치", 836, ch.titleY(), ch.titleSize(), Theme.GOLD, Face.TITLE, Canvas.CENTER);
        p.button(CLOSE, "skills/close", ch.close()[0], ch.close()[1], ch.close()[2], ch.close()[3], true);
        if (ch.waitX() > 0) p.text("대기 중인 파티  " + d.waitingParties(), ch.waitX(), ch.waitY(), 22, Theme.CREAM, Face.SERIF, Canvas.RIGHT);
        String[] names = {"경기 현황", "랭킹", "플레이어 기록"};
        for (Tab t : Tab.values()) {
            int i = t.ordinal(); boolean on = t == d.tab();
            float[] r = ch.tabs()[i];
            // the tab row is baked into every frame (main / frame_ranking / frame_record) with that frame's tab selected;
            // only the fallback (랭킹·기록 drawn on main) needs the selected tab redrawn
            if (ch == LIVE_CHROME && d.tab() != Tab.LIVE) p.image(on ? "dashboard/tab_selected" : "dashboard/tab_idle", r[0], r[1], r[2], r[3]);
            p.hit(tabId(t), r[0], r[1], r[2], r[3]);
            boolean over = !on && p.over(r[0], r[1], r[2], r[3]);
            p.text(names[i], r[0] + r[2] / 2, r[1] + r[3] / 2, ch.tabText(), on ? Theme.DARK_TEXT : over ? Theme.GOLD : Theme.CREAM, Face.SERIF, Canvas.CENTER);
        }
        switch (d.tab()) {
            case LIVE -> live(p, d);
            case RANKING -> { if (ownFrame(Tab.RANKING)) ranking(p, d, true); else { group(p, 205, 255, 1264, 635); ranking(p, d, false); p.pop(); } }
            case RECORD -> { if (ownFrame(Tab.RECORD)) record(p, d, true); else { group(p, 191, 187, 1293, 682); record(p, d, false); p.pop(); } }
        }
        if (d.notice() != null && !d.notice().isEmpty()) p.text(d.notice(), 836, d.tab() == Tab.LIVE ? 325 : 16, 17, 0xFFFFB0A8, Face.SANS, Canvas.CENTER);
    }

    /** Scale a reference rectangle uniformly into the content area, centred horizontally, top-aligned. */
    static void group(Pen p, float rx, float ry, float rw, float rh) {
        float s = Math.min(AW / rw, AH / rh);
        p.push(836 - (rx + rw / 2) * s, AY - ry * s, s);
    }

    // ================================================================== 경기 현황
    static void live(Pen p, Data d) {
        if (d.live().isEmpty()) { p.text("지금 진행 중인 경기가 없어요.", 836, 600, 26, Theme.MUTED, Face.SERIF, Canvas.CENTER); return; }
        for (int i = 0; i < LIVE_VISIBLE; i++) {
            int idx = d.liveScroll() + i;
            if (idx >= d.live().size()) break;
            var l = d.live().get(idx);
            float ct = 335 + i * 178;
            p.image("dashboard/match_card", 149, ct, 1372, 174);
            side(p, l.teamA(), 183, 290, ct, true);
            side(p, l.teamB(), 803, 908, ct, false);
            boolean prep = l.phase() == 0 || l.phase() == 1;
            p.text(prep ? "준비 중" : l.phase() == 3 ? "골!" : "진행 중", 642, ct + 40, 19, Theme.GOLD, Face.SERIF, Canvas.CENTER);
            if (prep) {
                p.image("status/setting_hourglass", 585, ct + 104, 26, 30);
                p.text("준비 " + Theme.time(l.timeLeftMs()), 620, ct + 120, 24, Theme.CREAM, Face.SERIF, Canvas.LEFT);
            } else {
                p.text(l.scoreA() + " : " + l.scoreB(), 642, ct + 86, 54, Theme.GOLD, Face.TITLE, Canvas.CENTER);
                p.text("남은 시간  " + Theme.time2(l.timeLeftMs()), 655, ct + 131, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
            }
            p.text("경기장", 1189, ct + 43, 15, Theme.GOLD, Face.SERIF, Canvas.CENTER);
            p.fit(l.arena(), 1189, ct + 112, 21, 150, Theme.WHITE, Face.SERIF, Canvas.CENTER);
            boolean full = l.spectators() >= 20;
            p.button(spectateId(l.arena()), "dashboard/spectate_button", 1297, ct + 51, 195, 82, !full);
            p.text(full ? "가득 참" : "관전", 1394, ct + 92, 28, full ? Theme.MUTED : Theme.CREAM, Face.TITLE, Canvas.CENTER);
        }
        if (d.live().size() > LIVE_VISIBLE)
            p.text((d.liveScroll() + 1) + "–" + Math.min(d.live().size(), d.liveScroll() + LIVE_VISIBLE) + " / " + d.live().size() + "  (휠)", 1500, 880, 15, Theme.MUTED, Face.SANS, Canvas.RIGHT);
    }

    private static void side(Pen p, List<Who> team, float bannerX, float nameX, float ct, boolean blue) {
        p.image(blue ? "result/banner_blue" : "result/banner_red", bannerX, ct + 28, 80, 91);
        String title = team.isEmpty() ? "" : team.get(0).nick() + " 파티";
        p.fit(title, nameX, ct + 48, 27, 200, Theme.WHITE, Face.SERIF, Canvas.LEFT);
        for (int k = 0; k < team.size() && k < 3; k++) p.head(team.get(k).id(), team.get(k).skin(), nameX + 2 + k * 63, ct + 80, 52);
    }

    // ================================================================== 랭킹 (ranking reference coordinates)
    public static final int RANK_ROWS_TOP = 3, RANK_ROWS_NORMAL = 4, RANK_ROWS_SCROLLED = 8;
    static void ranking(Pen p, Data d, boolean baked) {
        float[] cols = {339, 497, 651, 715, 1068, 1304};
        p.text("순위", 311, 272, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        p.text("티어", 497, 272, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        p.text("닉네임", 809, 272, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        p.text("레이팅", 1068, 272, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        p.text("승률", 1300, 272, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        var rows = d.ranking();
        if (rows.isEmpty()) p.text("아직 랭킹이 없어요.", 836, 520, 28, Theme.MUTED, Face.SERIF, Canvas.CENTER);
        if (d.rankScroll() == 0) {
            for (int i = 0; i < RANK_ROWS_TOP + RANK_ROWS_NORMAL && i < rows.size(); i++) {
                boolean top = i < RANK_ROWS_TOP;
                float y = top ? 291 + i * 85.5f : 550 + (i - RANK_ROWS_TOP) * 60;
                rankRow(p, rows.get(i), y, top, cols, !baked);
            }
        } else {
            // scrolled: same seven slots as the frame, every row in the plain style (the gold slots are covered)
            for (int i = 0; i < RANK_ROWS_TOP + RANK_ROWS_NORMAL && d.rankScroll() + i < rows.size(); i++) {
                boolean topSlot = i < RANK_ROWS_TOP;
                float y = topSlot ? 291 + i * 85.5f : 550 + (i - RANK_ROWS_TOP) * 60;
                if (topSlot) p.image("dashboard/normal_rank_row", 223, y, 1205, 89);
                rankRow(p, rows.get(d.rankScroll() + i), y, false, cols, !baked && !topSlot);
            }
        }
        // scroll bar
        p.image("dashboard/scrollbar_track", 1430, 274, 25, 512);
        int visible = RANK_ROWS_TOP + RANK_ROWS_NORMAL;
        if (rows.size() > visible) {
            float track = 512 - 30, thumb = Math.max(40, track * visible / rows.size());
            float at = (track - thumb) * d.rankScroll() / Math.max(1, rows.size() - visible);
            p.image("dashboard/scrollbar_thumb", 1430, 278 + Math.min(at, track - thumb), 25, thumb);
        } else p.image("dashboard/scrollbar_thumb", 1430, 278, 25, 482);
        // my row
        if (!baked) p.image("dashboard/my_rank_row", 205, 800, 1264, 87);
        var m = d.mine();
        p.text("내 순위", 328, 843, 24, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        if (m == null) { p.text("아직 랭크 경기 기록이 없어요", 836, 843, 22, Theme.MUTED, Face.SERIF, Canvas.CENTER); return; }
        p.text(m.rank() > 0 ? m.rank() + "위" : "-", 492, 843, 34, Theme.TEAL, Face.TITLE, Canvas.CENTER);
        p.image(Theme.tier(m.tier()), 612, 806, 74, 74);
        p.head(m.id(), m.skin(), 726, 815, 56);
        p.fit(m.nick(), 803, 843, 24, 200, Theme.WHITE, Face.SERIF, Canvas.LEFT);
        p.text(String.valueOf(m.rating()), 1068, 843, 30, Theme.TEAL, Face.TITLE, Canvas.CENTER);
        p.text(rate(m.wins(), m.games()), 1310, 843, 30, Theme.TEAL, Face.TITLE, Canvas.CENTER);
    }

    private static void rankRow(Pen p, Rank r, float y, boolean top, float[] cols, boolean drawRow) {
        float h = top ? 89 : 62, cy = y + h / 2;
        if (drawRow) p.image(top ? "dashboard/top_rank_row" : "dashboard/normal_rank_row", 223, y, 1205, h);
        p.text(String.valueOf(r.rank()), cols[0], cy, top ? 40 : 28, Theme.GOLD, Face.TITLE, Canvas.CENTER);
        float ts = top ? 80 : 58;
        p.image(Theme.tier(r.tier()), cols[1] - ts / 2, cy - ts / 2, ts, ts);
        float hs = top ? 58 : 50;
        p.head(r.id(), r.skin(), cols[2] - hs / 2, cy - hs / 2, hs);
        p.fit(r.nick(), cols[3], cy, top ? 24 : 22, 300, Theme.WHITE, Face.SERIF, Canvas.LEFT);
        p.text(String.valueOf(r.rating()), cols[4], cy, top ? 30 : 24, top ? Theme.GOLD : Theme.WHITE, Face.TITLE, Canvas.CENTER);
        p.text(rate(r.wins(), r.games()), cols[5], cy, top ? 30 : 24, top ? Theme.GOLD : Theme.WHITE, Face.TITLE, Canvas.CENTER);
    }
    static String rate(int wins, int games) { return games <= 0 ? "-" : Math.round(100.0 * wins / games) + "%"; }

    // ================================================================== 플레이어 기록 (record reference coordinates)
    static void record(Pen p, Data d, boolean baked) {
        if (!baked) p.image("dashboard/search_field", 208, 189, 472, 49);
        p.hit(SEARCH_FIELD, 208, 189, 472, 49);
        String s = d.search();
        if (s.isEmpty() && !d.searchFocused()) p.text("닉네임 검색", 262, 213, 19, Theme.MUTED, Face.SERIF, Canvas.LEFT);
        else {
            p.fit(s, 262, 213, 21, 400, Theme.WHITE, Face.SERIF, Canvas.LEFT);
            if (d.searchFocused() && d.caret()) { float cx = 262 + Math.min(400, p.width(s, 21, Face.SERIF)) + 2; p.c.fill(cx, 201, 2, 24, 0xFFF2D28A); }
        }
        if (baked) { if (p.over(686, 193, 118, 41)) p.image("dashboard/search_button_hover", 680, 187, 131, 53); p.hit(SEARCH, 686, 193, 118, 41); }
        else p.button(SEARCH, "dashboard/search_button", 680, 187, 131, 53, true);
        p.text("검색", 745, 213, 21, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        var r = d.record();
        if (!baked) p.image("dashboard/profile", 191, 242, 509, 237);
        float[] sx = {705, 893, 1088, 1290}; float[] sw = {176, 182, 189, 194};
        if (!baked) {
            for (int i = 0; i < 8; i++) p.image("dashboard/stat_card", sx[i % 4], i < 4 ? 241 : 358, sw[i % 4], 116);
            p.image("dashboard/recent_table", 193, 519, 1291, 350);
        }
        p.text("최근 10경기", 233, 500, 22, Theme.CREAM, Face.SERIF, Canvas.LEFT);
        String[] heads = {"결과", "점수", "내 골", "킬", "데스"};
        float[] hc = {325, 578, 843, 1089, 1345};
        for (int i = 0; i < 5; i++) p.text(heads[i], hc[i], 541, 19, Theme.CREAM, Face.SERIF, Canvas.CENTER);
        if (r == null) {
            p.text(d.recordLoaded() ? "기록이 없어요." : "불러오는 중…", 445, 360, 24, Theme.MUTED, Face.SERIF, Canvas.CENTER);
            return;
        }
        // profile
        p.head(r.id(), r.skin(), 219, 268, 112);
        p.fit(r.nick(), 362, 281, 34, 310, Theme.WHITE, Face.TITLE, Canvas.LEFT);
        p.image(Theme.tier(r.tier()), 352, 307, 118, 118);
        p.text(Theme.TIER_NAMES[Math.clamp(r.tier(), 0, 8)] + (r.rank() > 0 ? "  ·  " + r.rank() + "위" : ""), 490, 333, 22, Theme.CREAM, Face.SERIF, Canvas.LEFT);
        p.text(String.valueOf(r.rating()), 490, 369, 38, Theme.WHITE, Face.TITLE, Canvas.LEFT);
        String[] labels = {"경기", "승", "패", "무", "승률"};
        String[] values = {"" + r.games(), "" + r.wins(), "" + r.losses(), "" + r.draws(), rate(r.wins(), r.games())};
        float[] lx = {258, 351, 446, 537, 630};
        for (int i = 0; i < 5; i++) {
            p.text(labels[i], lx[i], 416, 20, Theme.CREAM, Face.SERIF, Canvas.CENTER);
            p.text(values[i], lx[i], 446, 30, Theme.WHITE, Face.TITLE, Canvas.CENTER);
        }
        // stats
        String[] icons = {"goal", "kill", "death", "damage", "heal", "snitch", "bludger", "mvp"};
        String[] names = {"골", "킬", "데스", "딜량", "파티원 힐량", "스니치 포획", "블러저 쳐내기 명중", "MVP 횟수"};
        String[] vals = {"" + r.goals(), "" + r.kills(), "" + r.deaths(), Theme.number(Math.round(r.damage())), Theme.number(Math.round(r.heal())),
                "" + r.snitch(), "" + r.deflectHits(), "" + r.mvp()};
        for (int i = 0; i < 8; i++) {
            float x = sx[i % 4], y = i < 4 ? 241 : 358, w = sw[i % 4];
            int[] size = Parts.size("statistics/" + icons[i]);
            float ih = 58, iw = ih * size[0] / size[1];
            p.image("statistics/" + icons[i], x + 50 - iw / 2, y + 58 - ih / 2, iw, ih);
            float cx = x + 66 + (w - 66) / 2;
            p.fit(names[i], cx, y + 37, 16, w - 70, Theme.CREAM, Face.SERIF, Canvas.CENTER);
            p.fit(vals[i], cx, y + 74, 30, w - 96, Theme.WHITE, Face.TITLE, Canvas.CENTER);
        }
        // recent games
        for (int i = 0; i < r.recent().size() && i < 10; i++) {
            var g = r.recent().get(i);
            float cy = 574 + i * 29.6f;
            String badge = g.outcome() == 1 ? "win_badge" : g.outcome() == 2 ? "draw_badge" : "loss_badge";
            p.image("dashboard/" + badge, 275, cy - 14, 102, 28);
            p.text(g.outcome() == 1 ? "승리" : g.outcome() == 2 ? "무승부" : "패배", 326, cy, 16, Theme.WHITE, Face.SERIF, Canvas.CENTER);
            int mine = g.team() == 0 ? g.scoreA() : g.scoreB(), them = g.team() == 0 ? g.scoreB() : g.scoreA();
            p.text(mine + " : " + them, 578, cy, 19, Theme.WHITE, Face.SERIF, Canvas.CENTER);
            p.text(String.valueOf(g.goals()), 843, cy, 19, Theme.WHITE, Face.SERIF, Canvas.CENTER);
            p.text(String.valueOf(g.kills()), 1089, cy, 19, Theme.WHITE, Face.SERIF, Canvas.CENTER);
            p.text(String.valueOf(g.deaths()), 1345, cy, 19, Theme.WHITE, Face.SERIF, Canvas.CENTER);
        }
        if (r.recent().isEmpty()) p.text("아직 경기 기록이 없어요.", 836, 700, 22, Theme.MUTED, Face.SERIF, Canvas.CENTER);
    }
}
