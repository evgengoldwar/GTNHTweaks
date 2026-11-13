package gtnhtweaks.mixins.early.chunkreveal;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkreveal.ChunkReveal;
import gtnhtweaks.modules.chunkreveal.ChunkRevealState;

/**
 * A piece of a chunk starts to appear when it first gets blocks after it moved to a new place. A piece that was built
 * empty while its chunk was there stays settled: a block placed in it later does not make it appear again.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer_ChunkReveal implements ChunkRevealState {

    @Shadow
    public World worldObj;
    @Shadow
    public int posX;
    @Shadow
    public int posY;
    @Shadow
    public int posZ;
    @Shadow
    private int glRenderList;

    @Unique
    private long gtnhtweaks$start = Long.MIN_VALUE;
    @Unique
    private boolean gtnhtweaks$settled;

    @Shadow
    public abstract boolean skipAllRenderPasses();

    @Override
    public long gtnhtweaks$revealStart() {
        return gtnhtweaks$start;
    }

    @Inject(method = "setPosition", at = @At("HEAD"))
    private void gtnhtweaks$moved(int x, int y, int z, CallbackInfo ci) {
        if (x == posX && y == posY && z == posZ) return;
        gtnhtweaks$start = Long.MIN_VALUE;
        gtnhtweaks$settled = false;
    }

    @Inject(method = "updateRenderer", at = @At("RETURN"))
    private void gtnhtweaks$built(EntityLivingBase viewer, CallbackInfo ci) {
        ChunkReveal.register(glRenderList, (WorldRenderer) (Object) this);
        if (gtnhtweaks$settled) return;
        if (!skipAllRenderPasses()) {
            gtnhtweaks$start = ChunkReveal.startTime((WorldRenderer) (Object) this);
            gtnhtweaks$settled = true;
        } else if (worldObj != null && !worldObj.getChunkFromBlockCoords(posX, posZ)
            .isEmpty()) {
                gtnhtweaks$settled = true;
            }
    }
}
