package school.chacademia.magicmythic;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * chaally{type=...} @self - support on the caster and nearby players.
 *  quench           - put out the caster's fire
 *  cleanse r=5      - remove poison, weakness, slowness from caster and allies
 *  share r=6 max=3  - pool current health of caster + allies, split by max health (no health is created)
 *  potion r=5 ticks=200 - spend the potion in hand; its effects go to caster and allies for `ticks`
 */
final class AllyMech extends Mech {
    private static final List<PotionEffectType> CLEANSE = List.of(PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS);

    AllyMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        LivingEntity caster = c.caster();
        String type = p.s("type", "cleanse").toLowerCase(Locale.ROOT);
        var targets = plugin.targets();
        switch (type) {
            case "quench" -> {
                if (caster.getFireTicks() <= 0) return false;
                caster.setFireTicks(0);
                return true;
            }
            case "cleanse" -> {
                boolean any = false;
                for (LivingEntity e : group(caster, p.d("r", 5), 32))
                    for (var t : CLEANSE) if (e.hasPotionEffect(t)) { e.removePotionEffect(t); any = true; }
                return any;
            }
            case "share" -> {
                var group = group(caster, p.d("r", 6), Math.max(1, p.i("max", 3)) + 1);
                if (group.size() < 2) return false;
                double[] cur = new double[group.size()], max = new double[group.size()];
                for (int i = 0; i < group.size(); i++) { cur[i] = group.get(i).getHealth(); max[i] = Targets.maxHealth(group.get(i)); }
                double[] next = Geometry.share(cur, max);
                for (int i = 0; i < group.size(); i++) group.get(i).setHealth(Math.clamp(next[i], 0.5, max[i]));
                return true;
            }
            case "potion" -> {
                if (!(caster instanceof Player player)) return false;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.POTION && hand.getType() != Material.SPLASH_POTION && hand.getType() != Material.LINGERING_POTION) return false;
                if (!(hand.getItemMeta() instanceof PotionMeta meta)) return false;
                var effects = new ArrayList<PotionEffect>();
                if (meta.getBasePotionType() != null) effects.addAll(meta.getBasePotionType().getPotionEffects());
                effects.addAll(meta.getCustomEffects());
                if (effects.isEmpty()) return false;
                int ticks = Math.clamp(p.i("ticks", 200), 1, 20 * 120);
                hand.setAmount(hand.getAmount() - 1);
                if (hand.getType() == Material.POTION) {
                    var leftover = player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
                    leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
                for (LivingEntity e : group(caster, p.d("r", 5), 32))
                    for (PotionEffect fx : effects) {
                        // Only known beneficial types are support; unknown/neutral effects fail closed for PvP.
                        boolean beneficial=SupportPolicy.beneficial(fx.getType().getKey().getKey());
                        if(!plugin.protection().pair(caster,e.getLocation()))continue;
                        if(!SupportPolicy.permits(beneficial,e.equals(caster),!beneficial&&!e.equals(caster)&&targets.hurt(caster,e,0)))continue;
                        int d = fx.getType().isInstant() ? 1 : ticks;
                        e.addPotionEffect(new PotionEffect(fx.getType(), d, fx.getAmplifier()));
                    }
                return true;
            }
            default -> { return false; }
        }
    }

    /** Caster first, then nearest allies. */
    private List<LivingEntity> group(LivingEntity caster, double r, int max) {
        var out = new ArrayList<LivingEntity>();
        if(plugin.protection().pair(caster,caster.getLocation()))out.add(caster);
        for (Player p : plugin.targets().alliesNear(caster, caster.getLocation(), r, max))
            if (!p.equals(caster) && out.size() < max) out.add(p);
        return out;
    }
}
