package kr.chacademy.portrait;

import kr.chacademy.portrait.ai.OpenAiImageClient;
import kr.chacademy.portrait.cmd.AdminCommand;
import kr.chacademy.portrait.core.PortraitSettings;
import kr.chacademy.portrait.data.Database;
import kr.chacademy.portrait.data.PortraitStorage;
import kr.chacademy.portrait.net.PortraitChannel;
import kr.chacademy.portrait.service.ItemsAdderHook;
import kr.chacademy.portrait.service.PortraitService;
import kr.chacademy.portrait.service.RerollService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ChacaPortrait — 플레이어 스킨을 읽어 레퍼런스 그림체의 상반신 일러스트를 만들고, MagicCodex 대화창 "내 차례"에 쓰도록 클라이언트로 보낸다.
 * 최초 접속 시 1회 자동, 이후 ItemsAdder 아이템(item:reroll) 우클릭으로 추가 요청과 함께 다시 그린다.
 */
public final class ChacaPortraitPlugin extends JavaPlugin implements Listener {

    private volatile PortraitSettings settings;
    private Database db;
    private PortraitStorage storage;
    private PortraitService service;
    private RerollService rerolls;
    private PortraitChannel channel;
    private ItemsAdderHook itemsAdder;
    private final OpenAiImageClient ai = new OpenAiImageClient();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("database.properties.example");
        new File(getDataFolder(), "reference").mkdirs();
        new File(getDataFolder(), "tests").mkdirs();
        settings = new PortraitSettings(getConfig());
        if (!PortraitSettings.supportedImageModel(settings.imageModel)) {
            getLogger().warning("[ChacaPortrait] image.model 값이 지원 목록(gpt-image-2, gpt-image-1.5)에 없습니다: " + settings.imageModel);
        }
        if (!settings.hasKey()) {
            getLogger().warning("[ChacaPortrait] OpenAI 키가 없습니다. 환경변수 CHACAPORTRAIT_OPENAI_KEY (또는 CHACANPC_OPENAI_KEY) 를 설정하세요. "
                    + "그 전까지 일러스트 생성은 쉬고, 이미 있는 일러스트 전송만 합니다.");
        }
        try {
            db = new Database(getDataFolder().toPath(), getLogger());
            storage = new PortraitStorage(db);
            db.call(() -> {
                storage.init();
                int locks = storage.releaseAll(settings.serverId);
                int rerollsBack = storage.failPendingRerolls(settings.serverId);
                int stale = storage.settleStale(settings.serverId);
                if (locks + rerollsBack + stale > 0) {
                    getLogger().info("[ChacaPortrait] 지난 실행 정리: 잠금 " + locks + ", 반환 대상 " + rerollsBack + ", 예약 정산 " + stale);
                }
                return null;
            }).get();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ChacaPortrait] DB 초기화 실패 — 플러그인을 끕니다", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (db.isMariaDb()) {
            getLogger().warning("[ChacaPortrait] 공유 DB 사용 중 — server-id(현재 '" + settings.serverId
                    + "')는 school/wild 서버마다 반드시 달라야 합니다. 같으면 한 서버 재시작이 다른 서버의 진행 중 작업을 정리합니다.");
        }
        itemsAdder = new ItemsAdderHook(getLogger());
        service = new PortraitService(this);
        service.start();
        rerolls = new RerollService(this);
        channel = new PortraitChannel(this);
        channel.register();
        Bukkit.getPluginManager().registerEvents(rerolls, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("portrait");
        if (cmd != null) {
            AdminCommand admin = new AdminCommand(this);
            cmd.setExecutor(admin);
            cmd.setTabCompleter(admin);
        }
        Bukkit.getScheduler().runTaskTimer(this, rerolls::sweep, 100L, 100L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            rerolls.deliverRefunds(p, false);
        }
        getLogger().info("[ChacaPortrait] 켜짐 — 모델 " + settings.imageModel + ", 품질 " + settings.quality
                + ", DB " + (db.isMariaDb() ? "MariaDB" : "SQLite") + ", ItemsAdder " + (itemsAdder.available() ? "연결" : "없음"));
    }

    @Override
    public void onDisable() {
        if (channel != null) {
            channel.unregister();
        }
        if (service != null) {
            service.stop();
        }
        if (db != null) {
            // 진행 중이던 작업의 잠금·예약은 다음 시작 때 정리된다
            db.close();
        }
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    /** 메인 스레드에서 실행 (플러그인이 꺼졌으면 무시). */
    public void main(Runnable r) {
        if (!isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(this, r);
        } catch (IllegalStateException ignored) {
            // 종료 중
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = new PortraitSettings(getConfig());
        service.reload();
    }

    /** 최초 접속: 일러스트가 없고 자동 시도 횟수가 남았으면 잠시 뒤 생성. 돌려줄 아이템도 지급. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        rerolls.deliverRefunds(p, false);
        PortraitSettings s = settings;
        if (!s.enabled || !s.autoFirstJoin || !s.hasKey() || !p.hasPermission("chacaportrait.auto")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player now = Bukkit.getPlayer(id);
            if (now == null || service.busy(id)) {
                return;
            }
            service.then(db.call(() -> storage.sha(id) == null && storage.autoAttempts(id) < settings.autoMaxAttempts), need -> {
                Player again = Bukkit.getPlayer(id);
                if (!Boolean.TRUE.equals(need) || again == null || service.busy(id)) {
                    return;
                }
                PortraitService.Job job = PortraitService.jobFor(PortraitService.Kind.AUTO, again, "", null, List.of(), null);
                if (job.skin().url() == null) {
                    return; // 스킨 없음 (기본 스킨) — 다음 접속 때 다시 확인
                }
                if (service.submit(job, null)) {
                    int pos = service.waitingCount();
                    again.sendMessage(pos > 0 ? settings.message("queued", "position", pos) : settings.message("started"));
                }
            }, ex -> getLogger().warning("[ChacaPortrait] 최초 생성 확인 실패: " + ex.getMessage()));
        }, Math.max(1, s.autoDelaySeconds * 20L));
    }

    public PortraitSettings settings() {
        return settings;
    }

    public Database db() {
        return db;
    }

    public PortraitStorage storage() {
        return storage;
    }

    public PortraitService service() {
        return service;
    }

    public RerollService rerolls() {
        return rerolls;
    }

    public PortraitChannel channel() {
        return channel;
    }

    public ItemsAdderHook itemsAdder() {
        return itemsAdder;
    }

    public OpenAiImageClient ai() {
        return ai;
    }
}
