package school.magiccodex.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** PNG regions are cropped before mip filtering, preserving transparent edges at small sizes. */
public final class TopMenuRenderer implements AutoCloseable {
    private static final Identifier ATLAS=Identifier.of("magiccodex","textures/hud/top-menu.png");
    private static final Identifier HOUSE=Identifier.of("magiccodex","textures/hud/house-points-menu.png");
    private static final Identifier EQUIPMENT=Identifier.of("magiccodex","textures/hud/equipment-menu.png");
    private static final Identifier STATS=Identifier.of("magiccodex","textures/hud/stats-menu.png");
    private static final Identifier TITLES=Identifier.of("magiccodex","textures/hud/titles-menu.png");
    private record MenuSprite(Identifier texture,int x,int y,int width,int height,int atlasWidth,int atlasHeight){}
    // Visible bounds retain a small transparent margin; all three use the same 22px optical height.
    private static final MenuSprite[] NEW_ICONS={
        new MenuSprite(STATS,404,308,539,592,1351,1164),
        new MenuSprite(EQUIPMENT,384,298,546,608,1312,1199),
        new MenuSprite(TITLES,431,243,539,602,1402,1122)};
    private static final Identifier FONT=Identifier.of("magiccodex","hud_bold");
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");
    private static final HudSeason[] SEASONS=HudSeason.values();
    private static final Identifier[] SEASON_TEXTURES=java.util.Arrays.stream(SEASONS)
            .map(s->Identifier.of(s.texture())).toArray(Identifier[]::new);
    // Bounds include a small transparent margin around each approved atlas sprite.
    private static final int[][] ICONS={{125,433,199,195},{451,436,199,194},{785,458,210,150},
        {1110,415,233,211},{1470,438,165,191},{142,655,166,191},{461,680,180,155},
        {798,676,175,166},{1194,691,87,133},{1505,691,87,133}};
    private static final int[] MENU_ATLAS_ICONS={5,2,3,4,0,6,7,0};
    private final CodexTypography text;
    private final HudTextureCache images;
    private final HudTextureCache seasons;
    private final TopMenuState state;
    private final float[] hover=new float[TopMenuState.LABELS.length+1];
    private long last,clockMinute=-1;
    private String clock="--:--";
    public TopMenuRenderer(MinecraftClient client,TopMenuState state){
        this.state=state;text=new CodexTypography(client);images=new HudTextureCache(client);
        seasons=new HudTextureCache(client,true);
        images.prepareRegion(HOUSE,440,454,374,344);
        for(var sprite:NEW_ICONS)images.prepareRegion(sprite.texture(),sprite.x(),sprite.y(),sprite.width(),sprite.height());
        // Decode off-thread once; only four cached sprites, no per-tick IO or server polling.
        for(var season:SEASONS){var r=season.crop();seasons.prepareRegion(SEASON_TEXTURES[season.ordinal()],r.x(),r.y(),r.width(),r.height());}
    }
    public float baseWidth(){return TopMenuState.baseWidth(text.width(WalletClient.label(),12,FONT));}
    public void render(DrawContext ctx,int width,int height,double mouseX,double mouseY,boolean cursor,long now){
        float scale=PlayerHudLayout.of(width,height).scale(),base=baseWidth();
        state.update(now);
        float dt=last==0?0:Math.clamp((now-last)/110f,0,1);last=now;
        int hit=cursor?state.hit((float)mouseX/scale-6,(float)mouseY/scale-3,base):-2;
        for(int i=0;i<hover.length;i++)hover[i]+=( (hit==i-1?1:0)-hover[i])*dt;
        long minute=System.currentTimeMillis()/60000;
        if(minute!=clockMinute){clockMinute=minute;clock=LocalTime.now().format(CLOCK);}
        text.beginFrame();images.beginFrame();seasons.beginFrame();ctx.getMatrices().push();
        try{
            ctx.getMatrices().scale(scale,scale,1);ctx.getMatrices().translate(6,3,0);
            int barWidth=Math.round(state.width(base));
            // Three slices keep the diamond and the pointed end undistorted during expansion.
            region(ctx,0,0,24,36,54,70,100,150,0xDFFFFFFF);
            region(ctx,24,0,barWidth-40,36,154,70,1492,150,0xDFFFFFFF);
            region(ctx,barWidth-16,0,16,36,1646,70,68,150,0xDFFFFFFF);
            season(ctx);
            String temperature=TemperatureClient.label();
            float tempSize=Math.min(11,11*45/Math.max(1,text.width(temperature,11,FONT)));
            text.draw(ctx,temperature,44,18,tempSize,TemperatureVisuals.labelColor(TemperatureClient.current()),FONT,false);
            float divider=(44+text.width(temperature,tempSize,FONT)+(25+TopMenuState.SEASON_SPACE+TopMenuState.TEMPERATURE_SPACE-8.5f))/2;
            HudMesh.line(ctx,divider,10,divider,26,.6f,0x708F9CA5);
            float offset=TopMenuState.SEASON_SPACE+TopMenuState.TEMPERATURE_SPACE;
            icon(ctx,0,25+offset,18,17,1);
            text.draw(ctx,clock,40+offset,18,12,0xFFF0EDE3,FONT,false);
            HudMesh.line(ctx,85+offset,10,85+offset,26,.6f,0x708F9CA5);
            icon(ctx,1,101+offset,18,17,1);
            String money=WalletClient.label();
            float size=Math.min(12,12*138/Math.max(1,text.width(money,12,FONT)));
            text.draw(ctx,money,115+offset,18,size,0xFFF0EDE3,FONT,false);
            float p=state.progress();
            for(int i=0;i<TopMenuState.LABELS.length;i++){
                // Reveal each icon only after there is room inside the moving right edge.
                float alpha=Math.clamp((p*TopMenuState.LABELS.length-i-.65f)/.35f,0,1);
                if(alpha<=0)continue;
                float x=state.iconCenter(base,i);
                glow(ctx,x,hover[i+1]*alpha);
                if(i==7){
                    images.drawTexture(ctx,HOUSE,Math.round(x-11),8,440,454,22,20,374,344,1254,1254,tint(0xFFFFFFFF,alpha));
                }else if(i==4){
                    menuSprite(ctx,NEW_ICONS[0],x,18,22,alpha);
                }else icon(ctx,MENU_ATLAS_ICONS[i],x,18,22,alpha);
            }
            float arrow=state.arrowCenter(base);
            glow(ctx,arrow,hover[0]);icon(ctx,state.expanded()?9:8,arrow,18,14,1);
        }finally{ctx.getMatrices().pop();images.endFrame();seasons.endFrame();}
    }
    public void renderTooltip(DrawContext ctx,int width,int height,double mouseX,double mouseY,boolean cursor){
        if(!cursor)return;
        float scale=PlayerHudLayout.of(width,height).scale(),base=baseWidth();
        int hit=state.hit((float)mouseX/scale-6,(float)mouseY/scale-3,base);
        float arrow=state.arrowCenter(base);
        ctx.getMatrices().push();
        try{
            ctx.getMatrices().scale(scale,scale,1);ctx.getMatrices().translate(6,3,300);
            if(hit>=0){
                String label=TopMenuState.LABELS[hit];float tw=text.width(label,11,FONT)+18;
                float x=state.iconCenter(base,hit);
                HudMesh.capsule(ctx,x-tw/2,41,tw,23,0xE6101E2C,0xE6101E2C);
                text.draw(ctx,label,x,52.5f,11,0xFFF1ECE0,FONT,true);
            }else if(hit==-1){
                String label=state.expanded()?"접기":"메뉴 펼치기";
                float tw=text.width(label,11,FONT)+18;
                HudMesh.capsule(ctx,arrow-tw/2,41,tw,23,0xE6101E2C,0xE6101E2C);
                text.draw(ctx,label,arrow,52.5f,11,0xFFF1ECE0,FONT,true);
            }else if(cursor && TopMenuState.seasonHovered((float)mouseX/scale-6,(float)mouseY/scale-3)){
                String label=state.season().label();float tw=text.width(label,11,FONT)+18;
                float x=Math.max(tw/2+4,TopMenuState.SEASON_CENTER);
                HudMesh.capsule(ctx,x-tw/2,41,tw,23,0xE6101E2C,0xE6101E2C);
                text.draw(ctx,label,x,52.5f,11,0xFFF1ECE0,FONT,true);
            }else if(cursor && (float)mouseX/scale-6>=42 && (float)mouseX/scale-6<=92
                    && (float)mouseY/scale-3>=5 && (float)mouseY/scale-3<=31){
                String label=TemperatureClient.previewing()?"온도 미리보기":Float.isFinite(TemperatureClient.current())?"현재 온도":"서버 온도 연결 대기";
                float tw=text.width(label,11,FONT)+18,x=Math.max(tw/2+4,68);
                HudMesh.capsule(ctx,x-tw/2,41,tw,23,0xE6101E2C,0xE6101E2C);
                text.draw(ctx,label,x,52.5f,11,0xFFF1ECE0,FONT,true);
            }

        }finally{ctx.getMatrices().pop();}
    }
    private void season(DrawContext ctx){
        var season=state.season();var r=season.crop();float scale=22f/Math.max(r.width(),r.height());
        int w=Math.round(r.width()*scale),h=Math.round(r.height()*scale);
        seasons.drawTexture(ctx,SEASON_TEXTURES[season.ordinal()],Math.round(TopMenuState.SEASON_CENTER-w/2f),
                Math.round(17-h/2f),r.x(),r.y(),w,h,r.width(),r.height(),1254,1254,0xFFFFFFFF);
    }
    private void glow(DrawContext ctx,float x,float amount){
        if(amount<.005f)return;
        HudMesh.capsule(ctx,x-17,3,34,30,tint(0x183C9FE0,amount),tint(0x183C9FE0,amount));
        HudMesh.capsule(ctx,x-14,5,28,26,tint(0x484391C2,amount),tint(0x484391C2,amount));
        HudMesh.line(ctx,x-10,32,x+10,32,1,tint(0xDB8DDEFA,amount));
        HudMesh.line(ctx,x-12,6,x+12,6,.65f,tint(0xAA8DDEFA,amount));
    }
    private static int tint(int color,float alpha){return (color&0xFFFFFF)|Math.round((color>>>24)*alpha)<<24;}
    private void icon(DrawContext ctx,int index,float cx,float cy,int size,float alpha){
        int[] r=ICONS[index];float ratio=(float)size/Math.max(r[2],r[3]);
        int w=Math.round(r[2]*ratio),h=Math.round(r[3]*ratio);
        region(ctx,Math.round(cx-w/2f),Math.round(cy-h/2f),w,h,r[0],r[1],r[2],r[3],tint(0xFFFFFFFF,alpha));
    }
    private void menuSprite(DrawContext ctx,MenuSprite sprite,float cx,float cy,int size,float alpha){
        float ratio=(float)size/Math.max(sprite.width(),sprite.height());
        int w=Math.round(sprite.width()*ratio),h=Math.round(sprite.height()*ratio);
        images.drawTexture(ctx,sprite.texture(),Math.round(cx-w/2f),Math.round(cy-h/2f),
                sprite.x(),sprite.y(),w,h,sprite.width(),sprite.height(),sprite.atlasWidth(),sprite.atlasHeight(),tint(0xFFFFFFFF,alpha));
    }
    private void region(DrawContext ctx,int x,int y,int w,int h,int u,int v,int sw,int sh,int color){
        images.drawTexture(ctx,ATLAS,x,y,u,v,w,h,sw,sh,1774,887,color);
    }
    public void verify(){images.verifyGpuState();seasons.verifyGpuState();}
    @Override public void close(){text.close();images.close();seasons.close();}
}
