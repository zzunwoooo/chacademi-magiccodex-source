package kr.chacademy.story.quest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * 스토리 단계 표시 (서버가 보내는 것만 그린다, 본인에게만 보임).
 * <ul>
 *   <li>HUD: 오른쪽 중간, 장 이름 · 설명 · 조건 진행도 · 목표 완료 ({@link QuestHud})</li>
 *   <li>월드: 준비 완료 시 목표 지점 포탈 / 말 걸 NPC 머리 위 징표 ({@link QuestWorld})</li>
 * </ul>
 * 애니메이션은 모두 클라이언트 시계로 돌린다 (서버는 값이 바뀔 때만 보낸다).
 */
public final class QuestClient {
    private QuestClient() {}

    /** 지금 받은 단계. step 이 비어 있으면 표시 없음. */
    static final class View {
        int session;
        String step = "";
        int run;
        String chapter = "", description = "";
        boolean ready;
        final List<QuestPackets.Cond> conds = new ArrayList<>();
        int targetType;
        String world = "";
        double x, y, z, radius;
        UUID npc;
        /** 화면 시계(ms): 단계를 받은 때 / 준비 완료가 된 때. */
        long shownAt, readyAt;
        int[] doneAt = new int[0];

        boolean active() { return !step.isEmpty(); }
    }

    static View view = new View();
    /** 방금 끝난 포탈 (들어가는 연출용). */
    static View vanishing;
    static long vanishAt;
    static boolean collapsed;

    private static final Path PREFS = FabricLoader.getInstance().getConfigDir().resolve("chaca_story").resolve("quest_hud.properties");

    public static void install() {
        PayloadTypeRegistry.playS2C().register(QuestPackets.StepS2C.TYPE, QuestPackets.StepS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestPackets.UpdateS2C.TYPE, QuestPackets.UpdateS2C.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(QuestPackets.StepS2C.TYPE, (p, ctx) -> ctx.client().execute(() -> onStep(p)));
        ClientPlayNetworking.registerGlobalReceiver(QuestPackets.UpdateS2C.TYPE, (p, ctx) -> ctx.client().execute(() -> onUpdate(p)));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> { view = new View(); vanishing = null; QuestWorld.reset(); }));
        HudRenderCallback.EVENT.register(QuestHud::render);
        // 물·유리·반투명 블록을 다 그린 뒤에 그린다 (AFTER_ENTITIES 면 뒤의 물·유리가 글자·포탈 위에 덮여 흐려 보였음)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(QuestWorld::render);
        ClientTickEvents.END_CLIENT_TICK.register(QuestWorld::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
                if (button == 0 && QuestHud.hitToggle(mx, my)) { toggle(); return false; }
                return true;
            });
        });
        try {
            if (Files.isRegularFile(PREFS)) collapsed = Files.readString(PREFS).contains("collapsed=true");
        } catch (Exception ignored) { }
    }

    static void toggle() {
        collapsed = !collapsed;
        try {
            Files.createDirectories(PREFS.getParent());
            Files.writeString(PREFS, "collapsed=" + collapsed + "\n");
        } catch (Exception ignored) { }
    }

    static long now() { return net.minecraft.Util.getMillis(); }

    private static void onStep(QuestPackets.StepS2C p) {
        View old = view;
        // 포탈로 들어가 다음 단계가 됨: 예전 포탈을 잠깐 사라지는 연출로 남긴다
        if (old.active() && old.ready && old.targetType == QuestPackets.TARGET_LOCATION && !old.step.equals(p.step()) && nearPortal(old)) {
            vanishing = old;
            vanishAt = now();
        }
        View v = new View();
        v.session = p.session();
        v.step = p.step();
        v.run = p.run();
        v.chapter = p.chapter();
        v.description = p.description();
        v.ready = p.ready();
        v.conds.addAll(p.conds());
        v.targetType = p.targetType();
        v.world = p.world();
        v.x = p.x(); v.y = p.y(); v.z = p.z(); v.radius = p.radius();
        try { v.npc = p.npcEntity().isEmpty() ? null : UUID.fromString(p.npcEntity()); } catch (IllegalArgumentException e) { v.npc = null; }
        long t = now();
        boolean same = old.active() && old.step.equals(v.step) && old.run == v.run;
        v.shownAt = same ? old.shownAt : t;
        v.readyAt = v.ready ? (same && old.ready ? old.readyAt : t) : 0;
        v.doneAt = new int[v.conds.size()];
        view = v;
        QuestWorld.reset();
    }

    private static void onUpdate(QuestPackets.UpdateS2C u) {
        View v = view;
        if (!v.active() || u.session() != v.session || !u.step().equals(v.step) || u.run() != v.run) return;
        for (int i = 0; i < Math.min(u.current().length, v.conds.size()); i++) {
            var c = v.conds.get(i);
            v.conds.set(i, new QuestPackets.Cond(c.label(), u.current()[i], c.required()));
        }
        // 포탈에 들어가 호출됨 (대화를 기다리는 동안 준비 완료가 꺼짐): 포탈이 빛나며 사라지는 연출
        if (v.ready && !u.ready() && v.targetType == QuestPackets.TARGET_LOCATION && nearPortal(v)) {
            View gone = new View();
            gone.world = v.world; gone.x = v.x; gone.y = v.y; gone.z = v.z; gone.radius = v.radius; gone.ready = true;
            gone.targetType = v.targetType; gone.step = v.step;
            vanishing = gone;
            vanishAt = now();
        }
        if (u.ready() && !v.ready) v.readyAt = now();
        if (!u.ready()) v.readyAt = 0;
        v.ready = u.ready();
    }

    private static boolean nearPortal(View v) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = mc.player.getX() - v.x, dz = mc.player.getZ() - v.z;
        return dx * dx + dz * dz <= (v.radius + 2) * (v.radius + 2) && Math.abs(mc.player.getY() - v.y) < 4;
    }
}
