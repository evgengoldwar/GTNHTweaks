package gtnhtweaks.common.render;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4ub;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/** Untextured immediate mode drawing helpers. GL_TEXTURE_2D must be disabled. */
public final class GLDraw {

    private GLDraw() {}

    public static void color(int argb) {
        glColor4ub((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24));
    }

    public static void rect(float x, float y, float w, float h, int argb) {
        color(argb);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x, y + h);
        glVertex2f(x + w, y + h);
        glVertex2f(x + w, y);
        glEnd();
    }

    public static void verticalGradient(float x, float y, float w, float h, int top, int bottom) {
        glBegin(GL_QUADS);
        color(top);
        glVertex2f(x, y);
        color(bottom);
        glVertex2f(x, y + h);
        glVertex2f(x + w, y + h);
        color(top);
        glVertex2f(x + w, y);
        glEnd();
    }
}
