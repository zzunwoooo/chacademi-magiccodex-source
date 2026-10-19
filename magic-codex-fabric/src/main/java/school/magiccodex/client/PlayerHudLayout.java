package school.magiccodex.client;

public record PlayerHudLayout(float scale,float width,float height,float dockLift,float portraitLift) {
    public static final float SIZE = 1.35f;
    public PlayerHudLayout(float scale,float width,float height){this(scale,width,height,0,0);}
    /** Only the portrait/vitals and inventory dock use this; menus and other HUDs keep of(). */
    public static PlayerHudLayout player(int width,int height,int percent){
        var base=of(width,height);percent=Math.clamp(percent,0,100);
        if(percent==0||percent==50)return base;
        float scale=base.scale()*percent/50f,w=width/scale,h=height/scale;
        float skillScale=Math.min(width/1672f,height/941f),lift=0;
        if(percent>50&&(w/2+238)*scale>width-402*skillScale)
            lift=Math.max(0,(76*skillScale-8*scale+8*base.scale())/scale);
        float portrait=percent>50&&370+8>w/2-238?129+lift:0;
        // Keep the portrait below the unchanged top-left menu. Preserve 50% coordinates exactly.
        portrait=Math.min(portrait,Math.max(0,h-190-43*base.scale()/scale));
        return new PlayerHudLayout(scale,w,h,lift,portrait);
    }

    public static PlayerHudLayout of(int width,int height){
        // Keep the corner HUD and inventory dock readable independently of Minecraft GUI scale.
        float scale=Math.max(.01f,SIZE*Math.min(width/1672f,height/941f));
        return new PlayerHudLayout(scale,width/scale,height/scale);
    }
    public static final int CELL = 44;
    public float hotbarLeft(){return width/2-238;}
    public float hotbarTop(){return height-68-dockLift;}
    public static final int FRAME_SIZE = 180;
    public static final float STAR_RADIUS = 80;
    public static final int VITAL_WIDTH = 190;
    public float frameX(){return 96;}
    public float frameY(){return height-100-portraitLift;}
    public float vitalX(){return 180;}
    public float healthY(){return height-91-portraitLift;}
    public float manaY(){return height-45-portraitLift;}
    public float slotX(int slot){return hotbarLeft()+56+slot*47;}
    public static float fraction(float value,float max){
        return !Float.isFinite(value)||!Float.isFinite(max)||max<=0?0:Math.clamp(value/max,0,1);
    }
}
