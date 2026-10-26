package gtnhtweaks.modules.minecraft.client;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.lwjgl.opengl.GL11;

/**
 * The item of a slot squeezes and springs back when the mouse comes onto it, to show that it is hovered. Every slot
 * keeps its own bounce, so the one left a moment ago finishes its spring while the next one starts.
 */
public final class SlotHoverBounce {

    /** How long a bounce lasts, in milliseconds. */
    private static final long DURATION = 400;
    /** How much the item shrinks at most. */
    private static final float DEPTH = 0.25F;
    /** Speed of the spring, in radians per second: the item is the smallest after about 50 ms. */
    private static final float FREQUENCY = 26F;
    /** How fast the spring calms down, per second. */
    private static final float DAMPING = 10F;

    /** Start of the bounce of each slot, in milliseconds. */
    private static final Map<Slot, Long> STARTS = new IdentityHashMap<>();
    private static Container container;
    private static Slot hovered;

    private SlotHoverBounce() {}

    /** Called before the slots of a screen are drawn, with the slot under the mouse, or null. */
    public static void update(Container current, Slot underMouse) {
        long now = Minecraft.getSystemTime();
        if (current != container) {
            container = current;
            STARTS.clear();
            hovered = null;
        }
        // only a slot with an item bounces: hovering an empty slot shows nothing
        Slot target = underMouse != null && underMouse.getHasStack() ? underMouse : null;
        if (target != hovered) {
            hovered = target;
            if (target != null) STARTS.put(target, now);
        }
        for (Iterator<Long> it = STARTS.values()
            .iterator(); it.hasNext();) {
            if (now - it.next() > DURATION) it.remove();
        }
    }

    /** Opens the scale of the item of the slot around its center, closed by {@link #end}. */
    public static void begin(Slot slot) {
        GL11.glPushMatrix();
        Long start = STARTS.get(slot);
        if (start == null) return;
        float t = (Minecraft.getSystemTime() - start) / 1000F;
        // a damped spring: shrinks first, then grows a little over the full size and settles
        float scale = 1F - DEPTH * (float) (Math.sin(FREQUENCY * t) * Math.exp(-DAMPING * t));
        float cx = slot.xDisplayPosition + 8F;
        float cy = slot.yDisplayPosition + 8F;
        GL11.glTranslatef(cx, cy, 0F);
        GL11.glScalef(scale, scale, 1F);
        GL11.glTranslatef(-cx, -cy, 0F);
    }

    public static void end() {
        GL11.glPopMatrix();
    }
}
