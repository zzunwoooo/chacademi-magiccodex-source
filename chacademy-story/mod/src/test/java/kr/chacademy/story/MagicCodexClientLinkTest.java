package kr.chacademy.story;
import org.junit.jupiter.api.Test;
import kr.chacademy.story.compat.MagicCodexClientLink;
import school.magiccodex.client.PortraitClient;
import static org.junit.jupiter.api.Assertions.*;
class MagicCodexClientLinkTest {
    @Test void publicNicknameAndPackagePrivatePortraitMethodsWork(){
        assertEquals("별빛",MagicCodexClientLink.nickname("Account"));
        assertTrue(MagicCodexClientLink.portraitReady());
        assertTrue(MagicCodexClientLink.drawPortrait1600(null));
        assertEquals(1,PortraitClient.draws);
    }
}
