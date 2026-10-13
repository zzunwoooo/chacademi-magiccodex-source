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
 static final float SOUND_VOLUME=.7f;
 static final String SOUND="ui.toast.in";
 static final String BODY_FACE="hud_bold",TITLE_FACE="ui_extra_bold";
}
