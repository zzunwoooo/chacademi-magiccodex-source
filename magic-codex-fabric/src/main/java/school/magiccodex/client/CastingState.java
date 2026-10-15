package school.magiccodex.client;

import java.util.*;
import java.util.function.Consumer;
import school.magiccodex.client.CodexData.Spell;

/** Local presentation cooldowns, never a replacement for server command/skill validation. */
public final class CastingState {
    public enum Kind { PASS, CONSUME, MODE, CAST, LOCAL_EFFECT, UNKNOWN, LOCKED, MISSING, NO_COMMAND, COOLDOWN, PENDING, AIM }
    public record Result(Kind kind, String spellId) { public boolean consumed() { return kind != Kind.PASS; } }
    private record Cooldown(long until, long duration) {}
    private List<SpellKeySettings.Binding> bindings = new SpellKeySettings().slots();
    private List<Spell> catalog = List.of();
    private final Map<String,Spell> spells = new HashMap<>();
    private final Map<String,Cooldown> cooldowns = new HashMap<>();
    private final Set<Integer> captured = new HashSet<>();
    private final Map<String,Long> pending=new HashMap<>();
    private boolean enabled;

    public boolean enabled() { return enabled; }
    public void setBindings(SpellKeySettings value) { bindings = value.orderedSlots(); }
    public List<SpellKeySettings.Binding> bindings() { return bindings; }
    /** A saved slot with a usable key, independent of magic-mode and catalog discovery. */
    public boolean registered(String id) {
        return bindings.stream().anyMatch(b -> id.equals(b.spellId()) && KeySettingsLayout.allowed(b.keyCode()));
    }
    public Spell spell(String id) { return spells.get(id); }
    public void catalog(List<Spell> value) {
        if (catalog == value) return;
        catalog = value; spells.clear(); value.forEach(s -> spells.put(s.id(), s));
    }
    public List<String> permissions() {
        return bindings.stream().filter(b -> !b.empty()).map(b -> spell(b.spellId()))
                .filter(Objects::nonNull).map(Spell::permission).distinct().toList();
    }
    public boolean bound(int key) { return bindings.stream().anyMatch(b -> !b.empty() && KeySettingsLayout.allowed(key) && b.keyCode() == key); }
    public long remaining(String id, long now) { var c=cooldowns.get(id); return c==null?0:Math.max(0,c.until()-now); }
    public float fraction(String id,long now) { var c=cooldowns.get(id); return c==null?0:Math.clamp((float)remaining(id,now)/c.duration(),0,1); }
    public void clearCooldown(String id) { cooldowns.remove(id); pending.remove(id); }
    public void releaseInputs() { captured.clear(); }
    public void disable() { enabled=false; }
    public void reset() { enabled=false; captured.clear(); cooldowns.clear(); pending.clear(); }
    public void serverResult(String id,int remaining,long now) {
        pending.remove(id);
        if(id.equals("wind_message")&&remaining==0)cooldowns.remove(id);
        if(remaining>0)cooldowns.put(id,new Cooldown(now+remaining,remaining));
    }

    public Result input(int key,int action,boolean toggleKey,boolean gameplay,long now,Consumer<String> send) {
        return input(key,action,toggleKey,gameplay,now,false,s->send.accept(s.command()));
    }
    public Result input(int key,int action,boolean toggleKey,boolean gameplay,long now,boolean managed,Consumer<Spell> send) {
        return input(key,action,toggleKey,gameplay,now,managed,s->false,send);
    }
    /** {@code aim} may take over a ready spell (LoL-style targeting); the later confirmation goes through {@link #fire}. */
    public Result input(int key,int action,boolean toggleKey,boolean gameplay,long now,boolean managed,java.util.function.Predicate<Spell> aim,Consumer<Spell> send) {
        if(action==0) { captured.remove(key); return result(Kind.PASS,""); }
        if(captured.contains(key)) return result(Kind.CONSUME,"");
        if(action!=1 || !gameplay || key<0) return result(Kind.PASS,"");
        if(toggleKey) { captured.add(key); enabled=!enabled; return result(Kind.MODE,""); }
        if(!enabled) return result(Kind.PASS,"");
        var binding=bindings.stream().filter(b -> !b.empty() && KeySettingsLayout.allowed(key) && b.keyCode()==key).findFirst().orElse(null);
        if(binding==null) return result(Kind.PASS,"");
        captured.add(key);
        String id=binding.spellId();
        Result blocked=check(id,now,managed);
        if(blocked!=null) return blocked;
        if(aim.test(spell(id))) return result(Kind.AIM,id);
        return send(id,now,managed,send);
    }
    /** Confirms a spell chosen earlier (aim preview). Re-checks everything; magic mode must still be on. */
    public Result fire(String id,long now,boolean managed,Consumer<Spell> send) {
        if(!enabled) return result(Kind.PASS,id);
        Result blocked=check(id,now,managed);
        return blocked!=null?blocked:send(id,now,managed,send);
    }
    private Result check(String id,long now,boolean managed) {
        Spell s=spell(id);
        if(s==null) return result(Kind.MISSING,id);
        if(!s.permissionKnown()) return result(Kind.UNKNOWN,id);
        if(!s.discovered()) return result(Kind.LOCKED,id);
        if(s.command().isBlank())return result(Kind.NO_COMMAND,id);
        if(managed&&pending.getOrDefault(id,0L)>now)return result(Kind.PENDING,id);
        if(remaining(id,now)>0) return result(Kind.COOLDOWN,id);
        return null;
    }
    private Result send(String id,long now,boolean managed,Consumer<Spell> send) {
        Spell s=spell(id);
        if(managed) { send.accept(s);pending.put(id,now+3000);return result(Kind.PENDING,id); }
        send.accept(s);
        cooldowns.entrySet().removeIf(e -> e.getValue().until()<=now);
        long duration=id.equals("wind_message")?0:school.magiccodex.protocol.MagicHaste.cooldown((int)Math.round(s.cooldown()*1000),ManaClient.haste());
        if(duration>0) cooldowns.put(id,new Cooldown(now+duration,duration));
        return result(Kind.CAST,id);
    }
    private static Result result(Kind kind,String id) { return new Result(kind,id); }
}
