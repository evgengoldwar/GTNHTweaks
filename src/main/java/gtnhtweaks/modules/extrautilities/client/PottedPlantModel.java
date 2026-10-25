package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.modules.extrautilities.client.Shapes.box;
import static gtnhtweaks.modules.extrautilities.client.Shapes.color;
import static gtnhtweaks.modules.extrautilities.client.Shapes.easeOut;
import static gtnhtweaks.modules.extrautilities.client.Shapes.face;
import static gtnhtweaks.modules.extrautilities.client.Shapes.letterZ;
import static gtnhtweaks.modules.extrautilities.client.Shapes.mix;
import static gtnhtweaks.modules.extrautilities.client.Shapes.pulse;
import static gtnhtweaks.modules.extrautilities.client.Shapes.rand;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * A clay pot with a living plant in it, drawn with untextured boxes. The plant sways, breathes, blinks, smiles at the
 * viewer and now and then does a happy little shake; its leaves grow, turn yellow, fall off beside the pot and grow
 * again.
 * <p>
 * It feels where it is ({@link Mood}): it sleeps at night, is happy in the rain and wilts in the heat.
 * <p>
 * Everything is a function of the time, so nothing has to be kept between frames: two plants with other seeds just
 * live out of step. The model fills the unit cube centered on the origin, the pot standing on y = -0.5, like a block
 * drawn as an item.
 */
public final class PottedPlantModel implements CompanionModel {

    private static final int POT = 0xC0653A;
    private static final int POT_FOOT = 0xA9552F;
    private static final int POT_BAND = 0xD9895C;
    private static final int RIM = 0xCF7446;
    private static final int SOIL = 0x4A2F1F;
    private static final int SOIL_DARK = 0x372215;
    static final int STEM = 0x3E8E3A;
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
    private static final int WILTED = 0xA39C45;
    private static final int SLEEP_Z = 0xE6EEFF;
    private static final int WATER = 0x7FB8FF;

    static final float SOIL_Y = -0.15F;
    private static final float SEGMENT = 0.11F;
    private static final int SEGMENTS = 3;
    private static final float GROUND_Y = -0.5F;

    /** Leaves on the stem: where along the stem (in segments) and which way they point (degrees around y). */
    private static final float[] LEAF_AT = { 0.55F, 1.05F, 1.6F, 2.15F, 2.55F };
    private static final float[] LEAF_YAW = { 15F, 195F, 80F, 260F, 140F };
    /** Parts of the life of a leaf: growing, then green, then turning yellow, then falling. */
    private static final double GROWN = 0.12, YELLOWING = 0.7, FALLING = 0.82;
    private static final float LEAF_SIZE = 1.35F;

    public static final PottedPlantModel INSTANCE = new PottedPlantModel();

    private PottedPlantModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        drawPot();

        float[][] stem = stem(t, mood);
        for (int i = 0; i < SEGMENTS; i++) {
            glPushMatrix();
            glTranslatef(stem[i][0], stem[i][1], stem[i][2]);
            glRotatef(stem[i][3], 0, 0, 1);
            glRotatef(stem[i][4], 1, 0, 0);
            box(-0.022F, -0.01F, -0.022F, 0.022F, SEGMENT + 0.01F, 0.022F, mix(STEM, WILTED, mood.heat * 0.5F));
            glPopMatrix();
        }

