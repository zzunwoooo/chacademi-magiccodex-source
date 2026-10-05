package school.magiccodex.paper;

import java.util.*;
import org.junit.jupiter.api.Test;
import school.magiccodex.protocol.StatsProtocol;
import school.magiccodex.protocol.StatsProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class StatsProtocolTest {
    private static final UUID ID=UUID.fromString("fedcba98-7654-4321-1234-56789abcdef0");
    private Profile profile(){return new Profile(ID,"Mage",5,"바람",24.0,null,40,12,34.0,180.0,7.5,62,200,"skin","signature",Collections.nCopies(6,new byte[]{1,2,3}));}
    @Test void remoteStatsRoundTripPreservesIdentityMissingFieldsAndEquipment(){
        var r=StatsProtocol.decodeResponse(StatsProtocol.encodeResponse(new Response(StatsProtocol.OPEN,7,ID,"",profile())));
        assertEquals(ID,r.target());assertEquals(7,r.session());assertEquals("Mage",r.profile().name());assertEquals(62,r.profile().learned());assertNull(r.profile().popularity());
        assertEquals(180,r.profile().maximum());assertEquals("바람",r.profile().dormitory());assertArrayEquals(new byte[]{1,2,3},r.profile().equipment().get(5));
    }
    @Test void requestsAreExactBoundedAndRejectTruncationTrailingDataAndUnknownActions(){
        for(int action=1;action<=4;action++){var r=new Request(action,19,ID);byte[] b=StatsProtocol.encodeRequest(r);assertEquals(r,StatsProtocol.decodeRequest(b));
            for(int n=0;n<b.length;n++){byte[] cut=Arrays.copyOf(b,n);assertThrows(IllegalArgumentException.class,()->StatsProtocol.decodeRequest(cut));}
            assertThrows(IllegalArgumentException.class,()->StatsProtocol.decodeRequest(Arrays.copyOf(b,b.length+1)));
        }
        assertThrows(IllegalArgumentException.class,()->StatsProtocol.encodeRequest(new Request(5,1,ID)));
        assertThrows(IllegalArgumentException.class,()->StatsProtocol.encodeRequest(new Request(1,0,ID)));
    }
    @Test void responseRejectsTruncationWrongTargetAndOversizedPayload(){
        byte[] b=StatsProtocol.encodeResponse(new Response(StatsProtocol.SNAPSHOT,3,ID,"",profile()));
        for(int i=0;i<b.length;i++){byte[] cut=Arrays.copyOf(b,i);assertThrows(IllegalArgumentException.class,()->StatsProtocol.decodeResponse(cut));}
        assertThrows(IllegalArgumentException.class,()->StatsProtocol.decodeResponse(Arrays.copyOf(b,b.length+1)));
        assertThrows(IllegalArgumentException.class,()->StatsProtocol.decodeResponse(new byte[30001]));
        assertThrows(IllegalArgumentException.class,()->StatsProtocol.encodeResponse(new Response(StatsProtocol.OPEN,3,UUID.randomUUID(),"",profile())));
    }
    @Test void missingExtrasAndNoticeRemainUnambiguous(){
        var r=new Response(StatsProtocol.NOTICE,3,ID,"해당 기능은 아직 준비 중입니다.",null);
        assertEquals(r,StatsProtocol.decodeResponse(StatsProtocol.encodeResponse(r)));
        assertThrows(IllegalArgumentException.class,()->new StatsService.Additional(10,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new StatsService.Additional(1,null,Double.NaN,null));
    }
    @Test void passiveSpellsStillCountAsDiscoveries()throws Exception{
        var file=java.nio.file.Files.createTempFile("stats-catalog",".yml");
        try{java.nio.file.Files.writeString(file,"spells:\n  passive:\n    enabled: false\n    permission: magic.learned.passive\n");
            var spells=ManaSpells.load(file.toFile());assertTrue(spells.byId.isEmpty());assertEquals(List.of("magic.learned.passive"),spells.discoveryPermissions);
        }finally{java.nio.file.Files.deleteIfExists(file);}
    }
}
