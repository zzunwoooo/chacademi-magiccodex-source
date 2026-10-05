package kr.chacademy.npc.dialogue;

import kr.chacademy.npc.ChacaNpcPlugin;
import kr.chacademy.npc.ai.OpenAiClient;
import kr.chacademy.npc.budget.BudgetService;
import kr.chacademy.npc.config.Settings;
import kr.chacademy.npc.core.AffinityStage;
import kr.chacademy.npc.core.BudgetMath;
import kr.chacademy.npc.core.CharacterSheet;
import kr.chacademy.npc.core.Defs.Button;
import kr.chacademy.npc.core.Defs.HintDef;
import kr.chacademy.npc.core.Defs.QuestDef;
import kr.chacademy.npc.core.Defs.RumorView;
import kr.chacademy.npc.core.Defs.Turn;
import kr.chacademy.npc.core.PromptBuilder;
import kr.chacademy.npc.core.ReplyParser;
import kr.chacademy.npc.core.StrikeTracker;
import kr.chacademy.npc.core.TextFilter;
import kr.chacademy.npc.core.TimeText;
import kr.chacademy.npc.data.Storage;
import kr.chacademy.npc.integration.MagicCodexLink;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import school.magiccodex.npctalk.NpcTalkProtocol;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 대화의 중심. 입력 검사 → 상황 정보 → 예산 예약 → AI 호출 → 대답 검사 → 반영 → (검사 통과한) 대사 표시.
 * AI는 제안과 대사만 만든다. 퀘스트 가능 여부·수락·보상은 MagicCodex(서버)가 판단한다.
 */
public final class DialogueService {

    private final ChacaNpcPlugin plugin;
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingQuest> pendingQuests = new ConcurrentHashMap<>();
    private final Map<String, String> jealousToday = new ConcurrentHashMap<>();
    private final ButtonCache buttonCache = new ButtonCache();
    private final Random random = new Random();
    private final SecureRandom tokens = new SecureRandom();
    private final ChatDialogueView chatView = new ChatDialogueView();
    private volatile StrikeTracker strikes;

    /** 수락 토큰: 플레이어·NPC·퀘스트·만료 시각에 묶이고 한 번만 처리된다. */
    private record PendingQuest(UUID player, String npcId, String sessionToken, String questId, long expiresAt) {
    }

    private static final String[] MAGIC_WORDS = {"마법", "주문", "힌트", "비밀", "마력", "스펠", "spell"};

    public DialogueService(ChacaNpcPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        Settings s = plugin.settings();
        strikes = new StrikeTracker(s.strikeWindowMinutes * 60_000L, s.strikeMax, s.strikeLockMinutes * 60_000L);
    }

    public ButtonCache buttonCache() {
        return buttonCache;
    }

    public DialogueSession session(UUID player) {
        return sessions.get(player);
    }

    public Collection<DialogueSession> sessions() {
        return sessions.values();
    }

    /** 세션이 없을 때 쓰는 안내용 화면. */
    public DialogueView viewFor(Player p) {
        return plugin.talkChannel().isCapable(p) ? plugin.talkChannel() : chatView;
    }

    public boolean isTalkingWith(int citizensId) {
        for (DialogueSession s : sessions.values()) {
            if (s.citizensId() != null && s.citizensId() == citizensId) {
                return true;
            }
        }
        return false;
    }

    private MagicCodexLink link() {
        return plugin.link();
    }

    // =================================================================== 열기/닫기

