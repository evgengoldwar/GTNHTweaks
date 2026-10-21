package gtnhtweaks.mixins.early.loadingscreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.loadingscreen.LoadingScreenHooks;

/** The render loop of the Forge splash screen: the anonymous Runnable in SplashProgress.start(). */
@Pseudo
@Mixin(targets = "cpw.mods.fml.client.SplashProgress$3", remap = false)
public class MixinSplashProgressThread_LoadingScreen {

    @Inject(
        method = "run",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V"),
        require = 0)
    private void gtnhtweaks$drawLoadingScreen(CallbackInfo ci) {
        LoadingScreenHooks.onForgeSplashFrame();
    }
}
