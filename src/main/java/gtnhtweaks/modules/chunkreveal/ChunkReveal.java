package gtnhtweaks.modules.chunkreveal;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

/**
 * The animation of the chunks drawn by Minecraft itself. A piece of a chunk, 16 blocks a side, starts to appear when it
 * first gets blocks after it moved to a new place; while it appears it is left out of the lists of RenderGlobal and
 * drawn on its own here, moved or scaled. With Angelica the same is done by {@link ChunkRevealShaders}.
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
            glCallList(held.list);
            glPopMatrix();
        }
        HELD.clear();
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
}
