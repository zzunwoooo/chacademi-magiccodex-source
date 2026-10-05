package school.magiccodex.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 외부 플러그인(ChacaNPC)용 공개 facade. Bukkit ServicesManager에 등록된다.
 *
 * <p>클래스 로더 충돌을 피하려고 이 클래스는 MagicCodexBridge JAR에만 있다. 호출하는 쪽은
 * {@code getKnownServices()}에서 이름으로 찾아 리플렉션으로 부른다. 그래서 시그니처에는 JDK·Bukkit 타입만 쓴다.
 *
 * <p>모든 메서드는 메인 스레드에서 호출하고, 반환된 future도 메인 스레드에서 완료된다.
 */
public final class NpcSocialFacade {

    public static final int API_VERSION = 1;

    private final NpcSocialService service;

    NpcSocialFacade(NpcSocialService service) {
        this.service = service;
    }

    public int apiVersion() {
        return API_VERSION;
    }

    // ------------------------------------------------------------------ 호감도

    /** {score, heart} */
    public CompletableFuture<int[]> affinity(UUID player, String npc) {
        return service.affinity(player, npc).thenApply(r -> new int[]{r.score(), r.heart()});
    }

    /**
     * 호감도 변화. source별 하루 한도(affinity.yml)는 서버 간 공유 DB 트랜잭션으로 지킨다.
     * 반환 {score, heart, applied}
     */
    public CompletableFuture<int[]> addAffinity(UUID player, String npc, String source, int amount) {
        return service.add(player, npc, source, amount)
                .thenApply(r -> new int[]{r.row().score(), r.row().heart(), r.applied()});
    }

    public CompletableFuture<String> nickname(UUID player, String npc) {
        return service.affinity(player, npc).thenApply(r -> r.nickname());
    }

    public CompletableFuture<Boolean> setNickname(UUID player, String npc, String nickname) {
        return service.setNickname(player, npc, nickname).thenApply(r -> true);
    }

    // ------------------------------------------------------------------ 선물 (2단계)

    /** 1단계: 하루 횟수 예약 + pending 기록. "OK" / "CAP" / "DUPLICATE" */
    public CompletableFuture<String> beginGift(UUID player, String npc, String token, int delta) {
        return service.beginGift(player, npc, token, delta).thenApply(Enum::name);
    }

    /** 2단계: 아이템 차감 후 확정. {score, heart, applied}, 이미 처리된 토큰이면 null */
    public CompletableFuture<int[]> commitGift(UUID player, String npc, String token) {
        return service.commitGift(player, npc, token)
                .thenApply(r -> r == null ? null : new int[]{r.row().score(), r.row().heart(), r.applied()});
    }

    /** 아이템 차감 전 취소. 하루 횟수를 돌려준다. */
    public CompletableFuture<Boolean> cancelGift(String token) {
        return service.cancelGift(token);
    }

    // ------------------------------------------------------------------ 스토리 대화

    /**
     * 이 Citizens NPC에 묶인 고정 대화 중 지금 열 수 있는 것을 연다.
     * 기존 권한·조건·진행 상태 검사를 그대로 쓴다.
     * "OPENED" = 열었음 / "NONE" = 열 수 있는 고정 대화 없음 / "BUSY" = 처리 중이라 판단 보류 /
     * "BLOCKED" = 모드 미설치·검토 대기 등 (안내 메시지는 Bridge가 보냄)
     * NONE 일 때만 AI 대화를 열어야 한다.
     */
    public CompletableFuture<String> openStory(Player player, int citizensId, Entity anchor) {
        return service.openStory(player, citizensId, anchor);
    }

    /** 이 NPC id들은 호출한 플러그인이 클릭을 처리한다 (Bridge의 Citizens 클릭 처리에서 제외). null = 해제 */
    public void claimCitizensNpcs(IntPredicate claimed) {
        service.claim(claimed);
    }

    /** 메인 퀘스트 진행 요약 (story.* 상태). 예) "첫 번째 이야기: 2" */
    public CompletableFuture<String> storySummary(UUID player) {
        return service.storySummary(player);
    }

    // ------------------------------------------------------------------ 퀘스트 (기존 MagicCodex 데이터)

    /** 지금 이 플레이어에게 제안할 수 있는 퀘스트인지 (존재·공개·권한·진행 중 아님). */
    public boolean questOfferable(Player player, String questId) {
        QuestBridge q = service.quests();
        return q != null && q.npcOfferable(player, questId);
    }

    public int activeQuestCount(Player player) {
        QuestBridge q = service.quests();
        return q == null ? 0 : q.activeCount(player);
    }

    public String questTitle(String questId) {
        QuestBridge q = service.quests();
        return q == null ? "" : q.title(questId);
    }

    /** 기존 의뢰 수락 경로(조건·권한·중복 검사 포함)로 수락. */
    public CompletableFuture<Boolean> acceptQuest(Player player, String questId) {
        QuestBridge q = service.quests();
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        if (q == null) {
            f.complete(false);
        } else {
            q.acceptFromDialogue(player, questId, f::complete);
        }
        return f;
    }

    // ------------------------------------------------------------------ 사건 알림

    /** 하트 이벤트·엔딩·호칭 설정 알림. map 키: player, playerName, npc, type(heart/ending/nickname), value */
    public void addSocialListener(Consumer<Map<String, String>> listener) {
        service.addListener(listener);
    }

    public void removeSocialListener(Consumer<Map<String, String>> listener) {
        service.removeListener(listener);
    }
}
