package gtnhtweaks.modules.bees.client;

import static gtnhtweaks.common.model.Shapes.frac;
import static gtnhtweaks.common.model.Shapes.mix;
import static gtnhtweaks.common.model.Shapes.rand;
import static gtnhtweaks.modules.bees.client.BeeMesh.box;
import static gtnhtweaks.modules.bees.client.BeeMesh.face;
import static gtnhtweaks.modules.bees.client.BeeMesh.letterZ;
import static gtnhtweaks.modules.bees.client.BeeMesh.pop;
import static gtnhtweaks.modules.bees.client.BeeMesh.push;
import static gtnhtweaks.modules.bees.client.BeeMesh.rotate;
import static gtnhtweaks.modules.bees.client.BeeMesh.scale;
import static gtnhtweaks.modules.bees.client.BeeMesh.translate;
import static gtnhtweaks.modules.bees.client.BeeMesh.translucent;

import forestry.api.apiculture.EnumBeeType;

/**
 * A bee in the style of the bees of Productive Bees and of vanilla: a fuzzy pixel body of 7 by 7 by 10 pixels with
 * stripes, a pixel face, antennae, see-through veined wings and little legs. It fills the unit cube centered on the
 * origin, like a block drawn as an item, and hovers in it. Drawn through {@link BeeMesh}, with the texture of its
 * species from {@link BeeTexture}.
 * <p>
 * It is modeled in pixels, a sixteenth of the cube, with the middle of the body at the origin and the face toward +z.
 * The drone is the smallest and has no sting, the princess wears a tiara and blushes, the queen is the biggest, with a
 * crown and a royal cape. The larva is a little grub.
 * <p>
 * It moves as the vanilla bee: slow and soft, the body bobbing and nodding, the wings beating. Everything is a function
 * of the time, so nothing is kept between frames but how the bee feels: two with other seeds live out of step.
 */
final class BeeModel {

    /** Half the width and the height of the body. */
    private static final float W = 3.5F, H = 3.5F;
    private static final int DARK = 0x1C1614;
    private static final int LEGS = 0x2B2420;
    private static final int SHINE = 0xFFFFFF;
    private static final int TONGUE = 0xE0707A;
    private static final int BLUSH = 0xF48FB1;
    private static final int HOT_BLUSH = 0xF0604A;
    private static final int SWEAT = 0x9ED8FF;
    private static final int SLEEP_Z = 0xE6EEFF;
    private static final int GOLD = 0xF2C437;
    private static final int RUBY = 0xE0303A;
    private static final int SAPPHIRE = 0x3A6AE0;
    private static final int CAPE = 0xA8202A;
    private static final int ERMINE = 0xF6F2EA;
    private static final int LARVA = 0xF4EEDC;

    // the face, in pixels of the 7 by 7 front: '#' dark, 'w' white, 'p' tongue, '.' nothing
    /**
     * The eyes, 2 by 4 pixels from the second row: big, at the outer edges of the face as on the vanilla bee, the left
     * one in the first two columns. The top row is for brows and lids.
     */
    private static final String[] OPEN_LEFT = { "..", "#w", "##", "##" }, OPEN_RIGHT = { "..", "w#", "##", "##" },
        SHUT = { "..", "..", "..", "##" }, CALM_LEFT = { "..", "..", "##", "#." },
        CALM_RIGHT = { "..", "..", "##", ".#" }, SURPRISED_LEFT = { "#w", "##", "##", "##" },
        SURPRISED_RIGHT = { "w#", "##", "##", "##" }, ANGRY_LEFT = { "..", "#.", "##", "#w" },
        ANGRY_RIGHT = { "..", ".#", "##", "w#" }, SAD_LEFT = { "..", ".w", "##", "##" },
        SAD_RIGHT = { "..", "w.", "##", "##" }, HALF = { "..", "..", "##", "##" }, STERN = { "##", "..", "##", "##" },
        DIZZY_LEFT = { "..", "#.", ".#", "#." }, DIZZY_RIGHT = { "..", ".#", "#.", ".#" };
    /** The mouths, 3 by 2 pixels between the eyes, in the fifth and sixth rows. */
    private static final String[] SMILE = { "...", ".#." }, GRIN = { "###", "#p#" }, OPEN = { ".#.", ".#." },
        FROWN = { ".#.", "#.#" }, SMIRK = { "...", ".##" }, LINE = { "...", "###" }, SNORE = { "...", ".#." },
        CHATTER = { "###", "w#w" }, CHATTER2 = { "###", "#w#" };
    /** The creeper face, all of it, from the second row. */
    private static final String[] CREEPER = { ".......", ".##.##.", ".##.##.", "...#...", "..###..", "..#.#.." };

