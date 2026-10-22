package school.magiccodex.client;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class NameplatePolicyTest {
 private boolean visible(String rule,boolean own,boolean same,boolean sneaky,double distance){return NameplatePolicy.visible(false,false,false,true,false,false,false,sneaky,distance,rule,own,same);}
 @Test void normalAndSneakingDistances(){assertTrue(visible("ALWAYS",false,false,false,48*48));assertFalse(visible("ALWAYS",false,false,false,48*48+.01));assertTrue(visible("ALWAYS",false,false,true,32*32-.01));assertFalse(visible("ALWAYS",false,false,true,32*32));}
 @Test void teamsMatchVanillaIncludingViewerWithoutTeam(){assertFalse(visible("NEVER",false,false,false,1));assertTrue(visible("HIDE_FOR_OTHER_TEAMS",false,false,false,1));assertTrue(visible("HIDE_FOR_OTHER_TEAMS",true,true,false,1));assertFalse(visible("HIDE_FOR_OTHER_TEAMS",true,false,false,1));assertFalse(visible("HIDE_FOR_OWN_TEAM",true,true,false,1));assertTrue(visible("HIDE_FOR_OWN_TEAM",true,false,false,1));}
 @Test void selfCameraHudInvisibilityPassengersAndSpectatorsAreHidden(){for(int i=0;i<7;i++){boolean[] b=new boolean[7];b[i]=true;assertFalse(NameplatePolicy.visible(b[0],b[1],b[2],!b[3],b[4],b[5],b[6],false,1,"ALWAYS",false,false));}}
}
