package gtnhtweaks.mixins.early.chunkreveal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gtnhtweaks.modules.chunkreveal.ChunkRevealShaders;

/**
 * Sets the settings of the chunk animation before Angelica draws a region, with the ages of its pieces. One shader
 * interface belongs to one program, so the location of the uniform is looked up once. Does nothing without Angelica.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface", remap = false)
public class MixinDefaultChunkShaderInterface_ChunkReveal {

    @Unique
    private int gtnhtweaks$paramsLocation = -2;

    @Inject(method = "setSectionAges", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$setParams(long timestamp, long[] loadTimes, CallbackInfo ci) {
        if (gtnhtweaks$paramsLocation == -2) gtnhtweaks$paramsLocation = ChunkRevealShaders.paramsLocation();
        ChunkRevealShaders.setParams(gtnhtweaks$paramsLocation);
    }
}
