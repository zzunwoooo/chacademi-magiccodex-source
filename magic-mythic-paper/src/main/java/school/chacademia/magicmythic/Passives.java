package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;

/**
 * Slot-equipped passives (they work while the spell sits in one of the player's magic key slots, see SpellSlots).
 * Casting the spell itself only plays its visual; the gameplay lives here.
 *
 *  017 trajectory_acceleration 미라지 슈터 - any damage the player deals (owner must be the player: melee, own
 *      projectile or own spell) summons one homing wind arrow at that target. mana per trigger.
 *  030 resonant_arrow 미스틱 슈터 - direct attacks only (melee / own arrow, not spells): one extra wind arrow.
 *      Both can trigger from the same hit; the extra arrows never trigger either passive again (proc flag).
 *  049 glass_firing 광석 재련 - ore drops are smelted (furnace recipe). mana per ore block.
 *  052 flame_footprints 불꽃 걸음 - mana per second; footprints behind the player explode after a fuse.
 *  055 glaze_coating 화염의 정복자 - immune to fire damage (in fire, burning) and to the 화상 status.
 *  121 automatic_supply 자동 보급 - the last building block in hand is refilled from the inventory.
 * Numbers: config.yml passives.* (all placeholders until the balance review).
 */
final class Passives implements Listener {
    private final MagicMythicPlugin plugin;
    private final Map<UUID, Long> icd = new HashMap<>();
    private final Map<UUID, Footsteps> steps = new HashMap<>();
    private final Map<Material, ItemStack> smelt = new HashMap<>();
    private long tick = 0;

    Passives(MagicMythicPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private ConfigurationSection cfg(String id) {
        var s = plugin.getConfig().getConfigurationSection("passives." + id);
        return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
    }
    private boolean on(Player p, String id) {
        return p.isOnline() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
            && plugin.slots().equipped(p, id) && plugin.protection().at(p.getLocation());
    }
    private double damage(Player p, ConfigurationSection c) {
        return Math.max(0, c.getDouble("base-damage", 0) + c.getDouble("power-ratio", 0) * Links.power(p));
    }

    // ------------------------------------------------------------------ 017 / 030 extra wind arrows
    /** Who dealt this hit, when it is a player: melee, a projectile they shot, or our own spell damage. */
    static Player owner(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dealt(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || e.getFinalDamage() <= 0) return;
        if (plugin.protection().inProc()) return; // the extra arrows themselves
        Player owner = owner(e.getDamager());
        if (owner == null || !plugin.targets().enemy(owner, victim)) return;
        boolean magic = plugin.protection().magicDamaging();
        boolean direct = !magic && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
            || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE && e.getDamager() instanceof AbstractArrow);
        if (on(owner, "trajectory_acceleration")) arrow(owner, victim, "trajectory_acceleration");
        if (direct && on(owner, "resonant_arrow")) arrow(owner, victim, "resonant_arrow");
    }

    private void arrow(Player owner, LivingEntity victim, String id) {
        var c = cfg(id);
        long key = c.getLong("internal-cooldown-ticks", 0);
        UUID k = new UUID(owner.getUniqueId().getMostSignificantBits() ^ id.hashCode(), owner.getUniqueId().getLeastSignificantBits());
        if (key > 0 && icd.getOrDefault(k, -1L) > tick) return;
        if (!Links.consume(owner, c.getDouble("mana", 0))) return; // not enough mana: no arrow (no message spam)
        if (key > 0) icd.put(k, tick + key);
        double dmg = damage(owner, c);
        Links.vfx(owner, id, "external:valid_direct_damage", victim.getLocation(), victim.getUniqueId());
        int delay = Math.clamp(c.getInt("delay-ticks", 0), 0, 100);
        Runnable fire = () -> {
            if (!owner.isOnline() || !victim.isValid() || victim.isDead()) return;
            var params = new Params(Map.of("speed", String.valueOf(c.getDouble("speed", 1.6)), "range", String.valueOf(c.getDouble("range", 24)),
                "radius", "0.5", "homing", "true", "proc", "true", "spell", id, "element", "wind"));
            var before = SpellTrace.set(SpellTrace.of(params));
            try { new BoltMech(plugin, params).run(new Mech.Ctx(null, owner, victim, victim.getLocation(), dmg)); }
            finally { SpellTrace.set(before); }
        };
        if (delay == 0) fire.run(); else Bukkit.getScheduler().runTaskLater(plugin, fire, delay);
    }

    // ------------------------------------------------------------------ 049 ore smelting
    private static boolean ore(Material m) { return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS; }

