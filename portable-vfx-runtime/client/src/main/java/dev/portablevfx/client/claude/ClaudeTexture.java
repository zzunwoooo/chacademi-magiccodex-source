package dev.portablevfx.client.claude;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** PNG-only CPU decoder. Bounds are checked before ImageIO can allocate or inflate image data. */
final class ClaudeTexture {
    static final int MAX_DIMENSION = 2048;
    static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
    static final int MAX_ASSET_PNG_BYTES = 32 * 1024 * 1024;
    static final int MAX_ASSET_RGBA_BYTES = 64 * 1024 * 1024;
    static final long MAX_BACKEND_RGBA_BYTES = 1024L * 1024 * 1024;
    private static final long SIGNATURE = 0x89504e470d0a1a0aL;
    private static final int IHDR = 0x49484452, PLTE = 0x504c5445, TRNS = 0x74524e53;
    private static final int IDAT = 0x49444154, IEND = 0x49454e44;

    record Decoded(int width, int height, byte[] rgba) { }
    private ClaudeTexture() { }

    static Decoded decode(byte[] png, long remainingRgbaBytes) throws IOException {
        if (png == null || png.length < 45 || png.length > MAX_PNG_BYTES)
            throw invalid("PNG must be within the 4 MiB encoded byte budget");
        ByteBuffer header = ByteBuffer.wrap(png);
        if (header.getLong() != SIGNATURE || header.getInt() != 13 || header.getInt() != IHDR)
            throw invalid("PNG requires its signature and first IHDR chunk");
        int width = header.getInt(), height = header.getInt();
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION)
            throw invalid("PNG dimensions must be 1..2048");
        int rgbaBytes = width * height * 4;
        if (rgbaBytes > remainingRgbaBytes) throw invalid("Decoded PNG texture byte budget exceeded");
        // Ancillary text/profile chunks can themselves contain compressed data. Never give those
        // chunks to ImageIO; only the five image-essential chunk types reach the decoder.
        byte[] imageOnly = imageChunks(png);
        var readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) throw invalid("PNG decoder unavailable");
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(imageOnly))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) != width || reader.getHeight(0) != height)
                throw invalid("PNG decoded dimensions disagree with IHDR");
            BufferedImage image = reader.read(0);
            if (image == null || image.getWidth() != width || image.getHeight() != height)
                throw invalid("PNG decoder returned invalid image dimensions");
            byte[] rgba = new byte[rgbaBytes];
            int[] row = new int[width];
            // Interchange UVs use bottom-left origin; PNG scanlines are top-to-bottom.
            for (int y = 0, output = 0; y < height; y++) {
                image.getRGB(0, height - 1 - y, width, 1, row, 0, width);
                for (int argb : row) {
                    rgba[output++] = (byte) (argb >>> 16);
                    rgba[output++] = (byte) (argb >>> 8);
                    rgba[output++] = (byte) argb;
                    rgba[output++] = (byte) (argb >>> 24);
                }
            }
            return new Decoded(width, height, rgba);
        } catch (RuntimeException e) {
            throw new IOException("Invalid PNG texture: " + e.getMessage(), e);
        } finally { reader.dispose(); }
    }

    private static byte[] imageChunks(byte[] png) throws IOException {
        var stripped = new ByteArrayOutputStream(png.length);
        stripped.write(png, 0, 8);
        int offset = 8;
        boolean sawData = false, endedData = false, sawPalette = false, sawTransparency = false;
        while (offset <= png.length - 12) {
            var chunk = ByteBuffer.wrap(png, offset, png.length - offset);
            int length = chunk.getInt(), type = chunk.getInt();
            if (length < 0 || length > png.length - offset - 12) throw invalid("Truncated PNG chunk");
            CRC32 crc = new CRC32(); crc.update(png, offset + 4, length + 4);
            if (crc.getValue() != Integer.toUnsignedLong(chunk.getInt(offset + length + 8)))
                throw invalid("PNG chunk CRC mismatch");
            if (type == IHDR && (offset != 8 || length != 13)) throw invalid("Duplicate or malformed PNG IHDR");
            if (type == 0x6163544c || type == 0x6663544c || type == 0x66644154)
                throw invalid("Animated PNG textures are unsupported");
            if (type == PLTE) {
                if (sawPalette || sawData || length == 0 || length > 768 || length % 3 != 0) throw invalid("Invalid PNG palette");
                sawPalette = true;
            }
            if (type == TRNS) {
                if (sawTransparency || sawData || length > 256) throw invalid("Invalid PNG transparency");
                sawTransparency = true;
            }
            if (type == IDAT) {
                if (endedData) throw invalid("PNG IDAT chunks must be consecutive");
                sawData = true;
            } else if (sawData) endedData = true;
            boolean essential = type == IHDR || type == PLTE || type == TRNS || type == IDAT || type == IEND;
            if (!essential && (type & 0x20000000) == 0) throw invalid("Unknown critical PNG chunk");
            if (essential) stripped.write(png, offset, length + 12);
            offset += length + 12;
            if (type == IEND) {
                if (length != 0 || !sawData || offset != png.length) throw invalid("Malformed PNG end or trailing data");
                return stripped.toByteArray();
            }
        }
        throw invalid("PNG missing complete IEND chunk");
    }
    private static IOException invalid(String message) { return new IOException(message); }
}
