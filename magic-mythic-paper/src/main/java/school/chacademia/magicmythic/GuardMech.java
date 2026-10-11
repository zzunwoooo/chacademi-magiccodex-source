package school.chacademia.magicmythic;

import java.util.Locale;

/** chaguard{type=lethal|reflect|block|deflect;ticks=..;r=4;reduce=0.5;heal=6;push=1.2} @self */
final class GuardMech extends Mech {
    GuardMech(MagicMythicPlugin plugin, Params p) { super(plugin, p); }

    @Override boolean run(Ctx c) {
        Guards.Type type;
        try { type = Guards.Type.valueOf(p.s("type", "lethal").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return false; }
        var guards = plugin.guards();
        guards.add(c.caster(), new Guards.Guard(type, guards.now() + Math.clamp(p.i("ticks", 100), 1, 20 * 3600),
            p.d("r", 4), p.d("reduce", 0.5), p.d("heal", 6), p.d("push", 1.2)));
        return true;
    }
}
