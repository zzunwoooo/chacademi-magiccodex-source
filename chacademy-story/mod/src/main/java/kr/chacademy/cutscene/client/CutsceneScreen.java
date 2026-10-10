package kr.chacademy.cutscene.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import kr.chacademy.cutscene.data.Cutscene;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 컷신 재생 화면.
 * <ul>
 *   <li>위아래 레터박스가 내려옴</li>
 *   <li>장면마다 지정한 지점으로 천천히 줌, 이미지가 여러 장이면 반복 재생</li>
 *   <li>잉크 번짐 / 페이드 / 컷 전환</li>
 *   <li>아래 띠에 자막이 한 글자씩</li>
 *   <li>오른쪽 위 &gt;&gt; 버튼으로 2배속, 건너뛰기 가능하면 스페이스 2초 꾹</li>
 * </ul>
 */
public class CutsceneScreen extends Screen {
    private static final double SKIP_HOLD_SECONDS = 2.0;

    public record Result(String id, boolean skipped, boolean completed) {
    }

    private final Cutscene cutscene;
    private final CutsceneTextures textures;
    private final boolean skippable;
    private final Consumer<Result> onEnd;
    private final ResourceLocation subtitleFont;

    private double time = 0;
    private long lastNanos = -1;
    private boolean doubleSpeed = false;
    private double skipHold = 0;
    private boolean ended = false;

    private int transitionScene = -1;
    private Transition transition;

    private int speedX0, speedY0, speedX1, speedY1;

    /** 타자기 소리: 어느 자막의 몇 글자까지 소리를 냈는지. */
    private Cutscene.Line soundLine;
    private int soundShown = 0;
    private long lastTickNanos = 0;
    private final SoundEvent typeSound;
    /** default = ChacaNPC/MagicCodex 대화창과 같은 소리 (버튼 클릭음, 높이 1.8, 크기 0.07, 45ms 간격). */
    private final boolean dialogueTick;

    public CutsceneScreen(Cutscene cutscene, CutsceneTextures textures, boolean skippable, Consumer<Result> onEnd) {
        super(Component.literal(cutscene.title()));
        this.cutscene = cutscene;
        this.textures = textures;
        this.skippable = skippable;
        this.onEnd = onEnd;
        String weight = switch (cutscene.font()) {
            case "light", "l" -> "l";
            case "bold", "b" -> "b";
            default -> "m";
        };
        this.subtitleFont = ResourceLocation.fromNamespaceAndPath("chaca_story", "subtitle_" + weight);
        String ts = cutscene.typeSound();
        this.dialogueTick = ts.equalsIgnoreCase("default");
        if (ts.equalsIgnoreCase("none") || ts.isEmpty()) {
            this.typeSound = null;
        } else if (dialogueTick) {
            this.typeSound = SoundEvents.UI_BUTTON_CLICK.value();
        } else {
            ResourceLocation soundId = ResourceLocation.tryParse(ts);
            this.typeSound = soundId == null ? null : SoundEvent.createVariableRangeEvent(soundId);
        }
    }

    // ---------------------------------------------------------------- 진행

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double realDt = lastNanos < 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        time += realDt * (doubleSpeed ? 2 : 1);

