package school.magiccodex.paper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import school.magiccodex.protocol.NameplateProtocol;
class NameplateProtocolTest {
 private NameplateProtocol.Entry e(int id,String n,String title){return new NameplateProtocol.Entry(id,UUID.randomUUID(),4,n,title);}
 @Test void roundTripUnicodeAndEmptySnapshot(){var v=List.of(e(7,"교수😀엘레나","변신술 교수"));assertEquals(v,NameplateProtocol.decode(NameplateProtocol.encode(v)));assertEquals(List.of(),NameplateProtocol.decode(NameplateProtocol.encode(List.of())));}
 @Test void validatesCodePointsNotUtf16Length(){assertDoesNotThrow(()->e(1,"😀".repeat(48),""));assertThrows(IllegalArgumentException.class,()->e(1,"😀".repeat(49),""));assertThrows(IllegalArgumentException.class,()->e(1,"\uD800",""));assertThrows(IllegalArgumentException.class,()->e(1,"\uDC00",""));}
 @Test void refusesControlAndFormatting(){for(String n:List.of("a\nb","a\u202Eb","§a엘레나","<name>",""))assertThrows(IllegalArgumentException.class,()->e(1,n,""));assertThrows(IllegalArgumentException.class,()->e(1,"엘레나","가".repeat(25)));}
 @Test void malformedPacketsFailClosed(){byte[] good=NameplateProtocol.encode(List.of(e(1,"이름","교수")));for(int size=0;size<good.length;size++){byte[] broken=Arrays.copyOf(good,size);assertThrows(IllegalArgumentException.class,()->NameplateProtocol.decode(broken));}byte[] extra=Arrays.copyOf(good,good.length+1);assertThrows(IllegalArgumentException.class,()->NameplateProtocol.decode(extra));good[3]=2;assertThrows(IllegalArgumentException.class,()->NameplateProtocol.decode(good));}
 @Test void duplicatesAndOversizedSnapshotsAreRejected(){var entry=e(1,"이름","");assertThrows(IllegalArgumentException.class,()->NameplateProtocol.decode(NameplateProtocol.encode(List.of(entry,entry))));assertThrows(IllegalArgumentException.class,()->NameplateProtocol.encode(Collections.nCopies(65,entry)));}
 @Test void worstCaseSnapshotFitsPluginMessageBudget(){var entries=new ArrayList<NameplateProtocol.Entry>();for(int i=0;i<64;i++)entries.add(e(i,"😀".repeat(48),"😀".repeat(24)));byte[] bytes=NameplateProtocol.encode(entries);assertTrue(bytes.length<=30000);assertEquals(entries,NameplateProtocol.decode(bytes));}
}
