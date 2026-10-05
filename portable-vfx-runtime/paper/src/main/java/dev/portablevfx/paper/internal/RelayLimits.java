package dev.portablevfx.paper.internal;

import dev.portablevfx.protocol.VfxProtocol;

/** Hard ceilings cannot be raised by configuration. Invalid numeric settings use safe defaults. */
public record RelayLimits(double maxRadius, double defaultRadius, int maxActiveHandles,
                          int maxPacketsPerTick, int maxPacketsPerPlayerPerTick,
                          int maxPlaysPerTick, int maxDurationTicks) {
    public static final RelayLimits DEFAULT = new RelayLimits(128, 64, 512, 1024, 32, 128, 12000);

    /*
     * Hard per-viewer class budgets inside maxPacketsPerPlayerPerTick. Each is strictly below the
     * matching client PacketAdmission bucket (client/network/PacketAdmission.java), so poses can
     * never starve new-phase PLAY/IMPACT and a compliant relay never trips client fail-safes:
     *   PLAY/IMPACT (1,4,6,9,10,12): token bucket 48 burst, +8/tick  (client 64 burst, 200/s = 10/tick)
     *   POSE/LINK_POSE/ORIENT (7,13,5): 16/tick, plus <=8 initial cast poses (client 128 burst, 640/s = 32/tick)
     *   STOP/CLEAR/FINISH (2,3,8,11):   10/tick                     (client 256 burst, 256/s = 12.8/tick)
     */
    public static final int VIEWER_PLAY_BURST = 48, VIEWER_PLAYS_PER_TICK = 8;
    public static final int VIEWER_STREAM_PER_TICK = 16;
    public static final int VIEWER_CONTROLS_PER_TICK = 10;

    public RelayLimits {
        maxRadius = bounded(maxRadius, 128, 1, 256);
        defaultRadius = bounded(defaultRadius, Math.min(64, maxRadius), 1, maxRadius);
        maxActiveHandles = bounded(maxActiveHandles, 1, 4096);
        maxPacketsPerTick = bounded(maxPacketsPerTick, 1, 8192);
        maxPacketsPerPlayerPerTick = bounded(maxPacketsPerPlayerPerTick, 1, 128);
        maxPlaysPerTick = bounded(maxPlaysPerTick, 1, 1024);
        maxDurationTicks = bounded(maxDurationTicks, 1, VfxProtocol.MAX_DURATION_TICKS);
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double bounded(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
