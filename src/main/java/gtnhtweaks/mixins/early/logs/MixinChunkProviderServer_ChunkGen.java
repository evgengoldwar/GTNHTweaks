package gtnhtweaks.mixins.early.logs;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ChunkGenLog;

/**
 * Times the making of new chunks for the Chunk generation log: the terrain by the chunk generator, when a chunk is
 * neither loaded nor saved, and the population of a chunk with the decoration of the generator. The world generators
 * of the mods inside the population are timed by {@link MixinGameRegistry_ChunkGen}.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer_ChunkGen {

    @Shadow
    public WorldServer worldObj;

    @Shadow
    public IChunkProvider currentChunkProvider;

    /** originalLoadChunk is added by Forge, so its name is the same everywhere; the call in it is of vanilla. */
    @Redirect(
        method = "originalLoadChunk",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            remap = true))
    private Chunk gtnhtweaks$timeTerrain(IChunkProvider generator, int x, int z) {
        Object frame = ChunkGenLog.begin(ChunkGenLog.TERRAIN, generator, worldObj, x, z);
        try {
            return generator.provideChunk(x, z);
        } finally {
            ChunkGenLog.end(frame);
        }
    }

    @Inject(method = "populate", at = @At("HEAD"))
    private void gtnhtweaks$beginPopulation(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkGenLog.beginPopulation(currentChunkProvider, worldObj, x, z);
    }

    @Inject(method = "populate", at = @At("RETURN"))
    private void gtnhtweaks$endPopulation(IChunkProvider provider, int x, int z, CallbackInfo ci) {
        ChunkGenLog.endPopulation(x, z);
    }

    @Redirect(
        method = "populate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;populate(Lnet/minecraft/world/chunk/IChunkProvider;II)V"))
    private void gtnhtweaks$timeDecoration(IChunkProvider generator, IChunkProvider provider, int x, int z) {
        Object frame = ChunkGenLog.begin(ChunkGenLog.DECORATION, generator, worldObj, x, z);
        try {
            generator.populate(provider, x, z);
        } finally {
            ChunkGenLog.end(frame);
        }
    }
}
