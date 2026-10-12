package school.chacademia.magicmythic;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 빗자루 비행(ChacademyMotion 플러그인) 연결. 컴파일 의존 없이 리플렉션으로 부른다.
 * ChacademyMotion 이 없거나 꺼져 있으면 모든 메서드가 "빗자루 없음"으로 동작하고,
 * 비행 마법은 예전처럼 바닐라 비행(10초)으로 처리된다.
 *
 * ChacademyMotionPlugin 쪽 공개 메서드:
 *   boolean broomFlight(Player, int practiceTicks)  - 타고 있으면 내리고, 아니면 탄다. 모드가 없으면 false
 *   boolean isRidingBroom(Player)
 *   void stopBroom(Player)
 */
final class BroomLink {
    private static Plugin cachedPlugin;
    private static Method flight, riding, stop;

    private BroomLink() {}

    private static Plugin plugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("ChacademyMotion");
        if (p == null || !p.isEnabled()) return null;
        if (p != cachedPlugin) {
            try {
                flight = p.getClass().getMethod("broomFlight", Player.class, int.class);
                riding = p.getClass().getMethod("isRidingBroom", Player.class);
                stop = p.getClass().getMethod("stopBroom", Player.class);
                cachedPlugin = p;
            } catch (ReflectiveOperationException | LinkageError e) {
                cachedPlugin = null;
                return null;
            }
        }
        return p;
    }

    /** 비행 마법. true = 빗자루로 처리함(탔거나 내렸음), false = 빗자루 비행 불가(모드 없음 등) → 예전 비행으로 */
    static Boolean flight(Player player, int practiceTicks) {
        Plugin p = plugin();
        if (p == null || !player.getListeningPluginChannels().contains("chacademy:broom_ride")) return null;
        try {
            return Boolean.TRUE.equals(flight.invoke(p, player, practiceTicks));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static boolean riding(Player player) {
        Plugin p = plugin();
        if (p == null) return false;
        try {
            return Boolean.TRUE.equals(riding.invoke(p, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static void stop(Player player) {
        Plugin p = plugin();
        if (p == null) return;
        try {
            stop.invoke(p, player);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
