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
    private YamlConfiguration habitats;
    private NamespacedKey naturalKey;
    private boolean groupSpawning;
    @Override public void onEnable() {
        saveDefaultConfig(); reloadHabitats(); naturalKey=new NamespacedKey(this,"natural");
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(structures,this);
        for(var w:Bukkit.getWorlds()) for(var c:w.getLoadedChunks()) structures.index(c);
        // Handles entities loaded after restart too; only our marked natural mobs are eligible.
        Bukkit.getScheduler().runTaskTimer(this,this::cleanup,200,200);
    }
    void reloadHabitats() {
        if(!new File(getDataFolder(),"habitats.yml").exists()) saveResource("habitats.yml",false);
        var next=new YamlConfiguration();
        try { next.load(new File(getDataFolder(),"habitats.yml")); }
        catch(Exception e) { throw new IllegalArgumentException("habitats.yml 오류",e); }
        var entries=next.getConfigurationSection("habitats");
        if(entries==null||entries.getKeys(false).isEmpty()) throw new IllegalArgumentException("habitats 누락");
        for(String id:entries.getKeys(false)) {
            var s=entries.getConfigurationSection(id);
            if(s.getStringList("biomes").isEmpty() || s.getInt("group-min")<1 || s.getInt("group-max")>6
                || s.getInt("group-max")<s.getInt("group-min") || s.getInt("cap")<s.getInt("group-max")
                || s.getInt("min-y")>s.getInt("max-y")) throw new IllegalArgumentException("잘못된 서식지: "+id);
            Object seasons=s.get("seasons");
            if(seasons!=null && !(seasons instanceof List<?>)) throw new IllegalArgumentException("seasons는 목록으로 지정하세요: "+id);
            if(seasons instanceof List<?> values) {
                var normalized=new LinkedHashSet<String>();
                for(Object value:values) normalized.add(seasonId(value,id));
                s.set("seasons",List.copyOf(normalized));
            }
        }
        habitats=next;
    }
    @EventHandler public void condition(MythicConditionLoadEvent e) {
        if(e.getConditionName().equalsIgnoreCase("cahabitat")) e.register(new HabitatCondition(this,e.getContainer().getLine(),e.getConfig().getString("id","")));
    }
    public Set<String> ids() { return habitats.getConfigurationSection("habitats").getKeys(false); }
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
    private String seasonCheck(List<String> allowed,World world) {
        if(allowed.isEmpty()) return "OK";
        if(!Bukkit.getPluginManager().isPluginEnabled("MagicCodexBridge")) return "계절 정보 없음";
        var climate=Bukkit.getServicesManager().load(ClimateService.class);
        if(climate==null || !climate.enabledIn(world)) return "계절 정보 없음";
        return allowed.contains(climate.season().id()) ? "OK" : "출현 계절 아님";
    }
    public String check(String id,Location l,boolean population) {
        if(!Bukkit.isPrimaryThread()) return "메인 스레드 외 호출";
        var s=habitats.getConfigurationSection("habitats."+id);
        if(s==null) return "등록되지 않은 몹";
        World w=l.getWorld();
        if(!getConfig().getBoolean("enabled",true)||!getConfig().getStringList("worlds").contains(w.getName())) return "허용 월드 아님";
        int x=l.getBlockX(),y=l.getBlockY(),z=l.getBlockZ();
        if(!w.isChunkLoaded(x>>4,z>>4)) return "청크 미로드";
        if(!w.getWorldBorder().isInside(l)) return "월드 경계";
        if(y<s.getInt("min-y")||y>s.getInt("max-y")) return "높이";
        if(!s.getStringList("biomes").contains(w.getBiome(x,y,z).getKey().toString())) return "바이옴";
        String season=seasonCheck(s.getStringList("seasons"),w);
        if(!season.equals("OK")) return season;
        long t=w.getTime();String period=s.getString("time","any");
        if(period.equals("day")&&(t>12000) || period.equals("night")&&(t<13000||t>23000)) return "시간대";
        if(s.getBoolean("thunder")&&!w.isThundering()) return "뇌우 아님";
        if(s.getBoolean("full-moon")&&Math.floorMod(w.getFullTime()/24000,8)!=0) return "보름달 아님";
        String medium=s.getString("medium","land");
        int width=s.getInt("clearance-width",1),height=s.getInt("clearance-height",2),r=(width-1)/2;
        if(!w.isChunkLoaded((x-r)>>4,(z-r)>>4)||!w.isChunkLoaded((x+r)>>4,(z+r)>>4)
           ||!w.isChunkLoaded((x-r)>>4,(z+r)>>4)||!w.isChunkLoaded((x+r)>>4,(z-r)>>4)) return "주변 청크 미로드";
        for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) for(int dy=0;dy<height;dy++) {
            Block b=w.getBlockAt(x+dx,y+dy,z+dz);
            if(medium.equals("water") ? b.getType()!=Material.WATER : !b.isPassable()||b.isLiquid()) return "몸체 공간 부족";
        }
        if(!medium.equals("water")) {
            Material floor=w.getBlockAt(x,y-1,z).getType();
            if(!floor.isSolid()||floor.name().contains("LEAVES")||Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE).contains(floor)) return "바닥";
            if(s.getBoolean("natural-ground")&&!naturalGround(floor)) return "자연 지면 아님";
            for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++)
                if(!w.getBlockAt(x+dx,y-1,z+dz).getType().isSolid()) return "발판 부족";
        }
        boolean cave=w.getBiome(x,y,z).getKey().toString().contains(":cave/");
        if(!cave && !medium.equals("water") && w.getBlockAt(x,y,z).getLightFromSky()==0) return "지상 조건";
        if(cave&&w.getBlockAt(x,y,z).getLightFromBlocks()>s.getInt("max-block-light",7)) return "동굴 조명";
        if(s.getBoolean("near-water")&&!waterNearby(w,x,y,z)) return "물가 아님";
        var required=new HashSet<>(s.getStringList("structures"));
        if(!required.isEmpty()&&structures.nearby(l,required,s.getDouble("structure-radius",48)).isEmpty()) return "구조물 없음";
        for(String area:getConfig().getConfigurationSection("excluded-areas").getKeys(false)) {
            var a=getConfig().getConfigurationSection("excluded-areas."+area);
            if(w.getName().equals(a.getString("world"))&&Math.hypot(l.getX()-a.getDouble("x"),l.getZ()-a.getDouble("z"))<a.getDouble("radius")) return "보호 구역";
        }
        if(population) {
            double nearest=Double.POSITIVE_INFINITY;
            for(Player p:w.getPlayers()) if(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE) nearest=Math.min(nearest,p.getLocation().distanceSquared(l));
            if(nearest<Math.pow(getConfig().getDouble("player-min-distance",24),2)) return "플레이어 너무 가까움";
            if(nearest>Math.pow(getConfig().getDouble("player-max-distance",80),2)) return "활성 플레이어 없음";
            int same=0,total=0; double range=s.getDouble("cap-radius",64);
            for(Entity e:w.getNearbyEntities(l,96,64,96)) {
                if(!(e instanceof LivingEntity)||e.getScoreboardTags().contains("chacademia_pet")) continue;
                var mob=MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId());
                if(mob.isEmpty()) continue;
                String type=mob.get().getType().getInternalName();
                if(type.startsWith("CA_Wild_")) {
                    total++;
                    if((type.equals("CA_Wild_"+id)||type.equals("CA_Wild_"+id+"_shiny"))&&e.getLocation().distanceSquared(l)<range*range) same++;
                }
            }
            if(same>=s.getInt("cap",4)) return "동종 개체 제한";
            if(total>=getConfig().getInt("area-cap",24)) return "지역 개체 제한";
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
        String id=e.getMobType().getInternalName().substring(8).replaceFirst("_shiny$","");
        if(!ids().contains(id)) return;
        // RandomSpawn conditions already checked population before creating this entity.
        if(!check(id,e.getLocation(),false).equals("OK")) { e.setCancelled();return; }
        e.getEntity().getPersistentDataContainer().set(naturalKey,PersistentDataType.LONG,System.currentTimeMillis());
        if(groupSpawning) return;
        Entity leader=e.getEntity();
        Bukkit.getScheduler().runTask(this,()-> { if(leader.isValid()) group(id,leader.getLocation()); });
    }
    public int group(String id,Location origin) {
        var s=habitats.getConfigurationSection("habitats."+id);var random=ThreadLocalRandom.current();
        int count=random.nextInt(s.getInt("group-min"),s.getInt("group-max")+1),spawned=0;
        groupSpawning=true;
        try {
            for(int i=1;i<count;i++) for(int attempt=0;attempt<8;attempt++) {
                Location l=origin.clone().add(random.nextInt(-6,7)+.5,0,random.nextInt(-6,7)+.5);
                if(!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)) continue;
                if(s.getString("medium").equals("water")) l.add(0,random.nextInt(-1,2),0);
                else {
                    boolean found=false;
                    for(int dy=3;dy>=-3;dy--) if(l.clone().add(0,dy-1,0).getBlock().getType().isSolid()&&l.clone().add(0,dy,0).getBlock().isPassable()) {l.add(0,dy,0);found=true;break;}
                    if(!found) continue;
                }
                if(!check(id,l,true).equals("OK")) continue;
                boolean shiny=random.nextInt(Math.max(1,getConfig().getInt("shiny-denominator",4096)))==0;
                var type=MythicBukkit.inst().getMobManager().getMythicMob("CA_Wild_"+id+(shiny?"_shiny":""));
                if(type.isPresent()) {var mob=type.get().spawn(BukkitAdapter.adapt(l),1,SpawnReason.NATURAL);if(mob!=null)spawned++;}
                break;
            }
        } finally {groupSpawning=false;}
        return spawned;
    }
    void cleanup() {
        long now=System.currentTimeMillis();
        for(var mob:new ArrayList<>(MythicBukkit.inst().getMobManager().getActiveMobs())) {
            Entity e=mob.getEntity().getBukkitEntity();
            Long born=e.getPersistentDataContainer().get(naturalKey,PersistentDataType.LONG);
            if(born==null||now-born<60000||e.getScoreboardTags().contains("chacademia_pet")) continue;
            boolean near=e.getWorld().getPlayers().stream().anyMatch(p->p.getLocation().distanceSquared(e.getLocation())<128*128);
            if(!near) e.remove();
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args) {
        if(!sender.hasPermission("chacademia.wildlife.admin")) return true;
        if(args.length>0&&args[0].equals("reload")) {
            try {reloadConfig();reloadHabitats();sender.sendMessage("§a서식지 재적용 완료. RandomSpawns 변경 시 /mm reload도 실행하세요.");}
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
