package gtnhtweaks.modules.chromatictooltips;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.core.TweakModule;

/**
 * Fixes of Chromatic Tooltips, its Compat and the Chromatic Spectrum addon. Does nothing when Chromatic Tooltips is not
 * installed.
 */
public final class ChromaticTooltipsModule extends TweakModule {

    public static final ChromaticTooltipsModule INSTANCE = new ChromaticTooltipsModule();

    private static final String CATEGORY_FIXES = "fixes";

    public static boolean colorTextTooltips = true;
    public static boolean fitTextWidth = true;

    private ChromaticTooltipsModule() {
        super("chromatictooltips", "Fixes of Chromatic Tooltips and its Chromatic Spectrum addon", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_FIXES, "Every fix needs a restart");
        colorTextTooltips = config.getBoolean(
            "colorTextTooltips",
            CATEGORY_FIXES,
            colorTextTooltips,
            "Chromatic Spectrum colors tooltips that have only text, too: of the fluid in a tank of GregTech and other "
                + "ModularUI windows, by the icon of the quest in BetterQuesting. Without it they stay gray");
        fitTextWidth = config.getBoolean(
            "fitTextWidth",
            CATEGORY_FIXES,
            fitTextWidth,
            "Widen the tooltip frame to the text as it is drawn. Some text draws wider than Chromatic Tooltips "
                + "measures it, like quest tooltips of BetterQuesting, and went past the frame");
    }
}
