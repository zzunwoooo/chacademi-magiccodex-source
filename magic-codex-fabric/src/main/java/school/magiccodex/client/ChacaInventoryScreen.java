package school.magiccodex.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenPos;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.AbstractCraftingRecipeBookWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.client.ChacaInventoryLayout.Element;
import school.magiccodex.client.ChacaInventoryLayout.Fit;
import school.magiccodex.client.ChacaInventoryLayout.Kind;
import school.magiccodex.client.ChacaInventoryLayout.Mode;

/**
 * Chacademi survival inventory. It is the vanilla player inventory screen with a different face:
 * same {@link PlayerScreenHandler}, same slots, same click/drag/shift/number-key/drop handling and
 * server-validated crafting, same recipe book widget. Only drawing and the point→slot test change.
 *
 * Vanilla asks {@code isPointWithinBounds(slot.x, slot.y, 16, 16, mouse)} for every slot. Slot
 * positions in the player handler are unique, so the override maps them back to the slot id and
 * answers with this screen's (animated) layout instead. Armor and offhand slots are not shown and
 * never hit; their items stay untouched.
 */
public final class ChacaInventoryScreen extends RecipeBookScreen<PlayerScreenHandler> {
    private static final String DIR = "textures/gui/inventory/";
    static final Identifier ORB = Identifier.of("magiccodex", DIR + "crafting_orb.png");
    static final Identifier CRYSTAL = Identifier.of("magiccodex", DIR + "crafting_crystal.png");
    static final Identifier PEDESTAL = Identifier.of("magiccodex", DIR + "inventory_pedestal.png");
    static final Identifier BOOK = Identifier.of("magiccodex", DIR + "recipe_book.png");
    /** Pixel sizes of the shipped (cropped) PNGs; see textures/gui/inventory/asset_manifest.json. */
    static final int ORB_W = ChacaInventoryLayout.ORB_PNG[0], ORB_H = ChacaInventoryLayout.ORB_PNG[1],
            CRYSTAL_W = ChacaInventoryLayout.CRYSTAL_PNG[0], CRYSTAL_H = ChacaInventoryLayout.CRYSTAL_PNG[1],
            PEDESTAL_W = ChacaInventoryLayout.PEDESTAL_PNG[0], PEDESTAL_H = ChacaInventoryLayout.PEDESTAL_PNG[1],
            BOOK_W = ChacaInventoryLayout.BOOK_PNG[0], BOOK_H = ChacaInventoryLayout.BOOK_PNG[1];
    private static final Identifier SERIF = Identifier.of("magiccodex", "title"), LABEL = Identifier.of("magiccodex", "label");

    private static final int GOLD = 0xFFE9D3A0, GOLD_SOFT = 0x99D9BC7E, GOLD_FAINT = 0x44D9BC7E, CREAM = 0xFFF4EAD2;
    private static final int CYAN = 0xE65FE3F2, CYAN_FAINT = 0x3355D6EA, HOVER = 0x8CF6E6B8;

    private final AbstractCraftingRecipeBookWidget book;
    private final Map<Long, Integer> slotsByPosition = new HashMap<>();
    private final UiResources.Entrance entrance = new UiResources.Entrance();
    private double seconds;
    private long lastFrame;
    private float calm = 1;
    private Fit fit = new Fit(Mode.FULL, 0, 0, 1);
    /** Vanilla InventoryScreen swallows the release after a book toggle; so do we (no stray drop/place). */
    private boolean swallowRelease;
    /** Slot under the mouse for this frame (vanilla only sets focusedSlot after the background). */
    private int hoveredId = -1;

    public ChacaInventoryScreen(PlayerEntity player) {
        this(player, new AbstractCraftingRecipeBookWidget(player.playerScreenHandler));
    }

    private ChacaInventoryScreen(PlayerEntity player, AbstractCraftingRecipeBookWidget book) {
        super(player.playerScreenHandler, book, player.getInventory(), Text.literal("소지품"));
        this.book = book;
    }

