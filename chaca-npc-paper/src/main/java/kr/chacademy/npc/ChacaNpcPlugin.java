package kr.chacademy.npc;

import kr.chacademy.npc.ai.OpenAiClient;
import kr.chacademy.npc.budget.BudgetService;
import kr.chacademy.npc.cmd.AdminCommand;
import kr.chacademy.npc.cmd.PlayerCommands;
import kr.chacademy.npc.config.CharacterRepository;
import kr.chacademy.npc.config.ContentRepository;
import kr.chacademy.npc.config.PlaceRepository;
import kr.chacademy.npc.config.Settings;
import kr.chacademy.npc.data.Database;
import kr.chacademy.npc.data.Storage;
import kr.chacademy.npc.dialogue.DialogueService;
import kr.chacademy.npc.dialogue.GiftService;
import kr.chacademy.npc.dialogue.NpcTalkChannel;
import kr.chacademy.npc.integration.MagicCodexLink;
import kr.chacademy.npc.npc.MovementController;
import kr.chacademy.npc.npc.NpcManager;
import kr.chacademy.npc.social.SocialService;
import kr.chacademy.npc.social.VibeService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * ChacaNPC — 차카데미 AI NPC 플러그인 (school 서버 전용).
 */
public final class ChacaNpcPlugin extends JavaPlugin implements Listener {

    private static volatile ChacaNpcPlugin instance;

    private volatile Settings settings;
    private CharacterRepository characters;
    private PlaceRepository places;
    private ContentRepository content;
    private Database database;
    private Storage storage;
    private BudgetService budget;
    private OpenAiClient ai;
    private DialogueService dialogue;
    private NpcManager npcs;
    private MovementController movement;
    private SocialService social;
    private VibeService vibe;
    private MagicCodexLink link;
    private NpcTalkChannel talkChannel;
    private GiftService gifts;
    private final Consumer<Map<String, String>> socialListener = ev -> {
        if (social != null) {
            social.onMagicCodexEvent(ev);
        }
    };

    public static ChacaNpcPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaults();
        settings = new Settings(getConfig());

        characters = new CharacterRepository(new File(getDataFolder(), "npcs"), getLogger());
        places = new PlaceRepository(new File(getDataFolder(), "places.yml"), getLogger());
        content = new ContentRepository(getDataFolder(), getLogger());
        characters.load();
        places.load();
        content.load();

        try {
            database = new Database(getDataFolder().toPath(), getLogger());
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "[ChacaNPC] DB 연결 실패 — 플러그인을 끕니다. plugins/ChacaNPC/database.properties 를 확인하세요.", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        storage = new Storage(database);
        budget = new BudgetService(() -> settings, storage);
        database.run(() -> {
            storage.createTables();
            budget.loadFromDb();
        });

        ai = new OpenAiClient(aiConfig(), getLogger());

        link = new MagicCodexLink(this);
        talkChannel = new NpcTalkChannel(this);
        npcs = new NpcManager(this);
        dialogue = new DialogueService(this);
        gifts = new GiftService(this);
        movement = new MovementController(this);
        social = new SocialService(this);
        vibe = new VibeService(this);
        vibe.refreshCache();

        talkChannel.register();
        Bukkit.getPluginManager().registerEvents(npcs, this);
        Bukkit.getPluginManager().registerEvents(link, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        link.onConnect(() -> {
            link.claim(npcs::isClaimed);
            link.removeSocialListener(socialListener);
            link.addSocialListener(socialListener);
        });
        link.connect();
        if (!link.available()) {
            getLogger().warning("[ChacaNPC] MagicCodexBridge NPC API 없음 — 호감도는 기본값, 퀘스트·선물·스토리 우선 판단 꺼짐");
        }

        AdminCommand admin = new AdminCommand(this);
        PlayerCommands playerCmds = new PlayerCommands(this);
        bind("cnpc", admin);
        bind("t", playerCmds);
        bind("nickname", playerCmds);
        bind("chacanpc-ui", playerCmds);

        Bukkit.getScheduler().runTaskLater(this, npcs::scan, 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            dialogue.tick();
            movement.tick();
        }, 60L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            social.propagate();
            vibe.tick();
        }, 20L * 60, 20L * 600);
        Bukkit.getScheduler().runTaskTimer(this, () -> social.ambientTick(), 20L * 90, 20L * 60);

