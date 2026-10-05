package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import school.magiccodex.npctalk.NpcTalkProtocol;

/**
 * AI NPC 대화창. 기존 {@link DialogueScreen}의 1600×900 배치와 dialogue-panel / speaker-nameplate / choice-button
 * 질감, label·hud_bold 글꼴을 그대로 쓰고, 입력칸·추천 버튼·하트 표시를 더한다.
 * 서버는 검사를 통과한 완성 대사(LINE)만 보내고, 이 화면이 타자기 효과로 보여준다.
 * 세션 토큰·요청 순번이 맞지 않는 응답은 버린다.
 */
public final class NpcTalkScreen extends Screen {
    private static final Identifier FONT = Identifier.of("magiccodex", "label"), BOLD = Identifier.of("magiccodex", "hud_bold");
    private static final Identifier PANEL = asset("dialogue-panel"), NAME = asset("speaker-nameplate"), CHOICE = asset("choice-button");
    private static boolean sound = true;

    private record Line(String text, float[] ends) {}
    private record Option(String label, Runnable action) {}

    private final Screen parent;
    private final String session;
    private String speaker;
    private String portraitName;
    private int hearts;
    private List<NpcTalkProtocol.Button> buttons;
    private boolean inputEnabled;

    private String text = "";
    private List<Line> lines = List.of();
    private int page, shown;
    private long start, lastSound;
    private final List<String> history = new ArrayList<>();
    private boolean log;
    private int historyPage;

    private int nextSeq;
    private int waitingSeq;
    /** 마지막으로 보낸 요청 순번. 이보다 작은 순번의 대답(창 닫기·시간 초과 전 요청)은 버린다. */
    private int floorSeq;
    private long waitingSince;
    private String stallText = "";
    private String offerTitle, offerToken;
    private String message = "";
    private long messageAt;
    private long closeAt;
    private boolean closed;

    private TextFieldWidget input;
    private Identifier portrait;
    private boolean portraitPresent;
    private int resourceGeneration = -1;

    public NpcTalkScreen(NpcTalkProtocol.Response open, Screen parent) {
        super(Text.literal("NPC 대화"));
        this.parent = parent;
        this.session = open.session();
        apply(open);
    }

    private static Identifier asset(String name) {
        return Identifier.of("magiccodex", "textures/gui/dialogue/" + name + ".png");
    }

    String session() {
        return session;
    }

    private void apply(NpcTalkProtocol.Response open) {
        speaker = open.speaker();
        portraitName = open.portrait();
        hearts = open.hearts();
        buttons = open.buttons();
        inputEnabled = open.input();
        portrait = portraitName != null && portraitName.matches("[a-z0-9_/-]{1,120}") ? asset(portraitName) : null;
        resourceGeneration = -1;
        setText(open.text());
    }

    /** 같은 세션에 OPEN이 다시 온 경우 (NPC를 다시 클릭). */
    void reopen(NpcTalkProtocol.Response open) {
        waitingSeq = 0;
        offerTitle = offerToken = null;
        apply(open);
    }

    @Override
    protected void init() {
        String draft = input == null ? "" : input.getText();
        input = new TextFieldWidget(textRenderer, 0, 0, 800, 30, Text.literal("npc_talk"));
        input.setMaxLength(NpcTalkProtocol.MAX_INPUT_CHARS);
        input.setText(draft);
        input.setFocused(inputEnabled);
    }

    // ------------------------------------------------------------------ 서버 응답

    void receive(NpcTalkProtocol.Response r) {
        switch (r.op()) {
            case NpcTalkProtocol.S_THINKING -> { if (r.seq() == waitingSeq) stallText = ""; }
            case NpcTalkProtocol.S_STALL -> { if (r.seq() == waitingSeq) stallText = r.text(); }
            case NpcTalkProtocol.S_LINE -> {
                if (r.seq() < floorSeq) return; // 이전 요청의 늦은 대답
                if (r.seq() == waitingSeq) waitingSeq = 0;
                offerTitle = offerToken = null;
                setText(r.text());
            }
            case NpcTalkProtocol.S_QUEST_OFFER -> {
                if (r.seq() < floorSeq) return;
                offerTitle = r.text();
                offerToken = r.token();
            }
            case NpcTalkProtocol.S_INFO -> {
                if (r.seq() == waitingSeq) { waitingSeq = 0; stallText = ""; } // 서버가 이번 요청을 처리하지 않음 (쿨타임·길이 등)
                message = r.text();
                messageAt = Util.getMeasuringTimeMs();
            }
            case NpcTalkProtocol.S_CLOSE -> serverClose(r.text());
            default -> { }
        }
    }

