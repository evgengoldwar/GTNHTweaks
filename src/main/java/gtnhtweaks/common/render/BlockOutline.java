package gtnhtweaks.common.render;

import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;

import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * The outline of the looked at block: the vanilla one, or the color, width, mode and fill of the minecraft module.
 * Used for the plain outline, the GregTech tool overlay and the preview of the outline screen.
 */
public final class BlockOutline {

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

    public static float fillOpacity() {
        return MinecraftModule.outlineActive() ? MinecraftModule.fillOpacity : 0F;
    }

    /**
     * Draws the outline of the box, already moved to camera space. GL_TEXTURE_2D must be disabled and blending on; the
     * line width, depth state and color are restored by the caller's attribute stack.
     */
    public static void draw(AxisAlignedBB box) {
        draw(box, color(), width(), throughBlocks(), fillOpacity(), 0);
    }

    /**
     * @param previewPixel 0 for a box around the camera at the origin, as in the world: it is then pulled toward the
     *                     camera. For a preview, the size of a screen pixel in the units of the box
     */
    public static void draw(AxisAlignedBB box, int color, float width, boolean throughBlocks, float fillOpacity,
        double previewPixel) {
        boolean inCameraSpace = previewPixel <= 0;
        // the box is in camera space: scaling it a little around the camera pulls it toward the eye, so its edges
        // are never hidden by the faces of the block itself, while blocks in front still hide it. Wide lines reach
        // further into those faces, half of them would be hidden: the wider, the more it is pulled
        float pull = inCameraSpace ? 1 - (0.002F + 0.0012F * width) : 1F;
        glPushMatrix();
        glScalef(pull, pull, pull);

        // A translucent outline must be blended once per pixel: where lines and corner points overlap, blending
        // twice makes the joints brighter. So it is drawn twice. The first pass writes only depth: the nearest
        // outline fragment of every pixel. The second draws the color with GL_EQUAL: only that fragment passes.
        // Seen through blocks, the outline depth is squeezed into the front of the depth range, so the world never
        // hides it while the outline still sorts itself.
        glEnable(GL_DEPTH_TEST);
        // the front 10% of the depth range holds only what is a few centimeters from the eye, and keeps enough
        // precision for the outline to sort itself without flickering
        if (throughBlocks) glDepthRange(0, 0.1);

        twoPasses(() -> edges(box, width, previewPixel), color);
        if (fillOpacity > 0) {
            // behind the lines: their pixels hold a nearer depth, the fill does not tint them
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(-1, -1);
            twoPasses(() -> {
                glBegin(GL_QUADS);
                quads(box);
                glEnd();
            }, color & 0xFFFFFF | Math.round(fillOpacity * 255) << 24);
            glDisable(GL_POLYGON_OFFSET_FILL);
        }

        glDepthRange(0, 1);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glPopMatrix();
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

    /**
     * The 12 edges. GL lines end square at the corner, which leaves a notch where two wide lines meet: every line is
     * made longer by half its width at both ends, measured in pixels where the end is, so the corners close square.
     */
    private static void edges(AxisAlignedBB b, float width, double previewPixel) {
        // height of the view in units at distance 1, to turn pixels into lengths at a given distance
        double viewHeight = previewPixel > 0 ? 0
            : 2 * Math.tan(Math.toRadians(Minecraft.getMinecraft().gameSettings.fovSetting) / 2)
                / Math.max(1, Minecraft.getMinecraft().displayHeight);
        glLineWidth(width);
        glBegin(GL_LINES);
        double[][] c = { { b.minX, b.minY, b.minZ }, { b.maxX, b.minY, b.minZ }, { b.maxX, b.minY, b.maxZ },
            { b.minX, b.minY, b.maxZ }, { b.minX, b.maxY, b.minZ }, { b.maxX, b.maxY, b.minZ },
            { b.maxX, b.maxY, b.maxZ }, { b.minX, b.maxY, b.maxZ } };
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 },
            { 1, 5 }, { 2, 6 }, { 3, 7 } };
        for (int[] e : edges) {
            double[] from = c[e[0]], to = c[e[1]];
            double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) continue;
            dx /= length;
            dy /= length;
            dz /= length;
            double extFrom = width / 2 * pixelAt(from, previewPixel, viewHeight);
            double extTo = width / 2 * pixelAt(to, previewPixel, viewHeight);
            glVertex3d(from[0] - dx * extFrom, from[1] - dy * extFrom, from[2] - dz * extFrom);
            glVertex3d(to[0] + dx * extTo, to[1] + dy * extTo, to[2] + dz * extTo);
        }
        glEnd();
    }

    /** Size of a screen pixel at the point: fixed in a preview, growing with the distance from the camera. */
    private static double pixelAt(double[] p, double previewPixel, double viewHeight) {
        if (previewPixel > 0) return previewPixel;
        return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) * viewHeight;
    }

    private static void quads(AxisAlignedBB b) {
        // bottom, top, north, south, west, east
        glVertex3d(b.minX, b.minY, b.minZ);
        glVertex3d(b.maxX, b.minY, b.minZ);
        glVertex3d(b.maxX, b.minY, b.maxZ);
        glVertex3d(b.minX, b.minY, b.maxZ);
        glVertex3d(b.minX, b.maxY, b.minZ);
        glVertex3d(b.minX, b.maxY, b.maxZ);
        glVertex3d(b.maxX, b.maxY, b.maxZ);
        glVertex3d(b.maxX, b.maxY, b.minZ);
        glVertex3d(b.minX, b.minY, b.minZ);
        glVertex3d(b.minX, b.maxY, b.minZ);
        glVertex3d(b.maxX, b.maxY, b.minZ);
        glVertex3d(b.maxX, b.minY, b.minZ);
        glVertex3d(b.minX, b.minY, b.maxZ);
        glVertex3d(b.maxX, b.minY, b.maxZ);
        glVertex3d(b.maxX, b.maxY, b.maxZ);
        glVertex3d(b.minX, b.maxY, b.maxZ);
        glVertex3d(b.minX, b.minY, b.minZ);
        glVertex3d(b.minX, b.minY, b.maxZ);
        glVertex3d(b.minX, b.maxY, b.maxZ);
        glVertex3d(b.minX, b.maxY, b.minZ);
        glVertex3d(b.maxX, b.minY, b.minZ);
        glVertex3d(b.maxX, b.maxY, b.minZ);
        glVertex3d(b.maxX, b.maxY, b.maxZ);
        glVertex3d(b.maxX, b.minY, b.maxZ);
    }
}
