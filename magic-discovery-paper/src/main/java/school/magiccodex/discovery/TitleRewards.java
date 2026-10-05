package school.magiccodex.discovery;
import java.io.*;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
final class TitleRewards {
    private final MagicDiscovery plugin;private final File file;private final YamlConfiguration data;private final NamespacedKey tokenKey;
    private final Set<UUID> busy=new HashSet<>();
    TitleRewards(MagicDiscovery plugin)throws Exception{this.plugin=plugin;file=new File(plugin.getDataFolder(),"titles.yml");data=new YamlConfiguration();if(file.exists())data.load(file);tokenKey=new NamespacedKey(plugin,"reward_token");}
    void configure(Player p,String id)throws Exception{
        if(!plugin.definitions.spells.containsKey(id))throw new IllegalArgumentException("알 수 없는 마법 ID");var item=p.getInventory().getItemInMainHand().clone();if(item.getType().isAir())throw new IllegalArgumentException("칭호 아이템을 손에 들어 주세요.");item.setAmount(1);
        data.set(id,Base64.getEncoder().encodeToString(item.serializeAsBytes()));data.save(file);p.sendMessage("칭호 보상 등록 완료: "+id+" (기존 획득자도 /칭호수령 가능)");
    }
    void claim(Player p,MagicDiscovery.Session s,boolean feedback){
        if(!p.isOnline()||p.isDead()||busy.contains(s.id))return;
        // A crash after reserving never blindly grants a second item. Existing marked items can reconcile safely.
        for(var a:s.acquired.values())if(a.reward()==2){
            boolean found=Arrays.stream(p.getInventory().getContents()).filter(Objects::nonNull).anyMatch(item->item.hasItemMeta()&&Objects.equals(item.getItemMeta().getPersistentDataContainer().get(tokenKey,PersistentDataType.LONG),a.token()));
            if(found)plugin.store.delivered(s.id,a.token());else if(feedback)p.sendMessage("지급 확인이 필요한 보상이 있습니다. 관리자에게 문의해 주세요. 번호: "+a.token());
        }
        var a=s.acquired.values().stream().filter(v->v.reward()==1&&data.isString(v.spell())).sorted(Comparator.comparingLong(DiscoveryStore.Acquisition::token)).findFirst().orElse(null);
        if(a==null){if(feedback)p.sendMessage("지금 수령할 수 있는 칭호가 없습니다. 아직 등록되지 않은 칭호는 수령 권리가 보관됩니다.");return;}
        if(p.getInventory().firstEmpty()<0){if(feedback)p.sendMessage("인벤토리 한 칸을 비운 뒤 다시 수령해 주세요.");return;}
        ItemStack item;try{item=ItemStack.deserializeBytes(Base64.getDecoder().decode(data.getString(a.spell())));}catch(Exception e){plugin.failure(e);return;}
        var meta=item.getItemMeta();meta.getPersistentDataContainer().set(tokenKey,PersistentDataType.LONG,a.token());item.setItemMeta(meta);item.setAmount(1);busy.add(s.id);
        plugin.store.reserve(s.id,a.token()).whenComplete((reserved,error)->plugin.later(()->{
            if(error!=null){busy.remove(s.id);plugin.failure(error);return;}if(!reserved){busy.remove(s.id);return;}
            if(!p.isOnline()||p.isDead()||plugin.sessions.get(s.id)!=s||p.getInventory().firstEmpty()<0){plugin.store.unreserve(s.id,a.token()).whenComplete((ok,e)->plugin.later(()->{busy.remove(s.id);if(e!=null)plugin.failure(e);}));return;}
            s.acquired.put(a.spell(),new DiscoveryStore.Acquisition(a.token(),a.spell(),a.first(),2,a.notified()));
            p.getInventory().addItem(item);p.sendMessage("최초 발견 칭호 아이템을 받았습니다.");
            plugin.store.delivered(s.id,a.token()).whenComplete((ok,e)->plugin.later(()->{busy.remove(s.id);if(e!=null){plugin.failure(e);return;}
                var latest=s.acquired.get(a.spell());s.acquired.put(a.spell(),new DiscoveryStore.Acquisition(a.token(),a.spell(),a.first(),3,latest.notified()));claim(p,s,false);
            }));
        }));
    }
}
