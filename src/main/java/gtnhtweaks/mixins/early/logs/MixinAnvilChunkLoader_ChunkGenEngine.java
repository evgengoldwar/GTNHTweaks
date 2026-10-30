package gtnhtweaks.mixins.early.logs;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the saving of chunks on the server thread, where they are written to NBT before the file. */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader_ChunkGenEngine {

    @Inject(method = "saveChunk", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enter(World world, Chunk chunk, CallbackInfo ci) {
        ChunkGenEngine.saveBegins();
    }

    @Inject(method = "saveChunk", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exit(World world, Chunk chunk, CallbackInfo ci) {
        ChunkGenEngine.saveEnds();
    }
}
