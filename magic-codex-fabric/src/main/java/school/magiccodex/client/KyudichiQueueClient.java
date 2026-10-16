package school.magiccodex.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.KyudichiQueueProtocol;
import school.magiccodex.protocol.KyudichiQueueProtocol.*;

/**
 * 큐디치 대기열 (Velocity 프록시 ↔ 클라이언트, chacademi:kyudichi_queue): 매칭 상태 HUD, 수락 확인 카드, 대시보드 데이터.
 * HUD: 화면 위 가운데 (경기 HUD가 떠 있으면 점수판 아래). 버튼은 Alt 커서나 채팅창에서 누른다.
 * 서버가 부탁하지 않은 대시보드·랭킹·기록을 보내면 (명령어 /큐디치, /큐디치 랭킹, /큐디치 정보) 대시보드를 그 탭으로 연다.
 */
public final class KyudichiQueueClient {
    private KyudichiQueueClient() {}

    public record Payload(byte[] bytes) implements CustomPayload {
        public static final Id<Payload> ID = new Id<>(Identifier.of(KyudichiQueueProtocol.QUEUE));
        public static final PacketCodec<RegistryByteBuf, Payload> CODEC = new PacketCodec<>() {
            public Payload decode(RegistryByteBuf b) { int n = b.readableBytes(); if (n > KyudichiQueueProtocol.MAX_BYTES) { b.skipBytes(n); return new Payload(new byte[0]); } byte[] d = new byte[n]; b.readBytes(d); return new Payload(d); }
            public void encode(RegistryByteBuf b, Payload v) { b.writeBytes(v.bytes()); }
        };
        public Id<Payload> getId() { return ID; }
    }

    static Status status = new Status(0, 0, 0, 0, 0, false, 0); static long statusAt;
    static Dash dash; static Ranking ranking; static RecordReply record;
    static boolean wantDash, wantRanking, wantRecord;
    private static long offerSoundAt;

