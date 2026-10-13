package school.magiccodex.client;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents; import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;import net.minecraft.util.*;
/** Reusable quest/friend/system notices. Discovery ACK and interactive friend requests stay separate. */
public final class SharedNotifications {
 private static final NotificationQueue QUEUE=new NotificationQueue(); private record Hit(String id,float x,float y,float w,float h){} private static final java.util.List<Hit> hits=new java.util.ArrayList<>();
private static boolean dialogue(net.minecraft.client.gui.screen.Screen s){return s instanceof DialogueScreen || s!=null&&s.getClass().getName().equals("kr.chacademy.cutscene.client.CutsceneScreen");}
static boolean click(double x,double y,int button){if(button!=0)return false;for(var h:java.util.List.copyOf(hits))if(x>=h.x&&x<h.x+h.w&&y>=h.y&&y<h.y+h.h){QUEUE.dismiss(h.id);hits.remove(h);return true;}return false;}
 private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/notification_card.png"),FONT=Identifier.of("magiccodex","label"),BOLD=Identifier.of("magiccodex","hud_bold");
 public static void offer(String id,String kind,String title,String body){enqueue(new NotificationQueue.Notice(id,kind,school.magiccodex.protocol.JournalProtocol.cut(title,140),school.magiccodex.protocol.JournalProtocol.cut(body,500),3500));}
 public static void unavailable(){enqueue(new NotificationQueue.Notice("broom:unavailable","warning","사용할 수 없는 마법","빗자루에서 내린 뒤 사용할 수 있어요.",2800));}
 private static void enqueue(NotificationQueue.Notice notice){if(QUEUE.offerIfAbsent(notice)){var c=MinecraftClient.getInstance();c.execute(()->{if(c.player!=null)c.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.2f,.25f));});}}
public static void initialize(){ ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{if(dialogue(screen)){ScreenEvents.afterRender(screen).register((s,ctx,x,y,delta)->render(ctx,client,client.player!=null&&client.player.isAlive()&&!client.options.hudHidden,Util.getMeasuringTimeMs()));}if(dialogue(screen)||screen instanceof HudCursorScreen)ScreenMouseEvents.allowMouseClick(screen).register((s,x,y,b)->!click(x,y,b));});ClientPlayConnectionEvents.DISCONNECT.register((h,c)->QUEUE.clear());HudRenderCallback.EVENT.register((ctx,t)->{
  var c=MinecraftClient.getInstance();boolean show=c.world!=null&&c.player!=null&&c.player.isAlive()&&!c.options.hudHidden&&c.getOverlay()==null&&(c.currentScreen==null||c.currentScreen instanceof HudCursorScreen);
  if(!dialogue(c.currentScreen))render(ctx,c,show,Util.getMeasuringTimeMs());
 });}
 static void render(net.minecraft.client.gui.DrawContext ctx,MinecraftClient c,boolean show,long now){
  hits.clear(); int sw=c.getWindow().getScaledWidth(),sh=c.getWindow().getScaledHeight();float scale=Math.min(sw/1920f,sh/1080f)*.9f;float w=560*scale,h=116*scale,gap=12*scale,bottom=230*scale;int capacity=(int)((sh-bottom-24*scale)/(h+gap));
  var notices=QUEUE.frame(now,show,capacity);if(notices.isEmpty())return;var images=UiResources.images();var text=UiResources.text();images.beginFrame();text.beginFrame();
  try{for(var v:notices){float enter=Math.min(1,v.age()/260f),leave=Math.min(1,(v.notice().duration()-v.age())/400f),alpha=Math.min(enter,leave);float x=sw-24*scale-w+(1-enter)*(w+24*scale),y=sh-bottom-h-(float)v.slot()*(h+gap);
   if(alpha>.1f)hits.add(new Hit(v.notice().id(),x,y,w,h)); int tint=((int)(alpha*255)<<24)|0xFFFFFF;ctx.getMatrices().push();ctx.getMatrices().translate(x,y,400);ctx.getMatrices().scale(scale,scale,1);
   // Crop to authoritative nontransparent bounds; no transparent-margin hit/anchor offset.
   if(c.getResourceManager().getResource(PANEL).isPresent())images.drawTexture(ctx,PANEL,0,0,53,160,560,116,2011,416,2117,743,tint);
   else{ctx.fill(0,0,560,116,((int)(alpha*235)<<24)|0x142737);}
   boolean warning=v.notice().kind().equals("warning");
   if(warning){
    int accent=((int)(alpha*235)<<24)|0xD4BC83;ctx.fill(2,18,5,98,accent);
    var iconId=Identifier.of("magiccodex","textures/gui/spell_unavailable.png");
    if(c.getResourceManager().getResource(iconId).isPresent())images.drawTexture(ctx,iconId,31,28,136,151,60,60,979,965,1254,1254,tint);
    else {HudMesh.arc(ctx,61,58,24,2.5f,0,(float)(Math.PI*2),accent);HudMesh.line(ctx,45,74,77,42,3,accent);}
   }
   String icon=switch(v.notice().kind()){case "quest"->"book";case "friend"->"emerald";default->"paper";};if(!warning)images.drawCentered(ctx,"minecraft:textures/item/"+icon+".png",61,58,54,tint);
   String title=v.notice().kind().equals("quest")?"\uc0c8\ub85c\uc6b4 \ud018\uc2a4\ud2b8":v.notice().title();text.draw(ctx,title,110,35,23,tint,BOLD,false);String body=(v.notice().kind().equals("quest")?v.notice().title():v.notice().body()).replace('\n',' ');while(body.length()>1&&text.width(body,18,FONT)>418)body=body.substring(0,body.length()-1);text.draw(ctx,body,110,77,18,tint,FONT,false);ctx.getMatrices().pop();}
  }finally{images.endFrame();}
 }
}
