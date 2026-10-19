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
 @Test void replacesKnownDefaultsWithoutBackupAndIsIdempotent()throws Exception {
  byte[] old=bytes("old\r\n");Files.write(dir.resolve("one.yml"),old);
  var history=Map.of("one.yml",Set.of(DefaultSpellMigration.hash(bytes("old\n"))));
  var defaults=Map.of("one.yml",bytes("new\n"));
  DefaultSpellMigration.migrate(dir,defaults,history,"v1");
  assertEquals("new\n",Files.readString(dir.resolve("one.yml")));
  assertFalse(Files.exists(dir.resolve(".defaults-backup")));
  DefaultSpellMigration.migrate(dir,defaults,history,"v1");
  assertFalse(Files.exists(dir.resolve(".defaults-backup")));
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
 @Test void legacyBackupDirectoryIsNeverReadOrModified()throws Exception {
  byte[] old=bytes("old");Files.write(dir.resolve("one.yml"),old);String h=DefaultSpellMigration.hash(old);
  Path backup=dir.resolve(".defaults-backup/v1");Files.createDirectories(backup);Files.writeString(backup.resolve("one.yml."+h+".bak"),"wrong");
  DefaultSpellMigration.migrate(dir,Map.of("one.yml",bytes("new")),Map.of("one.yml",Set.of(h)),"v1");
  assertEquals("new",Files.readString(dir.resolve("one.yml")));
  assertEquals("wrong",Files.readString(backup.resolve("one.yml."+h+".bak")));
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
 @Test void arcanaRangeExcludesDeferredAndLegacyWindFiles(){
  for(String name:List.of("168_shadow_double.yml","175_drowsiness.yml","177_hungry_shadow.yml","193_inverted_offering.yml","203_landing_airbrake.yml","214_folding_tideplane.yml"))assertTrue(DefaultSpellMigration.managedHistoryName(name),name);
  for(String name:List.of("149_rainbow_collar.yml","171_afterlife_gate.yml","187_shadow_tracking.yml","191_final_knot.yml","197_death_ledger.yml","199_twilight_bargain.yml","200_barrelroll_stitch.yml","201_horizon_trim.yml","202_folding_broom_wings.yml","204_wingmate_towline.yml","205_potion_effect_decant.yml","206_twin_cup_draught.yml","207_wandering_alembic.yml","208_heartfold_wrapping.yml","209_conversation_ripples.yml","203_air_brake.yml","300_fox_orbit.yml"))assertFalse(DefaultSpellMigration.managedHistoryName(name),name);
 }

}
