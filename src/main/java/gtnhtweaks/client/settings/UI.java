package gtnhtweaks.client.settings;

import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.common.render.ThemeShapes;
import gtnhtweaks.modules.menus.MenusModule;

/** Drawing helpers shared by the settings screens. */
final class UI {

    static final int ON = 0xFF4CC264;

    private UI() {}

    static GTNHTheme theme() {
        return MenusModule.style;
    }

    static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    /** Untextured drawing until {@link #endShapes}. */
    static void beginShapes() {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LIGHTING_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
    }

    static void endShapes() {
        glShadeModel(GL_FLAT);
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
    }

    static void rect(float x, float y, float w, float h, int color) {
        beginShapes();
        GLDraw.rect(x, y, w, h, color);
        endShapes();
    }

    static void panel(float x, float y, float w, float h) {
        beginShapes();
        ThemeShapes.panel(x, y, w, h, theme());
        endShapes();
    }

    static void inset(float x, float y, float w, float h) {
        beginShapes();
        ThemeShapes.inset(x, y, w, h, theme());
        endShapes();
    }

    /** A button face: beveled in SHADOW, flat with an accent border on hover in the other themes. */
    static void button(int x, int y, int w, int h, boolean hovered, boolean enabled) {
        GTNHTheme theme = theme();
        beginShapes();
        if (theme.isBeveled()) {
            GLDraw.bevel(x, y, w, h, !enabled ? theme.buttonDisabled : hovered ? theme.buttonHovered : theme.button);
        } else {
            GLDraw.rect(x, y, w, h, hovered && enabled ? theme.barFill : theme.barBorder);
            GLDraw.rect(x + 1, y + 1, w - 2, h - 2, hovered && enabled ? theme.buttonHover : theme.buttonBackground);
        }
        endShapes();
    }

    /** A button with a centered label. */
    static void button(int x, int y, int w, int h, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        button(x, y, w, h, hovered, enabled);
        GTNHTheme theme = theme();
        int color = !enabled ? theme.textDisabled : hovered ? theme.hoverText : theme.text;
        text(label, x + (w - font().getStringWidth(label)) / 2, y + (h - 8) / 2, color);
    }

    /** An on/off switch; {@code position} runs from 0 (off) to 1 (on) while it moves. */
    static void toggle(int x, int y, float position, boolean hovered) {
        GTNHTheme theme = theme();
        int w = 22, h = 10;
        beginShapes();
        if (theme.isBeveled()) GLDraw.bevel(x, y, w, h, theme.inset);
        else {
            GLDraw.rect(x, y, w, h, theme.barBorder);
            GLDraw.rect(x + 1, y + 1, w - 2, h - 2, theme.barBackground);
        }
        int track = lerp(theme.isBeveled() ? theme.inset.fill : theme.barBackground, ON, position);
        GLDraw.rect(x + 1, y + 1, w - 2, h - 2, track);
        float knob = x + 1 + (w - 2 - 8) * position;
        if (theme.isBeveled()) GLDraw.bevel(knob, y + 1, 8, h - 2, hovered ? theme.buttonHovered : theme.button);
        else GLDraw.rect(knob, y + 1, 8, h - 2, hovered ? theme.text : theme.textDim);
        endShapes();
    }

    static void text(String text, int x, int y, int color) {
        font().drawString(text, x, y, color, theme().textShadow);
    }

    static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    /** Limits drawing to the rectangle, in GUI coordinates. */
    static void scissor(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        glEnable(GL_SCISSOR_TEST);
        glScissor(x * scale, mc.displayHeight - (y + h) * scale, Math.max(0, w * scale), Math.max(0, h * scale));
    }

    static void endScissor() {
        glDisable(GL_SCISSOR_TEST);
    }

    /** Moves {@code value} toward {@code target}, about 95% of the way in {@code timeMs}, whatever the frame rate. */
    static float approach(float value, float target, float dtMs, float timeMs) {
        float t = 1F - (float) Math.exp(-3F * dtMs / timeMs);
        float next = value + (target - value) * t;
        return Math.abs(target - next) < 0.01F ? target : next;
    }

    static int lerp(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    /** "showSplashText" -> "Show splash text". */
    static String humanize(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) out.append(' ');
            out.append(out.length() == 0 ? Character.toUpperCase(c) : i > 0 ? Character.toLowerCase(c) : c);
        }
        return out.toString();
    }
}
