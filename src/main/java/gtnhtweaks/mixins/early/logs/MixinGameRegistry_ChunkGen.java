package gtnhtweaks.mixins.early.logs;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;
import gtnhtweaks.modules.logs.ChunkGenLog;

/** Times every world generator of the mods, one by one, when a chunk is populated, for the Chunk generation log. */
@Mixin(value = GameRegistry.class, remap = false)
public abstract class MixinGameRegistry_ChunkGen {

    @Redirect(
        method = "generateWorld",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/IWorldGenerator;generate(Ljava/util/Random;IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V"))
    private static void gtnhtweaks$timeGenerator(IWorldGenerator generator, Random random, int x, int z, World world,
        IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
        Object frame = ChunkGenLog.begin(ChunkGenLog.MOD_GENERATOR, generator, world, x, z);
        try {
            generator.generate(random, x, z, world, chunkGenerator, chunkProvider);
        } finally {
            ChunkGenLog.end(frame);
        }
    }
}
