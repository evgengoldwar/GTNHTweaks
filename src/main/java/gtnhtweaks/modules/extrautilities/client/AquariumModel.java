package gtnhtweaks.modules.extrautilities.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.face;
import static gtnhtweaks.common.model.Shapes.frac;
import static gtnhtweaks.common.model.Shapes.letterZ;
import static gtnhtweaks.common.model.Shapes.pulse;
import static gtnhtweaks.common.model.Shapes.rand;
import static gtnhtweaks.common.model.Shapes.translucent;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * A little glass tank with a goldfish. The fish swims round the tank, now and then comes to the front glass to look at
 * the viewer and blow a bubble; bubbles rise from a stone, seaweed waves.
 * <p>
 * At night the fish sleeps still on the sand, in the rain the water rises and the fish jumps out now and then, in
 * the heat the water is low and the fish gasps at the surface.
 */
public final class AquariumModel implements CompanionModel {

    public static final AquariumModel INSTANCE = new AquariumModel();

    private static final int STAND = 0x5A3A22;
    private static final int STAND_DARK = 0x452B18;
    private static final int FRAME = 0x2F3438;
    private static final int GLASS = 0xBFE6F2;
    private static final int WATER = 0x3F8FD0;
    private static final int SURFACE = 0x8FD0F0;
    private static final int SAND = 0xE3CC8E;
    private static final int PEBBLE = 0x9C8E7A;
    private static final int STONE = 0x7D7F86;
    private static final int WEED = 0x3FA34A;
    private static final int WEED_DARK = 0x2E7E38;
    private static final int FISH = 0xFF8C1A;
    private static final int FISH_LIGHT = 0xFFC46B;
    private static final int FIN = 0xF7A85A;
    private static final int EYE = 0x1E1E1E;
    private static final int EYE_WHITE = 0xFFFFFF;
    private static final int MOUTH = 0x7A2E10;
    private static final int BUBBLE = 0xE8F8FF;
    private static final int SLEEP_Z = 0xE6EEFF;

    /** Inside of the glass. */
    private static final float HALF = 0.28F, BOTTOM = -0.42F, TOP = 0.26F;
    private static final float SAND_TOP = -0.37F;
    /** Rounds of the fish round the tank per second. */
    private static final double LAPS_PER_SECOND = 0.12;
    /** In the rain the fish jumps out of the water once in so many seconds, for so long. */
    private static final double JUMP_EVERY = 7, JUMP_LENGTH = 1.2;

    private AquariumModel() {}

    @Override
    public void render(double time, int seed, float faceYaw, float facePitch, Mood mood) {
        double t = time + rand(seed, 0) * 1000;
        // the water rises in the rain and goes down in the heat
        float water = 0.16F + 0.06F * mood.rain - 0.2F * mood.heat + 0.006F * (float) Math.sin(t * 1.7);

        drawStand();
        drawBottom(t, seed);
        drawFish(t, seed, water, faceYaw, mood);

        translucent(true);
        drawBubbles(t, seed, water, mood);
        // the water, then the glass around it
        box(-HALF, SAND_TOP, -HALF, HALF, water, HALF, WATER, 0.18F);
        box(-HALF + 0.002F, water - 0.004F, -HALF + 0.002F, HALF - 0.002F, water, HALF - 0.002F, SURFACE, 0.35F);
        box(-HALF - 0.01F, BOTTOM, -HALF - 0.01F, HALF + 0.01F, TOP, HALF + 0.01F, GLASS, 0.18F);
        translucent(false);
        drawFrame();
    }

    private static void drawStand() {
        box(-0.34F, -0.5F, -0.34F, 0.34F, -0.46F, 0.34F, STAND_DARK);
        box(-0.32F, -0.46F, -0.32F, 0.32F, BOTTOM, 0.32F, STAND);
    }

