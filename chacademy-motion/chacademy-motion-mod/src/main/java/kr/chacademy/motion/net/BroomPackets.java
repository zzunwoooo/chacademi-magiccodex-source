package kr.chacademy.motion.net;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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

    // broom_evt 의 type
    public static final byte EVT_DISMOUNT = 0;
    public static final byte EVT_FALL = 1;
    public static final byte EVT_CATCH = 2;
    public static final byte EVT_BOOST = 3;
    public static final byte EVT_ROLL_LEFT = 4;
    public static final byte EVT_ROLL_RIGHT = 5;
    public static final byte EVT_CRASH = 6;

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
}
