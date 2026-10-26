package gtnhtweaks.modules.bees.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import gtnhtweaks.common.model.Shapes;

/**
 * The pixel texture of the bees of a species, painted from its colors when first drawn: a fuzzy body, its stripes round
 * the back half, light on top and shade below, what its skin adds (the shine of metal, the speckles of stone, the stars
 * of space), and the see-through wings with their veins. Every pixel is a little lighter or darker than the next, so
 * the body looks fuzzy, as the bees of Productive Bees and of vanilla.
 * <p>
 * The body is laid out as a box of 7 by 7 by 10 pixels: the regions are u, v, width and height.
 */
final class BeeTexture {

    static final int WIDTH = 64, HEIGHT = 32;
    /** The sides go from the back to the front and from the top down, the top and the bottom from the back. */
    static final int[] LEFT = { 0, 10, 10, 7 }, FRONT = { 10, 10, 7, 7 }, RIGHT = { 17, 10, 10, 7 },
        BACK = { 27, 10, 7, 7 }, TOP = { 10, 0, 7, 10 }, BOTTOM = { 17, 0, 7, 10 };
    /** Across the wing, from the body out, and along it, from the back. */
    static final int[] WING = { 36, 0, 9, 6 }, WING_UNDER = { 46, 0, 9, 6 };
    /** The middle of the white pixel, for what is not textured. */
    static final float WHITE_U = 63.5F / WIDTH, WHITE_V = 31.5F / HEIGHT;

    /** The columns of the sides, from the back, that are stripes. */
    private static final boolean[] STRIPE = { false, true, true, false, true, true, false, false, false, false };

    private BeeTexture() {}

    static ResourceLocation create(SpeciesLook look, Skin skin) {
        DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT);
        int[] pixels = texture.getTextureData();
        paintBody(pixels, look, skin);
        paintWing(pixels, WING);
        paintWing(pixels, WING_UNDER);
        pixels[31 * WIDTH + 63] = 0xFFFFFFFF;
        texture.updateDynamicTexture();
        return Minecraft.getMinecraft()
            .getTextureManager()
            .getDynamicTextureLocation("gtnhtweaks_bee", texture);
    }

    private static void paintBody(int[] pixels, SpeciesLook look, Skin skin) {
        int seed = look.body * 31 + look.stripe;
        // the sides: a column is a place along the body, from the back
        for (int[] side : new int[][] { LEFT, RIGHT }) {
            for (int col = 0; col < 10; col++) {
                for (int row = 0; row < 7; row++) {
                    float light = row == 0 ? 1.06F : row == 6 ? 0.82F : row == 5 ? 0.92F : 1;
                    boolean stripe = stripe(seed, col, row);
                    put(pixels, side, col, row, color(look, skin, seed, side[0], col, row, stripe, light));
                }
            }
        }
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 10; row++) {
                // across the top and the bottom a row is a place along the body
                put(pixels, TOP, col, row, color(look, skin, seed, 1, col, row, stripe(seed, row, col), 1.1F));
                put(pixels, BOTTOM, col, row, color(look, skin, seed, 2, col, row, stripe(seed, row, col), 0.72F));
            }
        }
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 7; row++) {
                float edge = row == 6 ? 0.85F : 1;
                put(pixels, FRONT, col, row, color(look, skin, seed, 3, col, row, false, 1.04F * edge));
                // the back is darker round the sting
                boolean middle = col >= 2 && col <= 4 && row >= 2 && row <= 4;
                float shade = (middle ? 0.8F : 0.95F) * edge;
                put(pixels, BACK, col, row, color(look, skin, seed, 4, col, row, false, shade));
            }
        }
    }

    /** Whether the pixel is of a stripe: its columns, and fuzzy at their edges. */
    private static boolean stripe(int seed, int col, int row) {
        if (STRIPE[col]) return true;
        boolean nextToStripe = col > 0 && STRIPE[col - 1] || col < 9 && STRIPE[col + 1];
        return nextToStripe && Shapes.rand(seed, col * 17 + row) < 0.22;
    }

    private static int color(SpeciesLook look, Skin skin, int seed, int face, int col, int row, boolean stripe,
        float light) {
        int i = face * 1000 + col * 31 + row;
        int base = stripe ? look.stripe : look.body;
        // fuzz: every pixel a little lighter or darker
        float fuzz = 0.9F + 0.16F * (float) Shapes.rand(seed, i);
        switch (skin) {
            case METAL:
                // diagonal shine
                if ((col + row) % 6 == 0) fuzz += 0.3F;
                break;
            case CRYSTAL:
                if (Shapes.rand(seed, i + 7) < 0.12) base = Shapes.mix(base, 0xFFFFFF, 0.6F);
                break;
            case STONE: {
                double r = Shapes.rand(seed, i + 7);
                if (r < 0.14) fuzz *= 0.7F;
                else if (r < 0.22) fuzz *= 1.2F;
                break;
            }
            case UNDEAD:
                if (!stripe && Shapes.rand(seed, i + 7) < 0.16) base = Shapes.mix(base, 0x6E8A5A, 0.5F);
                if (face == LEFT[0] && row == 3 && col >= 3 && col <= 7) base = 0x2A2420;
                break;
            case COSMIC:
                if (!stripe) {
                    double r = Shapes.rand(seed, i + 7);
                    if (r < 0.07) return opaque(r < 0.03 ? 0xFFFFFF : r < 0.05 ? 0x9FE8FF : 0xFF9FF0);
                }
                break;
            default:
                break;
        }
        return opaque(scale(base, light * fuzz));
    }

    /** A see-through wing: lighter at its edge, with veins. */
    private static void paintWing(int[] pixels, int[] region) {
        for (int col = 0; col < region[2]; col++) {
            for (int row = 0; row < region[3]; row++) {
                boolean edge = col == region[2] - 1 || row == 0 || row == region[3] - 1;
                boolean vein = row == 2 && col < region[2] - 2 || col == 3 && row > 0 && row < 4;
                int argb;
                if (edge) argb = 0xC8F2FAFF;
                else if (vein) argb = 0xB0B4CEE0;
                else argb = 0x78DCEEFF;
                // the corners rounded off
                if ((col == region[2] - 1 || col == 0) && (row == 0 || row == region[3] - 1)) argb = 0;
                put(pixels, region, col, row, argb);
            }
        }
    }

    private static void put(int[] pixels, int[] region, int col, int row, int argb) {
        pixels[(region[1] + row) * WIDTH + region[0] + col] = argb;
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * f));
        int g = Math.min(255, (int) ((rgb >> 8 & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return r << 16 | g << 8 | b;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
