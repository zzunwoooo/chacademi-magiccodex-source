package school.magiccodex.paper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.MailboxProtocol;
import school.magiccodex.protocol.MailboxProtocol.*;

/** Main-thread inventory, bounded JDBC worker, owner-fenced claims and durable origin receipts. */
final class MailboxBridge implements MailService,PluginMessageListener,Listener,AutoCloseable {
 private final MagicCodexBridge plugin;private final MailboxStore store;private final String origin;
 private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"MagicCodex-mailbox-io");t.setDaemon(true);return t;});
 private record Session(Player connection,long token,long sequence){}
 private final Map<UUID,Session> sessions=new HashMap<>();private final Set<UUID> busy=new HashSet<>();private final Map<UUID,Long> limits=new HashMap<>();private boolean closing;
 MailboxBridge(MagicCodexBridge plugin)throws Exception{
  this.plugin=plugin;Path dir=plugin.getDataFolder().toPath(),identity=dir.resolve("mailbox-origin.txt");
  if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString(),StandardOpenOption.CREATE_NEW);
  origin=UUID.fromString(Files.readString(identity).strip()).toString();
  try{store=io.submit(()->new MailboxStore(DatabaseSettings.load(dir.resolve("database.properties")),dir.resolve("mailbox.db"))).get(15,TimeUnit.SECONDS);}catch(Exception e){io.shutdownNow();throw e;}
  Bukkit.getMessenger().registerIncomingPluginChannel(plugin,MailboxProtocol.REQUEST,this);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,MailboxProtocol.RESPONSE);
  Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getServicesManager().register(MailService.class,this,plugin,ServicePriority.Normal);
 }
 private boolean current(Player p){return !closing&&p.isOnline()&&Bukkit.getPlayer(p.getUniqueId())==p;}
 private boolean allowed(Player p){return current(p)&&p.hasPermission("magiccodex.mailbox")&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.playerStateReady(p);}
 private void main(Runnable action){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,action);}
 private <T>void work(Callable<T> task,Consumer<T> success,Consumer<String> fail){try{io.execute(()->{try{T value=task.call();main(()->success.accept(value));}catch(Exception e){plugin.getLogger().warning("Mailbox operation held: "+e.getClass().getSimpleName());main(()->fail.accept(e instanceof IllegalStateException?e.getMessage():"우편 처리 기록을 확인해야 합니다. 다시 지급하지 않습니다."));}});}catch(RejectedExecutionException e){fail.accept("우편함이 바쁩니다. 잠시 뒤 다시 시도해 주세요.");}}
 @Override public CompletableFuture<UUID> sendSystem(String source,UUID recipient,String title,String body,List<ItemStack> attachments){
  if(!Bukkit.isPrimaryThread())throw new IllegalStateException("server thread required");
  if(closing||source==null||source.isBlank()||source.length()>160||recipient==null||title==null||title.isBlank()||title.length()>80||body==null||body.length()>2000||attachments==null||attachments.size()>9)throw new IllegalArgumentException("mail limits");
  var stored=new ArrayList<MailboxStore.Item>();int index=0;
  for(ItemStack original:attachments){if(original==null||original.getType().isAir()||original.getAmount()<1||original.getAmount()>original.getMaxStackSize()||original.getAmount()>99)throw new IllegalArgumentException("attachment stack");ItemStack copy=original.clone();byte[] bytes=copy.serializeAsBytes();if(bytes.length>32768)throw new IllegalArgumentException("attachment bytes");String name=copy.displayName().toString();name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(copy.displayName());if(name.length()>120)name=name.substring(0,120);stored.add(new MailboxStore.Item(index++,name,copy.getAmount(),bytes,"ready","",""));}
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
   case MailboxProtocol.DELETE_CLAIMED -> work(()->store.deleteClaimed(owner),n->snapshot(p,r,token,n+"개의 수령한 우편을 삭제했습니다."),m->finishError(p,r,token,m));
   default -> recover(p,r,token);
  }
 }
 private NamespacedKey receipt(String token){return new NamespacedKey(plugin,"mail_receipt_"+token.replace("-",""));}
 static Path playerFile(Player p){return Bukkit.getWorlds().getFirst().getWorldFolder().toPath().resolve("playerdata").resolve(p.getUniqueId()+".dat");}
 private void recover(Player p,Request r,long session){
  UUID owner=p.getUniqueId();Set<String> receipts=new HashSet<>();for(var key:p.getPersistentDataContainer().getKeys())if(key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))&&key.getKey().startsWith("mail_receipt_"))receipts.add(key.getKey().substring(13));
  Path savedFile=playerFile(p);work(()->{var pending=store.pending(owner);var tokens=new HashMap<String,String>();for(var ref:pending)tokens.put(ref.item().token(),ref.item().origin());boolean blocked=false;for(var entry:tokens.entrySet()){if(entry.getValue().equals(origin)&&receipts.contains(entry.getKey().replace("-",""))&&DeliveryReceipt.saved(savedFile,receipt(entry.getKey()).toString()))store.settle(owner,entry.getKey(),true);else blocked=true;}return blocked;},blocked->snapshot(p,r,session,blocked?"이전 수령 기록 확인이 필요합니다. 자동으로 재지급하지 않습니다.":""),m->finishError(p,r,session,m));
 }
 private record Snapshot(MailboxStore.Page page,MailboxStore.Mail mail){}
 private void snapshot(Player p,Request r,long token,String message){work(()->{var page=store.list(p.getUniqueId(),r.page());String selected=r.mail();if(selected.isEmpty()&&!page.entries().isEmpty())selected=page.entries().getFirst().id();return new Snapshot(page,selected.isEmpty()?null:store.detail(p.getUniqueId(),selected));},data->{busy.remove(p.getUniqueId());if(!current(p))return;var s=sessions.get(p.getUniqueId());if(s==null||s.token!=token||s.sequence!=r.sequence())return;var attachments=new ArrayList<Attachment>();if(data.mail!=null)for(var i:data.mail.items()){ItemStack stack=ItemStack.deserializeBytes(i.bytes());byte[] preview=i.bytes().length<=3072?i.bytes():new ItemStack(stack.getType(),stack.getAmount()).serializeAsBytes();attachments.add(new Attachment(i.index(),i.name(),i.amount(),i.state().equals("claimed"),preview.length<=3072?preview:new byte[0]));}p.sendPluginMessage(plugin,MailboxProtocol.RESPONSE,MailboxProtocol.encode(new Response(r.sequence(),token,message,r.page(),data.page.more(),data.page.entries(),data.mail==null?"":data.mail.id(),data.mail==null?"":data.mail.body(),List.copyOf(attachments))));},m->finishError(p,r,token,m));}
 private void finishError(Player p,Request r,long token,String message){busy.remove(p.getUniqueId());if(current(p))p.sendPluginMessage(plugin,MailboxProtocol.RESPONSE,MailboxProtocol.encode(new Response(r.sequence(),token,message,r.page(),false,List.of(),"","",List.of())));}
 static ItemStack[] copy(ItemStack[] source){return Arrays.stream(source).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
 static boolean same(ItemStack[] a,ItemStack[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++){if(a[i]==null||a[i].getType().isAir()){if(b[i]!=null&&!b[i].getType().isAir())return false;}else if(b[i]==null||!Arrays.equals(a[i].serializeAsBytes(),b[i].serializeAsBytes()))return false;}return true;}
 /** Inserts a whole attachment or leaves the simulated inventory unchanged. Never drops overflow. */
 static boolean insert(ItemStack[] inventory,ItemStack value){var next=copy(inventory);int left=value.getAmount();for(int i=0;i<next.length&&left>0;i++){var old=next[i];if(old!=null&&!old.getType().isAir()&&old.isSimilar(value)){int n=Math.min(left,Math.max(0,Math.min(64,old.getMaxStackSize())-old.getAmount()));old.setAmount(old.getAmount()+n);left-=n;}}for(int i=0;i<next.length&&left>0;i++)if(next[i]==null||next[i].getType().isAir()){int n=Math.min(left,Math.min(64,value.getMaxStackSize()));next[i]=value.clone();next[i].setAmount(n);left-=n;}if(left!=0)return false;System.arraycopy(next,0,inventory,0,next.length);return true;}
 private void claim(Player p,Request r,long session){
  if(r.action()==MailboxProtocol.CLAIM&&r.mail().isEmpty()){finishError(p,r,session,"우편을 선택해 주세요.");return;}
  UUID owner=p.getUniqueId();work(()->store.ready(owner,r.action()==MailboxProtocol.CLAIM_ALL?"":r.mail()),refs->{
   if(!allowed(p)){busy.remove(owner);return;}var before=copy(p.getInventory().getStorageContents());var next=copy(before);var fits=new ArrayList<MailboxStore.Ref>();for(var ref:refs)if(insert(next,ItemStack.deserializeBytes(ref.item().bytes())))fits.add(ref);
   if(fits.isEmpty()){snapshot(p,r,session,"수령할 첨부가 없거나 인벤토리 공간이 부족합니다.");return;}
   String claim=UUID.randomUUID().toString();work(()->{store.reserve(owner,List.copyOf(fits),claim,origin);return true;},reserved->{
    if(!allowed(p)||!same(before,p.getInventory().getStorageContents())){work(()->{store.settle(owner,claim,false);return true;},v->snapshot(p,r,session,"인벤토리가 변경되었습니다. 다시 수령해 주세요."),m->finishError(p,r,session,m));return;}
    // Item movement and receipt are written into the same vanilla player-data snapshot on the origin server.
    // A crash with no durable receipt remains pending for review; never blindly release/replay it.
    try{p.getInventory().setStorageContents(next);p.getPersistentDataContainer().set(receipt(claim),PersistentDataType.BYTE,(byte)1);p.saveData();}
    catch(RuntimeException e){finishError(p,r,session,"수령 저장을 확인해야 합니다. 자동으로 재지급하지 않습니다.");return;}
    Path file=playerFile(p);String savedKey=receipt(claim).toString();work(()->{if(!DeliveryReceipt.saved(file,savedKey))throw new IllegalStateException("아이템 저장 영수증 확인이 필요합니다. 자동으로 재지급하지 않습니다.");store.settle(owner,claim,true);return true;},v->{if(current(p)){p.getPersistentDataContainer().remove(receipt(claim));p.saveData();}snapshot(p,r,session,fits.size()+"개의 첨부를 수령했습니다. 공간이 부족한 첨부는 우편에 남아 있습니다.");},m->finishError(p,r,session,m));
   },m->finishError(p,r,session,m));
  },m->finishError(p,r,session,m));
 }
 @EventHandler public void quit(PlayerQuitEvent e){sessions.remove(e.getPlayer().getUniqueId());limits.remove(e.getPlayer().getUniqueId());/* busy remains until queued reserve/settle completes */}
 @Override public void close(){closing=true;Bukkit.getServicesManager().unregister(MailService.class,this);io.shutdown();try{if(io.awaitTermination(10,TimeUnit.SECONDS))store.close();else plugin.getLogger().warning("Mailbox IO still pending at shutdown; receipts retained.");}catch(Exception e){Thread.currentThread().interrupt();}sessions.clear();}
}
