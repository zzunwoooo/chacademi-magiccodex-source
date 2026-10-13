package school.magiccodex.client;

/** Bounded, scrollable status-effect viewport in GUI pixels. No item slots live in this area. */
final class InventoryEffectLayout {
    static final int ROW = 28, FOOTER = 11;
    record Panel(int x, int y, int width, int rows, int total) {
        int height() { return rows * ROW + (total > rows ? FOOTER : 0); }
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height(); }
        int offset(int requested) { return Math.max(0, Math.min(requested, total - rows)); }
    }
    static Panel fit(int width, int height, boolean bookOpen, int count) {
        if (count <= 0 || width < 80 || height < 40) return new Panel(4, 4, 0, 0, 0);
        var inventory = ChacaInventoryLayout.fit(width, height, false);
        int x = Math.max(4, Math.round(inventory.screenX(64)));
        int right = Math.round(inventory.screenX(414));
        int y = Math.max(4, Math.round(inventory.screenY(120)));
        int bottom = Math.min(height - 4, Math.round(inventory.screenY(486)));
        if (bookOpen) {
            x = 4; y = 4; right = Math.min(width - 4, ChacaInventoryLayout.bookLeft(width,true)-4);
            bottom = height-4;
        }
        int w = Math.max(64, Math.min(190, right - x));
        w = Math.min(w, width - 8); x=Math.max(4,right-w);
        int rows = Math.max(1, Math.min(count, (bottom - y - FOOTER) / ROW));
        rows = Math.min(rows, Math.max(1, (height - y - FOOTER - 4) / ROW));
        return new Panel(x, y, w, rows, count);
    }
}
