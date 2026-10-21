package gtnhtweaks.modules.menus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

import gtnhtweaks.common.render.GTNHTheme;
import gtnhtweaks.core.TweakModule;

/**
 * Restyles the screens opened from the main menu (singleplayer, multiplayer, mod list, mod settings, options,
 * language) like the GTNH loading screen: themed background and lists, flat buttons and theme text colors.
 */
public final class MenusModule extends TweakModule {

    public static final MenusModule INSTANCE = new MenusModule();

    private static final String CATEGORY_GENERAL = "general";

    private static final String[] DEFAULT_SCREENS = { //
        "net.minecraft.client.gui.GuiSelectWorld", //
        "net.minecraft.client.gui.GuiMultiplayer", //
        "cpw.mods.fml.client.GuiModList", //
        "cpw.mods.fml.client.config.GuiConfig", //
        "net.minecraft.client.gui.GuiOptions", //
        "net.minecraft.client.gui.GuiLanguage", //
    };

    public static GTNHTheme style = GTNHTheme.DARK;
    public static boolean recolorText = true;
    public static Set<String> screens = Collections.emptySet();

    private MenusModule() {
        super("menus", "Singleplayer, multiplayer, mod list, options and language screens in the GTNH style", true);
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_GENERAL, "Look of the menu screens");
        style = GTNHTheme.parse(
            config.getString(
                "style",
                CATEGORY_GENERAL,
                style.name(),
                "Color style of the menus: DARK or LIGHT",
                GTNHTheme.names()));
        recolorText = config.getBoolean(
            "recolorText",
            CATEGORY_GENERAL,
            recolorText,
            "Replace the white and gray text of the screens with the theme colors. Needed for the LIGHT style");
        String[] names = config.getStringList(
            "screens",
            CATEGORY_GENERAL,
            DEFAULT_SCREENS,
            "Full class names of the screens to restyle. Subclasses are restyled too. Screens are only restyled "
                + "outside of a world, the in-game options keep the vanilla look.\n"
                + "More examples: net.minecraft.client.gui.GuiVideoSettings, net.minecraft.client.gui.GuiControls, "
                + "net.minecraft.client.gui.GuiCreateWorld, net.minecraft.client.gui.GuiScreenAddServer");
        Set<String> trimmed = new LinkedHashSet<>();
        for (String name : names) {
            if (!name.trim()
                .isEmpty()) trimmed.add(name.trim());
        }
        screens = Collections.unmodifiableSet(trimmed);
    }
}
