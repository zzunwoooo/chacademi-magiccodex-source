package kr.chacademy.story.dialogue;

import kr.chacademy.cutscene.client.CutsceneTextures;
import kr.chacademy.story.compat.MagicCodexClientLink;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 메인 스토리 대화 화면 (비주얼노벨 형식).
 * <ul>
 *   <li>아래 대화창 + 화자 이름표, 왼쪽에 화자 일러스트 (대사마다 표정 변경)</li>
 *   <li>"나"가 말할 때는 ChacaPortrait 로 만든 내 일러스트 (MagicCodex 모드가 받아 둔 것)를 같은 자리에</li>
 *   <li>{player} = MagicCodex 한글 닉네임, {account} = 마인크래프트 닉네임, %...% = 서버가 보낸 플레이스홀더 값</li>
 *   <li>대사는 한 글자씩, 클릭/SPACE 로 바로 보기 → 다음 대사</li>
 *   <li>마지막 대사 뒤 선택지 (클릭 또는 숫자키), 호감도 조건에 맞는 것만 보임</li>
 *   <li>장면에 들어올 때 호감도에 따라 다른 장면으로 보내기 (redirect)</li>
 * </ul>
 */
public class DialogueScreen extends Screen {
    public interface Listener {
        /** 선택지를 골랐거나 이벤트가 있는 장면에 들어감. 서버가 연 대화면 서버로 보낸다. */
        void event(String dialogueId, String event, String npc, int add);

        /** 대화가 끝남. lastScene = 마지막 장면 id. */
        void done(String dialogueId, String lastScene);

        /** 지금 보고 있는 곳 (장면, 대사 번호). 서버가 저장해 두었다가 다시 접속하면 여기서부터 연다. */
        default void progress(String dialogueId, String scene, int line) {
        }
    }

    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("chaca_story", "subtitle_m");
    private static final ResourceLocation FONT_BOLD = ResourceLocation.fromNamespaceAndPath("chaca_story", "subtitle_b");
    private static final int GOLD = 0xFFC9A66B;

    private final Dialogue dialogue;
    private final CutsceneTextures textures;
    private final Map<String, Integer> affinity;
    private final Listener listener;
    private final Map<String, String> vars;

    private Dialogue.Scene scene;
    private int lineIndex = 0;
    private long lineStartNanos;
    private boolean forceComplete = false;
    private int soundShown = 0;
    private long lastTickNanos = 0;
    private boolean ended = false;

    private final Map<String, String> faces = new HashMap<>();
    private String lastPortrait = null;
    private List<Dialogue.Choice> visibleChoices = List.of();
    private final List<int[]> choiceBoxes = new ArrayList<>();
    private final java.util.Set<String> enteredAffinity = new java.util.HashSet<>();

    public DialogueScreen(Dialogue dialogue, CutsceneTextures textures, Map<String, Integer> affinity,
                          Map<String, String> vars, Listener listener) {
        this(dialogue, textures, affinity, vars, listener, "", 0);
    }

    /** startScene / startLine: 이어서 보기 (비우면 처음부터). */
    public DialogueScreen(Dialogue dialogue, CutsceneTextures textures, Map<String, Integer> affinity,
                          Map<String, String> vars, Listener listener, String startScene, int startLine) {
        super(Component.literal(dialogue.title()));
        this.dialogue = dialogue;
        this.textures = textures;
        this.affinity = new HashMap<>(affinity);
        this.listener = listener;
        this.vars = vars;
        boolean resume = startScene != null && dialogue.scenes().containsKey(startScene);
        if (resume) {
            scene = dialogue.scenes().get(startScene);
            enteredAffinity.add(startScene);
            lineIndex = Math.max(0, Math.min(startLine, Math.max(0, scene.lines().size() - 1)));
            startLine();
            report();
        } else enter(dialogue.start(), 0);
    }

    private void report() {
        if (!ended && scene != null) listener.progress(dialogue.id(), scene.id(), lineIndex);
    }

    // ---------------------------------------------------------------- 진행

    private void enter(String sceneId, int hops) {
        Dialogue.Scene s = dialogue.scenes().get(sceneId);
        if (s == null || hops > 20) {
            finish(scene == null ? sceneId : scene.id());
            return;
        }
        for (Dialogue.Redirect r : s.redirects()) {
            if (r.need().test(affinity) && dialogue.scenes().containsKey(r.to()) && !r.to().equals(sceneId)) {
                enter(r.to(), hops + 1);
                return;
            }
        }
        scene = s;
        if (!s.affinity().isEmpty() && enteredAffinity.add(s.id())) {
            for (Dialogue.AffinityChange a : s.affinity()) affinity.merge(a.npc(), a.add(), Integer::sum);
        }
        if (!s.event().isEmpty()) listener.event(dialogue.id(), s.event(), "", 0);
        lineIndex = 0;
        startLine();
        report();
        if (s.lines().isEmpty() && visibleChoices().isEmpty()) goNext(hops);
    }

