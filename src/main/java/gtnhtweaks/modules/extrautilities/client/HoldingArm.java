package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;
import static org.lwjgl.opengl.GL11.glNormal3f;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertex3f;

/**
 * The arm of the player under the pot in first person, as if the pot was held on the palm: vanilla draws no arm while
 * an item is held. It comes from the lower corner of the screen on the side of the hand, so in the off hand of
 * Backhand, drawn mirrored, it is the left arm.
 * <p>
 * Drawn in the space of {@link PottedPlantModel}, with the skin of the player bound and GL_TEXTURE_2D enabled. The arm
 * is as wide as the arm of the player model, 4 pixels of the skin, and longer, the skin stretched along it.
 */
final class HoldingArm {

    /** Width of the arm in model units: 4 pixels of the skin. */
    private static final float WIDTH = 0.3F;
    /** Longer than the 12 pixels of the skin, so its end stays below the screen when the view bobs. */
    private static final float LENGTH = WIDTH * 4.5F;
    /** Where the shoulder is in eye space, for the right hand: right of and below the eye. */
    private static final float SHOULDER_X = 0.62F, SHOULDER_Y = -0.95F, SHOULDER_Z = -0.25F;

    private HoldingArm() {}

    static void render() {
        EyeSpace eye = EyeSpace.current();
        float[] shoulder = eye
            .point(eye.mirrored() ? -SHOULDER_X : SHOULDER_X, SHOULDER_Y, SHOULDER_Z);
        // the hand under the middle of the pot, a little toward the shoulder so the palm is seen
        float hx = 0, hy = -0.5F - WIDTH * 0.15F, hz = 0;
        float ux = shoulder[0] - hx, uy = shoulder[1] - hy, uz = shoulder[2] - hz;
        float length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (length == 0) return;
        ux /= length;
        uy /= length;
        uz /= length;
        hx += ux * 0.06F;
        hz += uz * 0.06F;

        glPushMatrix();
        glTranslatef(hx, hy, hz);
        // turn +y to the shoulder, around (0, 1, 0) x u
        float axis = (float) Math.sqrt(uz * uz + ux * ux);
        if (axis > 1e-5F) {
            glRotatef((float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, uy)))), uz / axis, 0, -ux / axis);
        }
        // the outer side of the arm to the viewer
        glRotatef(eye.mirrored() ? -45 : 45, 0, 1, 0);
        drawArm();
        glPopMatrix();
    }

    /** The right arm of the skin along +y, the hand at y = 0. */
    private static void drawArm() {
        // skins are 64 by 32 or 64 by 64 pixels, maybe larger
        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        float pixelsHigh = width > 0 && height > 0 ? 64F * height / width : 32;
        float u = 1 / 64F, v = 1 / pixelsHigh;
        float x0 = -WIDTH / 2, x1 = WIDTH / 2, z0 = -WIDTH / 2, z1 = WIDTH / 2, y0 = 0, y1 = LENGTH;

        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        // the four sides from the shoulder (v = 20) down to the hand (v = 32): -x, +z, +x, -z side by side
        glNormal3f(0, 0, 1);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 44 * u, 48 * u, 20 * v, 32 * v);
        glNormal3f(1, 0, 0);
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 48 * u, 52 * u, 20 * v, 32 * v);
        glNormal3f(0, 0, -1);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 52 * u, 56 * u, 20 * v, 32 * v);
        glNormal3f(-1, 0, 0);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 40 * u, 44 * u, 20 * v, 32 * v);
        // the palm
        glNormal3f(0, -1, 0);
        glTexCoord2f(48 * u, 16 * v);
        glVertex3f(x0, y0, z0);
        glTexCoord2f(52 * u, 16 * v);
        glVertex3f(x1, y0, z0);
        glTexCoord2f(52 * u, 20 * v);
        glVertex3f(x1, y0, z1);
        glTexCoord2f(48 * u, 20 * v);
        glVertex3f(x0, y0, z1);
        glEnd();
    }

    /**
     * A side quad given from its lower left corner counterclockwise, seen from outside: the left edge at u0, the
     * shoulder (top) at v0, the hand (bottom) at v1.
     */
    private static void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
        float dx, float dy, float dz, float u0, float u1, float v0, float v1) {
        glTexCoord2f(u0, v1);
        glVertex3f(ax, ay, az);
        glTexCoord2f(u1, v1);
        glVertex3f(bx, by, bz);
        glTexCoord2f(u1, v0);
        glVertex3f(cx, cy, cz);
        glTexCoord2f(u0, v0);
        glVertex3f(dx, dy, dz);
    }
}
