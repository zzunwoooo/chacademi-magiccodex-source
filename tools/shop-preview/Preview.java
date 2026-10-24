package school.magiccodex.client;
import java.awt.*;import java.awt.geom.*;import java.awt.image.BufferedImage;import java.io.File;import java.util.*;import java.util.List;import javax.imageio.ImageIO;
public class Preview {
 static String RES, FONTS, OUT, ICONS, PORTRAIT;
 static Map<String,BufferedImage> cache=new HashMap<>();
 static BufferedImage img(String p){return cache.computeIfAbsent(p,k->{try{return ImageIO.read(new File(k));}catch(Exception e){throw new RuntimeException(k,e);}});}
 static Font font(String f,float size){try{return Font.createFont(Font.TRUETYPE_FONT,new File(FONTS+f)).deriveFont(size);}catch(Exception e){throw new RuntimeException(e);}}
 static Map<String,Font> fonts=new HashMap<>();
 static Font face(ShopView.Face f,float size){String n=switch(f){case TITLE,SECTION->"maruburi_bold.ttf";case BODY->"pretendard_regular.ttf";case LABEL->"pretendard_medium.ttf";case BOLD->"pretendard_bold.ttf";};return fonts.computeIfAbsent(n,k->font(k,10)).deriveFont(size);}
 static Color col(int argb){return new Color((argb>>16)&255,(argb>>8)&255,argb&255,(argb>>>24)&255);}
 public static void main(String[] a)throws Exception{RES=a[0];FONTS=a[1];ICONS=a[2];PORTRAIT=a[3];OUT=a[4];int scale=2;
  var rows=new ArrayList<ShopView.Row>();String[][] d={{"마나 물약","250","120"},{"체력 물약","200",""},{"낡은 마법서","1200",""},{"귀환권","300",""},{"사과","4","1"},{"빵","20",""},{"마력 코어","800",""},{"약초","40","8"},{"빈 병","10",""},{"깃펜","60",""},{"낚싯줄","150",""},{"랜턴","350",""}};
  for(int i=0;i<d.length;i++)rows.add(new ShopView.Row("p"+i,d[i][0],i==0?List.of("마력을 회복하는 물약."):List.of(),d[i][1],d[i][2],i==7?12:0,true,i!=1,i));
  for(int i=12;i<90;i++)rows.add(new ShopView.Row("p"+i,"상품 "+i,List.of(),"100","",0,true,false,i%12));
  boolean sortOpen=a.length>5;
  var m=new ShopView.Model("에드윈의 잡화점","상점 주인","에드윈",true,1024f/1536f,"988100",ShopView.Tab.ALL,new boolean[]{true,true,true},rows,0,"p0",false,"",64,"750",true,true,true,true,ShopView.Sort.PRICE_LOW,sortOpen,false,"",0,ShopView.cardX(1)+40,ShopView.cardY(1)+60,false,941);
  var im=new BufferedImage(ShopView.W*scale,ShopView.H*scale,BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();g.scale(scale,scale);
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  // blurred world stand-in: the reference background, darkened
  var bg=img(ICONS+"/../bg.png");g.drawImage(bg,0,0,ShopView.W,ShopView.H,null);g.setColor(new Color(10,16,28,120));g.fillRect(0,0,ShopView.W,ShopView.H);
  ShopView.Canvas c=new ShopView.Canvas(){
   public void image(String t,float x,float y,float w,float h,int u,int v,int sw,int sh,int tw,int th,int tint){var src=img(RES+t);var sub=src.getSubimage(u,v,sw,sh);
    if(tint!=0xFFFFFFFF){var tmp=new BufferedImage(sw,sh,BufferedImage.TYPE_INT_ARGB);float k=((tint>>16)&255)/255f;for(int yy=0;yy<sh;yy++)for(int xx=0;xx<sw;xx++){int p=sub.getRGB(xx,yy);int r=(int)(((p>>16)&255)*k),gg=(int)(((p>>8)&255)*k),b=(int)((p&255)*k);tmp.setRGB(xx,yy,(p&0xFF000000)|(r<<16)|(gg<<8)|b);}sub=tmp;}
    g.drawImage(sub,Math.round(x),Math.round(y),Math.round(w),Math.round(h),null);}
   public void text(String s,float x,float cy,float size,int color,ShopView.Face f,boolean centered){var font=face(f,size);g.setFont(font);var fm=g.getFontMetrics();float w=fm.stringWidth(s);float bx=centered?x-w/2:x;float by=cy+(fm.getAscent()-fm.getDescent())/2f;g.setColor(col(color));g.drawString(s,bx,by);}
   public float width(String s,float size,ShopView.Face f){g.setFont(face(f,size));return g.getFontMetrics().stringWidth(s);}
   public void fill(float x,float y,float w,float h,int color){g.setColor(col(color));g.fill(new Rectangle2D.Float(x,y,w,h));}
   public void line(float ax,float ay,float bx,float by,float t,int color){g.setColor(col(color));g.setStroke(new BasicStroke(t,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(new Line2D.Float(ax,ay,bx,by));}
   public void diamond(float x,float y,float rx,float r,int color){var p=new Path2D.Float();p.moveTo(x,y-r);p.lineTo(x-rx,y);p.lineTo(x,y+r);p.lineTo(x+rx,y);p.closePath();g.setColor(col(color));g.fill(p);}
   public void item(ShopView.Row r,float x,float y,float size){if(r.icon()==null)return;g.drawImage(img(ICONS+"/"+r.icon()+".png"),Math.round(x),Math.round(y),Math.round(size),Math.round(size),null);}
   public void portrait(float x,float y,float w,float h){g.drawImage(img(PORTRAIT),Math.round(x),Math.round(y),Math.round(w),Math.round(h),null);}
   public void searchField(float x,float y,float w,float h){text("아이템 검색",x+12,y+h/2,20,ShopView.MUTED,ShopView.Face.LABEL,false);}
   public void quantityField(float x,float y,float w,float h){text("3",x+w/2,y+h/2,27,ShopView.WHITE,ShopView.Face.BOLD,true);}
   public void pushLayer(float z){}public void popLayer(){}
  };
  ShopView.draw(c,m);ImageIO.write(im,"png",new File(OUT));
 }
}
