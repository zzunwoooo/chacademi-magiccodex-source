package school.magiccodex.paper;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import school.magiccodex.database.DatabaseSettings;import school.magiccodex.protocol.ShopProtocol;
class ShopStoreTest {
 @TempDir Path dir;ShopStore store;UUID owner=UUID.randomUUID();String product;
 @BeforeEach void setup()throws Exception{store=new ShopStore(DatabaseSettings.load(dir.resolve("missing.properties")),dir.resolve("shop.db"));store.create("general","일반 상점");product=store.add("general","커스텀 아이템",new byte[]{7,8},"10","5");}
 @AfterEach void close()throws Exception{store.close();}
 @Test void quantityRejectsZeroNegativeNonNumericAndOverflow(){for(String value:List.of("0","-1","x","2147483648","577","1.5",""))assertThrows(IllegalArgumentException.class,()->ShopProtocol.quantity(value));assertEquals(9,ShopProtocol.quantity("9"));}
 @Test void staleRevisionCannotPreparePayment()throws Exception{long rev=store.catalog().get("general").revision();store.prices("general",product,"20","5");assertThrows(Exception.class,()->store.prepare(owner,UUID.randomUUID().toString(),"general",rev,product,1,false));}
 @Test void heldOrderBlocksCrossConnectionAndNoDuplicateRequest()throws Exception{long rev=store.catalog().get("general").revision();String key=UUID.randomUUID().toString();var order=store.prepare(owner,key,"general",rev,product,2,false);try(var other=new ShopStore(DatabaseSettings.load(dir.resolve("missing.properties")),dir.resolve("shop.db"))){assertThrows(Exception.class,()->other.prepare(owner,UUID.randomUUID().toString(),"general",rev,product,2,false));}store.state(order.id(),"prepared","paid");assertEquals(1,store.paid(owner).size());assertEquals("20",order.total());store.state(order.id(),"paid","done");assertThrows(Exception.class,()->store.prepare(owner,key,"general",rev,product,2,false));}
 @Test void disabledBuyingAndSellingAreServerValidated()throws Exception{store.prices("general",product,"","");long rev=store.catalog().get("general").revision();assertThrows(Exception.class,()->store.prepare(owner,UUID.randomUUID().toString(),"general",rev,product,1,false));assertThrows(Exception.class,()->store.prepare(owner,UUID.randomUUID().toString(),"general",rev,product,1,true));}
 @Test void fullOriginalPayloadIsRetained()throws Exception{assertArrayEquals(new byte[]{7,8},store.catalog().get("general").products().getFirst().bytes());}
 @Test void catalogVersionAndServerScopedBindingUpdate()throws Exception{long v=store.version();store.bind("server-a","citizens:1","general");assertTrue(store.version()>v);assertEquals("general",store.bindings("server-a").get("citizens:1"));assertTrue(store.bindings("server-b").isEmpty());}
 @Test void invalidPriceAndTotalOverflowAreRejected()throws Exception{assertThrows(Exception.class,()->ShopStore.price("-1"));assertThrows(Exception.class,()->ShopStore.price("NaN"));store.prices("general",product,"1000000000000","5");long rev=store.catalog().get("general").revision();assertThrows(Exception.class,()->store.prepare(owner,UUID.randomUUID().toString(),"general",rev,product,2,false));}
}
