package school.magiccodex.paper;
import java.util.*;import java.util.concurrent.*;import java.util.function.*;
import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.inventory.ItemStack;import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.ShopAdminProtocol;import school.magiccodex.protocol.ShopAdminProtocol.*;
/** Independent operator session; no wallet/inventory effects or live schema migration. */
final class ShopAdminBridge implements CommandExecutor,TabCompleter,PluginMessageListener,Listener,AutoCloseable {
 private final MagicCodexBridge plugin;private final ShopStore store;
 private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"MagicCodex-shop-admin-io");t.setDaemon(true);return t;});
 private record Session(Player player,long token,long sequence,long expires){}
 private final Map<UUID,Session> sessions=new HashMap<>();private final Set<UUID> busy=new HashSet<>();
 private List<String> shopIds=List.of();private boolean closing;
 ShopAdminBridge(MagicCodexBridge p)throws Exception{plugin=p;var dir=p.getDataFolder().toPath();try{store=io.submit(()->new ShopStore(ShopMailboxDatabaseSettings.load(dir),dir.resolve("shops.db"))).get(15,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
  var cmd=Objects.requireNonNull(p.getCommand("상점관리화면"));cmd.setExecutor(this);cmd.setTabCompleter(this);
  Bukkit.getMessenger().registerIncomingPluginChannel(p,ShopAdminProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(p,ShopAdminProtocol.RESPONSE);Bukkit.getPluginManager().registerEvents(this,p);
 }
 private boolean allowed(Player p){return !closing&&p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p&&p.hasPermission("magiccodex.shop.admin");}
 private void main(Runnable r){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,r);}
 private <T>void work(Callable<T> task,Consumer<T> done,Consumer<String> fail){try{io.execute(()->{try{T v=task.call();main(()->done.accept(v));}catch(Exception e){plugin.getLogger().warning("Shop admin: "+e.getClass().getSimpleName());main(()->fail.accept(e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"저장 결과를 확인할 수 없습니다. 새로고침 후 확인하고 다시 저장하세요."));}});}catch(RejectedExecutionException e){fail.accept("관리 요청이 많습니다. 잠시 뒤 다시 시도하세요.");}}
 @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args){if(!(sender instanceof Player p)||!allowed(p)){sender.sendMessage("상점 관리자 권한이 있는 플레이어만 사용할 수 있습니다.");return true;}if(args.length>1||args.length==1&&!args[0].matches("[a-z0-9_-]{1,48}")){sender.sendMessage("/상점관리화면 [상점ID]");return true;}if(!p.getListeningPluginChannels().contains(ShopAdminProtocol.RESPONSE)){p.sendMessage("상점 관리 UI가 포함된 클라이언트 모드가 필요합니다.");return true;}if(busy.contains(p.getUniqueId())){p.sendMessage("이전 관리 요청을 처리 중입니다.");return true;}
  var s=new Session(p,ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE),0,System.currentTimeMillis()+600000);sessions.put(p.getUniqueId(),s);
  Request r=new Request(ShopAdminProtocol.LIST,0,s.token,args.length==1?args[0]:"",0,"","","off","off","",0);read(p,s,r,"");return true;
 }
 @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return s.hasPermission("magiccodex.shop.admin")&&a.length==1?shopIds.stream().filter(x->x.startsWith(a[0])).toList():List.of();}
 @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){if(!channel.equals(ShopAdminProtocol.REQUEST)||!allowed(p))return;Request r;try{r=ShopAdminProtocol.request(bytes);}catch(IllegalArgumentException ignored){return;}var s=sessions.get(p.getUniqueId());if(s==null||s.player!=p||s.token!=r.session()||r.sequence()<=s.sequence||System.currentTimeMillis()>s.expires)return;
  if(busy.contains(p.getUniqueId()))return;s=new Session(p,s.token,r.sequence(),System.currentTimeMillis()+600000);sessions.put(p.getUniqueId(),s);
  if(r.action()==ShopAdminProtocol.CLOSE){sessions.remove(p.getUniqueId());return;}
  if(r.action()==ShopAdminProtocol.LIST){read(p,s,r,"");return;}
  ItemStack held=null;String buy,sell;
  try{buy=r.action()==ShopAdminProtocol.DELETE?"":ShopStore.price(r.buy());sell=r.action()==ShopAdminProtocol.DELETE?"":ShopStore.price(r.sell());if(r.action()==ShopAdminProtocol.ADD){held=p.getInventory().getItemInMainHand().clone();if(held.getType().isAir())throw new IllegalArgumentException("등록할 커스텀 아이템을 주 손에 들어 주세요.");held.setAmount(1);}}
  catch(IllegalArgumentException e){read(p,s,r,e.getMessage());return;}
  byte[] payload=held==null?null:held.serializeAsBytes();if(payload!=null&&payload.length>ShopAdminProtocol.MAX_PREVIEW){read(p,s,r,"아이템 데이터가 너무 큽니다. 원본을 줄이지 않고 등록을 중단했습니다.");return;}
  Session accepted=s;busy.add(p.getUniqueId());
  work(()->{switch(r.action()){case ShopAdminProtocol.ADD->store.adminAdd(r.shop(),r.revision(),r.name(),payload,buy,sell);case ShopAdminProtocol.EDIT->store.adminEdit(r.shop(),r.revision(),r.product(),r.name(),buy,sell);case ShopAdminProtocol.DELETE->store.adminDelete(r.shop(),r.revision(),r.product());default->throw new IllegalArgumentException("관리 동작");}return snapshot(accepted,r,"상품 "+(r.action()==ShopAdminProtocol.DELETE?"삭제":"저장")+" 완료");},v->deliver(p,accepted,v),m->{busy.remove(p.getUniqueId());read(p,accepted,r,m);});
 }
 private void read(Player p,Session s,Request r,String message){busy.add(p.getUniqueId());work(()->snapshot(s,r,message),v->deliver(p,s,v),m->{busy.remove(p.getUniqueId());if(allowed(p)){p.sendMessage(m);p.sendPluginMessage(plugin,ShopAdminProtocol.RESPONSE,ShopAdminProtocol.encode(new Response(r.sequence(),s.token,r.shop(),r.revision(),m,List.of(),List.of(),0,-1)));}});}
 private Response snapshot(Session session,Request r,String message)throws Exception{
  var catalog=store.catalog();var sorted=catalog.values().stream().sorted(Comparator.comparing(ShopStore.Shop::id)).toList();var shops=new ArrayList<Shop>();int metadata=0;for(var entry:sorted){int cost=(entry.id().length()+entry.title().length())*3+8;if(shops.size()==128||metadata+cost>12000)break;metadata+=cost;shops.add(new Shop(entry.id(),entry.title()));}
  String id=r.shop().isEmpty()?(sorted.isEmpty()?"":sorted.getFirst().id()):r.shop();var shop=catalog.get(id);
  if(shop==null)return new Response(r.sequence(),session.token,id,0,message.isEmpty()?"등록된 상점을 선택하세요. 기존 명령으로 상점을 먼저 생성할 수 있습니다.":message,shops,List.of(),0,-1);
  String q=r.search().toLowerCase(Locale.ROOT);var matching=shop.products().stream().filter(p->p.name().toLowerCase(Locale.ROOT).contains(q)||p.id().contains(q)).toList();
  int offset=r.offset()<matching.size()?r.offset():0,index=offset,size=0;var products=new ArrayList<Product>();
  // Budget for UTF-8 shop/title metadata plus the original, unmodified ItemStack previews.
  for(;index<matching.size()&&products.size()<20;index++){var p=matching.get(index);int cost=p.bytes().length+700;if(p.bytes().length>ShopAdminProtocol.MAX_PREVIEW)throw new IllegalStateException("기존 상품 데이터가 UI 허용 크기를 초과합니다.");if(size+cost>38000&&!products.isEmpty())break;size+=cost;products.add(new Product(p.id(),p.name(),p.buy(),p.sell(),p.bytes()));}
  if(sorted.size()>shops.size()&&message.isEmpty())message="상점 목록은 패킷 크기에 맞춰 일부 표시합니다. /상점관리화면 상점ID로 직접 열 수 있습니다.";
  return new Response(r.sequence(),session.token,id,shop.revision(),message,shops,List.copyOf(products),offset,index<matching.size()?index:-1);
 }
 private void deliver(Player p,Session s,Response response){busy.remove(p.getUniqueId());var current=sessions.get(p.getUniqueId());if(!allowed(p)||current==null||current.token!=s.token||current.sequence!=response.sequence())return;shopIds=response.shops().stream().map(Shop::id).toList();p.sendPluginMessage(plugin,ShopAdminProtocol.RESPONSE,ShopAdminProtocol.encode(response));}
 @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());}
 @Override public void close(){closing=true;sessions.clear();io.shutdown();try{if(io.awaitTermination(10,TimeUnit.SECONDS))store.close();}catch(Exception e){plugin.getLogger().warning("Shop admin close: "+e.getClass().getSimpleName());}}
}
