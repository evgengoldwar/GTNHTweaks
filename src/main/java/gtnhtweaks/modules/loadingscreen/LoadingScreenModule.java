package gtnhtweaks.modules.loadingscreen;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.FMLCommonHandler;
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
    private static final String CATEGORY_WALL = "wall";
    private static final String CATEGORY_INTEGRATION = "integration";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean showTimer = true;
    public static boolean showPreviousLaunch = true;
    public static boolean showMemory = true;
    public static boolean showLoadTimeInMainMenu = true;

    public static boolean wall = true;
    public static boolean wallModBlocks = true;
    public static float wallModBlockShare = 0.6F;
    public static int wallRows = 9;
    public static float wallVeil = 0.7F;
    public static boolean wallCollapse = true;
    public static float wallCollapseSpeed = 1F;
    public static boolean wallCollapseSound = true;

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
        super(
            "loadingscreen",
            "GTNH loading screen with a loading timer and the previous launch time",
            ModuleSide.CLIENT);
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
            "Show the total loading time in the top left corner of the vanilla main menu. The GTNH main menu "
                + "shows it itself, see showLoadTime of the mainmenu module. Needs a restart");

        config.addCustomCategoryComment(
            CATEGORY_WALL,
            "A wall of vanilla and mod blocks that is built behind the loading screen while the game loads, "
                + "then crumbles over the main menu. The wall settings apply from the next launch, "
                + "the crumbling ones at once");
        for (String old : new String[] { "enabled", "veil", "collapse", "collapseSound" }) {
            removeKey(config, CATEGORY_WALL, old);
        }
        wall = config
            .getBoolean("buildWall", CATEGORY_WALL, wall, "Build the wall of blocks behind the loading screen");
        wallModBlocks = config.getBoolean(
            "modBlocks",
            CATEGORY_WALL,
            wallModBlocks,
            "Mix in block textures of the mods in the mods folder, not only vanilla blocks");
        wallModBlockShare = config.getInt(
            "modBlockShare",
            CATEGORY_WALL,
            Math.round(wallModBlockShare * 100),
            0,
            100,
            "Share of mod blocks in the wall, in percent; the rest are vanilla blocks") / 100F;
        wallRows = config.getInt("rows", CATEGORY_WALL, wallRows, 4, 30, "Rows of blocks that fill the screen height");
        wallVeil = config.getInt(
            "textVeil",
            CATEGORY_WALL,
            Math.round(wallVeil * 100),
            0,
            100,
            "How much the background color covers the wall while loading, in percent, so the text stays readable")
            / 100F;
        wallCollapse = config.getBoolean(
            "crumbleAtEnd",
            CATEGORY_WALL,
            wallCollapse,
            "When the game has loaded, fade the text out and let the wall crumble down, uncovering the main menu. "
                + "Off: the loading screen just ends as before");
        wallCollapseSpeed = config.getInt(
            "crumbleSpeed",
            CATEGORY_WALL,
            Math.round(wallCollapseSpeed * 100),
            25,
            400,
            "Speed of the end animation, in percent: 200 is twice as fast") / 100F;
        wallCollapseSound = config
            .getBoolean("crumbleSound", CATEGORY_WALL, wallCollapseSound, "Play a sound when the wall crumbles");

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
        if (wall) {
            // registered even with crumbling off, so it can be switched on and off in game
            FMLCommonHandler.instance()
                .bus()
                .register(new WallCollapse());
        }
    }
}
