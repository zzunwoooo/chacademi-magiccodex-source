package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import school.magiccodex.client.SpellTargets.Mode;
import school.magiccodex.client.SpellTargets.Shape;

/**
 * LoL-style "normal cast": a spell key starts a preview (range ring, area at the cursor, or target highlight),
 * left click casts, right click / the same key again cancels.
 * Just before the cast request the client sends one look packet towards the chosen point, so the existing
 * server ray (Bridge SpellRuntime, PortableVFX, MythicMobs mechanics) aims there without any protocol change.
 * The server still owns range, sight, protection and mana checks.
 */
public final class AimTargeting {
    /** Preview of the current frame. {@code point} is on the ground for area shapes; {@code target} only for UNIT. */
    public record Preview(Shape shape, Vec3d feet, Vec3d eye, Vec3d point, LivingEntity target, List<LivingEntity> candidates,
                          boolean valid, String reason, boolean clamped) {}

    private static CodexData.Spell spell;
    private static Shape shape;
    private static Preview preview;
    private static long lastNotice;

    private AimTargeting() {}

    public static boolean targeting() { return spell != null; }
    public static CodexData.Spell spell() { return spell; }
    public static Preview preview() { return spell == null ? null : preview; }

    /**
     * Called by CastingState when a ready spell key is pressed in magic mode.
     * @return true when the spell is now aimed (or was cast at once with smart cast) instead of cast directly.
     */
    public static boolean begin(CodexData.Spell s) {
        if (s == null) return false;
        Shape next = SpellTargets.get(s.id());
        if (next == null) return false; // self / utility spells keep the instant cast
        if (spell != null && spell.id().equals(s.id())) { cancel(true); return true; }
        spell = s; shape = next; preview = null;
        var c = MinecraftClient.getInstance();
        c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, .16f));
        if (AimSettings.get().smartCast) {
            preview = compute(c, 1f);
            if (!confirm()) return true; // invalid at the cursor: stay in preview so the player can correct it
        }
        return true;
    }

    public static void cancel(boolean sound) {
        if (spell != null && sound)
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), .7f, .14f));
        spell = null; shape = null; preview = null;
    }

    /** Every client tick: drop the preview when it can no longer be used. */
    public static void tick(MinecraftClient c) {
        if (spell == null) return;
        if (!CastingClient.enabled() || c.player == null || !c.player.isAlive() || c.player.isSpectator()
                || c.currentScreen != null && !(c.currentScreen instanceof HudCursorScreen)) cancel(false);
    }

    /** Left click while previewing. @return true when the click was used (cast or refused with a message). */
    public static boolean confirm() {
        var c = MinecraftClient.getInstance();
        if (spell == null) return false;
        var p = preview;
        if (p == null) p = preview = compute(c, 1f);
        if (p == null) { cancel(false); return true; }
        if (!p.valid()) { notice(c, p.reason()); return true; }
        ClientPlayerEntity player = c.player;
        if (player == null || c.getNetworkHandler() == null) { cancel(false); return true; }
        Vec3d aim = p.target() != null ? p.target().getBoundingBox().getCenter()
                : shape.mode() == Mode.LINE || shape.mode() == Mode.CONE ? p.point() : p.point().add(0, -.05, 0);
        Vec3d eye = player.getEyePos();
        float[] look = AimMath.yawPitch(aim.x - eye.x, aim.y - eye.y, aim.z - eye.z);
        // One rotation update for the server only; the local (frozen) view does not move.
        c.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(look[0], look[1], player.isOnGround(), player.horizontalCollision));
        var chosen = spell;
        cancel(false);
        CastingClient.fire(chosen);
        return true;
    }

    private static void notice(MinecraftClient c, String message) {
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        if (c.player != null && message != null && now - lastNotice > 500) { c.player.sendMessage(Text.literal(message), true); lastNotice = now; }
    }

    /** Recomputes the preview for this frame (call after {@link AimView#capture}). */
    static void update(MinecraftClient c, float tickDelta) {
        if (spell == null) return;
        preview = compute(c, tickDelta);
    }

    private static Preview compute(MinecraftClient c, float tickDelta) {
        ClientPlayerEntity player = c.player;
        World world = c.world;
        if (player == null || world == null || shape == null) return null;
        Vec3d feet = player.getLerpedPos(tickDelta);
        Vec3d eye = player.getCameraPosVec(tickDelta);
        double range = shape.range();
        Vec3d cam = AimView.valid() ? AimView.camera() : eye;
        Vec3d dir = AimView.valid() ? AimView.ray(AimCursor.screenX(), AimCursor.screenY()) : player.getRotationVec(tickDelta);
        double reach = cam.distanceTo(eye) + range + 6;
        Vec3d end = cam.add(dir.multiply(reach));
        BlockHitResult block = world.raycast(new RaycastContext(cam, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();

        List<LivingEntity> candidates = new ArrayList<>();
        LivingEntity hovered = null;
        if (shape.mode() == Mode.UNIT) {
            Box around = player.getBoundingBox().expand(range + 1);
            double best = Double.MAX_VALUE;
            for (Entity e : world.getOtherEntities(player, around, AimTargeting::targetable)) {
                var living = (LivingEntity) e;
                if (eye.distanceTo(living.getBoundingBox().getCenter()) > range + .5) continue;
                candidates.add(living);
                var hit = living.getBoundingBox().expand(.35).raycast(cam, stop);
                if (hit.isPresent()) { double d = hit.get().squaredDistanceTo(cam); if (d < best) { best = d; hovered = living; } }
            }
            if (hovered == null) hovered = nearestOnScreen(candidates, tickDelta);
            if (hovered == null) return new Preview(shape, feet, eye, null, null, candidates, false, "사거리 안의 대상을 가리켜 주세요.", false);
            String reason = null;
            if (!sight(world, player, eye, hovered.getBoundingBox().getCenter())) reason = "대상이 가려져 있습니다.";
            else if (shape.filter() == SpellTargets.Filter.AIR && hovered.isOnGround()) reason = "공중에 있는 대상에게 사용해 주세요.";
            else if (shape.filter() == SpellTargets.Filter.BURNING && !hovered.isOnFire()) reason = "불타는 대상에게 사용해 주세요.";
            return new Preview(shape, feet, eye, hovered.getLerpedPos(tickDelta), hovered, candidates, reason == null, reason, false);
        }

        if (shape.mode() == Mode.LINE || shape.mode() == Mode.CONE) {
            // Direction-only shapes: always castable; the server ray decides what is hit.
            Vec3d to = stop.subtract(eye);
            if (to.horizontalLengthSquared() < 1e-4) to = player.getRotationVec(tickDelta);
            return new Preview(shape, feet, eye, eye.add(to.normalize().multiply(Math.min(shape.length(), Math.max(1, to.length())))),
                    null, candidates, true, null, false);
        }

        // AREA / POINT / WALL: a ground point inside the range.
        Vec3d raw;
        if (block.getType() == HitResult.Type.MISS) {
            // Sky / beyond loaded terrain: follow the ray down to the feet level, or straight out when aiming above it.
            double t = dir.y < -1e-3 ? (feet.y - cam.y) / dir.y : -1;
            raw = t > 0 && t < 512 ? cam.add(dir.multiply(t)) : new Vec3d(cam.x + dir.x * 512, feet.y, cam.z + dir.z * 512);
        } else {
            raw = stop;
            if (block.getSide() != Direction.UP) raw = raw.add(Vec3d.of(block.getSide().getVector()).multiply(.05));
        }
        Double ground = Ground.top(world, raw.x, raw.y + .6, raw.z, 24);
        double y = ground == null ? raw.y : ground;
        boolean clamped = false;
        for (int pass = 0; pass < 2; pass++) {
            double limit = AimMath.horizontalReach(range - .35, y - eye.y);
            double[] xz = AimMath.clampHorizontal(feet.x, feet.z, raw.x, raw.z, limit);
            if (xz[2] == 0) break;
            clamped = true;
            raw = new Vec3d(xz[0], Math.max(y, feet.y) + 2, xz[1]);
            ground = Ground.top(world, raw.x, raw.y, raw.z, 24);
            y = ground == null ? feet.y : ground;
        }
        Vec3d point = new Vec3d(raw.x, y, raw.z);
        if (ground == null) return new Preview(shape, feet, eye, point, null, candidates, false, "지정할 바닥이 없습니다.", clamped);
        boolean visible = sight(world, player, eye, point.add(0, .12, 0));
        return new Preview(shape, feet, eye, point, null, candidates, visible, visible ? null : "시야가 막혀 있습니다.", clamped);
    }

    private static LivingEntity nearestOnScreen(List<LivingEntity> candidates, float tickDelta) {
        float cx = AimCursor.screenX(), cy = AimCursor.screenY();
        double best = 28 * 28;
        LivingEntity pick = null;
        for (var e : candidates) {
            var b = e.getBoundingBox();
            float[] s = AimView.toScreen(e.getLerpedPos(tickDelta).add(0, b.getLengthY() / 2, 0));
            if (s == null) continue;
            double d = (s[0] - cx) * (s[0] - cx) + (s[1] - cy) * (s[1] - cy);
            if (d < best) { best = d; pick = e; }
        }
        return pick;
    }

    static boolean targetable(Entity e) {
        return e instanceof LivingEntity living && living.isAlive() && !(e instanceof ArmorStandEntity) && !e.isSpectator() && !e.isInvisible();
    }

    /** Same idea as the server check: nothing solid between the eyes and the point. */
    private static boolean sight(World world, Entity player, Vec3d eye, Vec3d point) {
        var hit = world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getPos().squaredDistanceTo(point) < .36;
    }

    /** Top of the walkable surface in a column (collision shape height, water surface counts as ground). */
    public static final class Ground {
        private Ground() {}
        /** Scans down from {@code fromY} at most {@code depth} blocks. null when nothing is found. */
        public static Double top(World world, double x, double fromY, double z, int depth) {
            var pos = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(fromY), MathHelper.floor(z));
            int bottom = Math.max(world.getBottomY(), pos.getY() - depth);
            for (; pos.getY() >= bottom; pos.move(Direction.DOWN)) {
                if (!world.isChunkLoaded(pos)) return null;
                var state = world.getBlockState(pos);
                var fluid = state.getFluidState();
                if (!fluid.isEmpty()) return pos.getY() + (double) fluid.getHeight(world, pos);
                var shape = state.getCollisionShape(world, pos);
                if (!shape.isEmpty()) {
                    double top = pos.getY() + shape.getMax(Direction.Axis.Y);
                    if (top <= fromY + 1e-3 || pos.getY() == MathHelper.floor(fromY)) return Math.min(top, pos.getY() + 1.0);
                }
            }
            return null;
        }
    }
}
