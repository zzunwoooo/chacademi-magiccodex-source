package school.magiccodex.paper;
import static org.junit.jupiter.api.Assertions.*;import java.util.Map;import org.junit.jupiter.api.Test;import org.yaml.snakeyaml.Yaml;
class BootstrapTest {
 @Test void runtimeCommandNamesAndPermissionsMatchUtf8Descriptor(){try(var input=getClass().getResourceAsStream("/plugin.yml")){assertNotNull(input);Map<?,?> root=new Yaml().load(input);Map<?,?> commands=(Map<?,?>)root.get("commands");assertTrue(commands.containsKey("상점"));assertTrue(commands.containsKey("상점관리"));Map<?,?> permissions=(Map<?,?>)root.get("permissions");assertEquals(true,((Map<?,?>)permissions.get("magiccodex.mailbox")).get("default"));assertEquals("op",((Map<?,?>)permissions.get("magiccodex.shop.admin")).get("default"));
  // D-10: /상점 <ID> 는 관리자 전용. 유저의 NPC 클릭 경로는 magiccodex.shop(기본 허용)으로 유지한다.
  assertEquals("magiccodex.shop.admin",((Map<?,?>)commands.get("상점")).get("permission"));assertEquals(true,((Map<?,?>)permissions.get("magiccodex.shop")).get("default"));
  assertEquals("magiccodex.mailbox.admin",((Map<?,?>)commands.get("우편관리")).get("permission"));assertEquals("op",((Map<?,?>)permissions.get("magiccodex.mailbox.admin")).get("default"));}catch(Exception e){throw new AssertionError(e);}}
}
