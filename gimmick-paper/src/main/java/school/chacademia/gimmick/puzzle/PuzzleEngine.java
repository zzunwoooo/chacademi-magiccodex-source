package school.chacademia.gimmick.puzzle;

import java.util.*;

/**
 * State machine for one puzzle (no Bukkit; time is passed in as server ticks).
 * Direct triggers change input nodes; derived nodes (`when`) are re-evaluated after every change.
 */
public final class PuzzleEngine {
    public interface Listener {
        void changed(String node, boolean on, String cause, String who);
        void collected(String node, String item, String anim, String who);
        void reset(String node);
    }

    static final class State {
        boolean on; long since = Long.MIN_VALUE / 4; long offAt = -1; long lastTrigger = Long.MIN_VALUE / 4;
        final LinkedHashSet<String> collected = new LinkedHashSet<>();
    }

    private final PuzzleDef def;
    private final Listener listener;
    private final Map<String, State> states = new HashMap<>();
    private long now;

    public PuzzleEngine(PuzzleDef def, Listener listener) {
        this.def = def; this.listener = listener;
        for (String n : def.nodes().keySet()) states.put(n, new State());
    }

    public PuzzleDef def() { return def; }
    public boolean on(String node) { State s = states.get(node); return s != null && s.on; }
    public Set<String> collected(String node) { State s = states.get(node); return s == null ? Set.of() : Collections.unmodifiableSet(s.collected); }

    private final Expr.Ctx ctx = new Expr.Ctx() {
        public boolean on(String n) { return PuzzleEngine.this.on(n); }
        public long since(String n) { State s = states.get(n); return s == null ? 0 : s.since; }
    };

    /** A trigger reached a node. @return true when it changed something. */
    public boolean trigger(String node, String trigger, String who, long now) {
        this.now = now;
        var d = def.nodes().get(node); State s = states.get(node);
        if (d == null || s == null) return false;
        trigger = PuzzleDef.key(trigger);
        boolean relevant = d.listens(trigger, d.toggle()) || d.listens(trigger, d.on()) || d.listens(trigger, d.off());
        if (!relevant) return false;
        if (now - s.lastTrigger < Math.round(d.cooldown() * 20)) return false;
        s.lastTrigger = now;
        boolean changed;
        if (d.listens(trigger, d.toggle())) changed = set(node, !s.on, trigger, who);
        else if (d.listens(trigger, d.on())) {
            changed = set(node, true, trigger, who);
            if (!changed && s.on && d.hold() > 0 && !d.latch()) { s.offAt = now + Math.round(d.hold() * 20); }
        }
        else changed = set(node, false, trigger, who);
        if (changed && s.on) checkSequences(node, who);
        propagate(who);
        return changed;
    }

    /** "while: stand" input: on exactly while someone stands there. */
    public boolean standing(String node, boolean standing, String who, long now) {
        this.now = now;
        var d = def.nodes().get(node);
        if (d == null || !d.whileTriggers().contains("stand")) return false;
        boolean changed = set(node, standing, "stand", who);
        if (changed && standing) checkSequences(node, who);
        propagate(who);
        return changed;
    }

    /** Insert an item. @return true when the item was taken. */
    public boolean collect(String node, String item, String who, long now) {
        this.now = now;
        var d = def.nodes().get(node); State s = states.get(node);
        if (d == null || s == null) return false;
        item = PuzzleDef.key(item);
        if (!d.collect().containsKey(item) || s.collected.contains(item) || s.on) return false;
        s.collected.add(item);
        listener.collected(node, item, d.collect().get(item), who);
        if (s.collected.containsAll(d.collect().keySet())) set(node, true, "collect", who);
        propagate(who);
        return true;
    }

    /** Admin/command signal. */
    public boolean force(String node, boolean on, String who, long now) {
        this.now = now;
        if (!def.nodes().containsKey(node)) return false;
        boolean changed = set(node, on, "signal", who);
        propagate(who);
        return changed;
    }

    public void tick(long now) {
        this.now = now;
        boolean any = false;
        for (var e : states.entrySet()) {
            State s = e.getValue();
            if (s.on && s.offAt >= 0 && now >= s.offAt) { set(e.getKey(), false, "timeout", null); any = true; }
        }
        if (any) propagate(null);
    }

    public void reset() {
        for (var e : states.entrySet()) {
            State s = e.getValue();
            boolean wasOn = s.on, hadItems = !s.collected.isEmpty();
            s.on = false; s.offAt = -1; s.collected.clear(); s.since = Long.MIN_VALUE / 4;
            if (wasOn) listener.changed(e.getKey(), false, "reset", null);
            else if (hadItems) listener.reset(e.getKey());
        }
    }

    private boolean set(String node, boolean on, String cause, String who) {
        State s = states.get(node); var d = def.nodes().get(node);
        if (s == null || s.on == on) return false;
        if (!on && d.latch() && !cause.equals("reset")) return false;
        s.on = on; s.since = now;
        s.offAt = on && d.hold() > 0 && !d.latch() ? now + Math.round(d.hold() * 20) : -1;
        if (!on) s.collected.clear();
        listener.changed(node, on, cause, who);
        return true;
    }

    /** A wrong step in a seq() resets all of that sequence's inputs. */
    private void checkSequences(String turnedOn, String who) {
        for (var d : def.nodes().values()) {
            if (d.when() == null || !d.resetOnFail() || on(d.name())) continue;
            for (Expr.Seq seq : Expr.seqs(d.when())) {
                if (!seq.order().contains(turnedOn) || seq.prefixOk(ctx)) continue;
                for (String n : seq.order()) set(n, false, "fail", who);
            }
        }
    }

    private void propagate(String who) {
        for (int pass = 0; pass < 32; pass++) {
            boolean changed = false;
            for (var d : def.nodes().values()) {
                if (d.when() == null) continue;
                boolean want = d.when().eval(ctx);
                State s = states.get(d.name());
                if (s.on == want || (s.on && d.latch())) continue;
                changed |= set(d.name(), want, "logic", who);
            }
            if (!changed) return;
        }
    }

    // ------------------------------------------------------------------ save / load
    public Map<String, Object> save() {
        var out = new LinkedHashMap<String, Object>();
        for (var e : states.entrySet()) {
            if (!e.getValue().on && e.getValue().collected.isEmpty()) continue;
            var m = new LinkedHashMap<String, Object>();
            m.put("on", e.getValue().on);
            if (!e.getValue().collected.isEmpty()) m.put("collected", new ArrayList<>(e.getValue().collected));
            out.put(e.getKey(), m);
        }
        return out;
    }

    /** Restore without callbacks (models read state when they spawn). Timed (hold) nodes come back off. */
    public void load(Map<String, Object> saved) {
        for (var e : saved.entrySet()) {
            State s = states.get(e.getKey()); var d = def.nodes().get(e.getKey());
            if (s == null || !(e.getValue() instanceof Map<?, ?> m)) continue;
            s.on = Boolean.TRUE.equals(m.get("on")) && (d.hold() <= 0 || d.latch());
            s.since = 0;
            if (m.get("collected") instanceof List<?> l) for (Object o : l) s.collected.add(PuzzleDef.key(o));
        }
    }
}