    private void startLine() {
        lineStartNanos = System.nanoTime();
        forceComplete = false;
        soundShown = 0;
        if (lineIndex < scene.lines().size()) {
            Dialogue.Line line = scene.lines().get(lineIndex);
            Dialogue.Speaker sp = dialogue.speakers().get(line.who());
            if (sp != null && !sp.portraits().isEmpty()) {
                String face = !line.face().isEmpty() && sp.portraits().containsKey(line.face()) ? line.face()
                        : faces.getOrDefault(sp.id(), sp.defaultFace());
                faces.put(sp.id(), face);
                lastPortrait = sp.portraits().get(face);
            }
        }
        visibleChoices = List.of();
    }

    private List<Dialogue.Choice> visibleChoices() {
        List<Dialogue.Choice> out = new ArrayList<>();
        for (Dialogue.Choice c : scene.choices()) if (c.need() == null || c.need().test(affinity)) out.add(c);
        return out;
    }

    private boolean atLastLine() {
        return lineIndex >= scene.lines().size() - 1;
    }

    private void advance() {
        if (ended) return;
        if (lineIndex < scene.lines().size() && !lineComplete()) {
            forceComplete = true;
            return;
        }
        if (!atLastLine()) {
            lineIndex++;
            startLine();
            report();
            return;
        }
        if (!visibleChoices.isEmpty()) return; // 선택지를 골라야 함
        goNext(0);
    }

    private void goNext(int hops) {
        String next = scene.next();
        if (next.isEmpty() || !dialogue.scenes().containsKey(next)) finish(scene.id());
        else enter(next, hops + 1);
    }

    private void choose(int index) {
        if (index < 0 || index >= visibleChoices.size()) return;
        Dialogue.Choice c = visibleChoices.get(index);
        playClick();
        for (Dialogue.AffinityChange a : c.affinity()) affinity.merge(a.npc(), a.add(), Integer::sum);
        if (!c.event().isEmpty()) {
            // 실제 호감도 값은 서버 affinity.events만 사용한다. npc/add는 구형 패킷 형식 호환용이다.
            Dialogue.AffinityChange first = c.affinity().isEmpty() ? null : c.affinity().get(0);
            listener.event(dialogue.id(), c.event(), first == null ? "" : first.npc(), first == null ? 0 : first.add());
        } else if (!c.affinity().isEmpty()) {
            Dialogue.AffinityChange first = c.affinity().get(0);
            listener.event(dialogue.id(), "", first.npc(), first.add());
        }
        if (c.to().isEmpty() || !dialogue.scenes().containsKey(c.to())) finish(scene.id());
        else enter(c.to(), 0);
    }

    private void finish(String lastScene) {
        if (ended) return;
        ended = true;
        textures.close();
        if (minecraft != null) minecraft.setScreen(null);
        listener.done(dialogue.id(), lastScene == null ? "" : lastScene);
    }

    public String dialogueId() {
        return dialogue.id();
    }

    /** 생성하자마자 끝났으면 (장면이 비어 있는 등) 화면을 열 필요가 없다. */
    public boolean isEnded() {
        return ended;
    }

