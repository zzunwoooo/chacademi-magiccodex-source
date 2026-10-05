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
        plugin.getCommand("마법관리").setExecutor(this);
    }
    private void reload()throws Exception{var next=SpellRules.load(new File(plugin.getDataFolder(),"spell-runtime.yml"));rules=next;utility.reloadRecipes();}
    private static boolean enemy(Entity entity){
        return entity instanceof Monster||entity instanceof Slime||entity instanceof Ghast||entity instanceof Phantom||entity instanceof Shulker||entity instanceof EnderDragon;
    }
    Boolean cast(Player p,String id){
        var r=rules.get(id);if(r==null)return null;
        if(!Bukkit.getPluginManager().isPluginEnabled("MythicMobs"))return false;
        var mm=MythicBukkit.inst();var skill=mm.getSkillManager().getSkill(r.skill());
        if(skill.isEmpty()){p.sendMessage("마법 스킬을 불러오지 못했습니다. 관리자에게 알려 주세요.");return false;}
        Entity target=null;Location location=p.getLocation();
        if(r.target()==SpellRules.Target.AIM||r.target()==SpellRules.Target.ENTITY||r.target()==SpellRules.Target.AIR_ENTITY||r.target()==SpellRules.Target.BURNING_ENTITY){
            var trace=p.getWorld().rayTrace(p.getEyeLocation(),p.getEyeLocation().getDirection(),r.range(),FluidCollisionMode.NEVER,true,.55,e->!e.equals(p)&&enemy(e));
            target=trace==null?null:trace.getHitEntity();location=trace==null?p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(r.range())):trace.getHitPosition().toLocation(p.getWorld());
            if(r.target()!=SpellRules.Target.AIM&&target==null){p.sendMessage("시야 안의 몬스터를 지정해 주세요.");return false;}
            if(r.target()==SpellRules.Target.AIR_ENTITY&&((LivingEntity)target).isOnGround()){p.sendMessage("공중에 있는 몬스터에게 사용해 주세요.");return false;}
            if(r.target()==SpellRules.Target.BURNING_ENTITY&&target.getFireTicks()<=0){p.sendMessage("불타는 몬스터에게 사용해 주세요.");return false;}
        }
        double power=plugin.magicPower(p);
        boolean changed=false;
        if(r.utility()){
            if(!utility.apply(p,id)){p.sendMessage("마법을 적용할 대상이나 재료가 없습니다.");return false;}
            changed=true;
        }
        try{
            final Entity chosen=target;
            boolean okay=mm.getAPIHelper().castSkill(p,r.skill(),p,p.getLocation(),chosen==null?List.of():List.of(chosen),List.of(location),1f,data->{
                data.getVariables().putDouble("magic_power",power);
                data.getVariables().putDouble("damage",r.damage(power));
                data.getVariables().putDouble("magic_haste",plugin.magicHaste(p));
            });
            return changed||okay;
        }catch(RuntimeException error){
            plugin.getLogger().warning("마법 실행 실패: "+id+" / "+error.getClass().getSimpleName());
            // A committed utility must not become free because its cosmetic dispatch failed.
            return changed;
        }
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("마법관리")){
            if(!sender.hasPermission("magiccodex.mana.admin"))return false;
            try{if(args.length!=1||!args[0].equalsIgnoreCase("reload")){sender.sendMessage("/마법관리 reload");return true;}reload();sender.sendMessage("마법 실행 설정 "+rules.size()+"종을 불러왔습니다. 미씩몹 YAML 수정은 /mm reload도 실행해 주세요.");}catch(Exception e){sender.sendMessage("설정 오류: "+e.getMessage());}return true;
        }
        if(!(sender instanceof Player p))return false;
        String name=String.join("",args).replace(" ","");
        var route=rules.values().stream().filter(r->r.name().replace(" ","").equals(name)||r.id().equals(name)).findFirst().orElse(null);
        if(route==null){p.sendMessage("아직 구현되지 않았거나 이름이 다른 마법입니다.");return false;}
        return plugin.castMagic(p,route.id());
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){return rules.values().stream().map(SpellRules.Rule::name).map(s->s.replace(" ","")).filter(s->s.startsWith(String.join("",args))).toList();}
    public void close(){utility.close();rules=Map.of();}
}
