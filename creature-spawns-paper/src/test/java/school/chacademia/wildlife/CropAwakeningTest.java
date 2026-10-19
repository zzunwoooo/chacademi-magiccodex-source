package school.chacademia.wildlife;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.mobs.MythicMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class CropAwakeningTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<MythicBukkit> mythic;
    private BukkitScheduler scheduler;
    private YamlConfiguration config;
    private CropAwakening crops;
    private Player player;
    private Block block;
    private Ageable age;

    @BeforeEach void setup() {
        bukkit=mockStatic(Bukkit.class);
        mythic=mockStatic(MythicBukkit.class);
        scheduler=mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        var api=mock(MythicBukkit.class,RETURNS_DEEP_STUBS);
        mythic.when(MythicBukkit::inst).thenReturn(api);
        when(api.getMobManager().getMythicMob(anyString())).thenReturn(Optional.of(mock(MythicMob.class)));
        var plugin=mock(WildlifePlugin.class);
        config=new YamlConfiguration();
        config.set("crop-awakening.chance",1.0);
        config.set("crop-awakening.max-per-player",3);
        config.set("crop-awakening.crops.WHEAT","wheat");
        config.set("crop-awakening.crops.PUMPKIN","pumpkin");
        when(plugin.getConfig()).thenReturn(config);
        crops=new CropAwakening(plugin);
        crops.reload();
        player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        block=mock(Block.class);
        when(block.getType()).thenReturn(Material.WHEAT);
        var world=mock(World.class);
        when(world.getName()).thenReturn("world");
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenAnswer(call -> new Location(world,1,64,1));
        age=mock(Ageable.class);
        when(age.getMaximumAge()).thenReturn(7);
        when(age.getAge()).thenReturn(7);
        when(block.getBlockData()).thenReturn(age);
    }
    @AfterEach void closeMocks() { mythic.close(); bukkit.close(); }
    private BlockBreakEvent harvest() {
        var event=new BlockBreakEvent(block,player);
        crops.harvest(event);
        return event;
    }
    @Test void sameTickHarvestsReserveOnlyThreeSpawns() {
        for(int i=0;i<3;i++) assertFalse(harvest().isDropItems());
        assertTrue(harvest().isDropItems());
        verify(scheduler,times(3)).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
    }
    @Test void zeroChanceKeepsDropsAndSchedulesNothing() {
        config.set("crop-awakening.chance",0.0);
        assertTrue(harvest().isDropItems());
        verifyNoInteractions(scheduler);
    }
    @Test void immatureCropIsIgnored() {
        when(age.getAge()).thenReturn(6);
        assertTrue(harvest().isDropItems());
        verifyNoInteractions(scheduler);
    }
    @Test void creativeIsIgnored() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(harvest().isDropItems());
        verifyNoInteractions(scheduler);
    }
    @Test void excludedWorldIsIgnored() {
        config.set("crop-awakening.worlds",List.of("school"));
        assertTrue(harvest().isDropItems());
        verifyNoInteractions(scheduler);
    }
    @Test void placedPumpkinWithoutAttachedStemIsIgnored() {
        when(block.getType()).thenReturn(Material.PUMPKIN);
        when(block.getBlockData()).thenReturn(mock(Directional.class));
        var neighbor=mock(Block.class);
        when(neighbor.getType()).thenReturn(Material.AIR);
        when(block.getRelative(any(BlockFace.class))).thenReturn(neighbor);
        assertTrue(harvest().isDropItems());
        verifyNoInteractions(scheduler);
    }
    @Test void attachedStemMustFaceHarvestedPumpkin() {
        when(block.getType()).thenReturn(Material.PUMPKIN);
        when(block.getBlockData()).thenReturn(mock(Directional.class));
        var neighbor=mock(Block.class);
        when(neighbor.getType()).thenReturn(Material.ATTACHED_PUMPKIN_STEM);
        var facing=mock(Directional.class);
        when(facing.getFacing()).thenReturn(BlockFace.SOUTH);
        when(neighbor.getBlockData()).thenReturn(facing);
        when(block.getRelative(any(BlockFace.class))).thenReturn(neighbor);
        assertFalse(harvest().isDropItems());
        verify(scheduler).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
    }
    @Test void schedulingFailureReleasesReservation() {
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class)))
            .thenThrow(new IllegalStateException("scheduler stopped")).thenReturn(null);
        assertThrows(IllegalStateException.class,this::harvest);
        for(int i=0;i<3;i++) assertFalse(harvest().isDropItems());
        verify(scheduler,times(4)).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
    }
}
