package kr.chacademy.npc.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.logging.Logger;

/**
 * MagicCodexBridge의 공개 facade(school.magiccodex.paper.NpcSocialFacade)를 리플렉션으로 호출한다.
 * facade 클래스는 Bridge JAR에만 있으므로 클래스 로더 충돌이 없다 (시그니처는 JDK·Bukkit 타입만).
 * 모든 호출은 메인 스레드에서, 반환 future도 메인 스레드에서 완료된다 (Bridge 계약).
 */
public final class MagicCodexLink implements Listener {

    public static final String FACADE = "school.magiccodex.paper.NpcSocialFacade";
    public static final int API_VERSION = 1;

    private final Plugin plugin;
    private final Logger log;
    private volatile Object facade;
    private final Map<String, Method> methods = new HashMap<>();
    private Runnable onConnect = () -> { };

    public MagicCodexLink(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void onConnect(Runnable r) {
        this.onConnect = r == null ? () -> { } : r;
    }

    public boolean available() {
        return facade != null;
    }

    /** ServicesManager에서 facade를 찾는다. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized boolean connect() {
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices()) {
            if (!type.getName().equals(FACADE)) {
                continue;
            }
            RegisteredServiceProvider reg = Bukkit.getServicesManager().getRegistration((Class) type);
            if (reg == null) {
                continue;
            }
            Object provider = reg.getProvider();
            try {
                methods.clear();
                for (Method m : type.getMethods()) {
                    methods.put(m.getName(), m);
                }
                int version = ((Number) methods.get("apiVersion").invoke(provider)).intValue();
                if (version != API_VERSION) {
                    log.warning("[ChacaNPC] MagicCodexBridge NPC API 버전이 다릅니다 (" + version + " ≠ " + API_VERSION + ") — 연결하지 않음");
                    facade = null;
                    return false;
                }
                facade = provider;
                log.info("[ChacaNPC] MagicCodexBridge NPC API 연결됨 (호감도·스토리 대화·퀘스트)");
                onConnect.run();
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("[ChacaNPC] MagicCodexBridge 연결 실패: " + e.getClass().getSimpleName());
                facade = null;
                return false;
            }
        }
        facade = null;
        return false;
    }

    @EventHandler
    public void onRegister(ServiceRegisterEvent e) {
        if (e.getProvider().getService().getName().equals(FACADE)) {
            connect();
        }
    }

    @EventHandler
    public void onUnregister(ServiceUnregisterEvent e) {
        if (e.getProvider().getService().getName().equals(FACADE)) {
            facade = null;
            log.warning("[ChacaNPC] MagicCodexBridge NPC API 연결 끊김 — 호감도 기본값, 퀘스트·선물 꺼짐");
        }
    }

    // ------------------------------------------------------------------ 호출 도우미

    private Object call(String name, Object... args) throws ReflectiveOperationException {
        Object f = facade;
        Method m = methods.get(name);
        if (f == null || m == null) {
            throw new IllegalStateException("MagicCodexBridge not connected");
        }
        return m.invoke(f, args);
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> future(String name, Object... args) {
        try {
            Object r = call(name, args);
            return r instanceof CompletableFuture<?> cf ? (CompletableFuture<T>) cf
                    : CompletableFuture.failedFuture(new IllegalStateException("not a future"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ------------------------------------------------------------------ API

    /** Optional v1 extension: plain configured nickname, preserving old/disconnected bridge fallback. */
    public String playerName(Player player) {
        try {
            Object value = call("playerName", player);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older v1 bridge or unavailable integration: no nickname source.
        }
        return player.getName();
    }

    /** {score, heart} */
    public CompletableFuture<int[]> affinity(UUID player, String npc) {
        return future("affinity", player, npc);
    }

    /** {score, heart, applied} */
    public CompletableFuture<int[]> addAffinity(UUID player, String npc, String source, int amount) {
        return future("addAffinity", player, npc, source, amount);
    }

    public CompletableFuture<String> nickname(UUID player, String npc) {
        return future("nickname", player, npc);
    }

    public CompletableFuture<String> beginGift(UUID player, String npc, String token, int delta) {
        return future("beginGift", player, npc, token, delta);
    }

    public CompletableFuture<int[]> commitGift(UUID player, String npc, String token) {
        return future("commitGift", player, npc, token);
    }

    public CompletableFuture<Boolean> cancelGift(String token) {
        return future("cancelGift", token);
    }

    /** "OPENED" / "NONE" / "BUSY" / "BLOCKED" */
    public CompletableFuture<String> openStory(Player player, int citizensId, Entity anchor) {
        return future("openStory", player, citizensId, anchor);
    }

    public void claim(IntPredicate claimed) {
        try {
            call("claimCitizensNpcs", claimed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 연결 안 됨
        }
    }

    public CompletableFuture<String> storySummary(UUID player) {
        return future("storySummary", player);
    }

    public boolean questOfferable(Player player, String questId) {
        try {
            return Boolean.TRUE.equals(call("questOfferable", player, questId));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public int activeQuestCount(Player player) {
        try {
            return ((Number) call("activeQuestCount", player)).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    public String questTitle(String questId) {
        try {
            Object r = call("questTitle", questId);
            return r == null ? "" : r.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    public CompletableFuture<Boolean> acceptQuest(Player player, String questId) {
        return future("acceptQuest", player, questId);
    }

    public void addSocialListener(Consumer<Map<String, String>> listener) {
        try {
            call("addSocialListener", listener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 연결 안 됨
        }
    }

    public void removeSocialListener(Consumer<Map<String, String>> listener) {
        try {
            call("removeSocialListener", listener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 연결 안 됨
        }
    }
}