    /** The edges of the tank, so it reads as glass. */
    private static void drawFrame() {
        float o = HALF + 0.012F, w = 0.018F;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(sx * o - w, BOTTOM, sz * o - w, sx * o + w, TOP, sz * o + w, FRAME);
            }
        }
        for (int i = 0; i < 2; i++) {
            float y = i == 0 ? BOTTOM : TOP - 0.02F;
            box(-o, y, -o - w, o, y + 0.02F, -o + w, FRAME);
            box(-o, y, o - w, o, y + 0.02F, o + w, FRAME);
            box(-o - w, y, -o, -o + w, y + 0.02F, o, FRAME);
            box(o - w, y, -o, o + w, y + 0.02F, o, FRAME);
        }
    }

    private static void drawBottom(double t, int seed) {
        box(-HALF, BOTTOM, -HALF, HALF, SAND_TOP, HALF, SAND);
        for (int i = 0; i < 6; i++) {
            float x = (float) (rand(seed, 30 + i) - 0.5) * 0.48F, z = (float) (rand(seed, 40 + i) - 0.5) * 0.48F;
            box(x - 0.015F, SAND_TOP, z - 0.015F, x + 0.015F, SAND_TOP + 0.012F, z + 0.015F, PEBBLE);
        }
        // the stone the bubbles come from
        box(0.1F, SAND_TOP, -0.2F, 0.2F, SAND_TOP + 0.06F, -0.1F, STONE);
        box(0.12F, SAND_TOP + 0.06F, -0.18F, 0.18F, SAND_TOP + 0.09F, -0.12F, STONE);
        // seaweed: segments waving more toward their tips
        float[][] weeds = { { -0.18F, -0.15F, 5 }, { -0.12F, -0.2F, 4 }, { 0.18F, 0.15F, 3 } };
        for (int w = 0; w < weeds.length; w++) {
            glPushMatrix();
            glTranslatef(weeds[w][0], SAND_TOP, weeds[w][1]);
            for (int k = 0; k < weeds[w][2]; k++) {
                glRotatef((float) (9 * Math.sin(t * 1.4 + w * 2 + k * 0.7)), 0, 0, 1);
                glRotatef((float) (5 * Math.sin(t * 1.1 + w + k * 0.9)), 1, 0, 0);
                box(-0.014F, 0, -0.008F, 0.014F, 0.075F, 0.008F, k % 2 == 0 ? WEED : WEED_DARK);
                glTranslatef(0, 0.07F, 0);
            }
            glPopMatrix();
        }
    }

    private static void drawFish(double t, int seed, float water, float faceYaw, Mood mood) {
        // a slow figure of eight round the tank, always at the same pace: the moods change how far it goes, never
        // how fast, so it never jumps from one place to another as they come in
        float awake = 1 - mood.night;
        double lap = t * LAPS_PER_SECOND * 2 * Math.PI;
        float x = (float) (0.1 * Math.sin(lap)) * awake;
        float z = (float) (0.08 * Math.sin(2 * lap)) * awake;
        float y = (float) (-0.1 + 0.06 * Math.sin(t * 0.4));
        // heading is around y from +z, the fish being drawn along +x
        float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(lap) * 0.1, Math.cos(2 * lap) * 0.16)) - 90;
        // every few seconds it turns to look at the viewer; asleep it lies on its side, facing the viewer
        float look = pulse(t + rand(seed, 2) * 10, 11, 3) * awake;
        yaw += wrap(faceYaw - 90 - yaw) * look;
        yaw += wrap(faceYaw - yaw) * mood.night;
        // asleep on the sand, gasping at the surface in the heat, jumping out now and then in the rain
        y += (SAND_TOP + 0.06F - y) * mood.night + (water - 0.05F - y) * mood.heat;
        float jump = pulse(t, JUMP_EVERY, JUMP_LENGTH) * mood.rain;
        y += jump * (water - y + 0.12F);

        glPushMatrix();
        glTranslatef(x, y, z);
        glRotatef(yaw, 0, 1, 0);
        glRotatef(jump * 30 * (float) Math.cos(Math.PI * frac(t / JUMP_EVERY) * JUMP_EVERY / JUMP_LENGTH), 0, 0, 1);
        glRotatef((float) (4 * Math.sin(t * 3)) * awake, 0, 1, 0);
        glScalef(1.9F, 1.9F, 1.9F);
        // the body along +x, the head at +x
        box(-0.05F, -0.035F, -0.025F, 0.05F, 0.035F, 0.025F, FISH);
        box(-0.035F, -0.045F, -0.02F, 0.035F, 0.045F, 0.02F, FISH);
        box(0.05F, -0.025F, -0.02F, 0.07F, 0.025F, 0.02F, FISH);
        box(-0.03F, -0.03F, -0.0255F, 0.02F, -0.005F, 0.0255F, FISH_LIGHT);
        // fins
        box(-0.02F, 0.045F, -0.004F, 0.025F, 0.065F, 0.004F, FIN);
        glPushMatrix();
        glTranslatef(-0.05F, 0, 0);
        // the tail: slow and small asleep, a little livelier in the rain
        glRotatef((float) (Math.sin(t * 4) * (20 + 8 * mood.rain) * (0.15 + 0.85 * awake)), 0, 1, 0);
        box(-0.05F, -0.04F, -0.004F, 0, 0.04F, 0.004F, FIN);
        box(-0.06F, -0.05F, -0.003F, -0.04F, -0.02F, 0.003F, FIN);
        box(-0.06F, 0.02F, -0.003F, -0.04F, 0.05F, 0.003F, FIN);
        glPopMatrix();
        // eyes on both sides, closed at night
        for (int side = -1; side <= 1; side += 2) {
            float ez = side * 0.0256F;
            glPushMatrix();
            if (side < 0) glRotatef(180, 0, 1, 0);
            float ex = side < 0 ? -0.04F : 0.04F;
            if (mood.night > 0.5F) {
                face(ex - 0.012F, 0.008F, ex + 0.012F, 0.013F, Math.abs(ez), EYE);
            } else {
                face(ex - 0.013F, -0.002F, ex + 0.013F, 0.024F, Math.abs(ez), EYE_WHITE);
                face(ex - 0.006F, 0.003F, ex + 0.008F, 0.019F, Math.abs(ez) + 0.001F, EYE);
            }
            glPopMatrix();
        }
        // the mouth opening and closing, wide when gasping
        float open = (float) ((0.5 + 0.5 * Math.sin(t * 2.5)) * (1 - mood.heat)
            + (0.5 + 0.5 * Math.sin(t * 5)) * mood.heat) * (0.4F + 0.6F * Math.max(look, mood.heat))
            * (1 - 0.7F * mood.night);
        box(0.069F, -0.008F - 0.008F * open, -0.008F, 0.073F, 0.002F + 0.004F * open, 0.008F, MOUTH);
        if (mood.night > 0.5F) drawSleep(t);
        glPopMatrix();
    }

    /** Bubbles rising from the stone and from the fish, popping at the surface. */
    private static void drawBubbles(double t, int seed, float water, Mood mood) {
        for (int i = 0; i < 8; i++) {
            double period = 2.6 + 1.5 * rand(seed, 60 + i);
            double p = frac((t + rand(seed, 70 + i) * period) / period);
            float x = 0.15F + 0.02F * (float) Math.sin(t * 2 + i), z = -0.15F + 0.02F * (float) Math.cos(t * 1.7 + i);
            float y = SAND_TOP + 0.09F + (float) p * (water - SAND_TOP - 0.1F);
            float size = (0.012F + 0.008F * (float) rand(seed, 80 + i)) * (1 - mood.heat * 0.7F);
            glPushMatrix();
            glTranslatef(x, y, z);
            box(-size, -size, -size, size, size, size, BUBBLE, 0.7F);
            glPopMatrix();
        }
    }

    /** Small letters Z rising from the sleeping fish, in its space. */
    private static void drawSleep(double t) {
        for (int k = 0; k < 2; k++) {
            double p = frac(t / 2.2 + k / 2.0);
            float s = (float) (0.4 + 0.6 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
            glPushMatrix();
            glTranslatef(0.06F + 0.03F * (float) p, 0.06F + 0.16F * (float) p, 0.03F);
            glScalef(s, s, s);
            letterZ(0.018F, 0.018F, 0.005F, SLEEP_Z);
            glPopMatrix();
        }
    }

    /** An angle in degrees brought into [-180, 180). */
    private static float wrap(float degrees) {
        return (float) (frac((degrees + 180) / 360.0) * 360 - 180);
    }
}
