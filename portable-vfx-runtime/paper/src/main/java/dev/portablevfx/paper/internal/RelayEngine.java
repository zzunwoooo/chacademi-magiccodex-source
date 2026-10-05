package dev.portablevfx.paper.internal;

import dev.portablevfx.paper.api.PlayResult;
import dev.portablevfx.paper.api.VfxStatus;
import dev.portablevfx.protocol.ClearEffects;
import dev.portablevfx.protocol.EffectMessage;
import dev.portablevfx.protocol.EffectBasis;
import dev.portablevfx.protocol.PoseEffect;
import dev.portablevfx.protocol.ImpactEffect;
import dev.portablevfx.protocol.FinishEffect;
import dev.portablevfx.protocol.PlayEffect;
import dev.portablevfx.protocol.OrientEffect;
import dev.portablevfx.protocol.ProtocolException;
import dev.portablevfx.protocol.StopEffect;
import dev.portablevfx.protocol.VfxProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/** Main-thread-only relay state machine, kept independent of Bukkit for deterministic tests. */
public final class RelayEngine {
    public interface Transport {
        long currentTick();
        /** Absolute world game time (not daylight time), or -1 if unavailable. */
        default long worldTick(String dimensionId) { return -1; }
        Collection<Viewer> viewers(String dimensionId);
        Viewer viewer(UUID id);
        boolean send(UUID playerId, byte[] payload);
    }

    public record Viewer(UUID id, String dimensionId, double x, double y, double z,
                         boolean registeredChannel) {}

    private static final class Active {
        long expiresAt; final Set<UUID> recipients;
        final String dimensionId; final double originX, originY, originZ, radius;
        final boolean authoritative; boolean finished;
        long orientationTick = Long.MIN_VALUE, poseTick = Long.MIN_VALUE, sequence;
        Active(long expiresAt, Set<UUID> recipients, PlayEffect start, double radius, boolean authoritative) {
            this.expiresAt = expiresAt; this.recipients = recipients;
            dimensionId = start.dimensionId(); originX = start.x(); originY = start.y(); originZ = start.z();
            this.radius = radius; this.authoritative = authoritative;
        }
        long expiresAt(){return expiresAt;} Set<UUID> recipients(){return recipients;}
    }
    private static final class Pending {
        boolean clear;
        final Map<UUID, EffectMessage> endings = new LinkedHashMap<>();
    }

    private final Transport transport;
    private final SendBudget budget = new SendBudget();
    private final Set<UUID> compatible = new HashSet<>();
    private final Set<UUID> orientationCompatible = new HashSet<>();
    private final Set<UUID> extendedPlayCompatible = new HashSet<>();
    private final Set<UUID> authoritativeCompatible = new HashSet<>();
    private final Set<UUID> widthPlayCompatible = new HashSet<>();
    private final Set<UUID> stopIntentCompatible = new HashSet<>();
    private final Map<UUID,Set<String>> readyEffects=new HashMap<>();
    private final Map<UUID,String> readinessRejection=new HashMap<>();
    private Set<String> catalogRequirements=Set.of();
    public static final double MAX_CAST_DISTANCE = 256;
    public static final int MAX_FINISH_TRACK_TICKS = 1200;
    private final Map<UUID, Active> active = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private RelayLimits limits;
    private long packetsSent;
    private long rejectedHellos;
    /** Viewer -> cast handle -> {first skipped tick, last skipped tick}; stalest cast is served first. */
    private final Map<UUID, Map<UUID, long[]>> starvedPoses = new HashMap<>();
    /** Hellos that arrived before the transport could resolve the player; replayed in arrival order. */
    private final Map<UUID, DeferredHello> deferredHellos = new LinkedHashMap<>();
    public static final int DEFERRED_HELLO_TICKS = 200, MAX_DEFERRED_HELLO_PLAYERS = 1024;
    private record DeferredHello(long since, Map<String, byte[]> messages) {}

    public RelayEngine(Transport transport, RelayLimits limits) {
        this.transport = transport;
        this.limits = limits;
    }

    public RelayLimits limits() { return limits; }

    public void hello(UUID player, byte[] payload) {
        if (transport.viewer(player) == null) { defer(player, "hello", payload); return; }
        try {
            VfxProtocol.decodeHello(payload);
            compatible.add(player);
        } catch (ProtocolException | IllegalArgumentException e) {
            rejectedHellos++;
            forget(player);
        }
    }

