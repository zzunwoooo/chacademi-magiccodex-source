package school.magiccodex.client;

/** Shared image coordinates keep click targets aligned at every GUI scale. */
public record StatsLayout(float x, float y, float scale) {
    public static final int WIDTH=1448, HEIGHT=1086;
    public static final CodexHitboxes.Rect CLOSE=new CodexHitboxes.Rect(1294,78,70,68);
    public static final CodexHitboxes.Rect FRIEND=new CodexHitboxes.Rect(187,679,56,56);
    public static final CodexHitboxes.Rect POPULARITY=new CodexHitboxes.Rect(510,679,56,56);
    public static boolean socialContains(CodexHitboxes.Rect r,double x,double y){
        double dx=x-r.centerX(),dy=y-r.centerY(),radius=r.width()/2.0;
        return dx*dx+dy*dy<=radius*radius;
    }
    public static StatsLayout fit(int width,int height) {
        if(width<=0 || height<=0)throw new IllegalArgumentException("Invalid viewport");
        float s=Math.min(width*.80f/WIDTH,height*.82f/HEIGHT);
        return new StatsLayout((width-WIDTH*s)/2,(height-HEIGHT*s)/2,s);
    }
    public double localX(double value){return (value-x)/scale;}
    public double localY(double value){return (value-y)/scale;}
}
