package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/**
 * While the world is drawn, the own player stands at the eyes of its pose: the camera, the culling of the chunks and
 * every other use of its position see the world from there, not from inside the block over a low gap.
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Movement {

    @Shadow
    private Minecraft mc;

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void gtnhtweaks$lowerToEyes(float partialTicks, long finishTime, CallbackInfo ci) {
        MovementClient.beginFrame(mc.renderViewEntity, partialTicks);
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void gtnhtweaks$standBack(float partialTicks, long finishTime, CallbackInfo ci) {
        MovementClient.endFrame();
    }
}
