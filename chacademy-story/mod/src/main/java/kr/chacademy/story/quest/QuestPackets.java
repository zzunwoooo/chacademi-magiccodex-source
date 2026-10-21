package kr.chacademy.story.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 스토리 단계 HUD 패킷 (서버 → 클라만). 바이트 형식은 플러그인 QuestCodec 과 같아야 한다.
 * 문자열 = VarInt 길이 + UTF-8, 정수 = VarInt, 참거짓 = 1바이트, 실수 = 8바이트 double.
 */
public final class QuestPackets {
    public static final int MAX_CONDS = 16;
    public static final int TARGET_NONE = 0, TARGET_LOCATION = 1, TARGET_NPC = 2;

    private QuestPackets() {}

    public record Cond(String label, int current, int required) {}

    /** 단계 전체. step 이 비어 있으면 진행할 단계 없음 (HUD 숨김). ready = 조건을 다 채움 (포탈·징표 표시). */
    public record StepS2C(int session, String step, int run, String chapter, String description, boolean ready, List<Cond> conds,
                          int targetType, String world, double x, double y, double z, double radius, String npcEntity) implements CustomPacketPayload {
        public static final Type<StepS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("chacademy", "quest_step"));
        /** 깨진·모르는 형식의 패킷 (핸들러가 무시). */
        public static final StepS2C INVALID = new StepS2C(0, "", 0, "", "", false, List.of(), TARGET_NONE, "", 0, 0, 0, 0, "");
        public static final StreamCodec<RegistryFriendlyByteBuf, StepS2C> CODEC = SafeCodec.failSafe("quest_step", StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.session()); buf.writeUtf(v.step()); buf.writeVarInt(v.run()); buf.writeUtf(v.chapter()); buf.writeUtf(v.description());
            buf.writeBoolean(v.ready()); buf.writeVarInt(v.conds().size());
            for (Cond c : v.conds()) { buf.writeUtf(c.label()); buf.writeVarInt(c.current()); buf.writeVarInt(c.required()); }
            buf.writeVarInt(v.targetType()); buf.writeUtf(v.world()); buf.writeDouble(v.x()); buf.writeDouble(v.y()); buf.writeDouble(v.z());
            buf.writeDouble(v.radius()); buf.writeUtf(v.npcEntity());
        }, buf -> {
            int session = buf.readVarInt();
            String step = buf.readUtf(64);
            int run = buf.readVarInt();
            String chapter = buf.readUtf(256);
            String description = buf.readUtf(1024);
            boolean ready = buf.readBoolean();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_CONDS) throw new IllegalArgumentException("too many quest conditions: " + n);
            List<Cond> conds = new ArrayList<>(n);
            for (int i = 0; i < n; i++) conds.add(new Cond(buf.readUtf(256), buf.readVarInt(), Math.max(1, buf.readVarInt())));
            int type = buf.readVarInt();
            String world = buf.readUtf(256);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble(), radius = buf.readDouble();
            String npc = buf.readUtf(64);
            return new StepS2C(session, step, run, chapter, description, ready, List.copyOf(conds), type, world, x, y, z, radius, npc);
        }), INVALID);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 같은 단계 안의 진행도·준비 상태 변경. 같은 session·step·run 의 단계에만 적용한다. */
    public record UpdateS2C(int session, String step, int run, boolean ready, int[] current) implements CustomPacketPayload {
        public static final Type<UpdateS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("chacademy", "quest_update"));
        /** 깨진·모르는 형식의 패킷 (핸들러가 무시). */
        public static final UpdateS2C INVALID = new UpdateS2C(0, "", 0, false, new int[0]);
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateS2C> CODEC = SafeCodec.failSafe("quest_update", StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.session()); buf.writeUtf(v.step()); buf.writeVarInt(v.run()); buf.writeBoolean(v.ready());
            buf.writeVarInt(v.current().length);
            for (int c : v.current()) buf.writeVarInt(c);
        }, buf -> {
            int session = buf.readVarInt();
            String step = buf.readUtf(64);
            int run = buf.readVarInt();
            boolean ready = buf.readBoolean();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_CONDS) throw new IllegalArgumentException("too many quest conditions: " + n);
            int[] cur = new int[n];
            for (int i = 0; i < n; i++) cur[i] = buf.readVarInt();
            return new UpdateS2C(session, step, run, ready, cur);
        }), INVALID);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
