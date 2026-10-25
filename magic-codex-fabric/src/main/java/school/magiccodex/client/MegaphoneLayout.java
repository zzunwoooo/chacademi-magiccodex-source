package school.magiccodex.client;

import net.minecraft.util.Identifier;

/**
 * 확성기 (메모 #102) 화면 배치·텍스처를 한곳에 모은 것. <b>디자인 납품 대기 — 교체 지점은 이 클래스</b>.
 * 오너가 확성기 화면 디자인을 GPT 에게 맡겼다 (2026-10-26). 지금은 임시로 기존 텍스처를 쓴다:
 * 입력 창 = 일러스트 다시 그리기 입력창(PortraitPromptScreen)의 입력칸·버튼(dialogue/choice-button.png) + 반투명 패널,
 * 화면 위 공지 = 공통 알림 카드 바탕(notification/toast_background.png 9-slice, NotificationSkin).
 * GPT 납품 PNG 가 오면 아래 경로·원본 크기·모서리·좌표만 바꾸면 된다 (동작 코드 MegaphoneScreen·MegaphoneClient 는 그대로).
 */
final class MegaphoneLayout {
    private MegaphoneLayout() {}

    static final Identifier FONT = Identifier.of("magiccodex", "label"), BOLD = Identifier.of("magiccodex", "hud_bold"),
            TITLE_FONT = Identifier.of("magiccodex", NotificationPresentation.TITLE_FACE);

    // ------------------------------------------------------------------ 입력 창 (1600x900 설계 좌표, 화면에 맞춰 비율 유지)
    static final float DESIGN_W = 1600, DESIGN_H = 900;
    /**
     * 입력 창 패널 PNG (납품 전에는 없음 → 아래 PANEL_FILL/PANEL_BORDER 로 반투명 사각형).
     * 납품되면 이 경로에 넣고 PANEL_TEX_W/H 를 PNG 크기로.
     */
    static final Identifier PANEL = Identifier.of("magiccodex", "textures/gui/megaphone/input_panel.png");
    static final int PANEL_TEX_W = 840, PANEL_TEX_H = 174;
    static final int PANEL_X = 380, PANEL_Y = 360, PANEL_W = 840, PANEL_H = 174;
    static final int PANEL_FILL = 0xC0101D2C, PANEL_BORDER = 0xFF647F96;
    static final float TITLE_X = 414, TITLE_Y = 398, TITLE_SIZE = 28;
    static final int TITLE_COLOR = 0xFFE8D19B;
    static final float CLOSE_X = 1170, CLOSE_Y = 394; static final int CLOSE_HIT = 32;
    /** 입력칸·보내기 버튼 바탕 (임시: 대화 선택지 버튼 PNG 의 한 부분). */
    static final Identifier FIELD = Identifier.of("magiccodex", "textures/gui/dialogue/choice-button.png");
    static final int FIELD_U = 73, FIELD_V = 270, FIELD_SRC_W = 1952, FIELD_SRC_H = 210, FIELD_TEX_W = 2098, FIELD_TEX_H = 749;
    static final int INPUT_X = 414, INPUT_Y = 440, INPUT_W = 620, INPUT_H = 60;
    static final float INPUT_TEXT_X = 436, INPUT_TEXT_SIZE = 23, INPUT_TEXT_MAX = 560;
    static final int SEND_X = 1048, SEND_Y = 440, SEND_W = 138, SEND_H = 60;
    static final float SEND_TEXT_SIZE = 22;
    static final int TEXT = 0xFFF0F1F4, MUTED = 0xFF9FB0C0, CARET = 0xFF91E8F5, BUTTON_IDLE = 0xFFCBD5DD, HOVER = 0xFF92E7F2;

    // ------------------------------------------------------------------ 화면 위 공지 (1920x1080 기준 단위, 화면에 맞춰 비율 유지)
    /** 공지 카드 9-slice 바탕 (임시: 공통 알림 카드). */
    static final Identifier CARD = NotificationSkin.BACKGROUND;
    static final int CARD_TEX_W = NotificationSkin.BG_W, CARD_TEX_H = NotificationSkin.BG_H, CARD_CORNER = NotificationSkin.BG_CORNER;
    /** 카드 단위 1 당 PNG 모서리 비율 (공통 알림과 같은 두께: 0.4 × 640/560). */
    static final float CARD_CORNER_SCALE = NotificationSkin.BG_SCALE * SharedNotifications.CARD_SCALE;
    static final float CARD_W = 640, TOP = 24;
    /** 아이템 아이콘: 카드 바깥 위 가운데에 떠 있다 (카드에 걸치지 않음). ICON 크기로 바로 그린다 (작게 그려 키우지 않음). */
    static final float ICON = 80, ICON_GAP = 6;
    static final float PAD_X = 36, PAD_TOP = 20, TITLE_LINE = 32, BODY_LINE = 26, PAD_BOTTOM = 28;
    static final float NAME_SIZE = 24, BODY_SIZE = 19, HINT_SIZE = 14;
    static final int BODY_MAX_LINES = 3;
    static final int NAME_COLOR = NotificationSkin.TITLE, BODY_COLOR = NotificationSkin.BODY, HINT_COLOR = NotificationSkin.MUTED, BAR_COLOR = NotificationSkin.GOLD;
    /** 남은 시간 줄: 카드 안 아래 (FriendRequestToast 와 같은 위치·굵기). */
    static final float BAR_INSET = 24, BAR_BOTTOM = 13, BAR_H = 2;
    /** 툴팁: 카드 아래쪽으로 떨어뜨려 글씨를 가리지 않게. */
    static final float TOOLTIP_GAP = 10;

    /** 공지 한 장의 세로 배치 (카드 단위). 아이템이 있으면 아이콘이 카드 위에 따로 서고 카드는 그 아래. */
    record Notice(float iconY, float cardY, float cardH, float nameY, float bodyY) {}

    static Notice notice(boolean item, int bodyLines) {
        float cardY = item ? TOP + ICON + ICON_GAP : TOP;
        int lines = Math.max(1, Math.min(BODY_MAX_LINES, bodyLines));
        float h = PAD_TOP + TITLE_LINE + lines * BODY_LINE + PAD_BOTTOM;
        float nameY = cardY + PAD_TOP + TITLE_LINE / 2f;
        float bodyY = cardY + PAD_TOP + TITLE_LINE + BODY_LINE / 2f;
        return new Notice(TOP, cardY, h, nameY, bodyY);
    }
}
