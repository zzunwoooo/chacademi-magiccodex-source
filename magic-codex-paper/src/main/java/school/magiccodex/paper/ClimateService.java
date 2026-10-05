package school.magiccodex.paper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import school.magiccodex.protocol.SeasonProtocol;

/** Shared server season/temperature authority. Mutations are main-thread only. */
public final class ClimateService implements Listener, CommandExecutor, TabCompleter, PluginMessageListener, AutoCloseable {
    public enum Season {
        SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울");
        public final String label;
        Season(String label){this.label=label;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        static Season parse(String text){for(var s:values())if(s.id().equalsIgnoreCase(text)||s.label.equals(text))return s;throw new IllegalArgumentException("봄·여름·가을·겨울 중 선택하세요.");}
    }
    private final MagicCodexBridge plugin;
    private final ManaService mana;
    private final TemperatureService temperature;
    private final NamespacedKey coldKey;
    private final Map<UUID,Float> temperatures=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastSample=new HashMap<>();
    private final ArrayDeque<UUID> pending=new ArrayDeque<>();
    private final Set<UUID> queued=new HashSet<>();
    private final Map<UUID,Subscriber> subscribers=new HashMap<>();
    private final BukkitTask task;
    private final ClimateExpansion expansion;
    private YamlConfiguration config;
    private SeasonClock clock;
    private volatile Season current;
    private volatile boolean enabled;
    private volatile Set<String> worlds;
    private volatile double firePower=1,waterPower=1;
    private int ticks;
    private static final class Subscriber {long expires,lastSent;int season=-1;}

