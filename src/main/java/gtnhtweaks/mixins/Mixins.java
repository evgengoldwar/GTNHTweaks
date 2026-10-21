package gtnhtweaks.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import gtnhtweaks.modules.loadingscreen.LoadingScreenModule;

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
        .addClientMixins("loadingscreen.MixinBetterLoadingScreen_LoadingScreen"));

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