        if (settings.apiKey.isBlank()) {
            getLogger().warning("[ChacaNPC] OpenAI API 키가 없습니다. school 서버 프로세스에 환경변수 CHACANPC_OPENAI_KEY 를 설정하세요.");
        }
        getLogger().info("[ChacaNPC] 켜짐 — 모델 " + settings.model + ", DB " + (database.isMariaDb() ? "MariaDB" : "SQLite(chaca-npc.db)"));
    }

    @Override
    public void onDisable() {
        if (link != null && link.available()) {
            link.claim(null);
            link.removeSocialListener(socialListener);
        }
        if (dialogue != null) {
            dialogue.closeAll();
        }
        if (talkChannel != null) {
            talkChannel.unregister();
        }
        if (ai != null) {
            ai.shutdown();
        }
        if (database != null) {
            database.close();
        }
        instance = null;
    }

    private void bind(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("[ChacaNPC] plugin.yml에 명령어 '" + name + "'가 없습니다");
            return;
        }
        cmd.setExecutor((CommandExecutor) handler);
        if (handler instanceof TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    private void saveDefaults() {
        saveDefaultConfig();
        String[] files = {"places.yml", "quests.yml", "hints.yml", "filter.yml", "database.properties.example",
                "prompts/common.txt", "prompts/ambient.txt", "prompts/vibe.txt"};
        for (String f : files) {
            if (!new File(getDataFolder(), f).exists()) {
                saveResource(f, false);
            }
        }
        File npcFolder = new File(getDataFolder(), "npcs");
        if (!npcFolder.exists()) {
            saveResource("npcs/ella.yml", false);
            saveResource("npcs/mina.yml", false);
        }
    }

    private OpenAiClient.Config aiConfig() {
        OpenAiClient.Config c = new OpenAiClient.Config();
        c.apiKey = settings.apiKey;
        c.baseUrl = settings.baseUrl;
        c.model = settings.model;
        c.reasoningEffort = settings.reasoningEffort;
        c.hardTimeoutSeconds = settings.hardTimeoutSeconds;
        c.maxConcurrent = settings.maxConcurrent;
        c.moderationModel = settings.moderationModel;
        return c;
    }

    /** /cnpc reload */
    public void reloadEverything() {
        reloadConfig();
        settings = new Settings(getConfig());
        characters.load();
        places.load();
        content.load();
        ai.updateConfig(aiConfig());
        dialogue.reloadSettings();
        movement.reset();
        vibe.refreshCache();
        npcs.scan();
        if (!link.available()) {
            link.connect();
        }
    }

    /** 메인 스레드에서 실행 (플러그인이 꺼진 뒤면 무시). */
    public void sync(Runnable r) {
        if (!isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(this, r);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 꺼지는 중
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dialogue.closeQuietly(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ getters

    public Settings settings() {
        return settings;
    }

    public CharacterRepository characters() {
        return characters;
    }

    public PlaceRepository places() {
        return places;
    }

    public ContentRepository content() {
        return content;
    }

    public Database database() {
        return database;
    }

    public Storage storage() {
        return storage;
    }

    public BudgetService budget() {
        return budget;
    }

    public OpenAiClient ai() {
        return ai;
    }

    public DialogueService dialogue() {
        return dialogue;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public MovementController movement() {
        return movement;
    }

    public SocialService social() {
        return social;
    }

    public VibeService vibe() {
        return vibe;
    }

    public MagicCodexLink link() {
        return link;
    }

    public NpcTalkChannel talkChannel() {
        return talkChannel;
    }

    public GiftService gifts() {
        return gifts;
    }
}
