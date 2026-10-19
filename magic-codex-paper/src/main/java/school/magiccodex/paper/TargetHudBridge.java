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
 * Boss near (64 blocks): the boss is always the top bar (even while fighting normal mobs; the one I fight first,
 * else the nearest), and a second bar under it shows the normal mob under the crosshair.
 * No boss: the last entity this player fought (hit it or got hit by it) during the last 10 s, otherwise the entity
 * under the crosshair (the taming information ray: ModelEngine sub-hitboxes, 25 blocks). Players are shown
 * (Korean nickname, their mana), the player's own MCPets pets are not. Vanilla mobs have no mana bar.
 * The number above the bar is the damage this player dealt to that target (kept until it dies or 10 min idle).
 *
 * Cost: one pass every 5 ticks over players that registered the channel; the ray only runs for players without a
 * combat target; a packet only when something changed (or once a second as keepalive).
 */
final class TargetHudBridge implements Listener, AutoCloseable {
    private static final long COMBAT_TICKS = 200, DAMAGE_KEEP_TICKS = 20 * 600;
    private static final double LOOK_RANGE = 25, KEEP_RANGE = 48, BOSS_RANGE = 64;

    private record Combat(UUID target, long tick) {}
    private record Sent(byte[] bytes, long tick) {}
    /** The client's crosshair ray (camera offset from the eyes + view direction), see TargetHudProtocol.AIM_CHANNEL. */
    private record Aim(TargetHudProtocol.Aim ray, long tick) {}
    private static final long AIM_STALE_TICKS = 30;
    private final Map<UUID, Aim> aims = new HashMap<>();
    private final org.bukkit.plugin.messaging.PluginMessageListener aimListener = (channel, player, bytes) -> {
        if (!TargetHudProtocol.AIM_CHANNEL.equals(channel)) return;
        try { aims.put(player.getUniqueId(), new Aim(TargetHudProtocol.decodeAim(bytes), now())); } catch (IllegalArgumentException ignored) {}
    };

    private final MagicCodexBridge plugin;
    private final TamingBridge taming;
    private final ManaService mana;
    private final DisplayNames names;
    private final Map<UUID, Combat> combat = new HashMap<>();
    /** player -> target -> {damage, lastTick} */
    private final Map<UUID, Map<UUID, double[]>> dealt = new HashMap<>();
    private final Map<UUID, Sent> sent = new HashMap<>();
    /** Loaded boss entities, rescanned once a second. */
    private final List<LivingEntity> bosses = new ArrayList<>();
    private Set<String> bossTags = Set.of("chacademia_boss");
    private List<String> bossPrefixes = List.of("BOSS_");
    private final BukkitTask task;
    private long runs;

