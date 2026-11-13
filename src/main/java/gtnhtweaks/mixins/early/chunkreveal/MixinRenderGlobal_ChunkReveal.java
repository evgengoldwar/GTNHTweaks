package gtnhtweaks.mixins.early.chunkreveal;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtnhtweaks.modules.chunkreveal.ChunkReveal;

/**
 * The pieces of chunks that appear are kept out of the display lists RenderGlobal draws together, and drawn on their
 * own, moved or scaled, at the end of the same pass. Angelica draws the chunks otherwise: there these do nothing.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_ChunkReveal {

    @Inject(method = "renderSortedRenderers", at = @At("HEAD"), require = 0)
    private void gtnhtweaks$beginPass(int start, int end, int pass, double partialTicks,
        CallbackInfoReturnable<Integer> cir) {
        ChunkReveal.beginPass(pass);
    }

    @Redirect(
        method = "renderSortedRenderers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderList;addGLRenderList(I)V"),
        require = 0)
    private void gtnhtweaks$holdAppearing(RenderList renderList, int list) {
        if (!ChunkReveal.hold(list)) renderList.addGLRenderList(list);
    }

    @Inject(
        method = "renderAllRenderLists",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;disableLightmap(D)V"),
        require = 0)
    private void gtnhtweaks$drawAppearing(int pass, double partialTicks, CallbackInfo ci) {
        ChunkReveal.drawHeld(partialTicks);
    }
}
