package school.magiccodex.client;

public record PlayerHudLayout(float scale,float width,float height) {
    public static final float SIZE = 1.35f;
    public static PlayerHudLayout of(int width,int height){
        // Keep the corner HUD and inventory dock readable independently of Minecraft GUI scale.
        float scale=Math.max(.01f,SIZE*Math.min(width/1672f,height/941f));
        return new PlayerHudLayout(scale,width/scale,height/scale);
    }
    public static final int CELL = 44;
    public float hotbarLeft(){return width/2-238;}
    public float hotbarTop(){return height-68;}
    public static final int FRAME_SIZE = 180;
    public static final float STAR_RADIUS = 80;
    public static final int VITAL_WIDTH = 190;
    public float frameX(){return 96;}
    public float frameY(){return height-100;}
    public float vitalX(){return 180;}
    public float healthY(){return height-91;}
    public float manaY(){return height-45;}
    public float slotX(int slot){return hotbarLeft()+56+slot*47;}
    public static float fraction(float value,float max){
        return !Float.isFinite(value)||!Float.isFinite(max)||max<=0?0:Math.clamp(value/max,0,1);
    }
}
