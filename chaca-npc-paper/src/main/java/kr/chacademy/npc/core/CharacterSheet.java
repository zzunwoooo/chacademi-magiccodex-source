package kr.chacademy.npc.core;

import java.util.List;
import java.util.Map;

/**
 * 캐릭터 파일(npcs/*.yml) 하나의 내용. Bukkit 의존성 없음.
 */
public record CharacterSheet(
        String id,
        String name,
        String type,              // sub / main
        String gender,
        String skin,
        String color,             // 채팅 이름 색 (#RRGGBB) 또는 null
        String portrait,          // HUD 초상화 이름 (magiccodex:textures/gui/dialogue/<이름>.png), 없으면 ""
        boolean romanceable,
        boolean openOnRightClick,
        Persona persona,
        List<ScheduleEntry> schedule,
        List<String> quests,
        List<String> hintTopics,
        Map<String, Relation> relations,
        Map<String, List<String>> fallbackLines,
        Map<Integer, List<String>> sampleLinesByStage,
        Map<String, List<String>> giftLikes    // loved / liked / disliked → 아이템 키 목록
) {

    public record Persona(
            String role,
            String personality,
            String speech,
            List<String> likes,
            List<String> dislikes,
            List<String> knows,
            List<String> neverKnows,
            List<String> sampleLines
    ) {
    }

    /** 다른 NPC와의 관계. gossip = 소문을 전할 확률(0~1) */
    public record Relation(String type, double gossip) {
    }

    public boolean hasSchedule() {
        return schedule != null && !schedule.isEmpty();
    }

    /** 고정 대사 하나를 고른다. 없으면 기본값 목록에서. */
    public String fallback(String key, Map<String, List<String>> defaults, java.util.Random random) {
        List<String> lines = fallbackLines == null ? null : fallbackLines.get(key);
        if (lines == null || lines.isEmpty()) {
            lines = defaults == null ? null : defaults.get(key);
        }
        if (lines == null || lines.isEmpty()) {
            return "...";
        }
        return lines.get(random.nextInt(lines.size()));
    }
}
