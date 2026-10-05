package school.magiccodex.client;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import school.magiccodex.npctalk.NpcTalkProtocol;

/** Existing dialogue artwork/fonts, explicit direct-input mode and modal scrollable history. */
public final class NpcTalkScreen extends Screen {
 private static final Identifier FONT=Identifier.of("magiccodex","label"),BOLD=Identifier.of("magiccodex","hud_bold");
 private static final Identifier PANEL=asset("dialogue-panel"),NAME=asset("speaker-nameplate"),CHOICE=asset("choice-button");
 private static boolean sound=true;
 private record Line(String text,float[] ends){}
 private record Option(int number,String label,Runnable action){}
 private final Screen parent;private final String session;
 private String speaker,portraitName,text="",stallText="",message="";
 private int hearts,page,shown,historyScroll;private boolean inputEnabled,directInput,log,closed;
 private List<NpcTalkProtocol.Button> buttons=List.of();private List<Line> lines=List.of();
 private long start,lastSound,messageAt,closeAt;private String offerTitle,offerToken;
 private final NpcTalkFlow flow=new NpcTalkFlow();private final ConversationHistory history=new ConversationHistory();
 private char suppressedShortcut;private TextFieldWidget input;private Identifier portrait;private boolean portraitPresent;private int resourceGeneration=-1;
 public NpcTalkScreen(NpcTalkProtocol.Response open,Screen parent){super(Text.literal("NPC 대화"));this.parent=parent;session=open.session();apply(open);}
 private static Identifier asset(String name){return Identifier.of("magiccodex","textures/gui/dialogue/"+name+".png");}
 String session(){return session;}
 private void apply(NpcTalkProtocol.Response open){
  speaker=open.speaker();portraitName=open.portrait();hearts=open.hearts();buttons=open.buttons();inputEnabled=open.input();
  portrait=portraitName!=null&&portraitName.matches("[a-z0-9_/-]{1,120}")?asset(portraitName):null;resourceGeneration=-1;
  history.add("open:0",speaker,open.text());setText(open.text());
 }
 void reopen(NpcTalkProtocol.Response open){apply(open);}
 @Override protected void init(){String draft=input==null?"":input.getText();input=new TextFieldWidget(textRenderer,0,0,800,30,Text.literal("직접 말하기"));input.setMaxLength(NpcTalkProtocol.MAX_INPUT_CHARS*2);input.setText(draft);input.setFocused(directInput&&!log);}
 private void trace(String event,int sequence){NpcUiTrace.event(event,sequence,input!=null&&input.isFocused(),input==null?0:input.getText().codePointCount(0,input.getText().length()),flow.waiting());}
 void receive(NpcTalkProtocol.Response r){
  trace("response-op-"+r.op(),r.seq());
  switch(r.op()){
   case NpcTalkProtocol.S_THINKING->{if(flow.matches(r.seq()))stallText="";}
   case NpcTalkProtocol.S_STALL->{if(flow.matches(r.seq()))stallText=r.text();}
   case NpcTalkProtocol.S_LINE->{if(!flow.line(r.seq()))return;offerTitle=offerToken=null;history.add("npc:"+r.seq(),speaker,r.text());setText(r.text());}
   case NpcTalkProtocol.S_QUEST_OFFER->{if(!flow.current(r.seq()))return;offerTitle=r.text();offerToken=r.token();directInput=false;if(input!=null)input.setFocused(false);}
   case NpcTalkProtocol.S_INFO->{if(r.seq()!=0&&!flow.current(r.seq()))return;flow.rejected(r.seq());stallText="";note(r.text());}
   case NpcTalkProtocol.S_CLOSE->serverClose(r.text());
   default->{}
  }
 }
 void serverClose(String farewell){
  closed=true;directInput=false;offerTitle=offerToken=null;
  if(farewell==null||farewell.isEmpty()){if(client!=null)client.setScreen(parent);return;}
  log=false;history.add("farewell",speaker,farewell);setText(farewell);closeAt=Util.getMeasuringTimeMs()+(long)farewell.length()*23+1600;
 }
 private void setText(String value){text=value==null?"":value;stallText="";layout();page=shown=0;start=Util.getMeasuringTimeMs();}
 private void note(String value){message=value;messageAt=Util.getMeasuringTimeMs();}
 private boolean dispatch(int op,String value,boolean accept){
  if(closed||log||flow.waiting())return false;
  int seq=flow.begin(Util.getMeasuringTimeMs());trace("send-op-"+op,seq);
  if(seq==0)return false;
  if(!NpcTalkClient.send(session,op,seq,value,accept)){flow.rejected(seq);trace("send-failed",seq);note("NPC 대화 서버 연결이 없습니다. 다시 말을 걸어 주세요.");return false;}
  return true;
 }
 private void submit(){
  if(!directInput||!inputEnabled||input==null||closed||log||flow.waiting())return;
  String value=input.getText().strip();trace("submit",flow.pending());
  if(!NpcTalkProtocol.validInput(value)){note(value.isEmpty()?"할 말을 입력해 주세요.":"100자 이내의 문장으로 입력해 주세요.");return;}
  if(dispatch(NpcTalkProtocol.C_SAY,value,false)){history.add("player:"+flow.pending(),"나",value);input.setText("");directInput=false;input.setFocused(false);click();}
 }
 private void pressButton(NpcTalkProtocol.Button button){if(dispatch(NpcTalkProtocol.C_BUTTON,button.id(),false)){history.add("player:"+flow.pending(),"나",button.label());click();}}
 private void answerQuest(boolean accept){if(offerToken!=null&&dispatch(NpcTalkProtocol.C_QUEST,offerToken,accept)){history.add("player:"+flow.pending(),"나",accept?"부탁을 수락했어요.":"부탁을 거절했어요.");offerTitle=offerToken=null;click();}}
 private void openInput(){
  if(!inputEnabled||closed||log||flow.waiting())return;
  shown=count();while(!lastPage()){page++;}shown=count();directInput=true;input.setFocused(true);trace("input-open",0);click();
 }
 private List<Option> options(){
  if(log||directInput||closed||flow.waiting())return List.of();
  if(offerToken!=null)return List.of(new Option(1,"부탁 수락: "+offerTitle,()->answerQuest(true)),new Option(2,"거절",()->answerQuest(false)));
  if(!complete()||!lastPage())return List.of();
  var out=new ArrayList<Option>();int n=1;
  for(var b:buttons)out.add(new Option(n++,b.label(),()->pressButton(b)));
  if(inputEnabled)out.add(new Option(4,"직접 말하기",this::openInput));return out;
 }
 private void layout(){
  var rows=new ArrayList<Line>();StringBuilder current=new StringBuilder();
  for(int cp:text.codePoints().toArray()){String ch=new String(Character.toChars(cp));if(cp=='\n'||UiResources.text().width(current+ch,29,FONT)>1330){rows.add(line(current.toString()));current.setLength(0);if(cp=='\n')continue;}current.append(ch);}
  if(!current.isEmpty()||rows.isEmpty())rows.add(line(current.toString()));lines=List.copyOf(rows);
 }
 private Line line(String s){int[] cps=s.codePoints().toArray();float[] ends=new float[cps.length+1];StringBuilder prefix=new StringBuilder();for(int i=0;i<cps.length;i++){prefix.appendCodePoint(cps[i]);ends[i+1]=UiResources.text().width(prefix.toString(),29,FONT);}return new Line(s,ends);}
 private int count(){return lines.subList(page*3,Math.min(page*3+3,lines.size())).stream().mapToInt(l->l.ends.length-1).sum();}
 private boolean complete(){return shown>=count();}private boolean lastPage(){return (page+1)*3>=lines.size();}
 private CodexLayout fit(){float s=Math.min(width/1600f,height/900f);return new CodexLayout((width-1600*s)/2,(height-900*s)/2,s);}
 @Override public boolean shouldPause(){return false;}
 @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
 @Override public void tick(){suppressedShortcut=0;long now=Util.getMeasuringTimeMs();if(closeAt>0&&now>closeAt){client.setScreen(parent);return;}if(flow.timeout(now)){stallText="";note("응답이 늦습니다. 연결 상태를 확인하고 다시 말을 걸어 주세요.");trace("response-timeout",0);}if(client!=null&&(client.player==null||client.world==null))close();}
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
  long now=Util.getMeasuringTimeMs();int next=(int)Math.min(count(),Math.max(shown,(now-start)/23));
  if(!log&&next>shown){if(sound&&now-lastSound>=45){client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1.8f,.07f));lastSound=now;}shown=next;}
  if(resourceGeneration!=UiResources.generation()){resourceGeneration=UiResources.generation();portraitPresent=portrait!=null&&client.getResourceManager().getResource(portrait).isPresent();}
  var f=fit();double mx=f.localX(mouseX),my=f.localY(mouseY);var images=UiResources.images();images.beginFrame();UiResources.text().beginFrame();c.fill(0,0,width,height,0x30030A12);c.getMatrices().push();
  try{
   c.getMatrices().translate(f.x(),f.y(),0);c.getMatrices().scale(f.scale(),f.scale(),1);
   if(portraitPresent)images.drawTexture(c,portrait,65,15,0,0,700,1050,1024,1536,1024,1536);
   // The lower nameplate is the only NPC name. Vector hearts avoid font/placeholder glyphs.
   for(int i=0;i<NpcTalkProtocol.MAX_HEARTS;i++)heart(c,812+i*31,97,i<hearts?0xFFE8A0BF:0x707C8999);
   label(c,sound?"♪ 소리 켜짐":"♪ 소리 꺼짐",1110,54,21,0xFFD8DFE7,false,false,175);label(c,"기록",1305,54,21,0xFFD8DFE7,false,false,90);label(c,log?"기록 닫기 ×":"닫기 ×",1430,54,21,0xFFD8DFE7,false,false,130);
   if(log){drawHistory(c);return;}
   if(directInput&&inputEnabled){
    c.fill(914,457,1513,609,0xD0101D2C);label(c,"직접 말하기",944,481,23,0xFFE8D19B,true,false,450);
    images.drawTexture(c,CHOICE,935,505,73,270,443,60,1952,210,2098,749,input.isFocused()?0xFFFFFFFF:0xFFB8C4D0);
    drawInput(c,now);images.drawTexture(c,CHOICE,1390,505,73,270,109,60,1952,210,2098,749,hit(mx,my,1390,505,109,60)?0xFFFFFFFF:0xFFCBD5DD);label(c,"전송",1444,535,22,0xFFF0F3F5,false,true,90);label(c,"Enter 전송 · Esc 입력 닫기",944,589,19,0xFFB8C4D0,false,false,520);
   }
   images.drawTexture(c,PANEL,60,639,44,199,1480,220,2085,316,2172,724);images.drawTexture(c,NAME,86,615,200,244,255,54,1810,235,2172,724);label(c,speaker,131,642,23,0xFFE8D19B,true,false,193);
   if(flow.waiting()&&!closed)label(c,stallText.isEmpty()?".".repeat((int)(now/350%3)+1):stallText,120,704,29,0xFFB8C4D0,false,false,1330);
   else{int remaining=shown;for(int i=page*3;i<Math.min(page*3+3,lines.size());i++){var row=lines.get(i);int n=Math.min(remaining,row.ends.length-1);remaining-=n;if(n>0){int y=704+(i-page*3)*43;boolean partial=n<row.ends.length-1;if(partial)c.enableScissor(115,y-24,120+(int)Math.ceil(row.ends[n])+1,y+26);label(c,row.text,120,y,29,0xFFF0F1F4,false,false,1330);if(partial)c.disableScissor();}}}
   var opts=options();for(int i=0;i<opts.size();i++){int y=590-opts.size()*75+i*75;boolean hovered=hit(mx,my,962,y,545,67);c.fill(952,y-2,1517,y+69,0xD5101D2C);images.drawTexture(c,CHOICE,962,y,73,270,545,67,1952,210,2098,749,hovered?0xFFFFFFFF:0xFFCBD5DD);label(c,opts.get(i).number()+". "+opts.get(i).label(),1005,y+33,24,0xFFEEF1F5,false,false,465);}
   String hint=closed||directInput?"":flow.waiting()?"처리 중…":!complete()?"Enter  바로 보기":!lastPage()?"Enter  다음":"4  직접 말하기 · Esc  닫기";
   label(c,hint,800,825,20,0xFFC1CEDD,false,true,520);if(!message.isEmpty()&&now-messageAt<7000)label(c,message,800,874,19,0xFFE8D19B,false,true,1300);
  }finally{c.getMatrices().pop();images.endFrame();}
 }
 private void drawInput(DrawContext c,long now){
  String value=input.getText();if(value.isEmpty()){label(c,"할 말을 입력하세요",957,535,23,0xFF9FB0C0,false,false,395);if(input.isFocused()&&now/500%2==0)c.fill(957,521,959,549,0xFF91E8F5);return;}
  // Track the actual edit cursor so clipboard edits and arrows remain visible.
  int cursor=input.getCursor(),from=0;while(from<cursor&&UiResources.text().width(value.substring(from,cursor),23,FONT)>382)from++;
  int to=value.length();while(to>from&&UiResources.text().width(value.substring(from,to),23,FONT)>390)to--;
  label(c,value.substring(from,to),957,535,23,0xFFF0F1F4,false,false,395);
  if(input.isFocused()&&now/500%2==0){int x=957+(int)UiResources.text().width(value.substring(from,cursor),23,FONT);c.fill(x,521,x+2,549,0xFF91E8F5);}
 }
 private void drawHistory(DrawContext c){
  c.fill(240,130,1510,855,0xF30D192A);label(c,"대화 기록",274,168,27,0xFFE8D19B,true,false,1100);
  var rows=new ArrayList<String>();for(var e:history.entries()){rows.add(e.speaker());rows.addAll(UiResources.text().wrapText(e.text(),1150,25));rows.add("");}
  int visible=17,max=Math.max(0,rows.size()-visible);historyScroll=Math.clamp(historyScroll,0,max);
  c.enableScissor(270,198,1470,803);try{for(int i=historyScroll;i<Math.min(rows.size(),historyScroll+visible);i++)label(c,rows.get(i),274,218+(i-historyScroll)*34,25,0xFFE5E8EC,false,false,1150);}finally{c.disableScissor();}
  if(max>0){c.fill(1481,201,1484,795,0x4088AABB);int thumb=Math.max(24,594*visible/rows.size()),y=201+(594-thumb)*historyScroll/max;c.fill(1481,y,1484,y+thumb,0xFF9EC4CE);}
  label(c,"휠로 스크롤 · Esc 또는 기록 닫기",274,825,19,0xFFB2C2D0,false,false,1100);
 }
 private void heart(DrawContext c,float x,float y,int color){
  float[] xs={0,5,10,15,20,20,10,0},ys={5,0,4,0,5,10,21,10};var m=c.getMatrices().peek().getPositionMatrix();
  c.draw(p->{var v=p.getBuffer(net.minecraft.client.render.RenderLayer.getGui());for(int i=0;i<xs.length;i++){int j=(i+1)%xs.length;v.vertex(m,x+10,y+9,0).color(color);v.vertex(m,x+xs[j],y+ys[j],0).color(color);v.vertex(m,x+xs[i],y+ys[i],0).color(color);v.vertex(m,x+10,y+9,0).color(color);}});
 }
 private void label(DrawContext c,String value,float x,float y,float size,int color,boolean bold,boolean centered,float max){var font=bold?BOLD:FONT;float actual=Math.min(size,size*max/Math.max(max,UiResources.text().width(value,size,font)));UiResources.text().draw(c,value,x,y,actual,color,font,centered);}
 private static boolean hit(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
 private void click(){if(client!=null)client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1.2f,.12f));}
 private void advanceText(){if(closed)return;if(!complete()){shown=count();return;}if(!lastPage()){page++;start=Util.getMeasuringTimeMs();shown=0;}}
 @Override public boolean keyPressed(int key,int scan,int mods){
  if(log){if(key==GLFW.GLFW_KEY_ESCAPE){log=false;input.setFocused(directInput);}return true;}
  if(key==GLFW.GLFW_KEY_ESCAPE){if(directInput){directInput=false;input.setFocused(false);}else close();return true;}
  if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(directInput)submit();else advanceText();return true;}
  if(directInput){return input!=null&&input.isFocused()&&input.keyPressed(key,scan,mods)||super.keyPressed(key,scan,mods);}
  if(key>=GLFW.GLFW_KEY_1&&key<=GLFW.GLFW_KEY_4){int n=key-GLFW.GLFW_KEY_1+1;for(var o:options())if(o.number()==n){suppressedShortcut=(char)(48+n);o.action().run();return true;}}
  return super.keyPressed(key,scan,mods);
 }
 @Override public boolean charTyped(char ch,int mods){char suppressed=suppressedShortcut;suppressedShortcut=0;if(ch==suppressed)return true;if(log||closed||!directInput||!inputEnabled||input==null||!input.isFocused())return false;boolean typed=input.charTyped(ch,mods);trace("typed",0);return typed;}
 @Override public boolean mouseClicked(double x,double y,int button){
  if(button!=0)return false;var f=fit();double mx=f.localX(x),my=f.localY(y);
  if(log){if(hit(mx,my,1280,24,280,60)){log=false;input.setFocused(directInput);}return true;}
  if(hit(mx,my,1090,24,180,60)){sound=!sound;click();return true;}
  if(hit(mx,my,1280,24,130,60)){log=true;historyScroll=Integer.MAX_VALUE;if(input!=null)input.setFocused(false);trace("history-open",0);return true;}
  if(hit(mx,my,1420,24,140,60)){close();return true;}
  if(directInput){if(hit(mx,my,1390,505,109,60)){submit();return true;}input.setFocused(hit(mx,my,935,505,443,60));trace("focus",0);return true;}
  var opts=options();for(int i=0;i<opts.size();i++)if(hit(mx,my,962,590-opts.size()*75+i*75,545,67)){opts.get(i).action().run();return true;}
  if(hit(mx,my,60,639,1480,220))advanceText();return true;
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v){if(log){historyScroll=Math.max(0,historyScroll-(int)Math.signum(v)*3);return true;}return super.mouseScrolled(x,y,h,v);}
 @Override public void close(){if(!closed){closed=true;NpcTalkClient.send(session,NpcTalkProtocol.C_CLOSE,0,"",false);}if(client!=null)client.setScreen(parent);}
}