    /** 서버가 대화를 닫음. 작별 인사가 있으면 다 보여준 뒤 닫는다. */
    void serverClose(String farewell) {
        if (farewell == null || farewell.isEmpty()) {
            closed = true;
            if (client != null) client.setScreen(parent);
            return;
        }
        closed = true;
        waitingSeq = 0;
        offerTitle = offerToken = null;
        setText(farewell);
        closeAt = Util.getMeasuringTimeMs() + (long) farewell.length() * 23 + 1600;
    }

    private void setText(String value) {
        text = value == null ? "" : value;
        stallText = "";
        if (!text.isEmpty()) {
            history.add(speaker + "\n" + text);
            if (history.size() > 80) history.removeFirst();
        }
        layout();
        page = 0;
        start = Util.getMeasuringTimeMs();
        shown = 0;
    }

    // ------------------------------------------------------------------ 보내기

    private void submit() {
        if (closed || waitingSeq != 0 || input == null) return;
        String value = input.getText().strip();
        if (!NpcTalkProtocol.validInput(value)) {
            if (!value.isEmpty()) note("100자 이내로 입력해 주세요.");
            return;
        }
        int seq = ++nextSeq;
        if (NpcTalkClient.send(session, NpcTalkProtocol.C_SAY, seq, value, false)) {
            waitingSeq = seq;
            floorSeq = seq;
            waitingSince = Util.getMeasuringTimeMs();
            history.add("나\n" + value);
            input.setText("");
            click(1.2f, .12f);
        }
    }

    private void pressButton(String id) {
        if (closed || waitingSeq != 0) return;
        int seq = ++nextSeq;
        if (NpcTalkClient.send(session, NpcTalkProtocol.C_BUTTON, seq, id, false)) {
            waitingSeq = seq;
            floorSeq = seq;
            waitingSince = Util.getMeasuringTimeMs();
            click(1.2f, .12f);
        }
    }

    private void answerQuest(boolean accept) {
        if (closed || offerToken == null) return;
        int seq = ++nextSeq;
        if (NpcTalkClient.send(session, NpcTalkProtocol.C_QUEST, seq, offerToken, accept)) {
            waitingSeq = seq;
            floorSeq = seq;
            waitingSince = Util.getMeasuringTimeMs();
            offerTitle = offerToken = null;
        }
    }

    private void note(String m) {
        message = m;
        messageAt = Util.getMeasuringTimeMs();
    }

    private List<Option> options() {
        var out = new ArrayList<Option>();
        if (offerToken != null) {
            out.add(new Option("부탁 수락: " + offerTitle, () -> answerQuest(true)));
            out.add(new Option("거절", () -> answerQuest(false)));
            return out;
        }
        if (waitingSeq == 0 && complete() && lastPage() && closeAt == 0) {
            for (var b : buttons) out.add(new Option(b.label(), () -> pressButton(b.id())));
        }
        return out;
    }

    // ------------------------------------------------------------------ 배치·그리기 (DialogueScreen과 같은 좌표계)

