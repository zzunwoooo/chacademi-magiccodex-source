package kr.chacademy.npc.core;

import java.util.List;

/**
 * 퀘스트·힌트·대화 턴 같은 작은 데이터 묶음.
 */
public final class Defs {

    private Defs() {
    }

    /** AI가 제안할 수 있는 퀘스트 (quests.yml). 내용과 보상은 고정, AI는 말로만 꺼낸다. */
    public record QuestDef(String id, String title, String summary, int minScore, List<String> acceptCommands) {
    }

    /**
     * 마법 힌트 (hints.yml). difficulty 1 = 구체적(쉬움) ~ 3 = 막연함(어려움).
     * vague = 호감도가 낮을 때 재료 대신 AI에게 주는 막연한 설명 (없으면 "" → 서버가 만든 기본 문장).
     */
    public record HintDef(String id, String spell, String topic, String materials, int difficulty, String vague) {
    }

    /** 대화 한 턴. speaker = "player" 또는 "npc" */
    public record Turn(String speaker, String text) {
        public boolean isPlayer() {
            return "player".equals(speaker);
        }
    }

    /** 프롬프트에 넣을 소문 하나. */
    public record RumorView(long id, String text, String type, long eventCreatedAt) {
        public boolean isRomance() {
            return "heart".equals(type) || "ending".equals(type) || "gift_romance".equals(type);
        }
    }

    /** 대화 추천 버튼. */
    public record Button(String id, String label) {
    }
}
