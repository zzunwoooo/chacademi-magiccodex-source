package kr.chacademy.storyplugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 스토리 진행 단계 정의 (plugins/ChacademyStory/steps.yml). Bukkit 에 의존하지 않는다 (테스트용).
 *
 * <pre>
 * 시작: { 단계: ch1-6, 대화완료: ch1-5 }     # 대화완료가 있으면 그 대화를 끝낸 순간 첫 단계가 시작된다 (없으면 첫 접속 때)
 * 단계:
 *   ch1-8:
 *     장: "1장 · 항구 마을"
 *     설명: "피피네 잡화점을 찾아가자."
 *     조건:                                # 다 채우면 "준비 완료" → 포탈·NPC 징표가 보인다
 *       - { 종류: 아이템, 대상: "LEATHER_CHESTPLATE|교복", 개수: 1, 표시: "교복 사기" }
 *     호출: { 종류: NPC클릭, 서버: school, NPC: 12 }   # 자동(지연) · NPC클릭 · 위치 · 컷신
 *     대화: ch1-8                           # 호출되면 띄울 대화 (없으면 컷신, 둘 다 없으면 바로 다음 단계)
 *     컷신: ""
 *     다음: ch1-9                           # 비우면 스토리 끝
 * </pre>
 */
final class QuestDefs {
    static final int MAX_CONDITIONS = 8;
    static final int MAX_BLOCKS = 32;
    static final double MIN_RADIUS = 1.0, MAX_RADIUS = 16.0, DEFAULT_RADIUS = 1.5;
    static final Pattern KEY = Pattern.compile("[\\p{L}\\p{N}_\\-]{1,32}");

