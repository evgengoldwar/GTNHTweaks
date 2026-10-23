package gtnhtweaks.common.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.util.AxisAlignedBB;

import org.lwjgl.BufferUtils;

import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * The outline of the looked at block: the vanilla one, or the color, width, mode and fill of the minecraft module.
 * Used for the plain outline, the GregTech tool overlay and the preview of the outline screen.
 * <p>
 * Wide GL lines change their width with their slope, end square and overlap at the corners, so the edges are drawn as
 * strips on the screen instead: the corners of the box are projected with the current matrices, every edge becomes a
 * strip of exactly the width in pixels, and every corner a disc of the same width that joins the strips round. Each
 * point keeps the depth of the box, so blocks in front still hide the outline.
 */
public final class BlockOutline {

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final int DISC_SEGMENTS = 16;
    private static final int[][] EDGES = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 },
        { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };

    private BlockOutline() {}

    public static int color() {
        return MinecraftModule.outlineActive() ? MinecraftModule.outlineColor : MinecraftModule.VANILLA_COLOR;
    }

    public static float width() {
        return MinecraftModule.outlineActive() ? MinecraftModule.outlineWidth : MinecraftModule.VANILLA_WIDTH;
    }

    public static boolean throughBlocks() {
        return MinecraftModule.outlineActive()
            && MinecraftModule.outlineMode == MinecraftModule.OutlineMode.THROUGH_BLOCKS;
    }

    public static int fillColor() {
        return MinecraftModule.outlineActive() ? MinecraftModule.fillColor : 0;
    }

    public static boolean rainbow() {
        return MinecraftModule.outlineActive() && MinecraftModule.rainbow;
    }

    /** The rainbow color at a place along the outline, 0 to 1, now; with the alpha of the line color. */
    public static int rainbowColor(float place, int alphaOf) {
        double rounds = System.currentTimeMillis() / 1000D * MinecraftModule.rainbowSpeed;
        float hue = (float) ((rounds + place) % 1);
        return Color.HSBtoRGB(hue, 0.85F, 1F) & 0xFFFFFF | alphaOf & 0xFF000000;
    }

    /** The color of plain lines like the GregTech grid: the line color, or the rainbow of this moment. */
    public static int lineColor() {
        return rainbow() ? rainbowColor(0, color()) : color();
    }

    /**
     * Draws the outline of the box, already moved to camera space. GL_TEXTURE_2D must be disabled and blending on; the
     * depth state and color are restored by the caller's attribute stack.
     */
    public static void draw(AxisAlignedBB box) {
        draw(box, color(), width(), throughBlocks(), fillColor(), 0);
    }

    /**
     * @param preview 0 for a box around the camera at the origin, as in the world: it is then pulled a little toward
     *                the camera so the faces of the block itself never hide it. Above 0 for a preview, not pulled
     */
    public static void draw(AxisAlignedBB box, int color, float width, boolean throughBlocks, int fillColor,
        double preview) {
        glPushMatrix();
        if (preview <= 0) glScalef(0.997F, 0.997F, 0.997F);

        // Every pixel is blended once, also where strips and discs overlap: a first pass writes only the depth of
        // the nearest outline fragment, the color pass with GL_EQUAL lets only that one through. Seen through
        // blocks, the outline depth goes to the front 10% of the depth range: in front of everything but what is a
        // few centimeters from the eye, with the precision to sort itself without flickering.
        glEnable(GL_DEPTH_TEST);
        if (throughBlocks) glDepthRange(0, 0.1);

        if ((fillColor >>> 24) > 0) {
            // Only the faces toward the camera, so every pixel is blended once, and without writing depth, so the
            // strips drawn over it are never hidden by it
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(-1, -1);
            glDepthMask(false);
            glDepthFunc(GL_LEQUAL);
            GLDraw.color(fillColor);
            boolean[] facing = facingCamera(box);
            boolean cull = glIsEnabled(GL_CULL_FACE);
            glDisable(GL_CULL_FACE);
            glBegin(GL_QUADS);
            for (int face = 0; face < 6; face++) {
                if (facing[face]) face(box, face);
            }
            glEnd();
            if (cull) glEnable(GL_CULL_FACE);
            glDisable(GL_POLYGON_OFFSET_FILL);
        }

        float[][] screen = project(box);
        glPopMatrix();
        drawOnScreen(screen, width, color);

        glDepthRange(0, 1);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
    }

    /** Depth only, then the color of the nearest fragment of every pixel. */
    private static void twoPasses(Runnable geometry, int color) {
        glColorMask(false, false, false, false);
        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);
        geometry.run();
        glColorMask(true, true, true, true);
        glDepthMask(false);
        glDepthFunc(GL_EQUAL);
        GLDraw.color(color);
        geometry.run();
    }

    // ---- edges on the screen

    /**
     * The 8 corners in window pixels with their depth in normalized device coordinates: {x, y, z, visible}. A corner
     * behind the camera is not visible; its edges are cut at the near plane.
     */
    private static float[][] project(AxisAlignedBB b) {
        float[] m = matrix(GL_MODELVIEW_MATRIX);
        float[] p = matrix(GL_PROJECTION_MATRIX);
        float[] mvp = multiply(p, m);
        double[][] corners = { { b.minX, b.minY, b.minZ }, { b.maxX, b.minY, b.minZ }, { b.maxX, b.minY, b.maxZ },
            { b.minX, b.minY, b.maxZ }, { b.minX, b.maxY, b.minZ }, { b.maxX, b.maxY, b.minZ },
            { b.maxX, b.maxY, b.maxZ }, { b.minX, b.maxY, b.maxZ } };
        float[][] clip = new float[8][];
        for (int i = 0; i < 8; i++) clip[i] = transform(mvp, corners[i]);
        return clip;
    }

    private static void drawOnScreen(float[][] clip, float width, int color) {
        VIEWPORT.clear();
        glGetInteger(GL_VIEWPORT, VIEWPORT);
        float vx = VIEWPORT.get(0), vy = VIEWPORT.get(1), vw = VIEWPORT.get(2), vh = VIEWPORT.get(3);
        if (vw <= 0 || vh <= 0) return;

        // the strips are drawn in normalized device coordinates, with no transformation
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        rainbowNow = rainbow();
        rainbowAlpha = color;
        try {
            float half = width / 2;
            twoPasses(() -> {
                glBegin(GL_TRIANGLES);
                for (int[] e : EDGES) {
                    float[][] seg = clipToNear(clip[e[0]], clip[e[1]]);
                    if (seg == null) continue;
                    float[] a = toWindow(seg[0], vx, vy, vw, vh), c = toWindow(seg[1], vx, vy, vw, vh);
                    strip(a, c, half, vx, vy, vw, vh);
                }
                for (float[] corner : clip) {
                    if (corner[3] <= 1e-4F) continue;
                    disc(toWindow(corner, vx, vy, vw, vh), half, vx, vy, vw, vh);
                }
                glEnd();
            }, color);
        } finally {
            rainbowNow = false;
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        }
    }

    /** A strip of the width between two window points, as two triangles. */
    private static void strip(float[] a, float[] b, float half, float vx, float vy, float vw, float vh) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3F) return;
        float nx = -dy / length * half, ny = dx / length * half;
        vertex(a[0] + nx, a[1] + ny, a[2], vx, vy, vw, vh);
        vertex(a[0] - nx, a[1] - ny, a[2], vx, vy, vw, vh);
        vertex(b[0] - nx, b[1] - ny, b[2], vx, vy, vw, vh);
        vertex(a[0] + nx, a[1] + ny, a[2], vx, vy, vw, vh);
        vertex(b[0] - nx, b[1] - ny, b[2], vx, vy, vw, vh);
        vertex(b[0] + nx, b[1] + ny, b[2], vx, vy, vw, vh);
    }

    /** A disc of the width at a corner, so the strips meet round. */
    private static void disc(float[] c, float half, float vx, float vy, float vw, float vh) {
        for (int i = 0; i < DISC_SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / DISC_SEGMENTS, a1 = Math.PI * 2 * (i + 1) / DISC_SEGMENTS;
            vertex(c[0], c[1], c[2], vx, vy, vw, vh);
            vertex(c[0] + (float) Math.cos(a0) * half, c[1] + (float) Math.sin(a0) * half, c[2], vx, vy, vw, vh);
            vertex(c[0] + (float) Math.cos(a1) * half, c[1] + (float) Math.sin(a1) * half, c[2], vx, vy, vw, vh);
        }
    }

    /** Set while the rainbow is drawn: the alpha it keeps. */
    private static boolean rainbowNow;
    private static int rainbowAlpha;

    private static void vertex(float x, float y, float z, float vx, float vy, float vw, float vh) {
        if (rainbowNow) {
            // the hue runs diagonally over the screen, so it flows along the outline and is smooth at the joints
            float place = ((x - vx) + (y - vy)) / Math.max(1, Math.min(vw, vh)) * 1.5F;
            GLDraw.color(rainbowColor(place, rainbowAlpha));
        }
        glVertex3f((x - vx) / vw * 2 - 1, (y - vy) / vh * 2 - 1, z);
    }

    /** Clip coordinates to window pixels, keeping the normalized depth. */
    private static float[] toWindow(float[] c, float vx, float vy, float vw, float vh) {
        float w = c[3];
        return new float[] { vx + (c[0] / w * 0.5F + 0.5F) * vw, vy + (c[1] / w * 0.5F + 0.5F) * vh, c[2] / w };
    }

    /** The part of the segment in front of the near plane, or null if all of it is behind. */
    private static float[][] clipToNear(float[] a, float[] b) {
        float da = a[2] + a[3], db = b[2] + b[3];
        if (da < 0 && db < 0) return null;
        if (da >= 0 && db >= 0) return new float[][] { a, b };
        float t = da / (da - db);
        float[] cut = new float[4];
        for (int i = 0; i < 4; i++) cut[i] = a[i] + (b[i] - a[i]) * t;
        return da < 0 ? new float[][] { cut, b } : new float[][] { a, cut };
    }

    private static float[] matrix(int which) {
        MATRIX.clear();
        glGetFloat(which, MATRIX);
        float[] m = new float[16];
        MATRIX.get(m);
        return m;
    }

    /** a * b, column major. */
    private static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[col * 4 + k];
                r[col * 4 + row] = sum;
            }
        }
        return r;
    }

    private static float[] transform(float[] m, double[] v) {
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = (float) (m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row]);
        }
        return r;
    }

    /** Outward normals of the faces: bottom, top, north, south, west, east. */
    private static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    /** Which faces of the box the camera sees, from the current modelview and projection. */
    private static boolean[] facingCamera(AxisAlignedBB b) {
        float[] m = matrix(GL_MODELVIEW_MATRIX);
        float[] p = matrix(GL_PROJECTION_MATRIX);
        boolean ortho = p[11] == 0 && p[15] != 0;
        double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
        double hx = (b.maxX - b.minX) / 2, hy = (b.maxY - b.minY) / 2, hz = (b.maxZ - b.minZ) / 2;
        boolean[] facing = new boolean[6];
        for (int f = 0; f < 6; f++) {
            int[] n = NORMALS[f];
            // the normal in eye space, then the center of the face
            float nx = m[0] * n[0] + m[4] * n[1] + m[8] * n[2];
            float ny = m[1] * n[0] + m[5] * n[1] + m[9] * n[2];
            float nz = m[2] * n[0] + m[6] * n[1] + m[10] * n[2];
            if (ortho) {
                facing[f] = nz > 0;
                continue;
            }
            float[] c = transform(m, new double[] { cx + n[0] * hx, cy + n[1] * hy, cz + n[2] * hz });
            facing[f] = nx * c[0] + ny * c[1] + nz * c[2] < 0;
        }
        return facing;
    }

    private static void face(AxisAlignedBB b, int face) {
        switch (face) {
            case 0:
                glVertex3d(b.minX, b.minY, b.minZ);
                glVertex3d(b.maxX, b.minY, b.minZ);
                glVertex3d(b.maxX, b.minY, b.maxZ);
                glVertex3d(b.minX, b.minY, b.maxZ);
                break;
            case 1:
                glVertex3d(b.minX, b.maxY, b.minZ);
                glVertex3d(b.minX, b.maxY, b.maxZ);
                glVertex3d(b.maxX, b.maxY, b.maxZ);
                glVertex3d(b.maxX, b.maxY, b.minZ);
                break;
            case 2:
                glVertex3d(b.minX, b.minY, b.minZ);
                glVertex3d(b.minX, b.maxY, b.minZ);
                glVertex3d(b.maxX, b.maxY, b.minZ);
                glVertex3d(b.maxX, b.minY, b.minZ);
                break;
            case 3:
                glVertex3d(b.minX, b.minY, b.maxZ);
                glVertex3d(b.maxX, b.minY, b.maxZ);
                glVertex3d(b.maxX, b.maxY, b.maxZ);
                glVertex3d(b.minX, b.maxY, b.maxZ);
                break;
            case 4:
                glVertex3d(b.minX, b.minY, b.minZ);
                glVertex3d(b.minX, b.minY, b.maxZ);
                glVertex3d(b.minX, b.maxY, b.maxZ);
                glVertex3d(b.minX, b.maxY, b.minZ);
                break;
            default:
                glVertex3d(b.maxX, b.minY, b.minZ);
                glVertex3d(b.maxX, b.maxY, b.minZ);
                glVertex3d(b.maxX, b.maxY, b.maxZ);
                glVertex3d(b.maxX, b.minY, b.maxZ);
                break;
        }
    }
}
