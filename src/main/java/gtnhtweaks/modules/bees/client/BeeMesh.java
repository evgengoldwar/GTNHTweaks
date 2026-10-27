package gtnhtweaks.modules.bees.client;

import java.nio.FloatBuffer;
import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import gtnhtweaks.common.model.Shapes;

/**
 * Collects the quads of a bee and draws them in three batches through the Tessellator: opaque, glowing and
 * translucent. A bee is a hundred boxes and more; drawn one GL call per vertex, a page of bees in NEI lagged.
 * <p>
 * It keeps its own matrix stack, so the parts are placed with {@link #push}, {@link #translate}, {@link #rotate},
 * {@link #scale} and {@link #pop} as with GL, and the vertices are moved in Java. Everything is textured with the
 * texture of the bee: untextured parts take its white pixel and are colored by their vertices.
 * <p>
 * Only for the render thread.
 */
final class BeeMesh {

    private static final int OPAQUE = 0, GLOW = 1, TRANSLUCENT = 2;
    /** x, y, z, normal x, y, z, u, v of a vertex, and its color apart. */
    private static final int STRIDE = 8;

    private static final Batch[] BATCHES = { new Batch(), new Batch(), new Batch() };
    private static final float[][] STACK = new float[32][16];
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int depth;
    private static float[] m = STACK[0];
    private static boolean glowing, see;
    /** The vertex colors multiply this, as the blue of a cold bee. */
    private static int tint = 0xFFFFFF;

    private BeeMesh() {}

    private static final class Batch {

        float[] data = new float[STRIDE * 1024];
        int[] colors = new int[1024];
        int vertices;

