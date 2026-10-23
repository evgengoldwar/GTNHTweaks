package gtnhtweaks.modules.gregtech.client;

import static org.lwjgl.opengl.GL11.*;

import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL20;

import gtnhtweaks.common.render.BlockOutline;

/**
 * Draws the tool overlay of GregTech CEu Modern on the looked at face: the vanilla outline, a pulsing 3x3 grid and an
 * icon in every cell that tells what the tool does to that side. The cell under the cursor is drawn opaque, the others
 * faint. Each cell stands for a side of the block: the middle for the looked at face, the edges for the sides next to
 * them, the corners for the back.
 */
public final class GridOverlay {

    /** Size of an icon: a quarter of the face, minus GTCEu's margin of 0.2 / 16 on each side. */
    private static final double ICON = (4 - 2 * 0.2) / 16;
    private static final double LIFT = 0.01;

    private GridOverlay() {}

    /** The vanilla outline, the grid and the icon of every side; {@code icons} gives null for no icon. */
    public static void drawGrid(DrawBlockHighlightEvent event, Function<ForgeDirection, ToolIcon> icons) {
        MovingObjectPosition target = event.target;
        ForgeDirection face = ForgeDirection.getOrientation(target.sideHit);
        if (face == ForgeDirection.UNKNOWN) return;
        ForgeDirection hovered = gridSide(target);

        begin(event);
        try {
            drawOutline(event);
            drawGridAt(new Face(target, face), icons, hovered, lineWidth(3));
        } finally {
            end();
        }
    }

    /**
     * The grid and the icons on a face of a block centered at (x, y, z) in the current GL space, for a preview. GL
     * state as for the world overlay: blending on, texture off.
     */
    public static void drawGridAt(double x, double y, double z, ForgeDirection face,
        Function<ForgeDirection, ToolIcon> icons, ForgeDirection hovered, float lineWidth) {
        drawGridAt(new Face(x, y, z, face), icons, hovered, lineWidth);
    }

