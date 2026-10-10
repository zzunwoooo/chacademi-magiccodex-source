package school.magiccodex.client;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import school.magiccodex.portrait.PortraitProtocol;

/**
 * 내 일러스트 (ChacaPortrait 서버 플러그인). 서버 채널이 열리면 HELLO(버전 + 캐시 SHA-256)를 보내고,
 * 서버가 보낸 PNG 조각을 모아 SHA-256을 확인한 뒤 디스크에 캐시하고 텍스처로 올린다.
 * 대화창의 "내 차례" 연출은 {@link #ready()}일 때만 쓴다 (일러스트가 없으면 띄우지 않음).
 */
public final class PortraitClient {
    private interface Bytes { byte[] bytes(); }

    public record Query(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Query> ID = new Id<>(Identifier.of(PortraitProtocol.REQUEST));
        static final PacketCodec<RegistryByteBuf, Query> CODEC = codec(Query::new);
        public Id<Query> getId() { return ID; }
    }

    public record Reply(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Reply> ID = new Id<>(Identifier.of(PortraitProtocol.RESPONSE));
        static final PacketCodec<RegistryByteBuf, Reply> CODEC = codec(Reply::new);
        public Id<Reply> getId() { return ID; }
    }

    private static <T extends Bytes> PacketCodec<RegistryByteBuf, T> codec(Function<byte[], T> f) {
        return new PacketCodec<>() {
            public T decode(RegistryByteBuf b) {
                int n = b.readableBytes();
                if (n < 4 || n > PortraitProtocol.MAX_PACKET_BYTES) { b.skipBytes(n); return f.apply(new byte[0]); }
                byte[] bytes = new byte[n];
                b.readBytes(bytes);
                return f.apply(bytes);
            }
            public void encode(RegistryByteBuf b, T p) { b.writeBytes(p.bytes()); }
        };
    }

    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("magiccodex/portraits");
    private static boolean helloSent, ready;
    private static PlayerPortraitTexture texture;
    private static String textureSha = "";
    // 받는 중인 전송
    private static String incomingSha;
    private static int incomingTotal, incomingChunks, incomingNext;
    private static ByteArrayOutputStream incoming;

    private PortraitClient() {}

    /** 일러스트가 올라와 있으면 true ("내 차례" 화면 사용 조건). */
    static boolean ready() { return ready && texture != null; }

    /** 대화창 이름표에 쓸 내 이름 (닉네임이 있으면 닉네임). */
    static String displayName() {
        var c = MinecraftClient.getInstance();
        String fallback = c.player == null ? "나" : c.player.getName().getString();
        return NicknameClient.display(fallback);
    }

    /** NPC 초상화 자리에 같은 크기로 그린다. */
    static void draw(DrawContext c, int x, int y, int width) { if (texture != null) texture.draw(c, x, y, width); }

    private static void reset() {
        helloSent = false; ready = false; dropIncoming();
        if (texture != null) { texture.close(); texture = null; }
        textureSha = "";
    }

