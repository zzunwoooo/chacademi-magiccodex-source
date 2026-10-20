package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import io.lumine.mythic.bukkit.MythicBukkit;

/** MythicMobs owns combat/effects. This bridge only authenticates routes, captures stats and performs inventory/block utilities. */
final class SpellRuntime implements AutoCloseable {

    private final MagicCodexBridge plugin;
    private final SpellUtility utility;
    private Map<String,SpellRules.Rule> rules;
    SpellRuntime(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;utility=new SpellUtility(plugin);
        if(!new File(plugin.getDataFolder(),"spell-runtime.yml").exists())plugin.saveResource("spell-runtime.yml",false);
        reload();
        ManaBridge.runtime=new ManaBridge.Runtime(this::reload,this::routeCount,this);
    }
    int routeCount(){return rules.size();}
    /** spell-runtime.yml 을 다시 읽는다 (검증에 실패하면 예외, 기존 경로 유지). MythicMobs YAML 은 /mm reload 가 따로 읽는다. */
    void reload()throws Exception{var next=SpellRules.load(new File(plugin.getDataFolder(),"spell-runtime.yml"));rules=next;utility.reloadRecipes();}
    private static boolean enemy(Entity entity){
        return entity instanceof Monster||entity instanceof Slime||entity instanceof Ghast||entity instanceof Phantom||entity instanceof Shulker||entity instanceof EnderDragon;
    }
    /** Validated cast: route + chosen targets. ok=false means refuse (message already sent). */
    record Plan(boolean ok,SpellRules.Rule rule,Entity target,Location location){}
    private static final Plan REFUSED=new Plan(false,null,null,null);
    /** null: not a runtime spell. Never changes the world. */
    Plan prepare(Player p,String id){
        var r=rules.get(id);if(r==null)return null;
        if(!Bukkit.getPluginManager().isPluginEnabled("MythicMobs"))return REFUSED;
        var skill=MythicBukkit.inst().getSkillManager().getSkill(r.skill());
        if(skill.isEmpty()){plugin.getLogger().warning("마법 실행 경로 "+id+"의 MythicMobs 스킬 "+r.skill()+"을 찾지 못했습니다 (/mm reload 확인).");CodexCommands.warn(p,"magic-fail","지금은 이 마법을 쓸 수 없어요");return REFUSED;}
        Entity target=null;Location location=p.getLocation();
        if(r.target()==SpellRules.Target.AIM||r.target()==SpellRules.Target.ENTITY||r.target()==SpellRules.Target.AIR_ENTITY||r.target()==SpellRules.Target.BURNING_ENTITY){
            var trace=p.getWorld().rayTrace(p.getEyeLocation(),p.getEyeLocation().getDirection(),r.range(),FluidCollisionMode.NEVER,true,.55,e->!e.equals(p)&&MagicProtectionLink.target(p,e));
            target=trace==null?null:trace.getHitEntity();location=trace==null?p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(r.range())):trace.getHitPosition().toLocation(p.getWorld());
            // Nothing under the aim: a listener (큐디치 공중 조준) may move the point along the same ray to a chosen distance.
            if(trace==null&&r.target()==SpellRules.Target.AIM){
                var aim=new MagicAimPointEvent(p,id,r.range(),location.clone());Bukkit.getPluginManager().callEvent(aim);
                var chosen=aim.getLocation();
                if(chosen!=null&&chosen.getWorld()==p.getWorld()&&chosen.distanceSquared(p.getEyeLocation())<=(r.range()+.5)*(r.range()+.5))location=chosen;
            }
            // ModelEngine mobs (owner 2026-10-19: "타겟 마법이 모델 쓰는 몹한테 안 써짐"): the vanilla ray only knows the small
            // base box, and a hit on the model's hitbox Interaction/Display is not the mob. Same rule as the taming/target-HUD
            // ray: sub-hitboxes and hitbox entities resolve to the modeled base mob.
            if(r.target()!=SpellRules.Target.AIM&&!(target instanceof LivingEntity)){
                var tm=plugin.tamingBridge();LivingEntity modeled=null;
                if(tm!=null)try{modeled=tm.target(p,r.range());}catch(RuntimeException|LinkageError ignored){}
                if(modeled!=null&&MagicProtectionLink.target(p,modeled)){target=modeled;location=modeled.getLocation().add(0,modeled.getHeight()*.5,0);}
                else target=null;
            }
            if(r.target()!=SpellRules.Target.AIM&&target==null){CodexCommands.warn(p,"magic-target","시야 안의 몬스터를 바라봐 주세요");return REFUSED;}
            if(r.target()==SpellRules.Target.AIR_ENTITY&&((LivingEntity)target).isOnGround()){CodexCommands.warn(p,"magic-target","공중에 있는 몬스터에게 써 주세요");return REFUSED;}
            if(r.target()==SpellRules.Target.BURNING_ENTITY&&!(target instanceof LivingEntity burning&&MagicProtectionLink.status(burning,"burn"))){CodexCommands.warn(p,"magic-target","화상 상태인 몬스터에게 써 주세요");return REFUSED;}
        }
        if(!MagicProtectionLink.at(p,location))return REFUSED;
        return new Plan(true,r,target,location);
    }
    boolean execute(Player p,Plan plan){
        if(!MagicProtectionLink.at(p,plan.location())||(plan.target()!=null&&!MagicProtectionLink.target(p,plan.target())))return false;
        var r=plan.rule();var mm=MythicBukkit.inst();
        double power=plugin.magicPower(p);
        boolean changed=false;
        if(r.utility()){
            if(!utility.apply(p,r.id())){CodexCommands.warn(p,"magic-target","마법을 쓸 대상이나 재료가 없어요");return false;}
            changed=true;
        }
        try{
            final Entity chosen=plan.target();
            boolean okay=mm.getAPIHelper().castSkill(p,r.skill(),p,p.getLocation(),chosen==null?List.of():List.of(chosen),List.of(plan.location()),1f,data->{
                data.getVariables().putDouble("magic_power",power);
                data.getVariables().putDouble("damage",r.damage(power));
                data.getVariables().putDouble("magic_haste",plugin.magicHaste(p));
            });
            return changed||okay;
        }catch(RuntimeException error){
            plugin.getLogger().warning("마법 실행 실패: "+r.id()+" / "+error.getClass().getSimpleName());
            // A committed utility must not become free because its cosmetic dispatch failed.
            return changed;
        }
    }
    /** Kept for callers outside the catalogue path. */
    Boolean cast(Player p,String id){
        Plan plan=prepare(p,id);
        if(plan==null)return null;
        return plan.ok()&&execute(p,plan);
    }
    public void close(){var r=ManaBridge.runtime;if(r!=null&&r.owner()==this)ManaBridge.runtime=null;utility.close();rules=Map.of();}
}
