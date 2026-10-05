package school.magiccodex.paper;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.SocialProtocol;

class NicknameStoreTest {
    @TempDir Path temp;
    @Test void persistByUuidAndKeepFriendTables()throws Exception{
        Path file=temp.resolve("friends.db");UUID owner=UUID.randomUUID(),friend=UUID.randomUUID();
        try(var friends=new FriendStore(file)){assertTrue(friends.add(owner,new SocialProtocol.Entry(friend,"친구","루미나",false)));}
        try(var store=new NicknameStore(file,new DatabaseSettings(false,"","",""))){assertNull(store.load(owner));var saved=store.save(owner,"Account","별빛",0);assertEquals(1,saved.revision());assertEquals("Account",saved.account());}
        try(var store=new NicknameStore(file,new DatabaseSettings(false,"","",""));var friends=new FriendStore(file)){assertEquals("별빛",store.load(owner).nickname());assertEquals(friend,friends.load(owner).getFirst().id());assertEquals(1,friends.load(owner).size());}
    }
    @Test void staleRevisionAndOtherOwnerCannotOverwrite()throws Exception{
        Path file=temp.resolve("friends.db");UUID a=UUID.randomUUID(),b=UUID.randomUUID();var settings=new DatabaseSettings(false,"","","");
        try(var first=new NicknameStore(file,settings);var second=new NicknameStore(file,settings)){
            first.save(a,"A","처음",0);first.save(b,"B","다른유저",0);
            assertEquals("변경",first.save(a,"A","변경",1).nickname());
            assertEquals("변경",second.save(a,"A","오래된요청",1).nickname());
            assertEquals("변경",second.save(a,"A","중복최초",0).nickname());
            assertEquals("다른유저",second.load(b).nickname());assertEquals(2,second.load(a).revision());
        }
    }
}
