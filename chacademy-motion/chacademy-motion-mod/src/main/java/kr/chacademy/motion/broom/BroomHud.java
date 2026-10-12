package kr.chacademy.motion.broom;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** 조준점 아래 작은 균형 게이지 + 속도, 연습 빗자루 남은 시간, 다시 잡기 안내. */
public final class BroomHud {
    private BroomHud() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        var font = mc.font;

        if (BroomFlight.catchTicks() > 0) {
            boolean blink = (mc.player.tickCount / 3) % 2 == 0;
            g.drawCenteredString(font, "스페이스! 빗자루를 잡으세요", cx, cy + 18, blink ? 0xFFFFE066 : 0xFFFFB020);
            return;
        }
        var ph = BroomFlight.physics();
        if (ph == null) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float balance = Mth.lerp(pt, ph.prevBalance, ph.balance) / 100f;

        // 균형 게이지 (60x3)
        int w = 60, x0 = cx - w / 2, y0 = cy + 14;
        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + 4, 0x90000000);
        int color;
        if (balance > 0.6f) color = 0xFF7CE07C;
        else if (balance > 0.25f) color = 0xFFF2C94C;
        else color = (mc.player.tickCount / 2) % 2 == 0 ? 0xFFFF4040 : 0xFFB02020;
        g.fill(x0, y0, x0 + Math.round(w * balance), y0 + 3, color);

        // 부스트 재사용 대기 (얇은 줄)
        var ride = BroomFlight.ride();
        if (ride != null && ph.boostCooldown > 0 && ride.boostCooldown() > 0) {
            float c = 1f - ph.boostCooldown / (float) ride.boostCooldown();
            g.fill(x0, y0 + 5, x0 + Math.round(w * c), y0 + 6, 0xA0A0D8FF);
        } else if (ph.boostTicks > 0) {
            g.fill(x0, y0 + 5, x0 + w, y0 + 6, 0xFFA0D8FF);
        }

        // 속도 (초당 블록)
        String speed = Math.round(ph.horizontalSpeed() * 20) + " b/s";
        g.drawString(font, speed, x0 + w + 5, y0 - 3, 0xFFE8E8E8, true);

        int left = BroomFlight.limitLeft();
        if (left > 0) {
            int sec = (left + 19) / 20;
            g.drawCenteredString(font, "연습용 빗자루 " + sec + "초", cx, y0 + 9, sec <= 3 ? 0xFFFF7060 : 0xFFD0D0D0);
        }
    }
}
