package dev.portablevfx.paper.internal.spell;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Normal casting is silent on success. Operator diagnostics belong to the separate debug command. */
public final class SpellCommand implements CommandExecutor, TabCompleter {
    private final SpellCatalog catalog;
    private final SpellCastController controller;
    private final Predicate<UUID> compatibleClient;
    public SpellCommand(SpellCatalog catalog, SpellCastController controller, Predicate<UUID> compatibleClient) {
        this.catalog=Objects.requireNonNull(catalog);this.controller=Objects.requireNonNull(controller);
        this.compatibleClient=Objects.requireNonNull(compatibleClient);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)){sender.sendMessage("게임 안에서 사용해 주세요.");return true;}
        if(args.length==0){sender.sendMessage("사용법: /마법 <마법명>");return true;}
        String name=String.join(" ",args);
        var resolved=catalog.resolve(name);
        if(resolved.isEmpty()){sender.sendMessage("등록되지 않은 마법입니다.");return true;}
        if(!player.hasPermission(resolved.get().permission())){sender.sendMessage("아직 배우지 않은 마법입니다.");return true;}
        if(!resolved.get().enabled()){sender.sendMessage("아직 사용할 수 없는 마법입니다.");return true;}
        if(!compatibleClient.test(player.getUniqueId())){sender.sendMessage("마법을 사용하려면 최신 VFX 모드가 필요합니다.");return true;}
        SpellCastController.Result result=controller.cast(player.getUniqueId(),name);
        switch(result.status()) {
            case CAST -> { /* Never send normal-cast handle, count, status or debug messages. */ }
            case UNKNOWN_SPELL -> sender.sendMessage("등록되지 않은 마법입니다.");
            case NOT_READY -> sender.sendMessage("아직 사용할 수 없는 마법입니다.");
            case NO_AUTHORITY -> sender.sendMessage("마법 시전 연결이 준비되지 않았습니다.");
            case DENIED -> { /* The existing mana owner supplies its own bounded denial feedback. */ }
            case PLAYBACK_FAILED -> sender.sendMessage("마법 효과를 시작할 수 없습니다.");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(args.length>1)return List.of();
        String prefix=args.length==0?"":args[0];
        return catalog.complete(prefix).stream().filter(id->catalog.resolve(id).map(s->sender.hasPermission(s.permission())).orElse(false)).toList();
    }
}
