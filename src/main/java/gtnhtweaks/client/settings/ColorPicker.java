package gtnhtweaks.client.settings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;

/**
 * A color picker shown over a screen: a saturation and brightness square, a hue bar, an opacity bar over a
 * checkerboard, the new color next to the old one, a hex field and the recently picked colors. The color changes live
 * while it is picked; Cancel or Esc gives the old color back.
 */
public class ColorPicker {

    private static final int SQUARE = 120;
    private static final int BAR = 12;
    private static final int GAP = 6;
    private static final int WIDTH = GAP + SQUARE + GAP + BAR + GAP + BAR + GAP + 76 + GAP;
    private static final int HEIGHT = 24 + SQUARE + GAP + 14 + GAP + 18 + GAP;
    private static final int RECENT_COUNT = 8;
    /** Colors picked before, newest first, shared by every picker. */
    private static final List<Integer> RECENT = new ArrayList<>();

    private enum Part {
        SQUARE,
        HUE,
        ALPHA
    }

    private boolean open;
    private IntConsumer onChange;
    private int original;
    private float hue, saturation, brightness, alpha;
    private Part dragging;
    private GuiTextField hex;
    private int x, y;

    public boolean isOpen() {
        return open;
    }

    /** Opens the picker on {@code argb}; {@code onChange} gets every new color, the old one again on cancel. */
    public void open(int argb, IntConsumer onChange) {
        this.open = true;
        this.onChange = onChange;
        this.original = argb;
        setColor(argb);
        Keyboard.enableRepeatEvents(true);
    }

    private void setColor(int argb) {
        float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
        // a gray has no hue: keep the one picked so far
        if (hsb[1] > 0 && hsb[2] > 0) hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = (argb >>> 24) / 255F;
    }

