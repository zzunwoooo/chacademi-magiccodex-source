package school.magiccodex.client;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class DefaultSpellMigrationTest {
 @TempDir Path dir;
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 @Test void replacesKnownDefaultsWithExactBackupAndIsIdempotent()throws Exception {
  byte[] old=bytes("old\r\n");Files.write(dir.resolve("one.yml"),old);
  var history=Map.of("one.yml",Set.of(DefaultSpellMigration.hash(bytes("old\n"))));
  var defaults=Map.of("one.yml",bytes("new\n"));
  DefaultSpellMigration.migrate(dir,defaults,history,"v1");
  assertEquals("new\n",Files.readString(dir.resolve("one.yml")));
  try(var files=Files.list(dir.resolve(".defaults-backup/v1"))){assertArrayEquals(old,Files.readAllBytes(files.findFirst().orElseThrow()));}
  DefaultSpellMigration.migrate(dir,defaults,history,"v1");
  try(var files=Files.list(dir.resolve(".defaults-backup/v1"))){assertEquals(1,files.count());}
  assertEquals("v1",Files.readString(dir.resolve(".defaults-version")));
 }
 @Test void preservesCustomFilesAndRestoresMissingDefaultsOnEveryRun()throws Exception {
  Files.writeString(dir.resolve("one.yml"),"my custom spell");Files.writeString(dir.resolve("extra.yml"),"custom extra");
  var defaults=Map.of("one.yml",bytes("new"),"two.yml",bytes("two"));
  DefaultSpellMigration.migrate(dir,defaults,Map.of(),"v1");
  assertEquals("my custom spell",Files.readString(dir.resolve("one.yml")));assertEquals("custom extra",Files.readString(dir.resolve("extra.yml")));
  Files.delete(dir.resolve("two.yml"));DefaultSpellMigration.migrate(dir,defaults,Map.of(),"v1");
  assertEquals("two",Files.readString(dir.resolve("two.yml")));
 }
 @Test void corruptBackupPreventsReplacement()throws Exception {
  byte[] old=bytes("old");Files.write(dir.resolve("one.yml"),old);String h=DefaultSpellMigration.hash(old);
  Path backup=dir.resolve(".defaults-backup/v1");Files.createDirectories(backup);Files.writeString(backup.resolve("one.yml."+h+".bak"),"wrong");
  assertThrows(java.io.IOException.class,()->DefaultSpellMigration.migrate(dir,Map.of("one.yml",bytes("new")),Map.of("one.yml",Set.of(h)),"v1"));
  assertArrayEquals(old,Files.readAllBytes(dir.resolve("one.yml")));
 }
 @Test void upgradesDeployed147AndKeepsUserEdited101()throws Exception {
  try(var in=getClass().getResourceAsStream("/default-history/147_solar_judgment.yml")){Files.copy(in,dir.resolve("147_solar_judgment.yml"));}
  Files.writeString(dir.resolve("101_earth_shaping.yml"),"my custom spell");
  new SpellYamlLoader().installDefaults(dir);
  var spell=new SpellYamlLoader().parse(Files.readString(dir.resolve("147_solar_judgment.yml")));
  assertEquals(800,spell.manaCost());assertEquals(300,spell.cooldown());assertFalse(spell.command().isBlank());
  assertEquals("my custom spell",Files.readString(dir.resolve("101_earth_shaping.yml")));
  assertTrue(Files.exists(dir.resolve(".defaults-version")));
 }
 @Test void cooldownResetUnblocksTheLocalCooldown(){
  CastingState state=new CastingState();state.serverResult("solar_judgment",300000,1000);assertTrue(state.remaining("solar_judgment",1001)>0);
  state.clearCooldown("solar_judgment");assertEquals(0,state.remaining("solar_judgment",1001));
 }
}
