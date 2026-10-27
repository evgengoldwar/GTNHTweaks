package gtnhtweaks.modules.loadingscreen.render;

import static org.lwjgl.opengl.GL11.*;

import java.util.Arrays;
import java.util.Random;

/**
 * The wall of blocks behind the loading screen. It is laid row by row from the bottom while the game loads, so that it
 * covers the whole screen when loading is done; then {@link #collapse} lets every block fall off the screen.
 * <p>
 * Block positions are in screen pixels of the current window size; the wall is laid out again if the size changes.
 */
final class BlockWall {

    /** How long a new block takes to drop into place. */
    static final int DROP_MS = 260;
    /** Shortest time between two blocks being laid while loading. */
    private static final int LAY_INTERVAL_MS = 35;

    private static final Random random = new Random();

    private static int layoutWidth, layoutHeight, rows;
    private static int columns;
    private static float size, left;
    /** Cells in the order they are laid; a cell is {@code row * columns + column}, row 0 at the bottom. */
    private static int[] order = new int[0];
    /** By cell: the texture id and when the block was laid (its drop starts then). */
    private static int[] textures = new int[0];
    private static long[] laidAt = new long[0];
    private static int laid;
    private static long lastLaidAt;

    // Collapse, by cell
    private static long collapseAt = -1;
    private static float[] delay, velocityX, velocityY, spin;
    private static float gravity;

    private BlockWall() {}

    static boolean isEmpty() {
        return laid == 0;
    }

    /** Lays blocks until {@code progress} (0 to 1) of the wall is done, at most one block every few frames. */
    static void build(int width, int height, int wallRows, float progress, long now) {
        layout(width, height, wallRows);
        int target = Math.min(order.length, (int) Math.ceil(progress * order.length));
        while (laid < target) {
            long at = Math.max(now, lastLaidAt + LAY_INTERVAL_MS);
            if (at > now + LAY_INTERVAL_MS) break;
            if (!lay(at)) break;
        }
    }

    /**
     * Lays every block that is still missing, quickly enough to finish within about {@code ms}.
     *
     * @return when the last block will be in place
     */
    static long fill(int width, int height, int wallRows, long now, int ms) {
        layout(width, height, wallRows);
        int missing = order.length - laid;
        if (missing <= 0) return Math.max(now, lastLaidAt + DROP_MS);
        float interval = Math.min(LAY_INTERVAL_MS, (float) ms / missing);
        long start = Math.max(now, lastLaidAt);
        for (int i = 0; laid < order.length; i++) {
            if (!lay(start + (long) (i * interval))) break;
        }
        return lastLaidAt + DROP_MS;
    }

    private static boolean lay(long at) {
        int cell = order[laid];
        int texture = WallTextures.pick(random);
        // a different block than its left neighbour, if the dice allow it
        int column = cell % columns;
        if (column > 0 && texture == textures[cell - 1]) texture = WallTextures.pick(random);
        if (texture < 0) return false;
        textures[cell] = texture;
        laidAt[cell] = at;
        lastLaidAt = at;
        laid++;
        return true;
    }

    private static void layout(int width, int height, int wallRows) {
        if (width == layoutWidth && height == layoutHeight && wallRows == rows) return;
        float done = order.length == 0 ? 0 : (float) laid / order.length;
        layoutWidth = width;
        layoutHeight = height;
        rows = wallRows;
        size = (float) height / rows;
        columns = (int) Math.ceil(width / size);
        left = (width - columns * size) / 2;

        int cells = rows * columns;
        // Row after row from the bottom, alternating directions like a mason does, a little out of line
        float[] keys = new float[cells];
        Integer[] sorted = new Integer[cells];
        for (int cell = 0; cell < cells; cell++) {
            int row = cell / columns, column = cell % columns;
            float along = (row % 2 == 0 ? column : columns - 1 - column) / (float) columns;
            keys[cell] = row + along * 0.85F + random.nextFloat() * 0.3F;
            sorted[cell] = cell;
        }
        Arrays.sort(sorted, (a, b) -> Float.compare(keys[a], keys[b]));
        order = new int[cells];
        for (int i = 0; i < cells; i++) order[i] = sorted[i];

        // A resized window keeps the share of the wall that was built, with new blocks already in place
        textures = new int[cells];
        laidAt = new long[cells];
        int kept = Math.min(cells, Math.round(done * cells));
        laid = 0;
        while (laid < kept && lay(0)) {}
    }