    /** 서버가 멈추라고 할 때. 끝난 것으로 치지 않음. */
    public void stop() {
        if (ended) return;
        ended = true;
        textures.close();
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    // ---------------------------------------------------------------- 글자

    private String fill(String s) {
        return TextVars.fill(s, vars);
    }

    private String currentText() {
        if (lineIndex >= scene.lines().size()) return "";
        return fill(scene.lines().get(lineIndex).text());
    }

    private int totalChars() {
        String t = currentText();
        return t.codePointCount(0, t.length());
    }

    private int shownChars() {
        if (forceComplete || dialogue.typeSpeed() <= 0) return totalChars();
        double elapsed = (System.nanoTime() - lineStartNanos) / 1e9;
        return (int) Math.min(totalChars(), Math.floor(elapsed / dialogue.typeSpeed()));
    }

    private boolean lineComplete() {
        return shownChars() >= totalChars();
    }

    // ---------------------------------------------------------------- 그리기

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (ended) return;
        int w = width, h = height;
        int dimA = (int) Math.round(dialogue.dim() * 255);
        if (dimA > 0) g.fill(0, 0, w, h, dimA << 24);

        int px0 = Math.round(w * 0.06f), px1 = Math.round(w * 0.94f);
        int panelH = Math.max(60, Math.round(h * 0.26f));
        int py1 = h - Math.round(h * 0.04f), py0 = py1 - panelH;

        // 일러스트 (대화창 뒤). "나"가 말하면 내 AI 일러스트를 먼저 쓴다
        boolean mine = speakerIsPlayer() && drawMyPortrait(g, w, h, py0);
        CutsceneTextures.Tex tex = mine || lastPortrait == null ? null : textures.get(lastPortrait);
        if (tex != null) {
            float ph = h * 0.74f;
            float pw = ph * tex.width() / tex.height();
            int x = px0 + Math.round((px1 - px0) * 0.02f);
            int y = py0 + Math.round(panelH * 0.3f) - Math.round(ph);
            g.blit(RenderType::guiTextured, tex.id(), x, y, 0, 0, Math.round(pw), Math.round(ph),
                    tex.width(), tex.height(), tex.width(), tex.height());
        }

        // 대화창
        g.fill(px0, py0, px1, py1, 0xE6101722);
        g.fill(px0, py0, px1, py0 + 1, GOLD);
        g.renderOutline(px0, py0, px1 - px0, py1 - py0, 0x66C9A66B);

        Font font = this.font;
        float lineH = Math.max(9f, panelH * 0.15f);
        float scale = lineH / font.lineHeight;

        // 이름표
        if (lineIndex < scene.lines().size()) {
            Dialogue.Line line = scene.lines().get(lineIndex);
            Dialogue.Speaker sp = dialogue.speakers().get(line.who());
            String name = sp == null ? "" : fill(sp.name());
            if (!name.isEmpty()) {
                Component nc = Component.literal(name).withStyle(Style.EMPTY.withFont(FONT_BOLD));
                float ns = scale * 0.95f;
                int nw = Math.round(font.width(nc) * ns) + 24;
                int nh = Math.round(font.lineHeight * ns) + 10;
                int nx = px0 + 14, ny = py0 - nh / 2;
                g.fill(nx, ny, nx + nw, ny + nh, 0xF21B2636);
                g.renderOutline(nx, ny, nw, nh, GOLD);
                drawScaled(g, nc, nx + 12, ny + 5, ns, 0xFFE8D19B);
            }
        }

        // 대사 (줄바꿈 + 타자기)
        String text = currentText();
        int shown = shownChars();
        typeSound(shown);
        int pad = Math.round(panelH * 0.12f) + 6;
        float wrapW = (px1 - px0 - pad * 2) / scale;
        List<String> rows = wrap(text, wrapW);
        int remaining = shown;
        float ty = py0 + panelH * 0.26f;
        for (int i = 0; i < rows.size() && i < 4; i++) {
            String row = rows.get(i);
            int n = Math.min(remaining, row.codePointCount(0, row.length()));
            remaining -= n;
            if (n <= 0) break;
            String part = row.substring(0, row.offsetByCodePoints(0, n));
            drawScaled(g, Component.literal(part).withStyle(Style.EMPTY.withFont(FONT)), px0 + pad, Math.round(ty), scale, 0xFFF0F1F4);
            ty += lineH * 1.45f;
        }

        boolean complete = lineComplete();
        if (complete && atLastLine()) visibleChoices = visibleChoices();

        // 진행 표시
        if (complete && (visibleChoices.isEmpty() || !atLastLine())) {
            boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
            Component hint = Component.literal(blink ? "▼" : "▽");
            drawScaled(g, hint, px1 - pad, py1 - Math.round(lineH) - 6, scale * 0.8f, 0xFFC9A66B);
        }

        // 선택지
        choiceBoxes.clear();
        if (complete && atLastLine() && !visibleChoices.isEmpty()) {
            int bw = Math.round(w * 0.34f), bh = Math.max(18, Math.round(h * 0.06f));
            int gap = Math.max(4, Math.round(bh * 0.18f));
            int bx = px1 - bw;
            int by = py0 - 14 - visibleChoices.size() * (bh + gap);
            for (int i = 0; i < visibleChoices.size(); i++) {
                int y = by + i * (bh + gap);
                boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + bh;
                g.fill(bx, y, bx + bw, y + bh, hover ? 0xF0253752 : 0xE0121A27);
                g.renderOutline(bx, y, bw, bh, hover ? 0xFFE8D19B : 0x99C9A66B);
                Component c = Component.literal((i + 1) + ".  " + fill(visibleChoices.get(i).text()))
                        .withStyle(Style.EMPTY.withFont(FONT));
                float cs = Math.min(scale, (bh * 0.5f) / font.lineHeight);
                float maxW = (bw - 20) / cs;
                if (font.width(c) > maxW) cs = (bw - 20f) / font.width(c);
                drawScaled(g, c, bx + 12, y + Math.round((bh - font.lineHeight * cs) / 2), cs, hover ? 0xFFFFFFFF : 0xFFE6E9EE);
                choiceBoxes.add(new int[]{bx, y, bw, bh});
            }
        }
    }

