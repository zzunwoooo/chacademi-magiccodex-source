package school.magiccodex.protocol;

import java.io.*;
import java.util.UUID;

/**
 * Target health HUD (server MagicCodexBridge -> client MagicCodex). The server picks the target
 * (last combat partner for 10 s, otherwise the entity under the crosshair) and sends what the HUD shows.
 * Status icons are not in here: the client takes them from {@link StatusProtocol} / {@link ElementProtocol}
 * by entity id.
 *
 * v1 frame: byte version=1, byte type
 *   SHOW: int entityId, long uuidMost, long uuidLeast, byte kind, byte flags, UTF name, UTF translationKey,
 *         float health, float maxHealth, float mana (-1 = no mana bar), float maxMana,
 *         float damageDealt (by the receiving player to this target), float tameChance (0..1, -1 = hidden)
 *   HIDE: nothing
 */
public final class TargetHudProtocol {
    public static final String CHANNEL = "magiccodex:target_hud";
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 512;
    public static final int MAX_NAME = 48;

    public static final int KIND_VANILLA = 0, KIND_CUSTOM = 1, KIND_PLAYER = 2;
    public static final int FLAG_SHINY = 1, FLAG_BOSS = 2, FLAG_COMBAT = 4;

    public sealed interface Frame permits Show, Hide {}
    public record Show(int entityId, UUID uuid, int kind, int flags, String name, String translationKey,
                       float health, float maxHealth, float mana, float maxMana, float damage, float tameChance) implements Frame {
        public Show {
            if (uuid == null || kind < 0 || kind > 2 || flags < 0 || flags > 255) throw new IllegalArgumentException("target");
            name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
            translationKey = translationKey == null ? "" : translationKey.length() > 96 ? "" : translationKey;
            if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0) throw new IllegalArgumentException("health");
            health = Math.clamp(health, 0, maxHealth);
            if (!Float.isFinite(mana) || !Float.isFinite(maxMana)) throw new IllegalArgumentException("mana");
            if (!Float.isFinite(damage) || damage < 0) damage = 0;
            if (!Float.isFinite(tameChance)) tameChance = -1;
            tameChance = tameChance < 0 ? -1 : Math.min(1, tameChance);
        }
        public boolean shiny() { return (flags & FLAG_SHINY) != 0; }
        public boolean boss() { return (flags & FLAG_BOSS) != 0; }
        public boolean combat() { return (flags & FLAG_COMBAT) != 0; }
        public boolean hasMana() { return mana >= 0 && maxMana > 0; }
    }
    public record Hide() implements Frame {}

    private TargetHudProtocol() {}

    public static byte[] encode(Frame frame) {
        try {
            var b = new ByteArrayOutputStream(96);
            var o = new DataOutputStream(b);
            o.writeByte(VERSION);
            switch (frame) {
                case Show s -> {
                    o.writeByte(0); o.writeInt(s.entityId());
                    o.writeLong(s.uuid().getMostSignificantBits()); o.writeLong(s.uuid().getLeastSignificantBits());
                    o.writeByte(s.kind()); o.writeByte(s.flags()); o.writeUTF(s.name()); o.writeUTF(s.translationKey());
                    o.writeFloat(s.health()); o.writeFloat(s.maxHealth()); o.writeFloat(s.mana()); o.writeFloat(s.maxMana());
                    o.writeFloat(s.damage()); o.writeFloat(s.tameChance());
                }
                case Hide h -> o.writeByte(1);
            }
            if (b.size() > MAX_BYTES) throw new IOException("size");
            return b.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException(e); }
    }

    public static Frame decode(byte[] raw) {
        try {
            if (raw == null || raw.length > MAX_BYTES) throw new IOException("size");
            var i = new DataInputStream(new ByteArrayInputStream(raw));
            if (i.readUnsignedByte() != VERSION) throw new IOException("version");
            Frame f = switch (i.readUnsignedByte()) {
                case 0 -> new Show(i.readInt(), new UUID(i.readLong(), i.readLong()), i.readUnsignedByte(), i.readUnsignedByte(),
                        i.readUTF(), i.readUTF(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat());
                case 1 -> new Hide();
                default -> throw new IOException("type");
            };
            if (i.available() != 0) throw new IOException("trailing");
            return f;
        } catch (IOException | RuntimeException e) { throw new IllegalArgumentException(e); }
    }
}
