package school.magiccodex.discovery;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Lightable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;

/** Event listeners inspect only the affected player/block. Nearby searches run only for players still missing the spell. */
final class NativeConditions implements Listener,AutoCloseable {
    private final MagicDiscovery plugin;private final Map<UUID,Trace> traces=new HashMap<>();private final Map<BlockKey,Water> waters=new HashMap<>();private final NamespacedKey origin;
    private int tick;
    private static class Trace {int sprint,burning,hotUntil;boolean fell,wasAirborne;float rotation,hotRotation;Location still;}
    private record BlockKey(UUID world,int x,int y,int z){static BlockKey of(Block b){return new BlockKey(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}}
    private record Water(UUID player,int expires){}
    NativeConditions(MagicDiscovery plugin){this.plugin=plugin;origin=new NamespacedKey(plugin,"arrow_origin");Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    void join(Player p){var t=new Trace();t.sprint=p.getStatistic(Statistic.SPRINT_ONE_CM);var s=plugin.session(p);t.fell=s!=null&&s.progress.getOrDefault("trace.fell",0d)>0;traces.put(p.getUniqueId(),t);}
    void quit(Player p){traces.remove(p.getUniqueId());}
    void armHotRotation(Player p){var t=traces.get(p.getUniqueId());if(t!=null){t.hotUntil=tick+200;t.hotRotation=0;t.wasAirborne=false;}}
    void add(Player p,String key,double amount){plugin.signal(p.getUniqueId(),key,amount);}
    void fallState(Player p,boolean fell){var t=traces.get(p.getUniqueId());if(t!=null)t.fell=fell;var s=plugin.session(p);if(s!=null){double value=fell?1:0;if(s.progress.getOrDefault("trace.fell",0d)!=value){s.progress.put("trace.fell",value);s.dirty=true;}}}
    private void tick(){
        tick++;for(var p:Bukkit.getOnlinePlayers()){
            var t=traces.get(p.getUniqueId());if(t==null)continue;
            if(!plugin.eligible(p)){t.burning=0;t.hotUntil=0;t.rotation=0;continue;}
            if(plugin.needed(p,"flame_footprints")){if(p.getFireTicks()>0){if(++t.burning>=600)add(p,"burn.survive_thirty",1);}else t.burning=0;}
            if(t.hotUntil>0){if(!p.isOnGround())t.wasAirborne=true;if(tick>t.hotUntil&&!t.wasAirborne||t.wasAirborne&&p.isOnGround())t.hotUntil=0;}
            if(tick%20==0){int value=p.getStatistic(Statistic.SPRINT_ONE_CM);if(value>t.sprint)add(p,"sprint.centimeters",value-t.sprint);t.sprint=value;}
            int interval=Math.clamp(plugin.getConfig().getInt("nearby-check-ticks",40),20,200);
            if(tick%interval==Math.floorMod(p.getEntityId(),interval)&&plugin.needed(p,"wind_decoy")){
                int count=0;for(var e:p.getNearbyEntities(7,7,7))if(e instanceof Monster&&!e.isDead()&&e.getLocation().distanceSquared(p.getLocation())<=49&&++count>=10){add(p,"nearby.ten_monsters",1);break;}
            }
        }
        if(tick%200==0)waters.values().removeIf(v->v.expires()<tick);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p){int amount=e.getItem().getItemStack().getAmount()-e.getRemaining();if(amount>0){add(p,"pickup.items",amount);if(e.getItem().getItemStack().getType()==Material.WET_SPONGE)add(p,"obtain.wet_sponge",1);}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void full(PlayerAttemptPickupItemEvent e){if(e.getRemaining()>0&&e.getPlayer().getInventory().firstEmpty()<0)add(e.getPlayer(),"pickup.full_inventory",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void crop(BlockBreakEvent e){if(e.getBlock().getBlockData() instanceof Ageable age&&age.getAge()<age.getMaximumAge()){
        String key=switch(e.getBlock().getType()){case WHEAT->"break.young_wheat";case CARROTS->"break.young_carrots";case POTATOES->"break.young_potatoes";default->null;};if(key!=null)add(e.getPlayer(),key,1);
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void plant(BlockPlaceEvent e){if(Tag.SAPLINGS.isTagged(e.getBlockPlaced().getType()))add(e.getPlayer(),"plant.sapling",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void jump(PlayerJumpEvent e){if(e.getPlayer().isSprinting())add(e.getPlayer(),"jump.sprinting",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void shear(PlayerShearEntityEvent e){if(e.getEntity() instanceof Sheep)add(e.getPlayer(),"shear.sheep",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void feed(PlayerInteractEntityEvent e){
        if(!(e.getRightClicked() instanceof Chicken chicken))return;var p=e.getPlayer();var hand=e.getHand();var item=p.getInventory().getItem(hand);
        if(!Set.of(Material.WHEAT_SEEDS,Material.BEETROOT_SEEDS,Material.MELON_SEEDS,Material.PUMPKIN_SEEDS,Material.TORCHFLOWER_SEEDS,Material.PITCHER_POD).contains(item.getType()))return;
        int count=item.getAmount();Material type=item.getType();int love=chicken.getLoveModeTicks(),age=chicken.getAge();
        Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||!chicken.isValid())return;var after=p.getInventory().getItem(hand);boolean consumed=after.getType()==type&&after.getAmount()==count-1||count==1&&after.getType().isAir();if(consumed&&(chicken.getLoveModeTicks()>love||age<0&&chicken.getAge()>age+1))add(p,"feed.chicken",1);});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(e.getFinalDamage()<=0)return;if(e.getEntity() instanceof Player p){if(plugin.eligible(p)&&e.getCause()==EntityDamageEvent.DamageCause.FALL)fallState(p,true);if(e.getCause()==EntityDamageEvent.DamageCause.FIRE_TICK)add(p,"damage.fire_tick",e.getFinalDamage());}
        if(e instanceof EntityDamageByEntityEvent hit){Player p=attacker(hit.getDamager());if(p==null)return;if(e.getEntity() instanceof Parrot)add(p,"hit.parrot",1);
            if((e.getEntity() instanceof Monster||e.getEntity() instanceof Player)&&hit.getDamager() instanceof AbstractArrow arrow){long[] start=arrow.getPersistentDataContainer().get(origin,PersistentDataType.LONG_ARRAY);if(start!=null&&start.length==3){var l=e.getEntity().getLocation();double distance=Math.pow(l.getX()-Double.longBitsToDouble(start[0]),2)+Math.pow(l.getY()-Double.longBitsToDouble(start[1]),2)+Math.pow(l.getZ()-Double.longBitsToDouble(start[2]),2);if(distance>=4900)add(p,"arrow.distant_hit",1);}}
        }
    }
    @EventHandler public void death(PlayerDeathEvent e){var p=e.getEntity();var t=traces.get(p.getUniqueId());if(t!=null){boolean fell=t.fell;fallState(p,false);if(fell)add(p,"death.after_fall",1);t.burning=0;t.rotation=0;t.hotUntil=0;}var cause=p.getLastDamageCause();if(cause!=null&&cause.getCause()==EntityDamageEvent.DamageCause.LIGHTNING)add(p,"death.lightning",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        var p=e.getPlayer();var t=traces.get(p.getUniqueId());if(t==null||e.getTo()==null||!plugin.eligible(p))return;
        if(e.getFrom().getWorld()!=e.getTo().getWorld()||e.getFrom().distanceSquared(e.getTo())>0.0004){t.rotation=0;t.still=e.getTo().clone();}
        float delta=wrap(e.getTo().getYaw()-e.getFrom().getYaw());
        if(plugin.needed(p,"pressure_rupture")){if(t.still==null)t.still=e.getFrom().clone();if(t.still.getWorld()!=e.getTo().getWorld()||t.still.distanceSquared(e.getTo())>.0025){t.rotation=0;t.still=e.getTo().clone();}else{t.rotation=accumulate(t.rotation,delta);if(Math.abs(t.rotation)>=3600){add(p,"rotate.stationary_ten",1);t.rotation=0;}}}
        if(t.hotUntil>0&&!p.isOnGround()){t.hotRotation=accumulate(t.hotRotation,delta);if(Math.abs(t.hotRotation)>=360){add(p,"wind.hot_air_rotation",1);t.hotUntil=0;}}
    }
    static float wrap(float angle){return (angle%360+540)%360-180;}
    static float accumulate(float sum,float delta){return Math.abs(delta)<.01f?sum:sum!=0&&Math.signum(sum)!=Math.signum(delta)?delta:sum+delta;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){var t=traces.get(e.getPlayer().getUniqueId());if(t!=null){t.rotation=0;t.hotUntil=0;t.still=null;}}
    private Player attacker(Entity e){if(e instanceof Player p)return p;if(e instanceof Projectile proj&&proj.getShooter() instanceof Player p)return p;return null;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p){if(e.getEntity() instanceof Firework)add(p,"firework.launched",1);if(e.getEntity() instanceof AbstractArrow){var l=e.getEntity().getLocation();e.getEntity().getPersistentDataContainer().set(origin,PersistentDataType.LONG_ARRAY,new long[]{Double.doubleToLongBits(l.getX()),Double.doubleToLongBits(l.getY()),Double.doubleToLongBits(l.getZ())});}}}
    @EventHandler(priority=EventPriority.MONITOR) public void kill(EntityDeathEvent e){
        if(!(e.getEntity() instanceof Monster mob)||mob.getKiller()==null)return;Player p=mob.getKiller();boolean burning=mob.getFireTicks()>0;
        if(burning)add(p,"kill.burning_monster",1);var cause=mob.getLastDamageCause();boolean melee=cause instanceof EntityDamageByEntityEvent damage&&damage.getDamager().equals(p)&&damage.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        if(melee&&burning)add(p,"kill.burning_melee",1);var sword=p.getInventory().getItemInMainHand();if(melee&&Tag.ITEMS_SWORDS.isTagged(sword.getType())&&sword.getEnchantmentLevel(Enchantment.FIRE_ASPECT)>0)add(p,"kill.fire_aspect_sword",1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){if(e.getState()==PlayerFishEvent.State.CAUGHT_ENTITY&&e.getCaught() instanceof Monster)add(e.getPlayer(),"fish.monster_pulled",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void armor(PlayerItemDamageEvent e){var item=e.getItem();if(!(item.getItemMeta() instanceof Damageable d)||d.getDamage()+e.getDamage()<item.getType().getMaxDurability()||item.getType().getMaxDurability()<=0)return;for(var worn:e.getPlayer().getInventory().getArmorContents())if(worn!=null&&worn.equals(item)){add(e.getPlayer(),"armor.broken",1);break;}}
    @EventHandler(priority=EventPriority.MONITOR) public void furnace(FurnaceExtractEvent e){if(e.getItemType().isEdible())add(e.getPlayer(),"furnace.food_taken",e.getItemAmount());if(e.getItemType()==Material.WET_SPONGE)add(e.getPlayer(),"obtain.wet_sponge",1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void inventory(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null||e.getCurrentItem().getType()!=Material.WET_SPONGE)return;Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getInventory().contains(Material.WET_SPONGE))add(p,"obtain.wet_sponge",1);});}
    @EventHandler(priority=EventPriority.MONITOR) public void candle(PlayerInteractEvent e){
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||e.useInteractedBlock()==Event.Result.DENY||e.useItemInHand()==Event.Result.DENY)return;Block block=e.getClickedBlock();if(block==null||!Tag.CANDLES.isTagged(block.getType())&& !Tag.CANDLE_CAKES.isTagged(block.getType())||!(block.getBlockData() instanceof Lightable light))return;
        boolean before=light.isLit();Material held=e.getPlayer().getInventory().getItem(e.getHand()).getType();var p=e.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()->{if(!(block.getBlockData() instanceof Lightable after)||!p.isOnline())return;if(!before&&after.isLit()&&held==Material.FLINT_AND_STEEL)add(p,"candle.lit",1);else if(before&&!after.isLit()&&held==Material.AIR)add(p,"candle.extinguished",1);});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(e.getBucket()!=Material.WATER_BUCKET||waters.size()>30000)return;Block placed=e.getBlock();waters.put(BlockKey.of(placed),new Water(e.getPlayer().getUniqueId(),tick+200));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void flow(BlockFromToEvent e){var owner=waters.get(BlockKey.of(e.getBlock()));if(owner!=null&&owner.expires()>=tick&&waters.size()<30000)waters.put(BlockKey.of(e.getToBlock()),owner);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void form(BlockFormEvent e){if(e.getNewState().getType()!=Material.OBSIDIAN||e.getBlock().getType()!=Material.LAVA)return;
        Water best=null;for(var face:List.of(org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.DOWN,org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST)){Block water=e.getBlock().getRelative(face);if(water.getType()!=Material.WATER)continue;var owner=waters.get(BlockKey.of(water));if(owner!=null&&owner.expires()>=tick&&(best==null||owner.expires()>best.expires()))best=owner;}
        if(best!=null)plugin.signal(best.player(),"water.obsidian_created",1);
    }
    @Override public void close(){traces.clear();waters.clear();}
}