    /** True when a vanilla survival inventory should be shown as this screen instead. */
    public static boolean replaces(net.minecraft.client.MinecraftClient client, net.minecraft.client.gui.screen.Screen screen) {
        return screen != null && screen.getClass() == InventoryScreen.class && client.player != null
                && client.interactionManager != null && !client.interactionManager.hasCreativeInventory();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        super.init();
        // The vanilla green recipe button is replaced by recipe_book.png; both toggle the same widget.
        for (var child : List.copyOf(children()))
            if (child instanceof TexturedButtonWidget button && button.getX() == HIDDEN && button.getY() == HIDDEN) remove(button);
        slotsByPosition.clear();
        for (Slot slot : handler.slots) slotsByPosition.putIfAbsent(key(slot.x, slot.y), slot.id);
        lastFrame = 0;
        focusedSlot = null;
        updateFrame();
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        // Game mode switched to creative while open: hand over to vanilla, which opens the creative screen.
        if (client != null && client.player != null && client.interactionManager != null && client.interactionManager.hasCreativeInventory())
            client.setScreen(new net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen(client.player,
                    client.player.networkHandler.getEnabledFeatures(), client.options.getOperatorItemsTab().getValue()));
    }

    private static final int HIDDEN = -1000;
    @Override protected ScreenPos getRecipeBookButtonPos() { return new ScreenPos(HIDDEN, HIDDEN); }

