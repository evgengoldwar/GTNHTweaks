package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/** The camera of the own player is as low as the eyes of its pose. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Movement {

    @Shadow
    private Minecraft mc;

    @Inject(method = "orientCamera", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$lowerCamera(float partialTicks, CallbackInfo ci) {
        float drop = MovementClient.cameraDrop(mc.renderViewEntity, partialTicks);
        // the last move of the camera is in the axes of the world: moving the world up lowers the camera
        if (drop != 0) GL11.glTranslatef(0, drop, 0);
    }
}
