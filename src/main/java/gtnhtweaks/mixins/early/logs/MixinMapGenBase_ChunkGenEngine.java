package gtnhtweaks.mixins.early.logs;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the caves, ravines and structure layouts carved into a new chunk, by class. */
@Mixin(MapGenBase.class)
public abstract class MixinMapGenBase_ChunkGenEngine {

    @Inject(method = "func_151539_a", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enter(IChunkProvider provider, World world, int x, int z, Block[] blocks,
        CallbackInfo ci) {
        ChunkGenEngine.enterCarver(this);
    }

    @Inject(method = "func_151539_a", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exit(IChunkProvider provider, World world, int x, int z, Block[] blocks, CallbackInfo ci) {
        ChunkGenEngine.exitCarver(this);
    }
}
