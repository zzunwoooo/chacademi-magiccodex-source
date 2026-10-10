package school.magiccodex.paper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;

import school.magiccodex.protocol.MailboxProtocol;
import school.magiccodex.protocol.MailboxProtocol.*;

/**
 * Main-thread inventory, bounded JDBC worker, owner-fenced claims and durable origin receipts.
 *
 * 첨부 상태 (mail_items.state): ready -> pending(token=수령번호, origin=수령하던 서버) -> claimed, 취소 시 pending -> ready.
 * 아이템 지급과 영수증(PDC mail_receipt_<수령번호>)은 같은 틱에 같은 플레이어 데이터에 기록하고, claimed 기록이 끝난 뒤에만 영수증을 지운다.
 * 그래서 수령하던 서버에서 pending 을 다시 만났을 때:
 *  - 영수증이 없다  -> 아이템이 지급되지 않은 것이 확실 -> ready 로 되돌린다 (자동)
 *  - 영수증이 있고 디스크에도 저장됨 -> 지급된 것이 확실 -> claimed 로 닫는다 (자동)
 *  - 다른 서버에서 수령하던 첨부 -> 그 서버에 접속해 우편함을 열면 위 규칙으로 정리된다. 관리자는 /우편관리 로 직접 처리할 수 있다.
 */
