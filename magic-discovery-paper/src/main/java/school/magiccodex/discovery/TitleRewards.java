package school.magiccodex.discovery;
import java.io.*;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
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
    /** 관리자: 지급 확인 대기(예약, reward=2)로 멈춘 보상 목록. */
    void adminReserved(CommandSender sender,UUID id,String name){
        plugin.store.reserved(id).whenComplete((list,error)->plugin.later(()->{
            if(error!=null){plugin.failure(error);sender.sendMessage("보상 정보를 읽지 못했습니다. 서버 로그를 확인해 주세요.");return;}
            if(list.isEmpty()){sender.sendMessage(name+": 지급 확인 대기 상태인 칭호 보상이 없습니다.");return;}
            sender.sendMessage(name+": 지급 확인 대기 보상 "+list.size()+"건");
            for(var a:list)sender.sendMessage(" - 번호 "+a.token()+" / "+a.spell()+(a.first()?" (최초 발견)":""));
            sender.sendMessage("복구: /마법발견관리 보상 "+name+" <번호> 재수령|완료 (재수령=다시 받을 수 있게, 완료=이미 받은 것으로 처리)");
        }));
    }
    /** 관리자: 예약 상태 보상을 수령 가능(재수령) 또는 지급 완료로 되돌린다. 지급 처리 중에는 중복 지급을 막기 위해 거절한다. */
    void adminResolve(CommandSender sender,UUID id,String name,long token,boolean claimable){
        if(busy.contains(id)){sender.sendMessage("지금 지급 처리 중인 플레이어입니다. 잠시 뒤 다시 시도해 주세요.");return;}
        busy.add(id);
        plugin.store.resolveReserved(id,token,claimable).whenComplete((changed,error)->plugin.later(()->{
            busy.remove(id);
            if(error!=null){plugin.failure(error);sender.sendMessage("보상 상태를 바꾸지 못했습니다. 서버 로그를 확인해 주세요.");return;}
            if(!changed){sender.sendMessage("해당 번호의 지급 확인 대기 보상이 없습니다: "+token);return;}
            var s=plugin.sessions.get(id);
            if(s!=null)for(var a:List.copyOf(s.acquired.values()))if(a.token()==token)s.acquired.put(a.spell(),new DiscoveryStore.Acquisition(a.token(),a.spell(),a.first(),claimable?1:3,a.notified()));
            plugin.getLogger().info("칭호 보상 복구: "+sender.getName()+" → "+name+"("+id+") 번호 "+token+" "+(claimable?"수령 가능으로 되돌림":"지급 완료 처리"));
            sender.sendMessage(claimable?"번호 "+token+" 보상을 다시 수령할 수 있게 되돌렸습니다. (/칭호수령)":"번호 "+token+" 보상을 지급 완료로 처리했습니다.");
        }));
    }
}
