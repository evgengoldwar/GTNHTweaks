package gtnhtweaks.mixins.early.logs;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/** Measures the structures built into a chunk when it is populated: villages, mineshafts, strongholds, temples. */
@Mixin(MapGenStructure.class)
public abstract class MixinMapGenStructure_ChunkGenEngine {

    @Inject(method = "generateStructuresInChunk", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enter(World world, Random random, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.enterStructure(this);
    }

    @Inject(method = "generateStructuresInChunk", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exit(World world, Random random, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.exitStructure(this);
    }
}
