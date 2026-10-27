package gtnhtweaks.modules.loadingscreen.render;

/** One line of loading progress: a caption and an optional bar. */
public final class ProgressLine {

    public final String text;
    /** Progress from 0 to 1, or NaN when the line has no bar. */
    public final float progress;
    /** Right-aligned counter such as "12/40" or "30%", may be empty. */
    public final String counter;

    public ProgressLine(String text, float progress, String counter) {
        this.text = text == null ? "" : text;
        this.progress = progress;
        this.counter = counter == null ? "" : counter;
    }

    public static ProgressLine ofSteps(String text, int step, int steps) {
        float progress = steps > 0 ? Math.min(1F, Math.max(0F, (float) step / steps)) : Float.NaN;
        return new ProgressLine(text, progress, step + "/" + steps);
    }

    public static ProgressLine ofPercent(String text, float progress) {
        if (Float.isNaN(progress)) return new ProgressLine(text, Float.NaN, "");
        float clamped = Math.min(1F, Math.max(0F, progress));
        return new ProgressLine(text, clamped, (int) (clamped * 100) + "%");
    }
}
