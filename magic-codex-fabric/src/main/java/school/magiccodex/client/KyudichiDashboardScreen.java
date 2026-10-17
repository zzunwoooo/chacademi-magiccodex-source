package school.magiccodex.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import school.magiccodex.protocol.KyudichiQueueProtocol;
import school.magiccodex.protocol.KyudichiQueueProtocol.*;

/**
 * 큐디치 대시보드 (/큐디치): 진행 중 경기 (파티 vs 파티, 관전 버튼) · 랭킹 100 + 내 순위 · 전적 (나 / 닉네임 검색, 최근 10경기).
 * 아래: 매칭 신청/취소 (대기열은 대기 파티 수만), 연습장. Data comes from the proxy (KyudichiQueueClient).
 */
final class KyudichiDashboardScreen extends SocialScreen {
    enum Tab { LIVE, RANKING, RECORD }
    static final float W = 1200, H = 942;
    private Tab tab;
    private int scroll; private long refreshAt;
    private final Input search = new Input("닉네임", 16, "");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    KyudichiDashboardScreen(Tab tab) { super("큐디치"); this.tab = tab; }

    void show(Tab t) { tab = t; scroll = 0; }

    @Override SocialLayout.Fit fit() { float s = Math.min(width * .92f / W, height * .94f / H); return new SocialLayout.Fit((width - W * s) / 2, (height - H * s) / 2, s); }

    @Override protected void init() {
        super.init();
        if (KyudichiQueueClient.dash == null) KyudichiQueueClient.requestDash();
        if (tab == Tab.RANKING && KyudichiQueueClient.ranking == null) KyudichiQueueClient.requestRanking();
        if (tab == Tab.RECORD && KyudichiQueueClient.record == null) KyudichiQueueClient.requestRecord("");
    }

    @Override public void tick() {
        super.tick();
        long now = Util.getMeasuringTimeMs();
        if (tab == Tab.LIVE && now > refreshAt) { refreshAt = now + 3000; KyudichiQueueClient.requestDash(); }
    }

