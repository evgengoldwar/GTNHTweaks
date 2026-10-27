package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ScreenShake;

/** The camera shakes where vanilla shakes it when the player is hurt, so the world and the hand shake together. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_ScreenShake {

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$shake(float partialTicks, CallbackInfo ci) {
        ScreenShake.apply();
    }
}
