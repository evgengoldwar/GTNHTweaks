package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.mix;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import gtnhtweaks.common.model.Shapes;

/**
 * What a bee wears on its head. Drawn in pixels, from the top of the body: the origin is on its top, above its middle,
 * y up and the face toward +z, at z = 5. A hat sits around z = 2, between the antennae in front and the wings behind.
 */
enum Hat {

    NONE(0, 0),
    /** A big leaf: the forest bees. */
    LEAF(0x4E9A2E, 0.8F),
    /** A ring of flowers: the meadow bees. */
    FLOWER_CROWN(0x5E9F37, 1.2F),
    /** A straw hat with a red band: the farm bees. */
    STRAW_HAT(0xE2C66E, 2.3F),
    /** A black top hat with a band in the color of the species: the noble bees. */
    TOP_HAT(0x1E1E24, 3.7F),
    /** A yellow safety helmet: the working bees. */
    HARD_HAT(0xF2C230, 2.2F),
    /** A steel helmet with a lamp: the bees of metals, gems, coal and stone. */
    MINER_HELMET(0x8E949C, 2.2F),
    /** A steel helmet with a red plume: the heroic bees. */
    KNIGHT_HELMET(0xB4BAC2, 2.0F),
    /** Horns: the demonic and draconic bees. */
    HORNS(0x4A1414, 0),
    /** A pointed hat with stars: the magic bees. */
    WIZARD_HAT(0x3F2A86, 5.2F),
    /** A knitted cap with a pompom, and a scarf: the icy bees. */
    BEANIE(0xC8323C, 2.6F),
    /** A wide sombrero: the desert bees. */
    SOMBRERO(0xD9B26F, 2.4F),
    /** A red mushroom cap with white dots: the swamp bees. */
    MUSHROOM(0xC4302B, 2.4F),
    /** Long bunny ears: the leporine bees. */
    BUNNY_EARS(0xF4F1EC, 0),
    /** A Santa hat: the merry bees. */
    SANTA_HAT(0xC8202A, 3.8F),
    /** A striped party hat: the celebrating and tipsy bees. */
    PARTY_HAT(0xE8489A, 4.4F),
    /** A carved pumpkin with a glowing face: the tricky bees. */
    PUMPKIN(0xE07B18, 2.9F),
    /** A golden halo: the holy and pure bees. */
    HALO(0xFFE36A, 0),
    /** A monk hood: the monastic and hermit bees. */
    HOOD(0x5B4636, 1.0F),
    /** A glass bubble helmet with an aerial: the space bees. Its glass is drawn in the translucent pass. */
    ASTRONAUT(0xEDEFF2, 0),
    /** A mortarboard with a tassel: the scholarly bees. */
    GRAD_CAP(0x1E1E24, 1.6F),
    /** A puffy chef hat: the food bees. */
    CHEF_HAT(0xF6F6F6, 3.6F),
    /** A dark artist beret splashed with paint: the dye bees. */
    BERET(0x2B2B3A, 1.1F),
    /** A flame burning on the head: the fire bees. Glows. */
    FLAME(0xFF8A1E, 3.6F),
    /** A big tropical flower behind the ear: the tropical bees. */
    FLOWER(0xE8344E, 1.0F);

    /** The color it has when the species does not give it one. */
    final int color;
    /** How high the crown of the queen sits on it: on the head itself between horns or ears, under a halo or glass. */
    final float height;

    Hat(int color, float height) {
        this.color = color;
        this.height = height;
    }

