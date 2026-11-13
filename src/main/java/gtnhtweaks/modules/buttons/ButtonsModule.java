package gtnhtweaks.modules.buttons;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gtnhtweaks.core.ModuleManager;
import gtnhtweaks.core.TweakModule;
import gtnhtweaks.modules.buttons.mods.PendingFiles;

/** Buttons GTNHTweaks adds to the screens of Minecraft. Every button has its own switch. */
public final class ButtonsModule extends TweakModule {

    public static final ButtonsModule INSTANCE = new ButtonsModule();

    private static final String CATEGORY_MAIN_MENU = "mainMenu";

    public static boolean settingsButton = true;
    public static boolean languageButton = true;
    public static boolean modsButton = true;

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
        languageButton = config.getBoolean(
            "languageButton",
            CATEGORY_MAIN_MENU,
            languageButton,
            "Show the Language button, the small square with a globe beside Options. The language can still be "
                + "changed in Options");
        modsButton = config.getBoolean(
            "modsButton",
            CATEGORY_MAIN_MENU,
            modsButton,
            "Show the small square with an arrow right of Options, which opens the mods of EvgenWarGold: install, "
                + "update or delete them from their releases on GitHub");
    }

    /** Changes to the mods folder left by the last game, before the mods in it are read. */
    @Override
    public void coremodInit() {
        PendingFiles.applyAll(PendingFiles.queue(ModuleManager.getConfigDir()));
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        SettingsButton.register();
        LanguageButton.register();
        ModsButton.register();
    }
}
