package school.magiccodex.paper;
import java.io.File;import java.util.*;import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.configuration.file.YamlConfiguration;import org.bukkit.inventory.ItemStack;import org.bukkit.persistence.PersistentDataType;import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.EnhancementProtocol;import school.magiccodex.protocol.EnhancementProtocol.*;
/** Debit and base result commit before animation. Interrupted challenges keep that result, never reroll. */
final class EnhancementBridge implements CommandExecutor,Listener,PluginMessageListener,AutoCloseable {
 private final MagicCodexBridge plugin;private YamlConfiguration config;
 private final Map<UUID,Session> sessions=new HashMap<>();private final Map<UUID,Long> limits=new HashMap<>();
 private static long now(){return System.nanoTime()/1_000_000;}
 private static final class Session {
  long token,created,started;int slot,coreSlot,nodes,attempts,limit,window,hit,miss,state,latency;double cost,base,bonus,roll;boolean consume,baseWin;String name,message="";ItemStack wand,core,committed;List<Double> before,gains;List<Integer> targets;
 }
 EnhancementBridge(MagicCodexBridge p){plugin=p;File f=new File(p.getDataFolder(),"enhancement.yml");if(!f.exists())p.saveResource("enhancement.yml",false);reload();
  for(String n:new String[]{"강화","마법봉"})Objects.requireNonNull(p.getCommand(n)).setExecutor(this);
  Bukkit.getPluginManager().registerEvents(this,p);Bukkit.getMessenger().registerIncomingPluginChannel(p,EnhancementProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(p,EnhancementProtocol.RESPONSE);
  Bukkit.getScheduler().runTaskTimer(p,this::tick,2,2);
 }
 private NamespacedKey key(String k){return new NamespacedKey(plugin,k);}
 private int integer(ItemStack i,String k,int fallback){return i==null||!i.hasItemMeta()?fallback:i.getItemMeta().getPersistentDataContainer().getOrDefault(key(k),PersistentDataType.INTEGER,fallback);}
 private double number(ItemStack i,String k){if(i==null||!i.hasItemMeta())return 0;double v=i.getItemMeta().getPersistentDataContainer().getOrDefault(key(k),PersistentDataType.DOUBLE,0d);return Double.isFinite(v)&&v>=0?v:0;}
 private boolean wand(ItemStack i){return i!=null&&i.getAmount()==1&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(key("wand_id"),PersistentDataType.STRING);}
 private boolean core(ItemStack i){int n=integer(i,"core_nodes",0);return i!=null&&i.getAmount()==1&&integer(i,"core_state",-1)==1&&n>=1&&n<=10;}
 private void reload(){var c=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"enhancement.yml"));
  valid(c,"cost",0,1e12);valid(c,"maximum-attempts",1,100);for(String k:new String[]{"power","mana","haste"})valid(c,"gain."+k,0,10000);
  valid(c,"starcatch.interval-ms",650,3000);valid(c,"starcatch.minimum-window-ms",30,500);valid(c,"starcatch.first-window-ms",30,500);valid(c,"starcatch.window-decrease-ms",0,40);
  valid(c,"base-chance-override",-1,1);valid(c,"bonus-chance-override",-1,1);config=c;
 }
 private static void valid(YamlConfiguration c,String k,double lo,double hi){double v=c.getDouble(k,Double.NaN);if(!Double.isFinite(v)||v<lo||v>hi)throw new IllegalArgumentException(k);}
 @Override public boolean onCommand(CommandSender sender,Command cmd,String alias,String[] args){
  if(cmd.getName().equals("마법봉")){
   if(!sender.hasPermission("magiccodex.enhance.admin"))return true;
   try{
    if(args.length==1&&args[0].equals("reload")){reload();for(Player online:Bukkit.getOnlinePlayers())for(ItemStack item:online.getInventory().getContents())if(wand(item))lore(item);sender.sendMessage("강화 설정을 다시 읽었습니다.");return true;}
    if(args.length==2&&args[0].equals("give")){Player target=Bukkit.getPlayerExact(args[1]);if(target==null||target.getInventory().firstEmpty()<0)throw new IllegalArgumentException();ItemStack i=new ItemStack(Material.BLAZE_ROD);var m=i.getItemMeta();m.displayName(net.kyori.adventure.text.Component.text("수습 마법봉"));i.setItemMeta(m);register(i,24,0,0);target.getInventory().addItem(i);target.saveData();sender.sendMessage("수습 마법봉 지급 완료.");return true;}
    if(args.length==4&&args[0].equals("register")&&sender instanceof Player p){ItemStack i=p.getInventory().getItemInMainHand();if(i.getType().isAir()||i.getAmount()!=1||wand(i))throw new IllegalArgumentException();register(i,Double.parseDouble(args[1]),Double.parseDouble(args[2]),Double.parseDouble(args[3]));p.saveData();plugin.refreshEquipment(p);p.sendMessage("손에 든 아이템을 마법봉으로 등록했습니다.");return true;}
   }catch(Exception e){sender.sendMessage("설정 또는 아이템을 확인해 주세요: "+e.getMessage());return true;}
   sender.sendMessage("/마법봉 give <닉네임> | /마법봉 register <마력> <추가 마나> <마법 가속> | /마법봉 reload");return true;
  }
  if(!(sender instanceof Player p)||!p.hasPermission("magiccodex.enhance"))return true;
  if(!p.getListeningPluginChannels().contains(EnhancementProtocol.RESPONSE)){p.sendMessage("강화 UI 모드 0.27.0 이상이 필요합니다.");return true;}
  Session previous=sessions.get(p.getUniqueId());if(previous!=null&&previous.state==1){send(p,previous);return true;}
  if(now()<limits.getOrDefault(p.getUniqueId(),0L))return true;limits.put(p.getUniqueId(),now()+350);
  offer(p,-1);return true;
 }
 private void register(ItemStack i,double power,double mana,double haste){for(double v:new double[]{power,mana,haste})if(!Double.isFinite(v)||v<0||v>100000)throw new IllegalArgumentException("수치 범위 0~100000");var m=i.getItemMeta();var d=m.getPersistentDataContainer();d.set(key("wand_id"),PersistentDataType.STRING,UUID.randomUUID().toString());d.set(key("wand_power"),PersistentDataType.DOUBLE,power);d.set(key("wand_mana"),PersistentDataType.DOUBLE,mana);d.set(key("wand_haste"),PersistentDataType.DOUBLE,haste);i.setItemMeta(m);captureBase(i);lore(i);}
 void lore(ItemStack item){var m=item.getItemMeta();m.getPersistentDataContainer().set(key("enhance_limit"),PersistentDataType.INTEGER,config.getInt("maximum-attempts",10));var lines=new ArrayList<net.kyori.adventure.text.Component>();lines.add(net.kyori.adventure.text.Component.text("마법봉"));if(m.lore()!=null)for(var l:m.lore()){String s=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(l);if(!s.startsWith("[강화]")&&!s.equals("마법봉"))lines.add(l);}
  lines.add(net.kyori.adventure.text.Component.text("[강화] 사용 횟수 "+integer(item,"enhance_attempts",0)+" · 성공 "+integer(item,"enhance_successes",0)));
  lines.add(net.kyori.adventure.text.Component.text(String.format(Locale.ROOT,"[강화] 마력 %.1f · 추가 마나 %.1f · 마법 가속 %.1f",number(item,"wand_power"),number(item,"wand_mana"),number(item,"wand_haste"))));m.lore(lines);item.setItemMeta(m);
 }
 boolean active(Player p){Session s=sessions.get(p.getUniqueId());return s!=null&&s.state==1;}
 boolean hasBase(ItemStack i){if(i==null||!i.hasItemMeta())return false;for(String n:new String[]{"power","mana","haste"})if(!i.getItemMeta().getPersistentDataContainer().has(key("wand_base_"+n),PersistentDataType.DOUBLE))return false;return true;}
 void captureBase(ItemStack i){if(hasBase(i)||integer(i,"enhance_successes",0)!=0)return;var m=i.getItemMeta();for(String n:new String[]{"power","mana","haste"})m.getPersistentDataContainer().set(key("wand_base_"+n),PersistentDataType.DOUBLE,number(i,"wand_"+n));i.setItemMeta(m);}
 private boolean usable(Player p){return !p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&(p.getOpenInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING||p.getOpenInventory().getType()==org.bukkit.event.inventory.InventoryType.CREATIVE)&&p.getItemOnCursor().getType().isAir();}
 private void offer(Player p,int after){
  if(!usable(p)){p.sendMessage("다른 보관함을 닫은 뒤 사용해 주세요.");return;}
  var inv=p.getInventory();ItemStack w=inv.getItemInMainHand();if(!wand(w)){p.sendMessage("등록된 마법봉을 주 손에 들어 주세요.");return;}
  int slot=-1;for(int j=1;j<=36;j++){int i=Math.floorMod(after+j,36);if(i!=inv.getHeldItemSlot()&&core(inv.getItem(i))){slot=i;break;}}
  if(slot<0){p.sendMessage("인벤토리에 감정된 마력코어가 필요합니다.");return;}
  Session s=new Session();s.token=ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);s.created=now();s.slot=inv.getHeldItemSlot();s.coreSlot=slot;captureBase(w);s.wand=w.clone();s.core=inv.getItem(slot).clone();s.nodes=integer(s.core,"core_nodes",1);s.attempts=integer(w,"enhance_attempts",0);s.limit=config.getInt("maximum-attempts");if(s.attempts>=s.limit){p.sendMessage("이 마법봉의 강화 횟수를 모두 사용했습니다.");return;}
  s.cost=config.getDouble("cost");s.consume=config.getBoolean("consume-attempt-on-failure",true);s.base=Math.min(1,number(s.core,"core_base_success"));s.bonus=Math.min(1,number(s.core,"core_starcatch_bonus"));if(config.getDouble("base-chance-override")>=0)s.base=config.getDouble("base-chance-override");if(config.getDouble("bonus-chance-override")>=0)s.bonus=config.getDouble("bonus-chance-override");
  s.window=Math.max(config.getInt("starcatch.minimum-window-ms"),config.getInt("starcatch.first-window-ms")-(s.nodes-1)*config.getInt("starcatch.window-decrease-ms"));
  s.before=List.of(number(w,"wand_power"),number(w,"wand_mana"),number(w,"wand_haste"));double factor=number(s.core,"core_stat_gain");if(factor<=0||factor>10000){p.sendMessage("코어의 상승량 정보가 올바르지 않습니다.");return;}
  s.gains=List.of(factor*config.getDouble("gain.power")*school.magiccodex.protocol.CoreAffinity.multiplier(0,integer(s.core,"core_affinity",0)),factor*config.getDouble("gain.mana")*school.magiccodex.protocol.CoreAffinity.multiplier(1,integer(s.core,"core_affinity",0)),factor*config.getDouble("gain.haste")*school.magiccodex.protocol.CoreAffinity.multiplier(2,integer(s.core,"core_affinity",0)));for(int i=0;i<3;i++)if(s.before.get(i)+s.gains.get(i)>100000){p.sendMessage("능력치 상한을 초과합니다.");return;}
  String name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(w.displayName());s.name=name.length()>60?name.substring(0,60):name;
  s.targets=new ArrayList<>();int t=0;for(int i=0;i<s.nodes;i++){t+=Math.max(500,config.getInt("starcatch.interval-ms")-s.nodes*20+ThreadLocalRandom.current().nextInt(-100,101));s.targets.add(t);}
  sessions.put(p.getUniqueId(),s);send(p,s);
 }
 @Override public void onPluginMessageReceived(String channel,Player p,byte[] b){if(!channel.equals(EnhancementProtocol.REQUEST))return;Request r;try{r=EnhancementProtocol.request(b);}catch(IllegalArgumentException e){return;}Session s=sessions.get(p.getUniqueId());if(s==null||s.token!=r.token())return;
  if(r.action()==EnhancementProtocol.CLOSE){sessions.remove(p.getUniqueId());return;}
  if(!p.hasPermission("magiccodex.enhance")||!usable(p)){if(s.state==1)finish(p,s,false);else refuse(p,s,"지금은 강화할 수 없습니다.");return;}
  if(r.action()==EnhancementProtocol.NEXT&&s.state==0){if(now()<limits.getOrDefault(p.getUniqueId(),0L))return;limits.put(p.getUniqueId(),now()+200);offer(p,s.coreSlot);return;}
  if(r.action()==EnhancementProtocol.START){if(s.state!=0)return;
   if(now()-s.created>60000||p.getInventory().getHeldItemSlot()!=s.slot||!s.wand.equals(p.getInventory().getItem(s.slot))||!s.core.equals(p.getInventory().getItem(s.coreSlot))){refuse(p,s,"아이템이 변경되었습니다. 다시 열어 주세요.");return;}
   s.roll=ThreadLocalRandom.current().nextDouble();s.baseWin=EnhancementRules.success(s.roll,s.base);ItemStack result=result(s,s.baseWin);
   if(!withdraw(p,s.cost)){refuse(p,s,"돈이 부족합니다.");return;}
   p.getInventory().setItem(s.coreSlot,null);p.getInventory().setItem(s.slot,result);s.committed=result.clone();s.attempts=integer(result,"enhance_attempts",0);s.state=1;s.latency=Math.clamp(p.getPing(),0,200);p.saveData();plugin.refreshEquipment(p);s.started=now();send(p,s);return;
  }
  if(r.action()==EnhancementProtocol.HIT&&s.state==1){int expected=0;while(expected<s.nodes&&((s.hit|s.miss)&(1<<expected))!=0)expected++;if(r.node()!=expected)return;int bit=1<<r.node();if(r.node()>=s.nodes||((s.hit|s.miss)&bit)!=0)return;long elapsed=now()-s.started-800;
   if(EnhancementRules.hit(elapsed,s.targets.get(r.node()),s.window,s.latency))s.hit|=bit;else s.miss|=bit;send(p,s);
  }
 }
 private ItemStack result(Session s,boolean win){ItemStack i=s.wand.clone();var m=i.getItemMeta();var d=m.getPersistentDataContainer();d.set(key("enhance_attempts"),PersistentDataType.INTEGER,integer(s.wand,"enhance_attempts",0)+(s.consume||win?1:0));d.set(key("enhance_successes"),PersistentDataType.INTEGER,integer(s.wand,"enhance_successes",0)+(win?1:0));String[] keys={"wand_power","wand_mana","wand_haste"};for(int j=0;j<3;j++)d.set(key(keys[j]),PersistentDataType.DOUBLE,s.before.get(j)+(win?s.gains.get(j):0));i.setItemMeta(m);lore(i);return i;}
 private void finish(Player p,Session s,boolean eligible){boolean perfect=eligible&&s.miss==0&&s.hit==(1<<s.nodes)-1;boolean win=s.baseWin;
  if(perfect&&!win&&EnhancementRules.success(s.roll,Math.min(1,s.base+s.bonus))){if(s.committed.equals(p.getInventory().getItem(s.slot))){var result=result(s,true);p.getInventory().setItem(s.slot,result);s.committed=result.clone();s.attempts=integer(result,"enhance_attempts",0);p.saveData();plugin.refreshEquipment(p);win=true;}}
  s.state=win?2:3;s.message=win?"강화 성공":"강화 실패 · 기존 능력치는 유지됩니다.";send(p,s);
 }
 private void tick(){long time=now();for(var it=sessions.entrySet().iterator();it.hasNext();){var e=it.next();Session s=e.getValue();Player p=Bukkit.getPlayer(e.getKey());if(p==null){it.remove();continue;}if(s.state!=1){if(time-s.created>90000)it.remove();continue;}
  if(!p.hasPermission("magiccodex.enhance")||!usable(p)||p.getInventory().getHeldItemSlot()!=s.slot||!s.committed.equals(p.getInventory().getItem(s.slot))){finish(p,s,false);continue;}
  long elapsed=time-s.started-800;boolean changed=false;for(int i=0;i<s.nodes;i++){int bit=1<<i;if(((s.hit|s.miss)&bit)==0&&elapsed>s.targets.get(i)+s.window+s.latency+100){s.miss|=bit;changed=true;}}
  if(elapsed>s.targets.get(s.nodes-1)+s.window+s.latency+250)finish(p,s,true);else if(changed)send(p,s);
 }limits.values().removeIf(t->t<time);}
 private void refuse(Player p,Session s,String msg){s.state=4;s.message=msg;send(p,s);}
 private void send(Player p,Session s){if(p.getListeningPluginChannels().contains(EnhancementProtocol.RESPONSE))p.sendPluginMessage(plugin,EnhancementProtocol.RESPONSE,EnhancementProtocol.response(new Response(s.token,s.state,s.nodes,s.attempts,s.limit,s.hit,s.miss,s.window,800,s.name,s.message,s.cost,balance(p),s.base,s.bonus,s.before,s.gains,s.targets,school.magiccodex.protocol.CoreAffinity.valid(integer(s.core,"core_affinity",0)))));}
 private record Economy(Class<?> type,Object provider){}
 @SuppressWarnings({"unchecked","rawtypes"}) private Economy economy(){for(Class type:Bukkit.getServicesManager().getKnownServices())if(type.getName().equals("net.milkbowl.vault.economy.Economy")){var r=Bukkit.getServicesManager().getRegistration(type);if(r!=null)return new Economy(type,r.getProvider());}return null;}
 private double balance(Player p){try{var e=economy();if(e==null)return -1;double b=((Number)e.type.getMethod("getBalance",OfflinePlayer.class).invoke(e.provider,p)).doubleValue();return Double.isFinite(b)&&b>=0?b:-1;}catch(ReflectiveOperationException e){return -1;}}
 private boolean withdraw(Player p,double cost){if(cost==0)return true;try{var e=economy();if(e==null)return false;Object r=e.type.getMethod("withdrawPlayer",OfflinePlayer.class,double.class).invoke(e.provider,p,cost);return (boolean)r.getClass().getMethod("transactionSuccess").invoke(r);}catch(ReflectiveOperationException e){plugin.getLogger().warning("강화 결제 실패: "+e.getClass().getSimpleName());return false;}}
 @EventHandler public void join(PlayerJoinEvent e){for(ItemStack item:e.getPlayer().getInventory().getContents())if(wand(item))lore(item);}
 @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());}
 @Override public void close(){sessions.clear();limits.clear();}
}