    /**
     * @param color its color
     * @param body  the color of the bee, for what is tinted by it
     * @param time  seconds
     */
    void draw(int color, int body, double time) {
        switch (this) {
            case LEAF:
                leaf(color, time);
                break;
            case FLOWER_CROWN:
                flowerCrown(color);
                break;
            case STRAW_HAT:
                box(-3.2F, 0, -1.6F, 3.2F, 0.35F, 4.8F, color);
                box(-1.8F, 0.35F, -0.2F, 1.8F, 2.3F, 3.4F, color);
                box(-1.9F, 0.35F, -0.3F, 1.9F, 0.85F, 3.5F, 0xB8302A);
                // a stalk of wheat in the band
                box(1.9F, 0.6F, 0.4F, 2.1F, 2.6F, 0.6F, 0xC9A43A);
                box(1.85F, 2.6F, 0.35F, 2.15F, 3.4F, 0.65F, 0xE0BC52);
                break;
            case TOP_HAT:
                glPushMatrix();
                glRotatef(-8, 0, 0, 1);
                box(-2.5F, 0, -0.7F, 2.5F, 0.3F, 4.3F, color);
                box(-1.6F, 0.3F, 0.2F, 1.6F, 3.7F, 3.4F, color);
                box(-1.7F, 0.3F, 0.1F, 1.7F, 0.95F, 3.5F, body);
                glPopMatrix();
                break;
            case HARD_HAT:
                box(-2.5F, 0, -0.6F, 2.5F, 1.4F, 4.0F, color);
                box(-1.9F, 1.4F, 0, 1.9F, 2.2F, 3.4F, color);
                box(-0.35F, 1.4F, -0.5F, 0.35F, 2.4F, 3.9F, mix(color, 0xFFFFFF, 0.3F));
                box(-2.2F, 0, 4.0F, 2.2F, 0.35F, 5.2F, color);
                break;
            case MINER_HELMET:
                box(-2.5F, 0, -0.6F, 2.5F, 1.5F, 4.0F, color);
                box(-1.9F, 1.5F, 0, 1.9F, 2.2F, 3.4F, color);
                box(-2.6F, 0, -0.7F, 2.6F, 0.4F, 4.1F, mix(color, 0x000000, 0.35F));
                // the lamp, its lens glowing
                box(-0.8F, 0.4F, 3.9F, 0.8F, 1.7F, 4.6F, 0x5A5F66);
                boolean lit = Shapes.glow();
                box(-0.55F, 0.6F, 4.6F, 0.55F, 1.5F, 4.75F, 0xFFF3B0);
                Shapes.endGlow(lit);
                break;
            case KNIGHT_HELMET:
                box(-2.7F, 0, -0.8F, 2.7F, 1.6F, 4.2F, color);
                box(-2.0F, 1.6F, -0.2F, 2.0F, 2.0F, 3.6F, color);
                box(-0.25F, 0, 3.9F, 0.25F, 1.8F, 4.4F, mix(color, 0x000000, 0.3F));
                plume(time);
                break;
            case HORNS:
                for (int side = -1; side <= 1; side += 2) {
                    glPushMatrix();
                    glTranslatef(side * 1.9F, 0, 2.4F);
                    glRotatef(side * -18, 0, 0, 1);
                    box(-0.55F, 0, -0.55F, 0.55F, 1.2F, 0.55F, color);
                    glTranslatef(side * 0.25F, 1.1F, -0.15F);
                    glRotatef(side * -14, 0, 0, 1);
                    box(-0.4F, 0, -0.4F, 0.4F, 1.0F, 0.4F, mix(color, 0x000000, 0.25F));
                    glTranslatef(0, 0.95F, -0.1F);
                    box(-0.22F, 0, -0.22F, 0.22F, 0.8F, 0.22F, mix(color, 0x000000, 0.55F));
                    glPopMatrix();
                }
                break;
            case WIZARD_HAT:
                wizardHat(color, time);
                break;
            case BEANIE:
                box(-2.6F, -0.4F, -0.6F, 2.6F, 1.4F, 4.4F, color);
                box(-2.0F, 1.4F, 0, 2.0F, 2.0F, 3.8F, color);
                box(-2.7F, -0.5F, -0.7F, 2.7F, 0.25F, 4.5F, 0xF2F2F2);
                box(-0.7F, 2.0F, 1.2F, 0.7F, 2.9F, 2.6F, 0xF2F2F2);
                break;
            case SOMBRERO:
                box(-3.6F, 0, -1.8F, 3.6F, 0.3F, 5.4F, color);
                box(-3.7F, 0.3F, -1.9F, -3.2F, 0.75F, 5.5F, color);
                box(3.2F, 0.3F, -1.9F, 3.7F, 0.75F, 5.5F, color);
                box(-1.6F, 0.3F, 0.2F, 1.6F, 1.7F, 3.4F, color);
                box(-1.1F, 1.7F, 0.7F, 1.1F, 2.4F, 2.9F, color);
                box(-1.7F, 0.3F, 0.1F, 1.7F, 0.8F, 3.5F, 0x2F8A4A);
                box(-1.71F, 0.45F, 1.4F, 1.71F, 0.65F, 2.2F, 0xD8343A);
                break;
            case MUSHROOM:
                box(-0.8F, 0, 1.0F, 0.8F, 0.6F, 2.6F, 0xEFE6D2);
                box(-2.8F, 0.6F, -1.0F, 2.8F, 1.8F, 4.6F, color);
                box(-2.0F, 1.8F, -0.2F, 2.0F, 2.4F, 3.8F, color);
                box(-1.4F, 2.4F, 0.6F, -0.6F, 2.5F, 1.4F, 0xFFFFFF);
                box(0.6F, 2.4F, 1.8F, 1.4F, 2.5F, 2.6F, 0xFFFFFF);
                box(-0.4F, 2.4F, 2.8F, 0.4F, 2.5F, 3.5F, 0xFFFFFF);
                box(-2.85F, 1.0F, 1.0F, -2.75F, 1.5F, 1.6F, 0xFFFFFF);
                box(2.75F, 1.1F, 2.4F, 2.85F, 1.6F, 3.0F, 0xFFFFFF);
                box(-0.5F, 0.9F, 4.55F, 0.2F, 1.5F, 4.65F, 0xFFFFFF);
                break;
            case BUNNY_EARS:
                for (int side = -1; side <= 1; side += 2) {
                    glPushMatrix();
                    glTranslatef(side * 1.2F, 0, 2.0F);
                    glRotatef(side * -12 + (float) Math.sin(time * 2.1 + side) * 4, 0, 0, 1);
                    // the tips flop now and then
                    box(-0.6F, 0, -0.35F, 0.6F, 3.0F, 0.35F, color);
                    box(-0.35F, 0.4F, 0.35F, 0.35F, 2.7F, 0.45F, 0xF2A6B8);
                    glTranslatef(0, 3.0F, 0);
                    glRotatef(Math.max(0, (float) Math.sin(time * 0.9 + side * 1.3)) * 50, 1, 0, 0);
                    box(-0.6F, 0, -0.35F, 0.6F, 1.2F, 0.35F, color);
                    glPopMatrix();
                }
                break;
            case SANTA_HAT:
                box(-2.5F, 0, -0.5F, 2.5F, 0.8F, 4.1F, 0xF4F4F4);
                stackedCone(color, 0xF4F4F4, 3.0F, time, false);
                break;
            case PARTY_HAT:
                stackedCone(color, 0xFFD23C, 4.4F, time, true);
                break;
            case PUMPKIN:
                pumpkin(color);
                break;
            case HALO: {
                glPushMatrix();
                glTranslatef(0, 2.4F + (float) Math.sin(time * 1.8) * 0.3F, 1.8F);
                glRotatef((float) Math.sin(time * 0.7) * 8, 1, 0, 0);
                boolean glow = Shapes.glow();
                box(-2.2F, 0, -2.2F, 2.2F, 0.4F, -1.6F, color);
                box(-2.2F, 0, 1.6F, 2.2F, 0.4F, 2.2F, color);
                box(-2.2F, 0, -1.6F, -1.6F, 0.4F, 1.6F, color);
                box(1.6F, 0, -1.6F, 2.2F, 0.4F, 1.6F, color);
                Shapes.endGlow(glow);
                glPopMatrix();
                break;
            }
            case HOOD:
                // over the head and down its sides, framing the face
                box(-3.9F, 0, -1.8F, 3.9F, 0.9F, 5.3F, color);
                box(-3.9F, -3.6F, 0.2F, -3.5F, 0, 5.3F, color);
                box(3.5F, -3.6F, 0.2F, 3.9F, 0, 5.3F, color);
                box(-3.95F, -3.6F, 4.8F, 3.95F, 0.95F, 5.35F, mix(color, 0x000000, 0.3F));
                box(-1.2F, 0.2F, -3.4F, 1.2F, 0.7F, -1.8F, color);
                break;
            case ASTRONAUT:
                // the rim of the bubble round the neck and the aerial; the glass is drawn with what is translucent
                box(-4.3F, -7.4F, -0.6F, 4.3F, -6.9F, 0.0F, color);
                box(-4.3F, -7.4F, -0.6F, -3.8F, 0.6F, 0.0F, color);
                box(3.8F, -7.4F, -0.6F, 4.3F, 0.6F, 0.0F, color);
                box(-4.3F, 0.1F, -0.6F, 4.3F, 0.6F, 0.0F, color);
                box(2.0F, 0.6F, -0.4F, 2.3F, 3.0F, -0.1F, 0x9AA0A8);
                boolean on = Math.sin(time * 4) > 0;
                boolean glowTip = on && Shapes.glow();
                box(1.85F, 3.0F, -0.55F, 2.45F, 3.5F, 0.05F, on ? 0xFF3030 : 0x7A2020);
                if (on) Shapes.endGlow(glowTip);
                break;
            case GRAD_CAP:
                box(-1.8F, 0, 0.2F, 1.8F, 1.2F, 3.6F, color);
                glPushMatrix();
                glTranslatef(0, 1.2F, 1.9F);
                glRotatef(45, 0, 1, 0);
                box(-2.8F, 0, -2.8F, 2.8F, 0.3F, 2.8F, color);
                glPopMatrix();
                box(-0.25F, 1.5F, 1.65F, 0.25F, 1.7F, 2.15F, 0xE8B83A);
                glPushMatrix();
                glTranslatef(0, 1.6F, 1.9F);
                glRotatef(90, 0, 1, 0);
                glRotatef((float) Math.sin(time * 1.5) * 10, 0, 0, 1);
                box(-0.1F, -0.1F, 0, 0.1F, 0.1F, 3.0F, 0xE8B83A);
                box(-0.25F, -2.0F, 2.8F, 0.25F, 0, 3.3F, 0xE8B83A);
                glPopMatrix();
                break;
            case CHEF_HAT:
                box(-2.0F, 0, 0, 2.0F, 1.2F, 3.8F, color);
                box(-2.6F, 1.2F, -0.6F, 2.6F, 3.0F, 4.4F, color);
                box(-2.1F, 3.0F, -0.1F, 2.1F, 3.6F, 3.9F, color);
                box(-0.9F, 1.3F, -0.65F, -0.7F, 3.0F, 4.45F, 0xDCDCDC);
                box(0.7F, 1.3F, -0.65F, 0.9F, 3.0F, 4.45F, 0xDCDCDC);
                break;
            case BERET:
                glPushMatrix();
                glTranslatef(0, 0, 1.9F);
                glRotatef(-14, 0, 0, 1);
                box(-2.6F, 0, -2.4F, 2.8F, 0.9F, 2.6F, color);
                box(-2.0F, 0.9F, -1.8F, 2.2F, 1.1F, 2.0F, color);
                box(-0.2F, 1.1F, -0.2F, 0.2F, 1.6F, 0.2F, mix(color, 0x000000, 0.4F));
                // splashes of paint, one in the color of the bee
                box(-1.6F, 0.9F, 0.4F, -0.8F, 1.0F, 1.2F, body);
                box(0.6F, 1.1F, -1.2F, 1.3F, 1.2F, -0.5F, 0xF2D23C);
                box(0.9F, 1.1F, 0.8F, 1.5F, 1.2F, 1.4F, 0x3C7DE0);
                box(2.4F, 0.2F, 1.0F, 2.85F, 0.7F, 1.5F, body);
                glPopMatrix();
                break;
            case FLAME:
                flame(time);
                break;
            case FLOWER:
                glPushMatrix();
                glTranslatef(2.4F, 0.2F, 2.8F);
                glRotatef(-35, 0, 0, 1);
                glRotatef(20, 0, 1, 0);
                for (int i = 0; i < 5; i++) {
                    glPushMatrix();
                    glRotatef(i * 72, 0, 0, 1);
                    box(-0.55F, 0.2F, -0.15F, 0.55F, 1.7F, 0.15F, color);
                    glPopMatrix();
                }
                box(-0.4F, -0.4F, -0.1F, 0.4F, 0.4F, 0.4F, 0xFFD23C);
                glPopMatrix();
                break;
            default:
                break;
        }
    }

