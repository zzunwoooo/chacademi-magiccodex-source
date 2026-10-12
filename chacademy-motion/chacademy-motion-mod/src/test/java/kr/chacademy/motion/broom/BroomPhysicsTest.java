package kr.chacademy.motion.broom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BroomPhysicsTest {
 private BroomPhysics physics(){return new BroomPhysics(new BroomPhysics.Stats(1,.04f,10,.03f,1,.05f,1,120,60),0,0,0,0,1);}
 private BroomPhysics.Input input(boolean forward,boolean back,boolean shift,float pitch){return new BroomPhysics.Input(forward,back,false,false,false,shift,false,0,0,pitch,false,false,70);}
 @Test void downwardViewAndForwardStillDescendWithoutSneak(){var p=physics();for(int i=0;i<20;i++)p.step(input(true,false,false,45));assertTrue(p.vy<0);assertTrue(p.horizontalSpeed()>0);}
 @Test void sneakNoLongerAddsDownwardAcceleration(){var a=physics();var b=physics();for(int i=0;i<20;i++){a.step(input(true,false,false,0));b.step(input(true,false,true,0));}assertEquals(a.vy,b.vy);assertEquals(a.vz,b.vz);}
 @Test void brakingSlowsTheBroomWithoutAssigningANewDescentKey(){var p=physics();for(int i=0;i<30;i++)p.step(input(true,false,false,0));double speed=p.horizontalSpeed();for(int i=0;i<10;i++)p.step(input(false,true,false,0));assertTrue(p.horizontalSpeed()<speed);}
}
