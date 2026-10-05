package school.magiccodex.npctalk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AI NPC 대화 전용 패킷 계약 (ChacaNPC 서버 플러그인 ↔ MagicCodex Fabric UI).
 * 기존 {@code DialogueProtocol}과 채널을 분리한다. 서버가 세션·순번·검사를 모두 소유하고,
 * 클라이언트는 문장/버튼/퀘스트 응답만 올린다. 서버는 검사를 통과한 완성 대사(LINE)만 내려보낸다.
 *
 * <p>이 소스는 magic-codex-fabric 과 chaca-npc-paper 에만 포함된다 (Bridge JAR에는 없음).
 */
public final class NpcTalkProtocol {

    /** 패킷 계약 버전. HELLO / HELLO_ACK 로 양쪽이 같을 때만 HUD 대화를 쓴다. */
    public static final int VERSION = 1;

    public static final String REQUEST = "magiccodex:npc_talk_request";
    public static final String RESPONSE = "magiccodex:npc_talk_response";

    public static final int MAX_BYTES = 8192;
    /** 플레이어 입력 상한: 코드포인트 100자, UTF-8 400바이트. 서버도 반드시 다시 검사한다. */
    public static final int MAX_INPUT_CHARS = 100;
    public static final int MAX_INPUT_BYTES = 400;
    public static final int MAX_LINE_CHARS = 400;
    public static final int MAX_BUTTONS = 3;
    public static final int MAX_HEARTS = 5;

    // 클라이언트 → 서버
    public static final int C_HELLO = 0;
    public static final int C_SAY = 1;
    public static final int C_BUTTON = 2;
    public static final int C_QUEST = 3;
    public static final int C_CLOSE = 4;

    // 서버 → 클라이언트
    public static final int S_HELLO_ACK = 0;
    public static final int S_OPEN = 1;
    public static final int S_THINKING = 2;
    public static final int S_STALL = 3;
    public static final int S_LINE = 4;
    public static final int S_QUEST_OFFER = 5;
    public static final int S_CLOSE = 6;
    public static final int S_INFO = 7;

    private static final String SESSION_RE = "[a-f0-9-]{36}";
    private static final String ID_RE = "[a-z0-9_-]{1,32}";
    private static final String TOKEN_RE = "[a-z0-9]{1,24}";
    private static final String PORTRAIT_RE = "[a-z0-9_/-]{0,120}";

    private NpcTalkProtocol() {
    }

    /**
     * 클라이언트 요청.
     * HELLO: seq = 클라이언트 프로토콜 버전, text = 모드 버전 문자열.
     * SAY: text = 문장. BUTTON: text = 버튼 id. QUEST: text = 토큰, accept = 수락 여부. CLOSE: 세션만.
     * seq: 세션 안에서 클라이언트가 1씩 올리는 요청 순번 (서버는 더 작거나 같은 순번을 버린다).
     */
    public record Request(int op, String session, int seq, String text, boolean accept) {
    }

    public record Button(String id, String label) {
    }

    /**
     * 서버 응답. seq = 이 응답이 대답하는 요청 순번 (OPEN은 0).
     * OPEN: speaker/portrait/text(첫 인사)/buttons/hearts/input.
     * LINE: text = 검사를 통과한 완성 대사. QUEST_OFFER: text = 퀘스트 제목, token = 수락 토큰.
     * HELLO_ACK: seq = 서버 프로토콜 버전.
     */
    public record Response(int op, String session, int seq, String speaker, String portrait, String text,
                           List<Button> buttons, int hearts, boolean input, String token) {
        public static Response simple(int op, String session, int seq, String text) {
            return new Response(op, session, seq, "", "", text, List.of(), 0, false, "");
        }
    }

    // ------------------------------------------------------------------ 검사 도우미

