package dev.portablevfx.client;

import java.util.Set;

/**
 * [기능:보스이펙트항상 시작]
 * 11/07 오너: 마법 이펙트(다른 사람) 줄이기를 켜면 보스 패턴까지 안 보이던 문제 → 보스 패턴 이펙트는 설정과 상관없이 항상 원래대로.
 * EffectRuntime.opacityExempt 몸통을 이걸로 바꾼다 (기존 기믹 팩 목록·shrine 그대로 + 보스 팩 접두사).
 * [기능:보스이펙트항상 끝]
 */
public final class BossOpacity {
    private static final Set<String> PACKS = Set.of("elementreactions", "conduitturn", "dimensionmark", "lightbeam", "mirrortune", "prismtint",
        "rewind", "statuewake", "steamignition", "stoneweight", "thawingheat", "vineawaken", "waterlevel");
    /** 보스: 재의 그림자 as_ · 모르디안 mordian · 호로겐 tw_ · 모르바스 archlich · 하베스트 pr_ · (11/09 4장) 엘리시아 elysia_ · 벨자르 belzar_ · 해방된 악마 freeddemon_ · 룰레타 roulette_ · 솜니아 somnia_ · (11/11 빼빼로데이) 쇼콜라 pepchocola·pepsweet·pepgimmick */
    private static final String[] BOSS = {"as_", "mordian", "tw_", "archlich", "pr_", "elysia_", "belzar_", "freeddemon_", "roulette_", "somnia_", "pepchocola", "pepsweet", "pepgimmick"};

    private BossOpacity() {}

    public static boolean exempt(String id) {
        if (id == null || !id.startsWith("claude:")) return false;
        int slash = id.indexOf('/', 7);
        String pack = slash < 0 ? id.substring(7) : id.substring(7, slash);
        if (PACKS.contains(pack) || pack.startsWith("shrine")) return true;
        for (String b : BOSS) if (pack.startsWith(b)) return true;
        return false;
    }
}
