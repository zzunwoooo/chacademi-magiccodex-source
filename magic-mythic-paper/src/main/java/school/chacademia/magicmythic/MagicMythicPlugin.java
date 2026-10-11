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
    private Ticker ticker;
    private TempBlocks tempBlocks;
    private Guards guards;
    private Marks marks;
    private WorldMech.State worldState;

    @Override public void onEnable() {
        saveDefaultConfig();
        targets = new Targets(this);
        tempBlocks = new TempBlocks(this);
        tempBlocks.restoreLeftovers();
        ticker = new Ticker(this);
        guards = new Guards(this);
        marks = new Marks(this);
        worldState = new WorldMech.State(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(guards, this);
        getServer().getPluginManager().registerEvents(marks, this);
        var command = getCommand("마법미몹");
        if (command != null) command.setExecutor(this);
        getLogger().info("마법 미몹 메커닉 준비 완료 (MythicMobs 다시 불러오기 시 등록됩니다)");
    }

    @Override public void onDisable() {
        if (marks != null) marks.releaseAll();
        if (worldState != null) worldState.closeAll();
        if (ticker != null) ticker.close();
        if (tempBlocks != null) tempBlocks.removeAll();
    }

    @EventHandler public void mechanic(MythicMechanicLoadEvent event) {
        String name = event.getMechanicName().toLowerCase(Locale.ROOT);
        Params p = new Params(event.getConfig());
        Mech mechanic = switch (name) {
            case "chahit" -> new HitMech(this, p);
            case "chabolt" -> new BoltMech(this, p);
            case "chazone" -> new ZoneMech(this, p);
            case "chaguard" -> new GuardMech(this, p);
            case "chamark" -> new MarkMech(this, p);
            case "chaally" -> new AllyMech(this, p);
            case "chaworld" -> new WorldMech(this, p);
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
            reloadConfig(); targets.reload();
            sender.sendMessage("마법 미몹 설정을 다시 불러왔습니다. 스킬 YAML을 고쳤다면 /mm reload 도 실행하세요.");
            return true;
        }
        if (Set.of("상태", "status").contains(sub)) {
            sender.sendMessage("진행 중: " + ticker.summary() + ", 보호막 " + guards.count() + ", 표식 " + marks.count()
                + ", 임시 블록 " + tempBlocks.count());
            return true;
        }
        sender.sendMessage("/마법미몹 <새로고침|상태>");
        return true;
    }

    Targets targets() { return targets; }
    Ticker ticker() { return ticker; }
    TempBlocks tempBlocks() { return tempBlocks; }
    Guards guards() { return guards; }
    Marks marks() { return marks; }
    WorldMech.State worldState() { return worldState; }
    boolean debug() { return getConfig().getBoolean("debug", false); }
}
