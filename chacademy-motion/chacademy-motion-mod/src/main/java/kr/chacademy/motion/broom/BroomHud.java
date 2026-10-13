package kr.chacademy.motion.broom;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
/**
 * Flight HUD, fixed just above the MagicCodex skill bar (bottom right) so it never covers the view or gets cut off.
 * Boost readiness is shown only by the ring gauge (no countdown number); balance by the arc.
 * Layout is in the skill bar's reference units (1672x941 screen), scaled the same way as that bar.
 */
public final class BroomHud {
 private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("chacademy_motion","textures/gui/flight_atlas.png");
 /** A real font resource (assets/magiccodex/font/label.json). "magiccodex:hud_bold" only exists inside MagicCodex's own text renderer, so vanilla drew nothing with it. */
 private static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("magiccodex","label");
 /** Skill bar geometry (MagicCodex SkillHudRenderer): left = w-402s, top = h-76s, mode-key row 33 above top. */
 static final float SKILL_LEFT=402,SKILL_TOP=76,SKILL_ROW=33,SKILL_WIDTH=382;
 static final float PANEL_W=340,PANEL_H=76,GAP=12;
 private BroomHud(){}
 private static void part(GuiGraphics g,String name,int x,int y,int w,int h,int sw,int sh){g.blit(RenderType::guiTextured,ResourceLocation.fromNamespaceAndPath("chacademy_motion","textures/gui/followup/"+name+".png"),x,y,0f,0f,w,h,sw,sh,sw,sh);}
 private static void atlas(GuiGraphics g,int x,int y,int w,int h,int u,int v,int sw,int sh){g.blit(RenderType::guiTextured,ATLAS,x,y,(float)u,(float)v,w,h,sw,sh,1774,887);}
 private static Component styled(String value){return Component.literal(value).withStyle(s->s.withFont(FONT));}
 /** Centred text at a scale (vanilla font is 9 px tall), with a 1 px dark shadow so it reads on any background. */
 private static void text(GuiGraphics g,Minecraft mc,Component value,float cx,float cy,float scale,int color){
  var pose=g.pose();pose.pushPose();
  try{pose.translate(cx,cy,0);pose.scale(scale,scale,1);g.drawCenteredString(mc.font,value,0,-4,color);}finally{pose.popPose();}
 }
 private static void frame(GuiGraphics g,int x,int y,int w,int h,int fill,int edge){
  g.fill(x+2,y,x+w-2,y+h,fill);g.fill(x,y+2,x+2,y+h-2,fill);g.fill(x+w-2,y+2,x+w,y+h-2,fill);
  g.fill(x+3,y,x+w-3,y+1,edge);g.fill(x+3,y+h-1,x+w-3,y+h,edge);g.fill(x,y+3,x+1,y+h-3,edge);g.fill(x+w-1,y+3,x+w,y+h-3,edge);
 }
 /** Reference units -> GUI pixels for the skill-bar placement. */
 static float scale(int width,int height){return Math.min(width/1672f,height/941f);}
 public static void render(GuiGraphics g,DeltaTracker dt){
  var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.player==null||(mc.screen!=null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) && !mc.screen.getClass().getName().equals("school.magiccodex.client.HudCursorScreen")))return;
  if(BroomFlight.catchTicks()>0){drawCatch(g,mc);return;}var ph=BroomFlight.physics();if(ph==null)return;
  float pt=dt.getGameTimeDeltaPartialTick(false),balance=Mth.clamp(Mth.lerp(pt,ph.prevBalance,ph.balance)/100f,0,1);
  int w=g.guiWidth(),h=g.guiHeight();float s=scale(w,h);
  // Centred over the skill bar, its bottom GAP units above the mode-key row.
  float cx=w-SKILL_LEFT*s+SKILL_WIDTH/2*s,bottom=h-(SKILL_TOP+SKILL_ROW+GAP)*s;
  var pose=g.pose();pose.pushPose();
  try{
   pose.translate(cx-PANEL_W/2*s,bottom-PANEL_H*s,0);pose.scale(s,s,1);
   frame(g,0,0,(int)PANEL_W,(int)PANEL_H,0xB80B1622,0x99B89458);
   // Boost ring: the cyan disc refills from the bottom while recharging (gauge only, no countdown).
   int rx=14,ry=10,rs=56;float ready=1-Mth.clamp(ph.boostCooldown/(float)Math.max(1,ph.stats.boostCooldown()),0,1);
   g.enableScissor(rx+6,ry+6+Math.round(44*(1-ready)),rx+50,ry+50);try{part(g,"flight_ring_fill",rx+6,ry+6,44,44,264,250);}finally{g.disableScissor();}
   part(g,"flight_ring",rx,ry,rs,rs,333,337);
   part(g,"flight_feather",rx+16,ry+12,24,28,243,276);
   // Balance arc: gold track, cyan fill from the left.
   int ax=84,ay=10,aw=240,ah=49;
   part(g,"flight_arch",ax,ay,aw,ah,906,185);
   g.enableScissor(ax,ay-4,ax+Math.round(aw*balance),ay+ah+8);try{part(g,"flight_arch_fill",ax,ay-3,aw,59,918,222);}finally{g.disableScissor();}
   text(g,mc,styled("균형"),ax+aw/2f,PANEL_H-13,1.1f,0xFFB9C9D6);
   int left=BroomFlight.limitLeft();
   if(left>0)text(g,mc,styled("연습용 "+((left+19)/20)+"초"),PANEL_W/2,-12,1.4f,0xFFFFDBAD);
  }finally{pose.popPose();}
 }
 /** Fall: a framed card in the middle of the screen with big key caps (current = gold, done = cyan) and a time bar. */
 private static void drawCatch(GuiGraphics g,Minecraft mc){
  var sequence=BroomFlight.catchSequence();if(sequence==null)return;int[] keys=sequence.keys();
  var bindings=new net.minecraft.client.KeyMapping[]{mc.options.keyUp,mc.options.keyLeft,mc.options.keyDown,mc.options.keyRight};
  int w=g.guiWidth(),h=g.guiHeight();float s=Math.max(.5f,Math.min(1.2f,Math.min(w/1100f,h/650f)));
  int size=64,gap=10,total=keys.length*size+(keys.length-1)*gap,cardW=total+80,cardH=size+136;
  var pose=g.pose();pose.pushPose();
  try{
   pose.translate(w/2f,h*.58f,0);pose.scale(s,s,1);
   int x0=-total/2,top=-cardH/2;
   frame(g,-cardW/2,top,cardW,cardH,0xD00B1622,0xCCB89458);
   text(g,mc,styled("균형을 잃었습니다!"),0,top+24,1.9f,0xFFFFE1A5);
   text(g,mc,styled("표시된 키를 순서대로 빠르게 누르세요"),0,top+48,1.15f,0xFFE6EEF5);
   int ky=top+66;
   for(int i=0;i<keys.length;i++){
    int x=x0+i*(size+gap);boolean done=i<sequence.progress(),current=i==sequence.progress();
    part(g,done?"catch_key_done":current?"catch_key_current":"catch_key",x,ky,size,size,done?383:current?376:302,done?350:current?351:302);
    var label=bindings[keys[i]].getTranslatedKeyMessage().copy().withStyle(st->st.withFont(FONT));
    text(g,mc,label,x+size/2f,ky+size/2f,2.6f,done?0xFF7FE6F2:current?0xFFFFEC96:0xFFE6EEF5);
   }
   int barY=ky+size+12;
   atlas(g,x0,barY,total,9,40,709,960,65);
   g.enableScissor(x0,barY,x0+Math.round(total*BroomFlight.catchTicks()/(float)BroomFlight.CATCH_TICKS),barY+9);try{atlas(g,x0,barY,total,9,40,778,960,65);}finally{g.disableScissor();}
  }finally{pose.popPose();}
 }
}
