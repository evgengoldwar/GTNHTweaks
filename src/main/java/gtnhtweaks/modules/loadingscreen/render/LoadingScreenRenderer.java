package gtnhtweaks.modules.loadingscreen.render;

import static gtnhtweaks.common.render.GLDraw.rect;
import static org.lwjgl.opengl.GL11.*;

import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.Display;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHLogo;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.common.render.ThemeShapes;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.loadingscreen.LoadingTimer;

/**
 * Draws the whole GTNH loading screen into the current GL context, over whatever was drawn before. Leaves the GL state
 * as it found it.
 * <p>
 * Behind the text a wall of blocks is built while the game loads. Once it has loaded, {@link #renderOutro} finishes the
 * wall, fades the text out and lets the wall crumble down over the main menu.
 */
public final class LoadingScreenRenderer {

    /** Launch time the wall is built for when neither the previous launch nor the progress is known. */
    private static final float UNKNOWN_LOAD_MS = 4 * 60_000;
    private static final int FILL_MS = 450;
    private static final int FADE_MS = 500;

    private static List<ProgressLine> lastLines = Collections.emptyList();
    private static float wallProgress;
    private static long fadeAt = -1, collapseAt = -1;

    private LoadingScreenRenderer() {}

    public static synchronized void render(List<ProgressLine> lines) {
        lastLines = lines;
        long now = System.currentTimeMillis();
        if (LoadingScreenModule.wall) {
            WallTextures.start();
            wallProgress = Math.max(wallProgress, Math.min(0.97F, loadedShare(lines)));
        }
        draw(Display.getWidth(), Display.getHeight(), (width, height) -> {
            if (LoadingScreenModule.wall)
                BlockWall.build(width, height, LoadingScreenModule.wallRows, wallProgress, now);
            drawScreen(width, height, lastLines, LoadingScreenModule.style, now);
        });
    }

    /** How much of the loading is done, from 0 to 1, for the wall. */
    private static float loadedShare(List<ProgressLine> lines) {
        long elapsed = LoadingTimer.getElapsedMs();
        long previous = LoadingTimer.getPreviousMs();
        if (previous > 0) return (float) elapsed / previous;
        // The first line is the overall progress of Forge or BetterLoadingScreen
        if (!lines.isEmpty() && !Float.isNaN(lines.get(0).progress)) return lines.get(0).progress;
        return 1 - (float) Math.exp(-2 * elapsed / UNKNOWN_LOAD_MS);
    }

    /**
     * Draws a frame of the end of the loading screen over the main menu: the rest of the wall is laid, the text fades
     * out and the wall crumbles. Must be called on the main thread once the game has started, every frame until it
     * returns false.
     */
    public static synchronized boolean renderOutro(int width, int height) {
        long now = System.currentTimeMillis();
        if (collapseAt < 0) {
            if (!LoadingScreenModule.wall || BlockWall.isEmpty()) return false;
            fadeAt = BlockWall.fill(width, height, LoadingScreenModule.wallRows, now, FILL_MS) + 100;
            collapseAt = fadeAt + FADE_MS + 150;
        }
        if (now >= collapseAt) BlockWall.collapse(now);
        draw(width, height, (w, h) -> {
            if (now < collapseAt) {
                drawScreen(w, h, lastLines, LoadingScreenModule.style, now);
                // the finished wall covers the whole screen, drawing it over the text fades the text out
                BlockWall.draw(now, Math.max(0F, Math.min(1F, (now - fadeAt) / (float) FADE_MS)));
            } else {
                BlockWall.draw(now, 1F);
            }
        });
        if (now < collapseAt || BlockWall.isStanding(now)) return true;
        BlockWall.reset();
        dispose();
        return false;
    }

    /** Whether the wall has started to crumble. */
    public static synchronized boolean isCollapsing() {
        return collapseAt >= 0 && System.currentTimeMillis() >= collapseAt;
    }

    private interface Painter {

        void paint(int width, int height);
    }

