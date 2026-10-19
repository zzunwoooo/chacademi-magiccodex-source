package school.magiccodex.client;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PlayerHudScaleTest {
 @TempDir Path dir;
 @Test void zeroHidesFiftyIsOriginalAndHundredDoubles(){
  var s=new PlayerHudSettings();assertEquals(50,s.percent());assertEquals(1,s.multiplier());
  s.percent(0);assertFalse(s.visible());assertEquals(0,s.multiplier());
  s.percent(100);assertTrue(s.visible());assertEquals(2,s.multiplier());
  s.percent(-1);assertEquals(0,s.percent());s.percent(500);assertEquals(100,s.percent());s.percent(PlayerHudSettings.DEFAULT);assertEquals(50,s.percent());
 }
 @Test void saveReloadRetainsEachValueAndUnrelatedProperties()throws Exception{
  Path p=dir.resolve("config/gui-scale.properties");Files.createDirectories(p.getParent());Files.writeString(p,"future-setting=keep\n");
  for(int value:new int[]{0,50,100}){var s=new PlayerHudSettings();s.percent(value);assertTrue(s.save(p));assertEquals(value,PlayerHudSettings.load(p).percent());var props=new Properties();try(var in=Files.newBufferedReader(p)){props.load(in);}assertEquals("keep",props.getProperty("future-setting"));}
  try(var files=Files.list(p.getParent())){assertEquals(1,files.count());}
 }
 @Test void absentInvalidAndMalformedSettingsAreSafeAndDoNotDestroyExistingFile()throws Exception{
  Path p=dir.resolve("gui.properties");assertEquals(50,PlayerHudSettings.load(p).percent());
  for(String value:new String[]{"NaN","Infinity","broken",""}){Files.writeString(p,"player-hud-size="+value);assertEquals(50,PlayerHudSettings.load(p).percent());}
  Files.writeString(p,"player-hud-size=200");assertEquals(100,PlayerHudSettings.load(p).percent());
  String malformed="other=\\uZZZZ";Files.writeString(p,malformed);assertFalse(new PlayerHudSettings().save(p));assertEquals(malformed,Files.readString(p));
 }
 @Test void originalSizeIsExactAtEveryGuiScaleAndResolution(){
  for(int[] fb:new int[][]{{1920,1080},{2560,1440},{1280,720},{3440,1440},{900,1600},{640,480}}){
   int auto=Math.max(1,Math.min(fb[0]/320,fb[1]/240));
   for(int requested:new int[]{2,3,4,0}){int gui=requested==0?auto:Math.min(requested,auto);int w=(int)Math.ceil(fb[0]/(double)gui),h=(int)Math.ceil(fb[1]/(double)gui);
    var original=PlayerHudLayout.of(w,h);assertEquals(original,PlayerHudLayout.player(w,h,50));
    assertEquals(original.scale()*2,PlayerHudLayout.player(w,h,100).scale(),.00001);
    assertEquals(original,PlayerHudLayout.player(w,h,0));
   }
  }
 }
 @Test void enlargedHudFitsAndClearsUnchangedMenuAndSkillBar(){
  for(int[] fb:new int[][]{{1920,1080},{2560,1440},{1280,720},{3440,1440},{900,1600},{640,480}}){
   int auto=Math.max(1,Math.min(fb[0]/320,fb[1]/240));
   for(int requested:new int[]{2,3,4,0}){int gui=requested==0?auto:Math.min(requested,auto);int w=(int)Math.ceil(fb[0]/(double)gui),h=(int)Math.ceil(fb[1]/(double)gui);
    var base=PlayerHudLayout.of(w,h);float skills=Math.min(w/1672f,h/941f);
    for(int percent:new int[]{1,25,50,75,100}){var l=PlayerHudLayout.player(w,h,percent);float z=l.scale();
     assertTrue(l.hotbarLeft()>=0);assertTrue((l.hotbarLeft()+476)*z<=w+.01);assertTrue((l.hotbarTop()+60)*z<h);
     assertTrue(l.frameX()-90>=0);assertTrue((l.frameY()-90)*z>=43*base.scale()-.01);assertTrue((l.frameY()+90)*z<h);
     assertTrue((l.vitalX()+PlayerHudLayout.VITAL_WIDTH)*z<=w);assertTrue((l.healthY()-28)*z>=0);assertTrue((l.manaY()+16)*z<h);
     if((l.hotbarLeft()+476)*z>w-402*skills&&percent>50)assertTrue((l.hotbarTop()+60)*z<=h-76*skills+.01);
     if(l.vitalX()+PlayerHudLayout.VITAL_WIDTH>=l.hotbarLeft())assertTrue(l.frameY()+90<l.hotbarTop()-39);
    }
   }
  }
 }
}