package kr.chacademy.storyplugin;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 스토리 단계 HUD 패킷 (서버 → 클라만). 모드의 QuestPackets 와 같은 바이트 형식.
 * 문자열 = VarInt 길이 + UTF-8, 정수 = VarInt, 참거짓 = 1바이트, 실수 = 8바이트 빅엔디언 double.
 * <ul>
 *   <li>{@link #STEP}: 단계가 바뀔 때 / 접속·서버 이동 때 한 번. 단계 전체 (장 이름·설명·조건·호출 대상·현재 진행도).</li>
 *   <li>{@link #UPDATE}: 같은 단계 안에서 진행도나 준비 상태가 바뀔 때만. 같은 틱의 변경은 하나로 합쳐 보낸다.</li>
 * </ul>
 * session = 이 접속에서 보낸 STEP 번호 (UPDATE 는 같은 session 의 STEP 에만 적용), run = 단계에 들어간 횟수.
 * "준비 완료(ready)" = 조건을 다 채움 (포탈·징표 표시). 실제 단계 완료는 다음 STEP 이 와야 한다.
 */
final class QuestCodec {
    static final String STEP = "chacademy:quest_step";
    static final String UPDATE = "chacademy:quest_update";
    /** 길잡이 별: 지금 단계(session)의 목표를 머리 위 화살표로 가리키기 시작 (목표 10블록 안 / 단계가 바뀌면 클라가 스스로 끈다). */
    static final String GUIDE = "chacademy:quest_guide";

    static final int TARGET_NONE = 0, TARGET_LOCATION = 1, TARGET_NPC = 2;

    record CondView(String label, int current, int required) {}

    record StepView(int session, String step, int run, String chapter, String description, boolean ready,
                    List<CondView> conds, int targetType, String world, double x, double y, double z, double radius,
                    String npcEntity) {}

    private QuestCodec() {}

    static byte[] step(StepView v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StoryCodec.writeVarInt(out, v.session());
        StoryCodec.writeString(out, v.step());
        StoryCodec.writeVarInt(out, v.run());
        StoryCodec.writeString(out, v.chapter());
        StoryCodec.writeString(out, v.description());
        writeBool(out, v.ready());
        StoryCodec.writeVarInt(out, v.conds().size());
        for (CondView c : v.conds()) {
            StoryCodec.writeString(out, c.label());
            StoryCodec.writeVarInt(out, Math.max(0, c.current()));
            StoryCodec.writeVarInt(out, Math.max(1, c.required()));
        }
        StoryCodec.writeVarInt(out, v.targetType());
        StoryCodec.writeString(out, v.world());
        writeDouble(out, v.x());
        writeDouble(out, v.y());
        writeDouble(out, v.z());
        writeDouble(out, v.radius());
        StoryCodec.writeString(out, v.npcEntity());
        return out.toByteArray();
    }

    static byte[] update(int session, String step, int run, boolean ready, int[] current) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StoryCodec.writeVarInt(out, session);
        StoryCodec.writeString(out, step);
        StoryCodec.writeVarInt(out, run);
        writeBool(out, ready);
        StoryCodec.writeVarInt(out, current.length);
        for (int c : current) StoryCodec.writeVarInt(out, Math.max(0, c));
        return out.toByteArray();
    }

    static byte[] guide(int session) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StoryCodec.writeVarInt(out, session);
        return out.toByteArray();
    }

    private static void writeBool(ByteArrayOutputStream out, boolean b) { out.write(b ? 1 : 0); }

    private static void writeDouble(ByteArrayOutputStream out, double d) {
        long bits = Double.doubleToLongBits(d);
        for (int i = 7; i >= 0; i--) out.write((int) (bits >>> (i * 8)) & 0xFF);
    }
}
