package kr.chacademy.storyplugin;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 모드(Fabric)의 CutscenePackets 와 같은 바이트 형식.
 * 문자열 = VarInt 길이 + UTF-8, 정수(INT) = 4바이트 빅엔디언, 참거짓 = 1바이트.
 *
 * <p>프로토콜 버전: 모드는 {@code chacademy:story_v<버전>} 채널을 듣는다. 서버는 플레이어가 듣는 채널 이름에서
 * 버전을 읽고 ({@link #clientProtocol}), {@link #MIN_CLIENT_PROTOCOL} 보다 낮으면 아무 패킷도 보내지 않고
 * "모드를 업데이트해 주세요" 라고 알린다. 예전 패킷의 바이트 형식은 바꾸지 않았다 (새 패킷은 새 채널).
 *
 * <p>문자열 길이: 클라 → 서버는 {@value #MAX_STRING}자 (UTF-8 {@value #MAX_STRING_BYTES}바이트) 까지,
 * 서버 → 클라는 {@value #MAX_S2C_STRING}자까지. 모드의 CutscenePackets 와 같은 숫자여야 한다.
 */
public final class StoryCodec {
    public static final String CUTSCENE_PLAY = "chacademy:cutscene_play";
    public static final String CUTSCENE_STOP = "chacademy:cutscene_stop";
    public static final String CUTSCENE_DONE = "chacademy:cutscene_done";
    public static final String DIALOGUE_OPEN = "chacademy:dialogue_open";
    public static final String DIALOGUE_STOP = "chacademy:dialogue_stop";
    public static final String DIALOGUE_EVENT = "chacademy:dialogue_event";
    public static final String DIALOGUE_DONE = "chacademy:dialogue_done";
    public static final String DIALOGUE_PROGRESS = "chacademy:dialogue_progress";

    /** 클라 → 서버: 컷신 화면이 끝나기 전에 사라짐 (사망 화면, 서버 GUI, 월드 이동 등). 서버가 잠시 뒤 다시 보낸다. */
    public static final String CUTSCENE_ABORT = "chacademy:cutscene_abort";
    /** 클라 → 서버: 스토리 파일이 없거나 깨져서 보여 줄 수 없음 (컷신·대화 공통). 끝난 것으로 치지 않는다. */
    public static final String STORY_FAIL = "chacademy:story_fail";

    /** 이 플러그인이 쓰는 프로토콜 버전. 패킷 형식이 호환되지 않게 바뀌면 올린다. */
    public static final int PROTOCOL = 2;
    /** 이 버전보다 낮은 모드에는 스토리를 보내지 않는다. 1 = 버전 채널이 없던 예전 모드. */
    public static final int MIN_CLIENT_PROTOCOL = 2;
    public static final String HELLO_PREFIX = "chacademy:story_v";
    /** 서버 → 클라: 서버 프로토콜 버전 (VarInt). 모드가 이 채널을 듣는 것 자체가 "버전 2 이상" 이라는 표시다. */
    public static final String STORY_HELLO = HELLO_PREFIX + PROTOCOL;

    public static final int KIND_CUTSCENE = 0;
    public static final int KIND_DIALOGUE = 1;

    public static final int MODE_AUTO = 0;
    public static final int MODE_SKIP = 1;
    public static final int MODE_NOSKIP = 2;

    /** 클라가 보내는 문자열 한 개의 최대 글자 수 / UTF-8 바이트 수 (마인크래프트 stringUtf8(256) 과 같은 규칙). */
    public static final int MAX_STRING = 256;
    public static final int MAX_STRING_BYTES = MAX_STRING * 3;
    /** 서버가 보내는 문자열 한 개의 최대 글자 수 (모드의 STRING_UTF8 한도). */
    public static final int MAX_S2C_STRING = 32767;
    /** 클라가 보내는 패킷 전체 크기 한도. 이보다 크면 읽지 않고 버린다. */
    public static final int MAX_C2S_PACKET = 2048;

    private StoryCodec() {}

    /** 플레이어가 듣는 채널 목록에서 모드의 프로토콜 버전을 읽는다. 0 = 모드 없음, 1 = 버전 채널이 없던 예전 모드. */
    public static int clientProtocol(java.util.Collection<String> listeningChannels) {
        int best = 0;
        for (String c : listeningChannels) {
            if (c == null) continue;
            if (c.equals(CUTSCENE_PLAY)) best = Math.max(best, 1);
            if (c.startsWith(HELLO_PREFIX) && c.length() > HELLO_PREFIX.length() && c.length() <= HELLO_PREFIX.length() + 4) {
                try {
                    best = Math.max(best, Integer.parseInt(c.substring(HELLO_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    public static byte[] hello(int protocol) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, protocol);
        return out.toByteArray();
    }

    public static byte[] cutscenePlay(String id, int mode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, id);
        writeVarInt(out, mode);
        return out.toByteArray();
    }

    /** startScene 이 비어 있으면 처음부터, 있으면 그 장면의 startLine 번째 대사부터 (이어서 보기). */
    public static byte[] dialogueOpen(String id, String affinity, String vars, String startScene, int startLine) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, id);
        writeString(out, affinity);
        writeString(out, vars);
        writeString(out, startScene == null ? "" : startScene);
        writeVarInt(out, Math.max(0, startLine));
        return out.toByteArray();
    }

    public record DialogueProgress(String id, String scene, int line) {}

    public static DialogueProgress dialogueProgress(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new DialogueProgress(readString(b), readString(b), readVarInt(b)));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    /** 모드의 TextVars.decode 와 같은 형식: 항목은 \u001E, 키와 값은 \u001F. */
    public static String encodeVars(java.util.Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        for (var e : vars.entrySet()) {
            String k = e.getKey().replace("\u001E", "").replace("\u001F", "");
            String v = e.getValue() == null ? "" : e.getValue().replace("\u001E", "").replace("\u001F", "");
            if (k.isEmpty() || k.length() > 64) continue;
            if (v.length() > 128) v = v.substring(0, 128);
            if (sb.length() > 0) sb.append('\u001E');
            sb.append(k).append('\u001F').append(v);
        }
        return sb.toString();
    }

    public record CutsceneDone(String id, boolean skipped) {}

    public record DialogueEvent(String id, String event, String npc, int add) {}

    public record DialogueDone(String id, String lastScene) {}

    public record CutsceneAbort(String id) {}

    /** kind = KIND_CUTSCENE / KIND_DIALOGUE, reason = missing | broken | timeout | magiccodex 등 짧은 이유. */
    public record StoryFail(int kind, String id, String reason) {}

    public static CutsceneAbort cutsceneAbort(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new CutsceneAbort(readString(b)));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    public static StoryFail storyFail(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new StoryFail(readVarInt(b), readString(b), readString(b)));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    public static CutsceneDone cutsceneDone(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new CutsceneDone(readString(b), b.get() != 0));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    public static DialogueEvent dialogueEvent(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new DialogueEvent(readString(b), readString(b), readString(b), b.getInt()));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    public static DialogueDone dialogueDone(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        try {
            return complete(b, new DialogueDone(readString(b), readString(b)));
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("패킷이 너무 짧음");
        }
    }

    private static <T> T complete(ByteBuffer b, T value) {
        if (b.hasRemaining()) throw new IllegalArgumentException("Trailing story packet data");
        return value;
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(ByteBuffer buf) {
        int value = 0, shift = 0;
        byte b;
        do {
            if (shift >= 35) throw new IllegalArgumentException("VarInt 가 너무 김");
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    static void writeString(ByteArrayOutputStream out, String s) {
        if (s == null) s = "";
        // 모드가 읽을 수 있는 길이까지만 (넘으면 모드에서 패킷 전체가 깨진다)
        if (s.length() > MAX_S2C_STRING) s = s.substring(0, MAX_S2C_STRING);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    static String readString(ByteBuffer buf) {
        int len = readVarInt(buf);
        if (len < 0 || len > MAX_STRING_BYTES || len > buf.remaining()) throw new IllegalArgumentException("문자열 길이 이상: " + len);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.length() > MAX_STRING) throw new IllegalArgumentException("문자열이 너무 김: " + s.length());
        return s;
    }
}
