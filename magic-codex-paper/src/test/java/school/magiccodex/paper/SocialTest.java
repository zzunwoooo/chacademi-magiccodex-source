package school.magiccodex.paper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import school.magiccodex.protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialTest {
    @TempDir Path dir;
    private static SocialProtocol.Entry friend(UUID id,String name){return new SocialProtocol.Entry(id,name,"루미나",true);}
    @Test void persistenceLimitDuplicateAndSelf()throws Exception{
        UUID owner=UUID.randomUUID();Path db=dir.resolve("friends.db");var peers=new ArrayList<UUID>();
        try(var store=new FriendStore(db)){
            assertFalse(store.add(owner,friend(owner,"self")));
            for(int i=0;i<50;i++){UUID id=UUID.randomUUID();peers.add(id);assertTrue(store.add(owner,friend(id,"friend"+i)));}
            assertFalse(store.add(owner,friend(peers.getFirst(),"renamed")));assertFalse(store.add(owner,friend(UUID.randomUUID(),"extra")));
            assertEquals(50,store.load(owner).size());assertTrue(store.load(peers.getFirst()).isEmpty(),"Adding is one-way");
            assertTrue(store.pendingSignal(owner));
        }
        try(var store=new FriendStore(db)){
            assertEquals(50,store.load(owner).size());assertFalse(store.remove(UUID.randomUUID(),peers.getFirst()));
            store.profile(peers.getFirst(),"newName","아르케온");var e=store.load(owner).getFirst();assertEquals("newName",e.name());assertEquals("아르케온",e.dormitory());
            assertTrue(store.remove(owner,peers.getFirst()));assertFalse(store.remove(owner,peers.getFirst()));assertTrue(store.add(owner,friend(UUID.randomUUID(),"replacement")));assertEquals(50,store.load(owner).size());
            store.signalDelivered(owner);assertFalse(store.pendingSignal(owner));store.remove(owner,peers.get(1));store.add(owner,friend(peers.get(1),"friend1"));assertFalse(store.pendingSignal(owner),"Re-adding must not manufacture another first-friend reward");
        }
    }
    @Test void queuedDoubleAddsAreIdempotent()throws Exception{
        var executor=Executors.newSingleThreadExecutor();UUID owner=UUID.randomUUID(),target=UUID.randomUUID();
        try(var store=new FriendStore(dir.resolve("queue.db"))){var a=executor.submit(()->store.add(owner,friend(target,"target")));var b=executor.submit(()->store.add(owner,friend(target,"target")));assertTrue(a.get());assertFalse(b.get());assertEquals(1,store.load(owner).size());}finally{executor.shutdown();}
    }
    @Test void ticketsBoundToOwnerTargetAndSingleUse(){
        var tickets=new WhisperTickets();UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),target=UUID.randomUUID();var t=tickets.issue(owner,target,100);
        assertFalse(tickets.consume(other,target,t.token(),200));assertFalse(tickets.consume(owner,other,t.token(),200));assertFalse(tickets.consume(owner,target,t.token()+1,200));
        assertTrue(tickets.consume(owner,target,t.token(),200));assertFalse(tickets.consume(owner,target,t.token(),200));
    }
    @Test void unboundCastCanSelectOneFriendAndExpires(){
        var tickets=new WhisperTickets();UUID owner=UUID.randomUUID(),target=UUID.randomUUID();var t=tickets.issue(owner,SocialProtocol.NONE,0);
        assertNotNull(tickets.bind(owner,target,10));assertNull(tickets.bind(owner,UUID.randomUUID(),20));
        assertFalse(tickets.consume(owner,target,t.token(),120000));assertNull(tickets.get(owner,120000));
        t=tickets.issue(owner,target,0);tickets.cancel(owner,t.token());assertNull(tickets.get(owner,1));
        tickets.issue(owner,target,0);tickets.remove(owner);assertNull(tickets.get(owner,1));
    }
    @Test void successfulSocialCastChargesExactlyOnceAndRejectsLockedEmptyCooldown(){
        var spell=new ManaSpells.Spell("wind_message","마법 바람의전언","magic.learned.wind_message",1,1000);var a=new ManaAccount(3,100,0);int[] opens={0};
        assertEquals(ManaProtocol.LOCKED,ManaCasting.attempt(a,spell,false,0,()->{opens[0]++;return true;}).status());
        assertEquals(ManaProtocol.OK,ManaCasting.attempt(a,spell,true,0,()->{opens[0]++;return true;}).status());assertEquals(2,a.current);
        assertEquals(ManaProtocol.COOLDOWN,ManaCasting.attempt(a,spell,true,10,()->{opens[0]++;return true;}).status());assertEquals(1,opens[0]);
        assertEquals(ManaProtocol.FAILED,ManaCasting.attempt(a,spell,true,1001,()->false).status());assertEquals(2,a.current);
        a.current=0;assertEquals(ManaProtocol.EMPTY,ManaCasting.attempt(a,spell,true,1002,()->true).status());
    }
    @Test void koreanMessagesAndFullListRoundTripAndStaySmall(){
        UUID id=UUID.randomUUID();String message="도서관 앞에서 만날까? /op는 그냥 글자예요";
        var request=new SocialProtocol.Request(SocialProtocol.SEND,42,id,89,message);assertEquals(request,SocialProtocol.request(SocialProtocol.encode(request)));
        var entries=new ArrayList<SocialProtocol.Entry>();for(int i=0;i<50;i++)entries.add(friend(UUID.randomUUID(),"player"+i));
        var r=new SocialProtocol.Response(SocialProtocol.SNAPSHOT,42,id,89,"하루","루미나",message,1000,entries);
        byte[] b=SocialProtocol.encode(r);assertTrue(b.length<SocialProtocol.MAX_BYTES);assertEquals(r,SocialProtocol.response(b));
    }
    @Test void badPayloadsAndControlTextCannotReachHandlers(){
        var r=new SocialProtocol.Request(SocialProtocol.SEND,1,UUID.randomUUID(),1,"hello");byte[] b=SocialProtocol.encode(r);
        for(int i=0;i<b.length;i++){byte[] shortBytes=Arrays.copyOf(b,i);assertThrows(IllegalArgumentException.class,()->SocialProtocol.request(shortBytes));}
        assertThrows(IllegalArgumentException.class,()->SocialProtocol.request(Arrays.copyOf(b,b.length+1)));
        assertThrows(IllegalArgumentException.class,()->SocialProtocol.request(new byte[SocialProtocol.MAX_BYTES+1]));
        for(String s:List.of(""," ","line\nbreak","\u00a7cRed","a\u202Eb","a".repeat(241)))assertThrows(IllegalArgumentException.class,()->SocialProtocol.cleanMessage(s));
        assertEquals("한글 테스트",SocialProtocol.cleanMessage(" 한글 테스트 "));
    }
}
