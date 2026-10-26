package gtnhtweaks.mixins.late.chromatictooltips;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipLines;
import com.slprime.chromatictooltips.enricher.ContextInfoEnricher;

import gtnhtweaks.modules.chromatictooltips.FoundTitles;

/** Drops the name line under a title made from the found fluid or item: see {@link FoundTitles}. */
@Mixin(value = ContextInfoEnricher.class, remap = false)
public class MixinContextInfoEnricher_ChromaticTooltips {

    @Inject(
        method = "build(Lcom/slprime/chromatictooltips/api/TooltipContext;)Lcom/slprime/chromatictooltips/api/TooltipLines;",
        at = @At("RETURN"))
    private void gtnhtweaks$removeNameLine(TooltipContext context, CallbackInfoReturnable<TooltipLines> cir) {
        FoundTitles.removeNameLine(context, cir.getReturnValue());
    }
}