    private static void drawGridAt(Face f, Function<ForgeDirection, ToolIcon> icons, ForgeDirection hovered,
        float lineWidth) {
        // the grid, pulsing between blue and white like in GTCEu
        float rg = Math.max(0, 0.2F + (float) Math.sin((System.currentTimeMillis() % (Math.PI * 800)) / 800) / 2);
        glLineWidth(lineWidth);
        glColor4f(rg, rg, 1, 1);
        glBegin(GL_LINES);
        f.vertex(-0.25, -0.5, LIFT);
        f.vertex(-0.25, 0.5, LIFT);
        f.vertex(0.25, -0.5, LIFT);
        f.vertex(0.25, 0.5, LIFT);
        f.vertex(-0.5, -0.25, LIFT);
        f.vertex(0.5, -0.25, LIFT);
        f.vertex(-0.5, 0.25, LIFT);
        f.vertex(0.5, 0.25, LIFT);
        glEnd();

        // the icons, over everything
        glDisable(GL_DEPTH_TEST);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            ToolIcon icon = icons.apply(side);
            if (icon == null) continue;
            float alpha = side == hovered ? 1F : 0x44 / 255F;
            for (double[] cell : f.cells(side)) drawIcon(f, icon, cell[0], cell[1], ICON, alpha);
        }
        glEnable(GL_DEPTH_TEST);
    }

    /** Only the icon of the looked at face, larger, in its middle: for tools without a grid like the soft mallet. */
    public static void drawSingle(DrawBlockHighlightEvent event, ToolIcon icon) {
        MovingObjectPosition target = event.target;
        ForgeDirection face = ForgeDirection.getOrientation(target.sideHit);
        if (face == ForgeDirection.UNKNOWN || icon == null) return;
        begin(event);
        try {
            glDisable(GL_DEPTH_TEST);
            drawIcon(new Face(target, face), icon, 0, 0, 0.5, 1F);
        } finally {
            end();
        }
    }

    /**
     * The side a click on the face changes, from where the face is hit: the middle is the face itself, an edge the side
     * next to it, a corner the opposite side. The same rule as GregTech's wrenching side.
     */
    public static ForgeDirection gridSide(MovingObjectPosition target) {
        ForgeDirection face = ForgeDirection.getOrientation(target.sideHit);
        if (face == ForgeDirection.UNKNOWN || target.hitVec == null) return face;
        Face f = new Face(target, face);
        double dx = target.hitVec.xCoord - (target.blockX + 0.5);
        double dy = target.hitVec.yCoord - (target.blockY + 0.5);
        double dz = target.hitVec.zCoord - (target.blockZ + 0.5);
        double u = dx * f.u.offsetX + dy * f.u.offsetY + dz * f.u.offsetZ;
        double v = dx * f.v.offsetX + dy * f.v.offsetY + dz * f.v.offsetZ;
        boolean edgeU = Math.abs(u) > 0.25;
        boolean edgeV = Math.abs(v) > 0.25;
        if (edgeU && edgeV) return face.getOpposite();
        if (edgeU) return u > 0 ? f.u : f.u.getOpposite();
        if (edgeV) return v > 0 ? f.v : f.v.getOpposite();
        return face;
    }

    // ---- drawing

    private static void drawIcon(Face f, ToolIcon icon, double u, double v, double size, float alpha) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(icon.texture);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, alpha);
        double h = size / 2;
        double lift = LIFT * 1.5;
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1);
        f.vertex(u - h, v - h, lift);
        glTexCoord2f(1, 1);
        f.vertex(u + h, v - h, lift);
        glTexCoord2f(1, 0);
        f.vertex(u + h, v + h, lift);
        glTexCoord2f(0, 0);
        f.vertex(u - h, v + h, lift);
        glEnd();
        glDisable(GL_TEXTURE_2D);
    }

    /** The selection box, as it is drawn without the overlay: vanilla or the outline of the minecraft module. */
    private static void drawOutline(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.player;
        MovingObjectPosition target = event.target;
        World world = player.worldObj;
        Block block = world.getBlock(target.blockX, target.blockY, target.blockZ);
        if (block.getMaterial() == Material.air) return;
        block.setBlockBoundsBasedOnState(world, target.blockX, target.blockY, target.blockZ);
        AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(world, target.blockX, target.blockY, target.blockZ);
        if (box == null) return;
        box = box.expand(0.002, 0.002, 0.002)
            .getOffsetBoundingBox(-camX, -camY, -camZ);
        BlockOutline.draw(box);
    }

    private static double camX, camY, camZ;
    private static int savedProgram;

    private static void begin(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.player;
        double t = event.partialTicks;
        camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * t;
        camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * t;
        camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * t;
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LINE_BIT | GL_DEPTH_BUFFER_BIT);
        // no shader of a shader pack on the overlay
        savedProgram = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
    }

    private static void end() {
        GL20.glUseProgram(savedProgram);
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
    }

    /** GTCEu's line widths are for a 1080 pixel high window. */
    private static float lineWidth(float width) {
        return Math.max(1, width * Minecraft.getMinecraft().displayHeight / 1080F);
    }

    /**
     * A face of the looked at block, with the axes u (right) and v (up) as the face is seen from outside, in camera
     * space.
     */
    private static final class Face {

        final ForgeDirection normal, u, v;
        final double cx, cy, cz;

        Face(MovingObjectPosition target, ForgeDirection normal) {
            this(target.blockX + 0.5 - camX, target.blockY + 0.5 - camY, target.blockZ + 0.5 - camZ, normal);
        }

        Face(double cx, double cy, double cz, ForgeDirection normal) {
            this.normal = normal;
            if (normal == ForgeDirection.SOUTH) {
                u = ForgeDirection.EAST;
                v = ForgeDirection.UP;
            } else if (normal == ForgeDirection.NORTH) {
                u = ForgeDirection.WEST;
                v = ForgeDirection.UP;
            } else if (normal == ForgeDirection.EAST) {
                u = ForgeDirection.NORTH;
                v = ForgeDirection.UP;
            } else if (normal == ForgeDirection.WEST) {
                u = ForgeDirection.SOUTH;
                v = ForgeDirection.UP;
            } else if (normal == ForgeDirection.UP) {
                u = ForgeDirection.EAST;
                v = ForgeDirection.NORTH;
            } else {
                u = ForgeDirection.EAST;
                v = ForgeDirection.SOUTH;
            }
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        /** A point at (a, b) on the face, {@code lift} above it. */
        void vertex(double a, double b, double lift) {
            double n = 0.5 + lift;
            glVertex3d(
                cx + normal.offsetX * n + u.offsetX * a + v.offsetX * b,
                cy + normal.offsetY * n + u.offsetY * a + v.offsetY * b,
                cz + normal.offsetZ * n + u.offsetZ * a + v.offsetZ * b);
        }

        /** Centers of the cells that stand for {@code side}: the middle, an edge cell, or the four corners. */
        double[][] cells(ForgeDirection side) {
            if (side == normal) return new double[][] { { 0, 0 } };
            if (side == normal.getOpposite()) {
                return new double[][] { { -0.375, -0.375 }, { 0.375, -0.375 }, { -0.375, 0.375 }, { 0.375, 0.375 } };
            }
            if (side == u) return new double[][] { { 0.375, 0 } };
            if (side == u.getOpposite()) return new double[][] { { -0.375, 0 } };
            if (side == v) return new double[][] { { 0, 0.375 } };
            return new double[][] { { 0, -0.375 } };
        }
    }
}
