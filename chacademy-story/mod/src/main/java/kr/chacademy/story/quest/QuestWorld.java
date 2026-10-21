package kr.chacademy.story.quest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 월드 표시 (본인 화면에만):
 * <ul>
 *   <li>목표 지점 포탈: 바닥 링(천천히 회전) + 안쪽 링(반대로 회전) + 별 4개가 차례로 반짝 + 링 둘레의 빛 띠가 위로 흘러 오르며 위쪽이 사라짐
 *       + 작은 빛 입자. 들어가면 밝아지며 퍼져 사라진다.</li>
 *   <li>NPC 징표: 말 걸어야 하는 NPC 머리 위 "!" (둥실거림).</li>
 * </ul>
 * 준비 완료(조건을 다 채움)일 때만 보인다. 모든 움직임은 클라이언트 시계.
 */
final class QuestWorld {
    private QuestWorld() {}

    private static final ResourceLocation RING = tex("portal_ring"), BAND = tex("portal_band"), MARKER = tex("marker"), GLINT = tex("glint");
    private static final double MAX_DIST_SQ = 96 * 96;
    /** 링 그림에서 링 반지름이 그림 반폭의 몇 배인지 (portal_ring.png 기준). */
    private static final float RING_IN_IMAGE = 0.66f, STAR_IN_IMAGE = 0.65f;
    private static final int SEGMENTS = 20;

