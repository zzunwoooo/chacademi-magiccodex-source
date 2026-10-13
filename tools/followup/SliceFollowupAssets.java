import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
public class SliceFollowupAssets {
 static Path source=Path.of("C:/Chacademi/staging/aim-straight-20261013/png-assets-verified");
 static Path target=Path.of("C:/Chacademi/staging/aim-straight-20261013/source/magic-codex-fabric/src/main/resources/assets/magiccodex/textures/gui/followup");
 static StringBuilder manifest=new StringBuilder("# Approved UI asset slices\n\nOriginal ZIP SHA256: `2d7905ecce9b04f7cc1df84a2938474258bf723f1aa9438dd1270e275c256f0e`\n\nAll coordinates are original pixels. Inventory rectangle corner masks remove its colored atlas backdrop; no text or item icons are baked.\n\n|File|Dimensions|Alpha 0 / partial / 255|SHA256|\n|---|---|---|---|\n");
 static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 static void describe(Path p,BufferedImage image)throws Exception{
  long zero=0,partial=0,full=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int a=image.getRGB(x,y)>>>24;if(a==0)zero++;else if(a==255)full++;else partial++;}
  manifest.append("|").append(p.getFileName()).append("|").append(image.getWidth()).append("×").append(image.getHeight()).append("|").append(zero).append(" / ").append(partial).append(" / ").append(full).append("|").append(hash(p)).append("|\n");
 }
 static void crop(String input,String name,int x,int y,int w,int h,int radius)throws Exception{
  BufferedImage original=ImageIO.read(source.resolve(input+".png").toFile());
  BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  if(radius>0){g.setColor(Color.WHITE);g.fill(new RoundRectangle2D.Float(0,0,w,h,radius*2,radius*2));g.setComposite(AlphaComposite.SrcIn);}
  g.drawImage(original,0,0,w,h,x,y,x+w,y+h,null);g.dispose();
  if(name.equals("flight_arch")){for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){double n=(xx-w/2.0)/(w/2.0);if(yy>145+40*n*n)result.setRGB(xx,yy,0);}}
  Path file=target.resolve(name+".png");ImageIO.write(result,"png",file.toFile());describe(file,result);
 }
 public static void main(String[] args)throws Exception{
  Files.createDirectories(target.resolve("originals"));
  for(String name:new String[]{"inventory_atlas","keys_atlas","camera_atlas","menu_atlas","broom_atlas","settings_icon"}){
   Path p=source.resolve(name+".png"),out=target.resolve("originals").resolve(p.getFileName());Files.copy(p,out,StandardCopyOption.REPLACE_EXISTING);describe(out,ImageIO.read(out.toFile()));
  }
  crop("inventory_atlas","inventory_slot",96,89,290,268,43);
  crop("inventory_atlas","inventory_slot_hover",450,89,292,268,43);
  crop("inventory_atlas","inventory_small",99,420,190,183,32);
  crop("inventory_atlas","inventory_small_hover",351,420,192,183,32);
  crop("keys_atlas","key",51,380,100,96,0);crop("keys_atlas","key_hover",40,475,118,103,0);crop("keys_atlas","key_blocked",51,575,100,91,0);
  crop("keys_atlas","keyboard_panel",40,10,1595,349,0);crop("keys_atlas","mouse",916,373,280,497,0);
  crop("menu_atlas","menu_panel",59,25,866,856,0);
  crop("menu_atlas","category",949,106,665,140,0);crop("menu_atlas","category_hover",949,251,665,144,0);
  crop("menu_atlas","effect_icon",957,587,146,149,0);crop("menu_atlas","camera_icon",1103,604,135,120,0);crop("menu_atlas","keyboard_icon",1258,600,143,125,0);
  crop("camera_atlas","settings_panel",50,11,1162,846,0);
  crop("camera_atlas","slider_track",1255,76,469,51,0);crop("camera_atlas","slider_fill",1255,157,469,56,0);crop("camera_atlas","slider_knob",1282,239,87,89,0);
  crop("camera_atlas","checkbox",1280,346,88,90,0);crop("camera_atlas","checkbox_checked",1408,346,95,90,0);
  crop("camera_atlas","toggle_off",1261,449,169,98,0);crop("camera_atlas","toggle_on",1463,442,185,106,0);
  crop("camera_atlas","button",1253,549,337,96,0);crop("camera_atlas","button_hover",1253,639,337,96,0);crop("camera_atlas","button_primary",1253,731,337,105,0);crop("camera_atlas","close",1612,622,106,100,0);
  crop("broom_atlas","flight_arch",119,53,906,185,0);crop("broom_atlas","flight_arch_fill",110,239,918,222,0);
  crop("broom_atlas","flight_ring",1069,9,333,337,0);crop("broom_atlas","flight_ring_fill",1100,328,264,250,0);
  crop("broom_atlas","flight_feather",1440,91,243,276,0);
  crop("broom_atlas","catch_key",156,553,302,302,0);crop("broom_atlas","catch_key_current",489,531,376,351,0);crop("broom_atlas","catch_key_done",873,536,383,350,0);crop("broom_atlas","catch_key_wrong",1266,529,385,351,0);
  crop("settings_icon","settings_icon",303,280,676,650,0);
  Path doc=target.getParent().getParent().getParent().getParent().getParent().getParent().getParent().getParent();
  Files.writeString(Path.of("C:/Chacademi/staging/aim-straight-20261013/source/docs/handoff/CODEX_FOLLOWUP_ASSET_SLICES_20261013.md"),manifest.toString());
  System.out.println("Produced approved slices and alpha/SHA manifest: "+target);
 }
}
