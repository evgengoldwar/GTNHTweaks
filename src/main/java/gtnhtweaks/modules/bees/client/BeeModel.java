package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.box;
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

import forestry.api.apiculture.EnumBeeType;
import gtnhtweaks.common.model.Shapes;

/**
 * A bee in the style of Productive Bees: a fuzzy box of a body with stripes, big eyes, antennae, see-through wings and
 * little legs, drawn by code. It fills the unit cube centered on the origin, like a block drawn as an item, and hovers
 * in it. GL_TEXTURE_2D must be disabled; the normals are set for the item lighting.
 * <p>
 * It is modeled in pixels, a sixteenth of the cube, with the middle of the body at the origin and the face toward +z.
 * The drone is the smallest and has no sting, the princess wears a tiara and blushes, the queen is the biggest, with a
 * crown and a royal cape. The larva is a little grub.
 * <p>
 * Everything is a function of the time, so nothing is kept between frames but how the bee feels: two with other seeds
 * just live out of step.
 */
final class BeeModel {

    /** Half the width and the height of the body. */
    private static final float W = 3.5F, H = 3.5F;
    private static final int DARK = 0x161616;
    private static final int SHINE = 0xFFFFFF;
    private static final int MOUTH = 0x3A1E16;
    private static final int TONGUE = 0xE0707A;
    private static final int BLUSH = 0xF48FB1;
    private static final int HOT_BLUSH = 0xF0604A;
    private static final int SWEAT = 0x9ED8FF;
    private static final int WING = 0xEAF6FF;
    private static final int SLEEP_Z = 0xE6EEFF;
    private static final int GOLD = 0xF2C437;
    private static final int RUBY = 0xE0303A;
    private static final int SAPPHIRE = 0x3A6AE0;
    private static final int CAPE = 0xA8202A;
    private static final int ERMINE = 0xF6F2EA;
    private static final int LARVA = 0xF4EEDC;

    private BeeModel() {}

