package school.magiccodex.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.*;
import java.util.*;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import org.lwjgl.glfw.GLFW;

import school.magiccodex.protocol.ShopProtocol;
import school.magiccodex.protocol.ShopProtocol.*;

/**
 * NPC shop (reference: CHACADEMI_SHOP_REDESIGN approved.png). Layout, art and hit testing live in
 * {@link ShopView}; this class owns the server data, filter/selection/quantity state and the single
 * trade path ({@link #trade()}) that both the row buttons and the footer button go through.
 * Protocol, prices, validation and sounds are unchanged.
 */
public final class ShopScreen extends SocialScreen {
    private static final Identifier FACE_TITLE = Identifier.of("magiccodex", "title"), FACE_SECTION = Identifier.of("magiccodex", "section"),
            FACE_BODY = Identifier.of("magiccodex", "body");
    private static final Map<Identifier, float[]> SIZES = new HashMap<>();

    private Response data;
    private Input search, quantity;
    private String selected = "", sentSearch = "";
    private ShopView.Tab tab = ShopView.Tab.ALL;
    private boolean selling, pressed, dragging;
    private int scroll;
    private long searchAt;
    private Map<String, ItemStack> icons = Map.of();
    private Map<String, List<String>> lore = Map.of();

    ShopScreen() { super("상점"); }

    @Override SocialLayout.Fit fit() {
        float s = Math.min(width * .98f / ShopView.W, height * .98f / ShopView.H);
        return new SocialLayout.Fit((width - ShopView.W * s) / 2, (height - ShopView.H * s) / 2, s);
    }

    @Override protected void init() {
        super.init();
        search = new Input("아이템 검색", 80, search == null ? "" : search.text());
        quantity = new Input("수량", 9, quantity == null ? "1" : quantity.text());
        quantity.widget.setTextPredicate(v -> v.matches("[0-9]{0,9}"));
    }

