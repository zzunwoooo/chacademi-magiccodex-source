package school.magiccodex.paper;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import school.magiccodex.database.DatabaseSettings;

/** Copies only this plugin's persistent player values to MariaDB on one IO worker.
 * 저장 정책: 변경 표시(dirty)된 플레이어만 100틱마다, 전원은 600틱마다(임대 갱신 겸용) 저장한다.
 * 플레이어당 진행 중인 저장은 최대 1건이라 IO 대기열은 접속자 수를 넘지 않는다.
 * 추방은 임대 상실이 확정된 경우(다른 서버가 소유)와 접속 시 불러오기 실패뿐이며, 일시적인 DB 오류는 접속을 유지하고 재시도한다. */
final class PlayerStateBridge implements Listener,AutoCloseable {
    /** 임대(60초)가 만료될 때까지 기다린다: 10틱 × 150회 = 75초. 상대 서버가 비정상 종료해도 추방하지 않는다. */
    private static final int CLAIM_RETRY_TICKS=10,CLAIM_RETRIES=150;
    /** 종료 저장 재시도: 2·4·6·8초 뒤 (IO 스레드를 막지 않는 예약 실행). */
    private static final int QUIT_RETRIES=4;
    private final MagicCodexBridge plugin;
    private final ManaService mana;
    private final ScheduledExecutorService io;
    private final PlayerStateStore store;
    private final Map<UUID,String> sessions=new HashMap<>();
    private final Set<UUID> ready=new HashSet<>();
    // 메인 스레드 전용: dirty=다음 주기에 저장, urgent=진행 중인 저장이 끝나는 즉시 다시 저장, inFlight=IO 대기열에 저장 1건 있음.
    private final Set<UUID> dirty=new HashSet<>(),urgent=new HashSet<>(),inFlight=new HashSet<>();
    private volatile boolean closing;
    /** 일시 오류 직후 5초간은 주기 저장이 DB를 건드리지 않고 바로 재시도 대기로 돌아간다 (대기열 적체 방지). */
    private volatile long circuitUntil;
    private volatile long lastFailureLog;
    private final java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();
    private int passes;private boolean fullDue;
    PlayerStateBridge(MagicCodexBridge plugin,ManaService mana,DatabaseSettings settings)throws Exception {
        this.plugin=plugin;this.mana=mana;
        if(!settings.mariaDb()){io=null;store=null;return;}
        io=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"MagicCodex-player-state");t.setDaemon(true);return t;});
        try{store=io.submit(()->new PlayerStateStore(settings)).get(10,TimeUnit.SECONDS);}
        catch(Exception error){io.shutdownNow();throw error;}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers())load(p);
        Bukkit.getScheduler().runTaskTimer(plugin,this::flush,100L,100L);
    }
    boolean ready(Player p){return store==null||ready.contains(p.getUniqueId());}
    private NamespacedKey key(String name){return new NamespacedKey(plugin,name);}
    private PlayerStateStore.State capture(Player p){
        UUID id=p.getUniqueId();var account=mana.account(id);var data=p.getPersistentDataContainer();
        var slots=new byte[4][];for(int i=0;i<4;i++)slots[i]=data.get(key("accessory_slot_"+i),PersistentDataType.BYTE_ARRAY);
        var credits=new int[3];for(int i=0;i<3;i++)credits[i]=Math.clamp(data.getOrDefault(key("reconfig_credit_"+i),PersistentDataType.INTEGER,0),0,1_000_000);
        int circle=Math.clamp(data.getOrDefault(key("player_circle"),PersistentDataType.INTEGER,1),1,9);
        var cooldowns=new StringBuilder();long now=System.currentTimeMillis();
        account.cooldowns.forEach((spell,until)->{if(until>now&&until-now<=86_400_000&&spell.matches("[a-z0-9_-]{1,64}")&&cooldowns.length()<32000)cooldowns.append(spell).append('=').append(until).append(';');});
        return new PlayerStateStore.State(circle,account.current,account.baseMaximum,account.baseRegen,account.baseHaste,cooldowns.toString(),slots,credits);
    }
    private void apply(Player p,PlayerStateStore.State state){
        var data=p.getPersistentDataContainer();
        data.set(key("player_circle"),PersistentDataType.INTEGER,state.circle());
        data.set(key("mana_current"),PersistentDataType.DOUBLE,state.manaCurrent());
        data.set(key("mana_maximum"),PersistentDataType.DOUBLE,state.manaMaximum());
        data.set(key("mana_regeneration"),PersistentDataType.DOUBLE,state.manaRegeneration());
        data.set(key("magic_haste"),PersistentDataType.DOUBLE,state.magicHaste());
        data.set(key("mana_cooldowns"),PersistentDataType.STRING,state.cooldowns());
        for(int i=0;i<4;i++){if(state.accessories()[i]==null)data.remove(key("accessory_slot_"+i));else data.set(key("accessory_slot_"+i),PersistentDataType.BYTE_ARRAY,state.accessories()[i]);}
        for(int i=0;i<3;i++)data.set(key("reconfig_credit_"+i),PersistentDataType.INTEGER,state.reconfigurationCredits()[i]);
        var account=mana.account(p.getUniqueId());account.baseMaximum=state.manaMaximum();account.baseRegen=state.manaRegeneration();account.baseHaste=state.magicHaste();account.current=state.manaCurrent();account.cooldowns.clear();
        long now=System.currentTimeMillis();for(String part:state.cooldowns().split(";")){
            String[] pair=part.split("=",2);if(pair.length!=2||!pair[0].matches("[a-z0-9_-]{1,64}")||account.cooldowns.size()>=256)continue;
            try{long until=Long.parseLong(pair[1]);if(until>now&&until-now<=86_400_000)account.cooldowns.put(pair[0],until);}catch(NumberFormatException ignored){}
        }
        account.normalize();mana.publish(p.getUniqueId());p.saveData();
    }
    private void load(Player p){
        if(store==null||closing)return;UUID id=p.getUniqueId();String session=UUID.randomUUID().toString();sessions.put(id,session);ready.remove(id);dirty.remove(id);urgent.remove(id);
        loadAttempt(p,session,capture(p),0);
    }
    private void loadAttempt(Player p,String session,PlayerStateStore.State fallback,int retry){
        UUID id=p.getUniqueId();
        io.execute(()->{
            try{
                store.firstOrCurrent(id,fallback);
                if(!store.claim(id,session)){
                    // 이전 서버가 아직 저장·해제하지 않았거나 비정상 종료함: 준비 전 상태(마법·장비·상점 차단)로 임대 만료까지 기다린다.
                    onMain(()->{
                        if(closing||!session.equals(sessions.get(id))||!p.isOnline())return;
                        if(retry>=CLAIM_RETRIES){failure(p,new IllegalStateException("Previous server has not released player state"));return;}
                        if(retry==10)p.sendMessage("§7이전 서버에서 캐릭터 정보를 넘겨받는 중입니다. 잠시만 기다려 주세요.");
                        Bukkit.getScheduler().runTaskLater(plugin,()->{if(!closing&&session.equals(sessions.get(id))&&p.isOnline())loadAttempt(p,session,fallback,retry+1);},CLAIM_RETRY_TICKS);
                    });return;
                }
                var loaded=store.load(id).orElseThrow(()->new IllegalStateException("Missing player state"));
                onMain(()->{
                    if(closing||!session.equals(sessions.get(id))||!p.isOnline())return;
                    // ready 표시 뒤에 장비를 다시 계산해야 장신구(공유 상태)가 반영된다.
                    try{apply(p,loaded);ready.add(id);plugin.refreshEquipment(p);}catch(Exception error){ready.remove(id);failure(p,error);}
                });
            }catch(Exception error){onMain(()->{if(session.equals(sessions.get(id))&&p.isOnline())failure(p,error);});}
        });
    }
    private void onMain(Runnable task){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,task);}
    /** 접속 시 불러오기 실패 전용: 상태 없이 플레이하면 덮어쓰기 위험이 있어 추방한다. */
    private void failure(Player p,Exception error){
        plugin.getLogger().severe("MariaDB player state error for "+p.getUniqueId()+": "+error.getMessage());
        String token=sessions.get(p.getUniqueId());if(token!=null&&store!=null&&!closing)io.execute(()->{try{store.release(p.getUniqueId(),token);}catch(Exception ignored){}});
        p.kickPlayer("캐릭터 정보를 불러오지 못했습니다. 잠시 후 다시 접속해 주세요.");
    }
    /** 저장 UPDATE가 0행이었을 때만: 다른 서버가 이 캐릭터를 소유하므로 여기서는 더 저장하지 않고 내보낸다. */
    private void leaseLost(Player p){
        UUID id=p.getUniqueId();
        plugin.getLogger().severe("MariaDB player state lease lost for "+id+" (another server owns this session)");
        sessions.remove(id);ready.remove(id);dirty.remove(id);urgent.remove(id);
        p.kickPlayer("다른 서버에서 접속 중인 캐릭터입니다. 잠시 후 다시 접속해 주세요.");
    }
    private void transientFailure(Exception error){
        long now=System.currentTimeMillis();circuitUntil=now+5000;int count=failures.incrementAndGet();
        // 플레이어마다가 아니라 장애 구간당 30초에 한 줄.
        if(now-lastFailureLog>=30_000){lastFailureLog=now;plugin.getLogger().warning("MariaDB player state save failing ("+count+" attempt(s) in this streak; players stay online, retrying): "+error.getMessage());}
    }
    private void recovered(){
        int count=failures.getAndSet(0);
        if(count>0){lastFailureLog=0;plugin.getLogger().info("MariaDB player state save recovered after "+count+" failed attempt(s).");}
    }
    /** 마법 시전처럼 잦은 변경: 표시만 하고 다음 주기(100틱)에 한 번 저장한다. */
    void markDirty(Player p){if(store!=null&&!closing&&ready(p))dirty.add(p.getUniqueId());}
    private void flush(){
        if(store==null||closing)return;
        if(++passes%6==0)fullDue=true;
        if(System.currentTimeMillis()<circuitUntil)return;
        if(fullDue){fullDue=false;for(Player p:Bukkit.getOnlinePlayers())saveNow(p);return;}
        for(UUID id:List.copyOf(dirty)){Player p=Bukkit.getPlayer(id);if(p==null)dirty.remove(id);else saveNow(p);}
    }
    private void saveNow(Player p){
        try{save(p);}catch(RuntimeException error){plugin.getLogger().warning("Player state capture failed for "+p.getUniqueId()+": "+error.getMessage());}
    }
    /** 즉시 저장 (장비·승급·재구성 등). 같은 플레이어의 저장이 이미 대기 중이면 끝난 직후 한 번 더 저장한다. */
    void save(Player p){
        if(store==null||closing||!ready(p))return;
        UUID id=p.getUniqueId();String token=sessions.get(id);if(token==null)return;
        if(inFlight.contains(id)){urgent.add(id);return;}
        PlayerStateStore.State state=capture(p);
        dirty.remove(id);urgent.remove(id);inFlight.add(id);
        try{
            io.execute(()->{
                boolean lost=false,failed=false;
                try{
                    if(System.currentTimeMillis()<circuitUntil)failed=true;
                    else{store.save(id,state,token,false);recovered();}
                }catch(PlayerStateStore.LeaseLostException error){lost=true;}
                catch(Exception error){failed=true;transientFailure(error);}
                final boolean gone=lost,again=failed;
                onMain(()->{
                    inFlight.remove(id);
                    if(!token.equals(sessions.get(id)))return;
                    if(gone){if(p.isOnline())leaseLost(p);return;}
                    if(again){dirty.add(id);return;}
                    if(urgent.remove(id)&&p.isOnline())saveNow(p);
                });
            });
        }catch(RejectedExecutionException error){inFlight.remove(id);dirty.add(id);}
    }
    /** 종료·서버 이동 시 저장과 임대 해제. 일시 오류면 IO 스레드에서 몇 번 더 시도한다. */
    private void releaseSave(UUID id,PlayerStateStore.State state,String token,int attempt){
        Runnable task=()->{
            try{store.save(id,state,token,true);recovered();}
            catch(PlayerStateStore.LeaseLostException error){plugin.getLogger().warning("MariaDB player state: final save skipped, lease already moved for "+id);}
            catch(Exception error){
                if(attempt<QUIT_RETRIES){transientFailure(error);releaseSave(id,state,token,attempt+1);}
                else plugin.getLogger().severe("MariaDB player state: final save failed for "+id+" ("+error.getMessage()+"). The last periodic save is kept; the lease expires in 60s.");
            }
        };
        try{if(attempt==0)io.execute(task);else io.schedule(task,attempt*2L,TimeUnit.SECONDS);}
        catch(RejectedExecutionException error){
            // 종료 중에는 예약할 수 없다: IO 스레드에서 한 번만 바로 다시 시도한다.
            if(attempt==0){plugin.getLogger().severe("MariaDB player state: final save not queued for "+id);return;}
            try{Thread.sleep(500);store.save(id,state,token,true);}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            catch(Exception last){plugin.getLogger().severe("MariaDB player state: final save failed during shutdown for "+id+" ("+last.getMessage()+")");}
        }
    }
    private void release(UUID id,String token){
        try{io.execute(()->{try{store.release(id,token);}catch(Exception ignored){}});}catch(RejectedExecutionException ignored){}
    }
    /** 불러오기 전이면 임대만 해제하고, 불러온 뒤면 마지막 상태를 저장하며 해제한다. */
    private void leave(Player p){
        UUID id=p.getUniqueId();boolean loaded=ready.contains(id);
        String token=sessions.remove(id);ready.remove(id);dirty.remove(id);urgent.remove(id);
        if(token==null)return;
        if(!loaded){release(id,token);return;}
        PlayerStateStore.State state;
        try{state=capture(p);}catch(RuntimeException error){plugin.getLogger().severe("Player state capture failed on quit for "+id+": "+error.getMessage());release(id,token);return;}
        releaseSave(id,state,token,0);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){if(store!=null&&!closing)leave(e.getPlayer());}
    @Override public void close(){
        if(store==null)return;
        for(Player p:Bukkit.getOnlinePlayers())leave(p);
        closing=true;
        io.shutdown();
        try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("MariaDB state flush timeout");else store.close();}
        catch(InterruptedException error){Thread.currentThread().interrupt();plugin.getLogger().severe("MariaDB state close interrupted");}
        catch(Exception error){plugin.getLogger().severe("MariaDB state close failed: "+error.getMessage());}
        sessions.clear();ready.clear();dirty.clear();urgent.clear();inFlight.clear();
    }
}
