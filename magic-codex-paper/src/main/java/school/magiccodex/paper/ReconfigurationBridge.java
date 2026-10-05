package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.CoreAffinity;
import school.magiccodex.protocol.ReconfigurationProtocol;
import school.magiccodex.protocol.ReconfigurationProtocol.*;

/** Single-use offers, exact item snapshots and credits are checked/committed on the server thread. */
final class ReconfigurationBridge implements CommandExecutor,Listener,PluginMessageListener,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final EnhancementBridge enhancement;
    private final Map<UUID,Offer> offers=new HashMap<>();
    private final Map<UUID,Long> limits=new HashMap<>();
    private int restoreCount=1;
    private record Offer(Response view,ItemStack item,long expires) {}
    private static final String[] STATS={"power","mana","haste"};
    ReconfigurationBridge(MagicCodexBridge plugin,EnhancementBridge enhancement) {
        this.plugin=plugin;this.enhancement=enhancement;
        File f=new File(plugin.getDataFolder(),"reconfiguration.yml");if(!f.exists())plugin.saveResource("reconfiguration.yml",false);reload();
        Objects.requireNonNull(plugin.getCommand("마력재구성")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("재구성관리")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,ReconfigurationProtocol.REQUEST,this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,ReconfigurationProtocol.RESPONSE);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{long t=System.currentTimeMillis();offers.values().removeIf(o->o.expires<t);limits.values().removeIf(n->n<t);},200,200);
    }
    private void reload(){int n=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"reconfiguration.yml")).getInt("restore-count",1);if(n<1||n>100)throw new IllegalArgumentException("restore-count: 1~100");restoreCount=n;offers.clear();}
    private NamespacedKey key(String n){return new NamespacedKey(plugin,n);}
    private int integer(ItemStack i,String n,int fallback){return i==null||!i.hasItemMeta()?fallback:i.getItemMeta().getPersistentDataContainer().getOrDefault(key(n),PersistentDataType.INTEGER,fallback);}
    private double number(ItemStack i,String n){return i.getItemMeta().getPersistentDataContainer().getOrDefault(key(n),PersistentDataType.DOUBLE,0d);}
    private boolean target(ItemStack i,int mode){return i!=null&&i.getAmount()==1&&i.hasItemMeta()&&(mode==2?integer(i,"core_state",-1)==1&&integer(i,"core_nodes",-1)>=0&&integer(i,"core_nodes",11)<=10:i.getItemMeta().getPersistentDataContainer().has(key("wand_id"),PersistentDataType.STRING));}
    private int credits(Player p,int mode){return Math.clamp(p.getPersistentDataContainer().getOrDefault(key("reconfig_credit_"+mode),PersistentDataType.INTEGER,0),0,1000000);}
    void grant(Player p,int mode,int amount){
        if(!plugin.playerStateReady(p))throw new IllegalArgumentException("캐릭터 정보를 불러오는 중입니다.");
        if(!Bukkit.isPrimaryThread()||mode<0||mode>2||amount<1||amount>1000000||credits(p,mode)>1000000-amount)throw new IllegalArgumentException("종류 0~2, 합계 1~1000000, 서버 주 스레드");
        p.getPersistentDataContainer().set(key("reconfig_credit_"+mode),PersistentDataType.INTEGER,credits(p,mode)+amount);p.saveData();plugin.savePlayerState(p);
    }
    private int mode(String s){return switch(s){case "초기화","reset"->0;case "복구","restore"->1;case "변경","affinity"->2;default->throw new IllegalArgumentException("초기화 / 복구 / 변경");};}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        try{
            if(cmd.getName().equals("재구성관리")){
                if(!sender.hasPermission("magiccodex.reconfigure.admin"))return true;
                if(args.length==1&&args[0].equals("reload")){reload();sender.sendMessage("재구성 설정을 다시 읽었습니다.");return true;}
                if(args.length==4&&args[0].equals("지급")){Player p=Bukkit.getPlayerExact(args[1]);if(p==null)throw new IllegalArgumentException("접속 중인 유저가 필요합니다.");grant(p,mode(args[2]),Integer.parseInt(args[3]));sender.sendMessage("재구성 사용 횟수를 지급했습니다.");return true;}
                if(args.length==4&&args[0].equals("원본")&&sender instanceof Player p){
                    if(enhancement.active(p))throw new IllegalArgumentException("강화를 먼저 종료해 주세요.");
                    ItemStack i=p.getInventory().getItemInMainHand();if(!target(i,0))throw new IllegalArgumentException("마법봉을 들어 주세요.");
                    double[] values=new double[3];for(int j=0;j<3;j++){values[j]=Double.parseDouble(args[j+1]);if(!Double.isFinite(values[j])||values[j]<0||values[j]>number(i,"wand_"+STATS[j]))throw new IllegalArgumentException("원본 수치는 현재 수치 이하이어야 합니다.");}
                    var m=i.getItemMeta();for(int j=0;j<3;j++)m.getPersistentDataContainer().set(key("wand_base_"+STATS[j]),PersistentDataType.DOUBLE,values[j]);i.setItemMeta(m);p.saveData();sender.sendMessage("구형 마법봉의 강화 전 원본 수치를 등록했습니다.");return true;
                }
                sender.sendMessage("/재구성관리 지급 <닉네임> <초기화|복구|변경> <횟수>\n/재구성관리 원본 <마력> <추가 마나> <마법 가속>\n/재구성관리 reload");return true;
            }
            if(sender instanceof Player p&&p.hasPermission("magiccodex.reconfigure"))open(p,args.length==0?0:mode(args[0]));
        }catch(IllegalArgumentException e){sender.sendMessage("설정을 확인해 주세요: "+e.getMessage());}
        return true;
    }
    private boolean usable(Player p){var t=p.getOpenInventory().getType();return !p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&(t==org.bukkit.event.inventory.InventoryType.CRAFTING||t==org.bukkit.event.inventory.InventoryType.CREATIVE)&&p.getItemOnCursor().getType().isAir()&&!enhancement.active(p);}
    void open(Player p,int mode){
        if(!Bukkit.isPrimaryThread()||mode<0||mode>2)throw new IllegalArgumentException();
        long t=System.currentTimeMillis();if(t<limits.getOrDefault(p.getUniqueId(),0L))return;limits.put(p.getUniqueId(),t+200);
        if(!plugin.playerStateReady(p)){p.sendMessage("캐릭터 정보를 불러오는 중입니다.");return;}
        if(!p.hasPermission("magiccodex.reconfigure"))return;
        if(!p.getListeningPluginChannels().contains(ReconfigurationProtocol.RESPONSE)){p.sendMessage("마력 재구성 UI 모드 0.28.0 이상이 필요합니다.");return;}
        if(!usable(p)){p.sendMessage("다른 보관함이나 진행 중인 강화를 먼저 닫아 주세요.");return;}
        int slot=p.getInventory().getHeldItemSlot();ItemStack item=p.getInventory().getItem(slot);
        if(!target(item,mode)){slot=-1;for(int j=0;j<36;j++)if(target(p.getInventory().getItem(j),mode)){slot=j;break;}item=slot<0?null:p.getInventory().getItem(slot);}
        String message="",name="대상 없음";int attempts=0,successes=0,limit=10,nodes=0,affinity=0,nextAttempts=0;
        List<Double> before=List.of(0d,0d,0d),after=before;
        if(item==null)message=mode==2?"인벤토리에 감정된 마력코어가 필요합니다.":"인벤토리에 등록된 마법봉이 필요합니다.";
        else {
            name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()!=null?item.getItemMeta().displayName():item.displayName());if(name.length()>80)name=name.substring(0,80);
            if(mode==2){nodes=integer(item,"core_nodes",0);affinity=CoreAffinity.valid(integer(item,"core_affinity",0));}
            else {
                enhancement.captureBase(item);
                attempts=integer(item,"enhance_attempts",0);successes=integer(item,"enhance_successes",0);limit=integer(item,"enhance_limit",10);
                before=List.of(number(item,"wand_power"),number(item,"wand_mana"),number(item,"wand_haste"));after=before;
                if(attempts<0||attempts>100||successes<0||successes>attempts||limit<1||limit>100||before.stream().anyMatch(v->!Double.isFinite(v)||v<0||v>100000)){p.sendMessage("마법봉 데이터가 올바르지 않습니다.");return;}
                nextAttempts=mode==0?0:ReconfigurationRules.restore(attempts,successes,restoreCount);
                if(mode==0){
                    if(!enhancement.hasBase(item))message="강화 전 원본 수치를 먼저 등록해 주세요.";
                    else {after=List.of(number(item,"wand_base_power"),number(item,"wand_base_mana"),number(item,"wand_base_haste"));for(int j=0;j<3;j++)if(!Double.isFinite(after.get(j))||after.get(j)<0||after.get(j)>before.get(j))message="마법봉 원본 수치를 확인해 주세요.";}
                    if(attempts==0)message="초기화할 강화 기록이 없습니다.";
                }else if(attempts==successes)message="복구할 실패 횟수가 없습니다.";
            }
        }
        int credit=credits(p,mode);if(message.isEmpty()&&credit==0)message="사용 가능한 주문서가 없습니다.";
        Response r=new Response(ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE),mode,0,slot,name,message,credit,nodes,affinity,-1,attempts,successes,limit,nextAttempts,before,after,message.isEmpty());
        offers.put(p.getUniqueId(),new Offer(r,item==null?null:item.clone(),t+60000));send(p,r);
    }
    @Override public void onPluginMessageReceived(String ch,Player p,byte[] bytes){
        if(!ch.equals(ReconfigurationProtocol.REQUEST))return;Request q;try{q=ReconfigurationProtocol.request(bytes);}catch(IllegalArgumentException e){return;}
        Offer o=offers.get(p.getUniqueId());if(o==null||o.view.token()!=q.token())return;
        if(q.action()==ReconfigurationProtocol.CLOSE){offers.remove(p.getUniqueId());return;}
        if(q.action()==ReconfigurationProtocol.SELECT){open(p,q.mode());return;}
        if(o.view.state()!=0)return;
        offers.remove(p.getUniqueId());Response r=o.view;
        if(!r.available()||q.mode()!=r.mode()||!p.hasPermission("magiccodex.reconfigure")||!usable(p)||System.currentTimeMillis()>o.expires||r.slot()<0||!o.item.equals(p.getInventory().getItem(r.slot()))){reply(p,r,2,"아이템이 변경됐거나 요청이 만료됐습니다. 다시 열어 주세요.",-1);return;}
        if(credits(p,r.mode())<1){reply(p,r,2,"사용 가능한 주문서가 없습니다.",-1);return;}
        ItemStack result=o.item.clone();var m=result.getItemMeta();var d=m.getPersistentDataContainer();int next=-1;
        if(r.mode()==2){next=CoreAffinity.changed(r.affinity(),ThreadLocalRandom.current().nextInt(2));d.set(key("core_affinity"),PersistentDataType.INTEGER,next);}
        else {
            d.set(key("enhance_attempts"),PersistentDataType.INTEGER,r.nextAttempts());
            if(r.mode()==0){d.set(key("enhance_successes"),PersistentDataType.INTEGER,0);for(int j=0;j<3;j++)d.set(key("wand_"+STATS[j]),PersistentDataType.DOUBLE,r.after().get(j));}
        }
        result.setItemMeta(m);if(r.mode()!=2)enhancement.lore(result);
        // The inventory mutation and credit debit persist in the same player data save. Duplicate packets have no offer.
        p.getInventory().setItem(r.slot(),result);p.getPersistentDataContainer().set(key("reconfig_credit_"+r.mode()),PersistentDataType.INTEGER,credits(p,r.mode())-1);p.saveData();plugin.savePlayerState(p);plugin.refreshEquipment(p);
        reply(p,r,1,r.mode()==0?"강화를 초기화했습니다.":r.mode()==1?"실패 횟수 "+(r.attempts()-r.nextAttempts())+"회를 복구했습니다.":"코어 성향을 변경했습니다.",next);
    }
    private void reply(Player p,Response r,int state,String message,int affinity){Response result=new Response(r.token(),r.mode(),state,r.slot(),r.name(),message,credits(p,r.mode()),r.nodes(),r.affinity(),affinity,r.attempts(),r.successes(),r.limit(),r.nextAttempts(),r.before(),r.after(),false);send(p,result);offers.put(p.getUniqueId(),new Offer(result,null,System.currentTimeMillis()+60000));}
    private void send(Player p,Response r){p.sendPluginMessage(plugin,ReconfigurationProtocol.RESPONSE,ReconfigurationProtocol.response(r));}
    @EventHandler public void quit(PlayerQuitEvent e){offers.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){offers.clear();limits.clear();}
}
