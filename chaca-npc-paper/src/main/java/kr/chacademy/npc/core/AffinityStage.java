package kr.chacademy.npc.core;

/**
 * 호감도 단계. 실제 점수는 나중에 MagicCodexBridge가 관리한다.
 */
public enum AffinityStage {
    STRANGER(0, "낯선 사람", "예의 바르지만 거리감 있게 대한다."),
    ACQUAINTANCE(20, "아는 사이", "편하게 대화한다."),
    FRIEND(40, "친한 사이", "장난도 치고 고민도 털어놓는다."),
    BEST_FRIEND(60, "단짝", "비밀도 털어놓을 만큼 믿는다."),
    SPECIAL(80, "특별한 사이", "설레고 수줍어한다. 표현은 손잡기 정도까지, 노골적인 표현은 절대 하지 않는다.");

    public final int minScore;
    public final String label;
    public final String attitude;

    AffinityStage(int minScore, String label, String attitude) {
        this.minScore = minScore;
        this.label = label;
        this.attitude = attitude;
    }

    /**
     * 하트 이벤트 단계(heart)까지 반영한 단계: 점수 단계와 heart 중 작은 쪽.
     * (MagicCodex 연결 전처럼 하트 정보가 없으면 heart = 4 로 넘긴다.)
     */
    public static AffinityStage of(int score, int heart, boolean romanceable) {
        AffinityStage byScore = of(score, romanceable);
        int idx = Math.min(byScore.ordinal(), Math.max(0, heart));
        return values()[idx];
    }

    public static AffinityStage of(int score, boolean romanceable) {
        AffinityStage result = STRANGER;
        for (AffinityStage s : values()) {
            if (score >= s.minScore) {
                result = s;
            }
        }
        if (result == SPECIAL && !romanceable) {
            return BEST_FRIEND;
        }
        return result;
    }
}
