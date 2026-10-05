package school.magiccodex.client;

import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Dynamic text/player layers over the approved, unchanged transparent PNG. */
public final class StatsScreen extends Screen {
    private static final Identifier PANEL=Identifier.of("magiccodex","textures/gui/stats_panel_clean.png");
    private static final Identifier BODY=Identifier.of("magiccodex","label"),BOLD=Identifier.of("magiccodex","hud_bold");
    private static final int WHITE=0xFFF0F2F2,GOLD=0xFFE9D5A1,MUTED=0xFFA9B5C3;
    private static final String[] ROMAN={"I","II","III","IV","V","VI","VII","VIII","IX"};
    private final Supplier<StatsValues> source;
    private final boolean live;
    private RemoteStatsClient.View remote;
    private final StatsStars stars=new StatsStars();
    private CodexTypography text;
    private HudTextureCache images;
    private boolean sounded,hovered;
    private int socialHover;
    private long noticeAt=-1,lastSocialClick=-1000;
    private String notice="";
    private float lookX,lookY;
    private float backdropCircle=Float.NaN;
    private long lastTime;
    private int resourceGeneration=-1;
    private final UiResources.Entrance entrance=new UiResources.Entrance();
    public StatsScreen(){this(StatsClient::values,true);}
    public StatsScreen(RemoteStatsClient.View view){this(view::values,false);remote=view;}
    public boolean displays(RemoteStatsClient.View view){return remote==view;}
    public StatsValues displayedValues(){return source.get();}
    public net.minecraft.entity.player.PlayerEntity portraitPlayer(){return remote==null?client.player:remote.avatar();}
    public void showNotice(String message){notice=message;noticeAt=Util.getMeasuringTimeMs();}
    /** Preview fixture shares production rendering, but never subscribes to permissions. */
    public StatsScreen(Supplier<StatsValues> source,boolean live){super(Text.literal("내 정보"));this.source=source;this.live=live;}
    public boolean isLive(){return live;}
    @Override protected void init(){
        text=UiResources.text();images=UiResources.images();resourceGeneration=UiResources.generation();
        if(!sounded){sound(1.12f,.20f);sounded=true;}
    }
    public void invalidateResources(){release();}
    private void release(){text=null;images=null;}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        if(text==null || images==null || resourceGeneration!=UiResources.generation())init();
        var l=StatsLayout.fit(width,height);var v=source.get();long now=Util.getMeasuringTimeMs();
        double mx=l.localX(mouseX),my=l.localY(mouseY);
        boolean hover=StatsLayout.CLOSE.contains(mx,my);
        if(hover&&!hovered)sound(1.45f,.07f);hovered=hover;
        int nextHover=StatsLayout.socialContains(StatsLayout.FRIEND,mx,my)?1:StatsLayout.socialContains(StatsLayout.POPULARITY,mx,my)?2:0;
        if(nextHover!=0&&nextHover!=socialHover)sound(1.4f,.07f);socialHover=nextHover;
        float dt=lastTime==0?0:Math.min(.1f,(now-lastTime)/1000f);lastTime=now;
        float smoothing=1-(float)Math.exp(-dt*8);
        if(Float.isNaN(backdropCircle))backdropCircle=v.circle();
        else backdropCircle+=(v.circle()-backdropCircle)*(1-(float)Math.exp(-dt*3));
        lookX+=((float)Math.clamp((mx-377)*.09,-14,14)-lookX)*smoothing;
        lookY+=((float)Math.clamp((my-470)*.07,-10,10)-lookY)*smoothing;
        text.beginFrame();images.beginFrame();ctx.fill(0,0,width,height,0x30040911);
        ctx.getMatrices().push();
        try{
            ctx.getMatrices().translate(l.x(),l.y(),0);ctx.getMatrices().scale(l.scale(),l.scale(),1);
            images.drawTexture(ctx,PANEL,0,0,0,0,1448,1086,1448,1086,1448,1086,0xF2FFFFFF);
            StatsBackdrop.draw(ctx,backdropCircle,now/1000.0);
            stars.update(v.circle(),now/1000.0);
            drawStars(ctx,false,v.circle());
            portrait(ctx);
            ctx.getMatrices().push();ctx.getMatrices().translate(0,0,200);
            drawStars(ctx,true,v.circle());
            socialButton(ctx,StatsLayout.FRIEND,remote==null?"친구 목록":"친구 추가",true,socialHover==1);
            socialButton(ctx,StatsLayout.POPULARITY,"인기도 상승",false,socialHover==2);
            drawNotice(ctx,now);
            label(ctx,"내 정보",220,107,43,WHITE,true);
            label(ctx,"차카데미아",223,150,22,GOLD,false);
            label(ctx,"능력치",708,218,27,GOLD,true);
            if(remote==null&&live){
                infoAction(ctx,790,85,"닉네임",0,mx,my);
                infoAction(ctx,956,85,"장비",1,mx,my);
                infoAction(ctx,1122,85,"칭호",2,mx,my);
            }
            centered(ctx,v.nickname(),377,784,33,WHITE,BOLD,378);
            centered(ctx,v.circle()==0?"클래스 미정":"클래스 "+v.circle(),377,844,31,GOLD,BOLD,274);
            label(ctx,"기숙사",128,871,24,GOLD,false);
            centered(ctx,v.dormitory(),377,918,28,WHITE,BODY,465);
            // Full-height rows: consistent text baseline, optical icon size and value alignment.
            int[] separators={250,328,406,526,604,682,760,880,958};
            for(int line:separators)HudMesh.line(ctx,708,line,1342,line,1,0x50798894);
            StatIcons.draw(ctx,0,745,289,50);row(ctx,"마력",StatsValues.number(v.power()),289);
            StatIcons.draw(ctx,1,745,367,50);row(ctx,"체력",StatsValues.number(v.health()),367);
            StatIcons.draw(ctx,2,745,466,50);row(ctx,"마나 최대치",StatsValues.number(v.maxMana()),440);
            gaugeTrack(ctx,477);gauge(ctx,StatsValues.fraction(v.mana(),v.maxMana()),477,0xFF22C7E3,0xFF9CF8F1);
            right(ctx,StatsValues.number(v.mana())+" / "+StatsValues.number(v.maxMana()),1336,506,20,MUTED);
            StatIcons.draw(ctx,3,745,565,50);row(ctx,"마나 회복",StatsValues.number(v.manaRegen())+(v.manaRegen()==null?"":" / 초"),565);
            MagicHasteIcon.draw(ctx,745,643,25);row(ctx,"마법 가속",StatsValues.number(v.haste()),643);
            StatIcons.draw(ctx,4,745,721,50);row(ctx,"방어력",StatsValues.number(v.armor()),721);
            StatIcons.draw(ctx,5,745,820,50);row(ctx,"배운 마법 수",(v.learned()==null?"—":v.learned())+" / "+v.total(),800);
            gaugeTrack(ctx,842);gauge(ctx,StatsValues.fraction(v.learned()==null?null:v.learned().doubleValue(),(double)v.total()),842,0xFFE1B557,0xFFFFE6A2);
            StatIcons.draw(ctx,6,745,919,50);row(ctx,"인기도",StatsValues.number(v.popularity()),919);
            if(mx>=708&&mx<1342&&my>=604&&my<682&&v.haste()!=null){
                HudMesh.capsule(ctx,794,667,547,34,0xF5102534,0xF5102534);
                text.draw(ctx,"재사용 대기시간 "+StatsValues.number(school.magiccodex.protocol.MagicHaste.reduction(v.haste()))+"% 감소",1068,684,19,GOLD,BODY,true);
            }
            centered(ctx,"ESC 닫기 · "+StatsClient.keyLabel(),724,1006,21,MUTED,BODY,350);
            if(hover){
                HudMesh.line(ctx,1311,94,1346,129,2.6f,0xFF92E9F2);
                HudMesh.line(ctx,1346,94,1311,129,2.6f,0xFF92E9F2);
            }
            ctx.getMatrices().pop();
        }finally{ctx.getMatrices().pop();images.endFrame();}
        entrance.draw(ctx,width,height);
    }
    private void infoAction(DrawContext c,int x,int y,String name,int icon,double mx,double my){
        boolean hover=mx>=x&&mx<x+154&&my>=y&&my<y+55;
        HudMesh.capsule(c,x,y,154,55,hover?0xC895DDDF:0xA091927B,hover?0xC895DDDF:0xA091927B);
        HudMesh.capsule(c,x+1,y+1,152,53,0xEF101E2C,0xEF101E2C);
        Identifier asset=Identifier.of("magiccodex",icon==1?"textures/hud/equipment-menu.png":icon==2?"textures/hud/titles-menu.png":"textures/hud/top-menu.png");
        if(icon==0)images.drawTexture(c,asset,x+14,y+15,1470,438,22,25,165,191,1774,887,0xFFF0E4C5);
        else if(icon==1)images.drawTexture(c,asset,x+14,y+13,384,298,26,29,546,608,1312,1199,0xFFF0E4C5);
        else images.drawTexture(c,asset,x+14,y+13,431,243,26,29,539,602,1402,1122,0xFFF0E4C5);
        text.draw(c,name,x+91,y+27.5f,23,hover?0xFF96E4EC:GOLD,BOLD,true);
    }
    private void gaugeTrack(DrawContext c,int y){HudMesh.capsule(c,799,y-9,544,18,0xFF6A8293,0xFF6A8293);HudMesh.capsule(c,801,y-7,540,14,0xFF0C2031,0xFF0C2031);}
    private void row(DrawContext c,String name,String value,float y){
        label(c,name,801,y,28,WHITE,true);right(c,value,1336,y,29,WHITE);
    }
    private void socialButton(DrawContext c,CodexHitboxes.Rect r,String name,boolean friend,boolean hover){
        if(hover)HudMesh.capsule(c,r.x()-2,r.y()-2,r.width()+4,r.height()+4,0x284DC5EA,0x284DC5EA);
        HudMesh.capsule(c,r.x(),r.y(),r.width(),r.height(),hover?0xCB86DDEA:0x968DABB3,hover?0xCB86DDEA:0x969F977E);
        HudMesh.capsule(c,r.x()+1.5f,r.y()+1.5f,r.width()-3,r.height()-3,hover?0xF0224155:0xEB112433,hover?0xF01C3749:0xEB142735);
        float ix=r.centerX()-2,iy=r.centerY();
        int color=hover?0xFFACF3F5:0xFFE2D7B8;
        if(friend){
            HudMesh.disk(c,ix-3,iy-6,4.1f,color);
            HudMesh.capsule(c,ix-10,iy,14,10,color,color);
            HudMesh.line(c,ix+9,iy-5,ix+9,iy+5,2,color);HudMesh.line(c,ix+4,iy,ix+14,iy,2,color);
        }else{
            HudMesh.star(c,ix-1,iy+1,9,0xFFDC9B97);
            HudMesh.disk(c,ix-4,iy-4,4.7f,0xFFDC9B97);HudMesh.disk(c,ix+2,iy-4,4.7f,0xFFDC9B97);
            HudMesh.line(c,ix+10,iy-6,ix+10,iy+5,1.7f,color);
            HudMesh.line(c,ix+6,iy-2,ix+10,iy-6,1.7f,color);HudMesh.line(c,ix+10,iy-6,ix+14,iy-2,1.7f,color);
        }
        if(hover){
            float tw=text.width(name,22,BODY)+28,tx=r.centerX()-tw/2,ty=r.y()-44;
            c.getMatrices().push();
            try{
                c.getMatrices().translate(0,0,5);
                HudMesh.capsule(c,tx,ty,tw,34,0xC779B7C7,0xC779B7C7);
                HudMesh.capsule(c,tx+1,ty+1,tw-2,32,0xF5102534,0xF5102534);
                centered(c,name,r.centerX(),ty+17,22,WHITE,BODY,tw-20);
            }finally{c.getMatrices().pop();}
        }
    }
    private void drawNotice(DrawContext c,long now){
        long age=now-noticeAt;if(noticeAt<0||age>=2600||notice.isEmpty())return;
        float alpha=Math.min(1,Math.min(age/140f,(2600-age)/400f));
        HudMesh.capsule(c,128,218,500,39,((int)(225*alpha)<<24)|0x102735,((int)(225*alpha)<<24)|0x102735);
        centered(c,notice,378,237,21,((int)(255*alpha)<<24)|0xDDEDEF,BODY,476);
    }
    private void label(DrawContext c,String s,float x,float y,float size,int color,boolean bold){text.draw(c,s,x,y,size,color,bold?BOLD:BODY,false);}
    private void right(DrawContext c,String s,float x,float y,float size,int color){
        float fit=Math.min(size,size*250/Math.max(1,text.width(s,size,BODY)));
        text.draw(c,s,x-text.width(s,fit,BODY),y,fit,color,BODY,false);
    }
    private void centered(DrawContext c,String s,float x,float y,float size,int color,Identifier font,float max){
        float fit=Math.min(size,size*max/Math.max(1,text.width(s,size,font)));
        text.draw(c,s,x,y,fit,color,font,true);
    }
    private void gauge(DrawContext c,float fraction,float y,int from,int to){
        if(fraction<=0)return;
        float w=538*fraction;
        HudMesh.capsule(c,802,y-6,w,12,from,to);
        if(w>4)HudMesh.line(c,804,y-4,800+w,y-4,.7f,0x55FFFFFF);
    }
    private void drawStars(DrawContext c,boolean front,int circle){
        int tint=circle<=3?0xFFE3AC:circle<=6?0xCDECF5:0xDED3FF;
        for(int i=0;i<stars.count();i++){
            if(stars.foreground(i)!=front)continue;
            float x=377+stars.x(i)*222,y=474+stars.y(i)*207;
            float size=6+(stars.depth(i)+1)*2.8f;
            HudMesh.star(c,x,y,size*2.0f,0x0C000000|tint);
            HudMesh.star(c,x,y,size*1.3f,0x24000000|tint);
            HudMesh.star(c,x,y,size,0xD9000000|tint);
            HudMesh.star(c,x,y,size*.34f,0xFFFFF7DA);
        }
    }
    private void portrait(DrawContext c){
        var p=portraitPlayer();if(p==null)return;
        float body=p.bodyYaw,yaw=p.getYaw(),pitch=p.getPitch(),head=p.headYaw,previous=p.prevHeadYaw;
        try{
            InventoryScreen.drawEntity(c,155,244,599,727,224,0,377+lookX,485+lookY,p);
        }finally{p.bodyYaw=body;p.setYaw(yaw);p.setPitch(pitch);p.headYaw=head;p.prevHeadYaw=previous;}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(StatsClient.matches(key,scan)||key==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        var l=StatsLayout.fit(width,height);
        if(button==0&&remote==null&&live&&l.localY(y)>=85&&l.localY(y)<140){
            double mx=l.localX(x);
            if(mx>=790&&mx<944){NicknameClient.open(this);sound(1.12f,.2f);return true;}
            if(mx>=956&&mx<1110){EquipmentClient.open();sound(1.12f,.2f);return true;}
            if(mx>=1122&&mx<1276){TitleClient.open();sound(1.12f,.2f);return true;}
        }
        if(button==0&&StatsLayout.CLOSE.contains(l.localX(x),l.localY(y))){close();return true;}
        if(button==0&&remote==null&&live&&l.localX(x)>=188&&l.localX(x)<566&&l.localY(y)>=758&&l.localY(y)<810){NicknameClient.open(this);return true;}
        if(button==0){
            var action=StatsLayout.socialContains(StatsLayout.FRIEND,l.localX(x),l.localY(y))?StatsSocialActions.Action.FRIEND:
                StatsLayout.socialContains(StatsLayout.POPULARITY,l.localX(x),l.localY(y))?StatsSocialActions.Action.POPULARITY:null;
            if(action!=null){
                long now=Util.getMeasuringTimeMs();
                if(now-lastSocialClick<400)return true;
                lastSocialClick=now;sound(1.12f,.2f);
                if(client.player!=null){
                    try{if(remote==null&&action==StatsSocialActions.Action.FRIEND){SocialClient.open();notice="";}else notice=remote==null?"다른 플레이어의 내 정보에서 사용할 수 있습니다.":remote.action(action);}
                    catch(RuntimeException error){notice="요청을 처리하지 못했습니다.";org.slf4j.LoggerFactory.getLogger("magiccodex").warn("Stats social action failed",error);}
                    noticeAt=now;
                }
                return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public void tick(){if(client.player==null || client.world==null || !client.player.isAlive())close();}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){sound(.95f,.18f);super.close();}
    @Override public void removed(){if(remote!=null)remote.close();release();super.removed();}
    private void sound(float pitch,float volume){client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),pitch,volume));}
    public void verifyRenderer(){if(images==null||images.size()==0)throw new IllegalStateException("Stats image missing");images.verifyGpuState();}
}
