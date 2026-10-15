package school.magiccodex.client;

import java.util.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import school.magiccodex.protocol.ArtifactProtocol;
import school.magiccodex.protocol.ArtifactProtocol.Entry;

/**
 * Artifact codex data from the server (MagicCodexBridge ArtifactBridge). The catalogue comes from the server's
 * artifacts.yml, so editing that file changes the codex without rebuilding this mod. Never fabricates ownership.
 */
public final class ArtifactClient {
    private ArtifactClient() {}

    private interface Bytes { byte[] bytes(); }
    public record Request(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Request> ID = new Id<>(Identifier.of(ArtifactProtocol.REQUEST));
        static final PacketCodec<RegistryByteBuf, Request> CODEC = codec(Request::new);
        public Id<Request> getId() { return ID; }
    }
    public record Response(byte[] bytes) implements CustomPayload, Bytes {
        static final Id<Response> ID = new Id<>(Identifier.of(ArtifactProtocol.RESPONSE));
        static final PacketCodec<RegistryByteBuf, Response> CODEC = codec(Response::new);
        public Id<Response> getId() { return ID; }
    }
    private static <T extends Bytes> PacketCodec<RegistryByteBuf, T> codec(java.util.function.Function<byte[], T> f) {
        return new PacketCodec<>() {
            public T decode(RegistryByteBuf b) {
                int n = b.readableBytes();
                if (n > ArtifactProtocol.MAX_BYTES) { b.skipBytes(n); return f.apply(new byte[0]); }
                byte[] a = new byte[n]; b.readBytes(a); return f.apply(a);
            }
            public void encode(RegistryByteBuf b, T value) { b.writeBytes(value.bytes()); }
        };
    }

    private static List<Entry> catalog = List.of();
    private static long revision;
    private static long building = -1;
    private static Entry[][] parts = new Entry[0][];
    private static boolean[] owned = new boolean[0];
    private static List<String> equipped = List.of();
    private static int maxEquipped = 1;
    private static String notice = "";
    private static long version;
    private static boolean supported;

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Response.ID, Response.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Response.ID, (payload, context) -> {
            ArtifactProtocol.Frame frame;
            try { frame = ArtifactProtocol.decode(payload.bytes()); } catch (IllegalArgumentException ignored) { return; }
            context.client().execute(() -> accept(frame));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("아티팩트도감").executes(ctx -> {
                    var c = MinecraftClient.getInstance();
                    c.send(() -> c.setScreen(new ArtifactCodexScreen(null)));
                    return 1;
                })));
    }

    private static void accept(ArtifactProtocol.Frame frame) {
        supported = true;
        switch (frame) {
            case ArtifactProtocol.Catalog c -> {
                if (c.revision() != building || parts.length != c.parts()) { building = c.revision(); parts = new Entry[c.parts()][]; }
                parts[c.part()] = c.entries().toArray(Entry[]::new);
                for (Entry[] p : parts) if (p == null) return;
                var all = new ArrayList<Entry>();
                for (Entry[] p : parts) all.addAll(Arrays.asList(p));
                catalog = List.copyOf(all); revision = building; building = -1; parts = new Entry[0][];
            }
            case ArtifactProtocol.State s -> {
                if (s.revision() != revision) return; // a newer catalogue is still arriving
                owned = s.owned().length == catalog.size() ? s.owned().clone() : new boolean[catalog.size()];
                equipped = s.equipped(); maxEquipped = s.maxEquipped(); notice = s.notice(); version++;
            }
        }
    }

    private static void reset() {
        catalog = List.of(); revision = 0; building = -1; parts = new Entry[0][]; owned = new boolean[0];
        equipped = List.of(); notice = ""; supported = false; version++;
    }

    private static boolean send(int action, String id) {
        if (!ClientPlayNetworking.canSend(Request.ID)) return false;
        ClientPlayNetworking.send(new Request(ArtifactProtocol.request(new ArtifactProtocol.Request(action, revision, id))));
        return true;
    }

    /** Ask for the current state (and the catalogue when ours is out of date). */
    static boolean query() { return send(ArtifactProtocol.QUERY, ""); }
    static boolean equip(String id) { return send(ArtifactProtocol.EQUIP, id); }
    static boolean unequip(String id) { return send(ArtifactProtocol.UNEQUIP, id); }

    /** The server answered at least once on this connection. */
    static boolean supported() { return supported; }
    static boolean channelOpen() { return ClientPlayNetworking.canSend(Request.ID); }
    static List<Entry> catalog() { return catalog; }
    static boolean owned(int index) { return index >= 0 && index < owned.length && owned[index]; }
    static int ownedCount() { int n = 0; for (boolean b : owned) if (b) n++; return n; }
    static List<String> equipped() { return equipped; }
    static int maxEquipped() { return maxEquipped; }
    static String notice() { return notice; }
    static long version() { return version; }
    static Entry find(String id) { for (Entry e : catalog) if (e.id().equals(id)) return e; return null; }
}
