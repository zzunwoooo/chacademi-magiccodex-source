package kr.chacademy.storyplugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StoryCommandNamesTest {
 @Test void koreanNicknamesWithSpacesAndModesDoNotConsumeTheId(){
  assertEquals(new StoryCommandNames.Play("별빛 마법사","ch1-1","auto"),StoryCommandNames.play(true,new String[]{"재생","별빛","마법사","ch1-1"}));
  assertEquals(new StoryCommandNames.Play("Account","intro","skip"),StoryCommandNames.play(false,new String[]{"재생","Account","intro","건너뛰기허용"}));
  assertEquals(new StoryCommandNames.Play("@a","intro","noskip"),StoryCommandNames.play(false,new String[]{"play","@a","intro","noskip"}));
 }
 @Test void malformedPlayCannotBecomeAnExecutableTargetOrPath(){
  assertThrows(IllegalArgumentException.class,()->StoryCommandNames.play(true,new String[]{"재생","Account"}));
  assertThrows(IllegalArgumentException.class,()->StoryCommandNames.play(true,new String[]{"재생","Account","../x"}));
  assertThrows(IllegalArgumentException.class,()->StoryCommandNames.play(true,new String[]{"중지","Account","intro"}));
 }
}
