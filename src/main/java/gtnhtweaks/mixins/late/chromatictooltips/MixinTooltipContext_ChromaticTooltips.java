package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slprime.chromatictooltips.api.ITooltipRenderer;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import gtnhtweaks.modules.chromatictooltips.PaletteTargets;

/**
 * Keeps the target found for a tooltip of bare lines with the built tooltip, which Chromatic Tooltips draws later and
 * rebuilds many times a second, and hands it to Chromatic Spectrum while the tooltip is drawn.
 */
@Mixin(value = TooltipContext.class, remap = false)
public class MixinTooltipContext_ChromaticTooltips {

    @Unique
    private TooltipTarget gtnhtweaks$paletteTarget;

    // The other constructor, which keeps the page and animation of the previous tooltip, calls this one
    @Inject(
        method = "<init>(Lcom/slprime/chromatictooltips/api/TooltipRequest;Lcom/slprime/chromatictooltips/api/ITooltipRenderer;)V",
        at = @At("RETURN"))
    private void gtnhtweaks$keepPaletteTarget(TooltipRequest request, ITooltipRenderer renderer, CallbackInfo ci) {
        gtnhtweaks$paletteTarget = PaletteTargets.requested();
    }

    @Inject(method = "drawAt(II)V", at = @At("HEAD"))
    private void gtnhtweaks$beginDraw(int x, int y, CallbackInfo ci) {
        PaletteTargets.beginDraw(gtnhtweaks$paletteTarget);
    }

    @Inject(method = "drawAt(II)V", at = @At("RETURN"))
    private void gtnhtweaks$endDraw(int x, int y, CallbackInfo ci) {
        PaletteTargets.endDraw();
    }
}
