package school.magiccodex.client;

import java.util.function.IntBinaryOperator;

/** Shared 1600x900 dialogue coordinates. Only the body below the panel is clipped. */
final class PlayerTurnPortraitLayout {
    static final int TOP=75, PANEL_TOP=639;
    private static final float CENTER_X=415, BODY_HEIGHT=660, MAX_WIDTH=700;
    record Bounds(int x,int y,int width,int height) {}
    record Placement(int x,int y,int width) {}

    /** Run once on Portrait-IO, never scan pixels during a frame or GPU upload. */
    static Bounds bounds(int width,int height,IntBinaryOperator alpha) {
        int left=width,top=height,right=-1,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(alpha.applyAsInt(x,y)>0){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        return right<left?new Bounds(0,0,width,height):new Bounds(left,top,right-left+1,bottom-top+1);
    }

    static Placement place(int imageWidth,int imageHeight,Bounds b) {
        float scale=Math.min(BODY_HEIGHT/b.height(),MAX_WIDTH/b.width());
        int width=Math.max(1,Math.round(imageWidth*scale));
        // Use the actual rounded draw dimensions, including aspect-ratio rounding.
        float sx=width/(float)imageWidth;
        float sy=Math.round(width*(float)imageHeight/imageWidth)/(float)imageHeight;
        return new Placement(Math.round(CENTER_X-(b.x()+b.width()/2f)*sx),
                (int)Math.ceil(TOP-b.y()*sy),width);
    }
}
