package kr.chacademy.storyplugin;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class StoryCodecTest {
    static byte[] event(){var b=new ByteArrayOutputStream();for(String s:new String[]{"ch1_wakeup","c_wake_1","teacher"})StoryCodec.writeString(b,s);b.writeBytes(new byte[]{0,0,0,2});return b.toByteArray();}
    @Test void eventDecodesAndTruncationIsRejected(){
        byte[] bytes=event();assertEquals(new StoryCodec.DialogueEvent("ch1_wakeup","c_wake_1","teacher",2),StoryCodec.dialogueEvent(bytes));
        for(int i=0;i<bytes.length;i++){int size=i;assertThrows(IllegalArgumentException.class,()->StoryCodec.dialogueEvent(Arrays.copyOf(bytes,size)));}
    }
    @Test void trailingBytesAndOversizedStringsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->StoryCodec.dialogueEvent(Arrays.copyOf(event(),event().length+1)));
        var b=new ByteArrayOutputStream();StoryCodec.writeString(b,"x".repeat(257));
        assertThrows(IllegalArgumentException.class,()->StoryCodec.dialogueDone(b.toByteArray()));
    }
    @Test void progressRoundTrip(){
        var b=new ByteArrayOutputStream();StoryCodec.writeString(b,"ch1_wakeup");StoryCodec.writeString(b,"wake");StoryCodec.writeVarInt(b,12);
        assertEquals(new StoryCodec.DialogueProgress("ch1_wakeup","wake",12),StoryCodec.dialogueProgress(b.toByteArray()));
    }
}
