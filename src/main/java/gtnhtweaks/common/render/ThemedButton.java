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
        int x = button.xPosition;
        int y = button.yPosition;
        int width = width(button);
        int height = height(button);
        boolean hovered = isHovered(button, mouseX, mouseY);

        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            rect(x, y, width, height, hovered ? theme.barFill : theme.barBorder);
            rect(x + 1, y + 1, width - 2, height - 2, hovered ? theme.buttonHover : theme.buttonBackground);
            if (hovered) rect(x + 1, y + 1, 2, height - 2, theme.barFill);
            glEnable(GL_TEXTURE_2D);
            glColor4f(1, 1, 1, 1);

            if (label == null || label.isEmpty()) return;
            int color;
            if (button.packedFGColour != 0) color = button.packedFGColour;
            else if (!button.enabled) color = theme.textDisabled;
            else color = hovered ? theme.logo : theme.text;
            font.drawString(
                label,
                x + width / 2 - font.getStringWidth(label) / 2,
                y + (height - 8) / 2,
                color,
                theme == GTNHTheme.DARK);
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }
}