    /** 서버·클라이언트 공통 입력 검사: 비어 있지 않고, 제어문자 없고, 100자·400바이트 이하. */
    public static boolean validInput(String text) {
        if (text == null) {
            return false;
        }
        String t = text.strip();
        if (t.isEmpty()) {
            return false;
        }
        if (t.codePointCount(0, t.length()) > MAX_INPUT_CHARS) {
            return false;
        }
        if (t.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isISOControl(c) || c == '§') {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 인코딩

    public static byte[] encode(Request r) {
        return write(o -> {
            o.writeByte(r.op());
            o.writeUTF(r.session());
            o.writeInt(r.seq());
            o.writeUTF(r.text());
            o.writeBoolean(r.accept());
        });
    }

    public static Request request(byte[] bytes) {
        return read(bytes, i -> {
            int op = i.readUnsignedByte();
            String session = i.readUTF();
            int seq = i.readInt();
            String text = i.readUTF();
            boolean accept = i.readBoolean();
            if (seq < 0) {
                throw new IOException("seq");
            }
            switch (op) {
                case C_HELLO -> {
                    if (!session.isEmpty() || text.length() > 64) {
                        throw new IOException("hello");
                    }
                }
                case C_SAY -> {
                    if (!session.matches(SESSION_RE) || !validInput(text)) {
                        throw new IOException("say");
                    }
                }
                case C_BUTTON -> {
                    if (!session.matches(SESSION_RE) || !text.matches(ID_RE)) {
                        throw new IOException("button");
                    }
                }
                case C_QUEST -> {
                    if (!session.matches(SESSION_RE) || !text.matches(TOKEN_RE)) {
                        throw new IOException("quest");
                    }
                }
                case C_CLOSE -> {
                    if (!session.matches(SESSION_RE) || !text.isEmpty()) {
                        throw new IOException("close");
                    }
                }
                default -> throw new IOException("op");
            }
            return new Request(op, session, seq, op == C_SAY ? text.strip() : text, accept);
        });
    }

    public static byte[] encode(Response r) {
        return write(o -> {
            o.writeByte(r.op());
            o.writeUTF(r.session());
            o.writeInt(r.seq());
            o.writeUTF(r.speaker());
            o.writeUTF(r.portrait());
            o.writeUTF(r.text());
            o.writeByte(r.buttons().size());
            for (Button b : r.buttons()) {
                o.writeUTF(b.id());
                o.writeUTF(b.label());
            }
            o.writeByte(r.hearts());
            o.writeBoolean(r.input());
            o.writeUTF(r.token());
        });
    }

    public static Response response(byte[] bytes) {
        return read(bytes, i -> {
            int op = i.readUnsignedByte();
            String session = i.readUTF();
            int seq = i.readInt();
            String speaker = i.readUTF();
            String portrait = i.readUTF();
            String text = i.readUTF();
            int n = i.readUnsignedByte();
            if (op > S_INFO || seq < 0 || n > MAX_BUTTONS || speaker.length() > 64 || !portrait.matches(PORTRAIT_RE)
                    || text.length() > MAX_LINE_CHARS) {
                throw new IOException("response");
            }
            if (op != S_HELLO_ACK && !session.matches(SESSION_RE)) {
                throw new IOException("session");
            }
            List<Button> buttons = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                String id = i.readUTF();
                String label = i.readUTF();
                if (!id.matches(ID_RE) || label.length() > 40) {
                    throw new IOException("button");
                }
                buttons.add(new Button(id, label));
            }
            int hearts = i.readUnsignedByte();
            boolean input = i.readBoolean();
            String token = i.readUTF();
            if (hearts > MAX_HEARTS || !(token.isEmpty() || token.matches(TOKEN_RE))) {
                throw new IOException("tail");
            }
            return new Response(op, session, seq, speaker, portrait, text, List.copyOf(buttons), hearts, input, token);
        });
    }

    private interface Writer {
        void run(DataOutputStream o) throws IOException;
    }

    private interface Reader<T> {
        T run(DataInputStream i) throws IOException;
    }

    private static byte[] write(Writer f) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(VERSION);
            f.run(o);
            if (b.size() > MAX_BYTES) {
                throw new IOException("too large");
            }
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("NPC 대화 패킷 크기/형식 오류", e);
        }
    }

    private static <T> T read(byte[] b, Reader<T> f) {
        if (b == null || b.length < 4 || b.length > MAX_BYTES) {
            throw new IllegalArgumentException("size");
        }
        try {
            DataInputStream i = new DataInputStream(new ByteArrayInputStream(b));
            if (i.readInt() != VERSION) {
                throw new IOException("version");
            }
            T r = f.run(i);
            if (i.available() != 0) {
                throw new IOException("trailing");
            }
            return r;
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
