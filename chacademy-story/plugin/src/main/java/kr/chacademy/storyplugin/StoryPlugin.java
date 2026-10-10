package kr.chacademy.storyplugin;

import kr.chacademy.storyplugin.StoryState.Effect;
import kr.chacademy.storyplugin.StoryState.Kind;
import kr.chacademy.storyplugin.StoryState.PlayerStory;
import kr.chacademy.storyplugin.StoryState.Request;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * 차카데미 메인 스토리 서버 쪽.
 * <ul>
 *   <li>/cutscene play &lt;플레이어&gt; &lt;id&gt; → 컷신, 끝나면 config on-finish 명령</li>
 *   <li>/storydialogue &lt;플레이어&gt; &lt;id&gt; → 대화 (콘솔 가능), 선택지 이벤트·끝마다 서버 명령어 파일의 명령</li>
 *   <li>/affinity → 호감도. MagicCodexBridge 가 있으면 그 값 (ChacaNPC 와 같음), 없으면 affinity.yml</li>
 *   <li>대사의 {player} = MagicCodex 한글 닉네임, config 의 dialogue-placeholders = PlaceholderAPI 값</li>
 * </ul>
 * 구조:
 * <ul>
 *   <li>재생 요청은 플레이어별 대기열에 들어가고 한 번에 하나만 재생된다 (컷신 중에 온 대화, 대화 중에 온 대화 모두 차례를 기다림).
 *       모드 채널이 잡힐 때까지 기다렸다가 보내며, 끝날 때까지 progress.yml 에 남아 다시 접속하면 이어진다.</li>
 *   <li>대화 흐름은 서버의 dialogues/&lt;id&gt;/dialogue.yml 로 검증한다 ({@link StoryGraph.Walk}).</li>
 *   <li>효과 (명령어·호감도) 는 "기록을 디스크에 쓴 뒤" 실행한다. 파일 쓰기는 {@link StoryStore} 의 쓰기 스레드가 한다.</li>
 *   <li>보는 동안 플레이어 보호는 {@link StoryProtection}.</li>
 * </ul>
 * 모든 상태는 메인 스레드에서만 건드린다.
 */
public class StoryPlugin extends JavaPlugin implements PluginMessageListener, TabExecutor, Listener {
    private static final Pattern ID = StorySafety.ID;
    /** 접속 직후 이만큼은 기다렸다가 스토리를 보낸다 (화면이 다 뜨기 전에 보내지 않게). */
    private static final long JOIN_DELAY_MS = 2000;
    /** 접속 뒤 이 시간까지 모드 채널이 안 잡히면 "모드 없음" 으로 본다 (채널 등록은 접속 뒤 몇 틱 늦게 온다). */
    private static final long WAIT_MS = 15000;
    /** 한 번의 대화에서 흐름에 맞지 않는 패킷이 이만큼 오면 대화를 멈춘다. */
    private static final int ILLEGAL_LIMIT = 20;
    private static final char NO_PATH_SEPARATOR = '\u0001';
    private static final long MAX_YML_BYTES = 2L * 1024 * 1024;

    private StoryState state = new StoryState();
    private StoryStore store;
    /** 대화 정의 (흐름 + 명령어). reload 때 통째로 바꾼다. */
    private Map<String, StoryDef> defs = Map.of();
    /** 파일이 있지만 읽을 수 없는 대화 id → 이유. */
    private Map<String, String> brokenDefs = Map.of();
    private final Map<UUID, StorySafety.TokenBucket> buckets = new HashMap<>();
    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private final Set<UUID> helloSent = new HashSet<>();
    private final Map<UUID, Long> lastPacketLog = new HashMap<>();
    private AffinityStore affinity;
    private MagicCodexLink codex;
    private StoryProtection protection;

    private File progressFile;
    private boolean ready;
    private boolean dirty;
    private boolean flushScheduled;
    private final List<Runnable> afterDurable = new ArrayList<>();
    private int beat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File dialogues = new File(getDataFolder(), "dialogues");
        dialogues.mkdirs();
        affinity = new AffinityStore(new File(getDataFolder(), "affinity.yml"), getLogger());
        applyAffinityConfig();
        affinity.load();
        codex = new MagicCodexLink(getLogger());
        protection = new StoryProtection(() -> getConfig().getBoolean("protect-during-story", true),
                () -> Math.max(0, getConfig().getLong("protect-max-seconds", 900)) * 1000L);
        progressFile = new File(getDataFolder(), "progress.yml");
        loadProgress();
        store = new StoryStore(text -> StorySafety.atomicWrite(progressFile.toPath(), text), StoryStore.newWorker(),
                r -> Bukkit.getScheduler().runTask(this, r), getLogger());
        ready = true;
        applyDefs(loadDefs(dialogues), null);

