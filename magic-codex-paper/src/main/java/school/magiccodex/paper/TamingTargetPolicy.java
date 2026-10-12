package school.magiccodex.paper;

import org.bukkit.entity.*;

/** Exclusions apply before Mythic or vanilla profile lookup, including disguised NPCs. */
final class TamingTargetPolicy {
    private TamingTargetPolicy() {}
    static boolean excluded(LivingEntity entity,boolean activePet) {
        return entity instanceof Player||entity instanceof ArmorStand||entity.hasMetadata("NPC")
                ||entity instanceof Tameable tameable&&tameable.isTamed()
                ||entity instanceof AbstractHorse horse&&horse.isTamed()
                ||entity.getScoreboardTags().contains("chacademia_pet")||activePet;
    }
}
