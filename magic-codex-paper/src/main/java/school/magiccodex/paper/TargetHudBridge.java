package school.magiccodex.paper;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import school.magiccodex.protocol.TargetHudProtocol;

/**
 * Target health HUD (top of the screen, CHACADEMI_BOSS_HUD design, owner 2026-10-19).
 * Target: the last entity this player fought (hit it or got hit by it) during the last 10 s, otherwise the entity
 * under the crosshair (the taming information ray: ModelEngine sub-hitboxes, 25 blocks). Players are shown
 * (Korean nickname, their mana), the player's own MCPets pets are not. Vanilla mobs have no mana bar.
 * The number above the bar is the damage this player dealt to that target (kept until it dies or 10 min idle).
 *
 * Cost: one pass every 5 ticks over players that registered the channel; the ray only runs for players without a
 * combat target; a packet only when something changed (or once a second as keepalive).
 */
final class TargetHudBridge implements Listener, AutoCloseable {
    private static final long COMBAT_TICKS = 200, DAMAGE_KEEP_TICKS = 20 * 600;
    private static final double LOOK_RANGE = 25, KEEP_RANGE = 48;

    private record Combat(UUID target, long tick) {}
    private record Sent(byte[] bytes, long tick) {}

    private final MagicCodexBridge plugin;
    private final TamingBridge taming;
    private final ManaService mana;
    private final DisplayNames names;
    private final Map<UUID, Combat> combat = new HashMap<>();
    /** player -> target -> {damage, lastTick} */
    private final Map<UUID, Map<UUID, double[]>> dealt = new HashMap<>();
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final BukkitTask task;

