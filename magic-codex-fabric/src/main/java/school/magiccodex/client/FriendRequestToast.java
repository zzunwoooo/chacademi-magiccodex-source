package school.magiccodex.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.resource.*;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import school.magiccodex.protocol.SocialProtocol;
import school.magiccodex.protocol.SocialProtocol.Entry;

/** 친구 신청 알림 카드. 화면을 열지 않는 HUD 표시라 조작을 빼앗지 않으며, Alt 커서 모드에서만 클릭된다. */
public final class FriendRequestToast {
    /** 1920x1080 기준 좌표. */
    static final float W=600,H=168,X=1920-W-28,Y=236,BUTTON_W=150,BUTTON_H=46,BUTTON_Y=104,ACCEPT_X=W-2*BUTTON_W-36,DECLINE_X=W-BUTTON_W-22;
    static final long SHOW_MILLIS=20000;
    private static final Identifier BODY=Identifier.of("magiccodex","label"),BOLD=Identifier.of("magiccodex","hud_bold");
    private static final FriendRequestQueue QUEUE=new FriendRequestQueue();
    private static Entry current;private static long started,pausedAt;
    private static CodexTypography text;
    private FriendRequestToast(){}
    static void initialize(){
        HudRenderCallback.EVENT.register((ctx,ticks)->render(ctx));
        // HudCursorScreen(Alt 커서 모드)의 클릭만 가로챈다. 다른 화면에서는 카드가 보이지 않는다.
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            if(screen instanceof HudCursorScreen)ScreenMouseEvents.allowMouseClick(screen).register((s,x,y,button)->!click(x,y,button));
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){public Identifier getFabricId(){return Identifier.of("magiccodex","friend_request_toast");}public void reload(ResourceManager manager){close();}});
    }
    static void offer(Entry e){
        if(current!=null&&current.id().equals(e.id()))return;
        if(QUEUE.offer(e))MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.2f,.35f));
    }
    /** 서버가 보낸 받은 신청 목록과 맞춘다 (친구창에서 처리했거나 상대가 취소한 신청의 카드는 닫는다). */
    static void sync(List<Entry> incoming){
        var ids=new HashSet<UUID>();for(var e:incoming)ids.add(e.id());
        QUEUE.retain(ids);if(current!=null&&!ids.contains(current.id()))current=null;
    }
    static void reset(){QUEUE.clear();current=null;started=pausedAt=0;close();}
    private static void close(){if(text!=null){text.close();text=null;}}
    private static boolean visible(MinecraftClient c){return c.player!=null&&c.world!=null&&c.player.isAlive()&&!c.options.hudHidden&&c.getOverlay()==null&&(c.currentScreen==null||c.currentScreen instanceof HudCursorScreen);}
    private static float scale(MinecraftClient c){return Math.min(c.getWindow().getScaledWidth()/1920f,c.getWindow().getScaledHeight()/1080f);}
    /** 카드 기준 좌표로 바꾼 마우스 위치. 카드는 화면 오른쪽에 붙는다. */
    private static float localX(MinecraftClient c,double x){float s=scale(c);return (float)((x-(c.getWindow().getScaledWidth()-(1920-X)*s))/s);}
    private static float localY(MinecraftClient c,double y){return (float)(y/scale(c)-Y);}
    static int hit(float x,float y){return FriendRequestQueue.hit(x,y);}
    /** @return 클릭을 소비했으면 true */
    static boolean click(double x,double y,int button){
        var c=MinecraftClient.getInstance();
        if(button!=0||current==null||!visible(c)||!(c.currentScreen instanceof HudCursorScreen))return false;
        float lx=localX(c,x),ly=localY(c,y);
        if(lx<0||lx>=W||ly<0||ly>=H)return false;
        int hit=hit(lx,ly);if(hit==0)return true;
        c.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1.15f,.23f));
        // 전송하지 못했으면(연결 문제 등) 카드를 그대로 둔다.
        if(SocialClient.respond(null,current.id(),hit==1))current=null;
        return true;
    }
    private static void render(DrawContext ctx){
        var c=MinecraftClient.getInstance();long now=Util.getMeasuringTimeMs();
        if(!visible(c)){if(current!=null&&pausedAt==0)pausedAt=now;return;}
        if(pausedAt!=0){started+=now-pausedAt;pausedAt=0;}
        if(current==null){current=QUEUE.poll();if(current==null)return;started=now;}
        long age=now-started;
        // 시간이 지나면 거절하지 않고 닫는다. 신청은 친구창의 받은 신청 목록에 남는다.
        if(age>=SHOW_MILLIS){current=null;return;}
        if(text==null)text=new CodexTypography(c);
        boolean cursor=c.currentScreen instanceof HudCursorScreen;var w=c.getWindow();
        float s=scale(c),alpha=Math.min(1,Math.min(age/220f,(SHOW_MILLIS-age)/500f));
        int hot=cursor?hitInside(localX(c,c.mouse.getX()*w.getScaledWidth()/w.getWidth()),localY(c,c.mouse.getY()*w.getScaledHeight()/w.getHeight())):0;
        text.beginFrame();ctx.getMatrices().push();
        try{
            ctx.getMatrices().translate(w.getScaledWidth()-(1920-X)*s+(1-alpha)*24*s,Y*s,340);ctx.getMatrices().scale(s,s,1);
            HudMesh.quad(ctx,0,0,W,H,color(alpha,0xD8,0xA99770),color(alpha,0xD8,0x758A9C));
            HudMesh.quad(ctx,2,2,W-4,H-4,color(alpha,0xF2,0x102536),color(alpha,0xF2,0x0C1F32));
            HudMesh.quad(ctx,2,2,6,H-4,color(alpha,0xFF,0xE7CE91),color(alpha,0xFF,0xE7CE91));
            text.draw(ctx,"친구 신청",28,32,24,color(alpha,0xFF,0xE7CE91),BODY,false);
            if(QUEUE.size()>0)text.draw(ctx,"+"+QUEUE.size()+"건 대기",W-24-text.width("+"+QUEUE.size()+"건 대기",20,BODY),32,20,color(alpha,0xFF,0xA5B8C8),BODY,false);
            String name=current.name(),tail=" 님이 친구 신청을 보냈습니다";
            float tailWidth=text.width(tail,26,BODY),size=Math.max(10,Math.min(28,28*(W-56-tailWidth)/Math.max(1,text.width(name,28,BOLD)))),nameWidth=text.width(name,size,BOLD);
            text.draw(ctx,name,28,72,size,color(alpha,0xFF,SocialLayout.color(current.dormitory())&0xFFFFFF),BOLD,false);
            text.draw(ctx,tail,28+nameWidth,72,26,color(alpha,0xFF,0xF0F3F5),BODY,false);
            text.draw(ctx,cursor?"버튼을 눌러 주세요":"Alt 누른 채 클릭",28,BUTTON_Y+BUTTON_H/2,20,color(alpha,0xFF,0xA5B8C8),BODY,false);
            button(ctx,"수락",ACCEPT_X,hot==1,alpha);button(ctx,"거절",DECLINE_X,hot==2,alpha);
            // 남은 표시 시간
            HudMesh.quad(ctx,2,H-5,(W-4)*(1-age/(float)SHOW_MILLIS),3,color(alpha,0xB0,0x92E7F2),color(alpha,0xB0,0x92E7F2));
        }finally{ctx.getMatrices().pop();}
    }
    private static int hitInside(float x,float y){return x<0||x>=W||y<0||y>=H?0:hit(x,y);}
    private static int color(float alpha,int a,int rgb){return ((int)(a*alpha)<<24)|(rgb&0xFFFFFF);}
    private static void button(DrawContext ctx,String label,float x,boolean hover,float alpha){
        int edge=color(alpha,0xFF,hover?0x4BADC2:0x6A839A),fill=color(alpha,0xFF,hover?0x16394B:0x0C1F32);
        HudMesh.capsule(ctx,x,BUTTON_Y,BUTTON_W,BUTTON_H,edge,edge);HudMesh.capsule(ctx,x+1.5f,BUTTON_Y+1.5f,BUTTON_W-3,BUTTON_H-3,fill,fill);
        text.draw(ctx,label,x+BUTTON_W/2,BUTTON_Y+BUTTON_H/2,22,color(alpha,0xFF,hover?0x92E7F2:0xE7CE91),BODY,true);
    }
}
