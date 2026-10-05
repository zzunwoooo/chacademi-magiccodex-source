package school.magiccodex.discovery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import school.magiccodex.protocol.DiscoveryProtocol;
import static org.junit.jupiter.api.Assertions.*;
class DiscoveryTest {
    @TempDir Path dir;
    @Test void all62DefinitionsLoadWithDottedKeysAndExactCropConjunction()throws Exception{
        var d=new Definitions(Path.of("src/main/resources/discoveries.yml").toFile());assertEquals(62,d.spells.size());
        var crop=d.spells.get("harvest_wind");assertEquals(3,crop.requirements().size());assertEquals(12,crop.requirements().get("break.young_wheat"));
        assertFalse(Definitions.meets(crop,Map.of("break.young_wheat",36d)));
        assertTrue(Definitions.meets(crop,Map.of("break.young_wheat",12d,"break.young_carrots",12d,"break.young_potatoes",12d)));
        assertEquals(.1,d.spells.get("cross_breeze").chance());assertEquals(1,d.spells.get("wind_push").chance());
        assertFalse(d.spells.get("falling_star").prerequisites().contains("magic.learned.falling_star"));assertEquals(31,d.spells.get("falling_star").prerequisites().size());
        assertEquals(4,d.spells.get("trajectory_acceleration").requirements().size());
    }
    @Test void invalidDefinitionCannotGrantUnconditionally()throws Exception{
        var p=dir.resolve("bad.yml");Files.writeString(p,"spells:\n  test:\n    name: Test\n    permission: magic.test\n    icon: magic:test.png\n    requirements: {}\n");assertThrows(Exception.class,()->new Definitions(p.toFile()));
    }
    @Test void simultaneousDiscoverersProduceOneGlobalFirstAndDuplicateIsIdempotent()throws Exception{
        try(var db=new DiscoveryStore(dir.resolve("test.db"))){var a=UUID.randomUUID();var b=UUID.randomUUID();
            var first=db.acquire(a,"wind",Map.of("pickup.items",1000d),false);var second=db.acquire(b,"wind",Map.of(),false);
            assertTrue(first.get().acquisition().first());assertFalse(second.get().acquisition().first());assertEquals(0,second.get().acquisition().reward());
            var repeat=db.acquire(a,"wind",Map.of(),false).get();assertFalse(repeat.created());assertEquals(first.get().acquisition().token(),repeat.acquisition().token());assertEquals(1,db.load(a).get().acquired().size());
        }
    }
    @Test void progressAndPermissionClaimsSurviveRestartWithOwnerBoundAcknowledgement()throws Exception{
        var id=UUID.randomUUID();long token;Path path=dir.resolve("restart.db");
        try(var db=new DiscoveryStore(path)){db.save(id,Map.of("break.young_wheat",11d)).get();token=db.acquire(id,"spell",Map.of("break.young_wheat",12d),false).get().acquisition().token();db.acknowledge(UUID.randomUUID(),token).get();assertFalse(db.load(id).get().acquired().getFirst().notified());}
        try(var db=new DiscoveryStore(path)){var data=db.load(id).get();assertEquals(12,data.progress().get("break.young_wheat"));assertEquals(token,data.acquired().getFirst().token());db.acknowledge(id,token).get();assertTrue(db.load(id).get().acquired().getFirst().notified());}
    }
    @Test void rewardReservationCannotDuplicateAndWrongOwnerCannotClaim()throws Exception{
        var id=UUID.randomUUID();try(var db=new DiscoveryStore(dir.resolve("reward.db"))){long t=db.acquire(id,"spell",Map.of(),false).get().acquisition().token();
            assertFalse(db.reserve(UUID.randomUUID(),t).get());assertTrue(db.reserve(id,t).get());assertFalse(db.reserve(id,t).get());db.unreserve(id,t).get();assertTrue(db.reserve(id,t).get());db.delivered(id,t).get();assertFalse(db.reserve(id,t).get());assertEquals(3,db.load(id).get().acquired().getFirst().reward());
        }
    }
    @Test void perPlayerRewardSettingKeepsGlobalFirstDistinction()throws Exception{try(var db=new DiscoveryStore(dir.resolve("scope.db"))){db.acquire(UUID.randomUUID(),"spell",Map.of(),true).get();var a=db.acquire(UUID.randomUUID(),"spell",Map.of(),true).get().acquisition();assertFalse(a.first());assertEquals(1,a.reward());}}
    @Test void protocolRejectsTruncationTrailingDataAndTraversal(){
        var n=new DiscoveryProtocol.Notice(17,"harvest_wind","수확의 바람","magiccodex:textures/spells/test.png",true);byte[] b=DiscoveryProtocol.encode(n);assertEquals(n,DiscoveryProtocol.decode(b));
        for(int i=0;i<b.length;i++){byte[] cut=Arrays.copyOf(b,i);assertThrows(IllegalArgumentException.class,()->DiscoveryProtocol.decode(cut));}
        assertThrows(IllegalArgumentException.class,()->DiscoveryProtocol.decode(Arrays.copyOf(b,b.length+1)));
        assertThrows(IllegalArgumentException.class,()->DiscoveryProtocol.encode(new DiscoveryProtocol.Notice(1,"test","x","magic:../test.png",false)));
        assertEquals(0,DiscoveryProtocol.request(DiscoveryProtocol.request(0)));assertEquals(17,DiscoveryProtocol.request(DiscoveryProtocol.request(17)));
    }
    @Test void rotationsHandleYawWrapAndRejectBackAndForthShaking(){assertEquals(20,NativeConditions.wrap(-340));assertEquals(-20,NativeConditions.wrap(340));assertEquals(-15,NativeConditions.accumulate(350,-15));assertEquals(365,NativeConditions.accumulate(350,15));}
}
