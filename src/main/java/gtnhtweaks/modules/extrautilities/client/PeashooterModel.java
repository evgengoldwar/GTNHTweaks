package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.modules.extrautilities.client.PottedPlantModel.SOIL_Y;
import static gtnhtweaks.modules.extrautilities.client.PottedPlantModel.STEM;
import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.cube;
import static gtnhtweaks.common.model.Shapes.face;
import static gtnhtweaks.common.model.Shapes.frac;
import static gtnhtweaks.common.model.Shapes.letterZ;
import static gtnhtweaks.common.model.Shapes.mix;
import static gtnhtweaks.common.model.Shapes.rand;
import static gtnhtweaks.common.model.Shapes.translucent;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import gtnhtweaks.common.model.Shapes;

/**
 * A peashooter in the clay pot of {@link PottedPlantModel}: a round head with a tube for a mouth, big eyes and a tuft
 * of leaves at the back, on a thin stem over a rosette of leaves. It looks at the viewer three quarters on, so the tube
 * is seen side on, and every few seconds shoots a pea: the head jerks back, the tube puffs out, the pea flies off and a
 * little cloud bursts at the mouth.
 * <p>
 * At night it sleeps and does not shoot, in the rain it shoots faster and is happy, in the heat it wilts and its peas
 * come out small and slow.
 */
public final class PeashooterModel implements CompanionModel {

    public static final PeashooterModel INSTANCE = new PeashooterModel();

    private static final int HEAD = 0x7CD14A;
    private static final int HEAD_DARK = 0x5DB33A;
    private static final int TUBE_RIM = 0x4E9E2F;
    private static final int HOLE = 0x173A10;
    private static final int TUFT = 0x3F8F2E;
    private static final int LEAF = 0x5DBB4A;
    private static final int WILTED = 0xA39C45;
    private static final int PEA = 0x8BE04E;
    private static final int PEA_LIGHT = 0xC4F59A;
    private static final int PUFF = 0xD8F7C4;
    private static final int EYE = 0x1A1A1A;
    private static final int EYE_SHINE = 0xFFFFFF;
    private static final int SLEEP_Z = 0xE6EEFF;

    /** The head turns from the viewer by so much, so the tube is seen from the side. */
    private static final float THREE_QUARTERS = 55;
    /** Seconds between shots, and between the extra shots in the rain. */
    private static final double SHOT_EVERY = 3.2, RAIN_SHOT_EVERY = 1.6;
    /** Seconds a pea flies, and how far it gets. */
    private static final double FLIGHT = 0.7;
    private static final float RANGE = 0.55F;

    private PeashooterModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        PottedPlantModel.drawPot();
        float awake = 1 - mood.night;
        int green = mix(HEAD, WILTED, mood.heat * 0.5F);

        // the rosette of leaves on the soil, lying flat, drooping in the heat
        for (int i = 0; i < 4; i++) {
            glPushMatrix();
            glTranslatef(0, SOIL_Y + 0.01F, 0);
            glRotatef(45 + i * 90 + 6 * (float) Math.sin(t * 0.8 + i), 0, 1, 0);
            glRotatef(12 - 20 * mood.heat + 4 * (float) Math.sin(t * 1.3 + i * 2), 0, 0, 1);
            glScalef(1.3F, 1.3F, 1.3F);
            PottedPlantModel.leaf(mix(LEAF, WILTED, mood.heat * 0.6F));
            glPopMatrix();
        }

