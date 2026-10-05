package school.magiccodex.paper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import school.magiccodex.protocol.AppraisalProtocol;
class AppraisalRollTest {
 @Test void newFormationDistribution()throws Exception{try(var input=getClass().getResourceAsStream("/core-appraisal.yml")){var cfg=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));double reach=1,mean=0;for(int i=1;i<=10;i++){reach*=cfg.getDouble("formation."+i);mean+=reach;}assertEquals(3.805574523,mean,1e-8);assertEquals(.0004147605,reach,1e-10);assertTrue(cfg.getDouble("formation.5")>cfg.getDouble("formation.4"));assertTrue(cfg.getDouble("formation.8")>cfg.getDouble("formation.7"));}}
 @Test void tenIsHardLimit(){double[] p=new double[10];java.util.Arrays.fill(p,1);var r=AppraisalRoll.roll(p,1,()->.5);assertEquals(10,r.nodes());assertFalse(r.broken());}
 @Test void ordinaryFailurePreservesPriorSuccess(){double[] p={1,1,1,0,1,1,1,1,1,1};var r=AppraisalRoll.roll(p,0,()->.5);assertEquals(3,r.nodes());assertFalse(r.broken());}
 @Test void destructionOnlyAfterFailedFormation(){double[] p={1,1,1,0,1,1,1,1,1,1};assertTrue(AppraisalRoll.roll(p,1,()->.5).broken());}
 @Test void invalidProbabilityRejected(){double[] p=new double[10];p[4]=Double.NaN;assertThrows(IllegalArgumentException.class,()->AppraisalRoll.roll(p,0,()->0));}
 @Test void packetRoundTripAndTrailingBytesRejected(){var r=new AppraisalProtocol.Response(14,2,7,true,false,2500,10000,1,"");byte[] b=AppraisalProtocol.encodeResponse(r);assertEquals(r,AppraisalProtocol.decodeResponse(b));assertThrows(IllegalArgumentException.class,()->AppraisalProtocol.decodeResponse(java.util.Arrays.copyOf(b,b.length+1)));assertThrows(IllegalArgumentException.class,()->AppraisalProtocol.decodeRequest(new byte[0]));}
 @Test void outcomeBoundsRejected(){assertThrows(IllegalArgumentException.class,()->AppraisalProtocol.encodeResponse(new AppraisalProtocol.Response(1,1,11,true,false,0,0,0,"")));}
 @Test void probabilitiesRoundTrip(){var ps=java.util.List.of(1d,.95,.88,.35,.85,.75,.22,.70,.55,.08);var r=new AppraisalProtocol.Response(14,1,3,true,false,1000,500,0,"",ps);assertEquals(r,AppraisalProtocol.decodeResponse(AppraisalProtocol.encodeResponse(r)));assertThrows(IllegalArgumentException.class,()->AppraisalProtocol.encodeResponse(new AppraisalProtocol.Response(1,0,0,false,false,0,0,0,"",java.util.List.of(.5))));}
}

