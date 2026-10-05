package school.magiccodex.client;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import school.magiccodex.protocol.QuestProtocol;

/** Approved PNG assets, dynamic server text, six cards and a detail panel. */
public final class QuestScreen extends Screen {
    private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/quests/panel.png"),CARD=Identifier.of("magiccodex","textures/gui/quests/quest-card.png"),BUTTON=Identifier.of("magiccodex","textures/gui/quests/accept-button.png"),FONT=Identifier.of("magiccodex","label"),BOLD=Identifier.of("magiccodex","hud_bold");
    private static final Identifier STAMP=Identifier.of("magiccodex","textures/gui/quests/completed-stamp.png");
    private static final int GOLD=0xFFE8D19B,WHITE=0xFFF1EEE3,MUTED=0xFFB9CBD8,INK=0xFF263A4A;
    private String selected="",notice="";private long refresh,waiting,noticeUntil;private int page,tab,rewardScroll,descriptionScroll;private boolean confirmAbandon;
    public QuestScreen(){super(Text.literal("퀘스트 일지"));}
    @Override public boolean shouldPause(){return false;}
    void received(String message){waiting=0;page=QuestClient.data.page();if(!message.isEmpty()){notice=message;noticeUntil=Util.getMeasuringTimeMs()+6000;}}
    @Override public void tick(){long n=Util.getMeasuringTimeMs();if(waiting!=0&&n>waiting){waiting=0;notice="응답이 늦습니다. 잠시 후 다시 시도해 주세요.";noticeUntil=n+6000;}if(n>=refresh&&waiting==0){QuestClient.send(0,"",page,tab);refresh=n+3000;}}
    private QuestProtocol.Card selected(){var cards=QuestClient.data.cards();if(cards.isEmpty())return null;return cards.stream().filter(c->c.id().equals(selected)).findFirst().orElse(cards.getFirst());}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){var l=CodexLayout.codexFit(width,height);double mx=l.localX(mouseX),my=l.localY(mouseY);var images=UiResources.images();images.beginFrame();UiResources.text().beginFrame();c.fill(0,0,width,height,0x65050A14);c.getMatrices().push();try{
        c.getMatrices().translate(l.x(),l.y(),0);c.getMatrices().scale(l.scale(),l.scale(),1);images.drawTexture(c,PANEL,0,0,0,0,1671,941,1671,941);
        text(c,"퀘스트 일지",835,58,35,GOLD,true,true);for(int i=0;i<3;i++){int x=130+i*277;HudMesh.line(c,x,131,x+242,131,i==tab?3:1,i==tab?GOLD:0x555E7181);text(c,List.of("수락 가능","완료","진행중").get(i),x+121,113,21,i==tab?GOLD:MUTED,i==tab,true);}
        text(c,"×",1555,94,38,WHITE,true,true);text(c,"메인 퀘스트",1390,58,20,GOLD,true,true);text(c,"남은 서브 의뢰 "+QuestClient.data.remainingSubquests()+" / 3",835,869,19,GOLD,true,true);
        // Subtle time-based glints, no frame-stepped animation or text glow.
        double time=Util.getMeasuringTimeMs()/1000d;for(int i=0;i<12;i++){float x=120+(i*137)%1430,y=145+(i*79)%680;int a=(int)(25+25*(1+Math.sin(time*.8+i)));HudMesh.disk(c,x+(float)Math.sin(time*.2+i)*8,y,1.4f,(a<<24)|0xB1DFFF);}
        var chosen=selected();var cards=QuestClient.data.cards();
        for(int i=0;i<cards.size();i++){var q=cards.get(i);int x=119+(i%3)*289,y=161+(i/3)*318;images.drawTexture(c,CARD,x,y,0,0,278,306,1161,1355,1161,1355,paperTint(q.state()));
            c.getMatrices().push();c.getMatrices().translate(0,0,-.75f);text(c,q.rank(),x+139,y+178,190,0x0E6C5239,true,true);UiResources.text().drawOutline(c,q.rank(),x+139,y+178,190,0x306C5239,BOLD,true);c.getMatrices().pop();
            if(q.main())text(c,"메인",x+139,y+40,17,INK,true,true);wrapped(c,q.title(),x+32,y+76,214,27,INK,true,2);
            divider(c,x+30,y+133,218,0xCF806743);
            wrapped(c,q.description(),x+32,y+157,214,18,INK,false,4);
            if(q.state().equals("active")){long done=q.goals().stream().filter(g->g.current()>=g.required()).count();text(c,"목표 "+done+" / "+q.goals().size(),x+32,y+256,17,INK,true,false);}
            if(q.state().equals("claimed")){c.getMatrices().push();c.getMatrices().translate(x+174,y+224,2);c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-13));images.drawTexture(c,STAMP,-66,-66,0,0,132,132,1254,1254,1254,1254);c.getMatrices().pop();}

        }
        if(cards.isEmpty())text(c,List.of("지금 수락할 의뢰가 없습니다.","아직 완료한 의뢰가 없습니다.","진행 중인 의뢰가 없습니다.").get(tab),548,440,27,MUTED,false,true);
        text(c,"‹",260,826,34,GOLD,true,true);text(c,(QuestClient.data.page()+1)+" / "+Math.max(1,(QuestClient.data.total()+5)/6),370,826,22,WHITE,false,true);text(c,"›",480,826,34,GOLD,true,true);
        if(chosen!=null){text(c,(chosen.main()?"메인 퀘스트":"남은 완료 횟수 "+Math.max(0,chosen.limit()-chosen.completions())+" / "+chosen.limit()),775,826,21,GOLD,true,true);wrapped(c,chosen.title(),1060,185,350,28,GOLD,true,2);text(c,"난이도",1452,185,18,MUTED,false,true);text(c,chosen.rank(),1530,185,26,GOLD,true,true);divider(c,1060,224,492,0xAAAC9872);var lines=descriptionLines(chosen.description());descriptionScroll=Math.clamp(descriptionScroll,0,Math.max(0,lines.size()-3));for(int i=0;i<3&&i+descriptionScroll<lines.size();i++)text(c,lines.get(i+descriptionScroll),1060,255+i*28,21,WHITE,false,false);if(lines.size()>3)text(c,"설명 위에서 휠로 더 보기",1060,332,14,MUTED,false,false);
            text(c,"의뢰 목표",1060,381,23,GOLD,true,false);for(int i=0;i<chosen.goals().size();i++){var g=chosen.goals().get(i);int y=418+i*34;fitted(c,g.label(),1060,y,23,330,WHITE,false);text(c,g.current()+" / "+g.required(),1535,y,21,g.current()>=g.required()?0xFF8AE1C9:MUTED,true,true);}
            text(c,"보상",1060,663,23,GOLD,true,false);var rewards=rewardLines(chosen.reward());rewardScroll=Math.clamp(rewardScroll,0,Math.max(0,rewards.size()-3));for(int i=0;i<3&&i+rewardScroll<rewards.size();i++)fitted(c,rewards.get(i+rewardScroll),1130,663+i*25,19,411,WHITE,false);
            if(rewards.size()>3)text(c,"휠로 더 보기",1515,734,13,MUTED,false,true);
            if(!chosen.state().equals("claimed")){images.drawTexture(c,BUTTON,1082,731,0,0,440,104,2172,724,2172,724);String button=waiting!=0?"처리 중…":switch(chosen.state()){case "active"->"제출 및 보상 받기";case "paying"->"관리자 확인 대기";default->"의뢰 수락";};text(c,button,1301,783,25,WHITE,true,true);}

            if(chosen.state().equals("active"))text(c,confirmAbandon?"다시 누르면 포기합니다":"의뢰 포기",1300,833,18,MUTED,false,true);
        }
        if(Util.getMeasuringTimeMs()<noticeUntil)fitted(c,notice,120,869,19,850,GOLD,false);
    }finally{c.getMatrices().pop();images.endFrame();}}
    private static int paperTint(String state){return switch(state){case "active","paying"->0xFFE5EFE4;case "claimed"->0xFFB1ADA3;default->0xFFFFF1D2;};}
    private static void divider(DrawContext c,float x,float y,float width,int color){c.getMatrices().push();c.getMatrices().translate(0,0,.6f);float mid=x+width/2;HudMesh.line(c,x,y,mid-10,y,2.3f,color);HudMesh.line(c,mid+10,y,x+width,y,2.3f,color);c.getMatrices().translate(mid,y,0);c.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(45));c.fill(-3,-3,3,3,color);c.getMatrices().pop();}
    private static String state(String s){return switch(s){case "active"->"진행 중";case "claimed"->"완료";case "paying"->"처리 확인 중";default->"수락 가능";};}
    private void text(DrawContext c,String s,float x,float y,float size,int color,boolean bold,boolean center){UiResources.text().draw(c,s,x,y,size,color,bold?BOLD:FONT,center);}
    private void fitted(DrawContext c,String s,float x,float y,float size,float w,int color,boolean bold){var f=bold?BOLD:FONT;float fit=Math.min(size,size*w/Math.max(w,UiResources.text().width(s,size,f)));text(c,s,x,y,fit,color,bold,false);}
    private void wrapped(DrawContext c,String s,float x,float y,float w,float size,int color,boolean bold,int max){var f=bold?BOLD:FONT;String line="";int row=0;for(int i=0;i<s.length();i++){String next=line+s.charAt(i);if(s.charAt(i)=='\n'||UiResources.text().width(next,size,f)>w){text(c,line,x,y+row*(size+7),size,color,bold,false);if(++row>=max)return;line=s.charAt(i)=='\n'?"":String.valueOf(s.charAt(i));}else line=next;}if(!line.isEmpty())text(c,line,x,y+row*(size+7),size,color,bold,false);}
    private static boolean hit(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;var l=CodexLayout.codexFit(width,height);double mx=l.localX(x),my=l.localY(y);if(hit(mx,my,1285,37,210,43)){if(client.getNetworkHandler()!=null)client.getNetworkHandler().sendChatCommand("메인퀘스트");return true;}if(hit(mx,my,1520,65,70,65)){close();return true;}
        for(int i=0;i<3;i++)if(hit(mx,my,130+i*277,94,242,42)){tab=i;page=0;selected="";descriptionScroll=rewardScroll=0;waiting=0;send(0,"");return true;}
        for(int i=0;i<QuestClient.data.cards().size();i++)if(hit(mx,my,119+(i%3)*289,161+(i/3)*318,278,306)){selected=QuestClient.data.cards().get(i).id();confirmAbandon=false;descriptionScroll=rewardScroll=0;click();return true;}
        if(waiting!=0)return true;
        if(hit(mx,my,220,797,80,58)||hit(mx,my,440,797,80,58)){int step=mx<370?-1:1;page=Math.clamp(page+step,0,Math.max(0,(QuestClient.data.total()-1)/6));send(0,"");return true;}
        var q=selected();if(q!=null&&hit(mx,my,1100,746,405,75)){if(q.state().equals("available"))send(1,q.id());else if(q.state().equals("active"))send(2,q.id());return true;}
        if(q!=null&&q.state().equals("active")&&hit(mx,my,1110,817,390,34)){if(confirmAbandon){send(3,q.id());confirmAbandon=false;}else confirmAbandon=true;return true;}return super.mouseClicked(x,y,button);
    }
    private void send(int action,String id){if(QuestClient.send(action,id,page,tab)){waiting=Util.getMeasuringTimeMs()+8000;refresh=waiting+1000;click();}}
    private List<String> descriptionLines(String s){var lines=new ArrayList<String>();String line="";for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(ch=='\n'||UiResources.text().width(line+ch,21,FONT)>456){lines.add(line);line=ch=='\n'?"":String.valueOf(ch);}else line+=ch;}if(!line.isEmpty())lines.add(line);return lines;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){var l=CodexLayout.codexFit(width,height);if(hit(l.localX(x),l.localY(y),1050,642,500,98)){rewardScroll+=(int)-Math.signum(vertical);return true;}if(hit(l.localX(x),l.localY(y),1050,236,490,101)){descriptionScroll+=(int)-Math.signum(vertical);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    private List<String> rewardLines(String value){return value.lines().map(line->{var m=java.util.regex.Pattern.compile("([A-Z_]+) × ([0-9]+)").matcher(line);var out=new StringBuffer();while(m.find()){String id=m.group(1).toLowerCase(java.util.Locale.ROOT);var item=net.minecraft.registry.Registries.ITEM.get(Identifier.of("minecraft",id));var stack=item.getDefaultStack();String translated=ItemTooltipRenderer.localName(stack,stack.getName()).getString();m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(translated+" × "+m.group(2)));}m.appendTail(out);return out.toString();}).toList();}
    private void click(){client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1f,.25f));}
}
