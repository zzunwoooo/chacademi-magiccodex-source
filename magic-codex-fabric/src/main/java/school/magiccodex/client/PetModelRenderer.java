package school.magiccodex.client;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Decode off-thread, upload one texture/frame, retain at most three preview models. */
final class PetModelRenderer implements AutoCloseable {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"pet-model-loader");t.setDaemon(true);return t;});
    private final LinkedHashMap<String,Loaded> cache=new LinkedHashMap<>(4,.75f,true);
    private final MinecraftClient client=MinecraftClient.getInstance();
    private static int serial;
    private record Prepared(PetBbModel model,List<NativeImage> images){}
    private static final class Loaded{
        final CompletableFuture<Prepared> future;final List<Identifier> textures=new ArrayList<>();
        String error="";int upload;boolean checked;
        Loaded(Path root,String id){
            future=CompletableFuture.supplyAsync(()->{
                var images=new ArrayList<NativeImage>();
                try{var model=PetBbModel.load(root,id);for(var t:model.textures)images.add(NativeImage.read(t.png()));return new Prepared(model,images);}
                catch(Exception e){images.forEach(NativeImage::close);throw new CompletionException(e);}
            },WORKER);
        }
    }
    String status(String id){var value=cache.get(id);if(value==null||!value.future.isDone())return "모델을 불러오는 중…";if(!value.error.isEmpty())return value.error;return "";}
    private Loaded get(String id){
        var value=cache.get(id);if(value!=null)return value;
        value=new Loaded(PetClient.modelRoot(),id);cache.put(id,value);
        while(cache.size()>3){var first=cache.entrySet().iterator().next();cache.remove(first.getKey());dispose(first.getValue());}
        return value;
    }
    private void dispose(Loaded loaded){
        for(var texture:loaded.textures)client.getTextureManager().destroyTexture(texture);
        loaded.textures.clear();
        loaded.future.thenAccept(p->client.execute(()->{for(var im:p.images)if(im!=null)im.close();p.images.clear();}));
    }
    boolean draw(DrawContext c,String id,float cx,float cy,float size,float yaw,double time){
        Loaded value=get(id);if(!value.future.isDone()||!value.error.isEmpty())return false;
        Prepared prepared;
        try{prepared=value.future.join();}catch(CompletionException e){
            value.error=e.getCause() instanceof NoSuchFileException?"missing": "모델을 읽지 못했습니다";
            if(!(e.getCause() instanceof NoSuchFileException))org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Pet model {}: {}",id,e.getCause().toString());return false;
        }
        if(value.upload<prepared.images.size()){
            NativeImage image=prepared.images.get(value.upload);
            var texture=new NativeImageBackedTexture(image);texture.setFilter(false,false);
            var textureId=Identifier.of("magiccodex","pet_model/"+serial++);
            client.getTextureManager().registerTexture(textureId,texture);
            // Keep native PNG pixels; registering/loading must not re-enable linear filtering.
            texture.setFilter(false,false);
            prepared.images.set(value.upload,null);value.textures.add(textureId);value.upload++;
            return false;
        }
        var model=prepared.model;
        if(!value.checked){value.checked=true;for(String warning:model.warnings)org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Pet model {}: {}",id,warning);}
        float animationTime=model.length>0?(float)(time%model.length):0;
        Map<String,Map<String,float[]>> pose=new HashMap<>();
        for(var track:model.tracks)pose.computeIfAbsent(track.node(),k->new HashMap<>()).put(track.channel(),PetBbModel.sample(track.keys(),animationTime));
        Matrix4f view=new Matrix4f().rotateX((float)Math.toRadians(9)).rotateY((float)Math.toRadians(yaw)).translate(-model.center[0],-model.center[1],-model.center[2]);
        var faces=new ArrayList<Quad>();for(var root:model.roots)collect(root,new Matrix4f(),view,pose,model,faces);
        faces.sort(Comparator.comparingDouble(Quad::depth));float scale=size/model.span;
        var matrix=new Matrix4f(c.getMatrices().peek().getPositionMatrix());
        c.draw(provider->{
            RenderLayer previousLayer=null;
            for(var face:faces){
                var layer=RenderLayer.getGuiTextured(value.textures.get(face.texture));
                // GUI quads have no model depth buffer: retain painter order across texture layers.
                if(previousLayer!=null&&previousLayer!=layer&&provider instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate)immediate.draw();
                previousLayer=layer;
                var v=provider.getBuffer(layer);
                int[] order=area(face.points)>0?new int[]{3,2,1,0}:new int[]{0,1,2,3};
                for(int i:order){var pt=face.points[i];v.vertex(matrix,cx+pt.x*scale,cy-pt.y*scale,0).texture(face.uv[i][0],face.uv[i][1]).color(face.color);}
            }
        });
        return true;
    }
    private record Quad(int texture,Vector3f[] points,float[][] uv,float depth,int color){}
    private static float area(Vector3f[] p){return (p[1].x-p[0].x)*(-(p[2].y-p[0].y))-(-(p[1].y-p[0].y))*(p[2].x-p[0].x);}
    private static Matrix4f rotate(Matrix4f m,float[] origin,float[] rotation,float[] pos,float[] scale){
        return m.translate(pos[0],pos[1],pos[2]).translate(origin[0],origin[1],origin[2])
            .rotateZYX((float)Math.toRadians(rotation[2]),(float)Math.toRadians(rotation[1]),(float)Math.toRadians(rotation[0]))
            .scale(scale[0],scale[1],scale[2]).translate(-origin[0],-origin[1],-origin[2]);
    }
    private static void collect(PetBbModel.Node n,Matrix4f parent,Matrix4f view,Map<String,Map<String,float[]>> pose,PetBbModel model,List<Quad> out){
        var channels=pose.getOrDefault(n.id(),Map.of());float[] r=n.rotation().clone(),extra=channels.getOrDefault("rotation",new float[]{0,0,0});
        for(int i=0;i<3;i++)r[i]+=extra[i];
        Matrix4f matrix=rotate(new Matrix4f(parent),n.origin(),r,channels.getOrDefault("position",new float[]{0,0,0}),channels.getOrDefault("scale",new float[]{1,1,1}));
        if(n.cube()!=null){
            var cube=n.cube();var cm=rotate(new Matrix4f(matrix),cube.origin(),cube.rotation(),new float[]{0,0,0},new float[]{1,1,1});var transform=new Matrix4f(view).mul(cm);
            for(var face:cube.faces()){
                Vector3f[] p=new Vector3f[4];float depth=0;
                for(int i=0;i<4;i++){float[] q=face.points()[i];p[i]=transform.transformPosition(new Vector3f(q[0],q[1],q[2]));depth+=p[i].z/4;}
                var tex=model.textures.get(face.texture());float[] u=face.uv();
                float[][] raw={{u[0]/tex.uvWidth(),u[1]/tex.uvHeight()},{u[2]/tex.uvWidth(),u[1]/tex.uvHeight()},{u[2]/tex.uvWidth(),u[3]/tex.uvHeight()},{u[0]/tex.uvWidth(),u[3]/tex.uvHeight()}};
                float[][] uv=new float[4][];for(int i=0;i<4;i++)uv[i]=raw[(i+face.rotation())%4];
                Vector3f normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
                float len=normal.length();if(len<.0001f)continue;normal.div(len);
                int shade=(int)(255*(.70+.30*Math.abs(normal.y*.7+normal.z*.45-normal.x*.2)));
                shade=Math.clamp(shade,100,255);out.add(new Quad(face.texture(),p,uv,depth,0xff000000|shade<<16|shade<<8|shade));
            }
        }
        for(var child:n.children())collect(child,matrix,view,pose,model,out);
    }
    @Override public void close(){cache.values().forEach(this::dispose);cache.clear();}
}
