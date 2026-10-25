package gtnhtweaks.modules.mainmenu;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import gtnhtweaks.common.render.GLDraw;
import gtnhtweaks.common.render.GTNHTheme;

/**
 * Animated circuit board behind the main menu: copper traces with 45 degree bends and chips stamped with a GregTech
 * gear, with pulses of current running along the traces. The board is laid out once per window size; only the pulses change from frame to frame.
 * Everything is in GUI coordinates, so the board gets the blocky look of the rest of the GUI.
 */
final class CircuitBackground {

    private static final int[][] DIRECTIONS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 },
        { 0, -1 }, { 1, -1 } };
    /** Cells around the screen, so the parallax never shows the edge of the board. */
    private static final int MARGIN = 2;

    /** GregTech voltage tiers: color and how common they are on the board. */
    private static final int[] TIER_COLORS = { 0xFFDCDCDC, 0xFFFFAA00, 0xFFFFFF55, 0xFFC25CFF, 0xFF6F7DFF, 0xFFFF55FF,
        0xFFFF5555, 0xFF22D3D3, 0xFFE03030, 0xFF55FF55 };
    private static final int[] TIER_WEIGHTS = { 9, 8, 7, 6, 5, 4, 3, 2, 1, 1 };
    /** The gear stamped on every chip, 11 by 11 pixels of a seventh of a cell; its hole shows the tier color. */
    private static final String[] GEAR = { "...#.#.#...", "..#######..", ".#########.", "###.....###", ".##.....##.",
        "###.....###", ".##.....##.", "###.....###", ".#########.", "..#######..", "...#.#.#..." };

    private static final class Trace {

        final float[] xs, ys, lengths;
        final int tier;
        final Chip chip;
        long nextPulse;

        Trace(List<int[]> points, int tier, Chip chip, long nextPulse) {
            int n = points.size();
            xs = new float[n];
            ys = new float[n];
            lengths = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i)[0];
                ys[i] = points.get(i)[1];
                if (i > 0) lengths[i] = lengths[i - 1] + (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
            this.tier = tier;
            this.chip = chip;
            this.nextPulse = nextPulse;
        }

        float length() {
            return lengths[lengths.length - 1];
        }
    }

    private static final class Chip {

        final int x, y, w, h, tier;
        float lit;

        Chip(int x, int y, int w, int h, int tier) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.tier = tier;
        }
    }

    private static final class Pulse {

        final Trace trace;
        final float speed, trail;
        float position;
        boolean flashed;

        Pulse(Trace trace, float speed, float trail) {
            this.trace = trace;
            this.speed = speed;
            this.trail = trail;
        }
    }

    private static final class Flash {

        final float x, y;
        final int color;
        float life = 1;

        Flash(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private static final Random random = new Random();
    private static final List<Trace> traces = new ArrayList<>();
    private static final List<Chip> chips = new ArrayList<>();
    private static final List<Pulse> pulses = new ArrayList<>();
    private static final List<Flash> flashes = new ArrayList<>();

    private static int builtWidth = -1, builtHeight, builtRows;
    private static boolean builtChips;
    private static float cell;
    private static long lastNanos;
    private static float offsetX, offsetY;
    private static final float[] point = new float[2];

    private CircuitBackground() {}

    /** Draws the board and moves the pulses on. GL_TEXTURE_2D must be disabled and blending on. */
    static void draw(int width, int height, int mouseX, int mouseY, GTNHTheme theme) {
        if (width != builtWidth || height != builtHeight
            || MainMenuModule.circuitRows != builtRows
            || MainMenuModule.circuitChips != builtChips) build(width, height);

        long nanos = System.nanoTime();
        // the menu was closed meanwhile: carry on from where it was
        float dt = lastNanos == 0 ? 0 : Math.min(0.05F, (nanos - lastNanos) / 1e9F);
        lastNanos = nanos;
        long now = System.currentTimeMillis();

        float targetX = 0, targetY = 0;
        if (MainMenuModule.circuitParallax && width > 0 && height > 0) {
            targetX = ((float) mouseX / width - 0.5F) * cell * 0.6F;
            targetY = ((float) mouseY / height - 0.5F) * cell * 0.6F;
        }
        offsetX += (targetX - offsetX) * Math.min(1, dt * 4);
        offsetY += (targetY - offsetY) * Math.min(1, dt * 4);

        float brightness = MainMenuModule.circuitBrightness;
        boolean light = isLight(theme);
        drawBoard(theme, 0.55F + brightness * 0.45F);

        glBlendFunc(GL_SRC_ALPHA, light ? GL_ONE_MINUS_SRC_ALPHA : GL_ONE);
        spawnPulses(now);
        drawPulses(dt, brightness, theme);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        drawVignette(width, height, light ? 0x8CFFFFFF : 0x8C000000);
    }

    // ---- layout

    private static void build(int width, int height) {
        builtWidth = width;
        builtHeight = height;
        builtRows = MainMenuModule.circuitRows;
        builtChips = MainMenuModule.circuitChips;
        traces.clear();
        chips.clear();
        pulses.clear();
        flashes.clear();

        cell = Math.max(4F, (float) height / builtRows);
        int columns = (int) Math.ceil(width / cell) + MARGIN * 2;
        int rows = builtRows + 1 + MARGIN * 2;
        boolean[] used = new boolean[columns * rows];
        long now = System.currentTimeMillis();

        if (builtChips) {
            int count = columns * rows / 140;
            for (int tries = 0; chips.size() < count && tries < 400; tries++) {
                int w = 3 + random.nextInt(4), h = 2 + random.nextInt(2);
                int x = 1 + random.nextInt(Math.max(1, columns - w - 2));
                int y = 1 + random.nextInt(Math.max(1, rows - h - 2));
                if (!isFree(used, columns, rows, x - 2, y - 2, w + 4, h + 4)) continue;
                for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) used[yy * columns + xx] = true;
                Chip chip = new Chip(x, y, w, h, pickTier());
                chips.add(chip);
                // the pins on the long sides start traces going away from the chip
                for (int xx = x; xx < x + w; xx++) {
                    if (random.nextFloat() < 0.75F) walk(used, columns, rows, xx, y - 1, 6, chip.tier, chip, now);
                    if (random.nextFloat() < 0.75F) walk(used, columns, rows, xx, y + h, 2, chip.tier, chip, now);
                }
            }
        }
        int loose = columns * rows / 9;
        for (int i = 0; i < loose; i++) {
            int x = random.nextInt(columns), y = random.nextInt(rows);
            walk(used, columns, rows, x, y, random.nextInt(4) * 2, pickTier(), null, now);
        }
    }

    private static boolean isFree(boolean[] used, int columns, int rows, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (isUsed(used, columns, rows, xx, yy)) return false;
            }
        }
        return true;
    }

    private static boolean isUsed(boolean[] used, int columns, int rows, int x, int y) {
        return x < 0 || y < 0 || x >= columns || y >= rows || used[y * columns + x];
    }

    /** Lays a trace from (x, y): straight runs of copper, then a 45 degree bend, like a real board. */
    private static void walk(boolean[] used, int columns, int rows, int x, int y, int dir, int tier, Chip chip,
        long now) {
        if (isUsed(used, columns, rows, x, y)) return;
        List<int[]> points = new ArrayList<>();
        List<Integer> marked = new ArrayList<>();
        points.add(new int[] { x, y });
        used[y * columns + x] = true;
        marked.add(y * columns + x);
        int max = 4 + random.nextInt(22);
        int straight = 0;
        for (int step = 0; step < max; step++) {
            int d = dir;
            if (straight > 2 && random.nextFloat() < 0.28F) d = (dir + (random.nextBoolean() ? 1 : 7)) % 8;
            int nx = x + DIRECTIONS[d][0], ny = y + DIRECTIONS[d][1];
            // a diagonal step may not cut through another diagonal
            boolean crossing = d % 2 == 1 && isUsed(used, columns, rows, nx, y) && isUsed(used, columns, rows, x, ny);
            if (isUsed(used, columns, rows, nx, ny) || crossing) break;
            straight = d == dir ? straight + 1 : 0;
            dir = d;
            x = nx;
            y = ny;
            points.add(new int[] { x, y });
            used[y * columns + x] = true;
            marked.add(y * columns + x);
        }
        if (points.size() < 4) {
            for (int index : marked) used[index] = false;
            return;
        }
        traces.add(new Trace(points, tier, chip, now + random.nextInt(6000)));
    }

    private static int pickTier() {
        int total = 0;
        for (int weight : TIER_WEIGHTS) total += weight;
        int pick = random.nextInt(total);
        for (int tier = 0; tier < TIER_WEIGHTS.length; tier++) {
            pick -= TIER_WEIGHTS[tier];
            if (pick < 0) return tier;
        }
        return 0;
    }

    // ---- drawing

    /** Screen position of the middle of a grid cell. */
    private static float x(float gridX) {
        return (gridX - MARGIN) * cell + cell / 2 + offsetX;
    }

    private static float y(float gridY) {
        return (gridY - MARGIN) * cell + cell / 2 + offsetY;
    }

    private static void drawBoard(GTNHTheme theme, float alpha) {
        int trace = withAlpha(theme.textDim, 0.16F * alpha);
        int pad = withAlpha(theme.textDim, 0.30F * alpha);
        float thickness = Math.max(1F, cell * 0.13F);
        for (Trace t : traces) {
            for (int i = 1; i < t.xs.length; i++) {
                line(x(t.xs[i - 1]), y(t.ys[i - 1]), x(t.xs[i]), y(t.ys[i]), thickness, trace);
            }
            int last = t.xs.length - 1;
            ring(x(t.xs[last]), y(t.ys[last]), cell * 0.24F, Math.max(1F, cell * 0.09F), pad);
            if (t.chip == null) {
                float size = cell * 0.36F;
                GLDraw.rect(x(t.xs[0]) - size / 2, y(t.ys[0]) - size / 2, size, size, pad);
            }
        }
        int pin = withAlpha(theme.textDim, 0.55F * alpha);
        int edge = withAlpha(theme.textDisabled, 0.6F * alpha);
        int body = withAlpha(darker(theme.backgroundBottom), 0.95F);
        for (Chip chip : chips) {
            float left = x(chip.x) - cell / 2 + cell * 0.1F, top = y(chip.y) - cell / 2 + cell * 0.1F;
            float w = chip.w * cell - cell * 0.2F, h = chip.h * cell - cell * 0.2F;
            for (int i = 0; i < chip.w; i++) {
                float pinX = x(chip.x + i) - cell * 0.15F;
                GLDraw.rect(pinX, top - cell * 0.22F, cell * 0.3F, cell * 0.22F, pin);
                GLDraw.rect(pinX, top + h, cell * 0.3F, cell * 0.22F, pin);
            }
            GLDraw.rect(left, top, w, h, edge);
            GLDraw.rect(left + 1, top + 1, w - 2, h - 2, body);
            disc(left + cell * 0.35F, top + cell * 0.35F, cell * 0.1F, edge, edge);
            drawGear(chip, theme, alpha, 0.45F * MainMenuModule.circuitBrightness);
        }
    }

    /** The stamped gear in the middle of a chip, with the tier color in its hole at {@code glow} strength. */
    private static void drawGear(Chip chip, GTNHTheme theme, float alpha, float glow) {
        float unit = cell / 7;
        float left = x(chip.x) - cell / 2 + chip.w * cell / 2 - unit * 5.5F;
        float top = y(chip.y) - cell / 2 + chip.h * cell / 2 - unit * 5.5F;
        int gear = withAlpha(theme.textDisabled, 0.6F * alpha);
        int edge = withAlpha(theme.textDisabled, 0.9F * alpha);
        for (int row = 0; row < GEAR.length; row++) {
            for (int column = 0; column < GEAR[row].length(); column++) {
                if (GEAR[row].charAt(column) != '#') continue;
                float x = left + column * unit, y = top + row * unit;
                GLDraw.rect(x, y, unit, unit, gear);
                // light falls from above: the top edges of the stamp catch it
                if (row == 0 || GEAR[row - 1].charAt(column) != '#') GLDraw.rect(x, y, unit, unit * 0.3F, edge);
            }
        }
        if (glow > 0) {
            GLDraw.rect(left + unit * 4, top + unit * 4, unit * 3, unit * 3, withAlpha(color(chip.tier, theme), glow));
        }
    }

    private static void spawnPulses(long now) {
        for (Trace t : traces) {
            if (now < t.nextPulse) continue;
            pulses.add(
                new Pulse(
                    t,
                    MainMenuModule.circuitPulseSpeed * (0.7F + random.nextFloat() * 0.6F),
                    2.5F + random.nextFloat() * 3));
            t.nextPulse = now + 1200 + random.nextInt(5000);
            if (t.chip != null) t.chip.lit = 1;
        }
    }

    private static void drawPulses(float dt, float brightness, GTNHTheme theme) {
        float head = Math.max(1F, cell * 0.16F);
        for (int i = pulses.size() - 1; i >= 0; i--) {
            Pulse p = pulses.get(i);
            Trace t = p.trace;
            p.position += p.speed * dt;
            int color = color(t.tier, theme);
            // the trail: squares along the copper, fading out behind the head
            int steps = (int) Math.ceil(p.trail * 5);
            for (int k = 0; k <= steps; k++) {
                float s = p.position - k * p.trail / steps;
                if (s < 0 || s > t.length()) continue;
                pointAt(t, s);
                float fade = (1 - (float) k / steps) * brightness;
                float size = head * (1 - k * 0.5F / steps);
                GLDraw.rect(x(point[0]) - size / 2, y(point[1]) - size / 2, size, size, withAlpha(color, fade * fade));
            }
            if (p.position <= t.length()) {
                pointAt(t, p.position);
                disc(x(point[0]), y(point[1]), head * 2.2F, withAlpha(color, 0.35F * brightness), color & 0xFFFFFF);
            }
            if (p.position - p.trail > t.length()) {
                pulses.remove(i);
            } else if (p.position > t.length() && !p.flashed) {
                p.flashed = true;
                int last = t.xs.length - 1;
                flashes.add(new Flash(t.xs[last], t.ys[last], color));
            }
        }
        for (int i = flashes.size() - 1; i >= 0; i--) {
            Flash f = flashes.get(i);
            f.life -= dt * 2.2F;
            if (f.life <= 0) {
                flashes.remove(i);
                continue;
            }
            ring(
                x(f.x),
                y(f.y),
                cell * (0.24F + (1 - f.life) * 0.35F),
                Math.max(1F, cell * 0.1F),
                withAlpha(f.color, f.life * brightness));
        }
        // a small LED on each chip blinks when it sends a pulse, and the hole of its gear lights up
        for (Chip chip : chips) {
            if (chip.lit <= 0) continue;
            float left = x(chip.x) - cell / 2 + cell * 0.1F, top = y(chip.y) - cell / 2 + cell * 0.1F;
            int color = withAlpha(color(chip.tier, theme), chip.lit * brightness);
            disc(left + cell * 0.35F, top + cell * 0.35F, cell * 0.12F, color, color);
            float unit = cell / 7;
            float holeX = x(chip.x) - cell / 2 + chip.w * cell / 2 - unit * 1.5F;
            float holeY = y(chip.y) - cell / 2 + chip.h * cell / 2 - unit * 1.5F;
            GLDraw.rect(holeX, holeY, unit * 3, unit * 3, withAlpha(color, 0.55F));
            chip.lit = Math.max(0, chip.lit - dt * 1.5F);
        }
    }

    /** Darkens the edges of the screen, so the board stays behind the buttons. */
    private static void drawVignette(int width, int height, int edge) {
        float cx = width / 2F, cy = height * 0.55F;
        int clear = edge & 0xFFFFFF;
        float[][] perimeter = { { 0, 0 }, { cx, 0 }, { width, 0 }, { width, cy }, { width, height }, { cx, height },
            { 0, height }, { 0, cy }, { 0, 0 } };
        glBegin(GL_TRIANGLE_FAN);
        GLDraw.color(clear);
        glVertex2f(cx, cy);
        for (float[] corner : perimeter) {
            // the middles of the sides are nearer the center, so they get less shade
            boolean side = corner[0] == cx || corner[1] == cy;
            GLDraw.color(side ? withAlpha(edge, 0.45F) : edge);
            glVertex2f(corner[0], corner[1]);
        }
        glEnd();
    }

    private static void pointAt(Trace t, float s) {
        int i = 1;
        while (i < t.lengths.length - 1 && t.lengths[i] < s) i++;
        float segment = t.lengths[i] - t.lengths[i - 1];
        float k = segment > 0 ? Math.max(0, Math.min(1, (s - t.lengths[i - 1]) / segment)) : 0;
        point[0] = t.xs[i - 1] + (t.xs[i] - t.xs[i - 1]) * k;
        point[1] = t.ys[i - 1] + (t.ys[i] - t.ys[i - 1]) * k;
    }

    private static int color(int tier, GTNHTheme theme) {
        return MainMenuModule.circuitTierColors ? TIER_COLORS[tier] : theme.barFill;
    }

    /** A thick line with square ends, so the bends of a trace join up. */
    private static void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0) return;
        float half = thickness / 2;
        float nx = -dy / length * half, ny = dx / length * half;
        float ex = dx / length * half, ey = dy / length * half;
        GLDraw.color(argb);
        glBegin(GL_QUADS);
        glVertex2f(x1 - ex + nx, y1 - ey + ny);
        glVertex2f(x1 - ex - nx, y1 - ey - ny);
        glVertex2f(x2 + ex - nx, y2 + ey - ny);
        glVertex2f(x2 + ex + nx, y2 + ey + ny);
        glEnd();
    }

    private static void ring(float cx, float cy, float radius, float thickness, int argb) {
        GLDraw.color(argb);
        glBegin(GL_QUAD_STRIP);
        for (int i = 0; i <= 16; i++) {
            double angle = i * Math.PI / 8;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            glVertex2f(cx + cos * (radius + thickness / 2), cy + sin * (radius + thickness / 2));
            glVertex2f(cx + cos * (radius - thickness / 2), cy + sin * (radius - thickness / 2));
        }
        glEnd();
    }

    /** A disc going from the center color to the edge color. */
    private static void disc(float cx, float cy, float radius, int center, int edge) {
        glBegin(GL_TRIANGLE_FAN);
        GLDraw.color(center);
        glVertex2f(cx, cy);
        GLDraw.color(edge);
        for (int i = 0; i <= 16; i++) {
            double angle = i * Math.PI / 8;
            glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        glEnd();
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha)));
        return a << 24 | argb & 0xFFFFFF;
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 4 / 5, g = (argb >> 8 & 0xFF) * 4 / 5, b = (argb & 0xFF) * 4 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static boolean isLight(GTNHTheme theme) {
        int c = theme.backgroundTop;
        return (c >> 16 & 0xFF) + (c >> 8 & 0xFF) + (c & 0xFF) > 3 * 0x80;
    }
}
