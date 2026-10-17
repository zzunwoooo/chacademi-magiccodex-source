package school.chacademia.kyudichi;

import java.util.List;

/** Completion only; accepted command arguments and match rules stay in AdminCommand. */
final class TestCommandSuggestions {
    private static final List<String> OPTIONS = List.of("준비:0", "시간:3", "스니치:0", "무적", "블러저:끔");
    private TestCommandSuggestions() {}

    static List<String> complete(String[] args, List<String> arenas) {
        if (args.length < 3) return List.of();
        String prefix = args[args.length - 1];
        if (args[1].equals("참가")) return filter(args.length == 3 ? arenas : args.length == 4 ? List.of("A", "B") : List.of(), prefix);
        if (args[1].equals("종료")) return filter(args.length == 3 ? arenas : List.of(), prefix);
        return OPTIONS.stream().filter(option -> {
            String key = key(option);
            for (int i = 2; i < args.length - 1; i++) if (key(args[i]).equals(key)) return false;
            return option.startsWith(prefix);
        }).toList();
    }

    private static String key(String value) { int colon = value.indexOf(':'); return colon < 0 ? value : value.substring(0, colon); }
    private static List<String> filter(List<String> values, String prefix) { return values.stream().filter(v -> v.startsWith(prefix)).toList(); }
}