    ClimateService(MagicCodexBridge plugin,ManaService mana,TemperatureService temperature)throws IOException{
        this.plugin=plugin;this.mana=mana;this.temperature=temperature;this.coldKey=new NamespacedKey(plugin,"climate_cold");
        if(!new File(plugin.getDataFolder(),"climate.yml").exists())plugin.saveResource("climate.yml",false);
        config=readConfig();enabled=config.getBoolean("enabled",true);worlds=Set.copyOf(config.getStringList("worlds"));
        long now=clockTime();var state=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"climate-state.yml"));
        int start=Season.parse(state.getString("season",config.getString("initial-season","spring"))).ordinal();
        clock=new SeasonClock(state.getLong("anchor",now),start,state.getBoolean("automatic",config.getBoolean("automatic",true)),config.getLong("days-per-season",7)*24000,now);
        if(clock.anchor>now)clock.anchor=now;
        updateClock();saveState();
        Bukkit.getServicesManager().register(ClimateService.class,this,plugin,ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        var messenger=Bukkit.getMessenger();messenger.registerIncomingPluginChannel(plugin,SeasonProtocol.REQUEST,this);messenger.registerOutgoingPluginChannel(plugin,SeasonProtocol.RESPONSE);
        Objects.requireNonNull(plugin.getCommand("codexclimate")).setExecutor(this);plugin.getCommand("codexclimate").setTabCompleter(this);
        for(var p:Bukkit.getOnlinePlayers()){clearPenalty(p);enqueue(p);}
        expansion=Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")?new ClimateExpansion(this):null;
        if(expansion!=null)expansion.register();
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);
    }
    public Season season(){return current;}
    public boolean enabledIn(World world){return enabled&&world!=null&&worlds.contains(world.getName());}
    public OptionalDouble temperature(UUID id){var v=temperatures.get(id);return v==null?OptionalDouble.empty():OptionalDouble.of(v);}
    public double powerMultiplier(String element){return switch(element.toLowerCase(Locale.ROOT)){case "fire","flame","화염"->firePower;case "water","물"->waterPower;default->1;};}
    private long clockTime(){var w=Bukkit.getWorld(config.getString("clock-world","world"));return w==null?0:w.getFullTime();}
    private void updateClock(){
        clock.observe(clockTime());current=Season.values()[clock.season()];
        firePower=enabled?setting("seasons."+current.id()+".fire-power",1):1;
        waterPower=enabled?setting("seasons."+current.id()+".water-power",1):1;
    }
    private double setting(String key,double fallback){return config.getDouble(key,fallback);}
    private YamlConfiguration readConfig()throws IOException{
        var next=new YamlConfiguration();try{next.load(new File(plugin.getDataFolder(),"climate.yml"));}catch(Exception e){throw new IOException("climate.yml: "+e.getMessage(),e);}
        long days=next.getLong("days-per-season",7);if(days<1||days>365)throw new IOException("days-per-season must be 1..365");
        for(String key:next.getKeys(true)){Object value=next.get(key);if(value instanceof Number n&&(!Double.isFinite(n.doubleValue())||Math.abs(n.doubleValue())>10000))throw new IOException("Invalid number: "+key);}
        for(var season:Season.values()){
            String prefix="seasons."+season.id()+".";
            double min=next.getDouble(prefix+"minimum",0),max=next.getDouble(prefix+"maximum",30);
            if(min>max||min< -100||max>100)throw new IOException("Invalid temperatures: "+season.id());
            for(String key:List.of("mana-regeneration","fire-power","water-power")){double v=next.getDouble(prefix+key,1);if(v<0||v>3)throw new IOException("Invalid multiplier: "+prefix+key);}
        }
        for(String key:List.of("cold-speed-multiplier","severe-cold-speed-multiplier","cold-mana-multiplier","severe-cold-mana-multiplier")){double v=next.getDouble("temperature."+key,1);if(v<0||v>1)throw new IOException("Invalid multiplier: "+key);}
        return next;
    }
    private void tick(){
        ticks+=5;
        if(ticks%20==0){Season before=current;updateClock();if(before!=current){for(var p:Bukkit.getOnlinePlayers())enqueue(p);saveState();}sendSeasons();}
        int period=Math.clamp(config.getInt("performance.player-refresh-ticks",100),20,1200);
        if(ticks%period<5)for(var p:Bukkit.getOnlinePlayers())enqueue(p);
        for(int i=0;i<Math.clamp(config.getInt("performance.players-per-pass",8),1,64)&&!pending.isEmpty();i++){
            UUID id=pending.removeFirst();queued.remove(id);var p=Bukkit.getPlayer(id);if(p!=null)sample(p);
        }
    }
    private void enqueue(Player player){if(queued.add(player.getUniqueId()))pending.addLast(player.getUniqueId());}
    private void sample(Player p){
        UUID id=p.getUniqueId();
        if(!enabledIn(p.getWorld())){temperature.clearComputed(p);temperatures.remove(id);clearPenalty(p);return;}
        var block=p.getLocation().getBlock();String biome=block.getBiome().getKey().toString();
        var offsets=config.getConfigurationSection("temperature.biome-offsets");Object explicit=offsets==null?null:offsets.getValues(false).get(biome);
        double offset=explicit instanceof Number n?n.doubleValue():Math.clamp((block.getTemperature()-0.8)*setting("temperature.biome-offset-scale",12),-25,25);
        String prefix="seasons."+current.id()+".";
        float computed=ClimateMath.temperature(setting(prefix+"minimum",8),setting(prefix+"maximum",24),p.getWorld().getTime(),offset,p.getWorld().hasStorm(),setting("temperature.rain-cooling",2));
        temperature.computed(p,computed);float celsius=temperature.getTemperature(id);temperatures.put(id,celsius);
        boolean survival=!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);
        double speed=1,regen=setting(prefix+"mana-regeneration",1);
        if(survival&&celsius<setting("temperature.cold-below",0)){
            boolean severe=celsius<setting("temperature.severe-cold-below",-10);String band=severe?"severe-cold":"cold";
            speed=setting("temperature."+band+"-speed-multiplier",1);regen*=setting("temperature."+band+"-mana-multiplier",1);
        }
        var attribute=p.getAttribute(Attribute.MOVEMENT_SPEED);
        if(attribute!=null){var old=attribute.getModifier(coldKey);double amount=speed-1;
            if(old!=null&&Double.compare(old.getAmount(),amount)!=0){attribute.removeModifier(coldKey);old=null;}
            if(amount!=0&&old==null)attribute.addTransientModifier(new AttributeModifier(coldKey,amount,AttributeModifier.Operation.ADD_SCALAR));
        }
        if(mana.snapshot(id).isPresent())mana.setRegenerationMultiplier(id,"climate:regeneration",regen);
        Long previous=lastSample.put(id,(long)ticks);double seconds=previous==null?0:Math.clamp((ticks-previous)/20.0,0,10);
        if(survival&&celsius>=setting("temperature.hot-from",35)){
            String band=celsius>=setting("temperature.severe-hot-from",40)?"severe-hot":"hot";
            double extra=Math.max(0,setting("temperature."+band+"-exhaustion-per-5-seconds",0))*seconds/5;
            p.setExhaustion((float)Math.min(40,p.getExhaustion()+extra));
        }
    }
    private void clearPenalty(Player p){
        var attribute=p.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(coldKey);
        if(mana.snapshot(p.getUniqueId()).isPresent())mana.setRegenerationMultiplier(p.getUniqueId(),"climate:regeneration",1);
        lastSample.remove(p.getUniqueId());
    }
    @EventHandler public void join(PlayerJoinEvent event){clearPenalty(event.getPlayer());enqueue(event.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent event){clearPenalty(event.getPlayer());enqueue(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){var p=event.getPlayer();clearPenalty(p);temperatures.remove(p.getUniqueId());subscribers.remove(p.getUniqueId());}
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] bytes){
        if(channel.equals(SeasonProtocol.REQUEST)&&SeasonProtocol.validRequest(bytes)){var s=subscribers.computeIfAbsent(player.getUniqueId(),k->new Subscriber());s.expires=System.currentTimeMillis()+60000;sendSeason(player,s,System.currentTimeMillis());}
    }
    @EventHandler public void channelRegistered(PlayerRegisterChannelEvent event){
        if(SeasonProtocol.RESPONSE.equals(event.getChannel())){
            // May fire off the main thread; subscribers is main-thread only.
            var player=event.getPlayer();
            Bukkit.getScheduler().runTask(plugin,()->{var s=subscribers.computeIfAbsent(player.getUniqueId(),k->new Subscriber());s.expires=System.currentTimeMillis()+60000;if(player.isOnline())sendSeason(player,s,System.currentTimeMillis());});
        }
    }
    private void sendSeasons(){
        long now=System.currentTimeMillis();var iterator=subscribers.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();var s=entry.getValue();var p=Bukkit.getPlayer(entry.getKey());
            if(p==null||now>s.expires){iterator.remove();continue;}
            sendSeason(p,s,now);
        }
    }
    private void sendSeason(Player p,Subscriber s,long now){
        if((s.season!=current.ordinal()||now-s.lastSent>=15000)&&p.getListeningPluginChannels().contains(SeasonProtocol.RESPONSE)){
            p.sendPluginMessage(plugin,SeasonProtocol.RESPONSE,SeasonProtocol.encode(current.ordinal()));s.lastSent=now;s.season=current.ordinal();
        }
    }
    private void saveState(){
        var state=new YamlConfiguration();state.set("season",Season.values()[clock.start].id());state.set("anchor",clock.anchor);state.set("automatic",clock.automatic);
        try{state.save(new File(plugin.getDataFolder(),"climate-state.yml"));}catch(IOException e){plugin.getLogger().severe("계절 저장 실패: "+e.getMessage());}
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("magiccodex.climate.admin"))return true;
        try{
            if(args.length==2&&args[0].equalsIgnoreCase("season")){clock.set(Season.parse(args[1]).ordinal(),clockTime(),clock.automatic);updateClock();saveState();}
            else if(args.length==2&&args[0].equalsIgnoreCase("auto")&&Set.of("on","off").contains(args[1])){clock.set(current.ordinal(),clockTime(),args[1].equals("on"));saveState();}
            else if(args.length==1&&args[0].equalsIgnoreCase("reload")){
                var next=readConfig();int season=current.ordinal();boolean auto=clock.automatic;config=next;enabled=config.getBoolean("enabled",true);worlds=Set.copyOf(config.getStringList("worlds"));
                clock=new SeasonClock(clockTime(),season,auto,config.getLong("days-per-season",7)*24000,clockTime());updateClock();saveState();
            }else if(args.length!=0&&!(args.length==1&&args[0].equalsIgnoreCase("status")))throw new IllegalArgumentException("/codexclimate season 봄|여름|가을|겨울 · auto on|off · status · reload");
            for(var p:Bukkit.getOnlinePlayers())enqueue(p);
            sendSeasons();
            if(sender instanceof Player p && args.length==1 && args[0].equalsIgnoreCase("status")){
                var sub=subscribers.get(p.getUniqueId());
                sender.sendMessage("계절 HUD 채널: "+p.getListeningPluginChannels().contains(SeasonProtocol.RESPONSE)+" · 전송 계절: "+(sub==null||sub.season<0?"대기":Season.values()[sub.season].label));
            }
            sender.sendMessage("계절: "+current.label+" · "+clock.day()+"/"+(clock.length/24000)+"일 · 자동 전환 "+(clock.automatic?"ON":"OFF"));
        }catch(Exception e){sender.sendMessage("계절 설정 오류: "+e.getMessage());}return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(!s.hasPermission("magiccodex.climate.admin"))return List.of();
        var choices=args.length==1?List.of("status","season","auto","reload"):args.length==2&&args[0].equals("season")?List.of("봄","여름","가을","겨울"):args.length==2&&args[0].equals("auto")?List.of("on","off"):List.<String>of();
        return choices.stream().filter(x->x.startsWith(args[args.length-1])).toList();
    }
    @Override public void close(){task.cancel();saveState();for(var p:Bukkit.getOnlinePlayers()){clearPenalty(p);temperature.clearComputed(p);}if(expansion!=null)expansion.unregister();Bukkit.getServicesManager().unregister(ClimateService.class,this);temperatures.clear();subscribers.clear();pending.clear();queued.clear();}
}
