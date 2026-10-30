package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.PreparedProvider;

/**
 * Marks the populations, whose blocks are set quietly, and gives a chunk load the terrain a thread made for it. The
 * chunk generator of the world is stood in for from the start of originalLoadChunk, which Forge adds, until it makes
 * the terrain or the load ends: the call of the generator in it is redirected by ArchaicFix, so it is not redirected
 * here.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer_ChunkEngine {

    @Shadow
    public WorldServer worldObj;

    @Shadow
    public LongHashMap loadedChunkHashMap;

    @Inject(method = "populate", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$beginPopulation(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkEngine.beginPopulation();
    }

    @Inject(method = "populate", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$endPopulation(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkEngine.endPopulation();
    }

    @Inject(method = "originalLoadChunk", remap = false, at = @At("HEAD"), require = 0)
    private void gtnhtweaks$takePreparedTerrain(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z)) != null) return;
        PreparedProvider.install((ChunkProviderServer) (Object) this, worldObj, x, z);
    }

    @Inject(method = "originalLoadChunk", remap = false, at = @At("RETURN"), require = 0)
    private void gtnhtweaks$restoreGenerator(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        PreparedProvider.restore((ChunkProviderServer) (Object) this);
    }
}
