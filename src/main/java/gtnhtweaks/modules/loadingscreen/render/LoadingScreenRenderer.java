package gtnhtweaks.modules.loadingscreen.render;

import static gtnhtweaks.modules.loadingscreen.render.GLDraw.rect;
import static org.lwjgl.opengl.GL11.*;

import java.util.List;

import org.lwjgl.opengl.Display;

import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.loadingscreen.LoadingScreenTheme;
import gtnhtweaks.modules.loadingscreen.LoadingTimer;

/**
 * Draws the whole GTNH loading screen into the current GL context, over whatever was drawn before. Leaves the GL state
 * as it found it.
 */
public final class LoadingScreenRenderer {

    private LoadingScreenRenderer() {}

    public static synchronized void render(List<ProgressLine> lines) {
        int width = Display.getWidth();
        int height = Display.getHeight();
        if (width <= 0 || height <= 0) return;

        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_TEXTURE);
        glPushMatrix();
        glLoadIdentity();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        try {
            glViewport(0, 0, width, height);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_LIGHTING);
            glDisable(GL_CULL_FACE);
            glDisable(GL_ALPHA_TEST);
            glDisable(GL_FOG);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glShadeModel(GL_SMOOTH);
            glColorMask(true, true, true, true);
            drawScreen(width, height, lines, LoadingScreenModule.style);
        } finally {
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_TEXTURE);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopAttrib();
        }
    }

    public static void dispose() {
        SplashFont.dispose();
    }

    private static void drawScreen(int width, int height, List<ProgressLine> lines, LoadingScreenTheme theme) {
        float ui = Math.max(1F, Math.min(width / 640F, height / 480F));
        int textScale = Math.max(1, Math.round(1.5F * ui));
        int titleScale = Math.max(1, Math.round(2F * ui));
        int timerScale = Math.max(2, Math.round(3F * ui));
        int gap = Math.max(2, Math.round(4 * ui));

        GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);

        // logo
        int cell = Math.max(3, Math.round(9 * ui));
        int logoWidth = GTNHLogo.width(cell);
        int logoHeight = GTNHLogo.height(cell);
        int y = Math.max(gap * 4, Math.round(height * 0.22F) - logoHeight / 2);
        GTNHLogo.draw((width - logoWidth) / 2, y, cell, theme);
        y += logoHeight + gap * 3;

        if (!LoadingScreenModule.subtitle.isEmpty()) {
            drawCentered(LoadingScreenModule.subtitle, width / 2F, y, titleScale, theme.textDim);
            y += SplashFont.HEIGHT * titleScale;
        }

        // progress bars
        int barWidth = Math.min(width - gap * 8, Math.round(420 * ui));
        int barHeight = Math.max(4, Math.round(10 * ui));
        int barX = (width - barWidth) / 2;
        y = Math.max(y + gap * 6, Math.round(height * 0.48F));
        for (ProgressLine line : lines) {
            String counter = line.counter;
            float counterWidth = counter.isEmpty() ? 0 : SplashFont.width(counter) * textScale;
            String text = fit(line.text, (barWidth - counterWidth - gap * 2) / textScale);
            SplashFont.draw(text, barX, y, textScale, theme.text);
            if (!counter.isEmpty()) SplashFont.draw(counter, barX + barWidth - counterWidth, y, textScale, theme.textDim);
            y += SplashFont.HEIGHT * textScale + gap;
            if (!Float.isNaN(line.progress)) {
                drawBar(barX, y, barWidth, barHeight, line.progress, theme);
                y += barHeight;
            }
            y += gap * 2;
        }

        // timer
        if (LoadingScreenModule.showTimer) {
            y += gap;
            long elapsed = LoadingTimer.getElapsedMs();
            drawCentered(LoadingScreenModule.textLoadingTime, width / 2F, y, textScale, theme.textDim);
            y += SplashFont.HEIGHT * textScale + gap;
            drawCentered(LoadingTimer.format(elapsed), width / 2F, y, timerScale, theme.timer);
            y += SplashFont.HEIGHT * timerScale + gap;

            if (LoadingScreenModule.showPreviousLaunch) {
                long previous = LoadingTimer.getPreviousMs();
                if (previous > 0) {
                    String info = LoadingScreenModule.textPreviousLaunch + ": " + LoadingTimer.format(previous);
                    if (elapsed <= previous) info += "  (" + (int) (elapsed * 100 / previous) + "%)";
                    else info += "  (+" + LoadingTimer.format(elapsed - previous) + ")";
                    drawCentered(info, width / 2F, y, textScale, theme.textDim);
                    y += SplashFont.HEIGHT * textScale + gap;
                    int estimateWidth = barWidth / 2;
                    drawBar(
                        (width - estimateWidth) / 2,
                        y,
                        estimateWidth,
                        Math.max(3, barHeight / 2),
                        Math.min(1F, (float) elapsed / previous),
                        theme);
                } else {
                    drawCentered(LoadingScreenModule.textFirstLaunch, width / 2F, y, textScale, theme.textDim);
                }
            }
        }

        // memory
        if (LoadingScreenModule.showMemory) {
            Runtime runtime = Runtime.getRuntime();
            long mb = 1024 * 1024;
            long used = (runtime.totalMemory() - runtime.freeMemory()) / mb;
            long max = runtime.maxMemory() / mb;
            String memory = LoadingScreenModule.textMemory + ": " + used + " / " + max + " MB";
            SplashFont.draw(memory, gap * 2, height - gap * 2 - SplashFont.HEIGHT * textScale, textScale, theme.textDim);
        }
    }

    private static void drawBar(int x, int y, int width, int height, float progress, LoadingScreenTheme theme) {
        rect(x, y, width, height, theme.barBorder);
        rect(x + 1, y + 1, width - 2, height - 2, theme.barBackground);
        rect(x + 1, y + 1, (width - 2) * progress, height - 2, theme.barFill);
    }

    private static void drawCentered(String text, float centerX, float y, int scale, int color) {
        SplashFont.draw(text, centerX - SplashFont.width(text) * scale / 2, y, scale, color);
    }

    /** Cuts the text with "..." so that it is at most {@code maxWidth} unscaled pixels wide. */
    private static String fit(String text, float maxWidth) {
        if (SplashFont.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        float available = maxWidth - SplashFont.width(ellipsis);
        int end = text.length();
        while (end > 0 && SplashFont.width(text.substring(0, end)) > available) end--;
        return text.substring(0, end) + ellipsis;
    }
}
