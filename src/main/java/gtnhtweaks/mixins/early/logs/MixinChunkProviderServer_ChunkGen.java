package gtnhtweaks.mixins.early.logs;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;
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

    @Shadow
    public LongHashMap loadedChunkHashMap;

    /**
     * A chunk neither loaded nor saved is made in originalLoadChunk, which Forge adds, so its name is the same
     * everywhere. Its whole call is timed, not the call of the generator in it: ArchaicFix redirects that call, and of
     * two redirects of one call only one applies. The populations it starts are steps inside it.
     */
    @Inject(method = "originalLoadChunk", remap = false, at = @At("HEAD"))
    private void gtnhtweaks$beginTerrain(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z)) != null) return;
        ChunkGenLog.begin(ChunkGenLog.TERRAIN, currentChunkProvider, worldObj, x, z);
    }

    @Inject(method = "originalLoadChunk", remap = false, at = @At("RETURN"))
    private void gtnhtweaks$endTerrain(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkGenLog.endTerrain(x, z);
    }

    /** A chunk read from disk while chunks are made, mostly a neighbor a generator reached into. */
    @Inject(
        method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/common/chunkio/ChunkIOExecutor;syncChunkLoad(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/storage/AnvilChunkLoader;Lnet/minecraft/world/gen/ChunkProviderServer;II)Lnet/minecraft/world/chunk/Chunk;"),
        require = 0)
    private void gtnhtweaks$enterDiskLoad(int x, int z, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.DISK_LOAD);
    }

    @Inject(
        method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/common/chunkio/ChunkIOExecutor;syncChunkLoad(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/storage/AnvilChunkLoader;Lnet/minecraft/world/gen/ChunkProviderServer;II)Lnet/minecraft/world/chunk/Chunk;",
            shift = At.Shift.AFTER),
        require = 0)
    private void gtnhtweaks$exitDiskLoad(int x, int z, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.DISK_LOAD);
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
