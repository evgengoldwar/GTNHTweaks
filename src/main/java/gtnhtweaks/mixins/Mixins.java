package gtnhtweaks.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import gtnhtweaks.modules.gregtech.GregTechModule;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.logs.LogsModule;
import gtnhtweaks.modules.mainmenu.MainMenuModule;
import gtnhtweaks.modules.menus.MenusModule;

public enum Mixins implements IMixins {

    // Loading screen module
    LOADING_SCREEN(new MixinBuilder("GTNH loading screen").setPhase(Phase.EARLY)
        .setApplyIf(LoadingScreenModule.INSTANCE::isEnabled)
        .addClientMixins(
            "loadingscreen.MixinMinecraft_LoadingScreen",
            "loadingscreen.MixinSplashProgressThread_LoadingScreen")),
    // Not tied to BetterLoadingScreen being detected: the coremod list can be incomplete when early mixins are
    // collected, and the mixin is @Pseudo with optional injections, so it does nothing without the mod
    LOADING_SCREEN_BETTER_LOADING_SCREEN(
        new MixinBuilder("GTNH loading screen over BetterLoadingScreen").setPhase(Phase.EARLY)
            .setApplyIf(LoadingScreenModule.INSTANCE::isEnabled)
            .addClientMixins("loadingscreen.MixinBetterLoadingScreen_LoadingScreen")),

    // Main menu module
    MAIN_MENU(new MixinBuilder("GTNH main menu").setPhase(Phase.EARLY)
        .setApplyIf(MainMenuModule.INSTANCE::isEnabled)
        .addClientMixins("mainmenu.MixinGuiMainMenu_MainMenu")),
    MAIN_MENU_CUSTOM_MAIN_MENU(new MixinBuilder("GTNH main menu instead of Custom Main Menu").setPhase(Phase.LATE)
        .setApplyIf(() -> MainMenuModule.INSTANCE.isEnabled() && MainMenuModule.replaceCustomMainMenu)
        .addRequiredMod(TargetMods.CUSTOM_MAIN_MENU)
        .addClientMixins("custommainmenu.MixinCMMEventHandler_MainMenu")),

    // Menus module
    MENUS(new MixinBuilder("GTNH style menus").setPhase(Phase.EARLY)
        .setApplyIf(MenusModule.INSTANCE::isEnabled)
        .addClientMixins(
            "menus.MixinGuiScreen_Menus",
            "menus.MixinGuiButton_Menus",
            "menus.MixinGuiButtonExt_Menus",
            "menus.MixinGui_Menus",
            "menus.MixinGuiTextField_Menus",
            "menus.MixinGuiContainer_Menus",
            "menus.MixinTextureManager_Menus",
            "menus.MixinGuiSlot_Menus",
            "menus.MixinGuiScrollingList_Menus",
            "menus.MixinFontRenderer_Menus")),

    // GregTech module
    GREGTECH_HIDE_CABLE_INSULATION(
        new MixinBuilder("Draw placed insulated GregTech cables without insulation").setPhase(Phase.LATE)
            .setApplyIf(() -> GregTechModule.INSTANCE.isEnabled() && GregTechModule.hideCableInsulation)
            .addRequiredMod(TargetMods.GREGTECH)
            .addClientMixins("gregtech.MixinMTECable_GregTech")),

    // Logs module
    LOGS_CRASH_REPORT(new MixinBuilder("Copy crash reports to the GTNHTweaks logs").setPhase(Phase.EARLY)
        .setApplyIf(LogsModule.INSTANCE::isEnabled)
        .addCommonMixins("logs.MixinCrashReport_Logs")),

    // Shared by the modules that draw GTNH style buttons
    GUI_BUTTON_ACCESSOR(new MixinBuilder("GuiButton size accessor").setPhase(Phase.EARLY)
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
