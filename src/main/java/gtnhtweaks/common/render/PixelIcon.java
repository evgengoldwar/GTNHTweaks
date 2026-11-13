package gtnhtweaks.common.render;

import static org.lwjgl.opengl.GL11.*;

/** Small pixel icons for square buttons, one character per pixel: '#' is drawn, anything else is not. */
public final class PixelIcon {

    /** The globe of the Language button, as the vanilla icon. */
    public static final String[] GLOBE = { //
        "....####....", //
        "..##.##.##..", //
        ".#..#..#..#.", //
        ".#..#..#..#.", //
        "############", //
        "#...#..#...#", //
        "#...#..#...#", //
        "############", //
        ".#..#..#..#.", //
        ".#..#..#..#.", //
        "..##.##.##..", //
        "....####....", //
    };

    /** An arrow down into a tray: the button of the mods to download. */
    public static final String[] DOWNLOAD = { //
        ".....##.....", //
        ".....##.....", //
        ".....##.....", //
        ".....##.....", //
        "..##.##.##..", //
        "...######...", //
        "....####....", //
        ".....##.....", //
        "............", //
        "##........##", //
        "##........##", //
        "############", //
    };

    private PixelIcon() {}

    /** Draws the icon centered in the box. */
    public static void drawCentered(String[] icon, int x, int y, int w, int h, int argb) {
        draw(icon, x + (w - icon[0].length()) / 2, y + (h - icon.length) / 2, argb);
    }

    public static void draw(String[] icon, int x, int y, int argb) {
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT);
        try {
            glDisable(GL_TEXTURE_2D);
            glDisable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            for (int row = 0; row < icon.length; row++) {
                for (int column = 0; column < icon[row].length(); column++) {
                    if (icon[row].charAt(column) == '#') GLDraw.rect(x + column, y + row, 1, 1, argb);
                }
            }
        } finally {
            glPopAttrib();
            glColor4f(1, 1, 1, 1);
        }
    }
}
