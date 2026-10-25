package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LIGHTING;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4ub;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glNormal3f;
import static org.lwjgl.opengl.GL11.glVertex3f;

/** Untextured boxes and flat faces the companion models are made of, and the little maths they share. */
final class Shapes {

    private static final Runnable NOTHING = () -> {};
    /** Set by the item renderer: light at full brightness for what glows, and the light as it was after. */
    static Runnable fullBright = NOTHING, usualLight = NOTHING;

    private Shapes() {}

    /**
     * Starts drawing what glows by itself: unlit, at full brightness, not darker at night.
     *
     * @return whether the lighting was on, for {@link #endGlow}
     */
    static boolean glow() {
        boolean lit = glIsEnabled(GL_LIGHTING);
        fullBright.run();
        glDisable(GL_LIGHTING);
        return lit;
    }

    static void endGlow(boolean lit) {
        if (lit) glEnable(GL_LIGHTING);
        usualLight.run();
    }

    /** A box with outward facing quads and their normals. */
    static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        box(x0, y0, z0, x1, y1, z1, rgb, 1);
    }

    /** A box of the given opacity; see {@link #translucent}. */
    static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha) {
        color(rgb, alpha);
        glBegin(GL_QUADS);
        glNormal3f(0, 1, 0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x0, y1, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z0);
        glNormal3f(0, -1, 0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z1);
        glNormal3f(0, 0, 1);
        glVertex3f(x0, y0, z1);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z1);
        glNormal3f(0, 0, -1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x1, y1, z0);
        glNormal3f(1, 0, 0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z1);
        glNormal3f(-1, 0, 0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y0, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z0);
        glEnd();
    }

    /** A box centered on the origin. */
    static void cube(float halfX, float halfY, float halfZ, int rgb) {
        box(-halfX, -halfY, -halfZ, halfX, halfY, halfZ, rgb);
    }

    /** A flat rectangle facing +z. Works with x0 > x1 too. */
    static void face(float x0, float y0, float x1, float y1, float z, int rgb) {
        if (x0 > x1) {
            float swap = x0;
            x0 = x1;
            x1 = swap;
        }
        color(rgb, 1);
        glBegin(GL_QUADS);
        glNormal3f(0, 0, 1);
        glVertex3f(x0, y0, z);
        glVertex3f(x1, y0, z);
        glVertex3f(x1, y1, z);
        glVertex3f(x0, y1, z);
        glEnd();
    }

    /** The letter Z on the plane z = 0, facing +z, its middle at the origin. */
    static void letterZ(float halfWidth, float halfHeight, float line, int rgb) {
        face(-halfWidth, halfHeight - line, halfWidth, halfHeight, 0, rgb);
        face(-halfWidth, -halfHeight, halfWidth, -halfHeight + line, 0, rgb);
        // the slanted stroke, from the lower left to the upper right
        color(rgb, 1);
        glBegin(GL_QUADS);
        glNormal3f(0, 0, 1);
        glVertex3f(-halfWidth, -halfHeight + line, 0);
        glVertex3f(-halfWidth + line * 1.8F, -halfHeight + line, 0);
        glVertex3f(halfWidth, halfHeight - line, 0);
        glVertex3f(halfWidth - line * 1.8F, halfHeight - line, 0);
        glEnd();
    }

    /**
     * Starts or ends drawing see-through boxes: blended, and not hiding what is drawn after them. They are drawn last,
     * after everything opaque.
     */
    static void translucent(boolean on) {
        if (on) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
        } else {
            glDepthMask(true);
            glDisable(GL_BLEND);
        }
    }

    static void color(int rgb, float alpha) {
        glColor4ub((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb, (byte) (clamp(alpha) * 255));
    }

    static int mix(int a, int b, float f) {
        f = clamp(f);
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return r << 16 | g << 8 | bl;
    }

    static float clamp(float f) {
        return Math.max(0, Math.min(1, f));
    }

    static double easeOut(double f) {
        return 1 - (1 - f) * (1 - f);
    }

    /** The part after the point, in [0, 1), negative numbers included. */
    static double frac(double f) {
        return f - Math.floor(f);
    }

    /** 0, or up to 1 for the given length once in every period. */
    static float pulse(double t, double period, double length) {
        double u = frac(t / period) * period;
        return u < length ? (float) Math.sin(Math.PI * u / length) : 0;
    }

    /** A number in [0, 1) that only depends on its arguments. */
    static double rand(int seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