    private boolean speakerIsPlayer() {
        if (scene == null || lineIndex >= scene.lines().size()) return false;
        Dialogue.Speaker sp = dialogue.speakers().get(scene.lines().get(lineIndex).who());
        return sp != null && sp.isPlayer();
    }

    /** MagicCodex 대화창(1600x900 기준)과 같은 배치로 내 일러스트. 대화창 위까지만 보이게 자른다. */
    private boolean drawMyPortrait(GuiGraphics g, int w, int h, int panelTop) {
        if (!MagicCodexClientLink.portraitReady()) return false;
        float s = Math.min(w / 1600f, h / 900f);
        g.enableScissor(0, 0, w, panelTop);
        g.pose().pushPose();
        try {
            g.pose().translate((w - 1600 * s) / 2f, (h - 900 * s) / 2f, 0);
            g.pose().scale(s, s, 1);
            return MagicCodexClientLink.drawPortrait1600(g);
        } finally {
            g.pose().popPose();
            g.disableScissor();
        }
    }

    private void drawScaled(GuiGraphics g, Component c, int x, int y, float s, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /** 글자 단위 줄바꿈 (한글은 띄어쓰기가 없어도 잘림). 띄어쓰기에서 끊을 수 있으면 거기서 끊는다. */
    private List<String> wrap(String text, float maxWidth) {
        List<String> rows = new ArrayList<>();
        Style st = Style.EMPTY.withFont(FONT);
        StringBuilder cur = new StringBuilder();
        int lastSpace = -1;
        for (int cp : text.codePoints().toArray()) {
            if (cp == '\n') {
                rows.add(cur.toString());
                cur.setLength(0);
                lastSpace = -1;
                continue;
            }
            cur.appendCodePoint(cp);
            if (cp == ' ') lastSpace = cur.length();
            if (font.width(Component.literal(cur.toString()).withStyle(st)) > maxWidth && cur.length() > 1) {
                if (lastSpace > 0 && lastSpace < cur.length()) {
                    rows.add(cur.substring(0, lastSpace).stripTrailing());
                    String rest = cur.substring(lastSpace);
                    cur.setLength(0);
                    cur.append(rest);
                } else {
                    int cut = cur.offsetByCodePoints(cur.length(), -1);
                    rows.add(cur.substring(0, cut));
                    String rest = cur.substring(cut);
                    cur.setLength(0);
                    cur.append(rest);
                }
                lastSpace = -1;
            }
        }
        if (cur.length() > 0 || rows.isEmpty()) rows.add(cur.toString());
        return rows;
    }

    // ---------------------------------------------------------------- 소리

    private void typeSound(int shown) {
        if (shown <= soundShown || minecraft == null) {
            return;
        }
        String t = currentText();
        String added = t.substring(t.offsetByCodePoints(0, Math.min(soundShown, totalChars())),
                t.offsetByCodePoints(0, Math.min(shown, totalChars())));
        soundShown = shown;
        if (added.isBlank() || "none".equalsIgnoreCase(dialogue.typeSound()) || forceComplete) return;
        long now = System.nanoTime();
        if (now - lastTickNanos < 45_000_000L) return;
        lastTickNanos = now;
        SoundEvent ev;
        float pitch = 1.8f, volume = (float) (0.07 * dialogue.typeSoundVolume() / 0.5);
        if ("default".equalsIgnoreCase(dialogue.typeSound())) {
            ev = SoundEvents.UI_BUTTON_CLICK.value();
        } else {
            ResourceLocation id = ResourceLocation.tryParse(dialogue.typeSound());
            if (id == null) return;
            ev = SoundEvent.createVariableRangeEvent(id);
            pitch = 1f;
            volume = (float) dialogue.typeSoundVolume();
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, 0.25f));
        }
    }

    // ---------------------------------------------------------------- 입력

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        for (int i = 0; i < choiceBoxes.size(); i++) {
            int[] b = choiceBoxes.get(i);
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                choose(i);
                return true;
            }
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            advance();
        } else if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6 && !choiceBoxes.isEmpty()) {
            choose(keyCode - GLFW.GLFW_KEY_1);
        }
        return true; // ESC 포함 다른 키는 무시 (메인 스토리는 끝까지)
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
    }

    @Override
    public void removed() {
        // 다른 화면이 덮어써도 대화는 끝나지 않는다 (메인 스토리는 끝까지). 클라가 화면이 비면 다시 연다.
        // 텍스처는 끝날 때(finish) / 서버가 멈출 때·접속이 끊길 때(stop) 정리한다.
    }
}
