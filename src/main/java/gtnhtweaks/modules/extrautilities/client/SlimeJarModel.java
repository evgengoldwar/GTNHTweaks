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
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * A slime in a glass jar. It hops about, squashing and stretching, every third hop bumps the lid, which jumps, and now
 * and then presses its face against the glass to look at the viewer.
 * <p>
 * At night it sleeps flat as a puddle, in the rain it hops faster and laughs, in the heat it melts and drips.
 */
public final class SlimeJarModel implements CompanionModel {

    public static final SlimeJarModel INSTANCE = new SlimeJarModel();

    private static final int GLASS = 0xCFEFF5;
    private static final int GLASS_BOTTOM = 0x9EC3C9;
    private static final int SHINE = 0xFFFFFF;
    private static final int LID = 0x8A9098;
    private static final int LID_DARK = 0x6A7078;
    private static final int SHELL = 0x7BD66A;
    private static final int CORE = 0x4FA544;
    private static final int MELTED = 0x9BC97A;
    private static final int EYE = 0x173016;
    private static final int MOUTH = 0x173016;
    private static final int TONGUE = 0xD96A6A;
    private static final int SLEEP_Z = 0xE6EEFF;

    /** Inside of the jar. */
    private static final float HALF = 0.2F, FLOOR = -0.46F, LID_Y = 0.22F;
    /** Half the size of the slime. */
    private static final float SIZE = 0.15F;
    private static final double HOP = 1.8;

    private SlimeJarModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        // hops: faster in the rain, none asleep or melting
        double period = HOP / (1 + 0.6 * mood.rain);
        double hop = Math.floor(t / period);
        double u = frac(t / period);
        boolean big = hop % 3 == 2;
        float awake = (1 - mood.night) * (1 - mood.heat);
        float height = 0, squash = 0;
        if (u < 0.2) {
            squash = (float) Math.sin(Math.PI * u / 0.2) * 0.25F;
        } else if (u < 0.6) {
            double a = (u - 0.2) / 0.4;
            height = (float) (4 * a * (1 - a)) * (big ? LID_Y - FLOOR - 2.3F * SIZE : 0.22F);
            squash = -0.18F * (float) Math.sin(Math.PI * a);
        } else if (u < 0.75) {
            squash = (float) Math.sin(Math.PI * (u - 0.6) / 0.15) * 0.3F;
        } else {
            squash = 0.04F * (float) Math.sin(t * 6);
        }
        height *= awake;
        squash *= awake;
        // flat asleep and melting
        float flat = 0.4F * mood.night + 0.5F * mood.heat + (float) Math.sin(t * 1.2) * 0.04F * mood.night;
        float sy = (1 - squash) * (1 - flat), sxz = (1 + squash * 0.6F) * (1 + flat * 0.7F);

        // where it lands after each hop, and now and then right at the glass toward the viewer
        float hx = (float) (rand(seed, (int) hop) - 0.5) * 0.1F * awake;
        float hz = (float) (rand(seed, (int) hop + 7) - 0.5) * 0.1F * awake;
        float press = pulse(t + rand(seed, 3) * 13, 13, 2.6F) * awake;
        double toViewer = Math.toRadians(faceYaw);
        float reach = HALF - SIZE * sxz - 0.005F;
        hx += ((float) Math.sin(toViewer) * reach - hx) * press;
        hz += ((float) Math.cos(toViewer) * reach - hz) * press;

        // the lid jumps when the slime bumps it
        float bump = big && u > 0.36 && u < 0.5 ? (float) Math.sin(Math.PI * (u - 0.36) / 0.14) * awake : 0;
        drawLid(bump, t);

        float lift = SIZE * sy;
        glPushMatrix();
        glTranslatef(hx, FLOOR + lift + height, hz);
        glRotatef(faceYaw, 0, 1, 0);
        // pressed on the glass, the face flattens against it
        glScalef(sxz * (1 + press * 0.15F), sy, sxz * (1 - press * 0.25F));
        int core = mix(CORE, MELTED, mood.heat);
        cube(SIZE * 0.75F, SIZE * 0.75F, SIZE * 0.75F, core);
        drawFace(t, mood, press);
        translucent(true);
        drawBubbles(t, seed);
        Shapes.box(-SIZE, -SIZE, -SIZE, SIZE, SIZE, SIZE, mix(SHELL, MELTED, mood.heat), 0.4F);
        translucent(false);
        glPopMatrix();
        if (mood.heat > 0) drawDrips(t, seed, hx, hz, sxz, mood.heat);
        if (mood.night > 0.5F) drawSleep(t, hx, FLOOR + lift * 2 + 0.04F, hz, faceYaw);