        if (skippable && minecraft != null
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)) {
            skipHold += realDt;
            if (skipHold >= SKIP_HOLD_SECONDS) {
                finish(true);
                return;
            }
        } else {
            skipHold = Math.max(0, skipHold - realDt * 3);
        }

        if (time >= cutscene.totalDuration()) {
            finish(false);
            return;
        }
        updateBgm();

        int w = this.width, h = this.height;
        g.fill(0, 0, w, h, 0xFF000000);

        // 지금 장면 찾기
        int index = 0;
        double sceneStart = 0;
        for (int i = 0; i < cutscene.scenes().size(); i++) {
            double d = cutscene.scenes().get(i).duration();
            if (time < sceneStart + d || i == cutscene.scenes().size() - 1) {
                index = i;
                break;
            }
            sceneStart += d;
        }
        Cutscene.Scene scene = cutscene.scenes().get(index);
        double local = Math.min(time - sceneStart, scene.duration());

        boolean inTransition = !"cut".equals(scene.transition())
                && scene.transitionTime() > 0 && local < scene.transitionTime();
        if (inTransition) {
            if (index > 0) {
                Cutscene.Scene prev = cutscene.scenes().get(index - 1);
                drawScene(g, prev, frameName(prev, time), prev.duration(), null);
            }
            Transition t = transitionFor(index, scene);
            double p = easeInOut(local / scene.transitionTime());
            if (t != null) {
                t.update(p);
                drawScene(g, scene, scene.images().get(0), local, t.tex);
            } else {
                drawScene(g, scene, frameName(scene, time), local, null);
            }
        } else {
            drawScene(g, scene, frameName(scene, time), local, null);
            if (transitionScene != index) dropTransition();
        }

        // 레터박스
        double barFull = h * cutscene.letterbox();
        double barK = cutscene.letterboxTime() <= 0 ? 1 : easeInOut(Math.min(1, time / cutscene.letterboxTime()));
        int bar = (int) Math.round(barFull * barK);
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF0B0B0B);
            g.fill(0, h - bar, w, h, 0xFF0B0B0B);
        }

        drawSubtitle(g, scene, local, barFull, w, h);

        // 마지막 암전
        double over = time - cutscene.scenesDuration();
        if (over > 0 && cutscene.endFade() > 0) {
            int a = (int) Math.round(255 * Math.min(1, over / cutscene.endFade()));
            g.fill(0, 0, w, h, (a << 24));
        }

        drawSpeedButton(g, mouseX, mouseY, w, Math.max(bar, 14));
        if (skippable) drawSkipRing(g, w, h, Math.max(bar, 22));
    }

    private String frameName(Cutscene.Scene scene, double globalTime) {
        int n = scene.images().size();
        if (n == 1) return scene.images().get(0);
        int f = (int) Math.floor(globalTime * scene.fps()) % n;
        return scene.images().get(f);
    }

    /** 장면 그림을 화면을 꽉 채우게(cover) 그리고 줌을 건다. override 가 있으면 그 텍스처로 그림. */
    private void drawScene(GuiGraphics g, Cutscene.Scene scene, String imageName, double local,
                           CutsceneTextures.Tex override) {
        CutsceneTextures.Tex tex = override != null ? override : textures.get(imageName);
        if (tex == null) return;
        int w = this.width, h = this.height;
        double s = Math.max((double) w / tex.width(), (double) h / tex.height());
        double dw = tex.width() * s, dh = tex.height() * s;
        double dx = (w - dw) / 2, dy = (h - dh) / 2;

        double k = scene.duration() <= 0 ? 1 : easeInOut(Math.min(1, local / scene.duration()));
        double zoom = scene.zoomFrom() + (scene.zoomTo() - scene.zoomFrom()) * k;
        double zx = dx + scene.zoomX() * dw, zy = dy + scene.zoomY() * dh;

        g.pose().pushPose();
        g.pose().translate(zx, zy, 0);
        g.pose().scale((float) zoom, (float) zoom, 1);
        g.pose().translate(-zx, -zy, 0);
        // 반올림 틈이 보이지 않게 1px 크게
        g.blit(RenderType::guiTextured, tex.id(),
                (int) Math.floor(dx), (int) Math.floor(dy), 0, 0,
                (int) Math.ceil(dw) + 1, (int) Math.ceil(dh) + 1,
                tex.width(), tex.height(), tex.width(), tex.height());
        g.pose().popPose();
    }

    private void drawSubtitle(GuiGraphics g, Cutscene.Scene scene, double local, double barFull, int w, int h) {
        Cutscene.Line line = null;
        for (Cutscene.Line l : scene.lines()) {
            if (local >= l.start() && local < l.end()) line = l; // 겹치면 나중 것
        }
        if (line == null) return;
        boolean auto = cutscene.textY() < 0;
        if (auto && barFull < 4) return;

        String body = line.text();
        int total = body.codePointCount(0, body.length());
        int shown = cutscene.typeSpeed() <= 0 ? total
                : Math.min(total, (int) Math.floor((local - line.start()) / cutscene.typeSpeed()) + 1);
        String text = cutscene.prefix() + body.substring(0, body.offsetByCodePoints(0, Math.max(0, shown)));
        typeSoundFor(line, body, shown);

        double alpha = Math.min(1, (local - line.start()) / 0.15);
        alpha = Math.min(alpha, (line.end() - local) / 0.3);
        alpha = Math.max(0, Math.min(1, alpha));
        int a = Math.max(5, (int) Math.round(alpha * 255));

        Style style = Style.EMPTY.withFont(subtitleFont).withItalic(cutscene.italic());
        Component c = Component.literal(text).withStyle(style);
        Font font = this.font;
        // 크기 기준은 레터박스 높이 (레터박스가 0이면 화면 높이의 14%로 계산)
        double base = barFull >= 4 ? barFull : h * 0.14;
        float scale = (float) Math.max(0.3, base * 0.34 * cutscene.fontSize() / font.lineHeight);
        int tw = font.width(c);
        double cx = w * cutscene.textX();
        double cy = auto ? h - barFull / 2.0 : h * cutscene.textY();
        float maxW = (float) (2 * Math.min(cx, w - cx) * 0.95);
        if (maxW > 0 && tw * scale > maxW) scale = maxW / tw;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, c, -tw / 2, -font.lineHeight / 2, (a << 24) | (cutscene.textColor() & 0xFFFFFF),
                cutscene.textShadow());
        g.pose().popPose();
    }

    /** 새 글자가 써질 때마다 타자기 소리. 공백은 소리 없음, 너무 촘촘하면 건너뜀. */
    private void typeSoundFor(Cutscene.Line line, String body, int shown) {
        if (line != soundLine) {
            soundLine = line;
            soundShown = 0;
        }
        if (typeSound == null || minecraft == null || shown <= soundShown) return;
        int prev = soundShown;
        soundShown = shown;
        String added = body.substring(body.offsetByCodePoints(0, prev), body.offsetByCodePoints(0, shown));
        if (added.isBlank()) return;
        long now = System.nanoTime();
        if (now - lastTickNanos < 45_000_000L) return;
        lastTickNanos = now;
        if (dialogueTick) {
            // type_sound_volume 0.5 = 대화창과 똑같은 크기
            float volume = (float) (0.07 * cutscene.typeSoundVolume() / 0.5);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(typeSound, 1.8f, volume));
        } else {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(typeSound, 1.0f, (float) cutscene.typeSoundVolume()));
        }
    }

    /** 배경음 크기: 처음 bgm_fade_in 초 동안 커지고, 끝나기 전 bgm_fade_out 초 동안 줄어듦. */
    private void updateBgm() {
        if (!BgmPlayer.playing()) return;
        double in = cutscene.bgmFadeIn() <= 0 ? 1 : Math.min(1, time / cutscene.bgmFadeIn());
        double left = cutscene.totalDuration() - time;
        double out = cutscene.bgmFadeOut() <= 0 ? 1 : Math.min(1, left / cutscene.bgmFadeOut());
        BgmPlayer.setLevel(smooth(in) * smooth(out));
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private void drawSpeedButton(GuiGraphics g, int mouseX, int mouseY, int w, int bar) {
        Component label = Component.literal(doubleSpeed ? ">> 2x" : ">> 1x");
        int tw = font.width(label);
        int pad = 6;
        speedX1 = w - 10;
        speedX0 = speedX1 - tw - pad * 2;
        speedY0 = Math.max(2, bar / 2 - 8);
        speedY1 = speedY0 + 16;
        boolean hover = mouseX >= speedX0 && mouseX < speedX1 && mouseY >= speedY0 && mouseY < speedY1;
        int border = doubleSpeed ? 0xFFD9A46A : (hover ? 0xFFBBBBBB : 0xFF666666);
        g.fill(speedX0, speedY0, speedX1, speedY1, hover ? 0x40FFFFFF : 0x20FFFFFF);
        g.renderOutline(speedX0, speedY0, speedX1 - speedX0, speedY1 - speedY0, border);
        g.drawString(font, label, speedX0 + pad, speedY0 + 4, doubleSpeed ? 0xFFD9A46A : 0xFFDDDDDD, false);
    }

    private void drawSkipRing(GuiGraphics g, int w, int h, int bar) {
        int cx = w - 18, cy = h - bar / 2;
        int r = 7;
        double progress = Math.min(1, skipHold / SKIP_HOLD_SECONDS);
        int dots = 72;
        for (int i = 0; i < dots; i++) {
            double ang = -Math.PI / 2 + i * 2 * Math.PI / dots;
            int x = (int) Math.round(cx + Math.cos(ang) * r);
            int y = (int) Math.round(cy + Math.sin(ang) * r);
            boolean on = i < progress * dots;
            g.fill(x, y, x + 1, y + 1, on ? 0xFFEFE3C8 : 0x55EFE3C8);
        }
        Component hint = Component.translatable("chaca_story.hold_skip");
        int tw = font.width(hint);
        int a = skipHold > 0 ? 0xDD : 0x77;
        g.pose().pushPose();
        g.pose().translate(cx - r - 6, cy, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, hint, -tw, -4, (a << 24) | 0xEFE3C8, false);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- 전환

    private Transition transitionFor(int index, Cutscene.Scene scene) {
        if (transitionScene == index) return transition;
        dropTransition();
        transitionScene = index;
        CutsceneTextures.Tex src = textures.get(scene.images().get(0));
        if (src == null) return null;
        transition = new Transition(src, "fade".equals(scene.transition()),
                scene.zoomX(), scene.zoomY(), index + 1);
        return transition;
    }

    private void dropTransition() {
        // 전환 텍스처는 CutsceneTextures 가 끝날 때 한꺼번에 내린다. 참조만 끊음.
        transition = null;
        transitionScene = -1;
    }

    /** 새 장면 그림에 알파를 입혀 앞 장면 위로 서서히 드러나게 하는 텍스처. */
    private final class Transition {
        final CutsceneTextures.Tex tex;
        final int[] src;
        final float[] threshold;
        final boolean fade;
        final int width, height;
        double lastP = -1;

        Transition(CutsceneTextures.Tex source, boolean fade, double cx, double cy, int seed) {
            this.width = source.width();
            this.height = source.height();
            this.fade = fade;
            this.src = new int[width * height];
            NativeImage s = source.image();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) src[y * width + x] = s.getPixel(x, y);
            }
            this.threshold = fade ? null : InkMask.upscale(InkMask.build(width, height, cx, cy, seed), width, height);
            this.tex = textures.register(new NativeImage(width, height, true));
            update(0);
        }

        void update(double p) {
            if (Math.abs(p - lastP) < 0.002) return;
            lastP = p;
            NativeImage out = tex.image();
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    int px = src[row + x];
                    double a = fade ? p : InkMask.alpha(p, threshold[row + x]);
                    int srcA = (px >>> 24) & 0xFF;
                    int na = (int) Math.round(srcA * a);
                    out.setPixel(x, y, (na << 24) | (px & 0x00FFFFFF));
                }
            }
            textures.upload(tex);
        }
    }

    // ---------------------------------------------------------------- 입력과 종료

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= speedX0 && mouseX < speedX1 && mouseY >= speedY0 && mouseY < speedY1) {
            doubleSpeed = !doubleSpeed;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true; // ESC 포함 모든 키 무시 (건너뛰기는 스페이스 꾹)
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 배경은 render 에서 직접 그림
    }

    /** 서버가 멈추라고 하면 호출. 끝난 것으로 치지 않음. */
    public void stop() {
        if (ended) return;
        ended = true;
        BgmPlayer.release(1.5);
        textures.close();
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    private void finish(boolean skipped) {
        if (ended) return;
        ended = true;
        BgmPlayer.release(skipped ? 1.5 : 0.5);
        textures.close();
        if (minecraft != null) minecraft.setScreen(null);
        onEnd.accept(new Result(cutscene.id(), skipped, !skipped));
    }

    @Override
    public void removed() {
        // 접속 끊김 등으로 화면이 사라진 경우
        if (!ended) {
            ended = true;
            BgmPlayer.release(1.0);
            textures.close();
        }
    }

    private static double easeInOut(double t) {
        t = Math.max(0, Math.min(1, t));
        return 0.5 - 0.5 * Math.cos(Math.PI * t);
    }
}