    ItemStack smelted(ItemStack in) {
        ItemStack result = smelt.computeIfAbsent(in.getType(), m -> {
            var probe = new ItemStack(m);
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof FurnaceRecipe f && f.getInputChoice().test(probe)) return f.getResult();
            }
            return new ItemStack(Material.AIR);
        });
        if (result.getType().isAir()) return null;
        var out = result.clone();
        out.setAmount(Math.min(out.getMaxStackSize(), in.getAmount() * result.getAmount()));
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void drops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!ore(e.getBlockState().getType()) || e.getItems().isEmpty() || !on(p, "glass_firing")) return;
        var results = new LinkedHashMap<Item, ItemStack>();
        for (Item item : e.getItems()) { ItemStack out = smelted(item.getItemStack()); if (out != null) results.put(item, out); }
        if (results.isEmpty() || !Links.consume(p, cfg("glass_firing").getDouble("mana", 3))) return; // nothing to smelt = no mana
        results.forEach(Item::setItemStack);
        boolean any = true;
        if (any) Links.vfx(p, "glass_firing", "external:smelt", e.getBlock().getLocation().add(0.5, 0.5, 0.5), null);
    }

    // ------------------------------------------------------------------ 055 burn immunity
    // Owner 2026-10-19: 화염의 정복자 = immune to fire (standing in fire, burning) and to the 화상 status.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void burning(EntityDamageEvent e) {
        var cause = e.getCause();
        if ((cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.FIRE) && e.getEntity() instanceof Player p && on(p, "glaze_coating"))
            e.setCancelled(true);
    }
    /** 화염의 정복자 equipped (StatusEffects refuses 화상). */
    boolean fireImmune(Player p) { return on(p, "glaze_coating"); }

    // ------------------------------------------------------------------ 121 refill
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        ItemStack used = e.getItemInHand();
        if (used.getAmount() != 1 || !used.getType().isBlock() || !used.getType().isSolid()) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || !on(p, "automatic_supply")) return;
        EquipmentSlot hand = e.getHand();
        ItemStack template = used.asOne();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            var inv = p.getInventory();
            ItemStack now = inv.getItem(hand);
            if (now != null && !now.getType().isAir()) return;
            int handSlot = hand == EquipmentSlot.HAND ? inv.getHeldItemSlot() : 40;
            for (int slot = 0; slot < 36; slot++) {
                if (slot == handSlot) continue;
                ItemStack it = inv.getItem(slot);
                if (it == null || !it.isSimilar(template)) continue;
                inv.setItem(hand, it);
                inv.setItem(slot, null);
                Links.vfx(p, "automatic_supply", "external:refill", p.getLocation(), null);
                return;
            }
        });
    }

    // ------------------------------------------------------------------ 052 footprints
    private static final class Footsteps {
        Location last; boolean paid; long nextPay; final ArrayDeque<Object[]> pending = new ArrayDeque<>(); // Location, explodeTick
    }

    private void tick() {
        tick++;
        if ((tick & 1023) == 0) icd.values().removeIf(t -> t < tick);
        var c = cfg("flame_footprints");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Footsteps f = steps.get(p.getUniqueId());
            boolean active = on(p, "flame_footprints");
            if (!active) { if (f != null && f.pending.isEmpty()) steps.remove(p.getUniqueId()); }
            else {
                if (f == null) { f = new Footsteps(); steps.put(p.getUniqueId(), f); }
                if (tick >= f.nextPay) { f.paid = Links.consume(p, c.getDouble("mana-per-second", 10)); f.nextPay = tick + 20; }
                Location at = p.getLocation();
                if (f.paid && p.isOnGround() && (f.last == null || f.last.getWorld() != at.getWorld()
                    || f.last.distanceSquared(at) >= Math.pow(c.getDouble("spacing", 1.5), 2))) {
                    f.last = at.clone();
                    if (f.pending.size() < c.getInt("max-active", 12)) {
                        f.pending.add(new Object[]{at.clone(), tick + Math.clamp(c.getInt("fuse-ticks", 30), 1, 200)});
                        Links.vfx(p, "flame_footprints", "external:step", at, null);
                    }
                }
            }
            if (f == null) continue;
            while (!f.pending.isEmpty() && (long) f.pending.peek()[1] <= tick) {
                Location at = (Location) f.pending.poll()[0];
                if (!p.isOnline() || at.getWorld() == null || !at.getWorld().equals(p.getWorld())) continue;
                explode(p, at, c);
            }
        }
        steps.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void explode(Player p, Location at, ConfigurationSection c) {
        if (!plugin.protection().pair(p, at)) return;
        double r = c.getDouble("radius", 1.5), dmg = damage(p, c);
        MagicImpactEvent.fire(p, "fire", at, r, MagicImpactEvent.Kind.HIT);
        Links.vfx(p, "flame_footprints", "external:explode", at, null);
        for (LivingEntity t : plugin.targets().enemiesNear(p, at, r, 8)) {
            if (plugin.targets().hurt(p, t, dmg)) {
                int ignite = c.getInt("ignite-ticks", 0);
                if (ignite > 0) plugin.status().apply(p, t, school.magiccodex.protocol.StatusProtocol.Status.BURN, ignite, 0); // 화상 (no vanilla fire)
            }
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) { steps.remove(e.getPlayer().getUniqueId()); BuffMech.clearAll(e.getPlayer()); }
}
