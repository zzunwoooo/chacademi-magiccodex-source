package school.chacademia.gimmick;
import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import school.chacademia.gimmick.puzzle.PuzzleDef;
final class Solids implements Listener {
 private final GimmickPlugin plugin;private final File file;private final BarrierLedger ledger;private Set<String> known=Set.of();
 Solids(GimmickPlugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"solids.yml");
  ledger=new BarrierLedger(records->{var yaml=new YamlConfiguration();records.forEach((k,v)->yaml.set(k,new ArrayList<>(v)));AtomicYaml.save(yaml,file);},new BarrierLedger.Blocks(){
   public boolean available(String key){return block(key)!=null;}
   public boolean air(String key){Block b=block(key);return b!=null&&b.getType().isAir()&&plugin.protection().at(b.getLocation());}
   public void place(String key){Block b=block(key);if(b!=null&&b.getType().isAir()&&plugin.protection().at(b.getLocation()))b.setType(Material.BARRIER,false);}
   public void clearOwnedBarrier(String key){Block b=block(key);if(b!=null&&b.getType()==Material.BARRIER)b.setType(Material.AIR,false);}
  });
 }
 void load(Set<String> knownIds){known=Set.copyOf(knownIds);var yaml=AtomicYaml.load(file);var records=new LinkedHashMap<String,Set<String>>();
  for(String owner:yaml.getKeys(false)){var keys=new LinkedHashSet<>(yaml.getStringList(owner));for(String key:keys){String[] parts=key.split(":");if(parts.length!=4)throw new IllegalStateException("Malformed solids journal; preserved");for(int i=1;i<4;i++)Integer.parseInt(parts[i]);}records.put(owner,keys);}ledger.load(records);cleanup();
 }
 private void reconcile(String owner,Set<String> wanted){if(!ledger.reconcile(owner,wanted))plugin.getLogger().severe("solids.yml write failed; new barriers refused and recovery records retained");}
 private void cleanup(){for(String id:ledger.owners())if(plugin.world().get(id)==null)reconcile(id,Set.of());}
 void refresh(Gimmick g){if(!g.live())return;var solid=g.inPuzzle()?plugin.puzzles().solid(g.puzzle,g.node):null;if(solid==null){clear(g);return;}reconcile(g.id,plugin.puzzles().isOn(g.puzzle,g.node)==solid.whenOn()?blocks(g,solid):Set.of());}
 void clear(Gimmick g){reconcile(g.id,Set.of());}
 void clearAll(){for(String owner:ledger.owners())reconcile(owner,Set.of());}
 @EventHandler public void worldLoad(WorldLoadEvent event){cleanup();}
 @EventHandler public void chunkLoad(ChunkLoadEvent event){cleanup();}
    /** Box centered on the gimmick's block, turned to the nearest 90 degrees of its yaw. */
    private Set<String> blocks(Gimmick g, PuzzleDef.Solid s) {
        Location at = g.location();
        if (at == null) return Set.of();
        if((long)s.width()*s.depth()*s.height()>4096)throw new IllegalArgumentException("Solid box exceeds 4096 blocks");
        Vector f = g.forward();
        int fx, fz;
        if (Math.abs(f.getX()) > Math.abs(f.getZ())) { fx = (int) Math.signum(f.getX()); fz = 0; } else { fx = 0; fz = (int) Math.signum(f.getZ()); }
        int rx = -fz, rz = fx; // right
        int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
        var out = new LinkedHashSet<String>();
        int w0 = -(s.width() - 1) / 2, d0 = -(s.depth() - 1) / 2;
        for (int w = 0; w < s.width(); w++) for (int d = 0; d < s.depth(); d++) for (int h = 0; h < s.height(); h++) {
            int ow = w0 + w, od = d0 + d + s.forward();
            out.add(at.getWorld().getName() + ":" + (bx + rx * ow + fx * od) + ":" + (by + s.up() + h) + ":" + (bz + rz * ow + fz * od));
        }
        return out;
    }

    private Block block(String key) {
        String[] f = key.split(":");
        if(f.length!=4)return null;
        World w = Bukkit.getWorld(f[0]);
        if (w == null) return null;
        int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }

}
