package kr.chacademy.portrait.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PortraitResultTest {

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void validatesTransparencyAndSize() throws Exception {
        BufferedImage clear = new BufferedImage(64, 96, BufferedImage.TYPE_INT_ARGB);
        clear.setRGB(32, 48, 0xFFFFFFFF);
        assertNull(PortraitService.validateResult(png(clear), true));
        BufferedImage opaque = new BufferedImage(64, 96, BufferedImage.TYPE_INT_RGB);
        assertNotNull(PortraitService.validateResult(png(opaque), true));
        assertNull(PortraitService.validateResult(png(opaque), false));
        BufferedImage huge = new BufferedImage(2100, 10, BufferedImage.TYPE_INT_ARGB);
        assertNotNull(PortraitService.validateResult(png(huge), false));
        assertNotNull(PortraitService.validateResult("not a png at all, definitely not".repeat(4).getBytes(), false));
        assertEquals(64, PortraitService.sha256(new byte[]{1}).length());
    }
}
