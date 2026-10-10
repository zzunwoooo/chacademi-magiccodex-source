package school.chacademia.wildlife;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.*;
import io.lumine.mythic.api.mobs.entities.SpawnReason;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import school.magiccodex.paper.ClimateService;

public final class WildlifePlugin extends JavaPlugin implements Listener {
    public final StructureIndex structures=new StructureIndex();
    /** config.yml에서 스폰 후보마다 읽던 값을 로드/리로드 때 한 번만 읽어 둔다. */
    private record Excluded(String world,double x,double z,double radius) {}
    private record Settings(boolean enabled,Set<String> worlds,double playerMinSquared,double playerMaxSquared,int areaCap,int shinyDenominator,List<Excluded> excluded) {}
    private Map<String,Habitat> habitats=Map.of();
    private Settings settings=new Settings(false,Set.of(),0,0,0,1,List.of());
    private final PopulationCache population=new PopulationCache();
    // 메인 스레드 전용 임시 좌표: 비교할 때마다 Location을 새로 만들지 않는다.
    private final Location scratch=new Location(null,0,0,0);
    private NamespacedKey naturalKey;
    private boolean groupSpawning;
    private long lastCleanupLog;
    @Override public void onEnable() {
        saveDefaultConfig(); reloadSettings(); reloadHabitats(); naturalKey=new NamespacedKey(this,"natural");
        getServer().getPluginManager().registerEvents(this,this);
        // 구조물 색인은 지연 방식이다: 청크 로드 때가 아니라 구조물 조건이 있는 서식지가 물을 때만 계산한다.
        getServer().getPluginManager().registerEvents(structures,this);
        // Handles entities loaded after restart too; only our marked natural mobs are eligible.
        Bukkit.getScheduler().runTaskTimer(this,this::cleanup,200,200);
    }
    /** excluded-areas가 없거나 항목이 잘못돼도 스폰 검사에서 NPE가 나지 않도록 여기서 걸러 둔다. */
    void reloadSettings() {
        var c=getConfig(); var excluded=new ArrayList<Excluded>(); var areas=c.getConfigurationSection("excluded-areas");
        if(areas!=null) for(String area:areas.getKeys(false)) {
            var a=areas.getConfigurationSection(area);
            if(a==null||a.getString("world")==null) { getLogger().warning("excluded-areas."+area+" 항목을 건너뜁니다(world 누락)."); continue; }
            excluded.add(new Excluded(a.getString("world"),a.getDouble("x"),a.getDouble("z"),a.getDouble("radius")));
        }
        double min=c.getDouble("player-min-distance",24),max=c.getDouble("player-max-distance",80);
        settings=new Settings(c.getBoolean("enabled",true),Set.copyOf(c.getStringList("worlds")),min*min,max*max,
            c.getInt("area-cap",24),Math.max(1,c.getInt("shiny-denominator",4096)),List.copyOf(excluded));
        population.clear();
    }
    /** 모든 서식지를 불변 Habitat으로 한 번에 해석한다. 하나라도 필수 값이 잘못되면 예외를 던지고 기존 설정을 유지한다. */
    void reloadHabitats() {
        if(!new File(getDataFolder(),"habitats.yml").exists()) saveResource("habitats.yml",false);
        var next=new YamlConfiguration();
        try { next.load(new File(getDataFolder(),"habitats.yml")); }
        catch(Exception e) { throw new IllegalArgumentException("habitats.yml 오류",e); }
        var entries=next.getConfigurationSection("habitats");
        if(entries==null||entries.getKeys(false).isEmpty()) throw new IllegalArgumentException("habitats 누락");
        var parsed=new LinkedHashMap<String,Habitat>(); var warnings=new ArrayList<String>();
        for(String id:entries.getKeys(false)) parsed.put(id,Habitat.parse(id,entries.getConfigurationSection(id),warnings));
        habitats=Collections.unmodifiableMap(parsed);
        if(!warnings.isEmpty()) getLogger().warning("habitats.yml 값 "+warnings.size()+"건을 기본값으로 대체했습니다: "+String.join(", ",warnings.subList(0,Math.min(10,warnings.size())))+(warnings.size()>10?" …":""));
        structures.clear(); population.clear();
    }
    @EventHandler public void condition(MythicConditionLoadEvent e) {
        if(e.getConditionName().equalsIgnoreCase("cahabitat")) e.register(new HabitatCondition(this,e.getContainer().getLine(),e.getConfig().getString("id","")));
    }
    public Set<String> ids() { return habitats.keySet(); }
    static String seasonId(Object value,String id) {
        if(!(value instanceof String text)) throw new IllegalArgumentException("잘못된 계절: "+id);
        return switch(text.toLowerCase(Locale.ROOT).trim()) {
            case "봄","spring" -> "spring";
            case "여름","summer" -> "summer";
            case "가을","autumn","fall" -> "autumn";
            case "겨울","winter" -> "winter";
            default -> throw new IllegalArgumentException("계절은 봄·여름·가을·겨울 중 지정하세요: "+id+" / "+text);
        };
    }
    private String seasonCheck(Set<String> allowed,World world) {
        if(allowed.isEmpty()) return "OK";
        if(!Bukkit.getPluginManager().isPluginEnabled("MagicCodexBridge")) return "계절 정보 없음";
        var climate=Bukkit.getServicesManager().load(ClimateService.class);
        if(climate==null || !climate.enabledIn(world)) return "계절 정보 없음";
        return allowed.contains(climate.season().id()) ? "OK" : "출현 계절 아님";
    }
    private static final Set<Material> BAD_FLOORS=Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE);
    public String check(String id,Location l,boolean population) {
        if(!Bukkit.isPrimaryThread()) return "메인 스레드 외 호출";
        Habitat h=habitats.get(id);
        if(h==null) return "등록되지 않은 몹";
        World w=l.getWorld(); Settings cfg=settings;
        if(w==null||!cfg.enabled()||!cfg.worlds().contains(w.getName())) return "허용 월드 아님";
        int x=l.getBlockX(),y=l.getBlockY(),z=l.getBlockZ();
        if(!w.isChunkLoaded(x>>4,z>>4)) return "청크 미로드";
        if(!w.getWorldBorder().isInside(l)) return "월드 경계";
        if(y<h.minY()||y>h.maxY()) return "높이";
        String biome=w.getBiome(x,y,z).getKey().toString();
        if(!h.biomes().contains(biome)) return "바이옴";
        String season=seasonCheck(h.seasons(),w);
        if(!season.equals("OK")) return season;
        long t=w.getTime();
        if(h.time()==Habitat.DAY&&(t>12000) || h.time()==Habitat.NIGHT&&(t<13000||t>23000)) return "시간대";
        if(h.thunder()&&!w.isThundering()) return "뇌우 아님";
        if(h.fullMoon()&&Math.floorMod(w.getFullTime()/24000,8)!=0) return "보름달 아님";
        boolean water=h.water();
        int height=h.clearanceHeight(),r=h.clearanceRadius();
        if(!w.isChunkLoaded((x-r)>>4,(z-r)>>4)||!w.isChunkLoaded((x+r)>>4,(z+r)>>4)
           ||!w.isChunkLoaded((x-r)>>4,(z+r)>>4)||!w.isChunkLoaded((x+r)>>4,(z-r)>>4)) return "주변 청크 미로드";
        for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) for(int dy=0;dy<height;dy++) {
            Block b=w.getBlockAt(x+dx,y+dy,z+dz);
            if(water ? b.getType()!=Material.WATER : !b.isPassable()||b.isLiquid()) return "몸체 공간 부족";
        }
        if(!water) {
            Material floor=w.getBlockAt(x,y-1,z).getType();
            if(!floor.isSolid()||floor.name().contains("LEAVES")||BAD_FLOORS.contains(floor)) return "바닥";
            if(h.naturalGround()&&!naturalGround(floor)) return "자연 지면 아님";
            for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++)
                if(!w.getBlockAt(x+dx,y-1,z+dz).getType().isSolid()) return "발판 부족";
        }
        boolean cave=biome.contains(":cave/");
        if(!cave && !water && w.getBlockAt(x,y,z).getLightFromSky()==0) return "지상 조건";
        if(cave&&w.getBlockAt(x,y,z).getLightFromBlocks()>h.maxBlockLight()) return "동굴 조명";
        if(h.nearWater()&&!waterNearby(w,x,y,z)) return "물가 아님";
        // 구조물 색인은 structures를 지정한 서식지만 건드린다(지연 계산, 로드된 청크만).
        if(!h.structures().isEmpty()&&structures.nearby(l,h.structures(),h.structureRadius()).isEmpty()) return "구조물 없음";
        for(Excluded a:cfg.excluded()) {
            if(w.getName().equals(a.world())&&Math.hypot(l.getX()-a.x(),l.getZ()-a.z())<a.radius()) return "보호 구역";
        }
        if(population) {
            double nearest=Double.POSITIVE_INFINITY;
            for(Player p:w.getPlayers()) if(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE) nearest=Math.min(nearest,p.getLocation(scratch).distanceSquared(l));
            if(nearest<cfg.playerMinSquared()) return "플레이어 너무 가까움";
            if(nearest>cfg.playerMaxSquared()) return "활성 플레이어 없음";
            // 후보마다 주변 엔티티를 훑지 않고, 격자 칸별로 약 40틱 동안 공유하는 스캔 결과에서 센다.
            int[] counts=this.population.count(w,l,"CA_Wild_"+id,h.capRadius());
            if(counts[0]>=h.cap()) return "동종 개체 제한";
            if(counts[1]>=cfg.areaCap()) return "지역 개체 제한";
        }
        return "OK";
    }
    static boolean naturalGround(Material m) {
        String n=m.name();return n.contains("DIRT")||n.contains("GRASS")||n.contains("SAND")||n.contains("STONE")||n.contains("GRAVEL")||n.contains("SNOW")||n.contains("ICE")||n.contains("MOSS")||n.contains("TERRACOTTA")||n.contains("PODZOL")||n.contains("MYCELIUM")||n.contains("CLAY")||n.contains("MUD")||n.contains("CALCITE")||n.contains("TUFF")||n.contains("BASALT")||n.contains("DEEPSLATE");
    }
    boolean waterNearby(World w,int x,int y,int z) {
        for(int dx=-5;dx<=5;dx++) for(int dz=-5;dz<=5;dz++) {
            if(!w.isChunkLoaded((x+dx)>>4,(z+dz)>>4)) continue;
            for(int dy=-2;dy<=1;dy++) if(w.getBlockAt(x+dx,y+dy,z+dz).getType()==Material.WATER) return true;
        }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(MythicMobSpawnEvent e) {
        if(e.getSpawnReason()!=SpawnReason.NATURAL||!e.getMobType().getInternalName().startsWith("CA_Wild_")) return;
        String type=e.getMobType().getInternalName();
        String id=type.substring(8).replaceFirst("_shiny$","");
        if(!habitats.containsKey(id)) return;
        // 개체 수·플레이어 거리 제한은 여기서 다시 보지 않는다(설계 의도):
        // 우두머리는 RandomSpawn의 cahabitat 조건(check(...,true))을, 무리는 group()의 check(...,true)를 이미 통과했고,
        // 이 시점에는 방금 만들어진 자기 자신이 세어져 제한 직전 스폰이 스스로 취소될 수 있다.
        // 따라서 RandomSpawn 항목에는 반드시 cahabitat 조건이 있어야 제한이 적용된다.
        if(!check(id,e.getLocation(),false).equals("OK")) { e.setCancelled();return; }
        e.getEntity().getPersistentDataContainer().set(naturalKey,PersistentDataType.LONG,System.currentTimeMillis());
        // 같은 틱·같은 스캔 주기 안의 다음 후보가 이 개체를 세도록 살아 있는 스캔 결과에 바로 더한다.
        Location at=e.getLocation(); if(at.getWorld()!=null) population.noteSpawn(at.getWorld(),type,at);
        if(groupSpawning) return;
        Entity leader=e.getEntity();
        Bukkit.getScheduler().runTask(this,()-> { if(leader.isValid()) group(id,leader.getLocation()); });
    }
    public int group(String id,Location origin) {
        Habitat h=habitats.get(id); World w=origin.getWorld();
        if(h==null||w==null) return 0;
        var random=ThreadLocalRandom.current();
        int count=random.nextInt(h.groupMin(),h.groupMax()+1),spawned=0;
        groupSpawning=true;
        try {
            for(int i=1;i<count;i++) for(int attempt=0;attempt<8;attempt++) {
                Location l=origin.clone().add(random.nextInt(-6,7)+.5,0,random.nextInt(-6,7)+.5);
                int x=l.getBlockX(),y=l.getBlockY(),z=l.getBlockZ();
                if(!w.isChunkLoaded(x>>4,z>>4)) continue;
                if(h.water()) l.add(0,random.nextInt(-1,2),0);
                else {
                    boolean found=false;
                    for(int dy=3;dy>=-3;dy--) if(w.getBlockAt(x,y+dy-1,z).getType().isSolid()&&w.getBlockAt(x,y+dy,z).isPassable()) {l.add(0,dy,0);found=true;break;}
                    if(!found) continue;
                }
                if(!check(id,l,true).equals("OK")) continue;
                boolean shiny=random.nextInt(settings.shinyDenominator())==0;
                var type=MythicBukkit.inst().getMobManager().getMythicMob("CA_Wild_"+id+(shiny?"_shiny":""));
                if(type.isPresent()) {var mob=type.get().spawn(BukkitAdapter.adapt(l),1,SpawnReason.NATURAL);if(mob!=null)spawned++;}
                break;
            }
        } finally {groupSpawning=false;}
        return spawned;
    }
    /** 10초마다: 60초 넘게 근처(128블록)에 플레이어가 없는 자연 스폰 개체를 지운다. 한 개체의 오류가 전체 정리를 멈추지 않는다. */
    void cleanup() {
        long now=System.currentTimeMillis(); int failures=0; Throwable firstFailure=null;
        // 월드별 플레이어 좌표를 이번 정리에서 한 번만 모은다.
        Map<World,double[]> players=new HashMap<>();
        for(var mob:new ArrayList<>(MythicBukkit.inst().getMobManager().getActiveMobs())) {
            try {
                if(mob==null||mob.getEntity()==null) continue;
                Entity e=mob.getEntity().getBukkitEntity();
                if(e==null||!e.isValid()) continue;
                Long born=e.getPersistentDataContainer().get(naturalKey,PersistentDataType.LONG);
                if(born==null||now-born<60000||e.getScoreboardTags().contains("chacademia_pet")) continue;
                World w=e.getWorld();
                double[] at=players.get(w);
                if(at==null) {
                    var list=w.getPlayers(); at=new double[list.size()*3]; int n=0;
                    for(Player p:list) { p.getLocation(scratch); at[n++]=scratch.getX(); at[n++]=scratch.getY(); at[n++]=scratch.getZ(); }
                    players.put(w,at);
                }
                e.getLocation(scratch); double x=scratch.getX(),y=scratch.getY(),z=scratch.getZ(); boolean near=false;
                for(int n=0;n<at.length;n+=3) { double dx=at[n]-x,dy=at[n+1]-y,dz=at[n+2]-z; if(dx*dx+dy*dy+dz*dz<128*128) { near=true; break; } }
                if(!near) e.remove();
            } catch(RuntimeException|LinkageError error) { failures++; if(firstFailure==null) firstFailure=error; }
        }
        if(failures>0&&now-lastCleanupLog>=60000) {
            lastCleanupLog=now;
            getLogger().log(java.util.logging.Level.WARNING,"야생 개체 정리 중 "+failures+"건 실패(나머지는 계속 처리, 로그는 1분에 한 번)",firstFailure);
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args) {
        if(!sender.hasPermission("chacademia.wildlife.admin")) return true;
        if(args.length>0&&args[0].equals("reload")) {
            try {reloadConfig();reloadSettings();reloadHabitats();sender.sendMessage("§a서식지 재적용 완료. RandomSpawns 변경 시 /mm reload도 실행하세요.");}
            catch(Exception ex) {sender.sendMessage("§c설정 오류: "+ex.getMessage());} return true;
        }
        if(sender instanceof Player p) {
            if(args.length==2&&args[0].equals("check")) p.sendMessage(args[1]+": "+check(args[1],p.getLocation(),false)+" / "+p.getWorld().getBiome(p.getLocation()).getKey());
            else if(args.length==1&&args[0].equals("structures")) p.sendMessage(structures.nearby(p.getLocation(),Set.of(),96).toString());
            else p.sendMessage("/야생관리 check <몹ID> | structures | reload");
        }
        return true;
    }
}
