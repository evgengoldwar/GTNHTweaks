package gtnhtweaks.modules.mainmenu;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.TweakModule;

/**
 * Restyles the main menu like the GTNH loading screen: same background, GTNH logo and flat themed buttons. The vanilla
 * menu and its buttons stay in place, so every button keeps working and buttons added by other mods are kept.
 */
public final class MainMenuModule extends TweakModule {

    public static final MainMenuModule INSTANCE = new MainMenuModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.DARK;
    public static boolean showSplashText = true;
    public static boolean showLoadTime = true;
    public static boolean showBranding = true;

    public static String subtitle = "GT: New Horizons";

    private MainMenuModule() {
        super("mainmenu", "Main menu in the style of the GTNH loading screen", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look of the main menu");
        style = GTNHTheme.parse(
            config.getString(
                "style",
                CATEGORY_GENERAL,
                style.name(),
                "Color style of the main menu: DARK or LIGHT",
                GTNHTheme.names()));
        showSplashText = config
            .getBoolean("showSplashText", CATEGORY_GENERAL, showSplashText, "Show the yellow vanilla splash text");
        showLoadTime = config.getBoolean(
            "showLoadTime",
            CATEGORY_GENERAL,
            showLoadTime,
            "Show how long the game took to load. Needs the loadingscreen module");
        showBranding = config.getBoolean(
            "showBranding",
            CATEGORY_GENERAL,
            showBranding,
            "Show the Minecraft, Forge and mod count lines in the bottom left corner");

        config.addCustomCategoryComment(CATEGORY_TEXT, "Texts of the main menu. Latin and Cyrillic are supported");
        subtitle = config.getString("subtitle", CATEGORY_TEXT, subtitle, "Line under the GTNH logo, empty to hide");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new MainMenuLayout());
    }
}
