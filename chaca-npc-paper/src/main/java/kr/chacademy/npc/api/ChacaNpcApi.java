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

    /**
     * 마법 383 마음 읽기 (MagicCodexBridge LifeSpells 가 리플렉션으로 부름, 메인 스레드):
     * 이 엔티티가 캐릭터에 연결된 Citizens NPC 면 캐릭터 id, 아니면 null.
     */
    public static String characterOf(org.bukkit.entity.Entity entity) {
        ChacaNpcPlugin p = ChacaNpcPlugin.instance();
        if (p == null || entity == null) {
            return null;
        }
        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);
        return npc == null ? null : p.npcs().charIdFor(npc.getId());
    }

    /** 캐릭터 이름, 없으면 null (마법 383). */
    public static String characterName(String npcId) {
        ChacaNpcPlugin p = ChacaNpcPlugin.instance();
        CharacterSheet c = p == null || npcId == null ? null : p.characters().get(npcId);
        return c == null ? null : c.name();
    }

    /** 좋아하는 선물 아이템 키 (gift_likes 의 loved + liked, GiftService.keyOf 형식), 없으면 빈 목록 (마법 383). */
    public static java.util.List<String> likedGifts(String npcId) {
        ChacaNpcPlugin p = ChacaNpcPlugin.instance();
        CharacterSheet c = p == null || npcId == null ? null : p.characters().get(npcId);
        var out = new java.util.ArrayList<String>();
        if (c == null || c.giftLikes() == null) {
            return out;
        }
        for (String k : java.util.List.of("loved", "liked")) {
            var list = c.giftLikes().get(k);
            if (list != null) {
                for (String key : list) {
                    if (key != null && !key.isBlank() && !out.contains(key)) {
                        out.add(key);
                    }
                }
            }
        }
        return out;
    }
}
