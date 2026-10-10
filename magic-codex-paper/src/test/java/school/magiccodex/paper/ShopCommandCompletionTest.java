package school.magiccodex.paper;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShopCommandCompletionTest {
    private static final String A="11111111-1111-1111-1111-111111111111";
    private static final String B="22222222-2222-2222-2222-222222222222";
    private static final String USER="33333333-3333-3333-3333-333333333333";
    private final Map<String,ShopStore.Shop> shops=Map.of(
        "alpha",new ShopStore.Shop("alpha","A","elena-neutral",1,List.of(
            new ShopStore.Product(A,"Item A",new byte[0],"100","50"))),
        "beta",new ShopStore.Shop("beta","B","other-portrait",1,List.of(
            new ShopStore.Product(B,"Item B",new byte[0],"200",""))));
    private List<String> complete(String command,boolean player,boolean use,boolean admin,String... args) {
        return ShopCommandCompletion.complete(command,args,player,use,admin,shops,
            List.of("citizens:12","tag:merchant","citizens:12"),List.of(USER));
    }
    @Test void shopIdsRequirePlayerAndPermission() {
        assertEquals(List.of("alpha","beta"),complete("상점",true,true,true,""));
        assertEquals(List.of(),complete("상점",true,true,false,"")); // D-10: /상점 <ID> 는 관리자 전용
        assertEquals(List.of(),complete("상점",true,false,true,""));
        assertEquals(List.of(),complete("상점",false,true,true,""));
    }
    @Test void adminCannotLeakWithoutPermission() {
        assertEquals(List.of(),complete("상점관리",true,true,false,""));
        assertEquals(List.of(),complete("상점관리",true,true,false,"가격","alpha",""));
    }
    @Test void KoreanCommandsAndPrefixesResolveDeterministically() {
        assertEquals(List.of("alpha"),complete("상점",true,true,true,"AL"));
        assertEquals(List.of(),complete("codexshop",true,true,false,""));
        assertEquals(List.of(),complete("codexshopadmin",true,true,true,""));
        assertEquals(List.of("NPC"),complete("상점관리",true,false,true,"np"));
        assertEquals(List.of("beta"),complete("상점관리",true,true,true,"목록","b"));
    }
    @Test void consoleOnlyGetsUsableSubcommands() {
        assertFalse(complete("상점관리",false,false,true,"").contains("아이템추가"));
        assertTrue(complete("상점관리",true,false,true,"").contains("아이템추가"));
        assertEquals(List.of(),complete("상점관리",false,false,true,"아이템추가",""));
    }
    @Test void productIdsBelongToSelectedShop() {
        assertEquals(List.of(A),complete("상점관리",true,true,true,"가격","alpha",""));
        assertEquals(List.of(B),complete("상점관리",true,true,true,"가격","beta",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"가격","alpha",B,""));
    }
    @Test void priceCandidatesUseCurrentProductAndOff() {
        assertEquals(List.of("0","100","off"),complete("상점관리",true,true,true,"가격","alpha",A,""));
        assertEquals(List.of("0","50","off"),complete("상점관리",true,true,true,"가격","alpha",A,"100",""));
        assertEquals(List.of("off"),complete("상점관리",true,true,true,"아이템추가","beta","o"));
    }
    @Test void invalidEarlierPriceNeverCompletesNextArgument() {
        for(String bad:List.of("-1","NaN","0.1234567","1000000000001","nonsense")) {
            assertEquals(List.of(),complete("상점관리",true,true,true,"가격","alpha",A,bad,""));
            assertEquals(List.of(),complete("상점관리",true,true,true,"아이템추가","alpha",bad,""));
        }
    }
    @Test void cachedNpcKeysAreDeduplicatedAndFiltered() {
        assertEquals(List.of("citizens:12"),complete("상점관리",true,true,true,"NPC","alpha","c"));
        assertEquals(List.of("tag:merchant"),complete("상점관리",true,true,true,"NPC","alpha","tag:"));
    }
    @Test void portraitsComeFromKnownCatalogAndBuiltinAsset() {
        assertEquals(List.of("elena-neutral","other-portrait"),complete("상점관리",true,true,true,"초상","alpha",""));
    }
    @Test void auditUsesOnlineUuidAndFreeCreateFieldsAreEmpty() {
        assertEquals(List.of(USER),complete("상점관리",false,false,true,"기록",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"생성",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"생성","new_id",""));
    }
    @Test void resolveCompletesListedOrdersThenActions() {
        String order="44444444-4444-4444-4444-444444444444";
        var listed=ShopCommandCompletion.complete("상점관리",new String[]{"처리",""},false,false,true,shops,List.of(),List.of(),List.of(order));
        assertEquals(List.of(order),listed);
        assertEquals(List.of("완료","취소","환불"),complete("상점관리",false,false,true,"처리",order,""));
        assertEquals(List.of(),complete("상점관리",false,false,true,"처리",order,"완료",""));
        assertEquals(List.of(),ShopCommandCompletion.complete("상점관리",new String[]{"처리",""},true,true,false,shops,List.of(),List.of(),List.of(order)));
    }
    @Test void malformedUnknownAndExtraArgumentsReturnEmptyNeverNull() {
        assertEquals(List.of(),complete("상점관리",true,true,true,"가격","missing",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"bogus",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"목록","alpha",""));
        assertEquals(List.of(),complete("상점",true,true,true,"alpha",""));
        assertEquals(List.of(),complete("상점관리",true,true,true,"가격","alpha",A,"0","0","extra"));
        assertEquals(List.of(),ShopCommandCompletion.complete("상점관리",null,true,true,true,shops,List.of(),List.of()));
        assertEquals(List.of(),complete("상점관리",true,true,true,(String)null));
        assertEquals(List.of(),complete("상점관리",true,true,true));
    }
    @Test void suggestionsAreBounded() {
        var many=new HashMap<String,ShopStore.Shop>();
        for(int i=0;i<300;i++)many.put("shop"+i,shops.get("alpha"));
        assertEquals(256,ShopCommandCompletion.complete("상점",new String[]{""},true,true,true,many,List.of(),List.of()).size());
    }
}
