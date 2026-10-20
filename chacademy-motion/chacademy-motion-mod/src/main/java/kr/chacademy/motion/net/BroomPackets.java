package kr.chacademy.motion.net;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 빗자루 패킷. 바이트 형식은 플러그인의 {@code BroomCodec} 과 반드시 같아야 한다.
 * (문자열 = VarInt 길이 + UTF-8, UUID = long+long, float/int = 4바이트 빅엔디언, byte = 1바이트)
 */
public final class BroomPackets {
    private BroomPackets() {}

    // broom_state 의 reason
    public static final byte STATE_NORMAL = 0;
    public static final byte STATE_FALL = 1;
    public static final byte STATE_TIMEOUT = 2;
    public static final byte STATE_ROLL_LEFT = 3;
    public static final byte STATE_ROLL_RIGHT = 4;
    public static final byte STATE_START = 5;
    /** 큐디치: 안정성 0 → 한 팔로 매달림 (2026-10-20) */
    public static final byte STATE_HANG = 6;
    /** 큐디치: 회복 → 다시 올라타는 중 */
    public static final byte STATE_CLIMB = 7;

    // broom_evt 의 type
    public static final byte EVT_DISMOUNT = 0;
    public static final byte EVT_FALL = 1;
    public static final byte EVT_CATCH = 2;
    public static final byte EVT_BOOST = 3;
    public static final byte EVT_ROLL_LEFT = 4;
    public static final byte EVT_ROLL_RIGHT = 5;
    public static final byte EVT_CRASH = 6;
    public static final byte EVT_HANG = 7;
    public static final byte EVT_CLIMB = 8;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MotionPackets.NAMESPACE, path));
    }

    /** 서버 → 나: 빗자루 타기 시작 + 조작 수치. limitTicks 0 = 시간 제한 없음. */
    public record RideS2C(String broom, float maxSpeed, float accel, float turn, float brake, float stability,
                          float climb, float mastery, int limitTicks, int boostCooldown, int rollCooldown)
            implements CustomPacketPayload {
        public static final Type<RideS2C> TYPE = typeOf("broom_ride");
        public static final StreamCodec<RegistryFriendlyByteBuf, RideS2C> CODEC = StreamCodec.of(
                (buf, r) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, r.broom);
                    buf.writeFloat(r.maxSpeed);
                    buf.writeFloat(r.accel);
                    buf.writeFloat(r.turn);
                    buf.writeFloat(r.brake);
                    buf.writeFloat(r.stability);
                    buf.writeFloat(r.climb);
                    buf.writeFloat(r.mastery);
                    buf.writeInt(r.limitTicks);
                    buf.writeInt(r.boostCooldown);
                    buf.writeInt(r.rollCooldown);
                },
                buf -> new RideS2C(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 나와 주변: 누가 어떤 빗자루를 탔는지. broom 이 빈 문자열이면 내림. */
    public record StateS2C(UUID player, String broom, byte reason) implements CustomPacketPayload {
        public static final Type<StateS2C> TYPE = typeOf("broom_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, StateS2C> CODEC = StreamCodec.of(
                (buf, s) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, s.player);
                    ByteBufCodecs.STRING_UTF8.encode(buf, s.broom);
                    buf.writeByte(s.reason);
                },
                buf -> new StateS2C(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static final byte FX_REFILL_BALANCE = 1;
    public static final byte FX_FREE_BOOST = 2;
    /** 큐디치: 낙마 대신 매달림 켜기 / 끄기 */
    public static final byte FX_HANG_MODE_ON = 3;
    public static final byte FX_HANG_MODE_OFF = 4;

    /** 서버 → 나: 비행 효과 (큐디치 성소). 1 균형 가득, 2 쿨타임 없는 가속. */
    public record FxS2C(byte kind) implements CustomPacketPayload {
        public static final Type<FxS2C> TYPE = typeOf("broom_fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, FxS2C> CODEC = StreamCodec.of(
                (buf, f) -> buf.writeByte(f.kind),
                buf -> new FxS2C(buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 나 → 서버: 내리기, 낙마, 다시 잡기 등. */
    public record EventC2S(byte kind, float value) implements CustomPacketPayload {
        public static final Type<EventC2S> TYPE = typeOf("broom_evt");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventC2S> CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeByte(e.kind);
                    buf.writeFloat(e.value);
                },
                buf -> new EventC2S(buf.readByte(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 장비창에 보일 빗자루 하나 (서버 brooms.yml 수치). */
    public record Owned(String id, String name, String grade, float maxSpeed, float accel, float turn, float brake,
                        float stability, float climb) {}

    /** 서버 → 나: 가진 빗자루와 장착 중인 빗자루. equipped 가 빈 문자열이면 없음. */
    public record ListS2C(String equipped, float mastery, java.util.List<Owned> brooms) implements CustomPacketPayload {
        public static final Type<ListS2C> TYPE = typeOf("broom_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, ListS2C> CODEC = StreamCodec.of(
                (buf, m) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, m.equipped);
                    buf.writeFloat(m.mastery);
                    ByteBufCodecs.VAR_INT.encode(buf, m.brooms.size());
                    for (var b : m.brooms) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, b.id());
                        ByteBufCodecs.STRING_UTF8.encode(buf, b.name());
                        ByteBufCodecs.STRING_UTF8.encode(buf, b.grade());
                        buf.writeFloat(b.maxSpeed());
                        buf.writeFloat(b.accel());
                        buf.writeFloat(b.turn());
                        buf.writeFloat(b.brake());
                        buf.writeFloat(b.stability());
                        buf.writeFloat(b.climb());
                    }
                },
                buf -> {
                    String eq = ByteBufCodecs.STRING_UTF8.decode(buf);
                    float mastery = buf.readFloat();
                    int n = ByteBufCodecs.VAR_INT.decode(buf);
                    if (n < 0 || n > 64) throw new IllegalArgumentException("broom list size " + n);
                    var list = new java.util.ArrayList<Owned>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new Owned(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                                ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(), buf.readFloat()));
                    }
                    return new ListS2C(eq, mastery, List.copyOf(list));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static final byte REQ_REFRESH = 0;
    public static final byte REQ_EQUIP = 1;

    /** 나 → 서버: 목록 다시 보내 줘 / 이 빗자루 장착. */
    public record RequestC2S(byte kind, String broom) implements CustomPacketPayload {
        public static final Type<RequestC2S> TYPE = typeOf("broom_req");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestC2S> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeByte(r.kind);
                    ByteBufCodecs.STRING_UTF8.encode(buf, r.broom);
                },
                buf -> new RequestC2S(buf.readByte(), ByteBufCodecs.STRING_UTF8.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
