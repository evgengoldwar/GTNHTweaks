package gtnhtweaks.mixins.late.gregtech;

import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gregtech.client.BlockOverlayRenderer;
import gtnhtweaks.modules.gregtech.client.ToolOverlay;

/** Draws the tool overlay of GregTech CEu Modern where GT5 draws its tool grid. */
@Mixin(value = BlockOverlayRenderer.class, remap = false)
public class MixinBlockOverlayRenderer_GregTech {

    @Inject(method = "drawGrid", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gtnhtweaks$drawModernGrid(DrawBlockHighlightEvent event, boolean showCoverConnections,
        boolean isWrench, boolean isSneaking, CallbackInfo ci) {
        if (ToolOverlay.drawGrid(event, showCoverConnections, isWrench, isSneaking)) ci.cancel();
    }
}