    /** An exact bounded capability announcement, accepted only after normal hello. */
    public void orientationHello(UUID player, byte[] payload) {
        if (!compatible.contains(player)) { deferAfterBase(player, "orientation", payload); return; }
        try { VfxProtocol.decodeHello(payload); orientationCompatible.add(player); }
        catch (ProtocolException | IllegalArgumentException e) { orientationCompatible.remove(player); }
    }

    /** Independent capability, accepted only after the ordinary compatible hello. */
    public void extendedPlayHello(UUID player, byte[] payload) {
        if (!compatible.contains(player)) { deferAfterBase(player, "extended", payload); return; }
        try { VfxProtocol.decodeHello(payload); extendedPlayCompatible.add(player); }
        catch (ProtocolException | IllegalArgumentException e) { extendedPlayCompatible.remove(player); }
    }

    /** Independent, exact capability: a base hello is required and no client poses are accepted. */
    public void authoritativeHello(UUID player, byte[] payload) {
        if (!compatible.contains(player)) { deferAfterBase(player, "authoritative", payload); return; }
        try { VfxProtocol.decodeHello(payload); authoritativeCompatible.add(player); }
        catch (ProtocolException | IllegalArgumentException e) { authoritativeCompatible.remove(player); }
    }

    /** Explicit runtime width has a separate capability; never approximate it with uniform scale. */
    public void widthPlayHello(UUID player, byte[] payload) {
        if (!compatible.contains(player)) { deferAfterBase(player, "width", payload); return; }
        try { VfxProtocol.decodeHello(payload); widthPlayCompatible.add(player); }
        catch (ProtocolException | IllegalArgumentException e) { widthPlayCompatible.remove(player); }
    }

    public void stopIntentHello(UUID player, byte[] payload) {
        if(!compatible.contains(player)){deferAfterBase(player,"stop",payload);return;}
        try { VfxProtocol.decodeHello(payload);stopIntentCompatible.add(player); }
        catch(ProtocolException|IllegalArgumentException error){stopIntentCompatible.remove(player);}
    }
    public boolean supportsStopIntent(UUID player){ return stopIntentCompatible.contains(player); }

    /**
     * Duplicate hellos are idempotent: re-announcing after a plugin re-enable or a client retry
     * never resets active casts, budgets or readiness that is re-sent alongside it.
     */
    private void defer(UUID player, String kind, byte[] payload) {
        if (payload == null || payload.length > dev.portablevfx.protocol.CatalogReadiness.MAX_BYTES) return;
        DeferredHello deferred = deferredHellos.get(player);
        if (deferred == null) {
            if (deferredHellos.size() >= MAX_DEFERRED_HELLO_PLAYERS) return;
            deferred = new DeferredHello(transport.currentTick(), new LinkedHashMap<>());
            deferredHellos.put(player, deferred);
        }
        // Latest payload per kind; insertion order keeps the base hello first.
        deferred.messages().put(kind, payload.clone());
    }

    /** Capability hellos that race behind a deferred base hello are kept with it, never dropped. */
    private boolean deferAfterBase(UUID player, String kind, byte[] payload) {
        if (!deferredHellos.containsKey(player)) return false;
        defer(player, kind, payload);
        return true;
    }

    private void replayDeferredHellos() {
        if (deferredHellos.isEmpty()) return;
        long now = transport.currentTick();
        for (UUID player : new ArrayList<>(deferredHellos.keySet())) {
            DeferredHello deferred = deferredHellos.get(player);
            if (transport.viewer(player) == null) {
                if (now - deferred.since() > DEFERRED_HELLO_TICKS || now < deferred.since()) deferredHellos.remove(player);
                continue;
            }
            deferredHellos.remove(player);
            for (var message : deferred.messages().entrySet()) {
                byte[] payload = message.getValue();
                switch (message.getKey()) {
                    case "hello" -> hello(player, payload);
                    case "orientation" -> orientationHello(player, payload);
                    case "extended" -> extendedPlayHello(player, payload);
                    case "authoritative" -> authoritativeHello(player, payload);
                    case "width" -> widthPlayHello(player, payload);
                    case "stop" -> stopIntentHello(player, payload);
                    case "ready" -> catalogReady(player, payload);
                    default -> { }
                }
            }
        }
    }

