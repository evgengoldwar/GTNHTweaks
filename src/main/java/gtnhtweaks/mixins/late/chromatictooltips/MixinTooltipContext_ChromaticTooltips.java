package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slprime.chromatictooltips.api.ITooltipRenderer;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import gtnhtweaks.modules.chromatictooltips.PaletteTargets;

/**
 * Keeps the target found for a tooltip of bare lines with the built tooltip, which Chromatic Tooltips draws later and
 * rebuilds many times a second, and returns it as the target of the tooltip while it is drawn: Chromatic Spectrum
 * picks the palette of every decorator from the target then. The lines are built before, so they do not change.
 */
@Mixin(value = TooltipContext.class, remap = false)
public class MixinTooltipContext_ChromaticTooltips {

    @Shadow
    protected TooltipTarget target;

    @Unique
    private TooltipTarget gtnhtweaks$paletteTarget;

    @Unique
    private boolean gtnhtweaks$drawing;

    // The other constructor, which keeps the page and animation of the previous tooltip, calls this one
    @Inject(
        method = "<init>(Lcom/slprime/chromatictooltips/api/TooltipRequest;Lcom/slprime/chromatictooltips/api/ITooltipRenderer;)V",
        at = @At("RETURN"))
    private void gtnhtweaks$keepPaletteTarget(TooltipRequest request, ITooltipRenderer renderer, CallbackInfo ci) {
        gtnhtweaks$paletteTarget = PaletteTargets.requested();
    }

    @Inject(method = "drawAt(II)V", at = @At("HEAD"))
    private void gtnhtweaks$beginDraw(int x, int y, CallbackInfo ci) {
        // Tooltips built outside a request, or before the hovered widget was known: the widget is still hovered
        if (gtnhtweaks$paletteTarget == null && PaletteTargets.isBare(target)) {
            gtnhtweaks$paletteTarget = PaletteTargets.hoveredOnScreen();
        }
        gtnhtweaks$drawing = true;
    }

    @Inject(method = "drawAt(II)V", at = @At("RETURN"))
    private void gtnhtweaks$endDraw(int x, int y, CallbackInfo ci) {
        gtnhtweaks$drawing = false;
    }

    @Inject(
        method = "getTarget()Lcom/slprime/chromatictooltips/api/TooltipTarget;",
        at = @At("RETURN"),
        cancellable = true)
    private void gtnhtweaks$paletteTarget(CallbackInfoReturnable<TooltipTarget> cir) {
        if (gtnhtweaks$drawing && gtnhtweaks$paletteTarget != null && PaletteTargets.isBare(cir.getReturnValue())) {
            cir.setReturnValue(gtnhtweaks$paletteTarget);
        }
    }
}
