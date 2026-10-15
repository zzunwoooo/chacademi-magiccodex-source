package school.magiccodex.protocol;

import java.io.*;
import java.util.*;

/**
 * Artifact codex (아티팩트 도감). The server owns the catalogue (plugins/MagicCodexBridge/artifacts.yml), ownership and
 * the equipped list; the client only draws them. Only ids cross the trust boundary from the client.
 *
 * C2S request: int version, byte action (QUERY / EQUIP / UNEQUIP / USE), long knownCatalog, String id.
 * S2C response frames (each <= MAX_BYTES, so a 200-entry catalogue is split into parts):
 *   CATALOG: long catalogRevision, short part, short parts, entries... (v2: each entry ends with int cooldown)
 *   STATE:   long catalogRevision, byte maxEquipped, owned bitset (catalogue order), equipped ids, notice
 *            (v2: then one int per equipped id = remaining use cooldown in ms)
 * The client rebuilds the catalogue only when every part of one revision arrived, then applies STATE.
 *
 * Version 2 (2026-10-15, artifact use key): writers always send 2, readers accept 1 and 2 (v1 = no use data).
 * A v1 client cannot read v2 frames, so the mod and MagicCodexBridge must be deployed together.
 */
public final class ArtifactProtocol {
    public static final String REQUEST = "magiccodex:artifact_request", RESPONSE = "magiccodex:artifact_response";
    public static final int MAX_BYTES = 30000, MAX_ARTIFACTS = 1024, MAX_EQUIPPED = 8, MAX_PARTS = 256;
    public static final int VERSION = 2;
    public static final int QUERY = 0, EQUIP = 1, UNEQUIP = 2, USE = 3;
    /** Entry.cooldown for passive artifacts (no use key). Usable ones carry their cooldown in seconds (0 = none). */
    public static final int PASSIVE = -1, MAX_COOLDOWN = 86400;
    public static final int CATALOG = 1, STATE = 2;
    public static final int MAX_ID = 64, MAX_NAME = 40, MAX_DESCRIPTION = 200, MAX_EFFECT = 600, MAX_ICON = 200, MAX_NOTICE = 120;
    /** 0 일반, 1 고급, 2 유일, 3 전설. */
    public static final String[] GRADE_LABELS = {"일반", "고급", "유일", "전설"};
    public static final String[] GRADE_KEYS = {"common", "rare", "unique", "legendary"};

