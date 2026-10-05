package school.magiccodex.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogueTest {
    @TempDir Path dir;
    private DialogueDefinition sample()throws Exception{return DialogueDefinition.decode("elena",Files.readString(Path.of("src/main/resources/dialogues/elena.yml")));}
    @Test void graphsRoundTripAndConditions()throws Exception{
        var d=sample();assertEquals(d,DialogueDefinition.decode(d.id(),d.encode()));assertEquals(DialogueDefinition.revision(d),DialogueDefinition.revision(DialogueDefinition.decode(d.id(),d.encode())));
        var fields=new HashMap<>(d.fields());fields.put("node.start.choice.0.next","missing");assertThrows(IllegalArgumentException.class,()->DialogueDefinition.fromFields("elena",fields));
        assertTrue(DialogueDefinition.matches(List.of("story clue active","not-flag met yes","permission dialogue.use"),Map.of("story.clue","active"),p->p.equals("dialogue.use")));
        assertFalse(DialogueDefinition.matches(List.of("flag met yes"),Map.of(),p->true));
        assertFalse(DialogueDefinition.matches(List.of("story clue active"),Map.of("story.clue","completed"),p->true));
        assertTrue(DialogueDefinition.matches(List.of("quest patrol completed","custom circle 3"),Map.of(),p->false,(key,arg)->key.equals("quest:patrol")&&arg.equals("completed")||key.equals("custom:circle")&&arg.equals("3")));
        assertFalse(DialogueDefinition.matches(List.of("custom unavailable arg"),Map.of(),p->true));
        var arden=DialogueDefinition.decode("arden",Files.readString(Path.of("src/main/resources/dialogues/arden.yml")));assertTrue(arden.nodes().containsKey("completed"));
    }
    @Test void protocolRejectsTruncationSpoofsAndTrailingBytes(){
        var request=new DialogueProtocol.Request(UUID.randomUUID().toString(),3,"c1",false);byte[] packet=DialogueProtocol.encode(request);assertEquals(request,DialogueProtocol.request(packet));
        assertThrows(IllegalArgumentException.class,()->DialogueProtocol.request(Arrays.copyOf(packet,packet.length-1)));
        assertThrows(IllegalArgumentException.class,()->DialogueProtocol.request(Arrays.copyOf(packet,packet.length+1)));
        assertThrows(IllegalArgumentException.class,()->DialogueProtocol.request(DialogueProtocol.encode(new DialogueProtocol.Request("fake",0,"command op",false))));
    }
    @Test void crossConnectionFencingAndOnceOnlyEffects()throws Exception{
        var settings=new DatabaseSettings(false,"","","");UUID player=UUID.randomUUID();Path db=dir.resolve("dialogue.db");
        try(var a=new DialogueStore(settings,db);var b=new DialogueStore(settings,db)){
            var before=a.state(player);var stale=b.state(player);
            var first=a.transition(player,before,"elena:start:c0",List.of("story clue active","command give {player} diamond 1"));
            assertEquals("active",b.state(player).values().get("story.clue"));assertEquals(1,a.audit(player).size());
            assertThrows(IllegalStateException.class,()->b.transition(player,stale,"elena:start:c0",List.of("command give {player} diamond 1")));
            assertThrows(IllegalStateException.class,()->b.transition(player,b.state(player),"elena:start:c0",List.of("command give {player} diamond 1")));
            a.finish(player,first.receipt());
            var repeat=b.transition(player,b.state(player),"elena:start:c0",List.of("command give {player} diamond 1"));assertTrue(repeat.external().isEmpty());assertTrue(a.audit(player).isEmpty());
            assertThrows(java.sql.SQLException.class,()->a.finish(player,first.receipt()));
        }
        try(var reopened=new DialogueStore(settings,db)){assertEquals("active",reopened.state(player).values().get("story.clue"));}
    }
    @Test void administratorsCannotOverwriteEachOther()throws Exception{
        try(var store=new DialogueStore(new DatabaseSettings(false,"","",""),dir.resolve("catalog.db"))){var d=sample();assertTrue(store.edit(d.id(),"",d));assertFalse(store.edit(d.id(),"",d));var f=new HashMap<>(d.fields());f.put("title","새 제목");var edit=DialogueDefinition.fromFields(d.id(),f);assertTrue(store.edit(d.id(),DialogueDefinition.revision(d),edit));assertFalse(store.edit(d.id(),DialogueDefinition.revision(d),d));assertFalse(store.edit(d.id(),DialogueDefinition.revision(d),null));assertEquals("새 제목",store.catalog().get(d.id()).title());}
    }
    @Test void sampleDocumentsFitNetworkEnvelope()throws Exception{var d=sample();var r=new DialogueAdminProtocol.Response("",List.of(),d.id(),DialogueDefinition.revision(d),d.fields());assertEquals(r,DialogueAdminProtocol.response(DialogueAdminProtocol.encode(r)));}
}