    private BeeModel() {}

    /**
     * @param time      seconds
     * @param seed      bees with other seeds live out of step
     * @param faceYaw   degrees around y that turn the face (drawn toward +z) to the viewer
     * @param facePitch degrees the face tilts up to the viewer
     * @param dressed   with what its species wears, holds and glows with
     * @param carry     carrying what it produces when it holds nothing else
     */
    static void render(Bee bee, double time, int seed, float faceYaw, float facePitch, boolean dressed, boolean carry) {
        double t = time + rand(seed, 0) * 1000;
        SpeciesLook look = bee.look;
        BeeMesh.begin();
        if (bee.type == EnumBeeType.LARVAE) {
            larva(bee, t, faceYaw, dressed);
            BeeMesh.end(look.texture(dressed));
            return;
        }
        boolean queen = bee.type == EnumBeeType.QUEEN;
        float awake = 1 - bee.sleep;
        float size = queen ? 1.12F : bee.type == EnumBeeType.DRONE ? 0.9F : 1;
        float length = queen ? 5.6F : 5;

        push();
        // hovers softly, and lands to sleep
        float hover = (float) Math.sin(t * 1.7) * 0.03F * awake;
        float shiver = bee.cold > 0 ? (float) Math.sin(t * 14) * 0.008F * bee.cold : 0;
        // a little above the middle of the cube, so what it carries under it is in the cube too, not in the ground
        translate(shiver, 0.04F + hover - bee.sleep * 0.12F, 0);
        // turned a little from the viewer, so it is seen in 3D, looking about now and then
        rotate(faceYaw + 35 + (float) Math.sin(t * 0.35) * 8 * awake, 0, 1, 0);
        // nodding as it flies, as the vanilla bee
        rotate(-facePitch * 0.6F + 4 * awake + (float) Math.sin(t * 1.1) * 3 * awake, 1, 0, 0);
        float scale = size / 16;
        scale(scale, scale, scale);

        Skin skin = dressed ? look.skin : Skin.NORMAL;
        int tint = 0xFFFFFF;
        if (bee.cold > 0) tint = mix(tint, 0x9CC9F0, bee.cold * 0.5F);
        if (bee.hot > 0) tint = mix(tint, 0xFFB09A, bee.hot * 0.35F);
        BeeMesh.tint(tint);
        drawBody(skin, look, length);
        BeeMesh.tint(0xFFFFFF);
        if (bee.type != EnumBeeType.DRONE) {
            box(-0.5F, -1.5F, -length - (queen ? 2 : 1.5F), 0.5F, -0.5F, -length, DARK);
        }
        drawLegs(t, bee.sleep);
        drawAntennae(look, length, t, bee.sleep, dressed);

        Expression expression = bee.aggressive ? Expression.ANGRY : look.expression;
        drawFace(bee, expression, length, t, seed, dressed);

        Hat hat = dressed ? look.hat : Hat.NONE;
        if (queen) drawCape(length);
        if (hat != Hat.NONE) {
            push();
            translate(0, H, 0);
            hat.draw(look.hatColor, look.body, t);
            pop();
        }
        if (queen) drawCrown(H + hat.height);
        else if (bee.type == EnumBeeType.PRINCESS) drawTiara(length);
        FaceGear gear = dressed ? look.face : FaceGear.NONE;
        if (gear != FaceGear.NONE) {
            push();
            translate(0, 0, length);
            gear.draw(look.eyesGlow ? look.eyes : look.body, t);
            pop();
        }
        drawHeld(bee, length, t, dressed, carry);
        Aura aura = dressed ? look.aura : Aura.NONE;
        if (aura != Aura.NONE && !aura.translucent()) aura.draw(t, seed);
        if (bee.sleep > 0.5F) drawSleep(t, length);
        if (bee.hot > 0.3F) drawSweat(t, length, bee.hot);

        // what is see-through, drawn last
        translucent(true);
        if (skin == Skin.GHOST || skin == Skin.SLIME) {
            BeeMesh.tint(tint);
            BeeMesh.body(W, H, length, 0.55F);
            BeeMesh.tint(0xFFFFFF);
        }
        drawWings(t, awake, queen ? H + 0.3F : H);
        if (hat == Hat.ASTRONAUT) {
            push();
            translate(0, H, 0);
            Hat.bubble();
            pop();
        }
        if (gear == FaceGear.SNORKEL) {
            push();
            translate(0, 0, length);
            FaceGear.maskGlass();
            pop();
        }
        if (aura.translucent()) aura.draw(t, seed);
        translucent(false);
        pop();
        BeeMesh.end(look.texture(dressed));
    }

