package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/** One place that decides who a spell may hurt or help, and how damage/knockback is applied. */
final class Targets {
    private final MagicMythicPlugin plugin;
    private Set<String> bossTags = Set.of();
    private List<String> bossPrefixes = List.of();
    private double bossKnock = 0.3;

    Targets(MagicMythicPlugin plugin) { this.plugin = plugin; reload(); }

    void reload() {
        var c = plugin.getConfig();
        bossTags = Set.copyOf(c.getStringList("boss-tags"));
        bossPrefixes = List.copyOf(c.getStringList("boss-mythic-prefixes"));
        bossKnock = Math.clamp(c.getDouble("boss-knockback-multiplier", 0.3), 0, 1);
    }

    /** Mythic internal name or null. */
    static String mythicType(Entity e) {
        try {
            return MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId())
                .map(m -> m.getType().getInternalName()).orElse(null);
        } catch (RuntimeException | LinkageError ignored) { return null; }
    }

    boolean enemy(LivingEntity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e.equals(caster) || !le.isValid() || le.isDead()) return false;
        if (excluded(e)||!plugin.protection().harm(caster,le)) return false;
        // Animals, owned pets, summons and custom mobs are valid attack targets.
        // Creative/spectator players remain outside gameplay combat; WG handles region PvP.
        return !(e instanceof Player p) || playable(p);
    }

    boolean excluded(Entity e){
        return TargetPolicy.excluded(e instanceof ArmorStand, e.hasMetadata("NPC"), e.getScoreboardTags());
    }

    boolean ally(LivingEntity caster, Entity e) {
        if (!(e instanceof Player p) || !playable(p)||excluded(e)||!plugin.protection().pair(caster,p.getLocation())) return false;
        return caster instanceof Player || e.equals(caster);
    }

    boolean boss(LivingEntity e) {
        if (e instanceof EnderDragon || e instanceof Wither || e instanceof Warden || e instanceof ElderGuardian) return true;
        for (String tag : e.getScoreboardTags()) if (bossTags.contains(tag)) return true;
        String mythic = mythicType(e);
        if (mythic != null) for (String prefix : bossPrefixes) if (mythic.startsWith(prefix)) return true;
        return false;
    }

    private static boolean playable(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    /** Enemies around a point, nearest first. */
    List<LivingEntity> enemiesNear(LivingEntity caster, Location center, double radius, int max) {
        if (center.getWorld() == null || radius <= 0) return List.of();
        var list = new ArrayList<LivingEntity>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!enemy(caster, e)) continue;
            LivingEntity le = (LivingEntity) e;
            if (closestDistance(le, center) <= radius) list.add(le);
        }
        list.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(center)));
        return list.size() > max ? list.subList(0, Math.max(0, max)) : list;
    }

    List<Player> alliesNear(LivingEntity caster, Location center, double radius, int max) {
        var list = new ArrayList<Player>();
        if (center.getWorld() == null) return list;
        for (Entity entity : center.getWorld().getNearbyEntities(center,radius,radius,radius)) {
            if(!(entity instanceof Player p))continue;
            if (ally(caster, p) && p.getLocation().distanceSquared(center) <= radius * radius) list.add(p);
        }
        list.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(center)));
        return list.size() > max ? list.subList(0, Math.max(0, max)) : list;
    }

    /** Distance from a point to the entity's bounding box, so large mobs are hit at their edge. */
    static double closestDistance(Entity e, Location p) {
        var box = e.getBoundingBox();
        double x = Math.clamp(p.getX(), box.getMinX(), box.getMaxX());
        double y = Math.clamp(p.getY(), box.getMinY(), box.getMaxY());
        double z = Math.clamp(p.getZ(), box.getMinZ(), box.getMaxZ());
        return Math.sqrt(Math.pow(p.getX() - x, 2) + Math.pow(p.getY() - y, 2) + Math.pow(p.getZ() - z, 2));
    }

    boolean hurt(LivingEntity caster, LivingEntity target, double amount) { return hurt(caster, target, amount, "", ""); }

    /** Spell damage with its element (for {@link MagicDamageEvent}: artifact bonuses). */
    boolean hurt(LivingEntity caster, LivingEntity target, double amount, String element) { return hurt(caster, target, amount, element, ""); }

    boolean hurt(LivingEntity caster, LivingEntity target, double amount, String element, String reaction) {
        if (!enemy(caster,target)) return false;
        amount = Math.max(0, amount);
        if (amount > 0 && caster instanceof Player && MagicDamageEvent.listened()) {
            var mark = plugin.reactions() == null ? null : plugin.reactions().markOf(target);
            var e = new MagicDamageEvent(caster, target, element == null ? "" : element, reaction == null ? "" : reaction,
                    mark == null ? "" : mark.element().id, mark == null ? null : mark.caster(), amount);
            Bukkit.getPluginManager().callEvent(e);
            amount = e.getDamage();
        }
        return plugin.protection().damage(caster,target,amount);
    }

    /** Spell heal on a player (zone heal, rainbow). Fires {@link MagicHealEvent} for artifact bonuses. */
    void heal(LivingEntity caster, Player ally, double amount, String reaction) {
        if (amount <= 0) return;
        if (MagicHealEvent.listened()) {
            var e = new MagicHealEvent(caster, ally, reaction, amount);
            Bukkit.getPluginManager().callEvent(e);
            amount = e.getAmount();
        }
        ally.setHealth(Math.min(maxHealth(ally), ally.getHealth() + amount));
    }

    void effects(LivingEntity target, List<PotionEffect> effects) {
        for (PotionEffect e : effects) target.addPotionEffect(e);
    }

    /** Push away from `from` horizontally by `knock`, plus `up`. Bosses get a reduced push. */
    void knock(LivingEntity target, Location from, Vector fallbackDir, double knock, double up) {
        if (knock == 0 && up == 0) return;
        Vector dir = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1e-4) dir = fallbackDir.clone().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 0); else dir.normalize();
        double scale = boss(target) ? bossKnock : 1;
        Vector v = dir.multiply(knock * scale).setY(up * scale + (knock != 0 ? 0.1 * scale : 0));
        target.setVelocity(v);
    }

    static double maxHealth(LivingEntity e) {
        var a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    // ---------- block protection, same pattern as MagicCodexBridge SpellUtility ----------
    static boolean canBreak(Player p, Block b) {
        if(!MagicMythicAccess.at(p,b.getLocation()))return false;
        BlockData before = b.getBlockData().clone();
        var event = new BlockBreakEvent(b, p);
        event.setDropItems(false);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && b.getBlockData().equals(before);
    }

    static boolean canPlace(Player p, Block b, Block against, Material item) {
        if(!MagicMythicAccess.at(p,b.getLocation()))return false;
        BlockState replaced = b.getState();
        var event = new BlockPlaceEvent(b, replaced, against, new ItemStack(item), p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild();
    }
}
