package gtnhtweaks.mixins.early.logs;

import java.util.Random;

import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the animals spawned with a new chunk when it is populated. */
@Mixin(SpawnerAnimals.class)
public abstract class MixinSpawnerAnimals_ChunkGenEngine {

    @Inject(method = "performWorldGenSpawning", at = @At("HEAD"), require = 0)
    private static void gtnhtweaks$enter(World world, BiomeGenBase biome, int x, int z, int sizeX, int sizeZ,
        Random random, CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.SPAWN);
    }

    @Inject(method = "performWorldGenSpawning", at = @At("RETURN"), require = 0)
    private static void gtnhtweaks$exit(World world, BiomeGenBase biome, int x, int z, int sizeX, int sizeZ,
        Random random, CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.SPAWN);
    }
}