    /** The glass of the space helmet, drawn with what is translucent. */
    static void bubble() {
        Shapes.box(-4.2F, -7.3F, -0.5F, 4.2F, 0.5F, 6.4F, 0xBFE4FF, 0.22F);
        Shapes.box(-3.2F, -1.6F, 6.41F, -2.2F, -0.2F, 6.45F, 0xFFFFFF, 0.5F);
    }

    private static void leaf(int color, double time) {
        glPushMatrix();
        glTranslatef(0, 0, 1.8F);
        glRotatef(8 + (float) Math.sin(time * 1.6) * 5, 1, 0, 0);
        box(-2.3F, 0, -1.7F, 2.3F, 0.4F, 1.7F, color);
        box(-1.7F, 0, -2.4F, 1.7F, 0.4F, 2.4F, color);
        box(-0.15F, 0.4F, -2.4F, 0.15F, 0.5F, 2.4F, mix(color, 0xFFFFFF, 0.35F));
        box(-0.15F, 0.2F, -3.4F, 0.15F, 0.45F, -2.4F, 0x6B4A2B);
        glPopMatrix();
    }

    private static void flowerCrown(int leaves) {
        int[] petals = { 0xF6A6C9, 0xFFFFFF, 0x8EC8FF, 0xFFD966, 0xF6A6C9, 0xC8A2F0 };
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            glPushMatrix();
            glTranslatef((float) Math.sin(a) * 2.5F, 0.3F, 1.8F + (float) Math.cos(a) * 2.5F);
            glRotatef((float) Math.toDegrees(a), 0, 1, 0);
            box(-0.5F, -0.3F, -0.5F, 0.5F, 0.1F, 0.5F, leaves);
            box(-0.75F, 0.1F, -0.3F, 0.75F, 0.6F, 0.3F, petals[i]);
            box(-0.3F, 0.1F, -0.75F, 0.3F, 0.6F, 0.75F, petals[i]);
            box(-0.28F, 0.6F, -0.28F, 0.28F, 0.8F, 0.28F, 0xFFC93C);
            glPopMatrix();
        }
    }

    private static void plume(double time) {
        glPushMatrix();
        glTranslatef(0, 2.0F, 1.0F);
        glRotatef(-25 + (float) Math.sin(time * 2.4) * 5, 1, 0, 0);
        box(-0.35F, 0, -0.4F, 0.35F, 2.0F, 0.4F, 0xC8202A);
        glTranslatef(0, 2.0F, 0);
        glRotatef(-35, 1, 0, 0);
        box(-0.3F, 0, -0.35F, 0.3F, 1.6F, 0.35F, 0xE0353F);
        glPopMatrix();
    }

    private static void wizardHat(int color, double time) {
        box(-2.9F, 0, -1.1F, 2.9F, 0.3F, 4.7F, color);
        box(-1.9F, 0.3F, -0.1F, 1.9F, 0.85F, 3.7F, 0xE8B83A);
        glPushMatrix();
        glTranslatef(0, 0.3F, 1.8F);
        float half = 1.8F;
        for (int i = 0; i < 5; i++) {
            box(-half, 0, -half, half, 1.0F, half, color);
            glTranslatef(0, 1.0F, -0.15F * i);
            glRotatef(-6 - (float) Math.sin(time * 1.3) * 2, 1, 0, 0);
            half *= 0.74F;
        }
        glPopMatrix();
        boolean lit = Shapes.glow();
        star(-1.0F, 1.6F, 3.65F, 0.35F, time);
        star(0.7F, 2.9F, 2.9F, 0.28F, time + 1.7);
        star(1.4F, 1.2F, 3.66F, 0.22F, time + 0.6);
        Shapes.endGlow(lit);
    }

    private static void star(float x, float y, float z, float size, double time) {
        float s = size * (0.75F + 0.25F * (float) Math.sin(time * 3));
        box(x - s, y - s * 0.35F, z, x + s, y + s * 0.35F, z + 0.1F, 0xFFE36A);
        box(x - s * 0.35F, y - s, z, x + s * 0.35F, y + s, z + 0.1F, 0xFFE36A);
    }

    /** A soft cone of stacked boxes, its tip falling to the side, with a pompom: the Santa hat and the party hat. */
    private static void stackedCone(int color, int other, float height, double time, boolean stripes) {
        glPushMatrix();
        glTranslatef(0, stripes ? 0 : 0.8F, 1.8F);
        if (stripes) glRotatef(-10, 0, 0, 1);
        int steps = 5;
        float half = stripes ? 1.9F : 2.2F;
        float step = height / steps;
        for (int i = 0; i < steps; i++) {
            box(-half, 0, -half, half, step, half, stripes && i % 2 == 1 ? other : color);
            glTranslatef(0, step, 0);
            if (!stripes) glRotatef(-14 - (float) Math.sin(time * 1.7) * 3, 0, 0, 1);
            half *= 0.72F;
        }
        box(-0.6F, -0.2F, -0.6F, 0.6F, 1.0F, 0.6F, stripes ? other : 0xF4F4F4);
        glPopMatrix();
    }

    private static void pumpkin(int color) {
        int dark = mix(color, 0x000000, 0.3F);
        box(-2.3F, 0, -0.5F, 2.3F, 2.6F, 4.1F, color);
        box(-0.9F, -0.01F, -0.51F, -0.6F, 2.61F, 4.11F, dark);
        box(0.6F, -0.01F, -0.51F, 0.9F, 2.61F, 4.11F, dark);
        box(-0.3F, 2.6F, 1.5F, 0.3F, 3.4F, 2.1F, 0x4E7A2A);
        boolean lit = Shapes.glow();
        box(-1.6F, 1.4F, 4.1F, -0.7F, 2.1F, 4.15F, 0xFFD54A);
        box(0.7F, 1.4F, 4.1F, 1.6F, 2.1F, 4.15F, 0xFFD54A);
        box(-1.5F, 0.4F, 4.1F, 1.5F, 0.9F, 4.15F, 0xFFD54A);
        box(-0.9F, 0.9F, 4.1F, -0.4F, 1.1F, 4.15F, 0xFFD54A);
        box(0.4F, 0.9F, 4.1F, 0.9F, 1.1F, 4.15F, 0xFFD54A);
        Shapes.endGlow(lit);
    }

    private static void flame(double time) {
        boolean lit = Shapes.glow();
        glPushMatrix();
        glTranslatef(0, 0, 1.8F);
        int[] colors = { 0xFF5A14, 0xFF8A1E, 0xFFB83A, 0xFFE36A };
        float half = 1.7F;
        for (int i = 0; i < 4; i++) {
            float flicker = 1 + 0.15F * (float) Math.sin(time * 11 + i * 1.9);
            float h = 0.9F * flicker;
            glPushMatrix();
            glTranslatef((float) Math.sin(time * 5 + i) * 0.2F * i, 0, 0);
            glScalef(flicker, 1, flicker);
            box(-half, 0, -half, half, h, half, colors[i]);
            glPopMatrix();
            glTranslatef(0, h, 0);
            half *= 0.7F;
        }
        glPopMatrix();
        Shapes.endGlow(lit);
    }
}
