package gtnhtweaks.modules.chunkreveal;

import static org.lwjgl.opengl.GL11.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.BufferUtils;

/**
 * The animation of the chunks drawn by Minecraft itself. A piece of a chunk, 16 blocks a side, starts to appear when it
 * first gets blocks after it moved to a new place; while it appears it is left out of the lists of RenderGlobal and
 * drawn on its own here, moved, scaled or dissolved. With Angelica the same is done by {@link ChunkRevealShaders}.
 */
public final class ChunkReveal {

    /** Delay of the wave: milliseconds per block from the player. The shaders use the same numbers. */
    static final float WAVE_MS_PER_BLOCK = 1.5F;
    static final float WAVE_MAX_BLOCKS = 512;
    /** Longest delay of the random order, in milliseconds. */
    static final float RANDOM_MS = 600;

    /** The renderers by their first display list, to find them from the lists RenderGlobal collects. */
    private static final Map<Integer, WorldRenderer> RENDERERS = new HashMap<>();
    /** The pieces held out of the lists of this pass that appear now. */
    private static final List<Held> HELD = new ArrayList<>();
    private static int pass;

    private static final Random RANDOM = new Random();
    private static final int STIPPLE_LEVELS = 32;
    private static ByteBuffer[] stipples;

    /** A piece that appears: its renderer, the display list of the pass and how far it is. */
    private static final class Held {

        final WorldRenderer renderer;
        final int list;
        final float progress;

        Held(WorldRenderer renderer, int list, float progress) {
            this.renderer = renderer;
            this.list = list;
            this.progress = progress;
        }
    }

    private ChunkReveal() {}

    /** Now, in milliseconds. */
    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Remembers a renderer by its first display list. */
    public static void register(int glRenderList, WorldRenderer renderer) {
        if (glRenderList >= 0) RENDERERS.put(glRenderList, renderer);
    }

    /** When a piece that just got its blocks starts to appear: later the farther it is, or at a random moment. */
    public static long startTime(WorldRenderer renderer) {
        long now = now();
        if (!ChunkRevealModule.wave) return now + (long) (RANDOM.nextFloat() * RANDOM_MS);
        EntityLivingBase viewer = Minecraft.getMinecraft().renderViewEntity;
        if (viewer == null) return now;
        double dx = renderer.posX + 8 - viewer.posX, dy = renderer.posY + 8 - viewer.posY,
            dz = renderer.posZ + 8 - viewer.posZ;
        float blocks = (float) Math.min(WAVE_MAX_BLOCKS, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return now + (long) (blocks * WAVE_MS_PER_BLOCK);
    }

    /** From 0 to 1 while the piece appears, below 0 while it waits for its turn, 1 when it is in place. */
    static float progress(long start) {
        if (start == Long.MIN_VALUE || ChunkRevealModule.style == ChunkRevealModule.Style.DEFAULT) return 1;
        return (now() - start) / (float) Math.max(1, ChunkRevealModule.durationMs);
    }

    // ---- RenderGlobal

    /** A pass of RenderGlobal starts: nothing is held yet. */
    public static void beginPass(int renderPass) {
        pass = renderPass;
        HELD.clear();
    }

    /**
     * Whether the display list is kept out of the lists of RenderGlobal: its piece waits for its turn and is not drawn,
     * or appears and is drawn by {@link #drawHeld}.
     */
    public static boolean hold(int list) {
        WorldRenderer renderer = RENDERERS.get(list - pass);
        if (!(renderer instanceof ChunkRevealState)) return false;
        float progress = progress(((ChunkRevealState) renderer).gtnhtweaks$revealStart());
        if (progress >= 1) return false;
        if (progress >= 0) HELD.add(new Held(renderer, list, progress));
        return true;
    }

    /** Draws the pieces that appear, in the state of the lists of RenderGlobal: textures and lightmap on. */
    public static void drawHeld(double partialTicks) {
        if (HELD.isEmpty()) return;
        EntityLivingBase viewer = Minecraft.getMinecraft().renderViewEntity;
        if (viewer == null) return;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
        ChunkRevealModule.Style style = ChunkRevealModule.style;
        float distance = ChunkRevealModule.distance;
        for (Held held : HELD) {
            WorldRenderer r = held.renderer;
            float t = held.progress;
            glPushMatrix();
            // as RenderList does: the list draws its blocks at posXClip.. around the corner posXMinus..
            glTranslatef((float) (r.posXMinus - camX), (float) (r.posYMinus - camY), (float) (r.posZMinus - camZ));
            float cx = r.posXClip + 8, cy = r.posYClip + 8, cz = r.posZClip + 8;
            switch (style) {
                case RISE:
                    glTranslatef(0, -(1 - outCubic(t)) * distance, 0);
                    break;
                case DROP:
                    glTranslatef(0, (1 - outBounce(t)) * distance, 0);
                    break;
                case SLIDE: {
                    double dx = r.posX + 8 - camX, dz = r.posZ + 8 - camZ;
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length < 0.001) {
                        dx = 0;
                        dz = 1;
                        length = 1;
                    }
                    float k = (1 - outCubic(t)) * distance;
                    glTranslatef((float) (dx / length * k), 0, (float) (dz / length * k));
                    break;
                }
                case GROW: {
                    float k = Math.max(0.001F, outBack(t));
                    glTranslatef(cx, cy, cz);
                    glScalef(k, k, k);
                    glTranslatef(-cx, -cy, -cz);
                    break;
                }
                default:
                    break;
            }
            boolean dissolve = style == ChunkRevealModule.Style.DISSOLVE;
            if (dissolve) {
                glEnable(GL_POLYGON_STIPPLE);
                glPolygonStipple(stipple(t));
            }
            glCallList(held.list);
            if (dissolve) glDisable(GL_POLYGON_STIPPLE);
            glPopMatrix();
        }
        HELD.clear();
    }

    /**
     * A pattern of the screen pixels drawn, 32 by 32 bits: more of them the further the piece is, in a fixed random
     * order, so the chunk gathers from scattered pixels.
     */
    private static ByteBuffer stipple(float t) {
        if (stipples == null) {
            int[] order = new int[32 * 32];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Random random = new Random(1710);
            for (int i = order.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = order[i];
                order[i] = order[j];
                order[j] = swap;
            }
            stipples = new ByteBuffer[STIPPLE_LEVELS + 1];
            for (int level = 0; level <= STIPPLE_LEVELS; level++) {
                byte[] bits = new byte[128];
                int count = order.length * level / STIPPLE_LEVELS;
                for (int i = 0; i < count; i++) {
                    int pixel = order[i];
                    bits[pixel / 8] |= (byte) (0x80 >>> (pixel % 8));
                }
                ByteBuffer buffer = BufferUtils.createByteBuffer(128);
                buffer.put(bits);
                buffer.flip();
                stipples[level] = buffer;
            }
        }
        int level = Math.max(0, Math.min(STIPPLE_LEVELS, Math.round(t * STIPPLE_LEVELS)));
        return stipples[level];
    }

    // ---- easing, as in the shaders

    static float outCubic(float t) {
        float s = 1 - t;
        return 1 - s * s * s;
    }

    static float outBack(float t) {
        float c1 = 1.70158F, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    static float outBounce(float t) {
        float n = 7.5625F, d = 2.75F;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) return n * (t -= 1.5F / d) * t + 0.75F;
        if (t < 2.5F / d) return n * (t -= 2.25F / d) * t + 0.9375F;
        return n * (t -= 2.625F / d) * t + 0.984375F;
    }
}
