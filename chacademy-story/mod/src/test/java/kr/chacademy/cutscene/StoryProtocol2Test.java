package kr.chacademy.cutscene;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import kr.chacademy.cutscene.net.CutscenePackets;
import kr.chacademy.storyplugin.StoryCodec;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** 프로토콜 2 의 새 패킷이 플러그인 StoryCodec 과 바이트까지 같은지, 버전 숫자·문자열 한도가 양쪽에서 같은지. */
class StoryProtocol2Test {
    static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    static byte[] bytes(RegistryFriendlyByteBuf b){byte[] a=new byte[b.readableBytes()];b.readBytes(a);b.release();return a;}
    @Test void versionAndLimitsMatchThePlugin(){
        assertEquals(StoryCodec.PROTOCOL,CutscenePackets.PROTOCOL);
        assertEquals(StoryCodec.MAX_STRING,CutscenePackets.MAX_C2S_STRING);
        assertEquals(StoryCodec.STORY_HELLO,CutscenePackets.HelloS2C.TYPE.id().toString());
        assertEquals(StoryCodec.CUTSCENE_ABORT,CutscenePackets.AbortC2S.TYPE.id().toString());
        assertEquals(StoryCodec.STORY_FAIL,CutscenePackets.StoryFailC2S.TYPE.id().toString());
        assertEquals(StoryCodec.KIND_CUTSCENE,CutscenePackets.KIND_CUTSCENE);assertEquals(StoryCodec.KIND_DIALOGUE,CutscenePackets.KIND_DIALOGUE);
        // 서버는 모드가 듣는 채널 이름으로 버전을 안다
        assertEquals(CutscenePackets.PROTOCOL,StoryCodec.clientProtocol(List.of(CutscenePackets.PlayS2C.TYPE.id().toString(),CutscenePackets.HelloS2C.TYPE.id().toString())));
        assertTrue(StoryCodec.clientProtocol(List.of(CutscenePackets.PlayS2C.TYPE.id().toString()))<StoryCodec.MIN_CLIENT_PROTOCOL);
    }
    @Test void serverHelloIsReadByTheMod(){
        var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(StoryCodec.hello(StoryCodec.PROTOCOL)),RegistryAccess.EMPTY);
        try{assertEquals(StoryCodec.PROTOCOL,CutscenePackets.HelloS2C.CODEC.decode(b).protocol());assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void abortAndFailMatchServerCodec(){
        var a=buffer();CutscenePackets.AbortC2S.CODEC.encode(a,new CutscenePackets.AbortC2S("ch1_ashen_night"));
        assertEquals(new StoryCodec.CutsceneAbort("ch1_ashen_night"),StoryCodec.cutsceneAbort(bytes(a)));
        var f=buffer();CutscenePackets.StoryFailC2S.CODEC.encode(f,new CutscenePackets.StoryFailC2S(CutscenePackets.KIND_DIALOGUE,"ch1_wakeup",CutscenePackets.FAIL_MISSING));
        assertEquals(new StoryCodec.StoryFail(StoryCodec.KIND_DIALOGUE,"ch1_wakeup","missing"),StoryCodec.storyFail(bytes(f)));
        var d=buffer();CutscenePackets.DoneC2S.CODEC.encode(d,new CutscenePackets.DoneC2S("ch1_ashen_night",true));
        assertEquals(new StoryCodec.CutsceneDone("ch1_ashen_night",true),StoryCodec.cutsceneDone(bytes(d)));
    }
    @Test void overlongClientStringsAreClippedSoTheServerNeverRejectsThePacket(){
        String longKorean="가".repeat(1000);
        var p=new CutscenePackets.StoryFailC2S(CutscenePackets.KIND_CUTSCENE,"x",longKorean);
        assertEquals(CutscenePackets.MAX_C2S_STRING,p.reason().length());
        var b=buffer();CutscenePackets.StoryFailC2S.CODEC.encode(b,p);
        assertEquals("가".repeat(256),StoryCodec.storyFail(bytes(b)).reason());   // 256자 = 768바이트: 서버 한도와 같다
        var e=buffer();CutscenePackets.DialogueDoneC2S.CODEC.encode(e,new CutscenePackets.DialogueDoneC2S("x".repeat(300),null));
        assertEquals(new StoryCodec.DialogueDone("x".repeat(256),""),StoryCodec.dialogueDone(bytes(e)));
    }
}
