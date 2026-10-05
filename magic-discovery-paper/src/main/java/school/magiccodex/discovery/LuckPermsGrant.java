package school.magiccodex.discovery;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
final class LuckPermsGrant {
    static void grant(MagicDiscovery plugin,UUID id,String permission){var api=Bukkit.getServicesManager().load(LuckPerms.class);if(api==null)return;
        var loaded=api.getUserManager().getUser(id);if(loaded!=null&&loaded.getCachedData().getPermissionData().checkPermission(permission).asBoolean())return;
        api.getUserManager().modifyUser(id,user->user.data().add(Node.builder(permission).value(true).build())).exceptionally(e->{plugin.failure(e);return null;});
    }
}