    // ================================================================== draw
    private float mx, my;
    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x88060B12);
        var f = fit(); mx = (float) f.x(mouseX); my = (float) f.y(mouseY);
        start(c);
        try {
            KyudichiArt.draw(images, c, KyudichiArt.PANELS, KyudichiArt.P_FOOTER, 0, 0, W, H, 0xFFFFFFFF);
            label(c, "큐디치", 130, 102, 40, GOLD, true);
            tabs(c);
            switch (tab) { case LIVE -> live(c); case RANKING -> ranking(c); case RECORD -> record(c); }
            footer(c);
            toast(c, 300, 860, 600);
        } finally { end(c); }
    }

    private static final String[] TAB_NAMES = {"진행 중 경기", "랭킹", "전적"};
    private void tabs(DrawContext c) {
        for (int i = 0; i < 3; i++) {
            float x = 470 + i * 230, y = 70, w = 214, h = 64;
            boolean on = tab.ordinal() == i, hover = in(mx, my, x, y, w, h);
            KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, on ? KyudichiArt.BTN_GLOW : hover ? KyudichiArt.BTN : KyudichiArt.BTN_PLAIN, x, y, w, h, 0xFFFFFFFF);
            center(c, TAB_NAMES[i], x + w / 2, y + h / 2, 21, on ? 0xFFFFF0C8 : hover ? CYAN : MUTED);
        }
    }

    // ------------------------------------------------------------------ live matches
    private static final float LX = 70, LY = 196, LW = 1060, ROW_H = 92;
    private void live(DrawContext c) {
        var d = KyudichiQueueClient.dash;
        label(c, "대기 중인 파티 " + (d == null ? "-" : d.queuedParties()) + "개", LX + 6, LY + 14, 22, MUTED, false);
        if (d == null) { center(c, "불러오는 중…", W / 2, 470, 24, MUTED); return; }
        if (d.live().isEmpty()) { center(c, "지금 진행 중인 경기가 없어요.", W / 2, 470, 24, MUTED); return; }
        int i = 0;
        for (var l : d.live()) {
            if (i >= 6) break;
            float y = LY + 44 + i * (ROW_H + 10);
            row(c, LX, y, LW, ROW_H, false);
            label(c, l.arena(), LX + 24, y + 26, 18, MUTED, false);
            label(c, phase(l), LX + 24, y + 62, 18, GOLD, false);
            fitted(c, l.teamA(), LX + 170, y + ROW_H / 2, 21, 300, KyudichiHud.BLUE, true);
            center(c, l.scoreA() + "  :  " + l.scoreB(), LX + 560, y + ROW_H / 2, 34, WHITE);
            float tw = Math.min(300, type.width(l.teamB(), 21, BOLD));
            fitted(c, l.teamB(), LX + 950 - Math.max(tw, 1) - 40, y + ROW_H / 2, 21, 300, KyudichiHud.RED, true);
            float bx = LX + LW - 130, by = y + 18, bw = 110, bh = ROW_H - 36;
            boolean full = l.spectators() >= 20, hover = !full && in(mx, my, bx, by, bw, bh);
            KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, full ? KyudichiArt.BTN_OFF : hover ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN, bx, by, bw, bh, 0xFFFFFFFF);
            center(c, full ? "가득" : "관전", bx + bw / 2, by + bh / 2, 20, full ? MUTED : hover ? CYAN : GOLD);
            center(c, l.spectators() + "/20", bx - 46, y + ROW_H / 2, 16, MUTED);
            i++;
        }
    }
    private static String phase(Live l) {
        int s = l.timeLeftMs() / 1000;
        return switch (l.phase()) { case 0 -> "준비 중"; case 1 -> "곧 시작"; case 3 -> "골!"; default -> String.format("%d:%02d", s / 60, s % 60); };
    }

    // ------------------------------------------------------------------ ranking
    private static final int VISIBLE = 11;
    private void ranking(DrawContext c) {
        var r = KyudichiQueueClient.ranking;
        if (r == null) { center(c, "불러오는 중…", W / 2, 470, 24, MUTED); return; }
        header(c, LY + 14);
        if (r.top().isEmpty()) { center(c, "아직 랭킹이 없어요.", W / 2, 470, 24, MUTED); }
        scroll = Math.clamp(scroll, 0, Math.max(0, r.top().size() - VISIBLE));
        for (int i = 0; i < VISIBLE && scroll + i < r.top().size(); i++) rankRow(c, r.top().get(scroll + i), LY + 40 + i * 50, false);
        if (r.top().size() > VISIBLE) {
            float track = VISIBLE * 50, knob = track * VISIBLE / r.top().size(), at = (track - knob) * scroll / Math.max(1, r.top().size() - VISIBLE);
            HudMesh.quad(c, LX + LW + 8, LY + 40, 4, track, 0x55A5B8C8, 0x55A5B8C8);
            HudMesh.quad(c, LX + LW + 7, LY + 40 + at, 6, knob, 0xCCE7CE91, 0xCCE7CE91);
        }
        if (r.mine() != null) rankRow(c, r.mine(), LY + 40 + VISIBLE * 50 + 14, true);
        else center(c, "아직 랭크 경기 기록이 없어요.", W / 2, LY + 40 + VISIBLE * 50 + 38, 19, MUTED);
    }
    private void header(DrawContext c, float y) {
        label(c, "순위", LX + 20, y, 17, MUTED, false); label(c, "티어", LX + 120, y, 17, MUTED, false); label(c, "이름", LX + 300, y, 17, MUTED, false);
        label(c, "레이팅", LX + 640, y, 17, MUTED, false); label(c, "전적", LX + 820, y, 17, MUTED, false);
    }
    private void rankRow(DrawContext c, Rank r, float y, boolean mine) {
        row(c, LX, y, LW, 46, mine);
        int color = r.rank() <= 3 ? GOLD : WHITE;
        label(c, r.rank() > 0 ? r.rank() + "" : "-", LX + 24, y + 23, 21, color, true);
        images.drawCentered(c, KyudichiArt.tierIcon(r.tier()), LX + 136, y + 23, 38, 0xFFFFFFFF);
        label(c, KyudichiArt.TIER_NAMES[Math.clamp(r.tier(), 0, 8)], LX + 162, y + 23, 17, MUTED, false);
        fitted(c, r.nick() + (mine ? "  (나)" : ""), LX + 300, y + 23, 21, 320, mine ? CYAN : WHITE, false);
        label(c, String.valueOf(r.rating()), LX + 640, y + 23, 21, color, true);
        label(c, r.wins() + "승 " + r.losses() + "패 · " + r.games() + "경기", LX + 820, y + 23, 18, MUTED, false);
    }

    // ------------------------------------------------------------------ record
    private void record(DrawContext c) {
        float sx = LX, sy = LY + 2, sw = 420, sh = 50;
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, KyudichiArt.FIELD, sx - 6, sy - 6, sw + 12, sh + 12, 0xFFFFFFFF);
        search.draw(c, sx, sy, sw, sh, "닉네임으로 검색 (Enter)");
        float bx = sx + sw + 20; boolean hb = in(mx, my, bx, sy - 4, 120, sh + 8);
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, hb ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN, bx, sy - 4, 120, sh + 8, 0xFFFFFFFF);
        center(c, "검색", bx + 60, sy + sh / 2, 20, hb ? CYAN : GOLD);
        float mb = bx + 140; boolean hm = in(mx, my, mb, sy - 4, 120, sh + 8);
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, hm ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN, mb, sy - 4, 120, sh + 8, 0xFFFFFFFF);
        center(c, "내 전적", mb + 60, sy + sh / 2, 20, hm ? CYAN : GOLD);
        var reply = KyudichiQueueClient.record;
        if (reply == null) { center(c, "불러오는 중…", W / 2, 470, 24, MUTED); return; }
        var r = reply.record();
        if (r == null) { center(c, "기록이 없어요.", W / 2, 470, 24, MUTED); return; }
        float y = sy + 86;
        images.drawCentered(c, KyudichiArt.tierIcon(r.tier()), LX + 54, y + 40, 92, 0xFFFFFFFF);
        label(c, r.nick(), LX + 120, y + 18, 32, WHITE, true);
        label(c, KyudichiArt.TIER_NAMES[Math.clamp(r.tier(), 0, 8)] + " · " + r.rating() + (r.rank() > 0 ? " · " + r.rank() + "위" : ""), LX + 120, y + 60, 22, GOLD, false);
        label(c, r.games() + "경기 " + r.wins() + "승 " + r.losses() + "패 " + r.draws() + "무", LX + 640, y + 40, 22, WHITE, false);
        String[] names = {"골", "처치", "데스", "도움", "딜량", "파티 힐", "스니치", "블러저 명중", "MVP", "승률"};
        String[] values = {"" + r.goals(), "" + r.kills(), "" + r.deaths(), "" + r.assists(), "" + Math.round(r.damage()), "" + Math.round(r.heal()), "" + r.snitch(),
                "" + r.deflectHits(), "" + r.mvp(), r.games() == 0 ? "-" : Math.round(100.0 * r.wins() / r.games()) + "%"};
        for (int i = 0; i < names.length; i++) {
            float cx = LX + (i % 5) * 212, cy = y + 112 + (i / 5) * 76;
            row(c, cx, cy, 200, 66, false);
            center(c, values[i], cx + 100, cy + 26, 24, WHITE);
            center(c, names[i], cx + 100, cy + 52, 15, MUTED);
        }
        float ry = y + 278;
        label(c, "최근 10경기", LX + 6, ry, 19, MUTED, false);
        int i = 0;
        for (var g : r.recent()) {
            float gx = LX + (i % 2) * 535, gy = ry + 22 + (i / 2) * 46;
            String out = switch (g.outcome()) { case 1 -> "승"; case 2 -> "무"; default -> "패"; };
            int col = g.outcome() == 1 ? 0xFF8FE3A0 : g.outcome() == 2 ? MUTED : 0xFFFF9C9C;
            row(c, gx, gy, 520, 40, false);
            label(c, out, gx + 14, gy + 20, 20, col, true);
            label(c, WHEN.format(Instant.ofEpochMilli(g.endedAt())), gx + 50, gy + 20, 15, MUTED, false);
            label(c, (g.team() == 0 ? "파랑 " : "빨강 ") + g.scoreA() + ":" + g.scoreB(), gx + 170, gy + 20, 17, g.team() == 0 ? KyudichiHud.BLUE : KyudichiHud.RED, false);
            label(c, "골 " + g.goals() + " · 처치 " + g.kills() + " · 데스 " + g.deaths() + (g.mvp() ? " · MVP" : ""), gx + 290, gy + 20, 15, WHITE, false);
            String d = (g.delta() > 0 ? "+" : "") + g.delta();
            label(c, d, gx + 476, gy + 20, 17, g.delta() > 0 ? 0xFF8FE3A0 : g.delta() < 0 ? 0xFFFF9C9C : MUTED, true);
            i++;
        }
    }

    // ------------------------------------------------------------------ footer
    private static final float FY = 830;
    private void footer(DrawContext c) {
        int st = KyudichiQueueClient.state();
        boolean queued = st == KyudichiQueueProtocol.S_SEARCHING || st == KyudichiQueueProtocol.S_OFFER;
        boolean busy = st == KyudichiQueueProtocol.S_MOVING || st == KyudichiQueueProtocol.S_PLAYING || KyudichiClient.active() && !KyudichiClient.spectator();
        float bx = W - 70 - 260, bw = 260, bh = 70;
        boolean hover = !busy && in(mx, my, bx, FY, bw, bh);
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, busy ? KyudichiArt.BTN_OFF : queued ? KyudichiArt.BTN_RED : hover ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN, bx, FY, bw, bh, 0xFFFFFFFF);
        center(c, busy ? "경기 중" : queued ? "매칭 취소" : "매칭 신청", bx + bw / 2, FY + bh / 2, 24, busy ? MUTED : queued ? 0xFFFFD6D6 : hover ? CYAN : GOLD);
        float px = bx - 200; boolean hp = !busy && !queued && in(mx, my, px, FY + 8, 180, bh - 16);
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, busy || queued ? KyudichiArt.BTN_OFF : hp ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN_PLAIN, px, FY + 8, 180, bh - 16, 0xFFFFFFFF);
        center(c, "연습장", px + 90, FY + bh / 2, 20, busy || queued ? MUTED : hp ? CYAN : GOLD);
        String hint = queued ? "매칭 중이에요. 기다리는 동안 다른 곳에 있어도 돼요." : "3인 파티는 파티장이, 혼자면 랜덤 팀으로 신청해요.";
        fitted(c, hint, 80, FY + bh / 2, 18, 520, MUTED, false);
    }

    private void row(DrawContext c, float x, float y, float w, float h, boolean mine) {
        chamfer(c, x, y, w, h, Math.min(10, h / 4), mine ? 0xCC8FE3D2 : 0x99586F86);
        chamfer(c, x + 1.5f, y + 1.5f, w - 3, h - 3, Math.min(8, h / 4), mine ? 0xF0123A44 : 0xEA0B1A2C);
    }

    // ================================================================== input
    @Override public boolean mouseClicked(double x, double y, int button) {
        var f = fit(); float px = (float) f.x(x), py = (float) f.y(y);
        if (button != 0) return super.mouseClicked(x, y, button);
        for (int i = 0; i < 3; i++) if (in(px, py, 470 + i * 230, 70, 214, 64)) {
            Tab next = Tab.values()[i];
            if (next != tab) { sound(1.2f, .15f); show(next); }
            if (next == Tab.LIVE) KyudichiQueueClient.requestDash();
            if (next == Tab.RANKING) KyudichiQueueClient.requestRanking();
            if (next == Tab.RECORD && KyudichiQueueClient.record == null) KyudichiQueueClient.requestRecord("");
            return true;
        }
        if (tab == Tab.LIVE && KyudichiQueueClient.dash != null) {
            int i = 0;
            for (var l : KyudichiQueueClient.dash.live()) {
                if (i >= 6) break;
                float rowY = LY + 44 + i * (ROW_H + 10), bx = LX + LW - 130;
                if (l.spectators() < 20 && in(px, py, bx, rowY + 18, 110, ROW_H - 36)) { sound(1.3f, .2f); KyudichiQueueClient.send(new SpectateRequest(l.arena())); close(); return true; }
                i++;
            }
        }
        if (tab == Tab.RECORD) {
            float sx = LX, sy = LY + 2, sw = 420, sh = 50, bx = sx + sw + 20;
            if (search.click(px, py)) return true;
            if (in(px, py, bx, sy - 4, 120, sh + 8)) { doSearch(); return true; }
            if (in(px, py, bx + 140, sy - 4, 120, sh + 8)) { sound(1.2f, .15f); KyudichiQueueClient.requestRecord(""); return true; }
        }
        int st = KyudichiQueueClient.state();
        boolean queued = st == KyudichiQueueProtocol.S_SEARCHING || st == KyudichiQueueProtocol.S_OFFER;
        boolean busy = st == KyudichiQueueProtocol.S_MOVING || st == KyudichiQueueProtocol.S_PLAYING || KyudichiClient.active() && !KyudichiClient.spectator();
        float bx = W - 70 - 260;
        if (!busy && in(px, py, bx, FY, 260, 70)) { sound(1.3f, .2f); KyudichiQueueClient.send(queued ? new Leave() : new Join()); return true; }
        if (!busy && !queued && in(px, py, bx - 200, FY + 8, 180, 54)) { sound(1.3f, .2f); KyudichiQueueClient.send(new PracticeRequest()); close(); return true; }
        return super.mouseClicked(x, y, button);
    }

    private void doSearch() {
        String nick = search.text().strip();
        if (nick.isEmpty()) { notice("닉네임을 입력해 주세요."); return; }
        sound(1.2f, .15f); KyudichiQueueClient.requestRecord(nick);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (tab == Tab.RANKING) { scroll -= (int) Math.signum(vertical) * 3; return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean charTyped(char ch, int mods) { return tab == Tab.RECORD && search.focus() ? search.typed(ch, mods) : super.charTyped(ch, mods); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (tab == Tab.RECORD && search.focus()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { doSearch(); return true; }
            if (key != GLFW.GLFW_KEY_ESCAPE) return search.key(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }
}