    // ------------------------------------------------------------------ frame state

    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xFFFFFFFFL); }

    private boolean holding() { return !handler.getCursorStack().isEmpty() || cursorDragging; }

    /** Time, float calm and window fit are fixed once per frame and reused by every click test. */
    private void updateFrame() {
        long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1000f;
        lastFrame = now;
        seconds = now / 1000.0;
        calm = ChacaInventoryLayout.approachCalm(calm, holding(), dt);
        // Snaps (no animated rescale): text is rasterized per pixel size, so a tween would bake new textures every frame.
        fit = ChacaInventoryLayout.fit(width, height, book.isOpen());
    }

    private float animatedY(Element e) { return ChacaInventoryLayout.centerY(e, seconds, calm); }

    // ------------------------------------------------------------------ input mapping

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        Integer id = width == 16 && height == 16 ? slotsByPosition.get(key(x, y)) : null;
        if (id == null) return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
        Element element = ChacaInventoryLayout.element(id);
        if (element == null || !ChacaInventoryLayout.visible(element, fit.mode()) || overBook(pointX, pointY)) return false;
        return ChacaInventoryLayout.hit(element, fit.localX(pointX), fit.localY(pointY), seconds, calm);
    }

    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        // Never throw items while the recipe book covers the screen or when clicking the book itself.
        if (fit.mode() == Mode.BOOK_ONLY || overBook(mouseX, mouseY) || overBookButton(mouseX, mouseY)) return false;
        return !ChacaInventoryLayout.insideContent(fit.localX(mouseX), fit.localY(mouseY), fit.mode());
    }

    private boolean overBook(double x, double y) {
        if (!book.isOpen()) return false;
        int left = ChacaInventoryLayout.bookLeft(width, ChacaInventoryLayout.narrow(width)), top = ChacaInventoryLayout.bookTop(height);
        return x >= left && x < left + ChacaInventoryLayout.BOOK_W && y >= top && y < top + ChacaInventoryLayout.BOOK_H;
    }

    /** Screen rect of the book toggle in BOOK_ONLY mode: just right of the vanilla book panel. */
    private int[] compactBookButton() {
        int left = ChacaInventoryLayout.bookLeft(width, ChacaInventoryLayout.narrow(width)) + ChacaInventoryLayout.BOOK_W + 6;
        int size = 26;
        left = Math.min(left, width - size - 2);
        return new int[]{left, ChacaInventoryLayout.bookTop(height), size, size};
    }

    private boolean overBookButton(double x, double y) {
        if (fit.mode() == Mode.BOOK_ONLY) {
            int[] r = compactBookButton();
            return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
        }
        return ChacaInventoryLayout.bookHit(fit.localX(x), fit.localY(y));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBookButton(mouseX, mouseY) && !overBook(mouseX, mouseY)) {
            book.toggleOpen();
            swallowRelease = true;
            focusedSlot = null;
            x = book.findLeftEdge(width, backgroundWidth);
            if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.15f, 0.25f));
            updateFrame();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (swallowRelease) { swallowRelease = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        updateFrame();
        hoveredId = -1;
        if (fit.mode() != Mode.BOOK_ONLY && !overBook(mouseX, mouseY)) {
            double lx = fit.localX(mouseX), ly = fit.localY(mouseY);
            for (Element e : ChacaInventoryLayout.elements())
                if (ChacaInventoryLayout.visible(e, fit.mode()) && ChacaInventoryLayout.hit(e, lx, ly, seconds, calm)) { hoveredId = e.slotId(); break; }
        }
        var images = UiResources.images();
        var text = UiResources.text();
        images.beginFrame();
        text.beginFrame();
        try {
            super.render(c, mouseX, mouseY, delta);
        } finally {
            images.endFrame();
        }
        entrance.draw(c, width, height);
    }

    /** Replaces vanilla's grey in-game darkening; drawBackground lays the navy veil instead. */
    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        drawBackground(c, delta, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        // Dark navy veil over the world keeps the floating elements readable.
        c.fillGradient(0, 0, width, height, 0x84050A18, 0xB4030611);
        if (fit.mode() == Mode.BOOK_ONLY) { drawCompactBookButton(c, mouseX, mouseY); return; }
        var m = c.getMatrices();
        m.push();
        try {
            m.translate(fit.x(), fit.y(), 0);
            m.scale(fit.scale(), fit.scale(), 1);
            drawDecor(c, fit.localX(mouseX), fit.localY(mouseY));
        } finally { m.pop(); }
    }

    private void drawDecor(DrawContext c, double mx, double my) {
        var text = UiResources.text();
        float cx = ChacaInventoryLayout.CENTER_X;
        // Title and section labels: dynamic text, never baked into the PNGs.
        ornament(c, cx, ChacaInventoryLayout.TITLE_Y, 96, 230);
        text.draw(c, "소지품", cx, ChacaInventoryLayout.TITLE_Y, 40, GOLD, SERIF, true);
        HudMesh.star(c, cx, 76, 4.5f, GOLD_SOFT);
        ornament(c, cx, ChacaInventoryLayout.CRAFT_LABEL_Y, 46, 70);
        text.draw(c, "조합", cx, ChacaInventoryLayout.CRAFT_LABEL_Y, 26, CREAM, SERIF, true);

        // Thin rings around the result and connectors from each input orb.
        float centerY = animatedY(ChacaInventoryLayout.element(ChacaInventoryLayout.RESULT));
        HudMesh.arc(c, cx, centerY, 128, 1.2f, 0, (float) (Math.PI * 2), GOLD_FAINT);
        HudMesh.arc(c, cx, centerY, 150, 0.8f, 0, (float) (Math.PI * 2), 0x26D9BC7E);
        for (int i = 0; i < ChacaInventoryLayout.INPUT_COUNT; i++) {
            var e = ChacaInventoryLayout.element(ChacaInventoryLayout.INPUT_START + i);
            float ox = e.cx(), oy = animatedY(e), dx = cx - ox, dy = centerY - oy, len = (float) Math.sqrt(dx * dx + dy * dy);
            float ux = dx / len, uy = dy / len;
            float ax = ox + ux * 76, ay = oy + uy * 76, bx = cx - ux * 112, by = centerY - uy * 112;
            HudMesh.line(c, ax, ay, bx, by, 1.4f, GOLD_SOFT);
            HudMesh.star(c, (ax + bx) / 2, (ay + by) / 2, 5, GOLD);
            HudMesh.star(c, ax, ay, 3.5f, GOLD);
        }

        // Result orb: crystal stays behind the item and fades when there is a result to read.
        Slot resultSlot = handler.getSlot(ChacaInventoryLayout.RESULT);
        boolean hasResult = resultSlot.hasStack();
        float orb = ChacaInventoryLayout.RESULT_ORB_SIZE;
        image(c, ORB, cx, centerY, orb * ORB_W / ORB_H * 1.0f, orb, ORB_W, ORB_H, 0xFFFFFFFF);
        float crystalH = ChacaInventoryLayout.CRYSTAL_HEIGHT;
        image(c, CRYSTAL, cx, centerY - 4, crystalH * CRYSTAL_W / CRYSTAL_H, crystalH, CRYSTAL_W, CRYSTAL_H, hasResult ? 0x52FFFFFF : 0xF2FFFFFF);
        if (hasResult) HudMesh.disk(c, cx, centerY, 58, 0x5A050B1C);
        if (hoveredId == ChacaInventoryLayout.RESULT) HudMesh.arc(c, cx, centerY, ChacaInventoryLayout.RESULT_HIT_RADIUS + 6, 1.6f, 0, (float) (Math.PI * 2), HOVER);

        for (int i = 0; i < ChacaInventoryLayout.INPUT_COUNT; i++) {
            var e = ChacaInventoryLayout.element(ChacaInventoryLayout.INPUT_START + i);
            float y = animatedY(e), h = ChacaInventoryLayout.ORB_SIZE;
            image(c, ORB, e.cx(), y, h * ORB_W / ORB_H, h, ORB_W, ORB_H, 0xFFFFFFFF);
            if (hoveredId == e.slotId())
                HudMesh.arc(c, e.cx(), y, ChacaInventoryLayout.ORB_HIT_RADIUS + 3, 1.6f, 0, (float) (Math.PI * 2), HOVER);
        }

        // Recipe book button.
        boolean bookHover = ChacaInventoryLayout.bookHit(mx, my);
        float bw = ChacaInventoryLayout.BOOK_WIDTH * (bookHover ? 1.05f : 1f);
        image(c, BOOK, ChacaInventoryLayout.BOOK_X, ChacaInventoryLayout.BOOK_Y, bw, bw * BOOK_H / BOOK_W, BOOK_W, BOOK_H, 0xFFFFFFFF);
        ornament(c, cx, ChacaInventoryLayout.BOOK_LABEL_Y, 54, 84);
        text.draw(c, "조합법", cx, ChacaInventoryLayout.BOOK_LABEL_Y, 22, bookHover || book.isOpen() ? GOLD : CREAM, SERIF, true);

        // Inventory pedestals (hidden while the recipe book shows the crafting cluster alone).
        if (fit.mode() != Mode.FULL) return;
        int selected = client != null && client.player != null ? client.player.getInventory().selectedSlot : -1;
        for (Element e : ChacaInventoryLayout.elements()) {
            if (e.kind() != Kind.MAIN && e.kind() != Kind.HOTBAR) continue;
            boolean hotbar = e.kind() == Kind.HOTBAR;
            float w = hotbar ? ChacaInventoryLayout.HOTBAR_PEDESTAL_W : ChacaInventoryLayout.PEDESTAL_W, py = e.pedestalY();
            image(c, PEDESTAL, e.cx(), py, w, w * PEDESTAL_H / PEDESTAL_W, PEDESTAL_W, PEDESTAL_H, 0xFFFFFFFF);
            boolean hovered = hoveredId == e.slotId();
            boolean dragged = cursorDragging && cursorDragSlots.contains(handler.getSlot(e.slotId()));
            if (hotbar && e.slotId() - ChacaInventoryLayout.HOTBAR_START == selected) {
                ellipse(c, e.cx(), py - 2, w * 0.52f, 14, 2.2f, CYAN);
                ellipse(c, e.cx(), py - 2, w * 0.47f, 11, 5f, CYAN_FAINT);
            }
            if (hovered || dragged) ellipse(c, e.cx(), py - 2, w * 0.5f, 13, 1.4f, hovered ? HOVER : 0x66F6E6B8);
        }
        HudMesh.line(c, 212, ChacaInventoryLayout.DIVIDER_Y, cx - 22, ChacaInventoryLayout.DIVIDER_Y, 1, GOLD_FAINT);
        HudMesh.line(c, cx + 22, ChacaInventoryLayout.DIVIDER_Y, 1460, ChacaInventoryLayout.DIVIDER_Y, 1, GOLD_FAINT);
        HudMesh.star(c, cx, ChacaInventoryLayout.DIVIDER_Y, 8, GOLD_SOFT);
        HudMesh.star(c, cx, ChacaInventoryLayout.DIVIDER_Y, 4, GOLD);
        for (int i = 0; i < 9; i++) {
            float x = ChacaInventoryLayout.HOTBAR_COL0 + i * ChacaInventoryLayout.HOTBAR_COL_STEP;
            text.draw(c, Integer.toString(i + 1), x, ChacaInventoryLayout.HOTBAR_NUMBER_Y, 24, i == selected ? 0xFFBFF4FA : 0xD9E7DCC2, SERIF, true);
        }
    }

    /** Items, counts, durability and cooldown on the layout. Called inside HandledScreen's (x, y) translation. */
    @Override
    protected void drawSlots(DrawContext c) {
        if (fit.mode() == Mode.BOOK_ONLY) return;
        var m = c.getMatrices();
        m.push();
        try {
            m.translate(-x, -y, 0);
            m.translate(fit.x(), fit.y(), 0);
            m.scale(fit.scale(), fit.scale(), 1);
            ItemStack cursor = handler.getCursorStack();
            int heldButton = ((school.magiccodex.mixin.HandledScreenAccessor) (Object) this).magiccodex$heldButtonType();
            for (Element e : ChacaInventoryLayout.elements()) {
                if (!ChacaInventoryLayout.visible(e, fit.mode())) continue;
                Slot slot = handler.getSlot(e.slotId());
                ItemStack stack = slot.getStack();
                String amount = null;
                boolean preview = false;
                // Same drag-split preview as vanilla HandledScreen#drawSlot.
                if (cursorDragging && cursorDragSlots.contains(slot) && !cursor.isEmpty() && cursorDragSlots.size() > 1
                        && ScreenHandler.canInsertItemIntoSlot(slot, cursor, true) && handler.canInsertIntoSlot(slot)) {
                    int max = Math.min(cursor.getMaxCount(), slot.getMaxItemCount(cursor));
                    int count = ScreenHandler.calculateStackSize(cursorDragSlots, heldButton, cursor) + (stack.isEmpty() ? 0 : stack.getCount());
                    if (count > max) { count = max; amount = "§e" + max; }
                    stack = cursor.copyWithCount(count);
                    preview = true;
                }
                if (stack.isEmpty()) continue;
                float lift = hoveredId == e.slotId() && (e.kind() == Kind.MAIN || e.kind() == Kind.HOTBAR) ? 3 : 0;
                float cy = animatedY(e) - lift;
                drawStack(c, stack, e.cx(), cy, e.itemSize(), e.slotId(), amount, preview, countAnchor(e, cy));
            }
            drawGhostRecipe(c);
        } finally { m.pop(); }
    }

    /**
     * Recipe-book ghost (clicked a recipe without the ingredients): vanilla draws it at slot.x/y,
     * so the same data is drawn faded at the orb positions instead. Empty orbs only, like vanilla.
     */
    @SuppressWarnings("unchecked")
    private void drawGhostRecipe(DrawContext c) {
        if (!book.isOpen()) return;
        Map<Slot, Object> items;
        try {
            var ghost = ((school.magiccodex.mixin.RecipeBookWidgetAccessor) (Object) book).magiccodex$ghostRecipe();
            items = ((school.magiccodex.mixin.GhostRecipeAccessor) (Object) ghost).magiccodex$items();
        } catch (ClassCastException | NullPointerException unavailable) { return; }
        if (items == null || items.isEmpty()) return;
        int index = (int) (seconds / 1.5);
        for (var entry : items.entrySet()) {
            Element e = ChacaInventoryLayout.element(entry.getKey().id);
            if (e == null || !ChacaInventoryLayout.visible(e, fit.mode()) || entry.getKey().hasStack()) continue;
            if (!(entry.getValue() instanceof GhostItemView view) || view.magiccodex$stacks().isEmpty()) continue;
            var stacks = view.magiccodex$stacks();
            ItemStack stack = stacks.get(Math.floorMod(index, stacks.size()));
            float cy = animatedY(e), size = e.itemSize();
            var m = c.getMatrices();
            m.push();
            try {
                m.translate(e.cx() - size / 2, cy - size / 2, 0);
                m.scale(size / 16f, size / 16f, 1);
                c.drawItem(stack, 0, 0);
            } finally { m.pop(); }
            m.push();
            try {
                m.translate(0, 0, 210);
                // Faded "not placed yet" look; red tint as in vanilla for the missing ingredients.
                HudMesh.disk(c, e.cx(), cy, size * 0.62f, view.magiccodex$isResult() ? 0x8C0A1426 : 0x8C2A0E18);
            } finally { m.pop(); }
        }
    }

    /** Where the count's right edge and vertical centre sit for this element. */
    private static float[] countAnchor(Element e, float cy) {
        return switch (e.kind()) {
            case RESULT -> new float[]{e.cx() + 50, cy + 40};
            case INPUT -> new float[]{e.cx() + 42, cy + 32};
            case MAIN, HOTBAR -> new float[]{e.cx() + 52, e.pedestalY() - 7};
        };
    }

    private void drawStack(DrawContext c, ItemStack stack, float cx, float cy, float size, int seed, String amount, boolean preview, float[] countAnchor) {
        var m = c.getMatrices();
        float left = cx - size / 2, top = cy - size / 2;
        m.push();
        try {
            // Scale the GUI matrix, not the image: high-resolution icons are sampled once at the final
            // size (ItemIconTextures), 3D/vanilla models are rendered at that size.
            m.translate(left, top, 0);
            m.scale(size / 16f, size / 16f, 1);
            c.drawItem(stack, 0, 0, seed);
        } finally { m.pop(); }
        m.push();
        try {
            m.translate(0, 0, 200);
            if (preview) c.fill(Math.round(left), Math.round(top), Math.round(left + size), Math.round(top + size), 0x30FFFFFF);
            if (stack.isItemBarVisible()) {
                float w = size * 0.8f, x0 = cx - w / 2, y0 = top + size + 2;
                c.fill(Math.round(x0), Math.round(y0), Math.round(x0 + w), Math.round(y0 + 3), 0xFF0B0F1A);
                c.fill(Math.round(x0), Math.round(y0), Math.round(x0 + w * stack.getItemBarStep() / 13f), Math.round(y0 + 2), 0xFF000000 | stack.getItemBarColor());
            }
            if (client != null && client.player != null) {
                float cooldown = client.player.getItemCooldownManager().getCooldownProgress(stack, client.getRenderTickCounter().getTickDelta(true));
                if (cooldown > 0) c.fill(Math.round(left), Math.round(top + size * (1 - cooldown)), Math.round(left + size), Math.round(top + size), 0x66E8F2FF);
            }
            String count = amount != null ? amount : ChacaInventoryLayout.count(stack.getCount());
            if (!count.isEmpty()) {
                int color = CREAM;
                if (count.startsWith("§e")) { count = count.substring(2); color = 0xFFF6DD72; }
                var text = UiResources.text();
                float w = text.width(count, 26, SERIF);
                text.drawOutline(c, count, countAnchor[0] - w, countAnchor[1], 26, color, SERIF, false);
            }
        } finally { m.pop(); }
    }

    /** Hook from HandledScreenHooksMixin: the carried stack, drawn at the layout's item size. */
    public void drawCursorStack(DrawContext c, ItemStack stack, int localX, int localY, String amount) {
        if (stack.isEmpty()) return;
        float size = fit.mode() == Mode.BOOK_ONLY ? 16 : Math.max(16, ChacaInventoryLayout.MAIN_ITEM * fit.scale());
        // localX/Y are relative to (x, y) and point at vanilla's 16px item corner.
        float cx = localX + 8, cy = localY + 8;
        var m = c.getMatrices();
        m.push();
        try {
            m.translate(0, 0, 232);
            m.translate(cx - size / 2, cy - size / 2, 0);
            m.scale(size / 16f, size / 16f, 1);
            c.drawItem(stack, 0, 0);
        } finally { m.pop(); }
        String count = amount != null ? amount.replace("§e", "") : ChacaInventoryLayout.count(stack.getCount());
        if (count.isEmpty()) return;
        m.push();
        try {
            m.translate(0, 0, 432);
            var text = UiResources.text();
            float textSize = Math.max(8, 26 * fit.scale());
            float w = text.width(count, textSize, SERIF);
            text.drawOutline(c, count, cx + size * 0.62f - w, cy + size * 0.38f, textSize, amount != null && amount.startsWith("§e") ? 0xFFF6DD72 : CREAM, SERIF, false);
        } finally { m.pop(); }
    }

    @Override
    protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        // Titles are drawn by drawDecor with the codex fonts; vanilla "Crafting"/"Inventory" labels stay hidden.
    }

    private void drawCompactBookButton(DrawContext c, int mouseX, int mouseY) {
        int[] r = compactBookButton();
        boolean hover = overBookButton(mouseX, mouseY);
        if (hover) HudMesh.disk(c, r[0] + r[2] / 2f, r[1] + r[3] / 2f, r[2] * 0.62f, 0x33F6E6B8);
        image(c, BOOK, r[0] + r[2] / 2f, r[1] + r[3] / 2f, r[2], r[2] * (float) BOOK_H / BOOK_W, BOOK_W, BOOK_H, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ helpers

    private static void image(DrawContext c, Identifier path, float cx, float cy, float w, float h, int sourceW, int sourceH, int tint) {
        var m = c.getMatrices();
        m.push();
        try {
            m.translate(cx - w / 2, cy - h / 2, 0);
            // Draw at a fixed integer size and scale the remainder, so floating motion stays sub-pixel smooth.
            int iw = Math.max(1, (int) Math.ceil(w)), ih = Math.max(1, (int) Math.ceil(h));
            m.scale(w / iw, h / ih, 1);
            UiResources.images().drawTexture(c, path, 0, 0, 0, 0, iw, ih, sourceW, sourceH, sourceW, sourceH, tint);
        } finally { m.pop(); }
    }

    private static void ornament(DrawContext c, float cx, float y, float inner, float outer) {
        HudMesh.line(c, cx - outer, y, cx - inner, y, 1, GOLD_SOFT);
        HudMesh.line(c, cx + inner, y, cx + outer, y, 1, GOLD_SOFT);
        HudMesh.star(c, cx - inner + 6, y, 5, GOLD);
        HudMesh.star(c, cx + inner - 6, y, 5, GOLD);
    }

    private static void ellipse(DrawContext c, float cx, float cy, float rx, float ry, float thickness, int color) {
        final int steps = 40;
        float px = cx + rx, py = cy;
        for (int i = 1; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            float nx = cx + (float) Math.cos(a) * rx, ny = cy + (float) Math.sin(a) * ry;
            HudMesh.line(c, px, py, nx, ny, thickness, color);
            px = nx; py = ny;
        }
    }

    @Override public boolean shouldPause() { return false; }
}
