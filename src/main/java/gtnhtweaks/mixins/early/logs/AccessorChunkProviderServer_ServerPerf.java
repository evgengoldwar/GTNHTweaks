package gtnhtweaks.mixins.early.logs;

import java.util.Set;

import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunks waiting to be unloaded, for the Chunk generation log. */
@Mixin(ChunkProviderServer.class)
public interface AccessorChunkProviderServer_ServerPerf {

    @Accessor("chunksToUnload")
    Set<?> gtnhtweaks$getChunksToUnload();
}
