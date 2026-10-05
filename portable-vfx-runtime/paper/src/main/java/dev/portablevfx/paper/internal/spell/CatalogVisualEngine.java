package dev.portablevfx.paper.internal.spell;

import dev.portablevfx.paper.api.*;
import dev.portablevfx.protocol.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Bounded server-owned visual choreography. No damage, inventory, block or mana mutation. */
public final class CatalogVisualEngine {
    private static final int MAX_CASTS=128,MAX_PER_PLAYER=8,MAX_PENDING=256;
    private final PortableVfxService service;
    private final Map<String,Plan> plans;
    private final Map<UUID,Cast> casts=new LinkedHashMap<>();
    private long tick;
    private String lastFailure="";
    public String lastFailure(){return lastFailure;}
    private Boolean rejected(String reason){lastFailure=reason;return false;}
    public record Phase(String trigger,String effect,String anchor,int delay,int ttl,int finishAfter,
                        Vector offset,List<String> stopEffects,double width,double scaleInput,double linkLength,String role,int motionDelay,String impactEffect,String linkSource,String linkTarget,Vector targetOffset,boolean clearStoppedLocal,Vector localAim,String projectileOrigin) {}
    public record Plan(String id,double range,double speed,int maxTicks,int expireTick,String trajectory,List<Phase> phases,List<Map<?,?>> shots,int salvoStart,int salvoInterval,Vector virtualOffset,int virtualLifetime,int maxTargets,int attackInterval,double attackRange,String attackEvent) {}
    private record Pending(long at,long serial,Phase phase) {}
    private static final class Active {
        UUID handle;Phase phase;Location position;Vector direction;long finishAt,expires,motionAt;double travelled;boolean finished,following;String impactEffect;Vector arcStart,arcEnd;int arcTick,arcTicks;boolean returning;Set<UUID> hitEntities=new HashSet<>();
    }
    private static final class Cast {
        UUID id,owner,world,target,virtual;Plan plan;Location origin,aim,impact;Vector direction,normal=new Vector(0,1,0);
        long expires,serial,expireAt,nextAttack;boolean impacted,expired;Set<UUID> chainTargets=new HashSet<>();
        PriorityQueue<Pending> pending=new PriorityQueue<>(Comparator.comparingLong(Pending::at).thenComparingLong(Pending::serial));
        List<Active> active=new ArrayList<>();
    }
    public CatalogVisualEngine(PortableVfxService service,Map<String,Plan> plans){this.service=Objects.requireNonNull(service);this.plans=Map.copyOf(plans);}
    public Set<String> spellIds(){return plans.keySet();}
    public static Map<String,Plan> read(ConfigurationSection root){
        Map<String,Plan> out=new LinkedHashMap<>();
        for(String id:root.getKeys(false)){
            ConfigurationSection s=root.getConfigurationSection(id);if(s==null)throw new IllegalArgumentException("binding must be a map: "+id);
            double range=finite(s.getDouble("range",15),.1,128,"range"),speed=finite(s.getDouble("speed",1),.01,8,"speed");
            int maxTicks=s.getInt("max-ticks",VfxProtocol.MAX_DURATION_TICKS);
            if(maxTicks<1||maxTicks>VfxProtocol.MAX_DURATION_TICKS)throw new IllegalArgumentException("binding duration "+id);
            List<Phase> phases=new ArrayList<>();
            for(Map<?,?> p:s.getMapList("phases")){
                String trigger=text(p,"trigger","cast"),effect=text(p,"effect",""),anchor=text(p,"anchor","target");
                int delay=integer(p,"delay-ticks",0),ttl=integer(p,"duration-ticks",40),finish=integer(p,"finish-after-ticks",-1);
                VfxProtocol.validateId(effect);if(!effect.startsWith("claude:"))throw new IllegalArgumentException("Not a Claude effect "+effect);
                if(!Set.of("caster","target","projectile","impact","external","link").contains(anchor)||delay<0||delay>maxTicks||ttl<1||ttl>VfxProtocol.MAX_DURATION_TICKS||finish< -1||finish>ttl)throw new IllegalArgumentException("Invalid phase "+effect);
                List<String> stops=new ArrayList<>();Object raw=p.get("stop-effects");if(raw instanceof List<?> list)for(Object x:list)stops.add(x.toString());
                Vector offset=new Vector();Object vec=p.get("offset");if(vec instanceof List<?> v){if(v.size()!=3)throw new IllegalArgumentException("offset must contain3numbers");offset=new Vector(((Number)v.get(0)).doubleValue(),((Number)v.get(1)).doubleValue(),((Number)v.get(2)).doubleValue());if(!Double.isFinite(offset.lengthSquared())||offset.length()>128)throw new IllegalArgumentException("offset out of bounds");}
                double linkLength=numeric(p,"link-length",-1,-1,256);
                if(linkLength<0&&linkLength!=-1)throw new IllegalArgumentException("link-length must be -1 (unset) or nonnegative");
                phases.add(new Phase(trigger,effect,anchor,delay,ttl,finish,offset,List.copyOf(stops),numeric(p,"width",0,0,VfxProtocol.MAX_EFFECT_WIDTH),numeric(p,"scale-input",0,0,128),linkLength,text(p,"role",anchor.equals("caster")?"attached":"static"),integer(p,"motion-delay-ticks",0),text(p,"impact-effect",""),text(p,"link-source","caster"),text(p,"link-target","target"),vector(p.get("target-offset")),Boolean.TRUE.equals(p.get("clear-stopped-local")),null,text(p,"projectile-origin","caster")));
            }
            if(phases.isEmpty()||phases.size()>MAX_PENDING)throw new IllegalArgumentException("No/bloated phases "+id);
            int expireTick=-1;
            if(s.isConfigurationSection("sequence"))expireTick=(int)Math.ceil(s.getDouble("sequence.sustainStart",0)*20)+s.getInt("holdTicks",100);
            List<Map<?,?>> shots=s.getString("salvo-mode","").startsWith("external")?List.of():s.getMapList("salvo.shots");
            int salvoStart=(int)Math.ceil(s.getDouble("salvo.fireStart.default",1)*20),salvoInterval=(int)Math.ceil(s.getDouble("salvo.interval.default",.2)*20);
            out.put(id,new Plan(id,range,speed,maxTicks,expireTick,s.getString("trajectory","linear"),List.copyOf(phases),List.copyOf(shots),salvoStart,salvoInterval,s.isConfigurationSection("virtual-anchor")?vector(s.getList("virtual-anchor.offset")):null,s.getInt("virtual-anchor.lifetime-ticks",600),s.getInt("max-targets",s.getInt("target-limit",3)),s.getInt("virtual-anchor.attack-interval-ticks",40),s.getDouble("virtual-anchor.attack-range",15),s.getString("virtual-anchor.attack-event","")));
        }
        return out;
    }
    /** null means this catalogue does not own the ID; false means owned but unavailable. */
    public Boolean cast(Player player,String id){return castEvent(player,id,"cast");}
    /** Operator preview only; normal gameplay must use cast() and the mana owner. */
    public Boolean previewEvent(Player player,String id,String event){return castEvent(player,id,event);}
    private Boolean castEvent(Player player,String id,String initialEvent){
        lastFailure="";
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Catalog casts require the server thread");
        Plan plan=plans.get(id);if(plan==null){lastFailure="no visual plan";return null;}
        if(!service.supportsCatalogCast(player.getUniqueId()))return rejected("readiness withdrawn");
        if(player.isDead()||!player.isOnline())return rejected("player dead/offline");
        if(casts.size()>=MAX_CASTS||casts.values().stream().filter(c->c.owner.equals(player.getUniqueId())).count()>=MAX_PER_PLAYER)return rejected("active cast capacity");
        Cast c=new Cast();c.id=UUID.randomUUID();c.owner=player.getUniqueId();c.world=player.getWorld().getUID();c.plan=plan;c.origin=player.getLocation();c.direction=player.getEyeLocation().getDirection().normalize();
        double safeRange=safeRange(player.getEyeLocation(),c.direction,plan.range());if(safeRange<.1)return rejected("safe aim range below0.1");
        RayTraceResult hit=player.getWorld().rayTrace(player.getEyeLocation(),c.direction,safeRange,FluidCollisionMode.NEVER,true,.3,e->e instanceof LivingEntity&&!e.equals(player));
        c.aim=hit==null?player.getEyeLocation().add(c.direction.clone().multiply(safeRange)):hit.getHitPosition().toLocation(player.getWorld());
        if(plan.trajectory().equals("targeted-meteor")) {
            // The aim ray selects X/Z; this spell always fixes a ground point, even when looking at air or a wall.
            Location ground=TargetedMeteorPath.groundBelow(c.aim);
            if(ground==null)return rejected("no loaded ground below meteor target");
            c.aim=ground;c.normal=new Vector(0,1,0);
        } else if(hit!=null&&hit.getHitEntity()!=null)c.target=hit.getHitEntity().getUniqueId();
        if(c.target==null&&plan.phases().stream().anyMatch(ph->ph.trigger().equals(initialEvent)&&ph.anchor().equals("target")&&ph.role().equals("attached")))return rejected("requires living target");
        if(!plan.trajectory().equals("targeted-meteor")&&hit!=null&&hit.getHitBlockFace()!=null)c.normal=hit.getHitBlockFace().getDirection();
        if(!loaded(c.aim))return rejected("aim chunk not loaded");c.expires=tick+plan.maxTicks();c.expireAt=plan.expireTick()<0?-1:tick+plan.expireTick();
        enqueue(c,initialEvent);if(initialEvent.equals("cast"))enqueueSalvo(c);if(c.pending.isEmpty())return rejected("no phases for event "+initialEvent);
        if(plan.virtualOffset()!=null){
            Location spawn=player.getLocation().add(localOffset(plan.virtualOffset(),c.direction));if(!loaded(spawn))return rejected("virtual anchor chunk not loaded");
            ArmorStand anchor=player.getWorld().spawn(spawn,ArmorStand.class,e->{e.setVisible(false);e.setMarker(true);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setPersistent(false);e.setCollidable(false);});
            c.virtual=anchor.getUniqueId();c.expireAt=tick+plan.virtualLifetime();c.nextAttack=tick+Math.max(20,plan.attackInterval());
        }

        casts.put(c.id,c);
        try{runPending(c);return true;}catch(RuntimeException failure){cancel(c.id);return rejected("phase exception "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
    }
    public void tick(){
        tick++;
        for(Cast c:List.copyOf(casts.values())){
            Player p=Bukkit.getPlayer(c.owner);
            if(p==null||!p.isOnline()||p.isDead()||!p.getWorld().getUID().equals(c.world)){cancel(c.id);continue;}
            try{
                if(tick>=c.expires){cancel(c.id);continue;}
                if(c.virtual!=null){Entity v=Bukkit.getEntity(c.virtual);if(v==null){cancel(c.id);continue;}Location desired=p.getLocation().add(localOffset(c.plan.virtualOffset(),p.getLocation().getDirection()));Location current=v.getLocation();Vector change=desired.toVector().subtract(current.toVector()).multiply(.25);current.add(change);current.setYaw(p.getLocation().getYaw());current.setPitch(0);if(loaded(current))v.teleport(current);}
                if(c.virtual!=null&&!c.expired&&!c.plan.attackEvent().isEmpty()&&tick>=c.nextAttack){
                    c.nextAttack=tick+Math.max(20,c.plan.attackInterval());Entity anchor=Bukkit.getEntity(c.virtual);
                    if(anchor!=null){double range=Math.min(64,Math.max(1,c.plan.attackRange()));Entity target=anchor.getNearbyEntities(range,range,range).stream().filter(e->e instanceof Monster&&!e.isDead()).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(anchor.getLocation()))).orElse(null);
                        if(target!=null){c.target=target.getUniqueId();c.aim=((LivingEntity)target).getEyeLocation();Location facing=anchor.getLocation();facing.setDirection(c.aim.toVector().subtract(facing.toVector()));facing.setPitch(0);anchor.teleport(facing);enqueue(c,c.plan.attackEvent());}
                    }
                }
                if(!c.expired&&c.expireAt>=0&&tick>=c.expireAt){c.expired=true;enqueue(c,"expire");}
                runPending(c);
                for(Active a:List.copyOf(c.active)){
                    if(tick>=a.expires){c.active.remove(a);continue;}
                    if(!a.finished&&a.finishAt>=0&&tick>=a.finishAt){service.finishCast(a.handle);a.finished=true;}
                    if(!a.finished&&a.phase.anchor().equals("projectile")&&tick>=a.motionAt)moveProjectile(c,a);
                    else if(!a.finished&&a.phase.role().equals("link"))updateLink(c,a);
                }
                if(c.pending.isEmpty()&&c.active.isEmpty())cancel(c.id);
            }catch(RuntimeException failure){cancel(c.id);}
        }
    }
    private void enqueue(Cast c,String trigger){
        for(Phase phase:c.plan.phases())if(phase.trigger().equals(trigger)){
            if(c.pending.size()>=MAX_PENDING)throw new IllegalStateException("Too many queued visual phases");
            c.pending.add(new Pending(tick+phase.delay(),c.serial++,phase));
        }
    }
    private void enqueueSalvo(Cast c){
        if(c.plan.shots().isEmpty())return;
        for(Map<?,?> shot:c.plan.shots()){
            int index=integer(shot,"index",0);
            String system=text(shot,"projectileSystem","").toLowerCase(Locale.ROOT),impact=text(shot,"impactSystem","").toLowerCase(Locale.ROOT);
            List<Phase> projectiles=c.plan.phases().stream().filter(p->p.trigger().equals("external:shot")&&(system.isEmpty()||p.effect().endsWith("/"+system))).toList();
            if(projectiles.size()!=1)throw new IllegalArgumentException("Ambiguous salvo projectile");
            Phase p=projectiles.get(0);String prefix=p.effect().substring(0,p.effect().lastIndexOf('/')+1);
            if(impact.isEmpty()){var impacts=c.plan.phases().stream().filter(x->x.trigger().equals("impact")).toList();if(impacts.size()==1)impact=impacts.get(0).effect().substring(prefix.length());}
            Vector offset=new Vector();Object raw=shot.get("slot");if(raw instanceof List<?> v&&v.size()==3)offset=new Vector(((Number)v.get(0)).doubleValue(),((Number)v.get(1)).doubleValue(),((Number)v.get(2)).doubleValue());
            List<String> stops=new ArrayList<>();raw=shot.get("stopSystems");if(raw instanceof List<?> list)for(Object stop:list)stops.add(prefix+stop.toString().toLowerCase(Locale.ROOT));
            Vector localAim=new Vector(0,0,1);Object aimRaw=shot.get("aim");if(aimRaw instanceof List<?> av&&av.size()==2){double pitch=((Number)av.get(0)).doubleValue(),yaw=((Number)av.get(1)).doubleValue();localAim=new Vector(Math.cos(pitch)*Math.sin(yaw),-Math.sin(pitch),Math.cos(pitch)*Math.cos(yaw));}
            Phase firing=new Phase("cast",p.effect(),"projectile",0,p.ttl(),p.finishAfter(),offset,stops,p.width(),p.scaleInput(),p.linkLength(),p.role(),p.motionDelay(),impact.isEmpty()?"":prefix+impact,p.linkSource(),p.linkTarget(),p.targetOffset(),true,localAim,p.projectileOrigin());
            c.pending.add(new Pending(tick+c.plan.salvoStart()+(long)index*c.plan.salvoInterval(),c.serial++,firing));
        }
    }
    private void runPending(Cast c){while(!c.pending.isEmpty()&&c.pending.peek().at()<=tick)start(c,c.pending.remove().phase());}
    private void start(Cast c,Phase phase){
        for(Active old:c.active)if(!old.finished&&phase.stopEffects().contains(old.phase.effect())){service.finishCast(old.handle,phase.clearStoppedLocal());old.finished=true;}
        Player player=Bukkit.getPlayer(c.owner);if(player==null)throw new IllegalStateException("Owner left");
        Entity target=c.target==null?null:Bukkit.getEntity(c.target);
        Entity virtual=c.virtual==null?null:Bukkit.getEntity(c.virtual);
        boolean linked=phase.role().equals("link");
        Location at=switch(phase.anchor()){
            case "caster","link"->player.getLocation();case "external"->virtual!=null?virtual.getLocation():c.aim;case "projectile"->c.plan.trajectory().equals("ground")||phase.offset().lengthSquared()>0?player.getLocation():player.getEyeLocation();case "impact"->c.impact==null?c.aim:c.impact;default->target!=null&&phase.role().equals("attached")?target.getLocation():c.aim;
        };
        if(phase.anchor().equals("projectile")&&phase.projectileOrigin().equals("virtual")&&virtual!=null)at=virtual.getLocation();
        if(phase.anchor().equals("projectile")&&c.plan.trajectory().equals("chain-distinct-targets")&&c.impact!=null)at=c.impact;
        if(linked)at=phase.linkSource().equals("target")?c.aim:player.getLocation();
        Vector frameDirection=phase.projectileOrigin().equals("virtual")&&virtual!=null?virtual.getLocation().getDirection():phase.localAim()!=null?player.getLocation().getDirection():c.direction;
        if(phase.anchor().equals("projectile")&&c.plan.trajectory().equals("targeted-meteor"))at=c.aim;
        at=at.clone().add(localOffset(phase.offset(),frameDirection));if(!loaded(at))throw new IllegalStateException("Unloaded phase position");
        Vector dir=phase.projectileOrigin().equals("virtual")?c.aim.toVector().subtract(at.toVector()).normalize():phase.localAim()!=null?localOffset(phase.localAim(),frameDirection).normalize():c.direction.clone();if(c.plan.trajectory().equals("ground")){dir.setY(0);if(dir.lengthSquared()<1e-8)dir=new Vector(0,0,1);dir.normalize();}double link=phase.linkLength();
        if(phase.anchor().equals("projectile")&&c.plan.trajectory().equals("targeted-meteor"))dir=TargetedMeteorPath.direction(at,c.aim);
        if(linked){Location endpoint=phase.linkTarget().equals("caster")?player.getLocation():target==null?c.aim:target.getLocation();endpoint=endpoint.clone().add(localOffset(phase.targetOffset(),c.direction));Vector delta=endpoint.toVector().subtract(at.toVector());link=delta.length();dir=link<1e-5?new Vector(0,0,1):delta.normalize();}
        if(phase.role().equals("static")){dir.setY(0);if(dir.lengthSquared()<1e-8)dir=new Vector(0,0,1);dir.normalize();}
        EffectBasis basis=phase.anchor().equals("impact")||phase.role().equals("impact")?EffectBasis.impact(c.normal.getX(),c.normal.getY(),c.normal.getZ(),dir.getX(),dir.getY(),dir.getZ()):EffectBasis.projectile(dir.getX(),dir.getY(),dir.getZ());
        EffectRequest request=new EffectRequest(phase.effect(),at.getWorld().getKey().toString(),at.getX(),at.getY(),at.getZ(),1,phase.ttl(),0,0,0,0xffffff,1,64);
        PlayResult result;
        boolean follow=phase.role().equals("attached")&&(phase.anchor().equals("caster")||phase.anchor().equals("target")&&target!=null||phase.anchor().equals("external")&&virtual!=null)&&phase.offset().lengthSquared()==0;
        if(follow)result=service.startFollowCast(request,phase.anchor().equals("target")?target.getUniqueId():phase.anchor().equals("external")?virtual.getUniqueId():c.owner,c.id.getLeastSignificantBits()+c.serial++,phase.width(),phase.scaleInput());
        else result=service.startCast(request,basis,c.id.getLeastSignificantBits()+c.serial++,phase.width(),phase.scaleInput(),link);
        if(result.recipients()==0)throw new IllegalStateException("No compatible recipients");
        Active a=new Active();a.handle=result.handle();a.phase=phase;a.position=at;a.direction=dir;a.following=follow;a.finishAt=phase.anchor().equals("projectile")||phase.finishAfter()<0?-1:tick+phase.finishAfter();a.expires=tick+phase.ttl();a.motionAt=tick+phase.motionDelay();a.impactEffect=phase.impactEffect();
        if(phase.anchor().equals("projectile")&&c.plan.trajectory().equals("chain-distinct-targets")){
            a.arcStart=at.toVector();a.arcEnd=(target instanceof LivingEntity living?living.getEyeLocation():c.aim).toVector();a.arcTicks=Math.max(2,(int)Math.ceil(a.arcStart.distance(a.arcEnd)/c.plan.speed()));
        }
        c.active.add(a);
    }
    private void moveProjectile(Cast c,Active a){
        String trajectory=c.plan.trajectory();if(trajectory.equals("targeted-meteor")){moveMeteor(c,a);return;}if(trajectory.equals("chain-distinct-targets")){moveChain(c,a);return;}Player owner=Bukkit.getPlayer(c.owner);
        if(trajectory.equals("out-and-back")&&a.returning&&owner!=null){
            Vector home=owner.getEyeLocation().toVector().subtract(a.position.toVector());
            if(home.length()<=c.plan.speed()){service.finishCast(a.handle,true);a.finished=true;c.impact=owner.getEyeLocation();enqueue(c,"external:return_to_caster");runPending(c);return;}
            a.direction=home.normalize();
        }
        double step=a.returning?c.plan.speed():Math.min(c.plan.speed(),Math.max(0,c.plan.range()-a.travelled));
        if(step<=0){
            if(trajectory.equals("out-and-back")){a.returning=true;return;}
            service.finishCast(a.handle,true);a.finished=true;c.impact=a.position;c.normal=a.direction.clone().multiply(-1);
            if(c.plan.phases().stream().anyMatch(p->p.trigger().equals("expire")))enqueue(c,"expire");else enqueueImpact(c,a);runPending(c);return;
        }
        Location next=a.position.clone().add(a.direction.clone().multiply(step));if(!loadedSegment(a.position,next)){service.finishCast(a.handle,true);a.finished=true;return;}
        RayTraceResult hit=a.position.getWorld().rayTrace(a.position,a.direction,step,FluidCollisionMode.NEVER,true,.25,e->e instanceof LivingEntity&&!e.getUniqueId().equals(c.owner)&&!e.getUniqueId().equals(c.virtual)&&!a.hitEntities.contains(e.getUniqueId()));
        if(hit!=null){
            Entity entity=hit.getHitEntity();Vector normal=hit.getHitBlockFace()==null?a.direction.clone().multiply(-1):hit.getHitBlockFace().getDirection();
            Location point=hit.getHitPosition().toLocation(a.position.getWorld());
            if(entity!=null&&(trajectory.equals("piercing")||trajectory.equals("ground")||trajectory.equals("out-and-back"))){
                a.hitEntities.add(entity.getUniqueId());c.impact=point;c.normal=normal;
                enqueue(c,trajectory.equals("piercing")?"external:pierce_enemy":trajectory.equals("ground")?"external:wave_touches_enemy":"external:contact");runPending(c);
                if(trajectory.equals("piercing")&&a.hitEntities.size()>=c.plan.maxTargets()){service.finishCast(a.handle,true);a.finished=true;enqueue(c,"expire");runPending(c);return;}
            }else if(trajectory.equals("out-and-back")){a.returning=true;a.hitEntities.clear();return;}
            else if(trajectory.equals("piercing")){service.finishCast(a.handle,true);a.finished=true;c.impact=point;c.normal=normal;enqueue(c,"external:block_contact");enqueue(c,"expire");runPending(c);return;}
            else{impact(c,a,point,normal);return;}
        }
        a.position=next;a.travelled+=step;service.updateCast(a.handle,next.getX(),next.getY(),next.getZ(),EffectBasis.projectile(a.direction.getX(),a.direction.getY(),a.direction.getZ()));
    }
    private void moveMeteor(Cast c,Active a){
        // Targeting range is not flight distance: the authored sky offset is over 30 blocks long.
        Vector delta=c.aim.toVector().subtract(a.position.toVector());double remaining=delta.length();
        if(remaining<1e-6){impact(c,a,c.aim,new Vector(0,1,0));return;}
        a.direction=delta.clone().normalize();double step=Math.min(c.plan.speed(),remaining);
        Location next=a.position.clone().add(a.direction.clone().multiply(step));
        if(!loadedSegment(a.position,next)){service.finishCast(a.handle,true);a.finished=true;return;}
        RayTraceResult hit=a.position.getWorld().rayTraceBlocks(a.position,a.direction,step,FluidCollisionMode.NEVER,true);
        if(hit!=null){impact(c,a,hit.getHitPosition().toLocation(a.position.getWorld()),hit.getHitBlockFace()==null?new Vector(0,1,0):hit.getHitBlockFace().getDirection());return;}
        if(step>=remaining-1e-6){impact(c,a,c.aim,new Vector(0,1,0));return;}
        a.position=next;a.travelled+=step;
        service.updateCast(a.handle,next.getX(),next.getY(),next.getZ(),EffectBasis.projectile(a.direction.getX(),a.direction.getY(),a.direction.getZ()));
    }
    private void moveChain(Cast c,Active a){
        double t=Math.min(1,(double)++a.arcTick/a.arcTicks);
        Vector nextPoint=a.arcStart.clone().multiply(1-t).add(a.arcEnd.clone().multiply(t)).add(new Vector(0,Math.sin(Math.PI*t),0));
        Location next=nextPoint.toLocation(a.position.getWorld());if(!loadedSegment(a.position,next)){finishChain(c,a,a.position,null);return;}
        Vector delta=next.toVector().subtract(a.position.toVector());double distance=delta.length();
        RayTraceResult hit=distance<1e-6?null:a.position.getWorld().rayTrace(a.position,delta.clone().normalize(),distance,FluidCollisionMode.NEVER,true,.3,e->e instanceof LivingEntity&&!e.getUniqueId().equals(c.owner)&&!e.getUniqueId().equals(c.virtual)&&!c.chainTargets.contains(e.getUniqueId()));
        if(hit!=null){finishChain(c,a,hit.getHitPosition().toLocation(a.position.getWorld()),hit.getHitEntity());return;}
        a.position=next;if(distance>1e-6)a.direction=delta.normalize();
        service.updateCast(a.handle,next.getX(),next.getY(),next.getZ(),EffectBasis.projectile(a.direction.getX(),a.direction.getY(),a.direction.getZ()));
        if(t>=1){Entity target=c.target==null?null:Bukkit.getEntity(c.target);finishChain(c,a,next,target!=null&&target.getLocation().distanceSquared(next)<4?target:null);}
    }
    private void finishChain(Cast c,Active a,Location at,Entity hit){
        service.finishCast(a.handle,true);a.finished=true;c.impact=at;c.normal=a.direction.clone().multiply(-1);
        if(hit!=null)c.chainTargets.add(hit.getUniqueId());
        Entity next=null;
        if(hit!=null&&c.chainTargets.size()<c.plan.maxTargets())next=at.getWorld().getNearbyEntities(at,c.plan.range(),c.plan.range(),c.plan.range()).stream().filter(e->e instanceof Monster&&!e.isDead()&&!c.chainTargets.contains(e.getUniqueId())&&e.getLocation().distanceSquared(at)<=c.plan.range()*c.plan.range()).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at))).orElse(null);
        if(next!=null){c.target=next.getUniqueId();enqueue(c,"external:nonfinal_hit");}
        else enqueue(c,"external:final_hit");
        runPending(c);
    }
    private void impact(Cast c,Active a,Location at,Vector normal){
        // Impact termination must kill local projectile particles while world-space trails retain their tails.
        service.finishCast(a.handle,true);a.finished=true;c.impact=at;c.normal=normal;
        enqueueImpact(c,a);runPending(c);
    }
    private void enqueueImpact(Cast c,Active a){
        if(a.impactEffect.isEmpty())enqueue(c,"impact");
        else for(Phase p:c.plan.phases())if(p.trigger().equals("impact")&&p.effect().equals(a.impactEffect))c.pending.add(new Pending(tick+p.delay(),c.serial++,p));
    }
    private void updateLink(Cast c,Active a){
        Player p=Bukkit.getPlayer(c.owner);Entity target=c.target==null?null:Bukkit.getEntity(c.target);if(p==null)return;
        Location at=(a.phase.linkSource().equals("target")?c.aim.clone():p.getLocation()).add(localOffset(a.phase.offset(),p.getLocation().getDirection()));Location end=(a.phase.linkTarget().equals("caster")?p.getLocation():target==null?c.aim.clone():target.getLocation()).add(localOffset(a.phase.targetOffset(),p.getLocation().getDirection()));
        if(!at.getWorld().equals(end.getWorld())){service.finishCast(a.handle);a.finished=true;return;}
        Vector d=end.toVector().subtract(at.toVector());double len=d.length();if(len<1e-5)d=new Vector(0,0,1);else d.normalize();
        service.updateCast(a.handle,at.getX(),at.getY(),at.getZ(),EffectBasis.projectile(d.getX(),d.getY(),d.getZ()),len);
    }
    /** External server events may activate named branches; clients cannot supply this input. */
    public boolean trigger(UUID castId,String event){Cast c=casts.get(castId);if(c==null||c.plan.phases().stream().noneMatch(p->p.trigger().equals(event)))return false;if(event.equals("external:trigger")){if(c.expired)return false;c.expired=true;}enqueue(c,event);runPending(c);return true;}
    public int trigger(UUID owner,String spellId,String event,Location point,UUID target){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Visual events require server thread");
        int delivered=0;
        for(Cast c:List.copyOf(casts.values()))if(c.owner.equals(owner)&&c.plan.id().equals(spellId)){
            if(point!=null){if(!point.getWorld().getUID().equals(c.world)||!loaded(point)||point.distanceSquared(c.origin)>256*256)continue;c.impact=point.clone();}
            if(target!=null){Entity e=Bukkit.getEntity(target);if(e==null||!e.getWorld().getUID().equals(c.world))continue;c.target=target;}
            if(trigger(c.id,event))delivered++;
        }
        return delivered;
    }
    public void removePlayer(UUID player){for(Cast c:List.copyOf(casts.values()))if(c.owner.equals(player))cancel(c.id);}
    public void removeWorld(UUID world){for(Cast c:List.copyOf(casts.values()))if(c.world.equals(world))cancel(c.id);}
    public void clear(){for(UUID id:List.copyOf(casts.keySet()))cancel(id);}
    private void cancel(UUID id){Cast c=casts.remove(id);if(c!=null){for(Active a:c.active)service.stop(a.handle);if(c.virtual!=null){Entity e=Bukkit.getEntity(c.virtual);if(e!=null)e.remove();}}}
    private static double safeRange(Location from,Vector direction,double requested){double last=0;for(double d=0;d<=requested;d+=.5){if(!loaded(from.clone().add(direction.clone().multiply(d))))break;last=d;}return last;}
    private static boolean loadedSegment(Location from,Location to){Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();return length<1e-8||safeRange(from,delta.normalize(),length)+.5>=length&&loaded(to);}
    private static boolean loaded(Location at){return at.getWorld()!=null&&at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private static Vector localOffset(Vector v,Vector dir){Vector horizontal=dir.clone().setY(0);if(horizontal.lengthSquared()<1e-8)horizontal=new Vector(0,0,1);horizontal.normalize();Vector right=new Vector(horizontal.getZ(),0,-horizontal.getX());return right.multiply(v.getX()).add(new Vector(0,v.getY(),0)).add(horizontal.multiply(v.getZ()));}
    private static Vector vector(Object raw){if(raw==null)return new Vector();if(!(raw instanceof List<?> v)||v.size()!=3)throw new IllegalArgumentException("Expected 3D vector");Vector out=new Vector(((Number)v.get(0)).doubleValue(),((Number)v.get(1)).doubleValue(),((Number)v.get(2)).doubleValue());if(!Double.isFinite(out.lengthSquared())||out.length()>128)throw new IllegalArgumentException("Invalid vector");return out;}
    private static String text(Map<?,?> p,String k,String d){Object v=p.get(k);return v==null?d:v.toString();}
    private static int integer(Map<?,?> p,String k,int d){Object v=p.get(k);if(v==null)return d;if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue()))throw new IllegalArgumentException(k);return n.intValue();}
    private static double numeric(Map<?,?> p,String k,double d,double min,double max){Object v=p.get(k);return finite(v==null?d:((Number)v).doubleValue(),min,max,k);}
    private static double finite(double v,double min,double max,String name){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(name);return v;}
}
