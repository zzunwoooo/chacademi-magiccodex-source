package school.magiccodex.paper;

import java.util.*;
import java.util.concurrent.*;
import net.william278.husksync.api.BukkitHuskSyncAPI;
import net.william278.husksync.data.*;
import net.william278.husksync.event.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Version-pinned 3.8.7 API boundary. Not enabled until the shared CAS store/migration is ready. */
final class BagHuskSnapshotLink implements Listener {
    static final Identifier ID=Identifier.from("magiccodexbridge","bag_page_state",Set.of(Identifier.Dependency.optional(Identifier.INVENTORY.getKey())));
    interface Authority {
        default boolean managedSnapshot(UUID owner, UUID snapshot) { return false; }
        // Must validate the central head/session using CAS; must never infer ownership from local WAL or wall time.
        CompletionStage<Boolean> verifyIncoming(BagSnapshotBinding binding);
        void complete(UUID owner, BagSnapshotBinding binding);
        void block(UUID owner, String reason);
    }
    private final JavaPlugin plugin;
    private final BukkitHuskSyncAPI api;
    private final Authority authority;
    private final Map<UUID,Long> epochs=new HashMap<>();
    private final Map<UUID,BagSnapshotBinding> pending=new HashMap<>();
    private final Map<UUID,UUID> approved=new HashMap<>();
    BagHuskSnapshotLink(JavaPlugin plugin, BukkitHuskSyncAPI api, Authority authority) {
        this.plugin=plugin;this.api=api;this.authority=authority;
    }
    void approveLocalSnapshot(UUID owner,BagSnapshotBinding binding){
        pending.put(owner,binding);approved.put(owner,binding.snapshot());
    }
    void register() {
        var husk=plugin.getServer().getPluginManager().getPlugin("HuskSync");
        if(husk==null || !husk.isEnabled() || !husk.getPluginMeta().getVersion().startsWith("3.8.7"))
            throw new IllegalStateException("Shared bag requires enabled, validated HuskSync 3.8.7");
        api.registerDataSerializer(ID,new BagSnapshotBinding.Codec());
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    private String inventory(DataSnapshot.Unpacked s) {
        Data inv=s.getData().get(Identifier.INVENTORY);
        if(inv==null)throw new IllegalStateException("Missing matching inventory block");
        return api.getDataSerializer(Identifier.INVENTORY).orElseThrow().serialize(inv);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void beforeSave(BukkitDataSaveEvent e) {
        UUID owner=e.getUser().getUuid();
        if(authority.managedSnapshot(owner,e.getData().getId())) return;
        if(DataSnapshot.SaveCause.DEATH.equals(e.getSaveCause())) return; // historical drops snapshot, not current-state mutation
        // Existing admin/API restore behavior remains native. Its bag is unavailable until reconciled.
        authority.block(owner,"External native save/update; shared bag requires reconciliation");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void beforeApply(BukkitPreSyncEvent e) {
        UUID owner=e.getUser().getUuid();UUID snapshot=e.getData().getId();
        if(snapshot.equals(approved.remove(owner))) return;
        String cause=e.getSaveCause().name();
        if(!Set.of("DISCONNECT","SERVER_SHUTDOWN","WORLD_SAVE","SAVE_COMMAND").contains(cause)) {
            authority.block(owner,"External native restore/update; shared bag requires reconciliation");return;
        }
        var preliminary=api.unpackSnapshot(e.getData());
        if(!(preliminary.getData().get(ID) instanceof BagSnapshotBinding)) {
            authority.block(owner,"No reviewed shared bag bootstrap; vanilla synchronization remains native");return;
        }
        // Intentional deferred apply: HuskSync's lock remains held. Never manually complete/unlock on rejection.
        e.setCancelled(true);authority.block(owner,"Verifying shared bag snapshot");
        long epoch=epochs.merge(owner,1L,Long::sum);pending.remove(owner);
        try {
            var data=api.unpackSnapshot(e.getData());var raw=data.getData().get(ID);
            if(!(raw instanceof BagSnapshotBinding binding))throw new IllegalStateException("Snapshot requires reviewed bag bootstrap/migration");
            binding.verify(owner,snapshot,inventory(data));
            authority.verifyIncoming(binding).whenComplete((ok,error)->plugin.getServer().getScheduler().runTask(plugin,()->{
                if(!Objects.equals(epochs.get(owner),epoch))return;
                var user=api.getOnlineUser(owner).filter(u->!u.hasDisconnected()).orElse(null);
                if(user==null)return;
                if(error!=null || !Boolean.TRUE.equals(ok)){authority.block(owner,"Shared bag ownership check failed; original snapshot retained");return;}
                pending.put(owner,binding);approved.put(owner,snapshot);
                user.applySnapshot(e.getData(),DataSnapshot.UpdateCause.SYNCHRONIZED);
            }));
        } catch(RuntimeException ex){authority.block(owner,"Bag snapshot validation failed: "+ex.getClass().getSimpleName());}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void afterApply(BukkitSyncCompleteEvent e) {
        UUID owner=e.getUser().getUuid();var expected=pending.remove(owner);
        if(expected==null){authority.block(owner,"No verified shared bag snapshot");return;}
        var actual=e.getUser().getCustomDataStore().get(ID);
        if(!expected.equals(actual)){authority.block(owner,"Applied bag snapshot identity differs");return;}
        authority.complete(owner,expected);
        // HuskSync performs its own unlock after this event. Bag input must also check OnlineUser.isLocked().
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        UUID owner=e.getPlayer().getUniqueId();epochs.merge(owner,1L,Long::sum);pending.remove(owner);approved.remove(owner);
        // The LOCKSTEP hook already sealed a participant before the native quit callback.
        // Do not invalidate its immutable, in-flight handoff merely because this quit event finished.
    }
}
