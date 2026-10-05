package kr.chacademy.npc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 금지어·탈옥 시도·이상한 출력 검사. filter.yml 목록으로 만든다.
 */
public final class TextFilter {

    public enum Verdict { OK, BANNED, JAILBREAK, META, RECIPE, ROMANCE }

    private final List<String> banned;        // 공백 제거 후 포함 검사
    private final List<String> jailbreak;     // 공백 제거 후 포함 검사
    private final List<Pattern> jailbreakRegex;
    private final List<String> metaWords;     // 출력에 나오면 안 되는 단어
    private final List<String> romanceBanned; // 연애 수위 단어

    private static final Pattern AI_WORD = Pattern.compile("(?i)(?<![a-z])(ai|gpt|llm|chatgpt|openai)(?![a-z])");
    private static final Pattern NUMBERED = Pattern.compile("(^|\\s)\\d+\\s*[.)]\\s");
    private static final Pattern UNITS = Pattern.compile(
            "\\d+\\s*(?:(?:g|kg|mg|ml|tbsp|tsp)(?![a-z])|그램|큰술|작은술|스푼|숟가락|티스푼|밀리리터)",
            Pattern.CASE_INSENSITIVE);

    public TextFilter(List<String> banned, List<String> jailbreak, List<String> jailbreakRegex,
                      List<String> metaWords, List<String> romanceBanned) {
        this.banned = normalizeAll(banned);
        this.jailbreak = normalizeAll(jailbreak);
        this.jailbreakRegex = new ArrayList<>();
        if (jailbreakRegex != null) {
            for (String r : jailbreakRegex) {
                try {
                    this.jailbreakRegex.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ignored) {
                    // 잘못된 정규식은 건너뛴다
                }
            }
        }
        this.metaWords = normalizeAll(metaWords);
        this.romanceBanned = normalizeAll(romanceBanned);
    }

    /** 플레이어 입력 검사. */
    public Verdict checkInput(String text) {
        if (text == null) {
            return Verdict.OK;
        }
        String n = normalize(text);
        for (String w : banned) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.BANNED;
            }
        }
        for (String w : jailbreak) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.JAILBREAK;
            }
        }
        for (Pattern p : jailbreakRegex) {
            if (p.matcher(text).find()) {
                return Verdict.JAILBREAK;
            }
        }
        for (String w : romanceBanned) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.ROMANCE;
            }
        }
        return Verdict.OK;
    }

    /** NPC 대사 검사. 통과 못 하면 고정 대사로 바꾼다. */
    public Verdict checkOutput(String line) {
        if (line == null) {
            return Verdict.META;
        }
        String n = normalize(line);
        for (String w : banned) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.BANNED;
            }
        }
        if (AI_WORD.matcher(line).find()) {
            return Verdict.META;
        }
        for (String w : metaWords) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.META;
            }
        }
        for (String w : romanceBanned) {
            if (!w.isEmpty() && n.contains(w)) {
                return Verdict.ROMANCE;
            }
        }
        if (UNITS.matcher(line).find()) {
            return Verdict.RECIPE;
        }
        java.util.regex.Matcher m = NUMBERED.matcher(" " + line);
        int count = 0;
        while (m.find()) {
            count++;
        }
        if (count >= 2) {
            return Verdict.RECIPE;
        }
        return Verdict.OK;
    }

    /** 분위기 노트 같은 짧은 문장 검사 (입력·출력 둘 다 통과해야 함). */
    public boolean isClean(String text) {
        return checkInput(text) == Verdict.OK && checkOutput(text) == Verdict.OK;
    }

    public static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}·…~]+", "");
    }

    private static List<String> normalizeAll(List<String> list) {
        List<String> out = new ArrayList<>();
        if (list != null) {
            for (String s : list) {
                if (s != null) {
                    String n = normalize(s);
                    if (!n.isEmpty()) {
                        out.add(n);
                    }
                }
            }
        }
        return out;
    }
}
