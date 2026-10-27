package gtnhtweaks.modules.mainmenu;

import static org.lwjgl.opengl.GL11.*;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.Loader;
import gtnhtweaks.GTNHTweaks;
import gtnhtweaks.common.ResourcePackOverrides;
import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHLogo;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.common.render.ThemeShapes;
import gtnhtweaks.common.render.ThemedButton;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.loadingscreen.LoadingTimer;

/** Draws the main menu in place of the vanilla panorama, logo and buttons. */
public final class MainMenuRenderer {

    /** Set after a rendering error, so the vanilla menu is drawn instead from then on. */
    private static boolean broken;

    private MainMenuRenderer() {}

    private static final ResourceLocation[] PACK_TEXTURES = { //
        new ResourceLocation("textures/gui/title/minecraft.png"), //
        new ResourceLocation("textures/gui/widgets.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_0.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_1.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_2.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_3.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_4.png"), //
        new ResourceLocation("textures/gui/title/background/panorama_5.png"), //
    };

    /** True if a resource pack replaces the vanilla main menu textures and the vanilla menu must be shown. */
    static boolean replacedByPack() {
        return MainMenuModule.respectResourcePacks && ResourcePackOverrides.isAnyOverridden(PACK_TEXTURES);
    }

    /** Why the vanilla main menu is shown instead of the GTNH one, null while the GTNH menu is shown. */
    static String inactiveReason() {
        if (broken) return "the GTNH main menu failed to draw, see the error above";
        if (!MainMenuModule.respectResourcePacks) return null;
        for (ResourceLocation texture : PACK_TEXTURES) {
            if (ResourcePackOverrides.isOverridden(texture))
                return "a selected resource pack replaces " + texture + " (yieldToResourcePacks)";
        }
        return null;
    }

    /** @return true if the menu was drawn and the vanilla drawing must be skipped */
    public static boolean draw(GuiScreen gui, FontRenderer font, List<?> buttons, int mouseX, int mouseY,
        String splashText) {
        if (broken || replacedByPack()) return false;
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
        boolean shadow = theme.textShadow;

        beginShapes();
        GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
        if (MainMenuModule.circuit) CircuitBackground.draw(width, height, mouseX, mouseY, theme);

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

        if (theme.isBeveled()) drawButtonPanel(buttons, theme);
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

        // buttons; custom button classes of other mods draw themselves
        Minecraft mc = Minecraft.getMinecraft();
        for (Object element : buttons) {
            GuiButton button = (GuiButton) element;
            if (!button.visible) continue;
            if (isThemed(button)) ThemedButton.draw(button, label(button), font, mouseX, mouseY, theme);
            else button.drawButton(mc, mouseX, mouseY);
        }

        // footer: mod count and the settings button on the left, load time on the right
        if (MainMenuModule.showModCount) {
            String caption = MainMenuModule.textMods + ": ";
            font.drawString(caption, 4, height - 12, theme.textDim, shadow);
            font.drawString(modCount(), 4 + font.getStringWidth(caption), height - 12, theme.logo, shadow);
        }
        if (MainMenuModule.showLoadTime && LoadingScreenModule.INSTANCE.isEnabled() && LoadingTimer.isFinished()) {
            drawLoadTime(font, width, height, theme, shadow);
        }
    }

    /** Beveled panel behind the column of menu buttons, like a Shadow UI window. */
    private static void drawButtonPanel(List<?> buttons, GTNHTheme theme) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Object element : buttons) {
            GuiButton button = (GuiButton) element;
            if (!button.visible || button.id == MainMenuLayout.SETTINGS || !MainMenuLayout.isKnown(button.id)) continue;
            left = Math.min(left, button.xPosition);
            top = Math.min(top, button.yPosition);
            right = Math.max(right, button.xPosition + ThemedButton.width(button));
            bottom = Math.max(bottom, button.yPosition + ThemedButton.height(button));
        }
        if (left > right) return;
        int padding = 6;
        ThemeShapes.panel(left - padding, top - padding, right - left + padding * 2, bottom - top + padding * 2, theme);
    }

    private static String cachedModCount;

    private static String modCount() {
        if (cachedModCount == null) cachedModCount = Integer.toString(
            Loader.instance()
                .getActiveModList()
                .size());
        return cachedModCount;
    }

    /** "Mods: 312", or empty when the mod count is hidden. */
    static String modCountText() {
        return MainMenuModule.showModCount ? MainMenuModule.textMods + ": " + modCount() : "";
    }

    /** "Loaded in 8.1 s": dim caption, time in the accent color, and an accent strip like the hovered buttons. */
    private static void drawLoadTime(FontRenderer font, int width, int height, GTNHTheme theme, boolean shadow) {
        String caption = LoadingScreenModule.textLoadedIn + " ";
        String time = LoadingTimer.formatHuman(LoadingTimer.getElapsedMs());
        int x = width - 4 - font.getStringWidth(caption + time);
        int y = height - 12;

        beginShapes();
        GLDraw.rect(x - 6, y - 2, 2, font.FONT_HEIGHT + 3, theme.barFill);
        endShapes();

        font.drawString(caption, x, y, theme.textDim, shadow);
        font.drawString(time, x + font.getStringWidth(caption), y, theme.logo, shadow);
    }

    /** Vanilla buttons and plain buttons of other mods are drawn in the GTNH style; custom button classes are not. */
    private static boolean isThemed(GuiButton button) {
        return MainMenuLayout.isKnown(button.id) || button.getClass() == GuiButton.class;
    }

    private static String label(GuiButton button) {
        String label = button.displayString;
        if (button.id == MainMenuLayout.LANGUAGE && (label == null || label.isEmpty())) {
            label = I18n.format("options.language");
        }
        return label;
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
