package school.magiccodex.client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class HolyTamingDefaultMigrationTest {
 @TempDir Path root;
 @Test void upgradesOnlyKnownDefaultAndKeepsBackup() throws Exception {
  Path dir=Files.createDirectory(root.resolve("spells"));
  byte[] old=getClass().getResourceAsStream("/legacy-rainbow-collar.yml").readAllBytes();
  Path file=dir.resolve("149_rainbow_collar.yml");Files.write(file,old);
  SpellYamlLoader loader=new SpellYamlLoader();loader.installDefaults(dir);
  assertTrue(Files.readString(file).contains("mana-cost: 150"));
  assertArrayEquals(old,Files.readAllBytes(dir.resolve("149_rainbow_collar.yml.pre-holytaming.bak")));
  byte[] updated=Files.readAllBytes(file);loader.installDefaults(dir);assertArrayEquals(updated,Files.readAllBytes(file));
 }
 @Test void preservesCustomCatalogAndInstallsCompleteFreshCatalog() throws Exception {
  Path dir=Files.createDirectory(root.resolve("custom"));Path file=dir.resolve("149_rainbow_collar.yml");Files.writeString(file,"custom data");
  SpellYamlLoader loader=new SpellYamlLoader();loader.installDefaults(dir);assertEquals("custom data",Files.readString(file));
  Path fresh=root.resolve("fresh");loader.installDefaults(fresh);
  try(var files=Files.list(fresh)){assertTrue(files.count()>149);}
  assertTrue(loader.load(fresh).success());
 }
 @Test void updatesPreviousHolyTamingDefaultWithoutChangingCosts() throws Exception {
  Path dir=Files.createDirectory(root.resolve("previous"));Path file=dir.resolve("149_rainbow_collar.yml");
  Files.write(file,getClass().getResourceAsStream("/previous-holytaming.yml").readAllBytes());
  new SpellYamlLoader().installDefaults(dir);String updated=Files.readString(file);
  assertTrue(updated.contains("몬스터 교화 실습 수행하기"));assertTrue(updated.contains("mana-cost: 150"));assertTrue(updated.contains("cooldown-seconds: 60"));
 }
}
