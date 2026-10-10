package kr.chacademy.storyplugin;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class StoryPermissionsTest {
    @Test void allServerCommandsRequireOperatorPermission(){
        var in=getClass().getResourceAsStream("/plugin.yml");assertNotNull(in);
        var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8));
        for(String command:new String[]{"cutscene","storydialogue","affinity"})
            assertEquals("chacademy.story.admin",yaml.getString("commands."+command+".permission"));
        assertEquals("op",yaml.getString("permissions.chacademy.story.admin.default"));
    }
}
