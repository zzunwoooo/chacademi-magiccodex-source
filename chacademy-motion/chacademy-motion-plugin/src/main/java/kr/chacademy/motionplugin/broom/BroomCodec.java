package kr.chacademy.motionplugin.broom;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 빗자루 패킷. 모드의 {@code BroomPackets} 와 바이트 형식이 반드시 같아야 한다.
 * <ul>
 *   <li>문자열: VarInt(UTF-8 바이트 길이) + UTF-8</li>
 *   <li>UUID: long + long (빅엔디언)</li>
 *   <li>float / int: 4바이트 빅엔디언 (ByteBuf.writeFloat / writeInt 와 같음)</li>
 *   <li>byte: 1바이트</li>
 * </ul>
 * Bukkit 에 의존하지 않아서 단위 테스트로 바로 확인할 수 있다.
 */
public final class BroomCodec {
    /** 서버 → 본인: 빗자루 타기 시작 + 조작 수치. */
    public static final String CHANNEL_RIDE = "chacademy:broom_ride";
    /** 서버 → 본인과 주변 유저: 누가 어떤 빗자루를 탔는지 / 내렸는지. */
    public static final String CHANNEL_STATE = "chacademy:broom_state";
    /** 모드 → 서버: 내리기, 낙마, 다시 잡기 같은 사건. */
    public static final String CHANNEL_EVENT = "chacademy:broom_evt";

    /** 서버 → 본인: 가진 빗자루 목록과 장착 중인 빗자루 (장비창용). */
    public static final String CHANNEL_LIST = "chacademy:broom_list";
    /** 모드 → 서버: 목록 다시 보내 줘(0) / 이 빗자루 장착(1). */
    public static final String CHANNEL_REQUEST = "chacademy:broom_req";

    /** 서버 → 본인: 빗자루 비행 효과 (큐디치 성소). 1바이트 kind. */
    public static final String CHANNEL_FX = "chacademy:broom_fx";
    /** 균형 게이지 가득 (안정성 성소). */
    public static final byte FX_REFILL_BALANCE = 1;
    /** 기본 가속과 같은 가속, 쿨타임은 쓰지도 시작하지도 않음 (가속 성소). */
    public static final byte FX_FREE_BOOST = 2;

    public static final int MAX_ID_BYTES = 64;
    public static final int MAX_TEXT_BYTES = 256;
    public static final int MAX_LIST = 64;

    public static final byte REQ_REFRESH = 0;
    public static final byte REQ_EQUIP = 1;

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

    private BroomCodec() {}

    /** 본인에게 보내는 조작 수치. limitTicks 0 = 시간 제한 없음. */
    public record Ride(String broom, float maxSpeed, float accel, float turn, float brake, float stability,
                       float climb, float mastery, int limitTicks, int boostCooldown, int rollCooldown) {}

    public record State(UUID player, String broom, byte reason) {}

    public record Event(byte type, float value) {}

    /** 장비창에 보일 빗자루 하나. */
    public record Owned(String id, String name, String grade, float maxSpeed, float accel, float turn, float brake,
                        float stability, float climb) {}

    /** equipped 가 빈 문자열이면 장착 없음. */
    public record ListMsg(String equipped, float mastery, java.util.List<Owned> brooms) {}

    public record Request(byte kind, String broom) {}

    public static byte[] encodeList(ListMsg m) {
        var out = new ByteArrayOutputStream(64 + m.brooms().size() * 64);
        writeString(out, m.equipped());
        writeFloat(out, m.mastery());
        writeVarInt(out, m.brooms().size());
        for (var b : m.brooms()) {
            writeString(out, b.id());
            writeString(out, b.name());
            writeString(out, b.grade());
            writeFloat(out, b.maxSpeed());
            writeFloat(out, b.accel());
            writeFloat(out, b.turn());
            writeFloat(out, b.brake());
            writeFloat(out, b.stability());
            writeFloat(out, b.climb());
        }
        return out.toByteArray();
    }

