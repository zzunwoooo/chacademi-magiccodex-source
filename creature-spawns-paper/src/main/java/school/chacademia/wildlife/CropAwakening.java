package school.chacademia.wildlife;

import io.lumine.mythic.api.mobs.entities.SpawnReason;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 살아나는 작물: 다 자란 바닐라 작물을 수확하면 확률로 작물 몹(CA_Crop_<id>)이 땅에서 기어나온다.
 * 서버 부담: 수확 1회당 맵 조회 1번 + 확률 1번, 몹 1마리당 지연 작업 1개(수명 끝 제거)뿐. 매 틱 작업 없음.
 */
final class CropAwakening implements Listener {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private final WildlifePlugin plugin;
    private final Map<UUID, Set<UUID>> owned = new HashMap<>();
    private final Map<UUID, Integer> pending = new HashMap<>();
    private Map<Material, String> crops = Map.of();

    CropAwakening(WildlifePlugin plugin) { this.plugin = plugin; }

    void reload() {
        var config = plugin.getConfig();
        String path = "crop-awakening.crops";
        // JavaPlugin defaults supply scalar getters, but an absent section is materialized empty by
        // getConfigurationSection and getKeys(false) does not enumerate inherited defaults.
        // Check direct presence before requesting a section; explicit empty/custom maps remain authoritative.
        var section = config.isSet(path) ? config.getConfigurationSection(path)
            : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection(path);
        var next = new EnumMap<Material, String>(Material.class);
        if (section != null) for (String key : section.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null) { plugin.getLogger().warning("crop-awakening: 알 수 없는 블록 " + key); continue; }
            next.put(m, section.getString(key));
        }
        crops = next;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void harvest(BlockBreakEvent e) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("crop-awakening.enabled", true)) return;
        Block b = e.getBlock();
        String id = crops.get(b.getType());
        if (id == null) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        var worlds = cfg.getStringList("crop-awakening.worlds");
        if (!worlds.isEmpty() && !worlds.contains(b.getWorld().getName())) return;
        if (!mature(b)) return;
        var random = ThreadLocalRandom.current();
        if (random.nextDouble() >= cfg.getDouble("crop-awakening.chance", 0.05)) return;

        UUID ownerId = p.getUniqueId();
        if (activeCount(ownerId) + pending.getOrDefault(ownerId, 0) >= cfg.getInt("crop-awakening.max-per-player", 3)) return;

        int denom = cfg.getInt("crop-awakening.shiny-denominator", cfg.getInt("shiny-denominator", 4096));
        boolean shiny = random.nextInt(Math.max(1, denom)) == 0;
        if (MythicBukkit.inst().getMobManager().getMythicMob("CA_Crop_" + id + (shiny ? "_shiny" : "")).isEmpty()) {
            plugin.getLogger().warning("crop-awakening: 미몹 없음 CA_Crop_" + id); return;
        }
        if (cfg.getBoolean("crop-awakening.cancel-drops", true)) e.setDropItems(false);
        Location at = b.getLocation().add(0.5, 0, 0.5);
        // 블록이 실제로 부서진 다음 틱에 소환
        pending.merge(ownerId, 1, Integer::sum);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (p.isOnline() && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) spawn(p, id, shiny, at);
                } finally { releasePending(ownerId); }
            });
        } catch (RuntimeException error) { releasePending(ownerId); throw error; }
    }

    /** 작물 몹 소환(수확·테스트 명령 공용). 수확한 사람을 바라보며 나오고, 수명이 끝나면 사라진다. */
    private void releasePending(UUID ownerId) {
        pending.computeIfPresent(ownerId, (key, value) -> value <= 1 ? null : value - 1);
    }

    private int activeCount(UUID ownerId) {
        Set<UUID> mine = owned.get(ownerId);
        if (mine == null) return 0;
        mine.removeIf(u -> { Entity x = Bukkit.getEntity(u); return x == null || !x.isValid() || x.isDead(); });
        if (mine.isEmpty()) owned.remove(ownerId);
        return mine.size();
    }

    String spawn(Player owner, String id, boolean shiny, Location at) {
        if (activeCount(owner.getUniqueId()) >= plugin.getConfig().getInt("crop-awakening.max-per-player", 3)) return "동시 작물 개체 제한";
        var type = MythicBukkit.inst().getMobManager().getMythicMob("CA_Crop_" + id + (shiny ? "_shiny" : ""));
        if (type.isEmpty()) return "미몹 없음: CA_Crop_" + id + (shiny ? "_shiny" : "");
        Location l = at.clone();
        Vector look = owner.getLocation().toVector().subtract(l.toVector()).setY(0);
        if (look.lengthSquared() > 1.0E-4) l.setDirection(look);
        var mob = type.get().spawn(BukkitAdapter.adapt(l), 1, SpawnReason.OTHER);
        if (mob == null) return "소환 실패";
        Entity ent = mob.getEntity().getBukkitEntity();
        ent.setPersistent(false);
        owned.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>()).add(ent.getUniqueId());
        long life = plugin.getConfig().getLong("crop-awakening.lifetime-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Set<UUID> mine = owned.get(owner.getUniqueId());
            if (mine != null) {
                mine.remove(ent.getUniqueId());
                if (mine.isEmpty()) owned.remove(owner.getUniqueId());
            }
            if (!ent.isValid() || ent.isDead()) return;
            ent.getWorld().spawnParticle(Particle.POOF, ent.getLocation().add(0, 0.4, 0), 10, 0.25, 0.25, 0.25, 0.02);
            ent.remove();
        }, life);
        return "OK";
    }

    Collection<String> ids() { return new TreeSet<>(crops.values()); }

    /** 다 자란 작물만. 호박·수박은 붙어 있는 줄기가 이 블록을 가리킬 때만(설치한 블록 반복 수확 방지). */
    private static boolean mature(Block b) {
        if (b.getBlockData() instanceof Ageable a) return a.getAge() >= a.getMaximumAge();
        Material stem = switch (b.getType()) {
            case PUMPKIN -> Material.ATTACHED_PUMPKIN_STEM;
            case MELON -> Material.ATTACHED_MELON_STEM;
            default -> null;
        };
        if (stem == null) return false;
        for (BlockFace f : SIDES) {
            Block s = b.getRelative(f);
            if (s.getType() == stem && s.getBlockData() instanceof Directional d && d.getFacing() == f.getOppositeFace()) return true;
        }
        return false;
    }
}