    private void layout() {
        var rows = new ArrayList<Line>();
        StringBuilder current = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || UiResources.text().width(current + ch, 29, FONT) > 1330) {
                rows.add(line(current.toString()));
                current.setLength(0);
                if (cp == '\n') continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty() || rows.isEmpty()) rows.add(line(current.toString()));
        lines = List.copyOf(rows);
    }

    private Line line(String s) {
        int[] cps = s.codePoints().toArray();
        float[] ends = new float[cps.length + 1];
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < cps.length; i++) {
            prefix.appendCodePoint(cps[i]);
            ends[i + 1] = UiResources.text().width(prefix.toString(), 29, FONT);
        }
        return new Line(s, ends);
    }

    private int count() {
        return lines.subList(page * 3, Math.min(page * 3 + 3, lines.size())).stream().mapToInt(l -> l.ends.length - 1).sum();
    }

    private boolean complete() { return shown >= count(); }

    private boolean lastPage() { return (page + 1) * 3 >= lines.size(); }

    private CodexLayout layoutFit() {
        float scale = Math.min(width / 1600f, height / 900f);
        return new CodexLayout((width - 1600 * scale) / 2, (height - 900 * scale) / 2, scale);
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void renderBackground(DrawContext c, int x, int y, float delta) { }

    @Override
    public void tick() {
        long now = Util.getMeasuringTimeMs();
        if (closeAt > 0 && now > closeAt) {
            closeAt = 0;
            if (client != null) client.setScreen(parent);
            return;
        }
        if (waitingSeq != 0 && now - waitingSince > 20000) {
            waitingSeq = 0;
            stallText = "";
            note("대답이 없어요. 다시 말을 걸어 주세요.");
        }
        if (client != null && (client.player == null || client.world == null)) close();
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        int next = (int) Math.min(count(), Math.max(shown, (now - start) / 23));
        if (!log && next > shown) {
            if (sound && now - lastSound >= 45) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, .07f));
                lastSound = now;
            }
            shown = next;
        }
        if (resourceGeneration != UiResources.generation()) {
            resourceGeneration = UiResources.generation();
            portraitPresent = portrait != null && client.getResourceManager().getResource(portrait).isPresent();
        }
        var fit = layoutFit();
        double mx = fit.localX(mouseX), my = fit.localY(mouseY);
        var images = UiResources.images();
        images.beginFrame();
        UiResources.text().beginFrame();
        c.fill(0, 0, width, height, 0x30030A12);
        c.getMatrices().push();
        try {
            c.getMatrices().translate(fit.x(), fit.y(), 0);
            c.getMatrices().scale(fit.scale(), fit.scale(), 1);
            if (portraitPresent) images.drawTexture(c, portrait, 65, -5, 0, 0, 700, 1050, 1024, 1536, 1024, 1536);

            // 상단: 이름 + 하트(호감도 단계)
            c.fill(28, 28, 610, 112, 0xCB101D2C);
            label(c, speaker, 47, 69, 25, 0xFFE3C48C, true, false, 300);
            for (int i = 0; i < 4; i++) {
                int x = 380 + i * 34, y = 58;
                boolean on = i < hearts;
                c.fill(x, y, x + 22, y + 22, on ? 0xFFE8A0BF : 0x40FFFFFF);
                if (!on) c.fill(x + 3, y + 3, x + 19, y + 19, 0xCB101D2C);
            }
            label(c, sound ? "♪ 소리 켜짐" : "♪ 소리 꺼짐", 1110, 54, 21, 0xFFD8DFE7, false, false, 175);
            label(c, "기록", 1305, 54, 21, 0xFFD8DFE7, false, false, 90);
            label(c, "닫기 ×", 1445, 54, 21, 0xFFD8DFE7, false, false, 100);

            // 입력칸 (choice-button 질감)
            if (inputEnabled) {
                boolean focus = input != null && input.isFocused();
                images.drawTexture(c, CHOICE, 86, 552, 73, 270, 860, 56, 1952, 210, 2098, 749, focus ? 0xFFFFFFFF : 0xFFAEB9C4);
                String value = input == null ? "" : input.getText();
                if (value.isEmpty()) {
                    label(c, waitingSeq != 0 ? "대답을 기다리는 중…" : "하고 싶은 말을 입력하세요 (Enter)", 116, 580, 22, 0xFF9FB0C0, false, false, 800);
                } else {
                    String visible = value;
                    while (visible.length() > 1 && UiResources.text().width(visible, 23, FONT) > 790) visible = visible.substring(1);
                    label(c, visible, 116, 580, 23, 0xFFF0F1F4, false, false, 800);
                    if (focus && now / 500 % 2 == 0) {
                        float cx = 116 + Math.min(790, UiResources.text().width(visible, 23, FONT)) + 2;
                        c.fill((int) cx, 566, (int) cx + 2, 594, 0xFF91E8F5);
                    }
                }
            }

            // 대사 패널
            images.drawTexture(c, PANEL, 60, 639, 44, 199, 1480, 220, 2085, 316, 2172, 724);
            images.drawTexture(c, NAME, 86, 615, 200, 244, 255, 54, 1810, 235, 2172, 724);
            label(c, speaker, 131, 642, 23, 0xFFE8D19B, true, false, 193);
            if (waitingSeq != 0) {
                String dots = ".".repeat((int) (now / 350 % 3) + 1);
                label(c, stallText.isEmpty() ? dots : stallText, 120, 704, 29, 0xFFB8C4D0, false, false, 1400);
            } else {
                int remaining = shown;
                for (int i = page * 3; i < Math.min(page * 3 + 3, lines.size()); i++) {
                    var row = lines.get(i);
                    int n = Math.min(remaining, row.ends.length - 1);
                    remaining -= n;
                    if (n > 0) {
                        int y = 704 + (i - page * 3) * 43;
                        boolean partial = n < row.ends.length - 1;
                        if (partial) c.enableScissor(115, y - 24, 120 + (int) Math.ceil(row.ends[n]) + 1, y + 26);
                        label(c, row.text, 120, y, 29, 0xFFF0F1F4, false, false, 1400);
                        if (partial) c.disableScissor();
                    }
                }
            }

            // 추천 버튼 / 부탁 수락 (기존 선택지 위치)
            var opts = options();
            for (int i = 0; i < opts.size(); i++) {
                int y = 590 - opts.size() * 75 + i * 75;
                boolean hover = hit(mx, my, 962, y, 545, 67);
                images.drawTexture(c, CHOICE, 962, y, 73, 270, 545, 67, 1952, 210, 2098, 749, hover ? 0xFFFFFFFF : 0xFFCBD5DD);
                label(c, (i + 1) + ". " + opts.get(i).label(), 1005, y + 33, 24, 0xFFEEF1F5, false, false, 465);
            }

            String hint = closeAt > 0 ? "" : waitingSeq != 0 ? "처리 중…" : !complete() ? "Enter  바로 보기"
                    : !lastPage() ? "Enter  다음" : "Enter  보내기 · 1~3  추천 · Esc  닫기";
            label(c, hint, 800, 825, 20, 0xFFC1CEDD, false, true, 520);
            if (!message.isEmpty() && now - messageAt < 5000) label(c, message, 800, 874, 19, 0xFFE8D19B, false, true, 1300);

            if (log) {
                c.fill(200, 130, 1400, 605, 0xF30D192A);
                label(c, "대화 기록 · 클릭해서 닫기", 245, 166, 25, 0xFFE8D19B, true, false, 1100);
                int from = Math.max(0, history.size() - 1 - historyPage);
                String entry = history.isEmpty() ? "" : history.get(from);
                int yy = 212;
                for (String row : UiResources.text().wrapText(entry, 1090, 25)) {
                    if (yy > 540) break;
                    label(c, row, 245, yy, 25, 0xFFE5E8EC, false, false, 1110);
                    yy += 35;
                }
                label(c, "휠: 이전 / 다음 대화", 245, 572, 19, 0xFFB2C2D0, false, false, 1000);
            }
        } finally {
            c.getMatrices().pop();
            images.endFrame();
        }
        super.render(c, mouseX, mouseY, delta);
    }

    private void label(DrawContext c, String value, float x, float y, float size, int color, boolean bold, boolean centered, float max) {
        var font = bold ? BOLD : FONT;
        float actual = Math.min(size, size * max / Math.max(max, UiResources.text().width(value, size, font)));
        UiResources.text().draw(c, value, x, y, actual, color, font, centered);
    }

    private static boolean hit(double x, double y, int a, int b, int w, int h) {
        return x >= a && x < a + w && y >= b && y < b + h;
    }

    private void click(float pitch, float volume) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    /** Enter: 타자기 건너뛰기 → 다음 쪽 → 입력 보내기. */
    private void enter() {
        if (closeAt > 0) return;
        if (!complete()) { shown = count(); return; }
        if (!lastPage()) { page++; start = Util.getMeasuringTimeMs(); shown = 0; return; }
        submit();
    }

    // ------------------------------------------------------------------ 입력

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (log) return true;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { enter(); return true; }
        boolean empty = input == null || input.getText().isEmpty();
        if (empty && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            var opts = options();
            int index = key - GLFW.GLFW_KEY_1;
            if (index < opts.size()) { opts.get(index).action().run(); return true; }
        }
        if (inputEnabled && input != null && input.keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (log || !inputEnabled || input == null || closeAt > 0) return false;
        if (input.getText().isEmpty() && ch >= '1' && ch <= '3' && !options().isEmpty()) return true; // 단축키로 처리됨
        return input.charTyped(ch, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        var f = layoutFit();
        double mx = f.localX(x), my = f.localY(y);
        if (log) { log = false; return true; }
        if (hit(mx, my, 1090, 24, 180, 60)) { sound = !sound; return true; }
        if (hit(mx, my, 1280, 24, 130, 60)) { shown = count(); log = true; historyPage = 0; return true; }
        if (hit(mx, my, 1420, 24, 140, 60)) { close(); return true; }
        var opts = options();
        for (int i = 0; i < opts.size(); i++) {
            if (hit(mx, my, 962, 590 - opts.size() * 75 + i * 75, 545, 67)) { opts.get(i).action().run(); return true; }
        }
        if (inputEnabled && input != null) {
            input.setFocused(hit(mx, my, 86, 552, 860, 56) || input.isFocused());
        }
        if (hit(mx, my, 60, 639, 1480, 220)) { enter(); return true; }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (log) {
            historyPage = Math.clamp(historyPage + (int) Math.signum(v), 0, Math.max(0, history.size() - 1));
            return true;
        }
        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            NpcTalkClient.send(session, NpcTalkProtocol.C_CLOSE, 0, "", false);
        }
        if (client != null) client.setScreen(parent);
    }
}
