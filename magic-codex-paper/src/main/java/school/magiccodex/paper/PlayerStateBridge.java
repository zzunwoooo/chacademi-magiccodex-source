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

/** Copies only this plugin's persistent player values to MariaDB on one IO worker. */
final class PlayerStateBridge implements Listener,AutoCloseable {
    private final MagicCodexBridge plugin;
    private final ManaService mana;
    private final ExecutorService io;
    private final PlayerStateStore store;
    private final Map<UUID,String> sessions=new HashMap<>();
    private final Set<UUID> ready=new HashSet<>();
    private volatile boolean closing;
    PlayerStateBridge(MagicCodexBridge plugin,ManaService mana,DatabaseSettings settings)throws Exception {
        this.plugin=plugin;this.mana=mana;
        if(!settings.mariaDb()){io=null;store=null;return;}
        io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MagicCodex-player-state");t.setDaemon(true);return t;});
        try{store=io.submit(()->new PlayerStateStore(settings)).get(10,TimeUnit.SECONDS);}
        catch(Exception error){io.shutdownNow();throw error;}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers())load(p);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player p:Bukkit.getOnlinePlayers())save(p);},600L,600L);
    }
    boolean ready(Player p){return store==null||ready.contains(p.getUniqueId());}
    private NamespacedKey key(String name){return new NamespacedKey(plugin,name);}
    private double number(Player p,String name,double fallback){
        Double v=p.getPersistentDataContainer().get(key(name),PersistentDataType.DOUBLE);
        return v!=null&&Double.isFinite(v)&&v>=0&&v<=1_000_000?v:fallback;
    }
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
        account.normalize();mana.publish(p.getUniqueId());plugin.refreshEquipment(p);p.saveData();
    }
    private void load(Player p){
        if(store==null||closing)return;UUID id=p.getUniqueId();String session=UUID.randomUUID().toString();sessions.put(id,session);ready.remove(id);
        loadAttempt(p,session,capture(p),0);
    }
    private void loadAttempt(Player p,String session,PlayerStateStore.State fallback,int retry){
        UUID id=p.getUniqueId();
        io.execute(()->{
            try{
                store.firstOrCurrent(id,fallback);
                if(!store.claim(id,session)){
                    onMain(()->{
                        if(closing||!session.equals(sessions.get(id))||!p.isOnline())return;
                        if(retry>=40){failure(p,new IllegalStateException("Previous server has not released player state"));return;}
                        Bukkit.getScheduler().runTaskLater(plugin,()->loadAttempt(p,session,fallback,retry+1),5L);
                    });return;
                }
                var loaded=store.load(id).orElseThrow(()->new IllegalStateException("Missing player state"));
                onMain(()->{
                    if(closing||!session.equals(sessions.get(id))||!p.isOnline())return;
                    try{apply(p,loaded);ready.add(id);}catch(Exception error){failure(p,error);}
                });
            }catch(Exception error){onMain(()->{if(session.equals(sessions.get(id))&&p.isOnline())failure(p,error);});}
        });
    }
    private void onMain(Runnable task){if(!closing&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,task);}
    private void failure(Player p,Exception error){
        plugin.getLogger().severe("MariaDB player state error for "+p.getUniqueId()+": "+error.getMessage());
        String token=sessions.get(p.getUniqueId());if(token!=null&&store!=null&&!closing)io.execute(()->{try{store.release(p.getUniqueId(),token);}catch(Exception ignored){}});
        p.kickPlayer("캐릭터 정보를 불러오지 못했습니다. 잠시 후 다시 접속해 주세요.");
    }
    void save(Player p){save(p,false);}
    private void save(Player p,boolean release){
        if(store==null||closing||!ready(p))return;
        UUID id=p.getUniqueId();String token=sessions.get(id);if(token==null)return;PlayerStateStore.State state=capture(p);
        io.execute(()->{try{store.save(id,state,token,release);}catch(Exception error){plugin.getLogger().severe("MariaDB player state save failed for "+id+": "+error.getMessage());
            if(!release&&!closing)onMain(()->{if(token.equals(sessions.get(id))&&p.isOnline())failure(p,new IllegalStateException("Player state ownership lost"));});}});
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){
        UUID id=e.getPlayer().getUniqueId();boolean loaded=ready.contains(id);save(e.getPlayer(),true);
        String token=sessions.remove(id);ready.remove(id);
        if(!loaded&&token!=null&&store!=null)io.execute(()->{try{store.release(id,token);}catch(Exception ignored){}});
    }
    @Override public void close(){
        if(store==null)return;
        for(Player p:Bukkit.getOnlinePlayers())save(p,true);
        var remaining=Map.copyOf(sessions);
        closing=true;
        io.execute(()->remaining.forEach((id,token)->{try{store.release(id,token);}catch(Exception ignored){}}));
        io.shutdown();
        try{if(!io.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("MariaDB state flush timeout");else store.close();}
        catch(InterruptedException error){Thread.currentThread().interrupt();plugin.getLogger().severe("MariaDB state close interrupted");}
        catch(Exception error){plugin.getLogger().severe("MariaDB state close failed: "+error.getMessage());}
        sessions.clear();ready.clear();
    }
}
