package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.cube;
import static gtnhtweaks.common.model.Shapes.frac;
import static gtnhtweaks.common.model.Shapes.mix;
import static gtnhtweaks.common.model.Shapes.rand;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import gtnhtweaks.common.model.Shapes;

/**
 * Little things around a bee: sparks, flakes, bubbles and so on. Drawn in pixels round the middle of its body, in the
 * space of the bee. Everything is a function of the time, each with its own seed so two bees live out of step.
 */
enum Aura {

    NONE,
    /** Twinkling stars: the gem and magic bees, and the ones that shimmer in Forestry. */
    SPARKLES,
    /** Embers rising: the fire bees. */
    FLAMES,
    /** Snowflakes falling: the icy and merry bees. */
    SNOW,
    /** Bubbles rising: the water bees. Translucent. */
    BUBBLES,
    /** Musical notes: the jubilant bees. */
    NOTES,
    /** Purple specks drawn in, as round an enderman: the ender bees. */
    ENDER,
    /** Green specks circling: the radioactive bees. */
    RADIATION,
    /** Little lightning bolts: the energy and redstone bees. */
    SPARKS,
    /** A leaf falling now and then: the forest bees. */
    LEAVES,
    /** Puffs of smoke: the coal and oil bees. Translucent. */
    SMOKE,
    /** Floating spores: the swamp bees. */
    SPORES,
    /** Falling confetti: the party bees. */
    CONFETTI,
    /** Hearts rising: the lovely bees. */
    HEARTS;

    /** Whether it is drawn with what is translucent, after everything opaque. */
    boolean translucent() {
        return this == BUBBLES || this == SMOKE;
    }