    static void initialize() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (payload, context) -> {
            ToClient m;
            try { m = KyudichiQueueProtocol.decodeToClient(payload.bytes()); } catch (IllegalArgumentException bad) { return; }
            context.client().execute(() -> receive(m));
        });
        // Tell the proxy this client draws the queue itself (no action-bar fallback). Sent again after every server switch.
        ClientPlayConnectionEvents.JOIN.register((h, sender, c) -> c.execute(() -> send(new ClientHello())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { status = new Status(0, 0, 0, 0, 0, false, 0); dash = null; ranking = null; record = null; });
        HudLayerRegistrationCallback.EVENT.register(d -> d.attachLayerAfter(IdentifiedLayer.CROSSHAIR, Identifier.of("magiccodex", "kyudichi_queue"), KyudichiQueueClient::render));
    }

    static void send(FromClient m) {
        if (KyudichiPreview.active()) { KyudichiPreview.queueAction(m); return; }
        if (ClientPlayNetworking.canSend(Payload.ID)) ClientPlayNetworking.send(new Payload(KyudichiQueueProtocol.encode(m)));
    }
    static void requestDash() { wantDash = true; send(new DashRequest()); }
    static void requestRanking() { wantRanking = true; send(new RankingRequest()); }
    static void requestRecord(String nick) { wantRecord = true; send(new RecordRequest(nick == null ? "" : nick)); }

    static void receive(ToClient m) {
        var c = MinecraftClient.getInstance();
        switch (m) {
            case Status x -> {
                if (x.state() == KyudichiQueueProtocol.S_OFFER && status.state() != KyudichiQueueProtocol.S_OFFER && Util.getMeasuringTimeMs() - offerSoundAt > 3000) {
                    offerSoundAt = Util.getMeasuringTimeMs();
                    c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.7f));
                }
                status = x; statusAt = Util.getMeasuringTimeMs();
            }
            case Dash x -> { dash = x; boolean asked = wantDash; wantDash = false; if (!asked) open(KyudichiDashboardScreen.Tab.LIVE); }
            case Ranking x -> { ranking = x; boolean asked = wantRanking; wantRanking = false; if (!asked) open(KyudichiDashboardScreen.Tab.RANKING); }
            case RecordReply x -> { record = x; boolean asked = wantRecord; wantRecord = false; if (!asked) open(KyudichiDashboardScreen.Tab.RECORD); }
        }
    }

    private static void open(KyudichiDashboardScreen.Tab tab) {
        var c = MinecraftClient.getInstance();
        if (c.currentScreen instanceof KyudichiDashboardScreen d) { d.show(tab); return; }
        if (c.currentScreen == null || HudPointer.hudVisibleUnder(c.currentScreen)) c.setScreen(new KyudichiDashboardScreen(tab));
    }

    static int state() { return status.state(); }

    // ================================================================== HUD card
    private static float acceptX, acceptY, declineX, declineY, cancelX, cancelY, btnW, btnH, smallW, smallH;
    private static boolean acceptShown, cancelShown;
    private static HudTextureCache images; private static CodexTypography text; private static int generation = -1;

    private static void render(DrawContext ctx, RenderTickCounter ticks) {
        acceptShown = cancelShown = false;
        var c = MinecraftClient.getInstance();
        int st = status.state();
        if (st == KyudichiQueueProtocol.S_NONE || st == KyudichiQueueProtocol.S_PLAYING) return;
        if (c.player == null || c.options.hudHidden || !HudPointer.hudVisibleUnder(c.currentScreen)) return;
        if (KyudichiClient.active() && !KyudichiClient.spectator()) return;
        if (generation != UiResources.generation()) { if (images != null) images.close(); if (text != null) text.close(); images = null; text = null; generation = UiResources.generation(); }
        if (images == null) images = new HudTextureCache(c);
        if (text == null) text = new CodexTypography(c);
        images.beginFrame(); text.beginFrame();
        float w = c.getWindow().getScaledWidth(), h = c.getWindow().getScaledHeight(), s = Math.min(w / 1920f, h / 1080f);
        float top = (KyudichiClient.active() ? 150 : 14) * s;
        long now = Util.getMeasuringTimeMs();
        try {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, top, 150); ctx.getMatrices().scale(s, s, 1);
            if (st == KyudichiQueueProtocol.S_OFFER) offer(ctx, w, s, top, now);
            else pill(ctx, st, w, s, top, now);
        } finally { ctx.getMatrices().pop(); images.endFrame(); }
    }

    private static void pill(DrawContext ctx, int st, float w, float s, float top, long now) {
        float pw = 420, ph = 64;
        KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, KyudichiArt.BTN, -pw / 2, 0, pw, ph, 0xF2FFFFFF);
        int waited = status.waitedSec() + (int) ((now - statusAt) / 1000);
        String body = st == KyudichiQueueProtocol.S_MOVING ? "경기장으로 이동 중…" : "큐디치 매칭 중  " + waited / 60 + ":" + String.format("%02d", waited % 60) + "  ·  대기 파티 " + status.queuedParties();
        float bob = (float) Math.sin(now / 300.0) * 1.5f;
        KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, KyudichiArt.ICON_WAIT, -pw / 2 + 18, ph / 2 - 16 + bob, 32, 32, 0xFFFFFFFF);
        outlined(ctx, body, 8, ph / 2, 19, 0xFFF1E6C8, true);
        if (st == KyudichiQueueProtocol.S_SEARCHING && HudPointer.active()) {
            float bw = 92, bh = 40, bx = pw / 2 + 10, by = (ph - bh) / 2;
            KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, KyudichiArt.BTN_RED, bx, by, bw, bh, 0xFFFFFFFF);
            outlined(ctx, "취소", bx + bw / 2, by + bh / 2, 16, 0xFFFFD6D6, true);
            cancelShown = true; cancelX = w / 2f + bx * s; cancelY = top + by * s; smallW = bw * s; smallH = bh * s;
        }
    }

    private static void offer(DrawContext ctx, float w, float s, float top, long now) {
        float pw = 560, ph = pw * KyudichiArt.P_SMALL[3] / KyudichiArt.P_SMALL[2];
        float y0 = 40;
        KyudichiArt.draw(images, ctx, KyudichiArt.PANELS, KyudichiArt.P_SMALL, -pw / 2, y0, pw, ph, 0xFFFFFFFF);
        outlined(ctx, "경기를 찾았어요!", 0, y0 + 46, 26, 0xFFF4DE9C, true);
        int left = Math.max(0, status.offerLeftSec() - (int) ((now - statusAt) / 1000));
        // accepted dots
        int total = Math.max(1, status.total());
        for (int i = 0; i < total; i++) {
            float dx = (i - (total - 1) / 2f) * 34;
            int[] icon = i < status.accepted() ? KyudichiArt.ICON_OK : KyudichiArt.ICON_WAIT;
            KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, icon, dx - 14, y0 + 92, 28, 28, 0xFFFFFFFF);
        }
        outlined(ctx, left + "초", 0, y0 + 138, 18, left <= 3 ? 0xFFFF9C9C : 0xFFD7E2EA, true);
        float bw = 170, bh = 58, by = y0 + ph - bh - 30;
        if (status.iAccepted()) {
            outlined(ctx, "수락했어요 · 다른 사람을 기다리는 중", 0, by + bh / 2, 19, 0xFF9FE6A8, true);
        } else {
            boolean pointer = HudPointer.active();
            KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, KyudichiArt.BTN_GLOW, -bw - 14, by, bw, bh, 0xFFFFFFFF);
            outlined(ctx, "수락", -bw / 2 - 14, by + bh / 2, 22, 0xFFFFF1C4, true);
            KyudichiArt.draw(images, ctx, KyudichiArt.CONTROLS, KyudichiArt.BTN_RED, 14, by, bw, bh, 0xFFFFFFFF);
            outlined(ctx, "거절", bw / 2 + 14, by + bh / 2, 22, 0xFFFFD6D6, true);
            if (!pointer) outlined(ctx, "Alt 또는 채팅창(Enter)을 열고 누르세요 · /큐디치 수락", 0, by - 16, 14, 0xFFA5B8C8, true);
            acceptShown = true;
            acceptX = w / 2f + (-bw - 14) * s; acceptY = top + by * s; declineX = w / 2f + 14 * s; declineY = acceptY; btnW = bw * s; btnH = bh * s;
        }
    }

    /** Alt cursor / chat click. @return true when a queue button was hit. */
    public static boolean click(double x, double y, int button) {
        if (button != 0) return false;
        if (acceptShown && in(x, y, acceptX, acceptY, btnW, btnH)) { send(new Accept()); return true; }
        if (acceptShown && in(x, y, declineX, declineY, btnW, btnH)) { send(new Decline()); return true; }
        if (cancelShown && in(x, y, cancelX, cancelY, smallW, smallH)) { send(new Leave()); return true; }
        return false;
    }
    private static boolean in(double x, double y, float rx, float ry, float w, float h) { return x >= rx && x <= rx + w && y >= ry && y <= ry + h; }

    private static final Identifier LABEL = Identifier.of("magiccodex", "label");
    private static void outlined(DrawContext ctx, String v, float x, float y, float size, int color, boolean centered) {
        text.drawOutline(ctx, v, x, y, size, (color & 0xFF000000) | 0x10141A, LABEL, centered);
        text.draw(ctx, v, x, y, size, color, LABEL, centered);
    }
}
