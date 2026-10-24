package gtnhtweaks.modules.extrautilities.client;

import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4ub;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.glNormal3f;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertex3f;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

/**
 * A clay pot with a living plant in it, drawn with untextured boxes. The plant sways, breathes, blinks, smiles at the
 * viewer and now and then does a happy little shake; its leaves grow, turn yellow, fall off beside the pot and grow
 * again.
 * <p>
 * Everything is a function of the time, so nothing has to be kept between frames: two plants with other seeds just
 * live out of step. The model fills the unit cube centered on the origin, the pot standing on y = -0.5, like a block
 * drawn as an item. GL_TEXTURE_2D must be disabled; the normals are set for the item lighting.
 */
public final class PottedPlantModel {

    private static final int POT = 0xC0653A;
    private static final int POT_FOOT = 0xA9552F;
    private static final int POT_BAND = 0xD9895C;
    private static final int RIM = 0xCF7446;
    private static final int SOIL = 0x4A2F1F;
    private static final int SOIL_DARK = 0x372215;
    private static final int STEM = 0x3E8E3A;
    private static final int LEAF = 0x5DBB4A;
    private static final int LEAF_RIB = 0x3F8F35;
    private static final int LEAF_DRY = 0xD1A73A;
    private static final int HEAD = 0x7ACB5A;
    private static final int HEAD_TOP = 0x67B84A;
    private static final int EYE = 0x1E1E1E;
    private static final int EYE_SHINE = 0xFFFFFF;
    private static final int CHEEK = 0xF0959F;
    private static final int MOUTH = 0x3A1E1E;
    private static final int TONGUE = 0xD9534F;

    private static final float SOIL_Y = -0.15F;
    private static final float SEGMENT = 0.11F;
    private static final int SEGMENTS = 3;
    private static final float GROUND_Y = -0.5F;

    /** Leaves on the stem: where along the stem (in segments) and which way they point (degrees around y). */
    private static final float[] LEAF_AT = { 0.55F, 1.05F, 1.6F, 2.15F, 2.55F };
    private static final float[] LEAF_YAW = { 15F, 195F, 80F, 260F, 140F };
    /** Parts of the life of a leaf: growing, then green, then turning yellow, then falling. */
    private static final double GROWN = 0.12, YELLOWING = 0.7, FALLING = 0.82;
    private static final float LEAF_SIZE = 1.35F;

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private PottedPlantModel() {}

    /**
     * Draws the plant with its face turned to the viewer, found from the current modelview matrix.
     *
     * @param time      seconds
     * @param seed      plants with other seeds live out of step
     * @param towardEye true: the face turns to the eye, as seen in perspective, so a plant in the right hand looks to
     *                  the left and one in the left hand to the right; false: it faces the screen, for the inventory
     */
    public static void render(double time, int seed, boolean towardEye) {
        MATRIX.clear();
        glGetFloat(GL_MODELVIEW_MATRIX, MATRIX);
        // the direction to the viewer in eye space: to the eye at the origin, or +z
        float vx = 0, vy = 0, vz = 1;
        if (towardEye) {
            vx = -MATRIX.get(12);
            vy = -MATRIX.get(13);
            vz = -MATRIX.get(14);
        }
        // the same direction in model space, through the inverse of the linear part, whose columns are
        // (row 1 x row 2, row 2 x row 0, row 0 x row 1) / determinant
        float a = MATRIX.get(0), b = MATRIX.get(4), c = MATRIX.get(8);
        float d = MATRIX.get(1), e = MATRIX.get(5), f = MATRIX.get(9);
        float g = MATRIX.get(2), h = MATRIX.get(6), k = MATRIX.get(10);
        float x = vx * (e * k - f * h) + vy * (h * c - k * b) + vz * (b * f - c * e);
        float y = vx * (f * g - d * k) + vy * (k * a - g * c) + vz * (c * d - a * f);
        float z = vx * (d * h - e * g) + vy * (g * b - h * a) + vz * (a * e - b * d);
        float det = a * (e * k - f * h) + b * (f * g - d * k) + c * (d * h - e * g);
        if (det < 0) {
            x = -x;
            y = -y;
            z = -z;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(x, z));
        float pitch = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
        render(time, seed, yaw, Math.max(-25, Math.min(25, pitch * 0.5F)));
    }

