package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.mix;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslatef;

import gtnhtweaks.common.model.Shapes;

/**
 * What a bee holds in its front legs. Drawn in pixels from where the front legs hold it: under the chin, y up and z out
 * of the face.
 */
enum Held {

    NONE,
    /** What it produces, its comb or its specialty; drawn by {@link HeldItem}. */
    PRODUCT,
    /** A little sword: the heroic bees. */
    SWORD,
    /** A wrench: the working bees. */
    WRENCH,
    /** A wand with a glowing tip: the magic bees. */
    WAND,
    /** A book: the scholarly bees. */
    BOOK,
    /** An umbrella over the head, in the rain, for the bees that do not like it. */
    UMBRELLA;

    /**
     * @param glow the color of what glows, as the tip of the wand
     * @param time seconds
     */
    void draw(int glow, double time) {
        switch (this) {
            case SWORD:
                glPushMatrix();
                glTranslatef(2.4F, 0, 0.6F);
                glRotatef(-20 + (float) Math.sin(time * 1.9) * 6, 1, 0, 0);
                box(-0.3F, -1.6F, -0.3F, 0.3F, 0, 0.3F, 0x6B4A2B);
                box(-1.1F, 0, -0.35F, 1.1F, 0.4F, 0.35F, 0xE8B83A);
                box(-0.4F, 0.4F, -0.15F, 0.4F, 5.6F, 0.15F, 0xD8DDE3);
                box(-0.2F, 5.6F, -0.15F, 0.2F, 6.1F, 0.15F, 0xD8DDE3);
                box(-0.1F, 0.6F, 0.15F, 0.1F, 5.4F, 0.2F, 0xFFFFFF);
                glPopMatrix();
                break;
            case WRENCH:
                glPushMatrix();
                glTranslatef(2.2F, -0.5F, 0.8F);
                glRotatef(25 + (float) Math.sin(time * 2.3) * 8, 0, 0, 1);
                box(-0.35F, -1.0F, -0.25F, 0.35F, 3.4F, 0.25F, 0x8E949C);
                box(-1.0F, 3.4F, -0.25F, -0.3F, 4.6F, 0.25F, 0x8E949C);
                box(0.3F, 3.4F, -0.25F, 1.0F, 4.6F, 0.25F, 0x8E949C);
                box(-0.3F, -1.0F, 0.25F, 0.3F, 1.0F, 0.3F, 0xC8302A);
                glPopMatrix();
                break;
            case WAND: {
                glPushMatrix();
                glTranslatef(2.0F, 0, 1.0F);
                glRotatef(-30 + (float) Math.sin(time * 2.7) * 12, 1, 0, 0);
                glRotatef(-15, 0, 0, 1);
                box(-0.25F, -1.0F, -0.25F, 0.25F, 4.0F, 0.25F, 0x5A3A22);
                box(-0.3F, 1.0F, -0.3F, 0.3F, 1.4F, 0.3F, 0xE8B83A);
                boolean lit = Shapes.glow();
                float s = 0.5F + 0.12F * (float) Math.sin(time * 6);
                box(-s, 4.0F, -s, s, 4.0F + 2 * s, s, mix(glow, 0xFFFFFF, 0.5F));
                Shapes.endGlow(lit);
                glPopMatrix();
                break;
            }
            case BOOK:
                glPushMatrix();
                glTranslatef(0, -0.6F, 1.2F);
                glRotatef(-60, 1, 0, 0);
                box(-2.0F, -1.4F, -0.4F, 0, 1.4F, -0.1F, 0x7A2A20);
                box(0, -1.4F, -0.4F, 2.0F, 1.4F, -0.1F, 0x7A2A20);
                box(-1.8F, -1.2F, -0.1F, -0.1F, 1.2F, 0.15F, 0xF4EEDC);
                box(0.1F, -1.2F, -0.1F, 1.8F, 1.2F, 0.15F, 0xF4EEDC);
                for (int i = 0; i < 4; i++) {
                    float y = 0.8F - i * 0.5F;
                    box(-1.5F, y, 0.15F, -0.4F, y + 0.15F, 0.17F, 0x6A6A6A);
                    box(0.4F, y, 0.15F, 1.5F, y + 0.15F, 0.17F, 0x6A6A6A);
                }
                glPopMatrix();
                break;
            case UMBRELLA:
                umbrella(time);
                break;
            default:
                break;
        }
    }

    /** Held at the side, its canopy over the bee and what it wears. */
    private static void umbrella(double time) {
        glPushMatrix();
        glTranslatef(2.6F, 0, 0);
        glRotatef(-12 + (float) Math.sin(time * 1.3) * 4, 0, 0, 1);
        box(-0.2F, -1.0F, -0.2F, 0.2F, 14.0F, 0.2F, 0x3A3A3A);
        box(-0.2F, -1.0F, -0.2F, 0.6F, -0.6F, 0.2F, 0x3A3A3A);
        glTranslatef(0, 13.0F, 0);
        float[] halves = { 6.0F, 4.6F, 3.0F, 1.4F };
        for (int i = 0; i < halves.length; i++) {
            float h = halves[i];
            box(-h, i * 0.5F, -h, h, i * 0.5F + 0.5F, h, i % 2 == 0 ? 0xD8343A : 0xF4F4F4);
        }
        box(-0.3F, 2.0F, -0.3F, 0.3F, 2.6F, 0.3F, 0x3A3A3A);
        glPopMatrix();
    }
}
