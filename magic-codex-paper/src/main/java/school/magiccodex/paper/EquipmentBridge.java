package school.magiccodex.paper;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.EquipmentProtocol;
import school.magiccodex.protocol.EquipmentProtocol.*;

/** Server-owned equipment. Vanilla armor stays in vanilla slots; accessories live in player PDC. */
final class EquipmentBridge implements PluginMessageListener,Listener,CommandExecutor,AutoCloseable {
    private static final String[] TYPES={"artifact","ring","earring","necklace"};
    private final MagicCodexBridge plugin;
    private final ManaService mana;
    private final NamespacedKey type,hpModifier;
    private final NamespacedKey[] bonuses=new NamespacedKey[5],slots=new NamespacedKey[4];
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Long> limits=new HashMap<>();
    private final Map<UUID,double[]> totals=new HashMap<>();
    private long revision;
    private final Map<UUID,ItemStack> hands=new HashMap<>();
    void refresh(Player p){apply(p);}
    private record Session(long revision,long until,ItemStack[] equipped,Map<Integer,ItemStack> inventory){}
    EquipmentBridge(MagicCodexBridge plugin,ManaService mana){
        this.plugin=plugin;this.mana=mana;type=new NamespacedKey(plugin,"accessory_type");hpModifier=new NamespacedKey(plugin,"equipment_health");
        for(int i=0;i<5;i++)bonuses[i]=new NamespacedKey(plugin,"equipment_bonus_"+i);
        for(int i=0;i<4;i++)slots[i]=new NamespacedKey(plugin,"accessory_slot_"+i);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,EquipmentProtocol.REQUEST,this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,EquipmentProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("장비설정")).setExecutor(this);
        for(Player p:Bukkit.getOnlinePlayers())apply(p);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())if(!Objects.equals(hands.get(p.getUniqueId()),p.getInventory().getItemInMainHand()))apply(p);},10,10);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{long now=System.currentTimeMillis();sessions.values().removeIf(s->s.until<now);limits.values().removeIf(t->t<now);},200,200);
    }
    Double power(UUID id,Double base){double extra=totals.getOrDefault(id,new double[5])[0];if(base!=null)return base+extra;if(extra==0)return null;return extra;}
    private ItemStack accessory(Player p,int i){byte[] b=p.getPersistentDataContainer().get(slots[i],PersistentDataType.BYTE_ARRAY);if(b==null)return null;return ItemStack.deserializeBytes(b);}
    private void accessory(Player p,int i,ItemStack item){if(empty(item))p.getPersistentDataContainer().remove(slots[i]);else p.getPersistentDataContainer().set(slots[i],PersistentDataType.BYTE_ARRAY,item.serializeAsBytes());}
    private static boolean empty(ItemStack i){return i==null||i.getType().isAir()||i.getAmount()<=0;}
    private ItemStack[] equipped(Player p){var inv=p.getInventory();return new ItemStack[]{inv.getHelmet(),inv.getChestplate(),inv.getLeggings(),inv.getBoots(),accessory(p,0),accessory(p,1),accessory(p,2),accessory(p,3)};}
    private void set(Player p,int slot,ItemStack item){var inv=p.getInventory();switch(slot){case 0->inv.setHelmet(item);case 1->inv.setChestplate(item);case 2->inv.setLeggings(item);case 3->inv.setBoots(item);default->accessory(p,slot-4,item);}}
    private int slot(ItemStack item){
        if(empty(item))return -1;
        String tag=item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING);
        if(tag!=null){for(int i=0;i<4;i++)if(TYPES[i].equals(tag))return i+4;return -1;}
        return switch(item.getType().getEquipmentSlot()){case HEAD->0;case CHEST->1;case LEGS->2;case FEET->3;default->-1;};
    }
    private double[] bonuses(ItemStack item){double[] values=new double[5];if(empty(item)||slot(item)<4)return values;
        for(int i=0;i<5;i++){Double v=item.getItemMeta().getPersistentDataContainer().get(bonuses[i],PersistentDataType.DOUBLE);if(v!=null&&Double.isFinite(v)&&v>=0&&v<=(i==1?200:100000))values[i]=v;}return values;
    }
    private void apply(Player p){
        double[] sum=new double[5];for(int i=0;i<4;i++){ItemStack item=accessory(p,i);if(!empty(item)&&slot(item)==i+4){double[] b=bonuses(item);for(int j=0;j<5;j++)sum[j]+=b[j];}}
        ItemStack hand=p.getInventory().getItemInMainHand();hands.put(p.getUniqueId(),hand.clone());
        if(!empty(hand)&&hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"wand_id"),PersistentDataType.STRING)){
            String[] keys={"wand_power","wand_mana","wand_haste"};int[] indexes={0,2,4};
            for(int i=0;i<3;i++){double v=hand.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,keys[i]),PersistentDataType.DOUBLE,0d);if(Double.isFinite(v)&&v>=0&&v<=100000)sum[indexes[i]]+=v;}
        }
        totals.put(p.getUniqueId(),sum);
        var health=p.getAttribute(Attribute.MAX_HEALTH);if(health!=null){health.removeModifier(hpModifier);if(sum[1]>0)health.addTransientModifier(new AttributeModifier(hpModifier,sum[1],AttributeModifier.Operation.ADD_NUMBER));if(p.getHealth()>health.getValue())p.setHealth(health.getValue());}
        if(mana.snapshot(p.getUniqueId()).isPresent()){mana.setModifier(p.getUniqueId(),"magiccodex:equipment",sum[2],sum[3]);mana.setHasteModifier(p.getUniqueId(),"magiccodex:equipment",sum[4]);}
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
        if(!channel.equals(EquipmentProtocol.REQUEST)||!p.getListeningPluginChannels().contains(EquipmentProtocol.RESPONSE))return;
        Request r;try{r=EquipmentProtocol.request(bytes);}catch(IllegalArgumentException e){return;}
        if(r.action()==EquipmentProtocol.CLOSE){sessions.remove(p.getUniqueId());return;}
        long now=System.currentTimeMillis();if(now<limits.getOrDefault(p.getUniqueId(),0L))return;limits.put(p.getUniqueId(),now+100);
        if(!plugin.playerStateReady(p)){send(p,r.sequence(),"캐릭터 정보를 불러오는 중입니다.");return;}
        if(!p.hasPermission("magiccodex.equipment")||p.isDead()||p.getGameMode()==GameMode.SPECTATOR){send(p,r.sequence(),"지금은 장비를 변경할 수 없습니다.");return;}
        if(r.action()==EquipmentProtocol.VIEW){send(p,r.sequence(),"");return;}
        var s=sessions.get(p.getUniqueId());
        if(s==null||s.until<now||s.revision!=r.revision()||!Arrays.equals(s.equipped,equipped(p))){send(p,r.sequence(),"장비 정보가 바뀌었습니다. 다시 선택해 주세요.");return;}
        // Never modify inventory while a chest/trade/container or vanilla cursor owns an item.
        if(!equipmentInventoryReady(p)){send(p,r.sequence(),"다른 보관함을 닫은 뒤 다시 시도해 주세요.");return;}
        ItemStack old=s.equipped[r.slot()];
        if(r.slot()<4&&!empty(old)&&old.containsEnchantment(Enchantment.BINDING_CURSE)&&p.getGameMode()!=GameMode.CREATIVE){send(p,r.sequence(),"귀속 저주가 있는 장비는 해제할 수 없습니다.");return;}
        String notice;
        if(r.action()==EquipmentProtocol.REMOVE){
            int free=p.getInventory().firstEmpty();
            if(empty(old))notice="장착된 장비가 없습니다.";
            else if(free<0)notice="인벤토리에 빈 칸이 필요합니다.";
            else {set(p,r.slot(),null);p.getInventory().setItem(free,old);notice="장비를 해제했습니다.";}
        }else{
            ItemStack current=p.getInventory().getItem(r.inventory()),expected=s.inventory.get(r.inventory());
            if(expected==null||!expected.equals(current)||slot(current)!=r.slot()){send(p,r.sequence(),"아이템이 변경되었습니다. 다시 선택해 주세요.");return;}
            // Full metadata is retained in storage; only the preview is size-limited.
            if(current.serializeAsBytes().length>32768){send(p,r.sequence(),"데이터가 너무 큰 장비는 장착할 수 없습니다.");return;}
            int free=current.getAmount()==1?r.inventory():p.getInventory().firstEmpty();
            if(!empty(old)&&free<0){send(p,r.sequence(),"기존 장비를 돌려받을 빈 칸이 필요합니다.");return;}
            ItemStack next=current.clone();next.setAmount(1);ItemStack rest=current.clone();rest.setAmount(current.getAmount()-1);
            set(p,r.slot(),next);p.getInventory().setItem(r.inventory(),rest.getAmount()==0?null:rest);
            if(!empty(old))p.getInventory().setItem(free,old);
            notice="장비를 장착했습니다.";
        }
        apply(p);plugin.savePlayerState(p);p.updateInventory();send(p,r.sequence(),notice);
    }
    // CraftInventoryView reports CREATIVE for a creative player's personal CRAFTING inventory.
    // Inspect the actual top inventory: real containers/trades and held cursor items stay blocked.
    private static boolean equipmentInventoryReady(Player p){
        return p.getOpenInventory().getTopInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING
                && empty(p.getItemOnCursor());
    }
    private static byte[] preview(ItemStack item){if(empty(item))return new byte[0];byte[] b=item.serializeAsBytes();if(b.length>512){var simple=new ItemStack(item.getType());var meta=simple.getItemMeta();meta.displayName(item.displayName());simple.setItemMeta(meta);b=simple.serializeAsBytes();if(b.length>512)b=new ItemStack(item.getType()).serializeAsBytes();}return b.length>512?new byte[0]:b;}
    private void send(Player p,long seq,String notice){
        ItemStack[] eq=equipped(p);Map<Integer,ItemStack> inventory=new HashMap<>();List<Item> candidates=new ArrayList<>();
        for(int i=0;i<36;i++){ItemStack item=p.getInventory().getItem(i);int slot=slot(item);if(slot<0)continue;inventory.put(i,item.clone());candidates.add(new Item(i,slot,preview(item)));}
        ItemStack[] saved=Arrays.stream(eq).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);long rev=++revision;
        sessions.put(p.getUniqueId(),new Session(rev,System.currentTimeMillis()+30000,saved,inventory));
        double[] b=totals.getOrDefault(p.getUniqueId(),new double[5]);
        var response=new Response(seq,rev,Math.clamp(plugin.circle(p),1,9),plugin.names().name(p),notice,b[0],b[1],b[2],b[3],Arrays.stream(eq).map(EquipmentBridge::preview).toList(),candidates,b[4]);
        p.sendPluginMessage(plugin,EquipmentProtocol.RESPONSE,EquipmentProtocol.response(response));
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!(sender instanceof Player p)||!p.hasPermission("magiccodex.equipment.admin")){sender.sendMessage("관리자만 사용할 수 있습니다.");return true;}
        try{
            if((args.length!=5&&args.length!=6)||!List.of(TYPES).contains(args[0]))throw new IllegalArgumentException();
            ItemStack item=p.getInventory().getItemInMainHand();if(empty(item))throw new IllegalArgumentException();
            double[] v=new double[5];for(int i=0;i<5;i++){v[i]=i+1<args.length?Double.parseDouble(args[i+1]):0;if(!Double.isFinite(v[i])||v[i]<0||v[i]>(i==1?200:100000))throw new IllegalArgumentException();}
            var meta=item.getItemMeta();meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,args[0]);for(int i=0;i<5;i++)meta.getPersistentDataContainer().set(bonuses[i],PersistentDataType.DOUBLE,v[i]);item.setItemMeta(meta);
            p.sendMessage("손에 든 아이템을 "+args[0]+" 장비로 등록했습니다.");
        }catch(IllegalArgumentException e){p.sendMessage("/장비설정 <artifact|ring|earring|necklace> <마력> <체력 0~200> <최대 마나> <초당 마나 회복> [마법 가속]");}return true;
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){apply(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());totals.remove(e.getPlayer().getUniqueId());hands.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent e){if(e.getKeepInventory())return;Player p=e.getEntity();for(int i=0;i<4;i++){ItemStack item=accessory(p,i);if(!empty(item)&&!item.containsEnchantment(Enchantment.VANISHING_CURSE))e.getDrops().add(item);accessory(p,i,null);}plugin.savePlayerState(p);sessions.remove(p.getUniqueId());}
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTask(plugin,()->{if(e.getPlayer().isOnline())apply(e.getPlayer());});}
    @Override public void close(){for(Player p:Bukkit.getOnlinePlayers()){var hp=p.getAttribute(Attribute.MAX_HEALTH);if(hp!=null){hp.removeModifier(hpModifier);if(p.getHealth()>hp.getValue())p.setHealth(hp.getValue());}if(mana.snapshot(p.getUniqueId()).isPresent()){mana.removeModifier(p.getUniqueId(),"magiccodex:equipment");mana.setHasteModifier(p.getUniqueId(),"magiccodex:equipment",0);}}sessions.clear();totals.clear();limits.clear();hands.clear();}
}
