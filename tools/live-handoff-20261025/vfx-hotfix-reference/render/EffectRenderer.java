package dev.portablevfx.client.render;

import dev.portablevfx.client.EffectRuntime;
import dev.portablevfx.client.PortableVfxClient;
import dev.portablevfx.client.definition.EffectLibrary;
import dev.portablevfx.client.render.gl.WorldTargetPass;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Claude lifecycle, warmup and retained-world-depth render pass. */
public final class EffectRenderer implements AutoCloseable, EffectLibrary.Warmup {
    public static final int MAX_LOADED_EFFECTS=512;
    private final EffectRuntime runtime;private final EffectLibrary library;
    private final Map<String,EffectBackend> backends=new LinkedHashMap<>();
    private final Set<String> failedBackends=new HashSet<>();
    private long worldFrames;
    public long worldFrames() { return worldFrames; }
    private record Loaded(EffectBackend backend,EffectBackend.Asset asset){}
    private record Instance(EffectRuntime.ActiveEffect active,EffectBackend.Instance handle){}
    private final Map<Identifier,Loaded> loaded=new LinkedHashMap<>();
    private final Map<UUID,Instance> instances=new LinkedHashMap<>();
    private final Set<Identifier> failedAssets=new HashSet<>();
    private long generation=-1,revision=-1;private Vec3d origin;private double lastSimulationTime=Double.NaN;private float frozenTickDelta;
    private final java.util.function.Supplier<int[]> warmupDimensions;
    public EffectRenderer(EffectRuntime runtime,EffectLibrary library){this(runtime,library,()->{
        var target=MinecraftClient.getInstance().getFramebuffer();return new int[]{target.textureWidth,target.textureHeight};
    });}
    EffectRenderer(EffectRuntime runtime,EffectLibrary library,java.util.function.Supplier<int[]> dimensions){
        this.runtime=runtime;this.library=library;this.warmupDimensions=java.util.Objects.requireNonNull(dimensions);
    }
    private long gpuPrewarmStarted;
    private volatile String residentFingerprint;
    private volatile Set<Identifier> gpuReadyIds=Set.of();
    private int prewarmDeferred;
    private final java.util.concurrent.atomic.AtomicLong warmupEpoch=new java.util.concurrent.atomic.AtomicLong();
    private volatile String prewarmStatus="Claude preload: awaiting resource reload";
    /** Immutable ready inventory; never exposes CPU-only, partially uploaded, or failed assets. */
    public Set<Identifier> readyEffectIds(){
        if(gpuReadyIds.isEmpty()||!failedBackends.isEmpty())return Set.of();
        var raw=backends.get("claude");
        try{
            if(!(raw instanceof dev.portablevfx.client.claude.ClaudeBackend backend)||backend.worldDepthPending())return Set.of();
        }catch(dev.portablevfx.client.render.gl.GpuFailureException lostContext){
            residentFingerprint=null;gpuReadyIds=Set.of();return Set.of();
        }
        return gpuReadyIds;
    }
    /** Local inventory completeness. The server also compares ready IDs against its catalog requirements. */
    public boolean isReady(){return !library.ids().isEmpty()&&library.configErrors().isEmpty()
            &&failedAssets.isEmpty()&&readyEffectIds().size()==library.ids().size();}
    public String readinessDiagnostic(){return "gpuReady="+readyEffectIds().size()+", cachedReady="+gpuReadyIds.size()
            +", failedBackends="+failedBackends+", failedAssets="+failedAssets.size();}
    public String prewarmStatus(){
        var backend=backends.get("claude");
        return prewarmStatus+" / resident="+loaded.size()+" / available="+library.ids().size()+" / unloaded="+Math.max(0,library.ids().size()-loaded.size()-failedAssets.size())+(backend instanceof dev.portablevfx.client.claude.ClaudeBackend c ? " / "+c.diagnostics()+(c.worldDepthPending()?" / world depth pending":"") : "");
    }
    private record PreparedEntry(Identifier id, Identifier asset, dev.portablevfx.client.claude.ClaudeBackend.Prepared prepared) { }
    private record PreloadBatch(dev.portablevfx.client.claude.ClaudeBackend.PreparationCache cache,
                                List<PreparedEntry> entries, Map<Identifier,String> errors, int deferred, long cpuNanos, long epoch,String fingerprint,boolean reuse,long started,dev.portablevfx.client.definition.LoadLimits limits) implements AutoCloseable {
        @Override public void close(){for(var e:entries)e.prepared().close();if(cache!=null)cache.close();}
    }
    @Override public Object prepare(EffectLibrary.Snapshot snapshot){
        long epoch=warmupEpoch.incrementAndGet();gpuReadyIds=Set.of();prewarmStatus="Claude preload: decoding resources";
        var limits=library.limits();
        String fingerprint=ResourceFingerprint.of(snapshot,limits);
        if(snapshot.valid()&&fingerprint.equals(residentFingerprint))
            return new PreloadBatch(null,List.of(),Map.of(),0,0,epoch,fingerprint,true,System.nanoTime(),limits);
        return prepareFresh(snapshot,epoch,fingerprint,limits);
    }
    private PreloadBatch prepareFresh(EffectLibrary.Snapshot snapshot,long epoch,String fingerprint,dev.portablevfx.client.definition.LoadLimits limits){
        long started=System.nanoTime();
        // BC7 encodings are cached per PNG digest; only the first launch after a pack change encodes.
        java.nio.file.Path textureCache=null;
        try{textureCache=FabricLoader.getInstance().getGameDir().resolve("portablevfx/texture-cache");}
        catch(RuntimeException|LinkageError noLoader){/* unit tests / tools: encode without a disk cache */}
        dev.portablevfx.client.claude.ClaudeBackend.textureCacheDirectory(textureCache);
        dev.portablevfx.client.claude.ClaudeBackend.beginTexturePreparation(30);
        var cache=new dev.portablevfx.client.claude.ClaudeBackend.PreparationCache(limits.prewarmRgbaBytes());
        List<PreparedEntry> entries=new ArrayList<>();Map<Identifier,String> errors=new LinkedHashMap<>();int attempted=0,deferred=0;
        for(var entry:snapshot.definitions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var definition=entry.getValue();if(!definition.backend().equals("claude"))continue;
            if(attempted++>=limits.prewarmSystems()){deferred++;continue;}
            Identifier asset=Identifier.of(definition.effect());
            try{entries.add(new PreparedEntry(entry.getKey(),asset,dev.portablevfx.client.claude.ClaudeBackend.prepare(
                    snapshot.read(asset),definition.magnification(),ref->snapshot.dependency(asset,ref),cache)));}
            catch(dev.portablevfx.client.claude.ClaudeBackend.CapacityException capacity){deferred++;}
            catch(java.io.IOException|RuntimeException error){errors.put(asset,error.getMessage());PortableVfxClient.LOG.error("Claude preload rejected {}: {}",asset,error.getMessage());}
        }
        return new PreloadBatch(cache,List.copyOf(entries),Map.copyOf(errors),deferred,System.nanoTime()-started,epoch,fingerprint,false,started,limits);
    }
    @Override public java.util.concurrent.CompletableFuture<Void> apply(EffectLibrary.Snapshot snapshot,Object prepared,java.util.concurrent.Executor executor){
        var batch=(PreloadBatch)prepared;
        if(batch.reuse()){
            var reuseWork=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                if(batch.epoch()!=warmupEpoch.get())return true;
                var raw=backends.get("claude");
                boolean reuse=batch.fingerprint().equals(residentFingerprint)&&raw instanceof dev.portablevfx.client.claude.ClaudeBackend backend
                        &&failedAssets.isEmpty()&&failedBackends.isEmpty()&&backend.canReuseGpu();
                if(reuse){
                    stopInstances();generation=snapshot.generation();revision=runtime.revision();
                    gpuPrewarmStarted=System.nanoTime();prewarmStatus="Claude preload reused: "+loaded.size()+" systems";

                    PortableVfxClient.LOG.info("{} / CPU decode 0 ms / uploads 0 / total {} ms",prewarmStatus,(System.nanoTime()-batch.started())/1_000_000);
                }
                return reuse;
            },executor).thenCompose(reused->reused?prewarmGpu(batch,executor):
                java.util.concurrent.CompletableFuture.supplyAsync(()->prepareFresh(snapshot,batch.epoch(),batch.fingerprint(),batch.limits()))
                    .thenCompose(fresh->apply(snapshot,fresh,executor)));
            return track(reuseWork,batch);
        }
        var result=java.util.concurrent.CompletableFuture.runAsync(()->{
            if(batch.epoch()!=warmupEpoch.get())return;
            // Resource reload is the only cache-invalidating event. World changes only stop instances.
            disposeRendering();generation=snapshot.generation();revision=runtime.revision();gpuPrewarmStarted=System.nanoTime();
            prewarmDeferred=batch.deferred();failedAssets.addAll(batch.errors().keySet());
            if(batch.entries().isEmpty()){prewarmStatus="Claude preload incomplete: 0 systems / "+batch.errors().size()+" errors / "+prewarmDeferred+" deferred; cold loading disabled";return;}
            try{
                var backend=(dev.portablevfx.client.claude.ClaudeBackend)backend("claude");
                for(var entry:batch.entries()){
                    try{loaded.put(entry.id(),new Loaded(backend,backend.installPrepared(entry.prepared())));}
                    catch(dev.portablevfx.client.claude.ClaudeBackend.CapacityException capacity){prewarmDeferred++;}
                    catch(java.io.IOException|RuntimeException error){failedAssets.add(entry.asset());PortableVfxClient.LOG.error("Claude preload install failed {}",entry.id(),error);}
                }
                prewarmStatus="Claude preload: uploading "+loaded.size()+" systems";
            }catch(java.io.IOException|RuntimeException|LinkageError error){
                failedBackends.add("claude");prewarmStatus="Claude preload failed: "+error.getMessage();PortableVfxClient.LOG.error(prewarmStatus,error);
            }
        },executor).thenCompose(ignored->prewarmGpu(batch,executor));
        return track(result,batch);
    }
    private java.util.concurrent.CompletableFuture<Void> track(java.util.concurrent.CompletableFuture<Void> work,PreloadBatch batch){
        return track(work,batch,batch.epoch());
    }
    java.util.concurrent.CompletableFuture<Void> track(java.util.concurrent.CompletableFuture<Void> work,AutoCloseable cleanup,long epoch){
        var exposed=completeAfterCleanup(work,cleanup);
        exposed.whenComplete((ignored,error)->{
            if(error!=null&&epoch==warmupEpoch.get()){residentFingerprint=null;gpuReadyIds=Set.of();}
            if(exposed.isCancelled()&&warmupEpoch.compareAndSet(epoch,epoch+1)){residentFingerprint=null;gpuReadyIds=Set.of();}
        });
        return exposed;
    }
    /** Cleanup failure must terminate the reload future, even if its caller already cancelled. */
    static java.util.concurrent.CompletableFuture<Void> completeAfterCleanup(
            java.util.concurrent.CompletableFuture<Void> work,AutoCloseable cleanup){
        var exposed=new java.util.concurrent.CompletableFuture<Void>();
        work.whenComplete((ignored,error)->{
            Throwable failure=error;
            try{cleanup.close();}
            catch(Throwable cleanupError){
                if(failure==null)failure=cleanupError;
                else if(failure!=cleanupError)failure.addSuppressed(cleanupError);
            }
            if(failure==null)exposed.complete(null);else exposed.completeExceptionally(failure);
        });
        return exposed;
    }
    private java.util.concurrent.CompletableFuture<Void> prewarmGpu(PreloadBatch batch,java.util.concurrent.Executor executor){
        return GpuWarmup.run(()->{
            var raw=backends.get("claude");if(!(raw instanceof dev.portablevfx.client.claude.ClaudeBackend backend)||failedBackends.contains("claude"))return true;
            try{
                int[] dimensions=warmupDimensions.get();
                boolean ready=backend.prewarmStep(dimensions[0],dimensions[1]);
                if(ready&&batch.epoch()==warmupEpoch.get()){
                    gpuReadyIds=Set.copyOf(loaded.keySet());
                    boolean complete=loaded.size()==library.ids().size()&&failedAssets.isEmpty()&&library.configErrors().isEmpty()&&prewarmDeferred==0;
                    residentFingerprint=complete?batch.fingerprint():null;
                    prewarmStatus=(complete?"Claude preload ready: ":"Claude preload incomplete: ")+loaded.size()+" systems / "+failedAssets.size()+" errors / "+prewarmDeferred+" deferred; cold loading disabled";
                    PortableVfxClient.LOG.info("{} / CPU {} ms / GPU apply {} ms / {} / {}",prewarmStatus,batch.cpuNanos()/1_000_000,(System.nanoTime()-gpuPrewarmStarted)/1_000_000,backend.diagnostics(),dev.portablevfx.client.claude.ClaudeBackend.textureSummary());
                    PortableVfxClient.LOG.info("Claude upload calls: {}",backend.uploadTimings());}
                return ready;
            }catch(RuntimeException|LinkageError error){failedBackends.add("claude");prewarmStatus="Claude preload failed: "+error.getMessage();PortableVfxClient.LOG.error(prewarmStatus,error);releaseFailedPreload();return true;}
        },executor,()->batch.epoch()==warmupEpoch.get(),metrics->PortableVfxClient.LOG.info(
                "Claude warmup timing: CPU decode {} ms / upload-stage active {} ms / queued wait {} ms / GPU elapsed {} ms / total {} ms / stages {} / slices {} / cancelled {}",
                batch.cpuNanos()/1_000_000,metrics.activeNanos()/1_000_000,metrics.queuedNanos()/1_000_000,metrics.elapsedNanos()/1_000_000,
                (System.nanoTime()-batch.started())/1_000_000,metrics.stages(),metrics.slices(),metrics.cancelled()));
    }
    private void releaseFailedPreload(){
        residentFingerprint=null;gpuReadyIds=Set.of();var backend=backends.remove("claude");
        loaded.entrySet().removeIf(entry->entry.getValue().backend()==backend);
        if(backend!=null)try{backend.close();}catch(RuntimeException|LinkageError cleanup){PortableVfxClient.LOG.warn("Claude preload cleanup failed",cleanup);}
    }
    private EffectBackend backend(String name)throws java.io.IOException{
        if(failedBackends.contains(name))throw new java.io.IOException("Backend unavailable: "+name);
        var result=backends.get(name);if(result!=null)return result;
        try{
            if (!name.equals("claude")) throw new IllegalArgumentException("Unsupported backend: " + name);
            result=new dev.portablevfx.client.claude.ClaudeBackend(library.limits().residentRgbaBytes());
            backends.put(name,result);runtime.status("Ready: "+String.join(" + ",backends.keySet())+" / retained main world depth"
                    +(FabricLoader.getInstance().isModLoaded("iris")?" / Iris "+FabricLoader.getInstance().getModContainer("iris").orElseThrow().getMetadata().getVersion().getFriendlyString():""));
            return result;
        }catch(RuntimeException|LinkageError e){failedBackends.add(name);throw e;}
    }
    public void render(WorldRenderContext context){
        worldFrames++;
        runtime.synchronizeWorld();
        try{
            var snapshot=library.snapshot();
            if(generation!=snapshot.generation()){
                clearRendering();generation=snapshot.generation();failedBackends.clear();
            }
            if(revision!=runtime.revision())synchronizeLifecycle();
            Vec3d camera=context.camera().getPos();
            if(origin!=null&&origin.squaredDistanceTo(camera)>4096d*4096d){runtime.clear();stopInstances();origin=null;lastSimulationTime=Double.NaN;}
            var depthBackend=backends.get("claude");
            if(depthBackend instanceof dev.portablevfx.client.claude.ClaudeBackend claudeDepth&&!failedBackends.contains("claude")) {
                var depthTarget=MinecraftClient.getInstance().getFramebuffer();
                claudeDepth.prewarmWorldDepth(depthTarget.fbo,depthTarget.textureWidth,depthTarget.textureHeight);
            }
            var active=runtime.snapshot();if(active.isEmpty()&&instances.isEmpty()){lastSimulationTime=Double.NaN;return;}
            if(origin==null)origin=camera;
            var current=new LinkedHashMap<UUID,EffectRuntime.ActiveEffect>();for(var a:active)current.put(a.request.instanceId(),a);
            instances.entrySet().removeIf(e->{var old=e.getValue();if(current.get(e.getKey())!=old.active()){old.handle().stop();return true;}
                if(!old.handle().exists()){runtime.stop(e.getKey());current.remove(e.getKey());return true;}return false;});
            Set<Identifier> ready=readyEffectIds();
            for(var a:current.values()){
                UUID id=a.request.instanceId();if(instances.containsKey(id))continue;
                Identifier asset=Identifier.of(a.definition.effect());if(failedAssets.contains(asset)){runtime.stop(id);continue;}
                Identifier key=Identifier.of(a.request.effectId());
                try{
                    var effect=loaded.get(key);
                    if(effect==null||!ready.contains(key)){
                        // Casting never performs dependency reads, decode, GPU uploads or eviction.
                        // A low limit leaves the ID available but not ready until a successful reload.
                        runtime.stop(id);runtime.status("VFX not ready: "+key+" was not preloaded; reload resources after resolving loading limits/errors");continue;
                    }
                    var handle=effect.backend().play(effect.asset(),a.request.seed());
                    if(handle==null){runtime.stop(id);runtime.status("Effect scene/particle capacity exhausted");continue;}
                    a.renderStarted=true;
                    instances.put(id,new Instance(a,handle));
                }catch(IllegalArgumentException|LinkageError e){dropReady(key);ready=readyEffectIds();failedAssets.add(asset);runtime.stop(id);PortableVfxClient.LOG.error("VFX asset rejected {}: {}",asset,e.getMessage());runtime.status("Asset failed: "+asset+" (see latest.log)");}
            }
            if(!MinecraftClient.getInstance().isPaused())frozenTickDelta=context.tickCounter().getTickDelta(false);
            float tickDelta=frozenTickDelta;
            var iterator=instances.entrySet().iterator();while(iterator.hasNext()){
                var entry=iterator.next();var a=entry.getValue().active();var r=a.request;var handle=entry.getValue().handle();
                Vec3d position=runtime.position(a,tickDelta);
                if(position==null && a.attached) { runtime.finish(entry.getKey());position=runtime.position(a,tickDelta); }
                if(position==null){handle.stop();runtime.stop(entry.getKey());iterator.remove();continue;}
                try {
                handle.opacity(runtime.opacity(a));
                handle.worldOrigin(origin.x,origin.y,origin.z);
                handle.sceneTime((runtime.simulationTicks()+tickDelta)/20.0);
                handle.transform((float)(position.x-origin.x),(float)(position.y-origin.y),(float)(position.z-origin.z),a.orientation.nativePitch(),a.orientation.nativeYaw(),a.orientation.nativeRoll(),r.scale());
                float[] basis=runtime.renderBasis(a,tickDelta);
                if (basis != null) handle.basis(basis);
                if (a.effectWidth>0) handle.effectWidth(a.effectWidth);
                handle.parameters(a.scaleInput,a.linkLength);
                handle.seek((a.age+tickDelta)/20f);
                if (a.finishing) {
                    if(a.durationExpired)handle.expireEmissionAt(a.finishAge/20f);
                    else handle.finishEmissionAt(a.finishAge/20f,a.finishClearLocal);
                }
                } catch(RuntimeException error) {
                    handle.stop();runtime.stop(entry.getKey());iterator.remove();
                    runtime.status("Effect update rejected: "+r.effectId()+" (see latest.log)");PortableVfxClient.LOG.error(runtime.status(),error);
                }
            }
            double simulationTime=runtime.simulationTicks()+tickDelta;
            float seconds=Double.isNaN(lastSimulationTime)?0:(float)Math.max(0,Math.min(.25,(simulationTime-lastSimulationTime)/20));lastSimulationTime=simulationTime;
            if(!MinecraftClient.getInstance().isPaused())for(var backend:backends.values()) {
                if(failedBackends.contains(backend.id()))continue;
                try { backend.update(seconds); }
                catch(RuntimeException|LinkageError error) {
                    // Simulation is CPU-only: stop live instances, keep the backend and its GPU cache usable.
                    if(gpuFailure(error))failedBackends.add(backend.id());backend.stopAll();
                    runtime.status("Backend simulation failed: "+backend.id()+" (see latest.log)");
                    PortableVfxClient.LOG.error(runtime.status(),error);
                }
            }
            Matrix4f view=new Matrix4f(context.positionMatrix()).translate((float)(origin.x-camera.x),(float)(origin.y-camera.y),(float)(origin.z-camera.z));
            float[] v=view.get(new float[16]),p=context.projectionMatrix().get(new float[16]);var front=context.camera().getHorizontalPlane();var target=MinecraftClient.getInstance().getFramebuffer();
            try(var pass=WorldTargetPass.begin(target.fbo,target.textureWidth,target.textureHeight)){
                var claude = backends.get("claude");
                if (claude != null && !failedBackends.contains("claude")) {
                    try { claude.draw(v,p,front.x(),front.y(),front.z(),(float)(camera.x-origin.x),(float)(camera.y-origin.y),(float)(camera.z-origin.z)); }
                    catch (RuntimeException|LinkageError error) { if(gpuFailure(error))failedBackends.add("claude");claude.stopAll();runtime.status("Claude draw failed (see latest.log)");PortableVfxClient.LOG.error(runtime.status(),error); }
                }
            }
        }catch(RuntimeException|LinkageError e){
            runtime.status("Renderer frame failed: "+e.getClass().getSimpleName()+": "+e.getMessage());PortableVfxClient.LOG.error(runtime.status(),e);runtime.clear();
            // Only a real GL failure invalidates uploaded assets; otherwise keep the GPU cache and readiness.
            if(gpuFailure(e))clearRendering();else stopInstances();
        }
    }
    /** Budget, per-asset and simulation errors are recoverable; only GL/link failures (or a broken native binding) disable rendering. */
    static boolean gpuFailure(Throwable error){return error instanceof dev.portablevfx.client.render.gl.GpuFailureException||error instanceof LinkageError;}
    /** Removes only the rejected effect and effect IDs sharing its installed asset; other ready IDs keep being advertised. */
    private void dropReady(Identifier key){
        residentFingerprint=null;var failed=loaded.get(key);var next=new LinkedHashSet<>(gpuReadyIds);next.remove(key);
        if(failed!=null)for(var entry:loaded.entrySet())if(entry.getValue().asset()==failed.asset())next.remove(entry.getKey());
        gpuReadyIds=Set.copyOf(next);
    }
    public void synchronizeLifecycle(){if(revision!=runtime.revision()){stopInstances();revision=runtime.revision();}}
    private void stopInstances(){for(var backend:backends.values())backend.stopAll();instances.clear();origin=null;lastSimulationTime=Double.NaN;frozenTickDelta=0;}
    private void clearRendering(){residentFingerprint=null;gpuReadyIds=Set.of();stopInstances();for(var effect:loaded.values())effect.asset().close();loaded.clear();failedAssets.clear();}
    private void disposeRendering(){
        residentFingerprint=null;
        for(var backend:backends.values())if(backend instanceof dev.portablevfx.client.claude.ClaudeBackend c)c.prepareContextDisposal();
        clearRendering();for(var backend:backends.values())backend.close();backends.clear();failedBackends.clear();
    }
    @Override public void close(){warmupEpoch.incrementAndGet();disposeRendering();}
}
