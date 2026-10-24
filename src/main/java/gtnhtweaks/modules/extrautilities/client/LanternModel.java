package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.modules.extrautilities.client.Shapes.box;
import static gtnhtweaks.modules.extrautilities.client.Shapes.cube;
import static gtnhtweaks.modules.extrautilities.client.Shapes.face;
import static gtnhtweaks.modules.extrautilities.client.Shapes.frac;
import static gtnhtweaks.modules.extrautilities.client.Shapes.letterZ;
import static gtnhtweaks.modules.extrautilities.client.Shapes.mix;
import static gtnhtweaks.modules.extrautilities.client.Shapes.pulse;
import static gtnhtweaks.modules.extrautilities.client.Shapes.rand;
import static gtnhtweaks.modules.extrautilities.client.Shapes.translucent;
import static org.lwjgl.opengl.GL11.GL_LIGHTING;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * An iron lantern with a little fire spirit living in it. The spirit bobs, flickers, blinks and smiles at the viewer,
 * now and then turns round to look about, and sends sparks up the chimney. It glows: drawn unlit, at full brightness.
 * <p>
 * The handle is at the top, at y = 0.5: held in the hand the lantern hangs from it. At night the spirit dozes and
 * glows dimmer, in the rain it shivers small and scared, in the heat it burns big and happy.
 */
public final class LanternModel implements CompanionModel {

    public static final LanternModel INSTANCE = new LanternModel();

    private static final Runnable NOTHING = () -> {};
    /** Set by the item renderer: light at full brightness for the glowing spirit, and the light as it was after. */
    static Runnable fullBright = NOTHING, usualLight = NOTHING;

    private static final int IRON = 0x3B3C42;
    private static final int IRON_LIGHT = 0x5A5C66;
    private static final int GLASS = 0xFFE7B0;
    private static final int FIRE = 0xFFD45A;
    private static final int FIRE_HOT = 0xFFF4C2;
    private static final int FIRE_TIP = 0xFF9A2E;
    private static final int FIRE_DIM = 0xC9782E;
    private static final int GLOW = 0xFFA030;
    private static final int EYE = 0x5A2A00;
    private static final int EYE_WHITE = 0xFFFFFF;
    private static final int TONGUE = 0xE0503A;
    private static final int SLEEP_Z = 0xFFF0D0;

    private static final float HALF = 0.17F, BOTTOM = -0.44F, TOP = 0.06F;

    private LanternModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        drawIron();

        boolean lit = glIsEnabled(GL_LIGHTING);
        fullBright.run();
        glDisable(GL_LIGHTING);
        drawSpirit(t, seed, faceYaw, facePitch, mood);
        if (lit) glEnable(GL_LIGHTING);
        usualLight.run();

