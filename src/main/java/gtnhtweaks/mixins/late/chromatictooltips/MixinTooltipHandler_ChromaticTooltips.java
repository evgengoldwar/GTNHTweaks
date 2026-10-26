package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.ITooltipRenderer;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import gtnhtweaks.modules.chromatictooltips.PaletteTargets;

/** Finds what a tooltip of bare lines is about while it is requested: see {@link PaletteTargets}. */
@Mixin(value = TooltipHandler.class, remap = false)
public class MixinTooltipHandler_ChromaticTooltips {

    @Inject(method = "drawHoveringText(Lcom/slprime/chromatictooltips/api/TooltipRequest;)V", at = @At("HEAD"))
    private static void gtnhtweaks$findPaletteTarget(TooltipRequest request, CallbackInfo ci) {
        PaletteTargets.beginRequest(request);
    }

    @Inject(method = "drawHoveringText(Lcom/slprime/chromatictooltips/api/TooltipRequest;)V", at = @At("RETURN"))
    private static void gtnhtweaks$forgetPaletteTarget(TooltipRequest request, CallbackInfo ci) {
        PaletteTargets.endRequest();
    }

    /**
     * A tooltip of bare lines gets the theme of the default context, which a resource pack can leave without the
     * marker colors Chromatic Spectrum replaces. It gets the theme of the found fluid or item instead.
     */
    @Inject(
        method = "getRendererFor(Lcom/slprime/chromatictooltips/api/TooltipRequest;)Lcom/slprime/chromatictooltips/api/ITooltipRenderer;",
        at = @At("HEAD"),
        cancellable = true)
    private static void gtnhtweaks$themeOfPaletteTarget(TooltipRequest request,
        CallbackInfoReturnable<ITooltipRenderer> cir) {
        TooltipTarget target = PaletteTargets.requested();
        if (target != null && request != null && PaletteTargets.isBare(request.target)) {
            TooltipRequest themed = new TooltipRequest(request.context, target, request.tooltip, request.mouse);
            cir.setReturnValue(TooltipHandler.getRendererFor(themed));
        }
    }
}
