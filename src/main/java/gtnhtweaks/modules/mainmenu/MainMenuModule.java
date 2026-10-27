package gtnhtweaks.modules.mainmenu;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
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
    private static final String CATEGORY_CIRCUIT = "circuit";
    private static final String CATEGORY_TEXT = "text";

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean showSplashText = true;
    public static boolean showLoadTime = true;
    public static boolean showModCount = true;
    public static boolean showSettingsButton = true;
    public static boolean respectResourcePacks = false;
    public static boolean replaceCustomMainMenu = true;

    public static boolean circuit = true;
    public static boolean circuitTierColors = true;
    public static int circuitRows = 18;
    public static float circuitPulseSpeed = 10;
    public static float circuitBrightness = 0.65F;
    public static boolean circuitChips = true;
    public static boolean circuitParallax = true;

    public static String subtitle = "GT: New Horizons";
    public static String textMods = "Mods";
    public static String textSettings = "GTNHTweaks settings";

    private MainMenuModule() {
        super("mainmenu", "Main menu in the style of the GTNH loading screen", ModuleSide.CLIENT);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look of the main menu");
        style = readTheme(config, CATEGORY_GENERAL, "the main menu");
        showSplashText = config
            .getBoolean("showSplashText", CATEGORY_GENERAL, showSplashText, "Show the yellow vanilla splash text");
        showLoadTime = config.getBoolean(
            "showLoadTime",
            CATEGORY_GENERAL,
            showLoadTime,
            "Show how long the game took to load. Needs the loadingscreen module");
        showModCount = config
            .getBoolean("showModCount", CATEGORY_GENERAL, showModCount, "Show the number of loaded mods");
        showSettingsButton = config.getBoolean(
            "showSettingsButton",
            CATEGORY_GENERAL,
            showSettingsButton,
            "Show a button in the bottom left corner that opens the GTNHTweaks settings");
        // replaces respectResourcePacks, which was on by default and hid the menu behind every GUI resource pack
        removeKey(config, CATEGORY_GENERAL, "respectResourcePacks");
        respectResourcePacks = config.getBoolean(
            "yieldToResourcePacks",
            CATEGORY_GENERAL,
            false,
            "If an enabled resource pack replaces the Minecraft logo (title/minecraft.png), the panorama or the "
                + "buttons (widgets.png), show the vanilla main menu with the resource pack instead of the GTNH one.\n"
                + "false: the GTNH main menu is always shown");
        replaceCustomMainMenu = config.getBoolean(
            "replaceCustomMainMenu",
            CATEGORY_GENERAL,
            replaceCustomMainMenu,
            "Show the GTNH main menu instead of the one of the Custom Main Menu mod, the default menu of the GTNH "
                + "pack.\nfalse: Custom Main Menu keeps its menu");

        config.addCustomCategoryComment(
            CATEGORY_CIRCUIT,
            "Animated circuit board behind the main menu: copper traces and chips with pulses of current running "
                + "along them");
        circuit = config.getBoolean("showCircuit", CATEGORY_CIRCUIT, circuit, "Show the circuit board background");
        circuitTierColors = "TIERS".equals(
            config.getString(
                "currentColors",
                CATEGORY_CIRCUIT,
                "TIERS",
                "Colors of the pulses: TIERS (the colors of the GregTech voltage tiers, LV to UEV) or THEME (the "
                    + "accent color of the theme)",
                new String[] { "TIERS", "THEME" }));
        circuitRows = config.getInt(
            "rows",
            CATEGORY_CIRCUIT,
            circuitRows,
            8,
            40,
            "Size of the board: how many cells fit the screen height. More rows, finer and denser traces");
        circuitPulseSpeed = config.getInt(
            "pulseSpeed",
            CATEGORY_CIRCUIT,
            Math.round(circuitPulseSpeed),
            1,
            30,
            "Speed of the pulses, in cells per second");
        circuitBrightness = config.getInt(
            "brightness",
            CATEGORY_CIRCUIT,
            Math.round(circuitBrightness * 100),
            20,
            100,
            "Brightness of the traces and the pulses, in percent") / 100F;
        circuitChips = config.getBoolean("chips", CATEGORY_CIRCUIT, circuitChips, "Place chips on the board");
        circuitParallax = config.getBoolean(
            "parallax",
            CATEGORY_CIRCUIT,
            circuitParallax,
            "Shift the board a little after the mouse, for a feeling of depth");

        config.addCustomCategoryComment(CATEGORY_TEXT, "Texts of the main menu. Latin and Cyrillic are supported");
        subtitle = config.getString("subtitle", CATEGORY_TEXT, subtitle, "Line under the GTNH logo, empty to hide");
        textMods = config.getString("mods", CATEGORY_TEXT, textMods, "Caption of the number of loaded mods");
        textSettings = config.getString("settings", CATEGORY_TEXT, textSettings, "Label of the settings button");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new MainMenuLayout());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // registered after every pre-initialization, so it runs after the handler of Custom Main Menu
        if (Loader.isModLoaded(CustomMainMenuReplacer.MOD_ID)) {
            MinecraftForge.EVENT_BUS.register(new CustomMainMenuReplacer());
        }
    }
}
