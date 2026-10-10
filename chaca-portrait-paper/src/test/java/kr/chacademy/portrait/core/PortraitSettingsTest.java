package kr.chacademy.portrait.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PortraitSettingsTest {
    private PortraitSettings settings(Map<String, String> environment) {
        var config = new YamlConfiguration();
        config.set("openai.api-key", "ignored-config-value");
        return new PortraitSettings(config, environment::get);
    }
    @Test void configKeyIsIgnoredWithoutEnvironmentKeys() {
        assertFalse(settings(Map.of()).hasKey());
    }
    @Test void portraitEnvironmentKeyTakesPriority() {
        assertEquals("portrait-test", settings(Map.of(
                "CHACAPORTRAIT_OPENAI_KEY", " portrait-test ",
                "CHACANPC_OPENAI_KEY", "npc-test")).apiKey);
    }
    @Test void blankPortraitKeyFallsBackToNpcEnvironment() {
        assertEquals("npc-test", settings(Map.of(
                "CHACAPORTRAIT_OPENAI_KEY", " ",
                "CHACANPC_OPENAI_KEY", " npc-test ")).apiKey);
        assertFalse(settings(Map.of("CHACANPC_OPENAI_KEY", " ")).hasKey());
    }
    @Test void packagedConfigLoadsBothModelPrices() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        var settings = new PortraitSettings(config, name -> null);
        var cost = new CostModel(settings.prices, settings.estimate);
        assertTrue(cost.hasPrices("gpt-image-2"));
        assertTrue(cost.hasPrices("gpt-image-1.5"));
        assertEquals(156_300, cost.reserveImage("gpt-image-1.5", "medium"));
    }
}
