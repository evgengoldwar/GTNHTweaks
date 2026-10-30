package gtnhtweaks.mixins.early.logs;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the biome layers asked for by the generators, when a chunk generator does not have its own. */
@Mixin(WorldChunkManager.class)
public abstract class MixinWorldChunkManager_ChunkGenEngine {

    @Inject(
        method = { "getBiomesForGeneration", "loadBlockGeneratorData",
            "getBiomeGenAt([Lnet/minecraft/world/biome/BiomeGenBase;IIIIZ)[Lnet/minecraft/world/biome/BiomeGenBase;" },
        at = @At("HEAD"),
        require = 0)
    private void gtnhtweaks$enter(CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.BIOMES);
    }

    @Inject(
        method = { "getBiomesForGeneration", "loadBlockGeneratorData",
            "getBiomeGenAt([Lnet/minecraft/world/biome/BiomeGenBase;IIIIZ)[Lnet/minecraft/world/biome/BiomeGenBase;" },
        at = @At("RETURN"),
        require = 0)
    private void gtnhtweaks$exit(CallbackInfoReturnable<BiomeGenBase[]> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.BIOMES);
    }
}
