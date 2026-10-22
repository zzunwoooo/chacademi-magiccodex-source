package kr.chacademy.storyplugin;

import io.netty.buffer.Unpooled;
import java.util.List;
import kr.chacademy.story.quest.QuestPackets;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestWireCompatibilityTest {
    private static RegistryFriendlyByteBuf buffer(byte[] bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
    }
    @Test void serverStepDecodesInClientWithUnicodeLocationAndNpc() {
        String npc="12345678-1234-1234-1234-123456789abc";
        var server=new QuestCodec.StepView(128,"ch1-6",129,"1장","선착장 포탈",true,
                List.of(new QuestCodec.CondView("교복",127,128)),QuestCodec.TARGET_LOCATION,"world",1.25,64,-12.5,2,npc);
        var b=buffer(QuestCodec.step(server));
        var client=QuestPackets.StepS2C.CODEC.decode(b);
        assertEquals(new QuestPackets.StepS2C(128,"ch1-6",129,"1장","선착장 포탈",true,
                List.of(new QuestPackets.Cond("교복",127,128)),QuestPackets.TARGET_LOCATION,"world",1.25,64,-12.5,2,npc),client);
        assertEquals(0,b.readableBytes());
        b.release();
    }
    @Test void serverUpdateAndHiddenStepDecodeInClient() {
        var b=buffer(QuestCodec.update(128,"ch1-19",129,false,new int[]{0,127,128,10000}));
        var client=QuestPackets.UpdateS2C.CODEC.decode(b);
        assertEquals(128,client.session()); assertEquals("ch1-19",client.step());
        assertEquals(129,client.run()); assertFalse(client.ready());
        assertArrayEquals(new int[]{0,127,128,10000},client.current()); assertEquals(0,b.readableBytes()); b.release();
        var empty=buffer(QuestCodec.step(new QuestCodec.StepView(1,"",0,"","",false,List.of(),0,"",0,0,0,0,"")));
        assertEquals("",QuestPackets.StepS2C.CODEC.decode(empty).step()); assertEquals(0,empty.readableBytes()); empty.release();
    }
}
