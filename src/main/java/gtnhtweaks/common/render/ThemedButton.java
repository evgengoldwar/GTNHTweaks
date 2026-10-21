package gtnhtweaks.common.render;

import static gtnhtweaks.common.render.GLDraw.rect;
import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

import gtnhtweaks.mixins.early.common.AccessorGuiButton;

/** Flat GTNH style button: themed frame, accent strip and label color on hover. */
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
            rect(x, y, width, height, hovered ? theme.barFill : theme.barBorder);
            rect(x + 1, y + 1, width - 2, height - 2, hovered ? theme.buttonHover : theme.buttonBackground);
            if (hovered) rect(x + 1, y + 1, 2, height - 2, theme.barFill);
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
        else color = isHovered(button, mouseX, mouseY) ? theme.logo : theme.text;
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
                theme == GTNHTheme.DARK);
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }

    /** Slider knob part, in place of the vanilla knob texture. */
    public static void drawKnob(int x, int y, int width, int height, GTNHTheme theme) {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            rect(x, y, width, height, theme.barFill);
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }
}
