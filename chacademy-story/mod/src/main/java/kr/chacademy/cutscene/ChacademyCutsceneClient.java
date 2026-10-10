package kr.chacademy.cutscene;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kr.chacademy.cutscene.client.BgmPlayer;
import kr.chacademy.cutscene.client.CutsceneScreen;
import kr.chacademy.cutscene.client.CutsceneTextures;
import kr.chacademy.cutscene.client.SeenStore;
import kr.chacademy.cutscene.data.Cutscene;
import kr.chacademy.cutscene.data.CutsceneLoader;
import kr.chacademy.cutscene.net.CutscenePackets;
import kr.chacademy.story.dialogue.Affinity;
import kr.chacademy.story.dialogue.Dialogue;
import kr.chacademy.story.dialogue.DialogueLoader;
import kr.chacademy.story.dialogue.DialogueScreen;
import kr.chacademy.story.dialogue.TextVars;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Map;

/**
 * 차카데미 스토리 모드 (컷신 + 대화).
 * <pre>
 * [플러그인] /cutscene play 준우 ch1_intro     → 컷신 재생 → 끝나면 cutscene_done
 * [플러그인] /storydialogue 준우 ch1_wakeup    → 대화 열기 → 선택지마다 dialogue_event, 끝나면 dialogue_done
 * </pre>
 * 혼자 테스트: /story cutscene &lt;id&gt;, /story dialogue &lt;id&gt; (서버 명령어는 실행되지 않음).
 */
public class ChacademyCutsceneClient implements ClientModInitializer {
    public static final String MOD_ID = "chaca_story";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 채팅 명령으로 요청된 재생. 채팅창이 닫힌 다음 틱에 연다 (바로 열면 채팅창이 닫히면서 같이 닫힘). */
    private static Runnable pendingLocal = null;
    /** 진행 중인 스토리 대화. 끝나기 전에는 화면이 비면 다시 연다 (강제로 닫을 수 없음). */
    private static DialogueScreen activeDialogue = null;
    private static long reopenAt = 0;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(CutscenePackets.PlayS2C.TYPE, CutscenePackets.PlayS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(CutscenePackets.StopS2C.TYPE, CutscenePackets.StopS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.DoneC2S.TYPE, CutscenePackets.DoneC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(CutscenePackets.DialogueOpenS2C.TYPE, CutscenePackets.DialogueOpenS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(CutscenePackets.DialogueStopS2C.TYPE, CutscenePackets.DialogueStopS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.DialogueEventC2S.TYPE, CutscenePackets.DialogueEventC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.DialogueDoneC2S.TYPE, CutscenePackets.DialogueDoneC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.DialogueProgressC2S.TYPE, CutscenePackets.DialogueProgressC2S.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(CutscenePackets.PlayS2C.TYPE,
                (payload, context) -> play(payload.id(), payload.mode(), true));
        ClientPlayNetworking.registerGlobalReceiver(CutscenePackets.StopS2C.TYPE,
                (payload, context) -> stopCurrent());
        ClientPlayNetworking.registerGlobalReceiver(CutscenePackets.DialogueOpenS2C.TYPE,
                (payload, context) -> openDialogue(payload.id(), Affinity.decode(payload.affinity()), TextVars.decode(payload.vars()), true,
                        payload.startScene(), payload.startLine()));
        ClientPlayNetworking.registerGlobalReceiver(CutscenePackets.DialogueStopS2C.TYPE,
                (payload, context) -> stopDialogue());
        // 접속이 끊기면 대화 화면 정리 (다시 접속하면 서버가 저장된 곳부터 다시 연다)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(ChacademyCutsceneClient::stopDialogue));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BgmPlayer.tick();
            DialogueScreen d = activeDialogue;
            if (d != null) {
                if (d.isEnded()) activeDialogue = null;
                else if (client.screen == null && client.player != null && System.currentTimeMillis() >= reopenAt) {
                    reopenAt = System.currentTimeMillis() + 500;
                    client.setScreen(d);
                }
            }
            if (pendingLocal != null && !(client.screen instanceof ChatScreen)) {
                Runnable r = pendingLocal;
                pendingLocal = null;
                r.run();
            }
        });

        try {
            Files.createDirectories(CutsceneLoader.rootDir());
            Files.createDirectories(DialogueLoader.rootDir());
        } catch (Exception ignored) {
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("story")
                    .then(cutsceneArg(ClientCommandManager.literal("cutscene")))
                    .then(ClientCommandManager.literal("dialogue")
                            .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        DialogueLoader.listIds().forEach(b::suggest);
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        pendingLocal = () -> openDialogue(id, Map.of(), Map.of(), false, "", 0);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("list").executes(ctx -> {
                        var c = CutsceneLoader.listIds();
                        var d = DialogueLoader.listIds();
                        ctx.getSource().sendFeedback(Component.literal("컷신 " + c.size() + "개: " + String.join(", ", c)));
                        ctx.getSource().sendFeedback(Component.literal("대화 " + d.size() + "개: " + String.join(", ", d)));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("resetseen").executes(ctx -> {
                        SeenStore.reset();
                        ctx.getSource().sendFeedback(Component.literal("본 컷신 기록을 지웠어요."));
                        return 1;
                    })));
            // 예전 명령어도 그대로
            dispatcher.register(ClientCommandManager.literal("ccs")
                    .then(cutsceneArg(ClientCommandManager.literal("play")))
                    .then(ClientCommandManager.literal("list").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal("컷신: " + String.join(", ", CutsceneLoader.listIds())));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("resetseen").executes(ctx -> {
                        SeenStore.reset();
                        ctx.getSource().sendFeedback(Component.literal("본 컷신 기록을 지웠어요."));
                        return 1;
                    })));
        });

        LOGGER.info("Chacademy Story 로드됨 (컷신: {}, 대화: {})", CutsceneLoader.rootDir(), DialogueLoader.rootDir());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> cutsceneArg(
            LiteralArgumentBuilder<FabricClientCommandSource> literal) {
        return literal.then(ClientCommandManager.argument("id", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    CutsceneLoader.listIds().forEach(b::suggest);
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    pendingLocal = () -> play(id, CutscenePackets.MODE_SKIP, false);
                    return 1;
                }));
    }

