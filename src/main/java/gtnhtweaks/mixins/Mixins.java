package gtnhtweaks.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import gtnhtweaks.modules.chromatictooltips.ChromaticTooltipsModule;
import gtnhtweaks.modules.debug.DebugModule;
import gtnhtweaks.modules.gregtech.GregTechModule;
import gtnhtweaks.modules.itemphysics.ItemPhysicsModule;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;
import gtnhtweaks.modules.logs.LogsModule;
import gtnhtweaks.modules.mainmenu.MainMenuModule;
import gtnhtweaks.modules.menus.MenusModule;
import gtnhtweaks.modules.minecraft.MinecraftModule;
import gtnhtweaks.modules.nei.NEIModule;

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
            "menus.MixinFontRenderer_Menus",
            "menus.MixinMinecraft_Menus")),

    // Minecraft module
    MINECRAFT_CHAT_HEADS(
        new MixinBuilder("Heads of the players and bars of other messages in the chat").setPhase(Phase.EARLY)
            .setApplyIf(
                () -> MinecraftModule.INSTANCE.isEnabled()
                    && (MinecraftModule.chatHeads || MinecraftModule.systemMessageBar))
            .addClientMixins(
                "minecraft.MixinChatLine_ChatHeads",
                "minecraft.MixinGuiNewChat_ChatHeads",
                "minecraft.MixinGuiChat_ChatHeads")),
    MINECRAFT_DROP_SWING(new MixinBuilder("Swing of the hand when throwing an item").setPhase(Phase.EARLY)
        .setApplyIf(() -> MinecraftModule.INSTANCE.isEnabled() && MinecraftModule.dropSwing)
        .addClientMixins("minecraft.MixinEntityPlayer_DropSwing", "minecraft.MixinEntityClientPlayerMP_DropSwing")),
    MINECRAFT_SCREEN_BACKGROUND(
        new MixinBuilder("Darkness or blur of the world behind the screens").setPhase(Phase.EARLY)
            .setApplyIf(MinecraftModule.INSTANCE::isEnabled)
            .addClientMixins("minecraft.MixinGuiScreen_Background")),
    MINECRAFT_NEW_ITEMS(new MixinBuilder("Stars on the new items of the inventory").setPhase(Phase.EARLY)
        .setApplyIf(MinecraftModule.INSTANCE::isEnabled)
        .addClientMixins("minecraft.MixinGuiContainer_NewItems")),
    MINECRAFT_HOVER_BOUNCE(new MixinBuilder("Items in slots bounce when hovered").setPhase(Phase.EARLY)
        .setApplyIf(() -> MinecraftModule.INSTANCE.isEnabled() && MinecraftModule.hoverBounce)
        .addClientMixins("minecraft.MixinGuiContainer_HoverBounce")),
    // Applied with the module, the settings are read when the screen is used, so they need no restart
    MINECRAFT_RESOURCE_PACKS(
        new MixinBuilder("Dragging and presets of the resource packs").setPhase(Phase.EARLY)
            .setApplyIf(MinecraftModule.INSTANCE::isEnabled)
            .addClientMixins("minecraft.MixinGuiScreenResourcePacks_Packs", "minecraft.MixinGuiSlot_Packs")),

    // Item physics module
    ITEM_PHYSICS(new MixinBuilder("Dropped items lie on the ground and tumble when they fall").setPhase(Phase.EARLY)
        .setApplyIf(ItemPhysicsModule.INSTANCE::isEnabled)
        .addClientMixins("itemphysics.MixinRenderItem_ItemPhysics", "itemphysics.MixinEntity_ItemPhysics")),

    // GregTech module
    GREGTECH_HIDE_CABLE_INSULATION(
        new MixinBuilder("Draw placed insulated GregTech cables without insulation").setPhase(Phase.LATE)
            .setApplyIf(() -> GregTechModule.INSTANCE.isEnabled() && GregTechModule.hideCableInsulation)
            .addRequiredMod(TargetMods.GREGTECH)
            .addClientMixins("gregtech.MixinMTECable_GregTech")),
    GREGTECH_MODERN_TOOL_OVERLAY(new MixinBuilder("Tool overlay of GregTech CEu Modern").setPhase(Phase.LATE)
        .setApplyIf(() -> GregTechModule.INSTANCE.isEnabled() && GregTechModule.modernToolOverlay)
        .addRequiredMod(TargetMods.GREGTECH)
        .addClientMixins("gregtech.MixinBlockOverlayRenderer_GregTech")),

    // NEI module
    NEI_CRAFTING_TREE_CAMERA(new MixinBuilder("Smooth movement in the NEI crafting tree").setPhase(Phase.LATE)
        .setApplyIf(() -> NEIModule.INSTANCE.isEnabled() && (NEIModule.preciseDrag || NEIModule.smoothScroll))
        .addRequiredMod(TargetMods.NEI)
        .addClientMixins("nei.MixinCamera_NEI")),
    NEI_HOVER_BOUNCE(new MixinBuilder("Items of the NEI panels bounce when hovered").setPhase(Phase.LATE)
        .setApplyIf(() -> MinecraftModule.INSTANCE.isEnabled() && MinecraftModule.hoverBounce)
        .addRequiredMod(TargetMods.NEI)
        .addClientMixins(
            "nei.MixinItemsGrid_HoverBounce",
            "nei.MixinItemsGridSlot_HoverBounce",
            "nei.MixinScreenCapture_HoverBounce")),
    NEI_FILL_ONE_CRAFT(
        new MixinBuilder("Ctrl + Shift + click on the recipe + fills the grid for one craft").setPhase(Phase.LATE)
            .setApplyIf(() -> NEIModule.INSTANCE.isEnabled() && NEIModule.fillOneCraftWithCtrl)
            .addRequiredMod(TargetMods.NEI)
            .addClientMixins("nei.MixinGuiOverlayButton_NEI")),

    // Chromatic Tooltips module
    CHROMATIC_TOOLTIPS_FIT_TEXT_WIDTH(
        new MixinBuilder("Chromatic Tooltips frames as wide as the drawn text").setPhase(Phase.LATE)
            .setApplyIf(() -> ChromaticTooltipsModule.INSTANCE.isEnabled() && ChromaticTooltipsModule.fitTextWidth)
            .addRequiredMod(TargetMods.CHROMATIC_TOOLTIPS)
            .addClientMixins("chromatictooltips.MixinTooltipFontContext_ChromaticTooltips")),
    CHROMATIC_TOOLTIPS_COLOR_TEXT_TOOLTIPS(
        new MixinBuilder("Chromatic Spectrum colors of the hovered fluid in ModularUI tooltips").setPhase(Phase.LATE)
            .setApplyIf(() -> ChromaticTooltipsModule.INSTANCE.isEnabled() && ChromaticTooltipsModule.colorTextTooltips)
            .addRequiredMod(TargetMods.CHROMATIC_TOOLTIPS)
            .addRequiredMod(TargetMods.CHROMATIC_SPECTRUM)
            .addClientMixins(
                "chromatictooltips.MixinTooltipHandler_ChromaticTooltips",
                "chromatictooltips.MixinTooltipContext_ChromaticTooltips",
                "chromatictooltips.MixinTitleEnricher_ChromaticTooltips",
                "chromatictooltips.MixinContextInfoEnricher_ChromaticTooltips",
                "chromatictooltips.AccessorTooltipLines_ChromaticTooltips")),
    CHROMATIC_TOOLTIPS_COLOR_QUEST_TOOLTIPS(
        new MixinBuilder("Chromatic Spectrum colors of the quest icon in BetterQuesting quest tooltips")
            .setPhase(Phase.LATE)
            .setApplyIf(() -> ChromaticTooltipsModule.INSTANCE.isEnabled() && ChromaticTooltipsModule.colorTextTooltips)
            .addRequiredMod(TargetMods.CHROMATIC_TOOLTIPS)
            .addRequiredMod(TargetMods.CHROMATIC_SPECTRUM)
            .addRequiredMod(TargetMods.BETTER_QUESTING)
            .addClientMixins("chromatictooltips.MixinPanelButtonQuest_ChromaticTooltips")),

    // Debug module
    DEBUG_CRASH_TEST_MIXIN(
        new MixinBuilder("Mixin that cannot be applied, to test the crash screen").setPhase(Phase.EARLY)
            .setApplyIf(() -> DebugModule.INSTANCE.isEnabled() && DebugModule.crashTestEnabled())
            .addClientMixins("debug.MixinCrashTestTarget", "debug.MixinMinecraft_CrashTest")),

    // Logs module
    LOGS_CRASH_REPORT(new MixinBuilder("Copy crash reports to the GTNHTweaks logs").setPhase(Phase.EARLY)
        .setApplyIf(LogsModule.INSTANCE::isEnabled)
        .addCommonMixins("logs.MixinCrashReport_Logs")),
    LOGS_CRASH_SCREEN(new MixinBuilder("Crash screen in place of other crash screens").setPhase(Phase.EARLY)
        .setApplyIf(() -> LogsModule.INSTANCE.isEnabled() && LogsModule.replaceCrashScreen)
        .addClientMixins("logs.MixinMinecraft_CrashScreen")),
    LOGS_CRASH_HANDLER(new MixinBuilder("Keep the game open after a crash").setPhase(Phase.EARLY)
        .setApplyIf(
            () -> LogsModule.INSTANCE.isEnabled() && LogsModule.replaceCrashScreen
                && !LogsModule.betterCrashesInstalled())
        .addClientMixins("logs.MixinMinecraft_CrashHandler")),

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
