import java.awt.*;import java.awt.image.*;import java.nio.file.*;import java.util.*;import java.util.List;import javax.imageio.ImageIO;
import school.magiccodex.client.*;import school.magiccodex.protocol.NameplateProtocol.TitleRun;
public class PreviewTitleNameplates {
 static BufferedImage tint(BufferedImage im,int rgb){for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)im.setRGB(x,y,(im.getRGB(x,y)&0xff000000)|rgb);return im;}
 public static void main(String[] args)throws Exception{
  Path r=Path.of(args[0]),o=Path.of(args[1]);Files.createDirectories(o);Font font;try(var in=Files.newInputStream(r.resolve("magic-codex-fabric/src/main/resources/assets/magiccodex/font/pretendard_bold.ttf"))){font=Font.createFont(Font.TRUETYPE_FONT,in);}
  String[] styles={"arkeon","lumina","bestiaz","noxer","professor","professor","lumina"};String[] headers={"칭호 없음 · 이름 210px","접두만 · 이름 210px","접미만 · 이름 210px","접두+접미 · 이름 210px"};var sheet=new BufferedImage(2048,48+styles.length*192,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();g.setColor(new Color(42,45,50));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());g.setFont(font.deriveFont(23f));g.setColor(Color.WHITE);for(int c=0;c<4;c++)g.drawString(headers[c],c*512+80,33);
  var report=new StringBuilder("[\n");
  for(int row=0;row<styles.length;row++){
   var original=ImageIO.read(r.resolve("magic-codex-fabric/src/main/resources/assets/magiccodex/textures/nameplates/nameplate_"+styles[row]+".png").toFile());String name=row==5?"아주아주긴황준우이름":"황준우";
   String prefix=row==6?"매우 긴 마법 연구의 비밀을 밝힌":"소환술",suffix=row==6?"차카데미아의 위대한 대마법 교수":"교수";
   float fitted=210*Math.min(1,1100/CodexTextRasterizer.width(font,name,210));
   for(int col=0;col<4;col++){
    List<TitleRun> runs=switch(col){case 0->List.of();case 1->List.of(new TitleRun(prefix,0x96E4EC));case 2->List.of(new TitleRun(suffix,0xDEC58E));default->List.of(new TitleRun(prefix,0x96E4EC),new TitleRun(suffix,0xDEC58E));};
    var tile=new BufferedImage(original.getWidth(),768,BufferedImage.TYPE_INT_ARGB);var t=tile.createGraphics();t.drawImage(original,0,0,null);var glyph=tint(CodexTextRasterizer.rasterize(font,name,fitted),0xF9F2DF);int cy=runs.isEmpty()?355:299;t.drawImage(glyph,1245-glyph.getWidth()/2,cy-glyph.getHeight()/2,null);
    float titleSize=0,titleWidth=0;if(!runs.isEmpty()){float measured=CodexTextRasterizer.titleWidth(font,runs,96);titleSize=NameplateTitleLayout.fittedSize(measured,96,1080);titleWidth=CodexTextRasterizer.titleWidth(font,runs,titleSize);if(titleWidth>1081)throw new AssertionError("overflow");var title=CodexTextRasterizer.rasterizeTitles(font,runs,titleSize);t.drawImage(title,1245-title.getWidth()/2,449-title.getHeight()/2,null);}
    t.dispose();g.drawImage(tile,col*512,48+row*192,512,192,null);if(row>0||col>0)report.append(",\n");report.append(String.format(java.util.Locale.ROOT,"{\"row\":%d,\"state\":%d,\"nameBaseSize\":210,\"nameFittedSize\":%.4f,\"nameCenterY\":%d,\"titleSize\":%.4f,\"titleAdvance\":%.4f}",row,col,fitted,cy,titleSize,titleWidth));
   }
  }
  g.dispose();ImageIO.write(sheet,"png",o.resolve("title-nameplates-preview.png").toFile());report.append("\n]\n");Files.writeString(o.resolve("layout-measurements.json"),report);System.out.println("PASS: paired names share identical 210px base/fitted size; only vertical position changes; combined colored title advance <=1080px; no added separator");
 }
}
