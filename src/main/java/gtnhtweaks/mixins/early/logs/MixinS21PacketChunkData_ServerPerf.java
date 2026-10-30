package gtnhtweaks.mixins.early.logs;

import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ServerPerf;

/** The packet of the sections of one chunk, sent again when many of its blocks changed; compressed in it too. */
@Mixin(S21PacketChunkData.class)
public abstract class MixinS21PacketChunkData_ServerPerf {

    @Inject(
        method = "<init>(Lnet/minecraft/world/chunk/Chunk;ZI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Packet;<init>()V", shift = At.Shift.AFTER),
        require = 0)
    private void gtnhtweaks$begins(Chunk chunk, boolean full, int sections, CallbackInfo ci) {
        ServerPerf.resendBegins();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/chunk/Chunk;ZI)V", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$ends(Chunk chunk, boolean full, int sections, CallbackInfo ci) {
        ServerPerf.resendEnds();
    }
}
