package kr.chacademy.portrait.core;

/**
 * 이미지 모델 지시문 조립. 플레이어 문장은 정해진 "추가 요청" 칸에만 들어가며, 줄바꿈·제어문자·따옴표 남용을 정리한다.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static String build(String base, String appearanceTemplate, String requestTemplate,
                               String appearance, String request) {
        StringBuilder sb = new StringBuilder(base == null ? "" : base.strip());
        String a = clean(appearance, 600);
        if (!a.isEmpty() && appearanceTemplate != null && !appearanceTemplate.isBlank()) {
            sb.append("\n").append(appearanceTemplate.replace("{appearance}", a));
        }
        String r = clean(request, 200);
        if (!r.isEmpty() && requestTemplate != null && !requestTemplate.isBlank()) {
            // 사용자 문장은 따옴표로 감싸 하나의 값으로만 전달
            sb.append("\n").append(requestTemplate.replace("{request}", "\"" + r.replace("\"", "'") + "\""));
        }
        return sb.toString();
    }

    /** 한 줄로 만들고 제어문자·§ 제거, 길이 제한. */
    public static String clean(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(c -> {
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
            } else if (c >= 0x20 && c != 0x7F && c != '§' && !(c >= 0x80 && c < 0xA0)) {
                out.appendCodePoint(c);
            }
        });
        String r = out.toString().replaceAll(" {2,}", " ").strip();
        if (r.codePointCount(0, r.length()) > maxChars) {
            r = r.substring(0, r.offsetByCodePoints(0, maxChars));
        }
        return r;
    }
}
