package school.chacademia.magicmythic;

import java.util.*;
import school.magiccodex.protocol.ElementProtocol.Element;
import school.magiccodex.protocol.ElementProtocol.Reaction;

/**
 * Pure bookkeeping for element marks (no Bukkit, unit-tested). Time is the server tick passed in by the caller.
 * Nothing is scheduled: expiry is checked lazily on the next hit, and {@link #sweep} drops stale rows.
 *
 * Rules: a hit leaves its element for markTicks. A hit of another element while the mark is alive reacts once and
 * consumes the mark; the same target then cannot react again for cooldownTicks (a hit during the cooldown just
 * replaces the mark). The same element only refreshes the timer.
 */
final class ReactionBook {
    sealed interface Outcome permits Marked, Reacted {}
    /** Icon should show `element` for `ticks`. */
    record Marked(Element element, int ticks) implements Outcome {}
    /** Reaction fired; the mark is gone. `markCaster` is who left the first element (may equal the trigger). */
    record Reacted(Reaction reaction, UUID markCaster) implements Outcome {}

    private record Mark(Element element, long until, UUID caster) {}

    private final Map<UUID, Mark> marks = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private int markTicks, cooldownTicks;

    ReactionBook(int markTicks, int cooldownTicks) { configure(markTicks, cooldownTicks); }

    void configure(int markTicks, int cooldownTicks) {
        this.markTicks = Math.clamp(markTicks, 1, 20 * 60);
        this.cooldownTicks = Math.clamp(cooldownTicks, 0, 20 * 60);
    }

    int markTicks() { return markTicks; }

    Outcome hit(UUID target, UUID caster, Element element, long now) {
        Objects.requireNonNull(target); Objects.requireNonNull(element);
        Mark old = marks.get(target);
        if (old != null && old.until() > now && old.element() != element && !coolingDown(target, now)) {
            marks.remove(target);
            if (cooldownTicks > 0) cooldown.put(target, now + cooldownTicks);
            return new Reacted(Reaction.of(old.element(), element), old.caster());
        }
        marks.put(target, new Mark(element, now + markTicks, caster));
        return new Marked(element, markTicks);
    }

    /**
     * Spread reactions (불씨 확산): put a mark on a neighbour without reacting. Only on targets with no live mark,
     * so a spread never overwrites or consumes someone else's element. @return true when placed
     */
    boolean spread(UUID target, UUID caster, Element element, long now) {
        Mark old = marks.get(target);
        if (old != null && old.until() > now) return false;
        marks.put(target, new Mark(element, now + markTicks, caster));
        return true;
    }

    boolean coolingDown(UUID target, long now) {
        Long until = cooldown.get(target);
        return until != null && until > now;
    }

    /** Current element, or null. */
    Element element(UUID target, long now) {
        Mark m = marks.get(target);
        return m == null || m.until() <= now ? null : m.element();
    }

    void forget(UUID target) { marks.remove(target); cooldown.remove(target); }

    /** Drops expired rows. @return removed mark count */
    int sweep(long now) {
        int before = marks.size();
        marks.values().removeIf(m -> m.until() <= now);
        cooldown.values().removeIf(t -> t <= now);
        return before - marks.size();
    }

    int size() { return marks.size(); }
    void clear() { marks.clear(); cooldown.clear(); }
}
