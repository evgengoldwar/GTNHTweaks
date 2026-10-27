package gtnhtweaks.common.render;

import static gtnhtweaks.common.render.GLDraw.rect;
import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

import gtnhtweaks.mixins.early.common.AccessorGuiButton;

/**
 * GTNH style button. Flat themes: themed frame, accent strip and label color on hover. Beveled themes: the gray
 * beveled buttons of Shadow UI, lighter on hover and darker when disabled.
 */
public final class ThemedButton {

    private ThemedButton() {}

    public static int width(GuiButton button) {
        return ((AccessorGuiButton) button).gtnhtweaks$getWidth();
    }

    public static int height(GuiButton button) {
        return ((AccessorGuiButton) button).gtnhtweaks$getHeight();
    }

    public static boolean isHovered(GuiButton button, int mouseX, int mouseY) {
        return button.enabled && mouseX >= button.xPosition
            && mouseY >= button.yPosition
            && mouseX < button.xPosition + width(button)
            && mouseY < button.yPosition + height(button);
    }

    /** Draws the button with {@code label} as its text, or nothing if the button is hidden. */
    public static void draw(GuiButton button, String label, FontRenderer font, int mouseX, int mouseY,
        GTNHTheme theme) {
        if (!button.visible) return;
        drawFrame(button, mouseX, mouseY, theme);
        drawLabel(button, label, font, mouseX, mouseY, theme);
    }

    public static void drawFrame(GuiButton button, int mouseX, int mouseY, GTNHTheme theme) {
        int x = button.xPosition;
        int y = button.yPosition;
        int width = width(button);
        int height = height(button);
        boolean hovered = isHovered(button, mouseX, mouseY);
        ((AccessorGuiButton) button).gtnhtweaks$setHovered(hovered);

        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            if (theme.isBeveled()) {
                GLDraw.bevel(
                    x,
                    y,
                    width,
                    height,
                    !button.enabled ? theme.buttonDisabled : hovered ? theme.buttonHovered : theme.button);
            } else {
                rect(x, y, width, height, hovered ? theme.barFill : theme.barBorder);
                rect(x + 1, y + 1, width - 2, height - 2, hovered ? theme.buttonHover : theme.buttonBackground);
                if (hovered) rect(x + 1, y + 1, 2, height - 2, theme.barFill);
            }
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }

    public static void drawLabel(GuiButton button, String label, FontRenderer font, int mouseX, int mouseY,
        GTNHTheme theme) {
        if (label == null || label.isEmpty()) return;
        int color;
        if (button.packedFGColour != 0) color = button.packedFGColour;
        else if (!button.enabled) color = theme.textDisabled;
        else color = isHovered(button, mouseX, mouseY) ? theme.hoverText : theme.text;
        int width = width(button);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            font.drawString(
                label,
                button.xPosition + width / 2 - font.getStringWidth(label) / 2,
                button.yPosition + (height(button) - 8) / 2,
                color,
                theme.textShadow);
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }

    /** Last knob part drawn, to join the two halves vanilla sliders draw their knob from. */
    private static int knobX, knobY, knobRight, knobHeight;

    /** Slider knob part, in place of the vanilla knob texture. */
    public static void drawKnob(int x, int y, int width, int height, GTNHTheme theme) {
        // a beveled knob drawn per half would show two boxes: the right half redraws the whole knob over the left one
        if (theme.isBeveled() && x == knobRight && y == knobY && height == knobHeight) {
            width += x - knobX;
            x = knobX;
        }
        knobX = x;
        knobY = y;
        knobRight = x + width;
        knobHeight = height;
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            if (theme.isBeveled()) GLDraw.bevel(x, y, width, height, theme.button);
            else rect(x, y, width, height, theme.barFill);
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }
}
