package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkengine.TerrainWorkers;

/**
 * Structures are not started on a terrain thread: they read and write the structure data of the world. The server
 * thread registers them when it takes the chunk, as for a chunk read from disk. Caves and ravines are made as usual.
 */
@Mixin(MapGenBase.class)
public abstract class MixinMapGenBase_ChunkEngine {

    @Inject(method = "func_151539_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$noStructuresOnThreads(IChunkProvider provider, World world, int x, int z, Block[] blocks,
        CallbackInfo ci) {
        if ((Object) this instanceof MapGenStructure && TerrainWorkers.onTerrainThread()) ci.cancel();
    }
}
