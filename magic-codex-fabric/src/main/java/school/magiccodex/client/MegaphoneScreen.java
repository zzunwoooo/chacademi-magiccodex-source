package school.magiccodex.client;

import static school.magiccodex.client.MegaphoneLayout.*;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * 확성기 입력 창 (메모 #102). 오너 결정: 창은 제목·입력칸·보내기 버튼만, 패널만 반투명, 화면을 어둡게 가리거나 흐리게 하지 않는다.
 * 아이템은 고르지 않는다 (보낼 때 서버가 반대 손 아이템을 읽음). 배치·텍스처는 MegaphoneLayout (디자인 납품 대기).
 * Enter·보내기 = 보내기, Esc·X = 취소(확성기 그대로). 입력 동작은 PortraitPromptScreen 과 같다.
 */
final class MegaphoneScreen extends Screen {
    private final long token;
    private final int maxChars;
    private TextFieldWidget input;
    private boolean done;

    MegaphoneScreen(long token, int maxChars) { super(Text.literal("확성기")); this.token = token; this.maxChars = maxChars; }

    long token() { return token; }

    @Override protected void init() {
        String draft = input == null ? "" : input.getText();
        input = new TextFieldWidget(textRenderer, 0, 0, 800, 30, Text.literal("확성기"));
        input.setMaxLength(maxChars); input.setText(draft); input.setFocused(true);
    }
    private CodexLayout fit() { float s = Math.min(width / DESIGN_W, height / DESIGN_H); return new CodexLayout((width - DESIGN_W * s) / 2, (height - DESIGN_H * s) / 2, s); }
    @Override public boolean shouldPause() { return false; }
    /** 어둡게 가리기·블러 없음 (오너). */
    @Override public void renderBackground(DrawContext c, int x, int y, float delta) {}
    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs(); var f = fit(); double mx = f.localX(mouseX), my = f.localY(mouseY);
        var images = UiResources.images(); images.beginFrame(); UiResources.text().beginFrame();
        c.getMatrices().push();
        try {
            c.getMatrices().translate(f.x(), f.y(), 0); c.getMatrices().scale(f.scale(), f.scale(), 1);
            if (client != null && client.getResourceManager().getResource(PANEL).isPresent())
                images.drawTexture(c, PANEL, PANEL_X, PANEL_Y, 0, 0, PANEL_W, PANEL_H, PANEL_TEX_W, PANEL_TEX_H, PANEL_TEX_W, PANEL_TEX_H, 0xFFFFFFFF);
            else { c.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_W, PANEL_Y + PANEL_H, PANEL_FILL); c.drawBorder(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_BORDER); }
            label(c, "확성기", TITLE_X, TITLE_Y, TITLE_SIZE, TITLE_COLOR, true, false, 600);
            FriendsScreen.cross(c, CLOSE_X, CLOSE_Y, hit(mx, my, (int) CLOSE_X - CLOSE_HIT / 2, (int) CLOSE_Y - CLOSE_HIT / 2, CLOSE_HIT, CLOSE_HIT) ? HOVER : 0xFFF0F3F5);
            images.drawTexture(c, FIELD, INPUT_X, INPUT_Y, FIELD_U, FIELD_V, INPUT_W, INPUT_H, FIELD_SRC_W, FIELD_SRC_H, FIELD_TEX_W, FIELD_TEX_H, 0xFFFFFFFF);
            drawInput(c, now);
            boolean hot = hit(mx, my, SEND_X, SEND_Y, SEND_W, SEND_H);
            images.drawTexture(c, FIELD, SEND_X, SEND_Y, FIELD_U, FIELD_V, SEND_W, SEND_H, FIELD_SRC_W, FIELD_SRC_H, FIELD_TEX_W, FIELD_TEX_H, hot ? 0xFFFFFFFF : BUTTON_IDLE);
            label(c, "보내기", SEND_X + SEND_W / 2f, SEND_Y + SEND_H / 2f, SEND_TEXT_SIZE, 0xFFF0F3F5, false, true, SEND_W - 20);
        } finally { c.getMatrices().pop(); images.endFrame(); }
    }
    private void drawInput(DrawContext c, long now) {
        String value = input.getText(); float y = INPUT_Y + INPUT_H / 2f;
        if (value.isEmpty()) { if (now / 500 % 2 == 0) c.fill((int) INPUT_TEXT_X, (int) y - 14, (int) INPUT_TEXT_X + 2, (int) y + 14, CARET); return; }
        int cursor = Math.min(input.getCursor(), value.length()), from = 0;
        while (from < cursor && UiResources.text().width(value.substring(from, cursor), INPUT_TEXT_SIZE, FONT) > INPUT_TEXT_MAX) from++;
        int to = value.length();
        while (to > from && UiResources.text().width(value.substring(from, to), INPUT_TEXT_SIZE, FONT) > INPUT_TEXT_MAX) to--;
        label(c, value.substring(from, to), INPUT_TEXT_X, y, INPUT_TEXT_SIZE, TEXT, false, false, INPUT_TEXT_MAX);
        if (now / 500 % 2 == 0) { int x = (int) (INPUT_TEXT_X + UiResources.text().width(value.substring(from, Math.max(from, cursor)), INPUT_TEXT_SIZE, FONT)); c.fill(x, (int) y - 14, x + 2, (int) y + 14, CARET); }
    }
    private void label(DrawContext c, String value, float x, float y, float size, int color, boolean bold, boolean centered, float max) {
        var font = bold ? BOLD : FONT; float actual = Math.min(size, size * max / Math.max(max, UiResources.text().width(value, size, font)));
        UiResources.text().draw(c, value, x, y, actual, color, font, centered);
    }
    private static boolean hit(double x, double y, int a, int b, int w, int h) { return x >= a && x < a + w && y >= b && y < b + h; }
    private void click() { if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, .12f)); }
    private void submit() {
        if (done) return;
        String value = input.getText().strip();
        if (value.isEmpty()) return;   // 빈 문장은 보내지 않는다 (창 유지)
        done = true; click();
        // 결과(소모·거절 사유)는 서버가 공지·알림으로 알린다. 보내지 못했으면 확성기는 그대로다.
        MegaphoneClient.answer(token, value, true);
        if (client != null) client.setScreen(null);
    }
    void serverClosed() { done = true; if (client != null) client.setScreen(null); }
    @Override public void close() { if (!done) { done = true; MegaphoneClient.answer(token, "", false); } if (client != null) client.setScreen(null); }
    @Override public void removed() { if (!done) { done = true; MegaphoneClient.answer(token, "", false); } super.removed(); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return input.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char ch, int mods) { return input.charTyped(ch, mods); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false; var f = fit(); double mx = f.localX(x), my = f.localY(y);
        if (hit(mx, my, SEND_X, SEND_Y, SEND_W, SEND_H)) { submit(); return true; }
        if (hit(mx, my, (int) CLOSE_X - CLOSE_HIT / 2, (int) CLOSE_Y - CLOSE_HIT / 2, CLOSE_HIT, CLOSE_HIT)) { click(); close(); return true; }
        if (hit(mx, my, INPUT_X, INPUT_Y, INPUT_W, INPUT_H)) { input.setFocused(true); return true; }
        return true;
    }
}