    private static void drawBody(Skin skin, SpeciesLook look, float length) {
        switch (skin) {
            case GHOST:
                // all of it see-through, drawn with what is translucent
                return;
            case SLIME:
                // a darker core in the see-through shell
                box(
                    -W * 0.65F,
                    -H * 0.65F,
                    -length * 0.7F,
                    W * 0.65F,
                    H * 0.65F,
                    length * 0.7F,
                    mix(look.body, 0x000000, 0.3F));
                return;
            case GLOW: {
                boolean was = BeeMesh.glow();
                BeeMesh.body(W, H, length, 1);
                BeeMesh.endGlow(was);
                return;
            }
            default:
                BeeMesh.body(W, H, length, 1);
        }
    }

    /** Three pairs, hanging under the body as it flies, swaying a little, tucked in as it sleeps. */
    private static void drawLegs(double t, float sleep) {
        for (int i = 0; i < 3; i++) {
            for (int side = -1; side <= 1; side += 2) {
                push();
                translate(side * 1.6F, -H, 2.0F - i * 2.0F);
                float sway = 25 + (float) Math.sin(t * 1.4 + i * 0.8) * 6;
                rotate(sway * (1 - sleep) + 70 * sleep, 1, 0, 0);
                box(-0.5F, -2.0F, -0.5F, 0.5F, 0, 0.5F, LEGS);
                pop();
            }
        }
    }

    private static void drawAntennae(SpeciesLook look, float length, double t, float sleep, boolean dressed) {
        boolean glow = dressed && look.antennaGlow;
        for (int side = -1; side <= 1; side += 2) {
            push();
            translate(side * 1.5F, H, length - 0.8F);
            // tilted forward and out, swaying, drooping as it sleeps
            rotate(30 + (float) Math.sin(t * 1.3 + side) * 4 * (1 - sleep) + 40 * sleep, 1, 0, 0);
            rotate(side * -12, 0, 0, 1);
            box(-0.3F, 0, -0.3F, 0.3F, 2.2F, 0.3F, LEGS);
            // bent forward at the tip, as the vanilla bee
            translate(0, 2.2F, 0);
            rotate(50, 1, 0, 0);
            boolean was = glow && BeeMesh.glow();
            float pulse = glow ? 0.5F + 0.5F * (float) Math.sin(t * 3 + side) : 0;
            box(-0.4F, 0, -0.4F, 0.4F, 1.2F, 0.4F, glow ? mix(look.stripe, 0xFFFFFF, pulse * 0.6F) : LEGS);
            if (glow) BeeMesh.endGlow(was);
            pop();
        }
    }