        drawJar();
    }

    private static void drawLid(float bump, double t) {
        glPushMatrix();
        glTranslatef(0, LID_Y + 0.04F + bump * 0.04F, 0);
        glRotatef(bump * 8 * (float) Math.sin(t * 30), 0, 0, 1);
        box(-0.215F, -0.04F, -0.215F, 0.215F, 0.035F, 0.215F, LID);
        box(-0.2F, 0.035F, -0.2F, 0.2F, 0.05F, 0.2F, LID_DARK);
        box(-0.05F, 0.05F, -0.05F, 0.05F, 0.1F, 0.05F, LID);
        glPopMatrix();
    }

    private static void drawFace(double t, Mood mood, float press) {
        float z = SIZE * 0.75F + 0.001F;
        boolean asleep = mood.night > 0.5F;
        boolean sad = mood.heat > 0.5F;
        boolean happy = mood.rain > 0.5F || press > 0.5F;
        boolean blink = frac(t / 3.3) < 0.04;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.04F;
            if (asleep || blink) {
                face(ex - 0.018F, 0.01F, ex + 0.018F, 0.018F, z, EYE);
            } else {
                face(ex - 0.02F, -0.002F, ex + 0.02F, 0.04F, z, EYE);
                face(ex - 0.015F, 0.024F, ex - 0.005F, 0.034F, z + 0.001F, 0xA8E59B);
            }
        }
        if (sad) {
            face(-0.02F, -0.035F, 0.02F, -0.027F, z, MOUTH);
            face(-0.03F, -0.045F, -0.02F, -0.035F, z, MOUTH);
            face(0.02F, -0.045F, 0.03F, -0.035F, z, MOUTH);
        } else if (happy) {
            face(-0.025F, -0.055F, 0.025F, -0.025F, z, MOUTH);
            face(-0.015F, -0.055F, 0.015F, -0.042F, z + 0.001F, TONGUE);
        } else if (asleep) {
            face(-0.008F, -0.04F, 0.008F, -0.028F, z, MOUTH);
        } else {
            face(-0.016F, -0.05F, 0.016F, -0.036F, z, MOUTH);
        }
    }

    /** Bubbles rising inside the slime, in its space. */
    private static void drawBubbles(double t, int seed) {
        for (int i = 0; i < 4; i++) {
            double p = frac(t / (2 + rand(seed, 50 + i)) + rand(seed, 55 + i));
            float x = (float) (rand(seed, 60 + i) - 0.5) * SIZE * 1.4F;
            float z = (float) (rand(seed, 65 + i) - 0.5) * SIZE * 1.4F;
            float y = -SIZE * 0.8F + (float) p * SIZE * 1.6F;
            glPushMatrix();
            glTranslatef(x, y, z);
            cube(0.012F, 0.012F, 0.012F, 0xD8F7CE);
            glPopMatrix();
        }
    }

    /** Drops sliding off the melting slime onto the floor of the jar. */
    private static void drawDrips(double t, int seed, float x, float z, float spread, float heat) {
        for (int i = 0; i < 3; i++) {
            double p = frac(t / (1.4 + rand(seed, 90 + i)) + rand(seed, 95 + i));
            double angle = rand(seed, 100 + i) * Math.PI * 2;
            float r = SIZE * spread + 0.01F + (float) p * 0.03F;
            glPushMatrix();
            glTranslatef(x + (float) Math.sin(angle) * r, FLOOR + 0.012F, z + (float) Math.cos(angle) * r);
            float s = heat * (float) (0.6 + p);
            glScalef(s, s * (float) (1 - p * 0.6), s);
            cube(0.02F, 0.012F, 0.02F, MELTED);
            glPopMatrix();
        }
    }

    private static void drawSleep(double t, float x, float y, float z, float faceYaw) {
        for (int k = 0; k < 3; k++) {
            double p = frac(t / 2.4 + k / 3.0);
            float s = (float) (0.4 + 0.6 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
            glPushMatrix();
            glTranslatef(x, y + 0.2F * (float) p, z);
            glRotatef(faceYaw, 0, 1, 0);
            glTranslatef(0.04F + 0.05F * (float) p, 0, 0.05F);
            glScalef(s, s, s);
            letterZ(0.025F, 0.025F, 0.007F, SLEEP_Z);
            glPopMatrix();
        }
    }

    private static void drawJar() {
        translucent(true);
        box(-HALF - 0.012F, -0.5F, -HALF - 0.012F, HALF + 0.012F, LID_Y, HALF + 0.012F, GLASS, 0.2F);
        // a highlight down the glass, so it reads as glass from every side
        for (int i = 0; i < 4; i++) {
            glPushMatrix();
            glRotatef(i * 90, 0, 1, 0);
            box(-0.15F, -0.42F, HALF + 0.013F, -0.12F, 0.15F, HALF + 0.016F, SHINE, 0.35F);
            glPopMatrix();
        }
        translucent(false);
        box(-HALF - 0.012F, -0.5F, -HALF - 0.012F, HALF + 0.012F, FLOOR, HALF + 0.012F, GLASS_BOTTOM);
    }
}
