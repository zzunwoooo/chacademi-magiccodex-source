package school.magiccodex.client;
/** Pure ownership/animation policy; a DrawContext is created once for each game frame. */
final class NotificationPresentation {
 enum Surface { WORLD, CURSOR, DIALOGUE, HIDDEN }
 private Object lastContext;
 boolean owns(Object context,boolean screenPass,Surface surface){
  boolean owner=surface==Surface.WORLD?!screenPass:(surface==Surface.CURSOR||surface==Surface.DIALOGUE)&&screenPass;
  if(!owner||context==lastContext)return false;lastContext=context;return true;
 }
 void reset(){lastContext=null;}
 static float alpha(long age,long duration){return Math.clamp(Math.min(age/260f,(duration-age)/400f),0,1);}
 /**
  * Was vanilla ui.toast.in at 0.7: a very soft whoosh that is easy to miss, and notices that arrived while
  * chat was open were announced only after the chat closed. Now a clear chime on top of the toast sound,
  * on the master category so it plays whatever the other volume sliders say.
  */
 static final float SOUND_VOLUME=1f;
 static void playSound(net.minecraft.client.MinecraftClient c){
  var sounds=c.getSoundManager();
  sounds.play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_TOAST_IN,1f,SOUND_VOLUME));
  sounds.play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.35f,.85f));
 }
 static final String BODY_FACE="hud_bold",TITLE_FACE="ui_extra_bold";
}
