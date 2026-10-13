package school.magiccodex.paper;
import java.util.*;import java.util.logging.Logger;import org.junit.jupiter.api.Test;import org.mockito.ArgumentCaptor;import org.bukkit.*;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import school.magiccodex.protocol.JournalProtocol;import school.magiccodex.protocol.JournalProtocol.*;import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class JournalBridgeTest {
 @Test void adminRequiresPermissionAndAckIsBoundToPlayerIdOperationAndSingleUse(){
  var plugin=mock(MagicCodexBridge.class);var server=mock(Server.class,RETURNS_DEEP_STUBS);var quest=mock(QuestBridge.class);var dialogue=mock(DialogueBridge.class);when(plugin.questBridge()).thenReturn(quest);when(plugin.dialogueBridge()).thenReturn(dialogue);when(plugin.getLogger()).thenReturn(Logger.getLogger("journal-test"));when(quest.journalCatalog()).thenReturn(Map.of());when(dialogue.journalCatalog()).thenReturn(Map.of());
  Player target=player(),attacker=player();var admin=mock(CommandSender.class);when(admin.getName()).thenReturn("admin");
  var messenger=server.getMessenger();var manager=server.getPluginManager();var scheduler=server.getScheduler();try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::getMessenger).thenReturn(messenger);b.when(Bukkit::getPluginManager).thenReturn(manager);b.when(Bukkit::getScheduler).thenReturn(scheduler);var bridge=new JournalBridge(plugin);
   var entry=new Entry("story","ch1","Chapter","","active",true,"","");bridge.onPluginMessageReceived(JournalProtocol.QUERY,target,JournalProtocol.encode(new Message(5,"catalog","","","",0,1,List.of(entry))));
   assertThrows(IllegalArgumentException.class,()->bridge.command(admin,target,new String[]{"\ubd80\uc5ec","story:ch1"}));verify(target,never()).sendPluginMessage(any(),anyString(),any());
   when(admin.hasPermission("magiccodex.quests.admin")).thenReturn(true);bridge.command(admin,target,new String[]{"\ubd80\uc5ec","story:ch1"});var packet=ArgumentCaptor.forClass(byte[].class);verify(target).sendPluginMessage(eq(plugin),eq(JournalProtocol.REPLY),packet.capture());var request=JournalProtocol.decode(packet.getValue());assertEquals(2,request.kind());
   var ack=new Message(3,request.token(),"grant","ch1","ok",0,0,List.of());bridge.onPluginMessageReceived(JournalProtocol.QUERY,attacker,JournalProtocol.encode(ack));verify(admin,never()).sendMessage(anyString());
   bridge.onPluginMessageReceived(JournalProtocol.QUERY,target,JournalProtocol.encode(new Message(3,request.token(),"reset","ch1","ok",0,0,List.of())));verify(admin,never()).sendMessage(anyString());
   bridge.onPluginMessageReceived(JournalProtocol.QUERY,target,JournalProtocol.encode(ack));bridge.onPluginMessageReceived(JournalProtocol.QUERY,target,JournalProtocol.encode(ack));verify(admin,times(1)).sendMessage(eq("퀘스트 기록을 변경했습니다. 보상·호감도 수령 기록은 유지됩니다."));verify(quest,never()).journalEdit(any(),any(),anyString(),anyString());verify(dialogue,never()).journalEdit(any(),any(),anyString(),anyString());
  }
 }
 private Player player(){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);when(p.getListeningPluginChannels()).thenReturn(Set.of(JournalProtocol.REPLY));return p;}
}
