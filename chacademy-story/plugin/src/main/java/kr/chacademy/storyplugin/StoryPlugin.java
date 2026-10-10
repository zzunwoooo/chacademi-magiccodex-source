package kr.chacademy.storyplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 차카데미 메인 스토리 서버 쪽.
 * <ul>
 *   <li>/cutscene play &lt;플레이어&gt; &lt;id&gt; → 컷신, 끝나면 config on-finish 명령</li>
 *   <li>/storydialogue &lt;플레이어&gt; &lt;id&gt; → 대화 (콘솔 가능), 선택지 이벤트·끝마다 dialogues/&lt;id&gt;.yml 명령</li>
 *   <li>/affinity → 호감도. MagicCodexBridge 가 있으면 그 값 (ChacaNPC 와 같음), 없으면 affinity.yml</li>
 *   <li>대사의 {player} = MagicCodex 한글 닉네임, config 의 dialogue-placeholders = PlaceholderAPI 값</li>
 * </ul>
 * 대화 내용(대사·그림)은 클라 모드의 config/chaca_dialogue 에 있고, 서버는 명령어 파일만 가진다.
 */
public class StoryPlugin extends JavaPlugin implements PluginMessageListener, TabExecutor, Listener {
    private static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{1,64}");

    private final Map<UUID, String> cutscenePlaying = new ConcurrentHashMap<>();
    private final Map<UUID, DialogueSession> dialoguePlaying = new ConcurrentHashMap<>();
    private AffinityStore affinity;
    private MagicCodexLink codex;

    /** 진행 중인 대화. progress.yml 에 저장돼서 접속이 끊기거나 서버가 꺼져도 이어서 연다. */
    private static final class DialogueSession {
        final String id;
        final Set<String> fired = new HashSet<>();
        int affinityChanged = 0;
        String scene = "";
        int line = 0;

        DialogueSession(String id) {
            this.id = id;
        }
    }

