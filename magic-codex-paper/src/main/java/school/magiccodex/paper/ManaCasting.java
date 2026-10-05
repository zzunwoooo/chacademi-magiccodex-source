package school.magiccodex.paper;

import java.util.function.BooleanSupplier;
import school.magiccodex.protocol.ManaProtocol;

/** Single server-thread transaction: reject first, charge once, refund rejected dispatches. */
final class ManaCasting {
    record Result(int status,int cooldownMillis){}
    static Result attempt(ManaAccount a,ManaSpells.Spell spell,boolean permitted,long now,BooleanSupplier dispatch){
        if(spell==null)return new Result(ManaProtocol.UNKNOWN,0);
        if(!permitted)return new Result(ManaProtocol.LOCKED,0);
        long remaining=a.remaining(spell.id(),now);
        if(remaining>0)return new Result(ManaProtocol.COOLDOWN,(int)Math.min(86_400_000,remaining));
        if(!a.consume(spell.cost()))return new Result(ManaProtocol.EMPTY,0);
        boolean success=false;
        try{success=dispatch.getAsBoolean();}
        finally{if(!success)a.add(spell.cost());}
        if(!success)return new Result(ManaProtocol.FAILED,0);
        int cooldown=school.magiccodex.protocol.MagicHaste.cooldown(spell.cooldown(),a.snapshot().haste());
        a.cooldown(spell.id(),now+cooldown,now);
        return new Result(ManaProtocol.OK,cooldown);
    }
}
