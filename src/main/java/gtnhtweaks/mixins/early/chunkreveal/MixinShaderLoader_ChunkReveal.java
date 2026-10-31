package gtnhtweaks.mixins.early.chunkreveal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.chunkreveal.ChunkRevealShaders;

/** Writes the chunk animation into the terrain shaders of Angelica as they are read. Does nothing without Angelica. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.shader.ShaderLoader", remap = false)
public class MixinShaderLoader_ChunkReveal {

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void gtnhtweaks$animateChunks(String name, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        String patched = ChunkRevealShaders.patch(name, source);
        if (patched != source) cir.setReturnValue(patched);
    }
}
