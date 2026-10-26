package gtnhtweaks.modules.minecraft.client;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;

/**
 * Marks the items that are new in the inventory of the player with a little star in the corner of their slot, until
 * the mouse is brought onto them in an inventory.
 * <p>
 * An item is new when it comes into the inventory and was not in it before in this session, whether picked up, crafted
 * or taken from a chest. What is in the inventory on joining a world is not new. Damage of items without subtypes,
 * as of tools, does not make another item, nor does NBT. A new item that leaves the inventory before it is looked at
 * loses its star.
 */
public final class NewItems {

    /** Ticks after joining a world in which the items coming in are the inventory sent by the server, not new. */
    private static final int JOIN_TICKS = 40;
    private static final int OUTLINE = 0xFF5A3A00;
    private static final int FILL = 0xFFFFD23C;
    private static final int SHINE = 0xFFFFFFF0;

    /** The items that were in the inventory in this session, by {@link #key}. */
    private static final Set<String> KNOWN = new HashSet<>();
    /** The known items not looked at yet. */
    private static final Set<String> NEW = new HashSet<>();
    private static int ticksInWorld;

    private NewItems() {}

    public static void register() {
        NewItems tracker = new NewItems();
        FMLCommonHandler.instance()
            .bus()
            .register(tracker);
        MinecraftForge.EVENT_BUS.register(tracker);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            KNOWN.clear();
            NEW.clear();
            ticksInWorld = 0;
            return;
        }
        if (ticksInWorld < JOIN_TICKS) ticksInWorld++;
        boolean mark = MinecraftModule.newItemStars && ticksInWorld >= JOIN_TICKS;
        Set<String> present = new HashSet<>();
        for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
            String key = key(stack);
            if (key == null) continue;
            present.add(key);
            if (KNOWN.add(key) && mark) NEW.add(key);
        }
        for (Iterator<String> it = NEW.iterator(); it.hasNext();) {
            if (!present.contains(it.next())) it.remove();
        }
    }

    /** The stars on the hotbar, so a picked up item shows as new at once. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR || NEW.isEmpty()
            || !MinecraftModule.newItemStars) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        ScaledResolution resolution = event.resolution;
        int left = resolution.getScaledWidth() / 2 - 90;
        // where vanilla draws the items of the hotbar
        int top = resolution.getScaledHeight() - 19;
        for (int i = 0; i < 9; i++) {
            if (isNew(mc.thePlayer.inventory.mainInventory[i])) drawStar(left + i * 20 + 2, top);
        }
    }

    /** Whether the slot holds a new item of the inventory of the player. */
    public static boolean isNew(Slot slot) {
        if (!MinecraftModule.newItemStars || slot == null) return false;
        ItemStack stack = slot.getStack();
        return isNew(stack) && ofPlayer(slot, stack);
    }

    /**
     * Whether the slot shows the inventory of the player. Not only by its inventory: mods and the creative inventory
     * show it through slots of their own, whose stack is still the one in the inventory of the player.
     */
    private static boolean ofPlayer(Slot slot, ItemStack stack) {
        if (slot.inventory instanceof InventoryPlayer) return true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        for (ItemStack own : mc.thePlayer.inventory.mainInventory) {
            if (own == stack) return true;
        }
        return false;
    }

    private static boolean isNew(ItemStack stack) {
        if (NEW.isEmpty()) return false;
        String key = key(stack);
        return key != null && NEW.contains(key);
    }

    /** The mouse is on the slot: its item is not new any more. */
    public static void seen(Slot slot) {
        // whatever slot shows it: looked at is looked at
        if (slot != null && isNew(slot.getStack())) NEW.remove(key(slot.getStack()));
    }

    /** Draws the star of a new item in the corner of the slot, over its item. */
    public static void drawStar(Slot slot) {
        drawStar(slot.xDisplayPosition, slot.yDisplayPosition);
    }

    /**
     * A little star, twinkling, its upper left corner a pixel out of the corner of the slot.
     *
     * @param x the left of the slot
     * @param y the top of the slot
     */
    private static void drawStar(int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        // over the item and its count
        GL11.glTranslatef(0, 0, 320);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        x -= 1;
        y -= 1;
        // a diamond of 5 by 5 pixels in an outline, its middle shining now and then
        cross(x, y, 3, OUTLINE);
        cross(x, y, 2, FILL);
        boolean twinkle = Minecraft.getSystemTime() % 1200 < 300;
        pixel(x + 3, y + 3, twinkle ? SHINE : FILL);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** The pixels within the given distance of (x + 3, y + 3), in steps along x and y. */
    private static void cross(int x, int y, int reach, int argb) {
        for (int dy = -reach; dy <= reach; dy++) {
            int half = reach - Math.abs(dy);
            rect(x + 3 - half, y + 3 + dy, x + 4 + half, y + 4 + dy, argb);
        }
    }

    private static void pixel(int x, int y, int argb) {
        rect(x, y, x + 1, y + 1, argb);
    }

    private static void rect(int x0, int y0, int x1, int y1, int argb) {
        GL11.glColor4ub((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x0, y1);
        GL11.glVertex2i(x1, y1);
        GL11.glVertex2i(x1, y0);
        GL11.glVertex2i(x0, y0);
        GL11.glEnd();
    }

    /** What makes an item another one: the item, and its damage if it has subtypes. Null for an empty stack. */
    private static String key(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Item item = stack.getItem();
        int meta = item.getHasSubtypes() ? stack.getItemDamage() : 0;
        return Item.getIdFromItem(item) + ":" + meta;
    }
}
