package gtnhtweaks.client.settings;

/** Smooth scrolling of a list taller than its view, with a draggable bar. */
final class Scroll {

    static final int BAR_WIDTH = 5;
    private static final int WHEEL_STEP = 36;

    float value, target;
    /** Height of everything in the list. */
    int content;
    /** Height of the visible part. */
    int view;
    boolean dragging;

    int max() {
        return Math.max(0, content - view);
    }

    boolean active() {
        return max() > 0;
    }

    void clamp() {
        target = Math.max(0, Math.min(max(), target));
        value = Math.max(0, Math.min(max(), value));
    }

    void update(float dtMs) {
        value = UI.approach(value, target, dtMs, 120);
    }

    void wheel(int direction) {
        target -= direction * WHEEL_STEP;
        clamp();
    }

    void reset() {
        value = target = 0;
    }

    /** Scrolls just enough to show the part of the list from {@code top} to {@code bottom}. */
    void show(int top, int bottom) {
        if (top < target) target = top;
        else if (bottom > target + view) target = bottom - view;
        clamp();
    }

    int offset() {
        return Math.round(value);
    }

    private int thumbHeight(int trackH) {
        return Math.max(16, trackH * view / Math.max(1, content));
    }

    /** The bar at the right of the track from {@code y}, {@code h} high. */
    void drawBar(int x, int y, int h, int mouseX, int mouseY) {
        if (!active()) return;
        int thumbH = thumbHeight(h);
        int thumbY = y + Math.round((h - thumbH) * value / max());
        boolean hovered = dragging || UI.inside(mouseX, mouseY, x - 1, y, BAR_WIDTH + 2, h);
        UI.button(x, thumbY, BAR_WIDTH, thumbH, hovered, true);
    }

    /** Starts dragging when the click is on the track. */
    boolean press(int x, int y, int h, int mouseX, int mouseY) {
        if (!active() || !UI.inside(mouseX, mouseY, x - 2, y, BAR_WIDTH + 4, h)) return false;
        dragging = true;
        drag(y, h, mouseY);
        return true;
    }

    void drag(int y, int h, int mouseY) {
        int thumbH = thumbHeight(h);
        float t = (mouseY - y - thumbH / 2F) / Math.max(1, h - thumbH);
        target = value = Math.max(0, Math.min(1, t)) * max();
    }
}
