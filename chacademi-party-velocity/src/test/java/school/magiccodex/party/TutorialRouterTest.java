package school.magiccodex.party;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** 튜토리얼 서버 분산 (2026-10-25): 무리 안에서 인원이 적은 서버로, 허용치·같은 서버 유지·꺼진 서버 제외. */
class TutorialRouterTest {
 static final List<String> G=List.of("tutorial1","tutorial2");
 static Map<String,Integer> counts(int a,int b){var m=new LinkedHashMap<String,Integer>();m.put("tutorial1",a);m.put("tutorial2",b);return m;}
 @Test void parsesListAndOnlyTwoOrMoreEnable(){
  assertEquals(List.of("a","b"),TutorialRouter.parseList(" a, b ,a,,"));
  assertEquals(List.of(),TutorialRouter.parseList(""));
 }
 @Test void picksTheEmptierServer(){
  assertEquals("tutorial2",TutorialRouter.pick(G,counts(5,2),"tutorial1",null,0));
  assertEquals("tutorial1",TutorialRouter.pick(G,counts(2,5),"tutorial1",null,0));
  assertEquals("tutorial1",TutorialRouter.pick(G,counts(3,3),"tutorial1",null,0));
  assertEquals("tutorial2",TutorialRouter.pick(G,counts(3,3),"tutorial2",null,0));
 }
 @Test void toleranceKeepsTheRequestedServer(){
  assertEquals("tutorial1",TutorialRouter.pick(G,counts(5,3),"tutorial1",null,2));
  assertEquals("tutorial2",TutorialRouter.pick(G,counts(6,3),"tutorial1",null,2));
 }
 @Test void stickyServerWinsWhileItIsUp(){
  assertEquals("tutorial1",TutorialRouter.pick(G,counts(9,0),"tutorial2","tutorial1",0));
  var onlySecond=new LinkedHashMap<String,Integer>();onlySecond.put("tutorial2",9);
  assertEquals("tutorial2",TutorialRouter.pick(G,onlySecond,"tutorial1","tutorial1",0));
 }
 @Test void noServerUpKeepsTheRequest(){
  assertEquals("tutorial1",TutorialRouter.pick(G,Map.of(),"tutorial1",null,0));
 }
}
