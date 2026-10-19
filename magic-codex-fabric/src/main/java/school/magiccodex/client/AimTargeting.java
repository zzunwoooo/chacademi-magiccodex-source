package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
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
                          boolean valid, String reason, boolean clamped, Vec3d castPoint, Vec3d origin) {}

    private static CodexData.Spell spell;
    private static Shape shape;
    private static Preview preview;
    private static long lastNotice;
    /** 큐디치 공중 조준: distance along the aim when nothing is under it (mouse wheel), and whether this frame's point is such an air point. */
    private static double airDistance = -1; private static boolean airPoint;

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
        spell = s; shape = next; preview = null; airDistance = next.range();
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
        var p = preview = compute(c, 1f); // Revalidate the current world and mouse view on the actual click.
        if (p == null) { cancel(false); return true; }
        if (!p.valid()) { notice(c, p.reason()); return true; }
        ClientPlayerEntity player = c.player;
        if (player == null || c.getNetworkHandler() == null) { cancel(false); return true; }
        Vec3d aim = p.castPoint();
        Vec3d eye = player.getEyePos();
        float[] look = AimMath.yawPitch(aim.x - eye.x, aim.y - eye.y, aim.z - eye.z);
        // One rotation update for the server only; the local vanilla mouse view is not snapped.
        c.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(look[0], look[1], player.isOnGround(), player.horizontalCollision));
        if (airPoint && KyudichiClient.airAim()) KyudichiClient.sendAim(aim);
        var chosen = spell;
        cancel(false);
        CastingClient.fire(chosen);
        return true;
    }

    /** Mouse wheel while aiming in 큐디치: move the air point closer / farther. @return true when the scroll was used. */
    public static boolean scroll(double vertical) {
        if (spell == null || shape == null || vertical == 0 || !KyudichiClient.airAim()) return false;
        double reach = shape.range();
        airDistance = Math.clamp((airDistance > 0 ? airDistance : reach) + Math.signum(vertical) * Math.max(1, reach / 16), 2, reach);
        var c = MinecraftClient.getInstance();
        if (c.player != null) c.player.sendMessage(Text.literal("공중 조준 거리 " + Math.round(airDistance) + "m / " + Math.round(reach) + "m"), true);
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
        var player=c.player;var world=c.world;if(player==null||world==null||shape==null)return null;
        Vec3d feet=player.getLerpedPos(tickDelta),eye=player.getCameraPosVec(tickDelta);
        Vec3d camera=AimView.valid()?AimView.camera():eye;
        Vec3d screenDirection=player.getRotationVec(tickDelta);if(c.options.getPerspective().isFrontView())screenDirection=screenDirection.multiply(-1);
        double reach=shape.range();
        Vec3d cameraEnd=camera.add(screenDirection.multiply(camera.distanceTo(eye)+reach));
        var cameraHit=world.raycast(new RaycastContext(camera,cameraEnd,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        Vec3d screenPoint=cameraHit.getType()==HitResult.Type.MISS?cameraEnd:cameraHit.getPos();
        double cameraClosest=camera.squaredDistanceTo(screenPoint);
        for(var candidate:world.getOtherEntities(player,player.getBoundingBox().expand(reach+2),AimTargeting::targetable)){
            var cameraIntersection=candidate.getBoundingBox().expand(.55).raycast(camera,screenPoint);
            if(cameraIntersection.isPresent()&&camera.squaredDistanceTo(cameraIntersection.get())<cameraClosest){screenPoint=cameraIntersection.get();cameraClosest=camera.squaredDistanceTo(screenPoint);}
        }
        Vec3d direction=screenPoint.subtract(eye);if(direction.lengthSquared()<1e-8)direction=player.getRotationVec(tickDelta);direction=direction.normalize();
        double[] endpoint=AimPlane.aim(eye.x,eye.y,eye.z,screenPoint.x,screenPoint.y,screenPoint.z,reach);Vec3d end=new Vec3d(endpoint[0],endpoint[1],endpoint[2]);
        // A shoulder camera can see around a wall; this second ray must always start at the character's eyes.
        var hit=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        Vec3d castPoint=hit.getType()==HitResult.Type.MISS?end:hit.getPos();
        LivingEntity target=null;double closest=eye.squaredDistanceTo(castPoint);var candidates=new ArrayList<LivingEntity>();
        for(var entity:world.getOtherEntities(player,player.getBoundingBox().expand(reach+1),AimTargeting::targetable)){
            var living=(LivingEntity)entity;var intersection=living.getBoundingBox().expand(.55).raycast(eye,castPoint);
            if(intersection.isPresent()&&eye.squaredDistanceTo(intersection.get())<closest){target=living;castPoint=intersection.get();closest=eye.squaredDistanceTo(castPoint);}
        }
        if(target==null)for(var box:world.getOtherEntities(player,player.getBoundingBox().expand(reach+1),e->e instanceof InteractionEntity)){
            // ModelEngine: the model's hitbox is an Interaction entity, the mob itself is the (invisible, smaller) base.
            var intersection=box.getBoundingBox().raycast(eye,castPoint);
            if(intersection.isEmpty()||eye.squaredDistanceTo(intersection.get())>=closest)continue;
            LivingEntity base=modelBase(world,player,box);
            if(base!=null){target=base;castPoint=intersection.get();closest=eye.squaredDistanceTo(castPoint);}
        }
        String reason=null;boolean snapped=false;airPoint=false;
        if(KyudichiClient.airAim()&&target==null&&hit.getType()==HitResult.Type.MISS){
            // 큐디치: nothing under the aim → the point stays in the air at the wheel distance (server: MagicAimPointEvent).
            double d=airDistance>0?Math.min(airDistance,reach):reach;
            castPoint=eye.add(end.subtract(eye).normalize().multiply(d));airPoint=true;
        }
        else if(grounded(shape.mode())&&target==null&&hit.getType()==HitResult.Type.MISS){
            // Zones and walls belong on the ground: a ray into the air is moved down onto the ground under the aim
            // (pulled in toward the caster until it is within range and in sight), never left floating.
            Vec3d ground=groundUnder(world,player,eye,end,reach);
            if(ground==null)reason="바닥을 조준해 주세요.";else{castPoint=ground;snapped=true;}
        }
        if(shape.mode()==Mode.UNIT){
            if(target==null)reason="사거리 안의 대상을 조준해 주세요.";
            else if(shape.filter()==SpellTargets.Filter.AIR&&target.isOnGround())reason="공중에 있는 대상에게 사용해 주세요.";
            else if(shape.filter()==SpellTargets.Filter.BURNING&&!target.isOnFire())reason="불타는 대상에게 사용해 주세요.";
            if(target!=null)candidates.add(target);
        }
        Vec3d point=castPoint,origin=eye;
        if(shape.mode()==Mode.LINE){
            // BoltMech resolves a hit living entity to eye minus .3; otherwise it uses the traced cast location.
            if(shape.casterOrigin())origin=feet.add(0,1,0);
            Vec3d boltAim=target==null?castPoint:target.getEyePos().add(0,-.3,0);
            Vec3d boltDirection=boltAim.subtract(origin);if(boltDirection.lengthSquared()<1e-8)boltDirection=direction;else boltDirection=boltDirection.normalize();
            Vec3d full=origin.add(boltDirection.multiply(shape.length()));
            var wall=world.raycast(new RaycastContext(origin,full,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            point=wall.getType()==HitResult.Type.MISS?full:wall.getPos();
        }
        return new Preview(shape,feet,eye,point,target,candidates,reason==null,reason,snapped||hit.getType()!=HitResult.Type.MISS,castPoint,origin);
    }

    /** Area, point and wall spells are placed on the ground; lines, cones and unit spells are not. */
    static boolean grounded(Mode mode){return mode==Mode.AREA||mode==Mode.POINT||mode==Mode.WALL;}

    /**
     * Ground point for an aim that hit nothing: straight down from the end of the ray, pulled back toward the caster
     * in small steps until the point is inside the server ray length and nothing blocks the eyes' line to it.
     */
    private static Vec3d groundUnder(World world,Entity player,Vec3d eye,Vec3d end,double range){
        double dx=end.x-eye.x,dz=end.z-eye.z,top=Math.max(end.y,eye.y)+.5;
        for(int i=0;i<=16;i++){
            double f=1-i*.05;if(f<.15)break;
            double x=eye.x+dx*f,z=eye.z+dz*f;
            Double y=Ground.top(world,x,top,z,48);
            if(y==null)continue;
            Vec3d p=new Vec3d(x,y,z);
            if(eye.squaredDistanceTo(p)<=(range-.1)*(range-.1)&&sight(world,player,eye,p))return p;
        }
        return null;
    }

    /**
     * ModelEngine mobs are sent to clients with the invisible flag (the model is drawn by display entities), so
     * "invisible" alone must not hide a mob from targeting. Invisible players (옅은 은신) stay untargetable.
     */
    static boolean targetable(Entity e) {
        return e instanceof LivingEntity living && AimEntityPolicy.allowed(living.isAlive(),e instanceof ArmorStandEntity,e.isSpectator(),e instanceof PlayerEntity,e.isInvisible());
    }

    /** The modeled mob a ModelEngine hitbox belongs to: the entity it rides, else the living mob whose base sits inside it. */
    static LivingEntity modelBase(World world, Entity player, Entity hitbox) {
        if (hitbox.getVehicle() instanceof LivingEntity v && v != player && targetable(v)) return v;
        Box b = hitbox.getBoundingBox().expand(.6, 1, .6);
        LivingEntity best = null; double d = Double.MAX_VALUE;
        for (var e : world.getOtherEntities(player, b, AimTargeting::targetable)) {
            if (e instanceof PlayerEntity) continue;
            double q = e.getPos().squaredDistanceTo(hitbox.getPos());
            if (q < d) { d = q; best = (LivingEntity) e; }
        }
        return best;
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
