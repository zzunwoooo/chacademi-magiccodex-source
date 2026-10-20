package school.chacademia.gimmick.puzzle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PuzzleEngineTest {
    static final class Log implements PuzzleEngine.Listener {
        final List<String> events = new ArrayList<>();
        public void changed(String n, boolean on, String cause, String who) { events.add(n + (on ? "+" : "-") + ":" + cause); }
        public void collected(String n, String item, String anim, String who) { events.add(n + "<" + item + ">" + anim); }
        public void reset(String n) { events.add(n + "!reset"); }
    }

    @SuppressWarnings("unchecked")
    static PuzzleEngine engine(String yaml, Log log) {
        Map<String, Object> raw = (Map<String, Object>) new org.yaml.snakeyaml.Yaml().load(yaml);
        var warnings = new ArrayList<String>();
        var def = PuzzleDef.parse("p", raw, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        return new PuzzleEngine(def, log);
    }

    @Test void parsesExpressions() {
        assertEquals(new Expr.All(List.of(new Expr.Ref("a"), new Expr.Not(new Expr.Ref("b")))), Expr.parse("all(a, not(b))"));
        assertEquals(new Expr.Sync(1.5, List.of("x", "y")), Expr.parse("sync(1.5,x,y)"));
        assertEquals(new Expr.Count(2, List.of(new Expr.Ref("a"), new Expr.Ref("b"), new Expr.Ref("c"))), Expr.parse("count(2,a,b,c)"));
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("all(a,"));
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("nope(a)"));
    }

    @Test void twoBraziersOpenGate() {
        var log = new Log();
        var e = engine("""
            nodes:
              a: {turn-on: [magic:fire], turn-off: [magic:water]}
              b: {turn-on: [magic:fire]}
              gate: {when: "all(a,b)", latch: true}
            """, log);
        assertFalse(e.trigger("a", "magic:wind", "p", 0));
        assertTrue(e.trigger("a", "magic:fire", "p", 0));
        assertFalse(e.on("gate"));
        assertTrue(e.trigger("b", "magic:fire", "p", 20));
        assertTrue(e.on("gate"));
        e.trigger("a", "magic:water", "p", 40);
        assertFalse(e.on("a"));
        assertTrue(e.on("gate"), "latched gate stays open");
    }

    @Test void holdTurnsOffAndCooldownBlocksSpam() {
        var e = engine("nodes:\n  t: {turn-on: [magic:any], hold: 2, cooldown: 1}\n", new Log());
        assertTrue(e.trigger("t", "magic:earth", "p", 0));
        assertFalse(e.trigger("t", "magic:earth", "p", 5), "cooldown");
        e.tick(39); assertTrue(e.on("t"));
        e.tick(40); assertFalse(e.on("t"));
    }

    @Test void wrongBellOrderResets() {
        var log = new Log();
        var e = engine("""
            nodes:
              b1: {turn-on: [magic:wind], hold: 30}
              b2: {turn-on: [magic:wind], hold: 30}
              b3: {turn-on: [magic:wind], hold: 30}
              door: {when: "seq(b1,b2,b3)", latch: true}
            """, log);
        e.trigger("b1", "magic:wind", "p", 0);
        e.trigger("b3", "magic:wind", "p", 10);   // wrong
        assertFalse(e.on("b1")); assertFalse(e.on("b3"));
        e.trigger("b1", "magic:wind", "p", 20);
        e.trigger("b2", "magic:wind", "p", 30);
        e.trigger("b3", "magic:wind", "p", 40);
        assertTrue(e.on("door"));
    }

    @Test void syncNeedsBothWithinWindow() {
        var e = engine("""
            nodes:
              l: {turn-on: [magic:any], hold: 1}
              r: {turn-on: [magic:any], hold: 1}
              ok: {when: "sync(1, l, r)", latch: true}
            """, new Log());
        e.trigger("l", "magic:fire", "p", 0);
        e.tick(25);                      // l expired
        e.trigger("r", "magic:fire", "q", 25);
        assertFalse(e.on("ok"));
        e.trigger("l", "magic:fire", "p", 30);
        assertTrue(e.on("ok"));
    }

    @Test void standWhileAndCollect() {
        var log = new Log();
        var e = engine("""
            nodes:
              plate: {while: [stand]}
              altar: {collect: {"chacademi:fragment_fire": insert_fire, "chacademi:fragment_water": insert_water}}
              door: {when: "all(plate, altar)"}
            """, log);
        assertTrue(e.collect("altar", "chacademi:fragment_fire", "p", 0));
        assertFalse(e.collect("altar", "chacademi:fragment_fire", "p", 1), "duplicate");
        assertFalse(e.collect("altar", "minecraft:stone", "p", 1));
        e.collect("altar", "chacademi:fragment_water", "p", 2);
        assertTrue(e.on("altar"));
        e.standing("plate", true, "p", 3);
        assertTrue(e.on("door"));
        e.standing("plate", false, "p", 4);
        assertFalse(e.on("door"));
        assertTrue(log.events.contains("altar<chacademi:fragment_fire>insert_fire"));
    }

    @Test void saveLoadRoundTrip() {
        var e = engine("nodes:\n  a: {toggle: [interact]}\n  t: {turn-on: [interact], hold: 3}\n", new Log());
        e.trigger("a", "interact", "p", 0);
        e.trigger("t", "interact", "p", 0);
        var saved = e.save();
        var f = engine("nodes:\n  a: {toggle: [interact]}\n  t: {turn-on: [interact], hold: 3}\n", new Log());
        f.load(saved);
        assertTrue(f.on("a"));
        assertFalse(f.on("t"), "timed nodes come back off");
    }

    @Test void deviceConditionsAndManaInputs() {
        var log = new Log();
        var e = engine("""
            nodes:
              pillar: {level: 2}
              statue: {facing: west}
              plate: {turn-on: [weight], hold: 3, mana: out}
              door: {turn-on: [mana], hold: 8}
              sluice: {while: [mana]}
              receiver: {while: [light], color: red, mana: out}
            """, log);
        var d = e.def().nodes();
        assertTrue(d.get("plate").device().manaOut());
        assertEquals(1, d.get("statue").device().facing());
        assertEquals("red", d.get("receiver").device().color());
        assertTrue(d.get("pillar").device().condition());
        assertTrue(e.held("pillar", "state", true, null, 1));
        assertFalse(e.held("plate", "state", true, null, 1), "no condition: state is ignored");
        e.trigger("plate", "weight", null, 2);
        e.held("sluice", "mana", true, null, 3);
        e.trigger("door", "mana", null, 3);
        assertTrue(e.on("sluice") && e.on("door") && e.on("plate"));
        e.held("sluice", "mana", false, null, 4);
        assertFalse(e.on("sluice"));
        for (long t = 5; t < 200; t++) e.tick(t);
        assertFalse(e.on("plate"));
        assertFalse(e.on("door"));
        e.held("receiver", "light", true, null, 201);
        assertTrue(e.on("receiver"));
    }
}
