package school.magiccodex.paper;
import java.util.*;
/** A received private message authorizes a mana-paid reply for the current connection session. */
final class WhisperPeers {
 private final Map<UUID,Set<UUID>> peers=new HashMap<>();
 boolean contains(UUID a,UUID b){return peers.getOrDefault(a,Set.of()).contains(b);}
 void delivered(UUID a,UUID b){peers.computeIfAbsent(a,k->new HashSet<>()).add(b);peers.computeIfAbsent(b,k->new HashSet<>()).add(a);}
 void remove(UUID id){peers.remove(id);peers.values().forEach(s->s.remove(id));}
 void clear(){peers.clear();}
}