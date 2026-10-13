package school.magiccodex.client;
/** Full 1024x1536 PNG coordinates: draw and hit tests share the exact same transform. */
public final class MouseBindingLayout {
 public static final float X=1285,Y=225,W=160,H=240,S=W/1024f;
 private static final double[][] ONE={{244,645},{260,632},{279,642},{357,764},{369,807},{361,826},{319,829},{302,815},{251,719}};
 private static final double[][] TWO={{329,862},{338,848},{369,844},{390,853},{405,922},{416,989},{409,1018},{396,1030},{372,1023},{353,1005},{335,932}};
 public static double[][] polygon(int code){return code==KeySettingsLayout.THUMB_1?ONE:TWO;}
 public static int hit(double x,double y){double px=(x-X)/S,py=(y-Y)/S;return inside(ONE,px,py)?KeySettingsLayout.THUMB_1:inside(TWO,px,py)?KeySettingsLayout.THUMB_2:-1;}
 private static boolean inside(double[][] p,double x,double y){boolean hit=false;for(int i=0,j=p.length-1;i<p.length;j=i++){if((p[i][1]>y)!=(p[j][1]>y)&&x<(p[j][0]-p[i][0])*(y-p[i][1])/(p[j][1]-p[i][1])+p[i][0])hit=!hit;}return hit;}
}
