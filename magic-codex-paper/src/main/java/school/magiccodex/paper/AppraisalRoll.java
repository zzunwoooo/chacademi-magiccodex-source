package school.magiccodex.paper;
import java.util.function.DoubleSupplier;
final class AppraisalRoll {
 record Result(int nodes,boolean broken){}
 static Result roll(double[] chances,double breakChance,DoubleSupplier random){
  if(chances.length!=10||!Double.isFinite(breakChance)||breakChance<0||breakChance>1)throw new IllegalArgumentException();
  for(double p:chances)if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException();
  for(int i=0;i<10;i++)if(random.getAsDouble()>=chances[i])return new Result(i,random.getAsDouble()<breakChance);
  return new Result(10,false);
 }
}
