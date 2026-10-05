package kr.chacademy.npc.dialogue;

import kr.chacademy.npc.core.Defs.Button;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 채팅 대화 화면 (MagicCodex HUD 대화를 지원하지 않는 클라이언트용 대체 화면).
 * 플레이어는 /t <할 말> 로 대화한다.
 */
public final class ChatDialogueView implements DialogueView {

    private static TextColor nameColor(DialogueSession s) {
        String hex = s.character().color();
        if (hex != null) {
            TextColor c = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
            if (c != null) {
                return c;
            }
        }
        return NamedTextColor.GOLD;
    }

    private static Component npcLine(DialogueSession s, String text) {
        return Component.text("[" + s.character().name() + "] ", nameColor(s))
                .append(Component.text(text, NamedTextColor.WHITE));
    }

    @Override
    public void open(Player player, DialogueSession session, String greeting, List<Button> buttons, int hearts) {
        player.sendMessage(Component.empty());
        player.sendMessage(npcLine(session, greeting));
        TextComponent.Builder row = Component.text().content("  ");
        for (Button b : buttons) {
            row.append(Component.text("[" + b.label() + "]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/chacanpc-ui btn " + b.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("클릭해서 말하기"))));
            row.append(Component.text(" "));
        }
        player.sendMessage(row.build());
        player.sendMessage(Component.text("  /t <할 말> 로 대화, /t 그만 으로 끝내기", NamedTextColor.DARK_GRAY));
    }

    @Override
    public void thinking(Player player, DialogueSession session, int seq) {
        player.sendActionBar(Component.text(session.character().name() + ": …", nameColor(session)));
    }

    @Override
    public void stall(Player player, DialogueSession session, int seq, String line) {
        player.sendActionBar(Component.text(session.character().name() + ": " + line, nameColor(session)));
    }

    @Override
    public void line(Player player, DialogueSession session, int seq, String line) {
        player.sendActionBar(Component.empty());
        player.sendMessage(npcLine(session, line));
    }

    @Override
    public void questOffer(Player player, DialogueSession session, int seq, String title, String token) {
        player.sendMessage(Component.text("  부탁: " + title + " ", NamedTextColor.YELLOW)
                .append(Component.text("[수락]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/chacanpc-ui quest accept " + token)))
                .append(Component.text(" "))
                .append(Component.text("[거절]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/chacanpc-ui quest decline " + token))));
    }

    @Override
    public void info(Player player, DialogueSession session, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GRAY));
    }

    @Override
    public void close(Player player, DialogueSession session, String farewell) {
        player.sendActionBar(Component.empty());
        if (farewell != null) {
            player.sendMessage(npcLine(session, farewell));
        }
        player.sendMessage(Component.text("  (" + session.character().name() + "와의 대화가 끝났어요)", NamedTextColor.DARK_GRAY));
    }
}
