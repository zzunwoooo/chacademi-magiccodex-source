package kr.chacademy.npc.api;

import kr.chacademy.npc.ChacaNpcPlugin;
import kr.chacademy.npc.core.CharacterSheet;

import java.util.UUID;

/**
 * 다른 플러그인용 공개 API (선택). MagicCodex 연결은 MagicCodexBridge의 NpcSocialFacade를
 * ChacaNPC가 리플렉션으로 호출하는 방식이라, 이 클래스를 다른 JAR에 포함하지 말 것.
 */
public final class ChacaNpcApi {

    private ChacaNpcApi() {
    }

    private static ChacaNpcPlugin plugin() {
        ChacaNpcPlugin p = ChacaNpcPlugin.instance();
        if (p == null) {
            throw new IllegalStateException("ChacaNPC가 아직 켜지지 않았습니다");
        }
        return p;
    }

    /**
     * 사건 기록 → 관계도를 따라 소문이 된다.
     * type: gift / heart / ending / quest_done / quest_accept / promise / nickname (heart·ending은 연애 소문)
     */
    public static void recordEvent(UUID player, String playerName, String npcId, String type, String text) {
        plugin().social().recordEvent(player, playerName, npcId, type, text);
    }

    public static CharacterSheet character(String npcId) {
        return plugin().characters().get(npcId);
    }
}