    public void open(Player p, CharacterSheet c, Integer citizensId, boolean adminTest) {
        DialogueSession old = sessions.get(p.getUniqueId());
        if (old != null) {
            if (old.character().id().equals(c.id()) && old.adminTest() == adminTest) {
                old.lastActivity = System.currentTimeMillis();
                if (old.opened) {
                    old.view.open(p, old, c.fallback("greeting_again", plugin.settings().defaultFallbacks, random),
                            plugin.settings().buttons, hearts(old));
                }
                return;
            }
            close(p, false);
        }
        long now = System.currentTimeMillis();
        DialogueSession s = new DialogueSession(p.getUniqueId(), link().playerName(p), c, citizensId, adminTest, now, viewFor(p));
        sessions.put(p.getUniqueId(), s);
        loadContext(p, s);
        if (citizensId != null) {
            plugin.npcs().face(citizensId, p);
        }
        String greeting = c.fallback("greeting", plugin.settings().defaultFallbacks, random);
        // 호감도(하트 표시)를 불러온 뒤 화면을 연다. 늦어져도 1초 뒤에는 연다.
        int timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> showOpen(p, s, greeting), 20L).getTaskId();
        s.loading.whenComplete((v, ex) -> plugin.sync(() -> {
            Bukkit.getScheduler().cancelTask(timeoutTask);
            showOpen(p, s, greeting);
        }));
    }

    private void showOpen(Player p, DialogueSession s, String greeting) {
        if (s.opened || sessions.get(s.player()) != s || !p.isOnline()) {
            return;
        }
        s.opened = true;
        s.view.open(p, s, greeting, plugin.settings().buttons, hearts(s));
    }

    private int hearts(DialogueSession s) {
        return stage(s).ordinal();
    }

    private AffinityStage stage(DialogueSession s) {
        return AffinityStage.of(s.score, s.heart, s.character().romanceable());
    }

    private void loadContext(Player p, DialogueSession s) {
        Settings st = plugin.settings();
        String pid = s.player().toString();
        String npc = s.character().id();
        long now = System.currentTimeMillis();
        long dayStart = st.today().atStartOfDay(st.zone).toInstant().toEpochMilli();
        Storage storage = plugin.storage();
        CompletableFuture<Void> db = plugin.database().async(() -> {
            s.memos = storage.memos(pid, npc);
            Storage.LastChat lc = storage.lastChat(pid, npc);
            if (lc != null && now - lc.endedAt() <= st.lastChatExpireDays * 86_400_000L) {
                s.lastChat = lc;
            }
            s.rumors = storage.rumorsFor(npc, pid, now, Math.max(1, st.rumorMaxInPrompt) * 2);
            s.promises = storage.promises(pid, npc, 3);
            s.nickname = storage.nickname(pid, npc);
            s.hints = storage.hintState(pid, dayStart);
            s.quests = storage.questState(pid, npc, now - 86_400_000L);
            return (Void) null;
        });
        // MagicCodex: 호감도·NPC별 호칭·스토리 진행 (메인 스레드에서 호출, 메인 스레드에서 완료)
        CompletableFuture<Void> mc;
        if (link().available()) {
            CompletableFuture<Void> aff = link().affinity(s.player(), npc).handle((v, ex) -> {
                if (v != null && v.length >= 2) {
                    s.score = v[0];
                    s.heart = v[1];
                } else {
                    s.score = st.affinityDefaultScore;
                    s.heart = 0;
                }
                return null;
            });
            CompletableFuture<Void> nick = link().nickname(s.player(), npc).handle((v, ex) -> {
                s.npcNickname = v == null || v.isBlank() ? null : v;
                return null;
            });
            CompletableFuture<Void> story = link().storySummary(s.player()).handle((v, ex) -> {
                s.storyChapter = v == null || v.isBlank() ? null : v;
                return null;
            });
            mc = CompletableFuture.allOf(aff, nick, story);
        } else {
            s.score = st.affinityDefaultScore;
            s.heart = 4; // MagicCodex 없음: 하트 단계 제한 없이 기본 점수로
            mc = CompletableFuture.completedFuture(null);
        }
        s.loading = CompletableFuture.allOf(db, mc).handle((v, ex) -> {
            s.loaded = true;
            return null;
        });
    }

    /** 대화 종료. byPlayer = 플레이어가 직접 끝냄(작별 인사). */
    public void close(Player p, boolean byPlayer) {
        DialogueSession s = sessions.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        cancelTimers(s);
        s.busy = false;
        saveLastChat(s);
        String farewell = byPlayer ? s.character().fallback("farewell", plugin.settings().defaultFallbacks, random) : null;
        s.view.close(p, s, farewell);
    }

    /** 접속 종료 등으로 플레이어 없이 닫기. */
    public void closeQuietly(UUID player) {
        DialogueSession s = sessions.remove(player);
        if (s != null) {
            cancelTimers(s);
            saveLastChat(s);
        }
    }

    private void cancelTimers(DialogueSession s) {
        if (s.stallTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(s.stallTaskId);
            s.stallTaskId = -1;
        }
        if (s.timeoutTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(s.timeoutTaskId);
            s.timeoutTaskId = -1;
        }
    }

    private void saveLastChat(DialogueSession s) {
        if (s.saveable.isEmpty() || s.adminTest()) {
            return;
        }
        int n = plugin.settings().lastChatTurns * 2;
        List<Turn> last = new ArrayList<>(s.saveable.subList(Math.max(0, s.saveable.size() - n), s.saveable.size()));
        String pid = s.player().toString();
        String npc = s.character().id();
        long now = System.currentTimeMillis();
        plugin.database().run(() -> plugin.storage().saveLastChat(pid, npc, last, now));
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                close(p, false);
            } else {
                closeQuietly(id);
            }
        }
    }

    /** 1초마다: 오래 말 없거나 멀어지면 닫고, NPC가 플레이어를 바라보게 한다. */
    public void tick() {
        Settings st = plugin.settings();
        long now = System.currentTimeMillis();
        pendingQuests.values().removeIf(q -> now > q.expiresAt());
        for (DialogueSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.player());
            if (p == null || !p.isOnline()) {
                closeQuietly(s.player());
                continue;
            }
            if (s.busy) {
                continue;
            }
            if (now - s.lastActivity > st.idleTimeoutSeconds * 1000L) {
                close(p, false);
                continue;
            }
            if (s.citizensId() != null && !s.adminTest()) {
                Location npcLoc = plugin.npcs().location(s.citizensId());
                if (npcLoc == null || npcLoc.getWorld() != p.getWorld()
                        || npcLoc.distanceSquared(p.getLocation()) > st.maxDistance * st.maxDistance) {
                    close(p, false);
                    continue;
                }
                plugin.npcs().face(s.citizensId(), p);
            }
        }
    }

    // =================================================================== 입력 처리

    /** 채팅 방식 입력 (/t, 채팅 버튼). 요청 순번은 서버가 매긴다. */
    public void input(Player p, String rawText, String buttonId) {
        DialogueSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            viewFor(p).info(p, null, "대화 중인 NPC가 없어요. NPC를 우클릭하거나 가까이 가서 /t <할 말> 을 입력하세요.");
            return;
        }
        input(p, rawText, buttonId, ++s.chatSeq);
    }

    /** 플레이어가 말했다. buttonId가 있으면 추천 버튼. seq = 요청 순번. */
    public void input(Player p, String rawText, String buttonId, int seq) {
        DialogueSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        Settings st = plugin.settings();
        long now = System.currentTimeMillis();
        s.replySeq = seq;
        if (s.busy) {
            s.view.info(p, s, "(" + s.character().name() + "이(가) 아직 대답하는 중이에요)");
            return;
        }
        if (now - s.lastInputAt < st.cooldownSeconds * 1000L) {
            s.view.info(p, s, "(조금만 천천히 말해 주세요)");
            return;
        }
        String text;
        if (buttonId != null) {
            Button b = null;
            for (Button x : st.buttons) {
                if (x.id().equals(buttonId)) {
                    b = x;
                }
            }
            if (b == null) {
                return;
            }
            text = b.label();
        } else {
            text = rawText == null ? "" : rawText.strip();
            if (text.isEmpty()) {
                return;
            }
            // 서버에서도 100자·UTF-8 400바이트·제어문자 검사
            if (!NpcTalkProtocol.validInput(text) || text.codePointCount(0, text.length()) > st.inputMaxLength) {
                s.view.info(p, s, "(말이 너무 길거나 쓸 수 없는 문자가 있어요. " + Math.min(st.inputMaxLength,
                        NpcTalkProtocol.MAX_INPUT_CHARS) + "자 이내로 말해 주세요)");
                return;
            }
        }
        s.lastActivity = now;
        s.lastInputAt = now;
        s.activeSeq = seq;
        CharacterSheet c = s.character();
        String pid = p.getUniqueId().toString();

        if (strikes.isLocked(pid, c.id(), now)) {
            say(p, s, seq, text, c.fallback("locked", st.defaultFallbacks, random), true);
            return;
        }
        if (buttonId == null && plugin.content().filter().checkInput(text) != TextFilter.Verdict.OK) {
            strike(p, s, seq, text);
            return;
        }

        // 추천 버튼: 미리 만든 대답이 충분하면 바로 (AI 호출 없음)
        int slot = plugin.npcs().currentSlot(c);
        String today = st.today().toString();
        if (buttonId != null && st.buttonCacheEnabled) {
            String cached = buttonCache.take(today, c.id(), slot, buttonId, pid, st.buttonCacheVariants);
            if (cached != null) {
                say(p, s, seq, text, cached, false);
                return;
            }
        }

        if (!plugin.budget().isAiEnabled()) {
            say(p, s, seq, text, c.fallback("busy", st.defaultFallbacks, random), false);
            return;
        }
        if (plugin.budget().status() == BudgetMath.Status.EXHAUSTED && !s.adminTest()) {
            say(p, s, seq, text, c.fallback("tired", st.defaultFallbacks, random), false);
            return;
        }
        if (!s.adminTest() && !plugin.budget().tryUseCall(pid)) {
            say(p, s, seq, text, c.fallback("tired", st.defaultFallbacks, random), false);
            return;
        }

        s.busy = true;
        s.view.thinking(p, s, seq);
        final String finalText = text;
        s.loading.whenComplete((v, ex) -> plugin.sync(() -> proceed(p, s, seq, finalText, buttonId, slot)));
    }

    /** 고정 대사로 대답 (AI 없음). */
    private void say(Player p, DialogueSession s, int seq, String playerText, String line, boolean flagged) {
        s.view.line(p, s, seq, line);
        if (!flagged) {
            addTurns(s, playerText, line);
        }
        logChat(s, playerText, line, flagged);
    }

    private void strike(Player p, DialogueSession s, int seq, String text) {
        Settings st = plugin.settings();
        CharacterSheet c = s.character();
        boolean locked = strikes.addStrike(p.getUniqueId().toString(), c.id(), System.currentTimeMillis());
        if (locked) {
            addAffinity(s, "strike", -st.strikeAffinityPenalty);
            say(p, s, seq, text, c.fallback("locked", st.defaultFallbacks, random), true);
        } else {
            say(p, s, seq, text, c.fallback("confused", st.defaultFallbacks, random), true);
        }
    }

    private void addAffinity(DialogueSession s, String source, int amount) {
        if (amount == 0 || s.adminTest() || !link().available()) {
            return;
        }
        link().addAffinity(s.player(), s.character().id(), source, amount).thenAccept(v -> {
            if (v != null && v.length >= 2) {
                s.score = v[0];
                s.heart = v[1];
            }
        });
    }

    // =================================================================== AI 호출

    private void proceed(Player p, DialogueSession s, int seq, String text, String buttonId, int slot) {
        String pid = p.getUniqueId().toString();
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != s || s.activeSeq != seq) {
            s.busy = false;
            if (!s.adminTest()) {
                plugin.budget().refundCall(pid);
            }
            return;
        }
        Settings st = plugin.settings();
        CharacterSheet c = s.character();
        long now = System.currentTimeMillis();
        BudgetMath.Status status = plugin.budget().status();

        AffinityStage stage = stage(s);
        PromptBuilder.Context x = new PromptBuilder.Context();
        x.nowMillis = now;
        x.stage = stage;
        x.playerName = s.npcNickname != null ? s.npcNickname : s.nickname != null ? s.nickname : link().playerName(p);
        x.storyChapter = s.storyChapter != null ? s.storyChapter : st.defaultStoryChapter;
        fillPlace(x, c, slot);
        x.todayMood = moodOfDay(c.id());

        List<QuestDef> quests = pickQuests(p, s, x.todayMood);
        x.allowedQuests.addAll(quests);
        HintDef hint = pickHint(p, s, stage, text, buttonId);
        if (hint != null) {
            x.allowedHints.add(hint);
        }
        List<RumorView> rumors = new ArrayList<>();
        for (RumorView r : s.rumors) {
            if (!s.usedRumors.contains(r.id()) && rumors.size() < st.rumorMaxInPrompt) {
                rumors.add(r);
            }
        }
        decideJealousy(s, rumors);
        boolean generic = buttonId != null && st.buttonCacheEnabled && quests.isEmpty() && hint == null && !s.jealous;
        x.generic = generic;
        if (!generic) {
            x.memos.addAll(s.memos);
            x.promises.addAll(s.promises);
            if (s.lastChat != null) {
                x.lastChat.addAll(s.lastChat.turns());
                x.lastChatAgo = TimeText.ago(s.lastChat.endedAt(), now);
            }
            x.rumors.addAll(rumors);
            x.jealous = s.jealous;
            x.jealousWithPromise = s.jealousWithPromise;
        }

        String instructions = PromptBuilder.instructions(plugin.content().commonPrompt(), st.worldLore,
                plugin.vibe().approvedNotes(), c, stage);
        List<Map<String, Object>> input = OpenAiClient.messages();
        String ctx = "[상황 정보 — 대사가 아님]\n" + PromptBuilder.contextMessage(c, x);
        input.add(OpenAiClient.message("user", ctx));
        int chars = instructions.length() + ctx.length() + text.length();
        if (!generic) {
            for (Turn t : s.recent) {
                input.add(t.isPlayer()
                        ? OpenAiClient.message("user", x.playerName + ": " + t.text())
                        : OpenAiClient.message("assistant", t.text()));
                chars += t.text().length() + 8;
            }
        }
        input.add(OpenAiClient.message("user", (generic ? "학생" : x.playerName) + ": " + text));

        int maxTokens = status == BudgetMath.Status.REDUCED ? st.reducedOutputTokens : st.maxOutputTokens;
        BudgetService.Reservation res = plugin.budget().reserve(s.adminTest() ? BudgetService.SYSTEM : pid,
                plugin.budget().estimate(chars, maxTokens));
        if (res == null) {
            s.busy = false;
            if (!s.adminTest()) {
                plugin.budget().refundCall(pid);
            }
            say(p, s, seq, text, c.fallback("tired", st.defaultFallbacks, random), false);
            return;
        }
        OpenAiClient.Request req = new OpenAiClient.Request(instructions, input, PromptBuilder.replySchema(),
                "npc_reply", maxTokens, "chacanpc:" + c.id() + ":" + stage.ordinal());

        Set<String> questIds = new HashSet<>();
        for (QuestDef q : quests) {
            questIds.add(q.id());
        }
        Set<String> hintIds = hint == null ? Set.of() : Set.of(hint.id());
        Set<Long> rumorIds = new HashSet<>();
        for (RumorView r : x.rumors) {
            rumorIds.add(r.id());
        }
        ReplyParser.Limits limits = new ReplyParser.Limits(st.lineMaxLength, st.memoMaxLength, questIds, hintIds, rumorIds);

        // 3초 동안 대답이 없으면 "잠깐만...", timeout-seconds 가 지나면 고정 대사로 넘기고 이 요청 결과는 화면에 안 보냄
        cancelTimers(s);
        s.stallTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            s.stallTaskId = -1;
            if (s.busy && s.activeSeq == seq && p.isOnline() && sessions.get(s.player()) == s) {
                s.view.stall(p, s, seq, c.fallback("stall", st.defaultFallbacks, random));
            }
        }, Math.max(1, st.stallSeconds) * 20L).getTaskId();
        s.timeoutTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            s.timeoutTaskId = -1;
            if (s.busy && s.activeSeq == seq && sessions.get(s.player()) == s) {
                s.busy = false;
                s.activeSeq = -1; // 늦게 온 결과는 버린다 (비용 정산은 그대로)
                if (p.isOnline()) {
                    s.view.line(p, s, seq, c.fallback("busy", st.defaultFallbacks, random));
                }
            }
        }, Math.max(2, st.timeoutSeconds) * 20L).getTaskId();

        CompletableFuture<Boolean> moderation = st.moderationEnabled && buttonId == null
                ? plugin.ai().moderate(text) : CompletableFuture.completedFuture(false);
        plugin.ai().stream(req, null)
                .thenCombine(moderation, (r, flagged) -> new Object[]{r, flagged})
                .whenComplete((pair, ex) -> {
                    OpenAiClient.Result r = pair == null ? null : (OpenAiClient.Result) pair[0];
                    // 화면과 무관하게 비용은 반드시 정산 (늦은 응답·실패 포함)
                    plugin.budget().settle(res, r == null ? null : r.usage(), r != null && r.usageKnown());
                    if (r != null) {
                        plugin.budget().recordLatency(r.firstChunkMs(), r.totalMs());
                    }
                    boolean flagged = pair != null && Boolean.TRUE.equals(pair[1]);
                    plugin.sync(() -> finish(p, s, seq, text, buttonId, slot, generic, r, flagged, limits));
                });
    }

    private void finish(Player p, DialogueSession s, int seq, String text, String buttonId, int slot, boolean generic,
                        OpenAiClient.Result r, boolean flagged, ReplyParser.Limits limits) {
        UUID uuid = s.player();
        // 창을 닫았거나·재접속했거나·다른 NPC로 바꿨거나·시간 초과로 넘긴 요청이면 화면에 보내지 않는다
        if (!p.isOnline() || sessions.get(uuid) != s || s.activeSeq != seq) {
            return;
        }
        s.busy = false;
        cancelTimers(s);
        Settings st = plugin.settings();
        CharacterSheet c = s.character();
        String pid = uuid.toString();
        if (flagged) {
            strike(p, s, seq, text);
            return;
        }
        if (r == null || !r.ok()) {
            if (r != null && r.status() == OpenAiClient.Status.NO_KEY) {
                plugin.getLogger().warning("[ChacaNPC] OpenAI API 키가 없습니다. 환경변수 CHACANPC_OPENAI_KEY 를 설정하세요.");
            } else if (r != null && st.debug) {
                plugin.getLogger().info("[ChacaNPC] AI 실패 " + r.status() + ": " + r.error());
            }
            say(p, s, seq, text, c.fallback("busy", st.defaultFallbacks, random), false);
            return;
        }
        ReplyParser.AiReply reply = ReplyParser.parse(r.text(), limits);
        if (reply == null) {
            say(p, s, seq, text, c.fallback("busy", st.defaultFallbacks, random), false);
            return;
        }
        TextFilter.Verdict out = plugin.content().filter().checkOutput(reply.line());
        if (out != TextFilter.Verdict.OK) {
            if (st.debug) {
                plugin.getLogger().info("[ChacaNPC] 대사 차단(" + out + "): " + reply.line());
            }
            s.view.line(p, s, seq, c.fallback("confused", st.defaultFallbacks, random));
            logChat(s, text, "[차단:" + out + "] " + reply.line(), true);
            return;
        }

        long now = System.currentTimeMillis();
        if (!s.adminTest()) {
            if (!generic) {
                addAffinity(s, "chat", reply.mood());
            }
            if (reply.memo() != null) {
                s.memos.add(reply.memo());
                String memo = reply.memo();
                plugin.database().run(() -> plugin.storage().addMemo(pid, c.id(), memo, st.memoMaxLines, now));
            }
            if (reply.promise() != null) {
                String promise = reply.promise();
                s.promises.add(0, promise);
                plugin.social().recordEvent(uuid, link().playerName(p), c.id(), "promise", promise);
            }
            if (reply.hint() != null) {
                String h = reply.hint();
                if (s.hints != null) {
                    Set<String> given = new HashSet<>(s.hints.given());
                    given.add(h);
                    s.hints = new Storage.HintState(given, s.hints.today() + 1);
                }
                plugin.database().run(() -> plugin.storage().addHint(pid, h, c.id(), now));
            }
            if (reply.rumorId() != null) {
                long rid = reply.rumorId();
                s.usedRumors.add(rid);
                plugin.database().run(() -> plugin.storage().markRumorMentioned(rid));
            }
        }
        s.view.line(p, s, seq, reply.line());
        addTurns(s, text, reply.line());
        logChat(s, text, reply.line(), false);

        if (generic && buttonId != null) {
            buttonCache.add(st.today().toString(), c.id(), slot, buttonId, reply.line(), pid, st.buttonCacheVariants);
        }
        if (reply.quest() != null && link().available() && link().questOfferable(p, reply.quest())) {
            String title = link().questTitle(reply.quest());
            s.questOffered = true;
            String token = Long.toString(tokens.nextLong() & Long.MAX_VALUE, 36);
            pendingQuests.put(token, new PendingQuest(uuid, c.id(), s.token(), reply.quest(), now + 5 * 60_000L));
            s.view.questOffer(p, s, seq, title.isBlank() ? reply.quest() : title, token);
        }
        if (s.adminTest()) {
            s.view.info(p, s, String.format(Locale.ROOT, "(테스트) 입력 %d (캐시 %d) / 출력 %d 토큰, 첫 글자 %dms, 전체 %dms, 기분 %+d%s%s%s",
                    r.usage().input(), r.usage().cached(), r.usage().output(), r.firstChunkMs(), r.totalMs(), reply.mood(),
                    reply.quest() == null ? "" : ", 퀘스트 " + reply.quest(),
                    reply.hint() == null ? "" : ", 힌트 " + reply.hint(),
                    reply.memo() == null ? "" : ", 메모 \"" + reply.memo() + "\""));
        }
        if (reply.end()) {
            close(p, false);
        }
    }

    private void addTurns(DialogueSession s, String playerText, String npcLine) {
        int keep = plugin.settings().recentTurns * 2;
        s.recent.add(new Turn("player", playerText));
        s.recent.add(new Turn("npc", npcLine));
        while (s.recent.size() > keep) {
            s.recent.remove(0);
        }
        s.saveable.add(new Turn("player", playerText));
        s.saveable.add(new Turn("npc", npcLine));
        int maxSave = Math.max(keep, plugin.settings().lastChatTurns * 2) * 2;
        while (s.saveable.size() > maxSave) {
            s.saveable.remove(0);
        }
    }

    private void logChat(DialogueSession s, String playerText, String npcText, boolean flagged) {
        if (s.adminTest()) {
            return;
        }
        String pid = s.player().toString();
        String name = s.playerName();
        String npc = s.character().id();
        long now = System.currentTimeMillis();
        plugin.database().run(() -> plugin.storage().addChat(pid, name, npc, playerText, npcText, flagged, now));
    }

    // =================================================================== 상황 판단

    private void fillPlace(PromptBuilder.Context x, CharacterSheet c, int slot) {
        if (slot >= 0 && slot < c.schedule().size()) {
            var entry = c.schedule().get(slot);
            var place = plugin.places().get(entry.place());
            x.placeLabel = place != null ? place.label() : entry.place();
            x.activity = entry.activity();
        }
        x.partOfDay = plugin.npcs().partOfDay(c);
    }

    /** 그날 NPC 기분: 날짜+NPC로 정해져서 하루 동안 같다. */
    public String moodOfDay(String npcId) {
        int h = Math.floorMod((plugin.settings().today().toString() + npcId).hashCode(), 100);
        return h < 30 ? "좋음" : h < 80 ? "보통" : "별로";
    }

    /** 제안 가능한 퀘스트: 판단은 MagicCodex 기존 데이터(공개·권한·진행 중 여부) + 이 NPC 쿨타임·확률. */
    private List<QuestDef> pickQuests(Player p, DialogueSession s, String mood) {
        Settings st = plugin.settings();
        CharacterSheet c = s.character();
        if (!link().available() || s.questOffered || s.quests == null || c.quests() == null || c.quests().isEmpty()) {
            return List.of();
        }
        if (s.questRoll == null) {
            double chance = "좋음".equals(mood) ? st.questGoodMoodChance : st.questChance;
            s.questRoll = s.adminTest() || random.nextDouble() < chance;
        }
        if (!s.questRoll || s.score < st.questMinScore) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        if (now - s.quests.lastAcceptedFromNpc() < st.questCooldownMinutes * 60_000L) {
            return List.of();
        }
        if (link().activeQuestCount(p) >= st.questMaxActive) {
            return List.of();
        }
        List<QuestDef> out = new ArrayList<>();
        for (String id : c.quests()) {
            if (!link().questOfferable(p, id)) {
                continue;
            }
            QuestDef local = plugin.content().quest(id);
            int minScore = local == null ? 0 : local.minScore();
            if (s.score < minScore) {
                continue;
            }
            String title = link().questTitle(id);
            String summary = local == null ? "" : local.summary();
            out.add(new QuestDef(id, title.isBlank() ? id : title, summary, minScore, List.of()));
        }
        Collections.shuffle(out, random);
        return out.size() > 2 ? out.subList(0, 2) : out;
    }

    private HintDef pickHint(Player p, DialogueSession s, AffinityStage stage, String text, String buttonId) {
        Settings st = plugin.settings();
        CharacterSheet c = s.character();
        if (s.hints == null || s.hints.today() >= st.hintDailyPerPlayer || c.hintTopics() == null
                || c.hintTopics().isEmpty()) {
            return null;
        }
        boolean asked = "magic".equals(buttonId);
        if (!asked) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String w : MAGIC_WORDS) {
                if (lower.contains(w)) {
                    asked = true;
                    break;
                }
            }
        }
        if (!asked && random.nextDouble() >= 0.25) {
            return null;
        }
        int minDifficulty = stage.ordinal() <= 1 ? 3 : stage.ordinal() == 2 ? 2 : 1;
        List<HintDef> candidates = new ArrayList<>();
        for (HintDef h : plugin.content().hints()) {
            if (!c.hintTopics().contains(h.topic()) || s.hints.given().contains(h.id()) || h.difficulty() < minDifficulty) {
                continue;
            }
            // MagicDiscovery가 준 권한으로 이미 발견한 마법인지 확인
            if (p.hasPermission(String.format(Locale.ROOT, st.discoveryPermission, h.spell()))) {
                continue;
            }
            candidates.add(h);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private void decideJealousy(DialogueSession s, List<RumorView> rumors) {
        if (s.jealousDecided) {
            return;
        }
        s.jealousDecided = true;
        Settings st = plugin.settings();
        if (s.score < st.jealousyMinScore) {
            return;
        }
        boolean romanceRumor = false;
        for (RumorView r : rumors) {
            if (r.isRomance()) {
                romanceRumor = true;
                break;
            }
        }
        if (!romanceRumor) {
            return;
        }
        String key = s.player() + "|" + s.character().id();
        String today = st.today().toString();
        if (today.equals(jealousToday.get(key))) {
            return;
        }
        boolean promise = !s.promises.isEmpty();
        double chance = promise ? st.jealousyPromiseChance : st.jealousyChance;
        if (random.nextDouble() < chance) {
            s.jealous = true;
            s.jealousWithPromise = promise;
            jealousToday.put(key, today);
            addAffinity(s, "jealousy", -1);
        }
    }

    // =================================================================== 퀘스트 수락

    /** 채팅 버튼용. */
    public void answerQuest(Player p, String token, boolean accept) {
        DialogueSession s = sessions.get(p.getUniqueId());
        answerQuest(p, token, accept, s == null ? 0 : ++s.chatSeq);
    }

    public void answerQuest(Player p, String token, boolean accept, int seq) {
        DialogueView view = viewFor(p);
        DialogueSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.replySeq = seq;
        }
        PendingQuest pq = pendingQuests.get(token);
        // 토큰은 플레이어·NPC·대화 세션·만료 시각에 묶인다. 검증을 통과한 뒤에만 (한 번) 제거한다.
        if (pq == null || !pq.player().equals(p.getUniqueId()) || s == null
                || !s.token().equals(pq.sessionToken()) || !s.character().id().equals(pq.npcId())) {
            view.info(p, s, "(이미 지난 부탁이에요)");
            return;
        }
        if (System.currentTimeMillis() > pq.expiresAt()) {
            pendingQuests.remove(token, pq);
            view.info(p, s, "(시간이 지나서 부탁이 사라졌어요)");
            return;
        }
        if (!pendingQuests.remove(token, pq)) {
            return; // 동시에 들어온 같은 토큰
        }
        CharacterSheet c = plugin.characters().get(pq.npcId());
        if (c == null) {
            return;
        }
        Settings st = plugin.settings();
        String pid = p.getUniqueId().toString();
        long offeredAt = pq.expiresAt() - 5 * 60_000L;
        if (!accept) {
            plugin.database().run(() -> plugin.storage().addQuestLog(pid, c.id(), pq.questId(), offeredAt, false, 0));
            replyQuest(p, c, seq, c.fallback("quest_decline", st.defaultFallbacks, random));
            return;
        }
        if (!link().available()) {
            view.info(p, s, "(지금은 부탁을 받을 수 없어요)");
            return;
        }
        // 수락 조건·중복·권한·보상은 MagicCodex 기존 의뢰 경로가 판단
        link().acceptQuest(p, pq.questId()).whenComplete((ok, ex) -> {
            if (!p.isOnline()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!Boolean.TRUE.equals(ok)) {
                viewFor(p).info(p, sessions.get(p.getUniqueId()), "(지금은 이 부탁을 받을 수 없어요)");
                return;
            }
            plugin.database().run(() -> plugin.storage().addQuestLog(pid, c.id(), pq.questId(), offeredAt, true, now));
            String title = link().questTitle(pq.questId());
            plugin.social().recordEvent(p.getUniqueId(), link().playerName(p), c.id(), "quest_accept",
                    link().playerName(p) + "이(가) " + c.name() + "의 부탁(" + (title.isBlank() ? pq.questId() : title) + ")을 들어주기로 함");
            DialogueSession cur = sessions.get(p.getUniqueId());
            if (cur != null && cur.quests != null && cur.character().id().equals(c.id())) {
                Set<String> acc = new HashSet<>(cur.quests.accepted());
                acc.add(pq.questId());
                cur.quests = new Storage.QuestState(now, acc, cur.quests.acceptedLastDay() + 1);
            }
            replyQuest(p, c, seq, c.fallback("quest_thanks", st.defaultFallbacks, random));
        });
    }

    private void replyQuest(Player p, CharacterSheet c, int seq, String line) {
        DialogueSession s = sessions.get(p.getUniqueId());
        if (s != null && s.character().id().equals(c.id())) {
            s.view.line(p, s, seq, line);
            s.lastActivity = System.currentTimeMillis();
        } else {
            p.sendMessage("[" + c.name() + "] " + line);
        }
    }

    // =================================================================== 버튼 대답 미리 만들기

    /** 일과표 칸이 바뀔 때 버튼 대답을 백그라운드에서 채운다 (예산 예약 포함). */
    public void warmup(CharacterSheet c, int slot) {
        Settings st = plugin.settings();
        if (!st.buttonCacheEnabled || !st.buttonCacheWarmup || !plugin.budget().isAiEnabled()
                || plugin.budget().status() != BudgetMath.Status.NORMAL) {
            return;
        }
        String today = st.today().toString();
        int delay = 0;
        for (Button b : st.buttons) {
            int have = buttonCache.size(today, c.id(), slot, b.id());
            int need = Math.min(2, st.buttonCacheVariants - have);
            for (int i = 0; i < need; i++) {
                delay += 20 + random.nextInt(40);
                Bukkit.getScheduler().runTaskLater(plugin, () -> generateGeneric(c, slot, b), delay);
            }
        }
    }

    private void generateGeneric(CharacterSheet c, int slot, Button b) {
        Settings st = plugin.settings();
        if (plugin.budget().status() != BudgetMath.Status.NORMAL) {
            return;
        }
        PromptBuilder.Context x = new PromptBuilder.Context();
        x.generic = true;
        x.nowMillis = System.currentTimeMillis();
        fillPlace(x, c, slot);
        x.todayMood = moodOfDay(c.id());
        AffinityStage stage = AffinityStage.ACQUAINTANCE;
        String instructions = PromptBuilder.instructions(plugin.content().commonPrompt(), st.worldLore,
                plugin.vibe().approvedNotes(), c, stage);
        String ctx = "[상황 정보 — 대사가 아님]\n" + PromptBuilder.contextMessage(c, x);
        List<Map<String, Object>> input = OpenAiClient.messages();
        input.add(OpenAiClient.message("user", ctx));
        input.add(OpenAiClient.message("user", "학생: " + b.label()));
        BudgetService.Reservation res = plugin.budget().reserve(BudgetService.SYSTEM,
                plugin.budget().estimate(instructions.length() + ctx.length() + 20, st.maxOutputTokens));
        if (res == null) {
            return;
        }
        OpenAiClient.Request req = new OpenAiClient.Request(instructions, input, PromptBuilder.replySchema(),
                "npc_reply", st.maxOutputTokens, "chacanpc:" + c.id() + ":" + stage.ordinal());
        ReplyParser.Limits limits = new ReplyParser.Limits(st.lineMaxLength, st.memoMaxLength, Set.of(), Set.of(), Set.of());
        plugin.ai().stream(req, null).whenComplete((r, ex) -> {
            plugin.budget().settle(res, r == null ? null : r.usage(), r != null && r.usageKnown());
            plugin.sync(() -> {
                if (r == null || !r.ok()) {
                    return;
                }
                ReplyParser.AiReply reply = ReplyParser.parse(r.text(), limits);
                if (reply != null && plugin.content().filter().checkOutput(reply.line()) == TextFilter.Verdict.OK) {
                    buttonCache.add(st.today().toString(), c.id(), slot, b.id(), reply.line(), null, st.buttonCacheVariants);
                }
            });
        });
    }
}
