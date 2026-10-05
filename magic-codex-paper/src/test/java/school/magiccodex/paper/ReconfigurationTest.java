package school.magiccodex.paper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import school.magiccodex.protocol.*;
import java.util.*;
class ReconfigurationTest {
    @Test void restorationOnlyRemovesFailures(){assertEquals(4,ReconfigurationRules.restore(5,2,1));assertEquals(2,ReconfigurationRules.restore(3,2,10));assertEquals(2,ReconfigurationRules.restore(2,2,1));assertThrows(IllegalArgumentException.class,()->ReconfigurationRules.restore(1,2,1));}
    @Test void everyAffinityPreservesRelativeBudgetAndActuallyChanges(){double[] original={1,.5,.5};for(int affinity=0;affinity<3;affinity++){double total=0;for(int stat=0;stat<3;stat++)total+=original[stat]*CoreAffinity.multiplier(stat,affinity);assertEquals(2,total);for(int choice=0;choice<2;choice++)assertNotEquals(affinity,CoreAffinity.changed(affinity,choice));}assertEquals(1,CoreAffinity.multiplier(0,0));assertEquals(2,CoreAffinity.multiplier(2,2));}
    @Test void packetBoundsAndRoundtrip(){var r=new ReconfigurationProtocol.Response(1,1,0,0,"마법봉","",1,0,0,-1,5,2,10,4,List.of(24d,5d,1d),List.of(24d,5d,1d),true);byte[] bytes=ReconfigurationProtocol.response(r);assertEquals(r,ReconfigurationProtocol.response(bytes));assertThrows(IllegalArgumentException.class,()->ReconfigurationProtocol.response(Arrays.copyOf(bytes,bytes.length+1)));assertThrows(IllegalArgumentException.class,()->ReconfigurationProtocol.response(new byte[5000]));for(int n=0;n<bytes.length;n++){final int length=n;assertThrows(IllegalArgumentException.class,()->ReconfigurationProtocol.response(Arrays.copyOf(bytes,length)));}assertThrows(IllegalArgumentException.class,()->new ReconfigurationProtocol.Request(0,0,0));assertThrows(IllegalArgumentException.class,()->new ReconfigurationProtocol.Request(1,0,3));}
}
