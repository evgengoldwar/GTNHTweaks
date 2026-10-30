package gtnhtweaks.mixins.early.logs;

import net.minecraftforge.common.chunkio.ChunkIOExecutor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ServerPerf;

/** The chunks read from disk in the background, finished on the server thread every tick. */
@Mixin(value = ChunkIOExecutor.class, remap = false)
public abstract class MixinChunkIOExecutor_ServerPerf {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private static void gtnhtweaks$begins(CallbackInfo ci) {
        ServerPerf.asyncLoadsBegin();
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private static void gtnhtweaks$ends(CallbackInfo ci) {
        ServerPerf.asyncLoadsEnd();
    }
}
