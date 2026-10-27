package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.MovementClient;

/** The arm in the first person view takes no pose of the body: crouching or swimming does not bend it. */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayer_Movement {

    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"))
    private void gtnhtweaks$armBegin(EntityPlayer player, CallbackInfo ci) {
        MovementClient.drawingFirstPersonArm(true);
    }

    @Inject(method = "renderFirstPersonArm", at = @At("RETURN"))
    private void gtnhtweaks$armEnd(EntityPlayer player, CallbackInfo ci) {
        MovementClient.drawingFirstPersonArm(false);
    }
}
