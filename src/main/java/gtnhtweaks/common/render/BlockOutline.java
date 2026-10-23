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
        draw(box, color(), width(), throughBlocks(), fillOpacity());
    }

    public static void draw(AxisAlignedBB box, int color, float width, boolean throughBlocks, float fillOpacity) {
        glDepthMask(false);
        if (throughBlocks) glDisable(GL_DEPTH_TEST);
        else glEnable(GL_DEPTH_TEST);

        if (fillOpacity > 0) {
            GLDraw.color(color & 0xFFFFFF | Math.round(fillOpacity * 255) << 24);
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(-1, -1);
            glBegin(GL_QUADS);
            quads(box);
            glEnd();
            glDisable(GL_POLYGON_OFFSET_FILL);
        }

        glLineWidth(width);
        GLDraw.color(color);
        glBegin(GL_LINE_STRIP);
        glVertex3d(box.minX, box.minY, box.minZ);
        glVertex3d(box.maxX, box.minY, box.minZ);
        glVertex3d(box.maxX, box.minY, box.maxZ);
        glVertex3d(box.minX, box.minY, box.maxZ);
        glVertex3d(box.minX, box.minY, box.minZ);
        glEnd();
        glBegin(GL_LINE_STRIP);
        glVertex3d(box.minX, box.maxY, box.minZ);
        glVertex3d(box.maxX, box.maxY, box.minZ);
        glVertex3d(box.maxX, box.maxY, box.maxZ);
        glVertex3d(box.minX, box.maxY, box.maxZ);
        glVertex3d(box.minX, box.maxY, box.minZ);
        glEnd();
        glBegin(GL_LINES);
        glVertex3d(box.minX, box.minY, box.minZ);
        glVertex3d(box.minX, box.maxY, box.minZ);
        glVertex3d(box.maxX, box.minY, box.minZ);
        glVertex3d(box.maxX, box.maxY, box.minZ);
        glVertex3d(box.maxX, box.minY, box.maxZ);
        glVertex3d(box.maxX, box.maxY, box.maxZ);
        glVertex3d(box.minX, box.minY, box.maxZ);
        glVertex3d(box.minX, box.maxY, box.maxZ);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
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