    private static void dropIncoming() { incomingSha = null; incoming = null; incomingTotal = incomingChunks = incomingNext = 0; }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Query.ID, Query.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.ID, Reply.CODEC);
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(PortraitClient::reset));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> {
            reset();
            if (c.currentScreen instanceof PortraitPromptScreen screen) screen.serverClosed();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!helloSent && c.getNetworkHandler() != null && c.player != null && ClientPlayNetworking.canSend(Query.ID)) {
                helloSent = true;
                String cached = cachedSha(c);
                // 캐시가 있으면 서버 응답 전에 먼저 올려 둔다 (서버가 다른 SHA를 보내면 교체).
                // 캐시 파일이 깨져 못 올렸으면 SHA를 보내지 않아 서버가 다시 보내게 한다.
                if (!cached.isEmpty()) loadCached(c, cached);
                if (!cached.equals(textureSha)) cached = "";
                send(PortraitProtocol.Packet.of(PortraitProtocol.C_HELLO, cached, PortraitProtocol.VERSION, "magic-codex-ui"));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Reply.ID, (payload, context) -> {
            PortraitProtocol.Packet p;
            try { p = PortraitProtocol.decode(payload.bytes()); } catch (IllegalArgumentException ignored) { return; }
            context.client().execute(() -> receive(context.client(), p));
        });
    }

    private static void receive(MinecraftClient client, PortraitProtocol.Packet p) {
        switch (p.op()) {
            case PortraitProtocol.S_HELLO_ACK -> ready = p.number() == PortraitProtocol.VERSION;
            case PortraitProtocol.S_META -> { if (ready) meta(client, p); }
            case PortraitProtocol.S_CHUNK -> { if (ready) chunk(client, p); }
            case PortraitProtocol.S_PROMPT_OPEN -> {
                if (!ready || !PortraitProtocol.validToken(p.text()) || client.player == null) return;
                String hint = p.extra().length() > 200 ? p.extra().substring(0, 200) : p.extra();
                String token = p.text();
                DeferredScreens.open(() -> new PortraitPromptScreen(token, hint));
            }
            case PortraitProtocol.S_PROMPT_CLOSE -> {
                if (client.currentScreen instanceof PortraitPromptScreen screen) screen.serverClosed();
            }
            case PortraitProtocol.S_STATUS -> {
                if (ready && client.player != null && !p.extra().isEmpty())
                    client.player.sendMessage(Text.literal(p.extra().length() > 200 ? p.extra().substring(0, 200) : p.extra()), false);
            }
            default -> {}
        }
    }

    private static void meta(MinecraftClient client, PortraitProtocol.Packet p) {
        String sha = p.text();
        if (sha.isEmpty()) {
            // 서버에 일러스트 없음 (또는 관리자가 지움)
            dropIncoming();
            if (texture != null) { texture.close(); texture = null; }
            textureSha = "";
            deleteCache(client);
            return;
        }
        if (!PortraitProtocol.validSha(sha)) return;
        int chunks;
        try { chunks = Integer.parseInt(p.extra()); } catch (NumberFormatException e) { return; }
        if (chunks == 0) {
            // 서버와 캐시가 같음
            if (!sha.equals(textureSha)) loadCached(client, sha);
            return;
        }
        long total = p.number();
        if (total <= 0 || total > PortraitProtocol.MAX_IMAGE_BYTES || chunks != PortraitProtocol.chunks((int) total)) return;
        incomingSha = sha; incomingTotal = (int) total; incomingChunks = chunks; incomingNext = 0;
        incoming = new ByteArrayOutputStream(incomingTotal);
    }

    private static void chunk(MinecraftClient client, PortraitProtocol.Packet p) {
        if (incoming == null || !p.text().equals(incomingSha) || p.number() != incomingNext) { return; }
        byte[] data = p.data();
        int expected = incomingNext == incomingChunks - 1 ? incomingTotal - incomingNext * PortraitProtocol.CHUNK_BYTES : PortraitProtocol.CHUNK_BYTES;
        if (data.length != expected) { dropIncoming(); return; }
        incoming.writeBytes(data);
        incomingNext++;
        if (incomingNext < incomingChunks) return;
        byte[] png = incoming.toByteArray();
        String sha = incomingSha;
        dropIncoming();
        if (!sha.equals(sha256(png))) return;
        if (install(png, sha)) saveCache(client, sha, png);
    }

    private static boolean install(byte[] png, String sha) {
        try {
            var loaded = PlayerPortraitTexture.load(png, PortraitProtocol.MAX_IMAGE_SIDE);
            if (texture != null) texture.close();
            texture = loaded; textureSha = sha;
            return true;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Player portrait failed: {}", e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ 디스크 캐시 (서버 주소 + 내 UUID 별)

    private static Path cacheDir(MinecraftClient c) {
        var entry = c.getCurrentServerEntry();
        String server = entry == null ? "local" : entry.address.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (server.length() > 80) server = server.substring(0, 80);
        String player = c.player == null ? "unknown" : c.player.getUuidAsString();
        return ROOT.resolve(server).resolve(player);
    }

    private static String cachedSha(MinecraftClient c) {
        try {
            Path f = cacheDir(c).resolve("portrait.sha");
            if (!Files.exists(f)) return "";
            String s = Files.readString(f).strip();
            return PortraitProtocol.validSha(s) ? s : "";
        } catch (Exception e) { return ""; }
    }

    private static void loadCached(MinecraftClient c, String sha) {
        try {
            Path f = cacheDir(c).resolve("portrait.png");
            if (!Files.exists(f) || Files.size(f) > PortraitProtocol.MAX_IMAGE_BYTES) return;
            byte[] png = Files.readAllBytes(f);
            if (sha.equals(sha256(png))) install(png, sha);
        } catch (Exception ignored) {}
    }

    private static void saveCache(MinecraftClient c, String sha, byte[] png) {
        try {
            Path dir = cacheDir(c);
            Files.createDirectories(dir);
            Path tmp = dir.resolve("portrait.png.tmp");
            Files.write(tmp, png);
            Files.move(tmp, dir.resolve("portrait.png"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dir.resolve("portrait.sha"), sha);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Player portrait cache failed: {}", e.toString());
        }
    }

    private static void deleteCache(MinecraftClient c) {
        try {
            Path dir = cacheDir(c);
            Files.deleteIfExists(dir.resolve("portrait.sha"));
            Files.deleteIfExists(dir.resolve("portrait.png"));
        } catch (Exception ignored) {}
    }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (Exception e) { return ""; }
    }

    // ------------------------------------------------------------------ 다시 그리기 입력

    /** 입력창 결과 전송. proceed=false면 취소. */
    static boolean answer(String token, String text, boolean proceed) {
        return send(new PortraitProtocol.Packet(PortraitProtocol.C_PROMPT, token, 0, text, proceed, null));
    }

    private static boolean send(PortraitProtocol.Packet p) {
        var c = MinecraftClient.getInstance();
        if (c.getNetworkHandler() == null || !ClientPlayNetworking.canSend(Query.ID)) return false;
        try { ClientPlayNetworking.send(new Query(PortraitProtocol.encode(p))); return true; }
        catch (RuntimeException e) { return false; }
    }
}
