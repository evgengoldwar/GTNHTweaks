package gtnhtweaks.mixins.early.logs;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.logs.ChunkGenEngine;

/**
 * Measures the block work of the world inside chunk generation: setting blocks, light, notices to the neighbors and
 * tile entities. Outside generation the hooks return at once. Not required, so a method changed by another mod only
 * goes unmeasured.
 */
@Mixin(World.class)
public abstract class MixinWorld_ChunkGenEngine {

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterSetBlock(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.SET_BLOCK);
    }

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitSetBlock(int x, int y, int z, Block block, int meta, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.SET_BLOCK);
    }

    @Inject(method = "updateLightByType", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterLight(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.enter(ChunkGenEngine.LIGHT);
    }

    @Inject(method = "updateLightByType", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitLight(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        ChunkGenEngine.exit(ChunkGenEngine.LIGHT);
    }

    /** Added by Forge, so its name is the same everywhere. */
    @Inject(method = "markAndNotifyBlock", remap = false, at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterNotify(int x, int y, int z, Chunk chunk, Block oldBlock, Block newBlock, int flag,
        CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.NOTIFY);
    }

    @Inject(method = "markAndNotifyBlock", remap = false, at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitNotify(int x, int y, int z, Chunk chunk, Block oldBlock, Block newBlock, int flag,
        CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.NOTIFY);
    }

    @Inject(method = "notifyBlocksOfNeighborChange(IIILnet/minecraft/block/Block;)V", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterNeighbors(int x, int y, int z, Block block, CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.NEIGHBORS);
    }

    @Inject(method = "notifyBlocksOfNeighborChange(IIILnet/minecraft/block/Block;)V", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitNeighbors(int x, int y, int z, Block block, CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.NEIGHBORS);
    }

    @Inject(method = "setTileEntity", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$enterTileEntity(int x, int y, int z, TileEntity tileEntity, CallbackInfo ci) {
        ChunkGenEngine.enter(ChunkGenEngine.TILE_ENTITY);
    }

    @Inject(method = "setTileEntity", at = @At("RETURN"), require = 0)
    private void gtnhtweaks$exitTileEntity(int x, int y, int z, TileEntity tileEntity, CallbackInfo ci) {
        ChunkGenEngine.exit(ChunkGenEngine.TILE_ENTITY);
    }
}
