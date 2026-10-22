package gtnhtweaks.modules.loadingscreen;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;

/**
 * Replaces the Minecraft loading screen (vanilla Mojang screen, Forge splash and BetterLoadingScreen) with a GTNH
 * screen that shows the loading progress, the current loading time and the time of the previous launch.
 */
public final class LoadingScreenModule extends TweakModule {

    public static final LoadingScreenModule INSTANCE = new LoadingScreenModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_INTEGRATION = "integration";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean showTimer = true;
    public static boolean showPreviousLaunch = true;
    public static boolean showMemory = true;
    public static boolean showLoadTimeInMainMenu = true;

    public static boolean replaceVanillaScreen = true;
    public static boolean replaceForgeSplash = true;
    public static boolean drawOverBetterLoadingScreen = true;

    public static String subtitle = "GT: New Horizons";
    public static String textLoadingTime = "Loading time";
    public static String textPreviousLaunch = "Previous launch";
    public static String textFirstLaunch = "No previous launch recorded";
    public static String textMemory = "Memory";
    public static String textLoadedIn = "Loaded in";
    public static String unitHours = "h";
    public static String unitMinutes = "min";
    public static String unitSeconds = "s";

    private LoadingScreenModule() {
        super("loadingscreen", "GTNH loading screen with a loading timer and the previous launch time", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look of the loading screen");
        style = readTheme(config, CATEGORY_GENERAL, "the loading screen");
        showTimer = config.getBoolean("showTimer", CATEGORY_GENERAL, showTimer, "Show the loading timer");
        showPreviousLaunch = config.getBoolean(
            "showPreviousLaunch",
            CATEGORY_GENERAL,
            showPreviousLaunch,
            "Show how long the previous launch took and how far along the current one is");
        showMemory = config.getBoolean("showMemory", CATEGORY_GENERAL, showMemory, "Show the memory usage");
        showLoadTimeInMainMenu = config.getBoolean(
            "showLoadTimeInMainMenu",
            CATEGORY_GENERAL,
            showLoadTimeInMainMenu,
            "Show the total loading time in the top left corner of the main menu");

        config.addCustomCategoryComment(CATEGORY_INTEGRATION, "Which loading screens are replaced");
        replaceVanillaScreen = config.getBoolean(
            "replaceVanillaScreen",
            CATEGORY_INTEGRATION,
            replaceVanillaScreen,
            "Replace the Mojang logo screen shown before mods start loading");
        replaceForgeSplash = config.getBoolean(
            "replaceForgeSplash",
            CATEGORY_INTEGRATION,
            replaceForgeSplash,
            "Draw over the Forge splash screen (config/splash.properties, enabled=true)");
        drawOverBetterLoadingScreen = config.getBoolean(
            "drawOverBetterLoadingScreen",
            CATEGORY_INTEGRATION,
            drawOverBetterLoadingScreen,
            "Draw over BetterLoadingScreen when it is installed. BetterLoadingScreen keeps tracking the progress, "
                + "this screen shows it");

        config.addCustomCategoryComment(
            CATEGORY_TEXT,
            "Texts of the loading screen. Latin and Cyrillic characters are supported");
        subtitle = config.getString("subtitle", CATEGORY_TEXT, subtitle, "Line under the GTNH logo, empty to hide");
        textLoadingTime = config.getString("loadingTime", CATEGORY_TEXT, textLoadingTime, "Timer caption");
        textPreviousLaunch = config
            .getString("previousLaunch", CATEGORY_TEXT, textPreviousLaunch, "Caption of the previous launch time");
        textFirstLaunch = config.getString(
            "firstLaunch",
            CATEGORY_TEXT,
            textFirstLaunch,
            "Shown instead of the previous launch time when there is none");
        textMemory = config.getString("memory", CATEGORY_TEXT, textMemory, "Caption of the memory usage");
        textLoadedIn = config.getString("loadedIn", CATEGORY_TEXT, textLoadedIn, "Caption used in the main menu");
        unitHours = config.getString("unitHours", CATEGORY_TEXT, unitHours, "Hours unit of the main menu load time");
        unitMinutes = config
            .getString("unitMinutes", CATEGORY_TEXT, unitMinutes, "Minutes unit of the main menu load time");
        unitSeconds = config
            .getString("unitSeconds", CATEGORY_TEXT, unitSeconds, "Seconds unit of the main menu load time");

        if (isEnabled()) {
            LoadingTimer.init(new File(ModuleManager.getConfigDir(), "loadingscreen-stats.properties"));
        }
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (showLoadTimeInMainMenu) {
            MinecraftForge.EVENT_BUS.register(new MainMenuLoadTimeOverlay());
        }
    }
}
