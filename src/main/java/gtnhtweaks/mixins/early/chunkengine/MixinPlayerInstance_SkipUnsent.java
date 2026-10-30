package gtnhtweaks.mixins.early.chunkengine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.ChunkEngineModule;

/**
 * The block changes of a chunk that none of its players has yet are dropped before their packets are made: vanilla
 * makes the packets, with the whole sections and every changed tile entity, and then sends them to nobody. The players
 * get the chunk whole later.
 */
@Mixin(targets = "net.minecraft.server.management.PlayerManager$PlayerInstance")
public abstract class MixinPlayerInstance_SkipUnsent {

    @Shadow
    @Final
    private List<EntityPlayerMP> playersWatchingChunk;

    @Shadow
    @Final
    private ChunkCoordIntPair chunkLocation;

    @Shadow
    private int numberOfTilesToUpdate;

    @Shadow
    private int flagsYAreasToUpdate;

    @Inject(method = "sendChunkUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$skipUnsent(CallbackInfo ci) {
        if (numberOfTilesToUpdate == 0 || !ChunkEngineModule.skipUnsentUpdates) return;
        for (EntityPlayerMP player : playersWatchingChunk) {
            if (!ChunkEngine.waitsFor(player, chunkLocation.chunkXPos, chunkLocation.chunkZPos)) return;
        }
        numberOfTilesToUpdate = 0;
        flagsYAreasToUpdate = 0;
        ChunkEngine.SKIPPED_UPDATES.add();
        ci.cancel();
    }
}
