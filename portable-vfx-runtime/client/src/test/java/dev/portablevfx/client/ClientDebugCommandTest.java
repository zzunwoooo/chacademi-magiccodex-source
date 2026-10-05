package dev.portablevfx.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

final class ClientDebugCommandTest {
    @Test void localCommandsLiveOnlyUnderExplicitDebugNamespace() {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        dispatcher.register(ClientCommands.debugRoot(null, null, null, null));
        assertEquals(Set.of("pvfxdebug"), dispatcher.getRoot().getChildren().stream()
                .map(node -> node.getName()).collect(Collectors.toSet()));
        var debug = dispatcher.getRoot().getChild("pvfxdebug");
        assertNotNull(debug.getCommand());
        assertEquals(Set.of("play", "playproof", "playhere", "playat", "launch", "follow",
                "groundlaunch", "burst", "finish", "impact", "status", "configerrors", "list",
                "active", "clear", "reload", "stop"), debug.getChildren().stream()
                .map(node -> node.getName()).collect(Collectors.toSet()));
        assertNull(dispatcher.getRoot().getChild("pvfxclient"));
        assertNull(dispatcher.getRoot().getChild("마법"), "Normal spell command belongs to the server");
        assertNull(debug.getChild("fireball"));
        assertNull(debug.getChild("bloom"), "Claude authored HDR bloom must not use obsolete global bloom");
    }
}