    int deferredHelloPlayers() { return deferredHellos.size(); }
    public void catalogRequirements(Set<String> ids){catalogRequirements=Set.copyOf(ids);}
    public void catalogReady(UUID player,byte[] payload){
        if(!compatible.contains(player)&&deferAfterBase(player,"ready",payload))return;
        if(!compatible.contains(player)||!stopIntentCompatible.contains(player)){
            readinessRejection.put(player,"ready received before base/stop hello");return;
        }
        try {readyEffects.put(player,dev.portablevfx.protocol.CatalogReadiness.decode(payload));readinessRejection.remove(player);}
        catch(ProtocolException|IllegalArgumentException error){readyEffects.remove(player);readinessRejection.put(player,error.getMessage());}
    }
    public boolean supportsCatalogCast(UUID player){
        Set<String> ids=readyEffects.get(player);return stopIntentCompatible.contains(player)&&ids!=null&&!ids.isEmpty()&&ids.containsAll(catalogRequirements);
    }
    /** Read-only operator diagnostic: distinguishes transport, withdrawn readiness and mismatched packs. */
    public String catalogDiagnostic(UUID player){
        Set<String> ids=readyEffects.get(player);
        var missing=catalogRequirements.stream().filter(id->ids==null||!ids.contains(id)).sorted().toList();
        return "base="+compatible.contains(player)+", stop="+stopIntentCompatible.contains(player)
                +", received="+(ids==null?"none":ids.size())+", required="+catalogRequirements.size()
                +", missing="+missing.size()+", ready="+supportsCatalogCast(player)
                +(missing.isEmpty()?"":", sample="+missing.stream().limit(3).toList())
                +(readinessRejection.containsKey(player)?", rejected="+readinessRejection.get(player):"");
    }


    public int widthPlayClients() {
        return (int) widthPlayCompatible.stream().filter(id -> eligible(transport.viewer(id))).count();
    }

    public int authoritativeClients() {
        return (int) authoritativeCompatible.stream().filter(id -> eligible(transport.viewer(id))).count();
    }

    /** PLAY plus initial server basis are sent together within one shared packet reservation. */
    public PlayResult startCast(PlayEffect effect, double requestedRadius, EffectBasis basis) {
        requireClaudeCast(effect);
        java.util.Objects.requireNonNull(basis, "basis");
        return play(effect, requestedRadius, basis);
    }

    public PlayResult startCatalogCast(PlayEffect effect,double radius,EffectBasis basis) {
        requireClaudeCast(effect);java.util.Objects.requireNonNull(basis,"basis");return play(effect,radius,basis,true);
    }

    /** One current server pose per cast/tick. Never extends TTL or queues stale positions. */
    public int updateCast(UUID handle, double x, double y, double z, EffectBasis basis) { return updateCast(handle,x,y,z,basis,-1); }
    public int updateCast(UUID handle, double x, double y, double z, EffectBasis basis,double linkLength) {
        sync();
        Active effect = active.get(handle);
        if (effect == null || !effect.authoritative || effect.finished) return 0;
        PoseEffect pose = new PoseEffect(handle, effect.dimensionId, effect.sequence, x, y, z, basis,linkLength);
        requireCastDistance(effect, x, y, z);
        long now = transport.currentTick();
        if (effect.poseTick == now) return 0;
        effect.poseTick = now;
        effect.sequence++;
        flushControls();
        // Opcode 13 is only defined for stop_intent clients; others get the opcode 7 body (no link length).
        byte[] linkEncoded = linkLength >= 0 ? VfxProtocol.encode(pose) : null;
        byte[] plainEncoded = linkLength >= 0
                ? VfxProtocol.encode(new PoseEffect(handle, effect.dimensionId, pose.sequence(), x, y, z, basis)) : VfxProtocol.encode(pose);
        return sendPose(handle, effect, linkEncoded, plainEncoded);
    }

