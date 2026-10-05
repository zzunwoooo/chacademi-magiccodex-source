package school.magiccodex.paper;
final class EnhancementRules {
 static double chance(double base,double bonus,int nodes,int hits,int misses){return misses==0&&hits==(1<<nodes)-1?Math.min(1,base+bonus):base;}
 static boolean hit(long elapsed,int target,int window,int latency){return school.magiccodex.protocol.EnhancementTiming.inWindow(elapsed-Math.clamp(latency,0,200),target,window);}
 static boolean success(double roll,double chance){return roll<chance;}
}
