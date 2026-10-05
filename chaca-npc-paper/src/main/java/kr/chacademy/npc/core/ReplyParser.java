package kr.chacademy.npc.core;

import java.util.Map;
import java.util.Set;

/**
 * AI가 돌려준 JSON을 검사해서 안전한 값만 남긴다.
 * AI는 "제안"만 하고, 허락되지 않은 퀘스트·힌트·소문 id는 여기서 버린다.
 */
public final class ReplyParser {

    private ReplyParser() {
    }

    /** 검사를 통과한 NPC 대답. */
    public record AiReply(String line, int mood, String quest, String hint, String memo,
                          String promise, Long rumorId, boolean end) {
    }

    public record Limits(int lineMax, int memoMax, Set<String> allowedQuests, Set<String> allowedHints,
                         Set<Long> offeredRumors) {
    }

    /**
     * @return 검사된 대답, 대사를 하나도 못 건지면 null
     */
    public static AiReply parse(String raw, Limits limits) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Map<String, Object> m;
        try {
            m = Json.parseObject(raw.trim());
        } catch (RuntimeException ex) {
            // 출력이 잘린 경우: 대사만이라도 건진다
            String partial = StreamingLineExtractor.decodeLine(raw);
            String line = cleanLine(partial, limits.lineMax());
            return line == null ? null : new AiReply(line, 0, null, null, null, null, null, false);
        }
        String line = cleanLine(Json.str(m, "line"), limits.lineMax());
        if (line == null) {
            return null;
        }
        int mood = (int) Math.max(-2, Math.min(2, Json.num(m, "mood", 0)));

        String quest = Json.str(m, "quest");
        if (quest != null && (limits.allowedQuests() == null || !limits.allowedQuests().contains(quest))) {
            quest = null;
        }
        String hint = Json.str(m, "hint");
        if (hint != null && (limits.allowedHints() == null || !limits.allowedHints().contains(hint))) {
            hint = null;
        }
        String memo = shortText(Json.str(m, "memo"), limits.memoMax());
        String promise = shortText(Json.str(m, "promise"), limits.memoMax());

        Long rumorId = null;
        Object r = m.get("rumor");
        if (r != null && limits.offeredRumors() != null) {
            try {
                long id = Long.parseLong(r.toString().replaceAll("[^0-9]", ""));
                if (limits.offeredRumors().contains(id)) {
                    rumorId = id;
                }
            } catch (NumberFormatException ignored) {
                // 무시
            }
        }
        boolean end = Boolean.TRUE.equals(m.get("end"));
        return new AiReply(line, mood, quest, hint, memo, promise, rumorId, end);
    }

    /** 대사 정리: 공백 정리, 길이 제한(가능하면 문장 끝에서 자름). */
    public static String cleanLine(String line, int max) {
        if (line == null) {
            return null;
        }
        String s = line.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        String cut = s.substring(0, max);
        int best = -1;
        for (String end : new String[]{". ", "! ", "? ", "~ ", ".", "!", "?", "~"}) {
            int idx = cut.lastIndexOf(end);
            if (idx > best) {
                best = idx;
            }
        }
        if (best >= max / 2) {
            return cut.substring(0, best + 1).trim();
        }
        return cut.trim() + "…";
    }

    private static String shortText(String s, int max) {
        if (s == null) {
            return null;
        }
        s = s.replace('\n', ' ').trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
