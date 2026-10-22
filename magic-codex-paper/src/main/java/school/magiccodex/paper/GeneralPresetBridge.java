package school.magiccodex.paper;
import java.util.*;import java.util.function.Predicate;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.PlayerQuitEvent;import org.bukkit.persistence.PersistentDataType;import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.GeneralPresetProtocol;
/** Main-thread gate shared by the Z key and settings button. No client cooldown assertion is accepted. */
final class GeneralPresetBridge implements PluginMessageListener,Listener,AutoCloseable {
 private final MagicCodexBridge plugin;private final ManaService mana;private final Predicate<UUID> casting;private final NamespacedKey key;
 private final Map<UUID,Long> sequence=new HashMap<>(),next=new HashMap<>();
 GeneralPresetBridge(MagicCodexBridge p,ManaService mana,Predicate<UUID> casting){plugin=p;this.mana=mana;this.casting=casting;key=new NamespacedKey(p,"normal_magic_preset");var m=p.getServer().getMessenger();m.registerIncomingPluginChannel(p,GeneralPresetProtocol.REQUEST,this);m.registerOutgoingPluginChannel(p,GeneralPresetProtocol.RESPONSE);p.getServer().getPluginManager().registerEvents(this,p);}
 static boolean combat(UUID id){var plugin=Bukkit.getPluginManager().getPlugin("ChacaKyudichi");if(plugin==null||!plugin.isEnabled())return false;try{return (Boolean)plugin.getClass().getMethod("generalPresetLocked",UUID.class).invoke(plugin,id);}catch(ReflectiveOperationException|RuntimeException e){return true;}}
 public void onPluginMessageReceived(String channel,Player player,byte[] bytes){if(!channel.equals(GeneralPresetProtocol.REQUEST)||!Bukkit.isPrimaryThread())return;final GeneralPresetProtocol.Request r;try{r=GeneralPresetProtocol.request(bytes);}catch(IllegalArgumentException e){return;}var id=player.getUniqueId();long now=System.currentTimeMillis();if(r.sequence()<=sequence.getOrDefault(id,0L)||now<next.getOrDefault(id,0L))return;sequence.put(id,r.sequence());next.put(id,now+150);int selected=player.getPersistentDataContainer().getOrDefault(key,PersistentDataType.INTEGER,1);if(selected!=2)selected=1;
 int status=GeneralPresetPolicy.status(selected,r.target(),plugin.playerStateReady(player)&&!player.isDead()&&player.getGameMode()!=GameMode.SPECTATOR,combat(id),casting.test(id),mana.account(id).cooldowns,now);
 if(status==GeneralPresetProtocol.OK&&r.target()!=0&&r.target()!=selected){selected=r.target();player.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,selected);}
 if(player.getListeningPluginChannels().contains(GeneralPresetProtocol.RESPONSE))player.sendPluginMessage(plugin,GeneralPresetProtocol.RESPONSE,GeneralPresetProtocol.encode(new GeneralPresetProtocol.Response(r.sequence(),selected,status)));
 }
 @EventHandler public void quit(PlayerQuitEvent e){sequence.remove(e.getPlayer().getUniqueId());next.remove(e.getPlayer().getUniqueId());}
 public void close(){plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin,GeneralPresetProtocol.REQUEST,this);HandlerList.unregisterAll(this);sequence.clear();next.clear();}
}
