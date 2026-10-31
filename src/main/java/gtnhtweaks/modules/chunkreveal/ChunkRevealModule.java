package gtnhtweaks.modules.chunkreveal;

import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.core.TweakModule;

/**
 * New chunks of the world appear with an animation instead of out of nothing: they rise from below, slide in or grow.
 * Works with the renderer of Minecraft and with the one of Angelica; with a shader pack of Angelica the chunks appear
 * as
 * usual.
 */
public final class ChunkRevealModule extends TweakModule {

    public static final ChunkRevealModule INSTANCE = new ChunkRevealModule();

    private static final String CATEGORY_ANIMATION = "animation";

    /** How a chunk appears. */
    public enum Style {

        /** At once, as in vanilla. */
        DEFAULT,
        RISE,
        SLIDE,
        GROW;

        static Style parse(String name) {
            try {
                return valueOf(
                    name.trim()
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return RISE;
            }
        }
    }

    public static Style style = Style.RISE;
    /** True: the chunks near the player first, in a wave; false: in a random order. */
    public static boolean wave = true;
    /** How long one chunk takes to appear, in milliseconds. */
    public static int durationMs = 800;
    /** How far a chunk moves while it appears, in blocks. */
    public static int distance = 24;

    private ChunkRevealModule() {
        super("chunkreveal", "New chunks appear with an animation instead of out of nothing", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(
            CATEGORY_ANIMATION,
            "How new chunks appear. Works with and without Angelica; with an Angelica shader pack the chunks appear "
                + "as usual. Changes take effect at once");
        String styleName = config.getString(
            "style",
            CATEGORY_ANIMATION,
            "RISE",
            "DEFAULT (at once, as in vanilla), RISE (from below), SLIDE (from the outside toward the player) or GROW "
                + "(from the middle of the chunk)",
            new String[] { "DEFAULT", "RISE", "SLIDE", "GROW" });
        style = Style.parse(styleName);
        // a style that was removed, as DROP and DISSOLVE, becomes RISE in the file too
        if (!style.name()
            .equals(styleName))
            config.get(CATEGORY_ANIMATION, "style", "RISE")
                .set(style.name());
        wave = "WAVE".equals(
            config.getString(
                "order",
                CATEGORY_ANIMATION,
                "WAVE",
                "WAVE (the chunks near the player first) or RANDOM (in a random order)",
                new String[] { "WAVE", "RANDOM" }));
        durationMs = config.getInt(
            "durationMs",
            CATEGORY_ANIMATION,
            durationMs,
            100,
            3000,
            "How long one chunk takes to appear, in milliseconds");
        distance = config.getInt(
            "distance",
            CATEGORY_ANIMATION,
            distance,
            4,
            64,
            "How far a chunk moves while it appears, in blocks: RISE and SLIDE");
    }
}
