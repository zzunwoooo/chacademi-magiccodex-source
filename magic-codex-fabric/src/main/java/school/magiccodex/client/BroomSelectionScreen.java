package school.magiccodex.client;

import java.util.Arrays;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/** Five-card selector using the server's Motion snapshot and the existing equipment font/cache. */
final class BroomSelectionScreen extends Screen {
    private static final Identifier FONT=Identifier.of("magiccodex","hud_bold");
    private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/equipment/broom_selection_panel.png");
    private static final Identifier CARD=Identifier.of("magiccodex","textures/gui/equipment/broom_selection_card.png");
    private final EquipmentScreen parent;
    private int page;
    private String selected="",pending="",notice="";
    private long requestVersion,deadline;
    BroomSelectionScreen(EquipmentScreen parent){super(Text.literal("빗자루 선택"));this.parent=parent;}
    @Override protected void init(){BroomUiLink.refresh();if(selected.isBlank())selected=BroomUiLink.equipped();}
    @Override public void tick(){
        if(client==null||client.player==null||!client.player.isAlive()){close();return;}
        var owned=Arrays.asList(BroomUiLink.owned());page=BroomSelectionLayout.page(page,owned.size());
        if(!selected.isBlank()&&!owned.contains(selected))selected="";
        if(!pending.isBlank()&&BroomUiLink.version()!=requestVersion){
            notice=pending.equals(BroomUiLink.equipped())?"장착했습니다.":"보유 권한을 확인해 주세요.";pending="";
        }else if(!pending.isBlank()&&Util.getMeasuringTimeMs()>deadline){pending="";notice="서버 응답이 늦습니다. 다시 선택해 주세요.";BroomUiLink.refresh();}
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        var l=CodexLayout.codexFit(width,height);double mx=l.localX(mouseX),my=l.localY(mouseY);
        String[] owned=BroomUiLink.owned();page=BroomSelectionLayout.page(page,owned.length);String equipped=BroomUiLink.equipped();
        var images=UiResources.images();var text=UiResources.text();images.beginFrame();text.beginFrame();
        c.fill(0,0,width,height,0x65040911);var m=c.getMatrices();m.push();
        try{
            m.translate(l.x(),l.y(),0);m.scale(l.scale(),l.scale(),1);
            images.drawTexture(c,PANEL,0,0,0,0,1672,941,1672,941);
            label(c,"빗자루 선택",836,88,42,0xFFF0D69D,440);
            label(c,"×",1547,95,48,0xFFE9D7AB,54);
            int hover=-1;
            for(int slot=0;slot<5;slot++){
                int index=page*5+slot;if(index>=owned.length)break;
                String id=owned[index];var r=BroomSelectionLayout.card(slot);boolean over=r.contains(mx,my);
                if(over)hover=slot;
                // Actual alpha bounds, verified from source bytes; transparent margins do not shift the cards.
                images.drawTexture(c,CARD,r.x(),r.y(),57,89,r.w(),r.h(),731,1652,845,1860);
                if(id.equals(selected)||id.equals(equipped)){
                    int color=id.equals(equipped)?0xCCA9DCE2:0x99D6C48F;
                    HudMesh.line(c,r.x()+22,r.y()+r.h()-13,r.x()+r.w()-22,r.y()+r.h()-13,2,color);
                }
                c.enableScissor(r.x()+20,r.y()+22,r.x()+r.w()-20,r.y()+r.h()-80);
                try{
                    boolean rendered=BroomUiLink.render(c,id,r.x()+r.w()/2f,r.y()+r.h()*.43f,r.h()*.65f,0,90,over?(float)(Util.getMeasuringTimeMs()%12000)*.03f:0);
                    if(!rendered)label(c,"모델 준비 중",r.x()+r.w()/2f,r.y()+r.h()*.45f,21,0xFF9AB0C4,r.w()-48);
                }finally{c.disableScissor();}
                label(c,BroomUiLink.text("name",id),r.x()+r.w()/2f,r.y()+r.h()-33,26,0xFFEEE6D3,r.w()-56);
                if(id.equals(equipped))label(c,"장착 중",r.x()+r.w()/2f,r.y()+r.h()-90,23,0xFFABDCE3,r.w()-50);
            }
            if(owned.length==0)label(c,BroomUiLink.available()?"보유한 빗자루가 없습니다. 비행 마법은 연습용 빗자루로 10초간 탑승합니다.":"빗자루 정보를 연결할 수 없습니다.",836,444,26,0xFFD3DEE7,1160);
            if(BroomSelectionLayout.pages(owned.length)>1){label(c,"‹",101,463,55,page>0?0xFFEAD5A2:0xFF586779,50);label(c,"›",1567,463,55,page+1<BroomSelectionLayout.pages(owned.length)?0xFFEAD5A2:0xFF586779,50);}
            button(c,BroomSelectionLayout.EQUIP,pending.isBlank()?"장착":"확인 중…",!selected.isBlank()&&pending.isBlank(),mx,my);
            button(c,BroomSelectionLayout.BACK,"뒤로",true,mx,my);
            label(c,notice.isBlank()?"비행 숙련도 "+Math.round(BroomUiLink.mastery()*100)+"%   ·   "+(page+1)+" / "+BroomSelectionLayout.pages(owned.length):notice,836,888,21,0xFFB4C7D7,1000);
            if(hover>=0)tooltip(c,owned[page*5+hover],hover,equipped);
        }finally{m.pop();images.endFrame();}
    }
    private void tooltip(DrawContext c,String id,int slot,String equipped){
        var r=BroomSelectionLayout.tooltip(slot);var values=BroomUiLink.gauges(id);var compare=BroomUiLink.gauges(equipped);var labels=BroomUiLink.labels();var actual=BroomUiLink.gaugeTexts(id);
        var m=c.getMatrices();m.push();try{
            m.translate(0,0,600);c.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),0xF5101E30);
            HudMesh.line(c,r.x()+12,r.y()+2,r.x()+r.w()-12,r.y()+2,1,0xAAD8C28C);
            label(c,BroomUiLink.text("name",id),r.x()+r.w()/2f,r.y()+26,23,0xFFF0DFC0,r.w()-30);
            label(c,BroomUiLink.text("grade",id),r.x()+r.w()/2f,r.y()+53,18,0xFFABC1D2,r.w()-30);
            for(int i=0;i<Math.min(5,Math.min(values.length,Math.min(labels.length,actual.length)));i++){
                int y=r.y()+84+i*33;label(c,labels[i],r.x()+59,y,18,0xFFD4E1E9,94);
                float v=Float.isFinite(values[i])?Math.clamp(values[i],0,1):0;
                c.fill(r.x()+116,y-5,r.x()+238,y+5,0xFF283B4C);c.fill(r.x()+116,y-5,r.x()+116+Math.round(122*v),y+5,v>=.75f?0xFF86BBA2:v>=.5f?0xFF83ADCA:0xFFC9AD7A);
                if(!equipped.isBlank()&&i<compare.length&&Float.isFinite(compare[i])){int x=r.x()+116+Math.round(122*Math.clamp(compare[i],0,1));c.fill(x,y-8,x+1,y+8,0xCCEEE5CF);}
                label(c,actual[i]==null?"":actual[i],r.x()+287,y,18,0xFFE8E0D0,95);
            }
            label(c,"가는 선: 현재 장착",r.x()+r.w()/2f,r.y()+251,15,0xFF9AAEC0,r.w()-30);
        }finally{m.pop();}
    }
    private static void label(DrawContext c,String value,float x,float y,float size,int color,float max){
        var t=UiResources.text();String s=value==null?"":value.replace('\n',' ');
        if(t.width(s,size,FONT)>max){while(s.length()>1&&t.width(s+"…",size,FONT)>max)s=s.substring(0,s.offsetByCodePoints(s.length(),-1));s+="…";}
        t.draw(c,s,x,y,size,color,FONT,true);
    }
    private static void button(DrawContext c,BroomSelectionLayout.Rect r,String title,boolean enabled,double mx,double my){
        c.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),enabled&&r.contains(mx,my)?0xBB284456:0xBB132438);
        HudMesh.line(c,r.x()+14,r.y()+r.h()-2,r.x()+r.w()-14,r.y()+r.h()-2,1,enabled?0xBBD2C094:0x555D7184);
        label(c,title,r.x()+r.w()/2f,r.y()+r.h()/2f,29,enabled?0xFFE7D8B6:0xFF788B9D,r.w()-32);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return true;var l=CodexLayout.codexFit(width,height);double mx=l.localX(x),my=l.localY(y);String[] owned=BroomUiLink.owned();
        if(BroomSelectionLayout.CLOSE.contains(mx,my)||BroomSelectionLayout.BACK.contains(mx,my)){close();return true;}
        if(BroomSelectionLayout.PREVIOUS.contains(mx,my)){page=BroomSelectionLayout.page(page-1,owned.length);return true;}
        if(BroomSelectionLayout.NEXT.contains(mx,my)){page=BroomSelectionLayout.page(page+1,owned.length);return true;}
        if(BroomSelectionLayout.EQUIP.contains(mx,my)){
            if(pending.isBlank()&&!selected.isBlank()&&Arrays.asList(owned).contains(selected)){pending=selected;requestVersion=BroomUiLink.version();deadline=Util.getMeasuringTimeMs()+5000;notice="";BroomUiLink.equip(selected);}return true;
        }
        for(int i=0;i<5;i++)if(page*5+i<owned.length&&BroomSelectionLayout.card(i).contains(mx,my)){selected=owned[page*5+i];notice="";return true;}
        return true;
    }
    @Override public void close(){if(client!=null)client.setScreen(client.player==null?null:parent);}
    @Override public boolean shouldPause(){return false;}
}
