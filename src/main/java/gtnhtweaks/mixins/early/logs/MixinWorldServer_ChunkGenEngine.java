package gtnhtweaks.mixins.early.logs;

import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the block ticks scheduled inside chunk generation, as of the liquids of lakes and springs. */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer_ChunkGenEngine {

    @Inject(method = "scheduleBlockUpdateWithPriority", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterTick(int x, int y, int z, Block block, int delay, int priority, CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.TICKS);
    }

    @Inject(method = "scheduleBlockUpdateWithPriority", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitTick(int x, int y, int z, Block block, int delay, int priority, CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.TICKS);
    }
}
