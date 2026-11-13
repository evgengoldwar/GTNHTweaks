package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.BlockChunks;

/**
 * Around the world event of a broken block (2001), which plays its sound and its particles: the cubes are made from
 * it when another mod makes the particles past the destroy effects of EffectRenderer.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_BlockChunks {

    @Shadow
    private WorldClient theWorld;

    @Inject(method = "playAuxSFX", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$beginBreak(EntityPlayer player, int type, int x, int y, int z, int data, CallbackInfo ci) {
        if (type == 2001) BlockChunks.beginBreakEffect();
    }

    @Inject(method = "playAuxSFX", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$endBreak(EntityPlayer player, int type, int x, int y, int z, int data, CallbackInfo ci) {
        if (type == 2001) BlockChunks.endBreakEffect(theWorld, x, y, z, data);
    }
}
