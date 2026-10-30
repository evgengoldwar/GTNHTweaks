package gtnhtweaks.modules.buttons;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.TweakModule;

/** Buttons GTNHTweaks adds to the screens of Minecraft. Every button has its own switch. */
public final class ButtonsModule extends TweakModule {

    public static final ButtonsModule INSTANCE = new ButtonsModule();

    private static final String CATEGORY_MAIN_MENU = "mainMenu";

    public static boolean settingsButton = true;

    private ButtonsModule() {
        super("buttons", "Buttons GTNHTweaks adds to the screens of Minecraft", ModuleSide.CLIENT);
    }

    /** On by default, so the settings of GTNHTweaks can be opened from the main menu. */
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void loadConfig(Configuration config) {
        config.addCustomCategoryComment(CATEGORY_MAIN_MENU, "Buttons of the main menu");
        settingsButton = config.getBoolean(
            "settingsButton",
            CATEGORY_MAIN_MENU,
            settingsButton,
            "Show the GTNHTweaks button beside Mods, which opens the settings of GTNHTweaks and makes Mods narrower: "
                + "the two share the row of Mods");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        SettingsButton.register();
    }
}
