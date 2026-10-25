package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.modules.extrautilities.client.Shapes.box;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * An iron chain between two points, of links turned by a quarter one after the other, like the chains of newer
 * versions.
 */
final class Chain {

    private static final int IRON = 0x3B3C42;
    private static final int IRON_LIGHT = 0x5A5C66;
    /** Length of a link along the chain, the links going a little into each other, and their width. */
    private static final float LINK = 0.032F, STEP = 0.026F, WIDTH = 0.018F, BAR = 0.005F;

    private Chain() {}

    /** Draws the chain from one point to the other, in the current space. GL_TEXTURE_2D must be disabled. */
    static void draw(float[] from, float[] to) {
        float x = to[0] - from[0], y = to[1] - from[1], z = to[2] - from[2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-4F) return;
        glPushMatrix();
        glTranslatef(from[0], from[1], from[2]);
        // +y along the chain
        float ux = x / length, uy = y / length, uz = z / length;
        float axis = (float) Math.sqrt(ux * ux + uz * uz);
        if (axis > 1e-5F) {
            glRotatef((float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, uy)))), uz / axis, 0, -ux / axis);
        } else if (uy < 0) {
            glRotatef(180, 1, 0, 0);
        }
        int links = Math.max(1, Math.round((length - LINK) / STEP) + 1);
        float step = links > 1 ? (length - LINK) / (links - 1) : 0;
        for (int i = 0; i < links; i++) {
            glPushMatrix();
            glTranslatef(0, i * step, 0);
            if (i % 2 == 1) glRotatef(90, 0, 1, 0);
            int color = i % 2 == 0 ? IRON : IRON_LIGHT;
            float w = WIDTH / 2;
            // an open rectangle: two sides and two ends
            box(-w, 0, -BAR / 2, -w + BAR, LINK, BAR / 2, color);
            box(w - BAR, 0, -BAR / 2, w, LINK, BAR / 2, color);
            box(-w, 0, -BAR / 2, w, BAR, BAR / 2, color);
            box(-w, LINK - BAR, -BAR / 2, w, LINK, BAR / 2, color);
            glPopMatrix();
        }
        glPopMatrix();
    }
}
