package gtnhtweaks.mixins.early.logs;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraft.world.gen.feature.WorldGenBigTree;
import net.minecraft.world.gen.feature.WorldGenBlockBlob;
import net.minecraft.world.gen.feature.WorldGenCactus;
import net.minecraft.world.gen.feature.WorldGenCanopyTree;
import net.minecraft.world.gen.feature.WorldGenClay;
import net.minecraft.world.gen.feature.WorldGenDeadBush;
import net.minecraft.world.gen.feature.WorldGenDesertWells;
import net.minecraft.world.gen.feature.WorldGenDoublePlant;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenFire;
import net.minecraft.world.gen.feature.WorldGenFlowers;
import net.minecraft.world.gen.feature.WorldGenForest;
import net.minecraft.world.gen.feature.WorldGenGlowStone1;
import net.minecraft.world.gen.feature.WorldGenGlowStone2;
import net.minecraft.world.gen.feature.WorldGenHellLava;
import net.minecraft.world.gen.feature.WorldGenIcePath;
import net.minecraft.world.gen.feature.WorldGenIceSpike;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.feature.WorldGenLiquids;
import net.minecraft.world.gen.feature.WorldGenMegaJungle;
import net.minecraft.world.gen.feature.WorldGenMegaPineTree;
import net.minecraft.world.gen.feature.WorldGenMelon;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenReed;
import net.minecraft.world.gen.feature.WorldGenSand;
import net.minecraft.world.gen.feature.WorldGenSavannaTree;
import net.minecraft.world.gen.feature.WorldGenShrub;
import net.minecraft.world.gen.feature.WorldGenSpikes;
import net.minecraft.world.gen.feature.WorldGenSwamp;
import net.minecraft.world.gen.feature.WorldGenTaiga1;
import net.minecraft.world.gen.feature.WorldGenTaiga2;
import net.minecraft.world.gen.feature.WorldGenTallGrass;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraft.world.gen.feature.WorldGenVines;
import net.minecraft.world.gen.feature.WorldGenWaterlily;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/**
 * Measures the vanilla features, by class: ores, trees, lakes, plants... Mods use many of them, or extend them, as
 * WorldGenMinable for their ores; a feature of a mod that calls the vanilla one is named by its own class.
 */
@Mixin({ WorldGenBigMushroom.class, WorldGenBigTree.class, WorldGenBlockBlob.class, WorldGenCactus.class,
    WorldGenCanopyTree.class, WorldGenClay.class, WorldGenDeadBush.class, WorldGenDesertWells.class,
    WorldGenDoublePlant.class, WorldGenDungeons.class, WorldGenFire.class, WorldGenFlowers.class, WorldGenForest.class,
    WorldGenGlowStone1.class, WorldGenGlowStone2.class, WorldGenHellLava.class, WorldGenIcePath.class,
    WorldGenIceSpike.class, WorldGenLakes.class, WorldGenLiquids.class, WorldGenMegaJungle.class,
    WorldGenMegaPineTree.class, WorldGenMelon.class, WorldGenMinable.class, WorldGenPumpkin.class, WorldGenReed.class,
    WorldGenSand.class, WorldGenSavannaTree.class, WorldGenShrub.class, WorldGenSpikes.class, WorldGenSwamp.class,
    WorldGenTaiga1.class, WorldGenTaiga2.class, WorldGenTallGrass.class, WorldGenTrees.class, WorldGenVines.class,
    WorldGenWaterlily.class })
public abstract class MixinWorldGenerators_ChunkGenEngine {

    @Inject(method = "generate", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enter(World world, Random random, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.enterFeature(this);
    }

    @Inject(method = "generate", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exit(World world, Random random, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.exitFeature(this);
    }
}
