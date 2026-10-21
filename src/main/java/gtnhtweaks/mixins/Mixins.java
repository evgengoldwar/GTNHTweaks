package gtnhtweaks.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.mainmenu.MainMenuModule;
import gtnhtweaks.modules.menus.MenusModule;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {

    // Loading screen module
    LOADING_SCREEN(new MixinBuilder("GTNH loading screen")
        .setPhase(Phase.EARLY)
        .setApplyIf(LoadingScreenModule.INSTANCE::isEnabled)
        .addClientMixins(
            "loadingscreen.MixinMinecraft_LoadingScreen",
            "loadingscreen.MixinSplashProgressThread_LoadingScreen")),
    LOADING_SCREEN_BETTER_LOADING_SCREEN(new MixinBuilder("GTNH loading screen over BetterLoadingScreen")
        .setPhase(Phase.EARLY)
        .setApplyIf(LoadingScreenModule.INSTANCE::isEnabled)
        .addRequiredMod(TargetMods.BETTER_LOADING_SCREEN)
        .addClientMixins("loadingscreen.MixinBetterLoadingScreen_LoadingScreen")),

    // Main menu module
    MAIN_MENU(new MixinBuilder("GTNH main menu")
        .setPhase(Phase.EARLY)
        .setApplyIf(MainMenuModule.INSTANCE::isEnabled)
        .addClientMixins("mainmenu.MixinGuiMainMenu_MainMenu")),

    // Menus module
    MENUS(new MixinBuilder("GTNH style menus")
        .setPhase(Phase.EARLY)
        .setApplyIf(MenusModule.INSTANCE::isEnabled)
        .addClientMixins(
            "menus.MixinGuiScreen_Menus",
            "menus.MixinGuiUtils_Menus",
            "menus.MixinGuiButton_Menus",
            "menus.MixinGuiButtonExt_Menus",
            "menus.MixinGuiSlot_Menus",
            "menus.MixinGuiScrollingList_Menus",
            "menus.MixinFontRenderer_Menus")),

    // Shared by the modules that draw GTNH style buttons
    GUI_BUTTON_ACCESSOR(new MixinBuilder("GuiButton size accessor")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> MainMenuModule.INSTANCE.isEnabled() || MenusModule.INSTANCE.isEnabled())
        .addClientMixins("common.AccessorGuiButton"));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
