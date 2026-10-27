package gtnhtweaks.modules.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.minecraft.MinecraftModule.ScreenAnimationStyle;

/**
 * Every screen opens with a short animation, the ones of mods too: it grows from a bit smaller and rises from a bit
 * lower into its place, slowing down at the end. The background, the dimmed or blurred world, stays still, so the
 * edges of the screen never show the world.
 * <p>
 * It works for any screen as Forge tells when every screen is drawn: the whole drawing of the screen is scaled and
 * moved around its middle, and the background is drawn without it. Only the opening is animated: a closed screen is
 * gone at once.
 */
public final class ScreenAnimation {

    /** Size the screen grows from and how far below it rises from, in GUI pixels, at the start. */
    private static final float START_SCALE = 0.9F;
    private static final float START_DROP = 14F;

    private static GuiScreen shown;
    private static long openedAt;
    /** Whether the shown screen is animated: not when opened over another one, unless asked. */
    private static boolean animated;
    /** The transform of the frame being drawn, and whether it was pushed. */
    private static float scale = 1, drop;
    private static boolean pushed;
    private static boolean unscaled;

    private ScreenAnimation() {}

    public static void register() {
        ScreenAnimation animation = new ScreenAnimation();
        MinecraftForge.EVENT_BUS.register(animation);
        FMLCommonHandler.instance()
            .bus()
            .register(animation);
    }

    /** Forgets the shown screen when none is open, so the next one counts as opened from the game. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (Minecraft.getMinecraft().currentScreen == null) shown = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        pushed = false;
        GuiScreen screen = event.gui;
        if (screen != shown) {
            // a new screen: its animation starts now
            animated = shown == null || MinecraftModule.animateBetweenScreens;
            shown = screen;
            openedAt = Minecraft.getSystemTime();
        }
        if (!animated || event.isCanceled() || !MinecraftModule.screenAnimation || excluded(screen)) return;
        float t = (Minecraft.getSystemTime() - openedAt) / (float) Math.max(1, MinecraftModule.screenAnimationMs);
        if (t >= 1) return;
        // fast at first, slowing down into place
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        ScreenAnimationStyle style = MinecraftModule.screenAnimationStyle;
        scale = style == ScreenAnimationStyle.SLIDE ? 1 : START_SCALE + (1 - START_SCALE) * eased;
        drop = style == ScreenAnimationStyle.SCALE ? 0 : START_DROP * (1 - eased);
        float middleX = screen.width / 2F, middleY = screen.height / 2F;
        GL11.glPushMatrix();
        GL11.glTranslatef(middleX, middleY + drop, 0);
        GL11.glScalef(scale, scale, 1);
        GL11.glTranslatef(-middleX, -middleY, 0);
        pushed = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!pushed) return;
        GL11.glPopMatrix();
        pushed = false;
    }

    /**
     * Draws the background of the screen without the animation, so it covers the whole screen from the first frame.
     *
     * @return false if there is no animation now: the background is drawn as usual
     */
    public static boolean drawUnscaled(GuiScreen screen, Runnable background) {
        if (!pushed || unscaled || screen != shown) return false;
        float middleX = screen.width / 2F, middleY = screen.height / 2F;
        unscaled = true;
        GL11.glPushMatrix();
        // the reverse of the animation
        GL11.glTranslatef(middleX, middleY, 0);
        GL11.glScalef(1 / scale, 1 / scale, 1);
        GL11.glTranslatef(-middleX, -middleY - drop, 0);
        try {
            background.run();
        } finally {
            GL11.glPopMatrix();
            unscaled = false;
        }
        return true;
    }

    /** The screens left still: by the simple or full name of their class. */
    private static boolean excluded(GuiScreen screen) {
        Class<?> type = screen.getClass();
        for (String name : MinecraftModule.screenAnimationExcluded) {
            if (name.equals(type.getSimpleName()) || name.equals(type.getName())) return true;
        }
        return false;
    }
}
