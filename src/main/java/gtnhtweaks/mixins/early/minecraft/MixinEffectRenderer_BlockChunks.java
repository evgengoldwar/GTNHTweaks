package gtnhtweaks.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.minecraft.client.BlockChunks;

/** A broken block falls apart into little cubes of itself instead of the particles of vanilla. */
@Mixin(EffectRenderer.class)
public class MixinEffectRenderer_BlockChunks {

    @Shadow
    protected World worldObj;

    @Inject(method = "addBlockDestroyEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$chunks(int x, int y, int z, Block block, int meta, CallbackInfo ci) {
        if (block == null || block.isAir(worldObj, x, y, z)) return;
        EffectRenderer self = (EffectRenderer) (Object) this;
        // a mod with break effects of its own keeps them, as Forge does
        if (block.addDestroyEffects(worldObj, x, y, z, meta, self)) {
            ci.cancel();
            return;
        }
        if (BlockChunks.breakBlock(worldObj, self, x, y, z, block, meta)) ci.cancel();
    }
}
