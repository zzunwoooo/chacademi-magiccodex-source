package kr.chacademy.storyplugin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * MagicCodexBridge / PlaceholderAPI 와 연결. 둘 다 컴파일 때 필요 없도록 리플렉션으로 부른다.
 * <ul>
 *   <li>한글 닉네임: NpcSocialFacade.playerName(Player) → 없으면 %user_nickname% → 없으면 마인크래프트 닉네임</li>
 *   <li>호감도: NpcSocialFacade.affinity / addAffinity. ChacaNPC 도 같은 곳을 쓰므로 AI NPC 와 스토리가 같은 점수를 본다</li>
 * </ul>
 * facade 는 메인 스레드에서만 부르고, future 도 메인 스레드에서 끝난다 (MagicCodex 약속).
 */
public final class MagicCodexLink {
    public static final String FACADE = "school.magiccodex.paper.NpcSocialFacade";
    /** MagicCodex affinity.yml 의 출처 이름. 기본 하루 한도 0(없음). */
    public static final String SOURCE = "dialogue";

    private final Logger log;
    private Object facade;
    private final Map<String, Method> methods = new HashMap<>();
    private Method papi;
    private boolean papiLooked;

    public MagicCodexLink(Logger log) {
        this.log = log;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean connect() {
        facade = null;
        methods.clear();
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices()) {
            if (!type.getName().equals(FACADE)) continue;
            RegisteredServiceProvider reg = Bukkit.getServicesManager().getRegistration((Class) type);
            if (reg == null) continue;
            try {
                for (Method m : type.getMethods()) methods.put(m.getName(), m);
                int v = ((Number) methods.get("apiVersion").invoke(reg.getProvider())).intValue();
                if (v != 1) {
                    log.warning("MagicCodexBridge NPC API 버전이 달라요 (" + v + ") — 호감도는 이 플러그인 파일에 따로 저장");
                    methods.clear();
                    return false;
                }
                facade = reg.getProvider();
                log.info("MagicCodexBridge 연결 완료 (한글 닉네임, 호감도를 ChacaNPC 와 같이 씀)");
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("MagicCodexBridge 연결 실패: " + e);
                methods.clear();
            }
        }
        return false;
    }

    public boolean available() {
        return facade != null;
    }

    private Object call(String name, Object... args) throws ReflectiveOperationException {
        Method m = methods.get(name);
        if (facade == null || m == null) throw new NoSuchMethodException(name);
        return m.invoke(facade, args);
    }

    // ---------------------------------------------------------------- 이름

    /** MagicCodex 에서 정한 한글 닉네임 (칭호 없이). */
    public String nickname(Player p) {
        if (facade != null) {
            try {
                Object v = call("playerName", p);
                if (v instanceof String s && !s.isBlank()) return s;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        String v = placeholder(p, "%user_nickname%");
        if (v != null && !v.isBlank() && !v.contains("%")) return v;
        return p.getName();
    }

    /** PlaceholderAPI 로 계산. PlaceholderAPI 가 없으면 null. */
    public String placeholder(OfflinePlayer p, String text) {
        if (!papiLooked) {
            papiLooked = true;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true,
                                    Bukkit.getPluginManager().getPlugin("PlaceholderAPI").getClass().getClassLoader())
                            .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    log.warning("PlaceholderAPI 연결 실패: " + e);
                }
            }
        }
        if (papi == null) return null;
        try {
            Object v = papi.invoke(null, p, text);
            return v instanceof String s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public void resetPlaceholderLookup() {
        papiLooked = false;
        papi = null;
    }

    // ---------------------------------------------------------------- 호감도

    /** 점수. 실패하면 null 로 끝남. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Integer> affinity(UUID player, String npc) {
        try {
            return ((CompletableFuture<int[]>) call("affinity", player, npc)).handle((r, e) -> e != null || r == null ? null : r[0]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** 바꾼 뒤 점수. 실패하면 null. MagicCodex 의 하트 단계 상한·하루 한도가 그대로 적용된다. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Integer> addAffinity(UUID player, String npc, int amount) {
        try {
            return ((CompletableFuture<int[]>) call("addAffinity", player, npc, SOURCE, amount))
                    .handle((r, e) -> e != null || r == null ? null : r[0]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
