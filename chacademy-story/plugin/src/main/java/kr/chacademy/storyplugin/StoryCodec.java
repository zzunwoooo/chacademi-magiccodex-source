package kr.chacademy.storyplugin;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 모드(Fabric)의 CutscenePackets 와 같은 바이트 형식.
 * 문자열 = VarInt 길이 + UTF-8, 정수(INT) = 4바이트 빅엔디언, 참거짓 = 1바이트.
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

    public static final int MODE_AUTO = 0;
    public static final int MODE_SKIP = 1;
    public static final int MODE_NOSKIP = 2;

    private static final int MAX_STRING = 256;

    private StoryCodec() {}

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
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    static String readString(ByteBuffer buf) {
        int len = readVarInt(buf);
        if (len < 0 || len > MAX_STRING || len > buf.remaining()) throw new IllegalArgumentException("문자열 길이 이상: " + len);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
