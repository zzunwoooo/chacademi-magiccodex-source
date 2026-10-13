package school.magiccodex.client;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import org.yaml.snakeyaml.Yaml;import static org.junit.jupiter.api.Assertions.*;
class AimRangeContractTest {
 @Test void allPreviewTargetRangesMatchServerRuntimeYaml()throws Exception{
  var shapes=SpellTargets.parse(Files.readString(Path.of("src/main/resources/assets/magiccodex/spell-targeting.json")));
  Map<?,?> root=new Yaml().load(Files.readString(Path.of("../magic-mythic-paper/server-config/spell-runtime.yml")));
  Map<?,?> routes=(Map<?,?>)root.get("spells");assertNotNull(routes);
  for(var entry:shapes.entrySet()){Map<?,?> route=(Map<?,?>)routes.get(entry.getKey());assertNotNull(route,entry.getKey());assertEquals(((Number)route.get("range")).doubleValue(),entry.getValue().range(),1e-9,entry.getKey());}
 }
 @Test void coneAndWaveKeepMechanicalEnvelopeSeparateFromTargetRay()throws Exception{
  var shapes=SpellTargets.parse(Files.readString(Path.of("src/main/resources/assets/magiccodex/spell-targeting.json")));
  var cone=shapes.get("crimson_breath");assertEquals(15,cone.range());assertEquals(9,cone.length());assertEquals(50,cone.width());
  var firebird=shapes.get("firebird_hunt");assertEquals(10,firebird.range());assertEquals(12,firebird.length());assertEquals(3.2,firebird.width());
  assertTrue(shapes.get("tidal_wave").casterOrigin());assertFalse(shapes.get("ember_bolt").casterOrigin());
 }
}