    private static Entity npcCache;
    private static UUID npcCacheId;
    private static int npcSearch;

    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath("chaca_story", "textures/quest/" + name + ".png"); }

    static void reset() { npcCache = null; npcCacheId = null; npcSearch = 0; }

    private static boolean sameWorld(QuestClient.View v) {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().location().toString().equals(v.world);
    }

    // ---------------------------------------------------------------- 입자 (틱)

    static void tick(Minecraft mc) {
        var v = QuestClient.view;
        if (mc.level == null || mc.player == null || mc.isPaused() || !v.active() || !v.ready || v.targetType != QuestPackets.TARGET_LOCATION || !sameWorld(v)) return;
        if (mc.player.distanceToSqr(v.x, v.y, v.z) > 48 * 48) return;
        var rand = mc.level.random;
        if (mc.level.getGameTime() % 3 != 0) return;
        double r = Math.max(0.6, v.radius) * 0.85 * Math.sqrt(rand.nextDouble());
        double ang = rand.nextDouble() * Math.PI * 2;
        mc.level.addParticle(ParticleTypes.END_ROD, v.x + Math.cos(ang) * r, v.y + 0.1 + rand.nextDouble() * 0.3, v.z + Math.sin(ang) * r,
                0, 0.012 + rand.nextDouble() * 0.02, 0);
    }

    // ---------------------------------------------------------------- 그리기

    static void render(WorldRenderContext ctx) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var v = QuestClient.view;
        long now = QuestClient.now();
        boolean portal = v.active() && v.ready && v.targetType == QuestPackets.TARGET_LOCATION && sameWorld(v);
        boolean marker = v.active() && v.ready && v.targetType == QuestPackets.TARGET_NPC && sameWorld(v);
        QuestClient.View gone = QuestClient.vanishing;
        float vanish = gone == null ? 1 : (now - QuestClient.vanishAt) / 800f;
        if (gone != null && vanish >= 1) { QuestClient.vanishing = null; gone = null; }
        if (!portal && !marker && gone == null) return;

        MultiBufferSource buffers = ctx.consumers();
        if (buffers == null) buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
        var camera = ctx.camera();
        Vec3 cam = camera.getPosition();
        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        float t = (now % 3_600_000L) / 1000f;
        Set<RenderType> used = new HashSet<>();

        if (portal && cam.distanceToSqr(v.x, v.y, v.z) < MAX_DIST_SQ) {
            float appear = Mth.clamp((now - v.readyAt) / 500f, 0, 1);
            drawPortal(pose, buffers, used, camera.rotation(), v.x - cam.x, v.y - cam.y, v.z - cam.z, (float) v.radius, t, appear, 1f, 0f);
        }
        if (gone != null && sameWorld(gone)) {
            float k = vanish;
            float alpha = (1 - k) * (1 - k);
            drawPortal(pose, buffers, used, camera.rotation(), gone.x - cam.x, gone.y - cam.y, gone.z - cam.z, (float) gone.radius, t, alpha, 1 + 0.35f * k, 0.6f * (1 - k));
        }
        if (marker) {
            Vec3 at = markerPos(mc, v, pt);
            if (at != null && at.distanceToSqr(cam) < 64 * 64) {
                float appear = Mth.clamp((now - v.readyAt) / 400f, 0, 1);
                float bob = Mth.sin(t * 2.4f) * 0.07f;
                float s = 0.46f * (0.96f + 0.04f * Mth.sin(t * 3.1f));
                billboard(pose, buffers, used, MARKER, camera.rotation(), at.x - cam.x, at.y + bob - cam.y, at.z - cam.z, s, s, argb(appear, 1f));
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource src) for (RenderType type : used) src.endBatch(type);
    }

    /** NPC 3D 외형 모드(chacademi-npc-model)가 그 NPC를 모델로 그리는 중이면 모델 꼭대기, 아니면 히트박스 높이. 모드가 없어도 동작. */
    private static java.lang.reflect.Method npcModelTop;
    private static boolean npcModelLooked;

    private static double headHeight(Entity e) {
        if (!npcModelLooked) {
            npcModelLooked = true;
            try {
                npcModelTop = Class.forName("kr.chacademi.npcmodel.NpcModelClient").getMethod("modelTopHeight", Entity.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                npcModelTop = null;
            }
        }
        if (npcModelTop != null) {
            try {
                double h = (double) npcModelTop.invoke(null, e);
                if (h >= 0) return h;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return e.getBbHeight();
    }

    private static Vec3 markerPos(Minecraft mc, QuestClient.View v, float pt) {
        if (v.npc != null) {
            if (npcCache == null || npcCache.isRemoved() || !v.npc.equals(npcCacheId)) {
                if (npcSearch-- <= 0) {
                    npcSearch = 20;
                    npcCache = null;
                    npcCacheId = v.npc;
                    for (Entity e : mc.level.entitiesForRendering()) if (v.npc.equals(e.getUUID())) { npcCache = e; break; }
                }
            }
            if (npcCache != null && !npcCache.isRemoved()) return npcCache.getPosition(pt).add(0, headHeight(npcCache) + 0.75, 0);
        }
        return new Vec3(v.x, v.y + 2.55, v.z);
    }

    /** extraGlow: 들어갈 때 더 밝게 (0~1). */
    private static void drawPortal(PoseStack pose, MultiBufferSource buffers, Set<RenderType> used, org.joml.Quaternionf camRot,
                                   double x, double y, double z, float radius, float t, float alpha, float scale, float extraGlow) {
        if (alpha <= 0.01f) return;
        float r = Math.max(0.8f, radius) * scale;
        float half = r / RING_IN_IMAGE;
        float breath = 0.86f + 0.14f * Mth.sin(t * 1.7f);
        float boost = Math.min(1f, breath + extraGlow);

        pose.pushPose();
        pose.translate(x, y + 0.03, z);
        // 바깥 링: 천천히 시계 방향
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 12f));
        flat(pose, buffers, used, RING, half, argb(alpha * boost, 1f));
        pose.popPose();
        // 안쪽 링: 조금 작게, 반대로 더 빠르게, 은은하게
        pose.pushPose();
        pose.translate(0, 0.01, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-t * 20f + 45f));
        flat(pose, buffers, used, RING, half * 0.78f, argb(alpha * 0.42f * boost, 1f));
        pose.popPose();
        // 빛 띠: 두 겹이 엇갈려 위로 흘러 오르며 나타났다 사라짐 → 끊기지 않고 계속 오르는 느낌
        for (int k = 0; k < 2; k++) {
            float phase = ((t * 0.32f) + k * 0.5f) % 1f;
            float a = Mth.sin(phase * Mth.PI) * 0.8f * alpha * boost;
            float rise = phase * 0.45f;
            pose.pushPose();
            pose.translate(0, rise, 0);
            pose.mulPose(Axis.YP.rotationDegrees(k * 37f + t * 6f * (k == 0 ? 1 : -1)));
            band(pose, buffers, used, r * 0.93f, 1.75f * (0.9f + 0.1f * scale), a);
            pose.popPose();
        }
        pose.popPose();
        // 별 4개: 바깥 링의 별 자리(링과 같이 돈다)에서 차례로 반짝
        double turn = Math.toRadians(t * 12f);
        float d = half * STAR_IN_IMAGE;
        for (int i = 0; i < 4; i++) {
            float pulse = Mth.sin(t * 2.2f - i * Mth.HALF_PI);
            pulse = pulse > 0 ? pulse * pulse * pulse : 0;
            if (pulse < 0.02f) continue;
            double ang = i * Math.PI / 2 + turn;
            float size = (0.22f + 0.18f * pulse) * scale;
            billboard(pose, buffers, used, GLINT, camRot, x + Math.sin(ang) * d, y + 0.15, z + Math.cos(ang) * d, size, size, argb(alpha * pulse, 1f));
        }
    }

    /** 바닥에 눕힌 정사각형 (양면). */
    private static void flat(PoseStack pose, MultiBufferSource buffers, Set<RenderType> used, ResourceLocation tex, float half, int color) {
        RenderType type = RenderType.entityTranslucentEmissive(tex);
        used.add(type);
        VertexConsumer vc = buffers.getBuffer(type);
        var last = pose.last();
        float[][] p = {{-half, 0, -half}, {-half, 0, half}, {half, 0, half}, {half, 0, -half}};
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) vertex(vc, last, p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], color, 0, 1, 0);
        for (int i = 3; i >= 0; i--) vertex(vc, last, p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], color, 0, -1, 0);
    }

    /** 링 둘레의 원통 (양면). 아래는 진하고 위로 갈수록 사라진다. 둘레를 따라 그림이 두 번 반복된다. */
    private static void band(PoseStack pose, MultiBufferSource buffers, Set<RenderType> used, float radius, float height, float alpha) {
        if (alpha <= 0.01f) return;
        RenderType type = RenderType.entityTranslucentEmissive(BAND);
        used.add(type);
        VertexConsumer vc = buffers.getBuffer(type);
        var last = pose.last();
        int bottom = argb(alpha, 1f), top = argb(0, 1f);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2 / SEGMENTS), a1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float u0 = (float) i / SEGMENTS * 2f, u1 = (float) (i + 1) / SEGMENTS * 2f;
            float nx = Mth.cos((a0 + a1) / 2), nz = Mth.sin((a0 + a1) / 2);
            // 바깥쪽
            vertex(vc, last, x0, 0, z0, u0, 1, bottom, nx, 0, nz);
            vertex(vc, last, x1, 0, z1, u1, 1, bottom, nx, 0, nz);
            vertex(vc, last, x1, height, z1, u1, 0, top, nx, 0, nz);
            vertex(vc, last, x0, height, z0, u0, 0, top, nx, 0, nz);
            // 안쪽
            vertex(vc, last, x0, height, z0, u0, 0, top, -nx, 0, -nz);
            vertex(vc, last, x1, height, z1, u1, 0, top, -nx, 0, -nz);
            vertex(vc, last, x1, 0, z1, u1, 1, bottom, -nx, 0, -nz);
            vertex(vc, last, x0, 0, z0, u0, 1, bottom, -nx, 0, -nz);
        }
    }

    private static void billboard(PoseStack pose, MultiBufferSource buffers, Set<RenderType> used, ResourceLocation tex, org.joml.Quaternionf camRot,
                                  double x, double y, double z, float w, float h, int color) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camRot);
        quadXY(pose, buffers, used, tex, w, h, color);
        pose.popPose();
    }

    private static void quadXY(PoseStack pose, MultiBufferSource buffers, Set<RenderType> used, ResourceLocation tex, float w, float h, int color) {
        RenderType type = RenderType.entityTranslucentEmissive(tex);
        used.add(type);
        VertexConsumer vc = buffers.getBuffer(type);
        var last = pose.last();
        float hw = w / 2, hh = h / 2;
        vertex(vc, last, -hw, -hh, 0, 1, 1, color, 0, 0, 1);
        vertex(vc, last, hw, -hh, 0, 0, 1, color, 0, 0, 1);
        vertex(vc, last, hw, hh, 0, 0, 0, color, 0, 0, 1);
        vertex(vc, last, -hw, hh, 0, 1, 0, color, 0, 0, 1);
        vertex(vc, last, -hw, hh, 0, 1, 0, color, 0, 0, -1);
        vertex(vc, last, hw, hh, 0, 0, 0, color, 0, 0, -1);
        vertex(vc, last, hw, -hh, 0, 0, 1, color, 0, 0, -1);
        vertex(vc, last, -hw, -hh, 0, 1, 1, color, 0, 0, -1);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int color, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(last, nx, ny, nz);
    }

    private static int argb(float alpha, float bright) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255), c = Mth.clamp(Math.round(bright * 255), 0, 255);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

}
