package gtnhtweaks.common.render;

import static gtnhtweaks.common.render.GLDraw.rect;

/**
 * Panels and bars drawn in the look of the theme: beveled in SHADOW, flat with a 1 px border in the other themes.
 * GL_TEXTURE_2D must be disabled.
 */
public final class ThemeShapes {

    private ThemeShapes() {}

    /** A raised panel, like the background of an inventory. */
    public static void panel(float x, float y, float w, float h, GTNHTheme theme) {
        if (theme.isBeveled()) GLDraw.bevel(x, y, w, h, theme.panel);
        else {
            rect(x, y, w, h, theme.barBorder);
            rect(x + 1, y + 1, w - 2, h - 2, theme.buttonBackground);
        }
    }

    /** A sunken box, like a slot, with the given fill. */
    public static void inset(float x, float y, float w, float h, GTNHTheme theme) {
        if (theme.isBeveled()) GLDraw.bevel(x, y, w, h, theme.inset);
        else {
            rect(x, y, w, h, theme.barBorder);
            rect(x + 1, y + 1, w - 2, h - 2, theme.barBackground);
        }
    }

    /** Size of the edge of {@link #inset}, where the content of the box starts. */
    public static int insetEdge(GTNHTheme theme) {
        return theme.isBeveled() ? theme.inset.edge : 1;
    }

    /** A progress bar: sunken box, filled from the left by {@code progress} from 0 to 1. */
    public static void bar(float x, float y, float w, float h, float progress, int fill, GTNHTheme theme) {
        inset(x, y, w, h, theme);
        int e = insetEdge(theme);
        float inner = w - e * 2;
        if (progress > 0) rect(x + e, y + e, inner * Math.min(1, progress), h - e * 2, fill);
    }
}
