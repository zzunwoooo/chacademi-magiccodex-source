package school.magiccodex.paper;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.WalletProtocol;
import school.magiccodex.protocol.WalletProtocol.Snapshot;

/** Optional Vault integration: cached public API reflection avoids bundling a second Vault API. */
final class WalletBridge implements PluginMessageListener,Listener,AutoCloseable {
    private static final String ECONOMY="net.milkbowl.vault.economy.Economy";
    private final JavaPlugin plugin;
    private final WalletSubscriptions subscriptions;
    private Object economy;
    private Method balance;
    private long retryAt;
    private static long now(){return System.nanoTime()/1_000_000;}
    WalletBridge(JavaPlugin plugin){
        this.plugin=plugin;
        migrateSettings();
        long interval=Math.clamp(plugin.getConfig().getLong("wallet.refresh-millis",500),250,30000);
        int budget=Math.clamp(plugin.getConfig().getInt("wallet.max-reads-per-second",128),1,1024);
        subscriptions=new WalletSubscriptions(interval,budget);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,WalletProtocol.REQUEST,this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,WalletProtocol.RESPONSE);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        findEconomy();
        plugin.getServer().getScheduler().runTaskTimer(plugin,this::process,1,2);
    }
    private void migrateSettings(){
        var config=plugin.getConfig();
        if(config.contains("wallet.refresh-millis",true))return;
        double old=config.getDouble("wallet.refresh-seconds",5);
        // Upgrade the former defaults; preserve explicitly customized slower intervals/budgets.
        config.set("wallet.refresh-millis",old==5?500:Math.round(Math.clamp(old,0.25,30)*1000));
        if(config.getInt("wallet.max-reads-per-second",32)==32)config.set("wallet.max-reads-per-second",128);
        var source=plugin.getDataFolder().toPath().resolve("config.yml");
        var backup=source.resolveSibling("config.pre-wallet-0.9.1.yml");
        try{
            if(java.nio.file.Files.exists(source) && !java.nio.file.Files.exists(backup))java.nio.file.Files.copy(source,backup);
            config.set("wallet.refresh-seconds",null);
            plugin.saveConfig();
            plugin.getLogger().info("잔액 표시 설정을 갱신했습니다: "+config.getLong("wallet.refresh-millis")+"ms (기존 config 백업 보관)");
        }catch(java.io.IOException error){plugin.getLogger().warning("잔액 설정 백업 실패: 이번 실행에만 새 설정 적용. "+error.getMessage());}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void findEconomy(){
        economy=null;balance=null;retryAt=now()+30000;
        for(Class<?> service:plugin.getServer().getServicesManager().getKnownServices()){
            if(!service.getName().equals(ECONOMY))continue;
            var registration=plugin.getServer().getServicesManager().getRegistration((Class)service);
            if(registration==null)continue;
            try{balance=service.getMethod("getBalance",OfflinePlayer.class);economy=registration.getProvider();}
            catch(ReflectiveOperationException ignored){/* Optional provider unavailable. */}
            break;
        }
    }
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] data){
        if(channel.equals(WalletProtocol.REQUEST) && WalletProtocol.validRequest(data))
            subscriptions.subscribe(player.getUniqueId(),now());
    }
    private void process(){
        if(subscriptions.size()==0)return;
        if(economy==null && now()>=retryAt)findEconomy();
        subscriptions.process(now(),new WalletSubscriptions.Access(){
            public boolean online(UUID id){var p=plugin.getServer().getPlayer(id);return p!=null && p.isOnline();}
            public Snapshot read(UUID id){
                var player=plugin.getServer().getPlayer(id);
                if(player==null || economy==null || balance==null)return Snapshot.unavailable();
                try{return new Snapshot(true,((Number)balance.invoke(economy,player)).doubleValue());}
                catch(ReflectiveOperationException | RuntimeException error){
                    plugin.getLogger().warning("Vault 잔액 조회 실패. 30초 후 다시 연결합니다: "+error.getClass().getSimpleName());
                    economy=null;balance=null;retryAt=now()+30000;return Snapshot.unavailable();
                }
            }
            public void send(UUID id,Snapshot value){
                var player=plugin.getServer().getPlayer(id);
                if(player!=null)player.sendPluginMessage(plugin,WalletProtocol.RESPONSE,WalletProtocol.encode(value));
            }
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event){subscriptions.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void onService(ServiceRegisterEvent event){if(event.getProvider().getService().getName().equals(ECONOMY))findEconomy();}
    @EventHandler public void onServiceRemoved(ServiceUnregisterEvent event){if(event.getProvider().getService().getName().equals(ECONOMY))findEconomy();}
    @Override public void close(){subscriptions.clear();economy=null;balance=null;}
}
