package gtnhtweaks.modules.menus;

import static org.lwjgl.opengl.GL11.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gtnhtweaks.common.ResourcePackOverrides;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.common.render.ThemeShapes;
import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.mixins.early.common.AccessorGuiButton;
import gtnhtweaks.modules.mainmenu.MainMenuModule;

/** Decides what is restyled and draws the themed parts. Called from the menus mixins. */
public final class MenuStyle {

    private static final String[] VANILLA_PACKAGES = { "net.minecraft.", "cpw.mods.fml.", "net.minecraftforge." };
    private static final String[] DRAW_BUTTON_NAMES = { "drawButton", "func_146112_a" };

    /** Vanilla colors that get theme colors. */
    private static final int VANILLA_SELECTION_BORDER = 0x808080;
    private static final int VANILLA_SELECTION_FILL = 0x000000;
    private static final int VANILLA_FIELD_BORDER = 0xFFA0A0A0;
    private static final int VANILLA_FIELD_FILL = 0xFF000000;
    private static final int VANILLA_INVENTORY_TITLE = 0x404040;
    /** Base gray of the vanilla inventory textures. */
    private static final int VANILLA_INVENTORY_GRAY = 0xC6;

    private static final ResourceLocation BUTTON_TEXTURES = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation(
        "textures/gui/options_background.png");

    private static final Map<Class<?>, Boolean> SCREEN_MATCHES = new HashMap<>();
    private static final Map<Class<?>, Boolean> CUSTOM_DRAWN_BUTTONS = new HashMap<>();

    /** True while the current screen is drawn, so the HUD drawn before it is never touched. */
    private static boolean inScreenDraw;
    /** Textures bound while an inventory draws its background, to find out if a resource pack replaces them. */
    private static final List<ResourceLocation> INVENTORY_TEXTURES = new ArrayList<>();
    private static boolean recordingInventoryTextures;
    /** True while a themed button runs mouseDragged, where sliders draw their knob. */
    private static boolean inSliderKnob;
    /** True while a tooltip is drawn: tooltips keep their own dark background, so their text is not recolored. */
    private static boolean inTooltip;

    private MenuStyle() {}

    public static GTNHTheme theme() {
        return MenusModule.style;
    }

    public static void setInTooltip(boolean value) {
        inTooltip = value;
    }

    // ---- scopes

    private static GuiScreen currentScreen() {
        if (!inScreenDraw || !MenusModule.INSTANCE.isEnabled()) return null;
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.currentScreen;
    }

    private static boolean inGame() {
        return Minecraft.getMinecraft().theWorld != null;
    }

    /** True while a restyled menu screen is drawn: backgrounds, lists, buttons, text fields and text are themed. */
    public static boolean isActive() {
        GuiScreen screen = currentScreen();
        if (screen == null || screen instanceof GuiContainer) return false;
        if (inGame() && !MenusModule.themeInGame) return false;
        if (screen instanceof GuiMainMenu && MainMenuModule.INSTANCE.isEnabled()) return false;
        return matches(screen.getClass());
    }

    /** True if resource packs are respected and an enabled one replaces the texture. */
    private static boolean replacedByPack(ResourceLocation texture) {
        return MenusModule.respectResourcePacks && ResourcePackOverrides.isOverridden(texture);
    }

    /** Backgrounds, lists, text fields and text colors: themed unless a pack replaces the dirt background. */
    public static boolean themesBackground() {
        return isActive() && !replacedByPack(OPTIONS_BACKGROUND);
    }

    /** Buttons and sliders: themed unless a pack replaces the button texture. */
    public static boolean themesButtons() {
        return isActive() && !replacedByPack(BUTTON_TEXTURES);
    }

    /** True while an inventory is drawn and inventories are themed. */
    private static GuiContainer activeInventory() {
        GuiScreen screen = currentScreen();
        if (!(screen instanceof GuiContainer) || !MenusModule.themeInventories) return null;
        return isExcluded(screen.getClass()) ? null : (GuiContainer) screen;
    }

    /** Vanilla inventory textures are not tinted in the LIGHT style: a multiplied tint can only darken. */
    public static boolean tintsInventory(GuiContainer gui) {
        return gui == activeInventory() && theme() != GTNHTheme.LIGHT
            && isVanilla(
                gui.getClass()
                    .getName());
    }

