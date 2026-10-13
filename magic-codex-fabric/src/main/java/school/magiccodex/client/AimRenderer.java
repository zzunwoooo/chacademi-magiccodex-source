package school.magiccodex.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import school.magiccodex.client.SpellTargets.Mode;

/**
 * Planar holograms for aiming. Line geometry is never projected onto terrain.
 * Cost: one column lookup per covered block (area r=6 ~ 110, range ring <= 192 points); nothing when not aiming.
 */
public final class AimRenderer {
    static final Identifier AREA = Identifier.of("magiccodex", "textures/aim/area.png");
    static final Identifier FILL = Identifier.of("magiccodex", "textures/aim/fill.png");
    static final Identifier TARGET = Identifier.of("magiccodex", "textures/aim/target.png");
    static final Identifier BAND = Identifier.of("magiccodex", "textures/aim/band.png");
    private static final int INVALID = 0xFF5A4A;
    /** Decal lift above the surface so it never z-fights with the block face. */
    private static final double LIFT = .025;
    /** Layers further than this from the reference height are another floor (cave roof, ledge below): skipped. */
    private static final double MAX_STEP = 3.2;

    private AimRenderer() {}

    static void render(WorldRenderContext context) {
        AimView.capture(context);
        var c = MinecraftClient.getInstance();
        float tickDelta = context.tickCounter().getTickDelta(false);
        AimTargeting.update(c, tickDelta);
        var p = AimTargeting.preview();
        var spell = AimTargeting.spell();
        VertexConsumerProvider consumers = context.consumers();
        if (p == null || spell == null || consumers == null || context.matrixStack() == null || c.world == null || c.options.hudHidden) return;
        var matrices = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        matrices.push();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f m = matrices.peek().getPositionMatrix();
            int rgb = elementColor(spell.category());
            int main = p.valid() ? rgb : INVALID;
            World w = c.world;
            float pulse = .5f + .5f * MathHelper.sin(Util.getMeasuringTimeMs() / 180f);
            var shape = p.shape();
            Vec3d feet = p.feet();
            rangeRing(consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BAND)), m, w, feet,
                        AimMath.horizontalReach(shape.mode()==Mode.LINE||shape.mode()==Mode.CONE?shape.length():shape.range(), p.eye().y - feet.y), argb(rgb, .78f), c.player.getYaw()+(Util.getMeasuringTimeMs()%36000)/100f);
            switch (shape.mode()) {
                case AREA, POINT -> {
                    if (p.point() == null) break;
                    double r = Math.max(.6, shape.radius());
                    decal(consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(FILL)), m, w, p.point(), r, argb(main, .25f));
                    decal(consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(shape.mode() == Mode.POINT ? TARGET : AREA)), m, w, p.point(), r, argb(main, 1f));
                }
                case WALL -> {
                    if (p.point() == null) break;
                    Vec3d along = horizontal(p.point().subtract(feet)).rotateY((float) Math.PI / 2);
                    var points = line(p.point().subtract(along.multiply(shape.width() / 2)), along, shape.width());
                    strip(consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BAND)), m, w, points, p.point().y, shape.depth(), argb(main, .85f), false);
                    decal(consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TARGET)), m, w, p.point(), .7, argb(main, 1f));
                }
                case LINE -> {
                    Vec3d delta=p.point().subtract(p.origin());double length=delta.length();
                    var basis=AimPlane.frame(delta.x,delta.y,delta.z);
                    var band=consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BAND));
                    // The faint full-width rectangle marks the fixed mechanical envelope; the arrow sits inside it.
                    planeQuad(band,m,p.origin(),new AimPlane.V[]{basis.at(0,shape.width()/2),basis.at(0,-shape.width()/2),basis.at(length,-shape.width()/2),basis.at(length,shape.width()/2)},argb(main,.12f));
                    for(var quad:AimPlane.arrow(basis,length,shape.width()))planeQuad(band,m,p.origin(),quad,argb(main,.4f+.08f*pulse));
                    planeEdge(band,m,p.origin(),basis.at(0,shape.width()/2),basis.at(length,shape.width()/2),basis.right(),.035,argb(main,.92f));
                    planeEdge(band,m,p.origin(),basis.at(0,-shape.width()/2),basis.at(length,-shape.width()/2),basis.right(),.035,argb(main,.92f));
                }
                case CONE -> {
                    Vec3d dir = horizontal(p.point().subtract(feet));
                    double half = Math.toRadians(shape.width() / 2), r = shape.length();
                    var band = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BAND));
                    strip(band, m, w, line(feet, dir.rotateY((float) half), r), feet.y, .16, argb(rgb, 1f), false);
                    strip(band, m, w, line(feet, dir.rotateY((float) -half), r), feet.y, .16, argb(rgb, 1f), false);
                    var coneBasis=AimPlane.frame(dir.x,0,dir.z);
                    int pieces=Math.max(8,(int)Math.ceil(shape.width()/4));
                    for(int i=0;i<pieces;i++){double a=-half+2*half*i/pieces,b=-half+2*half*(i+1)/pieces;var zero=new AimPlane.V(0,0,0);planeQuad(band,m,feet.add(0,LIFT,0),new AimPlane.V[]{zero,coneBasis.at(Math.cos(a)*r,Math.sin(a)*r),coneBasis.at(Math.cos(b)*r,Math.sin(b)*r),zero},argb(main,.16f));}
                    List<Vec3d> arc = new ArrayList<>();
                    int n = Math.max(8, (int) Math.round(shape.width() / 4));
                    for (int i = 0; i <= n; i++) arc.add(feet.add(dir.rotateY((float) (-half + 2 * half * i / n)).multiply(r)));
                    strip(band, m, w, arc, feet.y, .16, argb(rgb, 1f), false);
                }
                case UNIT -> {
                    var ring = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TARGET));
                    for (var e : p.candidates()) {
                        if (e == p.target()) continue;
                        ring(ring, m, w, e, e.getLerpedPos(tickDelta), ringRadius(e), argb(rgb, .42f));
                    }
                    if (p.target() != null) {
                        double r = ringRadius(p.target());
                        ring(ring, m, w, p.target(), p.point(), r, argb(main, 1f));
                    }
                }
            }
        } finally {
            matrices.pop();
        }
    }

    static int elementColor(CodexData.Category category) {
        return switch (category == null ? CodexData.Category.ALL : category) {
            case FIRE -> 0xFF8A3D;
            case WATER -> 0x4DA6FF;
            case WIND -> 0x58E0A2;
            case LIGHT -> 0xFFD65C;
            case DARK -> 0xB27BFF;
            case EARTH -> 0xD9A066;
            default -> 0xEAD8A6;
        };
    }

    private static int argb(int rgb, float alpha) { return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF); }
    private static double ringRadius(net.minecraft.entity.LivingEntity e) { return Math.max(.55, Math.min(3, e.getWidth() * .8)); }
    private static Vec3d horizontal(Vec3d v) {
        Vec3d h = new Vec3d(v.x, 0, v.z);
        return h.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : h.normalize();
    }
    private static List<Vec3d> line(Vec3d from, Vec3d dir, double length) {
        int n = Math.max(1, (int) Math.ceil(length / .5));
        List<Vec3d> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) out.add(from.add(dir.multiply(length * i / n)));
        return out;
    }

    /** Target ring under a creature: on the ground, or flat at its feet when it flies above it. */
    private static void ring(VertexConsumer vc, Matrix4f m, World w, net.minecraft.entity.LivingEntity e, Vec3d feet, double r, int color) {
        Double top = AimTargeting.Ground.top(w, feet.x, feet.y + .5, feet.z, 2);
        if (top != null && feet.y - top < .4) { decal(vc, m, w, feet, r, color); return; }
        quadUp(vc, m, feet.x - r, feet.z - r, feet.x + r, feet.z + r, (float) (feet.y + LIFT), 0, 0, 1, 1, color);
    }

    /** Flat sprite of half-size r centred on (point.x, point.z), split per block column and laid on each column's top. */
    static void decal(VertexConsumer vc, Matrix4f m, World w, Vec3d point, double r, int color) {
        quadUp(vc,m,point.x-r,point.z-r,point.x+r,point.z+r,(float)(point.y+LIFT),0,0,1,1,color);
    }

    /** Glow ring that stays on the ground (stepped where the height changes), with four short ticks like the sprite. */
    static void rangeRing(VertexConsumer vc, Matrix4f m, World w, Vec3d center, double radius, int color, float yaw) {
        if (radius <= .5) return;
        int n = AimMath.ringSegments(radius);
        List<Vec3d> ring = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            ring.add(center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        strip(vc, m, w, ring, center.y, .16, color, true);
        Vec3d facing = Vec3d.fromPolar(0, yaw);
        for (int k = 0; k < 4; k++) {
            Vec3d out = facing.rotateY((float) (Math.PI / 2 * k));
            Vec3d at = center.add(out.multiply(radius));
            strip(vc, m, w, List.of(at.subtract(out.multiply(.45)), at.add(out.multiply(.45))), center.y, .12, color, false);
        }
    }

    /**
     * Polyline ribbon of the given width on the ground. Heights come from each point's column; a jump of more than
     * 0.6 block is drawn as two flat halves (a step) instead of a slanted quad through the block.
     */
    static void strip(VertexConsumer vc, Matrix4f m, World w, List<Vec3d> points, double referenceY, double width, int color, boolean closed) {
        for(int i=0;i+1<points.size();i++){
            Vec3d a=points.get(i),b=points.get(i+1),tangent=new Vec3d(b.x-a.x,0,b.z-a.z);
            if(tangent.lengthSquared()<1e-8)continue;var side=new Vec3d(-tangent.z,0,tangent.x).normalize().multiply(width/2);
            ribbon(vc,m,a,b,side,(float)(referenceY+LIFT),(float)(referenceY+LIFT),color);
        }
    }

    private static void planeQuad(VertexConsumer vc,Matrix4f m,Vec3d origin,AimPlane.V[] q,int color){
        for(var p:q)vc.vertex(m,(float)(origin.x+p.x()),(float)(origin.y+p.y()),(float)(origin.z+p.z())).color(color).texture(.5f,.5f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
    }
    private static void planeEdge(VertexConsumer vc,Matrix4f m,Vec3d origin,AimPlane.V a,AimPlane.V b,AimPlane.V right,double width,int color){var s=right.mul(width/2);planeQuad(vc,m,origin,new AimPlane.V[]{a.add(s),a.add(s.mul(-1)),b.add(s.mul(-1)),b.add(s)},color);}

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d side, float ya, float yb, int color) {
        // u runs along the band (uniform), v across it: 0 at one edge, 1 at the other.
        float[][] q = {
            {(float) (a.x + side.x), ya, (float) (a.z + side.z), 0, 0},
            {(float) (a.x - side.x), ya, (float) (a.z - side.z), 0, 1},
            {(float) (b.x - side.x), yb, (float) (b.z - side.z), 1, 1},
            {(float) (b.x + side.x), yb, (float) (b.z + side.z), 1, 0}};
        emit(vc, m, q, color);
    }

    private static void quadUp(VertexConsumer vc, Matrix4f m, double x0, double z0, double x1, double z1, float y, float u0, float v0, float u1, float v1, int color) {
        float[][] q = {
            {(float) x0, y, (float) z0, u0, v0},
            {(float) x0, y, (float) z1, u0, v1},
            {(float) x1, y, (float) z1, u1, v1},
            {(float) x1, y, (float) z0, u1, v0}};
        emit(vc, m, q, color);
    }

    /** Emits the quad counter-clockwise as seen from above (front face up), whatever order it was built in. */
    private static void emit(VertexConsumer vc, Matrix4f m, float[][] q, int color) {
        float cross = (q[1][0] - q[0][0]) * (q[2][2] - q[0][2]) - (q[1][2] - q[0][2]) * (q[2][0] - q[0][0]);
        int[] order = cross <= 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
        for (int i : order) {
            float[] v = q[i];
            vc.vertex(m, v[0], v[1], v[2]).color(color).texture(v[3], v[4]).overlay(OverlayTexture.DEFAULT_UV)
              .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
        }
    }
}
