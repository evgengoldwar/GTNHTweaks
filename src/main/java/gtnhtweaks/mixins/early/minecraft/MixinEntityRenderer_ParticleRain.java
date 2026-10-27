package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.ParticleRain;

/** The streaks of rain and snow of vanilla are not drawn while the particles of the rain take their place. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_ParticleRain {

    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$renderRainSnow(float partialTicks, CallbackInfo ci) {
        if (ParticleRain.replacesVanilla()) ci.cancel();
    }
}