        void add(float x, float y, float z, float nx, float ny, float nz, float u, float v, int argb) {
            if (vertices == colors.length) {
                data = Arrays.copyOf(data, data.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            int i = vertices * STRIDE;
            data[i] = x;
            data[i + 1] = y;
            data[i + 2] = z;
            data[i + 3] = nx;
            data[i + 4] = ny;
            data[i + 5] = nz;
            data[i + 6] = u;
            data[i + 7] = v;
            colors[vertices++] = argb;
        }

        void draw() {
            if (vertices == 0) return;
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (int k = 0; k < vertices; k++) {
                int i = k * STRIDE;
                int argb = colors[k];
                t.setColorRGBA(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
                t.setNormal(data[i + 3], data[i + 4], data[i + 5]);
                t.addVertexWithUV(data[i], data[i + 1], data[i + 2], data[i + 6], data[i + 7]);
            }
            t.draw();
            vertices = 0;
        }
    }

    /** Starts a bee: no quads, the matrix of the GL as it is. */
    static void begin() {
        for (Batch batch : BATCHES) batch.vertices = 0;
        depth = 0;
        m = STACK[0];
        identity(m);
        glowing = false;
        see = false;
        tint = 0xFFFFFF;
    }

    /**
     * Draws what was collected with the texture of the bee, GL_TEXTURE_2D enabled: the opaque quads, the glowing ones
     * unlit at full brightness, and the translucent ones last, blended and not hiding what is behind.
     */
    static void end(ResourceLocation texture) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        BATCHES[OPAQUE].draw();
        if (BATCHES[GLOW].vertices > 0) {
            boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
            float bx = OpenGlHelper.lastBrightnessX, by = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glDisable(GL11.GL_LIGHTING);
            BATCHES[GLOW].draw();
            if (lit) GL11.glEnable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bx, by);
        }
        if (BATCHES[TRANSLUCENT].vertices > 0) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            BATCHES[TRANSLUCENT].draw();
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    // ---- the matrix

    static void push() {
        float[] next = STACK[++depth];
        System.arraycopy(m, 0, next, 0, 16);
        m = next;
    }

    static void pop() {
        m = STACK[--depth];
    }

    static void translate(float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
    }

    static void scale(float x, float y, float z) {
        for (int i = 0; i < 3; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    /** As glRotatef: degrees around the axis, which need not be of length 1. */
    static void rotate(float degrees, float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length == 0 || degrees == 0) return;
        x /= length;
        y /= length;
        z /= length;
        double a = Math.toRadians(degrees);
        float c = (float) Math.cos(a), s = (float) Math.sin(a), t = 1 - c;
        // the columns of the rotation
        float r00 = t * x * x + c, r10 = t * x * y + s * z, r20 = t * x * z - s * y;
        float r01 = t * x * y - s * z, r11 = t * y * y + c, r21 = t * y * z + s * x;
        float r02 = t * x * z + s * y, r12 = t * y * z - s * x, r22 = t * z * z + c;
        for (int i = 0; i < 3; i++) {
            float a0 = m[i], a1 = m[4 + i], a2 = m[8 + i];
            m[i] = a0 * r00 + a1 * r10 + a2 * r20;
            m[4 + i] = a0 * r01 + a1 * r11 + a2 * r21;
            m[8 + i] = a0 * r02 + a1 * r12 + a2 * r22;
        }
    }

    private static void identity(float[] matrix) {
        Arrays.fill(matrix, 0);
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1;
    }

    // ---- what the quads go to

    /**
     * Starts drawing what glows by itself, unlit at full brightness.
     *
     * @return whether it glowed already, for {@link #endGlow}
     */
    static boolean glow() {
        boolean was = glowing;
        glowing = true;
        return was;
    }

    static void endGlow(boolean was) {
        glowing = was;
    }

    /** Starts or ends drawing see-through quads, drawn last. */
    static void translucent(boolean on) {
        see = on;
    }

    static void tint(int rgb) {
        tint = rgb;
    }

    /**
     * Draws the item with the current matrix, at once, with the GL: the bee keeps collecting its quads after it. Leaves
     * GL_TEXTURE_2D enabled.
     */
    static void item(ItemStack stack) {
        MATRIX.clear();
        MATRIX.put(m)
            .flip();
        GL11.glPushMatrix();
        GL11.glMultMatrix(MATRIX);
        HeldItem.draw(stack);
        GL11.glPopMatrix();
    }

    // ---- shapes

    /** A box with outward facing quads, colored, untextured. */
    static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        box(x0, y0, z0, x1, y1, z1, rgb, 1);
    }

    /** A box of the given opacity: below 1 it is see-through only between {@link #translucent} calls. */
    static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha) {
        int argb = argb(rgb, alpha);
        float u = BeeTexture.WHITE_U, v = BeeTexture.WHITE_V;
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u, v, u, v, argb, false);
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u, v, u, v, argb, false);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u, v, u, v, argb, false);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u, v, u, v, argb, false);
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u, v, u, v, argb, false);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u, v, u, v, argb, false);
    }

    /** A box centered on the origin. */
    static void cube(float halfX, float halfY, float halfZ, int rgb) {
        box(-halfX, -halfY, -halfZ, halfX, halfY, halfZ, rgb);
    }

    /** A flat rectangle facing +z. */
    static void face(float x0, float y0, float x1, float y1, float z, int rgb) {
        float u = BeeTexture.WHITE_U, v = BeeTexture.WHITE_V;
        quad(
            Math.min(x0, x1),
            y0,
            z,
            Math.max(x0, x1),
            y0,
            z,
            Math.max(x0, x1),
            y1,
            z,
            Math.min(x0, x1),
            y1,
            z,
            u,
            v,
            u,
            v,
            argb(rgb, 1),
            false);
    }

    /** The letter Z on the plane z = 0, facing +z, its middle at the origin. */
    static void letterZ(float halfWidth, float halfHeight, float line, int rgb) {
        face(-halfWidth, halfHeight - line, halfWidth, halfHeight, 0, rgb);
        face(-halfWidth, -halfHeight, halfWidth, -halfHeight + line, 0, rgb);
        float u = BeeTexture.WHITE_U, v = BeeTexture.WHITE_V;
        quad(
            -halfWidth,
            -halfHeight + line,
            0,
            -halfWidth + line * 1.8F,
            -halfHeight + line,
            0,
            halfWidth,
            halfHeight - line,
            0,
            halfWidth - line * 1.8F,
            halfHeight - line,
            0,
            u,
            v,
            u,
            v,
            argb(rgb, 1),
            false);
    }

    /**
     * A box with the texture of the body of the bee on it, as {@link BeeTexture} lays it out: its corners are the half
     * sizes, its faces mapped whatever its size.
     */
    static void body(float w, float h, float l, float alpha) {
        int argb = argb(0xFFFFFF, alpha);
        // the sides: from the back to the front, from the top down
        texQuad(-w, h, -l, -w, h, l, -w, -h, l, -w, -h, -l, BeeTexture.LEFT, argb, true);
        texQuad(w, h, -l, w, h, l, w, -h, l, w, -h, -l, BeeTexture.RIGHT, argb, false);
        texQuad(-w, h, l, w, h, l, w, -h, l, -w, -h, l, BeeTexture.FRONT, argb, true);
        texQuad(w, h, -l, -w, h, -l, -w, -h, -l, w, -h, -l, BeeTexture.BACK, argb, true);
        // the top and the bottom: across, from the back to the front
        texQuad(-w, h, -l, w, h, -l, w, h, l, -w, h, l, BeeTexture.TOP, argb, true);
        texQuad(-w, -h, -l, w, -h, -l, w, -h, l, -w, -h, l, BeeTexture.BOTTOM, argb, false);
    }

    /** A wing lying flat, from x = 0 to x = length, with the texture of the wing on its both sides. */
    static void wing(float length, float z0, float z1, int argb) {
        texQuad(0, 0, z0, length, 0, z0, length, 0, z1, 0, 0, z1, BeeTexture.WING, argb, true);
        texQuad(0, 0, z1, length, 0, z1, length, 0, z0, 0, 0, z0, BeeTexture.WING_UNDER, argb, true);
    }

    /**
     * A textured quad, its corners from the upper left of the region of the texture clockwise.
     *
     * @param region u, v, width, height of the region, in pixels of the texture
     */
    private static void texQuad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
        float cz, float dx, float dy, float dz, int[] region, int argb, boolean flip) {
        float u0 = region[0] / (float) BeeTexture.WIDTH, v0 = region[1] / (float) BeeTexture.HEIGHT;
        float u1 = (region[0] + region[2]) / (float) BeeTexture.WIDTH;
        float v1 = (region[1] + region[3]) / (float) BeeTexture.HEIGHT;
        quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u0, v0, u1, v1, argb, flip);
    }

    /**
     * a is at (u0, v0), b at (u1, v0), c at (u1, v1), d at (u0, v1). The normal follows the order of the corners,
     * turned round when flip.
     */
    private static void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
        float dx, float dy, float dz, float u0, float v0, float u1, float v1, int argb, boolean flip) {
        float[] k = m;
        float pax = k[0] * ax + k[4] * ay + k[8] * az + k[12], pay = k[1] * ax + k[5] * ay + k[9] * az + k[13],
            paz = k[2] * ax + k[6] * ay + k[10] * az + k[14];
        float pbx = k[0] * bx + k[4] * by + k[8] * bz + k[12], pby = k[1] * bx + k[5] * by + k[9] * bz + k[13],
            pbz = k[2] * bx + k[6] * by + k[10] * bz + k[14];
        float pcx = k[0] * cx + k[4] * cy + k[8] * cz + k[12], pcy = k[1] * cx + k[5] * cy + k[9] * cz + k[13],
            pcz = k[2] * cx + k[6] * cy + k[10] * cz + k[14];
        float pdx = k[0] * dx + k[4] * dy + k[8] * dz + k[12], pdy = k[1] * dx + k[5] * dy + k[9] * dz + k[13],
            pdz = k[2] * dx + k[6] * dy + k[10] * dz + k[14];
        // the normal of the moved quad, from its diagonals
        float ex = pcx - pax, ey = pcy - pay, ez = pcz - paz, fx = pdx - pbx, fy = pdy - pby, fz = pdz - pbz;
        float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (flip) n = -n;
        if (n != 0) {
            nx /= n;
            ny /= n;
            nz /= n;
        }
        Batch batch = BATCHES[see ? TRANSLUCENT : glowing ? GLOW : OPAQUE];
        batch.add(pax, pay, paz, nx, ny, nz, u0, v0, argb);
        batch.add(pbx, pby, pbz, nx, ny, nz, u1, v0, argb);
        batch.add(pcx, pcy, pcz, nx, ny, nz, u1, v1, argb);
        batch.add(pdx, pdy, pdz, nx, ny, nz, u0, v1, argb);
    }

    private static int argb(int rgb, float alpha) {
        int color = tint == 0xFFFFFF ? rgb : multiply(rgb, tint);
        return (int) (Shapes.clamp(alpha) * 255) << 24 | color & 0xFFFFFF;
    }

    private static int multiply(int a, int b) {
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return r << 16 | g << 8 | bl;
    }
}
