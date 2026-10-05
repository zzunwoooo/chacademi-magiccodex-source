package school.magiccodex.paper;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import school.magiccodex.protocol.SeasonProtocol;

class ClimateTest {
    @Test void sevenMinecraftDaysAndFullYear(){
        var clock=new SeasonClock(1000,0,true,7*24000,1000);
        clock.observe(1000+7*24000-1);assertEquals(0,clock.season());assertEquals(7,clock.day());
        clock.observe(1000+7*24000);assertEquals(1,clock.season());assertEquals(1,clock.day());
        clock.observe(1000+28*24000);assertEquals(0,clock.season());
    }
    @Test void backwardsTimeDoesNotRewindSeason(){
        var c=new SeasonClock(0,0,true,168000,0);c.observe(200000);assertEquals(1,c.season());c.observe(1000);assertEquals(1,c.season());assertEquals(2,c.day());
        c.set(2,1000,false);c.observe(9999999);assertEquals(2,c.season());
    }
    @Test void extremesAndNamespacedBiomeOffsetFormula(){
        assertEquals(36,ClimateMath.temperature(22,36,6000,0,false,2));
        assertEquals(22,ClimateMath.temperature(22,36,18000,0,false,2));
        assertEquals(44,ClimateMath.temperature(22,36,6000,10,true,2));
        assertEquals(-100,ClimateMath.temperature(-90,-80,18000,-25,false,0));
    }
    @Test void computedTemperatureNeverOverwritesAdminOverride(){
        var f=new TemperatureFeed(18);UUID id=UUID.randomUUID();f.computed(id,30);assertEquals(30,f.get(id));
        f.set(id,-12);f.computed(id,35);assertEquals(-12,f.get(id));f.reset(id);assertEquals(35,f.get(id));f.remove(id);assertEquals(18,f.get(id));
    }
    @Test void seasonMultiplierDoesNotStackOrAlterEquipment(){
        var a=new ManaAccount(0,100,5);a.modifier("gear:wand",20,3);a.regenerationMultiplier("climate:regeneration",1.25);assertEquals(10,a.snapshot().regeneration());
        a.regenerationMultiplier("climate:regeneration",1.25);assertEquals(10,a.snapshot().regeneration());
        a.regenerationMultiplier("climate:regeneration",1);assertEquals(8,a.snapshot().regeneration());assertEquals(120,a.snapshot().maximum());
    }
    @Test void seasonWireRejectsInvalidPackets(){for(int i=0;i<4;i++)assertEquals(i,SeasonProtocol.decode(SeasonProtocol.encode(i)));assertThrows(IllegalArgumentException.class,()->SeasonProtocol.decode(new byte[100]));assertThrows(IllegalArgumentException.class,()->SeasonProtocol.encode(4));}
}
