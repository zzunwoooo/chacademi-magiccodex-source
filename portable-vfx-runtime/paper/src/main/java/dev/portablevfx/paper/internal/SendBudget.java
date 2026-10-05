package dev.portablevfx.paper.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global and per-viewer packet budgets. Every per-viewer class budget is a strict subset of the
 * client's PacketAdmission bucket for the same class (see RelayLimits viewer constants), so a
 * compliant relay never makes a client drop PLAY/IMPACT because poses used the shared budget.
 */
final class SendBudget {
    private long tick = Long.MIN_VALUE;
    private int packets;
    private int plays;
    private final Map<UUID, Integer> perPlayer = new HashMap<>();
    private final Map<UUID, Integer> streams = new HashMap<>();
    private final Map<UUID, Integer> controls = new HashMap<>();
    /** Persistent per-viewer PLAY/IMPACT token bucket mirroring the client's burst/refill. */
    private final Map<UUID, double[]> playTokens = new HashMap<>();

    void sync(long now) {
        if (tick != now) {
            tick = now;
            packets = 0;
            plays = 0;
            perPlayer.clear();
            streams.clear();
            controls.clear();
        }
    }

    boolean takePlay(RelayLimits limits) {
        if (plays >= limits.maxPlaysPerTick()) return false;
        plays++;
        return true;
    }

    boolean takePacket(UUID player, RelayLimits limits) {
        return takePackets(player, limits, 1);
    }

    boolean takePackets(UUID player, RelayLimits limits, int count) {
        if (!hasPackets(player, limits, count)) return false;
        packets += count;
        perPlayer.merge(player, count, Integer::sum);
        return true;
    }

    private boolean hasPackets(UUID player, RelayLimits limits, int count) {
        return count >= 1 && packets + count <= limits.maxPacketsPerTick()
                && perPlayer.getOrDefault(player, 0) + count <= limits.maxPacketsPerPlayerPerTick();
    }

    /** New-phase PLAY/IMPACT: one viewer play token plus {@code count} packets (2 for PLAY+initial pose). */
    boolean takeViewerPlay(UUID player, RelayLimits limits, int count) {
        double[] bucket = playTokens.computeIfAbsent(player, ignored -> new double[] {RelayLimits.VIEWER_PLAY_BURST, tick});
        double refilled = Math.min(RelayLimits.VIEWER_PLAY_BURST,
                bucket[0] + Math.max(0, tick - (long) bucket[1]) * RelayLimits.VIEWER_PLAYS_PER_TICK);
        bucket[0] = refilled; bucket[1] = tick;
        if (refilled < 1 || !takePackets(player, limits, count)) return false;
        bucket[0] = refilled - 1;
        return true;
    }

    /** Remaining POSE/LINK_POSE/ORIENT slots for this viewer in the current tick. */
    int streamRemaining(UUID player, RelayLimits limits) {
        int left = Math.min(RelayLimits.VIEWER_STREAM_PER_TICK - streams.getOrDefault(player, 0),
                limits.maxPacketsPerPlayerPerTick() - perPlayer.getOrDefault(player, 0));
        return Math.max(0, Math.min(left, limits.maxPacketsPerTick() - packets));
    }

    boolean takeStream(UUID player, RelayLimits limits) {
        if (streamRemaining(player, limits) < 1 || !takePacket(player, limits)) return false;
        streams.merge(player, 1, Integer::sum);
        return true;
    }

    boolean takeControl(UUID player, RelayLimits limits) {
        if (controls.getOrDefault(player, 0) >= RelayLimits.VIEWER_CONTROLS_PER_TICK || !takePacket(player, limits)) return false;
        controls.merge(player, 1, Integer::sum);
        return true;
    }

    void forget(UUID player) { playTokens.remove(player); }

    int packets() { return packets; }
    int plays() { return plays; }
}
