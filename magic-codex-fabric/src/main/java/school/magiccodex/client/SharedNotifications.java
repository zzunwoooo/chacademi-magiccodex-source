package school.magiccodex.client;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents; import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;import net.minecraft.util.*;
/** Reusable quest/friend/system notices. Discovery ACK and interactive friend requests stay separate. */
public final class SharedNotifications {
 private static final NotificationQueue QUEUE=new NotificationQueue(); private static final NotificationPresentation PRESENTATION=new NotificationPresentation(); private static boolean initialized; private static HudTextureCache images; private static CodexTypography text; private static int resourceGeneration=-1; private record Hit(String id,float x,float y,float w,float h){} private static final java.util.List<Hit> hits=new java.util.ArrayList<>();
private static boolean dialogue(net.minecraft.client.gui.screen.Screen s){return s instanceof DialogueScreen || s!=null&&s.getClass().getName().equals("kr.chacademy.cutscene.client.CutsceneScreen");}
static boolean click(double x,double y,int button){
        if(HudCustomization.editingGesture())return false;var hudPoint=HudCustomization.pointer("notifications",x,y);x=hudPoint[0];y=hudPoint[1];if(button!=0)return false;for(var h:java.util.List.copyOf(hits))if(x>=h.x&&x<h.x+h.w&&y>=h.y&&y<h.y+h.h){QUEUE.dismiss(h.id);hits.remove(h);return true;}return false;}
 private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/notification_card.png"),FONT=Identifier.of("magiccodex",NotificationPresentation.BODY_FACE),BOLD=Identifier.of("magiccodex",NotificationPresentation.TITLE_FACE);
 public static void offer(String id,String kind,String title,String body){if(kind==null||kind.isBlank())kind="system";enqueue(new NotificationQueue.Notice(id,kind,school.magiccodex.protocol.JournalProtocol.cut(title,140),school.magiccodex.protocol.JournalProtocol.cut(body,500),3500));}
 /** Item icons for notices (e.g. shop receipts), keyed by notice id; bounded like the queue. */
 private static final java.util.Map<String,net.minecraft.item.ItemStack> ICONS=new java.util.LinkedHashMap<>(){protected boolean removeEldestEntry(java.util.Map.Entry<String,net.minecraft.item.ItemStack> e){return size()>16;}};
 /** A "shop" notice whose left icon is the given item (its resource-pack icon, else the item's own model). */
 public static void item(String id,String title,String body,net.minecraft.item.ItemStack icon){var stack=icon==null?net.minecraft.item.ItemStack.EMPTY:icon.copy();MinecraftClient.getInstance().execute(()->{if(!stack.isEmpty())ICONS.put(id,stack);QUEUE.offerIfAbsent(new NotificationQueue.Notice(id,"shop",school.magiccodex.protocol.JournalProtocol.cut(title,140),school.magiccodex.protocol.JournalProtocol.cut(body,500),4500));});}
 public static void unavailable(){enqueue(new NotificationQueue.Notice("broom:unavailable","warning","사용할 수 없는 마법","빗자루에서 내린 뒤 사용할 수 있어요.",2800));}
 private static void enqueue(NotificationQueue.Notice notice){MinecraftClient.getInstance().execute(()->QUEUE.offerIfAbsent(notice));}
 private static NotificationPresentation.Surface surface(MinecraftClient c){return c.currentScreen==null?NotificationPresentation.Surface.WORLD:c.currentScreen instanceof HudCursorScreen||c.currentScreen instanceof ShopScreen||c.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen?NotificationPresentation.Surface.CURSOR:dialogue(c.currentScreen)?NotificationPresentation.Surface.DIALOGUE:NotificationPresentation.Surface.HIDDEN;}
 private static boolean visible(MinecraftClient c){return c.world!=null&&c.player!=null&&c.player.isAlive()&&!c.options.hudHidden&&c.getOverlay()==null&&surface(c)!=NotificationPresentation.Surface.HIDDEN;}
 private static void closeResources(){if(images!=null)images.close();if(text!=null)text.close();images=null;text=null;resourceGeneration=-1;}
 private static void prepare(MinecraftClient c){if(resourceGeneration!=UiResources.generation()){closeResources();resourceGeneration=UiResources.generation();}if(images==null)images=new HudTextureCache(c);if(text==null)text=new CodexTypography(c);}
 public static void initialize(){
  if(initialized)return;initialized=true;
  ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
   if(dialogue(screen)||screen instanceof HudCursorScreen||screen instanceof ShopScreen||screen instanceof net.minecraft.client.gui.screen.ChatScreen){
    ScreenEvents.afterRender(screen).register((s,ctx,x,y,delta)->{if(client.currentScreen==s&&PRESENTATION.owns(ctx,true,surface(client)))render(ctx,client,visible(client),Util.getMeasuringTimeMs());});
    ScreenMouseEvents.allowMouseClick(screen).register((s,x,y,b)->!click(x,y,b));
   }
  });
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{NotificationPresentation.tickSound(c,Util.getMeasuringTimeMs());if(!visible(c)){QUEUE.frame(Util.getMeasuringTimeMs(),false,0);hits.clear();}});
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{QUEUE.clear();ICONS.clear();hits.clear();PRESENTATION.reset();closeResources();});
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c->closeResources());
  HudRenderCallback.EVENT.register((ctx,t)->{var c=MinecraftClient.getInstance();if(PRESENTATION.owns(ctx,false,surface(c)))render(ctx,c,visible(c),Util.getMeasuringTimeMs());});
 }
 static void render(net.minecraft.client.gui.DrawContext ctx,MinecraftClient c,boolean show,long now){
  hits.clear(); int sw=c.getWindow().getScaledWidth(),sh=c.getWindow().getScaledHeight();float scale=Math.min(sw/1920f,sh/1080f)*.9f;float w=560*scale,h=116*scale,gap=12*scale,bottom=230*scale;int capacity=(int)((sh-bottom-24*scale)/(h+gap));
  var notices=QUEUE.frame(now,show,capacity);if(notices.isEmpty())return;ctx.draw();prepare(c);images.beginFrame();text.beginFrame();
        try(var customization=HudCustomization.begin(ctx,"notifications",sw-24*scale-w,sh-bottom-h-(notices.size()-1)*(h+gap),w,notices.size()*h+(notices.size()-1)*gap)){
  try{for(var v:notices){if(v.announce())NotificationPresentation.playSound(c,v.notice().kind(),now);float enter=Math.min(1,v.age()/260f),alpha=NotificationPresentation.alpha(v.age(),v.notice().duration());float x=sw-24*scale-w+(1-enter)*(w+24*scale),y=sh-bottom-h-(float)v.slot()*(h+gap);
   if(alpha>.1f)hits.add(new Hit(v.notice().id(),x,y,w,h)); int tint=((int)(alpha*255)<<24)|0xFFFFFF;ctx.getMatrices().push();ctx.getMatrices().translate(x,y,400);ctx.getMatrices().scale(scale,scale,1);
   // Crop to authoritative nontransparent bounds; no transparent-margin hit/anchor offset.
   if(c.getResourceManager().getResource(PANEL).isPresent())images.drawTexture(ctx,PANEL,0,0,53,160,560,116,2011,416,2117,743,tint);
   else{ctx.fill(0,0,560,116,((int)(alpha*235)<<24)|0x142737);}
   boolean warning=NotificationPresentation.warningCard(v.notice().kind());
   if(warning){
    int accent=((int)(alpha*235)<<24)|0xD4BC83;ctx.fill(2,18,5,98,accent);
    var iconId=Identifier.of("magiccodex","textures/gui/spell_unavailable.png");
    if(c.getResourceManager().getResource(iconId).isPresent())images.drawTexture(ctx,iconId,31,28,136,151,60,60,979,965,1254,1254,tint);
    else {HudMesh.arc(ctx,61,58,24,2.5f,0,(float)(Math.PI*2),accent);HudMesh.line(ctx,45,74,77,42,3,accent);}
   }
   String icon=switch(v.notice().kind()){case "quest"->"book";case "friend"->"emerald";default->"paper";};var itemIcon=ICONS.get(v.notice().id());
   if(itemIcon!=null){if(alpha>.35f){ctx.getMatrices().push();ctx.getMatrices().translate(61-27,58-27,10);ctx.getMatrices().scale(54/16f,54/16f,1);ctx.drawItem(itemIcon,0,0);ctx.getMatrices().pop();}}
   else if(!warning)images.drawCentered(ctx,"minecraft:textures/item/"+icon+".png",61,58,54,tint);
   String title=v.notice().kind().equals("quest")?"\uc0c8\ub85c\uc6b4 \ud018\uc2a4\ud2b8":v.notice().title();while(title.length()>1&&text.width(title,23,BOLD)>418)title=title.substring(0,title.offsetByCodePoints(title.length(),-1));text.draw(ctx,title,110,35,23,tint,BOLD,false);String body=(v.notice().kind().equals("quest")?v.notice().title():v.notice().body()).replace('\n',' ');while(body.length()>1&&text.width(body,18,FONT)>418)body=body.substring(0,body.offsetByCodePoints(body.length(),-1));text.draw(ctx,body,110,77,18,tint,FONT,false);ctx.getMatrices().pop();}
  }finally{ctx.draw();images.endFrame();}

        }
}
}