        // the stem, swaying, bending asleep and in the heat
        float sway = (float) (4 * Math.sin(t * 1.1)) * awake + 10 * mood.heat;
        float nod = (float) (3 * Math.sin(t * 0.9)) + 12 * mood.night + 8 * mood.heat;
        glPushMatrix();
        glTranslatef(0, SOIL_Y, 0);
        glRotatef(sway, 0, 0, 1);
        glRotatef(nod * 0.5F, 1, 0, 0);
        box(-0.02F, 0, -0.02F, 0.02F, 0.24F, 0.02F, mix(STEM, WILTED, mood.heat * 0.5F));
        glTranslatef(0, 0.24F, 0);
        glRotatef(sway * 0.5F, 0, 0, 1);
        glRotatef(nod * 0.5F, 1, 0, 0);
        drawHead(t, faceYaw, facePitch, mood, green);
        glPopMatrix();
    }

    private static void drawHead(double t, float faceYaw, float facePitch, Mood mood, int green) {
        float awake = 1 - mood.night;
        float recoil = recoil(t, mood);
        glPushMatrix();
        glTranslatef(0, 0.1F, 0);
        glRotatef(faceYaw + THREE_QUARTERS, 0, 1, 0);
        glRotatef(-facePitch * 0.6F + 15 * mood.night + 12 * mood.heat - 8 * mood.rain, 1, 0, 0);
        // the jerk back when it shoots
        glTranslatef(0, 0, -0.035F * recoil);
        glRotatef(-10 * recoil, 1, 0, 0);
        float breath = (float) Math.sin(t * 2) * 0.02F * awake + (float) Math.sin(t * 1.1) * 0.035F * mood.night;
        glScalef(1 + breath, 1 - breath, 1 + breath);

        // the round head
        box(-0.11F, -0.1F, -0.1F, 0.11F, 0.1F, 0.1F, green);
        box(-0.085F, 0.1F, -0.08F, 0.085F, 0.125F, 0.08F, green);
        box(-0.085F, -0.125F, -0.08F, 0.085F, -0.1F, 0.08F, green);
        box(-0.125F, -0.075F, -0.08F, -0.11F, 0.075F, 0.08F, green);
        box(0.11F, -0.075F, -0.08F, 0.125F, 0.075F, 0.08F, green);
        box(-0.08F, -0.07F, -0.115F, 0.08F, 0.07F, -0.1F, green);

        // the tube, puffing out as it shoots
        float puff = 1 + 0.3F * recoil;
        glPushMatrix();
        glTranslatef(0, -0.015F, 0.1F);
        glScalef(puff, puff, 1);
        box(-0.045F, -0.04F, 0, 0.045F, 0.04F, 0.09F, green);
        box(-0.058F, -0.053F, 0.08F, 0.058F, 0.053F, 0.115F, mix(TUBE_RIM, WILTED, mood.heat * 0.5F));
        face(-0.035F, -0.032F, 0.035F, 0.032F, 0.1155F, HOLE);
        glPopMatrix();

        drawEyes(t, mood);
        drawTuft(t, mood);
        if (mood.night > 0.5F) {
            for (int k = 0; k < 3; k++) {
                double p = frac(t / 2.4 + k / 3.0);
                float s = (float) (0.5 + 0.7 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F) * mood.night;
                glPushMatrix();
                glTranslatef(-0.12F - 0.08F * (float) p, 0.14F + 0.25F * (float) p, 0.06F);
                glRotatef(-THREE_QUARTERS, 0, 1, 0);
                glScalef(s, s, s);
                letterZ(0.035F, 0.035F, 0.009F, SLEEP_Z);
                glPopMatrix();
            }
        }
        drawPeas(t, mood);
        glPopMatrix();
    }

    /** Big eyes above the tube, closed asleep and when it blinks, sad in the heat. */
    private static void drawEyes(double t, Mood mood) {
        float z = 0.1005F;
        boolean blink = frac(t / 3.5) < 0.04 || mood.night > 0.5F;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.05F;
            if (blink) {
                face(ex - 0.022F, 0.04F, ex + 0.022F, 0.05F, z, EYE);
            } else {
                face(ex - 0.022F, 0.025F, ex + 0.022F, 0.08F, z, EYE);
                face(ex - 0.016F, 0.058F, ex - 0.004F, 0.072F, z + 0.001F, EYE_SHINE);
                if (mood.heat > 0.5F) {
                    // brows going up to the middle
                    face(ex - side * 0.004F, 0.088F, ex - side * 0.026F, 0.096F, z, EYE);
                }
            }
        }
    }

    /** The tuft of leaves at the back of the head. */
    private static void drawTuft(double t, Mood mood) {
        for (int k = -1; k <= 1; k++) {
            glPushMatrix();
            glTranslatef(0, 0.07F, -0.1F);
            glRotatef(90 + k * 28, 0, 1, 0);
            glRotatef((float) (40 + 8 * Math.sin(t * 2.5 + k)) - 35 * mood.heat - 20 * mood.night, 0, 0, 1);
            glScalef(0.75F, 0.75F, 0.75F);
            PottedPlantModel.leaf(mix(TUFT, WILTED, mood.heat * 0.6F));
            glPopMatrix();
        }
    }

    /** 0, or up to 1 right after a shot. */
    private static float recoil(double t, Mood mood) {
        float shoot = (1 - mood.night) * (1 - 0.5F * mood.heat);
        float main = (float) Math.max(0, 1 - frac(t / SHOT_EVERY) * SHOT_EVERY / 0.25);
        float rain = (float) Math.max(0, 1 - frac(t / RAIN_SHOT_EVERY + 0.5) * RAIN_SHOT_EVERY / 0.25);
        return Math.max(main * shoot, rain * mood.rain);
    }

    /** The peas in flight out of the tube, and the little cloud at the mouth. */
    private static void drawPeas(double t, Mood mood) {
        float shoot = (1 - mood.night) * (1 - 0.5F * mood.heat);
        drawPea(frac(t / SHOT_EVERY) * SHOT_EVERY, shoot, mood);
        drawPea(frac(t / RAIN_SHOT_EVERY + 0.5) * RAIN_SHOT_EVERY, mood.rain, mood);
    }

    /**
     * @param age      seconds since the shot
     * @param strength 0 for no shot
     */
    private static void drawPea(double age, float strength, Mood mood) {
        if (strength <= 0.05F || age > FLIGHT) return;
        double p = age / FLIGHT;
        // slow and small in the heat
        float reach = RANGE * (1 - 0.4F * mood.heat);
        float size = 0.03F * (1 - 0.35F * mood.heat) * strength * (p < 0.8 ? 1 : (float) (1 - p) / 0.2F);
        glPushMatrix();
        glTranslatef(0, -0.015F - 0.06F * (float) (p * p), 0.215F + reach * (float) p);
        glRotatef((float) (p * 360), 1, 0, 0);
        cube(size, size, size, PEA);
        box(-size * 0.6F, size * 0.2F, -size * 0.6F, -size * 0.1F, size * 1.01F, size * 0.1F, PEA_LIGHT);
        glPopMatrix();
        // the cloud bursting at the mouth
        if (age < 0.18) {
            float c = (float) (age / 0.18);
            glPushMatrix();
            glTranslatef(0, -0.015F, 0.22F);
            glScalef(1 + c, 1 + c, 0.4F);
            translucent(true);
            Shapes.box(-0.04F, -0.04F, -0.03F, 0.04F, 0.04F, 0.03F, PUFF, 0.6F * (1 - c) * strength);
            translucent(false);
            glPopMatrix();
        }
    }
}
