package school.magiccodex.paper;
import java.util.*;import org.bukkit.*;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.PlayerQuitEvent;import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.JournalProtocol;import school.magiccodex.protocol.JournalProtocol.*;
/** Display records only; each owner retains its existing ledger and reward transaction boundaries. */
final class JournalBridge implements PluginMessageListener,Listener {
 private final MagicCodexBridge plugin;
 private record Pending(UUID target,CommandSender sender,String operation,String id,long until){}
 private final Map<String,Pending> pending=new HashMap<>();private final Map<UUID,List<Entry>> storyCatalog=new HashMap<>();private final Map<UUID,Long> limits=new HashMap<>();
 private static final class Catalog{String token="";int part,total;long start;final List<Entry> rows=new ArrayList<>();}
 private final Map<UUID,Catalog> catalogs=new HashMap<>();private final Set<UUID> loading=new HashSet<>();
 JournalBridge(MagicCodexBridge p){plugin=p;Bukkit.getMessenger().registerIncomingPluginChannel(p,JournalProtocol.QUERY,this);Bukkit.getMessenger().registerOutgoingPluginChannel(p,JournalProtocol.REPLY);Bukkit.getPluginManager().registerEvents(this,p);
  Bukkit.getScheduler().runTaskTimer(p,()->{long now=System.currentTimeMillis();pending.entrySet().removeIf(e->{if(e.getValue().until<now){e.getValue().sender.sendMessage("Story journal request timed out; no confirmation of edit.");return true;}return false;});catalogs.entrySet().removeIf(e->now-e.getValue().start>10000);},100,100);
 }
 @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();storyCatalog.remove(id);catalogs.remove(id);limits.remove(id);loading.remove(id);pending.entrySet().removeIf(x->x.getValue().target.equals(id));}
 static void send(MagicCodexBridge plugin,Player p,Message m){if(p.isOnline()&&p.getListeningPluginChannels().contains(JournalProtocol.REPLY))p.sendPluginMessage(plugin,JournalProtocol.REPLY,JournalProtocol.encode(m));}
 static void notify(MagicCodexBridge plugin,Player p,String title,String description){send(plugin,p,new Message(4,"","quest","grant:"+UUID.randomUUID(),JournalProtocol.cut(title,140),0,1,List.of(new Entry("quest","notice","",JournalProtocol.cut(description,500),"active",true,"",""))));}
 public void onPluginMessageReceived(String channel,Player player,byte[] bytes){if(!channel.equals(JournalProtocol.QUERY))return;try{
  Message m=JournalProtocol.decode(bytes);UUID id=player.getUniqueId();long now=System.currentTimeMillis();
  if(m.kind()==0){if(loading.contains(id)||now<limits.getOrDefault(id,0L)||!player.hasPermission("magiccodex.quests"))return;limits.put(id,now+1000);loading.add(id);
   plugin.questBridge().journalRows(player,quests->plugin.dialogueBridge().journalRows(player,dialogues->{loading.remove(id);if(!player.isOnline())return;var all=new ArrayList<Entry>(quests);all.addAll(dialogues);all.sort(Comparator.comparing(Entry::key));var rows=all.stream().limit(JournalProtocol.MAX).toList();for(int i=0;i<Math.max(1,rows.size());i+=JournalProtocol.CHUNK)send(plugin,player,new Message(1,m.token(),"","","",i/JournalProtocol.CHUNK,rows.size(),rows.subList(Math.min(i,rows.size()),Math.min(i+JournalProtocol.CHUNK,rows.size()))));}));
  }else if(m.kind()==3){var request=pending.get(m.token());if(request==null||!request.target.equals(id)||request.until<now||!request.id.equals(m.id())||!request.operation.equals(m.operation()))return;pending.remove(m.token());request.sender.sendMessage("[Story client journal] "+m.id()+": "+m.text());plugin.getLogger().info("STORY JOURNAL admin="+request.sender.getName()+" player="+id+" id="+m.id()+" operation="+m.operation()+" clientAck=true");
  }else if(m.kind()==5){var c=catalogs.computeIfAbsent(id,x->new Catalog());if(m.part()==0){if(now-c.start<1000)return;c.token=m.token();c.part=0;c.total=m.total();c.rows.clear();c.start=now;}
   if(!c.token.equals(m.token())||c.part!=m.part()||c.total!=m.total()||c.rows.size()+m.entries().size()>JournalProtocol.MAX||m.entries().stream().anyMatch(e->!e.source().equals("story")))return;
   for(var e:m.entries())c.rows.add(new Entry("story",e.id(),e.title(),"",e.state(),e.main(),"",""));c.part++;if(c.rows.size()==c.total)storyCatalog.put(id,List.copyOf(c.rows));
  }
 }catch(IllegalArgumentException ignored){}}
 Map<String,String> catalog(Player target){var rows=new TreeMap<String,String>();rows.putAll(plugin.questBridge().journalCatalog());rows.putAll(plugin.dialogueBridge().journalCatalog());for(var e:storyCatalog.getOrDefault(target.getUniqueId(),List.of()))rows.put(e.key(),e.title());return rows;}
 static final List<String> OPERATIONS=List.of("\uc870\ud68c","\ubd80\uc5ec","\uc9c4\ud589","\uc644\ub8cc","\ucd08\uae30\ud654");
 void command(CommandSender sender,Player target,String[] args){if(!sender.hasPermission("magiccodex.quests.admin"))throw new IllegalArgumentException("No journal admin permission");
  if(args.length==1&&args[0].equals("\uc870\ud68c")){plugin.questBridge().journalRows(target,rows->rows.forEach(e->sender.sendMessage(e.key()+" / "+e.title()+" / "+e.state())));plugin.dialogueBridge().journalRows(target,rows->rows.forEach(e->sender.sendMessage(e.key()+" / "+e.title()+" / "+e.state())));for(var e:storyCatalog.getOrDefault(target.getUniqueId(),List.of()))sender.sendMessage(e.key()+" / "+e.title()+" / "+e.state()+" (client snapshot)");return;}
  if(args.length<2||!OPERATIONS.contains(args[0]))throw new IllegalArgumentException("Quest journal: operation + source:id required");String key=String.join(" ",Arrays.copyOfRange(args,1,args.length));String resolved=AdminCommandRules.resolve(catalog(target),key);if(resolved==null)throw new IllegalArgumentException("Unknown or ambiguous record. Select source:id; open the target journal to refresh Story IDs.");
  String operation=switch(args[0]){case "\uc870\ud68c"->"query";case "\ubd80\uc5ec"->"grant";case "\uc9c4\ud589"->"progress";case "\uc644\ub8cc"->"complete";default->"reset";};String[] ref=resolved.split(":",2);
  if(ref[0].equals("quest")){plugin.questBridge().journalEdit(sender,target,ref[1],operation);return;}if(ref[0].equals("dialogue")){plugin.dialogueBridge().journalEdit(sender,target,ref[1],operation);return;}
  if(!target.getListeningPluginChannels().contains(JournalProtocol.REPLY))throw new IllegalArgumentException("Target requires updated Story and UI mods");if(pending.size()>=64)throw new IllegalArgumentException("Journal requests busy");String token=UUID.randomUUID().toString();pending.put(token,new Pending(target.getUniqueId(),sender,operation,ref[1],System.currentTimeMillis()+15000));send(plugin,target,new Message(2,token,operation,ref[1],"",0,0,List.of()));
 }
}
