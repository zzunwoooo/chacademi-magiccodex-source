package school.magiccodex.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import school.magiccodex.protocol.KyudichiProtocol;

/**
 * 큐디치 준비 시간 (60초) 마법 세팅. 위 6칸 = 경기 중 우측 아래 6칸 (키는 평소 칸의 키 그대로), 아래 = 배운 마법.
 * 칸을 고르고 마법을 누르면 들어간다 · 칸끼리 끌면 자리 바꾸기 · 우클릭 = 비우기 · 비행 칸은 고정 (자리만 이동).
 * 세팅은 다음 경기에도 남는다. "준비 완료" = 서버 READY.
 */
final class KyudichiPrepareScreen extends SocialScreen {
    static final float W = 1200, H = 800;
    private static final float SLOT = 128, SLOT_GAP = 22, SLOT_Y = 200, GRID_Y = 420, CELL = 74, GRID_X = 70;
    private static final int COLS = 14, ROWS = 3;
    private int selected = -1, dragFrom = -1, scroll;
    private float mx, my;
    private boolean dragging;

    KyudichiPrepareScreen() { super("큐디치 마법 세팅"); }

    @Override SocialLayout.Fit fit() { float s = Math.min(width * .9f / W, height * .9f / H); return new SocialLayout.Fit((width - W * s) / 2, (height - H * s) / 2, s); }

    @Override public void tick() {
        super.tick();
        if (!KyudichiClient.active() || KyudichiClient.match.phase() != 0) close();
    }

    private static float slotX(int i) { return W / 2 - (6 * SLOT + 5 * SLOT_GAP) / 2 + i * (SLOT + SLOT_GAP); }