    /**
     * @param time      seconds
     * @param seed      plants with other seeds live out of step
     * @param faceYaw   degrees around y that turn the face (drawn toward +z) to the viewer
     * @param facePitch degrees the face tilts up to the viewer
     */
    public static void render(double time, int seed, float faceYaw, float facePitch) {
        double t = time + rand(seed, 0) * 1000;
        drawPot();

        float[][] stem = stem(t);
        for (int i = 0; i < SEGMENTS; i++) {
            glPushMatrix();
            glTranslatef(stem[i][0], stem[i][1], stem[i][2]);
            glRotatef(stem[i][3], 0, 0, 1);
            glRotatef(stem[i][4], 1, 0, 0);
            box(-0.022F, -0.01F, -0.022F, 0.022F, SEGMENT + 0.01F, 0.022F, STEM);
            glPopMatrix();
        }

        for (int i = 0; i < LEAF_AT.length; i++) drawLeaf(t, seed, i, stem);
        drawHead(t, stem[SEGMENTS], faceYaw, facePitch);
    }

    // ---- pot ----

    private static void drawPot() {
        box(-0.17F, -0.5F, -0.17F, 0.17F, -0.44F, 0.17F, POT_FOOT);
        box(-0.2F, -0.44F, -0.2F, 0.2F, -0.22F, 0.2F, POT);
        box(-0.205F, -0.36F, -0.205F, 0.205F, -0.33F, 0.205F, POT_BAND);
        // the rim around the soil
        box(-0.25F, -0.22F, 0.205F, 0.25F, -0.12F, 0.25F, RIM);
        box(-0.25F, -0.22F, -0.25F, 0.25F, -0.12F, -0.205F, RIM);
        box(0.205F, -0.22F, -0.205F, 0.25F, -0.12F, 0.205F, RIM);
        box(-0.25F, -0.22F, -0.205F, -0.205F, -0.12F, 0.205F, RIM);
        box(-0.205F, -0.22F, -0.205F, 0.205F, SOIL_Y, 0.205F, SOIL);
        // crumbs of soil
        box(-0.13F, SOIL_Y, 0.06F, -0.09F, SOIL_Y + 0.012F, 0.1F, SOIL_DARK);
        box(0.08F, SOIL_Y, -0.12F, 0.12F, SOIL_Y + 0.012F, -0.08F, SOIL_DARK);
        box(0.1F, SOIL_Y, 0.09F, 0.13F, SOIL_Y + 0.01F, 0.12F, SOIL_DARK);
    }

    // ---- stem ----

