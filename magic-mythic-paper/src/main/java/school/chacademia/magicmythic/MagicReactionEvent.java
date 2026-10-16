package school.chacademia.magicmythic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an element reaction happens, before its effect (only when someone listens). The icon pop is already
 * sent. Listeners (artifacts) may cancel the effect (the name still shows) or add small bonuses:
 * extra radius (sand vortex pull), extra chain-lightning bounces, extra crowd-control ticks, extra rainbow heal.
 * Reaction damage itself goes through {@link MagicDamageEvent} with a reaction id.
 */
public final class MagicReactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity caster, target;
    private final String reaction, markElement, triggerElement;
    private boolean effectCancelled;
    private double radiusBonus, healBonus;
    private int bounceBonus, controlBonusTicks;

    public MagicReactionEvent(LivingEntity caster, LivingEntity target, String reaction, String markElement, String triggerElement) {
        this.caster = caster; this.target = target; this.reaction = reaction; this.markElement = markElement; this.triggerElement = triggerElement;
    }
    public LivingEntity getCaster() { return caster; }
    public LivingEntity getTarget() { return target; }
    /** Lower-case reaction id (steam, wildfire, magma, flare, ember_curse, shock, mud, rainbow, abyss, sandstorm, flash, void_gust, crystal, bind, eclipse). */
    public String getReaction() { return reaction; }
    public String getMarkElement() { return markElement; }
    public String getTriggerElement() { return triggerElement; }
    /** Crowd-control reactions (mud, abyss, crystal, bind). */
    public boolean isControl() { return switch (reaction) { case "mud", "abyss", "crystal", "bind" -> true; default -> false; }; }
    public boolean isEffectCancelled() { return effectCancelled; }
    public void setEffectCancelled(boolean cancelled) { effectCancelled = cancelled; }
    public double getRadiusBonus() { return radiusBonus; }
    public void setRadiusBonus(double v) { radiusBonus = Math.clamp(v, 0, 4); }
    public int getBounceBonus() { return bounceBonus; }
    public void setBounceBonus(int v) { bounceBonus = Math.clamp(v, 0, 4); }
    public int getControlBonusTicks() { return controlBonusTicks; }
    public void setControlBonusTicks(int v) { controlBonusTicks = Math.clamp(v, 0, 40); }
    public double getHealBonus() { return healBonus; }
    public void setHealBonus(double v) { healBonus = Math.clamp(v, 0, 10); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
    static boolean listened() { return HANDLERS.getRegisteredListeners().length > 0; }
}
