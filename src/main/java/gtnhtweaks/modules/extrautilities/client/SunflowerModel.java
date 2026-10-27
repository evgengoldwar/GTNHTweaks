package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.cube;
import static gtnhtweaks.common.model.Shapes.face;
import static gtnhtweaks.common.model.Shapes.frac;
import static gtnhtweaks.common.model.Shapes.letterZ;
import static gtnhtweaks.common.model.Shapes.mix;
import static gtnhtweaks.common.model.Shapes.rand;
import static gtnhtweaks.common.model.Shapes.translucent;
import static gtnhtweaks.modules.extrautilities.client.PottedPlantModel.SOIL_Y;
import static gtnhtweaks.modules.extrautilities.client.PottedPlantModel.STEM;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import gtnhtweaks.common.model.Shapes;

/**
 * A sunflower in the clay pot of {@link PottedPlantModel}: a smiling brown face in a ring of yellow petals, on a stem
 * with two leaves, swaying its head from side to side. Every few seconds its face lights up and it makes a sun, which
 * rises over its head, turning, and fades away. The sun glows: drawn unlit, at full brightness.
 * <p>
 * At night it sleeps with its petals folded and makes no sun, in the rain it is glum and makes fewer, in the heat it
 * beams and makes more.
 */
public final class SunflowerModel implements CompanionModel {

    public static final SunflowerModel INSTANCE = new SunflowerModel();

    private static final int PETAL = 0xFFD23A;
    private static final int PETAL_TIP = 0xF2AE1C;
    private static final int DISK = 0x8A5A2B;
    private static final int DISK_RIM = 0x6E4520;
    private static final int DISK_LIT = 0xC98B3E;
    private static final int BACK = 0x4E9E2F;
    private static final int LEAF = 0x5DBB4A;
    private static final int EYE = 0x1A1A1A;
    private static final int EYE_SHINE = 0xFFFFFF;
    private static final int MOUTH = 0x3A1E10;
    private static final int CHEEK = 0xE9876A;
    private static final int SUN = 0xFFE15A;
    private static final int SUN_CORE = 0xFFF6C8;
    private static final int SUN_RAY = 0xFFB52E;
    private static final int SUN_GLOW = 0xFFD040;
    private static final int SLEEP_Z = 0xE6EEFF;

    private static final int PETALS = 12;
    /** Seconds between suns, and between the extra suns in the heat. */
    private static final double SUN_EVERY = 8, HEAT_SUN_EVERY = 4;
    /** Seconds a sun is seen. */
    private static final double SUN_LIFE = 4.5;

    private SunflowerModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        PottedPlantModel.drawPot();
        float awake = 1 - mood.night;
        // makes suns awake, fewer in the rain
        float sunny = awake * (1 - 0.6F * mood.rain);

        // the stem with its two leaves
        float sway = (float) (3 * Math.sin(t * 1.1)) * awake;
        float droop = 10 * mood.night + 8 * mood.rain;
        glPushMatrix();
        glTranslatef(0, SOIL_Y, 0);
        glRotatef(sway, 0, 0, 1);
        glRotatef(droop * 0.5F, 1, 0, 0);
        box(-0.022F, 0, -0.022F, 0.022F, 0.25F, 0.022F, STEM);
        for (int side = -1; side <= 1; side += 2) {
            glPushMatrix();
            glTranslatef(0, 0.08F + (side > 0 ? 0.04F : 0), 0);
            glRotatef(side > 0 ? 20 : 200, 0, 1, 0);
            glRotatef((float) (30 + 6 * Math.sin(t * 1.7 + side)) - 25 * mood.rain - 15 * mood.night, 0, 0, 1);
            glScalef(1.3F, 1.3F, 1.3F);
            PottedPlantModel.leaf(LEAF);
            glPopMatrix();
        }
        glTranslatef(0, 0.25F, 0);
        glRotatef(droop * 0.5F, 1, 0, 0);
        float glowing = Math.max(sunGlow(t, SUN_EVERY, 0) * sunny, sunGlow(t, HEAT_SUN_EVERY, 0.5) * mood.heat);
        drawHead(t, faceYaw, facePitch, mood, glowing);
        glPopMatrix();

