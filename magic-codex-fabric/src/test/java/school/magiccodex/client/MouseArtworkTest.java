package school.magiccodex.client;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import javax.imageio.ImageIO;
class MouseArtworkTest {
 @Test void approvedPngAndThumbHitShareCoordinates()throws Exception{var image=ImageIO.read(getClass().getResourceAsStream("/assets/magiccodex/textures/gui/keybinding_mouse_v2.png"));assertEquals(1024,image.getWidth());assertEquals(1536,image.getHeight());assertEquals(KeySettingsLayout.THUMB_1,hit(300,740));assertEquals(KeySettingsLayout.THUMB_2,hit(375,940));assertEquals(-1,hit(600,450));assertEquals(-1,hit(20,20));assertTrue((image.getRGB(300,740)>>>24)>32);assertTrue((image.getRGB(375,940)>>>24)>32);}
 private int hit(double x,double y){return MouseBindingLayout.hit(MouseBindingLayout.X+x*MouseBindingLayout.S,MouseBindingLayout.Y+y*MouseBindingLayout.S);}
 @Test void keyboardAndSlotsRemainDisjointFromMouse(){for(var key:KeySettingsLayout.KEYS)assertTrue(key.box().x()+key.box().width()<MouseBindingLayout.X);for(int i=0;i<6;i++)assertTrue(KeySettingsLayout.slot(i).y()>MouseBindingLayout.Y+MouseBindingLayout.H);}
}
