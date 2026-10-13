package school.magiccodex.client;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import java.util.*;import school.magiccodex.protocol.JournalProtocol.Entry;
class JournalTreeTest {
 Entry e(String id,boolean main,String state){return new Entry("story",id,id,"",state,main,"","");}
 @Test void independentGroupsAndChaptersPreserveUnrelatedExpansion(){var rows=List.of(e("a",true,"active"),e("b",true,"active"),e("c",false,"active"));var all=JournalTree.rows(rows,false,k->k.endsWith("a")?"1장":"2장",Set.of());assertEquals(7,all.size());var one=JournalTree.rows(rows,false,k->k.endsWith("a")?"1장":"2장",Set.of("main:1장"));assertTrue(one.stream().noneMatch(x->x.key().equals("story:a")));assertTrue(one.stream().anyMatch(x->x.key().equals("story:b")));assertTrue(one.stream().anyMatch(x->x.key().equals("story:c")));}
 @Test void legacyMissingChapterAndCompletedFilterRemainUsable(){var rows=List.of(e("a",true,"claimed"),e("b",true,"active"));var all=JournalTree.rows(rows,true,k->"",Set.of());assertEquals(3,all.size());assertEquals("story:a",all.get(1).key());assertEquals(1,all.get(1).depth());}
 @Test void hotbarColumnsExactlyMatchAndOffhandNeverShiftsThem(){for(int i=0;i<9;i++)assertEquals(ChacaInventoryLayout.element(9+i).cx(),ChacaInventoryLayout.element(36+i).cx());assertTrue(ChacaInventoryLayout.element(45).cx()>ChacaInventoryLayout.element(44).cx()+128);var f=ChacaInventoryLayout.fit(1516,857,false);assertTrue(1718*f.scale()<=1516*.611);assertTrue(941*f.scale()<=857*.651);assertEquals(1516*.478,f.screenX(859),.01);}
}