    /** The face drawn in pixels on the front, as a texture would have it, so it can change with how the bee feels. */
    private static void drawFace(Bee bee, Expression expression, float length, double t, int seed, boolean dressed) {
        boolean asleep = bee.sleep > 0.5F;
        boolean blink = frac(t / 3.7 + rand(seed, 1)) < 0.04;
        boolean hot = bee.hot > 0.5F, cold = bee.cold > 0.5F;
        // a tolerant flyer laughs in the rain
        if (bee.rain > 0.5F && bee.tolerantFlyer) expression = Expression.GRIN;
        boolean eyesGlow = dressed && bee.look.eyesGlow;
        int eye = eyesGlow ? bee.look.eyes : DARK;

        String[] left, right;
        if (asleep || blink && expression != Expression.CALM) left = right = SHUT;
        else {
            switch (expression) {
                case CALM:
                    left = CALM_LEFT;
                    right = CALM_RIGHT;
                    break;
                case ANGRY:
                    left = ANGRY_LEFT;
                    right = ANGRY_RIGHT;
                    break;
                case SAD:
                    left = SAD_LEFT;
                    right = SAD_RIGHT;
                    break;
                case DIZZY:
                    left = DIZZY_LEFT;
                    right = DIZZY_RIGHT;
                    break;
                case SURPRISED:
                    left = SURPRISED_LEFT;
                    right = SURPRISED_RIGHT;
                    break;
                case SMUG:
                    left = right = HALF;
                    break;
                case DETERMINED:
                    left = right = STERN;
                    break;
                default:
                    left = OPEN_LEFT;
                    right = OPEN_RIGHT;
                    break;
            }
        }
        float z = length + 0.02F;
        if (expression == Expression.CREEPER && !asleep && !blink) {
            pixels(CREEPER, 0, 0, z, eye);
            return;
        }
        boolean was = eyesGlow && BeeMesh.glow();
        pixels(left, 0, 1, z, eye);
        pixels(right, 5, 1, z, eye);
        if (eyesGlow) BeeMesh.endGlow(was);

        String[] mouth;
        if (asleep) mouth = SNORE;
        else if (hot) mouth = GRIN;
        else if (cold) mouth = frac(t * 6) < 0.5 ? CHATTER : CHATTER2;
        else {
            switch (expression) {
                case GRIN:
                    mouth = GRIN;
                    break;
                case SURPRISED:
                    mouth = OPEN;
                    break;
                case ANGRY:
                case SAD:
                case DIZZY:
                    mouth = FROWN;
                    break;
                case SMUG:
                    mouth = SMIRK;
                    break;
                case DETERMINED:
                    mouth = LINE;
                    break;
                default:
                    mouth = SMILE;
                    break;
            }
        }
        pixels(mouth, 2, 4, z, DARK);

        int blush = hot ? HOT_BLUSH : bee.type == EnumBeeType.PRINCESS || expression == Expression.DIZZY ? BLUSH : -1;
        if (blush >= 0) {
            pixel(1, 5, z, blush);
            pixel(5, 5, z, blush);
        }
        if (bee.type == EnumBeeType.PRINCESS && !asleep) {
            // lashes on the outer corners
            pixel(0, 1, z, DARK);
            pixel(6, 1, z, DARK);
        }
    }

