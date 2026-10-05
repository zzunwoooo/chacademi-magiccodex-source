package school.magiccodex.paper;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.TitleProtocol;

class TitleTest {
    @TempDir Path dir;
    static final DatabaseSettings SQLITE=new DatabaseSettings(false,"","","");
    static final Map<String,TitleDefinition> CATALOG=Map.of("prefix_star",new TitleDefinition("prefix_star",0,"별빛을 발견한",0x96e4ec,true,false),"suffix_student",new TitleDefinition("suffix_student",1,"학생",0xdec58e,true,true),"suffix_research",new TitleDefinition("suffix_research",1,"연구가",0xdec58e,true,false));
    static final Set<String> DEFAULTS=Set.of("suffix_student");
    @Test void ownershipBothSidesAndReconnect()throws Exception{var p=UUID.randomUUID();var path=dir.resolve("titles.db");
        try(var s=new TitleStore(SQLITE,path)){var first=s.load(p,DEFAULTS);assertEquals(DEFAULTS,first.owned());assertFalse(s.select(p,first.revision(),"prefix_star","",CATALOG,DEFAULTS).applied());
            var granted=s.grant(p,"prefix_star",DEFAULTS);assertFalse(s.select(p,first.revision(),"prefix_star","suffix_student",CATALOG,DEFAULTS).applied());
            assertFalse(s.select(p,granted.revision(),"suffix_student","prefix_star",CATALOG,DEFAULTS).applied());assertFalse(s.select(p,granted.revision(),"forged","",CATALOG,DEFAULTS).applied());
            var selected=s.select(p,granted.revision(),"prefix_star","suffix_student",CATALOG,DEFAULTS);assertTrue(selected.applied());assertEquals("prefix_star",selected.state().prefix());assertEquals("suffix_student",selected.state().suffix());}
        try(var wild=new TitleStore(SQLITE,path)){var saved=wild.load(p,DEFAULTS);assertEquals("prefix_star",saved.prefix());assertEquals("suffix_student",saved.suffix());assertTrue(wild.select(p,saved.revision(),"","",CATALOG,DEFAULTS).applied());}
    }
    @Test void revokeSelectedDefaultDoesNotRegrantAndGrantIsIdempotent()throws Exception{var p=UUID.randomUUID();try(var s=new TitleStore(SQLITE,dir.resolve("titles.db"))){var first=s.load(p,DEFAULTS);var a=s.select(p,first.revision(),"","suffix_student",CATALOG,DEFAULTS);assertTrue(a.applied());long revision=a.state().revision();assertEquals(revision,s.grant(p,"suffix_student",DEFAULTS).revision());var removed=s.revoke(p,"suffix_student",DEFAULTS);assertEquals("",removed.suffix());assertFalse(s.load(p,DEFAULTS).owned().contains("suffix_student"));assertEquals(removed.revision(),s.revoke(p,"suffix_student",DEFAULTS).revision());}}
    @Test void staleOtherServerCannotOverwriteAndDisabledCannotApply()throws Exception{var p=UUID.randomUUID();var path=dir.resolve("titles.db");try(var school=new TitleStore(SQLITE,path);var wild=new TitleStore(SQLITE,path)){
        var a=school.grant(p,"prefix_star",DEFAULTS);var b=wild.load(p,DEFAULTS);assertTrue(school.select(p,a.revision(),"prefix_star","suffix_student",CATALOG,DEFAULTS).applied());assertFalse(wild.select(p,b.revision(),"","",CATALOG,DEFAULTS).applied());var current=wild.load(p,DEFAULTS);assertEquals("prefix_star",current.prefix());var disabled=new HashMap<>(CATALOG);disabled.put("prefix_star",new TitleDefinition("prefix_star",0,"별빛을 발견한",1,false,false));assertFalse(wild.select(p,current.revision(),"prefix_star","",disabled,DEFAULTS).applied());}}
    @Test void nicknameAndDefinitionArePlainAndComposeCleanly(){assertEquals("별빛을 발견한 연구가 차카",TitleDefinition.composed("별빛을 발견한","차카","연구가"));assertEquals("차카",TitleDefinition.composed("","차카",""));assertThrows(IllegalArgumentException.class,()->new TitleDefinition("bad",0,"%user_nickname%",1,true,false));assertThrows(IllegalArgumentException.class,()->new TitleDefinition("bad",2,"명칭",1,true,false));}
    @Test void boundedProtocolRejectsTrailingForgedSidesAndOversizedLists(){var r=new TitleProtocol.Request(TitleProtocol.APPLY,1,UUID.randomUUID().toString(),3,"prefix_star","suffix_student");assertEquals(r,TitleProtocol.request(TitleProtocol.encode(r)));var reply=new TitleProtocol.Response(true,1,r.token(),3,"차카",r.prefix(),r.suffix(),"적용 완료",List.of(new TitleProtocol.Entry("prefix_star",0,"별빛을 발견한",0x96e4ec)));assertEquals(reply,TitleProtocol.response(TitleProtocol.encode(reply)));byte[] bytes=TitleProtocol.encode(r);assertThrows(IllegalArgumentException.class,()->TitleProtocol.request(Arrays.copyOf(bytes,bytes.length+1)));assertThrows(IllegalArgumentException.class,()->TitleProtocol.request(new byte[40000]));assertThrows(IllegalArgumentException.class,()->TitleProtocol.encode(new TitleProtocol.Request(1,2,"",0,"","")));assertThrows(IllegalArgumentException.class,()->TitleProtocol.encode(new TitleProtocol.Response(false,1,r.token(),0,"차카","","","",List.of(new TitleProtocol.Entry("x",2,"칭호",1)))));}
}
