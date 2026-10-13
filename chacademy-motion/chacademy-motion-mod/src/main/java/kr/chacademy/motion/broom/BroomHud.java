package kr.chacademy.motion.broom;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
/** One shallow arch in both perspectives, projected above the rider in third person. */
public final class BroomHud {
 private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("chacademy_motion","textures/gui/flight_atlas.png");
 private static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("magiccodex","label");
 private BroomHud(){}
 private static void part(GuiGraphics g,String name,int x,int y,int w,int h,int sw,int sh){g.blit(RenderType::guiTextured,ResourceLocation.fromNamespaceAndPath("chacademy_motion","textures/gui/followup/"+name+".png"),x,y,0f,0f,w,h,sw,sh,sw,sh);}
 private static void atlas(GuiGraphics g,int x,int y,int w,int h,int u,int v,int sw,int sh){g.blit(RenderType::guiTextured,ATLAS,x,y,(float)u,(float)v,w,h,sw,sh,1774,887);}
 private static void text(GuiGraphics g,Minecraft mc,String value,int x,int y,int color){g.drawCenteredString(mc.font,Component.literal(value).withStyle(s->s.withFont(FONT)),x,y,color);}
 private static int[] anchor(Minecraft mc,GuiGraphics g,float pt){
  int x=g.guiWidth()/2,y=Math.round(g.guiHeight()*.39f);
  if(!mc.options.getCameraType().isFirstPerson()){
   var camera=mc.gameRenderer.getMainCamera();var head=mc.player.getPosition(pt).add(0,2.5,0).subtract(camera.getPosition());
   var point=new Vector3f((float)head.x,(float)head.y,(float)head.z).rotate(new Quaternionf(camera.rotation()).conjugate());
   if(point.z<-.05f){float focal=g.guiHeight()/(2f*(float)Math.tan(Math.toRadians(mc.options.fov().get())/2));x=Math.round(g.guiWidth()/2f-point.x*focal/point.z);y=Math.round(g.guiHeight()/2f+point.y*focal/point.z);}
  }
  return new int[]{Mth.clamp(x,90,Math.max(90,g.guiWidth()-90)),Mth.clamp(y,24,Math.max(24,g.guiHeight()-70))};
 }
 public static void render(GuiGraphics g,DeltaTracker dt){
  var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.player==null||(mc.screen!=null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) && !mc.screen.getClass().getName().equals("school.magiccodex.client.HudCursorScreen")))return;
  if(BroomFlight.catchTicks()>0){drawCatch(g,mc);return;}var ph=BroomFlight.physics();if(ph==null)return;
  float pt=dt.getGameTimeDeltaPartialTick(false),balance=Mth.clamp(Mth.lerp(pt,ph.prevBalance,ph.balance)/100f,0,1);var a=anchor(mc,g,pt);
  int w=140,h=32,x=a[0]-w/2,y=a[1]-h/2;
  part(g,"flight_arch",x,y,w,h,906,185);
  g.enableScissor(x,y,x+Math.round(w*balance),y+h);try{part(g,"flight_arch_fill",x,y,w,h,918,222);}finally{g.disableScissor();}
  int bx=x-26,by=y+1;part(g,"flight_ring",bx,by,25,25,333,337);
  float ready=1-Mth.clamp(ph.boostCooldown/(float)Math.max(1,ph.stats.boostCooldown()),0,1);
  g.enableScissor(bx,by+Math.round(25*(1-ready)),bx+25,by+25);try{part(g,"flight_ring_fill",bx+3,by+3,19,19,264,250);}finally{g.disableScissor();}
  part(g,"flight_feather",bx+6,by+5,13,15,243,276);
  if(ph.boostCooldown>0)text(g,mc,Integer.toString((ph.boostCooldown+19)/20),x-14,y+11,0xFFFFFFFF);
  int left=BroomFlight.limitLeft();if(left>0)text(g,mc,"연습용 "+((left+19)/20)+"초",a[0],y+h+13,0xFFFFDBAD);
 }
 private static void drawCatch(GuiGraphics g,Minecraft mc){
  var sequence=BroomFlight.catchSequence();if(sequence==null)return;int[] keys=sequence.keys();
  var bindings=new net.minecraft.client.KeyMapping[]{mc.options.keyUp,mc.options.keyLeft,mc.options.keyDown,mc.options.keyRight};
  int size=Math.min(38,Math.max(24,(g.guiWidth()-28)/keys.length-4)),left=(g.guiWidth()-keys.length*(size+4))/2,y=Math.round(g.guiHeight()*.56f);
  text(g,mc,"순서대로 빠르게 입력해 빗자루 잡기",g.guiWidth()/2,y-18,0xFFFFE1A5);
  for(int i=0;i<keys.length;i++){int x=left+i*(size+4);boolean done=i<sequence.progress();part(g,done?"catch_key_done":i==sequence.progress()?"catch_key_current":"catch_key",x,y,size,size,done?383:i==sequence.progress()?376:302,done?350:i==sequence.progress()?351:302);if(!done)g.drawCenteredString(mc.font,bindings[keys[i]].getTranslatedKeyMessage().copy().withStyle(style->style.withFont(ResourceLocation.fromNamespaceAndPath("magiccodex","hud_bold")).withBold(true)),x+size/2,y+size/2-4,i==sequence.progress()?0xFFFFEC96:0xFFCFDDE8);}
  int total=keys.length*(size+4)-4;atlas(g,left,y+size+5,total,9,40,709,960,65);g.enableScissor(left,y+size+5,left+Math.round(total*BroomFlight.catchTicks()/(float)BroomFlight.CATCH_TICKS),y+size+14);try{atlas(g,left,y+size+5,total,9,40,778,960,65);}finally{g.disableScissor();}
 }
}
