package kr.chacademy.story.compat;

import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * MagicCodex UI 모드(magiccodex)가 이미 가지고 있는 값을 빌려 쓴다. 그 모드가 없거나 버전이 달라도 조용히 넘어간다.
 * <ul>
 *   <li>한글 닉네임: school.magiccodex.client.NicknameClient.display(String)</li>
 *   <li>내 일러스트 (ChacaPortrait 로 만든 AI 그림): PortraitClient.ready() / drawTurn(DrawContext)</li>
 * </ul>
 * 일러스트는 MagicCodex 가 받아 둔 텍스처를 그대로 그리기만 한다. 서버에 요청을 보내거나 텍스처를 만들고 지우지 않으므로
 * ChacaPortrait 의 전송·캐시 흐름과 부딪히지 않는다.
 */
public final class MagicCodexClientLink {
    private static final Logger LOGGER = LoggerFactory.getLogger("chaca_story");
    private static boolean looked;
    private static Method display, ready, drawTurn;

    private MagicCodexClientLink() {}

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> nick = Class.forName("school.magiccodex.client.NicknameClient");
            display = nick.getMethod("display", String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        try {
            Class<?> portrait = Class.forName("school.magiccodex.client.PortraitClient");
            ready = portrait.getDeclaredMethod("ready");
            drawTurn = portrait.getDeclaredMethod("drawTurn", GuiGraphics.class);
            ready.setAccessible(true);
            drawTurn.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            ready = drawTurn = null;
        }
        LOGGER.info("MagicCodex 연결: 한글 닉네임 {}, 내 일러스트 {}", display != null ? "O" : "X", drawTurn != null ? "O" : "X");
    }

    /** MagicCodex 한글 닉네임. 모르면 fallback. */
    public static String nickname(String fallback) {
        look();
        if (display == null) return fallback;
        try {
            Object v = display.invoke(null, fallback);
            return v instanceof String s && !s.isBlank() ? s : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    /** 내 일러스트가 받아져 있는지. */
    public static boolean portraitReady() {
        look();
        if (ready == null) return false;
        try {
            return Boolean.TRUE.equals(ready.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            ready = null;
            return false;
        }
    }

    /**
     * MagicCodex 대화창의 "내 차례"와 같은 자리·크기로 내 일러스트를 그린다.
     * 좌표계는 1600x900 (호출 쪽에서 pose 로 맞춰 줌).
     */
    public static boolean drawPortrait1600(GuiGraphics g) {
        if (!portraitReady() || drawTurn == null) return false;
        try {
            drawTurn.invoke(null, g);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("MagicCodex 일러스트 그리기 실패, 끔: {}", e.toString());
            drawTurn = null;
            return false;
        }
    }
}
