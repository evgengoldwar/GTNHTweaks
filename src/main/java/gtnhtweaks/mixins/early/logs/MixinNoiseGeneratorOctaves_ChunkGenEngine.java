package gtnhtweaks.mixins.early.logs;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the vanilla noise of the terrain, used by the vanilla generators and many of the mods. */
@Mixin(NoiseGeneratorOctaves.class)
public abstract class MixinNoiseGeneratorOctaves_ChunkGenEngine {

    @Inject(method = "generateNoiseOctaves([DIIIIIIDDD)[D", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enter(double[] noise, int x, int y, int z, int sizeX, int sizeY, int sizeZ, double scaleX,
        double scaleY, double scaleZ, CallbackInfoReturnable<double[]> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.NOISE);
    }

    @Inject(method = "generateNoiseOctaves([DIIIIIIDDD)[D", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exit(double[] noise, int x, int y, int z, int sizeX, int sizeY, int sizeZ, double scaleX,
        double scaleY, double scaleZ, CallbackInfoReturnable<double[]> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.NOISE);
    }
}