    void receive(Response r) {
        data = r;
        busy = false;
        notice(r.message());
        var map = new HashMap<String, ItemStack>();
        var text = new HashMap<String, List<String>>();
        for (var p : r.products()) {
            var stack = decode(p.preview());
            map.put(p.id(), stack);
            text.put(p.id(), describe(stack));
        }
        icons = Map.copyOf(map);
        lore = Map.copyOf(text);
        normalize(false);
    }
    void failed(String m) { busy = false; notice(m); }
    void tradeSucceeded() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.25f, .30f));
    }

    private ItemStack decode(byte[] b) {
        if (b.length == 0 || client.world == null) return ItemStack.EMPTY;
        try {
            return ItemStack.fromNbt(client.world.getRegistryManager(),
                    NbtIo.readCompressed(new ByteArrayInputStream(b), NbtSizeTracker.of(2097152))).orElse(ItemStack.EMPTY);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }
    /**
     * Row description from the item's own lore. As in the MagicCodex tooltip, a first lore line
     * followed by more lines is the item-type label, so the description starts after it.
     * Resource-pack glyphs (private-use characters) are removed because the UI font cannot draw them.
     */
    private static List<String> describe(ItemStack stack) {
        var component = stack.isEmpty() ? null : stack.get(DataComponentTypes.LORE);
        if (component == null) return List.of();
        var lines = new ArrayList<String>();
        for (Text t : component.lines()) {
            String s = ShopView.clean(t.getString());
            if (!s.isEmpty()) lines.add(s);
        }
        if (lines.size() > 1) lines.removeFirst();
        return List.copyOf(lines.subList(0, Math.min(2, lines.size())));
    }
    private String name(Product p) {
        var stack = icons.getOrDefault(p.id(), ItemStack.EMPTY);
        return stack.isEmpty() ? p.name() : ItemTooltipRenderer.localName(stack, stack.getName()).getString();
    }

    // ---- filter / selection ----------------------------------------------------------------------------------
    private boolean shows(Product p) {
        return switch (tab) { case BUY -> !p.buy().isEmpty(); case SELL -> !p.sell().isEmpty(); case ALL -> !p.buy().isEmpty() || !p.sell().isEmpty(); };
    }
    private List<Product> visible() {
        if (data == null) return List.of();
        String q = search == null ? "" : search.text().toLowerCase(Locale.ROOT);
        return data.products().stream().filter(p -> shows(p)
                && (name(p).toLowerCase(Locale.ROOT).contains(q) || p.name().toLowerCase(Locale.ROOT).contains(q))).toList();
    }
    private Product selected() {
        return data == null ? null : visible().stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null);
    }
    /** Trade direction the 전체 tab gives a product when it is picked without a specific button. */
    private static boolean defaultSelling(Product p) { return p.buy().isEmpty(); }
    private boolean tradable(Product p, boolean sell) { return !(sell ? p.sell() : p.buy()).isEmpty(); }

    /**
     * Re-validates after data, search or tab changes: the selection must be a visible product with
     * a trade direction it supports, the quantity resets when either changes, and scroll stays in range.
     */
    private void normalize(boolean resetScroll) {
        var list = visible();
        String before = selected;
        boolean sellBefore = selling;
        var current = selected();
        if (current == null) { current = list.isEmpty() ? null : list.getFirst(); selected = current == null ? "" : current.id(); }
        if (tab == ShopView.Tab.BUY) selling = false;
        else if (tab == ShopView.Tab.SELL) selling = true;
        else if (current != null && !tradable(current, selling)) selling = defaultSelling(current);
        if (quantity != null && (!selected.equals(before) || selling != sellBefore)) quantity.widget.setText("1");
        if (resetScroll) scroll = 0;
        scroll = Math.clamp(scroll, 0, Math.max(0, list.size() - ShopView.ROWS));
    }
    private void choose(Product p, boolean sell) {
        boolean changed = !p.id().equals(selected) || sell != selling;
        selected = p.id();
        selling = sell;
        if (changed) quantity.widget.setText("1");
    }

    private int number() { try { return ShopProtocol.quantity(quantity.text()); } catch (Exception e) { return 0; } }
    private int possible(Product p, boolean sell) {
        if (p == null || data == null || !tradable(p, sell)) return 0;
        var icon = icons.getOrDefault(p.id(), ItemStack.EMPTY);
        int max = Math.min(576, (icon.isEmpty() ? 64 : icon.getMaxCount()) * 9);
        if (sell) return Math.min(max, p.owned());
        try {
            var price = new BigDecimal(p.buy());
            if (price.signum() == 0) return max;
            if (data.balance().isEmpty()) return 0;
            return Math.min(max, new BigDecimal(data.balance()).divide(price, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(max)).intValue());
        } catch (Exception e) { return 0; }
    }
    private int possible() { return possible(selected(), selling); }
    private String total() {
        var p = selected();
        if (p == null || number() == 0 || !tradable(p, selling)) return "";
        try { return new BigDecimal(selling ? p.sell() : p.buy()).multiply(BigDecimal.valueOf(number())).stripTrailingZeros().toPlainString(); }
        catch (Exception e) { return ""; }
    }

    // ---- requests --------------------------------------------------------------------------------------------
    /** The only place a BUY/SELL request is made (footer button, row button on the chosen row). */
    private void trade() {
        if (data == null || busy) return;
        var p = selected();
        if (p == null || number() == 0 || number() > possible()) { notice("가능 수량 안에서 숫자를 입력해 주세요."); return; }
        sound(1.1f, .16f);
        send(selling ? ShopProtocol.SELL : ShopProtocol.BUY, p);
    }
    private void send(int action, Product p) {
        if (data == null || busy) return;
        busy = ShopClient.request(this, new Request(action, 1, data.session(), data.shop(), data.revision(),
                p == null ? "" : p.id(), quantity.text(), UUID.randomUUID().toString(), search.text()));
        if (!busy && action != ShopProtocol.CLOSE) notice("상점 서버 연결 또는 처리 상태를 확인해 주세요.");
    }

    @Override public void tick() {
        super.tick();
        if (search != null && !search.text().equals(sentSearch) && !busy && data != null) {
            long now = Util.getMeasuringTimeMs();
            if (searchAt == 0) searchAt = now + 450;
            if (now >= searchAt) {
                sentSearch = search.text();
                searchAt = 0;
                normalize(true);
                if (sentSearch.isEmpty() || visible().isEmpty()) send(ShopProtocol.OPEN, null);
            }
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------------------
    private ShopView.Model model(double mx, double my) {
        var products = visible();
        var rows = new ArrayList<ShopView.Row>(products.size());
        for (var p : products)
            rows.add(new ShopView.Row(p.id(), name(p), lore.getOrDefault(p.id(), List.of()), p.buy(), p.sell(), p.owned(),
                    possible(p, false) > 0, possible(p, true) > 0, null));
        var all = data == null ? List.<Product>of() : data.products();
        boolean[] tabs = {!all.isEmpty(), all.stream().anyMatch(p -> !p.buy().isEmpty()), all.stream().anyMatch(p -> !p.sell().isEmpty())};
        tabs[tab.ordinal()] = true;
        String empty = data == null ? "상점 정보를 불러오는 중입니다" : all.isEmpty() ? "판매 중인 상품이 없습니다"
                : !search.text().isBlank() ? "검색 결과가 없습니다"
                : tab == ShopView.Tab.SELL ? "판매할 수 있는 상품이 없습니다" : "구매할 수 있는 상품이 없습니다";
        var portrait = portrait();
        float[] size = portrait == null ? null : size(portrait);
        int max = possible(), n = number();
        long age = Util.getMeasuringTimeMs() - noticeAt;
        float alpha = notice.isEmpty() || age > 5000 ? 0 : age > 4500 ? (5000 - age) / 500f : 1;
        var f = fit();
        return new ShopView.Model(data == null ? "상점" : data.title(), "상점 주인",
                data == null || data.npcName().isBlank() ? "상점" : data.npcName(), size != null, size == null ? 1 : size[0] / size[1],
                data == null ? "" : data.balance(), tab, tabs, rows, scroll, selected, selling, empty, max, total(),
                n > 1, n < max, selected() != null && n > 0 && n <= max, busy, notice, alpha, mx, my, pressed, (float) f.y(height));
    }
    private Identifier portrait() {
        if (data == null || data.portrait().isBlank()) return null;
        var id = Identifier.tryParse("magiccodex:textures/gui/dialogue/" + data.portrait() + ".png");
        return id != null && client.getResourceManager().getResource(id).isPresent() ? id : null;
    }
    /** PNG width/height from the IHDR header (cached), so any illustration keeps its own ratio. */
    private float[] size(Identifier id) {
        return SIZES.computeIfAbsent(id, k -> {
            try (InputStream in = client.getResourceManager().getResourceOrThrow(k).getInputStream()) {
                byte[] h = in.readNBytes(24);
                if (h.length < 24 || h[12] != 'I' || h[13] != 'H' || h[14] != 'D' || h[15] != 'R') return new float[]{1024, 1536};
                int w = ((h[16] & 255) << 24) | ((h[17] & 255) << 16) | ((h[18] & 255) << 8) | (h[19] & 255);
                int t = ((h[20] & 255) << 24) | ((h[21] & 255) << 16) | ((h[22] & 255) << 8) | (h[23] & 255);
                return w > 0 && t > 0 ? new float[]{w, t} : new float[]{1024, 1536};
            } catch (Exception e) { return new float[]{1024, 1536}; }
        });
    }

    private final class GameCanvas implements ShopView.Canvas {
        final DrawContext c;
        GameCanvas(DrawContext c) { this.c = c; }
        Identifier face(ShopView.Face f) {
            return switch (f) { case TITLE -> FACE_TITLE; case SECTION -> FACE_SECTION; case BODY -> FACE_BODY; case LABEL -> BODY; case BOLD -> BOLD; };
        }
        public void image(String tex, float x, float y, float w, float h, int u, int v, int sw, int sh, int tw, int th) {
            int x0 = Math.round(x), y0 = Math.round(y);
            images.drawTexture(c, Identifier.of("magiccodex", tex), x0, y0, u, v, Math.round(x + w) - x0, Math.round(y + h) - y0, sw, sh, tw, th, 0xFFFFFFFF);
        }
        public void text(String s, float x, float cy, float size, int color, ShopView.Face f, boolean centered) {
            type.draw(c, s, x, cy, size, color, face(f), centered);
        }
        public float width(String s, float size, ShopView.Face f) { return type.width(s, size, face(f)); }
        public void fill(float x, float y, float w, float h, int color) { HudMesh.quad(c, x, y, w, h, color, color); }
        public void item(ShopView.Row r, float x, float y, float size) {
            var stack = icons.getOrDefault(r.id(), ItemStack.EMPTY);
            if (stack.isEmpty()) return;
            // Hi-res pack icons go through ItemIconTextures (smooth, mipmapped); others are the item's own model.
            c.getMatrices().push();
            c.getMatrices().translate(x, y, 20);
            c.getMatrices().scale(size / 16f, size / 16f, 1);
            c.drawItem(stack, 0, 0);
            c.getMatrices().pop();
        }
        public void portrait(float x, float y, float w, float h) {
            var id = ShopScreen.this.portrait();
            if (id == null) return;
            float[] s = size(id);
            int x0 = Math.round(x), y0 = Math.round(y);
            images.drawTexture(c, id, x0, y0, 0, 0, Math.round(x + w) - x0, Math.round(y + h) - y0, (int) s[0], (int) s[1], (int) s[0], (int) s[1], 0xFFFFFFFF);
        }
        public void searchField(float x, float y, float w, float h) { search.draw(c, x, y, w, h, "아이템 검색"); }
        public void quantityField(float x, float y, float w, float h) { quantity.drawCentered(c, x, y, w, h, "1", WHITE); }
        public void pushLayer(float z) { c.getMatrices().push(); c.getMatrices().translate(0, 0, z); }
        public void popLayer() { c.getMatrices().pop(); }
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        var f = fit();
        double mx = f.x(mouseX), my = f.y(mouseY);
        ItemStack hovered = ItemStack.EMPTY;
        start(c);
        try {
            var m = model(mx, my);
            ShopView.draw(new GameCanvas(c), m);
            var hit = ShopView.hit(m, mx, my);
            if (hit.kind() == ShopView.Kind.ICON) hovered = icons.getOrDefault(m.rows().get(hit.index()).id(), ItemStack.EMPTY);
        } finally { end(c); }
        // Native item-tooltip entrypoint uses the existing rich MagicCodex renderer and Shift+wheel scrolling.
        if (!hovered.isEmpty() && entrance.ready()) c.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
    }

    // ---- input -----------------------------------------------------------------------------------------------
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        pressed = true;
        var f = fit();
        double mx = f.x(x), my = f.y(y);
        var m = model(mx, my);
        var hit = ShopView.hit(m, mx, my);
        if (hit.kind() == ShopView.Kind.CLOSE) { sound(1.1f, .16f); close(); return true; }
        if (hit.kind() != ShopView.Kind.SEARCH) search.focus(false);
        if (hit.kind() != ShopView.Kind.QUANTITY) quantity.focus(false);
        if (busy) return true;
        var list = visible();
        switch (hit.kind()) {
            case TAB -> {
                var next = ShopView.Tab.values()[hit.index()];
                if (m.tabEnabled()[hit.index()] && next != tab) { tab = next; normalize(true); sound(1.1f, .16f); }
            }
            case SEARCH -> search.click(mx, my);
            case ROW, ICON -> {
                var p = list.get(hit.index());
                boolean sell = tab == ShopView.Tab.SELL || tab == ShopView.Tab.ALL && (p.id().equals(selected) ? tradable(p, selling) ? selling : defaultSelling(p) : defaultSelling(p));
                choose(p, sell);
                sound(1.1f, .14f);
            }
            case ROW_BUY, ROW_SELL -> {
                var p = list.get(hit.index());
                boolean sell = hit.kind() == ShopView.Kind.ROW_SELL;
                // First press picks the product and direction; pressing it again on the chosen row trades
                // through the same path as the footer button (busy-guarded, one operation id per request).
                if (p.id().equals(selected) && sell == selling && possible(p, sell) > 0) trade();
                else { choose(p, sell); sound(1.1f, .14f); }
            }
            case SCROLL -> { dragging = true; scroll = ShopView.scrollAt(m, my); }
            case MINUS -> { if (number() > 1) { quantity.widget.setText(Integer.toString(number() - 1)); sound(1.1f, .14f); } }
            case PLUS -> { if (number() < possible()) { quantity.widget.setText(Integer.toString(Math.max(1, number() + 1))); sound(1.1f, .14f); } }
            case QUANTITY -> quantity.click(Math.clamp(mx, quantity.x, quantity.x + quantity.w - .1), Math.clamp(my, quantity.y, quantity.y + quantity.h - .1));
            case TRADE -> trade();
            default -> {}
        }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) { pressed = false; dragging = false; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { var f = fit(); scroll = ShopView.scrollAt(model(f.x(x), f.y(y)), f.y(y)); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, visible().size() - ShopView.ROWS));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && (search.focus() || quantity.focus())) {
            search.focus(false);
            quantity.focus(false);
            return true;
        }
        // A focused field consumes every key so letters typed into it never reach other key handlers.
        if (search.focus()) { search.key(key, scan, mods); return true; }
        if (quantity.focus()) { quantity.key(key, scan, mods); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) {
        return search.focus() && search.typed(c, mods) || quantity.focus() && quantity.typed(c, mods) || super.charTyped(c, mods);
    }
    @Override public void close() {
        if (data != null && !busy) send(ShopProtocol.CLOSE, null);
        ShopClient.detach(this);
        client.setScreen(null);
    }
}