    /** Atomic terminal flight-to-impact event. There is no local client collision prediction. */
    public PlayResult impactCast(UUID handle, PlayEffect impact, EffectBasis basis) {
        requireClaudeCast(impact);
        sync();
        Active effect = active.get(handle);
        if (effect == null || !effect.authoritative || effect.finished) return new PlayResult(impact.instanceId(), 0, 0, 0);
        if (!effect.dimensionId.equals(impact.dimensionId())) throw new IllegalArgumentException("Impact world mismatch");
        if (impact.followEntity() != null) throw new IllegalArgumentException("Impact must use a server world position");
        if (impact.durationTicks() > limits.maxDurationTicks()) throw new IllegalArgumentException("Impact duration exceeds configured maximum");
        if (active.size() >= limits.maxActiveHandles()) throw new RejectedExecutionException("active handle limit reached before impact");
        if (active.containsKey(impact.instanceId())) throw new IllegalArgumentException("Impact UUID is already active");
        requireCastDistance(effect, impact.x(), impact.y(), impact.z());
        ImpactEffect event = new ImpactEffect(handle, effect.sequence++, impact, basis);
        if (!budget.takePlay(limits)) throw new RejectedExecutionException("per-tick play request limit reached");
        flushControls();
        byte[] encoded = VfxProtocol.encode(event);
        Set<UUID> recipients = new LinkedHashSet<>();
        int skipped = 0;
        for (UUID player : effect.recipients()) {
            if (!eligibleCastRecipient(player, effect.dimensionId)) continue;
            if (((impact.parameterized()||impact.durationTicks()>1200)&&!stopIntentCompatible.contains(player)) || (impact.effectWidth() != 0 && !widthPlayCompatible.contains(player))
                    || pending.containsKey(player) || !budget.takeViewerPlay(player, limits, 1)) {
                skipped++; queueEnding(player, handle, new FinishEffect(handle, effect.dimensionId, effect.sequence));
                continue;
            }
            if (transport.send(player, encoded)) { recipients.add(player); packetsSent++; }
            // A client's bounded spawn queue may discard IMPACT while retaining control priority.
            // Redundant newer FINISH is ignored after accepted impact, but stops flight after a drop.
            queueEnding(player, handle, new FinishEffect(handle, effect.dimensionId, effect.sequence));
        }
        effect.finished = true;
        effect.expiresAt = transport.currentTick() + MAX_FINISH_TRACK_TICKS;
        if (!recipients.isEmpty()) active.put(impact.instanceId(), new Active(
                transport.currentTick() + impact.durationTicks(), recipients, impact, effect.radius, false));
        flushControls();
        return new PlayResult(impact.instanceId(), recipients.size(), skipped, effect.radius);
    }

    /** Finish without an impact. End messages are queued ahead of new plays if budget is exhausted. */
    public boolean finishCast(UUID handle) { return finishCast(handle,false); }
    public boolean finishCast(UUID handle,boolean clearLocal) {
        sync();
        Active effect = active.get(handle);
        if (effect == null || !effect.authoritative || effect.finished) return false;
        effect.finished = true;
        effect.expiresAt = transport.currentTick() + MAX_FINISH_TRACK_TICKS;
        long sequence=effect.sequence++;
        for (UUID player : effect.recipients()) queueEnding(player, handle, new FinishEffect(handle,effect.dimensionId,sequence,clearLocal||!stopIntentCompatible.contains(player)));
        flushControls();
        return true;
    }

    public boolean isCastActive(UUID handle) {
        sync(); Active value = active.get(handle); return value != null && value.authoritative && !value.finished;
    }

