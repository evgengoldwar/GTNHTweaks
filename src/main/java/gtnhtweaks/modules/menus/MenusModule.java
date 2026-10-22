package gtnhtweaks.modules.menus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.TweakModule;

/**
 * Restyles every vanilla, Forge and FML screen like the GTNH loading screen: themed background and lists, flat buttons,
 * sliders and text fields, theme text colors. In game the world stays behind the menus under the vanilla dark
 * gradient, and vanilla inventories get a dark tint in the DARK and SHADOW themes.
 */
public final class MenusModule extends TweakModule {

    public static final MenusModule INSTANCE = new MenusModule();

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_SCREENS = "screens";

    private static final String[] DEFAULT_EXCLUDED = { //
        "net.minecraft.client.gui.GuiChat", //
        "net.minecraft.client.gui.inventory.GuiContainerCreative", //
    };

    public static GTNHTheme style = GTNHTheme.SHADOW;
    public static boolean recolorText = true;
    public static boolean respectResourcePacks = false;
    public static boolean themeVanillaScreens = true;
    public static boolean themeInGame = true;
    public static boolean fullFramerate = true;
    public static boolean themeInventories = true;
    public static Set<String> extraScreens = Collections.emptySet();
    public static Set<String> excludedScreens = Collections.emptySet();

    private MenusModule() {
        super("menus", "Vanilla, Forge and in-game menus and inventories in the GTNH style", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look of the menu screens");
        style = readTheme(config, CATEGORY_GENERAL, "the menus");
        recolorText = config.getBoolean(
            "recolorText",
            CATEGORY_GENERAL,
            recolorText,
            "Replace the white and gray text of the screens with the theme colors. Needed for the LIGHT style");
        // replaces respectResourcePacks, which was on by default and hid the theme behind every GUI resource pack
        removeKey(config, CATEGORY_GENERAL, "respectResourcePacks");
        respectResourcePacks = config.getBoolean(
            "yieldToResourcePacks",
            CATEGORY_GENERAL,
            false,
            "If an enabled resource pack replaces a vanilla GUI texture, show the resource pack instead of the theme:\n"
                + "options_background.png - backgrounds and lists, widgets.png - buttons and sliders.\n"
                + "false: the theme is always drawn. Inventory textures replaced by a pack are never tinted");

        fullFramerate = config.getBoolean(
            "fullFramerate",
            CATEGORY_GENERAL,
            fullFramerate,
            "Use the frame rate limit of the video settings in the main menu and the other screens opened outside of "
                + "a world, instead of the 30 FPS of vanilla");

        config.addCustomCategoryComment(CATEGORY_SCREENS, "Which screens are restyled. Changes require a restart");
        themeVanillaScreens = config.getBoolean(
            "themeVanillaScreens",
            CATEGORY_SCREENS,
            themeVanillaScreens,
            "Restyle every Minecraft, Forge and FML screen and the screens of mods that extend them "
                + "(e.g. mod config screens)");
        themeInGame = config.getBoolean(
            "themeInGame",
            CATEGORY_SCREENS,
            themeInGame,
            "Also restyle the menus opened in game (pause menu, options...). The world stays visible behind them");
        themeInventories = config.getBoolean(
            "themeInventories",
            CATEGORY_SCREENS,
            themeInventories,
            "Dark tint of the vanilla inventory textures in the DARK and SHADOW themes. Mod inventories keep their "
                + "own textures");
        extraScreens = trimmed(
            config.getStringList(
                "extraScreens",
                CATEGORY_SCREENS,
                new String[0],
                "Full class names of other mod screens to restyle. Subclasses are restyled too"));
        excludedScreens = trimmed(
            config.getStringList(
                "excludedScreens",
                CATEGORY_SCREENS,
                DEFAULT_EXCLUDED,
                "Full class names of screens that keep the vanilla look. Subclasses are excluded too"));
    }

    private static Set<String> trimmed(String[] names) {
        Set<String> result = new LinkedHashSet<>();
        for (String name : names) {
            if (!name.trim()
                .isEmpty()) result.add(name.trim());
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new MenuStyle.ScreenDrawTracker());
    }
}
