package gtnhtweaks.modules.mainmenu;

import static gtnhtweaks.common.render.GLDraw.rect;
import static org.lwjgl.opengl.GL11.*;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.google.common.collect.Lists;

import cpw.mods.fml.common.FMLCommonHandler;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHLogo;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.mixins.early.mainmenu.AccessorGuiButton_MainMenu;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.loadingscreen.LoadingTimer;

/** Draws the main menu in place of the vanilla panorama, logo and buttons. */
public final class MainMenuRenderer {

    private static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

    /** Set after a rendering error, so the vanilla menu is drawn instead from then on. */
    private static boolean broken;

    private MainMenuRenderer() {}

    /** @return true if the menu was drawn and the vanilla drawing must be skipped */
    public static boolean draw(GuiScreen gui, FontRenderer font, List<?> buttons, int mouseX, int mouseY,
        String splashText) {
        if (broken) return false;
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_LIGHTING_BIT);
        try {
            drawMenu(gui, font, buttons, mouseX, mouseY, splashText, MainMenuModule.style);
            return true;
        } catch (Throwable t) {
            broken = true;
            GTNHTweaks.LOG.error("GTNH main menu failed, falling back to the vanilla main menu", t);
            return false;
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }

    private static void drawMenu(GuiScreen gui, FontRenderer font, List<?> buttons, int mouseX, int mouseY,
        String splashText, GTNHTheme theme) {
        int width = gui.width;
        int height = gui.height;
        boolean shadow = theme == GTNHTheme.DARK;

        beginShapes();
        GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);

        // logo with the subtitle and the splash text, centered in the space above the buttons
        int buttonsTop = MainMenuLayout.buttonsTop >= 0 ? MainMenuLayout.buttonsTop : height / 4 + 48;
        boolean hasSubtitle = !MainMenuModule.subtitle.isEmpty();
        boolean hasSplash = MainMenuModule.showSplashText && splashText != null && !splashText.isEmpty();
        int lineHeight = font.FONT_HEIGHT + 3;
        int textBlock = (hasSubtitle ? lineHeight : 0) + (hasSplash ? lineHeight : 0);
        int available = buttonsTop - 16 - textBlock;
        int cell = Math.max(2, Math.min(8, Math.min(available / 7, (width - 20) / 23)));
        int logoHeight = GTNHLogo.height(cell);
        int blockHeight = logoHeight + 6 + textBlock;
        int y = Math.max(4, (buttonsTop - 8 - blockHeight) / 2);
        GTNHLogo.draw((width - GTNHLogo.width(cell)) / 2, y, cell, theme);
        y += logoHeight + 6;

        // button frames
        for (Object element : buttons) {
            GuiButton button = (GuiButton) element;
            if (isThemed(button) && button.visible) drawButtonFrame(button, mouseX, mouseY, theme);
        }

        endShapes();

        if (hasSubtitle) {
            drawCentered(font, MainMenuModule.subtitle, width / 2, y, theme.textDim, shadow);
            y += lineHeight;
        }
        if (hasSplash) {
            float pulse = 1F + 0.04F * (float) Math.sin(Minecraft.getSystemTime() % 1000L / 1000F * Math.PI * 2);
            glPushMatrix();
            glTranslatef(width / 2F, y + font.FONT_HEIGHT / 2F, 0);
            glScalef(pulse, pulse, 1);
            font.drawString(
                splashText,
                -font.getStringWidth(splashText) / 2,
                -font.FONT_HEIGHT / 2,
                theme.barFill,
                shadow);
            glPopMatrix();
        }

        // button labels, and buttons that draw themselves (custom buttons of other mods)
        Minecraft mc = Minecraft.getMinecraft();
        for (Object element : buttons) {
            GuiButton button = (GuiButton) element;
            if (!button.visible) continue;
            if (isThemed(button)) drawButtonLabel(button, font, mouseX, mouseY, theme, shadow);
            else button.drawButton(mc, mouseX, mouseY);
        }

        // footer
        if (MainMenuModule.showBranding) {
            List<String> brandings = Lists.reverse(
                FMLCommonHandler.instance()
                    .getBrandings(true));
            for (int i = 0; i < brandings.size(); i++) {
                String branding = brandings.get(i);
                if (branding == null || branding.isEmpty()) continue;
                font.drawString(branding, 2, height - (10 + i * (font.FONT_HEIGHT + 1)), theme.textDim, shadow);
            }
        }
        font.drawString(COPYRIGHT, width - font.getStringWidth(COPYRIGHT) - 2, height - 10, theme.textDim, shadow);
        if (MainMenuModule.showLoadTime && LoadingScreenModule.INSTANCE.isEnabled() && LoadingTimer.isFinished()) {
            String loadTime = LoadingScreenModule.textLoadedIn + " "
                + LoadingTimer.format(LoadingTimer.getElapsedMs());
            long previous = LoadingTimer.getPreviousMs();
            if (previous > 0) {
                loadTime += " (" + LoadingScreenModule.textPreviousLaunch + ": " + LoadingTimer.format(previous) + ")";
            }
            font.drawString(
                loadTime,
                width - font.getStringWidth(loadTime) - 2,
                height - 20,
                theme.textDim,
                shadow);
        }
    }

    /** Vanilla buttons and plain buttons of other mods are drawn in the GTNH style; custom button classes are not. */
    private static boolean isThemed(GuiButton button) {
        return MainMenuLayout.isKnown(button.id) || button.getClass() == GuiButton.class;
    }

    private static int widthOf(GuiButton button) {
        return ((AccessorGuiButton_MainMenu) button).gtnhtweaks$getWidth();
    }

    private static int heightOf(GuiButton button) {
        return ((AccessorGuiButton_MainMenu) button).gtnhtweaks$getHeight();
    }

    private static boolean isHovered(GuiButton button, int mouseX, int mouseY) {
        int width = widthOf(button);
        int height = heightOf(button);
        return button.enabled && mouseX >= button.xPosition
            && mouseY >= button.yPosition
            && mouseX < button.xPosition + width
            && mouseY < button.yPosition + height;
    }

    private static void drawButtonFrame(GuiButton button, int mouseX, int mouseY, GTNHTheme theme) {
        int x = button.xPosition;
        int y = button.yPosition;
        int width = widthOf(button);
        int height = heightOf(button);
        boolean hovered = isHovered(button, mouseX, mouseY);
        rect(x, y, width, height, hovered ? theme.barFill : theme.barBorder);
        rect(x + 1, y + 1, width - 2, height - 2, hovered ? theme.buttonHover : theme.buttonBackground);
        if (hovered) rect(x + 1, y + 1, 2, height - 2, theme.barFill);
    }

    private static void drawButtonLabel(GuiButton button, FontRenderer font, int mouseX, int mouseY,
        GTNHTheme theme, boolean shadow) {
        String label = button.displayString;
        if (button.id == MainMenuLayout.LANGUAGE && (label == null || label.isEmpty())) {
            label = I18n.format("options.language");
        }
        if (label == null || label.isEmpty()) return;
        int color = !button.enabled ? theme.textDisabled
            : isHovered(button, mouseX, mouseY) ? theme.logo : theme.text;
        int width = widthOf(button);
        int height = heightOf(button);
        drawCentered(font, label, button.xPosition + width / 2, button.yPosition + (height - 8) / 2, color, shadow);
    }

    private static void drawCentered(FontRenderer font, String text, int centerX, int y, int color, boolean shadow) {
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color, shadow);
    }

    private static void beginShapes() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
    }

    private static void endShapes() {
        glShadeModel(GL_FLAT);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }
}
