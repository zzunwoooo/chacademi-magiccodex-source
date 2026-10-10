package kr.chacademy.story.dialogue;

import java.util.HashMap;
import java.util.Map;

/** 호감도 값 주고받기. 서버가 대화를 열 때 "npc=점수,npc=점수" 로 보내 준다. */
public final class Affinity {
    /** 기록이 없을 때 점수. ChacaNPC 의 "아는 사이"(25)와 같게. */
    public static final int DEFAULT = 25;

    private Affinity() {}

    public static Map<String, Integer> decode(String data) {
        Map<String, Integer> map = new HashMap<>();
        if (data == null || data.isBlank()) return map;
        for (String part : data.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(part.substring(0, eq).trim(), Integer.parseInt(part.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }
}