    /**
     * The joints of the stem at the given time: for each joint its position and the angles (around z, then x) of the
     * segment starting there. The last entry is the top of the stem.
     */
    private static float[][] stem(double t) {
        float[][] joints = new float[SEGMENTS + 1][5];
        float shake = shake(t);
        float x = 0, y = SOIL_Y, z = 0, roll = 0, pitch = 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            roll += (float) (4.5 * Math.sin(t * 1.3 + i * 0.6) + shake * 10 * Math.sin(t * 17 + i * 0.8));
            pitch += (float) (3.0 * Math.sin(t * 0.9 + 1 + i * 0.8) + shake * 4 * Math.sin(t * 13 + i));
            joints[i][0] = x;
            joints[i][1] = y;
            joints[i][2] = z;
            joints[i][3] = roll;
            joints[i][4] = pitch;
            float[] d = direction(roll, pitch, SEGMENT);
            x += d[0];
            y += d[1];
            z += d[2];
        }
        return joints;
    }

    /** Where (0, length, 0) goes after glRotatef(roll, 0, 0, 1) then glRotatef(pitch, 1, 0, 0). */
    private static float[] direction(float roll, float pitch, float length) {
        double r = Math.toRadians(roll), p = Math.toRadians(pitch);
        double up = length * Math.cos(p);
        return new float[] { (float) (-up * Math.sin(r)), (float) (up * Math.cos(r)), (float) (length * Math.sin(p)) };
    }

    /** A point at the given distance along the stem, in segments. */
    private static float[] alongStem(float[][] stem, float at) {
        int i = Math.min((int) at, SEGMENTS - 1);
        float[] d = direction(stem[i][3], stem[i][4], SEGMENT * (at - i));
        return new float[] { stem[i][0] + d[0], stem[i][1] + d[1], stem[i][2] + d[2], stem[i][3], stem[i][4] };
    }

    /** 0, or up to 1 during the happy shake the plant does now and then. */
    private static float shake(double t) {
        double period = 9.5;
        double u = ((t % period) + period) % period;
        double length = 1.4;
        return u < length ? (float) Math.sin(Math.PI * u / length) : 0;
    }

    // ---- leaves ----

    private static void drawLeaf(double t, int seed, int i, float[][] stem) {
        double period = 10 + 8 * rand(seed, i + 1);
        double shifted = t + rand(seed, i + 20) * period;
        double life = ((shifted / period) % 1 + 1) % 1;
        float tilt = (float) (28 + 7 * Math.sin(t * 2.1 + i * 1.7));

        if (life < FALLING) {
            float[] at = alongStem(stem, LEAF_AT[i]);
            float grow = life < GROWN ? (float) easeOut(life / GROWN) : 1;
            float dry = life > YELLOWING ? (float) ((life - YELLOWING) / (FALLING - YELLOWING)) : 0;
            glPushMatrix();
            glTranslatef(at[0], at[1], at[2]);
            glRotatef(at[3], 0, 0, 1);
            glRotatef(at[4], 1, 0, 0);
            glRotatef(LEAF_YAW[i], 0, 1, 0);
            // a dry leaf hangs lower
            glRotatef(tilt - dry * 25, 0, 0, 1);
            glScalef(grow * LEAF_SIZE, grow * LEAF_SIZE, grow * LEAF_SIZE);
            leaf(mix(LEAF, LEAF_DRY, dry * 0.85F));
            glPopMatrix();
            return;
        }

        // falling: from where it hung when it came off, outward and down to the ground beside the pot
        float fall = (float) ((life - FALLING) / (1 - FALLING));
        double detached = t - (life - FALLING) * period;
        float[] from = alongStem(stem(detached), LEAF_AT[i]);
        double yaw = Math.toRadians(LEAF_YAW[i]);
        // first away from the stem, over the rim, then down
        float out = 0.34F * (float) easeOut(Math.min(1, fall * 2.5));
        float swing = (float) Math.sin(fall * Math.PI * 3 + i);
        float side = 0.04F * swing * fall;
        float x = from[0] + (float) (Math.cos(yaw) * out + Math.sin(yaw) * side);
        float z = from[2] + (float) (-Math.sin(yaw) * out + Math.cos(yaw) * side);
        float y = from[1] + (GROUND_Y + 0.02F - from[1]) * (float) Math.pow(fall, 1.8);
        float size = LEAF_SIZE * (fall < 0.8F ? 1 : 1 - (fall - 0.8F) / 0.2F);
        glPushMatrix();
        glTranslatef(x, y, z);
        glRotatef(LEAF_YAW[i], 0, 1, 0);
        glRotatef(tilt * (1 - fall) - 25 * (1 - fall), 0, 0, 1);
        glRotatef(35 * swing * fall, 1, 0, 0);
        glScalef(size, size, size);
        leaf(mix(LEAF_DRY, 0xB07A2E, fall));
        glPopMatrix();
    }

    /** A leaf lying flat, from its stalk at the origin along +x. */
    private static void leaf(int color) {
        box(0, -0.007F, -0.01F, 0.03F, 0.007F, 0.01F, mix(color, STEM, 0.4F));
        box(0.025F, -0.006F, -0.032F, 0.115F, 0.006F, 0.032F, color);
        box(0.04F, -0.006F, -0.04F, 0.095F, 0.006F, 0.04F, color);
        box(0.115F, -0.005F, -0.018F, 0.15F, 0.005F, 0.018F, color);
        box(0.025F, 0.006F, -0.004F, 0.13F, 0.009F, 0.004F, mix(color, LEAF_RIB, 0.7F));
    }

    // ---- head ----

    private static void drawHead(double t, float[] top, float faceYaw, float facePitch) {
        float breath = (float) Math.sin(t * 2.2) * 0.025F;
        float shake = shake(t);
        glPushMatrix();
        glTranslatef(top[0], top[1], top[2]);
        // the head follows the stem only half way, to stay upright
        glRotatef(top[3] * 0.5F, 0, 0, 1);
        glRotatef(top[4] * 0.5F, 1, 0, 0);
        glTranslatef(0, 0.11F, 0);
        glRotatef(faceYaw + (float) (8 * Math.sin(t * 0.7)), 0, 1, 0);
        glRotatef(-facePitch, 1, 0, 0);
        glRotatef(shake * 8 * (float) Math.sin(t * 20), 0, 0, 1);
        glScalef(1 + breath, 1 - breath, 1 + breath);

        box(-0.11F, -0.1F, -0.08F, 0.11F, 0.1F, 0.08F, HEAD);
        box(-0.085F, 0.1F, -0.06F, 0.085F, 0.125F, 0.06F, HEAD_TOP);
        box(-0.08F, -0.12F, -0.055F, 0.08F, -0.1F, 0.055F, HEAD);
        box(-0.125F, -0.07F, -0.06F, -0.11F, 0.07F, 0.06F, HEAD);
        box(0.11F, -0.07F, -0.06F, 0.125F, 0.07F, 0.06F, HEAD);
        drawSprout(t);
        drawFace(t, shake);
        glPopMatrix();
    }

    /** Two little leaves on top of the head. */
    private static void drawSprout(double t) {
        box(-0.01F, 0.125F, -0.01F, 0.01F, 0.16F, 0.01F, STEM);
        for (int side = -1; side <= 1; side += 2) {
            glPushMatrix();
            glTranslatef(0, 0.155F, 0);
            glRotatef(side == 1 ? 0 : 180, 0, 1, 0);
            glRotatef((float) (35 + 12 * Math.sin(t * 3 + side)), 0, 0, 1);
            glScalef(0.6F, 0.6F, 0.6F);
            leaf(LEAF);
            glPopMatrix();
        }
    }

    private static void drawFace(double t, float shake) {
        float z = 0.081F;
        // blinks every few seconds, the time between them changing a little
        double blinkPeriod = 3.7;
        double sinceBlink = ((t + 0.6 * Math.sin(t * 0.31)) % blinkPeriod + blinkPeriod) % blinkPeriod;
        boolean blink = sinceBlink < 0.14 || shake > 0.2F;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.045F;
            if (blink) {
                // closed eyes: little arcs, happy when shaking
                face(ex - 0.018F, 0.018F, ex + 0.018F, 0.026F, z, EYE);
                if (shake > 0.2F) {
                    face(ex - 0.026F, 0.006F, ex - 0.016F, 0.02F, z, EYE);
                    face(ex + 0.016F, 0.006F, ex + 0.026F, 0.02F, z, EYE);
                }
            } else {
                face(ex - 0.016F, 0.0F, ex + 0.016F, 0.045F, z, EYE);
                face(ex - 0.012F, 0.027F, ex - 0.002F, 0.04F, z + 0.001F, EYE_SHINE);
            }
            face(side * 0.068F, -0.032F, side * 0.068F + side * 0.03F, -0.015F, z, CHEEK);
        }
        if (shake > 0.2F) {
            // laughing: an open mouth
            face(-0.035F, -0.075F, 0.035F, -0.035F, z, MOUTH);
            face(-0.022F, -0.075F, 0.022F, -0.058F, z + 0.001F, TONGUE);
        } else {
            face(-0.028F, -0.06F, 0.028F, -0.045F, z, MOUTH);
            face(-0.046F, -0.048F, -0.028F, -0.032F, z, MOUTH);
            face(0.028F, -0.048F, 0.046F, -0.032F, z, MOUTH);
        }
    }

    /** A flat rectangle facing +z. Works with x0 > x1 too. */
    private static void face(float x0, float y0, float x1, float y1, float z, int color) {
        if (x0 > x1) {
            float swap = x0;
            x0 = x1;
            x1 = swap;
        }
        color(color);
        glBegin(GL_QUADS);
        glNormal3f(0, 0, 1);
        glVertex3f(x0, y0, z);
        glVertex3f(x1, y0, z);
        glVertex3f(x1, y1, z);
        glVertex3f(x0, y1, z);
        glEnd();
    }

    // ---- helpers ----

    /** A box with outward facing quads and their normals. */
    private static void box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        color(color);
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

    private static void color(int rgb) {
        glColor4ub((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb, (byte) 255);
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return r << 16 | g << 8 | bl;
    }

    private static double easeOut(double f) {
        return 1 - (1 - f) * (1 - f);
    }

    /** A number in [0, 1) that only depends on its arguments. */
    private static double rand(int seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
