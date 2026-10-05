package school.magiccodex.paper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import school.magiccodex.database.DatabaseSettings;

/**
 * NPC 호감도·스토리 우선 판단·퀘스트 연결. 외부 플러그인은 {@link NpcSocialFacade}로만 쓴다.
 * DB는 전용 IO 스레드 하나, 결과는 메인 스레드에서 완료된다.
 */
final class NpcSocialService implements Listener, AutoCloseable {

    private final MagicCodexBridge plugin;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MagicCodex-affinity-io");
        t.setDaemon(true);
        return t;
    });
    private final AffinityStore store;
    private final Map<UUID, Map<String, AffinityStore.Row>> cache = new ConcurrentHashMap<>();
    private final List<Consumer<Map<String, String>>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> caps = new ConcurrentHashMap<>();
    private final int defaultCap;
    private final int giftDailyCount;
    private final ZoneId zone;
    private volatile boolean closing;
    private NpcSocialFacade facade;

    NpcSocialService(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        Path folder = plugin.getDataFolder().toPath();
        if (!Files.exists(folder.resolve("affinity.yml"))) {
            plugin.saveResource("affinity.yml", false);
        }
        var cfg = YamlConfiguration.loadConfiguration(folder.resolve("affinity.yml").toFile());
        var section = cfg.getConfigurationSection("daily-caps");
        if (section != null) {
            for (String k : section.getKeys(false)) {
                caps.put(k, Math.max(0, section.getInt(k)));
            }
        }
        defaultCap = Math.max(0, cfg.getInt("default-daily-cap", 5));
        giftDailyCount = Math.max(1, cfg.getInt("gift-daily-count", 1));
        zone = ZoneId.of(cfg.getString("timezone", "Asia/Seoul"));
        int recoverMinutes = Math.max(2, cfg.getInt("recover-pending-gift-minutes", 10));

        var settings = DatabaseSettings.load(folder.resolve("database.properties"));
        // SQLite fallback 경로를 명시: plugins/MagicCodexBridge/affinity.db
        Path sqlite = folder.resolve("affinity.db");
        try {
            store = io.submit(() -> new AffinityStore(settings, sqlite)).get(15, TimeUnit.SECONDS);
            long now = System.currentTimeMillis();
            int recovered = io.submit(() -> store.recoverPendingGifts(now - recoverMinutes * 60_000L, now)).get(15, TimeUnit.SECONDS);
            if (recovered > 0) {
                plugin.getLogger().warning("NPC 선물 미확정 " + recovered + "건을 확정 처리했습니다 (아이템 차감된 것으로 간주).");
            }
        } catch (Exception e) {
            io.shutdownNow();
            throw e;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p.getUniqueId());
        }
        registerDialogueHooks();
        facade = new NpcSocialFacade(this);
        Bukkit.getServicesManager().register(NpcSocialFacade.class, facade, plugin, org.bukkit.plugin.ServicePriority.Normal);
    }

    String playerName(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("server thread required");
        return plugin.names().name(player);
    }

    // ------------------------------------------------------------------ 공통

    private interface Job<T> {
        T run() throws Exception;
    }

    /** IO 스레드에서 실행하고 메인 스레드에서 완료. 실패하면 예외로 완료. */
    private <T> CompletableFuture<T> work(Job<T> job) {
        CompletableFuture<T> f = new CompletableFuture<>();
        if (closing) {
            f.completeExceptionally(new IllegalStateException("closing"));
            return f;
        }
        try {
            io.execute(() -> {
                try {
                    T v = job.run();
                    main(() -> f.complete(v));
                } catch (Exception e) {
                    plugin.getLogger().warning("NPC affinity storage: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    main(() -> f.completeExceptionally(e));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    /** 결과는 항상 메인 스레드에서 완료. 플러그인이 꺼진 뒤면 완료하지 않는다 (외부 콜백이 IO 스레드에서 돌지 않게). */
    private void main(Runnable r) {
        if (plugin.isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(plugin, r);
            } catch (IllegalStateException ignored) {
                // 꺼지는 중
            }
        }
    }

    String today() {
        return LocalDate.now(zone).toString();
    }

    int capFor(String source) {
        return caps.getOrDefault(source, defaultCap);
    }

    private void remember(UUID player, String npc, AffinityStore.Row row) {
        cache.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(npc, row);
    }

    private void load(UUID player) {
        work(() -> store.loadAll(player)).thenAccept(map -> {
            if (Bukkit.getPlayer(player) != null) {
                cache.put(player, new ConcurrentHashMap<>(map));
            }
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ 호감도

    CompletableFuture<AffinityStore.Row> affinity(UUID player, String npc) {
        return work(() -> store.load(player, npc)).thenApply(row -> {
            remember(player, npc, row);
            return row;
        });
    }

    CompletableFuture<AffinityStore.AddResult> add(UUID player, String npc, String source, int amount) {
        String day = today();
        int cap = capFor(source);
        long now = System.currentTimeMillis();
        return work(() -> store.add(player, npc, source, amount, cap, day, now)).thenApply(r -> {
            remember(player, npc, r.row());
            return r;
        });
    }

    CompletableFuture<AffinityStore.Row> setHeart(UUID player, String npc, int level) {
        long now = System.currentTimeMillis();
        return work(() -> store.setHeart(player, npc, level, now)).thenApply(row -> {
            remember(player, npc, row);
            return row;
        });
    }

    CompletableFuture<AffinityStore.Row> setNickname(UUID player, String npc, String nickname) {
        long now = System.currentTimeMillis();
        return work(() -> store.setNickname(player, npc, nickname, now)).thenApply(row -> {
            remember(player, npc, row);
            return row;
        });
    }

    CompletableFuture<AffinityStore.GiftBegin> beginGift(UUID player, String npc, String token, int delta) {
        String day = today();
        long now = System.currentTimeMillis();
        return work(() -> store.beginGift(token, player, npc, delta, day, giftDailyCount, now));
    }

    CompletableFuture<AffinityStore.AddResult> commitGift(UUID player, String npc, String token) {
        long now = System.currentTimeMillis();
        return work(() -> store.commitGift(token, now)).thenApply(r -> {
            if (r != null) {
                remember(player, npc, r.row());
            }
            return r;
        });
    }

    CompletableFuture<Boolean> cancelGift(String token) {
        return work(() -> store.cancelGift(token));
    }

    /** 고정 대화 조건용 (메인 스레드, 캐시만 본다). */
    AffinityStore.Row cached(UUID player, String npc) {
        Map<String, AffinityStore.Row> m = cache.get(player);
        if (m == null) {
            return null;
        }
        return m.getOrDefault(npc, AffinityStore.Row.EMPTY);
    }

    // ------------------------------------------------------------------ 고정 대화 조건·동작

    private void registerDialogueHooks() {
        // 조건: affinity  인자 예) "ella>=40"  "ella heart>=2"  "ella heart<1"
        plugin.registerDialogueCondition("affinity", (p, arg) -> {
            var m = java.util.regex.Pattern.compile("\\s*([a-z0-9_-]{1,48})\\s*(heart\\s*)?(>=|<=|>|<|=)\\s*(-?\\d{1,3})\\s*")
                    .matcher(arg == null ? "" : arg);
            if (!m.matches()) {
                return false;
            }
            AffinityStore.Row row = cached(p.getUniqueId(), m.group(1));
            if (row == null) {
                load(p.getUniqueId());
                return false;
            }
            int v = m.group(2) != null ? row.heart() : row.score();
            int n = Integer.parseInt(m.group(4));
            return switch (m.group(3)) {
                case ">=" -> v >= n;
                case "<=" -> v <= n;
                case ">" -> v > n;
                case "<" -> v < n;
                default -> v == n;
            };
        });
        // 동작: affinity  인자 "ella +5"
        plugin.registerDialogueAction("affinity", (p, arg) -> {
            String[] a = arg.trim().split("\\s+");
            if (a.length == 2 && a[0].matches("[a-z0-9_-]{1,48}") && a[1].matches("[+-]?\\d{1,3}")) {
                add(p.getUniqueId(), a[0], "dialogue", Integer.parseInt(a[1].replace("+", "")));
            } else {
                plugin.getLogger().warning("custom affinity 인자 오류: " + arg);
            }
        });
        // 동작: heart  인자 "ella 2"  (하트 이벤트 2단계 완료 → 점수 상한 해제, ChacaNPC 소문)
        plugin.registerDialogueAction("heart", (p, arg) -> {
            String[] a = arg.trim().split("\\s+");
            if (a.length == 2 && a[0].matches("[a-z0-9_-]{1,48}") && a[1].matches("[0-5]")) {
                int level = Integer.parseInt(a[1]);
                setHeart(p.getUniqueId(), a[0], level).thenAccept(row ->
                        notify(p, a[0], level >= 5 ? "ending" : "heart", String.valueOf(level)));
            } else {
                plugin.getLogger().warning("custom heart 인자 오류: " + arg);
            }
        });
        // 동작: nickname  인자 "ella 책벌레"
        plugin.registerDialogueAction("nickname", (p, arg) -> {
            String[] a = arg.trim().split("\\s+", 2);
            if (a.length == 2 && a[0].matches("[a-z0-9_-]{1,48}") && a[1].codePointCount(0, a[1].length()) <= 8) {
                setNickname(p.getUniqueId(), a[0], a[1]).thenAccept(row -> notify(p, a[0], "nickname", a[1]));
            } else {
                plugin.getLogger().warning("custom nickname 인자 오류: " + arg);
            }
        });
    }

    private void notify(Player p, String npc, String type, String value) {
        Map<String, String> event = Map.of("player", p.getUniqueId().toString(), "playerName", plugin.names().name(p),
                "npc", npc, "type", type, "value", value);
        for (var l : listeners) {
            try {
                l.accept(event);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("NPC social listener: " + e.getClass().getSimpleName());
            }
        }
    }

    void addListener(Consumer<Map<String, String>> l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    void removeListener(Consumer<Map<String, String>> l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------ 스토리·퀘스트 위임

    CompletableFuture<String> openStory(Player p, int citizensId, Entity anchor) {
        DialogueBridge d = plugin.dialogueBridge();
        return d == null ? CompletableFuture.completedFuture("BLOCKED") : d.openBoundStory(p, citizensId, anchor);
    }

    void claim(IntPredicate claimed) {
        DialogueBridge d = plugin.dialogueBridge();
        if (d != null) {
            d.claim(claimed);
        }
    }

    CompletableFuture<String> storySummary(UUID player) {
        DialogueBridge d = plugin.dialogueBridge();
        return d == null ? CompletableFuture.completedFuture("") : d.storySummary(player);
    }

    QuestBridge quests() {
        return plugin.questBridge();
    }

    @Override
    public void close() {
        closing = true;
        DialogueBridge d = plugin.dialogueBridge();
        if (d != null) {
            d.claim(null);
        }
        listeners.clear();
        if (facade != null) {
            Bukkit.getServicesManager().unregister(NpcSocialFacade.class, facade);
        }
        io.shutdown();
        try {
            if (io.awaitTermination(15, TimeUnit.SECONDS)) {
                store.close();
            } else {
                plugin.getLogger().warning("NPC affinity worker shutdown timeout");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("NPC affinity close failed");
        }
        HandlerList.unregisterAll(this);
        cache.clear();
    }
}
