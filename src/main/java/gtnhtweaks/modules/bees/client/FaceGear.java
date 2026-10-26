package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.box;
import static gtnhtweaks.common.model.Shapes.mix;

import gtnhtweaks.common.model.Shapes;

/**
 * What a bee wears on its face. Drawn in pixels from the middle of the face: x and y as on the body, the eyes at x =
 * ±2.1, y = 0.5, the mouth at y = -2.2, and z out of the face. The straps go back along the sides of the head, at x =
 * ±3.5.
 */
enum FaceGear {

    NONE(0),
    /** Black sunglasses: the tropical bees. */
    SUNGLASSES(0x141414),
    /** Brass goggles with glowing lenses: the thaumic and the windy bees. */
    GOGGLES(0xB08D3C),
    /** A golden monocle on a chain: the noble bees. */
    MONOCLE(0xE8B83A),
    /** A diving mask with a snorkel: the water bees. Its glass is drawn in the translucent pass. */
    SNORKEL(0x3C7DBF),
    /** A gas mask with a filter: the radioactive and toxic bees. */
    GASMASK(0x4A5244),
    /** Round reading glasses: the scholarly bees. */
    GLASSES(0x2A2A2A),
    /** A curled mustache: the ancient and classical bees. */
    MUSTACHE(0x3B2A1E);

    final int color;

    FaceGear(int color) {
        this.color = color;
    }

    /** @param lens the color of glowing lenses */
    void draw(int lens, double time) {
        switch (this) {
            case SUNGLASSES:
                for (int side = -1; side <= 1; side += 2) {
                    float x0 = side < 0 ? -3.3F : 0.9F, x1 = side < 0 ? -0.9F : 3.3F;
                    box(x0, -0.8F, 0, x1, 1.6F, 0.4F, color);
                    box(x0 + 0.3F, 1.0F, 0.4F, x0 + 0.7F, 1.4F, 0.45F, 0xFFFFFF);
                    temple(side, color);
                }
                box(-0.9F, 0.8F, 0, 0.9F, 1.2F, 0.3F, color);
                break;
            case GOGGLES: {
                for (int side = -1; side <= 1; side += 2) {
                    ring(side * 2.1F, 0.5F, 1.4F, 1.4F, 0.4F, 0.6F, color);
                    temple(side, 0x5A3E26);
                }
                box(-0.8F, 0.3F, 0, 0.8F, 0.8F, 0.4F, color);
                boolean lit = Shapes.glow();
                float pulse = 0.25F + 0.2F * (float) Math.sin(time * 2.2);
                for (int side = -1; side <= 1; side += 2) {
                    box(side * 2.1F - 1.0F, -0.5F, 0.3F, side * 2.1F + 1.0F, 1.5F, 0.35F, mix(lens, 0xFFFFFF, pulse));
                }
                Shapes.endGlow(lit);
                break;
            }
            case MONOCLE:
                ring(2.1F, 0.5F, 1.35F, 1.35F, 0.25F, 0.35F, color);
                // the chain, down to the side
                for (int i = 0; i < 5; i++) {
                    float y = -0.9F - i * 0.55F;
                    float x = 3.0F + i * 0.12F + (float) Math.sin(time * 1.4 + i * 0.5) * 0.08F * i;
                    box(x - 0.12F, y - 0.25F, 0.1F, x + 0.12F, y + 0.25F, 0.3F, color);
                }
                break;
            case SNORKEL:
                ring(0, 0.5F, 3.5F, 1.5F, 0.4F, 0.5F, color);
                // the frame between the eyes and the strap
                box(-0.25F, -1.0F, 0, 0.25F, 2.0F, 0.5F, color);
                temple(-1, 0x222222);
                temple(1, 0x222222);
                // the tube up the side of the head, its top in orange
                box(3.6F, -1.0F, -1.0F, 4.3F, 4.5F, -0.3F, 0xF2F2F2);
                box(3.6F, 4.5F, -1.0F, 4.3F, 5.8F, -0.3F, 0xF07A1E);
                box(3.6F, -1.7F, -1.0F, 4.3F, -1.0F, 0.4F, 0xF2F2F2);
                box(2.2F, -1.7F, 0, 3.6F, -1.0F, 0.4F, 0xF2F2F2);
                break;
            case GASMASK:
                box(-2.3F, -3.6F, 0, 2.3F, -0.9F, 1.0F, color);
                box(-1.0F, -3.2F, 1.0F, 1.0F, -1.4F, 2.2F, mix(color, 0x000000, 0.3F));
                for (int i = 0; i < 3; i++) {
                    float y = -2.95F + i * 0.55F;
                    box(-0.8F, y, 2.2F, 0.8F, y + 0.25F, 2.3F, 0x9AA0A8);
                }
                box(-3.6F, -2.6F, -3.0F, -2.3F, -1.9F, 0.6F, 0x2A2A2A);
                box(2.3F, -2.6F, -3.0F, 3.6F, -1.9F, 0.6F, 0x2A2A2A);
                break;
            case GLASSES:
                for (int side = -1; side <= 1; side += 2) {
                    ring(side * 2.1F, 0.5F, 1.3F, 1.3F, 0.22F, 0.3F, color);
                    temple(side, color);
                }
                box(-0.8F, 0.9F, 0, 0.8F, 1.15F, 0.3F, color);
                break;
            case MUSTACHE:
                box(-1.3F, -1.75F, 0, 1.3F, -1.15F, 0.45F, color);
                for (int side = -1; side <= 1; side += 2) {
                    float wag = (float) Math.sin(time * 1.1) * 0.1F;
                    box(side < 0 ? -2.3F : 1.3F, -1.55F, 0, side < 0 ? -1.3F : 2.3F, -0.95F, 0.4F, color);
                    box(
                        side < 0 ? -2.75F : 2.3F,
                        -1.0F + wag,
                        0,
                        side < 0 ? -2.3F : 2.75F,
                        -0.45F + wag,
                        0.35F,
                        color);
                }
                break;
            default:
                break;
        }
    }

    /** The glass of the diving mask, drawn with what is translucent. */
    static void maskGlass() {
        Shapes.box(-3.5F, -1.0F, 0.42F, 3.5F, 2.0F, 0.47F, 0xBFE4FF, 0.3F);
    }

    /** A frame of the given half sizes round (x, y), standing out of the face. */
    private static void ring(float x, float y, float halfX, float halfY, float line, float depth, int rgb) {
        box(x - halfX, y + halfY - line, 0, x + halfX, y + halfY, depth, rgb);
        box(x - halfX, y - halfY, 0, x + halfX, y - halfY + line, depth, rgb);
        box(x - halfX, y - halfY + line, 0, x - halfX + line, y + halfY - line, depth, rgb);
        box(x + halfX - line, y - halfY + line, 0, x + halfX, y + halfY - line, depth, rgb);
    }

    /** The strap or the arm of glasses, back along the side of the head. */
    private static void temple(int side, int rgb) {
        float x = side * 3.5F;
        box(Math.min(x, x + side * 0.25F), 0.6F, -3.0F, Math.max(x, x + side * 0.25F), 1.0F, 0.2F, rgb);
    }
}
