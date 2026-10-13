package kr.chacademy.motion.broom;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class BroomResponseTest {
 BroomPhysics make(float turn,float brake,float stability){return new BroomPhysics(new BroomPhysics.Stats(1,.04f,turn,brake,stability,.05f,.5f,120,60),0,0,0,.6,9);}
 BroomPhysics.Input in(float yaw,float pitch,boolean back){return new BroomPhysics.Input(!back,back,false,false,false,false,false,0,yaw,pitch,false,false,70);}
 @Test void pitchChangesClimbAndDive(){var up=make(10,.04f,1);var down=make(10,.04f,1);for(int i=0;i<15;i++){up.step(in(0,-40,false));down.step(in(0,40,false));}assertTrue(up.vy>0);assertTrue(down.vy<0);}
 @Test void turnAndBrakeStatsChangeActualMotion(){var quick=make(15,.12f,1);var slow=make(5,.02f,1);quick.step(in(60,0,false));slow.step(in(60,0,false));assertTrue(quick.heading>slow.heading);quick=make(10,.12f,1);slow=make(10,.02f,1);for(int i=0;i<5;i++){quick.step(in(0,0,true));slow.step(in(0,0,true));}assertTrue(quick.horizontalSpeed()<slow.horizontalSpeed());}
 @Test void stabilityAffectsRecoveryAndRecatchStartsLow(){var high=make(10,.04f,2);var low=make(10,.04f,.5f);high.balance=low.balance=20;high.step(in(0,0,false));low.step(in(0,0,false));assertTrue(high.balance>low.balance);high.caught();assertEquals(18,high.balance);}
}