    /** A little pixel map on the front, its upper left pixel at the column and row of the face. */
    private static void pixels(String[] map, int col, int row, float z, int dark) {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length(); c++) {
                switch (map[r].charAt(c)) {
                    case '#':
                        pixel(col + c, row + r, z, dark);
                        break;
                    case 'w':
                        pixel(col + c, row + r, z, SHINE);
                        break;
                    case 'p':
                        pixel(col + c, row + r, z, TONGUE);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** A pixel of the 7 by 7 front, columns from the left and rows from the top. */
    private static void pixel(int col, int row, float z, int rgb) {
        float x = -W + col, y = H - row;
        face(x, y - 1, x + 1, y, z, rgb);
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
        push();
        translate(0, y, 1.8F);
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
        pop();
    }

    /** A little golden tiara with a pink gem, at the front of the head. */
    private static void drawTiara(float length) {
        push();
        translate(0, H, length - 0.6F);
        box(-2.0F, 0, -0.3F, 2.0F, 0.4F, 0.3F, GOLD);
        box(-0.35F, 0.4F, -0.25F, 0.35F, 1.2F, 0.25F, GOLD);
        box(-1.4F, 0.4F, -0.2F, -0.9F, 0.8F, 0.2F, GOLD);
        box(0.9F, 0.4F, -0.2F, 1.4F, 0.8F, 0.2F, GOLD);
        box(-0.28F, 0.5F, 0.25F, 0.28F, 1.0F, 0.35F, 0xFF6AB0);
        pop();
    }

    private static void drawHeld(Bee bee, float length, double t, boolean dressed, boolean carry) {
        SpeciesLook look = bee.look;
        Held held = dressed ? look.held : Held.PRODUCT;
        if (held == Held.PRODUCT && (!carry || look.product == null)) held = Held.NONE;
        // the bees that do not fly in the rain hide under an umbrella
        if (bee.rain > 0.5F && !bee.tolerantFlyer) held = Held.UMBRELLA;
        if (held == Held.NONE) return;
        push();
        translate(0, -H, length - 0.6F);
        if (held == Held.PRODUCT) {
            // held out in front, under the face, so it is seen from above too
            translate(0, -0.9F + (float) Math.sin(t * 1.7 + 1) * 0.15F, 2.4F);
            rotate(-20, 1, 0, 0);
            rotate((float) Math.sin(t * 0.9) * 10, 0, 1, 0);
            scale(4.0F, 4.0F, 4.0F);
            BeeMesh.item(look.product);
        } else {
            held.draw(look.eyesGlow ? look.eyes : mix(look.body, 0xFFFFFF, 0.4F), t);
        }
        pop();
    }

    /** Beating as the wings of the vanilla bee, about six times a second; folded back along the body asleep. */
    private static void drawWings(double t, float awake, float y) {
        float beat = (float) Math.sin(t * Math.PI * 2 * 6);
        for (int side = -1; side <= 1; side += 2) {
            push();
            translate(side * 0.5F, y + 0.05F, -1.0F);
            rotate(side * (awake * (8 + 22 * beat) - 4 * (1 - awake)), 0, 0, 1);
            rotate(side * (12 + (1 - awake) * 18), 0, 1, 0);
            if (side < 0) scale(-1, 1, 1);
            BeeMesh.wing(8, -4.0F, 1.0F, 0xFFFFFFFF);
            pop();
        }
    }

    private static void drawSleep(double t, float length) {
        for (int k = 0; k < 3; k++) {
            double p = frac(t / 2.4 + k / 3.0);
            float s = (float) (0.4 + 0.6 * p) * (p < 0.75 ? 1 : (float) (1 - p) / 0.25F);
            push();
            translate(2.4F + 1.4F * (float) p, H + 1.5F + 4 * (float) p, length - 1);
            scale(s, s, s);
            letterZ(0.45F, 0.45F, 0.13F, SLEEP_Z);
            pop();
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
        push();
        translate(0, -0.5F, 0);
        rotate(faceYaw + 30, 0, 1, 0);
        scale(1.3F / 16, 1.3F / 16, 1.3F / 16);
        float headZ = 0, headTop = 0;
        for (int i = 4; i >= 0; i--) {
            float hw = 2.4F - i * 0.3F, hh = 2.1F - i * 0.25F, hl = 1.0F;
            float z = 3.4F - i * 1.9F;
            float lift = Math.max(0, (float) Math.sin(t * 2 - i * 0.9)) * 0.5F;
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
        face(-0.5F, cy - 1.3F, 0.5F, cy - 1.0F, headZ + 0.01F, DARK);
        face(-0.8F, cy - 1.0F, -0.5F, cy - 0.8F, headZ + 0.01F, DARK);
        face(0.5F, cy - 1.0F, 0.8F, cy - 0.8F, headZ + 0.01F, DARK);
        Hat hat = dressed ? look.hat : Hat.NONE;
        if (hat != Hat.NONE && hat != Hat.ASTRONAUT) {
            push();
            translate(0, headTop, headZ - 2.6F);
            scale(0.5F, 0.5F, 0.5F);
            hat.draw(look.hatColor, look.body, t);
            pop();
        }
        pop();
    }
}
