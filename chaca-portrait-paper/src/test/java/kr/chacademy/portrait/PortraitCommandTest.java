package kr.chacademy.portrait;
import kr.chacademy.portrait.cmd.AdminCommand;
import org.bukkit.command.CommandSender;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PortraitCommandTest {
 private CommandSender sender(boolean allowed){
  return (CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CommandSender.class},
      (proxy,method,args)->method.getName().equals("hasPermission")?allowed:null);
 }
 @Test void nonAdminsReceiveNoSuggestions(){
  assertEquals(List.of(),new AdminCommand(null).onTabComplete(sender(false),null,"portrait",new String[]{"regen"}));
 }
 @Test void manualGenerationOffersMatchingModelsAndNoComparison(){
  var command=new AdminCommand(null);
  assertFalse(command.onTabComplete(sender(true),null,"portrait",new String[]{""}).contains("test"));
  assertEquals(List.of("gpt-image-1.5"),command.onTabComplete(sender(true),null,"portrait",new String[]{"regen","Player","gpt-image-1"}));
  assertEquals(List.of(),command.onTabComplete(sender(true),null,"portrait",new String[]{"test","Player","b"}));
  assertEquals(List.of("gpt-image-2.5-sunburst", "gpt-image-2.5-flare", "gpt-image-2"),command.onTabComplete(sender(true),null,"portrait",new String[]{"model","gpt-image-2"}));
 }
}
