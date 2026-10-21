package gtnhtweaks.modules.menus;

import static org.lwjgl.opengl.GL11.*;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;

/** Decides when a screen is restyled and draws the themed parts. Called from the menus mixins. */
public final class MenuStyle {

    /** Vanilla colors of list selection boxes. */
    private static final int VANILLA_SELECTION_BORDER = 0x808080;
    private static final int VANILLA_SELECTION_FILL = 0x000000;

    private static final Map<Class<?>, Boolean> MATCHES = new HashMap<>();

    /** True while a tooltip is drawn: tooltips keep their own dark background, so their text is not recolored. */
    private static boolean inTooltip;

    private MenuStyle() {}

    public static GTNHTheme theme() {
        return MenusModule.style;
    }

    /** True if the current screen is restyled. Only outside of a world: in game the screens stay vanilla. */
    public static boolean isActive() {
        if (!MenusModule.INSTANCE.isEnabled()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld != null) return false;
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return false;
        Boolean match = MATCHES.get(screen.getClass());
        if (match == null) {
            match = matches(screen.getClass());
            MATCHES.put(screen.getClass(), match);
        }
        return match;
    }

    private static boolean matches(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (MenusModule.screens.contains(c.getName())) return true;
        }
        return false;
    }

    public static void setInTooltip(boolean value) {
        inTooltip = value;
    }

    // ---- text

    /** Maps the white and gray text colors of vanilla screens to the theme. Colored text is kept. */
    public static int textColor(int color) {
        if (!MenusModule.recolorText || inTooltip || !isActive()) return color;
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        int alpha = color & 0xFF000000;
        GTNHTheme theme = theme();
        int rgb = color & 0xFFFFFF;
        if (rgb == (theme.text & 0xFFFFFF) || rgb == (theme.textDim & 0xFFFFFF)
            || rgb == (theme.textDisabled & 0xFFFFFF)) return color;
        if (min >= 0xC8 && max - min <= 0x30) return alpha | (theme.text & 0xFFFFFF);
        if (max - min <= 0x10 && max >= 0x60) return alpha | (theme.textDim & 0xFFFFFF);
        return color;
    }

    /** Text shadows look dirty on the light style. */
    public static boolean suppressShadow() {
        return MenusModule.recolorText && !inTooltip && theme() == GTNHTheme.LIGHT && isActive();
    }

    // ---- lists

    public static int selectionColor(int color) {
        if (!isActive()) return color;
        if (color == VANILLA_SELECTION_BORDER) return theme().barFill & 0xFFFFFF;
        if (color == VANILLA_SELECTION_FILL) return theme().buttonHover & 0xFFFFFF;
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
            GLDraw.rect(left, top, right - left, bottom - top, theme().barBackground);
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
}
