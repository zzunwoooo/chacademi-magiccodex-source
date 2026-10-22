package kr.chacademy.story.quest;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 월드 표시 (본인 화면에만):
 * <ul>
 *   <li>목표 지점 포탈: 바닥 링(천천히 회전) + 안쪽 링(반대로 회전) + 별 4개가 차례로 반짝 + 링 둘레의 빛 띠가 위로 흘러 오르며 위쪽이 사라짐
 *       + 작은 빛 입자 + 위에 "퀘스트 목표 지점" 글자. 들어가면 밝아지며 퍼져 사라진다.</li>
 *   <li>블룸: 후처리 셰이더 없이, 흐린 빛 그림을 빛 더하기(additive)로 겹쳐 그린다 (바닥 빛·링 번짐·빛 띠·별·글자 뒤). 숨 쉬듯 천천히 밝아졌다 어두워진다.</li>
 *   <li>깜빡임 방지 (2026-10-22): 그림은 밉맵 + 선형 필터 (QuestArt.worldLayer). 예전 바닐라 엔티티 레이어는 최근접 필터라
 *       링의 가는 금색 선이 비스듬히 보일 때 깜빡였다. 바닥에서 0.04 띄움.</li>
 *   <li>NPC 징표: 말 걸어야 하는 NPC 머리 위 "!" (둥실거림).</li>
 * </ul>
 * 준비 완료(조건을 다 채움)일 때만 보인다. 모든 움직임은 클라이언트 시계.
 */
final class QuestWorld {
    private QuestWorld() {}

    private static final String RING = "portal_ring", RING_GLOW = "portal_ring_glow", GROUND_GLOW = "portal_glow", BAND = "portal_band",
            MARKER = "marker", GLINT = "glint", HALO = "halo";
    private static final String LABEL = "퀘스트 목표 지점";
    /** 이 모드 전용 버퍼 (그리는 순서대로 바로 그린다 → 겹치는 순서가 매번 같다). */
    private static final MultiBufferSource.BufferSource OWN = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static final double MAX_DIST_SQ = 96 * 96;
    /** 링 그림에서 링 반지름이 그림 반폭의 몇 배인지 (portal_ring.png 기준). */
    private static final float RING_IN_IMAGE = 0.66f, STAR_IN_IMAGE = 0.65f;
    private static final int SEGMENTS = 20;

    private static Entity npcCache;
    private static UUID npcCacheId;
    private static int npcSearch;

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

        PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
        var camera = ctx.camera();
        Vec3 cam = camera.getPosition();
        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        float t = (now % 3_600_000L) / 1000f;
        var camRot = camera.rotation();

