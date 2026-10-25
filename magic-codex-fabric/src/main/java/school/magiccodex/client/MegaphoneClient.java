package school.magiccodex.client;

import static school.magiccodex.client.MegaphoneLayout.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.MegaphoneProtocol;

/**
 * 확성기 (메모 #102): 입력 창 열기·답하기, 화면 위 공지 (받은 순서대로 하나씩), Ctrl 커서로 아이템 툴팁.
 *
 * <p>Ctrl 은 새 키가 아니라 기존 키 설정 "마우스 포인터"(UiKeys CURSOR, 기본 왼쪽 Ctrl)를 그대로 쓴다: 누르고 있는 동안
 * HudCursorScreen 이 열려 커서가 나오고, 떼면 닫혀 커서가 다시 잠긴다 (PlayerHudClient). 공지 아이콘 위에 커서가 오면 툴팁.
 * 공지가 사라지면 툴팁도 사라진다. 커서 자체는 다른 HUD(파티·알림 카드)도 쓰는 공용이라 Ctrl 을 떼야 닫힌다.
 *
 * <p>공지 시간은 화면에 보이는 동안만 흐른다 (다른 창을 열어 가려지면 멈췄다가 닫으면 이어서). 대기열 최대 {@link #QUEUE_MAX}개,
 * 넘치면 가장 오래 기다린 것부터 버린다.
 */
public final class MegaphoneClient {
    private MegaphoneClient() {}

