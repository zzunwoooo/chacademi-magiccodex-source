package school.chacademia.kyudichi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** Ball state + one shared mover. Positions are ball centres; velocities are blocks/tick. */
class Ball {
    final BallKind kind; final int index;
    Vector pos, prev, vel = new Vector();
    BallVisual visual;
    int wanderTimer;
    Vector steerTarget;

    Ball(BallKind kind, int index, Vector pos) { this.kind = kind; this.index = index; this.pos = pos.clone(); this.prev = pos.clone(); }

    /**
     * Moves by vel inside `box` (soft walls: reflect and damp) without entering solid blocks (axis-separated, reflect).
     * Returns true if anything was hit.
     */
    boolean step(World world, Box box, double restitution) {
        prev = pos.clone();
        boolean hit = false;
        double[] p = {pos.getX(), pos.getY(), pos.getZ()}, v = {vel.getX(), vel.getY(), vel.getZ()};
        double[] lo = {box.minX() + 0.3, box.minY() + 0.3, box.minZ() + 0.3}, hi = {box.maxX() - 0.3, box.maxY() - 0.3, box.maxZ() - 0.3};
        for (int a = 0; a < 3; a++) {
            double next = p[a] + v[a];
            if (next < lo[a] || next > hi[a]) { v[a] = -v[a] * restitution; next = Math.clamp(next, lo[a], Math.max(lo[a], hi[a])); hit = true; }
            double old = p[a]; p[a] = next;
            if (solid(world, p[0], p[1], p[2])) { p[a] = old; v[a] = -v[a] * restitution; hit = true; }
        }
        pos = new Vector(p[0], p[1], p[2]);
        vel = new Vector(v[0], v[1], v[2]);
        return hit;
    }

    static boolean solid(World w, double x, double y, double z) {
        Block b = w.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.isPassable();
    }

    Location location(World w) { return new Location(w, pos.getX(), pos.getY(), pos.getZ()); }

    /** Steers velocity toward `desired` (blocks/tick) by `rate` (0..1 per tick). */
    void steer(Vector desired, double rate) { vel.add(desired.clone().subtract(vel).multiply(Math.clamp(rate, 0, 1))); }

    static Vector randomDirection(java.util.Random r, double verticalScale) {
        Vector d = new Vector(r.nextGaussian(), r.nextGaussian() * verticalScale, r.nextGaussian());
        return d.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : d.normalize();
    }

    // ------------------------------------------------------------------
    static final class Quaffle extends Ball {
        enum State { FREE, HELD, FLYING }
        State state = State.FREE;
        UUID holder; long heldSince;
        UUID thrower; Team throwerTeam; long thrownAt;
        Quaffle(Vector p) { super(BallKind.QUAFFLE, 0, p); }
    }

    static final class Bludger extends Ball {
        enum State { FREE, CHASE, DEFLECT }
        State state = State.FREE;
        UUID target; long startAt;                       // startAt: second bludger delay
        UUID deflectBy; Team deflectTeam; long deflectUntil; UUID deflectedFrom;
        final Map<UUID, Long> ignore = new HashMap<>();
        Bludger(int i, Vector p) { super(BallKind.BLUDGER, i, p); }
    }

    static final class Snitch extends Ball {
        long nextPauseAt, pausedUntil;
        Snitch(Vector p) { super(BallKind.SNITCH, 0, p); }
    }
}
