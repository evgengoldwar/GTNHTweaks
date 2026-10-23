package gtnhtweaks.common.render;

import static org.lwjgl.opengl.GL11.*;

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
        draw(box, color(), width(), throughBlocks(), fillOpacity(), true);
    }

    /**
     * @param inCameraSpace true for a box around the camera at the origin, as in the world: it is then pulled toward
     *                      the camera. False for a preview, where the origin is the middle of the box
     */
    public static void draw(AxisAlignedBB box, int color, float width, boolean throughBlocks, float fillOpacity,
        boolean inCameraSpace) {
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
        if (throughBlocks) glDepthRange(0, 0.0001);

        twoPasses(() -> edges(box, width), color);
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

    private static void edges(AxisAlignedBB box, float width) {
        glLineWidth(width);
        glBegin(GL_LINES);
        // bottom, top, then the vertical edges: plain lines, no strip, so no vertex is drawn twice in a row
        line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ);
        line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
        line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
        line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
        glEnd();

        // wide lines end square and leave notches where they meet: a round point of the same width on every corner
        // closes the joint
        if (width > 1.5F) {
            glPointSize(width);
            glEnable(GL_POINT_SMOOTH);
            glBegin(GL_POINTS);
            glVertex3d(box.minX, box.minY, box.minZ);
            glVertex3d(box.maxX, box.minY, box.minZ);
            glVertex3d(box.maxX, box.minY, box.maxZ);
            glVertex3d(box.minX, box.minY, box.maxZ);
            glVertex3d(box.minX, box.maxY, box.minZ);
            glVertex3d(box.maxX, box.maxY, box.minZ);
            glVertex3d(box.maxX, box.maxY, box.maxZ);
            glVertex3d(box.minX, box.maxY, box.maxZ);
            glEnd();
            glDisable(GL_POINT_SMOOTH);
            glPointSize(1);
        }
    }

    private static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        glVertex3d(x1, y1, z1);
        glVertex3d(x2, y2, z2);
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