    private interface Bytes { byte[] bytes(); }
    public record Request(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Request> ID = new Id<>(Identifier.of(MegaphoneProtocol.REQUEST));
        static final PacketCodec<RegistryByteBuf, Request> CODEC = codec(Request::new, MegaphoneProtocol.MAX_REQUEST_BYTES);
        public Id<Request> getId() { return ID; }
    }
    public record Response(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Response> ID = new Id<>(Identifier.of(MegaphoneProtocol.RESPONSE));
        static final PacketCodec<RegistryByteBuf, Response> CODEC = codec(Response::new, MegaphoneProtocol.MAX_BYTES);
        public Id<Response> getId() { return ID; }
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf, T> codec(java.util.function.Function<byte[], T> f, int max) {
        return new PacketCodec<>() {
            public T decode(RegistryByteBuf b) {
                int n = b.readableBytes();
                if (n > max) { b.skipBytes(n); return f.apply(new byte[0]); }
                byte[] a = new byte[n]; b.readBytes(a); return f.apply(a);
            }
            public void encode(RegistryByteBuf b, T value) { b.writeBytes(value.bytes()); }
        };
    }

    /** 화면에 띄울 공지 하나 (아이템은 받을 때 한 번 풀어 둔다). */
    record Notice(String id, String sender, String text, long duration, ItemStack item) {}

    static final int QUEUE_MAX = 30;
    private static final ArrayDeque<Notice> QUEUE = new ArrayDeque<>();
    private static Notice current;
    private static long shown, last;
    private static List<String> body = List.of();
    private static HudTextureCache images;
    private static CodexTypography text;
    private static int generation = -1;

    static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Response.ID, Response.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Response.ID, (payload, context) -> {
            MegaphoneProtocol.Frame frame;
            try { frame = MegaphoneProtocol.frame(payload.bytes()); } catch (IllegalArgumentException ignored) { return; }
            var connection = context.client().getNetworkHandler();
            context.client().execute(() -> { if (connection == context.client().getNetworkHandler()) receive(context.client(), frame); });
        });
        HudRenderCallback.EVENT.register((ctx, ticks) -> render(ctx));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> {
            reset();
            if (c.currentScreen instanceof MegaphoneScreen screen) screen.serverClosed();
        }));
    }

    private static void receive(MinecraftClient c, MegaphoneProtocol.Frame f) {
        switch (f.kind()) {
            case MegaphoneProtocol.OPEN -> {
                if (c.player == null) return;
                var o = f.open();
                DeferredScreens.open(() -> new MegaphoneScreen(o.token(), o.maxChars()));
            }
            case MegaphoneProtocol.CLOSE -> { if (c.currentScreen instanceof MegaphoneScreen s && s.token() == f.closeToken()) s.serverClosed(); }
            case MegaphoneProtocol.SHOW -> {
                var s = f.show();
                if (QUEUE.stream().anyMatch(n -> n.id().equals(s.id())) || current != null && current.id().equals(s.id())) return;
                while (QUEUE.size() >= QUEUE_MAX) QUEUE.pollFirst();
                QUEUE.addLast(new Notice(s.id(), s.sender(), s.text(), s.durationMs(), decode(c, s.item())));
            }
            default -> {}
        }
    }

    /** 상점 미리보기와 같은 방식 (Paper serializeAsBytes = 압축 NBT). 실패하면 아이템 없음. */
    private static ItemStack decode(MinecraftClient c, byte[] b) {
        if (b.length == 0 || c.world == null) return ItemStack.EMPTY;
        try {
            return ItemStack.fromNbt(c.world.getRegistryManager(), NbtIo.readCompressed(new ByteArrayInputStream(b), NbtSizeTracker.of(2097152))).orElse(ItemStack.EMPTY);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    /** 입력 창 답 (보내기 = proceed). 연결이 없으면 false. */
    static boolean answer(long token, String value, boolean proceed) {
        if (!ClientPlayNetworking.canSend(Request.ID)) return false;
        try {
            ClientPlayNetworking.send(new Request(MegaphoneProtocol.request(new MegaphoneProtocol.Request(
                    proceed ? MegaphoneProtocol.SEND : MegaphoneProtocol.CANCEL, token, proceed ? value : ""))));
            return true;
        } catch (IllegalArgumentException e) { return false; }
    }

    private static void reset() { QUEUE.clear(); current = null; shown = last = 0; body = List.of(); close(); }
    private static void close() { if (text != null) { text.close(); text = null; } if (images != null) { images.close(); images = null; } generation = -1; }

    private static boolean visible(MinecraftClient c) {
        return c.player != null && c.world != null && !c.options.hudHidden && c.getOverlay() == null && HudPointer.hudVisibleUnder(c.currentScreen);
    }

    // ------------------------------------------------------------------ 그리기

    private static void render(DrawContext ctx) {
        var c = MinecraftClient.getInstance(); long now = Util.getMeasuringTimeMs();
        long delta = last == 0 ? 0 : Math.max(0, Math.min(1000, now - last)); last = now;
        if (!visible(c)) return;                    // 가려진 동안은 시간이 흐르지 않는다
        if (current == null) {
            current = QUEUE.pollFirst();
            if (current == null) return;
            shown = 0; body = List.of();
            NotificationPresentation.playSound(c);
        } else shown += delta;
        if (shown >= current.duration()) { current = null; return; }
        if (generation != UiResources.generation()) { close(); generation = UiResources.generation(); }
        if (text == null) text = new CodexTypography(c);
        if (images == null) images = new HudTextureCache(c);
        if (body.isEmpty()) {
            var rows = CodexTextLayout.wrap(current.text(), CARD_W - PAD_X * 2, s -> text.width(s, BODY_SIZE, BOLD));
            body = rows.size() > BODY_MAX_LINES ? List.copyOf(rows.subList(0, BODY_MAX_LINES)) : List.copyOf(rows);
        }
        boolean hasItem = !current.item().isEmpty();
        var n = MegaphoneLayout.notice(hasItem, body.size());
        var w = c.getWindow();
        float s = Math.min(w.getScaledWidth() / 1920f, w.getScaledHeight() / 1080f);
        float left = w.getScaledWidth() / 2f - CARD_W * s / 2f;   // 카드 왼쪽 (화면 좌표)
        float alpha = Math.clamp(Math.min(shown / 220f, (current.duration() - shown) / 400f), 0, 1);
        int tint = NotificationSkin.argb(alpha, 0xFFFFFF);
        text.beginFrame(); images.beginFrame(); ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(left, 0, 350); ctx.getMatrices().scale(s, s, 1);
            NotificationSkin.nine(images, ctx, CARD, CARD_TEX_W, CARD_TEX_H, CARD_CORNER, 0, n.cardY(), CARD_W, n.cardH(), CARD_CORNER_SCALE, tint);
            float mid = CARD_W / 2f;
            text.draw(ctx, fitName(current.sender()), mid, n.nameY(), NAME_SIZE, NotificationSkin.argb(alpha, NAME_COLOR), TITLE_FONT, true);
            float by = n.bodyY();
            for (String line : body) { text.draw(ctx, line, mid, by, BODY_SIZE, NotificationSkin.argb(alpha, BODY_COLOR), BOLD, true); by += BODY_LINE; }
            HudMesh.quad(ctx, BAR_INSET, n.cardY() + n.cardH() - BAR_BOTTOM, (CARD_W - BAR_INSET * 2) * (1 - shown / (float) current.duration()), BAR_H,
                    NotificationSkin.argb(alpha * .56f, BAR_COLOR), NotificationSkin.argb(alpha * .56f, BAR_COLOR));
            if (hasItem) {
                // 아이템: 카드 바깥 위 가운데. 바닐라 GUI 아이템 렌더를 ICON 크기로 그린다 (픽셀 그림을 늘리지 않고 모델을 그 크기로).
                if (alpha > .35f) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(mid - ICON / 2f, n.iconY(), 10); ctx.getMatrices().scale(ICON / 16f, ICON / 16f, 1);
                    ctx.drawItem(current.item(), 0, 0);
                    ctx.getMatrices().pop();
                }
                if (!HudPointer.active()) {
                    String key = UiKeys.cursorName();
                    if (!key.isEmpty()) text.drawOutline(ctx, key + " 아이템 보기", mid + ICON / 2f + 10, n.iconY() + ICON / 2f, HINT_SIZE, NotificationSkin.argb(alpha, HINT_COLOR), BOLD, false);
                }
            }
        } finally { ctx.getMatrices().pop(); images.endFrame(); }
        // Ctrl 커서가 아이콘 위에 있으면 실제 아이템 툴팁 (카드 아래쪽에 떨어뜨려 글씨를 가리지 않게)
        if (hasItem && HudPointer.active()) {
            double mx = HudPointer.x(), my = HudPointer.y();
            float ix = left + (CARD_W / 2f - ICON / 2f) * s, iy = n.iconY() * s, size = ICON * s;
            if (mx >= ix && mx < ix + size && my >= iy && my < iy + size) {
                int tx = Math.round(left + (CARD_W / 2f - ICON / 2f) * s), ty = Math.round((n.cardY() + n.cardH() + TOOLTIP_GAP) * s) + 12;
                if (!ItemTooltipRenderer.render(ctx, c.textRenderer, current.item(), tx, ty)) ctx.drawItemTooltip(c.textRenderer, current.item(), tx, ty);
            }
        }
    }

    private static String fitName(String name) { return name == null || name.isBlank() ? "?" : name; }
}
