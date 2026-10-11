package school.magiccodex.paper;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;

/** Sends only server-owned display names over the player's active backend connection. */
final class PartyIdentityBridge implements AutoCloseable {
    private static final String CHANNEL="chacademi:party_identity";
    private final MagicCodexBridge plugin;
    private final BukkitTask task;
    PartyIdentityBridge(MagicCodexBridge plugin){
        this.plugin=plugin;Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,CHANNEL);
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(var player:Bukkit.getOnlinePlayers())try{
                var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(1);out.writeUTF(plugin.names().name(player));
                player.sendPluginMessage(plugin,CHANNEL,bytes.toByteArray());
            }catch(IOException impossible){throw new IllegalStateException(impossible);}
        },20,40);
    }
    @Override public void close(){task.cancel();Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin,CHANNEL);}
}
