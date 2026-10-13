package school.magiccodex.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Approved atlas + dynamic font, one coordinate system for rendering and interaction. */
public final class SettingsScreen extends Screen {
    enum Page { HOME, EFFECT, CAMERA }
    private final Screen parent;
    private final Page page;
    private final AimSettings camera;
    private float own=1,other=1;
    private String notice="";
    private int dragging=-1;
    private float scale,ox,oy;
    private double mx,my;
    private static final Identifier FONT=Identifier.of("magiccodex","hud_bold"), TITLE=Identifier.of("magiccodex","ui_extra_bold");
    public SettingsScreen(Screen parent){this(parent,Page.HOME);}
    private SettingsScreen(Screen parent,Page page){super(Text.literal("설정"));this.parent=parent;this.page=page;camera=AimSettings.get().copy();
        if(page==Page.EFFECT)try{float[] v=(float[])Class.forName("dev.portablevfx.client.api.VfxPreferences").getMethod("values").invoke(null);own=v[0];other=v[1];}
        catch(ReflectiveOperationException|LinkageError e){notice="스킬 이펙트 모드를 확인해 주세요.";}
    }
    private void fit(double x,double y){scale=Math.min(width*.9f/1000,height*.9f/730);ox=(width-1000*scale)/2;oy=(height-730*scale)/2;mx=(x-ox)/scale;my=(y-oy)/scale;}
    private boolean hit(float x,float y,float w,float h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void sprite(DrawContext c,String name,int x,int y,int w,int h,int sw,int sh){UiResources.images().drawTexture(c,Identifier.of("magiccodex","textures/gui/followup/"+name+".png"),x,y,0,0,w,h,sw,sh,sw,sh,0xFFFFFFFF);}
    private void text(DrawContext c,String value,float x,float y,float size,boolean title,boolean center){UiResources.text().draw(c,value,x,y,size,0xFFF5E7CA,title?TITLE:FONT,center);}
    private void button(DrawContext c,String label,int x,int y,int w,boolean primary){sprite(c,hit(x,y,w,56)?"button_hover":primary?"button_primary":"button",x,y,w,56,337,primary?105:96);text(c,label,x+w/2f,y+28,23,false,true);}
    @Override public void render(DrawContext c,int x,int y,float delta){
        fit(x,y);var images=UiResources.images();var type=UiResources.text();images.beginFrame();type.beginFrame();c.getMatrices().push();
        try{c.getMatrices().translate(ox,oy,200);c.getMatrices().scale(scale,scale,1);
            sprite(c,"settings_panel",0,0,1000,730,1162,846);
            text(c,page==Page.HOME?"설정":page==Page.EFFECT?"스킬 이펙트":"시점 · 조작",500,85,34,true,true);
            sprite(c,"close",915,37,47,45,106,100);
            if(page==Page.HOME){String[] labels={"스킬 이펙트","시점 · 조작","키세팅"};String[] icons={"effect_icon","camera_icon","keyboard_icon"};int[] iw={146,135,143},ih={149,120,125};
                for(int i=0;i<3;i++){int yy=193+i*123;sprite(c,hit(155,yy,690,103)?"category_hover":"category",155,yy,690,103,665,hit(155,yy,690,103)?144:140);sprite(c,icons[i],191,yy+23,58,58,iw[i],ih[i]);text(c,labels[i],300,yy+52,28,false,false);}
            }else{
                if(page==Page.CAMERA){slider(c,"어깨 좌우 거리",0,camera.sideOffset,0,AimSettings.MAX_SIDE);slider(c,"카메라 높이",1,camera.height,-.5f,1.5f);slider(c,"카메라 거리",2,camera.distance,AimSettings.MIN_DISTANCE,AimSettings.MAX_DISTANCE);
                    sprite(c,camera.side<0?"checkbox_checked":"checkbox",145,491,35,35,camera.side<0?95:88,90);text(c,"반대쪽 어깨",198,509,23,false,false);
                    sprite(c,camera.smartCast?"toggle_on":"toggle_off",589,485,92,52,camera.smartCast?185:169,camera.smartCast?106:98);text(c,"스마트 시전",706,509,23,false,false);
                    text(c,"일반 마우스 조준 · F5의 1인칭과 정면 시점은 유지됩니다",500,566,20,false,true);
                }else{slider(c,"내 스킬 이펙트",0,own,0,1);slider(c,"다른 사람 스킬 이펙트",1,other,0,1);text(c,"화면에 보이는 효과만 조절합니다",500,449,23,false,true);text(c,"마나 · 피해 · 판정 · 지속시간은 바뀌지 않습니다",500,484,20,false,true);}
                text(c,notice,500,603,18,false,true);button(c,"초기화",125,640,205,false);button(c,"취소",397,640,205,false);button(c,"저장",670,640,205,true);
            }
        }finally{c.getMatrices().pop();images.endFrame();}
    }
    private void slider(DrawContext c,String label,int row,float value,float min,float max){int y=193+row*91;float t=Math.clamp((value-min)/(max-min),0,1);text(c,label,145,y,22,false,false);text(c,page==Page.EFFECT?Math.round(value*100)+"%":String.format(java.util.Locale.ROOT,"%.1f",value),848,y,23,false,true);
        sprite(c,"slider_track",145,y+22,708,30,469,51);c.enableScissor(145,y+22,Math.round(145+708*t),y+52);try{sprite(c,"slider_fill",145,y+22,708,30,469,56);}finally{c.disableScissor();}
        sprite(c,"slider_knob",Math.round(145+708*t)-19,y+18,38,38,87,89);text(c,page==Page.EFFECT?"0%":String.format(java.util.Locale.ROOT,"%.1f",min),145,y+67,16,false,true);text(c,page==Page.EFFECT?"100%":String.format(java.util.Locale.ROOT,"%.1f",max),853,y+67,16,false,true);
    }
    private void adjust(){float t=Math.clamp((float)(mx-145)/708,0,1);if(page==Page.EFFECT){if(dragging==0)own=t;else other=t;}else switch(dragging){case 0->camera.sideOffset=t*AimSettings.MAX_SIDE;case 1->camera.height=-.5f+t*2;case 2->camera.distance=AimSettings.MIN_DISTANCE+t*(AimSettings.MAX_DISTANCE-AimSettings.MIN_DISTANCE);}}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return true;fit(x,y);if(hit(915,37,47,45)){close();return true;}
        if(page==Page.HOME){for(int i=0;i<3;i++)if(hit(155,193+i*123,690,103)){client.setScreen(i==2?new KeySettingsScreen(this):new SettingsScreen(this,i==0?Page.EFFECT:Page.CAMERA));return true;}return true;}
        for(int i=0;i<(page==Page.CAMERA?3:2);i++)if(hit(126,209+i*91,745,44)){dragging=i;adjust();return true;}
        if(page==Page.CAMERA){if(hit(140,480,300,53))camera.side=-camera.side;if(hit(580,480,275,53))camera.smartCast=!camera.smartCast;}
        if(hit(125,640,205,56)){if(page==Page.CAMERA){camera.sideOffset=1.5f;camera.height=1;camera.distance=5;camera.side=1;camera.smartCast=false;camera.shoulder=true;}else{own=other=1;}}
        else if(hit(397,640,205,56))close();else if(hit(670,640,205,56))save();return true;
    }
    private void save(){try{if(page==Page.CAMERA){if(!camera.commit())throw new java.io.IOException("camera save failed");}else Class.forName("dev.portablevfx.client.api.VfxPreferences").getMethod("save",float.class,float.class).invoke(null,own,other);close();SharedNotifications.offer("settings:saved","system","설정을 저장했습니다","변경한 화면 설정이 적용되었습니다.");}catch(Exception|LinkageError error){notice="저장하지 못했습니다. 기존 설정은 유지됩니다.";}}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging>=0){fit(x,y);adjust();return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=-1;return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