    TargetHudBridge(MagicCodexBridge plugin, TamingBridge taming, ManaService mana, DisplayNames names) {
        this.plugin = plugin; this.taming = taming; this.mana = mana; this.names = names;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, TargetHudProtocol.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, TargetHudProtocol.AIM_CHANNEL, aimListener);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        var c = plugin.getConfig();
        if (c.isList("target-hud.boss-tags")) bossTags = Set.copyOf(c.getStringList("target-hud.boss-tags"));
        if (c.isList("target-hud.boss-mythic-prefixes")) bossPrefixes = List.copyOf(c.getStringList("target-hud.boss-mythic-prefixes"));
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
        combat.remove(id); dealt.remove(id); sent.remove(id); aims.remove(id);
        combat.values().removeIf(c -> c.target().equals(id));
    }

    // ------------------------------------------------------------------ target

    /** Same rule as MagicMythic Targets.boss: vanilla bosses, boss tag, MythicMobs BOSS_ prefix, taming boss profile. */
    boolean boss(LivingEntity e) {
        if (e instanceof Boss || e instanceof Warden || e instanceof ElderGuardian) return true;
        for (String tag : e.getScoreboardTags()) if (bossTags.contains(tag)) return true;
        String type = taming.mythicType(e);
        if (type != null) for (String prefix : bossPrefixes) if (type.startsWith(prefix)) return true;
        return type != null && taming.hudInfo(e).boss();
    }

    private void scanBosses() {
        bosses.clear();
        for (var w : Bukkit.getWorlds()) for (LivingEntity e : w.getLivingEntities())
            if (!(e instanceof Player) && e.isValid() && !e.isDead() && boss(e)) bosses.add(e);
    }

    /** The boss for the top bar: the one I fought last (10 s), else the nearest within 64 blocks. */
    private LivingEntity boss(Player p, long now) {
        if (bosses.isEmpty()) return null;
        Combat c = combat.get(p.getUniqueId());
        LivingEntity best = null; double bestD = BOSS_RANGE * BOSS_RANGE;
        for (LivingEntity b : bosses) {
            if (!b.isValid() || b.isDead() || b.getWorld() != p.getWorld() || !shown(p, b, BOSS_RANGE, true)) continue;
            if (c != null && now - c.tick() <= COMBAT_TICKS && c.target().equals(b.getUniqueId())) return b;
            double d = b.getLocation().distanceSquared(p.getLocation());
            if (d <= bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /** Entity under the player's crosshair: the client's aim ray when fresh (shoulder camera), else the eye ray (old clients). */
    private LivingEntity look(Player p) {
        LivingEntity look;
        Aim a = aims.get(p.getUniqueId());
        try {
            if (a != null && now() - a.tick() <= AIM_STALE_TICKS) {
                var r = a.ray();
                var origin = p.getEyeLocation().add(r.dx(), r.dy(), r.dz());
                origin.setYaw(r.yaw()); origin.setPitch(r.pitch());
                look = taming.lookTarget(p, origin, origin.getDirection(), LOOK_RANGE);
            } else look = taming.lookTarget(p, LOOK_RANGE);
        } catch (RuntimeException | LinkageError ex) { look = null; }
        return look != null && shown(p, look, LOOK_RANGE + 2, false) ? look : null;
    }

    private LivingEntity target(Player p, long now) {
        Combat c = combat.get(p.getUniqueId());
        if (c != null) {
            Entity e = now - c.tick() <= COMBAT_TICKS ? Bukkit.getEntity(c.target()) : null;
            if (e instanceof LivingEntity le && shown(p, le, KEEP_RANGE, true)) return le;
            combat.remove(p.getUniqueId());
        }
        return look(p);
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
        if (!boss && boss(e)) boss = true;
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
        long run = runs++; // the task runs every 5 ticks
        if (run % 240 == 0) for (var m : dealt.values()) m.entrySet().removeIf(x -> now - x.getValue()[1] > DAMAGE_KEEP_TICKS);
        if (run % 20 == 0) combat.values().removeIf(c -> now - c.tick() > COMBAT_TICKS);
        if (run % 4 == 0) scanBosses();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getListeningPluginChannels().contains(TargetHudProtocol.CHANNEL)) continue;
            byte[] bytes;
            try {
                var shows = new ArrayList<TargetHudProtocol.Show>(2);
                if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                    Combat c = combat.get(p.getUniqueId());
                    LivingEntity top = boss(p, now);
                    if (top != null) {
                        shows.add(frame(p, top, c != null && c.target().equals(top.getUniqueId())));
                        // Under the boss: only the normal mob I am looking at (fighting other mobs does not replace the boss).
                        LivingEntity look = look(p);
                        if (look != null && !look.equals(top) && !(look instanceof Player) && !boss(look))
                            shows.add(frame(p, look, c != null && c.target().equals(look.getUniqueId())));
                    } else {
                        LivingEntity t = target(p, now);
                        if (t != null) shows.add(frame(p, t, c != null && c.target().equals(t.getUniqueId())));
                    }
                }
                bytes = TargetHudProtocol.encode(new TargetHudProtocol.Frame(shows));
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
        combat.clear(); dealt.clear(); sent.clear(); bosses.clear(); aims.clear();
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, TargetHudProtocol.CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, TargetHudProtocol.AIM_CHANNEL, aimListener);
    }
}
