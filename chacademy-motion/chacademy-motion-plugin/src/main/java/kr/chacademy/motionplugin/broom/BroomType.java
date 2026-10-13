package kr.chacademy.motionplugin.broom;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 빗자루 한 종류의 조작 수치. brooms.yml 에서 읽는다.
 *
 * @param id        모델 파일 이름과 같은 ID (모드의 assets/chacademy_motion/brooms/&lt;id&gt;.bbmodel)
 * @param maxSpeed  최고 속도, 블록/틱 (1.0 = 초당 20블록)
 * @param accel     W 를 누를 때 틱마다 붙는 속도
 * @param turn      느릴 때 틱마다 돌 수 있는 각도(도). 빠를수록 이 값의 40% 까지 줄어든다
 * @param brake     S 를 누를 때 틱마다 줄어드는 속도 비율 (0.03 = 3%)
 * @param stability 균형 게이지가 깎이는 정도를 나누는 값. 클수록 덜 떨어진다
 * @param climb     상승 키의 양력
 */
public record BroomType(String id, String name, String grade, float maxSpeed, float accel, float turn, float brake,
                        float stability, float climb) {

    static BroomType read(String id, ConfigurationSection s) {
        String grade=s.getString("grade","");int tier=grade.contains("전설")?3:grade.contains("유일")||grade.contains("희귀")?2:grade.contains("고급")?1:0;
        return new BroomType(id,
                s.getString("name", id),
                new String[]{"일반","고급","유일","전설"}[tier],
                clamp(s.getDouble("max-speed", 0.9)*(.85+tier*.13), 0.2, 2.5),
                clamp(s.getDouble("accel", 0.035)*(.75+tier*.25), 0.005, 0.2),
                clamp(s.getDouble("turn", 10)*(.85+tier*.16), 1, 30),
                clamp(s.getDouble("brake", 0.035)*(.8+tier*.22), 0.005, 0.3),
                clamp(s.getDouble("stability", 1.0)*(.85+tier*.25), 0.3, 3),
                clamp(s.getDouble("climb", 0.05), 0.01, 0.2));
    }

    private static float clamp(double v, double lo, double hi) {
        return (float) Math.max(lo, Math.min(hi, v));
    }
}
