package school.magiccodex.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import school.magiccodex.protocol.KyudichiProtocol;
import school.magiccodex.protocol.KyudichiQueueProtocol;

/**
 * /큐디치미리보기 — 서버·다른 사람 없이 큐디치 화면을 가짜 데이터로 띄워 보는 UI 테스트용 (클라이언트 전용, 아무것도 서버로 안 보냄).
 * 준비 · 경기 · 결과 · 수락 · 매칭 · 대시보드 · 끄기. 미리보기 중에는 매칭/수락/준비 버튼이 화면 안에서만 반응한다.
 */
final class KyudichiPreview {
    private KyudichiPreview() {}
    enum Mode { NONE, PREPARE, MATCH, QUEUE }
    private static Mode mode = Mode.NONE;
    private static long until;
    private static final UUID[] FAKE = new UUID[5];
    private static final String[] NAMES = {"루나", "하린", "서하", "이안", "유진"};
    private static boolean ready;
    private static final boolean[] fakeReady = {true, false, true, false, false};

    static boolean active() { return mode != Mode.NONE; }

    static void initialize() {
        for (int i = 0; i < FAKE.length; i++) FAKE[i] = new UUID(0x6b79756469636869L, i + 1);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("큐디치미리보기")
                    .executes(c -> { c.getSource().sendFeedback(Text.literal("/큐디치미리보기 신청 · 매칭 · 수락 · 준비 · 경기 · 결과 · 대시보드 · 끄기  (가짜 데이터, 서버로 아무것도 안 보냄)")); return 1; });
            root.then(ClientCommandManager.literal("신청").executes(c -> { application(); return 1; }));
            root.then(ClientCommandManager.literal("준비").executes(c -> { prepare(); return 1; }));
            root.then(ClientCommandManager.literal("경기").executes(c -> { match(); return 1; }));
            root.then(ClientCommandManager.literal("결과").executes(c -> { result(); return 1; }));
            root.then(ClientCommandManager.literal("수락").executes(c -> { queue(KyudichiQueueProtocol.S_OFFER); return 1; }));
            root.then(ClientCommandManager.literal("매칭").executes(c -> { queue(KyudichiQueueProtocol.S_SEARCHING); return 1; }));
            root.then(ClientCommandManager.literal("대시보드").executes(c -> { dashboard(); return 1; }));
            root.then(ClientCommandManager.literal("끄기").executes(c -> { stop(); c.getSource().sendFeedback(Text.literal("미리보기를 껐어요.")); return 1; }));
            dispatcher.register(root);
        });
        ClientTickEvents.END_CLIENT_TICK.register(KyudichiPreview::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> mode = Mode.NONE);
    }

    private static UUID me() { var p = MinecraftClient.getInstance().player; return p == null ? new UUID(0, 0) : p.getUuid(); }
    /** MagicCodex 한글 닉네임 (the account name only before the server sent it). */
    private static String myName() { var p = MinecraftClient.getInstance().player; return SchoolClient.nickname(p == null ? "나" : p.getName().getString()); }

    // ------------------------------------------------------------------ fake data
    private static KyudichiProtocol.Match fakeMatch(int phase, int timeLeftMs) {
        var list = new ArrayList<KyudichiProtocol.Person>();
        list.add(new KyudichiProtocol.Person(me(), myName(), 0, ready ? 4 : 0));
        for (int i = 0; i < 5; i++) list.add(new KyudichiProtocol.Person(FAKE[i], NAMES[i], i < 2 ? 0 : 1, phase == 0 && fakeReady[i] ? 4 : 0));
        return new KyudichiProtocol.Match(phase, 0, phase == 0 ? 0 : 30, phase == 0 ? 0 : 20, timeLeftMs, phase == 0 ? 0 : 1, "미리보기", List.copyOf(list));
    }

    private static void prepare() {
        KyudichiClient.reset(); mode = Mode.PREPARE; ready = false; until = Util.getMeasuringTimeMs() + 60_000;
        KyudichiClient.receive(fakeMatch(0, 60_000));
        var entries = new ArrayList<KyudichiProtocol.Entry>();
        entries.add(new KyudichiProtocol.Entry(me(), 0, "", 1250));
        int[] houses = {1, 2, 0, 3, 1}; int[] ratings = {1280, 1220, 1260, 1240, 1250};
        for (int i = 0; i < 5; i++) entries.add(new KyudichiProtocol.Entry(FAKE[i], houses[i], "", ratings[i]));
        KyudichiClient.receive(new KyudichiProtocol.Roster(entries));
        MinecraftClient.getInstance().setScreen(new KyudichiPrepareScreen());
    }

    private static void match() {
        KyudichiClient.reset(); mode = Mode.MATCH; until = Util.getMeasuringTimeMs() + 60_000;
        KyudichiClient.receive(fakeMatch(2, 372_000));
        var p = MinecraftClient.getInstance().player;
        if (p != null) {
            var e = p.getPos(); var look = p.getRotationVec(1f);
            KyudichiClient.receive(new KyudichiProtocol.Shrines(List.of(
                    new KyudichiProtocol.Shrine(0, (float) (e.x + look.x * 12 + 6), (float) e.y + 4, (float) (e.z + look.z * 12)),
                    new KyudichiProtocol.Shrine(3, (float) (e.x - look.x * 10), (float) e.y + 6, (float) (e.z - look.z * 10)))));
        }
        KyudichiClient.receive(new KyudichiProtocol.Feed(1, 0, myName(), "", 10));
        KyudichiClient.receive(new KyudichiProtocol.Feed(2, 0, "루나", "이안", 3));
        KyudichiClient.receive(new KyudichiProtocol.Feed(1, 1, "서하", "", 10));
        KyudichiClient.receive(new KyudichiProtocol.Key(1, 0, 0));
    }

    private static void result() {
        stop();
        var lines = new ArrayList<KyudichiProtocol.Line>();
        lines.add(new KyudichiProtocol.Line(me(), myName(), 0, 3, 2, 0, 0, true));
        for (int i = 0; i < 5; i++) lines.add(new KyudichiProtocol.Line(FAKE[i], NAMES[i], i < 2 ? 0 : 1, 2 - i % 2, 1 + i % 3, i % 2, i == 0 ? 1 : 0, false));
        KyudichiClient.receive(new KyudichiProtocol.Result(1, 80, 50, 18, 1268, 3, 3, List.copyOf(lines)));
    }

    private static void queue(int state) {
        stop(); mode = Mode.QUEUE; until = Util.getMeasuringTimeMs() + 60_000;
        KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(state, 42, state == KyudichiQueueProtocol.S_OFFER ? 10 : 0, 2, 6, false, 4));
    }

    private static void application() {
        stop(); mode = Mode.QUEUE; until = Util.getMeasuringTimeMs() + 600_000;
        KyudichiQueueClient.receive(apply());
    }
    private static KyudichiQueueProtocol.Apply apply() {
        return new KyudichiQueueProtocol.Apply(1250, 18, 12, 1280, true, "파티장만 시작할 수 있습니다.", List.of(
                new KyudichiQueueProtocol.Member(me(), myName(), "", 0, 1250, true),
                new KyudichiQueueProtocol.Member(FAKE[0], NAMES[0], "", 1, 1280, false),
                new KyudichiQueueProtocol.Member(FAKE[1], NAMES[1], "", 2, 1220, false)));
    }

    private static void dashboard() {
        stop(); mode = Mode.QUEUE; until = Util.getMeasuringTimeMs() + 600_000;
        KyudichiQueueClient.dash = dash(); KyudichiQueueClient.ranking = ranking(); KyudichiQueueClient.record = record(myName());
        MinecraftClient.getInstance().setScreen(new KyudichiDashboardScreen(school.magiccodex.client.kui.DashboardView.Tab.LIVE));
    }
    private static KyudichiQueueProtocol.Dash dash() {
        return new KyudichiQueueProtocol.Dash(4, List.of(
                new KyudichiQueueProtocol.LiveCard("별빛 경기장", 2, 30, 20, 372_000, 3, team(-1, 0, 1), team(2, 3, 4)),
                new KyudichiQueueProtocol.LiveCard("하늘 경기장", 2, 50, 40, 228_000, 20, team(0, 1, -1), team(3, 2, 4)),
                new KyudichiQueueProtocol.LiveCard("새벽 경기장", 0, 0, 0, 32_000, 0, team(1, -1, 0), team(4, 3, 2))));
    }
    /** -1 = me, 0..4 = the fake players. */
    private static List<KyudichiQueueProtocol.Who> team(int... who) {
        var l = new ArrayList<KyudichiQueueProtocol.Who>();
        for (int i : who) l.add(i < 0 ? new KyudichiQueueProtocol.Who(me(), myName(), "") : new KyudichiQueueProtocol.Who(FAKE[i], NAMES[i], ""));
        return l;
    }
    private static KyudichiQueueProtocol.Ranking ranking() {
        var top = new ArrayList<KyudichiQueueProtocol.Rank>();
        String[] n = {"도윤", "서하", "루나", "이안", "유진", "하린", "지우"};
        int[] r = {1920, 1884, 1812, 1764, 1720, 1688, 1624};
        for (int i = 0; i < 40; i++) {
            int rating = i < 7 ? r[i] : 1600 - i * 12;
            int tier = i < 3 ? 6 + i : rating >= 1700 ? 5 : rating >= 1500 ? 4 : rating >= 1300 ? 3 : 2;
            top.add(new KyudichiQueueProtocol.Rank(i + 1, new UUID(7, i), i < 7 ? n[i] : "플레이어" + (i + 1), "", rating, tier, 50, 30, 20));
        }
        return new KyudichiQueueProtocol.Ranking(List.copyOf(top), new KyudichiQueueProtocol.Rank(47, me(), myName(), "", 1342, 3, 50, 27, 21));
    }
    private static KyudichiQueueProtocol.RecordReply record(String nick) {
        var recent = new ArrayList<KyudichiQueueProtocol.Recent>();
        int[][] g = {{1, 80, 50}, {0, 40, 60}, {1, 70, 30}, {0, 20, 50}, {1, 90, 70}, {0, 30, 60}, {1, 60, 40}, {2, 50, 50}, {1, 80, 60}, {0, 30, 40}};
        long now = System.currentTimeMillis();
        for (int i = 0; i < g.length; i++) recent.add(new KyudichiQueueProtocol.Recent(now - i * 3_600_000L, g[i][0], 0, g[i][1], g[i][2], 3 - i % 3, 2 + i % 4, 2 + i % 3, i == 0, g[i][0] == 1 ? 18 : g[i][0] == 2 ? 0 : -15));
        return new KyudichiQueueProtocol.RecordReply(new KyudichiQueueProtocol.PlayerRecord(me(), nick, "", 1342, 3, 47, 50, 27, 21, 2, 128, 96, 72, 40, 9, 147, 12,
                24_580f, 8_240f, List.copyOf(recent)));
    }

    // ------------------------------------------------------------------ actions inside the preview (instead of the network)
    /** KyudichiQueueClient.send while previewing: answer locally. */
    static void queueAction(KyudichiQueueProtocol.FromClient m) {
        switch (m) {
            case KyudichiQueueProtocol.Join x -> KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(KyudichiQueueProtocol.S_SEARCHING, 0, 0, 0, 0, false, 4));
            case KyudichiQueueProtocol.Leave x -> KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(0, 0, 0, 0, 0, false, 0));
            case KyudichiQueueProtocol.Decline x -> KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(0, 0, 0, 0, 0, false, 0));
            case KyudichiQueueProtocol.Accept x -> KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(KyudichiQueueProtocol.S_OFFER, 0, 6, 3, 6, true, 4));
            case KyudichiQueueProtocol.DashRequest x -> KyudichiQueueClient.receive(dash());
            case KyudichiQueueProtocol.RankingRequest x -> KyudichiQueueClient.receive(ranking());
            case KyudichiQueueProtocol.RecordRequest x -> KyudichiQueueClient.receive(record(x.nick().isBlank() ? myName() : x.nick()));
            case KyudichiQueueProtocol.ApplyRequest x -> KyudichiQueueClient.receive(apply());
            default -> MinecraftClient.getInstance().player.sendMessage(Text.literal("미리보기에서는 실제로 이동하지 않아요."), true);
        }
    }
    /** Prepare screen ready button while previewing. */
    static void toggleReady() { ready = !ready; KyudichiClient.receive(fakeMatch(0, (int) Math.max(1000, until - Util.getMeasuringTimeMs()))); }

    static void stop() {
        if (mode == Mode.PREPARE || mode == Mode.MATCH) KyudichiClient.reset();
        if (mode == Mode.QUEUE) {
            KyudichiQueueClient.receive(new KyudichiQueueProtocol.Status(0, 0, 0, 0, 0, false, 0));
            // fake 현황 / 랭킹 / 기록 / 신청 data must not stay behind for the real windows
            KyudichiQueueClient.dash = null; KyudichiQueueClient.ranking = null; KyudichiQueueClient.record = null; KyudichiQueueClient.apply = null;
        }
        mode = Mode.NONE;
    }
    /** A real packet from the arena server ends a 준비 / 경기 preview. */
    static void realArena() { if (mode == Mode.PREPARE || mode == Mode.MATCH) stop(); }
    /** Real 현황 · 랭킹 · 기록 · 신청 data, or a real queue state, from the proxy ends a queue / dashboard preview. */
    static void realQueue(KyudichiQueueProtocol.ToClient m) {
        if (mode != Mode.QUEUE) return;
        if (m instanceof KyudichiQueueProtocol.Status st && st.state() == 0) return;
        stop();
    }

    /** Keeps the fake match alive (the HUD drops a match after 4 s without packets) and moves the balls around the player. */
    private static void tick(MinecraftClient c) {
        if (mode == Mode.NONE) return;
        long now = Util.getMeasuringTimeMs();
        if (now > until || c.player == null) { stop(); return; }
        if (now % 1000 < 50 || KyudichiClient.match == null) {
            int left = (int) (until - now);
            if (mode == Mode.PREPARE) KyudichiClient.receive(fakeMatch(0, left));
            if (mode == Mode.MATCH) KyudichiClient.receive(fakeMatch(2, 312_000 + left));
        }
        if (mode == Mode.MATCH) {
            var e = c.player.getPos(); double t = now / 1000.0;
            KyudichiClient.receive(new KyudichiProtocol.Balls(List.of(
                    new KyudichiProtocol.Ball(0, 0, (float) (e.x + Math.cos(t) * 8), (float) e.y + 3, (float) (e.z + Math.sin(t) * 8), 0),
                    new KyudichiProtocol.Ball(1, 0, (float) (e.x - Math.cos(t * .7) * 14), (float) e.y + 5, (float) (e.z + Math.sin(t * .7) * 14), 2),
                    new KyudichiProtocol.Ball(1, 1, (float) (e.x + 30), (float) e.y + 2, (float) (e.z - 20), 0),
                    new KyudichiProtocol.Ball(2, 0, (float) (e.x + Math.sin(t * 1.3) * 25), (float) e.y + 10, (float) (e.z - 30), 0))));
            int action = (int) ((now / 2500) % 5);
            if (action != KyudichiClient.key.action()) KyudichiClient.receive(new KyudichiProtocol.Key(action, 0, action == 2 ? 7000 : 0));
        }
    }
}
