package school.magiccodex.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import school.magiccodex.client.kui.Pen;
import school.magiccodex.client.kui.SpellPickView;

/**
 * 마법 선택 창 (SpellPickView). Pick a slot, then a learned spell; drag slot onto slot to swap; right click empties a slot
 * (never the flight slot). Changes save at once (kyudichi-loadout.yml) and the bottom-right bar follows during the match.
 */
final class KyudichiSpellPickScreen extends KyudichiViewScreen {
    private final Screen parent;
    private int selected = 0, scroll, dragFrom = -1;
    private boolean dragging;
    private String notice = "";
    private long noticeAt;

    KyudichiSpellPickScreen(Screen parent) { super("큐디치 마법 세팅"); this.parent = parent; }

    @Override float designW() { return SpellPickView.W; }
    @Override float designH() { return SpellPickView.H; }

    private static List<CodexData.Spell> learned() {
        var out = new ArrayList<CodexData.Spell>();
        for (var s : CodexCatalog.spells()) if (s.discovered() && s.permissionKnown() && !s.command().isBlank()) out.add(s);
        out.sort(Comparator.comparingInt(CodexData.Spell::order));
        return out;
    }
    private static SpellPickView.Cell cell(CodexData.Spell s) {
        if (s == null) return null;
        String cd = s.cooldown() % 1 == 0 ? String.valueOf((long) s.cooldown()) : String.format("%.1f", s.cooldown());
        return new SpellPickView.Cell(s.id(), s.icon(), s.name(), "재사용 " + cd + "초" + (s.manaCost() > 0 ? " · 마나 " + s.manaCost() : ""));
    }

    @Override void paint(Pen p) {
        var l = KyudichiLoadout.get();
        var equipped = new ArrayList<SpellPickView.Cell>();
        for (int i = 0; i < KyudichiLoadout.SIZE; i++) equipped.add(cell(CastingClient.state().spell(l.get(i))));
        var keys = new ArrayList<String>();
        for (var b : l.bindings(CastingClient.base().orderedSlots())) keys.add(b.keyCode() < 0 ? "" : KeySettingsLayout.name(b.keyCode()));
        var catalog = learned().stream().map(KyudichiSpellPickScreen::cell).toList();
        int rows = (catalog.size() + SpellPickView.COLS - 1) / SpellPickView.COLS;
        scroll = Math.clamp(scroll, 0, Math.max(0, rows - SpellPickView.ROWS));
        String n = net.minecraft.util.Util.getMeasuringTimeMs() - noticeAt < 3000 ? notice : "";
        SpellPickView.paint(p, new SpellPickView.Data(equipped, keys, l.flightSlot(), catalog, selected, scroll, dragging ? dragFrom : -1, n));
    }

    private void say(String s) { notice = s; noticeAt = net.minecraft.util.Util.getMeasuringTimeMs(); }

    @Override void pressed(String id, float x, float y) {
        if (id.startsWith("slot:")) { dragFrom = Integer.parseInt(id.substring(5)); dragging = false; }
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragFrom >= 0) dragging = true;
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override void released(float x, float y) {
        if (dragging && dragFrom >= 0) {
            String id = hitAt(x, y);
            if (id != null && id.startsWith("slot:")) {
                int to = Integer.parseInt(id.substring(5));
                if (to != dragFrom) { KyudichiLoadout.get().swap(dragFrom, to); selected = to; changed(); sound(1.3f, .2f); }
            }
        }
        dragging = false; dragFrom = -1;
    }

    @Override void clicked(String id, int button) {
        var l = KyudichiLoadout.get();
        if (id.equals(SpellPickView.CLOSE) || id.equals(SpellPickView.CONFIRM)) { close(); return; }
        if (id.startsWith("slot:")) {
            int i = Integer.parseInt(id.substring(5));
            if (button == 1) { String e = l.set(i, ""); if (e != null) say(e); else changed(); }
            else selected = i;
            return;
        }
        if (id.startsWith("spell:")) {
            var list = learned();
            int idx = Integer.parseInt(id.substring(6));
            if (idx >= list.size()) return;
            int target = selected >= 0 ? selected : l.spells().indexOf("");
            if (target < 0) { say("넣을 칸을 먼저 고르세요."); return; }
            String e = l.set(target, list.get(idx).id());
            if (e != null) say(e); else changed();
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll -= (int) Math.signum(v); return true; }

    private void changed() { KyudichiLoadout.get().save(); CastingClient.refreshBindings(); }

    @Override public void close() { changed(); client.setScreen(parent instanceof KyudichiPrepareScreen && KyudichiClient.playing() && KyudichiClient.match.phase() == 0 ? parent : null); }
}
