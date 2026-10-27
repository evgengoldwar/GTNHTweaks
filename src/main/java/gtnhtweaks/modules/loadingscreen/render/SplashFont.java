package gtnhtweaks.modules.loadingscreen.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;

import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;

/**
 * Minimal Minecraft-style font renderer that works in any GL context (main thread, Forge splash thread,
 * BetterLoadingScreen thread). Uses the vanilla ascii.png for ASCII and the vanilla unicode pages for everything else,
 * read straight from the Minecraft jar, since resource packs are not loaded yet while the game starts.
 */
final class SplashFont {

    static final int HEIGHT = 8;

    private static int asciiTexture = -1;
    private static boolean asciiFailed;
    private static int asciiCell = 8;
    private static int asciiSize = 128;
    private static final int[] asciiWidths = new int[128];

    private static byte[] glyphSizes;
    private static final int[] unicodeTextures = new int[256];
    private static final boolean[] unicodeFailed = new boolean[256];

    static {
        Arrays.fill(unicodeTextures, -1);
    }

    private SplashFont() {}

    static synchronized float width(String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) width += charWidth(text.charAt(i));
        return width;
    }

    /** Draws text with its top left corner at (x, y), scaled by {@code scale}. */
    static synchronized void draw(String text, float x, float y, float scale, int argb) {
        if (text.isEmpty()) return;
        glEnable(GL_TEXTURE_2D);
        GLDraw.color(argb);
        float pos = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') drawChar(c, pos, y, scale);
            pos += charWidth(c) * scale;
        }
        glDisable(GL_TEXTURE_2D);
    }

    private static boolean isAscii(char c) {
        return c >= 32 && c < 127;
    }

    private static float charWidth(char c) {
        if (c == ' ') return 4;
        if (isAscii(c) && loadAscii()) return asciiWidths[c];
        int size = glyphSize(c);
        if (size == 0) return 0;
        int start = size >>> 4;
        int end = (size & 15) + 1;
        return (end - start) / 2F + 1;
    }

    private static void drawChar(char c, float x, float y, float scale) {
        if (isAscii(c) && loadAscii()) {
            float width = asciiWidths[c] - 1;
            float texWidth = width * asciiCell / 8F;
            float u = (c % 16) * asciiCell;
            float v = (c / 16) * asciiCell;
            glBindTexture(GL_TEXTURE_2D, asciiTexture);
            quad(x, y, width * scale, (HEIGHT - 0.01F) * scale, u, v, texWidth, asciiCell - 0.01F, asciiSize);
            return;
        }
        int size = glyphSize(c);
        int texture = size == 0 ? -1 : unicodeTexture(c >> 8);
        if (texture < 0) return;
        int start = size >>> 4;
        int end = (size & 15) + 1;
        float texWidth = end - start - 0.02F;
        float u = (c % 16) * 16 + start;
        float v = ((c & 255) / 16) * 16;
        glBindTexture(GL_TEXTURE_2D, texture);
        quad(x, y, texWidth / 2 * scale, (HEIGHT - 0.01F) * scale, u, v, texWidth, 15.98F, 256);
    }

    private static void quad(float x, float y, float w, float h, float u, float v, float tw, float th, float size) {
        glBegin(GL_QUADS);
        glTexCoord2f(u / size, v / size);
        glVertex2f(x, y);
        glTexCoord2f(u / size, (v + th) / size);
        glVertex2f(x, y + h);
        glTexCoord2f((u + tw) / size, (v + th) / size);
        glVertex2f(x + w, y + h);
        glTexCoord2f((u + tw) / size, v / size);
        glVertex2f(x + w, y);
        glEnd();
    }

    private static boolean loadAscii() {
        if (asciiTexture >= 0) return true;
        if (asciiFailed) return false;
        try {
            BufferedImage image = readImage("textures/font/ascii.png");
            asciiSize = image.getWidth();
            asciiCell = asciiSize / 16;
            float scale = 8F / asciiCell;
            for (int c = 0; c < 128; c++) {
                int column = asciiCell - 1;
                for (; column >= 0; column--) {
                    if (columnHasPixels(image, c, column)) break;
                }
                asciiWidths[c] = (int) (0.5 + (column + 1) * scale) + 1;
            }
            asciiTexture = upload(image);
            return true;
        } catch (Throwable t) {
            asciiFailed = true;
            GTNHTweaks.LOG.error("Could not load the loading screen font", t);
            return false;
        }
    }

    private static boolean columnHasPixels(BufferedImage image, int c, int column) {
        int x = (c % 16) * asciiCell + column;
        int y0 = (c / 16) * asciiCell;
        for (int y = y0; y < y0 + asciiCell; y++) {
            if ((image.getRGB(x, y) >>> 24) != 0) return true;
        }
        return false;
    }

    private static int glyphSize(char c) {
        if (glyphSizes == null) {
            try (InputStream in = open("font/glyph_sizes.bin")) {
                glyphSizes = IOUtils.toByteArray(in);
            } catch (Throwable t) {
                GTNHTweaks.LOG.error("Could not load glyph_sizes.bin", t);
                glyphSizes = new byte[0];
            }
        }
        return c < glyphSizes.length ? glyphSizes[c] & 0xFF : 0;
    }

    private static int unicodeTexture(int page) {
        if (unicodeTextures[page] >= 0) return unicodeTextures[page];
        if (unicodeFailed[page]) return -1;
        try {
            unicodeTextures[page] = upload(readImage(String.format("textures/font/unicode_page_%02x.png", page)));
        } catch (Throwable t) {
            unicodeFailed[page] = true;
            GTNHTweaks.LOG.warn("Could not load unicode font page " + page, t);
        }
        return unicodeTextures[page];
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = SplashFont.class.getResourceAsStream("/assets/minecraft/" + path);
        if (in == null) throw new FileNotFoundException(path);
        return in;
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream in = open(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Unreadable image " + path);
            return image;
        }
    }

    private static int upload(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        pixels.put(image.getRGB(0, 0, width, height, null, 0, width));
        pixels.flip();

        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                width,
                height,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                pixels);
            return texture;
        } finally {
            glPopClientAttrib();
        }
    }

    /** Frees the textures. Must be called with a GL context current, once nothing draws the loading screen anymore. */
    static synchronized void dispose() {
        if (asciiTexture >= 0) glDeleteTextures(asciiTexture);
        asciiTexture = -1;
        for (int page = 0; page < unicodeTextures.length; page++) {
            if (unicodeTextures[page] >= 0) glDeleteTextures(unicodeTextures[page]);
            unicodeTextures[page] = -1;
        }
    }
}