    public static ListMsg decodeList(byte[] data) throws MalformedException {
        var buf = ByteBuffer.wrap(data);
        try {
            String eq = readString(buf, MAX_ID_BYTES);
            float mastery = buf.getFloat();
            int n = readVarInt(buf);
            if (n < 0 || n > MAX_LIST) throw new MalformedException("목록 크기 " + n);
            var list = new java.util.ArrayList<Owned>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Owned(readString(buf, MAX_ID_BYTES), readString(buf, MAX_TEXT_BYTES), readString(buf, MAX_TEXT_BYTES),
                        buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat()));
            }
            if (buf.hasRemaining()) throw new MalformedException("남는 바이트 " + buf.remaining());
            return new ListMsg(eq, mastery, list);
        } catch (BufferUnderflowException e) {
            throw new MalformedException("패킷이 너무 짧음");
        }
    }

    public static byte[] encodeRequest(Request r) {
        var out = new ByteArrayOutputStream(16);
        out.write(r.kind());
        writeString(out, r.broom());
        return out.toByteArray();
    }

    public static Request decodeRequest(byte[] data) throws MalformedException {
        var buf = ByteBuffer.wrap(data);
        try {
            var r = new Request(buf.get(), readString(buf, MAX_ID_BYTES));
            if (buf.hasRemaining()) throw new MalformedException("남는 바이트 " + buf.remaining());
            return r;
        } catch (BufferUnderflowException e) {
            throw new MalformedException("패킷이 너무 짧음");
        }
    }

    public static byte[] encodeRide(Ride r) {
        var out = new ByteArrayOutputStream(64);
        writeString(out, r.broom());
        writeFloat(out, r.maxSpeed());
        writeFloat(out, r.accel());
        writeFloat(out, r.turn());
        writeFloat(out, r.brake());
        writeFloat(out, r.stability());
        writeFloat(out, r.climb());
        writeFloat(out, r.mastery());
        writeInt(out, r.limitTicks());
        writeInt(out, r.boostCooldown());
        writeInt(out, r.rollCooldown());
        return out.toByteArray();
    }

    public static Ride decodeRide(byte[] data) throws MalformedException {
        var buf = ByteBuffer.wrap(data);
        try {
            var r = new Ride(readString(buf, MAX_ID_BYTES), buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat(),
                    buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getInt(), buf.getInt(), buf.getInt());
            if (buf.hasRemaining()) throw new MalformedException("남는 바이트 " + buf.remaining());
            return r;
        } catch (BufferUnderflowException e) {
            throw new MalformedException("패킷이 너무 짧음");
        }
    }

    public static byte[] encodeState(State s) {
        var out = new ByteArrayOutputStream(32);
        writeLong(out, s.player().getMostSignificantBits());
        writeLong(out, s.player().getLeastSignificantBits());
        writeString(out, s.broom());
        out.write(s.reason());
        return out.toByteArray();
    }

    public static State decodeState(byte[] data) throws MalformedException {
        var buf = ByteBuffer.wrap(data);
        try {
            var s = new State(new UUID(buf.getLong(), buf.getLong()), readString(buf, MAX_ID_BYTES), buf.get());
            if (buf.hasRemaining()) throw new MalformedException("남는 바이트 " + buf.remaining());
            return s;
        } catch (BufferUnderflowException e) {
            throw new MalformedException("패킷이 너무 짧음");
        }
    }

    public static byte[] encodeEvent(Event e) {
        var out = new ByteArrayOutputStream(5);
        out.write(e.type());
        writeFloat(out, e.value());
        return out.toByteArray();
    }

    public static Event decodeEvent(byte[] data) throws MalformedException {
        if (data.length != 5) throw new MalformedException("길이 " + data.length);
        var buf = ByteBuffer.wrap(data);
        var e = new Event(buf.get(), buf.getFloat());
        if (!Float.isFinite(e.value())) throw new MalformedException("값이 숫자가 아님");
        return e;
    }

    // ---- 기본 형식 ----

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(ByteBuffer buf) throws MalformedException {
        int value = 0, shift = 0;
        byte b;
        do {
            if (shift >= 35) throw new MalformedException("VarInt 가 너무 김");
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    static void writeString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    static String readString(ByteBuffer buf, int maxBytes) throws MalformedException {
        int len = readVarInt(buf);
        if (len < 0 || len > maxBytes) throw new MalformedException("문자열 길이 이상: " + len);
        if (len > buf.remaining()) throw new MalformedException("문자열이 잘림");
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeInt(ByteArrayOutputStream out, int v) {
        for (int i = 3; i >= 0; i--) out.write((v >>> (i * 8)) & 0xFF);
    }

    static void writeFloat(ByteArrayOutputStream out, float v) {
        writeInt(out, Float.floatToIntBits(v));
    }

    static void writeLong(ByteArrayOutputStream out, long v) {
        for (int i = 7; i >= 0; i--) out.write((int) (v >>> (i * 8)) & 0xFF);
    }

    public static final class MalformedException extends Exception {
        public MalformedException(String message) {
            super(message);
        }
    }
}
