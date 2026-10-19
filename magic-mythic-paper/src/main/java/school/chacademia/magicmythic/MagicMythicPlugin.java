package school.chacademia.magicmythic;

import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MythicMobs glue for Chacademi spells. MagicCodexBridge keeps mana, cooldown, permission and target choice;
 * this plugin only supplies the gameplay mechanics (damage, knockback, zones, guards, small world utilities)
 * that the CHA_* skills call. Visuals stay in PortableVFX.
 */
public final class MagicMythicPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private Targets targets;
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

    @Override public void onEnable() {
        if(!protectionReady){getServer().getPluginManager().disablePlugin(this);return;}
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(protection,this);
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
        getServer().getPluginManager().registerEvents(new ChannelMech.Hooks(), this);
        getServer().getPluginManager().registerEvents(new SummonMech.Hooks(this), this);
        getServer().getPluginManager().registerEvents(new DiscoveryHooks(this), this);
        var command = getCommand("마법미몹");
        if (command != null) command.setExecutor(this);
        getLogger().info("마법 미몹 메커닉 준비 완료 (MythicMobs 다시 불러오기 시 등록됩니다)");
    }

    @Override public void onDisable() {
        if (reactions != null) reactions.close();
        Targets.sealed = null;
        Links.manaSealed = null;
        if (status != null) { status.close(); status = null; }
        if (telegraphs != null) { telegraphs.close(); telegraphs = null; }
        if (slots != null) slots.unregister();
        if (utilState != null) utilState.closeAll();
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
            default -> null;
        };
        if (mechanic != null) event.register(mechanic);
    }

    @EventHandler public void condition(MythicConditionLoadEvent event) {
        if (event.getConditionName().equalsIgnoreCase("chaenemy"))
            event.register(new EnemyCondition(this, event.getContainer().getLine()));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chacademia.magicmythic.admin")) return false;
        String sub = args.length == 0 ? "" : args[0];
        if (Set.of("새로고침", "reload").contains(sub)) {
            reloadConfig(); targets.reload(); reactions.reload(); status.reload(); telegraphs.reload();
            sender.sendMessage("마법 미몹 설정을 다시 불러왔습니다. 스킬 YAML을 고쳤다면 /mm reload 도 실행하세요.");
            return true;
        }
        if (Set.of("상태", "status").contains(sub)) {
            sender.sendMessage("진행 중: " + ticker.summary() + ", 보호막 " + guards.count() + ", 표식 " + marks.count()
                + ", 임시 블록 " + tempBlocks.count() + ", " + reactions.summary() + ", " + status.summary() + ", 예고 장판 " + telegraphs.count());
            return true;
        }
        if (Set.of("장착", "slots").contains(sub) && args.length >= 2) {
            var target = getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("접속 중인 플레이어가 아닙니다."); return true; }
            if (args.length == 2) { sender.sendMessage(target.getName() + " 장착: " + slots.list(target) + " (모드 " + slots.mode() + ", 보고 " + (slots.known(target) ? "있음" : "없음") + ")"); return true; }
            var ids = Arrays.asList(args).subList(2, args.length);
            slots.force(target, ids.contains("비우기") || ids.contains("clear") ? List.of() : SpellSlots.parse(String.join(",", ids)));
            sender.sendMessage(target.getName() + " 장착(강제): " + slots.list(target));
            return true;
        }
        if (Set.of("시험", "test").contains(sub) && args.length >= 2 && sender instanceof org.bukkit.entity.Player player) {
            sender.sendMessage(TestCast.cast(this, player, args[1]));
            return true;
        }
        if (Set.of("예고", "telegraph").contains(sub)) { sender.sendMessage(TelegraphCommand.run(this, sender, Arrays.copyOfRange(args, 1, args.length))); return true; }
        if (Set.of("상태이상", "debuff").contains(sub)) { sender.sendMessage(StatusCommand.run(this, sender, Arrays.copyOfRange(args, 1, args.length))); return true; }
        sender.sendMessage("/마법미몹 <새로고침|상태|장착 <플레이어> [id...|비우기]|시험 <마법ID>|상태이상 <종류|해제> [초] [나|바라봄|플레이어] [세기]|예고 <원형|직선|부채꼴|전부|봉인진|봉인진이로치> [초]>");
        return true;
    }

    Targets targets() { return targets; }
    Ticker ticker() { return ticker; }
    TempBlocks tempBlocks() { return tempBlocks; }
    Guards guards() { return guards; }
    Marks marks() { return marks; }
    WorldMech.State worldState() { return worldState; }
    Reactions reactions() { return reactions; }
    StatusEffects status() { return status; }
    Telegraphs telegraphs() { return telegraphs; }
    Passives passives() { return passives; }
    SpellSlots slots() { return slots; }
    boolean debug() { return getConfig().getBoolean("debug", false); }
}
