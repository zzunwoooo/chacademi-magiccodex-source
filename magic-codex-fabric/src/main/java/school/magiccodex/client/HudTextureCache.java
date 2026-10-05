package school.magiccodex.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Original PNG files stay unchanged. Private GPU copies use alpha-correct trilinear minification. */
public final class HudTextureCache implements AutoCloseable {
    private static final AtomicLong NEXT=new AtomicLong();
    private static final ShaderProgramKey PROGRAM=new ShaderProgramKey(
            Identifier.of("magiccodex","core/hud_premultiplied"),VertexFormats.POSITION_TEXTURE_COLOR,Defines.EMPTY);
    private final MinecraftClient client;
    private final long instance=NEXT.getAndIncrement();
    private int serial;
    private record Texture(Identifier id,int width,int height,int levels,RenderLayer layer) {}
    private record Region(String path,int x,int y,int width,int height) {}
    private final Map<Region,Optional<Texture>> textures=new HashMap<>();
    private final Map<Region,Long> lastUsed=new HashMap<>();
    // Decoded atlases are shared during upload, then released at the end of that frame.
    private final Map<String,NativeImage> sources=new HashMap<>();
    private long frame;
    private long textureBytes,loads;
    private final AsyncUiLoader<Region,NativeImage> pending;
    private boolean closed,missedThisFrame;
    private static final java.util.concurrent.ExecutorService DECODER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"magiccodex-ui-images");t.setDaemon(true);return t;});
    public HudTextureCache(MinecraftClient client) { this(client,false); }
    public HudTextureCache(MinecraftClient client,boolean asynchronous) {
        this.client=client;
        var manager=client.getResourceManager();
        pending=asynchronous?new AsyncUiLoader<>(DECODER,4,r->decode(r,manager),NativeImage::close):null;
    }
    public void beginFrame() {
        endFrame();
        missedThisFrame=false;
        frame++;
        if(pending!=null){
            // Only one GPU upload per frame; PNG decoding/cropping/premultiplication never blocks rendering.
            var ready=pending.takeReady();
            if(ready!=null){
                var result=ready.getValue();
                if(result.error()!=null){org.slf4j.LoggerFactory.getLogger("magiccodex").warn("UI texture failed: {}",ready.getKey(),result.error());textures.put(ready.getKey(),Optional.empty());}
                else textures.put(ready.getKey(),upload(ready.getKey(),result.value()));
                lastUsed.put(ready.getKey(),frame);
            }
        }
        // Never destroy an image referenced by the current or previous draw batch.
        if(textures.size()>192 || textureBytes>128L*1024*1024) {
            var oldest=new ArrayList<>(lastUsed.entrySet());oldest.sort(Map.Entry.comparingByValue());
            for(var entry:oldest) {
                if(textures.size()<=192 && textureBytes<=128L*1024*1024)break;
                if(entry.getValue()<frame-1) {
                    var removed=textures.remove(entry.getKey());if(removed==null)continue;
                    removed.ifPresent(t->{textureBytes-=bytes(t);client.getTextureManager().destroyTexture(t.id());});
                    lastUsed.remove(entry.getKey());
                }
            }
        }
    }
    public void endFrame() { if(pending==null)clearSources(); }
    private void clearSources(){sources.values().forEach(NativeImage::close);sources.clear();}
    private static long bytes(Texture t){return (long)t.width()*t.height()*16/3;}
    public boolean missedThisFrame(){return missedThisFrame;}
    public long uploadCount(){return loads;}
    public long retainedBytes(){return textureBytes;}

    private Optional<Texture> load(Region region) {
        try{return upload(region,decode(region,client.getResourceManager()));}
        catch(Exception error){org.slf4j.LoggerFactory.getLogger("magiccodex").warn("UI texture failed: {}",region,error);return Optional.empty();}
    }
    private NativeImage decode(Region region,net.minecraft.resource.ResourceManager manager)throws Exception {
        var source=sources.get(region.path());
        if(source==null){
            // A small worker-owned source cache avoids decoding the same atlas for each crop.
            if(pending!=null && sources.size()>=2)clearSources();
            try(var stream=manager.getResourceOrThrow(Identifier.of(region.path())).getInputStream()) {source=NativeImage.read(stream);}
            sources.put(region.path(),source);
        }
        int w=region.width()==0?source.getWidth():region.width(),h=region.height()==0?source.getHeight():region.height();
        if(region.x()<0||region.y()<0||w<=0||h<=0||region.x()+w>source.getWidth()||region.y()+h>source.getHeight())throw new IllegalArgumentException("Image region outside source: "+region);
        NativeImage image=new NativeImage(w,h,false);
        try{source.copyRect(image,region.x(),region.y(),0,0,w,h,false,false);image.apply(PremultipliedAlpha::pixel);return image;}
        catch(Exception error){image.close();throw error;}
    }
    private Optional<Texture> upload(Region region,NativeImage prepared) {
        if(closed){prepared.close();return Optional.empty();}
        NativeImage image=prepared;
        NativeImageBackedTexture texture=null;
        try {
            int width=image.getWidth(),height=image.getHeight();
            texture=new NativeImageBackedTexture(image); image=null;
            int levels=31-Integer.numberOfLeadingZeros(Math.max(width,height));
            texture.bindTexture();
            // NativeImage's base allocation sets MAX_LEVEL and MAX_LOD to zero.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,levels);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD,0);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD,levels);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            texture.setClamp(true);
            texture.setFilter(true,true);
            if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,levels,GL11.GL_TEXTURE_WIDTH)!=1)
                throw new IllegalStateException("Incomplete HUD mip chain");
            var id=Identifier.of("magiccodex","runtime_hud/"+instance+"/"+serial++);
            var layer=new HudLayer(client,id);
            client.getTextureManager().registerTexture(id,texture);
            texture=null;
            var loaded=new Texture(id,width,height,levels,layer);textureBytes+=bytes(loaded);loads++;
            return Optional.of(loaded);
        } catch(Exception error) {
            if(texture!=null) texture.close();
            if(image!=null) image.close();
            org.slf4j.LoggerFactory.getLogger("magiccodex").warn("UI texture failed: {}",region,error);
            return Optional.empty();
        }
    }
    public boolean draw(DrawContext ctx,String path,int cx,int cy,int size,int tint) {
        return drawCentered(ctx,path,cx,cy,size,tint,false);
    }
    public boolean drawCentered(DrawContext ctx,String path,float cx,float cy,int size,int tint) {
        return drawCentered(ctx,path,cx,cy,size,tint,true);
    }
    private Optional<Texture> get(Region region) {
        if(closed)return Optional.empty();
        lastUsed.put(region,frame);
        if(pending!=null && !textures.containsKey(region)){missedThisFrame=true;pending.request(region);return Optional.empty();}
        return textures.computeIfAbsent(region,this::load);
    }
    private boolean drawCentered(DrawContext ctx,String path,float cx,float cy,int size,int tint,boolean roundCenter) {
        if(path==null||path.isBlank())return true; // Intentionally empty icon; no missing-texture or fallback glyph.
        var value=get(new Region(path,0,0,0,0));
        if(value.isEmpty()) return false;
        var t=value.get(); float scale=(float)size/Math.max(t.width(),t.height());
        int w=Math.round(t.width()*scale),h=Math.round(t.height()*scale);
        render(ctx,t,roundCenter?Math.round(cx-w/2f):(int)cx-w/2,
                roundCenter?Math.round(cy-h/2f):(int)cy-h/2,w,h,tint);
        return true;
    }
    public void drawTexture(DrawContext ctx,Identifier path,int x,int y,int u,int v,int w,int h,int tw,int th) {
        drawTexture(ctx,path,x,y,u,v,w,h,w,h,tw,th,0xFFFFFFFF);
    }
    public void drawTexture(DrawContext ctx,Identifier path,int x,int y,int u,int v,int w,int h,int sw,int sh,int tw,int th) {
        drawTexture(ctx,path,x,y,u,v,w,h,sw,sh,tw,th,0xFFFFFFFF);
    }
    public void drawTexture(DrawContext ctx,Identifier path,int x,int y,int u,int v,int w,int h,int sw,int sh,int tw,int th,int tint) {
        if(w<=0 || h<=0) return;
        get(new Region(path.toString(),u,v,sw,sh)).ifPresent(t->render(ctx,t,x,y,w,h,tint));
    }
    /** Call on the render thread during loading to keep decoding/uploads out of first use. */
    public void prepareRegion(Identifier path,int x,int y,int width,int height){
        get(new Region(path.toString(),x,y,width,height));
    }
    private void render(DrawContext ctx,Texture t,int x,int y,int w,int h,int tint) {
        ctx.drawTexture(id->t.layer(),t.id(),x,y,0,0,w,h,t.width(),t.height(),t.width(),t.height(),tint);
    }
    /** Used by the in-engine check to verify actual GPU filters, not just Java settings. */
    public void verifyGpuState() {
        for(var optional:textures.values()) {
            var t=optional.orElseThrow();
            client.getTextureManager().getTexture(t.id()).bindTexture();
            if(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)!=GL11.GL_LINEAR_MIPMAP_LINEAR
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,t.levels(),GL11.GL_TEXTURE_WIDTH)!=1)
                throw new IllegalStateException("HUD trilinear minification is not active");
        }
    }
    public int size() { return textures.size(); }
    @Override public void close() {
        if(closed)return;closed=true;
        if(pending!=null){pending.close();DECODER.execute(this::clearSources);}
        textures.values().forEach(t->t.ifPresent(v->client.getTextureManager().destroyTexture(v.id())));
        textures.clear();
        lastUsed.clear();
        textureBytes=0;
        endFrame();
    }
    private static final class HudLayer extends RenderLayer {
        HudLayer(MinecraftClient client,Identifier id) {
            super("magiccodex_hud",VertexFormats.POSITION_TEXTURE_COLOR,VertexFormat.DrawMode.QUADS,1536,false,false,
                    ()->{
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                        RenderSystem.disableCull();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(false);
                        RenderSystem.setShader(PROGRAM);
                        RenderSystem.setShaderTexture(0,id);
                        client.getTextureManager().getTexture(id).setFilter(true,true);
                    },()->{
                        RenderSystem.depthMask(true);
                        RenderSystem.enableCull();
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });
        }
    }
}
