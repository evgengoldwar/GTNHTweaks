package gtnhtweaks.mixins.early.loadingscreen;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.loadingscreen.LoadingScreenHooks;

@Mixin(Minecraft.class)
public class MixinMinecraft_LoadingScreen {

    @Inject(method = "loadScreen", at = @At("HEAD"), cancellable = true)
    private void gtnhtweaks$drawLoadingScreen(CallbackInfo ci) {
        if (LoadingScreenHooks.onVanillaLoadScreen()) ci.cancel();
    }

    @Inject(method = "startGame", at = @At("TAIL"))
    private void gtnhtweaks$onGameStarted(CallbackInfo ci) {
        LoadingScreenHooks.onGameStarted();
    }
}
