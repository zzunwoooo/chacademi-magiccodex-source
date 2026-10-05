package school.magiccodex.client;

import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import school.magiccodex.protocol.CoreAffinity;
import school.magiccodex.protocol.ReconfigurationProtocol;
import school.magiccodex.protocol.ReconfigurationProtocol.Response;

/** Fixed logical grid: labels left, values right; all icon/text rows share a vertical centre. */
public final class ReconfigurationScreen extends Screen {
    private static final Identifier FONT=Identifier.of("magiccodex","hud_bold");
    private static final String[] TABS={"강화 초기화","실패 횟수 복구","코어 성향 변경"};
    private static final String[] SCROLLS={"scroll-reset","scroll-restore","scroll-affinity"};
    private static final String[] NAMES={"강화 초기화권","강화 횟수 복구권","능력치 변경권"};
    private static final String[] STATS={"마력","마나 최대치","마법 가속"};
    private static final String[] ICONS={"icon-power","icon-mana","icon-haste"};
    private static final int GOLD=0xFFE7CE9C,INK=0xFFDEE8EE,MUTED=0xFFACBBC8,CYAN=0xFF9CE5EF;
    private Response data;
    private long waiting=-1,completed=-1;
    private float scale,left,top;
    public ReconfigurationScreen(Response r){super(Text.literal("마력 재구성"));data=r;}
    Response snapshot(){return data;}
    private static Identifier asset(String n){String folder=n.equals("panel")||n.startsWith("scroll-")?"reconfiguration":"enhancement";return Identifier.of("magiccodex","textures/gui/"+folder+"/"+n+".png");}
    @Override protected void init(){for(String n:new String[]{"panel","core-intact","mana-glow","rune-cyan","scroll-reset","scroll-restore","scroll-affinity","icon-power","icon-mana","icon-haste"})UiResources.images().prepareRegion(asset(n),0,0,n.equals("panel")?1374:1254,n.equals("panel")?1145:1254);}
    public void receive(Response r){if(r.state()==1&&data.state()!=1){completed=Util.getMeasuringTimeMs();sound("block.amethyst_block.chime",1.2f,.5f);sound("block.beacon.activate",1.4f,.3f);}else if(r.state()==2)sound("block.note_block.bass",.7f,.3f);if(r.token()!=data.token())completed=-1;data=r;waiting=-1;}
    private void sound(String id,float pitch,float volume){if(client!=null)client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.of("minecraft",id)),pitch,volume));}
    private void layout(){scale=Math.min(width/680f,height/585f);left=(width-640*scale)/2;top=(height-550*scale)/2;}
    private void text(DrawContext c,String s,float x,float y,float size,int color,boolean center){UiResources.text().draw(c,s,x,y,size,color,FONT,center);}
    private void fit(DrawContext c,String s,float x,float y,float size,float max,int color,boolean right){float w=UiResources.text().width(s,size,FONT);if(w>max)size*=max/w;w=UiResources.text().width(s,size,FONT);text(c,s,right?x-w:x,y,size,color,false);}
    private void sprite(DrawContext c,String name,float x,float y,float w,float h,int tint){int tw=name.equals("panel")?1374:1254,th=name.equals("panel")?1145:1254;c.getMatrices().push();try{c.getMatrices().translate(x,y,0);c.getMatrices().scale(w/100,h/100,1);UiResources.images().drawTexture(c,asset(name),0,0,0,0,100,100,tw,th,tw,th,tint);c.draw();}finally{c.getMatrices().pop();}}
    private void divider(DrawContext c,float x,float y,float w){HudMesh.line(c,x,y,x+w/2-7,y,.9f,0x998E998D);HudMesh.line(c,x+w/2+7,y,x+w,y,.9f,0x998E998D);HudMesh.star(c,x+w/2,y,2.8f,GOLD);}
    private void box(DrawContext c,int x,int y,int w,int h,int border){c.fill(x,y,x+w,y+h,0x18071320);HudMesh.line(c,x,y,x+w,y,.6f,border);HudMesh.line(c,x,y+h,x+w,y+h,.6f,border);HudMesh.line(c,x,y,x,y+h,.6f,border);HudMesh.line(c,x+w,y,x+w,y+h,.6f,border);}
    private String num(double v){return String.format(Locale.ROOT,v==Math.rint(v)?"%.0f":"%.1f",v);}
    private boolean hit(double x,double y,double a,double b,double w,double h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        layout();double mouseX=(mx-left)/scale,mouseY=(my-top)/scale,t=Util.getMeasuringTimeMs()/1000d;
        var images=UiResources.images();images.beginFrame();UiResources.text().beginFrame();c.fill(0,0,width,height,0x65040A13);c.getMatrices().push();
        try{c.getMatrices().translate(left,top,70);c.getMatrices().scale(scale,scale,1);sprite(c,"panel",0,0,640,550,0xD9FFFFFF);
            float pulse=(float)(.5+.5*Math.sin(t*1.7));
            // Slow translucent nebula layers and drifting points; continuous render time, no tick-stepped animation.
            sprite(c,"mana-glow",170+(float)Math.sin(t*.12)*25,94,320,285,0x195F83BD);
            for(int i=0;i<24;i++){float x=42+(float)((i*.618033+t*.004)%1)*552,y=112+(float)((i*.371+t*.002)%1)*210;HudMesh.disk(c,x,y,.6f,((int)(20+25*(.5+.5*Math.sin(t+i)))<<24)|0xACCFEB);}
            UiResources.text().draw(c,"마력 재구성",320,41,27,GOLD,Identifier.of("magiccodex","ui_extra_bold"),true);text(c,"×",600,34,21,GOLD,true);
            for(int i=0;i<3;i++){int x=40+i*189;boolean hovered=hit(mouseX,mouseY,x,64,182,32);box(c,x,64,182,32,i==data.mode()?0xCF91DEEB:hovered?0xBBBDC4AC:0x777F8C8C);c.fill(x+1,65,x+181,95,0xFF112233);if(i==data.mode())c.fill(x+1,65,x+181,95,0x25387C99);text(c,TABS[i],x+91,80,13,i==data.mode()?CYAN:INK,true);}
            box(c,65,112,202,195,0x809FA88F);box(c,373,112,202,195,0x809FA88F);
            text(c,data.mode()==2?"대상 마력코어":"대상 마법봉",166,127,13,GOLD,true);text(c,"사용 주문서",474,127,13,GOLD,true);
            HudMesh.line(c,80,142,252,142,.5f,0x778B998F);HudMesh.line(c,388,142,560,142,.5f,0x778B998F);
            sprite(c,"mana-glow",96-pulse*8,145-pulse*8,140+pulse*16,140+pulse*16,((int)(80+55*pulse)<<24)|0xB2CBFF);
            for(int j=0;j<3;j++)HudMesh.arc(c,166,210,44+j*3+pulse*3,1.2f,0,(float)(Math.PI*2),((int)((22+20*pulse)*(1-j*.2))<<24)|0xB9B6FF);
            if(data.slot()>=0){
                if(data.mode()==2)sprite(c,"core-intact",95,140,142,142,0xFFFFFFFF);
                else if(client!=null&&client.player!=null){var item=client.player.getInventory().getStack(data.slot());c.draw();c.getMatrices().push();try{c.getMatrices().translate(166,211+(float)Math.sin(t*1.5)*2,10);c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)Math.sin(t*.6)*7));c.getMatrices().scale(4.5f,4.5f,1);c.drawItem(item,-8,-8);c.draw();}finally{c.getMatrices().pop();}}
                else text(c,"장비 미리보기",166,211,13,MUTED,true);
            }else text(c,"—",166,211,24,MUTED,true);
            String display=data.name();while(display.length()>1&&UiResources.text().width(display,13,FONT)>166)display=display.substring(0,display.length()-1);if(!display.equals(data.name())){while(display.length()>1&&UiResources.text().width(display+"…",13,FONT)>166)display=display.substring(0,display.length()-1);display+="…";}text(c,display,166,272,13,GOLD,true);
            text(c,data.mode()==2?data.nodes()+"코어":"남은 강화 "+Math.max(0,data.limit()-(data.state()==1?data.nextAttempts():data.attempts()))+" / "+data.limit(),166,292,11,INK,true);
            c.getMatrices().push();c.getMatrices().translate(320,210,0);c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)(t*9%360)));sprite(c,"rune-cyan",-33,-33,66,66,0xB0FFFFFF);c.getMatrices().pop();
            sprite(c,SCROLLS[data.mode()],400,136,148,136,0xFFFFFFFF);text(c,NAMES[data.mode()],474,273,13,GOLD,true);text(c,"사용 가능 "+data.credits()+"회",474,292,11,MUTED,true);
            divider(c,45,322,550);
            text(c,data.mode()==2?"현재 성향":"현재 능력치",166,341,13,GOLD,true);text(c,data.state()==1?"변경 완료":"변경 후",474,341,13,GOLD,true);
            for(int i=0;i<3;i++){int y=367+i*24;sprite(c,ICONS[i],76,y-11,22,22,0xFFFFFFFF);fit(c,data.mode()==2?CoreAffinity.label(i,data.affinity()):STATS[i],104,y,12,113,INK,false);if(data.mode()!=2)fit(c,num(data.before().get(i)),256,y,13,55,CYAN,true);
                if(data.mode()!=2){sprite(c,ICONS[i],384,y-11,22,22,0xFFFFFFFF);fit(c,STATS[i],412,y,12,99,INK,false);fit(c,num(data.after().get(i)),565,y,13,51,CYAN,true);}
                else if(data.nextAffinity()>=0){sprite(c,ICONS[i],384,y-11,22,22,0xFFFFFFFF);fit(c,CoreAffinity.label(i,data.nextAffinity()),412,y,12,150,CYAN,false);}
            }
            if(data.mode()==2&&data.nextAffinity()<0){text(c,"?",474,381,40,CYAN,true);text(c,"새로운 성향",474,416,11,MUTED,true);}
            else if(data.mode()!=2)text(c,"남은 강화 "+Math.max(0,data.limit()-data.attempts())+" → "+Math.max(0,data.limit()-data.nextAttempts()),320,440,12,CYAN,true);
            String note=data.mode()==0?"강화 횟수와 강화로 오른 능력치를 초기화합니다":data.mode()==1?"실패 횟수 "+Math.max(0,data.attempts()-data.nextAttempts())+"회 복구 · 성공한 강화 효과는 유지됩니다":"코어 개수와 상승량은 유지됩니다";
            fit(c,note,65,456,11,510,MUTED,false);divider(c,45,469,550);
            boolean enabled=data.available()&&waiting<0;box(c,215,481,210,32,enabled?0xFFE2C68B:0x997F8F96);c.fill(216,482,424,512,0xFF112233);if(enabled&&hit(mouseX,mouseY,215,481,210,32))c.fill(216,482,424,512,0x443A839C);
            text(c,waiting>=0?"요청 중…":data.state()==1?"확인":new String[]{"초기화","복구","성향 변경"}[data.mode()],320,497,15,enabled?GOLD:INK,true);
            if(!data.message().isBlank())fit(c,data.message(),45,519,10,550,data.state()==1?CYAN:0xFFE5B7AF,false);
            if(completed>=0){float u=(Util.getMeasuringTimeMs()-completed)/900f;if(u<1){int color=((int)(200*(1-u)*(1-u))<<24)|0xB5EFFF;HudMesh.arc(c,166,210,44+100*u,2*(1-u),0,(float)(Math.PI*2),color);HudMesh.star(c,166,210,45*(1-u),color);}}
            c.draw();
        }finally{c.getMatrices().pop();images.endFrame();}
        if(hit(mouseX,mouseY,80,258,174,27)&&UiResources.text().width(data.name(),13,FONT)>166){var lines=new java.util.ArrayList<Text>();String line="";for(int point:data.name().codePoints().toArray()){String ch=new String(Character.toChars(point));if(!line.isEmpty()&&textRenderer.getWidth(line+ch)>200){lines.add(Text.literal(line));line="";}line+=ch;}if(!line.isEmpty())lines.add(Text.literal(line));c.drawTooltip(textRenderer,lines,mx,my);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return false;layout();double x=(mx-left)/scale,y=(my-top)/scale;if(hit(x,y,585,18,30,30)){close();return true;}if(waiting>=0)return false;
        for(int i=0;i<3;i++)if(hit(x,y,40+i*189,64,182,32)){waiting=Util.getMeasuringTimeMs();ReconfigurationClient.send(data.token(),ReconfigurationProtocol.SELECT,i);return true;}
        if(hit(x,y,215,481,210,32)){if(data.state()==1){close();return true;}if(data.available()){waiting=Util.getMeasuringTimeMs();sound("block.amethyst_block.hit",1.1f,.45f);ReconfigurationClient.send(data.token(),ReconfigurationProtocol.APPLY,data.mode());}return true;}return false;
    }
    @Override public void tick(){if(waiting>=0&&Util.getMeasuringTimeMs()-waiting>7000)waiting=-1;}
    @Override public void close(){ReconfigurationClient.closed(data.token());super.close();}
    @Override public boolean shouldPause(){return false;}
}
