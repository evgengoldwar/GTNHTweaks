package gtnhtweaks.common.render;

import static gtnhtweaks.common.render.GLDraw.rect;

/** The "GTNH" block-letter logo that takes the place of the Mojang logo. Drawn with plain quads, no textures. */
public final class GTNHLogo {

    private static final String[][] LETTERS = { //
        { ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###." }, // G
        { "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.." }, // T
        { "#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#" }, // N
        { "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#" }, // H
    };

    private static final int LETTER_WIDTH = 5;
    private static final int LETTER_HEIGHT = 7;
    private static final int LETTER_GAP = 1;

    private GTNHLogo() {}

    public static int width(int cell) {
        return (LETTERS.length * LETTER_WIDTH + (LETTERS.length - 1) * LETTER_GAP) * cell;
    }

    public static int height(int cell) {
        return LETTER_HEIGHT * cell;
    }

    public static void draw(int x, int y, int cell, GTNHTheme theme) {
        int shadow = Math.max(1, cell / 3);
        int edge = Math.max(1, cell / 5);
        drawCells(x + shadow, y + shadow, cell, 0, theme.logoShadow, 0, 0);
        drawCells(x, y, cell, edge, theme.logo, theme.logoLight, theme.logoDark);
    }

    /** Draws every filled cell; edges facing an empty cell get a light (top, left) or dark (bottom, right) bevel. */
    private static void drawCells(int x, int y, int cell, int edge, int color, int light, int dark) {
        for (int letter = 0; letter < LETTERS.length; letter++) {
            int letterX = x + letter * (LETTER_WIDTH + LETTER_GAP) * cell;
            for (int row = 0; row < LETTER_HEIGHT; row++) {
                for (int col = 0; col < LETTER_WIDTH; col++) {
                    if (!filled(letter, row, col)) continue;
                    int cx = letterX + col * cell;
                    int cy = y + row * cell;
                    rect(cx, cy, cell, cell, color);
                    if (edge <= 0) continue;
                    if (!filled(letter, row - 1, col)) rect(cx, cy, cell, edge, light);
                    if (!filled(letter, row, col - 1)) rect(cx, cy, edge, cell, light);
                    if (!filled(letter, row + 1, col)) rect(cx, cy + cell - edge, cell, edge, dark);
                    if (!filled(letter, row, col + 1)) rect(cx + cell - edge, cy, edge, cell, dark);
                }
            }
        }
    }

    private static boolean filled(int letter, int row, int col) {
        if (row < 0 || row >= LETTER_HEIGHT || col < 0 || col >= LETTER_WIDTH) return false;
        return LETTERS[letter][row].charAt(col) == '#';
    }
}