final class MailboxBridge implements MailService,PluginMessageListener,Listener,CommandExecutor,TabCompleter,AutoCloseable {
 private final MagicCodexBridge plugin;private final MailboxStore store;private final String origin;
 /** 새 요청만 이 한도로 거절한다(work). 이미 예약한 수령의 후속 기록(must)과 시스템 우편 저장은 거절하지 않는다. */
 private static final int QUEUE_LIMIT=64;
 private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(),r->{var t=new Thread(r,"MagicCodex-mailbox-io");t.setDaemon(true);return t;});
 private record Session(Player connection,long token,long sequence){}
 private final Map<UUID,Session> sessions=new HashMap<>();private final Set<UUID> busy=new HashSet<>();private final Map<UUID,Long> limits=new HashMap<>();private volatile boolean closing;
 /** 관리자가 방금 `기록`으로 본 수령번호 (탭 완성용). */
 private final Map<String,List<String>> listed=new HashMap<>();
 MailboxBridge(MagicCodexBridge plugin)throws Exception{
  this.plugin=plugin;Path dir=plugin.getDataFolder().toPath(),identity=dir.resolve("mailbox-origin.txt");
  if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString(),StandardOpenOption.CREATE_NEW);
  origin=UUID.fromString(Files.readString(identity).strip()).toString();
  try{store=io.submit(()->new MailboxStore(ShopMailboxDatabaseSettings.load(dir),dir.resolve("mailbox.db"))).get(15,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
  Bukkit.getMessenger().registerIncomingPluginChannel(plugin,MailboxProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,MailboxProtocol.RESPONSE);
  Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getServicesManager().register(MailService.class,this,plugin,ServicePriority.Normal);
  var command=plugin.getCommand("우편관리");if(command!=null){command.setExecutor(this);command.setTabCompleter(this);}
 }
 private boolean current(Player p){return !closing&&p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
 private boolean allowed(Player p){return current(p)&&p.hasPermission("magiccodex.mailbox")&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.playerStateReady(p);}
 private void main(Runnable action){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,action);}
 /** 메인 스레드 후속 처리는 반드시 여기를 거친다: 예외가 나도 busy 가 풀리고, 첨부는 기록된 상태로 남아 다음에 우편함을 열 때 자동으로 판정된다. */
 private void guard(UUID owner,Runnable r){try{r.run();}catch(Throwable t){plugin.getLogger().log(Level.SEVERE,"Mailbox continuation failed"+(owner==null?"":" for "+owner)+"; attachments keep their recorded state",t);if(owner!=null){busy.remove(owner);var p=Bukkit.getPlayer(owner);if(p!=null)p.sendMessage("우편 처리 중 오류가 발생했습니다. 우편함을 다시 열어 주세요.");}}}
 /** 새 요청: 대기열이 차 있으면 아무 것도 바꾸지 않고 거절한다. */
 private <T>void work(UUID owner,Callable<T> task,Consumer<T> success,Consumer<String> fail){if(io.getQueue().size()>=QUEUE_LIMIT){fail.accept("우편함이 바쁩니다. 잠시 뒤 다시 시도해 주세요.");return;}must(owner,task,success,fail);}
 /** 진행 중인 수령의 후속 기록: 대기열 한도로 거절하지 않는다. */
 private <T>void must(UUID owner,Callable<T> task,Consumer<T> success,Consumer<String> fail){try{io.execute(()->{T value;try{value=task.call();}catch(Exception e){plugin.getLogger().warning("Mailbox operation held: "+e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));String m=e instanceof IllegalStateException||e instanceof IllegalArgumentException?String.valueOf(e.getMessage()):"우편 처리 기록을 확인해야 합니다. 다시 지급하지 않습니다.";main(()->guard(owner,()->fail.accept(m)));return;}main(()->guard(owner,()->success.accept(value)));});}catch(RejectedExecutionException e){guard(owner,()->fail.accept("우편함이 종료 중입니다. 잠시 뒤 다시 시도해 주세요."));}}
 @Override public CompletableFuture<UUID> sendSystem(String source,UUID recipient,String title,String body,List<ItemStack> attachments){
  if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");
  if(closing||source==null||source.isBlank()||source.length()>160||recipient==null||title==null||title.isBlank()||title.length()>80||body==null||body.length()>2000||attachments==null||attachments.size()>9)throw new IllegalArgumentException("mail limits");
  var stored=new ArrayList<MailboxStore.Item>();int index=0;
  for(ItemStack original:attachments){if(original==null||original.getType().isAir()||original.getAmount()<1||original.getAmount()>original.getMaxStackSize()||original.getAmount()>99)throw new IllegalArgumentException("attachment stack");ItemStack copy=original.clone();byte[] bytes=copy.serializeAsBytes();if(bytes.length>32768)throw new IllegalArgumentException("attachment bytes");String name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(copy.displayName());if(name.length()>120)name=name.substring(0,120);stored.add(new MailboxStore.Item(index++,name,copy.getAmount(),bytes,"ready","",""));}
  var future=new CompletableFuture<UUID>();try{io.execute(()->{try{future.complete(store.create(source,recipient,title,body,List.copyOf(stored)));}catch(Exception e){future.completeExceptionally(e);}});}catch(RejectedExecutionException e){future.completeExceptionally(e);}return future;
 }
 @Override public CompletableFuture<Boolean> restoreDeletedMail(UUID recipient,UUID mailId){if(!Bukkit.isPrimaryThread()||closing)throw new IllegalStateException("server thread required");Objects.requireNonNull(recipient);Objects.requireNonNull(mailId);var future=new CompletableFuture<Boolean>();try{io.execute(()->{try{future.complete(store.restore(recipient,mailId));}catch(Exception e){future.completeExceptionally(e);}});}catch(RejectedExecutionException e){future.completeExceptionally(e);}return future;}
 @Override public void onPluginMessageReceived(String channel,Player p,byte[] bytes){
  if(!channel.equals(MailboxProtocol.REQUEST)||!allowed(p))return;Request r;try{r=MailboxProtocol.request(bytes);}catch(IllegalArgumentException e){return;}
  UUID owner=p.getUniqueId();long now=System.currentTimeMillis();if(busy.contains(owner)||now<limits.getOrDefault(owner,0L))return;
  Session s=sessions.get(owner);if(r.action()==MailboxProtocol.OPEN){s=new Session(p,ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE),r.sequence());sessions.put(owner,s);}else{if(s==null||s.connection!=p||s.token!=r.session()||r.sequence()<=s.sequence)return;s=new Session(p,s.token,r.sequence());sessions.put(owner,s);}
  limits.put(owner,now+150);if(r.action()==MailboxProtocol.CLOSE){sessions.remove(owner);return;}busy.add(owner);long token=s.token;
  switch(r.action()){
   case MailboxProtocol.CLAIM,MailboxProtocol.CLAIM_ALL -> claim(p,r,token);
   case MailboxProtocol.DELETE_CLAIMED -> work(owner,()->store.deleteClaimed(owner),n->snapshot(p,r,token,n+"개의 수령한 우편을 삭제했습니다."),m->finishError(p,r,token,m));
   default -> recover(p,r,token);
  }
 }
 private NamespacedKey receipt(String token){return new NamespacedKey(plugin,"mail_receipt_"+token.replace("-",""));}
 static Path playerFile(Player p){return Bukkit.getWorlds().getFirst().getWorldFolder().toPath().resolve("playerdata").resolve(p.getUniqueId()+".dat");}
 private void recover(Player p,Request r,long session){
  UUID owner=p.getUniqueId();Set<String> receipts=new HashSet<>();for(var key:p.getPersistentDataContainer().getKeys())if(key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))&&key.getKey().startsWith("mail_receipt_"))receipts.add(key.getKey().substring(13));
  Path savedFile=playerFile(p);work(owner,()->{var tokens=new HashMap<String,String>();for(var ref:store.pending(owner))tokens.put(ref.item().token(),ref.item().origin());int review=0,elsewhere=0,saving=0;
   for(var entry:tokens.entrySet()){
    if(!entry.getValue().equals(origin)){if(entry.getValue().isEmpty())review++;else elsewhere++;continue;}
    // 이 서버에서 수령하던 첨부: 영수증이 없으면 지급 전에 멈춘 것이므로 다시 수령할 수 있게 되돌린다.
    if(!receipts.contains(entry.getKey().replace("-",""))){store.settle(owner,entry.getKey(),false);plugin.getLogger().info("Mailbox recovery: claim "+entry.getKey()+" ("+owner+") pending -> ready (영수증 없음)");continue;}
    if(DeliveryReceipt.saved(savedFile,receipt(entry.getKey()).toString())){store.settle(owner,entry.getKey(),true);plugin.getLogger().info("Mailbox recovery: claim "+entry.getKey()+" ("+owner+") pending -> claimed (영수증 저장 확인)");}else saving++;
   }
   return review>0?"이전 수령 기록을 관리자가 확인해야 합니다. 관리자에게 문의해 주세요.":elsewhere>0?"다른 서버에서 받던 첨부가 있습니다. 그 서버에서 우편함을 열면 자동으로 정리됩니다.":saving>0?"이전 수령을 저장하는 중입니다. 잠시 뒤 우편함을 다시 열어 주세요.":"";},message->snapshot(p,r,session,message),m->finishError(p,r,session,m));
 }
 private record Snapshot(MailboxStore.Page page,MailboxStore.Mail mail){}
 private void snapshot(Player p,Request r,long token,String message){must(p.getUniqueId(),()->{var page=store.list(p.getUniqueId(),r.page());String selected=r.mail();if(selected.isEmpty()&&!page.entries().isEmpty())selected=page.entries().getFirst().id();return new Snapshot(page,selected.isEmpty()?null:store.detail(p.getUniqueId(),selected));},data->{busy.remove(p.getUniqueId());if(!current(p))return;var s=sessions.get(p.getUniqueId());if(s==null||s.token!=token||s.sequence!=r.sequence())return;var attachments=new ArrayList<Attachment>();if(data.mail!=null)for(var i:data.mail.items()){byte[] preview=i.bytes();if(preview.length>3072)try{ItemStack stack=ItemStack.deserializeBytes(i.bytes());preview=new ItemStack(stack.getType(),stack.getAmount()).serializeAsBytes();}catch(RuntimeException e){preview=new byte[0];/* 미리보기만 비운다 */}attachments.add(new Attachment(i.index(),i.name(),i.amount(),i.state().equals("claimed"),preview.length<=3072?preview:new byte[0]));}p.sendPluginMessage(plugin,MailboxProtocol.RESPONSE,MailboxProtocol.encode(new Response(r.sequence(),token,message,r.page(),data.page.more(),data.page.entries(),data.mail==null?"":data.mail.id(),data.mail==null?"":data.mail.body(),List.copyOf(attachments))));},m->finishError(p,r,token,m));}
 private void finishError(Player p,Request r,long token,String message){busy.remove(p.getUniqueId());if(current(p))p.sendPluginMessage(plugin,MailboxProtocol.RESPONSE,MailboxProtocol.encode(new Response(r.sequence(),token,message,r.page(),false,List.of(),"","",List.of())));}
 static ItemStack[] copy(ItemStack[] source){return Arrays.stream(source).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
 static boolean same(ItemStack[] a,ItemStack[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++){if(a[i]==null||a[i].getType().isAir()){if(b[i]!=null&&!b[i].getType().isAir())return false;}else if(b[i]==null||!Arrays.equals(a[i].serializeAsBytes(),b[i].serializeAsBytes()))return false;}return true;}
 /** Inserts a whole attachment or leaves the simulated inventory unchanged. Never drops overflow. */
 static boolean insert(ItemStack[] inventory,ItemStack value){var next=copy(inventory);int left=value.getAmount();for(int i=0;i<next.length&&left>0;i++){var old=next[i];if(old!=null&&!old.getType().isAir()&&old.isSimilar(value)){int n=Math.min(left,Math.max(0,Math.min(64,old.getMaxStackSize())-old.getAmount()));old.setAmount(old.getAmount()+n);left-=n;}}for(int i=0;i<next.length&&left>0;i++)if(next[i]==null||next[i].getType().isAir()){int n=Math.min(left,Math.min(64,value.getMaxStackSize()));next[i]=value.clone();next[i].setAmount(n);left-=n;}if(left!=0)return false;System.arraycopy(next,0,inventory,0,next.length);return true;}
 private void claim(Player p,Request r,long session){
  if(r.action()==MailboxProtocol.CLAIM&&r.mail().isEmpty()){finishError(p,r,session,"우편을 선택해 주세요.");return;}
  UUID owner=p.getUniqueId();work(owner,()->store.ready(owner,r.action()==MailboxProtocol.CLAIM_ALL?"":r.mail()),refs->{
   if(!allowed(p)){busy.remove(owner);return;}var before=copy(p.getInventory().getStorageContents());var next=copy(before);var fits=new ArrayList<MailboxStore.Ref>();for(var ref:refs)try{if(insert(next,ItemStack.deserializeBytes(ref.item().bytes())))fits.add(ref);}catch(RuntimeException e){plugin.getLogger().warning("Mailbox attachment unreadable: mail "+ref.mail()+" #"+ref.item().index());}
   if(fits.isEmpty()){snapshot(p,r,session,"수령할 첨부가 없거나 인벤토리 공간이 부족합니다.");return;}
   String claim=UUID.randomUUID().toString();must(owner,()->{store.reserve(owner,List.copyOf(fits),claim,origin);return true;},reserved->{
    if(!allowed(p)||!same(before,p.getInventory().getStorageContents())){must(owner,()->{store.settle(owner,claim,false);return true;},v->snapshot(p,r,session,"인벤토리가 변경되었습니다. 다시 수령해 주세요."),m->finishError(p,r,session,m));return;}
    // Item movement and receipt are written into the same vanilla player-data snapshot on the origin server.
    // A crash with no durable receipt remains pending for review; never blindly release/replay it.
    try{p.getInventory().setStorageContents(next);p.getPersistentDataContainer().set(receipt(claim),PersistentDataType.BYTE,(byte)1);p.saveData();}
    catch(RuntimeException e){finishError(p,r,session,"수령 저장을 확인해야 합니다. 자동으로 재지급하지 않습니다.");return;}
    Path file=playerFile(p);String savedKey=receipt(claim).toString();must(owner,()->{if(!DeliveryReceipt.saved(file,savedKey))throw new IllegalStateException("아이템 저장 영수증 확인이 필요합니다. 자동으로 재지급하지 않습니다.");store.settle(owner,claim,true);return true;},v->{if(current(p)){p.getPersistentDataContainer().remove(receipt(claim));p.saveData();}snapshot(p,r,session,fits.size()+"개의 첨부를 수령했습니다. 공간이 부족한 첨부는 우편에 남아 있습니다.");},m->finishError(p,r,session,m));
   },m->finishError(p,r,session,m));
  },m->finishError(p,r,session,m));
 }
 private UUID target(String input){try{return UUID.fromString(input);}catch(IllegalArgumentException ignored){}Player online=plugin.names().resolve(input);if(online==null)throw new IllegalArgumentException("접속 중인 유저의 닉네임·영문 계정명 또는 UUID를 입력하세요.");return online.getUniqueId();}
 /** /우편관리 기록 <유저|UUID> · /우편관리 처리 <유저|UUID> <수령번호> 완료|반환 (권한 magiccodex.mailbox.admin) */
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!sender.hasPermission("magiccodex.mailbox.admin"))return true;
  try{
   if(args.length==2&&args[0].equals("기록")){UUID owner=target(args[1]);work(null,()->store.held(owner),rows->{
     var tokens=new LinkedHashSet<String>();for(var h:rows)tokens.add(h.item().token());listed.put(sender.getName(),List.copyOf(tokens));
     sender.sendMessage("수령 도중 멈춘 첨부 "+rows.size()+"개 ("+owner+")"+(rows.isEmpty()?"":" — /우편관리 처리 "+args[1]+" <수령번호> 완료|반환"));
     for(var h:rows)sender.sendMessage(h.item().token()+" | 우편 \""+h.title()+"\" ("+QuestBridge.timeText(h.created())+") | "+h.item().name()+" "+h.item().amount()+"개 | "+(h.item().origin().isEmpty()?"서버 기록 없음":h.item().origin().equals(origin)?"이 서버에서 수령":"다른 서버에서 수령"));
     if(!rows.isEmpty())sender.sendMessage("  → 수령하던 서버에서 유저가 우편함을 열면 자동으로 판정됩니다. 직접 처리: 유저가 이미 받았으면 완료(다시 주지 않음), 못 받았으면 반환(우편함에서 다시 수령).");
    },sender::sendMessage);return true;}
   if(args.length==4&&args[0].equals("처리")&&List.of("완료","반환").contains(args[3])){UUID owner=target(args[1]);String token=UUID.fromString(args[2]).toString();boolean claimed=args[3].equals("완료");
    if(busy.contains(owner)){sender.sendMessage("이 유저의 우편을 지금 처리하는 중입니다. 잠시 뒤 다시 시도하세요.");return true;}
    busy.add(owner);String admin=sender.getName();
    must(owner,()->store.settle(owner,token,claimed),n->{busy.remove(owner);plugin.getLogger().warning("MAIL ADMIN admin="+admin+" player="+owner+" claim="+token+" action="+args[3]+" items="+n);sender.sendMessage(n==0?"처리할 첨부가 없습니다. 이미 처리했거나 수령번호가 다릅니다.":claimed?"첨부 "+n+"개를 수령 완료로 닫았습니다. (다시 지급하지 않음)":"첨부 "+n+"개를 되돌렸습니다. 유저가 우편함에서 다시 수령할 수 있습니다.");},m->{busy.remove(owner);sender.sendMessage(m);});return true;}
  }catch(IllegalArgumentException e){sender.sendMessage(e.getMessage()==null?"입력을 확인하세요.":e.getMessage());return true;}
  sender.sendMessage("/우편관리 기록 <유저|UUID> — 수령 도중 멈춘 첨부 목록\n/우편관리 처리 <유저|UUID> <수령번호> 완료|반환 — 완료: 이미 받음(다시 주지 않음) / 반환: 못 받음(다시 수령 가능)");return true;
 }
 @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
  if(closing||!sender.hasPermission("magiccodex.mailbox.admin")||args.length==0)return List.of();String prefix=args[args.length-1];
  List<String> options=switch(args.length){case 1->List.of("기록","처리");case 2->Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();case 3->args[0].equals("처리")?listed.getOrDefault(sender.getName(),List.of()):List.<String>of();case 4->args[0].equals("처리")?List.of("완료","반환"):List.<String>of();default->List.<String>of();};
  return AdminCommandRules.filter(options,prefix);
 }
 @EventHandler public void quit(PlayerQuitEvent e){listed.remove(e.getPlayer().getName());sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());/* busy remains until queued reserve/settle completes */}
 @Override public void close(){closing=true;Bukkit.getServicesManager().unregister(MailService.class,this);io.shutdown();try{if(io.awaitTermination(10,TimeUnit.SECONDS))store.close();else plugin.getLogger().warning("Mailbox IO still pending at shutdown; receipts retained.");}catch(Exception e){Thread.currentThread().interrupt();}sessions.clear();}
}
