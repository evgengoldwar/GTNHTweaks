package gtnhtweaks.mixins.early.loadingscreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.loadingscreen.LoadingScreenHooks;

/**
 * Draws over every frame of BetterLoadingScreen. Its frame method is the same in threaded and main thread rendering;
 * older versions have no sub-progress, so both signatures are handled.
 */
@Pseudo
@Mixin(targets = "alexiil.mods.load.MinecraftDisplayer", remap = false)
public class MixinBetterLoadingScreen_LoadingScreen {

    @Inject(
        method = "displayProgressInWorkerThread(Ljava/lang/String;FLjava/lang/String;F)V",
        at = @At("RETURN"),
        require = 0)
    private void gtnhtweaks$drawLoadingScreen(String text, float percent, String subText, float subPercent,
        CallbackInfo ci) {
        LoadingScreenHooks.onBetterLoadingScreenFrame(text, percent, subText, subPercent);
    }

    @Inject(method = "displayProgressInWorkerThread(Ljava/lang/String;F)V", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$drawLoadingScreenLegacy(String text, float percent, CallbackInfo ci) {
        LoadingScreenHooks.onBetterLoadingScreenFrame(text, percent, null, Float.NaN);
    }
}
