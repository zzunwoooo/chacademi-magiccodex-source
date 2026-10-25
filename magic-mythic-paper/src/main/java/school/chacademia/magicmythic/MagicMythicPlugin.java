package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import java.util.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MythicMobs glue for Chacademi spells. MagicCodexBridge keeps mana, cooldown, permission and target choice;
 * this plugin only supplies the gameplay mechanics (damage, knockback, zones, guards, small world utilities)
 * that the CHA_* skills call. Visuals stay in PortableVFX.
 */
public final class MagicMythicPlugin extends JavaPlugin implements Listener {
    private Targets targets;
    private final AllyResolver allies=new AllyResolver();
    private final MagicProtection protection=new MagicProtection();
    private boolean protectionReady;
    @Override public void onLoad(){try{protection.register();protectionReady=true;}catch(RuntimeException|LinkageError e){getLogger().severe("Magic protection unavailable; all magic denied: "+e.getClass().getSimpleName());}}
    public boolean canCast(org.bukkit.entity.Player player){return isEnabled()&&protectionReady&&protection.at(player.getLocation());}
    public boolean canAffect(org.bukkit.entity.Player player,org.bukkit.Location at){return isEnabled()&&protectionReady&&protection.pair(player,at);}
    public boolean canTarget(org.bukkit.entity.Player player,org.bukkit.entity.Entity target){return targets!=null&&targets.enemy(player,target);}
    public boolean canTouch(org.bukkit.entity.Player player,org.bukkit.entity.Entity target){return targets!=null&&!targets.excluded(target)&&protection.pair(player,target.getLocation());}
    MagicProtection protection(){return protection;}

    // ---- status API (MagicCodexBridge calls this by reflection; main thread)
    /** Status effect id (stun, burn, ...) active on the entity. "burn" also counts vanilla fire (불타는 대상 마법). */
    /** Artifact API (제압의 머리띠): the entity has a movement/action crowd control status right now. */
    public boolean isControlled(org.bukkit.entity.LivingEntity entity){return status!=null&&entity!=null&&status.controlled(entity);}
    public boolean hasStatus(org.bukkit.entity.LivingEntity entity,String id){var s=school.magiccodex.protocol.StatusProtocol.Status.of(id);return status!=null&&s!=null&&entity!=null&&status.shows(entity,s);}

    // ---- artifact API (MagicCodexBridge calls these by reflection; main thread)
    /** Element id of the target's live mark, or "" when none. */
    public String markElement(org.bukkit.entity.LivingEntity target){var m=reactions==null?null:reactions.markOf(target);return m==null?"":m.element().id;}
    /** Puts `element`'s mark on an enemy, ignoring the post-reaction immunity. */
    public boolean forceMark(org.bukkit.entity.Player caster,org.bukkit.entity.LivingEntity target,String element){return reactions!=null&&targets.enemy(caster,target)&&protection.pair(caster,target.getLocation())&&reactions.forceMark(caster,target,element);}
    /** Pops the target's mark for `damage`. @return the popped element id, or null when it had no mark */
    public String detonateMark(org.bukkit.entity.Player caster,org.bukkit.entity.LivingEntity target,double damage){return reactions==null||!targets.enemy(caster,target)||!protection.pair(caster,target.getLocation())?null:reactions.detonate(caster,target,damage);}
    /** Moves `element`'s mark to the nearest unmarked enemy around `center`. */
    public boolean spreadMark(org.bukkit.entity.Player caster,org.bukkit.Location center,org.bukkit.entity.LivingEntity exclude,String element,double radius){return reactions!=null&&protection.pair(caster,center)&&reactions.spread(caster,center,exclude,element,radius);}
    private Ticker ticker;
    private TempBlocks tempBlocks;
    private Guards guards;
    private Marks marks;
    private WorldMech.State worldState;
    private Reactions reactions;
    private StatusEffects status;
    private Telegraphs telegraphs;
    private SpellSlots slots;
    private Passives passives;
    private UtilMech.State utilState;
    private ArcanaMech.State arcanaState;
    private TrainingDummies dummies;

