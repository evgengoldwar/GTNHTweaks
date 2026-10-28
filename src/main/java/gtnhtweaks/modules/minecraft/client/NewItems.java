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
 * the mouse is brought onto them in an inventory or they are chosen in the hotbar.
 * <p>
 * An item is new when it comes into the inventory and was not in it before in this session, whether picked up, crafted
 * or taken from a chest. What is in the inventory on joining a world is not new. Damage of items without subtypes,
 * as of tools, does not make another item, nor does NBT. A new item that leaves the inventory before it is looked at
 * loses its star.
 */
public final class NewItems {

    /** Ticks after joining a world in which the items coming in are the inventory sent by the server, not new. */
    private static final int JOIN_TICKS = 40;
    /**
     * The star, a sparkle of four rays: tips, rays, the glow around the middle and the middle. The second frame is the
     * twinkle, rays drawn in and the middle flashing.
     */
    private static final String[] STAR = { "...a...", "...b...", "..bcb..", "abcwcba", "..bcb..", "...b...",
        "...a..." };
    private static final String[] TWINKLE = { ".......", "...c...", "..cwc..", ".cwwwc.", "..cwc..", "...c...",
        "......." };
    private static final int TIP = 0xFFFFA820;
    private static final int RAY = 0xFFFFD84A;
    private static final int GLOW = 0xFFFFF3A6;
    private static final int MIDDLE = 0xFFFFFFFF;
    /** The shadow under the star, a pixel down and right, so it stands out on light items too. */
    private static final int SHADOW = 0x90301800;
    /** A twinkle every so many milliseconds, for a short while. */
    private static final long TWINKLE_EVERY = 1500, TWINKLE_FOR = 220;

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
        // chosen in the hotbar is looked at, as the mouse on its slot in an inventory
        String held = key(mc.thePlayer.inventory.getCurrentItem());
        if (held != null) NEW.remove(held);
    }

    /** The stars on the hotbar, so a picked up item shows as new at once. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR || NEW.isEmpty() || !MinecraftModule.newItemStars)
            return;
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
     * A little golden sparkle, twinkling now and then, its upper left corner a pixel out of the corner of the slot.
     *
     * @param x the left of the slot
     * @param y the top of the slot
     */
    private static void drawStar(int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        // over the item and its count
        GL11.glTranslatef(0, 0, 320);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        x -= 1;
        y -= 1;
        // each slot twinkles at its own time, so the stars do not blink all at once
        long time = Minecraft.getSystemTime() + (x * 31L + y * 17L) * 7;
        String[] frame = time % TWINKLE_EVERY < TWINKLE_FOR ? TWINKLE : STAR;
        drawPixels(frame, x + 1, y + 1, true);
        drawPixels(frame, x, y, false);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** Draws a frame of the star, or its shadow in one color. */
    private static void drawPixels(String[] frame, int x, int y, boolean shadow) {
        for (int row = 0; row < frame.length; row++) {
            for (int column = 0; column < frame[row].length(); column++) {
                int argb;
                switch (frame[row].charAt(column)) {
                    case 'a':
                        argb = TIP;
                        break;
                    case 'b':
                        argb = RAY;
                        break;
                    case 'c':
                        argb = GLOW;
                        break;
                    case 'w':
                        argb = MIDDLE;
                        break;
                    default:
                        continue;
                }
                int px = x + column, py = y + row;
                rect(px, py, px + 1, py + 1, shadow ? SHADOW : argb);
            }
        }
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
