import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import school.magiccodex.client.CodexTextRasterizer;
public class NameplateAssetAudit {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
  Font font;try(var in=Files.newInputStream(root.resolve("magic-codex-fabric/src/main/resources/assets/magiccodex/font/pretendard_bold.ttf"))){font=Font.createFont(Font.TRUETYPE_FONT,in);}
  String[] styles={"arkeon","lumina","bestiaz","noxer","professor"};var sheet=new BufferedImage(1024,960,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();g.setColor(new Color(42,45,50));g.fillRect(0,0,1024,960);
  StringBuilder json=new StringBuilder("[\n");
  for(int i=0;i<5;i++){
   Path p=root.resolve("magic-codex-fabric/src/main/resources/assets/magiccodex/textures/nameplates/nameplate_"+styles[i]+".png");var im=ImageIO.read(p.toFile());int min=255,max=0,safe=255,transparent=0;int left=im.getWidth(),top=im.getHeight(),right=0,bottom=0;
   for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){int a=im.getRGB(x,y)>>>24;min=Math.min(min,a);max=Math.max(max,a);if(a==0)transparent++;if(a>10){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}if(x>=660&&x<=1830&&y>=205&&y<=505)safe=Math.min(safe,a);}
   if(im.getWidth()!=(i==4?2046:2048)||im.getHeight()!=768||min!=0||max<250||safe<250)throw new AssertionError("Unexpected original geometry/alpha "+p+" safe="+safe);
   String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
   if(i>0)json.append(",\n");json.append("{\"style\":\"").append(styles[i]).append("\",\"width\":").append(im.getWidth()).append(",\"height\":768,\"alphaMin\":").append(min).append(",\"alphaMax\":").append(max).append(",\"safeAlphaMin\":").append(safe).append(",\"transparentPixels\":").append(transparent).append(",\"alphaBounds\":[").append(left).append(',').append(top).append(',').append(right).append(',').append(bottom).append("],\"sha256\":\"").append(sha).append("\"}");
   for(int col=0;col<2;col++){
    var tile=new BufferedImage(im.getWidth(),768,BufferedImage.TYPE_INT_ARGB);var t=tile.createGraphics();t.drawImage(im,0,0,null);
    String name=col==0?(i==4?"엘레나교수":"차카데미"):"아주긴마법학교교수이름";
    float size=i==4?210:250;size*=Math.min(1,1100/CodexTextRasterizer.width(font,name,size));var glyph=CodexTextRasterizer.rasterize(font,name,size);
    int cx=1245,cy=i==4?299:344;t.setComposite(AlphaComposite.SrcOver);t.drawImage(tint(glyph,new Color(249,242,223)),cx-glyph.getWidth()/2,cy-glyph.getHeight()/2,null);
    if(i==4){var title=CodexTextRasterizer.rasterize(font,"변신술 교수",96);t.drawImage(tint(title,new Color(240,215,160)),cx-title.getWidth()/2,449-title.getHeight()/2,null);}
    t.dispose();g.drawImage(tile,col*512,i*192,512,192,null);
   }
  }
  g.dispose();json.append("\n]\n");Files.writeString(out.resolve("asset-audit.json"),json);ImageIO.write(sheet,"png",out.resolve("layout-preview.png").toFile());System.out.println(json);
 }
 static BufferedImage tint(BufferedImage im,Color c){for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)im.setRGB(x,y,(im.getRGB(x,y)&0xff000000)|(c.getRGB()&0xffffff));return im;}
}
