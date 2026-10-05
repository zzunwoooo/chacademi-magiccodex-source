package kr.chacademy.npc.cmd;

import kr.chacademy.npc.ChacaNpcPlugin;
import kr.chacademy.npc.core.CharacterSheet;
import kr.chacademy.npc.core.TextFilter;
import kr.chacademy.npc.dialogue.DialogueSession;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 플레이어 명령어 (임시 대화 방식).
 * /t <할 말>, /t 그만, /호칭 <이름>, /chacanpc-ui (채팅 버튼 클릭용)
 */
public final class PlayerCommands implements CommandExecutor, TabCompleter {

    private final ChacaNpcPlugin plugin;

    public PlayerCommands(ChacaNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("플레이어만 쓸 수 있어요.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "t" -> talk(p, args);
            case "nickname" -> nickname(p, args);
            case "chacanpc-ui" -> ui(p, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void talk(Player p, String[] args) {
        String text = String.join(" ", args).trim();
        DialogueSession s = plugin.dialogue().session(p.getUniqueId());
        if (text.equals("그만") || text.equals("끝") || text.equalsIgnoreCase("bye")) {
            if (s != null) {
                plugin.dialogue().close(p, true);
            }
            return;
        }
        if (s == null) {
            NPC npc = plugin.npcs().nearest(p, plugin.settings().talkStartDistance);
            if (npc == null) {
                plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "근처에 대화할 수 있는 NPC가 없어요.");
                return;
            }
            String charId = plugin.npcs().charIdFor(npc.getId());
            CharacterSheet c = plugin.characters().get(charId);
            if (c == null) {
                plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "이 NPC의 캐릭터 파일이 없어요.");
                return;
            }
            int npcId = npc.getId();
            if (plugin.link().available()) {
                // 우클릭과 같은 우선순위: 열 수 있는 고정(스토리) 대화가 있으면 그쪽, NONE 일 때만 AI
                plugin.link().openStory(p, npcId, npc.getEntity()).whenComplete((res, ex) -> {
                    if (ex == null && "NONE".equals(res) && p.isOnline()) {
                        openAndSay(p, c, npcId, text);
                    }
                });
            } else if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MagicCodexBridge")) {
                openAndSay(p, c, npcId, text);
            } else {
                plugin.dialogue().viewFor(p).info(p, null, "지금은 이 NPC와 대화할 수 없어요.");
            }
            return;
        }
        if (!text.isEmpty()) {
            plugin.dialogue().input(p, text, null);
        }
    }

    private void openAndSay(Player p, CharacterSheet c, int npcId, String text) {
        plugin.dialogue().open(p, c, npcId, false);
        if (!text.isEmpty() && plugin.dialogue().session(p.getUniqueId()) != null) {
            plugin.dialogue().input(p, text, null);
        }
    }

    private void nickname(Player p, String[] args) {
        if (args.length == 0) {
            plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "사용법: /호칭 <NPC가 나를 부를 이름>  (되돌리기: /호칭 초기화)");
            return;
        }
        String name = String.join(" ", args).trim();
        String pid = p.getUniqueId().toString();
        if (name.equals("초기화") || name.equalsIgnoreCase("reset")) {
            plugin.database().run(() -> plugin.storage().setNickname(pid, "*", null));
            plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "이제 NPC들이 원래 이름으로 불러요.");
            return;
        }
        if (name.length() > 8) {
            plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "호칭은 8글자까지 할 수 있어요.");
            return;
        }
        TextFilter filter = plugin.content().filter();
        if (!filter.isClean(name)) {
            plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "그 호칭은 쓸 수 없어요.");
            return;
        }
        plugin.database().run(() -> plugin.storage().setNickname(pid, "*", name));
        plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()), "이제 NPC들이 '" + name + "'(이)라고 불러요. (다음 대화부터)");
    }

    private void ui(Player p, String[] args) {
        if (args.length >= 2 && args[0].equals("btn")) {
            plugin.dialogue().input(p, null, args[1]);
        } else if (args.length >= 3 && args[0].equals("quest")) {
            plugin.dialogue().answerQuest(p, args[2], args[1].equals("accept"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("t") && args.length == 1) {
            return List.of("그만");
        }
        return List.of();
    }

    @SuppressWarnings("unused")
    private static void sync(Runnable r) {
        Bukkit.getScheduler().runTask(ChacaNpcPlugin.instance(), r);
    }
}
