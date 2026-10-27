package gtnhtweaks.modules.chromatictooltips;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;

/**
 * Widths of tooltip lines as they were drawn. Chromatic Tooltips sizes the frame by {@link FontRenderer#getStringWidth}
 * before drawing, and some text draws wider than that: the frame takes the drawn width from the next layout on, which
 * Chromatic Tooltips redoes many times a second.
 */
public final class DrawnTextWidths {

    private static final int MAX_LINES = 512;

    /** Only the lines that drew wider than measured, the most recently drawn last. */
    private static final Map<String, Integer> WIDER = new LinkedHashMap<String, Integer>(64, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_LINES;
        }
    };

    private DrawnTextWidths() {}

    /**
     * @param end what {@link FontRenderer#drawString(String, int, int, int, boolean)} returned: the x after the text,
     *            one more with a shadow
     */
    public static void onDrawn(FontRenderer font, String text, int x, boolean shadow, int end) {
        if (text == null || text.isEmpty()) return;
        int drawn = end - x - (shadow ? 1 : 0);
        if (drawn > font.getStringWidth(text)) {
            WIDER.put(text, drawn);
        } else {
            WIDER.remove(text);
        }
    }

    /** The measured width, or the drawn one when the text drew wider. */
    public static int fit(String text, int measured) {
        if (text == null) return measured;
        Integer drawn = WIDER.get(text);
        return drawn != null && drawn > measured ? drawn : measured;
    }
}
