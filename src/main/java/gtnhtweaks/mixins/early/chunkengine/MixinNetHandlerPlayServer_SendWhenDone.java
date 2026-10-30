package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.ChunkEngineModule;

/**
 * Drops the copies of a chunk, and the tile entities in it, sent to a player who is still to get the chunk: it gets
 * the chunk whole, with its tile entities, when its turn comes. Hodgepodge sends a populated chunk again to every
 * player near it; to a player without the chunk that is a copy too many.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer_SendWhenDone {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhtweaks$dropCopies(Packet packet, CallbackInfo ci) {
        if (!ChunkEngineModule.sendFinishedChunks || playerEntity == null) return;
        int chunkX, chunkZ;
        if (packet instanceof S21PacketChunkData) {
            AccessorS21PacketChunkData data = (AccessorS21PacketChunkData) packet;
            chunkX = data.gtnhtweaks$getChunkX();
            chunkZ = data.gtnhtweaks$getChunkZ();
        } else if (packet instanceof S35PacketUpdateTileEntity) {
            AccessorS35PacketUpdateTileEntity data = (AccessorS35PacketUpdateTileEntity) packet;
            chunkX = data.gtnhtweaks$getX() >> 4;
            chunkZ = data.gtnhtweaks$getZ() >> 4;
        } else {
            return;
        }
        if (ChunkEngine.waitsFor(playerEntity, chunkX, chunkZ)) {
            ChunkEngine.DROPPED_COPIES.add();
            ci.cancel();
        }
    }
}
