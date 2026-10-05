package school.magiccodex.discovery;
import java.util.UUID;
/** Trusted SERVER integration only. Never call from a client packet without verifying the actual action. */
public final class DiscoveryService {
    private final MagicDiscovery plugin;
    DiscoveryService(MagicDiscovery plugin){this.plugin=plugin;}
    /** Adds a verified event count, e.g. book.flight=1 or cook.hearth_touch=4. */
    public boolean signal(UUID player,String event,double amount){return plugin.signal(player,event,amount);}
    /** Latest authoritative state (state.circle, state.temperature, state.flight_prohibited). */
    public boolean state(UUID player,String key,double value){return plugin.state(player,key,value);}
    /** A successful cast, not an attempted command. Existing Bridge forwards successful casts automatically. */
    public void cast(UUID player,String spell,double spentMana){plugin.cast(player,spell,spentMana);}
}