        drawSun(t, seed, SUN_EVERY, 0, sunny);
        drawSun(t, seed + 1, HEAT_SUN_EVERY, 0.5, mood.heat * awake);
    }

    private static void drawHead(double t, float faceYaw, float facePitch, Mood mood, float glowing) {
        float awake = 1 - mood.night;
        glPushMatrix();
        glTranslatef(0, 0.09F, 0);
        glRotatef(faceYaw, 0, 1, 0);
        glRotatef(-facePitch * 0.7F + 14 * mood.night + 10 * mood.rain - 6 * mood.heat, 1, 0, 0);
        // the happy sway of its head from side to side
        glRotatef((float) (9 * Math.sin(t * 2.2)) * awake * (1 - 0.6F * mood.rain), 0, 0, 1);
        float breath = (float) Math.sin(t * 1.1) * 0.03F * mood.night;
        glScalef(1 + breath, 1 + breath, 1);

        // petals round the face, folding in asleep, hanging in the rain
        float fold = 55 * mood.night + 25 * mood.rain;
        for (int i = 0; i < PETALS; i++) {
            float angle = i * 360F / PETALS;
            glPushMatrix();
            glRotatef(angle, 0, 0, 1);
            glTranslatef(0, 0.085F, -0.005F);
            glRotatef(fold + (float) (5 * Math.sin(t * 3 + i)), 1, 0, 0);
            box(-0.03F, 0, -0.008F, 0.03F, 0.05F, 0.008F, PETAL);
            box(-0.022F, 0.05F, -0.007F, 0.022F, 0.075F, 0.007F, PETAL_TIP);
            glPopMatrix();
        }
        // the green back, the brown disk, lighter as a sun is coming
        box(-0.075F, -0.075F, -0.035F, 0.075F, 0.075F, -0.012F, BACK);
        box(-0.09F, -0.09F, -0.015F, 0.09F, 0.09F, 0.02F, DISK_RIM);
        box(-0.075F, -0.075F, 0.02F, 0.075F, 0.075F, 0.028F, mix(DISK, DISK_LIT, glowing));
        drawFace(t, mood, glowing);
        if (mood.night > 0.5F) {
            for (int k = 0; k < 3; k++) {
                double p = frac(t / 2.4 + k / 3.0);
                float s = (float) (0.5 + 0.7 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F) * mood.night;
                glPushMatrix();
                glTranslatef(0.13F + 0.08F * (float) p, 0.14F + 0.25F * (float) p, 0.04F);
                glScalef(s, s, s);
                letterZ(0.035F, 0.035F, 0.009F, SLEEP_Z);
                glPopMatrix();
            }
        }
        glPopMatrix();
    }

    private static void drawFace(double t, Mood mood, float glowing) {
        float z = 0.0285F;
        boolean asleep = mood.night > 0.5F;
        boolean beaming = glowing > 0.3F || mood.heat > 0.5F;
        boolean blink = frac(t / 3.3) < 0.04;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.032F;
            if (asleep || blink) {
                face(ex - 0.016F, 0.014F, ex + 0.016F, 0.021F, z, EYE);
            } else if (beaming) {
                // happy closed eyes: little arcs
                face(ex - 0.014F, 0.022F, ex + 0.014F, 0.029F, z, EYE);
                face(ex - 0.02F, 0.012F, ex - 0.013F, 0.024F, z, EYE);
                face(ex + 0.013F, 0.012F, ex + 0.02F, 0.024F, z, EYE);
            } else {
                face(ex - 0.012F, 0.004F, ex + 0.012F, 0.036F, z, EYE);
                face(ex - 0.009F, 0.022F, ex - 0.002F, 0.031F, z + 0.001F, EYE_SHINE);
            }
            face(side * 0.05F, -0.018F, side * 0.05F + side * 0.018F, -0.006F, z, CHEEK);
        }
        if (asleep) {
            face(-0.008F, -0.04F, 0.008F, -0.028F, z, MOUTH);
        } else if (mood.rain > 0.5F) {
            // glum: a flat little mouth
            face(-0.018F, -0.036F, 0.018F, -0.029F, z, MOUTH);
        } else if (beaming) {
            face(-0.026F, -0.048F, 0.026F, -0.022F, z, MOUTH);
            face(-0.016F, -0.048F, 0.016F, -0.037F, z + 0.001F, 0xD9534F);
        } else {
            face(-0.02F, -0.042F, 0.02F, -0.033F, z, MOUTH);
            face(-0.032F, -0.034F, -0.02F, -0.024F, z, MOUTH);
            face(0.02F, -0.034F, 0.032F, -0.024F, z, MOUTH);
        }
    }

    /** 0, rising to 1 in the second before a sun is made. */
    private static float sunGlow(double t, double every, double shift) {
        double untilSun = (1 - frac(t / every + shift)) * every;
        return untilSun < 1 ? (float) (1 - untilSun) : 0;
    }

    /** A sun rising over the head, turning, and fading away. */
    private static void drawSun(double t, int seed, double every, double shift, float strength) {
        if (strength < 0.05F) return;
        double age = frac(t / every + shift) * every;
        if (age > SUN_LIFE) return;
        double p = age / SUN_LIFE;
        // pops out, rises, floats a little to the side, fades
        float size = strength * (float) Math.min(1, age / 0.3) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
        float side = (float) (rand(seed, (int) Math.floor(t / every + shift)) - 0.5) * 0.3F;
        float y = 0.36F + 0.16F * (float) Math.sqrt(p);
        glPushMatrix();
        glTranslatef(side * (float) p, y, 0.02F);
        glRotatef((float) (age * 60), 0, 1, 0);
        glRotatef((float) (age * 40), 0, 0, 1);
        glScalef(size, size, size);
        boolean lit = Shapes.glow();
        cube(0.05F, 0.05F, 0.05F, SUN);
        cube(0.03F, 0.03F, 0.055F, SUN_CORE);
        for (int r = 0; r < 8; r++) {
            glPushMatrix();
            glRotatef(r * 45, 0, 0, 1);
            glTranslatef(0, 0.075F + 0.008F * (float) Math.sin(t * 6 + r), 0);
            cube(0.012F, 0.018F, 0.012F, SUN_RAY);
            glPopMatrix();
        }
        translucent(true);
        Shapes.box(-0.08F, -0.08F, -0.08F, 0.08F, 0.08F, 0.08F, SUN_GLOW, 0.25F);
        translucent(false);
        Shapes.endGlow(lit);
        glPopMatrix();
    }
}