    public record Request(int action, long knownCatalog, String id) {
        public Request {
            if (action < QUERY || action > USE || id == null || id.length() > MAX_ID || (action != QUERY && !validId(id)))
                throw new IllegalArgumentException("artifact request");
        }
    }
    public record Entry(String id, String name, int grade, String description, String effect, String icon, int cooldown) {
        public Entry {
            if (!validId(id) || name == null || name.isBlank() || name.length() > MAX_NAME || grade < 0 || grade > 3
                    || description == null || description.length() > MAX_DESCRIPTION || effect == null || effect.length() > MAX_EFFECT
                    || icon == null || icon.length() > MAX_ICON || cooldown < PASSIVE || cooldown > MAX_COOLDOWN)
                throw new IllegalArgumentException("artifact entry " + id);
        }
        public Entry(String id, String name, int grade, String description, String effect, String icon) {
            this(id, name, grade, description, effect, icon, PASSIVE);
        }
        public boolean usable() { return cooldown >= 0; }
    }
    public sealed interface Frame permits Catalog, State {}
    public record Catalog(long revision, int part, int parts, List<Entry> entries) implements Frame {
        public Catalog { if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) throw new IllegalArgumentException("part"); entries = List.copyOf(entries); }
    }
    /** cooldownMs: remaining use cooldown per equipped id (same order), 0 = ready or passive. */
    public record State(long revision, int maxEquipped, boolean[] owned, List<String> equipped, String notice, int[] cooldownMs) implements Frame {
        public State {
            if (maxEquipped < 1 || maxEquipped > MAX_EQUIPPED || owned.length > MAX_ARTIFACTS || equipped.size() > MAX_EQUIPPED
                    || notice == null || notice.length() > MAX_NOTICE || cooldownMs == null || cooldownMs.length != equipped.size())
                throw new IllegalArgumentException("state");
            for (String id : equipped) if (!validId(id)) throw new IllegalArgumentException("equipped id");
            for (int ms : cooldownMs) if (ms < 0 || ms > MAX_COOLDOWN * 1000) throw new IllegalArgumentException("cooldown");
            equipped = List.copyOf(equipped); cooldownMs = cooldownMs.clone();
        }
        public State(long revision, int maxEquipped, boolean[] owned, List<String> equipped, String notice) {
            this(revision, maxEquipped, owned, equipped, notice, new int[equipped.size()]);
        }
    }

    private ArtifactProtocol() {}

    public static boolean validId(String id) { return id != null && id.matches("[a-z0-9_]{1," + MAX_ID + "}"); }

    public static byte[] request(Request r) {
        return write(d -> { d.writeInt(VERSION); d.writeByte(r.action()); d.writeLong(r.knownCatalog()); d.writeUTF(r.id()); });
    }
    public static Request request(byte[] raw) {
        return read(raw, d -> { int v = d.readInt(); if (v != 1 && v != VERSION) throw new IOException("version"); return new Request(d.readUnsignedByte(), d.readLong(), d.readUTF()); });
    }

    /** Splits the catalogue into frames that each stay under MAX_BYTES. */
    public static List<byte[]> catalog(long revision, List<Entry> entries) {
        if (entries.size() > MAX_ARTIFACTS) throw new IllegalArgumentException("too many artifacts");
        var groups = new ArrayList<List<Entry>>();
        var current = new ArrayList<Entry>();
        int size = 0;
        for (Entry e : entries) {
            int cost = 20 + 3 * (e.id().length() + e.name().length() + e.description().length() + e.effect().length() + e.icon().length());
            if (!current.isEmpty() && size + cost > MAX_BYTES - 64) { groups.add(current); current = new ArrayList<>(); size = 0; }
            current.add(e); size += cost;
        }
        groups.add(current);
        if (groups.size() > MAX_PARTS) throw new IllegalArgumentException("catalogue too large");
        var out = new ArrayList<byte[]>();
        for (int i = 0; i < groups.size(); i++) out.add(encode(new Catalog(revision, i, groups.size(), groups.get(i))));
        return out;
    }

    public static byte[] encode(Frame frame) {
        return write(d -> {
            d.writeInt(VERSION);
            switch (frame) {
                case Catalog c -> {
                    d.writeByte(CATALOG); d.writeLong(c.revision()); d.writeShort(c.part()); d.writeShort(c.parts()); d.writeShort(c.entries().size());
                    for (Entry e : c.entries()) { d.writeUTF(e.id()); d.writeUTF(e.name()); d.writeByte(e.grade()); d.writeUTF(e.description()); d.writeUTF(e.effect()); d.writeUTF(e.icon()); d.writeInt(e.cooldown()); }
                }
                case State s -> {
                    d.writeByte(STATE); d.writeLong(s.revision()); d.writeByte(s.maxEquipped());
                    d.writeShort(s.owned().length);
                    byte[] bits = new byte[(s.owned().length + 7) / 8];
                    for (int i = 0; i < s.owned().length; i++) if (s.owned()[i]) bits[i / 8] |= (byte) (1 << (i % 8));
                    d.write(bits);
                    d.writeByte(s.equipped().size()); for (String id : s.equipped()) d.writeUTF(id);
                    d.writeUTF(s.notice());
                    for (int ms : s.cooldownMs()) d.writeInt(ms);
                }
            }
        });
    }

    public static Frame decode(byte[] raw) {
        return read(raw, d -> {
            int version = d.readInt();
            if (version != 1 && version != VERSION) throw new IOException("version");
            int type = d.readUnsignedByte();
            if (type == CATALOG) {
                long revision = d.readLong(); int part = d.readUnsignedShort(), parts = d.readUnsignedShort(), count = d.readUnsignedShort();
                if (count > MAX_ARTIFACTS) throw new IOException("count");
                var list = new ArrayList<Entry>(count);
                for (int i = 0; i < count; i++) list.add(new Entry(d.readUTF(), d.readUTF(), d.readUnsignedByte(), d.readUTF(), d.readUTF(), d.readUTF(),
                        version == 1 ? PASSIVE : d.readInt()));
                return new Catalog(revision, part, parts, list);
            }
            if (type == STATE) {
                long revision = d.readLong(); int max = d.readUnsignedByte(); int n = d.readUnsignedShort();
                if (n > MAX_ARTIFACTS) throw new IOException("owned");
                byte[] bits = d.readNBytes((n + 7) / 8); if (bits.length != (n + 7) / 8) throw new IOException("bits");
                boolean[] owned = new boolean[n]; for (int i = 0; i < n; i++) owned[i] = (bits[i / 8] & (1 << (i % 8))) != 0;
                int e = d.readUnsignedByte(); if (e > MAX_EQUIPPED) throw new IOException("equipped");
                var equipped = new ArrayList<String>(e); for (int i = 0; i < e; i++) equipped.add(d.readUTF());
                String notice = d.readUTF();
                int[] cooldown = new int[e]; if (version >= 2) for (int i = 0; i < e; i++) cooldown[i] = d.readInt();
                return new State(revision, max, owned, equipped, notice, cooldown);
            }
            throw new IOException("type");
        });
    }

    private interface Writer { void run(DataOutputStream d) throws IOException; }
    private interface Reader<T> { T run(DataInputStream d) throws IOException; }
    private static byte[] write(Writer w) {
        try { var b = new ByteArrayOutputStream(); w.run(new DataOutputStream(b)); if (b.size() > MAX_BYTES) throw new IOException("size"); return b.toByteArray(); }
        catch (IOException | RuntimeException e) { throw new IllegalArgumentException("Invalid artifact packet", e); }
    }
    private static <T> T read(byte[] raw, Reader<T> r) {
        try {
            if (raw == null || raw.length > MAX_BYTES) throw new IOException("size");
            var d = new DataInputStream(new ByteArrayInputStream(raw)); T value = r.run(d);
            if (d.available() != 0) throw new IOException("trailing"); return value;
        } catch (IOException | RuntimeException e) { throw new IllegalArgumentException("Invalid artifact packet", e); }
    }
}