        for (int i = 0; i < LEAF_AT.length; i++) drawLeaf(t, seed, i, stem, mood);
        drawHead(t, stem[SEGMENTS], faceYaw, facePitch, mood);
        if (mood.rain > 0) drawRain(t, seed, mood.rain);
    }

    // ---- pot ----

    /** The clay pot with its soil, the top of the soil at {@link #SOIL_Y}. Also the pot of the peashooter. */
    static void drawPot() {
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
    private static float[][] stem(double t, Mood mood) {
        float[][] joints = new float[SEGMENTS + 1][5];
        float shake = shake(t, mood);
        // asleep it sways less and nods, in the rain more; wilting it bends to the side and forward
        float sway = 1 - 0.6F * mood.night - 0.5F * mood.heat + 0.3F * mood.rain;
        float bendRoll = 9 * mood.heat;
        float bendPitch = 6 * mood.heat + mood.night * (float) (3 + 2 * Math.sin(t * 0.8));
        float x = 0, y = SOIL_Y, z = 0, roll = 0, pitch = 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            roll += (float) (sway * 4.5 * Math.sin(t * 1.3 + i * 0.6) + shake * 10 * Math.sin(t * 17 + i * 0.8))
                + bendRoll;
            pitch += (float) (sway * 3.0 * Math.sin(t * 0.9 + 1 + i * 0.8) + shake * 4 * Math.sin(t * 13 + i))
                + bendPitch;
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

    /** 0, or up to 1 during the happy shake the plant does now and then, and more often in the rain. */
    private static float shake(double t, Mood mood) {
        float calm = pulse(t, 9.5, 1.4) * (1 - mood.night) * (1 - mood.heat);
        return Math.max(calm, pulse(t + 2, 4.1, 1.2) * mood.rain);
    }

    // ---- leaves ----

    private static void drawLeaf(double t, int seed, int i, float[][] stem, Mood mood) {
        double period = 10 + 8 * rand(seed, i + 1);
        double shifted = t + rand(seed, i + 20) * period;
        double life = ((shifted / period) % 1 + 1) % 1;
        // up in the rain, hanging asleep and more so wilting
        float tilt = (float) (28 + 7 * Math.sin(t * 2.1 + i * 1.7)) + 10 * mood.rain - 15 * mood.night - 45 * mood.heat;

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
            leaf(mix(mix(LEAF, WILTED, mood.heat * 0.6F), LEAF_DRY, dry * 0.85F));
            glPopMatrix();
            return;
        }

        // falling: from where it hung when it came off, outward and down to the ground beside the pot
        float fall = (float) ((life - FALLING) / (1 - FALLING));
        double detached = t - (life - FALLING) * period;
        float[] from = alongStem(stem(detached, mood), LEAF_AT[i]);
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
    static void leaf(int color) {
        box(0, -0.007F, -0.01F, 0.03F, 0.007F, 0.01F, mix(color, STEM, 0.4F));
        box(0.025F, -0.006F, -0.032F, 0.115F, 0.006F, 0.032F, color);
        box(0.04F, -0.006F, -0.04F, 0.095F, 0.006F, 0.04F, color);
        box(0.115F, -0.005F, -0.018F, 0.15F, 0.005F, 0.018F, color);
        box(0.025F, 0.006F, -0.004F, 0.13F, 0.009F, 0.004F, mix(color, LEAF_RIB, 0.7F));
    }

    // ---- head ----

    private static void drawHead(double t, float[] top, float faceYaw, float facePitch, Mood mood) {
        // asleep it breathes slower and deeper: the slow breath is mixed in, not the speed changed, so nothing jumps
        float breath = (float) (Math.sin(t * 2.2) * 0.025 * (1 - mood.night) + Math.sin(t * 1.1) * 0.045 * mood.night);
        float shake = shake(t, mood);
        glPushMatrix();
        glTranslatef(top[0], top[1], top[2]);
        // the head follows the stem only half way, to stay upright
        glRotatef(top[3] * 0.5F, 0, 0, 1);
        glRotatef(top[4] * 0.5F, 1, 0, 0);
        glTranslatef(0, 0.11F, 0);
        glRotatef(faceYaw + (float) (8 * Math.sin(t * 0.7)), 0, 1, 0);
        glRotatef(-facePitch, 1, 0, 0);
        // drooping asleep and wilting, looking up at the rain
        glRotatef(18 * mood.night + 22 * mood.heat - 15 * mood.rain, 1, 0, 0);
        glRotatef(14 * mood.heat, 0, 0, 1);
        glRotatef(shake * 8 * (float) Math.sin(t * 20), 0, 0, 1);
        glScalef(1 + breath, 1 - breath, 1 + breath);

        int head = mix(HEAD, WILTED, mood.heat * 0.5F);
        box(-0.11F, -0.1F, -0.08F, 0.11F, 0.1F, 0.08F, head);
        box(-0.085F, 0.1F, -0.06F, 0.085F, 0.125F, 0.06F, mix(HEAD_TOP, WILTED, mood.heat * 0.5F));
        box(-0.08F, -0.12F, -0.055F, 0.08F, -0.1F, 0.055F, head);
        box(-0.125F, -0.07F, -0.06F, -0.11F, 0.07F, 0.06F, head);
        box(0.11F, -0.07F, -0.06F, 0.125F, 0.07F, 0.06F, head);
        drawSprout(t, mood);
        drawFace(t, shake, mood);
        if (mood.night > 0) drawSleep(t, mood.night);
        if (mood.heat > 0) drawSweat(t, mood.heat);
        glPopMatrix();
    }

    /** Two little leaves on top of the head. */
    private static void drawSprout(double t, Mood mood) {
        box(-0.01F, 0.125F, -0.01F, 0.01F, 0.16F, 0.01F, STEM);
        for (int side = -1; side <= 1; side += 2) {
            glPushMatrix();
            glTranslatef(0, 0.155F, 0);
            glRotatef(side == 1 ? 0 : 180, 0, 1, 0);
            glRotatef((float) (35 + 12 * Math.sin(t * 3 + side)) - 50 * mood.heat - 20 * mood.night, 0, 0, 1);
            glScalef(0.6F, 0.6F, 0.6F);
            leaf(mix(LEAF, WILTED, mood.heat * 0.6F));
            glPopMatrix();
        }
    }

    private static void drawFace(double t, float shake, Mood mood) {
        float z = 0.081F;
        boolean asleep = mood.night > 0.5F;
        boolean sad = mood.heat > 0.5F;
        boolean happy = shake > 0.2F || mood.rain > 0.5F;
        // blinks every few seconds, the time between them changing a little
        double blinkPeriod = 3.7;
        double sinceBlink = ((t + 0.6 * Math.sin(t * 0.31)) % blinkPeriod + blinkPeriod) % blinkPeriod;
        boolean blink = sinceBlink < 0.14;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.045F;
            if (asleep) {
                // closed, hanging a little
                face(ex - 0.018F, 0.012F, ex + 0.018F, 0.02F, z, EYE);
            } else if (happy && !sad) {
                // closed with joy: little arcs
                face(ex - 0.018F, 0.018F, ex + 0.018F, 0.026F, z, EYE);
                face(ex - 0.026F, 0.006F, ex - 0.016F, 0.02F, z, EYE);
                face(ex + 0.016F, 0.006F, ex + 0.026F, 0.02F, z, EYE);
            } else if (blink) {
                face(ex - 0.018F, 0.018F, ex + 0.018F, 0.026F, z, EYE);
            } else if (sad) {
                // half closed, under brows going up to the middle
                face(ex - 0.016F, 0.0F, ex + 0.016F, 0.028F, z, EYE);
                face(ex - side * 0.004F, 0.042F, ex - side * 0.022F, 0.05F, z, EYE);
                face(ex + side * 0.004F, 0.036F, ex + side * 0.02F, 0.044F, z, EYE);
            } else {
                face(ex - 0.016F, 0.0F, ex + 0.016F, 0.045F, z, EYE);
                face(ex - 0.012F, 0.027F, ex - 0.002F, 0.04F, z + 0.001F, EYE_SHINE);
            }
            if (!sad) face(side * 0.068F, -0.032F, side * 0.068F + side * 0.03F, -0.015F, z, CHEEK);
        }
        if (asleep) {
            // a little open mouth, breathing
            face(-0.012F, -0.06F, 0.012F, -0.04F, z, MOUTH);
        } else if (sad) {
            // a frown: the smile upside down
            face(-0.028F, -0.045F, 0.028F, -0.032F, z, MOUTH);
            face(-0.046F, -0.06F, -0.028F, -0.044F, z, MOUTH);
            face(0.028F, -0.06F, 0.046F, -0.044F, z, MOUTH);
        } else if (happy) {
            // laughing: an open mouth
            face(-0.035F, -0.075F, 0.035F, -0.035F, z, MOUTH);
            face(-0.022F, -0.075F, 0.022F, -0.058F, z + 0.001F, TONGUE);
        } else {
            face(-0.028F, -0.06F, 0.028F, -0.045F, z, MOUTH);
            face(-0.046F, -0.048F, -0.028F, -0.032F, z, MOUTH);
            face(0.028F, -0.048F, 0.046F, -0.032F, z, MOUTH);
        }
    }

    /** Letters Z rising from the sleeping head, one after the other, growing then fading away. */
    private static void drawSleep(double t, float sleep) {
        for (int k = 0; k < 3; k++) {
            double p = ((t / 2.4 + k / 3.0) % 1 + 1) % 1;
            float size = sleep * (float) ((0.5 + 0.7 * p) * (p < 0.75 ? 1 : (1 - p) / 0.25));
            glPushMatrix();
            glTranslatef(0.13F + 0.1F * (float) p, 0.12F + 0.28F * (float) p, 0.02F);
            glRotatef((float) (10 * Math.sin(t * 2 + k)), 0, 0, 1);
            glScalef(size, size, size);
            letterZ(0.035F, 0.035F, 0.009F, SLEEP_Z);
            glPopMatrix();
        }
    }

    /** A drop of sweat running down the side of the wilting head. */
    private static void drawSweat(double t, float wilt) {
        double p = ((t / 1.8) % 1 + 1) % 1;
        float size = wilt * (p < 0.8 ? 1 : (float) (1 - p) / 0.2F);
        glPushMatrix();
        glTranslatef(0.105F, 0.06F - 0.12F * (float) (p * p), 0.06F);
        glScalef(size * 1.5F, size * 1.5F, size * 1.5F);
        box(-0.01F, -0.016F, -0.008F, 0.01F, 0.008F, 0.008F, WATER);
        box(-0.005F, 0.008F, -0.005F, 0.005F, 0.018F, 0.005F, WATER);
        glPopMatrix();
    }

    /** Drops of rain falling on the plant and splashing on the soil. */
    private static void drawRain(double t, int seed, float joy) {
        for (int i = 0; i < 10; i++) {
            double period = 0.8 + 0.4 * rand(seed, 40 + i);
            double p = (((t + rand(seed, 50 + i) * period) / period) % 1 + 1) % 1;
            float x = (float) (rand(seed, 60 + i) - 0.5) * 0.4F;
            float z = (float) (rand(seed, 70 + i) - 0.5) * 0.4F;
            glPushMatrix();
            if (p < 0.85) {
                glTranslatef(x, 0.62F - (0.62F - SOIL_Y) * (float) (p / 0.85), z);
                glScalef(joy, joy, joy);
                box(-0.01F, -0.035F, -0.01F, 0.01F, 0.035F, 0.01F, WATER);
            } else {
                // the splash spreads on the soil
                float splash = joy * (float) (0.5 + (p - 0.85) / 0.15);
                glTranslatef(x, SOIL_Y, z);
                glScalef(splash, joy, splash);
                box(-0.03F, 0, -0.03F, 0.03F, 0.008F, 0.03F, WATER);
            }
            glPopMatrix();
        }
    }
}
