package gtnhtweaks.modules.minecraft.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.MinecraftModule.PickupPosition;

/**
 * A list at the side of the screen of the items the player picks up: "64x Cobblestone" with the icon of the item, the
 * name in the color of its rarity. Picking up more of the same item while its line is shown adds to its count. Lines
 * slide in, stay a few seconds and slide out.
 * <p>
 * Only on the client, without a packet of its own: an item is picked up when a dropped item near the player vanishes,
 * or gets smaller, while the same item comes into the inventory, within half a second of each other. So items taken
 * from chests or crafted do not count, nor items that despawn or are picked up by others.
 */
public final class PickupNotifier {

    /** Dropped items closer than this, in blocks, are watched. */
    private static final double REACH = 6;
    /** Ticks a vanished item and an item come into the inventory wait for each other. */
    private static final int MATCH_TICKS = 10;
    private static final int LINE_HEIGHT = 18;
    private static final int MARGIN = 4;
    /** Time to slide in and out, in ms. */
    private static final float SLIDE_MS = 250;
    private static final float SLIDE_DISTANCE = 40;

    /** Items that left the ground or came into the inventory, waiting for their other half. */
    private static final class Change {

        final ItemStack stack;
        int amount;
        final long tick;

        Change(ItemStack stack, int amount, long tick) {
            this.stack = stack;
            this.amount = amount;
            this.tick = tick;
        }
    }

    private static final class Line {

        final ItemStack stack;
        final String key;
        int count;
        long shownAt;
        /** Drawn place in the list, moving smoothly to its index. */
        float place = Float.NaN;

        Line(ItemStack stack, String key, int count) {
            this.stack = stack;
            this.key = key;
            this.count = count;
            this.shownAt = Minecraft.getSystemTime();
        }
    }

    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Change> LOST = new ArrayList<>();
    private static final List<Change> GAINED = new ArrayList<>();
    private static Map<Integer, ItemStack> watched = new HashMap<>();
    private static Map<String, Integer> lastCounts;
    private static long tick;
    private static long lastFrame;
    private static RenderItem renderItem;

    private PickupNotifier() {}

    public static void register() {
        PickupNotifier notifier = new PickupNotifier();
        MinecraftForge.EVENT_BUS.register(notifier);
        FMLCommonHandler.instance()
            .bus()
            .register(notifier);
    }

    // ---- what is picked up

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (mc.theWorld == null || player == null || !MinecraftModule.pickupNotifier) {
            watched.clear();
            lastCounts = null;
            LOST.clear();
            GAINED.clear();
            if (mc.theWorld == null) LINES.clear();
            return;
        }
        if (mc.isGamePaused()) return;
        tick++;

