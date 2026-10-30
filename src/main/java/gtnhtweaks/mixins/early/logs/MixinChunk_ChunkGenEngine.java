package gtnhtweaks.mixins.early.logs;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the work of a chunk inside generation: its block storage, its sky light map and its light check. */
@Mixin(Chunk.class)
public abstract class MixinChunk_ChunkGenEngine {

    @Inject(method = "func_150807_a", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterSet(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.CHUNK_SET);
    }

    @Inject(method = "func_150807_a", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitSet(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.CHUNK_SET);
    }

    @Inject(method = "generateSkylightMap", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterSkylight(CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.SKYLIGHT);
    }

    @Inject(method = "generateSkylightMap", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitSkylight(CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.SKYLIGHT);
    }

    @Inject(method = "func_150809_p", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterLightCheck(CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.LIGHT_CHECK);
    }

    @Inject(method = "func_150809_p", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitLightCheck(CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.LIGHT_CHECK);
    }
}