    public void worldUnloaded(String dimensionId) {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Active value = active.get(id);
            if (value != null && value.dimensionId.equals(dimensionId)) stop(id);
        }
    }

    private static void requireClaudeCast(PlayEffect effect) {
        if (!effect.effectId().startsWith("claude:"))
            throw new IllegalArgumentException("Authoritative casts require a claude: config effect ID");
        if(effect.followEntity()!=null&&(effect.anchor()!=dev.portablevfx.protocol.EffectAnchor.ENTITY||effect.offsetX()!=0||effect.offsetY()!=0||effect.offsetZ()!=0))throw new IllegalArgumentException("Controlled follow requires entity feet");
        if (effect.scale() != 1f || effect.rgb() != 0xffffff || effect.opacity() != 1f)
            throw new IllegalArgumentException("Claude casts require scale=1, rgb=FFFFFF, opacity=1; author visuals in config");
    }

    private static void requireCastDistance(Active effect, double x, double y, double z) {
        double dx = x - effect.originX, dy = y - effect.originY, dz = z - effect.originZ;
        if (!Double.isFinite(dx * dx + dy * dy + dz * dz)
                || dx * dx + dy * dy + dz * dz > MAX_CAST_DISTANCE * MAX_CAST_DISTANCE)
            throw new IllegalArgumentException("Cast pose/impact must remain within 256 blocks of its start");
    }

    private boolean eligibleCastRecipient(UUID player, String dimensionId) {
        Viewer viewer = transport.viewer(player);
        return authoritativeCompatible.contains(player) && eligible(viewer) && dimensionId.equals(viewer.dimensionId());
    }

    /**
     * Per-viewer pose coalescing: samples are never queued or replayed. When a viewer's stream
     * budget is exhausted the cast is marked starved; next tick starved casts (stalest first)
     * keep a reserved slot ahead of casts that were delivered, so one busy viewer sees every
     * cast advance round-robin instead of the first-updated casts winning every tick.
     */
    private int sendPose(UUID handle, Active effect, byte[] linkEncoded, byte[] plainEncoded) {
        long now = transport.currentTick();
        int sent = 0;
        for (UUID player : effect.recipients()) {
            if (!eligibleCastRecipient(player, effect.dimensionId) || pending.containsKey(player)) continue;
            Map<UUID, long[]> starved = starvedPoses.get(player);
            long[] mine = starved == null ? null : starved.get(handle);
            int staler = 0;
            if (starved != null) for (var entry : starved.entrySet()) {
                if (entry.getKey().equals(handle) || (mine != null && entry.getValue()[0] >= mine[0])) continue;
                Active other = active.get(entry.getKey());
                if (other != null && other.authoritative && !other.finished && other.poseTick != now) staler++;
            }
            if (budget.streamRemaining(player, limits) > staler && budget.takeStream(player, limits)) {
                if (starved != null) { starved.remove(handle); if (starved.isEmpty()) starvedPoses.remove(player); }
                byte[] encoded = linkEncoded != null && stopIntentCompatible.contains(player) ? linkEncoded : plainEncoded;
                if (transport.send(player, encoded)) { packetsSent++; sent++; }
            } else {
                starvedPoses.computeIfAbsent(player, ignored -> new LinkedHashMap<>())
                        .merge(handle, new long[] {now, now}, (old, ignored) -> { old[1] = now; return old; });
            }
        }
        return sent;
    }

    private void purgeStarvedPoses(long now) {
        if (starvedPoses.isEmpty()) return;
        starvedPoses.values().removeIf(starved -> {
            starved.entrySet().removeIf(entry -> {
                Active cast = active.get(entry.getKey());
                return entry.getValue()[1] < now - 1 || cast == null || !cast.authoritative || cast.finished;
            });
            return starved.isEmpty();
        });
    }

    int starvedPoseViewers() { return starvedPoses.size(); }

    /** Once per handle/tick, best effort to original capable recipients using shared packet budgets.
     * Return delivered count; zero also covers stopped, expired, unsupported or exhausted cases.
     * Caller should send current orientation next tick, never replay old queued updates. */
    public int orient(UUID handle, float yaw, float pitch, float roll) {
        sync();
        new OrientEffect(handle,0,yaw,pitch,roll); // validate even if the handle is absent
        Active effect=active.get(handle);
        long now=transport.currentTick();
        if(effect==null || effect.authoritative || effect.orientationTick==now) return 0;
        effect.orientationTick=now;
        flushControls();
        byte[] encoded=VfxProtocol.encode(new OrientEffect(handle,effect.sequence++,yaw,pitch,roll));
        int sent=0;
        for(UUID recipient:effect.recipients()) {
            if(!orientationCompatible.contains(recipient)||!eligible(transport.viewer(recipient))||pending.containsKey(recipient))continue;
            if(!budget.takeStream(recipient,limits))continue;
            if(transport.send(recipient,encoded)){packetsSent++;sent++;}
        }
        return sent;
    }

    public PlayResult play(PlayEffect effect, double requestedRadius) {
        return play(effect, requestedRadius, null);
    }

    private PlayResult play(PlayEffect effect,double requestedRadius,EffectBasis initialBasis) { return play(effect,requestedRadius,initialBasis,false); }
    private PlayResult play(PlayEffect effect, double requestedRadius, EffectBasis initialBasis,boolean catalog) {
        sync();
        if (!Double.isFinite(requestedRadius) || requestedRadius <= 0 || requestedRadius > 256) {
            throw new IllegalArgumentException("radius must be finite and in (0, 256]");
        }
        if (effect.durationTicks() > limits.maxDurationTicks()) {
            throw new IllegalArgumentException("durationTicks exceeds configured maximum " + limits.maxDurationTicks());
        }
        if (active.containsKey(effect.instanceId())) {
            throw new IllegalArgumentException("effect instance UUID is already active");
        }
        if (!budget.takePlay(limits)) throw new RejectedExecutionException("per-tick play request limit reached");
        if (active.size() >= limits.maxActiveHandles()) {
            throw new RejectedExecutionException("active handle limit reached; stop or clear effects first");
        }
        flushControls();
        double radius = Math.min(requestedRadius, limits.maxRadius());
        double radiusSquared = radius * radius;
        byte[] encoded = VfxProtocol.encode(effect);
        long worldTick = transport.worldTick(effect.dimensionId());
        PlayEffect synchronizedEffect = effect.startTick() < 0 && worldTick >= 0 ? effect.withStartTick(worldTick) : effect;
        byte[] extendedEncoded = VfxProtocol.encode(synchronizedEffect);
        byte[] poseEncoded = initialBasis == null ? null : VfxProtocol.encode(new PoseEffect(effect.instanceId(),
                effect.dimensionId(), 0, effect.x(), effect.y(), effect.z(), initialBasis));
        long elapsed = effect.startTick() < 0 || worldTick < 0 ? 0 : Math.max(0, worldTick - effect.startTick());
        int remainingTicks = (int) Math.max(0, effect.durationTicks() - Math.min(effect.durationTicks(), elapsed));
        if (remainingTicks == 0) return new PlayResult(effect.instanceId(), 0, 0, radius);
        Set<UUID> recipients = new LinkedHashSet<>();
        int skipped = 0;
        for (Viewer viewer : transport.viewers(effect.dimensionId())) {
            if (!eligible(viewer) || !effect.dimensionId().equals(viewer.dimensionId())) continue;
            if(catalog&&!supportsCatalogCast(viewer.id()))continue;
            boolean extendedClient = extendedPlayCompatible.contains(viewer.id());
            if (initialBasis != null && (!authoritativeCompatible.contains(viewer.id()) || !extendedClient)) continue;
            if (effect.effectWidth() != 0 && !widthPlayCompatible.contains(viewer.id())) continue;
            if((effect.parameterized()||effect.durationTicks()>1200)&&!stopIntentCompatible.contains(viewer.id()))continue;
            // Explicit extended requests have no silent approximation on legacy clients.
            if (effect.extended() && !extendedClient) continue;
            double dx = viewer.x() - effect.x();
            double dy = viewer.y() - effect.y();
            double dz = viewer.z() - effect.z();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (!Double.isFinite(distanceSquared) || distanceSquared > radiusSquared) continue;
            if (pending.containsKey(viewer.id()) || !budget.takeViewerPlay(viewer.id(), limits, initialBasis == null ? 1 : 2)) {
                skipped++;
                continue;
            }
            if (transport.send(viewer.id(), extendedClient ? extendedEncoded : encoded)) {
                recipients.add(viewer.id());
                packetsSent++;
                if (poseEncoded != null && transport.send(viewer.id(), poseEncoded)) packetsSent++;
            }
        }
        if (!recipients.isEmpty()) {
            Active started = new Active(transport.currentTick() + remainingTicks, recipients, effect, radius, initialBasis != null);
            if (initialBasis != null) { started.sequence = 1; started.poseTick = transport.currentTick(); }
            active.put(effect.instanceId(), started);
        }
        return new PlayResult(effect.instanceId(), recipients.size(), skipped, radius);
    }

    public boolean stop(UUID handle) {
        sync();
        Active removed = active.remove(handle);
        if (removed == null) return false;
        for (UUID recipient : removed.recipients()) queueStop(recipient, handle);
        flushControls();
        return true;
    }

    public int clear() {
        sync();
        int count = active.size();
        Set<UUID> recipients = new HashSet<>(pending.keySet());
        for (Active handle : active.values()) recipients.addAll(handle.recipients());
        active.clear();
        for (UUID recipient : recipients) queueClear(recipient);
        flushControls();
        return count;
    }

    /** Reload preserves hello state and already-spent current-tick budgets. */
    public void reload(RelayLimits newLimits) {
        clear();
        limits = newLimits;
    }

    /** Client world changes reset visual state even when no relay handle remains. */
    public void worldChanged(UUID player) {
        removeRecipient(player);
        queueClear(player);
        sync();
        flushControls();
    }

    /** Disconnect/channel unregister: no further packets can be sent to this connection. */
    public void forget(UUID player) {
        compatible.remove(player);
        orientationCompatible.remove(player);
        extendedPlayCompatible.remove(player);
        authoritativeCompatible.remove(player);
        widthPlayCompatible.remove(player);
        stopIntentCompatible.remove(player);
        readyEffects.remove(player);
        readinessRejection.remove(player);
        pending.remove(player);
        starvedPoses.remove(player);
        deferredHellos.remove(player);
        budget.forget(player);
        removeRecipient(player);
    }

    public void tick() {
        sync();
        replayDeferredHellos();
        flushControls();
    }

    public VfxStatus status() {
        sync();
        int receiving = 0;
        for (UUID player : compatible) if (eligible(transport.viewer(player))) receiving++;
        return new VfxStatus(VfxProtocol.VERSION, compatible.size(), receiving, active.size(),
                pending.size(), budget.packets(), budget.plays(), packetsSent, rejectedHellos);
    }

    /** Best-effort budgeted clear; remaining client effects always retain their finite TTL. */
    public void shutdown() {
        clear();
        compatible.clear();
        orientationCompatible.clear();
        extendedPlayCompatible.clear();
        authoritativeCompatible.clear();
        widthPlayCompatible.clear();
        stopIntentCompatible.clear();
        readyEffects.clear();
        readinessRejection.clear();
        pending.clear();
        active.clear();
        starvedPoses.clear();
        deferredHellos.clear();
    }

    private void sync() {
        long now = transport.currentTick();
        budget.sync(now);
        active.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        purgeStarvedPoses(now);
    }

    private boolean eligible(Viewer viewer) {
        return viewer != null && viewer.registeredChannel() && compatible.contains(viewer.id());
    }

    private void removeRecipient(UUID player) {
        active.values().removeIf(handle -> {
            handle.recipients().remove(player);
            return handle.recipients().isEmpty();
        });
    }

    private void queueStop(UUID player, UUID handle) {
        queueEnding(player, handle, new StopEffect(handle));
    }

    private void queueEnding(UUID player, UUID handle, EffectMessage ending) {
        if (!eligible(transport.viewer(player))) return;
        Pending control = pending.computeIfAbsent(player, ignored -> new Pending());
        if (control.clear) return;
        control.endings.put(handle, ending);
        // Cap queued control state independently of future configuration reloads.
        if (control.endings.size() > 256) {
            control.endings.clear();
            control.clear = true;
        }
    }

    private void queueClear(UUID player) {
        if (!eligible(transport.viewer(player))) {
            pending.remove(player);
            return;
        }
        Pending control = pending.computeIfAbsent(player, ignored -> new Pending());
        control.clear = true;
        control.endings.clear();
    }

    private void flushControls() {
        // One message per recipient per pass, rotating unfinished queues for fairness.
        boolean progress;
        do {
            progress = false;
            for (UUID player : new ArrayList<>(pending.keySet())) {
                if (budget.packets() >= limits.maxPacketsPerTick()) return;
                Pending control = pending.remove(player);
                if (!eligible(transport.viewer(player))) continue;
                if (!budget.takeControl(player, limits)) {
                    pending.put(player, control);
                    continue;
                }
                UUID nextStop = control.clear ? null : control.endings.keySet().iterator().next();
                byte[] encoded = VfxProtocol.encode(control.clear ? new ClearEffects() : control.endings.get(nextStop));
                if (transport.send(player, encoded)) {
                    packetsSent++;
                    progress = true;
                    if (control.clear) control.clear = false;
                    else control.endings.remove(nextStop);
                }
                if (control.clear || !control.endings.isEmpty()) pending.put(player, control);
            }
        } while (progress && !pending.isEmpty());
    }
}
