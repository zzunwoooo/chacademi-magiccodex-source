package kr.chacademy.storyplugin;
import java.util.*;import java.lang.reflect.*;import org.bukkit.*;import org.bukkit.entity.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class QuestGuideTest {
 static void ready(QuestPortalReentryTest.Fixture f) throws Exception {
  QuestPortalReentryTest.set(f.engine,"ready",true);when(f.world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));when(f.player.isOnline()).thenReturn(true);when(f.player.getWorld()).thenReturn(f.world);
  when(f.player.getListeningPluginChannels()).thenReturn(Set.of(QuestCodec.STEP,QuestCodec.GUIDE));f.bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);f.step.trigger.x=40;
 }
 @Test void unfinishedPortalStepStillGuidesAndIncludesItsTargetInTheStepPacket() throws Exception {
  try(var f=new QuestPortalReentryTest.Fixture()) {ready(f);f.step.conds.add(new QuestDefs.Cond(QuestDefs.CondType.ITEM,"minecraft:stone",99,"items",false,List.of()));
   assertEquals(QuestEngine.GUIDE_OK,f.engine.guide(f.player));verify(f.player).sendPluginMessage(eq(f.plugin),eq(QuestCodec.GUIDE),argThat(b->Arrays.equals(b,QuestCodec.guide(1))));
   Method m=QuestEngine.class.getDeclaredMethod("view",int.class,QuestDefs.Step.class,int.class,int[].class,boolean.class);m.setAccessible(true);
   var view=(QuestCodec.StepView)m.invoke(f.engine,1,f.step,1,new int[]{0},false);assertFalse(view.ready());assertEquals(QuestCodec.TARGET_LOCATION,view.targetType());assertEquals(40,view.x());
  }
 }
 @Test void arrivalAtExactlyTenAndOtherWorldDoNotSendGuide() throws Exception {
  try(var f=new QuestPortalReentryTest.Fixture()){ready(f);f.step.trigger.x=10;assertEquals(10,QuestEngine.GUIDE_NEAR_DISTANCE);assertEquals(QuestEngine.GUIDE_NEAR,f.engine.guide(f.player));
   f.step.trigger.x=40;when(f.player.getWorld()).thenReturn(mock(World.class));assertEquals(QuestEngine.GUIDE_OTHER_WORLD,f.engine.guide(f.player));verify(f.player,never()).sendPluginMessage(any(),eq(QuestCodec.GUIDE),any());}
 }
 @Test void legacyClientMissingStepOrPreviewKeepsLegacyFallback() throws Exception {
  try(var f=new QuestPortalReentryTest.Fixture()){ready(f);when(f.player.getListeningPluginChannels()).thenReturn(Set.of(QuestCodec.STEP));assertEquals(QuestEngine.GUIDE_NONE,f.engine.guide(f.player));
   when(f.player.getListeningPluginChannels()).thenReturn(Set.of(QuestCodec.STEP,QuestCodec.GUIDE));Set<UUID> previews=QuestPortalReentryTest.field(f.engine,"previewing");previews.add(f.id);assertEquals(QuestEngine.GUIDE_NONE,f.engine.guide(f.player));previews.clear();
   QuestDefs defs=QuestPortalReentryTest.field(f.engine,"defs");defs.steps.clear();assertEquals(QuestEngine.GUIDE_NONE,f.engine.guide(f.player));verify(f.player,never()).sendPluginMessage(any(),eq(QuestCodec.GUIDE),any());}
 }
 public static class Npc {Entity entity;Location stored;public Entity getEntity(){return entity;}public Location getStoredLocation(){return stored;}}
 public static class Registry {Npc npc;public Object getById(int id){return id==12?npc:null;}}
 @Test void npcTargetUsesLiveThenStoredLocationWithoutConsumingQuestProgress() throws Exception {
  try(var f=new QuestPortalReentryTest.Fixture()){ready(f);f.step.trigger.type=QuestDefs.TrigType.NPC;f.step.trigger.server="";f.step.trigger.npc=12;
   var registry=new Registry();registry.npc=new Npc();registry.npc.entity=mock(Entity.class);when(registry.npc.entity.getLocation()).thenReturn(new Location(f.world,40,64,0));registry.npc.stored=new Location(f.world,60,64,0);
   QuestPortalReentryTest.set(f.engine,"citizens",true);QuestPortalReentryTest.set(f.engine,"registry",registry);QuestPortalReentryTest.set(f.engine,"registryById",Registry.class.getMethod("getById",int.class));QuestPortalReentryTest.set(f.engine,"npcEntity",Npc.class.getMethod("getEntity"));QuestPortalReentryTest.set(f.engine,"npcStored",Npc.class.getMethod("getStoredLocation"));
   var before=f.state.phase;assertEquals(QuestEngine.GUIDE_OK,f.engine.guide(f.player));
   assertEquals(QuestEngine.GUIDE_OFF,f.engine.guide(f.player));verify(f.player).sendPluginMessage(eq(f.plugin),eq(QuestCodec.GUIDE),argThat(b->Arrays.equals(b,QuestCodec.guide(0))));
   registry.npc.entity=null;assertEquals(QuestEngine.GUIDE_OK,f.engine.guide(f.player));assertEquals(before,f.state.phase);
  }
 }
}
