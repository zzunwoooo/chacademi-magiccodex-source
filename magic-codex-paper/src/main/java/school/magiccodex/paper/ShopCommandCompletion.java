package school.magiccodex.paper;

import java.util.*;
import java.util.stream.Stream;

/** Pure completion over already-loaded snapshots: no database, filesystem or Bukkit calls. */
final class ShopCommandCompletion {
    private static final List<String> ADMIN = List.of("생성", "아이템추가", "가격", "NPC", "초상", "목록", "기록", "처리");
    /** /상점관리 처리 <거래ID> 뒤에 오는 처리 방법. */
    static final List<String> RESOLUTIONS = List.of("완료", "환불", "취소");

    static List<String> complete(String command, String[] args, boolean player, boolean shopAllowed,
            boolean adminAllowed, Map<String, ShopStore.Shop> catalog, Collection<String> npcKeys,
            Collection<String> onlineIds) {
        return complete(command, args, player, shopAllowed, adminAllowed, catalog, npcKeys, onlineIds, List.of());
    }

    /** orderIds: 이 관리자가 방금 `기록`으로 조회한 미완료 거래 ID (없으면 빈 목록). */
    static List<String> complete(String command, String[] args, boolean player, boolean shopAllowed,
            boolean adminAllowed, Map<String, ShopStore.Shop> catalog, Collection<String> npcKeys,
            Collection<String> onlineIds, Collection<String> orderIds) {
        if (command == null || args == null || args.length == 0 || args.length > 5
                || Arrays.stream(args).anyMatch(Objects::isNull)) return List.of();
        String prefix = args[args.length - 1];
        if (command.equals("상점"))
            // /상점 <ID> 는 관리자 전용이다. 일반 유저는 NPC를 눌러서만 상점을 연다.
            return player && shopAllowed && adminAllowed && args.length == 1 ? match(catalog.keySet(), prefix) : List.of();
        if (!(command.equals("상점관리")) || !adminAllowed) return List.of();
        if (args.length == 1)
            return match(ADMIN.stream().filter(s -> player || !s.equals("아이템추가")).toList(), prefix);
        String sub = args[0];
        if (sub.equals("생성")) return List.of(); // New IDs and names are free input, not existing shops.
        if (sub.equals("기록")) return args.length == 2 ? match(onlineIds, prefix) : List.of();
        if (sub.equals("처리"))
            return args.length == 2 ? match(orderIds, prefix) : args.length == 3 ? match(RESOLUTIONS, prefix) : List.of();
        if (!ADMIN.contains(sub) || (sub.equals("아이템추가") && !player)) return List.of();
        if (args.length == 2) return match(catalog.keySet(), prefix);
        ShopStore.Shop shop = catalog.get(args[1]);
        if (shop == null) return List.of();
        return switch (sub) {
            case "아이템추가" -> args.length == 3 ? prices(shop.products(), true, prefix)
                    : args.length == 4 && validPrice(args[2]) ? prices(shop.products(), false, prefix) : List.of();
            case "가격" -> {
                if (args.length == 3) yield match(shop.products().stream().map(ShopStore.Product::id).toList(), prefix);
                var product = shop.products().stream().filter(p -> p.id().equals(args[2])).findFirst().orElse(null);
                if (product == null) yield List.of();
                if (args.length == 4) yield prices(List.of(product), true, prefix);
                yield args.length == 5 && validPrice(args[3]) ? prices(List.of(product), false, prefix) : List.of();
            }
            case "NPC" -> args.length == 3 ? match(npcKeys, prefix) : List.of();
            case "초상" -> args.length == 3
                    ? match(Stream.concat(Stream.of("elena-neutral"),
                            catalog.values().stream().map(ShopStore.Shop::portrait)).toList(), prefix) : List.of();
            default -> List.of();
        };
    }

    private static boolean validPrice(String value) {
        try { ShopStore.price(value); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }

    private static List<String> prices(Collection<ShopStore.Product> products, boolean buy, String prefix) {
        return match(Stream.concat(Stream.of("off", "0"),
                products.stream().map(p -> buy ? p.buy() : p.sell()).filter(s -> !s.isEmpty())).toList(), prefix);
    }

    private static List<String> match(Collection<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(Objects::nonNull).filter(s -> !s.isEmpty())
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().sorted().limit(256).toList();
    }
}