    // ---------------------------------------------------------------- 컷신

    /** 컷신 재생. 렌더 스레드에서 호출할 것. */
    public static void play(String id, int mode, boolean fromServer) {
        Minecraft mc = Minecraft.getInstance();
        stopCurrent();
        Cutscene cutscene;
        CutsceneTextures textures;
        try {
            cutscene = CutsceneLoader.load(id);
            textures = new CutsceneTextures(cutscene);
        } catch (Exception e) {
            LOGGER.warn("컷신 {} 불러오기 실패", id, e);
            error(Component.translatable("chaca_story.not_found", id).getString() + " (" + e.getMessage() + ")");
            if (fromServer) sendDone(id, true);
            return;
        }

        if (!cutscene.bgm().isEmpty()) {
            try {
                BgmPlayer.start(CutsceneLoader.folder(id).resolve(cutscene.bgm()), cutscene.bgmVolume(), cutscene.bgmStart());
            } catch (Exception e) {
                LOGGER.warn("컷신 {} 배경음을 재생하지 못함", id, e);
            }
        }

        LOGGER.info("컷신 재생: {} (장면 {}개, {}초)", id, cutscene.scenes().size(), cutscene.totalDuration());
        boolean skippable = switch (mode) {
            case CutscenePackets.MODE_SKIP -> true;
            case CutscenePackets.MODE_NOSKIP -> false;
            default -> SeenStore.hasSeen(id);
        };

        mc.setScreen(new CutsceneScreen(cutscene, textures, skippable, result -> {
            if (result.completed()) SeenStore.markSeen(result.id());
            if (fromServer) sendDone(result.id(), result.skipped());
        }));
    }

    public static void stopCurrent() {
        if (Minecraft.getInstance().screen instanceof CutsceneScreen screen) screen.stop();
    }

    private static void sendDone(String id, boolean skipped) {
        if (ClientPlayNetworking.canSend(CutscenePackets.DoneC2S.TYPE)) {
            ClientPlayNetworking.send(new CutscenePackets.DoneC2S(id, skipped));
        }
    }

    // ---------------------------------------------------------------- 대화

    /** 대화 열기. fromServer 면 선택지 이벤트와 끝을 서버로 알린다 (서버가 명령어 실행). */
    private static void stopDialogue() {
        DialogueScreen d = activeDialogue;
        activeDialogue = null;
        if (d != null) d.stop();
        if (Minecraft.getInstance().screen instanceof DialogueScreen s) s.stop();
    }

    public static void openDialogue(String id, Map<String, Integer> affinity, Map<String, String> vars, boolean fromServer,
                                    String startScene, int startLine) {
        Minecraft mc = Minecraft.getInstance();
        stopDialogue();
        Dialogue dialogue;
        CutsceneTextures textures;
        try {
            dialogue = DialogueLoader.load(id);
            textures = new CutsceneTextures("dialogue/" + id, DialogueLoader.folder(id), dialogue.imageNames(), true);
        } catch (Exception e) {
            LOGGER.warn("대화 {} 불러오기 실패", id, e);
            error("대화를 찾을 수 없어요: " + id + " (" + e.getMessage() + ")");
            if (fromServer && ClientPlayNetworking.canSend(CutscenePackets.DialogueDoneC2S.TYPE)) {
                ClientPlayNetworking.send(new CutscenePackets.DialogueDoneC2S(id, ""));
            }
            return;
        }
        LOGGER.info("대화 시작: {} (장면 {}개)", id, dialogue.scenes().size());
        DialogueScreen screen = new DialogueScreen(dialogue, textures, affinity, TextVars.withLocal(vars), new DialogueScreen.Listener() {
            @Override
            public void event(String dialogueId, String event, String npc, int add) {
                if (fromServer && ClientPlayNetworking.canSend(CutscenePackets.DialogueEventC2S.TYPE)) {
                    ClientPlayNetworking.send(new CutscenePackets.DialogueEventC2S(dialogueId, event, npc, add));
                }
            }

            @Override
            public void done(String dialogueId, String lastScene) {
                if (fromServer && ClientPlayNetworking.canSend(CutscenePackets.DialogueDoneC2S.TYPE)) {
                    ClientPlayNetworking.send(new CutscenePackets.DialogueDoneC2S(dialogueId, lastScene));
                }
            }

            @Override
            public void progress(String dialogueId, String scene, int line) {
                if (fromServer && ClientPlayNetworking.canSend(CutscenePackets.DialogueProgressC2S.TYPE)) {
                    ClientPlayNetworking.send(new CutscenePackets.DialogueProgressC2S(dialogueId, scene, line));
                }
            }
        }, startScene, startLine);
        if (!screen.isEnded()) {
            activeDialogue = screen;
            mc.setScreen(screen);
        }
    }

    private static void error(String message) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
    }
}
