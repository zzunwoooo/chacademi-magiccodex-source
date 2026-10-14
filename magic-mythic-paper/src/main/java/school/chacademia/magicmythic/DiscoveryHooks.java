package school.chacademia.magicmythic;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Acquisition (획득 조건) counters only this plugin can see, sent to MagicDiscovery's DiscoveryService.signal.
 * Keys must also be defined in magic-discovery-paper discoveries.yml (unknown keys are ignored there).
 *   kill.spell.<id>     - a kill whose last hit (within 3 s) was this spell   (128 산맥의 등: pebble_toss x30)
 *   kill.light_undead   - undead killed by light-element magic                 (147 태양의 심판 x300)
 *   cleanse.poison|weakness|slowness - removed by purifying_wave               (141 여명의 의식)
 *   heal.magic          - health restored by spell healing (anyone)             (164 성좌의 대리인 x600)
 *   heal.magic_other    - health restored on other players                      (157 마력 전선 x100)
 *   mana.exactly_zero   - current mana hit exactly 0                            (161 경험의 환전)
 *   cast.six_elements   - spells of all six elements within 30 s               (163 별빛 재현)
 */
final class DiscoveryHooks implements Listener {
    private record Last(UUID caster, String spell, String element, long at) {}
    private static final Map<UUID, Last> LAST = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> ELEMENTS = new HashMap<>();
    static final Set<String> SIX = Set.of("wind", "fire", "water", "earth", "light", "dark");
    private final MagicMythicPlugin plugin;

    DiscoveryHooks(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 4, 4);
    }

    // ------------------------------------------------------------------ inputs from mechanics
    static void hit(Player caster, LivingEntity target, SpellTrace.Now now) {
        if (now == null || now.spell().isBlank()) return;
        LAST.put(target.getUniqueId(), new Last(caster.getUniqueId(), now.spell(), now.element(), System.currentTimeMillis()));
        if (LAST.size() > 4096) { long cut = System.currentTimeMillis() - 3000; LAST.values().removeIf(l -> l.at() < cut); }
    }

    static void cast(Player caster, SpellTrace.Now now) {
        if (now == null || !SIX.contains(now.element())) return;
        long t = System.currentTimeMillis();
        var mine = ELEMENTS.computeIfAbsent(caster.getUniqueId(), k -> new HashMap<>());
        mine.put(now.element(), t);
        if (sixWithin(mine, t, 30_000)) { mine.clear(); signal(caster, "cast.six_elements", 1); }
    }
    static boolean sixWithin(Map<String, Long> times, long now, long window) {
        int n = 0;
        for (String e : SIX) { Long at = times.get(e); if (at != null && now - at <= window) n++; }
        return n == SIX.size();
    }

    static void healed(LivingEntity caster, LivingEntity target, double amount) {
        if (amount <= 0 || !(caster instanceof Player pl)) return;
        signal(pl, "heal.magic", amount);
        if (!target.equals(caster) && target instanceof Player) signal(pl, "heal.magic_other", amount);
    }

    static void cleansed(LivingEntity caster, String spell, String effectKey) {
        if (caster instanceof Player pl && "purifying_wave".equals(spell)) signal(pl, "cleanse." + effectKey, 1);
    }

    // ------------------------------------------------------------------ kills
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent e) {
        Last last = LAST.remove(e.getEntity().getUniqueId());
        if (last == null || System.currentTimeMillis() - last.at() > 3000) return;
        Player caster = Bukkit.getPlayer(last.caster());
        if (caster == null) return;
        signal(caster, "kill.spell." + last.spell(), 1);
        if ("light".equals(last.element()) && Targets.undead(e.getEntity())) signal(caster, "kill.light_undead", 1);
    }

    // ------------------------------------------------------------------ mana 0 (polled; the Bridge has no mana event)
    private final Set<UUID> empty = new HashSet<>();
    private void poll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Double mana = Links.mana(p);
            if (mana == null) continue;
            if (mana > 1e-9) { empty.remove(p.getUniqueId()); continue; }
            if (empty.add(p.getUniqueId())) signal(p, "mana.exactly_zero", 1); // once per time it reaches 0
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) { ELEMENTS.remove(e.getPlayer().getUniqueId()); empty.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ DiscoveryService
    private static Object service; private static Method signal;
    static boolean signal(Player p, String key, double amount) {
        var plugin = Bukkit.getPluginManager().getPlugin("MagicDiscovery");
        if (plugin == null || !plugin.isEnabled()) return false;
        try {
            if (service == null || signal == null) {
                Class<?> type = Class.forName("school.magiccodex.discovery.DiscoveryService", false, plugin.getClass().getClassLoader());
                service = Bukkit.getServicesManager().load(type);
                signal = type.getMethod("signal", UUID.class, String.class, double.class);
            }
            return service != null && Boolean.TRUE.equals(signal.invoke(service, p.getUniqueId(), key, amount));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { service = null; signal = null; return false; }
    }
    static void reset() { service = null; signal = null; LAST.clear(); ELEMENTS.clear(); }
}