    @Override public void onEnable() {
        if(!protectionReady){getServer().getPluginManager().disablePlugin(this);return;}
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(protection,this);
        getServer().getPluginManager().registerEvents(allies,this);
        getServer().getPluginManager().registerEvents(new PartyFriendlyFire(this),this); // #23 근접·투사체·불·물약 아군 피해 차단
        targets = new Targets(this);
        tempBlocks = new TempBlocks(this);
        tempBlocks.restoreLeftovers();
        ticker = new Ticker(this);
        guards = new Guards(this);
        marks = new Marks(this);
        worldState = new WorldMech.State(this);
        reactions = new Reactions(this);
        status = new StatusEffects(this);
        telegraphs = new Telegraphs(this);
        Targets.sealed = e -> status != null && status.recoverySealed(e);
        Links.manaSealed = pl -> status != null && status.recoverySealed(pl);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(guards, this);
        getServer().getPluginManager().registerEvents(marks, this);
        getServer().getPluginManager().registerEvents(tempBlocks, this);
        getServer().getPluginManager().registerEvents(reactions, this);
        getServer().getPluginManager().registerEvents(status, this);
        getServer().getPluginManager().registerEvents(telegraphs, this);
        slots = new SpellSlots(this);
        slots.register();
        getServer().getPluginManager().registerEvents(slots, this);
        passives = new Passives(this);
        getServer().getPluginManager().registerEvents(passives, this);
        utilState = new UtilMech.State(this);
        getServer().getPluginManager().registerEvents(utilState, this);
        arcanaState = new ArcanaMech.State(this);
        getServer().getPluginManager().registerEvents(arcanaState, this);
        getServer().getPluginManager().registerEvents(new ChannelMech.Hooks(), this);
        getServer().getPluginManager().registerEvents(new SummonMech.Hooks(this), this);
        getServer().getPluginManager().registerEvents(new DiscoveryHooks(this), this);
        dummies = new TrainingDummies(this); // #107 마법 연습장 훈련 허수아비
        getServer().getPluginManager().registerEvents(dummies, this);
        spellRoutes = new SpellRoutes(new java.io.File(getDataFolder().getParentFile(), "MagicCodexBridge/spell-runtime.yml"));
        if (ChacaLink.ready()) {
            try { new MythicCommands(this).register(); }
            catch (RuntimeException e) { getLogger().log(java.util.logging.Level.WARNING, "명령어 허브에 마법 미몹 명령을 붙이지 못했습니다", e); }
        } else getLogger().warning("MagicCodexBridge 명령어 허브가 없어 마법 미몹 관리·테스트 명령을 등록하지 않았습니다 (메커닉은 그대로 동작).");
        getLogger().info("마법 미몹 메커닉 준비 완료 (MythicMobs 다시 불러오기 시 등록됩니다)");
    }

    @Override public void onDisable() {
        if (reactions != null) reactions.close();
        if (dummies != null) { dummies.close(); dummies = null; }
        Targets.sealed = null;
        Links.manaSealed = null;
        if (status != null) { status.close(); status = null; }
        if (telegraphs != null) { telegraphs.close(); telegraphs = null; }
        if (slots != null) slots.unregister();
        if (utilState != null) utilState.closeAll();
        if (arcanaState != null) { arcanaState.closeAll(); arcanaState = null; }
        if (guards != null) guards.closeAll();
        BuffMech.clearAll();
        TempAbsorb.clearAll();
        SummonMech.removeAll();
        Links.resetMana();
        DiscoveryHooks.reset();
        if (marks != null) marks.releaseAll();
        if (worldState != null) worldState.closeAll();
        if (ticker != null) ticker.close();
        if (tempBlocks != null) tempBlocks.removeAll();
    }

    @EventHandler public void mechanic(MythicMechanicLoadEvent event) {
        String name = event.getMechanicName().toLowerCase(Locale.ROOT);
        Params p = new Params(event.getConfig());
        Mech mechanic = switch (name) {
            case "chaselfpotion" -> new SelfMech(this,p,true);
            case "chaselfvelocity" -> new SelfMech(this,p,false);
            case "chahit" -> new HitMech(this, p);
            case "chameteor" -> new MeteorMech(this, p);
            case "chabolt" -> new BoltMech(this, p);
            case "chazone" -> new ZoneMech(this, p);
            case "chaguard" -> new GuardMech(this, p);
            case "chamark" -> new MarkMech(this, p);
            case "chaally" -> new AllyMech(this, p);
            case "chaworld" -> new WorldMech(this, p);
            case "chafarm" -> new FarmMech(this, p);
            case "chautil" -> new UtilMech(this, p);
            case "chasummon" -> new SummonMech(this, p);
            case "chabuff" -> new BuffMech(this, p);
            case "chachannel" -> new ChannelMech(this, p);
            case "chanote" -> new NoteMech(this, p);
            case "chastatus" -> new StatusMech(this, p);
            case "chatelegraph" -> new TelegraphMech(this, p);
            case "chamobshot" -> new MobShotMech(this, p);
            case "chaarcana" -> new ArcanaMech(this, p);
            case "chagimmick" -> new GimmickMech(this, p);
            default -> null;
        };
        if (mechanic != null) event.register(mechanic);
    }

    @EventHandler public void condition(MythicConditionLoadEvent event) {
        if (event.getConditionName().equalsIgnoreCase("chaenemy"))
            event.register(new EnemyCondition(this, event.getContainer().getLine()));
    }

    /** /서버관리 새로고침 몹마법 */
    void reloadAll() {
        reloadConfig(); targets.reload(); reactions.reload(); status.reload(); telegraphs.reload();
    }

    /** /서버관리 상태 의 몹마법 줄 */
    String statusLine() {
        return "진행 중 " + ticker.summary() + ", 보호막 " + guards.count() + ", 표식 " + marks.count()
            + ", 임시 블록 " + tempBlocks.count() + ", " + reactions.summary() + ", " + status.summary() + ", 예고 장판 " + telegraphs.count();
    }

    private SpellRoutes spellRoutes;
    SpellRoutes spellNames() { return spellRoutes; }

    Targets targets() { return targets; }
    AllyResolver allies() { return allies; }
    Ticker ticker() { return ticker; }
    TempBlocks tempBlocks() { return tempBlocks; }
    Guards guards() { return guards; }
    Marks marks() { return marks; }
    WorldMech.State worldState() { return worldState; }
    Reactions reactions() { return reactions; }
    StatusEffects status() { return status; }
    Telegraphs telegraphs() { return telegraphs; }
    Passives passives() { return passives; }
    TrainingDummies dummies() { return dummies; }
    SpellSlots slots() { return slots; }
    boolean debug() { return getConfig().getBoolean("debug", false); }
}
