package kr.chacademy.cutscene.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 서버 플러그인과 주고받는 패킷. 바이트 형식은 플러그인의 {@code CutsceneCodec} 과 같아야 한다.
 * <ul>
 *   <li>문자열: VarInt(UTF-8 바이트 길이) + UTF-8 바이트</li>
 *   <li>정수: VarInt</li>
 *   <li>참/거짓: 1바이트 (0 또는 1)</li>
 * </ul>
 */
public final class CutscenePackets {
    public static final String NAMESPACE = "chacademy";

    /** 건너뛰기 규칙: 본 적 있으면 건너뛰기 가능. */
    public static final int MODE_AUTO = 0;
    /** 항상 건너뛰기 가능. */
    public static final int MODE_SKIP = 1;
    /** 절대 건너뛰기 불가. */
    public static final int MODE_NOSKIP = 2;

    private CutscenePackets() {}

    /** 서버 → 클라: "이 컷신을 재생해". */
    public record PlayS2C(String id, int mode) implements CustomPacketPayload {
        public static final Type<PlayS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "cutscene_play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PlayS2C::id,
                ByteBufCodecs.VAR_INT, PlayS2C::mode,
                PlayS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라: "재생 중인 컷신을 멈춰". 내용 없음. */
    public record StopS2C() implements CustomPacketPayload {
        public static final StopS2C INSTANCE = new StopS2C();
        public static final Type<StopS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "cutscene_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StopS2C> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라 → 서버: "이 컷신이 끝났어 (건너뛰었는지)". 퀘스트 연결용. */
    public record DoneC2S(String id, boolean skipped) implements CustomPacketPayload {
        public static final Type<DoneC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "cutscene_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DoneC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DoneC2S::id,
                ByteBufCodecs.BOOL, DoneC2S::skipped,
                DoneC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- 대화

    /**
     * 서버 → 클라: "이 대화를 열어". affinity = "npc=점수,npc=점수".
     * vars = 글자 바꾸기 목록 ("{player}" → 한글 닉네임 등). 항목은 \u001E, 키와 값은 \u001F 로 구분.
     */
    public record DialogueOpenS2C(String id, String affinity, String vars, String startScene, int startLine) implements CustomPacketPayload {
        public static final Type<DialogueOpenS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dialogue_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueOpenS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DialogueOpenS2C::id,
                ByteBufCodecs.STRING_UTF8, DialogueOpenS2C::affinity,
                ByteBufCodecs.STRING_UTF8, DialogueOpenS2C::vars,
                ByteBufCodecs.STRING_UTF8, DialogueOpenS2C::startScene,
                ByteBufCodecs.VAR_INT, DialogueOpenS2C::startLine,
                DialogueOpenS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라 → 서버: 지금 보고 있는 곳. 접속이 끊기거나 서버가 꺼져도 여기서부터 다시 연다. */
    public record DialogueProgressC2S(String id, String scene, int line) implements CustomPacketPayload {
        public static final Type<DialogueProgressC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dialogue_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueProgressC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DialogueProgressC2S::id,
                ByteBufCodecs.STRING_UTF8, DialogueProgressC2S::scene,
                ByteBufCodecs.VAR_INT, DialogueProgressC2S::line,
                DialogueProgressC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라: "대화를 닫아". 내용 없음. */
    public record DialogueStopS2C() implements CustomPacketPayload {
        public static final DialogueStopS2C INSTANCE = new DialogueStopS2C();
        public static final Type<DialogueStopS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dialogue_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueStopS2C> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라 → 서버: 선택지/장면 이벤트. add = 호감도 변화 (4바이트 정수). */
    public record DialogueEventC2S(String id, String event, String npc, int add) implements CustomPacketPayload {
        public static final Type<DialogueEventC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dialogue_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueEventC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DialogueEventC2S::id,
                ByteBufCodecs.STRING_UTF8, DialogueEventC2S::event,
                ByteBufCodecs.STRING_UTF8, DialogueEventC2S::npc,
                ByteBufCodecs.INT, DialogueEventC2S::add,
                DialogueEventC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라 → 서버: 대화가 끝남. */
    public record DialogueDoneC2S(String id, String lastScene) implements CustomPacketPayload {
        public static final Type<DialogueDoneC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "dialogue_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueDoneC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DialogueDoneC2S::id,
                ByteBufCodecs.STRING_UTF8, DialogueDoneC2S::lastScene,
                DialogueDoneC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
