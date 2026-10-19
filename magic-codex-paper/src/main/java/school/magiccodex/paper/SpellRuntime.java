package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import io.lumine.mythic.bukkit.MythicBukkit;

/** MythicMobs owns combat/effects. This bridge only authenticates routes, captures stats and performs inventory/block utilities. */
final class SpellRuntime implements CommandExecutor,TabCompleter,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final SpellUtility utility;
    private Map<String,SpellRules.Rule> rules;
    SpellRuntime(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;utility=new SpellUtility(plugin);
        if(!new File(plugin.getDataFolder(),"spell-runtime.yml").exists())plugin.saveResource("spell-runtime.yml",false);
        reload();
        plugin.getCommand("마법").setExecutor(this);plugin.getCommand("마법").setTabCompleter(this);
        plugin.getCommand("마법관리").setExecutor(this);plugin.getCommand("마법관리").setTabCompleter(this);
    }
    int routeCount(){return rules.size();}
    private void reload()throws Exception{var next=SpellRules.load(new File(plugin.getDataFolder(),"spell-runtime.yml"));rules=next;utility.reloadRecipes();}
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
        if(skill.isEmpty()){p.sendMessage("마법 스킬을 불러오지 못했습니다. 관리자에게 알려 주세요.");return REFUSED;}
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
            if(r.target()!=SpellRules.Target.AIM&&target==null){p.sendMessage("시야 안의 몬스터를 지정해 주세요.");return REFUSED;}
            if(r.target()==SpellRules.Target.AIR_ENTITY&&((LivingEntity)target).isOnGround()){p.sendMessage("공중에 있는 몬스터에게 사용해 주세요.");return REFUSED;}
            if(r.target()==SpellRules.Target.BURNING_ENTITY&&!(target instanceof LivingEntity burning&&MagicProtectionLink.status(burning,"burn"))){p.sendMessage("화상 상태인 몬스터에게 사용해 주세요.");return REFUSED;}
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
            if(!utility.apply(p,r.id())){p.sendMessage("마법을 적용할 대상이나 재료가 없습니다.");return false;}
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
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("마법관리")){
            if(!sender.hasPermission("magiccodex.mana.admin"))return false;
            try{if(args.length!=1||!Set.of("새로고침","reload").contains(args[0])){sender.sendMessage("/마법관리 새로고침");return true;}reload();sender.sendMessage("마법 실행 설정 "+rules.size()+"종을 불러왔습니다. 미씩몹 YAML 수정은 /mm reload도 실행해 주세요.");}catch(Exception e){sender.sendMessage("설정 오류: "+e.getMessage());}return true;
        }
        if(!(sender instanceof Player p))return false;
        String name=String.join("",args).replace(" ","");
        var route=rules.values().stream().filter(r->r.name().replace(" ","").equals(name)||r.id().equals(name)).findFirst().orElse(null);
        if(route==null){p.sendMessage("아직 구현되지 않았거나 이름이 다른 마법입니다.");return false;}
        return plugin.castMagic(p,route.id());
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){if(command.getName().equals("마법관리"))return sender.hasPermission("magiccodex.mana.admin")&&args.length==1?AdminCommandRules.filter(List.of("새로고침"),args[0]):List.of();return rules.values().stream().map(SpellRules.Rule::name).map(s->s.replace(" ","")).filter(s->s.startsWith(String.join("",args))).toList();}
    public void close(){utility.close();rules=Map.of();}
}
