package dev.portablevfx.client.definition;

import dev.portablevfx.client.PortableVfxClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Bounded immutable resource snapshots. Native code is never loaded from a resource pack. */
public final class EffectLibrary implements SimpleResourceReloadListener<EffectLibrary.ReloadData> {
    public static final int MAX_ASSET_BYTES = 16 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 128 * 1024 * 1024;
    /** Independent config quota: bundled/resource-pack textures must not consume it.
     * Each side is <=64 MiB decoded RGBA (128 MiB combined), plus backend-specific GPU caps. */
    public static final long MAX_CONFIG_TEXELS = TextureBudget.MAX_TOTAL_TEXELS;
    private volatile LoadLimits limits = LoadLimits.DEFAULT;
    public LoadLimits limits() { return limits; }
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), 0);
    private final Runnable afterReload;
    private final java.nio.file.Path configEffectsRoot;
    private volatile Map<Identifier,String> claudeRoles = Map.of();
    public String claudeRole(Identifier id) { return claudeRoles.get(id); }
    private volatile java.util.List<String> configErrors = java.util.List.of();
    public java.util.List<String> configErrors() { return configErrors; }
    /** CPU preparation happens off-thread; GPU apply belongs to the resource-loading game thread. */
    public interface Warmup {
        Object prepare(Snapshot snapshot);
        CompletableFuture<Void> apply(Snapshot snapshot, Object prepared, Executor executor);
    }
    private Warmup warmup;
    private final Object ownershipLock=new Object();
    private final Object preparationLock=new Object();
    private ReloadData unclaimed;
    private long loadRequest;
    private boolean stopped;
    private static void discard(ReloadData data){
        if(data!=null&&data.prepared() instanceof AutoCloseable owned)try{owned.close();}
        catch(Exception error){PortableVfxClient.LOG.warn("VFX cancelled preparation cleanup failed",error);}
    }
    public void close(){
        ReloadData pending;synchronized(ownershipLock){stopped=true;loadRequest++;pending=unclaimed;unclaimed=null;}discard(pending);
    }
    public void warmup(Warmup warmup) { this.warmup = java.util.Objects.requireNonNull(warmup); }
    public record ReloadData(Snapshot snapshot, Map<Identifier,String> roles,
                             java.util.List<String> errors, Object prepared) { }
    public record Snapshot(Map<Identifier, EffectDefinition> definitions, Map<Identifier, byte[]> assets, long generation, boolean valid) {
        public Snapshot(Map<Identifier,EffectDefinition> definitions,Map<Identifier,byte[]> assets,long generation){this(definitions,assets,generation,true);}
        public byte[] read(Identifier id) throws IOException {
            byte[] data = assets.get(id);
            if (data == null) throw new IOException("Missing resource: " + id);
            return data.clone();
        }
        public byte[] dependency(Identifier effect, String ref) throws IOException {
            try { return read(Identifier.of(effect.getNamespace(), AssetPath.dependency(effect.getPath(), ref))); }
            catch (IllegalArgumentException ex) { throw new IOException("Rejected dependency: " + ref, ex); }
        }
    }
    public EffectLibrary(Runnable afterReload) {
        this(afterReload, net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("portablevfx/effects"));
    }
    /** Explicit root enables integration tests without a running Fabric loader. */
    public EffectLibrary(Runnable afterReload, java.nio.file.Path configEffectsRoot) {
        this.afterReload = java.util.Objects.requireNonNull(afterReload);
        this.configEffectsRoot = java.util.Objects.requireNonNull(configEffectsRoot);
    }
    @Override public Identifier getFabricId() { return Identifier.of("portablevfx", "effect_definitions"); }
    @Override public CompletableFuture<ReloadData> load(ResourceManager manager, Executor executor) {
        var result=new CompletableFuture<ReloadData>();final long request;ReloadData old;
        synchronized(ownershipLock){if(stopped)return CompletableFuture.failedFuture(new IllegalStateException("VFX library closed"));request=++loadRequest;old=unclaimed;unclaimed=null;}
        discard(old);
        try{executor.execute(()->{
            ReloadData data=null;
            try{
                synchronized(preparationLock){
                    synchronized(ownershipLock){if(stopped||request!=loadRequest){result.cancel(false);return;}}
                    data=prepareResources(manager);
                }
                synchronized(ownershipLock){
                    if(stopped||request!=loadRequest){discard(data);result.cancel(false);return;}
                    unclaimed=data;
                    if(!result.complete(data)){unclaimed=null;discard(data);}
                }
            }catch(Throwable error){discard(data);result.completeExceptionally(error);}
        });}catch(Throwable error){result.completeExceptionally(error);}
        return result;
    }
    @Override public CompletableFuture<Void> apply(ReloadData data, ResourceManager manager, Executor executor) {
        synchronized(ownershipLock){if(unclaimed==data)unclaimed=null;}
        return CompletableFuture.runAsync(() -> {
            claudeRoles = data.roles(); configErrors = data.errors(); snapshot = data.snapshot();
            afterReload.run();
        }, executor).thenCompose(ignored -> warmup == null ? CompletableFuture.completedFuture(null)
                : warmup.apply(data.snapshot(), data.prepared(), executor));
    }
    /** Synchronous test/tool compatibility; production uses Minecraft's two-stage reload. */
    public void reload(ResourceManager manager) { apply(prepareResources(manager), manager, Runnable::run).join(); }
    private ReloadData prepareResources(ResourceManager manager) {
        Map<Identifier, byte[]> assets = new TreeMap<>();
        Map<Identifier, EffectDefinition> loaded = new TreeMap<>();
        var errors = new java.util.ArrayList<String>();
        try { limits = LoadLimits.load(configEffectsRoot.resolveSibling("load-limits.properties")); }
        catch(IOException error) { limits = LoadLimits.DEFAULT; errors.add(error.getMessage()+"; using safe defaults"); }
        var config = dev.portablevfx.client.claude.ClaudeConfigLoader.load(configEffectsRoot, limits);
        errors.addAll(config.errors());
        config.warnings().forEach(warning -> PortableVfxClient.LOG.warn("Claude compatibility warning: {}",warning));
        Map<Identifier,String> roles = new TreeMap<>();
        config.assets().forEach((path, data) -> assets.put(Identifier.of("claude", path), data));
        config.definitions().forEach(d -> loaded.put(Identifier.of(d.id()), new EffectDefinition(d.asset(), d.durationTicks(), 1, "claude")));
        config.definitions().forEach(d -> roles.put(Identifier.of(d.id()), d.role()));
        long bytes = config.assets().values().stream().mapToLong(a -> a.length).sum();
        errors.forEach(error -> PortableVfxClient.LOG.warn("Claude config: {}", error));
        boolean same=loaded.equals(snapshot.definitions())&&assets.keySet().equals(snapshot.assets().keySet())
                &&assets.entrySet().stream().allMatch(e->java.util.Arrays.equals(e.getValue(),snapshot.assets().get(e.getKey())));
        Snapshot next=new Snapshot(same?snapshot.definitions():Map.copyOf(loaded),same?snapshot.assets():Map.copyOf(assets),snapshot.generation()+1,errors.isEmpty());
        Object prepared = warmup == null ? null : warmup.prepare(next);
        PortableVfxClient.LOG.info("Loaded {} VFX definitions, {} resources, {} bytes ({} invalid)", loaded.size(), assets.size(), bytes, errors.size());
        return new ReloadData(next, Map.copyOf(roles), java.util.List.copyOf(errors), prepared);
    }
    public Snapshot snapshot() { return snapshot; }
    public EffectDefinition get(Identifier id) { return snapshot.definitions().get(id); }
    public Set<Identifier> ids() { return snapshot.definitions().keySet(); }
}
