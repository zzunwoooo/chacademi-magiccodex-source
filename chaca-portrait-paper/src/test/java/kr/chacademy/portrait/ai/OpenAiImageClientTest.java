package kr.chacademy.portrait.ai;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiImageClientTest {

    @Test
    void parsesImageAndUsage() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String body = "{\"created\":1,\"data\":[{\"b64_json\":\"" + b64 + "\"}],\"usage\":{\"input_tokens\":5000,"
                + "\"input_tokens_details\":{\"image_tokens\":4800,\"text_tokens\":200},\"output_tokens\":1584,\"total_tokens\":6584}}";
        var r = OpenAiImageClient.parseImage(body);
        assertEquals(4, r.png().length);
        assertEquals(200, r.usage().textInput());
        assertEquals(4800, r.usage().imageInput());
        assertEquals(1584, r.usage().output());
        assertTrue(r.usage().known());
    }

    @Test
    void missingUsageIsUnknown() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(new byte[]{1});
        var r = OpenAiImageClient.parseImage("{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}");
        assertFalse(r.usage().known());
        var e = assertThrows(OpenAiImageClient.ApiException.class, () -> OpenAiImageClient.parseImage("{\"data\":[]}"));
        assertTrue(e.billedUnknown);
    }

    @Test
    void parsesResponseText() throws Exception {
        String body = "{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"silver hair, blue tie\"}]}],"
                + "\"usage\":{\"input_tokens\":900,\"output_tokens\":40}}";
        var t = OpenAiImageClient.parseResponseText(body);
        assertEquals("silver hair, blue tie", t.text());
        assertEquals(900, t.inputTokens());
        assertEquals(40, t.outputTokens());
    }
}
