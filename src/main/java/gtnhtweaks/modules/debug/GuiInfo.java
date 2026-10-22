package gtnhtweaks.modules.debug;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.GTNHTweaks;

/**
 * Ctrl + Shift + F12 in an open screen: writes the mod, name, class and FPS of the screen to the chat and copies the
 * same text to the clipboard. Also measures how long the screen takes to draw.
 */
public class GuiInfo {

    private static final long NOTICE_MS = 2500;

    private boolean keysWereDown;
    private long drawStart;
    private GuiScreen measured;
    /** Average time of GuiScreen.drawScreen in milliseconds, -1 until measured. */
    private double drawMs = -1;
    private long noticeUntil;

    static void register() {
        GuiInfo info = new GuiInfo();
        MinecraftForge.EVENT_BUS.register(info);
        FMLCommonHandler.instance()
            .bus()
            .register(info);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !DebugModule.guiInfo || !Keyboard.isCreated()) return;
        boolean down = Keyboard.isKeyDown(Keyboard.KEY_F12)
            && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
            && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (down && !keysWereDown && screen != null) report(screen);
        keysWereDown = down;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        drawStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (drawStart != 0) {
            double ms = (System.nanoTime() - drawStart) / 1_000_000D;
            drawMs = measured == event.gui && drawMs >= 0 ? drawMs * 0.9 + ms * 0.1 : ms;
            measured = event.gui;
            drawStart = 0;
        }
        if (Minecraft.getSystemTime() < noticeUntil) drawNotice(event.gui);
    }

    private void report(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        List<String[]> rows = new ArrayList<>();
        Class<?> type = screen.getClass();
        rows.add(new String[] { "Mod", ModLookup.describe(type) });
        rows.add(new String[] { "Screen", simpleName(type) });
        rows.add(new String[] { "Class", type.getName() });
        Class<?> base = vanillaBase(type);
        if (base != null && base != type) rows.add(new String[] { "Extends", base.getName() });
        if (screen instanceof GuiContainer && ((GuiContainer) screen).inventorySlots != null) {
            Class<?> container = ((GuiContainer) screen).inventorySlots.getClass();
            rows.add(new String[] { "Container", container.getName() });
            String containerMod = ModLookup.describe(container);
            if (!containerMod.equals(rows.get(0)[1])) rows.add(new String[] { "Container mod", containerMod });
        }
        rows.add(new String[] { "FPS", fps(mc) });
        rows.add(
            new String[] { "Size",
                screen.width + " x " + screen.height + ", GUI scale "
                    + new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor() });

        StringBuilder text = new StringBuilder("GUI info");
        IChatComponent header = new ChatComponentText("[GTNHTweaks] ")
            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
        header.appendSibling(
            new ChatComponentText("GUI info").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
        print(mc, header);
        for (String[] row : rows) {
            text.append('\n')
                .append(row[0])
                .append(": ")
                .append(row[1]);
            IChatComponent line = new ChatComponentText(" " + row[0] + ": ")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
            line.appendSibling(
                new ChatComponentText(row[1]).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.WHITE)));
            print(mc, line);
        }

        GuiScreen.setClipboardString(text.toString());
        GTNHTweaks.LOG.info(text);
        noticeUntil = Minecraft.getSystemTime() + NOTICE_MS;
    }

    /** Chat lines are kept even without a world, so they also show up after leaving the main menu. */
    private static void print(Minecraft mc, IChatComponent line) {
        if (mc.ingameGUI != null) mc.ingameGUI.getChatGUI()
            .printChatMessage(line);
    }

    /** "144 (frame 6.9 ms, screen drawing 1.2 ms)". */
    private String fps(Minecraft mc) {
        int fps = parseFps(mc.debug);
        StringBuilder out = new StringBuilder(fps >= 0 ? Integer.toString(fps) : "?");
        List<String> details = new ArrayList<>();
        if (fps > 0) details.add(String.format("frame %.1f ms", 1000D / fps));
        if (drawMs >= 0 && measured == mc.currentScreen) details.add(String.format("screen drawing %.2f ms", drawMs));
        if (!details.isEmpty()) out.append(" (")
            .append(String.join(", ", details))
            .append(')');
        return out.toString();
    }

    /** Minecraft.debug starts with the FPS of the last second: "144 fps, 3 chunk updates". */
    private static int parseFps(String debug) {
        if (debug == null) return -1;
        int end = 0;
        while (end < debug.length() && Character.isDigit(debug.charAt(end))) end++;
        return end == 0 ? -1 : Integer.parseInt(debug.substring(0, end));
    }

    private static String simpleName(Class<?> type) {
        String name = type.getSimpleName();
        if (!name.isEmpty()) return name;
        String full = type.getName();
        return full.substring(full.lastIndexOf('.') + 1);
    }

    /** The closest Minecraft or Forge screen class the screen extends, like GuiContainer. */
    private static Class<?> vanillaBase(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("net.minecraft.") || name.startsWith("cpw.mods.fml.")) return c;
        }
        return null;
    }

    private static void drawNotice(GuiScreen screen) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = "GUI info copied to the clipboard";
        int x = (screen.width - font.getStringWidth(text)) / 2;
        GuiScreen.drawRect(x - 4, 2, x + font.getStringWidth(text) + 4, 14, 0xC0000000);
        font.drawStringWithShadow(text, x, 4, 0xFFFFAA00);
    }
}
