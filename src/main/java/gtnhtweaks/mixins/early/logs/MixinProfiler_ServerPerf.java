package gtnhtweaks.mixins.early.logs;

import net.minecraft.profiler.Profiler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ServerPerf;

/**
 * Follows the sections of the profiler of the server, which the server walks through every tick even with the
 * profiler off: taken before the profiler returns for being off. Only the server's profiler on its thread counts.
 */
@Mixin(Profiler.class)
public abstract class MixinProfiler_ServerPerf {

    @Inject(method = "startSection", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$start(String name, CallbackInfo ci) {
        ServerPerf.startSection(this, name);
    }

    @Inject(method = "endSection", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$end(CallbackInfo ci) {
        ServerPerf.endSection(this);
    }
}
