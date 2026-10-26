package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipRequest;

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
}
