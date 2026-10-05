package school.magiccodex.client;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Text remains dynamic, but is rasterized once per string/size and uploaded as an RGBA texture. */
public final class CodexTypography implements AutoCloseable {
    private record Key(String value, String face, int pixelSize64, boolean outline) {}
    private record Sprite(Identifier id, int width, int height) {}
    private record WidthKey(String value, String face, float size) {}
    private static final java.util.concurrent.atomic.AtomicLong NEXT_INSTANCE = new java.util.concurrent.atomic.AtomicLong();
    private final long instance = NEXT_INSTANCE.getAndIncrement();
    private final MinecraftClient client;
    private static volatile Map<String,Font> sharedFonts=Map.of();
    private final Map<String, Font> fonts;
    private final Map<Key, Sprite> sprites = new java.util.LinkedHashMap<>(128,.75f,true);
    private final Map<WidthKey, Float> widths = new java.util.LinkedHashMap<>(256,.75f,true);
    private int serial;
    private long spriteBytes;

    public CodexTypography(MinecraftClient client) {
        this.client = client;
        if(sharedFonts.isEmpty())sharedFonts=loadFonts(client.getResourceManager());
        fonts=sharedFonts;
    }
    static Map<String,Font> loadFonts(net.minecraft.resource.ResourceManager manager){
        var files=Map.of("title","maruburi_semibold.ttf","heading","maruburi_semibold.ttf","section","maruburi_bold.ttf",
                "body","pretendard_regular.ttf","label","pretendard_medium.ttf","hud_bold","pretendard_bold.ttf","ui_extra_bold","jamsil_extrabold.ttf");
        var loaded=new HashMap<String,Font>();var result=new HashMap<String,Font>();
        for(var entry:files.entrySet()){
            var file=entry.getValue();var font=loaded.get(file);
            if(font==null){try(var stream=manager.getResourceOrThrow(Identifier.of("magiccodex","font/"+file)).getInputStream()){
                font=Font.createFont(Font.TRUETYPE_FONT,stream);loaded.put(file,font);
            }catch(Exception error){throw new IllegalStateException("Could not load codex font "+file,error);}}
            result.put(entry.getKey(),font);
        }
        // Initialize AWT's Korean glyph/font machinery during resource loading, not the first window.
        for(var font:loaded.values())CodexTextRasterizer.rasterize(font,"차카데미아 마법 도감 친구 목록 바람의 전언 0123456789",23);
        return Map.copyOf(result);
    }
    static void installFonts(Map<String,Font> fonts){sharedFonts=Map.copyOf(fonts);}

    public void beginFrame() {
        // Clear only before new draw calls, never while the current frame references a texture.
        var it=sprites.entrySet().iterator();
        while(it.hasNext() && (sprites.size()>768 || spriteBytes>16L*1024*1024)){
            var sprite=it.next().getValue();spriteBytes-=(long)sprite.width()*sprite.height()*4;
            client.getTextureManager().destroyTexture(sprite.id());it.remove();
        }
        var widthsIt=widths.keySet().iterator();while(widths.size()>4096&&widthsIt.hasNext()){widthsIt.next();widthsIt.remove();}
    }

    public float width(String value, float size, Identifier face) {
        return widths.computeIfAbsent(new WidthKey(value, face.getPath(), size),
                key -> CodexTextRasterizer.width(fonts.get(key.face()), value, size));
    }

    public void draw(DrawContext context, String value, float x, float y, float size, int color,
                     Identifier face, boolean centered) {
        draw(context,value,x,y,size,color,face,centered,false);
    }
    public void drawOutline(DrawContext context,String value,float x,float y,float size,int color,Identifier face,boolean centered){draw(context,value,x,y,size,color,face,centered,true);}
    private void draw(DrawContext context,String value,float x,float y,float size,int color,Identifier face,boolean centered,boolean outline){
        if (value.isBlank()) return;
        var matrix = context.getMatrices().peek().getPositionMatrix();
        // GameRenderer's projection uses framebuffer / scaleFactor, not rounded Screen.width.
        // Dividing by that rounded width introduces fractional pixels at GUI scale 3.
        float deviceX = (float) client.getWindow().getScaleFactor();
        float deviceY = deviceX;
        float pixelsPerUnitX = Math.abs(matrix.m00() * deviceX);
        float pixelsPerUnitY = Math.abs(matrix.m11() * deviceY);
        int pixelSize64 = Math.max(64, Math.round(size * pixelsPerUnitY * 64));
        var key = new Key(value, face.getPath(), pixelSize64,outline);
        Sprite sprite = sprites.computeIfAbsent(key, this::bake);
        float left = centered ? x - sprite.width() / (2f * pixelsPerUnitX)
                : x - CodexTextRasterizer.PADDING / pixelsPerUnitX;
        float top = y - sprite.height() / (2f * pixelsPerUnitY);
        context.getMatrices().push();
        try {
            context.getMatrices().translate(left, top, 1);
            context.getMatrices().scale(1 / pixelsPerUnitX, 1 / pixelsPerUnitY, 1);
            var positioned = context.getMatrices().peek().getPositionMatrix();
            float px = positioned.m30() * deviceX, py = positioned.m31() * deviceY;
            context.getMatrices().translate(Math.round(px) - px, Math.round(py) - py, 0);
            context.drawTexture(RenderLayer::getGuiTextured, sprite.id(), 0, 0, 0, 0,
                    sprite.width(), sprite.height(), sprite.width(), sprite.height(), color);
        } finally { context.getMatrices().pop(); }
    }

    private Sprite bake(Key key) {
        var image = CodexTextRasterizer.rasterize(fonts.get(key.face()), key.value(), key.pixelSize64() / 64f,key.outline());
        var nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            // Preserve white RGB even in transparent texels for clean tinting at the edges.
            nativeImage.setColorArgb(x, y, (argb & 0xFF000000) | 0x00FFFFFF);
        }
        var id = Identifier.of("magiccodex", "runtime_text/" + instance + "/" + serial++);
        var texture = new NativeImageBackedTexture(nativeImage);
        texture.setFilter(true, false);
        client.getTextureManager().registerTexture(id, texture);
        spriteBytes+=(long)image.getWidth()*image.getHeight()*4;
        return new Sprite(id, image.getWidth(), image.getHeight());
    }

    private void clearSprites() {
        sprites.values().forEach(sprite -> client.getTextureManager().destroyTexture(sprite.id()));
        sprites.clear();
        spriteBytes=0;
    }

    @Override public void close() {
        clearSprites();
        widths.clear();
    }
    public int bakedCount(){return serial;}
    /** Pure CPU layout for the background paragraph worker; does not touch the mutable glyph caches. */
    java.util.List<String> wrapText(String value,float width,float size){
        var font=fonts.get("body");return CodexTextLayout.wrap(value,width,s->CodexTextRasterizer.width(font,s,size));
    }
}