        Messenger m = getServer().getMessenger();
        for (String out : List.of(StoryCodec.CUTSCENE_PLAY, StoryCodec.CUTSCENE_STOP, StoryCodec.DIALOGUE_OPEN, StoryCodec.DIALOGUE_STOP,
                StoryCodec.STORY_HELLO)) {
            m.registerOutgoingPluginChannel(this, out);
        }
        for (String in : List.of(StoryCodec.CUTSCENE_DONE, StoryCodec.DIALOGUE_EVENT, StoryCodec.DIALOGUE_DONE, StoryCodec.DIALOGUE_PROGRESS,
                StoryCodec.CUTSCENE_ABORT, StoryCodec.STORY_FAIL)) {
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
        getServer().getPluginManager().registerEvents(protection, this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, affinity::saveIfDirty, 100, 100);
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20, 20);
        // /reload 등으로 켜졌을 때 이미 접속해 있는 사람도 이어서
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) joinedAt.put(p.getUniqueId(), now);
        // MagicCodexBridge 가 서비스를 등록한 뒤 연결 (서버가 다 켜진 다음 틱)
        Bukkit.getScheduler().runTask(this, () -> {
            if (!codex.connect()) getLogger().info("MagicCodexBridge 없음 — 호감도는 affinity.yml, {player} 는 PlaceholderAPI 또는 마인크래프트 닉네임");
        });
        if (getConfig().getBoolean("finish-if-missing-mod", false)) {
            getLogger().warning("finish-if-missing-mod: true — 모드가 없는 (또는 모드 채널을 숨긴) 플레이어는 스토리를 보지 않고도 끝 명령·호감도를 받습니다.");
        }
        getLogger().info("차카데미 스토리 준비 완료 (프로토콜 " + StoryCodec.PROTOCOL + ")");
    }

    @Override
    public void onDisable() {
        Messenger m = getServer().getMessenger();
        m.unregisterIncomingPluginChannel(this);
        m.unregisterOutgoingPluginChannel(this);
        if (affinity != null) affinity.saveIfDirty();
        // 서버가 꺼질 때만 이 스레드에서 바로 쓴다
        if (ready && store != null) {
            Map<String, Object> snap = state.snapshot();
            store.flushNow(() -> render(snap));
        }
        ready = false;
        if (protection != null) protection.clear();
        buckets.clear();
        joinedAt.clear();
        helloSent.clear();
    }

    private void applyAffinityConfig() {
        affinity.limits(getConfig().getInt("affinity-default", 25), getConfig().getInt("affinity-min", -100),
                getConfig().getInt("affinity-max", 100));
    }

    public AffinityStore affinity() {
        return affinity;
    }

    public MagicCodexLink codex() {
        return codex;
    }

    // ================================================================ 저장 (progress.yml)

    /** yml 을 읽은 것을 Map / List / 값으로만 된 나무로 바꾼다. */
    private static Object plain(Object o) {
        if (o instanceof ConfigurationSection s) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var e : s.getValues(false).entrySet()) m.put(e.getKey(), plain(e.getValue()));
            return m;
        }
        if (o instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var e : map.entrySet()) m.put(String.valueOf(e.getKey()), plain(e.getValue()));
            return m;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(plain(x));
            return out;
        }
        return o;
    }

    /** 키에 점(.)이 있어도 쪼개지지 않게 읽는다. 아무 스레드에서나 부를 수 있다. */
    private static Map<?, ?> readYaml(File f) throws Exception {
        if (f.length() > MAX_YML_BYTES) throw new java.io.IOException("파일이 너무 큼 (" + f.length() + " 바이트)");
        YamlConfiguration y = new YamlConfiguration();
        y.options().pathSeparator(NO_PATH_SEPARATOR);
        y.load(f);
        return (Map<?, ?>) plain(y);
    }

    private void loadProgress() {
        state = new StoryState();
        if (!progressFile.isFile()) return;
        Map<?, ?> root;
        try {
            root = readYaml(progressFile);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read story progress; preserving the existing file", e);
        }
        boolean legacy = !root.isEmpty() && root.get("format") == null;
        List<String> notes = new ArrayList<>();
        try {
            state = StoryState.fromMap(root, notes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cannot use story progress; preserving the existing file", e);
        }
        if (legacy) {
            // 예전 형식은 한 번 복사해 두고, 다음 저장부터 새 형식으로 쓴다
            File backup = new File(getDataFolder(), "progress.yml.v1-backup");
            try {
                if (!backup.exists()) Files.copy(progressFile.toPath(), backup.toPath());
            } catch (java.io.IOException | RuntimeException e) {
                getLogger().log(Level.WARNING, "예전 progress.yml 을 복사해 두지 못했습니다", e);
            }
            dirty = true;
        }
        for (String n : notes) getLogger().info(n);
        int active = 0, effects = 0;
        for (PlayerStory ps : state.players.values()) {
            if (ps.active != null || !ps.queue.isEmpty()) active++;
            effects += ps.effects.size();
        }
        if (active > 0) getLogger().info("끝나지 않은 스토리가 있는 플레이어 " + active + "명 (접속하면 이어서 열림)");
        if (effects > 0) getLogger().info("실행 대기 중인 스토리 효과 " + effects + "개 (접속하면 실행)");
    }

    /** 쓰기 스레드에서 불린다: 복사본만 읽는다. */
    private static String render(Map<String, Object> snap) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().pathSeparator(NO_PATH_SEPARATOR);
        y.options().setHeader(List.of("스토리 진행·완료 기록 (format 2). 서버가 켜져 있을 때 직접 고치지 마세요.",
                "active/queue = 보는 중·대기 중, done/fired/gained = 완료 기록, effects = 저장 뒤 실행 대기."));
        y.set("format", snap.get("format"));
        y.createSection("players", (Map<?, ?>) snap.get("players"));
        return y.saveToString();
    }

    /** 바뀐 내용을 다음 틱에 한 번에 저장한다. after = 그 저장이 디스크에 끝난 뒤 메인 스레드에서 실행할 것. */
    private void saveSoon(Runnable after) {
        dirty = true;
        if (after != null) afterDurable.add(after);
        if (flushScheduled || !ready) return;
        flushScheduled = true;
        Bukkit.getScheduler().runTask(this, this::flush);
    }

    private void flush() {
        flushScheduled = false;
        if (!ready || (!dirty && afterDurable.isEmpty())) return;
        dirty = false;
        Map<String, Object> snap = state.snapshot();
        List<Runnable> callbacks = new ArrayList<>(afterDurable);
        afterDurable.clear();
        store.submit(() -> render(snap), callbacks);
    }

    // ================================================================ 대화 정의 읽기 (서버 켤 때 / reload)

    private record DefLoad(Map<String, StoryDef> defs, Map<String, String> broken, List<String> warnings) {}

    /**
     * dialogues 폴더를 읽는다 (파일 I/O — 서버를 켤 때와 reload 의 비동기 작업에서만 부른다).
     * <pre>
     * dialogues/&lt;id&gt;/dialogue.yml          흐름 (편집기 zip 의 것 그대로)
     * dialogues/&lt;id&gt;/server_commands.yml   명령어·호감도 (편집기 zip 의 것 그대로)
     * dialogues/&lt;id&gt;.yml                   예전 위치의 명령어 파일 (폴더 안에 server_commands.yml 이 없을 때만 씀)
     * </pre>
     */
    private static DefLoad loadDefs(File dir) {
        Map<String, StoryDef> out = new TreeMap<>();
        Map<String, String> broken = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        Set<String> ids = new TreeSet<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isDirectory()) {
                    if (new File(f, "dialogue.yml").isFile() || new File(f, "server_commands.yml").isFile()) {
                        if (StorySafety.validId(n)) ids.add(n);
                        else warnings.add("대화 폴더 이름은 소문자·숫자·_·- 1~64자 (무시됨): " + n);
                    }
                } else if (n.endsWith(".yml")) {
                    String id = n.substring(0, n.length() - 4);
                    if (StorySafety.validId(id)) ids.add(id);
                    else warnings.add("대화 파일 이름은 소문자·숫자·_·- 1~64자 (무시됨): " + n);
                }
            }
        }
        for (String id : ids) {
            File folder = new File(dir, id);
            File graphFile = new File(folder, "dialogue.yml");
            File commandFile = new File(folder, "server_commands.yml");
            File legacy = new File(dir, id + ".yml");
            if (commandFile.isFile() && legacy.isFile()) warnings.add(id + ": " + id + "/server_commands.yml 을 쓰고 예전 위치의 " + id + ".yml 은 무시합니다");
            if (!commandFile.isFile()) commandFile = legacy;
            List<String> errors = new ArrayList<>(), notes = new ArrayList<>();
            StoryGraph graph = null;
            if (graphFile.isFile()) {
                try {
                    graph = StoryGraph.parse(id, readYaml(graphFile), errors, notes);
                } catch (Exception e) {
                    errors.add("dialogue.yml 을 읽을 수 없음: " + e.getMessage());
                }
            }
            Map<?, ?> commands = null;
            if (commandFile.isFile()) {
                try {
                    commands = readYaml(commandFile);
                } catch (Exception e) {
                    errors.add(commandFile.getName() + " 을 읽을 수 없음: " + e.getMessage());
                }
            }
            if (!errors.isEmpty()) {
                broken.put(id, summarize(errors));
                continue;
            }
            out.put(id, StoryDef.parse(id, commands, graph, notes));
            if (!notes.isEmpty()) warnings.add(id + ": " + summarize(notes));
        }
        return new DefLoad(out, broken, warnings);
    }

    private static String summarize(List<String> problems) {
        int show = Math.min(5, problems.size());
        String s = String.join(" / ", problems.subList(0, show));
        return problems.size() > show ? s + " / … 외 " + (problems.size() - show) + "건" : s;
    }

    private void applyDefs(DefLoad load, CommandSender tell) {
        defs = load.defs();
        brokenDefs = load.broken();
        boolean require = getConfig().getBoolean("require-server-graph", true);
        List<String> noGraph = new ArrayList<>();
        for (StoryDef d : defs.values()) if (d.graph == null) noGraph.add(d.id);
        for (String w : load.warnings()) getLogger().warning("[대화 파일] " + w);
        for (var e : brokenDefs.entrySet()) getLogger().severe("[대화 파일] " + e.getKey() + " 을(를) 열 수 없습니다: " + e.getValue());
        if (!noGraph.isEmpty()) {
            if (require) getLogger().warning("서버에 dialogue.yml 이 없어서 열 수 없는 대화 (dialogues/<id>/dialogue.yml 을 넣어 주세요): " + String.join(", ", noGraph));
            else getLogger().warning("require-server-graph: false — 아래 대화는 서버 검증 없이 열립니다 (선택지·결말을 클라가 정함): " + String.join(", ", noGraph));
        }
        String summary = "대화 " + defs.size() + "개 (흐름 검증 " + (defs.size() - noGraph.size()) + "개"
                + (noGraph.isEmpty() ? "" : ", dialogue.yml 없음 " + noGraph.size() + "개")
                + (brokenDefs.isEmpty() ? "" : ", 오류 " + brokenDefs.size() + "개") + ")";
        getLogger().info(summary);
        if (tell != null && !(tell instanceof ConsoleCommandSender)) {
            tell.sendMessage(ChatColor.GRAY + summary + (load.warnings().isEmpty() && brokenDefs.isEmpty() ? "" : " — 자세한 내용은 콘솔"));
        }
    }

    // ================================================================ 모드 확인

    public boolean hasMod(Player p) {
        return p.getListeningPluginChannels().contains(StoryCodec.CUTSCENE_PLAY);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void message(Player p, String key, String def) {
        String msg = getConfig().getString(key, def);
        if (msg != null && !msg.isEmpty()) p.sendMessage(color(msg));
    }

    private long joined(Player p) {
        Long t = joinedAt.get(p.getUniqueId());
        return t == null ? 0 : t;
    }

    // ================================================================ 재생 요청과 대기열

    /** 컷신 재생 요청. 지금 다른 스토리를 보는 중이면 차례를 기다린다. 끝나면 on-finish 를 요청 한 번에 한 번만 실행. */
    public void playCutscene(Player p, String id, int mode) {
        requestCutscene(null, p, id, mode);
    }

    private boolean requestCutscene(CommandSender who, Player p, String id, int mode) {
        if (!StorySafety.validId(id)) return false;
        PlayerStory ps = state.of(p.getUniqueId());
        ps.name = p.getName();
        if (ps.has(Kind.CUTSCENE, id)) {
            note(who, p.getName() + ": 컷신 " + id + " 은(는) 이미 재생 중이거나 대기 중입니다 (요청 무시)");
            return false;
        }
        if (ps.queue.size() >= StoryState.MAX_QUEUE) {
            refuse(who, p.getName() + ": 스토리 대기열이 가득 찼습니다 (" + StoryState.MAX_QUEUE + "개). 컷신 " + id + " 요청을 버립니다");
            return false;
        }
        Request r = new Request(Kind.CUTSCENE, id, ps.nextSeq());
        r.mode = mode;
        ps.queue.add(r);
        saveSoon(null);
        pump(p);
        return true;
    }

    /** 대화 열기 요청 (다시 보기 아님). 이미 끝낸 대화면 아무 일도 하지 않는다. */
    public void openDialogue(Player p, String id) {
        requestDialogue(null, p, id, false);
    }

    private boolean requestDialogue(CommandSender who, Player p, String id, boolean replayArg) {
        if (!StorySafety.validId(id)) return false;
        String brokenWhy = brokenDefs.get(id);
        if (brokenWhy != null) {
            refuse(who, "대화 " + id + " 의 서버 파일에 오류가 있어 열지 않습니다: " + brokenWhy);
            return false;
        }
        StoryDef def = defs.get(id);
        if ((def == null || def.graph == null) && getConfig().getBoolean("require-server-graph", true)) {
            refuse(who, "대화 " + id + " 을(를) 열지 않습니다: 서버에 plugins/ChacademyStory/dialogues/" + id
                    + "/dialogue.yml 이 없습니다 (넣고 /cutscene reload, 또는 config 의 require-server-graph: false)");
            return false;
        }
        PlayerStory ps = state.of(p.getUniqueId());
        ps.name = p.getName();
        StoryState.Open open = ps.decideOpen(id, replayArg, def == null ? null : def.allowReplay, getConfig().getBoolean("allow-replay", false));
        switch (open) {
            case DUPLICATE -> {
                note(who, p.getName() + ": 대화 " + id + " 은(는) 이미 진행 중이거나 대기 중입니다 (요청 무시)");
                return false;
            }
            case ALREADY_DONE -> {
                note(who, p.getName() + ": 대화 " + id + " 은(는) 이미 끝냈습니다 — 다시 열지 않고 명령어도 실행하지 않습니다 (다시 보여 주려면 /storydialogue "
                        + p.getName() + " " + id + " replay)");
                return false;
            }
            case QUEUE_FULL -> {
                refuse(who, p.getName() + ": 스토리 대기열이 가득 찼습니다 (" + StoryState.MAX_QUEUE + "개). 대화 " + id + " 요청을 버립니다");
                return false;
            }
            default -> {
            }
        }
        boolean replay = open == StoryState.Open.REPLAY;
        Request r = new Request(Kind.DIALOGUE, id, ps.nextSeq());
        r.replay = replay;
        ps.queue.add(r);
        saveSoon(null);
        pump(p);
        return true;
    }

    private void note(CommandSender who, String msg) {
        getLogger().info(msg);
        if (who != null && !(who instanceof ConsoleCommandSender)) who.sendMessage(ChatColor.GRAY + msg);
    }

    private void refuse(CommandSender who, String msg) {
        getLogger().warning(msg);
        if (who != null && !(who instanceof ConsoleCommandSender)) who.sendMessage(ChatColor.RED + msg);
    }

    private void prune(UUID id, PlayerStory ps) {
        if (ps.empty()) state.players.remove(id);
    }

    /**
     * 이 플레이어의 다음 할 일을 진행한다: 대기 중인 효과가 없고 재생 중인 것이 없으면 대기열의 다음 요청을 시작하고,
     * 아직 보내지 않은 요청이면 모드가 준비됐을 때 보낸다. 아무 때나 여러 번 불러도 된다 (1초마다도 불린다).
     */
    private void pump(Player p) {
        UUID uuid = p.getUniqueId();
        PlayerStory ps = state.players.get(uuid);
        if (ps == null || !p.isOnline() || Bukkit.getPlayer(uuid) != p) return;
        long now = System.currentTimeMillis();
        if (now < joined(p) + JOIN_DELAY_MS) return;
        // 앞 스토리의 끝 명령이 먼저 실행된 다음에 다음 스토리를 시작한다
        if (!ps.effects.isEmpty() || ps.running) return;
        if (ps.active == null) {
            Request next = ps.queue.pollFirst();
            if (next == null) {
                prune(uuid, ps);
                return;
            }
            next.resetTransient();
            ps.active = next;
            saveSoon(null);
        }
        Request a = ps.active;
        if (a.sent || a.parked || now < a.retryAtMs) return;
        int proto = StoryCodec.clientProtocol(p.getListeningPluginChannels());
        if (proto >= StoryCodec.MIN_CLIENT_PROTOCOL) {
            if (a.kind == Kind.CUTSCENE) sendCutscene(p, a);
            else sendDialogue(p, ps, a);
            return;
        }
        // 채널 등록은 접속 뒤 늦게 오므로 충분히 기다린 다음에만 "모드 없음" 으로 본다
        if (now < joined(p) + WAIT_MS) return;
        if (!a.toldMissing) {
            a.toldMissing = true;
            if (proto >= 1) message(p, "outdated-mod-message", "&c차카데미 스토리 모드가 오래된 버전입니다. 모드를 업데이트해 주세요.");
            else message(p, "missing-mod-message", "");
            getLogger().info(p.getName() + ": 스토리 모드 " + (proto >= 1 ? "버전이 낮음 (프로토콜 " + proto + " < " + StoryCodec.MIN_CLIENT_PROTOCOL + ")" : "없음")
                    + " — " + (a.kind == Kind.CUTSCENE ? "컷신 " : "대화 ") + a.id);
        }
        if (getConfig().getBoolean("finish-if-missing-mod", false)) {
            if (a.kind == Kind.CUTSCENE) finishCutscene(p, ps, a, true, true);
            else finishDialogue(p, ps, a, "", true);
        } else {
            a.parked = true; // 스토리는 넘어가지 않는다. 모드를 넣고 다시 접속하면 이어진다
        }
    }

    /** 1초마다: 채널을 기다리던 요청, 다시 보내기, 접속 뒤 밀린 효과, 보호 시간 한도, 대사 위치 저장. */
    private void heartbeat() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (!helloSent.contains(uuid) && StoryCodec.clientProtocol(p.getListeningPluginChannels()) >= 2
                    && p.getListeningPluginChannels().contains(StoryCodec.STORY_HELLO)) {
                helloSent.add(uuid);
                p.sendPluginMessage(this, StoryCodec.STORY_HELLO, StoryCodec.hello(StoryCodec.PROTOCOL));
            }
            PlayerStory ps = state.players.get(uuid);
            if (ps == null) continue;
            try {
                if (!ps.effects.isEmpty() && ps.effects.get(0).durable && now >= joined(p) + JOIN_DELAY_MS) runEffects(uuid);
                pump(p);
            } catch (RuntimeException ex) {
                // 한 사람의 오류로 다른 사람의 대기열까지 멈추지 않게
                getLogger().log(Level.SEVERE, p.getName() + ": 스토리 진행 중 오류", ex);
            }
        }
        protection.tick();
        if (++beat % 5 == 0 && dirty && !flushScheduled) flush();
    }

    // ================================================================ 컷신

    private double minSeconds(String id) {
        return Math.max(0, getConfig().getDouble("min-seconds." + id, 0));
    }

    private void sendCutscene(Player p, Request a) {
        a.sent = true;
        a.sentAtMs = System.currentTimeMillis();
        protection.enter(p);
        // 최소 시청 시간이 걸린 컷신은 "본 적 있으면 건너뛰기" 를 서버가 믿을 수 없으므로 건너뛰기 없이 보낸다
        int mode = a.mode == StoryCodec.MODE_AUTO && minSeconds(a.id) > 0 ? StoryCodec.MODE_NOSKIP : a.mode;
        p.sendPluginMessage(this, StoryCodec.CUTSCENE_PLAY, StoryCodec.cutscenePlay(a.id, mode));
    }

    /** 지금 재생 중인 컷신을 멈춘다 (on-finish 는 실행하지 않음). 대기 중인 다음 요청이 있으면 이어서 시작한다. */
    public void stopCutscene(Player p) {
        PlayerStory ps = state.players.get(p.getUniqueId());
        if (ps != null && ps.active != null && ps.active.kind == Kind.CUTSCENE) {
            ps.active = null;
            protection.release(p);
            saveSoon(null);
        }
        if (hasMod(p)) p.sendPluginMessage(this, StoryCodec.CUTSCENE_STOP, new byte[0]);
        pump(p);
    }

    private void finishCutscene(Player p, PlayerStory ps, Request a, boolean skipped, boolean missing) {
        if (ps.active != a) return;
        ps.active = null;
        protection.release(p);
        Effect e = new Effect(ps.nextSeq(), Effect.CUTSCENE_END, a.id, "", skipped, missing);
        queueEffect(p.getUniqueId(), ps, e);
    }

    /** 컷신을 잠시 뒤 다시 보낸다 (화면이 중간에 사라졌거나 너무 빨리 끝났다고 한 경우). 한도를 넘으면 다음 접속까지 둔다. */
    private void resendLater(Player p, Request a, String why) {
        a.sent = false;
        protection.release(p);
        a.resends++;
        int max = Math.max(0, getConfig().getInt("cutscene-max-resends", 5));
        if (a.resends > max) {
            a.parked = true;
            getLogger().warning(p.getName() + ": 컷신 " + a.id + " 을(를) " + max + "번 다시 보냈지만 끝나지 않았습니다 (" + why + "). 다음 접속 때 다시 재생합니다.");
            return;
        }
        a.retryAtMs = System.currentTimeMillis() + (long) (Math.max(0.5, getConfig().getDouble("cutscene-resend-delay-seconds", 3)) * 1000);
    }

    // ================================================================ 대화

    /** 대화 화면을 연다. a.scene 이 있으면 거기서부터 (이어서 보기). */
    private void sendDialogue(Player p, PlayerStory ps, Request a) {
        StoryDef def = defs.get(a.id);
        if (brokenDefs.containsKey(a.id) || ((def == null || def.graph == null) && getConfig().getBoolean("require-server-graph", true))) {
            a.parked = true;
            getLogger().warning(p.getName() + ": 대화 " + a.id + " 을(를) 이어서 열 수 없습니다 — 서버에 dialogues/" + a.id
                    + "/dialogue.yml 이 없거나 오류가 있습니다. 파일을 넣고 /cutscene reload 하면 다시 시도합니다.");
            return;
        }
        // 저장된 장면이 지금 흐름에 없으면 (파일이 바뀜) 처음부터
        if (def != null && def.graph != null && !a.scene.isEmpty() && !def.graph.scenes.containsKey(a.scene)) {
            a.scene = "";
            a.line = 0;
        }
        a.sent = true;
        a.sentAtMs = System.currentTimeMillis();
        a.illegal = 0;
        a.walk = null;
        Object token = a.token = new Object();
        protection.enter(p);
        if (!a.scene.isEmpty()) getLogger().info(p.getName() + " 대화 이어서 열기: " + a.id + " (" + a.scene + " #" + a.line + ")");
        UUID uuid = p.getUniqueId();
        String vars = StoryCodec.encodeVars(textVars(p));
        List<String> npcs = def == null ? List.of() : def.npcs;
        if (!codex.available()) {
            // 파일 호감도: 기록된 것 전부 + 이 대화의 NPC (기록이 없으면 서버 기본값을 명시해서 보낸다)
            Map<String, Integer> scores = new TreeMap<>(affinity.all(uuid));
            for (String n : npcs) scores.put(n, affinity.get(uuid, n));
            deliverOpen(p, a, def, scores, vars);
            return;
        }
        // MagicCodex 호감도: 이 대화에 나오는 NPC (서버 yml 의 npcs) 만 물어본다
        Map<String, Integer> scores = new TreeMap<>();
        CompletableFuture<?>[] all = npcs.stream().map(n -> codex.affinity(uuid, n)
                .thenAccept(v -> { if (v != null) scores.put(n, v); })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(all).orTimeout(3, TimeUnit.SECONDS).handle((r, e) -> {
            if (!isEnabled()) return null;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!isEnabled() || !p.isOnline() || Bukkit.getPlayer(uuid) != p || ps.active != a || a.token != token || !a.sent) return;
                deliverOpen(p, a, def, new TreeMap<>(scores), vars);
            });
            return null;
        });
    }

    private void deliverOpen(Player p, Request a, StoryDef def, Map<String, Integer> scores, String vars) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> sent = new HashMap<>();
        for (var e : scores.entrySet()) {
            if (sent.size() >= 64 || !StorySafety.NPC.matcher(e.getKey()).matches()) continue;
            sent.put(e.getKey(), e.getValue());
            sb.append(sb.length() > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue());
        }
        // 서버도 모드와 같은 호감도에서 시작해 같은 규칙으로 흐름을 따라간다
        a.walk = def != null && def.graph != null ? new StoryGraph.Walk(def.graph, sent, a.scene, a.line) : null;
        p.sendPluginMessage(this, StoryCodec.DIALOGUE_OPEN, StoryCodec.dialogueOpen(a.id, sb.toString(), vars, a.scene, a.line));
    }

    /** 대사에서 바꿔 넣을 글자. {player} = 한글 닉네임, {account} = 마인크래프트 닉네임, %...% = PlaceholderAPI. */
    private Map<String, String> textVars(Player p) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("{player}", codex.nickname(p));
        v.put("{account}", p.getName());
        for (String ph : getConfig().getStringList("dialogue-placeholders")) {
            if (ph == null || !ph.matches("%[A-Za-z0-9_\\-:.]{1,60}%") || v.size() >= 40) continue;
            String r = codex.placeholder(p, ph);
            if (r != null && !r.equals(ph)) v.put(ph, ChatColor.stripColor(r));
        }
        return v;
    }

    /** 지금 진행 중인 대화를 닫는다 (끝 명령은 실행하지 않음, 완료로 기록하지 않음). 대기 중인 다음 요청이 있으면 이어서 시작한다. */
    public void stopDialogue(Player p) {
        PlayerStory ps = state.players.get(p.getUniqueId());
        if (ps != null && ps.active != null && ps.active.kind == Kind.DIALOGUE) {
            ps.active = null;
            protection.release(p);
            saveSoon(null);
        }
        if (hasMod(p)) p.sendPluginMessage(this, StoryCodec.DIALOGUE_STOP, new byte[0]);
        pump(p);
    }

    private void finishDialogue(Player p, PlayerStory ps, Request a, String lastScene, boolean missing) {
        if (ps.active != a) return;
        ps.active = null;
        protection.release(p);
        if (!ps.recordDone(a, lastScene, System.currentTimeMillis())) {
            // 다시 보기: 기록도 효과도 없다
            getLogger().info(p.getName() + ": 대화 " + a.id + " 다시 보기 끝 (명령어·호감도 없음)");
            saveSoon(null);
            pump(p);
            return;
        }
        Effect e = new Effect(ps.nextSeq(), Effect.DIALOGUE_END, a.id, lastScene, false, missing);
        queueEffect(p.getUniqueId(), ps, e);
    }

    /** 흐름에 맞지 않는 패킷. 무시하고 세다가, 너무 많으면 대화를 멈춘다 (클라 파일이 서버와 다르거나 조작된 클라). */
    private void illegal(Player p, Request a, String what) {
        illegal(p, a, what, false);
    }

    /**
     * stopNow = 인정할 수 없는 "끝" 신호: 클라는 이미 대화창을 닫았으므로 더 올 패킷이 없다. 세지 않고 바로 멈춘다
     * (그대로 두면 서버만 "보는 중" 으로 남아 플레이어가 제자리에 묶이고 대기열이 멈춘다).
     */
    private void illegal(Player p, Request a, String what, boolean stopNow) {
        a.illegal++;
        if (getConfig().getBoolean("story-debug", false)) getLogger().info(p.getName() + " 흐름에 없는 패킷 (" + a.id + "): " + what);
        if (a.illegal < ILLEGAL_LIMIT && !stopNow) return;
        a.sent = false;
        a.parked = true;
        a.walk = null;
        protection.release(p);
        getLogger().warning(p.getName() + ": 대화 " + a.id + " 에서 서버 흐름에 맞지 않는 패킷이 " + a.illegal + "개 와서 대화를 멈췄습니다 (마지막: " + what
                + "). 클라의 대화 파일이 서버와 다르거나 조작된 클라일 수 있습니다. 다음 접속 때 다시 엽니다.");
        if (hasMod(p)) p.sendPluginMessage(this, StoryCodec.DIALOGUE_STOP, new byte[0]);
        message(p, "client-content-missing-message", "&c스토리 파일이 최신이 아닙니다. 스토리 파일을 업데이트한 뒤 다시 접속해 주세요.");
    }

    // ================================================================ 효과 (저장된 뒤 실행)

    /** 효과를 기록에 넣고 저장을 요청한다. 디스크에 쓰인 다음에야 실행된다. */
    private void queueEffect(UUID uuid, PlayerStory ps, Effect e) {
        ps.effects.add(e);
        saveSoon(() -> {
            e.durable = true;
            runEffects(uuid);
        });
    }

    /**
     * 저장이 끝난 효과를 차례로 실행한다. 플레이어가 없으면 남겨 두었다가 다음 접속 때 실행한다.
     * 실행한 효과는 목록에서 빼고 다시 저장한다 — 실행과 그 저장 사이에 서버가 죽으면 다음 접속 때 한 번 더 실행될 수 있다.
     */
    private void runEffects(UUID uuid) {
        PlayerStory ps = state.players.get(uuid);
        if (ps == null || ps.running) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        boolean ran = false;
        ps.running = true;
        try {
            while (!ps.effects.isEmpty() && ps.effects.get(0).durable) {
                Effect e = ps.effects.get(0);
                try {
                    execute(p, ps, e);
                } catch (RuntimeException ex) {
                    getLogger().log(Level.SEVERE, p.getName() + ": 스토리 효과 실행 중 오류 (" + e.type + " " + e.id + " " + e.name + ") — 다시 실행하지 않습니다", ex);
                }
                ps.effects.remove(e);
                ran = true;
            }
        } finally {
            ps.running = false;
        }
        if (ran) saveSoon(null);
        pump(p);
    }

    private void execute(Player p, PlayerStory ps, Effect e) {
        switch (e.type) {
            case Effect.EVENT -> {
                StoryDef def = defs.get(e.id);
                if (def == null) {
                    getLogger().warning(p.getName() + ": 대화 " + e.id + " 의 서버 파일이 없어 이벤트 " + e.name + " 의 명령을 실행하지 못했습니다");
                    return;
                }
                applyAffinity(p, ps, e.id, def.affinityEvents.get(e.name), e.id + " " + e.name);
                Bukkit.getPluginManager().callEvent(new StoryEvents.DialogueChoice(p, e.id, e.name));
                run(def.events.get(e.name), p, Map.of("{id}", e.id, "{event}", e.name));
            }
            case Effect.DIALOGUE_END -> {
                Bukkit.getPluginManager().callEvent(new StoryEvents.DialogueFinish(p, e.id, e.name, e.missing));
                StoryDef def = defs.get(e.id);
                if (def == null) {
                    if (!e.missing) getLogger().warning(p.getName() + ": 대화 " + e.id + " 의 서버 파일이 없어 끝 명령을 실행하지 못했습니다");
                    return;
                }
                if (!e.name.isEmpty()) applyAffinity(p, ps, e.id, def.affinityEndings.get(e.name), e.id + " 결말 " + e.name);
                applyAffinity(p, ps, e.id, def.affinityEnd, e.id + " 끝");
                Map<String, String> vars = Map.of("{id}", e.id, "{scene}", e.name);
                if (!e.name.isEmpty()) run(def.endings.get(e.name), p, vars);
                run(def.end, p, vars);
            }
            case Effect.CUTSCENE_END -> {
                Bukkit.getPluginManager().callEvent(new StoryEvents.CutsceneFinish(p, e.id, e.skipped, e.missing));
                run(getConfig().getStringList("on-finish." + e.id), p, Map.of("{id}", e.id, "{skipped}", String.valueOf(e.skipped)));
            }
            default -> {
            }
        }
    }

    /**
     * {npc: 값} 을 적용한다. 값은 서버 파일에서 온 것이고 읽을 때 이미 한도로 잘랐다 (StoryDef).
     * affinity-gain-cap-per-dialogue 가 0보다 크면 한 플레이어가 한 대화에서 얻는 호감도 (오른 것만) 합을 거기까지로 막는다.
     */
    private void applyAffinity(Player p, PlayerStory ps, String dialogueId, Map<String, Integer> changes, String why) {
        if (changes == null || changes.isEmpty()) return;
        int cap = Math.max(0, getConfig().getInt("affinity-gain-cap-per-dialogue", 0));
        boolean logging = getConfig().getBoolean("affinity-log", false);
        for (var e : changes.entrySet()) {
            String npc = e.getKey();
            int v = e.getValue();
            if (v > 0 && cap > 0) {
                int room = Math.max(0, cap - ps.gained.getOrDefault(dialogueId, 0));
                if (v > room) {
                    if (logging) getLogger().info(p.getName() + " 호감도 " + npc + " +" + v + " → +" + room + " (대화 " + dialogueId + " 의 합계 한도 " + cap + ")");
                    v = room;
                }
            }
            if (v == 0) continue;
            if (v > 0) ps.gained.merge(dialogueId, v, Integer::sum);
            final int amount = v;
            if (codex.available()) {
                String name = p.getName();
                codex.addAffinity(p.getUniqueId(), npc, amount).thenAccept(now -> {
                    if (now == null) codex.affinityDropped(name, npc, amount);
                });
            } else {
                affinity.add(p.getUniqueId(), npc, amount);
            }
            if (logging) getLogger().info(p.getName() + " 호감도 " + npc + " " + (amount > 0 ? "+" : "") + amount + " (" + why + ")");
        }
    }

    private void run(List<String> commands, Player p, Map<String, String> vars) {
        if (commands == null) return;
        for (String raw : commands) {
            // 명령어의 {player} 는 마인크래프트 닉네임 그대로 (명령어 대상이므로). 한글 닉네임은 {nickname}
            String c = raw.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString());
            // 닉네임은 플레이어가 정한 글자라 명령어를 바꿔치기하지 못하게 글자·숫자·_·- 만 남긴다
            if (c.contains("{nickname}")) c = c.replace("{nickname}", StorySafety.safeNickname(codex.nickname(p), p.getName()));
            for (var v : vars.entrySet()) c = c.replace(v.getKey(), v.getValue());
            if (c.startsWith("/")) c = c.substring(1);
            if (c.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } catch (RuntimeException ex) {
                getLogger().log(Level.SEVERE, "스토리 명령어 실행 실패: " + c, ex);
            }
        }
    }

    // ================================================================ 모드에서 온 신호

    private void packetLog(Player p, String msg) {
        long now = System.currentTimeMillis();
        Long last = lastPacketLog.get(p.getUniqueId());
        if (last != null && now - last < 10_000) return;
        lastPacketLog.put(p.getUniqueId(), now);
        getLogger().warning(msg);
    }

    private static Request active(PlayerStory ps, Kind kind, String id) {
        Request a = ps == null ? null : ps.active;
        return a != null && a.kind == kind && a.sent && a.id.equals(id) ? a : null;
    }

    /** 흐름 검증이 있는 대화인데 아직 열기 신호가 나가지 않았으면 (호감도 조회 중) 그 사이의 패킷은 버린다. */
    private boolean awaitingOpen(Request a) {
        if (a.walk != null) return false;
        StoryDef def = defs.get(a.id);
        return def != null && def.graph != null;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        if (!ready || !p.isOnline() || Bukkit.getPlayer(p.getUniqueId()) != p) return;
        UUID uuid = p.getUniqueId();
        // 어떤 일도 하기 전에: 플레이어별 초당 20개 (순간 40개) 까지만
        StorySafety.TokenBucket bucket = buckets.computeIfAbsent(uuid, k -> new StorySafety.TokenBucket(20, 40));
        if (!bucket.tryTake(System.nanoTime())) {
            packetLog(p, p.getName() + " 의 스토리 패킷이 너무 많아 버립니다 (지금까지 " + bucket.dropped + "개)");
            return;
        }
        if (message.length > StoryCodec.MAX_C2S_PACKET) {
            packetLog(p, p.getName() + " 의 스토리 패킷이 너무 큼 (" + channel + ", " + message.length + " 바이트)");
            return;
        }
        PlayerStory ps = state.players.get(uuid);
        try {
            switch (channel) {
                case StoryCodec.CUTSCENE_DONE -> {
                    var d = StoryCodec.cutsceneDone(message);
                    // 서버가 재생시킨 것만 인정 (클라가 마음대로 퀘스트를 넘기지 못하게)
                    Request a = active(ps, Kind.CUTSCENE, d.id());
                    if (a == null) return;
                    double min = minSeconds(a.id);
                    if (min > 0 && a.mode != StoryCodec.MODE_SKIP && System.currentTimeMillis() - a.sentAtMs < (long) (min * 1000)) {
                        getLogger().warning(p.getName() + ": 컷신 " + a.id + " 이(가) 최소 시간 " + min + "초보다 빨리 끝났다고 왔습니다 — 인정하지 않고 다시 재생합니다");
                        resendLater(p, a, "min-seconds");
                        return;
                    }
                    finishCutscene(p, ps, a, d.skipped(), false);
                }
                case StoryCodec.CUTSCENE_ABORT -> {
                    var d = StoryCodec.cutsceneAbort(message);
                    Request a = active(ps, Kind.CUTSCENE, d.id());
                    if (a == null) return;
                    // 사망 화면·서버 GUI·월드 이동 등으로 컷신 화면이 사라짐: 잠시 뒤 다시 보낸다
                    resendLater(p, a, "화면이 중간에 닫힘");
                }
                case StoryCodec.STORY_FAIL -> {
                    var f = StoryCodec.storyFail(message);
                    Kind kind = f.kind() == StoryCodec.KIND_CUTSCENE ? Kind.CUTSCENE : Kind.DIALOGUE;
                    Request a = active(ps, kind, f.id());
                    if (a == null) return;
                    String reason = f.reason().replaceAll("[^A-Za-z0-9_\\-]", "");
                    if (reason.length() > 32) reason = reason.substring(0, 32);
                    getLogger().warning(p.getName() + ": 클라에 " + (kind == Kind.CUTSCENE ? "컷신 " : "대화 ") + a.id + " 을(를) 보여 줄 수 없습니다 (" + reason
                            + ") — 끝난 것으로 치지 않습니다. 스토리 파일(또는 모드)을 맞춘 뒤 다시 접속하면 이어집니다.");
                    if (!reason.startsWith("magiccodex")) {
                        message(p, "client-content-missing-message", "&c스토리 파일이 최신이 아닙니다. 스토리 파일을 업데이트한 뒤 다시 접속해 주세요.");
                    }
                    if (getConfig().getBoolean("finish-if-client-content-missing", false)) {
                        if (kind == Kind.CUTSCENE) finishCutscene(p, ps, a, true, true);
                        else finishDialogue(p, ps, a, "", true);
                    } else {
                        a.sent = false;
                        a.parked = true;
                        a.walk = null;
                        protection.release(p);
                    }
                }
                case StoryCodec.DIALOGUE_EVENT -> {
                    var e = StoryCodec.dialogueEvent(message);
                    Request a = active(ps, Kind.DIALOGUE, e.id());
                    if (a == null) return;
                    String event = e.event();
                    // 클라가 보낸 npc/add 값은 쓰지 않는다. 이름만 본다.
                    // 이름이 빈 것은 이벤트 없는 선택지의 예전 형식 알림: 할 일이 없다
                    if (event.isEmpty()) return;
                    if (!StorySafety.validId(event)) {
                        illegal(p, a, "event " + event.replaceAll("[^\\p{L}\\p{N}_\\-]", "?"));
                        return;
                    }
                    StoryDef def = defs.get(a.id);
                    if (awaitingOpen(a)) return;
                    if (a.walk != null) {
                        // 지금 고를 수 있는 선택지 / 들어가는 장면의 이벤트만, 장면 방문마다 선택지 이벤트 하나만
                        if (!a.walk.event(event)) {
                            illegal(p, a, "event " + event);
                            return;
                        }
                    }
                    if (def == null || !def.declares(event)) return; // 서버 파일에 할 일이 없는 이벤트
                    // 다시 보기는 아무것도 주지 않는다. 그 밖에는 플레이어마다 대화당 한 번
                    if (!ps.claimEvent(a, event)) return;
                    queueEffect(uuid, ps, new Effect(ps.nextSeq(), Effect.EVENT, a.id, event, false, false));
                }
                case StoryCodec.DIALOGUE_PROGRESS -> {
                    var g = StoryCodec.dialogueProgress(message);
                    Request a = active(ps, Kind.DIALOGUE, g.id());
                    if (a == null) return;
                    if (!StorySafety.validId(g.scene()) || g.line() < 0 || g.line() > 10000) {
                        illegal(p, a, "progress");
                        return;
                    }
                    if (awaitingOpen(a)) return;
                    if (a.walk != null) {
                        if (!a.walk.progress(g.scene(), g.line())) {
                            illegal(p, a, "progress " + g.scene() + " #" + g.line());
                            return;
                        }
                        if (!a.walk.scene.isEmpty()) {
                            a.scene = a.walk.scene;
                            a.line = a.walk.line;
                        }
                    } else {
                        a.scene = g.scene();
                        a.line = g.line();
                    }
                    dirty = true; // 대사 위치는 5초마다 모아서 저장
                }
                case StoryCodec.DIALOGUE_DONE -> {
                    var d = StoryCodec.dialogueDone(message);
                    Request a = active(ps, Kind.DIALOGUE, d.id());
                    if (a == null) return;
                    if (!StorySafety.validId(d.lastScene())) {
                        illegal(p, a, "done");
                        return;
                    }
                    if (awaitingOpen(a)) return;
                    if (a.walk != null) {
                        if (!a.walk.done(d.lastScene())) {
                            illegal(p, a, "done " + d.lastScene(), true);
                            return;
                        }
                    } else if (!d.lastScene().equals(a.scene)) {
                        return;
                    }
                    finishDialogue(p, ps, a, d.lastScene(), false);
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ex) {
            packetLog(p, p.getName() + " 의 잘못된 스토리 패킷 (" + channel + "): " + ex.getMessage());
        }
    }

    // ================================================================ 접속 / 종료 / MagicCodex 서비스

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        joinedAt.put(uuid, System.currentTimeMillis());
        helloSent.remove(uuid);
        buckets.remove(uuid);
        PlayerStory ps = state.players.get(uuid);
        if (ps == null) return;
        ps.name = p.getName();
        // 끝나지 않은 컷신·대화, 밀린 효과는 heartbeat 가 접속 2초 뒤부터 이어 간다 (모드 채널이 잡힐 때까지 기다림)
        if (ps.active != null) ps.active.resetTransient();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        joinedAt.remove(uuid);
        helloSent.remove(uuid);
        buckets.remove(uuid);
        lastPacketLog.remove(uuid);
        // 컷신도 대화도 지우지 않는다: 다시 접속하면 컷신은 처음부터, 대화는 저장된 곳부터 이어서 연다
        PlayerStory ps = state.players.get(uuid);
        if (ps != null && ps.active != null) ps.active.resetTransient();
        if (dirty) saveSoon(null);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent e) {
        if (e.getProvider().getService().getName().equals(MagicCodexLink.FACADE)) {
            Bukkit.getScheduler().runTask(this, () -> codex.connect());
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent e) {
        if (e.getProvider().getService().getName().equals(MagicCodexLink.FACADE)) codex.invalidate();
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
                s.sendMessage(ChatColor.GREEN + "스토리 설정을 다시 불러왔어요. 대화 파일을 읽는 중…");
                // 파일은 메인 스레드 밖에서 읽고, 다 읽으면 한 번에 바꾼다
                File dialogues = new File(getDataFolder(), "dialogues");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    DefLoad load = loadDefs(dialogues);
                    if (affinity.readFailed()) affinity.load();
                    if (!isEnabled()) return;
                    Bukkit.getScheduler().runTask(this, () -> {
                        applyDefs(load, s);
                        // 파일이 없어 멈춰 있던 요청을 다시 시도
                        for (PlayerStory ps : state.players.values()) {
                            if (ps.active != null && ps.active.parked) {
                                ps.active.parked = false;
                                ps.active.resends = 0;
                            }
                        }
                    });
                });
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
                int n = 0;
                for (Player p : t) if (requestCutscene(s, p, a[2], mode)) n++;
                s.sendMessage(ChatColor.GRAY + "컷신 " + a[2] + " 재생 요청: " + n + "명" + (n < t.size() ? " (" + (t.size() - n) + "명 제외)" : ""));
            }
            case "stop" -> {
                if (a.length < 2) return false;
                List<Player> t = targets(s, a[1]);
                t.forEach(this::stopCutscene);
                s.sendMessage(ChatColor.GRAY + "컷신 멈춤: " + t.size() + "명");
            }
            case "clear" -> {
                if (a.length < 2) return false;
                List<Player> t = targets(s, a[1]);
                for (Player p : t) clearStory(p);
                s.sendMessage(ChatColor.GRAY + "재생 중·대기 중인 스토리를 모두 취소: " + t.size() + "명 (끝 명령은 실행하지 않음, 완료 기록은 그대로)");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 재생 중인 것과 대기열을 모두 버린다 (막힌 플레이어를 풀 때). 완료 기록과 실행 대기 효과는 건드리지 않는다. */
    private void clearStory(Player p) {
        PlayerStory ps = state.players.get(p.getUniqueId());
        if (ps != null) {
            ps.active = null;
            ps.queue.clear();
            saveSoon(null);
            prune(p.getUniqueId(), ps);
        }
        protection.release(p);
        if (hasMod(p)) {
            p.sendPluginMessage(this, StoryCodec.CUTSCENE_STOP, new byte[0]);
            p.sendPluginMessage(this, StoryCodec.DIALOGUE_STOP, new byte[0]);
        }
    }

    private boolean dialogueCommand(CommandSender s, String[] a) {
        if (a.length < 2) return false;
        if (a[0].equalsIgnoreCase("stop")) {
            List<Player> t = targets(s, a[1]);
            t.forEach(this::stopDialogue);
            s.sendMessage(ChatColor.GRAY + "대화 닫음: " + t.size() + "명");
            return true;
        }
        if (a[0].equalsIgnoreCase("ledger")) return ledgerCommand(s, a);
        if (a[0].equalsIgnoreCase("reset")) return resetCommand(s, a);
        String id = a[1];
        if (!ID.matcher(id).matches()) {
            s.sendMessage(ChatColor.RED + "대화 id는 소문자, 숫자, _, - 만: " + id);
            return true;
        }
        boolean replay = a.length >= 3 && a[2].equalsIgnoreCase("replay");
        List<Player> t = targets(s, a[0]);
        if (t.isEmpty()) {
            s.sendMessage(ChatColor.RED + "대상 플레이어가 없어요: " + a[0]);
            return true;
        }
        int n = 0;
        for (Player p : t) if (requestDialogue(s, p, id, replay)) n++;
        s.sendMessage(ChatColor.GRAY + "대화 " + id + " 열기 요청: " + n + "명" + (n < t.size() ? " (" + (t.size() - n) + "명 제외 — 이미 끝냈거나 진행 중이거나 파일 없음)" : ""));
        return true;
    }

    /** 이름 (접속 중이거나 기록에 있는 사람) 또는 UUID 로 기록을 찾는다. 없으면 null. */
    private UUID findRecord(String arg) {
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) return online.getUniqueId();
        for (var e : state.players.entrySet()) if (e.getValue().name.equalsIgnoreCase(arg)) return e.getKey();
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String describe(Request r) {
        String what = (r.kind == Kind.CUTSCENE ? "컷신 " : "대화 ") + r.id + (r.replay ? " (다시 보기)" : "");
        if (r.kind == Kind.DIALOGUE && !r.scene.isEmpty()) what += " [" + r.scene + " #" + r.line + "]";
        return what;
    }

    /** /storydialogue ledger <플레이어> [대화id] — 진행 상태와 완료 기록 보기. */
    private boolean ledgerCommand(CommandSender s, String[] a) {
        UUID uuid = findRecord(a[1]);
        PlayerStory ps = uuid == null ? null : state.players.get(uuid);
        if (ps == null) {
            s.sendMessage(ChatColor.GRAY + a[1] + ": 스토리 기록이 없어요 (이 서버 기준).");
            return true;
        }
        if (a.length >= 3) {
            String id = a[2];
            StoryState.Done d = ps.done.get(id);
            s.sendMessage(ChatColor.GOLD + a[1] + " / 대화 " + id + ": " + (d == null ? "아직 끝내지 않음"
                    : "끝냄 (결말 " + (d.scene.isEmpty() ? "-" : d.scene) + ", " + d.times + "번, " + new java.util.Date(d.at) + ")"));
            Set<String> fired = ps.fired.get(id);
            s.sendMessage(ChatColor.GRAY + "실행된 이벤트: " + (fired == null || fired.isEmpty() ? "없음" : String.join(", ", fired)));
            s.sendMessage(ChatColor.GRAY + "이 대화로 오른 호감도 합: " + ps.gained.getOrDefault(id, 0));
            return true;
        }
        Request act = ps.active;
        s.sendMessage(ChatColor.GOLD + a[1] + " 스토리 기록 (이 서버)");
        s.sendMessage(ChatColor.GRAY + "지금: " + (act == null ? "없음" : describe(act) + (act.sent ? " — 보는 중" : act.parked ? " — 멈춤 (다음 접속/reload 때 다시)" : " — 보내기 대기")));
        if (!ps.queue.isEmpty()) {
            List<String> q = new ArrayList<>();
            for (Request r : ps.queue) q.add(describe(r));
            s.sendMessage(ChatColor.GRAY + "대기: " + String.join(", ", q));
        }
        if (!ps.effects.isEmpty()) s.sendMessage(ChatColor.GRAY + "실행 대기 효과: " + ps.effects.size() + "개");
        List<String> done = new ArrayList<>();
        for (var e : ps.done.entrySet()) done.add(e.getKey() + (e.getValue().scene.isEmpty() ? "" : "(" + e.getValue().scene + ")"));
        s.sendMessage(ChatColor.GRAY + "끝낸 대화 " + done.size() + "개: " + (done.isEmpty() ? "-" : String.join(", ", done)));
        return true;
    }

    /** /storydialogue reset <플레이어> <대화id|all> — 완료 기록 지우기 (다시 처음부터 보상까지 받을 수 있게 됨). */
    private boolean resetCommand(CommandSender s, String[] a) {
        if (a.length < 3) return false;
        UUID uuid = findRecord(a[1]);
        PlayerStory ps = uuid == null ? null : state.players.get(uuid);
        if (ps == null) {
            s.sendMessage(ChatColor.GRAY + a[1] + ": 스토리 기록이 없어요 (이 서버 기준).");
            return true;
        }
        String id = a[2];
        if (id.equalsIgnoreCase("all")) {
            int n = ps.done.size();
            ps.done.clear();
            ps.fired.clear();
            ps.gained.clear();
            getLogger().info(s.getName() + " 이(가) " + a[1] + " 의 대화 완료 기록 전체 (" + n + "개) 를 지웠습니다");
            s.sendMessage(ChatColor.GOLD + a[1] + ": 대화 완료 기록 " + n + "개를 모두 지웠어요.");
        } else {
            if (!ID.matcher(id).matches()) {
                s.sendMessage(ChatColor.RED + "대화 id는 소문자, 숫자, _, - 만: " + id);
                return true;
            }
            boolean had = ps.resetLedger(id);
            getLogger().info(s.getName() + " 이(가) " + a[1] + " 의 대화 " + id + " 완료 기록을 지웠습니다" + (had ? "" : " (기록 없었음)"));
            s.sendMessage(ChatColor.GOLD + a[1] + ": 대화 " + id + (had ? " 기록을 지웠어요. 다시 열면 이벤트·끝 명령이 다시 실행됩니다." : " 기록이 없어요."));
        }
        saveSoon(null);
        prune(uuid, ps);
        return true;
    }

    private boolean affinityCommand(CommandSender s, String[] a) {
        if (a.length == 0) return false;
        // 이름으로 프로필을 찾으러 나가면 메인 스레드가 멈출 수 있어서, 접속 중이거나 서버가 이미 아는 사람만 받는다
        OfflinePlayer op = Bukkit.getPlayerExact(a[0]);
        if (op == null) op = Bukkit.getOfflinePlayerIfCached(a[0]);
        UUID id;
        if (op != null) id = op.getUniqueId();
        else {
            try {
                id = UUID.fromString(a[0]);
            } catch (IllegalArgumentException e) {
                s.sendMessage(ChatColor.RED + "접속 중이거나 이 서버에 접속한 적 있는 플레이어 이름 (또는 UUID) 을 넣어 주세요: " + a[0]);
                return true;
            }
        }
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
        if (!StorySafety.NPC.matcher(npc).matches()) {
            s.sendMessage(ChatColor.RED + "NPC id 가 잘못됐어요: " + npc);
            return true;
        }
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
                if (a.length == 1) o.addAll(List.of("play", "stop", "clear", "reload"));
                else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) o.addAll(players);
                else if (a.length == 3 && a[0].equalsIgnoreCase("play")) o.addAll(getConfig().getStringList("cutscenes"));
                else if (a.length == 4 && a[0].equalsIgnoreCase("play")) o.addAll(List.of("auto", "skip", "noskip"));
            }
            case "storydialogue" -> {
                boolean admin = a[0].equalsIgnoreCase("ledger") || a[0].equalsIgnoreCase("reset");
                if (a.length == 1) {
                    o.addAll(List.of("stop", "ledger", "reset"));
                    o.addAll(players);
                } else if (a.length == 2 && (a[0].equalsIgnoreCase("stop") || admin)) {
                    Bukkit.getOnlinePlayers().forEach(p -> o.add(p.getName()));
                    if (a[0].equalsIgnoreCase("stop")) o.addAll(List.of("@a", "@p"));
                } else if (a.length == 2 || (a.length == 3 && admin)) {
                    // 파일을 뒤지지 않고 메모리에 읽어 둔 목록만 쓴다
                    o.addAll(defs.keySet());
                    if (a.length == 3 && a[0].equalsIgnoreCase("reset")) o.add("all");
                } else if (a.length == 3 && !a[0].equalsIgnoreCase("stop")) {
                    o.add("replay");
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
