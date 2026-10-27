package gtnhtweaks.modules.minecraft.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

/**
 * The bounce of {@link SlotHoverBounce} for the items of the NEI panels: the item list, the bookmarks and the other
 * grids. They are not slots of the inventory but cells of a grid drawn by NEI, so a bounce belongs to a place on the
 * screen. NEI can draw a grid into a picture refreshed a few times a second: it is drawn anew on every frame while an
 * item bounces.
 */
public final class NEIHoverBounce {

    private static final class Cell {

        final int x, y, size;
        final long start;

        Cell(int x, int y, int size, long start) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.start = start;
        }

        boolean contains(float px, float py) {
            return px >= x && px < x + size && py >= y && py < y + size;
        }
    }

    /** One more frame after the end of the bounces, so a cached grid is drawn back at the full size. */
    private static final long LAST_FRAME = 100;

    private static final List<Cell> CELLS = new ArrayList<>();
    /** The hovered cell of each grid, as x, y and size in one number, or -1. */
    private static final Map<Object, Long> HOVERED = new WeakHashMap<>();

    /** The scale applied by {@link #begin}, undone by {@link #end}. */
    private static float applied = 1F;
    private static float centerX, centerY;

    private NEIHoverBounce() {}

    /** Called before a grid is drawn, with its cell under the mouse, or a negative size for none. */
    public static void update(Object grid, int x, int y, int size) {
        long now = Minecraft.getSystemTime();
        long key = size <= 0 ? -1 : ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (size & 0xFFFFF);
        Long last = HOVERED.put(grid, key);
        if (key != -1 && (last == null || last != key)) {
            for (Iterator<Cell> it = CELLS.iterator(); it.hasNext();) {
                Cell cell = it.next();
                if (cell.x == x && cell.y == y) it.remove();
            }
            CELLS.add(new Cell(x, y, size, now));
        }
        for (Iterator<Cell> it = CELLS.iterator(); it.hasNext();) {
            if (!SlotHoverBounce.playing(it.next().start, now - LAST_FRAME)) it.remove();
        }
    }

    /** True while an item of a grid bounces: a cached grid has to be drawn again. */
    public static boolean animating() {
        return !CELLS.isEmpty();
    }

    /**
     * Scales the item drawn in the given area around the center of its cell, undone by {@link #end}. Without a push
     * of the matrix: NEI checks that drawing an item leaves the depth of the matrix stack as it was.
     */
    public static void begin(int x, int y, int w, int h) {
        applied = 1F;
        if (CELLS.isEmpty()) return;
        float px = x + w / 2F;
        float py = y + h / 2F;
        for (Cell cell : CELLS) {
            if (!cell.contains(px, py)) continue;
            float scale = SlotHoverBounce.scale(cell.start);
            if (scale == 1F) return;
            applied = scale;
            centerX = cell.x + cell.size / 2F;
            centerY = cell.y + cell.size / 2F;
            GL11.glTranslatef(centerX, centerY, 0F);
            GL11.glScalef(scale, scale, 1F);
            GL11.glTranslatef(-centerX, -centerY, 0F);
            return;
        }
    }

    public static void end() {
        if (applied == 1F) return;
        GL11.glTranslatef(centerX, centerY, 0F);
        GL11.glScalef(1F / applied, 1F / applied, 1F);
        GL11.glTranslatef(-centerX, -centerY, 0F);
        applied = 1F;
    }
}
