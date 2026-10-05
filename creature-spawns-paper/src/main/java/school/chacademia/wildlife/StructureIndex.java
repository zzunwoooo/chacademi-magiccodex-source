package school.chacademia.wildlife;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.util.BoundingBox;

/** Only indexes already-loaded structure starts. Never locate/generate chunks in a spawn condition. */
public final class StructureIndex implements Listener {
    record Key(UUID world, int x, int z) {}
    public record Site(String id, BoundingBox box) {}
    private final Map<Key,List<Site>> chunks = new HashMap<>();
    public void index(Chunk chunk) {
        var sites = new ArrayList<Site>();
        for (var s : chunk.getStructures()) {
            String id=s.getStructure().getKey().toString();
            if (id.startsWith("chacademia:") && !s.getPieces().isEmpty()) {
                // GeneratedStructure's box can include terrain-adaptation padding.
                // Measure proximity from the actual placed pieces instead.
                BoundingBox box=s.getPieces().iterator().next().getBoundingBox().clone();
                for(var piece:s.getPieces()) box.union(piece.getBoundingBox());
                sites.add(new Site(id,box));
            }
        }
        Key key=new Key(chunk.getWorld().getUID(),chunk.getX(),chunk.getZ());
        if (sites.isEmpty()) chunks.remove(key);
        else chunks.put(key,List.copyOf(sites));
    }
    @EventHandler public void load(ChunkLoadEvent e) { index(e.getChunk()); }
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent e) { chunks.remove(new Key(e.getWorld().getUID(),e.getChunk().getX(),e.getChunk().getZ())); }
    @EventHandler public void unloadWorld(WorldUnloadEvent e) { chunks.keySet().removeIf(k->k.world.equals(e.getWorld().getUID())); }
    public List<Site> nearby(Location loc, Set<String> allowed, double radius) {
        Set<Site> result=new LinkedHashSet<>();
        // Templates can extend from their start chunk; 128 covers the pack's maximum extent.
        int reach=(int)Math.ceil((radius+128)/16),cx=loc.getBlockX()>>4,cz=loc.getBlockZ()>>4;
        for(int x=cx-reach;x<=cx+reach;x++) for(int z=cz-reach;z<=cz+reach;z++) {
            for(var site:chunks.getOrDefault(new Key(loc.getWorld().getUID(),x,z),List.of())) {
                if ((allowed.isEmpty()||allowed.contains(site.id)) && distanceSquared(loc,site.box)<=radius*radius
                    && loc.getY()>=site.box.getMinY()-12 && loc.getY()<=site.box.getMaxY()+24) result.add(site);
            }
        }
        return List.copyOf(result);
    }
    static double distanceSquared(Location l,BoundingBox b) {
        double dx=Math.max(Math.max(b.getMinX()-l.getX(),0),l.getX()-b.getMaxX());
        double dz=Math.max(Math.max(b.getMinZ()-l.getZ(),0),l.getZ()-b.getMaxZ());
        return dx*dx+dz*dz;
    }
}