        if (portal && cam.distanceToSqr(v.x, v.y, v.z) < MAX_DIST_SQ) {
            float appear = Mth.clamp((now - v.readyAt) / 500f, 0, 1);
            drawPortal(pose, camRot, v.x - cam.x, v.y - cam.y, v.z - cam.z, (float) v.radius, t, appear, 1f, 0f);
            double dist = Math.sqrt(cam.distanceToSqr(v.x, v.y, v.z));
            float labelAlpha = appear * Mth.clamp((float) (48 - dist) / 12f, 0, 1);
            drawLabel(mc, pose, camRot, v.x - cam.x, v.y - cam.y, v.z - cam.z, (float) v.radius, t, labelAlpha);
        }
        if (gone != null && sameWorld(gone)) {
            float k = vanish;
            float alpha = (1 - k) * (1 - k);
            drawPortal(pose, camRot, gone.x - cam.x, gone.y - cam.y, gone.z - cam.z, (float) gone.radius, t, alpha, 1 + 0.35f * k, 0.6f * (1 - k));
            drawLabel(mc, pose, camRot, gone.x - cam.x, gone.y - cam.y, gone.z - cam.z, (float) gone.radius, t, alpha * (1 - k));
        }
        if (marker) {
            Vec3 at = markerPos(mc, v, pt);
            if (at != null && at.distanceToSqr(cam) < 64 * 64) {
                float appear = Mth.clamp((now - v.readyAt) / 400f, 0, 1);
                float bob = Mth.sin(t * 2.4f) * 0.07f;
                float s = 0.46f * (0.96f + 0.04f * Mth.sin(t * 3.1f));
                billboard(pose, camRot, HALO, true, at.x - cam.x, at.y + bob - cam.y, at.z - cam.z, s * 2.2f, s * 2.2f, argb(appear * 0.35f, 0.55f, 0.85f, 1f), false);
                billboard(pose, camRot, MARKER, false, at.x - cam.x, at.y + bob - cam.y, at.z - cam.z, s, s, white(appear), false);
            }
        }
        OWN.endBatch();
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
            if (npcCache != null && !npcCache.isRemoved()) return npcCache.getPosition(pt).add(0, npcCache.getBbHeight() + 0.75, 0);
        }
        return new Vec3(v.x, v.y + 2.55, v.z);
    }

    /** extraGlow: 들어갈 때 더 밝게 (0~1). */
    private static void drawPortal(PoseStack pose, org.joml.Quaternionf camRot,
                                   double x, double y, double z, float radius, float t, float alpha, float scale, float extraGlow) {
        if (alpha <= 0.01f) return;
        float r = Math.max(0.8f, radius) * scale;
        float half = r / RING_IN_IMAGE;
        float breath = 0.86f + 0.14f * Mth.sin(t * 1.7f);
        float boost = Math.min(1f, breath + extraGlow);
        // "우웅" — 블룸은 링보다 느리고 깊게 숨 쉰다
        float hum = 0.5f + 0.5f * Mth.sin(t * 1.25f);
        float glow = Math.min(1.4f, (0.75f + 0.35f * hum) + extraGlow * 1.5f);

        pose.pushPose();
        pose.translate(x, y, z);
        // 바닥 빛 (블룸): 링 안쪽이 은은하게 차오르고 링 둘레가 번진다
        pose.pushPose();
        pose.translate(0, 0.02, 0);
        flat(pose, GROUND_GLOW, true, half * 1.02f, argb(alpha * 0.55f * glow, 0.55f, 0.85f, 1f));
        pose.popPose();
        // 바깥 링: 천천히 시계 방향 (번짐을 같이 돌린다)
        pose.pushPose();
        pose.translate(0, 0.04, 0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 12f));
        flat(pose, RING_GLOW, true, half * 1.03f, argb(alpha * 0.5f * glow, 1f, 1f, 1f));
        flat(pose, RING, false, half, white(alpha * boost));
        flat(pose, RING, true, half, white(alpha * 0.35f * glow));
        pose.popPose();
        // 안쪽 링: 조금 작게, 반대로 더 빠르게, 은은하게
        pose.pushPose();
        pose.translate(0, 0.05, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-t * 20f + 45f));
        flat(pose, RING, false, half * 0.78f, white(alpha * 0.42f * boost));
        flat(pose, RING, true, half * 0.78f, white(alpha * 0.2f * glow));
        pose.popPose();
        // 빛 띠: 두 겹이 엇갈려 위로 흘러 오르며 나타났다 사라짐 → 끊기지 않고 계속 오르는 느낌. 더해서 그려 밝게.
        for (int k = 0; k < 2; k++) {
            float phase = ((t * 0.32f) + k * 0.5f) % 1f;
            float a = Mth.sin(phase * Mth.PI) * alpha;
            float rise = phase * 0.45f;
            pose.pushPose();
            pose.translate(0, 0.04 + rise, 0);
            pose.mulPose(Axis.YP.rotationDegrees(k * 37f + t * 6f * (k == 0 ? 1 : -1)));
            float h = 1.75f * (0.9f + 0.1f * scale);
            band(pose, false, r * 0.93f, h, a * 0.9f * boost);
            band(pose, true, r * 0.93f, h, a * 0.75f * glow);
            band(pose, true, r * 0.99f, h * 1.1f, a * 0.3f * glow);
            pose.popPose();
        }
        pose.popPose();
        // 별 4개: 바깥 링의 별 자리(링과 같이 돈다)에서 차례로 반짝 — 꺼지지 않고 은은하게 남아 있다가 부드럽게 커짐
        double turn = Math.toRadians(t * 12f);
        float d = half * STAR_IN_IMAGE;
        for (int i = 0; i < 4; i++) {
            float wave = 0.5f + 0.5f * Mth.sin(t * 2.2f - i * Mth.HALF_PI);
            float pulse = wave * wave * (3 - 2 * wave);
            double ang = i * Math.PI / 2 + turn;
            double sx = x + Math.sin(ang) * d, sz = z + Math.cos(ang) * d, sy = y + 0.16;
            float size = (0.2f + 0.16f * pulse) * scale;
            billboard(pose, camRot, HALO, true, sx, sy, sz, size * 2.6f, size * 2.6f, argb(alpha * (0.18f + 0.4f * pulse) * glow, 0.6f, 0.88f, 1f), false);
            billboard(pose, camRot, GLINT, true, sx, sy, sz, size, size, white(alpha * (0.25f + 0.75f * pulse)), false);
        }
    }

    /** 포탈 위 "퀘스트 목표 지점" (항상 나를 바라봄, 살짝 둥실). MagicCodex 폰트가 없으면 바닐라 글꼴. */
    private static void drawLabel(Minecraft mc, PoseStack pose, org.joml.Quaternionf camRot, double x, double y, double z, float radius, float t, float alpha) {
        if (alpha <= 0.02f) return;
        double ly = y + 2.1 + Math.min(0.5, Math.max(0, radius - 1) * 0.25) + Mth.sin(t * 1.6f) * 0.05;
        float hum = 0.5f + 0.5f * Mth.sin(t * 1.25f);
        var text = QuestArt.worldText(LABEL);
        float h = 0.3f;
        if (text.isPresent()) {
            var tex = text.get();
            float w = h * tex.width() / (float) tex.height();
            // 뒤쪽 은은한 빛 (블룸)
            billboardTex(pose, camRot, QuestArt.image(HALO).orElse(null), true, x, ly, z, w * 1.5f, h * 3.2f, argb(alpha * (0.22f + 0.12f * hum), 0.5f, 0.82f, 1f), false);
            // 검은 외곽선 → 채운 글자 (차카데미 글자 규칙)
            float o = h * 0.045f;
            int outline = argb(alpha * 0.85f, 0.04f, 0.04f, 0.06f);
            float[][] offs = {{-o, 0}, {o, 0}, {0, -o}, {0, o}, {-o * .7f, -o * .7f}, {o * .7f, -o * .7f}, {-o * .7f, o * .7f}, {o * .7f, o * .7f}};
            for (float[] off : offs) billboardTex(pose, camRot, tex, false, x, ly, z, w, h, outline, false, off[0], off[1]);
            billboardTex(pose, camRot, tex, false, x, ly, z, w, h, argb(alpha, 1f, 0.95f, 0.8f), false);
            billboardTex(pose, camRot, tex, true, x, ly, z, w, h, argb(alpha * 0.25f * hum, 0.6f, 0.88f, 1f), false);
        } else {
            pose.pushPose();
            pose.translate(x, ly, z);
            pose.mulPose(camRot);
            pose.scale(0.025f, -0.025f, 0.025f);
            var label = Component.literal(LABEL);
            float lx = -mc.font.width(label) / 2f;
            mc.font.drawInBatch(label, lx, -4, argb(alpha, 1f, 0.95f, 0.8f), true, pose.last().pose(), OWN,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    /** 바닥에 눕힌 정사각형 (양면이 한 번에 보인다 — 컬링 끔). */
    private static void flat(PoseStack pose, String name, boolean additive, float half, int color) {
        if (additive && QuestArt.shaderPack()) return;
        var tex = QuestArt.image(name).orElse(null);
        if (tex == null || (color >>> 24) == 0) return;
        VertexConsumer vc = OWN.getBuffer(QuestArt.worldLayer(tex, additive));
        var last = pose.last();
        vertex(vc, last, -half, 0, -half, 0, 0, color, 0, 1, 0);
        vertex(vc, last, -half, 0, half, 0, 1, color, 0, 1, 0);
        vertex(vc, last, half, 0, half, 1, 1, color, 0, 1, 0);
        vertex(vc, last, half, 0, -half, 1, 0, color, 0, 1, 0);
    }

    /** 링 둘레의 원통. 아래는 진하고 위로 갈수록 사라진다. 둘레를 따라 그림이 두 번 반복된다. */
    private static void band(PoseStack pose, boolean additive, float radius, float height, float alpha) {
        if (alpha <= 0.01f || (additive && QuestArt.shaderPack())) return;
        var tex = QuestArt.image(BAND).orElse(null);
        if (tex == null) return;
        VertexConsumer vc = OWN.getBuffer(QuestArt.worldLayer(tex, additive));
        var last = pose.last();
        int bottom = white(Math.min(1f, alpha)), top = white(0);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2 / SEGMENTS), a1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float u0 = (float) i / SEGMENTS * 2f, u1 = (float) (i + 1) / SEGMENTS * 2f;
            float nx = Mth.cos((a0 + a1) / 2), nz = Mth.sin((a0 + a1) / 2);
            vertex(vc, last, x0, 0, z0, u0, 1, bottom, nx, 0, nz);
            vertex(vc, last, x1, 0, z1, u1, 1, bottom, nx, 0, nz);
            vertex(vc, last, x1, height, z1, u1, 0, top, nx, 0, nz);
            vertex(vc, last, x0, height, z0, u0, 0, top, nx, 0, nz);
        }
    }

    private static void billboard(PoseStack pose, org.joml.Quaternionf camRot, String name, boolean additive,
                                  double x, double y, double z, float w, float h, int color, boolean mirror) {
        billboardTex(pose, camRot, QuestArt.image(name).orElse(null), additive, x, y, z, w, h, color, mirror);
    }

    private static void billboardTex(PoseStack pose, org.joml.Quaternionf camRot, QuestArt.Tex tex, boolean additive,
                                     double x, double y, double z, float w, float h, int color, boolean mirror) {
        billboardTex(pose, camRot, tex, additive, x, y, z, w, h, color, mirror, 0, 0);
    }

    /** 나를 바라보는 사각형. 화면 기준 오른쪽 +x, 위 +y (글자가 뒤집히지 않게). dx·dy = 화면 기준 밀기 (외곽선). */
    private static void billboardTex(PoseStack pose, org.joml.Quaternionf camRot, QuestArt.Tex tex, boolean additive,
                                     double x, double y, double z, float w, float h, int color, boolean mirror, float dx, float dy) {
        if (tex == null || (color >>> 24) == 0 || (additive && QuestArt.shaderPack())) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camRot);
        VertexConsumer vc = OWN.getBuffer(QuestArt.worldLayer(tex, additive));
        var last = pose.last();
        float hw = w / 2, hh = h / 2;
        float uL = mirror ? 1 : 0, uR = mirror ? 0 : 1;
        vertex(vc, last, dx - hw, dy - hh, 0, uL, 1, color, 0, 0, 1);
        vertex(vc, last, dx + hw, dy - hh, 0, uR, 1, color, 0, 0, 1);
        vertex(vc, last, dx + hw, dy + hh, 0, uR, 0, color, 0, 0, 1);
        vertex(vc, last, dx - hw, dy + hh, 0, uL, 0, color, 0, 0, 1);
        pose.popPose();
    }

    /** 두 레이어 모두에 맞게 쓴다: 직접 만든 레이어(위치·UV·색)는 나머지 값을 무시, 셰이더팩용 바닐라 발광 레이어는 오버레이·빛·법선까지 필요. */
    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int color, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, nx, ny, nz);
    }

    private static int white(float alpha) { return argb(alpha, 1f, 1f, 1f); }

    private static int argb(float alpha, float r, float g, float b) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        return (a << 24) | (Mth.clamp(Math.round(r * 255), 0, 255) << 16) | (Mth.clamp(Math.round(g * 255), 0, 255) << 8) | Mth.clamp(Math.round(b * 255), 0, 255);
    }
}
