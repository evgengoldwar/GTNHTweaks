package gtnhtweaks.mixins.early.logs;

import java.util.List;

import net.minecraft.network.play.server.S26PacketMapChunkBulk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ServerPerf;

/** The packet of chunks sent to a player: its chunks are copied and compressed in its constructor. */
@Mixin(S26PacketMapChunkBulk.class)
public abstract class MixinS26PacketMapChunkBulk_ServerPerf {

    @Inject(
        method = "<init>(Ljava/util/List;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Packet;<init>()V", shift = At.Shift.AFTER),
        require = 0)
    private void gtnhtweaks$begins(List<?> chunks, CallbackInfo ci) {
        ServerPerf.packetBegins();
    }

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$ends(List<?> chunks, CallbackInfo ci) {
        ServerPerf.packetEnds(chunks.size());
    }
}