    private File progressFile;
    private volatile boolean progressDirty = false;
    private boolean progressReady;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "dialogues").mkdirs();
        affinity = new AffinityStore(new File(getDataFolder(), "affinity.yml"));
        applyAffinityConfig();
        affinity.load();
        progressFile = new File(getDataFolder(), "progress.yml");
        loadProgress();
        progressReady = true;

        Messenger m = getServer().getMessenger();
        for (String out : List.of(StoryCodec.CUTSCENE_PLAY, StoryCodec.CUTSCENE_STOP, StoryCodec.DIALOGUE_OPEN, StoryCodec.DIALOGUE_STOP)) {
            m.registerOutgoingPluginChannel(this, out);
        }
        for (String in : List.of(StoryCodec.CUTSCENE_DONE, StoryCodec.DIALOGUE_EVENT, StoryCodec.DIALOGUE_DONE, StoryCodec.DIALOGUE_PROGRESS)) {
            m.registerIncomingPluginChannel(this, in, this);
        }
        for (String c : List.of("cutscene", "storydialogue", "affinity")) {
            var cmd = getCommand(c);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, affinity::saveIfDirty, 100, 100);
        Bukkit.getScheduler().runTaskTimer(this, this::saveProgressIfDirty, 100, 100);
        // /reload 등으로 켜졌을 때 이미 접속해 있는 사람도 이어서
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.getOnlinePlayers().forEach(p -> resumeLater(p, 0)), 40);
        // MagicCodexBridge 가 서비스를 등록한 뒤 연결 (서버가 다 켜진 다음 틱)
        codex = new MagicCodexLink(getLogger());
        Bukkit.getScheduler().runTask(this, () -> {
            if (!codex.connect()) getLogger().info("MagicCodexBridge 없음 — 호감도는 affinity.yml, {player} 는 PlaceholderAPI 또는 마인크래프트 닉네임");
        });
        getLogger().info("차카데미 스토리 준비 완료");
    }

    @Override
    public void onDisable() {
        Messenger m = getServer().getMessenger();
        m.unregisterIncomingPluginChannel(this);
        m.unregisterOutgoingPluginChannel(this);
        if (affinity != null) affinity.saveIfDirty();
        progressDirty = true;
        saveProgressIfDirty();
        cutscenePlaying.clear();
        dialoguePlaying.clear();
    }

    private void applyAffinityConfig() {
        affinity.limits(getConfig().getInt("affinity-default", 25), getConfig().getInt("affinity-min", -100),
                getConfig().getInt("affinity-max", 100));
    }

    public AffinityStore affinity() {
        return affinity;
    }

    // ================================================================ 모드 확인

    public boolean hasMod(Player p) {
        return p.getListeningPluginChannels().contains(StoryCodec.CUTSCENE_PLAY);
    }

    private boolean missingMod(Player p) {
        if (hasMod(p)) return false;
        String msg = getConfig().getString("missing-mod-message", "");
        if (msg != null && !msg.isEmpty()) p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        return true;
    }

    // ================================================================ 컷신

    public void playCutscene(Player p, String id, int mode) {
        if (missingMod(p)) {
            if (getConfig().getBoolean("finish-if-missing-mod", true)) cutsceneFinished(p, id, true, true);
            return;
        }
        cutscenePlaying.put(p.getUniqueId(), id);
        p.sendPluginMessage(this, StoryCodec.CUTSCENE_PLAY, StoryCodec.cutscenePlay(id, mode));
    }

    public void stopCutscene(Player p) {
        cutscenePlaying.remove(p.getUniqueId());
        if (hasMod(p)) p.sendPluginMessage(this, StoryCodec.CUTSCENE_STOP, new byte[0]);
    }

    private void cutsceneFinished(Player p, String id, boolean skipped, boolean missing) {
        Bukkit.getPluginManager().callEvent(new StoryEvents.CutsceneFinish(p, id, skipped, missing));
        run(getConfig().getStringList("on-finish." + id), p, Map.of("{id}", id, "{skipped}", String.valueOf(skipped)));
    }

    // ================================================================ 대화

    public void openDialogue(Player p, String id) {
        if (missingMod(p)) {
            if (getConfig().getBoolean("finish-if-missing-mod", true)) dialogueFinished(p, id, "", true);
            return;
        }
        DialogueSession session = new DialogueSession(id);
        DialogueSession previous = dialoguePlaying.put(p.getUniqueId(), session);
        progressDirty = true;
        if (!saveProgressIfDirty()) {
            if (previous == null) dialoguePlaying.remove(p.getUniqueId()); else dialoguePlaying.put(p.getUniqueId(), previous);
            progressDirty = true;
            p.sendMessage(ChatColor.RED + "스토리 진행을 저장하지 못했습니다. 다시 시도해 주세요.");
            return;
        }
        sendOpen(p, session);
    }

    /** 대화 화면을 연다. session.scene 이 있으면 거기서부터 (이어서 보기). */
    private void sendOpen(Player p, DialogueSession session) {
        String id = session.id;
        String vars = StoryCodec.encodeVars(textVars(p));
        if (!codex.available()) {
            p.sendPluginMessage(this, StoryCodec.DIALOGUE_OPEN,
                    StoryCodec.dialogueOpen(id, affinity.encode(p.getUniqueId()), vars, session.scene, session.line));
            return;
        }
        // MagicCodex 호감도: 이 대화에 나오는 NPC (서버 yml 의 npcs) 만 물어본다
        List<String> npcs = dialogueCommands(id).getStringList("npcs").stream()
                .filter(n -> n.matches("[^\\s,=]{1,48}")).distinct().limit(16).toList();
        Map<String, Integer> scores = new java.util.TreeMap<>();
        CompletableFuture<?>[] all = npcs.stream().map(n -> codex.affinity(p.getUniqueId(), n)
                .thenAccept(v -> { if (v != null) scores.put(n, v); })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(all).orTimeout(3, TimeUnit.SECONDS).handle((r, e) -> {
            Bukkit.getScheduler().runTask(this, () -> {
                if (!isEnabled() || !p.isOnline() || Bukkit.getPlayer(p.getUniqueId()) != p || dialoguePlaying.get(p.getUniqueId()) != session) return;
                StringBuilder sb = new StringBuilder();
                scores.forEach((k, v) -> sb.append(sb.length() > 0 ? "," : "").append(k).append('=').append(v));
                p.sendPluginMessage(this, StoryCodec.DIALOGUE_OPEN, StoryCodec.dialogueOpen(id, sb.toString(), vars, session.scene, session.line));
            });
            return null;
        });
    }

    /** 대사에서 바꿔 넣을 글자. {player} = 한글 닉네임, {account} = 마인크래프트 닉네임, %...% = PlaceholderAPI. */
    private Map<String, String> textVars(Player p) {
        Map<String, String> v = new java.util.LinkedHashMap<>();
        v.put("{player}", codex.nickname(p));
        v.put("{account}", p.getName());
        for (String ph : getConfig().getStringList("dialogue-placeholders")) {
            if (ph == null || !ph.matches("%[A-Za-z0-9_\\-:.]{1,60}%") || v.size() >= 40) continue;
            String r = codex.placeholder(p, ph);
            if (r != null && !r.equals(ph)) v.put(ph, ChatColor.stripColor(r));
        }
        return v;
    }

    public MagicCodexLink codex() {
        return codex;
    }

    // ---------------------------------------------------------------- 이어서 보기

    /** 접속하면 끝나지 않은 대화를 저장된 곳부터 다시 연다. 모드 채널이 늦게 잡히므로 몇 번 기다린다. */
    private void resumeLater(Player p, int attempt) {
        DialogueSession s = dialoguePlaying.get(p.getUniqueId());
        if (s == null || !p.isOnline() || Bukkit.getPlayer(p.getUniqueId()) != p) return;
        if (hasMod(p)) {
            getLogger().info(p.getName() + " 대화 이어서 열기: " + s.id + " (" + (s.scene.isEmpty() ? "처음" : s.scene + " #" + s.line) + ")");
            sendOpen(p, s);
            return;
        }
        if (attempt < 15) Bukkit.getScheduler().runTaskLater(this, () -> resumeLater(p, attempt + 1), 20);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (dialoguePlaying.containsKey(p.getUniqueId())) Bukkit.getScheduler().runTaskLater(this, () -> resumeLater(p, 0), 60);
    }

    private void loadProgress() {
        dialoguePlaying.clear();
        if (!progressFile.isFile()) return;
        YamlConfiguration y = new YamlConfiguration();
        try { y.load(progressFile); } catch (Exception e) { throw new IllegalStateException("Cannot read story progress; preserving the existing file", e); }
        for (String key : y.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String id = y.getString(key + ".id", "");
                if (!ID.matcher(id).matches()) continue;
                DialogueSession s = new DialogueSession(id);
                s.scene = y.getString(key + ".scene", "");
                s.line = Math.max(0, y.getInt(key + ".line", 0));
                s.affinityChanged = y.getInt(key + ".affinity-changed", 0);
                s.fired.addAll(y.getStringList(key + ".fired"));
                dialoguePlaying.put(uuid, s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!dialoguePlaying.isEmpty()) getLogger().info("끝나지 않은 대화 " + dialoguePlaying.size() + "개 (접속하면 이어서 열림)");
    }

    private boolean saveProgressIfDirty() {
        if (!progressReady || progressFile == null) return false;
        if (!progressDirty) return true;
        progressDirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("끝나지 않은 스토리 대화 (접속하면 여기서부터 다시 열림). 직접 고치지 마세요."));
        for (var e : dialoguePlaying.entrySet()) {
            String k = e.getKey().toString();
            DialogueSession s = e.getValue();
            y.set(k + ".id", s.id);
            y.set(k + ".scene", s.scene);
            y.set(k + ".line", s.line);
            y.set(k + ".affinity-changed", s.affinityChanged);
            y.set(k + ".fired", new ArrayList<>(new java.util.TreeSet<>(s.fired)));
        }
        try {
            StorySafety.atomicWrite(progressFile.toPath(), y.saveToString());
            return true;
        } catch (java.io.IOException ex) {
            progressDirty = true;
            getLogger().warning("progress.yml 저장 실패; 스토리 효과를 실행하지 않습니다.");
            return false;
        }
    }

    // ---------------------------------------------------------------- 호감도 (서버 파일 기준)

    /** {npc: 값} 목록을 적용한다. 서버 파일에서 온 값이라 그대로 믿는다 (±100 으로만 자름). */
    private void applyAffinity(Player p, org.bukkit.configuration.ConfigurationSection sec, String why) {
        if (sec == null) return;
        for (String npc : sec.getKeys(false)) {
            int v = Math.max(-100, Math.min(100, sec.getInt(npc, 0)));
            if (v == 0 || !npc.matches("[^\\s,=]{1,48}")) continue;
            if (codex.available()) codex.addAffinity(p.getUniqueId(), npc, v);
            else affinity.add(p.getUniqueId(), npc, v);
            if (getConfig().getBoolean("affinity-log", false)) getLogger().info(p.getName() + " 호감도 " + npc + " " + (v > 0 ? "+" : "") + v + " (" + why + ")");
        }
    }

    public void stopDialogue(Player p) {
        DialogueSession previous = dialoguePlaying.remove(p.getUniqueId());
        progressDirty = true;
        if (!saveProgressIfDirty()) {
            if (previous != null) dialoguePlaying.put(p.getUniqueId(), previous);
            progressDirty = true;
            return;
        }
        if (hasMod(p)) p.sendPluginMessage(this, StoryCodec.DIALOGUE_STOP, new byte[0]);
    }

    /** plugins/ChacademyStory/dialogues/&lt;id&gt;.yml (편집기의 server_commands.yml). */
    private YamlConfiguration dialogueCommands(String id) {
        File f = new File(new File(getDataFolder(), "dialogues"), id + ".yml");
        return f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    private void dialogueFinished(Player p, String id, String lastScene, boolean missing) {
        Bukkit.getPluginManager().callEvent(new StoryEvents.DialogueFinish(p, id, lastScene, missing));
        YamlConfiguration y = dialogueCommands(id);
        if (!lastScene.isEmpty()) applyAffinity(p, y.getConfigurationSection("affinity.endings." + lastScene), id + " 결말 " + lastScene);
        applyAffinity(p, y.getConfigurationSection("affinity.end"), id + " 끝");
        Map<String, String> vars = Map.of("{id}", id, "{scene}", lastScene);
        if (!lastScene.isEmpty()) run(y.getStringList("endings." + lastScene), p, vars);
        run(y.getStringList("end"), p, vars);
    }

    // ================================================================ 모드에서 온 신호

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        if (!p.isOnline() || Bukkit.getPlayer(p.getUniqueId()) != p) return;
        try {
            switch (channel) {
                case StoryCodec.CUTSCENE_DONE -> {
                    var d = StoryCodec.cutsceneDone(message);
                    // 서버가 재생시킨 것만 인정 (클라가 마음대로 퀘스트를 넘기지 못하게)
                    if (!d.id().equals(cutscenePlaying.get(p.getUniqueId()))) return;
                    cutscenePlaying.remove(p.getUniqueId());
                    cutsceneFinished(p, d.id(), d.skipped(), false);
                }
                case StoryCodec.DIALOGUE_EVENT -> {
                    var e = StoryCodec.dialogueEvent(message);
                    DialogueSession s = dialoguePlaying.get(p.getUniqueId());
                    if (s == null || !s.id.equals(e.id())) return;
                    YamlConfiguration y = dialogueCommands(e.id());
                    var commands = y.getConfigurationSection("events");
                    var affinityEvents = y.getConfigurationSection("affinity.events");
                    if (!StorySafety.declaredEvent(e.event(), commands == null ? Set.of() : commands.getKeys(false),
                            affinityEvents == null ? Set.of() : affinityEvents.getKeys(false))) return;
                    // Never trust client npc/add fields. Persist before any side effect.
                    StorySafety.once(s.fired, e.event(), () -> {
                        progressDirty = true;
                        return saveProgressIfDirty();
                    }, () -> {
                        applyAffinity(p, y.getConfigurationSection("affinity.events." + e.event()), e.id() + " " + e.event());
                        Bukkit.getPluginManager().callEvent(new StoryEvents.DialogueChoice(p, e.id(), e.event()));
                        run(y.getStringList("events." + e.event()), p, Map.of("{id}", e.id(), "{event}", e.event()));
                    });
                }
                case StoryCodec.DIALOGUE_PROGRESS -> {
                    var g = StoryCodec.dialogueProgress(message);
                    DialogueSession s = dialoguePlaying.get(p.getUniqueId());
                    if (s == null || !s.id.equals(g.id()) || !ID.matcher(g.scene()).matches() || g.line() < 0 || g.line() > 10000) return;
                    s.scene = g.scene();
                    s.line = g.line();
                    progressDirty = true;
                }
                case StoryCodec.DIALOGUE_DONE -> {
                    var d = StoryCodec.dialogueDone(message);
                    DialogueSession s = dialoguePlaying.get(p.getUniqueId());
                    if (s == null || !s.id.equals(d.id())) return;
                    if (!ID.matcher(d.lastScene()).matches() || !d.lastScene().equals(s.scene)) return;
                    dialoguePlaying.remove(p.getUniqueId());
                    progressDirty = true;
                    if (!saveProgressIfDirty()) {
                        dialoguePlaying.put(p.getUniqueId(), s);
                        progressDirty = true;
                        return;
                    }
                    dialogueFinished(p, d.id(), d.lastScene(), false);
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ex) {
            getLogger().warning(p.getName() + " 의 잘못된 스토리 패킷 (" + channel + "): " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cutscenePlaying.remove(e.getPlayer().getUniqueId());
        // 대화는 지우지 않는다: 다시 접속하면 저장된 곳부터 이어서 연다
        saveProgressIfDirty();
    }

    private void run(List<String> commands, Player p, Map<String, String> vars) {
        for (String raw : commands) {
            // 명령어의 {player} 는 마인크래프트 닉네임 그대로 (명령어 대상이므로). 한글 닉네임은 {nickname}
            String c = raw.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString());
            if (c.contains("{nickname}")) c = c.replace("{nickname}", codex.nickname(p));
            for (var v : vars.entrySet()) c = c.replace(v.getKey(), v.getValue());
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
    }

    // ================================================================ 명령어

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (!s.hasPermission("chacademy.story.admin")) {
            s.sendMessage(ChatColor.RED + "스토리 관리자 권한이 필요합니다."); return true;
        }
        return switch (cmd.getName()) {
            case "cutscene" -> cutsceneCommand(s, a);
            case "storydialogue" -> dialogueCommand(s, a);
            case "affinity" -> affinityCommand(s, a);
            default -> false;
        };
    }

    private boolean cutsceneCommand(CommandSender s, String[] a) {
        if (a.length == 0) return false;
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                applyAffinityConfig();
                codex.resetPlaceholderLookup();
                if (!codex.available()) codex.connect();
                s.sendMessage(ChatColor.GREEN + "스토리 설정을 다시 불러왔어요.");
            }
            case "play" -> {
                if (a.length < 3) return false;
                if (!ID.matcher(a[2]).matches()) {
                    s.sendMessage(ChatColor.RED + "컷신 id는 소문자, 숫자, _, - 만: " + a[2]);
                    return true;
                }
                int mode = a.length >= 4 ? switch (a[3].toLowerCase(Locale.ROOT)) {
                    case "skip" -> StoryCodec.MODE_SKIP;
                    case "noskip" -> StoryCodec.MODE_NOSKIP;
                    default -> StoryCodec.MODE_AUTO;
                } : StoryCodec.MODE_AUTO;
                List<Player> t = targets(s, a[1]);
                t.forEach(p -> playCutscene(p, a[2], mode));
                s.sendMessage(ChatColor.GRAY + "컷신 " + a[2] + " 재생: " + t.size() + "명");
            }
            case "stop" -> {
                if (a.length < 2) return false;
                List<Player> t = targets(s, a[1]);
                t.forEach(this::stopCutscene);
                s.sendMessage(ChatColor.GRAY + "컷신 멈춤: " + t.size() + "명");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean dialogueCommand(CommandSender s, String[] a) {
        if (a.length < 2) return false;
        if (a[0].equalsIgnoreCase("stop")) {
            List<Player> t = targets(s, a[1]);
            t.forEach(this::stopDialogue);
            s.sendMessage(ChatColor.GRAY + "대화 닫음: " + t.size() + "명");
            return true;
        }
        String id = a[1];
        if (!ID.matcher(id).matches()) {
            s.sendMessage(ChatColor.RED + "대화 id는 소문자, 숫자, _, - 만: " + id);
            return true;
        }
        List<Player> t = targets(s, a[0]);
        if (t.isEmpty()) {
            s.sendMessage(ChatColor.RED + "대상 플레이어가 없어요: " + a[0]);
            return true;
        }
        t.forEach(p -> openDialogue(p, id));
        s.sendMessage(ChatColor.GRAY + "대화 " + id + " 열기: " + t.size() + "명");
        return true;
    }

    private boolean affinityCommand(CommandSender s, String[] a) {
        if (a.length == 0) return false;
        @SuppressWarnings("deprecation") OfflinePlayer op = Bukkit.getOfflinePlayer(a[0]);
        UUID id = op.getUniqueId();
        String where = codex.available() ? " (MagicCodex)" : " (affinity.yml)";
        if (a.length == 1) {
            if (codex.available()) {
                s.sendMessage(ChatColor.GOLD + "NPC 를 지정해 주세요: /affinity " + a[0] + " <npc>" + where);
                return true;
            }
            var all = affinity.all(id);
            s.sendMessage(ChatColor.GOLD + a[0] + " 호감도 (기본 " + affinity.defaultScore() + "): "
                    + (all.isEmpty() ? "기록 없음" : all.toString()) + where);
            return true;
        }
        String npc = a[1];
        if (a.length == 2) {
            if (codex.available()) codex.affinity(id, npc).thenAccept(v ->
                    s.sendMessage(ChatColor.GOLD + a[0] + " → " + npc + ": " + (v == null ? "읽기 실패" : v) + where));
            else s.sendMessage(ChatColor.GOLD + a[0] + " → " + npc + ": " + affinity.get(id, npc) + where);
            return true;
        }
        if (a.length < 4) return false;
        int v;
        try {
            v = Integer.parseInt(a[3]);
        } catch (NumberFormatException e) {
            s.sendMessage(ChatColor.RED + "숫자를 넣어 주세요: " + a[3]);
            return true;
        }
        boolean set = a[2].equalsIgnoreCase("set");
        final int value = v;
        if (codex.available()) {
            // MagicCodex 에는 set 이 없어서 지금 값과의 차이만큼 더한다 (하트 단계 상한은 그대로 적용)
            CompletableFuture<Integer> f;
            if (set) {
                f = codex.affinity(id, npc).thenCompose(cur -> cur == null
                        ? CompletableFuture.<Integer>completedFuture(null)
                        : codex.addAffinity(id, npc, value - cur));
            } else {
                f = codex.addAffinity(id, npc, value);
            }
            f.thenAccept(now -> s.sendMessage(ChatColor.GOLD + a[0] + " → " + npc + ": " + (now == null ? "바꾸기 실패" : now) + where));
            return true;
        }
        int now = set ? affinity.set(id, npc, v) : affinity.add(id, npc, v);
        s.sendMessage(ChatColor.GOLD + a[0] + " → " + npc + ": " + now + where);
        return true;
    }

    private List<Player> targets(CommandSender s, String arg) {
        List<Player> out = new ArrayList<>();
        try {
            for (Entity e : Bukkit.selectEntities(s, arg)) if (e instanceof Player p) out.add(p);
        } catch (IllegalArgumentException e) {
            Player p = Bukkit.getPlayerExact(arg);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        if (!s.hasPermission("chacademy.story.admin") || a.length == 0) return List.of();
        List<String> o = new ArrayList<>();
        List<String> players = new ArrayList<>(List.of("@a", "@p"));
        Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
        switch (cmd.getName()) {
            case "cutscene" -> {
                if (a.length == 1) o.addAll(List.of("play", "stop", "reload"));
                else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) o.addAll(players);
                else if (a.length == 3 && a[0].equalsIgnoreCase("play")) o.addAll(getConfig().getStringList("cutscenes"));
                else if (a.length == 4 && a[0].equalsIgnoreCase("play")) o.addAll(List.of("auto", "skip", "noskip"));
            }
            case "storydialogue" -> {
                if (a.length == 1) {
                    o.add("stop");
                    o.addAll(players);
                } else if (a.length == 2 && a[0].equalsIgnoreCase("stop")) o.addAll(players);
                else if (a.length == 2) {
                    String[] files = new File(getDataFolder(), "dialogues").list((d, n) -> n.endsWith(".yml"));
                    if (files != null) for (String f : files) o.add(f.substring(0, f.length() - 4));
                }
            }
            case "affinity" -> {
                if (a.length == 1) Bukkit.getOnlinePlayers().forEach(p -> o.add(p.getName()));
                else if (a.length == 3) o.addAll(List.of("add", "set"));
            }
            default -> {
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        o.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return o;
    }
}
