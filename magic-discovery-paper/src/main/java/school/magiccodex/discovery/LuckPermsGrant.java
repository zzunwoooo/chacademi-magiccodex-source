package school.magiccodex.discovery;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;

/** Serialize permission writes so a delayed grant cannot overwrite a newer admin removal. */
final class LuckPermsGrant {
    private final PermissionWriteQueue writes = new PermissionWriteQueue();

    CompletableFuture<Void> set(MagicDiscovery plugin, UUID id, String permission, boolean learned) {
        MagicDiscovery.main();
        var api = Bukkit.getServicesManager().load(LuckPerms.class);
        if (api == null) return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms service unavailable"));
        return writes.enqueue(id, () ->
                api.getUserManager().modifyUser(id, user -> {
                    user.data().remove(Node.builder(permission).value(true).build());
                    user.data().remove(Node.builder(permission).value(false).build());
                    user.data().add(Node.builder(permission).value(learned).build());
                }));
    }
}
