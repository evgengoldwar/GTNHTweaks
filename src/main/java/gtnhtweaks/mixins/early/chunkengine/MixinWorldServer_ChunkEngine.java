package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.TerrainWorkers;

/** Each world tick: no population is running at its start, and the terrain threads get their next chunks. */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer_ChunkEngine {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$tickStarts(CallbackInfo ci) {
        ChunkEngine.tickStarts();
        TerrainWorkers.tick((WorldServer) (Object) this);
    }
}
