package gtnhtweaks.mixins.early.chunkengine;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import gtnhtweaks.modules.chunkengine.ChunkEngine;
import gtnhtweaks.modules.chunkengine.ChunkEngineModule;

/**
 * Setting blocks on the server: no light update when the light cannot change, and no notices to the neighbors while
 * a chunk is populated. Applied after other mixins, so it wraps what they put in place of the calls.
 */
@Mixin(value = World.class, priority = 1500)
public abstract class MixinWorld_ChunkEngine {

    @Shadow
    public boolean isRemote;

    /** The block just set in setBlock gives and lets through the light of the block before. */
    @Unique
    private boolean gtnhtweaks$sameLight;

    @WrapOperation(
        method = "setBlock(IIILnet/minecraft/block/Block;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;func_150807_a(IIILnet/minecraft/block/Block;I)Z"),
        require = 0)
    private boolean gtnhtweaks$setInChunk(Chunk chunk, int x, int y, int z, Block block, int meta,
        Operation<Boolean> original) {
        if (isRemote || !ChunkEngineModule.skipUnchangedLight) return original.call(chunk, x, y, z, block, meta);
        Block before = chunk.getBlock(x, y, z);
        boolean changed = original.call(chunk, x, y, z, block, meta);
        gtnhtweaks$sameLight = !changed || ChunkEngine.sameLight(before, block);
        return changed;
    }

    @WrapOperation(
        method = "setBlock(IIILnet/minecraft/block/Block;II)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_147451_t(III)Z"),
        require = 0)
    private boolean gtnhtweaks$checkLight(World world, int x, int y, int z, Operation<Boolean> original) {
        if (gtnhtweaks$sameLight) {
            gtnhtweaks$sameLight = false;
            ChunkEngine.SKIPPED_LIGHT.add();
            return false;
        }
        return original.call(world, x, y, z);
    }

    @WrapOperation(
        method = "markAndNotifyBlock",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;notifyBlockChange(IIILnet/minecraft/block/Block;)V",
            remap = true),
        require = 0)
    private void gtnhtweaks$quietNeighbors(World world, int x, int y, int z, Block block, Operation<Void> original) {
        if (!isRemote && ChunkEngine.quiet()) {
            ChunkEngine.QUIET_BLOCKS.add();
            return;
        }
        original.call(world, x, y, z, block);
    }

    @WrapOperation(
        method = "markAndNotifyBlock",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;func_147453_f(IIILnet/minecraft/block/Block;)V",
            remap = true),
        require = 0)
    private void gtnhtweaks$quietComparators(World world, int x, int y, int z, Block block, Operation<Void> original) {
        if (!isRemote && ChunkEngine.quiet()) return;
        original.call(world, x, y, z, block);
    }
}
