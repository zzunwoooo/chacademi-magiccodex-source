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
  var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.player==null||mc.screen!=null)return;
  if(BroomFlight.catchTicks()>0){drawCatch(g,mc);return;}var ph=BroomFlight.physics();if(ph==null)return;
  float pt=dt.getGameTimeDeltaPartialTick(false),balance=Mth.clamp(Mth.lerp(pt,ph.prevBalance,ph.balance)/100f,0,1);var a=anchor(mc,g,pt);
  int w=140,h=32,x=a[0]-w/2,y=a[1]-h/2;
  atlas(g,x,y,w,h,26,33,560,126);
  g.enableScissor(x,y,x+Math.round(w*balance),y+h);try{atlas(g,x,y,w,h,balance<.3f?1190:608,33,560,126);}finally{g.disableScissor();}
  text(g,mc,"균형 "+Math.round(balance*100)+"%",a[0],y+h+1,balance<.3?0xFFFFB569:0xFFE3F7FF);
  atlas(g,x-27,y+2,25,25,70,181,230,239);
  if(ph.boostCooldown>0)text(g,mc,Integer.toString((ph.boostCooldown+19)/20),x-14,y+11,0xFFFFFFFF);
  int left=BroomFlight.limitLeft();if(left>0)text(g,mc,"연습용 "+((left+19)/20)+"초",a[0],y+h+13,0xFFFFDBAD);
 }
 private static void drawCatch(GuiGraphics g,Minecraft mc){
  var sequence=BroomFlight.catchSequence();if(sequence==null)return;int[] keys=sequence.keys();
  var bindings=new net.minecraft.client.KeyMapping[]{mc.options.keyUp,mc.options.keyLeft,mc.options.keyDown,mc.options.keyRight};
  int size=Math.min(38,Math.max(24,(g.guiWidth()-28)/6-4)),left=(g.guiWidth()-6*(size+4))/2,y=Math.round(g.guiHeight()*.56f);
  text(g,mc,"순서대로 빠르게 입력해 빗자루 잡기",g.guiWidth()/2,y-18,0xFFFFE1A5);
  for(int i=0;i<6;i++){int x=left+i*(size+4);boolean done=i<sequence.progress();atlas(g,x,y,size,size,done?1465:1181,428,281,277);if(!done)g.drawCenteredString(mc.font,bindings[keys[i]].getTranslatedKeyMessage(),x+size/2,y+size/2-4,i==sequence.progress()?0xFFFFEC96:0xFFCFDDE8);}
  int total=6*(size+4)-4;atlas(g,left,y+size+5,total,9,40,709,960,65);g.enableScissor(left,y+size+5,left+Math.round(total*BroomFlight.catchTicks()/(float)BroomFlight.CATCH_TICKS),y+size+14);try{atlas(g,left,y+size+5,total,9,40,778,960,65);}finally{g.disableScissor();}
 }
}
