package school.magiccodex.paper;
import java.nio.file.*;import java.util.*;import org.bukkit.configuration.file.YamlConfiguration;import org.junit.jupiter.api.Test;import school.magiccodex.protocol.ArtifactProtocol;import static org.junit.jupiter.api.Assertions.*;
class ArtifactCatalogTest {
 @Test void shippedCatalogHas148VisibleEntriesAndAllDeclaredIconsExist()throws Exception {
  var y=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/artifacts.yml").toFile());var root=y.getConfigurationSection("artifacts");assertNotNull(root);assertEquals(150,root.getKeys(false).size());int icons=0,usable=0;var entries=new ArrayList<ArtifactProtocol.Entry>();
  for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);String icon=s.getString("icon","");if(!icon.isBlank()){icons++;assertTrue(Files.exists(Path.of("../magic-codex-fabric/src/main/resources/assets/magiccodex",icon.substring("magiccodex:".length()))),id);}if(s.getBoolean("use"))usable++;if(!s.getBoolean("enabled",true))continue;
   int grade=List.of("common","rare","unique","legendary").indexOf(s.getString("grade"));assertTrue(grade>=0,id);entries.add(new ArtifactProtocol.Entry(id,s.getString("name"),grade,s.getString("description",""),s.getString("effect",""),icon,s.getBoolean("use")?s.getInt("cooldown"):ArtifactProtocol.PASSIVE));
  }
  assertEquals(148,entries.size());assertEquals(71,icons);assertEquals(20,usable);assertFalse(root.getBoolean("bug_repellent_incense.enabled"));assertFalse(root.getBoolean("comeback_medal.enabled"));
  var decoded=new ArrayList<ArtifactProtocol.Entry>();for(byte[] packet:ArtifactProtocol.catalog(123,entries)){assertTrue(packet.length<=ArtifactProtocol.MAX_BYTES);decoded.addAll(((ArtifactProtocol.Catalog)ArtifactProtocol.decode(packet)).entries());}assertEquals(entries,decoded);
 }
}
