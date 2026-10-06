package school.magiccodex.discovery;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Trusted SERVER integration only. Never call from a client packet without verifying the actual action. */
public final class DiscoveryService {
    private final MagicDiscovery plugin;
    DiscoveryService(MagicDiscovery plugin){this.plugin=plugin;}
    /** Adds a verified event count, e.g. book.flight=1 or cook.hearth_touch=4. */
    public boolean signal(UUID player,String event,double amount){return plugin.signal(player,event,amount);}
    /** Latest authoritative state (state.circle, state.temperature, state.flight_prohibited). */
    public boolean state(UUID player,String key,double value){return plugin.state(player,key,value);}
    /** Explicit verified state event, including retrying a state-only discovery at the same value. */
    public boolean stateEvent(UUID player,String key,double value){return plugin.state(player,key,value,true);}
    /** A successful cast, not an attempted command. Existing Bridge forwards successful casts automatically. */
    public void cast(UUID player,String spell,double spentMana){plugin.cast(player,spell,spentMana);}
    /** Immutable ID -> display name map: all registered Bridge spells, or standalone discovery definitions. Must be called on the server thread. */
    public Map<String,String> registeredSpells(){return plugin.registeredSpells();}
    /**
     * Persist current knowledge for an online or offline player without minting first-discovery rewards.
     * Unlearning retains historical acquisitions and starts fresh, spell-specific retry progress.
     * Call on the server thread. The callback also runs there: null means success, otherwise a user-facing error.
     * Only trusted, permission-checked administrator integrations may invoke this method.
     */
    public void adminSetLearned(UUID player,String spellId,boolean learned,Consumer<String> completion){
        plugin.adminSetLearned(player,spellId,learned,completion);
    }
}