    /**
     * @param time      seconds
     * @param seed      bees with other seeds live out of step
     * @param faceYaw   degrees around y that turn the face (drawn toward +z) to the viewer
     * @param facePitch degrees the face tilts up to the viewer
     * @param dressed   with what its species wears, holds and glows with
     * @param carry     carrying what it produces when it holds nothing else
     */
    static void render(Bee bee, double time, int seed, float faceYaw, float facePitch, boolean dressed,
        boolean carry) {
        double t = time + rand(seed, 0) * 1000;
        SpeciesLook look = bee.look;
        if (bee.type == EnumBeeType.LARVAE) {
            larva(bee, t, faceYaw, dressed);
            return;
        }
        boolean queen = bee.type == EnumBeeType.QUEEN;
        float awake = 1 - bee.sleep;
        float size = queen ? 1.12F : bee.type == EnumBeeType.DRONE ? 0.9F : 1;
        float length = queen ? 5.6F : 5;

        glPushMatrix();
        // hovers, and lands to sleep
        float hover = (float) Math.sin(t * 2.3) * 0.045F * awake;
        float shiver = bee.cold > 0 ? (float) Math.sin(t * 73) * 0.012F * bee.cold : 0;
        glTranslatef(shiver, -0.06F + hover - bee.sleep * 0.12F, 0);
        // turned a little from the viewer, so it is seen in 3D, looking about
        glRotatef(faceYaw + 42 + (float) Math.sin(t * 0.43) * 12 * awake, 0, 1, 0);
        glRotatef(-facePitch * 0.6F + (float) Math.sin(t * 1.7) * 3 * awake, 1, 0, 0);
        float scale = size / 16;
        glScalef(scale, scale, scale);

        Skin skin = dressed ? look.skin : Skin.NORMAL;
        int body = look.body, stripe = look.stripe;
        if (skin == Skin.UNDEAD) body = mix(body, 0x8FA58A, 0.35F);
        if (bee.cold > 0) {
            body = mix(body, 0x9CC9F0, bee.cold * 0.35F);
            stripe = mix(stripe, 0x9CC9F0, bee.cold * 0.25F);
        }
        if (bee.hot > 0) body = mix(body, 0xFF8A6A, bee.hot * 0.2F);
        int legs = mix(body, 0x1A1410, 0.65F);

        drawBody(skin, body, stripe, length, t);
        if (bee.type != EnumBeeType.DRONE) {
            float sting = queen ? 0.45F : 0.35F;
            box(-sting, -1.05F - sting, -length - (queen ? 2 : 1.4F), sting, -1.05F + sting, -length,
                mix(stripe, 0x000000, 0.5F));
        }
        drawLegs(legs, t, bee.sleep);
        drawAntennae(look, legs, body, length, t, bee.sleep, dressed);

        Expression expression = bee.aggressive ? Expression.ANGRY : look.expression;
        drawFace(bee, expression, body, length, t, seed, dressed);

        Hat hat = dressed ? look.hat : Hat.NONE;
        if (queen) drawCape(length);
        if (hat != Hat.NONE) {
            glPushMatrix();
            glTranslatef(0, H, 0);
            hat.draw(look.hatColor, body, t);
            glPopMatrix();
        }
        if (queen) drawCrown(H + hat.height);
        else if (bee.type == EnumBeeType.PRINCESS) drawTiara(length);
        FaceGear gear = dressed ? look.face : FaceGear.NONE;
        if (gear != FaceGear.NONE) {
            glPushMatrix();
            glTranslatef(0, 0, length);
            gear.draw(look.eyesGlow ? look.eyes : body, t);
            glPopMatrix();
        }
        drawHeld(bee, length, t, dressed, carry);
        Aura aura = dressed ? look.aura : Aura.NONE;
        if (aura != Aura.NONE && !aura.translucent()) aura.draw(t, seed);
        if (bee.sleep > 0.5F) drawSleep(t, length);
        if (bee.hot > 0.3F) drawSweat(t, length, bee.hot);

        // what is see-through, last
        translucent(true);
        if (skin == Skin.GHOST) {
            Shapes.box(-W, -H, -length, W, H, length, body, 0.45F);
            drawStripes(stripe, 0.5F);
        } else if (skin == Skin.SLIME) {
            Shapes.box(-W, -H, -length, W, H, length, body, 0.45F);
        }
        drawWings(t, awake, queen ? H + 0.3F : H);
        if (hat == Hat.ASTRONAUT) {
            glPushMatrix();
            glTranslatef(0, H, 0);
            Hat.bubble();
            glPopMatrix();
        }
        if (gear == FaceGear.SNORKEL) {
            glPushMatrix();
            glTranslatef(0, 0, length);
            FaceGear.maskGlass();
            glPopMatrix();
        }
        if (aura.translucent()) aura.draw(t, seed);
        translucent(false);
        glPopMatrix();
    }