        translucent(true);
        box(-HALF, BOTTOM, -HALF, HALF, TOP, HALF, GLASS, 0.1F);
        translucent(false);
    }

    private static void drawIron() {
        box(-0.2F, -0.5F, -0.2F, 0.2F, BOTTOM, 0.2F, IRON);
        box(-0.18F, BOTTOM, -0.18F, 0.18F, BOTTOM + 0.015F, 0.18F, IRON_LIGHT);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(sx * HALF - 0.02F, BOTTOM, sz * HALF - 0.02F, sx * HALF + 0.02F, TOP, sz * HALF + 0.02F, IRON);
            }
        }
        box(-0.21F, TOP, -0.21F, 0.21F, 0.13F, 0.21F, IRON);
        box(-0.14F, 0.13F, -0.14F, 0.14F, 0.19F, 0.14F, IRON_LIGHT);
        box(-0.07F, 0.19F, -0.07F, 0.07F, 0.24F, 0.07F, IRON);
        // the handle, to hang it by
        box(-0.09F, 0.24F, -0.012F, -0.065F, 0.48F, 0.012F, IRON);
        box(0.065F, 0.24F, -0.012F, 0.09F, 0.48F, 0.012F, IRON);
        box(-0.09F, 0.46F, -0.012F, 0.09F, 0.5F, 0.012F, IRON);
    }

    private static void drawSpirit(double t, int seed, float faceYaw, float facePitch, Mood mood) {
        // big in the heat, small in the rain, a little smaller asleep
        float size = 1.45F * (1 + 0.15F * mood.heat - 0.3F * mood.rain - 0.1F * mood.night);
        float shiver = mood.rain * 0.008F * (float) Math.sin(t * 45);
        float bob = (float) Math.sin(t * 1.6) * 0.03F * (1 - mood.night * 0.6F);
        int fire = mix(mix(FIRE, FIRE_DIM, mood.night * 0.5F), FIRE_HOT, mood.heat * 0.5F);
        // now and then it turns round to look about
        float lookAbout = pulse(t + rand(seed, 4) * 10, 10, 2.5F) * (1 - mood.night);
        float yaw = faceYaw + lookAbout * 70 * (float) Math.sin(t * 2.5);

        glPushMatrix();
        glTranslatef(shiver, -0.24F + bob, 0);
        glScalef(size, size, size);
        glRotatef(yaw, 0, 1, 0);
        glRotatef(-facePitch * 0.5F, 1, 0, 0);
        // the body, then the flame on top flickering
        cube(0.07F, 0.065F, 0.055F, fire);
        cube(0.055F, 0.08F, 0.045F, fire);
        float flame = (1 + 0.5F * mood.heat - 0.4F * mood.rain - 0.3F * mood.night);
        for (int k = 0; k < 3; k++) {
            float sway = (float) (Math.sin(t * (7 + k * 2) + k) * 0.012 * (k + 1));
            float h = flame * (float) (0.035 + 0.012 * Math.sin(t * 11 + k * 1.7));
            float w = 0.045F - k * 0.013F;
            glPushMatrix();
            glTranslatef(sway, 0.08F + k * 0.035F * flame, 0);
            cube(w, h * 0.6F, w * 0.8F, k == 2 ? FIRE_TIP : mix(fire, FIRE_TIP, k * 0.4F));
            glPopMatrix();
        }
        drawFace(t, mood);
        if (mood.night > 0.5F) drawSleep(t);

        // a soft glow around it
        translucent(true);
        float glow = (0.2F + 0.05F * (float) Math.sin(t * 9)) * (1 - 0.4F * mood.night);
        Shapes.box(-0.1F, -0.09F, -0.08F, 0.1F, 0.16F * flame, 0.08F, GLOW, glow);
        translucent(false);
        glPopMatrix();
        drawSparks(t, seed, mood);
    }

    private static void drawFace(double t, Mood mood) {
        float z = 0.0555F;
        boolean asleep = mood.night > 0.5F;
        boolean scared = mood.rain > 0.5F;
        boolean happy = mood.heat > 0.5F;
        boolean blink = frac(t / 3.1) < 0.045;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.027F;
            if (asleep || (blink && !scared)) {
                face(ex - 0.013F, 0.008F, ex + 0.013F, 0.014F, z, EYE);
            } else if (scared) {
                // wide eyes with small pupils
                face(ex - 0.014F, -0.002F, ex + 0.014F, 0.03F, z, EYE_WHITE);
                face(ex - 0.005F, 0.008F, ex + 0.005F, 0.018F, z + 0.001F, EYE);
            } else if (happy) {
                face(ex - 0.013F, 0.012F, ex + 0.013F, 0.018F, z, EYE);
                face(ex - 0.018F, 0.004F, ex - 0.011F, 0.014F, z, EYE);
                face(ex + 0.011F, 0.004F, ex + 0.018F, 0.014F, z, EYE);
            } else {
                face(ex - 0.01F, 0.0F, ex + 0.01F, 0.026F, z, EYE);
            }
        }
        if (scared) {
            // a little round mouth
            face(-0.008F, -0.04F, 0.008F, -0.024F, z, EYE);
        } else if (happy) {
            face(-0.024F, -0.045F, 0.024F, -0.02F, z, EYE);
            face(-0.014F, -0.045F, 0.014F, -0.034F, z + 0.001F, TONGUE);
        } else if (!asleep) {
            face(-0.016F, -0.034F, 0.016F, -0.026F, z, EYE);
            face(-0.026F, -0.028F, -0.016F, -0.018F, z, EYE);
            face(0.016F, -0.028F, 0.026F, -0.018F, z, EYE);
        }
    }

    /** Sparks rising from the spirit up the chimney, fading. */
    private static void drawSparks(double t, int seed, Mood mood) {
        int count = 3 + Math.round(3 * mood.heat);
        for (int i = 0; i < count; i++) {
            double p = frac(t / (1.1 + rand(seed, 30 + i)) + rand(seed, 35 + i));
            float x = (float) (rand(seed, 40 + i) - 0.5) * 0.1F + 0.015F * (float) Math.sin(t * 6 + i);
            float z = (float) (rand(seed, 45 + i) - 0.5) * 0.1F;
            float s = (float) (1 - p) * (1 - 0.7F * mood.rain);
            glPushMatrix();
            glTranslatef(x, -0.08F + (float) p * 0.3F, z);
            glScalef(s, s, s);
            cube(0.01F, 0.01F, 0.01F, FIRE_HOT);
            glPopMatrix();
        }
    }

    private static void drawSleep(double t) {
        for (int k = 0; k < 2; k++) {
            double p = frac(t / 2.4 + k / 2.0);
            float s = (float) (0.4 + 0.6 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
            glPushMatrix();
            glTranslatef(0.06F + 0.03F * (float) p, 0.06F + 0.12F * (float) p, 0.06F);
            glScalef(s, s, s);
            letterZ(0.018F, 0.018F, 0.005F, SLEEP_Z);
            glPopMatrix();
        }
    }
}