    public int color() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        return Math.round(alpha * 255) << 24 | rgb;
    }

    private void changed() {
        onChange.accept(color());
        if (hex != null && !hex.isFocused()) hex.setText(String.format("%08X", color()));
    }

    private void close(boolean keep) {
        if (keep) {
            int picked = color();
            RECENT.remove(Integer.valueOf(picked));
            RECENT.add(0, picked);
            while (RECENT.size() > RECENT_COUNT) RECENT.remove(RECENT.size() - 1);
        } else {
            onChange.accept(original);
        }
        open = false;
        dragging = null;
    }

    // ---- layout

    private void layout(int screenWidth, int screenHeight) {
        x = (screenWidth - WIDTH) / 2;
        y = (screenHeight - HEIGHT) / 2;
        if (hex == null) {
            hex = new GuiTextField(UI.font(), 0, 0, 68, 12);
            hex.setEnableBackgroundDrawing(false);
            hex.setMaxStringLength(9);
        }
        hex.xPosition = sideX() + 4;
        hex.yPosition = y + 24 + 48 + 4;
        if (!hex.isFocused()) hex.setText(String.format("%08X", color()));
    }

    private int squareX() {
        return x + GAP;
    }

    private int squareY() {
        return y + 24;
    }

    private int hueX() {
        return squareX() + SQUARE + GAP;
    }

    private int alphaX() {
        return hueX() + BAR + GAP;
    }

    private int sideX() {
        return alphaX() + BAR + GAP;
    }

    private int buttonsY() {
        return y + HEIGHT - GAP - 18;
    }

    // ---- drawing

    public void draw(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!open) return;
        layout(screenWidth, screenHeight);
        if (dragging != null) {
            if (Mouse.isButtonDown(0)) drag(mouseX, mouseY);
            else dragging = null;
        }
        GTNHTheme theme = UI.theme();

        // 3D previews behind, drawn closer than the GUI, would show through the picker
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        // dim everything behind
        UI.rect(0, 0, screenWidth, screenHeight, 0x80000000);
        UI.panel(x, y, WIDTH, HEIGHT);
        UI.text("Pick a color", x + GAP + 2, y + 8, theme.logo);

        int hueRgb = Color.HSBtoRGB(hue, 1, 1) | 0xFF000000;
        int sx = squareX(), sy = squareY();

        UI.beginShapes();
        // saturation from white to the hue, brightness from full to black
        GLDraw.horizontalGradient(sx, sy, SQUARE, SQUARE, 0xFFFFFFFF, hueRgb);
        GLDraw.verticalGradient(sx, sy, SQUARE, SQUARE, 0x00000000, 0xFF000000);
        // the hue bar, red at the top going round the color wheel
        for (int i = 0; i < 6; i++) {
            int top = Color.HSBtoRGB(i / 6F, 1, 1) | 0xFF000000;
            int bottom = Color.HSBtoRGB((i + 1) / 6F, 1, 1) | 0xFF000000;
            GLDraw.verticalGradient(hueX(), sy + SQUARE * i / 6F, BAR, SQUARE / 6F, top, bottom);
        }
        // the opacity bar over a checkerboard
        checkerboard(alphaX(), sy, BAR, SQUARE);
        int rgb = color() & 0xFFFFFF;
        GLDraw.verticalGradient(alphaX(), sy, BAR, SQUARE, 0xFF000000 | rgb, rgb);
        UI.endShapes();

        // markers: a ring on the square, a notch on the bars
        int mx = sx + Math.round(saturation * SQUARE), my = sy + Math.round((1 - brightness) * SQUARE);
        UI.rect(mx - 3, my - 3, 7, 7, 0xFF000000);
        UI.rect(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
        UI.rect(mx - 1, my - 1, 3, 3, color() | 0xFF000000);
        marker(hueX(), sy + Math.round(hue * SQUARE));
        marker(alphaX(), sy + Math.round((1 - alpha) * SQUARE));
        frame(sx, sy, SQUARE, SQUARE);
        frame(hueX(), sy, BAR, SQUARE);
        frame(alphaX(), sy, BAR, SQUARE);

        // new and old color
        int side = sideX();
        UI.text("New", side, sy, theme.textDim);
        UI.text("Old", side + 40, sy, theme.textDim);
        swatch(side, sy + 10, 36, 24, color());
        swatch(side + 40, sy + 10, 36, 24, original);

        // hex
        UI.text("Hex (AARRGGBB)", side, sy + 38, theme.textDim);
        UI.inset(side, sy + 48, 76, 16);
        hex.setTextColor(theme.text & 0xFFFFFF);
        hex.drawTextBox();

        // channels
        int c = color();
        UI.text("R " + (c >> 16 & 0xFF) + "  G " + (c >> 8 & 0xFF), side, sy + 70, theme.textDim);
        UI.text("B " + (c & 0xFF) + "  A " + (c >>> 24), side, sy + 80, theme.textDim);
        UI.text(Math.round(alpha * 100) + "% opaque", side, sy + 94, theme.textDisabled);

        // recent colors
        int ry = sy + SQUARE + GAP;
        UI.text("Recent", x + GAP + 2, ry + 3, theme.textDim);
        for (int i = 0; i < RECENT_COUNT; i++) {
            int rx = recentX(i);
            if (i < RECENT.size()) {
                swatch(rx, ry, 14, 14, RECENT.get(i));
                if (UI.inside(mouseX, mouseY, rx, ry, 14, 14)) UI.rect(rx, ry + 13, 14, 1, theme.barFill);
            } else {
                UI.inset(rx, ry, 14, 14);
            }
        }

        int half = (WIDTH - GAP * 3) / 2;
        UI.button(x + GAP, buttonsY(), half, 18, "Cancel", mouseX, mouseY, true);
        UI.button(x + GAP * 2 + half, buttonsY(), half, 18, "OK", mouseX, mouseY, true);
    }

    private int recentX(int i) {
        return x + GAP + 42 + i * 18;
    }

    private static void checkerboard(int x, int y, int w, int h) {
        int cell = 4;
        for (int cy = 0; cy < h; cy += cell) {
            for (int cx = 0; cx < w; cx += cell) {
                GLDraw.rect(
                    x + cx,
                    y + cy,
                    Math.min(cell, w - cx),
                    Math.min(cell, h - cy),
                    ((cx + cy) / cell & 1) == 0 ? 0xFFBFBFBF : 0xFF7F7F7F);
            }
        }
    }

    /** A color over a checkerboard, so its opacity shows. */
    private static void swatch(int x, int y, int w, int h, int argb) {
        UI.inset(x, y, w, h);
        UI.beginShapes();
        checkerboard(x + 2, y + 2, w - 4, h - 4);
        GLDraw.rect(x + 2, y + 2, w - 4, h - 4, argb);
        UI.endShapes();
    }

    private static void frame(int x, int y, int w, int h) {
        int border = UI.theme()
            .isBeveled() ? UI.theme().inset.topLeft : UI.theme().barBorder;
        UI.rect(x - 1, y - 1, w + 2, 1, border);
        UI.rect(x - 1, y + h, w + 2, 1, border);
        UI.rect(x - 1, y, 1, h, border);
        UI.rect(x + w, y, 1, h, border);
    }

    private static void marker(int x, int y) {
        UI.rect(x - 2, y - 2, BAR + 4, 5, 0xFF000000);
        UI.rect(x - 1, y - 1, BAR + 2, 3, 0xFFFFFFFF);
    }

    // ---- input

    private void drag(int mouseX, int mouseY) {
        float tx = clamp((mouseX - squareX()) / (float) SQUARE);
        float ty = clamp((mouseY - squareY()) / (float) SQUARE);
        if (dragging == Part.SQUARE) {
            saturation = tx;
            brightness = 1 - ty;
        } else if (dragging == Part.HUE) {
            hue = Math.min(0.9999F, ty);
        } else if (dragging == Part.ALPHA) {
            alpha = 1 - ty;
        }
        changed();
    }

    private static float clamp(float t) {
        return Math.max(0, Math.min(1, t));
    }

    /** @return true if the picker is open and took the click */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) return false;
        hex.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return true;
        int sy = squareY();
        if (UI.inside(mouseX, mouseY, squareX(), sy, SQUARE, SQUARE)) dragging = Part.SQUARE;
        else if (UI.inside(mouseX, mouseY, hueX() - 2, sy, BAR + 4, SQUARE)) dragging = Part.HUE;
        else if (UI.inside(mouseX, mouseY, alphaX() - 2, sy, BAR + 4, SQUARE)) dragging = Part.ALPHA;
        if (dragging != null) {
            drag(mouseX, mouseY);
            return true;
        }
        int ry = sy + SQUARE + GAP;
        for (int i = 0; i < RECENT.size(); i++) {
            if (UI.inside(mouseX, mouseY, recentX(i), ry, 14, 14)) {
                setColor(RECENT.get(i));
                changed();
                UI.playClick();
                return true;
            }
        }
        int half = (WIDTH - GAP * 3) / 2;
        if (UI.inside(mouseX, mouseY, x + GAP, buttonsY(), half, 18)) {
            UI.playClick();
            close(false);
        } else if (UI.inside(mouseX, mouseY, x + GAP * 2 + half, buttonsY(), half, 18)) {
            UI.playClick();
            close(true);
        } else if (!UI.inside(mouseX, mouseY, x, y, WIDTH, HEIGHT)) {
            // a click outside keeps the color, like most pickers
            close(true);
        }
        return true;
    }

    /** @return true if the picker is open and took the key */
    public boolean keyTyped(char c, int code) {
        if (!open) return false;
        if (code == Keyboard.KEY_ESCAPE) {
            close(false);
            return true;
        }
        if (hex.isFocused()) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                hex.setFocused(false);
                hex.setText(String.format("%08X", color()));
                return true;
            }
            hex.textboxKeyTyped(c, code);
            String text = hex.getText()
                .trim()
                .replace("#", "");
            if (text.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
                long value = Long.parseLong(text, 16);
                if (text.length() == 6) value |= 0xFF000000L;
                setColor((int) value);
                onChange.accept(color());
            }
            return true;
        }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) close(true);
        return true;
    }

    public void update() {
        if (open && hex != null) hex.updateCursorCounter();
    }
}