    private static void drawBody(Skin skin, int body, int stripe, float length, double t) {
        if (skin == Skin.GHOST) return;
        if (skin == Skin.SLIME) {
            // a darker core, its shell drawn with what is translucent
            box(-W * 0.7F, -H * 0.7F, -length * 0.75F, W * 0.7F, H * 0.7F, length * 0.75F, mix(body, 0x000000, 0.25F));
            return;
        }
        boolean glow = skin == Skin.GLOW;
        boolean lit = glow && Shapes.glow();
        box(-W, -H, -length, W, H, length, body);
        drawStripes(stripe, 1);
        if (glow) Shapes.endGlow(lit);
        switch (skin) {
            case METAL: {
                int shine = mix(body, 0xFFFFFF, 0.6F);
                box(-W + 0.4F, H, -length + 0.6F, -W + 1.1F, H + 0.06F, length - 0.6F, shine);
                box(-W - 0.06F, H - 1.0F, -length + 0.6F, -W, H - 0.4F, length - 0.6F, shine);
                box(W, H - 1.0F, -length + 0.6F, W + 0.06F, H - 0.4F, length - 0.6F, mix(body, 0xFFFFFF, 0.35F));
                break;
            }
            case CRYSTAL: {
                int glint = mix(body, 0xFFFFFF, 0.75F);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        float x = sx * (W - 0.5F), z = sz * (length - 0.5F);
                        box(x - 0.5F, H, z - 0.5F, x + 0.5F, H + 0.08F, z + 0.5F, glint);
                    }
                }
                // a glint sliding along the side
                float g = (float) frac(t / 3) * (length * 2 + 4) - length - 2;
                if (g > -length && g < length - 1) box(-W - 0.08F, -H + 0.6F, g, -W, H - 0.6F, g + 1, SHINE);
                break;
            }
            case STONE:
                for (int i = 0; i < 12; i++) {
                    float a = (float) rand(77, i) * 2 - 1, b = (float) rand(77, i + 50) * 2 - 1;
                    float s = 0.3F + (float) rand(77, i + 100) * 0.4F;
                    int speck = i % 3 == 0 ? mix(body, 0xFFFFFF, 0.3F) : mix(body, 0x000000, 0.35F);
                    if (i % 3 == 0) box(a * (W - 0.5F) - s, H, b * (length - 0.5F) - s, a * (W - 0.5F) + s, H + 0.06F,
                        b * (length - 0.5F) + s, speck);
                    else {
                        float x = i % 2 == 0 ? -W - 0.06F : W;
                        box(x, a * (H - 0.5F) - s, b * (length - 0.5F) - s, x + 0.06F, a * (H - 0.5F) + s,
                            b * (length - 0.5F) + s, speck);
                    }
                }
                break;
            case UNDEAD:
                // a bandage round the body and stitches on its side
                box(-W - 0.08F, -H - 0.08F, 1.0F, W + 0.08F, H + 0.08F, 1.9F, 0xEDE6D6);
                box(W + 0.08F, -1.5F, 1.2F, W + 0.2F, 0, 1.7F, 0xEDE6D6);
                box(-W - 0.07F, -0.2F, -3.6F, -W, 0.15F, -0.6F, 0x2A2420);
                for (int i = 0; i < 4; i++) {
                    float z = -3.4F + i * 0.85F;
                    box(-W - 0.07F, -0.7F, z, -W, 0.65F, z + 0.2F, 0x2A2420);
                }
                break;
            case COSMIC: {
                boolean starsLit = Shapes.glow();
                int[] colors = { 0xFFFFFF, 0x9FE8FF, 0xFF9FF0, 0xFFF29F };
                for (int i = 0; i < 16; i++) {
                    float twinkle = 0.5F + 0.5F * (float) Math.sin(t * (2 + rand(91, i) * 3) + i);
                    float s = 0.12F + 0.2F * twinkle;
                    float a = (float) rand(91, i + 30) * 2 - 1, b = (float) rand(91, i + 60) * 2 - 1;
                    int c = colors[i % colors.length];
                    switch (i % 3) {
                        case 0:
                            box(a * W - s, H, b * length - s, a * W + s, H + 0.06F, b * length + s, c);
                            break;
                        case 1:
                            box(-W - 0.06F, a * H - s, b * length - s, -W, a * H + s, b * length + s, c);
                            break;
                        default:
                            box(W, a * H - s, b * length - s, W + 0.06F, a * H + s, b * length + s, c);
                            break;
                    }
                }
                Shapes.endGlow(starsLit);
                break;
            }
            default:
                break;
        }
    }

    /** The stripes round the back half of the body. */
    private static void drawStripes(int stripe, float alpha) {
        float e = 0.05F;
        Shapes.box(-W - e, -H - e, -1.4F, W + e, H + e, 0.2F, stripe, alpha);
        Shapes.box(-W - e, -H - e, -4.0F, W + e, H + e, -2.6F, stripe, alpha);
    }

    /** Three pairs, dangling as it flies and tucked in as it sleeps. */
    private static void drawLegs(int color, double t, float sleep) {
        for (int i = 0; i < 3; i++) {
            for (int side = -1; side <= 1; side += 2) {
                glPushMatrix();
                glTranslatef(side * 1.9F, -H, 2.0F - i * 2.0F);
                float dangle = (float) Math.sin(t * 3.1 + i + side) * 14;
                glRotatef(dangle * (1 - sleep) + 55 * sleep, 1, 0, 0);
                box(-0.35F, -1.7F, -0.35F, 0.35F, 0, 0.35F, color);
                glPopMatrix();
            }
        }
    }

    private static void drawAntennae(SpeciesLook look, int color, int body, float length, double t, float sleep,
        boolean dressed) {
        boolean glow = dressed && look.antennaGlow;
        for (int side = -1; side <= 1; side += 2) {
            glPushMatrix();
            glTranslatef(side * 1.3F, H, length - 0.9F);
            // tilted forward and out, twitching, drooping as it sleeps
            glRotatef(25 + (float) Math.sin(t * 2.6 + side) * 8 * (1 - sleep) + 40 * sleep, 1, 0, 0);
            glRotatef(side * -15, 0, 0, 1);
            box(-0.22F, 0, -0.22F, 0.22F, 2.6F, 0.22F, color);
            boolean lit = glow && Shapes.glow();
            float pulse = glow ? 0.5F + 0.5F * (float) Math.sin(t * 5 + side) : 0;
            box(-0.5F, 2.5F, -0.5F, 0.5F, 3.4F, 0.5F, glow ? mix(look.stripe, 0xFFFFFF, pulse * 0.6F) : color);
            if (glow) Shapes.endGlow(lit);
            glPopMatrix();
        }
    }

    private static void drawFace(Bee bee, Expression expression, int body, float length, double t, int seed,
        boolean dressed) {
        float z = length;
        boolean asleep = bee.sleep > 0.5F;
        boolean blink = frac(t / 3.7 + rand(seed, 1)) < 0.035;
        boolean hot = bee.hot > 0.5F, cold = bee.cold > 0.5F;
        // a tolerant flyer laughs in the rain
        if (bee.rain > 0.5F && bee.tolerantFlyer) expression = Expression.GRIN;
        int eye = dressed && bee.look.eyesGlow ? bee.look.eyes : DARK;
        boolean eyesGlow = dressed && bee.look.eyesGlow;

        for (int side = -1; side <= 1; side += 2) {
            float cx = side * 2.0F, cy = 0.6F;
            if (asleep || blink && expression != Expression.CALM) {
                box(cx - 1.05F, cy - 0.15F, z, cx + 1.05F, cy + 0.25F, z + 0.2F, DARK);
            } else {
                switch (expression) {
                    case CALM:
                        box(cx - 1.05F, cy + 0.2F, z, cx + 1.05F, cy + 0.6F, z + 0.2F, DARK);
                        box(cx - 1.05F, cy - 0.35F, z, cx - 0.65F, cy + 0.2F, z + 0.2F, DARK);
                        box(cx + 0.65F, cy - 0.35F, z, cx + 1.05F, cy + 0.2F, z + 0.2F, DARK);
                        break;
                    case DIZZY:
                        glPushMatrix();
                        glTranslatef(cx, cy, z);
                        glRotatef((float) (t * 200 * side), 0, 0, 1);
                        box(-0.95F, -0.95F, 0, 0.95F, 0.95F, 0.2F, DARK);
                        box(-0.6F, -0.6F, 0.2F, 0.6F, 0.6F, 0.22F, body);
                        box(0.25F, -0.15F, 0.22F, 0.6F, 0.15F, 0.24F, DARK);
                        box(-0.25F, -0.25F, 0.22F, 0.25F, 0.25F, 0.24F, DARK);
                        glPopMatrix();
                        break;
                    case CREEPER:
                        box(cx - 1.0F, cy - 0.6F, z, cx + 1.0F, cy + 1.2F, z + 0.2F, DARK);
                        break;
                    default: {
                        float hw = expression == Expression.SURPRISED ? 1.15F : 1.05F;
                        float hh = expression == Expression.SURPRISED ? 1.6F : 1.35F;
                        boolean lit = eyesGlow && Shapes.glow();
                        box(cx - hw, cy - hh, z, cx + hw, cy + hh, z + 0.25F, eye);
                        if (eyesGlow) Shapes.endGlow(lit);
                        else box(cx + hw - 0.85F, cy + hh - 0.85F, z + 0.25F, cx + hw - 0.2F, cy + hh - 0.2F,
                            z + 0.3F, SHINE);
                        if (expression == Expression.SMUG || expression == Expression.DETERMINED) {
                            // the lid over the eye
                            float lid = expression == Expression.SMUG ? cy - 0.05F : cy + 0.45F;
                            box(cx - hw - 0.05F, lid, z, cx + hw + 0.05F, cy + hh + 0.05F, z + 0.32F, body);
                            box(cx - hw - 0.05F, lid - 0.2F, z + 0.32F, cx + hw + 0.05F, lid, z + 0.34F, DARK);
                        }
                        break;
                    }
                }
            }
            // brows: down to the middle angry, up to the middle sad
            if (!asleep && (expression == Expression.ANGRY || expression == Expression.SAD)) {
                glPushMatrix();
                glTranslatef(cx, cy + 1.85F, z);
                glRotatef((expression == Expression.ANGRY ? 22 : -22) * side, 0, 0, 1);
                box(-1.0F, -0.2F, 0, 1.0F, 0.2F, 0.3F, DARK);
                glPopMatrix();
            }
            if (bee.type == EnumBeeType.PRINCESS && !asleep) {
                // lashes on the outer corner
                float x = cx + side * 0.9F;
                box(Math.min(x, x + side * 0.6F), cy + 0.6F, z, Math.max(x, x + side * 0.6F), cy + 0.85F, z + 0.2F,
                    DARK);
                box(Math.min(x, x + side * 0.5F), cy + 1.1F, z, Math.max(x, x + side * 0.5F), cy + 1.35F, z + 0.2F,
                    DARK);
            }
            int blush = hot ? HOT_BLUSH
                : bee.type == EnumBeeType.PRINCESS || expression == Expression.DIZZY ? BLUSH : -1;
            if (blush >= 0) face(cx - 0.6F + side * 0.6F, -1.6F, cx + 0.6F + side * 0.6F, -1.0F, z + 0.01F, blush);
        }

        float mz = z + 0.01F;
        if (asleep) {
            float snore = 0.25F + 0.15F * (float) Math.sin(t * 2);
            face(-snore, -2.2F - snore, snore, -2.2F + snore, mz, MOUTH);
        } else if (hot) {
            // panting
            float open = 0.5F + 0.2F * (float) Math.sin(t * 9);
            face(-1.1F, -2.2F - open, 1.1F, -1.8F, mz, MOUTH);
            face(-0.6F, -2.2F - open, 0.6F, -2.2F - open + 0.45F, mz + 0.01F, TONGUE);
        } else if (cold) {
            // chattering teeth
            float chatter = (float) Math.sin(t * 40) * 0.1F;
            face(-1.2F, -2.7F, 1.2F, -1.7F, mz, MOUTH);
            face(-1.0F, -2.15F + chatter, 1.0F, -1.85F, mz + 0.01F, SHINE);
            face(-1.0F, -2.55F, 1.0F, -2.25F + chatter, mz + 0.01F, SHINE);
        } else {
            switch (expression) {
                case GRIN:
                    face(-1.3F, -2.9F, 1.3F, -1.7F, mz, MOUTH);
                    face(-0.8F, -2.9F, 0.8F, -2.4F, mz + 0.01F, TONGUE);
                    break;
                case CALM:
                    face(-0.6F, -2.3F, 0.6F, -2.0F, mz, MOUTH);
                    face(-1.0F, -2.0F, -0.6F, -1.75F, mz, MOUTH);
                    face(0.6F, -2.0F, 1.0F, -1.75F, mz, MOUTH);
                    break;
                case SURPRISED:
                    face(-0.6F, -2.9F, 0.6F, -1.7F, mz, MOUTH);
                    break;
                case ANGRY:
                case SAD:
                    face(-0.8F, -2.1F, 0.8F, -1.7F, mz, MOUTH);
                    face(-1.4F, -2.5F, -0.8F, -2.1F, mz, MOUTH);
                    face(0.8F, -2.5F, 1.4F, -2.1F, mz, MOUTH);
                    break;
                case SMUG:
                    face(-0.4F, -2.3F, 1.0F, -2.0F, mz, MOUTH);
                    face(1.0F, -2.0F, 1.5F, -1.6F, mz, MOUTH);
                    break;
                case DIZZY:
                    for (int i = 0; i < 4; i++) {
                        float x = -1.4F + i * 0.7F;
                        float y = i % 2 == 0 ? -2.4F : -2.1F;
                        face(x, y, x + 0.7F, y + 0.3F, mz, MOUTH);
                    }
                    break;
                case DETERMINED:
                    face(-0.9F, -2.3F, 0.9F, -2.0F, mz, MOUTH);
                    break;
                case CREEPER:
                    face(-0.5F, -1.6F, 0.5F, -0.6F, mz, DARK);
                    face(-1.0F, -2.6F, 1.0F, -1.6F, mz, DARK);
                    face(-1.0F, -3.1F, -0.5F, -2.6F, mz, DARK);
                    face(0.5F, -3.1F, 1.0F, -2.6F, mz, DARK);
                    break;
                default:
                    face(-0.8F, -2.4F, 0.8F, -2.0F, mz, MOUTH);
                    face(-1.4F, -2.0F, -0.8F, -1.6F, mz, MOUTH);
                    face(0.8F, -2.0F, 1.4F, -1.6F, mz, MOUTH);
                    break;
            }
        }
    }

    /** A red cape over the back with an ermine collar, under the wings. */
    private static void drawCape(float length) {
        box(-W - 0.12F, H, -length - 0.3F, W + 0.12F, H + 0.25F, 1.6F, CAPE);
        box(-W - 0.12F, -0.8F, -length - 0.3F, -W, H, 1.6F, CAPE);
        box(W, -0.8F, -length - 0.3F, W + 0.12F, H, 1.6F, CAPE);
        box(-W - 0.25F, H - 0.4F, 1.5F, W + 0.25F, H + 0.5F, 2.4F, ERMINE);
        for (int i = -1; i <= 1; i++) box(i * 1.6F - 0.2F, H + 0.5F, 1.75F, i * 1.6F + 0.2F, H + 0.56F, 2.15F, DARK);
    }

    /** A golden crown with gems, on the head or on what it wears there. */
    private static void drawCrown(float y) {
        glPushMatrix();
        glTranslatef(0, y, 1.8F);
        box(-1.7F, 0, -1.7F, 1.7F, 0.8F, 1.7F, GOLD);
        box(-1.4F, 0.8F, -1.4F, 1.4F, 1.0F, 1.4F, CAPE);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            float x = (float) Math.sin(a) * 1.45F, z = (float) Math.cos(a) * 1.45F;
            float tip = i % 2 == 0 ? 1.0F : 0.6F;
            box(x - 0.25F, 0.8F, z - 0.25F, x + 0.25F, 0.8F + tip, z + 0.25F, GOLD);
        }
        box(-0.35F, 0.2F, 1.7F, 0.35F, 0.6F, 1.8F, RUBY);
        box(-1.8F, 0.2F, -0.35F, -1.7F, 0.6F, 0.35F, SAPPHIRE);
        box(1.7F, 0.2F, -0.35F, 1.8F, 0.6F, 0.35F, SAPPHIRE);
        glPopMatrix();
    }

    /** A little golden tiara with a pink gem, at the front of the head. */
    private static void drawTiara(float length) {
        glPushMatrix();
        glTranslatef(0, H, length - 0.6F);
        box(-2.0F, 0, -0.3F, 2.0F, 0.4F, 0.3F, GOLD);
        box(-0.35F, 0.4F, -0.25F, 0.35F, 1.2F, 0.25F, GOLD);
        box(-1.4F, 0.4F, -0.2F, -0.9F, 0.8F, 0.2F, GOLD);
        box(0.9F, 0.4F, -0.2F, 1.4F, 0.8F, 0.2F, GOLD);
        box(-0.28F, 0.5F, 0.25F, 0.28F, 1.0F, 0.35F, 0xFF6AB0);
        glPopMatrix();
    }

    private static void drawHeld(Bee bee, float length, double t, boolean dressed, boolean carry) {
        SpeciesLook look = bee.look;
        Held held = dressed ? look.held : Held.PRODUCT;
        if (held == Held.PRODUCT && (!carry || look.product == null)) held = Held.NONE;
        // the bees that do not fly in the rain hide under an umbrella
        if (bee.rain > 0.5F && !bee.tolerantFlyer) held = Held.UMBRELLA;
        if (held == Held.NONE) return;
        glPushMatrix();
        glTranslatef(0, -H, length - 0.6F);
        if (held == Held.PRODUCT) {
            glTranslatef(0, -1.9F + (float) Math.sin(t * 2.3 + 1) * 0.15F, 0.8F);
            glRotatef((float) Math.sin(t * 1.3) * 10, 0, 1, 0);
            glScalef(3.6F, 3.6F, 3.6F);
            HeldItem.draw(look.product);
        } else {
            held.draw(look.eyesGlow ? look.eyes : mix(look.body, 0xFFFFFF, 0.4F), t);
        }
        glPopMatrix();
    }

    private static void drawWings(double t, float awake, float y) {
        float flap = awake * (float) Math.sin(t * Math.PI * 2 * 7.5);
        for (int side = -1; side <= 1; side += 2) {
            glPushMatrix();
            glTranslatef(side * 0.5F, y, -1.2F);
            // up and down as it flies, folded back along the body as it sleeps
            glRotatef(side * (awake * (20 + 30 * flap) - 6 * (1 - awake)), 0, 0, 1);
            glRotatef(side * (1 - awake) * 25, 0, 1, 0);
            float x0 = side > 0 ? 0 : -5.0F, x1 = side > 0 ? 5.0F : 0;
            Shapes.box(x0, 0, -3.6F, x1, 0.1F, 1.0F, WING, 0.55F);
            Shapes.box(side > 0 ? 0.6F : -4.4F, 0.1F, -0.6F, side > 0 ? 4.4F : -0.6F, 0.12F, -0.4F, 0xB8D4E8, 0.5F);
            glPopMatrix();
        }
    }

    private static void drawSleep(double t, float length) {
        for (int k = 0; k < 3; k++) {
            double p = frac(t / 2.4 + k / 3.0);
            float s = (float) (0.4 + 0.6 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
            glPushMatrix();
            glTranslatef(2.4F + 1.4F * (float) p, H + 1.5F + 4 * (float) p, length - 1);
            glScalef(s, s, s);
            letterZ(0.45F, 0.45F, 0.13F, SLEEP_Z);
            glPopMatrix();
        }
    }

    /** A drop of sweat sliding down the side of the head. */
    private static void drawSweat(double t, float length, float hot) {
        double p = frac(t / 1.6);
        float s = 0.3F + 0.2F * hot;
        float y = H - 0.8F - (float) p * 3;
        box(W, y - s, length - 1.8F - s, W + 0.3F, y + s * 1.4F, length - 1.8F + s, SWEAT);
    }

    /**
     * A little grub lying on the ground, wriggling, pale with a touch of the color of its species, and what its species
     * wears on its head, small.
     */
    private static void larva(Bee bee, double t, float faceYaw, boolean dressed) {
        SpeciesLook look = bee.look;
        int skin = mix(LARVA, look.body, 0.3F);
        int ring = mix(LARVA, look.stripe, 0.2F);
        glPushMatrix();
        glTranslatef(0, -0.5F, 0);
        glRotatef(faceYaw + 30, 0, 1, 0);
        glScalef(1.3F / 16, 1.3F / 16, 1.3F / 16);
        float headZ = 0, headTop = 0;
        for (int i = 4; i >= 0; i--) {
            float hw = 2.4F - i * 0.3F, hh = 2.1F - i * 0.25F, hl = 1.0F;
            float z = 3.4F - i * 1.9F;
            float lift = Math.max(0, (float) Math.sin(t * 3 - i * 0.9)) * 0.5F;
            box(-hw, lift, z - hl, hw, lift + 2 * hh, z + hl, i % 2 == 0 ? skin : ring);
            if (i == 0) {
                headZ = z + hl;
                headTop = lift + 2 * hh;
            }
        }
        float cy = headTop * 0.6F;
        boolean blink = frac(t / 3.1) < 0.04;
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * 1.0F;
            if (blink || bee.sleep > 0.5F) box(cx - 0.4F, cy, headZ, cx + 0.4F, cy + 0.2F, headZ + 0.15F, DARK);
            else {
                box(cx - 0.4F, cy - 0.4F, headZ, cx + 0.4F, cy + 0.5F, headZ + 0.15F, DARK);
                box(cx, cy + 0.1F, headZ + 0.15F, cx + 0.3F, cy + 0.4F, headZ + 0.18F, SHINE);
            }
        }
        face(-0.5F, cy - 1.3F, 0.5F, cy - 1.0F, headZ + 0.01F, MOUTH);
        face(-0.8F, cy - 1.0F, -0.5F, cy - 0.8F, headZ + 0.01F, MOUTH);
        face(0.5F, cy - 1.0F, 0.8F, cy - 0.8F, headZ + 0.01F, MOUTH);
        Hat hat = dressed ? look.hat : Hat.NONE;
        if (hat != Hat.NONE && hat != Hat.ASTRONAUT) {
            glPushMatrix();
            glTranslatef(0, headTop, headZ - 2.6F);
            glScalef(0.5F, 0.5F, 0.5F);
            hat.draw(look.hatColor, look.body, t);
            glPopMatrix();
        }
        glPopMatrix();
    }
}
