package dev.portablevfx.paper.internal;

import dev.portablevfx.paper.api.PlayResult;
import dev.portablevfx.protocol.ClearEffects;
import dev.portablevfx.protocol.EffectBasis;
import dev.portablevfx.protocol.PoseEffect;
import dev.portablevfx.protocol.ImpactEffect;
import dev.portablevfx.protocol.FinishEffect;
import dev.portablevfx.protocol.EffectMessage;
import dev.portablevfx.protocol.PlayEffect;
import dev.portablevfx.protocol.StopEffect;
import dev.portablevfx.protocol.VfxProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelayEngineTest {
    private static final String WORLD = "minecraft:overworld";
    private final FakeTransport transport = new FakeTransport();
    private final RelayEngine engine = new RelayEngine(transport, RelayLimits.DEFAULT);

    @Test void catalogDiagnosticsDistinguishMissingHelloPacketAndInventoryWithoutChangingReadiness(){
        UUID viewer=joined(WORLD,0);
        var required=java.util.Set.of("claude:fireball/projectile","claude:fireball/impact");engine.catalogRequirements(required);
        assertTrue(engine.catalogDiagnostic(viewer).contains("stop=false, received=none"));
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(required));
        assertTrue(engine.catalogDiagnostic(viewer).contains("rejected=ready received before base/stop hello"));
        engine.stopIntentHello(viewer,VfxProtocol.encodeHello());
        assertFalse(engine.supportsCatalogCast(viewer));
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(java.util.Set.of("claude:fireball/projectile")));
        assertTrue(engine.catalogDiagnostic(viewer).contains("received=1, required=2, missing=1, ready=false"));
        assertTrue(engine.catalogDiagnostic(viewer).contains("sample=[claude:fireball/impact]"));
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(required));
        assertTrue(engine.catalogDiagnostic(viewer).contains("received=2, required=2, missing=0, ready=true"));
        assertFalse(engine.catalogDiagnostic(viewer).contains("rejected="));
        engine.catalogReady(viewer,new byte[]{1});assertTrue(engine.catalogDiagnostic(viewer).contains("rejected=Invalid readiness size"));
        engine.forget(viewer);assertFalse(engine.catalogDiagnostic(viewer).contains("rejected="));
    }

    @Test void catalogRequiresCompleteGpuReadyInventoryAndCanWithdrawIt() {
        UUID viewer=joined(WORLD,0);engine.extendedPlayHello(viewer,VfxProtocol.encodeHello());
        engine.authoritativeHello(viewer,VfxProtocol.encodeHello());engine.stopIntentHello(viewer,VfxProtocol.encodeHello());
        var required=java.util.Set.of("claude:fireball/projectile","claude:fireball/impact");engine.catalogRequirements(required);
        assertFalse(engine.supportsCatalogCast(viewer));
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(java.util.Set.of("claude:fireball/projectile")));
        assertFalse(engine.supportsCatalogCast(viewer),"CPU-loaded or partial packs cannot enable production casts");
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(required));assertTrue(engine.supportsCatalogCast(viewer));
        var effect=new PlayEffect(UUID.randomUUID(),"claude:fireball/projectile",WORLD,0,0,0,0,0,0,1,0xffffff,1,40);
        assertEquals(1,engine.startCatalogCast(effect,64,EffectBasis.identity()).recipients());
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(java.util.Set.of()));
        assertFalse(engine.supportsCatalogCast(viewer),"reload revokes readiness");
        engine.catalogReady(viewer,dev.portablevfx.protocol.CatalogReadiness.encode(required));
        engine.catalogReady(viewer,new byte[]{1});assertFalse(engine.supportsCatalogCast(viewer),"malformed inventory fails closed");
    }

    @Test void requiresCompatibleHelloAndAdvertisedChannel() {
        UUID noHello = transport.add(WORLD, 0, true);
        UUID noChannel = transport.add(WORLD, 0, false);
        engine.hello(noChannel, VfxProtocol.encodeHello());
        assertEquals(0, engine.play(play(40), 64).recipients());
        assertEquals(0, engine.status().activeHandles());
        engine.hello(noHello, VfxProtocol.encodeHello());
        assertEquals(1, engine.play(play(40), 64).recipients());
        assertEquals(noHello, transport.sent.getFirst().player());
    }

    @Test void unsupportedTruncatedAndTrailingHelloAreRejected() {
        UUID viewer = joined(WORLD, 0);
        for (byte[] invalid : List.of(new byte[] {0, 0, 0, 2}, new byte[] {0}, new byte[] {0, 0, 0, 1, 0})) {
            engine.hello(viewer, invalid);
            assertEquals(0, engine.status().compatibleClients());
        }
        assertEquals(3, engine.status().rejectedHellos());
        assertEquals(0, engine.play(play(40), 64).recipients());
    }

    @Test void sameWorldRadiusIsInclusiveAndConfiguredMaximumIsApplied() {
        joined(WORLD, 128);
        joined(WORLD, 128.01);
        joined("minecraft:the_nether", 0);
        PlayResult result = engine.play(play(40), 256);
        assertEquals(128, result.effectiveRadius());
        assertEquals(1, result.recipients());
    }

    @Test void stopTargetsActualRecipientsEvenAfterMovingOutsideRadius() {
        UUID viewer = joined(WORLD, 0);
        UUID excluded = joined(WORLD, 100);
        PlayEffect effect = play(40);
        assertEquals(1, engine.play(effect, 4).recipients());
        transport.move(viewer, WORLD, 500);
        transport.move(excluded, WORLD, 0);
        assertTrue(engine.stop(effect.instanceId()));
        assertEquals(new StopEffect(effect.instanceId()), transport.sent.getLast().message());
        assertEquals(viewer, transport.sent.getLast().player());
        assertFalse(engine.stop(effect.instanceId()));
    }

    @Test void sharedGlobalAndPerPlayerBudgetsDropExtraPlays() {
        engine.reload(limits(4, 2, 1, 10));
        joined(WORLD, 0);
        joined(WORLD, 0);
        assertEquals(2, engine.play(play(40), 64).recipients());
        PlayResult skipped = engine.play(play(40), 64);
        assertEquals(0, skipped.recipients());
        assertEquals(2, skipped.skippedRateLimited());
        assertEquals(2, transport.sent.size());
        transport.tick++;
        assertEquals(2, engine.play(play(40), 64).recipients());
    }

    @Test void queuedStopsHavePriorityAndWaitForNextTick() {
        engine.reload(limits(4, 1, 1, 10));
        joined(WORLD, 0);
        PlayEffect first = play(40);
        engine.play(first, 64);
        assertTrue(engine.stop(first.instanceId()));
        assertEquals(1, transport.sent.size());
        assertEquals(1, engine.status().pendingControlRecipients());
        transport.tick++;
        assertEquals(0, engine.play(play(40), 64).recipients());
        assertEquals(new StopEffect(first.instanceId()), transport.sent.getLast().message());
        assertEquals(0, engine.status().pendingControlRecipients());
        transport.tick++;
        assertEquals(1, engine.play(play(40), 64).recipients());
    }

    @Test void clearCoalescesPendingStopsIntoOneControl() {
        engine.reload(limits(4, 2, 2, 10));
        joined(WORLD, 0);
        PlayEffect first = play(40);
        PlayEffect second = play(40);
        engine.play(first, 64);
        engine.play(second, 64);
        engine.stop(first.instanceId());
        assertEquals(1, engine.clear());
        transport.tick++;
        engine.tick();
        assertEquals(3, transport.sent.size());
        assertInstanceOf(ClearEffects.class, transport.sent.getLast().message());
        assertEquals(0, engine.status().activeHandles());
    }

    @Test void controlsDoNotStarveLaterRecipientsUnderGlobalBudget() {
        engine.reload(limits(4, 4, 4, 10));
        UUID first = joined(WORLD, 0);
        UUID second = joined(WORLD, 0);
        PlayEffect a = play(40);
        PlayEffect b = play(40);
        engine.play(a, 64);
        engine.play(b, 64);
        engine.reload(limits(4, 1, 1, 10));
        // Reload queued clears while the previous tick's spend remains exhausted.
        transport.tick++;
        engine.tick();
        UUID clearedFirst = transport.sent.getLast().player();
        transport.tick++;
        engine.tick();
        UUID clearedSecond = transport.sent.getLast().player();
        assertNotEquals(clearedFirst, clearedSecond);
        assertTrue(List.of(first, second).contains(clearedFirst));
        assertEquals(0, engine.status().pendingControlRecipients());
    }

    @Test void ttlRemovesHandlesWithoutUnnecessaryStopPackets() {
        joined(WORLD, 0);
        PlayEffect effect = play(2);
        engine.play(effect, 64);
        transport.tick = 1;
        engine.tick();
        assertEquals(1, engine.status().activeHandles());
        transport.tick = 2;
        engine.tick();
        assertEquals(0, engine.status().activeHandles());
        assertFalse(engine.stop(effect.instanceId()));
        assertEquals(1, transport.sent.size());
    }

    @Test void disconnectRemovesHelloRecipientAndPendingState() {
        engine.reload(limits(4, 1, 1, 10));
        UUID viewer = joined(WORLD, 0);
        PlayEffect effect = play(40);
        engine.play(effect, 64);
        engine.stop(effect.instanceId());
        transport.players.remove(viewer);
        engine.forget(viewer);
        assertEquals(0, engine.status().compatibleClients());
        assertEquals(0, engine.status().pendingControlRecipients());
        assertEquals(0, engine.status().activeHandles());
        transport.tick++;
        engine.tick();
        assertEquals(1, transport.sent.size());
    }

    @Test void worldChangeClearsAndDetachesRecipientsButRetainsHello() {
        UUID viewer = joined(WORLD, 0);
        engine.play(play(40), 64);
        transport.move(viewer, "minecraft:the_nether", 0);
        engine.worldChanged(viewer);
        assertEquals(0, engine.status().activeHandles());
        assertEquals(1, engine.status().compatibleClients());
        assertInstanceOf(ClearEffects.class, transport.sent.getLast().message());
    }

    @Test void activeHandleAndPlayRequestLimitsAreEnforced() {
        engine.reload(limits(1, 10, 10, 10));
        joined(WORLD, 0);
        engine.play(play(40), 64);
        assertThrows(RejectedExecutionException.class, () -> engine.play(play(40), 64));
        engine.reload(limits(4, 10, 10, 1));
        transport.tick++;
        engine.play(play(40), 64);
        assertThrows(RejectedExecutionException.class, () -> engine.play(play(40), 64));
    }

    @Test void emptyAudienceStillConsumesPlayRequestBudget() {
        engine.reload(limits(4, 10, 10, 1));
        engine.play(play(40), 64);
        assertEquals(0, engine.status().activeHandles());
        assertThrows(RejectedExecutionException.class, () -> engine.play(play(40), 64));
    }

    @Test void rejectsDurationRadiusAndDuplicateHandle() {
        engine.reload(new RelayLimits(128, 64, 4, 10, 10, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> engine.play(play(11), 64));
        for (double radius : new double[] {0, -1, 257, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> engine.play(play(10), radius));
        }
        joined(WORLD, 0);
        PlayEffect effect = play(10);
        engine.play(effect, 64);
        assertThrows(IllegalArgumentException.class, () -> engine.play(effect, 64));
    }

    @Test void failedTransportIsNotTrackedAsRecipient() {
        joined(WORLD, 0);
        transport.failSends = true;
        assertEquals(0, engine.play(play(40), 64).recipients());
        assertEquals(0, engine.status().activeHandles());
        assertEquals(1, engine.status().packetsThisTick());
        assertEquals(0, engine.status().packetsSent());
    }

    @Test void malformedHelloCannotResetPlayerPacketBudget() {
        engine.reload(limits(4, 10, 1, 10));
        UUID viewer = joined(WORLD, 0);
        assertEquals(1, engine.play(play(40), 64).recipients());
        engine.hello(viewer, new byte[0]);
        engine.hello(viewer, VfxProtocol.encodeHello());
        assertEquals(0, engine.play(play(40), 64).recipients());
        assertEquals(1, engine.status().packetsThisTick());
    }

    @Test void reloadPreservesSpentTickBudgetsAndClearsOldHandles() {
        engine.reload(limits(4, 1, 1, 10));
        joined(WORLD, 0);
        engine.play(play(40), 64);
        engine.reload(limits(4, 1, 1, 10));
        assertEquals(1, engine.status().packetsThisTick());
        assertEquals(0, engine.status().activeHandles());
        assertEquals(1, engine.status().compatibleClients());
        assertEquals(0, engine.play(play(40), 64).recipients());
    }

    @Test void shutdownDropsAllStateWithFiniteTtlFallback() {
        engine.reload(limits(4, 1, 1, 10));
        joined(WORLD, 0);
        engine.play(play(40), 64);
        engine.shutdown();
        assertEquals(0, engine.status().compatibleClients());
        assertEquals(0, engine.status().activeHandles());
        assertEquals(0, engine.status().pendingControlRecipients());
        assertEquals(1, transport.sent.size());
    }

    @Test void excessiveQueuedStopsCollapseToBoundedClear() {
        engine.reload(limits(512, 8192, 128, 1024));
        joined(WORLD, 0);
        List<UUID> handles = new ArrayList<>();
        // The per-viewer PLAY bucket (48 burst, +8/tick) paces delivery to one client.
        while (handles.size() < 384) {
            PlayEffect effect = play(1200);
            if (engine.play(effect, 64).recipients() == 1) handles.add(effect.instanceId());
            else transport.tick++;
        }
        for (UUID handle : handles) assertTrue(engine.stop(handle));
        assertEquals(1, engine.status().pendingControlRecipients());
        transport.tick++;
        engine.tick();
        assertInstanceOf(ClearEffects.class, transport.sent.getLast().message());
        // 384 plays, the current tick's control allowance of stops, then one collapsed clear.
        assertEquals(384 + RelayLimits.VIEWER_CONTROLS_PER_TICK + 1, transport.sent.size());
        assertEquals(0, engine.status().pendingControlRecipients());
    }

    @Test void hardConfigCeilingsCannotBeDisabled() {
        RelayLimits config = new RelayLimits(Double.NaN, Double.POSITIVE_INFINITY,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(128, config.maxRadius());
        assertEquals(64, config.defaultRadius());
        assertEquals(4096, config.maxActiveHandles());
        assertEquals(8192, config.maxPacketsPerTick());
        assertEquals(128, config.maxPacketsPerPlayerPerTick());
        assertEquals(1024, config.maxPlaysPerTick());
        assertEquals(VfxProtocol.MAX_DURATION_TICKS, config.maxDurationTicks());
        RelayLimits minimum = new RelayLimits(-1, -1, 0, 0, 0, 0, 0);
        assertEquals(1, minimum.maxRadius());
        assertEquals(1, minimum.defaultRadius());
        assertEquals(1, minimum.maxPacketsPerTick());
    }

    @Test void orientationIsCapabilityGatedBoundedAndDoesNotExtendTtl() {
        UUID capable=joined(WORLD,0), legacy=joined(WORLD,0);
        engine.orientationHello(capable,VfxProtocol.encodeHello());
        var fx=play(2);assertEquals(2,engine.play(fx,64).recipients());
        assertEquals(1,engine.orient(fx.instanceId(),90,20,0));
        assertEquals(capable,transport.sent.getLast().player());
        assertInstanceOf(dev.portablevfx.protocol.OrientEffect.class,transport.sent.getLast().message());
        assertEquals(0,engine.orient(fx.instanceId(),180,20,0));
        transport.tick++;
        assertEquals(1,engine.orient(fx.instanceId(),180,20,0));
        assertEquals(1,((dev.portablevfx.protocol.OrientEffect)transport.sent.getLast().message()).sequence());
        transport.tick++;
        assertEquals(0,engine.orient(fx.instanceId(),180,20,0));
        assertEquals(0,engine.status().activeHandles());
        assertThrows(IllegalArgumentException.class,()->engine.orient(fx.instanceId(),Float.NaN,0,0));
    }
    @Test void orientationHonorsSharedBudgetAndStopWithoutReplay() {
        engine.reload(limits(4,1,1,10));
        UUID capable=joined(WORLD,0);engine.orientationHello(capable,VfxProtocol.encodeHello());
        var fx=play(40);engine.play(fx,64);
        assertEquals(0,engine.orient(fx.instanceId(),90,0,0));
        transport.tick++;engine.tick();assertEquals(1,transport.sent.size());
        assertEquals(1,engine.orient(fx.instanceId(),180,0,0));
        engine.stop(fx.instanceId());transport.tick++;engine.tick();
        assertInstanceOf(StopEffect.class,transport.sent.getLast().message());
        assertEquals(0,engine.orient(fx.instanceId(),0,0,0));
    }
    @Test void malformedCapabilityLeavesBasePlaybackAndWorldChangeRemovesHandle() {
        UUID p=joined(WORLD,0);engine.orientationHello(p,new byte[]{0});
        var fx=play(40);assertEquals(1,engine.play(fx,64).recipients());
        assertEquals(0,engine.orient(fx.instanceId(),30,0,0));
        engine.orientationHello(p,VfxProtocol.encodeHello());transport.tick++;
        assertEquals(1,engine.orient(fx.instanceId(),30,0,0));
        engine.worldChanged(p);transport.tick++;
        assertEquals(0,engine.orient(fx.instanceId(),45,0,0));
        engine.forget(p);engine.hello(p,VfxProtocol.encodeHello());
        var next=play(40);engine.play(next,64);assertEquals(0,engine.orient(next.instanceId(),0,0,0));
    }

    @Test void extensionCapabilityIsSeparateAndExact() {
        UUID legacy = joined(WORLD,0), capable = joined(WORLD,0);
        engine.orientationHello(legacy, VfxProtocol.encodeHello());
        engine.extendedPlayHello(capable, VfxProtocol.encodeHello());
        var base = play(40);
        var extended = base.withStartTick(0);
        assertEquals(1, engine.play(extended, 64).recipients());
        assertEquals(capable, transport.sent.getLast().player());
        assertEquals(extended, transport.sent.getLast().message());
        engine.forget(capable); engine.hello(capable, VfxProtocol.encodeHello());
        assertEquals(0, engine.play(play(40).withStartTick(0),64).recipients());
        engine.extendedPlayHello(capable, new byte[] {0,0,0,1,0});
        assertEquals(0, engine.play(play(40).withStartTick(0),64).recipients());
        assertEquals(2, engine.play(play(40),64).recipients());
    }

    @Test void synchronizedOrdinaryPlayPreservesLegacyWireAndUsesWorldClock() {
        UUID legacy = joined(WORLD,0), capable = joined(WORLD,0);
        engine.extendedPlayHello(capable,VfxProtocol.encodeHello());
        transport.worldTick = 12000;
        var effect = play(40);
        assertEquals(2,engine.play(effect,64).recipients());
        assertEquals(effect,transport.sent.get(0).message());
        assertEquals(legacy,transport.sent.get(0).player());
        assertEquals(effect.withStartTick(12000),transport.sent.get(1).message());
        assertEquals(capable,transport.sent.get(1).player());
    }

    @Test void namedAnchorNeverDowngradesAndDelayedTtlIsFinite() {
        joined(WORLD,0);
        UUID capable=joined(WORLD,0); engine.extendedPlayHello(capable,VfxProtocol.encodeHello());
        transport.worldTick=12000;
        var effect=new PlayEffect(UUID.randomUUID(),"portablevfx:demo",WORLD,0,0,0,0,0,0,1,0xffffff,1,40,
                UUID.randomUUID(),7,11970,dev.portablevfx.protocol.EffectAnchor.HEAD,0,.2f,0);
        assertEquals(1,engine.play(effect,64).recipients());
        assertEquals(capable,transport.sent.getLast().player());
        transport.tick=10;engine.tick(); assertEquals(0,engine.status().activeHandles());
        assertEquals(0,engine.play(play(40).withStartTick(11900),64).recipients());
        assertEquals(1,transport.sent.size());
    }

    @Test void prematureExtensionHelloCannotGrantCapability() {
        UUID p=transport.add(WORLD,0,true);
        engine.extendedPlayHello(p,VfxProtocol.encodeHello());
        engine.hello(p,VfxProtocol.encodeHello());
        assertEquals(0,engine.play(play(40).withStartTick(0),64).recipients());
        engine.extendedPlayHello(p,VfxProtocol.encodeHello());
        assertEquals(1,engine.play(play(40).withStartTick(0),64).recipients());
    }

    @Test void castStartIsFullyCapabilityGatedAndReservesPlayPoseTogether() {
        joined(WORLD, 0);
        UUID extendedOnly = joined(WORLD, 0); engine.extendedPlayHello(extendedOnly, VfxProtocol.encodeHello());
        UUID authorityOnly = joined(WORLD, 0); engine.authoritativeHello(authorityOnly, VfxProtocol.encodeHello());
        UUID capable = authorityJoined(WORLD, 0);
        var effect = castPlay(40).withStartTick(0);
        assertEquals(1, engine.startCast(effect, 64, EffectBasis.projectile(1, 0, 0)).recipients());
        assertEquals(2, transport.sent.size());
        assertEquals(capable, transport.sent.getFirst().player());
        assertEquals(capable, transport.sent.getLast().player());
        assertInstanceOf(PlayEffect.class, transport.sent.getFirst().message());
        PoseEffect pose = assertInstanceOf(PoseEffect.class, transport.sent.getLast().message());
        assertEquals(0, pose.sequence()); assertEquals(1, pose.basis().forwardX());
        assertEquals(2, engine.status().packetsThisTick());
    }

    @Test void castNeverSendsHalfStartWhenSharedPacketBudgetCannotFitPair() {
        engine.reload(limits(4, 1, 1, 10)); authorityJoined(WORLD, 0);
        var result = engine.startCast(castPlay(40), 64, EffectBasis.identity());
        assertEquals(0, result.recipients()); assertEquals(1, result.skippedRateLimited());
        assertEquals(0, transport.sent.size()); assertEquals(0, engine.status().activeHandles());
    }

    @Test void authoritativeHelloMustBeExactAndFollowBaseHello() {
        UUID player = transport.add(WORLD, 0, true);
        engine.authoritativeHello(player, VfxProtocol.encodeHello());
        engine.hello(player, VfxProtocol.encodeHello());
        engine.extendedPlayHello(player, VfxProtocol.encodeHello());
        assertEquals(0, engine.startCast(castPlay(40), 64, EffectBasis.identity()).recipients());
        engine.authoritativeHello(player, VfxProtocol.encodeHello());
        assertEquals(1, engine.authoritativeClients());
        engine.authoritativeHello(player, new byte[] {0,0,0,1,0});
        assertEquals(0, engine.authoritativeClients());
        assertEquals(0, engine.startCast(castPlay(40), 64, EffectBasis.identity()).recipients());
        assertEquals(1, engine.play(castPlay(40),64).recipients());
    }

    @Test void castPoseIsBoundedOncePerTickUsesMonotonicSequenceAndNeverExtendsTtl() {
        authorityJoined(WORLD, 0); var effect = castPlay(3);
        engine.startCast(effect, 64, EffectBasis.identity());
        assertEquals(0, engine.updateCast(effect.instanceId(), 1, 0, 0, EffectBasis.identity()));
        transport.tick++;
        assertEquals(1, engine.updateCast(effect.instanceId(), 10, 0, 0, EffectBasis.identity()));
        var pose = assertInstanceOf(PoseEffect.class, transport.sent.getLast().message());
        assertEquals(1, pose.sequence()); assertEquals(10, pose.x());
        assertEquals(0, engine.updateCast(effect.instanceId(), 20, 0, 0, EffectBasis.identity()));
        assertThrows(IllegalArgumentException.class, () -> engine.updateCast(effect.instanceId(), 257, 0, 0, EffectBasis.identity()));
        assertThrows(IllegalArgumentException.class, () -> engine.updateCast(effect.instanceId(), Double.NaN, 0, 0, EffectBasis.identity()));
        transport.tick = 3;
        assertEquals(0, engine.updateCast(effect.instanceId(), 0, 0, 0, EffectBasis.identity()));
        assertFalse(engine.isCastActive(effect.instanceId()));
    }

    @Test void castImpactIsAtomicAndKeepsOldHandleForStoppingResidualParticles() {
        UUID capable = authorityJoined(WORLD,0); joined(WORLD,0);
        var flight = castPlay(100); engine.startCast(flight,64,EffectBasis.identity());
        transport.tick++;
        var impact = castPlay(40).withStartTick(1);
        var basis = EffectBasis.impact(0,1,0,1,-1,0);
        PlayResult result = engine.impactCast(flight.instanceId(),impact,basis);
        assertEquals(1,result.recipients()); assertEquals(impact.instanceId(),result.handle());
        var event = assertInstanceOf(ImpactEffect.class,transport.sent.get(transport.sent.size()-2).message());
        var fallback = assertInstanceOf(FinishEffect.class,transport.sent.getLast().message());
        assertTrue(fallback.sequence()>event.sequence());
        assertEquals(capable,transport.sent.getLast().player()); assertEquals(impact,event.impact());
        assertEquals(basis,event.basis()); assertEquals(1,event.sequence());
        assertFalse(engine.isCastActive(flight.instanceId()));
        assertEquals(2,engine.status().activeHandles());
        assertEquals(0,engine.updateCast(flight.instanceId(),1,0,0,basis));
        assertEquals(0,engine.impactCast(flight.instanceId(),castPlay(40),basis).recipients());
        assertTrue(engine.stop(flight.instanceId())); assertTrue(engine.stop(impact.instanceId()));
    }

    @Test void finishQueuesUnderBudgetThenHardStopCanCancelResiduals() {
        engine.reload(limits(4,2,2,10)); authorityJoined(WORLD,0);
        var flight=castPlay(40); engine.startCast(flight,64,EffectBasis.identity());
        assertTrue(engine.finishCast(flight.instanceId())); assertFalse(engine.finishCast(flight.instanceId()));
        assertFalse(engine.isCastActive(flight.instanceId())); assertEquals(1,engine.status().pendingControlRecipients());
        transport.tick++;engine.tick();
        assertInstanceOf(FinishEffect.class,transport.sent.getLast().message());
        assertTrue(engine.stop(flight.instanceId()));
        assertInstanceOf(StopEffect.class,transport.sent.getLast().message());
    }

    @Test void skippedImpactStillQueuesFinishAndDoesNotReplayImpactLater() {
        engine.reload(limits(4,2,2,10)); authorityJoined(WORLD,0);
        var flight=castPlay(40); engine.startCast(flight,64,EffectBasis.identity());
        var result=engine.impactCast(flight.instanceId(),castPlay(40),EffectBasis.identity());
        assertEquals(0,result.recipients()); assertEquals(1,result.skippedRateLimited());
        assertFalse(engine.isCastActive(flight.instanceId()));
        transport.tick++;engine.tick();
        assertInstanceOf(FinishEffect.class,transport.sent.getLast().message());
        assertTrue(transport.sent.stream().noneMatch(sent -> sent.message() instanceof ImpactEffect));
    }

    @Test void castImpactRejectsWrongWorldDistanceAndActiveCapacityBeforeChangingFlight() {
        authorityJoined(WORLD,0); var flight=castPlay(40);engine.startCast(flight,64,EffectBasis.identity());
        var wrongWorld=new PlayEffect(UUID.randomUUID(),"claude:fireball/impact","minecraft:the_nether",0,0,0,0,0,0,1,0xffffff,1,40);
        assertThrows(IllegalArgumentException.class,()->engine.impactCast(flight.instanceId(),wrongWorld,EffectBasis.identity()));
        var far=new PlayEffect(UUID.randomUUID(),"claude:fireball/impact",WORLD,257,0,0,0,0,0,1,0xffffff,1,40);
        assertThrows(IllegalArgumentException.class,()->engine.impactCast(flight.instanceId(),far,EffectBasis.identity()));
        assertTrue(engine.isCastActive(flight.instanceId()));
        engine.reload(limits(1,10,10,10));transport.tick++;
        var one=castPlay(40);engine.startCast(one,64,EffectBasis.identity());
        assertThrows(RejectedExecutionException.class,()->engine.impactCast(one.instanceId(),castPlay(40),EffectBasis.identity()));
        assertTrue(engine.isCastActive(one.instanceId()));
    }

    @Test void castWorldCleanupAndDisconnectCannotLeaveAuthorityState() {
        UUID viewer=authorityJoined(WORLD,0);var flight=castPlay(40);engine.startCast(flight,64,EffectBasis.identity());
        engine.worldUnloaded(WORLD);assertFalse(engine.isCastActive(flight.instanceId()));
        assertInstanceOf(StopEffect.class,transport.sent.getLast().message());
        engine.forget(viewer);engine.hello(viewer,VfxProtocol.encodeHello());
        engine.extendedPlayHello(viewer,VfxProtocol.encodeHello());
        assertEquals(0,engine.startCast(castPlay(40),64,EffectBasis.identity()).recipients());
        engine.authoritativeHello(viewer,VfxProtocol.encodeHello());
        var next=castPlay(40);engine.startCast(next,64,EffectBasis.identity());
        transport.move(viewer,"minecraft:the_nether",0);engine.worldChanged(viewer);
        assertFalse(engine.isCastActive(next.instanceId()));
        assertInstanceOf(ClearEffects.class,transport.sent.getLast().message());
    }

    @Test void gracefulResidualOwnershipHasAnIndependentFiniteTrackingDeadline() {
        authorityJoined(WORLD,0);var flight=castPlay(2);engine.startCast(flight,64,EffectBasis.identity());
        transport.tick=1;assertTrue(engine.finishCast(flight.instanceId()));
        transport.tick=2;engine.tick();assertEquals(1,engine.status().activeHandles());
        transport.tick=1+RelayEngine.MAX_FINISH_TRACK_TICKS;engine.tick();
        assertEquals(0,engine.status().activeHandles());assertFalse(engine.stop(flight.instanceId()));
    }

    @Test void exhaustedPoseBudgetDropsSampleWithoutReplayAndReloadClearsBothPhases() {
        engine.reload(limits(4,2,2,10));authorityJoined(WORLD,0);
        var flight=castPlay(40);engine.startCast(flight,64,EffectBasis.identity());
        transport.tick++;engine.play(castPlay(40),64);engine.play(castPlay(40),64);
        assertEquals(0,engine.updateCast(flight.instanceId(),3,0,0,EffectBasis.identity()));
        int before=transport.sent.size();transport.tick++;engine.tick();assertEquals(before,transport.sent.size());
        assertEquals(1,engine.updateCast(flight.instanceId(),5,0,0,EffectBasis.identity()));
        PoseEffect latest=assertInstanceOf(PoseEffect.class,transport.sent.getLast().message());
        assertEquals(5,latest.x());assertEquals(2,latest.sequence());
        engine.reload(RelayLimits.DEFAULT);assertEquals(0,engine.status().activeHandles());
        assertFalse(engine.isCastActive(flight.instanceId()));
    }

    @Test void castsRejectUnsupportedBackendsAndIgnoredVisualOverrides() {
        assertThrows(IllegalArgumentException.class,()->engine.startCast(play(40),64,EffectBasis.identity()));
        for (PlayEffect invalid : List.of(
                new PlayEffect(UUID.randomUUID(),"claude:demo",WORLD,0,0,0,0,0,0,2,0xffffff,1,40),
                new PlayEffect(UUID.randomUUID(),"claude:demo",WORLD,0,0,0,0,0,0,1,0xff0000,1,40),
                new PlayEffect(UUID.randomUUID(),"claude:demo",WORLD,0,0,0,0,0,0,1,0xffffff,.5f,40))) {
            assertThrows(IllegalArgumentException.class,()->engine.startCast(invalid,64,EffectBasis.identity()));
            assertThrows(IllegalArgumentException.class,()->engine.impactCast(UUID.randomUUID(),invalid,EffectBasis.identity()));
        }
        joined(WORLD,0);assertEquals(1,engine.play(play(40),64).recipients());
    }

    @Test void explicitWidthRequiresBothExtendedAndWidthCapabilityBeforeAnyPlay() {
        authorityJoined(WORLD,0);
        UUID widthOnly=joined(WORLD,0); engine.widthPlayHello(widthOnly,VfxProtocol.encodeHello());
        UUID capable=authorityJoined(WORLD,0);engine.widthPlayHello(capable,VfxProtocol.encodeHello());
        var wave=castPlay(100).withEffectWidth(15.4);
        var result=engine.startCast(wave,64,EffectBasis.identity());
        assertEquals(1,result.recipients()); assertEquals(2,transport.sent.size());
        assertTrue(transport.sent.stream().allMatch(sent->sent.player().equals(capable)));
        assertEquals(15.4,assertInstanceOf(PlayEffect.class,transport.sent.getFirst().message()).effectWidth());
    }

    @Test void widthHelloMustBeExactAndResetsOnDisconnect() {
        UUID player=transport.add(WORLD,0,true);engine.widthPlayHello(player,VfxProtocol.encodeHello());
        engine.hello(player,VfxProtocol.encodeHello());engine.extendedPlayHello(player,VfxProtocol.encodeHello());
        engine.authoritativeHello(player,VfxProtocol.encodeHello());
        assertEquals(0,engine.widthPlayClients());
        engine.widthPlayHello(player,VfxProtocol.encodeHello());assertEquals(1,engine.widthPlayClients());
        engine.widthPlayHello(player,new byte[]{0,0,0,1,0});assertEquals(0,engine.widthPlayClients());
        engine.widthPlayHello(player,VfxProtocol.encodeHello());engine.forget(player);assertEquals(0,engine.widthPlayClients());
        engine.hello(player,VfxProtocol.encodeHello());engine.extendedPlayHello(player,VfxProtocol.encodeHello());
        engine.authoritativeHello(player,VfxProtocol.encodeHello());
        assertEquals(0,engine.startCast(castPlay(40).withEffectWidth(7),64,EffectBasis.identity()).recipients());
    }

    @Test void widthStartStillReservesBothPacketsAndPreservesWidthWithWorldTime() {
        UUID capable=authorityJoined(WORLD,0);engine.widthPlayHello(capable,VfxProtocol.encodeHello());
        engine.reload(limits(4,1,1,10));transport.worldTick=100;
        assertEquals(0,engine.startCast(castPlay(40).withEffectWidth(7),64,EffectBasis.identity()).recipients());
        assertTrue(transport.sent.isEmpty());
        engine.reload(limits(4,4,4,10));
        assertEquals(1,engine.startCast(castPlay(40).withEffectWidth(3.5),64,EffectBasis.identity()).recipients());
        PlayEffect play=assertInstanceOf(PlayEffect.class,transport.sent.getFirst().message());
        assertEquals(100,play.startTick());assertEquals(3.5,play.effectWidth());
    }

    @Test void widthCollapseIsAtomicAndUnsupportedRecipientsOnlyFinish() {
        UUID legacy=authorityJoined(WORLD,0);
        UUID capable=authorityJoined(WORLD,0);engine.widthPlayHello(capable,VfxProtocol.encodeHello());
        var wave=castPlay(100);engine.startCast(wave,64,EffectBasis.identity());transport.sent.clear();
        var result=engine.impactCast(wave.instanceId(),castPlay(100).withEffectWidth(15.4),EffectBasis.identity());
        assertEquals(1,result.recipients());assertFalse(engine.isCastActive(wave.instanceId()));
        assertTrue(transport.sent.stream().filter(sent->sent.player().equals(legacy)).allMatch(sent->sent.message() instanceof FinishEffect));
        ImpactEffect hit=(ImpactEffect)transport.sent.stream().filter(sent->sent.message() instanceof ImpactEffect).findFirst().orElseThrow().message();
        assertEquals(15.4,hit.impact().effectWidth());
    }

    @Test void independentSplashDoesNotConsumeTheWaveLifecycle() {
        UUID capable=authorityJoined(WORLD,0);engine.widthPlayHello(capable,VfxProtocol.encodeHello());
        var wave=castPlay(100).withEffectWidth(7);engine.startCast(wave,64,EffectBasis.identity());
        var splash=castPlay(40);engine.startCast(splash,64,EffectBasis.impact(0,1,0,0,0,1));
        assertTrue(engine.isCastActive(wave.instanceId()));assertTrue(engine.isCastActive(splash.instanceId()));
        assertTrue(transport.sent.stream().noneMatch(sent->sent.message() instanceof FinishEffect || sent.message() instanceof ImpactEffect));
        transport.tick++;assertEquals(1,engine.updateCast(wave.instanceId(),.4,0,0,EffectBasis.identity()));
        var end=engine.impactCast(wave.instanceId(),castPlay(100).withEffectWidth(7),EffectBasis.identity());
        assertEquals(1,end.recipients());assertTrue(engine.isCastActive(splash.instanceId()));
    }

    @Test void linkPoseIsOnlySentToStopIntentClientsOthersGetPlainPose() {
        UUID legacy=authorityJoined(WORLD,0);
        UUID capable=authorityJoined(WORLD,0);engine.stopIntentHello(capable,VfxProtocol.encodeHello());
        var beam=castPlay(100);assertEquals(2,engine.startCast(beam,64,EffectBasis.identity()).recipients());
        transport.sent.clear();transport.tick++;
        assertEquals(2,engine.updateCast(beam.instanceId(),1,0,0,EffectBasis.identity(),5.0));
        PoseEffect toLegacy=(PoseEffect)transport.sent.stream().filter(s->s.player().equals(legacy)).findFirst().orElseThrow().message();
        PoseEffect toCapable=(PoseEffect)transport.sent.stream().filter(s->s.player().equals(capable)).findFirst().orElseThrow().message();
        assertTrue(toLegacy.linkLength()<0,"opcode 7 body without link length");
        assertEquals(5.0,toCapable.linkLength());
        assertEquals(toLegacy.sequence(),toCapable.sequence());
        assertEquals(1,toLegacy.x());
    }

    @Test void posesCannotStarveNewPhasePlayOrImpact() {
        UUID viewer=authorityJoined(WORLD,0);
        List<PlayEffect> casts=new ArrayList<>();
        while(casts.size()<40){var c=castPlay(400);if(engine.startCast(c,64,EffectBasis.identity()).recipients()==1)casts.add(c);else transport.tick++;}
        transport.tick+=10;transport.sent.clear();
        int sent=0;for(var c:casts)sent+=engine.updateCast(c.instanceId(),1,0,0,EffectBasis.identity());
        assertEquals(RelayLimits.VIEWER_STREAM_PER_TICK,sent);
        assertEquals(1,engine.play(castPlay(40),64).recipients(),"new PLAY still fits after a pose storm");
        assertEquals(1,engine.impactCast(casts.getFirst().instanceId(),castPlay(40),EffectBasis.identity()).recipients());
        assertTrue(transport.sent.stream().anyMatch(s->s.message() instanceof ImpactEffect));
        assertEquals(viewer,transport.sent.getLast().player());
    }

    @Test void overBudgetPosesCoalesceAndServeStalestCastFirst() {
        authorityJoined(WORLD,0);
        List<PlayEffect> casts=new ArrayList<>();
        while(casts.size()<20){var c=castPlay(400);if(engine.startCast(c,64,EffectBasis.identity()).recipients()==1)casts.add(c);else transport.tick++;}
        transport.tick++;transport.sent.clear();
        for(var c:casts)engine.updateCast(c.instanceId(),1,0,0,EffectBasis.identity());
        var starved=casts.subList(16,20).stream().map(PlayEffect::instanceId).toList();
        assertTrue(transport.sent.stream().noneMatch(s->starved.contains(((PoseEffect)s.message()).instanceId())));
        transport.tick++;transport.sent.clear();
        for(var c:casts)engine.updateCast(c.instanceId(),2,0,0,EffectBasis.identity());
        assertEquals(16,transport.sent.size());
        var served=transport.sent.stream().map(s->((PoseEffect)s.message()).instanceId()).toList();
        assertTrue(served.containsAll(starved),"stale casts are served ahead of fresh ones");
        assertTrue(transport.sent.stream().allMatch(s->((PoseEffect)s.message()).x()==2),"no stale sample is replayed");
        transport.tick+=5;engine.tick();assertEquals(0,engine.starvedPoseViewers(),"idle starvation marks expire");
    }

    @Test void viewerPlayBucketMirrorsClientAdmission() {
        joined(WORLD,0);
        int[] perTick=new int[4];
        for(int tick=0;tick<4;tick++){transport.tick=tick;for(int i=0;i<40;i++)perTick[tick]+=engine.play(play(40),64).recipients();}
        // 32 = per-player packet cap; then the 48-token bucket refilled by 8/tick paces the rest.
        assertArrayEquals(new int[]{32,24,8,8},perTick);
        assertTrue(java.util.Arrays.stream(perTick).sum()<=PLAY_CLIENT_BURST+PLAY_CLIENT_PER_TICK*3,"server never outruns client admission");
    }

    @Test void helloBeforeViewerExistsIsReplayedInsteadOfDropped() {
        UUID early=UUID.randomUUID();byte[] hello=VfxProtocol.encodeHello();
        engine.catalogRequirements(java.util.Set.of("claude:fireball/projectile"));
        engine.hello(early,hello);engine.extendedPlayHello(early,hello);engine.authoritativeHello(early,hello);engine.stopIntentHello(early,hello);
        engine.catalogReady(early,dev.portablevfx.protocol.CatalogReadiness.encode(java.util.Set.of("claude:fireball/projectile")));
        assertEquals(0,engine.status().compatibleClients());assertEquals(1,engine.deferredHelloPlayers());
        transport.addWithId(early,WORLD,0);transport.tick++;engine.tick();
        assertEquals(1,engine.status().compatibleClients());assertEquals(1,engine.authoritativeClients());
        assertTrue(engine.supportsCatalogCast(early));assertEquals(0,engine.deferredHelloPlayers());
        UUID ghost=UUID.randomUUID();engine.hello(ghost,hello);
        transport.tick+=RelayEngine.DEFERRED_HELLO_TICKS+1;engine.tick();
        assertEquals(0,engine.deferredHelloPlayers(),"never-resolved hellos expire");
        assertEquals(1,engine.status().compatibleClients());
    }

    @Test void reHelloAfterPluginReEnableRestoresStateAndDuplicatesAreIdempotent() {
        UUID viewer=authorityJoined(WORLD,0);engine.stopIntentHello(viewer,VfxProtocol.encodeHello());
        var required=java.util.Set.of("claude:fireball/projectile");engine.catalogRequirements(required);
        byte[] ready=dev.portablevfx.protocol.CatalogReadiness.encode(required);engine.catalogReady(viewer,ready);
        var flight=castPlay(100);assertEquals(1,engine.startCatalogCast(flight,64,EffectBasis.identity()).recipients());
        // Duplicate announcement (client periodic/REGISTER re-handshake) keeps everything intact.
        byte[] hello=VfxProtocol.encodeHello();
        engine.hello(viewer,hello);engine.extendedPlayHello(viewer,hello);engine.authoritativeHello(viewer,hello);engine.stopIntentHello(viewer,hello);engine.catalogReady(viewer,ready);
        assertTrue(engine.isCastActive(flight.instanceId()));assertEquals(1,engine.status().compatibleClients());
        transport.tick++;assertEquals(1,engine.updateCast(flight.instanceId(),1,0,0,EffectBasis.identity()));
        // Plugin disable/enable: a fresh engine knows nothing until the client re-announces.
        engine.shutdown();
        var reEnabled=new RelayEngine(transport,RelayLimits.DEFAULT);reEnabled.catalogRequirements(required);
        assertFalse(reEnabled.supportsCatalogCast(viewer));
        reEnabled.hello(viewer,hello);reEnabled.extendedPlayHello(viewer,hello);reEnabled.authoritativeHello(viewer,hello);reEnabled.stopIntentHello(viewer,hello);reEnabled.catalogReady(viewer,ready);
        assertTrue(reEnabled.supportsCatalogCast(viewer));
        assertEquals(1,reEnabled.startCatalogCast(castPlay(100),64,EffectBasis.identity()).recipients());
    }

    /** Mirrors client/network/PacketAdmission PLAY bucket: 64 burst, 200/s. */
    private static final int PLAY_CLIENT_BURST = 64, PLAY_CLIENT_PER_TICK = 10;

    private static PlayEffect castPlay(int duration) {
        return new PlayEffect(UUID.randomUUID(),"claude:fireball/projectile",WORLD,0,0,0,0,0,0,1,0xffffff,1,duration);
    }

    private UUID authorityJoined(String world,double x) {
        UUID player=joined(world,x);
        engine.extendedPlayHello(player,VfxProtocol.encodeHello());
        engine.authoritativeHello(player,VfxProtocol.encodeHello());
        return player;
    }

    private UUID joined(String world, double x) {
        UUID player = transport.add(world, x, true);
        engine.hello(player, VfxProtocol.encodeHello());
        return player;
    }

    private static PlayEffect play(int duration) {
        return new PlayEffect(UUID.randomUUID(), "portablevfx:demo", WORLD,
                0, 0, 0, 0, 0, 0, 1, 0xFFFFFF, 1, duration);
    }

    private static RelayLimits limits(int active, int packets, int playerPackets, int plays) {
        return new RelayLimits(128, 64, active, packets, playerPackets, plays, 1200);
    }

    private record Sent(UUID player, EffectMessage message) {}
    private static final class FakeTransport implements RelayEngine.Transport {
        long tick;
        long worldTick=-1;
        boolean failSends;
        final Map<UUID, RelayEngine.Viewer> players = new LinkedHashMap<>();
        final List<Sent> sent = new ArrayList<>();

        UUID add(String dimension, double x, boolean registered) {
            UUID id = UUID.randomUUID();
            players.put(id, new RelayEngine.Viewer(id, dimension, x, 0, 0, registered));
            return id;
        }

        void addWithId(UUID id, String dimension, double x) {
            players.put(id, new RelayEngine.Viewer(id, dimension, x, 0, 0, true));
        }

        void move(UUID player, String dimension, double x) {
            players.put(player, new RelayEngine.Viewer(player, dimension, x, 0, 0, true));
        }

        @Override public long currentTick() { return tick; }
        @Override public long worldTick(String dimensionId) { return worldTick; }
        @Override public Collection<RelayEngine.Viewer> viewers(String dimensionId) { return players.values(); }
        @Override public RelayEngine.Viewer viewer(UUID id) { return players.get(id); }
        @Override public boolean send(UUID id, byte[] payload) {
            if (failSends) return false;
            try {
                sent.add(new Sent(id, VfxProtocol.decode(payload)));
                return true;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