    enum CondType {
        ITEM("아이템"), SUBMIT("제출"), KILL("처치"), INTERACT("상호작용"), DIALOGUE("대화완료"), SIGNAL("신호");
        final String ko;
        CondType(String ko) { this.ko = ko; }
        static CondType of(String s) {
            if (s == null) return null;
            for (CondType t : values()) if (t.ko.equals(s) || t.name().equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    enum TrigType {
        NONE("없음"), AUTO("자동"), NPC("NPC클릭"), LOCATION("위치"), CUTSCENE("컷신");
        final String ko;
        TrigType(String ko) { this.ko = ko; }
        static TrigType of(String s) {
            if (s == null) return null;
            for (TrigType t : values()) if (t.ko.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    /** blocks: 상호작용 대상 블록 "월드,x,y,z". */
    record Cond(CondType type, String target, int amount, String label, boolean hidden, List<String> blocks) {
        Cond {
            target = target == null ? "" : target;
            amount = Math.max(1, Math.min(amount, 10_000));
            label = label == null ? "" : label;
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
        Cond withBlocks(List<String> b) { return new Cond(type, target, amount, label, hidden, b); }

        /** 채워야 하는 수. 상호작용은 지정한 블록 수보다 많을 수 없다. */
        int required() { return type == CondType.INTERACT && !blocks.isEmpty() ? Math.min(amount, blocks.size()) : amount; }

        /** HUD 에 보일 글. 표시글이 없으면 종류에 맞게 만든다. */
        String display() {
            if (!label.isBlank()) return label;
            return switch (type) {
                case ITEM -> itemName(target) + " 가지고 있기";
                case SUBMIT -> itemName(target) + " 가져가기";
                case KILL -> (target.equals("*") ? "몬스터" : target.startsWith("이름:") ? target.substring(3) : target) + " 처치하기";
                case INTERACT -> "조사하기";
                case DIALOGUE -> "대화 마치기";
                case SIGNAL -> target + " 하기";
            };
        }

        private static String itemName(String target) {
            int bar = target.indexOf('|');
            if (bar >= 0 && bar + 1 < target.length()) return target.substring(bar + 1);
            if (target.startsWith("이름:")) return target.substring(3);
            return target.toLowerCase(Locale.ROOT);
        }
    }

    static final class Trigger {
        TrigType type = TrigType.NONE;
        String server = "";
        int npc = -1;
        String world = "";
        double x, y, z, radius = DEFAULT_RADIUS;
        String cutscene = "";
        int delay;

        Trigger copy() {
            Trigger t = new Trigger();
            t.type = type; t.server = server; t.npc = npc; t.world = world; t.x = x; t.y = y; t.z = z;
            t.radius = radius; t.cutscene = cutscene; t.delay = delay;
            return t;
        }

        String describe() {
            return switch (type) {
                case NONE -> "없음 (아직 정하지 않음)";
                case AUTO -> "자동" + (delay > 0 ? " (" + delay + "초 뒤)" : "");
                case NPC -> npc < 0 ? "NPC클릭 (NPC 미지정)" : "NPC클릭 (서버 " + server + ", Citizens " + npc + "번)";
                case LOCATION -> String.format(Locale.ROOT, "위치 (%s %.1f %.1f %.1f, 반경 %.1f)", world, x, y, z, radius);
                case CUTSCENE -> "컷신 " + cutscene + " 을(를) 다 보면";
            };
        }
    }

    static final class Step {
        final String id;
        String chapter = "", description = "", dialogue = "", cutscene = "", next = "";
        final List<Cond> conds = new ArrayList<>();
        Trigger trigger = new Trigger();

        Step(String id) { this.id = id; }
    }

    final Map<String, Step> steps = new LinkedHashMap<>();
    String startStep = "";
    String startAfterDialogue = "";

    Step get(String id) { return id == null ? null : steps.get(id); }

    // ------------------------------------------------------------ 읽기

    static QuestDefs parse(Map<?, ?> root, List<String> warnings) {
        QuestDefs d = new QuestDefs();
        if (root == null) return d;
        if (root.get("시작") instanceof Map<?, ?> start) {
            d.startStep = id(start.get("단계"));
            d.startAfterDialogue = id(start.get("대화완료"));
        }
        if (root.get("단계") instanceof Map<?, ?> steps) {
            for (var e : steps.entrySet()) {
                String id = String.valueOf(e.getKey());
                if (!StorySafety.validId(id)) { warnings.add("단계 ID는 영문 소문자·숫자·_·- 1~64자 (무시됨): " + id); continue; }
                if (!(e.getValue() instanceof Map<?, ?> m)) { warnings.add(id + ": 내용이 비어 있음"); d.steps.put(id, new Step(id)); continue; }
                d.steps.put(id, parseStep(id, m, warnings));
            }
        }
        for (Step s : d.steps.values()) {
            if (!s.next.isEmpty() && !d.steps.containsKey(s.next)) warnings.add(s.id + ": 다음 단계 " + s.next + " 이(가) 없음");
        }
        if (!d.startStep.isEmpty() && !d.steps.containsKey(d.startStep)) warnings.add("시작 단계 " + d.startStep + " 이(가) 없음");
        return d;
    }

    private static Step parseStep(String id, Map<?, ?> m, List<String> warnings) {
        Step s = new Step(id);
        s.chapter = text(m.get("장"), 64);
        s.description = text(m.get("설명"), 200);
        s.dialogue = id(m.get("대화"));
        s.cutscene = id(m.get("컷신"));
        s.next = id(m.get("다음"));
        if (m.get("조건") instanceof List<?> list) {
            for (Object o : list) {
                if (s.conds.size() >= MAX_CONDITIONS) { warnings.add(id + ": 조건은 " + MAX_CONDITIONS + "개까지"); break; }
                if (!(o instanceof Map<?, ?> c)) continue;
                CondType t = CondType.of(String.valueOf(c.get("종류")));
                if (t == null) { warnings.add(id + ": 모르는 조건 종류 " + c.get("종류")); continue; }
                List<String> blocks = new ArrayList<>();
                if (c.get("블록") instanceof List<?> bl) for (Object b : bl) if (b != null && blocks.size() < MAX_BLOCKS) blocks.add(String.valueOf(b));
                s.conds.add(new Cond(t, text(c.get("대상"), 64), integer(c.get("개수"), 1), text(c.get("표시"), 64),
                        Boolean.TRUE.equals(c.get("숨김")), blocks));
            }
        }
        if (m.get("호출") instanceof Map<?, ?> c) {
            Trigger t = s.trigger;
            TrigType type = TrigType.of(String.valueOf(c.get("종류")));
            if (type == null) { warnings.add(id + ": 모르는 호출 종류 " + c.get("종류")); type = TrigType.NONE; }
            t.type = type;
            t.server = text(c.get("서버"), 32);
            t.npc = integer(c.get("NPC"), -1);
            t.world = text(c.get("월드"), 64);
            t.x = number(c.get("x")); t.y = number(c.get("y")); t.z = number(c.get("z"));
            t.radius = clampRadius(c.get("반경") == null ? DEFAULT_RADIUS : number(c.get("반경")));
            t.cutscene = id(c.get("컷신"));
            t.delay = Math.max(0, Math.min(600, integer(c.get("지연"), 0)));
        }
        return s;
    }

    static double clampRadius(double r) {
        if (!Double.isFinite(r)) return DEFAULT_RADIUS;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
    }

    private static String id(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o).strip();
        return StorySafety.validId(s) ? s : "";
    }

    private static String text(Object o, int max) {
        if (o == null) return "";
        String s = String.valueOf(o).replace('\n', ' ').replace('\r', ' ').strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static int integer(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? def : Integer.parseInt(String.valueOf(o).strip()); } catch (NumberFormatException e) { return def; }
    }

    private static double number(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? 0 : Double.parseDouble(String.valueOf(o).strip()); } catch (NumberFormatException e) { return 0; }
    }

    // ------------------------------------------------------------ 쓰기

    Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("단계", startStep);
        start.put("대화완료", startAfterDialogue);
        root.put("시작", start);
        Map<String, Object> all = new LinkedHashMap<>();
        for (Step s : steps.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("장", s.chapter);
            m.put("설명", s.description);
            List<Object> conds = new ArrayList<>();
            for (Cond c : s.conds) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("종류", c.type().ko);
                cm.put("대상", c.target());
                cm.put("개수", c.amount());
                if (!c.label().isBlank()) cm.put("표시", c.label());
                if (c.hidden()) cm.put("숨김", true);
                if (!c.blocks().isEmpty()) cm.put("블록", new ArrayList<>(c.blocks()));
                conds.add(cm);
            }
            m.put("조건", conds);
            Trigger t = s.trigger;
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("종류", t.type.ko);
            switch (t.type) {
                case AUTO -> tm.put("지연", t.delay);
                case NPC -> { tm.put("서버", t.server); tm.put("NPC", t.npc); }
                case LOCATION -> { tm.put("월드", t.world); tm.put("x", t.x); tm.put("y", t.y); tm.put("z", t.z); tm.put("반경", t.radius); }
                case CUTSCENE -> tm.put("컷신", t.cutscene);
                default -> { }
            }
            m.put("호출", tm);
            m.put("대화", s.dialogue);
            m.put("컷신", s.cutscene);
            m.put("다음", s.next);
            all.put(s.id, m);
        }
        root.put("단계", all);
        return root;
    }
}
