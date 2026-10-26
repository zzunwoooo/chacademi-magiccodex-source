package school.magiccodex.paper;

import java.util.*;
import java.util.random.RandomGenerator;

/** 뽑기 상자 순수 규칙 (Bukkit 없음 — JUnit 대상): 등급 이름, 가중치 추첨, 대표 연출 등급, 확률 표. */
final class LootBoxRules {
    private LootBoxRules() {}

    static final String[] GRADE_NAMES = {"일반", "고급", "유일", "전설"};
    private static final String[] GRADE_KEYS = {"common", "rare", "unique", "legendary"};

    /** "일반"/"고급"/"유일"/"전설" 또는 common/rare/unique/legendary 또는 0~3. 모르면 -1. */
    static int grade(String text) {
        if (text == null) return -1;
        String t = text.strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < 4; i++) if (t.equals(GRADE_NAMES[i]) || t.equals(GRADE_KEYS[i]) || t.equals(Integer.toString(i))) return i;
        return -1;
    }

    static String gradeName(int g) { return g >= 0 && g < 4 ? GRADE_NAMES[g] : "?"; }

    /**
     * 가중치 추첨 한 번. weights[i] &lt;= 0 인 보상은 나오지 않는다. 전부 0 이면 -1.
     * 결과는 서버가 이 함수로 한 번만 정하고 저널에 적는다 (다시 굴리지 않는다).
     */
    static int draw(int[] weights, RandomGenerator random) {
        long total = 0;
        for (int w : weights) if (w > 0) total += w;
        if (total <= 0) return -1;
        long roll = random.nextLong(total);
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) continue;
            if (roll < weights[i]) return i;
            roll -= weights[i];
        }
        return -1; // 닿지 않음
    }

    /** count 번 추첨. 하나라도 실패하면 null. */
    static int[] drawMany(int[] weights, int count, RandomGenerator random) {
        var out = new int[count];
        for (int i = 0; i < count; i++) { out[i] = draw(weights, random); if (out[i] < 0) return null; }
        return out;
    }

    /**
     * 대표 연출 등급 = 결과 중 가장 높은 등급.
     * 기존 정책이 없어서 Claude 가 정함 (오너 확인 필요) — docs/handoff/CLAUDE_OPENING_FINAL_PATCHES_20261026.md 참고.
     */
    static int representative(int[] grades) {
        int best = 0;
        for (int g : grades) best = Math.max(best, g);
        return Math.clamp(best, 0, 3);
    }

    /** 가중치 → 백분율 문자열 (소수 둘째 자리까지, 끝의 0 은 뗌). */
    static String percent(long weight, long total) {
        if (total <= 0 || weight <= 0) return "0%";
        double p = weight * 100.0 / total;
        String s = String.format(Locale.ROOT, "%.2f", p);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "%";
    }

    /** 상자 id 규칙: 영문 소문자·숫자·_- 1~48자. 이름(한글)에서 id 를 만들 수 없으면 "box" + 번호. */
    static String id(String name, Collection<String> taken) {
        String base = name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty() || base.length() > 40) base = "box";
        String id = base;
        for (int n = 2; taken.contains(id); n++) id = base + "_" + n;
        return id;
    }
}
