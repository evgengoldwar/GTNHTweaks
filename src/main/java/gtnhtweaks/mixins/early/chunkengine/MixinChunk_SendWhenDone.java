package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.ChunkEngineModule;

/**
 * A chunk of a server world is ready to be sent once it and the neighbors whose population writes into it are
 * populated, or after a wait. func_150802_k is what every sender asks, vanilla and Hodgepodge alike, and what decides
 * whether a set block is sent: a chunk not ready yet is with no player. Once ready, a chunk stays ready, so the players
 * that got it get its changes. Applied after other mixins, also when one replaces the method.
 */
@Mixin(value = Chunk.class, priority = 1500)
public abstract class MixinChunk_SendWhenDone {

    @Shadow
    public World worldObj;

    @Unique
    private boolean gtnhtweaks$ready;
    @Unique
    private long gtnhtweaks$waitingSince = -1;
    /** The world time of the last answer "not ready", which holds for the rest of that tick. */
    @Unique
    private long gtnhtweaks$notReadyAt = -1;

    @ModifyReturnValue(method = "func_150802_k", at = @At("RETURN"), require = 0)
    private boolean gtnhtweaks$readyWhenDone(boolean ready) {
        if (!ready || gtnhtweaks$ready || !ChunkEngineModule.sendFinishedChunks || !(worldObj instanceof WorldServer))
            return ready;
        long now = worldObj.getTotalWorldTime();
        if (gtnhtweaks$notReadyAt == now) return false;
        if (ChunkEngine.neighborsPopulated((Chunk) (Object) this)) {
            gtnhtweaks$ready = true;
            return true;
        }
        if (gtnhtweaks$waitingSince < 0) {
            gtnhtweaks$waitingSince = now;
            ChunkEngine.HELD_BACK.add();
        } else if (now - gtnhtweaks$waitingSince >= ChunkEngineModule.sendWaitSeconds * 20L) {
            gtnhtweaks$ready = true;
            ChunkEngine.SENT_ON_TIMEOUT.add();
            return true;
        }
        gtnhtweaks$notReadyAt = now;
        return false;
    }
}