    /**
     * Starts the collapse: blocks break away from the middle outwards and fall off the screen.
     *
     * @param speed 1 for the normal speed, 2 for twice as fast
     */
    static void collapse(long now, float speed) {
        if (collapseAt >= 0) return;
        collapseAt = now;
        int cells = order.length;
        delay = new float[cells];
        velocityX = new float[cells];
        velocityY = new float[cells];
        spin = new float[cells];
        gravity = layoutHeight * 3.2F * speed * speed;
        float centerX = layoutWidth / 2F, centerY = layoutHeight * 0.55F;
        float reach = (float) Math
            .hypot(Math.max(centerX, layoutWidth - centerX), Math.max(centerY, layoutHeight - centerY));
        for (int cell = 0; cell < cells; cell++) {
            float dx = x(cell) + size / 2 - centerX, dy = y(cell) + size / 2 - centerY;
            float distance = (float) Math.hypot(dx, dy) / reach;
            delay[cell] = (distance * 650 + random.nextFloat() * 220) / speed;
            float push = (1.2F - distance) * layoutHeight;
            velocityX[cell] = (dx / reach * layoutHeight * 0.9F + (random.nextFloat() - 0.5F) * layoutHeight * 0.35F)
                * speed;
            velocityY[cell] = -random.nextFloat() * push * 0.6F * speed;
            spin[cell] = (random.nextFloat() - 0.5F) * 9F * speed;
        }
    }

    /** False once every block has fallen off the screen. */
    static boolean isStanding(long now) {
        if (collapseAt < 0) return laid > 0;
        for (int cell = 0; cell < order.length; cell++) {
            if (!hasFallen(cell, now)) return true;
        }
        return false;
    }

    private static boolean hasFallen(int cell, long now) {
        float t = (now - collapseAt - delay[cell]) / 1000F;
        return t > 0 && y(cell) + velocityY[cell] * t + gravity * t * t / 2 > layoutHeight + size;
    }

    private static float x(int cell) {
        return left + cell % columns * size;
    }

    private static float y(int cell) {
        return layoutHeight - (cell / columns + 1) * size;
    }

    /** Draws the laid blocks, multiplied by {@code alpha}. GL_TEXTURE_2D must be disabled, it stays so. */
    static void draw(long now, float alpha) {
        if (laid == 0 || alpha <= 0) return;
        glEnable(GL_TEXTURE_2D);
        if (collapseAt < 0) drawStanding(now, alpha);
        else drawCollapsing(now, alpha);
        glDisable(GL_TEXTURE_2D);
    }

    private static void drawStanding(long now, float alpha) {
        for (int i = 0; i < laid; i++) {
            int cell = order[i];
            long age = now - laidAt[cell];
            if (age < 0) continue;
            float p = Math.min(1F, age / (float) DROP_MS);
            float drop = 1 - p;
            drop = drop * drop * drop;
            float fade = Math.min(1F, age / 120F);
            block(cell, x(cell), y(cell) - drop * size * 0.75F, 0, 1F, alpha * fade);
        }
    }

    private static void drawCollapsing(long now, float alpha) {
        float elapsed = now - collapseAt;
        // A short shake before the wall gives way
        float shake = elapsed < 300 ? (1 - elapsed / 300F) * size * 0.05F : 0;
        float shakeX = (random.nextFloat() - 0.5F) * 2 * shake, shakeY = (random.nextFloat() - 0.5F) * 2 * shake;
        // standing blocks first, so the falling ones are drawn over them
        for (int cell = 0; cell < order.length; cell++) {
            if (now - collapseAt - delay[cell] <= 0) block(cell, x(cell) + shakeX, y(cell) + shakeY, 0, 1F, alpha);
        }
        for (int cell = 0; cell < order.length; cell++) {
            float t = (now - collapseAt - delay[cell]) / 1000F;
            if (t <= 0 || hasFallen(cell, now)) continue;
            float angle = spin[cell] * t;
            float shade = 0.8F + 0.2F * (float) Math.abs(Math.cos(angle));
            block(
                cell,
                x(cell) + velocityX[cell] * t,
                y(cell) + velocityY[cell] * t + gravity * t * t / 2,
                angle,
                shade,
                alpha);
        }
    }

    private static void block(int cell, float x, float y, float angle, float shade, float alpha) {
        if (!WallTextures.bind(textures[cell])) return;
        glColor4f(shade, shade, shade, alpha);
        float half = size / 2, cx = x + half, cy = y + half;
        float cos = (float) Math.cos(angle) * half, sin = (float) Math.sin(angle) * half;
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(cx - cos + sin, cy - sin - cos);
        glTexCoord2f(0, 1);
        glVertex2f(cx - cos - sin, cy - sin + cos);
        glTexCoord2f(1, 1);
        glVertex2f(cx + cos - sin, cy + sin + cos);
        glTexCoord2f(1, 0);
        glVertex2f(cx + cos + sin, cy + sin - cos);
        glEnd();
    }

    /** Forgets the wall, once the collapse is over. */
    static void reset() {
        order = new int[0];
        textures = new int[0];
        laidAt = new long[0];
        laid = 0;
        layoutWidth = layoutHeight = 0;
        collapseAt = -1;
        delay = velocityX = velocityY = spin = null;
    }
}