        // what came into the inventory
        Map<String, Integer> counts = new HashMap<>();
        Map<String, ItemStack> samples = new HashMap<>();
        for (ItemStack stack : player.inventory.mainInventory) {
            String key = key(stack);
            if (key == null) continue;
            Integer count = counts.get(key);
            counts.put(key, (count == null ? 0 : count) + stack.stackSize);
            samples.put(key, stack);
        }
        if (lastCounts != null) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                Integer before = lastCounts.get(entry.getKey());
                int more = entry.getValue() - (before == null ? 0 : before);
                if (more > 0) GAINED.add(new Change(samples.get(entry.getKey()), more, tick));
            }
        }
        lastCounts = counts;

        // what left the ground near the player
        Map<Integer, ItemStack> now = new HashMap<>();
        double reach = REACH * REACH;
        List<?> entities = mc.theWorld.loadedEntityList;
        for (int i = 0; i < entities.size(); i++) {
            Object element = entities.get(i);
            if (!(element instanceof EntityItem)) continue;
            EntityItem item = (EntityItem) element;
            ItemStack stack = item.getEntityItem();
            if (item.isDead || stack == null || item.getDistanceSqToEntity(player) > reach) continue;
            now.put(item.getEntityId(), stack.copy());
        }
        for (Map.Entry<Integer, ItemStack> entry : watched.entrySet()) {
            ItemStack before = entry.getValue();
            ItemStack after = now.get(entry.getKey());
            String key = key(before);
            if (key == null) continue;
            int lost = after == null ? before.stackSize
                : key.equals(key(after)) ? before.stackSize - after.stackSize : 0;
            if (lost > 0) LOST.add(new Change(before, lost, tick));
        }
        watched = now;

        match();
    }

    /** Pairs what left the ground with what came into the inventory: that is what was picked up. */
    private static void match() {
        for (Change lost : LOST) {
            String key = key(lost.stack);
            for (Change gained : GAINED) {
                if (lost.amount <= 0) break;
                if (gained.amount <= 0 || !key.equals(key(gained.stack))) continue;
                int amount = Math.min(lost.amount, gained.amount);
                lost.amount -= amount;
                gained.amount -= amount;
                show(lost.stack, amount);
            }
        }
        LOST.removeIf(change -> change.amount <= 0 || tick - change.tick > MATCH_TICKS);
        GAINED.removeIf(change -> change.amount <= 0 || tick - change.tick > MATCH_TICKS);
    }

    /** Adds to the line of the item, or makes a new one at the end of the list. */
    private static void show(ItemStack stack, int amount) {
        String key = key(stack) + "|" + stack.getDisplayName();
        long now = Minecraft.getSystemTime();
        for (Iterator<Line> it = LINES.iterator(); it.hasNext();) {
            Line line = it.next();
            if (!line.key.equals(key) || leaving(line, now)) continue;
            it.remove();
            line.count += amount;
            line.shownAt = now - (long) SLIDE_MS;
            LINES.add(line);
            return;
        }
        ItemStack shown = stack.copy();
        shown.stackSize = 1;
        LINES.add(new Line(shown, key, amount));
        while (LINES.size() > MinecraftModule.pickupMaxLines) LINES.remove(0);
    }

    /** Same item: same id and, for items with subtypes, the same damage. Their NBT does not matter. */
    private static String key(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        Item item = stack.getItem();
        return Item.getIdFromItem(item) + ":" + (item.getHasSubtypes() ? stack.getItemDamage() : 0);
    }

    // ---- drawing

    private static long lifeMs() {
        return MinecraftModule.pickupSeconds * 1000L;
    }

    private static boolean leaving(Line line, long now) {
        return now - line.shownAt > lifeMs() - SLIDE_MS;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        long now = Minecraft.getSystemTime();
        float frameMs = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        LINES.removeIf(line -> now - line.shownAt > lifeMs());
        if (LINES.isEmpty() || !MinecraftModule.pickupNotifier) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        if (renderItem == null) renderItem = new RenderItem();
        ScaledResolution resolution = event.resolution;
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        PickupPosition position = MinecraftModule.pickupPosition;
        boolean right = position.right();
        int total = LINES.size() * LINE_HEIGHT;
        int top;
        switch (position) {
            case TOP_RIGHT:
            case TOP_LEFT:
                top = MARGIN;
                break;
            case MIDDLE_RIGHT:
            case MIDDLE_LEFT:
                top = (height - total) / 2;
                break;
            default:
                top = height - MARGIN - total;
                break;
        }

        GL11.glPushMatrix();
        for (int i = 0; i < LINES.size(); i++) {
            Line line = LINES.get(i);
            // the lines move to their places smoothly when one leaves or comes
            if (Float.isNaN(line.place)) line.place = i;
            line.place += (i - line.place) * Math.min(1, frameMs / 80F);
            float age = now - line.shownAt;
            float in = Math.min(1, age / SLIDE_MS);
            float out = Math.max(0, (age - (lifeMs() - SLIDE_MS)) / SLIDE_MS);
            float slide = (1 - ease(in)) + ease(out);
            float alpha = Math.min(in, 1 - out);
            if (alpha <= 0.02F) continue;
            float shift = slide * SLIDE_DISTANCE * (right ? 1 : -1);
            int y = Math.round(top + line.place * LINE_HEIGHT);

            String text = line.count + "x " + line.stack.getDisplayName();
            EnumRarity rarity = line.stack.getRarity();
            EnumChatFormatting color = rarity == null || rarity == EnumRarity.common ? EnumChatFormatting.WHITE
                : rarity.rarityColor;
            text = color + text;
            int textWidth = font.getStringWidth(text);
            int iconX = right ? width - MARGIN - 16 : MARGIN;
            int textX = right ? iconX - 3 - textWidth : iconX + 16 + 3;

            GL11.glPushMatrix();
            GL11.glTranslatef(shift, 0, 0);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int argb = Math.max(5, Math.round(alpha * 255)) << 24 | 0xFFFFFF;
            font.drawStringWithShadow(text, textX, y + 5, argb);
            drawIcon(mc, line.stack, iconX, y + 1);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void drawIcon(Minecraft mc, ItemStack stack, int x, int y) {
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_BLEND);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }
}