    private List<CodexData.Spell> learned() {
        var out = new ArrayList<CodexData.Spell>();
        for (var s : CodexCatalog.spells()) if (s.discovered() && s.permissionKnown() && !s.command().isBlank()) out.add(s);
        out.sort(Comparator.comparingInt(CodexData.Spell::order));
        return out;
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x77060B12);
        var f = fit(); mx = (float) f.x(mouseX); my = (float) f.y(mouseY);
        start(c);
        try {
            KyudichiArt.draw(images, c, KyudichiArt.PANELS, KyudichiArt.P_WIDE2, 0, 0, W, H, 0xFFFFFFFF);
            label(c, "마법 세팅", 120, 86, 36, GOLD, true);
            var m = KyudichiClient.match;
            if (m != null) {
                int left = Math.max(0, m.timeLeftMs() - (int) (Util.getMeasuringTimeMs() - KyudichiClient.matchAt));
                label(c, "남은 준비 시간 " + left / 1000 + "초", 360, 86, 22, left < 10_000 ? 0xFFFF9C9C : WHITE, false);
            }
            slots(c);
            grid(c);
            bottom(c);
            toast(c, 300, 690, 600);
        } finally { end(c); }
    }

    private void slots(DrawContext c) {
        var l = KyudichiLoadout.get();
        var keys = KyudichiLoadout.get().bindings(CastingClient.base().orderedSlots());
        var swap = client.options.swapHandsKey;
        for (int i = 0; i < 6; i++) {
            float x = slotX(i), y = SLOT_Y - SLOT / 2;
            boolean sel = i == selected, hover = in(mx, my, x, y, SLOT, SLOT);
            if (sel || (dragging && hover)) chamfer(c, x - 6, y - 6, SLOT + 12, SLOT + 12, 14, sel ? 0xEEE7CE91 : 0xCC8FE3D2);
            KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, KyudichiArt.SQUARE, x, y, SLOT, SLOT, 0xFFFFFFFF);
            String id = l.get(i);
            var spell = CastingClient.state().spell(id);
            if (!id.isEmpty() && !(dragging && dragFrom == i)) {
                if (spell != null) images.drawCentered(c, spell.icon(), x + SLOT / 2, y + SLOT / 2, 80, 0xFFFFFFFF);
                else center(c, "?", x + SLOT / 2, y + SLOT / 2, 30, MUTED);
            } else if (id.isEmpty()) center(c, "비어 있음", x + SLOT / 2, y + SLOT / 2, 16, MUTED);
            if (KyudichiLoadout.FLIGHT.equals(id)) {
                chamfer(c, x + SLOT - 46, y + 6, 40, 22, 6, 0xEE2B6A55);
                center(c, "고정", x + SLOT - 26, y + 17, 13, 0xFFCFF7E2);
            }
            int code = keys.get(i).keyCode();
            String key = code < 0 ? "-" : KeySettingsLayout.name(code);
            boolean fKey = code >= 0 && swap.matchesKey(code, 0);
            chamfer(c, x + SLOT / 2 - 30, y + SLOT + 8, 60, 30, 7, fKey ? 0xEEB83A48 : 0xCC988B72);
            chamfer(c, x + SLOT / 2 - 28.5f, y + SLOT + 9.5f, 57, 27, 6, 0xF0111A20);
            center(c, key, x + SLOT / 2, y + SLOT + 23, 16, fKey ? 0xFFFFB0B8 : WHITE);
            if (spell != null) fitted(c, spell.name(), x + 4, y + SLOT + 56, 15, SLOT - 8, MUTED, false);
        }
        if (dragging && dragFrom >= 0) {
            var spell = CastingClient.state().spell(l.get(dragFrom));
            if (spell != null) images.drawCentered(c, spell.icon(), mx, my, 72, 0xDDFFFFFF);
        }
        boolean fUsed = keys.stream().anyMatch(b -> !b.empty() && b.keyCode() >= 0 && swap.matchesKey(b.keyCode(), 0));
        if (fUsed) center(c, "빨간 키는 경기 중 큐디치 키(공 잡기·쳐내기)와 겹쳐 쓸 수 없어요. 평소 키 설정에서 바꿔 주세요.", W / 2, SLOT_Y + SLOT / 2 + 100, 16, 0xFFFFB0B8);
    }

    private void grid(DrawContext c) {
        var spells = learned();
        label(c, "배운 마법  ·  칸을 고르고 마법을 누르세요 (우클릭: 칸 비우기, 칸끼리 끌기: 자리 바꾸기)", GRID_X, GRID_Y - 26, 17, MUTED, false);
        int rows = (spells.size() + COLS - 1) / COLS;
        scroll = Math.clamp(scroll, 0, Math.max(0, rows - ROWS));
        var l = KyudichiLoadout.get();
        CodexData.Spell hover = null;
        for (int k = 0; k < ROWS * COLS; k++) {
            int idx = (scroll + k / COLS) * COLS + k % COLS;
            if (idx >= spells.size()) break;
            var s = spells.get(idx);
            float x = GRID_X + (k % COLS) * CELL, y = GRID_Y + (k / COLS) * CELL;
            boolean in = l.spells().contains(s.id()), h = in(mx, my, x, y, CELL - 6, CELL - 6);
            chamfer(c, x, y, CELL - 6, CELL - 6, 8, h ? 0xEE8FE3D2 : in ? 0xCCE7CE91 : 0x88586F86);
            chamfer(c, x + 1.5f, y + 1.5f, CELL - 9, CELL - 9, 7, 0xEE0B1A2C);
            images.drawCentered(c, s.icon(), x + (CELL - 6) / 2, y + (CELL - 6) / 2, 54, in ? 0x88FFFFFF : 0xFFFFFFFF);
            if (h) hover = s;
        }
        if (rows > ROWS) center(c, (scroll + 1) + " / " + (rows - ROWS + 1) + "  (휠)", W - 110, GRID_Y + ROWS * CELL + 8, 15, MUTED);
        if (hover != null) {
            String t = hover.name() + "  ·  재사용 " + (hover.cooldown() % 1 == 0 ? String.valueOf((long) hover.cooldown()) : String.format("%.1f", hover.cooldown())) + "초" + (hover.manaCost() > 0 ? "  ·  마나 " + hover.manaCost() : "");
            float tw = type.width(t, 18, BODY) + 30;
            chamfer(c, Math.min(mx + 16, W - tw), my - 44, tw, 34, 8, 0xF0111A26);
            label(c, t, Math.min(mx + 16, W - tw) + 15, my - 27, 18, WHITE, false);
        }
    }

    private static final float BX = W - 70 - 280, BY = H - 120, BW = 280, BH = 74;
    private void bottom(DrawContext c) {
        var m = KyudichiClient.match; if (m == null) return;
        var me = client.player == null ? null : client.player.getUuid();
        boolean ready = false; int n = 0, all = 0;
        for (var p : m.players()) { all++; if ((p.flags() & 4) != 0) n++; if (p.id().equals(me)) ready = (p.flags() & 4) != 0; }
        boolean hover = in(mx, my, BX, BY, BW, BH);
        KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, ready ? KyudichiArt.BTN_GLOW : hover ? KyudichiArt.BTN_GLOW : KyudichiArt.BTN, BX, BY, BW, BH, 0xFFFFFFFF);
        if (ready) KyudichiArt.draw(images, c, KyudichiArt.CONTROLS, KyudichiArt.ICON_OK, BX + 22, BY + BH / 2 - 18, 36, 36, 0xFFFFFFFF);
        center(c, ready ? "준비 완료" : "준비하기", BX + BW / 2 + (ready ? 14 : 0), BY + BH / 2, 26, ready ? 0xFFFFF0C8 : hover ? CYAN : GOLD);
        label(c, "준비 " + n + " / " + all + "  ·  모두 준비하면 바로 시작해요", 80, BY + BH / 2, 19, MUTED, false);
    }

    // ================================================================== input
    private int slotAt(float x, float y) {
        for (int i = 0; i < 6; i++) if (in(x, y, slotX(i), SLOT_Y - SLOT / 2, SLOT, SLOT)) return i;
        return -1;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        var f = fit(); float px = (float) f.x(x), py = (float) f.y(y);
        var l = KyudichiLoadout.get();
        int slot = slotAt(px, py);
        if (slot >= 0) {
            if (button == 1) {
                String e = l.set(slot, "");
                if (e != null) notice(e); else { changed(); sound(.9f, .15f); }
                return true;
            }
            if (button == 0) { selected = slot; dragFrom = slot; dragging = false; sound(1.3f, .12f); return true; }
        }
        if (button == 0) {
            var spells = learned();
            for (int k = 0; k < ROWS * COLS; k++) {
                int idx = (scroll + k / COLS) * COLS + k % COLS;
                if (idx >= spells.size()) break;
                float gx = GRID_X + (k % COLS) * CELL, gy = GRID_Y + (k / COLS) * CELL;
                if (!in(px, py, gx, gy, CELL - 6, CELL - 6)) continue;
                int target = selected >= 0 ? selected : l.spells().indexOf("");
                if (target < 0) { notice("넣을 칸을 먼저 고르세요."); return true; }
                String e = l.set(target, spells.get(idx).id());
                if (e != null) notice(e);
                else { changed(); sound(1.4f, .18f); }
                return true;
            }
            if (in(px, py, BX, BY, BW, BH)) { toggleReady(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragFrom >= 0 && !KyudichiLoadout.get().get(dragFrom).isEmpty()) dragging = true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging && dragFrom >= 0) {
            var f = fit(); int to = slotAt((float) f.x(x), (float) f.y(y));
            if (to >= 0 && to != dragFrom) { KyudichiLoadout.get().swap(dragFrom, to); selected = to; changed(); sound(1.3f, .2f); }
        }
        dragging = false; dragFrom = -1;
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll -= (int) Math.signum(v); return true; }

    private void changed() { KyudichiLoadout.get().save(); CastingClient.refreshBindings(); }

    private void toggleReady() {
        var m = KyudichiClient.match; if (m == null || client.player == null) return;
        boolean ready = m.players().stream().anyMatch(p -> p.id().equals(client.player.getUuid()) && (p.flags() & 4) != 0);
        if (!ready && KyudichiLoadout.get().flightSlot() < 0) { notice("비행 마법이 한 칸에 있어야 해요."); return; }
        sound(1.5f, .25f);
        if (ClientPlayNetworking.canSend(KyudichiClient.Payload.ID))
            ClientPlayNetworking.send(new KyudichiClient.Payload(KyudichiProtocol.encode(new KyudichiProtocol.Ready(!ready))));
    }

    @Override public void close() { KyudichiLoadout.get().save(); CastingClient.refreshBindings(); super.close(); }
}
