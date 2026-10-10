package kr.chacademy.cutscene;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kr.chacademy.cutscene.client.BgmPlayer;
import kr.chacademy.cutscene.client.CutscenePlayback;
import kr.chacademy.cutscene.client.SeenStore;
import kr.chacademy.cutscene.data.CutsceneLoader;
import kr.chacademy.cutscene.net.CutscenePackets;
import kr.chacademy.story.dialogue.Affinity;
import kr.chacademy.story.dialogue.Dialogue;
import kr.chacademy.story.dialogue.DialogueLoader;
import kr.chacademy.story.dialogue.DialoguePresenter;
import kr.chacademy.story.dialogue.DialogueRunner;
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
 * 대화창은 이 모드가 그리지 않는다: 대화 내용·흐름(DialogueRunner)만 갖고, 화면은 MagicCodex 대화창이 그린다 (DialoguePresenter).
 * 혼자 테스트: /story cutscene &lt;id&gt;, /story dialogue &lt;id&gt; (서버 명령어는 실행되지 않음).
 */
public class ChacademyCutsceneClient implements ClientModInitializer {
    public static final String MOD_ID = "chaca_story";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 채팅 명령으로 요청된 재생. 채팅창이 닫힌 다음 틱에 연다 (바로 열면 채팅창이 닫히면서 같이 닫힘). */
    private static Runnable pendingLocal = null;

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
        // 프로토콜 2: 버전 알림 (이 채널을 듣는 것이 서버에게 "새 모드" 라는 표시), 컷신 중단, 스토리 파일 없음
        PayloadTypeRegistry.playS2C().register(CutscenePackets.HelloS2C.TYPE, CutscenePackets.HelloS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.AbortC2S.TYPE, CutscenePackets.AbortC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CutscenePackets.StoryFailC2S.TYPE, CutscenePackets.StoryFailC2S.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CutscenePackets.HelloS2C.TYPE,
                (payload, context) -> CutscenePlayback.serverHello(payload.protocol()));

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
                (handler, client) -> client.execute(() -> {
                    stopDialogue();
                    CutscenePlayback.disconnect();
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BgmPlayer.tick();
            // 게임 메뉴(ESC) 때문에 내려간 컷신을 다시 띄우고, 보여 줄 수 있게 된 재생 요청을 시작한다
            CutscenePlayback.tick(client);
            // 진행 중인 스토리 대화: 끝나기 전에는 화면이 비면 MagicCodex 대화창을 다시 띄운다 (강제로 닫을 수 없음)
            DialoguePresenter.tick(client);
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

    /**
     * 컷신 재생. 렌더 스레드에서 호출할 것. 파일 읽기는 작업 스레드에서 하고 화면은 바로 뜬다 (CutscenePlayback / CutsceneScreen).
     * 끝나면 cutscene_done, 화면이 중간에 사라지면 cutscene_abort, 파일이 없거나 깨졌으면 story_fail 을 서버로 보낸다.
     */
    public static void play(String id, int mode, boolean fromServer) {
        CutscenePlayback.play(id, mode, fromServer);
    }

    public static void stopCurrent() {
        CutscenePlayback.stop();
    }

    // ---------------------------------------------------------------- 대화

    /** 대화 열기. fromServer 면 선택지 이벤트와 끝을 서버로 알린다 (서버가 명령어 실행). */
    private static void stopDialogue() {
        DialoguePresenter.stop();
    }

    public static void openDialogue(String id, Map<String, Integer> affinity, Map<String, String> vars, boolean fromServer,
                                    String startScene, int startLine) {
        stopDialogue();
        if (!DialoguePresenter.available()) {
            // MagicCodex 대화창이 없으면 열지 않는다 (자체 창으로 대신하지 않음). 대화 파일이 없을 때처럼 끝 신호도 보내지 않는다
            LOGGER.warn("대화 {} 를 열 수 없음: MagicCodex 모드가 없거나 대화창 창구 버전이 낮음", id);
            DialoguePresenter.warnUnavailable();
            // 서버에는 "보여 줄 수 없음" 을 알린다 (끝난 것으로 치지 않고, 콘솔에 남아 운영자가 알 수 있게)
            if (fromServer) reportDialogueFailure(id, CutscenePackets.FAIL_MAGICCODEX);
            return;
        }
        Dialogue dialogue;
        try {
            dialogue = DialogueLoader.load(id);
        } catch (Exception e) {
            LOGGER.warn("대화 {} 불러오기 실패", id, e);
            error("대화를 찾을 수 없어요: " + id + " (" + e.getMessage() + ")");
            if (fromServer) {
                boolean missing = !Files.isRegularFile(DialogueLoader.folder(id).resolve("dialogue.yml"));
                reportDialogueFailure(id, missing ? CutscenePackets.FAIL_MISSING : CutscenePackets.FAIL_BROKEN);
            }
            return;
        }
        LOGGER.info("대화 시작: {} (장면 {}개)", id, dialogue.scenes().size());
        // 표정 그림은 MagicCodex 대화창이 폴더에서 직접 (렌더 스레드 밖에서) 읽는다
        DialogueRunner runner = new DialogueRunner(dialogue, DialogueLoader.folder(id), affinity, TextVars.withLocal(vars), new DialogueRunner.Listener() {
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
        DialoguePresenter.start(runner);
    }

    /** 서버가 연 대화를 보여 줄 수 없을 때 (파일 없음·깨짐, MagicCodex 없음). 컷신과 같은 story_fail 신호를 쓴다. */
    private static void reportDialogueFailure(String id, String reason) {
        try {
            CutscenePackets.sendContentMissing(CutscenePackets.KIND_DIALOGUE, id, reason);
        } catch (RuntimeException e) {
            LOGGER.warn("대화 실패를 서버에 알리지 못함", e);
        }
    }

    private static void error(String message) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
    }
}
