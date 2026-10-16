package school.chacademia.housing;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Vault 경제 (리플렉션, MagicCodexBridge 상점과 같은 방식). 메인 스레드에서만 부른다. */
final class Economy {
    private record Provider(Class<?> type, Object provider) {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Provider provider() {
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices())
            if (type.getName().equals("net.milkbowl.vault.economy.Economy")) {
                var r = Bukkit.getServicesManager().getRegistration((Class) type);
                if (r != null) return new Provider(type, r.getProvider());
            }
        return null;
    }

    static boolean available() { return provider() != null; }

    static double balance(OfflinePlayer p) {
        var e = provider();
        if (e == null) return -1;
        try { double v = ((Number) e.type().getMethod("getBalance", OfflinePlayer.class).invoke(e.provider(), p)).doubleValue(); return Double.isFinite(v) ? v : -1; }
        catch (ReflectiveOperationException | RuntimeException err) { return -1; }
    }

    /** @return true = 빠짐 (잔액 부족·경제 없음·실패면 false) */
    static boolean withdraw(OfflinePlayer p, long amount) {
        if (amount <= 0) return true;
        var e = provider();
        if (e == null || balance(p) < amount) return false;
        return call(e, "withdrawPlayer", p, amount);
    }

    static boolean deposit(OfflinePlayer p, long amount) {
        if (amount <= 0) return true;
        var e = provider();
        return e != null && call(e, "depositPlayer", p, amount);
    }

    private static boolean call(Provider e, String method, OfflinePlayer p, long amount) {
        try {
            Object result = e.type().getMethod(method, OfflinePlayer.class, double.class).invoke(e.provider(), p, (double) amount);
            return (boolean) result.getClass().getMethod("transactionSuccess").invoke(result);
        } catch (ReflectiveOperationException | RuntimeException err) { return false; }
    }

    private Economy() {}
}