    TargetHudBridge(MagicCodexBridge plugin, TamingBridge taming, ManaService mana, DisplayNames names) {
        this.plugin = plugin; this.taming = taming; this.mana = mana; this.names = names;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, TargetHudProtocol.CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    private static long now() { return Bukkit.getCurrentTick(); }

    // ------------------------------------------------------------------ combat + damage

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) return;
        LivingEntity attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        long now = now();
        if (attacker instanceof Player p) {
            combat.put(p.getUniqueId(), new Combat(victim.getUniqueId(), now));
            double real = Math.min(e.getFinalDamage(), victim.getHealth() + victim.getAbsorptionAmount());
            if (real > 0) {
                var mine = dealt.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
                if (mine.size() > 256) mine.entrySet().removeIf(x -> now - x.getValue()[1] > DAMAGE_KEEP_TICKS);
                double[] d = mine.computeIfAbsent(victim.getUniqueId(), k -> new double[2]);
                d[0] += real; d[1] = now;
            }
        }
        if (victim instanceof Player hurt && !(attacker instanceof ArmorStand))
            combat.put(hurt.getUniqueId(), new Combat(attacker.getUniqueId(), now));
    }

    private static LivingEntity attacker(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile pr) { ProjectileSource s = pr.getShooter(); if (s instanceof LivingEntity le) return le; }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof LivingEntity le) return le;
        return null;
    }

    @EventHandler public void death(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        for (var m : dealt.values()) m.remove(id);
        combat.values().removeIf(c -> c.target().equals(id));
        combat.remove(id);
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        combat.remove(id); dealt.remove(id); sent.remove(id);
        combat.values().removeIf(c -> c.target().equals(id));
    }

    // ------------------------------------------------------------------ target

    private LivingEntity target(Player p, long now) {
        Combat c = combat.get(p.getUniqueId());
        if (c != null) {
            Entity e = now - c.tick() <= COMBAT_TICKS ? Bukkit.getEntity(c.target()) : null;
            if (e instanceof LivingEntity le && shown(p, le, KEEP_RANGE, true)) return le;
            combat.remove(p.getUniqueId());
        }
        LivingEntity look;
        try { look = taming.lookTarget(p, LOOK_RANGE); } catch (RuntimeException | LinkageError ex) { look = null; }
        return look != null && shown(p, look, LOOK_RANGE + 2, false) ? look : null;
    }

    private boolean shown(Player p, LivingEntity e, double range, boolean fighting) {
        if (!e.isValid() || e.isDead() || e.equals(p) || e.getWorld() != p.getWorld()) return false;
        if (e instanceof ArmorStand || e.hasMetadata("NPC") || e.getScoreboardTags().contains("chacademia_taming_fx")) return false;
        if (p.getLocation().distanceSquared(e.getLocation()) > range * range) return false;
        if (e instanceof Player other) {
            if (other.getGameMode() == GameMode.SPECTATOR || !p.canSee(other)) return false;
            if (!fighting && (other.isInvisible() || other.hasPotionEffect(PotionEffectType.INVISIBILITY))) return false;
        } else if (!fighting && e.isInvisible() && !taming.configuredMythic(e)) return false;
        if (taming.pets().ownedBy(e, p.getUniqueId())) return false;
        return true;
    }

    // ------------------------------------------------------------------ frame

    private TargetHudProtocol.Show frame(Player viewer, LivingEntity e, boolean fighting) {
        int kind; String name = "", key = "";
        boolean shiny = false, boss = false; double chance = -1;
        if (e instanceof Player other) {
            kind = TargetHudProtocol.KIND_PLAYER;
            name = names.name(other);
        } else {
            kind = taming.configuredMythic(e) ? TargetHudProtocol.KIND_CUSTOM : TargetHudProtocol.KIND_VANILLA;
            var info = taming.hudInfo(e);
            shiny = info.shiny(); boss = info.boss(); chance = info.chance();
            name = info.name();
            if (name.isBlank() && e.getCustomName() != null) name = clean(e.getCustomName());
            if (name.isBlank()) { try { key = e.getType().translationKey(); } catch (RuntimeException | LinkageError ex) { name = e.getName(); } }
        }
        if (!boss && e instanceof Boss) boss = true;
        var max = e.getAttribute(Attribute.MAX_HEALTH);
        float maxHealth = (float) Math.max(1, Math.max(e.getHealth(), max == null ? 20 : max.getValue()));
        float m = -1, mm = 0;
        if (e instanceof Player other) {
            var s = mana.snapshot(other.getUniqueId());
            if (s.isPresent() && s.get().maximum() > 0) { m = (float) s.get().current(); mm = (float) s.get().maximum(); }
        }
        var mine = dealt.get(viewer.getUniqueId());
        double[] d = mine == null ? null : mine.get(e.getUniqueId());
        int flags = (shiny ? TargetHudProtocol.FLAG_SHINY : 0) | (boss ? TargetHudProtocol.FLAG_BOSS : 0) | (fighting ? TargetHudProtocol.FLAG_COMBAT : 0);
        return new TargetHudProtocol.Show(e.getEntityId(), e.getUniqueId(), kind, flags, name, key,
            (float) e.getHealth(), maxHealth, m, mm, d == null ? 0 : (float) d[0], (float) chance);
    }

    private static String clean(String s) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', s)).replaceAll("<[^>]*>", "").replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long now = now();
        if (now % 1200 == 0) for (var m : dealt.values()) m.entrySet().removeIf(x -> now - x.getValue()[1] > DAMAGE_KEEP_TICKS);
        if (now % 100 == 0) combat.values().removeIf(c -> now - c.tick() > COMBAT_TICKS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getListeningPluginChannels().contains(TargetHudProtocol.CHANNEL)) continue;
            byte[] bytes;
            try {
                LivingEntity t = p.isDead() || p.getGameMode() == GameMode.SPECTATOR ? null : target(p, now);
                Combat c = combat.get(p.getUniqueId());
                bytes = TargetHudProtocol.encode(t == null ? new TargetHudProtocol.Hide()
                    : frame(p, t, c != null && c.target().equals(t.getUniqueId())));
            } catch (RuntimeException ex) {
                continue;
            }
            Sent last = sent.get(p.getUniqueId());
            boolean hide = bytes.length <= 2;
            if (last != null && Arrays.equals(last.bytes(), bytes) && (hide || now - last.tick() < 20)) continue;
            if (last == null && hide) continue;
            sent.put(p.getUniqueId(), new Sent(bytes, now));
            p.sendPluginMessage(plugin, TargetHudProtocol.CHANNEL, bytes);
        }
    }

    @Override public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
        combat.clear(); dealt.clear(); sent.clear();
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, TargetHudProtocol.CHANNEL);
    }
}