    private static void draw(int width, int height, Painter painter) {
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
            painter.paint(width, height);
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

    /** Frees the textures. The pixels of the wall blocks are kept, the outro uploads them again. */
    public static void dispose() {
        SplashFont.dispose();
        WallTextures.dispose();
    }

    private static void drawScreen(int width, int height, List<ProgressLine> lines, GTNHTheme theme, long now) {
        float ui = Math.max(1F, Math.min(width / 640F, height / 480F));
        int textScale = Math.max(1, Math.round(1.5F * ui));
        int titleScale = Math.max(1, Math.round(2F * ui));
        int timerScale = Math.max(2, Math.round(3F * ui));
        int gap = Math.max(2, Math.round(4 * ui));

        GLDraw.verticalGradient(0, 0, width, height, theme.backgroundTop, theme.backgroundBottom);
        if (LoadingScreenModule.wall) {
            BlockWall.draw(now, 1F);
            // the background color over the wall keeps the text readable
            int veil = Math.round(LoadingScreenModule.wallVeil * 255F) << 24;
            GLDraw.verticalGradient(
                0,
                0,
                width,
                height,
                theme.backgroundTop & 0xFFFFFF | veil,
                theme.backgroundBottom & 0xFFFFFF | veil);
        }

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
            if (!counter.isEmpty())
                SplashFont.draw(counter, barX + barWidth - counterWidth, y, textScale, theme.textDim);
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
        if (LoadingScreenModule.showMemory) drawMemory(textScale, gap, ui, theme);
    }

    /** Top left panel: caption and percentage, a usage bar and the used / max amount. */
    private static void drawMemory(int textScale, int gap, float ui, GTNHTheme theme) {
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024 * 1024;
        long used = (runtime.totalMemory() - runtime.freeMemory()) / mb;
        long max = runtime.maxMemory() / mb;
        float usage = max > 0 ? Math.min(1F, (float) used / max) : 0F;

        String caption = LoadingScreenModule.textMemory;
        String percent = Math.round(usage * 100) + "%";
        String amount = groupDigits(used) + " / " + groupDigits(max) + " MB";

        int padding = gap * 2;
        int lineHeight = SplashFont.HEIGHT * textScale;
        int barHeight = Math.max(3, Math.round(6 * ui));
        float contentWidth = Math.max(
            SplashFont.width(amount) * textScale,
            (SplashFont.width(caption) + SplashFont.width(percent)) * textScale + gap * 4);
        contentWidth = Math.max(contentWidth, Math.round(140 * ui));
        float panelWidth = contentWidth + padding * 2;
        float panelHeight = padding * 2 + lineHeight * 2 + barHeight + gap * 4;
        float x = gap * 3;
        float y = gap * 3;

        ThemeShapes.panel(x, y, panelWidth, panelHeight, theme);
        int edge = theme.isBeveled() ? theme.panel.edge + 1 : 1;
        rect(x + edge, y + edge, Math.max(1, gap / 2), panelHeight - edge * 2, memoryColor(usage, theme));

        float left = x + padding;
        float top = y + padding;
        SplashFont.draw(caption, left, top, textScale, theme.textDim);
        SplashFont.draw(
            percent,
            left + contentWidth - SplashFont.width(percent) * textScale,
            top,
            textScale,
            memoryColor(usage, theme));
        top += lineHeight + gap * 2;

        if (theme.isBeveled()) {
            ThemeShapes.bar(left, top, contentWidth, barHeight, usage, memoryColor(usage, theme), theme);
        } else {
            rect(left, top, contentWidth, barHeight, theme.barBackground);
            rect(left, top, contentWidth * usage, barHeight, memoryColor(usage, theme));
        }
        top += barHeight + gap * 2;

        SplashFont.draw(amount, left, top, textScale, theme.text);
    }

    /** Theme accent while there is plenty of memory, shifting to red as the heap fills up. */
    private static int memoryColor(float usage, GTNHTheme theme) {
        float t = Math.max(0F, Math.min(1F, (usage - 0.7F) / 0.2F));
        return lerpColor(theme.barFill, 0xFFE0453A, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    /** 10000 -> "10 000". */
    private static String groupDigits(long value) {
        StringBuilder digits = new StringBuilder(Long.toString(value));
        for (int i = digits.length() - 3; i > 0; i -= 3) digits.insert(i, ' ');
        return digits.toString();
    }

    private static void drawBar(int x, int y, int width, int height, float progress, GTNHTheme theme) {
        ThemeShapes.bar(x, y, width, height, progress, theme.barFill, theme);
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
