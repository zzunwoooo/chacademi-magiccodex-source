import kr.chacademy.motion.broom.BroomPhysics;
import java.util.*;
public class BroomFlightSimulation {
 public static void main(String[] args){
  System.out.println("profile,mastery,stability,samples,mean_seconds,min_seconds,max_seconds,censored_at_180");
  for(String profile:new String[]{"smooth","ordinary-turns","rough-weather"})for(float mastery:new float[]{0,.5f,1}){
   double total=0,min=180,max=0;int censored=0;
   for(int seed=0;seed<30;seed++){
    Random rng=new Random(seed);float stability=1.3f;
    var p=new BroomPhysics(new BroomPhysics.Stats(.8f,.028f,8,.03f,stability,.045f,mastery,120,60),0,0,0,.8,seed);
    float yaw=0;int next=140+rng.nextInt(60),ticks=0;
    for(;ticks<3600&&!p.fellOff();ticks++){
     if(!profile.equals("smooth")&&ticks==next){yaw+=(rng.nextBoolean()?1:-1)*(65+rng.nextInt(21));next+=140+rng.nextInt(60);}
     p.step(new BroomPhysics.Input(true,false,false,false,false,false,false,0,yaw,0,profile.equals("rough-weather"),false,70));
    }
    double sec=ticks/20.0;total+=sec;min=Math.min(min,sec);max=Math.max(max,sec);if(!p.fellOff())censored++;
   }
   System.out.printf(Locale.ROOT,"%s,%.1f,1.3,30,%.2f,%.2f,%.2f,%d%n",profile,mastery,total/30,min,max,censored);
  }
 }
}
