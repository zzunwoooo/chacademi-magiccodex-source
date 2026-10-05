package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import school.magiccodex.protocol.TamingProtocol;
import school.magiccodex.protocol.TamingProtocol.State;

/** Event-driven grace states, bounded active casts; all Bukkit mutations on the main thread. */
final class TamingBridge implements Listener,PluginMessageListener,CommandExecutor,AutoCloseable {
    private ShinyBridge shiny;
    private final MagicCodexBridge plugin;private final ManaService mana;private final PetBridge pets;
    private final Map<String,Profile> profiles=new HashMap<>();
    private final Set<String> noShinyRoll=new HashSet<>();
    private final Map<UUID,Grace> graces=new HashMap<>();private final Map<UUID,Cast> casts=new HashMap<>();
    private final Map<UUID,long[]> requests=new HashMap<>();private final Set<UUID> locked=new HashSet<>();private final Set<UUID> casting=new HashSet<>();
    private final Set<TamingModelFx> effects=new HashSet<>();
    private final NamespacedKey graceKey;
    private double range,cost;private int duration,cooldown,graceSeconds,maxActive;private long token;private boolean closed;
    private boolean models;private Object mobManager;private java.lang.reflect.Method activeMob;
    private record Profile(String id,String mythic,String vanilla,String pet,String name,boolean boss,double base,double bonus,double scale){}
    private static final class Grace {
        final LivingEntity entity;final long until;final TamingModelFx fx;long idleAfter=now()+850;
        Grace(LivingEntity entity,long until,TamingModelFx fx){this.entity=entity;this.until=until;this.fx=fx;}
    }
    private static final class Cast {
        final Player player;final LivingEntity entity;final Profile profile;final String permission;final long token,start;final double chance;final TamingModelFx fx;
        boolean pending;long nextSend;
        Cast(Player p,LivingEntity e,Profile profile,String permission,long token,double chance,TamingModelFx fx){player=p;entity=e;this.profile=profile;this.permission=permission;this.token=token;start=now();this.chance=chance;this.fx=fx;}
    }
    TamingBridge(MagicCodexBridge p,ManaService mana,PetBridge pets)throws Exception{
        plugin=p;this.mana=mana;this.pets=pets;graceKey=new NamespacedKey(p,"taming_grace");
        if(!new File(p.getDataFolder(),"taming.yml").exists())p.saveResource("taming.yml",false);reload();
        var m=p.getServer().getMessenger();m.registerIncomingPluginChannel(p,TamingProtocol.REQUEST,this);m.registerOutgoingPluginChannel(p,TamingProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,p);Bukkit.getPluginManager().registerEvents(new CosmeticPetSafety(),p);p.getCommand("교화").setExecutor(this);p.getCommand("교화관리").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(p,this::tick,4,4);
        shiny=new ShinyBridge(p,this);
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    private void reload()throws Exception{
        var y=CreatureConfigFiles.load(plugin.getDataFolder(),"taming","targets");
        var rollDisabled=new HashSet<String>();
        var loaded=new HashMap<String,Profile>();var section=y.getConfigurationSection("targets");
        if(section!=null)for(String id:section.getKeys(false)){
            var s=section.getConfigurationSection(id);if(s==null)throw new IllegalArgumentException(id);
            String mythic=s.getString("mythic-id",""),vanilla=s.getString("vanilla-type",""),pet=s.getString("pet-id",""),name=s.getString("name",id);
            double base=s.getDouble("chance",.3),bonus=s.getDouble("low-health-bonus",.2),scale=s.getDouble("model-scale",1);
            if(pet.isBlank()||name.length()>96||!Double.isFinite(base)||base<0||base>1||!Double.isFinite(bonus)||bonus<0||bonus>1||!Double.isFinite(scale)||scale<.1||scale>10||(mythic.isBlank()&&vanilla.isBlank()))throw new IllegalArgumentException("교화 대상 설정: "+id);
            if(!vanilla.isEmpty())EntityType.valueOf(vanilla);
            loaded.put(id,new Profile(id,mythic,vanilla,pet,name,s.getBoolean("boss"),base,bonus,scale));
            if(!s.getBoolean("roll-shiny",true))rollDisabled.add(id);
        }
        double r=y.getDouble("range",12),c=y.getDouble("mana-cost",15);if(!Double.isFinite(r)||r<2||r>32||!Double.isFinite(c)||c<0||c>1000000)throw new IllegalArgumentException("교화 범위/마나 설정");
        profiles.clear();profiles.putAll(loaded);noShinyRoll.clear();noShinyRoll.addAll(rollDisabled);range=r;cost=c;duration=Math.clamp(y.getInt("cast-milliseconds",4000),1000,30000);cooldown=Math.clamp(y.getInt("cooldown-seconds",8),0,3600)*1000;graceSeconds=Math.clamp(y.getInt("boss-grace-seconds",30),5,300);maxActive=Math.clamp(y.getInt("max-active",64),1,256);
        models=y.getBoolean("modelengine",true)&&Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
        mobManager=null;activeMob=null;
        var mm=Bukkit.getPluginManager().getPlugin("MythicMobs");if(mm!=null&&mm.isEnabled()){
            var cls=Class.forName("io.lumine.mythic.bukkit.MythicBukkit",true,mm.getClass().getClassLoader());Object api=cls.getMethod("inst").invoke(null);mobManager=cls.getMethod("getMobManager").invoke(api);activeMob=mobManager.getClass().getMethod("getActiveMob",UUID.class);
        }
    }
    String profileId(LivingEntity e){var p=baseProfile(e);return p==null?null:p.id;}
    boolean allowsShinyRoll(String id){return !noShinyRoll.contains(id);}
    boolean captureLocked(LivingEntity e){return locked.contains(e.getUniqueId());}
    boolean configuredMythic(LivingEntity e){try{return activeMob!=null&&((Optional<?>)activeMob.invoke(mobManager,e.getUniqueId())).isPresent();}catch(Exception ex){return false;}}
    private Profile profile(LivingEntity e){
        var p=baseProfile(e);if(p==null||shiny==null||!shiny.isShiny(e))return p;
        return new Profile(p.id,p.mythic,p.vanilla,p.pet+"_shiny","§e"+ChatColor.stripColor(p.name),p.boss,p.base,p.bonus,p.scale);
    }
    private Profile baseProfile(LivingEntity e){
        if(e instanceof Player||e instanceof ArmorStand||e instanceof Tameable t&&t.isTamed()||e instanceof AbstractHorse h&&h.isTamed()||e.getScoreboardTags().contains("chacademia_pet")||pets.activePet(e))return null;
        if(mobManager!=null)try{
            var a=(Optional<?>)activeMob.invoke(mobManager,e.getUniqueId());if(a.isPresent()){
                Object type=a.get().getClass().getMethod("getType").invoke(a.get());String id=(String)type.getClass().getMethod("getInternalName").invoke(type);
                return profiles.values().stream().filter(p->p.mythic.equals(id)).findFirst().orElse(null); // Never capture an unconfigured custom boss as a vanilla mob.
            }
        }catch(Exception ex){return null;}
        return profiles.values().stream().filter(p->p.vanilla.equals(e.getType().name())).findFirst().orElse(null);
    }
    private LivingEntity target(Player p){return target(p,range);}
    /** Shared server-authoritative ray for taming and rarity commands. Never picks by proximity. */
    LivingEntity target(Player p,double maxRange){
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return null;
        var eye=p.getEyeLocation();var origin=eye.toVector();var direction=eye.getDirection();
        var block=p.getWorld().rayTraceBlocks(eye,direction,maxRange,FluidCollisionMode.NEVER,true);
        double limit=block==null?maxRange:Math.min(maxRange,block.getHitPosition().distance(origin));
        if(limit<=0)return null;
        var hit=p.getWorld().rayTraceEntities(eye,direction,limit,.25,e->e!=p&&e.isValid()&&!e.isDead()
                &&(e instanceof LivingEntity||e instanceof org.bukkit.entity.Interaction||e instanceof org.bukkit.entity.Display));
        double closest=hit==null?Double.POSITIVE_INFINITY:rayHitDistance(origin,direction,hit.getHitPosition(),limit);
        LivingEntity selected=hit==null?null:originalTarget(hit.getHitEntity());
        // Only query loaded bases in this bounded region; inspect actual sub-hitboxes, not visual proximity.
        if(models)try{
            for(var candidate:p.getWorld().getNearbyLivingEntities(eye,maxRange)){
                if(candidate==p||!candidate.isValid()||candidate.isDead())continue;
                var modeled=com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(candidate.getUniqueId());
                if(modeled==null)continue;
                for(var model:modeled.getModels().values())for(var bone:model.getBones().values()){
                    var behavior=bone.getBoneBehavior(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.SUB_HITBOX);
                    if(behavior.isEmpty())continue;
                    var sub=behavior.get().getHitboxEntity();if(sub==null||sub.getLocation().getWorld()!=p.getWorld())continue;
                    var box=sub.getOrientedBoundingBox();if(box==null)continue;
                    var intersection=box.rayTrace(origin.toVector3f(),direction.toVector3f(),limit,null);
                    var point=box.contains(origin.toVector3f())?origin:intersection==null?null:intersection.getHitPosition();
                    double distance=rayHitDistance(origin,direction,point,limit);
                    if(distance<closest){
                        var base=originalBase(sub.getBone().getActiveModel().getModeledEntity());
                        if(base!=null&&base!=p&&base.isValid()&&!base.isDead()&&base.getWorld()==p.getWorld()){
                            closest=distance;selected=base;
                        }
                    }
                }
            }
        }catch(RuntimeException|LinkageError ex){
            // Fail closed if the installed ModelEngine API cannot establish the hit's owner.
            return null;
        }
        // A nearer unregistered mob remains a blocker: eligibility is checked after the ray is resolved.
        return selected!=null&&selected!=p&&selected.isValid()&&!selected.isDead()&&selected.getWorld()==p.getWorld()
                &&p.getLocation().distanceSquared(selected.getLocation())<=maxRange*maxRange?selected:null;
    }
    private LivingEntity originalTarget(Entity hit){
        if(hit==null)return null;
        if(models)try{
            var tracker=com.ticxo.modelengine.api.ModelEngineAPI.getInteractionTracker();
            var sub=tracker.getHitbox(hit.getEntityId());
            if(sub!=null)return originalBase(sub.getBone().getActiveModel().getModeledEntity());
            var model=tracker.getModelRelay(hit.getEntityId());if(model!=null)return originalBase(model.getModeledEntity());
            var relay=tracker.getEntityRelay(hit.getEntityId());
            if(relay!=null)return originalBase(com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(relay));
        }catch(RuntimeException|LinkageError ex){return null;}
        return hit instanceof LivingEntity living&&!(living instanceof ArmorStand)?living:null;
    }
    private static LivingEntity originalBase(com.ticxo.modelengine.api.model.ModeledEntity modeled){
        if(modeled==null)return null;
        var base=modeled.getBase().getOriginal();return base instanceof LivingEntity living?living:null;
    }
    static double rayHitDistance(Vector origin,Vector direction,Vector point,double limit){
        if(point==null||!Double.isFinite(limit)||limit<0)return Double.POSITIVE_INFINITY;
        var offset=point.clone().subtract(origin);double length=direction.length();
        if(!Double.isFinite(length)||length<1e-9)return Double.POSITIVE_INFINITY;
        double along=offset.dot(direction)/length;
        double perpendicular=Math.max(0,offset.lengthSquared()-along*along);
        return Double.isFinite(along)&&Double.isFinite(perpendicular)&&along>=0&&along<=limit&&perpendicular<=1e-4
                ?along:Double.POSITIVE_INFINITY;
    }
    private boolean reachable(Player p,LivingEntity e){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&e.isValid()&&!e.isDead()&&p.getWorld()==e.getWorld()&&p.getLocation().distanceSquared(e.getLocation())<=range*range&&p.hasLineOfSight(e);}
    private double chance(LivingEntity e,Profile p){var a=e.getAttribute(Attribute.MAX_HEALTH);return TamingRules.chance(p.base,p.bonus,e.getHealth(),a==null?20:a.getValue(),p.boss);}
    private int graceMs(LivingEntity e){var g=graces.get(e.getUniqueId());return g==null?0:(int)Math.max(0,g.until-now());}
    private void send(Player p,State s){if(p.isOnline()&&p.getListeningPluginChannels().contains(TamingProtocol.RESPONSE))p.sendPluginMessage(plugin,TamingProtocol.RESPONSE,TamingProtocol.encode(s));}
    private void message(Player p,String text){send(p,new State(++token,TamingProtocol.ERROR,-1,"",false,0,0,0,0,text));p.sendMessage(text);}
    private void query(Player p,UUID claimed){
        if(casts.containsKey(p.getUniqueId()))return;LivingEntity e=target(p);Profile f=e==null?null:profile(e);
        if(e==null||!e.getUniqueId().equals(claimed)||f==null||!p.hasPermission("magiccodex.taming")){send(p,new State(0,0,-1,"",false,0,0,0,0,""));return;}
        String hint=f.boss&&!graces.containsKey(e.getUniqueId())?"처치 후 교화 가능":"/교화 · 시전";
        if(locked.contains(e.getUniqueId()))hint="다른 플레이어가 교화 중";
        send(p,new State(0,TamingProtocol.TARGET,e.getEntityId(),f.name,f.boss,chance(e,f),graceMs(e),0,0,hint));
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] data){
        if(!channel.equals(TamingProtocol.REQUEST))return;TamingProtocol.Request r;try{r=TamingProtocol.request(data);}catch(IllegalArgumentException ex){return;}
        // Per-action limit: frequent HUD queries must not swallow a cast or cancel press.
        if(r.action()<0||r.action()>2)return;long n=now();long[] last=requests.computeIfAbsent(p.getUniqueId(),k->new long[3]);if(n-last[r.action()]<200)return;last[r.action()]=n;
        if(r.action()==TamingProtocol.QUERY)query(p,r.target());else if(r.action()==TamingProtocol.CANCEL){var c=casts.get(p.getUniqueId());if(c!=null&&!c.pending)finish(c,false,"교화를 취소했습니다.");}else cast(p);
    }
    boolean cast(Player p){
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return false;
        // Same reentrancy guard as ManaBridge: messages/saves below may re-enter through commands or events.
        if(!casting.add(p.getUniqueId()))return false;
        try{return castGuarded(p);}finally{casting.remove(p.getUniqueId());}
    }
    private boolean castGuarded(Player p){
        if(!p.hasPermission("magiccodex.taming")||!plugin.playerStateReady(p)){message(p,"지금은 교화를 사용할 수 없습니다.");return false;}
        if(casts.containsKey(p.getUniqueId()))return false;
        var e=target(p);var f=e==null?null:profile(e);
        if(f==null||!reachable(p,e)){message(p,"교화할 대상을 바라봐 주세요.");return false;}
        if(f.boss&&!graces.containsKey(e.getUniqueId())){message(p,"이 대상은 처치한 뒤 교화할 수 있습니다.");return false;}
        if(f.boss&&graceMs(e)<duration){message(p,"교화할 시간이 부족합니다.");return false;}
        if(locked.contains(e.getUniqueId())||casts.size()>=maxActive){message(p,"지금은 이 대상을 교화할 수 없습니다.");return false;}
        final String permission;
        try{permission=pets.capturePermission(f.pet);if(!Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))throw new IllegalStateException("LuckPerms 연결이 필요합니다.");}
        catch(Exception ex){message(p,ex.getMessage()==null?"펫 연결 설정을 확인해 주세요.":ex.getMessage());return false;}
        if(p.hasPermission(permission)){message(p,"이미 도감에 등록된 펫입니다.");return false;}
        var account=mana.account(p.getUniqueId());var spell=new ManaSpells.Spell("taming","교화","magiccodex.taming",cost,cooldown);
        var result=ManaCasting.attempt(account,spell,true,System.currentTimeMillis(),()->{
            var g=graces.get(e.getUniqueId());var fx=g==null?effect(e,false,f.scale):g.fx;
            var c=new Cast(p,e,f,permission,++token,chance(e,f),fx);casts.put(p.getUniqueId(),c);locked.add(e.getUniqueId());if(fx!=null)fx.play("channel");emit(c,TamingProtocol.CHANNEL,"교화 중…");return true;
        });
        mana.publish(p.getUniqueId());mana.save(p);plugin.savePlayerState(p);
        if(result.status()!=school.magiccodex.protocol.ManaProtocol.OK){message(p,result.status()==school.magiccodex.protocol.ManaProtocol.EMPTY?"마나가 부족합니다.":"교화 재사용 대기 중입니다.");return false;}return true;
    }
    private void emit(Cast c,int status,String message){send(c.player,new State(c.token,status,c.entity.getEntityId(),c.profile.name,c.profile.boss,c.chance,graceMs(c.entity),Math.max(0,(int)(duration-(now()-c.start))),duration,message));}
    private TamingModelFx effect(LivingEntity e,boolean boss,double scale){
        if(!models)return null;
        try{var fx=new TamingModelFx(e,boss,scale);effects.add(fx);return fx;}
        catch(RuntimeException|LinkageError ex){plugin.getLogger().warning("교화 모델 생성 실패: "+ex.getMessage());models=false;return null;}
    }
    private void removeEffect(TamingModelFx fx){if(fx!=null&&effects.remove(fx))try{fx.close();}catch(RuntimeException ex){plugin.getLogger().warning("교화 모델 제거: "+ex.getMessage());}}
    private void tick(){
        long n=now();
        for(var g:List.copyOf(graces.values())){
            if(!g.entity.isValid()||g.entity.isDead()){graces.remove(g.entity.getUniqueId());removeEffect(g.fx);continue;}
            if(n>=g.until&&!locked.contains(g.entity.getUniqueId())){graces.remove(g.entity.getUniqueId());g.entity.getWorld().playSound(g.entity.getLocation(),"magiccodex:taming.seal_timeout",.6f,1);g.entity.remove();if(g.fx!=null)g.fx.play("timeout");Bukkit.getScheduler().runTaskLater(plugin,()->removeEffect(g.fx),23);continue;}
            g.entity.setVelocity(new Vector());if(g.fx!=null&&!locked.contains(g.entity.getUniqueId())&&n>=g.idleAfter)g.fx.play("idle");
        }
        for(var c:List.copyOf(casts.values())){
            if(c.pending)continue;
            if(!reachable(c.player,c.entity)||!c.player.hasPermission("magiccodex.taming")){finish(c,false,"대상에서 멀어져 교화가 중단되었습니다.");continue;}
            if(c.fx!=null)c.fx.follow(c.entity);
            if(n-c.start>=duration){if(TamingRules.succeeds(c.chance,ThreadLocalRandom.current().nextDouble()))grant(c);else finish(c,false,"교화에 실패했습니다.");}
            else if(n>=c.nextSend){c.nextSend=n+500;emit(c,TamingProtocol.CHANNEL,"교화 중…");}
        }
    }
    private void grant(Cast c){
        c.pending=true;emit(c,TamingProtocol.PENDING,"도감에 기록하는 중…");
        try{
            var lp=LuckPermsProvider.get();var user=lp.getUserManager().getUser(c.player.getUniqueId());if(user==null)throw new IllegalStateException("권한 사용자 없음");
            var node=Node.builder(c.permission).build();boolean added=user.data().add(node).wasSuccessful();
            lp.getUserManager().saveUser(user).whenComplete((v,error)->{
                if(error!=null&&added)user.data().remove(node);
                if(!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->{if(closed)return;if(error!=null){plugin.getLogger().warning("교화 권한 저장 실패: "+c.player.getUniqueId());finish(c,false,"펫을 저장하지 못했습니다. 다시 시도해 주세요.");}else finish(c,true,"교화 성공 · "+c.profile.name);});
            });
        }catch(RuntimeException ex){c.pending=false;finish(c,false,"펫을 저장하지 못했습니다. 다시 시도해 주세요.");}
    }
    private void finish(Cast c,boolean success,String text){
        if(!casts.remove(c.player.getUniqueId(),c))return;locked.remove(c.entity.getUniqueId());
        emit(c,success?TamingProtocol.SUCCESS:TamingProtocol.FAIL,text);
        if(success){graces.remove(c.entity.getUniqueId());c.entity.remove();if(c.fx!=null)c.fx.play("success");Bukkit.getScheduler().runTaskLater(plugin,()->removeEffect(c.fx),22);}
        else{if(c.fx!=null)c.fx.play("fail");var grace=graces.get(c.entity.getUniqueId());if(grace!=null)grace.idleAfter=now()+800;Bukkit.getScheduler().runTaskLater(plugin,()->{if(grace!=null&&graces.containsKey(c.entity.getUniqueId())){if(c.fx!=null&&!locked.contains(c.entity.getUniqueId()))c.fx.play("idle");}else removeEffect(c.fx);},16);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event){
        if(event instanceof EntityDamageByEntityEvent by){Entity source=by.getDamager();if(source instanceof Projectile pr&&pr.getShooter() instanceof Entity shooter)source=shooter;if(graces.containsKey(source.getUniqueId())){event.setCancelled(true);return;}}
        if(!(event.getEntity() instanceof LivingEntity e))return;
        if(locked.contains(e.getUniqueId())||graces.containsKey(e.getUniqueId())){event.setCancelled(true);return;}
        if(event.getFinalDamage()<e.getHealth())return;var f=profile(e);if(f==null||!f.boss)return;
        // Require a real player attack; environmental deaths must not create orphan capture encounters.
        Player attacker=null;if(event instanceof EntityDamageByEntityEvent by){if(by.getDamager() instanceof Player p)attacker=p;else if(by.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player p)attacker=p;}
        if(attacker==null||!attacker.hasPermission("magiccodex.taming")||graces.size()>=maxActive)return;
        try{pets.capturePermission(f.pet);}catch(Exception ex){return;}
        event.setCancelled(true);seal(e,f);
        attacker.playSound(e.getLocation(),"magiccodex:taming.seal_spawn",.7f,1);
    }
    private void seal(LivingEntity e,Profile f){
        var fx=effect(e,true,f.scale);var g=new Grace(e,now()+graceSeconds*1000L,fx);
        graces.put(e.getUniqueId(),g);e.getPersistentDataContainer().set(graceKey,PersistentDataType.BYTE,(byte)1);e.setHealth(1);e.setAI(false);e.setGravity(false);e.setInvulnerable(true);e.setVelocity(new Vector());e.setFireTicks(0);
    }
    @EventHandler public void quit(PlayerQuitEvent e){requests.remove(e.getPlayer().getUniqueId());var c=casts.get(e.getPlayer().getUniqueId());if(c!=null&&!c.pending)finish(c,false,"교화 중단");}
    @EventHandler public void unload(EntitiesUnloadEvent e){for(var entity:e.getEntities()){var g=graces.remove(entity.getUniqueId());if(g!=null){removeEffect(g.fx);entity.remove();}}}
    @EventHandler public void load(org.bukkit.event.world.EntitiesLoadEvent e){for(var entity:e.getEntities())if(entity.getPersistentDataContainer().has(graceKey)||entity.getScoreboardTags().contains("chacademia_taming_fx"))entity.remove();}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("교화")){if(sender instanceof Player p)cast(p);return true;}
        if(!sender.hasPermission("magiccodex.taming.admin"))return true;
        if(args.length==1&&args[0].equalsIgnoreCase("reload")){if(!casts.isEmpty()||!graces.isEmpty()){sender.sendMessage("교화 연출이 종료된 후 다시 불러와 주세요.");return true;}try{reload();sender.sendMessage("교화 설정을 다시 불러왔습니다.");}catch(Exception ex){sender.sendMessage("설정 오류: "+ex.getMessage());}return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("spawn")&&sender instanceof Player p){
            var f=profiles.get(args[1]);if(f==null||f.mythic.isEmpty()||mobManager==null){sender.sendMessage("등록된 MythicMobs 대상 ID를 확인해 주세요.");return true;}
            try{mobManager.getClass().getMethod("spawnMob",String.class,Location.class,double.class).invoke(mobManager,f.mythic,p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3)),1d);sender.sendMessage("예시 몹 소환: "+f.name);}catch(Exception ex){sender.sendMessage("MythicMobs에 예시 파일을 먼저 등록해 주세요.");}return true;
        }
        sender.sendMessage("/교화관리 reload | /교화관리 spawn <"+String.join("|",profiles.keySet())+">");return true;
    }
    public void close(){closed=true;if(shiny!=null)shiny.close();for(var c:List.copyOf(casts.values()))if(!c.profile.boss&&c.fx!=null)removeEffect(c.fx);casts.clear();locked.clear();for(var g:graces.values())g.entity.remove();graces.clear();for(var fx:List.copyOf(effects))removeEffect(fx);requests.clear();}
}