    /**
     * @param time seconds
     * @param seed bees with other seeds live out of step
     */
    void draw(double time, int seed) {
        switch (this) {
            case SPARKLES: {
                boolean lit = Shapes.glow();
                for (int i = 0; i < 5; i++) {
                    double p = frac(time / (1.6 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    // a new place for every twinkle
                    int n = (int) Math.floor(time / (1.6 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    float s = (float) Math.sin(Math.PI * p) * 0.55F;
                    float x = around(seed, 30 + i * 7 + n, 7), y = around(seed, 31 + i * 7 + n, 6);
                    float z = around(seed, 32 + i * 7 + n, 7);
                    glPushMatrix();
                    glTranslatef(x, y, z);
                    glRotatef((float) (time * 90), 0, 1, 0);
                    box(-s, -s * 0.3F, -s * 0.3F, s, s * 0.3F, s * 0.3F, 0xFFF6C8);
                    box(-s * 0.3F, -s, -s * 0.3F, s * 0.3F, s, s * 0.3F, 0xFFF6C8);
                    glPopMatrix();
                }
                Shapes.endGlow(lit);
                break;
            }
            case FLAMES: {
                boolean lit = Shapes.glow();
                for (int i = 0; i < 5; i++) {
                    double p = frac(time / (1.1 + rand(seed, 10 + i) * 0.6) + rand(seed, 20 + i));
                    float x = around(seed, 30 + i, 4) + (float) Math.sin(time * 4 + i) * 0.4F;
                    float z = around(seed, 40 + i, 5);
                    float s = 0.45F * (float) (1 - p);
                    glPushMatrix();
                    glTranslatef(x, -1 + (float) p * 9, z);
                    cube(s, s, s, mix(0xFFE36A, 0xFF4A10, (float) p));
                    glPopMatrix();
                }
                Shapes.endGlow(lit);
                break;
            }
            case SNOW:
                for (int i = 0; i < 6; i++) {
                    double p = frac(time / (2.6 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    float x = around(seed, 30 + i, 7) + (float) Math.sin(time * 1.5 + i) * 0.6F;
                    float z = around(seed, 40 + i, 7);
                    glPushMatrix();
                    glTranslatef(x, 8 - (float) p * 14, z);
                    glRotatef((float) (time * 60 + i * 40), 0, 1, 0);
                    box(-0.4F, -0.12F, -0.12F, 0.4F, 0.12F, 0.12F, 0xFFFFFF);
                    box(-0.12F, -0.4F, -0.12F, 0.12F, 0.4F, 0.12F, 0xFFFFFF);
                    glPopMatrix();
                }
                break;
            case BUBBLES:
                for (int i = 0; i < 4; i++) {
                    double p = frac(time / (1.8 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    float x = around(seed, 30 + i, 5) + (float) Math.sin(time * 3 + i) * 0.4F;
                    float z = 3 + around(seed, 40 + i, 2);
                    float s = 0.3F + 0.3F * (float) p;
                    Shapes.box(
                        x - s,
                        -2 + (float) p * 10 - s,
                        z - s,
                        x + s,
                        -2 + (float) p * 10 + s,
                        z + s,
                        0xCFEFFF,
                        0.6F * (float) (1 - p * p));
                }
                break;
            case NOTES:
                for (int i = 0; i < 3; i++) {
                    double p = frac(time / 2.4 + i / 3.0 + rand(seed, 10));
                    int c = new int[] { 0xFF5A8A, 0x5AC8FF, 0xFFD23C }[i];
                    glPushMatrix();
                    glTranslatef(
                        around(seed, 30 + i, 6) + (float) Math.sin(time * 2 + i) * 0.8F,
                        2 + (float) p * 7,
                        around(seed, 40 + i, 4));
                    float s = (float) Math.min(1, Math.min(p * 5, (1 - p) * 4));
                    glScalef(s, s, s);
                    box(-0.5F, -0.4F, -0.2F, 0.4F, 0.3F, 0.2F, c);
                    box(0.15F, 0.3F, -0.1F, 0.4F, 2.0F, 0.1F, c);
                    box(0.4F, 1.5F, -0.1F, 1.0F, 2.0F, 0.1F, c);
                    glPopMatrix();
                }
                break;
            case ENDER: {
                boolean lit = Shapes.glow();
                for (int i = 0; i < 6; i++) {
                    double p = frac(time / (1.4 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    float r = 8 * (float) (1 - p);
                    double a = rand(seed, 30 + i) * Math.PI * 2 + time * 0.4;
                    float s = 0.35F * (float) Math.sin(Math.PI * p);
                    glPushMatrix();
                    glTranslatef(
                        (float) Math.sin(a) * r,
                        around(seed, 40 + i, 5) * (float) (1 - p),
                        (float) Math.cos(a) * r);
                    cube(s, s, s, i % 2 == 0 ? 0xCC4BFF : 0x7A2ACC);
                    glPopMatrix();
                }
                Shapes.endGlow(lit);
                break;
            }
            case RADIATION: {
                boolean lit = Shapes.glow();
                for (int i = 0; i < 6; i++) {
                    double a = time * (1.2 + rand(seed, 10 + i)) + rand(seed, 20 + i) * Math.PI * 2;
                    float r = 5.5F + (float) rand(seed, 30 + i) * 2;
                    float y = around(seed, 40 + i, 4) + (float) Math.sin(time * 2 + i) * 1.2F;
                    float s = 0.25F + 0.15F * (float) Math.sin(time * 7 + i * 2);
                    glPushMatrix();
                    glTranslatef((float) Math.sin(a) * r, y, (float) Math.cos(a) * r);
                    cube(s, s, s, 0x8CFF3C);
                    glPopMatrix();
                }
                Shapes.endGlow(lit);
                break;
            }
            case SPARKS: {
                // a bolt flashes now and here, now there
                for (int i = 0; i < 2; i++) {
                    double period = 0.9 + rand(seed, 10 + i) * 0.7;
                    double u = time / period + rand(seed, 20 + i);
                    double p = frac(u);
                    if (p > 0.35) continue;
                    int n = (int) Math.floor(u);
                    boolean lit = Shapes.glow();
                    glPushMatrix();
                    glTranslatef(
                        around(seed, 30 + n * 3 + i, 6),
                        1 + around(seed, 31 + n * 3 + i, 4),
                        around(seed, 32 + n * 3 + i, 6));
                    glRotatef((float) rand(seed, 33 + n) * 360, 0, 1, 0);
                    int c = mix(0xFFFFFF, 0x7AD8FF, (float) p * 2);
                    box(-0.15F, 0.8F, -0.15F, 0.6F, 1.8F, 0.15F, c);
                    box(-0.6F, 0, -0.15F, 0.6F, 0.8F, 0.15F, c);
                    box(-0.6F, -1.0F, -0.15F, 0.15F, 0, 0.15F, c);
                    glPopMatrix();
                    Shapes.endGlow(lit);
                }
                break;
            }
            case LEAVES:
                for (int i = 0; i < 2; i++) {
                    double p = frac(time / (3.5 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    glPushMatrix();
                    glTranslatef(
                        around(seed, 30 + i, 6) + (float) Math.sin(time * 2 + i) * 1.5F,
                        7 - (float) p * 13,
                        around(seed, 40 + i, 6));
                    glRotatef((float) Math.sin(time * 2.5 + i) * 40, 0, 0, 1);
                    box(-0.8F, -0.08F, -0.45F, 0.8F, 0.08F, 0.45F, i == 0 ? 0x5E9F37 : 0x8CBF3F);
                    glPopMatrix();
                }
                break;
            case SMOKE:
                for (int i = 0; i < 4; i++) {
                    double p = frac(time / 2.2 + i / 4.0 + rand(seed, 10));
                    float s = 0.4F + 1.2F * (float) p;
                    float x = around(seed, 30 + i, 1.5F) + (float) Math.sin(time + i) * 0.8F;
                    float y = (float) p * 8;
                    float z = -5.5F - (float) p * 2;
                    Shapes.box(x - s, y - s, z - s, x + s, y + s, z + s, 0x5A5A5A, 0.45F * (float) (1 - p));
                }
                break;
            case SPORES:
                for (int i = 0; i < 6; i++) {
                    double a = time * 0.4 + rand(seed, 20 + i) * Math.PI * 2;
                    float r = 5 + (float) rand(seed, 30 + i) * 3;
                    float y = around(seed, 40 + i, 5) + (float) Math.sin(time * 0.9 + i) * 1.5F;
                    glPushMatrix();
                    glTranslatef((float) Math.sin(a) * r, y, (float) Math.cos(a) * r);
                    cube(0.18F, 0.18F, 0.18F, i % 2 == 0 ? 0xC9C27A : 0x9FBF6A);
                    glPopMatrix();
                }
                break;
            case CONFETTI: {
                int[] colors = { 0xFF4A6A, 0x4AC8FF, 0xFFD23C, 0x6AE06A, 0xC86AFF };
                for (int i = 0; i < 6; i++) {
                    double p = frac(time / (2.0 + rand(seed, 10 + i)) + rand(seed, 20 + i));
                    glPushMatrix();
                    glTranslatef(
                        around(seed, 30 + i, 7) + (float) Math.sin(time * 3 + i) * 0.5F,
                        8 - (float) p * 14,
                        around(seed, 40 + i, 7));
                    glRotatef((float) (time * 200 + i * 60), 1, 1, 0);
                    box(-0.35F, -0.35F, -0.05F, 0.35F, 0.35F, 0.05F, colors[i % colors.length]);
                    glPopMatrix();
                }
                break;
            }
            case HEARTS:
                for (int i = 0; i < 2; i++) {
                    double p = frac(time / 2.8 + i / 2.0 + rand(seed, 10));
                    float s = (float) Math.min(1, Math.min(p * 5, (1 - p) * 3)) * 0.5F;
                    glPushMatrix();
                    glTranslatef(around(seed, 30 + i, 5), 4 + (float) p * 6, 2 + around(seed, 40 + i, 2));
                    glScalef(s, s, s);
                    box(-1.0F, 0, -0.3F, 0, 1.0F, 0.3F, 0xFF5A8A);
                    box(0, 0, -0.3F, 1.0F, 1.0F, 0.3F, 0xFF5A8A);
                    box(-0.6F, -0.6F, -0.3F, 0.6F, 0, 0.3F, 0xFF5A8A);
                    box(-0.2F, -1.0F, -0.3F, 0.2F, -0.6F, 0.3F, 0xFF5A8A);
                    glPopMatrix();
                }
                break;
            default:
                break;
        }
    }

    /** A number in [-reach, reach) that only depends on its arguments. */
    private static float around(int seed, int i, float reach) {
        return (float) (rand(seed, i) * 2 - 1) * reach;
    }
}