    /** Starts recording the textures the inventory binds for its background. */
    public static void beginInventoryBackground() {
        INVENTORY_TEXTURES.clear();
        recordingInventoryTextures = true;
    }

    /**
     * @return true if a resource pack replaces one of the textures the inventory background used. Such textures are
     *         never tinted, whatever yieldToResourcePacks says: the pack already gives them its own colors.
     */
    public static boolean endInventoryBackground() {
        recordingInventoryTextures = false;
        for (ResourceLocation texture : INVENTORY_TEXTURES) {
            if (ResourcePackOverrides.isOverridden(texture)) return true;
        }
        return false;
    }

    public static void onBindTexture(ResourceLocation texture) {
        if (recordingInventoryTextures && texture != null && INVENTORY_TEXTURES.size() < 16) {
            INVENTORY_TEXTURES.add(texture);
        }
    }

    private static boolean matches(Class<?> type) {
        Boolean match = SCREEN_MATCHES.get(type);
        if (match == null) {
            match = !isExcluded(type) && isThemedScreen(type);
            SCREEN_MATCHES.put(type, match);
        }
        return match;
    }

    private static boolean isExcluded(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (MenusModule.excludedScreens.contains(c.getName())) return true;
        }
        return false;
    }

    /** A vanilla screen, or a mod screen extending one (e.g. a mod config screen), or a configured extra screen. */
    private static boolean isThemedScreen(Class<?> type) {
        for (Class<?> c = type; c != null && c != GuiScreen.class; c = c.getSuperclass()) {
            if (MenusModule.extraScreens.contains(c.getName())) return true;
            if (MenusModule.themeVanillaScreens && isVanilla(c.getName())) return true;
        }
        return false;
    }

    private static boolean isVanilla(String className) {
        for (String prefix : VANILLA_PACKAGES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    // ---- buttons

    /**
     * Buttons are themed unless their class draws itself: a subclass of {@code base} that overrides drawButton (icon
     * buttons, check boxes, mod buttons with textures). Plain subclasses and sliders are themed.
     */
    public static boolean canTheme(GuiButton button, Class<?> base) {
        Class<?> type = button.getClass();
        Boolean custom = CUSTOM_DRAWN_BUTTONS.get(type);
        if (custom == null) {
            try {
                custom = overridesDrawButton(type, base);
            } catch (Throwable t) {
                // Reflection on mod classes can fail on missing optional classes: leave such buttons alone
                custom = true;
            }
            CUSTOM_DRAWN_BUTTONS.put(type, custom);
        }
        return !custom;
    }

    private static boolean overridesDrawButton(Class<?> type, Class<?> base) {
        for (Class<?> c = type; c != null && c != base; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 3 || params[0] != Minecraft.class
                    || params[1] != int.class
                    || params[2] != int.class) continue;
                for (String name : DRAW_BUTTON_NAMES) {
                    if (name.equals(method.getName())) return true;
                }
            }
        }
        return false;
    }

    /**
     * Same order as vanilla drawButton: frame, mouseDragged (slider value and knob), label. The button texture is bound
     * like vanilla does, and the knob textures drawn by mouseDragged are replaced, see {@link #drawSliderKnob}.
     */
    public static void drawButton(GuiButton button, Minecraft mc, int mouseX, int mouseY) {
        if (!button.visible) return;
        ThemedButton.drawFrame(button, mouseX, mouseY, theme());
        mc.getTextureManager()
            .bindTexture(BUTTON_TEXTURES);
        inSliderKnob = true;
        try {
            ((AccessorGuiButton) button).gtnhtweaks$mouseDragged(mc, mouseX, mouseY);
        } finally {
            inSliderKnob = false;
        }
        ThemedButton.drawLabel(button, button.displayString, mc.fontRenderer, mouseX, mouseY, theme());
    }

    /** Draws a themed slider knob part in place of a knob texture, if a themed button is drawing its knob. */
    public static boolean drawSliderKnob(int x, int y, int width, int height) {
        if (!inSliderKnob) return false;
        ThemedButton.drawKnob(x, y, width, height, theme());
        return true;
    }

    // ---- text

    /** Maps the white and gray text colors of vanilla screens to the theme. Colored text is kept. */
    public static int textColor(int color) {
        if (!MenusModule.recolorText || inTooltip) return color;
        GuiContainer inventory = activeInventory();
        if (inventory != null) {
            // Only the dark gray titles of tinted vanilla inventories, the rest of their text is drawn on items
            if (tintsInventory(inventory) && (color & 0xFFFFFF) == VANILLA_INVENTORY_TITLE) {
                return color & 0xFF000000 | theme().text & 0xFFFFFF;
            }
            return color;
        }
        if (!themesBackground()) return color;
        GTNHTheme theme = theme();
        int rgb = color & 0xFFFFFF;
        if (rgb == (theme.text & 0xFFFFFF) || rgb == (theme.textDim & 0xFFFFFF)
            || rgb == (theme.textDisabled & 0xFFFFFF)) return color;
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        int alpha = color & 0xFF000000;
        if (min >= 0xC8 && max - min <= 0x30) return alpha | (theme.text & 0xFFFFFF);
        if (max - min <= 0x10 && max >= 0x60) return alpha | (theme.textDim & 0xFFFFFF);
        return color;
    }

    /** Text shadows look dirty on the light style. */
    public static boolean suppressShadow() {
        return MenusModule.recolorText && !inTooltip && !theme().textShadow && themesBackground();
    }

    // ---- lists and text fields

    public static int selectionColor(int color) {
        if (!themesBackground()) return color;
        if (color == VANILLA_SELECTION_BORDER) return theme().selectionBorder & 0xFFFFFF;
        if (color == VANILLA_SELECTION_FILL) return theme().selectionFill & 0xFFFFFF;
        return color;
    }

    public static int textFieldColor(int color) {
        if (!themesBackground()) return color;
        if (color == VANILLA_FIELD_BORDER) return theme().isBeveled() ? theme().inset.bottomRight : theme().barBorder;
        if (color == VANILLA_FIELD_FILL) return theme().barBackground;
        return color;
    }

    // ---- drawing

    /** Full screen background, in place of the dirt texture. */
    public static void drawScreenBackground(int width, int height) {
        begin();
        try {
            GLDraw.verticalGradient(0, 0, width, height, theme().backgroundTop, theme().backgroundBottom);
        } finally {
            end();
        }
    }

    /** The part of the screen background between y0 and y1, used for the strips above and below lists. */
    public static void drawBackgroundSlice(int x0, int y0, int x1, int y1) {
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        int height = screen != null && screen.height > 0 ? screen.height : Math.max(1, y1);
        GTNHTheme theme = theme();
        begin();
        try {
            GLDraw.verticalGradient(
                x0,
                y0,
                x1 - x0,
                y1 - y0,
                lerp(theme.backgroundTop, theme.backgroundBottom, (float) y0 / height),
                lerp(theme.backgroundTop, theme.backgroundBottom, (float) y1 / height));
        } finally {
            end();
        }
    }

    /** Background of a scrolling list. */
    public static void drawListPanel(int left, int top, int right, int bottom) {
        begin();
        try {
            if (theme().isBeveled()) ThemeShapes.inset(left, top, right - left, bottom - top, theme());
            else GLDraw.rect(left, top, right - left, bottom - top, theme().barBackground);
        } finally {
            end();
        }
    }

    /**
     * Multiplies the vanilla inventory texture with the theme color: the light gray background becomes the theme
     * panel color, slots and borders keep their relative shading.
     */
    public static void tintInventory(int x, int y, int width, int height) {
        int target = theme().panelFill();
        int r = Math.min(255, (target >> 16 & 0xFF) * 255 / VANILLA_INVENTORY_GRAY);
        int g = Math.min(255, (target >> 8 & 0xFF) * 255 / VANILLA_INVENTORY_GRAY);
        int b = Math.min(255, (target & 0xFF) * 255 / VANILLA_INVENTORY_GRAY);
        begin();
        try {
            glBlendFunc(GL_DST_COLOR, GL_ZERO);
            GLDraw.rect(x, y, width, height, 0xFF000000 | r << 16 | g << 8 | b);
        } finally {
            end();
        }
    }

    private static void begin() {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LIGHTING_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
    }

    private static void end() {
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    /** Marks the time the current screen is drawn. Forge posts these events around GuiScreen.drawScreen. */
    public static class ScreenDrawTracker {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
            inScreenDraw = true;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
            inScreenDraw = false;
        }
    }
}
