package gtnhtweaks.modules.backups.client;

import static org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLAT;
import static org.lwjgl.opengl.GL11.GL_LIGHTING;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SMOOTH;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glShadeModel;
import static org.lwjgl.opengl.GL11.glTranslatef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.common.render.ThemeShapes;
import gtnhtweaks.modules.backups.BackupsModule;

/**
 * Toast in the top right corner, like the advancement toasts of newer Minecraft versions: slides in when a backup
 * starts, shows the elapsed time and a progress bar, turns green or red when the backup ends and slides out.
 */
class BackupToast {

    private static final int HEIGHT = 40;
    private static final int MIN_WIDTH = 180;
    private static final int MAX_WIDTH = 300;
    private static final int MARGIN = 4;
    private static final int TEXT_X = 38;

    private static final int GREEN = 0xFF4CC264;
    private static final int GREEN_LIGHT = 0xFF8AE39B;
    private static final int RED = 0xFFE5534B;
    private static final int RED_LIGHT = 0xFFF2928C;

    private float shownProgress;
    private long lastFrame;
    private long widthFor = -1;
    private int width;

    void render(long now) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI && mc.currentScreen == null) return;
        GTNHTheme theme = BackupsModule.style;
        FontRenderer font = mc.fontRenderer;
        BackupClient.Phase phase = BackupClient.phase;
        boolean running = phase == BackupClient.Phase.RUNNING;

        float target = BackupClient.progress(now);
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        if (widthFor != BackupClient.shownAt) {
            // New toast: the bar grows from zero and the width is measured again
            widthFor = BackupClient.shownAt;
            shownProgress = 0;
            width = MIN_WIDTH;
        }
        if (target >= 0) shownProgress += (target - shownProgress) * Math.min(1, dt * 6);

        String title;
        String details;
        String percent = "";
        int accent;
        int accentLight;
        int titleColor;
        if (running) {
            title = BackupsModule.textTitle;
            long elapsed = now - BackupClient.startedAt;
            details = BackupsModule.textElapsed + " " + formatTime(elapsed);
            long remaining = BackupClient.remaining(now);
            if (remaining >= 0) details += "  ~" + formatTime(remaining) + " " + BackupsModule.textLeft;
            if (target >= 0) percent = (int) (shownProgress * 100) + "%";
            accent = theme.barFill;
            accentLight = theme.logoLight;
            titleColor = theme == GTNHTheme.LIGHT ? theme.logo : theme.logoLight;
        } else {
            boolean ok = phase == BackupClient.Phase.FINISHED;
            title = ok ? BackupsModule.textTitleDone : BackupsModule.textTitleFailed;
            details = BackupsModule.textTook + " " + formatTime(BackupClient.durationMs);
            if (ok) percent = BackupClient.size.isEmpty() ? "100%" : BackupClient.size;
            accent = ok ? GREEN : RED;
            accentLight = ok ? GREEN_LIGHT : RED_LIGHT;
            titleColor = accent;
        }

        int needed = Math.max(
            TEXT_X + font.getStringWidth(title) + 10,
            TEXT_X + font.getStringWidth(details) + font.getStringWidth(percent) + 18);
        width = Math.min(MAX_WIDTH, Math.max(width, needed));
        int w = width;

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        float slideIn = ease((now - BackupClient.shownAt) / (float) BackupClient.SLIDE_MS);
        float slideOut = now > BackupClient.hideAt ? ease((now - BackupClient.hideAt) / (float) BackupClient.SLIDE_MS)
            : 0;
        float offset = (1 - slideIn + slideOut) * (w + MARGIN * 2);
        float x = resolution.getScaledWidth() - w - MARGIN + offset;
        float y = MARGIN;

        mc.entityRenderer.setupOverlayRendering();
        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);

        // Panel with a soft shadow: beveled like a Shadow UI panel, or with a 1 px rounded border
        roundedRect(x - 1, y + 1, w + 2, HEIGHT + 2, 0x30000000);
        roundedRect(x, y + 2, w, HEIGHT, 0x40000000);
        int edge = 1;
        if (theme.isBeveled()) {
            ThemeShapes.panel(x, y, w, HEIGHT, theme);
            edge = theme.panel.edge + 1;
        } else {
            roundedRect(x, y, w, HEIGHT, theme.barBorder);
            GLDraw.verticalGradient(x + 1, y + 1, w - 2, HEIGHT - 2, theme.buttonHover, theme.buttonBackground);
        }
        GLDraw.verticalGradient(x + edge, y + edge, 2, HEIGHT - edge * 2, accentLight, accent);

        // Icon
        float boxX = x + 7;
        float boxY = y + 7;
        if (theme.isBeveled()) ThemeShapes.inset(boxX, boxY, 26, 26, theme);
        else {
            roundedRect(boxX, boxY, 26, 26, theme.barBorder);
            GLDraw.rect(boxX + 1, boxY + 1, 24, 24, theme.barBackground);
        }
        glPushMatrix();
        glTranslatef(boxX + 1, boxY + 1, 0);
        glScalef(1.5F, 1.5F, 1);
        drawFloppy(phase, accent, accentLight, now);
        glPopMatrix();

        // Progress bar
        float barX = x + TEXT_X;
        float barY = y + 30;
        float barW = w - TEXT_X - 9;
        float barH = 4;
        ThemeShapes.inset(barX - 1, barY - 1, barW + 2, barH + 2, theme);
        if (target >= 0 || !running) {
            float fill = barW * Math.max(0, Math.min(1, running ? shownProgress : 1));
            GLDraw.verticalGradient(barX, barY, fill, barH, accentLight, accent);
            if (running) shimmer(barX, barY, fill, barH, now, 1600, 24);
        } else {
            // Nothing known about the progress: a segment runs along the bar
            float segment = barW * 0.3F;
            float t = (now % 1400) / 1400F;
            float from = barX - segment + (barW + segment) * t;
            float left = Math.max(barX, from);
            float right = Math.min(barX + barW, from + segment);
            if (right > left) GLDraw.verticalGradient(left, barY, right - left, barH, accentLight, accent);
        }

        glShadeModel(GL_FLAT);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);

        // Texts
        boolean shadow = theme.textShadow;
        int textX = (int) (x + TEXT_X);
        String shownTitle = font.trimStringToWidth(title, w - TEXT_X - 8);
        font.drawString(shownTitle, textX, (int) y + 7, titleColor, shadow);
        font.drawString(details, textX, (int) y + 18, theme.textDim, shadow);
        if (!percent.isEmpty()) {
            int percentX = (int) (x + w - 9) - font.getStringWidth(percent);
            font.drawString(percent, percentX, (int) y + 18, theme.text, shadow);
        }

        glColor4f(1, 1, 1, 1);
        glEnable(GL_DEPTH_TEST);
    }

    /** A floppy disk in a 16x16 box. While the backup runs, lines are written onto its label. */
    private static void drawFloppy(BackupClient.Phase phase, int body, int bodyLight, long now) {
        GLDraw.verticalGradient(1, 1, 12, 14, bodyLight, body);
        GLDraw.verticalGradient(13, 3, 2, 12, bodyLight, body);
        GLDraw.rect(4, 1, 7, 5, 0xFFD5D8E0);
        GLDraw.rect(8, 2, 2, 3, 0xFF3A3D48);
        GLDraw.rect(3, 8, 10, 6, 0xFFF4F4F6);

        float first = 8;
        float second = 6;
        if (phase == BackupClient.Phase.RUNNING) {
            float t = (now % 1500) / 1500F * 16;
            first = Math.min(8, t);
            second = Math.max(0, Math.min(6, t - 9));
        }
        if (first > 0) GLDraw.rect(4, 10, first, 1, 0xFF8E94A3);
        if (second > 0) GLDraw.rect(4, 12, second, 1, 0xFF8E94A3);
        if (phase == BackupClient.Phase.FAILED) {
            GLDraw.rect(6, 9, 4, 4, RED);
        }
    }

    /** A light band running along the filled part of the bar. */
    private static void shimmer(float x, float y, float w, float h, long now, long period, float band) {
        if (w <= 2) return;
        float t = (now % period) / (float) period;
        float from = x - band + (w + band) * t;
        float mid = from + band / 2;
        drawClipped(from, mid, x, x + w, y, h, 0x00FFFFFF, 0x55FFFFFF);
        drawClipped(mid, from + band, x, x + w, y, h, 0x55FFFFFF, 0x00FFFFFF);
    }

    private static void drawClipped(float from, float to, float min, float max, float y, float h, int left, int right) {
        float a = Math.max(from, min);
        float b = Math.min(to, max);
        if (b <= a) return;
        GLDraw.horizontalGradient(
            a,
            y,
            b - a,
            h,
            lerp(left, right, (a - from) / (to - from)),
            lerp(left, right, (b - from) / (to - from)));
    }

    private static int lerp(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    private static void roundedRect(float x, float y, float w, float h, int argb) {
        GLDraw.rect(x + 1, y, w - 2, h, argb);
        GLDraw.rect(x, y + 1, 1, h - 2, argb);
        GLDraw.rect(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** Ease out cubic, clamped to 0..1. */
    private static float ease(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float inverse = 1 - t;
        return 1 - inverse * inverse * inverse;
    }

    static String formatTime(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        long hours = seconds / 3600;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
